package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EngineeringExportRetentionTest {
    @Test
    fun atomicExportReplacesOnlyWithCompleteContent() {
        val directory = Files.createTempDirectory("healthbridge-export-test").toFile()
        try {
            val target = File(directory, "health.json").apply { writeText("old-complete") }

            EngineeringExportRetention.writeAtomically(target, "new-complete")

            assertEquals("new-complete", target.readText())
            assertEquals(listOf("health.json"), directory.list()?.toList())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedAtomicExportDeletesPartialTempAndPreservesPriorCompleteExport() {
        val directory = Files.createTempDirectory("healthbridge-export-failure-test").toFile()
        try {
            val target = File(directory, "health.json").apply { writeText("old-complete") }

            try {
                EngineeringExportRetention.writeAtomically(target, "new-complete") { temporary, _ ->
                    temporary.writeText("partial-sensitive-content")
                    error("simulated disk/write failure")
                }
                throw AssertionError("Expected the simulated write failure")
            } catch (expected: IllegalStateException) {
                assertEquals("simulated disk/write failure", expected.message)
            }

            assertEquals("old-complete", target.readText())
            assertEquals(listOf("health.json"), directory.list()?.toList())
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun suiteExportMoveFailureRemovesTemporaryWithoutDeletingUnrelatedFiles() {
        val directory = Files.createTempDirectory("suite-export-move-failure-test").toFile()
        try {
            val target = File(directory, SensorSuiteDump.FILE_NAME).apply { mkdir() }
            val existing = File(target, "keep.json").apply { writeText("keep") }
            val unrelated = File(directory, "unrelated.json.tmp").apply { writeText("keep") }
            try {
                EngineeringExportRetention.writeAtomically(target, "new-complete", SensorSuiteDump.MAX_SUMMARY_BYTES.toLong())
                throw AssertionError("Expected move failure")
            } catch (expected: java.io.IOException) {
                assertTrue(target.isDirectory)
            }
            assertEquals("keep", existing.readText())
            assertEquals("keep", unrelated.readText())
            assertFalse(directory.listFiles().orEmpty().any { it.name.startsWith(".${SensorSuiteDump.FILE_NAME}.") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun restartCleanupRemovesOnlyPartialExportTemps() {
        val directory = Files.createTempDirectory("healthbridge-export-cleanup-test").toFile()
        try {
            File(directory, ".healthbridge-debug.json.abcd.tmp").writeText("partial")
            File(directory, "healthbridge-debug.json").writeText("complete")
            File(directory, ".unrelated.tmp").writeText("keep")

            val deleted = EngineeringExportRetention.deletePartialFiles(directory, ".healthbridge-debug.json.")

            assertEquals(1, deleted)
            assertEquals(listOf(".unrelated.tmp", "healthbridge-debug.json"), directory.list()?.sorted())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun exportsExpireAtAgeBoundaryAndRejectUnknownOrFutureTimestamps() {
        val now = 9_000_000L
        val maxAge = EngineeringExportRetention.RAW_EXPORT_MAX_AGE_MILLIS

        assertFalse(EngineeringExportRetention.isExpired(now - 1L, now, maxAge))
        assertTrue(EngineeringExportRetention.isExpired(now - maxAge, now, maxAge))
        assertTrue(EngineeringExportRetention.isExpired(0L, now, maxAge))
        assertTrue(EngineeringExportRetention.isExpired(now + 1L, now, maxAge))
        assertTrue(EngineeringExportRetention.remainingMillis(now - 1L, now, maxAge) == maxAge - 1L)
        assertTrue(EngineeringExportRetention.remainingMillis(now - maxAge, now, maxAge) == null)
    }

    @Test
    fun atomicExportChecksFinalUtf8BytesBeforeReplacingExistingFile() {
        val directory = Files.createTempDirectory("healthbridge-export-size-test").toFile()
        try {
            val target = File(directory, "health.json").apply { writeText("old-complete") }

            EngineeringExportRetention.writeAtomically(target, "健康", maxBytes = 6L)
            assertEquals("健康", target.readText())

            try {
                EngineeringExportRetention.writeAtomically(target, "過大", maxBytes = 5L)
                throw AssertionError("Expected the oversized UTF-8 export to be rejected")
            } catch (expected: java.io.IOException) {
                assertEquals("Export exceeds maximum byte size", expected.message)
            }

            assertEquals("健康", target.readText())
            assertEquals(listOf("health.json"), directory.list()?.toList())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun boundedUtf8LengthHandlesUnicodeAndOversizedContent() {
        assertEquals(6L, EngineeringExportRetention.utf8ByteCountWithinLimit("健康", 6L))
        assertEquals(4L, EngineeringExportRetention.utf8ByteCountWithinLimit("😀", 4L))
        assertEquals(1L, EngineeringExportRetention.utf8ByteCountWithinLimit("\uD800", 1L))
        assertEquals(null, EngineeringExportRetention.utf8ByteCountWithinLimit("健康", 5L))
        assertEquals(null, EngineeringExportRetention.utf8ByteCountWithinLimit("x".repeat(32_768), 24_576L))
    }
}
