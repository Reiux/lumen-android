package com.reiux.lumen

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.net.TestUri
import android.net.Uri
import com.reiux.lumen.data.model.AccountBlock
import com.reiux.lumen.data.model.AuthFailureKind
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.data.model.UploadItem
import com.reiux.lumen.data.model.UploadStatus
import com.reiux.lumen.data.network.GoogleAuthClient
import com.reiux.lumen.data.network.GooglePhotosClient
import com.reiux.lumen.data.repository.FakeSharedPreferences
import com.reiux.lumen.data.repository.SettingsRepository
import com.reiux.lumen.data.repository.UpdateRepository
import com.reiux.lumen.data.repository.UploadHashCache
import com.reiux.lumen.data.repository.UploadRepository
import com.reiux.lumen.ui.components.isAllowedBackupFolderPath
import com.reiux.lumen.ui.components.isStandardMediaCollectionPath
import com.reiux.lumen.util.FolderWatcherManager
import com.reiux.lumen.util.LogEntry
import com.reiux.lumen.util.LogLevel
import com.reiux.lumen.util.LumenLogger
import com.reiux.lumen.util.MediaFileReader
import com.reiux.lumen.util.OemIntentHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

class SecurityAuditRun2FixesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class TestSettingsContext(private val cacheRoot: File? = null) : ContextWrapper(null) {
        private val prefsMap = mutableMapOf<String, FakeSharedPreferences>()
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            return prefsMap.getOrPut(name ?: "default") { FakeSharedPreferences() }
        }
        override fun getApplicationContext(): Context = this
        override fun getCacheDir(): File = cacheRoot ?: super.getCacheDir()
    }

    private fun newSettingsRepository(cacheRoot: File? = null): Pair<FakeSharedPreferences, SettingsRepository> {
        val ctx = TestSettingsContext(cacheRoot)
        val prefs = ctx.getSharedPreferences("lumen_settings", Context.MODE_PRIVATE) as FakeSharedPreferences
        return prefs to SettingsRepository(context = ctx, sharedPreferencesOverride = prefs)
    }

    // -------------------------------------------------------------------------
    // Lead 1 + Lead 5: Share URI filtering, deduplication, and cap
    // -------------------------------------------------------------------------

    @Test
    fun shareUris_blocksSelfProviderNonContentDeduplicatesAndCaps() {
        val selfAuthority = "com.reiux.lumen.dev.fileprovider"
        val valid1 = TestUri("content://media/external/images/media/1", "content", "media", "/external/images/media/1")
        val valid1Dup = TestUri("content://media/external/images/media/1", "content", "media", "/external/images/media/1")
        val selfUri = TestUri("content://com.reiux.lumen.dev.fileprovider/logs/lumen.log", "content", selfAuthority, "/logs/lumen.log")
        val selfUpperUri = TestUri("content://COM.REIUX.LUMEN.DEV.FILEPROVIDER/cache/secret", "content", selfAuthority.uppercase(), "/cache/secret")
        val fileUri = TestUri("file:///sdcard/DCIM/Camera/a.jpg", "file", "", "/sdcard/DCIM/Camera/a.jpg")
        val httpUri = TestUri("https://example.com/photo.jpg", "https", "example.com", "/photo.jpg")
        val emptyAuthUri = TestUri("content:///missing/auth", "content", "   ", "/missing/auth")
        val nullSchemeUri = TestUri("/sdcard/DCIM/b.jpg", null, null, "/sdcard/DCIM/b.jpg")

        val extraValid = (2..105).map { id ->
            TestUri("content://media/external/images/media/$id", "content", "media", "/external/images/media/$id")
        }

        val input: List<Uri> = listOf(
            selfUri,
            fileUri,
            valid1,
            httpUri,
            emptyAuthUri,
            selfUpperUri,
            nullSchemeUri,
            valid1Dup
        ) + extraValid

        val defaultCapped = MainActivity.filterAndCapShareUris(input, selfAuthority)
        assertEquals(MainActivity.MAX_SHARE_URIS, defaultCapped.size)
        assertEquals(valid1, defaultCapped.first())
        assertEquals(1, defaultCapped.count { it == valid1 })
        assertFalse(defaultCapped.any { it.authority.equals(selfAuthority, ignoreCase = true) })
        assertTrue(defaultCapped.all { it.scheme == "content" && !it.authority.isNullOrBlank() })

        val customCapped = MainActivity.filterAndCapShareUris(input, selfAuthority, maxItems = 3)
        assertEquals(3, customCapped.size)
        assertTrue(MainActivity.filterAndCapShareUris(input, selfAuthority, maxItems = 0).isEmpty())
    }

    // -------------------------------------------------------------------------
    // Lead 2: Post-backup deletion scope enforcement
    // -------------------------------------------------------------------------

    @Test
    fun postBackupDeletion_enforcesAllowFlagFolderScopeAndStagingExclusions() {
        val stagingPrefix = "/data/user/0/com.reiux.lumen/cache/staging/"
        val enabledCameraFolder = BackupFolder(
            path = "/storage/emulated/0/DCIM/Camera",
            displayName = "Camera Roll",
            enabled = true,
            includeSubfolders = false,
            accountEmail = "a@example.com"
        )
        val disabledFolder = BackupFolder(
            path = "/storage/emulated/0/Pictures/Private",
            displayName = "Private",
            enabled = false,
            includeSubfolders = true,
            accountEmail = "a@example.com"
        )
        val otherAccountFolder = BackupFolder(
            path = "/storage/emulated/0/Pictures/Other",
            displayName = "Other",
            enabled = true,
            includeSubfolders = true,
            accountEmail = "b@example.com"
        )
        val configuredFolders = listOf(enabledCameraFolder, disabledFolder, otherAccountFolder)

        val baseItem = UploadItem(
            id = "u1",
            filePath = "/sdcard/DCIM/Camera/IMG_001.jpg",
            fileName = "IMG_001.jpg",
            fileSize = 1024L,
            accountEmail = "A@example.com",
            originalFilePath = "/sdcard/DCIM/Camera/IMG_001.jpg",
            allowDeleteAfterBackup = true
        )

        // 1. Disallowed when allowDeleteAfterBackup == false (e.g. Photo Picker / Share Sheet)
        assertFalse(
            UploadRepository.isEligibleForPostBackupDeletion(
                item = baseItem.copy(allowDeleteAfterBackup = false),
                targetPath = "/sdcard/DCIM/Camera/IMG_001.jpg",
                configuredFolders = configuredFolders,
                deleteAfterBackupSetting = true,
                stagingDirCanonicalPrefix = stagingPrefix
            )
        )

        // 2. Disallowed when deleteAfterBackupSetting == false
        assertFalse(
            UploadRepository.isEligibleForPostBackupDeletion(
                item = baseItem,
                targetPath = "/sdcard/DCIM/Camera/IMG_001.jpg",
                configuredFolders = configuredFolders,
                deleteAfterBackupSetting = false,
                stagingDirCanonicalPrefix = stagingPrefix
            )
        )

        // 3. Disallowed when path is outside enabled folders
        assertFalse(
            UploadRepository.isEligibleForPostBackupDeletion(
                item = baseItem,
                targetPath = "/sdcard/Download/photo.jpg",
                configuredFolders = configuredFolders,
                deleteAfterBackupSetting = true,
                stagingDirCanonicalPrefix = stagingPrefix
            )
        )

        // 4. Disallowed when path is inside staging cache (or traverses into /cache/staging/)
        assertFalse(
            UploadRepository.isEligibleForPostBackupDeletion(
                item = baseItem,
                targetPath = "/data/user/0/com.reiux.lumen/cache/staging/stg_123/IMG_001.jpg",
                configuredFolders = configuredFolders,
                deleteAfterBackupSetting = true,
                stagingDirCanonicalPrefix = stagingPrefix
            )
        )
        assertFalse(
            UploadRepository.isEligibleForPostBackupDeletion(
                item = baseItem,
                targetPath = "/storage/emulated/0/DCIM/Camera/../../../../data/user/0/com.reiux.lumen/cache/staging/stg_1/IMG_001.jpg",
                configuredFolders = configuredFolders,
                deleteAfterBackupSetting = true,
                stagingDirCanonicalPrefix = stagingPrefix
            )
        )

        // 5. Disallowed when folder is disabled or belongs to another account
        assertFalse(
            UploadRepository.isEligibleForPostBackupDeletion(
                item = baseItem,
                targetPath = "/storage/emulated/0/Pictures/Private/secret.jpg",
                configuredFolders = configuredFolders,
                deleteAfterBackupSetting = true,
                stagingDirCanonicalPrefix = stagingPrefix
            )
        )
        assertFalse(
            UploadRepository.isEligibleForPostBackupDeletion(
                item = baseItem,
                targetPath = "/storage/emulated/0/Pictures/Other/other.jpg",
                configuredFolders = configuredFolders,
                deleteAfterBackupSetting = true,
                stagingDirCanonicalPrefix = stagingPrefix
            )
        )

        // 6. Allowed ONLY when setting=true, item.allowDeleteAfterBackup=true, non-staging, and matches enabled folder for item's account
        assertTrue(
            UploadRepository.isEligibleForPostBackupDeletion(
                item = baseItem,
                targetPath = "/sdcard/DCIM/Camera/IMG_001.jpg",
                configuredFolders = configuredFolders,
                deleteAfterBackupSetting = true,
                stagingDirCanonicalPrefix = stagingPrefix
            )
        )
    }

    // -------------------------------------------------------------------------
    // Lead 3: Cross-volume MediaStore & path containment
    // -------------------------------------------------------------------------

    @Test
    fun folderWatcher_preventsCrossVolumeBypassAndSupportsExplicitSecondaryVolume() {
        val primaryFolder = BackupFolder(
            path = "/storage/emulated/0/DCIM/Camera",
            displayName = "Camera",
            enabled = true,
            includeSubfolders = false
        )
        val sdcardAliasFolder = BackupFolder(
            path = "/sdcard/DCIM/Camera",
            displayName = "Camera",
            enabled = true,
            includeSubfolders = false
        )
        val secondaryVolumeFolder = BackupFolder(
            path = "/storage/ABCD-1234/DCIM/Camera",
            displayName = "Camera",
            enabled = true,
            includeSubfolders = false
        )

        // Secondary volume physical path MUST NOT match primary folder despite identical relativePath/bucket
        assertFalse(
            FolderWatcherManager.isMediaStoreItemInFolder(
                filePath = "/storage/ABCD-1234/DCIM/Camera/IMG_001.jpg",
                relativePath = "DCIM/Camera/",
                bucketDisplayName = "Camera",
                folder = primaryFolder,
                volumeName = null
            )
        )
        assertFalse(
            FolderWatcherManager.isPathMatchingFolder(
                "/storage/ABCD-1234/DCIM/Camera/IMG_001.jpg",
                sdcardAliasFolder
            )
        )

        // Mismatched volumeName rejects even when filePath is null (both relativePath and bucket fallback)
        assertFalse(
            FolderWatcherManager.isMediaStoreItemInFolder(
                filePath = null,
                relativePath = "DCIM/Camera/",
                bucketDisplayName = "Camera",
                folder = primaryFolder,
                volumeName = "abcd-1234"
            )
        )
        assertFalse(
            FolderWatcherManager.isMediaStoreItemInFolder(
                filePath = null,
                relativePath = null,
                bucketDisplayName = "Camera",
                folder = primaryFolder,
                volumeName = "abcd-1234"
            )
        )

        // Primary volume aliases (/sdcard, /storage/emulated/0, /storage/self/primary) match consistently
        assertTrue(
            FolderWatcherManager.isMediaStoreItemInFolder(
                filePath = "/sdcard/DCIM/Camera/IMG_001.jpg",
                relativePath = "DCIM/Camera/",
                bucketDisplayName = "Camera",
                folder = primaryFolder,
                volumeName = "external_primary"
            )
        )
        assertTrue(
            FolderWatcherManager.isPathMatchingFolder(
                "/storage/self/primary/DCIM/Camera/IMG_002.jpg",
                sdcardAliasFolder
            )
        )
        assertTrue(
            FolderWatcherManager.isMediaStoreItemInFolder(
                filePath = null,
                relativePath = "DCIM/Camera/",
                bucketDisplayName = "Camera",
                folder = sdcardAliasFolder,
                volumeName = "external_primary"
            )
        )

        // Explicit secondary volume configured folder matches matching secondary volumeName and relativePath
        assertTrue(
            FolderWatcherManager.isMediaStoreItemInFolder(
                filePath = null,
                relativePath = "DCIM/Camera/",
                bucketDisplayName = "Camera",
                folder = secondaryVolumeFolder,
                volumeName = "abcd-1234"
            )
        )
        assertTrue(
            FolderWatcherManager.isMediaStoreItemInFolder(
                filePath = "/storage/ABCD-1234/DCIM/Camera/IMG_001.jpg",
                relativePath = "DCIM/Camera/",
                bucketDisplayName = "Camera",
                folder = secondaryVolumeFolder,
                volumeName = "abcd-1234"
            )
        )
        // Secondary folder rejects primary volume items
        assertFalse(
            FolderWatcherManager.isMediaStoreItemInFolder(
                filePath = null,
                relativePath = "DCIM/Camera/",
                bucketDisplayName = "Camera",
                folder = secondaryVolumeFolder,
                volumeName = "external_primary"
            )
        )
        // Spoofed relativePath cannot bypass physical filePath outside folder
        assertFalse(
            FolderWatcherManager.isMediaStoreItemInFolder(
                filePath = "/storage/emulated/0/Pictures/Screenshots/shot.png",
                relativePath = "DCIM/Camera/",
                bucketDisplayName = "Camera",
                folder = primaryFolder,
                volumeName = "external_primary"
            )
        )
    }

    // -------------------------------------------------------------------------
    // Lead 5 + Symlink Hardening: Bounded staging and symlink exclusion
    // -------------------------------------------------------------------------

    @Test
    fun mediaFileReader_copyStreamWithLimitAndStageBoundedStreamToDirEnforceLimits() {
        // Exact boundary succeeds
        val exactInput = ByteArrayInputStream(ByteArray(128) { 0x42 })
        val exactOut = ByteArrayOutputStream()
        var chunks = 0
        val copied = MediaFileReader.copyStreamWithLimit(exactInput, exactOut, 128L) { chunks++ }
        assertEquals(128L, copied)
        assertEquals(128, exactOut.size())
        assertTrue(chunks >= 1)

        // Exceeding maxBytes throws IOException
        val overInput = ByteArrayInputStream(ByteArray(129) { 0x42 })
        val overOut = ByteArrayOutputStream()
        try {
            MediaFileReader.copyStreamWithLimit(overInput, overOut, 128L)
            fail("Expected IOException when stream exceeds maxBytes")
        } catch (_: IOException) {
            // Expected
        }

        // Cancellation callback aborts cleanly
        try {
            MediaFileReader.copyStreamWithLimit(ByteArrayInputStream(ByteArray(32)), ByteArrayOutputStream(), 128L) {
                throw CancellationException("Cancelled in test")
            }
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // Expected
        }

        // stageBoundedStreamToDir: oversized stream leaves zero temp/staging files behind
        val stagingRoot = tempFolder.newFolder("staging_root")
        val stagingDir = File(stagingRoot, "stg_over")
        val stagedOver = MediaFileReader.stageBoundedStreamToDir(
            uniqueStagingDir = stagingDir,
            fileName = "oversized.jpg",
            occurrenceIndex = 0,
            input = ByteArrayInputStream(ByteArray(500) { 1 }),
            stagingRoot = stagingRoot,
            maxFileBytes = 256L,
            maxTotalStagingBytes = 1024L,
            minFreeReserveBytes = 0L
        )
        assertNull(stagedOver)
        assertFalse(stagingDir.exists())
        assertEquals(0, stagingRoot.listFiles()?.size ?: 0)
    }

    @Test
    fun mediaFileReader_enforceStagingBudgetEvictsOldestUnprotectedAndRejectsWhenFull() {
        val stagingRoot = tempFolder.newFolder("staging_budget")
        val oldDir = File(stagingRoot, "stg_old").apply { mkdirs() }
        val oldFile = File(oldDir, "old.jpg").apply { writeBytes(ByteArray(600)) }
        oldDir.setLastModified(1_000L)

        val protectedDir = File(stagingRoot, "stg_protected").apply { mkdirs() }
        val protectedFile = File(protectedDir, "active.jpg").apply { writeBytes(ByteArray(600)) }
        protectedDir.setLastModified(2_000L)

        // With 1200 bytes used and max=1000, requiring 200 more bytes must evict oldDir but keep protectedDir
        val remaining = MediaFileReader.enforceStagingBudget(
            stagingRoot = stagingRoot,
            requiredAdditionalBytes = 200L,
            maxTotalStagingBytes = 1000L,
            minFreeReserveBytes = 0L,
            protectedPaths = setOf(protectedDir.absolutePath)
        )
        assertFalse(oldFile.exists())
        assertFalse(oldDir.exists())
        assertTrue(protectedFile.exists())
        assertTrue(remaining <= 400L)

        // When protected dirs already saturate maxTotalStagingBytes, staging rejects and cleans up new dir
        val newDir = File(stagingRoot, "stg_rejected")
        val rejected = MediaFileReader.stageBoundedStreamToDir(
            uniqueStagingDir = newDir,
            fileName = "new.jpg",
            occurrenceIndex = 0,
            input = ByteArrayInputStream(ByteArray(100)),
            stagingRoot = stagingRoot,
            maxFileBytes = 500L,
            maxTotalStagingBytes = 600L,
            minFreeReserveBytes = 0L
        )
        assertNull(rejected)
        assertFalse(newDir.exists())
        assertTrue(protectedFile.exists())
    }

    @Test
    fun mediaFileReader_scanMediaFilesSkipsSymlinksEscapingRoot() {
        val sandbox = tempFolder.newFolder("symlink_sandbox")
        val scanRoot = File(sandbox, "watched").apply { mkdirs() }
        val outsideDir = File(sandbox, "outside").apply { mkdirs() }

        val insideFile = File(scanRoot, "legit.jpg").apply { writeBytes(ByteArray(32)) }
        val outsideFile = File(outsideDir, "secret.jpg").apply { writeBytes(ByteArray(32)) }

        val fileSymlink = File(scanRoot, "link_to_secret.jpg")
        val dirSymlink = File(scanRoot, "link_to_outside_dir")
        val symlinksSupported = try {
            Files.createSymbolicLink(fileSymlink.toPath(), outsideFile.toPath())
            Files.createSymbolicLink(dirSymlink.toPath(), outsideDir.toPath())
            true
        } catch (_: Exception) {
            false
        }
        assumeTrue("Filesystem must support symbolic links", symlinksSupported)

        val scanned = MediaFileReader.scanMediaFiles(scanRoot)
        assertEquals(listOf(insideFile.absolutePath), scanned.map { it.absolutePath })
    }

    // -------------------------------------------------------------------------
    // Lead 6: Account purge, recent album scoping, and internal launch token
    // -------------------------------------------------------------------------

    @Test
    fun settingsRepository_removeAccountDataAndRecentAlbumsScopeAndLaunchToken() {
        val (prefs, settingsRepo) = newSettingsRepository()

        val folderA = BackupFolder("/sdcard/DCIM/AccA", "AccA", enabled = true, accountEmail = "a@example.com")
        val folderB = BackupFolder("/sdcard/DCIM/AccB", "AccB", enabled = true, accountEmail = "b@example.com")
        settingsRepo.saveFolders(listOf(folderA, folderB))

        settingsRepo.saveAlbumMediaKey("a@example.com", "Album A", "AF1QipAlbumAKey123")
        settingsRepo.saveAlbumMediaKey("b@example.com", "Album B", "AF1QipAlbumBKey456")
        settingsRepo.saveFolderWatermarkId("a@example.com", folderA.path, 101L)
        settingsRepo.saveFolderWatermarkId("b@example.com", folderB.path, 202L)
        settingsRepo.setAccountBlock(AccountBlock(AuthFailureKind.SIGN_IN_REQUIRED, accountEmail = "a@example.com"))
        settingsRepo.setAccountBlock(AccountBlock(AuthFailureKind.RATE_LIMITED, accountEmail = "b@example.com", retryAfterTimestamp = System.currentTimeMillis() + 60_000L))

        // Only listed active accounts are returned by getAllRecentAlbums
        val onlyBAlbums = settingsRepo.getAllRecentAlbums(setOf("b@example.com"))
        assertEquals(1, onlyBAlbums.size)
        assertEquals("Album B", onlyBAlbums.first().title)
        assertTrue(settingsRepo.getAllRecentAlbums(emptySet()).isEmpty())

        // Removing account A purges its folders, album keys, watermarks, recents, and block
        settingsRepo.removeAccountData("A@example.com")

        val remainingFolders = settingsRepo.getAllFolders()
        assertEquals(1, remainingFolders.size)
        assertEquals("b@example.com", remainingFolders.first().accountEmail)
        assertEquals(1, settingsRepo.settings.value.folders.size)

        assertNull(settingsRepo.getAlbumMediaKey("a@example.com", "Album A"))
        assertEquals(0L, settingsRepo.getFolderWatermarkId("a@example.com", folderA.path))
        assertNull(settingsRepo.getAccountBlock("a@example.com"))
        assertFalse(prefs.contains("recent_albums_a@example.com"))
        assertFalse(prefs.all.keys.any { it.startsWith("album_key_a@example.com_") })
        assertFalse(prefs.all.keys.any { it.startsWith("watermark_id_a@example.com_") })

        // Account B data remains completely intact
        assertEquals("AF1QipAlbumBKey456", settingsRepo.getAlbumMediaKey("b@example.com", "Album B"))
        assertEquals(202L, settingsRepo.getFolderWatermarkId("b@example.com", folderB.path))
        assertNotNull(settingsRepo.getAccountBlock("b@example.com"))
        assertTrue(prefs.contains("recent_albums_b@example.com"))

        // Internal update notification launch token validation
        assertFalse(settingsRepo.isValidInternalLaunchToken(null))
        assertFalse(settingsRepo.isValidInternalLaunchToken("forged-token"))
        val token1 = settingsRepo.getOrCreateInternalLaunchToken()
        val token2 = settingsRepo.getOrCreateInternalLaunchToken()
        assertTrue(token1.length >= 32)
        assertEquals(token1, token2)
        assertTrue(settingsRepo.isValidInternalLaunchToken(token1))
        assertFalse(settingsRepo.isValidInternalLaunchToken(token1 + "x"))
        assertFalse(settingsRepo.isValidInternalLaunchToken(""))
    }

    @Test
    fun uploadHashCache_deleteForAccountHandlesInputsSafely() = runBlocking {
        val ctx = TestSettingsContext()
        val cache = UploadHashCache(ctx)
        // Blank email early-returns; non-blank executes cleanly without throwing on JVM
        cache.deleteForAccount("   ")
        cache.deleteForAccount("A@Example.com")
    }

    @Test
    fun uploadRepository_defaultDeletionFlagAndAccountPurgeCleansQueueAndStagedFiles() {
        val defaultItem = UploadItem(
            id = "default_item",
            filePath = "/sdcard/DCIM/Camera/test.jpg",
            fileName = "test.jpg",
            fileSize = 100L
        )
        assertFalse(defaultItem.allowDeleteAfterBackup)

        val cacheRoot = tempFolder.newFolder("repo_cache")
        val stagingItemDir = File(File(cacheRoot, "staging"), "stg_acc_a").apply { mkdirs() }
        val stagedFileA = File(stagingItemDir, "shared_a.jpg").apply { writeBytes(ByteArray(32)) }
        val fileB = File(tempFolder.newFolder("camera_b"), "camera_b.jpg").apply { writeBytes(ByteArray(32)) }

        val ctx = TestSettingsContext(cacheRoot)
        val prefs = ctx.getSharedPreferences("lumen_settings", Context.MODE_PRIVATE) as FakeSharedPreferences
        val settingsRepo = SettingsRepository(context = ctx, sharedPreferencesOverride = prefs)
        val uploadRepo = UploadRepository(context = ctx, settingsRepository = settingsRepo)

        val accA = GoogleAccount("a@example.com", "cred_a", "Acc A")
        val accB = GoogleAccount("b@example.com", "cred_b", "Acc B")
        uploadRepo.enqueueFiles(listOf(stagedFileA), accA, storageSaver = false)
        uploadRepo.enqueueFiles(listOf(fileB), accB, storageSaver = false, allowDeleteAfterBackup = true)

        val queuedA = uploadRepo.queue.value.first { it.accountEmail == "a@example.com" }
        val queuedB = uploadRepo.queue.value.first { it.accountEmail == "b@example.com" }
        assertFalse(queuedA.allowDeleteAfterBackup)
        assertTrue(queuedB.allowDeleteAfterBackup)

        uploadRepo.removeAccountData("A@Example.com")

        assertTrue(uploadRepo.queue.value.none { it.accountEmail.equals("a@example.com", ignoreCase = true) })
        assertEquals(1, uploadRepo.queue.value.size)
        assertEquals("b@example.com", uploadRepo.queue.value.first().accountEmail)
        assertFalse(stagedFileA.exists())
        assertTrue(fileB.exists())
    }

    // -------------------------------------------------------------------------
    // Lead 7: Redaction of filenames, paths, albums, and JSON mediaKeys
    // -------------------------------------------------------------------------

    @Test
    fun lumenLogger_redactsPathsFileNamesAlbumsAndMediaKeysWhenHiddenAndPreservesWhenShown() {
        val entries = listOf(
            LogEntry(
                id = 1L,
                timestamp = 1_700_000_000_000L,
                level = LogLevel.ERROR,
                tag = "UploadEngine",
                threadName = "main",
                message = "Failed reading /sdcard/DCIM/Camera/Secret.jpg",
                fileName = null,
                userMessage = null
            ),
            LogEntry(
                id = 2L,
                timestamp = 1_700_000_001_000L,
                level = LogLevel.ERROR,
                tag = "UploadEngine",
                threadName = "main",
                message = "Upload failed on /storage/emulated/0/Pictures/Vacation Pics/photo 1.heic",
                fileName = null,
                userMessage = null
            ),
            LogEntry(
                id = 3L,
                timestamp = 1_700_000_002_000L,
                level = LogLevel.ERROR,
                tag = "UploadEngine",
                threadName = "main",
                message = "Unhandled error on item [PrivateDoc.png]",
                fileName = null,
                userMessage = null
            ),
            LogEntry(
                id = 4L,
                timestamp = 1_700_000_003_000L,
                level = LogLevel.WARN,
                tag = "AlbumSync",
                threadName = "main",
                message = "Error checking 'My Secret.mp4' in album 'Private Album' {\"mediaKey\":\"AF1QipSecretKey987654\"}",
                fileName = null,
                userMessage = null
            ),
            LogEntry(
                id = 5L,
                timestamp = 1_700_000_004_000L,
                level = LogLevel.WARN,
                tag = "LumenApp",
                threadName = "main",
                message = "Error querying MediaStore for folder /storage/emulated/0/Pictures/Vacation Pics: denied",
                fileName = null,
                userMessage = null
            )
        )

        val hiddenLines = LumenLogger.formatProblemsForReport(entries, hideFileNames = true)
        val joinedHidden = hiddenLines.joinToString("\n")

        val forbiddenFragments = listOf(
            "Secret.jpg",
            "DCIM",
            "Vacation Pics",
            "Pics",
            "photo 1.heic",
            "photo",
            "PrivateDoc.png",
            "My Secret.mp4",
            "Private Album",
            "AF1QipSecretKey987654"
        )
        for (secret in forbiddenFragments) {
            assertFalse("Expected '$secret' to be scrubbed from:\n$joinedHidden", joinedHidden.contains(secret))
        }

        // Also test standalone redact() for JSON mediaKey fields and kv path with spaces
        val redactedJson = LumenLogger.redact("{\"mediaKey\":\"AF1QipKey1\",\"albumMediaKey\":\"AF1QipKey2\",\"initialMediaKey\":\"AF1QipKey3\"}")
        assertFalse(redactedJson.contains("AF1QipKey1"))
        assertFalse(redactedJson.contains("AF1QipKey2"))
        assertFalse(redactedJson.contains("AF1QipKey3"))

        val kvRedacted = LumenLogger.redactFileNamesAndPaths(
            "File cannot be read: path=/storage/emulated/0/Pictures/Vacation Pics/photo 1.heic elapsedMs=4000"
        )
        assertFalse(kvRedacted.contains("Vacation"))
        assertFalse(kvRedacted.contains("Pics"))
        assertFalse(kvRedacted.contains("photo"))
        assertTrue(kvRedacted.contains("elapsedMs=4000"))

        // With hideFileNames = false, diagnostic filenames remain intact while tokens/emails still redact
        val visibleEntry = LogEntry(
            id = 6L,
            timestamp = 1_700_000_005_000L,
            level = LogLevel.ERROR,
            tag = "UploadEngine",
            threadName = "main",
            message = "Error checking 'My Secret.mp4' for user@example.com",
            fileName = "My Secret.mp4",
            userMessage = "Error checking 'My Secret.mp4' for user@example.com"
        )
        val visibleLines = LumenLogger.formatProblemsForReport(listOf(visibleEntry), hideFileNames = false)
        assertEquals(1, visibleLines.size)
        assertTrue(visibleLines.first().contains("My Secret.mp4"))
        assertFalse(visibleLines.first().contains("[file_hidden]"))
        assertFalse(visibleLines.first().contains("user@example.com"))
    }

    // -------------------------------------------------------------------------
    // Section 5 Hardening: Permissions, HTTP bounds, Retry-After, and OEM flags
    // -------------------------------------------------------------------------

    @Test
    fun allFilesPermissionAndAllowedFolderPaths_enforceBoundariesAndTraversalGuards() {
        assertTrue(isStandardMediaCollectionPath("/sdcard/DCIM", "/storage/emulated/0"))
        assertTrue(isStandardMediaCollectionPath("/sdcard/DCIM/Camera", "/storage/emulated/0"))
        assertTrue(isStandardMediaCollectionPath("/storage/emulated/0/Pictures/Screenshots", "/storage/emulated/0"))
        assertTrue(isStandardMediaCollectionPath("/storage/emulated/0/Movies", "/storage/emulated/0"))
        assertTrue(isStandardMediaCollectionPath("/storage/emulated/0/Music", "/storage/emulated/0"))

        assertFalse(isStandardMediaCollectionPath("/sdcard/Pictures2", "/storage/emulated/0"))
        assertFalse(isStandardMediaCollectionPath("/sdcard/DCIM_Backup", "/storage/emulated/0"))
        assertFalse(isStandardMediaCollectionPath("/sdcard/Download", "/storage/emulated/0"))
        assertFalse(isStandardMediaCollectionPath("/storage/ABCD-1234/DCIM/Camera", "/storage/emulated/0"))

        assertFalse(isAllowedBackupFolderPath("/sdcard/Android/data/com.whatsapp"))
        assertFalse(isAllowedBackupFolderPath("/storage/emulated/0/Android/obb/com.whatsapp"))
        assertFalse(isAllowedBackupFolderPath("/sdcard/Android/media/../data/com.whatsapp"))
        assertFalse(isAllowedBackupFolderPath("/sdcard/Android"))
        assertFalse(isAllowedBackupFolderPath("/storage/ABCD-1234/Android"))
        assertFalse(isAllowedBackupFolderPath("/storage/ABCD-1234/Android/data/com.whatsapp"))
        assertFalse(isAllowedBackupFolderPath("/sdcard/Android/media/com.otherapp"))

        assertTrue(isAllowedBackupFolderPath("/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"))
        assertTrue(isAllowedBackupFolderPath("/storage/ABCD-1234/Android/media/com.whatsapp/WhatsApp/Media"))
        assertTrue(isAllowedBackupFolderPath("/sdcard/DCIM/Camera"))
    }

    @Test
    fun httpClients_clampRetryAfterDisableRedirectsAndEnforceResponseBounds() {
        assertEquals(86400L, GooglePhotosClient.parseRetryAfterSeconds(Long.MAX_VALUE.toString(), 30L))
        assertEquals(1L, GooglePhotosClient.parseRetryAfterSeconds("-5", 30L))
        assertEquals(1L, GooglePhotosClient.parseRetryAfterSeconds("0", 30L))
        assertEquals(120L, GooglePhotosClient.parseRetryAfterSeconds(" 120 ", 30L))
        assertEquals(60L, GooglePhotosClient.parseRetryAfterSeconds(null, 60L))
        assertEquals(1L, GooglePhotosClient.parseRetryAfterSeconds("invalid", 0L))

        val redirectingClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val photosClient = GooglePhotosClient(redirectingClient)
        val innerField = GooglePhotosClient::class.java.getDeclaredField("httpClient").apply { isAccessible = true }
        val innerClient = innerField.get(photosClient) as OkHttpClient
        assertFalse(innerClient.followRedirects)
        assertFalse(innerClient.followSslRedirects)

        // GoogleAuthClient.readBoundedAuthBytes
        assertEquals(0, GoogleAuthClient.readBoundedAuthBytes(null, 64).size)
        val exactAuthBytes = ByteArray(64) { 7 }
        assertArrayEquals(
            exactAuthBytes,
            GoogleAuthClient.readBoundedAuthBytes(exactAuthBytes.toResponseBody("text/plain".toMediaType()), 64)
        )
        try {
            GoogleAuthClient.readBoundedAuthBytes(ByteArray(65).toResponseBody("text/plain".toMediaType()), 64)
            fail("Expected IOException for declared length > maxBytes")
        } catch (_: IOException) {}

        val unknownLenOverAuth = object : ResponseBody() {
            override fun contentType() = "text/plain".toMediaType()
            override fun contentLength() = -1L
            override fun source(): BufferedSource = Buffer().write(ByteArray(65))
        }
        try {
            GoogleAuthClient.readBoundedAuthBytes(unknownLenOverAuth, 64)
            fail("Expected IOException for streaming auth body > maxBytes")
        } catch (_: IOException) {}

        // UpdateRepository.readBoundedUtf8String & URL/size constants
        assertEquals(2 * 1024 * 1024, UpdateRepository.MAX_RELEASE_JSON_BYTES)
        assertEquals(256L * 1024L * 1024L, UpdateRepository.MAX_APK_DOWNLOAD_BYTES)
        assertNull(UpdateRepository.readBoundedUtf8String(null, 64))
        assertEquals("{\"ok\":true}", UpdateRepository.readBoundedUtf8String("{\"ok\":true}".toResponseBody("application/json".toMediaType()), 64))
        try {
            UpdateRepository.readBoundedUtf8String("x".repeat(65).toResponseBody("application/json".toMediaType()), 64)
            fail("Expected IOException for declared JSON > maxBytes")
        } catch (_: IOException) {}

        val unknownLenOverJson = object : ResponseBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = -1L
            override fun source(): BufferedSource = Buffer().writeUtf8("x".repeat(65))
        }
        try {
            UpdateRepository.readBoundedUtf8String(unknownLenOverJson, 64)
            fail("Expected IOException for streaming JSON > maxBytes")
        } catch (_: IOException) {}

        assertTrue(
            UpdateRepository.isAllowedInitialDownloadUrl(
                "https://github.com/Reiux/lumen-android/releases/download/v1.3.2/lumen-release.apk"
            )
        )
        assertFalse(
            UpdateRepository.isAllowedInitialDownloadUrl(
                "http://github.com/Reiux/lumen-android/releases/download/v1.3.2/lumen-release.apk"
            )
        )
        assertFalse(
            UpdateRepository.isAllowedInitialDownloadUrl(
                "https://github.com/OtherUser/lumen-android/releases/download/v1.3.2/lumen-release.apk"
            )
        )
        assertFalse(
            UpdateRepository.isAllowedInitialDownloadUrl(
                "https://user:pass@github.com/Reiux/lumen-android/releases/download/v1.3.2/lumen-release.apk"
            )
        )
        assertFalse(
            UpdateRepository.isAllowedInitialDownloadUrl(
                "https://github.com/Reiux/lumen-android/releases/download/../evil.apk"
            )
        )
        assertTrue(
            UpdateRepository.isAllowedRedirectUrl(
                "https://objects.githubusercontent.com/github-production-release-asset/foo.apk".toHttpUrl()
            )
        )
        assertFalse(
            UpdateRepository.isAllowedRedirectUrl(
                "https://evil.example.com/foo.apk".toHttpUrl()
            )
        )

        // UpdateRepository.downloadUpdate rejects oversized APK and SHA-256 mismatch without network
        val oversizedBody = object : ResponseBody() {
            override fun contentType() = "application/vnd.android.package-archive".toMediaType()
            override fun contentLength() = UpdateRepository.MAX_APK_DOWNLOAD_BYTES + 1L
            override fun source(): BufferedSource = Buffer()
        }
        val mockOversizedClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(oversizedBody)
                    .build()
            })
            .build()
        val updateRepo = UpdateRepository(mockOversizedClient)
        val destApk = File(tempFolder.newFolder("upd"), "lumen.apk")
        try {
            runBlocking {
                updateRepo.downloadUpdate(
                    "https://github.com/Reiux/lumen-android/releases/download/v9.9.9/lumen-release.apk",
                    destApk
                ).collect()
            }
            fail("Expected IOException for oversized APK contentLength")
        } catch (_: IOException) {
            assertFalse(File(destApk.parentFile, "${destApk.name}.part").exists())
            assertFalse(destApk.exists())
        }

        // Verify SHA-256 verification fence extracted from release notes format
        val realBytes = "synthetic-apk-payload".toByteArray()
        val realSha = MessageDigest.getInstance("SHA-256").digest(realBytes).joinToString("") { "%02x".format(it) }
        val expectedAssetName = if (BuildConfig.DEBUG) "lumen-test-debug.apk" else "lumen-release.apk"
        val releaseJson = """
            {
              "tag_name": "v99.0.0",
              "body": "### Verification\n```text\n$realSha  $expectedAssetName\n```",
              "assets": [
                {
                  "name": "$expectedAssetName",
                  "browser_download_url": "https://github.com/Reiux/lumen-android/releases/download/v99.0.0/$expectedAssetName"
                }
              ]
            }
        """.trimIndent()
        var serveTamperedApk = false
        val mockVerifyClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val urlStr = chain.request().url.toString()
                val body = if (urlStr.contains("api.github.com")) {
                    releaseJson.toResponseBody("application/json".toMediaType())
                } else {
                    val payload = if (serveTamperedApk) "tampered-apk-payload".toByteArray() else realBytes
                    payload.toResponseBody("application/vnd.android.package-archive".toMediaType())
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            })
            .build()
        val verifyRepo = UpdateRepository(mockVerifyClient)
        val info = runBlocking { verifyRepo.checkForUpdates("1.0.0") }
        assertNotNull(info)
        assertTrue(info!!.isUpdateAvailable)

        serveTamperedApk = true
        val tamperedDest = File(tempFolder.newFolder("upd_tamper"), "tampered.apk")
        try {
            runBlocking { verifyRepo.downloadUpdate(info.downloadUrl, tamperedDest).collect() }
            fail("Expected SecurityException for SHA-256 mismatch")
        } catch (_: SecurityException) {
            assertFalse(File(tamperedDest.parentFile, "${tamperedDest.name}.part").exists())
            assertFalse(tamperedDest.exists())
        }

        serveTamperedApk = false
        val cleanDest = File(tempFolder.newFolder("upd_ok"), "verified.apk")
        runBlocking { verifyRepo.downloadUpdate(info.downloadUrl, cleanDest).collect() }
        assertTrue(cleanDest.exists())
        assertArrayEquals(realBytes, cleanDest.readBytes())

        // OemIntentHelper system app flag check
        assertFalse(OemIntentHelper.isSystemAppFlags(0))
        assertTrue(OemIntentHelper.isSystemAppFlags(ApplicationInfo.FLAG_SYSTEM))
        assertTrue(OemIntentHelper.isSystemAppFlags(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
        assertNotEquals(0, ApplicationInfo.FLAG_SYSTEM)
    }
}
