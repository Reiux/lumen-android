package com.reiux.lumen.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Material 3 Expressive Linear Wavy Progress Indicator.
 * In M3 Expressive, the active progress is a flowing sine wave.
 * The inactive track is a clean straight line that only covers the remaining portion,
 * separated by a small gap, ensuring the wave never overlaps a straight line.
 */
@Composable
fun ExpressiveWavyLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    strokeWidth: Dp = 4.dp,
    waveAmplitude: Dp = 3.5.dp,
    wavePeriod: Dp = 26.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavyProgressPhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phaseAnimation",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(strokeWidth * 3 + waveAmplitude * 2)
    ) {
        val width = size.width
        val centerY = size.height / 2f
        val strokePx = strokeWidth.toPx()
        val ampPx = waveAmplitude.toPx()
        val periodPx = wavePeriod.toPx().coerceAtLeast(10f)
        val gapPx = 4.dp.toPx()

        val startX = strokePx / 2f
        val endX = width - strokePx / 2f
        val usableWidth = (endX - startX).coerceAtLeast(0f)
        val clampedProgress = progress.coerceIn(0f, 1f)
        val activeEndX = startX + usableWidth * clampedProgress

        // 1. Draw inactive straight track ONLY for the remaining (unfilled) portion
        val remainingTrackStart = if (clampedProgress > 0f) activeEndX + gapPx else startX
        if (remainingTrackStart < endX) {
            drawLine(
                color = trackColor,
                start = Offset(remainingTrackStart, centerY),
                end = Offset(endX, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
        }

        // 2. Draw active wavy progress without any overlapping track line underneath
        val activeSegmentWidth = activeEndX - startX
        if (activeSegmentWidth > 1f) {
            val path = Path()
            val stepPx = 2f
            var x = startX
            var first = true

            while (x <= activeEndX) {
                // Smoothly taper wave amplitude to 0 near the endpoints so they seat flat on centerY
                val distFromStart = x - startX
                val distFromEnd = activeEndX - x
                val taper = when {
                    distFromStart < 14f -> (distFromStart / 14f).coerceIn(0f, 1f)
                    distFromEnd < 14f -> (distFromEnd / 14f).coerceIn(0f, 1f)
                    else -> 1f
                }
                val y = centerY + sin(((x - startX) / periodPx) * 2 * PI.toFloat() + phase) * ampPx * taper
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += stepPx
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun ExpressiveWavyIndeterminateProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
    waveAmplitude: Dp = 3.5.dp,
    wavePeriod: Dp = 26.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavyIndeterminate")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phaseAnimation",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(strokeWidth * 3 + waveAmplitude * 2)
    ) {
        val width = size.width
        val centerY = size.height / 2f
        val strokePx = strokeWidth.toPx()
        val ampPx = waveAmplitude.toPx()
        val periodPx = wavePeriod.toPx().coerceAtLeast(10f)

        val path = Path()
        val stepPx = 2f
        var x = strokePx / 2f
        var first = true
        val endX = width - strokePx / 2f

        while (x <= endX) {
            val distFromStart = x - (strokePx / 2f)
            val distFromEnd = endX - x
            val taper = when {
                distFromStart < 14f -> (distFromStart / 14f).coerceIn(0f, 1f)
                distFromEnd < 14f -> (distFromEnd / 14f).coerceIn(0f, 1f)
                else -> 1f
            }
            val y = centerY + sin((x / periodPx) * 2 * PI.toFloat() + phase) * ampPx * taper
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            x += stepPx
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }
}
