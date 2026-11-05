package com.jaysato.xiaomihealthbridge

internal object SensorSuiteProvenancePolicy {
    fun belongsToSession(measurementSession: Long, acquisitionSession: Long): Boolean =
        measurementSession > 0L && acquisitionSession > 0L && measurementSession == acquisitionSession

    fun dataProvenance(reportingMode: String?, provenance: String?): String =
        if (reportingMode == "derived") "derived_estimate"
        else provenance?.takeIf(String::isNotBlank) ?: "hardware_event"
}
