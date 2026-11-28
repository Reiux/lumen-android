package com.reiux.lumen.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChipDefaults
import com.reiux.lumen.ui.components.ExpressiveFilterChip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reiux.lumen.R
import com.reiux.lumen.ui.components.ExpressiveAlertDialog
import com.reiux.lumen.ui.components.ExpressiveButton
import com.reiux.lumen.ui.components.ExpressiveDialogTone
import com.reiux.lumen.ui.components.ExpressiveLoadingIndicator
import com.reiux.lumen.ui.components.ExpressiveTextButton
import com.reiux.lumen.ui.components.ExpressiveTonalButton
import com.reiux.lumen.data.model.AlbumTargetMode
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.data.repository.AlbumSyncState
import com.reiux.lumen.data.repository.SettingsRepository
import com.reiux.lumen.ui.components.AddFolderDialog
import com.reiux.lumen.ui.components.NullableBackupFolderSaver
import com.reiux.lumen.ui.components.isAllowedBackupFolderPath
import com.reiux.lumen.ui.components.ExpressiveIconButton
import com.reiux.lumen.ui.components.ExpressiveSwitch
import com.reiux.lumen.ui.components.ExpressiveWavyIndeterminateProgress
import com.reiux.lumen.ui.components.ExpressiveWavyLinearProgress
import com.reiux.lumen.ui.components.FolderMediaKind
import com.reiux.lumen.ui.components.FolderMediaTypeChip
import com.reiux.lumen.ui.components.LocalFoldPosture
import com.reiux.lumen.ui.components.TabletopSplitLayout
import com.reiux.lumen.ui.components.GroupPosition
import com.reiux.lumen.ui.components.groupPositionOf
import com.reiux.lumen.ui.components.groupedCardShape
import com.reiux.lumen.ui.components.resolveFolderMediaKindForPath
import com.reiux.lumen.util.LocalMediaFolder

