package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConclusionDebugLaunchPolicyTest {
    @Test fun debugBuildCanExplicitlyAutoRevealForDeviceQa() {
        assertTrue(HealthConclusionDebugLaunchPolicy.shouldAutoReveal(true, true))
    }

    @Test fun releaseBuildNeverAutoReveals() {
        assertFalse(HealthConclusionDebugLaunchPolicy.shouldAutoReveal(false, true))
        assertFalse(HealthConclusionDebugLaunchPolicy.shouldAutoReveal(false, false))
    }
}
