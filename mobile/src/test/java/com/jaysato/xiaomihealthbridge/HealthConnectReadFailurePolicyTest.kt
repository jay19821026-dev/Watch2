package com.jaysato.xiaomihealthbridge

import android.os.RemoteException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CancellationException

class HealthConnectReadFailurePolicyTest {
    @Test
    fun metricEmptyPermissionAndFailureStatesRemainDistinct() {
        assertEquals(
            HealthConnectMetricReadState.NO_RECORDS,
            HealthConnectMetricReadStatePolicy.classify("心率", emptySet(), setOf("心率"), emptySet())
        )
        assertEquals(
            HealthConnectMetricReadState.PERMISSION_DENIED,
            HealthConnectMetricReadStatePolicy.classify("心率", setOf("心率"), emptySet(), emptySet())
        )
        assertEquals(
            HealthConnectMetricReadState.READ_FAILED,
            HealthConnectMetricReadStatePolicy.classify("心率", emptySet(), emptySet(), setOf("心率"))
        )
        assertEquals(
            HealthConnectMetricReadState.UNAVAILABLE,
            HealthConnectMetricReadStatePolicy.classify("心率", emptySet(), emptySet(), emptySet())
        )
    }

    @Test
    fun invalidRecordIdentityIsNotCollapsedIntoGenericReadFailure() {
        assertEquals(
            "心率：Health Connect 紀錄 ID 空白或重複，拒絕合併",
            HealthConnectReadFailurePolicy.describe("心率", InvalidHealthConnectRecordIdentityException())
        )
    }

    @Test
    fun platformReadFailuresRetainActionableFailureCategories() {
        assertEquals(
            "心率：Health Connect 儲存讀取 I/O 失敗，稍後重試",
            HealthConnectReadFailurePolicy.describe("心率", java.io.IOException("private storage detail"))
        )
        assertEquals(
            "心率：Health Connect IPC 通訊失敗，稍後重試",
            HealthConnectReadFailurePolicy.describe("心率", RemoteException("private transport detail"))
        )
        assertEquals(
            "心率：Health Connect 服務目前不可用，確認服務狀態後重試",
            HealthConnectReadFailurePolicy.describe("心率", IllegalStateException("private provider detail"))
        )
        assertEquals(
            "心率：Health Connect 查詢參數無效，需檢查 App 查詢設定",
            HealthConnectReadFailurePolicy.describe("心率", IllegalArgumentException("private query detail"))
        )
        assertEquals(
            "心率：讀取失敗",
            HealthConnectReadFailurePolicy.describe("心率", UnsupportedOperationException("private provider detail"))
        )
    }

    @Test
    fun recordAndPageCapsExplainThatPartialResultsWereRejected() {
        assertEquals(
            "心率：Health Connect 分頁／紀錄數超過安全上限，拒絕部分結果",
            HealthConnectReadFailurePolicy.describe("心率", HealthConnectReadLimitException("limit"))
        )
    }

    @Test
    fun permissionRevocationDuringReadIsNotReportedAsGenericFailure() {
        assertEquals(
            "血氧：Health Connect 權限遭拒或已撤銷，請重新確認授權",
            HealthConnectReadFailurePolicy.describe("血氧", SecurityException("permission revoked"))
        )
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsPropagatedInsteadOfRecordedAsAReadFailure() {
        HealthConnectReadFailurePolicy.describe("心率", CancellationException("cancelled"))
    }
}
