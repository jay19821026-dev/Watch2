package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class WearReadinessPresentationTest {
    @Test
    fun trainingLoadUsesArbitraryUnitsAndKeepsMeasuredZero() {
        assertEquals("0 AU", WearReadinessPresentation.trainingLoad(0))
        assertEquals("42 AU", WearReadinessPresentation.trainingLoad(42))
    }

    @Test
    fun missingTrainingLoadExplainsUnavailableData() {
        assertEquals("尚無有效訓練負荷資料", WearReadinessPresentation.trainingLoad(-1))
    }
}
