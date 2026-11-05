package com.jaysato.xiaomihealthbridge

object ResearchNumericAdapter {
    fun finiteDouble(value: Number?): Double? = value?.toDouble()?.takeIf { it.isFinite() }

    fun finiteFloat(value: Number?): Float? = finiteDouble(value)?.toFloat()?.takeIf { it.isFinite() }

    fun boundedDouble(value: Number?, minimum: Double, maximum: Double): Double? =
        finiteDouble(value)?.takeIf { it in minimum..maximum }
}
