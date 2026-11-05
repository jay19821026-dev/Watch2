package com.jaysato.xiaomihealthbridge.engine

data class EcgEventStructureConclusion(
    val status: String,
    val headline: String,
    val details: List<String>
)

object EcgEventStructureInterpreter {
    fun interpret(eventCount: Int, layout: EcgCandidateLayoutComparison?): EcgEventStructureConclusion {
        val fields = layout?.fieldSummaries.orEmpty()
        if (eventCount < 2 || fields.size != EcgRawEventDecoder.OBSERVED_ROW_WIDTH) {
            return EcgEventStructureConclusion(
                status = "insufficient_structure_evidence",
                headline = "封包欄位結構資料不足",
                details = listOf("需要至少 2 列且每列欄位數符合目前觀察格式，才能比較欄位變化。")
            )
        }

        val expectedTransitions = eventCount - 1
        val firstTenVary = fields.take(EcgRawEventDecoder.CANDIDATE_FIELD_COUNT).all { field ->
            field.finiteValueCount == eventCount &&
                field.transitionCount == expectedTransitions &&
                field.changedTransitionCount == expectedTransitions
        }
        val eleventhIsConstant = fields[10].finiteValueCount == eventCount &&
            fields[10].changedTransitionCount == 0
        val twelfthAdvancesByTen = layout?.counterPlusTenTransitions == expectedTransitions &&
            layout.counterResetTransitions == 0 && layout.otherCounterTransitions == 0
        val finalFourAreZero = fields.drop(12).all { field ->
            field.finiteValueCount == eventCount && field.zeroValueCount == eventCount
        }
        val patternObserved = firstTenVary && eleventhIsConstant && twelfthAdvancesByTen && finalFourAreZero

        val details = if (patternObserved) {
            listOf(
                "${eventCount} 列均有 16 個有限值；前 10 欄每欄在相鄰事件間都變動。",
                "第 11 欄固定；第 12 欄在 ${expectedTransitions}/${expectedTransitions} 次相鄰事件中增加 10；第 13–16 欄均為 0。",
                "結構結論：資料符合「每列 10 個變動候選值 + 固定／遞增欄位 + 4 個零欄位」模式。這是封包形狀證據，不是已驗證的 ECG 樣本映射。"
            )
        } else {
            val varyingFieldCount = fields.count { it.changedTransitionCount > 0 }
            listOf(
                "${eventCount} 列、${fields.size} 欄；${varyingFieldCount} 欄至少有一次跨事件變化。",
                "目前欄位統計未符合完整的 10 欄變動／固定欄／+10 欄／4 欄零值模式。"
            )
        } + "欄位語義、事件內順序、ADC 取樣時鐘與物理單位未驗證；不得據此計算 bpm、RR、HRV、mV 或正常／異常節律。"

        return EcgEventStructureConclusion(
            status = if (patternObserved) "candidate_packet_pattern_observed" else "candidate_packet_pattern_not_established",
            headline = if (patternObserved) "已觀察到可重現的封包欄位模式（研究描述）" else "封包欄位模式尚未確立（研究描述）",
            details = details
        )
    }
}
