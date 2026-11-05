package com.jaysato.xiaomihealthbridge.engine

import kotlin.math.abs
import kotlin.math.sqrt

object EcgRhythmPatternEngine {
    const val VERSION = "ecg-rr-pattern-research-v1"
    const val MIN_INTERVALS = 5

    enum class Label { RELATIVELY_REGULAR, VARIABLE, INCONCLUSIVE, INSUFFICIENT }

    data class Result(
        val label: Label,
        val title: String,
        val rrCount: Int,
        val bpm: Double?,
        val cv: Double?,
        val deltaCv: Double?,
        val pRr50: Double?,
        val normalizedRmssd: Double?,
        val method: String = VERSION
    )

    fun assess(rrIntervalsMs: List<Double>): Result {
        val hrv = HealthAlgorithmEngine.hrvFromRr(rrIntervalsMs)
        val bpm = hrv.bpm
        if (hrv.rrCount < MIN_INTERVALS || hrv.meanRrMs == null) {
            return Result(Label.INSUFFICIENT, "RR 型態資料不足（研究描述）", hrv.rrCount, bpm, null, null, null, null)
        }
        if (bpm == null || bpm !in 50.0..150.0) {
            return Result(Label.INCONCLUSIVE, "RR 型態無法判定（研究描述）", hrv.rrCount, bpm, null, null, null, null)
        }
        val cv = hrv.cvRr ?: return Result(Label.INSUFFICIENT, "RR 型態資料不足（研究描述）", hrv.rrCount, bpm, null, null, null, null)
        val valid = rrIntervalsMs.filter { it in 300.0..2000.0 }
        val deltas = valid.zipWithNext { first, second -> abs(second - first) }
        val deltaSd = if (deltas.isEmpty()) 0.0 else {
            val mean = deltas.average()
            sqrt(deltas.sumOf { (it - mean) * (it - mean) } / deltas.size)
        }
        val deltaCv = deltaSd / hrv.meanRrMs
        val pRr50 = hrv.pNn50 ?: return Result(Label.INSUFFICIENT, "RR 型態資料不足（研究描述）", hrv.rrCount, bpm, cv, deltaCv, null, hrv.nRmssd)
        val nRmssd = hrv.nRmssd ?: return Result(Label.INSUFFICIENT, "RR 型態資料不足（研究描述）", hrv.rrCount, bpm, cv, deltaCv, pRr50, null)
        val label = when {
            cv >= 0.12 && (deltaCv >= 0.10 || pRr50 >= 0.20 || nRmssd >= 0.12) -> Label.VARIABLE
            cv < 0.10 && pRr50 < 0.12 && nRmssd < 0.10 -> Label.RELATIVELY_REGULAR
            else -> Label.INCONCLUSIVE
        }
        val title = when (label) {
            Label.RELATIVELY_REGULAR -> "RR 間距相對一致（研究描述；不排除心律異常）"
            Label.VARIABLE -> "RR 間距變異偏高（研究描述；不是 AF 診斷）"
            Label.INCONCLUSIVE -> "RR 型態無法判定（研究描述）"
            Label.INSUFFICIENT -> "RR 型態資料不足（研究描述）"
        }
        return Result(label, title, hrv.rrCount, bpm, cv, deltaCv, pRr50, nRmssd)
    }
}
