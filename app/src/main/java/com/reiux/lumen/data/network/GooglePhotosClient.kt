package com.reiux.lumen.data.network

import com.google.protobuf.ByteString
import com.reiux.lumen.data.model.AuthFailureKind
import com.reiux.lumen.proto.AddMediaToAlbumOuterClass.AddMediaToAlbum
import com.reiux.lumen.proto.CommitTokenOuterClass.CommitToken
import com.reiux.lumen.proto.CommitUploadOuterClass.CommitUpload
import com.reiux.lumen.proto.CommitUploadResponseOuterClass.CommitUploadResponse
import com.reiux.lumen.proto.CreateAlbumOuterClass.CreateAlbum
import com.reiux.lumen.proto.CreateAlbumResponseOuterClass.CreateAlbumResponse
import com.reiux.lumen.proto.CreateMediaItems.CreateMediaItemsResponse
import com.reiux.lumen.proto.GetUploadTokenOuterClass.GetUploadToken
import com.reiux.lumen.proto.HashCheckOuterClass.HashCheck
import com.reiux.lumen.proto.RemoteMatchesOuterClass.RemoteMatches
import com.reiux.lumen.util.AuthFailureException
import com.reiux.lumen.util.LumenLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream

data class GoogleAlbum(
    val mediaKey: String,
    val title: String,
    val accountEmail: String? = null,
    val isMissing: Boolean = false
) {
    fun getSubtitle(): String {
        if (isMissing) {
            return "Missing in Google Photos"
        }
        val keyRegex = Regex("^AF1Qip[A-Za-z0-9_-]+$")
        return if (mediaKey.startsWith("AF1Qip") || keyRegex.matches(mediaKey)) {
            val suffix = if (mediaKey.length >= 6) mediaKey.takeLast(6) else mediaKey
            "Key ending ...$suffix"
        } else {
            "Created on first upload"
        }
    }
}

