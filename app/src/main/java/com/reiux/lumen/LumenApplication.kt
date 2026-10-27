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
        accountRepository = AccountRepository(this)
        uploadRepository = UploadRepository(this, settingsRepository)

        // Clean stale account blocks for accounts that no longer exist
        val validAccountEmails = accountRepository.accounts.value.map { it.email }.toSet()
        settingsRepository.cleanStaleAccountBlocks(validAccountEmails)

        // Continuous in-process folder watcher
        folderWatcher = FolderWatcherManager(
            context = this,
            settingsRepository = settingsRepository
        ) { newFiles ->
            val account = accountRepository.selectedAccount.value ?: accountRepository.accounts.value.firstOrNull()
            val autoBackup = settingsRepository.settings.value.autoBackupEnabled
            if (account != null && autoBackup) {
                val isStorageSaver = settingsRepository.settings.value.storageSaverQuality
                val useQuota = settingsRepository.settings.value.useQuota
                uploadRepository.enqueueFiles(newFiles, account, isStorageSaver, useQuota)
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

        // Schedule periodic update checks (Once every 24 hours)
        appScope.launch {
            val workManager = androidx.work.WorkManager.getInstance(this@LumenApplication)
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            
            val updateCheckWork = androidx.work.PeriodicWorkRequestBuilder<com.reiux.lumen.service.UpdateCheckWorker>(
                24, java.util.concurrent.TimeUnit.HOURS
            ).setConstraints(constraints).build()
            
            workManager.enqueueUniquePeriodicWork(
                "LumenUpdateCheck",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                updateCheckWork
            )
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
            val account = accountRepository.selectedAccount.value ?: accountRepository.accounts.value.firstOrNull() ?: return@launch
            val settings = settingsRepository.settings.value
            if (!settings.autoBackupEnabled) return@launch

            val enabledPaths = settings.folders.filter { it.enabled }.map { it.path }
            if (enabledPaths.isEmpty()) return@launch

            val supportedExts = setOf(
                "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "dng", "cr2", "nef", "arw",
                "mp4", "mov", "mkv", "avi", "3gp", "webm"
            )

            val found = queryRecentMediaForPaths(this@LumenApplication, enabledPaths, supportedExts)
            if (found.isNotEmpty()) {
                uploadRepository.enqueueFiles(
                    files = found,
                    account = account,
                    storageSaver = settings.storageSaverQuality,
                    useQuota = settings.useQuota
                )
            }
        }
    }

    private fun queryRecentMediaForPaths(
        context: Context,
        enabledFolderPaths: List<String>,
        supportedExts: Set<String>
    ): List<File> {
        val results = mutableListOf<File>()
        val extStorage = try {
            android.os.Environment.getExternalStorageDirectory().absolutePath
        } catch (_: Exception) {
            "/storage/emulated/0"
        }
        val allNormalizedEnabledPaths = enabledFolderPaths.flatMap { folderPath ->
            val canonical = try { File(folderPath).canonicalPath } catch (_: Exception) { folderPath }
            listOf(folderPath, canonical, folderPath.replaceFirst("/sdcard", extStorage))
        }.toSet()

        val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_ADDED)
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC LIMIT 30"
        val pendingSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.IS_PENDING} = 0"
        } else null

        val queryUris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )

        for (uri in queryUris) {
            try {
                context.contentResolver.query(uri, projection, pendingSelection, null, sortOrder)?.use { cursor ->
                    val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    while (cursor.moveToNext()) {
                        if (dataCol >= 0) {
                            val path = cursor.getString(dataCol)
                            if (!path.isNullOrEmpty() && allNormalizedEnabledPaths.any { path.startsWith(it) }) {
                                val file = File(path)
                                if (file.exists() && file.isFile && file.length() > 0 && file.extension.lowercase() in supportedExts) {
                                    results.add(file)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Deduplicate
        val seen = mutableSetOf<String>()
        return results.filter { file ->
            val canonical = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
            seen.add(canonical)
        }
    }

    companion object {
        lateinit var instance: LumenApplication
            private set
    }
}
