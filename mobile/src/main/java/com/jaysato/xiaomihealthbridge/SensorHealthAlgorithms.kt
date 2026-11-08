package com.jaysato.xiaomihealthbridge

import java.time.Duration
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.Instant
import androidx.health.connect.client.records.SleepSessionRecord
import kotlin.math.*

object SensorHealthAlgorithms {
    data class PersonalBaselineDeviation(
        val baseline: Double,
        val latest: Double,
        val difference: Double,
        val baselineDays: Int
    )

    data class OxygenSampleSummary(
        val validSampleCount: Int,
        val minimumPercent: Double,
        val maximumPercent: Double,
        val below90Percent: Double?,
        val firstValidSampleAt: Instant? = null,
        val lastValidSampleAt: Instant? = null
    )

    fun intervalDurationMinutes(start: Instant, end: Instant): Double? {
        if (!start.isBefore(end)) return null
        val duration = Duration.between(start, end)
        return (duration.seconds + duration.nano / 1_000_000_000.0) / 60.0
    }

    fun banisterTrimp(durationMinutes: Double, averageHeartRate: Double, restingHeartRate: Double, maximumHeartRate: Double, sex: PhysiologyAlgorithms.Sex): Double? {
        if (!durationMinutes.isFinite() || !averageHeartRate.isFinite() || !restingHeartRate.isFinite() || !maximumHeartRate.isFinite() ||
            durationMinutes <= 0 || maximumHeartRate <= restingHeartRate || averageHeartRate <= restingHeartRate) return null
        val reserve = ((averageHeartRate - restingHeartRate) / (maximumHeartRate - restingHeartRate)).coerceIn(0.0, 1.0)
        val (scale, factor) = when (sex) {
            PhysiologyAlgorithms.Sex.MALE -> 0.64 to 1.92
            PhysiologyAlgorithms.Sex.FEMALE -> 0.86 to 1.67
            PhysiologyAlgorithms.Sex.UNSPECIFIED -> return null
        }
        return (durationMinutes * reserve * scale * exp(factor * reserve)).takeIf { it.isFinite() }
    }

    fun sleepRegularity(sessions: List<SleepSessionRecord>, zone: ZoneId = ZoneId.systemDefault()): Int? {
        if (sessions.size < 2) return null
        return sleepScheduleConsistency(sessions.map { it.startTime to it.endTime }, zone)
    }

    internal fun sleepScheduleConsistency(intervals: List<Pair<Instant, Instant>>, zone: ZoneId): Int? {
        val validIntervals = intervals.filter { (start, end) -> start.isBefore(end) }
        if (validIntervals.size < 2) return null
        val firstDate = validIntervals.minOf { it.first }.atZone(zone).toLocalDate()
        val lastDate = validIntervals.maxOf { it.second }.atZone(zone).toLocalDate()
        val dates = generateSequence(firstDate) { date -> date.plusDays(1).takeIf { it <= lastDate } }.toList()
        if (dates.size < 2) return null
        fun hasSleepRecordOn(date: java.time.LocalDate): Boolean {
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
            if (Duration.between(dayStart, dayEnd) != Duration.ofHours(24)) return false
            return validIntervals.any { (start, end) -> start < dayEnd && end > dayStart }
        }
        var matches = 0
        var comparisons = 0
        dates.zipWithNext().forEach { (firstDate, secondDate) ->
            if (!hasSleepRecordOn(firstDate) || !hasSleepRecordOn(secondDate)) return@forEach
            val first = BooleanArray(48)
            val second = BooleanArray(48)
            validIntervals.forEach { markSleep(first, it.first, it.second, firstDate, zone) }
            validIntervals.forEach { markSleep(second, it.first, it.second, secondDate, zone) }
            first.indices.forEach { index ->
                if (first[index] == second[index]) matches++
                comparisons++
            }
        }
        if (comparisons == 0) return null
        return (-100 + 200.0 * matches / comparisons).roundToInt().coerceIn(0, 100)
    }

