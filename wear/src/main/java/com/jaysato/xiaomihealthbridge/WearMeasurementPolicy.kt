package com.jaysato.xiaomihealthbridge

import android.hardware.SensorManager

internal object WearMeasurementPolicy {
    fun hasWornBiozRawCapture(worn: Boolean?, samples: Int, minimumSamples: Int): Boolean =
        worn == true && minimumSamples > 0 && samples >= minimumSamples

    enum class SuiteRunState(val wireValue: String, val terminal: Boolean, val successful: Boolean) {
        RUNNING("running", false, false),
        COMPLETED("completed", true, true),
        PARTIAL("partial", true, false),
        CANCELLED("cancelled", true, false),
        TIMED_OUT("timed_out", true, false),
        INTERRUPTED("interrupted", true, false),
        FAILED("failed", true, false)
    }

    fun completedSuiteState(unreportedKinds: Collection<RawVitalsProbe.Kind>): SuiteRunState =
        if (unreportedKinds.any { it != RawVitalsProbe.Kind.BIOZ_RESPIRATION }) {
            SuiteRunState.PARTIAL
        } else {
            SuiteRunState.COMPLETED
        }

    fun shouldIgnoreDuplicateSuiteStart(hasActiveSuite: Boolean): Boolean = hasActiveSuite

    fun hasLiveSuiteWindow(runState: String, startedAt: Long, now: Long, maximumDuration: Long): Boolean =
        runState == SuiteRunState.RUNNING.wireValue && startedAt > 0L &&
            maximumDuration > 0L && now >= startedAt && now - startedAt <= maximumDuration

    fun shouldRecoverInterruptedSuite(runState: String?): Boolean =
        runState == SuiteRunState.RUNNING.wireValue

    fun hasInterruptionSyncIdentity(suiteId: String?, sessionStartedAt: Long): Boolean =
        !suiteId.isNullOrBlank() && sessionStartedAt > 0L

    fun shouldClearPendingInterruptionSync(
        pending: Boolean,
        storedSuiteId: String?,
        storedTimestamp: Long,
        completedSuiteId: String,
        completedTimestamp: Long
    ): Boolean = pending && storedSuiteId == completedSuiteId && storedTimestamp == completedTimestamp

    fun shouldRecoverInterruptedSuiteForAction(action: String): Boolean = action in setOf(
        android.content.Intent.ACTION_BOOT_COMPLETED,
        android.content.Intent.ACTION_USER_UNLOCKED,
        android.content.Intent.ACTION_MY_PACKAGE_REPLACED
    )

    fun interruptionMessageForAction(action: String): String = when (action) {
        android.content.Intent.ACTION_MY_PACKAGE_REPLACED -> "掃描因 App 更新中斷；保留已收到的部分結果。"
        android.content.Intent.ACTION_BOOT_COMPLETED -> "掃描因裝置重新啟動而中斷；保留已收到的部分結果。"
        else -> "解鎖後發現掃描已中斷；保留已收到的部分結果。"
    }

    fun reportSuccess(state: SuiteRunState?, fallback: Boolean): Boolean =
        state?.successful ?: fallback

    fun shouldWriteSuiteEndTimestamp(
        state: SuiteRunState?,
        sessionStartedAt: Long,
        reportedAt: Long
    ): Boolean =
        state?.terminal == true && sessionStartedAt > 0L && reportedAt >= sessionStartedAt

    fun markSkippedCharging(snapshot: RawVitalsProbe.Snapshot): RawVitalsProbe.Snapshot =
        snapshot.copy(
            reportingMode = "not_attempted",
            samples = 0,
            finiteValueCount = 0,
            eventCount = 0,
            sampleRateHz = 0.0,
            channels = 0,
            accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
            minimum = null,
            maximum = null,
            average = null,
            latest = emptyList(),
            waveform = floatArrayOf(),
            eventTimestampsNs = longArrayOf(),
            eventCallbackElapsedRealtimeNs = longArrayOf(),
            eventSampleCounts = intArrayOf(),
            eventRawValueRows = emptyList(),
            halAdditionalInfoSupported = false,
            halSamplePeriodSeconds = null,
            halSampleJitterSeconds = null,
            candidateSummary = "",
            candidateCount = 0,
            status = "skipped_charging",
            registrationResult = "skipped_charging",
            acquisitionStopReason = "skipped_charging"
        )

    fun dataProvenance(reportingMode: String, status: String): String = when {
        status == "skipped_charging" -> "not_measured"
        reportingMode == "derived" -> "derived_estimate"
        else -> "hardware_event"
    }

    fun shouldBlockChargingMeasurement(isCharging: Boolean, isSensorSuite: Boolean): Boolean =
        isCharging && !isSensorSuite

