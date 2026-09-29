package com.miguenduval.mimicdj2.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLibraryTrackUrlTest {
    @Test
    fun http_path_matches_observed_eaas_download_shape() {
        val rawKey = "<C:/Users/migue/Downloads/Подождём.flac>"
        val path = "/download/" + encodeHttpPathComponent(rawKey)

        assertEquals(
            "/download/%3CC%3A%2FUsers%2Fmigue%2FDownloads%2F%D0%9F%D0%BE%D0%B4%D0%BE%D0%B6%D0%B4%D1%91%D0%BC.flac%3E",
            path
        )
        assertTrue(path.startsWith("/download/"))
    }
}
