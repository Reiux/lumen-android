package com.reiux.lumen.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.reiux.lumen.data.model.AuthFailureKind
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.model.UploadItem
import com.reiux.lumen.data.model.UploadStatus
import com.reiux.lumen.data.network.GoogleAlbum
import com.reiux.lumen.data.network.GooglePhotosClient
import com.reiux.lumen.util.AuthFailureException
import com.reiux.lumen.util.ErrorClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class UploadLogicTest {

    private class TestContext : ContextWrapper(null) {
        private val prefsMap = mutableMapOf<String, FakeSharedPreferences>()
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            return prefsMap.getOrPut(name ?: "default") { FakeSharedPreferences() }
        }
        override fun getApplicationContext(): Context = this
    }

    private fun newSettingsRepository(): SettingsRepository {
        val ctx = TestContext()
        val prefs = ctx.getSharedPreferences("lumen_settings", Context.MODE_PRIVATE)
        return SettingsRepository(context = ctx, sharedPreferencesOverride = prefs)
    }

    @Test
    fun testComputeRetryDelayMsWithJitter() {
        val attempt1Delay = UploadRepository.computeRetryDelayMs(1)
        assertTrue("Attempt 1 delay should be between 2250 and 3000 ms, was $attempt1Delay", attempt1Delay in 2250L..3000L)

        val attempt2Delay = UploadRepository.computeRetryDelayMs(2)
        assertTrue("Attempt 2 delay should be between 4250 and 5000 ms, was $attempt2Delay", attempt2Delay in 4250L..5000L)

        val maxAttemptDelay = UploadRepository.computeRetryDelayMs(5)
        assertTrue("Max attempt delay should be capped at base 6000 + jitter (up to 1000), was $maxAttemptDelay", maxAttemptDelay in 6250L..7000L)
    }

    @Test
    fun testShouldAttemptCommitRecovery() {
        assertTrue(UploadRepository.shouldAttemptCommitRecovery(IOException("Network error"), UploadStatus.COMMITTING))
        assertTrue(UploadRepository.shouldAttemptCommitRecovery(SocketTimeoutException("Timeout"), UploadStatus.COMMITTING))
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.ACCOUNT_RESTRICTED), UploadStatus.COMMITTING))
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.SIGN_IN_REQUIRED), UploadStatus.COMMITTING))
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.RATE_LIMITED), UploadStatus.COMMITTING))
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.TOKEN_EXPIRED), UploadStatus.COMMITTING))
        assertTrue(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.TRANSIENT), UploadStatus.COMMITTING))
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(IOException("Network error"), UploadStatus.UPLOADING))
    }

    @Test
    fun testAlbumAssignmentDecisionLogic() {
        val photosClient = GooglePhotosClient()
        val settingsRepo = newSettingsRepository()
        val account = "user@gmail.com"

        // 1. Direct media key and shared URL extract cleanly via production GooglePhotosClient
        val directKey = "AF1QipTestMediaKey12345"
        assertEquals(directKey, photosClient.resolveAlbumInput("  $directKey  "))
        assertEquals(
            "AF1QipUrlAlbumKey98765",
            photosClient.resolveAlbumInput("https://photos.google.com/album/AF1QipUrlAlbumKey98765")
        )

        // 2. Plain album title stays as title until saved to SettingsRepository
        val plainTitle = photosClient.resolveAlbumInput("Vacation 2025")
        assertEquals("Vacation 2025", plainTitle)
        assertNull(settingsRepo.getAlbumMediaKey(account, plainTitle))

        // 3. Saving created album key persists and resolves case-insensitively on subsequent lookups
        val createdKey = "AF1QipCreatedVacationKey2025"
        settingsRepo.saveAlbumMediaKey(account, plainTitle, createdKey)
        assertEquals(createdKey, settingsRepo.getAlbumMediaKey("USER@gmail.com", "  vacation   2025 "))
    }

    @Test
    fun testPickerFilterByAccount() {
        val settingsRepo = newSettingsRepository()
        settingsRepo.saveAlbumMediaKey("personal@gmail.com", "Personal Album 1", "AF1QipKeyPersonal1")
        settingsRepo.saveAlbumMediaKey("secondary@gmail.com", "Secondary Album 1", "AF1QipKeySecondary1")

        val personalFiltered = settingsRepo.getRecentAlbums("personal@gmail.com")
        assertEquals(1, personalFiltered.size)
        assertEquals("AF1QipKeyPersonal1", personalFiltered[0].mediaKey)
        assertEquals("personal@gmail.com", personalFiltered[0].accountEmail)

        val secondaryFiltered = settingsRepo.getRecentAlbums("secondary@gmail.com")
        assertEquals(1, secondaryFiltered.size)
        assertEquals("AF1QipKeySecondary1", secondaryFiltered[0].mediaKey)
        assertEquals("secondary@gmail.com", secondaryFiltered[0].accountEmail)

        val unassignedFiltered = settingsRepo.getRecentAlbums("other@gmail.com")
        assertTrue(unassignedFiltered.isEmpty())
    }

    @Test
    fun testAccountMismatchValidation() {
        val settingsRepo = newSettingsRepository()
        settingsRepo.saveAlbumMediaKey("secondary@gmail.com", "Secondary Album", "AF1QipKeySecondary1")

        // Album saved under secondary@gmail.com must not resolve for personal@gmail.com
        assertNull(settingsRepo.getAlbumMediaKey("personal@gmail.com", "Secondary Album"))
        assertEquals("AF1QipKeySecondary1", settingsRepo.getAlbumMediaKey("secondary@gmail.com", "Secondary Album"))

        val validAlbum = GoogleAlbum(
            mediaKey = "AF1QipKeySecondary1",
            title = "Secondary Album",
            accountEmail = "secondary@gmail.com"
        )
        assertEquals("Key ending ...ndary1", validAlbum.getSubtitle())

        val missingAlbum = validAlbum.copy(isMissing = true)
        assertEquals("Missing in Google Photos", missingAlbum.getSubtitle())
    }

    @Test
    fun testErrorClassification404VsOther() {
        val err404 = ErrorClassifier.classify(Exception("AddMediaToAlbum failed HTTP 404: Requested entity was not found"), httpCode = 404)
        assertNotEquals("AUTH_EXPIRED", err404.reasonCode)
        assertNotEquals("RATE_LIMITED", err404.reasonCode)
        assertNotEquals("SERVER_ERROR", err404.reasonCode)

        val err401 = ErrorClassifier.classify(Exception("HTTP 401 Unauthorized"), httpCode = 401)
        assertEquals("AUTH_EXPIRED", err401.reasonCode)

        val err429 = ErrorClassifier.classify(Exception("HTTP 429 Too Many Requests"), httpCode = 429)
        assertEquals("RATE_LIMITED", err429.reasonCode)

        val err500 = ErrorClassifier.classify(Exception("HTTP 500 Internal Server Error"), httpCode = 500)
        assertEquals("SERVER_ERROR", err500.reasonCode)

        val errTimeout = ErrorClassifier.classify(SocketTimeoutException("Socket closed"))
        assertEquals("NETWORK_TIMEOUT", errTimeout.reasonCode)
    }

    @Test
    fun testChoosingAccountPerItem() {
        val personalFolder = BackupFolder(
            path = "/sdcard/DCIM/Personal",
            displayName = "Test folder P",
            enabled = true,
            accountEmail = "personal@gmail.com"
        )
        val secondaryFolder = BackupFolder(
            path = "/sdcard/DCIM/Secondary",
            displayName = "Test folder S",
            enabled = true,
            accountEmail = "secondary@gmail.com"
        )

        val personalItem = UploadItem(
            id = "item_1",
            filePath = "/sdcard/DCIM/Personal/photo1.jpg",
            fileName = "photo1.jpg",
            fileSize = 1024L,
            accountEmail = personalFolder.accountEmail
        )

        val secondaryItem = UploadItem(
            id = "item_2",
            filePath = "/sdcard/DCIM/Secondary/photo2.jpg",
            fileName = "photo2.jpg",
            fileSize = 2048L,
            accountEmail = secondaryFolder.accountEmail
        )

        assertEquals("personal@gmail.com", personalItem.accountEmail)
        assertEquals("secondary@gmail.com", secondaryItem.accountEmail)
    }

    @Test
    fun testPerAccountDuplicateKeys() {
        val settingsRepo = newSettingsRepository()
        val folderPath = "/sdcard/DCIM/Camera"

        // Watermark and album keys stored for Personal account must not bleed into Secondary account
        settingsRepo.saveFolderWatermarkId("personal@gmail.com", folderPath, 4200L)
        settingsRepo.saveAlbumMediaKey("personal@gmail.com", "Camera Roll", "AF1QipMediaKeyPersonal123")

        assertEquals(4200L, settingsRepo.getFolderWatermarkId("PERSONAL@gmail.com", folderPath))
        assertEquals(0L, settingsRepo.getFolderWatermarkId("secondary@gmail.com", folderPath))

        assertEquals("AF1QipMediaKeyPersonal123", settingsRepo.getAlbumMediaKey("personal@gmail.com", "Camera Roll"))
        assertNull(settingsRepo.getAlbumMediaKey("secondary@gmail.com", "Camera Roll"))
    }

    @Test
    fun testPerAccountFolderSelection() {
        val settingsRepo = newSettingsRepository()
        settingsRepo.saveFolders(
            listOf(
                BackupFolder("/sdcard/DCIM/P", "Personal DCIM", enabled = true, accountEmail = "personal@gmail.com"),
                BackupFolder("/sdcard/DCIM/S", "Secondary DCIM", enabled = true, accountEmail = "secondary@gmail.com")
            )
        )

        val personalFolders = settingsRepo.getFoldersForAccount("personal@gmail.com")
        assertEquals(1, personalFolders.size)
        assertEquals("Personal DCIM", personalFolders[0].displayName)

        val secondaryFolders = settingsRepo.getFoldersForAccount("secondary@gmail.com")
        assertEquals(1, secondaryFolders.size)
        assertEquals("Secondary DCIM", secondaryFolders[0].displayName)

        settingsRepo.addFolder(BackupFolder("/sdcard/Download", "Downloads", enabled = true, accountEmail = null))
        val unassignedFolders = settingsRepo.getFoldersForAccount(null)
        assertEquals(1, unassignedFolders.size)
        assertEquals("Downloads", unassignedFolders[0].displayName)
    }

    @Test
    fun testMultiAccountSharedFolders() {
        val settingsRepo = newSettingsRepository()
        settingsRepo.saveFolders(emptyList())

        val path = "/sdcard/DCIM/Camera"
        val folderAccountA = BackupFolder(path = path, displayName = "Camera Roll", accountEmail = "accA@gmail.com")
        val folderAccountB = BackupFolder(path = path, displayName = "Camera Roll", accountEmail = "accB@gmail.com")

        settingsRepo.addFolderForAccount("accA@gmail.com", folderAccountA)
        assertEquals(1, settingsRepo.getAllFolders().size)

        settingsRepo.addFolderForAccount("accB@gmail.com", folderAccountB)
        assertEquals(2, settingsRepo.getAllFolders().size)

        val accAFolder = settingsRepo.getAllFolders().find { it.accountEmail == "accA@gmail.com" }
        val accBFolder = settingsRepo.getAllFolders().find { it.accountEmail == "accB@gmail.com" }
        assertNotNull(accAFolder)
        assertNotNull(accBFolder)
        assertEquals(path, accAFolder?.path)
        assertEquals(path, accBFolder?.path)
    }

    @Test
    fun testActiveUploadingItemReflectsStreamedBytes() {
        val item = UploadItem(
            id = "test-1",
            filePath = "/sdcard/DCIM/sample.mp4",
            fileName = "sample.mp4",
            fileSize = 100_000L,
            bytesUploaded = 0L,
            status = UploadStatus.UPLOADING,
            accountEmail = "user@gmail.com"
        )
        val updated = item.copy(bytesUploaded = 50_000L)
        assertEquals(0L, item.bytesUploaded)
        assertEquals(50_000L, updated.bytesUploaded)
        assertEquals(UploadStatus.UPLOADING, updated.status)
    }

    @Test
    fun testComputeAlbumSyncBatchesDeduplicatesAndChunks() {
        val empty = UploadRepository.computeAlbumSyncBatches(emptyList())
        assertTrue(empty.isEmpty())

        val keys = (1..120).map { "AF1Qip_key_$it" } + listOf("AF1Qip_key_1", "AF1Qip_key_2")
        val batches = UploadRepository.computeAlbumSyncBatches(keys, batchSize = 50)
        assertEquals(3, batches.size)
        assertEquals(50, batches[0].size)
        assertEquals(50, batches[1].size)
        assertEquals(20, batches[2].size)
    }

    @Test
    fun testFolderPathNormalizationMatchesSdcardAndEmulatedZero() {
        assertTrue(
            SettingsRepository.matchesFolderPath(
                "/sdcard/DCIM/Camera/",
                "/storage/emulated/0/DCIM/Camera"
            )
        )
        assertFalse(
            SettingsRepository.matchesFolderPath(
                "/sdcard/Download",
                "/sdcard/Download2"
            )
        )
        assertEquals(
            "pictures/screenshots",
            SettingsRepository.normalizeFolderPath("/storage/emulated/0/Pictures/Screenshots/")
        )
    }
}