@Composable
fun FoldersScreen(
    folders: List<BackupFolder>,
    accounts: List<GoogleAccount> = emptyList(),
    activeAccount: GoogleAccount? = null,
    remoteAlbums: List<com.reiux.lumen.data.network.GoogleAlbum> = emptyList(),
    discoveredFolders: List<LocalMediaFolder> = emptyList(),
    isMockMode: Boolean = false,
    onDiscoverDeviceFolders: () -> Unit = {},
    albumSyncState: AlbumSyncState = AlbumSyncState.Idle,
    onToggleFolder: (path: String, accountEmail: String?, enabled: Boolean) -> Unit,
    onSetIncludeSubfolders: (path: String, accountEmail: String?, includeSubfolders: Boolean) -> Unit,
    onAddFolder: (BackupFolder) -> Unit,
    onRemoveFolder: (path: String, accountEmail: String?) -> Unit,
    onAddAlbum: (keyOrUrl: String, nickname: String, targetEmail: String?) -> com.reiux.lumen.data.network.GoogleAlbum = { k, n, e -> com.reiux.lumen.data.network.GoogleAlbum(k, n, e) },
    onOpenAlbumManager: () -> Unit = {},
    onOpenDeviceFolders: () -> Unit = {},
    onSyncAllAlbums: () -> Unit = {},
    onSyncFolderToAlbum: (BackupFolder) -> Unit = {},
    onCancelAlbumSync: () -> Unit = {},
    onDismissAlbumSync: () -> Unit = {},
    autoSyncAlbums: Boolean = true,
    isOnline: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(folders.isNotEmpty(), isMockMode) {
        if (discoveredFolders.isEmpty() && folders.isNotEmpty() && (isMockMode || hasReadMediaPermission(context))) {
            onDiscoverDeviceFolders()
        }
    }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var folderToEdit by rememberSaveable(stateSaver = NullableBackupFolderSaver) { mutableStateOf<BackupFolder?>(null) }
    var folderToRemove by rememberSaveable(stateSaver = NullableBackupFolderSaver) { mutableStateOf<BackupFolder?>(null) }
    var showAllFilesDialog by rememberSaveable { mutableStateOf(false) }
    var pendingFolderToAdd by rememberSaveable(stateSaver = NullableBackupFolderSaver) { mutableStateOf<BackupFolder?>(null) }
    var pendingFolderToToggle by rememberSaveable(stateSaver = NullableBackupFolderSaver) { mutableStateOf<BackupFolder?>(null) }
    // Encoded "path\u0000accountEmail" pairs to enable once All-Files access is confirmed granted.
    var pendingEnableEncoded by rememberSaveable { mutableStateOf<ArrayList<String>>(arrayListOf()) }
    var returningFromPermission by rememberSaveable { mutableStateOf(false) }

    if (folderToEdit != null) {
        val editingOriginal = folderToEdit!!
        com.reiux.lumen.ui.components.EditFolderDialog(
            folder = editingOriginal,
            accounts = accounts,
            activeAccount = activeAccount,
            remoteAlbums = remoteAlbums,
            onAddAlbum = onAddAlbum,
            onOpenAlbumManager = onOpenAlbumManager,
            onSyncToAlbum = { target ->
                val willAutoSync = autoSyncAlbums &&
                    target.enabled &&
                    isOnline &&
                    target.targetAlbumMode != AlbumTargetMode.NONE &&
                    (editingOriginal.targetAlbumMode != target.targetAlbumMode ||
                        editingOriginal.customAlbumName != target.customAlbumName ||
                        editingOriginal.displayName != target.displayName ||
                        !editingOriginal.accountEmail.equals(target.accountEmail, ignoreCase = true))
                if (!willAutoSync) {
                    onSyncFolderToAlbum(target)
                }
                folderToEdit = null
            },
            onDismiss = { folderToEdit = null },
            onSaveFolder = { updated ->
                if (!editingOriginal.accountEmail.equals(updated.accountEmail, ignoreCase = true)) {
                    onRemoveFolder(editingOriginal.path, editingOriginal.accountEmail)
                }
                onAddFolder(updated)
                folderToEdit = null
            }
        )
    }

    if (showAllFilesDialog) {
        com.reiux.lumen.ui.components.AllFilesPermissionDialog(
            onConfirm = {
                // The system permission screen opens right after this callback, so never
                // add/enable a folder that can't be read yet: save the new folder DISABLED
                // and defer all enables until the grant is verified on return.
                pendingFolderToAdd?.let { onAddFolder(it.copy(enabled = false)) }
                pendingEnableEncoded = ArrayList(buildList {
                    pendingFolderToAdd?.let { add("${it.path}\u0000${it.accountEmail.orEmpty()}") }
                    pendingFolderToToggle?.let { add("${it.path}\u0000${it.accountEmail.orEmpty()}") }
                })
                pendingFolderToAdd = null
                pendingFolderToToggle = null
                returningFromPermission = true
            },
            onDismiss = {
                // The dialog fires onConfirm() and then onDismiss() on confirm, so only
                // treat this as a cancel when we didn't just confirm.
                showAllFilesDialog = false
                if (!returningFromPermission) {
                    pendingFolderToAdd = null
                    pendingFolderToToggle = null
                    Toast.makeText(context, context.getString(R.string.toast_all_files_permission_denied), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && returningFromPermission) {
                val decoded = pendingEnableEncoded.map { raw ->
                    val parts = raw.split('\u0000', limit = 2)
                    parts[0] to parts.getOrNull(1)?.ifEmpty { null }
                }
                val allGranted = decoded.none { (path, _) ->
                    com.reiux.lumen.ui.components.requiresAllFilesPermission(context, path)
                }
                if (allGranted) {
                    decoded.forEach { (path, email) ->
                        onToggleFolder(path, email, true)
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_all_files_permission_denied), Toast.LENGTH_SHORT).show()
                }
                pendingEnableEncoded = arrayListOf()
                returningFromPermission = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (folderToRemove != null) {
        val target = folderToRemove!!
        ExpressiveAlertDialog(
            onDismissRequest = { folderToRemove = null },
            icon = Icons.Rounded.FolderDelete,
            title = stringResource(R.string.dialog_remove_folder_title),
            tone = ExpressiveDialogTone.ERROR,
            confirmText = stringResource(R.string.btn_delete),
            onConfirm = {
                onRemoveFolder(target.path, target.accountEmail)
                folderToRemove = null
            },
            dismissText = stringResource(R.string.btn_cancel)
        ) {
            Text(
                text = stringResource(R.string.dialog_remove_folder_msg, target.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showAddDialog) {
        AddFolderDialog(
            accounts = accounts,
            activeAccount = activeAccount,
            remoteAlbums = remoteAlbums,
            onAddAlbum = onAddAlbum,
            onOpenAlbumManager = onOpenAlbumManager,
            onDismiss = { showAddDialog = false },
            onAddFolder = { folder ->
                if (isAllowedBackupFolderPath(folder.path)) {
                    if (com.reiux.lumen.ui.components.requiresAllFilesPermission(context, folder.path)) {
                        pendingFolderToAdd = folder
                        showAllFilesDialog = true
                        showAddDialog = false
                    } else {
                        onAddFolder(folder)
                        showAddDialog = false
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_forbidden_folder), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    var selectedAccountFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var inspectedFolderKey by rememberSaveable { mutableStateOf<String?>(null) }

    val filteredFolders = remember(folders, selectedAccountFilter) {
        val base = if (selectedAccountFilter == null) {
            folders
        } else if (selectedAccountFilter == "unassigned") {
            folders.filter { it.accountEmail.isNullOrBlank() }
        } else {
            folders.filter { it.accountEmail.equals(selectedAccountFilter, ignoreCase = true) }
        }
        base.sortedByDescending { it.enabled }
    }

    val folderKey: (BackupFolder) -> String = {
        "${it.accountEmail.orEmpty().lowercase()}:${SettingsRepository.normalizeFolderPath(it.path)}"
    }
    val inspectedFolder = remember(filteredFolders, inspectedFolderKey) {
        filteredFolders.firstOrNull { folderKey(it) == inspectedFolderKey }
            ?: filteredFolders.firstOrNull { it.enabled }
            ?: filteredFolders.firstOrNull()
    }

    val hasAlbumAssignedFolders = remember(folders) {
        folders.any { it.enabled && it.targetAlbumMode != AlbumTargetMode.NONE }
    }
    val isSyncingAlbums = albumSyncState is AlbumSyncState.Syncing

    val wideLazyListState = rememberLazyListState()
    val portraitLazyListState = rememberLazyListState()
    val leftScrollState = rememberScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val foldPosture = LocalFoldPosture.current
        val isTabletop = foldPosture.isTabletop && maxHeight >= 520.dp
        val isWideLayout = maxWidth >= 640.dp
        val isTallViewport = maxHeight >= 560.dp

        if (isTabletop) {
            val isWideTabletop = maxWidth >= 640.dp
            val enabledCount = remember(folders) { folders.count { it.enabled } }
            val albumLinkedCount = remember(folders) {
                folders.count { it.enabled && it.targetAlbumMode != AlbumTargetMode.NONE }
            }
            val tabletopLazyListState = rememberLazyListState()
            TabletopSplitLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                topContent = {
                    if (isWideTabletop) {
                        val tabletopLeftScrollState = rememberScrollState()
                        val tabletopRightScrollState = rememberScrollState()
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
                                    .weight(0.92f)
                                    .fillMaxHeight()
                                    .verticalScroll(tabletopLeftScrollState),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FoldersHeader(
                                    hasAlbumAssignedFolders = hasAlbumAssignedFolders,
                                    isSyncingAlbums = isSyncingAlbums,
                                    onSyncAllAlbums = onSyncAllAlbums
                                )

                                Surface(
                                    shape = groupedCardShape(GroupPosition.Single),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        FolderOverviewStat(
                                            value = "$enabledCount / ${folders.size}",
                                            label = stringResource(R.string.accounts_active_badge)
                                        )
                                        FolderOverviewStat(
                                            value = "$albumLinkedCount",
                                            label = stringResource(R.string.albums_title)
                                        )
                                        FolderOverviewStat(
                                            value = "${filteredFolders.size}",
                                            label = stringResource(R.string.nav_folders)
                                        )
                                    }
                                }

                                AccountFilterChips(
                                    accounts = accounts,
                                    folders = folders,
                                    selectedFilter = selectedAccountFilter,
                                    onSelectFilter = { selectedAccountFilter = it }
                                )

                                FoldersActionControls(
                                    onAddFolder = onOpenDeviceFolders,
                                    onOpenAlbumManager = onOpenAlbumManager
                                )

                                if (albumSyncState !is AlbumSyncState.Idle) {
                                    AlbumSyncStatusCard(
                                        albumSyncState = albumSyncState,
                                        onCancel = onCancelAlbumSync,
                                        onDismiss = onDismissAlbumSync
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1.08f)
                                    .fillMaxHeight()
                                    .verticalScroll(tabletopRightScrollState),
                                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                            ) {
                                if (inspectedFolder != null) {
                                    TabletopFolderInspectorCard(
                                        folder = inspectedFolder,
                                        accounts = accounts,
                                        remoteAlbums = remoteAlbums,
                                        isSyncingAlbums = isSyncingAlbums,
                                        onToggleEnabled = { nextEnabled ->
                                            if (nextEnabled && com.reiux.lumen.ui.components.requiresAllFilesPermission(context, inspectedFolder.path)) {
                                                pendingFolderToToggle = inspectedFolder
                                                showAllFilesDialog = true
                                            } else {
                                                onToggleFolder(inspectedFolder.path, inspectedFolder.accountEmail, nextEnabled)
                                            }
                                        },
                                        onEditFolder = { folderToEdit = inspectedFolder },
                                        onSyncFolderToAlbum = { onSyncFolderToAlbum(inspectedFolder) },
                                        onRemoveFolder = { folderToRemove = inspectedFolder }
                                    )
                                } else {
                                    FoldersEmptyStateCard(
                                        isFilteredByAccount = selectedAccountFilter != null && folders.isNotEmpty()
                                    )
                                }
                            }
                        }
                    } else {
                        val tabletopTopScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .widthIn(max = 680.dp)
                                .fillMaxSize()
                                .padding(top = 6.dp, bottom = 4.dp)
                                .verticalScroll(tabletopTopScrollState),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Spacer(modifier = Modifier.height(4.dp))
                            FoldersHeader(
                                hasAlbumAssignedFolders = hasAlbumAssignedFolders,
                                isSyncingAlbums = isSyncingAlbums,
                                onSyncAllAlbums = onSyncAllAlbums
                            )

                            FoldersActionControls(
                                onAddFolder = onOpenDeviceFolders,
                                onOpenAlbumManager = onOpenAlbumManager
                            )

                            if (albumSyncState !is AlbumSyncState.Idle) {
                                AlbumSyncStatusCard(
                                    albumSyncState = albumSyncState,
                                    onCancel = onCancelAlbumSync,
                                    onDismiss = onDismissAlbumSync
                                )
                            }

                            AccountFilterChips(
                                accounts = accounts,
                                folders = folders,
                                selectedFilter = selectedAccountFilter,
                                onSelectFilter = { selectedAccountFilter = it }
                            )
                        }
                    }
                },
                bottomContent = {
                    LazyColumn(
                        state = tabletopLazyListState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = if (isWideTabletop) 960.dp else 680.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = if (isMockMode) 88.dp else 16.dp
                        )
                    ) {
                        if (filteredFolders.isEmpty()) {
                            item(key = "folders_empty_tabletop") {
                                FoldersEmptyStateCard(
                                    isFilteredByAccount = selectedAccountFilter != null && folders.isNotEmpty()
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = filteredFolders,
                                key = { _, folder -> folderKey(folder) }
                            ) { index, folder ->
                                val key = folderKey(folder)
                                FolderRowItem(
                                    folder = folder,
                                    position = groupPositionOf(index, filteredFolders.size),
                                    accounts = accounts,
                                    remoteAlbums = remoteAlbums,
                                    mediaKind = resolveFolderMediaKindForPath(folder, discoveredFolders),
                                    accountsCount = accounts.size,
                                    isSelected = isWideTabletop && inspectedFolder != null && key == folderKey(inspectedFolder),
                                    onSelect = if (isWideTabletop) {
                                        { inspectedFolderKey = key }
                                    } else {
                                        null
                                    },
                                    onToggleFolder = onToggleFolder,
                                    onRequestAllFilesToggle = { targetFolder ->
                                        pendingFolderToToggle = targetFolder
                                        showAllFilesDialog = true
                                    },
                                    onSetIncludeSubfolders = onSetIncludeSubfolders,
                                    onEditFolder = { folderToEdit = it },
                                    onRemoveFolder = { folderToRemove = it },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            )
        } else if (isWideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = 1080.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isTallViewport) 28.dp else 16.dp)
            ) {
                // Left Pane: Title & Controls
                Column(
                    modifier = Modifier
                        .weight(if (isTallViewport) 1f else 0.42f)
                        .fillMaxHeight()
                        .verticalScroll(leftScrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FoldersHeader(
                        hasAlbumAssignedFolders = hasAlbumAssignedFolders,
                        isSyncingAlbums = isSyncingAlbums,
                        onSyncAllAlbums = onSyncAllAlbums
                    )

                    AlbumSyncStatusCard(
                        albumSyncState = albumSyncState,
                        onCancel = onCancelAlbumSync,
                        onDismiss = onDismissAlbumSync
                    )

                    FoldersActionControls(
                        onAddFolder = onOpenDeviceFolders,
                        onOpenAlbumManager = onOpenAlbumManager
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

            // Right Pane: Folder Items List
            LazyColumn(
                state = wideLazyListState,
                modifier = Modifier
                    .weight(if (isTallViewport) 1f else 0.58f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                item { Spacer(modifier = Modifier.height(9.dp)) }
                item {
                    Column {
                        AccountFilterChips(
                            accounts = accounts,
                            folders = folders,
                            selectedFilter = selectedAccountFilter,
                            onSelectFilter = { selectedAccountFilter = it }
                        )
                        Spacer(modifier = Modifier.height(9.dp))
                    }
                }
                if (filteredFolders.isEmpty()) {
                    item(key = "folders_empty_wide") {
                        FoldersEmptyStateCard(
                            isFilteredByAccount = selectedAccountFilter != null && folders.isNotEmpty()
                        )
                    }
                } else {
                    itemsIndexed(
                        items = filteredFolders,
                        key = { _, folder -> "${folder.accountEmail.orEmpty().lowercase()}:${SettingsRepository.normalizeFolderPath(folder.path)}" }
                    ) { index, folder ->
                        FolderRowItem(
                            folder = folder,
                            position = groupPositionOf(index, filteredFolders.size),
                            accounts = accounts,
                            remoteAlbums = remoteAlbums,
                            mediaKind = resolveFolderMediaKindForPath(folder, discoveredFolders),
                            accountsCount = accounts.size,
                            onToggleFolder = onToggleFolder,
                            onRequestAllFilesToggle = { targetFolder ->
                                pendingFolderToToggle = targetFolder
                                showAllFilesDialog = true
                            },
                            onSetIncludeSubfolders = onSetIncludeSubfolders,
                            onEditFolder = { folderToEdit = it },
                            onRemoveFolder = { folderToRemove = it },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(if (isMockMode) 88.dp else 16.dp)) }
            }
        }
    } else {
        // Portrait Single Column
        LazyColumn(
            state = portraitLazyListState,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                FoldersHeader(
                    hasAlbumAssignedFolders = hasAlbumAssignedFolders,
                    isSyncingAlbums = isSyncingAlbums,
                    onSyncAllAlbums = onSyncAllAlbums
                )
                Spacer(modifier = Modifier.height(12.dp))
                AccountFilterChips(
                    accounts = accounts,
                    folders = folders,
                    selectedFilter = selectedAccountFilter,
                    onSelectFilter = { selectedAccountFilter = it }
                )
                Spacer(modifier = Modifier.height(9.dp))
            }

            if (albumSyncState !is AlbumSyncState.Idle) {
                item {
                    Column {
                        AlbumSyncStatusCard(
                            albumSyncState = albumSyncState,
                            onCancel = onCancelAlbumSync,
                            onDismiss = onDismissAlbumSync
                        )
                        Spacer(modifier = Modifier.height(9.dp))
                    }
                }
            }

            if (filteredFolders.isEmpty()) {
                item(key = "folders_empty_portrait") {
                    FoldersEmptyStateCard(
                        isFilteredByAccount = selectedAccountFilter != null && folders.isNotEmpty()
                    )
                }
            } else {
                itemsIndexed(
                    items = filteredFolders,
                    key = { _, folder -> "${folder.accountEmail.orEmpty().lowercase()}:${SettingsRepository.normalizeFolderPath(folder.path)}" }
                ) { index, folder ->
                    FolderRowItem(
                        folder = folder,
                        position = groupPositionOf(index, filteredFolders.size),
                        accounts = accounts,
                        remoteAlbums = remoteAlbums,
                        mediaKind = resolveFolderMediaKindForPath(folder, discoveredFolders),
                        accountsCount = accounts.size,
                        onToggleFolder = onToggleFolder,
                        onRequestAllFilesToggle = { targetFolder ->
                            pendingFolderToToggle = targetFolder
                            showAllFilesDialog = true
                        },
                        onSetIncludeSubfolders = onSetIncludeSubfolders,
                        onEditFolder = { folderToEdit = it },
                        onRemoveFolder = { folderToRemove = it },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(9.dp))
                FoldersActionControls(
                    onAddFolder = onOpenDeviceFolders,
                    onOpenAlbumManager = onOpenAlbumManager
                )
                Spacer(modifier = Modifier.height(if (isMockMode) 88.dp else 16.dp))
            }
        }
        }
    }
}

@Composable
private fun FolderOverviewStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLargeEmphasized,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun FoldersEmptyStateCard(
    isFilteredByAccount: Boolean
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialShapes.Clover4Leaf.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Text(
                text = stringResource(
                    if (isFilteredByAccount) R.string.folders_empty_account_title else R.string.folders_empty_title
                ),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(
                    if (isFilteredByAccount) R.string.folders_empty_account_subtitle else R.string.folders_empty_subtitle
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FoldersHeader(
    hasAlbumAssignedFolders: Boolean,
    isSyncingAlbums: Boolean,
    onSyncAllAlbums: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.folders_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.folders_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (hasAlbumAssignedFolders) {
            Spacer(modifier = Modifier.width(8.dp))
            ExpressiveTextButton(
                onClick = onSyncAllAlbums,
                enabled = !isSyncingAlbums
            ) {
                if (isSyncingAlbums) {
                    ExpressiveLoadingIndicator(
                        size = 20.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSyncingAlbums) stringResource(R.string.settings_action_syncing) else stringResource(R.string.btn_sync_albums),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FoldersActionControls(
    onAddFolder: () -> Unit,
    onOpenAlbumManager: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ExpressiveButton(
            onClick = onAddFolder,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.folders_btn_add), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        ExpressiveTonalButton(
            onClick = onOpenAlbumManager,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.btn_album_manager), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AlbumSyncStatusCard(
    albumSyncState: AlbumSyncState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = albumSyncState !is AlbumSyncState.Idle,
        enter = fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec())
    ) {
        when (albumSyncState) {
            is AlbumSyncState.Syncing -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                ExpressiveLoadingIndicator(
                                    size = 20.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.album_sync_in_progress, albumSyncState.currentFolderName),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (albumSyncState.totalFiles > 0) {
                                            stringResource(
                                                R.string.album_sync_progress_detail,
                                                albumSyncState.processedFiles,
                                                albumSyncState.totalFiles,
                                                albumSyncState.addedCount
                                            )
                                        } else {
                                            stringResource(R.string.album_sync_preparing)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            ExpressiveTextButton(onClick = onCancel) {
                                Text(stringResource(R.string.btn_cancel))
                            }
                        }

                        if (albumSyncState.totalFiles > 0) {
                            val progress = (albumSyncState.processedFiles.toFloat() / albumSyncState.totalFiles.toFloat()).coerceIn(0f, 1f)
                            ExpressiveWavyLinearProgress(progress = progress)
                        } else {
                            ExpressiveWavyIndeterminateProgress()
                        }
                    }
                }
            }

            is AlbumSyncState.Done -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.album_sync_done_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                val summaryText = when {
                                    albumSyncState.addedCount > 0 && albumSyncState.notInCloudCount > 0 ->
                                        stringResource(
                                            R.string.album_sync_done_with_pending,
                                            albumSyncState.addedCount,
                                            albumSyncState.notInCloudCount
                                        )
                                    albumSyncState.addedCount > 0 ->
                                        stringResource(
                                            R.string.album_sync_done_all_synced,
                                            albumSyncState.addedCount
                                        )
                                    albumSyncState.notInCloudCount > 0 ->
                                        stringResource(
                                            R.string.album_sync_done_only_pending,
                                            albumSyncState.notInCloudCount
                                        )
                                    else ->
                                        stringResource(R.string.album_sync_done_nothing)
                                }
                                Text(
                                    text = summaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                        ExpressiveIconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.btn_close),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            AlbumSyncState.Idle -> Unit
        }
    }
}

@Composable
private fun AccountFilterChips(
    accounts: List<GoogleAccount>,
    folders: List<BackupFolder>,
    selectedFilter: String?,
    onSelectFilter: (String?) -> Unit
) {
    if (accounts.size <= 1 && folders.none { it.accountEmail.isNullOrBlank() }) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            ExpressiveFilterChip(
                selected = selectedFilter == null,
                onClick = { onSelectFilter(null) },
                label = { Text(stringResource(R.string.filter_all, folders.size), fontWeight = FontWeight.SemiBold) }
            )
        }
        items(accounts) { acc ->
            val name = acc.displayName?.takeIf { it.isNotBlank() && it != "null" } ?: acc.email.substringBefore("@")
            val count = folders.count { it.accountEmail.equals(acc.email, ignoreCase = true) }
            val isSelected = selectedFilter.equals(acc.email, ignoreCase = true)
            ExpressiveFilterChip(
                selected = isSelected,
                onClick = { onSelectFilter(if (isSelected) null else acc.email) },
                label = { Text("$name ($count)", fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    com.reiux.lumen.ui.components.AccountAvatar(account = acc, size = 18.dp)
                }
            )
        }
        val unassignedCount = folders.count { it.accountEmail.isNullOrBlank() }
        if (unassignedCount > 0) {
            item {
                val isUnassignedSelected = selectedFilter == "unassigned"
                ExpressiveFilterChip(
                    selected = isUnassignedSelected,
                    onClick = { onSelectFilter(if (isUnassignedSelected) null else "unassigned") },
                    label = { Text(stringResource(R.string.filter_unassigned, unassignedCount), fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TabletopFolderInspectorCard(
    folder: BackupFolder,
    accounts: List<GoogleAccount>,
    remoteAlbums: List<com.reiux.lumen.data.network.GoogleAlbum> = emptyList(),
    isSyncingAlbums: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEditFolder: () -> Unit,
    onSyncFolderToAlbum: () -> Unit,
    onRemoveFolder: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (folder.enabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = if (folder.enabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = folder.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (folder.enabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = stringResource(
                                if (folder.enabled) R.string.folders_status_enabled else R.string.folders_status_disabled
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (folder.enabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                ExpressiveSwitch(
                    checked = folder.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = folder.path,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!folder.accountEmail.isNullOrBlank()) {
                    val acc = accounts.find { it.email.equals(folder.accountEmail, ignoreCase = true) }
                    val accountLabel = acc?.displayName?.takeIf { it.isNotBlank() && it != "null" } ?: folder.accountEmail
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            com.reiux.lumen.ui.components.AccountAvatar(
                                account = acc,
                                emailFallback = folder.accountEmail,
                                size = 16.dp
                            )
                            Text(
                                text = accountLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.folder_unassigned_warning),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (folder.targetAlbumMode != AlbumTargetMode.NONE) {
                    val matchingAlbum = remoteAlbums.find {
                        (it.mediaKey == folder.customAlbumName || it.title == folder.customAlbumName) &&
                            (folder.accountEmail.isNullOrBlank() || it.accountEmail.isNullOrBlank() || it.accountEmail.equals(folder.accountEmail, ignoreCase = true))
                    }
                    val isMissing = folder.isMissingAlbum || matchingAlbum?.isMissing == true
                    val albumNickname = when (folder.targetAlbumMode) {
                        AlbumTargetMode.AUTO_FOLDER_NAME -> folder.displayName
                        AlbumTargetMode.CUSTOM_NAME -> folder.customAlbumNickname ?: folder.customAlbumName ?: folder.displayName
                        AlbumTargetMode.NONE -> ""
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isMissing) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isMissing) Icons.Rounded.Warning else Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                tint = if (isMissing) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isMissing) {
                                    stringResource(R.string.folder_album_missing, albumNickname)
                                } else {
                                    albumNickname
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMissing) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.album_choice_none),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (folder.includeSubfolders) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.tag_subfolders),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            maxLines = 1
                        )
                    }
                }

                if (folder.deleteAfterBackup) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.folder_delete_after_backup_badge),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExpressiveTonalButton(
                    onClick = onEditFolder,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.folders_edit_desc),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (folder.enabled && folder.targetAlbumMode != AlbumTargetMode.NONE) {
                    ExpressiveTonalButton(
                        onClick = onSyncFolderToAlbum,
                        enabled = !isSyncingAlbums,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (isSyncingAlbums) {
                            ExpressiveLoadingIndicator(
                                size = 16.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (isSyncingAlbums) R.string.settings_action_syncing else R.string.settings_action_sync
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                ExpressiveIconButton(
                    onClick = onRemoveFolder,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.btn_delete),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRowItem(
    folder: BackupFolder,
    position: com.reiux.lumen.ui.components.GroupPosition,
    accounts: List<GoogleAccount> = emptyList(),
    remoteAlbums: List<com.reiux.lumen.data.network.GoogleAlbum> = emptyList(),
    mediaKind: FolderMediaKind? = null,
    accountsCount: Int = 1,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    onToggleFolder: (path: String, accountEmail: String?, enabled: Boolean) -> Unit,
    onRequestAllFilesToggle: (folder: BackupFolder) -> Unit,
    onSetIncludeSubfolders: (path: String, accountEmail: String?, includeSubfolders: Boolean) -> Unit,
    onEditFolder: (BackupFolder) -> Unit,
    onRemoveFolder: (folder: BackupFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardShape = groupedCardShape(position)
    val cardColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val cardBorder = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
    } else {
        null
    }

    val cardContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Folder Icon (with optional Account badge), Folder Name, Switch, Edit, Delete
            val showAccountBadge = accountsCount > 1 && !folder.accountEmail.isNullOrBlank()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(40.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (folder.enabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = if (folder.enabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (showAccountBadge) {
                        val acc = accounts.find { it.email.equals(folder.accountEmail, ignoreCase = true) }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(cardColor, CircleShape)
                                .padding(1.5.dp)
                        ) {
                            com.reiux.lumen.ui.components.AccountAvatar(
                                account = acc,
                                emailFallback = folder.accountEmail,
                                size = 17.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = folder.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                ExpressiveSwitch(
                    checked = folder.enabled,
                    onCheckedChange = { nextEnabled ->
                        if (nextEnabled && com.reiux.lumen.ui.components.requiresAllFilesPermission(context, folder.path)) {
                            onRequestAllFilesToggle(folder)
                        } else {
                            onToggleFolder(folder.path, folder.accountEmail, nextEnabled)
                        }
                    }
                )

                Spacer(modifier = Modifier.width(4.dp))

                ExpressiveIconButton(
                    onClick = { onEditFolder(folder) }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.folders_edit_desc),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                ExpressiveIconButton(
                    onClick = { onRemoveFolder(folder) }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.btn_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Row 2: Media type chip, Subfolders chip, Auto-delete badge, and Album label
            val showRow2 = mediaKind != null || folder.deleteAfterBackup || (folder.enabled && (folder.includeSubfolders || folder.targetAlbumMode != AlbumTargetMode.NONE))
            if (showRow2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (mediaKind != null) {
                        FolderMediaTypeChip(
                            kind = mediaKind,
                            enabled = folder.enabled,
                            compact = true
                        )
                    }

                    if (folder.enabled && folder.includeSubfolders) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.tag_subfolders),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (folder.deleteAfterBackup) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = stringResource(R.string.folder_delete_after_backup_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (folder.enabled && folder.targetAlbumMode != AlbumTargetMode.NONE) {
                        val matchingAlbum = remoteAlbums.find {
                            (it.mediaKey == folder.customAlbumName || it.title == folder.customAlbumName) &&
                                (folder.accountEmail.isNullOrBlank() || it.accountEmail.isNullOrBlank() || it.accountEmail.equals(folder.accountEmail, ignoreCase = true))
                        }
                        val isMissing = folder.isMissingAlbum || matchingAlbum?.isMissing == true
                        val albumNickname = when (folder.targetAlbumMode) {
                            AlbumTargetMode.AUTO_FOLDER_NAME -> folder.displayName
                            AlbumTargetMode.CUSTOM_NAME -> folder.customAlbumNickname ?: folder.customAlbumName ?: folder.displayName
                            else -> ""
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = if (isMissing) Icons.Rounded.Warning else Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                tint = if (isMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isMissing) stringResource(R.string.folder_album_missing, albumNickname) else albumNickname,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (folder.accountEmail.isNullOrBlank() && accountsCount > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.folder_unassigned_warning),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Row 3: Path (small, ellipsize)
            Text(
                text = folder.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (onSelect != null) {
        Surface(
            onClick = onSelect,
            modifier = modifier.fillMaxWidth(),
            shape = cardShape,
            color = cardColor,
            border = cardBorder,
            content = cardContent
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = cardShape,
            color = cardColor,
            border = cardBorder,
            content = cardContent
        )
    }
}
