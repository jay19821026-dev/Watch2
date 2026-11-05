package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthCenterReadStatePolicyTest {
    @Test
    fun timedOutAndFailedReadsDoNotPreventNextMetric() = kotlinx.coroutines.runBlocking {
        val timedOut = HealthCenterReadStatePolicy.read("心率", 1L) {
            kotlinx.coroutines.delay(100L)
            3
        }
        org.junit.Assert.assertTrue(timedOut is HealthCenterMetricRead.ReadFailed)
        val failed = HealthCenterReadStatePolicy.read("睡眠") { throw IllegalStateException("unavailable") }
        org.junit.Assert.assertTrue(failed is HealthCenterMetricRead.ReadFailed)
        assertEquals(HealthCenterMetricRead.Success(7), HealthCenterReadStatePolicy.read("血氧") { 7 })
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun lifecycleCancellationStillPropagates() = kotlinx.coroutines.runBlocking {
        HealthCenterReadStatePolicy.read("心率") { throw kotlinx.coroutines.CancellationException("closed") }
        Unit
    }

    @Test
    fun separatesPermissionQueryFailureDenialEmptyAndSuccess() {
        assertEquals(
            HealthCenterMetricRead.PermissionQueryFailed("Health Connect 權限狀態查詢失敗"),
            HealthCenterReadStatePolicy.permissionFailure(setOf("read"), null)
        )
        assertEquals(
            HealthCenterMetricRead.PermissionDenied,
            HealthCenterReadStatePolicy.permissionFailure(setOf("read"), emptySet())
        )
        assertNull(HealthCenterReadStatePolicy.permissionFailure(setOf("read"), setOf("read")))
        assertEquals(HealthCenterMetricRead.Empty, HealthCenterReadStatePolicy.fromCount(0))
        assertEquals(HealthCenterMetricRead.Success(3), HealthCenterReadStatePolicy.fromCount(3))
        assertEquals(HealthCenterMetricRead.ReadFailed("Health Connect 讀取失敗"), HealthCenterReadStatePolicy.fromCount(-1))
    }

    @Test
    fun permissionQueryFailureRetainsUnderlyingHealthConnectCategory() {
        assertEquals(
            HealthCenterMetricRead.PermissionQueryFailed(
                "Health Connect 權限狀態：Health Connect IPC 通訊失敗，稍後重試"
            ),
            HealthCenterReadStatePolicy.permissionFailure(
                setOf("read"), null, android.os.RemoteException("private IPC details")
            )
        )
    }

    @Test
    fun elevationAndFloorsPermissionsAreEvaluatedIndependently() {
        val granted = setOf("read_elevation")

        assertNull(HealthCenterReadStatePolicy.permissionFailure(setOf("read_elevation"), granted))
        assertEquals(
            HealthCenterMetricRead.PermissionDenied,
            HealthCenterReadStatePolicy.permissionFailure(setOf("read_floors"), granted)
        )
        assertEquals(HealthCenterMetricRead.Success(2), HealthCenterReadStatePolicy.fromCount(2))

        val floorsGranted = setOf("read_floors")
        assertNull(HealthCenterReadStatePolicy.permissionFailure(setOf("read_floors"), floorsGranted))
        assertEquals(
            HealthCenterMetricRead.PermissionDenied,
            HealthCenterReadStatePolicy.permissionFailure(setOf("read_elevation"), floorsGranted)
        )
    }

    @Test
    fun preservesKnownReadFailureReasonsInsteadOfImplyingNoRecords() {
        assertEquals(
            "步數：Health Connect 分頁／紀錄數超過安全上限，拒絕部分結果",
            HealthCenterReadStatePolicy.readFailure("步數", HealthConnectReadLimitException("limit")).detail
        )
        assertEquals(
            "心率：Health Connect 紀錄 ID 空白或重複，拒絕合併",
            HealthCenterReadStatePolicy.readFailure("心率", InvalidHealthConnectRecordIdentityException()).detail
        )
        assertEquals(
            "血氧：Health Connect 服務目前不可用，確認服務狀態後重試",
            HealthCenterReadStatePolicy.readFailure("血氧", IllegalStateException("internal detail")).detail
        )
    }
}
