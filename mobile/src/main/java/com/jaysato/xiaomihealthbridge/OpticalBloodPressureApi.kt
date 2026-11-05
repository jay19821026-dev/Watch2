package com.jaysato.xiaomihealthbridge

/**
 * Blood-pressure category helpers for validated manual cuff readings only.
 */
object OpticalBloodPressureApi {
    const val DISCLAIMER = "本頁只分類已輸入的袖帶讀值，不能取代醫療診斷或專業建議。"

    enum class Category {
        NORMAL, ELEVATED, STAGE1, STAGE2, HYPERTENSIVE_CRISIS, LOW, UNKNOWN
    }

    fun categorize(systolic: Double, diastolic: Double): Category {
        if (!systolic.isFinite() || !diastolic.isFinite()) return Category.UNKNOWN
        if (systolic < 90 || diastolic < 60) return Category.LOW
        if (systolic > 180 || diastolic > 120) return Category.HYPERTENSIVE_CRISIS
        if (systolic >= 140 || diastolic >= 90) return Category.STAGE2
        if (systolic >= 130 || diastolic >= 80) return Category.STAGE1
        if (systolic >= 120 && diastolic < 80) return Category.ELEVATED
        if (systolic < 120 && diastolic < 80) return Category.NORMAL
        return Category.UNKNOWN
    }

    fun categoryLabel(c: Category) = when (c) {
        Category.NORMAL -> "正常"
        Category.ELEVATED -> "升高"
        Category.STAGE1 -> "高血壓第1期"
        Category.STAGE2 -> "高血壓第2期"
        Category.HYPERTENSIVE_CRISIS -> "高血壓危急"
        Category.LOW -> "偏低"
        Category.UNKNOWN -> "未知"
    }

}
