package com.reiux.lumen.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object FileHasher {
    suspend fun calculateSha1(file: File): Sha1Result = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val bytes = digest.digest()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        Sha1Result(bytes, base64)
    }

    suspend fun calculateSha1(context: Context, uri: Uri): Sha1Result = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-1")
        context.contentResolver.openInputStream(uri)?.buffered()?.use { stream ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        } ?: throw IllegalArgumentException("Cannot open URI: $uri")
        val bytes = digest.digest()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        Sha1Result(bytes, base64)
    }
}

data class Sha1Result(
    val bytes: ByteArray,
    val base64: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Sha1Result
        return bytes.contentEquals(other.bytes) && base64 == other.base64
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + base64.hashCode()
        return result
    }
}
