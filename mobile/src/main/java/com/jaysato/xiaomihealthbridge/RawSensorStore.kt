package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.content.SharedPreferences

internal object RawSensorStore {
    fun open(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(WearDataProcessor.RAW_SENSOR_PREFS, Context.MODE_PRIVATE)
}
