package com.jaysato.xiaomihealthbridge.engine

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

object HealthAlgorithmEngine {
    const val VERSION = "0.1.0"
    private const val MIN_VALID_PPG_RATE_HZ = 25.0
    const val SEA_LEVEL_HPA = 1013.25
    const val OPTICAL_FILL = 524287f

    data class HrvMetrics(
        val meanRrMs: Double?,
        val bpm: Double?,
        val sdnnMs: Double?,
        val rmssdMs: Double?,
        val nRmssd: Double?,
        val pNn50: Double?,
        val cvRr: Double?,
        val rrCount: Int,
        val citations: List<String>
    )

    data class OpticalMetrics(
        val ac: Double?,
        val dc: Double?,
        val perfusionIndexPercent: Double?,
        val pulseRateBpm: Double?,
        val rrIntervalsMs: List<Double>,
        val ratioOfRatiosR: Double?,
        val note: String,
        val relativeAcDcRatio: Double? = null
    )

    data class PressureMetrics(
        val pressureHpa: Double?,
        val altitudeM: Double?,
        val note: String
    )

    fun hrvFromRr(rrMs: List<Double>): HrvMetrics {
        val clean = rrMs.filter { it in 300.0..2000.0 }
        if (clean.size < 5) {
            return HrvMetrics(null, null, null, null, null, null, null, clean.size, citations())
        }
        val mean = clean.average()
        val sdnn = sqrt(clean.sumOf { (it - mean) * (it - mean) } / clean.size)
        val deltas = clean.zipWithNext { first, second -> abs(second - first) }
        val rmssd = if (deltas.isEmpty()) null else sqrt(deltas.sumOf { it * it } / deltas.size)
        val nRmssd = rmssd?.let { it / mean }
        val pNn50 = if (deltas.isEmpty()) null else deltas.count { it >= 50.0 }.toDouble() / deltas.size
        return HrvMetrics(
            meanRrMs = mean,
            bpm = 60_000.0 / mean,
            sdnnMs = sdnn,
            rmssdMs = rmssd,
            nRmssd = nRmssd,
            pNn50 = pNn50,
            cvRr = sdnn / mean,
            rrCount = clean.size,
            citations = citations()
        )
    }

    fun opticalFromWaveform(
        waveform: List<Float>,
        sampleRateHz: Double,
        redIrPairs: List<Pair<Double, Double>> = emptyList(),
        channelMappingVerified: Boolean = false
    ): OpticalMetrics {
        val samples = waveform.filter { it.isFinite() && it != OPTICAL_FILL }
        if (samples.size < 64) {
            return OpticalMetrics(null, null, null, null, emptyList(), null, "光學有效樣本不足 64 點；只保留原始波形")
        }
        val dc = samples.average().toDouble()
        val ac = samples.map { abs(it - dc) }.average()
        val relativeAcDc = if (abs(dc) > 1e-6) ac / abs(dc) else null
        val relativeEvidence = relativeAcDc?.takeIf(Double::isFinite)
        if (!sampleRateHz.isFinite() || sampleRateHz < MIN_VALID_PPG_RATE_HZ) {
            return OpticalMetrics(
                ac, dc, null, null, emptyList(), null,
                "沒有可信 PPG 取樣時基（至少 25 Hz）；不推算脈率／HRV。相對 AC/DC 僅為本段無單位描述指標，不是灌流指數或生理值。",
                relativeEvidence
            )
        }
        if (!channelMappingVerified) {
            return OpticalMetrics(
                ac, dc, null, null, emptyList(), null,
                "感測器通道映射未經裝置驗證；不推算脈率／HRV／PI／SpO₂。相對 AC/DC 僅為本段無單位描述指標，不是灌流指數或生理值。",
                relativeEvidence
            )
        }
        val pi = if (abs(dc) > 1e-6) 100.0 * ac / abs(dc) else null
        val rr = peakIntervalsMs(samples, sampleRateHz)
        val pr = hrvFromRr(rr).bpm
        val ratio = if (redIrPairs.size >= 8) {
            val redAcDc = channelAcDc(redIrPairs.map { it.first })
            val irAcDc = channelAcDc(redIrPairs.map { it.second })
            if (redAcDc != null && irAcDc != null && irAcDc > 1e-9) redAcDc / irAcDc else null
        } else null
        val note = buildString {
            append("PI%=100·AC/DC；脈率由波峰間距。")
            if (ratio == null) append(" R（紅／紅外比値法）需雙波長 AC/DC；本機單通道時僅輸出 PI／脈率，不輸出 SpO₂%。")
            else append(" R=${"%.3f".format(ratio)} 僅為研究比値，未套 OEM 校準曲線 → 不轉 SpO₂%。")
        }
        return OpticalMetrics(ac, dc, pi, pr, rr, ratio, note, relativeEvidence)
    }

