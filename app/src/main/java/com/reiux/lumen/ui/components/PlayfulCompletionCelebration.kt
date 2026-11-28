package com.reiux.lumen.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reiux.lumen.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun PlayfulCompletionCelebration(
    completedCount: Int,
    skippedCount: Int = 0,
    enabledButtons: Boolean,
    onStartBackup: () -> Unit,
    onPickPhotos: () -> Unit,
    onPickFiles: () -> Unit = {},
    modifier: Modifier = Modifier,
    customTitle: String? = null
) {
    val haptic = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()

    val scaleAnim = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val particleProgress = remember { Animatable(0f) }
    val badgeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val particleSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

    LaunchedEffect(completedCount, reducedMotion) {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {}

        if (reducedMotion) {
            scaleAnim.snapTo(1f)
            particleProgress.snapTo(0f)
            return@LaunchedEffect
        }

        scaleAnim.snapTo(0f)
        particleProgress.snapTo(0f)

        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = badgeSpec
        )

        particleProgress.animateTo(
            targetValue = 1f,
            animationSpec = particleSpec
        )
    }

    val particleColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer
    )

    val particles = remember(particleColors) {
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
        shape = MaterialTheme.shapes.extraLarge,
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
                if (!reducedMotion) {
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
                }

                // M3E Spring Pop Check Badge (SoftBurst)
                val badgeShape = MaterialShapes.SoftBurst.toShape()
                Box(
                    modifier = Modifier
                        .scale(scaleAnim.value)
                        .size(68.dp)
                        .clip(badgeShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.completion_cd_complete),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = customTitle ?: stringResource(R.string.completion_default_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.height(4.dp))

            val countText = when {
                completedCount > 0 && skippedCount > 0 ->
                    stringResource(R.string.completion_summary_both, completedCount, skippedCount)
                completedCount == 1 && skippedCount == 0 ->
                    stringResource(R.string.completion_summary_uploaded_one)
                completedCount > 1 && skippedCount == 0 ->
                    stringResource(R.string.completion_summary_uploaded, completedCount)
                completedCount == 0 && skippedCount == 1 ->
                    stringResource(R.string.completion_summary_skipped_one)
                completedCount == 0 && skippedCount > 1 ->
                    stringResource(R.string.completion_summary_skipped, skippedCount)
                else ->
                    stringResource(R.string.completion_summary_default)
            }
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
                    Text(androidx.compose.ui.res.stringResource(com.reiux.lumen.R.string.btn_start_backup))
                }
                ExpressiveTonalButton(
                    onClick = onPickPhotos,
                    enabled = enabledButtons,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(androidx.compose.ui.res.stringResource(com.reiux.lumen.R.string.pick_photos_button))
                }
                ExpressiveTonalButton(
                    onClick = onPickFiles,
                    enabled = enabledButtons,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.FolderCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(androidx.compose.ui.res.stringResource(com.reiux.lumen.R.string.pick_files_button))
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
    val reducedMotion = rememberReducedMotion()
    val particleProgress = remember { Animatable(0f) }
    val particleSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

    LaunchedEffect(reducedMotion) {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {}

        if (reducedMotion) {
            onFinished()
            return@LaunchedEffect
        }

        particleProgress.animateTo(
            targetValue = 1f,
            animationSpec = particleSpec
        )
        onFinished()
    }

    if (reducedMotion) return

    val particleColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer
    )

    val particles = remember(particleColors) {
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
