package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Spo2ChannelSelectionPolicyTest {
    @Test fun explicitChannelDoesNotSilentlySwitchToAnotherSource() {
        assertNull(Spo2ChannelSelectionPolicy.select("mi_spo2", setOf("spo2_raw"), emptyMap()))
    }

    @Test fun noExplicitChoiceSelectsMostRecentAvailableSource() {
        assertEquals(
            "mi_spo2",
            Spo2ChannelSelectionPolicy.select(
                null,
                setOf("spo2_raw", "mi_spo2"),
                mapOf("spo2_raw" to 100L, "mi_spo2" to 200L)
            )
        )
    }

    @Test fun storedRawChannelIsSelectedForSummaryWhenItIsTheOnlyAvailableSource() {
        assertEquals(
            "spo2_raw",
            Spo2ChannelSelectionPolicy.selectForStoredData(
                null,
                mapOf("spo2_raw" to 32, "mi_spo2" to 0),
                mapOf("spo2_raw" to 100L, "mi_spo2" to 0L)
            )
        )
    }

    @Test fun storedSelectionHonorsExplicitSourceOnlyWhenThatSourceHasData() {
        assertNull(
            Spo2ChannelSelectionPolicy.selectForStoredData(
                "mi_spo2",
                mapOf("spo2_raw" to 16, "mi_spo2" to 0),
                mapOf("spo2_raw" to 100L, "mi_spo2" to 0L)
            )
        )
    }

    @Test fun emptyExplicitChannelCanExposeOtherStoredSourceWithoutSwitchingSilently() {
        assertEquals(
            listOf("spo2_raw"),
            Spo2ChannelSelectionPolicy.otherAvailableKinds("mi_spo2", mapOf("spo2_raw" to 8, "mi_spo2" to 0))
        )
    }
}
