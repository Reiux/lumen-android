package com.reiux.lumen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LumenLogger.d("Service") { "UploadForegroundService onCreate" }
        ensureNotificationChannel(this)
        promoteToForeground(createInitialNotification(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LumenLogger.d("Service") { "UploadForegroundService onStartCommand flags=$flags startId=$startId" }
        ensureNotificationChannel(this)
        promoteToForeground(createInitialNotification(this))
        startObserving()
        return START_NOT_STICKY
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
        super.onDestroy()
        LumenLogger.d("Service") { "UploadForegroundService onDestroy" }
        observeJob?.cancel()
        scope.cancel()
        dismissNotificationInternal("onDestroy")
    }

    /**
     * Android 15+ limits dataSync foreground services to ~6h per 24h. When the limit is hit the
     * system calls this, and if the service doesn't stop within a few seconds the app is crashed.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        LumenLogger.w("Service") { "Foreground service time limit reached (fgsType=$fgsType). Stopping service." }
        stopForegroundAndDismiss(reason = "fgs_timeout")
    }

    private fun buildInitialNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lumen Backup")
            .setContentText("Initializing upload service...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
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
                combine(uploadRepo.isUploading, uploadRepo.currentUploadingItem, uploadRepo.queue) { u, c, q -> Triple(u, c, q) },
                combine(uploadRepo.conditionNotice, settingsRepo.settings, uploadRepo.uploadSpeed) { cn, s, sp -> Triple(cn, s, sp) }
            ) { (isUploading, currentItem, queue), (conditionNotice, settings, uploadSpeed) ->
                NotificationState(
                    isUploading = isUploading,
                    currentItem = currentItem,
                    queue = queue,
                    conditionNotice = conditionNotice,
                    notificationsEnabled = settings.notificationsEnabled,
                    uploadSpeed = uploadSpeed
                )
            }.collectLatest { state ->
                if (!state.isUploading) {
                    stopForegroundAndDismiss(reason = "uploads_completed")
                } else if (!state.notificationsEnabled) {
                    stopForegroundAndDismiss(reason = "notifications_disabled")
                } else {
                    val now = System.currentTimeMillis()
                    val isByteTransfer = state.currentItem?.status == UploadStatus.UPLOADING
                    // Throttle notification updates during rapid byte streaming to avoid OS throttling
                    if (now - lastNotificationTime >= 250L || !isByteTransfer) {
                        lastNotificationTime = now
                        val notification = createProgressNotification(
                            context = this@UploadForegroundService,
                            isUploading = state.isUploading,
                            currentItem = state.currentItem,
                            queue = state.queue,
                            conditionNotice = state.conditionNotice,
                            uploadSpeed = state.uploadSpeed
                        )
                        val manager = getSystemService(NotificationManager::class.java)
                        manager?.notify(NOTIFICATION_ID, notification)
                    }
                }
            }
        }
    }

    private fun dismissNotificationInternal(reason: String) {
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
        LumenLogger.d("Service") { "UploadForegroundService stopping foreground and dismissing notification reason='$reason'" }
        observeJob?.cancel()
        dismissNotificationInternal(reason)
        stopSelf()
    }

    private data class NotificationState(
        val isUploading: Boolean,
        val currentItem: UploadItem?,
        val queue: List<UploadItem>,
        val conditionNotice: String?,
        val notificationsEnabled: Boolean,
        val uploadSpeed: String? = null
    )

    private fun buildNotification(
        isUploading: Boolean,
        currentItem: UploadItem?,
        queue: List<UploadItem>,
        conditionNotice: String?,
        uploadSpeed: String? = null
    ): Notification {
        val totalInQueue = queue.size
        val processedInQueue = queue.count {
            it.status == UploadStatus.COMPLETED ||
            it.status == UploadStatus.SKIPPED ||
            it.status == UploadStatus.FAILED
        }

        // Calculate batch progress strictly based on processed file count vs total file count
        val batchPercentByCount = if (totalInQueue > 0) {
            ((processedInQueue.toDouble() / totalInQueue.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } else 0

        // Calculate current file progress
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
        val chipText: String
        val progressMax = 100
        val progressCurrent: Int
        var progressIndeterminate = false

        if (conditionNotice != null) {
            title = "Lumen Backup"
            text = "Paused: $conditionNotice"
            subText = if (totalInQueue > 0) "$fileIndex/$totalInQueue" else null
            chipText = if (totalInQueue > 0) "$fileIndex/$totalInQueue • Paused" else "Paused"
            progressCurrent = batchPercentByCount
        } else {
            title = "Uploading $currentName"
            chipText = if (totalInQueue > 1) "$fileIndex/$totalInQueue • $batchPercentByCount%" else "$filePercent%"

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
                    text = "Checking file..."
                    progressCurrent = batchPercentByCount
                    progressIndeterminate = true
                }
                UploadStatus.CHECKING -> {
                    text = "Checking Google Photos for duplicates..."
                    progressCurrent = batchPercentByCount
                    progressIndeterminate = true
                }
                UploadStatus.UPLOADING -> {
                    val uploadedStr = formatBytes(currentFileUploaded)
                    val totalStr = formatBytes(currentFileSize)
                    text = "$uploadedStr of $totalStr • $filePercent%"
                    progressCurrent = if (totalInQueue > 1) batchPercentByCount else filePercent
                    progressIndeterminate = false
                }
                UploadStatus.COMMITTING -> {
                    text = "Saving to Google Photos..."
                    progressCurrent = batchPercentByCount
                    progressIndeterminate = false
                }
                else -> {
                    text = "Preparing..."
                    progressCurrent = batchPercentByCount
                    progressIndeterminate = totalInQueue == 0
                }
            }
        }

        LumenLogger.v("Service") {
            "Notification updated: title='$title' text='$text' progress=$progressCurrent/$progressMax indeterminate=$progressIndeterminate"
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(com.reiux.lumen.R.drawable.ic_launcher_monochrome)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent)

        val livePillText = if (conditionNotice != null) {
            "Paused"
        } else {
            if (isUploading && batchPercentByCount >= 0) "$batchPercentByCount%" else "Backup"
        }

        applyLiveUpdatePromotions(builder, livePillText)

        if (!subText.isNullOrEmpty()) {
            builder.setSubText(subText)
        }

        if (isUploading) {
            builder.setProgress(progressMax, progressCurrent, progressIndeterminate)
        }

        return builder.build()
    }

    private fun applyLiveUpdatePromotions(builder: NotificationCompat.Builder, chipText: String) {
        val extras = android.os.Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            putString("android.shortCriticalText", chipText)
        }
        builder.addExtras(extras)

        try {
            val methodPromote = builder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
            methodPromote.invoke(builder, true)
        } catch (_: Exception) {}

        try {
            val methodChip = builder.javaClass.getMethod("setShortCriticalText", CharSequence::class.java)
            methodChip.invoke(builder, chipText)
        } catch (_: Exception) {}
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lumen Upload Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live upload progress for photos and videos"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "lumen_backup_channel"
        const val NOTIFICATION_ID = 1001

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Lumen Upload Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows live upload progress for photos and videos"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }
        }

        fun createInitialNotification(context: Context): Notification {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Lumen Backup")
                .setContentText("Initializing upload service...")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build()
        }

        fun createProgressNotification(
            context: Context,
            isUploading: Boolean,
            currentItem: UploadItem?,
            queue: List<UploadItem>,
            conditionNotice: String?,
            uploadSpeed: String? = null
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

            if (conditionNotice != null) {
                title = "Lumen Backup"
                text = "Paused: $conditionNotice"
                subText = if (totalInQueue > 0) "$fileIndex/$totalInQueue" else null
                progressCurrent = batchPercentByCount
            } else {
                title = "Uploading $currentName"

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
                        text = "Checking file..."
                        progressCurrent = batchPercentByCount
                        progressIndeterminate = true
                    }
                    UploadStatus.CHECKING -> {
                        text = "Checking Google Photos for duplicates..."
                        progressCurrent = batchPercentByCount
                        progressIndeterminate = true
                    }
                    UploadStatus.UPLOADING -> {
                        val uploadedStr = formatBytesStatic(currentFileUploaded)
                        val totalStr = formatBytesStatic(currentFileSize)
                        text = "$uploadedStr of $totalStr • $filePercent%"
                        progressCurrent = if (totalInQueue > 1) batchPercentByCount else filePercent
                        progressIndeterminate = false
                    }
                    UploadStatus.COMMITTING -> {
                        text = "Saving to Google Photos..."
                        progressCurrent = batchPercentByCount
                        progressIndeterminate = false
                    }
                    else -> {
                        text = "Preparing..."
                        progressCurrent = batchPercentByCount
                        progressIndeterminate = totalInQueue == 0
                    }
                }
            }

            val openIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(com.reiux.lumen.R.drawable.ic_launcher_monochrome)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openIntent)

            val livePillText = if (conditionNotice != null) {
                "Paused"
            } else {
                if (isUploading && batchPercentByCount >= 0) "$batchPercentByCount%" else "Backup"
            }

            applyLiveUpdatePromotionsStatic(builder, livePillText)

            if (!subText.isNullOrEmpty()) {
                builder.setSubText(subText)
            }

            if (isUploading) {
                builder.setProgress(progressMax, progressCurrent, progressIndeterminate)
            }

            return builder.build()
        }

        private fun applyLiveUpdatePromotionsStatic(builder: NotificationCompat.Builder, chipText: String) {
            val extras = android.os.Bundle().apply {
                putBoolean("android.requestPromotedOngoing", true)
                putString("android.shortCriticalText", chipText)
            }
            builder.addExtras(extras)

            try {
                val methodPromote = builder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                methodPromote.invoke(builder, true)
            } catch (_: Exception) {}

            try {
                val methodChip = builder.javaClass.getMethod("setShortCriticalText", CharSequence::class.java)
                methodChip.invoke(builder, chipText)
            } catch (_: Exception) {}
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
