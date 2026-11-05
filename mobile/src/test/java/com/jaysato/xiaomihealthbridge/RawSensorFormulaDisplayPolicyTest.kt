package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSensorFormulaDisplayPolicyTest {
    private val now = 100_000_000L

    @Test
    fun freshPpgFormulaRequiresCurrentSessionOpticalEligibilityAndHrvEligibility() {
        val result = evaluate(
            kind = "ppg",
            measuredAt = now - 5_000L,
            sessionStartedAt = now - 60_000L,
            hrvMeasuredAt = now - 5_000L,
            hrvSource = "ppg"
        )

        assertTrue(result.showOpticalMetrics)
        assertTrue(result.showHrvMetrics)
        assertFalse(result.showEcgPulse)
    }

    @Test
    fun staleAndUnknownTimePpgFormulasAreHidden() {
        val stale = evaluate(
            kind = "ppg",
            measuredAt = now - SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS - 1L,
            sessionStartedAt = now - SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS - 2_000L,
            hrvMeasuredAt = now - SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS - 1L,
            hrvSource = "ppg"
        )
        val unknown = evaluate(kind = "ppg", measuredAt = 0L, sessionStartedAt = 0L, hrvMeasuredAt = 0L, hrvSource = "ppg")

        assertFalse(stale.showOpticalMetrics)
        assertFalse(stale.showHrvMetrics)
        assertFalse(unknown.showOpticalMetrics)
        assertFalse(unknown.showHrvMetrics)
    }

    @Test
    fun formulaFromDifferentSessionOrDifferentHrvTimestampIsHidden() {
        val priorSession = evaluate(
            kind = "ppg",
            measuredAt = now - 5_000L,
            sessionStartedAt = now - 1_000L,
            hrvMeasuredAt = now - 5_000L,
            hrvSource = "ppg"
        )
        val mismatchedHrvTime = evaluate(
            kind = "ppg",
            measuredAt = now - 5_000L,
            sessionStartedAt = now - 60_000L,
            hrvMeasuredAt = now - 4_000L,
            hrvSource = "ppg"
        )

        assertFalse(priorSession.showOpticalMetrics)
        assertFalse(priorSession.showHrvMetrics)
        assertTrue(mismatchedHrvTime.showOpticalMetrics)
        assertFalse(mismatchedHrvTime.showHrvMetrics)
    }

    @Test
    fun opticalFormulaFieldsCannotBypassOpticalPulsePolicy() {
        val result = evaluate(
            kind = "ppg",
            measuredAt = now - 5_000L,
            sessionStartedAt = now - 60_000L,
            hrvMeasuredAt = now - 5_000L,
            hrvSource = "ppg",
            channelMappingVerified = false
        )

        assertFalse(result.showOpticalMetrics)
    }

    @Test
    fun callbackFallbackTimestampsBlockOpticalAndHrvResearchOutputs() {
        val result = evaluate(
            kind = "ppg",
            measuredAt = now - 5_000L,
            sessionStartedAt = now - 60_000L,
            hrvMeasuredAt = now - 5_000L,
            hrvSource = "ppg",
            sensorTimestampSource = "callback_wall_clock_fallback"
        )

        assertFalse(result.showOpticalMetrics)
        assertFalse(result.showHrvMetrics)
    }

    @Test
    fun ecgFormulaIsResearchOnlyAndRequiresQualifiedFreshSameSessionData() {
        val research = evaluate(
            kind = "ecg",
            status = "research_signal_unscaled",
            measuredAt = now - 5_000L,
            sessionStartedAt = now - 60_000L,
            hrvMeasuredAt = now - 5_000L,
            hrvSource = "ecg",
            ecgResearchOnly = true,
            waveformEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
        )
        val notResearchOnly = evaluate(
            kind = "ecg",
            status = "research_signal_unscaled",
            measuredAt = now - 5_000L,
            sessionStartedAt = now - 60_000L,
            hrvMeasuredAt = now - 5_000L,
            hrvSource = "ecg",
            ecgResearchOnly = false
        )

        assertTrue(research.showHrvMetrics)
        assertTrue(research.showEcgPulse)
        assertFalse(notResearchOnly.showEcgPulse)
    }

    @Test
    fun environmentalFormulaVisibilityRemainsUnchangedByPhysiologicalGates() {
        val result = evaluate(
            kind = "pressure",
            measuredAt = 0L,
            sessionStartedAt = 0L,
            hrvMeasuredAt = 0L,
            hrvSource = "",
            hasOpticalMetrics = false,
            hasHrvMetrics = false,
            hasEnvironmentMetrics = true
        )

        assertTrue(result.showEnvironmentMetrics)
        assertFalse(result.showOpticalMetrics)
        assertFalse(result.showHrvMetrics)
    }

    private fun evaluate(
        kind: String,
        status: String = "has_data",
        measuredAt: Long,
        sessionStartedAt: Long,
        hrvMeasuredAt: Long,
        hrvSource: String,
        sensorTimestampSource: String = "android_sensor_event_elapsed_realtime",
        channelMappingVerified: Boolean = true,
        ecgResearchOnly: Boolean = false,
        hasOpticalMetrics: Boolean = true,
        hasHrvMetrics: Boolean = true,
        hasEnvironmentMetrics: Boolean = false,
        wearQualified: Boolean? = true,
        waveformEncoding: String? = null
    ) = RawSensorFormulaDisplayPolicy.evaluate(
        kind = kind,
        samples = 128,
        status = status,
        measuredAtMillis = measuredAt,
        timestampSource = sensorTimestampSource,
        hrvTimestampSource = "android_sensor_event_elapsed_realtime",
        sessionStartedAtMillis = sessionStartedAt,
        nowMillis = now,
        qualityScore = 80,
        sampleRateHz = if (kind == "ecg") 0f else 100f,
        channelMappingVerified = channelMappingVerified,
        formulaPulseBpm = 72f,
        hrvMeasuredAtMillis = hrvMeasuredAt,
        hrvSourceKind = hrvSource,
        hrvSampleCount = 128,
        hrvRrCount = 8,
        ecgResearchOnly = ecgResearchOnly,
        hasOpticalMetrics = hasOpticalMetrics,
        hasHrvMetrics = hasHrvMetrics,
        hasEnvironmentMetrics = hasEnvironmentMetrics,
        wearQualified = wearQualified,
        waveformEncoding = waveformEncoding
    )
}
