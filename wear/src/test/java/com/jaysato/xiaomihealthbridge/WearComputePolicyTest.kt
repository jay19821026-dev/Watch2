package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class WearComputePolicyTest {
    @Test fun higherCoreCountUsesResponsivePreviewWhenCoolAndCharged() {
        assertEquals(400L, WearComputePolicy.ecgPreviewIntervalMillis(4, 0, 80))
    }

    @Test fun severeThermalStatusSlowsPreviewRegardlessOfCoreCount() {
        assertEquals(1_500L, WearComputePolicy.ecgPreviewIntervalMillis(8, 3, 80))
    }

    @Test fun lowBatteryOrLowCoreCountReducesPreviewWork() {
        assertEquals(1_000L, WearComputePolicy.ecgPreviewIntervalMillis(4, 0, 15))
        assertEquals(1_000L, WearComputePolicy.ecgPreviewIntervalMillis(2, 0, 80))
    }

    @Test fun intermediateDeviceUsesBalancedPreviewCadence() {
        assertEquals(700L, WearComputePolicy.ecgPreviewIntervalMillis(4, 0, 25))
    }

    @Test fun unavailableThermalOrBatteryEvidenceAvoidsFastestPreview() {
        assertEquals(1_000L, WearComputePolicy.ecgPreviewIntervalMillis(8, null, 90))
        assertEquals(700L, WearComputePolicy.ecgPreviewIntervalMillis(8, 0, null))
    }
}
