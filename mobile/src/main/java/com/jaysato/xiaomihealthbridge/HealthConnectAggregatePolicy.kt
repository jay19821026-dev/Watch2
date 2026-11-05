package com.jaysato.xiaomihealthbridge

internal object HealthConnectAggregatePolicy {
    const val RULE_VERSION = "HC_ACTIVITY_SLEEP_DEDUP_HYDRATION_SOURCE_V3"

    enum class PlatformTotalRejection { MISSING_TOTAL, INVALID_TOTAL, MISSING_SOURCE }

    fun shouldQuery(isInteractive: Boolean): Boolean = isInteractive

    fun hasSingleOrigin(origins: Collection<String>): Boolean =
        origins.isNotEmpty() && origins.all { it.isNotBlank() } && origins.distinct().size == 1

    fun singleOriginOrNull(origins: Collection<String>): String? =
        origins.distinct().singleOrNull()?.takeIf { it.isNotBlank() }

    fun <T> recordsForOrigin(records: List<T>, origin: String?, recordOrigin: (T) -> String): List<T> =
        origin?.takeIf { it.isNotBlank() }?.let { selected -> records.filter { recordOrigin(it) == selected } }.orEmpty()

    fun acceptsAggregate(
        aggregate: Double?,
        origins: Collection<String>,
        validatedRecordTotal: Double?,
        aggregateOrigins: Collection<String>
    ): Boolean {
        if (aggregate == null || validatedRecordTotal == null || !aggregate.isFinite() || aggregate < 0.0) return false
        if (!validatedRecordTotal.isFinite() || validatedRecordTotal < 0.0) return false
        val expectedOrigin = singleOriginOrNull(origins) ?: return false
        if (singleOriginOrNull(aggregateOrigins) != expectedOrigin) return false
        val tolerance = maxOf(1e-6, kotlin.math.abs(validatedRecordTotal) * 1e-9)
        return kotlin.math.abs(aggregate - validatedRecordTotal) <= tolerance
    }

    fun acceptsSingleOriginRecordAggregate(
        aggregate: Double?,
        aggregateOrigins: Collection<String>,
        recordTotal: Double?,
        recordOrigins: Collection<String>
    ): Boolean = acceptsAggregate(aggregate, aggregateOrigins, recordTotal, recordOrigins)

    fun acceptsPlatformTotal(total: Double?, origins: Collection<String>): Boolean =
        total != null && total.isFinite() && total >= 0.0 && origins.any(String::isNotBlank)

    fun platformTotalRejection(total: Double?, origins: Collection<String>): PlatformTotalRejection? = when {
        total == null -> PlatformTotalRejection.MISSING_TOTAL
        !total.isFinite() || total < 0.0 -> PlatformTotalRejection.INVALID_TOTAL
        origins.none(String::isNotBlank) -> PlatformTotalRejection.MISSING_SOURCE
        else -> null
    }

    fun acceptsDeduplicatedActivityTotal(total: Double?, origins: Collection<String>): Boolean =
        acceptsPlatformTotal(total, origins)

    fun <T> completeIntervalsWithinRange(
        records: List<T>,
        start: java.time.Instant,
        end: java.time.Instant,
        recordStart: (T) -> java.time.Instant,
        recordEnd: (T) -> java.time.Instant
    ): List<T> {
        require(end.isAfter(start))
        return records.filter { record ->
            val intervalStart = recordStart(record)
            val intervalEnd = recordEnd(record)
            intervalEnd.isAfter(intervalStart) && !intervalStart.isBefore(start) && !intervalEnd.isAfter(end)
        }
    }
}
