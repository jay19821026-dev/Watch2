package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorEvidenceRepairEngineTest {
    @Test fun undecodedHalWithPublishTimeGetsResearchPlan() {
        val plan = SensorEvidenceRepairEngine.plan(
            HalEvidenceInput(217, 0, 2170, 0, 1000L, false, true, false, true)
        )
        assertTrue(EvidenceIssue.HAL_UNDECODED in plan.issues)
        assertEquals(TimestampSource.ESTIMATED, plan.timestampSource)
        assertTrue(plan.canProduceResearchOutput)
        assertTrue(plan.actions.any { it.contains("索引時間軸") })
    }

    @Test fun lockedEmptySensorDoesNotClaimOutput() {
        val plan = SensorEvidenceRepairEngine.plan(
            HalEvidenceInput(0, 0, 0, 0, null, false, false, true, false)
        )
        assertTrue(EvidenceIssue.OEM_OR_PERMISSION_LOCKED in plan.issues)
        assertEquals(false, plan.canProduceResearchOutput)
        assertEquals("NO_RAW_OR_DECODED_EVIDENCE", plan.reason)
    }
    @Test fun classifiedCodesFeedTheSameRepairPlan() {
        val plan = SensorEvidenceRepairEngine.planFromCodes(
            listOf("research_signal_unscaled", "timestamp_missing"),
            hasRawEvidence = true,
            hasDecodedEvidence = false,
            hasPublishTime = true
        )
        assertTrue(EvidenceIssue.HAL_UNDECODED in plan.issues)
        assertTrue(EvidenceIssue.PUBLISH_TIME_ONLY in plan.issues)
        assertTrue(plan.actions.any { it.contains("ResearchAnalysisLoop") })
    }
}
