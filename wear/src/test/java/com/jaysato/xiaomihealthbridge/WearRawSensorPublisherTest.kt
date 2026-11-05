package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearRawSensorPublisherTest {
    @Test
    fun fusedOrientationAndStepEndpointsAreNotAttributedToPhysicalChips() {
        assertEquals(
            "Android fused/virtual sensor; components unverified",
            WearRawSensorPublisher.chipFor(RawVitalsProbe.Kind.ROTATION_VECTOR)
        )
        assertEquals(
            "Android fused/virtual sensor; components unverified",
            WearRawSensorPublisher.chipFor(RawVitalsProbe.Kind.ORIENTATION)
        )
        assertEquals(
            "Step sensor endpoint; vendor unverified",
            WearRawSensorPublisher.chipFor(RawVitalsProbe.Kind.STEP_COUNTER)
        )
        assertEquals(
            "Step sensor endpoint; vendor unverified",
            WearRawSensorPublisher.chipFor(RawVitalsProbe.Kind.STEP_DETECTOR)
        )
        assertEquals(
            "Android heart-rate endpoint; producer per runtime metadata",
            WearRawSensorPublisher.chipFor(RawVitalsProbe.Kind.HEART_RATE)
        )
        assertEquals(
            "LifeQ endpoint; device calibration unverified",
            WearRawSensorPublisher.chipFor(RawVitalsProbe.Kind.LIFE_Q_OTT)
        )
        assertEquals(
            "MAX86178 channel family; watch BOM/config unverified",
            WearRawSensorPublisher.chipFor(RawVitalsProbe.Kind.ECG)
        )
        assertEquals(
            "Android fused/virtual sensor; components unverified",
            WearRawSensorPublisher.chipFor(RawVitalsProbe.Kind.GEOMAGNETIC_ROTATION)
        )
    }

    @Test
    fun stepFeaturesAreListedAsLogicalEndpointsNotBoschImuChips() {
        val boschGroup = WearChipCatalog.chips.first { it.part.startsWith("BOSCH bmi26x") }
        val stepGroup = WearChipCatalog.chips.first { it.part == "計步／運動邏輯端點" }

        assertTrue(boschGroup.features.none { it.title.contains("計步") || it.title.contains("步伐") })
        assertTrue(stepGroup.features.any { it.title.contains("計步") })
        assertTrue(stepGroup.halEvidence.contains("step_counter"))
    }

    @Test
    fun biomedicalEventMetadataIncludesOpticalAndTemperatureChannels() {
        listOf(
            RawVitalsProbe.Kind.PPG,
            RawVitalsProbe.Kind.BIOZ,
            RawVitalsProbe.Kind.SPO2,
            RawVitalsProbe.Kind.MI_SPO2,
            RawVitalsProbe.Kind.BODY_TEMPERATURE,
            RawVitalsProbe.Kind.THERMOMETER
        ).forEach { kind ->
            assertTrue("${kind.wireName} must retain per-event provenance", WearSensorEventMetadataPolicy.supports(kind))
        }
        assertTrue(RawVitalsProbe.Kind.BODY_TEMPERATURE.hasRawValueCount)
        assertTrue(RawVitalsProbe.Kind.THERMOMETER.hasRawValueCount)
        assertTrue(!RawVitalsProbe.Kind.BODY_TEMPERATURE.biomedical)
        assertTrue(!RawVitalsProbe.Kind.THERMOMETER.biomedical)
    }

    @Test
    fun suiteRawValuePayloadIncludesRowsAndRetentionStatusForEverySupportedSensor() {
        listOf(
            RawVitalsProbe.Kind.ECG,
            RawVitalsProbe.Kind.PPG,
            RawVitalsProbe.Kind.BIOZ,
            RawVitalsProbe.Kind.BIOZ_RESPIRATION,
            RawVitalsProbe.Kind.SPO2,
            RawVitalsProbe.Kind.MI_SPO2,
            RawVitalsProbe.Kind.BODY_TEMPERATURE,
            RawVitalsProbe.Kind.THERMOMETER
        ).forEach { kind ->
            assertEquals(
                mapOf(
                    "event_raw_values_rows" to "1.0,2.0;3.0,4.0",
                    "event_raw_values_status" to "complete_for_observed_events"
                ),
                WearSensorEventMetadataPolicy.rawValueFields(
                    kind,
                    listOf("1.0,2.0", "3.0,4.0"),
                    "complete_for_observed_events"
                )
            )
        }
        assertTrue(WearSensorEventMetadataPolicy.rawValueFields(
            RawVitalsProbe.Kind.HEART_RATE, listOf("1.0"), "complete_for_observed_events"
        ).isEmpty())
    }

    @Test
    fun sourceTimestampPresenceFlagDoesNotClaimClockTrust() {
        assertTrue(WearSensorEventMetadataPolicy.sourceTimestampPresent(1L))
        assertTrue(!WearSensorEventMetadataPolicy.sourceTimestampPresent(0L))
        assertTrue(!WearSensorEventMetadataPolicy.sourceTimestampPresent(-1L))
    }

    @Test
    fun sharedSensorClockRequiresValidPositiveMonotonicTimestampsFromBothSources() {
        val valid = longArrayOf(10L, 20L, 30L)
        assertTrue(WearSensorEventMetadataPolicy.shareElapsedRealtimeSensorClock(valid, intArrayOf(1, 1, 1), valid))
        assertTrue(!WearSensorEventMetadataPolicy.shareElapsedRealtimeSensorClock(valid, intArrayOf(1, 0, 1), valid))
        assertTrue(!WearSensorEventMetadataPolicy.shareElapsedRealtimeSensorClock(valid, intArrayOf(1, 1, 1), longArrayOf(10L, 9L)))
        assertTrue(!WearSensorEventMetadataPolicy.shareElapsedRealtimeSensorClock(longArrayOf(10L, 10L), intArrayOf(1, 1), valid))
    }
}
