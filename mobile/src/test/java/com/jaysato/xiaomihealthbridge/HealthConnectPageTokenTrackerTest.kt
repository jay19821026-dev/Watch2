package com.jaysato.xiaomihealthbridge

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class HealthConnectPageTokenTrackerTest {
    @Test
    fun oversizedPageIsRejectedBeforeIterationEvenWhenSumWouldOverflow() {
        val oversized = object : AbstractList<Int>() {
            override val size = Int.MAX_VALUE
            override fun get(index: Int): Int = error("Oversized page must not be iterated")
        }
        assertThrows(HealthConnectReadLimitException::class.java) {
            runBlocking {
                collectHealthConnectRecordPages(maxPages = 3, maxRecords = 2) { token ->
                    if (token == null) HealthConnectRecordPage(listOf(1), "next")
                    else HealthConnectRecordPage(oversized, null)
                }
            }
        }
    }

    @Test
    fun detectsCyclesAndAllowsTerminalTokens() {
        val tracker = HealthConnectPageTokenTracker()
        assertFalse(tracker.isRepeated("page-a"))
        assertFalse(tracker.isRepeated("page-b"))
        assertTrue(tracker.isRepeated("page-a"))
        assertFalse(tracker.isRepeated(null))
        assertFalse(tracker.isRepeated(""))
    }

    @Test
    fun uniqueEmptyPagesCannotBypassThePageLimit() = runBlocking {
        var calls = 0
        assertThrows(HealthConnectReadLimitException::class.java) {
            runBlocking {
                collectHealthConnectRecordPages(maxPages = 2, maxRecords = 10) { token ->
                    calls++
                    HealthConnectRecordPage(emptyList<Int>(), if (token == null) "p1" else "p$calls")
                }
            }
        }
        assertEquals(2, calls)
    }

    @Test
    fun recordLimitRejectsWholeHealthConnectResult() {
        assertThrows(HealthConnectReadLimitException::class.java) {
            runBlocking {
                collectHealthConnectRecordPages(maxPages = 4, maxRecords = 2) { token ->
                    if (token == null) HealthConnectRecordPage(listOf(1, 2), "p1")
                    else HealthConnectRecordPage(listOf(3), null)
                }
            }
        }
    }

    @Test
    fun foldedPagesAggregateWithoutRetainingEarlierPagesAndRejectDuplicateIds() {
        runBlocking {
            val seen = mutableListOf<Int>()
            foldHealthConnectRecordPages(
                maxPages = 3,
                maxRecords = 4,
                fetchPage = { token ->
                    if (token == null) HealthConnectRecordPage(listOf(1, 2), "p1")
                    else HealthConnectRecordPage(listOf(3), null)
                },
                recordId = { "id-$it" },
                foldPage = { seen += it }
            )
            assertEquals(listOf(1, 2, 3), seen)
        }
        assertThrows(InvalidHealthConnectRecordIdentityException::class.java) {
            runBlocking {
                foldHealthConnectRecordPages(
                    maxPages = 3,
                    maxRecords = 4,
                    fetchPage = { token ->
                        if (token == null) HealthConnectRecordPage(listOf(1), "p1")
                        else HealthConnectRecordPage(listOf(1), null)
                    },
                    recordId = { "same-id" },
                    foldPage = {}
                )
            }
        }
    }

    @Test
    fun cancellationBetweenHealthConnectPagesStopsFetching() = runBlocking {
        var calls = 0
        val reader = launch {
            collectHealthConnectRecordPages(maxPages = 4, maxRecords = 10) { token ->
                calls++
                if (token == null) HealthConnectRecordPage(listOf(1), "p1") else awaitCancellation()
            }
        }
        yield()
        reader.cancelAndJoin()
        assertEquals(2, calls)
        assertTrue(reader.isCancelled)
    }

    @Test
    fun readLimitFailureHasDistinctUserFacingReason() {
        assertEquals(
            "心率：Health Connect 分頁／紀錄數超過安全上限，拒絕部分結果",
            HealthConnectReadFailurePolicy.describe("心率", HealthConnectReadLimitException("limit"))
        )
    }
}
