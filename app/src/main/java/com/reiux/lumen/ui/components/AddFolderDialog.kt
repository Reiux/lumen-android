package com.reiux.lumen.ui.components

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.reiux.lumen.data.model.BackupFolder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFolderDialog(
    onDismiss: () -> Unit,
    onAddFolder: (BackupFolder) -> Unit
) {
    val context = LocalContext.current
    var path by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    val presets = remember {
        listOf(
            "Camera" to "/sdcard/DCIM/Camera",
            "Screenshots" to "/sdcard/Pictures/Screenshots",
            "Pictures" to "/sdcard/Pictures",
            "Downloads" to "/sdcard/Download",
            "WhatsApp" to "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"
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

    // Dynamic M3E Bouncy Scale Animation for Dialog Entrance
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
            .widthIn(min = 340.dp, max = if (isWideLayout) 700.dp else 500.dp)
            .padding(horizontal = 16.dp)
            .scale(dialogScale.value),
        title = {
            Text(
                text = "Add Backup Folder",
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
                            text = "Quick Presets",
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
                                val haptic = rememberHapticClick {
                                    path = presetPath
                                    displayName = name
                                }
                                FilterChip(
                                    selected = path == presetPath,
                                    onClick = haptic,
                                    label = { Text(name) }
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
                                            text = "Selected Path",
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
                            text = "Custom Folder",
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
                                text = if (path.isBlank()) "Choose Folder..." else "Change Selected..."
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Folder Name") },
                            placeholder = { Text("e.g. Vacation Photos") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
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
                        text = "Quick Presets",
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
                            val haptic = rememberHapticClick {
                                path = presetPath
                                displayName = name
                            }
                            FilterChip(
                                selected = path == presetPath,
                                onClick = haptic,
                                label = { Text(name) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Custom Folder",
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
                            text = if (path.isBlank()) "Choose from File Manager..." else "Change Selected Folder..."
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
                                        text = "Selected Path",
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
                        label = { Text("Folder Name") },
                        placeholder = { Text("e.g. Vacation Photos") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
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
                        onAddFolder(BackupFolder(path = path.trim(), displayName = name, enabled = true))
                    }
                },
                enabled = path.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Add Folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

/**
 * Resolves a storage framework tree Uri (SAF) into a filesystem path and human-readable folder name.
 */
private fun resolveFolderPathFromUri(context: Context, uri: Uri): Pair<String, String>? {
    return try {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
        }

        val docId = DocumentsContract.getTreeDocumentId(uri)
        val basePath = Environment.getExternalStorageDirectory().absolutePath

        val (path, name) = if (docId.startsWith("primary:")) {
            val relPath = docId.removePrefix("primary:")
            val path = if (relPath.isEmpty()) basePath else "$basePath/$relPath".trimEnd('/')
            val name = relPath.trimEnd('/').substringAfterLast('/').ifEmpty { "Internal Storage" }
            path to name
        } else if (docId.contains(":")) {
            val parts = docId.split(":", limit = 2)
            val volume = parts[0]
            val relPath = parts.getOrElse(1) { "" }
            val path = "/storage/$volume/$relPath".trimEnd('/')
            val name = relPath.trimEnd('/').substringAfterLast('/').ifEmpty { volume }
            path to name
        } else {
            val name = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: "Folder"
            (uri.path ?: "") to name
        }
        path to name
    } catch (_: Exception) {
        val fallbackName = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: "Folder"
        (uri.path ?: "") to fallbackName
    }
}
