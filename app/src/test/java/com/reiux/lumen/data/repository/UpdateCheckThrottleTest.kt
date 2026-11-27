package com.reiux.lumen.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.reiux.lumen.BuildConfig
import com.reiux.lumen.data.model.UpdateInfo
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class UpdateCheckThrottleTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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

    @Test
    fun testShouldNotifyForVersion_neverNotified_returnsTrue() {
        assertTrue(UpdateRepository.shouldNotifyForVersion("1.4.0", null))
    }

    @Test
    fun testShouldNotifyForVersion_sameVersionAlreadyNotified_returnsFalse() {
        assertFalse(UpdateRepository.shouldNotifyForVersion("1.4.0", "1.4.0"))
    }

    @Test
    fun testShouldNotifyForVersion_newerReleaseAfterPreviousNotification_returnsTrue() {
        assertTrue(UpdateRepository.shouldNotifyForVersion("1.5.0", "1.4.0"))
    }

    @Test
    fun testShouldNotifyForVersion_blankVersion_returnsFalse() {
        assertFalse(UpdateRepository.shouldNotifyForVersion("", null))
    }

    @Test
    fun testSettingsRepository_lastNotifiedUpdateVersion_roundTrip() {
        assertEquals(null, settingsRepository.getLastNotifiedUpdateVersion())
        settingsRepository.setLastNotifiedUpdateVersion("1.4.0")
        assertEquals("1.4.0", settingsRepository.getLastNotifiedUpdateVersion())
    }

    // -------------------------------------------------------------------------
    // 1. Foreground check timestamp isolation
    // -------------------------------------------------------------------------

    @Test
    fun testSettingsRepository_lastForegroundUpdateCheckMillis_defaultsToZero() {
        assertEquals(0L, settingsRepository.getLastForegroundUpdateCheckMillis())
    }

    @Test
    fun testSettingsRepository_backgroundCheckDoesNotAffectForegroundCheck() {
        val now = 1_700_000_000_000L
        settingsRepository.setLastUpdateCheckMillis(now)

        assertEquals(now, settingsRepository.getLastUpdateCheckMillis())
        assertEquals(0L, settingsRepository.getLastForegroundUpdateCheckMillis())
        assertTrue(
            UpdateRepository.shouldAutoCheck(
                lastCheckMillis = settingsRepository.getLastForegroundUpdateCheckMillis(),
                nowMillis = now
            )
        )
    }

    @Test
    fun testSettingsRepository_lastForegroundUpdateCheckMillis_roundTrip() {
        val ts = 1_700_000_123_456L
        settingsRepository.setLastForegroundUpdateCheckMillis(ts)
        assertEquals(ts, settingsRepository.getLastForegroundUpdateCheckMillis())
        assertEquals(0L, settingsRepository.getLastUpdateCheckMillis())
    }

    // -------------------------------------------------------------------------
    // 2. Pending update persistence
    // -------------------------------------------------------------------------

    @Test
    fun testSettingsRepository_pendingUpdateInfo_roundTrip() {
        assertNull(settingsRepository.getPendingUpdateInfo())

        val validSha = "a".repeat(64)
        val info = UpdateInfo(
            versionName = "1.4.0",
            releaseNotes = "Bug fixes and improvements",
            downloadUrl = "https://github.com/Reiux/lumen-android/releases/download/v1.4.0/lumen-release.apk",
            isUpdateAvailable = true,
            expectedSha256 = validSha
        )
        settingsRepository.savePendingUpdateInfo(info)

        val loaded = settingsRepository.getPendingUpdateInfo()
        assertNotNull(loaded)
        assertEquals("1.4.0", loaded!!.versionName)
        assertEquals("Bug fixes and improvements", loaded.releaseNotes)
        assertEquals(info.downloadUrl, loaded.downloadUrl)
        assertTrue(loaded.isUpdateAvailable)
        assertEquals(validSha, loaded.expectedSha256)
    }

    @Test
    fun testSettingsRepository_pendingUpdateInfo_ignoresInvalidEntries() {
        val validUrl = "https://github.com/Reiux/lumen-android/releases/download/v1.4.0/lumen-release.apk"

        // isUpdateAvailable == false
        settingsRepository.savePendingUpdateInfo(
            UpdateInfo(
                versionName = "1.4.0",
                releaseNotes = "notes",
                downloadUrl = validUrl,
                isUpdateAvailable = false,
                expectedSha256 = "b".repeat(64)
            )
        )
        assertNull(settingsRepository.getPendingUpdateInfo())
        assertFalse(fakePrefs.contains("pending_update_info_json"))

        // blank versionName
        settingsRepository.savePendingUpdateInfo(
            UpdateInfo(
                versionName = "   ",
                releaseNotes = "notes",
                downloadUrl = validUrl,
                isUpdateAvailable = true,
                expectedSha256 = "b".repeat(64)
            )
        )
        assertNull(settingsRepository.getPendingUpdateInfo())
        assertFalse(fakePrefs.contains("pending_update_info_json"))

        // untrusted host
        settingsRepository.savePendingUpdateInfo(
            UpdateInfo(
                versionName = "1.4.0",
                releaseNotes = "notes",
                downloadUrl = "https://evil.example.com/app.apk",
                isUpdateAvailable = true,
                expectedSha256 = "b".repeat(64)
            )
        )
        assertNull(settingsRepository.getPendingUpdateInfo())
        assertFalse(fakePrefs.contains("pending_update_info_json"))

        // insecure http scheme
        settingsRepository.savePendingUpdateInfo(
            UpdateInfo(
                versionName = "1.4.0",
                releaseNotes = "notes",
                downloadUrl = "http://github.com/Reiux/lumen-android/releases/download/v1.4.0/lumen-release.apk",
                isUpdateAvailable = true,
                expectedSha256 = "b".repeat(64)
            )
        )
        assertNull(settingsRepository.getPendingUpdateInfo())
        assertFalse(fakePrefs.contains("pending_update_info_json"))
    }

    @Test
    fun testSettingsRepository_pendingUpdateInfo_corruptJsonOrUntrustedUrlCleansUp() {
        fakePrefs.putDirect("pending_update_info_json", "{not-valid-json")
        assertNull(settingsRepository.getPendingUpdateInfo())
        assertFalse(fakePrefs.contains("pending_update_info_json"))

        fakePrefs.putDirect(
            "pending_update_info_json",
            """{"versionName":"1.4.0","releaseNotes":"notes","downloadUrl":"https://evil.example.com/app.apk"}"""
        )
        assertNull(settingsRepository.getPendingUpdateInfo())
        assertFalse(fakePrefs.contains("pending_update_info_json"))
    }

    @Test
    fun testSettingsRepository_clearPendingUpdateInfo_removesStoredUpdate() {
        val info = UpdateInfo(
            versionName = "1.4.0",
            releaseNotes = "Release notes",
            downloadUrl = "https://github.com/Reiux/lumen-android/releases/download/v1.4.0/lumen-release.apk",
            isUpdateAvailable = true,
            expectedSha256 = "c".repeat(64)
        )
        settingsRepository.savePendingUpdateInfo(info)
        assertNotNull(settingsRepository.getPendingUpdateInfo())

        settingsRepository.clearPendingUpdateInfo()
        assertNull(settingsRepository.getPendingUpdateInfo())
        assertFalse(fakePrefs.contains("pending_update_info_json"))
    }

    // -------------------------------------------------------------------------
    // 3. Dismissed update snooze persistence
    // -------------------------------------------------------------------------

    @Test
    fun testSettingsRepository_dismissedUpdate_defaultsRoundTripAndClear() {
        assertNull(settingsRepository.getDismissedUpdateVersion())
        assertEquals(0L, settingsRepository.getDismissedUpdateMillis())

        val ts = 1_700_000_555_000L
        settingsRepository.setDismissedUpdate("1.3.3", ts)
        assertEquals("1.3.3", settingsRepository.getDismissedUpdateVersion())
        assertEquals(ts, settingsRepository.getDismissedUpdateMillis())

        settingsRepository.clearDismissedUpdate()
        assertNull(settingsRepository.getDismissedUpdateVersion())
        assertEquals(0L, settingsRepository.getDismissedUpdateMillis())
    }

    // -------------------------------------------------------------------------
    // 4. UpdateRepository.shouldPromptInAppForUpdate
    // -------------------------------------------------------------------------

    @Test
    fun testShouldPromptInAppForUpdate_blankVersion_returnsFalse() {
        assertFalse(
            UpdateRepository.shouldPromptInAppForUpdate(
                latestVersion = "   ",
                dismissedVersion = null,
                dismissedAtMillis = 0L,
                nowMillis = 1_000_000_000_000L
            )
        )
    }

    @Test
    fun testShouldPromptInAppForUpdate_neverDismissed_returnsTrue() {
        assertTrue(
            UpdateRepository.shouldPromptInAppForUpdate(
                latestVersion = "1.4.0",
                dismissedVersion = null,
                dismissedAtMillis = 0L,
                nowMillis = 1_000_000_000_000L
            )
        )
    }

    @Test
    fun testShouldPromptInAppForUpdate_newerVersionArrivesImmediatelyAfterDismiss_returnsTrue() {
        val now = 1_000_000_000_000L
        val oneMinuteAgo = now - (60 * 1000L)
        assertTrue(
            UpdateRepository.shouldPromptInAppForUpdate(
                latestVersion = "1.3.4",
                dismissedVersion = "1.3.3",
                dismissedAtMillis = oneMinuteAgo,
                nowMillis = now
            )
        )
    }

    @Test
    fun testShouldPromptInAppForUpdate_sameVersionOneHourAgo_returnsFalse() {
        val now = 1_000_000_000_000L
        val oneHourAgo = now - (60 * 60 * 1000L)
        assertFalse(
            UpdateRepository.shouldPromptInAppForUpdate(
                latestVersion = "1.3.3",
                dismissedVersion = "1.3.3",
                dismissedAtMillis = oneHourAgo,
                nowMillis = now
            )
        )
    }

    @Test
    fun testShouldPromptInAppForUpdate_sameVersionTwentyFourHoursAgo_returnsTrue() {
        val now = 1_000_000_000_000L
        val twentyFourHoursAgo = now - UpdateRepository.UPDATE_DISMISS_SNOOZE_MILLIS
        assertTrue(
            UpdateRepository.shouldPromptInAppForUpdate(
                latestVersion = "1.3.3",
                dismissedVersion = "1.3.3",
                dismissedAtMillis = twentyFourHoursAgo,
                nowMillis = now
            )
        )
    }

    @Test
    fun testShouldPromptInAppForUpdate_clockSkewInFuture_returnsTrue() {
        val now = 1_000_000_000_000L
        val futureDismiss = now + 5_000L
        assertTrue(
            UpdateRepository.shouldPromptInAppForUpdate(
                latestVersion = "1.3.3",
                dismissedVersion = "1.3.3",
                dismissedAtMillis = futureDismiss,
                nowMillis = now
            )
        )
    }

    // -------------------------------------------------------------------------
    // 5. Uppercase "V" tag prefix, expectedSha256, and cross-instance override
    // -------------------------------------------------------------------------

    @Test
    fun testUpdateRepository_uppercaseVTagAndExpectedSha256OverrideAcrossInstances() {
        val expectedAssetName = if (BuildConfig.DEBUG) "lumen-test-debug.apk" else "lumen-release.apk"
        val realBytes = "lumen-valid-apk-bytes-1.4.0".toByteArray()
        val realSha256 = MessageDigest.getInstance("SHA-256")
            .digest(realBytes)
            .joinToString("") { "%02x".format(it) }

        val checkReleaseJson = """
            {
              "tag_name": "V1.4.0",
              "body": "Release notes\n$realSha256  $expectedAssetName",
              "assets": [
                {
                  "name": "$expectedAssetName",
                  "browser_download_url": "https://github.com/Reiux/lumen-android/releases/download/V1.4.0/$expectedAssetName"
                }
              ]
            }
        """.trimIndent()

        val checkClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(checkReleaseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val checkRepo = UpdateRepository(checkClient)
        val updateInfo = runBlocking { checkRepo.checkForUpdates("1.3.3") }

        assertNotNull(updateInfo)
        assertEquals("1.4.0", updateInfo!!.versionName)
        assertTrue(updateInfo.isUpdateAvailable)
        assertEquals(realSha256, updateInfo.expectedSha256)

        // Simulate a post-restart download with a brand-new UpdateRepository instance
        // and a distinct release tag URL that is NOT in UpdateRepository's in-memory expectedSha256ByUrl cache.
        val distinctPostRestartUrl =
            "https://github.com/Reiux/lumen-android/releases/download/V1.4.0-persisted/$expectedAssetName"

        var serveTampered = true
        val downloadClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val payload = if (serveTampered) "tampered-apk-bytes".toByteArray() else realBytes
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                    .build()
            })
            .build()

        val postRestartRepo = UpdateRepository(downloadClient)
        val tamperedDest = File(tempFolder.newFolder("upd_tampered"), "lumen.apk")
        try {
            runBlocking {
                postRestartRepo.downloadUpdate(
                    url = distinctPostRestartUrl,
                    destFile = tamperedDest,
                    expectedSha256Override = updateInfo.expectedSha256
                ).collect()
            }
            fail("Expected SecurityException for tampered APK when expectedSha256Override is supplied")
        } catch (_: SecurityException) {
            assertFalse(File(tamperedDest.parentFile, "${tamperedDest.name}.part").exists())
            assertFalse(tamperedDest.exists())
        }

        serveTampered = false
        val validDest = File(tempFolder.newFolder("upd_valid"), "lumen.apk")
        runBlocking {
            postRestartRepo.downloadUpdate(
                url = distinctPostRestartUrl,
                destFile = validDest,
                expectedSha256Override = updateInfo.expectedSha256
            ).collect()
        }
        assertTrue(validDest.exists())
        assertArrayEquals(realBytes, validDest.readBytes())
    }

    @Test
    fun testUpdateRepository_malformedExpectedSha256Override_failsClosed() {
        val repo = UpdateRepository(OkHttpClient.Builder().build())
        val dest = File(tempFolder.newFolder("upd_bad_sha"), "lumen.apk")
        try {
            runBlocking {
                repo.downloadUpdate(
                    url = "https://github.com/Reiux/lumen-android/releases/download/v1.4.0/lumen-release.apk",
                    destFile = dest,
                    expectedSha256Override = "not-a-valid-sha256"
                ).collect()
            }
            fail("Expected SecurityException for malformed expectedSha256Override")
        } catch (_: SecurityException) {
            assertFalse(dest.exists())
        }
    }

    @Test
    fun testSettingsRepository_pendingUpdateInfo_rejectsNullVersionAndMalformedSha256() {
        fakePrefs.putDirect(
            "pending_update_info_json",
            """{"versionName":null,"releaseNotes":"notes","downloadUrl":"https://github.com/Reiux/lumen-android/releases/download/v1.4.0/lumen-release.apk"}"""
        )
        assertNull(settingsRepository.getPendingUpdateInfo())
        assertNull(fakePrefs.getString("pending_update_info_json", null))

        fakePrefs.putDirect(
            "pending_update_info_json",
            """{"versionName":"1.4.0","releaseNotes":"notes","downloadUrl":"https://github.com/Reiux/lumen-android/releases/download/v1.4.0/lumen-release.apk","expectedSha256":"bad-hex"}"""
        )
        assertNull(settingsRepository.getPendingUpdateInfo())
        assertNull(fakePrefs.getString("pending_update_info_json", null))
    }

    @Test
    fun testIsNewerVersion_handlesDevHashDateSuffix() {
        assertFalse(UpdateRepository.isNewerVersion("1.3.3-dev.5c831ca.20261125", "1.3.3"))
        assertTrue(UpdateRepository.isNewerVersion("1.3.3-dev.5c831ca.20261125", "1.3.4"))
        assertFalse(UpdateRepository.isNewerVersion("1.3.3-nightly.20261125.5c831ca", "1.3.3"))
        assertTrue(UpdateRepository.isNewerVersion("1.3.3-nightly.20261125.5c831ca", "1.4.0"))
    }
}
