package com.jaysato.xiaomihealthbridge.engine

object SerializedSensorPayloadPolicy {
    const val MAX_DATA_MAP_BYTES = 90 * 1024
    const val OVERSIZED_REASON = "oversized_serialized_data_map"

    fun rejectionReason(serializedBytes: Int, declaredReason: String? = null): String? = when {
        !declaredReason.isNullOrBlank() && declaredReason != OVERSIZED_REASON -> "unsupported_payload_rejection_marker"
        declaredReason == OVERSIZED_REASON -> OVERSIZED_REASON
        serializedBytes < 0 || serializedBytes > MAX_DATA_MAP_BYTES -> OVERSIZED_REASON
        else -> null
    }
}
