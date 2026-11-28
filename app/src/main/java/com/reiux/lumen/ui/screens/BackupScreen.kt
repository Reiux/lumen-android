package com.reiux.lumen.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import com.reiux.lumen.R
import com.reiux.lumen.ui.ScanState
import com.reiux.lumen.util.EasterEggsManager
import com.reiux.lumen.util.Season
import com.reiux.lumen.util.rememberShakeDetector
import java.util.Locale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.reiux.lumen.data.model.AccountBlock
import com.reiux.lumen.data.model.AuthFailureKind
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.data.model.UploadItem
import com.reiux.lumen.data.model.UploadStatus
import com.reiux.lumen.ui.components.DynamicLumenIcon
import com.reiux.lumen.ui.components.ExpressiveButton
import com.reiux.lumen.ui.components.ExpressiveIconButton
import com.reiux.lumen.ui.components.ExpressiveLoadingIndicator
import com.reiux.lumen.ui.components.ExpressiveTextButton
import com.reiux.lumen.ui.components.PlayfulParticleBurstOverlay
import com.reiux.lumen.ui.components.ExpressiveTonalButton
import com.reiux.lumen.ui.components.ExpressiveWavyLinearProgress
import com.reiux.lumen.ui.components.GroupPosition
import com.reiux.lumen.ui.components.LocalFoldPosture
import com.reiux.lumen.ui.components.PlayfulCompletionCelebration
import com.reiux.lumen.ui.components.TabletopSplitLayout
import com.reiux.lumen.ui.components.animatedGroupedCardShape
import com.reiux.lumen.ui.components.groupPositionOf
import com.reiux.lumen.ui.components.rememberExpressivePressed
import java.io.File

