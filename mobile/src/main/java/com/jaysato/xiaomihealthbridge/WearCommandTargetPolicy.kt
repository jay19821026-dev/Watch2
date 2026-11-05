package com.jaysato.xiaomihealthbridge

internal object WearCommandTargetPolicy {
    fun <T> uniqueTarget(nodes: List<T>): T? = nodes.singleOrNull()

    fun failureMessage(nodeCount: Int): String = when {
        nodeCount <= 0 -> "目前找不到已連線手錶"
        else -> "偵測到多台已連線手錶；請只保留目標手錶後重試"
    }
}
