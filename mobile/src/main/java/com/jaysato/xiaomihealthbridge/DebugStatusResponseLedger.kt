package com.jaysato.xiaomihealthbridge

internal object DebugStatusResponseLedger {
    const val MAX_RESPONSES = 8
    private const val RESPONSE_KEY_PREFIX = "debug_status_response_"

    fun responseKey(requestId: String): String = "$RESPONSE_KEY_PREFIX$requestId"

    fun appendRequestId(encodedIds: String?, requestId: String): List<String> {
        val existing = encodedIds.orEmpty()
            .split(',')
            .filter { it.matches(REQUEST_ID_PATTERN) }
        return (existing.filterNot { it == requestId } + requestId).takeLast(MAX_RESPONSES)
    }

    fun isActiveResponse(activeRequestId: String?, responseRequestId: String): Boolean =
        activeRequestId == responseRequestId

    private val REQUEST_ID_PATTERN = Regex("[A-Za-z0-9-]{16,64}")
}
