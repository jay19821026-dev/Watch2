package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BoundedSnapshotFileTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun readsExactLimitWithoutChangingEvidence() {
        val file = temporaryFolder.newFile()
        val bytes = byteArrayOf(1, 2, 3, 4)
        file.writeBytes(bytes)
        assertArrayEquals(bytes, BoundedSnapshotFile.read(file, 4))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun rejectsOversizedFileWithoutDeletingIt() {
        val file = temporaryFolder.newFile()
        file.writeBytes(ByteArray(5))
        assertTrue(runCatching { BoundedSnapshotFile.read(file, 4) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(file.exists())
        assertTrue(file.length() == 5L)
    }

    @Test fun rejectsEmptyMissingAndInvalidLimit() {
        val file = temporaryFolder.newFile()
        assertTrue(runCatching { BoundedSnapshotFile.read(file, 4) }.isFailure)
        file.delete()
        assertTrue(runCatching { BoundedSnapshotFile.read(file, 4) }.isFailure)
        assertTrue(runCatching { BoundedSnapshotFile.read(file, 0) }.isFailure)
    }
}
