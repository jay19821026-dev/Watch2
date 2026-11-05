package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperaturePresentationTest {
    private val now = 200_000_000L
    private val trusted = "android_sensor_event_elapsed_realtime"

    private fun channel(source: String, time: Long = now - 500L, value: Double = 33.0) =
        TemperaturePresentation.Channel(source, value, time, trusted, source, 2, listOf(32f, 34f))

    @Test
    fun preferredChannelKeepsItsOwnMetadataAndWaveform() {
        val thermometer = channel("thermometer")
        val selected = TemperaturePresentation.select(listOf(thermometer, channel("feature"), channel("body")), now)
        assertEquals(thermometer, selected)
        assertTrue(TemperaturePresentation.metadata(selected!!, now).contains("age_ms=500"))
        assertTrue(TemperaturePresentation.metadata(selected, now).contains("status=thermometer"))
    }

    @Test
    fun stalePreferredChannelFallsBackToFreshFeatureWithoutBorrowingRawMetadata() {
        val feature = channel("feature").copy(count = 0, waveform = emptyList())
        assertEquals(feature, TemperaturePresentation.select(
            listOf(channel("thermometer", 1L), feature, channel("body")), now
        ))
    }

    @Test
    fun historicalValueRetainsActualAgeAndStaleState() {
        val historical = channel("thermometer", 1L)
        assertEquals(historical, TemperaturePresentation.select(listOf(historical), now))
        assertEquals("stale", TemperaturePresentation.freshness(historical, now))
        assertTrue(TemperaturePresentation.metadata(historical, now).contains("age_ms=199999999"))
    }

    @Test
    fun missingUntrustedAndFutureTimesAreNotFresh() {
        assertEquals("measurement_time_missing", TemperaturePresentation.freshness(channel("body", 0L), now))
        assertEquals("timestamp_untrusted", TemperaturePresentation.freshness(channel("body").copy(timestampSource = null), now))
        val future = channel("body", now + 500L)
        assertEquals("future_timestamp", TemperaturePresentation.freshness(future, now))
        assertTrue(TemperaturePresentation.metadata(future, now).contains("age_ms=-500"))
    }

    @Test
    fun invalidValuesAreSkippedAndUnitsNeverInferredFromPlausibleNumbers() {
        assertNull(TemperaturePresentation.select(listOf(channel("thermometer", value = Double.NaN)
            .copy(waveform = listOf(Float.NaN, Float.POSITIVE_INFINITY))), now))
        val body = channel("body", value = 36.5)
        assertEquals(body, TemperaturePresentation.select(listOf(channel("thermometer", value = Double.POSITIVE_INFINITY)
            .copy(waveform = emptyList()), body), now))
        assertTrue(TemperaturePresentation.metadata(body, now).contains("raw / 單位未驗證"))
    }

    @Test
    fun finitePreviewRemainsVisibleWhenScalarIsMissingOrInvalid() {
        for (scalar in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) {
            val raw = channel("thermometer").copy(value = scalar,
                waveform = listOf(31f, 32f, Float.NaN))
            assertEquals(raw.copy(value = 32.0), TemperaturePresentation.select(listOf(raw), now))
        }
    }

    @Test
    fun previewFallbackKeepsChannelTimeStatusAndFreshnessPriority() {
        val stale = channel("thermometer", 1L).copy(value = null)
        val fresh = channel("body").copy(value = null, status = "quality_rejected")
        assertEquals(fresh.copy(value = 34.0), TemperaturePresentation.select(listOf(stale, fresh), now))
        assertEquals("stale", TemperaturePresentation.freshness(
            TemperaturePresentation.select(listOf(stale), now)!!, now))
        assertEquals("measurement_time_missing", TemperaturePresentation.freshness(
            TemperaturePresentation.select(listOf(fresh.copy(measuredAt = 0L)), now)!!, now))
    }

    @Test
    fun finiteScalarIsPreservedAndCountsAloneDoNotCreateValues() {
        val scalar = channel("thermometer", value = 0.0)
        assertEquals(scalar, TemperaturePresentation.select(listOf(scalar), now))
        assertNull(TemperaturePresentation.select(listOf(scalar.copy(value = null,
            count = 100, waveform = emptyList())), now))
    }

    @Test
    fun aggregationMethodIsKeptInChannelMetadata() {
        val averaged = channel("feature").copy(
            aggregationMethod = "probe_finite_sample_mean",
            aggregationSampleCount = 4
        )

        assertTrue(TemperaturePresentation.metadata(averaged, now)
            .contains("aggregation=probe_finite_sample_mean (n=4)"))
    }
}
