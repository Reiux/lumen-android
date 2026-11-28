package com.reiux.lumen.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.reiux.lumen.R
import com.reiux.lumen.data.model.AlbumTargetMode
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.data.network.GoogleAlbum
import com.reiux.lumen.data.repository.SettingsRepository
import com.reiux.lumen.ui.components.AccountAvatar
import com.reiux.lumen.ui.components.AddFolderDialog
import com.reiux.lumen.ui.components.AlbumChoice
import com.reiux.lumen.ui.components.AlbumDestinationBottomSheet
import com.reiux.lumen.ui.components.AllFilesPermissionDialog
import com.reiux.lumen.ui.components.ExpressiveButton
import com.reiux.lumen.ui.components.ExpressiveFilterChip
import com.reiux.lumen.ui.components.ExpressiveIconButton
import com.reiux.lumen.ui.components.ExpressiveLoadingIndicator
import com.reiux.lumen.ui.components.ExpressiveSwitch
import com.reiux.lumen.ui.components.ExpressiveTonalButton
import com.reiux.lumen.ui.components.ExpressiveWavyIndeterminateProgress
import com.reiux.lumen.ui.components.FolderMediaTypeChip
import com.reiux.lumen.ui.components.GroupPosition
import com.reiux.lumen.ui.components.LocalFoldPosture
import com.reiux.lumen.ui.components.TabletopSplitLayout
import com.reiux.lumen.ui.components.groupPositionOf
import com.reiux.lumen.ui.components.groupedCardShape
import com.reiux.lumen.ui.components.NullableBackupFolderSaver
import com.reiux.lumen.ui.components.isAllowedBackupFolderPath
import com.reiux.lumen.ui.components.rememberExpressivePressed
import com.reiux.lumen.ui.components.rememberHapticClick
import com.reiux.lumen.ui.components.requiresAllFilesPermission
import com.reiux.lumen.ui.components.resolveFolderMediaKind
import com.reiux.lumen.util.LocalMediaFolder
import com.reiux.lumen.util.MediaFileReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class DeviceFolderSort {
    RECENT,
    MOST_ITEMS
}

