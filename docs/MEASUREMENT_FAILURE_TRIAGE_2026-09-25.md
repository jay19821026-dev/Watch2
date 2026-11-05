# Measurement Failure Triage — 2026-09-25

## Evidence collected

- Phone and Xiaomi Watch 2 Pro were reachable over ADB. At capture time the watch was on its charging activity; the installed apps on both devices were `3.13.11` (code 121), not the newer local builds.
- The watch runs Android 14 / API 34. `BODY_SENSORS` and `ACTIVITY_RECOGNITION` were granted. The newer `android.permission.health.READ_HEART_RATE` permission was not granted, but it is not the foreground permission path for this API level. The sensor inventory contains HR/PPG/ECG/BIOZ/SpO₂/temperature hardware entries; that proves HAL enumeration, not a live app measurement.
- The contemporaneous SensorService snapshot had no active app HR, PPG, ECG, BIOZ or SpO₂ listener. Active connections were motion/pedometer, wrist-tilt/off-body, light and temperature-alarm paths. Old ring-buffer samples are not fresh session results.
- The watch log contains 20 historical `ForegroundServiceDidNotStartInTimeException` events from Sept. 23. Each names `c:com.android.shell`, so those events came from shell-driven service launches, not evidence of a normal in-app foreground-service crash.
- The phone log at 14:23 shows Xiaomi Fitness (`com.xiaomi.fitness`) Health Connect write errors: HR samples outside the record session interval and a sleep list `IndexOutOfBoundsException`; Xiaomi Fitness reports its daily sync failed. These are not exceptions from this bridge app.
- The phone's persisted exit history records a native crash (PSS 1.5 GB) and following ANR (PSS 709 MB) at 08:31 on Sept. 25. The tombstone has now been pulled and identifies `Scudo ERROR: internal map failure (error desc=Out of memory)`. Its native stack goes through ICU `RegexMatcher.matches` called by Kotlin `String.toDoubleOrNull`, then `SensorSuiteDump.write`, invoked from `WearDataProcessor`. This proves the old export parser was applying regex-based numeric conversion to an unbounded persisted sensor token; it is not just an unexplained Scudo signal. The ANR followed the native crash and is not evidence of a separate root cause.
- The phone had zero active notifications for this app at capture. Routine phone alerts default to disabled and are rate-limited; emergency fall/SOS alerts remain enabled.
- A second bounded capture at 14:38 showed no new bridge-owned exceptions in the phone log window and still zero active app notifications. The watch remained on the charging screen with the same non-biomedical active sensor subscriptions; no fresh measurement session was running.

## Corrective change

The phone health dashboard previously returned before rendering anything unless every Health Connect read permission was granted. It also wrapped all record queries in one failure boundary, so a single denied or unsupported record type hid every otherwise-readable metric. It now reads each metric only when its own permission is present, catches errors per metric, preserves other successful cards, reports missing permissions/read failures, and renders unavailable aggregates as `— —` rather than a fabricated zero. Coroutine cancellation is rethrown rather than swallowed. Trend cards now downsample to at most 120 points and compute the heart-rate range without flattening all samples into another full-size list; this also avoids the chart parser's 8,192-point all-or-empty limit.

The Wear foreground heart-rate permission flow was separated from optional background/passive monitoring in the local Wear build. The newer source parser rejects a complete CSV payload above 262,144 characters, rejects tokens above 48 characters and caps parsed values; raw Data Layer payloads also have whole-message limits before persistence. These bounds address the stack proven in the tombstone, but the old app remains installed on the phone, so the runtime OOM fix has not yet been exercised on-device.

The algorithmic-insights page has now been changed to check each Health Connect read permission and query independently, report missing permissions/read failures separately from empty 7-day histories, and continue displaying other metrics when one query fails. It no longer emits default body-energy/readiness or training-load values when their inputs are unavailable. Readiness requires both sleep and the rule-based autonomic estimate; recovery likewise requires both. TRIMP now uses heart-rate samples inside each recorded exercise interval rather than averaging the entire week's heart rate. Training monotony requires calorie records on at least five distinct days. This remains a wellness estimate, not clinical validation.

## Follow-up live capture — Sept. 25, 14:48 local

- Both devices were reachable. Phone (`2506BPN68G`) and watch (`Xiaomi Watch 2 Pro`, Android 14/API 34) still had version 3.13.11/code 121 installed. The watch's resumed activity was the system `ChargingActivity`; `dumpsys activity services` showed no running bridge service. SensorService had accelerometer, ambient light, pedometer, wrist-tilt/off-body and temperature-alarm subscriptions, but no active bridge HR/PPG/ECG/BIOZ/SpO₂ listener. Historical registration records for `RawVitalsProbe` and HAL ring-buffer samples are not evidence of current fresh data.
- The phone's latest 6,000-line log buffer had no bridge-owned exception or crash. `dumpsys notification` showed no active bridge notification. The app's routine alerts are disabled by default and use a silent, 30-minute throttle when explicitly enabled; only fall/SOS types bypass that throttle.
- Because the watch was charging and no live measurement screen/session was running, this capture cannot determine whether the currently installed measurement attempt produces new data. Wrist-contact measurements should be retried off charger, with the watch worn, from the visible measurement page.
- The new release has not been installed (installation is intentionally left to the user); hardware verification is pending.

