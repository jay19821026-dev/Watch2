package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorSuiteRateExportPolicyTest {
    @Test
    fun sampleRateExportsOnlyWhenItMatchesHalReportedPeriod() {
        val valid = SensorSuiteExportPolicy.sampleRateExport(100.0, 0.01, "continuous")
        assertEquals(100.0, valid.sampleRateHz!!, 0.0)
        assertEquals("sensor_hal_type_sampling", valid.source)

        val mismatch = SensorSuiteExportPolicy.sampleRateExport(8.0, 0.01, "continuous")
        assertNull(mismatch.sampleRateHz)
        assertEquals("sample_rate_period_mismatch", mismatch.source)
    }

    @Test
    fun missingInvalidAndDerivedRatesNeverLookLikeMeasuredZeroHertz() {
        assertEquals("unreported", SensorSuiteExportPolicy.sampleRateExport(8.0, null, "continuous").source)
        assertEquals("invalid_reported_sample_rate", SensorSuiteExportPolicy.sampleRateExport(Double.NaN, 0.01, "continuous").source)
        val internalUnknownSentinel = SensorSuiteExportPolicy.sampleRateExport(0.0, 0.01, "continuous")
        assertNull(internalUnknownSentinel.sampleRateHz)
        assertEquals("invalid_reported_sample_rate", internalUnknownSentinel.source)
        assertEquals("not_applicable_derived", SensorSuiteExportPolicy.sampleRateExport(1.0, 1.0, "derived").source)
        assertNull(SensorSuiteExportPolicy.sampleRateExport(1.0, 1.0, "derived").sampleRateHz)
    }

    @Test
    fun eventRateRequiresItsExplicitSessionThroughputSource() {
        val reported = SensorSuiteExportPolicy.eventRateExport(2.5, "event_count_over_elapsed_realtime_duration")
        assertEquals(2.5, reported.eventsPerSessionSecond!!, 0.0)
        assertNull(SensorSuiteExportPolicy.eventRateExport(Double.POSITIVE_INFINITY, "event_count_over_elapsed_realtime_duration").eventsPerSessionSecond)
        assertNull(SensorSuiteExportPolicy.eventRateExport(2.5, "sensor_clock").eventsPerSessionSecond)
    }

    @Test
    fun highSamplingJitterIsRetainedButExplicitlyClassifiedRelativeToPeriod() {
        val evidence = SensorSuiteExportPolicy.jitterExport(0.01, 0.05)
        assertEquals(5.0, evidence.periodRatio!!, 0.0)
        assertEquals("standard_deviation_above_mean_period", evidence.relation)
    }

    @Test
    fun invalidHalPeriodOrJitterCannotBeExportedAsEvidence() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 0.00001, 1.1).forEach { period ->
            assertNull(SensorSuiteExportPolicy.sampleRateExport(100.0, period, "continuous").sampleRateHz)
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach { jitter ->
            assertNull(SensorSuiteExportPolicy.jitterExport(0.01, jitter).standardDeviationSeconds)
        }
    }
}
