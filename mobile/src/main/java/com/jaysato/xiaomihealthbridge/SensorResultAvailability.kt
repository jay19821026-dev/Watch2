package com.jaysato.xiaomihealthbridge

internal enum class SensorResultAvailability(val label: String, val detail: String) {
    RAW_CAPTURED("已取得原始資料", "資料已保存，尚未達到可信計算條件"),
    QUALITY_INSUFFICIENT("品質不足", "已收到資料，但本次訊號品質不足，請重新量測"),
    RESULT_AVAILABLE("可用結果", "本次資料已通過目前的計算條件"),
    SYNC_FAILED("同步失敗", "手錶資料尚未完整保存到手機，請確認連線後重試");

    companion object {
        fun classify(
            hasRawEvidence: Boolean,
            resultEligible: Boolean,
            qualityRejected: Boolean,
            syncFailed: Boolean
        ): SensorResultAvailability? = when {
            syncFailed -> SYNC_FAILED
            resultEligible -> RESULT_AVAILABLE
            qualityRejected && hasRawEvidence -> QUALITY_INSUFFICIENT
            hasRawEvidence -> RAW_CAPTURED
            else -> null
        }
    }
}
