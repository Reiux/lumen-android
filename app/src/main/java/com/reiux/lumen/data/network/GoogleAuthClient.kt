package com.reiux.lumen.data.network

import android.net.Uri
import com.reiux.lumen.data.model.AuthFailureKind
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.util.AuthFailureException
import com.reiux.lumen.util.LumenLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class GoogleAuthClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder().build()
) {
    private val tokenCache = ConcurrentHashMap<String, CachedToken>()

    data class CachedToken(
        val bearerToken: String,
        val expiresAtEpochSec: Long
    )

    suspend fun exchangeOAuthToken(rawOAuthToken: String): GoogleAccount = withContext(Dispatchers.IO) {
        val cleanToken = rawOAuthToken.trim().removePrefix("oauth_token=")
        val androidId = generateAndroidId()

        val formBuilder = FormBody.Builder()
            .add("accountType", "HOSTED_OR_GOOGLE")
            .add("Email", "oauth-token@example.com")
            .add("has_permission", "1")
            .add("add_account", "1")
            .add("ACCESS_TOKEN", "1")
            .add("Token", cleanToken)
            .add("service", "ac2dm")
            .add("source", "android")
            .add("androidId", androidId)
            .add("device_country", "us")
            .add("operatorCountry", "us")
            .add("lang", "en")
            .add("sdk_version", "17")
            .add("google_play_services_version", "240913000")
            .add("client_sig", GOOGLE_PLAY_SERVICES_SIG)
            .add("callerSig", GOOGLE_PLAY_SERVICES_SIG)
            .add("droidguard_results", "dummy123")

        val request = Request.Builder()
            .url(AUTH_ENDPOINT)
            .post(formBuilder.build())
            .header("Accept-Encoding", "identity")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("User-Agent", "GoogleAuth/1.4")
            .build()

        val start = System.currentTimeMillis()
        val response = httpClient.newCall(request).execute()
        val duration = System.currentTimeMillis() - start
        val respBytes = response.body?.bytes() ?: ByteArray(0)
        val body = respBytes.decodeToString()
        val parsed = parseKeyValueResponse(body)

        val googleError = parsed["Error"]
        if (!googleError.isNullOrEmpty()) {
            val userFriendlyMsg = when (googleError) {
                "BadAuthentication" -> "Google rejected the oauth_token (expired or already used). Please sign in again to get a fresh token."
                "NeedsBrowser" -> "Google requires a fresh Embedded Setup browser sign-in."
                "MissingDroidguard" -> "Google rejected device verification data."
                else -> "Google authentication failed: $googleError"
            }
            LumenLogger.w("Auth") { "exchangeOAuthToken error '$googleError': $body" }
            throw IOException(userFriendlyMsg)
        }

        if (!response.isSuccessful) {
            val errBody = body.take(300).replace("\n", " ")
            LumenLogger.w("Network") {
                "[POST ${response.code} ERROR] ${LumenLogger.redact(AUTH_ENDPOINT)} reqSize=${formBuilder.build().contentLength()}B respSize=${respBytes.size}B duration=${duration}ms errBody='$errBody'"
            }
            throw IOException("Google token exchange failed: HTTP ${response.code} ($errBody)")
        }

        LumenLogger.d("Network") {
            "[POST ${response.code}] ${LumenLogger.redact(AUTH_ENDPOINT)} reqSize=${formBuilder.build().contentLength()}B respSize=${respBytes.size}B duration=${duration}ms"
        }

        val masterToken = parsed["Token"] ?: throw IOException("Google auth missing master Token. Response: $body")
        val email = parsed["Email"] ?: throw IOException("Google auth missing Email. Response: $body")

        val credentialString = buildCredentialString(email, masterToken, androidId)
        GoogleAccount(
            email = email,
            credentialString = credentialString,
            androidId = androidId,
            masterToken = masterToken
        )
    }

    suspend fun getBearerToken(account: GoogleAccount): String = withContext(Dispatchers.IO) {
        val cached = tokenCache[account.email]
        val now = System.currentTimeMillis() / 1000
        if (cached != null && cached.expiresAtEpochSec > now + 60) {
            return@withContext cached.bearerToken
        }

        LumenLogger.d("Auth", "Refreshing Google Photos bearer token for ${account.email}...")
        val formValues = parseQueryString(account.credentialString)
        val formBuilder = FormBody.Builder()
        for ((k, v) in formValues) {
            if (k != "it_caveat_types" && k != "assertion_jwt" && k != "token_binding_alias") {
                formBuilder.add(k, v)
            }
        }
        formBuilder.add("app", GOOGLE_PHOTOS_PKG)
        formBuilder.add("callerPkg", GOOGLE_PHOTOS_PKG)

        val request = Request.Builder()
            .url(AUTH_ENDPOINT)
            .post(formBuilder.build())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("device", account.androidId.ifEmpty { formValues["androidId"] ?: "" })
            .header("User-Agent", "GoogleAuth/1.4 (Pixel XL PQ2A.190205.001); gzip")
            .build()

        val start = System.currentTimeMillis()
        val response = httpClient.newCall(request).execute()
        val duration = System.currentTimeMillis() - start
        val respBytes = response.body?.bytes() ?: ByteArray(0)

        if (response.isSuccessful) {
            LumenLogger.d("Network") {
                "[POST ${response.code}] ${LumenLogger.redact(AUTH_ENDPOINT)} reqSize=${formBuilder.build().contentLength()}B respSize=${respBytes.size}B duration=${duration}ms"
            }
        } else {
            val errBody = respBytes.decodeToString().take(500)
            LumenLogger.w("Network") {
                "[POST ${response.code} ERROR] ${LumenLogger.redact(AUTH_ENDPOINT)} reqSize=${formBuilder.build().contentLength()}B respSize=${respBytes.size}B duration=${duration}ms errBody='$errBody'"
            }
            throw IOException("Failed to obtain Photos bearer token: HTTP ${response.code}")
        }

        val body = respBytes.decodeToString()
        val parsed = parseKeyValueResponse(body)
        val googleError = parsed["Error"] ?: parsed["Info"]

        if (!response.isSuccessful || !googleError.isNullOrEmpty()) {
            val errCode = response.code
            val rawDetail = "HTTP $errCode: ${googleError ?: body.take(300)}"
            val kind = when (googleError) {
                "NeedsBrowser", "NeedsVerification", "CaptchaRequired", "DeviceManagementRequired", "BadAuthentication", "NeedPermission", "TermsOfServiceNotAccepted" -> AuthFailureKind.SIGN_IN_REQUIRED
                "AccountDisabled", "AccountDeleted", "ServiceDisabled", "AccessNotConfigured", "GoogleAccountDataDeletion", "AccountLocked" -> AuthFailureKind.ACCOUNT_RESTRICTED
                else -> if (errCode == 429) AuthFailureKind.RATE_LIMITED else AuthFailureKind.SIGN_IN_REQUIRED
            }
            LumenLogger.w("Auth") { "getBearerToken failed ($kind): $rawDetail" }
            throw com.reiux.lumen.util.AuthFailureException(kind = kind, rawDetail = rawDetail)
        }

        val bearerToken = parsed["Auth"] ?: throw com.reiux.lumen.util.AuthFailureException(AuthFailureKind.SIGN_IN_REQUIRED, rawDetail = "Missing Auth field in response: $body")
        val expirySec = parsed["Expiry"]?.toLongOrNull() ?: (now + 3600)

        tokenCache[account.email] = CachedToken(bearerToken, expirySec)
        LumenLogger.d("Auth", "Acquired Photos bearer token for ${account.email} (expires in ${expirySec - now}s)")
        bearerToken
    }

    fun invalidateCache(email: String) {
        tokenCache.remove(email)
        LumenLogger.d("Auth", "Invalidated token cache for $email")
    }

    private fun generateAndroidId(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun buildCredentialString(email: String, masterToken: String, androidId: String): String {
        val params = mapOf(
            "androidId" to androidId,
            "app" to GOOGLE_PHOTOS_PKG,
            "callerPkg" to GOOGLE_PHOTOS_PKG,
            "callerSig" to GOOGLE_PHOTOS_SIG,
            "client_sig" to GOOGLE_PHOTOS_SIG,
            "device_country" to "us",
            "Email" to email,
            "google_play_services_version" to "240913000",
            "lang" to "en_US",
            "oauth2_foreground" to "1",
            "operatorCountry" to "us",
            "sdk_version" to "33",
            "service" to PHOTOS_SERVICE,
            "source" to "android",
            "Token" to masterToken
        )
        return params.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
    }

    private fun parseKeyValueResponse(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in body.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split("=", limit = 2)
            if (parts.size == 2) {
                result[parts[0]] = parts[1]
            }
        }
        return result
    }

    private fun parseQueryString(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }

    companion object {
        const val AUTH_ENDPOINT = "https://android.clients.google.com/auth"
        const val GOOGLE_PLAY_SERVICES_SIG = "38918a453d07199354f8b19af05ec6562ced5788"
        const val GOOGLE_PHOTOS_PKG = "com.google.android.apps.photos"
        const val GOOGLE_PHOTOS_SIG = "24bb24c05e47e0aefa68a58a766179d9b613a600"
        const val PHOTOS_SERVICE = "oauth2:openid https://www.googleapis.com/auth/mobileapps.native https://www.googleapis.com/auth/photos.native"
    }
}
