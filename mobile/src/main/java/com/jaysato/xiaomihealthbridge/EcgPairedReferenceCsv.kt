package com.jaysato.xiaomihealthbridge

import java.security.MessageDigest
import com.jaysato.xiaomihealthbridge.engine.EcgCoreResearchEngine

internal object EcgPairedReferenceCsv {
    const val MAX_CHARACTERS = 8 * 1024 * 1024
    const val MAX_ROWS = 100_000

    data class PairPoint(
        val rawTimestampNanos: Long,
        val referenceTimestampNanos: Long,
        val raw: Double,
        val millivolts: Double
    ) {
        val synchronizationErrorNanos: Long
            get() = if (rawTimestampNanos >= referenceTimestampNanos) {
                rawTimestampNanos - referenceTimestampNanos
            } else {
                referenceTimestampNanos - rawTimestampNanos
            }
    }

    data class Dataset(
        val referenceInstrument: String,
        val protocolId: String,
        val trainingCaptureId: String,
        val holdoutCaptureId: String,
        val maximumAllowedRmseMv: Double,
        val maximumPairSkewNanos: Long,
        val sensorProfileFingerprint: String,
        val training: List<PairPoint>,
        val holdout: List<PairPoint>,
        val alignedFromSeparateTraces: Boolean,
        val sha256: String
    )

    data class ParseResult(val dataset: Dataset?, val error: String?)

