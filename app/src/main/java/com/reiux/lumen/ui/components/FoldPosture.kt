package com.reiux.lumen.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class FoldPosture(
    val isTabletop: Boolean = false,
    val isBookMode: Boolean = false,
    val hingeTopDp: Dp = 0.dp,
    val hingeHeightDp: Dp = 0.dp,
    val windowHeightDp: Dp = 0.dp
) {
    val hingeBottomDp: Dp get() = hingeTopDp + hingeHeightDp

    fun tabletopBottomSheetMaxHeight(
        fallbackScreenHeightDp: Dp,
        topBufferDp: Dp = 120.dp
    ): Dp {
        val totalHeight = if (windowHeightDp > 0.dp) windowHeightDp else fallbackScreenHeightDp
        val available = totalHeight - hingeBottomDp - topBufferDp
        return available.coerceAtLeast(220.dp)
    }

    fun tabletopTopPaneHeight(
        containerTopInWindowDp: Dp,
        maxAvailableHeightDp: Dp,
        bottomMarginDp: Dp = 8.dp,
        minHeightDp: Dp = 180.dp
    ): Dp {
        val raw = if (hingeTopDp > 0.dp) {
            hingeTopDp - containerTopInWindowDp - bottomMarginDp
        } else {
            maxAvailableHeightDp / 2
        }
        return raw.coerceIn(minHeightDp, maxAvailableHeightDp.coerceAtLeast(minHeightDp))
    }
}

val LocalFoldPosture = staticCompositionLocalOf { FoldPosture() }

@Composable
fun TabletopSplitLayout(
    modifier: Modifier = Modifier,
    safetyMarginDp: Dp = 16.dp,
    topContent: @Composable BoxScope.() -> Unit,
    bottomContent: @Composable BoxScope.() -> Unit
) {
    val foldPosture = LocalFoldPosture.current
    val density = LocalDensity.current
    var containerTopInWindowPx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                containerTopInWindowPx = coords.positionInWindow().y
            }
    ) {
        val containerTopDp = with(density) { containerTopInWindowPx.toDp() }
        val gutterHeight = foldPosture.hingeHeightDp + safetyMarginDp * 2
        val rawTopHeight = if (foldPosture.hingeTopDp > 0.dp) {
            foldPosture.hingeTopDp - containerTopDp - safetyMarginDp
        } else {
            (maxHeight - gutterHeight) / 2
        }
        val maxAllowedTop = (maxHeight - gutterHeight - 140.dp).coerceAtLeast(120.dp)
        val topPaneHeight = rawTopHeight.coerceIn(120.dp, maxAllowedTop)

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topPaneHeight)
                    .clipToBounds(),
                content = topContent
            )
            Spacer(modifier = Modifier.height(gutterHeight))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds(),
                content = bottomContent
            )
        }
    }
}
