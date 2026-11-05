package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchSignalCompletionEngineTest {
    private val provenance = SensorProvenance(
        "session", "ppg", "raw capture", "capture-v1", null,
        0.8, "RESEARCH", TimestampSource.UNKNOWN, null, 1L
    )

    @Test fun completesAnalysisWithoutChangingRawAndKeepsProvenance() {
        val raw = listOf(1.0, null, 3.0)
        val report = ResearchSignalCompletionEngine.completePpg(
            raw, provenance, true, orderingVerified = true, channelMappingVerified = false
        )

        assertEquals("RESEARCH_COMPLETE_INDEX_DOMAIN", report.status)
        assertNotNull(report.signal)
        assertNull(raw[1])
        assertEquals(DataOrigin.IMPUTED, report.signal!!.points[1].origin)
        assertTrue(report.signal.points[1].provenance.provenance.contains("channel_mapping=unverified"))
        assertEquals(ResearchSignalCompletionEngine.algorithmVersion, report.method)
    }

    @Test fun reportsIncompleteWhenThereIsNoObservedSignal() {
        val report = ResearchSignalCompletionEngine.completePpg(
            listOf(null, Double.NaN), provenance, true, orderingVerified = true, channelMappingVerified = true
        )

        assertEquals("RESEARCH_INCOMPLETE", report.status)
        assertNull(report.signal)
        assertTrue(report.reason.contains("NO_OBSERVATIONS"))
    }
}
