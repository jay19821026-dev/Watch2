package com.jaysato.xiaomihealthbridge

internal object RawDiagnosticExportPreview {
    data class Channel(
        val label: String,
        val measuredAt: Long,
        val sampleCount: Int,
        val latestPointCount: Int,
        val waveformPointCount: Int,
        val sourceWaveformPointCount: Int
    )

    data class Summary(
        val channels: List<String>,
        val measuredChannelCount: Int,
        val storedSampleCount: Long,
        val previewPointCount: Long,
        val waveformPointCount: Long,
        val sourceWaveformPointCount: Long,
        val earliestMeasurementAt: Long?,
        val latestMeasurementAt: Long?
    )

    fun summarize(channels: List<Channel>): Summary {
        val included = channels.filter {
            it.sampleCount > 0 || it.latestPointCount > 0 || it.waveformPointCount > 0 ||
                it.sourceWaveformPointCount > 0 || it.measuredAt > 0
        }
        val timestamps = included.map { it.measuredAt }.filter { it > 0 }
        return Summary(
            channels = included.map { it.label },
            measuredChannelCount = included.size,
            storedSampleCount = included.sumOf { it.sampleCount.coerceAtLeast(0).toLong() },
            previewPointCount = included.sumOf { it.latestPointCount.coerceAtLeast(0).toLong() },
            waveformPointCount = included.sumOf { it.waveformPointCount.coerceAtLeast(0).toLong() },
            sourceWaveformPointCount = included.sumOf { it.sourceWaveformPointCount.coerceAtLeast(0).toLong() },
            earliestMeasurementAt = timestamps.minOrNull(),
            latestMeasurementAt = timestamps.maxOrNull()
        )
    }
}
