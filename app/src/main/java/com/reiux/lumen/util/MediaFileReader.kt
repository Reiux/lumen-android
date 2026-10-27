package com.reiux.lumen.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.reiux.lumen.data.model.BackupFolder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.ArrayDeque

data class ScannedFile(
    val file: File,
    val size: Long
)

data class ScanProgress(
    val filesFound: Int,
    val foldersScanned: Int,
    val currentFolderName: String,
    val isComplete: Boolean = false,
    val scannedFiles: List<ScannedFile> = emptyList()
)

object MediaFileReader {
    private const val TAG = "MediaFileReader"

    private val supportedExts = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "dng", "cr2", "nef", "arw",
        "mp4", "mov", "mkv", "avi", "3gp", "webm"
    )

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

            // 1. Fast Path: MediaStore Query
            val mediaStoreResults = queryMediaStoreForFolder(context, folder)
            if (mediaStoreResults != null && mediaStoreResults.isNotEmpty()) {
                LumenLogger.d(TAG) { "Fast path MediaStore query HIT for '${folder.displayName}': ${mediaStoreResults.size} files" }
                totalFoldersScanned++
                for (scanned in mediaStoreResults) {
                    currentCoroutineContext().ensureActive()
                    val canonical = try { scanned.file.canonicalPath } catch (_: Exception) { scanned.file.absolutePath }
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
                                    currentFolderName = folder.displayName,
                                    scannedFiles = allScannedFiles.toList()
                                )
                            )
                        }
                    }
                }
                continue
            }

            // 2. Iterative Filesystem Walk (Fallback)
            LumenLogger.d(TAG) { "Filesystem walk for '${folder.displayName}' (includeSubfolders=${folder.includeSubfolders})" }
            val rootDir = File(folder.path)
            if (!rootDir.exists() || !rootDir.isDirectory) continue

            val stack = ArrayDeque<Pair<File, Int>>()
            stack.push(rootDir to 0)

            var itemCountSinceCancelCheck = 0

            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val (currentDir, depth) = stack.pop()

                val canonicalDir = try { currentDir.canonicalPath } catch (_: Exception) { currentDir.absolutePath }
                if (!visitedCanonicalPaths.add(canonicalDir)) continue

                totalFoldersScanned++

                val files = currentDir.listFiles() ?: continue
                if (files.any { it.name.equals(".nomedia", ignoreCase = true) }) continue

                for (f in files) {
                    itemCountSinceCancelCheck++
                    if (itemCountSinceCancelCheck >= 200) {
                        itemCountSinceCancelCheck = 0
                        currentCoroutineContext().ensureActive()
                    }

                    if (f.isDirectory) {
                        val name = f.name
                        if (!name.startsWith(".") && folder.includeSubfolders && depth < 8) {
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
                                val canonical = try { f.canonicalPath } catch (_: Exception) { f.absolutePath }
                                if (processedFilePaths.add(canonical)) {
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
                                                currentFolderName = currentDir.name,
                                                scannedFiles = allScannedFiles.toList()
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

    private fun queryMediaStoreForFolder(context: Context, folder: BackupFolder): List<ScannedFile>? {
        val folderFile = File(folder.path)
        val folderPath = try { folderFile.canonicalPath } catch (_: Exception) { folder.path }

        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE
        )

        val pendingFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            " AND (${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.MediaColumns.IS_TRASHED} = 0)"
        } else ""

        val pathEscaped = folderPath.replace("'", "''").trimEnd('/')

        val selection = if (folder.includeSubfolders) {
            "(${MediaStore.MediaColumns.DATA} LIKE '$pathEscaped/%')$pendingFilter"
        } else {
            "(${MediaStore.MediaColumns.DATA} LIKE '$pathEscaped/%' AND ${MediaStore.MediaColumns.DATA} NOT LIKE '$pathEscaped/%/%')$pendingFilter"
        }

        val list = mutableListOf<ScannedFile>()

        try {
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
                    val path = cursor.getString(dataIdx) ?: continue
                    val size = cursor.getLong(sizeIdx)
                    if (size > 0L) {
                        val file = File(path)
                        val name = file.name
                        if (!name.startsWith(".") && !name.startsWith(".trash") && !name.startsWith(".pending")) {
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
                    val path = cursor.getString(dataIdx) ?: continue
                    val size = cursor.getLong(sizeIdx)
                    if (size > 0L) {
                        val file = File(path)
                        val name = file.name
                        if (!name.startsWith(".") && !name.startsWith(".trash") && !name.startsWith(".pending")) {
                            list.add(ScannedFile(file, size))
                        }
                    }
                }
            }

            return list
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
                return context.contentResolver.openInputStream(Uri.parse(filePath))
            } catch (_: Exception) {}
        }

        val contentUri = resolveToMediaStoreUri(context, filePath)
        if (contentUri != null) {
            try {
                return context.contentResolver.openInputStream(contentUri)
            } catch (_: Exception) {}
        }

        return null
    }

    private fun stageFromMediaStoreOrUri(context: Context, filePath: String, fileName: String): File? {
        val stream = openInputStream(context, filePath) ?: return null
        return try {
            val stagingDir = File(context.cacheDir, "staging").apply { mkdirs() }
            val tempFile = File(stagingDir, fileName)
            stream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                Log.d(TAG, "Staged unreadable media into cache: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
                tempFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed staging media $filePath: ${e.message}")
            null
        }
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

        val nameSelection = "(${MediaStore.MediaColumns.DISPLAY_NAME} = ? COLLATE NOCASE)$pendingFilter"
        val nameArgs = arrayOf(name)
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

    fun cleanupStagedFile(context: Context, file: File) {
        val stagingDir = File(context.cacheDir, "staging")
        if (file.parentFile?.absolutePath == stagingDir.absolutePath) {
            try {
                file.delete()
            } catch (_: Exception) {}
        }
    }

    fun scanMediaFiles(dir: File): List<File> {
        val result = mutableListOf<File>()
        if (!dir.exists() || !dir.isDirectory) return result
        if (dir.name.startsWith(".")) return result

        val files = dir.listFiles() ?: return result
        if (files.any { it.name.equals(".nomedia", ignoreCase = true) }) return result
        for (f in files) {
            if (f.isDirectory) {
                if (!f.name.startsWith(".")) {
                    result.addAll(scanMediaFiles(f))
                }
            } else if (f.isFile && f.length() > 0) {
                val name = f.name
                val ext = f.extension.lowercase()
                if (!name.startsWith(".trash") && !name.startsWith(".") && ext in supportedExts) {
                    result.add(f)
                }
            }
        }
        return result
    }

    fun stageUriFile(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val stagingDir = File(context.cacheDir, "staging").apply { mkdirs() }
            val tempFile = File(stagingDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) tempFile else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stage URI $uri: ${e.message}")
            null
        }
    }
}
