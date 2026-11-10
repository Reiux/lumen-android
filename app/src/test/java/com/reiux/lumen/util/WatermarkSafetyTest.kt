package com.reiux.lumen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermarkSafetyTest {

    @Test
    fun allReady_advancesToMaxSeen() {
        assertEquals(120L, FolderWatcherManager.computeSafeWatermark(lastWatermark = 100L, maxSeenId = 120L, firstNotReadyId = null))
    }

    @Test
    fun pendingItem_capsWatermarkJustBeforeIt() {
        // Video 105 still recording, photo 110 ready: watermark must stop at 104 so 105 is picked up later
        assertEquals(104L, FolderWatcherManager.computeSafeWatermark(lastWatermark = 100L, maxSeenId = 110L, firstNotReadyId = 105L))
    }

    @Test
    fun neverMovesBackwards() {
        assertEquals(100L, FolderWatcherManager.computeSafeWatermark(lastWatermark = 100L, maxSeenId = 100L, firstNotReadyId = 101L))
    }

    @Test
    fun recentNotReadyItem_holdsWatermark() {
        val now = 1_000_000L
        assertTrue(FolderWatcherManager.shouldHoldWatermark(fileReady = false, dateAddedSec = now - 60, nowSec = now))
    }

    @Test
    fun readyItem_doesNotHold() {
        val now = 1_000_000L
        assertFalse(FolderWatcherManager.shouldHoldWatermark(fileReady = true, dateAddedSec = now - 60, nowSec = now))
    }

    @Test
    fun abandonedNotReadyItem_doesNotHoldForever() {
        val now = 1_000_000L
        assertFalse(FolderWatcherManager.shouldHoldWatermark(fileReady = false, dateAddedSec = now - 7 * 3600, nowSec = now))
    }
}
