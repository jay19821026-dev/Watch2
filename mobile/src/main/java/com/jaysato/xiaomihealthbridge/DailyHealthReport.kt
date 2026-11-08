package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A dated account of observed data. No score or diagnosis is inferred from missing inputs. */
object DailyHealthReport {
    data class Report(
        val date: LocalDate,
        val generatedAt: Instant,
        val observations: List<String>,
        val missing: List<String>,
        val explanation: String
    )

    fun from(insights: AlgorithmicInsightsEngine.Insights, generatedAt: Instant, zone: ZoneId): Report {
        val date = generatedAt.atZone(zone).toLocalDate()
        val observations = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

        fun sameDay(time: Instant?): Boolean = time?.atZone(zone)?.toLocalDate() == date
        fun timeLabel(time: Instant) = time.atZone(zone).format(timeFormat)
        fun dailyAggregate(label: String, unit: String, summary: HealthConnectActivityObservationPolicy.Summary) {
            val day = summary.days.singleOrNull { it.date == date }
            val usable = summary.state in setOf(
                HealthConnectActivityObservationPolicy.State.AVAILABLE,
                HealthConnectActivityObservationPolicy.State.PARTIAL
            ) && !summary.dailyIntervalMismatch && day != null && day.sourceCount > 0 &&
                day.value?.let { it.isFinite() && it >= 0.0 } == true
            if (!usable) {
                missing += "$label：當日無可確認的 Health Connect 彙總值（${summary.state}）"
                return
            }
            val value = day!!.value!!
            val formatted = if (value % 1.0 == 0.0) "${value.toLong()}" else String.format(Locale.US, "%.1f", value)
            val sources = day.sourceIds?.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("、")
                ?: "${day.sourceCount} 個來源，名稱未提供"
            val yesterday = summary.days.singleOrNull { it.date == date.minusDays(1) }
            val comparison = if (
                day.sourceIds?.isNotEmpty() == true && day.sourceIds == yesterday?.sourceIds &&
                yesterday.value?.let { it.isFinite() && it > 0.0 } == true
            ) {
                val difference = value - yesterday.value!!
                val direction = if (difference >= 0) "+" else ""
                " · 較昨日同來源 $direction${String.format(Locale.US, "%.0f", difference)} $unit（描述性）"
            } else ""
            observations += "$label $formatted $unit · Health Connect 當日 aggregate · $sources$comparison"
        }

        dailyAggregate("步數", "步", insights.stepsObservation)
        dailyAggregate("活動熱量", "kcal", insights.activeCaloriesObservation)

        val restingTime = insights.latestObservedRestingHeartRateAt
        if (sameDay(restingTime) && insights.latestObservedRestingHeartRateBpm?.isFinite() == true) {
            observations += "靜息心率 ${String.format(Locale.US, "%.0f", insights.latestObservedRestingHeartRateBpm)} bpm · ${timeLabel(restingTime!!)} · ${insights.latestObservedRestingHeartRateOrigin ?: "來源未提供"}"
        } else missing += "靜息心率：當日無有效且註明量測時間的紀錄"

        val hrvTime = insights.latestObservedHrvAt
        if (sameDay(hrvTime) && insights.latestObservedHrvMs?.isFinite() == true) {
            observations += "HRV ${String.format(Locale.US, "%.1f", insights.latestObservedHrvMs)} ms · ${timeLabel(hrvTime!!)} · ${insights.latestObservedHrvOrigin ?: "來源未提供"}"
        } else missing += "HRV：當日無有效且註明量測時間的紀錄"

        val sleepTime = insights.dailySleepEndAt
        if (sameDay(sleepTime) && insights.dailySleepHours?.let { it.isFinite() && it > 0.0 } == true) {
            observations += "睡眠 ${String.format(Locale.US, "%.1f", insights.dailySleepHours)} 小時 · 醒來 ${timeLabel(sleepTime!!)} · ${insights.dailySleepOrigin ?: "來源未提供"} · 僅分期完整的紀錄"
        } else missing += "睡眠：當日無按醒來日歸屬且分期完整的紀錄"

        val oxygen = insights.dailyOxygenSummary
        if (oxygen != null && oxygen.validSampleCount > 0 && sameDay(oxygen.firstValidSampleAt) && sameDay(oxygen.lastValidSampleAt)) {
            observations += "血氧 ${String.format(Locale.US, "%.0f–%.0f", oxygen.minimumPercent, oxygen.maximumPercent)}% · ${oxygen.validSampleCount} 筆當日正值 · ${insights.dailyOxygenOrigin ?: "來源未提供"} · ${timeLabel(oxygen.firstValidSampleAt!!)}–${timeLabel(oxygen.lastValidSampleAt!!)}（範圍，非目前值）"
        } else missing += "血氧：當日無可確認來源與量測時間的正值百分比紀錄"

        val explanation = if (observations.isEmpty()) {
            "今天尚無可確認的單日紀錄；請檢查 Health Connect 授權、同步和量測時間。"
        } else {
            "今天已確認 ${observations.size} 項當日紀錄。這是來源資料整理，不代表全天量測完整；請查看每項的來源與時間。缺少的項目不推算，也不據此判定健康正常或異常。"
        }
        return Report(date, generatedAt, observations, missing, explanation)
    }
}
