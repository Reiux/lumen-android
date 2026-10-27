package com.reiux.lumen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.ContextCompat
import com.reiux.lumen.ui.MainViewModel
import com.reiux.lumen.ui.components.ColdStartSplashScreen
import com.reiux.lumen.ui.components.DevDiagnosticsDialog
import com.reiux.lumen.ui.components.ExpressiveNavBar
import com.reiux.lumen.ui.components.ExpressiveNavRail
import com.reiux.lumen.ui.components.NavDestination
import com.reiux.lumen.ui.screens.AboutScreen
import com.reiux.lumen.ui.screens.AccountsScreen
import com.reiux.lumen.ui.screens.BackupScreen
import com.reiux.lumen.ui.screens.FoldersScreen
import com.reiux.lumen.ui.screens.OnboardingScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.reiux.lumen.ui.screens.SettingsScreen
import com.reiux.lumen.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        com.reiux.lumen.util.LumenLogger.i("Permissions", "Permission results: $results")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val isRestored = savedInstanceState != null
        com.reiux.lumen.util.LumenLogger.i("AppLifecycle") {
            "MainActivity onCreate (SDK ${Build.VERSION.SDK_INT}, processRestore=$isRestored)"
        }
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        setContent {
            val settings by viewModel.settings.collectAsState()
            val isMockMode by viewModel.isMockMode.collectAsState()

            val activeSeason = remember(settings.forcedSeason) {
                com.reiux.lumen.util.EasterEggsManager.resolveActiveSeason(settings.forcedSeason)
            }
            
            LaunchedEffect(Unit) {
                if (intent?.getStringExtra("action") == "show_update") {
                    viewModel.checkAndShowUpdate(this@MainActivity)
                }
            }

            LumenTheme(
                colorPreset = settings.colorPreset,
                activeSeason = activeSeason
            ) {
                var showDevDiagnostics by remember { mutableStateOf(false) }
                var showAboutScreen by remember { mutableStateOf(false) }

                BackHandler(enabled = showAboutScreen) {
                    com.reiux.lumen.util.LumenLogger.i("Navigation") { "Back pressed: Exiting About screen" }
                    showAboutScreen = false
                }

                val selectedDestination by viewModel.selectedDestination.collectAsState()
                val selectedAccount by viewModel.selectedAccount.collectAsState()
                val accounts by viewModel.accounts.collectAsState()
                val isUploading by viewModel.isUploading.collectAsState()
                val currentItem by viewModel.currentUploadingItem.collectAsState()
                val queue by viewModel.queue.collectAsState()
                val completedCount by viewModel.completedCount.collectAsState()
                val conditionNotice by viewModel.conditionNotice.collectAsState()
                val isMockMode by viewModel.isMockMode.collectAsState()
                val isShowcaseMode by viewModel.isShowcaseMode.collectAsState()
                val isAddingAccount by viewModel.isAddingAccount.collectAsState()
                val addAccountError by viewModel.addAccountError.collectAsState()
                val uploadSpeed by viewModel.uploadSpeed.collectAsState()
                val scanState by viewModel.scanState.collectAsState()
                val accountBlock by viewModel.accountBlock.collectAsState()

                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val useNavRail = isLandscape || configuration.screenWidthDp >= 600

                val widthDp = configuration.screenWidthDp
                val heightDp = configuration.screenHeightDp
                val widthClass = when {
                    widthDp < 600 -> "Compact"
                    widthDp < 840 -> "Medium"
                    else -> "Expanded"
                }

                val isDarkNow = isSystemInDarkTheme()
                val lastDarkState = remember { viewModel.getLastSeenDarkState() }
                var playLightsOffAnim by remember { mutableStateOf(false) }

                LaunchedEffect(isDarkNow) {
                    if (lastDarkState != null) {
                        if (!lastDarkState && isDarkNow) {
                            playLightsOffAnim = true
                            com.reiux.lumen.util.LumenLogger.i("EasterEgg") { "Triggered 'Lights off' radial dim overlay" }
                        } else if (lastDarkState && !isDarkNow) {
                            com.reiux.lumen.util.EasterEggsManager.triggerLightsOffBoldChoice(this@MainActivity)
                        }
                    }
                    viewModel.setLastSeenDarkState(isDarkNow)
                }

                LaunchedEffect(useNavRail, widthDp, heightDp) {
                    com.reiux.lumen.util.LumenLogger.i("Layout") {
                        "Window layout: width=${widthDp}dp height=${heightDp}dp class=$widthClass mode=${if (useNavRail) "NavRail (Landscape/Wide)" else "NavBar (Portrait/Compact)"}"
                    }
                }

                @Composable
                fun ScreenContent(destination: NavDestination, screenModifier: Modifier = Modifier) {
                    when (destination) {
                        NavDestination.BACKUP -> BackupScreen(
                            selectedAccount = selectedAccount,
                            isUploading = isUploading,
                            uploadSpeed = uploadSpeed,
                            currentUploadingItem = currentItem,
                            queue = queue,
                            completedCount = completedCount,
                            storageSaverQuality = settings.storageSaverQuality,
                            useQuota = settings.useQuota,
                            conditionNotice = conditionNotice,
                            scanState = scanState,
                            accountBlock = accountBlock,
                            developerModeUnlocked = settings.developerModeUnlocked,
                            isMockMode = isMockMode,
                            forcedSeason = settings.forcedSeason,
                            onToggleMockCondition = { viewModel.toggleMockConditionNotice() },
                            onAddMockFiles = { viewModel.addMockUploadItems() },
                            onExitMockMode = { viewModel.setMockMode(false) },
                            onStartBackup = { viewModel.startBackup() },
                            onUploadFiles = { uris -> viewModel.uploadSelectedUris(uris, this@MainActivity) },
                            onCancelBackup = { viewModel.cancelBackup() },
                            onCancelScan = { viewModel.cancelScan() },
                            onClearCompleted = { viewModel.clearCompleted() },
                            onNavigateToAccounts = { 
                                if (isMockMode && accountBlock != null) {
                                    viewModel.clearMockBlock()
                                }
                                viewModel.selectDestination(NavDestination.ACCOUNTS) 
                            },
                            addAccountError = addAccountError,
                            onAddAccountToken = { viewModel.addAccountWithToken(it) },
                            onAddAccountCredential = { viewModel.addAccountWithCredential(it) },
                            onRemoveAccount = { viewModel.removeAccount(it) },
                            onClearAddAccountError = { viewModel.clearAddAccountError() },
                            onUnlockPrismTheme = { viewModel.unlockPrismTheme() },
                            onTriggerLightsOff = { playLightsOffAnim = true },
                            onSimulateLargeScan = { viewModel.simulateMockLargeScan() },
                            onSimulateUpdateAvailable = { viewModel.simulateUpdateAvailable() },
                            onSimulateBlock = { kind -> viewModel.simulateMockBlock(kind) },
                            onClearBlock = { viewModel.clearMockBlock() },
                            modifier = screenModifier
                        )

                        NavDestination.FOLDERS -> FoldersScreen(
                            folders = settings.folders,
                            onToggleFolder = { path, enabled -> viewModel.toggleFolder(path, enabled) },
                            onSetIncludeSubfolders = { path, inc -> viewModel.setIncludeSubfolders(path, inc) },
                            onAddFolder = { viewModel.addFolder(it) },
                            onRemoveFolder = { viewModel.removeFolder(it) },
                            modifier = screenModifier
                        )

                        NavDestination.ACCOUNTS -> AccountsScreen(
                            accounts = accounts,
                            selectedAccount = selectedAccount,
                            onSelectAccount = { viewModel.selectAccount(it) },
                            onAddAccountToken = { viewModel.addAccountWithToken(it) },
                            onAddAccountCredential = { viewModel.addAccountWithCredential(it) },
                            onUpdateDisplayName = { email, name -> viewModel.updateDisplayName(email, name) },
                            onUpdateAvatar = { email, uri -> viewModel.updateAccountAvatar(email, uri, this@MainActivity) },
                            onRemoveAccount = { viewModel.removeAccount(it) },
                            onClearError = { viewModel.clearAddAccountError() },
                            isAddingAccount = isAddingAccount,
                            addAccountError = addAccountError,
                            hasAcknowledgedAccountRisk = settings.hasAcknowledgedAccountRisk,
                            onAcknowledgeRisk = { viewModel.acknowledgeAccountRisk() },
                            modifier = screenModifier
                        )

                        NavDestination.SETTINGS -> SettingsScreen(
                            settings = settings,
                            isMockMode = isMockMode,
                            isShowcaseMode = isShowcaseMode,
                            onToggleMockMode = { viewModel.toggleMockMode() },
                            onToggleShowcaseMode = { viewModel.toggleShowcaseMode() },
                            onUpdateAutoBackup = { viewModel.updateAutoBackup(it) },
                            onUpdateWifiOnly = { viewModel.updateWifiOnly(it) },
                            onUpdateChargingOnly = { viewModel.updateChargingOnly(it) },
                            onUpdateStorageSaver = { viewModel.updateStorageSaver(it) },
                            onUpdateUseQuota = { viewModel.updateUseQuota(it) },
                            onUpdateUploadThreads = { viewModel.updateUploadThreads(it) },
                            onUpdateColorPreset = { viewModel.updateColorPreset(it) },
                            onUpdateNotifications = { viewModel.updateNotifications(it) },
                            onToggleDeveloperMode = { viewModel.updateDeveloperMode(it) },
                            onUpdateForcedSeason = { viewModel.updateForcedSeason(it) },
                            onReplayOnboarding = { viewModel.setHasCompletedOnboarding(false) },
                            onOpenDevDiagnostics = {
                                com.reiux.lumen.util.LumenLogger.i("UI", "Opening Dev Diagnostics")
                                showDevDiagnostics = true
                            },
                            onNavigateToAbout = {
                                com.reiux.lumen.util.LumenLogger.i("Navigation", "Navigating to About screen")
                                showAboutScreen = true
                            },
                            modifier = screenModifier
                        )
                    }
                }

                val showUpdateSheet by viewModel.showUpdateSheet.collectAsState()
                val updateInfo by viewModel.updateInfo.collectAsState()
                val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsState()

                if (showUpdateSheet && updateInfo != null) {
                    com.reiux.lumen.ui.components.UpdateBottomSheet(
                        updateInfo = updateInfo!!,
                        isDownloading = updateDownloadProgress >= 0,
                        downloadProgress = updateDownloadProgress,
                        onDismiss = { viewModel.dismissUpdateSheet() },
                        onUpdateClick = { viewModel.startUpdateDownload(this@MainActivity) }
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = settings.hasCompletedOnboarding,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "onboardingMainTransition"
                    ) { hasCompleted ->
                        if (!hasCompleted) {
                            OnboardingScreen(
                                viewModel = viewModel,
                                onFinish = {
                                    viewModel.setHasCompletedOnboarding(true)
                                }
                            )
                        } else {
                            if (!useNavRail) {
                                Scaffold(
                                    modifier = Modifier.fillMaxSize(),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    bottomBar = {
                                        ExpressiveNavBar(
                                            selectedDestination = selectedDestination,
                                            onDestinationSelected = {
                                                viewModel.selectDestination(it)
                                            }
                                        )
                                    }
                                ) { innerPadding ->
                                    AnimatedContent(
                                        targetState = selectedDestination,
                                        transitionSpec = {
                                            (fadeIn(animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)) +
                                                    scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)))
                                                .togetherWith(
                                                    fadeOut(animationSpec = spring(stiffness = 1600f)) +
                                                            scaleOut(targetScale = 0.97f, animationSpec = spring(stiffness = 1600f))
                                                )
                                        },
                                        label = "screenTransition",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                    ) { destination ->
                                        ScreenContent(destination)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    ExpressiveNavRail(
                                        selectedDestination = selectedDestination,
                                        onDestinationSelected = {
                                            viewModel.selectDestination(it)
                                        }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .windowInsetsPadding(
                                                WindowInsets.safeDrawing.only(
                                                    WindowInsetsSides.End + WindowInsetsSides.Vertical
                                                )
                                            )
                                    ) {
                                        AnimatedContent(
                                            targetState = selectedDestination,
                                            transitionSpec = {
                                                (fadeIn(animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)) +
                                                        scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)))
                                                    .togetherWith(
                                                        fadeOut(animationSpec = spring(stiffness = 1600f)) +
                                                                scaleOut(targetScale = 0.97f, animationSpec = spring(stiffness = 1600f))
                                                    )
                                            },
                                            label = "screenTransition",
                                            modifier = Modifier.fillMaxSize()
                                        ) { destination ->
                                            ScreenContent(destination)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (playLightsOffAnim) {
                        val animProgress = remember { androidx.compose.animation.core.Animatable(0f) }
                        LaunchedEffect(Unit) {
                            animProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(500, easing = LinearEasing)
                            )
                            playLightsOffAnim = false
                        }
                        val alpha = if (animProgress.value < 0.5f) {
                            animProgress.value * 1.6f
                        } else {
                            (1f - animProgress.value) * 1.6f
                        }.coerceIn(0f, 0.8f)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = alpha))
                        )
                    }

                    AnimatedVisibility(
                        visible = showAboutScreen,
                        enter = slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                        ) + fadeIn(animationSpec = tween(200)),
                        exit = slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                        ) + fadeOut(animationSpec = tween(180))
                    ) {
                        AboutScreen(
                            saveLogsEnabled = settings.saveLogsEnabled,
                            onToggleSaveLogs = { viewModel.updateSaveLogs(it, this@MainActivity) },
                            selectedAccount = selectedAccount,
                            settings = settings,
                            developerModeUnlocked = settings.developerModeUnlocked,
                            unlockedPrismTheme = settings.unlockedPrismTheme,
                            onToggleDeveloperMode = { viewModel.updateDeveloperMode(it) },
                            onUnlockPrismTheme = { viewModel.unlockPrismTheme() },
                            onUnlockMatrixTheme = { viewModel.unlockMatrixTheme() },
                            forcedSeason = settings.forcedSeason,
                            onCheckForUpdates = {
                                viewModel.checkAndShowUpdate(this@MainActivity, forceShow = false) {
                                    android.widget.Toast.makeText(this@MainActivity, "Lumen is up to date", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onForceUpdateDialog = {
                                viewModel.checkAndShowUpdate(this@MainActivity, forceShow = true) {}
                            },
                            onNavigateBack = {
                                com.reiux.lumen.util.LumenLogger.i("Navigation") { "Back button: Exiting About screen" }
                                showAboutScreen = false
                            }
                        )
                    }

                    if (showDevDiagnostics) {
                        DevDiagnosticsDialog(
                            selectedAccount = selectedAccount,
                            onRunLoggingSelfTest = { viewModel.runLoggingSelfTest() },
                            onDismiss = { showDevDiagnostics = false }
                        )
                    }

                    com.reiux.lumen.ui.components.FallingSnowOverlay(enabled = activeSeason == com.reiux.lumen.util.Season.CHRISTMAS)
                    com.reiux.lumen.ui.components.FlyingBatOverlay(enabled = activeSeason == com.reiux.lumen.util.Season.HALLOWEEN)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        com.reiux.lumen.util.LumenLogger.d("AppLifecycle") { "MainActivity onStart" }
    }

    override fun onResume() {
        super.onResume()
        com.reiux.lumen.util.LumenLogger.d("AppLifecycle") { "MainActivity onResume" }
    }

    override fun onPause() {
        super.onPause()
        com.reiux.lumen.util.LumenLogger.d("AppLifecycle") { "MainActivity onPause" }
    }

    override fun onStop() {
        super.onStop()
        com.reiux.lumen.util.LumenLogger.d("AppLifecycle") { "MainActivity onStop" }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.reiux.lumen.util.LumenLogger.d("AppLifecycle") { "MainActivity onDestroy" }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val orientation = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) "LANDSCAPE" else "PORTRAIT"
        com.reiux.lumen.util.LumenLogger.i("AppLifecycle") {
            "onConfigurationChanged: orientation=$orientation width=${newConfig.screenWidthDp}dp height=${newConfig.screenHeightDp}dp"
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
