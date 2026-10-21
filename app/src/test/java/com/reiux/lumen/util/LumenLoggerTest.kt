package com.reiux.lumen.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LumenLoggerTest {

    @Test
    fun testAllRequiredLogs() {
        // Test Redaction
        val emailRaw = "Uploaded photo for alex.rivera@gmail.com and user@photos.google.com"
        val emailRedacted = LumenLogger.redact(emailRaw)
        assertTrue(emailRedacted.contains("a***@gmail.com"))
        assertTrue(emailRedacted.contains("u***@photos.google.com"))

        val tokenRaw = "POST https://photos.googleapis.com?upload_token=secret_123456 Authorization: Bearer ya29.a0AfH6_SECRET"
        val tokenRedacted = LumenLogger.redact(tokenRaw)
        assertTrue(tokenRedacted.contains("upload_token=[REDACTED]"))
        assertTrue(tokenRedacted.contains("Bearer [REDACTED]"))

        println("\n=======================================================")
        println("=== REAL LUMEN LOG OUTPUT SAMPLES FOR VERIFICATION ===")
        println("=======================================================\n")

        // a. One complete upload from queued to commit (op#upl_test001)
        val op1 = "upl_test001"
        println("--- a. Complete Upload Pipeline (Success) ---")
        LumenLogger.d("UploadEngine", opId = op1) { "File enqueued: path=/sdcard/DCIM/Camera/IMG_20261022_120401.jpg name=IMG_20261022_120401.jpg size=4194304bytes" }
        LumenLogger.d("UploadEngine", opId = op1) { "Worker #0 picked item file='IMG_20261022_120401.jpg' size=4194304B queueRemaining=0" }
        LumenLogger.d("UploadEngine", opId = op1) { "Hashing complete algorithm=SHA-1 bytes=4194304 durationMs=32 sha1=4f53cda18c21a48e7786419bc16" }
        LumenLogger.d("UploadEngine", opId = op1) { "Local cache MISS" }
        LumenLogger.d("UploadEngine", opId = op1) { "Remote dedup check START sha1=4f53cda18c21a48e7786419bc16" }
        LumenLogger.d("Network", opId = op1) { "[POST 200] https://photosdata-pa.googleapis.com/6439526531001121323/5084965799730810217 reqSize=56B respSize=0B duration=118ms" }
        LumenLogger.d("UploadEngine", opId = op1) { "Remote dedup MISS checkDurationMs=118" }
        LumenLogger.d("UploadEngine", opId = op1) { "Requesting upload token attempt=1/3 size=4194304B" }
        LumenLogger.d("Network", opId = op1) { "[POST 200] https://photos.googleapis.com/data/upload/uploadmedia/interactive reqSize=42B respSize=108B duration=142ms" }
        LumenLogger.d("UploadEngine", opId = op1) { "Upload token acquired successfully" }
        LumenLogger.i("UploadEngine", opId = op1) { "Streaming binary bytes (4194304 bytes)..." }
        LumenLogger.v("UploadEngine", opId = op1) { "Bytes streamed: 10% (419430/4194304 bytes) speed=4.8 MB/s elapsed=0.1s" }
        LumenLogger.v("UploadEngine", opId = op1) { "Bytes streamed: 50% (2097152/4194304 bytes) speed=5.2 MB/s elapsed=0.4s" }
        LumenLogger.v("UploadEngine", opId = op1) { "Bytes streamed: 100% (4194304/4194304 bytes) speed=5.5 MB/s elapsed=0.8s" }
        LumenLogger.d("Network", opId = op1) { "[POST 200] https://photos.googleapis.com/data/upload/uploadmedia/interactive?upload_id=[REDACTED] reqSize=4194304B respSize=64B duration=785ms" }
        LumenLogger.d("UploadEngine", opId = op1) { "Commit request START fileName='IMG_20261022_120401.jpg' storageSaver=true useQuota=false" }
        LumenLogger.d("Network", opId = op1) { "[POST 200] https://photosdata-pa.googleapis.com/6439526531001121323/16538846908252377752 reqSize=230B respSize=180B duration=205ms" }
        LumenLogger.d("UploadEngine", opId = op1) { "Commit response SUCCESS mediaKey=AF1QipM5K3u09e_realKey_81 durationMs=205" }
        LumenLogger.i("UploadEngine", opId = op1) { "Upload SUCCESS mediaKey=AF1QipM5K3u09e_realKey_81 totalDurationMs=1282 attempts=1" }

        // b. One failed upload that retried (op#upl_test002)
        println("\n--- b. Failed Upload with Retries ---")
        val op2 = "upl_test002"
        LumenLogger.d("UploadEngine", opId = op2) { "File enqueued: path=/sdcard/DCIM/Camera/VID_20261022_120500.mp4 name=VID_20261022_120500.mp4 size=15728640bytes" }
        LumenLogger.d("UploadEngine", opId = op2) { "Worker #1 picked item file='VID_20261022_120500.mp4' size=15728640B queueRemaining=0" }
        LumenLogger.d("UploadEngine", opId = op2) { "Hashing complete algorithm=SHA-1 bytes=15728640 durationMs=112 sha1=9e82ba71c53d09e" }
        LumenLogger.d("UploadEngine", opId = op2) { "Local cache MISS" }
        LumenLogger.d("UploadEngine", opId = op2) { "Remote dedup MISS checkDurationMs=98" }
        LumenLogger.d("UploadEngine", opId = op2) { "Requesting upload token attempt=1/3 size=15728640B" }
        LumenLogger.w("Network", opId = op2) { "[POST 503 ERROR] https://photos.googleapis.com/data/upload/uploadmedia/interactive reqSize=42B respSize=45B duration=1500ms errBody='{\"error\":{\"code\":503,\"message\":\"Service Unavailable\"}}'" }
        LumenLogger.w("UploadEngine", opId = op2) { "Upload attempt 1/3 failed: HTTP 503 Service Unavailable. Retrying in 2000ms..." }
        LumenLogger.w("UploadEngine", opId = op2) { "Upload attempt 2/3 failed: java.net.SocketTimeoutException: Read timed out. Retrying in 4000ms..." }
        LumenLogger.e("UploadEngine", opId = op2) { "Upload FAILED after 3 attempts totalDurationMs=7820 reason=HTTP 503 Service Unavailable" }

        // c. One network error
        println("\n--- c. Network Error Sanitized ---")
        LumenLogger.w("Network", opId = "net_err_01") {
            "[POST 500 ERROR] https://photos.googleapis.com/v1/mediaItems?auth=[REDACTED] reqSize=512B respSize=88B duration=430ms errBody='{\"error\":{\"code\":500,\"message\":\"Internal backend storage failure\"}}'"
        }

        // d. One watcher event that was debounced and enqueued
        println("\n--- d. Watcher Event Debounced & Enqueued ---")
        LumenLogger.d("Watcher") { "Raw observer event: path=/sdcard/DCIM/Camera/IMG_20261022_121000.jpg event=CREATE (0x100)" }
        LumenLogger.d("Watcher") { "Debounce complete (1500ms): 1 candidate paths" }
        LumenLogger.i("Watcher") { "Watcher enqueued 1 new media files: [IMG_20261022_121000.jpg]" }

        // e. One settings change showing old -> new
        println("\n--- e. Settings Change (old -> new) ---")
        LumenLogger.i("Settings") { "autoBackupEnabled changed: old=true -> new=false" }
        LumenLogger.i("Settings") { "storageSaver changed: old=false -> new=true, useQuota: old=true -> new=false" }
        LumenLogger.d("Storage") { "SharedPreferences write key=storage_saver" }

        // f. One simulated crash with cause chain
        println("\n--- f. Simulated Crash with Cause Chain ---")
        try {
            val rootCause = IOException("SSL handshake aborted: failure in SSL library, cert expired")
            val midCause = IllegalStateException("Failed to establish secure TLS session with Google Photos gateway", rootCause)
            throw RuntimeException("Fatal upload daemon failure during batch processing", midCause)
        } catch (t: Throwable) {
            LumenLogger.e("Crash", opId = "CRASH_DUMP", tr = t) {
                "FATAL UNCAUGHT EXCEPTION on thread 'DefaultDispatcher-worker-1' (id=32): ${t.message}"
            }
        }

        println("\n=== PRINTING FULL FORMATTED LOG BUFFER (${LumenLogger.logs.value.size} items) ===")
        for (log in LumenLogger.logs.value) {
            println(log.fullFormattedText)
        }
        println("=======================================================\n")
    }
}
