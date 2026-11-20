package com.reiux.lumen.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.reiux.lumen.R
import com.reiux.lumen.ui.components.AccountPickerRow
import com.reiux.lumen.ui.components.AlbumPickerDropdown
import com.reiux.lumen.data.model.AlbumTargetMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ManualUploadDestinationDialog(
    itemCount: Int,
    accounts: List<com.reiux.lumen.data.model.GoogleAccount> = emptyList(),
    activeAccount: com.reiux.lumen.data.model.GoogleAccount? = null,
    remoteAlbums: List<com.reiux.lumen.data.network.GoogleAlbum>,
    isOnline: Boolean? = null,
    fileNames: List<String> = emptyList(),
    pendingReplacementCount: Int = 0,
    pendingReplacementNames: List<String> = emptyList(),
    onSwitchToReplacement: () -> Unit = {},
    onDiscardReplacement: () -> Unit = {},
    onAddAlbum: (keyOrUrl: String, nickname: String, targetEmail: String?) -> com.reiux.lumen.data.network.GoogleAlbum = { k, n, e -> com.reiux.lumen.data.network.GoogleAlbum(k, n, e) },
    onOpenAlbumManager: () -> Unit = {},
    onConfirm: (targetAlbumMode: AlbumTargetMode, customAlbumName: String?, customAlbumNickname: String?) -> Unit = { _, _, _ -> },
    onConfirmWithAccount: ((targetAccount: com.reiux.lumen.data.model.GoogleAccount?, targetAlbumMode: AlbumTargetMode, customAlbumName: String?, customAlbumNickname: String?) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var chosenAccountEmail by rememberSaveable { mutableStateOf(activeAccount?.email ?: accounts.firstOrNull()?.email) }
    val chosenAccount = remember(chosenAccountEmail, activeAccount, accounts) {
        accounts.firstOrNull { !chosenAccountEmail.isNullOrBlank() && it.email.equals(chosenAccountEmail, ignoreCase = true) }
            ?: activeAccount
            ?: accounts.firstOrNull()
    }
    var albumChoice by rememberSaveable(stateSaver = AlbumChoiceSaver) { mutableStateOf<AlbumChoice>(AlbumChoice.None) }

    val currentMonth = remember {
        SimpleDateFormat("MMM yyyy", Locale.US).format(Date())
    }

    PredictiveAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(min = 320.dp, max = 480.dp)
            .padding(horizontal = 16.dp),
        title = {
            Column {
                val fileWord = if (itemCount == 1) stringResource(R.string.manual_upload_word_file) else stringResource(R.string.manual_upload_word_files)
                Text(
                    text = stringResource(R.string.manual_upload_files_count, itemCount, fileWord),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val accName = chosenAccount?.displayName ?: chosenAccount?.email
                Text(
                    text = if (accName != null && accounts.size <= 1) {
                        stringResource(R.string.manual_upload_to_google_photos_account, accName)
                    } else {
                        stringResource(R.string.manual_upload_to_google_photos)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (pendingReplacementCount > 0) {
                    val replacementWord = if (pendingReplacementCount == 1) {
                        stringResource(R.string.manual_upload_word_file)
                    } else {
                        stringResource(R.string.manual_upload_word_files)
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.manual_upload_new_share_banner_title,
                                    pendingReplacementCount,
                                    replacementWord
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = stringResource(R.string.manual_upload_new_share_banner_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            if (pendingReplacementNames.isNotEmpty()) {
                                val preview = pendingReplacementNames.take(3)
                                val extraCount = (pendingReplacementCount - preview.size).coerceAtLeast(0)
                                val namesSummary = if (extraCount > 0) {
                                    "${preview.joinToString(", ")} (${stringResource(R.string.manual_upload_more_files, extraCount)})"
                                } else {
                                    preview.joinToString(", ")
                                }
                                Text(
                                    text = namesSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onDiscardReplacement) {
                                    Text(
                                        text = stringResource(R.string.manual_upload_btn_ignore),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = onSwitchToReplacement) {
                                    Text(
                                        text = stringResource(R.string.manual_upload_btn_switch),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                if (fileNames.isNotEmpty()) {
                    val previewNames = fileNames.take(3)
                    val remainingCount = (itemCount - previewNames.size).coerceAtLeast(0)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.manual_upload_selected_files_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            previewNames.forEach { name ->
                                Text(
                                    text = "• $name",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (remainingCount > 0) {
                                Text(
                                    text = stringResource(R.string.manual_upload_more_files, remainingCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (accounts.size > 1) {
                    Text(
                        text = stringResource(R.string.manual_upload_to_account_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AccountPickerRow(
                        accounts = accounts,
                        selectedAccount = chosenAccount,
                        onSelectAccount = {
                            chosenAccountEmail = it.email
                            albumChoice = AlbumChoice.None
                        }
                    )
                }

                Text(
                    text = stringResource(R.string.manual_upload_choose_destination),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AlbumPickerRow(
                    choice = albumChoice,
                    onChoiceSelected = { albumChoice = it },
                    savedAlbums = remoteAlbums,
                    accounts = accounts,
                    activeAccount = chosenAccount,
                    isManualUploadDialog = true,
                    currentMonthName = currentMonth,
                    onAddAlbum = onAddAlbum,
                    onOpenAlbumManager = onOpenAlbumManager
                )
            }
        },
        confirmButton = {
            val context = androidx.compose.ui.platform.LocalContext.current
            val effectiveOnline = isOnline ?: com.reiux.lumen.util.DeviceConditions.isInternetAvailable(context)

            ExpressiveButton(
                onClick = {
                    if (!effectiveOnline) {
                        android.widget.Toast.makeText(context, context.getString(R.string.manual_upload_no_internet_toast), android.widget.Toast.LENGTH_SHORT).show()
                        return@ExpressiveButton
                    }
                    val targetAcc = chosenAccount ?: activeAccount
                    if (targetAcc == null) {
                        android.widget.Toast.makeText(context, context.getString(R.string.manual_upload_no_account_toast), android.widget.Toast.LENGTH_LONG).show()
                        return@ExpressiveButton
                    }
                    val activeEmail = targetAcc.email
                    val (mode, customName, customNickname) = when (val c = albumChoice) {
                        is AlbumChoice.None -> Triple(AlbumTargetMode.NONE, null, null)
                        is AlbumChoice.ByMonth -> Triple(AlbumTargetMode.AUTO_FOLDER_NAME, null, null)
                        is AlbumChoice.CustomAlbum -> {
                            if (!c.album.accountEmail.isNullOrBlank() && !c.album.accountEmail.equals(activeEmail, ignoreCase = true)) {
                                albumChoice = AlbumChoice.None
                                return@ExpressiveButton
                            }
                            Triple(AlbumTargetMode.CUSTOM_NAME, c.album.mediaKey, c.album.title)
                        }
                        else -> Triple(AlbumTargetMode.NONE, null, null)
                    }
                    if (onConfirmWithAccount != null) {
                        onConfirmWithAccount(targetAcc, mode, customName, customNickname)
                    } else {
                        onConfirm(mode, customName, customNickname)
                    }
                },
                enabled = effectiveOnline
            ) {
                Text(if (effectiveOnline) stringResource(R.string.manual_upload_btn_start) else stringResource(R.string.manual_upload_no_internet))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