    fun pressureAltitude(pressureHpa: Double?): PressureMetrics {
        if (pressureHpa == null || !pressureHpa.isFinite() || pressureHpa <= 0.0) {
            return PressureMetrics(null, null, "無有效氣壓")
        }
        val ratio = (pressureHpa / SEA_LEVEL_HPA).coerceIn(0.1, 1.2)
        val altitude = 44330.77 * (1.0 - ratio.pow(0.190266))
        return PressureMetrics(
            pressureHpa,
            altitude,
            "ISA：h=44330.77·(1−(P/1013.25)^0.190266)；標準大氣壓力高度估算，非實際海拔／爬升"
        )
    }

    fun summaryLine(optical: OpticalMetrics?, hrv: HrvMetrics?, pressure: PressureMetrics?): String =
        buildString {
            optical?.perfusionIndexPercent?.let { append("PI ${"%.2f".format(it)}% · ") }
            optical?.relativeAcDcRatio?.let { append("相對 AC/DC ${"%.4f".format(it)} · ") }
            optical?.pulseRateBpm?.let { append("脈率 ${"%.0f".format(it)} · ") }
            optical?.ratioOfRatiosR?.let { append("R ${"%.3f".format(it)} · ") }
            hrv?.rmssdMs?.let { append("RMSSD ${"%.0f".format(it)}ms · ") }
            hrv?.nRmssd?.let { append("nRMSSD ${"%.3f".format(it)} · ") }
            hrv?.sdnnMs?.let { append("SDNN ${"%.0f".format(it)}ms · ") }
            researchStressScore(hrv?.nRmssd, hrv?.bpm)?.let { append("壓力指數 $it · ") }
            pressure?.altitudeM?.let { append("高度 ${"%.1f".format(it)}m · ") }
            if (isEmpty()) append("尚無公式輸出") else setLength(length - 3)
        }

    fun researchStressScore(nRmssd: Double?, pulseBpm: Double?): Int? {
        if (nRmssd == null || !nRmssd.isFinite() || nRmssd !in 0.0..2.0) return null
        if (pulseBpm != null && (!pulseBpm.isFinite() || pulseBpm !in 30.0..240.0)) return null
        val hrvPart = (1.0 - (nRmssd / 0.20).coerceIn(0.0, 1.0)) * 70.0
        val hrPart = pulseBpm?.let { ((it - 50.0) / 50.0).coerceIn(0.0, 1.0) * 30.0 } ?: 15.0
        return (hrvPart + hrPart).toInt().coerceIn(0, 100)
    }

    private fun channelAcDc(values: List<Double>): Double? {
        if (values.size < 8) return null
        val dc = values.average()
        if (abs(dc) < 1e-9) return null
        val ac = values.map { abs(it - dc) }.average()
        return ac / abs(dc)
    }

    private fun peakIntervalsMs(samples: List<Float>, fs: Double): List<Double> {
        if (samples.size < 16 || fs <= 1.0) return emptyList()
        val mean = samples.average()
        val threshold = mean + (samples.max() - mean) * 0.35f
        val minGap = (fs * 0.30).toInt().coerceAtLeast(2)
        val peaks = ArrayList<Int>()
        var index = 1
        while (index < samples.lastIndex) {
            if (samples[index] >= threshold && samples[index] >= samples[index - 1] && samples[index] >= samples[index + 1]) {
                if (peaks.isEmpty() || index - peaks.last() >= minGap) peaks.add(index)
                index += minGap
            } else index++
        }
        return peaks.zipWithNext { first, second -> (second - first) * 1000.0 / fs }.filter { it in 300.0..2000.0 }
    }

    private fun citations() = listOf(
        "HRV: SDNN/RMSSD/pNN50（短時 HRV 文獻）",
        "nRMSSD=RMSSD/meanRR（穿戴 PPG AF 研究常用正規化）",
        "Tateno & Glass 2001：CV(RR)/CV(ΔRR)",
        "Orphanidou et al. IEEE JBHI 2015：SQI 模板相關"
    )
}
