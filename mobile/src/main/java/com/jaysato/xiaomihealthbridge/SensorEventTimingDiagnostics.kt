package com.jaysato.xiaomihealthbridge

internal data class SensorEventTimingSummary(
    val eventCount: Int,
    val invalidTimestampCount: Int,
    val outOfOrderCount: Int,
    val minIntervalMs: Double?,
    val medianIntervalMs: Double?,
    val maxIntervalMs: Double?
)

internal object SensorEventTimingDiagnostics {
    private const val MAX_CSV_CHARS = 12_000
    private const val MAX_EVENTS = 512
    private const val MAX_TOKEN_CHARS = 20

    fun fromCsv(csv: String): SensorEventTimingSummary? {
        if (csv.isBlank() || csv.length > MAX_CSV_CHARS) return null
        val tokens = csv.split(',')
        if (tokens.size > MAX_EVENTS || tokens.any { it.length !in 1..MAX_TOKEN_CHARS }) return null
        val timestamps = tokens.map { it.toLongOrNull() ?: return null }
        val invalidTimestampCount = timestamps.count { it <= 0L }
        if (timestamps.size < 2) return SensorEventTimingSummary(timestamps.size, invalidTimestampCount, 0, null, null, null)

        var outOfOrder = 0
        val intervalsMs = ArrayList<Double>(timestamps.size - 1)
        timestamps.zipWithNext().forEach { (previous, current) ->
            if (previous <= 0L || current <= 0L) return@forEach
            if (current <= previous) {
                outOfOrder++
            } else {
                intervalsMs += (current - previous) / 1_000_000.0
            }
        }
        val sorted = intervalsMs.sorted()
        val median = when {
            sorted.isEmpty() -> null
            sorted.size % 2 == 1 -> sorted[sorted.size / 2]
            else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
        return SensorEventTimingSummary(
            eventCount = timestamps.size,
            invalidTimestampCount = invalidTimestampCount,
            outOfOrderCount = outOfOrder,
            minIntervalMs = sorted.firstOrNull(),
            medianIntervalMs = median,
            maxIntervalMs = sorted.lastOrNull()
        )
    }
}
