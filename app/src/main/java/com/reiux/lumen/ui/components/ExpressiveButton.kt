package com.reiux.lumen.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Latches the press state so that even ultra-fast taps (~20-40ms)
 * hold the pressed shape long enough (180ms) to visibly morph and recoil.
 */
@Composable
fun rememberExpressivePressed(
    interactionSource: InteractionSource,
    minDurationMillis: Long = 180L
): State<Boolean> {
    val pressedState = remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        var pressStart = 0L
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressStart = System.currentTimeMillis()
                    pressedState.value = true
                }
                is PressInteraction.Release -> {
                    val elapsed = System.currentTimeMillis() - pressStart
                    val remaining = minDurationMillis - elapsed
                    if (remaining > 0) {
                        delay(remaining)
                    }
                    pressedState.value = false
                }
                is PressInteraction.Cancel -> {
                    pressedState.value = false
                }
            }
        }
    }
    return pressedState
}

/**
 * Material 3 Expressive button with spring shape morphing:
 * Pill (28dp) at rest -> Squarer squircle (12dp) on press with recoil.
 */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val isGesturePressed by rememberExpressivePressed(interactionSource)
    val clickPulse = remember { mutableStateOf(false) }

    val isVisuallyPressed = isGesturePressed || clickPulse.value

    val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
        coroutineScope.launch {
            clickPulse.value = true
            delay(180)
            clickPulse.value = false
        }
        onClick()
    }

    val cornerRadius by animateDpAsState(
        targetValue = if (isVisuallyPressed) 12.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
        label = "buttonCornerRadius",
    )

    val scale by animateFloatAsState(
        targetValue = if (isVisuallyPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 800f),
        label = "buttonScale",
    )

    Surface(
        onClick = hapticClick,
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minWidth = 64.dp, minHeight = 44.dp),
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius),
        color = if (enabled) colors.containerColor else colors.disabledContainerColor,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
        border = border,
        interactionSource = interactionSource,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides (if (enabled) colors.contentColor else colors.disabledContentColor)
        ) {
            Row(
                modifier = Modifier.padding(contentPadding),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun ExpressiveTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    ExpressiveButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isGesturePressed by rememberExpressivePressed(interactionSource)
    val clickPulse = remember { mutableStateOf(false) }

    val isVisuallyPressed = isGesturePressed || clickPulse.value

    val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
        coroutineScope.launch {
            clickPulse.value = true
            delay(180)
            clickPulse.value = false
        }
        onClick()
    }

    val cornerRadius by animateDpAsState(
        targetValue = if (isVisuallyPressed) 10.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
        label = "iconButtonCornerRadius",
    )

    val scale by animateFloatAsState(
        targetValue = if (isVisuallyPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 800f),
        label = "iconButtonScale",
    )

    Surface(
        onClick = hapticClick,
        modifier = modifier
            .scale(scale)
            .size(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius),
        color = if (enabled) colors.containerColor else colors.disabledContainerColor,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
        interactionSource = interactionSource,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp)
        ) {
            content()
        }
    }
}
