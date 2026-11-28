package com.reiux.lumen.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive FilterChip that morphs its corner radius between rounded-square (8dp)
 * when unselected and full pill (20dp) when selected, with tactile haptic feedback.
 */
@Composable
fun ExpressiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected)
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 20.dp else 8.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "expressiveFilterChipCorner"
    )
    val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove, onClick)

    FilterChip(
        selected = selected,
        onClick = hapticClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(cornerRadius),
        colors = colors,
        border = border
    )
}
