package com.reiux.lumen.ui.components

import android.content.res.Configuration
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.reiux.lumen.R

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
    var showWebView by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var isRawCredential by rememberSaveable { mutableStateOf(false) }
    var showManualInput by rememberSaveable(isManualOnly) { mutableStateOf(isManualOnly) }
    var localAcknowledged by rememberSaveable(hasAcknowledgedAccountRisk) { mutableStateOf(hasAcknowledgedAccountRisk) }

    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    if (showWebView) {
        GoogleSignInWebViewDialog(
            onDismiss = { showWebView = false },
            onTokenCaptured = { token ->
                showWebView = false
                onSubmitToken(token)
            }
        )
    }

    PredictiveAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isLoading,
            dismissOnClickOutside = !isLoading
        ),
        modifier = Modifier
            .widthIn(min = 340.dp, max = if (isWideLayout) 560.dp else 500.dp)
            .padding(horizontal = 16.dp),
        title = {
            Text(
                text = stringResource(R.string.account_add_dialog_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
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
                                    text = stringResource(R.string.auth_error_title),
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
                                    Text(stringResource(R.string.btn_try_again))
                                }
                            }
                        }
                    }
                }

                if (!isManualOnly) {
                    ExpressiveButton(
                        onClick = {
                            onClearError()
                            showWebView = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !isLoading && localAcknowledged
                    ) {
                        if (isLoading && !showManualInput) {
                            ExpressiveLoadingIndicator(
                                size = 20.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = stringResource(R.string.btn_sign_in_google),
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
                            text = "  ${stringResource(R.string.divider_or)}  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!showManualInput) {
                        ExpressiveTonalButton(
                            onClick = { showManualInput = true },
                            enabled = !isLoading && localAcknowledged,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_paste_token_manual))
                        }
                    }
                }

                if (showManualInput) {
                    Text(
                        text = if (isRawCredential)
                            stringResource(R.string.account_paste_credential_hint)
                        else
                            stringResource(R.string.account_paste_token_hint),
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
                        label = { Text(if (isRawCredential) stringResource(R.string.account_field_credential) else stringResource(R.string.account_field_oauth)) },
                        placeholder = {
                            Text(if (isRawCredential) "androidId=...&Email=..." else "oauth_token=...")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        isError = errorMessage != null,
                        enabled = localAcknowledged
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    ExpressiveTextButton(
                        onClick = { isRawCredential = !isRawCredential },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = if (isRawCredential) stringResource(R.string.account_switch_to_oauth) else stringResource(R.string.account_switch_to_credential),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showManualInput) {
                ExpressiveButton(
                    onClick = {
                        if (isRawCredential) {
                            onSubmitCredential(input.trim())
                        } else {
                            onSubmitToken(input.trim())
                        }
                    },
                    enabled = input.isNotBlank() && !isLoading && localAcknowledged
                ) {
                    if (isLoading) {
                        ExpressiveLoadingIndicator(
                            size = 20.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.account_btn_connect))
                }
            }
        },
        dismissButton = {
            ExpressiveTextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}
