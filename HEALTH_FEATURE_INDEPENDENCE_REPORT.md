# Health feature independence — 2026-10-10

ECG candidate inspection lives in `health-engine` and the phone's ECG research decoder/UI. The existing Data Layer path is shared, but consumer processing is per sensor kind; `HealthConclusionEngine.buildCoreSensorItems` isolates each item with `buildSafely`, so an ECG calculation exception should not suppress other items. Daily heart rate prioritizes processed Health Services/Health Connect records and does not use ECG candidate fields as a fallback. PPG, BioZ, temperature, SpO₂, and synchronization retain their existing modules.

Code isolation is not a release approval. Health-engine, mobile, and Wear unit tests plus both Debug/Release builds passed during this cycle. The watch is currently reachable over wireless ADB, but the phone is not listed, so paired-device regression remains unverified. A successful build cannot prove a sensor result. `MAIN_HEALTH_APP_STATUS = BLOCKED` pending device-level regression verification and release-signing/publishing requirements, not because ECG research is blocked.

The checkout contains no Git metadata, so branch-based isolation and commit-by-commit comparison are unavailable. Do not fabricate a branch or historical commit list.

On 2026-10-14, read-only diagnostics from the connected watch reported an ECG registration with 209 events and a stopped-with-samples result. This confirms watch-side ECG acquisition evidence only; it does not verify phone transport, field mapping, or any consumer health result.

On 2026-10-10, a user-authorized off-charger ECG session provided fresh paired-device evidence that the existing Wear Data Layer can deliver multiple bounded ECG raw-event segments to phone storage without enabling waveform or AFib interpretation. This narrows the ECG transport gate only; it does not prove the independent heart-rate, PPG, BioZ, temperature, Health Connect, or consumer UI paths on both devices. `MAIN_HEALTH_APP_STATUS` remains `BLOCKED` for full device regression acceptance, while ECG remains isolated as `RESEARCH_BLOCKED`.

At 23:32 +08:00, the installed phone app `3.13.44` displayed its Summary screen after foreground loading: independent heart rate `99 bpm` with a 23:20 time label, a 24-hour range of `65–134 bpm` over 69 records, today's steps `2,715`, today's SpO₂ `100%`, and sleep `1.8 hours`. This is direct UI evidence that a blocked ECG research path did not suppress these four consumer cards. It does not independently verify each value's Health Connect origin, heart-rate trend drill-down, alert delivery, or sensor accuracy. The phone returned to another app before the detail page could be inspected, so no heart-rate detail acceptance is claimed. Temporary screenshots used for local inspection were removed from the workstation and phone.

A separate phone persistence check found `watch_feature_snapshot.whs_heart_rate=99.0` at 23:30:34 +08:00 with `timestamp_source=health_services_datapoint_epoch`. The corresponding raw-sensor result names Wear Health Services as source, reports one processed scalar sample and zero candidate waveform points. This confirms the phone has an independent processed-heart-rate path; it does **not** prove that the 23:20 home card was rendered from that later 23:30 snapshot or that all 69 history records share the same origin. ECG candidate values remain isolated from the processed BPM store.

## UI availability isolation — 2026-10-14

The home screen now presents ECG and PPG evidence through a shared four-outcome vocabulary without changing the physiological calculation gates. ECG raw events can therefore appear as `已取得原始資料` while decoded waveform, bpm, RR, HRV, mV, and AFib remain blocked. PPG raw capture can appear independently from HRV/stress eligibility; non-PPG HRV evidence cannot mark PPG as calculation-ready. This prevents the blocked ECG decoder and strict PPG interpretation gates from hiding acquisition evidence or suppressing independent daily heart-rate behavior.

Cross-module unit tests and both Debug/Release APK builds pass after this change. Hardware/UI acceptance remains separate and was not performed in this cycle.
