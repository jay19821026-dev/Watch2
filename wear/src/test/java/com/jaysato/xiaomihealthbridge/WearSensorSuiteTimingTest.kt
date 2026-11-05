package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSensorSuiteTimingTest {
    @Test
    fun everySensorKindIsQueuedOrExplicitlyDocumentedAsAnAlias() {
        val queuedKinds = WearSensorSuitePlan.generalQueue(RawVitalsProbe.Kind.entries) +
            WearSensorSuitePlan.biomedicalQueue() +
            WearSensorSuitePlan.wearGateKinds +
            WearSensorSuitePlan.motionTriggerKinds
        val intentionallyAliasedKinds = setOf(RawVitalsProbe.Kind.BIOZ_RESPIRATION)

        assertEquals(
            RawVitalsProbe.Kind.entries.toSet(),
            queuedKinds.toSet() + intentionallyAliasedKinds
        )
        assertEquals(
            queuedKinds.size,
            queuedKinds.toSet().size
        )
    }

    @Test
    fun productionQueueAndResolverFitBeforeDeadlineWithCleanupReserve() {
        val expectedQueue = listOf(
            RawVitalsProbe.Kind.HEART_RATE,
            RawVitalsProbe.Kind.MAX_HEART_RATE,
            RawVitalsProbe.Kind.PPG,
            RawVitalsProbe.Kind.SPO2,
            RawVitalsProbe.Kind.MI_SPO2,
            RawVitalsProbe.Kind.LIFE_Q_OTT,
            RawVitalsProbe.Kind.ECG,
            RawVitalsProbe.Kind.BIOZ
        )
        val queue = WearSensorSuitePlan.biomedicalQueue()

        assertEquals(expectedQueue, queue)
        assertEquals(5_000L, WearSensorSuitePlan.stageTimeoutMillis(RawVitalsProbe.Kind.MAX_HEART_RATE))
        assertEquals(5_000L, WearSensorSuitePlan.stageTimeoutMillis(RawVitalsProbe.Kind.MI_SPO2))
        assertEquals(15_000L, WearSensorSuitePlan.stageTimeoutMillis(RawVitalsProbe.Kind.PPG))
        assertEquals(WearMeasurementPolicy.ECG_SUITE_DISCOVERY_TIMEOUT_MILLIS,
            WearSensorSuitePlan.stageTimeoutMillis(RawVitalsProbe.Kind.ECG))
        assertEquals(5_000L, WearSensorSuitePlan.stageTimeoutMillis(RawVitalsProbe.Kind.BIOZ))
        assertEquals(45_000L, WearSensorSuitePlan.stageTimeoutMillis(RawVitalsProbe.Kind.HEART_RATE))
        assertEquals(
            WearSensorSuitePlan.wearGateKinds.size * WearSensorSuitePlan.WEAR_GATE_TIMEOUT_MILLIS +
                WearSensorSuitePlan.MOTION_TRIGGER_TIMEOUT_MILLIS +
                WearSensorSuitePlan.STATIONARY_QUIET_TIMEOUT_MILLIS +
                WearSensorSuitePlan.GENERAL_SUITE_TIMEOUT_MILLIS +
                queue.sumOf(WearSensorSuitePlan::stageTimeoutMillis),
            WearSensorSuiteTiming.CONFIGURED_STAGE_UPPER_BOUND_MILLIS
        )
        assertTrue(
            WearSensorSuiteTiming.CONFIGURED_STAGE_UPPER_BOUND_MILLIS +
                WearSensorSuiteTiming.CLEANUP_RESERVE_MILLIS < WearSensorSuiteTiming.DEADLINE_MILLIS
        )
        assertTrue(WearSensorSuiteTiming.DEADLINE_MILLIS > 0L)
        assertTrue(WearSensorSuiteTiming.WAKELOCK_TIMEOUT_MILLIS > WearSensorSuiteTiming.DEADLINE_MILLIS)
        assertTrue(WearSensorSuiteTiming.WAKELOCK_TIMEOUT_MILLIS <= 8 * 60_000L)
    }

    @Test
    fun timeoutCleanupKeepsPartialResultAndAttemptsEveryResourceRelease() {
        val calls = mutableListOf<String>()
        val failures = WearSensorSuiteCleanup.run(
            listOf(
                WearSensorSuiteCleanup.Action("publish_partial_result") { calls += "partial" },
                WearSensorSuiteCleanup.Action("stop_listener") {
                    calls += "listener"
                    error("test listener failure")
                },
                WearSensorSuiteCleanup.Action("release_wakelock") { calls += "wakelock" },
                WearSensorSuiteCleanup.Action("stop_service") { calls += "service" }
            )
        )

        assertEquals(listOf("partial", "listener", "wakelock", "service"), calls)
        assertEquals(listOf("stop_listener"), failures.map { it.action })
        assertFalse(WearMeasurementPolicy.reportSuccess(WearMeasurementPolicy.SuiteRunState.TIMED_OUT, false))
        assertEquals("掃描逾時", WearMeasurementPolicy.sensorSuiteStatusHeadline("timed_out"))
    }

    @Test
    fun terminalMissingChannelsAreComputedAfterActiveProbesAreFinalized() {
        val finalized = mutableSetOf<String>()
        var terminalSummary = ""
        val expected = listOf(RawVitalsProbe.Kind.ECG, RawVitalsProbe.Kind.BIOZ_RESPIRATION)

        WearSensorSuiteCleanup.run(
            listOf(
                WearSensorSuiteCleanup.Action("stop_active_probe") {
                    finalized += RawVitalsProbe.Kind.ECG.wireName
                },
                WearSensorSuiteCleanup.Action("publish_terminal_status") {
                    terminalSummary = WearMeasurementPolicy.unreportedChannelSummary(
                        WearMeasurementPolicy.unreportedSuiteKinds(finalized, expected)
                    )
                }
            )
        )

        assertFalse(terminalSummary.contains("未產生終態結果"))
        assertTrue(terminalSummary.contains("呼吸率未獨立量測"))
    }
}
