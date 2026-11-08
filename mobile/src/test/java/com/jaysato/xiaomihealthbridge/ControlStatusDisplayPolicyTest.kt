package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlStatusDisplayPolicyTest {
    @Test fun rawEcgEventCountBecomesReadableOutcome() {
        assertEquals(
            "心電圖 量測已結束；請到結果頁查看可用讀值。",
            ControlStatusDisplayPolicy.describe("ECG 原始事件值 65573：216 個事件 · 原始欄位未解碼", true)
        )
    }

    @Test fun missingOxygenReadingRequestsRemeasurement() {
        assertEquals(
            "血氧 這次沒有取得可顯示的讀值；請確認佩戴後重測。",
            ControlStatusDisplayPolicy.describe("SpO₂：等待過久仍無數值 · 0 筆", false)
        )
    }

    @Test fun ordinaryStatusIsPreserved() {
        assertEquals("手錶在線", ControlStatusDisplayPolicy.describe("手錶在線", true))
    }
}
