package com.reiux.lumen.util

import com.reiux.lumen.data.model.BackupFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FolderScanAndWatcherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testWatcherPathMatchingRule() {
        val folderNormal = BackupFolder("/sdcard/Pictures", "Pictures", enabled = true, includeSubfolders = false)
        val folderSubfolders = BackupFolder("/sdcard/Pictures", "Pictures", enabled = true, includeSubfolders = true)

        // 1. Direct parent matches in both modes
        assertTrue(FolderWatcherManager.isPathMatchingFolder("/sdcard/Pictures/photo.jpg", folderNormal))
        assertTrue(FolderWatcherManager.isPathMatchingFolder("/sdcard/Pictures/photo.jpg", folderSubfolders))

        // 2. Subdirectory file only matches when includeSubfolders is true
        assertFalse(FolderWatcherManager.isPathMatchingFolder("/sdcard/Pictures/Vacation/photo.jpg", folderNormal))
        assertTrue(FolderWatcherManager.isPathMatchingFolder("/sdcard/Pictures/Vacation/photo.jpg", folderSubfolders))

        // 3. Similar prefix path like Download2 does NOT match Download
        val folderDownload = BackupFolder("/sdcard/Download", "Downloads", enabled = true, includeSubfolders = true)
        assertFalse(FolderWatcherManager.isPathMatchingFolder("/sdcard/Download2/photo.jpg", folderDownload))
    }

    @Test
    fun testScannerFiltersAndHiddenDirs() {
        val rootDir = tempFolder.newFolder("TestCamera")
        val validImage = File(rootDir, "photo1.jpg").apply { writeText("fake image bytes") }
        val hiddenDir = File(rootDir, ".hidden").apply { mkdirs() }
        File(hiddenDir, "secret.jpg").apply { writeText("hidden bytes") }

        val noMediaDir = File(rootDir, "NoMediaSub").apply { mkdirs() }
        File(noMediaDir, ".nomedia").createNewFile()
        File(noMediaDir, "ignored.jpg").apply { writeText("nomedia bytes") }

        val folder = BackupFolder(rootDir.absolutePath, "TestCamera", enabled = true, includeSubfolders = true)

        val scannedFiles = MediaFileReader.scanMediaFiles(rootDir)
        assertEquals(1, scannedFiles.size)
        assertEquals(validImage.absolutePath, scannedFiles[0].absolutePath)
    }

    @Test
    fun testWatermarkAndEnabledFolderFiltering() {
        val cameraFolder = BackupFolder("/sdcard/DCIM/Camera", "Camera Roll", enabled = true)

        val cameraPhoto = "/sdcard/DCIM/Camera/IMG_001.jpg"
        val screenshot = "/sdcard/Pictures/Screenshots/Screenshot_001.png"

        // 1. Screenshot in disabled folder is NOT matched by enabled folders list
        val enabledFolders = listOf(cameraFolder)
        assertFalse(enabledFolders.any { FolderWatcherManager.isMediaStoreItemInFolder(screenshot, "Pictures/Screenshots/", "Screenshots", it) })

        // 2. Camera photo in enabled folder IS matched
        assertTrue(enabledFolders.any { FolderWatcherManager.isMediaStoreItemInFolder(cameraPhoto, "DCIM/Camera/", "Camera Roll", it) })

        // 3. Watermark advancement and hold safety via production FolderWatcherManager
        assertEquals(1501L, FolderWatcherManager.computeSafeWatermark(lastWatermark = 1500L, maxSeenId = 1501L, firstNotReadyId = null))
        assertEquals(1500L, FolderWatcherManager.computeSafeWatermark(lastWatermark = 1500L, maxSeenId = 1500L, firstNotReadyId = null))
        assertEquals(1504L, FolderWatcherManager.computeSafeWatermark(lastWatermark = 1500L, maxSeenId = 1510L, firstNotReadyId = 1505L))
        assertTrue(FolderWatcherManager.shouldHoldWatermark(fileReady = false, dateAddedSec = 10_000L - 30L, nowSec = 10_000L))
        assertFalse(FolderWatcherManager.shouldHoldWatermark(fileReady = true, dateAddedSec = 10_000L - 30L, nowSec = 10_000L))
    }

    @Test
    fun testDiscoveredFolderHelpers() {
        assertTrue(MediaFileReader.shouldSkipDiscoveredFolderPath("/storage/emulated/0/.thumbnails"))
        assertTrue(MediaFileReader.shouldSkipDiscoveredFolderPath("/storage/emulated/0/Pictures/.trash"))
        assertTrue(MediaFileReader.shouldSkipDiscoveredFolderPath("/storage/emulated/0/Android/data/com.foo/files"))
        assertFalse(MediaFileReader.shouldSkipDiscoveredFolderPath("/storage/emulated/0/DCIM/Camera"))
        assertFalse(MediaFileReader.shouldSkipDiscoveredFolderPath("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"))

        assertEquals(
            "/storage/emulated/0/DCIM/Camera",
            MediaFileReader.resolveFolderPathFromMediaRow(
                dataPath = "/storage/emulated/0/DCIM/Camera/IMG_001.jpg",
                relativePath = "DCIM/Camera/",
                externalRootPath = "/storage/emulated/0"
            )
        )
        assertEquals(
            "/storage/emulated/0/Pictures/Snapseed",
            MediaFileReader.resolveFolderPathFromMediaRow(
                dataPath = null,
                relativePath = "Pictures/Snapseed/",
                externalRootPath = "/storage/emulated/0"
            )
        )
        assertEquals(
            "Pictures/Snapseed",
            MediaFileReader.toShortDisplayPath("/storage/emulated/0/Pictures/Snapseed/")
        )
    }

    @Test
    fun folderMediaKindClassifiesPhotosVideosMixedAndSubfolders() {
        assertEquals(
            com.reiux.lumen.ui.components.FolderMediaKind.PHOTOS,
            com.reiux.lumen.ui.components.resolveFolderMediaKind(imageCount = 12, videoCount = 0)
        )
        assertEquals(
            com.reiux.lumen.ui.components.FolderMediaKind.VIDEOS,
            com.reiux.lumen.ui.components.resolveFolderMediaKind(imageCount = 0, videoCount = 5)
        )
        assertEquals(
            com.reiux.lumen.ui.components.FolderMediaKind.MEDIA,
            com.reiux.lumen.ui.components.resolveFolderMediaKind(imageCount = 12, videoCount = 5)
        )
        assertEquals(
            null,
            com.reiux.lumen.ui.components.resolveFolderMediaKind(imageCount = 0, videoCount = 0)
        )

        val discovered = listOf(
            LocalMediaFolder("/storage/emulated/0/Pictures/Telegram", "Telegram", 20, 20, 0, null, 100L),
            LocalMediaFolder("/storage/emulated/0/Movies/Telegram", "Telegram", 8, 0, 8, null, 200L),
            LocalMediaFolder("/storage/emulated/0/Movies/Telegram/Clips", "Clips", 3, 3, 0, null, 300L)
        )

        assertEquals(
            com.reiux.lumen.ui.components.FolderMediaKind.PHOTOS,
            com.reiux.lumen.ui.components.resolveFolderMediaKindForPath(
                BackupFolder("/storage/emulated/0/Pictures/Telegram", "Telegram", true, includeSubfolders = false),
                discovered
            )
        )
        assertEquals(
            com.reiux.lumen.ui.components.FolderMediaKind.VIDEOS,
            com.reiux.lumen.ui.components.resolveFolderMediaKindForPath(
                BackupFolder("/storage/emulated/0/Movies/Telegram", "Telegram", true, includeSubfolders = false),
                discovered
            )
        )
        assertEquals(
            com.reiux.lumen.ui.components.FolderMediaKind.MEDIA,
            com.reiux.lumen.ui.components.resolveFolderMediaKindForPath(
                BackupFolder("/storage/emulated/0/Movies/Telegram", "Telegram", true, includeSubfolders = true),
                discovered
            )
        )
    }
}
