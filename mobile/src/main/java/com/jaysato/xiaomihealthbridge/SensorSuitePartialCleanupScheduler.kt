package com.jaysato.xiaomihealthbridge

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal class SensorSuitePartialCleanupScheduler(
    private val enqueue: (Runnable) -> Unit,
    private val cleanup: (File) -> Unit
) {
    private val pending = AtomicBoolean(false)

    fun request(directory: File) {
        if (!pending.compareAndSet(false, true)) return
        try {
            enqueue(Runnable {
                try {
                    cleanup(directory)
                } finally {
                    pending.set(false)
                }
            })
        } catch (failure: Exception) {
            pending.set(false)
            throw failure
        }
    }
}
