package com.miguenduval.mimicdj2.server

/**
 * Splits a finite result set into the response batches used by the Engine
 * server-streaming GetTracks and SearchTracks RPCs.
 *
 * A zero/omitted page size means one response containing the complete result.
 * An empty result still produces one empty response for compatibility with
 * clients that expect an initial list message.
 */
internal object EaasGrpcResponseBatcher {
    fun <T> chunk(items: List<T>, requestedPageSize: Int): List<List<T>> {
        if (items.isEmpty()) return listOf(emptyList())
        val batchSize = if (requestedPageSize > 0) requestedPageSize else items.size
        return items.chunked(batchSize)
    }
}
