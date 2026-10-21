package com.reiux.lumen.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val rotation = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.7f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(20f) }
    val splashAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Phase 1: M3E Bouncy entrance spin for logo + slide-up fade-in for title
        launch {
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 300f
                )
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.65f,
                    stiffness = 350f
                )
            )
        }
        launch {
            titleAlpha.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 1.0f,
                    stiffness = 400f
                )
            )
        }
        launch {
            titleOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.75f,
                    stiffness = 350f
                )
            )
        }

        // Hold for glanceable brand presence
        delay(650)

        // Phase 2: M3E scale bloom and seamless fade into app UI
        launch {
            logoScale.animateTo(
                targetValue = 1.12f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 300f
                )
            )
        }
        launch {
            splashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 1.0f,
                    stiffness = 500f
                )
            )
        }

        delay(280)
        onAnimationFinished()
    }

    if (splashAlpha.value > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .alpha(splashAlpha.value)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                DynamicLumenIcon(
                    size = 104.dp,
                    rotationAngle = rotation.value,
                    scaleFactor = logoScale.value
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = titleAlpha.value
                            translationY = titleOffsetY.value.dp.toPx()
                        }
                ) {
                    Text(
                        text = "Lumen",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Unlimited Cloud Backup",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
