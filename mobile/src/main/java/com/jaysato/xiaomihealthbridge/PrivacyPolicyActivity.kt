package com.jaysato.xiaomihealthbridge

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(padding, padding, padding, padding)
            addView(TextView(this@PrivacyPolicyActivity).apply {
                textSize = 17f
                text = "隱私與本機資料\n\nPPG 原始證據與衍生分析的 Room 資料庫使用 SQLCipher 加密，金鑰由 Android Keystore 保護。其他感測器快照、偏好設定與診斷匯出尚未全部完成 App 層級靜態加密，保存在手機 App 私有儲存空間。原始資料不會自動上傳到本 App 伺服器。工程除錯原始匯出須由使用者明確操作，匯出為明文且應只傳給信任的接收端。\n\n下方功能會清除本手機 App 保存的手錶感測快照、原始證據與衍生分析、掃描診斷、ECG／外部感測校準設定、袖帶讀值、靜息心率估算狀態、運動 GPS 路線及相關匯出檔；不會刪除 Health Connect、Xiaomi/Mi Fitness、手錶端或其他 App 的資料，也不會刪除個人檔案。"
            })
            addView(Button(this@PrivacyPolicyActivity).apply {
                text = "清除此手機 App 的感測資料"
                setOnClickListener {
                    AlertDialog.Builder(this@PrivacyPolicyActivity)
                        .setTitle("清除本機感測資料？")
                        .setMessage("將清除手機 App 內的手錶快照、PPG 原始證據與衍生分析、掃描診斷、感測器校準設定、袖帶讀值、靜息心率估算狀態、運動 GPS 路線與相關匯出檔。此操作不會刪除 Health Connect 或手錶資料；後續新量測仍會保存。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("清除") { _, _ ->
                            isEnabled = false
                            lifecycleScope.launch {
                                val result = try {
                                    withContext(Dispatchers.IO) { LocalSensorDataDeletion.clear(this@PrivacyPolicyActivity) }
                                } finally {
                                    isEnabled = true
                                }
                                val summary = "已清除偏好檔 ${result.clearedPreferences} 個、檔案 ${result.deletedFiles} 個" +
                                    if (result.complete) "與證據庫。" else "；${result.failures} 項刪除失敗，請重試或清除 App 儲存空間。"
                                AlertDialog.Builder(this@PrivacyPolicyActivity)
                                    .setTitle(if (result.complete) "清除完成" else "部分清除")
                                    .setMessage(summary)
                                    .setPositiveButton("確定", null)
                                    .show()
                            }
                        }
                        .show()
                }
            })
        }
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
