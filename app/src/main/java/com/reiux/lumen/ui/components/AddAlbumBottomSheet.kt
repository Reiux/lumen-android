package com.reiux.lumen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
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
    var selectedSegment by rememberSaveable { mutableIntStateOf(0) } // 0 = Paste Link/Key, 1 = Create New
    var keyOrUrlInput by rememberSaveable { mutableStateOf("") }
    var nicknameInput by rememberSaveable { mutableStateOf("") }
    var newAlbumNameInput by rememberSaveable { mutableStateOf("") }

    var selectedSheetAccountEmail by rememberSaveable {
        mutableStateOf(activeAccount?.email ?: accounts.firstOrNull()?.email)
    }
    val selectedSheetAccount = remember(selectedSheetAccountEmail, activeAccount, accounts) {
        accounts.firstOrNull { !selectedSheetAccountEmail.isNullOrBlank() && it.email.equals(selectedSheetAccountEmail, ignoreCase = true) }
            ?: activeAccount
            ?: accounts.firstOrNull()
    }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }

    val extractedKey = remember(keyOrUrlInput) { extractKeyFromPastedUrl(keyOrUrlInput).trim() }
    val isValidKeyOrUrl = isValidAlbumKeyOrUrl(keyOrUrlInput)

    val isDuplicateName = remember(selectedSegment, newAlbumNameInput, albums, selectedSheetAccount) {
        val effEmail = selectedSheetAccount?.email
        val name = newAlbumNameInput.trim()
        selectedSegment == 1 && name.isNotBlank() && albums.any {
            (it.accountEmail.isNullOrBlank() || it.accountEmail.equals(effEmail, ignoreCase = true)) &&
                it.title.equals(name, ignoreCase = true)
        }
    }

    val isDuplicateKey = remember(selectedSegment, extractedKey, albums, selectedSheetAccount) {
        val effEmail = selectedSheetAccount?.email
        selectedSegment == 0 && extractedKey.isNotBlank() && albums.any {
            (it.accountEmail.isNullOrBlank() || it.accountEmail.equals(effEmail, ignoreCase = true)) &&
                it.mediaKey == extractedKey
        }
    }

    val isDuplicateNickname = remember(selectedSegment, nicknameInput, albums, selectedSheetAccount) {
        val effEmail = selectedSheetAccount?.email
        val nick = nicknameInput.trim()
        selectedSegment == 0 && nick.isNotBlank() && albums.any {
            (it.accountEmail.isNullOrBlank() || it.accountEmail.equals(effEmail, ignoreCase = true)) &&
                it.title.equals(nick, ignoreCase = true)
        }
    }

    val canSave = if (selectedSegment == 1) {
        newAlbumNameInput.isNotBlank() && !isDuplicateName
    } else {
        isValidKeyOrUrl && nicknameInput.isNotBlank() && !isDuplicateKey && !isDuplicateNickname
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.album_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // M3E Connected Toggle Button Group
            val segmentHaptic = LocalHapticFeedback.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                TonalToggleButton(
                    checked = selectedSegment == 0,
                    onCheckedChange = {
                        try { segmentHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                        selectedSegment = 0
                    },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.album_segment_paste),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TonalToggleButton(
                    checked = selectedSegment == 1,
                    onCheckedChange = {
                        try { segmentHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                        selectedSegment = 1
                    },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.album_segment_create),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // AccountPickerRow under title (hidden for one account)
            if (accounts.size > 1) {
                AccountPickerRow(
                    accounts = accounts,
                    selectedAccount = selectedSheetAccount,
                    onSelectAccount = { selectedSheetAccountEmail = it.email }
                )
            }

            if (selectedSegment == 1) {
                OutlinedTextField(
                    value = newAlbumNameInput,
                    onValueChange = { newAlbumNameInput = it },
                    label = { Text(stringResource(R.string.album_label_name)) },
                    placeholder = { Text(stringResource(R.string.album_hint_name)) },
                    isError = isDuplicateName,
                    supportingText = {
                        if (isDuplicateName) {
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
                    isError = (keyOrUrlInput.isNotBlank() && !isValidKeyOrUrl) || isDuplicateKey,
                    supportingText = {
                        if (isDuplicateKey) {
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
                    isError = isDuplicateNickname,
                    supportingText = {
                        if (isDuplicateNickname) {
                            Text(stringResource(R.string.album_err_exists))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    ExpressiveTextButton(
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
                ExpressiveTextButton(onClick = onDismiss) {
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

    val keyRegex = Regex("^AF1Qip[A-Za-z0-9_-]+$")
    if (keyRegex.matches(trimmed)) return true

    val looksLikeUrl = trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.contains("/")

    if (looksLikeUrl) {
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = try { java.net.URI(withScheme) } catch (_: Exception) { return false }
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path?.trim('/') ?: ""
        return when {
            host == "photos.google.com" -> {
                val extracted = extractKeyFromPastedUrl(trimmed)
                keyRegex.matches(extracted) ||
                    ((path.startsWith("share/") || path.startsWith("album/")) && extracted.length >= 16 && !extracted.contains('/'))
            }
            host == "photos.app.goo.gl" -> {
                path.isNotBlank() && !path.contains('/') && path.all { it.isLetterOrDigit() || it == '_' || it == '-' }
            }
            host == "goo.gl" -> {
                path.startsWith("photos/") && path.removePrefix("photos/").let { slug ->
                    slug.isNotBlank() && !slug.contains('/') && slug.all { it.isLetterOrDigit() || it == '_' || it == '-' }
                }
            }
            else -> false
        }
    }

    if (trimmed.length >= 20 && !trimmed.contains(" ") && trimmed.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
        return true
    }
    return false
}

@Composable
fun FindAlbumLinkHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val steps = listOf(
        stringResource(R.string.album_help_step_1),
        stringResource(R.string.album_help_step_2),
        stringResource(R.string.album_help_step_3),
        stringResource(R.string.album_help_step_4)
    )

    PredictiveAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(min = 320.dp, max = if (isWideLayout) 540.dp else 440.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.album_help_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        steps.forEachIndexed { index, stepText ->
                            HelpStepRow(number = index + 1, text = stepText)
                            if (index < steps.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "photos.google.com/album/AF1Qip...",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.album_help_nickname_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            ExpressiveButton(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
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
                Text(stringResource(R.string.album_help_btn_open), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            ExpressiveTonalButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(stringResource(R.string.btn_got_it), fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun HelpStepRow(number: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
