package com.reiux.lumen.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reiux.lumen.R
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.data.network.GoogleAlbum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAlbumBottomSheet(
    accounts: List<GoogleAccount> = emptyList(),
    activeAccount: GoogleAccount? = null,
    albums: List<GoogleAlbum> = emptyList(),
    onDismiss: () -> Unit,
    onSaveAlbum: (keyOrUrl: String, nickname: String, targetEmail: String?) -> GoogleAlbum
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedSegment by remember { mutableStateOf(0) } // 0 = Paste Link/Key, 1 = Create New
    var keyOrUrlInput by remember { mutableStateOf("") }
    var nicknameInput by remember { mutableStateOf("") }
    var newAlbumNameInput by remember { mutableStateOf("") }

    var selectedSheetAccount by remember(activeAccount, accounts) {
        mutableStateOf(activeAccount ?: accounts.firstOrNull())
    }
    var showHelpDialog by remember { mutableStateOf(false) }

    val extractedKey = remember(keyOrUrlInput) { extractKeyFromPastedUrl(keyOrUrlInput).trim() }
    val isValidKeyOrUrl = isValidAlbumKeyOrUrl(keyOrUrlInput)

    val isDuplicate = remember(selectedSegment, extractedKey, newAlbumNameInput, albums, selectedSheetAccount) {
        val effEmail = selectedSheetAccount?.email
        if (selectedSegment == 1) {
            val name = newAlbumNameInput.trim()
            name.isNotBlank() && albums.any { (it.accountEmail == null || it.accountEmail.equals(effEmail, ignoreCase = true)) && it.title.equals(name, ignoreCase = true) }
        } else {
            extractedKey.isNotBlank() && albums.any { (it.accountEmail == null || it.accountEmail.equals(effEmail, ignoreCase = true)) && (it.mediaKey == extractedKey || it.title.equals(nicknameInput.trim(), ignoreCase = true)) }
        }
    }

    val canSave = if (selectedSegment == 1) {
        newAlbumNameInput.isNotBlank() && !isDuplicate
    } else {
        isValidKeyOrUrl && nicknameInput.isNotBlank() && !isDuplicate
    }

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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.album_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Connected Segmented Button Group
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedSegment == 0,
                    onClick = { selectedSegment = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(stringResource(R.string.album_segment_paste), fontSize = 12.sp)
                }
                SegmentedButton(
                    selected = selectedSegment == 1,
                    onClick = { selectedSegment = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(stringResource(R.string.album_segment_create), fontSize = 12.sp)
                }
            }

            // AccountPickerRow under title (hidden for one account)
            if (accounts.size > 1) {
                AccountPickerRow(
                    accounts = accounts,
                    selectedAccount = selectedSheetAccount,
                    onSelectAccount = { selectedSheetAccount = it }
                )
            }

            if (selectedSegment == 1) {
                OutlinedTextField(
                    value = newAlbumNameInput,
                    onValueChange = { newAlbumNameInput = it },
                    label = { Text(stringResource(R.string.album_label_name)) },
                    placeholder = { Text(stringResource(R.string.album_hint_name)) },
                    isError = isDuplicate,
                    supportingText = {
                        if (isDuplicate) {
                            Text(stringResource(R.string.album_err_exists))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            } else {
                OutlinedTextField(
                    value = keyOrUrlInput,
                    onValueChange = { newValue ->
                        keyOrUrlInput = extractKeyFromPastedUrl(newValue)
                    },
                    label = { Text(stringResource(R.string.album_label_link_key)) },
                    placeholder = { Text(stringResource(R.string.album_hint_link_key)) },
                    isError = (keyOrUrlInput.isNotBlank() && !isValidKeyOrUrl) || isDuplicate,
                    supportingText = {
                        if (isDuplicate) {
                            Text(stringResource(R.string.album_err_key_exists))
                        } else if (keyOrUrlInput.isNotBlank() && !isValidKeyOrUrl) {
                            Text(stringResource(R.string.album_err_invalid_key))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = nicknameInput,
                    onValueChange = { nicknameInput = it },
                    label = { Text(stringResource(R.string.album_label_nickname)) },
                    placeholder = { Text(stringResource(R.string.album_hint_nickname)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    TextButton(
                        onClick = { showHelpDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.album_btn_how_find))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                ExpressiveButton(
                    onClick = {
                        if (canSave) {
                            val saved = if (selectedSegment == 1) {
                                onSaveAlbum(newAlbumNameInput.trim(), newAlbumNameInput.trim(), selectedSheetAccount?.email)
                            } else {
                                onSaveAlbum(extractedKey, nicknameInput.trim(), selectedSheetAccount?.email)
                            }
                            onDismiss()
                        }
                    },
                    enabled = canSave
                ) {
                    Text(stringResource(R.string.album_btn_save))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showHelpDialog) {
        FindAlbumLinkHelpDialog(onDismiss = { showHelpDialog = false })
    }
}

private fun extractKeyFromPastedUrl(input: String): String {
    val trimmed = input.trim()
    if (!trimmed.contains("http://") && !trimmed.contains("https://") && !trimmed.contains("photos.google.com") && !trimmed.contains("goo.gl")) {
        return trimmed
    }

    val shareIdx = trimmed.indexOf("/share/")
    if (shareIdx != -1) {
        val key = trimmed.substring(shareIdx + 7).substringBefore('?').substringBefore('/')
        if (key.isNotBlank()) return key
    }
    val albumIdx = trimmed.indexOf("/album/")
    if (albumIdx != -1) {
        val key = trimmed.substring(albumIdx + 7).substringBefore('?').substringBefore('/')
        if (key.isNotBlank()) return key
    }

    val uri = try { java.net.URI(trimmed) } catch (_: Exception) { null }
    val query = uri?.query
    if (query != null) {
        for (param in query.split("&")) {
            val kv = param.split("=")
            if (kv.size == 2 && kv[0] == "key") {
                if (kv[1].isNotBlank()) return kv[1]
            }
        }
    }

    val keyRegex = Regex("(AF1Qip[A-Za-z0-9_-]+)")
    val match = keyRegex.find(trimmed)
    if (match != null) return match.value

    return trimmed
}

fun isValidAlbumKeyOrUrl(input: String): Boolean {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return false
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.contains("photos.google.com") || trimmed.contains("goo.gl")) {
        return true
    }
    val keyRegex = Regex("^AF1Qip[A-Za-z0-9_-]+$")
    if (keyRegex.matches(trimmed)) return true
    if (trimmed.length >= 20 && !trimmed.contains(" ") && trimmed.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
        return true
    }
    return false
}

@Composable
fun FindAlbumLinkHelpDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
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

    PredictiveAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.scale(dialogScale.value),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(R.string.album_help_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HelpStepRow(number = 1, text = stringResource(R.string.album_help_step_1))
                HelpStepRow(number = 2, text = stringResource(R.string.album_help_step_2))
                HelpStepRow(number = 3, text = stringResource(R.string.album_help_step_3))
                HelpStepRow(number = 4, text = stringResource(R.string.album_help_step_4))

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "photos.google.com/album/AF1Qip...",
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.album_help_nickname_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            ExpressiveButton(
                onClick = {
                    try {
                        val browserIntent = android.content.Intent.makeMainSelectorActivity(
                            android.content.Intent.ACTION_MAIN,
                            android.content.Intent.CATEGORY_APP_BROWSER
                        ).apply {
                            data = android.net.Uri.parse("https://photos.google.com/albums")
                        }
                        context.startActivity(browserIntent)
                    } catch (_: Exception) {
                        try {
                            val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://photos.google.com/albums"))
                            context.startActivity(viewIntent)
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(context, context.getString(R.string.album_help_err_browser), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.album_help_btn_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_got_it))
            }
        }
    )
}

@Composable
private fun HelpStepRow(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