class GooglePhotosClient(
    httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 20
        })
        .connectionPool(okhttp3.ConnectionPool(20, 5, java.util.concurrent.TimeUnit.MINUTES))
        .build()
) {
    private val httpClient: OkHttpClient = if (!httpClient.followRedirects && !httpClient.followSslRedirects) {
        httpClient
    } else {
        httpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
    private fun readBoundedStream(stream: InputStream, maxBytes: Int = MAX_DECOMPRESSED_BYTES): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalRead = 0
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            totalRead += read
            if (totalRead > maxBytes) {
                throw IOException("Response exceeded maximum allowed size ($maxBytes bytes)")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun readBoundedResponseBytes(response: okhttp3.Response): ByteArray {
        val body = response.body ?: return ByteArray(0)
        val declaredLen = body.contentLength()
        if (declaredLen > MAX_DECOMPRESSED_BYTES) {
            throw IOException("Response exceeded maximum allowed size ($MAX_DECOMPRESSED_BYTES bytes)")
        }
        return PushbackInputStream(body.byteStream(), 2).use { pushback ->
            val b0 = pushback.read()
            if (b0 == -1) return@use ByteArray(0)
            val b1 = pushback.read()
            if (b1 != -1) pushback.unread(b1)
            pushback.unread(b0)

            val isGzip = (b0.toByte() == 0x1f.toByte()) && (b1 != -1 && b1.toByte() == 0x8b.toByte())
            if (isGzip) {
                try {
                    GZIPInputStream(pushback).use { gzip ->
                        readBoundedStream(gzip, MAX_DECOMPRESSED_BYTES)
                    }
                } catch (e: IOException) {
                    LumenLogger.w(TAG) { "GZIP decompression failed: ${e.message}" }
                    throw e
                } catch (e: Exception) {
                    LumenLogger.w(TAG) { "GZIP decompression failed: ${e.message}" }
                    throw IOException("Corrupted GZIP response: ${e.message}", e)
                }
            } else {
                readBoundedStream(pushback, MAX_DECOMPRESSED_BYTES)
            }
        }
    }

    private fun uncompressIfNeeded(bytes: ByteArray): ByteArray {
        if (bytes.size > MAX_DECOMPRESSED_BYTES) {
            throw IOException("Response exceeded maximum allowed size ($MAX_DECOMPRESSED_BYTES bytes)")
        }
        if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            return try {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { gzip ->
                    readBoundedStream(gzip, MAX_DECOMPRESSED_BYTES)
                }
            } catch (e: IOException) {
                LumenLogger.w(TAG) { "GZIP decompression failed: ${e.message}" }
                throw e
            } catch (e: Exception) {
                LumenLogger.w(TAG) { "GZIP decompression failed: ${e.message}" }
                throw IOException("Corrupted GZIP response: ${e.message}", e)
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
        opId: String? = null,
        errBody: String? = null
    ) {
        val redactedUrl = LumenLogger.redact(url)
        val msg = if (errBody != null) {
            "[$method $code ERROR] $redactedUrl reqSize=${reqSize}B respSize=${respSize}B duration=${durationMs}ms errBody='${LumenLogger.redact(errBody.take(200))}'"
        } else {
            "[$method $code] $redactedUrl reqSize=${reqSize}B respSize=${respSize}B duration=${durationMs}ms"
        }
        if (code in 200..299) {
            LumenLogger.d("Network", opId = opId) { msg }
        } else {
            LumenLogger.w("Network", opId = opId) { msg }
        }
    }

    private fun handleResponseError(response: okhttp3.Response, respBytes: ByteArray, endpointName: String): Nothing {
        val code = response.code
        val errBody = respBytes.decodeToString().take(300)
        when (code) {
            401 -> throw AuthFailureException(
                kind = AuthFailureKind.TOKEN_EXPIRED,
                rawDetail = "$endpointName HTTP 401: Authentication required"
            )
            403 -> {
                val retryHeader = parseRetryAfterSeconds(response.header("Retry-After"), 60L)
                when {
                    errBody.contains("RATE_LIMIT", ignoreCase = true) ||
                        errBody.contains("rateLimitExceeded", ignoreCase = true) ||
                        errBody.contains("userRateLimitExceeded", ignoreCase = true) -> {
                        throw AuthFailureException(
                            kind = AuthFailureKind.RATE_LIMITED,
                            retryAfterSec = retryHeader,
                            rawDetail = "$endpointName HTTP 403: Rate limit exceeded"
                        )
                    }
                    errBody.contains("AccountDisabled", ignoreCase = true) ||
                        errBody.contains("AccountRestricted", ignoreCase = true) ||
                        errBody.contains("ACCOUNT_DISABLED", ignoreCase = true) ||
                        errBody.contains("ACCOUNT_LOCKED", ignoreCase = true) -> {
                        throw AuthFailureException(
                            kind = AuthFailureKind.ACCOUNT_RESTRICTED,
                            rawDetail = "$endpointName HTTP 403: Account restricted"
                        )
                    }
                    errBody.contains("ACCESS_TOKEN_EXPIRED", ignoreCase = true) ||
                        errBody.contains("Token expired", ignoreCase = true) -> {
                        throw AuthFailureException(
                            kind = AuthFailureKind.TOKEN_EXPIRED,
                            rawDetail = "$endpointName HTTP 403: Token expired"
                        )
                    }
                    endpointName != "ListAlbums" && (
                        errBody.contains("UNAUTHENTICATED", ignoreCase = true) ||
                            errBody.contains("Invalid Credentials", ignoreCase = true)
                        ) -> {
                        throw AuthFailureException(
                            kind = AuthFailureKind.SIGN_IN_REQUIRED,
                            rawDetail = "$endpointName HTTP 403: Sign-in required"
                        )
                    }
                    else -> throw IOException("$endpointName failed HTTP 403: Forbidden")
                }
            }
            429 -> {
                val retryHeader = parseRetryAfterSeconds(response.header("Retry-After"), 30L)
                throw AuthFailureException(
                    kind = AuthFailureKind.RATE_LIMITED,
                    retryAfterSec = retryHeader,
                    rawDetail = "$endpointName HTTP 429: Rate limit exceeded"
                )
            }
            in 500..599 -> throw AuthFailureException(
                kind = AuthFailureKind.TRANSIENT,
                rawDetail = "$endpointName HTTP $code: Server error"
            )
            else -> {
                val notFoundHint = if (code == 404 ||
                    errBody.contains("NOT_FOUND") ||
                    errBody.contains("Requested entity was not found")
                ) {
                    ": Requested entity was not found (NOT_FOUND)"
                } else ""
                throw IOException("$endpointName failed HTTP $code$notFoundHint")
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
        httpClient.newCall(request).execute().use { response ->
            val respBytes = readBoundedResponseBytes(response)
            val duration = System.currentTimeMillis() - start

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
                handleResponseError(response, respBytes, "GetUploadToken")
            }

            val uploadId = response.header("X-GUploader-UploadID")
                ?: throw IOException("Upload ID missing in response headers")
            LumenLogger.d(TAG, opId = opId) { "Acquired Scotty upload session" }
            uploadId
        }
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
        val respBytes = httpClient.newCall(request).execute().use { response ->
            val bytes = readBoundedResponseBytes(response)
            val duration = System.currentTimeMillis() - start

            logNetwork(
                method = "POST",
                url = REMOTE_HASH_CHECK_ENDPOINT,
                reqSize = protoBytes.size.toLong(),
                code = response.code,
                respSize = bytes.size.toLong(),
                durationMs = duration,
                opId = opId,
                errBody = if (!response.isSuccessful) bytes.decodeToString() else null
            )

            if (!response.isSuccessful) {
                handleResponseError(response, bytes, "RemoteHashCheck")
            }
            bytes
        }

        if (respBytes.isEmpty()) {
            LumenLogger.d(TAG, opId = opId) { "Remote dedup response empty" }
            return@withContext null
        }

        try {
            val matches = RemoteMatches.parseFrom(respBytes)
            val key = matches.field1?.field2?.field2?.mediaKey
            if (!key.isNullOrEmpty()) {
                LumenLogger.d(TAG, opId = opId) { "Remote dedup match FOUND" }
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
        isPaused: (() -> Boolean)? = null,
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ): CommitToken = withContext(Dispatchers.IO) {
        val chunkSize = 512 * 1024 // 512 KiB chunk (multiple of 256 KiB for responsive mid-file pause)
        val totalBytes = file.length()
        val url = INTERACTIVE_UPLOAD_ENDPOINT.toHttpUrl()
            .newBuilder()
            .addQueryParameter("upload_id", uploadToken)
            .build()

        if (totalBytes == 0L) {
            while (isPaused?.invoke() == true) {
                currentCoroutineContext().ensureActive()
                delay(150)
            }

            val request = Request.Builder()
                .url(url)
                .put(ByteArray(0).toRequestBody("application/octet-stream".toMediaType()))
                .header("Authorization", "Bearer $bearerToken")
                .header("User-Agent", PHOTOS_USER_AGENT)
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Command", "upload, finalize")
                .header("X-Goog-Upload-Offset", "0")
                .build()

            val start = System.currentTimeMillis()
            val respBytes = httpClient.newCall(request).execute().use { response ->
                val bytes = readBoundedResponseBytes(response)
                val duration = System.currentTimeMillis() - start

                logNetwork(
                    method = "PUT",
                    url = INTERACTIVE_UPLOAD_ENDPOINT,
                    reqSize = 0L,
                    code = response.code,
                    respSize = bytes.size.toLong(),
                    durationMs = duration,
                    opId = opId,
                    errBody = if (!response.isSuccessful) bytes.decodeToString() else null
                )

                if (!response.isSuccessful) {
                    handleResponseError(response, bytes, "UploadChunk")
                }
                bytes
            }

            onProgress?.invoke(0L, 0L)
            if (respBytes.isEmpty()) throw IOException("Empty Scotty finalize response")
            return@withContext CommitToken.parseFrom(respBytes)
        }

        FileInputStream(file).use { input ->
            var currentOffset = 0L
            while (currentOffset < totalBytes) {
                currentCoroutineContext().ensureActive()

                while (isPaused?.invoke() == true) {
                    currentCoroutineContext().ensureActive()
                    delay(150)
                }

                val targetLen = minOf(chunkSize.toLong(), totalBytes - currentOffset).toInt()
                val chunkBuffer = ByteArray(targetLen)
                var chunkLen = 0
                while (chunkLen < targetLen) {
                    val read = input.read(chunkBuffer, chunkLen, targetLen - chunkLen)
                    if (read == -1) break
                    chunkLen += read
                }
                if (chunkLen == 0) {
                    throw IOException("Unexpected EOF reading ${file.name} at offset $currentOffset/$totalBytes")
                }

                val isLast = (currentOffset + chunkLen) >= totalBytes
                val command = if (isLast) "upload, finalize" else "upload"
                val chunkOffset = currentOffset

                val chunkBody = object : RequestBody() {
                    override fun contentType() = "application/octet-stream".toMediaType()
                    override fun contentLength() = chunkLen.toLong()

                    override fun writeTo(sink: BufferedSink) {
                        val sliceSize = 32 * 1024
                        var writtenInChunk = 0
                        while (writtenInChunk < chunkLen) {
                            val toWrite = minOf(sliceSize, chunkLen - writtenInChunk)
                            sink.write(chunkBuffer, writtenInChunk, toWrite)
                            writtenInChunk += toWrite
                            onProgress?.invoke(chunkOffset + writtenInChunk, totalBytes)
                        }
                    }
                }

                val request = Request.Builder()
                    .url(url)
                    .put(chunkBody)
                    .header("Authorization", "Bearer $bearerToken")
                    .header("User-Agent", PHOTOS_USER_AGENT)
                    .header("X-Goog-Upload-Protocol", "resumable")
                    .header("X-Goog-Upload-Command", command)
                    .header("X-Goog-Upload-Offset", chunkOffset.toString())
                    .build()

                val start = System.currentTimeMillis()
                val respBytes = httpClient.newCall(request).execute().use { response ->
                    val bytes = readBoundedResponseBytes(response)
                    val duration = System.currentTimeMillis() - start

                    logNetwork(
                        method = "PUT",
                        url = INTERACTIVE_UPLOAD_ENDPOINT,
                        reqSize = chunkLen.toLong(),
                        code = response.code,
                        respSize = bytes.size.toLong(),
                        durationMs = duration,
                        opId = opId,
                        errBody = if (!response.isSuccessful) bytes.decodeToString() else null
                    )

                    if (!response.isSuccessful) {
                        handleResponseError(response, bytes, "UploadChunk")
                    }
                    bytes
                }

                currentOffset += chunkLen
                onProgress?.invoke(currentOffset, totalBytes)

                if (isLast) {
                    if (respBytes.isEmpty()) throw IOException("Empty Scotty finalize response")
                    return@withContext CommitToken.parseFrom(respBytes)
                }
            }
        }

        throw IOException("Unexpected end of upload loop")
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
        val respBytes = httpClient.newCall(request).execute().use { response ->
            val bytes = readBoundedResponseBytes(response)
            val duration = System.currentTimeMillis() - start

            logNetwork(
                method = "POST",
                url = COMMIT_UPLOAD_ENDPOINT,
                reqSize = protoBytes.size.toLong(),
                code = response.code,
                respSize = bytes.size.toLong(),
                durationMs = duration,
                opId = opId,
                errBody = if (!response.isSuccessful) bytes.decodeToString() else null
            )

            if (!response.isSuccessful) {
                handleResponseError(response, bytes, "CommitUpload")
            }
            bytes
        }

        if (respBytes.isEmpty()) throw IOException("Empty commit upload response")

        try {
            val cmResp = CreateMediaItemsResponse.parseFrom(respBytes)
            for (item in cmResp.itemList) {
                val mk = item.resultItem?.mediaKey
                if (!mk.isNullOrEmpty()) {
                    LumenLogger.d(TAG, opId = opId) { "commitUpload success (CreateMediaItems)" }
                    return@withContext mk
                }
            }
        } catch (_: Exception) {}

        try {
            val cuResp = CommitUploadResponse.parseFrom(respBytes)
            val mk = cuResp.field1?.field3?.mediaKey
            if (!mk.isNullOrEmpty()) {
                LumenLogger.d(TAG, opId = opId) { "commitUpload success (CommitUpload)" }
                return@withContext mk
            }
        } catch (_: Exception) {}

        LumenLogger.e(TAG, opId = opId) { "commitUpload response did not contain a valid mediaKey (respSize=${respBytes.size}B)" }
        throw IOException("CommitUpload failed: response missing mediaKey")
    }

    suspend fun createAlbum(
        bearerToken: String,
        albumName: String,
        initialMediaKey: String? = null,
        model: String = "Pixel XL",
        opId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val builder = CreateAlbum.newBuilder()
            .setAlbumName(albumName)
            .setTimestamp(System.currentTimeMillis() / 1000L)
            .setField3(1L)
            .setField6(CreateAlbum.field6_type.newBuilder())
            .setField7(CreateAlbum.field7_type.newBuilder().setField1(3L))
            .setDeviceInfo(
                CreateAlbum.field8_type.newBuilder()
                    .setModel(model)
                    .setMake("Google")
                    .setAndroidApiVersion(28L)
            )

        if (!initialMediaKey.isNullOrBlank()) {
            val mediaKeyItem = CreateAlbum.field4_type.newBuilder()
                .setField1(
                    CreateAlbum.field4_type.field1_type.newBuilder()
                        .setMediaKey(initialMediaKey)
                )
                .build()
            builder.addMediaKeys(mediaKeyItem)
        }

        val protoBytes = builder.build().toByteArray()
        val requestBody = protoBytes.toRequestBody("application/x-protobuf".toMediaType())

        val request = Request.Builder()
            .url(CREATE_ALBUM_ENDPOINT)
            .post(requestBody)
            .header("Content-Type", "application/x-protobuf")
            .header("Authorization", "Bearer $bearerToken")
            .header("User-Agent", PHOTOS_USER_AGENT)
            .build()

        val start = System.currentTimeMillis()
        val respBytes = httpClient.newCall(request).execute().use { response ->
            val bytes = readBoundedResponseBytes(response)
            val duration = System.currentTimeMillis() - start

            logNetwork(
                method = "POST",
                url = CREATE_ALBUM_ENDPOINT,
                reqSize = protoBytes.size.toLong(),
                code = response.code,
                respSize = bytes.size.toLong(),
                durationMs = duration,
                opId = opId,
                errBody = if (!response.isSuccessful) bytes.decodeToString() else null
            )

            if (!response.isSuccessful) {
                handleResponseError(response, bytes, "CreateAlbum")
            }
            bytes
        }

        val resp = CreateAlbumResponse.parseFrom(respBytes)
        val albumMediaKey = resp.field1?.albumMediaKey
        if (albumMediaKey.isNullOrEmpty()) {
            throw IOException("Failed to extract albumMediaKey from CreateAlbum response")
        }
        LumenLogger.d(TAG, opId = opId) { "createAlbum success (hasInitialMedia=${!initialMediaKey.isNullOrBlank()})" }
        albumMediaKey
    }

    suspend fun addMediaToAlbum(
        bearerToken: String,
        albumMediaKey: String,
        mediaKeys: List<String>,
        model: String = "Pixel XL",
        opId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (mediaKeys.isEmpty()) return@withContext true

        val proto = AddMediaToAlbum.newBuilder()
            .addAllMediaKeys(mediaKeys)
            .setAlbumMediaKey(albumMediaKey)
            .setField5(AddMediaToAlbum.field5_type.newBuilder().setField1(2L))
            .setDeviceInfo(
                AddMediaToAlbum.field6_type.newBuilder()
                    .setModel(model)
                    .setMake("Google")
                    .setAndroidApiVersion(28L)
            )
            .setTimestamp(System.currentTimeMillis() / 1000L)
            .build()

        val protoBytes = proto.toByteArray()
        val requestBody = protoBytes.toRequestBody("application/x-protobuf".toMediaType())

        val request = Request.Builder()
            .url(ADD_MEDIA_TO_ALBUM_ENDPOINT)
            .post(requestBody)
            .header("Content-Type", "application/x-protobuf")
            .header("Authorization", "Bearer $bearerToken")
            .header("User-Agent", PHOTOS_USER_AGENT)
            .build()

        val start = System.currentTimeMillis()
        httpClient.newCall(request).execute().use { response ->
            val respBytes = readBoundedResponseBytes(response)
            val duration = System.currentTimeMillis() - start

            logNetwork(
                method = "POST",
                url = ADD_MEDIA_TO_ALBUM_ENDPOINT,
                reqSize = protoBytes.size.toLong(),
                code = response.code,
                respSize = respBytes.size.toLong(),
                durationMs = duration,
                opId = opId,
                errBody = if (!response.isSuccessful) respBytes.decodeToString() else null
            )

            if (!response.isSuccessful) {
                handleResponseError(response, respBytes, "AddMediaToAlbum")
            }
        }
        LumenLogger.d(TAG, opId = opId) { "addMediaToAlbum success: count=${mediaKeys.size}" }
        true
    }

    fun resolveAlbumInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""

        val keyRegex = Regex("AF1Qip[A-Za-z0-9_-]+")

        // 1. Direct raw media key check
        if (trimmed.startsWith("AF1Qip") || keyRegex.matches(trimmed)) {
            val match = keyRegex.find(trimmed)
            if (match != null) return match.value
        }

        // 2. Extracted from a URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.contains("photos.google.com") || trimmed.contains("goo.gl")) {
            val match = keyRegex.find(trimmed)
            if (match != null) {
                LumenLogger.d(TAG) { "Extracted album key from URL" }
                return match.value
            }
        }

        return trimmed
    }

    suspend fun listAlbums(bearerToken: String, opId: String? = null): List<GoogleAlbum> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://photoslibrary.googleapis.com/v1/albums?pageSize=50")
                .get()
                .header("Authorization", "Bearer $bearerToken")
                .header("User-Agent", PHOTOS_USER_AGENT)
                .build()

            val start = System.currentTimeMillis()
            val respBytes = httpClient.newCall(request).execute().use { response ->
                val bytes = readBoundedResponseBytes(response)
                val duration = System.currentTimeMillis() - start

                logNetwork(
                    method = "GET",
                    url = "https://photoslibrary.googleapis.com/v1/albums",
                    reqSize = 0L,
                    code = response.code,
                    respSize = bytes.size.toLong(),
                    durationMs = duration,
                    opId = opId,
                    errBody = if (!response.isSuccessful) bytes.decodeToString() else null
                )

                if (!response.isSuccessful) {
                    handleResponseError(response, bytes, "ListAlbums")
                }
                bytes
            }

            val jsonStr = respBytes.decodeToString()
            val jsonObj = org.json.JSONObject(jsonStr)
            val albumsArr = jsonObj.optJSONArray("albums") ?: return@withContext emptyList()

            val result = mutableListOf<GoogleAlbum>()
            for (i in 0 until albumsArr.length()) {
                val a = albumsArr.getJSONObject(i)
                val id = a.optString("id")
                val title = a.optString("title")
                if (id.isNotEmpty() && title.isNotEmpty()) {
                    result.add(GoogleAlbum(id, title))
                }
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthFailureException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            LumenLogger.w(TAG, opId = opId) { "listAlbums parse failed: ${e.message}" }
            emptyList()
        }
    }

    companion object {
        private const val TAG = "GooglePhotosClient"
        private const val MAX_DECOMPRESSED_BYTES = 16 * 1024 * 1024 // 16 MB limit against zip bombs / oversized payloads
        const val PHOTOS_USER_AGENT = "com.google.android.apps.photos/49029607 (Linux; U; Android 9; Pixel XL; en_US; Build/PQ2A.190205.001; Cronet/127.0.6510.5) (gzip)"
        const val INTERACTIVE_UPLOAD_ENDPOINT = "https://photos.googleapis.com/data/upload/uploadmedia/interactive"
        const val REMOTE_HASH_CHECK_ENDPOINT = "https://photosdata-pa.googleapis.com/6439526531001121323/5084965799730810217"
        const val COMMIT_UPLOAD_ENDPOINT = "https://photosdata-pa.googleapis.com/6439526531001121323/16538846908252377752"
        const val CREATE_ALBUM_ENDPOINT = "https://photosdata-pa.googleapis.com/6439526531001121323/8386163679468898444"
        const val ADD_MEDIA_TO_ALBUM_ENDPOINT = "https://photosdata-pa.googleapis.com/6439526531001121323/484917746253879292"

        internal fun parseRetryAfterSeconds(headerValue: String?, defaultSeconds: Long): Long {
            val parsed = headerValue?.trim()?.toLongOrNull() ?: defaultSeconds
            return parsed.coerceIn(1L, 86400L)
        }
    }
}
