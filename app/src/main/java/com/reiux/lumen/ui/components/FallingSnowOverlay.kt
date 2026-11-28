package com.reiux.lumen.ui.components

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private class SnowflakeState(
    var xFraction: Float,
    var yFraction: Float,
    var speedPerSecond: Float,
    var radiusDp: Float,
    var baseAlpha: Float,
    var swayAmplitudeDp: Float,
    var swaySpeed: Float,
    var swayPhase: Float
)

private fun randomSnowflake(random: Random, initialYFraction: Float): SnowflakeState {
    // 3 subtle depth tiers: 0 = distant/small/slow, 1 = mid, 2 = foreground/larger/faster
    val tier = when (val roll = random.nextFloat()) {
        in 0f..0.45f -> 0
        in 0.45f..0.82f -> 1
        else -> 2
    }
    val (speed, radiusDp, alpha, swayAmp) = when (tier) {
        0 -> Quad(
            random.nextFloat() * 0.035f + 0.045f,
            random.nextFloat() * 0.7f + 1.4f,
            random.nextFloat() * 0.20f + 0.25f,
            random.nextFloat() * 6f + 6f
        )
        1 -> Quad(
            random.nextFloat() * 0.045f + 0.075f,
            random.nextFloat() * 0.9f + 2.1f,
            random.nextFloat() * 0.22f + 0.42f,
            random.nextFloat() * 8f + 10f
        )
        else -> Quad(
            random.nextFloat() * 0.055f + 0.115f,
            random.nextFloat() * 0.9f + 2.9f,
            random.nextFloat() * 0.20f + 0.62f,
            random.nextFloat() * 10f + 14f
        )
    }
    return SnowflakeState(
        xFraction = random.nextFloat(),
        yFraction = initialYFraction,
        speedPerSecond = speed,
        radiusDp = radiusDp,
        baseAlpha = alpha,
        swayAmplitudeDp = swayAmp,
        swaySpeed = random.nextFloat() * 1.1f + 0.7f,
        swayPhase = random.nextFloat() * (2f * PI.toFloat())
    )
}

private data class Quad(
    val speed: Float,
    val radiusDp: Float,
    val alpha: Float,
    val swayAmp: Float
)

@Composable
fun FallingSnowOverlay(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!enabled) return

    val context = LocalContext.current
    val reducedMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
    if (reducedMotion) return

    val isDark = isSystemInDarkTheme()
    val flakeBaseColor = if (isDark) {
        Color.White
    } else {
        // Soft icy-blue tint so snow stays gently visible against light surfaces
        Color(0xFF90B8DC)
    }
    val glowTint = if (isDark) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color(0xFFB3D4F0)
    }

    val random = remember { Random(System.nanoTime()) }
    val snowflakes = remember {
        List(42) {
            // Pre-distribute evenly + randomly across the full screen height on frame 1
            val staggeredY = (it.toFloat() / 42f + random.nextFloat() * (1f / 42f)) * 1.10f - 0.05f
            randomSnowflake(random, staggeredY)
        }
    }

    var frameTick by remember { mutableLongStateOf(0L) }
    val elapsedHolder = remember { FloatArray(1) }

    LaunchedEffect(enabled) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                if (lastNanos != 0L) {
                    // Clamp dt so returning from background never teleports flakes
                    val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    elapsedHolder[0] += dt
                    for (flake in snowflakes) {
                        flake.yFraction += flake.speedPerSecond * dt
                        if (flake.yFraction > 1.06f) {
                            // Respawn only this individual flake cleanly above the top edge
                            val replacement = randomSnowflake(
                                random,
                                -0.04f - random.nextFloat() * 0.08f
                            )
                            flake.xFraction = replacement.xFraction
                            flake.yFraction = replacement.yFraction
                            flake.speedPerSecond = replacement.speedPerSecond
                            flake.radiusDp = replacement.radiusDp
                            flake.baseAlpha = replacement.baseAlpha
                            flake.swayAmplitudeDp = replacement.swayAmplitudeDp
                            flake.swaySpeed = replacement.swaySpeed
                            flake.swayPhase = replacement.swayPhase
                        }
                    }
                    frameTick = nowNanos
                }
                lastNanos = nowNanos
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Read frameTick to trigger draw invalidation every frame without recomposing
        @Suppress("UNUSED_EXPRESSION")
        frameTick

        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val t = elapsedHolder[0]
        for (flake in snowflakes) {
            val yFrac = flake.yFraction
            // Gentle fade-in at top edge and fade-out at bottom edge so wrapping is invisible
            val edgeFade = when {
                yFrac < 0.08f -> ((yFrac + 0.05f) / 0.13f).coerceIn(0f, 1f)
                yFrac > 0.90f -> ((1.06f - yFrac) / 0.16f).coerceIn(0f, 1f)
                else -> 1f
            }
            val alpha = (flake.baseAlpha * edgeFade).coerceIn(0f, 1f)
            if (alpha <= 0.01f) continue

            val radiusPx = flake.radiusDp * density
            val swayPx = sin(t * flake.swaySpeed + flake.swayPhase) * (flake.swayAmplitudeDp * density)
            val cx = (flake.xFraction * w) + swayPx
            val cy = yFrac * h

            // Soft halo for foreground flakes
            if (flake.radiusDp >= 2.8f) {
                drawCircle(
                    color = glowTint.copy(alpha = alpha * 0.25f),
                    radius = radiusPx * 1.9f,
                    center = Offset(cx, cy)
                )
            }
            drawCircle(
                color = flakeBaseColor.copy(alpha = alpha),
                radius = radiusPx,
                center = Offset(cx, cy)
            )
        }
    }
}
