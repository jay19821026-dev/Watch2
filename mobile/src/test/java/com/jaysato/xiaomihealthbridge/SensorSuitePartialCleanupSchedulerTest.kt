package com.jaysato.xiaomihealthbridge

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorSuitePartialCleanupSchedulerTest {
    @Test
    fun startupAndReadRequestsCoalesceAndCleanOnlyOnExecutor() {
        val directory = Files.createTempDirectory("suite-startup-read-test").toFile()
        try {
            val now = System.currentTimeMillis()
            val legacy = File(directory, "sensor-suite-latest.json.tmp").apply {
                writeText("partial")
                assertTrue(setLastModified(now - SensorSuiteDumpRetention.MAX_AGE_MILLIS))
            }
            val queued = mutableListOf<Runnable>()
            val scheduler = SensorSuitePartialCleanupScheduler({ queued.add(it) }) {
                assertEquals(0, SensorSuiteDumpRetention.cleanupExpiredPartials(it, now))
            }
            scheduler.request(directory)
            scheduler.request(directory)
            assertEquals(1, queued.size)
            assertTrue(legacy.exists())
            queued.removeAt(0).run()
            assertFalse(legacy.exists())
            scheduler.request(directory)
            assertEquals(1, queued.size)
            queued.removeAt(0).run()
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedCleanupAllowsLaterReadToRetry() {
        val queued = mutableListOf<Runnable>()
        val scheduler = SensorSuitePartialCleanupScheduler({ queued.add(it) }) { error("cleanup failure") }
        scheduler.request(File("unused"))
        try {
            queued.removeAt(0).run()
            throw AssertionError("Expected failure")
        } catch (expected: IllegalStateException) {
            assertEquals("cleanup failure", expected.message)
        }
        scheduler.request(File("unused"))
        assertEquals(1, queued.size)
    }
}
