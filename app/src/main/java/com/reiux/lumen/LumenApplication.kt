package com.reiux.lumen

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.reiux.lumen.data.repository.AccountRepository
import com.reiux.lumen.data.repository.SettingsRepository
import com.reiux.lumen.data.repository.UploadRepository
import com.reiux.lumen.service.BackgroundUploadWorker
import com.reiux.lumen.service.MediaContentJobService
import com.reiux.lumen.service.UploadForegroundService
import com.reiux.lumen.util.FolderWatcherManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    val mediaScanMutex = Mutex()

    @Volatile
    var startedFromBootBroadcast: Boolean = false

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this)
        com.reiux.lumen.util.LumenLogger.init(this, settingsRepository.settings.value.saveLogsEnabled)
        val authClient = com.reiux.lumen.data.network.GoogleAuthClient()
        accountRepository = AccountRepository(this, authClient = authClient)
        uploadRepository = UploadRepository(this, settingsRepository, accountRepository, authClient = authClient)

        publishDynamicShortcuts()

        // Clean stale account blocks for accounts that no longer exist
        val validAccountEmails = accountRepository.accounts.value.map { it.email }.toSet()
        settingsRepository.cleanStaleAccountBlocks(validAccountEmails)

        // Clean leftover temporary staged files and stale update downloads from any interrupted/killed previous sessions
        val appStartMillis = System.currentTimeMillis()
        appScope.launch {
            try {
                com.reiux.lumen.util.MediaFileReader.cleanupStaleStagingOnStartup(
                    this@LumenApplication,
                    appStartMillis - 5_000L
                )
            } catch (_: Exception) {}
            try {
                val updatesDir = File(cacheDir, "updates")
                if (updatesDir.exists() && updatesDir.isDirectory) {
                    val stalePartCutoff = appStartMillis - 5_000L
                    val twoHoursAgo = appStartMillis - java.util.concurrent.TimeUnit.HOURS.toMillis(2)
                    updatesDir.listFiles()?.forEach { file ->
                        try {
                            if (file.isFile && (
                                    (file.name.endsWith(".part") && file.lastModified() < stalePartCutoff) ||
                                        (file.name.endsWith(".apk") && file.lastModified() < twoHoursAgo)
                                )) {
                                file.delete()
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        // Continuous in-process folder watcher
        folderWatcher = FolderWatcherManager(
            context = this,
            settingsRepository = settingsRepository,
            watermarkMutex = mediaScanMutex
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

            startedFromBootBroadcast = false
            for ((emailLower, accountFiles) in filesByAccount) {
                val account = allAccounts.firstOrNull { it.email.equals(emailLower, ignoreCase = true) } ?: continue
                uploadRepository.enqueueFiles(
                    files = accountFiles.distinctBy { it.absolutePath },
                    account = account,
                    storageSaver = currentSettings.storageSaverQuality,
                    useQuota = currentSettings.useQuota,
                    allowDeleteAfterBackup = true
                )
            }
        }
        folderWatcher.start()

        // Schedule OS-level MediaStore triggers via JobScheduler whenever auto-backup is enabled,
        // and cancel the JobScheduler watcher immediately when auto-backup is disabled.
        appScope.launch {
            settingsRepository.settings
                .map { it.autoBackupEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        MediaContentJobService.ensureMediaWatcherScheduled(this@LumenApplication)
                        checkAndEnqueueRecentMedia(fromBoot = false, clearBootFlag = false)
                    } else {
                        MediaContentJobService.cancelMediaWatcher(this@LumenApplication)
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
                            .setInitialDelay(
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

        // Foreground notification ONLY runs while files are actively uploading, and dismisses when finished.
        // Never call context.stopService() externally here: if isUploading flips true -> false quickly
        // (e.g. instant local-cache deduplication), calling stopService() while startForegroundService()
        // is still in-flight prevents UploadForegroundService from calling startForeground() in time and
        // crashes with ForegroundServiceDidNotStartInTimeException. UploadForegroundService stops itself
        // via stopSelf() after calling startForeground().
        appScope.launch {
            uploadRepository.isUploading.collectLatest { isUploading ->
                if (isUploading) {
                    kotlinx.coroutines.delay(150L)
                    if (!uploadRepository.isUploading.value) return@collectLatest
                    val notificationsEnabled = settingsRepository.settings.value.notificationsEnabled
                    val bootFgsBlockedOnApi35 = Build.VERSION.SDK_INT >= 35 && startedFromBootBroadcast
                    if (bootFgsBlockedOnApi35 || !notificationsEnabled) {
                        // Android 15+ blocks starting dataSync FGS from BOOT_COMPLETED, and when
                        // notifications are disabled we still want a WorkManager execution lease.
                        BackgroundUploadWorker.enqueue(this@LumenApplication)
                    } else {
                        UploadForegroundService.startOrUpdate(this@LumenApplication)
                    }
                } else {
                    startedFromBootBroadcast = false
                    BackgroundUploadWorker.cancel(this@LumenApplication)
                }
            }
        }
    }

    private fun publishDynamicShortcuts() {
        try {
            val internalToken = settingsRepository.getOrCreateInternalLaunchToken()
            val syncIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", "shortcut_sync")
                putExtra("internal_token", internalToken)
            }
            val pickIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", "shortcut_pick")
                putExtra("internal_token", internalToken)
            }

            val syncShortcut = ShortcutInfoCompat.Builder(this, "shortcut_sync")
                .setShortLabel(getString(R.string.shortcut_sync_short))
                .setLongLabel(getString(R.string.shortcut_sync_long))
                .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(syncIntent)
                .build()

            val pickShortcut = ShortcutInfoCompat.Builder(this, "shortcut_pick")
                .setShortLabel(getString(R.string.shortcut_pick_short))
                .setLongLabel(getString(R.string.shortcut_pick_long))
                .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(pickIntent)
                .build()

            ShortcutManagerCompat.setDynamicShortcuts(this, listOf(syncShortcut, pickShortcut))
        } catch (e: Exception) {
            com.reiux.lumen.util.LumenLogger.w("LumenApp") { "Failed publishing dynamic shortcuts: ${e.message}" }
        }
    }

    /**
     * Checks enabled folders for any recent media and enqueues them for background backup.
     * Can be invoked by BootReceiver on startup or MediaContentJobService on OS MediaStore trigger.
     */
    @JvmOverloads
    fun checkAndEnqueueRecentMedia(fromBoot: Boolean = false, clearBootFlag: Boolean = false): Job {
        if (fromBoot) {
            startedFromBootBroadcast = true
        } else if (clearBootFlag) {
            startedFromBootBroadcast = false
        }
        return appScope.launch {
            mediaScanMutex.withLock {
                val allAccounts = accountRepository.accounts.value
                if (allAccounts.isEmpty()) return@withLock
                val settings = settingsRepository.settings.value
                if (!settings.autoBackupEnabled) return@withLock

                val supportedExts = setOf(
                    "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "dng", "cr2", "nef", "arw",
                    "mp4", "mov", "mkv", "avi", "3gp", "webm"
                )
                val highestMediaStoreId by lazy { FolderWatcherManager.queryHighestMediaStoreId(contentResolver) }

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
                                add(MediaStore.MediaColumns.VOLUME_NAME)
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
                                    val volCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME) else -1
                                    val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                                    val pendingCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING) else -1
                                    val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)

                                    while (cursor.moveToNext()) {
                                        val mediaId = if (idCol >= 0) cursor.getLong(idCol) else 0L
                                        val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                                        val relPath = if (relCol >= 0) cursor.getString(relCol) else null
                                        val volumeName = if (volCol >= 0) cursor.getString(volCol) else null
                                        val bucketName = if (bucketCol >= 0) cursor.getString(bucketCol) else null

                                        if (FolderWatcherManager.isMediaStoreItemInFolder(path, relPath, bucketName, folder, volumeName)) {
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
                                            if (fileReady && file != null &&
                                                FolderWatcherManager.isPathMatchingFolder(file.absolutePath, folder) &&
                                                file.extension.lowercase() in supportedExts
                                            ) {
                                                folderNewFiles.add(file)
                                            }
                                        }
                                    }
                                }
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                com.reiux.lumen.util.LumenLogger.w("LumenApp") { "Error querying MediaStore for folder ${folder.path}: ${e.message}" }
                            }
                        }

                        if (lastWatermarkId == 0L && maxId == 0L && firstNotReadyId == null) {
                            maxId = highestMediaStoreId
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
                            useQuota = settings.useQuota,
                            allowDeleteAfterBackup = true
                        )
                    }
                }
            }
        }
    }

    companion object {
        lateinit var instance: LumenApplication
            private set
    }
}
