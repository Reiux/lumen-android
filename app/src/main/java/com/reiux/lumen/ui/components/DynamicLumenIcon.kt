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

@Composable
fun DynamicLumenIcon(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    rotationAngle: Float = 0f,
    scaleFactor: Float = 1f,
    blade1Color: Color = MaterialTheme.colorScheme.primary,
    blade2Color: Color = MaterialTheme.colorScheme.secondary,
    blade3Color: Color = MaterialTheme.colorScheme.tertiary,
    blade4Color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
    centerColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val scale = w / 108f

        scale(scaleFactor, pivot = center) {
            rotate(rotationAngle, pivot = center) {
                val bladeColors = listOf(blade1Color, blade2Color, blade3Color, blade4Color)

                // Draw 4 rotating aperture blades
                bladeColors.forEachIndexed { index, color ->
                    rotate(degrees = index * 90f, pivot = center) {
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
                    color = centerColor,
                    radius = 7f * scale,
                    center = center
                )
            }
        }
    }
}
