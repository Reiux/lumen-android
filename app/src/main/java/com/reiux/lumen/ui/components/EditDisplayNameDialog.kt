package com.reiux.lumen.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reiux.lumen.R

@Composable
fun EditDisplayNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    ExpressiveAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.Badge,
        title = stringResource(R.string.dialog_edit_display_name_title),
        tone = ExpressiveDialogTone.PRIMARY,
        confirmText = stringResource(R.string.btn_save),
        onConfirm = { onSave(name.trim()) },
        dismissText = stringResource(R.string.btn_cancel),
        tabletopPlacement = TabletopDialogPlacement.TOP_HALF
    ) {
        Text(
            text = stringResource(R.string.dialog_edit_display_name_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.accounts_display_name_label)) },
            placeholder = { Text(stringResource(R.string.dialog_edit_display_name_hint)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )
    }
}
