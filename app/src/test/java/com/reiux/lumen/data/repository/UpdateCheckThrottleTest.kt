package com.reiux.lumen.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateCheckThrottleTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var settingsRepository: SettingsRepository

    private class DummyContext : ContextWrapper(null) {
        private val prefsMap = mutableMapOf<String, SharedPreferences>()
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            return prefsMap.getOrPut(name ?: "default") { FakeSharedPreferences() }
        }
    }

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        settingsRepository = SettingsRepository(
            context = DummyContext(),
            sharedPreferencesOverride = fakePrefs
        )
    }

    @Test
    fun testShouldAutoCheck_lastZero_returnsTrue() {
        val now = 1_000_000_000_000L
        assertTrue(UpdateRepository.shouldAutoCheck(lastCheckMillis = 0L, nowMillis = now))
    }

    @Test
    fun testShouldAutoCheck_oneHourAgo_returnsFalse() {
        val now = 1_000_000_000_000L
        val oneHourAgo = now - (60 * 60 * 1000L)
        assertFalse(UpdateRepository.shouldAutoCheck(lastCheckMillis = oneHourAgo, nowMillis = now))
    }

    @Test
    fun testShouldAutoCheck_exactlyTwelveHoursAgo_returnsTrue() {
        val now = 1_000_000_000_000L
        val twelveHoursAgo = now - (12 * 60 * 60 * 1000L)
        assertTrue(UpdateRepository.shouldAutoCheck(lastCheckMillis = twelveHoursAgo, nowMillis = now))
    }

    @Test
    fun testShouldAutoCheck_thirteenHoursAgo_returnsTrue() {
        val now = 1_000_000_000_000L
        val thirteenHoursAgo = now - (13 * 60 * 60 * 1000L)
        assertTrue(UpdateRepository.shouldAutoCheck(lastCheckMillis = thirteenHoursAgo, nowMillis = now))
    }

    @Test
    fun testShouldAutoCheck_lastInFuture_returnsTrue() {
        val now = 1_000_000_000_000L
        val future = now + 5000L
        assertTrue(UpdateRepository.shouldAutoCheck(lastCheckMillis = future, nowMillis = now))
    }

    @Test
    fun testShouldAutoCheck_customInterval() {
        val now = 1_000_000_000_000L
        val thirtyMinsAgo = now - (30 * 60 * 1000L)
        val customInterval = 15 * 60 * 1000L
        assertTrue(UpdateRepository.shouldAutoCheck(lastCheckMillis = thirtyMinsAgo, nowMillis = now, intervalMillis = customInterval))
        
        val tenMinsAgo = now - (10 * 60 * 1000L)
        assertFalse(UpdateRepository.shouldAutoCheck(lastCheckMillis = tenMinsAgo, nowMillis = now, intervalMillis = customInterval))
    }

    @Test
    fun testSettingsRepository_lastUpdateCheckMillis_roundTrip() {
        assertEquals(0L, settingsRepository.getLastUpdateCheckMillis())
        val testMillis = 123456789L
        settingsRepository.setLastUpdateCheckMillis(testMillis)
        assertEquals(testMillis, settingsRepository.getLastUpdateCheckMillis())
    }
}
