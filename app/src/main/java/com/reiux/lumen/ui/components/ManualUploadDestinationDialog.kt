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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
    onAddAlbum: (keyOrUrl: String, nickname: String, targetEmail: String?) -> com.reiux.lumen.data.network.GoogleAlbum = { k, n, e -> com.reiux.lumen.data.network.GoogleAlbum(k, n, e) },
    onOpenAlbumManager: () -> Unit = {},
    onConfirm: (targetAlbumMode: AlbumTargetMode, customAlbumName: String?, customAlbumNickname: String?) -> Unit = { _, _, _ -> },
    onConfirmWithAccount: ((targetAccount: com.reiux.lumen.data.model.GoogleAccount?, targetAlbumMode: AlbumTargetMode, customAlbumName: String?, customAlbumNickname: String?) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var chosenAccount by remember(activeAccount, accounts) { mutableStateOf(activeAccount ?: accounts.firstOrNull()) }
    var albumChoice by remember { mutableStateOf<AlbumChoice>(AlbumChoice.None) }

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
                Text(
                    text = "Upload Destination ($itemCount ${if (itemCount == 1) "file" else "files"})",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val accName = chosenAccount?.displayName ?: chosenAccount?.email
                Text(
                    text = if (accName != null && accounts.size <= 1) "Upload to Google Photos • $accName" else "Upload to Google Photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (accounts.size > 1) {
                    Text(
                        text = "Upload to Account",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AccountPickerRow(
                        accounts = accounts,
                        selectedAccount = chosenAccount,
                        onSelectAccount = {
                            chosenAccount = it
                            albumChoice = AlbumChoice.None
                        }
                    )
                }

                Text(
                    text = "Choose where these selected files should be saved in Google Photos:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AlbumPickerRow(
                    choice = albumChoice,
                    onChoiceSelected = { albumChoice = it },
                    savedAlbums = remoteAlbums,
                    activeAccount = chosenAccount,
                    isManualUploadDialog = true,
                    currentMonthName = currentMonth,
                    onAddAlbum = onAddAlbum,
                    onOpenAlbumManager = {
                        onDismiss()
                        onOpenAlbumManager()
                    }
                )
            }
        },
        confirmButton = {
            val context = androidx.compose.ui.platform.LocalContext.current
            val isOnline = remember { com.reiux.lumen.util.DeviceConditions.isInternetAvailable(context) }

            ExpressiveButton(
                onClick = {
                    if (!isOnline) {
                        android.widget.Toast.makeText(context, "No internet connection", android.widget.Toast.LENGTH_SHORT).show()
                        return@ExpressiveButton
                    }
                    val targetAcc = chosenAccount ?: activeAccount
                    if (targetAcc == null) {
                        android.widget.Toast.makeText(context, "No Google account connected. Please sign in first.", android.widget.Toast.LENGTH_LONG).show()
                        return@ExpressiveButton
                    }
                    val activeEmail = targetAcc.email
                    val (mode, customName, customNickname) = when (val c = albumChoice) {
                        is AlbumChoice.None -> Triple(AlbumTargetMode.NONE, null, null)
                        is AlbumChoice.ByMonth -> Triple(AlbumTargetMode.AUTO_FOLDER_NAME, null, null)
                        is AlbumChoice.CustomAlbum -> {
                            if (c.album.accountEmail.isNullOrBlank() || !c.album.accountEmail.equals(activeEmail, ignoreCase = true)) {
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
                enabled = isOnline
            ) {
                Text(if (isOnline) "Start Upload" else "No Internet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
