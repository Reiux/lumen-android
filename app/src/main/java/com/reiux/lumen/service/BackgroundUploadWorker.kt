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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Android 12+ (API 31+) blocks direct startForegroundService() calls when the app is woken in the
 * background by MediaContentJobService or BootReceiver unless battery optimizations are disabled,
 * and Android 15+ blocks dataSync FGS starts from BOOT_COMPLETED altogether.
 * When that happens, Lumen falls back to enqueueing this expedited Worker, which
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

        LumenLogger.i("Service") { "BackgroundUploadWorker started to keep background upload alive" }
        UploadForegroundService.ensureNotificationChannel(appContext)

        var postedNotification = false
        var hasForegroundLease = false
        if (settingsRepo.settings.value.notificationsEnabled &&
            !(Build.VERSION.SDK_INT >= 35 && app.startedFromBootBroadcast)
        ) {
            try {
                setForeground(getForegroundInfo())
                postedNotification = true
                hasForegroundLease = true
            } catch (e: Exception) {
                LumenLogger.d("Service") { "BackgroundUploadWorker setForeground skipped: ${e.message}" }
            }
        }

        val startTimeMs = System.currentTimeMillis()
        var lastNotificationTime = 0L
        var lastStructuralKey: String? = null
        var lastContentKey: String? = null
        val parentJob = kotlin.coroutines.coroutineContext[Job]
        val notifyScope = CoroutineScope(kotlin.coroutines.coroutineContext + SupervisorJob(parentJob))
        val notifyJob = notifyScope.launch {
            try {
                combine(
                    combine(
                        uploadRepo.isUploading,
                        uploadRepo.isPaused,
                        uploadRepo.currentUploadingItem,
                        uploadRepo.queue
                    ) { u, p, c, q ->
                        Pair(Pair(u, p), Pair(c, q))
                    },
                    combine(uploadRepo.conditionNotice, settingsRepo.settings, uploadRepo.uploadSpeed) { cn, s, sp ->
                        Triple(cn, s, sp)
                    }
                ) { (statusPair, itemQueuePair), (conditionNotice, settings, uploadSpeed) ->
                    Triple(
                        Pair(statusPair, itemQueuePair),
                        conditionNotice,
                        Pair(settings.notificationsEnabled, uploadSpeed)
                    )
                }.collectLatest { (uploadState, conditionNotice, settingsPair) ->
                    val (statusPair, itemQueuePair) = uploadState
                    val (isUploading, isPaused) = statusPair
                    val (currentItem, queue) = itemQueuePair
                    val (notificationsEnabled, uploadSpeed) = settingsPair
                    if (isUploading && notificationsEnabled && !UploadForegroundService.isRunning) {
                        val processedCount = queue.count {
                            it.status == UploadStatus.COMPLETED ||
                                it.status == UploadStatus.SKIPPED ||
                                it.status == UploadStatus.FAILED
                        }
                        val structuralKey = "${currentItem?.id}|${currentItem?.status}|$conditionNotice|$isPaused|${queue.size}|$processedCount"
                        val progressPercent = if (queue.size > 1) {
                            ((processedCount.toDouble() / queue.size.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                        } else {
                            val totalBytes = currentItem?.fileSize ?: 0L
                            val uploadedBytes = currentItem?.bytesUploaded ?: 0L
                            if (totalBytes > 0L) ((uploadedBytes.toDouble() / totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100) else 0
                        }
                        val contentKey = "$structuralKey|$progressPercent|${uploadSpeed ?: ""}"

                        val now = System.currentTimeMillis()
                        val structuralChanged = structuralKey != lastStructuralKey
                        val contentChanged = contentKey != lastContentKey
                        if (contentChanged && (structuralChanged || now - lastNotificationTime >= NOTIF_THROTTLE_MS)) {
                            lastNotificationTime = now
                            lastStructuralKey = structuralKey
                            lastContentKey = contentKey
                            try {
                                val notif = UploadForegroundService.createProgressNotification(
                                    context = appContext,
                                    isUploading = isUploading,
                                    currentItem = currentItem,
                                    queue = queue,
                                    conditionNotice = conditionNotice,
                                    uploadSpeed = uploadSpeed,
                                    isPaused = isPaused
                                )
                                val nm = appContext.getSystemService(android.app.NotificationManager::class.java)
                                nm?.notify(UploadForegroundService.NOTIFICATION_ID, notif)
                                postedNotification = true
                            } catch (e: Exception) {
                                LumenLogger.d("Service") { "BackgroundUploadWorker notification update skipped: ${e.message}" }
                            }
                        }
                    } else if (!notificationsEnabled && postedNotification && !UploadForegroundService.isRunning) {
                        UploadForegroundService.clearNotification(appContext)
                        postedNotification = false
                        lastStructuralKey = null
                        lastContentKey = null
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                LumenLogger.w("Service") { "BackgroundUploadWorker notify observer error: ${e.message}" }
            }
        }

        var shouldRetry = false
        try {
            while (uploadRepo.isUploading.value) {
                if (isStopped) {
                    shouldRetry = true
                    break
                }
                if (!hasForegroundLease && System.currentTimeMillis() - startTimeMs >= NON_FGS_MAX_RUN_MS) {
                    LumenLogger.i("Service") { "BackgroundUploadWorker approaching JobScheduler execution limit; yielding for retry" }
                    shouldRetry = true
                    break
                }
                delay(500L)
            }
        } catch (ce: CancellationException) {
            notifyJob.cancel()
            if (uploadRepo.isUploading.value) {
                LumenLogger.w("Service") { "BackgroundUploadWorker cancelled while upload is active; requesting retry" }
                return Result.retry()
            }
            throw ce
        } finally {
            notifyJob.cancel()
            if (postedNotification && !UploadForegroundService.isRunning && !uploadRepo.isUploading.value) {
                UploadForegroundService.clearNotification(appContext)
            }
        }

        if (shouldRetry || uploadRepo.isUploading.value) {
            LumenLogger.i("Service") { "BackgroundUploadWorker returning Result.retry() to continue active upload batch" }
            return Result.retry()
        }

        LumenLogger.i("Service") { "BackgroundUploadWorker finished" }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "LumenBackgroundUploadKeepAlive"
        private const val NOTIF_THROTTLE_MS = 1500L
        private const val NON_FGS_MAX_RUN_MS = 9 * 60 * 1000L

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
