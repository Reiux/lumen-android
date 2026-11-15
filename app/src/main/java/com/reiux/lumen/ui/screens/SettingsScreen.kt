package com.reiux.lumen.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.reiux.lumen.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reiux.lumen.data.model.BackupSettings
import com.reiux.lumen.ui.components.DynamicLumenIcon
import com.reiux.lumen.ui.components.ExpressiveButton
import com.reiux.lumen.ui.components.ExpressiveSwitch
import com.reiux.lumen.ui.components.ExpressiveTonalButton
import com.reiux.lumen.ui.components.OemBackgroundGuideDialog
import com.reiux.lumen.ui.components.rememberHapticClick
import com.reiux.lumen.ui.theme.ColorPreset
import com.reiux.lumen.ui.theme.getAvailablePresets
import com.reiux.lumen.util.DeviceConditions

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: BackupSettings,
    onUpdateWifiOnly: (Boolean) -> Unit,
    onUpdateChargingOnly: (Boolean) -> Unit,
    onUpdateStorageSaver: (Boolean) -> Unit,
    onUpdateUseQuota: (Boolean) -> Unit,
    onUpdateUploadThreads: (Int) -> Unit,
    onUpdateColorPreset: (String) -> Unit,
    onUpdateNotifications: (Boolean) -> Unit,
    isUploading: Boolean = false,
    isMockMode: Boolean = false,
    isShowcaseMode: Boolean = false,
    onToggleMockMode: () -> Unit = {},
    onToggleShowcaseMode: () -> Unit = {},
    onUpdateAutoBackup: (Boolean) -> Unit = {},
    onUpdateAutoCheckUpdates: (Boolean) -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    onOpenDevDiagnostics: () -> Unit = {},
    onOpenAlbumManager: () -> Unit = {},
    onToggleDeveloperMode: (Boolean) -> Unit = {},
    onUpdateForcedSeason: (String) -> Unit = {},
    onUpdateDeleteAfterBackup: (Boolean) -> Unit = {},
    onUpdateEnableBlurEffects: (Boolean) -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var showOemGuideDialog by remember { mutableStateOf(false) }
    var showAccountSafetyDialog by remember { mutableStateOf(false) }
    var showUploadActiveBlockedDialog by remember { mutableStateOf<String?>(null) }
    var showConfirmDeleteAfterBackupDialog by remember { mutableStateOf(false) }
    var showAllFilesPermissionDialog by remember { mutableStateOf(false) }
    var pendingDeleteAfterBackup by remember { mutableStateOf(false) }
    var returningFromAllFilesPermission by remember { mutableStateOf(false) }
    var isBatteryOptimizedExempt by remember {
        mutableStateOf(DeviceConditions.isIgnoringBatteryOptimizations(context))
    }

    if (showConfirmDeleteAfterBackupDialog) {
        com.reiux.lumen.ui.components.PredictiveAlertDialog(
            onDismissRequest = { showConfirmDeleteAfterBackupDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .widthIn(max = 560.dp)
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.dialog_delete_after_backup_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_delete_after_backup_lead),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.dialog_delete_after_backup_bullets),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = stringResource(R.string.dialog_delete_after_backup_confirm_question),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                ExpressiveButton(
                    onClick = {
                        showConfirmDeleteAfterBackupDialog = false
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
                            onUpdateDeleteAfterBackup(true)
                        } else {
                            // Don't arm the destructive flag yet: the permission screen opens
                            // next and the user may decline. The flag is only applied in the
                            // ON_RESUME handler once the grant is verified.
                            pendingDeleteAfterBackup = true
                            showAllFilesPermissionDialog = true
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.dialog_delete_after_backup_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteAfterBackupDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showAllFilesPermissionDialog) {
        com.reiux.lumen.ui.components.AllFilesPermissionDialog(
            onDismiss = {
                // The dialog fires onConfirm() and then onDismiss() on confirm, so only
                // treat this as a cancel when we didn't just confirm.
                showAllFilesPermissionDialog = false
                if (!returningFromAllFilesPermission) {
                    // User declined the permission flow — leave the destructive flag off.
                    pendingDeleteAfterBackup = false
                }
            },
            onConfirm = {
                // The system permission screen opens right after this; keep the pending
                // flag alive so ON_RESUME can apply it once the grant is verified.
                returningFromAllFilesPermission = true
                showAllFilesPermissionDialog = false
            }
        )
    }

    if (showAccountSafetyDialog) {
        com.reiux.lumen.ui.components.AccountSafetyDialog(
            onDismiss = { showAccountSafetyDialog = false }
        )
    }

    if (showUploadActiveBlockedDialog != null) {
        com.reiux.lumen.ui.components.PredictiveAlertDialog(
            onDismissRequest = { showUploadActiveBlockedDialog = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.backup_upload_in_progress_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_upload_in_progress_msg, showUploadActiveBlockedDialog ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                com.reiux.lumen.ui.components.ExpressiveButton(
                    onClick = { showUploadActiveBlockedDialog = null }
                ) {
                    Text(stringResource(R.string.btn_got_it))
                }
            }
        )
    }

    val handleStorageSaverToggle: (Boolean) -> Unit = { newValue ->
        if (isUploading) {
            showUploadActiveBlockedDialog = context.getString(R.string.pref_storage_saver_title)
            Toast.makeText(context, context.getString(R.string.toast_cannot_change_quality_uploading), Toast.LENGTH_SHORT).show()
        } else {
            onUpdateStorageSaver(newValue)
        }
    }

    val handleUseQuotaToggle: (Boolean) -> Unit = { newValue ->
        if (isUploading) {
            showUploadActiveBlockedDialog = context.getString(R.string.pref_use_quota_title)
            Toast.makeText(context, context.getString(R.string.toast_cannot_change_quota_uploading), Toast.LENGTH_SHORT).show()
        } else {
            onUpdateUseQuota(newValue)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryOptimizedExempt = DeviceConditions.isIgnoringBatteryOptimizations(context)

                // Returned from the All-Files permission screen: apply the destructive flag
                // only if the grant actually went through, otherwise back off and tell the
                // user — never leave a silently-armed delete setting.
                if (pendingDeleteAfterBackup) {
                    pendingDeleteAfterBackup = false
                    returningFromAllFilesPermission = false
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
                        onUpdateDeleteAfterBackup(true)
                    } else {
                        Toast.makeText(context, R.string.toast_all_files_permission_denied, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    val activeSeason = remember(settings.forcedSeason) {
        com.reiux.lumen.util.EasterEggsManager.resolveActiveSeason(settings.forcedSeason)
    }

    val presetsToDisplay = remember(
        settings.unlockedPrismTheme,
        settings.unlockedMatrixTheme,
        settings.unlockedSpookyTheme,
        settings.unlockedXmasTheme,
        settings.unlockedNewYrTheme,
        activeSeason
    ) {
        getAvailablePresets(
            settings.unlockedPrismTheme,
            settings.unlockedMatrixTheme,
            settings.unlockedSpookyTheme,
            settings.unlockedXmasTheme,
            settings.unlockedNewYrTheme,
            activeSeason
        )
    }

    if (isWideLayout) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Pane: Theme Accent Palette & Developer Tools
            val leftScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxHeight()
                    .verticalScroll(leftScrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(R.string.settings_theme_palette),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_accent_palette),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_accent_palette_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            presetsToDisplay.forEach { preset ->
                                val isSelected = settings.colorPreset.equals(preset.name, ignoreCase = true)
                                PalettePresetCard(
                                    preset = preset,
                                    isSelected = isSelected,
                                    onClick = { onUpdateColorPreset(preset.name) }
                                )
                            }
                        }
                    }
                }

                if (settings.developerModeUnlocked) {
                    Text(
                        text = stringResource(R.string.settings_dev_tools),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (com.reiux.lumen.BuildConfig.DEBUG) {
                            SettingSwitchRow(
                                title = stringResource(R.string.settings_blur_dialogs_title),
                                subtitle = stringResource(R.string.settings_blur_dialogs_desc),
                                checked = settings.enableBlurEffects,
                                onCheckedChange = onUpdateEnableBlurEffects
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            SettingSwitchRow(
                                title = stringResource(R.string.settings_showcase_mode_title),
                                subtitle = stringResource(R.string.settings_showcase_mode_desc),
                                checked = isShowcaseMode,
                                onCheckedChange = { onToggleShowcaseMode() }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            SettingSwitchRow(
                                title = stringResource(R.string.settings_mock_sim_title),
                                subtitle = stringResource(R.string.settings_mock_sim_desc),
                                checked = isMockMode,
                                onCheckedChange = { onToggleMockMode() }
                            )
                        }
                        SettingActionRow(
                            title = stringResource(R.string.settings_run_diagnostics_title),
                            subtitle = stringResource(R.string.settings_run_diagnostics_desc),
                            actionLabel = stringResource(R.string.settings_action_run),
                            onClick = onOpenDevDiagnostics
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingActionRow(
                            title = stringResource(R.string.settings_replay_walkthrough_title),
                            subtitle = stringResource(R.string.settings_replay_walkthrough_desc),
                            actionLabel = stringResource(R.string.settings_action_replay),
                            onClick = onReplayOnboarding
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        if (com.reiux.lumen.BuildConfig.DEBUG) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_force_season_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.settings_force_season_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val seasons = listOf("NONE", "HALLOWEEN", "CHRISTMAS", "NEW_YEAR")
                                    seasons.forEach { s ->
                                        val isSel = settings.forcedSeason.equals(s, ignoreCase = true)
                                        ExpressiveTonalButton(
                                            onClick = { onUpdateForcedSeason(s) },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = when (s) {
                                                    "NONE" -> "None"
                                                    "HALLOWEEN" -> "Halloween"
                                                    "CHRISTMAS" -> "Christmas"
                                                    "NEW_YEAR" -> "New Year"
                                                    else -> s
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                            SettingActionRow(
                                title = stringResource(R.string.settings_disable_dev_mode_title),
                                subtitle = stringResource(R.string.settings_disable_dev_mode_desc),
                                actionLabel = stringResource(R.string.settings_action_lock),
                                onClick = { onToggleDeveloperMode(false) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Right Pane: Network & Concurrency, Storage & Quality, About
            val rightScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(0.52f)
                    .fillMaxHeight()
                    .verticalScroll(rightScrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Network & Upload Speed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                NetworkAndSpeedCard(
                    settings = settings,
                    isBatteryOptimizedExempt = isBatteryOptimizedExempt,
                    context = context,
                    onUpdateAutoBackup = onUpdateAutoBackup,
                    onUpdateWifiOnly = onUpdateWifiOnly,
                    onUpdateChargingOnly = onUpdateChargingOnly,
                    onUpdateUploadThreads = onUpdateUploadThreads,
                    onShowOemGuide = { showOemGuideDialog = true }
                )

                Text(
                    text = stringResource(R.string.settings_storage_quality_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingSwitchRow(
                            title = stringResource(R.string.pref_storage_saver_title),
                            subtitle = if (settings.storageSaverQuality) stringResource(R.string.pref_storage_saver_sub_compressed) else stringResource(R.string.pref_storage_saver_sub_original),
                            checked = settings.storageSaverQuality,
                            onCheckedChange = handleStorageSaverToggle
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.pref_use_quota_title),
                            subtitle = stringResource(R.string.pref_use_quota_desc),
                            checked = settings.useQuota,
                            onCheckedChange = handleUseQuotaToggle
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.pref_upload_notifs_title),
                            subtitle = stringResource(R.string.pref_upload_notifs_desc),
                            checked = settings.notificationsEnabled,
                            onCheckedChange = onUpdateNotifications
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingActionRow(
                            title = stringResource(R.string.account_safety_title),
                            subtitle = stringResource(R.string.account_safety_subtitle),
                            actionLabel = stringResource(R.string.settings_action_view),
                            onClick = { showAccountSafetyDialog = true }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.settings_storage_cleanup_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingSwitchRow(
                            title = stringResource(R.string.pref_delete_after_backup_title),
                            subtitle = stringResource(R.string.pref_delete_after_backup_desc),
                            checked = settings.deleteAfterBackup,
                            onCheckedChange = { next ->
                                if (next) {
                                    showConfirmDeleteAfterBackupDialog = true
                                } else {
                                    onUpdateDeleteAfterBackup(false)
                                }
                            }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.settings_updates_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingSwitchRow(
                            title = stringResource(R.string.pref_auto_check_updates_title),
                            subtitle = stringResource(R.string.pref_auto_check_updates_subtitle),
                            checked = settings.autoCheckUpdates,
                            onCheckedChange = onUpdateAutoCheckUpdates
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.settings_about_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val appVersionName = remember(context) {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0)?.versionName ?: "1.0.0"
                    } catch (_: Exception) {
                        "1.0.0"
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onNavigateToAbout() },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DynamicLumenIcon(size = 38.dp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_about_lumen),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "v$appVersionName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.settings_about_lumen),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    } else {
        // Single Column Layout
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Appearance & Palette Style Presets
            item {
                Text(
                    text = stringResource(R.string.settings_theme_palette),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_accent_palette),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_accent_palette_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            presetsToDisplay.forEach { preset ->
                                val isSelected = settings.colorPreset.equals(preset.name, ignoreCase = true)
                                PalettePresetCard(
                                    preset = preset,
                                    isSelected = isSelected,
                                    onClick = { onUpdateColorPreset(preset.name) }
                                )
                            }
                        }
                    }
                }
            }

            // Network & Threads
            item {
                Text(
                    text = stringResource(R.string.settings_network_speed_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                NetworkAndSpeedCard(
                    settings = settings,
                    isBatteryOptimizedExempt = isBatteryOptimizedExempt,
                    context = context,
                    onUpdateAutoBackup = onUpdateAutoBackup,
                    onUpdateWifiOnly = onUpdateWifiOnly,
                    onUpdateChargingOnly = onUpdateChargingOnly,
                    onUpdateUploadThreads = onUpdateUploadThreads,
                    onShowOemGuide = { showOemGuideDialog = true }
                )
            }

            // Upload Quality & Quota
            item {
                Text(
                    text = stringResource(R.string.settings_storage_quality_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingSwitchRow(
                            title = stringResource(R.string.pref_storage_saver_title),
                            subtitle = if (settings.storageSaverQuality) stringResource(R.string.pref_storage_saver_sub_compressed) else stringResource(R.string.pref_storage_saver_sub_original),
                            checked = settings.storageSaverQuality,
                            onCheckedChange = handleStorageSaverToggle
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.pref_use_quota_title),
                            subtitle = stringResource(R.string.pref_use_quota_desc),
                            checked = settings.useQuota,
                            onCheckedChange = handleUseQuotaToggle
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.pref_upload_notifs_title),
                            subtitle = stringResource(R.string.pref_upload_notifs_desc),
                            checked = settings.notificationsEnabled,
                            onCheckedChange = onUpdateNotifications
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingActionRow(
                            title = stringResource(R.string.account_safety_title),
                            subtitle = stringResource(R.string.account_safety_subtitle),
                            actionLabel = stringResource(R.string.settings_action_view),
                            onClick = { showAccountSafetyDialog = true }
                        )
                    }
                }
            }

            // Storage Cleanup (Red section)
            item {
                Text(
                    text = stringResource(R.string.settings_storage_cleanup_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingSwitchRow(
                            title = stringResource(R.string.pref_delete_after_backup_title),
                            subtitle = stringResource(R.string.pref_delete_after_backup_desc),
                            checked = settings.deleteAfterBackup,
                            onCheckedChange = { next ->
                                if (next) {
                                    showConfirmDeleteAfterBackupDialog = true
                                } else {
                                    onUpdateDeleteAfterBackup(false)
                                }
                            }
                        )
                    }
                }
            }

            // Developer & Testing Tools (only shown when developer mode is unlocked)
            if (settings.developerModeUnlocked) {
                item {
                    Text(
                        text = stringResource(R.string.settings_dev_tools),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (com.reiux.lumen.BuildConfig.DEBUG) {
                                SettingSwitchRow(
                                    title = stringResource(R.string.settings_blur_dialogs_title),
                                    subtitle = stringResource(R.string.settings_blur_dialogs_desc),
                                    checked = settings.enableBlurEffects,
                                    onCheckedChange = onUpdateEnableBlurEffects
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                SettingSwitchRow(
                                    title = stringResource(R.string.settings_showcase_mode_title),
                                    subtitle = stringResource(R.string.settings_showcase_mode_desc),
                                    checked = isShowcaseMode,
                                    onCheckedChange = { onToggleShowcaseMode() }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                SettingSwitchRow(
                                    title = stringResource(R.string.settings_mock_sim_title),
                                    subtitle = stringResource(R.string.settings_mock_sim_desc),
                                    checked = isMockMode,
                                    onCheckedChange = { onToggleMockMode() }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            SettingActionRow(
                                title = stringResource(R.string.settings_run_diagnostics_title),
                                subtitle = stringResource(R.string.settings_run_diagnostics_desc),
                                actionLabel = stringResource(R.string.settings_action_run),
                                onClick = onOpenDevDiagnostics
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            SettingActionRow(
                                title = stringResource(R.string.settings_replay_walkthrough_title),
                                subtitle = stringResource(R.string.settings_replay_walkthrough_desc),
                                actionLabel = stringResource(R.string.settings_action_replay),
                                onClick = onReplayOnboarding
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            if (com.reiux.lumen.BuildConfig.DEBUG) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Text(
                                        text = stringResource(R.string.settings_force_season_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_force_season_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val seasons = listOf("NONE", "HALLOWEEN", "CHRISTMAS", "NEW_YEAR")
                                        seasons.forEach { s ->
                                            val isSel = settings.forcedSeason.equals(s, ignoreCase = true)
                                            ExpressiveTonalButton(
                                                onClick = { onUpdateForcedSeason(s) },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = when (s) {
                                                        "NONE" -> "None"
                                                        "HALLOWEEN" -> "Halloween"
                                                        "CHRISTMAS" -> "Christmas"
                                                        "NEW_YEAR" -> "New Year"
                                                        else -> s
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            SettingActionRow(
                                title = stringResource(R.string.settings_disable_dev_mode_title),
                                subtitle = stringResource(R.string.settings_disable_dev_mode_desc),
                                actionLabel = stringResource(R.string.settings_action_lock),
                                onClick = { onToggleDeveloperMode(false) }
                            )
                        }
                    }
                }
            }

            // Updates
            item {
                Text(
                    text = stringResource(R.string.settings_updates_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingSwitchRow(
                            title = stringResource(R.string.pref_auto_check_updates_title),
                            subtitle = stringResource(R.string.pref_auto_check_updates_subtitle),
                            checked = settings.autoCheckUpdates,
                            onCheckedChange = onUpdateAutoCheckUpdates
                        )
                    }
                }
            }

            // About
            item {
                Text(
                    text = stringResource(R.string.settings_about_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                val appVersionName = remember(context) {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0)?.versionName ?: "1.0.0"
                    } catch (_: Exception) {
                        "1.0.0"
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onNavigateToAbout() },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DynamicLumenIcon(size = 38.dp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_about_lumen),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "v$appVersionName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.settings_about_lumen),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showOemGuideDialog) {
        OemBackgroundGuideDialog(onDismiss = { showOemGuideDialog = false })
    }
}

@Composable
fun PalettePresetCard(
    preset: ColorPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hapticClick = rememberHapticClick(onClick = onClick)
    val shape = RoundedCornerShape(16.dp)

    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = hapticClick)
            .border(
                width = 1.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = shape
            ),
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Three overlapping mini swatches
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .background(preset.previewPrimary)
                )
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .background(preset.previewSecondary)
                )
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .background(preset.previewTertiary)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = preset.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        ExpressiveSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        ExpressiveTonalButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun NetworkAndSpeedCard(
    settings: BackupSettings,
    isBatteryOptimizedExempt: Boolean,
    context: Context,
    onUpdateAutoBackup: (Boolean) -> Unit,
    onUpdateWifiOnly: (Boolean) -> Unit,
    onUpdateChargingOnly: (Boolean) -> Unit,
    onUpdateUploadThreads: (Int) -> Unit,
    onShowOemGuide: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Back up new photos and videos
            SettingSwitchRow(
                title = stringResource(R.string.pref_auto_backup_title),
                subtitle = stringResource(R.string.pref_auto_backup_desc),
                checked = settings.autoBackupEnabled,
                onCheckedChange = onUpdateAutoBackup
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 2. Wi-Fi only
            SettingSwitchRow(
                title = stringResource(R.string.pref_wifi_only_title),
                subtitle = stringResource(R.string.pref_wifi_only_desc),
                checked = settings.wifiOnly,
                onCheckedChange = onUpdateWifiOnly
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 3. While charging only
            SettingSwitchRow(
                title = stringResource(R.string.pref_charging_only_title),
                subtitle = stringResource(R.string.pref_charging_only_desc),
                checked = settings.chargingOnly,
                onCheckedChange = onUpdateChargingOnly
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 4. Simultaneous Uploads
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.pref_upload_threads_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.pref_upload_threads_count, settings.uploadThreads),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = stringResource(R.string.pref_upload_threads_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = settings.uploadThreads.toFloat(),
                    onValueChange = { onUpdateUploadThreads(it.toInt()) },
                    valueRange = 1f..20f,
                    steps = 18
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 5. Allow Background Activity
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.pref_background_activity_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.pref_background_activity_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isBatteryOptimizedExempt) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.status_allowed),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    ExpressiveTonalButton(
                        onClick = {
                            DeviceConditions.requestIgnoreBatteryOptimizations(context)
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.btn_allow))
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 6. Background Guide for Your Phone
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onShowOemGuide() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.pref_oem_guide_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.pref_oem_guide_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ExpressiveTonalButton(
                    onClick = onShowOemGuide,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_guide))
                }
            }
        }
    }
}
