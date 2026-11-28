package com.reiux.lumen.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Linear Wavy Progress Indicator backed by official
 * [androidx.compose.material3.LinearWavyProgressIndicator].
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
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "wavyProgress"
    )
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)

    LinearWavyProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier
            .fillMaxWidth()
            .requiredHeightIn(min = 10.dp),
        color = color,
        trackColor = trackColor,
        stroke = stroke,
        trackStroke = stroke,
        amplitude = { p ->
            if (waveAmplitude <= 0.dp) 0f else WavyProgressIndicatorDefaults.indicatorAmplitude(p)
        },
        wavelength = wavePeriod,
    )
}

@Composable
fun ExpressiveWavyIndeterminateProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
    waveAmplitude: Dp = 3.5.dp,
    wavePeriod: Dp = 26.dp,
) {
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)

    LinearWavyProgressIndicator(
        modifier = modifier
            .fillMaxWidth()
            .requiredHeightIn(min = 10.dp),
        color = color,
        stroke = stroke,
        trackStroke = stroke,
        amplitude = if (waveAmplitude <= 0.dp) 0f else 1f,
        wavelength = wavePeriod,
    )
}

@Composable
fun ExpressiveCircularWavyProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    if (progress != null) {
        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "circularWavyProgress"
        )
        CircularWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = modifier,
            color = color,
            trackColor = trackColor,
        )
    } else {
        CircularWavyProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor,
        )
    }
}
