package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class HearingExposureTest {
    @Test fun nioshThreeDbExchangeRate() {
        assertEquals(480.0, HearingExposure.allowedMinutesAt(85.0)!!, 0.001)
        assertEquals(240.0, HearingExposure.allowedMinutesAt(88.0)!!, 0.001)
        assertEquals(60.0, HearingExposure.allowedMinutesAt(94.0)!!, 0.001)
    }
}
