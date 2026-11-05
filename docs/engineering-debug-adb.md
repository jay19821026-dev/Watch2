# Engineering Debug over ADB

The phone Engineering Debug activity has an ADB-only activity alias protected by `android.permission.DUMP`. Use a paired phone with USB or wireless ADB already authorized.

```powershell
adb -s <PHONE_SERIAL> install -r mobile/build/outputs/apk/release/mobile-release.apk
adb -s <PHONE_SERIAL> shell am start -n com.jaysato.xiaomihealthbridge/.EngineeringDebugShellActivity --ez query_watch_on_open true
```

To query the watch and write a complete local diagnostic bundle for computer-side analysis:

```powershell
adb -s <PHONE_SERIAL> shell am start -n com.jaysato.xiaomihealthbridge/.EngineeringDebugShellActivity --ez query_watch_on_open true --ez export_raw_on_open true
Start-Sleep -Seconds 20
adb -s <PHONE_SERIAL> pull /sdcard/Android/data/com.jaysato.xiaomihealthbridge/files/debug-export/healthbridge-debug.json .\healthbridge-debug.json
adb -s <PHONE_SERIAL> logcat -d -t 3000 > .\phone-logcat.txt
adb -s <WATCH_SERIAL> logcat -d -t 3000 > .\watch-logcat.txt
```

To start one full sensor suite from the authorized ADB shell entry and collect its final snapshot (up to about seven minutes):

```powershell
adb -s <PHONE_SERIAL> shell am start -n com.jaysato.xiaomihealthbridge/.EngineeringDebugShellActivity --ez start_sensor_suite_on_open true --ez export_raw_on_open true
Start-Sleep -Seconds 490
adb -s <PHONE_SERIAL> pull /sdcard/Android/data/com.jaysato.xiaomihealthbridge/files/debug-export/healthbridge-debug.json .\healthbridge-debug.json
```

The bundle contains local raw sensor records and can include sensitive health data. It is written on-device only; ADB pull is an explicit copy to the connected computer. Review and redact before sharing. A scan request is only a request: Data Layer acceptance alone is not proof that the watch started or completed it. Charging, contact, permissions, vendor state and unsupported sensors can limit collection; use exported run/session fields to assess completion.
