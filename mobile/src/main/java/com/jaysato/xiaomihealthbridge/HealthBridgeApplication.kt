package com.jaysato.xiaomihealthbridge

import android.app.Application

class HealthBridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SensorSuiteDump.requestPartialCleanup(this)
    }
}
