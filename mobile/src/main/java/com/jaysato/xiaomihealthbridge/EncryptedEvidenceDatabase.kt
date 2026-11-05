package com.jaysato.xiaomihealthbridge

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

internal object EncryptedEvidenceDatabase {
    fun build(context: Context, name: String, passphrase: ByteArray): SignalEvidenceDatabase {
        require(name.isNotBlank() && '/' !in name && '\\' !in name)
        require(passphrase.size >= 32)
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context.applicationContext, SignalEvidenceDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
            .build()
    }
}
