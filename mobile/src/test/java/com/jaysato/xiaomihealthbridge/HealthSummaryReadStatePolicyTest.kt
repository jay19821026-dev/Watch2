package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSummaryReadStatePolicyTest {
    @Test fun dataQueryFailureClearsEveryCardWithoutClaimingMissingRecords() {
        val states = HealthSummaryReadStatePolicy.dataQueryFailureCards()
        assertEquals(HealthSummaryReadStatePolicy.permissionQueryFailureCards().keys, states.keys)
        states.values.forEach {
            assertEquals("— —", it.value)
            assertEquals("", it.trend)
            assertTrue(it.detail.contains("資料查詢失敗"))
            assertTrue(!it.detail.contains("無紀錄"))
        }
    }
    @Test
    fun permissionQueryFailureClearsAllSummaryValuesAndTrendsWithExplicitState() {
        val cards = HealthSummaryReadStatePolicy.permissionQueryFailureCards()

        assertEquals(setOf("weight", "steps", "calories", "sleep", "heart_rate", "resting_heart_rate", "oxygen", "distance", "temperature", "elevation", "floors", "hydration"), cards.keys)
        cards.values.forEach { card ->
            assertEquals("— —", card.value)
            assertEquals("", card.trend)
            assertTrue(card.detail.contains("權限清單查詢失敗"))
            assertTrue(card.detail.contains("本次未取得資料"))
            assertTrue(card.detail.contains("前次摘要已清除"))
        }
    }
}
