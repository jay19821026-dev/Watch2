package com.jaysato.xiaomihealthbridge

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Entity(tableName = "signal_sessions")
data class SensorSessionEntity(
    @PrimaryKey val sessionId: String,
    val source: String,
    val createdAt: Long,
    val metadataJson: String
)

@Entity(
    tableName = "raw_signal_samples",
    primaryKeys = ["sessionId", "sampleIndex"],
    foreignKeys = [ForeignKey(entity = SensorSessionEntity::class, parentColumns = ["sessionId"], childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT)]
)
data class RawSensorSampleEntity(
    val sessionId: String,
    val sampleIndex: Int,
    val rawEncoding: String,
    val sourceTimestampNanos: Long?,
    val callbackTimestampNanos: Long?,
    val timestampSource: String,
    val eventIndex: Int,
    val channelIndex: Int
)

@Entity(
    tableName = "derived_signal_results",
    indices = [Index("sessionId")],
    foreignKeys = [ForeignKey(entity = SensorSessionEntity::class, parentColumns = ["sessionId"], childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT)]
)
data class DerivedSignalEntity(
    @PrimaryKey val resultId: String,
    val sessionId: String,
    val resultType: String,
    val algorithmVersion: String,
    val modelVersion: String?,
    val provenanceJson: String,
    val payloadJson: String,
    val createdAt: Long
)

@Dao
abstract class SignalEvidenceDao {
    @Query("SELECT COUNT(*) FROM signal_sessions")
    abstract suspend fun sessionCount(): Long

    @Query("SELECT * FROM signal_sessions ORDER BY sessionId LIMIT 201")
    abstract suspend fun readMigrationSessions(): List<SensorSessionEntity>

    @Query("SELECT COUNT(*) FROM raw_signal_samples")
    abstract suspend fun rawCount(): Long

    @Query("SELECT * FROM signal_sessions WHERE sessionId = :sessionId")
    abstract suspend fun readSession(sessionId: String): SensorSessionEntity?

    @Query("SELECT * FROM signal_sessions WHERE source = :source ORDER BY createdAt DESC, sessionId LIMIT 3")
    abstract suspend fun recentSessions(source: String): List<SensorSessionEntity>

    @Query("SELECT * FROM signal_sessions WHERE source = :source AND (EXISTS (SELECT 1 FROM raw_signal_samples WHERE raw_signal_samples.sessionId = signal_sessions.sessionId) OR instr(metadataJson, '\"raw_data_available\":true') > 0) ORDER BY createdAt DESC, sessionId LIMIT 1")
    abstract suspend fun latestSessionWithRawEvidence(source: String): SensorSessionEntity?

