package com.reiux.lumen.ui.screens

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.data.model.UploadItem
import com.reiux.lumen.data.model.UploadStatus
import com.reiux.lumen.ui.components.DevDiagnosticsDialog
import com.reiux.lumen.ui.components.DynamicLumenIcon
import com.reiux.lumen.ui.components.ExpressiveButton
import com.reiux.lumen.ui.components.ExpressiveIconButton
import com.reiux.lumen.ui.components.ExpressiveLoadingIndicator
import com.reiux.lumen.ui.components.PlayfulParticleBurstOverlay
import com.reiux.lumen.ui.components.ExpressiveTonalButton
import com.reiux.lumen.ui.components.ExpressiveWavyLinearProgress
import com.reiux.lumen.ui.components.PlayfulCompletionCelebration
import java.io.File

@Composable
fun BackupScreen(
    selectedAccount: GoogleAccount?,
    isUploading: Boolean,
    currentUploadingItem: UploadItem?,
    queue: List<UploadItem>,
    completedCount: Int,
    storageSaverQuality: Boolean = false,
    useQuota: Boolean = false,
    uploadSpeed: String? = null,
    conditionNotice: String? = null,
    isScanningFolders: Boolean = false,
    developerModeUnlocked: Boolean = false,
    isMockMode: Boolean = false,
    onToggleMockCondition: () -> Unit = {},
    onAddMockFiles: () -> Unit = {},
    onExitMockMode: () -> Unit = {},
    onStartBackup: () -> Unit,
    onUploadFiles: (List<Uri>) -> Unit,
    onPauseBackup: () -> Unit = {},
    onCancelBackup: () -> Unit = {},
    onClearCompleted: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onUnlockPrismTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onUploadFiles(uris)
        }
    }

    var showDevDiagnostics by remember { mutableStateOf(false) }

    if (showDevDiagnostics) {
        DevDiagnosticsDialog(
            selectedAccount = selectedAccount,
            onDismiss = { showDevDiagnostics = false }
        )
    }

    val configuration = LocalConfiguration.current
    val isTwoPane = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    val lazyListState = rememberLazyListState()

    if (isTwoPane) {
        // Tablet / Landscape 2-Pane Adaptive Layout
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Pane: Controls & Hero Status
            val leftScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxHeight()
                    .verticalScroll(leftScrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                BackupTopHeader(
                    storageSaverQuality = storageSaverQuality,
                    useQuota = useQuota,
                    developerModeUnlocked = developerModeUnlocked,
                    onOpenDiagnostics = { showDevDiagnostics = true },
                    onUnlockPrismTheme = onUnlockPrismTheme
                )

                if (developerModeUnlocked && isMockMode) {
                    MockModeCard(
                        onExitMockMode = onExitMockMode,
                        onToggleMockCondition = onToggleMockCondition,
                        onAddMockFiles = onAddMockFiles
                    )
                }

                AccountStatusCard(
                    selectedAccount = selectedAccount,
                    onNavigateToAccounts = onNavigateToAccounts
                )

                HeroBackupCard(
                    isUploading = isUploading,
                    uploadSpeed = uploadSpeed,
                    currentUploadingItem = currentUploadingItem,
                    queue = queue,
                    completedCount = completedCount,
                    conditionNotice = conditionNotice,
                    isScanningFolders = isScanningFolders,
                    selectedAccount = selectedAccount,
                    onStartBackup = onStartBackup,
                    onCancelBackup = onCancelBackup,
                    onPickPhotos = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Right Pane: Upload Queue & Recent Activity
            Column(
                modifier = Modifier
                    .weight(0.52f)
                    .fillMaxHeight()
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activity (${queue.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (queue.any { it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED || it.status == UploadStatus.FAILED }) {
                        TextButton(
                            onClick = onClearCompleted,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Clear")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (queue.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Queue is empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Choose photos & videos or tap Sync Folders to start backup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(queue.takeLast(50).reversed()) { item ->
                            UploadItemRow(item = item)
                        }
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    } else {
        // Single-Column Layout (Phones in Portrait & Landscape)
        LazyColumn(
            state = lazyListState,
            modifier = modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                BackupTopHeader(
                    storageSaverQuality = storageSaverQuality,
                    useQuota = useQuota,
                    developerModeUnlocked = developerModeUnlocked,
                    onOpenDiagnostics = { showDevDiagnostics = true },
                    onUnlockPrismTheme = onUnlockPrismTheme
                )
            }

            if (developerModeUnlocked && isMockMode) {
                item {
                    MockModeCard(
                        onExitMockMode = onExitMockMode,
                        onToggleMockCondition = onToggleMockCondition,
                        onAddMockFiles = onAddMockFiles
                    )
                }
            }

            item {
                AccountStatusCard(
                    selectedAccount = selectedAccount,
                    onNavigateToAccounts = onNavigateToAccounts
                )
            }

            item {
                HeroBackupCard(
                    isUploading = isUploading,
                    uploadSpeed = uploadSpeed,
                    currentUploadingItem = currentUploadingItem,
                    queue = queue,
                    completedCount = completedCount,
                    conditionNotice = conditionNotice,
                    isScanningFolders = isScanningFolders,
                    selectedAccount = selectedAccount,
                    onStartBackup = onStartBackup,
                    onCancelBackup = onCancelBackup,
                    onPickPhotos = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                )
            }

            if (queue.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Activity (${queue.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (queue.any { it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED || it.status == UploadStatus.FAILED }) {
                            TextButton(
                                onClick = onClearCompleted,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }

                items(queue.takeLast(25).reversed()) { item ->
                    UploadItemRow(item = item)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BackupTopHeader(
    storageSaverQuality: Boolean,
    useQuota: Boolean,
    developerModeUnlocked: Boolean = false,
    onOpenDiagnostics: () -> Unit,
    onUnlockPrismTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var showBurst by remember { mutableStateOf(false) }

    if (showBurst) {
        PlayfulParticleBurstOverlay(
            onFinished = { showBurst = false }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime > 1200L) {
                            tapCount = 1
                        } else {
                            tapCount++
                        }
                        lastTapTime = now
                    },
                    onLongPress = {
                        val now = System.currentTimeMillis()
                        if (tapCount >= 3 && (now - lastTapTime) < 1800L) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUnlockPrismTheme()
                            showBurst = true
                            Toast.makeText(context, "Unlocked 'Master of Light' & Prism Rainbow Theme!", Toast.LENGTH_SHORT).show()
                            tapCount = 0
                        }
                    }
                )
            }
        ) {
            DynamicLumenIcon(size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Lumen",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Unlimited Cloud Backup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val (statusLabel, statusColor, statusContainer) = when {
            useQuota -> Triple(
                "Counts to Quota",
                MaterialTheme.colorScheme.error,
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            )
            storageSaverQuality -> Triple(
                "Storage Saver",
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            )
            else -> Triple(
                "Unlimited",
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = statusContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            if (developerModeUnlocked) {
                Spacer(modifier = Modifier.width(6.dp))

                ExpressiveIconButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BugReport,
                        contentDescription = "Developer Diagnostics",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MockModeCard(
    onExitMockMode: () -> Unit,
    onToggleMockCondition: () -> Unit,
    onAddMockFiles: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mock Mode Active",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                TextButton(
                    onClick = onExitMockMode,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Exit Mock", color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Using sample accounts, uploads, and network states to test the UI.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExpressiveTonalButton(
                    onClick = onToggleMockCondition,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("Cycle Wait State", fontSize = 11.sp, maxLines = 1)
                }
                ExpressiveTonalButton(
                    onClick = onAddMockFiles,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("+ Add 2 Files", fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AccountStatusCard(
    selectedAccount: GoogleAccount?,
    onNavigateToAccounts: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarFile = selectedAccount?.avatarPath?.let { File(it) }
            if (avatarFile != null && avatarFile.exists()) {
                AsyncImage(
                    model = avatarFile,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedAccount?.email?.take(1)?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedAccount?.displayName ?: selectedAccount?.email ?: "No Account Selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selectedAccount?.displayName != null) {
                    Text(
                        text = selectedAccount.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ExpressiveTonalButton(
                onClick = onNavigateToAccounts,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(if (selectedAccount == null) "Connect" else "Switch")
            }
        }
    }
}

@Composable
private fun HeroBackupCard(
    isUploading: Boolean,
    uploadSpeed: String?,
    currentUploadingItem: UploadItem?,
    queue: List<UploadItem>,
    completedCount: Int,
    conditionNotice: String?,
    isScanningFolders: Boolean = false,
    selectedAccount: GoogleAccount?,
    onStartBackup: () -> Unit,
    onCancelBackup: () -> Unit,
    onPickPhotos: () -> Unit
) {
    if (isScanningFolders) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ExpressiveLoadingIndicator(
                    size = 52.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Indexing Media Folders...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Checking Google Photos for duplicates",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
        return
    }

    val totalInQueue = queue.size
    val isDone = totalInQueue == 0 || queue.all { it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED }
    val completedRecently = completedCount > 0 && !isUploading && conditionNotice == null && queue.any { it.status == UploadStatus.COMPLETED || it.status == UploadStatus.SKIPPED }

    if (completedRecently) {
        PlayfulCompletionCelebration(
            completedCount = completedCount,
            enabledButtons = selectedAccount != null,
            onStartBackup = onStartBackup,
            onPickPhotos = onPickPhotos
        )
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                conditionNotice != null -> MaterialTheme.colorScheme.tertiaryContainer
                isUploading -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            conditionNotice != null -> Icons.Rounded.HourglassEmpty
                            isUploading -> Icons.Rounded.CloudUpload
                            else -> Icons.Rounded.CloudDone
                        },
                        contentDescription = null,
                        tint = when {
                            conditionNotice != null -> MaterialTheme.colorScheme.onTertiaryContainer
                            isUploading -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when {
                            conditionNotice != null -> conditionNotice
                            isUploading -> "Uploading..."
                            isDone -> "Backup Ready"
                            else -> "Backup Ready"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            conditionNotice != null -> MaterialTheme.colorScheme.onTertiaryContainer
                            isUploading -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (conditionNotice != null) {
                Text(
                    text = "Backup is paused ($conditionNotice) and will resume automatically when ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            } else if (isUploading && currentUploadingItem != null) {
                Text(
                    text = currentUploadingItem.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                val progress = if (currentUploadingItem.fileSize > 0) {
                    currentUploadingItem.bytesUploaded.toFloat() / currentUploadingItem.fileSize.toFloat()
                } else 0f

                ExpressiveWavyLinearProgress(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatBytes(currentUploadingItem.bytesUploaded)} / ${formatBytes(currentUploadingItem.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )

                    if (!uploadSpeed.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = uploadSpeed,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = if (isDone) "Pick photos and videos to upload, or sync your folders." else "$totalInQueue items in queue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (isUploading || conditionNotice != null) {
                // When actively uploading or waiting on a condition, provide prominent Cancel Backup action
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveTonalButton(
                        onClick = onCancelBackup,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel Backup", fontWeight = FontWeight.SemiBold)
                    }

                    ExpressiveTonalButton(
                        onClick = onPickPhotos,
                        enabled = selectedAccount != null,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Photos & Videos")
                    }
                }
            } else {
                // Idle state: Sync Folders and Pick Photos & Videos
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveButton(
                        onClick = onStartBackup,
                        enabled = selectedAccount != null,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Folders")
                    }
                    ExpressiveTonalButton(
                        onClick = onPickPhotos,
                        enabled = selectedAccount != null,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Photos & Videos")
                    }
                }
            }
        }
    }
}

@Composable
fun UploadItemRow(item: UploadItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.status == UploadStatus.UPLOADING || item.status == UploadStatus.HASHING || item.status == UploadStatus.CHECKING || item.status == UploadStatus.COMMITTING) {
                ExpressiveLoadingIndicator(
                    size = 24.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                val (icon, color) = when (item.status) {
                    UploadStatus.COMPLETED -> Icons.Rounded.CheckCircle to Color(0xFF2E7D32)
                    UploadStatus.SKIPPED -> Icons.Rounded.CloudDone to MaterialTheme.colorScheme.secondary
                    UploadStatus.FAILED -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
                    else -> Icons.Rounded.HourglassEmpty to MaterialTheme.colorScheme.outline
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (item.status) {
                        UploadStatus.COMPLETED -> "Backed up"
                        UploadStatus.SKIPPED -> "Already in Google Photos"
                        UploadStatus.FAILED -> item.error ?: "Failed"
                        UploadStatus.UPLOADING -> "Uploading (${formatBytes(item.bytesUploaded)} / ${formatBytes(item.fileSize)})"
                        UploadStatus.HASHING -> "Hashing..."
                        UploadStatus.CHECKING -> "Checking library..."
                        UploadStatus.COMMITTING -> "Committing..."
                        UploadStatus.QUEUED -> "Queued"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == UploadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}