package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class CompassActivity : ComponentActivity(), SensorEventListener {
    private lateinit var heading: TextView
    private val manager by lazy { getSystemService(SensorManager::class.java) }
    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        heading = TextView(this).apply {
            text = "—°\n指南針準備中"
            textSize = 30f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        }
        WearUi.installStatic(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.BLACK)
            addView(TextView(context).apply { text = "指南針"; textSize = 16f; setTextColor(Color.rgb(100, 210, 255)); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(64)))
            addView(heading, LinearLayout.LayoutParams(-1, dp(240)))
            addView(TextView(context).apply { text = "使用旋轉向量與磁力計；離開頁面即停止感測。"; textSize = 11f; setTextColor(Color.GRAY); gravity = Gravity.CENTER })
        })
    }

    override fun onResume() { super.onResume(); manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } }
    override fun onPause() { manager?.unregisterListener(this); super.onPause() }
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        val degrees = ((Math.toDegrees(orientation[0].toDouble()) + 360) % 360).toInt()
        val direction = listOf("北", "東北", "東", "東南", "南", "西南", "西", "西北")[(degrees + 22) / 45 % 8]
        heading.text = "$degrees°\n$direction"
        getSharedPreferences(AdvancedSensorController.PREFS, MODE_PRIVATE).edit().putFloat(AdvancedSensorController.KEY_HEADING, degrees.toFloat()).apply()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
