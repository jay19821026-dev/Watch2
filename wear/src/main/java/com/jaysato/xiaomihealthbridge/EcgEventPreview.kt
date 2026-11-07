package com.jaysato.xiaomihealthbridge

internal object EcgEventPreview {
    /** Research-only packet preview from the first ten fields of each observed ECG event. */
    fun oemPacketCandidates(rows: List<String>, contactStates: IntArray): List<Float> {
        if (rows.size != contactStates.size) return emptyList()
        var result = ArrayList<Float>()
        val offset = (rows.size - 50).coerceAtLeast(0)
        for (index in offset until rows.size) {
            val row = rows[index]
            if (row.length > 512) return emptyList()
            val fields = row.split(',')
            if (fields.size != 16) return emptyList()
            val values = fields.map { it.trim().toFloatOrNull()?.takeIf { value -> value.isFinite() } ?: return emptyList() }
            // OEM checks both the packet marker and a separate on-body state.
            if (values[10] == 1000f && contactStates[index] == 1) result.addAll(values.take(10))
            else result = ArrayList() // Do not draw continuity across missing/unknown contact.
        }
        return result
    }

    /** 1 = on-body, -1 = off-body, 0 = absent/stale/invalid; not electrode-contact proof. */
    fun contactAtEvent(eventTimestampNs: Long, contactTimestampNs: Long, onBody: Boolean?): Int {
        if (eventTimestampNs <= 0L || contactTimestampNs <= 0L || onBody == null) return 0
        val ageNs = eventTimestampNs - contactTimestampNs
        if (ageNs !in 0L..60_000_000_000L) return 0
        return if (onBody) 1 else -1
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
