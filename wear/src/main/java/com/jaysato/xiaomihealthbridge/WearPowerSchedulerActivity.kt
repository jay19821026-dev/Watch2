package com.jaysato.xiaomihealthbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import java.text.DateFormat
import java.util.Date

/**
 * Round-watch power scheduler UI — high contrast Wear Compose, large tap targets.
 * Does not force system AOD/brightness (OEM); schedules App background work safely.
 */
class WearPowerSchedulerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var state by remember { mutableStateOf(WearPowerScheduler.currentState(this)) }
            var policy by remember { mutableStateOf(WearPowerScheduler.policy(this)) }
            var reminders by remember { mutableStateOf(WearPowerScheduler.remindersEnabled(this)) }
            var deferSync by remember { mutableStateOf(WearPowerScheduler.deferSyncWhenLow(this)) }
            var estimate by remember { mutableStateOf(WearPowerScheduler.batteryEstimate(this)) }
            var decision by remember { mutableStateOf(WearPowerScheduler.aiDecision(this)) }
            var message by remember { mutableStateOf("可調度本 App 背景工作") }

            fun refresh(msg: String? = null) {
                state = WearPowerScheduler.apply(this)
                policy = WearPowerScheduler.policy(this)
                reminders = WearPowerScheduler.remindersEnabled(this)
                deferSync = WearPowerScheduler.deferSyncWhenLow(this)
                estimate = WearPowerScheduler.batteryEstimate(this)
                decision = WearPowerScheduler.aiDecision(this)
                if (msg != null) message = msg
            }

            val modeColor = when (state.mode) {
                WearPowerMode.NORMAL -> Color(0xFF34C759)
                WearPowerMode.SAVER -> Color(0xFFFFB340)
                WearPowerMode.CRITICAL -> Color(0xFFFF5F57)
            }

            WearComposeUi.Screen("智慧省電") {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1C1C1E), RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(modeColor.copy(alpha = 0.22f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${state.batteryPercent}%",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.label,
                            color = modeColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            state.detail,
                            color = WearComposeUi.Secondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            buildString {
                                if (state.charging) append("充電中 · ")
                                if (state.systemPowerSave) append("系統省電開 · ")
                                append(
                                    when (policy) {
                                        WearPowerPolicy.AUTO -> "策略：自動"
                                        WearPowerPolicy.FORCE_NORMAL -> "策略：強制正常"
                                        WearPowerPolicy.FORCE_SAVER -> "策略：強制省電"
                                        WearPowerPolicy.FORCE_CRITICAL -> "策略：強制極省"
                                    }
                                )
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            estimate.percentPerHour?.let { rate ->
                                val hours = estimate.estimatedHoursRemaining?.let { String.format("%.1f", it) } ?: "—"
                                "放電 %.1f%%/小時 · 預估剩餘 $hours 小時".format(rate)
                            } ?: "正在收集耗電樣本（至少需要兩次記錄）",
                            color = if (estimate.shouldConserve) Color(0xFFFFB340) else WearComposeUi.Secondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            "${decision.batteryBand} · ${decision.reason}",
                            color = WearComposeUi.Secondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }

                item {
                    Text(
                        "調度策略",
                        color = WearComposeUi.Secondary,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                        textAlign = TextAlign.Center
                    )
                }

                listOf(
                    WearPowerPolicy.AUTO to "自動",
                    WearPowerPolicy.FORCE_NORMAL to "正常",
                    WearPowerPolicy.FORCE_SAVER to "省電",
                    WearPowerPolicy.FORCE_CRITICAL to "極省"
                ).chunked(2).forEach { row ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { (value, label) ->
                                val selected = policy == value
                                Button(
                                    onClick = {
                                        WearPowerScheduler.setPolicy(this@WearPowerSchedulerActivity, value)
                                        refresh("已套用$label")
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) Color(0xFF0A84FF) else Color(0xFF2C2C2E),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                item {
                    WearComposeUi.ToggleRow(
                        title = "久坐提醒",
                        subtitle = if (reminders) "低電量時自動延後" else "已關閉",
                        checked = reminders,
                        onToggle = {
                            WearPowerScheduler.setRemindersEnabled(this@WearPowerSchedulerActivity, !reminders)
                            refresh(if (!reminders) "久坐提醒已開" else "久坐提醒已關")
                        }
                    )
                }
                item {
                    WearComposeUi.ToggleRow(
                        title = "低電延後同步",
                        subtitle = if (deferSync) "極省時暫停非必要背景" else "不延後",
                        checked = deferSync,
                        onToggle = {
                            WearPowerScheduler.setDeferSyncWhenLow(this@WearPowerSchedulerActivity, !deferSync)
                            refresh(if (!deferSync) "低電延後已開" else "低電延後已關")
                        }
                    )
                }

                item {
                    Text(
                        message,
                        color = Color(0xFFFFD60A),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }

                item {
                    val updated = WearPowerScheduler.lastUpdatedMs(this@WearPowerSchedulerActivity)
                    Text(
                        if (updated > 0) {
                            "更新 ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(updated))}"
                        } else {
                            "尚未套用"
                        },
                        color = WearComposeUi.Secondary,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Button(
                        onClick = { refresh("已立即重新調度") },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF30D5C8),
                            contentColor = Color.Black
                        )
                    ) {
                        Text("立即套用", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                item {
                    Button(
                        onClick = { finish() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2C2C2E),
                            contentColor = Color.White
                        )
                    ) {
                        Text("完成", fontSize = 14.sp)
                    }
                }
                item {
                    Text(
                        "無法改系統常亮／AOD 亮度（原廠鎖定）。此頁調度本 App 背景工作與提醒。",
                        color = Color(0xFFFFBE78),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
