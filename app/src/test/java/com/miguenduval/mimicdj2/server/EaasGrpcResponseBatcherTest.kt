package com.miguenduval.mimicdj2.server

import org.junit.Assert.assertEquals
import org.junit.Test

class EaasGrpcResponseBatcherTest {
    @Test
    fun splits_all_results_into_requested_page_sizes_without_dropping_tail() {
        val batches = EaasGrpcResponseBatcher.chunk(
            items = (1..7).toList(),
            requestedPageSize = 3
        )

        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7)), batches)
        assertEquals(7, batches.flatten().size)
    }

    @Test
    fun zero_page_size_returns_every_result_in_one_response() {
        assertEquals(
            listOf(listOf("a", "b", "c")),
            EaasGrpcResponseBatcher.chunk(listOf("a", "b", "c"), 0)
        )
    }

    @Test
    fun empty_result_still_emits_one_empty_response_batch() {
        assertEquals(
            listOf(emptyList<String>()),
            EaasGrpcResponseBatcher.chunk(emptyList<String>(), 50)
        )
    }
}
