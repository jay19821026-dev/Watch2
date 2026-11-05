package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugStatusResponseValidationTest {
    @Test
    fun acceptsCorrelatedStatusEnvelope() {
        assertNull(DebugStatusResponseValidation.rejectionReason("12345678-1234-1234-1234-123456789abc", "ok", 1L))
    }

    @Test
    fun reportsBoundedReasonForInvalidEnvelopeFields() {
        assertEquals("invalid_request_id", DebugStatusResponseValidation.rejectionReason("bad-id", "ok", 1L))
        assertEquals("missing_status", DebugStatusResponseValidation.rejectionReason("12345678-1234-1234-1234-123456789abc", " ", 1L))
        assertEquals("invalid_watch_timestamp", DebugStatusResponseValidation.rejectionReason("12345678-1234-1234-1234-123456789abc", "ok", 0L))
    }

    @Test
    fun removesWristHeartRateFromStoredDebugStatus() {
        assertEquals(
            "控制通道正常；生理數值 [隱去]；背景同步正常",
            DebugStatusResponseValidation.sanitizeStatusForStorage("控制通道正常；WHS 心率 86 bpm；背景同步正常")
        )
        assertEquals(
            "生理數值 [隱去]；WHS 心率權限=已授權",
            DebugStatusResponseValidation.sanitizeStatusForStorage("血氧：97%；WHS 心率權限=已授權")
        )
        assertEquals(
            "生理數值 [隱去]；生理數值 [隱去]",
            DebugStatusResponseValidation.sanitizeStatusForStorage("SpO₂: 97%；HRV RMSSD 42 ms")
        )
    }
}
