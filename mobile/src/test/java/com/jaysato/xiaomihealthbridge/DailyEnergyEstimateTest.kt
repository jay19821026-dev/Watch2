package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class DailyEnergyEstimateTest {
    private val profile = UserProfileStore.Profile(
        age = 30,
        sex = PhysiologyAlgorithms.Sex.MALE,
        heightCm = 180.0,
        weightKg = 75.0
    )

    @Test
    fun addsActiveCaloriesToElapsedFractionOfMifflinBasalEstimate() {
        val noon = Instant.parse("2026-09-28T12:00:00Z")
        assertEquals(1_265, DailyEnergyEstimate.totalKcal(profile, 400.0, noon, ZoneOffset.UTC))
    }

    @Test
    fun rejectsMissingNegativeAndNonFiniteActiveCalories() {
        val noon = Instant.parse("2026-09-28T12:00:00Z")
        assertNull(DailyEnergyEstimate.totalKcal(profile, null, noon, ZoneOffset.UTC))
        assertNull(DailyEnergyEstimate.totalKcal(profile, -1.0, noon, ZoneOffset.UTC))
        assertNull(DailyEnergyEstimate.totalKcal(profile, Double.NaN, noon, ZoneOffset.UTC))
        assertNull(DailyEnergyEstimate.totalKcal(profile, Double.POSITIVE_INFINITY, noon, ZoneOffset.UTC))
    }

    @Test
    fun supportsZeroActiveCaloriesAsBasalEstimate() {
        val midnight = Instant.parse("2026-09-28T00:00:00Z")
        assertEquals(0, DailyEnergyEstimate.totalKcal(profile, 0.0, midnight, ZoneOffset.UTC))
    }

    @Test
    fun doesNotInventSexCoefficientWhenProfileIsUnspecified() {
        val unspecified = profile.copy(sex = PhysiologyAlgorithms.Sex.UNSPECIFIED)

        val noon = Instant.parse("2026-09-28T12:00:00Z")
        assertNull(DailyEnergyEstimate.totalKcal(unspecified, 400.0, noon, ZoneOffset.UTC))
    }

    @Test
    fun startsAtLocalMidnightAcrossDaylightSavingTransition() {
        val zone = ZoneId.of("America/Los_Angeles")
        val now = Instant.parse("2024-03-10T16:00:00Z")
        assertEquals(
            Instant.parse("2024-03-10T08:00:00Z"),
            DailyEnergyEstimate.startOfLocalDay(now, zone)
        )
    }

    @Test
    fun proratesBasalCaloriesByActualShortAndLongDstDayLength() {
        val zone = ZoneId.of("America/Los_Angeles")
        val activeCalories = 100.0

        assertEquals(
            965,
            DailyEnergyEstimate.totalKcal(
                profile, activeCalories, Instant.parse("2024-03-10T19:30:00Z"), zone
            )
        )
        assertEquals(
            965,
            DailyEnergyEstimate.totalKcal(
                profile, activeCalories, Instant.parse("2024-11-03T19:30:00Z"), zone
            )
        )
    }
}
