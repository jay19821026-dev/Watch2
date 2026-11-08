package com.jaysato.xiaomihealthbridge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

class WearVitalsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dash = WearHealthStore.dashboard(this)
        val hrSub = if (dash.heartRate > 0) "最近 ${dash.heartRate} bpm · 點一下量測" else "點一下開始量測"
        val bp = WearHealthStore.latestOpticalBp(this)
        val bpSub = bp?.let { "%.0f/%.0f mmHg · 研究估計".format(it.first, it.second) }
            ?: "非醫療光學估計 · 點一下量測"
        setContent {
            WearComposeUi.Screen("健康指標") {
                item { metric("健康摘要", "今天的健康紀錄", Color(0xFF30D5C8)) { open(WearReadinessActivity::class.java) } }
                item { metric("心率", hrSub, Color(0xFFFF3B30)) { measure(WearMeasurePageActivity.Mode.HEART_RATE) } }
                item { metric("心率歷史", "日／週／月／年", Color(0xFFFF6482)) { open(WearHeartHistoryActivity::class.java) } }
                item { metric("血氧", "點一下開始量測", Color(0xFF0A84FF)) { measure(WearMeasurePageActivity.Mode.SPO2) } }
                item { metric("腕部溫度", "量測後查看是否有可用讀值", Color(0xFFFF9F0A)) { measure(WearMeasurePageActivity.Mode.TEMPERATURE) } }
                item { metric("HRV", "恢復與壓力", Color(0xFFBF5AF2)) { open(WearHrvActivity::class.java) } }
                item { metric("心律", "查看心電量測", Color(0xFF34C759)) { open(WearRhythmActivity::class.java) } }
                item { metric("呼吸", "點一下開始量測", Color(0xFF64D2FF)) { measure(WearMeasurePageActivity.Mode.RESPIRATION) } }
                item { metric("血壓", bpSub, Color(0xFFFF2D55)) { open(WearOpticalBpActivity::class.java) } }
                item { metric("睡眠", "睡眠時長與趨勢", Color(0xFF5E5CE6)) { open(WearSleepActivity::class.java) } }
                item { metric("全部 Apps", "查看所有健康工具", Color(0xFF5E5CE6)) { open(WearAppsActivity::class.java) } }
            }
        }
    }

    @Composable
    private fun metric(title: String, subtitle: String, color: Color, onClick: () -> Unit) =
        WearComposeUi.HealthButton(title, subtitle, color, onClick = onClick)

    private fun measure(mode: WearMeasurePageActivity.Mode) = startActivity(WearMeasurePageActivity.intent(this, mode))
    private fun open(activity: Class<*>) = startActivity(Intent(this, activity))
}
