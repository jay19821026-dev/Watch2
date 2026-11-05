package com.jaysato.xiaomihealthbridge

import java.util.concurrent.atomic.AtomicBoolean

internal class DebugStatusReplyGate {
    private val primaryClaimed = AtomicBoolean(false)
    private val fallbackClaimed = AtomicBoolean(false)

    fun claimPrimary(): Boolean = primaryClaimed.compareAndSet(false, true)

    fun claimFailure(alreadyClaimed: Boolean): Boolean =
        if (alreadyClaimed) {
            primaryClaimed.get() && fallbackClaimed.compareAndSet(false, true)
        } else {
            claimPrimary()
        }
}
