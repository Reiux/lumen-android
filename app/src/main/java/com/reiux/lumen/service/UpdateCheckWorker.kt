package com.reiux.lumen.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
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
        LumenLogger.d("Updater") { "Starting background update check..." }
        val repo = UpdateRepository()
        
        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0)?.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }

        val updateInfo = repo.checkForUpdates(currentVersion)
        if (updateInfo != null && updateInfo.isUpdateAvailable) {
            LumenLogger.i("Updater") { "Update available! v${updateInfo.versionName}. Showing notification." }
            showUpdateNotification(updateInfo.versionName)
        } else {
            LumenLogger.d("Updater") { "No update available." }
        }

        return Result.success()
    }

    private fun showUpdateNotification(newVersion: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "lumen_updates_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("action", "show_update")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            123,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Lumen Update Available")
            .setContentText("Version $newVersion is available. Tap to update.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(9002, notif)
    }
}
