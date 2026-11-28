package com.reiux.lumen.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.reiux.lumen.LumenApplication
import com.reiux.lumen.MainActivity
import com.reiux.lumen.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class LumenSyncTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
        val app = applicationContext as? LumenApplication ?: return
        listenJob?.cancel()
        listenJob = scope.launch {
            combine(
                app.uploadRepository.isUploading,
                app.uploadRepository.isPaused,
                app.accountRepository.selectedAccount,
                app.accountRepository.accounts
            ) { _, _, _, _ -> }.collect {
                updateTileState()
            }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext as? LumenApplication ?: return

        val isUploading = app.uploadRepository.isUploading.value
        val isPaused = app.uploadRepository.isPaused.value

        if (isUploading && isPaused) {
            val account = app.accountRepository.selectedAccount.value
                ?: app.accountRepository.accounts.value.firstOrNull()
            if (account != null) {
                val settings = app.settingsRepository.settings.value
                app.uploadRepository.resumeQueue(
                    account = account,
                    storageSaver = settings.storageSaverQuality,
                    useQuota = settings.useQuota
                )
            }
            updateTileState()
            return
        }

        if (isUploading) {
            app.uploadRepository.pauseQueue()
            updateTileState()
            return
        }

        val hasAccount = app.accountRepository.selectedAccount.value != null ||
            app.accountRepository.accounts.value.isNotEmpty()
        if (!hasAccount) {
            updateTileState()
            return
        }

        app.startedFromBootBroadcast = false
        val internalToken = app.settingsRepository.getOrCreateInternalLaunchToken()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "shortcut_sync")
            putExtra("internal_token", internalToken)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                REQUEST_CODE_TILE_SYNC,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapseLegacy(intent)
        }
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseLegacy(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    override fun onDestroy() {
        listenJob?.cancel()
        listenJob = null
        scope.cancel()
        super.onDestroy()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val app = applicationContext as? LumenApplication ?: return

        val isUploading = app.uploadRepository.isUploading.value
        val isPaused = app.uploadRepository.isPaused.value
        val hasAccount = app.accountRepository.selectedAccount.value != null ||
            app.accountRepository.accounts.value.isNotEmpty()

        tile.label = getString(R.string.qs_tile_sync_label)
        val stateLabel: String
        when {
            isUploading && isPaused -> {
                tile.state = Tile.STATE_INACTIVE
                stateLabel = getString(R.string.backup_paused_title)
            }
            isUploading -> {
                tile.state = Tile.STATE_ACTIVE
                stateLabel = getString(R.string.qs_tile_syncing)
            }
            !hasAccount -> {
                tile.state = Tile.STATE_UNAVAILABLE
                stateLabel = getString(R.string.qs_tile_no_account)
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                stateLabel = getString(R.string.qs_tile_idle)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = stateLabel
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = stateLabel
        }
        tile.updateTile()
    }

    companion object {
        private const val REQUEST_CODE_TILE_SYNC = 201
    }
}
