package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.jaysato.xiaomihealthbridge.engine.RawSensorCountContract

/**
 * Shared wire-contract vectors exercised through the production count encoder,
 * decoder, and mobile count policies.
 * This deliberately avoids constructing a real Google Play services DataItem.
 */
class WearCountContractFixtureTest {
    @Test
    fun allSixPrimarySensorKindsKeepRawCountsSeparateFromEventsAndPreview() {
        listOf("ppg", "ecg", "bioz", "spo2_raw", "body_temperature_raw", "heart_rate_raw")
            .forEach { kind ->
                val received = consume(
                    kind,
                    RawSensorCountContract.encode(
                        samples = 64,
                        rawValueCount = 64,
                        previewPointCount = 32,
                        eventCount = 8
                    )
                )

                assertTrue("$kind must use the biomedical raw count", RawSensorPayloadPolicy.isBiomedicalKind(kind))
                assertEquals("$kind raw count", 64, received.rawValueCount)
                assertEquals("$kind display count", 64, received.displayCount)
                assertEquals("$kind event count", 8, received.eventCount)
                assertEquals("$kind preview count", 32, received.previewPointCount)
                assertTrue("$kind counts as raw evidence", received.hasStatisticSampleCount)
            }
    }

    @Test
    fun rawEventAndPreviewCountsStayIndependentAcrossTheContract() {
        val producerFields = RawSensorCountContract.encode(
            samples = 3_976,
            rawValueCount = 3_976,
            previewPointCount = 512,
            eventCount = 497
        )

        val received = consume("ppg", producerFields)

        assertEquals(2, received.contractVersion)
        assertEquals(3_976, received.rawValueCount)
        assertEquals(3_976, received.displayCount)
        assertEquals(512, received.previewPointCount)
        assertEquals(497, received.eventCount)
        assertTrue(received.eventCountKnown)
        assertTrue(received.hasStatisticSampleCount)
    }

    @Test
    fun temperatureRawCountSurvivesVersionedCrossDeviceContract() {
        listOf("body_temperature_raw", "thermometer").forEach { kind ->
            val received = consume(
                kind,
                RawSensorCountContract.encode(samples = 14, rawValueCount = 14, previewPointCount = 8, eventCount = 14)
            )
            assertEquals(14, received.rawValueCount)
            assertEquals(14, received.displayCount)
            assertEquals(8, received.previewPointCount)
            assertEquals(14, received.eventCount)
            assertTrue(received.hasStatisticSampleCount)
        }
    }

    @Test
    fun absentRawCountRemainsUnknownWhileExplicitZeroRemainsZero() {
        val absentRaw = consume(
            "ppg",
            RawSensorCountContract.encode(samples = 0, rawValueCount = null, previewPointCount = 4, eventCount = 4)
        )
        val explicitZero = consume(
            "ppg",
            RawSensorCountContract.encode(samples = 0, rawValueCount = 0, previewPointCount = 4, eventCount = 4)
        )

        assertNull(absentRaw.rawValueCount)
        assertEquals(0, absentRaw.displayCount)
        assertEquals(0, explicitZero.rawValueCount)
        assertEquals(0, explicitZero.displayCount)
        assertEquals(4, absentRaw.previewPointCount)
        assertEquals(4, absentRaw.eventCount)
        assertFalse(absentRaw.hasStatisticSampleCount)
        assertFalse(explicitZero.hasStatisticSampleCount)
    }

    @Test
    fun nonBiomedicalSamplesDoNotDependOnBiomedicalRawCount() {
        val received = consume(
            "accelerometer",
            RawSensorCountContract.encode(samples = 9, rawValueCount = null, previewPointCount = 5, eventCount = 7)
        )

        assertEquals(9, received.displayCount)
        assertEquals(5, received.previewPointCount)
        assertTrue(received.hasStatisticSampleCount)
    }

    @Test
    fun previewCannotExceedRetainedWaveformAndDoesNotInflateRawCount() {
        val received = consume(
            "ppg",
            RawSensorCountContract.encode(samples = 32, rawValueCount = 32, previewPointCount = 512, eventCount = 6),
            waveformPointCount = 80
        )

        assertEquals(80, received.previewPointCount)
        assertEquals(32, received.rawValueCount)
        assertEquals(32, received.displayCount)
    }

    @Test
    fun absentEventCountIsDistinctFromExplicitZero() {
        val absent = RawSensorCountContract.encode(0, null, 0, null)
        val zero = RawSensorCountContract.encode(0, null, 0, 0)

        assertFalse(consume("ppg", absent).eventCountKnown)
        assertTrue(consume("ppg", zero).eventCountKnown)
        assertEquals(0, consume("ppg", zero).eventCount)
    }

    private data class ConsumerView(
        val contractVersion: Int,
        val rawValueCount: Int?,
        val displayCount: Int,
        val previewPointCount: Int,
        val eventCount: Int,
        val eventCountKnown: Boolean,
        val hasStatisticSampleCount: Boolean
    )

    private fun consume(
        kind: String,
        fields: Map<String, Int>,
        waveformPointCount: Int = fields["preview_point_count"] ?: 0
    ): ConsumerView {
        val counts = RawSensorCountContract.decode(
            samples = fields["samples"],
            rawValueCount = fields["raw_value_count"],
            previewPointCount = fields["preview_point_count"],
            contractVersion = fields["count_contract_version"],
            eventCount = fields["event_count"]
        )
        val version = counts.contractVersion
        val rawCount = if (RawSensorPayloadPolicy.isBiomedicalKind(kind)) {
            RawSensorPayloadPolicy.sourceValueCount(version, counts.rawValueCount)
        } else {
            counts.samples
        }
        val reportedSamples = counts.samples
        val displayCount = RawSensorPayloadPolicy.displayCount(
            isBiomedical = RawSensorPayloadPolicy.isBiomedicalKind(kind),
            contractVersion = version,
            rawValueCount = rawCount,
            reportedSamples = reportedSamples
        )
        val previewCount = counts.previewPointCount?.let {
            RawSensorPayloadPolicy.previewPointCount(it, waveformPointCount)
        } ?: waveformPointCount
        val eventCountKnown = counts.eventCount != null
        val eventCount = counts.eventCount ?: 0

        return ConsumerView(
            contractVersion = version,
            rawValueCount = rawCount,
            displayCount = displayCount,
            previewPointCount = previewCount,
            eventCount = eventCount,
            eventCountKnown = eventCountKnown,
            hasStatisticSampleCount = RawSensorPayloadPolicy.hasStatisticSampleCount(
                RawSensorPayloadPolicy.isBiomedicalKind(kind), rawCount, displayCount
            )
        )
    }
}
