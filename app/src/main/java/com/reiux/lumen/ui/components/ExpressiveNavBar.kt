package com.reiux.lumen.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import com.reiux.lumen.util.LumenLogger

enum class NavDestination(
    val title: String,
    val icon: ImageVector
) {
    BACKUP("Backup", Icons.Rounded.CloudUpload),
    FOLDERS("Folders", Icons.Rounded.FolderCopy),
    ACCOUNTS("Accounts", Icons.Rounded.AccountCircle),
    SETTINGS("Settings", Icons.Rounded.Settings)
}

@Composable
fun ExpressiveNavBar(
    selectedDestination: NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavDestination.entries.forEach { destination ->
                    val selected = destination == selectedDestination
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by rememberExpressivePressed(interactionSource)
                    val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
                        LumenLogger.i("NavBar", "Selected tab: ${destination.title}")
                        onDestinationSelected(destination)
                    }

                    val indicatorWidth by animateDpAsState(
                        targetValue = if (selected) (if (isPressed) 68.dp else 64.dp) else (if (isPressed) 48.dp else 36.dp),
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
                        label = "navIndicatorWidth"
                    )

                    val indicatorCorner by animateDpAsState(
                        targetValue = if (isPressed) 10.dp else 16.dp,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
                        label = "navIndicatorCorner"
                    )

                    val iconScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.88f else (if (selected) 1.05f else 1.0f),
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
                        label = "navIconScale"
                    )

                    val indicatorColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else (if (isPressed) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent),
                        animationSpec = tween(durationMillis = 200),
                        label = "indicatorColor"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(durationMillis = 200),
                        label = "iconColor"
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(durationMillis = 200),
                        label = "labelColor"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = hapticClick
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(indicatorWidth)
                                .height(32.dp)
                                .clip(RoundedCornerShape(indicatorCorner))
                                .background(indicatorColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.title,
                                tint = iconColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(iconScale)
                            )
                        }
                        Text(
                            text = destination.title,
                            color = labelColor,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveNavRail(
    selectedDestination: NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutDirection = LocalLayoutDirection.current
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val startPadding = safeInsets.calculateStartPadding(layoutDirection)
    val topPadding = safeInsets.calculateTopPadding()
    val bottomPadding = safeInsets.calculateBottomPadding()

    // 96dp base width matching the comfortable item size of portrait mode + edge cutout padding
    val railBaseWidth = 96.dp
    val totalRailWidth = railBaseWidth + startPadding

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(totalRailWidth),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = startPadding,
                    top = topPadding.coerceAtLeast(16.dp),
                    bottom = bottomPadding.coerceAtLeast(16.dp)
                )
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NavDestination.entries.forEach { destination ->
                val selected = destination == selectedDestination
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by rememberExpressivePressed(interactionSource)
                val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
                    LumenLogger.i("NavRail", "Selected tab: ${destination.title}")
                    onDestinationSelected(destination)
                }

                // Identical size and spring animation as portrait ExpressiveNavBar
                val indicatorWidth by animateDpAsState(
                    targetValue = if (selected) (if (isPressed) 68.dp else 64.dp) else (if (isPressed) 48.dp else 36.dp),
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
                    label = "railIndicatorWidth"
                )

                val indicatorCorner by animateDpAsState(
                    targetValue = if (isPressed) 10.dp else 16.dp,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
                    label = "railIndicatorCorner"
                )

                val iconScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.88f else (if (selected) 1.05f else 1.0f),
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
                    label = "railIconScale"
                )

                val indicatorColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else (if (isPressed) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent),
                    animationSpec = tween(durationMillis = 200),
                    label = "railIndicatorColor"
                )
                val iconColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 200),
                    label = "railIconColor"
                )
                val labelColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 200),
                    label = "railLabelColor"
                )

                Column(
                    modifier = Modifier
                        .width(84.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = hapticClick
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(indicatorWidth)
                            .height(32.dp)
                            .clip(RoundedCornerShape(indicatorCorner))
                            .background(indicatorColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.title,
                            tint = iconColor,
                            modifier = Modifier
                                .size(24.dp)
                                .scale(iconScale)
                        )
                    }
                    Text(
                        text = destination.title,
                        color = labelColor,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

