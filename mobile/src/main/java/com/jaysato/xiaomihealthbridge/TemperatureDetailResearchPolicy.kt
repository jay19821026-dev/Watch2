package com.jaysato.xiaomihealthbridge

internal object TemperatureDetailResearchPolicy {
    fun findings(
        waveform: List<Float>,
        fallbackRawValue: Double?,
        source: String,
        timestampSource: String?,
        measuredAtMillis: Long,
        nowMillis: Long
    ): List<String> {
        val finiteWaveform = waveform.filter(Float::isFinite)
        val fallback = fallbackRawValue?.takeIf(Double::isFinite)?.toFloat()
        val usedFallback = finiteWaveform.isEmpty() && fallback != null
        val sequence = if (usedFallback) listOfNotNull(fallback) else waveform
        val trustedTime = HealthAnalysisFreshnessPolicy.isFreshMeasurement(
            measuredAtMillis, timestampSource, nowMillis, TemperaturePresentation.MAX_AGE_MILLIS
        )
        val summary = TemperatureResearchSummary.calculate(
            values = sequence,
            sourceEvidence = ResearchSourceEvidence.score(source),
            timeEvidence = if (trustedTime) 100 else 0
        )
        val finiteCount = sequence.count(Float::isFinite)
        val displaySource = source.trim().takeIf { it.isNotEmpty() && it != "/" } ?: "UNKNOWN_SOURCE"
        val time = measuredAtMillis.takeIf { it > 0L && it <= nowMillis }
            ?.let { "$it ms" }
            ?: "缺少／無效"
        return buildList {
            add("原始溫度樣本：有限值 ${finiteWaveform.size} / 輸入 ${waveform.size} 點（單位未驗證）")
            if (usedFallback) add("波形沒有有限值；以下單點摘要使用獨立保存的原始值，不代表波形樣本")
            summary?.let { add(it.displayText()) }
            if (summary == null) add("研究指標：未評分（沒有有限原始樣本）")
            add("資料來源：$displaySource · 時間來源：${timestampSource?.ifBlank { "UNKNOWN" } ?: "UNKNOWN"} · 保存時間欄位：$time")
            add("時間軸：NORMALIZED_INDEX；無可信時間戳時僅代表樣本順序，不代表秒或生理變化速率")
            add("訊號品質／分析信心：未評分（原始單位與裝置校準未驗證）；資料完整度另見上列指標")
        }
    }
}
