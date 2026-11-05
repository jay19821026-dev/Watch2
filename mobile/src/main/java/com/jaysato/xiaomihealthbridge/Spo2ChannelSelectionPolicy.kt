package com.jaysato.xiaomihealthbridge

internal object Spo2ChannelSelectionPolicy {
    val KINDS = listOf("spo2_raw", "mi_spo2")

    fun select(
        preferredKind: String?,
        availableKinds: Set<String>,
        measuredAtByKind: Map<String, Long>
    ): String? {
        if (preferredKind in KINDS) return preferredKind.takeIf { it in availableKinds }
        return KINDS.asSequence()
            .filter { it in availableKinds }
            .maxByOrNull { measuredAtByKind[it] ?: 0L }
    }

    fun selectForStoredData(
        preferredKind: String?,
        receivedCounts: Map<String, Int>,
        measuredAtByKind: Map<String, Long>
    ): String? = select(
        preferredKind,
        KINDS.filterTo(mutableSetOf()) { (receivedCounts[it] ?: 0) > 0 },
        measuredAtByKind
    )

    fun otherAvailableKinds(selectedKind: String?, receivedCounts: Map<String, Int>): List<String> =
        KINDS.filter { it != selectedKind && (receivedCounts[it] ?: 0) > 0 }
}
