package com.jaysato.xiaomihealthbridge

import org.junit.Assert.*
import org.junit.Test

class ClinicalVerdictTest {
    @Test
    fun ecgResearchChartLabelsIndexTimeAndCalibrationScale() {
        assertTrue(EcgResearchDisplay.chartAxisLabel(false).contains("NORMALIZED_INDEX"))
        assertTrue(EcgResearchDisplay.chartAxisLabel(false).contains("未校準"))
        assertTrue(EcgResearchDisplay.chartAxisLabel(true).contains("外部參考研究 mV 映射"))
        assertTrue(EcgResearchDisplay.chartAxisLabel(true).contains("非秒"))
    }

    @Test
    fun ppgIndexPeriodicityIsVisibleWithoutClaimingPulseRate() {
        val result = ClinicalVerdict.forPpg(
            qualityScore = 70,
            sampleCount = 128,
            sampleRate = 0f,
            status = "research_signal_unscaled",
            wearQualified = true,
            indexPeriodSamples = 24,
            periodCorrelation = 0.82
        )

        assertTrue(result.findings.any { it.contains("24 點／週期") && it.contains("非秒、非脈率") })
        assertTrue(result.findings.any { it.contains("PPG 波形指標 70/100") && it.contains("臨床品質") })
        assertTrue(result.findings.any { it.contains("動作干擾：未評分") && it.contains("未知不代表 0") })
        assertFalse(result.findings.any { it.contains("bpm") })
    }

    @Test
    fun relativePpgAcDcIsDisplayedAsUnitlessResearchEvidenceOnly() {
        val result = ClinicalVerdict.forPpg(
            qualityScore = 0,
            sampleCount = 128,
            sampleRate = 0f,
            status = "research_signal_unscaled",
            wearQualified = true,
            relativeAcDcRatio = 0.0421
        )

        assertTrue(result.findings.any {
            it.contains("相對 AC/DC 0.0421") && it.contains("無單位研究描述指標")
        })
        assertFalse(result.findings.any { it.contains("PI 0.0421") || it.contains("灌流指數 0.0421") })
        assertFalse(result.findings.any { it.contains("bpm") })
    }

    @Test
    fun suiteCannotPromoteUnscoredRawPpgIntoExcellentQuality() {
        val ppg = ClinicalVerdict.forPpg(99, 128, 0f, "research_signal_unscaled", true)
        val suite = ClinicalVerdict.forSuite(1, 36, true, ppg, null, null, null)
        assertNull(suite.score)
        assertEquals("研究資料", suite.grade)
        assertTrue(suite.findings.any { it.contains("尚無可計分通道") })
    }

    @Test
    fun unknownPpgClockAndIndependentHeartRateRemainDistinct() {
        val verdict = ClinicalVerdict.forPpg(25, 128, 0f, "research_signal_unscaled", true,
            healthServicesHeartRateBpm = 78.0)
        assertTrue(verdict.findings.any { it.contains("取樣率未知") })
        assertTrue(verdict.findings.any { it.contains("PPG 波形指標 25/100") })
        assertTrue(verdict.findings.any { it.contains("Health Services 心率 78 bpm") })
        assertFalse(verdict.findings.any { it.contains("取樣率 0.0 Hz") })
        val invalid = ClinicalVerdict.forPpg(25, 128, 0f, "research_signal_unscaled", true,
            healthServicesHeartRateBpm = Double.NaN)
        assertFalse(invalid.findings.any { it.contains("Health Services 心率") })
    }

    @Test
    fun ppgResearchWaveformIsShownWithoutClaimingPhysiologicalUnits() {
        val verdict = ClinicalVerdict.forPpg(80, 128, 50f, "research_optical", true)
        assertTrue(verdict.headline.contains("工程檢視"))
        assertTrue(verdict.findings.any { it.contains("PPG 波形指標 80/100") && it.contains("臨床品質") })
        assertTrue(verdict.recommendation.contains("手錶心率量測"))
        assertEquals("—", verdict.grade)
    }

    @Test
    fun researchOpticalPulseEstimateIsDisplayedOnlyWhenAllResearchGatesPass() {
        val usable = ClinicalVerdict.forPpg(70, 128, 50f, "research_optical", true, 72.0)
        assertTrue(usable.headline.contains("研究估算脈率 72 bpm"))
        assertTrue(usable.findings.any { it.contains("72 bpm") && it.contains("研究估計") })
        assertEquals("—", usable.grade)
        assertTrue(usable.recommendation.contains("醫療量測"))

        val badQuality = ClinicalVerdict.forPpg(49, 128, 50f, "research_optical", true, 72.0)
        val badWear = ClinicalVerdict.forPpg(70, 128, 50f, "research_optical", false, 72.0)
        val invalidRate = ClinicalVerdict.forPpg(70, 128, 50f, "research_optical", true, 250.0)
        assertFalse(badQuality.findings.any { it.contains("72 bpm") && it.contains("研究估計") })
        assertFalse(badWear.findings.any { it.contains("72 bpm") && it.contains("研究估計") })
        assertFalse(invalidRate.findings.any { it.contains("250 bpm") })
    }

