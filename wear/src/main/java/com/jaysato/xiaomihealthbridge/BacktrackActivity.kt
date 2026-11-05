package com.jaysato.xiaomihealthbridge

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class BacktrackActivity : ComponentActivity(), LocationListener {
    private lateinit var status: TextView
    private var target: Location? = null
    private val manager by lazy { getSystemService(LocationManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = WearHealthStore.lastRoute(this).firstOrNull()?.split(',')?.let { parts ->
            val latitude = parts.getOrNull(1)?.toDoubleOrNull() ?: return@let null
            val longitude = parts.getOrNull(2)?.toDoubleOrNull() ?: return@let null
            Location("saved").apply { this.latitude = latitude; this.longitude = longitude }
        }
        status = TextView(this).apply {
            text = if (target == null) "尚無離線路線\n完成一次 GPS 運動後即可返航" else "正在取得 GPS…"
            textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        WearUi.installStatic(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.BLACK)
            addView(TextView(context).apply {
                text = "返航導航"; textSize = 14f; letterSpacing = .12f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#64D2FF"))
            })
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(260)))
            addView(TextView(context).apply {
                text = "路線儲存在手錶內，不需要網路"; textSize = 11f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#8E8E93"))
            })
        })
    }

    override fun onResume() {
        super.onResume()
        if (target != null && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000, 2f, this)
        }
    }

    override fun onPause() { manager.removeUpdates(this); super.onPause() }

    override fun onLocationChanged(location: Location) {
        val destination = target ?: return
        val distance = location.distanceTo(destination)
        val bearing = (location.bearingTo(destination) + 360f) % 360f
        val heading = getSharedPreferences(AdvancedSensorController.PREFS, MODE_PRIVATE)
            .getFloat(AdvancedSensorController.KEY_HEADING, 0f)
        val relative = (bearing - heading + 360f) % 360f
        status.text = "${arrow(relative)}\n${distance.toInt()} 公尺\n方位 ${bearing.toInt()}°"
    }

    private fun arrow(angle: Float) = when {
        angle < 22.5 || angle >= 337.5 -> "↑"
        angle < 67.5 -> "↗"
        angle < 112.5 -> "→"
        angle < 157.5 -> "↘"
        angle < 202.5 -> "↓"
        angle < 247.5 -> "↙"
        angle < 292.5 -> "←"
        else -> "↖"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