@Composable
fun DeviceFoldersScreen(
    discoveredFolders: List<LocalMediaFolder>,
    isDiscovering: Boolean,
    configuredFolders: List<BackupFolder>,
    accounts: List<GoogleAccount>,
    activeAccount: GoogleAccount?,
    remoteAlbums: List<GoogleAlbum>,
    isMockMode: Boolean = false,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onToggleDiscoveredFolder: (LocalMediaFolder, Boolean, String?) -> Unit,
    onAssignAlbum: (LocalMediaFolder, AlbumTargetMode, String?, String?, String?) -> Unit,
    onAddCustomFolder: (BackupFolder) -> Unit,
    onAddAlbum: (keyOrUrl: String, nickname: String, targetEmail: String?) -> GoogleAlbum,
    onOpenAlbumManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var hasMediaPermission by remember { mutableStateOf(isMockMode || hasReadMediaPermission(context)) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortModeName by rememberSaveable { mutableStateOf(DeviceFolderSort.RECENT.name) }
    val sortMode = remember(sortModeName) {
        runCatching { DeviceFolderSort.valueOf(sortModeName) }.getOrDefault(DeviceFolderSort.RECENT)
    }
    var targetAccountEmail by rememberSaveable { mutableStateOf(activeAccount?.email ?: accounts.firstOrNull()?.email) }
    val targetAccount = remember(targetAccountEmail, activeAccount, accounts) {
        accounts.firstOrNull { !targetAccountEmail.isNullOrBlank() && it.email.equals(targetAccountEmail, ignoreCase = true) }
            ?: activeAccount
            ?: accounts.firstOrNull()
    }
    var albumPickerTargetPath by rememberSaveable { mutableStateOf<String?>(null) }
    var albumPickerAccountEmail by rememberSaveable { mutableStateOf<String?>(null) }
    val albumPickerTarget = remember(albumPickerTargetPath, discoveredFolders) {
        albumPickerTargetPath?.let { p -> discoveredFolders.firstOrNull { SettingsRepository.matchesFolderPath(it.path, p) } }
    }
    var showCustomFolderDialog by rememberSaveable { mutableStateOf(false) }

    var showAllFilesDialog by rememberSaveable { mutableStateOf(false) }
    var pendingEnableFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAlbumAssignment by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    var pendingEnableCustomFolder by rememberSaveable(stateSaver = NullableBackupFolderSaver) { mutableStateOf<BackupFolder?>(null) }
    var returningFromAllFiles by rememberSaveable { mutableStateOf(false) }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val granted = isMockMode || hasReadMediaPermission(context)
        if (granted && hasMediaPermission) {
            onRefresh()
        }
        hasMediaPermission = granted
    }

    LaunchedEffect(hasMediaPermission) {
        if (hasMediaPermission) {
            onRefresh()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, discoveredFolders, targetAccount) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = isMockMode || hasReadMediaPermission(context)
                if (nowGranted && !hasMediaPermission) {
                    // Updating hasMediaPermission triggers LaunchedEffect(hasMediaPermission) once.
                    hasMediaPermission = true
                }
                if (returningFromAllFiles) {
                    returningFromAllFiles = false
                    pendingEnableFolderPath?.let { pendingPath ->
                        val folder = discoveredFolders.firstOrNull { SettingsRepository.matchesFolderPath(it.path, pendingPath) }
                        if (!requiresAllFilesPermission(context, pendingPath) && folder != null) {
                            onToggleDiscoveredFolder(folder, true, targetAccount?.email)
                        } else if (requiresAllFilesPermission(context, pendingPath)) {
                            Toast.makeText(context, R.string.toast_all_files_permission_denied, Toast.LENGTH_SHORT).show()
                        }
                        pendingEnableFolderPath = null
                    }
                    pendingAlbumAssignment?.let { assignData ->
                        if (assignData.size >= 4) {
                            val pendingPath = assignData[0]
                            val mode = runCatching { AlbumTargetMode.valueOf(assignData[1]) }.getOrDefault(AlbumTargetMode.NONE)
                            val customName = assignData[2].ifEmpty { null }
                            val customNick = assignData[3].ifEmpty { null }
                            val folder = discoveredFolders.firstOrNull { SettingsRepository.matchesFolderPath(it.path, pendingPath) }
                            if (!requiresAllFilesPermission(context, pendingPath) && folder != null) {
                                onAssignAlbum(folder, mode, customName, customNick, targetAccount?.email)
                            } else if (requiresAllFilesPermission(context, pendingPath)) {
                                Toast.makeText(context, R.string.toast_all_files_permission_denied, Toast.LENGTH_SHORT).show()
                            }
                        }
                        pendingAlbumAssignment = null
                    }
                    pendingEnableCustomFolder?.let { custom ->
                        if (!requiresAllFilesPermission(context, custom.path)) {
                            onAddCustomFolder(custom.copy(enabled = true))
                        } else {
                            Toast.makeText(context, R.string.toast_all_files_permission_denied, Toast.LENGTH_SHORT).show()
                        }
                        pendingEnableCustomFolder = null
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
                returningFromAllFiles = true
            },
            onDismiss = {
                showAllFilesDialog = false
                if (!returningFromAllFiles) {
                    pendingEnableFolderPath = null
                    pendingAlbumAssignment = null
                    pendingEnableCustomFolder = null
                    Toast.makeText(context, R.string.toast_all_files_permission_denied, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    val targetEmail = targetAccount?.email

    fun findConfiguredMatchForEmail(discovered: LocalMediaFolder, email: String?): BackupFolder? {
        if (!email.isNullOrBlank()) {
            configuredFolders.firstOrNull { f ->
                SettingsRepository.matchesFolderPath(f.path, discovered.path) &&
                    f.accountEmail.equals(email, ignoreCase = true)
            }?.let { return it }
        }
        return configuredFolders.firstOrNull { f ->
            SettingsRepository.matchesFolderPath(f.path, discovered.path) &&
                (email.isNullOrBlank() || f.accountEmail.isNullOrBlank())
        }
    }

    fun findConfiguredMatch(discovered: LocalMediaFolder): BackupFolder? =
        findConfiguredMatchForEmail(discovered, targetEmail)

    val searchedFolders = remember(discoveredFolders, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            discoveredFolders
        } else {
            discoveredFolders.filter {
                it.displayName.contains(q, ignoreCase = true) ||
                    MediaFileReader.toShortDisplayPath(it.path).contains(q, ignoreCase = true)
            }
        }
    }

    val enabledCount = remember(discoveredFolders, configuredFolders, targetEmail) {
        discoveredFolders.count { findConfiguredMatch(it)?.enabled == true }
    }

    if (showCustomFolderDialog) {
        AddFolderDialog(
            accounts = accounts,
            activeAccount = targetAccount,
            remoteAlbums = remoteAlbums,
            onAddAlbum = onAddAlbum,
            onOpenAlbumManager = {
                showCustomFolderDialog = false
                onOpenAlbumManager()
            },
            onDismiss = { showCustomFolderDialog = false },
            onAddFolder = { folder ->
                if (isAllowedBackupFolderPath(folder.path)) {
                    if (requiresAllFilesPermission(context, folder.path)) {
                        pendingEnableCustomFolder = folder.copy(enabled = false)
                        showAllFilesDialog = true
                        showCustomFolderDialog = false
                    } else {
                        onAddCustomFolder(folder)
                        showCustomFolderDialog = false
                    }
                } else {
                    Toast.makeText(context, R.string.toast_forbidden_folder, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (albumPickerTarget != null) {
        val folder = albumPickerTarget
        val pickerEmail = albumPickerAccountEmail ?: targetEmail
        val pickerAccount = remember(pickerEmail, accounts, targetAccount) {
            accounts.firstOrNull { !pickerEmail.isNullOrBlank() && it.email.equals(pickerEmail, ignoreCase = true) }
                ?: targetAccount
        }
        val existing = findConfiguredMatchForEmail(folder, pickerEmail)
        val currentChoice = when (existing?.targetAlbumMode) {
            AlbumTargetMode.AUTO_FOLDER_NAME -> AlbumChoice.AutoFolderName
            AlbumTargetMode.CUSTOM_NAME -> {
                val key = existing.customAlbumName ?: ""
                val matched = remoteAlbums.find {
                    (it.mediaKey == key || it.title == key) &&
                        (it.accountEmail.isNullOrBlank() || pickerEmail.isNullOrBlank() || it.accountEmail.equals(pickerEmail, ignoreCase = true))
                } ?: remoteAlbums.find { it.mediaKey == key || it.title == key }
                if (matched != null) AlbumChoice.CustomAlbum(matched)
                else if (key.isNotBlank()) {
                    AlbumChoice.CustomAlbum(
                        GoogleAlbum(
                            mediaKey = key,
                            title = existing.customAlbumNickname ?: key,
                            accountEmail = pickerEmail
                        )
                    )
                } else AlbumChoice.None
            }
            else -> AlbumChoice.None
        }

        AlbumDestinationBottomSheet(
            choice = currentChoice,
            onChoiceSelected = { selected ->
                val (mode, customName, customNick) = when (selected) {
                    is AlbumChoice.None -> Triple(AlbumTargetMode.NONE, null, null)
                    is AlbumChoice.AutoFolderName -> Triple(AlbumTargetMode.AUTO_FOLDER_NAME, null, null)
                    is AlbumChoice.CustomAlbum -> Triple(AlbumTargetMode.CUSTOM_NAME, selected.album.mediaKey, selected.album.title)
                    else -> Triple(AlbumTargetMode.NONE, null, null)
                }
                val wouldEnableRestricted = (existing == null || existing.enabled) && requiresAllFilesPermission(context, folder.path)
                if (wouldEnableRestricted) {
                    pendingAlbumAssignment = arrayListOf(folder.path, mode.name, customName ?: "", customNick ?: "")
                    showAllFilesDialog = true
                } else {
                    onAssignAlbum(folder, mode, customName, customNick, pickerEmail)
                }
                albumPickerTargetPath = null
                albumPickerAccountEmail = null
            },
            savedAlbums = remoteAlbums,
            accounts = accounts,
            activeAccount = pickerAccount,
            folderDisplayName = existing?.displayName ?: folder.displayName,
            onAddAlbum = onAddAlbum,
            onOpenAlbumManager = {
                albumPickerTargetPath = null
                albumPickerAccountEmail = null
                onOpenAlbumManager()
            },
            onDismiss = {
                albumPickerTargetPath = null
                albumPickerAccountEmail = null
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.device_folders_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.device_folders_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    ExpressiveIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                actions = {
                    ExpressiveTonalButton(
                        onClick = { showCustomFolderDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.device_folders_btn_custom),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    ExpressiveIconButton(
                        onClick = onRefresh,
                        enabled = hasMediaPermission && !isDiscovering,
                        colors = IconButtonDefaults.iconButtonColors()
                    ) {
                        if (isDiscovering) {
                            ExpressiveLoadingIndicator(
                                size = 20.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.folders_btn_scan)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (!hasMediaPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.widthIn(max = 420.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = MaterialShapes.SoftBurst.toShape(),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.device_folders_permission_title),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.device_folders_permission_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ExpressiveButton(
                            onClick = {
                                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    arrayOf(
                                        Manifest.permission.READ_MEDIA_IMAGES,
                                        Manifest.permission.READ_MEDIA_VIDEO,
                                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                                    )
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                                } else {
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                                mediaPermissionLauncher.launch(perms)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.device_folders_permission_btn))
                        }
                    }
                }
            }
            return@Scaffold
        }

        val handleFolderToggle: (LocalMediaFolder, Boolean, String?) -> Unit = { folder, next, pageEmail ->
            try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
            if (next && requiresAllFilesPermission(context, folder.path)) {
                pendingEnableFolderPath = folder.path
                showAllFilesDialog = true
            } else {
                onToggleDiscoveredFolder(folder, next, pageEmail ?: targetEmail)
            }
        }

        val rememberedFindMatchForEmail = remember(configuredFolders) {
            { discovered: LocalMediaFolder, email: String? -> findConfiguredMatchForEmail(discovered, email) }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val foldPosture = LocalFoldPosture.current
            val isTabletop = foldPosture.isTabletop && maxHeight >= 520.dp
            val isWideLayout = maxWidth >= 700.dp
            val isTallViewport = maxHeight >= 560.dp

            if (isTabletop) {
                val tabletopControlsScrollState = rememberScrollState()
                TabletopSplitLayout(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    topContent = {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .widthIn(max = 840.dp)
                                .fillMaxSize()
                                .verticalScroll(tabletopControlsScrollState)
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                        ) {
                            DeviceFoldersControls(
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                accounts = accounts,
                                selectedAccount = targetAccount,
                                onSelectAccount = { targetAccountEmail = it.email },
                                sortMode = sortMode,
                                onSelectSort = { sortModeName = it.name },
                                enabledCount = enabledCount,
                                totalCount = discoveredFolders.size
                            )
                        }
                    },
                    bottomContent = {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 840.dp)
                                .fillMaxSize()
                                .align(Alignment.TopCenter)
                        ) {
                            DeviceFoldersListContent(
                                folders = searchedFolders,
                                isDiscovering = isDiscovering,
                                searchQuery = searchQuery,
                                sortMode = sortMode,
                                selectedAccountEmail = targetEmail,
                                findMatchForEmail = rememberedFindMatchForEmail,
                                remoteAlbums = remoteAlbums,
                                isWideLayout = false,
                                onClearSearch = { searchQuery = "" },
                                onToggle = handleFolderToggle,
                                onPickAlbum = { folder, pageEmail ->
                                    albumPickerAccountEmail = pageEmail ?: targetEmail
                                    albumPickerTargetPath = folder.path
                                }
                            )
                        }
                    }
                )
            } else if (isWideLayout) {
                val leftWeight = if (isTallViewport) 0.44f else 0.38f
                val rightWeight = if (isTallViewport) 0.56f else 0.62f
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 1080.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isTallViewport) 28.dp else 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(leftWeight)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DeviceFoldersControls(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            accounts = accounts,
                            selectedAccount = targetAccount,
                            onSelectAccount = { targetAccountEmail = it.email },
                            sortMode = sortMode,
                            onSelectSort = { sortModeName = it.name },
                            enabledCount = enabledCount,
                            totalCount = discoveredFolders.size
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Box(
                        modifier = Modifier
                            .weight(rightWeight)
                            .fillMaxHeight()
                    ) {
                        DeviceFoldersListContent(
                            folders = searchedFolders,
                            isDiscovering = isDiscovering,
                            searchQuery = searchQuery,
                            sortMode = sortMode,
                            selectedAccountEmail = targetEmail,
                            findMatchForEmail = rememberedFindMatchForEmail,
                            remoteAlbums = remoteAlbums,
                            isWideLayout = true,
                            onClearSearch = { searchQuery = "" },
                            onToggle = handleFolderToggle,
                            onPickAlbum = { folder, pageEmail ->
                                albumPickerAccountEmail = pageEmail ?: targetEmail
                                albumPickerTargetPath = folder.path
                            }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 720.dp)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DeviceFoldersControls(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        accounts = accounts,
                        selectedAccount = targetAccount,
                        onSelectAccount = { targetAccountEmail = it.email },
                        sortMode = sortMode,
                        onSelectSort = { sortModeName = it.name },
                        enabledCount = enabledCount,
                        totalCount = discoveredFolders.size
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        DeviceFoldersListContent(
                            folders = searchedFolders,
                            isDiscovering = isDiscovering,
                            searchQuery = searchQuery,
                            sortMode = sortMode,
                            selectedAccountEmail = targetEmail,
                            findMatchForEmail = rememberedFindMatchForEmail,
                            remoteAlbums = remoteAlbums,
                            isWideLayout = false,
                            onClearSearch = { searchQuery = "" },
                            onToggle = handleFolderToggle,
                            onPickAlbum = { folder, pageEmail ->
                                albumPickerAccountEmail = pageEmail ?: targetEmail
                                albumPickerTargetPath = folder.path
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceFoldersControls(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    accounts: List<GoogleAccount>,
    selectedAccount: GoogleAccount?,
    onSelectAccount: (GoogleAccount) -> Unit,
    sortMode: DeviceFolderSort,
    onSelectSort: (DeviceFolderSort) -> Unit,
    enabledCount: Int,
    totalCount: Int
) {
    // M3E Search bar: full-pill shape, 56dp height, surfaceContainerHigh
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.device_folders_search_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    ExpressiveIconButton(
                        onClick = { onSearchQueryChange("") },
                        colors = IconButtonDefaults.iconButtonColors()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.btn_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (accounts.size > 1) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(accounts, key = { it.email }) { acc ->
                val isSelected = selectedAccount?.email.equals(acc.email, ignoreCase = true)
                val label = acc.displayName?.takeIf { it.isNotBlank() && it != "null" } ?: acc.email.substringBefore("@")
                ExpressiveFilterChip(
                    selected = isSelected,
                    onClick = { onSelectAccount(acc) },
                    leadingIcon = { AccountAvatar(account = acc, size = 18.dp) },
                    label = { Text(label, fontWeight = FontWeight.SemiBold) }
                )
            }
        }
    }

    val sortHaptic = LocalHapticFeedback.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        TonalToggleButton(
            checked = sortMode == DeviceFolderSort.RECENT,
            onCheckedChange = {
                try { sortHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                onSelectSort(DeviceFolderSort.RECENT)
            },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.device_folders_sort_recent),
                style = MaterialTheme.typography.labelLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        TonalToggleButton(
            checked = sortMode == DeviceFolderSort.MOST_ITEMS,
            onCheckedChange = {
                try { sortHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                onSelectSort(DeviceFolderSort.MOST_ITEMS)
            },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.device_folders_sort_most),
                style = MaterialTheme.typography.labelLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (totalCount > 0) {
        Text(
            text = stringResource(R.string.device_folders_summary_enabled, enabledCount, totalCount),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DeviceFoldersListContent(
    folders: List<LocalMediaFolder>,
    isDiscovering: Boolean,
    searchQuery: String,
    sortMode: DeviceFolderSort,
    selectedAccountEmail: String?,
    findMatchForEmail: (LocalMediaFolder, String?) -> BackupFolder?,
    remoteAlbums: List<GoogleAlbum>,
    isWideLayout: Boolean = false,
    onClearSearch: () -> Unit,
    onToggle: (LocalMediaFolder, Boolean, String?) -> Unit,
    onPickAlbum: (LocalMediaFolder, String?) -> Unit
) {
    if (isDiscovering && folders.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ExpressiveLoadingIndicator(size = 48.dp)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.device_folders_scanning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (folders.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (isWideLayout) 12.dp else 0.dp),
            contentAlignment = if (isWideLayout) Alignment.TopCenter else Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = MaterialShapes.Puffy.toShape(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (searchQuery.isNotBlank()) Icons.Rounded.Search else Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            stringResource(R.string.device_folders_no_matches, searchQuery.trim())
                        } else {
                            stringResource(R.string.device_folders_empty)
                        },
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    if (searchQuery.isNotBlank()) {
                        ExpressiveTonalButton(onClick = onClearSearch) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_clear))
                        }
                    }
                }
            }
        }
        return
    }

    val accountKey = selectedAccountEmail?.lowercase().orEmpty()
    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isDiscovering && folders.isNotEmpty(),
            enter = fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) +
                expandVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
            exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) +
                shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
        ) {
            ExpressiveWavyIndeterminateProgress(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        val motionScheme = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = accountKey,
            transitionSpec = {
                (fadeIn(animationSpec = motionScheme.fastEffectsSpec()) + slideInVertically(
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    initialOffsetY = { it / 28 }
                )).togetherWith(
                    fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                )
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "deviceFoldersAccountSwitch"
        ) { pageAccountKey ->
        val pageEmail = pageAccountKey.ifBlank { null }
        val sortedFolders = remember(folders, findMatchForEmail, pageEmail, sortMode) {
            val comparator = when (sortMode) {
                DeviceFolderSort.RECENT ->
                    compareByDescending<LocalMediaFolder> { findMatchForEmail(it, pageEmail)?.enabled == true }
                        .thenByDescending { it.latestDateAddedSec }
                        .thenByDescending { it.mediaCount }
                DeviceFolderSort.MOST_ITEMS ->
                    compareByDescending<LocalMediaFolder> { findMatchForEmail(it, pageEmail)?.enabled == true }
                        .thenByDescending { it.mediaCount }
                        .thenByDescending { it.latestDateAddedSec }
            }
            folders.sortedWith(comparator)
        }

        val listState = rememberLazyListState()
        var lastSortMode by remember { mutableStateOf(sortMode) }
        var lastSearchQuery by remember { mutableStateOf(searchQuery) }
        LaunchedEffect(sortMode, searchQuery) {
            if (lastSortMode != sortMode || lastSearchQuery != searchQuery) {
                lastSortMode = sortMode
                lastSearchQuery = searchQuery
                listState.animateScrollToItem(0)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(
                items = sortedFolders,
                key = { _, item -> SettingsRepository.normalizeFolderPath(item.path) }
            ) { index, folder ->
                val configured = findMatchForEmail(folder, pageEmail)
                DeviceFolderCardItem(
                    folder = folder,
                    configured = configured,
                    position = groupPositionOf(index, sortedFolders.size),
                    remoteAlbums = remoteAlbums,
                    onToggle = { next -> onToggle(folder, next, pageEmail) },
                    onPickAlbum = { onPickAlbum(folder, pageEmail) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
    }
}

@Composable
private fun DeviceFolderCardItem(
    folder: LocalMediaFolder,
    configured: BackupFolder?,
    position: GroupPosition,
    remoteAlbums: List<GoogleAlbum>,
    onToggle: (Boolean) -> Unit,
    onPickAlbum: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled = configured?.enabled == true
    val cardColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "deviceFolderCardColor"
    )
    val shortPath = remember(folder.path) { MediaFileReader.toShortDisplayPath(folder.path) }

    val albumMode = configured?.targetAlbumMode ?: AlbumTargetMode.NONE
    val albumLabel = when (albumMode) {
        AlbumTargetMode.NONE -> stringResource(R.string.device_folders_album_none)
        AlbumTargetMode.AUTO_FOLDER_NAME -> configured?.displayName ?: folder.displayName
        AlbumTargetMode.CUSTOM_NAME -> {
            val matched = remoteAlbums.find {
                it.mediaKey == configured?.customAlbumName || it.title == configured?.customAlbumName
            }
            matched?.title ?: configured?.customAlbumNickname ?: configured?.customAlbumName ?: folder.displayName
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = groupedCardShape(position),
        color = cardColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nested corner: outer 20dp - 12dp padding = 8dp
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (folder.latestThumbnailUri != null) {
                        AsyncImage(
                            model = folder.latestThumbnailUri,
                            contentDescription = folder.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${stringResource(R.string.device_folders_items_count, folder.mediaCount)} • $shortPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                ExpressiveSwitch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val hasAlbum = albumMode != AlbumTargetMode.NONE
                val mediaKind = remember(folder.imageCount, folder.videoCount) {
                    resolveFolderMediaKind(folder.imageCount, folder.videoCount)
                }
                val chipInteraction = remember { MutableInteractionSource() }
                val chipScope = rememberCoroutineScope()
                val isGesturePressed by rememberExpressivePressed(chipInteraction)
                var chipPulse by remember { mutableStateOf(false) }
                val isChipPressed = isGesturePressed || chipPulse
                val chipClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
                    chipScope.launch {
                        chipPulse = true
                        delay(160L)
                        chipPulse = false
                    }
                    onPickAlbum()
                }
                val chipCorner by animateDpAsState(
                    targetValue = when {
                        isChipPressed -> 8.dp
                        hasAlbum -> 18.dp
                        else -> 10.dp
                    },
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "deviceFolderAlbumChipCorner"
                )
                val chipScale by animateFloatAsState(
                    targetValue = if (isChipPressed) 0.96f else 1f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "deviceFolderAlbumChipScale"
                )
                val chipContainerColor by animateColorAsState(
                    targetValue = if (hasAlbum) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "deviceFolderAlbumChipBg"
                )
                val chipContentColor by animateColorAsState(
                    targetValue = if (hasAlbum) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "deviceFolderAlbumChipContent"
                )
                val chipShape = RoundedCornerShape(chipCorner)

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Surface(
                        shape = chipShape,
                        color = chipContainerColor,
                        modifier = Modifier
                            .scale(chipScale)
                            .minimumInteractiveComponentSize()
                            .clip(chipShape)
                            .clickable(
                                interactionSource = chipInteraction,
                                indication = LocalIndication.current,
                                onClick = chipClick
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                tint = chipContentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = albumLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = chipContentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (mediaKind != null) {
                    FolderMediaTypeChip(
                        kind = mediaKind,
                        enabled = isEnabled,
                        compact = false
                    )
                }
            }
        }
    }
}

internal fun hasReadMediaPermission(context: Context): Boolean {
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
