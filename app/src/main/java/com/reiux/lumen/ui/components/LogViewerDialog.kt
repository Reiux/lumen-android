package com.reiux.lumen.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
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
import com.reiux.lumen.util.LogCategory
import com.reiux.lumen.util.LogEntry
import com.reiux.lumen.util.LogLevel
import com.reiux.lumen.util.LumenLogger
import com.reiux.lumen.util.UiSnapshotDumper
import kotlinx.coroutines.launch

@Composable
fun LogViewerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allLogs by LumenLogger.logs.collectAsState()
    val droppedEvents by LumenLogger.droppedEvents.collectAsState()

    // Filters state
    var selectedLevels by remember { mutableStateOf(setOf<LogLevel>()) }
    var selectedCategories by remember { mutableStateOf(setOf<LogCategory>()) }
    var searchQuery by remember { mutableStateOf("") }
    var opIdFilter by remember { mutableStateOf("") }

    // Controls
    var isPaused by remember { mutableStateOf(false) }
    var isAutoScrollEnabled by remember { mutableStateOf(true) }
    var pausedSnapshot by remember { mutableStateOf<List<LogEntry>>(emptyList()) }

    val listState = rememberLazyListState()

    // Active logs list respecting pause
    val activeLogs = if (isPaused) pausedSnapshot else allLogs

    // Filtered logs computation
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
                                text = "Logs",
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
                                    text = "${filteredLogs.size}/${allLogs.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (droppedEvents > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = "$droppedEvents dropped",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isPaused) "Paused" else "Updating live",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
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

                Spacer(modifier = Modifier.height(8.dp))

                // --- Search & Op# Filter Row ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1.4f),
                        placeholder = { Text("Search logs or tags...", fontSize = 12.sp) },
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
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
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
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // --- Level Filter Chips ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedLevels.isEmpty(),
                        onClick = { selectedLevels = emptySet() },
                        label = { Text("ALL LEVELS", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    LogLevel.entries.forEach { level ->
                        val isSelected = level in selectedLevels
                        val chipColor = when (level) {
                            LogLevel.VERBOSE -> Color(0xFF757575)
                            LogLevel.DEBUG -> Color(0xFF1976D2)
                            LogLevel.INFO -> Color(0xFF2E7D32)
                            LogLevel.WARN -> Color(0xFFE65100)
                            LogLevel.ERROR -> Color(0xFFD32F2F)
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedLevels = if (isSelected) {
                                    selectedLevels - level
                                } else {
                                    selectedLevels + level
                                }
                            },
                            label = { Text(level.name, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor.copy(alpha = 0.25f),
                                selectedLabelColor = chipColor
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // --- Category Filter Chips ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategories.isEmpty(),
                        onClick = { selectedCategories = emptySet() },
                        label = { Text("ALL TAGS", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    listOf(
                        LogCategory.UPLOAD,
                        LogCategory.NETWORK,
                        LogCategory.WATCHER,
                        LogCategory.STORAGE,
                        LogCategory.UI,
                        LogCategory.ANIM,
                        LogCategory.PERF,
                        LogCategory.INTERACTION,
                        LogCategory.LIFECYCLE
                    ).forEach { cat ->
                        val isSelected = cat in selectedCategories
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCategories = if (isSelected) {
                                    selectedCategories - cat
                                } else {
                                    selectedCategories + cat
                                }
                            },
                            label = { Text(cat.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // --- Action Toolbar (Pause, Auto-Scroll, Snapshot UI, Copy, Export, Clear) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Pause/Resume button
                    TextButton(
                        onClick = {
                            if (!isPaused) {
                                pausedSnapshot = allLogs
                                isPaused = true
                            } else {
                                isPaused = false
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPaused) "Resume" else "Pause", fontSize = 11.sp)
                    }

                    // Auto-scroll toggle
                    FilterChip(
                        selected = isAutoScrollEnabled,
                        onClick = { isAutoScrollEnabled = !isAutoScrollEnabled },
                        label = { Text("Auto-Scroll", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )

                    // Snapshot UI button
                    TextButton(
                        onClick = {
                            UiSnapshotDumper.dump()
                            Toast.makeText(context, "Saved UI snapshot to logs", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Snapshot UI", fontSize = 11.sp)
                    }

                    // Jump to Bottom
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                if (filteredLogs.isNotEmpty()) {
                                    listState.animateScrollToItem(filteredLogs.size - 1)
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Bottom", fontSize = 11.sp)
                    }

                    // Copy
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = filteredLogs.joinToString("\n") { it.fullFormattedText }
                            clipboard.setPrimaryClip(ClipData.newPlainText("Lumen Logs", text))
                            Toast.makeText(context, "Copied ${filteredLogs.size} lines to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 11.sp)
                    }

                    // Export / Share
                    TextButton(
                        onClick = { LumenLogger.exportLogs(context) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export", fontSize = 11.sp)
                    }

                    // Clear
                    TextButton(
                        onClick = { LumenLogger.clearLogs(context) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // --- Log Entries Container ---
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    if (filteredLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No logs match the current filters",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredLogs, key = { it.id }) { logItem ->
                                LogRow(
                                    entry = logItem,
                                    onOpClick = { op -> opIdFilter = op }
                                )
                            }
                        }
                    }
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
        // Line Header: [Time] [Level/Tag] [thread] [op#id]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.formattedTime,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = entry.level.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = levelColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "/${entry.tag}",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!entry.opId.isNullOrEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { onOpClick(entry.opId) }
                ) {
                    Text(
                        text = entry.opString,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = entry.threadName,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Message text
        Text(
            text = entry.message,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 15.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3
        )

        // Stack trace / exception details
        if (!entry.throwableText.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (expanded) entry.throwableText else entry.throwableText.lines().take(2).joinToString("\n") + if (entry.throwableText.lines().size > 2) "\n... [tap to expand]" else "",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error,
                lineHeight = 13.sp
            )
        }

        // Expanded full formatted line
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(
                        text = "Full trace:\n${entry.fullFormattedText}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}
