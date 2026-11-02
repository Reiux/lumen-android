package com.reiux.lumen.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reiux.lumen.data.network.GoogleAlbum

sealed class AlbumChoice {
    object None : AlbumChoice()
    object AutoFolderName : AlbumChoice()
    object ByMonth : AlbumChoice()
    data class CustomAlbum(val album: GoogleAlbum) : AlbumChoice()

    fun displayLabel(folderName: String? = null, monthName: String? = null): String {
        return when (this) {
            is None -> "No album"
            is AutoFolderName -> if (folderName != null) "Named after folder ($folderName)" else "Named after the folder"
            is ByMonth -> if (monthName != null) "By month ($monthName)" else "By month"
            is CustomAlbum -> album.title
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerRow(
    choice: AlbumChoice,
    onChoiceSelected: (AlbumChoice) -> Unit,
    savedAlbums: List<GoogleAlbum>,
    accounts: List<com.reiux.lumen.data.model.GoogleAccount> = emptyList(),
    activeAccount: com.reiux.lumen.data.model.GoogleAccount? = null,
    isManualUploadDialog: Boolean = false,
    folderDisplayName: String? = null,
    currentMonthName: String? = null,
    onAddAlbum: (keyOrUrl: String, nickname: String, targetEmail: String?) -> GoogleAlbum = { key, nick, email -> GoogleAlbum(key, nick, email) },
    onOpenAlbumManager: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    var showAddAlbumSheet by remember { mutableStateOf(false) }

    val accountAlbums = remember(savedAlbums, activeAccount) {
        val email = activeAccount?.email
        if (email.isNullOrBlank()) emptyList()
        else savedAlbums.filter { !it.accountEmail.isNullOrBlank() && it.accountEmail.equals(email, ignoreCase = true) }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { showSheet = true },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Album  ·  ${choice.displayLabel(folderDisplayName, currentMonthName)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Select album",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Select Album Destination",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Option 1: No album
                    item {
                        RadioChoiceRow(
                            title = "No album",
                            subtitle = "Save to main Google Photos timeline",
                            isSelected = choice is AlbumChoice.None,
                            onClick = {
                                onChoiceSelected(AlbumChoice.None)
                                showSheet = false
                            }
                        )
                    }

                    // Option 2: Named after folder (folder dialogs only)
                    if (!isManualUploadDialog) {
                        item {
                            RadioChoiceRow(
                                title = "Named after the folder",
                                subtitle = if (folderDisplayName != null) "Album name: '$folderDisplayName'" else null,
                                isSelected = choice is AlbumChoice.AutoFolderName,
                                onClick = {
                                    onChoiceSelected(AlbumChoice.AutoFolderName)
                                    showSheet = false
                                }
                            )
                        }
                    }

                    // Option 3: By month (Manual upload dialog only)
                    if (isManualUploadDialog) {
                        item {
                            RadioChoiceRow(
                                title = "By month",
                                subtitle = if (currentMonthName != null) "Album name: 'Uploads $currentMonthName'" else null,
                                isSelected = choice is AlbumChoice.ByMonth,
                                onClick = {
                                    onChoiceSelected(AlbumChoice.ByMonth)
                                    showSheet = false
                                }
                            )
                        }
                    }

                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    // Option 4: Saved albums
                    if (accountAlbums.isEmpty()) {
                        item {
                            Text(
                                text = "No albums yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(accountAlbums, key = { "${it.accountEmail}:${it.mediaKey}:${it.title}" }) { album ->
                            RadioChoiceRow(
                                title = album.title,
                                subtitle = album.getSubtitle(),
                                isSelected = choice is AlbumChoice.CustomAlbum && (choice as AlbumChoice.CustomAlbum).album.mediaKey == album.mediaKey,
                                onClick = {
                                    onChoiceSelected(AlbumChoice.CustomAlbum(album))
                                    showSheet = false
                                }
                            )
                        }
                    }

                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    // Add album & Manage albums
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAddAlbumSheet = true
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Add album",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            TextButton(
                                onClick = {
                                    showSheet = false
                                    onOpenAlbumManager()
                                }
                            ) {
                                Text("Manage albums")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showAddAlbumSheet) {
        AddAlbumBottomSheet(
            accounts = accounts,
            activeAccount = activeAccount,
            albums = savedAlbums,
            onDismiss = { showAddAlbumSheet = false },
            onSaveAlbum = { keyOrUrl, nickname, targetEmail ->
                val created = onAddAlbum(keyOrUrl, nickname, targetEmail)
                onChoiceSelected(AlbumChoice.CustomAlbum(created))
                created
            }
        )
    }
}

@Composable
private fun RadioChoiceRow(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
