package com.jaysato.xiaomihealthbridge

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Research-only irregular-rhythm / possible-AF screening from RR intervals.
 *
 * References:
 * - Tateno & Glass, Med Biol Eng Comput 2001 (CV of RR / ΔRR)
 * - Short-term HRV: RMSSD, pRR50, SDNN; nRMSSD = RMSSD/meanRR (Sci Rep 2019 PPG AF)
 * - Apple Watch AF IFU: 50–150 bpm inconclusive band mirrored as gate only
 */
object WearRhythmScreening {
    private const val MIN_VALID_RR_INTERVALS = 5

    enum class Label {
        REGULAR_PATTERN,
        IRREGULAR_POSSIBLE,
        INCONCLUSIVE,
        INSUFFICIENT
    }

    data class Result(
        val label: Label,
        val title: String,
        val detail: String,
        val bpm: Double?,
        val rrCount: Int,
        val cv: Double?,
        val deltaCv: Double?,
        val pRr50: Double?,
        val rmssdMs: Double?,
        val nRmssd: Double? = null,
        val sdnnMs: Double? = null,
        val researchOnly: Boolean = true
    )

    fun assess(rrMs: List<Double>): Result {
        val hrv = com.jaysato.xiaomihealthbridge.engine.HealthAlgorithmEngine.hrvFromRr(rrMs)
        if (hrv.rrCount < MIN_VALID_RR_INTERVALS || hrv.meanRrMs == null) {
            return Result(
                Label.INSUFFICIENT, "資料不足",
                "研究級 RR 統計至少需要 $MIN_VALID_RR_INTERVALS 個有效間隔；目前 ${hrv.rrCount} 個。請繼續接觸電極。",
                null, hrv.rrCount, null, null, null, null
            )
        }
        val mean = hrv.meanRrMs ?: return Result(
            Label.INSUFFICIENT, "資料不足", "RR 平均無效。", null, hrv.rrCount, null, null, null, null
        )
        val bpm = hrv.bpm ?: return Result(
            Label.INSUFFICIENT, "資料不足", "RR 平均無效。", null, hrv.rrCount, null, null, null, null
        )
        if (bpm !in 50.0..150.0) {
            return Result(
                Label.INCONCLUSIVE, "無法判定",
                "估心率 ${"%.0f".format(bpm)} bpm 超出常見篩檢窗 50–150；延長靜止並保持電極接觸後重試。",
                bpm, hrv.rrCount, null, null, null, null, hrv.nRmssd, hrv.sdnnMs
            )
        }
        val cv = hrv.cvRr ?: return insufficientMetrics(hrv.rrCount)
        val deltas = rrMs.filter { it in 300.0..2000.0 }.zipWithNext { a, b -> abs(b - a) }
        val deltaSd = if (deltas.isEmpty()) 0.0 else {
            val dm = deltas.average()
            sqrt(deltas.sumOf { (it - dm) * (it - dm) } / deltas.size)
        }
        val deltaCv = deltaSd / mean
        val pRr50 = hrv.pNn50 ?: return insufficientMetrics(hrv.rrCount)
        val rmssd = hrv.rmssdMs ?: return insufficientMetrics(hrv.rrCount)
        val nRmssd = hrv.nRmssd ?: return insufficientMetrics(hrv.rrCount)
        val sdnn = hrv.sdnnMs ?: return insufficientMetrics(hrv.rrCount)
        // Literature-inspired gates (not validated on Watch 2 Pro).
        val irregular = cv >= 0.12 && (deltaCv >= 0.10 || pRr50 >= 0.20 || nRmssd >= 0.12)
        val label = when {
            irregular -> Label.IRREGULAR_POSSIBLE
            cv < 0.10 && pRr50 < 0.12 && nRmssd < 0.10 -> Label.REGULAR_PATTERN
            else -> Label.INCONCLUSIVE
        }
        val title = when (label) {
            Label.IRREGULAR_POSSIBLE -> "RR 間距不規則（研究；非 AF 判讀）"
            Label.REGULAR_PATTERN -> "RR 間距規則（研究；不排除心律異常）"
            Label.INCONCLUSIVE -> "心律結果不明"
            Label.INSUFFICIENT -> "資料不足"
        }
        val detail = buildString {
            append("估心率 ${"%.0f".format(bpm)} bpm · RR ${hrv.rrCount}")
            append(" · CV ${"%.3f".format(cv)} · ΔRR-CV ${"%.3f".format(deltaCv)}")
            append(" · pRR50 ${"%.0f".format(pRr50 * 100)}%")
            append(" · RMSSD ${"%.0f".format(rmssd)} · nRMSSD ${"%.3f".format(nRmssd)}")
            append(" · SDNN ${"%.0f".format(sdnn)} ms\n")
            append("公式：Tateno–Glass CV；短時 HRV RMSSD/pNN50/SDNN；nRMSSD=RMSSD/meanRR（Sci Rep 2019 等）。")
            append("非醫療診斷、非原廠 AF。胸痛／暈厥請就醫。")
        }
        return Result(
            label, title, detail, bpm, hrv.rrCount, cv, deltaCv, pRr50, rmssd,
            nRmssd, hrv.sdnnMs
        )
    }

    private fun insufficientMetrics(rrCount: Int) = Result(
        Label.INSUFFICIENT,
        "資料不足",
        "有效 RR 特徵不足，未執行研究篩檢（$rrCount 個間隔）。",
        null,
        rrCount,
        null,
        null,
        null,
        null
    )
}
