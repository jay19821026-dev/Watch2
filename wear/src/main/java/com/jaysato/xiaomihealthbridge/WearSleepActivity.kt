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
import java.time.Instant
import java.time.ZoneId

/** Round-watch Sleep glance — honest stages unavailable; detail on phone. */
class WearSleepActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(WearInsightsListenerService.PREFS, MODE_PRIVATE)
        val hours = prefs.getFloat("sleep_hours", Float.NaN)
        val sessionHours = prefs.getFloat("sleep_session_hours", Float.NaN)
        val stagesComplete = prefs.getBoolean("sleep_stages_complete", false)
        val inferred = prefs.getBoolean("sleep_inferred", false)
        val sleepEndedAt = prefs.getLong("sleep_session_end_at", 0L)
        val sleepRecordTime = WearSleepRecencyPolicy.label(sleepEndedAt, System.currentTimeMillis(), ZoneId.systemDefault())
        val displayedHours = when {
            hours.isFinite() && hours > 0f -> hours
            sessionHours.isFinite() && sessionHours > 0f -> sessionHours
            else -> Float.NaN
        }
        val hoursText = if (!inferred && displayedHours.isFinite()) "%.1f".format(displayedHours) else "—"
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
                            inferred -> "尚無可確認的睡眠時長"
                            !displayedHours.isFinite() || displayedHours <= 0f -> "尚無睡眠紀錄"
                            stagesComplete -> "小時 · $sleepRecordTime；已扣除記錄的清醒時間"
                            else -> "小時 · $sleepRecordTime；時段可能包含醒來時間"
                        },
                        color = WearComposeUi.Secondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        "睡眠階段目前沒有可靠紀錄；詳情可在手機查看。",
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

internal object WearSleepRecencyPolicy {
    fun label(endedAt: Long, now: Long, zone: ZoneId): String {
        if (endedAt <= 0L || endedAt > now) return "紀錄時間未確認"
        val recordDate = Instant.ofEpochMilli(endedAt).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            recordDate == today -> "今天醒來的紀錄"
            recordDate == today.minusDays(1) -> "昨天醒來的紀錄"
            else -> "較早的睡眠紀錄"
        }
    }
}
