package com.reiux.lumen.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PickerFilenameAndStagingTest {

    @Test
    fun testChooseDisplayName_pickerCase_preservesDisplayNameAndOriginalCase() {
        val displayName = "Screenshot 2026-10-30-15-00-05-47.JPG"
        val dataPath: String? = null
        val lastSegment = "209290"
        val mimeExt = "jpg"

        val result = MediaFileReader.chooseDisplayName(displayName, dataPath, lastSegment, mimeExt)
        assertEquals("Screenshot 2026-10-30-15-00-05-47.JPG", result)
    }

    @Test
    fun testChooseDisplayName_dataPresent_returnsFileNameFromPath() {
        val displayName: String? = null
        val dataPath = "/sdcard/DCIM/Camera/IMG_20261030_120000.JPG"
        val lastSegment = "100"
        val mimeExt = "jpg"

        val result = MediaFileReader.chooseDisplayName(displayName, dataPath, lastSegment, mimeExt)
        assertEquals("IMG_20261030_120000.JPG", result)
    }

    @Test
    fun testChooseDisplayName_bothMissing_returnsSegmentAndMimeExtension() {
        val displayName: String? = null
        val dataPath: String? = null
        val lastSegment = "209290"
        val mimeExt = "jpeg"

        val result = MediaFileReader.chooseDisplayName(displayName, dataPath, lastSegment, mimeExt)
        assertEquals("209290.jpeg", result)
    }

    @Test
    fun testChooseDisplayName_pathTraversalSanitizedToLastComponent() {
        val resultTraversal1 = MediaFileReader.chooseDisplayName("../x.jpg", null, "1", "jpg")
        assertEquals("x.jpg", resultTraversal1)

        val resultTraversal2 = MediaFileReader.chooseDisplayName("a/b.jpg", null, "1", "jpg")
        assertEquals("b.jpg", resultTraversal2)

        val resultTraversal3 = MediaFileReader.chooseDisplayName("../../../etc/passwd", null, "1", "jpg")
        assertEquals("passwd", resultTraversal3)
    }

    @Test
    fun testCreateDistinctStagingFile_duplicateNamesUseDistinctSubfolders() {
        val stagingDir = File("/tmp/test_staging")
        val fileName = "Screenshot.jpg"

        val file0 = MediaFileReader.createDistinctStagingFile(stagingDir, fileName, occurrenceIndex = 0)
        val file1 = MediaFileReader.createDistinctStagingFile(stagingDir, fileName, occurrenceIndex = 1)
        val file2 = MediaFileReader.createDistinctStagingFile(stagingDir, fileName, occurrenceIndex = 2)

        assertEquals("Screenshot.jpg", file0.name)
        assertEquals("Screenshot.jpg", file1.name)
        assertEquals("Screenshot.jpg", file2.name)

        assertEquals(File(stagingDir, "Screenshot.jpg").absolutePath, file0.absolutePath)
        assertEquals(File(File(stagingDir, "dup_1"), "Screenshot.jpg").absolutePath, file1.absolutePath)
        assertEquals(File(File(stagingDir, "dup_2"), "Screenshot.jpg").absolutePath, file2.absolutePath)
    }
}
