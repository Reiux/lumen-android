package com.reiux.lumen.ui.components

import android.graphics.Matrix
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

/**
 * Material 3 Expressive shape-morphing loading indicator.
 * Cycles smoothly through geometric shapes (Flower/Scallop -> Pentagon -> Puffy Squircle -> Circle)
 * while rotating gently.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressiveLoading")

    // Rotation angle
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "loadingRotation",
    )

    // Morph cycle: 0 -> 1 -> 2 -> 3 -> 0
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "morphProgress",
    )

    // 4 Distinct Expressive Shapes normalized to unit circle (-1..1)
    val shapes = remember {
        val flower = RoundedPolygon.star(
            numVerticesPerRadius = 8,
            radius = 1f,
            innerRadius = 0.72f,
            rounding = CornerRounding(0.35f),
            innerRounding = CornerRounding(0.35f),
        )
        val pentagon = RoundedPolygon(
            numVertices = 5,
            radius = 1f,
            rounding = CornerRounding(0.32f),
        )
        val squircle = RoundedPolygon(
            numVertices = 4,
            radius = 1f,
            rounding = CornerRounding(0.55f),
        )
        val circle = RoundedPolygon.circle(
            numVertices = 16,
            radius = 0.95f,
        )
        listOf(flower, pentagon, squircle, circle)
    }

    val morph1 = remember(shapes) { Morph(shapes[0], shapes[1]) }
    val morph2 = remember(shapes) { Morph(shapes[1], shapes[2]) }
    val morph3 = remember(shapes) { Morph(shapes[2], shapes[0]) }

    val matrix = remember { Matrix() }
    val androidPath = remember { android.graphics.Path() }

    Canvas(
        modifier = modifier.size(size)
    ) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        val stage = morphProgress.toInt().coerceIn(0, 2)
        val frac = (morphProgress - stage).coerceIn(0f, 1f)

        androidPath.rewind()
        when (stage) {
            0 -> morph1.toPath(frac, androidPath)
            1 -> morph2.toPath(frac, androidPath)
            else -> morph3.toPath(frac, androidPath)
        }

        matrix.reset()
        matrix.postScale(radius, radius)
        matrix.postTranslate(center.x, center.y)
        androidPath.transform(matrix)

        rotate(degrees = rotation, pivot = center) {
            drawPath(
                path = androidPath.asComposePath(),
                color = color,
            )
        }
    }
}

@Composable
fun ExpressiveLoadingBox(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        ExpressiveLoadingIndicator(size = size, color = color)
    }
}
