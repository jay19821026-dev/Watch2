package com.jaysato.xiaomihealthbridge

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

internal class SnapshotSequence(private val clock: () -> Long) {
    private val lastValue = AtomicLong(0L)

    fun next(): Long {
        while (true) {
            val previous = lastValue.get()
            val next = maxOf(clock(), previous + 1L)
            if (lastValue.compareAndSet(previous, next)) return next
        }
    }

    companion object {
        private val process = SnapshotSequence(SystemClock::elapsedRealtimeNanos)
        fun next(): Long = process.next()
    }
}
