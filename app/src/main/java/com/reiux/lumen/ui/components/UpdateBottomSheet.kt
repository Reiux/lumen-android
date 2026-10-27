package com.reiux.lumen.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.reiux.lumen.data.model.UpdateInfo
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Int,
    onDismiss: () -> Unit,
    onUpdateClick: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdateAlt,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Update Available",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Version ${updateInfo.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "What's New",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val releaseNotes = updateInfo.releaseNotes.takeIf { it.isNotBlank() } ?: "Bug fixes and improvements."
                    val annotatedText = parseSimpleMarkdown(releaseNotes, MaterialTheme.colorScheme.primary)
                    
                    Text(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(visible = isDownloading) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Downloading update...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$downloadProgress%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ExpressiveWavyLinearProgress(
                        progress = (downloadProgress / 100f).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isDownloading
                ) {
                    Text("Later")
                }
                Spacer(modifier = Modifier.width(12.dp))
                ExpressiveButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        } else {
                            onUpdateClick()
                        }
                    },
                    enabled = !isDownloading
                ) {
                    Text(if (isDownloading) "Downloading..." else "Update Now")
                }
            }
        }
    }
}

fun launchApkInstall(context: Context, apkFile: File) {
    try {
        android.widget.Toast.makeText(context, "Launching installer...", android.widget.Toast.LENGTH_SHORT).show()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun parseSimpleMarkdown(text: String, primaryColor: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    
    text.lines().forEach { line ->
        var currentLine = line.trimEnd()
        
        when {
            currentLine.startsWith("### ") -> {
                builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = primaryColor)) {
                    append(currentLine.removePrefix("### "))
                }
                builder.append("\n")
            }
            currentLine.startsWith("## ") -> {
                builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = primaryColor)) {
                    append(currentLine.removePrefix("## "))
                }
                builder.append("\n")
            }
            currentLine.startsWith("# ") -> {
                builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = primaryColor)) {
                    append(currentLine.removePrefix("# "))
                }
                builder.append("\n")
            }
            currentLine.startsWith("- ") || currentLine.startsWith("* ") -> {
                builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = primaryColor)) {
                    append(" •  ")
                }
                appendInlineMarkdown(builder, currentLine.drop(2))
                builder.append("\n")
            }
            else -> {
                if (currentLine.isNotEmpty()) {
                    appendInlineMarkdown(builder, currentLine)
                }
                builder.append("\n")
            }
        }
    }
    return builder.toAnnotatedString()
}

private fun appendInlineMarkdown(builder: androidx.compose.ui.text.AnnotatedString.Builder, text: String) {
    val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
    var lastIndex = 0
    
    boldRegex.findAll(text).forEach { matchResult ->
        // Text before the match
        builder.append(text.substring(lastIndex, matchResult.range.first))
        // The bold text
        builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
            append(matchResult.groupValues[1])
        }
        lastIndex = matchResult.range.last + 1
    }
    
    // Remaining text
    if (lastIndex < text.length) {
        builder.append(text.substring(lastIndex))
    }
}
