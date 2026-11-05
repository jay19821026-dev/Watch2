package com.jaysato.xiaomihealthbridge.engine

data class ExplanationFact(val id: String, val verifiedText: String, val provenance: SensorProvenance) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        require(verifiedText.isNotBlank() && verifiedText.length <= 512)
    }
}

data class ModelExplanationClaim(val factId: String, val text: String)
data class ValidatedExplanation(val accepted: Boolean, val text: String, val rejectionReason: String?)

object AiExplanationValidator {
    const val SYSTEM_PROMPT = "你是感測資料說明器。只能選擇輸入 facts 中的 id 與完整原文，禁止新增數值、單位、時間、根因、校準或生理換算。callback receipt time 不是 sensor acquisition time。未知維持未知。不得輸出自由文字；回傳 claims，每項僅含 factId 與 text。"

    fun validate(facts: List<ExplanationFact>, claims: List<ModelExplanationClaim>): ValidatedExplanation {
        require(facts.size <= 64)
        require(facts.map { it.id }.distinct().size == facts.size)
        require(facts.map { it.provenance.sessionId }.distinct().size <= 1)
        val fallback = facts.joinToString("\n") { it.verifiedText }.ifBlank { "未知；尚無可驗證的感測事實。" }
        fun reject(reason: String) = ValidatedExplanation(false, fallback, reason)
        if (claims.isEmpty() || claims.size > 64) return reject("INVALID_CLAIM_COUNT")
        if (claims.map { it.factId }.distinct().size != claims.size) return reject("DUPLICATE_FACT_REFERENCE")
        val indexedFacts = facts.associateBy { it.id }
        for (claim in claims) {
            val fact = indexedFacts[claim.factId] ?: return reject("UNSUPPORTED_FACT_REFERENCE")
            if (claim.text != fact.verifiedText) return reject("UNSUPPORTED_OR_ALTERED_CLAIM")
        }
        if (claims.map { it.factId }.toSet() != indexedFacts.keys) return reject("OMITTED_REQUIRED_FACT")
        return ValidatedExplanation(true, fallback, null)
    }
}
