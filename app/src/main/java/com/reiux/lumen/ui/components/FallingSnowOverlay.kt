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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

private data class Snowflake(
    val startX: Float,
    val speed: Float,
    val radius: Float,
    val alpha: Float,
    val swayAmplitude: Float
)

@Composable
fun FallingSnowOverlay(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!enabled) return

    val infiniteTransition = rememberInfiniteTransition(label = "snowFall")
    val fallProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fallAnimation"
    )

    val snowflakes = remember {
        List(30) {
            Snowflake(
                startX = Random.nextFloat(),
                speed = Random.nextFloat() * 0.5f + 0.7f,
                radius = Random.nextFloat() * 3f + 2f,
                alpha = Random.nextFloat() * 0.5f + 0.4f,
                swayAmplitude = Random.nextFloat() * 20f + 10f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        snowflakes.forEach { flake ->
            val y = ((fallProgress * flake.speed * h * 1.5f) % (h + 40f)) - 20f
            val x = (flake.startX * w) + sin((y / h) * 4 * Math.PI).toFloat() * flake.swayAmplitude
            drawCircle(
                color = Color.White.copy(alpha = flake.alpha),
                radius = flake.radius,
                center = Offset(x, y)
            )
        }
    }
}
