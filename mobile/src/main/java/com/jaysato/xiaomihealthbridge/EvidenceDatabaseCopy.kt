package com.jaysato.xiaomihealthbridge

import androidx.room.withTransaction

internal object EvidenceDatabaseCopy {
    data class Result(val sessions: Int, val rawSamples: Long, val derivedResults: Long)

    suspend fun verify(source: SignalEvidenceDatabase, destination: SignalEvidenceDatabase): Result {
        require(source !== destination)
        return source.withTransaction {
            require(java.io.File(source.openHelper.writableDatabase.path).canonicalPath !=
                java.io.File(destination.openHelper.writableDatabase.path).canonicalPath)
            destination.withTransaction {
                val original = source.evidence()
                val target = destination.evidence()
                val sessions = original.readMigrationSessions()
                check(sessions.size <= 200 && original.sessionCount() == sessions.size.toLong())
                check(target.readMigrationSessions() == sessions && target.sessionCount() == sessions.size.toLong())
                var rawCount = 0L
                var derivedCount = 0L
                for (session in sessions) {
                    val raw = original.readRaw(session.sessionId)
                    val derived = original.readDerived(session.sessionId)
                    check(target.readRaw(session.sessionId) == raw && target.readDerived(session.sessionId) == derived)
                    rawCount += raw.size
                    derivedCount += derived.size
                }
                check(original.rawCount() == rawCount && target.rawCount() == rawCount)
                Result(sessions.size, rawCount, derivedCount)
            }
        }
    }

    suspend fun copyAndVerify(
        source: SignalEvidenceDatabase,
        destination: SignalEvidenceDatabase,
        onSessionVerified: suspend () -> Unit = {}
    ): Result {
        require(source !== destination)
        val sourceName = source.openHelper.databaseName
        val destinationName = destination.openHelper.databaseName
        require(sourceName == null || destinationName == null || sourceName != destinationName)
        return source.withTransaction {
            val sourcePath = source.openHelper.writableDatabase.path
            val destinationPath = destination.openHelper.writableDatabase.path
            require(sourcePath == ":memory:" || destinationPath == ":memory:" ||
                java.io.File(sourcePath).canonicalPath != java.io.File(destinationPath).canonicalPath)
            val original = source.evidence()
            val sessions = original.readMigrationSessions()
            check(sessions.size <= 200 && original.sessionCount() == sessions.size.toLong()) {
                "Source evidence exceeds migration bounds."
            }
            destination.withTransaction {
                val target = destination.evidence()
                check(target.sessionCount() == 0L && target.rawCount() == 0L) { "Destination must be empty." }
                var rawCount = 0L
                var derivedCount = 0L
                for (session in sessions) {
                    val raw = original.readRaw(session.sessionId)
                    val derived = original.readDerived(session.sessionId)
                    target.appendRawSession(session, raw)
                    target.replaceDerived(session.sessionId, derived)
                    check(target.readSession(session.sessionId) == session &&
                        target.readRaw(session.sessionId) == raw &&
                        target.readDerived(session.sessionId) == derived) { "Copied evidence differs from source." }
                    rawCount += raw.size
                    derivedCount += derived.size
                    onSessionVerified()
                }
                check(target.sessionCount() == sessions.size.toLong() &&
                    target.rawCount() == rawCount && original.rawCount() == rawCount) { "Evidence count mismatch." }
                Result(sessions.size, rawCount, derivedCount)
            }
        }
    }
}
