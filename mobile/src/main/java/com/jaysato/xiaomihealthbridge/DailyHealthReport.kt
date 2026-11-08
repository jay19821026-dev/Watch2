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
                missing += "$label：今天沒有可用紀錄"
                return
            }
            val value = day!!.value!!
            val formatted = if (value % 1.0 == 0.0) "${value.toLong()}" else String.format(Locale.US, "%.1f", value)
            val yesterday = summary.days.singleOrNull { it.date == date.minusDays(1) }
            val comparison = if (
                day.sourceIds?.isNotEmpty() == true && day.sourceIds == yesterday?.sourceIds &&
                yesterday.value?.let { it.isFinite() && it > 0.0 } == true
            ) {
                val difference = value - yesterday.value!!
                val direction = if (difference >= 0) "+" else ""
                " · 較昨天 $direction${String.format(Locale.US, "%.0f", difference)} $unit"
            } else ""
            observations += "$label $formatted $unit$comparison"
        }

        dailyAggregate("步數", "步", insights.stepsObservation)
        dailyAggregate("活動熱量", "kcal", insights.activeCaloriesObservation)

        val restingTime = insights.latestObservedRestingHeartRateAt
        if (sameDay(restingTime) && insights.latestObservedRestingHeartRateBpm?.isFinite() == true) {
            observations += "靜息心率 ${String.format(Locale.US, "%.0f", insights.latestObservedRestingHeartRateBpm)} bpm · ${timeLabel(restingTime!!)}"
        } else missing += "靜息心率：今天沒有可用紀錄"

        val hrvTime = insights.latestObservedHrvAt
        if (sameDay(hrvTime) && insights.latestObservedHrvMs?.isFinite() == true) {
            observations += "心率變化（HRV）${String.format(Locale.US, "%.1f", insights.latestObservedHrvMs)} ms · ${timeLabel(hrvTime!!)}"
        } else missing += "心率變化：今天沒有可用紀錄"

        val sleepTime = insights.dailySleepEndAt
        if (sameDay(sleepTime) && insights.dailySleepHours?.let { it.isFinite() && it > 0.0 } == true) {
            observations += "睡眠 ${String.format(Locale.US, "%.1f", insights.dailySleepHours)} 小時 · 醒來 ${timeLabel(sleepTime!!)}"
        } else missing += "睡眠：今天沒有可用紀錄"

        val oxygen = insights.dailyOxygenSummary
        if (oxygen != null && oxygen.validSampleCount > 0 && sameDay(oxygen.firstValidSampleAt) && sameDay(oxygen.lastValidSampleAt)) {
            val oxygenStatus = if (oxygen.minimumPercent < 95.0) "有偏低紀錄" else "在常見參考範圍"
            observations += "血氧 ${String.format(Locale.US, "%.0f–%.0f", oxygen.minimumPercent, oxygen.maximumPercent)}% · $oxygenStatus · 今天的紀錄，非目前值"
        } else missing += "血氧：今天沒有可用紀錄"

        val explanation = if (observations.isEmpty()) {
            "今天尚無可用的健康紀錄；請檢查同步與讀取權限。"
        } else {
            "今天的健康紀錄如下。"
        }
        return Report(date, generatedAt, observations, missing, explanation)
    }
}
