package com.jaysato.xiaomihealthbridge

internal fun isEngineeringDebugRequestCurrent(
    requestGeneration: Int,
    currentGeneration: Int,
    isFinishing: Boolean,
    isDestroyed: Boolean,
    isChangingConfigurations: Boolean
): Boolean = requestGeneration == currentGeneration &&
    !isFinishing && !isDestroyed && !isChangingConfigurations

internal fun isMatchingWatchStatusResponse(requestId: String, responseRequestId: String): Boolean =
    requestId.isNotBlank() && requestId == responseRequestId
