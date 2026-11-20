package com.reiux.lumen.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.reiux.lumen.R
import java.io.File
import java.util.Locale

@Composable
fun AllFilesPermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit = {},
    onLaunchFailed: () -> Unit = {}
) {
    val context = LocalContext.current
    ExpressiveAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.FolderOpen,
        title = stringResource(R.string.all_files_dialog_title),
        tone = ExpressiveDialogTone.PRIMARY,
        confirmText = stringResource(R.string.all_files_dialog_button),
        onConfirm = {
            val launched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                } catch (_: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            } else {
                true
            }
            if (launched) {
                onConfirm()
            } else {
                onLaunchFailed()
            }
            onDismiss()
        },
        dismissText = stringResource(R.string.btn_cancel)
    ) {
        Text(
            text = stringResource(R.string.all_files_dialog_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getCanonicalPrimaryStorageRoot(): String {
    val rawExt = try {
        Environment.getExternalStorageDirectory()?.absolutePath
    } catch (_: Exception) {
        null
    }?.takeIf { it.isNotBlank() } ?: "/storage/emulated/0"
    return (try { File(rawExt).canonicalPath } catch (_: Exception) { rawExt }).trimEnd('/')
}

private fun normalizeStoragePath(path: String, primaryRoot: String): String {
    val slashed = path.replace('\\', '/').trim()
    if (slashed.isBlank() || slashed.contains('\u0000')) return ""

    val canonical = try {
        File(slashed).canonicalPath
    } catch (_: Exception) {
        if (slashed.split('/').any { it == ".." }) return ""
        slashed.replace(Regex("/+"), "/")
    }.trimEnd('/')

    val cleanPrimary = primaryRoot.trimEnd('/')
    val aliases = listOf("/sdcard", "/mnt/sdcard", "/storage/self/primary")
    for (alias in aliases) {
        if (canonical.equals(alias, ignoreCase = true)) {
            return cleanPrimary
        }
        if (canonical.startsWith("$alias/", ignoreCase = true)) {
            return cleanPrimary + canonical.substring(alias.length)
        }
    }
    return canonical
}

internal fun isStandardMediaCollectionPath(
    folderPath: String,
    primaryRoot: String = getCanonicalPrimaryStorageRoot()
): Boolean {
    val canonical = normalizeStoragePath(folderPath, primaryRoot)
    if (canonical.isBlank()) return false

    val exemptRoots = listOf(
        "$primaryRoot/DCIM",
        "$primaryRoot/Pictures",
        "$primaryRoot/Movies",
        "$primaryRoot/Music"
    )
    return exemptRoots.any { root ->
        canonical.equals(root, ignoreCase = true) ||
            canonical.startsWith("$root/", ignoreCase = true)
    }
}

fun requiresAllFilesPermission(context: Context, folderPath: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    if (Environment.isExternalStorageManager()) return false
    return !isStandardMediaCollectionPath(folderPath)
}

fun isAllowedBackupFolderPath(folderPath: String): Boolean {
    val trimmed = folderPath.trim()
    if (trimmed.isBlank()) return false

    val primaryRoot = getCanonicalPrimaryStorageRoot()
    val normalized = normalizeStoragePath(trimmed, primaryRoot)
    if (normalized.isBlank()) return false

    val lower = normalized.lowercase(Locale.ROOT)
    val primaryLower = primaryRoot.lowercase(Locale.ROOT).trimEnd('/')

    // Block canonical Android/data and Android/obb anywhere, even if com.whatsapp appears in the path
    if (Regex("(?:^|/)android/(?:data|obb)(?:/|$)").containsMatchIn(lower)) {
        return false
    }

    if (lower == "/android" || lower.startsWith("/android/")) {
        return false
    }

    val primaryAndroidPrefix = "$primaryLower/android"
    val androidSubpath: String? = when {
        lower == primaryAndroidPrefix -> ""
        lower.startsWith("$primaryAndroidPrefix/") -> lower.substring(primaryAndroidPrefix.length + 1)
        else -> {
            val match = Regex("^/storage/(?:emulated/[^/]+|[^/]+)/android(?:/(.*))?$").matchEntire(lower)
            match?.groupValues?.get(1)
        }
    }

    if (androidSubpath != null) {
        return Regex("^media/com\\.whatsapp(?:\\.[^/]+)?(?:/.*)?$").matches(androidSubpath)
    }

    return true
}

val NullableBackupFolderSaver: androidx.compose.runtime.saveable.Saver<com.reiux.lumen.data.model.BackupFolder?, ArrayList<String>> =
    androidx.compose.runtime.saveable.Saver(
        save = { folder ->
            if (folder == null) {
                arrayListOf()
            } else {
                arrayListOf(
                    folder.path,
                    folder.displayName,
                    folder.enabled.toString(),
                    folder.includeSubfolders.toString(),
                    folder.targetAlbumMode.name,
                    folder.customAlbumName ?: "",
                    folder.customAlbumNickname ?: "",
                    folder.accountEmail ?: "",
                    folder.isMissingAlbum.toString()
                )
            }
        },
        restore = { list ->
            if (list.size < 9) {
                null
            } else {
                com.reiux.lumen.data.model.BackupFolder(
                    path = list[0],
                    displayName = list[1],
                    enabled = list[2].toBoolean(),
                    includeSubfolders = list[3].toBoolean(),
                    targetAlbumMode = runCatching { com.reiux.lumen.data.model.AlbumTargetMode.valueOf(list[4]) }
                        .getOrDefault(com.reiux.lumen.data.model.AlbumTargetMode.NONE),
                    customAlbumName = list[5].ifEmpty { null },
                    customAlbumNickname = list[6].ifEmpty { null },
                    accountEmail = list[7].ifEmpty { null },
                    isMissingAlbum = list[8].toBoolean()
                )
            }
        }
    )