    @Test
    fun disclaimerAlwaysPresent() {
        val v = ClinicalVerdict.forPpg(80, 128, 50f, "valid_signal", true, 72.0)
        assertTrue(v.disclaimer.contains("非醫療用途"))
        assertEquals("良好", v.grade)
        assertTrue(v.headline.contains("訊號品質良好"))
        assertTrue(v.recommendation.contains("日常脈搏波形參考"))
        assertTrue(v.clinicalComparisons.isNotEmpty())
    }

    @Test
    fun ppgRawDataStatusCannotMasqueradeAsValidPulseWaveform() {
        val v = ClinicalVerdict.forPpg(95, 512, 0f, "has_data", true, 72.0)
        assertEquals("—", v.grade)
        assertTrue(v.headline.contains("尚無可解讀的脈率"))
        assertTrue(v.findings.any { it.contains("未評分") || it.contains("不適用") })
        assertFalse(v.findings.any { it.contains("72 bpm") })
        assertFalse(v.clinicalComparisons.any { it.contains("72.0 bpm") })
    }

    @Test
    fun ppgRawSamplesWithoutVerifiedClockAreNotReportedAsMissingAcquisition() {
        val verdict = ClinicalVerdict.forPpg(25, 128, 0f, "research_signal_unscaled", true)
        assertTrue(verdict.headline.contains("尚無可解讀的脈率"))
        assertTrue(verdict.recommendation.contains("手錶心率量測"))
        assertEquals("—", verdict.grade)
    }

    @Test
    fun weakPpgIsNotGood() {
        val v = ClinicalVerdict.forPpg(20, 10, 1f, "invalid_optical", false)
        assertEquals("—", v.grade)
        assertTrue(v.headline.contains("未貼合") || v.headline.contains("未取得"))
    }

    @Test
    fun ecgUnknownTimebaseCannotShowCachedQualityGradeOrRr() {
        val v = ClinicalVerdict.forEcg(95, 512, 0f, "has_data", 72.0, 12, true)
        assertEquals("—", v.grade)
        assertTrue(v.headline.contains("尚無可判讀"))
        assertTrue(v.findings.any { it.contains("未評分") || it.contains("不適用") })
        assertTrue(v.findings.any { it.contains("忽略舊 RR") })
    }

    @Test
    fun biozDoesNotTreatLegacyRawValuesAsOhms() {
        val v = ClinicalVerdict.forBioz(70, 650.0, 640.0, 40.0, 55, "calibrated_research")
        assertFalse(v.findings.any { it.contains("|Z|") || it.contains("650") || it.contains("55/100") })
        assertTrue(v.disclaimer.contains("本程式提供參考"))
        assertTrue(v.clinicalComparisons.any { it.contains("尚未驗證") })
    }

    @Test
    fun segmentalBiozHidesHydrationClaim() {
        val v = ClinicalVerdict.forBioz(70, 3740.0, 3739.0, 13.0, null, "calibrated_research")
        assertTrue(v.findings.any { it.contains("未輸出") || it.contains("腕段") })
        assertTrue(v.headline.contains("尚未收到 BioZ 原始訊號"))
    }

    @Test
    fun biozRawSignalIsShownWhilePhysicalImpedanceRemainsBlocked() {
        val verdict = ClinicalVerdict.forBioz(
            45, null, null, null, null, "research_signal_unscaled", rawSampleCount = 96, confidenceScore = 58
        )
        assertTrue(verdict.headline.contains("BioZ 原始波形參考"))
        assertTrue(verdict.findings.any { it.contains("原始波形點數 96") })
        assertTrue(verdict.findings.any { it.contains("研究證據信心指數：58/100") })
        assertTrue(verdict.findings.any { it.contains("裝置級單位尚未驗證") || it.contains("單位尚未驗證") })
        assertTrue(verdict.findings.any { it.contains("校準狀態：未校準") && it.contains("不是本手錶") })
        assertTrue(verdict.findings.any { it.contains("NORMALIZED_INDEX") && it.contains("量測時間未確認") })
        assertFalse(verdict.findings.any { it.contains("校準來源：MAX86178") })
        assertTrue(verdict.recommendation.contains("無法可靠換算阻抗、體脂或體水"))
    }

