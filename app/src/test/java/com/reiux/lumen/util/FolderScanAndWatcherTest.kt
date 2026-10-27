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
}
