package com.reiux.lumen.ui.components

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

val LocalBlurEffectsEnabled = compositionLocalOf { true }

/**
 * Applies native hardware Gaussian blur behind Dialogs and ModalBottomSheets on Android 12+ (API 31+).
 */
@Composable
fun DialogBlurEffect(
    enabled: Boolean = LocalBlurEffectsEnabled.current,
    radiusPx: Int = 45
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    SideEffect {
        try {
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window
                ?: (view.context as? DialogWindowProvider)?.window
                ?: (generateSequence(view.parent) { (it as? View)?.parent }
                    .mapNotNull { it as? DialogWindowProvider }
                    .firstOrNull()?.window)
                ?: (view.context as? android.app.Dialog)?.window

            if (dialogWindow != null) {
                if (enabled) {
                    dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    dialogWindow.attributes = dialogWindow.attributes.apply {
                        blurBehindRadius = radiusPx
                    }
                } else {
                    dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    dialogWindow.attributes = dialogWindow.attributes.apply {
                        blurBehindRadius = 0
                    }
                }
            }

            // Also support ModalBottomSheet / PopupLayouts whose root view is added to WindowManager
            val root = view.rootView
            val rootParams = root?.layoutParams as? WindowManager.LayoutParams
            if (rootParams != null) {
                val wm = root.context.getSystemService(android.content.Context.WINDOW_SERVICE) as? WindowManager
                if (enabled) {
                    rootParams.flags = rootParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    rootParams.blurBehindRadius = radiusPx
                } else {
                    rootParams.flags = rootParams.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                    rootParams.blurBehindRadius = 0
                }
                wm?.updateViewLayout(root, rootParams)
            }
        } catch (_: Throwable) {}
    }
}

/**
 * Wraps full-screen overlay screens (such as Album Manager and About screen) to provide
 * the Android Predictive Back gesture with a centered card-switch animation.
 *
 * During interactive back swipe:
 * - Shrinks deeper down to ~0.68 at the screen center (leaving generous space around the card)
 * - Retains smooth curved corners (32dp) matching the device bezel
 *
 * When committed:
 * - Card drops back into depth (scale 0.50, alpha 0), allowing the destination screen underneath to surface
 *
 * When cancelled:
 * - Springs smoothly back to full-screen resting state (scale=1, alpha=1)
 */
@Composable
fun PredictiveBackScreenContainer(
    enabled: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onProgressUpdate: (Float) -> Unit = {},
    content: @Composable () -> Unit
) {
    val cornerRadiusDp = 32.dp

    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    val backdropAlpha = remember { Animatable(0f) }

    val reducedMotion = rememberReducedMotion()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    LaunchedEffect(enabled) {
        if (enabled) {
            scale.snapTo(1f)
            alpha.snapTo(1f)
            backdropAlpha.snapTo(0f)
            onProgressUpdate(0f)
        }
    }

    PredictiveBackHandler(enabled = enabled) { progressFlow: Flow<BackEventCompat> ->
        try {
            progressFlow.collect { backEvent ->
                val progress = backEvent.progress // 0.0 to 1.0

                // Shrinks deeper down to ~0.68 at the center
                val targetScale = 1f - (progress * 0.32f)

                scale.snapTo(targetScale)
                alpha.snapTo(1f)
                backdropAlpha.snapTo(progress * 0.68f)
                onProgressUpdate(progress)
            }

            // Card-switch release: current card drops deep into 0.50 scale depth while destination surfaces
            coroutineScope {
                if (reducedMotion) {
                    launch { alpha.animateTo(0f, fastEffectsSpec) }
                    launch { backdropAlpha.animateTo(0f, fastEffectsSpec) }
                } else {
                    launch { scale.animateTo(0.50f, spatialSpec) }
                    launch { alpha.animateTo(0f, effectsSpec) }
                    launch { backdropAlpha.animateTo(0f, effectsSpec) }
                }
            }
            onProgressUpdate(0f)
            onDismiss()
        } catch (e: CancellationException) {
            // User cancelled gesture: spring back smoothly to resting position
            coroutineScope {
                if (reducedMotion) {
                    launch { scale.snapTo(1f) }
                    launch { alpha.animateTo(1f, fastEffectsSpec) }
                    launch { backdropAlpha.animateTo(0f, fastEffectsSpec) }
                } else {
                    launch { scale.animateTo(1f, spatialSpec) }
                    launch { alpha.animateTo(1f, effectsSpec) }
                    launch { backdropAlpha.animateTo(0f, effectsSpec) }
                }
            }
            onProgressUpdate(0f)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Backdrop dimming behind the active window
        if (backdropAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = backdropAlpha.value))
            )
        }

        // Active card going back with depth elevation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = 0f
                    translationY = 0f
                    this.alpha = alpha.value
                    shadowElevation = if (scale.value < 0.999f) 16.dp.toPx() else 0f
                    clip = true
                    shape = RoundedCornerShape(cornerRadiusDp)
                }
                .background(MaterialTheme.colorScheme.surface)
        ) {
            content()
        }
    }
}

