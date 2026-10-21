package com.reiux.lumen.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.ui.components.AddFolderDialog
import com.reiux.lumen.ui.components.ExpressiveButton
import com.reiux.lumen.ui.components.ExpressiveIconButton
import com.reiux.lumen.ui.components.ExpressiveSwitch
import com.reiux.lumen.ui.components.groupPositionOf
import com.reiux.lumen.ui.components.groupedCardShape

@Composable
fun FoldersScreen(
    folders: List<BackupFolder>,
    onToggleFolder: (path: String, enabled: Boolean) -> Unit,
    onAddFolder: (BackupFolder) -> Unit,
    onRemoveFolder: (path: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddFolderDialog(
            onDismiss = { showAddDialog = false },
            onAddFolder = {
                onAddFolder(it)
                showAddDialog = false
            }
        )
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
                itemsIndexed(folders) { index, folder ->
                    FolderRowItem(
                        folder = folder,
                        position = groupPositionOf(index, folders.size),
                        onToggleFolder = onToggleFolder,
                        onRemoveFolder = onRemoveFolder
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
                Spacer(modifier = Modifier.height(8.dp))
            }

            itemsIndexed(folders) { index, folder ->
                FolderRowItem(
                    folder = folder,
                    position = groupPositionOf(index, folders.size),
                    onToggleFolder = onToggleFolder,
                    onRemoveFolder = onRemoveFolder
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
private fun FolderRowItem(
    folder: BackupFolder,
    position: com.reiux.lumen.ui.components.GroupPosition,
    onToggleFolder: (path: String, enabled: Boolean) -> Unit,
    onRemoveFolder: (path: String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = groupedCardShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = if (folder.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = folder.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            ExpressiveSwitch(
                checked = folder.enabled,
                onCheckedChange = { onToggleFolder(folder.path, it) }
            )

            Spacer(modifier = Modifier.width(4.dp))

            ExpressiveIconButton(
                onClick = { onRemoveFolder(folder.path) },
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
    }
}
