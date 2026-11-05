package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepDayAggregationTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val midnight = LocalDate.parse("2026-09-28").atStartOfDay(zone).toInstant()

    @Test
    fun wholeIntervalsEndingAtMidnightStayWithPreviousObservedDay() {
        val totals = StepDayAggregation.totalsByObservedDay(
            listOf(
                StepIntervalMeasurement("watch.app", midnight.minusSeconds(600), midnight.minusSeconds(300), 120),
                StepIntervalMeasurement("watch.app", midnight.minusSeconds(300), midnight, 80)
            ),
            zone
        )

        assertEquals(mapOf(LocalDate.parse("2026-09-27") to 200L), totals)
    }

    @Test
    fun crossingMidnightIntervalsAreNotAssignedToEitherDay() {
        val totals = StepDayAggregation.totalsByObservedDay(
            listOf(
                StepIntervalMeasurement("watch.app", midnight.minusSeconds(300), midnight.plusSeconds(300), 200)
            ),
            zone
        )

        assertNull(totals)
    }

    @Test
    fun overlappingOrOverflowingStepIntervalsAreRejected() {
        val overlapping = listOf(
            StepIntervalMeasurement("watch.app", midnight, midnight.plusSeconds(120), 100),
            StepIntervalMeasurement("watch.app", midnight.plusSeconds(60), midnight.plusSeconds(180), 100)
        )
        val overflowing = listOf(
            StepIntervalMeasurement("watch.app", midnight, midnight.plusSeconds(60), Long.MAX_VALUE),
            StepIntervalMeasurement("watch.app", midnight.plusSeconds(60), midnight.plusSeconds(120), 1)
        )

        assertNull(StepDayAggregation.totalsByObservedDay(overlapping, zone))
        assertNull(StepDayAggregation.totalsByObservedDay(overflowing, zone))
    }
}
