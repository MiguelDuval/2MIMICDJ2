package com.miguenduval.mimicdj2.server

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class StartupFailureGuardTest {

    @Test
    fun startup_guard_contains_errors_instead_of_leaking_them() {
        var seen: Throwable? = null

        val result = StartupFailureGuard.run(
            onFailure = { seen = it }
        ) {
            throw AssertionError("synthetic startup linkage failure")
        }

        assertNull(result)
        assertSame(AssertionError::class.java, seen?.javaClass)
    }

    @Test
    fun startup_guard_returns_success_value_without_invoking_failure_handler() {
        var failures = 0

        val result = StartupFailureGuard.run(
            onFailure = { failures++ }
        ) {
            "started"
        }

        org.junit.Assert.assertEquals("started", result)
        org.junit.Assert.assertEquals(0, failures)
    }
}
