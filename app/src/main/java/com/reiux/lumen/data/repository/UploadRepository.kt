package com.reiux.lumen.data.repository

import android.content.Context
import android.util.Log
import com.reiux.lumen.data.model.AccountBlock
import com.reiux.lumen.data.model.AuthFailureKind
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.data.model.UploadItem
import com.reiux.lumen.data.model.UploadStatus
import com.reiux.lumen.data.network.GoogleAuthClient
import com.reiux.lumen.data.network.GooglePhotosClient
import com.reiux.lumen.util.DeviceConditions
import com.reiux.lumen.util.FileHasher
import com.reiux.lumen.util.LumenLogger
import com.reiux.lumen.util.MediaFileReader
import com.reiux.lumen.util.ScannedFile
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
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
    private val accountRepository: AccountRepository? = null,
    private val authClient: GoogleAuthClient = GoogleAuthClient(),
    private val photosClient: GooglePhotosClient = GooglePhotosClient(),
    private val hashCache: UploadHashCache = UploadHashCache(context)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var uploadJob: Job? = null
    private var speedTickerJob: Job? = null
    private val speedTracker = UploadSpeedTracker()
    private val albumCreationLocks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    private val remoteDedupMutex = Mutex()
    private var lastRemoteDedupTime = 0L

    init {
        scope.launch {
            settingsRepository.settings
                .map { it.folders.filter { f -> !f.enabled }.map { f -> f.path } }
                .distinctUntilChanged()
                .collect { disabledPaths ->
                    if (disabledPaths.isNotEmpty()) {
                        pruneQueuedItemsForDisabledFolders(disabledPaths)
                    }
                }
        }
    }

    fun pruneQueuedItemsForDisabledFolders(disabledFolderPaths: List<String>) {
        val current = _queue.value
        if (current.isEmpty() || disabledFolderPaths.isEmpty()) return

        val (toDrop, toKeep) = current.partition { item ->
            disabledFolderPaths.any { path ->
                val folder = com.reiux.lumen.data.model.BackupFolder(path, "", enabled = false)
                com.reiux.lumen.util.FolderWatcherManager.isPathMatchingFolder(item.filePath, folder)
            }
        }
        if (toDrop.isNotEmpty()) {
            _queue.value = toKeep
            for (dropped in toDrop) {
                LumenLogger.i("UploadEngine") { "Dropped queued item '${dropped.fileName}' (${dropped.filePath}) because folder is disabled." }
            }
        }
    }

    suspend fun fetchRemoteAlbums(account: GoogleAccount): List<com.reiux.lumen.data.network.GoogleAlbum> {
        return try {
            val token = authClient.getBearerToken(account)
            photosClient.listAlbums(token)
        } catch (e: Exception) {
            LumenLogger.w("UploadEngine") { "fetchRemoteAlbums failed: ${e.message}" }
            emptyList()
        }
    }

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

    private val _skippedCount = MutableStateFlow(0)
    val skippedCount: StateFlow<Int> = _skippedCount.asStateFlow()

    private val _accountBlock = MutableStateFlow<AccountBlock?>(null)
    val accountBlock: StateFlow<AccountBlock?> = _accountBlock.asStateFlow()

    fun getAccountBlock(email: String): AccountBlock? {
        val current = _accountBlock.value
        if (current != null && current.accountEmail.equals(email, ignoreCase = true)) {
            if (current.kind == AuthFailureKind.RATE_LIMITED && current.retryAfterTimestamp != null && System.currentTimeMillis() >= current.retryAfterTimestamp) {
                clearAccountBlock(email)
                return null
            }
            return current
        }
        val persisted = settingsRepository.getAccountBlock(email)
        if (persisted != null) {
            _accountBlock.value = persisted
        }
        return persisted
    }

    fun triggerAccountBlock(email: String, kind: AuthFailureKind, rawDetail: String? = null, retryAfterSec: Long? = null) {
        val retryAfterTs = if (retryAfterSec != null) System.currentTimeMillis() + (retryAfterSec * 1000L) else if (kind == AuthFailureKind.RATE_LIMITED) System.currentTimeMillis() + (30 * 1000L) else null
        val block = AccountBlock(
            kind = kind,
            timestamp = System.currentTimeMillis(),
            accountEmail = email,
            retryAfterTimestamp = retryAfterTs,
            rawDetail = rawDetail
        )

        uploadJob?.cancel()
        uploadJob = null
        speedTickerJob?.cancel()
        speedTracker.reset()
        _uploadSpeed.value = null
        _isUploading.value = false
        _currentUploadingItem.value = null
        _conditionNotice.value = null

        synchronized(_queue) {
            _queue.value = _queue.value.map { item ->
                if (item.status == UploadStatus.QUEUED ||
                    item.status == UploadStatus.HASHING ||
                    item.status == UploadStatus.CHECKING ||
                    item.status == UploadStatus.UPLOADING ||
                    item.status == UploadStatus.COMMITTING
                ) {
                    val err = when (kind) {
                        AuthFailureKind.RATE_LIMITED -> "Rate limited by Google Photos"
                        AuthFailureKind.SIGN_IN_REQUIRED -> "Sign in required"
                        AuthFailureKind.ACCOUNT_RESTRICTED -> "Account restricted by Google"
                        else -> "Upload paused"
                    }
                    item.copy(status = UploadStatus.FAILED, error = err, bytesUploaded = 0L)
                } else item
            }
        }

        settingsRepository.setAccountBlock(block)
        _accountBlock.value = block

        LumenLogger.logBlocked(
            runId = "CircuitBreaker",
            reasonCode = kind.name,
            userMessage = when (kind) {
                AuthFailureKind.SIGN_IN_REQUIRED -> "Google is asking you to sign in again. Uploads are paused."
                AuthFailureKind.ACCOUNT_RESTRICTED -> "Google has restricted this account. Uploads are stopped."
                AuthFailureKind.RATE_LIMITED -> "Google upload rate limit reached. Uploads paused."
                else -> "Upload paused due to account protection."
            },
            suggestedAction = when (kind) {
                AuthFailureKind.SIGN_IN_REQUIRED -> "Sign in again in Accounts tab."
                AuthFailureKind.ACCOUNT_RESTRICTED -> "Check your Google account directly; do not keep retrying."
                AuthFailureKind.RATE_LIMITED -> "Uploads will resume automatically when rate limit expires."
                else -> "Check account status."
            }
        )

        postBlockNotification(block)
    }

    fun clearAccountBlock(email: String) {
        settingsRepository.clearAccountBlock(email)
        if (_accountBlock.value?.accountEmail.equals(email, ignoreCase = true)) {
            _accountBlock.value = null
        }
    }

    private fun postBlockNotification(block: AccountBlock) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            val title = when (block.kind) {
                AuthFailureKind.SIGN_IN_REQUIRED -> "Lumen: Sign-in Required"
                AuthFailureKind.ACCOUNT_RESTRICTED -> "Lumen: Account Restricted"
                AuthFailureKind.RATE_LIMITED -> "Lumen: Rate Limited"
                else -> "Lumen: Account Action Required"
            }
            val text = when (block.kind) {
                AuthFailureKind.SIGN_IN_REQUIRED -> "Google asked you to sign in again. Uploads paused."
                AuthFailureKind.ACCOUNT_RESTRICTED -> "Google restricted this account. Uploads stopped."
                AuthFailureKind.RATE_LIMITED -> "Rate limit reached. Will resume automatically."
                else -> "Uploads paused."
            }
            val notif = androidx.core.app.NotificationCompat.Builder(context, "lumen_backup_channel")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(9001, notif)
        } catch (_: Exception) {}
    }

    private fun getActiveUploadingItem(): UploadItem? {
        val q = _queue.value
        return q.firstOrNull { it.status == UploadStatus.UPLOADING }
            ?: q.firstOrNull { it.status == UploadStatus.COMMITTING }
            ?: q.firstOrNull { it.status == UploadStatus.CHECKING }
            ?: q.firstOrNull { it.status == UploadStatus.HASHING }
    }

    fun enqueueFiles(
        files: List<File>,
        account: GoogleAccount,
        storageSaver: Boolean,
        useQuota: Boolean = false,
        targetAlbumMode: com.reiux.lumen.data.model.AlbumTargetMode = com.reiux.lumen.data.model.AlbumTargetMode.NONE,
        customAlbumName: String? = null,
        customAlbumNickname: String? = null,
        originalFilePaths: List<String?>? = null
    ) {
        val scanned = files.mapIndexed { index, file ->
            ScannedFile(file, file.length(), originalFilePaths?.getOrNull(index) ?: file.absolutePath)
        }
        enqueueScannedFiles(scanned, account, storageSaver, useQuota, targetAlbumMode, customAlbumName, customAlbumNickname)
    }

    fun enqueueScannedFiles(
        scannedFiles: List<ScannedFile>,
        account: GoogleAccount,
        storageSaver: Boolean,
        useQuota: Boolean = false,
        targetAlbumMode: com.reiux.lumen.data.model.AlbumTargetMode = com.reiux.lumen.data.model.AlbumTargetMode.NONE,
        customAlbumName: String? = null,
        customAlbumNickname: String? = null
    ) {
        if (scannedFiles.isEmpty()) return

        val activeKeys = _queue.value
            .filter {
                it.status == UploadStatus.QUEUED ||
                it.status == UploadStatus.HASHING ||
                it.status == UploadStatus.CHECKING ||
                it.status == UploadStatus.UPLOADING ||
                it.status == UploadStatus.COMMITTING
            }
            .map { "${it.accountEmail ?: account.email}:${it.filePath}" }
            .toSet()

        val uniqueFiles = scannedFiles.filter { "${account.email}:${it.file.absolutePath}" !in activeKeys }
        if (uniqueFiles.isNotEmpty()) {
            val keysToEnqueue = uniqueFiles.map { "${account.email}:${it.file.absolutePath}" }.toSet()

            val newItems = uniqueFiles.map { sf ->
                UploadItem(
                    id = UUID.randomUUID().toString(),
                    filePath = sf.file.absolutePath,
                    fileName = sf.file.name,
                    fileSize = sf.size,
                    status = UploadStatus.QUEUED,
                    targetAlbumMode = targetAlbumMode,
                    customAlbumName = customAlbumName,
                    customAlbumNickname = customAlbumNickname,
                    accountEmail = account.email,
                    originalFilePath = sf.originalFilePath ?: sf.file.absolutePath
                )
            }

            val totalBytes = newItems.sumOf { it.fileSize }
            LumenLogger.i("UploadEngine") {
                "Enqueued ${newItems.size} files for ${LumenLogger.redact(account.email)} (${totalBytes / (1024 * 1024)}MB total). Total queue size: ${_queue.value.size + newItems.size}"
            }

            synchronized(_queue) {
                val remainingQueue = _queue.value.filter { "${it.accountEmail ?: account.email}:${it.filePath}" !in keysToEnqueue }
                val updatedQueue = remainingQueue.toMutableList()

                // Chunk enqueue in batches of 500 for UI responsiveness
                newItems.chunked(500).forEach { chunk ->
                    updatedQueue.addAll(chunk)
                    _queue.value = updatedQueue.toList()
                }
            }
        }
        startQueue(account, storageSaver, useQuota)
    }

    fun startQueue(account: GoogleAccount, storageSaver: Boolean, useQuota: Boolean = false) {
        val block = getAccountBlock(account.email)
        if (block != null) {
            LumenLogger.i("UploadEngine") { "Skipped: account blocked (${block.kind})" }
            return
        }

        if (_isUploading.value) return

        _completedCount.value = 0
        _skippedCount.value = 0
        
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
                launch(Dispatchers.Main) {
                    com.reiux.lumen.util.EasterEggsManager.checkLateNightToast(context)
                }
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
                it.status != UploadStatus.FAILED &&
                it.status != UploadStatus.STOPPED
            }
            _currentUploadingItem.value = getActiveUploadingItem()
            LumenLogger.i("UploadEngine") { "Cleared completed/stopped items (removed ${before - _queue.value.size} items)" }
        }
    }

    fun retryFailedItems(itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        synchronized(_queue) {
            _queue.value = _queue.value.map { item ->
                if (item.id in itemIds) {
                    item.copy(status = UploadStatus.QUEUED, error = null, bytesUploaded = 0L)
                } else item
            }
            _currentUploadingItem.value = getActiveUploadingItem()
        }
        LumenLogger.i("UploadEngine") { "Re-queued ${itemIds.size} failed/stopped items for retry" }
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
        val concurrency = settingsRepository.settings.value.uploadThreads.coerceIn(1, 20)
        LumenLogger.i("UploadEngine") { "processQueue started with $concurrency concurrent worker threads" }

        coroutineScope {
            val workers = List(concurrency) { workerIndex ->
                launch {
                    while (isActive) {
                        // Atomically claim the next QUEUED item and immediately mark it HASHING
                        var queueRemaining = 0
                        val nextItem = synchronized(_queue) {
                            val item = _queue.value.firstOrNull {
                                it.status == UploadStatus.QUEUED && getAccountBlock(it.accountEmail ?: account.email) == null
                            }
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

                        val itemAccountEmail = nextItem.accountEmail ?: account.email
                        val itemAccount = accountRepository?.accounts?.value?.find { it.email.equals(itemAccountEmail, ignoreCase = true) }
                            ?: GoogleAccount(itemAccountEmail, itemAccountEmail)

                        val tokenProvider: suspend () -> String = { authClient.getBearerToken(itemAccount) }

                        try {
                            uploadSingleItem(nextItem, tokenProvider, storageSaver, useQuota, itemAccountEmail, opId, workerIndex)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            LumenLogger.e("UploadEngine", opId = opId, tr = e) {
                                "Unhandled error on item [${nextItem.fileName}] for account ${LumenLogger.redact(itemAccountEmail)}: ${e.message}"
                            }
                            val errorMsg = if (e is com.reiux.lumen.util.AuthFailureException) {
                                "Authentication failed for $itemAccountEmail: ${e.message}"
                            } else {
                                "Upload failed: ${e.message}"
                            }
                            updateItemError(nextItem.id, errorMsg)
                        }
                    }
                }
            }
            workers.joinAll()
        }
    }

    private suspend fun uploadSingleItem(
        item: UploadItem,
        tokenProvider: suspend () -> String,
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
                
                val activeToken = tokenProvider()
                handleAlbumAssignment(activeToken, accountEmail, file.absolutePath, item, cachedMediaKey, opId)
                
                updateItemCompleted(item.id, cachedMediaKey, isSkipped = true)
                return
            }
            LumenLogger.d("UploadEngine", opId = opId) { "Local cache MISS" }

            // Step 3: Remote Google Photos dedup check
            updateItemStatus(item.id, UploadStatus.CHECKING)
            LumenLogger.d("UploadEngine", opId = opId) { "Remote dedup check START sha1=${sha1Result.base64}" }

            val bearerToken = try {
                tokenProvider()
            } catch (e: com.reiux.lumen.util.AuthFailureException) {
                triggerAccountBlock(accountEmail, e.kind, e.rawDetail, e.retryAfterSec)
                throw e
            }

            val remoteCheckStart = System.currentTimeMillis()
            val existingMediaKey = remoteDedupMutex.withLock {
                val elapsed = System.currentTimeMillis() - lastRemoteDedupTime
                if (elapsed < 60) {
                    delay(60 - elapsed)
                }
                lastRemoteDedupTime = System.currentTimeMillis()

                try {
                    photosClient.findRemoteMediaByHash(bearerToken, sha1Result.bytes, opId)
                } catch (e: com.reiux.lumen.util.AuthFailureException) {
                    if (e.kind == AuthFailureKind.TOKEN_EXPIRED) {
                        authClient.invalidateCache(accountEmail)
                        val freshToken = tokenProvider()
                        photosClient.findRemoteMediaByHash(freshToken, sha1Result.bytes, opId)
                    } else if (e.kind == AuthFailureKind.SIGN_IN_REQUIRED || e.kind == AuthFailureKind.ACCOUNT_RESTRICTED || e.kind == AuthFailureKind.RATE_LIMITED) {
                        triggerAccountBlock(accountEmail, e.kind, e.rawDetail, e.retryAfterSec)
                        throw e
                    } else null
                } catch (e: Exception) {
                    val remoteCheckDuration = System.currentTimeMillis() - remoteCheckStart
                    LumenLogger.w("UploadEngine", opId = opId, tr = e) {
                        "Remote dedup check network error durationMs=$remoteCheckDuration: ${e.message}"
                    }
                    null
                }
            }
            val remoteCheckDuration = System.currentTimeMillis() - remoteCheckStart

            if (!existingMediaKey.isNullOrEmpty()) {
                val totalMs = System.currentTimeMillis() - overallStartTime
                LumenLogger.i("UploadEngine", opId = opId) {
                    "Remote dedup HIT in Google Photos cloud mediaKey=$existingMediaKey checkDurationMs=$remoteCheckDuration totalDurationMs=$totalMs. Skipping upload."
                }
                
                handleAlbumAssignment(bearerToken, accountEmail, file.absolutePath, item, existingMediaKey, opId)

                hashCache.put(
                    accountEmail = accountEmail,
                    sha1B64 = sha1Result.base64,
                    mediaKey = existingMediaKey,
                    fileSize = file.length(),
                    mtime = file.lastModified() / 1000
                )
                updateItemCompleted(item.id, existingMediaKey, isSkipped = true)
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
                    val activeToken = tokenProvider()

                    // Check if file was already registered by Google Photos before starting a full re-upload
                    if (currentAttempt > 1) {
                        LumenLogger.d("UploadEngine", opId = opId) {
                            "Retrying upload attempt $currentAttempt/$maxAttempts. Checking if file was registered by server during previous commit..."
                        }
                        val retryCheckKey = try {
                            photosClient.findRemoteMediaByHash(activeToken, sha1Result.bytes, opId)
                        } catch (_: Exception) { null }

                        if (!retryCheckKey.isNullOrEmpty()) {
                            val totalMs = System.currentTimeMillis() - overallStartTime
                            LumenLogger.i("UploadEngine", opId = opId) {
                                "Ambiguous commit recovery HIT: File was registered by server mediaKey=$retryCheckKey. Skipping re-upload."
                            }
                            
                            handleAlbumAssignment(activeToken, accountEmail, file.absolutePath, item, retryCheckKey, opId)

                            hashCache.put(
                                accountEmail = accountEmail,
                                sha1B64 = sha1Result.base64,
                                mediaKey = retryCheckKey,
                                fileSize = file.length(),
                                mtime = file.lastModified() / 1000
                            )
                            updateItemCompleted(item.id, retryCheckKey, isSkipped = false)
                            handlePostBackupDeletion(item, file, opId)
                            return
                        }
                    }

                    // Stage 4a: Acquire upload token
                    updateItemStatus(item.id, UploadStatus.UPLOADING)
                    LumenLogger.d("UploadEngine", opId = opId) {
                        "Requesting upload token attempt=$currentAttempt/$maxAttempts size=${file.length()}B"
                    }

                    val uploadToken = photosClient.getUploadToken(activeToken, sha1Result.base64, file.length(), opId)
                    LumenLogger.d("UploadEngine", opId = opId) { "Upload token acquired successfully" }

                    // Stage 4b: Stream file bytes
                    LumenLogger.i("UploadEngine", opId = opId) {
                        "Streaming binary bytes (${file.length()} bytes)..."
                    }

                    var lastSentBytes = 0L
                    var lastLoggedPercent = 0
                    val streamStartTime = System.currentTimeMillis()

                    val commitToken = photosClient.uploadFile(uploadToken, file, activeToken, opId) { bytesSent, total ->
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

                    // Stage 5: Commit upload
                    updateItemStatus(item.id, UploadStatus.COMMITTING)
                    LumenLogger.d("UploadEngine", opId = opId) {
                        "Commit request START fileName='${item.fileName}' storageSaver=$storageSaver useQuota=$useQuota"
                    }

                    val commitStart = System.currentTimeMillis()
                    val timestampSec = file.lastModified() / 1000
                    val mediaKey = photosClient.commitUpload(
                        activeToken,
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

                    handleAlbumAssignment(activeToken, accountEmail, file.absolutePath, item, mediaKey, opId)

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
                    handlePostBackupDeletion(item, file, opId)

                    val (_, milestoneTriggered) = settingsRepository.recordRealUploads(1)
                    if (milestoneTriggered != null) {
                        scope.launch(Dispatchers.Main) {
                            com.reiux.lumen.util.EasterEggsManager.checkMilestoneToast(context, milestoneTriggered)
                        }
                    }
                    return // Success!

                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    lastError = e

                    if (shouldAttemptCommitRecovery(e, getItemStatus(item.id))) {
                        LumenLogger.w("UploadEngine", opId = opId) {
                            "Commit failed (${e.message}). Checking if Google Photos server committed the media..."
                        }
                        val postCommitKey = try {
                            val freshToken = tokenProvider()
                            photosClient.findRemoteMediaByHash(freshToken, sha1Result.bytes, opId)
                        } catch (_: Exception) { null }

                        if (!postCommitKey.isNullOrEmpty()) {
                            val totalMs = System.currentTimeMillis() - overallStartTime
                            LumenLogger.i("UploadEngine", opId = opId) {
                                "Ambiguous commit recovery SUCCESS: Server registered mediaKey=$postCommitKey despite network drop. Duration: ${totalMs}ms."
                            }
                            hashCache.put(
                                accountEmail = accountEmail,
                                sha1B64 = sha1Result.base64,
                                mediaKey = postCommitKey,
                                fileSize = file.length(),
                                mtime = file.lastModified() / 1000
                            )
                            updateItemCompleted(item.id, postCommitKey, isSkipped = false)
                            handlePostBackupDeletion(item, file, opId)
                            return
                        }
                    }

                    if (e is com.reiux.lumen.util.AuthFailureException) {
                        when (e.kind) {
                            AuthFailureKind.TOKEN_EXPIRED -> {
                                LumenLogger.w("UploadEngine", opId = opId) { "Token expired. Invalidating cache and retrying once..." }
                                authClient.invalidateCache(accountEmail)
                                try {
                                    val freshToken = tokenProvider()
                                    val uploadToken = photosClient.getUploadToken(freshToken, sha1Result.base64, file.length(), opId)
                                    val commitToken = photosClient.uploadFile(uploadToken, file, freshToken, opId)
                                    val mediaKey = photosClient.commitUpload(freshToken, commitToken, item.fileName, sha1Result.bytes, file.lastModified() / 1000, storageSaver, useQuota, opId)

                                    updateItemCompleted(item.id, mediaKey, isSkipped = false)
                                    handlePostBackupDeletion(item, file, opId)
                                    return
                                } catch (retryErr: Exception) {
                                    LumenLogger.e("UploadEngine", opId = opId, tr = retryErr) { "Token refresh retry failed: ${retryErr.message}" }
                                    triggerAccountBlock(accountEmail, AuthFailureKind.SIGN_IN_REQUIRED, retryErr.message)
                                    throw retryErr
                                }
                            }
                            AuthFailureKind.SIGN_IN_REQUIRED,
                            AuthFailureKind.ACCOUNT_RESTRICTED,
                            AuthFailureKind.RATE_LIMITED -> {
                                triggerAccountBlock(accountEmail, e.kind, e.rawDetail, e.retryAfterSec)
                                throw e
                            }
                            else -> {
                                // TRANSIENT
                            }
                        }
                    }

                    if (currentAttempt < maxAttempts) {
                        val backoffDelayMs = computeRetryDelayMs(currentAttempt)
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

    private suspend fun handleAlbumAssignment(
        token: String,
        accountEmail: String,
        filePath: String,
        item: UploadItem,
        mediaKey: String,
        opId: String? = null
    ) {
        val (albumMode, customName, customNickname) = if (item.targetAlbumMode != com.reiux.lumen.data.model.AlbumTargetMode.NONE) {
            Triple(item.targetAlbumMode, item.customAlbumName, item.customAlbumNickname)
        } else {
            val folders = settingsRepository.getFoldersForAccount(accountEmail)
            val matchingFolder = folders.firstOrNull { f ->
                f.enabled && f.targetAlbumMode != com.reiux.lumen.data.model.AlbumTargetMode.NONE && (
                    filePath.startsWith(f.path) || File(filePath).parentFile?.absolutePath == File(f.path).absolutePath
                )
            }
            if (matchingFolder != null) {
                Triple(matchingFolder.targetAlbumMode, matchingFolder.customAlbumName, matchingFolder.customAlbumNickname)
            } else {
                Triple(com.reiux.lumen.data.model.AlbumTargetMode.NONE, null, null)
            }
        }

        if (albumMode == com.reiux.lumen.data.model.AlbumTargetMode.NONE) return

        val rawTitle = when (albumMode) {
            com.reiux.lumen.data.model.AlbumTargetMode.AUTO_FOLDER_NAME -> {
                if (item.targetAlbumMode != com.reiux.lumen.data.model.AlbumTargetMode.NONE) {
                    val currentMonth = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.US).format(java.util.Date())
                    "Uploads $currentMonth"
                } else {
                    val folders = settingsRepository.getFoldersForAccount(accountEmail)
                    val f = folders.firstOrNull { filePath.startsWith(it.path) || File(filePath).parentFile?.absolutePath == File(it.path).absolutePath }
                    f?.displayName ?: "Uploads"
                }
            }
            com.reiux.lumen.data.model.AlbumTargetMode.CUSTOM_NAME -> customName?.ifBlank { "Uploads" } ?: "Uploads"
            else -> return
        }

        val albumTitleOrKey = photosClient.resolveAlbumInput(rawTitle)
        if (albumTitleOrKey.isBlank()) return

        val keyRegex = Regex("AF1Qip[A-Za-z0-9_-]+")
        
        fun handle404(e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("404") || msg.contains("Requested entity was not found") || msg.contains("NOT_FOUND")) {
                LumenLogger.e("UploadEngine", opId = opId) { "Target album no longer exists in Google Photos!" }
                if (rawTitle.isNotBlank()) {
                    settingsRepository.markAlbumMissing(accountEmail, rawTitle)
                }
                synchronized(_queue) {
                    _queue.value = _queue.value.map { qItem ->
                        if (qItem.customAlbumName == rawTitle || qItem.customAlbumNickname == rawTitle) {
                            qItem.copy(
                                status = UploadStatus.STOPPED,
                                error = "Album not found in Google Photos"
                            )
                        } else {
                            qItem
                        }
                    }
                }
                throw IOException("Target album no longer exists in Google Photos")
            }
        }

        if (albumTitleOrKey.startsWith("AF1Qip") || keyRegex.matches(albumTitleOrKey)) {
            LumenLogger.i("UploadEngine", opId = opId) { "Adding mediaKey $mediaKey to direct album key $albumTitleOrKey for account $accountEmail..." }
            try {
                photosClient.addMediaToAlbum(token, albumTitleOrKey, listOf(mediaKey), opId = opId)
                if (customNickname != null) {
                    settingsRepository.saveAlbumMediaKey(accountEmail, customNickname, albumTitleOrKey)
                }
            } catch (e: Exception) {
                handle404(e)
                throw e
            }
            return
        }

        var albumMediaKey = settingsRepository.getAlbumMediaKey(accountEmail, albumTitleOrKey)
        if (albumMediaKey != null && !albumMediaKey.startsWith("AF1Qip") && !keyRegex.matches(albumMediaKey)) {
            albumMediaKey = null
        }

        if (albumMediaKey.isNullOrEmpty()) {
            val lockKey = "$accountEmail:${albumTitleOrKey.lowercase().trim()}"
            val mutex = albumCreationLocks.computeIfAbsent(lockKey) { Mutex() }
            
            mutex.withLock {
                albumMediaKey = settingsRepository.getAlbumMediaKey(accountEmail, albumTitleOrKey)
                if (albumMediaKey != null && !albumMediaKey.startsWith("AF1Qip") && !keyRegex.matches(albumMediaKey)) {
                    albumMediaKey = null
                }

                if (albumMediaKey.isNullOrEmpty()) {
                    val existingAlbums = try {
                        photosClient.listAlbums(token, opId = opId)
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val matchedAlbum = existingAlbums.firstOrNull { it.title.equals(albumTitleOrKey, ignoreCase = true) }
                    if (matchedAlbum != null) {
                        LumenLogger.i("UploadEngine", opId = opId) { "Found existing Google Photos album '${matchedAlbum.title}' (${matchedAlbum.mediaKey}) for account $accountEmail" }
                        val mKey = matchedAlbum.mediaKey
                        albumMediaKey = mKey
                        settingsRepository.saveAlbumMediaKey(accountEmail, albumTitleOrKey, mKey)
                        if (!customNickname.isNullOrBlank()) {
                            settingsRepository.saveAlbumMediaKey(accountEmail, customNickname, mKey)
                        }
                    } else {
                        LumenLogger.i("UploadEngine", opId = opId) { "Creating new Google Photos album '$albumTitleOrKey' with initial mediaKey $mediaKey for account $accountEmail..." }
                        try {
                            val createdKey = photosClient.createAlbum(token, albumTitleOrKey, initialMediaKey = mediaKey, opId = opId)
                            settingsRepository.saveAlbumMediaKey(accountEmail, albumTitleOrKey, createdKey)
                            if (!customNickname.isNullOrBlank()) {
                                settingsRepository.saveAlbumMediaKey(accountEmail, customNickname, createdKey)
                            }
                            return
                        } catch (createErr: Exception) {
                            LumenLogger.e("UploadEngine", opId = opId, tr = createErr) { "Failed to create new album '$albumTitleOrKey': ${createErr.message}" }
                            throw IOException("Failed to create new album in Google Photos")
                        }
                    }
                }
            }
        }

        if (!albumMediaKey.isNullOrEmpty()) {
            LumenLogger.i("UploadEngine", opId = opId) { "Adding mediaKey $mediaKey to existing album '$albumTitleOrKey' ($albumMediaKey)..." }
            try {
                photosClient.addMediaToAlbum(token, albumMediaKey!!, listOf(mediaKey), opId = opId)
            } catch (e: Exception) {
                handle404(e)
                throw e
            }
        }
    }

    private fun getItemStatus(id: String): UploadStatus? {
        return _queue.value.firstOrNull { it.id == id }?.status
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
        if (isSkipped) {
            _skippedCount.value += 1
        } else {
            _completedCount.value += 1
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

    private fun handlePostBackupDeletion(item: UploadItem, file: File, opId: String) {
        // Clean up temporary staging cache copy if this item was staged
        if (file.absolutePath.contains("/cache/staging/")) {
            try {
                file.delete()
            } catch (_: Exception) {}
        }

        if (settingsRepository.settings.value.deleteAfterBackup) {
            val targetPath = item.originalFilePath ?: item.filePath
            val targetFile = File(targetPath)
            if (targetFile.exists() && !targetFile.absolutePath.contains("/cache/staging/")) {
                try {
                    val deleted = targetFile.delete()
                    LumenLogger.i("UploadEngine", opId = opId) {
                        "Post-backup original file deletion: path=$targetPath deleted=$deleted"
                    }
                    if (deleted) {
                        try {
                            android.media.MediaScannerConnection.scanFile(
                                context,
                                arrayOf(targetPath),
                                null,
                                null
                            )
                        } catch (_: Exception) {}
                    }
                } catch (delEx: Exception) {
                    LumenLogger.w("UploadEngine", opId = opId, tr = delEx) {
                        "Failed to delete original file after backup: $targetPath"
                    }
                }
            }
        }
    }

    companion object {
        internal fun shouldAttemptCommitRecovery(error: Throwable, status: UploadStatus?): Boolean {
            if (status != UploadStatus.COMMITTING) return false
            if (error is com.reiux.lumen.util.AuthFailureException) {
                val kind = error.kind
                if (kind == AuthFailureKind.ACCOUNT_RESTRICTED ||
                    kind == AuthFailureKind.SIGN_IN_REQUIRED ||
                    kind == AuthFailureKind.RATE_LIMITED ||
                    kind == AuthFailureKind.TOKEN_EXPIRED
                ) {
                    return false
                }
            }
            return true
        }

        internal fun computeRetryDelayMs(attempt: Int): Long {
            val base = (attempt * 2000L).coerceAtMost(6000L)
            val jitter = kotlin.random.Random.nextLong(250L, 1000L)
            return base + jitter
        }
    }
}
