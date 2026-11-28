package com.reiux.lumen.data.repository

import com.reiux.lumen.BuildConfig
import com.reiux.lumen.data.model.UpdateInfo
import com.reiux.lumen.util.LumenLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class UpdateRepository(
    httpClient: OkHttpClient = defaultNoRedirectClient
) {
    private val noRedirectClient: OkHttpClient = if (!httpClient.followRedirects && !httpClient.followSslRedirects) {
        httpClient
    } else {
        httpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Lumen-Android")
                .build()

            val body = noRedirectClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LumenLogger.w("Updater") { "Failed to fetch updates: HTTP ${response.code}" }
                    return@withContext null
                }
                readBoundedUtf8String(response.body, MAX_RELEASE_JSON_BYTES) ?: return@withContext null
            }
            val release = JSONObject(body)

            val rawTag = release.optString("tag_name", "").trim()
            val latestVersion = rawTag.replaceFirst(Regex("^[vV]"), "").trim()
            if (latestVersion.isEmpty() || latestVersion.contains('/') || latestVersion.contains('\\') || latestVersion.contains("..")) {
                LumenLogger.w("Updater") { "Ignoring malformed release tag_name" }
                return@withContext null
            }
            val releaseNotes = release.optString("body", "")

            val assets = release.optJSONArray("assets") ?: JSONArray()
            var selectedAsset: JSONObject? = null
            val expectedApk = if (BuildConfig.DEBUG) "lumen-test-debug.apk" else "lumen-release.apk"

            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if ((name == expectedApk || (BuildConfig.DEBUG && name == "lumen-dev-debug.apk")) &&
                    isAllowedInitialDownloadUrl(url)
                ) {
                    selectedAsset = asset
                    break
                }
            }

            if (selectedAsset == null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val url = asset.optString("browser_download_url", "")
                    val isDebugAsset = name.contains("debug", ignoreCase = true)
                    if (name.endsWith(".apk", ignoreCase = true) &&
                        (BuildConfig.DEBUG || !isDebugAsset) &&
                        isAllowedInitialDownloadUrl(url)
                    ) {
                        selectedAsset = asset
                        break
                    }
                }
            }

            val downloadUrl = selectedAsset?.optString("browser_download_url", "")?.takeIf {
                isAllowedInitialDownloadUrl(it)
            } ?: ""

            var resolvedSha256: String? = null
            if (selectedAsset != null && downloadUrl.isNotEmpty()) {
                val assetName = selectedAsset.optString("name", "")
                val sha256Hex = extractExpectedSha256(selectedAsset, assetName, releaseNotes)
                resolvedSha256 = sha256Hex
                if (sha256Hex != null) {
                    expectedSha256ByUrl[downloadUrl] = sha256Hex
                } else {
                    expectedSha256ByUrl.remove(downloadUrl)
                }
            }

            val hasUpdate = downloadUrl.isNotEmpty() && isNewerVersion(currentVersion, latestVersion)

            LumenLogger.i("Updater") { "Update check complete. Latest: $latestVersion, Current: $currentVersion, HasUpdate: $hasUpdate" }

            UpdateInfo(
                versionName = latestVersion,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl,
                isUpdateAvailable = hasUpdate,
                expectedSha256 = resolvedSha256
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LumenLogger.e("Updater", tr = e) { "Error checking for updates: ${e.message}" }
            null
        }
    }

    @JvmOverloads
    fun downloadUpdate(
        url: String,
        destFile: File,
        expectedSha256Override: String? = null
    ): Flow<Int> = flow {
        if (!isAllowedInitialDownloadUrl(url)) {
            throw SecurityException("Untrusted or insecure download URL: $url")
        }

        val expectedSha256 = if (expectedSha256Override != null) {
            val trimmed = expectedSha256Override.trim()
            if (!isValidSha256Hex(trimmed)) {
                throw SecurityException("Invalid expected SHA-256 digest")
            }
            trimmed.lowercase(Locale.US)
        } else {
            expectedSha256ByUrl[url]
        }
        val parentDir = destFile.parentFile?.apply { mkdirs() }
        val tempFile = if (parentDir != null) {
            File(parentDir, "${destFile.name}.part")
        } else {
            File("${destFile.absolutePath}.part")
        }

        var currentUrl: HttpUrl = url.toHttpUrlOrNull()
            ?: throw SecurityException("Malformed download URL: $url")
        var redirectCount = 0
        var touchedDest = false

        try {
            while (true) {
                val request = Request.Builder().url(currentUrl).get().build()
                var nextRedirect: HttpUrl? = null

                noRedirectClient.newCall(request).execute().use { response ->
                    if (response.code in REDIRECT_STATUS_CODES) {
                        if (redirectCount >= MAX_REDIRECTS) {
                            throw IOException("Too many redirects while downloading update")
                        }
                        val location = response.header("Location")
                            ?: throw IOException("Redirect missing Location header")
                        val resolved = response.request.url.resolve(location)
                            ?: throw SecurityException("Invalid redirect Location: $location")
                        if (!isAllowedRedirectUrl(resolved)) {
                            throw SecurityException("Untrusted redirect URL host or path: ${resolved.host}")
                        }
                        nextRedirect = resolved
                        redirectCount++
                    } else {
                        if (!response.isSuccessful) {
                            throw IOException("Download failed with HTTP ${response.code}")
                        }
                        val body = response.body ?: throw IOException("Empty response body")
                        val contentLength = body.contentLength()
                        if (contentLength > MAX_APK_DOWNLOAD_BYTES) {
                            throw IOException("Update APK exceeded maximum allowed size ($MAX_APK_DOWNLOAD_BYTES bytes)")
                        }
                        val sha256Digest = MessageDigest.getInstance("SHA-256")

                        var bytesCopied = 0L
                        body.byteStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                val buffer = ByteArray(8 * 1024)
                                var read: Int
                                var lastProgress = 0

                                while (input.read(buffer).also { read = it } >= 0) {
                                    if (read > MAX_APK_DOWNLOAD_BYTES - bytesCopied) {
                                        throw IOException("Update APK exceeded maximum allowed size ($MAX_APK_DOWNLOAD_BYTES bytes)")
                                    }
                                    output.write(buffer, 0, read)
                                    sha256Digest.update(buffer, 0, read)
                                    bytesCopied += read

                                    if (contentLength > 0) {
                                        val progress = ((bytesCopied * 100) / contentLength).toInt().coerceIn(0, 99)
                                        if (progress != lastProgress) {
                                            lastProgress = progress
                                            emit(progress)
                                        }
                                    }
                                }
                            }
                        }

                        if (bytesCopied <= 0L) {
                            throw IOException("Downloaded update APK is empty")
                        }

                        if (!expectedSha256.isNullOrBlank()) {
                            val actualSha256 = sha256Digest.digest().joinToString("") { "%02x".format(it) }
                            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                                tempFile.delete()
                                throw SecurityException("APK SHA-256 digest mismatch")
                            }
                        }
                    }
                }

                val redirectTarget = nextRedirect
                if (redirectTarget != null) {
                    currentUrl = redirectTarget
                } else {
                    break
                }
            }

            if (destFile.exists() && !destFile.delete()) {
                throw IOException("Could not replace existing update APK")
            }
            touchedDest = true
            if (!tempFile.renameTo(destFile)) {
                tempFile.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.delete()
            }
            touchedDest = false
            emit(100)
        } catch (t: Throwable) {
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Exception) {}
            if (touchedDest) {
                try {
                    if (destFile.exists()) destFile.delete()
                } catch (_: Exception) {}
            }
            throw t
        }
    }.flowOn(Dispatchers.IO)

    private fun isNewerVersion(current: String, latest: String): Boolean =
        Companion.isNewerVersion(current, latest)

    companion object {
        /** How often the background worker polls GitHub for a new release. */
        const val BACKGROUND_CHECK_INTERVAL_HOURS = 4L
        const val UPDATE_DISMISS_SNOOZE_MILLIS = 24L * 60 * 60 * 1000
        const val MAX_RELEASE_JSON_BYTES = 2 * 1024 * 1024
        const val MAX_APK_DOWNLOAD_BYTES = 256L * 1024L * 1024L

        private val defaultNoRedirectClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }

        private const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/Reiux/lumen-android/releases/latest"
        private const val ALLOWED_RELEASE_PATH_PREFIX = "/Reiux/lumen-android/releases/download/"
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        private val ALLOWED_REDIRECT_HOSTS = setOf(
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com"
        )
        private val SHA256_HEX_REGEX = Regex("^[0-9a-fA-F]{64}$")
        private val expectedSha256ByUrl = ConcurrentHashMap<String, String>()

        internal fun isValidSha256Hex(value: String): Boolean = SHA256_HEX_REGEX.matches(value.trim())

        internal fun isValidVersionName(versionName: String): Boolean {
            val trimmed = versionName.trim()
            return trimmed.isNotEmpty() &&
                !trimmed.contains('/') &&
                !trimmed.contains('\\') &&
                !trimmed.contains("..")
        }

        internal fun isAllowedInitialDownloadUrl(rawUrl: String): Boolean {
            if (rawUrl.isBlank() || rawUrl.contains("..") || rawUrl.contains('\\')) return false
            val parsed = rawUrl.toHttpUrlOrNull() ?: return false
            if (parsed.scheme != "https") return false
            if (!parsed.host.equals("github.com", ignoreCase = true)) return false
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return false
            if (parsed.pathSegments.any { it == "." || it == ".." }) return false
            return parsed.encodedPath.startsWith(ALLOWED_RELEASE_PATH_PREFIX) &&
                parsed.encodedPath.length > ALLOWED_RELEASE_PATH_PREFIX.length
        }

        internal fun isAllowedRedirectUrl(url: HttpUrl): Boolean {
            if (url.scheme != "https") return false
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
            if (url.pathSegments.any { it == "." || it == ".." }) return false
            val hostLower = url.host.lowercase(Locale.US)
            if (hostLower == "github.com") {
                return url.encodedPath.startsWith(ALLOWED_RELEASE_PATH_PREFIX)
            }
            return hostLower in ALLOWED_REDIRECT_HOSTS
        }

        private fun extractExpectedSha256(asset: JSONObject, assetName: String, releaseNotes: String): String? {
            if (!asset.isNull("digest")) {
                val rawDigest = asset.optString("digest", "").trim()
                if (rawDigest.startsWith("sha256:", ignoreCase = true)) {
                    val hex = rawDigest.substringAfter(':').trim()
                    if (SHA256_HEX_REGEX.matches(hex)) {
                        return hex.lowercase(Locale.US)
                    }
                }
            }
            if (assetName.isNotBlank() && releaseNotes.isNotBlank()) {
                val noteRegex = Regex(
                    "(?m)^([0-9a-fA-F]{64})\\s+\\*?${Regex.escape(assetName)}\\s*$"
                )
                val match = noteRegex.find(releaseNotes)
                if (match != null) {
                    return match.groupValues[1].lowercase(Locale.US)
                }
            }
            return null
        }

        /**
         * Only notify once per release, so frequent background checks don't re-post
         * the same "update available" notification every few hours.
         */
        internal fun shouldNotifyForVersion(latestVersion: String, lastNotifiedVersion: String?): Boolean {
            return latestVersion.isNotBlank() && latestVersion != lastNotifiedVersion
        }

        internal fun shouldPromptInAppForUpdate(
            latestVersion: String,
            dismissedVersion: String?,
            dismissedAtMillis: Long,
            nowMillis: Long,
            snoozeMillis: Long = UPDATE_DISMISS_SNOOZE_MILLIS
        ): Boolean {
            if (latestVersion.isBlank()) return false
            if (dismissedVersion.isNullOrBlank() || dismissedVersion != latestVersion) return true
            return dismissedAtMillis <= 0L ||
                dismissedAtMillis > nowMillis ||
                (nowMillis - dismissedAtMillis) >= snoozeMillis
        }

        internal fun isNewerVersion(current: String, latest: String): Boolean {
            try {
                val currentClean = current.substringBefore("-").replace(Regex("[^0-9.]"), "")
                val latestClean = latest.substringBefore("-").replace(Regex("[^0-9.]"), "")

                val currentParts = currentClean.split(".").map { it.toIntOrNull() ?: 0 }
                val latestParts = latestClean.split(".").map { it.toIntOrNull() ?: 0 }

                for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
                    val c = currentParts.getOrElse(i) { 0 }
                    val l = latestParts.getOrElse(i) { 0 }
                    if (l > c) return true
                    if (l < c) return false
                }
                return false
            } catch (e: Exception) {
                return latest != current
            }
        }

        internal fun shouldAutoCheck(
            lastCheckMillis: Long,
            nowMillis: Long,
            intervalMillis: Long = 12L * 60 * 60 * 1000
        ): Boolean {
            return lastCheckMillis <= 0 || lastCheckMillis > nowMillis || (nowMillis - lastCheckMillis) >= intervalMillis
        }

        @JvmOverloads
        internal fun readBoundedUtf8String(
            body: ResponseBody?,
            maxBytes: Int = MAX_RELEASE_JSON_BYTES
        ): String? {
            if (body == null) return null
            val declaredLen = body.contentLength()
            if (declaredLen > maxBytes) {
                throw IOException("Response exceeded maximum allowed size ($maxBytes bytes)")
            }
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var totalRead = 0
            body.byteStream().use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    if (read > maxBytes - totalRead) {
                        throw IOException("Response exceeded maximum allowed size ($maxBytes bytes)")
                    }
                    out.write(buffer, 0, read)
                    totalRead += read
                }
            }
            return out.toByteArray().toString(Charsets.UTF_8)
        }
    }
}
