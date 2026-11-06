package com.miguenduval.mimicdj2.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLibrarySelectionTest {
    @Test
    fun recognizes_audio_mime_types_and_common_dj_formats() {
        assertTrue(isSupportedAudioDocument("audio/mpeg", "track.bin"))
        assertTrue(isSupportedAudioDocument("application/octet-stream", "track.FLAC"))
        assertTrue(isSupportedAudioDocument(null, "track.wav"))
        assertTrue(isSupportedAudioDocument(null, "track.aiff"))
        assertFalse(isSupportedAudioDocument("application/pdf", "manual.pdf"))
        assertFalse(isSupportedAudioDocument(null, "cover.jpg"))
    }

    @Test
    fun document_track_id_is_stable_and_namespaced() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Ftrack.flac"
        val first = stableDocumentTrackId(uri)
        val second = stableDocumentTrackId(uri)

        assertEquals(first, second)
        assertTrue(first.startsWith("saf-"))
        assertEquals(36, first.length)
        assertFalse(first.contains("track.flac"))
    }
}
