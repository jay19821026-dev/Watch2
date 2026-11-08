package com.jaysato.xiaomihealthbridge

import org.junit.Assert.*
import org.junit.Test
import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.text.DateFormat
import java.util.Date

class HealthConclusionEngineTest {
    @Test
    fun failedSensorCalculationDoesNotSuppressFollowingResults() {
        val failed = HealthConclusionEngine.buildSafely("ecg", "ECG") {
            throw IllegalStateException("synthetic test failure")
        }
        val succeeding = HealthConclusionEngine.buildSafely("heart", "心率") {
            HealthConclusionEngine.Item(
                id = "heart",
                title = "心率",
                valueText = "72 bpm",
                clinicalCompare = "測試資料",
                conclusion = "研究參考",
                level = HealthConclusionEngine.Level.RESEARCH,
                score = null,
                available = true
            )
        }

        assertEquals(HealthConclusionEngine.Level.UNAVAILABLE, failed.level)
        assertFalse(failed.available)
        assertNull(failed.score)
        assertTrue(failed.conclusion.contains("其他健康項目仍會繼續分析"))
        assertEquals("72 bpm", succeeding.valueText)
        assertTrue(succeeding.available)
    }

    @Test
    fun biozConclusionShowsSavedResearchMetricsWithoutPhysicalUnits() {
        val raw = preferences(mapOf(
            "bioz_raw_value_count" to 128,
            "bioz_time" to System.currentTimeMillis(),
            "bioz_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "bioz_waveform_median_position_index" to 53,
            "bioz_relative_change_percent" to 2.5f,
            "bioz_stability_score" to 81,
            "bioz_algorithm_version" to BiomedicalCalibration.BIOZ_RESEARCH_VERSION
        ))

        val item = HealthConclusionEngine.biozConclusion(raw)

        assertTrue(item.valueText.contains("128 點原始"))
        assertTrue(item.valueText.contains("視窗內相對中位位置 53/100"))
        assertTrue(item.valueText.contains("首尾區段相對振幅差 +2.5%（非跨次趨勢）"))
        assertTrue(item.valueText.contains("波形相對離散度"))
        assertFalse(item.valueText.contains("相對離散度反向指標"))
        assertFalse(item.valueText.contains("Ω"))
        assertFalse(item.conclusion.contains("體脂％"))
    }

    @Test
    fun biozConclusionDoesNotInventMissingResearchMetrics() {
        val raw = preferences(mapOf(
            "bioz_raw_value_count" to 128,
            "bioz_time" to System.currentTimeMillis(),
            "bioz_timestamp_source" to "android_sensor_event_elapsed_realtime"
        ))

        val item = HealthConclusionEngine.biozConclusion(raw)

        assertEquals("128 點原始", item.valueText)
        assertTrue(item.conclusion.contains("目前沒有足夠波形可計算衍生研究指標"))
        assertFalse(item.valueText.contains("/100"))
    }

    @Test
    fun biozConclusionRecomputesRelativeMetricsFromSavedWaveformWhenCacheIsMissing() {
        val waveform = List(64) { it.toFloat() }.joinToString(",")
        val raw = preferences(mapOf(
            "bioz_raw_value_count" to 64,
            "bioz_time" to System.currentTimeMillis(),
            "bioz_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "bioz_display_waveform" to waveform
        ))

        val item = HealthConclusionEngine.biozConclusion(raw)

        assertTrue(item.valueText.contains("64 點原始"))
        assertTrue(item.valueText.contains("視窗內相對中位位置 50/100"))
        assertTrue(item.valueText.contains("首尾區段相對振幅差"))
        assertTrue(item.conclusion.contains("由手機保存波形"))
        assertTrue(item.conclusion.contains(BiomedicalCalibration.BIOZ_RESEARCH_VERSION))
        assertFalse(item.valueText.contains("Ω"))
        assertFalse(item.conclusion.contains("體脂％") && item.valueText.contains("體脂值"))
    }

    @Test
    fun biozConclusionIgnoresEveryCachedMetricFromAnOlderAlgorithmVersion() {
        val waveform = List(64) { it.toFloat() }.joinToString(",")
        val raw = preferences(mapOf(
            "bioz_raw_value_count" to 64,
            "bioz_time" to System.currentTimeMillis(),
            "bioz_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "bioz_display_waveform" to waveform,
            "bioz_algorithm_version" to "bioz-relative-waveform-v1",
            "bioz_waveform_median_position_index" to 99,
            "bioz_relative_change_percent" to 88f,
            "bioz_stability_score" to 77,
            "bioz_confidence_score" to 66
        ))

        val item = HealthConclusionEngine.biozConclusion(raw)

        assertTrue(item.valueText.contains("視窗內相對中位位置 50/100"))
        assertFalse(item.valueText.contains("99/100"))
        assertFalse(item.valueText.contains("+88.0%"))
        assertFalse(item.valueText.contains("波形相對離散度 77%"))
        assertFalse(item.valueText.contains("資料完整度 66/100"))
        assertTrue(item.conclusion.contains(BiomedicalCalibration.BIOZ_RESEARCH_VERSION))
    }

    @Test
    fun biozShowsSavedWaveformPreviewWithoutChangingZeroRawSampleCount() {
        val waveform = List(64) { it.toFloat() }.joinToString(",")
        val raw = preferences(mapOf(
            "bioz_raw_value_count" to 0,
            "bioz_time" to System.currentTimeMillis(),
            "bioz_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "bioz_display_waveform" to waveform
        ))

        val item = HealthConclusionEngine.biozConclusion(raw)

        assertTrue(item.available)
        assertTrue(item.valueText.contains("原始樣本計數 0"))
        assertTrue(item.valueText.contains("保存波形預覽 64 點"))
        assertTrue(item.valueText.contains("視窗內相對中位位置"))
        assertFalse(item.valueText.contains("64 點原始"))
        assertFalse(item.valueText.contains("Ω"))
    }

    @Test
    fun biozUnknownTimestampIsNotMislabelledAsHistorical() {
        val raw = preferences(mapOf("bioz_raw_value_count" to 128))

        val item = HealthConclusionEngine.biozConclusion(raw)

        assertTrue(item.valueText.startsWith("128 點時間未確認的原始資料"))
        assertFalse(item.valueText.contains("歷史"))
        assertTrue(item.clinicalCompare.contains("NORMALIZED_INDEX"))
        assertTrue(item.conclusion.contains("量測時間未知"))
    }

    @Test
    fun userStateSeparatesNormalAttentionResearchAndUnavailableEvidence() {
        fun item(level: HealthConclusionEngine.Level) = HealthConclusionEngine.Item(
            id = level.name,
            title = level.name,
            valueText = "",
            clinicalCompare = "",
            conclusion = "",
            level = level,
            score = null,
            available = level != HealthConclusionEngine.Level.UNAVAILABLE
        )

        assertEquals(HealthConclusionEngine.UserState.NORMAL, item(HealthConclusionEngine.Level.GOOD).userState)
        assertEquals(HealthConclusionEngine.UserState.ATTENTION, item(HealthConclusionEngine.Level.WATCH).userState)
        assertEquals(HealthConclusionEngine.UserState.ATTENTION, item(HealthConclusionEngine.Level.ATTENTION).userState)
        assertEquals(HealthConclusionEngine.UserState.RESEARCH, item(HealthConclusionEngine.Level.RESEARCH).userState)
        assertEquals(HealthConclusionEngine.UserState.UNAVAILABLE, item(HealthConclusionEngine.Level.UNAVAILABLE).userState)
        assertEquals(HealthConclusionEngine.UserState.UNAVAILABLE, item(HealthConclusionEngine.Level.GOOD).copy(available = false).userState)
    }

