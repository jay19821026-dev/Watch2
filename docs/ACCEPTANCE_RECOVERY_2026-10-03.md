# Acceptance recovery

- Installed-APK verification: local `wear-debug.apk` SHA-256 `2846F2F25CF243BA727D3EDA5F05E7BBC644D896714CFCAEFCC4472992E33532` matches the watch installed `base.apk` SHA-256 after overwrite install. Wear Debug unit results: 183 tests, zero failures/errors. The dispatched independent review could not complete because that agent reached its platform usage limit; no review result was used.

- Vendor recovery observation: a bounded recent log window contains `whs-hal: HalOnDataUpdate` callbacks from PID 16736 at 15:22:38–15:22:43. This proves later HAL callback activity after the 11:39 crash, not correct physiological output or delivery to this App. The combined process/log query experienced `error: closed` and temporarily waited for the device; it was interrupted to avoid indefinite waiting. A subsequent device listing showed both devices online. The returned window did not establish the 11:39 trigger.

- Crash-buffer follow-up found a vendor Health Services native crash at `2026-10-03 11:39:21 +0800`, before this update: process `/vendor/bin/hw/vendor.qti.hardware.healthservices@1-service`, signal 11 / SEGV_MAPERR, vendor `libhealthservices.so` / `libwhshal.so` callback frames. A separate September 29 entry names the same vendor service. These are vendor-process failures, not evidence of a Java App crash or proof that this App caused them. Correlate acquisition/registration logs around 11:39 and service reconnection before choosing a mitigation. This bounded historical crash-buffer check does not validate post-install App operation.

- Installation result confirmed: the existing ADB install process returned `Success`; watch package readback reports versionCode 147 / versionName 3.13.37 and last update `2026-10-03 15:20:40`. This installs the Debug end-timestamp fix, not a signed production Release. No post-install scan has yet verified the changed persisted timestamps.

- Release follow-up: Wear Release assembly and lint passed after the end-timestamp fix. Debug overwrite installation was started through the connected watch ADB transport while the suite reported completed; installation process handle `47802` had not yet returned a result at the last poll. Do not infer installation success. Watch battery was 8%, unplugged; no fresh scan was started.

- Follow-up fix: `WearControlService.report` now clears the persisted end timestamp for nonterminal/unknown reports and records a terminal report timestamp only when it is at or after the current session start. Wear Debug unit tests and Debug assembly passed. Existing tests do not directly prove SharedPreferences mutation behavior; paired hardware readback and Release checks remain pending. The new APK has not been installed in this follow-up.

- At 2026-10-03 15:14 Asia/Taipei, both phone USB and watch wireless ADB were connected. No new scan was started.
- Phone persisted aggregate suite `18d4a78c-548d-4bd4-8c81-86fa70af7fdc` reports `completed`. This differs from the previously documented scan1 session; its initiation and paired watch evidence have not yet been reconciled.
- The bounded collector successfully read `raw_sensor_results`, `watch_feature_snapshot`, and `sync_status`. Evidence: `build/acceptance/20261003-151406-281-ee8a4079/poll-summary.jsonl` and its adjacent raw XML files.
- The collected per-channel terminal entries include older suite IDs for ADT, off-body, hardware ADT, and Health Services heart rate. They must be filtered by suite ID before calculating this session's channel coverage. This observation alone does not establish a merge bug.
- Aggregate completion proves a persisted terminal receipt, not successful acquisition or physiological interpretation for every channel. Three paired UI attempts and release acceptance remain incomplete.
- Hermes/Grokbot collaboration connectivity is a separate workstream and must not block local source review or recovery of existing device evidence. No App integration requirement is inferred solely from the request to collaborate with these assistants.

## Session coverage audit

- The recovered preference snapshot has 33 channel suite-ID entries: 29 match the aggregate session and four belong to other sessions (ADT, off-body, hardware ADT, Health Services heart rate). These counts describe persisted entries, not expected hardware channels or successful measurements.
- Among matching entries, BioZ has stored legacy `samples=3312`, raw SpO2 `samples=184`, and MAX HR `samples=7936`; all three report `sensor_event_timestamp_ahead_of_capture`. These sample fields do not establish physiological validity or a reliable sample clock.
- Body-temperature raw, LifeQ OTT, Mi SpO2, significant motion, stationary, step detector, thermometer, and wrist tilt have zero stored samples in the matching entries. Motion-trigger channels need appropriate stimulation; zero alone does not identify a software defect.
- `HealthCenterActivity` overview explicitly applies `SensorSuiteSessionPolicy.belongsToLatestSuite` before counting measured channels. Older stored rows therefore do not, by themselves, prove overview contamination.
- ECG and PPG fields exist, but neither appears in the recovered `_suite_id` list. Their presence must not be counted as acquisition by this aggregate session. Trace missing/replaced session provenance and paired watch state before assigning a cause or changing merge policy.

## ECG/PPG provenance follow-up

- Aggregate session start: `1790974990819`; stored ECG session start: `1790975176480`; stored PPG session start: `1790975254732`. ECG and PPG thus have later session starts than the aggregate start. ECG reports 2965 events; PPG reports eight events and measurement time zero. These are persisted observations, not a fresh test.
- Phone consumer explicitly removes a channel's prior suite ID when an accepted incoming snapshot lacks a suite ID (`WearDataProcessor`, adjacent to the channel session/sequence write). This avoids falsely assigning a standalone measurement to an earlier suite.
- The later session times and missing suite IDs are consistent with standalone measurements replacing the latest-per-channel cache. They do not prove that the aggregate scan failed to acquire ECG/PPG. Establishing that requires the watch session history or retained aggregate receipts.
- A remaining acceptance limitation is historical reconstruction: the latest channel cache cannot independently prove all results from an earlier completed aggregate suite after subsequent standalone measurements replace entries. Do not add these later events to the aggregate success numerator.
- The phone app's private `files` listing returned only `profileInstalled`; no suite export file was found at that directory's top level. This does not exclude exports under cache, external storage, or other preference stores. The raw snapshot receipt ledger referenced in the consumer is used for replay/durability tracking; its existence alone does not prove a retained historical payload archive.

## Paired watch readback

- Watch `sensor_suite_status` stores the same aggregate ID and start as the phone. Watch acquisition diagnostics corroborate the later ECG session: registration succeeded, 2965 events, stop reason `time_limit_reached`. PPG registration succeeded, eight events, stop reason `stopped_with_samples`, first/last measurement time zero and source `sensor_event_timestamp_missing`.
- This corroborates delivery of these later measurement observations; it still does not reconstruct ECG/PPG acquisition within the earlier aggregate session.
- Confirmed metadata inconsistency: watch status retains `suite_session_ended_at_ms=1790599702775`, which predates current suite start `1790974990819`. Source review finds `StartupReceiver` writes that end field during interruption recovery, while `WearControlService.report` updates current suite identity/start/state without resetting or updating it. Acceptance must ignore this stale end field until fixed. Proposed fix: clear it for a new/running session and write it for terminal current-session reports, with regression coverage for interruption followed by a new scan. No production patch or hardware retest has yet been performed for this finding.
