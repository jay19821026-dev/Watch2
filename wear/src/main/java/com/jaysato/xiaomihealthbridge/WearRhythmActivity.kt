package com.jaysato.xiaomihealthbridge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** Watch-side irregular rhythm glance — research labels only. */
class WearRhythmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val title = prefs.getString("ecg_rhythm_title", null)
        val detail = prefs.getString("ecg_rhythm_detail", null)
        val bpm = prefs.getFloat("ecg_rhythm_bpm", Float.NaN).takeIf { it.isFinite() }
        val rr = prefs.getInt("ecg_rhythm_rr_count", 0)
        val samples = prefs.getInt("ecg_samples", 0)
        val time = prefs.getLong("ecg_time", 0L)
        val fresh = time > 0L && System.currentTimeMillis() - time in 0..RESULT_MAX_AGE_MILLIS
        val abnormal = fresh && rr >= 5 && bpm != null && listOf("房顫", "不整", "不規則", "異常").any { title.orEmpty().contains(it) }
        val normal = fresh && rr >= 5 && bpm != null && listOf("規則", "竇性").any { title.orEmpty().contains(it) } && !abnormal
        val consumerTitle = when {
            abnormal -> "異常"
            normal -> "正常"
            else -> "測量中"
        }
        val accent = when {
            abnormal -> Color(0xFFFF3B30)
            normal -> Color(0xFF34C759)
            else -> Color(0xFF64D2FF)
        }
        setContent {
            WearComposeUi.Screen("心律通知") {
                item {
                    Text(
                        consumerTitle,
                        color = accent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        buildString {
                            if (normal || abnormal) append("%.0f bpm · RR $rr".format(bpm))
                            else append(if (samples > 0) "已收到訊號，等待足夠資料完成判定" else "請在 ECG 頁量測")
                            if (time > 0) {
                                append('\n')
                                append(SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(time)))
                            }
                        },
                        color = WearComposeUi.Secondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
                if ((normal || abnormal) && !detail.isNullOrBlank()) {
                    item {
                        Text(
                            detail.take(120),
                            color = Color.White,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                }
                item {
                    Text(
                        "研究篩檢 · 非醫療 AF 診斷",
                        color = Color(0xFFFFBE78),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    )
                }
                item {
                    WearComposeUi.HealthButton("開始 ECG", "等到有波形／RR 才結束", Color(0xFF34C759), "〽") {
                        startActivity(
                            Intent(this@WearRhythmActivity, WearEcgActivity::class.java)
                                .putExtra(WearEcgActivity.EXTRA_AUTO_START, true)
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val PREFS = "wear_rhythm_local"
        private const val RESULT_MAX_AGE_MILLIS = 10 * 60 * 1000L
    }
}
