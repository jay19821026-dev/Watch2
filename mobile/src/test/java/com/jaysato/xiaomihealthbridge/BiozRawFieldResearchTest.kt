package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiozRawFieldResearchTest {
    @Test
    fun keepsBiozPacketFieldsAsSeparateEventIndexedSeries() {
        val result = BiozRawFieldResearch.analyze("10,100;20,200;30,300;40,400", "watch_bioz")

        assertNotNull(result)
        result!!
        assertEquals(4, result.eventCount)
        assertEquals(8, result.expectedValueCount)
        assertEquals(8, result.finiteValueCount)
        assertEquals(2, result.fields.size)
        assertEquals(listOf(0.0, 1.0 / 3.0, 2.0 / 3.0, 1.0), result.fields[0].relativeAmplitude.map { it!! })
        assertEquals(result.fields[0].eventCount, result.fields[1].eventCount)
        assertNotNull(result.evidenceCompleteness.score)
    }

    @Test
    fun reportsPerFieldRelativeChangeWithoutPhysicalImpedanceUnits() {
        val result = BiozRawFieldResearch.analyze(
            "1,8;2,7;3,6;4,5;5,4;6,3;7,2;8,1", "watch_bioz"
        )!!

        assertTrue(result.fields.all { it.firstToLastQuartileChangePercent != null })
        assertTrue(result.fields.all { it.relativeDispersionPercent != null })
        assertTrue(result.fields.all { it.relativeAmplitude.all { point -> point != null } })
        assertFalse(result.fields.toString().contains("ohm"))
    }

    @Test
    fun rejectsMalformedOrInconsistentRowsInsteadOfTruncatingToAValidPrefix() {
        assertNull(BiozRawFieldResearch.analyze("1,2;3", "watch_bioz"))
        assertNull(BiozRawFieldResearch.analyze("Infinity,2;3,4", "watch_bioz"))
        assertNull(BiozRawFieldResearch.analyze("1,,3;4,5,", "watch_bioz"))
    }

    @Test
    fun preservesNanAsAnEventGapAndAnalyzesOtherBiozSamples() {
        val result = BiozRawFieldResearch.analyze("1,8;NaN,7;3,6", "watch_bioz")!!

        assertEquals(3, result.eventCount)
        assertEquals(6, result.expectedValueCount)
        assertEquals(5, result.finiteValueCount)
        assertNull(result.fields[0].relativeAmplitude[1])
        assertTrue(result.fields[1].relativeAmplitude.all { it != null })
    }
}
