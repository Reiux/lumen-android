package com.reiux.lumen.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.reiux.lumen.data.model.BackupSettings
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.util.DeviceConditions
import com.reiux.lumen.util.LogCategory
import com.reiux.lumen.util.LogEntry
import com.reiux.lumen.util.LogKind
import com.reiux.lumen.util.LogLevel
import com.reiux.lumen.util.LumenLogger
import com.reiux.lumen.util.UiSnapshotDumper
import com.reiux.lumen.util.UploadOutcome
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun LogViewerDialog(
    selectedAccount: GoogleAccount? = null,
    settings: BackupSettings? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allLogs by LumenLogger.logs.collectAsState()
    val droppedEvents by LumenLogger.droppedEvents.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Activity, 1 = Problems, 2 = Raw

    // Check for crash file
    val crashLogFile = remember {
        val dir = File(context.cacheDir, "logs")
        val file = File(dir, "lumen_crash.log")
        if (file.exists() && file.length() > 0) file else null
    }
    var showCrashReportDialog by remember { mutableStateOf(false) }

    // Share / Report dialog
    var showShareReportDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // --- Top Header ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Diagnostics & Logs",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${allLogs.size} entries",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row {
                        IconButton(onClick = { showShareReportDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share Report",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Crash Banner if app crashed last time
                if (crashLogFile != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "The app crashed during the previous session",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            TextButton(onClick = { showCrashReportDialog = true }) {
                                Text("View Crash Report", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Tabs: Activity, Problems, Raw
                val problemCount = remember(allLogs) {
                    allLogs.count { it.level == LogLevel.WARN || it.level == LogLevel.ERROR || it.kind == LogKind.BLOCKED }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Activity", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Problems", fontWeight = FontWeight.Bold)
                                if (problemCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.error
                                    ) {
                                        Text(
                                            text = "$problemCount",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Raw Logs", fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (selectedTab) {
                    0 -> ActivityTabContent(context, selectedAccount, settings, allLogs)
                    1 -> ProblemsTabContent(context, allLogs)
                    2 -> RawLogsTabContent(context, allLogs, droppedEvents)
                }
            }
        }
    }

    if (showCrashReportDialog && crashLogFile != null) {
        val crashText = remember { crashLogFile.readText() }
        AlertDialog(
            onDismissRequest = { showCrashReportDialog = false },
            title = { Text("Last Session Crash Report", fontWeight = FontWeight.Bold) },
            text = {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Text(
                        text = crashText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .padding(12.dp)
                            .horizontalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Crash Report", crashText))
                        Toast.makeText(context, "Copied crash report to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCrashReportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showShareReportDialog) {
        ShareReportDialog(
            context = context,
            selectedAccount = selectedAccount,
            settings = settings,
            allLogs = allLogs,
            crashText = crashLogFile?.readText(),
            onDismiss = { showShareReportDialog = false }
        )
    }
}

@Composable
private fun ActivityTabContent(
    context: Context,
    selectedAccount: GoogleAccount?,
    settings: BackupSettings?,
    allLogs: List<LogEntry>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // "Why isn't my backup running?" Diagnostic Card
        item {
            BackupDiagnosticCheckCard(context, selectedAccount, settings)
        }

        // Group activity logs by runId or session
        val runGroups = allLogs.filter { it.kind == LogKind.RUN_START || it.kind == LogKind.FILE_RESULT || it.kind == LogKind.RUN_SUMMARY || it.kind == LogKind.BLOCKED }
            .groupBy { it.runId ?: "Session Runs" }

        if (runGroups.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No backup runs recorded yet. Start a backup or enable Mock Mode to see timeline activity.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(runGroups.entries.toList().reversed(), key = { it.key }) { (runId, entries) ->
                BackupRunCard(runId = runId, entries = entries)
            }
        }
    }
}

@Composable
private fun BackupDiagnosticCheckCard(
    context: Context,
    selectedAccount: GoogleAccount?,
    settings: BackupSettings?
) {
    var expanded by remember { mutableStateOf(false) }

    val hasAccount = selectedAccount != null
    val hasFolders = settings?.folders?.any { it.enabled } == true
    val autoBackupOn = settings?.autoBackupEnabled == true
    val wifiOk = settings?.wifiOnly != true || DeviceConditions.isWifiConnected(context)
    val chargingOk = settings?.chargingOnly != true || DeviceConditions.isDeviceCharging(context)
    val batteryOptOk = DeviceConditions.isIgnoringBatteryOptimizations(context)
    val mediaPermOk = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    val notifPermOk = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    val allPass = hasAccount && hasFolders && autoBackupOn && wifiOk && chargingOk && batteryOptOk && mediaPermOk && notifPermOk

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allPass) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (allPass) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = if (allPass) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (allPass) "Why isn't my backup running? (All Systems Ready)" else "Why isn't my backup running? (Action Required)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (allPass) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = if (allPass) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }

            AnimatedVisibility(visible = expanded || !allPass) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticCheckRow("Google Account Connected", hasAccount, "Connect an account in Accounts tab.")
                    DiagnosticCheckRow("Backup Folders Enabled", hasFolders, "Enable at least one folder in Folders tab.")
                    DiagnosticCheckRow("Auto-Backup Switched On", autoBackupOn, "Turn on Auto-Backup in Settings.")
                    DiagnosticCheckRow("Wi-Fi Condition Met", wifiOk, "Connect to Wi-Fi or turn off 'Wi-Fi only' in Settings.")
                    DiagnosticCheckRow("Charging Condition Met", chargingOk, "Connect charger or turn off 'Charging only' in Settings.")
                    DiagnosticCheckRow("Battery Optimization Exempt", batteryOptOk, "Tap to grant exemption.") {
                        DeviceConditions.requestIgnoreBatteryOptimizations(context)
                    }
                    DiagnosticCheckRow("Storage/Media Permission", mediaPermOk, "Grant media permissions in Android Settings.")
                    DiagnosticCheckRow("Notifications Permission", notifPermOk, "Grant notifications permission in Android Settings.")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCheckRow(
    label: String,
    passed: Boolean,
    suggestion: String,
    onFixAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = if (passed) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = if (passed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                if (!passed) {
                    Text(text = suggestion, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (!passed && onFixAction != null) {
            TextButton(
                onClick = onFixAction,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Fix", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BackupRunCard(
    runId: String,
    entries: List<LogEntry>
) {
    var expanded by remember { mutableStateOf(false) }

    val startEntry = entries.firstOrNull { it.kind == LogKind.RUN_START }
    val summaryEntry = entries.firstOrNull { it.kind == LogKind.RUN_SUMMARY }
    val fileResults = entries.filter { it.kind == LogKind.FILE_RESULT }
    val blockedEvents = entries.filter { it.kind == LogKind.BLOCKED }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (runId.startsWith("Run")) runId else "Backup Run ($runId)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summaryEntry?.message ?: startEntry?.message ?: "Run in progress...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null
                )
            }

            if (blockedEvents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                blockedEvents.forEach { blk ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "BLOCKED: ${blk.userMessage ?: blk.message}\nAction: ${blk.suggestedAction ?: "-"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    fileResults.forEach { res ->
                        FileResultRow(res)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileResultRow(entry: LogEntry) {
    var rawExpanded by remember { mutableStateOf(false) }

    val (icon, tint) = when (entry.outcome) {
        UploadOutcome.UPLOADED -> Pair(Icons.Rounded.CheckCircle, Color(0xFF2E7D32))
        UploadOutcome.SKIPPED_DUPLICATE -> Pair(Icons.Rounded.Info, Color(0xFF1976D2))
        UploadOutcome.FAILED -> Pair(Icons.Rounded.Error, MaterialTheme.colorScheme.error)
        UploadOutcome.NONE -> Pair(Icons.Rounded.Info, Color(0xFF757575))
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { rawExpanded = !rawExpanded }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.fileName ?: "file",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                entry.sizeBytes?.let {
                    Text(
                        text = "%.1f MB".format(Locale.US, it.toDouble() / (1024 * 1024)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (entry.outcome == UploadOutcome.FAILED) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reason: ${entry.userMessage ?: entry.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                entry.suggestedAction?.let {
                    Text(
                        text = "Action: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                    )
                }
            }

            AnimatedVisibility(visible = rawExpanded) {
                Text(
                    text = "Trace:\n${entry.fullFormattedText}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ProblemsTabContent(
    context: Context,
    allLogs: List<LogEntry>
) {
    val problems = remember(allLogs) {
        allLogs.filter { it.level == LogLevel.WARN || it.level == LogLevel.ERROR || it.kind == LogKind.BLOCKED || it.tag.equals("Crash", ignoreCase = true) }
            .reversed()
    }

    if (problems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No problems or warnings detected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(problems, key = { it.id }) { entry ->
                ProblemRow(entry)
            }
        }
    }
}

@Composable
private fun ProblemRow(entry: LogEntry) {
    var expanded by remember { mutableStateOf(false) }

    val cardColor = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (entry.level == LogLevel.ERROR) Icons.Rounded.Error else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = if (entry.level == LogLevel.ERROR) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.reasonCode ?: entry.tag,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (entry.level == LogLevel.ERROR) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = entry.formattedTime,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (entry.level == LogLevel.ERROR) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.userMessage ?: entry.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.level == LogLevel.ERROR) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
            )

            entry.suggestedAction?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Suggested Action: $it",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (entry.level == LogLevel.ERROR) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    text = "Technical Detail:\n${entry.fullFormattedText}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = if (entry.level == LogLevel.ERROR) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RawLogsTabContent(
    context: Context,
    allLogs: List<LogEntry>,
    droppedEvents: Long
) {
    val coroutineScope = rememberCoroutineScope()

    var selectedLevels by remember { mutableStateOf(setOf<LogLevel>()) }
    var selectedCategories by remember { mutableStateOf(setOf<LogCategory>()) }
    var searchQuery by remember { mutableStateOf("") }
    var opIdFilter by remember { mutableStateOf("") }

    var isPaused by remember { mutableStateOf(false) }
    var isAutoScrollEnabled by remember { mutableStateOf(true) }
    var pausedSnapshot by remember { mutableStateOf<List<LogEntry>>(emptyList()) }

    val listState = rememberLazyListState()

    val activeLogs = if (isPaused) pausedSnapshot else allLogs

    val filteredLogs = remember(activeLogs, selectedLevels, selectedCategories, searchQuery, opIdFilter) {
        activeLogs.filter { entry ->
            val levelMatches = selectedLevels.isEmpty() || entry.level in selectedLevels
            val catMatches = selectedCategories.isEmpty() || entry.category in selectedCategories
            val opMatches = opIdFilter.isBlank() || (entry.opId != null && entry.opId.contains(opIdFilter.trim(), ignoreCase = true))
            val searchMatches = searchQuery.isBlank() ||
                entry.message.contains(searchQuery.trim(), ignoreCase = true) ||
                entry.tag.contains(searchQuery.trim(), ignoreCase = true) ||
                (entry.throwableText?.contains(searchQuery.trim(), ignoreCase = true) == true)
            levelMatches && catMatches && opMatches && searchMatches
        }
    }

    LaunchedEffect(filteredLogs.size, isAutoScrollEnabled, isPaused) {
        if (isAutoScrollEnabled && !isPaused && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1.4f),
                placeholder = { Text("Search logs...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = opIdFilter,
                onValueChange = { opIdFilter = it },
                modifier = Modifier.weight(1.0f),
                placeholder = { Text("Filter op#...", fontSize = 12.sp) },
                trailingIcon = {
                    if (opIdFilter.isNotEmpty()) {
                        IconButton(onClick = { opIdFilter = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val popularTags = listOf("EasterEgg", "Sensors", "UploadEngine", "Watcher", "Network", "UI", "Storage")
            FilterChip(
                selected = searchQuery.isEmpty() && opIdFilter.isEmpty(),
                onClick = { searchQuery = ""; opIdFilter = "" },
                label = { Text("ALL LOGS", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            )
            popularTags.forEach { tag ->
                val isSel = searchQuery.equals(tag, ignoreCase = true)
                FilterChip(
                    selected = isSel,
                    onClick = { searchQuery = if (isSel) "" else tag },
                    label = { Text(tag, fontSize = 10.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextButton(
                onClick = {
                    if (!isPaused) {
                        pausedSnapshot = allLogs
                        isPaused = true
                    } else {
                        isPaused = false
                    }
                }
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isPaused) "Resume" else "Pause", fontSize = 11.sp)
            }

            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = filteredLogs.joinToString("\n") { it.fullFormattedText }
                    clipboard.setPrimaryClip(ClipData.newPlainText("Lumen Logs", text))
                    Toast.makeText(context, "Copied ${filteredLogs.size} lines to clipboard", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy", fontSize = 11.sp)
            }

            TextButton(onClick = { LumenLogger.clearLogs(context) }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredLogs, key = { it.id }) { logItem ->
                    LogRow(entry = logItem, onOpClick = { op -> opIdFilter = op })
                }
            }
        }
    }
}

@Composable
private fun LogRow(
    entry: LogEntry,
    onOpClick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val levelColor = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> Color(0xFFE65100)
        LogLevel.INFO -> Color(0xFF2E7D32)
        LogLevel.DEBUG -> Color(0xFF1976D2)
        LogLevel.VERBOSE -> Color(0xFF757575)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { expanded = !expanded }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = entry.formattedTime, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = entry.level.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = levelColor)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "/${entry.tag}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (!entry.opId.isNullOrEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { onOpClick(entry.opId) }
                ) {
                    Text(text = entry.opString, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(text = entry.threadName, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = entry.message + if (entry.repeatCount > 1) " (x${entry.repeatCount})" else "",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 15.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3
        )
    }
}

@Composable
private fun ShareReportDialog(
    context: Context,
    selectedAccount: GoogleAccount?,
    settings: BackupSettings?,
    allLogs: List<LogEntry>,
    crashText: String?,
    onDismiss: () -> Unit
) {
    var hideFileNames by remember { mutableStateOf(true) }

    fun generateReport(asGitHubMarkdown: Boolean): String {
        val sb = StringBuilder()
        sb.appendLine("### Lumen Diagnostics Report")
        sb.appendLine("- **App Version**: 1.0.0-dev (Build 1)")
        sb.appendLine("- **Device**: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("- **Active Account**: ${if (selectedAccount != null) "Signed In" else "None"}")
        settings?.let {
            sb.appendLine("- **Settings**: AutoBackup=${it.autoBackupEnabled}, WifiOnly=${it.wifiOnly}, ChargingOnly=${it.chargingOnly}, StorageSaver=${it.storageSaverQuality}, Threads=${it.uploadThreads}")
        }
        sb.appendLine()

        val lastRunSummary = allLogs.lastOrNull { it.kind == LogKind.RUN_SUMMARY }
        if (lastRunSummary != null) {
            sb.appendLine("#### Last Run Summary")
            sb.appendLine(lastRunSummary.message)
            sb.appendLine()
        }

        val problems = allLogs.filter { it.level == LogLevel.WARN || it.level == LogLevel.ERROR || it.kind == LogKind.BLOCKED }
            .takeLast(20)
        if (problems.isNotEmpty()) {
            sb.appendLine("#### Recent Problems (${problems.size})")
            problems.forEach { p ->
                val fileName = if (hideFileNames && p.fileName != null) "[file_hidden]" else p.fileName ?: ""
                sb.appendLine("- [${p.formattedTime}] [${p.reasonCode ?: p.tag}] ${p.userMessage ?: p.message} $fileName")
            }
            sb.appendLine()
        }

        if (!crashText.isNullOrEmpty()) {
            sb.appendLine("#### Last Crash Trace")
            sb.appendLine("```")
            sb.appendLine(crashText.lines().take(20).joinToString("\n"))
            sb.appendLine("```")
        }

        return sb.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Diagnostics Report", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Generate a privacy-safe diagnostic report for troubleshooting.", style = MaterialTheme.typography.bodyMedium)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { hideFileNames = !hideFileNames }
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = hideFileNames,
                        onCheckedChange = { hideFileNames = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hide file names in shared report", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val text = generateReport(asGitHubMarkdown = false)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Lumen Report", text))
                    Toast.makeText(context, "Copied report to clipboard", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) {
                Text("Copy Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
