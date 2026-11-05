package com.jaysato.xiaomihealthbridge

import android.content.Context

internal object EncryptedRawPreferencesRegistry {
    private val instances = mutableMapOf<String, EncryptedSnapshotPreferences>()

    @Synchronized
    fun open(context: Context, name: String): EncryptedSnapshotPreferences {
        val app = context.applicationContext
        val identity = app.noBackupFilesDir.canonicalPath + "/" + name
        return instances.getOrPut(identity) {
            val storage = AndroidRawSnapshotMigrationStorage(app, name)
            val result = RawSnapshotMigration.open(storage)
            EncryptedSnapshotPreferences(result.values, storage::writeEncrypted)
        }
    }
}
