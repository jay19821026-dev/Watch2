package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal object LegacyMigrationImporter {
    data class Summary(val sessions: Int, val rawSamples: Long, val derivedResults: Long, val profileImported: Boolean)
    private val uri = Uri.parse("content://${LegacyMigrationProvider.LEGACY_PACKAGE}.migration/export")

    suspend fun importFromInstalledLegacyApp(context: Context): Summary = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        check(app.packageName != LegacyMigrationProvider.LEGACY_PACKAGE) { "Open the renamed app to import." }
        val input = app.contentResolver.openInputStream(uri)
            ?: error("Legacy migration bridge is not installed. Keep the old app and install its bridge update first.")
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            val header = JSONObject(reader.readLine() ?: error("Empty migration stream."))
            check(header.getString("type") == "header" && header.getInt("format") == 1 &&
                header.getString("sourcePackage") == LegacyMigrationProvider.LEGACY_PACKAGE)
            val dao = SignalEvidenceDatabase.get(app).evidence()
            var profile: UserProfileStore.Profile? = null
            var session: SensorSessionEntity? = null
            val raw = ArrayList<RawSensorSampleEntity>()
            val derived = ArrayList<DerivedSignalEntity>()
            var sessionCount = 0
            var rawCount = 0L
            var derivedCount = 0L
            fun nullableLong(o: JSONObject, key: String): Long? = if (o.isNull(key)) null else o.getLong(key)
            suspend fun flushSession() {
                val current = session ?: return
                dao.appendOrVerifyRawSession(current, raw)
                for (result in derived) dao.appendOrVerifyDerived(result)
                sessionCount++
                rawCount += raw.size
                derivedCount += derived.size
                raw.clear(); derived.clear(); session = null
            }
            var ended = false
            while (true) {
                val text = reader.readLine() ?: break
                check(text.length <= 1_100_000) { "Oversize migration record." }
                val o = JSONObject(text)
                when (o.getString("type")) {
                    "profile" -> {
                        check(session == null && profile == null)
                        val candidate = UserProfileStore.Profile(o.getInt("age"),
                            PhysiologyAlgorithms.Sex.valueOf(o.getString("sex")),
                            o.getDouble("heightCm"), o.getDouble("weightKg"))
                        check(candidate.age in 13..100 && candidate.heightCm in 100.0..230.0 &&
                            candidate.weightKg in 25.0..350.0)
                        profile = candidate
                    }
                    "session" -> {
                        flushSession()
                        check(sessionCount < 200)
                        session = SensorSessionEntity(o.getString("sessionId"), o.getString("source"),
                            o.getLong("createdAt"), o.getString("metadataJson"))
                    }
                    "raw" -> {
                        val current = checkNotNull(session)
                        check(raw.size < 100_000 && o.getString("sessionId") == current.sessionId)
                        raw.add(RawSensorSampleEntity(current.sessionId, o.getInt("sampleIndex"),
                            o.getString("rawEncoding"), nullableLong(o, "sourceTimestampNanos"),
                            nullableLong(o, "callbackTimestampNanos"), o.getString("timestampSource"),
                            o.getInt("eventIndex"), o.getInt("channelIndex")))
                    }
                    "derived" -> {
                        val current = checkNotNull(session)
                        check(derived.size < 100_000 && o.getString("sessionId") == current.sessionId)
                        derived.add(DerivedSignalEntity(o.getString("resultId"), current.sessionId,
                            o.getString("resultType"), o.getString("algorithmVersion"),
                            if (o.isNull("modelVersion")) null else o.getString("modelVersion"),
                            o.getString("provenanceJson"), o.getString("payloadJson"), o.getLong("createdAt")))
                    }
                    "end" -> {
                        flushSession()
                        check(o.getInt("sessions") == sessionCount && o.getLong("raw") == rawCount &&
                            o.getLong("derived") == derivedCount && reader.readLine() == null)
                        ended = true
                        break
                    }
                    else -> error("Unsupported migration record.")
                }
            }
            check(ended) { "Migration stream was incomplete; old app data remains intact. Retry import." }
            val importedProfile = profile != null && UserProfileStore(app).read() == null
            if (importedProfile) UserProfileStore(app).save(checkNotNull(profile))
            Summary(sessionCount, rawCount, derivedCount, importedProfile)
        }
    }
}
