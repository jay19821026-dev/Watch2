package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserFacingMeasurementOutcomeTest {
    @Test
    fun rawEcgProducesTruthfulConclusionAndAction() {
        val result = UserFacingMeasurementOutcome.from(
            SensorDisplayState.CALIBRATION_MISSING,
            metricKind = "ecg",
            sourceStatus = "research_signal_unscaled"
        )

        assertEquals(UserFacingMeasurementOutcome.Kind.RAW_CAPTURED, result.kind)
        assertEquals("已收到心電訊號", result.label)
        assertEquals("尚無可解讀的心律結果", result.conclusion)
        assertFalse(result.conclusion.contains("正常"))
        assertFalse(result.conclusion.contains("異常"))
    }

    @Test
    fun capturedSensorKindsExplainWhatWasReceivedWithoutClaimingAReading() {
        val expected = mapOf(
            "spo2_raw" to ("已收到血氧訊號" to "尚無可用的血氧百分比"),
            "bioz" to ("已收到阻抗原始訊號" to "尚無可用的阻抗量測結果"),
            "body_temperature_raw" to ("已收到溫度訊號" to "尚無可用的體溫讀值")
        )
        expected.forEach { (metric, labels) ->
            val result = UserFacingMeasurementOutcome.from(
                SensorDisplayState.RAW_ONLY, metric, "research_signal_unscaled"
            )
            assertEquals(labels.first, result.label)
            assertEquals(labels.second, result.conclusion)
        }
    }

    @Test
    fun syncFailureTakesPrecedenceOverLocalDataState() {
        val result = UserFacingMeasurementOutcome.from(
            SensorDisplayState.AVAILABLE,
            metricKind = "ppg",
            sourceStatus = "has_data",
            hasSyncFailure = true
        )

        assertEquals(UserFacingMeasurementOutcome.Kind.SYNC_FAILED, result.kind)
        assertEquals("尚未同步到手機", result.label)
    }

    @Test
    fun historicalStatusTextDoesNotOverrideAUsablePersistedResult() {
        val result = UserFacingMeasurementOutcome.from(
            SensorDisplayState.AVAILABLE,
            metricKind = "ppg",
            sourceStatus = "sync_error",
            hasSyncFailure = false
        )

        assertEquals(UserFacingMeasurementOutcome.Kind.USABLE_RESULT, result.kind)
    }

    @Test
    fun researchAndDerivedStatesNeverBecomeMeasuredResults() {
        val research = UserFacingMeasurementOutcome.from(
            SensorDisplayState.SIGNAL_VALID_RESEARCH,
            "ppg",
            "valid_signal"
        )
        val derived = UserFacingMeasurementOutcome.from(
            SensorDisplayState.DERIVED_ESTIMATE,
            "heart_rate_raw",
            "derived_estimate"
        )

        assertEquals(UserFacingMeasurementOutcome.Kind.RAW_CAPTURED, research.kind)
        assertEquals(UserFacingMeasurementOutcome.Kind.RAW_CAPTURED, derived.kind)
        assertEquals("已收到脈搏訊號", research.label)
        assertEquals("尚無可用的脈率或心率讀值", research.conclusion)
        assertEquals("這是根據其他紀錄估算的結果", derived.conclusion)
    }

    @Test
    fun oemOnlyStateIsUnsupportedRatherThanUserFixable() {
        val result = UserFacingMeasurementOutcome.from(
            SensorDisplayState.CONDITION_NOT_MET,
            "lifeq_ott",
            "needs_oem_mode"
        )

        assertEquals(UserFacingMeasurementOutcome.Kind.UNSUPPORTED, result.kind)
        assertEquals("功能不支援", result.label)
    }

    @Test
    fun qualityRejectedHasRetryableTerminalOutcome() {
        val result = UserFacingMeasurementOutcome.from(
            SensorDisplayState.QUALITY_REJECTED,
            "ppg",
            "invalid_optical"
        )

        assertEquals(UserFacingMeasurementOutcome.Kind.QUALITY_INSUFFICIENT, result.kind)
        assertEquals("訊號不清楚", result.label)
    }

    @Test
    fun availableStateIsTheOnlyDirectMeasuredResultInThisPolicy() {
        val result = UserFacingMeasurementOutcome.from(
            SensorDisplayState.AVAILABLE,
            "whs_heart_rate",
            "has_data"
        )

        assertEquals(UserFacingMeasurementOutcome.Kind.USABLE_RESULT, result.kind)
        assertEquals("可用結果", result.label)
    }

    @Test
    fun staleUnknownAndFutureTimesNeverBecomeUsableResults() {
        listOf(
            SensorDisplayState.STALE,
            SensorDisplayState.TIMESTAMP_UNKNOWN,
            SensorDisplayState.FUTURE_TIMESTAMP
        ).forEach { state ->
            val outcome = UserFacingMeasurementOutcome.from(state, "ppg", "has_data")
            assertEquals(
                UserFacingMeasurementOutcome.Kind.STALE,
                outcome.kind
            )
            assertEquals(
                if (state == SensorDisplayState.STALE) "這是較早的紀錄" else "時間無法確認",
                outcome.label
            )
        }
    }

    @Test
    fun chargingAndOffWristGiveSpecificCorrectiveActions() {
        val charging = UserFacingMeasurementOutcome.from(
            SensorDisplayState.CONDITION_NOT_MET,
            "ppg",
            "skipped_charging"
        )
        val offWrist = UserFacingMeasurementOutcome.from(
            SensorDisplayState.CONDITION_NOT_MET,
            "ppg",
            "off_wrist"
        )

        assertEquals("充電中未執行貼腕感測", charging.conclusion)
        assertEquals("尚未確認手錶貼合皮膚", offWrist.conclusion)
    }

    @Test
    fun missingDataNeverPretendsToBeZeroOrNormal() {
        val result = UserFacingMeasurementOutcome.from(
            SensorDisplayState.NOT_MEASURED,
            "spo2_raw",
            "not_measured"
        )

        assertEquals(UserFacingMeasurementOutcome.Kind.NO_DATA, result.kind)
        assertEquals("尚無資料", result.label)
        assertFalse(result.conclusion.contains("0"))
        assertFalse(result.conclusion.contains("正常"))
    }
}
