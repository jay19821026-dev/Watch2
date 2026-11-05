package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.EcgCandidateLayoutComparison
import com.jaysato.xiaomihealthbridge.engine.EcgEventStructureInterpreter

internal object EcgResearchDisplay {
    const val MIN_SAMPLES_FOR_COMPLETENESS_INDEX = 32

    fun chartAxisLabel(calibratedMv: Boolean): String = if (calibratedMv) {
        "縱軸：外部參考研究 mV 映射 · 橫軸：樣本索引（非秒）"
    } else {
        "縱軸：正規化相對振幅（未校準）· 橫軸：NORMALIZED_INDEX（非秒）"
    }

    fun stableWindowQualityLabel(score: Int?): String =
        "穩定區段研究品質 ${ResearchScoreDisplay.format(score)}"

    fun evidenceCompletenessScore(
        waveform: List<Float>,
        timelineSource: String,
        decoderTraceability: Int?
    ): Int? {
        val finiteCount = waveform.count(Float::isFinite)
        if (waveform.isEmpty() || finiteCount < MIN_SAMPLES_FOR_COMPLETENESS_INDEX || decoderTraceability == null) return null
        val timeEvidence = when (timelineSource) {
            "SENSOR" -> 100
            "ESTIMATED" -> 40
            "NORMALIZED_INDEX" -> 15
            else -> 0
        }
        return ResearchQualityIndexCalculator.calculate(
            finiteRatio = finiteCount.toDouble() / waveform.size,
            sampleCount = finiteCount,
            waveformStability = null,
            sourceEvidence = decoderTraceability.coerceIn(0, 100),
            timeEvidence = timeEvidence
        ).score
    }

    fun canApplyValidatedMvProfile(waveform: List<Float>): Boolean =
        waveform.isNotEmpty() && waveform.all(Float::isFinite)

    fun decoderTraceabilityScore(eventRowsStatus: String, decoderEncoding: String, decoded: Boolean): Int? {
        if (!decoded || decoderEncoding != EcgHalEventResearchDecoder.ENCODING) return null
        return when (eventRowsStatus) {
            "complete_for_observed_events" -> 100
            "bounded_recent_events" -> 60
            else -> null
        }
    }

    fun decodedSampleHeadline(waveform: List<Float>): String {
        val validCount = waveform.count(Float::isFinite)
        val gapCount = waveform.size - validCount
        return buildString {
            append("已解碼 $validCount 個有效研究樣本")
            if (gapCount > 0) append(" · 保留 $gapCount 個缺口索引")
        }
    }

    fun waveformPointHeadline(waveform: List<Float>): String {
        val validCount = waveform.count(Float::isFinite)
        val gapCount = waveform.size - validCount
        return buildString {
            append("$validCount 個有效點")
            if (gapCount > 0) append(" · $gapCount 個缺口索引")
        }
    }

    fun candidateInspectionLines(
        candidateValueCount: Int?,
        layout: EcgCandidateLayoutComparison?,
        status: String,
        inspectedFieldValueCount: Int? = null,
        eventCount: Int? = null
    ): List<String> {
        if (layout == null || candidateValueCount == null) {
            return listOf(
                "未取得可檢查候選數值（檢查狀態：${status.ifBlank { "UNKNOWN" }}）",
                "欄位映射：未驗證；未產生 bpm、RR、HRV 或 mV"
            )
        }
        val lines = mutableListOf(
            "原始事件結構：${eventCount ?: layout.fieldSummaries.firstOrNull()?.finiteValueCount ?: 0} 列、${layout.fieldSummaries.size} 欄；有限候選數值 ${candidateValueCount.coerceAtLeast(0)} 個（不是已解碼 ECG 樣本）",
            "欄位映射：未驗證；未產生 bpm、RR、HRV 或 mV"
        )
        fun number(value: Double?): String = value?.takeIf(Double::isFinite)?.let { "%.3f".format(it) } ?: "未取得"
        inspectedFieldValueCount?.takeIf { it >= 0 }?.let {
            lines += "原始事件列欄位數值 $it 個（${layout.fieldSummaries.size} 欄 × ${layout.fieldSummaries.firstOrNull()?.finiteValueCount ?: 0} 列）；與候選值數不同，也不等於已解碼樣本"
        }
        lines += "排列假設相鄰差絕對中位數：列優先 ${number(layout.rowMajorMedianAbsoluteDelta)} · 欄優先 ${number(layout.columnMajorMedianAbsoluteDelta)}；同列 ${number(layout.withinRowMedianAbsoluteDelta)} · 跨列邊界 ${number(layout.rowBoundaryMedianAbsoluteDelta)}（均依事件列／欄索引；不能確認映射）"
        lines += "第 ${layout.counterColumnIndexHypothesis + 1} 欄 +10 假設：${layout.counterPlusTenTransitions} 次 · 重置 ${layout.counterResetTransitions} 次 · 其他正向變化 ${layout.otherCounterTransitions} 次（語義未驗證）"
        if (layout.fieldSummaries.isNotEmpty()) {
            lines += "各欄描述統計（依事件列序號；欄位語義未確認，非 ECG 樣本／時間軸）"
            layout.fieldSummaries.forEach { field ->
                lines += "欄位 ${field.fieldIndex + 1}：有限值 ${field.finiteValueCount} · 0 值 ${field.zeroValueCount} · 範圍 ${number(field.minimum)}–${number(field.maximum)} · 平均 ${number(field.mean)} · 變動 ${field.changedTransitionCount}/${field.transitionCount} 次"
            }
        }
        val conclusion = EcgEventStructureInterpreter.interpret(
            eventCount = eventCount ?: layout.fieldSummaries.firstOrNull()?.finiteValueCount ?: 0,
            layout = layout
        )
        lines += "封包結構結論：${conclusion.headline}"
        lines += conclusion.details
        return lines
    }