    fun parse(csv: String): ParseResult {
        if (csv.isBlank() || csv.length > MAX_CHARACTERS) return ParseResult(null, "檔案空白或超過 8 MiB")
        val metadata = linkedMapOf<String, String>()
        val rows = csv.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val dataLines = mutableListOf<String>()
        var headerSeen = false
        var traceFormat = false
        for (line in rows) {
            if (line.startsWith("#")) {
                val entry = line.removePrefix("#").trim().split("=", limit = 2)
                if (entry.size != 2 || entry[0].isBlank() || metadata.put(entry[0].trim(), entry[1].trim()) != null) {
                    return ParseResult(null, "CSV 中繼資料格式錯誤或重複")
                }
            } else if (!headerSeen) {
                traceFormat = line == "split,capture_id,stream,timestamp_ns,value"
                if (!traceFormat && line != "split,capture_id,raw_timestamp_ns,reference_timestamp_ns,raw,mv") {
                    return ParseResult(null, "欄位標題不符")
                }
                headerSeen = true
            } else {
                dataLines += line
                if (dataLines.size > MAX_ROWS) return ParseResult(null, "資料列超過上限")
            }
        }
        if (!headerSeen) return ParseResult(null, "缺少資料欄位標題")
        val instrument = metadata["reference_instrument"]?.takeIf(String::isNotBlank)
            ?: return ParseResult(null, "缺少 reference_instrument")
        val protocol = metadata["protocol_id"]?.takeIf(String::isNotBlank)
            ?: return ParseResult(null, "缺少 protocol_id")
        val sensorProfileFingerprint = metadata["watch_sensor_profile_fingerprint"]
            ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            ?: return ParseResult(null, "缺少有效 watch_sensor_profile_fingerprint（64 位 SHA-256）")
        val trainingId = metadata["training_capture_id"]?.takeIf(String::isNotBlank)
            ?: return ParseResult(null, "缺少 training_capture_id")
        val holdoutId = metadata["holdout_capture_id"]?.takeIf(String::isNotBlank)
            ?: return ParseResult(null, "缺少 holdout_capture_id")
        val maxRmse = metadata["maximum_allowed_rmse_mv"]?.toDoubleOrNull()
            ?.takeIf {
                it.isFinite() && it > 0.0 &&
                    it <= EcgCoreResearchEngine.MAX_ABSOLUTE_HOLDOUT_RMSE_MV
            } ?: return ParseResult(null, "留出 RMSE 門檻無效；研究上限為 ${EcgCoreResearchEngine.MAX_ABSOLUTE_HOLDOUT_RMSE_MV} mV")
        val maxPairSkew = metadata["maximum_pair_skew_ns"]?.toLongOrNull()
            ?.takeIf { it in 1L..1_000_000_000L } ?: return ParseResult(null, "maximum_pair_skew_ns 必須介於 1 ns 至 1 s")
        if (metadata["timestamp_basis"] != "shared_monotonic_ns") {
            return ParseResult(null, "timestamp_basis 必須是 shared_monotonic_ns")
        }
        if (trainingId == holdoutId) return ParseResult(null, "訓練與留出 capture ID 必須不同")

        val training = mutableListOf<PairPoint>()
        val holdout = mutableListOf<PairPoint>()
        if (traceFormat) {
            val traces = parseTraces(dataLines, trainingId, holdoutId, maxPairSkew)
            if (traces.error != null) return ParseResult(null, traces.error)
            training += traces.training
            holdout += traces.holdout
        } else for ((index, line) in dataLines.withIndex()) {
            val fields = line.split(',')
            if (fields.size != 6) return ParseResult(null, "第 ${index + 2} 列欄位數錯誤")
            val captureId = fields[1].trim()
            val target = when (fields[0].trim()) {
                "train" -> if (captureId == trainingId) training else null
                "holdout" -> if (captureId == holdoutId) holdout else null
                else -> null
            } ?: return ParseResult(null, "第 ${index + 2} 列 split 或 capture ID 不符")
            val rawTimestamp = fields[2].trim().toLongOrNull()?.takeIf { it > 0L }
                ?: return ParseResult(null, "第 ${index + 2} 列 raw 時間戳無效")
            val referenceTimestamp = fields[3].trim().toLongOrNull()?.takeIf { it > 0L }
                ?: return ParseResult(null, "第 ${index + 2} 列參考儀器時間戳無效")
            val raw = fields[4].trim().toDoubleOrNull()?.takeIf(Double::isFinite)
                ?: return ParseResult(null, "第 ${index + 2} 列 raw 無效")
            val mv = fields[5].trim().toDoubleOrNull()?.takeIf(Double::isFinite)
                ?: return ParseResult(null, "第 ${index + 2} 列 mV 無效")
            val previous = target.lastOrNull()
            if (previous != null && (rawTimestamp <= previous.rawTimestampNanos ||
                    referenceTimestamp <= previous.referenceTimestampNanos)) {
                return ParseResult(null, "每個 capture 的兩組時間戳都必須嚴格遞增")
            }
            val pair = PairPoint(rawTimestamp, referenceTimestamp, raw, mv)
            if (pair.synchronizationErrorNanos > maxPairSkew) {
                return ParseResult(null, "第 ${index + 2} 列兩端時間差超過預設同步門檻")
            }
            target += pair
        }
        if (training.size < EcgResearchProfileStore.MIN_POINTS || holdout.size < EcgResearchProfileStore.MIN_POINTS) {
            return ParseResult(null, "訓練及獨立留出資料各需至少 ${EcgResearchProfileStore.MIN_POINTS} 組同步點")
        }
        val maximumPoints = EcgResearchCalibration.MAX_RESEARCH_CALIBRATION_POINTS
        if (training.size > maximumPoints || holdout.size > maximumPoints) {
            return ParseResult(null, "訓練與留出資料各不可超過 $maximumPoints 組配對點")
        }
        val trainingPairs = training.map { it.raw to it.millivolts }.toSet()
        val holdoutPairs = holdout.map { it.raw to it.millivolts }.toSet()
        val overlapRatio = trainingPairs.intersect(holdoutPairs).size.toDouble() /
            minOf(trainingPairs.size, holdoutPairs.size).coerceAtLeast(1)
        if (overlapRatio >= 0.8) {
            return ParseResult(null, "訓練與留出配對高度重複，拒絕作為獨立驗證")
        }
        val trainingRawRange = training.first().rawTimestampNanos..training.last().rawTimestampNanos
        val holdoutRawRange = holdout.first().rawTimestampNanos..holdout.last().rawTimestampNanos
        val trainingReferenceRange = training.first().referenceTimestampNanos..training.last().referenceTimestampNanos
        val holdoutReferenceRange = holdout.first().referenceTimestampNanos..holdout.last().referenceTimestampNanos
        if (trainingRawRange.first <= holdoutRawRange.last && holdoutRawRange.first <= trainingRawRange.last ||
            trainingReferenceRange.first <= holdoutReferenceRange.last &&
            holdoutReferenceRange.first <= trainingReferenceRange.last) {
            return ParseResult(null, "訓練與獨立留出擷取時間區間重疊")
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(csv.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return ParseResult(Dataset(instrument, protocol, trainingId, holdoutId, maxRmse,
            maxPairSkew, sensorProfileFingerprint, training, holdout, traceFormat, digest), null)
    }

    private data class TraceParseResult(
        val training: List<PairPoint> = emptyList(),
        val holdout: List<PairPoint> = emptyList(),
        val error: String? = null
    )

    private fun parseTraces(
        lines: List<String>,
        trainingId: String,
        holdoutId: String,
        maximumPairSkewNanos: Long
    ): TraceParseResult {
        val traces = mutableMapOf<String, MutableList<EcgReferenceTraceAligner.Sample>>()
        var alignmentError: String? = null
        for ((index, line) in lines.withIndex()) {
            val fields = line.split(',')
            if (fields.size != 5) return TraceParseResult(error = "第 ${index + 2} 列欄位數錯誤")
            val split = fields[0].trim()
            val captureId = fields[1].trim()
            val stream = fields[2].trim()
            val expectedId = when (split) {
                "train" -> trainingId
                "holdout" -> holdoutId
                else -> return TraceParseResult(error = "第 ${index + 2} 列 split 無效")
            }
            if (captureId != expectedId || stream !in setOf("raw", "reference")) {
                return TraceParseResult(error = "第 ${index + 2} 列 capture ID 或 stream 不符")
            }
            val timestamp = fields[3].trim().toLongOrNull()?.takeIf { it > 0L }
                ?: return TraceParseResult(error = "第 ${index + 2} 列時間戳無效")
            val value = fields[4].trim().toDoubleOrNull()?.takeIf(Double::isFinite)
                ?: return TraceParseResult(error = "第 ${index + 2} 列樣本值無效")
            val key = "$split:$stream"
            val trace = traces.getOrPut(key) { mutableListOf() }
            if (trace.lastOrNull()?.let { timestamp <= it.timestampNanos } == true) {
                return TraceParseResult(error = "第 ${index + 2} 列同一波形時間戳未嚴格遞增")
            }
            trace += EcgReferenceTraceAligner.Sample(timestamp, value)
        }

        fun align(split: String): List<PairPoint>? {
            val raw = traces["$split:raw"] ?: return null
            val reference = traces["$split:reference"] ?: return null
            val result = EcgReferenceTraceAligner.align(
                raw,
                reference,
                maximumSkewNanos = maximumPairSkewNanos,
                maximumPairs = EcgResearchCalibration.MAX_RESEARCH_CALIBRATION_POINTS
            )
            if (result.error != null) {
                alignmentError = result.error
                return null
            }
            if (result.matchedPairCount < EcgResearchProfileStore.MIN_POINTS ||
                result.matchedPairCount.toDouble() / minOf(raw.size, reference.size) < MIN_TRACE_PAIR_COVERAGE
            ) {
                alignmentError = "$split 波形時間配對不足或覆蓋率低於 ${(MIN_TRACE_PAIR_COVERAGE * 100).toInt()}%"
                return null
            }
            return result.pairs.map {
                PairPoint(it.rawTimestampNanos, it.referenceTimestampNanos, it.raw, it.millivolts)
            }
        }

        val rawTraining = traces["train:raw"]
        val referenceTraining = traces["train:reference"]
        val rawHoldout = traces["holdout:raw"]
        val referenceHoldout = traces["holdout:reference"]
        if (rawTraining == null || referenceTraining == null || rawHoldout == null || referenceHoldout == null) {
            return TraceParseResult(error = "訓練及留出擷取都需包含 raw 與 reference 波形")
        }
        val training = align("train") ?: return TraceParseResult(error = alignmentError ?: "訓練波形配對失敗")
        val holdout = align("holdout") ?: return TraceParseResult(error = alignmentError ?: "留出波形配對失敗")
        return TraceParseResult(training, holdout)
    }

    private const val MIN_TRACE_PAIR_COVERAGE = 0.8
}
