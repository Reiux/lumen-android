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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.util.Locale

class FolderWatcherManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val watermarkMutex: Mutex = Mutex(),
    private val onNewMediaDetected: (List<File>) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watcherLock = Any()
    private val pendingLock = Any()
    private val mediaStoreQueryLock = Any()
    private val fileObservers = mutableListOf<FileObserver>()
    private var contentObserver: ContentObserver? = null
    private var debounceJob: Job? = null
    private val pendingFiles = mutableSetOf<File>()
    private var mediaStoreScanJob: Job? = null
    private var mediaStoreRescanPending = false

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
        synchronized(watcherLock) {
            stopWatchers()

            if (!autoBackupEnabled || enabledFolders.isEmpty()) {
                LumenLogger.i(TAG) {
                    "Watcher idle (reason='$reason', autoBackupEnabled=$autoBackupEnabled, enabledFoldersCount=${enabledFolders.size})"
                }
                return@synchronized
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
            if (!isSymbolicLink(rootDir)) {
                val rootCanonical = try { rootDir.canonicalPath.trimEnd('/') } catch (_: Exception) { rootDir.absolutePath.trimEnd('/') }
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
                            dir.listFiles()?.filter { sub ->
                                sub.isDirectory && !sub.name.startsWith(".") && !isSymbolicLink(sub) && isWithinCanonicalRoot(sub, rootCanonical)
                            }?.forEach { sub ->
                                stack.push(sub to (depth + 1))
                            }
                        }
                    }
                } else {
                    dirsToWatch.add(rootDir)
                }
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
                queryRecentMediaStoreItems()
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

        val currentEnabledFolders = settingsRepository.getAllFolders().filter { it.enabled }
        if (!currentEnabledFolders.any { isPathMatchingFolder(file.absolutePath, it) }) {
            LumenLogger.v(TAG) { "Ignored file outside enabled folders: '${file.name}' (${file.absolutePath})" }
            return
        }

        if (ext in supportedExts) {
            LumenLogger.d(TAG) { "Candidate media accepted: '${file.name}', scheduling debounce..." }
            queueFileForBackup(file)
        } else {
            LumenLogger.v(TAG) { "Ignored file: '${file.name}' (extension '$ext' not in supportedExts)" }
        }
    }

    private fun queryRecentMediaStoreItems() {
        synchronized(mediaStoreQueryLock) {
            if (mediaStoreScanJob?.isActive == true) {
                mediaStoreRescanPending = true
                return
            }
            mediaStoreRescanPending = false
            mediaStoreScanJob = scope.launch {
                do {
                    synchronized(mediaStoreQueryLock) {
                        mediaStoreRescanPending = false
                    }
                    runMediaStoreWatermarkScan()
                } while (synchronized(mediaStoreQueryLock) { mediaStoreRescanPending })
            }
        }
    }

    private suspend fun runMediaStoreWatermarkScan() {
        if (!settingsRepository.settings.value.autoBackupEnabled) return
        val enabledFolders = settingsRepository.getAllFolders().filter { it.enabled && !it.accountEmail.isNullOrBlank() }
        if (enabledFolders.isEmpty()) {
            LumenLogger.d(TAG) { "MediaStore ContentObserver triggered, but zero folders are enabled. Ignoring." }
            return
        }

        watermarkMutex.withLock {
            try {
                val liveEnabled = settingsRepository.getAllFolders().filter { it.enabled && !it.accountEmail.isNullOrBlank() }
                if (liveEnabled.isEmpty()) return@withLock

                val foldersByAccount = liveEnabled.groupBy { it.accountEmail?.lowercase()?.trim() }
                var totalMatchedCount = 0

                val projection = mutableListOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DATE_ADDED
                ).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        add(MediaStore.MediaColumns.RELATIVE_PATH)
                        add(MediaStore.MediaColumns.IS_PENDING)
                        add(MediaStore.MediaColumns.VOLUME_NAME)
                    }
                    add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                }.toTypedArray()

                val queryUris = listOf(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                )

                for ((accountEmail, accFolders) in foldersByAccount) {
                    if (accountEmail.isNullOrBlank()) continue

                    for (folder in accFolders) {
                        val lastWatermarkId = settingsRepository.getFolderWatermarkId(accountEmail, folder.path)
                        var maxId = lastWatermarkId
                        var folderMatchedCount = 0

                        var selection = "${MediaStore.MediaColumns._ID} > ?"
                        var selectionArgs = arrayOf(lastWatermarkId.toString())

                        if (lastWatermarkId == 0L) {
                            val yesterdaySec = (System.currentTimeMillis() / 1000) - 86400
                            selection = "${MediaStore.MediaColumns.DATE_ADDED} > ?"
                            selectionArgs = arrayOf(yesterdaySec.toString())
                        }

                        val nowSec = System.currentTimeMillis() / 1000
                        var firstNotReadyId: Long? = null
                        for (uri in queryUris) {
                            try {
                                queryIncludingPending(
                                    context.contentResolver,
                                    uri,
                                    projection,
                                    selection,
                                    selectionArgs,
                                    "${MediaStore.MediaColumns._ID} ASC"
                                )?.use { cursor ->
                                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                                    val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                                    val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
                                    val volCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME) else -1
                                    val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                                    val pendingCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING) else -1
                                    val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)

                                    while (cursor.moveToNext()) {
                                        val mediaId = if (idCol >= 0) cursor.getLong(idCol) else 0L
                                        val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                                        val relPath = if (relCol >= 0) cursor.getString(relCol) else null
                                        val volumeName = if (volCol >= 0) cursor.getString(volCol) else null
                                        val bucketName = if (bucketCol >= 0) cursor.getString(bucketCol) else null

                                        if (isMediaStoreItemInFolder(path, relPath, bucketName, folder, volumeName)) {
                                            val isPending = pendingCol >= 0 && cursor.getInt(pendingCol) == 1
                                            val file = if (!path.isNullOrEmpty()) File(path) else null
                                            val fileReady = !isPending && file != null && file.exists() && file.isFile && file.length() > 0 && isPathMatchingFolder(file.absolutePath, folder)
                                            val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L

                                            if (shouldHoldWatermark(fileReady, dateAdded, nowSec)) {
                                                if (firstNotReadyId == null || mediaId < firstNotReadyId!!) firstNotReadyId = mediaId
                                                continue
                                            }
                                            if (mediaId > maxId) {
                                                maxId = mediaId
                                            }
                                            if (fileReady && file != null && file.extension.lowercase() in supportedExts) {
                                                folderMatchedCount++
                                                totalMatchedCount++
                                                queueFileForBackup(file)
                                            }
                                        }
                                    }
                                }
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                LumenLogger.w(TAG) { "MediaStore query error for folder ${folder.path}: ${e.message}" }
                            }
                        }

                        val newWatermark = computeSafeWatermark(lastWatermarkId, maxId, firstNotReadyId)
                        if (newWatermark > lastWatermarkId) {
                            settingsRepository.saveFolderWatermarkId(accountEmail, folder.path, newWatermark)
                            LumenLogger.d(TAG) { "Updated watermark for folder '${folder.displayName}' (account=$accountEmail) to $newWatermark (found $folderMatchedCount new item(s))" }
                        }
                    }
                }

                if (totalMatchedCount == 0) {
                    LumenLogger.d(TAG) { "MediaStore ContentObserver triggered: 0 new items in enabled folders since watermark. Worker not triggered." }
                } else {
                    LumenLogger.i(TAG) { "MediaStore ContentObserver: matched $totalMatchedCount new file(s) in enabled folders since watermark." }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                LumenLogger.w(TAG) { "MediaStore query execution error: ${e.message}" }
            }
        }
    }

    private fun queueFileForBackup(file: File) {
        val currentEnabledFolders = settingsRepository.getAllFolders().filter { it.enabled }
        if (!currentEnabledFolders.any { isPathMatchingFolder(file.absolutePath, it) }) {
            return
        }

        synchronized(pendingLock) {
            pendingFiles.add(file)
            scheduleDebounceLocked(delayMs = 2000L)
        }
    }

    private fun scheduleDebounceLocked(delayMs: Long) {
        val countSnapshot = pendingFiles.size
        debounceJob?.cancel()
        debounceJob = scope.launch {
            LumenLogger.d(TAG) { "Debounce timer started (${delayMs}ms) for $countSnapshot pending file(s)" }
            delay(delayMs)

            val filesToSend = mutableListOf<File>()
            synchronized(pendingLock) {
                val nowMs = System.currentTimeMillis()
                val stillWriting = mutableListOf<File>()
                var discarded = 0
                for (candidate in pendingFiles) {
                    if (candidate.exists() && candidate.isFile && candidate.length() > 0L) {
                        filesToSend.add(candidate)
                    } else if (candidate.exists() && candidate.isFile && candidate.length() == 0L && (nowMs - candidate.lastModified()) < 60_000L) {
                        // Keep newly created zero-length files still being flushed for the next debounce pass
                        stillWriting.add(candidate)
                    } else {
                        discarded++
                    }
                }
                if (discarded > 0) {
                    LumenLogger.d(TAG) { "Debounce discarded $discarded incomplete/deleted file(s)" }
                }
                pendingFiles.clear()
                pendingFiles.addAll(stillWriting)
                debounceJob = null
                if (stillWriting.isNotEmpty()) {
                    scheduleDebounceLocked(delayMs = 2000L)
                }
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
        synchronized(watcherLock) {
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
    }

    companion object {
        private const val TAG = "FolderWatcher"

        /**
         * A not-ready item (still pending / empty) older than this is treated as abandoned, so a
         * broken entry can't hold a folder's watermark back forever. Long enough for long recordings.
         */
        private const val NOT_READY_GRACE_SEC = 6 * 3600L

        /**
         * Queries MediaStore including items that are still pending (being written). Camera apps
         * insert a pending row when a video recording *starts*, so without this a photo taken
         * during recording could push the watermark past the video and it would never be backed up.
         */
        fun queryIncludingPending(
            resolver: android.content.ContentResolver,
            uri: android.net.Uri,
            projection: Array<String>,
            selection: String,
            selectionArgs: Array<String>,
            sortOrder: String
        ): android.database.Cursor? {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val args = android.os.Bundle().apply {
                        putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                        putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                        putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                    }
                    resolver.query(uri, projection, args, null)
                }
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                    @Suppress("DEPRECATION")
                    resolver.query(MediaStore.setIncludePending(uri), projection, selection, selectionArgs, sortOrder)
                }
                else -> resolver.query(uri, projection, selection, selectionArgs, sortOrder)
            }
        }

        /** True if a MediaStore item is still being written and should hold the watermark back. */
        internal fun shouldHoldWatermark(fileReady: Boolean, dateAddedSec: Long, nowSec: Long): Boolean {
            return !fileReady && (nowSec - dateAddedSec) < NOT_READY_GRACE_SEC
        }

        /**
         * Never advance a folder's watermark past an item that isn't ready yet, otherwise that item
         * is skipped forever once it finishes writing.
         */
        internal fun computeSafeWatermark(lastWatermark: Long, maxSeenId: Long, firstNotReadyId: Long?): Long {
            val capped = if (firstNotReadyId == null) maxSeenId else minOf(maxSeenId, firstNotReadyId - 1)
            return maxOf(lastWatermark, capped)
        }

        private const val PRIMARY_STORAGE_ROOT = "/storage/emulated/0"

        private fun isSymbolicLink(file: File): Boolean = try {
            Files.isSymbolicLink(file.toPath())
        } catch (_: Exception) {
            false
        }

        private fun isWithinCanonicalRoot(entry: File, rootCanonical: String): Boolean {
            val canonical = try { entry.canonicalPath.trimEnd('/') } catch (_: Exception) { entry.absolutePath.trimEnd('/') }
            return canonical == rootCanonical || canonical.startsWith("$rootCanonical/")
        }

        internal fun normalizeStoragePath(rawPath: String): String {
            val cleaned = rawPath.trim().replace('\\', '/')
            val canonical = try {
                File(cleaned).canonicalPath
            } catch (_: Exception) {
                cleaned
            }.replace('\\', '/').trimEnd('/')
            if (canonical.isEmpty()) return "/"

            return when {
                canonical.equals("/sdcard", ignoreCase = true) -> PRIMARY_STORAGE_ROOT
                canonical.startsWith("/sdcard/", ignoreCase = true) ->
                    PRIMARY_STORAGE_ROOT + canonical.substring("/sdcard".length)
                canonical.equals("/storage/self/primary", ignoreCase = true) -> PRIMARY_STORAGE_ROOT
                canonical.startsWith("/storage/self/primary/", ignoreCase = true) ->
                    PRIMARY_STORAGE_ROOT + canonical.substring("/storage/self/primary".length)
                canonical.equals(PRIMARY_STORAGE_ROOT, ignoreCase = true) -> PRIMARY_STORAGE_ROOT
                canonical.startsWith("$PRIMARY_STORAGE_ROOT/", ignoreCase = true) ->
                    PRIMARY_STORAGE_ROOT + canonical.substring(PRIMARY_STORAGE_ROOT.length)
                else -> canonical
            }
        }

        internal fun resolveExpectedMediaStoreVolume(folderCanonical: String): String? {
            val norm = normalizeStoragePath(folderCanonical)
            if (norm == PRIMARY_STORAGE_ROOT || norm.startsWith("$PRIMARY_STORAGE_ROOT/")) {
                return "external_primary"
            }
            if (norm.startsWith("/storage/")) {
                val afterStorage = norm.removePrefix("/storage/")
                val volSegment = afterStorage.substringBefore('/')
                if (volSegment.isNotBlank() &&
                    !volSegment.equals("emulated", ignoreCase = true) &&
                    !volSegment.equals("self", ignoreCase = true)
                ) {
                    return volSegment.lowercase(Locale.US)
                }
            }
            return null
        }

        internal fun extractRelativeFolderPath(folderCanonical: String): String? {
            val norm = normalizeStoragePath(folderCanonical)
            if (norm == PRIMARY_STORAGE_ROOT) return ""
            if (norm.startsWith("$PRIMARY_STORAGE_ROOT/")) {
                return norm.removePrefix("$PRIMARY_STORAGE_ROOT/").trim('/')
            }
            if (norm.startsWith("/storage/")) {
                val afterStorage = norm.removePrefix("/storage/")
                if (afterStorage.equals("emulated", ignoreCase = true) ||
                    afterStorage.equals("self", ignoreCase = true) ||
                    afterStorage.startsWith("self/", ignoreCase = true)
                ) {
                    return null
                }
                if (afterStorage.startsWith("emulated/", ignoreCase = true)) {
                    val afterUser = afterStorage.substringAfter('/', "").substringAfter('/', "")
                    return afterUser.trim('/')
                }
                return afterStorage.substringAfter('/', "").trim('/')
            }
            return null
        }

        fun isPathMatchingFolder(filePath: String, folder: BackupFolder): Boolean {
            if (filePath.isBlank() || folder.path.isBlank()) return false
            val folderCanonical = normalizeStoragePath(folder.path)
            val fileCanonical = normalizeStoragePath(filePath)
            if (folderCanonical == "/" || fileCanonical == "/") return false

            val lastSlash = fileCanonical.lastIndexOf('/')
            if (lastSlash <= 0) return false
            val fileParent = fileCanonical.substring(0, lastSlash)

            if (fileParent == folderCanonical) return true
            if (folder.includeSubfolders && fileCanonical.startsWith("$folderCanonical/")) return true

            return false
        }

        @JvmOverloads
        fun isMediaStoreItemInFolder(
            filePath: String?,
            relativePath: String?,
            bucketDisplayName: String?,
            folder: BackupFolder,
            volumeName: String? = null
        ): Boolean {
            val folderCanonical = normalizeStoragePath(folder.path)

            if (!volumeName.isNullOrBlank()) {
                val expectedVol = resolveExpectedMediaStoreVolume(folderCanonical)
                if (expectedVol != null && !volumeName.trim().equals(expectedVol, ignoreCase = true)) {
                    return false
                }
            }

            if (!filePath.isNullOrBlank()) {
                return isPathMatchingFolder(filePath, folder)
            }

            if (!relativePath.isNullOrBlank()) {
                val rawRel = relativePath.trim().replace('\\', '/')
                val relSegments = rawRel.split('/').filter { it.isNotEmpty() }
                if (relSegments.any { it == "." || it == ".." }) {
                    return false
                }
                val relNorm = relSegments.joinToString("/").lowercase(Locale.US)
                val folderRel = extractRelativeFolderPath(folderCanonical)?.lowercase(Locale.US) ?: return false
                if (folderRel.isEmpty()) {
                    return folder.includeSubfolders || relNorm.isEmpty()
                }
                return relNorm == folderRel || (folder.includeSubfolders && relNorm.startsWith("$folderRel/"))
            }

            if (!bucketDisplayName.isNullOrBlank() && bucketDisplayName.trim().equals(folder.displayName.trim(), ignoreCase = true)) {
                return true
            }

            return false
        }
    }
}
