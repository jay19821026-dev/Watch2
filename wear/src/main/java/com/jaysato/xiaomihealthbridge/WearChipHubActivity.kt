package com.jaysato.xiaomihealthbridge

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Hub: every verified chip → feature pages matching other-watch / ADI reference uses.
 */
class WearChipHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(16), dp(14), dp(28))
            setBackgroundColor(Color.BLACK)
            addView(TextView(context).apply {
                text = "CHIP FEATURE MAP"; textSize = 10f; letterSpacing = .14f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(100, 210, 255))
            })
            addView(Button(context).apply {
                text = WearChipCatalog.calibrationFeature.title
                isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor("#126A83"), Color.parseColor("#243B68"))).apply {
                    cornerRadius = dp(8).toFloat()
                }
                setOnClickListener { open(WearChipCatalog.Page.Calibration) }
            }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) })
            addView(TextView(context).apply {
                text = "晶片功能中心"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                setPadding(0, dp(4), 0, dp(4))
            })
            addView(TextView(context).apply {
                text = "依公開 Sensor API 名稱與候選晶片族群整理功能；料號與量測狀態仍需核實"
                textSize = 11f; gravity = Gravity.CENTER; setTextColor(Color.rgb(150, 158, 178))
                setPadding(0, 0, 0, dp(12))
            })
            WearChipCatalog.chips.forEach { chip ->
                addView(TextView(context).apply {
                    text = "${chip.part} · ${chip.vendor}"
                    textSize = 14f; setTextColor(Color.WHITE); setPadding(0, dp(14), 0, dp(2))
                })
                addView(TextView(context).apply {
                    text = "${chip.role}\nHAL：${chip.halEvidence}"
                    textSize = 11f; setTextColor(Color.rgb(150, 158, 178)); setPadding(0, 0, 0, dp(6))
                })
                chip.features.forEach { feature ->
                    addView(Button(context).apply {
                        text = feature.title
                        isAllCaps = false
                        textSize = 13f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        background = GradientDrawable(
                            GradientDrawable.Orientation.TL_BR,
                            intArrayOf(Color.parseColor("#243044"), Color.parseColor("#151B28"))
                        ).apply { cornerRadius = dp(8).toFloat() }
                        setPadding(dp(12), dp(8), dp(12), dp(8))
                        setOnClickListener { open(feature.page) }
                    }, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(4)
                        height = dp(48)
                    })
                    addView(TextView(context).apply {
                        text = feature.otherWatchUse
                        textSize = 10f; setTextColor(Color.rgb(120, 130, 150))
                        setPadding(dp(6), dp(2), dp(6), dp(4))
                    })
                }
            }
            addView(TextView(context).apply {
                text = "研究／健康參考，非醫療診斷。未公開校準者不輸出臨床單位。"
                textSize = 11f; setTextColor(Color.rgb(255, 190, 120)); setPadding(0, dp(16), 0, 0)
            })
        }
        WearUi.installScrollable(this, content)
    }

    private fun open(page: WearChipCatalog.Page) {
        val intent = when (page) {
            is WearChipCatalog.Page.Measure -> WearMeasurePageActivity.intent(this, page.mode)
            WearChipCatalog.Page.Ecg -> Intent(this, WearEcgActivity::class.java)
            WearChipCatalog.Page.Hrv -> Intent(this, WearHrvActivity::class.java)
            WearChipCatalog.Page.Compass -> Intent(this, CompassActivity::class.java)
            WearChipCatalog.Page.Backtrack -> Intent(this, BacktrackActivity::class.java)
            WearChipCatalog.Page.Workout -> Intent(this, WearWorkoutActivity::class.java)
            WearChipCatalog.Page.Gesture -> Intent(this, WearGestureActivity::class.java)
            WearChipCatalog.Page.Calibration -> Intent(this, WearCalibrationActivity::class.java)
        }
        startActivity(intent)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
