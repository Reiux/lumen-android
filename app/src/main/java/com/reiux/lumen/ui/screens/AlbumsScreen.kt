package com.reiux.lumen.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import com.reiux.lumen.ui.components.DialogBlurEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.reiux.lumen.R
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.data.network.GoogleAlbum
import com.reiux.lumen.ui.components.AccountPickerRow
import com.reiux.lumen.ui.components.ExpressiveButton
import com.reiux.lumen.ui.components.ExpressiveExtendedFloatingActionButton
import com.reiux.lumen.ui.components.ExpressiveFilterChip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlbumsScreen(
    albums: List<GoogleAlbum>,
    accounts: List<GoogleAccount> = emptyList(),
    activeAccount: GoogleAccount? = null,
    onBack: () -> Unit,
    onRemoveAlbum: (GoogleAlbum) -> Unit,
    onAddAlbum: (keyOrUrl: String, nickname: String?, targetAccountEmail: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var albumToDelete by remember { mutableStateOf<GoogleAlbum?>(null) }
    var albumToRename by remember { mutableStateOf<GoogleAlbum?>(null) }
    var selectedFilterEmail by rememberSaveable { mutableStateOf<String?>("ALL") }

    LaunchedEffect(accounts, selectedFilterEmail) {
        if (selectedFilterEmail != null &&
            selectedFilterEmail != "ALL" &&
            accounts.none { it.email.equals(selectedFilterEmail, ignoreCase = true) }
        ) {
            selectedFilterEmail = "ALL"
        }
    }

    val filteredAlbums = remember(albums, selectedFilterEmail, accounts) {
        if (selectedFilterEmail == "ALL" || selectedFilterEmail == null || accounts.size <= 1) {
            albums
        } else {
            albums.filter { it.accountEmail.isNullOrBlank() || it.accountEmail.equals(selectedFilterEmail, ignoreCase = true) }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.albums_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.albums_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExpressiveExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.albums_btn_add), fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Account Filter Chips (if accounts.size > 1)
                if (accounts.size > 1) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExpressiveFilterChip(
                            selected = selectedFilterEmail == "ALL",
                            onClick = { selectedFilterEmail = "ALL" },
                            label = { Text(stringResource(R.string.albums_filter_all), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        )
                        accounts.forEach { acc ->
                            ExpressiveFilterChip(
                                selected = selectedFilterEmail == acc.email,
                                onClick = { selectedFilterEmail = acc.email },
                                label = { Text(acc.displayName ?: acc.email, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }
                }

                if (filteredAlbums.isEmpty()) {
                    // M3E Centered Empty State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.widthIn(max = 420.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.PhotoLibrary,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.albums_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = stringResource(R.string.albums_empty_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        items(filteredAlbums, key = { "${it.accountEmail}:${it.mediaKey}:${it.title}" }) { album ->
                            var menuExpanded by remember { mutableStateOf(false) }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.PhotoLibrary,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = album.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = album.getSubtitle(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Account Avatar or "No account" badge
                                    if (album.accountEmail.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                text = stringResource(R.string.albums_no_account),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else if (accounts.size > 1) {
                                        val acc = accounts.find { it.email.equals(album.accountEmail, ignoreCase = true) }
                                        com.reiux.lumen.ui.components.AccountAvatar(account = acc, emailFallback = album.accountEmail, size = 28.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    // Single Overflow Menu Button
                                    Box {
                                        IconButton(onClick = { menuExpanded = true }) {
                                            Icon(
                                                imageVector = Icons.Rounded.MoreVert,
                                                contentDescription = stringResource(R.string.albums_options_desc),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false },
                                            shape = RoundedCornerShape(20.dp),
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            tonalElevation = 6.dp
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.btn_rename)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Edit,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                onClick = {
                                                    menuExpanded = false
                                                    albumToRename = album
                                                },
                                                modifier = Modifier.height(48.dp)
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                onClick = {
                                                    menuExpanded = false
                                                    albumToDelete = album
                                                },
                                                modifier = Modifier.height(48.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(88.dp)) }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Album Deletion
    if (albumToDelete != null) {
        val target = albumToDelete!!

        com.reiux.lumen.ui.components.ExpressiveAlertDialog(
            onDismissRequest = { albumToDelete = null },
            icon = Icons.Rounded.DeleteOutline,
            title = stringResource(R.string.dialog_remove_album_title),
            tone = com.reiux.lumen.ui.components.ExpressiveDialogTone.ERROR,
            confirmText = stringResource(R.string.btn_remove),
            onConfirm = {
                onRemoveAlbum(target)
                albumToDelete = null
            },
            dismissText = stringResource(R.string.btn_cancel)
        ) {
            Text(
                text = stringResource(R.string.dialog_remove_album_msg, target.title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Rename / Edit Album Dialog
    if (albumToRename != null) {
        val target = albumToRename!!
        var newNickname by rememberSaveable(target.accountEmail, target.mediaKey, target.title) { mutableStateOf(target.title) }
        var targetAccountEmail by rememberSaveable(target.accountEmail, target.mediaKey) {
            mutableStateOf(target.accountEmail ?: activeAccount?.email ?: accounts.firstOrNull()?.email)
        }
        val targetAccount = remember(targetAccountEmail, accounts, activeAccount) {
            accounts.find { !targetAccountEmail.isNullOrBlank() && it.email.equals(targetAccountEmail, ignoreCase = true) }
                ?: activeAccount
                ?: accounts.firstOrNull()
        }

        com.reiux.lumen.ui.components.ExpressiveAlertDialog(
            onDismissRequest = { albumToRename = null },
            icon = Icons.Rounded.Edit,
            title = stringResource(R.string.dialog_edit_album_title),
            tone = com.reiux.lumen.ui.components.ExpressiveDialogTone.PRIMARY,
            confirmText = stringResource(R.string.btn_save),
            confirmEnabled = newNickname.isNotBlank(),
            onConfirm = {
                if (newNickname.isNotBlank()) {
                    val newEmail = targetAccount?.email
                    if (!target.accountEmail.isNullOrBlank() && !target.accountEmail.equals(newEmail, ignoreCase = true)) {
                        onRemoveAlbum(target)
                    }
                    onAddAlbum(target.mediaKey, newNickname.trim(), newEmail)
                    albumToRename = null
                }
            },
            dismissText = stringResource(R.string.btn_cancel)
        ) {
            Text(
                text = stringResource(R.string.dialog_edit_album_nickname_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = newNickname,
                onValueChange = { newNickname = it },
                label = { Text(stringResource(R.string.dialog_edit_album_nickname_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            if (accounts.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.dialog_edit_album_assign_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AccountPickerRow(
                    accounts = accounts,
                    selectedAccount = targetAccount,
                    onSelectAccount = { targetAccountEmail = it.email }
                )
            }
        }
    }

    // Add Album Modal Bottom Sheet
    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selectedSegment by rememberSaveable { mutableIntStateOf(0) } // 0 = Paste Link/Key, 1 = Create New
        var keyOrUrlInput by rememberSaveable { mutableStateOf("") }
        var nicknameInput by rememberSaveable { mutableStateOf("") }
        var newAlbumNameInput by rememberSaveable { mutableStateOf("") }

        val defaultSheetEmail = if (selectedFilterEmail != null && selectedFilterEmail != "ALL") {
            accounts.find { it.email.equals(selectedFilterEmail, ignoreCase = true) }?.email
                ?: activeAccount?.email
                ?: accounts.firstOrNull()?.email
        } else {
            activeAccount?.email ?: accounts.firstOrNull()?.email
        }
        var selectedSheetAccountEmail by rememberSaveable { mutableStateOf(defaultSheetEmail) }
        val selectedSheetAccount = remember(selectedSheetAccountEmail, activeAccount, accounts) {
            accounts.find { !selectedSheetAccountEmail.isNullOrBlank() && it.email.equals(selectedSheetAccountEmail, ignoreCase = true) }
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
            onDismissRequest = { showAddSheet = false },
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
                    text = stringResource(R.string.albums_btn_add),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Step 7: M3E Connected Button Group
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedSegment == 0,
                        onClick = { selectedSegment = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.albums_tab_paste_link), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SegmentedButton(
                        selected = selectedSegment == 1,
                        onClick = { selectedSegment = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(R.string.albums_tab_create_new), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Step 7: AccountPickerRow under title (hidden for one account)
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
                        label = { Text(stringResource(R.string.albums_field_album_name)) },
                        placeholder = { Text(stringResource(R.string.album_hint_name)) },
                        isError = isDuplicateName,
                        supportingText = {
                            if (isDuplicateName) {
                                Text(stringResource(R.string.albums_error_name_exists))
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
                        label = { Text(stringResource(R.string.albums_field_link_or_key)) },
                        placeholder = { Text(stringResource(R.string.album_hint_link_key)) },
                        isError = (keyOrUrlInput.isNotBlank() && !isValidKeyOrUrl) || isDuplicateKey,
                        supportingText = {
                            if (isDuplicateKey) {
                                Text(stringResource(R.string.albums_error_key_exists))
                            } else if (keyOrUrlInput.isNotBlank() && !isValidKeyOrUrl) {
                                Text(stringResource(R.string.albums_error_invalid_key))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = nicknameInput,
                        onValueChange = { nicknameInput = it },
                        label = { Text(stringResource(R.string.dialog_edit_album_nickname_label)) },
                        placeholder = { Text(stringResource(R.string.album_hint_nickname)) },
                        isError = isDuplicateNickname,
                        supportingText = {
                            if (isDuplicateNickname) {
                                Text(stringResource(R.string.albums_error_name_exists))
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
                        TextButton(
                            onClick = { showHelpDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.albums_link_help))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showAddSheet = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    ExpressiveButton(
                        onClick = {
                            if (canSave) {
                                if (selectedSegment == 1) {
                                    onAddAlbum(newAlbumNameInput.trim(), newAlbumNameInput.trim(), selectedSheetAccount?.email)
                                } else {
                                    onAddAlbum(extractedKey, nicknameInput.trim(), selectedSheetAccount?.email)
                                }
                                showAddSheet = false
                            }
                        },
                        enabled = canSave
                    ) {
                        Text(stringResource(R.string.albums_btn_save_album))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Bouncy M3E Instruction Popup Dialog
        if (showHelpDialog) {
            com.reiux.lumen.ui.components.FindAlbumLinkHelpDialog(onDismiss = { showHelpDialog = false })
        }
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
    return com.reiux.lumen.ui.components.isValidAlbumKeyOrUrl(input)
}
