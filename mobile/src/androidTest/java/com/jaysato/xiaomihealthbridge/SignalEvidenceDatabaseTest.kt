package com.jaysato.xiaomihealthbridge

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jaysato.xiaomihealthbridge.engine.PpgCompositionQualityEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignalEvidenceDatabaseTest {
    @Test fun archiveStatisticsUseValuesCapturedBeforeDatabaseSuspension() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = mutableListOf(1f, Float.NaN, 3f)
        val mutated = java.util.concurrent.atomic.AtomicBoolean(false)
        val database = Room.inMemoryDatabaseBuilder(context, SignalEvidenceDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if (sql.contains("INSERT", ignoreCase = true) && sql.contains("raw_signal_samples")) {
                    values.clear()
                    mutated.set(true)
                }
            }, java.util.concurrent.Executor { command -> command.run() })
            .build()
        try {
            PpgEvidenceArchive.appendPreview(context, "mutable", values, null, 0, null,
                1, 3, 3, emptyList(), emptyList(), dao = database.evidence())
            assertTrue(mutated.get())
            val sessionId = "ppg-preview-v4:mutable"
            assertEquals(listOf("1.0", "NaN", "3.0"), database.evidence().readRaw(sessionId).map { it.rawEncoding })
            val statistics = org.json.JSONObject(database.evidence().readDerived(sessionId).single().payloadJson)
            assertEquals(3, statistics.getInt("preview_count"))
            assertEquals(2, statistics.getInt("finite_count"))
            assertEquals(2.0, statistics.getDouble("mean_raw_units"), 0.0)
            assertEquals(1.0, statistics.getDouble("standard_deviation_raw_units"), 0.0)
            assertEquals("0..1 per preview", statistics.getString("relative_amplitude_range"))
            assertEquals("LOW", statistics.getString("descriptive_support"))
            assertEquals(ResearchSignalSummary.VERSION, statistics.getString("algorithm_version"))
        } finally { database.close() }
    }

    @Test fun eventCountDoesNotSubstituteForArchivedRawPayload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SignalEvidenceDatabase::class.java).build()
        try {
            listOf("", "NaN,-0.0;Infinity").forEachIndexed { index, rawRows ->
                PpgEvidenceArchive.appendPreview(context, "availability-$index", emptyList(), null, 0, null,
                    2, null, 0, emptyList(), emptyList(), dao = database.evidence(), rawEventRows = rawRows)
                val metadata = org.json.JSONObject(database.evidence().readSession("ppg-preview-v4:availability-$index")!!.metadataJson)
                assertEquals(rawRows.isNotEmpty(), metadata.getBoolean("raw_data_available"))
                assertEquals(rawRows, metadata.getString("event_raw_values_rows"))
            }
        } finally { database.close() }
    }

    @Test fun archiveReplayPreservesRawAndRejectsChangedProvenance() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SignalEvidenceDatabase::class.java).build()
        try {
            suspend fun append(flags: String = "SOURCE_TIME_UNVERIFIED", timestamps: List<Long> = listOf(9, 7)) {
                PpgEvidenceArchive.appendPreview(context, "provenance", listOf(-0.0f, Float.NaN, Float.POSITIVE_INFINITY),
                    "unverified", 100, "callback", 2, 8, 3, timestamps, listOf(4, 4), dao = database.evidence(),
                    kind = "ecg", publishedAt = 200, evidenceFlags = flags, evidenceFlagsVersion = 1,
                    callbackTimestamps = listOf(11, 10), rawEventRows = "-0.0,NaN;Infinity")
            }
            append()
            val sessionId = "ecg-preview-v4:provenance"
            val session = database.evidence().readSession(sessionId)
            val raw = database.evidence().readRaw(sessionId)
            val derived = database.evidence().readDerived(sessionId)
            val metadata = org.json.JSONObject(session!!.metadataJson)
            assertEquals("SOURCE_TIME_UNVERIFIED", metadata.getString("evidence_flags"))
            assertEquals(1, metadata.getInt("evidence_flags_version"))
            assertEquals("[9,7]", metadata.getJSONArray("event_timestamps_ns").toString())
            assertEquals("[4,4]", metadata.getJSONArray("event_sample_counts").toString())
            assertEquals("[11,10]", metadata.getJSONArray("event_callback_elapsed_realtime_ns").toString())
            assertEquals(100L, metadata.getLong("source_reported_timestamp_ms"))
            assertEquals(200L, metadata.getLong("published_at_ms"))
            assertFalse(metadata.getBoolean("source_time_available"))
            append()
            assertEquals(session, database.evidence().readSession(sessionId))
            assertEquals(derived, database.evidence().readDerived(sessionId))
            assertEquals(listOf("-0.0", "NaN", "Infinity"), raw.map { it.rawEncoding })
            assertTrue(raw.all { it.eventIndex == -1 && it.sourceTimestampNanos == null && it.callbackTimestampNanos == null })
            for (changeFlags in listOf(true, false)) {
                var rejected = false
                try {
                    if (changeFlags) append(flags = "SOURCE_TIME_VERIFIED") else append(timestamps = listOf(7, 9))
                } catch (_: IllegalStateException) { rejected = true }
                assertTrue(rejected)
            }
            assertEquals(session, database.evidence().readSession(sessionId))
            assertEquals(raw, database.evidence().readRaw(sessionId))
            assertEquals(derived, database.evidence().readDerived(sessionId))
        } finally { database.close() }
    }

    @Test fun archiveRejectsOversizedMetadataBeforePersistingAnyEvidence() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SignalEvidenceDatabase::class.java).build()
        try {
            for (rawRows in listOf("a".repeat(512_001), "\u0001".repeat(200_000))) {
                var rejected = false
                try {
                    PpgEvidenceArchive.appendPreview(context, "oversized", listOf(1f), null, 0, null,
                        1, 1, 1, emptyList(), emptyList(), dao = database.evidence(), rawEventRows = rawRows)
                } catch (_: IllegalArgumentException) { rejected = true }
                assertTrue(rejected)
                assertEquals(0L, database.evidence().sessionCount())
                assertEquals(0L, database.evidence().rawCount())
                assertTrue(database.evidence().readDerived("ppg-preview-v4:oversized").isEmpty())
            }
        } finally { database.close() }
    }

    @Test fun failedEvidenceDeletionRollsBackAllTablesAndCanRetry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SignalEvidenceDatabase::class.java).build()
        try {
            val dao = database.evidence()
            val session = SensorSessionEntity("rollback-delete", "test-only", 1, "{}")
            val raw = listOf(RawSensorSampleEntity(session.sessionId, 0, "NaN", null, null, "UNKNOWN", -1, -1))
            val derived = listOf(DerivedSignalEntity("rollback-result", session.sessionId, "test", "v1", null, "{}", "[]", 2))
            dao.appendRawSession(session, raw)
            dao.replaceDerived(session.sessionId, derived)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                database.openHelper.writableDatabase.execSQL(
                    "CREATE TEMP TRIGGER reject_session_delete BEFORE DELETE ON signal_sessions BEGIN SELECT RAISE(ABORT, 'test deletion failure'); END")
            }
            var rejected = false
            try { dao.deleteAllEvidence() }
            catch (_: android.database.sqlite.SQLiteConstraintException) { rejected = true }
            assertTrue(rejected)
            assertEquals(session, dao.readSession(session.sessionId))
            assertEquals(raw, dao.readRaw(session.sessionId))
            assertEquals(derived, dao.readDerived(session.sessionId))
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_session_delete")
            }
            dao.deleteAllEvidence()
            assertEquals(0L, dao.sessionCount())
            dao.appendRawSession(session.copy(sessionId = "new-session"),
                raw.map { it.copy(sessionId = "new-session") })
            assertEquals(1L, dao.sessionCount())
        } finally { database.close() }
    }

    @Test fun clearAllEvidenceRemovesRawDerivedAndSessionTogether() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SignalEvidenceDatabase::class.java).build()
        try {
            val dao = database.evidence()
            dao.appendRawSession(SensorSessionEntity("clear-all", "test-only", 1, "{}"),
                listOf(RawSensorSampleEntity("clear-all", 0, "NaN", null, null, "UNKNOWN", -1, -1)))
            dao.replaceDerived("clear-all", listOf(DerivedSignalEntity("result", "clear-all", "test", "v1", null, "{}", "[]", 2)))
            dao.deleteAllEvidence()
            dao.deleteAllEvidence()
            assertEquals(0L, dao.sessionCount())
            assertEquals(0L, dao.rawCount())
            assertTrue(dao.readDerived("clear-all").isEmpty())
        } finally { database.close() }
    }

    @Test fun explicitEvidenceDeletionRemovesOnlySelectedSessionAcrossReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "evidence-delete-test-${java.util.UUID.randomUUID()}.db"
        try {
            val first = Room.databaseBuilder(context, SignalEvidenceDatabase::class.java, name).build()
            try {
                listOf("delete", "keep").forEach { sessionId ->
                    first.evidence().appendRawSession(
                        SensorSessionEntity(sessionId, "test-only", 1, "{}"),
                        listOf(RawSensorSampleEntity(sessionId, 0, "NaN", null, null, "UNKNOWN", -1, -1)))
                    first.evidence().replaceDerived(sessionId,
                        listOf(DerivedSignalEntity("result-$sessionId", sessionId, "test", "v1", null, "{}", "[]", 2)))
                }
                first.evidence().deleteEvidenceSession("delete")
                first.evidence().deleteEvidenceSession("delete")
            } finally { first.close() }
            val reopened = Room.databaseBuilder(context, SignalEvidenceDatabase::class.java, name).build()
            try {
                assertNull(reopened.evidence().readSession("delete"))
                assertTrue(reopened.evidence().readRaw("delete").isEmpty())
                assertTrue(reopened.evidence().readDerived("delete").isEmpty())
                assertNotNull(reopened.evidence().readSession("keep"))
                assertEquals(1, reopened.evidence().readRaw("keep").size)
                assertEquals(1, reopened.evidence().readDerived("keep").size)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun fileDatabaseReopenPreservesRawAndDerivedDeletionIsIsolated() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "evidence-reopen-test-${java.util.UUID.randomUUID()}.db"
        val session = SensorSessionEntity("reopen", "test-only", 1, "{}")
        val raw = listOf(RawSensorSampleEntity("reopen", 0, "NaN", null, null, "UNKNOWN", -1, -1))
        val derived = DerivedSignalEntity("reopen-result", "reopen", "FULL_COVERAGE", "v1", "test-model", "{}", "[]", 2)
        try {
            val first = Room.databaseBuilder(context, SignalEvidenceDatabase::class.java, name).build()
            try {
                first.evidence().appendOrVerifyRawSession(session, raw)
                first.evidence().replaceDerived("reopen", listOf(derived))
            } finally { first.close() }
            val reopened = Room.databaseBuilder(context, SignalEvidenceDatabase::class.java, name).build()
            try {
                assertEquals(session, reopened.evidence().readSession("reopen"))
                assertEquals(raw, reopened.evidence().readRaw("reopen"))
                assertEquals(listOf(derived), reopened.evidence().readDerived("reopen"))
                reopened.evidence().deleteDerived("reopen")
            } finally { reopened.close() }
            val finalRead = Room.databaseBuilder(context, SignalEvidenceDatabase::class.java, name).build()
            try {
                assertEquals(raw, finalRead.evidence().readRaw("reopen"))
                assertTrue(finalRead.evidence().readDerived("reopen").isEmpty())
            } finally { finalRead.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun failedDerivedReplacementRollsBackWithoutDeletingEvidence() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SignalEvidenceDatabase::class.java).build()
        try {
            val dao = database.evidence()
            val raw = listOf(RawSensorSampleEntity("rollback", 0, "NaN", null, null, "UNKNOWN", -1, -1))
            dao.appendRawSession(SensorSessionEntity("rollback", "test-only", 1, "{}"), raw)
            val previous = DerivedSignalEntity("previous", "rollback", "FULL_COVERAGE", "v1", null, "{}", "[]", 2)
            dao.replaceDerived("rollback", listOf(previous))
            val replacement = previous.copy(resultId = "duplicate", algorithmVersion = "v2")
            var rejected = false
            try { dao.replaceDerived("rollback", listOf(replacement, replacement)) }
            catch (_: android.database.sqlite.SQLiteConstraintException) { rejected = true }
            assertTrue(rejected)
            assertEquals(listOf(previous), dao.readDerived("rollback"))
            assertEquals(raw, dao.readRaw("rollback"))
        } finally { database.close() }
    }

    @Test fun replayIsIdempotentAndConflictCannotOverwriteRaw() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SignalEvidenceDatabase::class.java).build()
        try {
            val dao = database.evidence()
            val session = SensorSessionEntity("replay", "test-only", 1, "{}")
            val raw = listOf(RawSensorSampleEntity("replay", 0, "NaN", null, null, "UNKNOWN", -1, -1))
            dao.appendOrVerifyRawSession(session, raw)
            dao.appendOrVerifyRawSession(session.copy(createdAt = 2), raw)
            assertEquals(1L, dao.sessionCount())
            assertEquals(1L, dao.rawCount())
            assertEquals(1L, dao.readSession("replay")!!.createdAt)
            var rejected = false
            try { dao.appendOrVerifyRawSession(session, listOf(raw.single().copy(rawEncoding = "3.0"))) }
            catch (_: IllegalStateException) { rejected = true }
            assertTrue(rejected)
            assertEquals(raw, dao.readRaw("replay"))
        } finally { database.close() }
    }

    @Test fun ppgArchivePreservesUnknownTimingAndPreviewCountSemantics() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SignalEvidenceDatabase::class.java).build()
        try {
            PpgEvidenceArchive.appendPreview(context, "test-receipt", listOf(1f, Float.NaN, 3f), "unverified", 100, "callback", 2, 20, 3, listOf(0, 0), listOf(10, 10), dao = database.evidence())
            val rows = database.evidence().readRaw("ppg-preview-v4:test-receipt")
            assertEquals(3, rows.size)
            assertEquals("NaN", rows[1].rawEncoding)
            assertTrue(rows.all { it.sourceTimestampNanos == null && it.timestampSource == "UNKNOWN" && it.channelIndex == -1 })
        } finally {
            database.close()
        }
    }

    @Test fun biozFlagsPersistWithoutTurningPublicationIntoMeasurementTime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SignalEvidenceDatabase::class.java).build()
        try {
            repeat(2) {
                PpgEvidenceArchive.appendPreview(context, "bioz-flags", listOf(1f, 2f), "unverified",
                    0, "sensor_event_timestamp_missing", 1, 16, 2, listOf(0), listOf(16),
                    dao = database.evidence(), kind = "bioz", publishedAt = 2000,
                    reportedRate = 0.0, evidenceFlags = "EVENTS_RECEIVED,SOURCE_TIME_UNVERIFIED",
                    evidenceFlagsVersion = 1, callbackTimestamps = listOf(1234),
                    rawEventRows = "1.0,2.0", sourceSessionStartedAt = 1000)
            }
            val session = database.evidence().readSession("bioz-preview-v4:bioz-flags")!!
            val metadata = org.json.JSONObject(session.metadataJson)
            assertTrue(metadata.getBoolean("raw_data_available"))
            assertFalse(metadata.getBoolean("source_time_available"))
            assertTrue(metadata.getBoolean("publication_time_available"))
            assertFalse(metadata.getBoolean("reported_rate_available"))
            assertEquals(0L, metadata.getLong("source_reported_timestamp_ms"))
            assertEquals(2000L, metadata.getLong("published_at_ms"))
            assertEquals(1234L, metadata.getJSONArray("event_callback_elapsed_realtime_ns").getLong(0))
            assertEquals("1.0,2.0", metadata.getString("event_raw_values_rows"))
            assertEquals(1L, database.evidence().sessionCount())
            assertEquals(2L, database.evidence().rawCount())
            val derived = database.evidence().readDerived(session.sessionId).single()
            val statistics = org.json.JSONObject(derived.payloadJson)
            assertEquals(1.5, statistics.getDouble("mean_raw_units"), 0.0001)
            assertEquals("FAIL", statistics.getString("physiological_conversion"))
            database.evidence().deleteDerived(session.sessionId)
            assertEquals(2L, database.evidence().rawCount())
        } finally { database.close() }
    }

    @Test fun rawSurvivesDerivedDeletionAndDuplicateRawIsRejected() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SignalEvidenceDatabase::class.java).build()
        try {
            val dao = database.evidence()
            val raw = listOf(RawSensorSampleEntity("test-session", 0, "null", null, 10, "UNKNOWN", 0, 0))
            dao.appendRawSession(SensorSessionEntity("test-session", "test-only", 1, "{}"), raw)
            val derived = DerivedSignalEntity("result", "test-session", "FULL_COVERAGE", "v1", "test-model", "{}", "[]", 2)
            dao.replaceDerived("test-session", listOf(derived))
            assertEquals("test-model", dao.readDerived("test-session").single().modelVersion)
            dao.deleteDerived("test-session")
            assertEquals(raw, dao.readRaw("test-session"))
            assertTrue(dao.readDerived("test-session").isEmpty())
            var rejected = false
            try { dao.insertRaw(raw) } catch (_: android.database.sqlite.SQLiteConstraintException) { rejected = true }
            assertTrue(rejected)
            assertEquals(raw, dao.readRaw("test-session"))
        } finally {
            database.close()
        }
    }

    @Test fun biozRelativeFieldResearchPersistsAcrossDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "bioz-derived-reopen-${java.util.UUID.randomUUID()}.db"
        val sessionId = "bioz-preview-v4:derived-reopen"
        val rawRows = "10,100;20,200;30,300;40,400"
        val rawSample = RawSensorSampleEntity(sessionId, 0, "10,100", null, 1234L, "UNKNOWN", 0, -1)
        val session = SensorSessionEntity(sessionId, "wear-data-layer-bioz-preview", 1000L,
            org.json.JSONObject().put("event_raw_values_rows", rawRows).toString())
        val analysis = BiozRawFieldResearch.analyze(rawRows, session.source)!!
        val derived = BiozRawFieldResearch.toDerivedRecord(
            sessionId = sessionId,
            source = session.source,
            timestampSource = "UNKNOWN",
            sourceTimestampMillis = null,
            publishedAtMillis = 2000L,
            result = analysis,
            createdAt = 3000L
        )
        try {
            val first = Room.databaseBuilder(context, SignalEvidenceDatabase::class.java, name).build()
            try {
                first.evidence().appendRawSession(session, listOf(rawSample))
                first.evidence().appendOrVerifyDerived(derived)
            } finally { first.close() }

            val reopened = Room.databaseBuilder(context, SignalEvidenceDatabase::class.java, name).build()
            try {
                assertEquals(listOf(rawSample), reopened.evidence().readRaw(sessionId))
                val stored = reopened.evidence().readDerived(sessionId).single()
                assertEquals(derived, stored)
                val provenance = org.json.JSONObject(stored.provenanceJson)
                assertEquals("UNVERIFIED", provenance.getString("field_mapping"))
                assertEquals("NORMALIZED_EVENT_INDEX", provenance.getString("timeline"))
                assertEquals("UNKNOWN", provenance.getString("timestamp_source"))
                assertFalse(provenance.getBoolean("clinical_use"))
                assertTrue(provenance.isNull("sample_rate_hz"))
                val payload = org.json.JSONObject(stored.payloadJson)
                assertEquals(4, payload.getInt("event_count"))
                assertEquals(8, payload.getInt("finite_value_count"))
                assertEquals(2, payload.getJSONArray("fields").length())
                assertEquals(BiozRawFieldResearch.ALGORITHM_VERSION, stored.algorithmVersion)
                reopened.evidence().appendOrVerifyDerived(derived.copy(createdAt = 4000L))
                assertEquals(1, reopened.evidence().readDerived(sessionId).size)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun spo2AndTemperatureRelativePreviewResearchPersistsWithoutChangingRawEvidence() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "relative-preview-${java.util.UUID.randomUUID()}.db"
        var database = Room.databaseBuilder(context, SignalEvidenceDatabase::class.java, databaseName).build()
        try {
            val cases = listOf(
                Triple("spo2_raw", listOf(0.0, 4.0, null), "spo2-preview-v4:research"),
                Triple("thermometer", listOf(33.2, 33.3), "thermometer-preview-v4:research")
            )
            for ((caseIndex, case) in cases.withIndex()) {
                val (kind, values, sessionId) = case
                val dao = database.evidence()
                val source = "wear-data-layer-$kind-preview"
                val session = SensorSessionEntity(sessionId, source, 1_000L, "{}")
                val raw = values.mapIndexed { index, value ->
                    RawSensorSampleEntity(sessionId, index, value?.toString() ?: "null", null, null, "UNKNOWN", index, -1)
                }
                dao.appendRawSession(session, raw)
                val summary = ResearchSignalSummary.analyze(
                    values,
                    mappedOrderedWaveform = false,
                    preserveInputOrder = true,
                    missingSentinels = if (kind == "spo2_raw") setOf(PpgCompositionQualityEngine.GAP_SENTINEL) else emptySet()
                )
                val previewResearch = RawPreviewResearch.summarize(kind, source, summary)
                val derived = RawPreviewResearch.toDerivedRecord(
                    sessionId = sessionId,
                    source = source,
                    timestampSource = "UNKNOWN",
                    sourceMeasurementTimestampMillis = null,
                    publishedAtMillis = null,
                    result = previewResearch,
                    createdAt = 2_000L
                )
                dao.appendOrVerifyDerived(derived)

                if (caseIndex == 0) {
                    database.close()
                    database = Room.databaseBuilder(context, SignalEvidenceDatabase::class.java, databaseName).build()
                }
                val reopenedDao = database.evidence()
                val stored = reopenedDao.readDerived(sessionId).single()
                assertEquals(derived, stored)
                assertEquals(raw, reopenedDao.readRaw(sessionId))
                val provenance = org.json.JSONObject(stored.provenanceJson)
                assertEquals("RELATIVE_INDEX_DERIVED_FROM_RECEIVED_PREVIEW", provenance.getString("origin"))
                assertEquals("NORMALIZED_PREVIEW_INDEX", provenance.getString("timeline"))
                assertTrue(provenance.isNull("sample_rate_hz"))
                assertEquals("UNVERIFIED", provenance.getString("calibration_status"))
                assertFalse(provenance.getBoolean("clinical_use"))
                val payload = org.json.JSONObject(stored.payloadJson)
                assertEquals(values.size, payload.getInt("preview_point_count"))
                assertTrue(payload.isNull("accuracy_or_clinical_confidence"))
                if (kind == "spo2_raw") {
                    assertEquals(1, payload.getInt("zero_finite_count"))
                    assertTrue(payload.getJSONArray("relative_amplitude_0_to_1").isNull(2))
                    assertEquals("PRESENT_IN_RECEIVED_PREVIEW", payload.getJSONArray("preview_point_origin_labels").getString(0))
                    assertEquals("UNKNOWN_OR_GAP", payload.getJSONArray("preview_point_origin_labels").getString(2))
                }
                reopenedDao.appendOrVerifyDerived(derived.copy(createdAt = 2_500L))
                assertEquals(1, reopenedDao.readDerived(sessionId).size)
                var conflictingReplayRejected = false
                try {
                    reopenedDao.appendOrVerifyDerived(derived.copy(algorithmVersion = "conflicting-version"))
                } catch (_: IllegalStateException) {
                    conflictingReplayRejected = true
                }
                assertTrue(conflictingReplayRejected)
                assertEquals(raw, reopenedDao.readRaw(sessionId))
            }
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
