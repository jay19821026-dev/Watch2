package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.RawSensorCountContract as SharedRawSensorCountContract

internal object RawSensorCountContract {
    const val VERSION = SharedRawSensorCountContract.VERSION

    fun fields(
        samples: Int,
        rawValueCount: Int?,
        previewPointCount: Int,
        eventCount: Int
    ): Map<String, Int> = SharedRawSensorCountContract.encode(
        samples = samples,
        rawValueCount = rawValueCount,
        previewPointCount = previewPointCount,
        eventCount = eventCount
    )
}
