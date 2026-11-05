package com.jaysato.xiaomihealthbridge

import java.util.UUID

internal object RawSensorDataItemPath {
    fun create(kind: String, timestampMillis: Long, snapshotId: String = UUID.randomUUID().toString()): String =
        "/raw-sensor/$kind/$timestampMillis-$snapshotId"
}
