package com.reiux.lumen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.reiux.lumen.LumenApplication
import com.reiux.lumen.MainActivity
import com.reiux.lumen.data.model.UploadItem
import com.reiux.lumen.data.model.UploadStatus
import com.reiux.lumen.util.LumenLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class UploadForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var lastNotificationTime = 0L
    private var lastIsPaused: Boolean? = null
    private var notificationDismissed = false
    private var lastStartId: Int = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        notificationDismissed = false
        LumenLogger.d("Service") { "UploadForegroundService onCreate" }
        ensureNotificationChannel(this)
        promoteToForeground(buildCurrentOrInitialNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        isRunning = true
        notificationDismissed = false
        LumenLogger.d("Service") { "UploadForegroundService onStartCommand flags=$flags startId=$startId action=${intent?.action}" }
        ensureNotificationChannel(this)
        // Always call startForeground() to satisfy Android O+'s per-startForegroundService() token
        // contract, while preserving live progress instead of resetting to "Initializing...".
        promoteToForeground(buildCurrentOrInitialNotification())

        val app = applicationContext as? LumenApplication
        when (intent?.action) {
            ACTION_PAUSE_BACKUP -> {
                LumenLogger.i("Service") { "Handling notification action: ACTION_PAUSE_BACKUP" }
                app?.uploadRepository?.pauseQueue()
            }
            ACTION_RESUME_BACKUP -> {
                LumenLogger.i("Service") { "Handling notification action: ACTION_RESUME_BACKUP" }
                if (app != null) {
                    val account = app.accountRepository.selectedAccount.value
                        ?: app.accountRepository.accounts.value.firstOrNull()
                    if (account != null) {
                        val settings = app.settingsRepository.settings.value
                        app.uploadRepository.resumeQueue(
                            account = account,
                            storageSaver = settings.storageSaverQuality,
                            useQuota = settings.useQuota
                        )
                    } else {
                        LumenLogger.w("Service") { "ACTION_RESUME_BACKUP ignored: no active account" }
                    }
                }
            }
            ACTION_CANCEL_BACKUP -> {
                LumenLogger.i("Service") { "Handling notification action: ACTION_CANCEL_BACKUP" }
                app?.uploadRepository?.cancelQueue()
                stopForegroundAndDismiss(reason = "action_cancel")
                return START_NOT_STICKY
            }
        }

        startObserving()
        return START_NOT_STICKY
    }

    private fun buildCurrentOrInitialNotification(): Notification {
        val app = applicationContext as? LumenApplication ?: return createInitialNotification(this)
        val uploadRepo = app.uploadRepository
        val settingsRepo = app.settingsRepository
        return if (uploadRepo.isUploading.value && settingsRepo.settings.value.notificationsEnabled) {
            createProgressNotification(
                context = this,
                isUploading = true,
                currentItem = uploadRepo.currentUploadingItem.value,
                queue = uploadRepo.queue.value,
                conditionNotice = uploadRepo.conditionNotice.value,
                uploadSpeed = uploadRepo.uploadSpeed.value,
                isPaused = uploadRepo.isPaused.value
            )
        } else {
            createInitialNotification(this)
        }
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
        LumenLogger.d("Service") { "UploadForegroundService onDestroy" }
        observeJob?.cancel()
        scope.cancel()
        if (!notificationDismissed) {
            dismissNotificationInternal("onDestroy")
        }
    }

    /**
     * Android 15+ limits dataSync foreground services to ~6h per 24h. When the limit is hit the
     * system calls this, and if the service doesn't stop within a few seconds the app is crashed.
     * Pause the active upload queue cleanly so transfers aren't left running un-foregrounded.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        LumenLogger.w("Service") { "Foreground service time limit reached (fgsType=$fgsType). Pausing queue and stopping service." }
        (applicationContext as? LumenApplication)?.uploadRepository?.pauseQueue()
        stopForegroundAndDismiss(reason = "fgs_timeout")
    }

    private fun startObserving() {
        if (observeJob != null && observeJob?.isActive == true) return

        val app = applicationContext as? LumenApplication
        if (app == null) {
            scope.launch { stopForegroundAndDismiss(reason = "no_app_context") }
            return
        }
        val uploadRepo = app.uploadRepository
        val settingsRepo = app.settingsRepository

        // Defer stopForegroundAndDismiss into `scope.launch` (Main looper post) so that
        // onStartCommand() always finishes in the foreground state first, satisfying Android's
        // startForegroundService() contract before stopForeground()/stopSelf() is called.
        observeJob = scope.launch {
            combine(
                combine(
                    uploadRepo.isUploading,
                    uploadRepo.isPaused,
                    uploadRepo.currentUploadingItem,
                    uploadRepo.queue
                ) { u, p, c, q -> QueueSnapshot(u, p, c, q) },
                combine(
                    uploadRepo.conditionNotice,
                    settingsRepo.settings,
                    uploadRepo.uploadSpeed
                ) { cn, s, sp -> ConfigSnapshot(cn, s.notificationsEnabled, sp) }
            ) { queueSnap, configSnap ->
                NotificationState(
                    isUploading = queueSnap.isUploading,
                    isPaused = queueSnap.isPaused,
                    currentItem = queueSnap.currentItem,
                    queue = queueSnap.queue,
                    conditionNotice = configSnap.conditionNotice,
                    notificationsEnabled = configSnap.notificationsEnabled,
                    uploadSpeed = configSnap.uploadSpeed
                )
            }.collectLatest { state ->
                if (!state.isUploading) {
                    lastIsPaused = null
                    stopForegroundAndDismiss(reason = "uploads_completed")
                } else if (!state.notificationsEnabled) {
                    lastIsPaused = null
                    stopForegroundAndDismiss(reason = "notifications_disabled")
                } else {
                    val now = System.currentTimeMillis()
                    val isByteTransfer = !state.isPaused && state.currentItem?.status == UploadStatus.UPLOADING
                    val pauseStateChanged = state.isPaused != lastIsPaused
                    // Throttle notification updates during rapid byte streaming to avoid OS throttling
                    if (pauseStateChanged || now - lastNotificationTime >= 250L || !isByteTransfer) {
                        lastNotificationTime = now
                        lastIsPaused = state.isPaused
                        val notification = createProgressNotification(
                            context = this@UploadForegroundService,
                            isUploading = state.isUploading,
                            currentItem = state.currentItem,
                            queue = state.queue,
                            conditionNotice = state.conditionNotice,
                            uploadSpeed = state.uploadSpeed,
                            isPaused = state.isPaused
                        )
                        try {
                            val manager = getSystemService(NotificationManager::class.java)
                            manager?.notify(NOTIFICATION_ID, notification)
                        } catch (_: SecurityException) {}
                    }
                }
            }
        }
    }

    private fun dismissNotificationInternal(reason: String) {
        notificationDismissed = true
        LumenLogger.d("Service") { "UploadForegroundService dismissNotificationInternal reason='$reason'" }
        val manager = getSystemService(NotificationManager::class.java)
        try {
            // Post a non-ongoing notification update first so Android 14/15/16 System UI releases the status bar pill
            val clearNotif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.reiux.lumen.R.drawable.ic_launcher_monochrome)
                .setOngoing(false)
                .setSilent(true)
                .build()
            manager?.notify(NOTIFICATION_ID, clearNotif)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        manager?.cancel(NOTIFICATION_ID)
    }

    private fun stopForegroundAndDismiss(reason: String = "completed") {
        isRunning = false
        LumenLogger.d("Service") { "UploadForegroundService stopping foreground and dismissing notification reason='$reason'" }
        observeJob?.cancel()
        observeJob = null
        dismissNotificationInternal(reason)
        if (lastStartId != -1) {
            stopSelfResult(lastStartId)
        } else {
            stopSelf()
        }
    }

    private data class QueueSnapshot(
        val isUploading: Boolean,
        val isPaused: Boolean,
        val currentItem: UploadItem?,
        val queue: List<UploadItem>
    )

    private data class ConfigSnapshot(
        val conditionNotice: String?,
        val notificationsEnabled: Boolean,
        val uploadSpeed: String?
    )

    private data class NotificationState(
        val isUploading: Boolean,
        val isPaused: Boolean,
        val currentItem: UploadItem?,
        val queue: List<UploadItem>,
        val conditionNotice: String?,
        val notificationsEnabled: Boolean,
        val uploadSpeed: String? = null
    )

    companion object {
        const val CHANNEL_ID = "lumen_backup_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE_BACKUP = "com.reiux.lumen.action.PAUSE_BACKUP"
        const val ACTION_RESUME_BACKUP = "com.reiux.lumen.action.RESUME_BACKUP"
        const val ACTION_CANCEL_BACKUP = "com.reiux.lumen.action.CANCEL_BACKUP"
        private const val REQUEST_CODE_OPEN = 0
        private const val REQUEST_CODE_PAUSE = 101
        private const val REQUEST_CODE_CANCEL = 102
        private const val REQUEST_CODE_RESUME = 103

        @Volatile
        var isRunning: Boolean = false
            private set

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(com.reiux.lumen.R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(com.reiux.lumen.R.string.notif_channel_desc)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }
        }

        private fun createOpenAppPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private fun createServiceActionPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, UploadForegroundService::class.java).apply {
                this.action = action
            }
            return PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun createInitialNotification(context: Context): Notification {
            val pendingIntent = createOpenAppPendingIntent(context)

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(com.reiux.lumen.R.string.notif_backup_title))
                .setContentText(context.getString(com.reiux.lumen.R.string.notif_init))
                .setSmallIcon(com.reiux.lumen.R.drawable.ic_launcher_monochrome)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build()
        }

        fun createProgressNotification(
            context: Context,
            isUploading: Boolean,
            currentItem: UploadItem?,
            queue: List<UploadItem>,
            conditionNotice: String?,
            uploadSpeed: String? = null,
            isPaused: Boolean = false
        ): Notification {
            val totalInQueue = queue.size
            val processedInQueue = queue.count {
                it.status == UploadStatus.COMPLETED ||
                it.status == UploadStatus.SKIPPED ||
                it.status == UploadStatus.FAILED
            }

            val batchPercentByCount = if (totalInQueue > 0) {
                ((processedInQueue.toDouble() / totalInQueue.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            } else 0

            val currentFileSize = currentItem?.fileSize ?: 0L
            val currentFileUploaded = currentItem?.bytesUploaded ?: 0L
            val filePercent = if (currentFileSize > 0) {
                ((currentFileUploaded.toDouble() / currentFileSize.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            } else 0

            val fileIndex = (processedInQueue + 1).coerceAtMost(totalInQueue.coerceAtLeast(1))
            val currentName = currentItem?.fileName ?: "media"

            val title: String
            val text: String
            val subText: String?
            val progressMax = 100
            val progressCurrent: Int
            var progressIndeterminate = false

            if (isPaused) {
                title = context.getString(com.reiux.lumen.R.string.backup_paused_title)
                val remaining = (totalInQueue - processedInQueue).coerceAtLeast(0)
                text = if (conditionNotice != null) {
                    context.getString(com.reiux.lumen.R.string.notif_paused, conditionNotice)
                } else {
                    context.getString(com.reiux.lumen.R.string.backup_paused_subtitle, remaining)
                }
                subText = if (totalInQueue > 0) "$fileIndex/$totalInQueue" else null
                progressCurrent = if (totalInQueue <= 1 && currentItem?.status == UploadStatus.UPLOADING) {
                    filePercent
                } else {
                    batchPercentByCount
                }
                progressIndeterminate = false
            } else if (conditionNotice != null) {
                title = context.getString(com.reiux.lumen.R.string.notif_backup_title)
                text = context.getString(com.reiux.lumen.R.string.notif_paused, conditionNotice)
                subText = if (totalInQueue > 0) "$fileIndex/$totalInQueue" else null
                progressCurrent = batchPercentByCount
            } else {
                title = context.getString(com.reiux.lumen.R.string.notif_uploading, currentName)

                val countPrefix = if (totalInQueue > 1) "$fileIndex/$totalInQueue" else ""
                val speedSuffix = if (!uploadSpeed.isNullOrEmpty()) " • $uploadSpeed" else ""
                subText = if (countPrefix.isNotEmpty() && speedSuffix.isNotEmpty()) {
                    "$countPrefix$speedSuffix"
                } else if (countPrefix.isNotEmpty()) {
                    countPrefix
                } else if (!uploadSpeed.isNullOrEmpty()) {
                    uploadSpeed
                } else null

                when (currentItem?.status) {
                    UploadStatus.HASHING -> {
                        text = context.getString(com.reiux.lumen.R.string.notif_checking_file)
                        progressCurrent = batchPercentByCount
                        progressIndeterminate = true
                    }
                    UploadStatus.CHECKING -> {
                        text = context.getString(com.reiux.lumen.R.string.notif_checking_duplicates)
                        progressCurrent = batchPercentByCount
                        progressIndeterminate = true
                    }
                    UploadStatus.UPLOADING -> {
                        val uploadedStr = formatBytesStatic(currentFileUploaded)
                        val totalStr = formatBytesStatic(currentFileSize)
                        text = context.getString(com.reiux.lumen.R.string.notif_upload_progress, uploadedStr, totalStr, filePercent)
                        progressCurrent = if (totalInQueue > 1) batchPercentByCount else filePercent
                        progressIndeterminate = false
                    }
                    UploadStatus.COMMITTING -> {
                        text = context.getString(com.reiux.lumen.R.string.notif_saving)
                        progressCurrent = batchPercentByCount
                        progressIndeterminate = false
                    }
                    else -> {
                        text = context.getString(com.reiux.lumen.R.string.notif_preparing)
                        progressCurrent = batchPercentByCount
                        progressIndeterminate = totalInQueue == 0
                    }
                }
            }

            val openIntent = createOpenAppPendingIntent(context)
            val publicText = if (isPaused) {
                context.getString(com.reiux.lumen.R.string.backup_paused_title)
            } else if (conditionNotice != null) {
                context.getString(com.reiux.lumen.R.string.notif_paused, conditionNotice)
            } else {
                context.getString(com.reiux.lumen.R.string.notif_preparing)
            }

            val livePillText = if (isPaused || conditionNotice != null) {
                context.getString(com.reiux.lumen.R.string.notif_paused_chip)
            } else if (isUploading && queue.size == 1 && filePercent >= 0) {
                "$filePercent%"
            } else if (isUploading && batchPercentByCount >= 0) {
                "$batchPercentByCount%"
            } else {
                context.getString(com.reiux.lumen.R.string.notif_backup_chip)
            }

            val primaryActionIntent = if (isPaused) {
                createServiceActionPendingIntent(context, ACTION_RESUME_BACKUP, REQUEST_CODE_RESUME)
            } else if (isUploading || conditionNotice != null) {
                createServiceActionPendingIntent(context, ACTION_PAUSE_BACKUP, REQUEST_CODE_PAUSE)
            } else null
            val cancelPendingIntent = if (isPaused || isUploading || conditionNotice != null) {
                createServiceActionPendingIntent(context, ACTION_CANCEL_BACKUP, REQUEST_CODE_CANCEL)
            } else null

            if (Build.VERSION.SDK_INT >= 36) {
                return createApi36ProgressNotification(
                    context = context,
                    title = title,
                    text = text,
                    subText = subText,
                    publicText = publicText,
                    chipText = livePillText,
                    isUploading = isUploading,
                    progressMax = progressMax,
                    progressCurrent = progressCurrent,
                    progressIndeterminate = progressIndeterminate,
                    openIntent = openIntent,
                    primaryActionIntent = primaryActionIntent,
                    cancelPendingIntent = cancelPendingIntent,
                    isPaused = isPaused
                )
            }

            // Redacted lock-screen notification without private filenames
            val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(com.reiux.lumen.R.string.notif_backup_title))
                .setContentText(publicText)
                .setSmallIcon(com.reiux.lumen.R.drawable.ic_launcher_monochrome)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(com.reiux.lumen.R.drawable.ic_launcher_monochrome)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicNotification)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openIntent)

            if (!subText.isNullOrEmpty()) {
                builder.setSubText(subText)
            }

            if (isUploading) {
                builder.setProgress(progressMax, progressCurrent, if (isPaused) false else progressIndeterminate)
            }

            if (primaryActionIntent != null) {
                val actionIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
                val actionLabel = context.getString(
                    if (isPaused) com.reiux.lumen.R.string.btn_resume_backup else com.reiux.lumen.R.string.notif_action_pause
                )
                builder.addAction(
                    actionIcon,
                    actionLabel,
                    primaryActionIntent
                )
            }
            if (cancelPendingIntent != null) {
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(com.reiux.lumen.R.string.notif_action_cancel),
                    cancelPendingIntent
                )
            }

            return builder.build()
        }

        @RequiresApi(36)
        private fun createApi36ProgressNotification(
            context: Context,
            title: String,
            text: String,
            subText: String?,
            publicText: String,
            chipText: String,
            isUploading: Boolean,
            progressMax: Int,
            progressCurrent: Int,
            progressIndeterminate: Boolean,
            openIntent: PendingIntent,
            primaryActionIntent: PendingIntent?,
            cancelPendingIntent: PendingIntent?,
            isPaused: Boolean = false
        ): Notification {
            val publicNotification = Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(com.reiux.lumen.R.string.notif_backup_title))
                .setContentText(publicText)
                .setSmallIcon(com.reiux.lumen.R.drawable.ic_launcher_monochrome)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build()

            val builder = Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(com.reiux.lumen.R.drawable.ic_launcher_monochrome)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicNotification)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openIntent)
                .setShortCriticalText(chipText)
                .addExtras(Bundle().apply {
                    putBoolean("android.requestPromotedOngoing", true)
                })

            if (Build.VERSION.SDK_INT >= 37) {
                builder.setRequestPromotedOngoing(true)
            } else {
                try {
                    builder.javaClass
                        .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                        .invoke(builder, true)
                } catch (_: Exception) {}
            }

            if (!subText.isNullOrEmpty()) {
                builder.setSubText(subText)
            }

            if (isUploading) {
                builder.setProgress(progressMax, progressCurrent, if (isPaused) false else progressIndeterminate)
            }

            if (primaryActionIntent != null) {
                val actionIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
                val actionLabel = context.getString(
                    if (isPaused) com.reiux.lumen.R.string.btn_resume_backup else com.reiux.lumen.R.string.notif_action_pause
                )
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, actionIcon),
                        actionLabel,
                        primaryActionIntent
                    ).build()
                )
            }
            if (cancelPendingIntent != null) {
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                        context.getString(com.reiux.lumen.R.string.notif_action_cancel),
                        cancelPendingIntent
                    ).build()
                )
            }

            return builder.build()
        }

        private fun formatBytesStatic(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
        }

        fun clearNotification(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            try {
                val clearNotif = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(com.reiux.lumen.R.drawable.ic_launcher_monochrome)
                    .setOngoing(false)
                    .setSilent(true)
                    .build()
                manager?.notify(NOTIFICATION_ID, clearNotif)
            } catch (_: Exception) {}
            manager?.cancel(NOTIFICATION_ID)
        }

        fun startOrUpdate(context: Context) {
            val intent = Intent(context, UploadForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                LumenLogger.w("Service") { "Direct foreground service start blocked in background (${e.javaClass.simpleName}). Falling back to expedited BackgroundUploadWorker." }
                BackgroundUploadWorker.enqueue(context)
            }
        }

        fun stop(context: Context) {
            BackgroundUploadWorker.cancel(context)
        }
    }
}
