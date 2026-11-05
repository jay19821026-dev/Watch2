package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectPermissionPolicyTest {
    @Test
    fun distinguishesGrantedDeniedAndUnqueryablePermissionStates() {
        assertEquals(
            HealthConnectPermissionPolicy.State.GRANTED,
            HealthConnectPermissionPolicy.state("read", setOf("read"))
        )
        assertEquals(
            HealthConnectPermissionPolicy.State.DENIED,
            HealthConnectPermissionPolicy.state("read", emptySet())
        )
        assertEquals(
            HealthConnectPermissionPolicy.State.QUERY_FAILED,
            HealthConnectPermissionPolicy.state("read", null)
        )
    }
}
