package com.jaysato.xiaomihealthbridge

internal object RawPacketChannelDecoder {
    data class Result(
        val channels: List<List<Double>>,
        val eventCount: Int,
        val status: String,
        val receivedEventCount: Int = eventCount
    )

    fun decode(rawRows: String): Result {
        val rows = SensorSuiteCsvParser.parseRows(rawRows, EcgEventRowExportPolicy.MAX_EVENT_ROWS,
            SensorSuiteCsvParser.MAX_ECG_EVENT_ROW_CHARS)
        if (rows.isEmpty()) return Result(emptyList(), 0, "no_rows")
        val packets = rows.map { parsePacket(it) }
        val width = packets.first().size
        if (width == 0 || packets.any { it.size != width || it.any(Double::isInfinite) }) {
            return Result(emptyList(), 0, "inconsistent_or_invalid_packet", rows.size)
        }
        return Result(List(width) { channel -> packets.map { it[channel] } }, packets.size,
            "structured_raw_fields_unmapped")
    }

    private fun parsePacket(row: String): List<Double> {
        if (row.isEmpty()) return emptyList()
        val values = ArrayList<Double>()
        var tokenStart = 0
        for (index in 0..row.length) {
            if (index == row.length || row[index] == ',') {
                if (index == tokenStart || values.size == 64) return emptyList()
                val token = row.substring(tokenStart, index)
                val value = if (token == "NaN") Double.NaN else
                    SensorSuiteCsvParser.parse(token, 1).singleOrNull() ?: return emptyList()
                values += value
                tokenStart = index + 1
            }
        }
        return values
    }
}
