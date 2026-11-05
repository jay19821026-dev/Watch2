package com.jaysato.xiaomihealthbridge

internal object RawEvidenceFlags {
    fun finiteValueFlag(count: Int?): String = when {
        count == null -> "FINITE_VALUE_COUNT_UNKNOWN"
        count > 0 -> "FINITE_VALUES_RECEIVED"
        else -> "FINITE_VALUES_MISSING"
    }
}
