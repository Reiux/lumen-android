package com.reiux.lumen.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.reiux.lumen.R
import com.reiux.lumen.data.model.UpdateInfo
import com.reiux.lumen.util.LumenLogger
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadError: String? = null,
    onDismiss: () -> Unit,
    onUpdateClick: () -> Unit
) {
    val currentIsDownloading by rememberUpdatedState(isDownloading)
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            !(currentIsDownloading && targetValue == androidx.compose.material3.SheetValue.Hidden)
        }
    )
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        androidx.activity.compose.BackHandler(enabled = isDownloading) {
            // Block back press dismissal while an update download is in progress
        }
        DialogBlurEffect()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                        text = stringResource(R.string.update_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.update_dialog_subtitle, updateInfo.versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val defaultNotes = stringResource(R.string.update_dialog_default_notes)
                    val releaseNotes = updateInfo.releaseNotes.takeIf { it.isNotBlank() } ?: defaultNotes
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

            AnimatedVisibility(
                visible = isDownloading,
                enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.update_downloading_progress),
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
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(
                visible = !downloadError.isNullOrBlank() && !isDownloading,
                enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = downloadError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ExpressiveTextButton(
                    onClick = onDismiss,
                    enabled = !isDownloading
                ) {
                    Text(stringResource(R.string.update_btn_later))
                }
                Spacer(modifier = Modifier.width(12.dp))
                ExpressiveButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                )
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Could not open unknown app sources settings",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            onUpdateClick()
                        }
                    },
                    enabled = !isDownloading
                ) {
                    Text(if (isDownloading) stringResource(R.string.update_btn_downloading) else stringResource(R.string.update_btn_update_now))
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

        // Verify package archive integrity, package name, and signing certificate matching
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            android.content.pm.PackageManager.GET_SIGNATURES
        }
        val archiveInfo = getPackageArchiveInfoWithRetry(pm, apkFile, flags)
        if (archiveInfo == null) {
            LumenLogger.w("Update") { "getPackageArchiveInfo returned null for ${apkFile.absolutePath}; refusing to install unverified archive" }
            android.widget.Toast.makeText(context, "Security check failed: could not verify update package", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val expectedPackage = context.packageName.removeSuffix(".dev")
        val archivePackage = archiveInfo.packageName.removeSuffix(".dev")
        if (archivePackage != expectedPackage) {
            android.widget.Toast.makeText(context, "Security check failed: Package name mismatch", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        val installedInfo = pm.getPackageInfo(context.packageName, flags)
        val archiveDigests = extractSigningDigests(archiveInfo)
        val installedDigests = extractSigningDigests(installedInfo)
        if (archiveDigests.isEmpty() || archiveDigests.intersect(installedDigests).isEmpty()) {
            android.widget.Toast.makeText(context, "Security check failed: APK signature mismatch", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        launchInstaller(context, apkFile)
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Could not launch package installer: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/**
 * Parses an APK archive, retrying once with extra flag bits. Some OEM builds return
 * null from getPackageArchiveInfo for perfectly valid APKs unless additional package
 * metadata (e.g. GET_ACTIVITIES) is requested.
 */
private fun getPackageArchiveInfoWithRetry(
    pm: android.content.pm.PackageManager,
    apkFile: File,
    flags: Int
): android.content.pm.PackageInfo? {
    pm.getPackageArchiveInfo(apkFile.absolutePath, flags)?.let { return it }
    return pm.getPackageArchiveInfo(apkFile.absolutePath, flags or android.content.pm.PackageManager.GET_ACTIVITIES)
}

private fun launchInstaller(context: Context, apkFile: File) {
    android.widget.Toast.makeText(context, "Launching installer...", android.widget.Toast.LENGTH_SHORT).show()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}

private fun extractSigningDigests(pkgInfo: android.content.pm.PackageInfo): Set<String> {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val signingInfo = pkgInfo.signingInfo ?: return emptySet()
        if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
    } else {
        @Suppress("DEPRECATION")
        pkgInfo.signatures
    } ?: return emptySet()

    val md = java.security.MessageDigest.getInstance("SHA-256")
    return signatures.mapNotNull { sig ->
        try {
            md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }.toSet()
}

fun stripDownloadsSection(rawText: String): String {
    val hrRegex = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    val headerRegex = Regex("^#{1,6}\\s+.*")
    val downloadsHeaderRegex = Regex("^#{1,6}\\s+downloads\\b.*", RegexOption.IGNORE_CASE)

    val keptLines = mutableListOf<String>()
    var skippingDownloads = false

    for (rawLine in rawText.lines()) {
        val trimmed = rawLine.trim()
        if (downloadsHeaderRegex.matches(trimmed)) {
            skippingDownloads = true
            continue
        }
        if (skippingDownloads) {
            if (headerRegex.matches(trimmed)) {
                skippingDownloads = false
            } else {
                // Skip lines inside the Downloads section (including its trailing --- divider)
                continue
            }
        }
        keptLines.add(rawLine)
    }

    // Trim trailing blank lines and trailing horizontal rules so the sheet ends cleanly
    while (keptLines.isNotEmpty()) {
        val last = keptLines.last().trim()
        if (last.isEmpty() || hrRegex.matches(last)) {
            keptLines.removeAt(keptLines.lastIndex)
        } else {
            break
        }
    }

    // Remove blank lines immediately before or after horizontal rules (---) so dividers
    // don't render with large empty gaps above and below them
    val compactLines = mutableListOf<String>()
    for (i in keptLines.indices) {
        val curr = keptLines[i].trim()
        if (curr.isEmpty()) {
            val prevIsHr = compactLines.lastOrNull()?.trim()?.let { hrRegex.matches(it) } == true
            val nextNonEmpty = keptLines.subList(i + 1, keptLines.size).firstOrNull { it.trim().isNotEmpty() }?.trim()
            val nextIsHr = nextNonEmpty != null && hrRegex.matches(nextNonEmpty)
            if (prevIsHr || nextIsHr) {
                continue
            }
        }
        compactLines.add(keptLines[i])
    }

    return compactLines.joinToString("\n")
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
    val cleanedText = stripDownloadsSection(text)
    
    cleanedText.lines().forEach { rawLine ->
        val line = rawLine.trim()
        
        when {
            // Horizontal rule separator (---, ***, ___)
            line.matches(hrRegex) -> {
                builder.withStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = dividerColor,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                ) {
                    append("────────────────────────────────────────")
                }
                builder.append("\n")
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
