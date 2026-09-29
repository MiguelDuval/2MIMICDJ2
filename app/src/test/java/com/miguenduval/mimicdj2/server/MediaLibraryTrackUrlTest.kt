package com.miguenduval.mimicdj2.server

import android.net.Uri
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLibraryTrackUrlTest {
    @Test
    fun http_path_matches_observed_eaas_download_shape() {
        val track = MediaLibrary.Track(
            id = "123",
            uri = Uri.parse("content://media/external/audio/media/123"),
            pathKey = "/storage/emulated/0/Music/Подождём.flac",
            displayName = "Подождём.flac",
            title = "Подождём",
            artist = "Artist",
            album = "Album",
            year = 2026,
            durationMs = 180_000,
            sizeBytes = 41_576_584,
            dateAddedSeconds = 1_760_000_000,
            mimeType = "audio/flac"
        )

        val path = track.httpPath()
        assertTrue(path.startsWith("/download/"))
        assertTrue(path.contains("%3C"))
        assertTrue(path.contains("%3E"))
        assertTrue(path.contains("%2F"))
        assertTrue(path.contains("%D0"))
    }
}