    fun packetStructureSummaryLines(
        eventCount: Int,
        layout: EcgCandidateLayoutComparison?
    ): List<String> {
        if (layout == null) return emptyList()
        val conclusion = EcgEventStructureInterpreter.interpret(eventCount, layout)
        val finiteCandidates = layout.fieldSummaries
            .take(EcgHalEventResearchDecoder.CANDIDATE_FIELD_COUNT)
            .sumOf { it.finiteValueCount }
        return listOf(
            "封包結構研究結果：${eventCount.coerceAtLeast(0)} 列、${layout.fieldSummaries.size} 欄；有限候選數值 $finiteCandidates 個，解碼 ECG 樣本仍為 0。",
            "${conclusion.headline}。欄位映射與取樣時間未驗證，因此不輸出 bpm、RR、HRV、mV 或正常／異常判定。"
        )
    }

    fun rawRowsRetentionLabel(status: String): String = when (status) {
        "rejected_size_limit" -> "資料超出安全大小上限，整批原始列已拒收；沒有顯示截斷片段。"
        "bounded_recent_events" -> "超過保留列數上限，只保留最新事件列。"
        "complete_for_observed_events" -> "本次觀測到的事件列已完整保存。"
        else -> "事件列保存狀態未確認（${status.ifBlank { "UNKNOWN" }}）。"
    }

    fun rawRowsPanelTitle(hasRows: Boolean): String = if (hasRows) {
        "未映射 SensorEvent.values（最新一列預覽）"
    } else {
        "SensorEvent.values 保存與檢查狀態"
    }

    fun evidenceLines(
        waveform: List<Float>,
        sampleRateHz: Float?,
        timelineSource: String,
        rPeakIndices: List<Int>,
        rPeakIndexDistancesSamples: List<Int>,
        calibratedMv: Boolean,
        decoderTraceability: Int? = null
    ): List<String> {
        val finite = waveform.filter(Float::isFinite)
        val showMv = calibratedMv && canApplyValidatedMvProfile(waveform)
        val span = finite.maxOrNull()?.minus(finite.minOrNull() ?: 0f)
        val evidenceScore = evidenceCompletenessScore(waveform, timelineSource, decoderTraceability)
        val timeLine = when (timelineSource) {
            "SENSOR" -> "SENSOR 事件時間軸（不代表 ADC 取樣率已驗證）"
            "ESTIMATED" -> "ESTIMATED_TIMELINE（推估時間軸；非感測器時鐘）"
            "NORMALIZED_INDEX" -> "NORMALIZED_INDEX（索引時間軸；無物理時間）"
            else -> "UNKNOWN（時間來源未確認）"
        }
        val pointRate = sampleRateHz?.takeIf { it.isFinite() && it > 0f }
            ?.let { " · 點速率估算 ${"%.1f".format(it)} 點/秒" }
            ?: " · 點速率：未確認"
        val waveformScale = when {
            span == null -> "波形峰峰值：未取得"
            showMv -> "波形峰峰值：${"%.5f".format(span)} mV（外部參考研究映射）"
            else -> "波形峰峰值：${"%.3f".format(span)} raw code（相對振幅；單位未驗證）"
        }
        val peaks = if (rPeakIndices.isEmpty()) "R 峰候選索引：未檢出" else
            "R 峰候選索引（研究候選）：${rPeakIndices.take(32).joinToString(", ")}" +
                if (rPeakIndices.size > 32) " … 共 ${rPeakIndices.size} 個" else ""
        val distances = if (rPeakIndexDistancesSamples.isEmpty()) "R 峰索引間距：未取得" else
            "相鄰 R 峰索引間距：${rPeakIndexDistancesSamples.take(32).joinToString(", ")} samples（非 ms）" +
                if (rPeakIndexDistancesSamples.size > 32) " … 共 ${rPeakIndexDistancesSamples.size} 段" else ""
        return listOf(
            "研究解碼 ${finite.size} 個有效樣本 · ${waveform.size} 個索引位置 · $timeLine$pointRate",
            "研究資料完整度指數：${ResearchScoreDisplay.format(evidenceScore)}（至少 $MIN_SAMPLES_FOR_COMPLETENESS_INDEX 個有效樣本；有限值比例、樣本量、解碼來源與時間來源證據；非訊號準確度／診斷信心）· ${ResearchQualityIndexCalculator.VERSION}",
            waveformScale,
            peaks,
            distances
        )
    }
}
