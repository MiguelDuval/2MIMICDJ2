package com.miguenduval.mimicdj2.server

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.util.Locale

/**
 * Read-only view of Android's indexed local audio library.
 *
 * The Engine/Prime GO protocol never sees Android content:// URIs directly.
 * We expose a stable track id to gRPC and an URL-encoded local-path key to
 * the HTTP /download endpoint, matching the wire shape observed from Engine
 * Desktop (including the angle-bracket wrapper around the file key).
 */
class MediaLibrary(context: Context) {
    data class Track(
        val id: String,
        val uri: Uri,
        val pathKey: String,
        val displayName: String,
        val title: String,
        val artist: String,
        val album: String,
        val year: Int,
        val durationMs: Long,
        val sizeBytes: Long,
        val dateAddedSeconds: Long,
        val mimeType: String
    ) {
        fun httpPath(): String = "/download/" + Uri.encode("<$pathKey>")

        fun httpUrl(host: String, port: Int = HTTP_PORT): String =
            "http://$host:$port" + httpPath()
    }

    companion object {
        const val HTTP_PORT = 50020
        private const val CACHE_TTL_MS = 1500L
    }

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    @Volatile
    private var cached: List<Track> = emptyList()
    @Volatile
    private var cachedAtMs: Long = 0L

    @Synchronized
    fun snapshot(forceRefresh: Boolean = false): List<Track> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && now - cachedAtMs < CACHE_TTL_MS) {
            return cached
        }

        val result = runCatching { queryTracks() }.getOrElse {
            cached = emptyList()
            cachedAtMs = now
            return cached
        }

        cached = result
        cachedAtMs = now
        return result
    }

    fun findById(id: String): Track? =
        snapshot().firstOrNull { it.id == id }

    fun findByPathKey(pathKey: String): Track? {
        val normalized = normalizeKey(pathKey)
        return snapshot().firstOrNull { normalizeKey(it.pathKey) == normalized }
    }

    fun findByDisplayName(name: String): Track? =
        snapshot().firstOrNull { it.displayName == name }

    fun open(track: Track): android.os.ParcelFileDescriptor? =
        resolver.openFileDescriptor(track.uri, "r")

    private fun queryTracks(): List<Track> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA
        )

        val selection = MediaStore.Audio.Media.MIME_TYPE + " LIKE ?"
        val selectionArgs = arrayOf("audio/%")
        val sortOrder = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC, " +
                MediaStore.Audio.Media._ID + " ASC"

        val output = ArrayList<Track>()
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaId.toString()
                )

                val displayName = cursor.getStringOrNull(nameCol).orEmpty()
                val title = cursor.getStringOrNull(titleCol).takeUnless { it.isNullOrBlank() }
                    ?: displayName.substringBeforeLast('.', displayName)
                val artist = cursor.getStringOrNull(artistCol).takeUnless { it.isNullOrBlank() }
                    ?: "Unknown Artist"
                val album = cursor.getStringOrNull(albumCol).takeUnless { it.isNullOrBlank() }
                    ?: "Unknown Album"
                val year = cursor.getIntOrNull(yearCol) ?: 0
                val durationMs = cursor.getLongOrNull(durationCol) ?: 0L
                val sizeBytes = cursor.getLongOrNull(sizeCol) ?: -1L
                val dateAdded = cursor.getLongOrNull(addedCol) ?: 0L
                val mime = cursor.getStringOrNull(mimeCol).takeUnless { it.isNullOrBlank() }
                    ?: mimeFromName(displayName)

                val dataPath = cursor.getStringOrNull(dataCol)
                val key = dataPath.takeUnless { it.isNullOrBlank() }
                    ?: uri.toString()

                output += Track(
                    id = mediaId.toString(),
                    uri = uri,
                    pathKey = key,
                    displayName = displayName,
                    title = title,
                    artist = artist,
                    album = album,
                    year = year,
                    durationMs = durationMs,
                    sizeBytes = sizeBytes.coerceAtLeast(0L),
                    dateAddedSeconds = dateAdded,
                    mimeType = mime
                )
            }
        }

        return output
    }

    private fun normalizeKey(value: String): String =
        value.trim().removePrefix("<").removeSuffix("<")
            .removeSuffix(">").replace("\\\\", "/")
            .lowercase(Locale.US)

    private fun mimeFromName(name: String): String =
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase(Locale.US))
            ?: "application/octet-stream"
}

private fun Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)

private fun Cursor.getIntOrNull(index: Int): Int? =
    if (isNull(index)) null else getInt(index)

private fun Cursor.getLongOrNull(index: Int): Long? =
    if (isNull(index)) null else getLong(index)
