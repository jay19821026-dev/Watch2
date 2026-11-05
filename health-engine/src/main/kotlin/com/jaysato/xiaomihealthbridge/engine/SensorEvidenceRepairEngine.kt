package com.jaysato.xiaomihealthbridge.engine

enum class EvidenceIssue {
    HAL_UNDECODED,
    SOURCE_TIME_MISSING,
    PUBLISH_TIME_ONLY,
    UNIT_UNVERIFIED,
    OEM_OR_PERMISSION_LOCKED,
    QUALITY_INSUFFICIENT,
    UI_SUPPRESSED
}

data class HalEvidenceInput(
    val eventCount: Int,
    val decodedValueCount: Int,
    val rawValueCount: Int,
    val sourceTimestampCount: Int,
    val publishTimestamp: Long?,
    val unitKnown: Boolean,
    val permissionGranted: Boolean,
    val oemModeRequired: Boolean,
    val uiSuppressed: Boolean
)

data class EvidenceRepairPlan(
    val issues: Set<EvidenceIssue>,
    val outputDomain: String,
    val timestampSource: TimestampSource,
    val canProduceResearchOutput: Boolean,
    val actions: List<String>,
    val reason: String
)

/** Classifies HAL evidence and selects a non-destructive research recovery path. */
object SensorEvidenceRepairEngine {
    fun planFromCodes(
        codes: Collection<String>,
        hasRawEvidence: Boolean,
        hasDecodedEvidence: Boolean,
        hasPublishTime: Boolean
    ): EvidenceRepairPlan {
        val report = HalAbnormalityCatalog.classify(codes)
        val issues = report.matched.mapTo(mutableSetOf()) { abnormality ->
            when (abnormality) {
                HalAbnormality.UNMAPPED, HalAbnormality.ROW_SHAPE_MISMATCH,
                HalAbnormality.METADATA_LAYOUT_MISMATCH, HalAbnormality.SAMPLE_COUNTER_MISMATCH -> EvidenceIssue.HAL_UNDECODED
                HalAbnormality.UNKNOWN_TIMESTAMP -> EvidenceIssue.SOURCE_TIME_MISSING
                HalAbnormality.NEEDS_OEM_MODE, HalAbnormality.PERMISSION_DENIED -> EvidenceIssue.OEM_OR_PERMISSION_LOCKED
                HalAbnormality.UNCALIBRATED -> EvidenceIssue.UNIT_UNVERIFIED
                HalAbnormality.UI_SUPPRESSED -> EvidenceIssue.UI_SUPPRESSED
                HalAbnormality.NO_EVENTS, HalAbnormality.NOT_MEASURED,
                HalAbnormality.REJECTED_SIZE -> EvidenceIssue.QUALITY_INSUFFICIENT
            }
        }
        if (hasPublishTime && EvidenceIssue.SOURCE_TIME_MISSING in issues) issues.add(EvidenceIssue.PUBLISH_TIME_ONLY)
        val canOutput = hasRawEvidence || hasDecodedEvidence
        return EvidenceRepairPlan(
            issues = issues,
            outputDomain = if (EvidenceIssue.UNIT_UNVERIFIED in issues) "NORMALIZED_INDEX_OR_RELATIVE" else "SOURCE_OR_RELATIVE",
            timestampSource = if (hasPublishTime && EvidenceIssue.SOURCE_TIME_MISSING in issues) TimestampSource.ESTIMATED else TimestampSource.UNKNOWN,
            canProduceResearchOutput = canOutput,
            actions = (report.actions + if (canOutput) "重新解碼後交由 ResearchAnalysisLoop 重算" else "保留異常狀態並等待新證據").distinct(),
            reason = if (canOutput) "CODE_CLASSIFIED_WITH_EVIDENCE" else "CODE_CLASSIFIED_WITHOUT_EVIDENCE"
        )
    }

    fun plan(input: HalEvidenceInput): EvidenceRepairPlan {
        require(input.eventCount >= 0 && input.decodedValueCount >= 0 && input.rawValueCount >= 0)
        val issues = buildSet {
            if (input.eventCount > 0 && input.decodedValueCount == 0) add(EvidenceIssue.HAL_UNDECODED)
            if (input.sourceTimestampCount == 0) add(EvidenceIssue.SOURCE_TIME_MISSING)
            if (input.sourceTimestampCount == 0 && input.publishTimestamp != null) add(EvidenceIssue.PUBLISH_TIME_ONLY)
            if (!input.unitKnown) add(EvidenceIssue.UNIT_UNVERIFIED)
            if (!input.permissionGranted || input.oemModeRequired) add(EvidenceIssue.OEM_OR_PERMISSION_LOCKED)
            if (input.rawValueCount == 0) add(EvidenceIssue.QUALITY_INSUFFICIENT)
            if (input.uiSuppressed) add(EvidenceIssue.UI_SUPPRESSED)
        }
        val hasEvidence = input.rawValueCount > 0 || input.decodedValueCount > 0
        val timestamp = if (input.sourceTimestampCount > 0) TimestampSource.SENSOR
        else if (input.publishTimestamp != null) TimestampSource.ESTIMATED else TimestampSource.UNKNOWN
        val actions = buildList {
            if (EvidenceIssue.HAL_UNDECODED in issues) add("保留 HAL 原始事件；只有通過格式／欄位驗證才解碼")
            if (EvidenceIssue.SOURCE_TIME_MISSING in issues) add("使用索引時間軸或發布時間，標記 estimated_timeline")
            if (EvidenceIssue.UNIT_UNVERIFIED in issues) add("只輸出相對值、正規化波形與品質，不轉成 mV／Ω／%")
            if (EvidenceIssue.OEM_OR_PERMISSION_LOCKED in issues) add("顯示鎖定原因與重試入口，不把鎖定當成零值")
            if (EvidenceIssue.UI_SUPPRESSED in issues) add("保留研究輸出並顯示來源／信心／狀態")
            if (hasEvidence) add("進入 ResearchAnalysisLoop，保存 raw 與 derived 分離")
        }
        return EvidenceRepairPlan(
            issues = issues,
            outputDomain = if (input.unitKnown) "SOURCE_UNIT_OR_RELATIVE" else "NORMALIZED_INDEX_OR_RELATIVE",
            timestampSource = timestamp,
            canProduceResearchOutput = hasEvidence,
            actions = actions,
            reason = if (hasEvidence) "RESEARCH_EVIDENCE_AVAILABLE" else "NO_RAW_OR_DECODED_EVIDENCE"
        )
    }
}
