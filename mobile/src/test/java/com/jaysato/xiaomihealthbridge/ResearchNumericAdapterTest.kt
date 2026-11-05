package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResearchNumericAdapterTest {
    @Test fun normalizesFloatIntAndDoubleThroughOnePath() {
        assertEquals(64.5, ResearchNumericAdapter.finiteDouble(64.5f)!!, 0.0001)
        assertEquals(64.0, ResearchNumericAdapter.finiteDouble(64)!!, 0.0001)
        assertEquals(64.5, ResearchNumericAdapter.finiteDouble(64.5)!!, 0.0001)
    }

    @Test fun rejectsNonFiniteAndOutOfRangeValues() {
        assertNull(ResearchNumericAdapter.finiteDouble(Float.NaN))
        assertNull(ResearchNumericAdapter.boundedDouble(500.0, 30.0, 220.0))
    }
}