/**
 * Stateful controller for dialog predictive back and M3E entrance/snappy exit animations.
 */
class PredictiveBackDialogState(
    private val onDismiss: () -> Unit,
    initialScale: Float = 0.88f
) {
    val scale = Animatable(initialScale)
    val alpha = Animatable(1f)

    @Composable
    fun StartEntrance() {
        val reducedMotion = rememberReducedMotion()
        val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

        LaunchedEffect(Unit) {
            if (reducedMotion) {
                scale.snapTo(1f)
                alpha.snapTo(0f)
                alpha.animateTo(1f, fastEffectsSpec)
            } else if (scale.value != 1f) {
                scale.animateTo(1f, spatialSpec)
            }
        }
    }

    @Composable
    fun BackHandler(enabled: Boolean = true) {
        val view = LocalView.current
        val blurEnabled = LocalBlurEffectsEnabled.current
        val reducedMotion = rememberReducedMotion()
        val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

        fun findDialogOwner(): OnBackPressedDispatcherOwner? {
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window
                ?: (view.context as? DialogWindowProvider)?.window
                ?: (generateSequence(view.parent) { (it as? View)?.parent }
                    .mapNotNull { it as? DialogWindowProvider }
                    .firstOrNull()?.window)
                ?: (view.context as? android.app.Dialog)?.window

            return (dialogWindow?.callback as? OnBackPressedDispatcherOwner)
                ?: (view.context as? OnBackPressedDispatcherOwner)
                ?: view.findViewTreeOnBackPressedDispatcherOwner()
        }

        var targetOwner by remember(view) {
            mutableStateOf(findDialogOwner())
        }

        DisposableEffect(view) {
            findDialogOwner()?.let { targetOwner = it }
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    findDialogOwner()?.let { targetOwner = it }
                }
                override fun onViewDetachedFromWindow(v: View) {}
            }
            view.addOnAttachStateChangeListener(listener)
            onDispose {
                view.removeOnAttachStateChangeListener(listener)
            }
        }

        val activeOwner = targetOwner ?: findDialogOwner() ?: LocalOnBackPressedDispatcherOwner.current

        if (activeOwner != null) {
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides activeOwner) {
                PredictiveBackHandler(enabled = enabled) { progressFlow: Flow<BackEventCompat> ->
                    try {
                        progressFlow.collect { backEvent ->
                            val progress = backEvent.progress
                            // Inward scale shrink while keeping dialog solid during swipe
                            scale.snapTo(1f - (progress * 0.20f))
                            alpha.snapTo(1f)
                        }

                        // Instantly clear the window blur flag as soon as the swipe commits
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val dialogWindow = (view.parent as? DialogWindowProvider)?.window
                                    ?: (view.context as? DialogWindowProvider)?.window
                                    ?: (generateSequence(view.parent) { (it as? View)?.parent }
                                        .mapNotNull { it as? DialogWindowProvider }
                                        .firstOrNull()?.window)
                                    ?: (view.context as? android.app.Dialog)?.window
                                dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                                dialogWindow?.attributes = dialogWindow?.attributes?.apply {
                                    blurBehindRadius = 0
                                }
                            }
                        } catch (_: Throwable) {}

                        // Expressive spatial scale + critically damped opacity dismiss
                        coroutineScope {
                            if (reducedMotion) {
                                launch { alpha.animateTo(0f, fastEffectsSpec) }
                            } else {
                                launch { scale.animateTo(0.70f, spatialSpec) }
                                launch { alpha.animateTo(0f, effectsSpec) }
                            }
                        }
                        onDismiss()

                        // Safety net: if onDismiss() was guarded and kept the dialog mounted,
                        // restore visibility and blur instead of leaving an invisible blocker.
                        kotlinx.coroutines.delay(50)
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEnabled) {
                                val dialogWindow = (view.parent as? DialogWindowProvider)?.window
                                    ?: (view.context as? DialogWindowProvider)?.window
                                    ?: (generateSequence(view.parent) { (it as? View)?.parent }
                                        .mapNotNull { it as? DialogWindowProvider }
                                        .firstOrNull()?.window)
                                    ?: (view.context as? android.app.Dialog)?.window
                                dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                                dialogWindow?.attributes = dialogWindow?.attributes?.apply {
                                    blurBehindRadius = 45
                                }
                            }
                        } catch (_: Throwable) {}
                        coroutineScope {
                            if (reducedMotion) {
                                launch { scale.snapTo(1f) }
                                launch { alpha.animateTo(1f, fastEffectsSpec) }
                            } else {
                                launch { scale.animateTo(1f, spatialSpec) }
                                launch { alpha.animateTo(1f, effectsSpec) }
                            }
                        }
                    } catch (e: CancellationException) {
                        coroutineScope {
                            if (reducedMotion) {
                                launch { scale.snapTo(1f) }
                                launch { alpha.animateTo(1f, fastEffectsSpec) }
                            } else {
                                launch { scale.animateTo(1f, spatialSpec) }
                                launch { alpha.animateTo(1f, effectsSpec) }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Handler(enabled: Boolean = true) {
        StartEntrance()
        BackHandler(enabled = enabled)
    }

    val modifier: Modifier
        get() = Modifier.graphicsLayer {
            scaleX = this@PredictiveBackDialogState.scale.value
            scaleY = this@PredictiveBackDialogState.scale.value
            this.alpha = this@PredictiveBackDialogState.alpha.value
        }
}

enum class TabletopDialogPlacement {
    BOTTOM_HALF,
    TOP_HALF
}

/**
 * Drop-in replacement for Material 3 AlertDialog with original M3E spring entrance and Predictive Back support.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PredictiveAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier.widthIn(max = 560.dp).padding(horizontal = 24.dp),
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
    tabletopPlacement: TabletopDialogPlacement = TabletopDialogPlacement.BOTTOM_HALF,
    fillTabletopHalf: Boolean = false,
) {
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val reducedMotion = rememberReducedMotion()
    val foldPosture = LocalFoldPosture.current
    val shouldFillTabletop = foldPosture.isTabletop && fillTabletopHalf
    val state = remember { PredictiveBackDialogState({ currentOnDismiss() }, initialScale = if (reducedMotion) 1f else 0.88f) }
    state.StartEntrance()

    val safeProperties = remember(properties) {
        DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            securePolicy = properties.securePolicy,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = safeProperties
    ) {
        DialogBlurEffect()
        state.BackHandler(enabled = properties.dismissOnBackPress)

        val dialogSurface: @Composable (Modifier) -> Unit = { extraModifier ->
            Surface(
                modifier = modifier
                    .then(extraModifier)
                    .then(state.modifier)
                    .pointerInput(Unit) {
                        detectTapGestures { /* consume taps inside dialog card */ }
                    },
                shape = shape,
                color = containerColor,
                tonalElevation = tonalElevation
            ) {
                Column(
                    modifier = Modifier
                        .then(if (shouldFillTabletop) Modifier.fillMaxSize() else Modifier)
                        .padding(24.dp)
                ) {
                    icon?.let {
                        CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 16.dp)
                                    .align(Alignment.CenterHorizontally)
                            ) {
                                it()
                            }
                        }
                    }
                    title?.let {
                        CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                            val textStyle = MaterialTheme.typography.headlineSmall
                            ProvideTextStyle(value = textStyle) {
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 16.dp)
                                        .align(
                                            if (icon == null) Alignment.Start
                                            else Alignment.CenterHorizontally
                                        )
                                ) {
                                    it()
                                }
                            }
                        }
                    }
                    text?.let {
                        CompositionLocalProvider(LocalContentColor provides textContentColor) {
                            val textStyle = MaterialTheme.typography.bodyMedium
                            ProvideTextStyle(value = textStyle) {
                                Box(
                                    modifier = Modifier
                                        .weight(weight = 1f, fill = shouldFillTabletop)
                                        .padding(bottom = 24.dp)
                                        .align(Alignment.Start)
                                ) {
                                    it()
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier.align(Alignment.End)) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                            ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    dismissButton?.invoke()
                                    confirmButton()
                                }
                            }
                        }
                    }
                }
            }
        }

        if (foldPosture.isTabletop && tabletopPlacement == TabletopDialogPlacement.TOP_HALF) {
            val density = LocalDensity.current
            var containerTopInWindowPx by remember { mutableFloatStateOf(0f) }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(properties.dismissOnClickOutside) {
                        if (properties.dismissOnClickOutside) {
                            detectTapGestures {
                                currentOnDismiss()
                            }
                        }
                    }
            ) {
                val containerTopDp = with(density) { containerTopInWindowPx.toDp() }
                val topMaxHeight = foldPosture.tabletopTopPaneHeight(
                    containerTopInWindowDp = containerTopDp,
                    maxAvailableHeightDp = maxHeight,
                    bottomMarginDp = 8.dp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        )
                        .padding(top = 4.dp, start = 12.dp, end = 12.dp)
                        .onGloballyPositioned { coords ->
                            containerTopInWindowPx = coords.positionInWindow().y
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topMaxHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        dialogSurface(Modifier.heightIn(max = topMaxHeight))
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (foldPosture.isTabletop) {
                            Modifier
                                .padding(top = (foldPosture.hingeBottomDp + 8.dp).coerceAtLeast(0.dp))
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                                )
                                .padding(bottom = 8.dp)
                        } else {
                            Modifier
                                .safeDrawingPadding()
                                .padding(vertical = 12.dp)
                        }
                    )
                    .pointerInput(properties.dismissOnClickOutside) {
                        if (properties.dismissOnClickOutside) {
                            detectTapGestures {
                                currentOnDismiss()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                dialogSurface(Modifier)
            }
        }
    }
}

