package com.reiux.lumen.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

object MediaFileReader {
    private const val TAG = "MediaFileReader"

    /**
     * Resolves and provides a readable File.
     * 1. Checks if targetFile can be read directly.
     * 2. Checks canonical file (resolves symlinks like /sdcard -> /storage/emulated/0).
     * 3. If direct read fails due to Scoped Storage, stages a copy in cacheDir from MediaStore.
     */
    fun getReadableFile(context: Context, filePath: String, preferredFileName: String? = null): File? {
        val targetFile = File(filePath)

        // 1. Direct file test
        try {
            if (targetFile.exists() && targetFile.isFile) {
                FileInputStream(targetFile).use {
                    return targetFile
                }
            }
        } catch (_: Exception) {}

        // 2. Canonical file test
        try {
            val canonical = targetFile.canonicalFile
            if (canonical.exists() && canonical.isFile) {
                FileInputStream(canonical).use {
                    return canonical
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback: Stage via ContentResolver / MediaStore into app cache
        val resolvedName = preferredFileName ?: targetFile.name
        val staged = stageFromMediaStoreOrUri(context, filePath, resolvedName)
        if (staged != null && staged.exists() && staged.length() > 0) {
            return staged
        }

        return null
    }

    fun openInputStream(context: Context, filePath: String): InputStream? {
        // 1. Direct file
        val file = File(filePath)
        try {
            if (file.exists() && file.isFile) {
                return FileInputStream(file)
            }
        } catch (_: Exception) {}

        // 2. Canonical file
        try {
            val canonical = file.canonicalFile
            if (canonical.exists() && canonical.isFile) {
                return FileInputStream(canonical)
            }
        } catch (_: Exception) {}

        // 3. Content URI
        if (filePath.startsWith("content://")) {
            try {
                return context.contentResolver.openInputStream(Uri.parse(filePath))
            } catch (_: Exception) {}
        }

        // 4. Resolve path through MediaStore
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

        // Fallback: case-insensitive match by DISPLAY_NAME (handles .HEIC vs .heic)
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

    private val supportedExts = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "dng", "cr2", "nef", "arw",
        "mp4", "mov", "mkv", "avi", "3gp", "webm"
    )

    fun scanMediaFiles(dir: File): List<File> {
        val result = mutableListOf<File>()
        if (!dir.exists() || !dir.isDirectory) return result
        
        // Skip hidden folders (folders starting with '.')
        if (dir.name.startsWith(".")) return result
        
        val files = dir.listFiles() ?: return result
        for (f in files) {
            // Traverse into subdirectories (if they are not hidden)
            if (f.isDirectory) {
                if (!f.name.startsWith(".")) {
                    result.addAll(scanMediaFiles(f))
                }
            } else if (f.isFile && f.length() > 0) {
                val name = f.name
                val ext = f.extension.lowercase()
                
                // Skip files specifically marked as Android trash (.trash-xxx...)
                // Skip any file that starts with a dot (hidden file)
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
