package com.reiux.lumen.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test

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
}
