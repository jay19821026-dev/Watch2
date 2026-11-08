package com.jaysato.xiaomihealthbridge

internal object UserFacingMeasurementOutcome {
    enum class Kind {
        USABLE_RESULT,
        RAW_CAPTURED,
        QUALITY_INSUFFICIENT,
        STALE,
        SYNC_FAILED,
        ACTION_REQUIRED,
        UNSUPPORTED,
        NO_DATA
    }

    data class Outcome(
        val kind: Kind,
        val label: String,
        val conclusion: String,
        val nextAction: String
    )

    fun from(
        state: SensorDisplayState,
        metricKind: String,
        sourceStatus: String,
        hasSyncFailure: Boolean = false
    ): Outcome {
        if (hasSyncFailure) {
            return Outcome(
                Kind.SYNC_FAILED,
                "尚未同步到手機",
                "這次手錶資料尚未完整送到手機",
                "請保持兩台裝置連線後重試同步"
            )
        }
        return when (state) {
            SensorDisplayState.AVAILABLE -> Outcome(
                Kind.USABLE_RESULT,
                "可用結果",
                "已取得可顯示的量測結果",
                "可查看時間與近期趨勢"
            )
            SensorDisplayState.SIGNAL_VALID_RESEARCH -> Outcome(
                Kind.RAW_CAPTURED,
                capturedSignalLabel(metricKind),
                rawOnlyConclusion(metricKind),
                retryAction(metricKind)
            )
            SensorDisplayState.DERIVED_ESTIMATE -> Outcome(
                Kind.RAW_CAPTURED,
                "估算結果",
                "這是根據其他紀錄估算的結果",
                "可查看計算來源與近期變化"
            )
            SensorDisplayState.RAW_ONLY,
            SensorDisplayState.CALIBRATION_MISSING -> Outcome(
                Kind.RAW_CAPTURED,
                capturedSignalLabel(metricKind),
                rawOnlyConclusion(metricKind),
                retryAction(metricKind)
            )
            SensorDisplayState.QUALITY_REJECTED -> Outcome(
                Kind.QUALITY_INSUFFICIENT,
                "訊號不清楚",
                "這次量測無法產生可靠讀值",
                retryAction(metricKind)
            )
            SensorDisplayState.STALE,
            SensorDisplayState.FUTURE_TIMESTAMP,
            SensorDisplayState.TIMESTAMP_UNKNOWN -> Outcome(
                Kind.STALE,
                if (state == SensorDisplayState.STALE) "這是較早的紀錄" else "時間無法確認",
                if (state == SensorDisplayState.STALE) "最近一次量測已過期" else "這筆紀錄缺少可確認的量測時間",
                "在手錶重新量測"
            )
            SensorDisplayState.CONDITION_NOT_MET -> if (sourceStatus in setOf("needs_oem", "needs_oem_mode")) {
                Outcome(
                    Kind.UNSUPPORTED,
                    "功能不支援",
                    "目前公開介面不支援這項量測",
                    "可改用目前已支援的量測功能"
                )
            } else {
                Outcome(
                    Kind.ACTION_REQUIRED,
                    "需要操作",
                    conditionConclusion(sourceStatus),
                    conditionAction(sourceStatus, metricKind)
                )
            }
            SensorDisplayState.NOT_MEASURED -> Outcome(
                Kind.NO_DATA,
                "尚無資料",
                "目前尚未取得這項量測",
                retryAction(metricKind)
            )
        }
    }

    private fun capturedSignalLabel(metricKind: String): String = when (metricKind) {
        "ecg" -> "已收到心電訊號"
        "ppg", "max_hr", "lifeq_ott" -> "已收到脈搏訊號"
        "spo2_raw", "mi_spo2" -> "已收到血氧訊號"
        "bioz", "bioz_respiration" -> "已收到阻抗原始訊號"
        "body_temperature_raw", "thermometer" -> "已收到溫度訊號"
        else -> "已收到手錶訊號"
    }

    private fun rawOnlyConclusion(metricKind: String): String = when (metricKind) {
        "ecg" -> "尚無可解讀的心律結果"
        "ppg", "max_hr", "lifeq_ott" -> "尚無可用的脈率或心率讀值"
        "spo2_raw", "mi_spo2" -> "尚無可用的血氧百分比"
        "bioz", "bioz_respiration" -> "尚無可用的阻抗量測結果"
        "body_temperature_raw", "thermometer" -> "尚無可用的體溫讀值"
        else -> "尚無可用的健康讀值"
    }

    private fun retryAction(metricKind: String): String = when (metricKind) {
        "ecg" -> "請戴緊手錶、保持靜止並依畫面接觸電極後重測"
        "ppg", "spo2_raw", "mi_spo2", "max_hr", "lifeq_ott" ->
            "請戴緊手錶並保持手腕靜止後重測"
        else -> "請確認佩戴與量測條件後重測"
    }

    private fun conditionConclusion(sourceStatus: String): String = when (sourceStatus) {
        "skipped_charging" -> "充電中未執行貼腕感測"
        "skipped_off_wrist", "off_wrist", "needs_contact" -> "尚未確認手錶貼合皮膚"
        "needs_motion" -> "這項量測需要指定動作"
        else -> "目前量測條件尚未完成"
    }

    private fun conditionAction(sourceStatus: String, metricKind: String): String = when (sourceStatus) {
        "skipped_charging" -> "請離開充電器、正確佩戴後再測"
        "skipped_off_wrist", "off_wrist", "needs_contact" -> "請戴緊手錶並確認感測器貼合後再測"
        "needs_motion" -> "請依畫面完成指定動作後再測"
        else -> retryAction(metricKind)
    }
}
