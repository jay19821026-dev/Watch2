package com.jaysato.xiaomihealthbridge

internal object WearDataEventDispatchPolicy {
    data class Result(val processed: Int, val failed: Int, val skipped: Int)

    fun <T> dispatch(
        events: Iterable<T>,
        shouldProcess: (T) -> Boolean,
        process: (T) -> Unit,
        onFailure: (Exception) -> Unit
    ): Result {
        var processed = 0
        var failed = 0
        var skipped = 0
        for (event in events) {
            try {
                if (!shouldProcess(event)) {
                    skipped++
                    continue
                }
                process(event)
                processed++
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failed++
                onFailure(error)
            }
        }
        return Result(processed, failed, skipped)
    }
}
