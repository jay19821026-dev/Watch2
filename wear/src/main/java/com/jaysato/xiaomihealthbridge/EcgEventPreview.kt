package com.jaysato.xiaomihealthbridge

internal object EcgEventPreview {
    /** Research-only packet preview from the first ten fields of each observed ECG event. */
    fun oemPacketCandidates(rows: List<String>): List<Float> {
        var result = ArrayList<Float>()
        for (row in rows.takeLast(50)) {
            if (row.length > 512) return emptyList()
            val fields = row.split(',')
            if (fields.size != 16) return emptyList()
            val values = fields.map { it.trim().toFloatOrNull()?.takeIf { value -> value.isFinite() } ?: return emptyList() }
            // The installed OEM app checks field 10 == 1000 together with a separate off-body sensor.
            // We do not have the concurrent off-body gate here, so this marker is not proof of contact.
            if (values[10] == 1000f) result.addAll(values.take(10))
            else result = ArrayList() // Do not draw continuity across a missing contact marker.
        }
        return result
    }

    fun oemMarkerCount(rows: List<String>): Int = rows.count { row ->
        if (row.length > 512) return@count false
        val fields = row.split(',')
        fields.size == 16 && fields[10].trim().toFloatOrNull() == 1000f
    }

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
