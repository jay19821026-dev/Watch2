package com.jaysato.xiaomihealthbridge

import androidx.health.connect.client.records.metadata.Metadata

/** Health Connect recording method is provenance, not proof of device calibration. */
object OxygenRecordingMethodPolicy {
    enum class Method(val label: String) {
        ALL("全部量測方式"),
        AUTOMATIC("裝置自動記錄"),
        ACTIVE("主動量測"),
        MANUAL("手動輸入"),
        UNKNOWN("方式未註明")
    }

    fun classify(recordingMethod: Int?): Method = when (recordingMethod) {
        Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED -> Method.AUTOMATIC
        Metadata.RECORDING_METHOD_ACTIVELY_RECORDED -> Method.ACTIVE
        Metadata.RECORDING_METHOD_MANUAL_ENTRY -> Method.MANUAL
        else -> Method.UNKNOWN
    }

    fun eligibleForDeviceReference(methods: List<Int?>): Boolean =
        methods.isNotEmpty() && methods.all {
            classify(it) == Method.AUTOMATIC || classify(it) == Method.ACTIVE
        }
}
