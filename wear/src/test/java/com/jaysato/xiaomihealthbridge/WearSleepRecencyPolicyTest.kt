package com.jaysato.xiaomihealthbridge

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class WearSleepRecencyPolicyTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val now = LocalDate.of(2026, 11, 8).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun distinguishesTodayYesterdayAndUnknownTime() {
        val todayEnd = LocalDate.of(2026, 11, 8).atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        val yesterdayEnd = LocalDate.of(2026, 11, 7).atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("今天醒來的紀錄", WearSleepRecencyPolicy.label(todayEnd, now, zone))
        assertEquals("昨天醒來的紀錄", WearSleepRecencyPolicy.label(yesterdayEnd, now, zone))
        assertEquals("紀錄時間未確認", WearSleepRecencyPolicy.label(0, now, zone))
    }
}
