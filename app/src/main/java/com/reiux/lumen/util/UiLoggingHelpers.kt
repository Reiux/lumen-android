package com.reiux.lumen.util

import android.view.Choreographer
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single-line modifier helper to log interaction events (taps, presses, long presses)
 * Example: Modifier.logInteractions("BackupButton")
 */
fun Modifier.logInteractions(
    componentName: String,
    tag: String = "Interaction"
): Modifier = this.pointerInput(componentName) {
    detectTapGestures(
        onPress = { offset ->
            LumenLogger.d(tag) { "[PRESS] component='$componentName' offset=(${offset.x.toInt()}, ${offset.y.toInt()})" }
            val success = tryAwaitRelease()
            LumenLogger.d(tag) {
                if (success) "[RELEASE] component='$componentName'"
                else "[CANCEL] component='$componentName'"
            }
        },
        onTap = { offset ->
            LumenLogger.i(tag) { "[TAP] component='$componentName' offset=(${offset.x.toInt()}, ${offset.y.toInt()})" }
        },
        onLongPress = { offset ->
            LumenLogger.i(tag) { "[LONG_PRESS] component='$componentName' offset=(${offset.x.toInt()}, ${offset.y.toInt()})" }
        }
    )
}

/**
 * Tracks InteractionSource state changes (press, focus, hover, drag) as discrete state transitions.
 */
@Composable
fun LogInteractionSource(componentName: String, interactionSource: InteractionSource) {
    LaunchedEffect(interactionSource, componentName) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> LumenLogger.d("Interaction") { "[PRESS_DOWN] component='$componentName'" }
                is PressInteraction.Release -> LumenLogger.d("Interaction") { "[PRESS_UP] component='$componentName'" }
                is PressInteraction.Cancel -> LumenLogger.d("Interaction") { "[PRESS_CANCEL] component='$componentName'" }
                is FocusInteraction.Focus -> LumenLogger.d("Interaction") { "[FOCUS_GAINED] component='$componentName'" }
                is FocusInteraction.Unfocus -> LumenLogger.d("Interaction") { "[FOCUS_LOST] component='$componentName'" }
                is HoverInteraction.Enter -> LumenLogger.v("Interaction") { "[HOVER_ENTER] component='$componentName'" }
                is HoverInteraction.Exit -> LumenLogger.v("Interaction") { "[HOVER_EXIT] component='$componentName'" }
                is DragInteraction.Start -> LumenLogger.d("Interaction") { "[DRAG_START] component='$componentName'" }
                is DragInteraction.Stop -> LumenLogger.d("Interaction") { "[DRAG_STOP] component='$componentName'" }
                is DragInteraction.Cancel -> LumenLogger.d("Interaction") { "[DRAG_CANCEL] component='$componentName'" }
            }
        }
    }
}

/**
 * Recomposition tracker reported every 2 seconds only if > 0.
 * Flags recomposition counts > 30 in 2s as WARN (likely an unintentional loop / recomposition bug).
 */
@Composable
fun TrackRecompositions(screenOrComponentName: String) {
    val count = remember { AtomicInteger(0) }
    count.incrementAndGet()

    LaunchedEffect(screenOrComponentName) {
        while (isActive) {
            delay(2000L)
            val currentCount = count.getAndSet(0)
            if (currentCount > 0) {
                if (currentCount > 30) {
                    LumenLogger.w("Perf") {
                        "High Recomposition Warning: '$screenOrComponentName' count=$currentCount/2s (likely a bug or unnecessary state churn)"
                    }
                } else {
                    LumenLogger.v("Perf") {
                        "Recomp '$screenOrComponentName' count=$currentCount/2s"
                    }
                }
            }
        }
    }
}

/**
 * Logs screen enter and exit lifecycle events.
 */
@Composable
fun LogScreenLifecycle(screenName: String, args: String = "") {
    DisposableEffect(screenName) {
        LumenLogger.i("UI") { "[SCREEN ENTER] name='$screenName'${if (args.isNotEmpty()) " args='$args'" else ""}" }
        onDispose {
            LumenLogger.i("UI") { "[SCREEN EXIT] name='$screenName'" }
        }
    }
}

/**
 * Rolling Choreographer performance frame monitor.
 * When activeAnimations has items, tracks frame times over a 1-second window:
 * Logs: frames, janky frames (> 16ms), worst frame ms.
 * Logs a WARN if any frame takes > 32ms.
 */
object FrameRatePerfMonitor {
    private val activeAnimations = CopyOnWriteArraySet<String>()
    private var isRunning = false
    private var lastFrameTimeNanos = 0L
    private var windowStartNanos = 0L
    private var totalFrames = 0
    private var jankyFrames = 0
    private var worstFrameMs = 0.0

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            if (lastFrameTimeNanos != 0L) {
                val frameDurationMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000.0
                totalFrames++
                if (frameDurationMs > 16.6) {
                    jankyFrames++
                }
                if (frameDurationMs > worstFrameMs) {
                    worstFrameMs = frameDurationMs
                }

                if (frameDurationMs > 32.0) {
                    LumenLogger.w("Perf") {
                        "[PERF SLOW FRAME] frameDuration=${String.format("%.1f", frameDurationMs)}ms (>32ms) activeAnimations=$activeAnimations"
                    }
                }

                val windowDurationMs = (frameTimeNanos - windowStartNanos) / 1_000_000.0
                if (windowDurationMs >= 1000.0) {
                    if (activeAnimations.isNotEmpty() && totalFrames > 0) {
                        val fps = (totalFrames * 1000.0) / windowDurationMs
                        LumenLogger.d("Perf") {
                            "[PERF 1s SAMPLE] fps=${String.format("%.1f", fps)} frames=$totalFrames janky=$jankyFrames worst=${String.format("%.1f", worstFrameMs)}ms activeAnimations=$activeAnimations"
                        }
                    }
                    totalFrames = 0
                    jankyFrames = 0
                    worstFrameMs = 0.0
                    windowStartNanos = frameTimeNanos
                }
            } else {
                windowStartNanos = frameTimeNanos
            }

