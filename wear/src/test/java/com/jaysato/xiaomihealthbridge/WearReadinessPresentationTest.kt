package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class WearReadinessPresentationTest {
    @Test
    fun trainingLoadExplainsAnEstimateWithoutShowingArbitraryUnits() {
        assertEquals("已取得運動負荷估算", WearReadinessPresentation.trainingLoad(0))
        assertEquals("已取得運動負荷估算", WearReadinessPresentation.trainingLoad(42))
    }

    @Test
    fun missingTrainingLoadExplainsUnavailableData() {
        assertEquals("尚無運動負荷紀錄", WearReadinessPresentation.trainingLoad(-1))
    }
}
