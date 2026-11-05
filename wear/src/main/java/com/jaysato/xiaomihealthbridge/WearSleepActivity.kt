package com.jaysato.xiaomihealthbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text

/** Round-watch Sleep glance — honest stages unavailable; detail on phone. */
class WearSleepActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(WearInsightsListenerService.PREFS, MODE_PRIVATE)
        val hours = prefs.getFloat("sleep_hours", Float.NaN)
        val sessionHours = prefs.getFloat("sleep_session_hours", Float.NaN)
        val stagesComplete = prefs.getBoolean("sleep_stages_complete", false)
        val inferred = prefs.getBoolean("sleep_inferred", false)
        val displayedHours = when {
            hours.isFinite() && hours > 0f -> hours
            sessionHours.isFinite() && sessionHours > 0f -> sessionHours
            else -> Float.NaN
        }
        val hoursText = if (displayedHours.isFinite()) "%.1f".format(displayedHours) else "—"
        setContent {
            WearComposeUi.Screen("睡眠") {
                item {
                    Text(
                        hoursText,
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        when {
                            !displayedHours.isFinite() || displayedHours <= 0f -> "小時 · 詳情在手機 Sleep 專頁"
                            inferred -> "小時 · 低心率跨度，非睡眠紀錄"
                            stagesComplete -> "小時 · Health Connect 分期完整覆蓋，已扣除清醒"
                            else -> "小時 · 睡眠 session 時段，非實際睡眠時數"
                        },
                        color = WearComposeUi.Secondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        "REM／核心／深睡：無經驗證分期 API，不推算假分期。",
                        color = Color(0xFFFFBE78),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
                item {
                    Button(
                        onClick = { finish() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5E5CE6),
                            contentColor = Color.White
                        )
                    ) {
                        Text("完成", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
