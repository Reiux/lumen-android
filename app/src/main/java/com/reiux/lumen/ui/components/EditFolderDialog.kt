package com.reiux.lumen.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.reiux.lumen.R
import com.reiux.lumen.ui.components.AlbumPickerDropdown
import com.reiux.lumen.data.model.AlbumTargetMode
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.model.GoogleAccount

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditFolderDialog(
    folder: BackupFolder,
    accounts: List<GoogleAccount> = emptyList(),
    activeAccount: GoogleAccount? = null,
    remoteAlbums: List<com.reiux.lumen.data.network.GoogleAlbum>,
    onAddAlbum: (keyOrUrl: String, nickname: String, targetEmail: String?) -> com.reiux.lumen.data.network.GoogleAlbum = { k, n, e -> com.reiux.lumen.data.network.GoogleAlbum(k, n, e) },
    onOpenAlbumManager: () -> Unit = {},
    onSyncToAlbum: ((BackupFolder) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSaveFolder: (BackupFolder) -> Unit
) {
    var displayName by rememberSaveable(folder.path, folder.accountEmail) { mutableStateOf(folder.displayName) }
    var includeSubfolders by rememberSaveable(folder.path, folder.accountEmail) { mutableStateOf(folder.includeSubfolders) }
    var deleteAfterBackup by remember(folder) { mutableStateOf(folder.deleteAfterBackup) }
    var assignedAccountEmail by rememberSaveable(folder.path, folder.accountEmail) { mutableStateOf(folder.accountEmail ?: activeAccount?.email) }
    val selectedAccount = remember(assignedAccountEmail, activeAccount, accounts) {
        accounts.firstOrNull { !assignedAccountEmail.isNullOrBlank() && it.email.equals(assignedAccountEmail, ignoreCase = true) }
            ?: activeAccount
            ?: accounts.firstOrNull()
    }

    val initialChoice = remember(folder, remoteAlbums) {
        when (folder.targetAlbumMode) {
            AlbumTargetMode.NONE -> AlbumChoice.None
            AlbumTargetMode.AUTO_FOLDER_NAME -> AlbumChoice.AutoFolderName
            AlbumTargetMode.CUSTOM_NAME -> {
                val key = folder.customAlbumName ?: ""
                val matched = remoteAlbums.find {
                    (it.mediaKey == key || it.title == key) &&
                        (it.accountEmail.isNullOrBlank() || assignedAccountEmail.isNullOrBlank() || it.accountEmail.equals(assignedAccountEmail, ignoreCase = true))
                } ?: remoteAlbums.find { it.mediaKey == key || it.title == key }
                if (matched != null) AlbumChoice.CustomAlbum(matched)
                else if (key.isNotBlank()) AlbumChoice.CustomAlbum(com.reiux.lumen.data.network.GoogleAlbum(mediaKey = key, title = folder.customAlbumNickname ?: key, accountEmail = assignedAccountEmail))
                else AlbumChoice.None
            }
        }
    }
    var albumChoice by rememberSaveable(folder.path, folder.accountEmail, stateSaver = AlbumChoiceSaver) { mutableStateOf<AlbumChoice>(initialChoice) }

    PredictiveAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(min = 320.dp, max = 500.dp)
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(R.string.edit_folder_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.add_folder_folder_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                if (accounts.size > 1) {
                    AccountPickerRow(
                        accounts = accounts,
                        selectedAccount = selectedAccount,
                        onSelectAccount = { acc ->
                            assignedAccountEmail = acc.email
                            albumChoice = AlbumChoice.None
                        }
                    )
                }

                val accountAlbums = remember(remoteAlbums, selectedAccount) {
                    val email = selectedAccount?.email
                    if (email.isNullOrBlank()) remoteAlbums
                    else remoteAlbums.filter { it.accountEmail.isNullOrBlank() || it.accountEmail.equals(email, ignoreCase = true) }
                }

                AlbumPickerRow(
                    choice = albumChoice,
                    onChoiceSelected = { albumChoice = it },
                    savedAlbums = accountAlbums,
                    accounts = accounts,
                    activeAccount = selectedAccount,
                    folderDisplayName = displayName.ifBlank { folder.displayName },
                    onAddAlbum = onAddAlbum,
                    onOpenAlbumManager = {
                        onDismiss()
                        onOpenAlbumManager()
                    }
                )

                if (onSyncToAlbum != null && folder.enabled && albumChoice !is AlbumChoice.None) {
                    ExpressiveTonalButton(
                        onClick = {
                            val selectedEmail = selectedAccount?.email
                            val (mode, albumName, albumNickname) = when (val c = albumChoice) {
                                is AlbumChoice.AutoFolderName -> Triple(AlbumTargetMode.AUTO_FOLDER_NAME, null, null)
                                is AlbumChoice.CustomAlbum -> {
                                    if (!c.album.accountEmail.isNullOrBlank() && !selectedEmail.isNullOrBlank() && !c.album.accountEmail.equals(selectedEmail, ignoreCase = true)) {
                                        albumChoice = AlbumChoice.None
                                        return@ExpressiveTonalButton
                                    }
                                    Triple(AlbumTargetMode.CUSTOM_NAME, c.album.mediaKey, c.album.title)
                                }
                                else -> Triple(AlbumTargetMode.NONE, null, null)
                            }
                            if (mode != AlbumTargetMode.NONE) {
                                val updated = folder.copy(
                                    displayName = displayName.ifBlank { folder.displayName },
                                    includeSubfolders = includeSubfolders,
                                    deleteAfterBackup = deleteAfterBackup,
                                    targetAlbumMode = mode,
                                    customAlbumName = albumName,
                                    customAlbumNickname = albumNickname,
                                    accountEmail = selectedAccount?.email ?: assignedAccountEmail ?: folder.accountEmail ?: activeAccount?.email ?: accounts.firstOrNull()?.email
                                )
                                onSaveFolder(updated)
                                onSyncToAlbum(updated)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_sync_to_album_now))
                    }
                }

                val editHaptic = LocalHapticFeedback.current
                val toggleSubfolders: (Boolean) -> Unit = { next ->
                    try { editHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                    includeSubfolders = next
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggleSubfolders(!includeSubfolders) }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.edit_folder_include_subfolders), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.edit_folder_include_subfolders_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ExpressiveSwitch(
                            checked = includeSubfolders,
                            onCheckedChange = toggleSubfolders
                        )
                    }
                }

                val toggleDeleteAfterBackup: (Boolean) -> Unit = { next ->
                    try { editHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                    deleteAfterBackup = next
                }
                val deleteContainerColor by animateColorAsState(
                    targetValue = if (deleteAfterBackup) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "editDeleteAfterBackupContainer"
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = deleteContainerColor
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggleDeleteAfterBackup(!deleteAfterBackup) }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = if (deleteAfterBackup) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.folder_delete_after_backup_title),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (deleteAfterBackup) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.folder_delete_after_backup_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (deleteAfterBackup) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ExpressiveSwitch(
                            checked = deleteAfterBackup,
                            onCheckedChange = toggleDeleteAfterBackup
                        )
                    }
                }
            }
        },
        confirmButton = {
            ExpressiveButton(
                onClick = {
                    val selectedEmail = selectedAccount?.email
                    val (mode, albumName, albumNickname) = when (val c = albumChoice) {
                        is AlbumChoice.None -> Triple(AlbumTargetMode.NONE, null, null)
                        is AlbumChoice.AutoFolderName -> Triple(AlbumTargetMode.AUTO_FOLDER_NAME, null, null)
                        is AlbumChoice.CustomAlbum -> {
                            if (!c.album.accountEmail.isNullOrBlank() && !selectedEmail.isNullOrBlank() && !c.album.accountEmail.equals(selectedEmail, ignoreCase = true)) {
                                albumChoice = AlbumChoice.None
                                return@ExpressiveButton
                            }
                            Triple(AlbumTargetMode.CUSTOM_NAME, c.album.mediaKey, c.album.title)
                        }
                        else -> Triple(AlbumTargetMode.NONE, null, null)
                    }
                    onSaveFolder(
                        folder.copy(
                            displayName = displayName.ifBlank { folder.displayName },
                            includeSubfolders = includeSubfolders,
                            deleteAfterBackup = deleteAfterBackup,
                            targetAlbumMode = mode,
                            customAlbumName = albumName,
                            customAlbumNickname = albumNickname,
                            accountEmail = selectedAccount?.email ?: assignedAccountEmail ?: folder.accountEmail ?: activeAccount?.email ?: accounts.firstOrNull()?.email
                        )
                    )
                },
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(stringResource(R.string.edit_folder_save_changes))
            }
        },
        dismissButton = {
            ExpressiveTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
