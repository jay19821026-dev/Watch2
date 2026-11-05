package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSnapshotCodecTest {
    @Test fun mixedTypesAndRawFloatBitsSurvive() {
        val nan = Float.fromBits(0x7fc00001)
        val source = mapOf("text" to "原始訊號", "int" to Int.MIN_VALUE,
            "long" to Long.MAX_VALUE, "float" to nan, "boolean" to true,
            "set" to setOf("ECG", "PPG"))
        val decoded = RawSnapshotCodec.decode(RawSnapshotCodec.encode(source))
        source.filterKeys { it != "float" }.forEach { (key, value) -> assertEquals(value, decoded[key]) }
        assertEquals(nan.toRawBits(), (decoded["float"] as Float).toRawBits())
    }

    @Test fun encodingIsDeterministic() {
        assertTrue(RawSnapshotCodec.encode(mapOf("b" to 2, "a" to 1)).contentEquals(
            RawSnapshotCodec.encode(mapOf("a" to 1, "b" to 2))))
    }

    @Test fun rejectsTruncationAndTrailingBytes() {
        val encoded = RawSnapshotCodec.encode(mapOf("raw" to "123"))
        assertTrue(runCatching { RawSnapshotCodec.decode(encoded.dropLast(1).toByteArray()) }.isFailure)
        assertTrue(runCatching { RawSnapshotCodec.decode(encoded + byteArrayOf(0)) }.isFailure)
    }

    @Test fun rejectsUnsupportedValuesAndOversizedText() {
        listOf(mapOf("raw" to null), mapOf("raw" to 1.0),
            mapOf("raw" to setOf(1)), mapOf("raw" to "a".repeat(1048577))).forEach {
            assertTrue(runCatching { RawSnapshotCodec.encode(it) }.isFailure)
        }
    }

    @Test fun emptySnapshotRoundTrips() {
        assertTrue(RawSnapshotCodec.decode(RawSnapshotCodec.encode(emptyMap<String, Any>())).isEmpty())
    }

    @Test fun rejectsMalformedUnicodeRatherThanChangingEvidence() {
        assertTrue(runCatching { RawSnapshotCodec.encode(mapOf("raw" to "\uD800")) }.isFailure)
    }

    @Test fun rejectsMalformedUtf8Payload() {
        val encoded = RawSnapshotCodec.encode(mapOf("raw" to "x"))
        encoded[encoded.lastIndex] = 0xff.toByte()
        assertTrue(runCatching { RawSnapshotCodec.decode(encoded) }.isFailure)
    }

    @Test fun rejectsDuplicateKeysInEncodedInput() {
        val encoded = RawSnapshotCodec.encode(mapOf("raw" to 1))
        val header = encoded.copyOfRange(0, 8)
        header[7] = 2
        val entry = encoded.copyOfRange(8, encoded.size)
        assertTrue(runCatching { RawSnapshotCodec.decode(header + entry + entry) }.isFailure)
    }
}
