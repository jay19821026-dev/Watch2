package com.jaysato.xiaomihealthbridge

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.tasks.Tasks
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class WearFileReceiverService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path?.startsWith(PATH) == true }
            .forEach { event ->
                val uri = event.dataItem.uri
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val asset = map.getAsset("file") ?: return@forEach
                val safeName = File(map.getString("name") ?: "received.apk").name
                    .takeIf { it.endsWith(".apk", true) } ?: "received.apk"
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                val target = File(filesDir, "transfers/$safeName")
                target.parentFile?.mkdirs()
                val partial = File(target.parentFile, "$safeName.partial")
                try {
                    // WearableListenerService callbacks use a background looper. Copying a large APK
                    // in addOnSuccessListener would instead run on the main thread and could ANR.
                    val response = Tasks.await(Wearable.getDataClient(this).getFdForAsset(asset))
                    try {
                        val input = response.inputStream ?: error("Asset stream unavailable")
                        input.use { source -> partial.outputStream().use(source::copyTo) }
                    } finally {
                        response.release()
                    }
                    check(partial.length() > 0L) { "Received empty APK" }
                    Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    check(prefs.edit().putString(KEY_FILE, target.absolutePath)
                        .putString(KEY_NAME, safeName).remove(KEY_ERROR).commit()) { "Cannot save receipt" }
                    Wearable.getDataClient(this).deleteDataItems(uri)
                } catch (error: Exception) {
                    partial.delete()
                    prefs.edit().putString(KEY_ERROR, error.javaClass.simpleName + ": " + error.message.orEmpty().take(100)).apply()
                    android.util.Log.e("WearFileReceiver", "APK reception failed", error)
                }
            }
    }

    companion object {
        const val PATH = "/file-transfer/"
        const val PREFS = "received_files"
        const val KEY_FILE = "latest_file"
        const val KEY_NAME = "latest_name"
        const val KEY_ERROR = "last_error"
    }
}