    @Test
    fun pressureAltitudeIsExplicitlyLabeledAsStandardAtmosphereEstimate() {
        val now = System.currentTimeMillis()
        val raw = preferences(mapOf(
            "pressure_average" to 1000f,
            "pressure_time" to now,
            "pressure_timestamp_source" to "android_sensor_event_elapsed_realtime"
        ))
        val feature = preferences(mapOf(
            "altitude" to 110.0f,
            "altitude_timestamp" to now,
            "altitude_timestamp_source" to "android_sensor_event_elapsed_realtime"
        ))

        val item = HealthConclusionEngine.pressureConclusion(raw, feature)

        assertTrue(item.valueText.contains("標準大氣壓力高度估算"))
        assertFalse(item.valueText.contains("相對高度"))
        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertFalse(HealthConclusionSummaryPolicy.shouldShowScore(item))
        assertTrue(HealthConclusionSummaryPolicy.summarize(listOf(item), 1).second.contains("可對照生理數值 0/1"))
        assertTrue(item.clinicalCompare.contains("不是當地校準海拔"))
        assertTrue(item.conclusion.contains("不據此判定樓層或爬升"))
    }

    @Test
    fun stalePressureValueRemainsVisibleAsHistoricalAndUnscored() {
        val oldTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val raw = preferences(mapOf(
            "pressure_average" to 1002.4f,
            "pressure_time" to oldTime,
            "pressure_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "pressure_source_sensor" to "watch-barometer"
        ))

        val item = HealthConclusionEngine.pressureConclusion(raw, preferences(emptyMap()))

        assertTrue(item.valueText.contains("1002.4 hPa"))
        assertTrue(item.valueText.contains("歷史讀值"))
        assertTrue(item.clinicalCompare.contains("非即時值"))
        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertNull(item.score)
        assertTrue(item.available)
    }

    @Test
    fun pressureWithoutAnyRawValueRemainsUnavailable() {
        val item = HealthConclusionEngine.pressureConclusion(
            preferences(emptyMap()), preferences(emptyMap())
        )

        assertEquals("—", item.valueText)
        assertEquals(HealthConclusionEngine.Level.UNAVAILABLE, item.level)
        assertFalse(item.available)
    }

    @Test
    fun freshQualifiedEcgCannotSupplyGeneralHeartRate() {
        val now = System.currentTimeMillis()
        val raw = preferences(mapOf(
            "ecg_rhythm_bpm" to 72f,
            "ecg_quality_score" to 95,
            "ecg_raw_value_count" to 128,
            "ecg_rhythm_rr_count" to 8,
            "ecg_time" to now,
            "ecg_rhythm_research_only" to false
        ))
        val item = HealthConclusionEngine.heartConclusion(raw, preferences(emptyMap()))

        assertEquals(HealthConclusionEngine.Level.UNAVAILABLE, item.level)
        assertFalse(item.available)
        assertTrue(item.valueText.contains("無法判定"))
    }

