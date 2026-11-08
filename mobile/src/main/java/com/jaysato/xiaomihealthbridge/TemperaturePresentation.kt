package com.jaysato.xiaomihealthbridge

internal object TemperaturePresentation {
    const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
    data class Channel(
        val source: String,
        val value: Double?,
        val measuredAt: Long,
        val timestampSource: String?,
        val status: String,
        val count: Int,
        val waveform: List<Float>,
        val unitStatus: String = "unverified",
        val aggregationMethod: String = "unreported",
        val aggregationSampleCount: Int = 0
    )

    fun select(channels: List<Channel>, now: Long): Channel? {
        val available = channels.mapNotNull { channel ->
            val value = channel.value?.takeIf { it.isFinite() }
                ?: channel.waveform.lastOrNull { it.isFinite() }?.toDouble()
            value?.let { channel.copy(value = it) }
        }
        return available.filter { freshness(it, now) == "fresh" }
            .maxByOrNull(Channel::measuredAt)
            ?: available.filter { freshness(it, now) == "stale" }
                .maxByOrNull(Channel::measuredAt)
            ?: available.firstOrNull()
    }

    fun freshness(channel: Channel, now: Long): String = when {
        channel.measuredAt <= 0L -> "measurement_time_missing"
        channel.measuredAt > now -> "future_timestamp"
        !HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(channel.timestampSource) -> "timestamp_untrusted"
        HealthAnalysisFreshnessPolicy.isFreshMeasurement(
            channel.measuredAt, channel.timestampSource, now, MAX_AGE_MILLIS
        ) -> "fresh"
        else -> "stale"
    }

    fun metadata(channel: Channel, now: Long): String =
        "來源=${channel.source} · timestamp_source=${channel.timestampSource ?: "unknown"}" +
            " · age_ms=${if (channel.measuredAt > 0L) (now - channel.measuredAt).toString() else "unknown"}" +
        " · ${freshness(channel, now)} · status=${channel.status}" +
            " · aggregation=${channel.aggregationMethod} (n=${channel.aggregationSampleCount.coerceAtLeast(0)})" +
            " · raw / 單位未驗證 · unit_status=${channel.unitStatus} · 無校準溫度；不代表核心體溫"
}
