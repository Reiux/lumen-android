package com.reiux.lumen.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reiux.lumen.LumenApplication
import com.reiux.lumen.data.model.AccountBlock
import com.reiux.lumen.data.model.AuthFailureKind
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.model.BackupSettings
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.data.model.UploadItem
import com.reiux.lumen.data.model.UploadStatus
import com.reiux.lumen.ui.components.NavDestination
import com.reiux.lumen.util.LumenLogger
import com.reiux.lumen.util.MediaFileReader
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(
        val filesFound: Int,
        val foldersScanned: Int,
        val currentFolderName: String
    ) : ScanState()
    data class Done(val totalFilesFound: Int) : ScanState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LumenApplication
    val accountRepo = app.accountRepository
    val settingsRepo = app.settingsRepository
    val uploadRepo = app.uploadRepository

    private val _selectedDestination = MutableStateFlow(NavDestination.BACKUP)
    val selectedDestination: StateFlow<NavDestination> = _selectedDestination.asStateFlow()

    // --- Mock Mode & Showcase Mode States ---
    private val _isMockMode = MutableStateFlow(false)
    val isMockMode: StateFlow<Boolean> = _isMockMode.asStateFlow()

    private val _isShowcaseMode = MutableStateFlow(false)
    val isShowcaseMode: StateFlow<Boolean> = _isShowcaseMode.asStateFlow()

    // Showcase Mode Dataset (Pristine static data for screenshots)
    private val _showcaseAccounts = listOf(
        GoogleAccount("alex.morgan@gmail.com", "showcase_cred_1", "Alex Morgan"),
        GoogleAccount("photos.vault@gmail.com", "showcase_cred_2", "Backup Vault")
    )

    private val _showcaseActiveItem = UploadItem(
        id = "showcase_1",
        filePath = "/sdcard/DCIM/Camera/IMG_20261023_184500.jpg",
        fileName = "IMG_20261023_184500.jpg",
        fileSize = 12_500_000L,
        bytesUploaded = 8_500_000L,
        status = UploadStatus.UPLOADING
    )

    private val _showcaseQueue = listOf(
        _showcaseActiveItem,
        UploadItem(
            id = "showcase_2",
            filePath = "/sdcard/DCIM/Camera/VID_20261023_182500.mp4",
            fileName = "VID_20261023_182500.mp4",
            fileSize = 48_000_000L,
            status = UploadStatus.HASHING
        ),
        UploadItem(
            id = "showcase_3",
            filePath = "/sdcard/DCIM/Camera/IMG_20261023_182000.jpg",
            fileName = "IMG_20261023_182000.jpg",
            fileSize = 9_100_000L,
            status = UploadStatus.CHECKING
        ),
        UploadItem(
            id = "showcase_4",
            filePath = "/sdcard/Pictures/Screenshots/Screenshot_20261023.png",
            fileName = "Screenshot_20261023.png",
            fileSize = 3_400_000L,
            status = UploadStatus.QUEUED
        ),
        UploadItem(
            id = "showcase_5",
            filePath = "/sdcard/DCIM/Camera/IMG_20261022_173000.jpg",
            fileName = "IMG_20261022_173000.jpg",
            fileSize = 6_400_000L,
            status = UploadStatus.COMPLETED
        )
    )

    private val _updateInfo = MutableStateFlow<com.reiux.lumen.data.model.UpdateInfo?>(null)
    val updateInfo: StateFlow<com.reiux.lumen.data.model.UpdateInfo?> = _updateInfo.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow(-1)
    val updateDownloadProgress: StateFlow<Int> = _updateDownloadProgress.asStateFlow()

    private val _showUpdateSheet = MutableStateFlow(false)
    val showUpdateSheet: StateFlow<Boolean> = _showUpdateSheet.asStateFlow()

    // Original Frontend Mock Simulation Dataset & State
    private val _mockAccounts = MutableStateFlow(
        listOf(
            GoogleAccount(
                email = "alex.rivera@gmail.com",
                credentialString = "mock_cred_1",
                displayName = "Alex Rivera (Demo)"
            ),
            GoogleAccount(
                email = "qa.tester@lumen.dev",
                credentialString = "mock_cred_2",
                displayName = "Lumen QA Tester"
            )
        )
    )
    private val _mockSelectedAccount = MutableStateFlow<GoogleAccount?>(_mockAccounts.value.first())
    private val _mockSettings = MutableStateFlow(
        BackupSettings(
            autoBackupEnabled = true,
            wifiOnly = true,
            chargingOnly = false,
            storageSaverQuality = false,
            useQuota = false,
            uploadThreads = 2,
            colorPreset = "Dynamic",
            notificationsEnabled = true,
            hasCompletedOnboarding = true,
            folders = listOf(
                BackupFolder("/sdcard/DCIM/Camera", "Camera Roll", true),
                BackupFolder("/sdcard/Pictures/Screenshots", "Screenshots", true),
                BackupFolder("/sdcard/Pictures/Wallpapers", "Wallpapers", false),
                BackupFolder("/sdcard/Download", "Downloads", false)
            )
        )
    )
    private val _mockIsUploading = MutableStateFlow(true)
    private val _mockCurrentUploadingItem = MutableStateFlow<UploadItem?>(
        UploadItem(
            id = "mock_1",
            filePath = "/sdcard/DCIM/Camera/IMG_20261022_120401.jpg",
            fileName = "IMG_20261022_120401.jpg",
            fileSize = 8_600_000L,
            bytesUploaded = 3_400_000L,
            status = UploadStatus.UPLOADING
        )
    )
    private val _mockQueue = MutableStateFlow<List<UploadItem>>(
        listOf(
            UploadItem(
                id = "mock_1",
                filePath = "/sdcard/DCIM/Camera/IMG_20261022_120401.jpg",
                fileName = "IMG_20261022_120401.jpg",
                fileSize = 8_600_000L,
                bytesUploaded = 3_400_000L,
                status = UploadStatus.UPLOADING
            ),
            UploadItem(
                id = "mock_2",
                filePath = "/sdcard/DCIM/Camera/VID_20261021_210512.mp4",
                fileName = "VID_20261021_210512.mp4",
                fileSize = 42_100_000L,
                status = UploadStatus.HASHING
            ),
            UploadItem(
                id = "mock_3",
                filePath = "/sdcard/DCIM/Camera/PXL_20261020_083015.jpg",
                fileName = "PXL_20261020_083015.jpg",
                fileSize = 5_200_000L,
                status = UploadStatus.CHECKING
            ),
            UploadItem(
                id = "mock_4",
                filePath = "/sdcard/Pictures/Screenshots/Screenshot_20261019.png",
                fileName = "Screenshot_20261019.png",
                fileSize = 2_100_000L,
                status = UploadStatus.QUEUED
            ),
            UploadItem(
                id = "mock_5",
                filePath = "/sdcard/DCIM/Camera/IMG_20261018_173000.jpg",
                fileName = "IMG_20261018_173000.jpg",
                fileSize = 6_400_000L,
                status = UploadStatus.COMPLETED
            ),
            UploadItem(
                id = "mock_6",
                filePath = "/sdcard/DCIM/Camera/IMG_20261018_142010.jpg",
                fileName = "IMG_20261018_142010.jpg",
                fileSize = 4_800_000L,
                status = UploadStatus.SKIPPED
            ),
            UploadItem(
                id = "mock_7",
                filePath = "/sdcard/DCIM/Camera/CORRUPTED_RAW.dng",
                fileName = "CORRUPTED_RAW.dng",
                fileSize = 54_000_000L,
                status = UploadStatus.FAILED,
                error = "Scotty upload server timeout (HTTP 504)"
            )
        )
    )
    private val _mockCompletedCount = MutableStateFlow(2)
    private val _mockConditionNotice = MutableStateFlow<String?>(null)
    private val _mockUploadSpeed = MutableStateFlow<String?>("5.6 MB/s")
    private var mockSimJob: Job? = null
    private var previousQueueCounts = ""

    init {
        // Track queue state transitions as before -> after
        viewModelScope.launch {
            uploadRepo.queue.collect { q ->
                val pending = q.count { it.status == UploadStatus.QUEUED }
                val active = q.count {
                    it.status == UploadStatus.HASHING ||
                    it.status == UploadStatus.CHECKING ||
                    it.status == UploadStatus.UPLOADING ||
                    it.status == UploadStatus.COMMITTING
                }
                val completed = q.count { it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED }
                val failed = q.count { it.status == UploadStatus.FAILED }
                val counts = "pending=$pending active=$active completed=$completed failed=$failed"
                if (previousQueueCounts.isNotEmpty() && previousQueueCounts != counts) {
                    LumenLogger.d("UploadState") {
                        "Queue state transition: before=($previousQueueCounts) -> after=($counts)"
                    }
                }
                previousQueueCounts = counts
            }
        }
    }

    // --- Exposed StateFlows (Dynamic routing: Showcase vs Mock vs Live) ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val accounts: StateFlow<List<GoogleAccount>> = combine(_isShowcaseMode, _isMockMode) { s, m -> Pair(s, m) }.flatMapLatest { (showcase, mock) ->
        when {
            showcase -> flowOf(_showcaseAccounts)
            mock -> _mockAccounts
            else -> accountRepo.accounts
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedAccount: StateFlow<GoogleAccount?> = combine(_isShowcaseMode, _isMockMode) { s, m -> Pair(s, m) }.flatMapLatest { (showcase, mock) ->
        when {
            showcase -> flowOf(_showcaseAccounts.firstOrNull())
            mock -> _mockSelectedAccount
            else -> accountRepo.selectedAccount
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val settings: StateFlow<BackupSettings> = combine(_isShowcaseMode, _isMockMode) { s, m -> Pair(s, m) }.flatMapLatest { (showcase, mock) ->
        when {
            showcase -> _mockSettings.map { it.copy(developerModeUnlocked = true) }
            mock -> _mockSettings.map { it.copy(developerModeUnlocked = true) }
            else -> settingsRepo.settings
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepo.settings.value)

    @OptIn(ExperimentalCoroutinesApi::class)
    val isUploading: StateFlow<Boolean> = combine(_isShowcaseMode, _isMockMode) { s, m -> Pair(s, m) }.flatMapLatest { (showcase, mock) ->
        when {
            showcase -> flowOf(true)
            mock -> _mockIsUploading
            else -> uploadRepo.isUploading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUploadingItem: StateFlow<UploadItem?> = combine(_isShowcaseMode, _isMockMode) { s, m -> Pair(s, m) }.flatMapLatest { (showcase, mock) ->
        when {
            showcase -> flowOf(_showcaseActiveItem)
            mock -> _mockCurrentUploadingItem
            else -> uploadRepo.currentUploadingItem
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val queue: StateFlow<List<UploadItem>> = combine(_isShowcaseMode, _isMockMode) { s, m -> Pair(s, m) }.flatMapLatest { (showcase, mock) ->
        when {
            showcase -> flowOf(_showcaseQueue)
            mock -> _mockQueue
            else -> uploadRepo.queue
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val completedCount: StateFlow<Int> = combine(_isShowcaseMode, _isMockMode) { s, m -> Pair(s, m) }.flatMapLatest { (showcase, mock) ->
        when {
            showcase -> flowOf(88)
            mock -> _mockCompletedCount
            else -> uploadRepo.completedCount
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val conditionNotice: StateFlow<String?> = combine(_isShowcaseMode, _isMockMode) { s, m -> Pair(s, m) }.flatMapLatest { (showcase, mock) ->
        when {
            showcase -> flowOf(null)
            mock -> _mockConditionNotice
            else -> uploadRepo.conditionNotice
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uploadSpeed: StateFlow<String?> = combine(_isShowcaseMode, _isMockMode) { s, m -> Pair(s, m) }.flatMapLatest { (showcase, mock) ->
        when {
            showcase -> flowOf("12.4 MB/s")
            mock -> _mockUploadSpeed
            else -> uploadRepo.uploadSpeed
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isAddingAccount = MutableStateFlow(false)
    val isAddingAccount: StateFlow<Boolean> = _isAddingAccount.asStateFlow()

    private val _addAccountError = MutableStateFlow<String?>(null)
    val addAccountError: StateFlow<String?> = _addAccountError.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _mockAccountBlock = MutableStateFlow<AccountBlock?>(null)

    val accountBlock: StateFlow<AccountBlock?> = combine(_isMockMode, _mockAccountBlock, uploadRepo.accountBlock) { isMock, mockBlock, realBlock ->
        if (isMock) mockBlock else realBlock
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var scanJob: Job? = null

    override fun onCleared() {
        super.onCleared()
        mockSimJob?.cancel()
        scanJob?.cancel()
    }

    fun selectDestination(dest: NavDestination) {
        LumenLogger.i("Navigation", "Navigated to tab: ${dest.title}")
        _selectedDestination.value = dest
    }

    fun checkAndShowUpdate(context: Context, forceShow: Boolean = false, onUpToDate: (() -> Unit)? = null) {
        viewModelScope.launch {
            val repo = com.reiux.lumen.data.repository.UpdateRepository()
            val currentVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0)?.versionName ?: "1.0.0"
            } catch (_: Exception) { "1.0.0" }
            
            val info = repo.checkForUpdates(currentVersion)
            if (info != null) {
                if (info.isUpdateAvailable || forceShow) {
                    _updateInfo.value = info
                    _updateDownloadProgress.value = -1
                    _showUpdateSheet.value = true
                } else {
                    withContext(Dispatchers.Main) { onUpToDate?.invoke() }
                }
            } else {
                withContext(Dispatchers.Main) { onUpToDate?.invoke() }
            }
        }
    }

    fun dismissUpdateSheet() {
        _showUpdateSheet.value = false
        _updateDownloadProgress.value = -1
    }

    fun startUpdateDownload(context: Context) {
        val info = _updateInfo.value ?: return
        viewModelScope.launch {
            _updateDownloadProgress.value = 0
            val destFile = File(context.cacheDir, "lumen_update_${info.versionName}.apk")
            try {
                com.reiux.lumen.data.repository.UpdateRepository().downloadUpdate(info.downloadUrl, destFile).collect { p ->
                    _updateDownloadProgress.value = p
                }
                _showUpdateSheet.value = false
                com.reiux.lumen.ui.components.launchApkInstall(context, destFile)
            } catch (e: Exception) {
                LumenLogger.e("Updater", tr = e) { "Failed to download update" }
                _updateDownloadProgress.value = -1
            }
        }
    }

    fun setShowcaseMode(enabled: Boolean) {
        LumenLogger.i("ShowcaseMode", "Screenshot Showcase mode set to: $enabled")
        if (enabled) {
            _isMockMode.value = false
            mockSimJob?.cancel()
            mockSimJob = null
            _mockSettings.value = _mockSettings.value.copy(developerModeUnlocked = true)
        }
        _isShowcaseMode.value = enabled
    }

    fun toggleShowcaseMode() {
        setShowcaseMode(!_isShowcaseMode.value)
    }

    // --- Mock Mode Controls ---
    fun setMockMode(enabled: Boolean) {
        LumenLogger.i("MockMode", "Mock mode set to: $enabled")
        if (enabled) {
            _isShowcaseMode.value = false
            _mockSettings.value = _mockSettings.value.copy(developerModeUnlocked = true)
        }
        _isMockMode.value = enabled
        if (enabled) {
            startMockSimulation()
        } else {
            mockSimJob?.cancel()
            mockSimJob = null
        }
    }

    fun toggleMockMode() {
        setMockMode(!_isMockMode.value)
    }

    fun toggleMockConditionNotice() {
        val next = when (_mockConditionNotice.value) {
            null -> "Waiting for Wi-Fi"
            "Waiting for Wi-Fi" -> "Waiting for charger"
            else -> null
        }
        LumenLogger.d("MockMode", "Mock condition notice toggled to: $next")
        _mockConditionNotice.value = next
    }

    fun addMockUploadItems() {
        val count = _mockQueue.value.size + 1
        LumenLogger.i("MockMode", "Added 2 simulated mock upload items (total queue size: ${_mockQueue.value.size + 2})")
        val newItems = listOf(
            UploadItem(
                id = UUID.randomUUID().toString(),
                filePath = "/sdcard/DCIM/Camera/MOCK_PHOTO_$count.jpg",
                fileName = "MOCK_PHOTO_$count.jpg",
                fileSize = 7_500_000L,
                status = UploadStatus.QUEUED
            ),
            UploadItem(
                id = UUID.randomUUID().toString(),
                filePath = "/sdcard/DCIM/Camera/MOCK_PHOTO_${count + 1}.heic",
                fileName = "MOCK_PHOTO_${count + 1}.heic",
                fileSize = 3_200_000L,
                status = UploadStatus.QUEUED
            )
        )
        _mockQueue.value = _mockQueue.value + newItems
        if (!_mockIsUploading.value) {
            startMockSimulation()
        }
    }

    private fun startMockSimulation() {
        mockSimJob?.cancel()
        _mockIsUploading.value = true
        _mockCompletedCount.value = 0

        val mockRunId = "Mock Run 1"
        LumenLogger.logRunStart(
            runId = mockRunId,
            queuedCount = _mockQueue.value.size,
            wifiOnly = _mockSettings.value.wifiOnly,
            chargingOnly = _mockSettings.value.chargingOnly,
            storageSaver = _mockSettings.value.storageSaverQuality,
            useQuota = _mockSettings.value.useQuota,
            threads = _mockSettings.value.uploadThreads,
            isMock = true
        )

        mockSimJob = viewModelScope.launch {
            var uploadedCount = 0
            var skippedCount = 0
            var failedCount = 0
            var totalBytesProcessed = 0L
            val simStart = System.currentTimeMillis()

            while (_isMockMode.value && _mockIsUploading.value) {
                var active = _mockQueue.value.firstOrNull { it.status == UploadStatus.UPLOADING }
                if (active == null) {
                    val next = _mockQueue.value.firstOrNull { it.status == UploadStatus.QUEUED } ?: break

                    // Simulate Hashing
                    _mockQueue.value = _mockQueue.value.map { if (it.id == next.id) it.copy(status = UploadStatus.HASHING) else it }
                    _mockCurrentUploadingItem.value = next.copy(status = UploadStatus.HASHING)
                    delay(400)

                    // Simulate Checking
                    _mockQueue.value = _mockQueue.value.map { if (it.id == next.id) it.copy(status = UploadStatus.CHECKING) else it }
                    _mockCurrentUploadingItem.value = next.copy(status = UploadStatus.CHECKING)
                    delay(400)

                    active = next.copy(status = UploadStatus.UPLOADING, bytesUploaded = 0L)
                    _mockQueue.value = _mockQueue.value.map { if (it.id == next.id) active else it }
                    _mockCurrentUploadingItem.value = active
                }

                val totalBytes = active.fileSize
                var currentBytes = active.bytesUploaded
                val step = (totalBytes / 20).coerceAtLeast(150 * 1024)
                _mockUploadSpeed.value = "8.4 MB/s"

                while (currentBytes < totalBytes && _mockIsUploading.value && _isMockMode.value) {
                    delay(80)
                    currentBytes = (currentBytes + step).coerceAtMost(totalBytes)
                    val updated = active.copy(bytesUploaded = currentBytes)
                    _mockCurrentUploadingItem.value = updated
                    _mockQueue.value = _mockQueue.value.map { if (it.id == active.id) updated else it }
                    _mockUploadSpeed.value = if ((currentBytes / 1024) % 2 == 0L) "8.6 MB/s" else "8.2 MB/s"
                }

                if (!_mockIsUploading.value || !_isMockMode.value) break

                // Committing
                _mockUploadSpeed.value = null
                val committing = active.copy(status = UploadStatus.COMMITTING, bytesUploaded = totalBytes)
                _mockCurrentUploadingItem.value = committing
                _mockQueue.value = _mockQueue.value.map { if (it.id == active.id) committing else it }
                delay(300)

                if (active.fileName.contains("CORRUPTED", ignoreCase = true)) {
                    failedCount++
                    val classified = com.reiux.lumen.util.ErrorClassifier.classify(null, 504, "Scotty upload server timeout (HTTP 504)")
                    LumenLogger.logFileResult(
                        runId = mockRunId,
                        fileName = active.fileName,
                        sizeBytes = totalBytes,
                        durationMs = 1200L,
                        outcome = com.reiux.lumen.util.UploadOutcome.FAILED,
                        reasonCode = classified.reasonCode,
                        userMessage = classified.userMessage,
                        suggestedAction = classified.suggestedAction,
                        rawMessage = "Scotty upload server timeout (HTTP 504)",
                        isMock = true
                    )
                    _mockQueue.value = _mockQueue.value.map { if (it.id == active.id) it.copy(status = UploadStatus.FAILED) else it }
                } else if (active.fileName.contains("SKIPPED", ignoreCase = true) || active.status == UploadStatus.SKIPPED) {
                    skippedCount++
                    LumenLogger.logFileResult(
                        runId = mockRunId,
                        fileName = active.fileName,
                        sizeBytes = totalBytes,
                        durationMs = 300L,
                        outcome = com.reiux.lumen.util.UploadOutcome.SKIPPED_DUPLICATE,
                        reasonCode = "DUPLICATE",
                        userMessage = "Skipped (already in Google Photos)",
                        suggestedAction = "No action needed.",
                        rawMessage = "Duplicate cloud match",
                        isMock = true
                    )
                    _mockQueue.value = _mockQueue.value.map { if (it.id == active.id) it.copy(status = UploadStatus.SKIPPED) else it }
                } else {
                    uploadedCount++
                    totalBytesProcessed += totalBytes
                    LumenLogger.logFileResult(
                        runId = mockRunId,
                        fileName = active.fileName,
                        sizeBytes = totalBytes,
                        durationMs = 1100L,
                        outcome = com.reiux.lumen.util.UploadOutcome.UPLOADED,
                        rawMessage = "Upload SUCCESS mediaKey=mock_key_${active.id.take(4)}",
                        isMock = true
                    )
                    _mockQueue.value = _mockQueue.value.map { if (it.id == active.id) it.copy(status = UploadStatus.COMPLETED) else it }
                    _mockCompletedCount.value += 1
                }

                _mockCurrentUploadingItem.value = null
                delay(200)
            }

            _mockIsUploading.value = false
            _mockCurrentUploadingItem.value = null
            _mockUploadSpeed.value = null

            val simDuration = (System.currentTimeMillis() - simStart).coerceAtLeast(1)
            val avgSpeed = (totalBytesProcessed.toDouble() * 1000.0) / simDuration.toDouble()
            LumenLogger.logRunSummary(
                runId = mockRunId,
                uploadedCount = uploadedCount,
                skippedCount = skippedCount,
                failedCount = failedCount,
                totalBytes = totalBytesProcessed,
                durationMs = simDuration,
                avgSpeedBps = avgSpeed,
                isMock = true
            )
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = ScanState.Idle
        LumenLogger.i("BackupUI") { "User cancelled folder indexing" }
    }

    fun simulateUpdateAvailable() {
        val mockInfo = com.reiux.lumen.data.model.UpdateInfo(
            versionName = "1.0.3-mock",
            releaseNotes = """
                ### What's New
                - **Simulated Update**: This is a mock update changelog triggered from developer tools.
                - **Markdown Support**: Fixed the parser to actually show bold text, bullets, and properly scaled headers.
                
                ### Fixes
                - Tested the update sheet UI scaling on tablets.
                - Re-wired the download simulation.
            """.trimIndent(),
            downloadUrl = "mock://update",
            isUpdateAvailable = true
        )
        _updateInfo.value = mockInfo
        _showUpdateSheet.value = true
        LumenLogger.i("MockMode") { "Simulated update available notification" }
    }

    fun simulateMockLargeScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val totalFakeFiles = 50_000
            val mockFolders = listOf("DCIM/Camera", "Pictures/Screenshots", "Download", "WhatsApp/Media", "Telegram")
            var currentFiles = 0

            while (currentFiles < totalFakeFiles && isActive) {
                delay(150L)
                currentFiles += (1200..3500).random()
                if (currentFiles > totalFakeFiles) currentFiles = totalFakeFiles
                val folder = mockFolders.random()
                _scanState.value = ScanState.Scanning(
                    filesFound = currentFiles,
                    foldersScanned = (currentFiles / 1000) + 1,
                    currentFolderName = folder
                )
            }

            _scanState.value = ScanState.Done(totalFakeFiles)
            delay(1200L)
            _scanState.value = ScanState.Idle
            LumenLogger.i("MockMode") { "Simulated mock scan completed: 50,000 files" }
        }
    }

    fun simulateMockBlock(kind: AuthFailureKind) {
        val email = selectedAccount.value?.email ?: "photos.vault@gmail.com"
        val retryAfterTs = if (kind == AuthFailureKind.RATE_LIMITED) System.currentTimeMillis() + 120_000L else null
        _mockAccountBlock.value = AccountBlock(
            kind = kind,
            timestamp = System.currentTimeMillis(),
            accountEmail = email,
            retryAfterTimestamp = retryAfterTs,
            rawDetail = "Simulated mock circuit breaker block: $kind"
        )
        LumenLogger.w("MockMode") { "Simulated mock circuit breaker block: $kind" }
    }

    fun clearMockBlock() {
        _mockAccountBlock.value = null
        val email = selectedAccount.value?.email
        if (email != null) {
            uploadRepo.clearAccountBlock(email)
        }
        LumenLogger.i("MockMode") { "Cleared circuit breaker block" }
    }

    // --- Backup Operations ---
    fun startBackup() {
        if (_isMockMode.value) {
            if (_mockIsUploading.value) return
            _mockIsUploading.value = true
            startMockSimulation()
            return
        }
        if (_scanState.value is ScanState.Scanning || uploadRepo.isUploading.value) {
            LumenLogger.d("BackupUI", "Backup or scan already in progress, ignoring start request")
            return
        }
        val account = selectedAccount.value
        if (account == null) {
            LumenLogger.w("BackupUI", "Cannot start backup: No account selected")
            return
        }

        val currentSettings = settings.value
        val enabledFolders = currentSettings.folders.filter { it.enabled }
        if (enabledFolders.isEmpty()) {
            LumenLogger.w("BackupUI", "Cannot start backup: No folders enabled")
            return
        }

        LumenLogger.i("BackupUI") { "User initiated backup with ${enabledFolders.size} active folders" }

        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanState.Scanning(0, 0, "")
            val scanStart = System.currentTimeMillis()

            try {
                var lastProgress = com.reiux.lumen.util.ScanProgress(0, 0, "")
                com.reiux.lumen.util.MediaFileReader.scanMediaFilesIncremental(getApplication(), enabledFolders)
                    .collect { progress ->
                        lastProgress = progress
                        if (!progress.isComplete) {
                            _scanState.value = ScanState.Scanning(
                                filesFound = progress.filesFound,
                                foldersScanned = progress.foldersScanned,
                                currentFolderName = progress.currentFolderName
                            )
                        }
                    }

                val durationMs = System.currentTimeMillis() - scanStart
                LumenLogger.i("BackupUI") { "Indexing complete: ${lastProgress.filesFound} files found in ${durationMs}ms" }

                _scanState.value = ScanState.Done(lastProgress.filesFound)

                if (lastProgress.scannedFiles.isNotEmpty()) {
                    uploadRepo.enqueueScannedFiles(
                        scannedFiles = lastProgress.scannedFiles,
                        account = account,
                        storageSaver = currentSettings.storageSaverQuality,
                        useQuota = currentSettings.useQuota
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    LumenLogger.i("BackupUI") { "Folder scan job cancelled by user" }
                } else {
                    LumenLogger.e("BackupUI", tr = e) { "Folder scan error: ${e.message}" }
                }
            } finally {
                _scanState.value = ScanState.Idle
            }
        }
    }

    fun uploadSelectedUris(uris: List<Uri>, context: Context) {
        val account = selectedAccount.value
        if (account == null) {
            LumenLogger.w("BackupUI", "Cannot upload selected URIs: No account selected")
            return
        }
        LumenLogger.i("BackupUI", "User picked ${uris.size} URIs via System Photo Picker")

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val files = mutableListOf<File>()
                for (uri in uris) {
                    val originalName = getFileNameFromUri(context, uri) ?: "picker_${System.currentTimeMillis()}.jpg"
                    val file = MediaFileReader.stageUriFile(context, uri, originalName)
                    if (file != null) {
                        files.add(file)
                    }
                }
                if (files.isNotEmpty()) {
                    uploadRepo.enqueueFiles(files, account, settings.value.storageSaverQuality, settings.value.useQuota)
                }
            }
        }
    }

    fun runLoggingSelfTest() {
        viewModelScope.launch {
            LumenLogger.i("Diagnostics") { "=== RUNNING LOGGING SELF-TEST ===" }

            // 1. Fake Full Upload (Success) with op#upl_test001
            val op1 = "upl_test001"
            LumenLogger.d("UploadEngine", opId = op1) { "File enqueued: path=/sdcard/DCIM/Camera/IMG_TEST_SUCCESS.jpg name=IMG_TEST_SUCCESS.jpg size=4194304bytes" }
            LumenLogger.d("UploadEngine", opId = op1) { "Worker #0 picked item file='IMG_TEST_SUCCESS.jpg' size=4194304B queueRemaining=1" }
            LumenLogger.d("UploadEngine", opId = op1) { "Hashing complete algorithm=SHA-1 bytes=4194304 durationMs=35 sha1=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0" }
            LumenLogger.d("UploadEngine", opId = op1) { "Local cache MISS" }
            LumenLogger.d("UploadEngine", opId = op1) { "Remote dedup check START sha1=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0" }
            LumenLogger.d("Network", opId = op1) { "[POST 200] https://photosdata-pa.googleapis.com/6439526531001121323/5084965799730810217 reqSize=56B respSize=0B duration=120ms" }
            LumenLogger.d("UploadEngine", opId = op1) { "Remote dedup MISS checkDurationMs=120" }
            LumenLogger.d("UploadEngine", opId = op1) { "Requesting upload token attempt=1/3 size=4194304B" }
            LumenLogger.d("Network", opId = op1) { "[POST 200] https://photos.googleapis.com/data/upload/uploadmedia/interactive reqSize=42B respSize=108B duration=145ms" }
            LumenLogger.d("UploadEngine", opId = op1) { "Upload token acquired successfully" }
            LumenLogger.i("UploadEngine", opId = op1) { "Streaming binary bytes (4194304 bytes)..." }
            LumenLogger.v("UploadEngine", opId = op1) { "Bytes streamed: 10% (419430/4194304 bytes) speed=4.8 MB/s elapsed=0.1s" }
            LumenLogger.v("UploadEngine", opId = op1) { "Bytes streamed: 50% (2097152/4194304 bytes) speed=5.2 MB/s elapsed=0.4s" }
            LumenLogger.v("UploadEngine", opId = op1) { "Bytes streamed: 100% (4194304/4194304 bytes) speed=5.5 MB/s elapsed=0.8s" }
            LumenLogger.d("Network", opId = op1) { "[POST 200] https://photos.googleapis.com/data/upload/uploadmedia/interactive?upload_id=test_tok reqSize=4194304B respSize=64B duration=800ms" }
            LumenLogger.d("UploadEngine", opId = op1) { "Commit request START fileName='IMG_TEST_SUCCESS.jpg' storageSaver=true useQuota=false" }
            LumenLogger.d("Network", opId = op1) { "[POST 200] https://photosdata-pa.googleapis.com/6439526531001121323/16538846908252377752 reqSize=230B respSize=180B duration=210ms" }
            LumenLogger.d("UploadEngine", opId = op1) { "Commit response SUCCESS mediaKey=AF1QipN3x_fake_key_998 durationMs=210" }
            LumenLogger.i("UploadEngine", opId = op1) { "Upload SUCCESS mediaKey=AF1QipN3x_fake_key_998 totalDurationMs=1320 attempts=1" }

            // 2. Fake Failed Upload with Retry with op#upl_test002
            val op2 = "upl_test002"
            LumenLogger.d("UploadEngine", opId = op2) { "File enqueued: path=/sdcard/DCIM/Camera/IMG_TEST_FAIL.jpg name=IMG_TEST_FAIL.jpg size=8388608bytes" }
            LumenLogger.d("UploadEngine", opId = op2) { "Worker #1 picked item file='IMG_TEST_FAIL.jpg' size=8388608B queueRemaining=0" }
            LumenLogger.d("UploadEngine", opId = op2) { "Hashing complete algorithm=SHA-1 bytes=8388608 durationMs=68 sha1=b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1" }
            LumenLogger.d("UploadEngine", opId = op2) { "Local cache MISS" }
            LumenLogger.d("UploadEngine", opId = op2) { "Remote dedup MISS checkDurationMs=115" }
            LumenLogger.d("UploadEngine", opId = op2) { "Requesting upload token attempt=1/3 size=8388608B" }
            LumenLogger.d("UploadEngine", opId = op2) { "Upload token acquired successfully" }
            LumenLogger.w("Network", opId = op2) { "[POST 503 ERROR] https://photos.googleapis.com/data/upload/uploadmedia/interactive reqSize=8388608B respSize=45B duration=1500ms errBody='{\"error\":{\"code\":503,\"message\":\"Service Unavailable\"}}'" }
            LumenLogger.w("UploadEngine", opId = op2) { "Upload attempt 1/3 failed: HTTP 503 Service Unavailable. Retrying in 2000ms..." }
            LumenLogger.w("UploadEngine", opId = op2) { "Upload attempt 2/3 failed: java.net.SocketTimeoutException: timeout. Retrying in 4000ms..." }
            LumenLogger.e("UploadEngine", opId = op2) { "Upload FAILED after 3 attempts totalDurationMs=7800 reason=HTTP 503 Service Unavailable" }

            // 3. Network Error
            LumenLogger.w("Network", opId = "net_test003") {
                "[POST 500 ERROR] https://photos.googleapis.com/v1/mediaItems reqSize=512B respSize=88B duration=450ms errBody='{\"error\":{\"code\":500,\"message\":\"Internal server error\"}}'"
            }

            // 4. Folder Watcher Event
            LumenLogger.d("Watcher") { "Raw observer event: path=/sdcard/DCIM/Camera/IMG_20261022_120400.jpg event=CREATE (0x100)" }
            LumenLogger.d("Watcher") { "Debounce complete (1500ms): 1 candidate paths" }
            LumenLogger.i("Watcher") { "Watcher enqueued 1 new media files: [IMG_20261022_120400.jpg]" }

            // 5. UI Interaction & Animation
            LumenLogger.d("Interaction") { "Click on 'Start Backup' at (x=540, y=1120)" }
            LumenLogger.animStart("screenTransition", "BackupScreen", "alpha", 0f, 1f, "spring(stiffness=400, damping=0.85)", "user_tap")
            LumenLogger.animEnd("screenTransition", "BackupScreen", 280L, 1f, true)

            // 6. Settings change old -> new
            LumenLogger.i("Settings") { "autoBackupEnabled changed: old=true -> new=false" }

            // 7. Crash simulation (logged without killing process)
            try {
                throw java.lang.IllegalStateException("Simulated self-test diagnostic exception for crash logger verification")
            } catch (e: Exception) {
                LumenLogger.e("Crash", opId = "CRASH_TEST", tr = e) {
                    "SIMULATED UNCAUGHT EXCEPTION: ${e.message}"
                }
            }

            LumenLogger.i("Diagnostics") { "=== LOGGING SELF-TEST COMPLETE: All events emitted successfully ===" }
        }
    }

    fun setIncludeSubfolders(path: String, includeSubfolders: Boolean) {
        if (_isMockMode.value) {
            val updated = _mockSettings.value.folders.map {
                if (it.path == path) it.copy(includeSubfolders = includeSubfolders) else it
            }
            _mockSettings.value = _mockSettings.value.copy(folders = updated)
        } else {
            settingsRepo.setIncludeSubfolders(path, includeSubfolders)
        }
    }

    fun pauseBackup() {
        LumenLogger.i("BackupUI", "pauseBackup called")
        if (_isMockMode.value) {
            _mockIsUploading.value = false
            mockSimJob?.cancel()
            mockSimJob = null
            return
        }
        uploadRepo.pauseQueue()
    }

    fun cancelBackup() {
        LumenLogger.w("BackupUI", "User clicked Cancel Backup")
        if (_isMockMode.value) {
            _mockIsUploading.value = false
            mockSimJob?.cancel()
            mockSimJob = null
            _mockUploadSpeed.value = null
            _mockQueue.value = _mockQueue.value.filter {
                it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED || it.status == UploadStatus.FAILED
            }
            _mockCurrentUploadingItem.value = null
            return
        }
        uploadRepo.cancelQueue()
    }

    fun acknowledgeAccountRisk() {
        LumenLogger.i("Settings", "User acknowledged account risk disclosure")
        settingsRepo.updateHasAcknowledgedAccountRisk(true)
    }

    fun clearCompleted() {
        LumenLogger.i("BackupUI", "User clicked Clear Completed")
        if (_isMockMode.value) {
            _mockQueue.value = _mockQueue.value.filter {
                it.status != UploadStatus.COMPLETED &&
                it.status != UploadStatus.SKIPPED &&
                it.status != UploadStatus.FAILED
            }
            return
        }
        uploadRepo.clearCompleted()
    }

    // --- Folders Operations ---
    fun toggleFolder(path: String, enabled: Boolean) {
        LumenLogger.i("FoldersUI", "Toggled folder '$path' to enabled=$enabled")
        if (_isMockMode.value) {
            _mockSettings.value = _mockSettings.value.copy(
                folders = _mockSettings.value.folders.map {
                    if (it.path == path) it.copy(enabled = enabled) else it
                }
            )
            return
        }
        settingsRepo.toggleFolder(path, enabled)
    }

    fun addFolder(folder: BackupFolder) {
        LumenLogger.i("FoldersUI", "Added folder '${folder.path}' (${folder.displayName})")
        if (_isMockMode.value) {
            _mockSettings.value = _mockSettings.value.copy(
                folders = _mockSettings.value.folders + folder
            )
            return
        }
        settingsRepo.addFolder(folder)
    }

    fun removeFolder(path: String) {
        LumenLogger.i("FoldersUI", "Removed folder '$path'")
        if (_isMockMode.value) {
            _mockSettings.value = _mockSettings.value.copy(
                folders = _mockSettings.value.folders.filter { it.path != path }
            )
            return
        }
        settingsRepo.removeFolder(path)
    }

    // --- Settings Operations ---
    fun updateAutoBackup(enabled: Boolean) {
        LumenLogger.i("SettingsUI", "Auto backup updated to: $enabled")
        if (_isMockMode.value) {
            _mockSettings.value = _mockSettings.value.copy(autoBackupEnabled = enabled)
        } else {
            settingsRepo.updateAutoBackup(enabled)
        }
    }

    fun updateWifiOnly(enabled: Boolean) {
        LumenLogger.i("SettingsUI", "Backup over Wi-Fi only updated to: $enabled")
        if (_isMockMode.value) {
            _mockSettings.value = _mockSettings.value.copy(wifiOnly = enabled)
        } else {
            settingsRepo.updateWifiOnly(enabled)
        }
    }

    fun updateChargingOnly(enabled: Boolean) {
        LumenLogger.i("SettingsUI", "Backup while charging only updated to: $enabled")
        if (_isMockMode.value) {
            _mockSettings.value = _mockSettings.value.copy(chargingOnly = enabled)
        } else {
            settingsRepo.updateChargingOnly(enabled)
        }
    }

    fun updateStorageSaver(enabled: Boolean) {
        LumenLogger.i("SettingsUI", "Storage Saver quality updated to: $enabled")
        if (_isMockMode.value) {
            val newUseQuota = if (enabled) false else _mockSettings.value.useQuota
            _mockSettings.value = _mockSettings.value.copy(
                storageSaverQuality = enabled,
                useQuota = newUseQuota
            )
        } else {
            settingsRepo.updateStorageSaver(enabled)
        }
    }

    fun updateUseQuota(enabled: Boolean) {
        LumenLogger.i("SettingsUI", "Use Account Quota updated to: $enabled")
        if (_isMockMode.value) {
            val newStorageSaver = if (enabled) false else _mockSettings.value.storageSaverQuality
            _mockSettings.value = _mockSettings.value.copy(
                useQuota = enabled,
                storageSaverQuality = newStorageSaver
            )
        } else {
            settingsRepo.updateUseQuota(enabled)
        }
    }

    fun unlockPrismTheme() {
        LumenLogger.i("EasterEgg", "Unlocked Prism Theme!")
        if (_isMockMode.value) {
            _mockSettings.value = _mockSettings.value.copy(
                unlockedPrismTheme = true,
                colorPreset = "Prism"
            )
            return
        }
        settingsRepo.unlockPrismTheme()
    }

    fun unlockMatrixTheme() {
        LumenLogger.i("EasterEgg", "Unlocked Matrix Theme!")
        if (_isMockMode.value) {
            _mockSettings.value = _mockSettings.value.copy(
                developerModeUnlocked = true,
                unlockedMatrixTheme = true,
                colorPreset = "Matrix"
            )
            return
        }
        settingsRepo.updateDeveloperMode(true)
        settingsRepo.unlockMatrixTheme()
    }

    fun updateUploadThreads(threads: Int) {
        val safeThreads = threads.coerceIn(1, 20)
        LumenLogger.i("SettingsUI", "Concurrent upload threads updated to: $safeThreads")
        if (_isMockMode.value) {
            _mockSettings.value = _mockSettings.value.copy(uploadThreads = safeThreads)
        } else {
            settingsRepo.updateUploadThreads(safeThreads)
        }
    }

    fun updateColorPreset(preset: String) {
        LumenLogger.i("SettingsUI", "Color preset updated to: $preset")
        if (_isMockMode.value) {
            _mockSettings.value = _mockSettings.value.copy(colorPreset = preset)
        }
        settingsRepo.updateColorPreset(preset)
    }

    fun updateNotifications(enabled: Boolean) {
        LumenLogger.i("SettingsUI", "Notifications updated to: $enabled")
        if (_isMockMode.value) {
            _mockSettings.value = _mockSettings.value.copy(notificationsEnabled = enabled)
        } else {
            settingsRepo.updateNotifications(enabled)
        }
    }

    fun setHasCompletedOnboarding(completed: Boolean) {
        LumenLogger.i("Onboarding", "Onboarding state updated to: $completed")
        settingsRepo.updateHasCompletedOnboarding(completed)
    }

    fun updateForcedSeason(season: String) {
        LumenLogger.i("SettingsUI", "Forced season updated to: $season")
        if (_isMockMode.value) {
            _mockSettings.value = _mockSettings.value.copy(forcedSeason = season)
        } else {
            settingsRepo.updateForcedSeason(season)
        }
    }

    fun getLastSeenDarkState(): Boolean? {
        return settingsRepo.getLastSeenDarkState()
    }

    fun setLastSeenDarkState(isDark: Boolean) {
        settingsRepo.setLastSeenDarkState(isDark)
    }

    fun updateDeveloperMode(unlocked: Boolean) {
        LumenLogger.i("SettingsUI", "Developer mode updated to: $unlocked")
        if (!unlocked) {
            _isShowcaseMode.value = false
            _isMockMode.value = false
            mockSimJob?.cancel()
            mockSimJob = null
        }
        _mockSettings.value = _mockSettings.value.copy(developerModeUnlocked = unlocked)
        settingsRepo.updateDeveloperMode(unlocked)
    }

    fun updateSaveLogs(enabled: Boolean, context: Context) {
        LumenLogger.i("SettingsUI", "Save debug logs to file updated to: $enabled")
        if (_isMockMode.value) {
            _mockSettings.value = _mockSettings.value.copy(saveLogsEnabled = enabled)
            return
        }
        settingsRepo.updateSaveLogs(enabled)
        com.reiux.lumen.util.LumenLogger.setFileLogging(enabled, context)
    }

    fun exportLogs(context: Context) {
        LumenLogger.i("SettingsUI", "User triggered export logs")
        com.reiux.lumen.util.LumenLogger.exportLogs(context)
    }

    fun clearLogs(context: Context) {
        LumenLogger.i("SettingsUI", "User cleared debug logs")
        com.reiux.lumen.util.LumenLogger.clearLogs(context)
    }

    // --- Accounts Operations ---
    fun selectAccount(email: String) {
        LumenLogger.i("AccountsUI", "Switched active account to: $email")
        if (_isMockMode.value) {
            _mockSelectedAccount.value = _mockAccounts.value.firstOrNull { it.email == email }
            return
        }
        accountRepo.selectAccount(email)
    }

    fun updateDisplayName(email: String, displayName: String) {
        LumenLogger.i("AccountsUI", "Updated display name for $email to '$displayName'")
        if (_isMockMode.value) {
            _mockAccounts.value = _mockAccounts.value.map {
                if (it.email == email) it.copy(displayName = displayName) else it
            }
            if (_mockSelectedAccount.value?.email == email) {
                _mockSelectedAccount.value = _mockSelectedAccount.value?.copy(displayName = displayName)
            }
            return
        }
        accountRepo.updateDisplayName(email, displayName)
    }

    fun removeAccount(email: String) {
        LumenLogger.i("AccountsUI", "Removed account: $email")
        if (_isMockMode.value) {
            _mockAccounts.value = _mockAccounts.value.filter { it.email != email }
            if (_mockSelectedAccount.value?.email == email) {
                _mockSelectedAccount.value = _mockAccounts.value.firstOrNull()
            }
            return
        }
        accountRepo.removeAccount(email)
    }

    fun addAccountWithToken(token: String) {
        LumenLogger.i("AccountsUI", "Adding account via OAuth token...")
        if (_isMockMode.value) {
            val count = _mockAccounts.value.size + 1
            val newAcc = GoogleAccount("user$count@example.com", "mock_cred_$count", "User $count")
            _mockAccounts.value = _mockAccounts.value + newAcc
            _mockSelectedAccount.value = newAcc
            return
        }
        viewModelScope.launch {
            _isAddingAccount.value = true
            _addAccountError.value = null
            accountRepo.addAccountFromOAuthToken(token)
                .onSuccess {
                    LumenLogger.i("AccountsUI", "Successfully added account via OAuth token: ${it.email}")
                    _isAddingAccount.value = false
                }
                .onFailure {
                    LumenLogger.e("AccountsUI", "Failed to authenticate account with OAuth token: ${it.message}", it)
                    _isAddingAccount.value = false
                    _addAccountError.value = it.message ?: "Failed to authenticate account"
                }
        }
    }

    fun addAccountWithCredential(credential: String) {
        LumenLogger.i("AccountsUI", "Importing account credential string...")
        if (_isMockMode.value) {
            val count = _mockAccounts.value.size + 1
            val newAcc = GoogleAccount("user$count@example.com", "mock_cred_$count", "User $count")
            _mockAccounts.value = _mockAccounts.value + newAcc
            _mockSelectedAccount.value = newAcc
            return
        }
        viewModelScope.launch {
            _isAddingAccount.value = true
            _addAccountError.value = null
            accountRepo.importCredentialString(credential)
                .onSuccess {
                    LumenLogger.i("AccountsUI", "Successfully imported credential for: ${it.email}")
                    _isAddingAccount.value = false
                }
                .onFailure {
                    LumenLogger.e("AccountsUI", "Failed to import credential string: ${it.message}", it)
                    _isAddingAccount.value = false
                    _addAccountError.value = it.message ?: "Failed to import credential"
                }
        }
    }

    fun clearAddAccountError() {
        _addAccountError.value = null
    }

    fun updateAccountAvatar(email: String, uri: Uri, context: Context) {
        LumenLogger.i("AccountsUI", "Updating custom avatar for $email")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
                    val targetFile = File(avatarDir, "${email.hashCode()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (targetFile.exists()) {
                        accountRepo.updateAvatar(email, targetFile.absolutePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                // Query MediaStore DATA column first to preserve exact original filesystem case (e.g. .HEIC)
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.MediaStore.MediaColumns.DATA, OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIdx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                        if (dataIdx >= 0) {
                            val dataPath = cursor.getString(dataIdx)
                            if (!dataPath.isNullOrEmpty()) {
                                name = File(dataPath).name
                            }
                        }
                        if (name == null) {
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (index >= 0) {
                                name = cursor.getString(index)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }
}
