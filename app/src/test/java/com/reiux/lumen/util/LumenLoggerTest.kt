package com.reiux.lumen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class LumenLoggerTest {

    @Test
    fun testRedaction() {
        val emailRaw = "Uploaded photo for alex.rivera@gmail.com and user@photos.google.com"
        val emailRedacted = LumenLogger.redact(emailRaw)
        assertTrue(emailRedacted.contains("a***@gmail.com"))
        assertTrue(emailRedacted.contains("u***@photos.google.com"))

        val tokenRaw = "POST https://photos.googleapis.com?upload_token=secret_123456 Authorization: Bearer ya29.a0AfH6_SECRET"
        val tokenRedacted = LumenLogger.redact(tokenRaw)
        assertTrue(tokenRedacted.contains("upload_token=[REDACTED]"))
        assertTrue(tokenRedacted.contains("Bearer [REDACTED]"))
    }

    @Test
    fun testErrorClassifierMapping() {
        val authErr = ErrorClassifier.classify(null, 401, "Unauthorized token")
        assertEquals("AUTH_EXPIRED", authErr.reasonCode)
        assertTrue(authErr.suggestedAction.contains("Accounts tab"))

        val rateErr = ErrorClassifier.classify(null, 429, "Rate limit hit")
        assertEquals("RATE_LIMITED", rateErr.reasonCode)

        val serverErr = ErrorClassifier.classify(null, 503, "Service Unavailable")
        assertEquals("SERVER_ERROR", serverErr.reasonCode)

        val timeoutErr = ErrorClassifier.classify(SocketTimeoutException("Read timed out"), null, null)
        assertEquals("NETWORK_TIMEOUT", timeoutErr.reasonCode)

        val hostErr = ErrorClassifier.classify(UnknownHostException("Unable to resolve host"), null, null)
        assertEquals("NO_INTERNET", hostErr.reasonCode)

        val fileErr = ErrorClassifier.classify(FileNotFoundException("File missing"), null, null)
        assertEquals("FILE_UNREADABLE", fileErr.reasonCode)
    }

    @Test
    fun testDuplicateLineCollapsing() {
        LumenLogger.clearLogs(null)
        LumenLogger.i("TestTag") { "Identical test log line" }
        LumenLogger.i("TestTag") { "Identical test log line" }
        LumenLogger.i("TestTag") { "Identical test log line" }

        val lastLog = LumenLogger.logs.value.lastOrNull()
        assertNotNull(lastLog)
        assertEquals(3, lastLog?.repeatCount)
        assertTrue(lastLog?.fullFormattedText?.contains("(x3)") == true)
    }

    @Test
    fun testRunSummary() {
        LumenLogger.logRunStart(
            runId = "TestRun_01",
            queuedCount = 10,
            wifiOnly = true,
            chargingOnly = false,
            storageSaver = false,
            useQuota = false,
            threads = 2
        )

        LumenLogger.logRunSummary(
            runId = "TestRun_01",
            uploadedCount = 8,
            skippedCount = 1,
            failedCount = 1,
            totalBytes = 10_485_760L,
            durationMs = 5000L,
            avgSpeedBps = 2_097_152.0
        )

        val summaryLog = LumenLogger.logs.value.lastOrNull { it.kind == LogKind.RUN_SUMMARY }
        assertNotNull(summaryLog)
        assertEquals("TestRun_01", summaryLog?.runId)
        assertTrue(summaryLog?.message?.contains("8 uploaded, 1 skipped, 1 failed") == true)
    }
}
