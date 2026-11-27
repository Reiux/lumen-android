package com.reiux.lumen.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.UploadFile
import com.reiux.lumen.util.EasterEggsManager
import com.reiux.lumen.util.Season
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reiux.lumen.R
import com.reiux.lumen.ui.components.DynamicLumenIcon
import com.reiux.lumen.ui.components.ExpressiveAlertDialog
import com.reiux.lumen.ui.components.ExpressiveDialogTone
import com.reiux.lumen.ui.components.ExpressiveIconButton
import com.reiux.lumen.ui.components.ExpressiveLoadingIndicator
import com.reiux.lumen.ui.components.ExpressiveSwitch
import com.reiux.lumen.ui.components.GroupPosition
import com.reiux.lumen.ui.components.LogViewerDialog
import com.reiux.lumen.ui.components.PlayfulParticleBurstOverlay
import com.reiux.lumen.ui.components.animatedGroupedCardShape
import com.reiux.lumen.ui.components.groupedCardShape
import com.reiux.lumen.ui.components.rememberExpressivePressed
import com.reiux.lumen.ui.components.rememberHapticClick
import com.reiux.lumen.util.LumenLogger

@Composable
fun AboutScreen(
    saveLogsEnabled: Boolean,
    onToggleSaveLogs: (Boolean) -> Unit,
    selectedAccount: com.reiux.lumen.data.model.GoogleAccount? = null,
    settings: com.reiux.lumen.data.model.BackupSettings? = null,
    developerModeUnlocked: Boolean = false,
    unlockedPrismTheme: Boolean = false,
    onToggleDeveloperMode: (Boolean) -> Unit = {},
    onUnlockPrismTheme: () -> Unit = {},
    onUnlockMatrixTheme: () -> Unit = {},
    forcedSeason: String = "NONE",
    hasUnreadWhatsNew: Boolean = false,
    onOpenWhatsNew: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onForceUpdateDialog: () -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showLogViewer by rememberSaveable { mutableStateOf(false) }
    var showLicensesDialog by rememberSaveable { mutableStateOf(false) }
    var showMatrixDialog by rememberSaveable { mutableStateOf(false) }
    var showPrismBurst by remember { mutableStateOf(false) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var lastLogOpenTime by remember { mutableLongStateOf(0L) }

    val activeSeason = remember(forcedSeason) {
        EasterEggsManager.resolveActiveSeason(forcedSeason)
    }

    val appVersionName = com.reiux.lumen.BuildConfig.VERSION_NAME
    val appVersionCode = com.reiux.lumen.BuildConfig.VERSION_CODE.toLong()

    if (showLogViewer) {
        LogViewerDialog(
            selectedAccount = selectedAccount,
            settings = settings,
            onDismiss = { showLogViewer = false }
        )
    }

    if (showLicensesDialog) {
        OpenSourceLicensesDialog(onDismiss = { showLicensesDialog = false })
    }

    if (showMatrixDialog) {
        ExpressiveAlertDialog(
            onDismissRequest = { showMatrixDialog = false },
            icon = Icons.Rounded.Code,
            title = stringResource(R.string.about_matrix_title),
            tone = ExpressiveDialogTone.PRIMARY,
            confirmText = stringResource(R.string.about_matrix_ack),
            onConfirm = { showMatrixDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D130F), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.about_matrix_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF00FF66)
                )
            }
        }
    }

    if (showPrismBurst) {
        PlayfulParticleBurstOverlay(
            onFinished = { showPrismBurst = false }
        )
    }

    val handleCheckForUpdates: () -> Unit = {
        if (!isCheckingUpdates) {
            isCheckingUpdates = true
            Toast.makeText(context, context.getString(R.string.toast_checking_updates), Toast.LENGTH_SHORT).show()
            onCheckForUpdates()
            coroutineScope.launch {
                delay(1200L)
                isCheckingUpdates = false
            }
        }
    }

    val handleOpenLiveLogs: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastLogOpenTime > 600L) {
            lastLogOpenTime = now
            LumenLogger.i("AboutUI") { "Opened Live Log Viewer dialog" }
            showLogViewer = true
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideLayout = maxWidth >= 700.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                // Top App Bar with back button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = if (isWideLayout) 1080.dp else 720.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveIconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (isWideLayout) {
                    // Tablet / Landscape 2-Pane Split
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 1080.dp)
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Left Pane: Brand Aperture, Title & Secret Developer Unlock
                        val leftScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .weight(0.44f)
                                .fillMaxHeight()
                                .verticalScroll(leftScrollState),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))

                            BrandHeaderSection(
                                appVersionName = appVersionName,
                                appVersionCode = appVersionCode,
                                developerModeUnlocked = developerModeUnlocked,
                                unlockedPrismTheme = unlockedPrismTheme,
                                activeSeason = activeSeason,
                                onUnlockMatrixTheme = onUnlockMatrixTheme,
                                onShowMatrixDialog = { showMatrixDialog = true }
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Right Pane: About Actions & Debug Tools
                        val rightScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .weight(0.56f)
                                .fillMaxHeight()
                                .verticalScroll(rightScrollState),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))

                            AboutLinksGroup(
                                context = context,
                                isCheckingUpdates = isCheckingUpdates,
                                hasUnreadWhatsNew = hasUnreadWhatsNew,
                                onOpenWhatsNew = onOpenWhatsNew,
                                onOpenLicenses = {
                                    LumenLogger.i("AboutUI") { "Opened Open Source Licenses dialog" }
                                    showLicensesDialog = true
                                },
                                onCheckForUpdates = handleCheckForUpdates
                            )

                            CreditsAndDisclaimerSection(context)

                            if (developerModeUnlocked) {
                                DebugToolsGroup(
                                    context = context,
                                    saveLogsEnabled = saveLogsEnabled,
                                    onToggleSaveLogs = onToggleSaveLogs,
                                    onOpenLiveLogs = handleOpenLiveLogs,
                                    onForceUpdateDialog = onForceUpdateDialog
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                } else {
                    // Portrait / Unfolded Single Column Layout
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 720.dp)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        BrandHeaderSection(
                            appVersionName = appVersionName,
                            appVersionCode = appVersionCode,
                            developerModeUnlocked = developerModeUnlocked,
                            unlockedPrismTheme = unlockedPrismTheme,
                            activeSeason = activeSeason,
                            onUnlockMatrixTheme = onUnlockMatrixTheme,
                            onShowMatrixDialog = { showMatrixDialog = true }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        AboutLinksGroup(
                            context = context,
                            isCheckingUpdates = isCheckingUpdates,
                            hasUnreadWhatsNew = hasUnreadWhatsNew,
                            onOpenWhatsNew = onOpenWhatsNew,
                            onOpenLicenses = {
                                LumenLogger.i("AboutUI") { "Opened Open Source Licenses dialog" }
                                showLicensesDialog = true
                            },
                            onCheckForUpdates = handleCheckForUpdates
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        CreditsAndDisclaimerSection(context)

                        if (developerModeUnlocked) {
                            Spacer(modifier = Modifier.height(24.dp))

                            DebugToolsGroup(
                                context = context,
                                saveLogsEnabled = saveLogsEnabled,
                                onToggleSaveLogs = onToggleSaveLogs,
                                onOpenLiveLogs = handleOpenLiveLogs,
                                onForceUpdateDialog = onForceUpdateDialog
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutLinksGroup(
    context: Context,
    isCheckingUpdates: Boolean,
    hasUnreadWhatsNew: Boolean,
    onOpenWhatsNew: () -> Unit,
    onOpenLicenses: () -> Unit,
    onCheckForUpdates: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            AboutActionRow(
                icon = Icons.Rounded.AutoAwesome,
                title = stringResource(R.string.whats_new_title),
                subtitle = stringResource(R.string.whats_new_subtitle),
                position = GroupPosition.Top,
                showUnreadBadge = hasUnreadWhatsNew,
                onClick = onOpenWhatsNew
            )
            AboutActionRow(
                icon = Icons.Rounded.Code,
                title = stringResource(R.string.about_source_code_title),
                subtitle = stringResource(R.string.about_source_code_desc),
                position = GroupPosition.Middle,
                onClick = {
                    openWebUrl(context, "https://github.com/Reiux/lumen-android")
                }
            )
            AboutActionRow(
                icon = Icons.Rounded.Description,
                title = stringResource(R.string.about_licenses_title),
                subtitle = stringResource(R.string.about_licenses_desc),
                position = GroupPosition.Middle,
                onClick = onOpenLicenses
            )
            AboutActionRow(
                icon = Icons.Rounded.SystemUpdate,
                title = stringResource(R.string.about_check_updates_title),
                subtitle = stringResource(R.string.about_check_updates_desc),
                position = GroupPosition.Bottom,
                isLoading = isCheckingUpdates,
                onClick = onCheckForUpdates
            )
        }
    }
}

@Composable
private fun DebugToolsGroup(
    context: Context,
    saveLogsEnabled: Boolean,
    onToggleSaveLogs: (Boolean) -> Unit,
    onOpenLiveLogs: () -> Unit,
    onForceUpdateDialog: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.about_debug_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val switchInteractionSource = remember { MutableInteractionSource() }
            val isSwitchGesturePressed by rememberExpressivePressed(switchInteractionSource)
            var switchClickPulse by remember { mutableStateOf(false) }
            val switchShape = animatedGroupedCardShape(
                position = GroupPosition.Top,
                pressed = isSwitchGesturePressed || switchClickPulse
            )
            val toggleLogs: (Boolean) -> Unit = { next ->
                try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                coroutineScope.launch {
                    switchClickPulse = true
                    delay(180L)
                    switchClickPulse = false
                }
                onToggleSaveLogs(next)
            }

            Surface(
                onClick = { toggleLogs(!saveLogsEnabled) },
                modifier = Modifier.fillMaxWidth(),
                shape = switchShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                interactionSource = switchInteractionSource
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = stringResource(R.string.about_save_logs_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.about_save_logs_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ExpressiveSwitch(
                        checked = saveLogsEnabled,
                        onCheckedChange = toggleLogs
                    )
                }
            }

            AboutActionRow(
                icon = Icons.Rounded.UploadFile,
                title = stringResource(R.string.about_export_logs_title),
                subtitle = stringResource(R.string.about_export_logs_desc),
                position = GroupPosition.Middle,
                onClick = {
                    LumenLogger.exportLogs(context)
                }
            )

            AboutActionRow(
                icon = Icons.Rounded.BugReport,
                title = stringResource(R.string.about_view_live_logs_title),
                subtitle = stringResource(R.string.about_view_live_logs_desc),
                position = GroupPosition.Middle,
                onClick = onOpenLiveLogs
            )

            AboutActionRow(
                icon = Icons.Rounded.SystemUpdate,
                title = stringResource(R.string.about_force_update_title),
                subtitle = stringResource(R.string.about_force_update_desc),
                position = GroupPosition.Bottom,
                onClick = {
                    Toast.makeText(context, context.getString(R.string.toast_checking_updates), Toast.LENGTH_SHORT).show()
                    onForceUpdateDialog()
                }
            )
        }
    }
}

@Composable
private fun BrandHeaderSection(
    appVersionName: String,
    appVersionCode: Long,
    developerModeUnlocked: Boolean,
    unlockedPrismTheme: Boolean,
    activeSeason: Season = Season.NONE,
    onUnlockMatrixTheme: () -> Unit,
    onShowMatrixDialog: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var versionTapCount by remember { mutableIntStateOf(0) }
    var versionLastTapTime by remember { mutableLongStateOf(0L) }

    var manualRotation by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }

    val wordmarkPunchScale = remember { Animatable(1.0f) }
    val defaultSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    val onBrandTap: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        manualRotation += 360f // Spin logo 360 clockwise!
        coroutineScope.launch {
            wordmarkPunchScale.animateTo(1.26f, defaultSpatialSpec)
            wordmarkPunchScale.animateTo(1.0f, spring(dampingRatio = 0.58f, stiffness = 240f))
        }
    }

    LaunchedEffect(Unit) {
        delay(220L) // Wait for screen slide-in to finish before springing in
        wordmarkPunchScale.animateTo(1.26f, defaultSpatialSpec)
        wordmarkPunchScale.animateTo(1.0f, spring(dampingRatio = 0.58f, stiffness = 240f))
    }

    val animatedRotation by animateFloatAsState(
        targetValue = manualRotation + (if (developerModeUnlocked) 360f else 0f),
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "logoDevRotation"
    )

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            while (isHolding) {
                delay(16L) // ~60fps
                val elapsed = System.currentTimeMillis() - startTime
                val speedFactor = (1f + (elapsed / 500f) * (elapsed / 500f)).coerceAtMost(10f)
                manualRotation += (6f * speedFactor) // Clockwise rotation!
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            tryAwaitRelease()
                            isHolding = false
                        },
                        onTap = {
                            onBrandTap()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(124.dp)
                    .graphicsLayer {
                        rotationZ = animatedRotation * 0.15f
                    },
                shape = MaterialShapes.SoftBurst.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ) {}
            DynamicLumenIcon(
                size = 96.dp,
                rotationAngle = animatedRotation,
                activeSeason = activeSeason
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val dynamicWeight = remember(wordmarkPunchScale.value) {
            val progress = ((wordmarkPunchScale.value - 1.0f) / 0.26f).coerceIn(0f, 1f)
            FontWeight((800 + progress * 150f).toInt())
        }

        Text(
            text = "Lumen",
            style = MaterialTheme.typography.displaySmallEmphasized,
            fontWeight = dynamicWeight,
            letterSpacing = (-0.5).sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = wordmarkPunchScale.value
                    scaleY = wordmarkPunchScale.value
                    transformOrigin = TransformOrigin.Center
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onBrandTap()
                }
        )

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            onClick = {
                val now = System.currentTimeMillis()
                if (now - versionLastTapTime > 2000L) {
                    versionTapCount = 1
                } else {
                    versionTapCount++
                }
                versionLastTapTime = now
                if (versionTapCount >= 5) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUnlockMatrixTheme()
                    onShowMatrixDialog()
                    versionTapCount = 0
                }
            },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Text(
                text = "v$appVersionName ($appVersionCode)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        if (unlockedPrismTheme) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = stringResource(R.string.about_prism_badge),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    position: GroupPosition = GroupPosition.Single,
    isLoading: Boolean = false,
    showUnreadBadge: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    val isGesturePressed by rememberExpressivePressed(interactionSource)
    var clickPulse by remember { mutableStateOf(false) }
    val shape = animatedGroupedCardShape(
        position = position,
        pressed = isGesturePressed || clickPulse
    )

    val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
        coroutineScope.launch {
            clickPulse = true
            delay(180L)
            clickPulse = false
        }
        onClick()
    }

    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val fastSpatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    Surface(
        onClick = hapticClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
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
            }

            Spacer(modifier = Modifier.width(12.dp))

            AnimatedContent(
                targetState = isLoading,
                transitionSpec = {
                    (fadeIn(animationSpec = fastEffects) +
                        scaleIn(initialScale = 0.75f, animationSpec = fastSpatial)) togetherWith
                        fadeOut(animationSpec = fastEffects)
                },
                label = "aboutRowTrailing"
            ) { checking ->
                if (checking) {
                    ExpressiveLoadingIndicator(
                        size = 20.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showUnreadBadge) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                    Text(
                                        text = stringResource(R.string.whats_new_badge_new),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    val licenses = listOf(
        "Jetpack Compose" to "Apache License 2.0 (Google LLC)",
        "Material 3 Expressive Components" to "Apache License 2.0 (Google LLC)",
        "OkHttp" to "Apache License 2.0 (Square, Inc.)",
        "Protobuf Lite" to "BSD-3-Clause (Google LLC)",
        "Coil Image Loader" to "Apache License 2.0 (Coil Contributors)",
        "AndroidX WorkManager" to "Apache License 2.0 (Google LLC)",
        "Kotlin Coroutines" to "Apache License 2.0 (JetBrains s.r.o.)"
    )

    ExpressiveAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.Description,
        title = stringResource(R.string.about_licenses_title),
        tone = ExpressiveDialogTone.SECONDARY,
        confirmText = stringResource(R.string.btn_close),
        onConfirm = onDismiss
    ) {
        licenses.forEach { (lib, license) ->
            Column {
                Text(
                    text = lib,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun openWebUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_cannot_open_browser), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun CreditsAndDisclaimerSection(context: Context) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.about_credits_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            AboutActionRow(
                icon = Icons.Rounded.Person,
                title = stringResource(R.string.about_developer_title),
                subtitle = "Reiux (GitHub @Reiux)",
                position = GroupPosition.Top,
                onClick = {
                    openWebUrl(context, "https://github.com/Reiux")
                }
            )
            AboutActionRow(
                icon = Icons.Rounded.Favorite,
                title = stringResource(R.string.about_core_protocol_title),
                subtitle = "xob0t/gotohp",
                position = GroupPosition.Bottom,
                onClick = {
                    openWebUrl(context, "https://github.com/xob0t/gotohp")
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            shape = groupedCardShape(GroupPosition.Single),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.about_disclaimer_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_disclaimer_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
