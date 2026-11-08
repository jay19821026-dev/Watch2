package com.jaysato.xiaomihealthbridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyTemperatureMeasurementLocation
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

internal object SleepInferencePolicy {
    fun shouldInfer(
        sleepQuerySucceeded: Boolean,
        hasAmbiguousRecords: Boolean,
        hasRecentRecordedSession: Boolean
    ): Boolean = sleepQuerySucceeded && !hasAmbiguousRecords && !hasRecentRecordedSession

    data class Sample(val time: Instant, val bpm: Double)
    data class Result(val hours: Double, val startAt: Instant, val endAt: Instant)

    fun infer(samples: List<Sample>, now: Instant, zoneId: ZoneId): Result? {
        val recent = samples.asSequence()
            .filter { !it.time.isBefore(now.minus(30, ChronoUnit.HOURS)) && !it.time.isAfter(now) }
            .sortedBy { it.time }
            .toList()
        if (recent.size < 12) return null
        if (recent.any { !it.bpm.isFinite() || it.bpm <= 0.0 }) return null
        if (recent.zipWithNext().any { (first, second) -> first.time == second.time }) return null
        val baseline = recent.map { it.bpm }.percentile(0.30) ?: return null
        fun isLowRateNightSample(sample: Sample): Boolean {
            val hour = sample.time.atZone(zoneId).hour
            return (hour >= 20 || hour < 12) && sample.bpm <= baseline * 1.08
        }
        var bestMinutes = 0L
        var bestStart: Instant? = null
        var bestEnd: Instant? = null
        var segmentStart: Instant? = null
        var previous: Instant? = null
        for (sample in recent) {
            if (previous != null && Duration.between(previous, sample.time) > Duration.ofMinutes(45)) {
                segmentStart = null
            }
            if (!isLowRateNightSample(sample)) {
                segmentStart = null
                previous = sample.time
                continue
            }
            val currentStart = segmentStart ?: sample.time
            val currentMinutes = Duration.between(currentStart, sample.time).toMinutes()
            if (currentMinutes > bestMinutes) {
                bestMinutes = currentMinutes
                bestStart = currentStart
                bestEnd = sample.time
            }
            segmentStart = currentStart
            previous = sample.time
        }
        if (bestMinutes < 180) return null
        return Result(bestMinutes / 60.0, bestStart ?: return null, bestEnd ?: return null)
    }
}

internal object HealthConnectEmptyInputPolicy {
    enum class Window { TODAY, LAST_SEVEN_DAYS }

    fun message(window: Window): String = when (window) {
        Window.TODAY -> "今天沒有同步紀錄"
        Window.LAST_SEVEN_DAYS -> "近 7 天沒有同步紀錄"
    }
}

enum class RecordedTotalCaloriesState {
    AVAILABLE, PARTIAL, PERMISSION_DENIED, PERMISSION_QUERY_FAILED, READ_FAILED, NO_RECORDS, BOUNDARY_EXCLUDED, AMBIGUOUS
}

internal object HealthConnectIntervalWindowPolicy {
    data class Selection<T>(val retained: List<T>, val excludedOverlaps: Int)

    fun <T> retainContained(
        records: List<T>,
        windowStart: Instant,
        windowEnd: Instant,
        recordStart: (T) -> Instant,
        recordEnd: (T) -> Instant
    ): Selection<T> {
        val retained = ArrayList<T>(records.size)
        var excludedOverlaps = 0
        records.forEach { record ->
            val start = recordStart(record)
            val end = recordEnd(record)
            val contained = !start.isBefore(windowStart) && !end.isAfter(windowEnd)
            val overlaps = start.isBefore(windowEnd) && end.isAfter(windowStart)
            if (contained) retained += record
            else if (overlaps) excludedOverlaps++
        }
        return Selection(retained, excludedOverlaps)
    }
}

internal object RecordedTotalCaloriesPolicy {

    fun classify(
        permissionState: HealthConnectPermissionPolicy.State,
        measurements: List<SourceIntervalMeasurement>?,
        excludedBoundaryRecords: Int = 0
    ): RecordedTotalCaloriesState = when {
        permissionState == HealthConnectPermissionPolicy.State.DENIED -> RecordedTotalCaloriesState.PERMISSION_DENIED
        permissionState == HealthConnectPermissionPolicy.State.QUERY_FAILED -> RecordedTotalCaloriesState.PERMISSION_QUERY_FAILED
        measurements == null -> RecordedTotalCaloriesState.READ_FAILED
        measurements.isEmpty() && excludedBoundaryRecords > 0 -> RecordedTotalCaloriesState.BOUNDARY_EXCLUDED
        measurements.isEmpty() -> RecordedTotalCaloriesState.NO_RECORDS
        SourceIntervalAggregation.sumOrNull(measurements) == null -> RecordedTotalCaloriesState.AMBIGUOUS
        excludedBoundaryRecords > 0 -> RecordedTotalCaloriesState.PARTIAL
        else -> RecordedTotalCaloriesState.AVAILABLE
    }

    fun display(state: RecordedTotalCaloriesState, kcal: Int?, origin: String?, recordCount: Int = 0, excludedBoundaryRecords: Int = 0): String = when (state) {
        RecordedTotalCaloriesState.AVAILABLE -> "Health Connect 今日已記錄總熱量區間加總：${kcal ?: "—"} kcal（$recordCount 筆；單一來源：${origin ?: "未標示"}）。覆蓋完整性未確認，不代表全天總量；不與活動熱量估計相加。"
        RecordedTotalCaloriesState.PARTIAL -> "Health Connect 今日已記錄總熱量區間小計：${kcal ?: "—"} kcal（$recordCount 筆；另排除 $excludedBoundaryRecords 筆跨日區間）。覆蓋完整性未確認，不代表全天總量。"
        RecordedTotalCaloriesState.PERMISSION_DENIED -> "Health Connect 今日總消耗熱量：未授權讀取；可至權限設定允許。"
        RecordedTotalCaloriesState.PERMISSION_QUERY_FAILED -> "Health Connect 今日總消耗熱量：權限狀態查詢失敗。"
        RecordedTotalCaloriesState.READ_FAILED -> "Health Connect 今日總消耗熱量：讀取失敗；請稍後重試。"
        RecordedTotalCaloriesState.NO_RECORDS -> "Health Connect 今日總消耗熱量：今天沒有同步紀錄。"
        RecordedTotalCaloriesState.BOUNDARY_EXCLUDED -> "Health Connect 今日總消耗熱量：$excludedBoundaryRecords 筆紀錄跨越日期邊界，已排除；今日沒有可合計的完整區間。"
        RecordedTotalCaloriesState.AMBIGUOUS -> "Health Connect 今日總消耗熱量：來源多重、區間重疊或數值無效，保留原始紀錄但不合計。"
    }
}

internal object HealthInsightInputPolicy {
    data class TimedSourceValue<T>(val value: T, val time: Instant, val source: String)
    data class PairedValues<T, U>(val first: TimedSourceValue<T>, val second: TimedSourceValue<U>)

    fun timestampsWithinWindow(times: List<Instant>, start: Instant, end: Instant): Boolean =
        times.all { !it.isBefore(start) && !it.isAfter(end) }

    fun <T> retainTimestampsWithinWindow(
        records: List<T>,
        start: Instant,
        end: Instant,
        time: (T) -> Instant
    ): List<T> = records.filter { record ->
        val measuredAt = time(record)
        !measuredAt.isBefore(start) && !measuredAt.isAfter(end)
    }

    fun <T, U> latestSameDayPair(
        first: List<TimedSourceValue<T>>,
        second: List<TimedSourceValue<U>>,
        zoneId: ZoneId
    ): PairedValues<T, U>? {
        val candidates = first.flatMap { firstValue ->
            second.mapNotNull { secondValue ->
                val sameDay = firstValue.time.atZone(zoneId).toLocalDate() == secondValue.time.atZone(zoneId).toLocalDate()
                if (sameDay && firstValue.source.isNotBlank() && firstValue.source == secondValue.source) {
                    PairedValues(firstValue, secondValue)
                } else null
            }
        }
        val newestPairDate = candidates.maxOfOrNull { pair ->
            maxOf(pair.first.time, pair.second.time).atZone(zoneId).toLocalDate()
        } ?: return null
        return candidates.filter { pair ->
            maxOf(pair.first.time, pair.second.time).atZone(zoneId).toLocalDate() == newestPairDate
        }.minWithOrNull(
            compareBy<PairedValues<T, U>> { Duration.between(it.first.time, it.second.time).abs() }
                .thenByDescending { maxOf(it.first.time, it.second.time) }
        )
    }

    fun <T> latestSameDayBefore(
        values: List<TimedSourceValue<T>>,
        anchor: Instant,
        zoneId: ZoneId,
        preferredSource: String? = null
    ): TimedSourceValue<T>? {
        val candidates = values.asSequence()
        .filter { it.source.isNotBlank() && it.time.isBefore(anchor) }
        .filter { it.time.atZone(zoneId).toLocalDate() == anchor.atZone(zoneId).toLocalDate() }
        .toList()
        val selected = if (preferredSource.isNullOrBlank()) candidates else candidates.filter { it.source == preferredSource }
        return selected
            .maxWithOrNull(compareBy<TimedSourceValue<T>> { it.time }.thenBy { it.source })
    }

    fun crossesLocalDate(start: Instant, end: Instant, zoneId: ZoneId): Boolean =
        !start.isBefore(end) || start.atZone(zoneId).toLocalDate() != end.minusNanos(1).atZone(zoneId).toLocalDate()

    fun sleepHours(sessions: List<SleepSessionValue>, windowStart: Instant, windowEnd: Instant): Double? {
        val clipped = sessions.mapNotNull { session ->
            val start = maxOf(session.start, windowStart)
            val end = minOf(session.end, windowEnd)
            if (!start.isBefore(end)) null else SleepSessionValue(
                start,
                end,
                session.awakeIntervals,
                session.hasStageData,
                session.stageIntervals,
                session.stageTypesKnown
            )
        }
        if (!MetricDetailAggregation.sleepSessionsAreNonOverlapping(clipped)) return null
        return clipped.sumOf(MetricDetailAggregation::sleepHours)
    }

