package com.jaysato.xiaomihealthbridge

internal object WearSensorSuiteCoveragePolicy {
    fun shouldEmit(kind: String, finalizedKinds: Set<String>): Boolean = kind !in finalizedKinds
}