    fun isSensorSuiteStatusMessage(message: String): Boolean =
        message.contains("全感測器掃描") || message.contains("感測器盤點") ||
            message.contains("充電中低耗電盤點")

    fun sensorSuiteStatusHeadline(runState: String): String = when (runState) {
        "running" -> "掃描中"
        "completed" -> "掃描完成"
        "partial" -> "部分完成"
        "cancelled" -> "掃描已取消"
        "timed_out" -> "掃描逾時"
        "interrupted" -> "掃描中斷"
        "failed" -> "掃描失敗"
        else -> "掃描狀態"
    }

    fun hasRecentSensorSuiteStatus(timestamp: Long, now: Long, freshnessWindowMillis: Long): Boolean =
        timestamp > 0L && now >= timestamp && now - timestamp <= freshnessWindowMillis

    fun compactSensorSuiteStatus(message: String): String {
        val stageCount = Regex("(\\d+/\\d+) 階段收到樣本").find(message)?.groupValues?.get(1)
        val channelCount = Regex("感測器目錄 (\\d+/\\d+) 通道有原始事件").find(message)?.groupValues?.get(1)
        return if (stageCount != null && channelCount != null) {
            if (channelCount.substringBefore('/').toIntOrNull() == 0) {
                "這次沒有取得量測資料，請確認佩戴後重測"
            } else {
                "掃描完成，部分項目有資料；到手機查看各項結果"
            }
        } else when {
            message.contains("充電中") -> "手錶正在充電，貼腕量測未執行；請拔除充電器後再測"
            message.contains("失敗") || message.contains("權限不足") -> "掃描未完成；請檢查權限與佩戴後重試"
            message.contains("逾時") || message.contains("中斷") -> "掃描未完成；可重新開始"
            message.contains("完成") -> "掃描完成；請到手機查看各項結果"
            else -> "正在收集手錶資料；完成後到手機查看結果"
        }
    }

    fun sensorSuiteStartupFailureMessage(reason: String, isSensorSuite: Boolean): String =
        "${if (isSensorSuite) "全感測器掃描未啟動" else "感測未啟動"}：$reason"

    fun appendSensorSuiteWarnings(message: String, warnings: List<String>): String =
        if (warnings.isEmpty()) message else "$message 注意：${warnings.joinToString("；")}。"

    const val ECG_SUITE_DISCOVERY_TIMEOUT_MILLIS = 5_000L

    fun hasEnoughRrIntervalsForHrv(rrCount: Int): Boolean = rrCount >= MIN_HRV_RR_INTERVALS

    fun canPublishResearchStressScore(worn: Boolean?, opticalValid: Boolean, rrCount: Int): Boolean =
        worn == true && opticalValid && hasEnoughRrIntervalsForHrv(rrCount)

    fun shouldFinishRawPpgCapture(samples: Int, waveform: List<Float>): Boolean =
        samples >= MIN_RAW_PPG_CAPTURE_SAMPLES &&
            ChipCalibrationProfiles.assessOpticalWaveform(waveform).valid

    fun hasInvalidRawPpgSignal(samples: Int, waveform: List<Float>): Boolean =
        samples > 0 && !ChipCalibrationProfiles.assessOpticalWaveform(waveform).valid

    fun wearGateMessage(worn: Boolean?): String = when (worn) {
        true -> "佩戴合格，開始動作觸發掃描；請抬腕並輕輕晃動手錶"
        false -> "離腕感測器回報未貼合；此訊號不單獨證明佩戴狀態，仍採集原始資料但不進行生理推算"
        null -> "佩戴狀態未知；仍嘗試有界原始採集，但不進行生理校正"
    }

    fun shouldPublishCandidateCapture(snapshot: RawVitalsProbe.Snapshot): Boolean =
        snapshot.samples > 0 || snapshot.eventCount > 0 || snapshot.waveform.isNotEmpty()

    fun broadSuiteOemProbeTimeoutMillis(kind: RawVitalsProbe.Kind): Long? =
        if (kind.needsOemMode && kind != RawVitalsProbe.Kind.PPG) OEM_SUITE_PROBE_TIMEOUT_MILLIS else null

    fun shouldEmitIntermediateSuiteSnapshots(kind: RawVitalsProbe.Kind): Boolean =
        kind in INTERMEDIATE_SNAPSHOT_KINDS

