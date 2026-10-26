package com.reiux.lumen.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin

@Composable
fun FlyingBatOverlay(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!enabled) return

    val infiniteTransition = rememberInfiniteTransition(label = "batFly")
    val flyProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flyAnimation"
    )

    val flapProgress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(220, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flapAnimation"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val x = (flyProgress * (w + 100f)) - 50f
        val y = (h * 0.15f) + sin(flyProgress * 3 * Math.PI).toFloat() * 25f

        val batColor = Color(0x991E1228)

        rotate(degrees = sin(flyProgress * 3 * Math.PI).toFloat() * 12f, pivot = Offset(x, y)) {
            val wingFlap = flapProgress * 8f
            val path = Path().apply {
                moveTo(x, y)
                // Left Wing
                cubicTo(x - 12f, y - 10f + wingFlap, x - 24f, y - 4f + wingFlap, x - 32f, y - 14f + wingFlap)
                cubicTo(x - 24f, y + 2f, x - 16f, y + 6f, x, y + 2f)
                // Right Wing
                cubicTo(x + 16f, y + 6f, x + 24f, y + 2f, x + 32f, y - 14f + wingFlap)
                cubicTo(x + 24f, y - 4f + wingFlap, x + 12f, y - 10f + wingFlap, x, y)
                close()
            }
            drawPath(path = path, color = batColor)
        }
    }
}
