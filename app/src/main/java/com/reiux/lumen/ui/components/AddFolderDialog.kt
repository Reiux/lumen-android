package com.reiux.lumen.ui.components

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import com.reiux.lumen.data.model.AlbumTargetMode
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.reiux.lumen.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.model.GoogleAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFolderDialog(
    accounts: List<GoogleAccount> = emptyList(),
    activeAccount: GoogleAccount? = null,
    remoteAlbums: List<com.reiux.lumen.data.network.GoogleAlbum>,
    onAddAlbum: (keyOrUrl: String, nickname: String, targetEmail: String?) -> com.reiux.lumen.data.network.GoogleAlbum = { k, n, e -> com.reiux.lumen.data.network.GoogleAlbum(k, n, e) },
    onOpenAlbumManager: () -> Unit = {},
    onDismiss: () -> Unit,
    onAddFolder: (BackupFolder) -> Unit
) {
    val context = LocalContext.current
    var path by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var assignedAccountEmail by rememberSaveable { mutableStateOf(activeAccount?.email) }
    val selectedAccount = remember(assignedAccountEmail, activeAccount, accounts) {
        accounts.firstOrNull { !assignedAccountEmail.isNullOrBlank() && it.email.equals(assignedAccountEmail, ignoreCase = true) }
            ?: activeAccount
            ?: accounts.firstOrNull()
    }
    var albumChoice by rememberSaveable(stateSaver = AlbumChoiceSaver) { mutableStateOf<AlbumChoice>(AlbumChoice.None) }
    var deleteAfterBackup by remember { mutableStateOf(false) }

    val presets = remember {
        listOf(
            "Camera" to "/sdcard/DCIM/Camera",
            "Screenshots" to "/sdcard/Pictures/Screenshots",
            "Pictures" to "/sdcard/Pictures",
            "Downloads" to "/sdcard/Download",
            "WhatsApp" to "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images",
            "Messenger" to "/sdcard/Pictures/Messenger",
            "Instagram" to "/sdcard/Pictures/Instagram",
            "Telegram" to "/sdcard/Telegram",
            "Facebook" to "/sdcard/DCIM/Facebook",
            "TikTok" to "/sdcard/Movies/TikTok",
            "Reddit" to "/sdcard/Pictures/Reddit"
        )
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val resolved = resolveFolderPathFromUri(context, uri)
            if (resolved != null) {
                path = resolved.first
                if (displayName.isBlank() || presets.any { it.first == displayName }) {
                    displayName = resolved.second
                }
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val foldPosture = LocalFoldPosture.current

    PredictiveAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        tabletopPlacement = TabletopDialogPlacement.TOP_HALF,
        fillTabletopHalf = true,
        modifier = if (foldPosture.isTabletop) {
            Modifier.fillMaxWidth().fillMaxHeight()
        } else {
            Modifier
                .widthIn(
                    min = 340.dp,
                    max = if (isWideLayout) 700.dp else 500.dp
                )
                .padding(horizontal = 16.dp)
        },
        title = {
            Text(
                text = stringResource(R.string.add_folder_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            val scrollState = rememberScrollState()

            if (isWideLayout) {
                // Split 2-Column Wide Landscape / Tablet Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Presets & Path Summary
                    Column(modifier = Modifier.weight(0.5f)) {
                        Text(
                            text = stringResource(R.string.add_folder_quick_presets),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { (name, presetPath) ->
                                ExpressivePresetChip(
                                    name = name,
                                    isSelected = path == presetPath,
                                    onClick = {
                                        path = presetPath
                                        displayName = name
                                    }
                                )
                            }
                        }

                        if (path.isNotBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.add_folder_selected_path),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = path,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right Column: Custom File Manager Picker & Folder Name Input
                    Column(modifier = Modifier.weight(0.5f)) {
                        Text(
                            text = stringResource(R.string.add_folder_custom_header),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        ExpressiveTonalButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (path.isBlank()) stringResource(R.string.albums_choose_folder_custom) else stringResource(R.string.albums_change_folder_custom)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text(stringResource(R.string.add_folder_folder_name)) },
                            placeholder = { Text(stringResource(R.string.add_folder_hint_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        if (accounts.size > 1) {
                            Spacer(modifier = Modifier.height(14.dp))
                            AccountPickerRow(
                                accounts = accounts,
                                selectedAccount = selectedAccount,
                                onSelectAccount = { acc ->
                                    assignedAccountEmail = acc.email
                                    albumChoice = AlbumChoice.None
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

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
                            folderDisplayName = displayName.ifBlank { "Folder" },
                            onAddAlbum = onAddAlbum,
                            onOpenAlbumManager = {
                                onDismiss()
                                onOpenAlbumManager()
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        DeleteAfterBackupOptionRow(
                            checked = deleteAfterBackup,
                            onCheckedChange = { deleteAfterBackup = it }
                        )
                    }
                }
            } else {
                // Portrait Single Column Layout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = stringResource(R.string.add_folder_quick_presets),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { (name, presetPath) ->
                            ExpressivePresetChip(
                                name = name,
                                isSelected = path == presetPath,
                                onClick = {
                                    path = presetPath
                                    displayName = name
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.add_folder_custom_header),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ExpressiveTonalButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (path.isBlank()) stringResource(R.string.albums_choose_folder_custom) else stringResource(R.string.albums_change_folder_custom)
                        )
                    }

                    if (path.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.add_folder_selected_path),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = path,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(stringResource(R.string.add_folder_folder_name)) },
                        placeholder = { Text(stringResource(R.string.add_folder_hint_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    if (accounts.size > 1) {
                        Spacer(modifier = Modifier.height(14.dp))
                        AccountPickerRow(
                            accounts = accounts,
                            selectedAccount = selectedAccount,
                            onSelectAccount = { acc ->
                                assignedAccountEmail = acc.email
                                albumChoice = AlbumChoice.None // Rule: Changing account resets album choice to No album
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

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
                        folderDisplayName = displayName.ifBlank { "Folder" },
                        onAddAlbum = onAddAlbum,
                        onOpenAlbumManager = {
                            onDismiss()
                            onOpenAlbumManager()
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    DeleteAfterBackupOptionRow(
                        checked = deleteAfterBackup,
                        onCheckedChange = { deleteAfterBackup = it }
                    )
                }
            }
        },
        confirmButton = {
            ExpressiveButton(
                onClick = {
                    if (path.isNotBlank()) {
                        val name = displayName.ifBlank {
                            path.trimEnd('/').substringAfterLast('/').ifEmpty { "Folder" }
                        }
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
                        onAddFolder(
                            BackupFolder(
                                path = path.trim(),
                                displayName = name,
                                enabled = true,
                                includeSubfolders = false,
                                deleteAfterBackup = deleteAfterBackup,
                                targetAlbumMode = mode,
                                customAlbumName = albumName,
                                customAlbumNickname = albumNickname,
                                accountEmail = selectedAccount?.email ?: assignedAccountEmail ?: activeAccount?.email ?: accounts.firstOrNull()?.email
                            )
                        )
                    }
                },
                enabled = path.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(stringResource(R.string.btn_add_folder))
            }
        },
        dismissButton = {
            ExpressiveTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
private fun DeleteAfterBackupOptionRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val toggle: (Boolean) -> Unit = { next ->
        try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
        onCheckedChange(next)
    }
    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "addDeleteAfterBackupContainer"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { toggle(!checked) }
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.folder_delete_after_backup_title),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (checked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.folder_delete_after_backup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (checked) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ExpressiveSwitch(
                checked = checked,
                onCheckedChange = toggle
            )
        }
    }
}

@Composable
private fun ExpressivePresetChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isGesturePressed by rememberExpressivePressed(interactionSource)
    var clickPulse by remember { mutableStateOf(false) }
    val isPressed = isGesturePressed || clickPulse

    val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
        coroutineScope.launch {
            clickPulse = true
            delay(180L)
            clickPulse = false
        }
        onClick()
    }

    val cornerRadius by animateDpAsState(
        targetValue = when {
            isPressed -> 8.dp
            isSelected -> 20.dp
            else -> 10.dp
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "presetChipCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "presetChipScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "presetChipContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "presetChipBorder"
    )
    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        onClick = hapticClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .scale(scale),
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = borderColor
        ),
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Resolves a storage framework tree Uri (SAF) into a filesystem path and human-readable folder name.
 */
private fun resolveFolderPathFromUri(context: Context, uri: Uri): Pair<String, String>? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val basePath = Environment.getExternalStorageDirectory().absolutePath

        val (path, name) = if (docId.startsWith("primary:")) {
            val relPath = docId.removePrefix("primary:")
            val path = if (relPath.isEmpty()) basePath else "$basePath/$relPath".trimEnd('/')
            val name = relPath.trimEnd('/').substringAfterLast('/').ifEmpty { context.getString(R.string.add_folder_internal_storage) }
            path to name
        } else if (docId.contains(":")) {
            val parts = docId.split(":", limit = 2)
            val volume = parts[0]
            val relPath = parts.getOrElse(1) { "" }
            val path = "/storage/$volume/$relPath".trimEnd('/')
            val name = relPath.trimEnd('/').substringAfterLast('/').ifEmpty { volume }
            path to name
        } else {
            val name = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: context.getString(R.string.add_folder_fallback_name)
            (uri.path ?: "") to name
        }
        path to name
    } catch (_: Exception) {
        val fallbackName = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: context.getString(R.string.add_folder_fallback_name)
        (uri.path ?: "") to fallbackName
    }
}
