package com.jaysato.xiaomihealthbridge

internal object DebugStatusResponseValidation {
    private val requestIdPattern = Regex("[A-Za-z0-9-]{16,64}")
    private val sensitiveStatusPattern = Regex("(?:WHS\\s+心率|心率|血氧|SpO[₂2]|HRV(?:\\s+(?:RMSSD|SDNN))?|BIOZ|阻抗|體溫|體表溫度|ECG|心電圖|heart\\s*rate)\\s*(?:[:：=]\\s*)?[-+]?\\d+(?:\\.\\d+)?[^；;\\n]*", RegexOption.IGNORE_CASE)

    fun rejectionReason(requestId: String, status: String, timestamp: Long): String? = when {
        !requestIdPattern.matches(requestId) -> "invalid_request_id"
        status.isBlank() -> "missing_status"
        timestamp <= 0L -> "invalid_watch_timestamp"
        else -> null
    }

    fun sanitizeStatusForStorage(status: String): String =
        sensitiveStatusPattern.replace(status, "生理數值 [隱去]")
}
