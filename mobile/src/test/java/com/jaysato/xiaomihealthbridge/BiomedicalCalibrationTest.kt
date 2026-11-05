package com.jaysato.xiaomihealthbridge

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlin.math.sin

class BiomedicalCalibrationTest {
    @Test
    fun opticalResearchQualityUsesWaveformEvidenceInsteadOfPulseEstimate() {
        val periodic = List(128) { index -> (12_000 + sin(index / 3.0) * 400).toFloat() }
        val flat = List(128) { 12_000f }
        val periodicScore = BiomedicalCalibration.researchOpticalQuality(periodic, periodic, 800f)
        val flatScore = BiomedicalCalibration.researchOpticalQuality(flat, flat, 0f)

        assertTrue(periodicScore > flatScore)
        assertTrue(periodicScore in 1..99)
        assertEquals(0, flatScore)
    }

    @Test
    fun opticalResearchQualityDoesNotCountTheTransitionAcrossAMissingGap() {
        val raw = List(64) { index ->
            when {
                index < 32 -> 12_000f
                index == 32 -> 524287f
                else -> 12_400f
            }
        }
        val cleaned = raw.filter { it != 524287f }

        assertEquals(0, BiomedicalCalibration.researchOpticalQuality(raw, cleaned, 400f))
    }

    @Test
    fun isolatedPpgExcursionCannotReceiveHighQualityScore() {
        val values = MutableList(128) { 12000f }
        values[64] = 12024f
        val result = BiomedicalCalibration.refinePpg(values, 0f)
        assertTrue(result.qualityScore < 10)
        assertNull(result.pulseRateBpm)
    }

    @Test
    fun constantPpgWithoutClockCannotReceiveHighQualityScore() {
        val result = BiomedicalCalibration.refinePpg(List(128) { 12000f }, 0f)
        assertEquals(0, result.qualityScore)
        assertNull(result.pulseRateBpm)
    }

    @Test
    fun ppgRejectsFillValueFrames() {
        val result = BiomedicalCalibration.refinePpg(List(64) { 524287f }, 25f)
        assertFalse(result.valid)
        assertEquals(0, result.qualityScore)
    }

    @Test
    fun ppgPreservesFiniteZeroSamplesInsteadOfTreatingThemAsMissing() {
        val values = List(128) { index -> if (index % 8 == 0) 0f else 12000f + index }
        val result = BiomedicalCalibration.refinePpg(values, 0f)

        assertEquals(values.size, result.waveform.size)
        assertTrue(result.waveform.any { it == 0f })
        assertTrue(result.comparisons.none { it.contains("高比例 HAL 填充值") })
    }

    @Test
    fun ppgCanPublishResearchPulseWithoutClaimingClinicalCalibration() {
        val values = List(128) { index -> (sin(index / 3.0) * 400 + 12000).toFloat() }
        val result = BiomedicalCalibration.refinePpg(
            values, 50f, "android_sensor_event_elapsed_realtime", channelMappingVerified = true, wearQualified = true
        )
        assertTrue(result.valid)
        assertEquals("research_optical", BiomedicalCalibration.ppgMeasurementStatus(result))
        assertNotNull(result.pulseRateBpm)
        assertTrue(result.pulseRateBpm!! in 40.0..180.0)
        assertEquals(
            SensorHealthAlgorithms.waveformQuality(result.waveform, result.effectiveSampleRateHz, "ppg").score,
            result.qualityScore
        )
        val verdict = ClinicalVerdict.forPpg(
            qualityScore = result.qualityScore,
            sampleCount = result.waveform.size,
            sampleRate = result.effectiveSampleRateHz,
            status = BiomedicalCalibration.ppgMeasurementStatus(result),
            wearQualified = true,
            pulseRateBpm = result.pulseRateBpm
        )
        assertTrue(verdict.findings.any { it.contains("研究估計") && it.contains("bpm") })
        assertTrue(result.rrCount >= 0)
        assertTrue(result.waveform.size >= 64)
        assertTrue(result.summary.contains("研究") || result.summary.contains("PPG"))
        assertTrue(result.comparisons.any { it.contains("取樣率") || it.contains("PPG") })
    }

