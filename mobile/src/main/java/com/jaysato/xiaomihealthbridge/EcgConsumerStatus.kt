package com.jaysato.xiaomihealthbridge

object EcgConsumerStatus {
    enum class State { NORMAL, ABNORMAL, IDLE, MEASURING, UNDETERMINED }

    data class Result(val state: State, val label: String, val detail: String)

    fun classify(
        eligible: Boolean,
        rhythmTitle: String?,
        hasEvidence: Boolean,
        historical: Boolean = false,
        measurementComplete: Boolean = false,
        measurementInProgress: Boolean = false
    ): Result {
        if (!eligible) {
            if (measurementInProgress) return Result(State.MEASURING, "ECG 擷取中", "請保持電極接觸，等待手錶完成量測。")
            if (measurementComplete) {
                return Result(
                    State.UNDETERMINED,
                    if (hasEvidence) "已擷取 ECG 訊號" else "尚未取得 ECG 訊號",
                    if (hasEvidence) "手錶已送出訊號，目前沒有心律分析結果。可查看波形或再次量測。"
                    else "手錶這次沒有回傳 ECG 訊號；請確認電極接觸後再次量測。"
                )
            }
            return if (hasEvidence) Result(
                State.UNDETERMINED,
                "已擷取 ECG 訊號",
                "目前沒有心律分析結果；可查看波形或再次量測。"
            ) else Result(State.IDLE, "尚未量測", "請在手錶開始 ECG 量測。")
        }
        val title = rhythmTitle.orEmpty()
        val abnormal = listOf("房顫", "不整", "異常", "過速", "過緩").any(title::contains)
        val normal = listOf("規則", "竇性", "未發現明顯異常").any(title::contains)
        val result = if (abnormal) {
            Result(State.ABNORMAL, "異常", "研究演算法偵測到需注意的心律跡象")
        } else if (normal) {
            Result(State.NORMAL, "正常", "研究演算法未偵測到明顯心律異常")
        } else {
            Result(State.UNDETERMINED, "尚無心律結果", "這次未產生心律分類；可再次量測。")
        }
        return if (historical && result.state in setOf(State.NORMAL, State.ABNORMAL)) {
            result.copy(label = "歷史${result.label}", detail = "歷史研究結果，不代表目前心律。${result.detail}")
        } else result
    }
}
