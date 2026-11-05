package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

internal object WearControlCommandAuth {
    const val ACTION_WEARABLE_MESSAGE_RECEIVED = "com.google.android.gms.wearable.MESSAGE_RECEIVED"
    private const val KEY_ALIAS = "wear_control_command_auth_v1"
    private const val PREFS = "wear_control_command_auth"
    private const val ISSUED_AT_EXTRA = "com.jaysato.xiaomihealthbridge.extra.CONTROL_ISSUED_AT"
    private const val NONCE_EXTRA = "com.jaysato.xiaomihealthbridge.extra.CONTROL_NONCE"
    const val SIGNATURE_EXTRA = "com.jaysato.xiaomihealthbridge.extra.CONTROL_SIGNATURE"

    fun isWearableListenerStart(action: String?): Boolean = action == ACTION_WEARABLE_MESSAGE_RECEIVED

    fun requiresForegroundHandoff(messagePath: String): Boolean =
        messagePath == WearControlService.PATH_SENSOR_SUITE

    fun createIntent(context: Context, action: String): Intent {
        val issuedAt = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        return Intent(context, WearControlService::class.java)
            .setAction(action)
            .putExtra(ISSUED_AT_EXTRA, issuedAt)
            .putExtra(NONCE_EXTRA, nonce)
            .putExtra(SIGNATURE_EXTRA, signature(context, action, issuedAt, nonce))
    }

    @Synchronized
    fun isAuthorized(context: Context, intent: Intent?): Boolean {
        val action = intent?.action ?: return false
        val issuedAt = intent.getLongExtra(ISSUED_AT_EXTRA, 0L)
        val nonce = intent.getStringExtra(NONCE_EXTRA) ?: return false
        val supplied = intent.getStringExtra(SIGNATURE_EXTRA) ?: return false
        val now = System.currentTimeMillis()
        val validAge = now - issuedAt in 0..TimeUnit.SECONDS.toMillis(30)
        if (!validAge || nonce.length !in 16..64) return false
        val expected = runCatching { signature(context, action, issuedAt, nonce) }.getOrNull() ?: return false
        val validSignature = java.security.MessageDigest.isEqual(
            supplied.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
        if (!validSignature) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val used = prefs.getStringSet("used_nonces", emptySet()).orEmpty()
            .filter { entry -> entry.substringBefore(':').toLongOrNull()?.let { now - it <= TimeUnit.SECONDS.toMillis(30) } == true }
            .toMutableSet()
        val nonceEntry = "$issuedAt:$nonce"
        if (nonceEntry in used) return false
        used.add(nonceEntry)
        return prefs.edit().putStringSet("used_nonces", used).commit()
    }

    @Synchronized
    private fun signature(context: Context, action: String, issuedAt: Long, nonce: String): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
                .apply {
                    init(KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                    ).build())
                }
                .generateKey()
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(key)
            update(context.packageName.toByteArray(Charsets.UTF_8))
            update(byteArrayOf(0))
        }
        return android.util.Base64.encodeToString(
            mac.doFinal("$action\u0000$issuedAt\u0000$nonce".toByteArray(Charsets.UTF_8)),
            android.util.Base64.NO_WRAP
        )
    }
}
