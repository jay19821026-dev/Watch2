package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorRegistrationGuardTest {
    @Test
    fun constructionFailuresLeaveOtherChannelsRunnable() {
        val failures = mutableListOf<RuntimeException>()
        val channels = listOf("permission", "runtime", "working").mapNotNull { channel ->
            SensorRegistrationGuard.create(
                factory = {
                    when (channel) {
                        "permission" -> throw SecurityException("denied")
                        "runtime" -> throw IllegalStateException("unavailable")
                        else -> channel
                    }
                },
                onFailure = { failures.add(it) }
            )
        }
        assertEquals(listOf("working"), channels)
        assertEquals(2, failures.size)
        assertTrue(failures.first() is SecurityException)
    }

    @Test
    fun terminalRegistrationFailurePreservesSpecificCauseAndUsesFallbackOnlyWhenUnset() {
        assertEquals("permission_denied", SensorRegistrationGuard.terminalFailureReason("permission_denied"))
        assertEquals("registration_exception", SensorRegistrationGuard.terminalFailureReason("registration_exception"))
        assertEquals("registration_failed", SensorRegistrationGuard.terminalFailureReason("unknown"))
        assertEquals("registration_failed", SensorRegistrationGuard.terminalFailureReason("running"))
    }

    @Test
    fun primaryRuntimeFailureTriesFallbackSensor() {
        var fallbackAttempts = 0
        var primaryFailures = 0

        val started = SensorRegistrationGuard.runPrimaryThenFallback(
            primary = { throw IllegalArgumentException("primary unavailable") },
            fallback = { fallbackAttempts++; true },
            onPrimaryRuntimeFailure = { primaryFailures++ }
        )

        assertTrue(started)
        assertEquals(1, fallbackAttempts)
        assertEquals(1, primaryFailures)
    }

    @Test
    fun preservesPrimaryRuntimeFailureWhenFallbackAlsoFails() {
        var failureReason: String? = null
        val started = SensorRegistrationGuard.run(
            register = {
                SensorRegistrationGuard.runPrimaryThenFallback(
                    primary = { throw IllegalArgumentException("primary unavailable") },
                    fallback = { false },
                    onPrimaryRuntimeFailure = { }
                )
            },
            onPermissionDenied = { failureReason = "permission_denied"; false },
            onRuntimeFailure = { failureReason = "registration_exception"; false }
        )

        assertFalse(started)
        assertEquals("registration_exception", failureReason)
    }

    @Test
    fun failedCleanupIsReportedAndDoesNotEscape() {
        var reported = false
        val succeeded = SensorRegistrationGuard.cleanupSucceeded(
            action = { throw IllegalStateException("unregister failed") },
            onFailure = { reported = it.message == "unregister failed" }
        )

        assertFalse(succeeded)
        assertTrue(reported)
    }

    @Test
    fun fallbackRegistrationIsNotAttemptedWhenUnregisterFails() {
        var fallbackAttempts = 0
        var cleanupReported = false

        val started = SensorRegistrationGuard.runAfterCleanup(
            cleanup = { throw IllegalStateException("unregister failed") },
            register = { fallbackAttempts++; true },
            onCleanupFailure = { cleanupReported = true },
            onPermissionDenied = { false },
            onRuntimeFailure = { false }
        )

        assertFalse(started)
        assertTrue(cleanupReported)
        assertEquals(0, fallbackAttempts)
    }

    @Test
    fun rejectedPrimaryTriesFallbackSensor() {
        var fallbackAttempts = 0

        val started = SensorRegistrationGuard.runPrimaryThenFallback(
            primary = { false },
            fallback = { fallbackAttempts++; true },
            onPrimaryRuntimeFailure = { throw AssertionError("no exception expected") }
        )

        assertTrue(started)
        assertEquals(1, fallbackAttempts)
    }

    @Test(expected = SecurityException::class)
    fun primaryPermissionFailureDoesNotTryFallbackSensor() {
        SensorRegistrationGuard.runPrimaryThenFallback(
            primary = { throw SecurityException("permission required") },
            fallback = { throw AssertionError("fallback must not bypass permission") },
            onPrimaryRuntimeFailure = { throw AssertionError("permission failure is not a runtime fallback") }
        )
    }

    @Test
    fun runtimeFailureBecomesTerminalRegistrationFailure() {
        var failureReason: String? = null
        val started = SensorRegistrationGuard.run(
            register = { throw IllegalArgumentException("unsupported sensor delay") },
            onPermissionDenied = { failureReason = "permission_denied"; false },
            onRuntimeFailure = { failureReason = "registration_exception"; false }
        )

        assertFalse(started)
        assertEquals("registration_exception", failureReason)
    }

    @Test
    fun securityFailureKeepsDistinctPermissionState() {
        var failureReason: String? = null
        val started = SensorRegistrationGuard.run(
            register = { throw SecurityException("permission revoked") },
            onPermissionDenied = { failureReason = "permission_denied"; false },
            onRuntimeFailure = { failureReason = "registration_exception"; false }
        )

        assertFalse(started)
        assertEquals("permission_denied", failureReason)
    }

    @Test
    fun successfulRegistrationDoesNotInvokeFailureCallbacks() {
        val started = SensorRegistrationGuard.run(
            register = { true },
            onPermissionDenied = { false },
            onRuntimeFailure = { false }
        )

        assertTrue(started)
    }

    @Test
    fun cleanupContinuesAfterAnActionThrows() {
        var completedActions = 0
        var reportedFailures = 0
        SensorRegistrationGuard.cleanup(
            actions = listOf(
                { throw IllegalStateException("unregister failed") },
                { completedActions++ }
            ),
            onFailure = { reportedFailures++ }
        )

        assertEquals(1, completedActions)
        assertEquals(1, reportedFailures)
    }
}
