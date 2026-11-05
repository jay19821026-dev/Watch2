package com.jaysato.xiaomihealthbridge

internal object HeartRateDailySummaryPolicy {
    data class Sample(val timeMillis: Long, val bpm: Double, val source: String)
    data class Range(val minimumBpm: Double, val maximumBpm: Double, val sampleCount: Int, val sourceCount: Int)

    class Accumulator {
        private var minimum = Double.POSITIVE_INFINITY
        private var maximum = Double.NEGATIVE_INFINITY
        private var sampleCount = 0
        private var latest: Sample? = null
        private val sources = mutableSetOf<String>()

        fun add(sample: Sample) {
            if (sample.timeMillis <= 0L || !sample.bpm.isFinite() || sample.bpm !in 30.0..220.0 || sample.source.isBlank()) return
            minimum = minOf(minimum, sample.bpm)
            maximum = maxOf(maximum, sample.bpm)
            sampleCount++
            if (sample.timeMillis > (latest?.timeMillis ?: Long.MIN_VALUE)) latest = sample
            sources += sample.source
        }

        fun result(): Range? = if (sampleCount == 0) null else Range(minimum, maximum, sampleCount, sources.size)
        fun latestSample(): Sample? = latest
    }

    fun summarize(samples: List<Sample>): Range? = Accumulator().apply { samples.forEach(::add) }.result()

    fun summarizeWindow(samples: List<Sample>, startInclusiveMillis: Long, endInclusiveMillis: Long): Range? {
        if (startInclusiveMillis > endInclusiveMillis) return null
        return Accumulator().apply {
            samples.asSequence()
                .filter { it.timeMillis in startInclusiveMillis..endInclusiveMillis }
                .forEach(::add)
        }.result()
    }

    fun latestResting(samples: List<Sample>): Sample? = samples.asSequence()
        .filter { it.timeMillis > 0L && it.bpm.isFinite() && it.bpm in 30.0..220.0 && it.source.isNotBlank() }
        .maxByOrNull { it.timeMillis }
}
