package com.miguenduval.mimicdj2.server

import kotlinx.coroutines.CancellationException

/**
 * Contains startup failures, including JVM/Linkage Errors that are not Exceptions,
 * so the Android process can stay alive long enough to expose diagnostics.
 */
internal object StartupFailureGuard {
    fun <T> run(
        onFailure: (Throwable) -> Unit,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            onFailure(t)
            null
        }
    }
}
