package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.File
import java.nio.file.Files

class SensorSuiteDumpRetentionTest {
    @Test
    fun expiryCleanupRemovesOnlyOldLegacyAndHiddenPartialFiles() {
        val directory = Files.createTempDirectory("suite-partial-cleanup-test").toFile()
        try {
            val now = System.currentTimeMillis()
            val expired = now - SensorSuiteDumpRetention.MAX_AGE_MILLIS
            val legacy = File(directory, "sensor-suite-latest.json.tmp").apply { writeText("partial"); assertTrue(setLastModified(expired)) }
            val hidden = File(directory, ".sensor-suite-latest.json.old.tmp").apply { writeText("partial"); assertTrue(setLastModified(expired)) }
            val recent = File(directory, ".sensor-suite-latest.json.recent.tmp").apply { writeText("partial"); assertTrue(setLastModified(now)) }
            val unrelated = File(directory, ".sensor-suite-latest.json.keep").apply { writeText("keep"); assertTrue(setLastModified(expired)) }
            val future = File(directory, ".sensor-suite-latest.json.future.tmp").apply { writeText("partial"); assertTrue(setLastModified(now + 60_000L)) }
            assertEquals(0, SensorSuiteDumpRetention.cleanupExpiredPartials(directory, now))
            assertFalse(legacy.exists())
            assertFalse(hidden.exists())
            assertTrue(recent.exists())
            assertTrue(unrelated.exists())
            assertTrue(future.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedPartialDeletionIsReportedAndAttemptsAreBounded() {
        val directory = Files.createTempDirectory("suite-partial-failure-test").toFile()
        try {
            val now = System.currentTimeMillis()
            repeat(65) { index ->
                File(directory, ".sensor-suite-latest.json.$index.tmp").apply {
                    writeText("partial")
                    assertTrue(setLastModified(now - SensorSuiteDumpRetention.MAX_AGE_MILLIS))
                }
            }
            var attempts = 0
            assertEquals(64, SensorSuiteDumpRetention.cleanupExpiredPartials(directory, now) { attempts++; false })
            assertEquals(64, attempts)
            assertEquals(65, directory.listFiles()!!.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun onlyRecentNonFutureSummaryCanBeAttachedToExplicitExport() {
        val now = 1_800_000_000_000L
        assertTrue(SensorSuiteDumpRetention.isAvailable(now - SensorSuiteDumpRetention.MAX_AGE_MILLIS, now))
        assertFalse(SensorSuiteDumpRetention.isAvailable(now - SensorSuiteDumpRetention.MAX_AGE_MILLIS - 1L, now))
        assertFalse(SensorSuiteDumpRetention.isAvailable(now + 1L, now))
        assertFalse(SensorSuiteDumpRetention.isAvailable(0L, now))
    }
}