    @Test
    fun ppgDoesNotInventSampleRate() {
        assertEquals(1f, BiomedicalCalibration.sanitizePpgSampleRate(1f, 256), 0.01f)
        assertEquals(0f, BiomedicalCalibration.sanitizePpgSampleRate(Float.NaN, 256), 0.01f)
    }

    @Test
    fun ppgEventThroughputCannotBeUsedAsSampleRate() {
        val values = List(256) { index -> (sin(index / 3.0) * 400 + 12000).toFloat() }
        val result = BiomedicalCalibration.refinePpg(values, 10.3f)
        assertFalse(result.valid)
        assertNull(result.pulseRateBpm)
        assertEquals(0, result.rrCount)
        assertTrue(result.comparisons.any { it.contains("取樣時鐘") || it.contains("取樣率") })
    }

    @Test
    fun receivedPpgWithoutClockIsResearchDataNotAQualityFailure() {
        val values = List(128) { index -> (sin(index / 3.0) * 400 + 12000).toFloat() }
        val result = BiomedicalCalibration.refinePpg(values, 0f)
        assertEquals("research_signal_unscaled", BiomedicalCalibration.ppgMeasurementStatus(result))
        assertNull(result.pulseRateBpm)
        assertTrue(result.waveform.isNotEmpty())
        assertTrue(result.qualityScore > 25)
        assertNotNull(result.confidenceScore)
        assertEquals("UNKNOWN", result.timestampSource)
        assertEquals(19, result.dominantPeriodSamples)
        assertTrue(result.periodCorrelation!! >= 0.35)
        assertTrue(result.summary.contains("索引域重複週期"))
        assertNull(result.pulseRateBpm)
    }

    @Test
    fun ppgIndexDomainQualityDownweightsNonPeriodicNoiseWithoutInventingRate() {
        val periodic = List(512) { index -> (12_000 + sin(index * Math.PI * 2.0 / 40.0) * 400).toFloat() }
        val noise = Random(41).let { random -> List(512) { (12_000 + random.nextInt(-500, 501)).toFloat() } }

        val periodicResult = BiomedicalCalibration.refinePpg(periodic, 0f)
        val noiseResult = BiomedicalCalibration.refinePpg(noise, 0f)

        assertTrue(periodicResult.qualityScore > noiseResult.qualityScore)
        assertNull(periodicResult.pulseRateBpm)
        assertNull(noiseResult.pulseRateBpm)
    }

    @Test
    fun ppgIndexPeriodicityPreservesGapsInsteadOfCompressingIndices() {
        val values = List(512) { index ->
            if (index % 17 == 0) 524287f else (sin(index * Math.PI * 2.0 / 40.0) * 400 + 12000).toFloat()
        }
        val result = BiomedicalCalibration.refinePpg(values, 0f)

        assertEquals(40, result.dominantPeriodSamples)
        assertNull(result.pulseRateBpm)
        assertEquals(values.size, result.displayWaveform.size)
        assertEquals(524287f, result.displayWaveform[0])
        assertEquals(values[1], result.displayWaveform[1])
    }

    @Test
    fun ppgMissingFramesNeverProduceTimeDomainPulseFromCompressedSamples() {
        val values = List(512) { index ->
            if (index % 17 == 0) 524287f else (sin(index * Math.PI * 2.0 / 40.0) * 400 + 12000).toFloat()
        }
        val result = BiomedicalCalibration.refinePpg(
            values, 50f, "android_sensor_event_elapsed_realtime", channelMappingVerified = true, wearQualified = true
        )

        assertNull(result.pulseRateBpm)
        assertEquals(0, result.rrCount)
        assertTrue(result.waveform.isNotEmpty())
        assertEquals(values.size, result.displayWaveform.size)
        assertEquals(524287f, result.displayWaveform[17])
        val complete = BiomedicalCalibration.refinePpg(
            List(512) { index -> (sin(index * Math.PI * 2.0 / 40.0) * 400 + 12000).toFloat() },
            50f, "android_sensor_event_elapsed_realtime", channelMappingVerified = true, wearQualified = true
        )
        assertTrue(result.qualityScore < complete.qualityScore)
        assertTrue(result.comparisons.any { it.contains("移除無效點會改變時間間距") })
    }

