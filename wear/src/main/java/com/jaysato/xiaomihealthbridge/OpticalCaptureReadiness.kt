package com.jaysato.xiaomihealthbridge

internal object OpticalCaptureReadiness {
    fun hasCapturedPayload(eventCount: Int, waveform: List<Float>, elapsedMillis: Long): Boolean =
        elapsedMillis >= MIN_CAPTURE_DURATION_MILLIS &&
            eventCount >= MIN_CAPTURE_EVENTS &&
            waveform.any { it.isFinite() }

    private const val MIN_CAPTURE_DURATION_MILLIS = 5_000L
    private const val MIN_CAPTURE_EVENTS = 8
}
