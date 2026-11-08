---
name: oem-sensor-result-recovery
description: Trace AILISA Watch 2 Pro PPG, HRV, ECG, BioZ, and temperature readings that appear once or disappear, using decompiled OEM behavior as comparative evidence and repairing the public API data path.
---

# OEM sensor result recovery

Use with `healthbridge-debugger` when a captured reading vanishes, has time `0`, or an OEM result appears while AILISA only has raw events. Read `docs/OEM_SENSOR_RESULT_RECOVERY_PLAN.md` for the current evidence and checkpoints.

1. Identify the exact app versions and connected devices. Inspect only bounded, relevant app state and logs; keep personal measurement values out of shared reports.
2. Trace acquisition → event clock and callback clock → watch snapshot → Data Layer → phone persistence → freshness classifier → card. Record each state separately: raw event, valid timestamp, calibrated value, OEM typed result, and user-visible outcome.
3. Compare the locally documented OEM call paths in `docs/OEM_PPG_BIOZ_ECG_HEALTH_CONNECT_TRACE.md` and `docs/OEM_SPO2_TEMPERATURE_SOURCE_TRACE.md`. Treat decompiled class names and service behavior as clues, not reusable code or proof of device entitlement.
4. Preserve an event's original timestamp and a separately labeled callback receipt timestamp. If the sensor timestamp is missing or ahead of capture, do not silently substitute it as a verified sample clock; retain the raw capture and display the last usable dated result as history.
5. Keep raw event counts, parser state, and clock errors in Debug/Export. User cards show a short outcome, measurement time, and one action. An unscaled ECG/PPG/BioZ signal cannot become a rhythm, oxygen, body-composition, or temperature diagnosis.
6. Reproduce disappearance with a valid snapshot followed by one with a bad timestamp and with a new empty session. Verify storage and UI retain the dated prior result without presenting it as current. Test relevant mobile and Wear modules; state hardware verification separately.

Do not copy OEM binaries, native algorithms, private service calls, or activation flags into the distributed app. Do not call an old research AF label a confirmed current result.
