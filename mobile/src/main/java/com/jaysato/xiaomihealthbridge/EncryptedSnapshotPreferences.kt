package com.jaysato.xiaomihealthbridge

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.WeakHashMap
import java.util.concurrent.Future

internal class EncryptedSnapshotPreferences(
    initial: Map<String, *>,
    private val persist: (Map<String, *>) -> Unit
) : SharedPreferences {
    private val lock = Any()
    private var values = copy(initial)
    private val listeners = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Boolean>()
    private val writer = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue(4),
        java.util.concurrent.ThreadFactory { task -> Thread(task, "raw-snapshot-writer").apply { isDaemon = true } },
        java.util.concurrent.ThreadPoolExecutor.AbortPolicy()
    )
    private val main = Handler(Looper.getMainLooper())
    private val outcomes = RawPersistenceOutcome()
    val lastPersistenceFailure: String? get() = outcomes.failure

    override fun getAll(): MutableMap<String, *> = synchronized(lock) { copy(values).toMutableMap() }
    override fun contains(key: String): Boolean = synchronized(lock) { key in values }
    override fun getString(key: String, defValue: String?): String? = synchronized(lock) {
        if (key in values) values[key] as String else defValue
    }
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = synchronized(lock) {
        if (key in values) (values[key] as Set<*>).map { it as String }.toMutableSet() else defValues?.toMutableSet()
    }
    override fun getInt(key: String, defValue: Int): Int = synchronized(lock) { if (key in values) values[key] as Int else defValue }
    override fun getLong(key: String, defValue: Long): Long = synchronized(lock) { if (key in values) values[key] as Long else defValue }
    override fun getFloat(key: String, defValue: Float): Float = synchronized(lock) { if (key in values) values[key] as Float else defValue }
    override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(lock) { if (key in values) values[key] as Boolean else defValue }
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(lock) { listeners[listener] = true }
    }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(lock) { listeners.remove(listener) }
    }

    private fun enqueue(update: RawSnapshotTransactions.Update): Future<Boolean> {
        val future: Future<Boolean>
        val changed: Set<String>
        val cleared: Boolean
        val observers: List<SharedPreferences.OnSharedPreferenceChangeListener>
        synchronized(lock) {
            val operation = outcomes.begin()
            val previous = values
            val next = if (update.clear) linkedMapOf() else copy(previous).toMutableMap()
            update.removals.forEach(next::remove)
            next.putAll(copy(update.values))
            val snapshot = copy(next)
            val notificationBase = if (update.clear) emptyMap() else previous
            cleared = update.clear && previous.isNotEmpty() && android.os.Build.VERSION.SDK_INT >= 30
            changed = (notificationBase.keys + snapshot.keys).filter { key ->
                key !in notificationBase || key !in snapshot || !sameValue(notificationBase[key], snapshot[key])
            }.toSet()
            future = try { writer.submit<Boolean> {
                try {
                    persist(snapshot)
                    outcomes.complete(operation, null)
                    true
                } catch (error: Exception) {
                    outcomes.complete(operation, error.javaClass.simpleName)
                    Log.e("RawSnapshotStore", "encrypted_snapshot_write_failed:${error.javaClass.simpleName}")
                    false
                }
            } } catch (error: java.util.concurrent.RejectedExecutionException) {
                outcomes.complete(operation, "RejectedExecutionException")
                throw error
            }
            values = snapshot
            observers = listeners.keys.toList()
            if (cleared || changed.isNotEmpty()) main.post {
                observers.forEach { listener ->
                    if (cleared) listener.onSharedPreferenceChanged(this, null)
                    changed.forEach { key -> listener.onSharedPreferenceChanged(this, key) }
                }
            }
        }
        return future
    }

    private inner class Editor : SharedPreferences.Editor {
        private val changes = RawSnapshotEditorChanges()
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { changes.put(key, value) }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { changes.put(key, values) }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { changes.put(key, value) }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { changes.put(key, value) }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { changes.put(key, value) }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { changes.put(key, value) }
        override fun remove(key: String): SharedPreferences.Editor = apply { changes.remove(key) }
        override fun clear(): SharedPreferences.Editor = apply { changes.clear() }
        override fun apply() { enqueue(changes.drain()) }
        override fun commit(): Boolean = try {
            enqueue(changes.drain()).get()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: java.util.concurrent.ExecutionException) { false }
        catch (_: java.util.concurrent.RejectedExecutionException) { false }
    }

    private fun sameValue(left: Any?, right: Any?): Boolean =
        if (left is Float && right is Float) left.toRawBits() == right.toRawBits() else left == right

    private fun copy(source: Map<String, *>): Map<String, Any> {
        val encoded = RawSnapshotCodec.encode(source)
        return try { RawSnapshotCodec.decode(encoded) } finally { encoded.fill(0) }
    }
}