    @Test
    fun biozWithoutMappedTimelineDescribesRawFieldsInsteadOfWaveformMetrics() {
        val verdict = ClinicalVerdict.forBioz(
            qualityScore = 0,
            impedanceOhm = null,
            resistanceOhm = null,
            reactanceOhm = null,
            hydrationIndex = null,
            status = "research_signal_unscaled",
            rawSampleCount = 0,
            hasReceivedSignal = true,
            rawChannelValues = listOf(0 to 123.0, 1 to 456.0)
        )

        assertTrue(verdict.headline.contains("BioZ 原始波形參考"))
        assertTrue(verdict.findings.any { it.contains("欄位到時間樣本映射未驗證") })
        assertTrue(verdict.findings.any { it.contains("不跨欄位計算波形指標") })
        assertTrue(verdict.findings.any { it.contains("通道 0=123.00000") })
        assertNull(verdict.score)
        assertFalse(verdict.findings.any { it.contains("0/100") })
    }

    @Test
    fun biozRespirationShowsCurrentWaveformResearchMetricsWithoutInventingRate() {
        val research = BiomedicalCalibration.calibrateBioz(
            emptyList(), List(96) { index -> (100 + (index % 12) * 3).toFloat() }
        )
        val verdict = ClinicalVerdict.forBiozRespiration(
            research = research,
            rawSampleCount = 96,
            hasReceivedSignal = true,
            status = "research_signal_unscaled",
            source = "watch_bioz_respiration",
            measuredAtMillis = 0L,
            timestampSource = "UNKNOWN",
            wearQualified = true
        )

        assertEquals("呼吸研究（BioZ）", verdict.title)
        assertTrue(verdict.findings.any { it.contains("NORMALIZED_INDEX") })
        assertTrue(verdict.findings.any { it.contains("相對差") })
        assertTrue(verdict.findings.any { it.contains("研究證據信心指數") })
        assertTrue(verdict.findings.any { it.contains("來源：watch_bioz_respiration") })
        assertTrue(verdict.headline.contains("未輸出呼吸率"))
        assertFalse(verdict.findings.any { it.matches(Regex(".*呼吸率\\s+\\d+.*")) })
    }

    @Test
    fun emptyBiozRespirationDoesNotShowZeroAsAQualityScore() {
        val verdict = ClinicalVerdict.forBiozRespiration(
            research = BiomedicalCalibration.calibrateBioz(emptyList(), emptyList()),
            rawSampleCount = 0,
            hasReceivedSignal = false,
            status = "not_measured",
            source = "UNKNOWN",
            measuredAtMillis = 0L,
            timestampSource = "UNKNOWN",
            wearQualified = null
        )

        assertNull(verdict.score)
        assertTrue(verdict.findings.any { it.contains("波形變化覆蓋分數 未評分") })
    }

    @Test
    fun biozWithoutRawSignalDoesNotExposeFallbackQualityAsScore() {
        val verdict = ClinicalVerdict.forBioz(
            qualityScore = 85,
            impedanceOhm = null,
            resistanceOhm = null,
            reactanceOhm = null,
            hydrationIndex = null,
            status = "not_measured",
            rawSampleCount = 0,
            hasReceivedSignal = false
        )

        assertNull(verdict.score)
        assertTrue(verdict.headline.contains("尚未收到 BioZ 原始訊號"))
        assertTrue(verdict.findings.any { it.contains("覆蓋分數 未評分（未收到訊號）") })
    }

    @Test
    fun ppgWithoutRawSignalDoesNotExposeFallbackQualityAsScore() {
        val verdict = ClinicalVerdict.forPpg(
            qualityScore = 85,
            sampleCount = 0,
            sampleRate = 0f,
            status = "not_measured",
            wearQualified = null,
            hasReceivedSignal = false
        )

        assertNull(verdict.score)
        assertTrue(verdict.findings.any { it.contains("品質分數：未評分") })
    }

    @Test
    fun ecgWithoutAnalyzableWaveformDoesNotExposeFallbackQualityAsScore() {
        val verdict = ClinicalVerdict.forEcg(
            qualityScore = 85,
            sampleCount = 0,
            sampleRate = 0f,
            status = "not_measured",
            heartRateBpm = null,
            rrCount = 0,
            wearQualified = null
        )

        assertNull(verdict.score)
        assertEquals("—", verdict.grade)
        assertTrue(verdict.findings.any { it.contains("品質分數：未評分") })
    }

    @Test
    fun biozSegmentDifferenceIsNotMislabelledAsAcrossSessionTrend() {
        val verdict = ClinicalVerdict.forBioz(
            45, null, null, null, null, "research_signal_unscaled",
            rawSampleCount = 96, relativeChangePercent = 12.5
        )
        assertTrue(verdict.findings.any {
            it.contains("首尾四分位區段相對差") && it.contains("整段波形跨度") && it.contains("非跨次趨勢")
        })
    }

