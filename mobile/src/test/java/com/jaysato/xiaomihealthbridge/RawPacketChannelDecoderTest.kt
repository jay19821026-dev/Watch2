package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawPacketChannelDecoderTest {
    @Test
    fun packetColumnsDoNotBecomeSuccessiveWaveformSamples() {
        val result = RawPacketChannelDecoder.decode("0,100,1;0,110,2")
        assertEquals(2, result.eventCount)
        assertEquals(listOf(0.0, 0.0), result.channels[0])
        assertEquals(listOf(100.0, 110.0), result.channels[1])
    }

    @Test
    fun inconsistentPacketsAreNotSilentlyTruncated() {
        val result = RawPacketChannelDecoder.decode("1,2;3")
        assertTrue(result.channels.isEmpty())
        assertEquals(0, result.eventCount)
        assertEquals(2, result.receivedEventCount)
    }

    @Test
    fun explicitNanPreservesAnEventIndexedGap() {
        val result = RawPacketChannelDecoder.decode("1,2;NaN,3;4,5")

        assertEquals(3, result.receivedEventCount)
        assertEquals(3, result.eventCount)
        assertTrue(result.channels[0][1].isNaN())
        assertEquals(listOf(2.0, 3.0, 5.0), result.channels[1])
    }
}
