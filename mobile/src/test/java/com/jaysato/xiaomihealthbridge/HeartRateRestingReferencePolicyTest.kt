package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateRestingReferencePolicyTest {
    @Test
    fun classifiesInclusiveAdultRestingReferenceBoundaries() {
        assertEquals(HeartRateRestingReferencePolicy.Status.WITHIN_REFERENCE, HeartRateRestingReferencePolicy.assess(60.0, 35).status)
        assertEquals(HeartRateRestingReferencePolicy.Status.WITHIN_REFERENCE, HeartRateRestingReferencePolicy.assess(100.0, 35).status)
        assertEquals(HeartRateRestingReferencePolicy.Status.BELOW_REFERENCE, HeartRateRestingReferencePolicy.assess(59.9, 35).status)
        assertEquals(HeartRateRestingReferencePolicy.Status.ABOVE_REFERENCE, HeartRateRestingReferencePolicy.assess(100.1, 35).status)
    }

    @Test
    fun invalidOrMissingValuesRemainUnknown() {
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, 0.0, 221.0).forEach { bpm ->
            assertEquals(HeartRateRestingReferencePolicy.Status.UNKNOWN, HeartRateRestingReferencePolicy.assess(bpm, 35).status)
        }
        assertEquals(HeartRateRestingReferencePolicy.Status.UNKNOWN, HeartRateRestingReferencePolicy.assess(72.0, null).status)
        assertEquals(HeartRateRestingReferencePolicy.Status.UNKNOWN, HeartRateRestingReferencePolicy.assess(72.0, 17).status)
    }
}
