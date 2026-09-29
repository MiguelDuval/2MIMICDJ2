
package com.miguenduval.mimicdj2.server

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.OutputStream
import java.io.InputStream
import java.util.Locale

/**
 * EAAS HTTP file endpoint observed in the direct Prime GO capture.
 *
 * The captured client request was:
 *   GET /download/<URL-encoded file key> HTTP/1.1
 *
 * The real server answered HTTP/1.1 200 OK and then streamed the complete
 * audio file over the same TCP/50020 connection.
 */
class EaasHttpFileServer(
    private val library: MediaLibrary,
    private val diagnostics: ServerDiagnostics
) {
    private data class HttpRequest(
        val method: String,
        val target: String,
        val headers: Map<String, String>
    )

    private data class ByteRange(
        val start: Long,
        val endInclusive: Long
    ) {
        val length: Long get() = endInclusive - start + 1L
    }

    fun handle(fd: java.io.FileDescriptor) {
        var input: InputStream? = null
        var output: OutputStream? = null
        try {
            val inputFd = Os.dup(fd)
            val outputFd = Os.dup(fd)
            input = BufferedInputStream(FileInputStream(inputFd), 16 * 1024)
            output = BufferedOutputStream(java.io.FileOutputStream(outputFd), 64 * 1024)

            val request = readRequest(input) ?: return
            diagnostics.lastClientContact = System.currentTimeMillis()

            val target = request.target.substringBefore('?')
            diagnostics.info("EAAS-HTTP", "${request.method} $target")

            when {
                request.method == "GET" && target == "/ping" -> {
                    writeTextResponse(
                        output,
                        status = 200,
                        reason = "OK",
                        body = ByteArray(0)
                    )
                }

                request.method == "HEAD" && target == "/ping" -> {
                    writeTextResponse(
                        output,
                        status = 200,
                        reason = "OK",
                        body = ByteArray(0),
                        includeBody = false
                    )
                }

                request.method != "GET" && request.method != "HEAD" -> {
                    writeTextResponse(
                        output,
                        status = 405,
                        reason = "Method Not Allowed",
                        body = "Method Not Allowed".toByteArray(Charsets.UTF_8)
                    )
                }

                !target.startsWith("/download/") -> {
                    diagnostics.errors404.incrementAndGet()
                    writeTextResponse(
                        output,
                        status = 404,
                        reason = "Not Found",
                        body = "Not Found".toByteArray(Charsets.UTF_8)
                    )
                }

                else -> serveDownload(
                    request = request,
                    target = target,
                    output = output
                )
            }
            output.flush()
        } catch (t: Throwable) {
            diagnostics.errors500.incrementAndGet()
            diagnostics.error("EAAS-HTTP", "HTTP client handling failed", t)
        } finally {
            runCatching { output?.close() }
            runCatching { input?.close() }
            runCatching { Os.close(fd) }
        }
    }

    private fun serveDownload(
        request: HttpRequest,
        target: String,
        output: OutputStream
    ) {
        diagnostics.fileRequests.incrementAndGet()
        diagnostics.lastFileTransfer = System.currentTimeMillis()

        val encodedKey = target.removePrefix("/download/")
        val decodedKey = Uri.decode(encodedKey)
            .removePrefix("<")
            .removeSuffix(">")
        val track = library.findByPathKey(decodedKey)
            ?: library.findByDisplayName(decodedKey)

        if (track == null) {
            diagnostics.errors404.incrementAndGet()
            diagnostics.warn("EAAS-HTTP", "Download key not found: $decodedKey")
            writeTextResponse(
                output,
                status = 404,
                reason = "Not Found",
                body = "Track not found".toByteArray(Charsets.UTF_8)
            )
            return
        }

        val pfd = runCatching { library.open(track) }.getOrNull()
        if (pfd == null) {
            diagnostics.openFileFailures.incrementAndGet()
            diagnostics.errors500.incrementAndGet()
            writeTextResponse(
                output,
                status = 500,
                reason = "Internal Server Error",
                body = "Unable to open track".toByteArray(Charsets.UTF_8)
            )
            return
        }

        pfd.use { descriptor ->
            val descriptorSize = runCatching { descriptor.statSize }.getOrDefault(-1L)
            val totalSize = when {
                descriptorSize > 0L -> descriptorSize
                track.sizeBytes > 0L -> track.sizeBytes
                else -> 0L
            }

            if (totalSize <= 0L) {
                diagnostics.errors500.incrementAndGet()
                writeTextResponse(
                    output,
                    status = 500,
                    reason = "Internal Server Error",
                    body = "Track has no readable size".toByteArray(Charsets.UTF_8)
                )
                return
            }

            val rangeHeader = request.headers["range"]
            val byteRange = if (rangeHeader == null) {
                null
            } else {
                diagnostics.rangeRequests.incrementAndGet()
                parseRange(rangeHeader, totalSize)
            }

            if (rangeHeader != null && byteRange == null) {
                diagnostics.errors416.incrementAndGet()
                val headers = buildString {
                    append("HTTP/1.1 416 Range Not Satisfiable\r\n")
                    append("Content-Range: bytes */$totalSize\r\n")
                    append("Content-Length: 0\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                output.write(headers.toByteArray(Charsets.US_ASCII))
                return
            }

            val start = byteRange?.start ?: 0L
            val end = byteRange?.endInclusive ?: (totalSize - 1L)
            val length = end - start + 1L
            val status = if (byteRange == null) 200 else 206
            val reason = if (status == 200) "OK" else "Partial Content"

            val headers = buildString {
                append("HTTP/1.1 $status $reason\r\n")
                append("Content-Type: ${track.mimeType}\r\n")
                append("Content-Length: $length\r\n")
                append("Accept-Ranges: bytes\r\n")
                if (byteRange != null) {
                    append("Content-Range: bytes $start-$end/$totalSize\r\n")
                }
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(headers.toByteArray(Charsets.US_ASCII))

            if (request.method == "HEAD") {
                output.flush()
                return
            }

            val stream = ParcelFileDescriptor.AutoCloseInputStream(
                ParcelFileDescriptor.dup(descriptor.fileDescriptor)
            )
            stream.use { fileInput ->
                fileInput.channel.position(start)
                streamExactly(fileInput, output, length)
            }

            diagnostics.bytesServed.addAndGet(length)
        }
    }

    private fun streamExactly(
        input: InputStream,
        output: OutputStream,
        length: Long
    ) {
        val buffer = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0L) {
            val wanted = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, wanted)
            if (read < 0) {
                throw java.io.EOFException(
                    "Track ended before Content-Length: remaining=$remaining"
                )
            }
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun parseRange(value: String, totalSize: Long): ByteRange? {
        val raw = value.trim()
        if (!raw.startsWith("bytes=", ignoreCase = true)) return null

        val spec = raw.substringAfter('=').substringBefore(',').trim()
        if (spec.isEmpty()) return null

        return when {
            spec.startsWith("-") -> {
                val suffix = spec.drop(1).toLongOrNull() ?: return null
                if (suffix <= 0L) return null
                val length = minOf(suffix, totalSize)
                ByteRange(totalSize - length, totalSize - 1L)
            }

            spec.contains('-') -> {
                val parts = spec.split('-', limit = 2)
                val start = parts[0].toLongOrNull() ?: return null
                if (start < 0L || start >= totalSize) return null

                val end = parts[1].toLongOrNull()?.coerceAtMost(totalSize - 1L)
                    ?: (totalSize - 1L)
                if (end < start) return null
                ByteRange(start, end)
            }

            else -> null
        }
    }

    private fun readRequest(input: InputStream): HttpRequest? {
        val bytes = ByteArrayOutputStream()

        while (bytes.size() < 16 * 1024) {
            val value = input.read()
            if (value < 0) break
            bytes.write(value)

            if (bytes.size() >= 4) {
                val tail = bytes.toByteArray()
                val size = tail.size
                if (
                    tail[size - 4] == 13.toByte() &&
                    tail[size - 3] == 10.toByte() &&
                    tail[size - 2] == 13.toByte() &&
                    tail[size - 1] == 10.toByte()
                ) {
                    break
                }
            }
        }

        if (bytes.size() == 0) return null

        val headerText = bytes.toString(Charsets.US_ASCII.name())
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull()?.trim().orEmpty()
        val parts = requestLine.split(" ")
        if (parts.size < 2) return null

        val headers = linkedMapOf<String, String>()
        for (line in lines.drop(1)) {
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().lowercase(Locale.US)
            val value = line.substring(separator + 1).trim()
            headers[name] = value
        }

        return HttpRequest(
            method = parts[0].uppercase(Locale.US),
            target = parts[1],
            headers = headers
        )
    }

    private fun writeTextResponse(
        output: OutputStream,
        status: Int,
        reason: String,
        body: ByteArray,
        includeBody: Boolean = true
    ) {
        val headers = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(headers.toByteArray(Charsets.US_ASCII))
        if (includeBody && body.isNotEmpty()) output.write(body)
    }
}
