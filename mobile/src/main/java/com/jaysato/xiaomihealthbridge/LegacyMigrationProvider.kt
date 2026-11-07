package com.jaysato.xiaomihealthbridge

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter

/** Owner-initiated, same-signature pipe from the old debug package to the renamed app. */
class LegacyMigrationProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r" && uri.path == "/export")
        val app = checkNotNull(context).applicationContext
        check(app.packageName == LEGACY_PACKAGE) { "Only the legacy package can export." }
        val pipe = ParcelFileDescriptor.createPipe()
        Thread({
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).bufferedWriter(Charsets.UTF_8).use { out ->
                runBlocking { writeArchive(app, out) }
            }
        }, "ailisa-legacy-migration").start()
        return pipe[0]
    }

    private suspend fun writeArchive(app: android.content.Context, out: BufferedWriter) {
        fun line(value: JSONObject) { out.write(value.toString()); out.newLine() }
        line(JSONObject().put("type", "header").put("format", 1).put("sourcePackage", LEGACY_PACKAGE))
        UserProfileStore(app).read()?.let { p ->
            line(JSONObject().put("type", "profile").put("age", p.age).put("sex", p.sex.name)
                .put("heightCm", p.heightCm).put("weightKg", p.weightKg))
        }
        val dao = SignalEvidenceDatabase.get(app).evidence()
        val sessions = dao.readMigrationSessions()
        check(sessions.size <= 200 && dao.sessionCount() == sessions.size.toLong())
        var rawCount = 0L
        var derivedCount = 0L
        for (session in sessions) {
            line(JSONObject().put("type", "session").put("sessionId", session.sessionId)
                .put("source", session.source).put("createdAt", session.createdAt)
                .put("metadataJson", session.metadataJson))
            for (sample in dao.readRaw(session.sessionId)) {
                line(JSONObject().put("type", "raw").put("sessionId", sample.sessionId)
                    .put("sampleIndex", sample.sampleIndex).put("rawEncoding", sample.rawEncoding)
                    .put("sourceTimestampNanos", sample.sourceTimestampNanos)
                    .put("callbackTimestampNanos", sample.callbackTimestampNanos)
                    .put("timestampSource", sample.timestampSource).put("eventIndex", sample.eventIndex)
                    .put("channelIndex", sample.channelIndex))
                rawCount++
            }
            for (result in dao.readDerived(session.sessionId)) {
                line(JSONObject().put("type", "derived").put("resultId", result.resultId)
                    .put("sessionId", result.sessionId).put("resultType", result.resultType)
                    .put("algorithmVersion", result.algorithmVersion).put("modelVersion", result.modelVersion)
                    .put("provenanceJson", result.provenanceJson).put("payloadJson", result.payloadJson)
                    .put("createdAt", result.createdAt))
                derivedCount++
            }
        }
        check(rawCount == dao.rawCount())
        line(JSONObject().put("type", "end").put("sessions", sessions.size)
            .put("raw", rawCount).put("derived", derivedCount))
        out.flush()
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = if (uri.path == "/export") "application/x-ndjson" else null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object { const val LEGACY_PACKAGE = "com.jaysato.xiaomihealthbridge" }
}
