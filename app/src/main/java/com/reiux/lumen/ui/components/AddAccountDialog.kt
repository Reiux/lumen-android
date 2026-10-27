package com.reiux.lumen.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSubmitToken: (String) -> Unit,
    onSubmitCredential: (String) -> Unit,
    onClearError: () -> Unit = {},
    isLoading: Boolean,
    errorMessage: String?,
    hasAcknowledgedAccountRisk: Boolean = false,
    onAcknowledgeRisk: () -> Unit = {},
    isManualOnly: Boolean = false
) {
    var showWebView by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var isRawCredential by remember { mutableStateOf(false) }
    var showManualInput by remember { mutableStateOf(isManualOnly) }
    var localAcknowledged by remember(hasAcknowledgedAccountRisk) { mutableStateOf(hasAcknowledgedAccountRisk) }

    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    val dialogScale = remember { Animatable(0.88f) }
    LaunchedEffect(Unit) {
        dialogScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    if (showWebView) {
        GoogleSignInWebViewDialog(
            onDismiss = { showWebView = false },
            onTokenCaptured = { token ->
                showWebView = false
                onSubmitToken(token)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(min = 340.dp, max = if (isWideLayout) 700.dp else 500.dp)
            .padding(horizontal = 16.dp)
            .scale(dialogScale.value),
        title = {
            Text(
                text = "Add Google Account",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                AccountRiskCard(
                    hasAcknowledged = localAcknowledged,
                    onAcknowledge = {
                        localAcknowledged = true
                        onAcknowledgeRisk()
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Authentication Error",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                ExpressiveTonalButton(
                                    onClick = onClearError,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Try Again")
                                }
                            }
                        }
                    }
                }

                if (!isManualOnly) {
                    Button(
                        onClick = {
                            onClearError()
                            showWebView = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLoading && localAcknowledged
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sign In with Google",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = "  OR  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!showManualInput) {
                        FilledTonalButton(
                            onClick = { showManualInput = true },
                            enabled = !isLoading && localAcknowledged,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Paste Token or Credential Manually")
                        }
                    }
                }

                if (showManualInput) {
                    Text(
                        text = if (isRawCredential)
                            "Paste a full Lumen credential string (androidId=...&Token=...)."
                        else
                            "Enter the oauth_token cookie obtained from Google Embedded Setup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            if (it.contains("androidId=") && it.contains("Email=")) {
                                isRawCredential = true
                            }
                        },
                        label = { Text(if (isRawCredential) "Credential string" else "OAuth Token") },
                        placeholder = {
                            Text(if (isRawCredential) "androidId=...&Email=..." else "oauth_token=...")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        isError = errorMessage != null,
                        enabled = localAcknowledged
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(
                        onClick = { isRawCredential = !isRawCredential },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = if (isRawCredential) "Switch to OAuth Token" else "Switch to Credential String",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showManualInput) {
                Button(
                    onClick = {
                        if (isRawCredential) {
                            onSubmitCredential(input.trim())
                        } else {
                            onSubmitToken(input.trim())
                        }
                    },
                    enabled = input.isNotBlank() && !isLoading && localAcknowledged,
                    shape = ButtonDefaults.shape
                ) {
                    if (isLoading) {
                        ExpressiveLoadingIndicator(
                            size = 20.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Connect")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}