    fun shouldEarlyFinishBroadSuiteProbe(
        kind: RawVitalsProbe.Kind,
        eventCount: Int,
        waveform: List<Float>,
        elapsedMillis: Long
    ): Boolean {
        if (kind !in EARLY_FINISH_OPTICAL_KINDS) return false
        if (kind == RawVitalsProbe.Kind.PPG && !ChipCalibrationProfiles.assessOpticalWaveform(waveform).valid) {
            return false
        }
        return OpticalCaptureReadiness.hasCapturedPayload(eventCount, waveform, elapsedMillis)
    }

    fun needsManualOemModeHint(kind: RawVitalsProbe.Kind, snapshot: RawVitalsProbe.Snapshot): Boolean =
        kind.needsOemMode && snapshot.status == "needs_oem_mode" &&
            snapshot.samples == 0 && snapshot.eventCount == 0

    fun validSupplementalBpm(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it in 30.0..220.0 }

    fun hasRawEvents(eventCount: Int, reportingMode: String): Boolean =
        eventCount > 0 && reportingMode != "derived"

    fun directHeartRateBpm(snapshot: RawVitalsProbe.Snapshot): Double? {
        if (snapshot.kind != RawVitalsProbe.Kind.HEART_RATE ||
            snapshot.status !in setOf("has_data", "worn", "derived_estimate")) return null
        return (snapshot.average ?: snapshot.latest.firstOrNull())
            ?.takeIf { it.isFinite() && it in 30.0..220.0 }
    }

    fun sensorSuiteCompletionMessage(
        phasesWithSamples: Int,
        phasesTested: Int,
        channelsWithRawEvents: Int,
        totalChannels: Int,
        wearStatus: String,
        opticalAdvice: String?,
        temperatureNote: String,
        unreportedChannels: List<RawVitalsProbe.Kind> = emptyList()
    ): String = buildString {
        append("全感測器盤點完成：$phasesWithSamples/$phasesTested 階段收到樣本（不代表品質或校準通過）；")
        append("感測器目錄 $channelsWithRawEvents/$totalChannels 通道有原始事件。請依各通道狀態判讀可用數值。$wearStatus。")
        opticalAdvice?.let { append(" 光學候選通道（晶片／設定未核實）：$it。") }
        append(" $temperatureNote")
        append(unreportedChannelSummary(unreportedChannels))
    }

    fun unreportedSuiteKinds(
        completedKinds: Set<String>,
        expectedKinds: Collection<RawVitalsProbe.Kind> = RawVitalsProbe.Kind.entries
    ): List<RawVitalsProbe.Kind> =
        expectedKinds.filterNot { it.wireName in completedKinds }

    fun unreportedChannelSummary(kinds: List<RawVitalsProbe.Kind>): String {
        if (kinds.isEmpty()) return ""
        val respirationIsNotIndependent = RawVitalsProbe.Kind.BIOZ_RESPIRATION in kinds
        val unreported = kinds.filterNot { it == RawVitalsProbe.Kind.BIOZ_RESPIRATION }
        return buildString {
            if (unreported.isNotEmpty()) {
                append(" 本輪未產生終態結果 ${unreported.size} 項：${unreported.joinToString("、") { it.label }}。")
            }
            if (respirationIsNotIndependent) {
                append(" 呼吸率未獨立量測：目前沒有可區分的呼吸採集路徑；雖與 BioZ 共用公開原始通道，BioZ 原始資料不等於呼吸率。缺少可驗證的裝置級呼吸推導／驗證條件，因此不輸出呼吸率。")
            }
        }
    }

    fun hasOpticalRawCapture(snapshot: RawVitalsProbe.Snapshot, minSamples: Int): Boolean =
        snapshot.kind in setOf(
            RawVitalsProbe.Kind.PPG,
            RawVitalsProbe.Kind.MAX_HEART_RATE,
            RawVitalsProbe.Kind.LIFE_Q_OTT
        ) && snapshot.samples >= minSamples &&
            ChipCalibrationProfiles.assessOpticalWaveform(snapshot.waveform.toList()).valid

    private const val MIN_HRV_RR_INTERVALS = 5
    private const val MIN_RAW_PPG_CAPTURE_SAMPLES = 64
    private const val OEM_SUITE_PROBE_TIMEOUT_MILLIS = 5_000L
    private val EARLY_FINISH_OPTICAL_KINDS = setOf(
        RawVitalsProbe.Kind.PPG,
        RawVitalsProbe.Kind.MAX_HEART_RATE,
        RawVitalsProbe.Kind.LIFE_Q_OTT
    )
    private val INTERMEDIATE_SNAPSHOT_KINDS = EARLY_FINISH_OPTICAL_KINDS
}

internal object WearSensorSuiteCallbackPolicy {
    fun accepts(callbackToken: Int, activeToken: Int?, currentToken: Int): Boolean =
        activeToken == callbackToken && currentToken == callbackToken
}
