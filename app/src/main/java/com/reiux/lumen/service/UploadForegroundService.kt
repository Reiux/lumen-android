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
        createNotificationChannel()
        startObserving()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LumenLogger.d("Service") { "UploadForegroundService onStartCommand flags=$flags startId=$startId" }
        startObserving()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        LumenLogger.d("Service") { "UploadForegroundService onDestroy" }
        observeJob?.cancel()
        scope.cancel()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        manager?.cancel(NOTIFICATION_ID)
    }

    private fun startObserving() {
        if (observeJob != null && observeJob?.isActive == true) return

        val app = applicationContext as? LumenApplication ?: return
        val uploadRepo = app.uploadRepository
        val settingsRepo = app.settingsRepository

        // If not uploading or notifications disabled, dismiss immediately
        if (!uploadRepo.isUploading.value) {
            stopForegroundAndDismiss(reason = "upload_engine_idle")
            return
        }
        if (!settingsRepo.settings.value.notificationsEnabled) {
            stopForegroundAndDismiss(reason = "notifications_disabled_in_settings")
            return
        }

        // Show immediate foreground notification
        val initialNotification = buildNotification(
            isUploading = true,
            currentItem = uploadRepo.currentUploadingItem.value,
            queue = uploadRepo.queue.value,
            conditionNotice = uploadRepo.conditionNotice.value,
            uploadSpeed = uploadRepo.uploadSpeed.value
        )
        startForeground(NOTIFICATION_ID, initialNotification)
        LumenLogger.d("Service") { "UploadForegroundService started foreground notification" }

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
                        val notification = buildNotification(
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

    private fun stopForegroundAndDismiss(reason: String = "completed") {
        LumenLogger.d("Service") { "UploadForegroundService stopping foreground and dismissing notification reason='$reason'" }
        observeJob?.cancel()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        manager?.cancel(NOTIFICATION_ID)
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
        // Exclude items already in Google Photos (SKIPPED) from notification counters & percentages
        val actualQueue = queue.filter { it.status != UploadStatus.SKIPPED }
        val totalInQueue = actualQueue.size
        val completedInQueue = actualQueue.count { it.status == UploadStatus.COMPLETED }

        // Calculate overall batch bytes progress for actual uploads
        val totalBatchBytes = actualQueue.sumOf { it.fileSize }
        val doneBatchBytes = actualQueue.sumOf { item ->
            when (item.status) {
                UploadStatus.COMPLETED -> item.fileSize
                UploadStatus.UPLOADING, UploadStatus.COMMITTING -> item.bytesUploaded
                else -> 0L
            }
        }
        val batchPercent = if (totalBatchBytes > 0) {
            ((doneBatchBytes.toDouble() / totalBatchBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } else 0

        // Calculate current file progress
        val currentFileSize = currentItem?.fileSize ?: 0L
        val currentFileUploaded = currentItem?.bytesUploaded ?: 0L
        val filePercent = if (currentFileSize > 0) {
            ((currentFileUploaded.toDouble() / currentFileSize.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } else 0

        val fileIndex = (completedInQueue + 1).coerceAtMost(totalInQueue.coerceAtLeast(1))
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
            progressCurrent = batchPercent
        } else {
            title = "Uploading $currentName"
            chipText = if (totalInQueue > 1) "$fileIndex/$totalInQueue • $batchPercent%" else "$filePercent%"

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
                    progressCurrent = batchPercent
                    progressIndeterminate = true
                }
                UploadStatus.CHECKING -> {
                    text = "Checking Google Photos for duplicates..."
                    progressCurrent = batchPercent
                    progressIndeterminate = true
                }
                UploadStatus.UPLOADING -> {
                    val uploadedStr = formatBytes(currentFileUploaded)
                    val totalStr = formatBytes(currentFileSize)
                    text = "$uploadedStr of $totalStr • $filePercent%"
                    progressCurrent = if (totalInQueue > 1) batchPercent else filePercent
                    progressIndeterminate = false
                }
                UploadStatus.COMMITTING -> {
                    text = "Saving to Google Photos..."
                    progressCurrent = batchPercent
                    progressIndeterminate = false
                }
                else -> {
                    text = "Preparing..."
                    progressCurrent = batchPercent
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

        applyLiveUpdatePromotions(builder, chipText)

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

        fun startOrUpdate(context: Context) {
            val intent = Intent(context, UploadForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, UploadForegroundService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
