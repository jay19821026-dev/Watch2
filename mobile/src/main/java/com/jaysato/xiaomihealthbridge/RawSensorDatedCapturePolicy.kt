package com.jaysato.xiaomihealthbridge

/** A dated raw capture is history, never a replacement for a current calibrated reading. */
internal object RawSensorDatedCapturePolicy {
    const val MAX_RAW_PREVIEW_CHARS = 256
    const val MAX_SUMMARY_CHARS = 160

    fun shouldRemember(kind: String, eventCount: Int, sampleTime: Long, timestampSource: String?): Boolean =
        eventCount > 0 && sampleTime > 0L &&
            SensorDisplayState.hasTrustedTimestamp(kind, timestampSource)

    fun historicalTime(currentTime: Long, savedTime: Long): Long? =
        savedTime.takeIf { it > 0L && (currentTime <= 0L || it != currentTime) }

    fun callbackReceiptTime(eventCount: Int, watchCallbackAt: Long, phoneReceivedAt: Long): Pair<Long, String>? {
        if (eventCount <= 0) return null
        return when {
            watchCallbackAt > 0L && phoneReceivedAt > 0L &&
                watchCallbackAt in (phoneReceivedAt - MAX_WATCH_RECEIPT_AGE_MS)..(phoneReceivedAt + MAX_CLOCK_SKEW_MS) ->
                watchCallbackAt to "watch_callback_wall_clock"
            phoneReceivedAt > 0L -> phoneReceivedAt to "phone_receive_wall_clock"
            else -> null
        }
    }

    fun showPreviousCapture(currentEventCount: Int, savedReceiptAt: Long): Boolean =
        currentEventCount <= 0 && savedReceiptAt > 0L

    private const val MAX_WATCH_RECEIPT_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_CLOCK_SKEW_MS = 5L * 60 * 1000
}
