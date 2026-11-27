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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reiux.lumen.R
import com.reiux.lumen.data.network.GoogleAlbum

sealed class AlbumChoice {
    object None : AlbumChoice()
    object AutoFolderName : AlbumChoice()
    object ByMonth : AlbumChoice()
    data class CustomAlbum(val album: GoogleAlbum) : AlbumChoice()

    @Composable
    fun displayLabel(folderName: String? = null, monthName: String? = null): String {
        return when (this) {
            is None -> stringResource(R.string.album_choice_none)
            is AutoFolderName -> if (folderName != null) stringResource(R.string.album_choice_folder, folderName) else stringResource(R.string.album_choice_folder_generic)
            is ByMonth -> if (monthName != null) stringResource(R.string.album_choice_month, monthName) else stringResource(R.string.album_choice_month_generic)
            is CustomAlbum -> album.title
        }
    }
}

val AlbumChoiceSaver: Saver<AlbumChoice, ArrayList<String>> = Saver(
    save = { choice ->
        when (choice) {
            is AlbumChoice.None -> arrayListOf("none")
            is AlbumChoice.AutoFolderName -> arrayListOf("auto")
            is AlbumChoice.ByMonth -> arrayListOf("month")
            is AlbumChoice.CustomAlbum -> arrayListOf(
                "custom",
                choice.album.mediaKey,
                choice.album.title,
                choice.album.accountEmail ?: "",
                choice.album.isMissing.toString()
            )
        }
    },
    restore = { list ->
        when (list.firstOrNull()) {
            "auto" -> AlbumChoice.AutoFolderName
            "month" -> AlbumChoice.ByMonth
            "custom" -> AlbumChoice.CustomAlbum(
                GoogleAlbum(
                    mediaKey = list.getOrElse(1) { "" },
                    title = list.getOrElse(2) { "" },
                    accountEmail = list.getOrElse(3) { "" }.ifBlank { null },
                    isMissing = list.getOrElse(4) { "false" }.toBoolean()
                )
            )
            else -> AlbumChoice.None
        }
    }
)

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
    var showSheet by rememberSaveable { mutableStateOf(false) }

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
                    text = stringResource(R.string.album_row_prefix, choice.displayLabel(folderDisplayName, currentMonthName)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.album_desc_select),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showSheet) {
        AlbumDestinationBottomSheet(
            choice = choice,
            onChoiceSelected = {
                onChoiceSelected(it)
                showSheet = false
            },
            savedAlbums = savedAlbums,
            accounts = accounts,
            activeAccount = activeAccount,
            isManualUploadDialog = isManualUploadDialog,
            folderDisplayName = folderDisplayName,
            currentMonthName = currentMonthName,
            onAddAlbum = onAddAlbum,
            onOpenAlbumManager = {
                showSheet = false
                onOpenAlbumManager()
            },
            onDismiss = { showSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDestinationBottomSheet(
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
    onDismiss: () -> Unit
) {
    var showAddAlbumSheet by rememberSaveable { mutableStateOf(false) }
    val accountAlbums = remember(savedAlbums, activeAccount) {
        val email = activeAccount?.email
        if (email.isNullOrBlank()) savedAlbums
        else savedAlbums.filter { it.accountEmail.isNullOrBlank() || it.accountEmail.equals(email, ignoreCase = true) }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        DialogBlurEffect()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.album_sheet_dest_title),
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
                        title = stringResource(R.string.album_choice_none),
                        subtitle = stringResource(R.string.album_choice_none_sub),
                        isSelected = choice is AlbumChoice.None,
                        onClick = { onChoiceSelected(AlbumChoice.None) }
                    )
                }

                // Option 2: Named after folder (folder dialogs only)
                if (!isManualUploadDialog) {
                    item {
                        RadioChoiceRow(
                            title = stringResource(R.string.album_choice_folder_generic),
                            subtitle = if (folderDisplayName != null) {
                                stringResource(R.string.album_choice_folder_sub, folderDisplayName)
                            } else {
                                stringResource(R.string.album_choice_folder_sub_generic)
                            },
                            isSelected = choice is AlbumChoice.AutoFolderName,
                            onClick = { onChoiceSelected(AlbumChoice.AutoFolderName) }
                        )
                    }
                }

                // Option 3: By month (Manual upload dialog only)
                if (isManualUploadDialog) {
                    item {
                        RadioChoiceRow(
                            title = stringResource(R.string.album_choice_month_generic),
                            subtitle = if (currentMonthName != null) stringResource(R.string.album_choice_month_sub, currentMonthName) else null,
                            isSelected = choice is AlbumChoice.ByMonth,
                            onClick = { onChoiceSelected(AlbumChoice.ByMonth) }
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
                            text = stringResource(R.string.album_empty_saved),
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
                            isSelected = choice is AlbumChoice.CustomAlbum && choice.album.mediaKey == album.mediaKey,
                            onClick = { onChoiceSelected(AlbumChoice.CustomAlbum(album)) }
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
                            text = stringResource(R.string.album_btn_add_album),
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
                        ExpressiveTextButton(
                            onClick = {
                                onDismiss()
                                onOpenAlbumManager()
                            }
                        ) {
                            Text(stringResource(R.string.album_btn_manage_albums))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
