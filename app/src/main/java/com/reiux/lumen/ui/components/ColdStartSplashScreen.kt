package com.reiux.lumen.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reiux.lumen.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive Cold Start Opening Animation.
 * Uses Compose's exact MaterialTheme.typography (displayMedium Bold) to render the "Lumen" brand title,
 * guaranteeing identical font, kerning, and styling as the Home screen.
 */
@Composable
fun ColdStartSplashScreen(
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnAnimationFinished = rememberUpdatedState(onAnimationFinished)
    val motionScheme = MaterialTheme.motionScheme
    val softBurstShape = MaterialShapes.SoftBurst.toShape()

    val rotation = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.7f) }
    val burstScale = remember { Animatable(0.45f) }
    val burstAlpha = remember { Animatable(0f) }
    val burstRotation = remember { Animatable(-15f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleScale = remember { Animatable(0.85f) }
    val titleOffsetY = remember { Animatable(24f) }
    val titleLetterSpacing = remember { Animatable(2.0f) }
    val splashAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Phase 1: M3E expressive entrance spin, SoftBurst bloom, and spring settle for title
        launch {
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = motionScheme.slowSpatialSpec()
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = motionScheme.defaultSpatialSpec()
            )
        }
        launch {
            burstScale.animateTo(
                targetValue = 1f,
                animationSpec = motionScheme.slowSpatialSpec()
            )
        }
        launch {
            burstRotation.animateTo(
                targetValue = 18f,
                animationSpec = motionScheme.slowSpatialSpec()
            )
        }
        launch {
            burstAlpha.animateTo(
                targetValue = 0.65f,
                animationSpec = motionScheme.fastEffectsSpec()
            )
        }
        launch {
            titleAlpha.animateTo(
                targetValue = 1f,
                animationSpec = motionScheme.fastEffectsSpec()
            )
        }
        launch {
            titleScale.animateTo(
                targetValue = 1f,
                animationSpec = motionScheme.defaultSpatialSpec()
            )
        }
        launch {
            titleOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = motionScheme.defaultSpatialSpec()
            )
        }
        launch {
            titleLetterSpacing.animateTo(
                targetValue = -0.5f,
                animationSpec = motionScheme.defaultSpatialSpec()
            )
        }

        // Crisp hold for glanceable brand presence (~650ms before fade out)
        delay(650)

        // Phase 2: M3E scale bloom and seamless fade into app UI
        launch {
            logoScale.animateTo(
                targetValue = 1.12f,
                animationSpec = motionScheme.defaultSpatialSpec()
            )
        }
        launch {
            burstScale.animateTo(
                targetValue = 1.35f,
                animationSpec = motionScheme.slowSpatialSpec()
            )
        }
        launch {
            burstAlpha.animateTo(
                targetValue = 0f,
                animationSpec = motionScheme.fastEffectsSpec()
            )
        }
        splashAlpha.animateTo(
            targetValue = 0f,
            animationSpec = motionScheme.fastEffectsSpec()
        )

        currentOnAnimationFinished.value()
    }

    if (splashAlpha.value > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { alpha = splashAlpha.value }
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(164.dp)
                            .graphicsLayer {
                                scaleX = burstScale.value
                                scaleY = burstScale.value
                                rotationZ = burstRotation.value
                                alpha = burstAlpha.value
                            }
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                                        Color.Transparent
                                    )
                                ),
                                shape = softBurstShape
                            )
                    )
                    DynamicLumenIcon(
                        size = 104.dp,
                        rotationAngle = rotation.value,
                        scaleFactor = logoScale.value
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = titleAlpha.value
                            scaleX = titleScale.value
                            scaleY = titleScale.value
                            translationY = titleOffsetY.value.dp.toPx()
                        }
                ) {
                    Text(
                        text = "Lumen",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = titleLetterSpacing.value.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}
