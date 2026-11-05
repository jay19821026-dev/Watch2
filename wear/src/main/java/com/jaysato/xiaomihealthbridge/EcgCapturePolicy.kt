package com.jaysato.xiaomihealthbridge

internal object EcgCapturePolicy {
    const val NO_CALLBACK_TIMEOUT_MILLIS = 8_000L
    const val MAX_CAPTURE_MILLIS = 60_000L
    const val SIGNAL_QUIET_TIMEOUT_MILLIS = 3_500L
    const val MAX_AUTO_SEGMENTS = 3

    fun canAutoContinue(stopReason: String?, completedSegments: Int): Boolean =
        stopReason == "time_limit_reached" && completedSegments < MAX_AUTO_SEGMENTS

    fun idleStopReason(sampleCount: Int, idleMillis: Long): String? = when {
        sampleCount <= 0 && idleMillis >= NO_CALLBACK_TIMEOUT_MILLIS -> "no_callback_timeout"
        sampleCount > 0 && idleMillis >= SIGNAL_QUIET_TIMEOUT_MILLIS -> "signal_stalled"
        else -> null
    }
}
