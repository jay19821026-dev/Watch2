package com.jaysato.xiaomihealthbridge

object WatchStatusRequestState {
    fun shouldRetryNodeLookup(state: String): Boolean = state == "querying_connected_nodes"

    fun isAwaitingResponse(state: String): Boolean =
        state == "sending_status_request" || state == "waiting_for_watch_response"

    fun shouldArmResponseTimeout(state: String): Boolean = state == "waiting_for_watch_response"

    fun timeoutDeadline(
        state: String,
        requestStartedAt: Long,
        messageAcceptedAt: Long,
        sendTimeoutMs: Long,
        responseTimeoutMs: Long
    ): Long? = when (state) {
        "sending_status_request" -> requestStartedAt.takeIf { it > 0L }?.plus(sendTimeoutMs)
        "waiting_for_watch_response" -> messageAcceptedAt.takeIf { it > 0L }?.plus(responseTimeoutMs)
        else -> null
    }

    fun canResolveTimeout(currentState: String, expectedState: String): Boolean =
        currentState == expectedState && isAwaitingResponse(currentState)

    fun restoredElapsedTimestamp(
        savedElapsedAt: Long,
        savedWallClockAt: Long,
        nowElapsedAt: Long,
        nowWallClockAt: Long
    ): Long? = when {
        savedElapsedAt > 0L -> savedElapsedAt
        savedWallClockAt > 0L -> nowElapsedAt - (nowWallClockAt - savedWallClockAt).coerceAtLeast(0L)
        else -> null
    }
}