    @Test
    fun biozKeepsResearchComparisonsAndDoesNotRepeatCoverageScore() {
        val verdict = ClinicalVerdict.forBioz(
            qualityScore = 45,
            impedanceOhm = null,
            resistanceOhm = null,
            reactanceOhm = null,
            hydrationIndex = null,
            status = "research_signal_unscaled",
            comparisons = listOf("合成研究比較：時間證據未提供"),
            rawSampleCount = 96,
            stabilityScore = 62
        )

        assertTrue(verdict.clinicalComparisons.any { it == "合成研究比較：時間證據未提供" })
        assertTrue(verdict.clinicalComparisons.any { it.contains("HAL 原始欄位尚未驗證") })
        assertEquals(1, verdict.findings.count { it.contains("波形變化覆蓋分數") })
        assertTrue(verdict.findings.any { it.contains("波形相對離散度 62%") && it.contains("非時間穩定度") })
    }

    @Test
    fun biozRequiresActualTrustedMeasurementTimeBeforeShowingSensorTimestamp() {
        val missingTime = ClinicalVerdict.forBioz(
            45, null, null, null, null, "research_signal_unscaled",
            rawSampleCount = 96,
            timestampSource = "android_sensor_event_elapsed_realtime",
            measuredAtMillis = 0L
        )
        assertTrue(missingTime.findings.any { it.contains("NORMALIZED_INDEX") && it.contains("量測時間未確認") })

        val freshTime = ClinicalVerdict.forBioz(
            45, null, null, null, null, "research_signal_unscaled",
            rawSampleCount = 96,
            timestampSource = "android_sensor_event_elapsed_realtime",
            source = "bioz_hal",
            measuredAtMillis = System.currentTimeMillis() - 1_000L
        )
        assertTrue(freshTime.findings.any { it.contains("來源：bioz_hal") && it.contains("近期") })
        assertTrue(freshTime.findings.any { it.contains("感測器時間：") })
    }

    @Test
    fun rawSpo2SamplesAreVisibleButNeverPresentedAsPercent() {
        val verdict = ClinicalVerdict.forRawSpO2(
            64, "has_data", true,
            source = "watch_spo2_raw",
            measuredAtMillis = 1234L,
            timestampSource = "android_sensor_event_elapsed_realtime",
            calibrationStatus = "UNVERIFIED"
        )
        assertTrue(verdict.headline.contains("SpO₂ 原始波形參考"))
        assertTrue(verdict.findings.any { it.contains("原始波形點數 64") })
        assertTrue(verdict.findings.any { it.contains(ResearchSignalSummary.VERSION) })
        assertTrue(verdict.findings.any { it.contains("資料來源：watch_spo2_raw") })
        assertTrue(verdict.findings.any { it.contains("量測時間：1234") })
        assertTrue(verdict.findings.any { it.contains("校準狀態：UNVERIFIED") })
        assertEquals("研究資料", verdict.grade)
    }

    @Test
    fun rawSpo2UntrustedTimeIsNotLabeledAsMeasurementTime() {
        val verdict = ClinicalVerdict.forRawSpO2(
            sampleCount = 64,
            status = "has_data",
            wearQualified = true,
            source = "watch_spo2_raw",
            measuredAtMillis = 1_234L,
            timestampSource = "callback_wall_clock_fallback"
        )

        assertTrue(verdict.findings.any { it.contains("時間欄位：1234") && it.contains("來源未確認") })
        assertTrue(verdict.findings.any { it.contains("時間軸：NORMALIZED_INDEX") && it.contains("estimated_timeline=false") })
        assertFalse(verdict.findings.any { it.contains("量測時間：1234") })
    }

    @Test
    fun rawSpo2ExplicitEstimatedTimeIsMarkedAsEstimatedTimeline() {
        val verdict = ClinicalVerdict.forRawSpO2(
            sampleCount = 64,
            status = "has_data",
            wearQualified = true,
            measuredAtMillis = 1_234L,
            timestampSource = "ESTIMATED_TIMELINE"
        )

        assertTrue(verdict.findings.any { it.contains("估算時間：1234") })
        assertTrue(verdict.findings.any { it.contains("時間軸：ESTIMATED_TIMELINE") && it.contains("estimated_timeline=true") })
    }

    @Test
    fun rawSpo2EstimatedTimelineWithoutTimeValueDoesNotImplyAnEstimatedDate() {
        val verdict = ClinicalVerdict.forRawSpO2(
            sampleCount = 64,
            status = "has_data",
            wearQualified = true,
            measuredAtMillis = 0L,
            timestampSource = "ESTIMATED_TIMELINE"
        )

        assertTrue(verdict.findings.any { it.contains("估算時間：未提供具體時間值") })
        assertTrue(verdict.findings.any { it.contains("時間軸：ESTIMATED_TIMELINE") && it.contains("estimated_timeline=true") })
    }

