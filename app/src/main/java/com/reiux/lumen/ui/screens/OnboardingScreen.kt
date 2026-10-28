package com.reiux.lumen.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reiux.lumen.ui.MainViewModel
import com.reiux.lumen.ui.components.AddAccountDialog
import com.reiux.lumen.ui.components.ExpressiveButton
import com.reiux.lumen.ui.components.ExpressiveIconButton
import com.reiux.lumen.ui.components.ExpressiveLoadingIndicator
import com.reiux.lumen.ui.components.ExpressiveSwitch
import com.reiux.lumen.ui.components.ExpressiveTonalButton
import com.reiux.lumen.ui.components.GoogleSignInWebViewDialog
import com.reiux.lumen.ui.components.GroupPosition
import com.reiux.lumen.ui.components.OemBackgroundGuideDialog
import com.reiux.lumen.ui.components.groupedCardShape
import com.reiux.lumen.ui.theme.AvailablePresets
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

    val settings by viewModel.settings.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val isAddingAccount by viewModel.isAddingAccount.collectAsState()
    val addAccountError by viewModel.addAccountError.collectAsState()

    var showSignInWebView by remember { mutableStateOf(false) }
    var showManualTokenDialog by remember { mutableStateOf(false) }

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
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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

    if (showSignInWebView) {
        GoogleSignInWebViewDialog(
            onDismiss = { showSignInWebView = false },
            onTokenCaptured = { token ->
                showSignInWebView = false
                viewModel.addAccountWithToken(token)
            }
        )
    }

    if (showManualTokenDialog) {
        AddAccountDialog(
            onDismiss = { showManualTokenDialog = false },
            onSubmitToken = { token ->
                viewModel.addAccountWithToken(token)
                showManualTokenDialog = false
            },
            onSubmitCredential = { credential ->
                viewModel.addAccountWithCredential(credential)
                showManualTokenDialog = false
            },
            isLoading = isAddingAccount,
            errorMessage = addAccountError,
            isManualOnly = true,
            hasAcknowledgedAccountRisk = settings.hasAcknowledgedAccountRisk,
            onAcknowledgeRisk = { viewModel.acknowledgeAccountRisk() }
        )
    }

    val configuration = LocalConfiguration.current
    val isTwoPane = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        if (isTwoPane) {
            // Split Layout (Landscape / Tablet / Wide Screen)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
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
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (pagerState.currentPage > 0) {
                                ExpressiveIconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back",
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
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                                label = "indicatorWidth"
                            )
                            val color by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                animationSpec = spring(dampingRatio = 1.0f, stiffness = 1600f),
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
                                onRequestPermissions = {
                                    val permissions = mutableListOf<String>()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                                        permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                    permissionLauncher.launch(permissions.toTypedArray())
                                },
                                onRequestBackground = {
                                    DeviceConditions.requestIgnoreBatteryOptimizations(context)
                                },
                                isTwoPane = true
                            )
                            2 -> OnboardingAccountPage(
                                selectedAccount = selectedAccount,
                                hasAcknowledgedAccountRisk = settings.hasAcknowledgedAccountRisk,
                                onAcknowledgeRisk = { viewModel.acknowledgeAccountRisk() },
                                onOpenSignIn = { showSignInWebView = true },
                                onOpenManual = { showManualTokenDialog = true },
                                isTwoPane = true
                            )
                            3 -> OnboardingFoldersPage(
                                folders = settings.folders,
                                onToggleFolder = { path, enabled ->
                                    viewModel.toggleFolder(path, enabled)
                                },
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
                                    pagerState.animateScrollToPage(3)
                                }
                            },
                            onNext = {
                                if (pagerState.currentPage < 5) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
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
            // Single-Column Layout (Portrait)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
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
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
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
                            onRequestPermissions = {
                                val permissions = mutableListOf<String>()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                                    permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                                permissionLauncher.launch(permissions.toTypedArray())
                            },
                            onRequestBackground = {
                                DeviceConditions.requestIgnoreBatteryOptimizations(context)
                            },
                            isTwoPane = false
                        )
                        2 -> OnboardingAccountPage(
                            selectedAccount = selectedAccount,
                            hasAcknowledgedAccountRisk = settings.hasAcknowledgedAccountRisk,
                            onAcknowledgeRisk = { viewModel.acknowledgeAccountRisk() },
                            onOpenSignIn = { showSignInWebView = true },
                            onOpenManual = { showManualTokenDialog = true },
                            isTwoPane = false
                        )
                        3 -> OnboardingFoldersPage(
                            folders = settings.folders,
                            onToggleFolder = { path, enabled ->
                                viewModel.toggleFolder(path, enabled)
                            },
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
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                                label = "indicatorWidth"
                            )
                            val color by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                animationSpec = spring(dampingRatio = 1.0f, stiffness = 1600f),
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
                                pagerState.animateScrollToPage(3)
                            }
                        },
                        onNext = {
                            if (pagerState.currentPage < 5) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
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

@Composable
private fun OnboardingActionButton(
    currentPage: Int,
    hasMediaPermission: Boolean,
    selectedAccount: com.reiux.lumen.data.model.GoogleAccount?,
    onSetAccountLater: () -> Unit,
    onNext: () -> Unit
) {
    when {
        currentPage == 1 && !hasMediaPermission -> {
            // Button hidden until permissions granted
        }
        currentPage == 2 && selectedAccount == null -> {
            ExpressiveTonalButton(
                onClick = onSetAccountLater,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Set Up Account Later",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
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
                    text = when (currentPage) {
                        0 -> "Get Started"
                        5 -> "Start Backing Up"
                        else -> "Continue"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OnboardingStepLeftHeader(page: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(end = 8.dp)
    ) {
        when (page) {
            0 -> {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        com.reiux.lumen.ui.components.DynamicLumenIcon(size = 52.dp)
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Welcome to Lumen",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Unlimited, private Google Photos backup right from your Android device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
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
                                text = "Account Privacy & Security",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Lumen never sees or stores your password. It uses a temporary OAuth token that only permits uploading media directly to Google Photos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
            1 -> {
                Surface(
                    modifier = Modifier.size(76.dp),
                    shape = RoundedCornerShape(24.dp),
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
                    text = "Allow Access",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To find your media and run in the background, Lumen needs a few standard permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            2 -> {
                Surface(
                    modifier = Modifier.size(76.dp),
                    shape = RoundedCornerShape(24.dp),
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
                    text = "Connect Google Account",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sign in to the Google account where your unlimited photos will be stored.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            3 -> {
                Surface(
                    modifier = Modifier.size(76.dp),
                    shape = RoundedCornerShape(24.dp),
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
                    text = "Choose Backup Folders",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select which local media folders should be automatically backed up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            4 -> {
                Surface(
                    modifier = Modifier.size(76.dp),
                    shape = RoundedCornerShape(24.dp),
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
                    text = "Preferences & Style",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Choose your backup rules and pick your favorite theme color.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            5 -> {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(28.dp),
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
                    text = "You're all set",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Lumen is ready to back up your photos and videos in full quality.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

            Surface(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    com.reiux.lumen.ui.components.DynamicLumenIcon(size = 64.dp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Welcome to Lumen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Unlimited, private Google Photos backup right from your Android device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
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
                            text = "Account Privacy & Security",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Lumen never sees or stores your password and has no access to your Google Account. It uses a temporary OAuth token that only permits uploading media directly to Google Photos (photos.googleapis.com).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        FeatureHighlightCard(
            icon = Icons.Rounded.AllInclusive,
            title = "Unlimited Original Quality",
            description = "Uses Pixel original quality so your uploads don't count against your Google One storage."
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureHighlightCard(
            icon = Icons.Rounded.Security,
            title = "Direct and Private",
            description = "Sends files straight from your device to Google. No third-party servers, proxies, or tracking."
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureHighlightCard(
            icon = Icons.Rounded.Sync,
            title = "Background Backup",
            description = "Uploads when charging or on Wi-Fi to keep your camera roll backed up without interruptions."
        )

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
                shape = RoundedCornerShape(28.dp),
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
                text = "Allow Access",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "To find your media and run in the background, Lumen needs a few standard permissions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        PermissionItemCard(
            icon = Icons.Rounded.PhotoLibrary,
            title = "Photos & Videos",
            description = "Needed to read your photos and videos so Lumen can back them up.",
            isGranted = hasMedia,
            onRequest = onRequestPermissions
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionItemCard(
            icon = Icons.Rounded.Notifications,
            title = "Notifications",
            description = "Shows upload progress and backup status in your notification shade.",
            isGranted = hasNotifications,
            onRequest = onRequestPermissions
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionItemCard(
            icon = Icons.Rounded.BatteryChargingFull,
            title = "Background Activity",
            description = "Lets Lumen back up files in the background without being stopped by battery saver.",
            isGranted = hasBackground,
            onRequest = onRequestBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // OEM Auto-Start & Skin Detection Card
        val detectedBrand = remember { OemIntentHelper.detectCurrentBrand() }
        val brandName = remember { detectedBrand.displayName }
        var showOemDialog by remember { mutableStateOf(false) }

        Surface(
            shape = RoundedCornerShape(20.dp),
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
                        text = "$brandName Auto-Start",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (detectedBrand == OemIntentHelper.Brand.XIAOMI) {
                        "HyperOS / MIUI requires enabling 'Autostart' and setting Battery saver to 'No restrictions' in App Info so backups can run in the background."
                    } else if (detectedBrand == OemIntentHelper.Brand.SAMSUNG) {
                        "Samsung One UI requires setting Battery to 'Unrestricted' and adding Lumen to 'Never sleeping apps' in Device Care."
                    } else if (detectedBrand == OemIntentHelper.Brand.ONEPLUS || detectedBrand == OemIntentHelper.Brand.OPPO_REALME) {
                        "ColorOS / OxygenOS requires enabling 'Allow auto-launch' & 'Allow background activity' in Battery usage."
                    } else if (detectedBrand == OemIntentHelper.Brand.HUAWEI_HONOR) {
                        "Huawei / Honor requires setting App Launch to 'Manage manually' and enabling 'Auto-launch' and 'Run in background'."
                    } else if (detectedBrand == OemIntentHelper.Brand.VIVO) {
                        "Vivo / iQOO requires turning on 'Autostart' and 'High background power consumption' for Lumen."
                    } else {
                        "Pixel UI and stock Android run background sync reliably once battery optimization is allowed above."
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
                        Text("Configure $brandName Settings")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { showOemDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("View All Phone Brands Guide", fontSize = 12.sp)
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
    hasAcknowledgedAccountRisk: Boolean,
    onAcknowledgeRisk: () -> Unit,
    onOpenSignIn: () -> Unit,
    onOpenManual: () -> Unit,
    isTwoPane: Boolean = false
) {
    var localAcknowledged by remember(hasAcknowledgedAccountRisk) { mutableStateOf(hasAcknowledgedAccountRisk) }

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
                shape = RoundedCornerShape(28.dp),
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
                text = "Connect Google Account",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Sign in to the Google account where your unlimited photos will be stored.",
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
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = selectedAccount.email.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

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
                        color = MaterialTheme.colorScheme.primaryContainer
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
                                text = "Ready",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onOpenSignIn) {
                Text("Switch or Add Another Account")
            }
        } else {
            ExpressiveButton(
                onClick = onOpenSignIn,
                enabled = localAcknowledged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Sign In with Google", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            ExpressiveTonalButton(
                onClick = onOpenManual,
                enabled = localAcknowledged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Paste Token or Credential Manually", style = MaterialTheme.typography.titleSmall)
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
    onToggleFolder: (String, Boolean) -> Unit,
    isTwoPane: Boolean = false
) {
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
                shape = RoundedCornerShape(28.dp),
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
                text = "Choose Backup Folders",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select which local media folders should be automatically backed up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                folders.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (folder.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
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
                            onCheckedChange = { onToggleFolder(folder.path, it) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You can add custom folders anytime in the Folders tab.",
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
                shape = RoundedCornerShape(28.dp),
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
                text = "Preferences & Style",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Choose your backup rules and pick your favorite theme color.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            text = "Back Up on Wi-Fi Only",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Avoid using cellular mobile data for uploads",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExpressiveSwitch(
                        checked = wifiOnly,
                        onCheckedChange = onUpdateWifiOnly
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AllInclusive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Original Quality (Pixel 1)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (!storageSaver) "Free unlimited original quality storage" else "Compressed Storage Saver quality",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExpressiveSwitch(
                        checked = !storageSaver,
                        onCheckedChange = { onUpdateStorageSaver(!it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Theme selection
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "App Theme Color",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Dynamic theming adapts to your device wallpaper on Android 12+",
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
                shape = RoundedCornerShape(32.dp),
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
                text = "You're all set",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Lumen is ready to back up your photos and videos in full quality.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))
        }

        FeatureHighlightCard(
            icon = Icons.Rounded.AllInclusive,
            title = "Unlimited Backup Ready",
            description = "Uploads won't use up your Google account storage quota."
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureHighlightCard(
            icon = Icons.Rounded.FolderCopy,
            title = "Automatic Sync Ready",
            description = "Your selected folders are watched and will back up in the background."
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureHighlightCard(
            icon = Icons.Rounded.Security,
            title = "Direct Connection",
            description = "All files are sent directly from your phone to Google servers."
        )

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
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
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
    onRequest: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (isGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
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
                    color = MaterialTheme.colorScheme.primaryContainer
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
                            text = "Allowed",
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
                    Text("Allow", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}



private fun checkMediaPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
