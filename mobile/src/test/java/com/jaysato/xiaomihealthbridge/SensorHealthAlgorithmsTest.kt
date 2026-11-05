package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SensorHealthAlgorithmsTest {
    @Test fun gaitEfficiencyReturnsTheUnclampedRatioAndRejectsInvalidInputs() {
        assertEquals(4.0, SensorHealthAlgorithms.gaitEfficiency(400.0, 100)!!, 0.0)
        assertEquals(0.05, SensorHealthAlgorithms.gaitEfficiency(5.0, 100)!!, 0.0)
        assertNull(SensorHealthAlgorithms.gaitEfficiency(Double.POSITIVE_INFINITY, 100))
        assertNull(SensorHealthAlgorithms.gaitEfficiency(100.0, 0))
        assertNull(SensorHealthAlgorithms.gaitEfficiency(0.0, 100))
    }

    @Test fun exerciseDurationRetainsFractionalMinutes() {
        val start = Instant.parse("2026-09-28T10:00:00Z")

        assertEquals(1.5, SensorHealthAlgorithms.intervalDurationMinutes(start, start.plusSeconds(90))!!, 0.000001)
        assertNull(SensorHealthAlgorithms.intervalDurationMinutes(start, start))
        assertNull(SensorHealthAlgorithms.intervalDurationMinutes(start, start.minusSeconds(1)))
    }

    @Test fun oxygenObservationWindowUsesOnlyValidSampleTimestamps() {
        val first = Instant.parse("2026-09-27T10:00:00Z")
        val last = Instant.parse("2026-09-28T10:00:00Z")
        val summary = SensorHealthAlgorithms.timedOxygenSampleSummary(
            listOf(
                Instant.parse("2026-09-27T00:00:00Z") to Double.NaN,
                first to 96.0,
                last to 94.0,
                Instant.parse("2026-09-29T00:00:00Z") to 101.0
            )
        )!!

        assertEquals(2, summary.validSampleCount)
        assertEquals(first, summary.firstValidSampleAt)
        assertEquals(last, summary.lastValidSampleAt)
    }

    @Test fun timedOxygenSummaryRejectsOnlyInvalidSamplesAndReturnsNullWhenNoneRemain() {
        val time = Instant.parse("2026-09-28T10:00:00Z")
        assertNull(SensorHealthAlgorithms.timedOxygenSampleSummary(listOf(time to 101.0, time to Double.NaN)))
    }

    @Test fun sleepScheduleComparisonDoesNotTreatMissingDatesAsNoSleep() {
        val zone = java.time.ZoneOffset.UTC
        fun interval(day: Int) = java.time.LocalDate.of(2026, 9, day)
            .atTime(22, 0).atZone(zone).toInstant() to
            java.time.LocalDate.of(2026, 9, day).atTime(23, 0).atZone(zone).toInstant()

        assertNull(SensorHealthAlgorithms.sleepScheduleConsistency(listOf(interval(1), interval(3)), zone))
        assertEquals(100, SensorHealthAlgorithms.sleepScheduleConsistency(listOf(interval(1), interval(2)), zone))
    }

    @Test fun sleepScheduleComparisonSkipsDaylightSavingTransitionDates() {
        val zone = java.time.ZoneId.of("America/New_York")
        fun interval(date: LocalDate) = date.atTime(22, 0).atZone(zone).toInstant() to
            date.atTime(23, 0).atZone(zone).toInstant()
        val spring = listOf(7, 8, 9).map { interval(LocalDate.of(2026, 3, it)) }
        val autumn = listOf(31, 1, 2).mapIndexed { index, day ->
            interval(if (index == 0) LocalDate.of(2026, 10, day) else LocalDate.of(2026, 11, day))
        }

        assertNull(SensorHealthAlgorithms.sleepScheduleConsistency(spring, zone))
        assertNull(SensorHealthAlgorithms.sleepScheduleConsistency(autumn, zone))
    }

    @Test fun oxygenSummaryReportsValidSampleRangeWithoutCallingItStability() {
        val summary = SensorHealthAlgorithms.oxygenSampleSummary(listOf(98.0, 65.0, 101.0, Double.NaN, 0.0, 92.0))!!

        assertEquals(4, summary.validSampleCount)
        assertEquals(0.0, summary.minimumPercent, 0.0)
        assertEquals(98.0, summary.maximumPercent, 0.0)
        assertEquals(50.0, summary.below90Percent!!, 0.0001)
        assertNull(SensorHealthAlgorithms.oxygenSampleSummary(listOf(-1.0, 101.0, Double.NaN)))
        assertNull(SensorHealthAlgorithms.oxygenSampleSummary(listOf(98.0, 88.0))!!.below90Percent)
    }

    @Test fun banisterTrimpUsesSexSpecificPublishedCoefficients() {
        val male = SensorHealthAlgorithms.banisterTrimp(60.0, 120.0, 60.0, 180.0, PhysiologyAlgorithms.Sex.MALE)
        val female = SensorHealthAlgorithms.banisterTrimp(60.0, 120.0, 60.0, 180.0, PhysiologyAlgorithms.Sex.FEMALE)

        assertEquals(60.0 * 0.5 * 0.64 * kotlin.math.exp(1.92 * 0.5), male!!, 0.0001)
        assertEquals(60.0 * 0.5 * 0.86 * kotlin.math.exp(1.67 * 0.5), female!!, 0.0001)
        assertNull(SensorHealthAlgorithms.banisterTrimp(60.0, 120.0, 60.0, 180.0, PhysiologyAlgorithms.Sex.UNSPECIFIED))
    }

    @Test fun banisterTrimpRejectsInvalidAndNonFiniteInputs() {
        assertNull(SensorHealthAlgorithms.banisterTrimp(Double.NaN, 120.0, 60.0, 180.0, PhysiologyAlgorithms.Sex.MALE))
        assertNull(SensorHealthAlgorithms.banisterTrimp(60.0, Double.POSITIVE_INFINITY, 60.0, 180.0, PhysiologyAlgorithms.Sex.MALE))
        assertNull(SensorHealthAlgorithms.banisterTrimp(0.0, 120.0, 60.0, 180.0, PhysiologyAlgorithms.Sex.MALE))
    }

    @Test fun temperatureTrendRequiresFourDistinctDaysAndUsesDailyMedians() {
        val readings = listOf(
            LocalDate.parse("2026-09-25") to 35.0,
            LocalDate.parse("2026-09-25") to 39.0,
            LocalDate.parse("2026-09-26") to 35.2,
            LocalDate.parse("2026-09-27") to 36.0,
            LocalDate.parse("2026-09-28") to 38.0
        )

        assertNull(SensorHealthAlgorithms.temperatureDeviationByDay(readings.take(3)))
        assertEquals(2.0, SensorHealthAlgorithms.temperatureDeviationByDay(readings)!!, 0.0001)
    }

    @Test fun hrvTrendComparesLatestDailyMedianWithPriorPersonalBaseline() {
        val readings = listOf(
            LocalDate.parse("2026-09-24") to 40.0,
            LocalDate.parse("2026-09-25") to 50.0,
            LocalDate.parse("2026-09-25") to 42.0,
            LocalDate.parse("2026-09-26") to 48.0,
            LocalDate.parse("2026-09-27") to 35.0,
            LocalDate.parse("2026-09-27") to 39.0
        )

        val trend = SensorHealthAlgorithms.hrvDeviationFromPersonalBaseline(readings)!!

        assertEquals(46.0, trend.baseline, 0.0001)
        assertEquals(37.0, trend.latest, 0.0001)
        assertEquals(-9.0, trend.difference, 0.0001)
        assertEquals(3, trend.baselineDays)
    }

    @Test fun hrvTrendRequiresTwoPriorDaysAndRejectsNonPositiveValues() {
        assertNull(
            SensorHealthAlgorithms.hrvDeviationFromPersonalBaseline(
                listOf(LocalDate.parse("2026-09-27") to 35.0, LocalDate.parse("2026-09-28") to 40.0)
            )
        )
        assertNull(
            SensorHealthAlgorithms.hrvDeviationFromPersonalBaseline(
                listOf(LocalDate.parse("2026-09-27") to 0.0, LocalDate.parse("2026-09-28") to Double.NaN)
            )
        )
    }

    @Test fun constantChannelCannotBecomePulseSignal() {
        val quality = SensorHealthAlgorithms.waveformQuality(List(64) { 524287f }, 25f, "ppg")
        assertEquals(0, quality.score)
        assertTrue(quality.rrIntervalsMs.isEmpty())
    }

    @Test fun invalidTimingOrSamplesCannotProduceRhythm() {
        listOf(0f, Float.NaN, Float.POSITIVE_INFINITY).forEach { rate ->
            val quality = SensorHealthAlgorithms.waveformQuality(List(64) { it.toFloat() }, rate, "ecg")
            assertTrue(quality.score > 0)
            assertTrue(quality.relativeOnly)
            assertTrue(quality.rrIntervalsMs.isEmpty())
        }
        assertEquals(0, SensorHealthAlgorithms.waveformQuality(List(64) { Float.NaN }, 25f, "ecg").score)
    }

    @Test fun unknownSamplingRateCannotProduceFallbackQualityOrIntervals() {
        val waveform = List(256) { index -> kotlin.math.sin(index / 5.0).toFloat() * 100f }

        val quality = SensorHealthAlgorithms.waveformQuality(waveform, 0f, "ppg")

        assertTrue(quality.score > 0)
        assertTrue(quality.rrIntervalsMs.isEmpty())
        assertTrue(quality.relativeOnly)
        assertTrue(quality.metrics.contains("僅相對樣本域指標"))
    }

    @Test fun finiteSegmentsRemainAnalyzableAcrossNaNGapMarkers() {
        val waveform = List(128) { index ->
            if (index in 48..63) Float.NaN
            else kotlin.math.sin(index / 4.0).toFloat() * 100f + 1_000f
        }

        val quality = SensorHealthAlgorithms.waveformQuality(waveform, 25f, "ppg")

        assertTrue(quality.score > 0)
        assertTrue(quality.metrics.contains("有限樣本 112/128 點"))
        assertTrue(quality.rrIntervalsMs.isNotEmpty())
        assertFalse(quality.relativeOnly)
    }

    @Test fun allNonFiniteWaveformStillHasNoSignalQualityEvidence() {
        val quality = SensorHealthAlgorithms.waveformQuality(List(64) { Float.NaN }, 25f, "ecg")

        assertEquals(0, quality.score)
        assertTrue(quality.rrIntervalsMs.isEmpty())
        assertTrue(quality.metrics.contains("沒有有限波形樣本"))
    }

    @Test fun wearGateUsesAndroidOffBodyEventSemantics() {
        assertEquals(false, SensorHealthAlgorithms.wearQualified(0.0))
        assertEquals(true, SensorHealthAlgorithms.wearQualified(1.0))
        assertNull(SensorHealthAlgorithms.wearQualified(null))
        assertNull(SensorHealthAlgorithms.wearQualified(Double.NaN))
        assertNull(SensorHealthAlgorithms.wearQualified(0.5))
        assertNull(SensorHealthAlgorithms.wearQualified(-1.0))
    }

    @Test fun trustedOxygenRequiresPercentRange() {
        assertTrue(SensorHealthAlgorithms.isTrustedOxygenPercent(97.0, 1.0))
        assertFalse(SensorHealthAlgorithms.isTrustedOxygenPercent(92.0, 1.0))
        assertFalse(SensorHealthAlgorithms.isTrustedOxygenPercent(44.0, 24.0))
    }

    @Test fun waveformQualityScoresStableOscillation() {
        val values = List(64) { index -> kotlin.math.sin(index / 4.0).toFloat() * 100f + 1000f }
        val quality = SensorHealthAlgorithms.waveformQuality(values, 25f, "ppg")
        assertTrue(quality.score >= 50)
        assertTrue(quality.rrIntervalsMs.isNotEmpty())
    }

    @Test fun packedSamplesKeepFinitePrefix() {
        assertEquals(listOf(1f, 2f, 3f), SensorHealthAlgorithms.packedSamples(floatArrayOf(1f, 2f, 3f, Float.NaN), 8))
    }

    @Test fun heartRateReserveMinutesUseElapsedTimeAndStandardIntensityWeighting() {
        val start = Instant.parse("2026-09-27T10:00:00Z")
        val samples = listOf(
            start to 120.0,
            start.plusSeconds(120) to 120.0,
            start.plusSeconds(240) to 150.0
        )

        assertEquals(6, SensorHealthAlgorithms.heartRateReserveEquivalentMinutes(samples, 60.0, 180.0))
    }

    @Test fun heartRateReserveMinutesSkipInvalidAndLongSamplingGaps() {
        val start = Instant.parse("2026-09-27T10:00:00Z")
        val longGap = listOf(start to 150.0, start.plusSeconds(301) to 150.0)

        assertNull(SensorHealthAlgorithms.heartRateReserveEquivalentMinutes(longGap, 60.0, 180.0))
        assertNull(SensorHealthAlgorithms.heartRateReserveEquivalentMinutes(listOf(start to Double.NaN, start.plusSeconds(60) to 150.0), 60.0, 180.0))
    }

    @Test fun heartRateReserveMinutesSortSamplesAndDoNotDoubleCountDuplicateTimestamps() {
        val start = Instant.parse("2026-09-27T10:00:00Z")
        val samples = listOf(
            start.plusSeconds(60) to 120.0,
            start to 120.0,
            start to 150.0
        )

        assertEquals(1, SensorHealthAlgorithms.heartRateReserveEquivalentMinutes(samples, 60.0, 180.0))
    }

    @Test fun heartRateReserveMinutesRejectInvalidHeartRateRange() {
        val start = Instant.parse("2026-09-27T10:00:00Z")
        val samples = listOf(start to 150.0, start.plusSeconds(60) to 150.0)

        assertNull(SensorHealthAlgorithms.heartRateReserveEquivalentMinutes(samples, 180.0, 60.0))
    }

    @Test fun overlappingExerciseIntervalsAreCountedOnce() {
        val start = Instant.parse("2026-09-27T10:00:00Z")
        val intervals = listOf(
            start to start.plusSeconds(60 * 60),
            start.plusSeconds(30 * 60) to start.plusSeconds(90 * 60)
        )

        assertEquals(1, SensorHealthAlgorithms.mergeIntervals(intervals).size)
        assertEquals(90L, SensorHealthAlgorithms.unionDurationMinutes(intervals))
    }
}
