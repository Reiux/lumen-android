package com.reiux.lumen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

enum class ExpressiveDialogTone {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    ERROR
}

/**
 * Material 3 Expressive dialog wrapper around [PredictiveAlertDialog] with a tonal icon badge,
 * bold titleLarge header, responsive container width, and expressive pill-to-squircle action buttons.
 */
@Composable
fun ExpressiveAlertDialog(
    onDismissRequest: () -> Unit,
    icon: ImageVector,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tone: ExpressiveDialogTone = ExpressiveDialogTone.PRIMARY,
    confirmEnabled: Boolean = true,
    isDestructiveConfirm: Boolean = (tone == ExpressiveDialogTone.ERROR),
    dismissText: String? = null,
    onDismissAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val badgeContainerColor = when (tone) {
        ExpressiveDialogTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
        ExpressiveDialogTone.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
        ExpressiveDialogTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
        ExpressiveDialogTone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val badgeContentColor = when (tone) {
        ExpressiveDialogTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
        ExpressiveDialogTone.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
        ExpressiveDialogTone.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
        ExpressiveDialogTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    val confirmColors = if (isDestructiveConfirm) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }

    PredictiveAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier
            .widthIn(min = 312.dp, max = 480.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = badgeContainerColor,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeContentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        },
        confirmButton = {
            ExpressiveButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = confirmColors
            ) {
                Text(
                    text = confirmText,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = dismissText?.let { label ->
            {
                ExpressiveTonalButton(
                    onClick = onDismissAction ?: onDismissRequest,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    )
}

/**
 * Tinted M3E callout container for warning details, impact summaries, or error text inside dialogs.
 */
@Composable
fun ExpressiveDialogCallout(
    modifier: Modifier = Modifier,
    tone: ExpressiveDialogTone = ExpressiveDialogTone.SECONDARY,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor = when (tone) {
        ExpressiveDialogTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ExpressiveDialogTone.SECONDARY -> MaterialTheme.colorScheme.surfaceContainerHighest
        ExpressiveDialogTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        ExpressiveDialogTone.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}
