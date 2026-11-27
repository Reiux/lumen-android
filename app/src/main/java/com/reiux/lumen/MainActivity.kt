package com.reiux.lumen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.reiux.lumen.ui.MainViewModel
import com.reiux.lumen.ui.ShareUploadBatch
import com.reiux.lumen.ui.components.DevDiagnosticsDialog
import com.reiux.lumen.ui.components.ExpressiveExtendedFloatingActionButton
import com.reiux.lumen.ui.components.ExpressiveNavBar
import com.reiux.lumen.ui.components.ExpressiveNavRail
import com.reiux.lumen.ui.components.LocalBlurEffectsEnabled
import com.reiux.lumen.ui.components.MockControlsBottomSheet
import com.reiux.lumen.ui.components.NavDestination
import com.reiux.lumen.ui.components.PredictiveBackScreenContainer
import com.reiux.lumen.ui.screens.AboutScreen
import com.reiux.lumen.ui.screens.AccountsScreen
import com.reiux.lumen.ui.screens.AlbumsScreen
import com.reiux.lumen.ui.screens.BackupScreen
import com.reiux.lumen.ui.screens.DeviceFoldersScreen
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        handleShareIntent(intent)
        if (isValidInternalUpdateIntent(intent)) {
            viewModel.setPendingShowUpdate(true)
        }
        val extraAction = intent.getStringExtra("action")
        val token = intent.getStringExtra("internal_token")
        val hasValidToken = (application as? LumenApplication)
            ?.settingsRepository
            ?.isValidInternalLaunchToken(token) == true

        val resolvedShortcut = when {
            (extraAction == "shortcut_sync" || extraAction == "shortcut_pick") && hasValidToken -> extraAction
            intent.action == "com.reiux.lumen.action.SHORTCUT_SYNC" -> "shortcut_sync"
            intent.action == "com.reiux.lumen.action.SHORTCUT_PICK" -> "shortcut_pick"
            else -> null
        }
        if (resolvedShortcut != null) {
            viewModel.setPendingShortcutAction(resolvedShortcut)
        } else if (extraAction == "shortcut_sync" || extraAction == "shortcut_pick") {
            com.reiux.lumen.util.LumenLogger.w("Shortcuts") {
                "Ignored unauthenticated shortcut launch intent: $extraAction"
            }
        }
    }

    private fun isValidInternalUpdateIntent(intent: Intent?): Boolean {
        if (intent?.getStringExtra("action") != "show_update") return false
        val token = intent.getStringExtra("internal_token")
        val isValid = (application as? LumenApplication)
            ?.settingsRepository
            ?.isValidInternalLaunchToken(token) == true
        if (!isValid) {
            com.reiux.lumen.util.LumenLogger.w("Updater") {
                "Ignored unauthenticated show_update launch intent"
            }
        }
        return isValid
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        val extracted = mutableListOf<android.net.Uri>()
        if (action == Intent.ACTION_SEND) {
            val streamUri = androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)
            if (streamUri != null) {
                extracted.add(streamUri)
            } else if (intent.data != null) {
                extracted.add(intent.data!!)
            }
        } else if (action == Intent.ACTION_SEND_MULTIPLE) {
            val streamUris = androidx.core.content.IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)
            if (!streamUris.isNullOrEmpty()) {
                for (item in (streamUris as List<*>)) {
                    val uri = item as? android.net.Uri ?: continue
                    extracted.add(uri)
                    if (extracted.size > MAX_SHARE_URIS) break
                }
            }
        }
        if (extracted.isEmpty() && intent.clipData != null) {
            val clipData = intent.clipData!!
            for (i in 0 until clipData.itemCount) {
                val itemUri = clipData.getItemAt(i).uri
                if (itemUri != null) {
                    extracted.add(itemUri)
                    if (extracted.size > MAX_SHARE_URIS) break
                }
            }
        }

        val wasCapped = extracted.size > MAX_SHARE_URIS
        val selfAuthority = "${packageName}.fileprovider"
        val safeUris = filterAndCapShareUris(extracted, selfAuthority, MAX_SHARE_URIS)

        if (safeUris.isNotEmpty()) {
            if (wasCapped) {
                com.reiux.lumen.util.LumenLogger.w("ShareTarget") {
                    "Share intent exceeded $MAX_SHARE_URIS URIs; capped batch at ${safeUris.size}"
                }
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.share_uris_capped_toast, MAX_SHARE_URIS),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            val batch = ShareUploadBatch(uris = safeUris)
            val active = viewModel.incomingShareBatch.value
            if (active == null) {
                com.reiux.lumen.util.LumenLogger.i("ShareTarget") {
                    "Received ${safeUris.size} shared media URIs via share sheet (batch=${batch.batchId})"
                }
                viewModel.setIncomingShareBatch(batch)
            } else if (batch.uris != active.uris) {
                com.reiux.lumen.util.LumenLogger.w("ShareTarget") {
                    "Queued replacement share batch (${safeUris.size} URIs, batch=${batch.batchId}) while active batch=${active.batchId} is awaiting confirmation"
                }
                viewModel.setQueuedShareReplacement(batch)
            }
        } else if (extracted.isNotEmpty()) {
            com.reiux.lumen.util.LumenLogger.w("ShareTarget") { "Rejected ${extracted.size} untrusted share URI(s)" }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
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

        // Release legacy SAF persisted URI permissions to clean up App Info external storage grants
        try {
            contentResolver.persistedUriPermissions.forEach { perm ->
                try {
                    contentResolver.releasePersistableUriPermission(
                        perm.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        setContent {
            val settings by viewModel.settings.collectAsState()
            val isMockMode by viewModel.isMockMode.collectAsState()
            val isSecureStorageAvailable by viewModel.isSecureStorageAvailable.collectAsState()

            val activeSeason = remember(settings.forcedSeason) {
                com.reiux.lumen.util.EasterEggsManager.resolveActiveSeason(settings.forcedSeason)
            }
            
            LaunchedEffect(isSecureStorageAvailable) {
                if (!isSecureStorageAvailable && !viewModel.secureStorageWarningShown) {
                    android.widget.Toast.makeText(
                        this@MainActivity, 
                        getString(R.string.secure_storage_unavailable_warning), 
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    viewModel.secureStorageWarningShown = true
                }
            }

            val showUpdateRequest by viewModel.pendingShowUpdate.collectAsState()
            val incomingShareBatch by viewModel.incomingShareBatch.collectAsState()
            val queuedShareReplacement by viewModel.queuedShareReplacement.collectAsState()
            val shortcutAction by viewModel.pendingShortcutAction.collectAsState()

            LaunchedEffect(showUpdateRequest) {
                if (showUpdateRequest) {
                    viewModel.setPendingShowUpdate(false)
                    intent?.removeExtra("action")
                    intent?.removeExtra("internal_token")
                    viewModel.openUpdateFromNotification(this@MainActivity)
                }
            }

            LaunchedEffect(Unit) {
                viewModel.loadAlbums()
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        val currentSettings = viewModel.settings.value
                        if (!viewModel.pendingShowUpdate.value &&
                            currentSettings.autoCheckUpdates &&
                            currentSettings.hasCompletedOnboarding &&
                            viewModel.incomingShareBatch.value == null
                        ) {
                            viewModel.checkForForegroundUpdateOnResume(this@MainActivity)
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            var wasOnboardingCompleted by remember { mutableStateOf(settings.hasCompletedOnboarding) }
            LaunchedEffect(settings.hasCompletedOnboarding) {
                val transitionedToComplete = !wasOnboardingCompleted && settings.hasCompletedOnboarding
                wasOnboardingCompleted = settings.hasCompletedOnboarding
                if (transitionedToComplete &&
                    !viewModel.pendingShowUpdate.value &&
                    settings.autoCheckUpdates &&
                    viewModel.incomingShareBatch.value == null
                ) {
                    viewModel.checkForForegroundUpdateOnResume(this@MainActivity)
                }
                if (settings.hasCompletedOnboarding &&
                    viewModel.incomingShareBatch.value == null &&
                    !viewModel.pendingShowUpdate.value &&
                    viewModel.pendingShortcutAction.value == null
                ) {
                    viewModel.checkPostUpdateWhatsNewSheet()
                }
            }

            LaunchedEffect(settings.hasCompletedOnboarding) {
                if (settings.hasCompletedOnboarding &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    !viewModel.hasRequestedMediaLocationUpgrade
                ) {
                    val hasAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        android.os.Environment.isExternalStorageManager()
                    if (!hasAllFilesAccess) {
                        val hasMediaRead = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                        } else {
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        }
                        val hasMediaLocation = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_MEDIA_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMediaRead && !hasMediaLocation) {
                            viewModel.hasRequestedMediaLocationUpgrade = true
                            com.reiux.lumen.util.LumenLogger.i("Permissions") {
                                "Requesting ACCESS_MEDIA_LOCATION to preserve original EXIF/GPS metadata and deduplication"
                            }
                            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_MEDIA_LOCATION))
                        }
                    }
                }
            }

            LumenTheme(
                colorPreset = settings.colorPreset,
                activeSeason = activeSeason
            ) {
                val motionScheme = MaterialTheme.motionScheme
                val reducedMotion = remember {
                    Settings.Global.getFloat(
                        contentResolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                        1f
                    ) == 0f
                }

                CompositionLocalProvider(
                    LocalBlurEffectsEnabled provides settings.enableBlurEffects
                ) {
                var showDevDiagnostics by rememberSaveable { mutableStateOf(false) }
                var showAboutScreen by rememberSaveable { mutableStateOf(false) }
                var showWhatsNewScreen by rememberSaveable { mutableStateOf(false) }
                var showAlbumsScreen by rememberSaveable { mutableStateOf(false) }
                var showDeviceFoldersScreen by rememberSaveable { mutableStateOf(false) }
                var pendingPickMediaNonce by rememberSaveable { mutableIntStateOf(0) }
                var predictiveBackProgress by remember { mutableFloatStateOf(0f) }

                LaunchedEffect(shortcutAction, settings.hasCompletedOnboarding) {
                    val action = shortcutAction ?: return@LaunchedEffect
                    if (!settings.hasCompletedOnboarding) return@LaunchedEffect
                    viewModel.setPendingShortcutAction(null)
                    intent?.removeExtra("action")
                    intent?.removeExtra("internal_token")
                    if (intent?.action == "com.reiux.lumen.action.SHORTCUT_SYNC" ||
                        intent?.action == "com.reiux.lumen.action.SHORTCUT_PICK"
                    ) {
                        intent?.action = null
                    }
                    showAboutScreen = false
                    showWhatsNewScreen = false
                    showAlbumsScreen = false
                    showDeviceFoldersScreen = false
                    viewModel.selectDestination(NavDestination.BACKUP)
                    when (action) {
                        "shortcut_sync" -> viewModel.startBackup()
                        "shortcut_pick" -> pendingPickMediaNonce++
                    }
                }

                val selectedDestination by viewModel.selectedDestination.collectAsState()
                val selectedAccount by viewModel.selectedAccount.collectAsState()
                val accounts by viewModel.accounts.collectAsState()
                val remoteAlbums by viewModel.remoteAlbums.collectAsState()
                val isUploading by viewModel.isUploading.collectAsState()
                val isPaused by viewModel.isPaused.collectAsState()
                val currentItem by viewModel.currentUploadingItem.collectAsState()
                val queue by viewModel.queue.collectAsState()
                val completedCount by viewModel.completedCount.collectAsState()
                val skippedCount by viewModel.skippedCount.collectAsState()
                val conditionNotice by viewModel.conditionNotice.collectAsState()
                val isShowcaseMode by viewModel.isShowcaseMode.collectAsState()
                val isAddingAccount by viewModel.isAddingAccount.collectAsState()
                val addAccountError by viewModel.addAccountError.collectAsState()
                val uploadSpeed by viewModel.uploadSpeed.collectAsState()
                val scanState by viewModel.scanState.collectAsState()
                val accountBlock by viewModel.accountBlock.collectAsState()
                val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
                val simulateMissingAlbum by viewModel.simulateMissingAlbum.collectAsState()
                val simulateOffline by viewModel.simulateOffline.collectAsState()
                val deviceFolders by viewModel.deviceFolders.collectAsState()
                val isDiscoveringFolders by viewModel.isDiscoveringFolders.collectAsState()
                val albumSyncState by viewModel.albumSyncState.collectAsState()
                val mockSwipeNudgeNonce by viewModel.mockSwipeNudgeNonce.collectAsState()
                val scrollToAccountForNudge by viewModel.scrollToAccountForNudge.collectAsState()
                val readWhatsNewIds by viewModel.readWhatsNewIds.collectAsState()
                val hasUnreadWhatsNew = remember(readWhatsNewIds) {
                    com.reiux.lumen.data.model.WhatsNewCatalog.hasUnread(readWhatsNewIds)
                }
                val showPostUpdateWhatsNewSheet by viewModel.showPostUpdateWhatsNewSheet.collectAsState()
                val mockForcedPunTitle by viewModel.mockForcedPunTitle.collectAsState()
                val mockEmptyStateSubtitle by viewModel.mockEmptyStateSubtitle.collectAsState()
                val mockLightsOffNonce by viewModel.mockLightsOffNonce.collectAsState()
                val mockShakeFlickerNonce by viewModel.mockShakeFlickerNonce.collectAsState()
                var showMockControlsSheet by rememberSaveable { mutableStateOf(false) }

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
                var lastDarkState by remember { mutableStateOf(viewModel.getLastSeenDarkState()) }
                var playLightsOffAnim by remember { mutableStateOf(false) }

                LaunchedEffect(mockLightsOffNonce) {
                    if (mockLightsOffNonce > 0) {
                        playLightsOffAnim = true
                    }
                }

                LaunchedEffect(isMockMode) {
                    if (!isMockMode) {
                        showMockControlsSheet = false
                    }
                }

                LaunchedEffect(isDarkNow) {
                    val prevDark = lastDarkState
                    if (prevDark != null) {
                        if (!prevDark && isDarkNow) {
                            playLightsOffAnim = true
                            com.reiux.lumen.util.LumenLogger.i("EasterEgg") { "Triggered 'Lights off' radial dim overlay" }
                        } else if (prevDark && !isDarkNow) {
                            com.reiux.lumen.util.EasterEggsManager.triggerLightsOffBoldChoice(this@MainActivity)
                        }
                    }
                    lastDarkState = isDarkNow
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
                            accounts = accounts,
                            remoteAlbums = remoteAlbums,
                            isUploading = isUploading,
                            isPaused = isPaused,
                            isOnline = isOnline,
                            simulateOffline = simulateOffline,
                            simulateMissingAlbum = simulateMissingAlbum,
                            uploadSpeed = uploadSpeed,
                            currentUploadingItem = currentItem,
                            queue = queue,
                            completedCount = completedCount,
                            skippedCount = skippedCount,
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
                            onToggleSimulateOffline = { viewModel.toggleSimulateOffline() },
                            onToggleSimulateMissingAlbum = { viewModel.toggleSimulateMissingAlbum() },
                            onExitMockMode = { viewModel.setMockMode(false) },
                            onStartBackup = { viewModel.startBackup() },
                            onUploadFiles = { uris -> viewModel.uploadSelectedUris(uris, this@MainActivity) },
                            onUploadFilesWithAlbumTarget = { uris, targetAccount, mode, customName, customNickname ->
                                viewModel.uploadSelectedUris(
                                    uris = uris,
                                    context = this@MainActivity,
                                    targetAlbumMode = mode,
                                    customAlbumName = customName,
                                    customAlbumNickname = customNickname,
                                    targetAccount = targetAccount
                                )
                            },
                            onAddAlbum = { key, nick, email -> viewModel.addAlbum(key, nick, email) },
                            onOpenAlbumManager = { showAlbumsScreen = true },
                            onPauseBackup = { viewModel.pauseBackup() },
                            onResumeBackup = { viewModel.resumeBackup() },
                            onCancelBackup = { viewModel.cancelBackup() },
                            onCancelScan = { viewModel.cancelScan() },
                            onClearCompleted = { viewModel.clearCompleted() },
                            onRetryFailed = { viewModel.retryFailedUploads() },
                            onRetryItem = { viewModel.retrySingleItem(it) },
                            triggerPickMediaNonce = pendingPickMediaNonce,
                            onConsumedPickMedia = { pendingPickMediaNonce = 0 },
                            onSelectAccount = { viewModel.selectAccount(it.email) },
                            shouldShowAccountSwipeNudge = !settings.hasShownAccountSwipeNudge,
                            accountSwipeNudgeNonce = mockSwipeNudgeNonce,
                            scrollToAccountForNudge = scrollToAccountForNudge,
                            onConsumedScrollToAccountForNudge = { viewModel.consumeScrollToAccountForNudge() },
                            onAccountSwipeNudgeShown = { viewModel.markAccountSwipeNudgeShown() },
                            onReplayAccountSwipeNudge = { viewModel.replayMockAccountSwipeNudge() },
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
                            externalForcedPunTitle = mockForcedPunTitle,
                            externalEmptyStateSubtitle = mockEmptyStateSubtitle,
                            externalShakeFlickerNonce = mockShakeFlickerNonce,
                            isAlbumsScreenOpen = showAlbumsScreen,
                            modifier = screenModifier
                        )

                        NavDestination.FOLDERS -> FoldersScreen(
                            folders = settings.folders,
                            accounts = accounts,
                            activeAccount = selectedAccount,
                            remoteAlbums = remoteAlbums,
                            discoveredFolders = deviceFolders,
                            isMockMode = isMockMode || isShowcaseMode,
                            onDiscoverDeviceFolders = { viewModel.discoverDeviceFolders() },
                            albumSyncState = albumSyncState,
                            onToggleFolder = { path, email, enabled -> viewModel.toggleFolder(path, email, enabled) },
                            onSetIncludeSubfolders = { path, email, inc -> viewModel.setIncludeSubfolders(path, email, inc) },
                            onAddFolder = { viewModel.addFolder(it) },
                            onRemoveFolder = { path, email -> viewModel.removeFolder(path, email) },
                            onAddAlbum = { key, nick, email -> viewModel.addAlbum(key, nick, email) },
                            onOpenAlbumManager = { showAlbumsScreen = true },
                            onOpenDeviceFolders = {
                                showDeviceFoldersScreen = true
                            },
                            onSyncAllAlbums = { viewModel.syncAllAlbums() },
                            onSyncFolderToAlbum = { folder -> viewModel.syncFolderToAlbum(folder) },
                            onCancelAlbumSync = { viewModel.cancelAlbumSync() },
                            onDismissAlbumSync = { viewModel.dismissAlbumSyncState() },
                            autoSyncAlbums = settings.autoSyncAlbums,
                            isOnline = isOnline && !simulateOffline,
                            modifier = screenModifier
                        )

                        NavDestination.ACCOUNTS -> AccountsScreen(
                            accounts = accounts,
                            selectedAccount = selectedAccount,
                            folders = settings.folders,
                            albums = remoteAlbums,
                            onSelectAccount = { viewModel.selectAccount(it) },
                            onAddAccountToken = { viewModel.addAccountWithToken(it) },
                            onAddAccountCredential = { viewModel.addAccountWithCredential(it) },
                            onUpdateDisplayName = { email, name -> viewModel.updateDisplayName(email, name) },
                            onUpdateAvatar = { email, uri -> viewModel.updateAccountAvatar(email, uri, this@MainActivity) },
                            onRemoveAccount = { viewModel.removeAccount(it) },
                            onClearError = { viewModel.clearAddAccountError() },
                            isAddingAccount = isAddingAccount,
                            addAccountError = addAccountError,
                            isUploading = isUploading,
                            hasAcknowledgedAccountRisk = settings.hasAcknowledgedAccountRisk,
                            onAcknowledgeRisk = { viewModel.acknowledgeAccountRisk() },
                            modifier = screenModifier
                        )

                        NavDestination.SETTINGS -> SettingsScreen(
                            settings = settings,
                            isUploading = isUploading,
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
                            onUpdateDeleteAfterBackup = { viewModel.updateDeleteAfterBackup(it) },
                            onUpdateEnableBlurEffects = { viewModel.updateEnableBlurEffects(it) },
                            onUpdateAutoSyncAlbums = { viewModel.updateAutoSyncAlbums(it) },
                            onUpdateAutoCheckUpdates = { viewModel.setAutoCheckUpdates(it) },
                            onToggleDeveloperMode = { viewModel.updateDeveloperMode(it) },
                            onReplayOnboarding = { viewModel.setHasCompletedOnboarding(false) },
                            onOpenDevDiagnostics = {
                                com.reiux.lumen.util.LumenLogger.i("UI", "Opening Dev Diagnostics")
                                showDevDiagnostics = true
                            },
                            onOpenAlbumManager = { showAlbumsScreen = true },
                            hasUnreadWhatsNew = hasUnreadWhatsNew,
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
                val updateDownloadError by viewModel.updateDownloadError.collectAsState()

                if (showUpdateSheet && updateInfo != null) {
                    com.reiux.lumen.ui.components.UpdateBottomSheet(
                        updateInfo = updateInfo!!,
                        isDownloading = updateDownloadProgress >= 0,
                        downloadProgress = updateDownloadProgress,
                        downloadError = updateDownloadError,
                        onDismiss = { viewModel.dismissUpdateSheet() },
                        onUpdateClick = { viewModel.startUpdateDownload(this@MainActivity) }
                    )
                }

                if (showPostUpdateWhatsNewSheet && !showUpdateSheet && incomingShareBatch == null) {
                    val latestRelease = com.reiux.lumen.data.model.WhatsNewCatalog.releases.firstOrNull()
                    if (latestRelease != null) {
                        com.reiux.lumen.ui.screens.PostUpdateWhatsNewSheet(
                            release = latestRelease,
                            onSeeAll = {
                                viewModel.dismissPostUpdateWhatsNewSheet()
                                showAboutScreen = true
                                showWhatsNewScreen = true
                            },
                            onDismiss = { viewModel.dismissPostUpdateWhatsNewSheet() }
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    val tabTransitionSpec: AnimatedContentTransitionScope<NavDestination>.() -> ContentTransform = {
                        if (reducedMotion) {
                            fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                                fadeOut(animationSpec = motionScheme.defaultEffectsSpec())
                        } else {
                            val isForward = NavDestination.entries.indexOf(targetState) >
                                NavDestination.entries.indexOf(initialState)
                            (fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                                slideInHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { fullWidth ->
                                    val offset = (fullWidth * 0.08f).toInt()
                                    if (isForward) offset else -offset
                                } +
                                scaleIn(
                                    initialScale = 0.96f,
                                    animationSpec = motionScheme.defaultSpatialSpec()
                                ))
                                .togetherWith(
                                    fadeOut(animationSpec = motionScheme.defaultEffectsSpec()) +
                                        slideOutHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { fullWidth ->
                                            val offset = (fullWidth * 0.08f).toInt()
                                            if (isForward) -offset else offset
                                        }
                                )
                        }
                    }

                    val overlayEnter = if (reducedMotion) {
                        fadeIn(animationSpec = motionScheme.defaultEffectsSpec())
                    } else {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = motionScheme.defaultSpatialSpec()
                        ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())
                    }
                    val overlayExit = if (reducedMotion) {
                        fadeOut(animationSpec = motionScheme.defaultEffectsSpec())
                    } else {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = motionScheme.defaultSpatialSpec()
                        ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec())
                    }

                    val underlyingBlurDp = if (settings.enableBlurEffects) (predictiveBackProgress * 20).dp else 0.dp
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (underlyingBlurDp > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.blur(underlyingBlurDp)
                                } else Modifier
                            )
                    ) {
                        AnimatedContent(
                            targetState = settings.hasCompletedOnboarding,
                            transitionSpec = {
                                val enter = if (reducedMotion) {
                                    fadeIn(animationSpec = motionScheme.slowEffectsSpec())
                                } else {
                                    fadeIn(animationSpec = motionScheme.slowEffectsSpec()) +
                                        scaleIn(
                                            initialScale = 0.96f,
                                            animationSpec = motionScheme.slowSpatialSpec()
                                        )
                                }
                                enter togetherWith fadeOut(animationSpec = motionScheme.slowEffectsSpec())
                            },
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
                                            transitionSpec = tabTransitionSpec,
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
                                                transitionSpec = tabTransitionSpec,
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
                        enter = overlayEnter,
                        exit = overlayExit
                    ) {
                        PredictiveBackScreenContainer(
                            enabled = showAboutScreen && !showWhatsNewScreen,
                            onProgressUpdate = { predictiveBackProgress = it },
                            onDismiss = {
                                predictiveBackProgress = 0f
                                com.reiux.lumen.util.LumenLogger.i("Navigation") { "Predictive back: Exiting About screen" }
                                showAboutScreen = false
                            }
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
                                hasUnreadWhatsNew = hasUnreadWhatsNew,
                                onOpenWhatsNew = { showWhatsNewScreen = true },
                                onCheckForUpdates = {
                                    val appCtx = applicationContext
                                    viewModel.checkAndShowUpdate(
                                        context = appCtx,
                                        forceShow = false,
                                        onUpToDate = {
                                            android.widget.Toast.makeText(
                                                appCtx,
                                                appCtx.getString(R.string.update_up_to_date),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onError = {
                                            android.widget.Toast.makeText(
                                                appCtx,
                                                appCtx.getString(R.string.update_check_failed),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                },
                                onForceUpdateDialog = {
                                    val appCtx = applicationContext
                                    viewModel.checkAndShowUpdate(
                                        context = appCtx,
                                        forceShow = true,
                                        onError = {
                                            android.widget.Toast.makeText(
                                                appCtx,
                                                appCtx.getString(R.string.update_check_failed),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                },
                                onNavigateBack = {
                                    predictiveBackProgress = 0f
                                    com.reiux.lumen.util.LumenLogger.i("Navigation") { "Back button: Exiting About screen" }
                                    showAboutScreen = false
                                }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showWhatsNewScreen,
                        enter = overlayEnter,
                        exit = overlayExit
                    ) {
                        PredictiveBackScreenContainer(
                            enabled = showWhatsNewScreen,
                            onProgressUpdate = { predictiveBackProgress = it },
                            onDismiss = {
                                predictiveBackProgress = 0f
                                com.reiux.lumen.util.LumenLogger.i("Navigation") { "Predictive back: Exiting What's New screen" }
                                showWhatsNewScreen = false
                            }
                        ) {
                            com.reiux.lumen.ui.screens.WhatsNewScreen(
                                readItemIds = readWhatsNewIds,
                                onMarkItemRead = { viewModel.markWhatsNewItemRead(it) },
                                onMarkAllRead = { viewModel.markAllWhatsNewRead() },
                                onNavigateBack = {
                                    predictiveBackProgress = 0f
                                    com.reiux.lumen.util.LumenLogger.i("Navigation") { "Back button: Exiting What's New screen" }
                                    showWhatsNewScreen = false
                                }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showDeviceFoldersScreen,
                        enter = overlayEnter,
                        exit = overlayExit
                    ) {
                        PredictiveBackScreenContainer(
                            enabled = showDeviceFoldersScreen && !showAlbumsScreen,
                            onProgressUpdate = { predictiveBackProgress = it },
                            onDismiss = {
                                predictiveBackProgress = 0f
                                com.reiux.lumen.util.LumenLogger.i("Navigation") { "Predictive back: Exiting Device Folders screen" }
                                showDeviceFoldersScreen = false
                            }
                        ) {
                            DeviceFoldersScreen(
                                discoveredFolders = deviceFolders,
                                isDiscovering = isDiscoveringFolders,
                                configuredFolders = settings.folders,
                                accounts = accounts,
                                activeAccount = selectedAccount,
                                remoteAlbums = remoteAlbums,
                                isMockMode = isMockMode || isShowcaseMode,
                                onRefresh = { viewModel.discoverDeviceFolders() },
                                onBack = {
                                    predictiveBackProgress = 0f
                                    com.reiux.lumen.util.LumenLogger.i("Navigation") { "Back button: Exiting Device Folders screen" }
                                    showDeviceFoldersScreen = false
                                },
                                onToggleDiscoveredFolder = { folder, enabled, email ->
                                    viewModel.toggleDiscoveredFolder(folder, enabled, email)
                                },
                                onAssignAlbum = { folder, mode, customName, customNick, email ->
                                    viewModel.assignAlbumToDiscoveredFolder(folder, mode, customName, customNick, email)
                                },
                                onAddCustomFolder = { viewModel.addFolder(it) },
                                onAddAlbum = { key, nick, email -> viewModel.addAlbum(key, nick, email) },
                                onOpenAlbumManager = { showAlbumsScreen = true }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showAlbumsScreen,
                        enter = overlayEnter,
                        exit = overlayExit
                    ) {
                        PredictiveBackScreenContainer(
                            enabled = showAlbumsScreen,
                            onProgressUpdate = { predictiveBackProgress = it },
                            onDismiss = {
                                predictiveBackProgress = 0f
                                com.reiux.lumen.util.LumenLogger.i("Navigation") { "Predictive back: Exiting Album Manager screen" }
                                showAlbumsScreen = false
                            }
                        ) {
                            AlbumsScreen(
                                albums = remoteAlbums,
                                accounts = accounts,
                                activeAccount = selectedAccount,
                                onBack = {
                                    predictiveBackProgress = 0f
                                    com.reiux.lumen.util.LumenLogger.i("Navigation") { "Back button: Exiting Album Manager screen" }
                                    showAlbumsScreen = false
                                },
                                onRemoveAlbum = { album -> viewModel.removeAlbum(album) },
                                onAddAlbum = { keyOrUrl, nickname, targetEmail -> viewModel.addAlbum(keyOrUrl, nickname, targetEmail) }
                            )
                        }
                    }

                    if (showDevDiagnostics) {
                        DevDiagnosticsDialog(
                            selectedAccount = selectedAccount,
                            onRunLoggingSelfTest = { viewModel.runLoggingSelfTest() },
                            onDismiss = { showDevDiagnostics = false }
                        )
                    }

                    val activeBatch = incomingShareBatch
                    val pendingReplacement = queuedShareReplacement
                    if (activeBatch != null && !showAlbumsScreen) {
                        val batch = activeBatch
                        androidx.compose.runtime.key(batch.batchId) {
                            var resolvedFileNames by remember(batch.batchId) { mutableStateOf<List<String>>(emptyList()) }
                            LaunchedEffect(batch.batchId) {
                                resolvedFileNames = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    batch.uris.take(5).map { uri ->
                                        com.reiux.lumen.util.MediaFileReader.getFileNameFromUri(this@MainActivity, uri)
                                    }
                                }
                            }
                            var replacementFileNames by remember(pendingReplacement?.batchId) { mutableStateOf<List<String>>(emptyList()) }
                            LaunchedEffect(pendingReplacement?.batchId) {
                                val replacement = pendingReplacement
                                replacementFileNames = if (replacement == null) {
                                    emptyList()
                                } else {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        replacement.uris.take(3).map { uri ->
                                            com.reiux.lumen.util.MediaFileReader.getFileNameFromUri(this@MainActivity, uri)
                                        }
                                    }
                                }
                            }

                            com.reiux.lumen.ui.components.ManualUploadDestinationDialog(
                                itemCount = batch.uris.size,
                                fileNames = resolvedFileNames,
                                pendingReplacementCount = pendingReplacement?.uris?.size ?: 0,
                                pendingReplacementNames = replacementFileNames,
                                onSwitchToReplacement = {
                                    val nextBatch = viewModel.queuedShareReplacement.value
                                    if (nextBatch != null) {
                                        viewModel.setIncomingShareBatch(nextBatch)
                                        viewModel.setQueuedShareReplacement(null)
                                    }
                                },
                                onDiscardReplacement = {
                                    viewModel.setQueuedShareReplacement(null)
                                },
                                accounts = accounts,
                                activeAccount = selectedAccount,
                                remoteAlbums = remoteAlbums,
                                isOnline = isOnline && !simulateOffline,
                                onAddAlbum = { key, nick, email -> viewModel.addAlbum(key, nick, email) },
                                onOpenAlbumManager = { showAlbumsScreen = true },
                                onConfirmWithAccount = { targetAccount, albumMode, customName, customNickname ->
                                    if (viewModel.incomingShareBatch.value?.batchId == batch.batchId) {
                                        viewModel.uploadSelectedUris(
                                            uris = batch.uris,
                                            context = this@MainActivity,
                                            targetAlbumMode = albumMode,
                                            customAlbumName = customName,
                                            customAlbumNickname = customNickname,
                                            targetAccount = targetAccount
                                        )
                                        viewModel.setIncomingShareBatch(null)
                                        viewModel.setQueuedShareReplacement(null)
                                        intent?.action = null
                                    }
                                },
                                onDismiss = {
                                    viewModel.setIncomingShareBatch(null)
                                    viewModel.setQueuedShareReplacement(null)
                                    intent?.action = null
                                }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isMockMode &&
                            settings.hasCompletedOnboarding &&
                            !showAboutScreen &&
                            !showWhatsNewScreen &&
                            !showDeviceFoldersScreen &&
                            !showAlbumsScreen,
                        enter = if (reducedMotion) {
                            fadeIn(animationSpec = motionScheme.defaultEffectsSpec())
                        } else {
                            fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                                scaleIn(
                                    initialScale = 0.85f,
                                    animationSpec = motionScheme.defaultSpatialSpec()
                                )
                        },
                        exit = if (reducedMotion) {
                            fadeOut(animationSpec = motionScheme.defaultEffectsSpec())
                        } else {
                            fadeOut(animationSpec = motionScheme.defaultEffectsSpec()) +
                                scaleOut(
                                    targetScale = 0.85f,
                                    animationSpec = motionScheme.defaultSpatialSpec()
                                )
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.End + WindowInsetsSides.Bottom
                                )
                            )
                            .padding(end = 16.dp, bottom = if (!useNavRail) 96.dp else 20.dp)
                    ) {
                        ExpressiveExtendedFloatingActionButton(
                            onClick = { showMockControlsSheet = true },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.BugReport,
                                    contentDescription = "Mock Controls"
                                )
                            },
                            text = {
                                Text(
                                    text = "Mock",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    if (showMockControlsSheet && isMockMode) {
                        MockControlsBottomSheet(
                            simulateOffline = simulateOffline,
                            simulateMissingAlbum = simulateMissingAlbum,
                            forcedSeason = settings.forcedSeason,
                            onDismiss = { showMockControlsSheet = false },
                            onExitMockMode = { viewModel.setMockMode(false) },
                            onToggleMockCondition = { viewModel.toggleMockConditionNotice() },
                            onAddMockFiles = { viewModel.addMockUploadItems() },
                            onToggleSimulateOffline = { viewModel.toggleSimulateOffline() },
                            onToggleSimulateMissingAlbum = { viewModel.toggleSimulateMissingAlbum() },
                            onSimulateLargeScan = {
                                viewModel.selectDestination(NavDestination.BACKUP)
                                viewModel.simulateMockLargeScan()
                            },
                            onSimulateBlock = {
                                viewModel.selectDestination(NavDestination.BACKUP)
                                viewModel.simulateMockBlock(it)
                            },
                            onClearBlock = { viewModel.clearMockBlock() },
                            onSimulateUpdateAvailable = { viewModel.simulateUpdateAvailable() },
                            onPreviewWhatsNewSheet = { viewModel.previewPostUpdateWhatsNewSheet() },
                            onReplayAccountSwipeNudge = {
                                viewModel.selectDestination(NavDestination.BACKUP)
                                viewModel.replayMockAccountSwipeNudge()
                            },
                            onTriggerCompletionPun = {
                                viewModel.selectDestination(NavDestination.BACKUP)
                                viewModel.triggerMockCompletionPun()
                            },
                            onCycleEmptyStateVariant = {
                                viewModel.selectDestination(NavDestination.BACKUP)
                                viewModel.cycleMockEmptySubtitle()
                            },
                            onSelectSeason = { viewModel.updateForcedSeason(it) },
                            onTriggerSeasonalGreeting = {
                                com.reiux.lumen.util.EasterEggsManager.checkSeasonalGreeting(
                                    this@MainActivity,
                                    activeSeason,
                                    force = true
                                )
                            },
                            onTriggerLightsOff = { viewModel.triggerMockLightsOff() },
                            onTriggerBoldChoice = {
                                com.reiux.lumen.util.EasterEggsManager.triggerLightsOffBoldChoice(
                                    this@MainActivity,
                                    force = true
                                )
                            },
                            onTriggerShakeFlicker = {
                                viewModel.selectDestination(NavDestination.BACKUP)
                                viewModel.triggerMockShakeFlicker()
                            },
                            onTriggerLateNight = {
                                com.reiux.lumen.util.EasterEggsManager.checkLateNightToast(
                                    this@MainActivity,
                                    force = true
                                )
                            },
                            onTriggerMilestone = {
                                com.reiux.lumen.util.EasterEggsManager.checkMilestoneToast(
                                    this@MainActivity,
                                    it
                                )
                            }
                        )
                    }

                    com.reiux.lumen.ui.components.FallingSnowOverlay(enabled = activeSeason == com.reiux.lumen.util.Season.CHRISTMAS)
                    com.reiux.lumen.ui.components.FlyingBatOverlay(enabled = activeSeason == com.reiux.lumen.util.Season.HALLOWEEN)
                }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasMedia = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            if (!hasMedia) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    companion object {
        internal const val MAX_SHARE_URIS = 100

        internal fun filterAndCapShareUris(
            uris: List<android.net.Uri>,
            selfAuthority: String,
            maxItems: Int = MAX_SHARE_URIS
        ): List<android.net.Uri> {
            if (maxItems <= 0) return emptyList()
            val blockedAuthority = selfAuthority.trim()
            return uris.asSequence()
                .filter { uri ->
                    val scheme = uri.scheme?.lowercase(java.util.Locale.ROOT)
                    val authority = uri.authority?.trim()
                    scheme == "content" &&
                        !authority.isNullOrBlank() &&
                        !authority.equals(blockedAuthority, ignoreCase = true)
                }
                .distinct()
                .take(maxItems)
                .toList()
        }
    }
}
