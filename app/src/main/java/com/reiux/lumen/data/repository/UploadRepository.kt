package com.reiux.lumen.data.repository

import android.content.Context
import android.util.Log
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.data.model.UploadItem
import com.reiux.lumen.data.model.UploadStatus
import com.reiux.lumen.data.network.GoogleAuthClient
import com.reiux.lumen.data.network.GooglePhotosClient
import com.reiux.lumen.util.DeviceConditions
import com.reiux.lumen.util.FileHasher
import com.reiux.lumen.util.LumenLogger
import com.reiux.lumen.util.MediaFileReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class UploadSpeedTracker {
    private var lastCalculationTime = System.currentTimeMillis()
    private val bytesAccumulator = AtomicLong(0L)
    private var smoothedSpeedBps = 0.0

    fun addBytes(bytes: Long) {
        if (bytes > 0) {
            bytesAccumulator.addAndGet(bytes)
        }
    }

    fun tick(): String? {
        val now = System.currentTimeMillis()
        val elapsedMs = now - lastCalculationTime
        if (elapsedMs < 300) {
            return if (smoothedSpeedBps > 100) formatSpeed(smoothedSpeedBps) else null
        }

        val bytes = bytesAccumulator.getAndSet(0L)
        lastCalculationTime = now

        val instantaneousBps = (bytes.toDouble() * 1000.0) / elapsedMs.toDouble()
        smoothedSpeedBps = if (smoothedSpeedBps == 0.0) {
            instantaneousBps
        } else {
            (0.35 * instantaneousBps) + (0.65 * smoothedSpeedBps)
        }

        return if (smoothedSpeedBps > 100) formatSpeed(smoothedSpeedBps) else null
    }

    fun reset() {
        bytesAccumulator.set(0L)
        smoothedSpeedBps = 0.0
        lastCalculationTime = System.currentTimeMillis()
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1024.0 * 1024.0 -> {
                String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            }
            bytesPerSec >= 1024.0 -> {
                String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024.0)
            }
            else -> {
                String.format(Locale.US, "%.0f B/s", bytesPerSec)
            }
        }
    }
}

class UploadRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val authClient: GoogleAuthClient = GoogleAuthClient(),
    private val photosClient: GooglePhotosClient = GooglePhotosClient(),
    private val hashCache: UploadHashCache = UploadHashCache(context)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var uploadJob: Job? = null
    private var speedTickerJob: Job? = null
    private val speedTracker = UploadSpeedTracker()

    private val _queue = MutableStateFlow<List<UploadItem>>(emptyList())
    val queue: StateFlow<List<UploadItem>> = _queue.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadSpeed = MutableStateFlow<String?>(null)
    val uploadSpeed: StateFlow<String?> = _uploadSpeed.asStateFlow()

    private val _conditionNotice = MutableStateFlow<String?>(null)
    val conditionNotice: StateFlow<String?> = _conditionNotice.asStateFlow()

    private val _currentUploadingItem = MutableStateFlow<UploadItem?>(null)
    val currentUploadingItem: StateFlow<UploadItem?> = _currentUploadingItem.asStateFlow()

    private val _completedCount = MutableStateFlow(0)
    val completedCount: StateFlow<Int> = _completedCount.asStateFlow()

    private fun getActiveUploadingItem(): UploadItem? {
        val q = _queue.value
        return q.firstOrNull { it.status == UploadStatus.UPLOADING }
            ?: q.firstOrNull { it.status == UploadStatus.COMMITTING }
            ?: q.firstOrNull { it.status == UploadStatus.CHECKING }
            ?: q.firstOrNull { it.status == UploadStatus.HASHING }
    }

    fun enqueueFiles(files: List<File>, account: GoogleAccount, storageSaver: Boolean, useQuota: Boolean = false) {
        val activePaths = _queue.value
            .filter {
                it.status == UploadStatus.QUEUED ||
                it.status == UploadStatus.HASHING ||
                it.status == UploadStatus.CHECKING ||
                it.status == UploadStatus.UPLOADING ||
                it.status == UploadStatus.COMMITTING
            }
            .map { it.filePath }
            .toSet()

        val filesToEnqueue = files.filter { it.absolutePath !in activePaths }
        if (filesToEnqueue.isNotEmpty()) {
            val pathsToEnqueue = filesToEnqueue.map { it.absolutePath }.toSet()
            // Clear any stale completed, skipped, or failed items for these exact paths so they get re-evaluated fresh
            val remainingQueue = _queue.value.filter { it.filePath !in pathsToEnqueue }
            val newItems = filesToEnqueue.map { file ->
                val id = UUID.randomUUID().toString()
                val opId = "upl_${id.take(8)}"
                LumenLogger.d("UploadEngine", opId = opId) {
                    "File enqueued: path=${file.absolutePath} name=${file.name} size=${file.length()}bytes"
                }
                UploadItem(
                    id = id,
                    filePath = file.absolutePath,
                    fileName = file.name,
                    fileSize = file.length(),
                    status = UploadStatus.QUEUED
                )
            }
            _queue.value = remainingQueue + newItems
            LumenLogger.i("UploadEngine") {
                "Enqueued ${newItems.size} files for upload. Total queue size: ${_queue.value.size}"
            }
        }
        startQueue(account, storageSaver, useQuota)
    }

    fun startQueue(account: GoogleAccount, storageSaver: Boolean, useQuota: Boolean = false) {
        if (_isUploading.value) return

        LumenLogger.i("UploadEngine") {
            "Starting upload queue for ${LumenLogger.redact(account.email)} (StorageSaver: $storageSaver, UseQuota: $useQuota)"
        }
        speedTracker.reset()
        _uploadSpeed.value = null

        speedTickerJob?.cancel()
        speedTickerJob = scope.launch {
            while (isActive) {
                delay(500)
                if (_isUploading.value) {
                    _uploadSpeed.value = speedTracker.tick()
                }
            }
        }

        uploadJob = scope.launch {
            _isUploading.value = true
            try {
                processQueue(account, storageSaver, useQuota)
            } finally {
                _isUploading.value = false
                speedTickerJob?.cancel()
                speedTracker.reset()
                _uploadSpeed.value = null
                _currentUploadingItem.value = null
                _conditionNotice.value = null
                LumenLogger.i("UploadEngine") { "Upload queue completed / finished" }
            }
        }
    }

    fun pauseQueue() {
        cancelQueue()
    }

    fun clearCompleted() {
        synchronized(_queue) {
            val before = _queue.value.size
            _queue.value = _queue.value.filter {
                it.status != UploadStatus.COMPLETED &&
                it.status != UploadStatus.SKIPPED &&
                it.status != UploadStatus.FAILED
            }
            _currentUploadingItem.value = getActiveUploadingItem()
            LumenLogger.i("UploadEngine") { "Cleared completed items (removed ${before - _queue.value.size} items)" }
        }
    }

    fun cancelQueue() {
        val pendingCount = _queue.value.count { it.status == UploadStatus.QUEUED || it.status == UploadStatus.UPLOADING }
        LumenLogger.w("UploadEngine") { "Cancelling upload queue ($pendingCount pending items)" }
        uploadJob?.cancel()
        uploadJob = null
        speedTickerJob?.cancel()
        speedTracker.reset()
        _uploadSpeed.value = null
        _isUploading.value = false
        _currentUploadingItem.value = null
        _conditionNotice.value = null

        synchronized(_queue) {
            _queue.value = _queue.value.filter {
                it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED
            }
        }
    }

    private suspend fun processQueue(account: GoogleAccount, storageSaver: Boolean, useQuota: Boolean) {
        val bearerToken = try {
            authClient.getBearerToken(account)
        } catch (e: Exception) {
            LumenLogger.e("UploadEngine", tr = e) {
                "Authentication token retrieval failed for ${LumenLogger.redact(account.email)}: ${e.message}"
            }
            synchronized(_queue) {
                _queue.value = _queue.value.map {
                    if (it.status == UploadStatus.QUEUED) it.copy(status = UploadStatus.FAILED, error = "Authentication failed: ${e.message}") else it
                }
                _currentUploadingItem.value = getActiveUploadingItem()
            }
            return
        }

        val concurrency = settingsRepository.settings.value.uploadThreads.coerceIn(1, 20)
        LumenLogger.i("UploadEngine") { "processQueue started with $concurrency concurrent worker threads" }

        coroutineScope {
            val workers = List(concurrency) { workerIndex ->
                launch {
                    while (isActive) {
                        // Atomically claim the next QUEUED item and immediately mark it HASHING
                        var queueRemaining = 0
                        val nextItem = synchronized(_queue) {
                            val item = _queue.value.firstOrNull { it.status == UploadStatus.QUEUED }
                            if (item != null) {
                                _queue.value = _queue.value.map {
                                    if (it.id == item.id) it.copy(status = UploadStatus.HASHING) else it
                                }
                                _currentUploadingItem.value = getActiveUploadingItem()
                                queueRemaining = _queue.value.count { it.status == UploadStatus.QUEUED }
                            }
                            item
                        } ?: break

                        val opId = "upl_${nextItem.id.take(8)}"
                        LumenLogger.d("UploadEngine", opId = opId) {
                            "Worker #$workerIndex picked item file='${nextItem.fileName}' size=${nextItem.fileSize}B queueRemaining=$queueRemaining"
                        }

                        // Enforce Wi-Fi and Charging constraints
                        var conditionLogged = false
                        while (isActive) {
                            val settings = settingsRepository.settings.value
                            val isWifiRequired = settings.wifiOnly
                            val isChargingRequired = settings.chargingOnly

                            val wifiOk = !isWifiRequired || DeviceConditions.isWifiConnected(context)
                            val chargingOk = !isChargingRequired || DeviceConditions.isDeviceCharging(context)

                            if (!wifiOk) {
                                _conditionNotice.value = "Waiting for Wi-Fi"
                                if (!conditionLogged) {
                                    LumenLogger.w("UploadEngine", opId = opId) {
                                        "Worker #$workerIndex waiting: Wi-Fi required but device is on cellular or offline"
                                    }
                                    conditionLogged = true
                                }
                                delay(2000)
                                continue
                            }
                            if (!chargingOk) {
                                _conditionNotice.value = "Waiting for charger"
                                if (!conditionLogged) {
                                    LumenLogger.w("UploadEngine", opId = opId) {
                                        "Worker #$workerIndex waiting: Charging required but device is running on battery"
                                    }
                                    conditionLogged = true
                                }
                                delay(2000)
                                continue
                            }
                            if (conditionLogged) {
                                LumenLogger.i("UploadEngine", opId = opId) {
                                    "Worker #$workerIndex: Device constraints satisfied (wifiOk=$wifiOk, chargingOk=$chargingOk), proceeding"
                                }
                            }
                            _conditionNotice.value = null
                            break
                        }

                        if (!isActive) break

                        try {
                            uploadSingleItem(nextItem, bearerToken, storageSaver, useQuota, account.email, opId, workerIndex)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            LumenLogger.e("UploadEngine", opId = opId, tr = e) {
                                "Unhandled error on item [${nextItem.fileName}]: ${e.message}"
                            }
                            updateItemError(nextItem.id, "Upload failed: ${e.message}")
                        }
                    }
                }
            }
            workers.joinAll()
        }
    }

    private suspend fun uploadSingleItem(
        item: UploadItem,
        bearerToken: String,
        storageSaver: Boolean,
        useQuota: Boolean,
        accountEmail: String,
        opId: String,
        workerIndex: Int
    ) {
        val overallStartTime = System.currentTimeMillis()
        LumenLogger.d("UploadEngine", opId = opId) {
            "Processing file='${item.fileName}' size=${item.fileSize}B on worker #$workerIndex"
        }
        updateItemStatus(item.id, UploadStatus.HASHING)

        // Retry reading the file up to 4 times (1s interval) to allow camera/writers to flush
        var file: File? = null
        for (attempt in 1..4) {
            file = MediaFileReader.getReadableFile(context, item.filePath, item.fileName)
            if (file != null && file.exists() && file.length() > 0) {
                break
            }
            if (attempt < 4) {
                LumenLogger.d("UploadEngine", opId = opId) {
                    "File not ready, waiting for write flush (attempt $attempt/4)..."
                }
                delay(1000)
            }
        }

        if (file == null || !file.exists() || file.length() == 0L) {
            val elapsed = System.currentTimeMillis() - overallStartTime
            LumenLogger.e("UploadEngine", opId = opId) {
                "File cannot be read after 4 attempts: path=${item.filePath} elapsedMs=$elapsed"
            }
            updateItemError(item.id, "File cannot be read")
            return
        }

        try {
            // Step 1: Hashing
            val hashStart = System.currentTimeMillis()
            val sha1Result = try {
                FileHasher.calculateSha1(file)
            } catch (e: Exception) {
                val hashDuration = System.currentTimeMillis() - hashStart
                LumenLogger.e("UploadEngine", opId = opId, tr = e) {
                    "Hash calculation failed after ${hashDuration}ms: ${e.message}"
                }
                updateItemError(item.id, "Hash calculation failed: ${e.message}")
                return
            }
            val hashDuration = System.currentTimeMillis() - hashStart
            LumenLogger.d("UploadEngine", opId = opId) {
                "Hashing complete algorithm=SHA-1 bytes=${file.length()} durationMs=$hashDuration sha1=${sha1Result.base64}"
            }

            // Step 2: Local cache check (fast, zero network)
            val cachedMediaKey = hashCache.get(accountEmail, sha1Result.base64, file.length())
            if (!cachedMediaKey.isNullOrEmpty()) {
                val totalMs = System.currentTimeMillis() - overallStartTime
                LumenLogger.i("UploadEngine", opId = opId) {
                    "Local cache HIT mediaKey=$cachedMediaKey reason=already_backed_up totalDurationMs=$totalMs. Skipping upload."
                }
                updateItemCompleted(item.id, cachedMediaKey, isSkipped = true)
                _completedCount.value += 1
                return
            }
            LumenLogger.d("UploadEngine", opId = opId) { "Local cache MISS" }

            // Step 3: Remote Google Photos dedup check
            updateItemStatus(item.id, UploadStatus.CHECKING)
            LumenLogger.d("UploadEngine", opId = opId) { "Remote dedup check START sha1=${sha1Result.base64}" }

            val remoteCheckStart = System.currentTimeMillis()
            val existingMediaKey = try {
                photosClient.findRemoteMediaByHash(bearerToken, sha1Result.bytes, opId)
            } catch (e: Exception) {
                val remoteCheckDuration = System.currentTimeMillis() - remoteCheckStart
                LumenLogger.w("UploadEngine", opId = opId, tr = e) {
                    "Remote dedup check network error durationMs=$remoteCheckDuration: ${e.message}"
                }
                null
            }
            val remoteCheckDuration = System.currentTimeMillis() - remoteCheckStart

            if (!existingMediaKey.isNullOrEmpty()) {
                val totalMs = System.currentTimeMillis() - overallStartTime
                LumenLogger.i("UploadEngine", opId = opId) {
                    "Remote dedup HIT in Google Photos cloud mediaKey=$existingMediaKey checkDurationMs=$remoteCheckDuration totalDurationMs=$totalMs. Skipping upload."
                }
                hashCache.put(
                    accountEmail = accountEmail,
                    sha1B64 = sha1Result.base64,
                    mediaKey = existingMediaKey,
                    fileSize = file.length(),
                    mtime = file.lastModified() / 1000
                )
                updateItemCompleted(item.id, existingMediaKey, isSkipped = true)
                _completedCount.value += 1
                return
            }
            LumenLogger.d("UploadEngine", opId = opId) { "Remote dedup MISS checkDurationMs=$remoteCheckDuration" }

            // Step 4 & 5: Upload transfer with retry logic (up to 3 attempts)
            var currentAttempt = 0
            val maxAttempts = 3
            var lastError: Exception? = null

            while (currentAttempt < maxAttempts) {
                currentAttempt++
                try {
                    // Stage 4a: Acquire upload token
                    updateItemStatus(item.id, UploadStatus.UPLOADING)
                    LumenLogger.d("UploadEngine", opId = opId) {
                        "Requesting upload token attempt=$currentAttempt/$maxAttempts size=${file.length()}B"
                    }

                    val uploadToken = photosClient.getUploadToken(bearerToken, sha1Result.base64, file.length(), opId)
                    LumenLogger.d("UploadEngine", opId = opId) { "Upload token acquired successfully" }

                    // Stage 4b: Stream file bytes with 10% progress milestones
                    LumenLogger.i("UploadEngine", opId = opId) {
                        "Streaming binary bytes (${file.length()} bytes)..."
                    }

                    var lastSentBytes = 0L
                    var lastLoggedPercent = 0
                    val streamStartTime = System.currentTimeMillis()

                    val commitToken = photosClient.uploadFile(uploadToken, file, bearerToken, opId) { bytesSent, total ->
                        val delta = bytesSent - lastSentBytes
                        if (delta > 0) {
                            speedTracker.addBytes(delta)
                            lastSentBytes = bytesSent
                        }
                        updateItemProgress(item.id, bytesSent)

                        val percent = if (total > 0) ((bytesSent * 100) / total).toInt() else 0
                        if (percent >= lastLoggedPercent + 10 || (percent == 100 && lastLoggedPercent < 100)) {
                            lastLoggedPercent = (percent / 10) * 10
                            val elapsedSec = (System.currentTimeMillis() - streamStartTime).coerceAtLeast(1) / 1000.0
                            val speedBps = if (elapsedSec > 0) (bytesSent / elapsedSec) else 0.0
                            val speedStr = if (speedBps >= 1024 * 1024) {
                                String.format(Locale.US, "%.1f MB/s", speedBps / (1024 * 1024))
                            } else {
                                String.format(Locale.US, "%.1f KB/s", speedBps / 1024)
                            }
                            LumenLogger.v("UploadEngine", opId = opId) {
                                "Bytes streamed: $percent% ($bytesSent/$total bytes) speed=$speedStr elapsed=${String.format(Locale.US, "%.1fs", elapsedSec)}"
                            }
                        }
                    }

                    // Stage 5: Commit upload to Google Photos library
                    updateItemStatus(item.id, UploadStatus.COMMITTING)
                    LumenLogger.d("UploadEngine", opId = opId) {
                        "Commit request START fileName='${item.fileName}' storageSaver=$storageSaver useQuota=$useQuota"
                    }

                    val commitStart = System.currentTimeMillis()
                    val timestampSec = file.lastModified() / 1000
                    val mediaKey = photosClient.commitUpload(
                        bearerToken,
                        commitToken,
                        item.fileName,
                        sha1Result.bytes,
                        timestampSec,
                        storageSaver,
                        useQuota,
                        opId
                    )
                    val commitDuration = System.currentTimeMillis() - commitStart
                    LumenLogger.d("UploadEngine", opId = opId) {
                        "Commit response SUCCESS mediaKey=$mediaKey durationMs=$commitDuration"
                    }

                    // Stage 6: Cache uploaded mediaKey locally
                    hashCache.put(
                        accountEmail = accountEmail,
                        sha1B64 = sha1Result.base64,
                        mediaKey = mediaKey,
                        fileSize = file.length(),
                        mtime = file.lastModified() / 1000
                    )

                    val totalDuration = System.currentTimeMillis() - overallStartTime
                    LumenLogger.i("UploadEngine", opId = opId) {
                        "Upload SUCCESS mediaKey=$mediaKey totalDurationMs=$totalDuration attempts=$currentAttempt"
                    }
                    updateItemCompleted(item.id, mediaKey, isSkipped = false)
                    _completedCount.value += 1
                    return // Success!

                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    lastError = e
                    if (currentAttempt < maxAttempts) {
                        val backoffDelayMs = (currentAttempt * 2000L).coerceAtMost(6000L)
                        LumenLogger.w("UploadEngine", opId = opId, tr = e) {
                            "Upload attempt $currentAttempt/$maxAttempts failed: ${e.message}. Retrying in ${backoffDelayMs}ms..."
                        }
                        delay(backoffDelayMs)
                    } else {
                        val totalDuration = System.currentTimeMillis() - overallStartTime
                        LumenLogger.e("UploadEngine", opId = opId, tr = e) {
                            "Upload FAILED after $maxAttempts attempts totalDurationMs=$totalDuration reason=${e.message}"
                        }
                    }
                }
            }

            // If we exhausted all retry attempts
            updateItemError(item.id, "Upload failed after $maxAttempts attempts: ${lastError?.message}")

        } finally {
            MediaFileReader.cleanupStagedFile(context, file)
        }
    }

    private fun updateItemStatus(id: String, status: UploadStatus) {
        synchronized(_queue) {
            _queue.value = _queue.value.map {
                if (it.id == id) it.copy(status = status) else it
            }
            _currentUploadingItem.value = getActiveUploadingItem()
        }
    }

    private fun updateItemProgress(id: String, bytesUploaded: Long) {
        synchronized(_queue) {
            _queue.value = _queue.value.map {
                if (it.id == id) it.copy(bytesUploaded = bytesUploaded) else it
            }
            _currentUploadingItem.value = getActiveUploadingItem()
        }
    }

    private fun updateItemCompleted(id: String, mediaKey: String, isSkipped: Boolean) {
        val status = if (isSkipped) UploadStatus.SKIPPED else UploadStatus.COMPLETED
        synchronized(_queue) {
            _queue.value = _queue.value.map {
                if (it.id == id) it.copy(status = status, mediaKey = mediaKey, bytesUploaded = it.fileSize) else it
            }
            _currentUploadingItem.value = getActiveUploadingItem()
        }
    }

    private fun updateItemError(id: String, error: String) {
        synchronized(_queue) {
            _queue.value = _queue.value.map {
                if (it.id == id) it.copy(status = UploadStatus.FAILED, error = error) else it
            }
            _currentUploadingItem.value = getActiveUploadingItem()
        }
    }
}
