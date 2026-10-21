package com.reiux.lumen.data.network

import com.google.protobuf.ByteString
import com.reiux.lumen.proto.CommitTokenOuterClass.CommitToken
import com.reiux.lumen.proto.CommitUploadOuterClass.CommitUpload
import com.reiux.lumen.proto.CommitUploadResponseOuterClass.CommitUploadResponse
import com.reiux.lumen.proto.CreateMediaItems.CreateMediaItemsResponse
import com.reiux.lumen.proto.GetUploadTokenOuterClass.GetUploadToken
import com.reiux.lumen.proto.HashCheckOuterClass.HashCheck
import com.reiux.lumen.proto.RemoteMatchesOuterClass.RemoteMatches
import com.reiux.lumen.util.LumenLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

class GooglePhotosClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder().build()
) {
    private fun uncompressIfNeeded(bytes: ByteArray): ByteArray {
        if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            return try {
                ByteArrayInputStream(bytes).use { bais ->
                    GZIPInputStream(bais).use { gzis ->
                        gzis.readBytes()
                    }
                }
            } catch (e: Exception) {
                LumenLogger.w(TAG) { "GZIP decompression failed: ${e.message}" }
                bytes
            }
        }
        return bytes
    }

    private fun logNetwork(
        method: String,
        url: String,
        reqSize: Long,
        code: Int,
        respSize: Long,
        durationMs: Long,
        opId: String?,
        errBody: String? = null
    ) {
        val sanitizedUrl = LumenLogger.redact(url)
        if (code in 200..299) {
            LumenLogger.d("Network", opId = opId) {
                "[$method $code] $sanitizedUrl reqSize=${reqSize}B respSize=${respSize}B duration=${durationMs}ms"
            }
        } else {
            val trimmed = errBody?.take(500)?.replace("\n", " ") ?: ""
            LumenLogger.w("Network", opId = opId) {
                "[$method $code ERROR] $sanitizedUrl reqSize=${reqSize}B respSize=${respSize}B duration=${durationMs}ms errBody='$trimmed'"
            }
        }
    }

    suspend fun getUploadToken(
        bearerToken: String,
        sha1Base64: String,
        fileSizeBytes: Long,
        opId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val proto = GetUploadToken.newBuilder()
            .setF1(2)
            .setF2(2)
            .setF3(1)
            .setF4(3)
            .setFileSizeBytes(fileSizeBytes)
            .build()

        val protoBytes = proto.toByteArray()
        val requestBody = protoBytes.toRequestBody("application/x-protobuf".toMediaType())

        val request = Request.Builder()
            .url(INTERACTIVE_UPLOAD_ENDPOINT)
            .post(requestBody)
            .header("Content-Type", "application/x-protobuf")
            .header("Authorization", "Bearer $bearerToken")
            .header("X-Goog-Hash", "sha1=$sha1Base64")
            .header("X-Upload-Content-Length", fileSizeBytes.toString())
            .header("User-Agent", PHOTOS_USER_AGENT)
            .build()

        val start = System.currentTimeMillis()
        val response = httpClient.newCall(request).execute()
        val duration = System.currentTimeMillis() - start
        val respBytes = uncompressIfNeeded(response.body?.bytes() ?: ByteArray(0))

        logNetwork(
            method = "POST",
            url = INTERACTIVE_UPLOAD_ENDPOINT,
            reqSize = protoBytes.size.toLong(),
            code = response.code,
            respSize = respBytes.size.toLong(),
            durationMs = duration,
            opId = opId,
            errBody = if (!response.isSuccessful) respBytes.decodeToString() else null
        )

        if (!response.isSuccessful) {
            val errBody = respBytes.decodeToString()
            throw IOException("GetUploadToken failed HTTP ${response.code}: $errBody")
        }

        val uploadId = response.header("X-GUploader-UploadID")
            ?: throw IOException("Missing X-GUploader-UploadID in upload token response")
        uploadId
    }

    suspend fun findRemoteMediaByHash(
        bearerToken: String,
        sha1Bytes: ByteArray,
        opId: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val proto = HashCheck.newBuilder()
            .setField1(
                HashCheck.Container.newBuilder()
                    .setField1(
                        HashCheck.Container.Sha1Wrapper.newBuilder()
                            .setSha1Hash(ByteString.copyFrom(sha1Bytes))
                    )
                    .setField2(HashCheck.Container.DummyWrapper.newBuilder())
            )
            .build()

        val protoBytes = proto.toByteArray()
        val requestBody = protoBytes.toRequestBody("application/x-protobuf".toMediaType())

        val request = Request.Builder()
            .url(REMOTE_HASH_CHECK_ENDPOINT)
            .post(requestBody)
            .header("Content-Type", "application/x-protobuf")
            .header("Authorization", "Bearer $bearerToken")
            .header("User-Agent", PHOTOS_USER_AGENT)
            .build()

        val start = System.currentTimeMillis()
        val response = httpClient.newCall(request).execute()
        val duration = System.currentTimeMillis() - start
        val respBytes = uncompressIfNeeded(response.body?.bytes() ?: ByteArray(0))

        logNetwork(
            method = "POST",
            url = REMOTE_HASH_CHECK_ENDPOINT,
            reqSize = protoBytes.size.toLong(),
            code = response.code,
            respSize = respBytes.size.toLong(),
            durationMs = duration,
            opId = opId,
            errBody = if (!response.isSuccessful) respBytes.decodeToString() else null
        )

        if (!response.isSuccessful) {
            LumenLogger.w(TAG, opId = opId) { "Remote dedup check HTTP ${response.code} error" }
            return@withContext null
        }

        if (respBytes.isEmpty()) {
            LumenLogger.d(TAG, opId = opId) { "Remote dedup response empty" }
            return@withContext null
        }

        try {
            val matches = RemoteMatches.parseFrom(respBytes)
            val key = matches.field1?.field2?.field2?.mediaKey
            if (!key.isNullOrEmpty()) {
                LumenLogger.d(TAG, opId = opId) { "Remote dedup match FOUND: mediaKey=$key" }
                key
            } else {
                LumenLogger.d(TAG, opId = opId) { "Remote dedup check returned no mediaKey (not in Google Photos)" }
                null
            }
        } catch (e: Exception) {
            LumenLogger.w(TAG, opId = opId) { "RemoteMatches parse failed: ${e.message}" }
            null
        }
    }

    suspend fun uploadFile(
        uploadToken: String,
        file: File,
        bearerToken: String,
        opId: String? = null,
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ): CommitToken = withContext(Dispatchers.IO) {
        val totalBytes = file.length()
        val countingBody = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = totalBytes

            override fun writeTo(sink: BufferedSink) {
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var totalRead = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                        totalRead += read
                        onProgress?.invoke(totalRead, totalBytes)
                    }
                }
            }
        }

        val url = "$INTERACTIVE_UPLOAD_ENDPOINT?upload_id=$uploadToken"
        val request = Request.Builder()
            .url(url)
            .put(countingBody)
            .header("Authorization", "Bearer $bearerToken")
            .header("User-Agent", PHOTOS_USER_AGENT)
            .build()

        val start = System.currentTimeMillis()
        val response = httpClient.newCall(request).execute()
        val duration = System.currentTimeMillis() - start
        val respBytes = uncompressIfNeeded(response.body?.bytes() ?: ByteArray(0))

        logNetwork(
            method = "PUT",
            url = url,
            reqSize = totalBytes,
            code = response.code,
            respSize = respBytes.size.toLong(),
            durationMs = duration,
            opId = opId,
            errBody = if (!response.isSuccessful) respBytes.decodeToString() else null
        )

        if (!response.isSuccessful) {
            val errBody = respBytes.decodeToString()
            throw IOException("Upload chunk failed HTTP ${response.code}: $errBody")
        }

        if (respBytes.isEmpty()) throw IOException("Empty Scotty finalize response")
        CommitToken.parseFrom(respBytes)
    }

    suspend fun commitUpload(
        bearerToken: String,
        commitToken: CommitToken,
        fileName: String,
        sha1Bytes: ByteArray,
        uploadTimestampSec: Long,
        storageSaver: Boolean = false,
        useQuota: Boolean = false,
        opId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val quality = if (storageSaver && !useQuota) 1L else 3L
        val model = when {
            useQuota -> "Pixel 8"
            storageSaver -> "Pixel 2"
            else -> "Pixel XL"
        }
        val apiVersion = when {
            useQuota -> 34L
            else -> 28L
        }

        val proto = CommitUpload.newBuilder()
            .setField1(
                CommitUpload.MediaInfo.newBuilder()
                    .setField1(
                        CommitUpload.MediaInfo.TokenData.newBuilder()
                            .setField1(commitToken.field1)
                            .setField2(commitToken.field2)
                    )
                    .setFileName(fileName)
                    .setSha1Hash(ByteString.copyFrom(sha1Bytes))
                    .setField4(
                        CommitUpload.MediaInfo.TimestampData.newBuilder()
                            .setFileLastModifiedTimestamp(uploadTimestampSec)
                            .setField2(46000000L)
                    )
                    .setQuality(quality)
                    .setField10(1L)
            )
            .setField2(
                CommitUpload.DeviceInfo.newBuilder()
                    .setModel(model)
                    .setMake("Google")
                    .setAndroidApiVersion(apiVersion)
            )
            .setField3(ByteString.copyFrom(byteArrayOf(1, 3)))
            .build()

        val protoBytes = proto.toByteArray()
        val requestBody = protoBytes.toRequestBody("application/x-protobuf".toMediaType())

        val request = Request.Builder()
            .url(COMMIT_UPLOAD_ENDPOINT)
            .post(requestBody)
            .header("Content-Type", "application/x-protobuf")
            .header("Authorization", "Bearer $bearerToken")
            .header("x-goog-ext-173412678-bin", "CgcIAhClARgC")
            .header("x-goog-ext-174067345-bin", "CgIIAg==")
            .header("User-Agent", PHOTOS_USER_AGENT)
            .build()

        val start = System.currentTimeMillis()
        val response = httpClient.newCall(request).execute()
        val duration = System.currentTimeMillis() - start
        val respBytes = uncompressIfNeeded(response.body?.bytes() ?: ByteArray(0))

        logNetwork(
            method = "POST",
            url = COMMIT_UPLOAD_ENDPOINT,
            reqSize = protoBytes.size.toLong(),
            code = response.code,
            respSize = respBytes.size.toLong(),
            durationMs = duration,
            opId = opId,
            errBody = if (!response.isSuccessful) respBytes.decodeToString() else null
        )

        if (!response.isSuccessful) {
            val errBody = respBytes.decodeToString()
            throw IOException("Commit upload failed HTTP ${response.code}: $errBody")
        }

        if (respBytes.isEmpty()) throw IOException("Empty commit upload response")

        // Try CreateMediaItemsResponse first
        try {
            val cmResp = CreateMediaItemsResponse.parseFrom(respBytes)
            for (item in cmResp.itemList) {
                val mk = item.resultItem?.mediaKey
                if (!mk.isNullOrEmpty()) {
                    LumenLogger.d(TAG, opId = opId) { "commitUpload success (CreateMediaItems): mediaKey=$mk" }
                    return@withContext mk
                }
            }
        } catch (_: Exception) {}

        // Fallback to CommitUploadResponse
        try {
            val cuResp = CommitUploadResponse.parseFrom(respBytes)
            val mk = cuResp.field1?.field3?.mediaKey
            if (!mk.isNullOrEmpty()) {
                LumenLogger.d(TAG, opId = opId) { "commitUpload success (CommitUpload): mediaKey=$mk" }
                return@withContext mk
            }
        } catch (_: Exception) {}

        val fallbackKey = "uploaded_${System.currentTimeMillis()}"
        LumenLogger.d(TAG, opId = opId) { "commitUpload fallback generated key: $fallbackKey" }
        fallbackKey
    }

    companion object {
        private const val TAG = "GooglePhotosClient"
        const val PHOTOS_USER_AGENT = "com.google.android.apps.photos/49029607 (Linux; U; Android 9; Pixel XL; en_US; Build/PQ2A.190205.001; Cronet/127.0.6510.5) (gzip)"
        const val INTERACTIVE_UPLOAD_ENDPOINT = "https://photos.googleapis.com/data/upload/uploadmedia/interactive"
        const val REMOTE_HASH_CHECK_ENDPOINT = "https://photosdata-pa.googleapis.com/6439526531001121323/5084965799730810217"
        const val COMMIT_UPLOAD_ENDPOINT = "https://photosdata-pa.googleapis.com/6439526531001121323/16538846908252377752"
    }
}

