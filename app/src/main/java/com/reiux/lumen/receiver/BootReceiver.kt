package com.reiux.lumen.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reiux.lumen.LumenApplication
import com.reiux.lumen.service.MediaContentJobService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action in ALLOWED_ACTIONS) {
            val app = context.applicationContext as? LumenApplication ?: return
            if (app.settingsRepository.settings.value.autoBackupEnabled) {
                MediaContentJobService.scheduleMediaWatcher(context)
                app.checkAndEnqueueRecentMedia()
            }
        }
    }

    companion object {
        private val ALLOWED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON"
        )
    }
}
