package com.jaysato.xiaomihealthbridge

internal object ControlStatusOrdering {
    fun shouldAccept(incomingTimestamp: Long, previousTimestamp: Long): Boolean =
        incomingTimestamp > previousTimestamp && incomingTimestamp > 0L
}