    @Test
    fun rawSpo2HealthServicesMeasurementSourceIsIdentifiedAsMeasurementTime() {
        val verdict = ClinicalVerdict.forRawSpO2(
            sampleCount = 64,
            status = "has_data",
            wearQualified = true,
            measuredAtMillis = 1_234L,
            timestampSource = HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        )

        assertTrue(verdict.findings.any { it.contains("量測時間：1234") })
        assertTrue(verdict.findings.any { it.contains("時間軸：MEASUREMENT_TIME") && it.contains("estimated_timeline=false") })
    }

    @Test
    fun rawSpo2MissingTimeAndSourceUsesUnknownIndexTimeline() {
        val verdict = ClinicalVerdict.forRawSpO2(
            sampleCount = 64,
            status = "has_data",
            wearQualified = true,
            measuredAtMillis = 0L,
            timestampSource = null
        )

        assertTrue(verdict.findings.any { it.contains("量測時間：未知") })
        assertTrue(verdict.findings.any { it.contains("時間軸：NORMALIZED_INDEX") && it.contains("estimated_timeline=false") })
    }

    @Test
    fun absentSpo2SamplesAreDistinguishedFromCalibrationBlock() {
        val verdict = ClinicalVerdict.forRawSpO2(0, "no_events", null)
        assertTrue(verdict.headline.contains("尚未收到 SpO₂ 原始通道資料"))
        assertTrue(verdict.recommendation.contains("確認手錶韌體"))
    }

    @Test
    fun ecgResearchTimelineIsNotPresentedAsExternalAmplitudeCalibration() {
        val v = ClinicalVerdict.forEcg(
            qualityScore = 86,
            sampleCount = 5_120,
            sampleRate = 500f,
            status = "research_signal_unscaled",
            heartRateBpm = 72.0,
            rrCount = 12,
            wearQualified = true,
            waveformEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING,
            timelineSource = "SENSOR"
        )
        assertTrue(v.headline.contains("已開放研究 RR") || v.headline.contains("研究用原始 ECG"))
        assertTrue(v.findings.any { it.contains("SENSOR 事件時間軸") && it.contains("500.0 點/秒") })
        assertTrue(v.findings.any { it.contains("不可作 AF 判定") || it.contains("已開放") })
        assertTrue(v.findings.any { it.contains("缺少外部 raw→mV") })
        assertTrue(v.findings.any { it.contains("ECG mV 校準：未建立") && it.contains("非手錶 raw→mV 校準") })
    }

    @Test
    fun ecgExternalProfileMustBeExplicitlyMarked() {
        val v = ClinicalVerdict.forEcg(
            80, 512, 500f, "calibrated_external_reference", null, 0, true,
            waveformEncoding = "ecg_external_reference_mv",
            externalCalibrationVerified = true
        )
        assertTrue(v.headline.contains("外部線性參考") && v.headline.contains("非小米校準"))
        assertTrue(v.findings.any { it.contains("只轉換顯示尺度") && it.contains("不解鎖診斷") })
        assertTrue(v.findings.any { it.contains("顯示尺度來源：外部參考配對研究 profile") })
    }

    @Test
    fun staleCalibrationStatusCannotUnlockEcgMillivolts() {
        val v = ClinicalVerdict.forEcg(
            80, 512, 500f, "calibrated_external_reference", null, 0, true,
            waveformEncoding = "ecg_external_reference_mv"
        )
        assertTrue(v.findings.any { it.contains("ECG mV 校準：未建立") })
        assertTrue(v.findings.none { it.contains("顯示尺度來源：外部參考配對研究 profile") })
    }

    @Test
    fun temperatureRemainsUncalibratedResearchData() {
        val good = ClinicalVerdict.forTemperature(34.2, 1, "has_data", true)
        assertTrue(good.score in 1..100)
        assertEquals("研究資料", good.grade)
        assertTrue(good.findings.any { it.contains("單位未驗證") })
        assertTrue(good.clinicalComparisons.isEmpty())
        val cool = ClinicalVerdict.forTemperature(30.0, 1, "has_data", true)
        assertTrue(cool.score in 1..100)
        val raw = ClinicalVerdict.forTemperature(512.0, 1, "has_data", true)
        assertTrue(raw.score in 1..100)
        assertTrue(raw.findings.any { it.contains("512.00") && it.contains("單位未驗證") })
        assertFalse(raw.findings.any { it.contains("512.00") && it.contains("°C") })
    }