    fun sleepHoursWhenStagesComplete(
        stagesComplete: Boolean,
        sessions: List<SleepSessionValue>?,
        windowStart: Instant,
        windowEnd: Instant
    ): Double? = if (stagesComplete) sessions?.let { sleepHours(it, windowStart, windowEnd) } else null

    fun sleepStagesCoverSessions(sessions: List<SleepSessionValue>, windowStart: Instant, windowEnd: Instant): Boolean {
        val clipped = sessions.mapNotNull { session ->
            val start = maxOf(session.start, windowStart)
            val end = minOf(session.end, windowEnd)
            if (!start.isBefore(end)) null else session to (start to end)
        }
        if (clipped.isEmpty() || clipped.any { (session, _) ->
                !session.hasStageData || !session.stageTypesKnown || !hasValidSleepStages(session)
            }
        ) return false
        val clippedSessions = clipped.map { (session, range) ->
            SleepSessionValue(range.first, range.second, session.awakeIntervals, true, session.stageIntervals, true)
        }
        if (!MetricDetailAggregation.sleepSessionsAreNonOverlapping(clippedSessions)) return false
        return clipped.all { (session, range) ->
            val clippedStages = session.stageIntervals.mapNotNull { stage ->
                val stageStart = maxOf(range.first, stage.start)
                val stageEnd = minOf(range.second, stage.end)
                (stageStart to stageEnd).takeIf { stageStart.isBefore(stageEnd) }
            }.sortedBy { it.first }
            var coveredUntil = range.first
            for ((stageStart, stageEnd) in clippedStages) {
                if (stageStart != coveredUntil) return@all false
                coveredUntil = stageEnd
            }
            coveredUntil == range.second
        }
    }

    private fun hasValidSleepStages(session: SleepSessionValue): Boolean {
        val ordered = session.stageIntervals.sortedBy { it.start }
        var previousEnd: Instant? = null
        for (stage in ordered) {
            if (!stage.start.isBefore(stage.end) || stage.start.isBefore(session.start) || stage.end.isAfter(session.end)) return false
            if (previousEnd?.isAfter(stage.start) == true) return false
            previousEnd = stage.end
        }
        return ordered.isNotEmpty()
    }
}

internal object HealthInsightWindowPolicy {
    fun lastSevenLocalDatesStart(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().minusDays(6).atStartOfDay(zone).toInstant()

    fun <T> retainOverlappingIntervals(
        records: List<T>, start: Instant, end: Instant,
        recordStart: (T) -> Instant, recordEnd: (T) -> Instant
    ): List<T> = records.filter { record ->
        val from = recordStart(record)
        val to = recordEnd(record)
        from.isBefore(to) && from.isBefore(end) && to.isAfter(start)
    }
}

object HealthConnectActivityObservationPolicy {
    enum class State { AVAILABLE, PARTIAL, NO_RECORDS, PERMISSION_DENIED, PERMISSION_STATE_FAILED, READ_FAILED, DEFERRED }

    data class DayValue(
        val date: LocalDate,
        val value: Double?,
        val sourceCount: Int,
        val sourceIds: Set<String>? = null
    )

    fun localDailyPeriodRange(startDate: LocalDate, endTime: Instant, zone: ZoneId): Pair<java.time.LocalDateTime, java.time.LocalDateTime> =
        startDate.atStartOfDay() to endTime.atZone(zone).toLocalDateTime()

    fun validSourceIds(sourceIds: Collection<String>): Set<String> = sourceIds.map(String::trim).filter(String::isNotEmpty).toSet()

    fun validSourceCount(sourceIds: Collection<String>): Int = validSourceIds(sourceIds).size

    fun fromDailyPeriodBucket(
        startTime: java.time.LocalDateTime,
        value: Double,
        sourceIds: Set<String>
    ): DayValue {
        val validSourceIds = validSourceIds(sourceIds)
        return DayValue(startTime.toLocalDate(), value, validSourceIds.size, validSourceIds)
    }

    fun stateAfterAggregateReads(
        state: State,
        dailyReadFailed: Boolean,
        intervalReadFailed: Boolean,
        dailyRows: List<DayValue>,
        intervalTotal: Double?,
        intervalSourceCount: Int
    ): State {
        if (state != State.AVAILABLE) return state
        val hasDailyEvidence = dailyRows.any {
            it.value?.let { value -> value.isFinite() && value >= 0.0 } == true && it.sourceCount > 0
        }
        val hasIntervalEvidence = intervalTotal?.let { it.isFinite() && it >= 0.0 } == true && intervalSourceCount > 0
        if (dailyReadFailed || intervalReadFailed) {
            return if (hasDailyEvidence || hasIntervalEvidence) State.PARTIAL else State.READ_FAILED
        }
        return if (hasDailyEvidence || hasIntervalEvidence) State.AVAILABLE else State.NO_RECORDS
    }

    data class Summary(
        val state: State,
        val windowStart: Instant,
        val windowEnd: Instant,
        val days: List<DayValue>,
        val total: Double?,
        val observedDays: Int,
        val sourceCount: Int,
        val dailyIntervalMismatch: Boolean = false
    ) {
        fun display(label: String, unit: String, zoneId: ZoneId): String = buildString {
            append("$label：")
            when (state) {
                State.PERMISSION_DENIED -> append("未授權")
                State.PERMISSION_STATE_FAILED -> append("權限狀態查詢失敗")
                State.READ_FAILED -> append("讀取失敗（可能需要在前景互動時重試）")
                State.DEFERRED -> append("目前非互動狀態，為避免背景呼叫 Health Connect，已延後查詢")
                State.NO_RECORDS -> append("此區間沒有 aggregate 紀錄；不以 0 代替")
                State.AVAILABLE -> {
                    append("7 日 aggregate 總量 ${format(total!!)} $unit；有資料 ${observedDays}/${days.size} 日；區間彙總來源 $sourceCount 個\n")
                    days.forEachIndexed { index, day ->
                        if (index > 0) append(" · ")
                        append("${day.date}: ${day.value?.let { "${format(it)} $unit" } ?: "—"} (彙總來源 ${day.sourceCount})")
                    }
                }
                State.PARTIAL -> {
                    if (dailyIntervalMismatch) {
                        append("逐日桶完整，但日加總與區間 aggregate 不一致；保留區間總量，逐日值不納入趨勢推論\n")
                    } else if (sourceCount > 0) {
                        append("區間彙總 ${format(total!!)} $unit；逐日明細部分未取得；觀測 ${observedDays}/${days.size} 日；彙總來源 $sourceCount 個\n")
                    } else {
                        append("區間總量未取得；有值日小計 ${format(total!!)} $unit；觀測 ${observedDays}/${days.size} 日；不可視為完整 7 日總量；區間彙總來源未確認\n")
                    }
                    days.forEachIndexed { index, day ->
                        if (index > 0) append(" · ")
                        append("${day.date}: ${day.value?.let { "${format(it)} $unit" } ?: "—"} (彙總來源 ${day.sourceCount})")
                    }
                }
            }
            append("\n時間窗：${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date.from(windowStart))} 至 ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date.from(windowEnd))} (${zoneId.id})")
        }

        private fun format(value: Double): String = if (value % 1.0 == 0.0) "${value.toLong()}" else "%.1f".format(value)
    }

    fun observedDailyStepCounts(summary: Summary): List<Long> {
        if (summary.state !in setOf(State.AVAILABLE, State.PARTIAL) || summary.dailyIntervalMismatch) return emptyList()
        return summary.days.mapNotNull { day ->
            val value = day.value ?: return@mapNotNull null
            if (day.sourceCount <= 0 || !value.isFinite() || value < 0.0 || value % 1.0 != 0.0 || value >= Long.MAX_VALUE.toDouble()) {
                return@mapNotNull null
            }
            value.toLong()
        }
    }

    fun summarize(
        state: State,
        windowStart: Instant,
        windowEnd: Instant,
        dates: List<LocalDate>,
        returnedDays: List<DayValue>,
        intervalTotal: Double?,
        intervalSourceCount: Int
    ): Summary {
        val validDailyRowsByDate = returnedDays
            .filter { it.value?.let { value -> value.isFinite() && value >= 0.0 } == true && it.sourceCount > 0 }
            .groupBy { it.date }
        fun duplicateDailyBucketsAreAmbiguous(rows: List<DayValue>): Boolean =
            rows.size > 1 && (
                rows.any { it.sourceIds == null || it.sourceIds.size != it.sourceCount } ||
                    rows.map { Triple(it.value, it.sourceCount, it.sourceIds) }.distinct().size > 1
                )
        val hasConflictingDailyBuckets = validDailyRowsByDate.values.any(::duplicateDailyBucketsAreAmbiguous)
        val daily = dates.distinct().map { date ->
            val rows = validDailyRowsByDate[date].orEmpty()
            when {
                rows.isEmpty() -> DayValue(date, null, 0)
                !duplicateDailyBucketsAreAmbiguous(rows) -> rows.first()
                else -> DayValue(date, null, 0)
            }
        }
        val observed = daily.filter { it.value != null }
        val validInterval = intervalTotal?.let { it.isFinite() && it >= 0.0 } == true && intervalSourceCount > 0
        val fullDailyCoverage = daily.isNotEmpty() && observed.size == daily.size && !hasConflictingDailyBuckets
        val dailySum = observed.sumOf { it.value!! }
        val dailyIntervalMismatch = validInterval && fullDailyCoverage &&
            !aggregateTotalsMatch(dailySum, intervalTotal!!)
        val effectiveState = when {
            state != State.AVAILABLE -> state
            dailyIntervalMismatch -> State.PARTIAL
            validInterval && !hasConflictingDailyBuckets -> State.AVAILABLE
            validInterval -> State.PARTIAL
            observed.isNotEmpty() -> State.PARTIAL
            intervalTotal == null || (intervalTotal == 0.0 && intervalSourceCount == 0) -> State.NO_RECORDS
            else -> State.READ_FAILED
        }
        val periodValue = when (effectiveState) {
            State.AVAILABLE -> intervalTotal
            State.PARTIAL -> if (validInterval) intervalTotal else observed.sumOf { it.value!! }
            else -> null
        }
        return Summary(
            effectiveState,
            windowStart,
            windowEnd,
            daily,
            periodValue,
            observed.size.takeIf { effectiveState == State.AVAILABLE || effectiveState == State.PARTIAL } ?: 0,
            intervalSourceCount.takeIf { validInterval && effectiveState in setOf(State.AVAILABLE, State.PARTIAL) } ?: 0,
            dailyIntervalMismatch
        )
    }

