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
        val errorStr = "${throwable?.message ?: ""} ${rawErrorMsg ?: ""}".lowercase()

        if (httpCode == 401 || httpCode == 403 || errorStr.contains("401") || errorStr.contains("403") || errorStr.contains("unauthorized") || errorStr.contains("forbidden")) {
            return ClassifiedError(
                reasonCode = "AUTH_EXPIRED",
                userMessage = "Google sign-in expired or unauthorized.",
                suggestedAction = "Re-add your Google account in Accounts tab."
            )
        }

        if (httpCode == 429 || errorStr.contains("429") || errorStr.contains("rate limit") || errorStr.contains("quota exceeded")) {
            return ClassifiedError(
                reasonCode = "RATE_LIMITED",
                userMessage = "Google upload rate limit reached.",
                suggestedAction = "Lumen will pause and retry automatically later."
            )
        }

        if ((httpCode != null && httpCode in 500..599) || errorStr.contains("http 50") || errorStr.contains("scotty upload server timeout") || errorStr.contains("service unavailable")) {
            return ClassifiedError(
                reasonCode = "SERVER_ERROR",
                userMessage = "Google Photos server problem (HTTP ${httpCode ?: 500}).",
                suggestedAction = "Google's servers are temporarily busy. Retrying automatically."
            )
        }

        if (throwable is SocketTimeoutException || errorStr.contains("timeout") || errorStr.contains("timed out")) {
            return ClassifiedError(
                reasonCode = "NETWORK_TIMEOUT",
                userMessage = "Network connection timed out.",
                suggestedAction = "Check your Wi-Fi or cellular connection and retry."
            )
        }

        if (throwable is UnknownHostException || errorStr.contains("unknownhost") || errorStr.contains("unable to resolve host") || errorStr.contains("no network")) {
            return ClassifiedError(
                reasonCode = "NO_INTERNET",
                userMessage = "No internet connection available.",
                suggestedAction = "Connect to Wi-Fi or mobile data to resume backup."
            )
        }

        if (throwable is FileNotFoundException || errorStr.contains("filenotfound") || errorStr.contains("cannot be read") || errorStr.contains("does not exist")) {
            return ClassifiedError(
                reasonCode = "FILE_UNREADABLE",
                userMessage = "Media file could not be read or was moved.",
                suggestedAction = "Check if the file was deleted or stored on an unmounted SD card."
            )
        }

        if (errorStr.contains("hash") || errorStr.contains("checksum") || errorStr.contains("corrupted")) {
            return ClassifiedError(
                reasonCode = "HASH_FAILED",
                userMessage = "File integrity check failed.",
                suggestedAction = "The file on disk may be corrupted or partially written."
            )
        }

        if (errorStr.contains("permission") || errorStr.contains("securityexception")) {
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
