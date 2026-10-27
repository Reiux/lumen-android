package com.reiux.lumen.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reiux.lumen.R

@Composable
fun AllFilesPermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit = {}
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.all_files_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.all_files_dialog_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            ExpressiveButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.all_files_dialog_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

fun requiresAllFilesPermission(context: Context, folderPath: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    if (Environment.isExternalStorageManager()) return false

    val canonical = try { java.io.File(folderPath).canonicalPath } catch (_: Exception) { folderPath }
    val ext = Environment.getExternalStorageDirectory().absolutePath
    val dcim = "$ext/DCIM"
    val pictures = "$ext/Pictures"
    val movies = "$ext/Movies"
    val music = "$ext/Music"

    return !(canonical.startsWith(dcim) || canonical.startsWith(pictures) || canonical.startsWith(movies) || canonical.startsWith(music))
}
