package com.reiux.lumen.util

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
            // FIX: Only restart watcher when autoBackupEnabled or the enabled folder list ACTUALLY changes.
            // Changes to color preset, notifications, storage saver, etc. will not trigger watcher restart.
            settingsRepository.settings
                .map { Pair(it.autoBackupEnabled, it.folders.filter { f -> f.enabled }.map { f -> f.path }) }
                .distinctUntilChanged()
                .collectLatest { (autoBackupEnabled, enabledFolderPaths) ->
                    val reason = if (!autoBackupEnabled) {
                        "Auto-backup is disabled in settings"
                    } else if (enabledFolderPaths.isEmpty()) {
                        "No enabled backup folders selected"
                    } else {
                        "Enabled folder list updated: ${enabledFolderPaths.size} folders"
                    }
                    syncWatchers(autoBackupEnabled, enabledFolderPaths, reason)
                }
        }
    }

    private fun syncWatchers(autoBackupEnabled: Boolean, enabledFolderPaths: List<String>, reason: String) {
        stopWatchers()

        if (!autoBackupEnabled || enabledFolderPaths.isEmpty()) {
            LumenLogger.i(TAG) {
                "Watcher idle (reason='$reason', autoBackupEnabled=$autoBackupEnabled, enabledFoldersCount=${enabledFolderPaths.size})"
            }
            return
        }

        LumenLogger.i(TAG) {
            "Starting folder watchers (reason='$reason', count=${enabledFolderPaths.size}): $enabledFolderPaths"
        }

        // 1. Setup FileObserver on each enabled directory
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
        for (folderPath in enabledFolderPaths) {
            val dir = File(folderPath)
            if (!dir.exists() || !dir.isDirectory) {
                LumenLogger.w(TAG) { "Cannot watch folder (directory does not exist): $folderPath" }
                continue
            }

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
                LumenLogger.w(TAG) { "Failed to start FileObserver on $folderPath: ${e.message}" }
            }
        }

        // 2. Setup MediaStore ContentObserver for camera & gallery writes
        val handler = Handler(Looper.getMainLooper())
        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                LumenLogger.d(TAG) { "MediaStore ContentObserver triggered: onChange(selfChange=$selfChange)" }
                queryRecentMediaStoreItems(enabledFolderPaths)
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

        if (ext in supportedExts) {
            LumenLogger.d(TAG) { "Candidate media accepted: '${file.name}', scheduling debounce..." }
            queueFileForBackup(file)
        } else {
            LumenLogger.v(TAG) { "Ignored file: '${file.name}' (extension '$ext' not in supportedExts)" }
        }
    }

    private fun queryRecentMediaStoreItems(enabledFolderPaths: List<String>) {
        scope.launch {
            try {
                val extStorage = try {
                    android.os.Environment.getExternalStorageDirectory().absolutePath
                } catch (_: Exception) {
                    "/storage/emulated/0"
                }
                val allNormalizedEnabledPaths = enabledFolderPaths.flatMap { folderPath ->
                    val canonical = try { File(folderPath).canonicalPath } catch (_: Exception) { folderPath }
                    listOf(folderPath, canonical, folderPath.replaceFirst("/sdcard", extStorage))
                }.toSet()

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
                                if (!path.isNullOrEmpty() && allNormalizedEnabledPaths.any { path.startsWith(it) }) {
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
            delay(2000L) // Debounce so camera or file writer finishes completely

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
    }
}
