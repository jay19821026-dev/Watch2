package com.jaysato.xiaomihealthbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Round-watch HR history with 日／週／月／年 buckets — phone MetricDetail remains the deep chart. */
class WearHeartHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val points = WearHealthStore.heartPoints(this)
        setContent {
            var period by remember { mutableIntStateOf(0) }
            val filtered = remember(period, points) { filterByPeriod(points, period) }
            val avg = filtered.map { it.second }.average().takeIf { filtered.isNotEmpty() }
            val min = filtered.minOfOrNull { it.second }
            val max = filtered.maxOfOrNull { it.second }
            val latest = points.lastOrNull()
            WearComposeUi.Screen("心率歷史") {
                item {
                    Text(
                        latest?.second?.toString() ?: "—",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        latest?.let { "最近 ${fmt(it.first)} · bpm" } ?: "尚無心率紀錄",
                        color = WearComposeUi.Secondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    )
                }
                item {
                    WearComposeUi.HealthButton(
                        listOf("日", "週", "月", "年")[period],
                        "點一下切換期間",
                        Color(0xFFFF3B30),
                        "⏱"
                    ) { period = (period + 1) % 4 }
                }
                item {
                    Text(
                        buildString {
                            append(if (avg != null) "平均 %.0f".format(avg) else "平均 —")
                            append(" · ")
                            append(if (min != null && max != null) "範圍 $min–$max" else "範圍 —")
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
                filtered.takeLast(6).asReversed().forEach { (t, bpm) ->
                    item {
                        Text(
                            "${fmt(t)}  $bpm bpm",
                            color = WearComposeUi.Secondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                }
                item {
                    WearComposeUi.HealthButton("立即量測", "有 bpm 後結束並同步", Color(0xFFFF3B30), "♥") {
                        startActivity(WearMeasurePageActivity.intent(this@WearHeartHistoryActivity, WearMeasurePageActivity.Mode.HEART_RATE))
                    }
                }
            }
        }
    }

    private fun filterByPeriod(points: List<Pair<Long, Long>>, period: Int): List<Pair<Long, Long>> {
        val now = System.currentTimeMillis()
        val window = when (period) {
            1 -> TimeUnit.DAYS.toMillis(7)
            2 -> TimeUnit.DAYS.toMillis(31)
            3 -> TimeUnit.DAYS.toMillis(365)
            else -> TimeUnit.DAYS.toMillis(1)
        }
        return points.filter { now - it.first <= window }
    }

    private fun fmt(time: Long) =
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(time))
}
