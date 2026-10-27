package com.reiux.lumen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reiux.lumen.R
import com.reiux.lumen.data.model.AccountBlock
import com.reiux.lumen.data.model.AuthFailureKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountBlockCard(
    block: AccountBlock,
    onSignInAgain: () -> Unit,
    onRemoveAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSafetyDialog by remember { mutableStateOf(false) }

    if (showSafetyDialog) {
        AccountSafetyDialog(onDismiss = { showSafetyDialog = false })
    }

    val cardColor = if (block.kind == AuthFailureKind.ACCOUNT_RESTRICTED) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val iconColor = if (block.kind == AuthFailureKind.ACCOUNT_RESTRICTED) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when (block.kind) {
                        AuthFailureKind.SIGN_IN_REQUIRED -> stringResource(R.string.breaker_sign_in_required_title)
                        AuthFailureKind.ACCOUNT_RESTRICTED -> stringResource(R.string.breaker_restricted_title)
                        AuthFailureKind.RATE_LIMITED -> stringResource(R.string.breaker_rate_limited_title)
                        else -> "Upload Paused"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val timeStr = remember(block.retryAfterTimestamp) {
                if (block.retryAfterTimestamp != null) {
                    SimpleDateFormat("HH:mm", Locale.US).format(Date(block.retryAfterTimestamp))
                } else "soon"
            }

            Text(
                text = when (block.kind) {
                    AuthFailureKind.SIGN_IN_REQUIRED -> stringResource(R.string.breaker_sign_in_required_msg)
                    AuthFailureKind.ACCOUNT_RESTRICTED -> stringResource(R.string.breaker_restricted_msg)
                    AuthFailureKind.RATE_LIMITED -> stringResource(R.string.breaker_rate_limited_msg, timeStr)
                    else -> "Uploads are paused."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = iconColor,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveTonalButton(
                    onClick = { showSafetyDialog = true },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.account_safety_button))
                }

                if (block.kind == AuthFailureKind.SIGN_IN_REQUIRED) {
                    ExpressiveButton(
                        onClick = onSignInAgain,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.breaker_sign_in_required_button))
                    }
                } else if (block.kind == AuthFailureKind.ACCOUNT_RESTRICTED) {
                    ExpressiveButton(
                        onClick = onRemoveAccount,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.breaker_restricted_button))
                    }
                }
            }
        }
    }
}
