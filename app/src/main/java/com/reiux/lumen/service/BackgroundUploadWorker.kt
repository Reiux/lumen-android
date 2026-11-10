package com.reiux.lumen.service

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reiux.lumen.LumenApplication
import com.reiux.lumen.data.model.UploadStatus
import com.reiux.lumen.util.LumenLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Android 12+ (API 31+) blocks direct startForegroundService() calls when the app is woken in the
 * background by MediaContentJobService or BootReceiver unless battery optimizations are disabled.
 * When that happens, UploadForegroundService falls back to enqueueing this expedited Worker, which
 * keeps the background upload process alive and drives the live progress notification.
 */
class BackgroundUploadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        UploadForegroundService.ensureNotificationChannel(appContext)
        val notif = UploadForegroundService.createInitialNotification(appContext)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                UploadForegroundService.NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(UploadForegroundService.NOTIFICATION_ID, notif)
        }
    }

    override suspend fun doWork(): Result {
        val app = appContext.applicationContext as? LumenApplication ?: return Result.success()
        val uploadRepo = app.uploadRepository
        val settingsRepo = app.settingsRepository

        if (!uploadRepo.isUploading.value) {
            return Result.success()
        }

        LumenLogger.i("Service") { "BackgroundUploadWorker started to keep background upload alive (Android 12+ fallback)" }
        UploadForegroundService.ensureNotificationChannel(appContext)

        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            LumenLogger.d("Service") { "BackgroundUploadWorker setForeground skipped: ${e.message}" }
        }

        var lastNotificationTime = 0L
        val notifyJob = kotlinx.coroutines.CoroutineScope( kotlin.coroutines.coroutineContext).launch {
            combine(
                combine(uploadRepo.isUploading, uploadRepo.currentUploadingItem, uploadRepo.queue) { u, c, q -> Triple(u, c, q) },
                combine(uploadRepo.conditionNotice, settingsRepo.settings, uploadRepo.uploadSpeed) { cn, s, sp -> Triple(cn, s, sp) }
            ) { (isUploading, currentItem, queue), (conditionNotice, settings, uploadSpeed) ->
                Triple(
                    Triple(isUploading, currentItem, queue),
                    conditionNotice,
                    Pair(settings.notificationsEnabled, uploadSpeed)
                )
            }.collectLatest { (uploadTriple, conditionNotice, settingsPair) ->
                val (isUploading, currentItem, queue) = uploadTriple
                val (notificationsEnabled, uploadSpeed) = settingsPair
                if (isUploading && notificationsEnabled) {
                    val now = System.currentTimeMillis()
                    val isByteTransfer = currentItem?.status == UploadStatus.UPLOADING
                    if (now - lastNotificationTime >= 250L || !isByteTransfer) {
                        lastNotificationTime = now
                        val notif = UploadForegroundService.createProgressNotification(
                            context = appContext,
                            isUploading = isUploading,
                            currentItem = currentItem,
                            queue = queue,
                            conditionNotice = conditionNotice,
                            uploadSpeed = uploadSpeed
                        )
                        val nm = appContext.getSystemService(android.app.NotificationManager::class.java)
                        nm?.notify(UploadForegroundService.NOTIFICATION_ID, notif)
                    }
                }
            }
        }

        try {
            while (uploadRepo.isUploading.value) {
                delay(500L)
            }
        } finally {
            notifyJob.cancel()
            UploadForegroundService.clearNotification(appContext)
        }

        LumenLogger.i("Service") { "BackgroundUploadWorker finished" }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "LumenBackgroundUploadKeepAlive"

        fun enqueue(context: Context) {
            try {
                val request = OneTimeWorkRequestBuilder<BackgroundUploadWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )
            } catch (e: Exception) {
                LumenLogger.w("Service") { "Failed to enqueue BackgroundUploadWorker: ${e.message}" }
            }
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            } catch (_: Exception) {}
        }
    }
}
