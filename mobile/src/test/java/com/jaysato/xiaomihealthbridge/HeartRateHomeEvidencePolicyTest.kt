package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateHomeEvidencePolicyTest {
    @Test
    fun trustedHealthServicesTimeIsShownAsMeasurementTimeWithSource() {
        val label = HeartRateHomeEvidencePolicy.describe(
            "health_services", 1_791_446_400_000L,
            HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        )

        assertTrue(label.contains("Health Services"))
        assertTrue(label.contains("量測時間 2026-10-08T"))
        assertTrue(label.contains("timestamp_source=health_services_datapoint_epoch"))
    }

    @Test
    fun untrustedRawSensorTimeIsNotLabeledMeasurementTime() {
        val label = HeartRateHomeEvidencePolicy.describe(
            "raw_sensor", 1_791_446_400_000L, "watch_snapshot_published_at"
        )

        assertTrue(label.contains("手錶原始心率通道"))
        assertTrue(label.contains("時間欄位未確認"))
        assertTrue(!label.contains("量測時間"))
    }
}
