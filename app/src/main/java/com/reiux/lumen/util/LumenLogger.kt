package com.reiux.lumen.util

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

enum class LogLevel(val priority: Int, val shortCode: String) {
    VERBOSE(2, "V"),
    DEBUG(3, "D"),
    INFO(4, "I"),
    WARN(5, "W"),
    ERROR(6, "E")
}

enum class LogCategory(val displayName: String) {
    UI("UI"),
    ANIM("Anim"),
    PERF("Perf"),
    INTERACTION("Interaction"),
    NETWORK("Network"),
    UPLOAD("Upload"),
    LIFECYCLE("Lifecycle"),
    STORAGE("Storage"),
    WATCHER("Watcher"),
    OTHER("Other")
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val threadName: String,
    val opId: String? = null,
    val category: LogCategory = LogCategory.OTHER,
    val message: String,
    val throwableText: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))

    val opString: String
        get() = if (!opId.isNullOrEmpty()) {
            if (opId.startsWith("op#")) opId else "op#$opId"
        } else {
            "-"
        }

    val fullFormattedText: String
        get() {
            val base = "[$formattedTime] [${level.name}/$tag] [$threadName] [$opString] $message"
            return if (!throwableText.isNullOrEmpty()) "$base\n$throwableText" else base
        }
}

object LumenLogger {
    private const val MAX_IN_MEMORY_LOGS = 2500
    private const val ROTATING_FILE_COUNT = 5
    private const val MAX_FILE_SIZE_BYTES = 2L * 1024L * 1024L // 2 MB
    private const val RATE_LIMIT_MAX_PER_SEC = 20

    private var nextId = 0L
    private val droppedCounter = AtomicLong(0L)

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _droppedEvents = MutableStateFlow(0L)
    val droppedEvents: StateFlow<Long> = _droppedEvents.asStateFlow()

    // Verbose / Dev diagnostics toggle: ON = VERBOSE & DEBUG enabled; OFF = INFO, WARN, ERROR only
    var isVerboseEnabled: Boolean = true
        private set

    var devDiagnosticsEnabled: Boolean = false
        private set

    // Category filter toggles (Live in viewer; default all enabled)
    val activeCategories = ConcurrentHashMap<LogCategory, Boolean>().apply {
        LogCategory.entries.forEach { put(it, true) }
    }

    private var isFileLoggingEnabled = false
    private var logDir: File? = null
    private var currentFileIndex = 0
    private val scope = CoroutineScope(Dispatchers.IO)
    private var snapshotJob: Job? = null
    private var appContext: Context? = null

    // Rate limiter tracking
    private class RateLimitTracker {
        var windowSecond = 0L
        var count = 0
        var suppressed = 0
    }
    private val rateLimiters = ConcurrentHashMap<String, RateLimitTracker>()

