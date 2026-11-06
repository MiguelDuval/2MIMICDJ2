package com.miguenduval.mimicdj2.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtobufWireTagSummaryTest {
    @Test
    fun summarizes_field_numbers_and_wire_types_without_values() {
        val payload = byteArrayOf(0x0A, 0x01, 'x'.code.toByte(), 0x18, 0x07)
        val summary = ProtobufWireTagSummary.summarize(payload)
        assertEquals("[1/wire2, 3/wire0]", summary)
        assertFalse(summary.contains("x"))
    }

    @Test
    fun empty_message_has_no_fields() {
        assertEquals("[]", ProtobufWireTagSummary.summarize(byteArrayOf()))
    }

    @Test
    fun truncated_message_is_marked_invalid() {
        assertTrue(ProtobufWireTagSummary.summarize(byteArrayOf(0x0A, 0x03, 0x41)).startsWith("INVALID@"))
    }
}
