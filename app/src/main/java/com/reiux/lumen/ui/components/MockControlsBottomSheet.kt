package com.reiux.lumen.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reiux.lumen.data.model.AuthFailureKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockControlsBottomSheet(
    simulateOffline: Boolean,
    simulateMissingAlbum: Boolean,
    forcedSeason: String,
    onDismiss: () -> Unit,
    onExitMockMode: () -> Unit,
    onToggleMockCondition: () -> Unit,
    onAddMockFiles: () -> Unit,
    onToggleSimulateOffline: () -> Unit,
    onToggleSimulateMissingAlbum: () -> Unit,
    onSimulateLargeScan: () -> Unit,
    onSimulateBlock: (AuthFailureKind) -> Unit,
    onClearBlock: () -> Unit,
    onSimulateUpdateAvailable: () -> Unit,
    onPreviewWhatsNewSheet: () -> Unit,
    onReplayAccountSwipeNudge: () -> Unit,
    onTriggerCompletionPun: () -> Unit,
    onCycleEmptyStateVariant: () -> Unit,
    onSelectSeason: (String) -> Unit,
    onTriggerSeasonalGreeting: () -> Unit,
    onTriggerLightsOff: () -> Unit,
    onTriggerBoldChoice: () -> Unit,
    onTriggerShakeFlicker: () -> Unit,
    onTriggerLateNight: () -> Unit,
    onTriggerMilestone: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Backup", "Safety", "Prompts", "Fun")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        DialogBlurEffect()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "Mock Controls",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                ExpressiveTonalButton(
                    onClick = {
                        onDismiss()
                        onExitMockMode()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Exit",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Connected M3E TonalToggleButton category bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                tabs.forEachIndexed { index, label ->
                    val shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    TonalToggleButton(
                        checked = selectedTab == index,
                        onCheckedChange = {
                            try {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } catch (_: Exception) {
                            }
                            selectedTab = index
                        },
                        shapes = shapes,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Tab content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (fadeIn(motionScheme.fastEffectsSpec()) +
                            slideInHorizontally(motionScheme.fastSpatialSpec()) { width ->
                                if (forward) width / 8 else -width / 8
                            }) togetherWith
                            (fadeOut(motionScheme.fastEffectsSpec()) +
                                slideOutHorizontally(motionScheme.fastSpatialSpec()) { width ->
                                    if (forward) -width / 8 else width / 8
                                })
                    },
                    label = "mockControlsTabContent"
                ) { tab ->
                    when (tab) {
                        0 -> BackupControlsTab(
                            simulateOffline = simulateOffline,
                            simulateMissingAlbum = simulateMissingAlbum,
                            onToggleSimulateOffline = onToggleSimulateOffline,
                            onToggleSimulateMissingAlbum = onToggleSimulateMissingAlbum,
                            onAddMockFiles = onAddMockFiles,
                            onToggleMockCondition = onToggleMockCondition,
                            onSimulateLargeScan = {
                                onDismiss()
                                onSimulateLargeScan()
                            }
                        )

                        1 -> SafetyControlsTab(
                            onSimulateBlock = { kind ->
                                onDismiss()
                                onSimulateBlock(kind)
                            },
                            onClearBlock = {
                                onDismiss()
                                onClearBlock()
                            }
                        )

                        2 -> PromptsControlsTab(
                            onSimulateUpdateAvailable = {
                                onDismiss()
                                onSimulateUpdateAvailable()
                            },
                            onPreviewWhatsNewSheet = {
                                onDismiss()
                                onPreviewWhatsNewSheet()
                            },
                            onReplayAccountSwipeNudge = {
                                onDismiss()
                                onReplayAccountSwipeNudge()
                            },
                            onTriggerCompletionPun = {
                                onDismiss()
                                onTriggerCompletionPun()
                            },
                            onCycleEmptyStateVariant = {
                                onDismiss()
                                onCycleEmptyStateVariant()
                            }
                        )

                        else -> FunControlsTab(
                            forcedSeason = forcedSeason,
                            onSelectSeason = onSelectSeason,
                            onTriggerSeasonalGreeting = onTriggerSeasonalGreeting,
                            onTriggerLightsOff = {
                                onDismiss()
                                onTriggerLightsOff()
                            },
                            onTriggerBoldChoice = onTriggerBoldChoice,
                            onTriggerShakeFlicker = {
                                onDismiss()
                                onTriggerShakeFlicker()
                            },
                            onTriggerLateNight = onTriggerLateNight,
                            onTriggerMilestone = onTriggerMilestone
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupControlsTab(
    simulateOffline: Boolean,
    simulateMissingAlbum: Boolean,
    onToggleSimulateOffline: () -> Unit,
    onToggleSimulateMissingAlbum: () -> Unit,
    onAddMockFiles: () -> Unit,
    onToggleMockCondition: () -> Unit,
    onSimulateLargeScan: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        MockSwitchRow(
            title = "Simulate Offline",
            subtitle = "Force backup queue into offline waiting state",
            icon = Icons.Rounded.WifiOff,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            checked = simulateOffline,
            onToggle = onToggleSimulateOffline,
            position = groupPositionOf(0, 5)
        )
        MockSwitchRow(
            title = "Simulate Missing Album",
            subtitle = "Pretend target Google Photos album was deleted",
            icon = Icons.Rounded.BrokenImage,
            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            checked = simulateMissingAlbum,
            onToggle = onToggleSimulateMissingAlbum,
            position = groupPositionOf(1, 5)
        )
        MockActionRow(
            title = "+ Add 2 Mock Files",
            subtitle = "Append 2 sample media items to the mock queue",
            icon = Icons.Rounded.AddPhotoAlternate,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            position = groupPositionOf(2, 5),
            onClick = onAddMockFiles
        )
        MockActionRow(
            title = "Cycle Wait Condition",
            subtitle = "Rotate between Wi-Fi, Charger, and None",
            icon = Icons.Rounded.HourglassEmpty,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            position = groupPositionOf(3, 5),
            onClick = onToggleMockCondition
        )
        MockActionRow(
            title = "Simulate Large Folder Scan",
            subtitle = "Show live folder scanning progress card",
            icon = Icons.Rounded.FolderCopy,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            position = groupPositionOf(4, 5),
            onClick = onSimulateLargeScan
        )
    }
}

@Composable
private fun SafetyControlsTab(
    onSimulateBlock: (AuthFailureKind) -> Unit,
    onClearBlock: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        MockActionRow(
            title = "Sign-In Required",
            subtitle = "Trigger re-authentication circuit breaker",
            icon = Icons.AutoMirrored.Rounded.Login,
            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            position = groupPositionOf(0, 4),
            onClick = { onSimulateBlock(AuthFailureKind.SIGN_IN_REQUIRED) }
        )
        MockActionRow(
            title = "Rate Limited (HTTP 429)",
            subtitle = "Pause uploads with a 2-minute cooldown timer",
            icon = Icons.Rounded.Speed,
            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            position = groupPositionOf(1, 4),
            onClick = { onSimulateBlock(AuthFailureKind.RATE_LIMITED) }
        )
        MockActionRow(
            title = "Account Restricted",
            subtitle = "Show high-severity account restriction banner",
            icon = Icons.Rounded.Block,
            iconContainerColor = MaterialTheme.colorScheme.errorContainer,
            iconContentColor = MaterialTheme.colorScheme.onErrorContainer,
            position = groupPositionOf(2, 4),
            onClick = { onSimulateBlock(AuthFailureKind.ACCOUNT_RESTRICTED) }
        )
        MockActionRow(
            title = "Clear Active Block",
            subtitle = "Reset circuit breaker and resume normal state",
            icon = Icons.Rounded.CheckCircle,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            position = groupPositionOf(3, 4),
            onClick = onClearBlock
        )
    }
}

@Composable
private fun PromptsControlsTab(
    onSimulateUpdateAvailable: () -> Unit,
    onPreviewWhatsNewSheet: () -> Unit,
    onReplayAccountSwipeNudge: () -> Unit,
    onTriggerCompletionPun: () -> Unit,
    onCycleEmptyStateVariant: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        MockActionRow(
            title = "Update Available Sheet",
            subtitle = "Preview the in-app release update bottom sheet",
            icon = Icons.Rounded.SystemUpdateAlt,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            position = groupPositionOf(0, 5),
            onClick = onSimulateUpdateAvailable
        )
        MockActionRow(
            title = "Post-Update What's New Sheet",
            subtitle = "Open the release highlights popup for current build",
            icon = Icons.Rounded.AutoAwesome,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            position = groupPositionOf(1, 5),
            onClick = onPreviewWhatsNewSheet
        )
        MockActionRow(
            title = "Replay Account Swipe Nudge",
            subtitle = "Re-run the horizontal swipe hint on Account card",
            icon = Icons.Rounded.SwipeRight,
            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            position = groupPositionOf(2, 5),
            onClick = onReplayAccountSwipeNudge
        )
        MockActionRow(
            title = "Completion Pun Title",
            subtitle = "Test the playful backup completion celebration",
            icon = Icons.Rounded.SentimentSatisfiedAlt,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            position = groupPositionOf(3, 5),
            onClick = onTriggerCompletionPun
        )
        MockActionRow(
            title = "Cycle Empty State Subtitle",
            subtitle = "Rotate through alternate empty-queue taglines",
            icon = Icons.Rounded.TextFields,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            position = groupPositionOf(4, 5),
            onClick = onCycleEmptyStateVariant
        )
    }
}

@Composable
private fun FunControlsTab(
    forcedSeason: String,
    onSelectSeason: (String) -> Unit,
    onTriggerSeasonalGreeting: () -> Unit,
    onTriggerLightsOff: () -> Unit,
    onTriggerBoldChoice: () -> Unit,
    onTriggerShakeFlicker: () -> Unit,
    onTriggerLateNight: () -> Unit,
    onTriggerMilestone: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val seasons = listOf(
        "NONE" to "Off",
        "HALLOWEEN" to "Halloween",
        "CHRISTMAS" to "Christmas",
        "NEW_YEAR" to "New Year"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Seasonal Theme",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                seasons.forEachIndexed { index, (value, label) ->
                    val isSelected = forcedSeason.equals(value, ignoreCase = true) ||
                        (value == "NONE" && forcedSeason.isBlank())
                    val buttonShapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        seasons.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    TonalToggleButton(
                        checked = isSelected,
                        onCheckedChange = {
                            try {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } catch (_: Exception) {
                            }
                            onSelectSeason(value)
                        },
                        shapes = buttonShapes,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            MockActionRow(
                title = "Show Seasonal Greeting",
                subtitle = "Trigger seasonal banner greeting message",
                icon = Icons.Rounded.Celebration,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                position = groupPositionOf(0, 5),
                onClick = onTriggerSeasonalGreeting
            )
            MockActionRow(
                title = "Lights Off Dim Overlay",
                subtitle = "Play the ambient lights-off screen effect",
                icon = Icons.Rounded.Lightbulb,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                position = groupPositionOf(1, 5),
                onClick = onTriggerLightsOff
            )
            MockActionRow(
                title = "Bold Choice Toast",
                subtitle = "Display the playful bold-choice toast",
                icon = Icons.Rounded.AutoAwesome,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                position = groupPositionOf(2, 5),
                onClick = onTriggerBoldChoice
            )
            MockActionRow(
                title = "Shake Flicker",
                subtitle = "Simulate shake-to-flicker easter egg animation",
                icon = Icons.Rounded.Vibration,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                position = groupPositionOf(3, 5),
                onClick = onTriggerShakeFlicker
            )
            MockActionRow(
                title = "Late Night Toast",
                subtitle = "Show the late-night backup companion message",
                icon = Icons.Rounded.NightsStay,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                position = groupPositionOf(4, 5),
                onClick = onTriggerLateNight
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = groupedCardShape(GroupPosition.Single),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "Milestones",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                listOf(100 to "100", 1000 to "1k", 10000 to "10k").forEach { (count, label) ->
                    ExpressiveTonalButton(
                        onClick = { onTriggerMilestone(count) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MockSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    checked: Boolean,
    onToggle: () -> Unit,
    position: GroupPosition
) {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    val isGesturePressed by rememberExpressivePressed(interactionSource)
    var clickPulse by remember { mutableStateOf(false) }
    val shape = animatedGroupedCardShape(
        position = position,
        pressed = isGesturePressed || clickPulse
    )

    val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
        coroutineScope.launch {
            clickPulse = true
            delay(180L)
            clickPulse = false
        }
        onToggle()
    }

    Surface(
        onClick = hapticClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconContainerColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconContentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ExpressiveSwitch(
                checked = checked,
                onCheckedChange = { hapticClick() }
            )
        }
    }
}

@Composable
private fun MockActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    position: GroupPosition,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    val isGesturePressed by rememberExpressivePressed(interactionSource)
    var clickPulse by remember { mutableStateOf(false) }
    val shape = animatedGroupedCardShape(
        position = position,
        pressed = isGesturePressed || clickPulse
    )

    val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
        coroutineScope.launch {
            clickPulse = true
            delay(180L)
            clickPulse = false
        }
        onClick()
    }

    Surface(
        onClick = hapticClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconContainerColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconContentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
