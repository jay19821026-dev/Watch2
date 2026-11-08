package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSensorDatedCapturePolicyTest {
    @Test fun validCaptureIsRememberedThenBadClockAttemptLeavesItAsHistory() {
        assertTrue(RawSensorDatedCapturePolicy.shouldRemember(
            "ecg", 216, 10_000L, "android_sensor_event_elapsed_realtime"
        ))
        assertFalse(RawSensorDatedCapturePolicy.shouldRemember(
            "ecg", 2971, 0L, "sensor_event_timestamp_ahead_of_capture"
        ))
        assertEquals(10_000L, RawSensorDatedCapturePolicy.historicalTime(0L, 10_000L))
    }

    @Test fun emptyOrUntrustedCaptureCannotReplaceDatedHistory() {
        for (kind in listOf("ppg", "ecg", "bioz", "body_temperature_raw", "thermometer")) {
            assertFalse(RawSensorDatedCapturePolicy.shouldRemember(
                kind, 0, 12_000L, "android_sensor_event_elapsed_realtime"
            ))
            assertFalse(RawSensorDatedCapturePolicy.shouldRemember(
                kind, 2, 12_000L, "sensor_event_timestamp_missing"
            ))
        }
        assertNull(RawSensorDatedCapturePolicy.historicalTime(0L, 0L))
        assertNull(RawSensorDatedCapturePolicy.historicalTime(10_000L, 10_000L))
    }

    @Test fun missingSensorClockUsesLabeledReceiptAndEmptyRetryKeepsPreviousPreview() {
        val receipt = RawSensorDatedCapturePolicy.callbackReceiptTime(216, 12_000L, 13_000L)
        assertEquals(12_000L to "watch_callback_wall_clock", receipt)
        assertTrue(RawSensorDatedCapturePolicy.showPreviousCapture(0, receipt!!.first))
        assertFalse(RawSensorDatedCapturePolicy.showPreviousCapture(216, receipt.first))
        assertNull(RawSensorDatedCapturePolicy.callbackReceiptTime(0, 14_000L, 15_000L))
        assertEquals(15_000L to "phone_receive_wall_clock",
            RawSensorDatedCapturePolicy.callbackReceiptTime(2, 0L, 15_000L))
        assertEquals(256, "1".repeat(4096).take(RawSensorDatedCapturePolicy.MAX_RAW_PREVIEW_CHARS).length)
    }

    @Test fun impossibleWatchWallClockFallsBackToPhoneReceipt() {
        val phone = 1_800_000_000_000L
        assertEquals(phone to "phone_receive_wall_clock",
            RawSensorDatedCapturePolicy.callbackReceiptTime(2, phone + 600_000L, phone))
        assertEquals(phone to "phone_receive_wall_clock",
            RawSensorDatedCapturePolicy.callbackReceiptTime(2, phone - 8L * 24 * 60 * 60 * 1000, phone))
    }
}
