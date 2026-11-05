package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.telephony.TelephonyManager

object SmartSyncPolicy {
    fun state(context: Context): State {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0) level * 100 / scale.coerceAtLeast(1) else -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        val wifi = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        val cellular = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val nfc = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val simReady = runCatching { telephony.simState == TelephonyManager.SIM_STATE_READY }.getOrDefault(false)
        return State(percent, charging, wifi, cellular, nfc, simReady)
    }

    fun shouldUseUrgent(context: Context) = state(context).let { it.charging || it.batteryPercent >= 25 }

    data class State(
        val batteryPercent: Int,
        val charging: Boolean,
        val wifi: Boolean,
        val cellular: Boolean,
        val nfc: Boolean,
        val simReady: Boolean
    )
}