    private fun aggregateTotalsMatch(dailyTotal: Double, intervalTotal: Double): Boolean {
        val tolerance = maxOf(1e-6, kotlin.math.abs(intervalTotal) * 1e-9)
        return kotlin.math.abs(dailyTotal - intervalTotal) <= tolerance
    }
}

class AlgorithmicInsightsEngine(context: Context) {
    private val appContext = context.applicationContext
    private val client = HealthConnectClient.getOrCreate(appContext)
    private val profile = UserProfileStore(appContext).read()

    suspend fun calculate(): Insights {
        val now = Instant.now()
        val localZone = ZoneId.systemDefault()
        val today = now.atZone(localZone).toLocalDate()
        val start = HealthInsightWindowPolicy.lastSevenLocalDatesStart(now, localZone)
        val filter = TimeRangeFilter.between(start, now)
        val todayStart = DailyEnergyEstimate.startOfLocalDay(now, localZone)
        val completeDailyWindowStart = start
        val activityCaloriesFilter = TimeRangeFilter.between(completeDailyWindowStart, now)
        val granted = try {
            client.permissionController.getGrantedPermissions()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val unavailableInputs = mutableListOf<String>()
        val emptyInputs = mutableListOf<String>()
        suspend fun <T> readRecords(
            label: String,
            permission: String,
            emptyWindow: HealthConnectEmptyInputPolicy.Window = HealthConnectEmptyInputPolicy.Window.LAST_SEVEN_DAYS,
            query: suspend () -> List<T>
        ): List<T>? {
            when (HealthConnectPermissionPolicy.state(permission, granted)) {
                HealthConnectPermissionPolicy.State.QUERY_FAILED -> {
                    unavailableInputs += "$label：Health Connect 權限狀態查詢失敗，未嘗試讀取"
                    return null
                }
                HealthConnectPermissionPolicy.State.DENIED -> {
                    unavailableInputs += "$label：未授權"
                    return null
                }
                HealthConnectPermissionPolicy.State.GRANTED -> Unit
            }
            return try {
                query().also { if (it.isEmpty()) emptyInputs += "$label：${HealthConnectEmptyInputPolicy.message(emptyWindow)}" }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                unavailableInputs += HealthConnectReadFailurePolicy.describe(label, failure)
                null
            }
        }
        suspend fun <T> readContainedIntervals(
            label: String,
            permission: String,
            windowStart: Instant,
            windowEnd: Instant,
            read: suspend () -> List<T>,
            recordStart: (T) -> Instant,
            recordEnd: (T) -> Instant
        ): List<T>? {
            var excludedOverlaps = 0
            val records = readRecords(label, permission) {
                HealthConnectIntervalWindowPolicy.retainContained(
                    read(), windowStart, windowEnd, recordStart, recordEnd
                ).also { excludedOverlaps = it.excludedOverlaps }.retained
            }
            if (excludedOverlaps > 0) {
                emptyInputs.remove("$label：${HealthConnectEmptyInputPolicy.message(HealthConnectEmptyInputPolicy.Window.LAST_SEVEN_DAYS)}")
                unavailableInputs += "$label：$excludedOverlaps 筆跨查詢區間的紀錄已排除；保留結果為部分涵蓋"
            }
            return records
        }
        val observationStart = start
        val observationPeriodRange = HealthConnectActivityObservationPolicy.localDailyPeriodRange(
            today.minusDays(6), now, localZone
        )
        val observationDates = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val activityAggregateReader = HealthConnectActivityAggregateReader(client)
        suspend fun readActivityObservation(
            label: String,
            permission: String,
            dailyQuery: suspend () -> List<HealthConnectActivityObservationPolicy.DayValue>,
            intervalQuery: suspend () -> Pair<Double?, Int>
        ): HealthConnectActivityObservationPolicy.Summary {
            var aggregateState = when (HealthConnectPermissionPolicy.state(permission, granted)) {
                HealthConnectPermissionPolicy.State.DENIED -> HealthConnectActivityObservationPolicy.State.PERMISSION_DENIED
                HealthConnectPermissionPolicy.State.QUERY_FAILED -> HealthConnectActivityObservationPolicy.State.PERMISSION_STATE_FAILED
                HealthConnectPermissionPolicy.State.GRANTED -> if (
                    appContext.getSystemService(android.os.PowerManager::class.java)?.isInteractive == true
                ) HealthConnectActivityObservationPolicy.State.AVAILABLE
                else HealthConnectActivityObservationPolicy.State.DEFERRED
            }
            var dailyRows: List<HealthConnectActivityObservationPolicy.DayValue> = emptyList()
            var intervalTotal: Double? = null
            var intervalSourceCount = 0
            var dailyReadFailed = false
            var intervalReadFailed = false
            if (aggregateState == HealthConnectActivityObservationPolicy.State.AVAILABLE) {
                try {
                    dailyRows = dailyQuery()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    dailyReadFailed = true
                    unavailableInputs += "$label 每日 aggregate：${HealthConnectReadFailurePolicy.describe(label, failure)}"
                }
                try {
                    val interval = intervalQuery()
                    intervalTotal = interval.first
                    intervalSourceCount = interval.second
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    intervalReadFailed = true
                    unavailableInputs += "$label 區間 aggregate：${HealthConnectReadFailurePolicy.describe(label, failure)}"
                }
                aggregateState = HealthConnectActivityObservationPolicy.stateAfterAggregateReads(
                    aggregateState, dailyReadFailed, intervalReadFailed, dailyRows, intervalTotal, intervalSourceCount
                )
            }
            when (aggregateState) {
                HealthConnectActivityObservationPolicy.State.PERMISSION_DENIED -> unavailableInputs += "$label aggregate：未授權"
                HealthConnectActivityObservationPolicy.State.PERMISSION_STATE_FAILED -> unavailableInputs += "$label aggregate：權限狀態查詢失敗"
                HealthConnectActivityObservationPolicy.State.READ_FAILED -> Unit
                HealthConnectActivityObservationPolicy.State.DEFERRED -> unavailableInputs += "$label aggregate：目前非互動狀態，已延後查詢"
                else -> Unit
            }
            val summary = HealthConnectActivityObservationPolicy.summarize(
                aggregateState, observationStart, now, observationDates, dailyRows, intervalTotal, intervalSourceCount
            )
            if (summary.state == HealthConnectActivityObservationPolicy.State.NO_RECORDS) emptyInputs += "$label aggregate：近 7 日無紀錄"
            if (summary.state == HealthConnectActivityObservationPolicy.State.READ_FAILED && aggregateState == HealthConnectActivityObservationPolicy.State.AVAILABLE) {
                unavailableInputs += "$label aggregate：區間 aggregate 無有效總量"
            }
            return summary
        }
        fun <T> onlyInQueryWindow(label: String, records: List<T>?, time: (T) -> Instant): List<T>? {
            if (records == null) return null
            val inWindow = HealthInsightInputPolicy.retainTimestampsWithinWindow(records, start, now, time)
            val excludedCount = records.size - inWindow.size
            if (excludedCount > 0) unavailableInputs += "$label：$excludedCount 筆量測時間超出查詢區間，已逐筆排除"
            return inWindow.takeIf { it.isNotEmpty() }
        }
        fun <T> onlyUnambiguousIntervals(
            label: String,
            records: List<T>?,
            toMeasurement: (T) -> SourceIntervalMeasurement
        ): List<T>? {
            if (records.isNullOrEmpty()) return null
            val assessment = SourceIntervalAggregation.assess(records.map(toMeasurement))
            if (!assessment.eligible && assessment.rejection != SourceIntervalAggregation.Rejection.NO_RECORDS) {
                unavailableInputs += "$label：來源或時段重疊不明，保留原始紀錄但不合併計算"
            }
            return records.takeIf { assessment.eligible }
        }
        fun <T> selectUnambiguousSource(
            label: String,
            records: List<T>?,
            origin: (T) -> String,
            completeness: (T) -> Int,
            measuredAt: (T) -> Instant,
            toMeasurement: (T) -> SourceIntervalMeasurement
        ): List<T>? {
            val selection = AlgorithmicInsightProvenance.selectMostCompleteUnambiguousOrigin(
                records, origin, completeness, measuredAt, toMeasurement
            )
            if (selection != null) {
                if (records.orEmpty().map(origin).distinct().size > 1) {
                    unavailableInputs += "$label：只採涵蓋最多且區間不重疊的單一來源，避免重複合計"
                }
                return selection.records
            }
            return onlyUnambiguousIntervals(label, records, toMeasurement)
        }
        val rawHeartRates = readRecords("心率", HealthPermission.getReadPermission(HeartRateRecord::class)) {
            client.readAllRecords(HeartRateRecord::class, filter)
        }
        val heartRates = selectUnambiguousSource(
            "心率",
            rawHeartRates,
            origin = { it.metadata.dataOrigin.packageName },
            completeness = { record -> record.samples.count { it.beatsPerMinute in 30L..220L } },
            measuredAt = { it.endTime },
            toMeasurement = {
                SourceIntervalMeasurement(
                it.metadata.dataOrigin.packageName,
                it.startTime,
                it.endTime,
                Duration.between(it.startTime, it.endTime).toMillis().toDouble()
            )
            }
        )
        val heartRateOrigin = heartRates?.firstOrNull()?.metadata?.dataOrigin?.packageName
        val samples = heartRates.orEmpty().asSequence().flatMap { it.samples.asSequence() }
            .filter { it.beatsPerMinute in 30L..220L && !it.time.isBefore(start) && !it.time.isAfter(now) }
            .sortedBy { it.time }
            .toList()

        val rawSteps = readContainedIntervals(
            "步數", HealthPermission.getReadPermission(StepsRecord::class), start, now,
            read = { client.readAllRecords(StepsRecord::class, filter) },
            recordStart = { it.startTime }, recordEnd = { it.endTime }
        )
        val stepsObservation = readActivityObservation(
            "步數",
            HealthPermission.getReadPermission(StepsRecord::class),
            dailyQuery = { activityAggregateReader.dailySteps(observationPeriodRange.first, observationPeriodRange.second) },
            intervalQuery = { activityAggregateReader.intervalSteps(observationStart, now) }
        )
        val steps = selectUnambiguousSource(
            "步數", rawSteps, origin = { it.metadata.dataOrigin.packageName },
            completeness = { record ->
                if (record.count >= 0L) java.time.Duration.between(record.startTime, record.endTime).seconds
                    .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt() else 0
            }, measuredAt = { it.endTime }
        ) { SourceIntervalMeasurement(it.metadata.dataOrigin.packageName, it.startTime, it.endTime, it.count.toDouble()) }
        val rawCalories = readContainedIntervals(
            "活動卡路里", HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class), completeDailyWindowStart, now,
            read = { client.readAllRecords(ActiveCaloriesBurnedRecord::class, activityCaloriesFilter) },
            recordStart = { it.startTime }, recordEnd = { it.endTime }
        )
        val totalCaloriesPermission = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        val totalCaloriesPermissionState = HealthConnectPermissionPolicy.state(totalCaloriesPermission, granted)
        var excludedTotalCaloriesBoundaryRecords = 0
        val rawTotalCalories = readRecords(
            "總消耗熱量",
            totalCaloriesPermission,
            emptyWindow = HealthConnectEmptyInputPolicy.Window.TODAY
        ) {
            client.readAllRecords(TotalCaloriesBurnedRecord::class, TimeRangeFilter.between(todayStart, now)).also { records ->
                excludedTotalCaloriesBoundaryRecords = records.count { it.startTime < todayStart || it.endTime > now }
            }.filter { it.startTime >= todayStart && it.endTime <= now }
        }
        val totalCalories = selectUnambiguousSource(
            "總消耗熱量", rawTotalCalories,
            origin = { it.metadata.dataOrigin.packageName },
            completeness = { record -> java.time.Duration.between(record.startTime, record.endTime).seconds
                .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt() },
            measuredAt = { it.endTime }
        ) { SourceIntervalMeasurement(it.metadata.dataOrigin.packageName, it.startTime, it.endTime, it.energy.inKilocalories) }
        val totalCalorieMeasurements = totalCalories?.map {
            SourceIntervalMeasurement(it.metadata.dataOrigin.packageName, it.startTime, it.endTime, it.energy.inKilocalories)
        }
        val recordedTotalCaloriesSum = totalCalorieMeasurements?.let(SourceIntervalAggregation::sumOrNull)
        val recordedTotalCaloriesState = RecordedTotalCaloriesPolicy.classify(
            totalCaloriesPermissionState, totalCalorieMeasurements, excludedTotalCaloriesBoundaryRecords
        )
        val recordedTotalCaloriesKcal = recordedTotalCaloriesSum
            ?.takeIf { it.isFinite() && it >= 0.0 }
        val recordedTotalCaloriesOrigin = totalCalories?.firstOrNull()?.metadata?.dataOrigin?.packageName
        val calorieObservation = readActivityObservation(
            "活動卡路里",
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            dailyQuery = {
                client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(observationPeriodRange.first, observationPeriodRange.second),
                        timeRangeSlicer = java.time.Period.ofDays(1)
                    )
                ).mapNotNull { group ->
                    group.result.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories?.let { value ->
                        HealthConnectActivityObservationPolicy.fromDailyPeriodBucket(
                            group.startTime, value, group.result.dataOrigins.map { it.packageName }.toSet()
                        )
                    }
                }
            },
            intervalQuery = {
                val result = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(observationStart, now)
                    )
                )
                result.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories to result.dataOrigins.size
            }
        )
        val calories = selectUnambiguousSource(
            "活動卡路里", rawCalories, origin = { it.metadata.dataOrigin.packageName },
            completeness = { record -> java.time.Duration.between(record.startTime, record.endTime).seconds
                .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt() }, measuredAt = { it.endTime }
        ) {
            SourceIntervalMeasurement(
                it.metadata.dataOrigin.packageName,
                it.startTime,
                it.endTime,
                it.energy.inKilocalories
            )
        }
        val rawExercises = readContainedIntervals(
            "運動", HealthPermission.getReadPermission(ExerciseSessionRecord::class), start, now,
            read = { client.readAllRecords(ExerciseSessionRecord::class, filter) },
            recordStart = { it.startTime }, recordEnd = { it.endTime }
        )
        val exercises = selectUnambiguousSource(
            "運動", rawExercises, origin = { it.metadata.dataOrigin.packageName },
            completeness = { 1 }, measuredAt = { it.endTime }
        ) {
            SourceIntervalMeasurement(
                it.metadata.dataOrigin.packageName,
                it.startTime,
                it.endTime,
                Duration.between(it.startTime, it.endTime).toMillis().toDouble()
            )
        }
        val rawSleep = readRecords("睡眠", HealthPermission.getReadPermission(SleepSessionRecord::class)) {
            HealthInsightWindowPolicy.retainOverlappingIntervals(
                client.readAllRecords(SleepSessionRecord::class, filter), start, now,
                { it.startTime }, { it.endTime }
            )
        }
        val sleep = selectUnambiguousSource(
            "睡眠", rawSleep, origin = { it.metadata.dataOrigin.packageName },
            completeness = { 1 }, measuredAt = { it.endTime }
        ) {
            SourceIntervalMeasurement(
                it.metadata.dataOrigin.packageName,
                it.startTime,
                it.endTime,
                Duration.between(it.startTime, it.endTime).toMillis().toDouble()
            )
        }
        val rawResting = readRecords("靜息心率", HealthPermission.getReadPermission(RestingHeartRateRecord::class)) {
            client.readAllRecords(RestingHeartRateRecord::class, filter)
        }
        val windowResting = onlyInQueryWindow("靜息心率", rawResting) { it.time }
        val restingSourceSelection = AlgorithmicInsightProvenance.selectMostCompleteOrigin(
            windowResting,
            origin = { it.metadata.dataOrigin.packageName },
            completeness = { if (it.beatsPerMinute in 30L..120L) 1 else 0 },
            measuredAt = { it.time }
        )
        if (windowResting.orEmpty().map { it.metadata.dataOrigin.packageName }.distinct().size > 1) {
            unavailableInputs += "靜息心率：多來源時只採最近且樣本數最多的單一來源"
        }
        val resting = restingSourceSelection?.records
        val rawOxygen = readRecords("血氧", HealthPermission.getReadPermission(OxygenSaturationRecord::class)) {
            client.readAllRecords(OxygenSaturationRecord::class, filter)
        }
        val windowOxygen = onlyInQueryWindow("血氧", rawOxygen) { it.time }
        val oxygenSelection = AlgorithmicInsightProvenance.selectMostCompleteOrigin(
            windowOxygen,
            origin = { it.metadata.dataOrigin.packageName },
            completeness = { if (it.percentage.value.isFinite() && it.percentage.value in 0.0..100.0) 1 else 0 },
            measuredAt = { it.time }
        )
        if (windowOxygen.orEmpty().map { it.metadata.dataOrigin.packageName }.distinct().size > 1) {
            unavailableInputs += "血氧趨勢：多來源時只採有效樣本數最多的單一來源"
        }
        val oxygen = oxygenSelection?.records
        val rawHrvRecords = readRecords("HRV", HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)) {
            client.readAllRecords(HeartRateVariabilityRmssdRecord::class, filter)
        }
        val windowHrvRecords = onlyInQueryWindow("HRV", rawHrvRecords) { it.time }
        val hrvSourceSelection = AlgorithmicInsightProvenance.selectMostCompleteOrigin(
            windowHrvRecords,
            origin = { it.metadata.dataOrigin.packageName },
            completeness = { if (it.heartRateVariabilityMillis.isFinite() && it.heartRateVariabilityMillis > 0.0) 1 else 0 },
            measuredAt = { it.time }
        )
        if (windowHrvRecords.orEmpty().map { it.metadata.dataOrigin.packageName }.distinct().size > 1) {
            unavailableInputs += "HRV 趨勢：多來源時只採有效紀錄數最多的單一來源"
        }
        val hrvRecords = hrvSourceSelection?.records
        val rawTemperatures = readRecords("體表溫度", HealthPermission.getReadPermission(BodyTemperatureRecord::class)) {
            client.readAllRecords(BodyTemperatureRecord::class, filter)
                .filter {
                    it.metadata.dataOrigin.packageName != appContext.packageName &&
                        it.measurementLocation == BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_WRIST
                }
        }
        val windowTemperatures = onlyInQueryWindow("體表溫度", rawTemperatures) { it.time }
        val temperatureSelection = AlgorithmicInsightProvenance.selectMostCompleteOrigin(
            windowTemperatures,
            origin = { it.metadata.dataOrigin.packageName },
            completeness = { if (it.temperature.inCelsius.isFinite()) 1 else 0 },
            measuredAt = { it.time }
        )
        if (windowTemperatures.orEmpty().map { it.metadata.dataOrigin.packageName }.distinct().size > 1) {
            unavailableInputs += "腕部溫度趨勢：多來源時只採有效紀錄數最多的單一來源"
        }
        val temperatures = temperatureSelection?.records
        val rawDistances = readContainedIntervals(
            "距離", HealthPermission.getReadPermission(DistanceRecord::class), start, now,
            read = { client.readAllRecords(DistanceRecord::class, filter) },
            recordStart = { it.startTime }, recordEnd = { it.endTime }
        )
        val distances = selectUnambiguousSource(
            "距離", rawDistances, origin = { it.metadata.dataOrigin.packageName },
            completeness = { record -> java.time.Duration.between(record.startTime, record.endTime).seconds
                .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt() }, measuredAt = { it.endTime }
        ) {
            SourceIntervalMeasurement(
                it.metadata.dataOrigin.packageName,
                it.startTime,
                it.endTime,
                it.distance.inMeters
            )
        }

        val baselineRecord = resting.orEmpty().asSequence()
            .filter { it.beatsPerMinute in 30L..120L }
            .maxByOrNull { it.time }
        val baseline = baselineRecord?.beatsPerMinute?.toDouble()
        val validHrvRecords = hrvRecords.orEmpty().filter {
            it.heartRateVariabilityMillis.isFinite() && it.heartRateVariabilityMillis > 0.0
        }
        val hrvPersonalBaselineDeviation = SensorHealthAlgorithms.hrvDeviationFromPersonalBaseline(
            validHrvRecords.map {
                it.time.atZone(localZone).toLocalDate() to it.heartRateVariabilityMillis
            }
        )
        val hrvOrigin = validHrvRecords.firstOrNull()?.metadata?.dataOrigin?.packageName
        val latestObservedRestingHeartRate = rawResting.orEmpty()
            .filter { !it.time.isBefore(start) && !it.time.isAfter(now) }
            .filter { it.beatsPerMinute in 30L..120L }
            .maxByOrNull { it.time }
        val latestObservedHrvRecord = validHrvRecords
            .maxByOrNull { it.time }

        val uniqueSleep = sleep
        val sleepWindowStart = now.minus(30, ChronoUnit.HOURS)
        val sleepSessionValues = uniqueSleep?.map { record ->
                val awake = record.stages.filter { stage ->
                    stage.stage == SleepSessionRecord.STAGE_TYPE_AWAKE ||
                        stage.stage == SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED ||
                        stage.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
                }.map { SleepAwakeInterval(it.startTime, it.endTime) }
                val allStages = record.stages.map { SleepAwakeInterval(it.startTime, it.endTime) }
                val knownStageTypes = record.stages.all {
                    it.stage in setOf(
                        SleepSessionRecord.STAGE_TYPE_AWAKE,
                        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
                        SleepSessionRecord.STAGE_TYPE_SLEEPING,
                        SleepSessionRecord.STAGE_TYPE_LIGHT,
                        SleepSessionRecord.STAGE_TYPE_DEEP,
                        SleepSessionRecord.STAGE_TYPE_REM
                    )
                }
                SleepSessionValue(record.startTime, record.endTime, awake, record.stages.isNotEmpty(), allStages, knownStageTypes)
            }
        val sleepStagesComplete = sleepSessionValues?.let {
            HealthInsightInputPolicy.sleepStagesCoverSessions(it, sleepWindowStart, now)
        } == true
        if (sleepSessionValues?.isNotEmpty() == true && !sleepStagesComplete) {
            unavailableInputs += "睡眠時長分數：Health Connect 睡眠階段缺失、不連續、重疊、超出 session 範圍或含未知階段；保留 session 時段但不當作實際睡眠時長計分"
        }
        val sleepSessionHours = uniqueSleep?.let { records ->
            SleepIntervalMath.unionMinutes(records.map { it.startTime to it.endTime }, sleepWindowStart, now) / 60.0
        }
        val sleepHours = HealthInsightInputPolicy.sleepHoursWhenStagesComplete(
            sleepStagesComplete, sleepSessionValues, sleepWindowStart, now
        )
        val sleepSessionEndAt = uniqueSleep.orEmpty().asSequence()
            .filter { it.endTime.isAfter(sleepWindowStart) && !it.endTime.isAfter(now) && it.startTime.isBefore(now) }
            .maxOfOrNull { it.endTime }
        // A low heart-rate span does not establish a sleep session or its duration.
        // Only stage-complete recorded sleep may enter health summaries and scoring.
        val sleepInference: SleepInferencePolicy.Result? = null
        val sleepInferred = false
        val effectiveSleep = sleepHours?.takeIf { it > 0 }
        var todayCaloriesProvenance: String? = null
        val todayCalorieRecords = calories?.filter { it.startTime >= todayStart && it.endTime <= now }
            ?.takeIf { it.isNotEmpty() }
        val todayCalorieOrigins = todayCalorieRecords.orEmpty().map { it.metadata.dataOrigin.packageName }
        val validatedTodayCalories = todayCalorieRecords?.let { entries ->
            SourceIntervalAggregation.sumOrNull(entries.map {
                SourceIntervalMeasurement(it.metadata.dataOrigin.packageName, it.startTime, it.endTime, it.energy.inKilocalories)
            })
        }
        val todayCaloriesPermission = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        val todayCalories = if (
            HealthConnectPermissionPolicy.state(todayCaloriesPermission, granted) == HealthConnectPermissionPolicy.State.GRANTED
        ) {
            if (!HealthConnectAggregatePolicy.shouldQuery(
                    (appContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isInteractive == true
                )) {
                if (validatedTodayCalories != null) todayCaloriesProvenance = "單一來源 ${todayCalorieRecords?.first()?.metadata?.dataOrigin?.packageName}；非重疊原始區間加總"
                validatedTodayCalories
            } else try {
                val result = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(todayStart, now)
                    )
                )
                val aggregateCalories = result?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories
                val aggregateOrigins = result?.dataOrigins?.map { it.packageName }.orEmpty()
                if (HealthConnectAggregatePolicy.acceptsDeduplicatedActivityTotal(aggregateCalories, aggregateOrigins)) {
                    todayCaloriesProvenance = "Health Connect Activity 去重彙總；來源 ${aggregateOrigins.size} 個"
                    aggregateCalories
                } else {
                    if (aggregateCalories != null) {
                        unavailableInputs += "今日活動卡路里：平台彙總缺少有效值或來源；僅採用已驗證的同來源非重疊區間"
                    }
                    if (validatedTodayCalories != null) todayCaloriesProvenance = "單一來源 ${todayCalorieRecords?.first()?.metadata?.dataOrigin?.packageName}；非重疊原始區間加總"
                    validatedTodayCalories
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (validatedTodayCalories == null) {
                    unavailableInputs += "今日活動卡路里：彙總與記錄均不可用"
                    null
                } else {
                    todayCaloriesProvenance = "單一來源 ${todayCalorieRecords?.first()?.metadata?.dataOrigin?.packageName}；非重疊原始區間加總"
                    validatedTodayCalories
                }
            }
        } else null
        val exerciseIntervals = exercises?.takeIf { it.isNotEmpty() }?.map { it.startTime to it.endTime }
        val exerciseMinutes = exerciseIntervals?.sumOf { (startTime, endTime) ->
            SensorHealthAlgorithms.intervalDurationMinutes(startTime, endTime) ?: 0.0
        }
        val sleepOrigin = sleep?.firstOrNull()?.metadata?.dataOrigin?.packageName
        val estimatedDailyCalories = profile?.let {
            DailyEnergyEstimate.totalKcal(it, todayCalories, now, localZone)
        }
        val totalSteps = steps?.sumOf { it.count }
        val totalDistance = distances?.sumOf { it.distance.inMeters }
        val gaitInputsComparable = steps != null && distances != null && SourceIntervalAggregation.hasEquivalentCoverage(
            steps.map {
                SourceIntervalMeasurement(it.metadata.dataOrigin.packageName, it.startTime, it.endTime, it.count.toDouble())
            },
            distances.map {
                SourceIntervalMeasurement(it.metadata.dataOrigin.packageName, it.startTime, it.endTime, it.distance.inMeters)
            }
        )
        if (steps != null && distances != null && !gaitInputsComparable) {
            unavailableInputs += "平均步長：步數與距離的來源或涵蓋時段不一致，未合併計算"
        }
        val gaitEfficiency = if (totalDistance != null && totalSteps != null && gaitInputsComparable)
            SensorHealthAlgorithms.gaitEfficiency(totalDistance, totalSteps) else null
        val oxygenSamples = oxygen?.takeIf { records ->
            records.all { !it.time.isBefore(start) && !it.time.isAfter(now) }
        }?.map { it.time to it.percentage.value }
        if (oxygen != null && oxygenSamples == null) {
            unavailableInputs += "血氧：有樣本時間超出查詢區間，整組資料未納入摘要"
        }
        val rejectedOxygenCount = oxygenSamples?.count { (_, value) -> !value.isFinite() || value !in 0.0..100.0 } ?: 0
        if (rejectedOxygenCount > 0) {
            unavailableInputs += "血氧：$rejectedOxygenCount 筆樣本值不在 Health Connect 0–100 範圍，已排除"
        }
        val oxygenSummary = oxygenSamples?.let(SensorHealthAlgorithms::timedOxygenSampleSummary)
        val oxygenWindowStartAt = oxygenSummary?.firstValidSampleAt
        val oxygenWindowEndAt = oxygenSummary?.lastValidSampleAt
        val wristTemperatureRecords = temperatures.orEmpty().filter {
            it.measurementLocation == BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_WRIST
        }
        val temperatureDeviation = wristTemperatureRecords
            .map { it.time.atZone(localZone).toLocalDate() to it.temperature.inCelsius }
            .let(SensorHealthAlgorithms::temperatureDeviationByDay)
        val wristTemperatureMeasuredAt = wristTemperatureRecords.maxOfOrNull { it.time }
        val sleepRegularity = uniqueSleep?.let { SensorHealthAlgorithms.sleepRegularity(it) }
        val restingHeartRateValues = windowResting.orEmpty().filter { it.beatsPerMinute in 30L..120L }.map {
            HealthInsightInputPolicy.TimedSourceValue(it, it.time, it.metadata.dataOrigin.packageName)
        }
        val trimpInputsAvailable = heartRateOrigin != null && restingHeartRateValues.isNotEmpty()
        if (exerciseMinutes != null && exerciseMinutes > 0L && profile?.sex == PhysiologyAlgorithms.Sex.UNSPECIFIED) {
            unavailableInputs += "TRIMP：此 Banister 公式需要性別相關係數；個人資料未指定性別，未推定係數"
        }
        val usedTrimpIntervals = mutableListOf<Pair<Instant, Instant>>()
        val usedTrimpRestingHeartRateTimes = mutableListOf<Instant>()
        val usedTrimpRestingHeartRateOrigins = mutableSetOf<String>()
        var missingSameDayTrimpBaseline = false
        val trimp = if (profile != null && exerciseMinutes != null && exerciseMinutes > 0.0 && trimpInputsAvailable) {
            exerciseIntervals.orEmpty().mapNotNull { (startTime, endTime) ->
                val sessionSamples = samples.filter { !it.time.isBefore(startTime) && it.time.isBefore(endTime) }
                if (!AlgorithmicInsightProvenance.hasPairedSamples(startTime, endTime, sessionSamples.map { it.time })) return@mapNotNull null
                val sessionBaseline = HealthInsightInputPolicy.latestSameDayBefore(
                    restingHeartRateValues, startTime, localZone, heartRateOrigin
                )
                if (sessionBaseline == null) {
                    missingSameDayTrimpBaseline = true
                    return@mapNotNull null
                }
                val durationMinutes = SensorHealthAlgorithms.intervalDurationMinutes(startTime, endTime) ?: return@mapNotNull null
                val sessionTrimp = SensorHealthAlgorithms.banisterTrimp(
                    durationMinutes,
                    sessionSamples.map { it.beatsPerMinute.toDouble() }.average(), sessionBaseline.value.beatsPerMinute.toDouble(),
                    208.0 - 0.7 * profile.age, profile.sex
                )
                if (sessionTrimp != null) {
                    usedTrimpIntervals += startTime to endTime
                    usedTrimpRestingHeartRateTimes += sessionBaseline.time
                    usedTrimpRestingHeartRateOrigins += sessionBaseline.source
                }
                sessionTrimp
            }.takeIf { it.isNotEmpty() }?.sum()
        } else null
        if (exerciseMinutes != null && exerciseMinutes > 0.0 && missingSameDayTrimpBaseline) {
            unavailableInputs += "TRIMP：運動當地日期沒有運動開始前的靜息心率紀錄，該場未計入"
        }
        val trainingWindowStartAt = usedTrimpIntervals.minOfOrNull { it.first }
        val trainingWindowEndAt = usedTrimpIntervals.maxOfOrNull { it.second }
        val trimpCandidateSessionCount = exerciseIntervals.orEmpty().size
        val trimpIncludedSessionCount = usedTrimpIntervals.size
        val trimpBaselineWindowStartAt = usedTrimpRestingHeartRateTimes.minOrNull()
        val trimpBaselineWindowEndAt = usedTrimpRestingHeartRateTimes.maxOrNull()
        val trainingLoad = trimp?.roundToInt()
        val vo2HeartRatio = if (profile != null && baseline != null) SensorHealthAlgorithms.vo2MaxHeartRateRatio(profile.age, baseline) else null
        val observedAggregateStepDays = HealthConnectActivityObservationPolicy.observedDailyStepCounts(stepsObservation)
        val activityRegularity = SensorHealthAlgorithms.activityRegularity(observedAggregateStepDays)
        val restingDeviation = resting?.let {
            SensorHealthAlgorithms.restingHeartRateDeviation(it.sortedBy { record -> record.time }.map { record -> record.beatsPerMinute.toDouble() })
        }
        val socialJetlag = uniqueSleep?.let { SensorHealthAlgorithms.socialJetlagMinutes(it) }
        val recordedSleepDurationGap = profile?.takeIf { sleepStagesComplete }?.let { user ->
            uniqueSleep?.let { SensorHealthAlgorithms.recordedSleepDurationGapMinutes(it, user.age) }
        }
        val confidenceInputsShareOrigin = AlgorithmicInsightProvenance.sameOrigin(
            heartRateOrigin,
            resting?.firstOrNull()?.metadata?.dataOrigin?.packageName,
            hrvOrigin,
            sleepOrigin
        )
        if (samples.isNotEmpty() && baseline != null && validHrvRecords.isNotEmpty() && sleepHours?.let { it > 0 } == true && !confidenceInputsShareOrigin) {
            unavailableInputs += "信心度：心率、靜息心率、HRV 與睡眠來源不一致，未合併評級"
        }
        val restingOrigin = resting?.firstOrNull()?.metadata?.dataOrigin?.packageName
        val heartRateReserveEquivalentMinutes = profile?.takeIf {
            HealthInsightCompositeInputPolicy.canComputeHeartRateReserveEquivalentMinutes(
                hasProfile = true,
                hasRestingHeartRate = baseline != null,
                hasHeartRateSamples = samples.isNotEmpty(),
                hasKnownHeartRateAndRestingOrigins = heartRateOrigin != null && restingOrigin != null
            )
        }?.let { user ->
            SensorHealthAlgorithms.heartRateReserveEquivalentMinutes(
                samples.map { it.time to it.beatsPerMinute.toDouble() }, baseline ?: return@let null,
                208.0 - 0.7 * user.age
            )
        }

        val compositePaired = ResearchCompositeInputPolicy.canPair(
            latestObservedHrvRecord?.time,
            sleepSessionEndAt,
            hrvOrigin,
            sleepOrigin,
            now
        )
        val compositeCrossOrigin = compositePaired && hrvOrigin != sleepOrigin
        if (compositeCrossOrigin) {
            unavailableInputs += "研究整合模型：HRV 來源 $hrvOrigin、睡眠來源 $sleepOrigin；依時間配對產生跨來源低信心研究估算，未視為同一裝置演算法量測"
        }
        val composite = ResearchCompositeModel.calculate(
            hrvPersonalBaselineDeviation?.latest, hrvPersonalBaselineDeviation?.baseline,
            sleepHours, compositePaired
        )
        if (composite.readiness == null) unavailableInputs += "研究整合模型：需近期有來源且時間可配對的 HRV／睡眠，以及至少三個 HRV 紀錄日建立個人基準"
        return Insights(
            historicalSleepSessionHours = AlgorithmicInsightProvenance.selectMostCompleteOrigin(
                rawSleep, origin = { it.metadata.dataOrigin.packageName }, completeness = { 1 }, measuredAt = { it.endTime }
            )?.records?.let { records ->
                SleepIntervalMath.unionMinutes(records.map { it.startTime to it.endTime }, start, now) / 60.0
            },
            stress = composite.stress,
            bodyEnergy = composite.bodyEnergy,
            recovery = composite.recovery,
            trainingLoad = trainingLoad,
            recordedExerciseMinutes = exerciseMinutes?.toInt(),
            sleepHours = effectiveSleep,
            stepsObservation = stepsObservation,
            activeCaloriesObservation = calorieObservation,
            sleepSessionHours = sleepSessionHours,
            sleepStagesComplete = sleepStagesComplete,
            activityLoadIncluded = false,
            sleepInferred = sleepInferred,
            oxygenAvailable = oxygenSummary != null,
            hrvAvailable = validHrvRecords.isNotEmpty(),
            estimatedDailyCalories = estimatedDailyCalories,
            ansAdvice = null,
            readiness = composite.readiness,
            sleepRegularity = sleepRegularity,
            restingHeartRateAt = latestObservedRestingHeartRate?.time,
            hrvAt = latestObservedHrvRecord?.time,
            latestObservedRestingHeartRateBpm = latestObservedRestingHeartRate?.beatsPerMinute?.toDouble(),
            latestObservedRestingHeartRateAt = latestObservedRestingHeartRate?.time,
            latestObservedRestingHeartRateOrigin = latestObservedRestingHeartRate?.metadata?.dataOrigin?.packageName,
            latestObservedHrvMs = latestObservedHrvRecord?.heartRateVariabilityMillis,
            latestObservedHrvAt = latestObservedHrvRecord?.time,
            latestObservedHrvOrigin = latestObservedHrvRecord?.metadata?.dataOrigin?.packageName,
            hrvPersonalBaselineDeviation = hrvPersonalBaselineDeviation,
            sleepSessionEndAt = sleepSessionEndAt,
            sleepInferenceStartAt = sleepInference?.startAt,
            sleepInferenceEndAt = sleepInference?.endAt,
            wristTemperatureMeasuredAt = wristTemperatureMeasuredAt,
            trainingWindowStartAt = trainingWindowStartAt,
            trainingWindowEndAt = trainingWindowEndAt,
            trimpBaselineWindowStartAt = trimpBaselineWindowStartAt,
            trimpBaselineWindowEndAt = trimpBaselineWindowEndAt,
            trimpHeartRateOrigin = heartRateOrigin,
            trimpCandidateSessionCount = trimpCandidateSessionCount,
            trimpIncludedSessionCount = trimpIncludedSessionCount,
            trimpRestingHeartRateOrigins = usedTrimpRestingHeartRateOrigins,
            oxygenWindowStartAt = oxygenWindowStartAt,
            oxygenWindowEndAt = oxygenWindowEndAt,
            dailyEnergyWindowStartAt = todayStart.takeIf { estimatedDailyCalories != null },
            dailyEnergyWindowEndAt = now.takeIf { estimatedDailyCalories != null },
            activityLoadWindowStartAt = null,
            activityLoadWindowEndAt = null,
            sourceDataCutoffAt = now,
            generatedAt = Instant.now(),
            oxygenSampleSummary = oxygenSummary,
            temperatureDeviation = temperatureDeviation,
            gaitEfficiency = gaitEfficiency,
            trimp = trimp,
            vo2HeartRatio = vo2HeartRatio,
            activityRegularity = activityRegularity,
            activityRegularityObservedDays = observedAggregateStepDays.size,
            restingDeviation = restingDeviation,
            socialJetlagMinutes = socialJetlag,
            recordedSleepDurationGapMinutes = recordedSleepDurationGap,
            trainingMonotony = null,
            trainingStrain = null,
            heartRateReserveEquivalentMinutes = heartRateReserveEquivalentMinutes,
            dailyEnergyProvenance = todayCaloriesProvenance,
            recordedTotalCaloriesKcal = recordedTotalCaloriesKcal?.roundToInt(),
            recordedTotalCaloriesOrigin = recordedTotalCaloriesOrigin,
            recordedTotalCaloriesState = recordedTotalCaloriesState,
            recordedTotalCaloriesRecordCount = totalCalories?.size ?: 0,
            recordedTotalCaloriesExcludedBoundaryRecords = excludedTotalCaloriesBoundaryRecords,
            confidence = when {
                composite.readiness != null -> "低"
                !confidenceInputsShareOrigin -> "不足"
                samples.size >= 100 && baseline != null && sleepHours?.let { it > 0 } == true && validHrvRecords.isNotEmpty() -> "中"
                samples.isNotEmpty() || effectiveSleep != null || validHrvRecords.isNotEmpty() -> "低"
                else -> "不足"
            },
            unavailableInputs = unavailableInputs,
            emptyInputs = emptyInputs
        )
    }

    data class Insights(
        val stress: Int?, val bodyEnergy: Int?, val recovery: Int?, val trainingLoad: Int?,
        val recordedExerciseMinutes: Int?, val sleepHours: Double?, val sleepInferred: Boolean, val confidence: String
        , val oxygenAvailable: Boolean, val hrvAvailable: Boolean, val estimatedDailyCalories: Int?, val ansAdvice: String?, val readiness: Int?,
        val stepsObservation: HealthConnectActivityObservationPolicy.Summary = HealthConnectActivityObservationPolicy.summarize(
            HealthConnectActivityObservationPolicy.State.NO_RECORDS, Instant.EPOCH, Instant.EPOCH, emptyList(), emptyList(), null, 0
        ),
        val activeCaloriesObservation: HealthConnectActivityObservationPolicy.Summary = HealthConnectActivityObservationPolicy.summarize(
            HealthConnectActivityObservationPolicy.State.NO_RECORDS, Instant.EPOCH, Instant.EPOCH, emptyList(), emptyList(), null, 0
        ),
        val sleepRegularity: Int? = null, val oxygenSampleSummary: SensorHealthAlgorithms.OxygenSampleSummary? = null, val temperatureDeviation: Double? = null,
        val gaitEfficiency: Double? = null, val trimp: Double? = null, val vo2HeartRatio: Double? = null,
        val activityRegularity: Int? = null, val activityRegularityObservedDays: Int = 0,
        val restingDeviation: Double? = null,
        val socialJetlagMinutes: Int? = null, val recordedSleepDurationGapMinutes: Int? = null,
        val trainingMonotony: Double? = null, val trainingStrain: Double? = null,
        val heartRateReserveEquivalentMinutes: Int? = null,
        val dailyEnergyProvenance: String? = null,
        val recordedTotalCaloriesKcal: Int? = null,
        val recordedTotalCaloriesOrigin: String? = null,
        val recordedTotalCaloriesState: RecordedTotalCaloriesState = RecordedTotalCaloriesState.NO_RECORDS,
        val recordedTotalCaloriesRecordCount: Int = 0,
        val recordedTotalCaloriesExcludedBoundaryRecords: Int = 0,
        val unavailableInputs: List<String> = emptyList(), val emptyInputs: List<String> = emptyList(),
        val restingHeartRateAt: Instant? = null, val hrvAt: Instant? = null, val sleepSessionEndAt: Instant? = null,
        val sleepInferenceStartAt: Instant? = null, val sleepInferenceEndAt: Instant? = null,
        val wristTemperatureMeasuredAt: Instant? = null,
        val trainingWindowStartAt: Instant? = null, val trainingWindowEndAt: Instant? = null,
        val trimpBaselineWindowStartAt: Instant? = null, val trimpBaselineWindowEndAt: Instant? = null,
        val trimpHeartRateOrigin: String? = null,
        val trimpCandidateSessionCount: Int = 0, val trimpIncludedSessionCount: Int = 0,
        val trimpRestingHeartRateOrigins: Set<String> = emptySet(),
        val oxygenWindowStartAt: Instant? = null, val oxygenWindowEndAt: Instant? = null,
        val dailyEnergyWindowStartAt: Instant? = null, val dailyEnergyWindowEndAt: Instant? = null,
        val activityLoadWindowStartAt: Instant? = null, val activityLoadWindowEndAt: Instant? = null,
        val sourceDataCutoffAt: Instant? = null,
        val sleepSessionHours: Double? = null, val sleepStagesComplete: Boolean = false,
        val activityLoadIncluded: Boolean = false,
        val generatedAt: Instant? = null,
        val latestObservedRestingHeartRateBpm: Double? = null,
        val latestObservedRestingHeartRateAt: Instant? = null,
        val latestObservedRestingHeartRateOrigin: String? = null,
        val latestObservedHrvMs: Double? = null,
        val latestObservedHrvAt: Instant? = null,
        val latestObservedHrvOrigin: String? = null,
        val hrvPersonalBaselineDeviation: SensorHealthAlgorithms.PersonalBaselineDeviation? = null,
        val historicalSleepSessionHours: Double? = null,
    ) {
        fun display() = buildString {
            append("演算法健康洞察（非醫療量測）\n")
            fun measurementTime(time: Instant?) = time?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date.from(it))
            } ?: "時間未提供"
            val observedResting = latestObservedRestingHeartRateBpm?.let { "%.0f bpm".format(it) } ?: "無紀錄"
            val observedHrv = latestObservedHrvMs?.let { "%.1f ms".format(it) } ?: "無紀錄"
            append("獨立觀測（非複合分數）：靜息心率 $observedResting，${measurementTime(latestObservedRestingHeartRateAt)}，來源 ${latestObservedRestingHeartRateOrigin ?: "未提供"}；HRV $observedHrv，${measurementTime(latestObservedHrvAt)}，來源 ${latestObservedHrvOrigin ?: "未提供"}。\n")
            append("HRV 個人趨勢（每日中位數；最新有紀錄日相對此前 ${hrvPersonalBaselineDeviation?.baselineDays ?: 0} 個有紀錄日中位數，描述性、非壓力診斷）：${hrvPersonalBaselineDeviation?.let { "%+.1f ms（最新 %.1f / 個人基準 %.1f ms）".format(it.difference, it.latest, it.baseline) } ?: "需至少 3 個同來源有效紀錄日"}\n")
            append("腕溫來源時間：${measurementTime(wristTemperatureMeasuredAt)}；訓練負荷區間：${trainingWindowStartAt?.let(::measurementTime) ?: "未取得"} 至 ${trainingWindowEndAt?.let(::measurementTime) ?: "未取得"}；TRIMP 所用靜息心率時間：${trimpBaselineWindowStartAt?.let(::measurementTime) ?: "未取得"} 至 ${trimpBaselineWindowEndAt?.let(::measurementTime) ?: "未取得"}\n")
            append("血氧資料觀測區間：${oxygenWindowStartAt?.let(::measurementTime) ?: "未取得"} 至 ${oxygenWindowEndAt?.let(::measurementTime) ?: "未取得"}；每日能量計算區間：${dailyEnergyWindowStartAt?.let(::measurementTime) ?: "未取得"} 至 ${dailyEnergyWindowEndAt?.let(::measurementTime) ?: "未取得"}\n")
            append("Health Connect 活動資料使用平台 aggregate 去重；樣本日步數一致性只作探索規則分，不推導步態／負荷／ANS／TRIMP：\n")
            append(stepsObservation.display("步數", "步", ZoneId.systemDefault()).replace("\n", "\n  ") + "\n")
            append(activeCaloriesObservation.display("活動卡路里", "kcal", ZoneId.systemDefault()).replace("\n", "\n  ") + "\n")
            append("Health Connect 查詢資料截止（非量測時間）：${measurementTime(sourceDataCutoffAt)}\n")
            append("研究規則 ${ResearchCompositeModel.VERSION}（低信心、未臨床驗證）：壓力 ${stress ?: "待配對"}／身體能量 ${bodyEnergy ?: "待配對"}／恢復 ${recovery ?: "待配對"}／準備度 ${readiness ?: "待配對"}；有值時為模型估算\n")
            append("相鄰有睡眠紀錄日時段相似度（探索規則分；缺紀錄日不視為無睡眠）  ${sleepRegularity?.let { "$it/100" } ?: "沒有可比較的相鄰紀錄日"}\n")
            append("近 7 日有效血氧樣本範圍（n=${oxygenSampleSummary?.validSampleCount ?: 0}；非夜間專項）  ${oxygenSampleSummary?.let { "%.1f–%.1f%%".format(it.minimumPercent, it.maximumPercent) } ?: "等待血氧紀錄"}\n")
            append("手腕溫度相對近 7 日個人中位數（描述性）  ${temperatureDeviation?.let { "%+.1f °C".format(it) } ?: "需至少 4 個有腕部紀錄日"}\n")
            append("平均步長  ${gaitEfficiency?.let { "%.2f m/步".format(it) } ?: "等待步數與距離"}\n")
            val trimpCoverage = if (trimpCandidateSessionCount > 0) "（納入 $trimpIncludedSessionCount/$trimpCandidateSessionCount 場）" else ""
            append("TRIMP  ${trimp?.let { "%.0f".format(it) } ?: "需要運動心率與個人資料"}$trimpCoverage\n")
            if (trimp != null && trimpRestingHeartRateOrigins.any { it != trimpHeartRateOrigin }) {
                append("TRIMP 資料來源：心率 $trimpHeartRateOrigin；靜息心率 ${trimpRestingHeartRateOrigins.sorted().joinToString()}；同來源優先、再依同日量測時間配對，屬跨來源研究估算\n")
            }
            append("VO₂max 公式估算（年齡推算 HRmax；非運動測試）  ${vo2HeartRatio?.let { "%.1f ml/kg/min".format(it) } ?: "需要靜息心率與年齡"}\n")
            append("近 7 日步數一致性（Health Connect aggregate 有值日 n=$activityRegularityObservedDays；探索規則分，非完整週）  ${activityRegularity?.let { "$it/100" } ?: "至少需要 3 個有 aggregate 紀錄日"}\n")
            append("靜息心率偏移  ${restingDeviation?.let { "%+.0f bpm".format(it) } ?: "等待靜息心率紀錄"}\n")
            append("社交時差  ${socialJetlagMinutes?.let { "$it 分鐘" } ?: "需要平日與週末睡眠"}\n")
            append("已記錄睡眠時段短於目標的累積差額（估算）  ${recordedSleepDurationGapMinutes?.let { "${it / 60} 小時 ${it % 60} 分" } ?: "需要至少 3 個有紀錄夜晚"}\n")
            append("心率儲備加權活動分鐘（估算；非 MET）  ${heartRateReserveEquivalentMinutes ?: "有效心率時序不足"}\n")
            append("活動卡路里僅作活動紀錄，不作訓練負荷代理；訓練負荷僅列具運動心率與個人資料的 TRIMP\n")
            append("近 7 日有效樣本中低於 90% 的比例（描述性；非夜間專項）  ${oxygenSampleSummary?.below90Percent?.let { "%.1f%%".format(it) } ?: "至少需要 3 個有效樣本"}\n")
            append("7 日 Banister TRIMP 相對負荷（AU；每場至少 2 筆運動心率樣本的算術平均；同日且運動前最近靜息心率；最大心率採年齡估算，非個人化校準）  ${trainingLoad?.toString() ?: "需要有效運動心率與基準"}\n")
            append("已記錄運動時長  ${recordedExerciseMinutes?.let { "$it 分鐘" } ?: "沒有可用的運動紀錄"}（重疊時段已合併）\n")
            append("今日總消耗估計（截至目前）  ${estimatedDailyCalories?.let { "$it kcal" } ?: "需要有效活動熱量與個人資料"}")
            dailyEnergyProvenance?.let { append("（來源：$it）") }
            append("\n")
            append(RecordedTotalCaloriesPolicy.display(recordedTotalCaloriesState, recordedTotalCaloriesKcal, recordedTotalCaloriesOrigin, recordedTotalCaloriesRecordCount, recordedTotalCaloriesExcludedBoundaryRecords) + "\n")
            append(when {
                sleepInferred -> "低心率樣本跨度（非睡眠紀錄；心率推估樣本區間 ${measurementTime(sleepInferenceStartAt)} 至 ${measurementTime(sleepInferenceEndAt)}）  ${sleepHours?.let { "%.1f 小時".format(it) } ?: "無"}\n"
                sleepStagesComplete -> "睡眠時長（Health Connect 分期完整覆蓋，扣除已記錄清醒）  ${sleepHours?.let { "%.1f 小時".format(it) } ?: "無"}\n"
                sleepSessionHours != null -> "睡眠 session 時段  ${"%.1f 小時".format(sleepSessionHours)}；階段缺失／不連續，非實際睡眠時數，不計睡眠分數\n"
                else -> "睡眠  等待睡眠同步\n"
            })
            append("信心度  $confidence\n\n")
            append(if (oxygenAvailable) "SpO₂：已有 Health Connect 實測資料，不使用演算法推估\n" else "SpO₂：尚無實測；不可由心率可靠推估\n")
            append(if(hrvAvailable) "HRV：使用 Health Connect RMSSD 紀錄\n" else "HRV：無有效 Health Connect RMSSD 紀錄\n")
            if (unavailableInputs.isNotEmpty()) append("無法讀取：${unavailableInputs.joinToString("、")}\n")
            if (emptyInputs.isNotEmpty()) append("無資料的查詢區間：${emptyInputs.joinToString("、")}\n")
            append("\n臨床公開資料比對\n")
            restingDeviation?.let { append("· 靜息心率偏移 ${"%+.0f".format(it)} bpm · ${ClinicalReferenceTables.RESTING_HR_BPM.label}\n") }
            if (!sleepInferred && sleepStagesComplete) sleepHours?.let { h -> ClinicalReferenceTables.sleepCompare(h).forEach { append("· $it\n") } }
            else append("· 低心率跨度只作探索觀察，不作睡眠時長或臨床常模比較\n")
            gaitEfficiency?.let { ClinicalReferenceTables.gaitCompare(it).forEach { line -> append("· $line\n") } }
            append("不可可靠推估：血壓、體重、ECG；所有洞察僅供健康參考\n")
            append(ClinicalVerdict.DISCLAIMER)
        }
    }
}

