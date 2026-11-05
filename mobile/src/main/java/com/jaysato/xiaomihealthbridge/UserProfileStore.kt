package com.jaysato.xiaomihealthbridge

import android.content.Context

class UserProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("physiology_profile", Context.MODE_PRIVATE)
    data class Profile(val age: Int, val sex: PhysiologyAlgorithms.Sex, val heightCm: Double, val weightKg: Double)

    fun read(): Profile? {
        if (!prefs.contains("age")) return null
        return Profile(
            prefs.getInt("age", 0),
            runCatching { PhysiologyAlgorithms.Sex.valueOf(prefs.getString("sex", "UNSPECIFIED").orEmpty()) }.getOrDefault(PhysiologyAlgorithms.Sex.UNSPECIFIED),
            prefs.getFloat("height", 0f).toDouble(),
            prefs.getFloat("weight", 0f).toDouble()
        ).takeIf { it.age in 13..100 && it.heightCm in 100.0..230.0 && it.weightKg in 25.0..350.0 }
    }

    fun save(profile: Profile) {
        prefs.edit().putInt("age", profile.age).putString("sex", profile.sex.name)
            .putFloat("height", profile.heightCm.toFloat()).putFloat("weight", profile.weightKg.toFloat()).apply()
    }
}