    @Test
    fun temperatureTimeEvidenceRequiresFreshTrustedMeasurementTimestamp() {
        val unknownTime = ClinicalVerdict.forTemperature(
            34.2, 64, "has_data", true, source = "watch_raw"
        )
        val trustedTime = ClinicalVerdict.forTemperature(
            34.2, 64, "has_data", true,
            source = "watch_raw",
            measuredAtMillis = System.currentTimeMillis(),
            timestampSource = HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        )

        assertTrue(unknownTime.score!! < trustedTime.score!!)
        assertTrue(unknownTime.findings.any { it.contains("量測時間：未知") })
        assertTrue(trustedTime.findings.any { it.contains(ResearchQualityIndexCalculator.VERSION) })
        assertTrue(trustedTime.findings.any { it.contains("校準狀態：UNVERIFIED") })
    }

    @Test
    fun temperatureDoesNotLabelUntrustedOrEstimatedTimeAsMeasuredTime() {
        val timestamp = System.currentTimeMillis()
        val untrusted = ClinicalVerdict.forTemperature(
            34.2, 64, "has_data", true,
            source = "watch_raw",
            measuredAtMillis = timestamp,
            timestampSource = "callback_receipt_time"
        )
        val estimated = ClinicalVerdict.forTemperature(
            34.2, 64, "has_data", true,
            source = "watch_raw",
            measuredAtMillis = timestamp,
            timestampSource = "ESTIMATED_TIMELINE"
        )
        val trusted = ClinicalVerdict.forTemperature(
            34.2, 64, "has_data", true,
            source = "watch_raw",
            measuredAtMillis = timestamp,
            timestampSource = HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        )

        assertTrue(untrusted.findings.any { it.contains("時間欄位：$timestamp") })
        assertTrue(untrusted.findings.none { it.contains("量測時間：$timestamp") })
        assertTrue(untrusted.findings.any { it.contains("時間軸：NORMALIZED_INDEX") })
        assertTrue(estimated.findings.any { it.contains("估算時間：$timestamp") })
        assertTrue(estimated.findings.none { it.contains("量測時間：$timestamp") })
        assertTrue(estimated.findings.any { it.contains("estimated_timeline=true") })
        assertTrue(trusted.findings.any { it.contains("量測時間：$timestamp") })
        assertTrue(trusted.findings.any { it.contains("時間軸：MEASUREMENT_TIME") })
    }

    @Test
    fun unavailableTemperatureDoesNotExposeAResearchScore() {
        val result = ClinicalVerdict.forTemperature(null, 0, "not_measured", null)

        assertEquals("無資料", result.grade)
        assertFalse(result.qualityScoreAvailable)
        assertTrue(result.findings.none { it.contains("研究證據完整度") })
        assertEquals("未評分", ResearchScoreDisplay.format(null as Int?))
    }

    @Test
    fun staleTemperatureKeepsRawValueButLabelsItHistorical() {
        val result = ClinicalVerdict.forTemperature(
            deviceRawValue = 36.64,
            sampleCount = 12,
            status = "has_data",
            wearQualified = true,
            measuredAtMillis = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )

        assertTrue(result.headline.contains("歷史／過期"))
        assertTrue(result.findings.any { it == "裝置原始值 36.64（單位未驗證）" })
        assertTrue(result.findings.any { it == "時間狀態：歷史／過期" })
    }

    @Test
    fun ecgLowQualityRetainsWaveformButSuppressesDerivedHeartRate() {
        val verdict = ClinicalVerdict.forEcg(
            0, 2_170, 50f, "has_data", 165.03, 14, true,
            waveformEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
        )
        assertTrue(verdict.headline.contains("品質未評分"))
        assertFalse(verdict.headline.contains("165"))
        assertTrue(verdict.findings.any { it.contains("不輸出心率") })
        assertFalse(ClinicalVerdict.isEcgEstimateUsable(0, 2_170, 50f, "has_data", 14, true))
    }