    fun oxygenSampleSummary(values: List<Double>): OxygenSampleSummary? {
        val clean = values.filter { it.isFinite() && it > 0.0 && it <= 100.0 }
        if (clean.isEmpty()) return null
        val below90Percent = if (clean.size >= 3) clean.count { it < 90.0 } * 100.0 / clean.size else null
        return OxygenSampleSummary(clean.size, clean.min(), clean.max(), below90Percent)
    }

    fun timedOxygenSampleSummary(samples: List<Pair<Instant, Double>>): OxygenSampleSummary? {
        val clean = samples.filter { (_, value) -> value.isFinite() && value > 0.0 && value <= 100.0 }
        val summary = oxygenSampleSummary(clean.map { it.second }) ?: return null
        return summary.copy(
            firstValidSampleAt = clean.minOf { it.first },
            lastValidSampleAt = clean.maxOf { it.first }
        )
    }

    fun temperatureDeviationByDay(values: List<Pair<java.time.LocalDate, Double>>): Double? {
        val dailyMedians = values.asSequence()
            .filter { (_, value) -> value.isFinite() && value in 20.0..45.0 }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, readings) -> median(readings) }
            .toSortedMap()
        if (dailyMedians.size < 4) return null
        val priorDays = dailyMedians.values.toList().dropLast(1)
        return dailyMedians.values.last() - median(priorDays)
    }

    fun hrvDeviationFromPersonalBaseline(values: List<Pair<java.time.LocalDate, Double>>): PersonalBaselineDeviation? {
        val dailyMedians = values.asSequence()
            .filter { (_, value) -> value.isFinite() && value > 0.0 }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, readings) -> median(readings) }
            .toSortedMap()
        if (dailyMedians.size < 3) return null
        val dailyEntries = dailyMedians.entries.toList()
        val latestEntry = dailyEntries.last()
        val baselineValues = dailyEntries.dropLast(1).takeLast(6).map { it.value }
        if (baselineValues.size < 2) return null
        val baseline = median(baselineValues)
        return PersonalBaselineDeviation(
            baseline = baseline,
            latest = latestEntry.value,
            difference = latestEntry.value - baseline,
            baselineDays = baselineValues.size
        )
    }

    private fun median(values: List<Double>): Double {
        val ordered = values.sorted()
        val middle = ordered.size / 2
        return if (ordered.size % 2 == 0) (ordered[middle - 1] + ordered[middle]) / 2.0 else ordered[middle]
    }

    fun gaitEfficiency(distanceMeters: Double, steps: Long): Double? =
        if (distanceMeters.isFinite() && distanceMeters > 0.0 && steps > 0) {
            (distanceMeters / steps).takeIf { it.isFinite() && it > 0.0 }
        } else null

    fun cadence(steps: Long, activeMinutes: Double): Double? =
        if (steps > 0 && activeMinutes > 0) steps / activeMinutes else null

    fun activityRegularity(dailySteps: List<Long>): Int? {
        val clean = dailySteps.filter { it >= 0 }
        if (clean.size < 3 || clean.average() <= 0.0) return null
        val mean = clean.average()
        val standardDeviation = sqrt(clean.sumOf { (it - mean).pow(2) } / clean.size)
        return (100 - standardDeviation / mean * 100).roundToInt().coerceIn(0, 100)
    }

    fun restingHeartRateDeviation(values: List<Double>): Double? {
        val clean = values.filter { it in 30.0..220.0 }
        if (clean.size < 3) return null
        val latest = clean.last()
        val historical = clean.dropLast(1).sorted()
        val median = if (historical.size % 2 == 0) (historical[historical.size / 2 - 1] + historical[historical.size / 2]) / 2 else historical[historical.size / 2]
        return latest - median
    }

    fun cardiovascularDrift(values: List<Double>): Double? {
        val clean = values.filter { it in 30.0..240.0 }
        if (clean.size < 10) return null
        val middle = clean.size / 2
        val firstHalf = clean.take(middle).average()
        val secondHalf = clean.drop(middle).average()
        return if (firstHalf > 0.0) (secondHalf - firstHalf) / firstHalf * 100.0 else null
    }

    fun socialJetlagMinutes(sessions: List<SleepSessionRecord>, zone: ZoneId = ZoneId.systemDefault()): Int? {
        val midpoints = sessions.map { session ->
            val midpoint = session.startTime.plusMillis(Duration.between(session.startTime, session.endTime).toMillis() / 2)
            val local = midpoint.atZone(zone)
            val minute = local.hour * 60 + local.minute
            local.dayOfWeek to minute
        }
        val work = midpoints.filter { it.first !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.map { it.second }
        val free = midpoints.filter { it.first in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.map { it.second }
        if (work.size < 2 || free.isEmpty()) return null
        val difference = abs(circularMeanMinutes(work) - circularMeanMinutes(free)).roundToInt()
        return min(difference, 1440 - difference)
    }

    fun recordedSleepDurationGapMinutes(sessions: List<SleepSessionRecord>, age: Int, zone: ZoneId = ZoneId.systemDefault()): Int? {
        if (sessions.isEmpty()) return null
        val target = if (age in 13..17) 9 * 60 else 8 * 60
        val nightly = sessions.groupBy { it.endTime.atZone(zone).toLocalDate() }
            .values.map { records -> unionDurationMinutes(records.map { it.startTime to it.endTime }) }
        if (nightly.size < 3) return null
        return nightly.sumOf { (target - it).coerceAtLeast(0) }.toInt()
    }

    fun unionDurationMinutes(intervals: List<Pair<Instant, Instant>>): Long {
        return mergeIntervals(intervals).sumOf { (start, end) -> Duration.between(start, end).toMillis() } / 60_000L
    }

    fun mergeIntervals(intervals: List<Pair<Instant, Instant>>): List<Pair<Instant, Instant>> {
        val ordered = intervals.filter { (start, end) -> end.isAfter(start) }.sortedBy { it.first }
        if (ordered.isEmpty()) return emptyList()
        var mergedStart = ordered.first().first
        var mergedEnd = ordered.first().second
        val merged = mutableListOf<Pair<Instant, Instant>>()
        ordered.drop(1).forEach { (start, end) ->
            if (!start.isAfter(mergedEnd)) {
                if (end.isAfter(mergedEnd)) mergedEnd = end
            } else {
                merged += mergedStart to mergedEnd
                mergedStart = start
                mergedEnd = end
            }
        }
        merged += mergedStart to mergedEnd
        return merged
    }

    fun trainingMonotony(dailyLoad: List<Double>): Pair<Double, Double>? {
        if (dailyLoad.size < 7) return null
        val mean = dailyLoad.average()
        if (mean <= 0.0) return null
        val standardDeviation = sqrt(dailyLoad.sumOf { (it - mean).pow(2) } / dailyLoad.size)
        if (standardDeviation < 1e-6) return null
        val monotony = mean / standardDeviation
        return monotony to dailyLoad.sum() * monotony
    }

    fun heartRateReserveEquivalentMinutes(
        samples: List<Pair<Instant, Double>>,
        restingHeartRate: Double,
        maximumHeartRate: Double
    ): Int? {
        if (samples.size < 2 || maximumHeartRate <= restingHeartRate) return null
        val ordered = samples.filter { it.second in 30.0..240.0 }
            .groupBy { it.first }
            .toSortedMap()
            .map { (time, readings) -> time to readings.map { it.second }.average() }
        var totalEquivalentMinutes = 0.0
        var usableIntervals = 0
        ordered.zipWithNext().forEach { (first, second) ->
            val elapsedMillis = Duration.between(first.first, second.first).toMillis()
            if (elapsedMillis !in 1..MAX_HEART_RATE_INTERVAL_MILLIS) return@forEach
            val averageHeartRate = (first.second + second.second) / 2.0
            val reserve = (averageHeartRate - restingHeartRate) / (maximumHeartRate - restingHeartRate)
            val minutes = elapsedMillis / MILLIS_PER_MINUTE
            when {
                reserve >= VIGOROUS_HEART_RATE_RESERVE -> totalEquivalentMinutes += minutes * 2.0
                reserve >= MODERATE_HEART_RATE_RESERVE -> totalEquivalentMinutes += minutes
            }
            usableIntervals++
        }
        return totalEquivalentMinutes.roundToInt().takeIf { usableIntervals > 0 }
    }

    private fun markSleep(target: BooleanArray, startTime: Instant, endTime: Instant, date: java.time.LocalDate, zone: ZoneId) {
        val dayStart = date.atStartOfDay(zone).toInstant()
        target.indices.forEach { index ->
            val epochStart = dayStart.plus(Duration.ofMinutes(index * 30L))
            val epochEnd = epochStart.plus(Duration.ofMinutes(30))
            target[index] = target[index] || (startTime < epochEnd && endTime > epochStart)
        }
    }

    private fun circularMeanMinutes(values: List<Int>): Double {
        val radians = values.map { it / 1440.0 * 2 * Math.PI }
        val angle = atan2(radians.sumOf(::sin), radians.sumOf(::cos))
        return ((if (angle < 0) angle + 2 * Math.PI else angle) / (2 * Math.PI) * 1440.0)
    }

    private const val MAX_HEART_RATE_INTERVAL_MILLIS = 5 * 60 * 1000L
    private const val MILLIS_PER_MINUTE = 60_000.0
    private const val MODERATE_HEART_RATE_RESERVE = 0.40
    private const val VIGOROUS_HEART_RATE_RESERVE = 0.60

    data class WaveformQuality(
        val score: Int,
        val level: String,
        val metrics: String,
        val snrDb: Double?,
        val rrIntervalsMs: List<Double>,
        val relativeOnly: Boolean = false
    )

    fun packedSamples(values: FloatArray, count: Int): List<Float> =
        values.take(count.coerceAtLeast(1)).filter { it.isFinite() }

    fun isTrustedOxygenPercent(value: Double, quality: Double? = null): Boolean =
        value in ClinicalReferenceTables.SPO2_NORMAL_PCT.min..ClinicalReferenceTables.SPO2_NORMAL_PCT.max &&
            (quality == null || quality <= 2.0)

    fun oxygenClinicalNote(value: Double): String =
        ClinicalReferenceTables.spo2Compare(value).joinToString("；")

    fun wearQualified(offBody: Double?): Boolean? = when (offBody) {
        1.0 -> true
        0.0 -> false
        else -> null
    }

    fun waveformQuality(values: List<Float>, sampleRate: Float, kind: String = ""): WaveformQuality {
        val finiteRuns = contiguousFiniteRuns(values)
        val finite = finiteRuns.flatten()
        val relativeOnly = !sampleRate.isFinite() || sampleRate <= 0f
        if (finite.isEmpty()) {
            return WaveformQuality(0, "資料無效", "沒有有限波形樣本可計算。", null, emptyList(), relativeOnly)
        }
        if (finite.size < 8) {
            val range = finite.max().toDouble() - finite.min().toDouble()
            return WaveformQuality(
                0,
                "資料不足",
                "有限樣本 ${finite.size}/${values.size} 點 · 相對振幅範圍 ${"%.3f".format(range)} · 至少需要 8 個有限樣本",
                null,
                emptyList(),
                relativeOnly
            )
        }
        val mean = finite.average()
        val min = finite.min().toDouble()
        val max = finite.max().toDouble()
        val span = max - min
        if (span == 0.0) {
            return WaveformQuality(0, "固定值", "有限樣本 ${finite.size}/${values.size} 點 · 通道持續輸出固定值；無法辨識有效波形。", null, emptyList(), relativeOnly)
        }
        val rms = sqrt(finite.sumOf { (it - mean) * (it - mean) } / finite.size)
        val jumps = finiteRuns.flatMap { run -> run.zipWithNext().map { abs(it.second - it.first).toDouble() } }
        if (jumps.isEmpty()) {
            return WaveformQuality(
                0,
                "缺少連續片段",
                "有限樣本 ${finite.size}/${values.size} 點，但沒有連續樣本對可計算波形變化。",
                null,
                emptyList(),
                relativeOnly
            )
        }
        val noise = jumps.average()
        val flatRatio = jumps.count { it < 1e-4 }.toDouble() / jumps.size
        val dynamic = if (span > 0.0) (rms / span).coerceIn(0.0, 1.0) else 0.0
        val snr = if (noise > 1e-9) 20 * log10((span / noise).coerceAtLeast(1e-9)) else null
        val rateKnown = !relativeOnly
        val rr = if (kind in setOf("ecg", "ppg") && rateKnown) {
            finiteRuns.flatMap { run -> estimateRrIntervalsMs(run, sampleRate) }
        } else emptyList()
        val morphology = ((1.0 - flatRatio) * 45 + dynamic * 35 + if (rateKnown) 20 else 0).toInt().coerceIn(0, 100)
        val templateCorrelations = if (kind in setOf("ecg", "ppg") && rateKnown) {
            finiteRuns.mapNotNull { run ->
                val runRr = estimateRrIntervalsMs(run, sampleRate)
                templateCorrelation(run, sampleRate, runRr)?.let { it to run.size }
            }
        } else emptyList()
        val templateCorr = templateCorrelations
            .takeIf { it.isNotEmpty() }
            ?.let { parts -> parts.sumOf { it.first * it.second } / parts.sumOf { it.second }.toDouble() }
        // Orphanidou et al., IEEE JBHI 2015: ECG corr threshold 0.66, PPG 0.86 for "good" SQI.
        val threshold = if (kind == "ppg") ClinicalReferenceTables.PPG_SQI_CORR_THRESHOLD
            else ClinicalReferenceTables.ECG_SQI_CORR_THRESHOLD
        val score = when {
            kind in setOf("ecg", "ppg") && templateCorr != null -> {
                val corrComponent = ((templateCorr / threshold).coerceIn(0.0, 1.25) * 70.0).toInt().coerceAtMost(85)
                val rrBonus = when {
                    rr.size >= 8 -> 15
                    rr.size >= 3 -> 10
                    else -> 0
                }
                val rateBonus = when {
                    kind == "ecg" && sampleRate >= ClinicalReferenceTables.AMBULATORY_ECG_RATE_HZ.min.toFloat() -> 10
                    kind == "ecg" && sampleRate >= ClinicalReferenceTables.WRIST_ECG_USABLE_RATE_HZ.min.toFloat() -> 8
                    kind == "ppg" && sampleRate >= ClinicalReferenceTables.PPG_RATE_HZ.min.toFloat() -> 10
                    sampleRate > 0f -> 5
                    else -> 0
                }
                maxOf(morphology, (corrComponent + rrBonus + rateBonus).coerceIn(0, 100))
            }
            else -> morphology
        }
        val completeness = finite.size.toDouble() / values.size.coerceAtLeast(1)
        val coverageAdjustedScore = (score * completeness).toInt().coerceIn(0, 100)
        val level = when {
            coverageAdjustedScore >= 90 -> "優良"
            coverageAdjustedScore >= 75 -> "良好"
            coverageAdjustedScore >= 50 -> "可用"
            coverageAdjustedScore >= 25 -> "偏弱"
            else -> "無效"
        }
        val metrics = buildString {
            append("有限樣本 ${finite.size}/${values.size} 點 · 完整率 ${"%.1f".format(completeness * 100)}%\n")
            append(if (rateKnown) "回報取樣率 ${"%.1f".format(sampleRate)} Hz（時間軸未必已驗證）\n" else "取樣率未知 · 僅相對樣本域指標，不計 RR／心率\n")
            append("振幅範圍 ${"%.3f".format(span)}\n")
            append("RMS ${"%.3f".format(rms)}\n")
            append("相鄰變化 ${"%.3f".format(noise)}")
            snr?.let { append("\n估計 SNR ${"%.1f".format(it)} dB") }
            templateCorr?.let {
                append("\n模板相關 ${"%.3f".format(it)}（Orphanidou SQI 門檻 ${"%.2f".format(threshold)}）")
            }
            if (rr.size >= 3) {
                append("\nRR 間期 ${rr.size} 個")
                append(" · 平均 ${"%.0f".format(rr.average())} ms")
            }
        }
        return WaveformQuality(coverageAdjustedScore, level, metrics, snr, rr, relativeOnly)
    }

    private fun contiguousFiniteRuns(values: List<Float>): List<List<Float>> {
        val runs = mutableListOf<List<Float>>()
        var run = mutableListOf<Float>()
        values.forEach { value ->
            if (value.isFinite()) {
                run += value
            } else if (run.isNotEmpty()) {
                runs += run
                run = mutableListOf()
            }
        }
        if (run.isNotEmpty()) runs += run
        return runs
    }

    /**
     * Orphanidou-style average beat/pulse template correlation (IEEE JBHI 2015).
     * Returns null when too few peaks for a meaningful template.
     */
    fun templateCorrelation(values: List<Float>, sampleRate: Float, rrMs: List<Double>): Double? {
        if (values.size < 32 || sampleRate <= 0f) return null
        val meanRr = rrMs.takeIf { it.size >= 3 }?.average()
            ?: return null
        val half = ((meanRr / 1000.0) * sampleRate / 2.0).toInt().coerceIn(4, values.size / 4)
        val mean = values.average().toFloat()
        val span = (values.max() - values.min()).takeIf { it > 0f } ?: return null
        val threshold = mean + span * 0.32f
        val minDistance = (sampleRate * 0.30f).toInt().coerceAtLeast(2)
        val peaks = ArrayList<Int>()
        var index = 1
        while (index < values.lastIndex) {
            if (values[index] >= threshold && values[index] >= values[index - 1] && values[index] >= values[index + 1]) {
                val previous = peaks.lastOrNull()
                if (previous == null || index - previous >= minDistance) peaks += index
                else if (values[index] > values[previous]) peaks[peaks.lastIndex] = index
                index += minDistance
            } else index++
        }
        if (peaks.size < 3) return null
        val width = half * 2 + 1
        val segments = peaks.mapNotNull { peak ->
            val from = peak - half
            val to = peak + half
            if (from < 0 || to >= values.size) null else values.subList(from, to + 1).map { it.toDouble() }
        }
        if (segments.size < 3) return null
        val template = DoubleArray(width) { i -> segments.map { it[i] }.average() }
        val correlations = segments.map { seg -> pearson(seg.toDoubleArray(), template) }
        return correlations.average().takeIf { it.isFinite() }
    }

    private fun pearson(a: DoubleArray, b: DoubleArray): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        val ma = a.average()
        val mb = b.average()
        var num = 0.0
        var da = 0.0
        var db = 0.0
        for (i in a.indices) {
            val xa = a[i] - ma
            val xb = b[i] - mb
            num += xa * xb
            da += xa * xa
            db += xb * xb
        }
        val den = sqrt(da * db)
        return if (den < 1e-12) 0.0 else (num / den).coerceIn(-1.0, 1.0)
    }

    fun estimateRrIntervalsMs(values: List<Float>, sampleRate: Float): List<Double> {
        if (values.size < 16 || sampleRate <= 0f) return emptyList()
        val mean = values.average().toFloat()
        val span = (values.max() - values.min()).takeIf { it > 0f } ?: return emptyList()
        // 放寬峰門檻：腕式 ECG／封包通道振幅不均，過嚴會導致 RR 被封鎖
        val threshold = mean + span * 0.22f
        val minDistance = (sampleRate * 0.28f).toInt().coerceAtLeast(2)
        val peaks = ArrayList<Int>()
        var index = 1
        while (index < values.lastIndex) {
            if (values[index] >= threshold && values[index] >= values[index - 1] && values[index] >= values[index + 1]) {
                val previous = peaks.lastOrNull()
                if (previous == null || index - previous >= minDistance) {
                    peaks += index
                } else if (values[index] > values[previous]) {
                    peaks[peaks.lastIndex] = index
                }
                index += minDistance
            } else index++
        }
        // 開放 40–200 bpm（300–1500 ms）並保留較寬離群過濾
        val intervals = peaks.zipWithNext { first, second -> (second - first) * 1000.0 / sampleRate }
            .filter { it in 300.0..1_500.0 }
        if (intervals.size < 2) return intervals
        if (intervals.size < 3) return intervals
        val median = intervals.sorted()[intervals.size / 2]
        return intervals.filter { abs(it - median) <= median * 0.55 }
    }
}