    @Test
    fun ppgQualityDoesNotPairSamplesAcrossFillValueGap() {
        val values = List(128) { index ->
            when {
                index < 64 -> 12_000f
                index == 64 -> 524287f
                else -> 12_400f
            }
        }

        val result = BiomedicalCalibration.refinePpg(values, 50f)

        assertEquals(0, result.qualityScore)
        assertTrue(result.comparisons.any { it.contains("不跨越缺口配對相鄰樣本") })
    }

    @Test
    fun ppgNonFiniteValuesSplitQualityAndDisableRateCalculation() {
        val values = List(256) { index ->
            when (index) {
                64 -> Float.NaN
                128 -> Float.POSITIVE_INFINITY
                192 -> Float.NEGATIVE_INFINITY
                else -> (sin(index / 3.0) * 400 + 12_000).toFloat()
            }
        }

        val result = BiomedicalCalibration.refinePpg(
            values, 50f, "android_sensor_event_elapsed_realtime", channelMappingVerified = true, wearQualified = true
        )

        assertNull(result.pulseRateBpm)
        assertEquals(0, result.rrCount)
        assertEquals(values.size, result.displayWaveform.size)
        assertEquals(3, result.displayWaveform.count { it == 524287f })
        assertTrue(result.comparisons.any { it.contains("不跨越缺口配對相鄰樣本") })
        assertTrue(result.comparisons.any { it.contains("分段評估 4 段、253 點") })
    }

    @Test
    fun ppgIndexPeriodicityDoesNotReportRandomNoiseOrUnboundedInputs() {
        val noise = kotlin.random.Random(42).let { random ->
            List(512) { (11_000 + random.nextInt(2_000)).toFloat() }
        }
        val oversized = List(16_385) { index -> (sin(index / 3.0) * 400 + 12000).toFloat() }

        assertNull(BiomedicalCalibration.refinePpg(noise, 0f).dominantPeriodSamples)
        assertNull(BiomedicalCalibration.refinePpg(oversized, 0f).dominantPeriodSamples)
    }

    @Test
    fun ppgIndexPeriodicityDoesNotTreatSearchBoundaryAsPeak() {
        val waveform = List(512) { index ->
            (sin(index * Math.PI * 2.0 / 2_000.0) * 400 + 12_000).toFloat()
        }
        val result = BiomedicalCalibration.refinePpg(waveform, 0f)

        assertNotEquals(waveform.size / 2, result.dominantPeriodSamples)
    }

    @Test
    fun ppgReportedRateAloneDoesNotPublishDerivedPulse() {
        val values = List(256) { index -> (sin(index / 3.0) * 400 + 12000).toFloat() }
        val result = BiomedicalCalibration.refinePpg(values, 50f)
        assertNull(result.pulseRateBpm)
        assertEquals(0, result.rrCount)
        assertEquals("research_signal_unscaled", BiomedicalCalibration.ppgMeasurementStatus(result))
        assertTrue(result.waveform.isNotEmpty())
        assertTrue(result.comparisons.any { it.contains("通道映射未驗證") })
    }

    @Test
    fun ppgEvidenceConfidenceImprovesOnlyWithDeclaredSourceAndTimeEvidence() {
        val values = List(256) { index -> (sin(index / 3.0) * 400 + 12000).toFloat() }
        val unknown = BiomedicalCalibration.refinePpg(values, 0f)
        val evidenced = BiomedicalCalibration.refinePpg(
            values, 32f, "android_sensor_event_elapsed_realtime", channelMappingVerified = true, wearQualified = true
        )
        assertNotNull(unknown.confidenceScore)
        assertNotNull(evidenced.confidenceScore)
        assertTrue(evidenced.confidenceScore!! > unknown.confidenceScore!!)
    }

    @Test
    fun sentinelOnlyPpgRemainsRejectedRatherThanPretendingToBeUsable() {
        val result = BiomedicalCalibration.refinePpg(List(128) { 524287f }, 0f)
        assertEquals("invalid_optical", BiomedicalCalibration.ppgMeasurementStatus(result))
        assertTrue(result.waveform.isEmpty())
    }

