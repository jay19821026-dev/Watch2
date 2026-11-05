package com.jaysato.xiaomihealthbridge

import kotlin.math.abs
import kotlin.math.sqrt

data class VascularResearchResult(
    val vascularAgeYears: Int?,
    val appNormalizedApgIndex: Double?,
    val acceptedBeatCount: Int,
    val evidenceScore: Int?,
    val ppgQualityScore: Int?,
    val trend: String,
    val source: String,
    val method: String,
    val limitations: String,
    val reasonCode: String
)

internal object VascularResearchEngine {
    const val VERSION = "ppg-app-normalized-apg-index-v2"
    private const val MIN_SAMPLE_RATE_HZ = 100.0
    private const val MIN_CAPTURE_SECONDS = 15.0
    private const val MIN_ACCEPTED_BEATS = 10
    private const val RESAMPLED_BEAT_SIZE = 128
    private const val MAX_WAVEFORM_SAMPLES = 100_000

    fun fromPpg(
        qualityScore: Int,
        rawValueCount: Int,
        status: String,
        waveform: List<Float> = emptyList(),
        sampleRateHz: Float = 0f,
        channelMappingVerified: Boolean = false,
        wearQualified: Boolean = false,
        timestampSource: String = "UNKNOWN"
    ): VascularResearchResult {
        val inputError = when {
            waveform.size > MAX_WAVEFORM_SAMPLES -> "INVALID_OR_OVERSIZED_WAVEFORM"
            waveform.any { it == 524287f } -> "PPG_GAP_PRESENT"
            waveform.any { !it.isFinite() } -> "INVALID_OR_OVERSIZED_WAVEFORM"
            else -> null
        }
        val usable = rawValueCount >= 64 && status in setOf(
            "valid_signal", "research_optical", "research_signal_unscaled"
        )
        val quality = qualityScore.takeIf { usable && it in 1..100 }
        val indexResult = when {
            inputError != null -> ApgIndexResult(null, 0, inputError)
            !usable -> ApgIndexResult(null, 0, "INSUFFICIENT_PPG_EVIDENCE")
            quality == null || quality < 80 -> ApgIndexResult(null, 0, "LOW_PPG_QUALITY")
            !channelMappingVerified -> ApgIndexResult(null, 0, "PPG_CHANNEL_MAPPING_UNVERIFIED")
            !wearQualified -> ApgIndexResult(null, 0, "WEAR_NOT_CONFIRMED")
            timestampSource != "android_sensor_event_elapsed_realtime" ->
                ApgIndexResult(null, 0, "SENSOR_TIME_UNVERIFIED")
            !sampleRateHz.isFinite() || sampleRateHz < MIN_SAMPLE_RATE_HZ ->
                ApgIndexResult(null, 0, "SAMPLE_RATE_BELOW_RESEARCH_GATE")
            waveform.size < (sampleRateHz * MIN_CAPTURE_SECONDS).toInt() ->
                ApgIndexResult(null, 0, "CAPTURE_TOO_SHORT")
            else -> calculateApgIndex(waveform, sampleRateHz.toDouble())
        }
        val evidenceScore = indexResult.index?.let {
            minOf(quality ?: 0, (indexResult.acceptedBeats * 5).coerceAtMost(100))
                .takeIf { score -> score in 1..100 }
        }
        return VascularResearchResult(
            vascularAgeYears = null,
            appNormalizedApgIndex = indexResult.index,
            acceptedBeatCount = indexResult.acceptedBeats,
            evidenceScore = evidenceScore,
            ppgQualityScore = quality,
            trend = "尚未建立可比較的個人基線",
            source = "PPG 波形／${timestampSource}",
            method = if (indexResult.index != null) {
                "程式自訂正規化 APG 形態指數=(b−c−d−e)/a；${VERSION}；公式參考 SDPPG 文獻，但濾波及 a–e 波定位未與論文流程驗證，不可直接比較論文 AGI；不換算血管年齡或診斷"
            } else {
                "程式自訂正規化 APG 形態指數=(b−c−d−e)/a；${VERSION}；濾波及 a–e 波定位未與論文流程驗證；不換算血管年齡；目前未通過證據閘門：${indexResult.reasonCode}"
            },
            limitations = "參考研究使用指尖 PPG；本腕式通道未完成跨部位／裝置驗證。文獻 SDPPG 指數亦受年齡、性別、心率與血壓影響；此程式指數不是已驗證的 SDPPG AGI，不換算血管年齡或疾病風險。",
            reasonCode = indexResult.reasonCode
        )
    }

    private data class ApgIndexResult(val index: Double?, val acceptedBeats: Int, val reasonCode: String)

