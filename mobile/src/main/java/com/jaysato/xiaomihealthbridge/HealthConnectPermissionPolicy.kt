package com.jaysato.xiaomihealthbridge

internal object HealthConnectPermissionPolicy {
    enum class State { GRANTED, DENIED, QUERY_FAILED }

    fun state(permission: String, grantedPermissions: Set<String>?): State = when {
        grantedPermissions == null -> State.QUERY_FAILED
        permission in grantedPermissions -> State.GRANTED
        else -> State.DENIED
    }
}
