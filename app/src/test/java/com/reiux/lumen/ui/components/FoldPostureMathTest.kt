package com.reiux.lumen.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldPostureMathTest {

    @Test
    fun pixel9ProFold_tabletopTopPaneAndBottomSheetRespectCreaseAndChrome() {
        val posture = FoldPosture(
            isTabletop = true,
            hingeTopDp = 426.dp,
            hingeHeightDp = 0.dp,
            windowHeightDp = 852.dp
        )
        val containerTopDp = 40.dp

        val topPaneHeight = posture.tabletopTopPaneHeight(
            containerTopInWindowDp = containerTopDp,
            maxAvailableHeightDp = 760.dp
        )
        val bottomSheetMaxHeight = posture.tabletopBottomSheetMaxHeight(
            fallbackScreenHeightDp = 852.dp
        )

        assertEquals(426.dp, posture.hingeBottomDp)
        assertEquals(378.dp, topPaneHeight)
        assertEquals(418.dp, containerTopDp + topPaneHeight)
        assertTrue(containerTopDp + topPaneHeight <= posture.hingeTopDp - 8.dp)
        assertEquals(306.dp, bottomSheetMaxHeight)
        assertEquals(852.dp - 120.dp, posture.hingeBottomDp + bottomSheetMaxHeight)
    }

    @Test
    fun galaxyZFlipOrRazrClamshell_tabletopSplitsCleanlyAtHorizontalCrease() {
        val posture = FoldPosture(
            isTabletop = true,
            hingeTopDp = 440.dp,
            hingeHeightDp = 0.dp,
            windowHeightDp = 880.dp
        )
        val containerTopDp = 32.dp

        val topPaneHeight = posture.tabletopTopPaneHeight(
            containerTopInWindowDp = containerTopDp,
            maxAvailableHeightDp = 800.dp
        )
        val bottomSheetMaxHeight = posture.tabletopBottomSheetMaxHeight(
            fallbackScreenHeightDp = 880.dp
        )

        assertEquals(440.dp, posture.hingeBottomDp)
        assertEquals(400.dp, topPaneHeight)
        assertTrue(containerTopDp + topPaneHeight <= posture.hingeTopDp - 8.dp)
        assertEquals(320.dp, bottomSheetMaxHeight)
        assertEquals(880.dp - 120.dp, posture.hingeBottomDp + bottomSheetMaxHeight)
    }

    @Test
    fun dualScreenPhysicalHingeGap_topAndBottomStayClearOfHingeBounds() {
        val posture = FoldPosture(
            isTabletop = true,
            hingeTopDp = 408.dp,
            hingeHeightDp = 24.dp,
            windowHeightDp = 840.dp
        )
        val containerTopDp = 24.dp

        assertEquals(432.dp, posture.hingeBottomDp)

        val topPaneHeight = posture.tabletopTopPaneHeight(
            containerTopInWindowDp = containerTopDp,
            maxAvailableHeightDp = 780.dp
        )
        val bottomSheetMaxHeight = posture.tabletopBottomSheetMaxHeight(
            fallbackScreenHeightDp = 840.dp
        )

        assertEquals(376.dp, topPaneHeight)
        assertTrue(containerTopDp + topPaneHeight < posture.hingeTopDp)
        assertEquals(288.dp, bottomSheetMaxHeight)
        assertTrue(bottomSheetMaxHeight <= 840.dp - posture.hingeBottomDp)
    }

    @Test
    fun fallbackWhenHingeTopIsZero_splitsEvenlyAndClampsToMinHeight() {
        val posture = FoldPosture(
            isTabletop = true,
            hingeTopDp = 0.dp,
            hingeHeightDp = 0.dp,
            windowHeightDp = 800.dp
        )

        val evenSplit = posture.tabletopTopPaneHeight(
            containerTopInWindowDp = 40.dp,
            maxAvailableHeightDp = 600.dp
        )
        assertEquals(300.dp, evenSplit)

        val clampedFromHalf = posture.tabletopTopPaneHeight(
            containerTopInWindowDp = 40.dp,
            maxAvailableHeightDp = 240.dp
        )
        assertEquals(180.dp, clampedFromHalf)

        val clampedWhenMaxBelowMin = posture.tabletopTopPaneHeight(
            containerTopInWindowDp = 40.dp,
            maxAvailableHeightDp = 100.dp
        )
        assertEquals(180.dp, clampedWhenMaxBelowMin)
    }

    @Test
    fun topPaneClampsToMaxAvailableAndBottomSheetUsesFallbackAndMinHeight() {
        val posture = FoldPosture(
            isTabletop = true,
            hingeTopDp = 500.dp,
            hingeHeightDp = 0.dp,
            windowHeightDp = 0.dp
        )

        val clampedToMax = posture.tabletopTopPaneHeight(
            containerTopInWindowDp = 0.dp,
            maxAvailableHeightDp = 420.dp
        )
        assertEquals(420.dp, clampedToMax)

        val bottomWithFallback = posture.tabletopBottomSheetMaxHeight(
            fallbackScreenHeightDp = 900.dp
        )
        assertEquals(280.dp, bottomWithFallback)

        val bottomClampedToMin = posture.tabletopBottomSheetMaxHeight(
            fallbackScreenHeightDp = 700.dp
        )
        assertEquals(220.dp, bottomClampedToMin)
    }
}