            lastFrameTimeNanos = frameTimeNanos
            if (isRunning) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun startAnimation(name: String) {
        activeAnimations.add(name)
        if (!isRunning) {
            isRunning = true
            lastFrameTimeNanos = 0L
            windowStartNanos = 0L
            totalFrames = 0
            jankyFrames = 0
            worstFrameMs = 0.0
            try {
                Choreographer.getInstance().postFrameCallback(frameCallback)
            } catch (_: Exception) {}
        }
    }

    fun stopAnimation(name: String) {
        activeAnimations.remove(name)
        if (activeAnimations.isEmpty()) {
            isRunning = false
        }
    }
}

/**
 * UI State Snapshot generator
 * Captures current UI tree / state hierarchy into a structured, human-readable format under 300 lines.
 */
object UiSnapshotDumper {
    private var lastSnapshotString: String = "No snapshot taken yet."

    fun getLatestSnapshot(): String = lastSnapshotString

    fun dump(): String {
        val snapshot = buildString {
            appendLine("=== UI STATE SNAPSHOT ===")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
            appendLine("Status: Live snapshot requested from Log Viewer")
            appendLine("Memory: ${(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)}MB used / ${Runtime.getRuntime().maxMemory() / (1024 * 1024)}MB max")
            appendLine("Threads: ${Thread.activeCount()}")
            appendLine(lastSnapshotString)
            appendLine("=== END SNAPSHOT ===")
        }
        LumenLogger.i("UI") { snapshot }
        return snapshot
    }

    fun dumpCurrentUi(
        activeDestination: String,
        isLandscape: Boolean,
        windowWidthDp: Int,
        windowHeightDp: Int,
        sizeClass: String,
        accountsCount: Int,
        activeAccount: String?,
        backupQueuePending: Int,
        backupQueueActive: Int,
        backupQueueCompleted: Int,
        isUploading: Boolean,
        conditionNotice: String?,
        colorPreset: String,
        autoBackup: Boolean,
        wifiOnly: Boolean,
        chargingOnly: Boolean,
        storageSaver: Boolean,
        threads: Int
    ): String {
        val tree = buildString {
            appendLine("=== UI STATE SNAPSHOT ===")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
            appendLine("Window: width=${windowWidthDp}dp height=${windowHeightDp}dp class=$sizeClass orientation=${if (isLandscape) "Landscape" else "Portrait"}")
            appendLine("Navigation: activeTab='$activeDestination' layoutMode=${if (isLandscape || windowWidthDp >= 600) "NavigationRail" else "BottomBar"}")
            appendLine("Theme: preset='$colorPreset'")
            appendLine("Account: selected='${activeAccount?.let { LumenLogger.redact(it) } ?: "None"}' totalAccounts=$accountsCount")
            appendLine("Backup Queue: pending=$backupQueuePending active=$backupQueueActive completed=$backupQueueCompleted isUploading=$isUploading")
            appendLine("Condition Banner: ${conditionNotice ?: "None (All conditions met)"}")
            appendLine("Settings: autoBackup=$autoBackup wifiOnly=$wifiOnly chargingOnly=$chargingOnly storageSaver=$storageSaver threads=$threads")
            appendLine("Tree Hierarchy:")
            appendLine("  [Scaffold/Container]")
            if (isLandscape || windowWidthDp >= 600) {
                appendLine("    |- [ExpressiveNavRail] (width=96dp, visible=true, enabled=true)")
                appendLine("    |    |- Items: [Backup, Folders, Accounts, Settings] selected='$activeDestination'")
            } else {
                appendLine("    |- [ExpressiveNavBar] (height=80dp, visible=true, enabled=true)")
                appendLine("    |    |- Items: [Backup, Folders, Accounts, Settings] selected='$activeDestination'")
            }
            appendLine("    |- [Screen: $activeDestination] (bounds=match_parent)")
            when (activeDestination.uppercase()) {
                "BACKUP" -> {
                    appendLine("    |    |- [HeroStatusCard] (state=${if (isUploading) "Uploading" else "Idle"}, conditionNotice='$conditionNotice')")
                    appendLine("    |    |- [ActionRow] Buttons: ['Sync Folders', 'Pick Photos & Videos']")
                    appendLine("    |    |- [QueueList] itemsShowing=${backupQueuePending + backupQueueActive}")
                }
                "FOLDERS" -> {
                    appendLine("    |    |- [FoldersList] autoBackupEnabled=$autoBackup")
                    appendLine("    |    |- [AddFolderButton] (height=48dp, enabled=true)")
                }
                "ACCOUNTS" -> {
                    appendLine("    |    |- [AccountsList] count=$accountsCount active='$activeAccount'")
                    appendLine("    |    |- [AddAccountButton] (height=48dp, enabled=true)")
                }
                "SETTINGS" -> {
                    appendLine("    |    |- [SettingsCard] Toggles: [AutoBackup, WifiOnly, ChargingOnly, StorageSaver, Quota, Threads, ColorPreset, Logs]")
                    appendLine("    |    |- [AboutLumenCard] (target=AboutScreen)")
                }
            }
            appendLine("=== END SNAPSHOT ===")
        }
        lastSnapshotString = tree
        LumenLogger.i("UI") { tree }
        return tree
    }
}
