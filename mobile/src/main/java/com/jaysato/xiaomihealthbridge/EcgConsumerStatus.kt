package com.jaysato.xiaomihealthbridge

object EcgConsumerStatus {
    enum class State { NORMAL, ABNORMAL, MEASURING, UNDETERMINED }

    data class Result(val state: State, val label: String, val detail: String)

    fun classify(
        eligible: Boolean,
        rhythmTitle: String?,
        hasEvidence: Boolean,
        historical: Boolean = false,
        measurementComplete: Boolean = false
    ): Result {
        if (!eligible) {
            if (measurementComplete) {
                return Result(
                    State.UNDETERMINED,
                    "無法判定",
                    if (hasEvidence) "本次 ECG 已完成並保存，但資料未達可信心律判讀條件。請重新量測。"
                    else "本次 ECG 已結束，但沒有可用資料。請重新量測。"
                )
            }
            return Result(
                State.MEASURING,
                "測量中",
                if (hasEvidence) "已收到訊號，正在等待足夠資料完成判定" else "請在手錶開始 ECG 量測"
            )
        }
        val title = rhythmTitle.orEmpty()
        val abnormal = listOf("房顫", "不整", "異常", "過速", "過緩").any(title::contains)
        val normal = listOf("規則", "竇性", "未發現明顯異常").any(title::contains)
        val result = if (abnormal) {
            Result(State.ABNORMAL, "異常", "研究演算法偵測到需注意的心律跡象")
        } else if (normal) {
            Result(State.NORMAL, "正常", "研究演算法未偵測到明顯心律異常")
        } else {
            Result(State.UNDETERMINED, "無法判定", "尚無可用的心律分類結果；請重新量測。")
        }
        return if (historical && result.state in setOf(State.NORMAL, State.ABNORMAL)) {
            result.copy(label = "歷史${result.label}", detail = "歷史研究結果，不代表目前心律。${result.detail}")
        } else result
    }
}
