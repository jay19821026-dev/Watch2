package com.jaysato.xiaomihealthbridge

internal object BiozRawChannelEvidence {
    const val MAX_CHANNELS = 32
    private const val KEY = "raw_channel_values="

    fun encode(values: List<Double>): String = values.take(MAX_CHANNELS)
        .mapIndexed { index, value ->
            if (value.isFinite()) "$index:${value}" else "$index:unknown"
        }
        .joinToString("|")

    fun decode(detail: String?): List<Pair<Int, Double?>> {
        val encoded = detail?.substringAfter(KEY, missingDelimiterValue = "")
            ?.substringBefore(';')
            ?.substringBefore('\n')
            .orEmpty()
        if (encoded.isBlank() || encoded.length > 2048) return emptyList()
        return encoded.split('|').take(MAX_CHANNELS).mapNotNull { token ->
            val separator = token.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val index = token.substring(0, separator).toIntOrNull()?.takeIf { it in 0 until MAX_CHANNELS }
                ?: return@mapNotNull null
            val value = token.substring(separator + 1).toDoubleOrNull()?.takeIf(Double::isFinite)
            index to value
        }
    }
}