    @Test
    fun ppgAtHeartRateOnlyFrequencyCanPublishResearchRateButNotHrv() {
        val values = List(512) { index -> (sin(index / 4.0) * 500 + 12000).toFloat() }
        val result = BiomedicalCalibration.refinePpg(values, 32f)
        assertNull(result.pulseRateBpm)
        assertEquals(0, result.rrCount)
        assertTrue(result.comparisons.any { it.contains("64 Hz") })
    }

    @Test
    fun ecgCalibrationRemovesBaselineAndEstimatesRate() {
        // Synthetic ~60 bpm ECG at 100 Hz with clear R spikes.
        val values = List(600) { index ->
            val phase = (index % 100) / 100.0
            val beat = (sin(phase * Math.PI * 2) * 200 + if (phase in 0.18..0.22) 5000 else 0).toFloat()
            beat + 8000f
        }
        val result = BiomedicalCalibration.calibrateEcg(values, 100f)
        assertTrue(result.waveform.size >= 64)
        assertTrue(result.waveform.any { it < 0f } && result.waveform.any { it > 0f })
        val centered = values.map { it - values.average().toFloat() }
        val span = (centered.max() - centered.min()).coerceAtLeast(1e-6f)
        val scaled = centered.map { (it / span) * 1000f }
        assertEquals(SensorHealthAlgorithms.waveformQuality(scaled, 100f, "ecg").score, result.contactScore)
        assertTrue(result.comparisons.any { it.contains("不代表 AF 分類準確率") })
        assertTrue(result.summary.contains("ECG"))
        assertNull(result.heartRateBpm)
        assertEquals(0, result.rrCount)
        assertTrue(result.comparisons.any { it.contains("閘門未通過") })
        assertTrue(result.comparisons.any { it.contains("IEC") || it.contains("取樣率") })
    }

    @Test
    fun ecgPublishedHeartRateRequiresStableRr() {
        val noise = List(500) { i ->
            when {
                i % 13 == 0 -> 2200f
                i % 19 == 0 -> 1900f
                i % 29 == 0 -> 1600f
                else -> ((i % 7) - 3).toFloat()
            }
        }
        val result = BiomedicalCalibration.calibrateEcg(noise, 97.5f)
        if (result.heartRateBpm != null) {
            assertNotNull(result.rrCv)
            assertTrue(result.rrCv!! <= 0.22)
            assertTrue(result.rrCount >= 4)
            assertTrue(result.heartRateBpm in 40.0..180.0)
        }
    }

    @Test
    fun ecgUnknownSampleClockNeverCreatesRrOrHeartRate() {
        val values = List(600) { index ->
            if (index % 100 in 18..22) 5000f else (index % 17).toFloat()
        }
        listOf(0f, Float.NaN, Float.POSITIVE_INFINITY).forEach { sampleRate ->
            val result = BiomedicalCalibration.calibrateEcg(values, sampleRate)
            assertNull(result.heartRateBpm)
            assertEquals(0, result.rrCount)
            assertNull(result.rrCv)
            assertTrue(result.waveform.isNotEmpty())
            assertEquals(0, result.contactScore)
            assertTrue(result.summary.contains("NORMALIZED_INDEX"))
        }
    }

