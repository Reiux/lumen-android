package com.reiux.lumen.ui.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.UploadFile
import com.reiux.lumen.util.EasterEggsManager
import com.reiux.lumen.util.Season
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reiux.lumen.ui.components.DynamicLumenIcon
import com.reiux.lumen.ui.components.ExpressiveButton
import com.reiux.lumen.ui.components.ExpressiveSwitch
import com.reiux.lumen.ui.components.LogViewerDialog
import com.reiux.lumen.ui.components.PlayfulParticleBurstOverlay
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
    onCheckForUpdates: () -> Unit = {},
    onForceUpdateDialog: () -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLogViewer by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showMatrixDialog by remember { mutableStateOf(false) }
    var showPrismBurst by remember { mutableStateOf(false) }
    var lastLogOpenTime by remember { mutableLongStateOf(0L) }

    val activeSeason = remember(forcedSeason) {
        EasterEggsManager.resolveActiveSeason(forcedSeason)
    }

    val (appVersionName, appVersionCode) = remember(context) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val vName = pInfo?.versionName ?: "1.0.0"
            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo?.longVersionCode ?: 1L
            } else {
                @Suppress("DEPRECATION")
                pInfo?.versionCode?.toLong() ?: 1L
            }
            vName to vCode
        } catch (_: Exception) {
            "1.0.0" to 1L
        }
    }

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
        AlertDialog(
            onDismissRequest = { showMatrixDialog = false },
            title = {
                Text(
                    text = "SYSTEM OVERRIDE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FF66)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D130F), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "SYSTEM OVERRIDE: Welcome, Architect.\nAll cloud systems operational.\n\nDeveloper tools unlocked.\nMatrix theme preset added to Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00FF66)
                    )
                }
            },
            confirmButton = {
                ExpressiveButton(onClick = { showMatrixDialog = false }) {
                    Text("Acknowledge")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color(0xFF111713)
        )
    }

    if (showPrismBurst) {
        PlayfulParticleBurstOverlay(
            onFinished = { showPrismBurst = false }
        )
    }

    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
        ) {
            // Top App Bar with back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isWideLayout) {
                // Tablet / Landscape 2-Pane Split
                Row(
                    modifier = Modifier
                        .fillMaxSize()
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

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "About",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    AboutActionRow(
                                        icon = Icons.Rounded.Code,
                                        title = "Source code",
                                        subtitle = "View repository on GitHub",
                                        onClick = {
                                            openWebUrl(context, "https://github.com/Reiux/lumen-android")
                                        }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    AboutActionRow(
                                        icon = Icons.Rounded.Description,
                                        title = "Open source licenses",
                                        subtitle = "Third-party libraries and licenses",
                                        onClick = {
                                            LumenLogger.i("AboutUI") { "Opened Open Source Licenses dialog" }
                                            showLicensesDialog = true
                                        }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    AboutActionRow(
                                        icon = Icons.Rounded.SystemUpdate,
                                        title = "Check for updates",
                                        subtitle = "Check GitHub for latest release",
                                        onClick = {
                                            android.widget.Toast.makeText(context, "Checking for updates...", android.widget.Toast.LENGTH_SHORT).show()
                                            onCheckForUpdates()
                                        }
                                    )
                                }
                            }
                        }

                        CreditsAndDisclaimerSection(context)

                        if (developerModeUnlocked) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Debug",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 4.dp)
                                )

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                                Text(
                                                    text = "Save Logs",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Save logs to local files on your device",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            ExpressiveSwitch(
                                                checked = saveLogsEnabled,
                                                onCheckedChange = onToggleSaveLogs
                                            )
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        )

                                        AboutActionRow(
                                            icon = Icons.Rounded.UploadFile,
                                            title = "Export Logs",
                                            subtitle = "Share or save a text file of your logs",
                                            onClick = {
                                                LumenLogger.exportLogs(context)
                                            }
                                        )

                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        )

                                        AboutActionRow(
                                            icon = Icons.Rounded.BugReport,
                                            title = "View Live Logs",
                                            subtitle = "View live app activity in real time",
                                            onClick = {
                                                val now = System.currentTimeMillis()
                                                if (now - lastLogOpenTime > 600L) {
                                                    lastLogOpenTime = now
                                                    LumenLogger.i("AboutUI") { "Opened Live Log Viewer dialog" }
                                                    showLogViewer = true
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            } else {
                // Portrait Single Column Layout
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 680.dp)
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

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                AboutActionRow(
                                    icon = Icons.Rounded.Code,
                                    title = "Source code",
                                    subtitle = "View repository on GitHub",
                                    onClick = {
                                        openWebUrl(context, "https://github.com/Reiux/lumen-android")
                                    }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                AboutActionRow(
                                    icon = Icons.Rounded.Description,
                                    title = "Open source licenses",
                                    subtitle = "Third-party libraries and licenses",
                                    onClick = {
                                        LumenLogger.i("AboutUI") { "Opened Open Source Licenses dialog" }
                                        showLicensesDialog = true
                                    }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                AboutActionRow(
                                    icon = Icons.Rounded.SystemUpdate,
                                    title = "Check for updates",
                                    subtitle = "Check GitHub for latest release",
                                    onClick = {
                                        android.widget.Toast.makeText(context, "Checking for updates...", android.widget.Toast.LENGTH_SHORT).show()
                                        onCheckForUpdates()
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    CreditsAndDisclaimerSection(context)

                    if (developerModeUnlocked) {
                        Spacer(modifier = Modifier.height(24.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Debug",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                            Text(
                                                text = "Save Logs",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Save logs to local files on your device",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        ExpressiveSwitch(
                                            checked = saveLogsEnabled,
                                            onCheckedChange = onToggleSaveLogs
                                        )
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )

                                    AboutActionRow(
                                        icon = Icons.Rounded.UploadFile,
                                        title = "Export Logs",
                                        subtitle = "Share or save a text file of your logs",
                                        onClick = {
                                            LumenLogger.exportLogs(context)
                                        }
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )

                                    AboutActionRow(
                                        icon = Icons.Rounded.BugReport,
                                        title = "View Live Logs",
                                        subtitle = "View live app activity in real time",
                                        onClick = {
                                            val now = System.currentTimeMillis()
                                            if (now - lastLogOpenTime > 600L) {
                                                lastLogOpenTime = now
                                                LumenLogger.i("AboutUI") { "Opened Live Log Viewer dialog" }
                                                showLogViewer = true
                                            }
                                        }
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )

                                    AboutActionRow(
                                        icon = Icons.Rounded.SystemUpdate,
                                        title = "Force Update",
                                        subtitle = "Reinstall or force update to the latest release",
                                        onClick = {
                                            android.widget.Toast.makeText(context, "Checking for updates...", android.widget.Toast.LENGTH_SHORT).show()
                                            onForceUpdateDialog()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
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
    var versionTapCount by remember { mutableIntStateOf(0) }
    var versionLastTapTime by remember { mutableLongStateOf(0L) }

    var manualRotation by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }

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
                .size(100.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            tryAwaitRelease()
                            isHolding = false
                        },
                        onTap = {
                            manualRotation += 45f // Clockwise step!
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            DynamicLumenIcon(
                size = 100.dp,
                rotationAngle = animatedRotation,
                activeSeason = com.reiux.lumen.util.Season.NONE
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Lumen",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
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
                    text = "Master of Light",
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Open Source Licenses",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
        },
        confirmButton = {
            ExpressiveButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

private fun openWebUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun CreditsAndDisclaimerSection(context: Context) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Credits & Acknowledgments",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AboutActionRow(
                    icon = Icons.Rounded.Person,
                    title = "Developer",
                    subtitle = "Reiux (GitHub @Reiux)",
                    onClick = {
                        openWebUrl(context, "https://github.com/Reiux")
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                )
                AboutActionRow(
                    icon = Icons.Rounded.Favorite,
                    title = "Original Core Protocol Logic",
                    subtitle = "xob0t/gotohp",
                    onClick = {
                        openWebUrl(context, "https://github.com/xob0t/gotohp")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Disclaimer",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lumen is an independent open-source application and is not affiliated with, endorsed by, or sponsored by Google LLC. Users use this app and services at their own discretion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
