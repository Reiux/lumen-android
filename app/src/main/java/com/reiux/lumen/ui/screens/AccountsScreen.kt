package com.reiux.lumen.ui.screens

import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reiux.lumen.R
import coil.compose.AsyncImage
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.ui.components.AddAccountDialog
import com.reiux.lumen.ui.components.EditDisplayNameDialog
import com.reiux.lumen.ui.components.ExpressiveAlertDialog
import com.reiux.lumen.ui.components.ExpressiveButton
import com.reiux.lumen.ui.components.ExpressiveDialogCallout
import com.reiux.lumen.ui.components.ExpressiveDialogTone
import com.reiux.lumen.ui.components.ExpressiveIconButton
import com.reiux.lumen.ui.components.ExpressiveTonalButton
import com.reiux.lumen.ui.components.groupPositionOf
import com.reiux.lumen.ui.components.groupedCardShape
import java.io.File

@Composable
fun AccountsScreen(
    accounts: List<GoogleAccount>,
    selectedAccount: GoogleAccount?,
    folders: List<com.reiux.lumen.data.model.BackupFolder> = emptyList(),
    albums: List<com.reiux.lumen.data.network.GoogleAlbum> = emptyList(),
    onSelectAccount: (email: String) -> Unit,
    onAddAccountToken: (token: String) -> Unit,
    onAddAccountCredential: (credential: String) -> Unit,
    onUpdateDisplayName: (email: String, displayName: String) -> Unit,
    onUpdateAvatar: (email: String, uri: Uri) -> Unit,
    onRemoveAccount: (email: String) -> Unit,
    onClearError: () -> Unit = {},
    isAddingAccount: Boolean,
    addAccountError: String?,
    isUploading: Boolean = false,
    hasAcknowledgedAccountRisk: Boolean = false,
    onAcknowledgeRisk: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var accountToRenameEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var accountToRemoveEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var showUploadActiveBlockedDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var targetAvatarAccountEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var showTimeCapsule by rememberSaveable { mutableStateOf(false) }

    val accountToRename = remember(accountToRenameEmail, accounts) {
        accountToRenameEmail?.let { email -> accounts.find { it.email.equals(email, ignoreCase = true) } }
    }
    val accountToRemove = remember(accountToRemoveEmail, accounts) {
        accountToRemoveEmail?.let { email -> accounts.find { it.email.equals(email, ignoreCase = true) } }
    }

    if (showUploadActiveBlockedDialog != null) {
        val activeLabel = selectedAccount?.displayName ?: selectedAccount?.email ?: stringResource(R.string.accounts_active_fallback)
        val msg = if (showUploadActiveBlockedDialog == "switch") {
            stringResource(R.string.accounts_upload_active_switch_msg, activeLabel)
        } else {
            stringResource(R.string.accounts_upload_active_remove_msg, activeLabel)
        }
        ExpressiveAlertDialog(
            onDismissRequest = { showUploadActiveBlockedDialog = null },
            icon = Icons.Rounded.CloudSync,
            title = stringResource(R.string.backup_upload_in_progress_title),
            tone = ExpressiveDialogTone.TERTIARY,
            confirmText = stringResource(R.string.btn_got_it),
            onConfirm = { showUploadActiveBlockedDialog = null }
        ) {
            Text(
                text = msg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    val handleSelectAccount: (String) -> Unit = { targetEmail ->
        if (isUploading && targetEmail != selectedAccount?.email) {
            showUploadActiveBlockedDialog = "switch"
            Toast.makeText(context, context.getString(R.string.toast_cannot_switch_uploading), Toast.LENGTH_SHORT).show()
        } else {
            onSelectAccount(targetEmail)
        }
    }

    val handleRemoveAccount: (GoogleAccount) -> Unit = { targetAccount ->
        if (isUploading) {
            showUploadActiveBlockedDialog = "remove"
            Toast.makeText(context, context.getString(R.string.toast_cannot_remove_uploading), Toast.LENGTH_SHORT).show()
        } else {
            accountToRemoveEmail = targetAccount.email
        }
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && targetAvatarAccountEmail != null) {
            onUpdateAvatar(targetAvatarAccountEmail!!, uri)
            targetAvatarAccountEmail = null
        }
    }

    if (showTimeCapsule) {
        LumenTimeCapsuleDialog(onDismiss = { showTimeCapsule = false })
    }

    var isAddSubmitInFlight by rememberSaveable { mutableStateOf(false) }

    if (showAddDialog || isAddingAccount || addAccountError != null) {
        AddAccountDialog(
            onDismiss = {
                if (!isAddingAccount) {
                    showAddDialog = false
                    onClearError()
                }
            },
            onSubmitToken = {
                isAddSubmitInFlight = true
                onAddAccountToken(it)
            },
            onSubmitCredential = {
                isAddSubmitInFlight = true
                onAddAccountCredential(it)
            },
            onClearError = {
                showAddDialog = true
                onClearError()
            },
            isLoading = isAddingAccount,
            errorMessage = addAccountError,
            hasAcknowledgedAccountRisk = hasAcknowledgedAccountRisk,
            onAcknowledgeRisk = onAcknowledgeRisk
        )
    }

    // The dialog stays mounted for the whole submit so the pasted token / credential
    // survives a failed attempt (Tap "Try Again", fix the input, resubmit). It only
    // closes itself once the ViewModel reports a finished, error-free submit.
    LaunchedEffect(isAddingAccount, addAccountError, isAddSubmitInFlight) {
        if (!isAddSubmitInFlight || isAddingAccount) return@LaunchedEffect
        isAddSubmitInFlight = false
        if (addAccountError == null) {
            showAddDialog = false
            onClearError()
        }
    }

    if (accountToRename != null) {
        EditDisplayNameDialog(
            initialName = accountToRename.displayName ?: "",
            onDismiss = { accountToRenameEmail = null },
            onSave = {
                onUpdateDisplayName(accountToRename.email, it)
                accountToRenameEmail = null
            }
        )
    }

    if (accountToRemove != null) {
        val targetAcc = accountToRemove
        val attachedFolders = folders.count { it.accountEmail == null || it.accountEmail.equals(targetAcc.email, ignoreCase = true) }
        val attachedAlbums = albums.count { it.accountEmail == null || it.accountEmail.equals(targetAcc.email, ignoreCase = true) }

        ExpressiveAlertDialog(
            onDismissRequest = { accountToRemoveEmail = null },
            icon = Icons.Rounded.PersonRemove,
            title = stringResource(R.string.accounts_btn_remove),
            tone = ExpressiveDialogTone.ERROR,
            confirmText = stringResource(R.string.btn_delete),
            onConfirm = {
                onRemoveAccount(targetAcc.email)
                accountToRemoveEmail = null
            },
            dismissText = stringResource(R.string.btn_cancel)
        ) {
            Text(
                text = stringResource(R.string.dialog_remove_account_desc, targetAcc.displayName ?: targetAcc.email),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (attachedFolders > 0 || attachedAlbums > 0) {
                ExpressiveDialogCallout(tone = ExpressiveDialogTone.ERROR) {
                    Text(
                        text = stringResource(R.string.dialog_remove_account_affects, attachedFolders, attachedAlbums),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            ExpressiveDialogCallout(tone = ExpressiveDialogTone.SECONDARY) {
                Text(
                    text = stringResource(R.string.remove_account_device_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val wideLazyListState = rememberLazyListState()
    val portraitLazyListState = rememberLazyListState()

    val otherAccounts = accounts.filterNot { it.email == selectedAccount?.email }

    if (isWideLayout) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Pane: Active Account & Add Action
            val leftScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxHeight()
                    .verticalScroll(leftScrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.accounts_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.accounts_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (selectedAccount != null) {
                    ActiveAccountHeroCard(
                        selectedAccount = selectedAccount,
                        onPickAvatar = {
                            targetAvatarAccountEmail = selectedAccount.email
                            avatarPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRename = { accountToRenameEmail = selectedAccount.email },
                        onRemove = { handleRemoveAccount(selectedAccount) },
                        onTriggerCapsule = { showTimeCapsule = true }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                ExpressiveButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.accounts_btn_add))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Right Pane: Other Connected Accounts List
            Column(
                modifier = Modifier
                    .weight(0.52f)
                    .fillMaxHeight()
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                if (otherAccounts.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.accounts_other_title, otherAccounts.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        state = wideLazyListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = otherAccounts,
                            key = { _, account -> account.email }
                        ) { index, account ->
                            OtherAccountRowItem(
                                account = account,
                                position = groupPositionOf(index, otherAccounts.size),
                                onSelectAccount = handleSelectAccount,
                                onRemove = { handleRemoveAccount(account) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.accounts_no_other_connected),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Single Column Layout
        LazyColumn(
            state = portraitLazyListState,
            modifier = modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.accounts_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.accounts_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selectedAccount != null) {
                item {
                    ActiveAccountHeroCard(
                        selectedAccount = selectedAccount,
                        onPickAvatar = {
                            targetAvatarAccountEmail = selectedAccount.email
                            avatarPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRename = { accountToRenameEmail = selectedAccount.email },
                        onRemove = { handleRemoveAccount(selectedAccount) },
                        onTriggerCapsule = { showTimeCapsule = true }
                    )
                }
            }

            if (otherAccounts.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.accounts_other_title, otherAccounts.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                itemsIndexed(
                    items = otherAccounts,
                    key = { _, account -> account.email }
                ) { index, account ->
                    OtherAccountRowItem(
                        account = account,
                        position = groupPositionOf(index, otherAccounts.size),
                        onSelectAccount = handleSelectAccount,
                        onRemove = { handleRemoveAccount(account) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                ExpressiveButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.accounts_btn_add))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActiveAccountHeroCard(
    selectedAccount: GoogleAccount,
    onPickAvatar: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onTriggerCapsule: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(72.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.TopStart)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = onPickAvatar,
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTriggerCapsule()
                                }
                            ),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.20f)),
                        tonalElevation = 2.dp
                    ) {
                        val avatarFile = selectedAccount.avatarPath?.let { File(it) }
                        if (avatarFile != null && avatarFile.exists()) {
                            AsyncImage(
                                model = avatarFile,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = selectedAccount.email.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(26.dp)
                            .align(Alignment.BottomEnd)
                            .clickable { onPickAvatar() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f)),
                        tonalElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Change profile picture",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedAccount.displayName ?: selectedAccount.email,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.accounts_active_badge),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (selectedAccount.displayName != null) {
                        Text(
                            text = selectedAccount.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                ExpressiveIconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.btn_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveTonalButton(
                    onClick = onPickAvatar,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.accounts_btn_change_avatar), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                ExpressiveTonalButton(
                    onClick = onRename,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.accounts_btn_rename_short), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun OtherAccountRowItem(
    account: GoogleAccount,
    position: com.reiux.lumen.ui.components.GroupPosition,
    onSelectAccount: (email: String) -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = groupedCardShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarFile = account.avatarPath?.let { File(it) }
            if (avatarFile != null && avatarFile.exists()) {
                AsyncImage(
                    model = avatarFile,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = account.email.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.displayName ?: account.email,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (account.displayName != null) {
                    Text(
                        text = account.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ExpressiveTonalButton(
                onClick = { onSelectAccount(account.email) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.accounts_btn_use))
            }

            Spacer(modifier = Modifier.width(4.dp))

            ExpressiveIconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun LumenTimeCapsuleDialog(
    onDismiss: () -> Unit
) {
    com.reiux.lumen.ui.components.PredictiveAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Lumen Memory Capsule",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Preserved Lifetime Stats",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "• Pixels Preserved: 4.2 Trillion",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Space Saved: 133.7 GB",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Photos Protected: ∞",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Account Status: Verified & Encrypted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            ExpressiveButton(onClick = onDismiss) {
                Text("Close Capsule")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}
