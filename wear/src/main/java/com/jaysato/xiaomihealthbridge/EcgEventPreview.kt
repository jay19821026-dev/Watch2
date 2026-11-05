package com.jaysato.xiaomihealthbridge

internal object EcgEventPreview {
    fun firstField(rows: List<String>): List<Float> {
        val result = ArrayList<Float>()
        for (row in rows.takeLast(360)) {
            val delimiter = row.indexOf(',').let { if (it < 0) row.length else it }
            if (delimiter !in 1..48) return emptyList()
            val value = row.substring(0, delimiter).toFloatOrNull()?.takeIf { it.isFinite() }
                ?: return emptyList()
            result.add(value)
        }
        return result
    }
}
