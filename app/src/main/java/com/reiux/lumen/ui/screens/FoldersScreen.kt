package com.reiux.lumen.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reiux.lumen.R
import com.reiux.lumen.data.model.AlbumTargetMode
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.ui.components.AddFolderDialog
import com.reiux.lumen.ui.components.ExpressiveButton
import com.reiux.lumen.ui.components.ExpressiveIconButton
import com.reiux.lumen.ui.components.ExpressiveSwitch
import com.reiux.lumen.ui.components.groupPositionOf
import com.reiux.lumen.ui.components.groupedCardShape

@Composable
fun FoldersScreen(
    folders: List<BackupFolder>,
    accounts: List<GoogleAccount> = emptyList(),
    activeAccount: GoogleAccount? = null,
    remoteAlbums: List<com.reiux.lumen.data.network.GoogleAlbum> = emptyList(),
    onToggleFolder: (path: String, accountEmail: String?, enabled: Boolean) -> Unit,
    onSetIncludeSubfolders: (path: String, accountEmail: String?, includeSubfolders: Boolean) -> Unit,
    onAddFolder: (BackupFolder) -> Unit,
    onRemoveFolder: (path: String, accountEmail: String?) -> Unit,
    onAddAlbum: (keyOrUrl: String, nickname: String, targetEmail: String?) -> com.reiux.lumen.data.network.GoogleAlbum = { k, n, e -> com.reiux.lumen.data.network.GoogleAlbum(k, n, e) },
    onOpenAlbumManager: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var folderToEdit by remember { mutableStateOf<BackupFolder?>(null) }
    var folderToRemove by remember { mutableStateOf<BackupFolder?>(null) }
    var showAllFilesDialog by remember { mutableStateOf(false) }
    var pendingFolderToAdd by remember { mutableStateOf<BackupFolder?>(null) }
    var pendingFolderToToggle by remember { mutableStateOf<BackupFolder?>(null) }

    if (folderToEdit != null) {
        com.reiux.lumen.ui.components.EditFolderDialog(
            folder = folderToEdit!!,
            accounts = accounts,
            activeAccount = activeAccount,
            remoteAlbums = remoteAlbums,
            onAddAlbum = onAddAlbum,
            onOpenAlbumManager = onOpenAlbumManager,
            onDismiss = { folderToEdit = null },
            onSaveFolder = { updated ->
                onAddFolder(updated)
                folderToEdit = null
            }
        )
    }

    if (showAllFilesDialog) {
        com.reiux.lumen.ui.components.AllFilesPermissionDialog(
            onConfirm = {
                pendingFolderToAdd?.let { onAddFolder(it) }
                pendingFolderToToggle?.let { onToggleFolder(it.path, it.accountEmail, true) }
                pendingFolderToAdd = null
                pendingFolderToToggle = null
            },
            onDismiss = {
                showAllFilesDialog = false
                pendingFolderToAdd = null
                pendingFolderToToggle = null
                Toast.makeText(context, context.getString(R.string.toast_all_files_permission_denied), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (folderToRemove != null) {
        val target = folderToRemove!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { folderToRemove = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Remove backup folder?", fontWeight = FontWeight.Bold) },
            text = { Text("Lumen will stop monitoring '${target.displayName}'. Your local files will stay safe on your device.") },
            confirmButton = {
                com.reiux.lumen.ui.components.ExpressiveButton(
                    onClick = {
                        onRemoveFolder(target.path, target.accountEmail)
                        folderToRemove = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
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
                if (folder.path.contains("com.whatsapp") || !folder.path.startsWith("/sdcard/Android")) {
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

    var selectedAccountFilter by remember { mutableStateOf<String?>(null) }

    val filteredFolders = remember(folders, selectedAccountFilter) {
        if (selectedAccountFilter == null) {
            folders
        } else if (selectedAccountFilter == "unassigned") {
            folders.filter { it.accountEmail.isNullOrBlank() }
        } else {
            folders.filter { it.accountEmail.equals(selectedAccountFilter, ignoreCase = true) }
        }
    }

    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val lazyListState = rememberLazyListState()

    if (isWideLayout) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Pane: Title & Controls
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Backup Folders",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Photos and videos in enabled folders will be backed up automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExpressiveButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Folder")
                }
            }

            // Right Pane: Folder Items List
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                item {
                    AccountFilterChips(
                        accounts = accounts,
                        folders = folders,
                        selectedFilter = selectedAccountFilter,
                        onSelectFilter = { selectedAccountFilter = it }
                    )
                }
                itemsIndexed(filteredFolders) { index, folder ->
                    FolderRowItem(
                        folder = folder,
                        position = groupPositionOf(index, filteredFolders.size),
                        accounts = accounts,
                        remoteAlbums = remoteAlbums,
                        accountsCount = accounts.size,
                        onToggleFolder = onToggleFolder,
                        onRequestAllFilesToggle = { targetFolder ->
                            pendingFolderToToggle = targetFolder
                            showAllFilesDialog = true
                        },
                        onSetIncludeSubfolders = onSetIncludeSubfolders,
                        onEditFolder = { folderToEdit = it },
                        onRemoveFolder = { folderToRemove = it }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    } else {
        // Portrait Single Column
        LazyColumn(
            state = lazyListState,
            modifier = modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Backup Folders",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Photos and videos in enabled folders will be backed up",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (remoteAlbums.any { it.isMissing } || folders.any { it.isMissingAlbum }) {
                    Spacer(modifier = Modifier.height(8.dp))
                    com.reiux.lumen.ui.components.MissingAlbumBanner(
                        accountName = activeAccount?.displayName ?: activeAccount?.email ?: "your account",
                        onOpenAlbums = onOpenAlbumManager
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                AccountFilterChips(
                    accounts = accounts,
                    folders = folders,
                    selectedFilter = selectedAccountFilter,
                    onSelectFilter = { selectedAccountFilter = it }
                )
            }

            itemsIndexed(filteredFolders) { index, folder ->
                FolderRowItem(
                    folder = folder,
                    position = groupPositionOf(index, filteredFolders.size),
                    accounts = accounts,
                    remoteAlbums = remoteAlbums,
                    accountsCount = accounts.size,
                    onToggleFolder = onToggleFolder,
                    onRequestAllFilesToggle = { targetFolder ->
                        pendingFolderToToggle = targetFolder
                        showAllFilesDialog = true
                    },
                    onSetIncludeSubfolders = onSetIncludeSubfolders,
                    onEditFolder = { folderToEdit = it },
                    onRemoveFolder = { folderToRemove = it }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                ExpressiveButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Folder")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
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
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            val isSelected = selectedFilter == null
            val scale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isSelected) 1.04f else 1f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
                label = "chipScaleAll"
            )
            FilterChip(
                selected = isSelected,
                onClick = {
                    try { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                    onSelectFilter(null)
                },
                label = { Text("All (${folders.size})", fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.scale(scale)
            )
        }
        items(accounts) { acc ->
            val name = acc.displayName?.takeIf { it.isNotBlank() && it != "null" } ?: acc.email.substringBefore("@")
            val count = folders.count { it.accountEmail.equals(acc.email, ignoreCase = true) }
            val isSelected = selectedFilter.equals(acc.email, ignoreCase = true)
            val scale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isSelected) 1.04f else 1f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
                label = "chipScaleAccount"
            )
            FilterChip(
                selected = isSelected,
                onClick = {
                    try { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                    onSelectFilter(if (isSelected) null else acc.email)
                },
                label = { Text("$name ($count)", fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    com.reiux.lumen.ui.components.AccountAvatar(account = acc, size = 18.dp)
                },
                modifier = Modifier.scale(scale)
            )
        }
        val unassignedCount = folders.count { it.accountEmail.isNullOrBlank() }
        if (unassignedCount > 0) {
            item {
                val isUnassignedSelected = selectedFilter == "unassigned"
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isUnassignedSelected) 1.04f else 1f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
                    label = "chipScaleUnassigned"
                )
                FilterChip(
                    selected = isUnassignedSelected,
                    onClick = {
                        try { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                        onSelectFilter(if (isUnassignedSelected) null else "unassigned")
                    },
                    label = { Text("Unassigned ($unassignedCount)", fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.scale(scale)
                )
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
    accountsCount: Int = 1,
    onToggleFolder: (path: String, accountEmail: String?, enabled: Boolean) -> Unit,
    onRequestAllFilesToggle: (folder: BackupFolder) -> Unit,
    onSetIncludeSubfolders: (path: String, accountEmail: String?, includeSubfolders: Boolean) -> Unit,
    onEditFolder: (BackupFolder) -> Unit,
    onRemoveFolder: (folder: BackupFolder) -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = groupedCardShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Folder Icon, Folder Name (Ellipsize, Weight 1), Switch, Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = if (folder.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

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
                    onClick = { onEditFolder(folder) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                ExpressiveIconButton(
                    onClick = { onRemoveFolder(folder) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove folder",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Row 2: Subfolders chip, Album text line ("→ <album nickname>"), Account Tag (if accounts.size > 1)
            val showRow2 = folder.includeSubfolders || (folder.targetAlbumMode != AlbumTargetMode.NONE && folder.enabled) || (accountsCount > 1 && !folder.accountEmail.isNullOrBlank())
            if (showRow2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (folder.includeSubfolders && folder.enabled) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "subfolders",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (folder.targetAlbumMode != AlbumTargetMode.NONE && folder.enabled) {
                        val matchingAlbum = remoteAlbums.find { (it.mediaKey == folder.customAlbumName || it.title == folder.customAlbumName) }
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
                                text = if (isMissing) "→ $albumNickname (Missing in Google Photos)" else "→ $albumNickname",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (accountsCount > 1 && !folder.accountEmail.isNullOrBlank()) {
                        val acc = accounts.find { it.email.equals(folder.accountEmail, ignoreCase = true) }
                        com.reiux.lumen.ui.components.AccountAvatar(account = acc, emailFallback = folder.accountEmail, size = 20.dp)
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
                            text = "Unassigned folder: Please choose an account",
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
}
