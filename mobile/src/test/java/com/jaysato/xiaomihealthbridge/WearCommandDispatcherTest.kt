package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCommandDispatcherTest {
    @Test
    fun commandFeedbackExplainsDeliveryIsNotMeasurementCompletion() {
        val feedback = WearCommandDispatcher.feedback(WearCommandDispatcher.Outcome.Sent)

        assertTrue(feedback.contains("已送達手錶"))
        assertTrue(feedback.contains("不代表量測已完成"))
    }

    @Test
    fun commandFeedbackCanDescribeVitalsMeasurementDelivery() {
        val message = "量測命令已送達手錶；請在手錶完成量測。命令送達不代表已取得讀值。"

        assertEquals(message, WearCommandDispatcher.feedback(WearCommandDispatcher.Outcome.Sent, message))
    }

    @Test
    fun commandFeedbackExplainsNoWatchAndTransportFailures() {
        val noWatch = WearCommandDispatcher.feedback(WearCommandDispatcher.Outcome.NoUniqueWatch(0))
        val failed = WearCommandDispatcher.feedback(WearCommandDispatcher.Outcome.Failed("timeout"))

        assertTrue(noWatch.contains("找不到已連線手錶"))
        assertTrue(failed.contains("timeout"))
        assertFalse(failed.isBlank())
    }
}