    @Test
    fun ecgTimelineAndPeakIndicesArePresentedWithoutInventingPhysicalTime() {
        val estimated = ClinicalVerdict.forEcg(
            70, 512, 100f, "research_signal_unscaled", null, 0, true,
            waveformEncoding = EcgPayloadInterpretationPolicy.UNMAPPED_EVENT_PREVIEW,
            timelineSource = "ESTIMATED",
            rPeakIndices = listOf(12, 87, 162),
            rPeakIndexDistancesSamples = listOf(75, 75)
        )
        assertTrue(estimated.findings.any { it.contains("ESTIMATED_TIMELINE") })
        assertTrue(estimated.findings.any { it.contains("12, 87, 162") })
        assertFalse(estimated.findings.any { it.contains("索引間距（samples") })
        assertFalse(estimated.findings.any { it.contains("相對時間軸 100.0") })

        val normalized = ClinicalVerdict.forEcg(
            70, 512, 100f, "research_signal_unscaled", null, 0, true,
            waveformEncoding = EcgPayloadInterpretationPolicy.UNMAPPED_EVENT_PREVIEW,
            timelineSource = "NORMALIZED_INDEX",
            rPeakIndices = listOf(12, 87, 162),
            rPeakIndexDistancesSamples = listOf(75, 75)
        )
        assertTrue(normalized.findings.any { it.contains("NORMALIZED_INDEX") && it.contains("無物理時間尺度") })
        assertTrue(normalized.findings.any { it.contains("索引間距（samples；非 ms）：75, 75") })
    }

    @Test
    fun ecgEstimateRequiresAdequateQualitySamplesClockAndRr() {
        val verifiedEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
        assertTrue(ClinicalVerdict.isEcgEstimateUsable(80, 2_170, 50f, "has_data", 14, true, verifiedEncoding, "SENSOR"))
        assertFalse(ClinicalVerdict.isEcgEstimateUsable(80, 2_170, 50f, "has_data", 14, true, verifiedEncoding, "NORMALIZED_INDEX"))
        assertFalse(ClinicalVerdict.isEcgEstimateUsable(80, 2_170, 50f, "has_data", 14, true, verifiedEncoding, "ESTIMATED"))
        assertFalse(ClinicalVerdict.isEcgEstimateUsable(80, 2_170, 50f, "has_data", 14, true, "hal_event_values_unmapped_preview"))
        assertFalse(ClinicalVerdict.isEcgEstimateUsable(80, 2_170, 0f, "has_data", 14, true))
        assertTrue(ClinicalVerdict.isEcgEstimateUsable(80, 2_170, 50f, "has_data", 2, true, verifiedEncoding, "SENSOR"))
        assertFalse(ClinicalVerdict.isEcgEstimateUsable(80, 2_170, 50f, "has_data", 1, true, verifiedEncoding, "SENSOR"))
        assertFalse(ClinicalVerdict.isEcgEstimateUsable(80, 2_170, 50f, "has_data", 14, false))
        assertFalse(ClinicalVerdict.isEcgEstimateUsable(80, 2_170, 50f, "has_data", 14, null, verifiedEncoding))
    }

    @Test
    fun ecgPulseDisplayRequiresPlausibleFiniteBpmAndSharedEstimateGate() {
        fun displayable(bpm: Double?) = ClinicalVerdict.isEcgPulseDisplayable(
            qualityScore = 80,
            sampleCount = 2_170,
            sampleRate = 50f,
            status = "has_data",
            rrCount = 14,
            wearQualified = true,
            heartRateBpm = bpm,
            waveformEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING,
            timelineSource = "SENSOR"
        )

        assertTrue(displayable(165.0))
        assertFalse(displayable(null))
        assertFalse(displayable(Double.NaN))
        assertFalse(displayable(900.0))
        assertFalse(
            ClinicalVerdict.isEcgPulseDisplayable(0, 2_170, 50f, "has_data", 14, true, 165.0, EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING)
        )
    }

    @Test
    fun twoTrustedRrIntervalsDisplayResearchHeartRateWithoutDiagnosis() {
        val verdict = ClinicalVerdict.forEcg(
            qualityScore = 80,
            sampleCount = 128,
            sampleRate = 50f,
            status = "has_data",
            heartRateBpm = 75.0,
            rrCount = 2,
            wearQualified = true,
            waveformEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING,
            timelineSource = "SENSOR"
        )

        assertTrue(verdict.headline.contains("估心率 75"))
        assertTrue(verdict.findings.any { it.contains("研究 RR 2 段") })
        assertTrue(verdict.findings.any { it.contains("不可作 AF 判定") })
    }

    @Test
    fun unmappedHalEventsNeverExposeCachedEcgPulseOrRrAsPhysiology() {
        val verdict = ClinicalVerdict.forEcg(
            qualityScore = 80,
            sampleCount = 2_170,
            sampleRate = 50f,
            status = "has_data",
            heartRateBpm = 165.03,
            rrCount = 14,
            wearQualified = true,
            waveformEncoding = EcgPayloadInterpretationPolicy.UNMAPPED_EVENT_PREVIEW
        )
        assertFalse(verdict.headline.contains("165"))
        assertTrue(verdict.headline.contains("尚無可判讀"))
        assertFalse(verdict.findings.any { it.contains("研究 RR 14 段") })
        assertFalse(ClinicalVerdict.isEcgPulseDisplayable(
            80, 2_170, 50f, "has_data", 14, true, 165.03,
            EcgPayloadInterpretationPolicy.UNMAPPED_EVENT_PREVIEW
        ))
    }

