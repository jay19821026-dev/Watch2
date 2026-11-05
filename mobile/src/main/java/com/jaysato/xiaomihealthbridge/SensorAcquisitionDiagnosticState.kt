package com.jaysato.xiaomihealthbridge

internal enum class SensorAcquisitionDiagnosticState(val label: String, val exportValue: String) {
    NOT_RECORDED("本次掃描沒有擷取診斷", "not_recorded"),
    ROW_SESSION_UNKNOWN("量測資料沒有工作階段資訊，無法配對本次掃描", "row_session_unknown"),
    DIFFERENT_SESSION("診斷來自其他掃描工作階段", "different_session"),
    SKIPPED_CHARGING("充電中略過，本次未嘗試貼腕量測", "skipped_charging"),
    SENSOR_NOT_EXPOSED("系統未暴露此感測器", "sensor_not_exposed"),
    PERMISSION_DENIED("感測器權限遭拒", "permission_denied"),
    REGISTRATION_EXCEPTION("感測器啟動發生錯誤", "registration_exception"),
    REGISTRATION_FAILED("感測器註冊失敗", "registration_failed"),
    NO_EVENTS("已註冊，但本次沒有事件", "no_events"),
    PHONE_RECEIPT_UNCONFIRMED("手錶有事件，手機尚未確認收到封包", "phone_receipt_unconfirmed"),
    SNAPSHOT_RECEIVED("手機已收到本次掃描封包", "snapshot_received");

    companion object {
        fun classify(
            registration: String,
            watchSessionStartedAt: Long,
            rowSessionStartedAt: Long,
            watchEventCount: Int,
            phoneReceivedCount: Int,
            phoneSessionStartedAt: Long,
            watchSuiteId: String = "",
            rowSuiteId: String = ""
        ): SensorAcquisitionDiagnosticState {
            if (registration.isBlank() || registration == "unknown" || watchSessionStartedAt <= 0L) {
                return NOT_RECORDED
            }
            if (rowSessionStartedAt <= 0L) return ROW_SESSION_UNKNOWN
            if (!SensorAcquisitionDiagnosticExportPolicy.matchesRowSession(
                    watchSessionStartedAt, rowSessionStartedAt, watchSuiteId, rowSuiteId
                )
            ) {
                return DIFFERENT_SESSION
            }
            if (registration == "skipped_charging") return SKIPPED_CHARGING
            if (registration == "sensor_not_exposed") return SENSOR_NOT_EXPOSED
            if (registration == "permission_denied") return PERMISSION_DENIED
            if (registration == "registration_exception") return REGISTRATION_EXCEPTION
            if (registration == "registration_failed") return REGISTRATION_FAILED
            if (watchEventCount <= 0) return NO_EVENTS
            if (phoneReceivedCount <= 0 || phoneSessionStartedAt != watchSessionStartedAt) {
                return PHONE_RECEIPT_UNCONFIRMED
            }
            return SNAPSHOT_RECEIVED
        }
    }
}