    @Query("SELECT * FROM raw_signal_samples WHERE sessionId = :sessionId ORDER BY sampleIndex LIMIT 256")
    abstract suspend fun readPreview(sessionId: String): List<RawSensorSampleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSession(session: SensorSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRaw(samples: List<RawSensorSampleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertDerived(results: List<DerivedSignalEntity>)

    @Query("SELECT * FROM raw_signal_samples WHERE sessionId = :sessionId ORDER BY sampleIndex")
    abstract suspend fun readRaw(sessionId: String): List<RawSensorSampleEntity>

    @Query("SELECT * FROM derived_signal_results WHERE sessionId = :sessionId ORDER BY resultId")
    abstract suspend fun readDerived(sessionId: String): List<DerivedSignalEntity>

    @Query("DELETE FROM derived_signal_results WHERE sessionId = :sessionId")
    abstract suspend fun deleteDerived(sessionId: String)

    @Query("DELETE FROM raw_signal_samples WHERE sessionId = :sessionId")
    protected abstract suspend fun deleteRaw(sessionId: String)

    @Query("DELETE FROM signal_sessions WHERE sessionId = :sessionId")
    protected abstract suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM derived_signal_results")
    protected abstract suspend fun deleteAllDerived()

    @Query("DELETE FROM raw_signal_samples")
    protected abstract suspend fun deleteAllRaw()

    @Query("DELETE FROM signal_sessions")
    protected abstract suspend fun deleteAllSessions()

    @Transaction
    open suspend fun deleteAllEvidence() {
        deleteAllDerived()
        deleteAllRaw()
        deleteAllSessions()
    }

    @Transaction
    open suspend fun deleteEvidenceSession(sessionId: String) {
        require(sessionId.isNotBlank())
        deleteDerived(sessionId)
        deleteRaw(sessionId)
        deleteSession(sessionId)
    }

    @Transaction
    open suspend fun appendOrVerifyRawSession(session: SensorSessionEntity, samples: List<RawSensorSampleEntity>) {
        require(samples.size <= 100_000)
        val existing = readSession(session.sessionId)
        if (existing == null) {
            appendRawSession(session, samples)
        } else {
            check(existing.source == session.source && existing.metadataJson == session.metadataJson &&
                readRaw(session.sessionId) == samples.sortedBy { it.sampleIndex }) { "Conflicting evidence for existing receipt." }
        }
    }

    @Transaction
    open suspend fun appendRawSession(session: SensorSessionEntity, samples: List<RawSensorSampleEntity>) {
        require(session.sessionId.isNotBlank() && session.source.isNotBlank() && session.createdAt >= 0)
        require(samples.size <= 100_000 && samples.all { it.sessionId == session.sessionId && it.sampleIndex >= 0 && it.rawEncoding.length <= 256 })
        require(samples.map { it.sampleIndex }.distinct().size == samples.size)
        check(sessionCount() < 200 && rawCount() + samples.size <= 1_000_000) { "Evidence capacity reached; explicit retention/export is required." }
        insertSession(session)
        insertRaw(samples)
    }

    @Transaction
    open suspend fun replaceDerived(sessionId: String, results: List<DerivedSignalEntity>) {
        require(results.size <= 100_000 && results.all {
            it.sessionId == sessionId && it.algorithmVersion.isNotBlank() && it.provenanceJson.isNotBlank() &&
                it.payloadJson.length <= 1_000_000 && it.createdAt >= 0
        })
        deleteDerived(sessionId)
        insertDerived(results)
    }

    @Transaction
    open suspend fun appendOrVerifyDerived(result: DerivedSignalEntity) {
        require(result.resultId.isNotBlank() && result.algorithmVersion.isNotBlank() &&
            result.provenanceJson.isNotBlank() && result.payloadJson.length <= 1_000_000 && result.createdAt >= 0L)
        check(readSession(result.sessionId) != null)
        val existing = readDerived(result.sessionId).firstOrNull { it.resultId == result.resultId }
        if (existing == null) insertDerived(listOf(result))
        else check(existing.copy(createdAt = result.createdAt) == result) { "Conflicting derived evidence." }
    }
}

@Database(entities = [SensorSessionEntity::class, RawSensorSampleEntity::class, DerivedSignalEntity::class], version = 1, exportSchema = false)
abstract class SignalEvidenceDatabase : RoomDatabase() {
    abstract fun evidence(): SignalEvidenceDao

    companion object {
        @Volatile private var instance: SignalEvidenceDatabase? = null
        private val initialization = Mutex()

        suspend fun get(context: Context): SignalEvidenceDatabase = instance ?: initialization.withLock {
            instance ?: withContext(NonCancellable + Dispatchers.IO) {
                val app = context.applicationContext
                val opened = EvidenceDatabaseCutover.open(app,
                    "ailisa-signal-evidence.db", "ailisa-signal-evidence-encrypted.db", "ailisa-evidence-cutover-v1.json",
                    { allowCreate -> EvidencePassphraseStore.load(app, allowCreate) })
                instance = opened.database
                app.getSharedPreferences("ailisa_archive_status", Context.MODE_PRIVATE).edit()
                    .putBoolean("evidence_encrypted", true)
                    .putBoolean("legacy_cleanup_complete", opened.legacyCleanupComplete)
                    .commit()
                opened.database
            }
        }
    }
}
