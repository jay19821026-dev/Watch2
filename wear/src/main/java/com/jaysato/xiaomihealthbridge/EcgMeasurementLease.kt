package com.jaysato.xiaomihealthbridge

object EcgMeasurementLease {
    private var owner: Any? = null

    @Synchronized
    fun acquire(requester: Any): Boolean {
        if (owner != null && owner !== requester) return false
        owner = requester
        return true
    }

    @Synchronized
    fun release(requester: Any) {
        if (owner === requester) owner = null
    }
}