internal object AlgorithmicInsightProvenance {
    data class OriginSelection<T>(val origin: String, val records: List<T>)

    fun hasSingleOrigin(origins: List<String>): Boolean =
        origins.isNotEmpty() && origins.none(String::isBlank) && origins.distinct().size == 1

    fun <T> selectMostCompleteOrigin(
        records: List<T>?,
        origin: (T) -> String,
        completeness: (T) -> Int,
        measuredAt: (T) -> Instant
    ): OriginSelection<T>? {
        val selected = records.orEmpty()
            .filter { origin(it).isNotBlank() && completeness(it) > 0 }
            .groupBy(origin)
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<T>>> { entry -> entry.value.sumOf { completeness(it).coerceAtLeast(0).toLong() } }
                .thenByDescending { entry -> entry.value.maxOfOrNull(measuredAt) ?: Instant.MIN }
                .thenBy { it.key }
            )
            .toList()
        val best = selected.firstOrNull() ?: return null
        val bestCompleteness = best.value.sumOf { completeness(it).coerceAtLeast(0).toLong() }
        val bestMeasuredAt = best.value.maxOfOrNull(measuredAt) ?: Instant.MIN
        if (selected.drop(1).any { (_, sourceRecords) ->
                sourceRecords.sumOf { completeness(it).coerceAtLeast(0).toLong() } == bestCompleteness &&
                    (sourceRecords.maxOfOrNull(measuredAt) ?: Instant.MIN) == bestMeasuredAt
            }
        ) return null
        return OriginSelection(best.key, best.value)
    }

    fun <T> selectMostCompleteUnambiguousOrigin(
        records: List<T>?,
        origin: (T) -> String,
        completeness: (T) -> Int,
        measuredAt: (T) -> Instant,
        toMeasurement: (T) -> SourceIntervalMeasurement
    ): OriginSelection<T>? {
        val candidates = records.orEmpty()
            .filter { origin(it).isNotBlank() && completeness(it) > 0 }
            .groupBy(origin)
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<T>>> { entry -> entry.value.sumOf { completeness(it).coerceAtLeast(0).toLong() } }
                    .thenByDescending { entry -> entry.value.maxOfOrNull(measuredAt) ?: Instant.MIN }
                    .thenBy { it.key }
            )
        val eligible = candidates.filter { (_, sourceRecords) ->
            SourceIntervalAggregation.assess(sourceRecords.map(toMeasurement)).eligible
        }
        val best = eligible.firstOrNull() ?: return null
        val bestCompleteness = best.value.sumOf { completeness(it).coerceAtLeast(0).toLong() }
        val bestMeasuredAt = best.value.maxOfOrNull(measuredAt) ?: Instant.MIN
        val tiedBest = eligible.drop(1).any { (_, sourceRecords) ->
            sourceRecords.sumOf { completeness(it).coerceAtLeast(0).toLong() } == bestCompleteness &&
                (sourceRecords.maxOfOrNull(measuredAt) ?: Instant.MIN) == bestMeasuredAt
        }
        if (tiedBest) return null
        return OriginSelection(best.key, best.value)
    }

    fun sameOrigin(vararg origins: String?): Boolean =
        origins.isNotEmpty() && origins.all { !it.isNullOrBlank() } && origins.distinct().size == 1

    fun hasPairedSamples(start: Instant, end: Instant, sampleTimes: List<Instant>): Boolean =
        start.isBefore(end) && sampleTimes.count { !it.isBefore(start) && it.isBefore(end) } >= 2
}

