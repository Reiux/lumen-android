package com.reiux.lumen.util

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ScannedFile(
    val file: File,
    val size: Long,
    val originalFilePath: String? = null
)

data class ScanProgress(
    val filesFound: Int,
    val foldersScanned: Int,
    val currentFolderName: String,
    val isComplete: Boolean = false,
    val scannedFiles: List<ScannedFile> = emptyList()
)

data class LocalMediaFolder(
    val path: String,
    val displayName: String,
    val mediaCount: Int,
    val imageCount: Int,
    val videoCount: Int,
    val latestThumbnailUri: Uri?,
    val latestDateAddedSec: Long
)

object MediaFileReader {
    private const val TAG = "MediaFileReader"

    const val MAX_STAGED_FILE_BYTES = 1024L * 1024L * 1024L // 1 GiB per staged file
    const val MAX_TOTAL_STAGING_BYTES = 1536L * 1024L * 1024L // 1.5 GiB staging cap
    const val MIN_FREE_SPACE_RESERVE_BYTES = 256L * 1024L * 1024L // 256 MiB disk reserve
    const val MAX_AVATAR_BYTES = 10L * 1024L * 1024L // 10 MiB avatar cap

    private val stagingLock = Any()
    private val activeStagingDirs = ConcurrentHashMap.newKeySet<String>()

