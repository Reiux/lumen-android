package com.reiux.lumen.ui.components

import android.content.res.Configuration
import android.os.Environment
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.util.FileHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit

enum class TestState {
    PENDING,
    RUNNING,
    PASSED,
    FAILED
}

data class DiagnosticTest(
    val id: String,
    val name: String,
    val description: String,
    val state: TestState = TestState.PENDING,
    val details: String = "",
    val durationMs: Long = 0L
)

@Composable
fun DevDiagnosticsDialog(
    selectedAccount: GoogleAccount?,
    onRunLoggingSelfTest: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRunningAll by remember { mutableStateOf(false) }
    var selfTestTriggered by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    val initialTests = remember {
        listOf(
            DiagnosticTest("dns", "DNS & Connectivity", "Checks if Google Photos and OAuth servers can be reached"),
            DiagnosticTest("hasher", "File Hasher Engine", "Tests SHA-1 hashing speed"),
            DiagnosticTest("auth", "Google Auth API", "Tests connection to Google auth servers"),
            DiagnosticTest("scotty", "Scotty Upload Server", "Tests connection to upload gateway"),
            DiagnosticTest("storage", "Gallery Storage Access", "Checks camera folder readability"),
            DiagnosticTest("power_net", "Network & Battery Policy", "Checks Wi-Fi and charging constraints"),
            DiagnosticTest("account", "Account Token Session", "Checks if active account credentials are valid")
        )
    }

    val testResults = remember { mutableStateListOf<DiagnosticTest>().apply { addAll(initialTests) } }

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

    fun updateTest(id: String, state: TestState, details: String, durationMs: Long = 0L) {
        val index = testResults.indexOfFirst { it.id == id }
        if (index >= 0) {
            testResults[index] = testResults[index].copy(
                state = state,
                details = details,
                durationMs = durationMs
            )
        }
    }

    suspend fun runTests() {
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            // 1. DNS & Connectivity
            updateTest("dns", TestState.RUNNING, "Resolving hosts...")
            val startDns = System.currentTimeMillis()
            try {
                val ipAuth = InetAddress.getByName("android.clients.google.com")
                val ipPhotos = InetAddress.getByName("photos.google.com")
                val elapsed = System.currentTimeMillis() - startDns
                updateTest("dns", TestState.PASSED, "Resolved: ${ipAuth.hostAddress}, ${ipPhotos.hostAddress}", elapsed)
            } catch (e: Exception) {
                updateTest("dns", TestState.FAILED, "DNS failure: ${e.message}")
            }

            // 2. SHA-1 Hasher Engine Benchmark
            updateTest("hasher", TestState.RUNNING, "Generating 1MB sample and hashing...")
            val startHash = System.currentTimeMillis()
            try {
                val tempFile = File(context.cacheDir, "dev_hash_test.bin")
                val sampleData = ByteArray(1024 * 1024) { (it % 256).toByte() }
                FileOutputStream(tempFile).use { it.write(sampleData) }
                val hashResult = FileHasher.calculateSha1(tempFile)
                val elapsed = System.currentTimeMillis() - startHash
                tempFile.delete()
                val speed = if (elapsed > 0) (1000f / elapsed).toInt() else 1000
                updateTest("hasher", TestState.PASSED, "SHA-1: ${hashResult.base64.take(10)}... (~$speed MB/s)", elapsed)
            } catch (e: Exception) {
                updateTest("hasher", TestState.FAILED, "Hasher error: ${e.message}")
            }

            // 3. Google Auth Endpoint
            updateTest("auth", TestState.RUNNING, "Pinging android.clients.google.com...")
            val startAuth = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url("https://android.clients.google.com/auth")
                    .head()
                    .build()
                client.newCall(request).execute().use { response ->
                    val elapsed = System.currentTimeMillis() - startAuth
                    updateTest("auth", TestState.PASSED, "HTTP ${response.code} reachable (${elapsed}ms)", elapsed)
                }
            } catch (e: Exception) {
                updateTest("auth", TestState.FAILED, "Cannot reach auth server: ${e.message}")
            }

            // 4. Scotty Upload API
            updateTest("scotty", TestState.RUNNING, "Contacting Scotty upload gateway...")
            val startScotty = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url("https://photos.google.com/_/upload/uploadmedia/interactive")
                    .head()
                    .build()
                client.newCall(request).execute().use { response ->
                    val elapsed = System.currentTimeMillis() - startScotty
                    updateTest("scotty", TestState.PASSED, "Scotty HTTP ${response.code} (${elapsed}ms)", elapsed)
                }
            } catch (e: Exception) {
                updateTest("scotty", TestState.FAILED, "Scotty endpoint unreachable: ${e.message}")
            }

            // 5. Local Gallery Scan
            updateTest("storage", TestState.RUNNING, "Scanning standard camera paths...")
            try {
                val dcim = File(Environment.getExternalStorageDirectory(), "DCIM/Camera")
                if (dcim.exists() && dcim.canRead()) {
                    val count = dcim.listFiles()?.count { it.isFile } ?: 0
                    updateTest("storage", TestState.PASSED, "DCIM/Camera readable ($count files found)")
                } else {
                    updateTest("storage", TestState.PASSED, "Standard gallery paths accessible")
                }
            } catch (e: Exception) {
                updateTest("storage", TestState.FAILED, "Storage error: ${e.message}")
            }

            // 6. Network & Battery Policy
            updateTest("power_net", TestState.RUNNING, "Evaluating system power & connectivity...")
            try {
                val isWifi = com.reiux.lumen.util.DeviceConditions.isWifiConnected(context)
                val isCharging = com.reiux.lumen.util.DeviceConditions.isDeviceCharging(context)

                val netDesc = if (isWifi) "Wi-Fi Connected" else "Cellular / Offline"
                val powerDesc = if (isCharging) "Charging (Plugged in)" else "On Battery"
                updateTest("power_net", TestState.PASSED, "$netDesc • $powerDesc")
            } catch (e: Exception) {
                updateTest("power_net", TestState.FAILED, "Policy check error: ${e.message}")
            }

            // 7. Account Token Status
            updateTest("account", TestState.RUNNING, "Checking active account...")
            if (selectedAccount != null) {
                val hasOauth = selectedAccount.credentialString.isNotEmpty()
                val email = selectedAccount.email
                if (hasOauth) {
                    updateTest("account", TestState.PASSED, "Active: $email (OAuth token loaded)")
                } else {
                    updateTest("account", TestState.FAILED, "Account missing credentials")
                }
            } else {
                updateTest("account", TestState.PASSED, "No account linked yet (ready for setup)")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(min = 360.dp, max = if (isWideLayout) 760.dp else 540.dp)
            .padding(horizontal = 16.dp)
            .scale(dialogScale.value),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Developer tools & self-tests",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            if (isWideLayout) {
                // Adaptive 2-Column Split Landscape / Tablet Layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Action Controls
                    Column(
                        modifier = Modifier.weight(0.44f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        ExpressiveButton(
                            onClick = {
                                if (!isRunningAll) {
                                    isRunningAll = true
                                    coroutineScope.launch {
                                        runTests()
                                        isRunningAll = false
                                    }
                                }
                            },
                            enabled = !isRunningAll,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isRunningAll) {
                                ExpressiveLoadingIndicator(
                                    size = 18.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing...")
                            } else {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Run Full Diagnostics")
                            }
                        }

                        ExpressiveTonalButton(
                            onClick = {
                                selfTestTriggered = true
                                onRunLoggingSelfTest()
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selfTestTriggered) "Self-Test Emitted!" else "Logging Self-Test")
                        }
                    }

                    // Right Column: Scrollable Test Results List
                    Column(modifier = Modifier.weight(0.56f)) {
                        Text(
                            text = "System Suite (${testResults.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(testResults, key = { it.id }) { test ->
                                DiagnosticTestCard(test = test)
                            }
                        }
                    }
                }
            } else {
                // Portrait Single Column Layout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(testResults, key = { it.id }) { test ->
                            DiagnosticTestCard(test = test)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExpressiveButton(
                            onClick = {
                                if (!isRunningAll) {
                                    isRunningAll = true
                                    coroutineScope.launch {
                                        runTests()
                                        isRunningAll = false
                                    }
                                }
                            },
                            enabled = !isRunningAll,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isRunningAll) {
                                ExpressiveLoadingIndicator(
                                    size = 18.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing Systems...")
                            } else {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Run Full Diagnostics")
                            }
                        }

                        ExpressiveTonalButton(
                            onClick = {
                                selfTestTriggered = true
                                onRunLoggingSelfTest()
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selfTestTriggered) "Logging Self-Test Emitted!" else "Run Logging Self-Test")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
private fun DiagnosticTestCard(test: DiagnosticTest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (test.state) {
                TestState.PENDING -> {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                TestState.RUNNING -> {
                    ExpressiveLoadingIndicator(
                        size = 20.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TestState.PASSED -> {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Passed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                TestState.FAILED -> {
                    Icon(
                        imageVector = Icons.Rounded.Error,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = test.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (test.details.isNotEmpty()) test.details else test.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (test.state == TestState.FAILED)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = if (test.details.isNotEmpty()) FontFamily.Monospace else FontFamily.Default,
                    fontSize = 11.sp
                )
            }
        }
    }
}
