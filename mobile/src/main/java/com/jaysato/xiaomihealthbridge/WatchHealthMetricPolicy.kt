package com.jaysato.xiaomihealthbridge

internal object WatchHealthMetricPolicy {
    private val supportedMetrics = setOf(
        "steps", "distance", "calories", "oxygen", "exercise", "elevation", "floors", "hydration"
    )

    fun isSupported(metric: String): Boolean = metric in supportedMetrics
}
