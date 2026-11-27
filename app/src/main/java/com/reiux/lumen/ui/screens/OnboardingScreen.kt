package com.reiux.lumen.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.reiux.lumen.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reiux.lumen.ui.MainViewModel
import com.reiux.lumen.ui.components.AddAccountDialog
import com.reiux.lumen.ui.components.AllFilesPermissionDialog
import com.reiux.lumen.ui.components.ExpressiveButton
import com.reiux.lumen.ui.components.ExpressiveIconButton
import com.reiux.lumen.ui.components.ExpressiveLoadingIndicator
import com.reiux.lumen.ui.components.ExpressiveSwitch
import com.reiux.lumen.ui.components.ExpressiveTextButton
import com.reiux.lumen.ui.components.ExpressiveTonalButton
import com.reiux.lumen.ui.components.GoogleSignInWebViewDialog
import com.reiux.lumen.ui.components.GroupPosition
import com.reiux.lumen.ui.components.OemBackgroundGuideDialog
import com.reiux.lumen.ui.components.groupPositionOf
import com.reiux.lumen.ui.components.groupedCardShape
import com.reiux.lumen.ui.components.requiresAllFilesPermission
import com.reiux.lumen.util.DeviceConditions
import com.reiux.lumen.util.LumenLogger
import com.reiux.lumen.util.OemIntentHelper
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 6 })
    val pagerScrollSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    val settings by viewModel.settings.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val isAddingAccount by viewModel.isAddingAccount.collectAsState()
    val addAccountError by viewModel.addAccountError.collectAsState()

    var showSignInWebView by rememberSaveable { mutableStateOf(false) }
    var showManualTokenDialog by rememberSaveable { mutableStateOf(false) }
    var showAddAccountDialog by rememberSaveable { mutableStateOf(false) }
    var hasPendingWebViewSubmit by rememberSaveable { mutableStateOf(false) }
    var showAllFilesDialog by rememberSaveable { mutableStateOf(false) }
    var returningFromAllFilesPermission by rememberSaveable { mutableStateOf(false) }
    var pendingEnableFolderEncoded by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = pagerState.currentPage > 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(
                page = pagerState.currentPage - 1,
                animationSpec = pagerScrollSpec
            )
        }
    }

    // Dynamic permission states
    var hasMediaPermission by remember { mutableStateOf(checkMediaPermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission(context)) }
    var hasBackgroundPermission by remember { mutableStateOf(DeviceConditions.isIgnoringBatteryOptimizations(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMediaPermission = checkMediaPermission(context)
                hasNotificationPermission = checkNotificationPermission(context)
                hasBackgroundPermission = DeviceConditions.isIgnoringBatteryOptimizations(context)
                if (returningFromAllFilesPermission) {
                    returningFromAllFilesPermission = false
                    pendingEnableFolderEncoded?.let { raw ->
                        val parts = raw.split('\u0000', limit = 3)
                        val path = parts[0]
                        val email = parts.getOrNull(1)?.ifEmpty { null }
                        val displayName = parts.getOrNull(2)?.ifEmpty { null }
                        if (!requiresAllFilesPermission(context, path)) {
                            val exists = viewModel.settings.value.folders.any {
                                com.reiux.lumen.data.repository.SettingsRepository.matchesFolderPath(it.path, path) &&
                                    (it.accountEmail.isNullOrBlank() || it.accountEmail.equals(email, ignoreCase = true))
                            }
                            if (exists) {
                                val existingEmail = viewModel.settings.value.folders.firstOrNull {
                                    com.reiux.lumen.data.repository.SettingsRepository.matchesFolderPath(it.path, path) &&
                                        (it.accountEmail.isNullOrBlank() || it.accountEmail.equals(email, ignoreCase = true))
                                }?.accountEmail
                                viewModel.toggleFolder(path, existingEmail, true)
                            } else {
                                viewModel.addFolder(
                                    com.reiux.lumen.data.model.BackupFolder(
                                        path = path,
                                        displayName = displayName ?: java.io.File(path).name,
                                        enabled = true,
                                        accountEmail = email
                                    )
                                )
                            }
                        } else {
                            Toast.makeText(context, R.string.toast_all_files_permission_denied, Toast.LENGTH_SHORT).show()
                        }
                        pendingEnableFolderEncoded = null
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showAllFilesDialog) {
        AllFilesPermissionDialog(
            onConfirm = {
                returningFromAllFilesPermission = true
            },
            onDismiss = {
                showAllFilesDialog = false
                if (!returningFromAllFilesPermission) {
                    pendingEnableFolderEncoded = null
                    Toast.makeText(context, R.string.toast_all_files_permission_denied, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        LumenLogger.i("Onboarding", "Permission request results: $results")
        hasMediaPermission = checkMediaPermission(context)
        hasNotificationPermission = checkNotificationPermission(context)
        hasBackgroundPermission = DeviceConditions.isIgnoringBatteryOptimizations(context)
    }

    LaunchedEffect(pagerState.currentPage) {
        LumenLogger.i("Onboarding", "Active onboarding step: page ${pagerState.currentPage}")
    }

    var isAddSubmitInFlight by rememberSaveable { mutableStateOf(false) }

    if (showSignInWebView) {
        GoogleSignInWebViewDialog(
            onDismiss = { showSignInWebView = false },
            onTokenCaptured = { token ->
                showSignInWebView = false
                hasPendingWebViewSubmit = true
                isAddSubmitInFlight = true
                viewModel.addAccountWithToken(token)
            }
        )
    }

    if (hasPendingWebViewSubmit && !showManualTokenDialog && !showAddAccountDialog && addAccountError != null) {
        com.reiux.lumen.ui.components.ExpressiveAlertDialog(
            onDismissRequest = {
                hasPendingWebViewSubmit = false
                viewModel.clearAddAccountError()
            },
            icon = Icons.Rounded.ErrorOutline,
            title = stringResource(R.string.dialog_auth_error_title),
            tone = com.reiux.lumen.ui.components.ExpressiveDialogTone.ERROR,
            isDestructiveConfirm = false,
            confirmText = stringResource(R.string.btn_try_again),
            onConfirm = {
                viewModel.clearAddAccountError()
                showSignInWebView = true
            },
            dismissText = stringResource(R.string.btn_cancel),
            onDismissAction = {
                hasPendingWebViewSubmit = false
                viewModel.clearAddAccountError()
            }
        ) {
            com.reiux.lumen.ui.components.ExpressiveDialogCallout(
                tone = com.reiux.lumen.ui.components.ExpressiveDialogTone.ERROR
            ) {
                Text(
                    text = addAccountError ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    if (showManualTokenDialog) {
        AddAccountDialog(
            onDismiss = {
                if (!isAddingAccount) {
                    showManualTokenDialog = false
                    viewModel.clearAddAccountError()
                }
            },
            onSubmitToken = { token ->
                isAddSubmitInFlight = true
                viewModel.addAccountWithToken(token)
            },
            onSubmitCredential = { credential ->
                isAddSubmitInFlight = true
                viewModel.addAccountWithCredential(credential)
            },
            onClearError = { viewModel.clearAddAccountError() },
            isLoading = isAddingAccount,
            errorMessage = addAccountError,
            isManualOnly = true,
            hasAcknowledgedAccountRisk = settings.hasAcknowledgedAccountRisk,
            onAcknowledgeRisk = { viewModel.acknowledgeAccountRisk() }
        )
    }

    if (showAddAccountDialog) {
        AddAccountDialog(
            onDismiss = {
                if (!isAddingAccount) {
                    showAddAccountDialog = false
                    viewModel.clearAddAccountError()
                }
            },
            onSubmitToken = { token ->
                isAddSubmitInFlight = true
                viewModel.addAccountWithToken(token)
            },
            onSubmitCredential = { credential ->
                isAddSubmitInFlight = true
                viewModel.addAccountWithCredential(credential)
            },
            onClearError = { viewModel.clearAddAccountError() },
            isLoading = isAddingAccount,
            errorMessage = addAccountError,
            isManualOnly = false,
            hasAcknowledgedAccountRisk = settings.hasAcknowledgedAccountRisk,
            onAcknowledgeRisk = { viewModel.acknowledgeAccountRisk() }
        )
    }

    // Keep the dialog mounted while the submit is in flight (the pasted token / credential
    // must survive a failed attempt). Only close once the ViewModel finishes an error-free
    // submit; a failed submit leaves the error card visible for a retry.
    LaunchedEffect(isAddingAccount, addAccountError, isAddSubmitInFlight) {
        if (!isAddSubmitInFlight || isAddingAccount) return@LaunchedEffect
        isAddSubmitInFlight = false
        if (addAccountError == null) {
            hasPendingWebViewSubmit = false
            showManualTokenDialog = false
            showAddAccountDialog = false
        }
    }

    val onboardingFolders = remember(settings.folders, selectedAccount) {
        val email = selectedAccount?.email
        val scoped = if (!email.isNullOrBlank()) {
            settings.folders.filter { it.accountEmail.isNullOrBlank() || it.accountEmail.equals(email, ignoreCase = true) }
        } else {
            settings.folders
        }.distinctBy { com.reiux.lumen.data.repository.SettingsRepository.normalizeFolderPath(it.path) }

        val existingPaths = scoped.map {
            com.reiux.lumen.data.repository.SettingsRepository.normalizeFolderPath(it.path)
        }.toSet()
        val missingStarters = com.reiux.lumen.data.repository.SettingsRepository.defaultStarterFolders()
            .filterNot {
                com.reiux.lumen.data.repository.SettingsRepository.normalizeFolderPath(it.path) in existingPaths
            }
            .map { it.copy(accountEmail = email) }

        scoped + missingStarters
    }

    val handleOnboardingFolderToggle: (com.reiux.lumen.data.model.BackupFolder, Boolean) -> Unit = { folder, enabled ->
        val effectiveEmail = folder.accountEmail ?: selectedAccount?.email
        val existingMatch = settings.folders.firstOrNull {
            com.reiux.lumen.data.repository.SettingsRepository.matchesFolderPath(it.path, folder.path) &&
                (it.accountEmail.isNullOrBlank() || it.accountEmail.equals(effectiveEmail, ignoreCase = true))
        }
        if (enabled && requiresAllFilesPermission(context, folder.path)) {
            pendingEnableFolderEncoded = "${folder.path}\u0000${effectiveEmail.orEmpty()}\u0000${folder.displayName}"
            showAllFilesDialog = true
        } else if (existingMatch != null) {
            viewModel.toggleFolder(existingMatch.path, existingMatch.accountEmail, enabled)
        } else if (enabled) {
            viewModel.addFolder(folder.copy(enabled = true, accountEmail = effectiveEmail))
        }
    }

    val requestOnboardingPermissions: () -> Unit = {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTwoPane = maxWidth >= 700.dp

            if (isTwoPane) {
                // Split Layout (Landscape / Tablet / Wide Screen)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 1080.dp)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Left Pane: Step Header, Back Button, Hero Illustration, and Pager Indicator
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (pagerState.currentPage > 0) {
                                    ExpressiveIconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(
                                                    page = pagerState.currentPage - 1,
                                                    animationSpec = pagerScrollSpec
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = stringResource(R.string.nav_back),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(40.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OnboardingStepLeftHeader(page = pagerState.currentPage)
                        }

                        // M3E Expressive Spring Pager Indicator
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (pageIndex in 0 until 6) {
                                val isSelected = pagerState.currentPage == pageIndex
                                val width by animateDpAsState(
                                    targetValue = if (isSelected) 28.dp else 8.dp,
                                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                                    label = "indicatorWidth"
                                )
                                val color by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                    label = "indicatorColor"
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .height(8.dp)
                                        .width(width)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                            }
                        }
                    }

                    // Right Pane: Content Page & Action Button
                    Column(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = false,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) { page ->
                            when (page) {
                                0 -> OnboardingWelcomePage(isTwoPane = true)
                                1 -> OnboardingPermissionsPage(
                                    hasMedia = hasMediaPermission,
                                    hasNotifications = hasNotificationPermission,
                                    hasBackground = hasBackgroundPermission,
                                    onRequestPermissions = requestOnboardingPermissions,
                                    onRequestBackground = {
                                        DeviceConditions.requestIgnoreBatteryOptimizations(context)
                                    },
                                    isTwoPane = true
                                )
                                2 -> OnboardingAccountPage(
                                    selectedAccount = selectedAccount,
                                    isAddingAccount = isAddingAccount,
                                    hasAcknowledgedAccountRisk = settings.hasAcknowledgedAccountRisk,
                                    onAcknowledgeRisk = { viewModel.acknowledgeAccountRisk() },
                                    onOpenSignIn = { showSignInWebView = true },
                                    onOpenManual = { showManualTokenDialog = true },
                                    onOpenSwitchOrAddAccount = { showAddAccountDialog = true },
                                    isTwoPane = true
                                )
                                3 -> OnboardingFoldersPage(
                                    folders = onboardingFolders,
                                    onToggleFolder = handleOnboardingFolderToggle,
                                    isTwoPane = true
                                )
                                4 -> OnboardingPreferencesPage(
                                    wifiOnly = settings.wifiOnly,
                                    onUpdateWifiOnly = { viewModel.updateWifiOnly(it) },
                                    storageSaver = settings.storageSaverQuality,
                                    onUpdateStorageSaver = { viewModel.updateStorageSaver(it) },
                                    selectedPreset = settings.colorPreset,
                                    onUpdatePreset = { viewModel.updateColorPreset(it) },
                                    unlockedPrismTheme = settings.unlockedPrismTheme,
                                    unlockedMatrixTheme = settings.unlockedMatrixTheme,
                                    isTwoPane = true
                                )
                                5 -> OnboardingCompletionPage(isTwoPane = true)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            OnboardingActionButton(
                                currentPage = pagerState.currentPage,
                                hasMediaPermission = hasMediaPermission,
                                selectedAccount = selectedAccount,
                                onSetAccountLater = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(
                                            page = 3,
                                            animationSpec = pagerScrollSpec
                                        )
                                    }
                                },
                                onNext = {
                                    if (pagerState.currentPage < 5) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(
                                                page = pagerState.currentPage + 1,
                                                animationSpec = pagerScrollSpec
                                            )
                                        }
                                    } else {
                                        onFinish()
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // Single-Column Layout (Portrait / Compact & Unfolded Foldables)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 600.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        if (pagerState.currentPage > 0) {
                            ExpressiveIconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(
                                            page = pagerState.currentPage - 1,
                                            animationSpec = pagerScrollSpec
                                        )
                                    }
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.nav_back),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(44.dp))
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> OnboardingWelcomePage(isTwoPane = false)
                            1 -> OnboardingPermissionsPage(
                                hasMedia = hasMediaPermission,
                                hasNotifications = hasNotificationPermission,
                                hasBackground = hasBackgroundPermission,
                                onRequestPermissions = requestOnboardingPermissions,
                                onRequestBackground = {
                                    DeviceConditions.requestIgnoreBatteryOptimizations(context)
                                },
                                isTwoPane = false
                            )
                            2 -> OnboardingAccountPage(
                                selectedAccount = selectedAccount,
                                isAddingAccount = isAddingAccount,
                                hasAcknowledgedAccountRisk = settings.hasAcknowledgedAccountRisk,
                                onAcknowledgeRisk = { viewModel.acknowledgeAccountRisk() },
                                onOpenSignIn = { showSignInWebView = true },
                                onOpenManual = { showManualTokenDialog = true },
                                onOpenSwitchOrAddAccount = { showAddAccountDialog = true },
                                isTwoPane = false
                            )
                            3 -> OnboardingFoldersPage(
                                folders = onboardingFolders,
                                onToggleFolder = handleOnboardingFolderToggle,
                                isTwoPane = false
                            )
                            4 -> OnboardingPreferencesPage(
                                wifiOnly = settings.wifiOnly,
                                onUpdateWifiOnly = { viewModel.updateWifiOnly(it) },
                                storageSaver = settings.storageSaverQuality,
                                onUpdateStorageSaver = { viewModel.updateStorageSaver(it) },
                                selectedPreset = settings.colorPreset,
                                onUpdatePreset = { viewModel.updateColorPreset(it) },
                                unlockedPrismTheme = settings.unlockedPrismTheme,
                                unlockedMatrixTheme = settings.unlockedMatrixTheme,
                                isTwoPane = false
                            )
                            5 -> OnboardingCompletionPage(isTwoPane = false)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.padding(bottom = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (pageIndex in 0 until 6) {
                                val isSelected = pagerState.currentPage == pageIndex
                                val width by animateDpAsState(
                                    targetValue = if (isSelected) 28.dp else 8.dp,
                                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                                    label = "indicatorWidth"
                                )
                                val color by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                    label = "indicatorColor"
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .height(8.dp)
                                        .width(width)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                            }
                        }

                        OnboardingActionButton(
                            currentPage = pagerState.currentPage,
                            hasMediaPermission = hasMediaPermission,
                            selectedAccount = selectedAccount,
                            onSetAccountLater = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = 3,
                                        animationSpec = pagerScrollSpec
                                    )
                                }
                            },
                            onNext = {
                                if (pagerState.currentPage < 5) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(
                                            page = pagerState.currentPage + 1,
                                            animationSpec = pagerScrollSpec
                                        )
                                    }
                                } else {
                                    onFinish()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingActionButton(
    currentPage: Int,
    hasMediaPermission: Boolean,
    selectedAccount: com.reiux.lumen.data.model.GoogleAccount?,
    onSetAccountLater: () -> Unit,
    onNext: () -> Unit
) {
    val buttonState = when {
        currentPage == 1 && !hasMediaPermission -> "hidden"
        currentPage == 2 && selectedAccount == null -> "later"
        currentPage == 0 -> "start"
        currentPage == 5 -> "finish"
        else -> "continue"
    }
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val fastSpatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    AnimatedContent(
        targetState = buttonState,
        transitionSpec = {
            (fadeIn(animationSpec = fastEffects) +
                scaleIn(initialScale = 0.96f, animationSpec = fastSpatial)) togetherWith
                fadeOut(animationSpec = fastEffects)
        },
        label = "onboardingActionButton"
    ) { state ->
        when (state) {
            "hidden" -> {
                Box(modifier = Modifier.fillMaxWidth())
            }
            "later" -> {
                ExpressiveTonalButton(
                    onClick = onSetAccountLater,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_btn_set_account_later),
                        style = MaterialTheme.typography.titleMediumEmphasized
                    )
                }
            }
            else -> {
                ExpressiveButton(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = when (state) {
                            "start" -> stringResource(R.string.onboarding_btn_get_started)
                            "finish" -> stringResource(R.string.onboarding_btn_finish)
                            else -> stringResource(R.string.btn_continue)
                        },
                        style = MaterialTheme.typography.titleMediumEmphasized
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedWelcomeLumenIcon(size: Dp) {
    val scale = remember { Animatable(0.75f) }
    val rotation = remember { Animatable(-24f) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, animationSpec = spatialSpec) }
        launch { rotation.animateTo(0f, animationSpec = spatialSpec) }
    }
    com.reiux.lumen.ui.components.DynamicLumenIcon(
        size = size,
        rotationAngle = rotation.value,
        scaleFactor = scale.value
    )
}

@Composable
private fun OnboardingStepLeftHeader(page: Int) {
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            (fadeIn(animationSpec = effectsSpec) +
                scaleIn(initialScale = 0.94f, animationSpec = spatialSpec)) togetherWith
                fadeOut(animationSpec = effectsSpec)
        },
        label = "onboardingLeftHeader"
    ) { targetPage ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(end = 8.dp)
        ) {
            when (targetPage) {
                0 -> {
                    AnimatedWelcomeLumenIcon(size = 68.dp)
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.onboarding_welcome_title),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_welcome_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = groupedCardShape(GroupPosition.Single),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.onboarding_privacy_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.onboarding_privacy_desc_short),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                1 -> {
                    Surface(
                        modifier = Modifier.size(76.dp),
                        shape = MaterialShapes.SoftBurst.toShape(),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.onboarding_permissions_title),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_permissions_setup_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                2 -> {
                    Surface(
                        modifier = Modifier.size(76.dp),
                        shape = MaterialShapes.Cookie9Sided.toShape(),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.onboarding_account_google_title),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_account_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                3 -> {
                    Surface(
                        modifier = Modifier.size(76.dp),
                        shape = MaterialShapes.Clover8Leaf.toShape(),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.folders_title),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.folders_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                4 -> {
                    Surface(
                        modifier = Modifier.size(76.dp),
                        shape = MaterialShapes.Flower.toShape(),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.onboarding_prefs_title),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_prefs_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                5 -> {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = MaterialShapes.Sunny.toShape(),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.onboarding_done_title),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_done_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page 0: Welcome to Lumen
// ---------------------------------------------------------------------------
@Composable
private fun OnboardingWelcomePage(isTwoPane: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTwoPane) 0.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isTwoPane) {
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedWelcomeLumenIcon(size = 84.dp)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_welcome_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = groupedCardShape(GroupPosition.Single),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.onboarding_privacy_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.onboarding_privacy_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            FeatureHighlightCard(
                icon = Icons.Rounded.PhotoLibrary,
                title = stringResource(R.string.onboarding_feat_unlimited_title),
                description = stringResource(R.string.onboarding_feat_unlimited_desc),
                position = GroupPosition.Top
            )
            FeatureHighlightCard(
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.onboarding_feat_direct_title),
                description = stringResource(R.string.onboarding_feat_direct_desc),
                position = GroupPosition.Middle
            )
            FeatureHighlightCard(
                icon = Icons.Rounded.Sync,
                title = stringResource(R.string.onboarding_feat_background_title),
                description = stringResource(R.string.onboarding_feat_background_desc),
                position = GroupPosition.Bottom
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Page 1: Permissions
// ---------------------------------------------------------------------------
@Composable
private fun OnboardingPermissionsPage(
    hasMedia: Boolean,
    hasNotifications: Boolean,
    hasBackground: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestBackground: () -> Unit,
    isTwoPane: Boolean = false
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTwoPane) 0.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isTwoPane) {
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.size(88.dp),
                shape = MaterialShapes.SoftBurst.toShape(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onboarding_permissions_title),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_permissions_setup_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            PermissionItemCard(
                icon = Icons.Rounded.PhotoLibrary,
                title = stringResource(R.string.onboarding_perm_media_title),
                description = stringResource(R.string.device_folders_permission_desc),
                isGranted = hasMedia,
                onRequest = onRequestPermissions,
                position = GroupPosition.Top
            )
            PermissionItemCard(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.onboarding_perm_notifs_title),
                description = stringResource(R.string.pref_upload_notifs_desc),
                isGranted = hasNotifications,
                onRequest = onRequestPermissions,
                position = GroupPosition.Middle
            )
            PermissionItemCard(
                icon = Icons.Rounded.BatteryChargingFull,
                title = stringResource(R.string.pref_background_activity_title),
                description = stringResource(R.string.pref_background_activity_desc),
                isGranted = hasBackground,
                onRequest = onRequestBackground,
                position = GroupPosition.Bottom
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // OEM Background Setup & Skin Detection Card
        val detectedBrand = remember { OemIntentHelper.detectCurrentBrand() }
        val brandName = remember { detectedBrand.displayName }
        var showOemDialog by rememberSaveable { mutableStateOf(false) }

        Surface(
            shape = groupedCardShape(GroupPosition.Single),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_brand_setup_title, brandName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (detectedBrand == OemIntentHelper.Brand.XIAOMI) {
                        stringResource(R.string.onboarding_oem_xiaomi)
                    } else if (detectedBrand == OemIntentHelper.Brand.SAMSUNG) {
                        stringResource(R.string.onboarding_oem_samsung)
                    } else if (detectedBrand == OemIntentHelper.Brand.OPPO_REALME) {
                        stringResource(R.string.onboarding_oem_coloros)
                    } else if (detectedBrand == OemIntentHelper.Brand.HUAWEI_HONOR) {
                        stringResource(R.string.onboarding_oem_magicos)
                    } else if (detectedBrand == OemIntentHelper.Brand.VIVO) {
                        stringResource(R.string.onboarding_oem_originos)
                    } else {
                        stringResource(R.string.onboarding_oem_aosp)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (detectedBrand != OemIntentHelper.Brand.PIXEL_AOSP) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ExpressiveTonalButton(
                        onClick = { OemIntentHelper.openAutoStartSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.onboarding_btn_configure_brand, brandName))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                ExpressiveTextButton(
                    onClick = { showOemDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_btn_view_brand_guide),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        if (showOemDialog) {
            OemBackgroundGuideDialog(onDismiss = { showOemDialog = false })
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Page 2: Connect Account
// ---------------------------------------------------------------------------
@Composable
private fun OnboardingAccountPage(
    selectedAccount: com.reiux.lumen.data.model.GoogleAccount?,
    isAddingAccount: Boolean = false,
    hasAcknowledgedAccountRisk: Boolean,
    onAcknowledgeRisk: () -> Unit,
    onOpenSignIn: () -> Unit,
    onOpenManual: () -> Unit,
    onOpenSwitchOrAddAccount: () -> Unit = onOpenSignIn,
    isTwoPane: Boolean = false
) {
    var localAcknowledged by rememberSaveable(hasAcknowledgedAccountRisk) { mutableStateOf(hasAcknowledgedAccountRisk) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTwoPane) 0.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isTwoPane) {
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.size(88.dp),
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onboarding_account_google_title),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_account_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        com.reiux.lumen.ui.components.AccountRiskCard(
            hasAcknowledged = localAcknowledged,
            onAcknowledge = {
                localAcknowledged = true
                onAcknowledgeRisk()
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (selectedAccount != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = groupedCardShape(GroupPosition.Single),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.reiux.lumen.ui.components.AccountAvatar(
                        account = selectedAccount,
                        size = 48.dp
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedAccount.displayName ?: selectedAccount.email.substringBefore('@'),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = selectedAccount.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

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
                                text = stringResource(R.string.onboarding_status_ready),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ExpressiveTextButton(onClick = onOpenSwitchOrAddAccount) {
                Text(stringResource(R.string.onboarding_switch_or_add_account))
            }
        } else {
            ExpressiveButton(
                onClick = onOpenSignIn,
                enabled = localAcknowledged && !isAddingAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (isAddingAccount) {
                    ExpressiveLoadingIndicator(
                        size = 20.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(stringResource(R.string.btn_sign_in_google), style = MaterialTheme.typography.titleMediumEmphasized)
            }

            Spacer(modifier = Modifier.height(12.dp))

            ExpressiveTonalButton(
                onClick = onOpenManual,
                enabled = localAcknowledged && !isAddingAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.btn_paste_token_manual), style = MaterialTheme.typography.titleSmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Page 3: Folders to Sync
// ---------------------------------------------------------------------------
@Composable
private fun OnboardingFoldersPage(
    folders: List<com.reiux.lumen.data.model.BackupFolder>,
    onToggleFolder: (com.reiux.lumen.data.model.BackupFolder, Boolean) -> Unit,
    isTwoPane: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTwoPane) 0.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isTwoPane) {
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.size(88.dp),
                shape = MaterialShapes.Clover8Leaf.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.folders_title),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.folders_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            folders.forEachIndexed { index, folder ->
                val position = groupPositionOf(index, folders.size)
                val toggleFolderWithHaptic: (Boolean) -> Unit = { next ->
                    try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                    onToggleFolder(folder, next)
                }
                Surface(
                    onClick = { toggleFolderWithHaptic(!folder.enabled) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = groupedCardShape(position),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (folder.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = if (folder.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = folder.path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        ExpressiveSwitch(
                            checked = folder.enabled,
                            onCheckedChange = toggleFolderWithHaptic
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_folders_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Page 4: Preferences & Style
// ---------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingPreferencesPage(
    wifiOnly: Boolean,
    onUpdateWifiOnly: (Boolean) -> Unit,
    storageSaver: Boolean,
    onUpdateStorageSaver: (Boolean) -> Unit,
    selectedPreset: String,
    onUpdatePreset: (String) -> Unit,
    unlockedPrismTheme: Boolean = false,
    unlockedMatrixTheme: Boolean = false,
    isTwoPane: Boolean = false
) {
    val presetsToDisplay = remember(unlockedPrismTheme, unlockedMatrixTheme) {
        com.reiux.lumen.ui.theme.getAvailablePresets(unlockedPrismTheme, unlockedMatrixTheme)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTwoPane) 0.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isTwoPane) {
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.size(88.dp),
                shape = MaterialShapes.Flower.toShape(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onboarding_prefs_title),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_prefs_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        val onboardingHaptic = LocalHapticFeedback.current
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val toggleWifi: (Boolean) -> Unit = { next ->
                try { onboardingHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                onUpdateWifiOnly(next)
            }
            Surface(
                onClick = { toggleWifi(!wifiOnly) },
                modifier = Modifier.fillMaxWidth(),
                shape = groupedCardShape(GroupPosition.Top),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.pref_wifi_only_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.pref_wifi_only_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExpressiveSwitch(
                        checked = wifiOnly,
                        onCheckedChange = toggleWifi
                    )
                }
            }

            val toggleStorageSaver: (Boolean) -> Unit = { next ->
                try { onboardingHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                onUpdateStorageSaver(next)
            }
            Surface(
                onClick = { toggleStorageSaver(!storageSaver) },
                modifier = Modifier.fillMaxWidth(),
                shape = groupedCardShape(GroupPosition.Bottom),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HighQuality,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.pref_storage_saver_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (storageSaver) stringResource(R.string.pref_storage_saver_sub_compressed) else stringResource(R.string.pref_storage_saver_sub_original),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExpressiveSwitch(
                        checked = storageSaver,
                        onCheckedChange = toggleStorageSaver
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Theme selection
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = groupedCardShape(GroupPosition.Single),
            color = MaterialTheme.colorScheme.surfaceContainer
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetsToDisplay.forEach { preset ->
                        PalettePresetCard(
                            preset = preset,
                            isSelected = selectedPreset.equals(preset.name, ignoreCase = true),
                            onClick = { onUpdatePreset(preset.name) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// Page 5: All Set / Ready to Go
// ---------------------------------------------------------------------------
@Composable
private fun OnboardingCompletionPage(isTwoPane: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTwoPane) 0.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isTwoPane) {
            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier.size(96.dp),
                shape = MaterialShapes.Sunny.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onboarding_done_title),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_done_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            FeatureHighlightCard(
                icon = Icons.Rounded.PhotoLibrary,
                title = stringResource(R.string.onboarding_done_feat_quality_title),
                description = stringResource(R.string.onboarding_done_feat_quality_desc),
                position = GroupPosition.Top
            )
            FeatureHighlightCard(
                icon = Icons.Rounded.FolderCopy,
                title = stringResource(R.string.onboarding_done_feat_auto_title),
                description = stringResource(R.string.onboarding_done_feat_auto_desc),
                position = GroupPosition.Middle
            )
            FeatureHighlightCard(
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.onboarding_done_feat_direct_title),
                description = stringResource(R.string.onboarding_done_feat_direct_desc),
                position = GroupPosition.Bottom
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// Helpers and Cards
// ---------------------------------------------------------------------------
@Composable
private fun FeatureHighlightCard(
    icon: ImageVector,
    title: String,
    description: String,
    position: GroupPosition = GroupPosition.Single
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = groupedCardShape(position),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit,
    position: GroupPosition = GroupPosition.Single
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = groupedCardShape(position),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isGranted) {
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
                    onClick = onRequest,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.btn_allow),
                        style = MaterialTheme.typography.labelLargeEmphasized
                    )
                }
            }
        }
    }
}



private fun checkMediaPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
