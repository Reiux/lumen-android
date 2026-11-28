package com.reiux.lumen.data.repository

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.reiux.lumen.data.model.WhatsNewCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WhatsNewReadStateTest {

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
    fun onlyLatestReleaseItemsAreUnreadInitially() {
        val emptyRead = settingsRepository.getReadWhatsNewIds()
        assertTrue(emptyRead.isEmpty())
        assertTrue(WhatsNewCatalog.hasUnread(emptyRead))
        assertEquals(WhatsNewCatalog.latestReleaseItemIds, WhatsNewCatalog.unreadItemIds(emptyRead))

        for (latestId in WhatsNewCatalog.latestReleaseItemIds) {
            assertTrue(WhatsNewCatalog.isItemUnread(latestId, emptyRead))
        }

        val olderReleaseIds = WhatsNewCatalog.releases
            .drop(1)
            .flatMap { it.items }
            .map { it.id }
        assertTrue(olderReleaseIds.isNotEmpty())
        for (olderId in olderReleaseIds) {
            assertFalse(WhatsNewCatalog.isItemUnread(olderId, emptyRead))
        }
    }

    @Test
    fun markWhatsNewItemRead_persistsAndRemovesSingleIdFromUnread() {
        val targetId = WhatsNewCatalog.latestReleaseItemIds.first()
        settingsRepository.markWhatsNewItemRead(targetId)

        val readIds = settingsRepository.getReadWhatsNewIds()
        assertTrue(targetId in readIds)
        assertEquals(readIds, settingsRepository.readWhatsNewIds.value)
        assertFalse(WhatsNewCatalog.isItemUnread(targetId, readIds))
        assertFalse(targetId in WhatsNewCatalog.unreadItemIds(readIds))
        assertEquals(WhatsNewCatalog.latestReleaseItemIds - targetId, WhatsNewCatalog.unreadItemIds(readIds))

        val reloaded = SettingsRepository(
            context = DummyContext(),
            sharedPreferencesOverride = fakePrefs
        )
        assertTrue(targetId in reloaded.getReadWhatsNewIds())
        assertFalse(WhatsNewCatalog.isItemUnread(targetId, reloaded.getReadWhatsNewIds()))
    }

    @Test
    fun markAllWhatsNewRead_clearsHasUnreadAndSurvivesReload() {
        val allIds = WhatsNewCatalog.allItemIds()
        settingsRepository.markAllWhatsNewRead(allIds)

        val readIds = settingsRepository.getReadWhatsNewIds()
        assertFalse(WhatsNewCatalog.hasUnread(readIds))
        assertTrue(WhatsNewCatalog.unreadItemIds(readIds).isEmpty())
        assertEquals(allIds, readIds)
        assertEquals(allIds, settingsRepository.readWhatsNewIds.value)

        val reloaded = SettingsRepository(
            context = DummyContext(),
            sharedPreferencesOverride = fakePrefs
        )
        val reloadedIds = reloaded.getReadWhatsNewIds()
        assertFalse(WhatsNewCatalog.hasUnread(reloadedIds))
        assertTrue(WhatsNewCatalog.unreadItemIds(reloadedIds).isEmpty())
        assertEquals(allIds, reloadedIds)
    }

    @Test
    fun allItemIdsAcrossReleasesAreUniqueAndNonBlank() {
        val allItems = WhatsNewCatalog.releases.flatMap { it.items }
        assertTrue(allItems.isNotEmpty())

        val seen = mutableSetOf<String>()
        for (item in allItems) {
            assertTrue("Item ID should not be blank", item.id.isNotBlank())
            assertTrue("Duplicate item ID found: ${item.id}", seen.add(item.id))
        }
        assertEquals(allItems.size, WhatsNewCatalog.allItemIds().size)
    }

    @Test
    fun releasesCatalogCoversFrom134DownTo100With15Releases() {
        val versions = WhatsNewCatalog.releases.map { it.version }
        assertEquals(
            listOf(
                "1.3.4", "1.3.3", "1.3.2", "1.3.1", "1.3.0",
                "1.2.1", "1.2.0",
                "1.1.4", "1.1.3", "1.1.2", "1.1.1", "1.1.0",
                "1.0.2", "1.0.1", "1.0.0"
            ),
            versions
        )
        assertEquals(15, WhatsNewCatalog.releases.size)
        assertEquals("1.3.4", WhatsNewCatalog.releases.first().version)
        assertEquals("1.0.0", WhatsNewCatalog.releases.last().version)
    }

    @Test
    fun shouldShowPostUpdateSheet_handlesOnboardingAndNormalizedVersions() {
        assertFalse(
            WhatsNewCatalog.shouldShowPostUpdateSheet(
                currentAppVersion = "1.3.4-dev.abc1234.20261127",
                lastSeenVersion = null,
                hasCompletedOnboarding = false
            )
        )
        assertTrue(
            WhatsNewCatalog.shouldShowPostUpdateSheet(
                currentAppVersion = "1.3.4-dev.abc1234.20261127",
                lastSeenVersion = null,
                hasCompletedOnboarding = true
            )
        )
        assertFalse(
            WhatsNewCatalog.shouldShowPostUpdateSheet(
                currentAppVersion = "1.3.4-dev.abc1234.20261127",
                lastSeenVersion = "1.3.4",
                hasCompletedOnboarding = true
            )
        )
        assertTrue(
            WhatsNewCatalog.shouldShowPostUpdateSheet(
                currentAppVersion = "1.3.4-dev.abc1234.20261127",
                lastSeenVersion = "1.3.3",
                hasCompletedOnboarding = true
            )
        )
    }

    @Test
    fun setLastSeenWhatsNewVersion_normalizesAndRoundTripsAcrossReload() {
        assertNull(settingsRepository.getLastSeenWhatsNewVersion())

        settingsRepository.setLastSeenWhatsNewVersion("1.3.4-dev.abc1234.20261127")
        assertEquals("1.3.4", settingsRepository.getLastSeenWhatsNewVersion())

        val reloaded = SettingsRepository(
            context = DummyContext(),
            sharedPreferencesOverride = fakePrefs
        )
        assertEquals("1.3.4", reloaded.getLastSeenWhatsNewVersion())
    }
}