internal object HealthInsightCompositeInputPolicy {
    fun canComputeHeartRateReserveEquivalentMinutes(
        hasProfile: Boolean,
        hasRestingHeartRate: Boolean,
        hasHeartRateSamples: Boolean,
        hasKnownHeartRateAndRestingOrigins: Boolean
    ): Boolean = hasProfile && hasRestingHeartRate && hasHeartRateSamples && hasKnownHeartRateAndRestingOrigins
}

internal object ResearchCompositeInputPolicy {
    fun canPair(
        hrvAt: Instant?,
        sleepEndAt: Instant?,
        hrvOrigin: String?,
        sleepOrigin: String?,
        now: Instant
    ): Boolean {
        if (hrvAt == null || sleepEndAt == null || hrvOrigin.isNullOrBlank() || sleepOrigin.isNullOrBlank()) return false
        if (hrvAt.isAfter(now) || sleepEndAt.isAfter(now)) return false
        if (hrvAt.isBefore(now.minus(36, ChronoUnit.HOURS))) return false
        return kotlin.math.abs(Duration.between(hrvAt, sleepEndAt).toHours()) <= 24
    }
}

internal object DailyEnergyEstimate {
    fun startOfLocalDay(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

    fun totalKcal(
        profile: UserProfileStore.Profile,
        activeCaloriesKcal: Double?,
        now: Instant,
        zone: ZoneId
    ): Int? {
        val activeCalories = activeCaloriesKcal?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        if (profile.age !in 13..100 || profile.heightCm !in 100.0..230.0 || profile.weightKg !in 25.0..350.0) return null
        val sexOffset = when (profile.sex) {
            PhysiologyAlgorithms.Sex.MALE -> 5.0
            PhysiologyAlgorithms.Sex.FEMALE -> -161.0
            PhysiologyAlgorithms.Sex.UNSPECIFIED -> return null
        }
        val basalCalories = 10 * profile.weightKg + 6.25 * profile.heightCm - 5 * profile.age + sexOffset
        val dayStart = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val dayEnd = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        val daySeconds = Duration.between(dayStart, dayEnd).seconds
        if (daySeconds <= 0) return null
        val elapsedSeconds = Duration.between(dayStart, now.coerceAtMost(dayEnd)).seconds.coerceIn(0, daySeconds)
        val basalToNow = basalCalories * elapsedSeconds / daySeconds
        return (basalToNow + activeCalories).takeIf { it.isFinite() && it >= 0.0 }?.roundToInt()
    }
}

private fun List<Double>.averageOrNull() = takeIf { it.isNotEmpty() }?.average()

private fun List<Double>.percentile(fraction: Double): Double? {
    if (isEmpty()) return null
    val sorted = sorted()
    return sorted[((sorted.lastIndex * fraction).roundToInt()).coerceIn(0, sorted.lastIndex)]
}
