package com.jaysato.xiaomihealthbridge

internal object ObservedBooleanStatus {
    fun label(value: Any?): String = when (value) {
        true -> "true"
        false -> "false"
        else -> "未知"
    }
}
