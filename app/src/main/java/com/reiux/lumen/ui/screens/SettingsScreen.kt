package com.reiux.lumen.ui.screens

import android.content.Context
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reiux.lumen.data.model.BackupSettings
import com.reiux.lumen.ui.components.DynamicLumenIcon
import com.reiux.lumen.ui.components.ExpressiveAlertDialog
import com.reiux.lumen.ui.components.ExpressiveDialogCallout
import com.reiux.lumen.ui.components.ExpressiveDialogTone
import com.reiux.lumen.ui.components.ExpressiveSwitch
import com.reiux.lumen.ui.components.ExpressiveTonalButton
import com.reiux.lumen.ui.components.GroupPosition
import com.reiux.lumen.ui.components.OemBackgroundGuideDialog
import com.reiux.lumen.ui.components.animatedGroupedCardShape
import com.reiux.lumen.ui.components.groupPositionOf
import com.reiux.lumen.ui.components.groupedCardShape
import com.reiux.lumen.ui.components.rememberExpressivePressed
import com.reiux.lumen.ui.components.rememberHapticClick
import com.reiux.lumen.ui.theme.ColorPreset
import com.reiux.lumen.ui.theme.getAvailablePresets
import com.reiux.lumen.util.DeviceConditions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    onUpdateDeleteAfterBackup: (Boolean) -> Unit = {},
    onUpdateEnableBlurEffects: (Boolean) -> Unit = {},
    onUpdateAutoSyncAlbums: (Boolean) -> Unit = {},
    hasUnreadWhatsNew: Boolean = false,
    onNavigateToAbout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var showOemGuideDialog by rememberSaveable { mutableStateOf(false) }
    var showAccountSafetyDialog by rememberSaveable { mutableStateOf(false) }
    var showUploadActiveBlockedDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var showConfirmDeleteAfterBackupDialog by rememberSaveable { mutableStateOf(false) }
    var showAllFilesPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteAfterBackup by rememberSaveable { mutableStateOf(false) }
    var returningFromAllFilesPermission by rememberSaveable { mutableStateOf(false) }
    var isBatteryOptimizedExempt by remember {
        mutableStateOf(DeviceConditions.isIgnoringBatteryOptimizations(context))
    }

    if (showConfirmDeleteAfterBackupDialog) {
        ExpressiveAlertDialog(
            onDismissRequest = { showConfirmDeleteAfterBackupDialog = false },
            icon = Icons.Rounded.DeleteForever,
            title = stringResource(R.string.dialog_delete_after_backup_title),
            tone = ExpressiveDialogTone.ERROR,
            confirmText = stringResource(R.string.dialog_delete_after_backup_confirm),
            onConfirm = {
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
            dismissText = stringResource(R.string.btn_cancel)
        ) {
            ExpressiveDialogCallout(tone = ExpressiveDialogTone.ERROR) {
                Text(
                    text = stringResource(R.string.dialog_delete_after_backup_lead),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.dialog_delete_after_backup_bullets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
            Text(
                text = stringResource(R.string.dialog_delete_after_backup_confirm_question),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (showAllFilesPermissionDialog) {
        com.reiux.lumen.ui.components.AllFilesPermissionDialog(
            onDismiss = {
                // The dialog fires onConfirm() and then onDismiss() on confirm, so only
                // treat this as a cancel when we didn't just confirm.
                showAllFilesPermissionDialog = false
                if (!returningFromAllFilesPermission) {
                    // User declined or settings failed to launch — leave the destructive flag off.
                    if (pendingDeleteAfterBackup) {
                        Toast.makeText(context, R.string.toast_all_files_permission_denied, Toast.LENGTH_SHORT).show()
                    }
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
        ExpressiveAlertDialog(
            onDismissRequest = { showUploadActiveBlockedDialog = null },
            icon = Icons.Rounded.CloudSync,
            title = stringResource(R.string.backup_upload_in_progress_title),
            tone = ExpressiveDialogTone.TERTIARY,
            confirmText = stringResource(R.string.btn_got_it),
            onConfirm = { showUploadActiveBlockedDialog = null }
        ) {
            Text(
                text = stringResource(R.string.settings_upload_in_progress_msg, showUploadActiveBlockedDialog ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideLayout = maxWidth >= 640.dp

        if (isWideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = 1080.dp)
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
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_theme_palette),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = groupedCardShape(GroupPosition.Single),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
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

                        DevToolsGroup(
                            settings = settings,
                            isShowcaseMode = isShowcaseMode,
                            isMockMode = isMockMode,
                            onUpdateEnableBlurEffects = onUpdateEnableBlurEffects,
                            onToggleShowcaseMode = onToggleShowcaseMode,
                            onToggleMockMode = onToggleMockMode,
                            onOpenDevDiagnostics = onOpenDevDiagnostics,
                            onReplayOnboarding = onReplayOnboarding,
                            onToggleDeveloperMode = onToggleDeveloperMode
                        )
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
                        text = stringResource(R.string.settings_network_speed_title),
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

                    StorageAndQualityCard(
                        settings = settings,
                        onStorageSaverToggle = handleStorageSaverToggle,
                        onUseQuotaToggle = handleUseQuotaToggle,
                        onUpdateNotifications = onUpdateNotifications,
                        onUpdateAutoSyncAlbums = onUpdateAutoSyncAlbums,
                        onOpenAccountSafety = { showAccountSafetyDialog = true }
                    )

                    Text(
                        text = stringResource(R.string.settings_storage_cleanup_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )

                    StorageCleanupGroup(
                        deleteAfterBackup = settings.deleteAfterBackup,
                        onCheckedChange = { next ->
                            if (next) {
                                showConfirmDeleteAfterBackupDialog = true
                            } else {
                                onUpdateDeleteAfterBackup(false)
                            }
                        }
                    )

                    Text(
                        text = stringResource(R.string.settings_updates_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    UpdatesGroup(
                        autoCheckUpdates = settings.autoCheckUpdates,
                        onCheckedChange = onUpdateAutoCheckUpdates
                    )

                    Text(
                        text = stringResource(R.string.settings_about_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AboutEntryCard(
                        appVersionName = com.reiux.lumen.BuildConfig.VERSION_NAME,
                        hasUnreadWhatsNew = hasUnreadWhatsNew,
                        onClick = onNavigateToAbout
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            // Single Column Layout
            LazyColumn(
                state = listState,
                modifier = Modifier
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
                        style = MaterialTheme.typography.headlineMediumEmphasized,
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
                        shape = groupedCardShape(GroupPosition.Single),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
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
                    StorageAndQualityCard(
                        settings = settings,
                        onStorageSaverToggle = handleStorageSaverToggle,
                        onUseQuotaToggle = handleUseQuotaToggle,
                        onUpdateNotifications = onUpdateNotifications,
                        onUpdateAutoSyncAlbums = onUpdateAutoSyncAlbums,
                        onOpenAccountSafety = { showAccountSafetyDialog = true }
                    )
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
                    StorageCleanupGroup(
                        deleteAfterBackup = settings.deleteAfterBackup,
                        onCheckedChange = { next ->
                            if (next) {
                                showConfirmDeleteAfterBackupDialog = true
                            } else {
                                onUpdateDeleteAfterBackup(false)
                            }
                        }
                    )
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
                        DevToolsGroup(
                            settings = settings,
                            isShowcaseMode = isShowcaseMode,
                            isMockMode = isMockMode,
                            onUpdateEnableBlurEffects = onUpdateEnableBlurEffects,
                            onToggleShowcaseMode = onToggleShowcaseMode,
                            onToggleMockMode = onToggleMockMode,
                            onOpenDevDiagnostics = onOpenDevDiagnostics,
                            onReplayOnboarding = onReplayOnboarding,
                            onToggleDeveloperMode = onToggleDeveloperMode
                        )
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
                    UpdatesGroup(
                        autoCheckUpdates = settings.autoCheckUpdates,
                        onCheckedChange = onUpdateAutoCheckUpdates
                    )
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
                    AboutEntryCard(
                        appVersionName = com.reiux.lumen.BuildConfig.VERSION_NAME,
                        hasUnreadWhatsNew = hasUnreadWhatsNew,
                        onClick = onNavigateToAbout
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
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
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    val isGesturePressed by rememberExpressivePressed(interactionSource)
    var clickPulse by remember { mutableStateOf(false) }
    val isPressed = isGesturePressed || clickPulse

    val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
        coroutineScope.launch {
            clickPulse = true
            delay(180L)
            clickPulse = false
        }
        onClick()
    }

    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()
    val floatSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val colorEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()

    val cornerRadius by animateDpAsState(
        targetValue = when {
            isPressed -> 10.dp
            isSelected -> 22.dp
            else -> 14.dp
        },
        animationSpec = spatialSpec,
        label = "paletteCardCorner"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = floatSpatialSpec,
        label = "paletteCardScale"
    )
    val swatchScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = floatSpatialSpec,
        label = "paletteSwatchScale"
    )
    val swatchSpacing by animateDpAsState(
        targetValue = if (isSelected) (-4).dp else (-6).dp,
        animationSpec = spatialSpec,
        label = "paletteSwatchSpacing"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = colorEffectsSpec,
        label = "paletteContainerColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        animationSpec = colorEffectsSpec,
        label = "paletteBorderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 1.5.dp else 1.dp,
        animationSpec = spatialSpec,
        label = "paletteBorderWidth"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = colorEffectsSpec,
        label = "paletteLabelColor"
    )
    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = Modifier
            .scale(pressScale)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = hapticClick
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = shape
            ),
        shape = shape,
        color = containerColor,
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Three overlapping mini swatches
            Row(
                modifier = Modifier.scale(swatchScale),
                horizontalArrangement = Arrangement.spacedBy(swatchSpacing)
            ) {
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
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleIn(initialScale = 0.6f, animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()) +
                    expandHorizontally(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
                exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleOut(targetScale = 0.6f, animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()) +
                    shrinkHorizontally(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: GroupPosition = GroupPosition.Single,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    border: BorderStroke? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    val isGesturePressed by rememberExpressivePressed(interactionSource)
    var clickPulse by remember { mutableStateOf(false) }
    val isPressed = isGesturePressed || clickPulse
    val shape = animatedGroupedCardShape(position = position, pressed = isPressed)

    val haptic = LocalHapticFeedback.current
    val toggleWithHaptic: (Boolean) -> Unit = { next ->
        try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
        coroutineScope.launch {
            clickPulse = true
            delay(180L)
            clickPulse = false
        }
        onCheckedChange(next)
    }

    Surface(
        onClick = { toggleWithHaptic(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
        border = border,
        interactionSource = interactionSource
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
                onCheckedChange = toggleWithHaptic
            )
        }
    }
}

@Composable
fun SettingActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    enabled: Boolean = true,
    position: GroupPosition = GroupPosition.Single,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    val isGesturePressed by rememberExpressivePressed(interactionSource)
    var clickPulse by remember { mutableStateOf(false) }
    val isPressed = isGesturePressed || clickPulse
    val shape = animatedGroupedCardShape(position = position, pressed = isPressed)

    val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
        coroutineScope.launch {
            clickPulse = true
            delay(180L)
            clickPulse = false
        }
        onClick()
    }

    Surface(
        onClick = hapticClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        interactionSource = interactionSource
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
                onClick = hapticClick,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun StorageCleanupGroup(
    deleteAfterBackup: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingSwitchRow(
            title = stringResource(R.string.pref_delete_after_backup_title),
            subtitle = stringResource(R.string.pref_delete_after_backup_desc),
            checked = deleteAfterBackup,
            onCheckedChange = onCheckedChange,
            position = GroupPosition.Single,
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
        )
        Text(
            text = stringResource(R.string.settings_delete_after_backup_global_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun UpdatesGroup(
    autoCheckUpdates: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        SettingSwitchRow(
            title = stringResource(R.string.pref_auto_check_updates_title),
            subtitle = stringResource(R.string.pref_auto_check_updates_subtitle),
            checked = autoCheckUpdates,
            onCheckedChange = onCheckedChange,
            position = GroupPosition.Single
        )
    }
}

@Composable
private fun DevToolsGroup(
    settings: BackupSettings,
    isShowcaseMode: Boolean,
    isMockMode: Boolean,
    onUpdateEnableBlurEffects: (Boolean) -> Unit,
    onToggleShowcaseMode: () -> Unit,
    onToggleMockMode: () -> Unit,
    onOpenDevDiagnostics: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onToggleDeveloperMode: (Boolean) -> Unit
) {
    val items = buildList<@Composable (GroupPosition) -> Unit> {
        if (com.reiux.lumen.BuildConfig.DEBUG) {
            add { pos ->
                SettingSwitchRow(
                    title = stringResource(R.string.settings_blur_dialogs_title),
                    subtitle = stringResource(R.string.settings_blur_dialogs_desc),
                    checked = settings.enableBlurEffects,
                    onCheckedChange = onUpdateEnableBlurEffects,
                    position = pos
                )
            }
            add { pos ->
                SettingSwitchRow(
                    title = stringResource(R.string.settings_showcase_mode_title),
                    subtitle = stringResource(R.string.settings_showcase_mode_desc),
                    checked = isShowcaseMode,
                    onCheckedChange = { onToggleShowcaseMode() },
                    position = pos
                )
            }
            add { pos ->
                SettingSwitchRow(
                    title = stringResource(R.string.settings_mock_sim_title),
                    subtitle = stringResource(R.string.settings_mock_sim_desc),
                    checked = isMockMode,
                    onCheckedChange = { onToggleMockMode() },
                    position = pos
                )
            }
        }
        add { pos ->
            SettingActionRow(
                title = stringResource(R.string.settings_run_diagnostics_title),
                subtitle = stringResource(R.string.settings_run_diagnostics_desc),
                actionLabel = stringResource(R.string.settings_action_run),
                position = pos,
                onClick = onOpenDevDiagnostics
            )
        }
        add { pos ->
            SettingActionRow(
                title = stringResource(R.string.settings_replay_walkthrough_title),
                subtitle = stringResource(R.string.settings_replay_walkthrough_desc),
                actionLabel = stringResource(R.string.settings_action_replay),
                position = pos,
                onClick = onReplayOnboarding
            )
        }
        add { pos ->
            SettingActionRow(
                title = stringResource(R.string.settings_disable_dev_mode_title),
                subtitle = stringResource(R.string.settings_disable_dev_mode_desc),
                actionLabel = stringResource(R.string.settings_action_lock),
                position = pos,
                onClick = { onToggleDeveloperMode(false) }
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items.forEachIndexed { index, renderItem ->
            renderItem(groupPositionOf(index, items.size))
        }
    }
}

@Composable
private fun AboutEntryCard(
    appVersionName: String,
    hasUnreadWhatsNew: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    val isGesturePressed by rememberExpressivePressed(interactionSource)
    var clickPulse by remember { mutableStateOf(false) }
    val isPressed = isGesturePressed || clickPulse

    val corner by animateDpAsState(
        targetValue = if (isPressed) 14.dp else 24.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "aboutEntryCardCorner"
    )
    val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
        coroutineScope.launch {
            clickPulse = true
            delay(180L)
            clickPulse = false
        }
        onClick()
    }

    Surface(
        onClick = hapticClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasUnreadWhatsNew) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.settings_about_lumen),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun StorageAndQualityCard(
    settings: BackupSettings,
    onStorageSaverToggle: (Boolean) -> Unit,
    onUseQuotaToggle: (Boolean) -> Unit,
    onUpdateNotifications: (Boolean) -> Unit,
    onUpdateAutoSyncAlbums: (Boolean) -> Unit,
    onOpenAccountSafety: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        SettingSwitchRow(
            title = stringResource(R.string.pref_storage_saver_title),
            subtitle = if (settings.storageSaverQuality) stringResource(R.string.pref_storage_saver_sub_compressed) else stringResource(R.string.pref_storage_saver_sub_original),
            checked = settings.storageSaverQuality,
            onCheckedChange = onStorageSaverToggle,
            position = GroupPosition.Top
        )
        SettingSwitchRow(
            title = stringResource(R.string.pref_use_quota_title),
            subtitle = stringResource(R.string.pref_use_quota_desc),
            checked = settings.useQuota,
            onCheckedChange = onUseQuotaToggle,
            position = GroupPosition.Middle
        )
        SettingSwitchRow(
            title = stringResource(R.string.pref_auto_sync_albums_title),
            subtitle = stringResource(R.string.pref_auto_sync_albums_desc),
            checked = settings.autoSyncAlbums,
            onCheckedChange = onUpdateAutoSyncAlbums,
            position = GroupPosition.Middle
        )
        SettingSwitchRow(
            title = stringResource(R.string.pref_upload_notifs_title),
            subtitle = stringResource(R.string.pref_upload_notifs_desc),
            checked = settings.notificationsEnabled,
            onCheckedChange = onUpdateNotifications,
            position = GroupPosition.Middle
        )
        SettingActionRow(
            title = stringResource(R.string.account_safety_title),
            subtitle = stringResource(R.string.account_safety_subtitle),
            actionLabel = stringResource(R.string.settings_action_view),
            position = GroupPosition.Bottom,
            onClick = onOpenAccountSafety
        )
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
    val sliderHaptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // 1. Back up new photos and videos
        SettingSwitchRow(
            title = stringResource(R.string.pref_auto_backup_title),
            subtitle = stringResource(R.string.pref_auto_backup_desc),
            checked = settings.autoBackupEnabled,
            onCheckedChange = onUpdateAutoBackup,
            position = GroupPosition.Top
        )

        // 2. Wi-Fi only
        SettingSwitchRow(
            title = stringResource(R.string.pref_wifi_only_title),
            subtitle = stringResource(R.string.pref_wifi_only_desc),
            checked = settings.wifiOnly,
            onCheckedChange = onUpdateWifiOnly,
            position = GroupPosition.Middle
        )

        // 3. While charging only
        SettingSwitchRow(
            title = stringResource(R.string.pref_charging_only_title),
            subtitle = stringResource(R.string.pref_charging_only_desc),
            checked = settings.chargingOnly,
            onCheckedChange = onUpdateChargingOnly,
            position = GroupPosition.Middle
        )

        // 4. Simultaneous Uploads
        var sliderThreads by remember(settings.uploadThreads) {
            mutableFloatStateOf(settings.uploadThreads.toFloat())
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = groupedCardShape(GroupPosition.Middle),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
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
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.pref_upload_threads_count, sliderThreads.roundToInt()),
                        style = MaterialTheme.typography.labelLargeEmphasized,
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
                    value = sliderThreads,
                    onValueChange = { next ->
                        val prevStep = sliderThreads.roundToInt()
                        val nextStep = next.roundToInt()
                        sliderThreads = next
                        if (nextStep != prevStep) {
                            try { sliderHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                        }
                    },
                    onValueChangeFinished = { onUpdateUploadThreads(sliderThreads.roundToInt()) },
                    valueRange = 1f..20f,
                    steps = 18
                )
            }
        }

        // 5. Allow Background Activity
        val bgInteractionSource = remember { MutableInteractionSource() }
        val bgGesturePressed by rememberExpressivePressed(bgInteractionSource)
        var bgClickPulse by remember { mutableStateOf(false) }
        val bgShape = animatedGroupedCardShape(
            position = GroupPosition.Middle,
            pressed = !isBatteryOptimizedExempt && (bgGesturePressed || bgClickPulse)
        )
        val requestBackgroundHaptic = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
            coroutineScope.launch {
                bgClickPulse = true
                delay(180L)
                bgClickPulse = false
            }
            DeviceConditions.requestIgnoreBatteryOptimizations(context)
        }
        Surface(
            onClick = requestBackgroundHaptic,
            enabled = !isBatteryOptimizedExempt,
            modifier = Modifier.fillMaxWidth(),
            shape = bgShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            interactionSource = bgInteractionSource
        ) {
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
                        onClick = requestBackgroundHaptic,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.btn_allow))
                    }
                }
            }
        }

        // 6. Background Guide for Your Phone
        val oemInteractionSource = remember { MutableInteractionSource() }
        val oemGesturePressed by rememberExpressivePressed(oemInteractionSource)
        var oemClickPulse by remember { mutableStateOf(false) }
        val oemShape = animatedGroupedCardShape(
            position = GroupPosition.Bottom,
            pressed = oemGesturePressed || oemClickPulse
        )
        val openOemHaptic = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
            coroutineScope.launch {
                oemClickPulse = true
                delay(180L)
                oemClickPulse = false
            }
            onShowOemGuide()
        }
        Surface(
            onClick = openOemHaptic,
            modifier = Modifier.fillMaxWidth(),
            shape = oemShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            interactionSource = oemInteractionSource
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                    onClick = openOemHaptic,
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
