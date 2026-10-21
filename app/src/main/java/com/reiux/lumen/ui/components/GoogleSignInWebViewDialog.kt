package com.reiux.lumen.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleSignInWebViewDialog(
    onDismiss: () -> Unit,
    onTokenCaptured: (String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var captured by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideLayout = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = if (isWideLayout) 860.dp else 540.dp)
                .padding(if (isWideLayout) 24.dp else 12.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(28.dp)
                ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Bordered M3E Container for WebView
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(20.dp)
                            ),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    val cookieManager = CookieManager.getInstance()
                                    cookieManager.removeAllCookies(null)
                                    cookieManager.flush()
                                    cookieManager.setAcceptCookie(true)
                                    cookieManager.setAcceptThirdPartyCookies(this, true)

                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString =
                                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36"

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            isLoading = true
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            isLoading = false
                                            checkCookie(url)
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val url = request?.url?.toString() ?: ""
                                            checkCookie(url)
                                            return false
                                        }

                                        private fun checkCookie(currentUrl: String?) {
                                            if (captured) return

                                            // Check URL parameters first
                                            if (currentUrl != null && (currentUrl.contains("oauth_token=") || currentUrl.contains("Token="))) {
                                                val queryToken = extractFromUrl(currentUrl)
                                                if (!queryToken.isNullOrEmpty()) {
                                                    captured = true
                                                    onTokenCaptured(queryToken)
                                                    return
                                                }
                                            }

                                            // Check CookieManager on account setup completion
                                            val cookieString = cookieManager.getCookie("https://accounts.google.com") ?: ""
                                            val token = extractOAuthTokenFromCookies(cookieString)
                                            if (!token.isNullOrEmpty()) {
                                                captured = true
                                                onTokenCaptured(token)
                                            }
                                        }
                                    }

                                    loadUrl("https://accounts.google.com/EmbeddedSetup")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Pleasant M3E Loading Screen
                    if (isLoading) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp)),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                            ) {
                                DynamicLumenIcon(size = 64.dp)
                                Spacer(modifier = Modifier.height(20.dp))
                                ExpressiveLoadingIndicator(
                                    size = 44.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Connecting to Google...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Sign in to grant backup permission for Google Photos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun extractOAuthTokenFromCookies(cookieString: String): String? {
    val cookies = cookieString.split(";")
    for (cookie in cookies) {
        val trimmed = cookie.trim()
        if (trimmed.startsWith("oauth_token=")) {
            val value = trimmed.removePrefix("oauth_token=").trim()
            if (value.isNotEmpty()) return value
        }
    }
    return null
}

fun extractFromUrl(url: String): String? {
    val regex = Regex("[?&#](?:oauth_token|Token)=([^&#]+)")
    val match = regex.find(url)
    return match?.groupValues?.get(1)
}
