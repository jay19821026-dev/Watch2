package com.jaysato.xiaomihealthbridge

internal enum class SensorResultAvailability(val label: String, val detail: String) {
    RAW_CAPTURED("已收到訊號", "手錶訊號已保存，目前沒有可顯示的讀值"),
    QUALITY_INSUFFICIENT("訊號不穩", "這次訊號不穩定，請保持手錶貼合後重測"),
    RESULT_AVAILABLE("已有結果", "查看本次讀值與說明"),
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