    private fun calculateApgIndex(waveform: List<Float>, sampleRateHz: Double): ApgIndexResult {
        if (waveform.size > MAX_WAVEFORM_SAMPLES || waveform.any { !it.isFinite() }) {
            return ApgIndexResult(null, 0, "INVALID_OR_OVERSIZED_WAVEFORM")
        }
        val values = waveform.map(Float::toDouble)
        val amplitude = (values.maxOrNull() ?: 0.0) - (values.minOrNull() ?: 0.0)
        if (!amplitude.isFinite() || amplitude <= 1e-6) return ApgIndexResult(null, 0, "FLATLINE")
        val peaks = detectPeaks(values, sampleRateHz, amplitude)
        if (peaks.size < MIN_ACCEPTED_BEATS + 2) return ApgIndexResult(null, 0, "INSUFFICIENT_PULSE_PEAKS")
        val beatAgi = peaks.indices.drop(1).dropLast(1).mapNotNull { index ->
            val peak = peaks[index]
            val previousPeak = peaks[index - 1]
            val nextPeak = peaks[index + 1]
            val left = localMinimum(values, previousPeak, peak)
            val right = localMinimum(values, peak, nextPeak)
            if (right - left < sampleRateHz * 0.30 || right - left > sampleRateHz * 1.50) return@mapNotNull null
            val pulse = resampleNormalized(values.subList(left, right + 1), RESAMPLED_BEAT_SIZE)
                ?: return@mapNotNull null
                appNormalizedApgIndexFromPulse(pulse)
        }.filter(Double::isFinite)
        if (beatAgi.size < MIN_ACCEPTED_BEATS) return ApgIndexResult(null, beatAgi.size, "INSUFFICIENT_APG_FIDUCIALS")
        val sorted = beatAgi.sorted()
        val median = sorted[sorted.size / 2]
        return ApgIndexResult(median, beatAgi.size, "APP_APG_INDEX_RESEARCH_OUTPUT")
    }

    private fun detectPeaks(values: List<Double>, sampleRateHz: Double, amplitude: Double): List<Int> {
        val minimumDistance = (sampleRateHz * 60.0 / 180.0).toInt().coerceAtLeast(1)
        val threshold = (values.minOrNull() ?: 0.0) + amplitude * 0.35
        val peaks = mutableListOf<Int>()
        for (index in 1 until values.lastIndex) {
            if (values[index] < threshold || values[index] < values[index - 1] || values[index] <= values[index + 1]) continue
            if (peaks.isEmpty() || index - peaks.last() >= minimumDistance) peaks += index
            else if (values[index] > values[peaks.last()]) peaks[peaks.lastIndex] = index
        }
        return peaks
    }

    private fun localMinimum(values: List<Double>, from: Int, to: Int): Int {
        var minimumIndex = from
        for (index in from + 1 until to) if (values[index] < values[minimumIndex]) minimumIndex = index
        return minimumIndex
    }

    private fun resampleNormalized(values: List<Double>, size: Int): List<Double>? {
        val minimum = values.minOrNull() ?: return null
        val range = (values.maxOrNull() ?: return null) - minimum
        if (!range.isFinite() || range <= 1e-9) return null
        return List(size) { index ->
            val position = index.toDouble() * (values.size - 1) / (size - 1)
            val left = position.toInt()
            val fraction = position - left
            val right = minOf(left + 1, values.lastIndex)
            ((values[left] + (values[right] - values[left]) * fraction) - minimum) / range
        }
    }

    internal fun appNormalizedApgIndexFromPulse(pulse: List<Double>): Double? {
        val secondDerivative = (1 until pulse.lastIndex).map { index ->
            pulse[index - 1] - 2.0 * pulse[index] + pulse[index + 1]
        }
        val extrema = (1 until secondDerivative.lastIndex).mapNotNull { index ->
            val before = secondDerivative[index - 1]
            val current = secondDerivative[index]
            val after = secondDerivative[index + 1]
            when {
                current > 0.0 && current >= before && current > after -> index + 1 to 1
                current < 0.0 && current <= before && current < after -> index + 1 to -1
                else -> null
            }
        }
        val aIndex = extrema.indexOfFirst { (position, sign) ->
            sign > 0 && position in 4..(pulse.size * 3 / 5)
        }
        if (aIndex < 0 || aIndex + 4 >= extrema.size) return null
        val points = extrema.subList(aIndex, extrema.size).take(5)
        if (points.map { it.second } != listOf(1, -1, 1, -1, 1)) return null
        val a = secondDerivative[points[0].first - 1]
        val b = secondDerivative[points[1].first - 1]
        val c = secondDerivative[points[2].first - 1]
        val d = secondDerivative[points[3].first - 1]
        val e = secondDerivative[points[4].first - 1]
        val derivativeScale = secondDerivative.maxOfOrNull { abs(it) } ?: return null
        if (!a.isFinite() || !derivativeScale.isFinite() || derivativeScale <= 0.0 ||
            a <= derivativeScale * sqrt(Math.ulp(1.0)) || c >= a || e >= a) return null
        return ((b - c - d - e) / a).takeIf(Double::isFinite)
    }
}
