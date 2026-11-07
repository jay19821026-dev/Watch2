package com.jaysato.xiaomihealthbridge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color

class WearAppsActivity : ComponentActivity() {
    private data class AppTile(val title: String, val subtitle: String, val icon: String, val color: Color, val open: WearAppsActivity.() -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apps = listOf(
            AppTile("智慧省電", "背景調度與低電策略", "⚡", Color(0xFF30D5C8)) { open(WearPowerSchedulerActivity::class.java) },
            AppTile("健康摘要", "個別觀測與訓練紀錄", "✓", Color(0xFF30D5C8)) { open(WearReadinessActivity::class.java) },
            AppTile("心率", "即時量測與警示", "♥", Color(0xFFFF3B30)) { startActivity(WearMeasurePageActivity.intent(this, WearMeasurePageActivity.Mode.HEART_RATE)) },
            AppTile("心率歷史", "日／週／月／年", "⏱", Color(0xFFFF6482)) { open(WearHeartHistoryActivity::class.java) },
            AppTile("ECG", "研究波形與訊號品質", "〽", Color(0xFF34C759)) { startActivity(Intent(this, WearEcgActivity::class.java).putExtra(WearEcgActivity.EXTRA_AUTO_START, false)) },
            AppTile("心律通知", "不規則心律篩檢", "!", Color(0xFF30D158)) { open(WearRhythmActivity::class.java) },
            AppTile("血氧", "SpO₂ 量測", "O₂", Color(0xFF0A84FF)) { startActivity(WearMeasurePageActivity.intent(this, WearMeasurePageActivity.Mode.SPO2)) },
            AppTile("HRV", "恢復與壓力趨勢", "H", Color(0xFFBF5AF2)) { open(WearHrvActivity::class.java) },
            AppTile("Vitals", "關鍵健康指標", "+", Color(0xFF007AFF)) { open(WearVitalsActivity::class.java) },
            AppTile("運動", "記錄訓練", "▶", Color(0xFF30D158)) { open(WearWorkoutActivity::class.java) },
            AppTile("噪音", "聽力暴露", "dB", Color(0xFFFFD60A)) { open(WearNoiseActivity::class.java) },
            AppTile("體溫", "腕部溫度趨勢", "°", Color(0xFFFF9F0A)) { startActivity(WearMeasurePageActivity.intent(this, WearMeasurePageActivity.Mode.TEMPERATURE)) },
            AppTile("血壓", "此錶不提供量測 · 袖帶說明", "BP", Color(0xFFFF2D55)) { open(WearOpticalBpActivity::class.java) },
            AppTile("睡眠", "總時長 · 無假分期", "Zzz", Color(0xFF5E5CE6)) { open(WearSleepActivity::class.java) },
            AppTile("PPG", "光學原始訊號", "P", Color(0xFFFF453A)) { startActivity(WearMeasurePageActivity.intent(this, WearMeasurePageActivity.Mode.PPG)) },
            AppTile("BioZ", "生物阻抗研究通道", "Z", Color(0xFF5E5CE6)) { startActivity(WearMeasurePageActivity.intent(this, WearMeasurePageActivity.Mode.BIOZ)) },
            AppTile("氣壓", "海拔與氣壓", "↥", Color(0xFF64D2FF)) { startActivity(WearMeasurePageActivity.intent(this, WearMeasurePageActivity.Mode.PRESSURE)) },
            AppTile("指南針", "方向與返航", "N", Color(0xFFAC8E68)) { open(CompassActivity::class.java) },
            AppTile("手勢", "動作快捷控制", "✦", Color(0xFF8E8E93)) { open(WearGestureActivity::class.java) },
            AppTile("離腕", "佩戴狀態", "○", Color(0xFFFFD60A)) { startActivity(WearMeasurePageActivity.intent(this, WearMeasurePageActivity.Mode.OFF_BODY)) },
            AppTile("晶片", "感測器與校準", "⚙", Color(0xFF1F6FEB)) { open(WearChipHubActivity::class.java) }
        )

        setContent {
            WearComposeUi.Screen("健康 App") {
                apps.forEach { app ->
                    item {
                        WearComposeUi.HealthButton(app.title, app.subtitle, app.color, app.icon) {
                            app.open(this@WearAppsActivity)
                        }
                    }
                }
            }
        }
    }

    private fun open(activity: Class<*>) = startActivity(Intent(this, activity))
}
