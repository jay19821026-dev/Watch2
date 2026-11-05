package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgRawEventPreviewTest {
    @Test fun preservesEventFieldsWithoutFlatteningChannels() {
        assertEquals(listOf(1f, 3f), EcgRawEventPreview.firstField(listOf("1,2", "3,4")))
    }

    @Test fun rejectsInvalidRowsAndFloatOverflow() {
        assertTrue(EcgRawEventPreview.firstField(listOf("1,2", "NaN,3")).isEmpty())
        assertTrue(EcgRawEventPreview.firstField(listOf("1e100,2")).isEmpty())
    }
}
