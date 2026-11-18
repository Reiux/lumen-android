package com.reiux.lumen.data.repository

import android.content.Context
import android.util.Log
import com.reiux.lumen.data.model.AccountBlock
import com.reiux.lumen.data.model.AlbumTargetMode
import com.reiux.lumen.data.model.AuthFailureKind
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.data.model.UploadItem
import com.reiux.lumen.data.model.UploadStatus
import com.reiux.lumen.data.network.GoogleAuthClient
import com.reiux.lumen.data.network.GooglePhotosClient
import com.reiux.lumen.util.DeviceConditions
import com.reiux.lumen.util.FileHasher
import com.reiux.lumen.util.LogLevel
import com.reiux.lumen.util.LumenLogger
import com.reiux.lumen.util.MediaFileReader
import com.reiux.lumen.util.ScannedFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

sealed class AlbumSyncState {
    object Idle : AlbumSyncState()
    data class Syncing(
        val currentFolderName: String,
        val processedFiles: Int,
        val totalFiles: Int,
        val addedCount: Int
    ) : AlbumSyncState()
    data class Done(
        val matchedCount: Int,
        val addedCount: Int,
        val notInCloudCount: Int,
        val folderCount: Int
    ) : AlbumSyncState()
}

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
    private val jobLock = Any()
    private val speedTracker = UploadSpeedTracker()
    private val albumCreationLocks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    private val remoteDedupMutex = Mutex()
    private var lastRemoteDedupTime = 0L

    init {
        scope.launch {
            settingsRepository.settings
                .map { it.folders.filter { f -> !f.enabled } }
                .distinctUntilChanged()
                .collect { disabledFolders ->
                    if (disabledFolders.isNotEmpty()) {
                        pruneQueuedItemsForDisabledFolders(disabledFolders)
                    }
                }
        }
    }

    fun pruneQueuedItemsForDisabledFolders(disabledFolders: List<com.reiux.lumen.data.model.BackupFolder>) {
        if (disabledFolders.isEmpty()) return
        val droppedItems = mutableListOf<UploadItem>()
        synchronized(_queue) {
            val current = _queue.value
            if (current.isEmpty()) return
            val (toDrop, toKeep) = current.partition { item ->
                // Never prune or delete staged files of in-flight worker items or completed history;
                // only prune pending QUEUED / STOPPED items.
                val canSafelyPrune = item.status == UploadStatus.QUEUED || item.status == UploadStatus.STOPPED
                if (!canSafelyPrune) {
                    false
                } else {
                    val checkPath = item.originalFilePath ?: item.filePath
                    disabledFolders.any { disabled ->
                        val accountMatches = disabled.accountEmail.isNullOrBlank() ||
                            item.accountEmail.isNullOrBlank() ||
                            disabled.accountEmail.equals(item.accountEmail, ignoreCase = true)
                        accountMatches && com.reiux.lumen.util.FolderWatcherManager.isPathMatchingFolder(checkPath, disabled)
                    }
                }
            }
            if (toDrop.isNotEmpty()) {
                _queue.value = toKeep
                _currentUploadingItem.value = getActiveUploadingItem()
                droppedItems.addAll(toDrop)
            }
        }
        for (dropped in droppedItems) {
            MediaFileReader.cleanupStagedFile(context, File(dropped.filePath))
            LumenLogger.i("UploadEngine") { "Dropped queued item '${dropped.fileName}' (${dropped.filePath}) because folder is disabled." }
        }
    }

    fun pruneQueuedItemsForDisabledFolders(disabledFolderPaths: List<String>, accountEmail: String? = null) {
        val folders = disabledFolderPaths.map { path ->
            com.reiux.lumen.data.model.BackupFolder(path, "", enabled = false, accountEmail = accountEmail)
        }
        pruneQueuedItemsForDisabledFolders(folders)
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

    /** Per-item wall clock of the last queue progress emission (client reports per 32KB chunk). */
    private val lastProgressEmitAt = ConcurrentHashMap<String, Long>()

    private val _completedCount = MutableStateFlow(0)
    val completedCount: StateFlow<Int> = _completedCount.asStateFlow()

    private val _skippedCount = MutableStateFlow(0)
    val skippedCount: StateFlow<Int> = _skippedCount.asStateFlow()

    private val _accountBlock = MutableStateFlow<AccountBlock?>(null)
    val accountBlock: StateFlow<AccountBlock?> = _accountBlock.asStateFlow()

    private val _albumSyncState = MutableStateFlow<AlbumSyncState>(AlbumSyncState.Idle)
    val albumSyncState: StateFlow<AlbumSyncState> = _albumSyncState.asStateFlow()
    private var albumSyncJob: Job? = null
    private val albumSyncJobLock = Any()
    private val pendingSyncFolders = LinkedHashMap<String, BackupFolder>()

    /** Pure lookup safe to call inside StateFlow combine blocks without mutating _accountBlock. */
    fun peekAccountBlock(email: String): AccountBlock? {
        val current = _accountBlock.value
        if (current != null && current.accountEmail.equals(email, ignoreCase = true)) {
            if (!SettingsRepository.isBlockExpired(current)) {
                return current
            }
        }
        return settingsRepository.getAccountBlock(email)
    }

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
        if (persisted != null && _accountBlock.value != persisted) {
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

        var hasOtherActiveItems = false
        val stagedPathsToCleanup = mutableListOf<String>()

        synchronized(_queue) {
            _queue.value = _queue.value.map { item ->
                val isTargetAccount = item.accountEmail == null || item.accountEmail.equals(email, ignoreCase = true)
                val isActiveStatus = item.status == UploadStatus.QUEUED ||
                    item.status == UploadStatus.HASHING ||
                    item.status == UploadStatus.CHECKING ||
                    item.status == UploadStatus.UPLOADING ||
                    item.status == UploadStatus.COMMITTING

                if (isActiveStatus && isTargetAccount) {
                    if (item.status == UploadStatus.QUEUED) {
                        stagedPathsToCleanup.add(item.filePath)
                    }
                    val err = when (kind) {
                        AuthFailureKind.RATE_LIMITED -> "Rate limited by Google Photos"
                        AuthFailureKind.SIGN_IN_REQUIRED -> "Sign in required"
                        AuthFailureKind.ACCOUNT_RESTRICTED -> "Account restricted by Google"
                        else -> "Upload paused"
                    }
                    item.copy(status = UploadStatus.FAILED, error = err, bytesUploaded = 0L)
                } else {
                    if (isActiveStatus) hasOtherActiveItems = true
                    item
                }
            }
            _currentUploadingItem.value = getActiveUploadingItem()
        }

        stagedPathsToCleanup.forEach { path ->
            MediaFileReader.cleanupStagedFile(context, File(path))
        }

        if (!hasOtherActiveItems) {
            detachAndCancelJobs()
            speedTracker.reset()
            _uploadSpeed.value = null
            _isUploading.value = false
            _currentUploadingItem.value = null
            _conditionNotice.value = null
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

    fun removeAccountData(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty()) return

        val removedPaths = mutableListOf<String>()
        var jobsToCancel: Pair<Job?, Job?>? = null
        var shouldResetUploadState = false

        synchronized(jobLock) {
            var hasRemainingActiveItems = false
            synchronized(_queue) {
                val (removed, keep) = _queue.value.partition { item ->
                    item.accountEmail?.trim()?.equals(cleanEmail, ignoreCase = true) == true
                }
                if (removed.isNotEmpty()) {
                    _queue.value = keep
                    for (item in removed) {
                        lastProgressEmitAt.remove(item.id)
                        removedPaths.add(item.filePath)
                    }
                    _completedCount.value = keep.count { it.status == UploadStatus.COMPLETED }
                    _skippedCount.value = keep.count { it.status == UploadStatus.SKIPPED }
                    _currentUploadingItem.value = getActiveUploadingItem()
                }
                hasRemainingActiveItems = keep.any { item ->
                    item.status == UploadStatus.QUEUED ||
                        item.status == UploadStatus.HASHING ||
                        item.status == UploadStatus.CHECKING ||
                        item.status == UploadStatus.UPLOADING ||
                        item.status == UploadStatus.COMMITTING
                }
            }

            if (!hasRemainingActiveItems && (uploadJob != null || speedTickerJob != null || _isUploading.value)) {
                jobsToCancel = uploadJob to speedTickerJob
                uploadJob = null
                speedTickerJob = null
                shouldResetUploadState = true
            }
        }

        jobsToCancel?.let { (job, ticker) ->
            job?.cancel()
            ticker?.cancel()
        }
        if (shouldResetUploadState) {
            speedTracker.reset()
            _uploadSpeed.value = null
            _isUploading.value = false
            _currentUploadingItem.value = null
            _conditionNotice.value = null
        }

        for (path in removedPaths) {
            MediaFileReader.cleanupStagedFile(context, File(path))
        }

        val lockPrefix = "${cleanEmail.lowercase(Locale.ROOT)}:"
        val lockIter = albumCreationLocks.keys.iterator()
        while (lockIter.hasNext()) {
            if (lockIter.next().startsWith(lockPrefix)) {
                lockIter.remove()
            }
        }

        synchronized(albumSyncJobLock) {
            val syncIter = pendingSyncFolders.keys.iterator()
            while (syncIter.hasNext()) {
                if (syncIter.next().startsWith(lockPrefix)) {
                    syncIter.remove()
                }
            }
        }

        authClient.invalidateCache(cleanEmail)
        clearAccountBlock(cleanEmail)
        if (_accountBlock.value?.accountEmail?.trim()?.equals(cleanEmail, ignoreCase = true) == true) {
            _accountBlock.value = null
        }

        scope.launch {
            hashCache.deleteForAccount(cleanEmail)
        }

        LumenLogger.i("UploadEngine") {
            "Removed account queue/state for ${LumenLogger.redact(cleanEmail)} (removedQueueItems=${removedPaths.size})"
        }
    }

    suspend fun purgeAccountHashCache(email: String) {
        hashCache.deleteForAccount(email)
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
        originalFilePaths: List<String?>? = null,
        allowDeleteAfterBackup: Boolean = false
    ) {
        val scanned = files.mapIndexed { index, file ->
            ScannedFile(file, file.length(), originalFilePaths?.getOrNull(index) ?: file.absolutePath)
        }
        enqueueScannedFiles(
            scannedFiles = scanned,
            account = account,
            storageSaver = storageSaver,
            useQuota = useQuota,
            targetAlbumMode = targetAlbumMode,
            customAlbumName = customAlbumName,
            customAlbumNickname = customAlbumNickname,
            allowDeleteAfterBackup = allowDeleteAfterBackup
        )
    }

    fun enqueueScannedFiles(
        scannedFiles: List<ScannedFile>,
        account: GoogleAccount,
        storageSaver: Boolean,
        useQuota: Boolean = false,
        targetAlbumMode: com.reiux.lumen.data.model.AlbumTargetMode = com.reiux.lumen.data.model.AlbumTargetMode.NONE,
        customAlbumName: String? = null,
        customAlbumNickname: String? = null,
        allowDeleteAfterBackup: Boolean = false
    ) {
        if (scannedFiles.isEmpty()) return

        var enqueuedCount = 0
        var totalBytes = 0L
        var newQueueSize = 0

        synchronized(_queue) {
            val currentQueue = _queue.value
            val activeKeys = currentQueue
                .filter {
                    it.status == UploadStatus.QUEUED ||
                        it.status == UploadStatus.HASHING ||
                        it.status == UploadStatus.CHECKING ||
                        it.status == UploadStatus.UPLOADING ||
                        it.status == UploadStatus.COMMITTING
                }
                .map { "${(it.accountEmail ?: account.email).lowercase()}:${it.filePath}" }
                .toSet()

            val seenInBatch = HashSet<String>()
            val uniqueFiles = scannedFiles.filter { sf ->
                val key = "${account.email.lowercase()}:${sf.file.absolutePath}"
                key !in activeKeys && seenInBatch.add(key)
            }

            if (uniqueFiles.isNotEmpty()) {
                val keysToEnqueue = uniqueFiles.map { "${account.email.lowercase()}:${it.file.absolutePath}" }.toSet()
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
                        originalFilePath = sf.originalFilePath ?: sf.file.absolutePath,
                        // Capture the policy per item: a second account's items can join a queue
                        // that was started for a first account (startQueue early-returns then),
                        // so workers must read the flags from the item, not the queue args.
                        storageSaver = storageSaver,
                        useQuota = useQuota,
                        allowDeleteAfterBackup = allowDeleteAfterBackup
                    )
                }

                val remainingQueue = currentQueue.filter {
                    "${(it.accountEmail ?: account.email).lowercase()}:${it.filePath}" !in keysToEnqueue
                }
                val updatedQueue = ArrayList<UploadItem>(remainingQueue.size + newItems.size).apply {
                    addAll(remainingQueue)
                    addAll(newItems)
                }
                _queue.value = updatedQueue
                enqueuedCount = newItems.size
                totalBytes = newItems.sumOf { it.fileSize }
                newQueueSize = updatedQueue.size
            }
        }

        if (enqueuedCount > 0) {
            LumenLogger.i("UploadEngine") {
                "Enqueued $enqueuedCount files for ${LumenLogger.redact(account.email)} (${totalBytes / (1024 * 1024)}MB total). Total queue size: $newQueueSize"
            }
        }
        startQueue(account, storageSaver, useQuota)
    }

    private fun hasEligibleQueuedItems(fallbackAccountEmail: String): Boolean {
        return synchronized(_queue) {
            _queue.value.any {
                it.status == UploadStatus.QUEUED && peekAccountBlock(it.accountEmail ?: fallbackAccountEmail) == null
            }
        }
    }

    fun startQueue(account: GoogleAccount, storageSaver: Boolean, useQuota: Boolean = false) {
        val block = getAccountBlock(account.email)
        if (block != null && !hasEligibleQueuedItems(account.email)) {
            LumenLogger.i("UploadEngine") { "Skipped: account blocked (${block.kind})" }
            return
        }

        // Check-and-start must be atomic: auto-backup triggers (folder watcher, media job,
        // boot) and the UI can call this concurrently. Without the lock, two callers could both
        // launch a processQueue job, leaving one orphaned and impossible to cancel.
        synchronized(jobLock) {
            if (uploadJob?.isActive == true) return
            if (!hasEligibleQueuedItems(account.email)) return

            synchronized(_queue) {
                _completedCount.value = _queue.value.count { it.status == UploadStatus.COMPLETED }
                _skippedCount.value = _queue.value.count { it.status == UploadStatus.SKIPPED }
            }
            lastProgressEmitAt.clear()

            LumenLogger.i("UploadEngine") {
                "Starting upload queue for ${LumenLogger.redact(account.email)} (StorageSaver: $storageSaver, UseQuota: $useQuota)"
            }
            speedTracker.reset()
            _uploadSpeed.value = null
            _isUploading.value = true

            speedTickerJob?.cancel()
            val ticker = scope.launch {
                while (isActive) {
                    delay(500)
                    if (_isUploading.value) {
                        _uploadSpeed.value = speedTracker.tick()
                    }
                }
            }
            speedTickerJob = ticker

            lateinit var job: Job
            job = scope.launch(start = CoroutineStart.LAZY) {
                var finishedCleanly = false
                try {
                    while (isActive) {
                        processQueue(account, storageSaver, useQuota)
                        // Re-check atomically under jobLock -> _queue so items enqueued right as
                        // workers exited are drained instead of stranded behind a dying uploadJob.
                        val shouldContinue = synchronized(jobLock) {
                            uploadJob === job && isActive && hasEligibleQueuedItems(account.email)
                        }
                        if (!shouldContinue) break
                    }
                    finishedCleanly = isActive
                } finally {
                    ticker.cancel()
                    // Only reset shared state if this job is still the current one and no new
                    // eligible items arrived before jobLock was acquired.
                    var relaunchNeeded = false
                    val isCurrent = synchronized(jobLock) {
                        if (uploadJob === job) {
                            uploadJob = null
                            if (finishedCleanly && hasEligibleQueuedItems(account.email)) {
                                relaunchNeeded = true
                            }
                            true
                        } else {
                            false
                        }
                    }
                    if (relaunchNeeded) {
                        startQueue(account, storageSaver, useQuota)
                    } else if (isCurrent) {
                        _isUploading.value = false
                        speedTracker.reset()
                        _uploadSpeed.value = null
                        _currentUploadingItem.value = null
                        _conditionNotice.value = null
                    }
                    LumenLogger.i("UploadEngine") { "Upload queue completed / finished" }
                    if (isCurrent && finishedCleanly && !relaunchNeeded) {
                        scope.launch(Dispatchers.Main) {
                            com.reiux.lumen.util.EasterEggsManager.checkLateNightToast(context)
                        }
                    }
                }
            }
            uploadJob = job
            job.start()
        }
    }

    /** Detaches the current upload/ticker jobs (so their cleanup won't touch shared state) and cancels them. */
    private fun detachAndCancelJobs() {
        val (job, ticker) = synchronized(jobLock) {
            val pair = uploadJob to speedTickerJob
            uploadJob = null
            speedTickerJob = null
            pair
        }
        job?.cancel()
        ticker?.cancel()
    }

    fun pauseQueue() {
        LumenLogger.i("UploadEngine") { "Pausing upload queue (preserving queued items and staged files)" }
        detachAndCancelJobs()
        speedTracker.reset()
        _uploadSpeed.value = null
        _isUploading.value = false
        _currentUploadingItem.value = null
        _conditionNotice.value = null

        synchronized(_queue) {
            lastProgressEmitAt.clear()
            _queue.value = _queue.value.map { item ->
                when (item.status) {
                    UploadStatus.HASHING,
                    UploadStatus.CHECKING,
                    UploadStatus.UPLOADING,
                    UploadStatus.COMMITTING -> item.copy(
                        status = UploadStatus.QUEUED,
                        bytesUploaded = 0L,
                        error = null
                    )
                    else -> item
                }
            }
        }
    }

    fun clearCompleted() {
        val removedPaths = mutableListOf<String>()
        synchronized(_queue) {
            val before = _queue.value.size
            val (keep, remove) = _queue.value.partition {
                it.status != UploadStatus.COMPLETED &&
                it.status != UploadStatus.SKIPPED &&
                it.status != UploadStatus.FAILED &&
                it.status != UploadStatus.STOPPED
            }
            _queue.value = keep
            remove.forEach { removedPaths.add(it.filePath) }
            _currentUploadingItem.value = getActiveUploadingItem()
            LumenLogger.i("UploadEngine") { "Cleared completed/stopped items (removed ${before - _queue.value.size} items)" }
        }
        removedPaths.forEach { path ->
            MediaFileReader.cleanupStagedFile(context, File(path))
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
        detachAndCancelJobs()
        speedTracker.reset()
        _uploadSpeed.value = null
        _isUploading.value = false
        _currentUploadingItem.value = null
        _conditionNotice.value = null

        val removedPaths = mutableListOf<String>()
        synchronized(_queue) {
            val (keep, remove) = _queue.value.partition {
                it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED
            }
            _queue.value = keep
            remove.forEach { removedPaths.add(it.filePath) }
        }
        removedPaths.forEach { path ->
            MediaFileReader.cleanupStagedFile(context, File(path))
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
                            ?: account.takeIf { it.email.equals(itemAccountEmail, ignoreCase = true) }
                        if (itemAccount == null) {
                            updateItemError(nextItem.id, "Account $itemAccountEmail is no longer signed in")
                            MediaFileReader.cleanupStagedFile(context, File(nextItem.filePath))
                            continue
                        }

                        val tokenProvider: suspend () -> String = { authClient.getBearerToken(itemAccount) }

                        try {
                            uploadSingleItem(nextItem, tokenProvider, nextItem.storageSaver, nextItem.useQuota, itemAccountEmail, opId, workerIndex)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            LumenLogger.log(
                                level = LogLevel.ERROR,
                                tag = "UploadEngine",
                                opId = opId,
                                tr = e,
                                fileName = nextItem.fileName,
                                userMessage = "Unhandled error on item for account ${LumenLogger.redact(itemAccountEmail)}"
                            ) {
                                "Unhandled error on item for account ${LumenLogger.redact(itemAccountEmail)}: ${e.message}"
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
            LumenLogger.log(
                level = LogLevel.ERROR,
                tag = "UploadEngine",
                opId = opId,
                fileName = item.fileName,
                userMessage = "File cannot be read after 4 attempts (elapsedMs=$elapsed)"
            ) {
                "File cannot be read after 4 attempts: path=${item.filePath} elapsedMs=$elapsed"
            }
            updateItemError(item.id, "File cannot be read")
            MediaFileReader.cleanupStagedFile(context, File(item.filePath))
            if (file != null && file.absolutePath != item.filePath) {
                MediaFileReader.cleanupStagedFile(context, file)
            }
            return
        }

        val albumMatchPath = item.originalFilePath ?: item.filePath
        try {
            // Step 1: Hashing
            val hashStart = System.currentTimeMillis()
            val sha1Result = try {
                FileHasher.calculateSha1(file)
            } catch (e: CancellationException) {
                throw e
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

                handleAlbumAssignmentWithTokenRefresh(tokenProvider, accountEmail, albumMatchPath, item, cachedMediaKey, opId)

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
                    if (e is CancellationException) throw e
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

                handleAlbumAssignmentWithTokenRefresh(tokenProvider, accountEmail, albumMatchPath, item, existingMediaKey, opId)

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
            var tokenRefreshRetryUsed = false
            var committedMediaKey: String? = null
            var hadPriorCommitAttempt = false

            while (currentAttempt < maxAttempts) {
                currentAttempt++
                try {
                    val alreadyCommittedKey = committedMediaKey
                    if (!alreadyCommittedKey.isNullOrEmpty()) {
                        handleAlbumAssignmentWithTokenRefresh(tokenProvider, accountEmail, albumMatchPath, item, alreadyCommittedKey, opId)
                        updateItemCompleted(item.id, alreadyCommittedKey, isSkipped = false)
                        handlePostBackupDeletion(item, file, opId)
                        val (_, milestoneTriggered) = settingsRepository.recordRealUploads(1)
                        if (milestoneTriggered != null) {
                            scope.launch(Dispatchers.Main) {
                                com.reiux.lumen.util.EasterEggsManager.checkMilestoneToast(context, milestoneTriggered)
                            }
                        }
                        return
                    }

                    val activeToken = tokenProvider()

                    // Check if file was already registered by Google Photos before starting a full re-upload
                    if (currentAttempt > 1 || hadPriorCommitAttempt) {
                        LumenLogger.d("UploadEngine", opId = opId) {
                            "Retrying upload attempt $currentAttempt/$maxAttempts. Checking if file was registered by server during previous commit..."
                        }
                        val retryCheckKey = try {
                            photosClient.findRemoteMediaByHash(activeToken, sha1Result.bytes, opId)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (ae: com.reiux.lumen.util.AuthFailureException) {
                            if (ae.kind == AuthFailureKind.TOKEN_EXPIRED) {
                                authClient.invalidateCache(accountEmail)
                                val refreshed = tokenProvider()
                                try { photosClient.findRemoteMediaByHash(refreshed, sha1Result.bytes, opId) } catch (ce2: CancellationException) { throw ce2 } catch (_: Exception) { null }
                            } else if (ae.kind == AuthFailureKind.SIGN_IN_REQUIRED || ae.kind == AuthFailureKind.ACCOUNT_RESTRICTED || ae.kind == AuthFailureKind.RATE_LIMITED) {
                                triggerAccountBlock(accountEmail, ae.kind, ae.rawDetail, ae.retryAfterSec)
                                throw ae
                            } else null
                        } catch (_: Exception) { null }

                        if (!retryCheckKey.isNullOrEmpty()) {
                            LumenLogger.i("UploadEngine", opId = opId) {
                                "Ambiguous commit recovery HIT: File was registered by server mediaKey=$retryCheckKey. Skipping re-upload."
                            }
                            committedMediaKey = retryCheckKey
                            hashCache.put(
                                accountEmail = accountEmail,
                                sha1B64 = sha1Result.base64,
                                mediaKey = retryCheckKey,
                                fileSize = file.length(),
                                mtime = file.lastModified() / 1000
                            )

                            handleAlbumAssignmentWithTokenRefresh(tokenProvider, accountEmail, albumMatchPath, item, retryCheckKey, opId)

                            updateItemCompleted(item.id, retryCheckKey, isSkipped = false)
                            handlePostBackupDeletion(item, file, opId)
                            val (_, milestoneTriggered) = settingsRepository.recordRealUploads(1)
                            if (milestoneTriggered != null) {
                                scope.launch(Dispatchers.Main) {
                                    com.reiux.lumen.util.EasterEggsManager.checkMilestoneToast(context, milestoneTriggered)
                                }
                            }
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
                    hadPriorCommitAttempt = true
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

                    // Stage 6: Cache uploaded mediaKey locally BEFORE album assignment so a transient
                    // album error or TOKEN_EXPIRED cannot lose the confirmed mediaKey or re-upload bytes
                    committedMediaKey = mediaKey
                    hashCache.put(
                        accountEmail = accountEmail,
                        sha1B64 = sha1Result.base64,
                        mediaKey = mediaKey,
                        fileSize = file.length(),
                        mtime = file.lastModified() / 1000
                    )

                    handleAlbumAssignmentWithTokenRefresh(tokenProvider, accountEmail, albumMatchPath, item, mediaKey, opId)

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

                    if (committedMediaKey == null && shouldAttemptCommitRecovery(e, getItemStatus(item.id))) {
                        LumenLogger.w("UploadEngine", opId = opId) {
                            "Commit failed (${e.message}). Checking if Google Photos server committed the media..."
                        }
                        val postCommitKey = try {
                            val freshToken = tokenProvider()
                            photosClient.findRemoteMediaByHash(freshToken, sha1Result.bytes, opId)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (ae: com.reiux.lumen.util.AuthFailureException) {
                            if (ae.kind == AuthFailureKind.TOKEN_EXPIRED) {
                                authClient.invalidateCache(accountEmail)
                                val refreshed = tokenProvider()
                                try { photosClient.findRemoteMediaByHash(refreshed, sha1Result.bytes, opId) } catch (ce2: CancellationException) { throw ce2 } catch (_: Exception) { null }
                            } else null
                        } catch (_: Exception) { null }

                        if (!postCommitKey.isNullOrEmpty()) {
                            val totalMs = System.currentTimeMillis() - overallStartTime
                            LumenLogger.i("UploadEngine", opId = opId) {
                                "Ambiguous commit recovery SUCCESS: Server registered mediaKey=$postCommitKey despite network drop. Duration: ${totalMs}ms."
                            }
                            committedMediaKey = postCommitKey
                            hashCache.put(
                                accountEmail = accountEmail,
                                sha1B64 = sha1Result.base64,
                                mediaKey = postCommitKey,
                                fileSize = file.length(),
                                mtime = file.lastModified() / 1000
                            )
                            try {
                                handleAlbumAssignmentWithTokenRefresh(tokenProvider, accountEmail, albumMatchPath, item, postCommitKey, opId)
                                updateItemCompleted(item.id, postCommitKey, isSkipped = false)
                                handlePostBackupDeletion(item, file, opId)
                                val (_, milestoneTriggered) = settingsRepository.recordRealUploads(1)
                                if (milestoneTriggered != null) {
                                    scope.launch(Dispatchers.Main) {
                                        com.reiux.lumen.util.EasterEggsManager.checkMilestoneToast(context, milestoneTriggered)
                                    }
                                }
                                return
                            } catch (albumErr: Exception) {
                                if (albumErr is CancellationException) throw albumErr
                                lastError = albumErr
                                LumenLogger.w("UploadEngine", opId = opId, tr = albumErr) {
                                    "Album assignment after commit recovery failed: ${albumErr.message}"
                                }
                            }
                        }
                    }

                    val activeErr = lastError ?: e
                    if (activeErr is com.reiux.lumen.util.AuthFailureException) {
                        if (activeErr.kind == AuthFailureKind.TOKEN_EXPIRED) {
                            LumenLogger.w("UploadEngine", opId = opId) { "Token expired. Invalidating cache and retrying..." }
                            authClient.invalidateCache(accountEmail)
                            if (!tokenRefreshRetryUsed) {
                                tokenRefreshRetryUsed = true
                                currentAttempt--
                            }
                        } else when (activeErr.kind) {
                            AuthFailureKind.SIGN_IN_REQUIRED,
                            AuthFailureKind.ACCOUNT_RESTRICTED,
                            AuthFailureKind.RATE_LIMITED -> {
                                triggerAccountBlock(accountEmail, activeErr.kind, activeErr.rawDetail, activeErr.retryAfterSec)
                                throw activeErr
                            }
                            else -> {
                                // TRANSIENT
                            }
                        }
                    }

                    if (activeErr.message?.contains("Target album no longer exists") == true) {
                        break
                    }

                    if (currentAttempt < maxAttempts) {
                        val backoffDelayMs = computeRetryDelayMs(currentAttempt)
                        LumenLogger.w("UploadEngine", opId = opId, tr = activeErr) {
                            "Upload attempt $currentAttempt/$maxAttempts failed: ${activeErr.message}. Retrying in ${backoffDelayMs}ms..."
                        }
                        delay(backoffDelayMs)
                    } else {
                        val totalDuration = System.currentTimeMillis() - overallStartTime
                        LumenLogger.e("UploadEngine", opId = opId, tr = activeErr) {
                            "Upload FAILED after $maxAttempts attempts totalDurationMs=$totalDuration reason=${activeErr.message}"
                        }
                    }
                }
            }

            // If we exhausted all retry attempts
            updateItemError(item.id, "Upload failed after $maxAttempts attempts: ${lastError?.message}")

        } finally {
            val finalStatus = getItemStatus(item.id)
            val shouldCleanupItemSource = finalStatus == null ||
                finalStatus == UploadStatus.COMPLETED ||
                finalStatus == UploadStatus.SKIPPED ||
                finalStatus == UploadStatus.FAILED
            if (file.absolutePath != item.filePath || shouldCleanupItemSource) {
                MediaFileReader.cleanupStagedFile(context, file)
            }
            if (shouldCleanupItemSource && file.absolutePath != item.filePath) {
                MediaFileReader.cleanupStagedFile(context, File(item.filePath))
            }
        }
    }

    private suspend fun handleAlbumAssignmentWithTokenRefresh(
        tokenProvider: suspend () -> String,
        accountEmail: String,
        filePath: String,
        item: UploadItem,
        mediaKey: String,
        opId: String? = null
    ) {
        val token = try {
            tokenProvider()
        } catch (e: com.reiux.lumen.util.AuthFailureException) {
            triggerAccountBlock(accountEmail, e.kind, e.rawDetail, e.retryAfterSec)
            throw e
        }

        try {
            handleAlbumAssignment(token, accountEmail, filePath, item, mediaKey, opId)
        } catch (e: com.reiux.lumen.util.AuthFailureException) {
            when (e.kind) {
                AuthFailureKind.TOKEN_EXPIRED -> {
                    LumenLogger.w("UploadEngine", opId = opId) { "Token expired during album assignment. Refreshing and retrying..." }
                    authClient.invalidateCache(accountEmail)
                    val freshToken = try {
                        tokenProvider()
                    } catch (refreshErr: com.reiux.lumen.util.AuthFailureException) {
                        triggerAccountBlock(accountEmail, refreshErr.kind, refreshErr.rawDetail, refreshErr.retryAfterSec)
                        throw refreshErr
                    }
                    handleAlbumAssignment(freshToken, accountEmail, filePath, item, mediaKey, opId)
                }
                AuthFailureKind.SIGN_IN_REQUIRED,
                AuthFailureKind.ACCOUNT_RESTRICTED,
                AuthFailureKind.RATE_LIMITED -> {
                    triggerAccountBlock(accountEmail, e.kind, e.rawDetail, e.retryAfterSec)
                    throw e
                }
                else -> throw e
            }
        }
    }

    private fun syncFolderKey(folder: BackupFolder): String {
        return "${folder.accountEmail?.lowercase()?.trim().orEmpty()}:${SettingsRepository.normalizeFolderPath(folder.path)}"
    }

    fun syncFoldersToAlbums(targetFolders: List<BackupFolder>? = null) {
        val candidates = (targetFolders ?: settingsRepository.getAllFolders().filter { it.enabled })
            .filter { it.targetAlbumMode != AlbumTargetMode.NONE && !it.accountEmail.isNullOrBlank() }

        synchronized(albumSyncJobLock) {
            if (candidates.isEmpty()) {
                if (albumSyncJob?.isActive != true) {
                    _albumSyncState.value = AlbumSyncState.Done(0, 0, 0, 0)
                }
                return
            }

            if (!DeviceConditions.isInternetAvailable(context)) {
                LumenLogger.w("AlbumSync") { "Cannot sync albums: no internet connection" }
                return
            }

            for (c in candidates) {
                pendingSyncFolders[syncFolderKey(c)] = c
            }

            if (albumSyncJob?.isActive == true) {
                LumenLogger.d("AlbumSync") { "Album sync already in progress; queued ${candidates.size} folder(s) for sequential sync" }
                return
            }

            albumSyncJob = scope.launch {
                val opId = "als_${UUID.randomUUID().toString().take(8)}"
                var totalMatched = 0
                var totalAdded = 0
                var totalNotInCloud = 0
                var syncedFolderCount = 0

                try {
                    while (isActive) {
                        val folder = synchronized(albumSyncJobLock) {
                            val firstKey = pendingSyncFolders.keys.firstOrNull()
                            if (firstKey != null) pendingSyncFolders.remove(firstKey) else null
                        } ?: break

                        coroutineContext.ensureActive()
                        val accounts = accountRepository?.accounts?.value.orEmpty()
                        val accountEmail = folder.accountEmail ?: continue
                        val account = accounts.firstOrNull { it.email.equals(accountEmail, ignoreCase = true) } ?: continue
                        if (getAccountBlock(account.email) != null) {
                            LumenLogger.w("AlbumSync", opId = opId) { "Skipping folder '${folder.displayName}': account ${account.email} is blocked" }
                            continue
                        }

                        val rawTitle = when (folder.targetAlbumMode) {
                            AlbumTargetMode.AUTO_FOLDER_NAME -> folder.displayName
                            AlbumTargetMode.CUSTOM_NAME -> folder.customAlbumName?.ifBlank { folder.displayName } ?: folder.displayName
                            AlbumTargetMode.NONE -> continue
                        }

                        _albumSyncState.value = AlbumSyncState.Syncing(
                            currentFolderName = folder.displayName,
                            processedFiles = 0,
                            totalFiles = 0,
                            addedCount = totalAdded
                        )

                        var scanResult = com.reiux.lumen.util.ScanProgress(0, 0, folder.displayName)
                        MediaFileReader.scanMediaFilesIncremental(context, listOf(folder.copy(enabled = true)))
                            .collect { progress ->
                                scanResult = progress
                            }

                        val filesToCheck = scanResult.scannedFiles
                        if (filesToCheck.isEmpty()) {
                            syncedFolderCount++
                            continue
                        }

                        val tokenProvider: suspend () -> String = { authClient.getBearerToken(account) }
                        val matchedKeys = LinkedHashSet<String>()

                        filesToCheck.forEachIndexed { index, scanned ->
                            coroutineContext.ensureActive()
                            _albumSyncState.value = AlbumSyncState.Syncing(
                                currentFolderName = folder.displayName,
                                processedFiles = index + 1,
                                totalFiles = filesToCheck.size,
                                addedCount = totalAdded
                            )

                            val readable = MediaFileReader.getReadableFile(context, scanned.file.absolutePath, scanned.file.name)
                            if (readable == null || !readable.exists() || readable.length() == 0L) {
                                return@forEachIndexed
                            }

                            try {
                                val sha1 = FileHasher.calculateSha1(readable)
                                var mediaKey = hashCache.get(account.email, sha1.base64, readable.length())

                                if (mediaKey.isNullOrEmpty()) {
                                    val bearerToken = try {
                                        tokenProvider()
                                    } catch (e: com.reiux.lumen.util.AuthFailureException) {
                                        triggerAccountBlock(account.email, e.kind, e.rawDetail, e.retryAfterSec)
                                        throw e
                                    }

                                    mediaKey = remoteDedupMutex.withLock {
                                        val elapsed = System.currentTimeMillis() - lastRemoteDedupTime
                                        if (elapsed < 60) {
                                            delay(60 - elapsed)
                                        }
                                        lastRemoteDedupTime = System.currentTimeMillis()

                                        try {
                                            photosClient.findRemoteMediaByHash(bearerToken, sha1.bytes, opId)
                                        } catch (e: com.reiux.lumen.util.AuthFailureException) {
                                            if (e.kind == AuthFailureKind.TOKEN_EXPIRED) {
                                                authClient.invalidateCache(account.email)
                                                val freshToken = tokenProvider()
                                                photosClient.findRemoteMediaByHash(freshToken, sha1.bytes, opId)
                                            } else if (e.kind == AuthFailureKind.SIGN_IN_REQUIRED ||
                                                e.kind == AuthFailureKind.ACCOUNT_RESTRICTED ||
                                                e.kind == AuthFailureKind.RATE_LIMITED
                                            ) {
                                                triggerAccountBlock(account.email, e.kind, e.rawDetail, e.retryAfterSec)
                                                throw e
                                            } else null
                                        } catch (e: Exception) {
                                            if (e is CancellationException) throw e
                                            LumenLogger.log(
                                                level = LogLevel.WARN,
                                                tag = "AlbumSync",
                                                opId = opId,
                                                fileName = scanned.file.name,
                                                userMessage = "Remote lookup failed during album sync: ${e.message}"
                                            ) {
                                                "Remote lookup failed for '${scanned.file.name}': ${e.message}"
                                            }
                                            null
                                        }
                                    }

                                    if (!mediaKey.isNullOrEmpty()) {
                                        hashCache.put(
                                            accountEmail = account.email,
                                            sha1B64 = sha1.base64,
                                            mediaKey = mediaKey,
                                            fileSize = readable.length(),
                                            mtime = readable.lastModified() / 1000
                                        )
                                    }
                                }

                                if (!mediaKey.isNullOrEmpty()) {
                                    matchedKeys.add(mediaKey)
                                    totalMatched++
                                } else {
                                    totalNotInCloud++
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                if (e is com.reiux.lumen.util.AuthFailureException &&
                                    (e.kind == AuthFailureKind.SIGN_IN_REQUIRED ||
                                        e.kind == AuthFailureKind.ACCOUNT_RESTRICTED ||
                                        e.kind == AuthFailureKind.RATE_LIMITED)
                                ) {
                                    throw e
                                }
                                LumenLogger.log(
                                    level = LogLevel.WARN,
                                    tag = "AlbumSync",
                                    opId = opId,
                                    fileName = scanned.file.name,
                                    userMessage = "Error checking item during album sync: ${e.message}"
                                ) {
                                    "Error checking '${scanned.file.name}': ${e.message}"
                                }
                            } finally {
                                MediaFileReader.cleanupStagedFile(context, readable)
                            }
                        }

                        if (matchedKeys.isNotEmpty()) {
                            batchAssignMediaKeysToAlbum(
                                tokenProvider = tokenProvider,
                                accountEmail = account.email,
                                rawTitle = rawTitle,
                                customNickname = folder.customAlbumNickname,
                                mediaKeys = matchedKeys.toList(),
                                opId = opId,
                                onBatchAdded = { delta ->
                                    totalAdded += delta
                                    _albumSyncState.value = AlbumSyncState.Syncing(
                                        currentFolderName = folder.displayName,
                                        processedFiles = filesToCheck.size,
                                        totalFiles = filesToCheck.size,
                                        addedCount = totalAdded
                                    )
                                }
                            )
                        }
                        syncedFolderCount++
                    }

                    LumenLogger.i("AlbumSync", opId = opId) {
                        "Album sync complete: folders=$syncedFolderCount matched=$totalMatched added=$totalAdded notInCloud=$totalNotInCloud"
                    }
                    _albumSyncState.value = AlbumSyncState.Done(
                        matchedCount = totalMatched,
                        addedCount = totalAdded,
                        notInCloudCount = totalNotInCloud,
                        folderCount = syncedFolderCount
                    )
                } catch (e: CancellationException) {
                    LumenLogger.i("AlbumSync", opId = opId) { "Album sync cancelled" }
                    _albumSyncState.value = AlbumSyncState.Idle
                } catch (e: Exception) {
                    LumenLogger.e("AlbumSync", opId = opId, tr = e) { "Album sync failed: ${e.message}" }
                    _albumSyncState.value = AlbumSyncState.Done(
                        matchedCount = totalMatched,
                        addedCount = totalAdded,
                        notInCloudCount = totalNotInCloud,
                        folderCount = syncedFolderCount
                    )
                } finally {
                    synchronized(albumSyncJobLock) {
                        pendingSyncFolders.clear()
                        albumSyncJob = null
                    }
                }
            }
        }
    }

    fun cancelAlbumSync() {
        synchronized(albumSyncJobLock) {
            pendingSyncFolders.clear()
            albumSyncJob?.cancel()
            albumSyncJob = null
        }
        _albumSyncState.value = AlbumSyncState.Idle
    }

    fun resetAlbumSyncState() {
        if (_albumSyncState.value !is AlbumSyncState.Syncing) {
            _albumSyncState.value = AlbumSyncState.Idle
        }
    }

    private suspend fun <T> withTokenRefresh(
        tokenProvider: suspend () -> String,
        accountEmail: String,
        opId: String? = null,
        block: suspend (token: String) -> T
    ): T {
        val token = try {
            tokenProvider()
        } catch (e: com.reiux.lumen.util.AuthFailureException) {
            triggerAccountBlock(accountEmail, e.kind, e.rawDetail, e.retryAfterSec)
            throw e
        }
        return try {
            block(token)
        } catch (e: com.reiux.lumen.util.AuthFailureException) {
            when (e.kind) {
                AuthFailureKind.TOKEN_EXPIRED -> {
                    authClient.invalidateCache(accountEmail)
                    val freshToken = try {
                        tokenProvider()
                    } catch (refreshErr: com.reiux.lumen.util.AuthFailureException) {
                        triggerAccountBlock(accountEmail, refreshErr.kind, refreshErr.rawDetail, refreshErr.retryAfterSec)
                        throw refreshErr
                    }
                    block(freshToken)
                }
                AuthFailureKind.SIGN_IN_REQUIRED,
                AuthFailureKind.ACCOUNT_RESTRICTED,
                AuthFailureKind.RATE_LIMITED -> {
                    triggerAccountBlock(accountEmail, e.kind, e.rawDetail, e.retryAfterSec)
                    throw e
                }
                else -> throw e
            }
        }
    }

    private suspend fun batchAssignMediaKeysToAlbum(
        tokenProvider: suspend () -> String,
        accountEmail: String,
        rawTitle: String,
        customNickname: String?,
        mediaKeys: List<String>,
        opId: String? = null,
        onBatchAdded: ((Int) -> Unit)? = null
    ): Int {
        if (mediaKeys.isEmpty()) return 0
        val albumTitleOrKey = photosClient.resolveAlbumInput(rawTitle)
        if (albumTitleOrKey.isBlank()) return 0

        val keyRegex = Regex("AF1Qip[A-Za-z0-9_-]+")
        var addedCount = 0

        fun handleMissingAlbumIf404(e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("404") || msg.contains("Requested entity was not found") || msg.contains("NOT_FOUND")) {
                LumenLogger.e("AlbumSync", opId = opId) { "Target album no longer exists in Google Photos!" }
                if (rawTitle.isNotBlank()) {
                    settingsRepository.markAlbumMissing(accountEmail, rawTitle)
                }
                throw IOException("Target album no longer exists in Google Photos")
            }
        }

        val resolvedAlbumKey: String
        val remainingKeys: List<String>

        if (albumTitleOrKey.startsWith("AF1Qip") || keyRegex.matches(albumTitleOrKey)) {
            resolvedAlbumKey = albumTitleOrKey
            remainingKeys = mediaKeys
            if (customNickname != null) {
                settingsRepository.saveAlbumMediaKey(accountEmail, customNickname, resolvedAlbumKey)
            }
        } else {
            val lockKey = "${accountEmail.lowercase()}:${albumTitleOrKey.lowercase().trim()}"
            val mutex = albumCreationLocks.computeIfAbsent(lockKey) { Mutex() }
            var foundKey: String? = null
            var createdNewWithFirst = false

            mutex.withLock {
                var saved = settingsRepository.getAlbumMediaKey(accountEmail, albumTitleOrKey)
                if (saved != null && !saved.startsWith("AF1Qip") && !keyRegex.matches(saved)) {
                    saved = null
                }
                if (saved.isNullOrEmpty()) {
                    val existingAlbums = try {
                        withTokenRefresh(tokenProvider, accountEmail, opId) { t ->
                            photosClient.listAlbums(t, opId = opId)
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (ae: com.reiux.lumen.util.AuthFailureException) {
                        if (ae.kind == AuthFailureKind.SIGN_IN_REQUIRED ||
                            ae.kind == AuthFailureKind.ACCOUNT_RESTRICTED ||
                            ae.kind == AuthFailureKind.RATE_LIMITED ||
                            ae.kind == AuthFailureKind.TRANSIENT
                        ) {
                            throw ae
                        }
                        emptyList()
                    } catch (ioe: IOException) {
                        if (ioe.message?.contains("403") == true) emptyList() else throw ioe
                    }
                    val matched = existingAlbums.firstOrNull { it.title.equals(albumTitleOrKey, ignoreCase = true) }
                    if (matched != null) {
                        saved = matched.mediaKey
                        settingsRepository.saveAlbumMediaKey(accountEmail, albumTitleOrKey, saved)
                        if (!customNickname.isNullOrBlank()) {
                            settingsRepository.saveAlbumMediaKey(accountEmail, customNickname, saved)
                        }
                    } else {
                        val firstKey = mediaKeys.first()
                        val createdKey = withTokenRefresh(tokenProvider, accountEmail, opId) { t ->
                            photosClient.createAlbum(t, albumTitleOrKey, initialMediaKey = firstKey, opId = opId)
                        }
                        settingsRepository.saveAlbumMediaKey(accountEmail, albumTitleOrKey, createdKey)
                        if (!customNickname.isNullOrBlank()) {
                            settingsRepository.saveAlbumMediaKey(accountEmail, customNickname, createdKey)
                        }
                        saved = createdKey
                        createdNewWithFirst = true
                        addedCount += 1
                        onBatchAdded?.invoke(1)
                    }
                }
                foundKey = saved
            }

            resolvedAlbumKey = foundKey ?: return addedCount
            remainingKeys = if (createdNewWithFirst) mediaKeys.drop(1) else mediaKeys
        }

        val batches = computeAlbumSyncBatches(remainingKeys)
        for (batch in batches) {
            try {
                withTokenRefresh(tokenProvider, accountEmail, opId) { t ->
                    photosClient.addMediaToAlbum(t, resolvedAlbumKey, batch, opId = opId)
                }
                addedCount += batch.size
                onBatchAdded?.invoke(batch.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleMissingAlbumIf404(e)
                throw e
            }
        }
        return addedCount
    }

    private suspend fun handleAlbumAssignment(
        token: String,
        accountEmail: String,
        filePath: String,
        item: UploadItem,
        mediaKey: String,
        opId: String? = null
    ) {
        var matchedFolderDisplayName: String? = null
        val (albumMode, customName, customNickname) = if (item.targetAlbumMode != com.reiux.lumen.data.model.AlbumTargetMode.NONE) {
            Triple(item.targetAlbumMode, item.customAlbumName, item.customAlbumNickname)
        } else {
            val folders = settingsRepository.getFoldersForAccount(accountEmail)
            val matchingFolder = folders.firstOrNull { f ->
                f.enabled && f.targetAlbumMode != com.reiux.lumen.data.model.AlbumTargetMode.NONE &&
                    com.reiux.lumen.util.FolderWatcherManager.isPathMatchingFolder(filePath, f)
            }
            if (matchingFolder != null) {
                matchedFolderDisplayName = matchingFolder.displayName
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
                    val f = folders.firstOrNull { com.reiux.lumen.util.FolderWatcherManager.isPathMatchingFolder(filePath, it) }
                    f?.displayName ?: "Uploads"
                }
            }
            com.reiux.lumen.data.model.AlbumTargetMode.CUSTOM_NAME -> {
                val fallback = matchedFolderDisplayName?.ifBlank { "Uploads" } ?: "Uploads"
                customName?.ifBlank { fallback } ?: fallback
            }
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
                    val folders = settingsRepository.getFoldersForAccount(accountEmail)
                    _queue.value = _queue.value.map { qItem ->
                        val sameAccount = (qItem.accountEmail ?: accountEmail).equals(accountEmail, ignoreCase = true)
                        val isPending = qItem.status == UploadStatus.QUEUED
                        val matchesDirect = qItem.customAlbumName == rawTitle || qItem.customAlbumNickname == rawTitle
                        val qCheckPath = qItem.originalFilePath ?: qItem.filePath
                        val matchesFolder = qItem.targetAlbumMode == com.reiux.lumen.data.model.AlbumTargetMode.NONE &&
                            folders.any { f ->
                                f.enabled && (f.customAlbumName == rawTitle || f.customAlbumNickname == rawTitle) &&
                                    com.reiux.lumen.util.FolderWatcherManager.isPathMatchingFolder(qCheckPath, f)
                            }
                        if (sameAccount && isPending && (matchesDirect || matchesFolder)) {
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
            LumenLogger.i("UploadEngine", opId = opId) { "Adding mediaKey=$mediaKey to direct albumMediaKey=$albumTitleOrKey for account $accountEmail..." }
            try {
                photosClient.addMediaToAlbum(token, albumTitleOrKey, listOf(mediaKey), opId = opId)
                if (customNickname != null) {
                    settingsRepository.saveAlbumMediaKey(accountEmail, customNickname, albumTitleOrKey)
                }
            } catch (e: CancellationException) {
                throw e
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
            val lockKey = "${accountEmail.lowercase()}:${albumTitleOrKey.lowercase().trim()}"
            val mutex = albumCreationLocks.computeIfAbsent(lockKey) { Mutex() }
            
            mutex.withLock {
                albumMediaKey = settingsRepository.getAlbumMediaKey(accountEmail, albumTitleOrKey)
                if (albumMediaKey != null && !albumMediaKey.startsWith("AF1Qip") && !keyRegex.matches(albumMediaKey)) {
                    albumMediaKey = null
                }

                if (albumMediaKey.isNullOrEmpty()) {
                    val existingAlbums = try {
                        photosClient.listAlbums(token, opId = opId)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (ae: com.reiux.lumen.util.AuthFailureException) {
                        if (ae.kind == AuthFailureKind.TOKEN_EXPIRED ||
                            ae.kind == AuthFailureKind.SIGN_IN_REQUIRED ||
                            ae.kind == AuthFailureKind.ACCOUNT_RESTRICTED ||
                            ae.kind == AuthFailureKind.RATE_LIMITED ||
                            ae.kind == AuthFailureKind.TRANSIENT
                        ) {
                            throw ae
                        }
                        emptyList()
                    } catch (ioe: IOException) {
                        if (ioe.message?.contains("403") == true) emptyList() else throw ioe
                    }

                    val matchedAlbum = existingAlbums.firstOrNull { it.title.equals(albumTitleOrKey, ignoreCase = true) }
                    if (matchedAlbum != null) {
                        LumenLogger.i("UploadEngine", opId = opId) { "Found existing Google Photos album '${matchedAlbum.title}' albumMediaKey=${matchedAlbum.mediaKey} for account $accountEmail" }
                        val mKey = matchedAlbum.mediaKey
                        albumMediaKey = mKey
                        settingsRepository.saveAlbumMediaKey(accountEmail, albumTitleOrKey, mKey)
                        if (!customNickname.isNullOrBlank()) {
                            settingsRepository.saveAlbumMediaKey(accountEmail, customNickname, mKey)
                        }
                    } else {
                        LumenLogger.i("UploadEngine", opId = opId) { "Creating new Google Photos album '$albumTitleOrKey' with initial mediaKey=$mediaKey for account $accountEmail..." }
                        try {
                            val createdKey = photosClient.createAlbum(token, albumTitleOrKey, initialMediaKey = mediaKey, opId = opId)
                            settingsRepository.saveAlbumMediaKey(accountEmail, albumTitleOrKey, createdKey)
                            if (!customNickname.isNullOrBlank()) {
                                settingsRepository.saveAlbumMediaKey(accountEmail, customNickname, createdKey)
                            }
                            return
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (ae: com.reiux.lumen.util.AuthFailureException) {
                            throw ae
                        } catch (createErr: Exception) {
                            LumenLogger.log(
                                level = LogLevel.ERROR,
                                tag = "UploadEngine",
                                opId = opId,
                                tr = createErr,
                                fileName = item.fileName,
                                userMessage = "Failed to create new album in Google Photos: ${createErr.message}"
                            ) {
                                "Failed to create new album '$albumTitleOrKey': ${createErr.message}"
                            }
                            throw IOException("Failed to create new album in Google Photos", createErr)
                        }
                    }
                }
            }
        }

        if (!albumMediaKey.isNullOrEmpty()) {
            LumenLogger.i("UploadEngine", opId = opId) { "Adding mediaKey=$mediaKey to existing album '$albumTitleOrKey' albumMediaKey=$albumMediaKey..." }
            try {
                photosClient.addMediaToAlbum(token, albumMediaKey!!, listOf(mediaKey), opId = opId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handle404(e)
                throw e
            }
        }
    }

    private fun getItemStatus(id: String): UploadStatus? {
        return synchronized(_queue) {
            _queue.value.firstOrNull { it.id == id }?.status
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
            // Called once per 32KB chunk by the upload client. Rebuilding the whole queue
            // (O(N) .map + active-item scan + StateFlow re-emit) per chunk made the UI jank
            // on large batches, so throttle to one emission per item per ~250ms. Status
            // transitions still go through updateItemStatus(), which emits immediately.
            val now = System.currentTimeMillis()
            val last = lastProgressEmitAt[id] ?: 0L
            if (now - last < 250L) return
            lastProgressEmitAt[id] = now
            _queue.value = _queue.value.map {
                if (it.id == id) it.copy(bytesUploaded = bytesUploaded) else it
            }
            _currentUploadingItem.value = getActiveUploadingItem()
        }
    }

    private fun updateItemCompleted(id: String, mediaKey: String, isSkipped: Boolean) {
        val status = if (isSkipped) UploadStatus.SKIPPED else UploadStatus.COMPLETED
        synchronized(_queue) {
            lastProgressEmitAt.remove(id)
            _queue.value = _queue.value.map {
                if (it.id == id) it.copy(status = status, mediaKey = mediaKey, bytesUploaded = it.fileSize) else it
            }
            _currentUploadingItem.value = getActiveUploadingItem()
        }
        if (isSkipped) {
            _skippedCount.update { it + 1 }
        } else {
            _completedCount.update { it + 1 }
        }
    }

    private fun updateItemError(id: String, error: String) {
        synchronized(_queue) {
            lastProgressEmitAt.remove(id)
            _queue.value = _queue.value.map {
                if (it.id == id) it.copy(status = UploadStatus.FAILED, error = error) else it
            }
            _currentUploadingItem.value = getActiveUploadingItem()
        }
    }

    private fun handlePostBackupDeletion(item: UploadItem, file: File, opId: String) {
        // Clean up temporary staging cache copy if this item was staged
        MediaFileReader.cleanupStagedFile(context, file)

        val deleteAfterBackupSetting = settingsRepository.settings.value.deleteAfterBackup
        if (!deleteAfterBackupSetting || !item.allowDeleteAfterBackup) return

        val targetPath = item.originalFilePath ?: item.filePath
        val stagingDir = File(context.cacheDir, "staging")
        val stagingDirCanonicalPrefix = try {
            stagingDir.canonicalPath.trimEnd('/') + "/"
        } catch (_: Exception) {
            stagingDir.absolutePath.trimEnd('/') + "/"
        }

        if (!isEligibleForPostBackupDeletion(
                item = item,
                targetPath = targetPath,
                configuredFolders = settingsRepository.getAllFolders(),
                deleteAfterBackupSetting = deleteAfterBackupSetting,
                stagingDirCanonicalPrefix = stagingDirCanonicalPrefix
            )
        ) {
            LumenLogger.d("UploadEngine", opId = opId) {
                "Skipping post-backup deletion for '${item.fileName}' (outside configured enabled folder scope)"
            }
            return
        }

        val targetFile = File(targetPath)
        if (targetFile.exists()) {
            try {
                val deleted = targetFile.delete()
                LumenLogger.log(
                    level = LogLevel.INFO,
                    tag = "UploadEngine",
                    opId = opId,
                    fileName = item.fileName,
                    userMessage = "Post-backup original file deletion: deleted=$deleted"
                ) {
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
                LumenLogger.log(
                    level = LogLevel.WARN,
                    tag = "UploadEngine",
                    opId = opId,
                    tr = delEx,
                    fileName = item.fileName,
                    userMessage = "Failed to delete original file after backup"
                ) {
                    "Failed to delete original file after backup: path=$targetPath"
                }
            }
        }
    }

    companion object {
        const val ALBUM_SYNC_BATCH_SIZE = 50

        internal fun isEligibleForPostBackupDeletion(
            item: UploadItem,
            targetPath: String,
            configuredFolders: List<BackupFolder>,
            deleteAfterBackupSetting: Boolean,
            stagingDirCanonicalPrefix: String
        ): Boolean {
            if (!deleteAfterBackupSetting || !item.allowDeleteAfterBackup) return false
            if (targetPath.isBlank()) return false

            val targetFile = File(targetPath)
            val canonicalTargetPath = try {
                targetFile.canonicalPath
            } catch (_: Exception) {
                targetFile.absolutePath
            }
            val cleanCanonical = canonicalTargetPath.replace('\\', '/')
            val cleanRaw = targetPath.replace('\\', '/')
            val cleanStagingPrefix = stagingDirCanonicalPrefix.replace('\\', '/').trimEnd('/')
            if (cleanStagingPrefix.isNotEmpty() &&
                (cleanCanonical == cleanStagingPrefix || cleanCanonical.startsWith("$cleanStagingPrefix/"))
            ) {
                return false
            }
            if (cleanCanonical.contains("/cache/staging/") || cleanRaw.contains("/cache/staging/")) {
                return false
            }

            return configuredFolders.any { folder ->
                if (!folder.enabled) return@any false
                val accountMatches = folder.accountEmail.isNullOrBlank() ||
                    item.accountEmail.isNullOrBlank() ||
                    folder.accountEmail.equals(item.accountEmail, ignoreCase = true)
                accountMatches && com.reiux.lumen.util.FolderWatcherManager.isPathMatchingFolder(targetPath, folder)
            }
        }

        internal fun computeAlbumSyncBatches(mediaKeys: List<String>, batchSize: Int = ALBUM_SYNC_BATCH_SIZE): List<List<String>> {
            if (mediaKeys.isEmpty()) return emptyList()
            val safeSize = batchSize.coerceAtLeast(1)
            return mediaKeys.distinct().chunked(safeSize)
        }

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
