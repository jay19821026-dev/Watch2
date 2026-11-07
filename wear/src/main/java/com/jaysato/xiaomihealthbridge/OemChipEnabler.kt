package com.jaysato.xiaomihealthbridge

import android.app.Activity
import android.content.Intent

object OemChipEnabler {
    fun enable(activity: Activity, kind: RawVitalsProbe.Kind): String? {
        val intent = intentFor(kind) ?: return null
        return runCatching {
            activity.startActivity(intent)
            "已請求開啟原廠${kind.label}頁；是否啟用由原廠畫面顯示"
        }.getOrElse { "原廠${kind.label}頁面無法由此裝置開啟" }
    }

    /** From a foreground service: bring up OEM measure pages that arm MAX86178 / SpO₂ AFE. */
    fun enableFromService(context: android.content.Context, kind: RawVitalsProbe.Kind): Boolean {
        if (kind == RawVitalsProbe.Kind.ECG) return false // ECG requires a visible user action.
        val intent = intentFor(kind)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun intentFor(kind: RawVitalsProbe.Kind): Intent? = when (kind) {
        RawVitalsProbe.Kind.HEART_RATE,
        RawVitalsProbe.Kind.MAX_HEART_RATE,
        RawVitalsProbe.Kind.PPG,
        RawVitalsProbe.Kind.LIFE_Q_OTT -> action("com.xiaomi.wear.fitness.start.heartrate")
        RawVitalsProbe.Kind.MI_SPO2,
        RawVitalsProbe.Kind.SPO2 -> action("com.xiaomi.wear.fitness.health.spo2.activity.MainActivity")
        RawVitalsProbe.Kind.ECG -> component(
            "com.xiaomi.wear.fitness.health.ecg.EcgMainTileActivity"
        )
        RawVitalsProbe.Kind.BIOZ -> component(
            "com.xiaomi.wear.fitness.health.bodycomposition.activity.BodyCompositionActivity"
        )
        RawVitalsProbe.Kind.BODY_TEMPERATURE,
        RawVitalsProbe.Kind.THERMOMETER,
        RawVitalsProbe.Kind.BOARD_TEMPERATURE -> component(
            "com.xiaomi.wear.fitness.health.temperature.TemperatureMainActivity"
        )
        else -> null
    }

    private fun action(action: String) = Intent(action).setPackage(OEM_PACKAGE).addCategory(Intent.CATEGORY_DEFAULT)

    private fun component(className: String) = Intent().setClassName(OEM_PACKAGE, className)

    private const val OEM_PACKAGE = "com.xiaomi.wear.fitness"
}
