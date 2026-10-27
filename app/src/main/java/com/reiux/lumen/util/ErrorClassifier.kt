package com.reiux.lumen.util

import com.reiux.lumen.data.model.AuthFailureKind
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AuthFailureException(
    val kind: AuthFailureKind,
    val retryAfterSec: Long? = null,
    val rawDetail: String? = null
) : IOException("Auth failure ($kind): ${rawDetail ?: "-"}")

data class ClassifiedError(
    val reasonCode: String,
    val userMessage: String,
    val suggestedAction: String
)

object ErrorClassifier {

    fun classify(throwable: Throwable?, httpCode: Int? = null, rawErrorMsg: String? = null): ClassifiedError {
        // 1. By AuthFailureException kind
        if (throwable is AuthFailureException) {
            return when (throwable.kind) {
                AuthFailureKind.SIGN_IN_REQUIRED, AuthFailureKind.TOKEN_EXPIRED -> ClassifiedError(
                    reasonCode = "AUTH_EXPIRED",
                    userMessage = "Google sign-in expired or unauthorized.",
                    suggestedAction = "Sign in again once from the Accounts tab. If Google keeps signing you out, stop and check your Google account directly."
                )
                AuthFailureKind.ACCOUNT_RESTRICTED -> ClassifiedError(
                    reasonCode = "ACCOUNT_RESTRICTED",
                    userMessage = "Google has restricted this account.",
                    suggestedAction = "Check your Google account directly; do not keep retrying."
                )
                AuthFailureKind.RATE_LIMITED -> ClassifiedError(
                    reasonCode = "RATE_LIMITED",
                    userMessage = "Google upload rate limit reached.",
                    suggestedAction = "Lumen will pause and retry automatically later."
                )
                AuthFailureKind.TRANSIENT -> ClassifiedError(
                    reasonCode = "SERVER_ERROR",
                    userMessage = "Google Photos server problem.",
                    suggestedAction = "Google's servers are temporarily busy. Retrying automatically."
                )
                AuthFailureKind.NONE -> ClassifiedError(
                    reasonCode = "UNKNOWN_ERROR",
                    userMessage = "Upload failed due to an unexpected error.",
                    suggestedAction = "Check debug logs or retry the upload."
                )
            }
        }

        // 2. By exception type
        if (throwable is SocketTimeoutException) {
            return ClassifiedError(
                reasonCode = "NETWORK_TIMEOUT",
                userMessage = "Network connection timed out.",
                suggestedAction = "Check your Wi-Fi or cellular connection and retry."
            )
        }
        if (throwable is UnknownHostException) {
            return ClassifiedError(
                reasonCode = "NO_INTERNET",
                userMessage = "No internet connection available.",
                suggestedAction = "Connect to Wi-Fi or mobile data to resume backup."
            )
        }
        if (throwable is FileNotFoundException) {
            return ClassifiedError(
                reasonCode = "FILE_UNREADABLE",
                userMessage = "Media file could not be read or was moved.",
                suggestedAction = "Check if the file was deleted or stored on an unmounted SD card."
            )
        }
        if (throwable is SecurityException) {
            return ClassifiedError(
                reasonCode = "PERMISSION_DENIED",
                userMessage = "Storage permission is missing.",
                suggestedAction = "Grant media permissions in Android Settings."
            )
        }

        // 3. By HTTP status code (httpCode parameter or explicit HTTP 3-digit status in message)
        val parsedHttpCode = httpCode ?: run {
            val fullText = "${throwable?.message ?: ""} ${rawErrorMsg ?: ""}"
            val match = Regex("""\bHTTP\s+(\d{3})\b""", RegexOption.IGNORE_CASE).find(fullText)
            match?.groupValues?.get(1)?.toIntOrNull()
        }

        if (parsedHttpCode != null) {
            when (parsedHttpCode) {
                401, 403 -> return ClassifiedError(
                    reasonCode = "AUTH_EXPIRED",
                    userMessage = "Google sign-in expired or unauthorized.",
                    suggestedAction = "Sign in again once from the Accounts tab. If Google keeps signing you out, stop and check your Google account directly."
                )
                429 -> return ClassifiedError(
                    reasonCode = "RATE_LIMITED",
                    userMessage = "Google upload rate limit reached.",
                    suggestedAction = "Lumen will pause and retry automatically later."
                )
                in 500..599 -> return ClassifiedError(
                    reasonCode = "SERVER_ERROR",
                    userMessage = "Google Photos server problem (HTTP $parsedHttpCode).",
                    suggestedAction = "Google's servers are temporarily busy. Retrying automatically."
                )
            }
        }

        // 4. Exact phrases produced by our own code
        val errorText = "${throwable?.message ?: ""} ${rawErrorMsg ?: ""}"

        if (errorText.contains("Hash calculation failed", ignoreCase = true) ||
            errorText.contains("File integrity check failed", ignoreCase = true)
        ) {
            return ClassifiedError(
                reasonCode = "HASH_FAILED",
                userMessage = "File integrity check failed.",
                suggestedAction = "The file on disk may be corrupted or partially written."
            )
        }

        if (errorText.contains("File cannot be read", ignoreCase = true) ||
            errorText.contains("Media file could not be read", ignoreCase = true)
        ) {
            return ClassifiedError(
                reasonCode = "FILE_UNREADABLE",
                userMessage = "Media file could not be read or was moved.",
                suggestedAction = "Check if the file was deleted or stored on an unmounted SD card."
            )
        }

        if (errorText.contains("Scotty upload server timeout", ignoreCase = true) ||
            errorText.contains("Service Unavailable", ignoreCase = true)
        ) {
            return ClassifiedError(
                reasonCode = "SERVER_ERROR",
                userMessage = "Google Photos server problem.",
                suggestedAction = "Google's servers are temporarily busy. Retrying automatically."
            )
        }

        if (errorText.contains("Storage permission is missing", ignoreCase = true)) {
            return ClassifiedError(
                reasonCode = "PERMISSION_DENIED",
                userMessage = "Storage permission is missing.",
                suggestedAction = "Grant media permissions in Android Settings."
            )
        }

        return ClassifiedError(
            reasonCode = "UNKNOWN_ERROR",
            userMessage = rawErrorMsg?.takeIf { it.isNotBlank() } ?: throwable?.message?.takeIf { it.isNotBlank() } ?: "Upload failed due to an unexpected error.",
            suggestedAction = "Check debug logs or retry the upload."
        )
    }
}
