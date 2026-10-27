package com.reiux.lumen.util

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class FolderWatcherManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val onNewMediaDetected: (List<File>) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileObservers = mutableListOf<FileObserver>()
    private var contentObserver: ContentObserver? = null
    private var debounceJob: Job? = null
    private val pendingFiles = mutableSetOf<File>()

    private val supportedExts = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "dng", "cr2", "nef", "arw",
        "mp4", "mov", "mkv", "avi", "3gp", "webm"
    )

    fun start() {
        scope.launch {
            settingsRepository.settings
                .map { Pair(it.autoBackupEnabled, it.folders.filter { f -> f.enabled }) }
                .distinctUntilChanged()
                .collectLatest { (autoBackupEnabled, enabledFolders) ->
                    val reason = if (!autoBackupEnabled) {
                        "Auto-backup is disabled in settings"
                    } else if (enabledFolders.isEmpty()) {
                        "No enabled backup folders selected"
                    } else {
                        "Enabled folder list updated: ${enabledFolders.size} folders"
                    }
                    syncWatchers(autoBackupEnabled, enabledFolders, reason)
                }
        }
    }

    private fun syncWatchers(autoBackupEnabled: Boolean, enabledFolders: List<BackupFolder>, reason: String) {
        stopWatchers()

        if (!autoBackupEnabled || enabledFolders.isEmpty()) {
            LumenLogger.i(TAG) {
                "Watcher idle (reason='$reason', autoBackupEnabled=$autoBackupEnabled, enabledFoldersCount=${enabledFolders.size})"
            }
            return
        }

        LumenLogger.i(TAG) {
            "Starting folder watchers (reason='$reason', count=${enabledFolders.size})"
        }

        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
        val maxObservers = 200

        for (folder in enabledFolders) {
            val rootDir = File(folder.path)
            if (!rootDir.exists() || !rootDir.isDirectory) {
                LumenLogger.w(TAG) { "Cannot watch folder (directory does not exist): ${folder.path}" }
                continue
            }

            val dirsToWatch = mutableListOf<File>()
            if (folder.includeSubfolders) {
                val stack = java.util.ArrayDeque<Pair<File, Int>>()
                stack.push(rootDir to 0)
                while (stack.isNotEmpty()) {
                    val (dir, depth) = stack.pop()
                    dirsToWatch.add(dir)
                    if (fileObservers.size + dirsToWatch.size >= maxObservers) {
                        LumenLogger.w(TAG) { "Reached maximum FileObserver cap ($maxObservers). Relying on MediaStore ContentObserver for remaining subdirectories." }
                        break
                    }
                    if (depth < 8) {
                        dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { sub ->
                            stack.push(sub to (depth + 1))
                        }
                    }
                }
            } else {
                dirsToWatch.add(rootDir)
            }

            for (dir in dirsToWatch) {
                if (fileObservers.size >= maxObservers) break

                val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    object : FileObserver(dir, mask) {
                        override fun onEvent(event: Int, path: String?) {
                            handleFileEvent(dir, path, event)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    object : FileObserver(dir.absolutePath, mask) {
                        override fun onEvent(event: Int, path: String?) {
                            handleFileEvent(dir, path, event)
                        }
                    }
                }

                try {
                    observer.startWatching()
                    fileObservers.add(observer)
                    LumenLogger.d(TAG) { "FileObserver registered on: ${dir.absolutePath}" }
                } catch (e: Exception) {
                    LumenLogger.w(TAG) { "Failed to start FileObserver on ${dir.absolutePath}: ${e.message}" }
                }
            }
        }

        val handler = Handler(Looper.getMainLooper())
        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                LumenLogger.d(TAG) { "MediaStore ContentObserver triggered: onChange(selfChange=$selfChange)" }
                queryRecentMediaStoreItems(enabledFolders)
            }
        }

        try {
            contentObserver?.let { obs ->
                context.contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    obs
                )
                context.contentResolver.registerContentObserver(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    true,
                    obs
                )
                LumenLogger.d(TAG) { "Registered MediaStore ContentObserver for Images and Video URIs" }
            }
        } catch (e: Exception) {
            LumenLogger.w(TAG) { "Failed to register MediaStore observer: ${e.message}" }
        }
    }

    private fun handleFileEvent(dir: File, relativePath: String?, event: Int) {
        val eventName = when (event and FileObserver.ALL_EVENTS) {
            FileObserver.CLOSE_WRITE -> "CLOSE_WRITE"
            FileObserver.MOVED_TO -> "MOVED_TO"
            FileObserver.CREATE -> "CREATE"
            FileObserver.MODIFY -> "MODIFY"
            else -> "EVENT_0x${Integer.toHexString(event)}"
        }

        if (relativePath.isNullOrEmpty()) {
            LumenLogger.v(TAG) { "Raw FileObserver event: event=$eventName in dir='${dir.name}' (null relative path, ignored)" }
            return
        }

        val file = File(dir, relativePath)
        val ext = file.extension.lowercase()

        LumenLogger.d(TAG) {
            "Raw FileObserver event: file='${file.name}' event=$eventName dir='${dir.path}' ext='$ext'"
        }

        if (file.name.startsWith(".") || file.name.startsWith(".trash") || dir.name.startsWith(".")) {
            LumenLogger.v(TAG) { "Ignored hidden/trash file: '${file.name}'" }
            return
        }

        if (ext in supportedExts) {
            LumenLogger.d(TAG) { "Candidate media accepted: '${file.name}', scheduling debounce..." }
            queueFileForBackup(file)
        } else {
            LumenLogger.v(TAG) { "Ignored file: '${file.name}' (extension '$ext' not in supportedExts)" }
        }
    }

    private fun queryRecentMediaStoreItems(enabledFolders: List<BackupFolder>) {
        scope.launch {
            try {
                val projection = arrayOf(
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATE_MODIFIED
                )
                val queryUris = listOf(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                )

                var matchedCount = 0
                for (uri in queryUris) {
                    context.contentResolver.query(
                        uri,
                        projection,
                        null,
                        null,
                        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                    )?.use { cursor ->
                        val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        var checked = 0
                        while (cursor.moveToNext() && checked < 25) {
                            checked++
                            if (dataCol >= 0) {
                                val path = cursor.getString(dataCol)
                                if (!path.isNullOrEmpty()) {
                                    if (enabledFolders.any { isPathMatchingFolder(path, it) }) {
                                        val file = File(path)
                                        if (file.extension.lowercase() in supportedExts && file.exists() && file.length() > 0) {
                                            matchedCount++
                                            queueFileForBackup(file)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                LumenLogger.v(TAG) { "MediaStore query finished: matchedCount=$matchedCount in watched folders" }
            } catch (e: Exception) {
                LumenLogger.w(TAG) { "MediaStore query error: ${e.message}" }
            }
        }
    }

    private fun queueFileForBackup(file: File) {
        synchronized(pendingFiles) {
            pendingFiles.add(file)
        }

        debounceJob?.cancel()
        debounceJob = scope.launch {
            LumenLogger.d(TAG) { "Debounce timer started (2000ms) for ${pendingFiles.size} pending file(s)" }
            delay(2000L)

            val filesToSend: List<File>
            synchronized(pendingFiles) {
                filesToSend = pendingFiles.filter { it.exists() && it.isFile && it.length() > 0 }
                val discarded = pendingFiles.size - filesToSend.size
                if (discarded > 0) {
                    LumenLogger.d(TAG) { "Debounce discarded $discarded incomplete/deleted file(s)" }
                }
                pendingFiles.clear()
            }

            if (filesToSend.isNotEmpty()) {
                LumenLogger.i(TAG) {
                    "Debounce complete: enqueued ${filesToSend.size} new media file(s) for auto-backup: ${filesToSend.map { it.name }}"
                }
                onNewMediaDetected(filesToSend)
            } else {
                LumenLogger.d(TAG) { "Debounce complete: 0 valid files to enqueue" }
            }
        }
    }

    fun stopWatchers() {
        if (fileObservers.isNotEmpty() || contentObserver != null) {
            LumenLogger.d(TAG) { "Stopping ${fileObservers.size} FileObservers and ContentObserver" }
        }
        for (obs in fileObservers) {
            try {
                obs.stopWatching()
            } catch (_: Exception) {}
        }
        fileObservers.clear()

        contentObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
        }
        contentObserver = null
    }

    companion object {
        private const val TAG = "FolderWatcher"

        fun isPathMatchingFolder(filePath: String, folder: BackupFolder): Boolean {
            val folderCanonical = try { File(folder.path).canonicalPath } catch (_: Exception) { folder.path }.trimEnd('/')
            val fileCanonical = try { File(filePath).canonicalPath } catch (_: Exception) { filePath }

            val fileParent = try { File(fileCanonical).parentFile?.canonicalPath } catch (_: Exception) { File(fileCanonical).parent }?.trimEnd('/')

            if (fileParent == folderCanonical) return true
            if (folder.includeSubfolders && fileCanonical.startsWith("$folderCanonical/")) return true

            return false
        }
    }
}