    @Test
    fun selectedHealthServicesHeartRateShowsItsSourceAndMeasurementTime() {
        val measuredAt = System.currentTimeMillis() - 2 * 60_000L
        val feature = preferences(mapOf(
            "whs_heart_rate" to 106f,
            "whs_heart_rate_timestamp" to measuredAt,
            "whs_heart_rate_timestamp_source" to HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        ))

        val item = HealthConclusionEngine.heartConclusion(preferences(emptyMap()), feature)

        assertEquals("106 bpm", item.valueText)
        assertTrue(item.clinicalCompare.contains("來源：Health Services"))
        val expectedTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(measuredAt))
        assertTrue(item.clinicalCompare.contains("量測時間：$expectedTime"))
        assertTrue(item.conclusion.contains("不是經確認的靜息心率"))
    }

    @Test
    fun healthConnectHistoryIsVisibleButNotTreatedAsCurrent() {
        val measuredAt = System.currentTimeMillis() - 60 * 60_000L
        val raw = preferences(mapOf(
            "health_connect_heart_rate_bpm" to 75f,
            "health_connect_heart_rate_time" to measuredAt,
            "health_connect_heart_rate_origin" to "watch-app"
        ))

        val item = HealthConclusionEngine.heartConclusion(raw, preferences(emptyMap()))

        assertEquals("75 bpm · 歷史紀錄", item.valueText)
        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertTrue(item.conclusion.contains("不作目前結論"))
    }

    @Test
    fun olderTrustedHeartRateRemainsVisibleWithItsOriginalDate() {
        val measuredAt = System.currentTimeMillis() - 14L * 24L * 60L * 60L * 1000L
        val raw = preferences(mapOf(
            "health_connect_heart_rate_bpm" to 75f,
            "health_connect_heart_rate_time" to measuredAt,
            "health_connect_heart_rate_origin" to "watch-app"
        ))

        val item = HealthConclusionEngine.heartConclusion(raw, preferences(emptyMap()))

        assertEquals("75 bpm · 歷史紀錄", item.valueText)
        assertTrue(item.conclusion.contains(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(measuredAt))))
        assertTrue(item.conclusion.contains("不作目前結論"))
    }

    @Test
    fun olderHeartRateIsExplainedButNotPresentedAsCurrentConclusion() {
        val measuredAt = System.currentTimeMillis() - 15 * 60_000L
        val feature = preferences(mapOf(
            "whs_heart_rate" to 106f,
            "whs_heart_rate_timestamp" to measuredAt,
            "whs_heart_rate_timestamp_source" to HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        ))

        val item = HealthConclusionEngine.heartConclusion(preferences(emptyMap()), feature)

        assertEquals("106 bpm · 歷史紀錄", item.valueText)
        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertEquals(HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL, item.evidenceCategory)
        assertFalse(HealthConclusionSummaryPolicy.shouldShowScore(item))
        assertTrue(item.conclusion.contains("較舊的心率紀錄"))
        assertTrue(item.conclusion.contains("超出本頁 10 分鐘分析輸入窗"))
        assertTrue(item.conclusion.contains("不作目前結論"))
    }

    @Test
    fun healthServicesHeartRateWithoutTrustedTimestampIsNotUsed() {
        val feature = preferences(mapOf(
            "whs_heart_rate" to 106f,
            "whs_heart_rate_timestamp" to System.currentTimeMillis()
        ))

        val item = HealthConclusionEngine.heartConclusion(preferences(emptyMap()), feature)

        assertEquals(HealthConclusionEngine.Level.UNAVAILABLE, item.level)
        assertTrue(item.valueText.contains("無法判定"))
    }

    @Test
    fun rawHealthServicesHeartRateWithoutTrustedTimestampIsNotUsed() {
        val raw = preferences(mapOf(
            "whs_heart_rate_average" to 106f,
            "whs_heart_rate_time" to System.currentTimeMillis()
        ))

        val item = HealthConclusionEngine.heartConclusion(raw, preferences(emptyMap()))

        assertEquals(HealthConclusionEngine.Level.UNAVAILABLE, item.level)
        assertTrue(item.valueText.contains("無法判定"))
    }

    @Test
    fun missingSensorReadingsShowExplicitUnavailableStateAndPreserveZeroCounts() {
        val now = System.currentTimeMillis()
        val items = listOf(
            HealthConclusionEngine.spo2Conclusion(preferences(emptyMap())),
            HealthConclusionEngine.ecgConclusion(
                samples = 0, eventCount = 0, qualityScore = 0, hr = null, rrCount = 0,
                titleRhythm = null, measuredAt = 0L, now = now
            ),
            HealthConclusionEngine.hrvConclusion(preferences(emptyMap())),
            HealthConclusionEngine.temperatureResearchItem(null, 0L, "UNKNOWN", now),
            HealthConclusionEngine.biozConclusion(preferences(emptyMap())),
            HealthConclusionEngine.ppgConclusion(
                status = "not_measured", reason = "", samples = 0, eventCount = 0,
                qualityScore = 0, sampleRateHz = 0f, measuredAt = 0L,
                piPercent = Float.NaN, pulseBpm = Float.NaN, now = now
            )
        )

        assertTrue(items.all { it.valueText != "—" })
        assertTrue(items.all { it.valueText.isNotBlank() })
        assertTrue(items.any { it.valueText.contains("0 筆原始值") })
        assertTrue(items.any { it.id == "ecg" && it.valueText == "尚無 ECG 結果" })
    }

    @Test
    fun rawHealthServicesHeartRateWithTrustedFreshTimestampRemainsAvailable() {
        val raw = preferences(mapOf(
            "whs_heart_rate_average" to 106f,
            "whs_heart_rate_time" to System.currentTimeMillis(),
            "whs_heart_rate_timestamp_source" to HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        ))

        val item = HealthConclusionEngine.heartConclusion(raw, preferences(emptyMap()))

        assertEquals("106 bpm", item.valueText)
        assertTrue(item.clinicalCompare.contains("來源：手錶心率"))
    }

    @Test
    fun freshRawHeartRateSensorBpmAppearsInHealthConclusion() {
        val measuredAt = System.currentTimeMillis() - 60_000L
        val raw = preferences(mapOf(
            "heart_rate_raw_average" to 74f,
            "heart_rate_raw_status" to "has_data",
            "heart_rate_raw_time" to measuredAt,
            "heart_rate_raw_timestamp_source" to "android_sensor_event_elapsed_realtime"
        ))

        val item = HealthConclusionEngine.heartConclusion(raw, preferences(emptyMap()))

        assertEquals("74 bpm", item.valueText)
        assertTrue(item.available)
        assertTrue(item.clinicalCompare.contains("來源：手錶心率感測通道"))
    }

    @Test
    fun olderHeartRateExplanationRequiresTrustedTimestampSource() {
        val oldTime = System.currentTimeMillis() - 15 * 60_000L
        val raw = preferences(mapOf(
            "whs_heart_rate_average" to 106f,
            "whs_heart_rate_time" to oldTime
        ))
        val feature = preferences(mapOf(
            "whs_heart_rate" to 105f,
            "whs_heart_rate_timestamp" to oldTime,
            "whs_heart_rate_timestamp_source" to "unreported"
        ))

        val item = HealthConclusionEngine.heartConclusion(raw, feature)

        assertFalse(item.conclusion.contains("較舊的心率紀錄"))
        assertTrue(item.conclusion.contains("目前沒有符合"))
    }

    @Test
    fun selectedPpgPulseIsMarkedEstimateAndKeepsItsMeasurementTime() {
        val measuredAt = System.currentTimeMillis() - 2 * 60_000L
        val raw = preferences(mapOf(
            "ppg_formula_pulse_bpm" to 82f,
            "ppg_status" to "valid_signal",
            "ppg_wear_qualified" to true,
            "ppg_channel_mapping_verified" to true,
            "ppg_raw_value_count" to 128,
            "ppg_quality_score" to 80,
            "ppg_sample_rate" to 50f,
            "ppg_time" to measuredAt,
            "ppg_timestamp_source" to "android_sensor_event_elapsed_realtime"
        ))

        val item = HealthConclusionEngine.heartConclusion(raw, preferences(emptyMap()))

        assertEquals("82 bpm · 研究估算", item.valueText)
        assertTrue(item.clinicalCompare.contains("來源：PPG 研究估算"))
        val expectedTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(measuredAt))
        assertTrue(item.clinicalCompare.contains("量測時間：$expectedTime"))
        assertTrue(item.conclusion.contains("不等同經確認的心率"))
    }

    @Test
    fun ppgPulseBelowSharedQualityThresholdIsNotDisplayedAsBpm() {
        val now = System.currentTimeMillis()
        val item = HealthConclusionEngine.ppgConclusion(
            status = "valid_signal",
            reason = "",
            samples = 128,
            eventCount = 128,
            qualityScore = 55,
            sampleRateHz = 50f,
            channelMappingVerified = true,
            measuredAt = now,
            piPercent = 1.2f,
            pulseBpm = 82f,
            now = now,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )

        assertFalse(item.valueText.contains("bpm"))
        assertFalse(item.clinicalCompare.contains("PI "))
    }

    @Test
    fun ppgRejectsDerivedValuesWhenTimestampSourceIsUnknownEvenIfTimestampIsPositive() {
        val item = HealthConclusionEngine.ppgConclusion(
            status = "valid_signal", reason = "", samples = 128, eventCount = 32,
            qualityScore = 95, sampleRateHz = 100f, channelMappingVerified = true,
            wearQualified = true, measuredAt = 9_000L, piPercent = 2f,
            pulseBpm = 72f, now = 10_000L, timestampSource = "UNKNOWN"
        )

        assertFalse(item.valueText.contains("bpm"))
        assertFalse(item.clinicalCompare.contains("PI "))
        assertTrue(item.conclusion.contains("時間來源未驗證"))
        assertTrue(item.valueText.contains("時間未確認"))
    }

    @Test
    fun ppgDerivedResearchOutputIncludesEvidenceAndProvenance() {
        val item = HealthConclusionEngine.ppgConclusion(
            status = "valid_signal", reason = "", samples = 128, eventCount = 32,
            qualityScore = 82, sampleRateHz = 100f, channelMappingVerified = true,
            wearQualified = true, measuredAt = 9_000L, piPercent = 2f,
            pulseBpm = 72f, now = 10_000L, confidenceScore = 74,
            source = "PPG test source",
            timestampSource = "android_sensor_event_elapsed_realtime"
        )

        assertTrue(item.valueText.contains("72 bpm"))
        assertTrue(item.clinicalCompare.contains("資料完整度 74/100"))
        assertTrue(item.clinicalCompare.contains("來源：PPG test source"))
        assertTrue(item.clinicalCompare.contains("時間：android_sensor_event_elapsed_realtime"))
        assertTrue(item.clinicalCompare.contains("校準：未驗證"))
    }

    @Test
    fun freshQualifiedEcgCannotSupplyGeneralHealthHrv() {
        val now = System.currentTimeMillis()
        val raw = preferences(mapOf(
            "hrv_research_time" to now,
            "hrv_research_source_kind" to "ecg",
            "hrv_research_formula_rmssd_ms" to 42f,
            "hrv_research_formula_sdnn_ms" to 50f,
            "hrv_research_samples" to 128,
            "hrv_research_rr_count" to 8,
            "ecg_rhythm_research_only" to false,
            "ecg_quality_score" to 95,
            "ecg_raw_value_count" to 128,
            "ecg_rhythm_rr_count" to 8,
            "ecg_time" to now,
            "ecg_status" to "valid_signal",
            "ecg_sample_rate" to 100f,
            "ecg_channel_mapping_verified" to true,
            "ecg_formula_rmssd_ms" to 42f,
            "ecg_formula_sdnn_ms" to 50f
        ))
        val item = HealthConclusionEngine.hrvConclusion(raw)

        assertEquals("128 筆歷史原始樣本", item.valueText)
        assertRetainedResearchExcludedFromHealthSummary(item)
        assertFalse(item.valueText.contains("ms"))
        assertFalse(item.valueText.contains("壓力"))
        assertTrue(item.conclusion.contains("不以未校準 ECG 推算 HRV"))
    }

    @Test
    fun hrvConclusionExplainsWhenRawPpgExistsButIsOutsideAnalysisWindow() {
        val now = System.currentTimeMillis()
        val raw = preferences(mapOf(
            "ppg_time" to now - 11 * 60_000L,
            "ppg_status" to "valid_signal",
            "ppg_raw_value_count" to 500,
            "ppg_sample_rate" to 100f,
            "ppg_quality_score" to 90,
            "ppg_channel_mapping_verified" to true,
            "hrv_research_source_kind" to "ppg",
            "hrv_research_time" to now - 11 * 60_000L,
            "hrv_research_samples" to 500,
            "hrv_research_rr_count" to 10,
            "ppg_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "hrv_research_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "ppg_wear_qualified" to true,
            "ppg_formula_rmssd_ms" to 42f,
            "ppg_formula_sdnn_ms" to 50f,
            "ppg_formula_stress_score" to 20,
            "hrv_research_formula_rmssd_ms" to 42f,
            "hrv_research_formula_sdnn_ms" to 50f,
            "hrv_research_formula_stress_score" to 20
        ))

        val item = HealthConclusionEngine.hrvConclusion(raw)

        assertRetainedResearchExcludedFromHealthSummary(item)
        assertEquals("500 筆歷史原始樣本", item.valueText)
        assertFalse(item.valueText.contains("ms"))
        assertFalse(item.valueText.contains("壓力"))
        assertTrue(item.conclusion.contains("已收到 500 筆"))
        assertTrue(item.conclusion.contains("不在目前 10 分鐘分析窗"))
    }

    private fun preferences(values: Map<String, Any>): SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java)
    ) { _, method, args ->
        val key = args?.firstOrNull() as? String
        val value = key?.let(values::get)
        when (method.name) {
            "getFloat" -> value as? Float ?: args?.getOrNull(1) as? Float ?: Float.NaN
            "getInt" -> value as? Int ?: args?.getOrNull(1) as? Int ?: 0
            "getLong" -> value as? Long ?: args?.getOrNull(1) as? Long ?: 0L
            "getString" -> value as? String ?: args?.getOrNull(1) as? String
            "getBoolean" -> value as? Boolean ?: args?.getOrNull(1) as? Boolean ?: false
            "contains" -> key in values
            else -> null
        }
    } as SharedPreferences

    @Test
    fun ecgWithTooFewSamplesCannotReceivePositiveConclusionOrScore() {
        val item = HealthConclusionEngine.ecgConclusion(
            samples = 8,
            qualityScore = 95,
            hr = 72.0,
            rrCount = 4,
            titleRhythm = "規律",
            measuredAt = 9_000L,
            now = 10_000L,
            sampleRate = 50f,
            status = "has_data",
            wearQualified = true,
            waveformEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING,
            timelineSource = "SENSOR"
        )

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertNull(item.score)
        assertTrue(item.available)
        assertEquals("ECG 訊號不清楚，請重新量測", item.valueText)
        assertTrue(item.conclusion.contains("樣本數低於工程最低完整性門檻"))
    }

    @Test
    fun zeroQualityEcgKeepsRawCaptureButHidesDerivedBpmAndScore() {
        val item = HealthConclusionEngine.ecgConclusion(
            samples = 2_170,
            qualityScore = 0,
            hr = 165.03,
            rrCount = 14,
            titleRhythm = "無法判定",
            measuredAt = 9_000L,
            now = 10_000L
        )

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertNull(item.score)
        assertTrue(item.available)
        assertTrue(item.valueText.contains("尚無心律結果"))
        assertFalse(item.valueText.contains("165"))
        assertTrue(item.conclusion.contains("隱藏 bpm"))
        assertTrue(item.clinicalCompare.contains("來源：公開 Sensor HAL 原始通道"))
        assertTrue(item.clinicalCompare.contains("時間來源：UNKNOWN"))
        assertTrue(item.clinicalCompare.contains("時間軸：NORMALIZED_INDEX"))
        assertTrue(item.clinicalCompare.contains("振幅校準：未驗證"))
    }

    @Test
    fun qualifiedEcgEstimateRemainsResearchOnlyAndCannotImproveOverallScore() {
        val item = HealthConclusionEngine.ecgConclusion(
            samples = 2_170,
            qualityScore = 80,
            hr = 82.0,
            rrCount = 14,
            titleRhythm = "規律",
            measuredAt = 9_000L,
            now = 10_000L,
            sampleRate = 50f,
            status = "has_data",
            wearQualified = true,
            waveformEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING,
            timelineSource = "SENSOR"
        )

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertNull(item.score)
        assertTrue(item.valueText.contains("估算心率"))
        assertFalse(HealthConclusionSummaryPolicy.shouldShowScore(item))
    }

    @Test
    fun ecgBpmRequiresTheSameMappingClockQualityAndWearGateAsResultScreen() {
        val base = mapOf(
            "samples" to 2_170,
            "qualityScore" to 80,
            "hr" to 82.0,
            "rrCount" to 14,
            "titleRhythm" to "規律",
            "measuredAt" to 9_000L,
            "now" to 10_000L
        )
        fun evaluate(
            sampleRate: Float,
            status: String,
            wearQualified: Boolean?,
            waveformEncoding: String? = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING,
            timelineSource: String = "SENSOR"
        ) =
            HealthConclusionEngine.ecgConclusion(
                samples = base.getValue("samples") as Int,
                qualityScore = base.getValue("qualityScore") as Int,
                hr = base.getValue("hr") as Double,
                rrCount = base.getValue("rrCount") as Int,
                titleRhythm = base.getValue("titleRhythm") as String,
                measuredAt = base.getValue("measuredAt") as Long,
                now = base.getValue("now") as Long,
                sampleRate = sampleRate,
                status = status,
                wearQualified = wearQualified,
                waveformEncoding = waveformEncoding,
                timelineSource = timelineSource
            )

        val eligible = evaluate(50f, "has_data", true)
        assertTrue(eligible.valueText.contains("估算心率"))
        assertTrue(evaluate(50f, "has_data", true, timelineSource = "NORMALIZED_INDEX").valueText.contains("尚無心律結果"))
        assertTrue(evaluate(50f, "has_data", true, timelineSource = "ESTIMATED").valueText.contains("尚無心律結果"))
        assertTrue(evaluate(0f, "has_data", true).valueText.contains("尚無心律結果"))
        assertTrue(evaluate(50f, "no_events", true).valueText.contains("尚無心律結果"))
        assertTrue(evaluate(50f, "has_data", false).valueText.contains("尚無心律結果"))
        assertTrue(evaluate(50f, "has_data", true, EcgPayloadInterpretationPolicy.UNMAPPED_EVENT_PREVIEW).valueText.contains("尚無心律結果"))
        assertTrue(
            HealthConclusionEngine.ecgConclusion(
                samples = 2_170, qualityScore = 80, hr = 900.0, rrCount = 14,
                titleRhythm = "規律", measuredAt = 9_000L, now = 10_000L,
                sampleRate = 50f, status = "has_data", wearQualified = true,
                waveformEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
            ).valueText.contains("尚無心律結果")
        )
    }

    @Test
    fun recentHeartRateIsNotMisclassifiedAsRestingHeartRate() {
        val item = HealthConclusionEngine.heartRateItem(72.0)

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertFalse(item.clinicalCompare.contains("落在公開參考"))
        assertTrue(item.conclusion.contains("不是經確認的靜息心率"))
    }

    @Test
    fun restingHeartRateInBandIsGood() {
        val compare = ClinicalReferenceTables.hrVsResting(72.0)
        assertTrue(compare.contains("落在公開參考"))
    }

    @Test
    fun spo2NormalBandText() {
        val lines = ClinicalReferenceTables.spo2Compare(97.0)
        assertTrue(lines.any { it.contains("95") })
    }

    @Test
    fun skinTempBand() {
        assertTrue(ClinicalReferenceTables.WRIST_SKIN_TEMP_C.contains(34.5))
        assertFalse(ClinicalReferenceTables.WRIST_SKIN_TEMP_C.contains(30.0))
    }

    @Test
    fun unverifiedWatchTemperatureIsResearchOnlyAndNotScored() {
        val item = HealthConclusionEngine.temperatureResearchItem(38.4, 9_000L, "CT7117", 10_000L)

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertNull(item.score)
        assertTrue(item.available)
        assertTrue(item.valueText.contains("裝置原始值"))
        assertTrue(item.conclusion.contains("不是體溫"))
        assertFalse(item.valueText.contains("°C"))
    }

    @Test
    fun finiteTemperatureDeviceValueOutsideCelsiusRangeRemainsVisibleAsUnitlessRawData() {
        val item = HealthConclusionEngine.temperatureResearchItem(512.0, 9_000L, "private sensor", 10_000L)

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertTrue(item.available)
        assertTrue(item.valueText.contains("512.0"))
        assertTrue(item.valueText.contains("裝置原始值"))
        assertFalse(item.valueText.contains("°C"))
    }

    @Test
    fun staleWatchTemperatureRemainsVisibleAsHistoricalResearchData() {
        val item = HealthConclusionEngine.temperatureResearchItem(36.5, 1L, "CT7117", 24 * 60 * 60 * 1_000L + 2L)

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertTrue(item.available)
        assertTrue(item.valueText.contains("36.5"))
        assertTrue(item.clinicalCompare.contains("歷史量測時間"))
        assertNull(item.score)
    }

    @Test
    fun rawTemperatureWithoutTrustedMeasurementTimestampRemainsVisibleButUnrated() {
        val now = System.currentTimeMillis()
        val raw = preferences(mapOf(
            "body_temperature_raw_average" to 36.5f,
            "body_temperature_raw_time" to now,
            "body_temperature_raw_timestamp_source" to "watch_callback_wall_clock_fallback"
        ))

        val item = HealthConclusionEngine.temperatureConclusion(raw, preferences(emptyMap()))

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertTrue(item.available)
        assertTrue(item.valueText.contains("36.5"))
        assertTrue(item.clinicalCompare.contains("量測時間未知"))
        assertNull(item.score)
    }

    @Test
    fun temperaturePublicationTimeIsNotPresentedAsMeasurementTime() {
        val now = System.currentTimeMillis()
        val raw = preferences(mapOf(
            "body_temperature_raw_average" to 36.5f,
            "body_temperature_raw_time" to now,
            "body_temperature_raw_timestamp_source" to "watch_callback_wall_clock_fallback",
            "body_temperature_raw_published_at" to now
        ))

        val item = HealthConclusionEngine.temperatureConclusion(raw, preferences(emptyMap()))

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertTrue(item.valueText.contains("36.5"))
        assertTrue(item.clinicalCompare.contains("接收時間"))
        assertTrue(item.clinicalCompare.contains("不是量測時間"))
        assertFalse(item.clinicalCompare.contains("感測事件時間"))
        assertNull(item.score)
    }

    @Test
    fun rawTemperatureWithTrustedSensorTimestampRemainsUnitlessResearchData() {
        val now = System.currentTimeMillis()
        val raw = preferences(mapOf(
            "body_temperature_raw_average" to 36.5f,
            "body_temperature_raw_time" to now,
            "body_temperature_raw_timestamp_source" to "android_sensor_event_elapsed_realtime"
        ))

        val item = HealthConclusionEngine.temperatureConclusion(raw, preferences(emptyMap()))

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertTrue(item.available)
        assertTrue(item.valueText.contains("36.5"))
        assertFalse(item.valueText.contains("°C"))
    }

    @Test
    fun freshThermometerChannelAppearsInHomeTemperatureConclusion() {
        val now = System.currentTimeMillis()
        val raw = preferences(mapOf(
            "thermometer_latest" to "36.7",
            "thermometer_time" to now,
            "thermometer_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "thermometer_source_sensor" to "watch_thermometer_hal"
        ))

        val item = HealthConclusionEngine.temperatureConclusion(raw, preferences(emptyMap()))

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertTrue(item.available)
        assertTrue(item.valueText.contains("36.7"))
        assertTrue(item.valueText.contains("watch_thermometer_hal"))
        assertFalse(item.valueText.contains("°C"))
    }

    @Test
    fun newerRawTemperatureKeepsItsOwnSourceInsteadOfFeatureSource() {
        val now = System.currentTimeMillis()
        val raw = preferences(mapOf(
            "body_temperature_raw_average" to 36.5f,
            "body_temperature_raw_time" to now,
            "body_temperature_raw_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "body_temperature_raw_source_sensor" to "watch_raw_channel"
        ))
        val feature = preferences(mapOf(
            "temperature" to 35.8f,
            "temperature_timestamp" to now - 60_000L,
            "temperature_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "temperature_source" to "older_feature_channel"
        ))

        val item = HealthConclusionEngine.temperatureConclusion(raw, feature)

        assertTrue(item.valueText.contains("36.5"))
        assertTrue(item.valueText.contains("watch_raw_channel"))
        assertFalse(item.valueText.contains("older_feature_channel"))
    }

    @Test
    fun newerTrustedHealthServicesTemperatureTakesPrecedenceOverOlderRawChannel() {
        val now = System.currentTimeMillis()
        val raw = preferences(mapOf(
            "body_temperature_raw_average" to 36.5f,
            "body_temperature_raw_time" to now - 60_000L,
            "body_temperature_raw_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "body_temperature_raw_source_sensor" to "older_raw_channel"
        ))
        val feature = preferences(mapOf(
            "temperature" to 35.8f,
            "temperature_timestamp" to now,
            "temperature_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "temperature_source" to "Health Services"
        ))

        val item = HealthConclusionEngine.temperatureConclusion(raw, feature)

        assertTrue(item.valueText.contains("35.8"))
        assertTrue(item.valueText.contains("Health Services"))
        assertFalse(item.valueText.contains("older_raw_channel"))
        assertNull(item.score)
    }

    @Test
    fun healthConnectSpO2ReferenceRetainsPlausibleHistoricalValuesWithoutFreshnessClaim() {
        assertTrue(HealthConclusionEngine.isValidSpO2Reference(96f, 1_000L, "com.example.source"))
        assertTrue(HealthConclusionEngine.isValidSpO2Reference(96f, 1L, "com.example.source"))
        assertFalse(HealthConclusionEngine.isValidSpO2Reference(101f, 1_000L, "com.example.source"))
        assertFalse(HealthConclusionEngine.isValidSpO2Reference(96f, System.currentTimeMillis() + 60_000L, "com.example.source"))
        assertFalse(HealthConclusionEngine.isValidSpO2Reference(96f, 1_000L, ""))
    }

    @Test
    fun unknownPpgTimebaseIsShownAsCapturedResearchDataNotFailedOpticalSignal() {
        val item = HealthConclusionEngine.ppgConclusion(
            status = "invalid_optical",
            reason = "PPG 取樣時鐘未提供；只保留原始資料",
            samples = 76,
            eventCount = 16,
            qualityScore = 25,
            sampleRateHz = 0f,
            measuredAt = 9_000L,
            piPercent = Float.NaN,
            pulseBpm = 72f,
            now = 10_000L,
            wearQualified = true,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertEquals("已取得光學訊號，尚無脈率結果", item.valueText)
        assertTrue(item.available)
        assertTrue(item.clinicalCompare.contains("封包 16"))
        assertTrue(item.conclusion.contains("不能換算 bpm、HRV"))
        assertTrue(item.conclusion.contains("這不是量測失敗"))
    }

    @Test
    fun ppgUnknownTimestampKeepsIndexMetricsAndIsNotMislabelledAsHistorical() {
        val item = HealthConclusionEngine.ppgConclusion(
            status = "research_signal_unscaled",
            reason = "",
            samples = 128,
            eventCount = 32,
            qualityScore = 0,
            sampleRateHz = 0f,
            measuredAt = 0L,
            piPercent = Float.NaN,
            pulseBpm = 72f,
            now = 10_000L,
            wearQualified = true,
            indexPeriodSamples = 40,
            periodCorrelation = 0.91,
            confidenceScore = 60,
            source = "PPG HAL",
            timestampSource = "UNKNOWN"
        )

        assertTrue(item.valueText.contains("時間未確認"))
        assertTrue(item.clinicalCompare.contains("最大自相關 0.91"))
        assertFalse(item.valueText.contains("歷史"))
        assertFalse(item.valueText.contains("bpm"))
        assertTrue(item.clinicalCompare.contains("NORMALIZED_INDEX"))
        assertTrue(item.clinicalCompare.contains("最大自相關 0.91"))
        assertTrue(item.clinicalCompare.contains("資料完整度 60/100"))
    }

    @Test
    fun ppgSummaryRecomputesIndexPeriodFromSavedWaveformWithoutInventingBpm() {
        val waveform = List(512) { index ->
            (1_000.0 * kotlin.math.sin(2.0 * Math.PI * index / 40.0)).toFloat()
        }.joinToString(",")
        val raw = preferences(mapOf(
            "ppg_status" to "research_signal_unscaled",
            "ppg_raw_value_count" to 512,
            "ppg_event_count" to 128,
            "ppg_quality_score" to 0,
            "ppg_sample_rate" to 0f,
            "ppg_wear_qualified" to true,
            "ppg_channel_mapping_verified" to false,
            "ppg_time" to System.currentTimeMillis(),
            "ppg_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "ppg_confidence_score" to 72,
            "ppg_source_sensor" to "MAX86178 raw optical",
            "ppg_display_waveform" to waveform
        ))

        val item = HealthConclusionEngine.ppgConclusion(raw)

        assertTrue(item.valueText == "已取得光學訊號，尚無脈率結果")
        assertTrue(item.clinicalCompare.contains("最大自相關"))
        assertTrue(item.clinicalCompare.contains("品質 未評分"))
        assertTrue(item.clinicalCompare.contains("資料完整度 72/100"))
        assertTrue(item.clinicalCompare.contains("來源：MAX86178 raw optical"))
        assertTrue(item.clinicalCompare.contains("時間：android_sensor_event_elapsed_realtime"))
        assertTrue(item.conclusion.contains("不能換算 bpm"))
        assertFalse(item.valueText.contains("bpm"))
    }

    @Test
    fun stalePpgRetainsEvidenceWithoutCurrentPhysiologyOrScore() {
        val item = HealthConclusionEngine.ppgConclusion(
            status = "research_signal_unscaled",
            reason = "",
            samples = 76,
            eventCount = 16,
            qualityScore = 95,
            sampleRateHz = 100f,
            channelMappingVerified = true,
            wearQualified = true,
            measuredAt = 1L,
            piPercent = 3f,
            pulseBpm = 72f,
            now = 10 * 60 * 1_000L + 2L,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )

        assertRetainedResearchExcludedFromHealthSummary(item)
        assertEquals("較早的光學訊號紀錄", item.valueText)
        assertFalse(item.valueText.contains("bpm"))
        assertFalse(item.clinicalCompare.contains("PI "))
        assertTrue(item.conclusion.contains("超出健康結論 10 分鐘分析輸入窗"))
    }

    @Test
    fun unknownPpgTimebaseNeverUsesCachedPulseOrPerfusionValues() {
        val item = HealthConclusionEngine.ppgConclusion(
            status = "research_optical",
            reason = "",
            samples = 100,
            eventCount = 20,
            qualityScore = 85,
            sampleRateHz = 25f,
            measuredAt = 9_000L,
            piPercent = 3f,
            pulseBpm = 72f,
            now = 10_000L,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertEquals("已取得光學訊號，尚無脈率結果", item.valueText)
        assertTrue(item.clinicalCompare.contains("不計算脈率"))
    }

    @Test
    fun oldHealthConnectSpO2RemainsVisibleWithoutCurrentClassificationOrScore() {
        val oldTime = 1L
        val raw = preferences(mapOf(
            "spo2_trusted_percent" to 96f,
            "spo2_trusted_time" to oldTime,
            "spo2_trusted_source" to "health_connect",
            "spo2_trusted_origin" to "test.provider"
        ))

        val item = HealthConclusionEngine.spo2Conclusion(raw)

        assertEquals("96% · 已記錄", item.valueText)
        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertNull(item.score)
        assertTrue(item.clinicalCompare.contains("新鮮度規則尚未設定"))
        assertTrue(item.conclusion.contains("不標示為目前值"))
    }

    @Test
    fun ppgInsideDisplayWindowButOutsideConclusionWindowIsRetainedAsRawOnly() {
        val now = 60 * 60 * 1_000L
        val measuredAt = now - 15 * 60 * 1_000L
        val displayState = SensorDisplayState.from(
            samples = 76,
            status = "research_signal_unscaled",
            measuredAt = measuredAt,
            now = now,
            freshnessMs = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS,
            kind = "ppg",
            timestampSource = "android_sensor_event_elapsed_realtime"
        )
        val conclusion = HealthConclusionEngine.ppgConclusion(
            status = "research_signal_unscaled",
            reason = "",
            samples = 76,
            eventCount = 16,
            qualityScore = 25,
            sampleRateHz = 0f,
            measuredAt = measuredAt,
            piPercent = Float.NaN,
            pulseBpm = Float.NaN,
            now = now,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )

        assertEquals(SensorDisplayState.CALIBRATION_MISSING, displayState)
        assertRetainedResearchExcludedFromHealthSummary(conclusion)
        assertEquals("較早的光學訊號紀錄", conclusion.valueText)
        assertFalse(conclusion.valueText.contains("bpm"))
        assertTrue(conclusion.conclusion.contains("超出健康結論 10 分鐘分析輸入窗"))
    }

    @Test
    fun validLookingPpgStillCannotShowDerivedPulseWithoutVerifiedChannelMap() {
        val item = HealthConclusionEngine.ppgConclusion(
            status = "valid_signal",
            reason = "",
            samples = 128,
            eventCount = 32,
            qualityScore = 90,
            sampleRateHz = 100f,
            measuredAt = 9_000L,
            piPercent = 2f,
            pulseBpm = 72f,
            now = 10_000L,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertEquals("已取得光學訊號，尚無脈率結果", item.valueText)
        assertTrue(item.clinicalCompare.contains("通道映射未驗證"))
    }

    @Test
    fun zeroPpgQualityIsDisplayedAsUnscoredAndDoesNotExposePulse() {
        val item = HealthConclusionEngine.ppgConclusion(
            status = "valid_signal",
            reason = "",
            samples = 128,
            eventCount = 32,
            qualityScore = 0,
            sampleRateHz = 50f,
            channelMappingVerified = true,
            measuredAt = 9_000L,
            piPercent = 2f,
            pulseBpm = 70f,
            now = 10_000L,
            wearQualified = true,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )

        assertTrue(item.clinicalCompare.contains("品質指標 未評分"))
        assertFalse(item.clinicalCompare.contains("0/100"))
        assertFalse(item.valueText.contains("bpm"))
    }

    @Test
    fun validLookingPpgExplainsUnknownOrOffWristInsteadOfClaimingQualityPassed() {
        val unknownWear = HealthConclusionEngine.ppgConclusion(
            status = "valid_signal", reason = "", samples = 128, eventCount = 32,
            qualityScore = 90, sampleRateHz = 100f, channelMappingVerified = true,
            measuredAt = 9_000L, piPercent = 2f, pulseBpm = 72f, now = 10_000L,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )
        val offWrist = HealthConclusionEngine.ppgConclusion(
            status = "valid_signal", reason = "", samples = 128, eventCount = 32,
            qualityScore = 90, sampleRateHz = 100f, channelMappingVerified = true,
            measuredAt = 9_000L, piPercent = 2f, pulseBpm = 72f, now = 10_000L,
            wearQualified = false, timestampSource = "android_sensor_event_elapsed_realtime"
        )

        listOf(unknownWear, offWrist).forEach { item ->
            assertEquals("已取得光學訊號，尚無脈率結果", item.valueText)
            assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
            assertTrue(item.clinicalCompare.contains("佩戴狀態未確認"))
            assertTrue(item.conclusion.contains("佩戴狀態未知或判定離腕"))
            assertFalse(item.valueText.contains("bpm"))
        }
    }

    @Test
    fun staleRawSpo2RetainsHistoricalEvidenceWithoutCurrentPercentageOrScore() {
        val item = HealthConclusionEngine.rawSpO2Item(
            samples = 512,
            measuredAt = 1_000L,
            timestampSource = "android_sensor_event_elapsed_realtime",
            now = 11 * 60 * 1_000L
        )

        assertRetainedResearchExcludedFromHealthSummary(item)
        assertEquals("512 筆歷史原始值（無 %）", item.valueText)
        assertFalse(Regex("\\d+\\s*%").containsMatchIn(item.valueText))
        assertTrue(item.conclusion.contains("超出健康結論 10 分鐘分析輸入窗"))
    }

    @Test
    fun recentRawSpo2IsResearchDataButNeverAnOxygenPercentage() {
        val item = HealthConclusionEngine.rawSpO2Item(
            samples = 512,
            measuredAt = 9 * 60 * 1_000L,
            timestampSource = "android_sensor_event_elapsed_realtime",
            now = 10 * 60 * 1_000L
        )

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertTrue(item.available)
        assertEquals("512 筆原始值（無 %）", item.valueText)
        assertFalse(Regex("\\d+\\s*%").containsMatchIn(item.valueText))
        assertTrue(item.conclusion.contains("無裝置級校準"))
        assertResearchExcludedFromHealthSummary(item)
    }

    @Test
    fun rawSpo2ConclusionShowsEventsAndIndexWaveformWhenDecodedSampleCountIsZero() {
        val waveform = (0 until 128).joinToString(",") { index ->
            kotlin.math.sin(index * 0.2).toFloat().toString()
        }
        val raw = preferences(mapOf(
            "spo2_raw_value_count" to 0,
            "spo2_raw_event_count" to 8,
            "spo2_raw_time" to System.currentTimeMillis(),
            "spo2_raw_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "spo2_raw_waveform" to waveform,
            "spo2_raw_source_sensor" to "research_optical"
        ))

        val item = HealthConclusionEngine.spo2Conclusion(raw)

        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertTrue(item.available)
        assertTrue(item.valueText.contains("原始樣本 0"))
        assertTrue(item.valueText.contains("原始事件 8"))
        assertTrue(item.valueText.contains("預覽有效點 128/128"))
        assertTrue(item.valueText.contains("索引形狀品質"))
        assertTrue(item.clinicalCompare.contains("NORMALIZED_INDEX"))
        assertTrue(item.clinicalCompare.contains("research_optical"))
        assertFalse(Regex("\\d+\\s*%").containsMatchIn(item.valueText))
        assertResearchExcludedFromHealthSummary(item)
    }

    @Test
    fun finiteZeroInSpo2PreviewCountsAsEvidenceNotMissing() {
        val assessment = Spo2RawQualityPolicy.assess(
            List(32) { index -> if (index == 0) 0f else kotlin.math.sin(index * 0.3).toFloat() },
            0f
        )

        assertEquals(32, assessment.finiteSampleCount)
        assertEquals(32, assessment.totalSampleCount)
        assertEquals(100, assessment.finiteCoveragePercent)
    }

    @Test
    fun sixSavedPreferenceFixturesReachCardPresentationWithResearchProvenance() {
        val now = System.currentTimeMillis()
        val timestampSource = "android_sensor_event_elapsed_realtime"
        val waveform = List(128) { index -> kotlin.math.sin(index * 0.2).toFloat() }
        val encodedWaveform = waveform.joinToString(",")
        val raw = preferences(mapOf(
            "spo2_raw_value_count" to 0,
            "spo2_raw_event_count" to 8,
            "spo2_raw_time" to now,
            "spo2_raw_timestamp_source" to timestampSource,
            "spo2_raw_waveform" to encodedWaveform,
            "spo2_raw_source_sensor" to "research_optical",
            "ecg_raw_value_count" to 128,
            "ecg_event_count" to 8,
            "ecg_quality_score" to 72,
            "ecg_time" to now,
            "ecg_timestamp_source" to timestampSource,
            "ecg_sample_rate" to 0f,
            "ecg_status" to "research_signal_unscaled",
            "ecg_research_timeline_source" to "NORMALIZED_INDEX",
            "bioz_raw_value_count" to 128,
            "bioz_time" to now,
            "bioz_timestamp_source" to timestampSource,
            "bioz_display_waveform" to encodedWaveform,
            "body_temperature_raw_average" to 33.28f,
            "body_temperature_raw_time" to now,
            "body_temperature_raw_timestamp_source" to timestampSource,
            "body_temperature_raw_status" to "has_data",
            "body_temperature_raw_value_count" to 8,
            "ppg_raw_value_count" to 128,
            "ppg_event_count" to 8,
            "ppg_quality_score" to 72,
            "ppg_time" to now,
            "ppg_sample_rate" to 0f,
            "ppg_status" to "research_signal_unscaled",
            "ppg_timestamp_source" to "UNKNOWN",
            "ppg_display_waveform" to encodedWaveform
        ))
        val feature = preferences(mapOf(
            "whs_heart_rate" to 67f,
            "whs_heart_rate_timestamp" to now,
            "whs_heart_rate_timestamp_source" to timestampSource
        ))
        val results = HealthConclusionEngine.buildCoreSensorItems(raw, feature)
        val displays = results.map(ConsumerMetricPresentation::from)

        assertEquals(listOf("心率", "血氧", "心電圖", "腕部溫度", "體成分", "脈搏"), displays.map { it.title })
        displays.forEachIndexed { index, display ->
            assertTrue("${display.title} must show a result", display.value.isNotBlank() && display.value != "待量測")
            assertTrue("${display.title} distinguishes a captured signal from a health reading", display.state.startsWith("尚無") || display.state == "溫度單位未確認")
            assertEquals("${display.title} result exists", true, results[index].available)
        }
        assertEquals("已收到血氧訊號", displays[1].value)
        assertFalse(Regex("\\d+\\s*%").containsMatchIn(displays[1].value))
        assertEquals("已收到心電訊號", displays[2].value)
        assertEquals("已收到體成分訊號", displays[4].value)
        assertEquals("已收到脈搏訊號", displays[5].value)
        assertTrue(results[5].clinicalCompare.contains("校準：未驗證"))
    }

    @Test
    fun oneSensorExceptionDoesNotHideOtherFiveCoreCards() {
        val sensorIds = listOf("hr", "spo2", "ecg", "temp", "bioz", "ppg")
        sensorIds.forEach { failedId ->
            val items = HealthConclusionEngine.buildCoreSensorItems(
                preferences(emptyMap()),
                preferences(emptyMap()),
                safeBuild = { id, title, calculate ->
                    HealthConclusionEngine.buildSafely(id, title) {
                        if (id == failedId) error("isolated sensor failure")
                        calculate()
                    }
                }
            )

            assertEquals("all six production core cards remain present", sensorIds.size, items.size)
            assertEquals("$failedId failure is isolated", 1, items.count {
                it.valueText == "分析暫時不可用" && it.clinicalCompare.contains("IllegalStateException")
            })
            assertEquals("other five sensor calculations execute", 5, items.count { it.id != failedId })
            assertEquals("all six cards remain present", 6, items.size)
        }
    }

    @Test
    fun recentRawSpo2WithUntrustedTimestampIsNotPresentedAsRecent() {
        val item = HealthConclusionEngine.rawSpO2Item(
            samples = 512,
            measuredAt = 9 * 60 * 1_000L,
            timestampSource = "watch_sample_timestamp_source_unreported",
            now = 10 * 60 * 1_000L
        )

        assertEquals("512 筆原始值（時間未確認，無 %）", item.valueText)
        assertTrue(item.conclusion.contains("時間來源未驗證"))
        assertFalse(item.conclusion.contains("已有 SpO₂ 原始通道事件"))
        assertResearchExcludedFromHealthSummary(item)
    }

    private fun assertRetainedResearchExcludedFromHealthSummary(item: HealthConclusionEngine.Item) {
        assertNull(item.score)
        assertResearchExcludedFromHealthSummary(item)
    }

    private fun assertResearchExcludedFromHealthSummary(item: HealthConclusionEngine.Item) {
        assertEquals(HealthConclusionEngine.Level.RESEARCH, item.level)
        assertTrue(item.available)
        assertFalse(HealthConclusionSummaryPolicy.shouldShowScore(item))
        val summary = HealthConclusionSummaryPolicy.summarize(listOf(item), 1)
        assertEquals("目前只有研究級原始訊號，沒有足夠可對照的生理數值", summary.first)
        assertEquals("可對照生理數值 0/1 項；歷史／未分級生理紀錄 0 項；研究級原始訊號 1 項。良好 0 · 觀察 0 · 需留意 0。非醫療診斷。", summary.second)
        val comparable = HealthConclusionEngine.Item(
            "bp", "袖帶", "120 / 80 mmHg", "參考", "單次讀值非診斷",
            HealthConclusionEngine.Level.WATCH, 60, true
        )
        val mixed = HealthConclusionSummaryPolicy.summarize(listOf(comparable, item), 2)
        assertEquals(HealthConclusionSummaryPolicy.summarize(listOf(comparable), 2).first, mixed.first)
        assertEquals("可對照生理數值 1/2 項；歷史／未分級生理紀錄 0 項；研究級原始訊號 1 項。良好 0 · 觀察 1 · 需留意 0。非醫療診斷。", mixed.second)
    }

    @Test
    fun maxHrvFallbackCannotScoreOffWristOrRejectedSignal() {
        val now = System.currentTimeMillis()
        val base = mapOf(
            "max_hr_time" to now,
            "max_hr_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "max_hr_channel_mapping_verified" to true,
            "max_hr_quality_score" to 95,
            "max_hr_sample_rate" to 100f,
            "max_hr_raw_value_count" to 128,
            "max_hr_formula_rmssd_ms" to 42f
        )
        listOf(
            mapOf("max_hr_status" to "valid_signal", "max_hr_wear_qualified" to false),
            mapOf("max_hr_status" to "invalid_optical", "max_hr_wear_qualified" to true)
        ).forEach { qualification ->
            val item = HealthConclusionEngine.hrvConclusion(preferences(base + qualification))

            assertRetainedResearchExcludedFromHealthSummary(item)
            assertFalse(item.valueText.contains("RMSSD"))
            assertFalse(item.valueText.contains("ms"))
        }
    }

    @Test
    fun qualifiedPpgWithoutRestingEvidenceCannotReceiveRestingClassificationOrScore() {
        val item = HealthConclusionEngine.ppgConclusion(
            status = "valid_signal", reason = "", samples = 128, eventCount = 32,
            qualityScore = 95, sampleRateHz = 100f, channelMappingVerified = true,
            wearQualified = true, measuredAt = 9_000L, piPercent = 2f,
            pulseBpm = 72f, now = 10_000L,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )

        assertResearchExcludedFromHealthSummary(item)
        assertFalse(item.clinicalCompare.contains("落在公開參考"))
    }
}
