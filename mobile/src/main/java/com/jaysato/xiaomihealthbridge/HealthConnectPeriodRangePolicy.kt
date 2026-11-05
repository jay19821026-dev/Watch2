package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

internal object HealthConnectPeriodRangePolicy {
    fun localRange(
        start: Instant,
        end: Instant,
        zone: ZoneId
    ): Pair<LocalDateTime, LocalDateTime> {
        require(end.isAfter(start))
        return start.atZone(zone).toLocalDateTime() to end.atZone(zone).toLocalDateTime()
    }
}
