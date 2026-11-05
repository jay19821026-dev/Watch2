package com.jaysato.xiaomihealthbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysato.xiaomihealthbridge.engine.EcgRawEventDecoder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EcgEventFieldResearchSerializationTest {
    @Test
    fun serializesIndexedFieldPreviewsWithExplicitUnverifiedProvenance() {
        val rows = (0 until 4).map { eventIndex ->
            (0 until EcgHalEventResearchDecoder.OBSERVED_ROW_WIDTH).map { fieldIndex ->
                if (fieldIndex == 15) 0.0 else fieldIndex * 10.0 + eventIndex
            }.joinToString(",")
        }.joinToString(";")
        val decoded = EcgHalEventResearchDecoder.decode(
            rows,
            listOf(1_000_000_000L, 1_020_000_000L, 1_040_000_000L, 1_060_000_000L)
        )
        val summary = requireNotNull(EcgEventFieldResearch.summarize(decoded))
        val record = requireNotNull(EcgEventFieldResearch.toDerivedRecord(
            sessionId = "session-1",
            source = "wear-data-layer-ecg-preview",
            sourceTimestampSource = "android_sensor_event_elapsed_realtime",
            sourceTimestampMillis = 1_700_000_000_000L,
            publishedAtMillis = 1_700_000_000_100L,
            sourceSnapshotRows = rows,
            summary = summary,
            createdAt = 123L
        ))
        val provenance = JSONObject(record.provenanceJson)
        val payload = JSONObject(record.payloadJson)
        val traces = payload.getJSONArray("field_relative_preview")

        assertEquals("ecg-event-fields-relative-index-v3", record.algorithmVersion)
        assertEquals(16, payload.getInt("field_count"))
        assertEquals(4, payload.getInt("event_count"))
        assertEquals(64, payload.getInt("inspected_field_value_count"))
        assertEquals(0, payload.getInt("decoded_ecg_sample_count"))
        assertEquals("UNVERIFIED", provenance.getString("event_field_mapping"))
        assertEquals("NORMALIZED_EVENT_INDEX", provenance.getString("timestamp_source"))
        assertEquals("android_sensor_event_elapsed_realtime", provenance.getString("source_timestamp_source"))
        assertEquals(record.resultId.substringAfterLast(':'), provenance.getString("source_snapshot_sha256"))
        assertTrue(provenance.isNull("sample_rate_hz"))
        assertEquals("UNVERIFIED", provenance.getString("calibration_status"))
        assertFalse(provenance.getBoolean("clinical_use"))
        assertEquals(EcgRawEventDecoder.MAX_PREVIEW_POINTS, payload.getInt("preview_point_limit"))
        assertEquals(16, traces.length())
        assertEquals(4, traces.getJSONObject(0).getJSONArray("relative_amplitude_0_to_1").length())
        assertEquals(4, traces.getJSONObject(15).getInt("zero_value_count"))
        assertEquals(0.5, traces.getJSONObject(15).getJSONArray("relative_amplitude_0_to_1").getDouble(0), 0.0)
        assertEquals(123L, record.createdAt)
    }

    @Test
    fun snapshotBasedIdsAreStableForReplayAndChangeWhenRawRowsGrow() {
        val firstSnapshot = (0 until 4).joinToString(";") { eventIndex ->
            (0 until EcgHalEventResearchDecoder.OBSERVED_ROW_WIDTH).joinToString(",") { fieldIndex ->
                (fieldIndex * 10 + eventIndex).toString()
            }
        }

        fun record(rows: String): DerivedSignalEntity {
            val decoded = EcgHalEventResearchDecoder.decode(rows, emptyList())
            val summary = requireNotNull(EcgEventFieldResearch.summarize(decoded))
            return requireNotNull(EcgEventFieldResearch.toDerivedRecord(
                sessionId = "same-session",
                source = "wear-data-layer-ecg-preview",
                sourceTimestampSource = "UNKNOWN",
                sourceTimestampMillis = null,
                publishedAtMillis = null,
                sourceSnapshotRows = rows,
                summary = summary,
                createdAt = 1L
            ))
        }

        val original = record(firstSnapshot)
        val replay = record(firstSnapshot)
        val expanded = record("$firstSnapshot;${firstSnapshot.substringAfterLast(';')}")

        assertEquals(original.resultId, replay.resultId)
        assertEquals(original.provenanceJson, replay.provenanceJson)
        assertTrue(original.resultId != expanded.resultId)
    }
}
