package com.reiux.lumen.data.repository

import com.reiux.lumen.data.model.AuthFailureKind
import com.reiux.lumen.data.model.UploadStatus
import com.reiux.lumen.util.AuthFailureException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class UploadLogicTest {

    @Test
    fun testComputeRetryDelayMsWithJitter() {
        val attempt1Delay = UploadRepository.computeRetryDelayMs(1)
        assertTrue("Attempt 1 delay should be between 2250 and 3000 ms, was $attempt1Delay", attempt1Delay in 2250L..3000L)

        val attempt2Delay = UploadRepository.computeRetryDelayMs(2)
        assertTrue("Attempt 2 delay should be between 4250 and 5000 ms, was $attempt2Delay", attempt2Delay in 4250L..5000L)
        
        val maxAttemptDelay = UploadRepository.computeRetryDelayMs(5)
        assertTrue("Max attempt delay should be capped at base 6000 + jitter (up to 1000), was $maxAttemptDelay", maxAttemptDelay in 6250L..7000L)
    }

    @Test
    fun testShouldAttemptCommitRecovery() {
        // COMMITTING + IOException -> true
        assertTrue(UploadRepository.shouldAttemptCommitRecovery(IOException("Network error"), UploadStatus.COMMITTING))
        
        // COMMITTING + SocketTimeoutException -> true
        assertTrue(UploadRepository.shouldAttemptCommitRecovery(SocketTimeoutException("Timeout"), UploadStatus.COMMITTING))

        // COMMITTING + AuthFailureException(ACCOUNT_RESTRICTED) -> false
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.ACCOUNT_RESTRICTED), UploadStatus.COMMITTING))

        // (SIGN_IN_REQUIRED) -> false
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.SIGN_IN_REQUIRED), UploadStatus.COMMITTING))

        // (RATE_LIMITED) -> false
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.RATE_LIMITED), UploadStatus.COMMITTING))

        // (TOKEN_EXPIRED) -> false
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.TOKEN_EXPIRED), UploadStatus.COMMITTING))

        // (TRANSIENT) -> true
        assertTrue(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.TRANSIENT), UploadStatus.COMMITTING))

        // status UPLOADING (not COMMITTING) + IOException -> false
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(IOException("Network error"), UploadStatus.UPLOADING))
    }
}