## Literature check

- Android's `PermissionController.getGrantedPermissions()` is the supported way to check current Health Connect grants; grants can change, and apps should proceed with the permissions actually granted rather than assuming the full set. [Android Developers API reference](https://developer.android.com/reference/androidx/health/connect/client/PermissionController) and [Health Connect read data guidance](https://developer.android.com/health-and-fitness/health-connect/read-data).
- A 44-paper wrist-PPG meta-analysis found average heart-rate agreement depended on activity; estimates were less accurate during resistance training and cycling than during rest, sleep and treadmill activity. The app should expose signal context/uncertainty and not interpret arbitrary raw PPG as a clinical pulse. [Zhang et al., 2020](https://pubmed.ncbi.nlm.nih.gov/32552580/).
- A systematic review found limited and heterogeneous evidence for actigraphy sleep-stage classification compared with PSG. Movement/HRV-derived sleep architecture should stay clearly labelled as an estimate, not PSG-equivalent staging. [Yuan et al., 2024](https://pubmed.ncbi.nlm.nih.gov/38384163/).
- Consumer wearable HRV must be validated per device and protocol; generic chipset capabilities or pooled wearable results cannot establish Xiaomi Watch 2 Pro RMSSD accuracy. A systematic review/meta-analysis found wearable HRV vs ECG results heterogeneous. [Seshadri et al., 2019](https://pubmed.ncbi.nlm.nih.gov/30706234/).

## Health evidence and safe application

- Android's Wear Health Services permission guidance is version-specific: use legacy `BODY_SENSORS` on older Wear OS releases and request background body-sensor access separately only when needed. [Android Developers: Health Services permissions](https://developer.android.com/health-and-fitness/health-services/permissions)
- A systematic review of wrist actigraphy versus PSG found limited studies and substantial heterogeneity; actigraphy-only sleep stages should remain estimates, not clinical staging. [Yuan et al., 2024](https://pubmed.ncbi.nlm.nih.gov/38384163/)
- A scoping review of 62 wearable setups found accelerometer-only devices useful for sleep/wake but weaker for multi-stage classification; sensor fusion still requires device-specific validation. [Birrer et al., 2024](https://pubmed.ncbi.nlm.nih.gov/38499793/)
- A 44-study meta-analysis found wrist PPG heart rate generally closer to criterion during sleep/rest and treadmill activity than during resistance training/cycling. Use signal-quality gates and label activity-context uncertainty; do not equate a plausible waveform with diagnostic accuracy. [Zhang et al., 2020](https://pubmed.ncbi.nlm.nih.gov/32552580/)

These papers support trend and wellness functions with explicit quality/uncertainty, not `100%` accuracy, disease diagnosis, or substituting generic chipset specifications for Xiaomi device calibration.

## Verification still required

1. Install the new local APKs, open the watch's foreground measurement page, grant the on-screen heart-rate permission if requested, and keep the watch worn and off-charger during capture.
2. Capture a short fresh watch session log and verify a new HR callback, timestamp, quality state, Data Layer delivery, and phone card update end to end.
3. Compare Health Connect grants per record type and verify the dashboard still shows authorized metrics when another record permission is withheld.
4. Reproduce the phone memory issue with a bounded bugreport/ANR trace before attributing its native crash to any parser or feature.
5. Confirm notification count over a real day; retain only critical fall/SOS alerts unless the user explicitly enables routine alerts.

## Follow-up code review — Health Connect pagination

- The official Health Connect raw-read guide specifies a default page size of 1,000 and recommends following `pageToken` to retrieve all records. It also warns some system versions can return an empty-string token at completion. [Android Developers: Read raw data](https://developer.android.com/health-and-fitness/health-connect/read-data)
- Dashboard, algorithmic insights, health-center record counts, sleep summary, workout analysis and the resting-heart-rate estimator now share a bounded reader that follows every page and treats null or empty tokens as completion. The reader rejects results above 25,000 records rather than presenting an incomplete prefix as complete. Trend-detail already paginated and now also handles an empty completion token.
- Algorithmic activity points no longer report a numeric score from heart-rate samples when no resting-heart-rate baseline is available.
- Verification after the pagination change: mobile unit tests 133/133 and Wear unit tests 34/34; mobile and Wear `lintVitalRelease` and `assembleRelease` succeeded. Release APKs: mobile 3.13.12 (code 122), Wear 3.13.13 (code 123). SHA-256 values are recorded in the task handoff, not treated as hardware validation.
- These checks prove compilation, tests, and release packaging only. The devices still had 3.13.11 installed during the live capture; no updated APK installation or off-charger fresh sensor session was performed, so the OOM correction and new-data path remain pending device verification.
