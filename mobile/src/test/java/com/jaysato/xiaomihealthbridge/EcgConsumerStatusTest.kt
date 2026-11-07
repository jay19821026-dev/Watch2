package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgConsumerStatusTest {
    @Test fun idleCardDoesNotClaimMeasurementIsRunning() {
        val result = EcgConsumerStatus.classify(false, null, false)
        assertEquals(EcgConsumerStatus.State.IDLE, result.state)
        assertEquals("尚未量測", result.label)
    }
    @Test fun savedRawEvidenceIsNotShownAsAnActiveMeasurement() {
        assertEquals(
            EcgConsumerStatus.State.UNDETERMINED,
            EcgConsumerStatus.classify(false, "規則", true).state
        )
    }

    @Test fun activeMeasurementIsExplicit() {
        assertEquals(EcgConsumerStatus.State.MEASURING,
            EcgConsumerStatus.classify(false, null, false, measurementInProgress = true).state)
    }

    @Test fun eligibleRegularRhythmIsNormal() {
        assertEquals(
            EcgConsumerStatus.State.NORMAL,
            EcgConsumerStatus.classify(true, "RR 間距規則", true).state
        )
    }

    @Test fun eligibleIrregularRhythmIsAbnormal() {
        assertEquals(
            EcgConsumerStatus.State.ABNORMAL,
            EcgConsumerStatus.classify(true, "研究推斷心律不整", true).state
        )
    }

    @Test fun eligibleHistoricalRhythmRemainsVisibleAsHistorical() {
        val result = EcgConsumerStatus.classify(true, "RR 間距規則", true, historical = true)

        assertEquals(EcgConsumerStatus.State.NORMAL, result.state)
        assertEquals("歷史正常", result.label)
        assertTrue(result.detail.contains("不代表目前心律"))
    }

    @Test fun eligibleUnknownLabelDoesNotBecomeNormal() {
        assertEquals(
            EcgConsumerStatus.State.UNDETERMINED,
            EcgConsumerStatus.classify(true, "", true).state
        )
    }

    @Test fun completedCaptureWithEvidenceShowsUndeterminedInsteadOfMeasuring() {
        val result = EcgConsumerStatus.classify(
            eligible = false,
            rhythmTitle = null,
            hasEvidence = true,
            measurementComplete = true
        )

        assertEquals(EcgConsumerStatus.State.UNDETERMINED, result.state)
        assertEquals("無法判定", result.label)
        assertTrue(result.detail.contains("已完成並保存"))
    }

    @Test fun completedCaptureWithoutEvidenceRequestsRetest() {
        val result = EcgConsumerStatus.classify(
            eligible = false,
            rhythmTitle = null,
            hasEvidence = false,
            measurementComplete = true
        )

        assertEquals(EcgConsumerStatus.State.UNDETERMINED, result.state)
        assertTrue(result.detail.contains("沒有可用資料"))
    }
}
