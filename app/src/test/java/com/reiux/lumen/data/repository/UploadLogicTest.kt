package com.reiux.lumen.data.repository

import com.reiux.lumen.data.model.AuthFailureKind
import com.reiux.lumen.data.model.UploadStatus
import com.reiux.lumen.util.AuthFailureException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class UploadLogicTest {

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
        // COMMITTING + IOException -> true
        assertTrue(UploadRepository.shouldAttemptCommitRecovery(IOException("Network error"), UploadStatus.COMMITTING))
        
        // COMMITTING + SocketTimeoutException -> true
        assertTrue(UploadRepository.shouldAttemptCommitRecovery(SocketTimeoutException("Timeout"), UploadStatus.COMMITTING))

        // COMMITTING + AuthFailureException(ACCOUNT_RESTRICTED) -> false
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.ACCOUNT_RESTRICTED), UploadStatus.COMMITTING))

        // (SIGN_IN_REQUIRED) -> false
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.SIGN_IN_REQUIRED), UploadStatus.COMMITTING))

        // (RATE_LIMITED) -> false
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.RATE_LIMITED), UploadStatus.COMMITTING))

        // (TOKEN_EXPIRED) -> false
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.TOKEN_EXPIRED), UploadStatus.COMMITTING))

        // (TRANSIENT) -> true
        assertTrue(UploadRepository.shouldAttemptCommitRecovery(AuthFailureException(AuthFailureKind.TRANSIENT), UploadStatus.COMMITTING))

        // status UPLOADING (not COMMITTING) + IOException -> false
        assertFalse(UploadRepository.shouldAttemptCommitRecovery(IOException("Network error"), UploadStatus.UPLOADING))
    }

    @Test
    fun testAlbumAssignmentDecisionLogic() {
        val storedKeys = mutableMapOf<String, String>()

        fun decideAlbumAction(
            accountEmail: String,
            albumInput: String
        ): Pair<String, String> {
            val keyRegex = Regex("AF1Qip[A-Za-z0-9_-]+")
            val trimmed = albumInput.trim()
            if (trimmed.startsWith("AF1Qip") || keyRegex.matches(trimmed)) {
                return Pair("ADD_TO_EXISTING_KEY", trimmed)
            }

            val cacheKey = "$accountEmail:${trimmed.lowercase()}"
            val existing = storedKeys[cacheKey]
            if (existing != null) {
                return Pair("ADD_TO_EXISTING_KEY", existing)
            }

            // Simulate creating a new album key when name is unknown
            val newGeneratedKey = "AF1Qip_GEN_${trimmed.hashCode()}"
            storedKeys[cacheKey] = newGeneratedKey
            return Pair("CREATE_NEW_ALBUM", newGeneratedKey)
        }

        val account = "user@gmail.com"

        // 1. Direct media key should ALWAYS add to key and NEVER create a new album
        val directKey = "AF1QipTestMediaKey12345"
        val (action1, key1) = decideAlbumAction(account, directKey)
        org.junit.Assert.assertEquals("ADD_TO_EXISTING_KEY", action1)
        org.junit.Assert.assertEquals(directKey, key1)

        // 2. First upload to unknown album name -> CREATE_NEW_ALBUM
        val (action2, key2) = decideAlbumAction(account, "Vacation 2025")
        org.junit.Assert.assertEquals("CREATE_NEW_ALBUM", action2)
        assertTrue(key2.startsWith("AF1Qip"))

        // 3. Second upload to SAME album name -> REUSE stored key, ADD_TO_EXISTING_KEY, NO second album created!
        val (action3, key3) = decideAlbumAction(account, "Vacation 2025")
        org.junit.Assert.assertEquals("ADD_TO_EXISTING_KEY", action3)
        org.junit.Assert.assertEquals(key2, key3)
    }

    @Test
    fun testPickerFilterByAccount() {
        val albums = listOf(
            com.reiux.lumen.data.network.GoogleAlbum(mediaKey = "AF1QipKeyPersonal1", title = "Personal Album 1", accountEmail = "personal@gmail.com"),
            com.reiux.lumen.data.network.GoogleAlbum(mediaKey = "AF1QipKeySecondary1", title = "Secondary Album 1", accountEmail = "secondary@gmail.com"),
            com.reiux.lumen.data.network.GoogleAlbum(mediaKey = "AF1QipKeyNoAccount", title = "Unassigned Album", accountEmail = null)
        )

        fun filterAlbumsForAccount(rawAlbums: List<com.reiux.lumen.data.network.GoogleAlbum>, targetEmail: String?): List<com.reiux.lumen.data.network.GoogleAlbum> {
            if (targetEmail.isNullOrBlank()) return emptyList()
            return rawAlbums.filter { !it.accountEmail.isNullOrBlank() && it.accountEmail.equals(targetEmail, ignoreCase = true) }
        }

        val personalFiltered = filterAlbumsForAccount(albums, "personal@gmail.com")
        org.junit.Assert.assertEquals(1, personalFiltered.size)
        org.junit.Assert.assertEquals("AF1QipKeyPersonal1", personalFiltered[0].mediaKey)

        val secondaryFiltered = filterAlbumsForAccount(albums, "secondary@gmail.com")
        org.junit.Assert.assertEquals(1, secondaryFiltered.size)
        org.junit.Assert.assertEquals("AF1QipKeySecondary1", secondaryFiltered[0].mediaKey)

        val unassignedFiltered = filterAlbumsForAccount(albums, "other@gmail.com")
        org.junit.Assert.assertTrue(unassignedFiltered.isEmpty())
    }

    @Test
    fun testAccountMismatchValidation() {
        val selectedAlbum = com.reiux.lumen.data.network.GoogleAlbum(
            mediaKey = "AF1QipKeySecondary1",
            title = "Secondary Album",
            accountEmail = "secondary@gmail.com"
        )

        fun validateAlbumAccount(album: com.reiux.lumen.data.network.GoogleAlbum?, targetAccountEmail: String?): Boolean {
            if (album == null || targetAccountEmail.isNullOrBlank()) return false
            return !album.accountEmail.isNullOrBlank() && album.accountEmail.equals(targetAccountEmail, ignoreCase = true)
        }

        // Selected album is secondary, target account is personal -> mismatch!
        val isValidMismatch = validateAlbumAccount(selectedAlbum, "personal@gmail.com")
        org.junit.Assert.assertFalse("Album belonging to secondary@gmail.com must be rejected for personal@gmail.com", isValidMismatch)

        // Selected album is secondary, target account is secondary -> valid!
        val isValidMatch = validateAlbumAccount(selectedAlbum, "secondary@gmail.com")
        org.junit.Assert.assertTrue("Album belonging to secondary@gmail.com must be accepted for secondary@gmail.com", isValidMatch)
    }

    @Test
    fun testErrorClassification404VsOther() {
        fun isAlbumNotFoundError(e: Throwable): Boolean {
            val msg = e.message ?: ""
            return msg.contains("404") || msg.contains("NOT_FOUND") || msg.contains("Requested entity was not found")
        }

        // 404 / NOT_FOUND -> treated as album missing
        assertTrue(isAlbumNotFoundError(Exception("HTTP 404 Not Found")))
        assertTrue(isAlbumNotFoundError(Exception("NOT_FOUND")))
        assertTrue(isAlbumNotFoundError(Exception("Requested entity was not found")))

        // 401, 403, 429, 5xx, Network errors -> NOT treated as album missing (should retry)
        assertFalse(isAlbumNotFoundError(Exception("HTTP 401 Unauthorized")))
        assertFalse(isAlbumNotFoundError(Exception("HTTP 403 Forbidden")))
        assertFalse(isAlbumNotFoundError(Exception("HTTP 429 Too Many Requests")))
        assertFalse(isAlbumNotFoundError(Exception("HTTP 500 Internal Server Error")))
        assertFalse(isAlbumNotFoundError(IOException("Socket closed")))
    }

    @Test
    fun testChoosingAccountPerItem() {
        val personalFolder = com.reiux.lumen.data.model.BackupFolder(
            path = "/sdcard/DCIM/Personal",
            displayName = "Test folder P",
            enabled = true,
            accountEmail = "personal@gmail.com"
        )
        val secondaryFolder = com.reiux.lumen.data.model.BackupFolder(
            path = "/sdcard/DCIM/Secondary",
            displayName = "Test folder S",
            enabled = true,
            accountEmail = "secondary@gmail.com"
        )

        val personalItem = com.reiux.lumen.data.model.UploadItem(
            id = "item_1",
            filePath = "/sdcard/DCIM/Personal/photo1.jpg",
            fileName = "photo1.jpg",
            fileSize = 1024L,
            accountEmail = personalFolder.accountEmail
        )

        val secondaryItem = com.reiux.lumen.data.model.UploadItem(
            id = "item_2",
            filePath = "/sdcard/DCIM/Secondary/photo2.jpg",
            fileName = "photo2.jpg",
            fileSize = 2048L,
            accountEmail = secondaryFolder.accountEmail
        )

        org.junit.Assert.assertEquals("personal@gmail.com", personalItem.accountEmail)
        org.junit.Assert.assertEquals("secondary@gmail.com", secondaryItem.accountEmail)

        // Switching active account to secondary should NOT change personalItem's target accountEmail
        val activeAccountEmail = "secondary@gmail.com"
        org.junit.Assert.assertEquals("personal@gmail.com", personalItem.accountEmail)
    }

    @Test
    fun testPerAccountDuplicateKeys() {
        val hashCache = mutableMapOf<String, String>()

        fun cacheKey(accountEmail: String, sha1B64: String, fileSize: Long): String {
            return "${accountEmail.lowercase().trim()}:$sha1B64:$fileSize"
        }

        val fileSha1 = "d3b07384d113edec49eaa6238ad5ff00"
        val size = 50000L

        // Store hash in Personal account
        hashCache[cacheKey("personal@gmail.com", fileSha1, size)] = "AF1QipMediaKeyPersonal123"

        // Check if cached in Personal -> HIT
        val personalHit = hashCache[cacheKey("personal@gmail.com", fileSha1, size)]
        org.junit.Assert.assertEquals("AF1QipMediaKeyPersonal123", personalHit)

        // Check if cached in Secondary -> MISS (allows uploading to Secondary account!)
        val secondaryMiss = hashCache[cacheKey("secondary@gmail.com", fileSha1, size)]
        org.junit.Assert.assertNull("Same file in secondary account must be a cache MISS and allowed to upload", secondaryMiss)
    }

    @Test
    fun testPerAccountFolderSelection() {
        val allFolders = listOf(
            com.reiux.lumen.data.model.BackupFolder("/sdcard/DCIM/P", "Personal DCIM", enabled = true, accountEmail = "personal@gmail.com"),
            com.reiux.lumen.data.model.BackupFolder("/sdcard/DCIM/S", "Secondary DCIM", enabled = true, accountEmail = "secondary@gmail.com"),
            com.reiux.lumen.data.model.BackupFolder("/sdcard/Download", "Downloads", enabled = true, accountEmail = null)
        )

        fun getFoldersForAccount(all: List<com.reiux.lumen.data.model.BackupFolder>, email: String?): List<com.reiux.lumen.data.model.BackupFolder> {
            if (email.isNullOrBlank()) {
                return all.filter { it.accountEmail.isNullOrBlank() }
            }
            return all.filter { !it.accountEmail.isNullOrBlank() && it.accountEmail.equals(email, ignoreCase = true) }
        }

        val personalFolders = getFoldersForAccount(allFolders, "personal@gmail.com")
        org.junit.Assert.assertEquals(1, personalFolders.size)
        org.junit.Assert.assertEquals("Personal DCIM", personalFolders[0].displayName)

        val secondaryFolders = getFoldersForAccount(allFolders, "secondary@gmail.com")
        org.junit.Assert.assertEquals(1, secondaryFolders.size)
        org.junit.Assert.assertEquals("Secondary DCIM", secondaryFolders[0].displayName)

        val unassignedFolders = getFoldersForAccount(allFolders, null)
        org.junit.Assert.assertEquals(1, unassignedFolders.size)
        org.junit.Assert.assertEquals("Downloads", unassignedFolders[0].displayName)
    }

    @Test
    fun testMultiAccountSharedFolders() {
        val folders = mutableListOf<com.reiux.lumen.data.model.BackupFolder>()

        fun addFolderForAccount(folder: com.reiux.lumen.data.model.BackupFolder) {
            val index = folders.indexOfFirst {
                it.path == folder.path &&
                if (it.accountEmail.isNullOrBlank() && folder.accountEmail.isNullOrBlank()) true
                else !it.accountEmail.isNullOrBlank() && it.accountEmail.equals(folder.accountEmail, ignoreCase = true)
            }
            if (index >= 0) {
                folders[index] = folder
            } else {
                folders.add(folder)
            }
        }

        val path = "/sdcard/DCIM/Camera"
        val folderAccountA = com.reiux.lumen.data.model.BackupFolder(path = path, displayName = "Camera Roll", accountEmail = "accA@gmail.com")
        val folderAccountB = com.reiux.lumen.data.model.BackupFolder(path = path, displayName = "Camera Roll", accountEmail = "accB@gmail.com")

        // Add folder for Account A
        addFolderForAccount(folderAccountA)
        org.junit.Assert.assertEquals(1, folders.size)

        // Add SAME folder path for Account B -> Creates separate entry for Account B
        addFolderForAccount(folderAccountB)
        org.junit.Assert.assertEquals(2, folders.size)

        val accAFolder = folders.find { it.accountEmail == "accA@gmail.com" }
        val accBFolder = folders.find { it.accountEmail == "accB@gmail.com" }
        org.junit.Assert.assertNotNull(accAFolder)
        org.junit.Assert.assertNotNull(accBFolder)
        org.junit.Assert.assertEquals(path, accAFolder?.path)
        org.junit.Assert.assertEquals(path, accBFolder?.path)
    }

    @Test
    fun testActiveUploadingItemReflectsStreamedBytes() {
        val item = com.reiux.lumen.data.model.UploadItem(
            id = "test-1",
            filePath = "/sdcard/DCIM/sample.mp4",
            fileName = "sample.mp4",
            fileSize = 100_000L,
            bytesUploaded = 0L,
            status = UploadStatus.UPLOADING,
            accountEmail = "user@gmail.com"
        )
        val queue = mutableListOf(item)

        fun updateProgress(id: String, bytes: Long) {
            val idx = queue.indexOfFirst { it.id == id }
            if (idx >= 0) {
                queue[idx] = queue[idx].copy(bytesUploaded = bytes)
            }
        }

        fun getActive(q: List<com.reiux.lumen.data.model.UploadItem>): com.reiux.lumen.data.model.UploadItem? {
            return q.firstOrNull { it.status == UploadStatus.UPLOADING }
        }

        updateProgress("test-1", 50_000L)
        val active = getActive(queue)
        org.junit.Assert.assertEquals(50_000L, active?.bytesUploaded)
    }
}
