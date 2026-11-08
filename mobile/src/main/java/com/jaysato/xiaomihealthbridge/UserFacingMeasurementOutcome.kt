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
                "同步失敗",
                "手錶資料尚未完整送到手機",
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
                "研究資料",
                "訊號品質已通過工程檢查，但不是已驗證的生理結果",
                "可查看研究趨勢；不要當作疾病診斷"
            )
            SensorDisplayState.DERIVED_ESTIMATE -> Outcome(
                Kind.RAW_CAPTURED,
                "推估資料",
                "這是由其他資料推估的結果，不是此感測器實測值",
                "可查看來源與方法；不要當作實測或診斷"
            )
            SensorDisplayState.RAW_ONLY,
            SensorDisplayState.CALIBRATION_MISSING -> Outcome(
                Kind.RAW_CAPTURED,
                "已取得原始資料",
                rawOnlyConclusion(metricKind),
                "原始資料已保存；可重測或查看技術細節"
            )
            SensorDisplayState.QUALITY_REJECTED -> Outcome(
                Kind.QUALITY_INSUFFICIENT,
                "品質不足",
                "本次訊號品質不足，無法產生可信結果",
                retryAction(metricKind)
            )
            SensorDisplayState.STALE,
            SensorDisplayState.FUTURE_TIMESTAMP,
            SensorDisplayState.TIMESTAMP_UNKNOWN -> Outcome(
                Kind.STALE,
                "請重新量測",
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

    private fun rawOnlyConclusion(metricKind: String): String = when (metricKind) {
        "ecg" -> "已保存 ECG 原始資料，但目前不能判定心律"
        "ppg", "max_hr", "lifeq_ott" -> "已保存光學原始資料，但目前不能換算脈率"
        "spo2_raw", "mi_spo2" -> "已保存血氧原始資料，但目前不能換算百分比"
        "bioz", "bioz_respiration" -> "已保存 BioZ 原始資料，但目前不能換算身體組成"
        "body_temperature_raw", "thermometer" -> "已保存溫度通道原始資料，但目前不能當作體溫"
        else -> "已保存原始資料，但目前不能換算生理結果"
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
