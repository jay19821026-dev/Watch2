package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.ClockEvidence
import com.jaysato.xiaomihealthbridge.engine.EcgCoreInput
import com.jaysato.xiaomihealthbridge.engine.EcgCoreResearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class EcgPairedReferenceCsvTest {
    @Test
    fun parsesSynchronizedTrainingAndHoldoutPairs() {
        val result = EcgPairedReferenceCsv.parse(validCsv())

        assertNull(result.error)
        val dataset = result.dataset
        assertNotNull(dataset)
        assertEquals(5, dataset!!.training.size)
        assertEquals(5, dataset.holdout.size)
        assertEquals("Reference ECG", dataset.referenceInstrument)
        assertEquals("a".repeat(64), dataset.sensorProfileFingerprint)
        assertEquals(1.0, dataset.training[0].millivolts, 0.0)
        assertEquals(0L, dataset.training[0].synchronizationErrorNanos)
        assertEquals(false, dataset.alignedFromSeparateTraces)
        assertEquals(64, dataset.sha256.length)
    }

    @Test
    fun automaticallyAlignsSeparateTimestampedRawAndReferenceTraces() {
        val result = EcgPairedReferenceCsv.parse(validTraceCsv())

        assertNull(result.error)
        assertEquals(5, result.dataset!!.training.size)
        assertEquals(5, result.dataset.holdout.size)
        assertEquals(true, result.dataset.alignedFromSeparateTraces)
        assertEquals(1.0, result.dataset.training.first().millivolts, 0.0)
        assertEquals(1_000_000L, result.dataset.training.first().synchronizationErrorNanos)
    }

    @Test
    fun pairedCsvCalibrationMapsResearchMvWithoutUnlockingRate() {
        val dataset = EcgPairedReferenceCsv.parse(validCalibrationCsv()).dataset
        assertNotNull(dataset)
        val paired = dataset!!
        val profile = EcgCoreResearchEngine.fitValidatedCalibration(
            trainRaw = paired.training.map { it.raw },
            trainReferenceMv = paired.training.map { it.millivolts },
            holdoutRaw = paired.holdout.map { it.raw },
            holdoutReferenceMv = paired.holdout.map { it.millivolts },
            profileId = "test-only-profile",
            trainingDatasetId = paired.trainingCaptureId,
            holdoutDatasetId = paired.holdoutCaptureId,
            inputEncoding = EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING,
            maxHoldoutRmseMv = paired.maximumAllowedRmseMv,
            sensorProfileFingerprint = paired.sensorProfileFingerprint,
            pairingEvidence = com.jaysato.xiaomihealthbridge.engine.EcgCalibrationPairingEvidence(
                maximumPairSkewNanos = paired.maximumPairSkewNanos,
                trainingRawTimestampsNanos = paired.training.map { it.rawTimestampNanos },
                trainingReferenceTimestampsNanos = paired.training.map { it.referenceTimestampNanos },
                holdoutRawTimestampsNanos = paired.holdout.map { it.rawTimestampNanos },
                holdoutReferenceTimestampsNanos = paired.holdout.map { it.referenceTimestampNanos }
            )
        )
        assertNotNull(profile)

        val rawWaveform = List(64) { index -> 25.0 + 20.0 * sin(index * 0.31) }
        val result = EcgCoreResearchEngine.analyze(
            EcgCoreInput(
                rawValues = rawWaveform,
                clock = ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false),
                waveformMappingVerified = true,
                qualityScore = 80,
                wearQualified = true,
                waveformEncoding = EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING,
                sensorProfileFingerprint = paired.sensorProfileFingerprint
            ),
            profile
        )

        assertEquals(rawWaveform.size, result.researchMv?.size)
        assertEquals(rawWaveform.first() * 0.002 + 0.15, result.researchMv!!.first(), 1e-9)
        assertTrue(result.rrIntervalsMs.isEmpty())
        assertNull(result.heartRateBpm)

        val wrongSensor = EcgCoreResearchEngine.analyze(
            EcgCoreInput(
                rawValues = rawWaveform,
                clock = ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false),
                waveformMappingVerified = true,
                qualityScore = 80,
                wearQualified = true,
                waveformEncoding = EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING,
                sensorProfileFingerprint = "b".repeat(64)
            ),
            profile
        )
        assertNull(wrongSensor.researchMv)
        assertTrue("SENSOR_PROFILE_MISMATCH" in wrongSensor.reasonCodes)

        val unmapped = EcgCoreResearchEngine.analyze(
            EcgCoreInput(
                rawValues = rawWaveform,
                clock = ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false),
                waveformMappingVerified = false,
                qualityScore = 80,
                wearQualified = true,
                waveformEncoding = EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING,
                sensorProfileFingerprint = paired.sensorProfileFingerprint
            ),
            profile
        )
        assertEquals(rawWaveform.size, unmapped.researchMv?.size)
        assertTrue(unmapped.rrIntervalsMs.isEmpty())
        assertNull(unmapped.heartRateBpm)
    }

    @Test
    fun rejectsTracePairsOutsideSkewAndMalformedOrdering() {
        val skewed = validTraceCsv().replace(
            "train,train-1,reference,1001000000,1.0",
            "train,train-1,reference,1020000000,1.0"
        )
        assertEquals("train 波形時間配對不足或覆蓋率低於 80%", EcgPairedReferenceCsv.parse(skewed).error)

        val unordered = validTraceCsv().replace(
            "train,train-1,raw,2000000000,2",
            "train,train-1,raw,1000000000,2"
        )
        assertEquals("第 4 列同一波形時間戳未嚴格遞增", EcgPairedReferenceCsv.parse(unordered).error)
    }

    @Test
    fun rejectsNonMonotonicTimestamps() {
        val csv = validCsv().replace("train,train-1,2000000000,2000000000,2,2.0", "train,train-1,1000000000,1000000000,2,2.0")

        assertEquals("每個 capture 的兩組時間戳都必須嚴格遞增", EcgPairedReferenceCsv.parse(csv).error)
    }

    @Test
    fun rejectsOverlappingCaptureWindowsEvenWhenCaptureIdsDiffer() {
        val overlapping = validCsv()
            .replace("holdout,holdout-1,10000000000,10000000000,6,6.0", "holdout,holdout-1,1000000000,1000000000,6,6.0")

        assertEquals("訓練與獨立留出擷取時間區間重疊", EcgPairedReferenceCsv.parse(overlapping).error)
    }

    @Test
    fun rejectsMismatchedCaptureIdAndInsufficientHoldout() {
        val mismatch = validCsv().replace("train,train-1,", "train,other-capture,")
        assertEquals("第 2 列 split 或 capture ID 不符", EcgPairedReferenceCsv.parse(mismatch).error)

        val shortHoldout = validCsv().lineSequence()
            .filterNot { it.startsWith("holdout,") && it != "holdout,holdout-1,1000000000,1000000000,1,1.0" }
            .joinToString("\n")
        assertEquals("訓練及獨立留出資料各需至少 ${EcgResearchProfileStore.MIN_POINTS} 組同步點", EcgPairedReferenceCsv.parse(shortHoldout).error)
    }

    @Test
    fun rejectsCopiedTrainingPairsRelabeledAsHoldout() {
        val copied = validCsv()
            .replace("holdout,holdout-1,10000000000,10000000000,6,6.0", "holdout,holdout-1,10000000000,10000000000,1,1.0")
            .replace("holdout,holdout-1,11000000000,11000000000,7,7.0", "holdout,holdout-1,11000000000,11000000000,2,2.0")
            .replace("holdout,holdout-1,12000000000,12000000000,8,8.0", "holdout,holdout-1,12000000000,12000000000,3,3.0")
            .replace("holdout,holdout-1,13000000000,13000000000,9,9.0", "holdout,holdout-1,13000000000,13000000000,4,4.0")
            .replace("holdout,holdout-1,14000000000,14000000000,10,10.0", "holdout,holdout-1,14000000000,14000000000,5,5.0")

        assertEquals("訓練與留出配對高度重複，拒絕作為獨立驗證", EcgPairedReferenceCsv.parse(copied).error)
    }

    @Test
    fun rejectsUnknownTimestampBasisAndNonFiniteNumbers() {
        assertEquals("timestamp_basis 必須是 shared_monotonic_ns", EcgPairedReferenceCsv.parse(validCsv().replace("shared_monotonic_ns", "wall_clock")).error)
        assertEquals("第 2 列 raw 無效", EcgPairedReferenceCsv.parse(validCsv().replace("train,train-1,1000000000,1000000000,1,1.0", "train,train-1,1000000000,1000000000,NaN,1.0")).error)
        val skewed = validCsv().replace("train,train-1,1000000000,1000000000,1,1.0", "train,train-1,1000000000,1200000000,1,1.0")
        assertEquals("第 2 列兩端時間差超過預設同步門檻", EcgPairedReferenceCsv.parse(skewed).error)
    }

    @Test
    fun rejectsCsvThatRelaxesAbsoluteHoldoutRmseCeiling() {
        val csv = validCsv().replace("#maximum_allowed_rmse_mv=0.01", "#maximum_allowed_rmse_mv=0.5")

        assertEquals(
            "留出 RMSE 門檻無效；研究上限為 ${EcgCoreResearchEngine.MAX_ABSOLUTE_HOLDOUT_RMSE_MV} mV",
            EcgPairedReferenceCsv.parse(csv).error
        )
    }

    @Test
    fun rejectsMissingOrMalformedWatchSensorProfileFingerprint() {
        assertEquals(
            "缺少有效 watch_sensor_profile_fingerprint（64 位 SHA-256）",
            EcgPairedReferenceCsv.parse(validCsv().replace("#watch_sensor_profile_fingerprint=${"a".repeat(64)}\n", "")).error
        )
        assertEquals(
            "缺少有效 watch_sensor_profile_fingerprint（64 位 SHA-256）",
            EcgPairedReferenceCsv.parse(validCsv().replace("${"a".repeat(64)}", "not-a-fingerprint")).error
        )
    }

    @Test
    fun rejectsCalibrationSplitsLargerThanFitterLimitDuringImport() {
        val maximumPoints = EcgResearchCalibration.MAX_RESEARCH_CALIBRATION_POINTS
        val csv = buildString {
            appendLine("#reference_instrument=Reference ECG")
            appendLine("#protocol_id=paired-protocol-v1")
            appendLine("#watch_sensor_profile_fingerprint=${"a".repeat(64)}")
            appendLine("#training_capture_id=train-1")
            appendLine("#holdout_capture_id=holdout-1")
            appendLine("#maximum_allowed_rmse_mv=0.01")
            appendLine("#maximum_pair_skew_ns=5000000")
            appendLine("#timestamp_basis=shared_monotonic_ns")
            appendLine("split,capture_id,raw_timestamp_ns,reference_timestamp_ns,raw,mv")
            repeat(maximumPoints + 1) { index ->
                val timestamp = 1_000_000_000L + index * 1_000_000L
                appendLine("train,train-1,$timestamp,$timestamp,$index,${index * 0.01}")
            }
            repeat(EcgResearchProfileStore.MIN_POINTS) { index ->
                val timestamp = 2_000_000_000L + index * 1_000_000L
                appendLine("holdout,holdout-1,$timestamp,$timestamp,${index + 300},${(index + 300) * 0.01}")
            }
        }

        assertEquals(
            "訓練與留出資料各不可超過 $maximumPoints 組配對點",
            EcgPairedReferenceCsv.parse(csv).error
        )
    }

    private fun validCsv(): String = """
        #reference_instrument=Reference ECG
        #protocol_id=paired-protocol-v1
        #watch_sensor_profile_fingerprint=${"a".repeat(64)}
        #training_capture_id=train-1
        #holdout_capture_id=holdout-1
        #maximum_allowed_rmse_mv=0.01
        #maximum_pair_skew_ns=5000000
        #timestamp_basis=shared_monotonic_ns
        split,capture_id,raw_timestamp_ns,reference_timestamp_ns,raw,mv
        train,train-1,1000000000,1000000000,1,1.0
        train,train-1,2000000000,2000000000,2,2.0
        train,train-1,3000000000,3000000000,3,3.0
        train,train-1,4000000000,4000000000,4,4.0
        train,train-1,5000000000,5000000000,5,5.0
        holdout,holdout-1,10000000000,10000000000,6,6.0
        holdout,holdout-1,11000000000,11000000000,7,7.0
        holdout,holdout-1,12000000000,12000000000,8,8.0
        holdout,holdout-1,13000000000,13000000000,9,9.0
        holdout,holdout-1,14000000000,14000000000,10,10.0
    """.trimIndent()

    private fun validTraceCsv(): String = buildString {
        appendLine("#reference_instrument=Reference ECG")
        appendLine("#protocol_id=paired-protocol-v1")
        appendLine("#watch_sensor_profile_fingerprint=${"a".repeat(64)}")
        appendLine("#training_capture_id=train-1")
        appendLine("#holdout_capture_id=holdout-1")
        appendLine("#maximum_allowed_rmse_mv=0.01")
        appendLine("#maximum_pair_skew_ns=5000000")
        appendLine("#timestamp_basis=shared_monotonic_ns")
        appendLine("split,capture_id,stream,timestamp_ns,value")
        listOf("train" to "train-1", "holdout" to "holdout-1").forEachIndexed { captureIndex, (split, captureId) ->
            val base = if (captureIndex == 0) 1_000_000_000L else 10_000_000_000L
            repeat(5) { index ->
                val timestamp = base + index * 1_000_000_000L
                val value = if (captureIndex == 0) index + 1.0 else listOf(1.5, 2.5, 3.5, 4.5, 2.2)[index]
                appendLine("$split,$captureId,raw,$timestamp,$value")
                appendLine("$split,$captureId,reference,${timestamp + 1_000_000L},$value")
            }
        }
    }

    private fun validCalibrationCsv(): String = buildString {
        appendLine("#reference_instrument=Test fixture only")
        appendLine("#protocol_id=synthetic-regression-fixture-not-device-calibration")
        appendLine("#watch_sensor_profile_fingerprint=${"a".repeat(64)}")
        appendLine("#training_capture_id=synthetic-train")
        appendLine("#holdout_capture_id=synthetic-holdout")
        appendLine("#maximum_allowed_rmse_mv=0.0001")
        appendLine("#maximum_pair_skew_ns=1000000")
        appendLine("#timestamp_basis=shared_monotonic_ns")
        appendLine("split,capture_id,stream,timestamp_ns,value")
        (0..62 step 2).forEachIndexed { index, raw ->
            val timestamp = 1_000_000_000L + index * 1_000_000L
            appendLine("train,synthetic-train,raw,$timestamp,$raw")
            appendLine("train,synthetic-train,reference,$timestamp,${raw * 0.002 + 0.15}")
        }
        (1..61 step 4).forEachIndexed { index, raw ->
            val timestamp = 10_000_000_000L + index * 1_000_000L
            appendLine("holdout,synthetic-holdout,raw,$timestamp,$raw")
            appendLine("holdout,synthetic-holdout,reference,$timestamp,${raw * 0.002 + 0.15}")
        }
    }
}
