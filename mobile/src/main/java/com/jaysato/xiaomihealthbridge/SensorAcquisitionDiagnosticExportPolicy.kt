package com.jaysato.xiaomihealthbridge

internal object SensorAcquisitionDiagnosticExportPolicy {
    fun matchesRowSession(
        diagnosticSessionStartedAt: Long,
        rowSessionStartedAt: Long,
        diagnosticSuiteId: String,
        rowSuiteId: String
    ): Boolean {
        if (diagnosticSessionStartedAt <= 0L || rowSessionStartedAt <= 0L ||
            diagnosticSessionStartedAt != rowSessionStartedAt
        ) return false
        return if (diagnosticSuiteId.isBlank() && rowSuiteId.isBlank()) {
            true
        } else {
            diagnosticSuiteId.isNotBlank() && diagnosticSuiteId == rowSuiteId
        }
    }
}
