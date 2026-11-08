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
        DialogBlurEffect()
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
                    val releaseNotes = updateInfo.releaseNotes.takeIf { it.isNotBlank() } ?: "Bug fixes and improvements."
                    val annotatedText = parseSimpleMarkdown(
                        text = releaseNotes,
                        primaryColor = MaterialTheme.colorScheme.primary,
                        codeBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        codeTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    
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
        if (!apkFile.exists() || apkFile.length() <= 0L || !apkFile.name.endsWith(".apk", ignoreCase = true)) {
            android.widget.Toast.makeText(context, "Invalid update package", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Verify package archive integrity and package name matching
        val archiveInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        if (archiveInfo == null) {
            android.widget.Toast.makeText(context, "Downloaded update is corrupt or invalid", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val expectedPackage = context.packageName.removeSuffix(".dev")
        val archivePackage = archiveInfo.packageName.removeSuffix(".dev")
        if (archivePackage != expectedPackage) {
            android.widget.Toast.makeText(context, "Security check failed: Package name mismatch", android.widget.Toast.LENGTH_LONG).show()
            return
        }

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
        android.widget.Toast.makeText(context, "Could not launch package installer: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun parseSimpleMarkdown(
    text: String,
    primaryColor: androidx.compose.ui.graphics.Color,
    codeBgColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    codeTextColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    dividerColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    val hrRegex = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    
    text.lines().forEach { rawLine ->
        val line = rawLine.trim()
        
        when {
            // Horizontal rule separator (---, ***, ___)
            line.matches(hrRegex) -> {
                builder.append("\n")
                builder.withStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = dividerColor,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                ) {
                    append("────────────────────────────────────────")
                }
                builder.append("\n\n")
            }
            line.startsWith("###### ") -> {
                builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryColor)) {
                    appendInlineMarkdown(builder, line.removePrefix("###### ").trim(), codeBgColor, codeTextColor)
                }
                builder.append("\n")
            }
            line.startsWith("##### ") -> {
                builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = primaryColor)) {
                    appendInlineMarkdown(builder, line.removePrefix("##### ").trim(), codeBgColor, codeTextColor)
                }
                builder.append("\n")
            }
            line.startsWith("#### ") -> {
                builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = primaryColor)) {
                    appendInlineMarkdown(builder, line.removePrefix("#### ").trim(), codeBgColor, codeTextColor)
                }
                builder.append("\n")
            }
            line.startsWith("### ") -> {
                builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = primaryColor)) {
                    appendInlineMarkdown(builder, line.removePrefix("### ").trim(), codeBgColor, codeTextColor)
                }
                builder.append("\n")
            }
            line.startsWith("## ") -> {
                builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = primaryColor)) {
                    appendInlineMarkdown(builder, line.removePrefix("## ").trim(), codeBgColor, codeTextColor)
                }
                builder.append("\n")
            }
            line.startsWith("# ") -> {
                builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = primaryColor)) {
                    appendInlineMarkdown(builder, line.removePrefix("# ").trim(), codeBgColor, codeTextColor)
                }
                builder.append("\n")
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = primaryColor)) {
                    append(" •  ")
                }
                appendInlineMarkdown(builder, line.drop(2).trim(), codeBgColor, codeTextColor)
                builder.append("\n")
            }
            else -> {
                if (line.isNotEmpty()) {
                    appendInlineMarkdown(builder, line, codeBgColor, codeTextColor)
                }
                builder.append("\n")
            }
        }
    }
    return builder.toAnnotatedString()
}

private fun appendInlineMarkdown(
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    text: String,
    codeBgColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    codeTextColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    // Regex matching inline code `code`, bold **text**, or italic *text*
    val inlineRegex = Regex("(`[^`]+`|\\*\\*.*?\\*\\*|\\*.*?\\*)")
    var lastIndex = 0

    inlineRegex.findAll(text).forEach { matchResult ->
        if (matchResult.range.first > lastIndex) {
            builder.append(text.substring(lastIndex, matchResult.range.first))
        }

        val match = matchResult.value
        when {
            // Inline code `example`
            match.startsWith("`") && match.endsWith("`") && match.length >= 2 -> {
                val codeContent = match.substring(1, match.length - 1)
                builder.withStyle(
                    androidx.compose.ui.text.SpanStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 13.sp,
                        background = codeBgColor,
                        color = codeTextColor
                    )
                ) {
                    append(" $codeContent ")
                }
            }
            // Bold **text**
            match.startsWith("**") && match.endsWith("**") && match.length >= 4 -> {
                val boldContent = match.substring(2, match.length - 2)
                builder.withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(boldContent)
                }
            }
            // Italic *text*
            match.startsWith("*") && match.endsWith("*") && match.length >= 2 -> {
                val italicContent = match.substring(1, match.length - 1)
                builder.withStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(italicContent)
                }
            }
            else -> {
                builder.append(match)
            }
        }
        lastIndex = matchResult.range.last + 1
    }

    if (lastIndex < text.length) {
        builder.append(text.substring(lastIndex))
    }
}
