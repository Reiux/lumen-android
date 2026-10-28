package com.reiux.lumen.data.repository

import android.content.Context
import com.reiux.lumen.BuildConfig
import com.reiux.lumen.data.model.UpdateInfo
import com.reiux.lumen.util.LumenLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class UpdateRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder().build()
) {
    suspend fun checkForUpdates(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/Reiux/lumen-android/releases/latest"
            val request = Request.Builder().url(url).build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                LumenLogger.w("Updater") { "Failed to fetch updates: HTTP ${response.code}" }
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val release = JSONObject(body)

            val latestVersion = release.optString("tag_name", "").removePrefix("v")
            val releaseNotes = release.optString("body", "")
            
            val assets = release.optJSONArray("assets") ?: JSONArray()
            var downloadUrl = ""
            
            val expectedApk = if (BuildConfig.DEBUG) "lumen-dev-debug.apk" else "lumen-release.apk"
            
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name == expectedApk) {
                    downloadUrl = asset.optString("browser_download_url", "")
                    break
                }
            }
            
            if (downloadUrl.isEmpty()) {
                // Fallback to any APK if expected isn't found
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name", "").endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            val hasUpdate = isNewerVersion(currentVersion, latestVersion)

            LumenLogger.i("Updater") { "Update check complete. Latest: $latestVersion, Current: $currentVersion, HasUpdate: $hasUpdate" }

            UpdateInfo(
                versionName = latestVersion,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl,
                isUpdateAvailable = hasUpdate
            )
        } catch (e: Exception) {
            LumenLogger.e("Updater", tr = e) { "Error checking for updates: ${e.message}" }
            null
        }
    }
    
    fun downloadUpdate(url: String, destFile: File): Flow<Int> = flow {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Download failed with HTTP ${response.code}")
        }
        
        val body = response.body ?: throw Exception("Empty response body")
        val contentLength = body.contentLength()
        
        body.byteStream().use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesCopied = 0L
                var read: Int
                var lastProgress = 0
                
                while (input.read(buffer).also { read = it } >= 0) {
                    output.write(buffer, 0, read)
                    bytesCopied += read
                    
                    if (contentLength > 0) {
                        val progress = ((bytesCopied * 100) / contentLength).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            emit(progress)
                        }
                    }
                }
            }
        }
        emit(100)
    }.flowOn(Dispatchers.IO)

    private fun isNewerVersion(current: String, latest: String): Boolean {
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

    companion object {
        internal fun shouldAutoCheck(
            lastCheckMillis: Long,
            nowMillis: Long,
            intervalMillis: Long = 12L * 60 * 60 * 1000
        ): Boolean {
            return lastCheckMillis <= 0 || lastCheckMillis > nowMillis || (nowMillis - lastCheckMillis) >= intervalMillis
        }
    }
}
