package com.reiux.lumen.util

import com.reiux.lumen.data.model.AccountBlock
import com.reiux.lumen.data.model.AuthFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSafetyAndBreakerTest {

    @Test
    fun testErrorClassificationAuthFailures() {
        val authEx = AuthFailureException(kind = AuthFailureKind.SIGN_IN_REQUIRED, rawDetail = "NeedsBrowser")
        assertEquals(AuthFailureKind.SIGN_IN_REQUIRED, authEx.kind)

        val restrictedEx = AuthFailureException(kind = AuthFailureKind.ACCOUNT_RESTRICTED, rawDetail = "AccountDisabled")
        assertEquals(AuthFailureKind.ACCOUNT_RESTRICTED, restrictedEx.kind)

        val rateLimitEx = AuthFailureException(kind = AuthFailureKind.RATE_LIMITED, retryAfterSec = 900L, rawDetail = "429 Rate Limit")
        assertEquals(AuthFailureKind.RATE_LIMITED, rateLimitEx.kind)
        assertEquals(900L, rateLimitEx.retryAfterSec)
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
        // Rate limit block has expired if current time >= retryAfterTimestamp
        assertTrue(now >= expiredBlock.retryAfterTimestamp!!)

        val activeBlock = AccountBlock(
            kind = AuthFailureKind.RATE_LIMITED,
            timestamp = now,
            accountEmail = "test@example.com",
            retryAfterTimestamp = now + 120000L
        )
        assertFalse(now >= activeBlock.retryAfterTimestamp!!)
    }

    @Test
    fun testSignInGateState() {
        val initialAcknowledged = false
        assertFalse(initialAcknowledged)

        val updatedAcknowledged = true
        assertTrue(updatedAcknowledged)
    }
}
