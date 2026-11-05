package com.jaysato.xiaomihealthbridge

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File

class WearFileReceiverService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path?.startsWith(PATH) == true }
            .forEach { event ->
                val uri = event.dataItem.uri
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val asset = map.getAsset("file") ?: return@forEach
                val safeName = File(map.getString("name") ?: "received.apk").name
                    .takeIf { it.endsWith(".apk", true) } ?: "received.apk"
                Wearable.getDataClient(this).getFdForAsset(asset)
                    .addOnSuccessListener { response ->
                        val target = File(filesDir, "transfers/$safeName")
                        target.parentFile?.mkdirs()
                        response.inputStream.use { input -> target.outputStream().use(input::copyTo) }
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(KEY_FILE, target.absolutePath)
                            .putString(KEY_NAME, safeName)
                            .apply()
                        Wearable.getDataClient(this).deleteDataItems(uri)
                    }
            }
    }

    companion object {
        const val PATH = "/file-transfer/"
        const val PREFS = "received_files"
        const val KEY_FILE = "latest_file"
        const val KEY_NAME = "latest_name"
    }
}
