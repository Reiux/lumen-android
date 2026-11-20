package com.reiux.lumen.ui.components

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reiux.lumen.R
import java.net.URLDecoder
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale

private const val EMBEDDED_SETUP_URL = "https://accounts.google.com/EmbeddedSetup"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleSignInWebViewDialog(
    onDismiss: () -> Unit,
    onTokenCaptured: (String) -> Unit,
    disclaimerText: String? = null
) {
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var captured by remember { mutableStateOf(false) }
    var isDisposed by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val destroyedViews = remember { Collections.newSetFromMap(IdentityHashMap<WebView, Boolean>()) }
    val currentOnTokenCaptured by rememberUpdatedState(onTokenCaptured)

    fun clearSessionStorage() {
        try {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
        } catch (_: Exception) {}
        try {
            WebStorage.getInstance().deleteAllData()
        } catch (_: Exception) {}
    }

    fun cleanupWebView(target: WebView?) {
        clearSessionStorage()
        if (target != null && destroyedViews.add(target)) {
            try {
                target.stopLoading()
                target.webViewClient = WebViewClient()
                target.loadUrl("about:blank")
                target.clearHistory()
                target.clearFormData()
                target.clearCache(true)
                (target.parent as? ViewGroup)?.removeView(target)
                target.removeAllViews()
                target.destroy()
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isDisposed = true
            cleanupWebView(webViewRef)
            webViewRef = null
        }
    }

    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        PredictiveBackDialogContainer(onDismiss = onDismiss) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (isWideLayout) 32.dp else 16.dp,
                        vertical = if (isWideLayout) 24.dp else 18.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = if (isWideLayout) 760.dp else 500.dp)
                        .fillMaxHeight(if (isWideLayout) 0.92f else 0.94f)
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
                                    text = stringResource(R.string.google_signin_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        )

                        if (disclaimerText != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = disclaimerText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

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
                                            webViewRef = this
                                            val cookieManager = CookieManager.getInstance()
                                            cookieManager.setAcceptCookie(true)
                                            cookieManager.setAcceptThirdPartyCookies(this, false)

                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.allowFileAccess = false
                                            settings.allowContentAccess = false
                                            @Suppress("DEPRECATION")
                                            settings.saveFormData = false
                                            settings.setGeolocationEnabled(false)
                                            settings.userAgentString =
                                                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36"

                                            webViewClient = object : WebViewClient() {
                                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                    if (url != "about:blank") {
                                                        isLoading = true
                                                    }
                                                }

                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    isLoading = false
                                                    if (loadError == null && url != "about:blank") {
                                                        checkCookie(url)
                                                    }
                                                }

                                                override fun onReceivedError(
                                                    view: WebView?,
                                                    request: WebResourceRequest?,
                                                    error: WebResourceError?
                                                ) {
                                                    if (request?.isForMainFrame == true) {
                                                        isLoading = false
                                                        val desc = error?.description?.toString()?.trim()
                                                        loadError = if (!desc.isNullOrEmpty()) {
                                                            desc
                                                        } else {
                                                            "Unable to connect to Google Sign-In. Check your internet connection."
                                                        }
                                                    }
                                                }

                                                override fun shouldOverrideUrlLoading(
                                                    view: WebView?,
                                                    request: WebResourceRequest?
                                                ): Boolean {
                                                    val uri = request?.url ?: return true
                                                    val host = uri.host?.lowercase(Locale.ROOT) ?: ""
                                                    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: ""
                                                    val isGoogle = host == "google.com" || host.endsWith(".google.com")
                                                    if (scheme != "https" || !isGoogle) {
                                                        return true // Block navigation outside Google auth
                                                    }
                                                    if (request.isForMainFrame) {
                                                        checkCookie(uri.toString())
                                                    }
                                                    return false
                                                }

                                                private fun checkCookie(currentUrl: String?) {
                                                    if (captured || isDisposed) return

                                                    // Check URL parameters first (strictly from accounts.google.com)
                                                    if (currentUrl != null && (currentUrl.contains("oauth_token=") || currentUrl.contains("Token="))) {
                                                        val parsedHost = try { Uri.parse(currentUrl).host?.lowercase(Locale.ROOT) } catch (_: Exception) { null }
                                                        if (parsedHost == "accounts.google.com") {
                                                            val queryToken = extractFromUrl(currentUrl)
                                                            if (!queryToken.isNullOrEmpty()) {
                                                                captured = true
                                                                clearSessionStorage()
                                                                currentOnTokenCaptured(queryToken)
                                                                return
                                                            }
                                                        }
                                                    }

                                                    // Check CookieManager on account setup completion
                                                    val cookieString = cookieManager.getCookie("https://accounts.google.com") ?: ""
                                                    val token = extractOAuthTokenFromCookies(cookieString)
                                                    if (!token.isNullOrEmpty()) {
                                                        captured = true
                                                        clearSessionStorage()
                                                        currentOnTokenCaptured(token)
                                                    }
                                                }
                                            }

                                            try {
                                                WebStorage.getInstance().deleteAllData()
                                            } catch (_: Exception) {}

                                            cookieManager.removeAllCookies {
                                                try {
                                                    cookieManager.flush()
                                                } catch (_: Exception) {}
                                                if (!isDisposed && !destroyedViews.contains(this)) {
                                                    loadUrl(EMBEDDED_SETUP_URL)
                                                }
                                            }
                                        }
                                    },
                                    onRelease = { wv ->
                                        cleanupWebView(wv)
                                        if (webViewRef === wv) {
                                            webViewRef = null
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (loadError != null) {
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
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        DynamicLumenIcon(size = 56.dp)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = stringResource(R.string.title_offline),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = loadError ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        ExpressiveButton(
                                            onClick = {
                                                loadError = null
                                                isLoading = true
                                                val wv = webViewRef
                                                val current = wv?.url
                                                if (wv != null && !destroyedViews.contains(wv)) {
                                                    if (current.isNullOrBlank() || current == "about:blank" || current.startsWith("data:") || current.startsWith("chrome-error:")) {
                                                        wv.loadUrl(EMBEDDED_SETUP_URL)
                                                    } else {
                                                        wv.reload()
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(stringResource(R.string.btn_try_again))
                                        }
                                    }
                                }
                            } else if (isLoading) {
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
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        DynamicLumenIcon(size = 64.dp)
                                        Spacer(modifier = Modifier.height(20.dp))
                                        ExpressiveLoadingIndicator(
                                            size = 44.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = stringResource(R.string.google_signin_connecting),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.google_signin_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodePercentEncodedToken(raw: String): String {
    val trimmed = raw.trim().removeSurrounding("\"")
    if (!trimmed.contains('%')) return trimmed
    return try {
        URLDecoder.decode(trimmed.replace("+", "%2B"), "UTF-8").trim()
    } catch (_: Exception) {
        trimmed
    }
}

fun extractOAuthTokenFromCookies(cookieString: String): String? {
    val cookies = cookieString.split(";")
    for (cookie in cookies) {
        val trimmed = cookie.trim()
        if (trimmed.startsWith("oauth_token=")) {
            val value = decodePercentEncodedToken(trimmed.removePrefix("oauth_token="))
            if (value.isNotEmpty()) return value
        }
    }
    return null
}

fun extractFromUrl(url: String): String? {
    val regex = Regex("[?&#](?:oauth_token|Token)=([^&#]+)")
    val match = regex.find(url)
    val raw = match?.groupValues?.get(1) ?: return null
    return decodePercentEncodedToken(raw).takeIf { it.isNotEmpty() }
}