@Composable
fun BackupScreen(
    selectedAccount: GoogleAccount?,
    accounts: List<GoogleAccount> = emptyList(),
    remoteAlbums: List<com.reiux.lumen.data.network.GoogleAlbum> = emptyList(),
    isUploading: Boolean,
    isPaused: Boolean = false,
    isOnline: Boolean = true,
    simulateOffline: Boolean = false,
    simulateMissingAlbum: Boolean = false,
    currentUploadingItem: UploadItem?,
    queue: List<UploadItem>,
    completedCount: Int,
    skippedCount: Int = 0,
    storageSaverQuality: Boolean = false,
    useQuota: Boolean = false,
    uploadSpeed: String? = null,
    conditionNotice: String? = null,
    scanState: ScanState = ScanState.Idle,
    developerModeUnlocked: Boolean = false,
    isMockMode: Boolean = false,
    hyperdriveAuraEnabled: Boolean = false,
    unlockedPrismTheme: Boolean = false,
    forcedSeason: String = "NONE",
    onToggleMockCondition: () -> Unit = {},
    onAddMockFiles: () -> Unit = {},
    onToggleSimulateOffline: () -> Unit = {},
    onToggleSimulateMissingAlbum: () -> Unit = {},
    onExitMockMode: () -> Unit = {},
    onStartBackup: () -> Unit,
    onUploadFiles: (List<Uri>) -> Unit,
    onUploadFilesWithAlbumTarget: (List<Uri>, GoogleAccount?, com.reiux.lumen.data.model.AlbumTargetMode, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onAddAlbum: (keyOrUrl: String, nickname: String, targetEmail: String?) -> com.reiux.lumen.data.network.GoogleAlbum = { k, n, e -> com.reiux.lumen.data.network.GoogleAlbum(k, n, e) },
    onOpenAlbumManager: () -> Unit = {},
    onPauseBackup: () -> Unit = {},
    onResumeBackup: () -> Unit = {},
    onCancelBackup: () -> Unit = {},
    onCancelScan: () -> Unit = {},
    onClearCompleted: () -> Unit,
    onRetryFailed: () -> Unit = {},
    onRetryItem: (String) -> Unit = {},
    triggerPickMediaNonce: Int = 0,
    onConsumedPickMedia: () -> Unit = {},
    onSelectAccount: (GoogleAccount) -> Unit = {},
    shouldShowAccountSwipeNudge: Boolean = false,
    accountSwipeNudgeNonce: Int = 0,
    scrollToAccountForNudge: Boolean = false,
    onConsumedScrollToAccountForNudge: () -> Unit = {},
    onAccountSwipeNudgeShown: () -> Unit = {},
    onReplayAccountSwipeNudge: () -> Unit = {},
    addAccountError: String? = null,
    isAddingAccount: Boolean = false,
    onAddAccountToken: (String) -> Unit = {},
    onAddAccountCredential: (String) -> Unit = {},
    onRemoveAccount: (String) -> Unit = {},
    onClearAddAccountError: () -> Unit = {},
    onNavigateToAccounts: () -> Unit,
    accountBlock: AccountBlock? = null,
    onUnlockPrismTheme: () -> Unit = {},
    onTriggerLightsOff: () -> Unit = {},
    onSimulateLargeScan: () -> Unit = {},
    onSimulateUpdateAvailable: () -> Unit = {},
    onSimulateBlock: (AuthFailureKind) -> Unit = {},
    onClearBlock: () -> Unit = {},
    isAlbumsScreenOpen: Boolean = false,
    externalForcedPunTitle: String? = null,
    externalEmptyStateSubtitle: String? = null,
    externalShakeFlickerNonce: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSignInDialog by rememberSaveable { mutableStateOf(false) }
    var hasPendingReauth by rememberSaveable { mutableStateOf(false) }
    var showRestrictedRemoveDialog by rememberSaveable { mutableStateOf(false) }
    var showAccountSwitchBlockedDialog by rememberSaveable { mutableStateOf(false) }

    if (showAccountSwitchBlockedDialog) {
        val accountName = selectedAccount?.displayName ?: selectedAccount?.email ?: stringResource(R.string.accounts_active_fallback)
        com.reiux.lumen.ui.components.ExpressiveAlertDialog(
            onDismissRequest = { showAccountSwitchBlockedDialog = false },
            icon = Icons.Rounded.CloudSync,
            title = stringResource(R.string.backup_upload_in_progress_title),
            tone = com.reiux.lumen.ui.components.ExpressiveDialogTone.TERTIARY,
            confirmText = stringResource(R.string.btn_got_it),
            onConfirm = { showAccountSwitchBlockedDialog = false }
        ) {
            Text(
                text = stringResource(R.string.backup_upload_in_progress_msg, accountName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    val handleSelectAccount: (GoogleAccount) -> Unit = { targetAccount ->
        if (isUploading && targetAccount.email != selectedAccount?.email) {
            showAccountSwitchBlockedDialog = true
            Toast.makeText(context, context.getString(R.string.toast_cannot_switch_uploading), Toast.LENGTH_SHORT).show()
        } else {
            onSelectAccount(targetAccount)
        }
    }

    if (showSignInDialog) {
        com.reiux.lumen.ui.components.GoogleSignInWebViewDialog(
            onDismiss = {
                showSignInDialog = false
                onClearAddAccountError()
            },
            onTokenCaptured = {
                hasPendingReauth = true
                onAddAccountToken(it)
                showSignInDialog = false
            },
            disclaimerText = stringResource(R.string.backup_sign_in_prompt, accountBlock?.accountEmail.orEmpty())
        )
    }

    var wasAddingAccount by remember { mutableStateOf(isAddingAccount) }
    LaunchedEffect(accountBlock, isAddingAccount, addAccountError) {
        val finishedAddingAccount = wasAddingAccount && !isAddingAccount
        wasAddingAccount = isAddingAccount
        if (addAccountError == null && (accountBlock == null || finishedAddingAccount)) {
            hasPendingReauth = false
        }
    }

    if (hasPendingReauth && addAccountError != null) {
        com.reiux.lumen.ui.components.ExpressiveAlertDialog(
            onDismissRequest = {
                hasPendingReauth = false
                onClearAddAccountError()
            },
            icon = Icons.Rounded.ErrorOutline,
            title = stringResource(R.string.dialog_auth_error_title),
            tone = com.reiux.lumen.ui.components.ExpressiveDialogTone.ERROR,
            isDestructiveConfirm = false,
            confirmText = stringResource(R.string.btn_try_again),
            onConfirm = {
                onClearAddAccountError()
                showSignInDialog = true
            },
            dismissText = stringResource(R.string.btn_cancel),
            onDismissAction = {
                hasPendingReauth = false
                onClearAddAccountError()
            }
        ) {
            com.reiux.lumen.ui.components.ExpressiveDialogCallout(
                tone = com.reiux.lumen.ui.components.ExpressiveDialogTone.ERROR
            ) {
                Text(
                    text = addAccountError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    if (showRestrictedRemoveDialog && accountBlock != null) {
        com.reiux.lumen.ui.components.ExpressiveAlertDialog(
            onDismissRequest = { showRestrictedRemoveDialog = false },
            icon = Icons.Rounded.PersonOff,
            title = stringResource(R.string.dialog_remove_restricted_title),
            tone = com.reiux.lumen.ui.components.ExpressiveDialogTone.ERROR,
            confirmText = stringResource(R.string.btn_remove),
            onConfirm = {
                onRemoveAccount(accountBlock.accountEmail)
                showRestrictedRemoveDialog = false
                onClearBlock()
            },
            dismissText = stringResource(R.string.btn_cancel)
        ) {
            Text(
                text = stringResource(R.string.dialog_remove_restricted_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    var pendingPickedUriStrings by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    val pendingPickedUris = remember(pendingPickedUriStrings) {
        pendingPickedUriStrings?.map { Uri.parse(it) }
    }
    var pendingPickedFileNames by remember(pendingPickedUriStrings) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(pendingPickedUriStrings) {
        val uris = pendingPickedUris
        pendingPickedFileNames = if (uris.isNullOrEmpty()) {
            emptyList()
        } else {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                uris.take(5).map { uri ->
                    com.reiux.lumen.util.MediaFileReader.getFileNameFromUri(context, uri)
                }
            }
        }
    }
    val actualIsOffline = !isOnline || simulateOffline

    if (pendingPickedUris != null && !isAlbumsScreenOpen) {
        com.reiux.lumen.ui.components.ManualUploadDestinationDialog(
            itemCount = pendingPickedUris.size,
            fileNames = pendingPickedFileNames,
            accounts = accounts,
            activeAccount = selectedAccount,
            remoteAlbums = remoteAlbums,
            isOnline = !actualIsOffline,
            onAddAlbum = onAddAlbum,
            onOpenAlbumManager = onOpenAlbumManager,
            onConfirm = { albumMode, customName, customNickname ->
                onUploadFilesWithAlbumTarget(pendingPickedUris, selectedAccount, albumMode, customName, customNickname)
                pendingPickedUriStrings = null
            },
            onConfirmWithAccount = { targetAccount, albumMode, customName, customNickname ->
                onUploadFilesWithAlbumTarget(pendingPickedUris, targetAccount, albumMode, customName, customNickname)
                pendingPickedUriStrings = null
            },
            onDismiss = { pendingPickedUriStrings = null }
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingPickedUriStrings = ArrayList(uris.map { it.toString() })
        }
    }

    LaunchedEffect(triggerPickMediaNonce) {
        if (triggerPickMediaNonce > 0) {
            onConsumedPickMedia()
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingPickedUriStrings = ArrayList(uris.map { it.toString() })
        }
    }

    var triggerShakeFlicker by remember { mutableStateOf(false) }

    rememberShakeDetector {
        triggerShakeFlicker = true
    }

    LaunchedEffect(externalShakeFlickerNonce) {
        if (externalShakeFlickerNonce > 0) {
            triggerShakeFlicker = true
        }
    }

    val flickerAlpha = remember { Animatable(1f) }
    val flickerSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(triggerShakeFlicker) {
        if (triggerShakeFlicker) {
            val animScale = try {
                android.provider.Settings.Global.getFloat(
                    context.contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                )
            } catch (_: Exception) {
                1f
            }
            if (animScale > 0f) {
                flickerAlpha.animateTo(0.2f, flickerSpec)
                flickerAlpha.animateTo(1.0f, flickerSpec)
                flickerAlpha.animateTo(0.3f, flickerSpec)
                flickerAlpha.animateTo(1.0f, flickerSpec)
            }
            triggerShakeFlicker = false
        }
    }

    val effectiveForcedPunTitle = if (isMockMode) externalForcedPunTitle else null
    val showMockCompletionCelebration = effectiveForcedPunTitle != null
    val activeSeason = EasterEggsManager.resolveActiveSeason(forcedSeason)

    LaunchedEffect(activeSeason) {
        if (activeSeason != Season.NONE && !isMockMode) {
            EasterEggsManager.checkSeasonalGreeting(context, activeSeason)
        }
    }

    val missingAlbum = remoteAlbums.find { it.isMissing }
    val hasMissingAlbum = simulateMissingAlbum || queue.any { it.status == UploadStatus.STOPPED }

    val twoPaneQueueListState = rememberLazyListState()
    val tabletopQueueListState = rememberLazyListState()
    val portraitListState = rememberLazyListState()
    val leftScrollState = rememberScrollState()
    val tabletopBottomScrollState = rememberScrollState()
    var accountCardTopPx by remember { mutableIntStateOf(0) }
    var selectedQueueStatusFilter by rememberSaveable { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val foldPosture = LocalFoldPosture.current
        val isTabletop = foldPosture.isTabletop && maxHeight >= 520.dp
        val isTwoPane = maxWidth >= 640.dp
        val isTallViewport = maxHeight >= 560.dp

        LaunchedEffect(scrollToAccountForNudge, isTwoPane, isTabletop) {
            if (scrollToAccountForNudge) {
                if (isTabletop || isTwoPane) {
                    leftScrollState.animateScrollTo(accountCardTopPx)
                } else {
                    portraitListState.animateScrollToItem(1)
                }
                onConsumedScrollToAccountForNudge()
            }
        }

        if (isTabletop) {
            // Tabletop Fold Mode: Hero Dashboard on angled upper display, Recent Activity Queue on flat lower touch deck
            TabletopSplitLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer { alpha = flickerAlpha.value },
                topContent = {
                    if (isTwoPane) {
                        // Wide book-style foldable: 2-column Hero Dashboard (brand/account top-left, Hero card right)
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .widthIn(max = 960.dp)
                                .fillMaxSize()
                                .padding(top = 6.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(0.95f)
                                    .fillMaxHeight()
                                    .verticalScroll(leftScrollState),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                BackupTopHeader(
                                    storageSaverQuality = storageSaverQuality,
                                    useQuota = useQuota,
                                    unlockedPrismTheme = unlockedPrismTheme,
                                    activeSeason = activeSeason,
                                    onUnlockPrismTheme = onUnlockPrismTheme
                                )

                                AccountStatusCard(
                                    selectedAccount = selectedAccount,
                                    accounts = accounts,
                                    shouldShowSwipeNudge = shouldShowAccountSwipeNudge,
                                    nudgeReplayNonce = accountSwipeNudgeNonce,
                                    onSwipeNudgeShown = onAccountSwipeNudgeShown,
                                    onSelectAccount = handleSelectAccount,
                                    onNavigateToAccounts = onNavigateToAccounts,
                                    modifier = Modifier.onGloballyPositioned { accountCardTopPx = it.positionInParent().y.toInt() }
                                )

                                if (accountBlock != null) {
                                    com.reiux.lumen.ui.components.AccountBlockCard(
                                        block = accountBlock,
                                        onSignInAgain = { showSignInDialog = true },
                                        onRemoveAccount = { showRestrictedRemoveDialog = true },
                                        onClearBlock = onClearBlock
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1.05f)
                                    .fillMaxHeight()
                                    .verticalScroll(tabletopBottomScrollState),
                                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                            ) {
                                HeroBackupCard(
                                    isUploading = isUploading,
                                    isPaused = isPaused,
                                    uploadSpeed = uploadSpeed,
                                    currentUploadingItem = currentUploadingItem,
                                    queue = queue,
                                    completedCount = completedCount,
                                    skippedCount = skippedCount,
                                    conditionNotice = conditionNotice,
                                    hasMissingAlbum = hasMissingAlbum,
                                    missingAlbum = missingAlbum,
                                    isOffline = actualIsOffline,
                                    onOpenAlbums = onOpenAlbumManager,
                                    scanState = scanState,
                                    accountBlock = null,
                                    showMockCompletionCelebration = showMockCompletionCelebration,
                                    mockForcedPunTitle = effectiveForcedPunTitle,
                                    selectedAccount = selectedAccount,
                                    onStartBackup = onStartBackup,
                                    onPauseBackup = onPauseBackup,
                                    onResumeBackup = onResumeBackup,
                                    onCancelBackup = onCancelBackup,
                                    onCancelScan = onCancelScan,
                                    onSignInAgain = { showSignInDialog = true },
                                    onRemoveRestrictedAccount = { showRestrictedRemoveDialog = true },
                                    onClearBlock = onClearBlock,
                                    onPickPhotos = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    },
                                    onPickFiles = {
                                        documentPickerLauncher.launch(arrayOf("image/*", "video/*"))
                                    }
                                )
                            }
                        }
                    } else {
                        // Narrow clamshell Flip: top-aligned scrollable Hero Dashboard column
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .widthIn(max = 680.dp)
                                .fillMaxSize()
                                .verticalScroll(leftScrollState)
                                .padding(top = 6.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BackupTopHeader(
                                storageSaverQuality = storageSaverQuality,
                                useQuota = useQuota,
                                unlockedPrismTheme = unlockedPrismTheme,
                                activeSeason = activeSeason,
                                onUnlockPrismTheme = onUnlockPrismTheme
                            )

                            AccountStatusCard(
                                selectedAccount = selectedAccount,
                                accounts = accounts,
                                shouldShowSwipeNudge = shouldShowAccountSwipeNudge,
                                nudgeReplayNonce = accountSwipeNudgeNonce,
                                onSwipeNudgeShown = onAccountSwipeNudgeShown,
                                onSelectAccount = handleSelectAccount,
                                onNavigateToAccounts = onNavigateToAccounts,
                                modifier = Modifier.onGloballyPositioned { accountCardTopPx = it.positionInParent().y.toInt() }
                            )

                            if (accountBlock != null) {
                                com.reiux.lumen.ui.components.AccountBlockCard(
                                    block = accountBlock,
                                    onSignInAgain = { showSignInDialog = true },
                                    onRemoveAccount = { showRestrictedRemoveDialog = true },
                                    onClearBlock = onClearBlock
                                )
                            }

                            HeroBackupCard(
                                isUploading = isUploading,
                                isPaused = isPaused,
                                uploadSpeed = uploadSpeed,
                                currentUploadingItem = currentUploadingItem,
                                queue = queue,
                                completedCount = completedCount,
                                skippedCount = skippedCount,
                                conditionNotice = conditionNotice,
                                hasMissingAlbum = hasMissingAlbum,
                                missingAlbum = missingAlbum,
                                isOffline = actualIsOffline,
                                onOpenAlbums = onOpenAlbumManager,
                                scanState = scanState,
                                accountBlock = null,
                                showMockCompletionCelebration = showMockCompletionCelebration,
                                mockForcedPunTitle = effectiveForcedPunTitle,
                                selectedAccount = selectedAccount,
                                onStartBackup = onStartBackup,
                                onPauseBackup = onPauseBackup,
                                onResumeBackup = onResumeBackup,
                                onCancelBackup = onCancelBackup,
                                onCancelScan = onCancelScan,
                                onSignInAgain = { showSignInDialog = true },
                                onRemoveRestrictedAccount = { showRestrictedRemoveDialog = true },
                                onClearBlock = onClearBlock,
                                onPickPhotos = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                onPickFiles = {
                                    documentPickerLauncher.launch(arrayOf("image/*", "video/*"))
                                }
                            )
                        }
                    }
                },
                bottomContent = {
                    // Bottom flat touch deck: Recent Activity Queue
                    RecentActivityPane(
                        queue = queue,
                        accounts = accounts,
                        selectedQueueStatusFilter = selectedQueueStatusFilter,
                        onSelectQueueStatusFilter = { selectedQueueStatusFilter = it },
                        onRetryFailed = onRetryFailed,
                        onClearCompleted = onClearCompleted,
                        onRetryItem = onRetryItem,
                        listState = tabletopQueueListState,
                        isMockMode = isMockMode,
                        externalEmptyStateSubtitle = externalEmptyStateSubtitle,
                        isEmbeddedInScroll = false,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = if (isTwoPane) 960.dp else 680.dp)
                            .fillMaxSize()
                            .padding(top = 4.dp)
                    )
                }
            )
        } else if (isTwoPane) {
            // Tablet / Landscape 2-Pane Adaptive Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = 1080.dp)
                    .padding(horizontal = 16.dp)
                    .graphicsLayer { alpha = flickerAlpha.value },
                horizontalArrangement = Arrangement.spacedBy(if (isTallViewport) 28.dp else 16.dp)
            ) {
            // Left Pane: Controls & Hero Status
            Column(
                modifier = Modifier
                    .weight(if (isTallViewport) 1f else 0.48f)
                    .fillMaxHeight()
                    .padding(top = 8.dp)
                    .verticalScroll(leftScrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BackupTopHeader(
                    storageSaverQuality = storageSaverQuality,
                    useQuota = useQuota,
                    unlockedPrismTheme = unlockedPrismTheme,
                    activeSeason = activeSeason,
                    onUnlockPrismTheme = onUnlockPrismTheme
                )

                AccountStatusCard(
                    selectedAccount = selectedAccount,
                    accounts = accounts,
                    shouldShowSwipeNudge = shouldShowAccountSwipeNudge,
                    nudgeReplayNonce = accountSwipeNudgeNonce,
                    onSwipeNudgeShown = onAccountSwipeNudgeShown,
                    onSelectAccount = handleSelectAccount,
                    onNavigateToAccounts = onNavigateToAccounts,
                    modifier = Modifier.onGloballyPositioned { accountCardTopPx = it.positionInParent().y.toInt() }
                )

                HeroBackupCard(
                    isUploading = isUploading,
                    isPaused = isPaused,
                    uploadSpeed = uploadSpeed,
                    currentUploadingItem = currentUploadingItem,
                    queue = queue,
                    completedCount = completedCount,
                    skippedCount = skippedCount,
                    conditionNotice = conditionNotice,
                    hasMissingAlbum = hasMissingAlbum,
                    missingAlbum = missingAlbum,
                    isOffline = actualIsOffline,
                    onOpenAlbums = onOpenAlbumManager,
                    scanState = scanState,
                    accountBlock = accountBlock,
                    showMockCompletionCelebration = showMockCompletionCelebration,
                    mockForcedPunTitle = effectiveForcedPunTitle,
                    selectedAccount = selectedAccount,
                    onStartBackup = onStartBackup,
                    onPauseBackup = onPauseBackup,
                    onResumeBackup = onResumeBackup,
                    onCancelBackup = onCancelBackup,
                    onCancelScan = onCancelScan,
                    onSignInAgain = { showSignInDialog = true },
                    onRemoveRestrictedAccount = { showRestrictedRemoveDialog = true },
                    onClearBlock = onClearBlock,
                    onPickPhotos = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onPickFiles = {
                        documentPickerLauncher.launch(arrayOf("image/*", "video/*"))
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Right Pane: Upload Queue & Recent Activity
            RecentActivityPane(
                queue = queue,
                accounts = accounts,
                selectedQueueStatusFilter = selectedQueueStatusFilter,
                onSelectQueueStatusFilter = { selectedQueueStatusFilter = it },
                onRetryFailed = onRetryFailed,
                onClearCompleted = onClearCompleted,
                onRetryItem = onRetryItem,
                listState = twoPaneQueueListState,
                isMockMode = isMockMode,
                externalEmptyStateSubtitle = externalEmptyStateSubtitle,
                modifier = Modifier
                    .weight(if (isTallViewport) 1f else 0.52f)
                    .fillMaxHeight()
                    .padding(top = 8.dp)
            )
        }
    } else {
        // Single-Column Layout (Phones & Narrow Foldables)
        LazyColumn(
            state = portraitListState,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp)
                .graphicsLayer { alpha = flickerAlpha.value },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                BackupTopHeader(
                    storageSaverQuality = storageSaverQuality,
                    useQuota = useQuota,
                    unlockedPrismTheme = unlockedPrismTheme,
                    activeSeason = activeSeason,
                    onUnlockPrismTheme = onUnlockPrismTheme
                )
            }

            item {
                AccountStatusCard(
                    selectedAccount = selectedAccount,
                    accounts = accounts,
                    shouldShowSwipeNudge = shouldShowAccountSwipeNudge,
                    nudgeReplayNonce = accountSwipeNudgeNonce,
                    onSwipeNudgeShown = onAccountSwipeNudgeShown,
                    onSelectAccount = handleSelectAccount,
                    onNavigateToAccounts = onNavigateToAccounts
                )
            }

            item {
                HeroBackupCard(
                    isUploading = isUploading,
                    isPaused = isPaused,
                    uploadSpeed = uploadSpeed,
                    currentUploadingItem = currentUploadingItem,
                    queue = queue,
                    completedCount = completedCount,
                    skippedCount = skippedCount,
                    conditionNotice = conditionNotice,
                    hasMissingAlbum = hasMissingAlbum,
                    missingAlbum = missingAlbum,
                    isOffline = actualIsOffline,
                    onOpenAlbums = onOpenAlbumManager,
                    scanState = scanState,
                    accountBlock = accountBlock,
                    showMockCompletionCelebration = showMockCompletionCelebration,
                    mockForcedPunTitle = effectiveForcedPunTitle,
                    selectedAccount = selectedAccount,
                    onStartBackup = onStartBackup,
                    onPauseBackup = onPauseBackup,
                    onResumeBackup = onResumeBackup,
                    onCancelBackup = onCancelBackup,
                    onCancelScan = onCancelScan,
                    onSignInAgain = { showSignInDialog = true },
                    onRemoveRestrictedAccount = { showRestrictedRemoveDialog = true },
                    onClearBlock = onClearBlock,
                    onPickPhotos = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onPickFiles = {
                        documentPickerLauncher.launch(arrayOf("image/*", "video/*"))
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.backup_recent_activity, queue.size),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val hasFailed = queue.any { it.status == UploadStatus.FAILED || it.status == UploadStatus.STOPPED }
                        if (hasFailed) {
                            val failedCount = queue.count { it.status == UploadStatus.FAILED || it.status == UploadStatus.STOPPED }
                            ExpressiveTextButton(
                                onClick = onRetryFailed,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_retry_failed_count, failedCount), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        if (queue.any { it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED || it.status == UploadStatus.FAILED || it.status == UploadStatus.STOPPED }) {
                            ExpressiveTextButton(
                                onClick = onClearCompleted,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(stringResource(R.string.btn_clear))
                            }
                        }
                    }
                }
            }

            if (queue.isNotEmpty()) {
                item {
                    QueueFilterChips(
                        queue = queue,
                        selectedFilter = selectedQueueStatusFilter,
                        onSelectFilter = { selectedQueueStatusFilter = it }
                    )
                }
            }

            if (queue.isEmpty() || (isMockMode && externalEmptyStateSubtitle != null)) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = MaterialShapes.Clover8Leaf.toShape(),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.backup_queue_empty_title),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val emptyStateSubtitle = remember(queue.size) {
                                EasterEggsManager.getEmptyStateSubtitle(context)
                            }
                            val effectiveEmptySubtitle = if (isMockMode) {
                                externalEmptyStateSubtitle ?: emptyStateSubtitle
                            } else {
                                emptyStateSubtitle
                            }
                            Text(
                                text = effectiveEmptySubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    val motionScheme = MaterialTheme.motionScheme
                    AnimatedContent(
                        targetState = selectedQueueStatusFilter,
                        transitionSpec = {
                            (fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                             slideInVertically(animationSpec = motionScheme.fastSpatialSpec()) { height -> height / 6 })
                                .togetherWith(
                                    fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                                    slideOutVertically(animationSpec = motionScheme.fastSpatialSpec()) { height -> -height / 6 }
                                )
                        },
                        label = "queueFilterAnimationPortrait"
                    ) { activeFilter ->
                        val currentFilteredQueue = remember(queue, activeFilter) {
                            when (activeFilter) {
                                "uploading" -> queue.filter {
                                    it.status == UploadStatus.UPLOADING ||
                                    it.status == UploadStatus.HASHING ||
                                    it.status == UploadStatus.CHECKING ||
                                    it.status == UploadStatus.COMMITTING
                                }
                                "failed" -> queue.filter { it.status == UploadStatus.FAILED || it.status == UploadStatus.STOPPED }
                                "done" -> queue.filter { it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED }
                                else -> queue
                            }
                        }

                        if (currentFilteredQueue.isEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.filter_no_items_match),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            val displayQueue = remember(currentFilteredQueue) {
                                currentFilteredQueue.takeLast(25).reversed()
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                displayQueue.forEachIndexed { index, item ->
                                    androidx.compose.runtime.key(item.id) {
                                        UploadItemRow(
                                            item = item,
                                            position = groupPositionOf(index, displayQueue.size),
                                            accounts = accounts,
                                            showAccountBadge = accounts.size > 1,
                                            onRetry = { onRetryItem(item.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(if (isMockMode) 88.dp else 16.dp))
            }
        }
        }
    }
}

@Composable
private fun RecentActivityPane(
    queue: List<UploadItem>,
    accounts: List<GoogleAccount>,
    selectedQueueStatusFilter: String?,
    onSelectQueueStatusFilter: (String?) -> Unit,
    onRetryFailed: () -> Unit,
    onClearCompleted: () -> Unit,
    onRetryItem: (String) -> Unit,
    listState: LazyListState,
    isMockMode: Boolean,
    externalEmptyStateSubtitle: String?,
    modifier: Modifier = Modifier,
    isEmbeddedInScroll: Boolean = false
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.backup_recent_activity, queue.size),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                val hasFailed = queue.any { it.status == UploadStatus.FAILED || it.status == UploadStatus.STOPPED }
                if (hasFailed) {
                    val failedCount = queue.count { it.status == UploadStatus.FAILED || it.status == UploadStatus.STOPPED }
                    ExpressiveTextButton(
                        onClick = onRetryFailed,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_retry_failed_count, failedCount), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                if (queue.any { it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED || it.status == UploadStatus.FAILED || it.status == UploadStatus.STOPPED }) {
                    ExpressiveTextButton(
                        onClick = onClearCompleted,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.btn_clear))
                    }
                }
            }
        }

        if (queue.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            QueueFilterChips(
                queue = queue,
                selectedFilter = selectedQueueStatusFilter,
                onSelectFilter = onSelectQueueStatusFilter
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (queue.isEmpty() || (isMockMode && externalEmptyStateSubtitle != null)) {
            Column(
                modifier = if (isEmbeddedInScroll) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = MaterialShapes.Clover8Leaf.toShape(),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.backup_queue_empty_title),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val emptyStateSubtitle = remember(queue.size) {
                            EasterEggsManager.getEmptyStateSubtitle(context)
                        }
                        val effectiveEmptySubtitle = if (isMockMode) {
                            externalEmptyStateSubtitle ?: emptyStateSubtitle
                        } else {
                            emptyStateSubtitle
                        }
                        Text(
                            text = effectiveEmptySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(if (isMockMode) 88.dp else 16.dp))
            }
        } else {
            val motionScheme = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = selectedQueueStatusFilter,
                transitionSpec = {
                    (fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                     slideInVertically(animationSpec = motionScheme.fastSpatialSpec()) { height -> height / 6 })
                        .togetherWith(
                            fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                            slideOutVertically(animationSpec = motionScheme.fastSpatialSpec()) { height -> -height / 6 }
                        )
                },
                label = "queueFilterAnimation"
            ) { activeFilter ->
                val currentFilteredQueue = remember(queue, activeFilter) {
                    when (activeFilter) {
                        "uploading" -> queue.filter {
                            it.status == UploadStatus.UPLOADING ||
                            it.status == UploadStatus.HASHING ||
                            it.status == UploadStatus.CHECKING ||
                            it.status == UploadStatus.COMMITTING
                        }
                        "failed" -> queue.filter { it.status == UploadStatus.FAILED || it.status == UploadStatus.STOPPED }
                        "done" -> queue.filter { it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED }
                        else -> queue
                    }
                }

                if (currentFilteredQueue.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.filter_no_items_match),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (isEmbeddedInScroll) {
                    val displayQueue = remember(currentFilteredQueue) {
                        currentFilteredQueue.takeLast(50).reversed()
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        displayQueue.forEachIndexed { index, item ->
                            androidx.compose.runtime.key(item.id) {
                                UploadItemRow(
                                    item = item,
                                    position = groupPositionOf(index, displayQueue.size),
                                    accounts = accounts,
                                    showAccountBadge = accounts.size > 1,
                                    onRetry = { onRetryItem(item.id) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(if (isMockMode) 88.dp else 16.dp))
                    }
                } else {
                    val displayQueue = remember(currentFilteredQueue) {
                        currentFilteredQueue.takeLast(50).reversed()
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(displayQueue, key = { _, item -> item.id }) { index, item ->
                            UploadItemRow(
                                item = item,
                                position = groupPositionOf(index, displayQueue.size),
                                accounts = accounts,
                                showAccountBadge = accounts.size > 1,
                                onRetry = { onRetryItem(item.id) }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(if (isMockMode) 88.dp else 16.dp))
                        }
                    }
                }
            }
        }
    }
}

private var hasPlayedBootAnimation = false

@Composable
private fun BackupTopHeader(
    storageSaverQuality: Boolean,
    useQuota: Boolean,
    unlockedPrismTheme: Boolean = false,
    activeSeason: Season = Season.NONE,
    onUnlockPrismTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    var showBurst by remember { mutableStateOf(false) }

    var tapSpinAngle by remember { mutableFloatStateOf(0f) }
    val animatedTapSpin by animateFloatAsState(
        targetValue = tapSpinAngle,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "iconTapSpin"
    )
    val wordmarkStretchX = remember { Animatable(1.0f) }

    LaunchedEffect(Unit) {
        if (!hasPlayedBootAnimation) {
            hasPlayedBootAnimation = true
            delay(250L) // Animate once on initial app boot
            tapSpinAngle += 360f // Spin the logo 360 on boot
            wordmarkStretchX.animateTo(1.35f, motionScheme.fastSpatialSpec())
            wordmarkStretchX.animateTo(1.0f, motionScheme.defaultSpatialSpec())
        }
    }

    val onBrandTap: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        tapSpinAngle += 360f
        coroutineScope.launch {
            wordmarkStretchX.animateTo(1.35f, motionScheme.fastSpatialSpec())
            wordmarkStretchX.animateTo(1.0f, motionScheme.defaultSpatialSpec())
        }
    }

    val prismSpinAngle = if (unlockedPrismTheme) {
        val infiniteTransition = rememberInfiniteTransition(label = "prismSpin")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "prismSpinAngle"
        )
        angle
    } else {
        0f
    }

    if (showBurst) {
        PlayfulParticleBurstOverlay(
            onFinished = { showBurst = false }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUnlockPrismTheme()
                            showBurst = true
                            Toast.makeText(context, context.getString(R.string.toast_unlocked_prism), Toast.LENGTH_SHORT).show()
                        },
                        onTap = {
                            onBrandTap()
                        }
                    )
                }
        ) {
            DynamicLumenIcon(
                size = 38.dp,
                rotationAngle = animatedTapSpin + (if (unlockedPrismTheme) prismSpinAngle else 0f),
                activeSeason = activeSeason
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Lumen",
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    scaleX = wordmarkStretchX.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
            )
        }

        val (statusLabel, statusColor, statusContainer) = when {
            useQuota -> Triple(
                stringResource(R.string.status_counts_to_quota),
                MaterialTheme.colorScheme.error,
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            )
            storageSaverQuality -> Triple(
                stringResource(R.string.status_storage_saver),
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            )
            else -> Triple(
                stringResource(R.string.status_unlimited_original),
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = statusContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountStatusCard(
    selectedAccount: GoogleAccount?,
    accounts: List<GoogleAccount> = emptyList(),
    shouldShowSwipeNudge: Boolean = false,
    nudgeReplayNonce: Int = 0,
    onSwipeNudgeShown: () -> Unit = {},
    onSelectAccount: (GoogleAccount) -> Unit = {},
    onNavigateToAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "accountCardSwipeOffset"
    )
    val nudgeOffsetX = remember { Animatable(0f) }

    LaunchedEffect(accounts.size > 1, shouldShowSwipeNudge, nudgeReplayNonce) {
        if (accounts.size > 1 && shouldShowSwipeNudge) {
            val animScale = try {
                android.provider.Settings.Global.getFloat(
                    context.contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                )
            } catch (_: Exception) {
                1f
            }
            if (animScale > 0f) {
                val nudgePx = with(density) { 24.dp.toPx() }
                try {
                    // Wait slightly so any tab transition or scroll-into-view settles first,
                    // and mark shown only after the animation finishes so flipping the state
                    // key doesn't cancel this coroutine mid-flight.
                    delay(500L)
                    nudgeOffsetX.snapTo(0f)
                    nudgeOffsetX.animateTo(
                        targetValue = nudgePx,
                        animationSpec = motionScheme.defaultSpatialSpec()
                    )
                    delay(150L)
                    nudgeOffsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = motionScheme.defaultSpatialSpec()
                    )
                    onSwipeNudgeShown()
                } catch (e: CancellationException) {
                    withContext(NonCancellable) {
                        nudgeOffsetX.snapTo(0f)
                    }
                    throw e
                }
            } else {
                onSwipeNudgeShown()
            }
        }
    }

    val currentIdx = remember(selectedAccount, accounts) {
        accounts.indexOfFirst { it.email.equals(selectedAccount?.email, ignoreCase = true) }
    }

    val swipeThresholdPx = with(density) { 56.dp.toPx() }
    val maxDragPx = with(density) { 84.dp.toPx() }

    val swipeModifier = if (accounts.size > 1 && currentIdx >= 0) {
        Modifier.pointerInput(accounts, currentIdx, swipeThresholdPx, maxDragPx) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (offsetX < -swipeThresholdPx) {
                        val nextIdx = (currentIdx + 1) % accounts.size
                        try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                        onSelectAccount(accounts[nextIdx])
                    } else if (offsetX > swipeThresholdPx) {
                        val prevIdx = (currentIdx - 1 + accounts.size) % accounts.size
                        try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                        onSelectAccount(accounts[prevIdx])
                    }
                    offsetX = 0f
                },
                onDragCancel = {
                    offsetX = 0f
                },
                onHorizontalDrag = { _, dragAmount ->
                    offsetX = (offsetX + dragAmount * 0.4f).coerceIn(-maxDragPx, maxDragPx)
                }
            )
        }
    } else Modifier

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by rememberExpressivePressed(interactionSource)
    val cardCorner by animateDpAsState(
        targetValue = if (isPressed) 14.dp else 24.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "accountCardCorner"
    )
    val cardShape = RoundedCornerShape(cardCorner)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = animatedOffsetX + nudgeOffsetX.value
            }
            .clip(cardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onNavigateToAccounts
            )
            .then(swipeModifier),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarFile = selectedAccount?.avatarPath?.let { File(it) }
            if (avatarFile != null && avatarFile.exists()) {
                AsyncImage(
                    model = avatarFile,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedAccount?.email?.take(1)?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedAccount?.displayName ?: selectedAccount?.email ?: stringResource(R.string.account_no_selected),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selectedAccount?.displayName != null) {
                    Text(
                        text = selectedAccount.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (selectedAccount == null) {
                ExpressiveTonalButton(
                    onClick = onNavigateToAccounts,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.btn_connect))
                }
            } else {
                Spacer(modifier = Modifier.width(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (accounts.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            accounts.forEachIndexed { index, _ ->
                                val isActive = index == currentIdx
                                val dotWidth by animateDpAsState(
                                    targetValue = if (isActive) 18.dp else 6.dp,
                                    animationSpec = motionScheme.fastSpatialSpec(),
                                    label = "accountDotWidth"
                                )
                                val dotColor by animateColorAsState(
                                    targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                    animationSpec = motionScheme.fastEffectsSpec(),
                                    label = "accountDotColor"
                                )
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .width(dotWidth)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.nav_accounts),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBackupCard(
    isUploading: Boolean,
    isPaused: Boolean = false,
    uploadSpeed: String?,
    currentUploadingItem: UploadItem?,
    queue: List<UploadItem>,
    completedCount: Int,
    skippedCount: Int = 0,
    conditionNotice: String?,
    hasMissingAlbum: Boolean = false,
    missingAlbum: com.reiux.lumen.data.network.GoogleAlbum? = null,
    isOffline: Boolean = false,
    onOpenAlbums: () -> Unit = {},
    scanState: ScanState = ScanState.Idle,
    accountBlock: AccountBlock? = null,
    showMockCompletionCelebration: Boolean = false,
    mockForcedPunTitle: String? = null,
    selectedAccount: GoogleAccount?,
    onStartBackup: () -> Unit,
    onPauseBackup: () -> Unit = {},
    onResumeBackup: () -> Unit = {},
    onCancelBackup: () -> Unit,
    onCancelScan: () -> Unit = {},
    onSignInAgain: () -> Unit = {},
    onRemoveRestrictedAccount: () -> Unit = {},
    onClearBlock: () -> Unit = {},
    onPickPhotos: () -> Unit,
    onPickFiles: () -> Unit = {}
) {
    val context = LocalContext.current
    if (accountBlock != null) {
        com.reiux.lumen.ui.components.AccountBlockCard(
            block = accountBlock,
            onSignInAgain = onSignInAgain,
            onRemoveAccount = onRemoveRestrictedAccount,
            onClearBlock = onClearBlock
        )
        return
    }

    if (scanState is ScanState.Scanning) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ExpressiveLoadingIndicator(
                    size = 52.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.title_indexing_folders),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                val countText = if (scanState.filesFound > 0) {
                    stringResource(R.string.backup_scan_files_found, scanState.filesFound, scanState.currentFolderName)
                } else {
                    stringResource(R.string.backup_scanning_folders)
                }
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                ExpressiveTonalButton(
                    onClick = onCancelScan,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.btn_cancel_scan), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val totalInQueue = queue.size
    val remainingCount = queue.count {
        it.status == UploadStatus.QUEUED ||
            it.status == UploadStatus.UPLOADING ||
            it.status == UploadStatus.HASHING ||
            it.status == UploadStatus.CHECKING ||
            it.status == UploadStatus.COMMITTING
    }
    val isDone = totalInQueue == 0 || queue.all { it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED }
    val completedRecently = !isOffline && !hasMissingAlbum && isDone && queue.isNotEmpty() && (completedCount > 0 || skippedCount > 0) && !isUploading && !isPaused && conditionNotice == null

    if (completedRecently || showMockCompletionCelebration) {
        PlayfulCompletionCelebration(
            completedCount = completedCount,
            skippedCount = skippedCount,
            enabledButtons = selectedAccount != null && !isOffline,
            onStartBackup = onStartBackup,
            onPickPhotos = onPickPhotos,
            onPickFiles = onPickFiles,
            customTitle = mockForcedPunTitle
        )
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isOffline -> MaterialTheme.colorScheme.tertiaryContainer
                    hasMissingAlbum -> MaterialTheme.colorScheme.errorContainer
                    isPaused -> MaterialTheme.colorScheme.secondaryContainer
                    conditionNotice != null -> MaterialTheme.colorScheme.tertiaryContainer
                    isUploading -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainer
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                isOffline -> Icons.Rounded.CloudOff
                                hasMissingAlbum -> Icons.Rounded.Warning
                                isPaused -> Icons.Rounded.Pause
                                conditionNotice != null -> Icons.Rounded.HourglassEmpty
                                isUploading -> Icons.Rounded.CloudUpload
                                else -> Icons.Rounded.CloudDone
                            },
                            contentDescription = null,
                            tint = when {
                                isOffline -> MaterialTheme.colorScheme.onTertiaryContainer
                                hasMissingAlbum -> MaterialTheme.colorScheme.onErrorContainer
                                isPaused -> MaterialTheme.colorScheme.onSecondaryContainer
                                conditionNotice != null -> MaterialTheme.colorScheme.onTertiaryContainer
                                isUploading -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.secondary
                            },
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when {
                                isOffline -> stringResource(R.string.backup_paused_no_internet)
                                hasMissingAlbum -> stringResource(R.string.title_album_not_found)
                                isPaused -> stringResource(R.string.backup_paused_title)
                                conditionNotice != null -> conditionNotice
                                isUploading -> stringResource(R.string.backup_status_uploading)
                                isDone -> stringResource(R.string.backup_status_ready)
                                else -> stringResource(R.string.backup_status_ready)
                            },
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = when {
                                isOffline -> MaterialTheme.colorScheme.onTertiaryContainer
                                hasMissingAlbum -> MaterialTheme.colorScheme.onErrorContainer
                                isPaused -> MaterialTheme.colorScheme.onSecondaryContainer
                                conditionNotice != null -> MaterialTheme.colorScheme.onTertiaryContainer
                                isUploading -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isOffline) {
                    Text(
                        text = stringResource(R.string.backup_offline_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else if (hasMissingAlbum) {
                    val albumNickname = missingAlbum?.title ?: stringResource(R.string.backup_target_album_fallback)
                    val accountName = selectedAccount?.displayName ?: selectedAccount?.email ?: stringResource(R.string.accounts_active_fallback)
                    val hasCompletedFiles = completedCount > 0 || queue.any { it.status == UploadStatus.COMPLETED }
                    val bodyText = if (!hasCompletedFiles) {
                        stringResource(R.string.backup_missing_album_desc_uncompleted, albumNickname, accountName)
                    } else {
                        stringResource(R.string.backup_missing_album_desc_completed, albumNickname, accountName)
                    }
                    Text(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else if ((isPaused || (conditionNotice == null && isUploading)) && currentUploadingItem != null) {
                val contentColor = if (isPaused) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
                Text(
                    text = currentUploadingItem.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                val progress = if (currentUploadingItem.fileSize > 0) {
                    currentUploadingItem.bytesUploaded.toFloat() / currentUploadingItem.fileSize.toFloat()
                } else 0f

                ExpressiveWavyLinearProgress(
                    progress = progress.coerceIn(0f, 1f),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.22f),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatBytes(currentUploadingItem.bytesUploaded)} / ${formatBytes(currentUploadingItem.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.85f)
                    )

                    if (isPaused) {
                        Text(
                            text = stringResource(R.string.backup_paused_subtitle, remainingCount),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor.copy(alpha = 0.85f)
                        )
                    } else if (!uploadSpeed.isNullOrEmpty()) {
                        val remainingBytes = remember(queue, currentUploadingItem) {
                            queue.filter { it.status == UploadStatus.QUEUED || it.status == UploadStatus.UPLOADING }
                                .sumOf { (it.fileSize - it.bytesUploaded).coerceAtLeast(0L) }
                        }
                        val eta = calculateRemainingTimeText(context, remainingBytes, uploadSpeed)
                        val speedLabel = if (!eta.isNullOrBlank()) "$uploadSpeed • $eta" else uploadSpeed

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = speedLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            } else if (isPaused) {
                Text(
                    text = stringResource(R.string.backup_paused_subtitle, remainingCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            } else if (conditionNotice != null) {
                Text(
                    text = stringResource(R.string.backup_paused_condition, conditionNotice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            } else {
                Text(
                    text = if (isDone) stringResource(R.string.backup_idle_subtitle) else stringResource(R.string.backup_queue_count, totalInQueue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (hasMissingAlbum) {
                // Missing album state: single Open Album Manager button replacing the 3 action buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveButton(
                        onClick = onOpenAlbums,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.FolderCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_open_album_manager))
                    }
                }
            } else if (isPaused) {
                // Paused state: Prominent primary Resume + side-by-side Cancel + paired Pick Photos & Choose from Files
                val buttonsEnabled = selectedAccount != null && !isOffline
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveButton(
                            onClick = onResumeBackup,
                            enabled = buttonsEnabled,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_resume_backup),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        ExpressiveTonalButton(
                            onClick = onCancelBackup,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_cancel_backup),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onPickPhotos,
                            shapes = ButtonDefaults.shapes(),
                            enabled = buttonsEnabled,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.pick_photos_button),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = onPickFiles,
                            shapes = ButtonDefaults.shapes(),
                            enabled = buttonsEnabled,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.FolderCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.pick_files_button),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else if (isUploading || conditionNotice != null) {
                // When actively uploading or waiting on a condition, provide Pause and Cancel Backup actions
                val buttonsEnabled = selectedAccount != null && !isOffline
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveTonalButton(
                            onClick = onPauseBackup,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_pause_backup),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        ExpressiveTonalButton(
                            onClick = onCancelBackup,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_cancel_backup),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onPickPhotos,
                            shapes = ButtonDefaults.shapes(),
                            enabled = buttonsEnabled,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.pick_photos_button),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = onPickFiles,
                            shapes = ButtonDefaults.shapes(),
                            enabled = buttonsEnabled,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.FolderCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.pick_files_button),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                // Idle state: Prominent primary Sync Folders + paired secondary Pick Photos & Choose from Files
                val buttonsEnabled = selectedAccount != null && !isOffline
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveButton(
                        onClick = onStartBackup,
                        enabled = buttonsEnabled,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_sync_folders))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onPickPhotos,
                            shapes = ButtonDefaults.shapes(),
                            enabled = buttonsEnabled,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.pick_photos_button),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = onPickFiles,
                            shapes = ButtonDefaults.shapes(),
                            enabled = buttonsEnabled,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.FolderCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.pick_files_button),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadItemRow(
    item: UploadItem,
    position: GroupPosition = GroupPosition.Single,
    accounts: List<GoogleAccount> = emptyList(),
    showAccountBadge: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    val canRetry = onRetry != null && (item.status == UploadStatus.FAILED || item.status == UploadStatus.STOPPED)
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by rememberExpressivePressed(interactionSource)
    val shape = animatedGroupedCardShape(
        position = position,
        pressed = canRetry && isPressed
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (canRetry) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current
                    ) {
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } catch (_: Exception) {}
                        onRetry?.invoke()
                    }
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.status == UploadStatus.UPLOADING || item.status == UploadStatus.HASHING || item.status == UploadStatus.CHECKING || item.status == UploadStatus.COMMITTING) {
                ExpressiveLoadingIndicator(
                    size = 24.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                val (icon, color) = when (item.status) {
                    UploadStatus.COMPLETED -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.primary
                    UploadStatus.SKIPPED -> Icons.Rounded.CloudDone to MaterialTheme.colorScheme.secondary
                    UploadStatus.FAILED -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
                    else -> Icons.Rounded.HourglassEmpty to MaterialTheme.colorScheme.outline
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (showAccountBadge && !item.accountEmail.isNullOrBlank()) {
                        val acc = accounts.find { it.email.equals(item.accountEmail, ignoreCase = true) }
                        val badgeText = acc?.displayName?.takeIf { it.isNotBlank() && it != "null" }
                            ?: item.accountEmail.substringBefore("@")

                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = when (item.status) {
                        UploadStatus.COMPLETED -> stringResource(R.string.status_backed_up)
                        UploadStatus.SKIPPED -> stringResource(R.string.status_already_in_photos)
                        UploadStatus.FAILED -> item.error ?: stringResource(R.string.status_failed)
                        UploadStatus.STOPPED -> item.error ?: stringResource(R.string.status_stopped)
                        UploadStatus.UPLOADING -> stringResource(R.string.status_uploading_bytes, formatBytes(item.bytesUploaded), formatBytes(item.fileSize))
                        UploadStatus.HASHING -> stringResource(R.string.status_hashing)
                        UploadStatus.CHECKING -> stringResource(R.string.status_checking_library)
                        UploadStatus.COMMITTING -> stringResource(R.string.status_committing)
                        UploadStatus.QUEUED -> stringResource(R.string.status_queued)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == UploadStatus.FAILED || item.status == UploadStatus.STOPPED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canRetry) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.queue_item_tap_to_retry),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

fun calculateRemainingTimeText(
    context: android.content.Context,
    remainingBytes: Long,
    uploadSpeedStr: String?
): String? {
    if (remainingBytes <= 0 || uploadSpeedStr.isNullOrBlank()) return null

    val bytesPerSec = parseSpeedToBytesPerSecond(uploadSpeedStr) ?: return null
    if (bytesPerSec <= 0) return null

    val totalSeconds = remainingBytes / bytesPerSec
    if (totalSeconds < 1) return context.getString(R.string.backup_eta_seconds, 1L)
    if (totalSeconds < 60) return context.getString(R.string.backup_eta_seconds, totalSeconds)

    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    if (minutes < 60) {
        return context.getString(R.string.backup_eta_minutes, minutes, seconds)
    }

    val hours = minutes / 60
    val remMinutes = minutes % 60
    return context.getString(R.string.backup_eta_hours, hours, remMinutes)
}

fun parseSpeedToBytesPerSecond(speedStr: String): Long? {
    val parts = speedStr.trim().split(" ")
    if (parts.size < 2) return null
    val number = parts[0].toDoubleOrNull() ?: return null
    val unit = parts[1].uppercase()

    return when {
        unit.startsWith("GB") -> (number * 1024 * 1024 * 1024).toLong()
        unit.startsWith("MB") -> (number * 1024 * 1024).toLong()
        unit.startsWith("KB") -> (number * 1024).toLong()
        unit.startsWith("B") -> number.toLong()
        else -> null
    }
}

@Composable
private fun QueueFilterChips(
    queue: List<UploadItem>,
    selectedFilter: String?,
    onSelectFilter: (String?) -> Unit
) {
    if (queue.isEmpty()) return

    val uploadingCount = queue.count {
        it.status == UploadStatus.UPLOADING ||
        it.status == UploadStatus.HASHING ||
        it.status == UploadStatus.CHECKING ||
        it.status == UploadStatus.COMMITTING
    }
    val failedCount = queue.count { it.status == UploadStatus.FAILED || it.status == UploadStatus.STOPPED }
    val doneCount = queue.count { it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED }

    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            com.reiux.lumen.ui.components.ExpressiveFilterChip(
                selected = selectedFilter == null,
                onClick = { onSelectFilter(null) },
                label = { Text("${stringResource(R.string.albums_filter_all)} (${queue.size})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
            )
        }
        if (uploadingCount > 0) {
            item {
                val isSelected = selectedFilter == "uploading"
                com.reiux.lumen.ui.components.ExpressiveFilterChip(
                    selected = isSelected,
                    onClick = { onSelectFilter(if (isSelected) null else "uploading") },
                    label = { Text(stringResource(R.string.filter_uploading, uploadingCount), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                )
            }
        }
        if (failedCount > 0) {
            item {
                val isSelected = selectedFilter == "failed"
                com.reiux.lumen.ui.components.ExpressiveFilterChip(
                    selected = isSelected,
                    onClick = { onSelectFilter(if (isSelected) null else "failed") },
                    label = { Text(stringResource(R.string.filter_failed, failedCount), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }
        if (doneCount > 0) {
            item {
                val isSelected = selectedFilter == "done"
                com.reiux.lumen.ui.components.ExpressiveFilterChip(
                    selected = isSelected,
                    onClick = { onSelectFilter(if (isSelected) null else "done") },
                    label = { Text(stringResource(R.string.filter_done, doneCount), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                )
            }
        }
    }
}