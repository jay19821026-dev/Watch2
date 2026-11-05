package com.jaysato.xiaomihealthbridge

import java.math.BigDecimal

internal object SensorSuiteCsvParser {
    private const val MAX_INPUT_CHARS = 262_144
    private const val MAX_TOKEN_CHARS = 48
    private const val MAX_ECG_TIMELINE_CHARS = 16_384
    private const val MAX_ECG_METADATA_CHARS = 65_536
    const val MAX_ECG_EVENT_ROW_CHARS = 32_768

    fun parse(csv: String, maxValues: Int = 8_192): List<Double> {
        if (csv.isEmpty() || csv.length > MAX_INPUT_CHARS || maxValues <= 0) return emptyList()
        val values = ArrayList<Double>(minOf(maxValues, 256))
        val end = csv.length
        var tokenStart = 0
        var index = 0
        while (index <= end) {
            if (index == end || csv[index] == ',') {
                var tokenEnd = index
                while (tokenEnd > tokenStart && csv[tokenEnd - 1].isWhitespace()) tokenEnd--
                var trimmedStart = tokenStart
                while (trimmedStart < tokenEnd && csv[trimmedStart].isWhitespace()) trimmedStart++
                if (tokenEnd > trimmedStart) {
                    if (tokenEnd - trimmedStart > MAX_TOKEN_CHARS) return emptyList()
                    val token = csv.substring(trimmedStart, tokenEnd)
                    if (!hasBoundedExponent(token)) return emptyList()
                    val value = runCatching {
                        val decimal = BigDecimal(token)
                        decimal.toDouble().takeUnless { decimal.signum() != 0 && it == 0.0 }
                    }.getOrNull()
                    if (value == null || !value.isFinite()) return emptyList()
                    if (values.size == maxValues) return emptyList()
                    values.add(value)
                }
                tokenStart = index + 1
            }
            index++
        }
        return values
    }

    private fun hasBoundedExponent(token: String): Boolean {
        val marker = token.indexOfAny(charArrayOf('e', 'E'))
        if (marker < 0) return true
        val exponent = token.substring(marker + 1).toIntOrNull() ?: return false
        return exponent in -400..400
    }

    fun parseFloats(csv: String, maxValues: Int = 8_192): List<Float> =
        parse(csv, maxValues).map { it.toFloat() }.takeIf { values -> values.all(Float::isFinite) }.orEmpty()

    fun parseFloatsWithGapMarkers(csv: String, maxValues: Int = 8_192): List<Float> {
        if (csv.isEmpty() || csv.length > MAX_INPUT_CHARS || maxValues <= 0) return emptyList()
        val tokens = csv.split(',')
        if (tokens.size > maxValues) return emptyList()
        return tokens.map { token ->
            if (token == "NaN") Float.NaN
            else parse(token).singleOrNull()?.toFloat()?.takeIf(Float::isFinite) ?: return emptyList()
        }
    }

    fun parseLongs(csv: String, maxValues: Int): List<Long> = parseIntegral(csv, maxValues, 20, MAX_ECG_TIMELINE_CHARS) { it.toLongOrNull() }

    fun parseInts(csv: String, maxValues: Int): List<Int> = parseIntegral(csv, maxValues, 11, MAX_ECG_TIMELINE_CHARS) { it.toIntOrNull() }

    fun parseRows(csv: String, maxRows: Int, maxRowChars: Int = MAX_TOKEN_CHARS * 16): List<String> {
        if (csv.isEmpty() || csv.length > MAX_ECG_METADATA_CHARS || maxRows <= 0 || maxRowChars <= 0) return emptyList()
        val rows = ArrayList<String>(minOf(maxRows, 512))
        var start = 0
        for (index in csv.indices) {
            if (csv[index] == ';') {
                if (index - start > maxRowChars) return emptyList()
                rows.add(csv.substring(start, index))
                if (rows.size >= maxRows) return emptyList()
                start = index + 1
            }
        }
        if (csv.length - start > maxRowChars) return emptyList()
        rows.add(csv.substring(start))
        return rows.takeIf { it.size <= maxRows }.orEmpty()
    }

    private fun <T : Any> parseIntegral(
        csv: String,
        maxValues: Int,
        maxTokenChars: Int,
        maxInputChars: Int,
        parseToken: (String) -> T?
    ): List<T> {
        if (csv.isEmpty() || csv.length > maxInputChars || maxValues <= 0) return emptyList()
        val values = ArrayList<T>(minOf(maxValues, 512))
        var tokenStart = 0
        for (index in 0..csv.length) {
            if (index == csv.length || csv[index] == ',') {
                if (index - tokenStart > maxTokenChars) return emptyList()
                if (index > tokenStart) values.add(parseToken(csv.substring(tokenStart, index)) ?: return emptyList())
                if (values.size > maxValues || (values.size == maxValues && index < csv.length)) return emptyList()
                tokenStart = index + 1
            }
        }
        return values
    }
}
