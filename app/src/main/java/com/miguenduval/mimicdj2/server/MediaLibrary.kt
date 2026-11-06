package com.miguenduval.mimicdj2.server

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.util.Locale
import java.util.ArrayDeque
import java.security.MessageDigest

/**
 * Read-only view of Android's indexed local audio library.
 *
 * We expose a stable track id in metadata and an opaque, bracketed file key
 * in TrackBlobUrl.url. Prime GO URL-encodes that key when it requests
 * /download/<key> from the paired HTTP endpoint on port 50020. Android
 * content URIs can be retained as opaque keys and resolved only by this app.
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
        fun blobKey(): String = buildEaasTrackBlobKey(pathKey)

        fun httpPath(): String = "/download/" + encodeHttpPathComponent(blobKey())
    }

    companion object {
        const val HTTP_PORT = 50020
        const val PREFS_NAME = "media_library"
        const val KEY_SELECTED_TREE_URI = "selected_tree_uri"
        private const val CACHE_TTL_MS = 1500L
        private const val MAX_TREE_DOCUMENTS = 10000
        private const val MAX_TREE_DEPTH = 32
    }

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    @Volatile
    private var cached: List<Track> = emptyList()
    @Volatile
    private var cachedAtMs: Long = 0L
    @Volatile
    var lastScanError: String? = null
        private set

    @Synchronized
    fun snapshot(forceRefresh: Boolean = false): List<Track> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && now - cachedAtMs < CACHE_TTL_MS) {
            return cached
        }

        val result = runCatching { queryTracks() }
            .onSuccess { lastScanError = null }
            .getOrElse { error ->
                lastScanError = "${error.javaClass.simpleName}: ${error.message ?: "media scan failed"}"
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

    fun findByReference(value: String): Track? =
        findById(value) ?: findByPathKey(value) ?: findByDisplayName(value)

    fun open(track: Track): android.os.ParcelFileDescriptor? =
        resolver.openFileDescriptor(track.uri, "r")

    fun fileSize(track: Track): Long {
        if (track.sizeBytes > 0L) return track.sizeBytes
        return runCatching {
            open(track)?.use { descriptor -> descriptor.statSize.coerceAtLeast(0L) } ?: 0L
        }.getOrDefault(0L)
    }

    fun selectedTreeUriString(): String? =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_TREE_URI, null)

    fun refresh(): List<Track> = snapshot(forceRefresh = true)

    private fun queryTracks(): List<Track> {
        val selected = selectedTreeUriString()?.takeIf { it.isNotBlank() }
        val treeUri = selected?.let { runCatching { Uri.parse(it) }.getOrNull() }
        return if (treeUri != null) querySelectedTree(treeUri) else queryMediaStoreTracks()
    }

    private fun querySelectedTree(treeUri: Uri): List<Track> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val pending = ArrayDeque<Pair<String, Int>>()
        val visited = HashSet<String>()
        val output = ArrayList<Track>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        pending.addLast(rootId to 0)
        while (pending.isNotEmpty() && visited.size < MAX_TREE_DOCUMENTS) {
            val (parentId, depth) = pending.removeFirst()
            if (!visited.add(parentId)) continue

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idColumn < 0 || nameColumn < 0) return@use

                while (cursor.moveToNext() && visited.size + output.size < MAX_TREE_DOCUMENTS) {
                    val documentId = cursor.getString(idColumn) ?: continue
                    val displayName = cursor.getString(nameColumn).orEmpty()
                    if (displayName.isBlank()) continue

                    val mimeType = if (mimeColumn >= 0 && !cursor.isNull(mimeColumn)) {
                        cursor.getString(mimeColumn)
                    } else {
                        null
                    }

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < MAX_TREE_DEPTH) pending.addLast(documentId to depth + 1)
                        continue
                    }
                    if (!isSupportedAudioDocument(mimeType, displayName)) continue

                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val size = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                        cursor.getLong(sizeColumn).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    val modifiedMillis = if (modifiedColumn >= 0 && !cursor.isNull(modifiedColumn)) {
                        cursor.getLong(modifiedColumn).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    val mime = mimeType?.takeIf { it.startsWith("audio/", ignoreCase = true) }
                        ?: mimeFromName(displayName)

                    output += Track(
                        id = stableDocumentTrackId(documentUri.toString()),
                        uri = documentUri,
                        pathKey = documentUri.toString(),
                        displayName = displayName,
                        title = displayName.substringBeforeLast('.', displayName),
                        artist = "Unknown Artist",
                        album = "Unknown Album",
                        year = 0,
                        durationMs = 0L,
                        sizeBytes = size,
                        dateAddedSeconds = modifiedMillis / 1000L,
                        mimeType = mime
                    )
                }
            }
        }

        return output.sortedWith(compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.id })
    }

    private fun queryMediaStoreTracks(): List<Track> {
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
        value.trim().removePrefix("<").removeSuffix(">").replace('\\', '/')

    private fun mimeFromName(name: String): String =
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase(Locale.US))
            ?: "application/octet-stream"
}


/**
 * TrackBlobUrl.url is an opaque file key, not an absolute HTTP URL.
 * Engine DJ wraps the path in angle brackets; Prime GO constructs GET
 * /download/<URL-encoded key> against the host advertised by EAAS.
 */
internal fun buildEaasTrackBlobKey(pathKey: String): String {
    require(pathKey.isNotBlank()) { "EAAS track path key must not be blank" }
    return "<$pathKey>"
}

private const val HEX_DIGITS = "0123456789ABCDEF"

/**
 * Percent-encode one HTTP path component using UTF-8.
 *
 * This deliberately mirrors android.net.Uri.encode()'s documented allowlist,
 * but is kept platform-independent so JVM unit tests exercise the real wire
 * encoding instead of the Android local-test stub.
 */
internal fun encodeHttpPathComponent(value: String): String {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val out = StringBuilder(bytes.size)
    bytes.forEach { byte ->
        val c = byte.toInt() and 0xFF
        val allowed =
            (c in 'A'.code..'Z'.code) ||
            (c in 'a'.code..'z'.code) ||
            (c in '0'.code..'9'.code) ||
            c == '_'.code || c == '-'.code || c == '!'.code || c == '.'.code ||
            c == '~'.code || c == '\''.code || c == '('.code || c == ')'.code || c == '*'.code
        if (allowed) {
            out.append(c.toChar())
        } else {
            out.append('%')
            out.append(HEX_DIGITS[c ushr 4])
            out.append(HEX_DIGITS[c and 0x0F])
        }
    }
    return out.toString()
}

private fun Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)

private fun Cursor.getIntOrNull(index: Int): Int? =
    if (isNull(index)) null else getInt(index)

private fun Cursor.getLongOrNull(index: Int): Long? =
    if (isNull(index)) null else getLong(index)


private val supportedAudioExtensions = setOf(
    "mp3", "m4a", "aac", "wav", "wave", "flac", "ogg", "opus",
    "aif", "aiff", "alac", "wma", "ape", "dsf", "dff"
)

internal fun isSupportedAudioDocument(mimeType: String?, displayName: String): Boolean {
    val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US)
    if (!normalizedMime.isNullOrBlank() && normalizedMime.startsWith("audio/")) return true
    val extension = displayName.substringAfterLast('.', "").lowercase(Locale.US)
    return extension in supportedAudioExtensions
}

internal fun stableDocumentTrackId(documentUri: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(documentUri.toByteArray(Charsets.UTF_8))
    return "saf-" + digest.take(16).joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }
}
