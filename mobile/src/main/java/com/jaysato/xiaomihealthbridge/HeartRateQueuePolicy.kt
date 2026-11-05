package com.jaysato.xiaomihealthbridge

internal object HeartRateQueuePolicy {
    const val SAMPLE_INTERVAL_MILLIS = 60_000L

    fun shouldFlushBeforeAppend(queueSize: Int, capacity: Int): Boolean = queueSize >= capacity

    fun fitsCapacity(queueSize: Int, capacity: Int): Boolean = queueSize <= capacity

    fun compact(queue: Map<Long, Long>): Map<Long, Long> = queue.entries
        .filter { it.value in 1..300 }
        .sortedBy { it.key }
        .groupBy { it.key / SAMPLE_INTERVAL_MILLIS }
        .mapValues { (_, samples) ->
            val representative = samples.last()
            representative.key to samples.map { it.value }.average().toLong()
        }
        .values
        .associate { it }

    fun clientRecordId(sampleTimeMillis: Long): String =
        "xiaomi-watch-2-pro-heart-rate-minute-${sampleTimeMillis / SAMPLE_INTERVAL_MILLIS}"

    fun clientRecordVersion(sampleTimeMillis: Long): Long = sampleTimeMillis
}
