package com.reiux.lumen.util

import com.reiux.lumen.data.model.AccountBlock
import com.reiux.lumen.data.model.AuthFailureKind
import com.reiux.lumen.data.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.net.SocketTimeoutException

class AccountSafetyAndBreakerTest {

    @Test
    fun testErrorClassifierPrecedenceAndParsing() {
        // 1. FILE_UNREADABLE containing path with "4012.jpg" is NOT AUTH_EXPIRED
        val unreadableErr = ErrorClassifier.classify(
            throwable = null,
            rawErrorMsg = "File cannot be read: path=/sdcard/DCIM/Camera/IMG_4012.jpg"
        )
        assertEquals("FILE_UNREADABLE", unreadableErr.reasonCode)
        assertNotEquals("AUTH_EXPIRED", unreadableErr.reasonCode)

        // 2. "Upload chunk failed HTTP 500: {"id":"4031"}" is SERVER_ERROR (not AUTH_EXPIRED despite "4031")
        val serverErr = ErrorClassifier.classify(
            throwable = null,
            rawErrorMsg = "Upload chunk failed HTTP 500: {\"id\":\"4031\"}"
        )
        assertEquals("SERVER_ERROR", serverErr.reasonCode)

        // 3. "Commit upload failed HTTP 401: ..." is AUTH_EXPIRED
        val authErr = ErrorClassifier.classify(
            throwable = null,
            rawErrorMsg = "Commit upload failed HTTP 401: Unauthorized"
        )
        assertEquals("AUTH_EXPIRED", authErr.reasonCode)
        assertTrue(authErr.suggestedAction.contains("Sign in again once from the Accounts tab"))

        // 4. AuthFailureException(ACCOUNT_RESTRICTED) is ACCOUNT_RESTRICTED
        val restrictedEx = AuthFailureException(kind = AuthFailureKind.ACCOUNT_RESTRICTED, rawDetail = "AccountDisabled")
        val restrictedErr = ErrorClassifier.classify(throwable = restrictedEx)
        assertEquals("ACCOUNT_RESTRICTED", restrictedErr.reasonCode)
        assertTrue(restrictedErr.userMessage.contains("Google has restricted this account"))

        // 5. SocketTimeoutException is NETWORK_TIMEOUT
        val timeoutErr = ErrorClassifier.classify(throwable = SocketTimeoutException("connect timed out"))
        assertEquals("NETWORK_TIMEOUT", timeoutErr.reasonCode)

        // 6. FileNotFoundException on hash_test.jpg is FILE_UNREADABLE, not HASH_FAILED
        val fnfErr = ErrorClassifier.classify(
            throwable = FileNotFoundException("/sdcard/hash_test.jpg (No such file or directory)")
        )
        assertEquals("FILE_UNREADABLE", fnfErr.reasonCode)
        assertNotEquals("HASH_FAILED", fnfErr.reasonCode)

        // 7. "Hash calculation failed after 12ms: ..." is HASH_FAILED
        val hashErr = ErrorClassifier.classify(
            throwable = null,
            rawErrorMsg = "Hash calculation failed after 12ms: file unreadable"
        )
        assertEquals("HASH_FAILED", hashErr.reasonCode)
    }

    @Test
    fun testAccountBlockExpiry() {
        val now = System.currentTimeMillis()

        val expiredBlock = AccountBlock(
            kind = AuthFailureKind.RATE_LIMITED,
            timestamp = now - 10000L,
            accountEmail = "test@example.com",
            retryAfterTimestamp = now - 1000L
        )
        assertTrue(SettingsRepository.isBlockExpired(expiredBlock, now))

        val activeBlock = AccountBlock(
            kind = AuthFailureKind.RATE_LIMITED,
            timestamp = now,
            accountEmail = "test@example.com",
            retryAfterTimestamp = now + 120000L
        )
        assertFalse(SettingsRepository.isBlockExpired(activeBlock, now))

        val permanentBlock = AccountBlock(
            kind = AuthFailureKind.SIGN_IN_REQUIRED,
            timestamp = now,
            accountEmail = "test@example.com"
        )
        assertFalse(SettingsRepository.isBlockExpired(permanentBlock, now))
    }
}
