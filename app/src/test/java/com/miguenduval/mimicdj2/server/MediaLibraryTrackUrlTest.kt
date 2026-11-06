package com.miguenduval.mimicdj2.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLibraryTrackUrlTest {
    @Test
    fun get_track_blob_url_is_the_bracketed_opaque_file_key_not_an_http_url() {
        val rawKey = "C:/Users/migue/Downloads/Подождём.flac"

        val blobKey = buildEaasTrackBlobKey(rawKey)

        assertEquals("<C:/Users/migue/Downloads/Подождём.flac>", blobKey)
        assertFalse(blobKey.startsWith("http://"))
        assertFalse(blobKey.startsWith("https://"))
    }

    @Test
    fun prime_go_download_path_is_built_from_the_bracketed_blob_key() {
        val rawKey = "C:/Users/migue/Downloads/Подождём.flac"
        val blobKey = buildEaasTrackBlobKey(rawKey)
        val path = "/download/" + encodeHttpPathComponent(blobKey)

        assertEquals(
            "/download/%3CC%3A%2FUsers%2Fmigue%2FDownloads%2F%D0%9F%D0%BE%D0%B4%D0%BE%D0%B6%D0%B4%D1%91%D0%BC.flac%3E",
            path
        )
        assertTrue(path.startsWith("/download/"))
    }

    @Test
    fun saf_content_uri_is_preserved_as_an_opaque_blob_key() {
        val rawKey =
            "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fmix%20one.mp3"

        assertEquals("<$rawKey>", buildEaasTrackBlobKey(rawKey))
    }
}
