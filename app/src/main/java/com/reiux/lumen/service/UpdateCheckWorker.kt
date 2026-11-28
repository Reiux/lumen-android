package com.reiux.lumen.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reiux.lumen.MainActivity
import com.reiux.lumen.R
import com.reiux.lumen.data.repository.UpdateRepository
import com.reiux.lumen.util.LumenLogger

class UpdateCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settingsRepo = com.reiux.lumen.LumenApplication.instance.settingsRepository
        if (!settingsRepo.settings.value.autoCheckUpdates) {
            LumenLogger.d("Updater") { "Auto-update checks are disabled in settings. Skipping background check." }
            return Result.success()
        }

        LumenLogger.d("Updater") { "Starting background update check..." }
        val repo = UpdateRepository()

        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0)?.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }

        val updateInfo = repo.checkForUpdates(currentVersion)
        if (updateInfo == null) {
            // Network/API failure: let WorkManager retry with backoff instead of
            // waiting a whole period before the next attempt.
            return if (runAttemptCount < MAX_RETRIES) {
                LumenLogger.w("Updater") { "Background update check failed (attempt ${runAttemptCount + 1}). Will retry." }
                Result.retry()
            } else {
                LumenLogger.w("Updater") { "Background update check failed (attempt ${runAttemptCount + 1}). Max retries reached; waiting for next scheduled run." }
                Result.success()
            }
        }

        settingsRepo.setLastUpdateCheckMillis(System.currentTimeMillis())

        if (updateInfo.isUpdateAvailable) {
            settingsRepo.savePendingUpdateInfo(updateInfo)
            val lastNotified = settingsRepo.getLastNotifiedUpdateVersion()
            if (!settingsRepo.settings.value.notificationsEnabled) {
                LumenLogger.d("Updater") { "Update v${updateInfo.versionName} available, but notifications are disabled. Skipping notification." }
            } else if (UpdateRepository.shouldNotifyForVersion(updateInfo.versionName, lastNotified)) {
                LumenLogger.i("Updater") { "Update available! v${updateInfo.versionName}. Showing notification." }
                val internalToken = settingsRepo.getOrCreateInternalLaunchToken()
                if (showUpdateNotification(updateInfo.versionName, internalToken)) {
                    settingsRepo.setLastNotifiedUpdateVersion(updateInfo.versionName)
                }
            } else {
                LumenLogger.d("Updater") { "Update v${updateInfo.versionName} already notified. Skipping." }
            }
        } else {
            LumenLogger.d("Updater") { "No update available." }
            settingsRepo.clearPendingUpdateInfo()
            cancelUpdateNotification(context)
        }

        return Result.success()
    }

    private fun showUpdateNotification(newVersion: String, internalToken: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            LumenLogger.w("Updater") { "POST_NOTIFICATIONS permission not granted; skipping update notification." }
            return false
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            LumenLogger.w("Updater") { "System notifications are disabled for Lumen; skipping update notification." }
            return false
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        val channelId = "lumen_updates_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
            if (nm.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE) {
                LumenLogger.w("Updater") { "Update notification channel is blocked; skipping update notification." }
                return false
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "show_update")
            putExtra("internal_token", internalToken)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            123,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Lumen Update Available")
            .setContentText("Version $newVersion is available. Tap to update.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            nm.notify(UPDATE_NOTIFICATION_ID, notif)
            true
        } catch (e: SecurityException) {
            LumenLogger.w("Updater") { "POST_NOTIFICATIONS denied; skipping update notification: ${e.message}" }
            false
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        const val UPDATE_NOTIFICATION_ID = 9002

        fun cancelUpdateNotification(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(UPDATE_NOTIFICATION_ID)
            } catch (_: Exception) {}
        }
    }
}
