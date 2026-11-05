package com.jaysato.xiaomihealthbridge

internal object WorkoutIntervalMetricPolicy {
    data class Interval(val startMillis: Long, val endMillis: Long)

    enum class Rejection { UNSUPPORTED_METRIC, INVALID_VALUE, OUT_OF_RANGE, INVALID_INTERVAL, OUTSIDE_SESSION, DUPLICATE, OUT_OF_ORDER }

    data class Decision(val accepted: Boolean, val rejection: Rejection? = null)

    fun evaluate(
        metric: String,
        value: Double,
        startMillis: Long,
        endMillis: Long,
        workoutStartMillis: Long,
        nowMillis: Long,
        previous: Interval?
    ): Decision {
        val maximum = when (metric) {
            "steps" -> 100_000.0
            "distance" -> 100_000.0
            "calories" -> 10_000.0
            else -> return Decision(false, Rejection.UNSUPPORTED_METRIC)
        }
        if (!value.isFinite() || value <= 0.0) return Decision(false, Rejection.INVALID_VALUE)
        if (value > maximum) return Decision(false, Rejection.OUT_OF_RANGE)
        if (workoutStartMillis <= 0L || startMillis < workoutStartMillis || endMillis <= startMillis ||
            endMillis - startMillis > MAX_INTERVAL_MILLIS || endMillis > nowMillis + FUTURE_TOLERANCE_MILLIS
        ) return Decision(false, Rejection.INVALID_INTERVAL)
        if (previous != null) {
            if (startMillis == previous.startMillis && endMillis == previous.endMillis) {
                return Decision(false, Rejection.DUPLICATE)
            }
            if (startMillis < previous.endMillis || endMillis <= previous.endMillis) {
                return Decision(false, Rejection.OUT_OF_ORDER)
            }
        }
        return Decision(true)
    }

    const val MAX_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
    const val FUTURE_TOLERANCE_MILLIS = 60_000L
}

internal object WorkoutMetricDeliveryPolicy {
    const val MAX_ATTEMPTS = 4
    const val MAX_PENDING_PER_METRIC = 128

    fun retryDelayAfterFailure(attempt: Int): Long? = when (attempt) {
        1 -> 500L
        2 -> 1_000L
        3 -> 2_000L
        else -> null
    }

    fun intervalPath(metric: String, startMillis: Long, endMillis: Long): String =
        "/health/$metric/$startMillis-$endMillis"

    fun shouldProcessMetrics(active: Boolean, ending: Boolean, endedUpdate: Boolean): Boolean =
        active || ending && endedUpdate
}