    // Redaction patterns
    private val emailPattern = Pattern.compile("(?i)[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")
    private val bearerPattern = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9_\\-\\.]+")
    private val sensitiveParamPattern = Pattern.compile("(?i)(token|masterToken|credentialString|upload_token|password|auth|secret)=([^&\\s]+)")

    fun init(context: Context, saveLogsEnabled: Boolean, isDevDiagnostics: Boolean = true) {
        appContext = context.applicationContext
        logDir = File(context.cacheDir, "logs").apply { mkdirs() }
        devDiagnosticsEnabled = isDevDiagnostics
        isVerboseEnabled = isDevDiagnostics || saveLogsEnabled
        isFileLoggingEnabled = saveLogsEnabled || isDevDiagnostics

        installCrashHandler()
        startPeriodicSnapshot()

        i("LumenLogger") {
            "LumenLogger initialized (verbose=$isVerboseEnabled, fileLogging=$isFileLoggingEnabled, devDiagnostics=$devDiagnosticsEnabled, cacheDir=${logDir?.absolutePath})"
        }
    }

    fun setDevDiagnostics(enabled: Boolean, context: Context? = null) {
        devDiagnosticsEnabled = enabled
        isVerboseEnabled = enabled || isFileLoggingEnabled
        if (enabled) {
            isFileLoggingEnabled = true
        }
        context?.let { appContext = it.applicationContext }
        i("LumenLogger") { "devDiagnosticsEnabled set to: $enabled (isVerboseEnabled=$isVerboseEnabled, isFileLoggingEnabled=$isFileLoggingEnabled)" }
    }

    fun setFileLogging(enabled: Boolean, context: Context) {
        appContext = context.applicationContext
        isFileLoggingEnabled = enabled
        if (logDir == null) {
            logDir = File(context.cacheDir, "logs").apply { mkdirs() }
        }
        i("LumenLogger") { "File logging changed: $enabled (dir=${logDir?.absolutePath})" }
    }

    fun setCategoryEnabled(category: LogCategory, enabled: Boolean) {
        activeCategories[category] = enabled
    }

    fun isCategoryEnabled(category: LogCategory): Boolean = activeCategories[category] ?: true

    // --- Redaction ---
    fun redact(text: String): String {
        var result = text
        // Mask emails: e.g. alex.rivera@gmail.com -> a***@gmail.com
        val emailMatcher = emailPattern.matcher(result)
        val emailSb = StringBuffer()
        while (emailMatcher.find()) {
            val fullEmail = emailMatcher.group()
            val atIdx = fullEmail.indexOf('@')
            val masked = if (atIdx > 1) {
                fullEmail.substring(0, 1) + "***@" + fullEmail.substring(atIdx + 1)
            } else {
                "***@" + fullEmail.substring(atIdx + 1)
            }
            emailMatcher.appendReplacement(emailSb, MatcherQuote(masked))
        }
        emailMatcher.appendTail(emailSb)
        result = emailSb.toString()

        // Mask Bearer tokens
        result = bearerPattern.matcher(result).replaceAll("$1[REDACTED]")

        // Mask query string secrets
        result = sensitiveParamPattern.matcher(result).replaceAll("$1=[REDACTED]")

        return result
    }

    private fun MatcherQuote(s: String): String = java.util.regex.Matcher.quoteReplacement(s)

    // --- Category Resolver ---
    private fun inferCategory(tag: String): LogCategory {
        return when (tag.lowercase()) {
            "ui", "aboutui", "backupui", "foldersui", "accountsui", "settingsui", "nav", "navbar", "navrail" -> LogCategory.UI
            "anim", "animation" -> LogCategory.ANIM
            "perf", "performance", "recomp", "jank" -> LogCategory.PERF
            "interaction", "gesture", "click", "touch" -> LogCategory.INTERACTION
            "network", "googlephotos", "googlephotosclient", "auth", "googleauth", "googleauthclient", "http" -> LogCategory.NETWORK
            "upload", "uploadengine", "uploadrepository", "uploader", "backup" -> LogCategory.UPLOAD
            "lifecycle", "applifecycle", "layout" -> LogCategory.LIFECYCLE
            "storage", "datastore", "db", "hashcache" -> LogCategory.STORAGE
            "watcher", "folderwatcher", "folderwatchermanager" -> LogCategory.WATCHER
            else -> LogCategory.OTHER
        }
    }

    // --- Core Logging Logic ---
    private fun shouldLog(level: LogLevel): Boolean {
        return if (isVerboseEnabled || devDiagnosticsEnabled) {
            true // Log all levels in verbose / dev mode
        } else {
            level.priority >= LogLevel.INFO.priority // Drop VERBOSE and DEBUG when verbose is off
        }
    }

    fun log(
        level: LogLevel,
        tag: String,
        opId: String? = null,
        tr: Throwable? = null,
        messageProvider: () -> String
    ) {
        if (!shouldLog(level)) return

        val now = System.currentTimeMillis()
        val currentSec = now / 1000L

        // Rate limiter check for identical repetitive lines
        val rawMessage = try { messageProvider() } catch (e: Exception) { "<Log evaluation failed: ${e.message}>" }
        val redactedMessage = redact(rawMessage)

        val rateKey = "$tag:$redactedMessage"
        val tracker = rateLimiters.computeIfAbsent(rateKey) { RateLimitTracker() }
        synchronized(tracker) {
            if (tracker.windowSecond != currentSec) {
                if (tracker.suppressed > 0) {
                    emitLogEntry(
                        LogLevel.WARN,
                        "RateLimiter",
                        threadName = Thread.currentThread().name,
                        opId = null,
                        category = LogCategory.PERF,
                        message = "${tracker.suppressed} similar lines suppressed for tag: $tag",
                        throwable = null
                    )
                }
                tracker.windowSecond = currentSec
                tracker.count = 1
                tracker.suppressed = 0
            } else {
                tracker.count++
                if (tracker.count > RATE_LIMIT_MAX_PER_SEC) {
                    tracker.suppressed++
                    return
                }
            }
        }

        val threadName = Thread.currentThread().name
        val category = inferCategory(tag)

        emitLogEntry(
            level = level,
            tag = tag,
            threadName = threadName,
            opId = opId,
            category = category,
            message = redactedMessage,
            throwable = tr
        )
    }

    private fun emitLogEntry(
        level: LogLevel,
        tag: String,
        threadName: String,
        opId: String?,
        category: LogCategory,
        message: String,
        throwable: Throwable?
    ) {
        val throwableText = throwable?.let { redact(buildStackTraceString(it)) }

        val entry = LogEntry(
            id = synchronized(this) { ++nextId },
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            threadName = threadName,
            opId = opId,
            category = category,
            message = message,
            throwableText = throwableText
        )

        // Android logcat mirror (safely caught for non-Android unit test environments)
        try {
            when (level) {
                LogLevel.VERBOSE -> Log.v(tag, entry.fullFormattedText)
                LogLevel.DEBUG -> Log.d(tag, entry.fullFormattedText)
                LogLevel.INFO -> Log.i(tag, entry.fullFormattedText)
                LogLevel.WARN -> Log.w(tag, entry.fullFormattedText, throwable)
                LogLevel.ERROR -> Log.e(tag, entry.fullFormattedText, throwable)
            }
        } catch (_: Throwable) {}

        // In-memory queue
        synchronized(_logs) {
            val current = _logs.value
            val updated = if (current.size >= MAX_IN_MEMORY_LOGS) {
                val dropCount = current.size - MAX_IN_MEMORY_LOGS + 1
                _droppedEvents.value = droppedCounter.addAndGet(dropCount.toLong())
                current.drop(dropCount) + entry
            } else {
                current + entry
            }
            _logs.value = updated
        }

        // File logging
        if (isFileLoggingEnabled && logDir != null) {
            scope.launch {
                appendToFileRotating(entry)
            }
        }
    }

    // --- Rotating File Storage (5 files x 2MB) ---
    private fun appendToFileRotating(entry: LogEntry) {
        try {
            val dir = logDir ?: return
            val file = File(dir, "lumen_$currentFileIndex.log")

            if (file.exists() && file.length() >= MAX_FILE_SIZE_BYTES) {
                currentFileIndex = (currentFileIndex + 1) % ROTATING_FILE_COUNT
                val nextFile = File(dir, "lumen_$currentFileIndex.log")
                if (nextFile.exists()) {
                    nextFile.delete()
                }
            }

            val targetFile = File(dir, "lumen_$currentFileIndex.log")
            FileWriter(targetFile, true).use { writer ->
                writer.appendLine(entry.fullFormattedText)
            }
        } catch (e: Exception) {
            Log.w("LumenLogger", "appendToFileRotating error: ${e.message}")
        }
    }

    // --- Lazy Logging API ---
    inline fun v(tag: String, opId: String? = null, tr: Throwable? = null, noinline msg: () -> String) {
        log(LogLevel.VERBOSE, tag, opId, tr, msg)
    }

    inline fun d(tag: String, opId: String? = null, tr: Throwable? = null, noinline msg: () -> String) {
        log(LogLevel.DEBUG, tag, opId, tr, msg)
    }

    inline fun i(tag: String, opId: String? = null, tr: Throwable? = null, noinline msg: () -> String) {
        log(LogLevel.INFO, tag, opId, tr, msg)
    }

    inline fun w(tag: String, opId: String? = null, tr: Throwable? = null, noinline msg: () -> String) {
        log(LogLevel.WARN, tag, opId, tr, msg)
    }

    inline fun e(tag: String, opId: String? = null, tr: Throwable? = null, noinline msg: () -> String) {
        log(LogLevel.ERROR, tag, opId, tr, msg)
    }

    // String overloads
    fun v(tag: String, message: String, tr: Throwable?) = log(LogLevel.VERBOSE, tag, null, tr) { message }
    fun d(tag: String, message: String, tr: Throwable?) = log(LogLevel.DEBUG, tag, null, tr) { message }
    fun i(tag: String, message: String, tr: Throwable?) = log(LogLevel.INFO, tag, null, tr) { message }
    fun w(tag: String, message: String, tr: Throwable?) = log(LogLevel.WARN, tag, null, tr) { message }
    fun e(tag: String, message: String, tr: Throwable?) = log(LogLevel.ERROR, tag, null, tr) { message }

    fun v(tag: String, message: String, opId: String? = null, tr: Throwable? = null) {
        log(LogLevel.VERBOSE, tag, opId, tr) { message }
    }

    fun d(tag: String, message: String, opId: String? = null, tr: Throwable? = null) {
        log(LogLevel.DEBUG, tag, opId, tr) { message }
    }

    fun i(tag: String, message: String, opId: String? = null, tr: Throwable? = null) {
        log(LogLevel.INFO, tag, opId, tr) { message }
    }

    fun w(tag: String, message: String, opId: String? = null, tr: Throwable? = null) {
        log(LogLevel.WARN, tag, opId, tr) { message }
    }

    fun e(tag: String, message: String, opId: String? = null, tr: Throwable? = null) {
        log(LogLevel.ERROR, tag, opId, tr) { message }
    }

    // --- Timed Operation Helper ---
    inline fun <T> timed(tag: String, operation: String, opId: String? = null, block: () -> T): T {
        d(tag, opId = opId) { "[START] $operation" }
        val start = System.currentTimeMillis()
        return try {
            val result = block()
            val duration = System.currentTimeMillis() - start
            d(tag, opId = opId) { "[END] $operation completed in ${duration}ms" }
            result
        } catch (t: Throwable) {
            val duration = System.currentTimeMillis() - start
            e(tag, opId = opId, tr = t) { "[FAILED] $operation failed after ${duration}ms: ${t.message}" }
            throw t
        }
    }

    suspend inline fun <T> timedSuspend(tag: String, operation: String, opId: String? = null, crossinline block: suspend () -> T): T {
        d(tag, opId = opId) { "[START] $operation" }
        val start = System.currentTimeMillis()
        return try {
            val result = block()
            val duration = System.currentTimeMillis() - start
            d(tag, opId = opId) { "[END] $operation completed in ${duration}ms" }
            result
        } catch (t: Throwable) {
            val duration = System.currentTimeMillis() - start
            e(tag, opId = opId, tr = t) { "[FAILED] $operation failed after ${duration}ms: ${t.message}" }
            throw t
        }
    }

    // --- Animation Logging Helper ---
    fun animStart(
        name: String,
        target: String,
        property: String,
        from: Any?,
        to: Any?,
        spec: String,
        trigger: String,
        token: String? = null
    ) {
        d("Anim") {
            "[ANIM START] name='$name' target='$target' prop='$property' from='$from' -> to='$to' spec='$spec' trigger='$trigger'${token?.let { " token='$it'" } ?: ""}"
        }
        if ((property == "alpha" || property == "color") && spec.contains("dampingRatio", ignoreCase = true)) {
            w("Anim") {
                "MotionScheme Warning: Effects property '$property' on '$target' may overshoot with spring spec '$spec'. Use non-overshooting spring/effects token."
            }
        }
    }

    fun animEnd(
        name: String,
        target: String,
        durationMs: Long,
        finalValue: Any?,
        completed: Boolean,
        interruptedBy: String? = null
    ) {
        d("Anim") {
            "[ANIM END] name='$name' target='$target' duration=${durationMs}ms finalValue='$finalValue' status=${if (completed) "COMPLETED" else "INTERRUPTED by '$interruptedBy'"}"
        }
    }

    // --- Global Uncaught Exception Handler ---
    private val isCrashHandlerInstalled = AtomicBoolean(false)
    fun installCrashHandler() {
        if (isCrashHandlerInstalled.compareAndSet(false, true)) {
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    e("Crash", opId = "CRASH", tr = throwable) {
                        "FATAL UNCAUGHT EXCEPTION on thread '${thread.name}' (id=${thread.id}): ${throwable.message}"
                    }
                    // Synchronously write to crash log file
                    logDir?.let { dir ->
                        val crashFile = File(dir, "lumen_crash.log")
                        FileWriter(crashFile, true).use { writer ->
                            writer.appendLine("==========================================")
                            writer.appendLine("LUMEN CRASH: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
                            writer.appendLine("Thread: ${thread.name} (id=${thread.id})")
                            writer.appendLine("Message: ${throwable.message}")
                            writer.appendLine(buildStackTraceString(throwable))
                            writer.appendLine("==========================================")
                        }
                    }
                } catch (ignored: Exception) {}
                originalHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    // --- Periodic 30s Snapshot (Verbose Mode) ---
    private fun startPeriodicSnapshot() {
        snapshotJob?.cancel()
        snapshotJob = scope.launch {
            while (isActive) {
                delay(30_000L)
                if (isVerboseEnabled) {
                    takeSystemSnapshot()
                }
            }
        }
    }

    private fun takeSystemSnapshot() {
        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)
        val activeThreads = Thread.activeCount()

        val ctx = appContext
        var netType = "Unknown"
        var batteryLevel = -1
        var isCharging = false

        if (ctx != null) {
            try {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val net = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(net)
                netType = when {
                    caps == null -> "None"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Other"
                }

                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                batteryLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                isCharging = DeviceConditions.isDeviceCharging(ctx)
            } catch (_: Exception) {}
        }

        d("Perf") {
            "[SNAPSHOT 30s] heap_used=${usedMemMb}MB max_heap=${maxMemMb}MB threads=$activeThreads network=$netType battery=$batteryLevel% charging=$isCharging events_dropped=${_droppedEvents.value}"
        }
    }

    // --- Stacktrace Helper with Cause Chain ---
    fun buildStackTraceString(t: Throwable): String {
        val sb = StringBuilder()
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < 6) {
            if (depth > 0) sb.append("\nCaused by: ")
            sb.append(current::class.java.name).append(": ").append(current.message).append("\n")
            for (elem in current.stackTrace.take(20)) {
                sb.append("    at ").append(elem.toString()).append("\n")
            }
            current = current.cause
            depth++
        }
        return sb.toString()
    }

    // --- Export Logs to Shared File ---
    fun exportLogs(context: Context, extraMetadata: Map<String, String> = emptyMap()) {
        scope.launch {
            try {
                val exportDir = File(context.cacheDir, "logs").apply { mkdirs() }
                val exportFile = File(exportDir, "lumen_debug_export.txt")

                val header = buildExportHeader(context, extraMetadata)
                val logsCopy = synchronized(_logs) { _logs.value }

                FileWriter(exportFile, false).use { writer ->
                    writer.write(header)
                    writer.write("\n=================== LOG ENTRIES (${logsCopy.size}) ===================\n")
                    for (entry in logsCopy) {
                        writer.appendLine(entry.fullFormattedText)
                    }
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Lumen Debug Logs - ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooser = Intent.createChooser(intent, "Export Lumen Debug Logs").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                i("LumenLogger") { "Exported ${logsCopy.size} logs to $uri" }
            } catch (e: Exception) {
                e("LumenLogger", tr = e) { "Failed to export logs: ${e.message}" }
            }
        }
    }

    fun buildExportHeader(context: Context, extraMetadata: Map<String, String> = emptyMap()): String {
        val pm = context.packageManager
        val pInfo = try { pm.getPackageInfo(context.packageName, 0) } catch (_: Exception) { null }
        val versionName = pInfo?.versionName ?: "1.0.0-dev"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo?.longVersionCode ?: 1L
        } else {
            @Suppress("DEPRECATION")
            pInfo?.versionCode?.toLong() ?: 1L
        }

        return buildString {
            appendLine("==========================================")
            appendLine("LUMEN DEBUG LOGS EXPORT")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            appendLine("App Version: $versionName (build $versionCode)")
            appendLine("Build Type: debug")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Locale: ${Locale.getDefault()}")
            appendLine("Events Dropped: ${_droppedEvents.value}")
            if (extraMetadata.isNotEmpty()) {
                appendLine("--- Context & Settings ---")
                extraMetadata.forEach { (k, v) ->
                    appendLine("$k: $v")
                }
            }
            appendLine("==========================================")
        }
    }

    fun clearLogs(context: Context) {
        synchronized(_logs) {
            _logs.value = emptyList()
            _droppedEvents.value = 0L
            droppedCounter.set(0L)
        }
        scope.launch {
            try {
                logDir?.listFiles()?.forEach { if (it.name.startsWith("lumen_")) it.delete() }
            } catch (_: Exception) {}
        }
        i("LumenLogger") { "Cleared in-memory logs and rotating log files" }
    }
}
