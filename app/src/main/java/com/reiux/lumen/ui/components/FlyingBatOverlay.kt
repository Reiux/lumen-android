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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class BatState(
    var xFraction: Float,
    var baseYFraction: Float,
    var direction: Float, // +1f = left->right, -1f = right->left
    var speedPerSecond: Float,
    var wingspanDp: Float,
    var swoopAmplitudeDp: Float,
    var swoopFreq: Float,
    var swoopPhase: Float,
    var flapFreq: Float,
    var flapPhase: Float,
    var alpha: Float,
    var cooldownSeconds: Float
)

private fun spawnBat(random: Random, initialCooldown: Float, startOnScreen: Boolean = false): BatState {
    val direction = if (random.nextBoolean()) 1f else -1f
    val startX = when {
        startOnScreen -> random.nextFloat() * 0.7f + 0.15f
        direction > 0f -> -0.14f
        else -> 1.14f
    }
    // Depth-varied size and speed (smaller distant bats vs closer bats)
    val scale = random.nextFloat() * 0.55f + 0.65f // 0.65..1.20
    return BatState(
        xFraction = startX,
        baseYFraction = random.nextFloat() * 0.34f + 0.09f, // upper/mid screen band
        direction = direction,
        speedPerSecond = (random.nextFloat() * 0.08f + 0.12f) * scale,
        wingspanDp = 22f * scale,
        swoopAmplitudeDp = (random.nextFloat() * 14f + 12f) * scale,
        swoopFreq = random.nextFloat() * 1.4f + 1.6f,
        swoopPhase = random.nextFloat() * (2f * PI.toFloat()),
        flapFreq = random.nextFloat() * 4.0f + 11.5f, // smooth natural wingbeat (~2 flaps/sec)
        flapPhase = random.nextFloat() * (2f * PI.toFloat()),
        alpha = (0.48f + 0.30f * ((scale - 0.65f) / 0.55f)).coerceIn(0.45f, 0.80f),
        cooldownSeconds = initialCooldown
    )
}

