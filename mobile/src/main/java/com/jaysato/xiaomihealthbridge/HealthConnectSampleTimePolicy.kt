package com.jaysato.xiaomihealthbridge

import java.time.Instant

internal object HealthConnectSampleTimePolicy {
    fun isWithinHalfOpenRange(sampleTime: Instant, start: Instant, end: Instant): Boolean =
        !sampleTime.isBefore(start) && sampleTime.isBefore(end)
}
