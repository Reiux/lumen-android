package com.reiux.lumen.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
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
    onDismiss: () -> Unit,
    onSaveFolder: (BackupFolder) -> Unit
) {
    var displayName by remember { mutableStateOf(folder.displayName) }
    var includeSubfolders by remember { mutableStateOf(folder.includeSubfolders) }
    var assignedAccountEmail by remember { mutableStateOf(folder.accountEmail ?: activeAccount?.email) }
    var selectedAccount by remember(assignedAccountEmail, activeAccount, accounts) {
        mutableStateOf(accounts.firstOrNull { it.email == assignedAccountEmail } ?: activeAccount ?: accounts.firstOrNull())
    }

    val initialChoice = remember(folder, remoteAlbums) {
        when (folder.targetAlbumMode) {
            AlbumTargetMode.NONE -> AlbumChoice.None
            AlbumTargetMode.AUTO_FOLDER_NAME -> AlbumChoice.AutoFolderName
            AlbumTargetMode.CUSTOM_NAME -> {
                val key = folder.customAlbumName ?: ""
                val matched = remoteAlbums.find { it.mediaKey == key || it.title == key }
                if (matched != null) AlbumChoice.CustomAlbum(matched)
                else if (key.isNotBlank()) AlbumChoice.CustomAlbum(com.reiux.lumen.data.network.GoogleAlbum(mediaKey = key, title = folder.customAlbumNickname ?: key, accountEmail = assignedAccountEmail))
                else AlbumChoice.None
            }
        }
    }
    var albumChoice by remember { mutableStateOf<AlbumChoice>(initialChoice) }

    val dialogScale = remember { Animatable(0.88f) }
    LaunchedEffect(Unit) {
        dialogScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(min = 320.dp, max = 500.dp)
            .padding(horizontal = 16.dp)
            .scale(dialogScale.value),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = "Edit Folder Settings",
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
                    label = { Text("Folder Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                if (accounts.size > 1) {
                    AccountPickerRow(
                        accounts = accounts,
                        selectedAccount = selectedAccount,
                        onSelectAccount = { acc ->
                            selectedAccount = acc
                            assignedAccountEmail = acc.email
                            albumChoice = AlbumChoice.None
                        }
                    )
                }

                val accountAlbums = remember(remoteAlbums, selectedAccount) {
                    val email = selectedAccount?.email
                    if (email.isNullOrBlank()) remoteAlbums
                    else remoteAlbums.filter { it.accountEmail == null || it.accountEmail.equals(email, ignoreCase = true) }
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

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Include Subfolders", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("Scan files in nested subfolders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ExpressiveSwitch(
                            checked = includeSubfolders,
                            onCheckedChange = { includeSubfolders = it }
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
                            if (c.album.accountEmail.isNullOrBlank() || !c.album.accountEmail.equals(selectedEmail, ignoreCase = true)) {
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
                            targetAlbumMode = mode,
                            customAlbumName = albumName,
                            customAlbumNickname = albumNickname,
                            accountEmail = selectedAccount?.email ?: assignedAccountEmail ?: folder.accountEmail ?: activeAccount?.email ?: accounts.firstOrNull()?.email
                        )
                    )
                },
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