@Composable
fun FlyingBatOverlay(
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
    // Visible on both dark spooky themes and light themes without overwhelming content
    val batBaseColor = if (isDark) {
        MaterialTheme.colorScheme.primary.copy(alpha = 1f)
    } else {
        Color(0xFF2B1838)
    }

    val random = remember { Random(System.nanoTime()) }
    val bats = remember {
        listOf(
            spawnBat(random, initialCooldown = 0f, startOnScreen = true),
            spawnBat(random, initialCooldown = 0.8f, startOnScreen = false),
            spawnBat(random, initialCooldown = 2.4f, startOnScreen = false),
            spawnBat(random, initialCooldown = 4.2f, startOnScreen = false)
        )
    }

    val batPath = remember { Path() }
    var frameTick by remember { mutableLongStateOf(0L) }
    val elapsedHolder = remember { FloatArray(1) }

    LaunchedEffect(enabled) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                if (lastNanos != 0L) {
                    val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    elapsedHolder[0] += dt
                    for (bat in bats) {
                        if (bat.cooldownSeconds > 0f) {
                            bat.cooldownSeconds = (bat.cooldownSeconds - dt).coerceAtLeast(0f)
                            continue
                        }
                        bat.xFraction += bat.direction * bat.speedPerSecond * dt
                        val exitedRight = bat.direction > 0f && bat.xFraction > 1.16f
                        val exitedLeft = bat.direction < 0f && bat.xFraction < -0.16f
                        if (exitedRight || exitedLeft) {
                            val next = spawnBat(
                                random = random,
                                initialCooldown = random.nextFloat() * 3.5f + 0.8f,
                                startOnScreen = false
                            )
                            bat.xFraction = next.xFraction
                            bat.baseYFraction = next.baseYFraction
                            bat.direction = next.direction
                            bat.speedPerSecond = next.speedPerSecond
                            bat.wingspanDp = next.wingspanDp
                            bat.swoopAmplitudeDp = next.swoopAmplitudeDp
                            bat.swoopFreq = next.swoopFreq
                            bat.swoopPhase = next.swoopPhase
                            bat.flapFreq = next.flapFreq
                            bat.flapPhase = next.flapPhase
                            bat.alpha = next.alpha
                            bat.cooldownSeconds = next.cooldownSeconds
                        }
                    }
                    frameTick = nowNanos
                }
                lastNanos = nowNanos
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION")
        frameTick

        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val t = elapsedHolder[0]
        for (bat in bats) {
            if (bat.cooldownSeconds > 0f) continue

            val cx = bat.xFraction * w
            val swoopAmpPx = bat.swoopAmplitudeDp * density
            val swoopAngle = t * bat.swoopFreq + bat.swoopPhase
            val cy = (bat.baseYFraction * h) + sin(swoopAngle) * swoopAmpPx

            // Subtle bank tilt following the vertical derivative of the swoop curve
            val tiltDeg = (cos(swoopAngle) * 10f) * bat.direction
            // Smooth sinusoidal wingbeat instead of sharp linear bounce
            val flap = sin(t * bat.flapFreq + bat.flapPhase)

            val halfSpan = (bat.wingspanDp * density) * 0.5f
            val bodyW = halfSpan * 0.22f
            val bodyH = halfSpan * 0.28f
            val wingLift = flap * halfSpan * 0.42f
            val midLift = flap * halfSpan * 0.20f

            // Gentle edge fade as bat enters/exits screen bounds
            val edgeFade = when {
                bat.xFraction < 0.04f -> ((bat.xFraction + 0.14f) / 0.18f).coerceIn(0f, 1f)
                bat.xFraction > 0.96f -> ((1.14f - bat.xFraction) / 0.18f).coerceIn(0f, 1f)
                else -> 1f
            }
            val finalAlpha = (bat.alpha * edgeFade * (if (isDark) 0.55f else 0.72f)).coerceIn(0f, 1f)
            if (finalAlpha <= 0.01f) continue

            withTransform({
                translate(left = cx, top = cy)
                rotate(degrees = tiltDeg, pivot = androidx.compose.ui.geometry.Offset.Zero)
            }) {
                batPath.reset()
                // Head & ears (centered around origin)
                batPath.moveTo(0f, -bodyH * 0.55f)
                // Right ear
                batPath.lineTo(bodyW * 0.55f, -bodyH * 1.05f)
                batPath.lineTo(bodyW * 0.85f, -bodyH * 0.35f)
                // Right wing leading edge
                batPath.cubicTo(
                    halfSpan * 0.40f, -halfSpan * 0.32f + midLift,
                    halfSpan * 0.78f, -halfSpan * 0.22f + wingLift,
                    halfSpan, wingLift * 0.95f
                )
                // Right wing scalloped trailing edge
                batPath.cubicTo(
                    halfSpan * 0.74f, halfSpan * 0.16f + midLift * 0.5f,
                    halfSpan * 0.56f, halfSpan * 0.06f + midLift * 0.3f,
                    halfSpan * 0.44f, halfSpan * 0.24f
                )
                batPath.cubicTo(
                    halfSpan * 0.30f, halfSpan * 0.10f,
                    bodyW * 1.1f, bodyH * 0.45f,
                    0f, bodyH * 0.75f
                )
                // Left wing scalloped trailing edge (mirror)
                batPath.cubicTo(
                    -bodyW * 1.1f, bodyH * 0.45f,
                    -halfSpan * 0.30f, halfSpan * 0.10f,
                    -halfSpan * 0.44f, halfSpan * 0.24f
                )
                batPath.cubicTo(
                    -halfSpan * 0.56f, halfSpan * 0.06f + midLift * 0.3f,
                    -halfSpan * 0.74f, halfSpan * 0.16f + midLift * 0.5f,
                    -halfSpan, wingLift * 0.95f
                )
                // Left wing leading edge
                batPath.cubicTo(
                    -halfSpan * 0.78f, -halfSpan * 0.22f + wingLift,
                    -halfSpan * 0.40f, -halfSpan * 0.32f + midLift,
                    -bodyW * 0.85f, -bodyH * 0.35f
                )
                // Left ear
                batPath.lineTo(-bodyW * 0.55f, -bodyH * 1.05f)
                batPath.close()

                drawPath(
                    path = batPath,
                    color = batBaseColor.copy(alpha = finalAlpha)
                )
            }
        }
    }
}