    @Test
    fun suiteWeightsQualityAndCoverage() {
        val ppg = ClinicalVerdict.forPpg(80, 128, 50f, "valid_signal", true, 70.0)
        val temp = ClinicalVerdict.forTemperature(34.0, 1, "has_data", true)
        val suite = ClinicalVerdict.forSuite(30, 36, true, ppg, null, null, temp)
        val withoutUncalibratedTemp = ClinicalVerdict.forSuite(30, 36, true, ppg, null, null, null)
        assertEquals(withoutUncalibratedTemp.score, suite.score)
        assertTrue(suite.clinicalComparisons.any { it.contains("覆蓋") })
    }

    @Test
    fun sensorCoverageDoesNotChangeSignalQualityScore() {
        val ppg = ClinicalVerdict.forPpg(80, 128, 50f, "valid_signal", true, 70.0)
        val sparse = ClinicalVerdict.forSuite(1, 48, true, ppg, null, null, null)
        val full = ClinicalVerdict.forSuite(48, 48, true, ppg, null, null, null)

        assertEquals(sparse.score, full.score)
    }

    @Test
    fun researchOnlyScanDoesNotProducePhysiologicalScore() {
        val temp = ClinicalVerdict.forTemperature(34.0, 1, "has_data", true)
        val suite = ClinicalVerdict.forSuite(48, 48, true, null, null, null, temp)

        assertEquals("研究資料", suite.grade)
        assertNull(suite.score)
        assertTrue(suite.clinicalComparisons.any { it.contains("不代表生理品質或健康程度") })
        assertTrue(suite.clinicalComparisons.any { it.contains("溫度感測器原始值：研究資料（未計分）") })
    }
}

class ClinicalReferenceTablesTest {
    @Test
    fun unknownEcgSampleRateIsNotReportedAsInvalidMeasurement() {
        val unknown = ClinicalReferenceTables.ecgRateCompare(null)
        assertTrue(unknown.contains("未提供"))
        assertTrue(unknown.contains("無法對照"))
        assertFalse(unknown.contains("無效"))

        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { sampleRate ->
            val comparison = ClinicalReferenceTables.ecgRateCompare(sampleRate)
            assertTrue(comparison.contains("回報的 ECG 取樣率無效"))
        }
    }

    @Test
    fun unknownPpgSampleRateIsNotDisplayedAsMeasuredZeroOrBelowThreshold() {
        val unknown = ClinicalReferenceTables.ppgRateCompare(null)
        assertTrue(unknown.contains("未提供"))
        assertTrue(unknown.contains("無法對照"))
        assertTrue(unknown.contains("非 Watch 2 Pro 規格"))
        assertFalse(unknown.contains("低於公開參考下限"))

        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { sampleRate ->
            val comparison = ClinicalReferenceTables.ppgRateCompare(sampleRate)
            assertTrue(comparison.contains("回報取樣率無效"))
            assertFalse(comparison.contains("低於公開參考下限"))
        }
        assertTrue(ClinicalReferenceTables.ppgRateCompare(0f).contains("0 Hz"))
    }

    @Test
    fun reportedPpgSampleRateStillUsesReferenceBand() {
        val low = ClinicalReferenceTables.ppgRateCompare(10f)
        val inBand = ClinicalReferenceTables.ppgRateCompare(50f)
        assertTrue(low.contains("低於公開參考下限"))
        assertTrue(inBand.contains("落在公開參考"))
        assertTrue(low.contains("非 Watch 2 Pro 規格或校準門檻"))
        assertTrue(inBand.contains("非 Watch 2 Pro 規格或校準門檻"))
    }

    @Test
    fun rawSpo2ChannelNeverUsesPercentThresholds() {
        val result = ClinicalReferenceTables.compareMetric("mi_spo2", 96.0)
        assertTrue(result.single().contains("單位未驗證"))
        assertFalse(result.single().contains("95"))
    }

    @Test
    fun classifiesBiozModes() {
        assertEquals(
            ClinicalReferenceTables.BiozMode.WHOLE_BODY_LIKE,
            ClinicalReferenceTables.classifyBioz(600.0)
        )
        assertEquals(
            ClinicalReferenceTables.BiozMode.SEGMENTAL_WRIST,
            ClinicalReferenceTables.classifyBioz(3740.0)
        )
    }

    @Test
    fun phaseAngleMatchesAtan2() {
        val pha = ClinicalReferenceTables.phaseAngleDeg(3739.9, 13.7)
        assertNotNull(pha)
        assertTrue(pha!! in 0.0..5.0)
    }
}
