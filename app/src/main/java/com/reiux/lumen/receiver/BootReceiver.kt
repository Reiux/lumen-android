package com.reiux.lumen.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reiux.lumen.LumenApplication
import com.reiux.lumen.service.MediaContentJobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action in ALLOWED_ACTIONS) {
            val app = context.applicationContext as? LumenApplication ?: return
            if (app.settingsRepository.settings.value.autoBackupEnabled) {
                MediaContentJobService.ensureMediaWatcherScheduled(app)
                val pendingResult = goAsync()
                val isBootCompleted = action == Intent.ACTION_BOOT_COMPLETED
                val scanJob = app.checkAndEnqueueRecentMedia(fromBoot = isBootCompleted)
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        withTimeoutOrNull(8_000L) {
                            scanJob.join()
                            // Allow LumenApplication's isUploading collector (150ms debounce) time
                            // to enqueue BackgroundUploadWorker before releasing the broadcast lock.
                            delay(250L)
                        }
                    } finally {
                        try {
                            pendingResult.finish()
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    companion object {
        private val ALLOWED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