    private val supportedExts = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "dng", "cr2", "nef", "arw",
        "mp4", "mov", "mkv", "avi", "3gp", "webm"
    )

    private fun isSymbolicLink(file: File): Boolean = try {
        Files.isSymbolicLink(file.toPath())
    } catch (_: Exception) {
        false
    }

    private fun canonicalTrimmed(file: File): String = try {
        file.canonicalPath.trimEnd('/')
    } catch (_: Exception) {
        file.absolutePath.trimEnd('/')
    }

    private fun isWithinCanonicalRoot(entry: File, rootCanonical: String): Boolean {
        val canonical = canonicalTrimmed(entry)
        return canonical == rootCanonical || canonical.startsWith("$rootCanonical/")
    }

    @JvmOverloads
    fun copyStreamWithLimit(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long,
        onChunk: (() -> Unit)? = null
    ): Long {
        if (maxBytes < 0L) {
            throw IOException("Stream exceeded maximum allowed size ($maxBytes bytes)")
        }
        val buffer = ByteArray(16 * 1024)
        var totalCopied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            onChunk?.invoke()
            if (read == 0) continue
            if (read > maxBytes - totalCopied) {
                throw IOException("Stream exceeded maximum allowed size ($maxBytes bytes)")
            }
            output.write(buffer, 0, read)
            totalCopied += read
        }
        return totalCopied
    }

    internal fun computeDirectorySizeBytes(dir: File): Long {
        if (!dir.exists() || isSymbolicLink(dir)) return 0L
        if (dir.isFile) return dir.length().coerceAtLeast(0L)
        if (!dir.isDirectory) return 0L

        var total = 0L
        val stack = ArrayDeque<File>()
        stack.push(dir)
        while (stack.isNotEmpty()) {
            val current = stack.pop()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (isSymbolicLink(child)) continue
                if (child.isDirectory) {
                    stack.push(child)
                } else if (child.isFile) {
                    total += child.length().coerceAtLeast(0L)
                }
            }
        }
        return total
    }

    @JvmOverloads
    internal fun enforceStagingBudget(
        stagingRoot: File,
        requiredAdditionalBytes: Long = 0L,
        maxTotalStagingBytes: Long = MAX_TOTAL_STAGING_BYTES,
        minFreeReserveBytes: Long = MIN_FREE_SPACE_RESERVE_BYTES,
        protectedPaths: Set<String> = emptySet()
    ): Long {
        if (!stagingRoot.exists()) {
            stagingRoot.mkdirs()
        }
        val reqBytes = requiredAdditionalBytes.coerceAtLeast(0L)
        val protectedCanonical = protectedPaths.map { path ->
            val f = File(path)
            canonicalTrimmed(f)
        }.toSet()
        val protectedAbs = protectedPaths.map { it.trimEnd('/') }.toSet()

        fun isProtected(entry: File): Boolean {
            val canon = canonicalTrimmed(entry)
            val abs = entry.absolutePath.trimEnd('/')
            return canon in protectedCanonical ||
                abs in protectedAbs ||
                protectedCanonical.any { it.startsWith("$canon/") } ||
                protectedAbs.any { it.startsWith("$abs/") }
        }

        var currentBytes = computeDirectorySizeBytes(stagingRoot)
        if (currentBytes + reqBytes > maxTotalStagingBytes) {
            val candidates = (stagingRoot.listFiles() ?: emptyArray())
                .filter { !isProtected(it) }
                .sortedBy { it.lastModified() }
            for (candidate in candidates) {
                if (currentBytes + reqBytes <= maxTotalStagingBytes) break
                val entryBytes = computeDirectorySizeBytes(candidate)
                val deleted = if (candidate.isDirectory) {
                    candidate.deleteRecursively()
                } else {
                    candidate.delete()
                }
                if (deleted || !candidate.exists()) {
                    currentBytes = (currentBytes - entryBytes).coerceAtLeast(0L)
                }
            }
            currentBytes = computeDirectorySizeBytes(stagingRoot)
        }

        var remaining = maxTotalStagingBytes - currentBytes
        val usable = stagingRoot.usableSpace
        if (usable > 0L) {
            remaining = minOf(remaining, usable - minFreeReserveBytes)
        }
        return remaining
    }

    @JvmOverloads
    internal fun stageBoundedStreamToDir(
        uniqueStagingDir: File,
        fileName: String,
        occurrenceIndex: Int,
        input: InputStream,
        stagingRoot: File,
        maxFileBytes: Long = MAX_STAGED_FILE_BYTES,
        maxTotalStagingBytes: Long = MAX_TOTAL_STAGING_BYTES,
        minFreeReserveBytes: Long = 0L,
        onChunk: (() -> Unit)? = null
    ): File? {
        if (!uniqueStagingDir.exists()) {
            uniqueStagingDir.mkdirs()
        }
        val dirKey = canonicalTrimmed(uniqueStagingDir)
        activeStagingDirs.add(dirKey)
        var tempFile: File? = null
        return try {
            input.use { stream ->
                val remainingBudget = synchronized(stagingLock) {
                    enforceStagingBudget(
                        stagingRoot = stagingRoot,
                        requiredAdditionalBytes = 0L,
                        maxTotalStagingBytes = maxTotalStagingBytes,
                        minFreeReserveBytes = minFreeReserveBytes,
                        protectedPaths = activeStagingDirs.toSet()
                    )
                }
                val effectiveMax = minOf(maxFileBytes, remainingBudget)
                if (effectiveMax <= 0L) {
                    try { uniqueStagingDir.deleteRecursively() } catch (_: Exception) {}
                    return null
                }

                tempFile = createDistinctStagingFile(uniqueStagingDir, fileName, occurrenceIndex)
                FileOutputStream(tempFile).use { output ->
                    copyStreamWithLimit(stream, output, effectiveMax, onChunk)
                }
                if (tempFile.exists() && tempFile.length() > 0L) {
                    tempFile
                } else {
                    try { uniqueStagingDir.deleteRecursively() } catch (_: Exception) {}
                    null
                }
            }
        } catch (t: Throwable) {
            try { tempFile?.delete() } catch (_: Exception) {}
            try { uniqueStagingDir.deleteRecursively() } catch (_: Exception) {}
            if (t is CancellationException) throw t
            try { Log.w(TAG, "Failed staging bounded stream for $fileName: ${t.message}") } catch (_: Throwable) {}
            null
        } finally {
            activeStagingDirs.remove(dirKey)
        }
    }

    fun scanMediaFilesIncremental(
        context: Context,
        folders: List<BackupFolder>
    ): Flow<ScanProgress> = flow {
        var totalFilesFound = 0
        var totalFoldersScanned = 0
        val visitedCanonicalPaths = mutableSetOf<String>()
        val processedFilePaths = mutableSetOf<String>()
        val allScannedFiles = mutableListOf<ScannedFile>()

        var lastEmitTime = System.currentTimeMillis()

        for (folder in folders.filter { it.enabled }) {
            currentCoroutineContext().ensureActive()

            val rootDir = File(folder.path)
            val rootCanonical = canonicalTrimmed(rootDir)

            // 1. Fast Path: MediaStore Query
            val mediaStoreResults = queryMediaStoreForFolder(context, folder)
            var mediaStoreCountedFolder = false
            if (mediaStoreResults != null && mediaStoreResults.isNotEmpty()) {
                LumenLogger.d(TAG) { "Fast path MediaStore query HIT for '${folder.displayName}': ${mediaStoreResults.size} files" }
                totalFoldersScanned++
                mediaStoreCountedFolder = true
                for (scanned in mediaStoreResults) {
                    currentCoroutineContext().ensureActive()
                    if (isSymbolicLink(scanned.file)) continue
                    val canonical = canonicalTrimmed(scanned.file)
                    if (canonical != rootCanonical && !canonical.startsWith("$rootCanonical/")) continue
                    if (processedFilePaths.add(canonical)) {
                        allScannedFiles.add(scanned)
                        totalFilesFound++

                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime >= 200L) {
                            lastEmitTime = now
                            emit(
                                ScanProgress(
                                    filesFound = totalFilesFound,
                                    foldersScanned = totalFoldersScanned,
                                    currentFolderName = folder.displayName
                                )
                            )
                        }
                    }
                }
            }

            // 2. Iterative Filesystem Walk (picks up unindexed files or acts as full fallback)
            LumenLogger.d(TAG) { "Filesystem walk for '${folder.displayName}' (includeSubfolders=${folder.includeSubfolders})" }
            if (!rootDir.exists() || !rootDir.isDirectory || isSymbolicLink(rootDir)) continue

            val stack = ArrayDeque<Pair<File, Int>>()
            stack.push(rootDir to 0)

            var itemCountSinceCancelCheck = 0

            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val (currentDir, depth) = stack.pop()

                if (isSymbolicLink(currentDir)) continue
                val canonicalDir = canonicalTrimmed(currentDir)
                if (canonicalDir != rootCanonical && !canonicalDir.startsWith("$rootCanonical/")) continue
                if (!visitedCanonicalPaths.add(canonicalDir)) continue

                if (!(depth == 0 && mediaStoreCountedFolder)) {
                    totalFoldersScanned++
                }

                val files = currentDir.listFiles() ?: continue
                if (files.any { it.name.equals(".nomedia", ignoreCase = true) }) continue

                for (f in files) {
                    itemCountSinceCancelCheck++
                    if (itemCountSinceCancelCheck >= 200) {
                        itemCountSinceCancelCheck = 0
                        currentCoroutineContext().ensureActive()
                    }

                    if (isSymbolicLink(f)) continue

                    if (f.isDirectory) {
                        val name = f.name
                        if (!name.startsWith(".") && folder.includeSubfolders && depth < 8 && isWithinCanonicalRoot(f, rootCanonical)) {
                            stack.push(f to (depth + 1))
                        }
                    } else if (f.isFile) {
                        val name = f.name
                        val ext = f.extension.lowercase()

                        if (!name.startsWith(".") &&
                            !name.startsWith(".trash") &&
                            !name.startsWith(".pending") &&
                            ext in supportedExts
                        ) {
                            val size = f.length()
                            if (size > 0L) {
                                val canonical = canonicalTrimmed(f)
                                if ((canonical == rootCanonical || canonical.startsWith("$rootCanonical/")) &&
                                    processedFilePaths.add(canonical)
                                ) {
                                    val scanned = ScannedFile(f, size)
                                    allScannedFiles.add(scanned)
                                    totalFilesFound++

                                    val now = System.currentTimeMillis()
                                    if (now - lastEmitTime >= 200L) {
                                        lastEmitTime = now
                                        emit(
                                            ScanProgress(
                                                filesFound = totalFilesFound,
                                                foldersScanned = totalFoldersScanned,
                                                currentFolderName = currentDir.name
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        emit(
            ScanProgress(
                filesFound = totalFilesFound,
                foldersScanned = totalFoldersScanned,
                currentFolderName = "",
                isComplete = true,
                scannedFiles = allScannedFiles.toList()
            )
        )
    }

    private suspend fun queryMediaStoreForFolder(context: Context, folder: BackupFolder): List<ScannedFile>? {
        val folderFile = File(folder.path)
        val folderPath = try { folderFile.canonicalPath } catch (_: Exception) { folder.path }

        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE
        )

        val pendingFilter = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                " AND (${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.MediaColumns.IS_TRASHED} = 0)"
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
                " AND (${MediaStore.MediaColumns.IS_PENDING} = 0)"
            else -> ""
        }

        val pathEscaped = folderPath
            .trimEnd('/')
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
            .replace("'", "''")

        val selection = if (folder.includeSubfolders) {
            "(${MediaStore.MediaColumns.DATA} LIKE '$pathEscaped/%' ESCAPE '\\')$pendingFilter"
        } else {
            "(${MediaStore.MediaColumns.DATA} LIKE '$pathEscaped/%' ESCAPE '\\' AND ${MediaStore.MediaColumns.DATA} NOT LIKE '$pathEscaped/%/%' ESCAPE '\\')$pendingFilter"
        }

        val list = mutableListOf<ScannedFile>()

        try {
            var rowCount = 0
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext()) {
                    if (++rowCount >= 200) {
                        rowCount = 0
                        currentCoroutineContext().ensureActive()
                    }
                    val path = cursor.getString(dataIdx) ?: continue
                    val size = cursor.getLong(sizeIdx)
                    if (size > 0L) {
                        val file = File(path)
                        val name = file.name
                        // Match the recursive scanner: skip files whose extension isn't
                        // supported, or they get enqueued and then fail one by one.
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (!name.startsWith(".") && !name.startsWith(".trash") && !name.startsWith(".pending") && ext in supportedExts) {
                            list.add(ScannedFile(file, size))
                        }
                    }
                }
            }

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext()) {
                    if (++rowCount >= 200) {
                        rowCount = 0
                        currentCoroutineContext().ensureActive()
                    }
                    val path = cursor.getString(dataIdx) ?: continue
                    val size = cursor.getLong(sizeIdx)
                    if (size > 0L) {
                        val file = File(path)
                        val name = file.name
                        // Match the recursive scanner: skip files whose extension isn't
                        // supported, or they get enqueued and then fail one by one.
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (!name.startsWith(".") && !name.startsWith(".trash") && !name.startsWith(".pending") && ext in supportedExts) {
                            list.add(ScannedFile(file, size))
                        }
                    }
                }
            }

            return list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LumenLogger.w(TAG) { "MediaStore fast path query failed for '${folder.displayName}': ${e.message}" }
            return null
        }
    }

    fun getReadableFile(context: Context, filePath: String, preferredFileName: String? = null): File? {
        val targetFile = File(filePath)

        try {
            if (targetFile.exists() && targetFile.isFile) {
                FileInputStream(targetFile).use {
                    return targetFile
                }
            }
        } catch (_: Exception) {}

        try {
            val canonical = targetFile.canonicalFile
            if (canonical.exists() && canonical.isFile) {
                FileInputStream(canonical).use {
                    return canonical
                }
            }
        } catch (_: Exception) {}

        val resolvedName = preferredFileName ?: targetFile.name
        val staged = stageFromMediaStoreOrUri(context, filePath, resolvedName)
        if (staged != null && staged.exists() && staged.length() > 0) {
            return staged
        }

        return null
    }

    fun hasMediaLocationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    internal fun isDirectMediaStoreContentUri(scheme: String?, authority: String?, path: String?): Boolean {
        return scheme.equals("content", ignoreCase = true) &&
            authority.equals("media", ignoreCase = true) &&
            path?.contains("picker", ignoreCase = true) != true
    }

    internal fun isDirectMediaStoreContentUri(uri: Uri): Boolean {
        return isDirectMediaStoreContentUri(uri.scheme, uri.authority, uri.path)
    }

    fun resolveOriginalReadableUri(context: Context, uri: Uri): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !hasMediaLocationPermission(context)) {
            return uri
        }
        return try {
            when {
                isDirectMediaStoreContentUri(uri) -> MediaStore.setRequireOriginal(uri)
                uri.scheme.equals("content", ignoreCase = true) &&
                    uri.authority.equals("com.android.providers.media.documents", ignoreCase = true) -> {
                    val mediaUri = MediaStore.getMediaUri(context, uri)
                    if (mediaUri != null && isDirectMediaStoreContentUri(mediaUri)) {
                        MediaStore.setRequireOriginal(mediaUri)
                    } else {
                        uri
                    }
                }
                uri.scheme.equals("content", ignoreCase = true) &&
                    uri.authority.equals(MediaStore.AUTHORITY, ignoreCase = true) &&
                    uri.path?.contains("picker", ignoreCase = true) == true -> {
                    val details = resolveRealNameFromPickerUriDetails(context, uri)
                    val pickerSize = details?.pickerSize
                    val targetId = uri.lastPathSegment?.toLongOrNull()
                    if (targetId != null && pickerSize != null && pickerSize > 0L && pickerSize == details.mediaStoreSize) {
                        val collections = listOf(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        )
                        var verifiedMediaUri: Uri? = null
                        for (collectionUri in collections) {
                            try {
                                context.contentResolver.query(
                                    collectionUri,
                                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE),
                                    "${MediaStore.MediaColumns._ID} = ?",
                                    arrayOf(targetId.toString()),
                                    null
                                )?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                                        val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                                        val rowId = if (idIdx >= 0 && !cursor.isNull(idIdx)) cursor.getLong(idIdx) else -1L
                                        val rowSize = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else -1L
                                        if (rowId == targetId && rowSize == pickerSize) {
                                            verifiedMediaUri = ContentUris.withAppendedId(collectionUri, rowId)
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                            if (verifiedMediaUri != null) break
                        }
                        if (verifiedMediaUri != null) {
                            MediaStore.setRequireOriginal(verifiedMediaUri)
                        } else {
                            uri
                        }
                    } else {
                        uri
                    }
                }
                else -> uri
            }
        } catch (_: Exception) {
            uri
        }
    }

    fun openOriginalInputStream(context: Context, uri: Uri): InputStream? {
        val originalUri = resolveOriginalReadableUri(context, uri)
        if (originalUri != uri) {
            try {
                val originalStream = context.contentResolver.openInputStream(originalUri)
                if (originalStream != null) {
                    return originalStream
                }
                LumenLogger.d(TAG) { "setRequireOriginal open returned null; falling back to standard URI" }
            } catch (e: Exception) {
                LumenLogger.d(TAG) { "setRequireOriginal open failed (${e.javaClass.simpleName}); falling back to standard URI" }
            }
        }
        return context.contentResolver.openInputStream(uri)
    }

    fun openInputStream(context: Context, filePath: String): InputStream? {
        val file = File(filePath)
        try {
            if (file.exists() && file.isFile) {
                return FileInputStream(file)
            }
        } catch (_: Exception) {}

        try {
            val canonical = file.canonicalFile
            if (canonical.exists() && canonical.isFile) {
                return FileInputStream(canonical)
            }
        } catch (_: Exception) {}

        if (filePath.startsWith("content://")) {
            try {
                return openOriginalInputStream(context, Uri.parse(filePath))
            } catch (_: Exception) {}
        }

        val contentUri = resolveToMediaStoreUri(context, filePath)
        if (contentUri != null) {
            try {
                return openOriginalInputStream(context, contentUri)
            } catch (_: Exception) {}
        }

        return null
    }

    fun sanitizeFileName(candidate: String?): String? {
        if (candidate.isNullOrBlank()) return null
        val normalized = candidate.replace('\\', '/')
        val nameOnly = File(normalized).name
        if (nameOnly.isBlank() || nameOnly == "." || nameOnly == "..") return null
        return nameOnly
    }

    fun chooseDisplayName(
        displayName: String?,
        dataPath: String?,
        lastSegment: String?,
        mimeExt: String?
    ): String {
        val cleanDisplayName = sanitizeFileName(displayName)
        if (cleanDisplayName != null) {
            return cleanDisplayName
        }

        val cleanDataName = sanitizeFileName(dataPath)
        if (cleanDataName != null) {
            return cleanDataName
        }

        val rawSegment = sanitizeFileName(lastSegment) ?: "media_file"
        val cleanMimeExt = mimeExt?.lowercase()?.removePrefix(".")?.ifBlank { null }

        val hasExtension = rawSegment.contains('.') && rawSegment.substringAfterLast('.').isNotBlank()
        return if (hasExtension) {
            rawSegment
        } else if (cleanMimeExt != null) {
            "$rawSegment.$cleanMimeExt"
        } else {
            "$rawSegment.jpg"
        }
    }

    fun preferRealName(
        pickerName: String?,
        mediaStoreName: String?,
        pickerSize: Long?,
        mediaStoreSize: Long?
    ): String? {
        val cleanMediaStore = sanitizeFileName(mediaStoreName)
        if (pickerSize != null && pickerSize > 0 &&
            mediaStoreSize != null && mediaStoreSize > 0 &&
            pickerSize == mediaStoreSize &&
            !cleanMediaStore.isNullOrBlank()
        ) {
            return cleanMediaStore
        }
        return sanitizeFileName(pickerName)
    }

    data class PickerUriDetails(
        val mediaStoreName: String?,
        val pickerSize: Long?,
        val mediaStoreSize: Long?
    )

    fun resolveRealNameFromPickerUriDetails(context: Context, uri: Uri): PickerUriDetails? {
        if (uri.authority != "media" || uri.path?.contains("picker") != true) {
            return null
        }

        val id = uri.lastPathSegment?.toLongOrNull() ?: return null

        var pickerSize: Long? = null
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        pickerSize = cursor.getLong(idx)
                    }
                }
            }
        } catch (_: Exception) {}

        var msName: String? = null
        var msSize: Long? = null

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
                "${MediaStore.MediaColumns._ID} = ?",
                arrayOf(id.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (nameIdx >= 0) msName = cursor.getString(nameIdx)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) msSize = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}

        if (msName.isNullOrBlank()) {
            try {
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
                    "${MediaStore.MediaColumns._ID} = ?",
                    arrayOf(id.toString()),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                        if (nameIdx >= 0) msName = cursor.getString(nameIdx)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) msSize = cursor.getLong(sizeIdx)
                    }
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {}
        }

        return PickerUriDetails(msName, pickerSize, msSize)
    }

    fun resolveRealNameFromPickerUri(context: Context, uri: Uri): String? {
        val details = resolveRealNameFromPickerUriDetails(context, uri) ?: return null
        if (details.mediaStoreName != null && details.pickerSize != null && details.pickerSize > 0 && details.pickerSize == details.mediaStoreSize) {
            return details.mediaStoreName
        }
        return null
    }

    fun isSafeExternalMediaPath(context: Context, rawPath: String?): String? {
        if (rawPath.isNullOrBlank()) return null
        return try {
            val canonicalFile = File(rawPath).canonicalFile
            val canonicalPath = canonicalFile.path

            val blockedRoots = buildList {
                add(try { File(context.applicationInfo.dataDir).canonicalPath } catch (_: Exception) { context.applicationInfo.dataDir })
                add(try { context.filesDir.canonicalPath } catch (_: Exception) { context.filesDir.absolutePath })
                add(try { context.cacheDir.canonicalPath } catch (_: Exception) { context.cacheDir.absolutePath })
                context.externalCacheDir?.let {
                    add(try { it.canonicalPath } catch (_: Exception) { it.absolutePath })
                }
                context.getExternalFilesDir(null)?.let {
                    add(try { it.canonicalPath } catch (_: Exception) { it.absolutePath })
                }
                add("/data/data/")
                add("/data/user/")
                add("/data/user_de/")
            }

            if (blockedRoots.any { root ->
                    val normalizedRoot = root.trimEnd('/')
                    canonicalPath == normalizedRoot || canonicalPath.startsWith("$normalizedRoot/")
                }
            ) {
                LumenLogger.w(TAG) { "Blocked access to internal app path: $canonicalPath" }
                return null
            }

            if (!canonicalFile.exists() || !canonicalFile.isFile) return null
            val ext = canonicalFile.extension.lowercase()
            if (ext !in supportedExts) return null
            canonicalPath
        } catch (_: Exception) {
            null
        }
    }

    fun resolveRealFilePathFromUri(context: Context, uri: Uri): String? {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "file") {
            return isSafeExternalMediaPath(context, uri.path)
        }
        if (scheme != "content") return null

        val authority = uri.authority?.lowercase() ?: return null
        if (authority == "${context.packageName}.fileprovider".lowercase()) {
            return null
        }

        // Strictly restrict MediaStore _ID / _data resolution to system MediaStore / MediaDocumentsProvider.
        // Arbitrary content:// authorities (e.g. downloads, messaging apps) ending in numeric IDs must NEVER
        // be looked up against MediaStore._ID, or an unrelated local file can be uploaded/deleted.
        val isMediaStoreAuthority = authority == MediaStore.AUTHORITY.lowercase()
        val isMediaDocumentsAuthority = authority == "com.android.providers.media.documents"
        if (!isMediaStoreAuthority && !isMediaDocumentsAuthority) {
            return null
        }

        val isPickerUri = isMediaStoreAuthority && uri.path?.contains("picker") == true
        var requiredPickerSize: Long? = null
        if (isPickerUri) {
            // Cloud-backed Photo Picker URIs may have synthetic IDs that collide with local MediaStore _IDs;
            // require an exact byte-size match against the picker descriptor before trusting a local path.
            val details = resolveRealNameFromPickerUriDetails(context, uri)
            val pSize = details?.pickerSize
            if (pSize == null || pSize <= 0L || pSize != details.mediaStoreSize) {
                return null
            }
            requiredPickerSize = pSize
        }

        var targetId: Long? = null
        var candidateCollections: List<Uri> = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )

        if (isMediaDocumentsAuthority) {
            try {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val parts = docId.split(':')
                    if (parts.size == 2) {
                        targetId = parts[1].toLongOrNull()
                        candidateCollections = when (parts[0].lowercase()) {
                            "image" -> listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                            "video" -> listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                            else -> emptyList()
                        }
                    }
                }
            } catch (_: Exception) {}
        } else {
            targetId = uri.lastPathSegment?.toLongOrNull()
            val pathLower = uri.path?.lowercase().orEmpty()
            if (!isPickerUri) {
                candidateCollections = when {
                    pathLower.contains("/images/") -> listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    pathLower.contains("/video/") -> listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    else -> candidateCollections
                }
            }
        }

        val idProjection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE)
        if (targetId != null && candidateCollections.isNotEmpty()) {
            for (contentUri in candidateCollections) {
                try {
                    context.contentResolver.query(
                        contentUri,
                        idProjection,
                        "${MediaStore.MediaColumns._ID} = ?",
                        arrayOf(targetId.toString()),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            if (requiredPickerSize != null) {
                                val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                                val rowSize = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else -1L
                                if (rowSize != requiredPickerSize) {
                                    return@use
                                }
                            }
                            val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (idx >= 0) {
                                val safePath = isSafeExternalMediaPath(context, cursor.getString(idx))
                                if (safePath != null) {
                                    return safePath
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Only trust _data column directly when queried from non-picker system MediaStore URIs
        if (!isPickerUri) {
            val dataProjection = arrayOf(MediaStore.MediaColumns.DATA)
            try {
                context.contentResolver.query(uri, dataProjection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (idx >= 0) {
                            val safePath = isSafeExternalMediaPath(context, cursor.getString(idx))
                            if (safePath != null) {
                                return safePath
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var displayName: String? = null
        var dataPath: String? = null

        if (uri.scheme == "content") {
            // (1) Query ONLY OpenableColumns.DISPLAY_NAME in its OWN try/catch
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            displayName = cursor.getString(idx)
                        }
                    }
                }
            } catch (_: Exception) {}

            // Check if this is a picker URI and attempt to resolve real MediaStore name
            if (uri.authority == "media" && uri.path?.contains("picker") == true) {
                val pickerResolved = resolveRealNameFromPickerUriDetails(context, uri)
                if (pickerResolved != null) {
                    val msName = pickerResolved.mediaStoreName
                    val preferred = preferRealName(displayName, msName, pickerResolved.pickerSize, pickerResolved.mediaStoreSize)
                    if (!preferred.isNullOrBlank()) {
                        displayName = preferred
                    }
                }
            }

            // (2) Query MediaStore DATA ONLY if (1) gave nothing, in its OWN try/catch
            if (displayName.isNullOrBlank()) {
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.MediaColumns.DATA),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (idx >= 0) {
                                dataPath = cursor.getString(idx)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // (3) Fallback: uri.lastPathSegment + MimeTypeMap extension
        var lastSegment: String? = null
        try {
            lastSegment = uri.lastPathSegment
        } catch (_: Exception) {}

        var mimeExt: String? = null
        try {
            val mimeType = context.contentResolver.getType(uri)
            if (!mimeType.isNullOrBlank()) {
                mimeExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            }
        } catch (_: Exception) {}

        return chooseDisplayName(displayName, dataPath, lastSegment, mimeExt)
    }

    fun createDistinctStagingFile(stagingDir: File, fileName: String, occurrenceIndex: Int = 0): File {
        val cleanName = sanitizeFileName(fileName) ?: "media_file.jpg"
        val targetDir = if (occurrenceIndex <= 0) stagingDir else File(stagingDir, "dup_$occurrenceIndex")
        targetDir.mkdirs()
        return File(targetDir, cleanName)
    }

    fun resolveToMediaStoreUri(context: Context, filePath: String): Uri? {
        val file = File(filePath)
        val canonical = try { file.canonicalPath } catch (_: Exception) { filePath }
        val name = file.name

        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val pendingFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            " AND (${MediaStore.MediaColumns.IS_PENDING} = 0)"
        } else ""

        val dataSelection = "(${MediaStore.MediaColumns.DATA} = ? OR ${MediaStore.MediaColumns.DATA} = ?)$pendingFilter"
        val selectionArgs = arrayOf(filePath, canonical)

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                dataSelection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (_: Exception) {}

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                dataSelection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (_: Exception) {}

        val parentDir = file.parentFile
        val parentRelPath = parentDir?.let {
            val pCanon = try { it.canonicalPath } catch (_: Exception) { it.absolutePath }
            pCanon.substringAfter("/sdcard/").substringAfter("/storage/emulated/0/").trim('/')
        }
        val bucketName = parentDir?.name

        val (nameSelection, nameArgs) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !parentRelPath.isNullOrEmpty()) {
            "(${MediaStore.MediaColumns.DISPLAY_NAME} = ? COLLATE NOCASE AND (${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} = ?))$pendingFilter" to
                arrayOf(name, "$parentRelPath/", parentRelPath)
        } else if (!bucketName.isNullOrEmpty()) {
            "(${MediaStore.MediaColumns.DISPLAY_NAME} = ? COLLATE NOCASE AND ${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} = ? COLLATE NOCASE)$pendingFilter" to
                arrayOf(name, bucketName)
        } else {
            return null
        }

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                nameSelection,
                nameArgs,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (_: Exception) {}

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                nameSelection,
                nameArgs,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun queryUriDeclaredSize(context: Context, uri: Uri): Long? {
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        val size = cursor.getLong(idx)
                        if (size > 0L) return size
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                val len = afd.length
                if (len > 0L) return len
            }
        } catch (_: Exception) {}

        return null
    }

    private fun stageFromMediaStoreOrUri(
        context: Context,
        filePath: String,
        fileName: String,
        occurrenceIndex: Int = 0
    ): File? {
        val sourceUri = if (filePath.startsWith("content://")) {
            Uri.parse(filePath)
        } else {
            resolveToMediaStoreUri(context, filePath)
        }
        val declaredSize = sourceUri?.let { queryUriDeclaredSize(context, it) }
            ?: if (!filePath.startsWith("content://")) {
                File(filePath).takeIf { it.exists() && it.isFile && it.length() > 0L }?.length()
            } else {
                null
            }
        if (declaredSize != null && declaredSize > MAX_STAGED_FILE_BYTES) {
            Log.w(TAG, "Refusing to stage oversized media ($declaredSize bytes > $MAX_STAGED_FILE_BYTES): $filePath")
            return null
        }

        val stagingRoot = File(context.cacheDir, "staging").apply { mkdirs() }
        val uniqueStagingDir = File(stagingRoot, "stg_${UUID.randomUUID()}").apply { mkdirs() }
        val dirKey = canonicalTrimmed(uniqueStagingDir)
        activeStagingDirs.add(dirKey)
        return try {
            val remainingBudget = synchronized(stagingLock) {
                enforceStagingBudget(
                    stagingRoot = stagingRoot,
                    requiredAdditionalBytes = declaredSize ?: 0L,
                    maxTotalStagingBytes = MAX_TOTAL_STAGING_BYTES,
                    minFreeReserveBytes = MIN_FREE_SPACE_RESERVE_BYTES,
                    protectedPaths = activeStagingDirs.toSet()
                )
            }
            val effectiveMax = minOf(MAX_STAGED_FILE_BYTES, remainingBudget)
            if (effectiveMax <= 0L || (declaredSize != null && declaredSize > effectiveMax)) {
                try { uniqueStagingDir.deleteRecursively() } catch (_: Exception) {}
                return null
            }

            val stream = if (sourceUri != null) {
                try {
                    openOriginalInputStream(context, sourceUri)
                } catch (_: Exception) {
                    null
                } ?: openInputStream(context, filePath)
            } else {
                openInputStream(context, filePath)
            }
            if (stream == null) {
                try { uniqueStagingDir.deleteRecursively() } catch (_: Exception) {}
                return null
            }

            val staged = stageBoundedStreamToDir(
                uniqueStagingDir = uniqueStagingDir,
                fileName = fileName,
                occurrenceIndex = occurrenceIndex,
                input = stream,
                stagingRoot = stagingRoot,
                maxFileBytes = effectiveMax,
                maxTotalStagingBytes = MAX_TOTAL_STAGING_BYTES,
                minFreeReserveBytes = MIN_FREE_SPACE_RESERVE_BYTES
            )
            if (staged != null && staged.exists() && staged.length() > 0L) {
                Log.d(TAG, "Staged unreadable media into cache: ${staged.absolutePath} (${staged.length()} bytes)")
            }
            staged
        } catch (t: Throwable) {
            try { uniqueStagingDir.deleteRecursively() } catch (_: Exception) {}
            if (t is CancellationException) throw t
            Log.w(TAG, "Failed staging media $filePath: ${t.message}")
            null
        } finally {
            activeStagingDirs.remove(dirKey)
        }
    }

    fun cleanupStagedFile(context: Context, file: File) {
        val stagingDir = File(context.cacheDir, "staging")
        val stagingCanon = canonicalTrimmed(stagingDir)
        val stagingCanonPrefix = "$stagingCanon/"
        val fileCanon = canonicalTrimmed(file)
        if (fileCanon.startsWith(stagingCanonPrefix)) {
            try {
                file.delete()
                var parent = file.parentFile
                while (parent != null) {
                    val parentCanon = canonicalTrimmed(parent)
                    if (parentCanon == stagingCanon || !parentCanon.startsWith(stagingCanonPrefix)) {
                        break
                    }
                    val nextParent = parent.parentFile
                    if (parent.isDirectory && parent.listFiles()?.isEmpty() == true) {
                        parent.delete()
                    } else {
                        break
                    }
                    parent = nextParent
                }
            } catch (_: Exception) {}
        }
    }

    fun scanMediaFiles(dir: File): List<File> {
        if (!dir.exists() || !dir.isDirectory || isSymbolicLink(dir)) return emptyList()
        val rootCanonical = canonicalTrimmed(dir)
        return scanMediaFilesWithinRoot(dir, rootCanonical, mutableSetOf())
    }

    private fun scanMediaFilesWithinRoot(
        dir: File,
        rootCanonical: String,
        visitedDirs: MutableSet<String>
    ): List<File> {
        val result = mutableListOf<File>()
        if (!dir.exists() || !dir.isDirectory || isSymbolicLink(dir)) return result
        if (dir.name.startsWith(".")) return result
        val dirCanonical = canonicalTrimmed(dir)
        if (dirCanonical != rootCanonical && !dirCanonical.startsWith("$rootCanonical/")) return result
        if (!visitedDirs.add(dirCanonical)) return result

        val files = dir.listFiles() ?: return result
        if (files.any { it.name.equals(".nomedia", ignoreCase = true) }) return result
        for (f in files) {
            if (isSymbolicLink(f)) continue
            if (f.isDirectory) {
                if (!f.name.startsWith(".") && isWithinCanonicalRoot(f, rootCanonical)) {
                    result.addAll(scanMediaFilesWithinRoot(f, rootCanonical, visitedDirs))
                }
            } else if (f.isFile && f.length() > 0) {
                val name = f.name
                val ext = f.extension.lowercase()
                if (!name.startsWith(".trash") && !name.startsWith(".") && ext in supportedExts && isWithinCanonicalRoot(f, rootCanonical)) {
                    result.add(f)
                }
            }
        }
        return result
    }

    @JvmOverloads
    fun stageUriFile(
        context: Context,
        uri: Uri,
        fileName: String,
        occurrenceIndex: Int = 0,
        maxFileBytes: Long = MAX_STAGED_FILE_BYTES,
        maxTotalStagingBytes: Long = MAX_TOTAL_STAGING_BYTES,
        minFreeReserveBytes: Long = MIN_FREE_SPACE_RESERVE_BYTES
    ): File? {
        val scheme = uri.scheme?.lowercase()
        val authority = uri.authority?.lowercase()
        if (scheme != "content" || authority.isNullOrBlank() || authority == "${context.packageName}.fileprovider".lowercase()) {
            Log.w(TAG, "Refusing to stage untrusted URI: $uri")
            return null
        }

        val declaredSize = queryUriDeclaredSize(context, uri)
        if (declaredSize != null && declaredSize > maxFileBytes) {
            Log.w(TAG, "Refusing to stage oversized URI ($declaredSize bytes > $maxFileBytes): $uri")
            return null
        }

        val stagingRoot = File(context.cacheDir, "staging").apply { mkdirs() }
        val uniqueStagingDir = File(stagingRoot, "stg_${UUID.randomUUID()}").apply { mkdirs() }
        val dirKey = canonicalTrimmed(uniqueStagingDir)
        activeStagingDirs.add(dirKey)
        return try {
            val remainingBudget = synchronized(stagingLock) {
                enforceStagingBudget(
                    stagingRoot = stagingRoot,
                    requiredAdditionalBytes = declaredSize ?: 0L,
                    maxTotalStagingBytes = maxTotalStagingBytes,
                    minFreeReserveBytes = minFreeReserveBytes,
                    protectedPaths = activeStagingDirs.toSet()
                )
            }
            val effectiveMax = minOf(maxFileBytes, remainingBudget)
            if (effectiveMax <= 0L || (declaredSize != null && declaredSize > effectiveMax)) {
                try { uniqueStagingDir.deleteRecursively() } catch (_: Exception) {}
                return null
            }

            val stream = openOriginalInputStream(context, uri)
            if (stream == null) {
                try { uniqueStagingDir.deleteRecursively() } catch (_: Exception) {}
                return null
            }
            stageBoundedStreamToDir(
                uniqueStagingDir = uniqueStagingDir,
                fileName = fileName,
                occurrenceIndex = occurrenceIndex,
                input = stream,
                stagingRoot = stagingRoot,
                maxFileBytes = effectiveMax,
                maxTotalStagingBytes = maxTotalStagingBytes,
                minFreeReserveBytes = minFreeReserveBytes
            )
        } catch (t: Throwable) {
            try { uniqueStagingDir.deleteRecursively() } catch (_: Exception) {}
            if (t is CancellationException) throw t
            Log.w(TAG, "Failed to stage URI $uri: ${t.message}")
            null
        } finally {
            activeStagingDirs.remove(dirKey)
        }
    }

    internal fun shouldSkipDiscoveredFolderPath(folderPath: String): Boolean {
        val norm = folderPath.replace('\\', '/').trimEnd('/')
        if (norm.isBlank()) return true
        val segments = norm.split('/').filter { it.isNotEmpty() }
        if (segments.any { it.startsWith(".") }) return true
        val lower = norm.lowercase()
        if (lower.contains("/android/data") || lower.contains("/android/obb")) return true
        return false
    }

    internal fun resolveFolderPathFromMediaRow(
        dataPath: String?,
        relativePath: String?,
        externalRootPath: String
    ): String? {
        if (!dataPath.isNullOrBlank()) {
            val parent = File(dataPath).parentFile?.absolutePath
            if (!parent.isNullOrBlank()) return parent.trimEnd('/')
        }
        if (!relativePath.isNullOrBlank()) {
            val trimmedRel = relativePath.replace('\\', '/').trim('/')
            val root = externalRootPath.trimEnd('/')
            return if (trimmedRel.isEmpty()) root else "$root/$trimmedRel"
        }
        return null
    }

    internal fun toShortDisplayPath(folderPath: String): String {
        val clean = folderPath.replace('\\', '/').trimEnd('/')
        return clean
            .removePrefix("/storage/emulated/0/")
            .removePrefix("/sdcard/")
            .ifBlank { "Internal Storage" }
    }

    private data class MutableFolderBucket(
        val path: String,
        val displayName: String,
        var imageCount: Int = 0,
        var videoCount: Int = 0,
        var latestThumbnailUri: Uri? = null,
        var latestDateAddedSec: Long = 0L
    )

    suspend fun discoverLocalMediaFolders(context: Context): List<LocalMediaFolder> = withContext(Dispatchers.IO) {
        val buckets = LinkedHashMap<String, MutableFolderBucket>()
        val externalRoot = Environment.getExternalStorageDirectory().absolutePath

        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.VOLUME_NAME)
            }
        }.toTypedArray()

        val selection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                "(${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.MediaColumns.IS_TRASHED} = 0)"
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
                "(${MediaStore.MediaColumns.IS_PENDING} = 0)"
            else -> null
        }

        val sources = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to true,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to false
        )

        for ((collectionUri, isImage) in sources) {
            try {
                var rowCount = 0
                context.contentResolver.query(
                    collectionUri,
                    projection,
                    selection,
                    null,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else -1
                    val volCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
                    } else -1

                    while (cursor.moveToNext()) {
                        if (++rowCount >= 200) {
                            rowCount = 0
                            ensureActive()
                        }
                        val id = if (idCol >= 0) cursor.getLong(idCol) else -1L
                        val dataPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                        val fileName = if (nameCol >= 0) cursor.getString(nameCol) else dataPath?.substringAfterLast('/')
                        val bucketName = if (bucketCol >= 0) cursor.getString(bucketCol) else null
                        val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                        val relPath = if (relCol >= 0) cursor.getString(relCol) else null
                        val volumeName = if (volCol >= 0) cursor.getString(volCol) else null

                        val cleanName = fileName?.trim() ?: ""
                        if (cleanName.isEmpty() || cleanName.startsWith(".") || cleanName.startsWith(".trash") || cleanName.startsWith(".pending")) {
                            continue
                        }
                        val ext = if (cleanName.contains('.')) cleanName.substringAfterLast('.', "").lowercase() else ""
                        if (ext !in supportedExts) continue

                        val rowRoot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            !volumeName.isNullOrBlank() &&
                            !volumeName.equals(MediaStore.VOLUME_EXTERNAL_PRIMARY, ignoreCase = true)
                        ) {
                            "/storage/$volumeName"
                        } else {
                            externalRoot
                        }

                        val folderPath = resolveFolderPathFromMediaRow(dataPath, relPath, rowRoot) ?: continue
                        if (shouldSkipDiscoveredFolderPath(folderPath)) continue

                        val normalizedKey = SettingsRepository.normalizeFolderPath(folderPath)
                        val displayName = bucketName?.takeIf { it.isNotBlank() }
                            ?: File(folderPath).name.ifBlank { "Internal Storage" }

                        val bucket = buckets.getOrPut(normalizedKey) {
                            MutableFolderBucket(
                                path = folderPath,
                                displayName = displayName
                            )
                        }

                        if (isImage) bucket.imageCount++ else bucket.videoCount++
                        if (id >= 0 && (bucket.latestThumbnailUri == null || dateAdded > bucket.latestDateAddedSec)) {
                            bucket.latestThumbnailUri = ContentUris.withAppendedId(collectionUri, id)
                            bucket.latestDateAddedSec = dateAdded
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                LumenLogger.w(TAG) { "MediaStore discovery skipped ($collectionUri): permission denied" }
            } catch (e: Exception) {
                LumenLogger.w(TAG) { "MediaStore discovery error ($collectionUri): ${e.message}" }
            }
        }

        buckets.values
            .map { bucket ->
                LocalMediaFolder(
                    path = bucket.path,
                    displayName = bucket.displayName,
                    mediaCount = bucket.imageCount + bucket.videoCount,
                    imageCount = bucket.imageCount,
                    videoCount = bucket.videoCount,
                    latestThumbnailUri = bucket.latestThumbnailUri,
                    latestDateAddedSec = bucket.latestDateAddedSec
                )
            }
            .sortedByDescending { it.latestDateAddedSec }
    }
}
