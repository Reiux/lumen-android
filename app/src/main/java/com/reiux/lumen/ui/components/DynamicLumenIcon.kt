package com.reiux.lumen.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reiux.lumen.util.Season

@Composable
fun DynamicLumenIcon(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    rotationAngle: Float = 0f,
    scaleFactor: Float = 1f,
    activeSeason: Season = Season.NONE,
    blade1Color: Color? = null,
    blade2Color: Color? = null,
    blade3Color: Color? = null,
    blade4Color: Color? = null,
    centerColor: Color? = null
) {
    val (seasonB1, seasonB2, seasonB3, seasonB4, seasonCenter) = when (activeSeason) {
        Season.HALLOWEEN -> Quintuple(
            Color(0xFFFF6D00), Color(0xFF7B1FA2), Color(0xFF00E676), Color(0xFFFF3D00), Color(0xFFFFD600)
        )
        Season.CHRISTMAS -> Quintuple(
            Color(0xFFD50000), Color(0xFF00C853), Color(0xFFFFD600), Color(0xFF80D8FF), Color(0xFFFFD600)
        )
        Season.NEW_YEAR -> Quintuple(
            Color(0xFFFFD600), Color(0xFF00F2FE), Color(0xFFFF007F), Color(0xFFE0E0E0), Color(0xFFE0E0E0)
        )
        Season.NONE -> Quintuple(null, null, null, null, null)
    }

    val b1 = blade1Color ?: seasonB1 ?: MaterialTheme.colorScheme.primary
    val b2 = blade2Color ?: seasonB2 ?: MaterialTheme.colorScheme.secondary
    val b3 = blade3Color ?: seasonB3 ?: MaterialTheme.colorScheme.tertiary
    val b4 = blade4Color ?: seasonB4 ?: MaterialTheme.colorScheme.primaryContainer
    val center = centerColor ?: seasonCenter ?: MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val scale = w / 108f

        scale(scaleFactor, pivot = this.center) {
            rotate(rotationAngle, pivot = this.center) {
                val bladeColors = listOf(b1, b2, b3, b4)

                // Draw 4 rotating aperture blades
                bladeColors.forEachIndexed { index, color ->
                    rotate(degrees = index * 90f, pivot = this.center) {
                        val path = Path().apply {
                            moveTo(54f * scale, 26f * scale)
                            cubicTo(
                                65f * scale, 26f * scale,
                                76f * scale, 36f * scale,
                                76f * scale, 47f * scale
                            )
                            cubicTo(
                                76f * scale, 52f * scale,
                                70f * scale, 54f * scale,
                                63f * scale, 54f * scale
                            )
                            cubicTo(
                                59f * scale, 44f * scale,
                                54f * scale, 35f * scale,
                                54f * scale, 26f * scale
                            )
                            close()
                        }
                        drawPath(path = path, color = color)
                    }
                }

                // Central Radiant Lens Core
                drawCircle(
                    color = center,
                    radius = 7f * scale,
                    center = this.center
                )
            }
        }
    }
}

private data class Quintuple<T1, T2, T3, T4, T5>(
    val first: T1,
    val second: T2,
    val third: T3,
    val fourth: T4,
    val fifth: T5
)
