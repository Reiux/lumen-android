package com.reiux.lumen

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.reiux.lumen.data.repository.AccountRepository
import com.reiux.lumen.data.repository.SettingsRepository
import com.reiux.lumen.data.repository.UploadRepository
import com.reiux.lumen.service.MediaContentJobService
import com.reiux.lumen.service.UploadForegroundService
import com.reiux.lumen.util.FolderWatcherManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class LumenApplication : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var accountRepository: AccountRepository
        private set

    lateinit var uploadRepository: UploadRepository
        private set

    lateinit var folderWatcher: FolderWatcherManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this)
        com.reiux.lumen.util.LumenLogger.init(this, settingsRepository.settings.value.saveLogsEnabled)
        val authClient = com.reiux.lumen.data.network.GoogleAuthClient()
        accountRepository = AccountRepository(this, authClient = authClient)
        uploadRepository = UploadRepository(this, settingsRepository, accountRepository, authClient = authClient)

        // Clean stale account blocks for accounts that no longer exist
        val validAccountEmails = accountRepository.accounts.value.map { it.email }.toSet()
        settingsRepository.cleanStaleAccountBlocks(validAccountEmails)

        // Clean leftover temporary staged files from any interrupted/killed previous sessions
        appScope.launch {
            try {
                File(cacheDir, "staging").deleteRecursively()
            } catch (_: Exception) {}
        }

        // Continuous in-process folder watcher
        folderWatcher = FolderWatcherManager(
            context = this,
            settingsRepository = settingsRepository
        ) { newFiles ->
            val currentSettings = settingsRepository.settings.value
            if (!currentSettings.autoBackupEnabled) return@FolderWatcherManager

            val allAccounts = accountRepository.accounts.value
            if (allAccounts.isEmpty()) return@FolderWatcherManager

            val enabledFolders = settingsRepository.getAllFolders().filter { it.enabled && !it.accountEmail.isNullOrBlank() }
            if (enabledFolders.isEmpty()) return@FolderWatcherManager

            val filesByAccount = mutableMapOf<String, MutableList<File>>()
            for (file in newFiles) {
                val matchingFolders = enabledFolders.filter {
                    FolderWatcherManager.isPathMatchingFolder(file.absolutePath, it)
                }
                for (folder in matchingFolders) {
                    val email = folder.accountEmail?.trim() ?: continue
                    filesByAccount.getOrPut(email.lowercase()) { mutableListOf() }.add(file)
                }
            }

            for ((emailLower, accountFiles) in filesByAccount) {
                val account = allAccounts.firstOrNull { it.email.equals(emailLower, ignoreCase = true) } ?: continue
                uploadRepository.enqueueFiles(
                    files = accountFiles.distinctBy { it.absolutePath },
                    account = account,
                    storageSaver = currentSettings.storageSaverQuality,
                    useQuota = currentSettings.useQuota
                )
            }
        }
        folderWatcher.start()

        // Schedule OS-level MediaStore triggers via JobScheduler whenever auto-backup is enabled
        appScope.launch {
            settingsRepository.settings
                .map { it.autoBackupEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        MediaContentJobService.scheduleMediaWatcher(this@LumenApplication)
                        checkAndEnqueueRecentMedia()
                    }
                }
        }

        // Schedule periodic background update checks so users get notified of new releases
        appScope.launch {
            val workManager = androidx.work.WorkManager.getInstance(this@LumenApplication)
            settingsRepository.settings
                .map { it.autoCheckUpdates }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        val constraints = androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                        
                        val updateCheckWork = androidx.work.PeriodicWorkRequestBuilder<com.reiux.lumen.service.UpdateCheckWorker>(
                            com.reiux.lumen.data.repository.UpdateRepository.BACKGROUND_CHECK_INTERVAL_HOURS,
                            java.util.concurrent.TimeUnit.HOURS
                        )
                            .setConstraints(constraints)
                            .setBackoffCriteria(
                                androidx.work.BackoffPolicy.EXPONENTIAL,
                                15, java.util.concurrent.TimeUnit.MINUTES
                            )
                            .build()
                        
                        // UPDATE (not KEEP) so installs that already have the old 24h job
                        // pick up the new interval instead of keeping the stale schedule.
                        workManager.enqueueUniquePeriodicWork(
                            "LumenUpdateCheck",
                            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                            updateCheckWork
                        )
                    } else {
                        workManager.cancelUniqueWork("LumenUpdateCheck")
                    }
                }
        }

        // Foreground notification ONLY runs while files are actively uploading, and dismisses when finished
        appScope.launch {
            uploadRepository.isUploading.collect { isUploading ->
                if (isUploading) {
                    UploadForegroundService.startOrUpdate(this@LumenApplication)
                } else {
                    UploadForegroundService.stop(this@LumenApplication)
                }
            }
        }
    }

    /**
     * Checks enabled folders for any recent media and enqueues them for background backup.
     * Can be invoked by BootReceiver on startup or MediaContentJobService on OS MediaStore trigger.
     */
    fun checkAndEnqueueRecentMedia() {
        appScope.launch {
            val allAccounts = accountRepository.accounts.value
            if (allAccounts.isEmpty()) return@launch
            val settings = settingsRepository.settings.value
            if (!settings.autoBackupEnabled) return@launch

            val supportedExts = setOf(
                "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "dng", "cr2", "nef", "arw",
                "mp4", "mov", "mkv", "avi", "3gp", "webm"
            )

            for (account in allAccounts) {
                val enabledFolders = settingsRepository.getFoldersForAccount(account.email).filter { it.enabled }
                val validFolders = enabledFolders.filter { !it.accountEmail.isNullOrBlank() }
                if (validFolders.isEmpty()) continue

                val foundFiles = mutableListOf<File>()

                for (folder in validFolders) {
                    val lastWatermarkId = settingsRepository.getFolderWatermarkId(account.email, folder.path)
                    var maxId = lastWatermarkId
                    val folderNewFiles = mutableListOf<File>()

                    val queryUris = listOf(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    )

                    val projection = mutableListOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DATA,
                        MediaStore.MediaColumns.DATE_ADDED
                    ).apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            add(MediaStore.MediaColumns.RELATIVE_PATH)
                            add(MediaStore.MediaColumns.IS_PENDING)
                        }
                        add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    }.toTypedArray()

                    var selection = "${MediaStore.MediaColumns._ID} > ?"
                    var selectionArgs = arrayOf(lastWatermarkId.toString())

                    if (lastWatermarkId == 0L) {
                        val yesterdaySec = (System.currentTimeMillis() / 1000) - 86400
                        selection = "${MediaStore.MediaColumns.DATE_ADDED} > ?"
                        selectionArgs = arrayOf(yesterdaySec.toString())
                    }

                    val nowSec = System.currentTimeMillis() / 1000
                    var firstNotReadyId: Long? = null
                    for (uri in queryUris) {
                        try {
                            FolderWatcherManager.queryIncludingPending(contentResolver, uri, projection, selection, selectionArgs, "${MediaStore.MediaColumns._ID} ASC")?.use { cursor ->
                                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                                val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
                                val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                                val pendingCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING) else -1
                                val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)

                                while (cursor.moveToNext()) {
                                    val mediaId = if (idCol >= 0) cursor.getLong(idCol) else 0L
                                    val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                                    val relPath = if (relCol >= 0) cursor.getString(relCol) else null
                                    val bucketName = if (bucketCol >= 0) cursor.getString(bucketCol) else null

                                    if (FolderWatcherManager.isMediaStoreItemInFolder(path, relPath, bucketName, folder)) {
                                        val isPending = pendingCol >= 0 && cursor.getInt(pendingCol) == 1
                                        val file = if (!path.isNullOrEmpty()) File(path) else null
                                        val fileReady = !isPending && file != null && file.exists() && file.isFile && file.length() > 0
                                        val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L

                                        // Don't move the watermark past files still being written
                                        if (FolderWatcherManager.shouldHoldWatermark(fileReady, dateAdded, nowSec)) {
                                            if (firstNotReadyId == null || mediaId < firstNotReadyId!!) firstNotReadyId = mediaId
                                            continue
                                        }
                                        if (mediaId > maxId) maxId = mediaId
                                        if (fileReady && file != null && file.extension.lowercase() in supportedExts) {
                                            folderNewFiles.add(file)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            com.reiux.lumen.util.LumenLogger.w("LumenApp") { "Error querying MediaStore for folder ${folder.path}: ${e.message}" }
                        }
                    }

                    val newWatermark = FolderWatcherManager.computeSafeWatermark(lastWatermarkId, maxId, firstNotReadyId)
                    if (newWatermark > lastWatermarkId) {
                        settingsRepository.saveFolderWatermarkId(account.email, folder.path, newWatermark)
                    }
                    foundFiles.addAll(folderNewFiles)
                }

                if (foundFiles.isNotEmpty()) {
                    com.reiux.lumen.util.LumenLogger.i("LumenApp") { "Found ${foundFiles.size} new item(s) in enabled folders for ${account.email} since watermark. Enqueueing." }
                    uploadRepository.enqueueFiles(
                        files = foundFiles.distinctBy { it.absolutePath },
                        account = account,
                        storageSaver = settings.storageSaverQuality,
                        useQuota = settings.useQuota
                    )
                }
            }
        }
    }

    companion object {
        lateinit var instance: LumenApplication
            private set
    }
}
