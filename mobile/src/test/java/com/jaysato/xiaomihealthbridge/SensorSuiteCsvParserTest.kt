package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorSuiteCsvParserTest {
    @Test
    fun gapMarkerParserPreservesOnlyExplicitNaNAsAnIndexGap() {
        val parsed = SensorSuiteCsvParser.parseFloatsWithGapMarkers("1.0,NaN,2.0")

        assertEquals(3, parsed.size)
        assertEquals(1f, parsed[0])
        assertTrue(parsed[1].isNaN())
        assertEquals(2f, parsed[2])
        assertTrue(SensorSuiteCsvParser.parseFloatsWithGapMarkers("1.0,Infinity,2.0").isEmpty())
    }

    @Test
    fun rejectsMalformedAndNonFiniteTokensWithoutReturningPartialData() {
        assertEquals(listOf(1.0, -2.5, 3.0e2), SensorSuiteCsvParser.parse("1,-2.5,3e2"))
        assertTrue(SensorSuiteCsvParser.parse("1,bad,3").isEmpty())
        assertTrue(SensorSuiteCsvParser.parse("1,NaN,3").isEmpty())
        assertTrue(SensorSuiteCsvParser.parse("1,Infinity,3").isEmpty())
    }

    @Test
    fun parsesBoundedScientificNotationWithoutFloatOverflow() {
        assertEquals(listOf(1.0e-8, -2.5e12), SensorSuiteCsvParser.parse("1e-8,-2.5E12"))
        assertEquals(listOf(1f, -2.5e12f), SensorSuiteCsvParser.parseFloats("1,-2.5e12"))
        assertTrue(SensorSuiteCsvParser.parseFloats("1,3.5e38").isEmpty())
    }

    @Test
    fun rejectsExtremeScientificExponentsAndUnderflowBeforeAcceptingValues() {
        assertTrue(SensorSuiteCsvParser.parse("1E999999999").isEmpty())
        assertTrue(SensorSuiteCsvParser.parse("1E-999999999").isEmpty())
        assertTrue(SensorSuiteCsvParser.parse("1E-400").isEmpty())
        assertEquals(listOf(1E-323), SensorSuiteCsvParser.parse("1E-323"))
    }

    @Test
    fun rejectsOversizedTokensAndValueCountWithoutReturningPartialData() {
        val veryLongToken = "9".repeat(10_000)
        assertTrue(SensorSuiteCsvParser.parse("$veryLongToken,1,2,3", 2).isEmpty())
        assertTrue(SensorSuiteCsvParser.parse("1,2,3", 2).isEmpty())
    }

    @Test
    fun handlesEmptyAndWhitespaceInput() {
        assertTrue(SensorSuiteCsvParser.parse("").isEmpty())
        assertEquals(listOf(4.25, -7.0), SensorSuiteCsvParser.parse(" 4.25 , -7 "))
    }

    @Test
    fun rejectsOversizedPayloadInsteadOfReturningPartialData() {
        val oversized = "1," + "9".repeat(262_144)
        assertTrue(SensorSuiteCsvParser.parse(oversized).isEmpty())
    }

    @Test
    fun parsesBoundedEcgTimelineValuesAndRejectsHugeTokens() {
        assertEquals(listOf(1L, Long.MAX_VALUE), SensorSuiteCsvParser.parseLongs("1,${Long.MAX_VALUE}", 2))
        assertTrue(SensorSuiteCsvParser.parseLongs("9".repeat(100_000), 512).isEmpty())
        assertEquals(listOf(1, 20), SensorSuiteCsvParser.parseInts("1,20", 2))
        assertTrue(SensorSuiteCsvParser.parseInts("1," + "9".repeat(100_000), 512).isEmpty())
    }

    @Test
    fun parsesEcgMetadataWithinExplicitBounds() {
        assertEquals(listOf("a,b", "c,d"), SensorSuiteCsvParser.parseRows("a,b;c,d", 2))
        assertTrue(SensorSuiteCsvParser.parseRows("x".repeat(100_000), 512).isEmpty())
    }

    @Test
    fun parseRowsAllowsBoundedLargeRawEcgEventRowsOnlyWhenExplicitlyRequested() {
        val row = "1.0,".repeat(250)
        assertTrue(SensorSuiteCsvParser.parseRows(row, 1).isEmpty())
        assertEquals(listOf(row), SensorSuiteCsvParser.parseRows(
            row, 1, SensorSuiteCsvParser.MAX_ECG_EVENT_ROW_CHARS
        ))
        assertTrue(SensorSuiteCsvParser.parseRows(
            "x".repeat(SensorSuiteCsvParser.MAX_ECG_EVENT_ROW_CHARS + 1),
            1,
            SensorSuiteCsvParser.MAX_ECG_EVENT_ROW_CHARS
        ).isEmpty())
    }

    @Test
    fun enforcesTokenAndWholeInputBoundaries() {
        val longestAcceptedFiniteToken = "1." + "0".repeat(46)
        assertEquals(listOf(1.0), SensorSuiteCsvParser.parse(longestAcceptedFiniteToken))
        assertTrue(SensorSuiteCsvParser.parse(longestAcceptedFiniteToken + "0").isEmpty())
        assertTrue(SensorSuiteCsvParser.parse("1".repeat(262_145)).isEmpty())
    }
}