/**
 * Container for custom Dialog implementations (like GoogleSignInWebViewDialog and LogViewerDialog)
 * to provide M3E spring entrance, blur, and predictive back scaling and fading.
 */
@Composable
fun PredictiveBackDialogContainer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackPress: Boolean = true,
    constrainToTabletop: Boolean = true,
    content: @Composable () -> Unit
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val reducedMotion = rememberReducedMotion()
    val foldPosture = LocalFoldPosture.current
    val state = remember { PredictiveBackDialogState({ currentOnDismiss() }, initialScale = if (reducedMotion) 1f else 0.88f) }
    val applyTabletop = constrainToTabletop && foldPosture.isTabletop

    Box(
        modifier = modifier
            .then(
                if (applyTabletop) {
                    Modifier
                        .fillMaxSize()
                        .padding(top = (foldPosture.hingeBottomDp + 8.dp).coerceAtLeast(0.dp))
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        )
                        .padding(bottom = 8.dp)
                } else if (!foldPosture.isTabletop) {
                    Modifier.safeDrawingPadding()
                } else {
                    Modifier
                }
            )
            .then(state.modifier),
        contentAlignment = if (applyTabletop) Alignment.Center else Alignment.TopStart
    ) {
        DialogBlurEffect()
        state.Handler(enabled = dismissOnBackPress)
        content()
    }
}

/**
 * Legacy compatibility helper.
 */
@Composable
fun rememberPredictiveBackDialogModifier(
    enabled: Boolean = true,
    initialScale: Float = 0.88f,
    onDismiss: () -> Unit
): Modifier {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val reducedMotion = rememberReducedMotion()
    val state = remember { PredictiveBackDialogState({ currentOnDismiss() }, initialScale = if (reducedMotion) 1f else initialScale) }
    state.Handler(enabled = enabled)
    return state.modifier
}
