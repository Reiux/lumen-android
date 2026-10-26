package com.reiux.lumen.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun PlayfulCompletionCelebration(
    completedCount: Int,
    enabledButtons: Boolean,
    onStartBackup: () -> Unit,
    onPickPhotos: () -> Unit,
    modifier: Modifier = Modifier,
    customTitle: String? = null
) {
    val haptic = LocalHapticFeedback.current

    val scaleAnim = remember { Animatable(0f) }
    val particleProgress = remember { Animatable(0f) }

    LaunchedEffect(completedCount) {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {}

        scaleAnim.snapTo(0f)
        particleProgress.snapTo(0f)

        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )

        particleProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessVeryLow
            )
        )
    }

    val particleColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        Color(0xFFFFB74D),
        Color(0xFF81C784)
    )

    val particles = remember {
        List(20) { index ->
            val angle = (index * (360f / 20f)) * (Math.PI / 180.0)
            val distance = Random.nextFloat() * 65f + 55f
            val radius = Random.nextFloat() * 4f + 3f
            val color = particleColors[index % particleColors.size]
            ParticleData(angle, distance, radius, color)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentAlignment = Alignment.Center
            ) {
                // Particle burst canvas
                Canvas(
                    modifier = Modifier.size(200.dp)
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val p = particleProgress.value
                    val alpha = (1f - p * 0.75f).coerceIn(0f, 1f)

                    particles.forEach { particle ->
                        val dist = particle.distance * p
                        val x = center.x + (cos(particle.angle) * dist).toFloat()
                        val y = center.y + (sin(particle.angle) * dist).toFloat()
                        drawCircle(
                            color = particle.color.copy(alpha = alpha),
                            radius = particle.radius * (1.3f - p * 0.5f),
                            center = Offset(x, y)
                        )
                    }
                }

                // M3E Spring Pop Check Badge
                Box(
                    modifier = Modifier
                        .scale(scaleAnim.value)
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Complete",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = customTitle ?: "All Backed Up!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.height(4.dp))

            val countText = if (completedCount > 0) "$completedCount items backed up to Google Photos" else "Your photos and videos are safe in Google Photos"
            Text(
                text = countText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExpressiveButton(
                    onClick = onStartBackup,
                    enabled = enabledButtons,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Folders")
                }
                ExpressiveTonalButton(
                    onClick = onPickPhotos,
                    enabled = enabledButtons,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick Photos & Videos")
                }
            }
        }
    }
}

@Composable
fun PlayfulParticleBurstOverlay(
    onFinished: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val particleProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {}

        particleProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessVeryLow
            )
        )
        onFinished()
    }

    val particleColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFFFFB74D),
        Color(0xFF81C784),
        Color(0xFFE040FB)
    )

    val particles = remember {
        List(24) { index ->
            val angle = (index * (360f / 24f)) * (Math.PI / 180.0)
            val distance = Random.nextFloat() * 120f + 80f
            val radius = Random.nextFloat() * 6f + 4f
            val color = particleColors[index % particleColors.size]
            ParticleData(angle, distance, radius, color)
        }
    }

    Box(
        modifier = Modifier.size(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(300.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val p = particleProgress.value
            val alpha = (1f - p * 0.85f).coerceIn(0f, 1f)

            particles.forEach { particle ->
                val dist = particle.distance * p
                val x = center.x + (cos(particle.angle) * dist).toFloat()
                val y = center.y + (sin(particle.angle) * dist).toFloat()
                drawCircle(
                    color = particle.color.copy(alpha = alpha),
                    radius = particle.radius * (1.4f - p * 0.5f),
                    center = Offset(x, y)
                )
            }
        }
    }
}

private data class ParticleData(
    val angle: Double,
    val distance: Float,
    val radius: Float,
    val color: Color
)
