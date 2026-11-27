package com.reiux.lumen.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileReaderOriginalUriTest {

    @Test
    fun directMediaStoreImageAndVideoContentUris_returnTrue() {
        assertTrue(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "content",
                authority = "media",
                path = "/external/images/media/123"
            )
        )
        assertTrue(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "content",
                authority = "media",
                path = "/external/video/media/456"
            )
        )
    }

    @Test
    fun directMediaStoreContentUris_areCaseInsensitiveForSchemeAndAuthority() {
        assertTrue(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "CONTENT",
                authority = "MEDIA",
                path = "/external/images/media/123"
            )
        )
        assertTrue(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "Content",
                authority = "Media",
                path = "/external/video/media/456"
            )
        )
    }

    @Test
    fun androidPhotoPickerUris_returnFalse() {
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "content",
                authority = "media",
                path = "/picker/0/com.android.providers.media.photopicker/media/123"
            )
        )
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "content",
                authority = "media",
                path = "/picker_get_content/0/com.android.providers.media.photopicker/media/123"
            )
        )
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "content",
                authority = "media",
                path = "/PICKER/0/com.android.providers.media.photopicker/media/789"
            )
        )
    }

    @Test
    fun nonMediaStoreProvidersAndFileUris_returnFalse() {
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "content",
                authority = "com.android.providers.media.documents",
                path = "/document/image:123"
            )
        )
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "content",
                authority = "com.google.android.apps.nbu.files.provider",
                path = "/1/file/content%3A%2F%2Fmedia%2Fexternal%2Fimages%2Fmedia%2F123/ORIGINAL/NONE/image%2Fjpeg/123"
            )
        )
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "file",
                authority = "",
                path = "/storage/emulated/0/DCIM/Camera/IMG_20261127_000001.jpg"
            )
        )
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "file",
                authority = null,
                path = "/storage/emulated/0/Movies/VID_20261127_000001.mp4"
            )
        )
    }

    @Test
    fun nullAndBlankComponents_returnFalse() {
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = null,
                authority = null,
                path = null
            )
        )
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "",
                authority = "",
                path = ""
            )
        )
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "   ",
                authority = "   ",
                path = "   "
            )
        )
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = null,
                authority = "media",
                path = "/external/images/media/123"
            )
        )
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "",
                authority = "media",
                path = "/external/images/media/123"
            )
        )
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "content",
                authority = null,
                path = "/external/images/media/123"
            )
        )
        assertFalse(
            MediaFileReader.isDirectMediaStoreContentUri(
                scheme = "content",
                authority = "  ",
                path = "/external/images/media/123"
            )
        )
    }
}
