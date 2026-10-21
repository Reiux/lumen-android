package com.reiux.lumen.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun rememberHapticClick(
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    onClick: () -> Unit,
): () -> Unit {
    val haptic = LocalHapticFeedback.current
    val currentOnClick = rememberUpdatedState(onClick)
    return remember(haptic, hapticType) {
        {
            try {
                haptic.performHapticFeedback(hapticType)
            } catch (_: Exception) {
            }
            currentOnClick.value()
        }
    }
}
