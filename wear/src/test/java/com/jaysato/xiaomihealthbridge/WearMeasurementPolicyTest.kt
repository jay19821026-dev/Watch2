package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.HealthAlgorithmEngine as ClinicalSensorFormulas

import android.hardware.SensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearMeasurementPolicyTest {
    @Test
    fun biozRawCaptureNeedsRecentConfirmedWearAsWellAsEnoughEvents() {
        assertTrue(WearMeasurementPolicy.hasWornBiozRawCapture(true, 40, 40))
        assertFalse(WearMeasurementPolicy.hasWornBiozRawCapture(false, 40, 40))
        assertFalse(WearMeasurementPolicy.hasWornBiozRawCapture(null, 40, 40))
        assertFalse(WearMeasurementPolicy.hasWornBiozRawCapture(true, 39, 40))
        assertFalse(WearMeasurementPolicy.hasWornBiozRawCapture(true, 40, 0))
        assertTrue(WearHealthConclusion.forBioz(40, false).contains("沒有戴好"))
        assertTrue(WearHealthConclusion.forBioz(40, null).contains("貼合"))
        assertTrue(WearHealthConclusion.forBioz(40, true).contains("沒有可顯示的體成分讀值"))
    }

    @Test
    fun researchStressRequiresConfirmedWearOpticalSignalAndEnoughIntervals() {
        assertTrue(WearMeasurementPolicy.canPublishResearchStressScore(true, true, 5))
        assertFalse(WearMeasurementPolicy.canPublishResearchStressScore(false, true, 5))
        assertFalse(WearMeasurementPolicy.canPublishResearchStressScore(null, true, 5))
        assertFalse(WearMeasurementPolicy.canPublishResearchStressScore(true, false, 5))
        assertFalse(WearMeasurementPolicy.canPublishResearchStressScore(true, true, 4))
    }

    @Test
    fun dedicatedPpgRequiresExtendedRawValueTarget() {
        assertEquals(1024, WearMeasurePageActivity.Mode.PPG.minSamples)
    }
    @Test
    fun recentReadingRejectsFutureMissingAndExpiredTimestamps() {
        assertTrue(WearMeasurementPolicy.hasRecentSensorSuiteStatus(100L, 200L, 100L))
        assertFalse(WearMeasurementPolicy.hasRecentSensorSuiteStatus(201L, 200L, 100L))
        assertFalse(WearMeasurementPolicy.hasRecentSensorSuiteStatus(0L, 200L, 100L))
        assertFalse(WearMeasurementPolicy.hasRecentSensorSuiteStatus(99L, 200L, 100L))
        assertFalse(WearMeasurementPolicy.hasRecentSensorSuiteStatus(100L, 200L, -1L))
    }
    @Test
    fun rawEventCoverageRequiresEventsAndExcludesDerivedValues() {
        assertTrue(WearMeasurementPolicy.hasRawEvents(8, "continuous"))
        assertTrue(WearMeasurementPolicy.hasRawEvents(1, "one_shot"))
        assertFalse(WearMeasurementPolicy.hasRawEvents(0, "continuous"))
        assertFalse(WearMeasurementPolicy.hasRawEvents(-1, "continuous"))
        assertFalse(WearMeasurementPolicy.hasRawEvents(8, "derived"))
    }
    @Test fun runningUiRequiresBoundedChronologicalSession() {
        assertTrue(WearMeasurementPolicy.hasLiveSuiteWindow("running", 100L, 200L, 100L))
        assertFalse(WearMeasurementPolicy.hasLiveSuiteWindow("running", 100L, 201L, 100L))
        assertFalse(WearMeasurementPolicy.hasLiveSuiteWindow("running", 0L, 200L, 100L))
        assertFalse(WearMeasurementPolicy.hasLiveSuiteWindow("running", 201L, 200L, 100L))
        assertFalse(WearMeasurementPolicy.hasLiveSuiteWindow("completed", 100L, 200L, 100L))
        assertFalse(WearMeasurementPolicy.hasLiveSuiteWindow("running", 100L, 200L, 0L))
    }

    @Test
    fun writesSuiteEndTimestampOnlyForChronologicalTerminalStates() {
        assertTrue(
            WearMeasurementPolicy.shouldWriteSuiteEndTimestamp(
                WearMeasurementPolicy.SuiteRunState.COMPLETED,
                sessionStartedAt = 100L,
                reportedAt = 100L
            )
        )
        assertTrue(
            WearMeasurementPolicy.shouldWriteSuiteEndTimestamp(
                WearMeasurementPolicy.SuiteRunState.PARTIAL,
                sessionStartedAt = 100L,
                reportedAt = 101L
            )
        )
        assertFalse(
            WearMeasurementPolicy.shouldWriteSuiteEndTimestamp(
                WearMeasurementPolicy.SuiteRunState.RUNNING,
                sessionStartedAt = 100L,
                reportedAt = 101L
            )
        )
        assertFalse(
            WearMeasurementPolicy.shouldWriteSuiteEndTimestamp(
                WearMeasurementPolicy.SuiteRunState.COMPLETED,
                sessionStartedAt = 0L,
                reportedAt = 101L
            )
        )
        assertFalse(
            WearMeasurementPolicy.shouldWriteSuiteEndTimestamp(
                WearMeasurementPolicy.SuiteRunState.COMPLETED,
                sessionStartedAt = 101L,
                reportedAt = 100L
            )
        )
        assertFalse(
            WearMeasurementPolicy.shouldWriteSuiteEndTimestamp(
                state = null,
                sessionStartedAt = 100L,
                reportedAt = 101L
            )
        )
    }

    @Test
    fun ignoresRepeatedSuiteStartWhileOneIsActive() {
        assertTrue(WearMeasurementPolicy.shouldIgnoreDuplicateSuiteStart(true))
        assertFalse(WearMeasurementPolicy.shouldIgnoreDuplicateSuiteStart(false))
    }

    @Test
    fun packageReplacementRecoversOnlyNonterminalRunningSuites() {
        assertTrue(WearMeasurementPolicy.shouldRecoverInterruptedSuite("running"))
        assertFalse(WearMeasurementPolicy.shouldRecoverInterruptedSuite("completed"))
        assertFalse(WearMeasurementPolicy.shouldRecoverInterruptedSuite("partial"))
        assertFalse(WearMeasurementPolicy.shouldRecoverInterruptedSuite("interrupted"))
        assertFalse(WearMeasurementPolicy.shouldRecoverInterruptedSuite(null))
    }

    @Test
    fun completedOldRecoveryCannotClearANewerSuiteRetryMarker() {
        assertTrue(WearMeasurementPolicy.shouldClearPendingInterruptionSync(true, "suite-a", 100L, "suite-a", 100L))
        assertFalse(WearMeasurementPolicy.shouldClearPendingInterruptionSync(true, "suite-b", 200L, "suite-a", 100L))
        assertFalse(WearMeasurementPolicy.shouldClearPendingInterruptionSync(true, "suite-a", 101L, "suite-a", 100L))
        assertFalse(WearMeasurementPolicy.shouldClearPendingInterruptionSync(false, "suite-a", 100L, "suite-a", 100L))
    }

    @Test
    fun interruptedSuiteRecoveryUsesSupportedLifecycleActionsAndAccurateReasons() {
        assertTrue(WearMeasurementPolicy.shouldRecoverInterruptedSuiteForAction(android.content.Intent.ACTION_BOOT_COMPLETED))
        assertTrue(WearMeasurementPolicy.shouldRecoverInterruptedSuiteForAction(android.content.Intent.ACTION_USER_UNLOCKED))
        assertTrue(WearMeasurementPolicy.shouldRecoverInterruptedSuiteForAction(android.content.Intent.ACTION_MY_PACKAGE_REPLACED))
        assertFalse(WearMeasurementPolicy.shouldRecoverInterruptedSuiteForAction(android.content.Intent.ACTION_BATTERY_LOW))
        val unlockMessage = WearMeasurementPolicy.interruptionMessageForAction(android.content.Intent.ACTION_USER_UNLOCKED)
        assertTrue(unlockMessage.contains("解鎖後"))
        assertFalse(unlockMessage.contains("重新啟動"))
        assertTrue(WearMeasurementPolicy.hasInterruptionSyncIdentity("suite-a", 1L))
        assertFalse(WearMeasurementPolicy.hasInterruptionSyncIdentity("", 1L))
        assertFalse(WearMeasurementPolicy.hasInterruptionSyncIdentity("suite-a", 0L))
    }

    @Test
    fun suiteStatusIsCompactAndUsesRunStateForTheWatchHeadline() {
        val message = "全感測器盤點完成：24/34 階段收到樣本（不代表品質或校準通過）；感測器目錄 26/36 通道有原始事件。佩戴合格。 光學候選通道（晶片／設定未核實）：raw details"
        assertEquals("部分完成", WearMeasurementPolicy.sensorSuiteStatusHeadline("partial"))
        assertEquals("掃描完成", WearMeasurementPolicy.sensorSuiteStatusHeadline("completed"))
        assertEquals("掃描完成，部分項目有資料；到手機查看各項結果", WearMeasurementPolicy.compactSensorSuiteStatus(message))
        assertEquals(
            "這次沒有取得量測資料，請確認佩戴後重測",
            WearMeasurementPolicy.compactSensorSuiteStatus(message.replace("26/36", "0/36"))
        )
        assertEquals(
            "掃描未完成；請檢查權限與佩戴後重試",
            WearMeasurementPolicy.compactSensorSuiteStatus("掃描啟動失敗：權限不足")
        )
        assertEquals(
            "正在收集手錶資料；完成後到手機查看結果",
            WearMeasurementPolicy.compactSensorSuiteStatus("生醫通道 1/8：已註冊；等待事件")
        )
        assertTrue(WearMeasurementPolicy.hasRecentSensorSuiteStatus(100L, 200L, 100L))
        assertFalse(WearMeasurementPolicy.hasRecentSensorSuiteStatus(0L, 200L, 100L))
        assertFalse(WearMeasurementPolicy.hasRecentSensorSuiteStatus(100L, 201L, 100L))
        assertFalse(WearMeasurementPolicy.hasRecentSensorSuiteStatus(201L, 200L, 100L))
    }

    @Test
    fun chargingSkippedSnapshotCarriesExplicitTerminalAcquisitionState() {
        val skipped = WearMeasurementPolicy.markSkippedCharging(
            RawVitalsProbe.derived(
                kind = RawVitalsProbe.Kind.PPG,
                values = emptyList(),
                sensorName = "charging-guard",
                vendor = "HealthBridge",
                reason = "略過貼腕量測"
            )
        )

        assertEquals(0, skipped.samples)
        assertEquals("skipped_charging", skipped.status)
        assertEquals("skipped_charging", skipped.registrationResult)
        assertEquals("skipped_charging", skipped.acquisitionStopReason)
        assertEquals("not_attempted", skipped.reportingMode)
        assertEquals(0, skipped.finiteValueCount)
        assertEquals(0, skipped.eventCount)
        assertEquals(0.0, skipped.sampleRateHz, 0.0)
        assertEquals(0, skipped.channels)
        assertNull(skipped.minimum)
        assertNull(skipped.maximum)
        assertNull(skipped.average)
        assertTrue(skipped.latest.isEmpty())
        assertEquals(0, skipped.waveform.size)
        assertEquals("not_measured", WearMeasurementPolicy.dataProvenance(skipped.reportingMode, skipped.status))
        assertEquals("derived_estimate", WearMeasurementPolicy.dataProvenance("derived", "derived_estimate"))
        assertEquals("hardware_event", WearMeasurementPolicy.dataProvenance("continuous", "has_data"))
    }

    @Test
    fun chargingBlocksContactCaptureButAllowsExplicitLowPowerSuitePath() {
        assertTrue(WearMeasurementPolicy.shouldBlockChargingMeasurement(isCharging = true, isSensorSuite = false))
        assertFalse(WearMeasurementPolicy.shouldBlockChargingMeasurement(isCharging = true, isSensorSuite = true))
        assertFalse(WearMeasurementPolicy.shouldBlockChargingMeasurement(isCharging = false, isSensorSuite = true))
    }

    @Test
    fun suiteStartupFailuresArePersistedAsSuiteStatusButSingleSensorFailuresAreNot() {
        val scanFailure = WearMeasurementPolicy.sensorSuiteStartupFailureMessage("權限未授權", true)
        val singleFailure = WearMeasurementPolicy.sensorSuiteStartupFailureMessage("權限未授權", false)

        assertTrue(WearMeasurementPolicy.isSensorSuiteStatusMessage(scanFailure))
        assertFalse(WearMeasurementPolicy.isSensorSuiteStatusMessage(singleFailure))
    }

    @Test
    fun suiteCompletionSurfacesHealthServicesPermissionWarnings() {
        val base = "掃描完成：其他通道已處理"

        assertEquals(base, WearMeasurementPolicy.appendSensorSuiteWarnings(base, emptyList()))
        assertTrue(
            WearMeasurementPolicy.appendSensorSuiteWarnings(
                base,
                listOf("Health Services 心率未啟動：未授予讀取心率權限")
            ).contains("未授予讀取心率權限")
        )
    }

    @Test
    fun wearGateMessagesKeepUnknownDistinctFromQualified() {
        assertTrue(WearMeasurementPolicy.wearGateMessage(true).startsWith("佩戴合格"))
        assertTrue(WearMeasurementPolicy.wearGateMessage(false).startsWith("離腕感測器回報未貼合"))
        assertTrue(WearMeasurementPolicy.wearGateMessage(false).contains("不單獨證明佩戴狀態"))
        assertTrue(WearMeasurementPolicy.wearGateMessage(null).startsWith("佩戴狀態未知"))
        assertFalse(WearMeasurementPolicy.wearGateMessage(null).contains("佩戴合格"))
    }

    @Test
    fun hrvCaptureCompletesOnlyAfterMinimumRrIntervals() {
        assertFalse(WearMeasurementPolicy.hasEnoughRrIntervalsForHrv(0))
        assertFalse(WearMeasurementPolicy.hasEnoughRrIntervalsForHrv(4))
        assertTrue(WearMeasurementPolicy.hasEnoughRrIntervalsForHrv(5))
    }

    @Test
    fun candidateCaptureIsPublishedWhenSensorEmittedAnyRawData() {
        assertTrue(WearMeasurementPolicy.shouldPublishCandidateCapture(
            snapshot(RawVitalsProbe.Kind.PPG, samples = 1, waveform = floatArrayOf(42f))
        ))
        assertTrue(WearMeasurementPolicy.shouldPublishCandidateCapture(
            snapshot(RawVitalsProbe.Kind.PPG, samples = 0).copy(eventCount = 1)
        ))
        assertFalse(WearMeasurementPolicy.shouldPublishCandidateCapture(
            snapshot(RawVitalsProbe.Kind.PPG, samples = 0, waveform = FloatArray(0)).copy(eventCount = 0)
        ))
    }

    @Test
    fun zeroFiniteBiomedicalValuesRemainZeroDespiteCallbacksOrPreview() {
        val snapshot = snapshot(RawVitalsProbe.Kind.PPG, samples = 0, waveform = floatArrayOf(Float.NaN))
            .copy(eventCount = 7)

        assertEquals(0, snapshot.samples)
        assertEquals(0, snapshot.finiteValueCount)
        assertEquals(7, snapshot.eventCount)
        assertEquals(1, snapshot.waveform.size)
    }

    @Test
    fun broadSuiteDoesNotWaitForOemPagesThatItCannotLaunchInBackground() {
        assertEquals(5_000L, WearMeasurementPolicy.broadSuiteOemProbeTimeoutMillis(RawVitalsProbe.Kind.MAX_HEART_RATE))
        assertEquals(5_000L, WearMeasurementPolicy.broadSuiteOemProbeTimeoutMillis(RawVitalsProbe.Kind.MI_SPO2))
        assertNull(WearMeasurementPolicy.broadSuiteOemProbeTimeoutMillis(RawVitalsProbe.Kind.PPG))
        assertNull(WearMeasurementPolicy.broadSuiteOemProbeTimeoutMillis(RawVitalsProbe.Kind.HEART_RATE))
    }

    @Test
    fun suiteCompletionSeparatesRawEventCountsFromMeasurementValidity() {
        val message = WearMeasurementPolicy.sensorSuiteCompletionMessage(
            phasesWithSamples = 24,
            phasesTested = 34,
            channelsWithRawEvents = 26,
            totalChannels = 36,
            wearStatus = "佩戴合格",
            opticalAdvice = "原始光學資料待驗證",
            temperatureNote = "體表溫度僅供趨勢參考"
        )

        assertTrue(message.contains("24/34 階段收到樣本"))
        assertTrue(message.contains("不代表品質或校準通過"))
        assertTrue(message.contains("26/36 通道有原始事件"))
        assertFalse(message.contains("階段有資料；目錄"))
    }

    @Test
    fun suiteCompletionListsOnlyChannelsWithoutFinalResults() {
        val incomplete = WearMeasurementPolicy.unreportedSuiteKinds(
            setOf(RawVitalsProbe.Kind.HEART_RATE.wireName, RawVitalsProbe.Kind.ECG.wireName)
        )
        val summary = WearMeasurementPolicy.unreportedChannelSummary(incomplete)

        assertEquals(RawVitalsProbe.Kind.entries.size - 2, incomplete.size)
        assertFalse(incomplete.any { it == RawVitalsProbe.Kind.HEART_RATE || it == RawVitalsProbe.Kind.ECG })
        assertTrue(summary.contains("本輪未產生終態結果"))
        assertTrue(WearMeasurementPolicy.unreportedChannelSummary(emptyList()).isEmpty())
    }

    @Test
    fun runCompletionDependsOnTerminalChannelCoverageNotRawSampleCount() {
        assertEquals(
            WearMeasurementPolicy.SuiteRunState.COMPLETED,
            WearMeasurementPolicy.completedSuiteState(emptyList())
        )
        assertEquals(
            WearMeasurementPolicy.SuiteRunState.PARTIAL,
            WearMeasurementPolicy.completedSuiteState(listOf(RawVitalsProbe.Kind.HEART_RATE))
        )
        assertFalse(WearMeasurementPolicy.SuiteRunState.RUNNING.terminal)
        assertFalse(WearMeasurementPolicy.SuiteRunState.RUNNING.successful)
    }

    @Test
    fun biozRespirationAliasDoesNotForceEverySuiteToPartialButRealMissingChannelsDo() {
        assertEquals(
            WearMeasurementPolicy.SuiteRunState.COMPLETED,
            WearMeasurementPolicy.completedSuiteState(listOf(RawVitalsProbe.Kind.BIOZ_RESPIRATION))
        )
        assertEquals(
            WearMeasurementPolicy.SuiteRunState.PARTIAL,
            WearMeasurementPolicy.completedSuiteState(
                listOf(RawVitalsProbe.Kind.BIOZ_RESPIRATION, RawVitalsProbe.Kind.ECG)
            )
        )
    }

    @Test
    fun chargingSafeSuiteOnlyReportsMissingResultsForChannelsItAttemptedOrExplicitlySkipped() {
        val expected = listOf(
            RawVitalsProbe.Kind.PRESSURE,
            RawVitalsProbe.Kind.LIGHT,
            RawVitalsProbe.Kind.ACCELEROMETER,
            RawVitalsProbe.Kind.MAGNETOMETER,
            RawVitalsProbe.Kind.ROTATION_VECTOR,
            RawVitalsProbe.Kind.STEP_COUNTER,
            RawVitalsProbe.Kind.PPG,
            RawVitalsProbe.Kind.ECG
        )
        val completed = expected.mapTo(mutableSetOf()) { it.wireName }
        val completeResult = WearMeasurementPolicy.unreportedSuiteKinds(completed, expected)
        val partialResult = WearMeasurementPolicy.unreportedSuiteKinds(
            completedKinds = completed - RawVitalsProbe.Kind.PRESSURE.wireName,
            expectedKinds = expected
        )

        assertTrue(completeResult.isEmpty())
        assertEquals(listOf(RawVitalsProbe.Kind.PRESSURE), partialResult)
        assertFalse(partialResult.any { it == RawVitalsProbe.Kind.BIOZ_RESPIRATION })
    }

    @Test
    fun biozRespirationIsExplainedAsNotIndependentlyMeasuredWithoutClaimingFailure() {
        val summary = WearMeasurementPolicy.unreportedChannelSummary(
            listOf(RawVitalsProbe.Kind.BIOZ_RESPIRATION)
        )

        assertTrue(summary.contains("沒有可區分的呼吸採集路徑"))
        assertTrue(summary.contains("BioZ 原始資料不等於呼吸率"))
        assertTrue(summary.contains("裝置級呼吸推導／驗證條件"))
        assertFalse(summary.contains("未產生終態結果"))
        assertFalse(summary.contains("呼吸率 0"))
    }

    @Test
    fun biozRespirationDoesNotHideOtherUnreportedSuiteKinds() {
        val summary = WearMeasurementPolicy.unreportedChannelSummary(
            listOf(RawVitalsProbe.Kind.BIOZ_RESPIRATION, RawVitalsProbe.Kind.ECG)
        )

        assertTrue(summary.contains("未產生終態結果 1 項：ECG"))
        assertTrue(summary.contains("沒有可區分的呼吸採集路徑"))
    }

    @Test
    fun broadSuiteOnlyBuildsIntermediateSnapshotsForOpticalEarlyCompletion() {
        assertTrue(WearMeasurementPolicy.shouldEmitIntermediateSuiteSnapshots(RawVitalsProbe.Kind.PPG))
        assertTrue(WearMeasurementPolicy.shouldEmitIntermediateSuiteSnapshots(RawVitalsProbe.Kind.MAX_HEART_RATE))
        assertTrue(WearMeasurementPolicy.shouldEmitIntermediateSuiteSnapshots(RawVitalsProbe.Kind.LIFE_Q_OTT))
        assertFalse(WearMeasurementPolicy.shouldEmitIntermediateSuiteSnapshots(RawVitalsProbe.Kind.ECG))
        assertFalse(WearMeasurementPolicy.shouldEmitIntermediateSuiteSnapshots(RawVitalsProbe.Kind.BIOZ))
        assertFalse(WearMeasurementPolicy.shouldEmitIntermediateSuiteSnapshots(RawVitalsProbe.Kind.SPO2))
        assertFalse(WearMeasurementPolicy.shouldEmitIntermediateSuiteSnapshots(RawVitalsProbe.Kind.THERMOMETER))
    }

    @Test
    fun broadSuiteRequiresUsablePayloadBeforeEarlyFinishing() {
        assertFalse(WearMeasurementPolicy.shouldEarlyFinishBroadSuiteProbe(
            RawVitalsProbe.Kind.ECG, 1, List(10) { it.toFloat() }, 100
        ))
        assertFalse(WearMeasurementPolicy.shouldEarlyFinishBroadSuiteProbe(
            RawVitalsProbe.Kind.PPG, 8, List(40) { (it + 1).toFloat() }, 5_000
        ))
        assertTrue(WearMeasurementPolicy.shouldEarlyFinishBroadSuiteProbe(
            RawVitalsProbe.Kind.PPG, 8, List(ChipCalibrationProfiles.MIN_VALID_OPTICAL_SAMPLES) { (it + 1).toFloat() }, 5_000
        ))
        assertFalse(WearMeasurementPolicy.shouldEarlyFinishBroadSuiteProbe(
            RawVitalsProbe.Kind.PPG,
            8,
            List(ChipCalibrationProfiles.MIN_VALID_OPTICAL_SAMPLES) { ChipCalibrationProfiles.OPTICAL_FILL },
            5_000
        ))
    }

    @Test
    fun unknownPpgAdcRateDoesNotBlockQualityPassedRawCaptureCompletion() {
        val waveform = List(128) { index -> 20_000f + kotlin.math.sin(index / 5.0).toFloat() * 120f }

        assertEquals(0.0, RawVitalsProbe.reportedSampleRateHz(RawVitalsProbe.Kind.PPG, null), 0.0)
        assertTrue(WearMeasurementPolicy.shouldFinishRawPpgCapture(waveform.size, waveform))
        assertFalse(WearMeasurementPolicy.shouldFinishRawPpgCapture(63, waveform))
        assertFalse(WearMeasurementPolicy.shouldFinishRawPpgCapture(128, List(128) { ChipCalibrationProfiles.OPTICAL_FILL }))
        assertFalse(WearMeasurementPolicy.hasInvalidRawPpgSignal(waveform.size, waveform))
        assertTrue(WearMeasurementPolicy.hasInvalidRawPpgSignal(128, List(128) { ChipCalibrationProfiles.OPTICAL_FILL }))
    }

    @Test
    fun broadSuiteEcgDiscoveryIsBriefAndBounded() {
        assertEquals(5_000L, WearMeasurementPolicy.ECG_SUITE_DISCOVERY_TIMEOUT_MILLIS)
    }

    @Test
    fun manualOemHintIsOnlyShownForAnAvailableButSilentOemChannel() {
        val silentOem = snapshot(RawVitalsProbe.Kind.MAX_HEART_RATE, samples = 0, status = "needs_oem_mode")
        val absentOem = silentOem.copy(status = "missing")
        val dataReceived = silentOem.copy(status = "research_optical", samples = 10, eventCount = 2)
        assertTrue(WearMeasurementPolicy.needsManualOemModeHint(RawVitalsProbe.Kind.MAX_HEART_RATE, silentOem))
        assertFalse(WearMeasurementPolicy.needsManualOemModeHint(RawVitalsProbe.Kind.MAX_HEART_RATE, absentOem))
        assertFalse(WearMeasurementPolicy.needsManualOemModeHint(RawVitalsProbe.Kind.MAX_HEART_RATE, dataReceived))
        assertFalse(WearMeasurementPolicy.needsManualOemModeHint(RawVitalsProbe.Kind.HEART_RATE, silentOem))
    }

    @Test
    fun supplementalHealthServicesHeartRateMustBePlausibleAndFinite() {
        assertEquals(72.0, WearMeasurementPolicy.validSupplementalBpm(72.0)!!, 0.0)
        assertNull(WearMeasurementPolicy.validSupplementalBpm(29.0))
        assertNull(WearMeasurementPolicy.validSupplementalBpm(221.0))
        assertNull(WearMeasurementPolicy.validSupplementalBpm(Double.NaN))
    }

    @Test
    fun rawOpticalCountsNeverBecomeHeartRateThroughAverageFallback() {
        val snapshot = snapshot(RawVitalsProbe.Kind.MAX_HEART_RATE, average = 87.0, latest = listOf(87.0))
        assertNull(WearMeasurementPolicy.directHeartRateBpm(snapshot))
    }

    @Test
    fun directHeartRateSensorCanProvidePlausibleBpm() {
        val snapshot = snapshot(RawVitalsProbe.Kind.HEART_RATE, average = 72.0, latest = listOf(72.0))
        assertEquals(72.0, WearMeasurementPolicy.directHeartRateBpm(snapshot)!!, 0.0)
    }

    @Test
    fun offWristOrInvalidDirectHeartRateIsNotAccepted() {
        assertNull(WearMeasurementPolicy.directHeartRateBpm(
            snapshot(RawVitalsProbe.Kind.HEART_RATE, average = 72.0, status = "off_wrist")
        ))
        assertNull(WearMeasurementPolicy.directHeartRateBpm(
            snapshot(RawVitalsProbe.Kind.HEART_RATE, average = 250.0)
        ))
    }

    @Test
    fun ppgRawCaptureCompletionIsDistinctFromPhysiologicalValidity() {
        val raw = snapshot(RawVitalsProbe.Kind.PPG, samples = 64, waveform = FloatArray(64) { (it + 1).toFloat() })
        assertTrue(WearMeasurementPolicy.hasOpticalRawCapture(raw, 64))
        assertFalse(WearMeasurementPolicy.hasOpticalRawCapture(raw, 65))
        assertFalse(WearMeasurementPolicy.hasOpticalRawCapture(
            snapshot(RawVitalsProbe.Kind.PPG, samples = 64, waveform = FloatArray(64) { ChipCalibrationProfiles.OPTICAL_FILL }), 64
        ))
        assertFalse(WearMeasurementPolicy.hasOpticalRawCapture(
            snapshot(RawVitalsProbe.Kind.HEART_RATE, samples = 64, waveform = FloatArray(64)), 64
        ))
    }

    @Test
    fun opticalFormulasRequireVerifiedClockAndChannelMap() {
        val waveform = List(256) { index -> (10_000 + (index % 25) * 10).toFloat() }
        val noClock = ClinicalSensorFormulas.opticalFromWaveform(waveform, 0.0, channelMappingVerified = true)
        assertNull(noClock.pulseRateBpm)
        assertNull(noClock.perfusionIndexPercent)
        assertTrue(noClock.note.contains("取樣時基"))

        val noChannelMap = ClinicalSensorFormulas.opticalFromWaveform(waveform, 50.0)
        assertNull(noChannelMap.pulseRateBpm)
        assertNull(noChannelMap.perfusionIndexPercent)
        assertTrue(noChannelMap.note.contains("通道映射"))
    }

    @Test
    fun ppgConclusionLabelsUncalibratedRawDataWithoutInventingPulse() {
        val conclusion = WearHealthConclusion.forPpg(128, null)
        assertTrue(conclusion.contains("沒有取得可顯示的脈搏讀值"))
        assertFalse(conclusion.contains("原始波形"))
    }

    @Test
    fun pressureConclusionDoesNotClaimRelativeAltitudeOrClimb() {
        val conclusion = WearHealthConclusion.forPressure(1000.0, 110.0)
        assertTrue(conclusion.contains("標準大氣公式估算"))
        assertTrue(conclusion.contains("非實際海拔／爬升"))
        assertFalse(conclusion.contains("相對高度趨勢可用"))
    }

    @Test
    fun watchSnapshotSummaryExplainsTheResultInsteadOfEventCounts() {
        val ecg = snapshot(RawVitalsProbe.Kind.ECG, samples = 216).summary()
        assertEquals("心電圖：已收到訊號，尚無可解讀的心律結果", ecg)
        assertFalse(ecg.contains("216"))
        assertEquals(
            "血氧：已收到訊號，尚無可用的血氧百分比",
            snapshot(RawVitalsProbe.Kind.SPO2).summary()
        )
        assertEquals(
            "心率：72 次／分鐘",
            snapshot(RawVitalsProbe.Kind.HEART_RATE, average = 72.0).summary()
        )
    }

    private fun snapshot(
        kind: RawVitalsProbe.Kind,
        samples: Int = 64,
        average: Double? = null,
        latest: List<Double> = emptyList(),
        waveform: FloatArray = FloatArray(0),
        status: String = "has_data"
    ) = RawVitalsProbe.Snapshot(
        kind = kind,
        sensorName = "test",
        vendor = "test",
        reportingMode = "continuous",
        samples = samples,
        eventCount = samples,
        sampleRateHz = 0.0,
        channels = 1,
        accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
        minimum = null,
        maximum = null,
        average = average,
        latest = latest,
        waveform = waveform,
        worn = null,
        status = status,
        reason = "test"
    )
}