    @Test
    fun ecgMissingRateSentinelIsReportedAsNotProvidedWhenSamplesAreInsufficient() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { sampleRate ->
            val result = BiomedicalCalibration.calibrateEcg(List(10) { it.toFloat() }, sampleRate)
            assertTrue(result.comparisons.single().contains("ECG 取樣率未提供"))
            assertFalse(result.comparisons.single().contains("無效"))
        }
    }

    @Test
    fun ecgNonFiniteSampleGapDoesNotCompressTimeForRateOrMv() {
        val peaks = listOf(100, 200, 300, 400, 500)
        val values = MutableList(600) { index ->
            peaks.map { peak ->
                val offset = (index - peak).toDouble()
                (kotlin.math.exp(-(offset * offset) / (2.0 * 2.0 * 2.0)) * 5_000.0).toFloat()
            }.sum()
        }.apply { this[250] = Float.NaN }
        val timestamps = (0 until values.size).map { 1_000_000_000L + it * 10_000_000L }
        val clock = com.jaysato.xiaomihealthbridge.engine.ClockEvidence(
            sourceTimestampsNanos = timestamps,
            callbackTimestampsNanos = timestamps,
            nominalSampleRateHz = 100.0,
            sampleRateSource = com.jaysato.xiaomihealthbridge.engine.EcgCoreResearchEngine.HAL_SAMPLE_RATE_SOURCE,
            eventOrderingVerified = true,
            samplesPerEventKnown = true,
            sessionOrderingVerified = true,
            sourceClockVerified = true
        )

        val result = BiomedicalCalibration.calibrateEcg(
            raw = values,
            sampleRateHz = 100f,
            clockEvidence = clock,
            waveformMappingVerified = true,
            wearQualified = true
        )

        assertNull(result.heartRateBpm)
        assertEquals(0, result.rrCount)
        assertFalse(result.calibrated)
        assertEquals(values.size, result.waveform.size)
        assertTrue(result.waveform[250].isNaN())
        assertEquals(0, result.contactScore)
        assertTrue(result.comparisons.any { it.contains("缺樣") || it.contains("NONFINITE_SAMPLE_GAP") })
    }

    @Test
    fun ecgTooFewFiniteSamplesPreserveOriginalGapPositions() {
        val values = MutableList(40) { it.toFloat() }.apply { repeat(10) { this[it * 2] = Float.NaN } }
        val result = BiomedicalCalibration.calibrateEcg(values, 100f)

        assertEquals(values.size, result.waveform.size)
        assertTrue(result.waveform.indices.filter { values[it].isNaN() }.all { result.waveform[it].isNaN() })
    }

    @Test
    fun ecgCarrierWarnsButStillAllowsResearchRr() {
        val carrier = List(2500) { index ->
            (sin(index * Math.PI * 2.0 / 25.0) * 20000.0).toFloat()
        }
        val result = BiomedicalCalibration.calibrateEcg(carrier, 500f)
        assertFalse(result.calibrated)
        assertTrue(result.summary.contains("載波") || result.comparisons.any { it.contains("載波") || it.contains("重複") })
        assertEquals(0, result.rrCount)
        assertNull(result.heartRateBpm)
    }

    @Test
    fun ecgVerdictDoesNotTreatUnscaledWaveformQualityAsClinicalValidity() {
        val verdict = ClinicalVerdict.forEcg(
            qualityScore = 90,
            sampleCount = 600,
            sampleRate = 100f,
            status = "research_signal_unscaled",
            heartRateBpm = null,
            rrCount = 0,
            wearQualified = true,
            waveformEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
        )
        assertTrue(verdict.headline.contains("研究用原始 ECG"))
        assertTrue(verdict.findings.any { it.contains("不代表診斷準確度") })
        assertTrue(verdict.recommendation.contains("不能診斷"))
    }

    @Test
    fun ecgVerdictDoesNotGradeUnknownOrInvalidSignalAsUsable() {
        val verdict = ClinicalVerdict.forEcg(
            qualityScore = 90,
            sampleCount = 600,
            sampleRate = 100f,
            status = "invalid_optical",
            heartRateBpm = null,
            rrCount = 0,
            wearQualified = true
        )
        assertFalse(verdict.headline.contains("已取得研究用原始 ECG"))
        assertEquals("—", verdict.grade)
    }

    @Test
    fun externalEcgScaleFitDoesNotClaimDeviceOrClinicalCalibration() {
        val verdict = ClinicalVerdict.forEcg(
            qualityScore = 90,
            sampleCount = 600,
            sampleRate = 100f,
            status = "calibrated_external_reference",
            heartRateBpm = null,
            rrCount = 0,
            wearQualified = true,
            waveformEncoding = "ecg_external_reference_mv",
            externalCalibrationVerified = true
        )
        assertTrue(verdict.headline.contains("非小米校準／非臨床驗證"))
        assertTrue(verdict.findings.any { it.contains("只轉換顯示尺度") })
        assertTrue(verdict.recommendation.contains("不能診斷"))
    }

    @Test
    fun biozRawMagnitudeDoesNotImplyOhmsOrHydration() {
        val result = BiomedicalCalibration.calibrateBioz(
            latest = listOf(650.0, 40.0),
            waveform = List(80) { 650f + it % 5 }
        )
        assertFalse(result.calibrated)
        assertEquals(ClinicalReferenceTables.BiozMode.UNKNOWN, result.mode)
        assertNull(result.impedanceOhm)
        assertNull(result.hydrationIndex)
        assertNotNull(result.waveformMedianPositionIndex)
        assertNotNull(result.relativeChangePercent)
        assertNotNull(result.stabilityScore)
        assertNotNull(result.confidenceScore)
        assertTrue(result.summary.contains("單位未驗證"))
        assertTrue(result.comparisons.any { it.contains("非阻抗值或跨次趨勢") })
    }

    @Test
    fun biozFlatlineAndIsolatedSpikeDoNotReceiveHighWaveformEvidence() {
        val flatline = BiomedicalCalibration.calibrateBioz(
            latest = listOf(650.0, 40.0),
            waveform = List(1024) { 650f }
        )
        val isolatedSpike = BiomedicalCalibration.calibrateBioz(
            latest = listOf(650.0, 40.0),
            waveform = List(1024) { if (it == 512) 652f else 650f }
        )

        assertEquals(0, flatline.contactScore)
        assertTrue(isolatedSpike.contactScore <= 1)
        assertEquals(flatline.confidenceScore, isolatedSpike.confidenceScore)
        assertTrue(flatline.comparisons.any { it.contains("平線／稀疏尖峰") })
        assertTrue(isolatedSpike.comparisons.any { it.contains("相鄰變化覆蓋率") })
        assertEquals(3, isolatedSpike.stabilityScore)
        assertTrue(isolatedSpike.comparisons.any { it.contains("波形相對離散度：3%") })
    }

    @Test
    fun biozDoesNotCountAChangeAcrossMissingSamplesAsAnAdjacentTransition() {
        val waveform = List(64) { index ->
            when {
                index < 24 -> 100f
                index < 40 -> Float.NaN
                else -> 200f
            }
        }

        val result = BiomedicalCalibration.calibrateBioz(emptyList(), waveform)

        assertEquals(0, result.contactScore)
        assertEquals(100.0, result.relativeChangePercent!!, 0.001)
        assertTrue(result.comparisons.any { it.contains("46 對／63 個可能相鄰位置") })
    }

    @Test
    fun biozDisplayWaveformPreservesMissingSampleIndicesAndTrueZeroValues() {
        val source = listOf(0f, 1f, Float.NaN, Float.POSITIVE_INFINITY, 4f)

        val result = BiomedicalCalibration.calibrateBioz(emptyList(), source)
        val encoded = result.displayWaveform.joinToString(",")
        val decoded = SensorSuiteCsvParser.parseFloatsWithGapMarkers(encoded)

        assertEquals(source.size, decoded.size)
        assertEquals(0f, decoded[0])
        assertEquals(1f, decoded[1])
        assertTrue(decoded[2].isNaN())
        assertTrue(decoded[3].isNaN())
        assertEquals(4f, decoded[4])
        assertEquals(listOf(0f, 1f, 4f), result.waveform)
    }

    @Test
    fun biozCompletenessIndexDoesNotReuseWaveformChangeCoverage() {
        val flatline = BiomedicalCalibration.calibrateBioz(emptyList(), List(1024) { 650f })
        val varying = BiomedicalCalibration.calibrateBioz(emptyList(), List(1024) { (it % 7).toFloat() })

        assertEquals(flatline.confidenceScore, varying.confidenceScore)
        assertTrue(flatline.contactScore < varying.contactScore)
    }

    @Test
    fun biozChangingWaveformGetsEvidenceScoreWithoutPhysicalUnitClaim() {
        val result = BiomedicalCalibration.calibrateBioz(
            latest = listOf(650.0, 40.0),
            waveform = List(1024) { index -> (650 + index % 7).toFloat() }
        )

        assertTrue(result.contactScore > 0)
        assertTrue(result.summary.contains("波形證據"))
        assertFalse(result.calibrated)
        assertNull(result.impedanceOhm)
        assertTrue(result.comparisons.any { it.contains("相鄰變化覆蓋率") })
    }

    @Test
    fun biozPreservesFiniteZerosAndDoesNotUseUnpairedLatestChannelsToScoreWaveform() {
        val zeroWaveform = List(128) { if (it % 2 == 0) 0f else 1f }
        val withoutChannels = BiomedicalCalibration.calibrateBioz(emptyList(), zeroWaveform)
        val withUnpairedChannels = BiomedicalCalibration.calibrateBioz(listOf(500.0, 20.0), zeroWaveform)

        assertEquals(zeroWaveform.size, withoutChannels.waveform.size)
        assertEquals(withoutChannels.confidenceScore, withUnpairedChannels.confidenceScore)
        assertEquals(withoutChannels.contactScore, withUnpairedChannels.contactScore)
        assertTrue(withUnpairedChannels.waveform.contains(0f))
    }

    @Test
    fun biozRetainsRawChannelsAsUnitUnverifiedNumbersIncludingZero() {
        val result = BiomedicalCalibration.calibrateBioz(
            latest = listOf(0.0, 650.25, Double.NaN),
            waveform = List(80) { (it % 5).toFloat() }
        )

        assertEquals(3, result.rawChannelValues.size)
        val encoded = BiozRawChannelEvidence.encode(result.rawChannelValues)
        val decoded = BiozRawChannelEvidence.decode("confidence=70;raw_channel_values=$encoded;stability=80")
        assertEquals(3, decoded.size)
        assertEquals(0.0, decoded[0].second!!, 0.0)
        assertEquals(1, decoded[1].first)
        assertEquals(650.25, decoded[1].second!!, 0.0)
        assertNull(decoded[2].second)

        val verdict = ClinicalVerdict.forBioz(
            qualityScore = result.contactScore,
            impedanceOhm = null,
            resistanceOhm = null,
            reactanceOhm = null,
            hydrationIndex = null,
            status = "research_signal_unscaled",
            rawSampleCount = result.waveform.size,
            rawChannelValues = decoded
        )
        assertTrue(verdict.findings.any { it.contains("通道 0=0.00000") })
        assertTrue(verdict.findings.any { it.contains("單位未驗證") })
        assertTrue(verdict.findings.none { it.contains("0.00000 Ω") })
    }

    @Test
    fun biozWaveformEvidenceIsInvariantToPositiveOffsetAndScale() {
        val waveform = List(256) { (it % 11).toFloat() }
        val offsetAndScaled = waveform.map { 2_000f + it * 0.01f }
        val first = BiomedicalCalibration.calibrateBioz(emptyList(), waveform)
        val second = BiomedicalCalibration.calibrateBioz(emptyList(), offsetAndScaled)

        assertEquals(first.contactScore, second.contactScore)
        assertEquals(first.confidenceScore, second.confidenceScore)
    }

    @Test
    fun ecgResearchMvRequiresValidatedProfileAndExactEncodingNotRateMappingFlag() {
        val values = List(600) { index -> if (index % 100 in 18..22) 5_000f else (index % 17).toFloat() }
        val trainRaw = (0..5_000 step 500).map(Int::toDouble)
        val holdoutRaw = (250..4_750 step 500).map(Int::toDouble)
        val calibration = com.jaysato.xiaomihealthbridge.engine.EcgCoreResearchEngine.fitValidatedCalibration(
            trainRaw, trainRaw.map { it * 0.001 }, holdoutRaw, holdoutRaw.map { it * 0.001 },
            "profile-1", "capture-train", "capture-holdout",
            com.jaysato.xiaomihealthbridge.engine.EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.01,
            com.jaysato.xiaomihealthbridge.engine.EcgCalibrationPairingEvidence(
                maximumPairSkewNanos = 1_000_000L,
                trainingRawTimestampsNanos = (0 until trainRaw.size).map { 1_000_000_000L + it * 10_000_000L },
                trainingReferenceTimestampsNanos = (0 until trainRaw.size).map { 1_000_000_000L + it * 10_000_000L },
                holdoutRawTimestampsNanos = (0 until holdoutRaw.size).map { 10_000_000_000L + it * 10_000_000L },
                holdoutReferenceTimestampsNanos = (0 until holdoutRaw.size).map { 10_000_000_000L + it * 10_000_000L }
            ),
            "a".repeat(64)
        )!!
        val unverifiedMapping = BiomedicalCalibration.calibrateEcg(
            values,
            100f,
            calibration = calibration,
            waveformEncoding = com.jaysato.xiaomihealthbridge.engine.EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING,
            sensorProfileFingerprint = "a".repeat(64)
        )
        assertEquals(values.size, unverifiedMapping.researchMvWaveform?.size)
        assertTrue(unverifiedMapping.calibrated)
        assertNull(unverifiedMapping.heartRateBpm)

        val pairedReferenceMapping = BiomedicalCalibration.calibrateEcg(
            values, 100f, waveformMappingVerified = true, calibration = calibration,
            waveformEncoding = com.jaysato.xiaomihealthbridge.engine.EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING,
            sensorProfileFingerprint = "a".repeat(64)
        )
        assertEquals(values.size, pairedReferenceMapping.researchMvWaveform?.size)
        assertTrue(pairedReferenceMapping.researchMvWaveform!!.all { it.isFinite() })
        assertTrue(pairedReferenceMapping.calibrated)
        assertTrue(pairedReferenceMapping.summary.contains("外部配對研究 mV 映射"))
        assertNull(pairedReferenceMapping.heartRateBpm)

        val referenceProfileCrossOver = BiomedicalCalibration.calibrateEcg(
            values,
            100f,
            calibration = calibration,
            waveformEncoding = com.jaysato.xiaomihealthbridge.engine.EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING,
            sensorProfileFingerprint = "a".repeat(64)
        )
        assertEquals(values.size, referenceProfileCrossOver.researchMvWaveform?.size)
        assertTrue(referenceProfileCrossOver.calibrated)

        val mismatchedReferenceFormat = BiomedicalCalibration.calibrateEcg(
            values, 100f, calibration = calibration, waveformEncoding = "unrelated_encoding"
        )
        assertNull(mismatchedReferenceFormat.researchMvWaveform)
        assertFalse(mismatchedReferenceFormat.calibrated)
    }

    @Test
    fun biozSessionPositionIndexDoesNotDependOnArbitraryRawOffsetOrScale() {
        val base = List(80) { it.toFloat() }
        val shiftedAndScaled = base.map { 1_200f + it * 3f }
        val first = BiomedicalCalibration.calibrateBioz(emptyList(), base)
        val second = BiomedicalCalibration.calibrateBioz(emptyList(), shiftedAndScaled)

        assertEquals(first.waveformMedianPositionIndex, second.waveformMedianPositionIndex)
        assertEquals(first.relativeChangePercent!!, second.relativeChangePercent!!, 1e-6)
        assertEquals(first.stabilityScore, second.stabilityScore)
        assertNull(BiomedicalCalibration.calibrateBioz(emptyList(), List(80) { 650f }).waveformMedianPositionIndex)
        assertNull(BiomedicalCalibration.calibrateBioz(emptyList(), List(80) { 650f }).stabilityScore)
        assertNull(BiomedicalCalibration.calibrateBioz(emptyList(), List(80) { 650f }).relativeChangePercent)
        assertNull(BiomedicalCalibration.calibrateBioz(emptyList(), List(63) { 650f + it % 5 }).confidenceScore)
    }

    @Test
    fun biozEvenSampleMedianUsesAverageOfTwoCenterValues() {
        val result = BiomedicalCalibration.calibrateBioz(emptyList(), listOf(0f, 1f, 2f, 3f))

        assertEquals(50, result.waveformMedianPositionIndex)
    }

    @Test
    fun biozSegmentalWristDoesNotClaimHydration() {
        val result = BiomedicalCalibration.calibrateBioz(
            latest = listOf(3740.0, 13.7),
            waveform = List(80) { 3740f + it % 3 }
        )
        assertFalse(result.calibrated)
        assertEquals(ClinicalReferenceTables.BiozMode.UNKNOWN, result.mode)
        assertNull(result.hydrationIndex)
        assertTrue(result.comparisons.any { it.contains("不可換算") })
    }
}
