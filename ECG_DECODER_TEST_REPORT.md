# ECG decoder test report — 2026-10-10

## Deterministic fixture

`EcgDecoderReadinessTest` uses three synthetic 16-field rows with ten varying candidate fields, a fixed field, a counter-like `+10` field, and four zeros. It verifies 30 candidate values do not become decoded ECG samples and records mapping/clock/rate blockers. A second case verifies that even a hypothetical decoded stream remains blocked when the sample rate is not verified.

Existing `EcgHalEventResearchDecoderTest` covers valid-shaped rows, missing/extra/reordered timestamps, and counter changes. It asserts no waveform, physical rate, or inferred lost samples. Existing ECG engine and mobile/Wear tests remain separate from the core PPG, BioZ, temperature, heart-rate, and sync code paths.

## Verification

Investigation start: 2026-10-10 22:58:44 +08:00. End: 2026-10-10 23:01:45 +08:00. These are actual command-recorded wall-clock times, not estimated engineering hours.

`./gradlew.bat :health-engine:test :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:assembleDebug :wear:assembleDebug :mobile:assembleRelease :wear:assembleRelease --console=plain` completed `BUILD SUCCESSFUL in 1m 19s`. This covers synthetic decoder/readiness tests and cross-module compilation, not live hardware behavior. The Android SDK tool emitted a non-fatal XML-version compatibility warning. No synchronized reference ECG was available in this cycle.

ADB had listed both phone and watch earlier in the session, but after the daemon restarted during the raw-preferences query it listed no devices. The failed query produced no new raw-export evidence; do not treat its `ABSENT` placeholders as sensor observations. No new device capture, UI acceptance, or installation is claimed for this cycle.

## Device reconnection check — 2026-10-10 23:09 +08:00

ADB connected to the Xiaomi Watch 2 Pro at `192.168.0.16:44175`; the installed watch app remains `3.13.44` (`versionCode=154`). `dumpsys battery` showed 72% and USB charging, and the charging activity was foreground. The `sensor_suite_status` preference only showed a completed prior run; no new ECG event stream or verified clock was acquired. Recent app exits were classified by Android as background low-memory exits or package updates, not an observed ECG measurement crash. The phone was not listed by ADB. This restores watch command access but does not satisfy the off-charger, worn, paired-device ECG acceptance gate.

## Phone-side persistence check — 2026-10-10 23:18 +08:00

The Android phone briefly connected through USB with app `3.13.44` (`versionCode=154`). Its existing `raw_sensor_results` preferences contained `ecg_event_count=216`, `ecg_raw_value_count=0`, `ecg_sample_rate=0`, and `ecg_research_timeline_source=NORMALIZED_INDEX`. This is a persisted snapshot, not a newly observed ECG session and not the older 2,967-event export; these counts must not be merged. The phone was displaying another app, while the watch Wi-Fi ADB endpoint had become offline. Paired live transport, decoded ECG, and user-facing rhythm acceptance remain unverified.

## Bounded live capture — 2026-10-10 23:21–23:25 +08:00

After reconnection at the new ADB port, the watch showed USB power disconnected and `WearEcgActivity` foreground. Its live screen showed 1,640 Sensor API events during collection. The phone subsequently persisted at least four distinct final segments: 2,967, 2,969, 2,967, and 2,967 events, respectively, each with `ecg_raw_value_count=0` and `ecg_sample_rate=0`. In segment 1, the retained 261 source event timestamps were positive and strictly increasing; in segments 2–4, the retained 264, 264, and 268 timestamps were negative and marked invalid. Publication timestamps and `ecg_final=true` were observed. This proves acquisition and watch-to-phone persistence for these segments, but not waveform mapping, ADC sampling, AFib analysis, or medical calibration. The wireless ADB endpoint became unavailable during capture, so no post-run watch-side process dump was obtained. No source rows were copied out of the devices for this report. The activity's `MAX_AUTO_SEGMENTS=10` was noticed after the fourth segment; the user was asked to stop the continuous run rather than collect redundant additional segments.

## Automatic capture bound correction — 2026-10-10 23:28–23:29 +08:00

The installed watch build was observed persisting at least six automatic segments even though all were research-only candidates. The source default in `EcgCapturePolicy.MAX_AUTO_SEGMENTS` was reduced from 10 to 3, with a boundary assertion in `EcgCapturePolicyTest`. The first combined Gradle attempt failed on a transient Windows lock of `wear/build/intermediates/dex/release/mergeDexRelease/classes2.dex`; a retry of `:wear:assembleRelease` succeeded, and focused Wear unit tests plus `:wear:assembleDebug` succeeded. This new APK was **not installed**, so the ongoing old-version capture remains governed by the previous ten-segment limit. The user was asked to end and save the run manually; no stop confirmation was available at report time.

The follow-up cross-module command `:health-engine:test :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:assembleDebug :wear:assembleDebug :mobile:assembleRelease :wear:assembleRelease` completed `BUILD SUCCESSFUL in 3s` on this checkout. At the same check, the installed watch's seventh final ECG segment had reached phone storage (`ecg_event_count=2966`); this is additional transport evidence, not an independent decoder validation. Other sensor hardware and consumer UI flows were not exercised by these unit/build tasks.

The user then confirmed stopping the ongoing old-version capture. The phone's last persisted segment was partial, with `ecg_event_count=1446`, `ecg_final=true`, and `ecg_research_decode_status=candidate_values_unverified`; its session and publication timestamps remained stable on the next available ADB check at 23:31:35 +08:00. This is consistent with a saved manual stop, but the watch endpoint was offline, so the exact watch UI stop reason was not independently read. No further capture was initiated.

On 2026-10-14, a fresh ADB device listing contained neither phone nor watch. The user's stop confirmation remains the only direct stop action evidence; no later watch process or UI state can be verified from this disconnected host. The unavailable device connection must not be described as a new capture failure or as proof of hardware acceptance.

## Reproducibility check — 2026-10-14

The current checkout reran `:health-engine:test`, `:mobile:testDebugUnitTest`, and `:wear:testDebugUnitTest`; all completed `BUILD SUCCESSFUL`. A combined invocation that supplied multiple `--tests` filters was rejected by Gradle as an invalid task-argument expansion; the unfiltered per-module runs are the authoritative result. The Android SDK emitted a non-fatal XML schema compatibility warning. No device test or installation was performed.

## Fresh watch diagnostics — 2026-10-14

Read-only ADB reconnection to `192.168.0.16:37633` succeeded. The watch reported version `3.13.44`/code `154`, battery `95%`, and USB power `false`. Its latest completed suite reported `ecg_registration=registered`, `ecg_event_count=209`, `ecg_stop_reason=stopped_with_samples`, populated first/last event times, and `ecg_sample_timestamp_source=android_sensor_event_elapsed_realtime`. This confirms the watch listener produced events with a recorded elapsed-time field; it does not establish field mapping, ADC sampling rate, lead semantics, waveform decoding, or clinical interpretation. The same diagnostics reported PPG `8` events with `sensor_event_timestamp_missing`, so PPG evidence was not substituted for ECG evidence.

SensorService metadata separately reported `max86178_ecg` with `maxRate=500.00Hz`. This was recorded as capability metadata only; it was not promoted to `ecg_sample_rate` or used to unlock RR/heart-rate/HRV calculations.

## AI review and selected implementation — 2026-10-14

Four independent reviewers converged on the same implementable boundary: preserve immutable raw events, separate event count from decoded point count, and make zero-point outcomes explicit without inventing waveform or physiological values. The selected patch adds `ecg_research_decode_state` to the existing decoder result and persistence/export key set:

- `NO_EVENTS` when no rows are received.
- `EVENTS_PRESENT_ZERO_POINTS` when rows are received but the mapping contract is unverified and no waveform points are emitted.

The existing `candidate_values_unverified` status, raw rows, timestamps, and sample-rate gate remain unchanged. This makes the feature usable for engineering review and replay while preventing `decoded_points=0` from being confused with no sensor events.

Verification after the patch: `:mobile:testDebugUnitTest` and `:wear:testDebugUnitTest` both completed `BUILD SUCCESSFUL`. No physical-unit or AFib output was enabled.

The follow-up `:mobile:assembleDebug :wear:assembleDebug` build also completed `BUILD SUCCESSFUL`. The generated debug APKs were not installed because this turn did not request deployment.

## Four-state consumer regression — 2026-10-14 20:07 +08:00

`SensorResultAvailabilityTest` verifies that persisted raw evidence is visible as `RAW_CAPTURED`, quality-rejected raw evidence is distinct, eligible calculations become `RESULT_AVAILABLE`, explicit transport/persistence rejection becomes `SYNC_FAILED`, and an untouched sensor is not falsely reported as a synchronization failure. ECG eligibility now calls the same `ClinicalVerdict.isEcgPulseDisplayable` gate used by the card value, preventing status/value disagreement. PPG availability only accepts PPG-qualified HRV evidence, not another optical source.

The complete command `:health-engine:test :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:assembleDebug :wear:assembleDebug :mobile:assembleRelease :wear:assembleRelease` completed `BUILD SUCCESSFUL in 1m 30s`. After the review corrections, `:mobile:testDebugUnitTest :mobile:assembleDebug` completed `BUILD SUCCESSFUL in 13s`. These prove deterministic regression and build integrity only; no APK was installed and no new hardware measurement was claimed.

## External-review reconciliation — 2026-10-14

A previously submitted, de-identified Gemini review recommended separating raw evidence from clinical readiness and surfacing rejected Data Layer snapshots. Source inspection confirmed that `WearDataProcessor.recordRawSnapshotRejection` already preserves bounded rejection metadata, and `RawVitalsProbe` already calls `SensorManager.flush` during registration; therefore those suggestions were not copied as new implementations. One actual gap was confirmed: the Vitals four-state mapper did not read the existing rejection metadata stored in the control preferences, so a rejected ECG/PPG snapshot could remain invisible on the home card.

`SensorSyncFailurePolicy` now exposes only a same-kind rejection within a bounded 30-minute window, and only when no successful raw evidence exists. Tests cover matching, cross-sensor, stale, and future rejection records. `:mobile:testDebugUnitTest` with the focused policies plus `:mobile:assembleDebug` completed `BUILD SUCCESSFUL in 15s`. No decoder, sample-rate, physical-unit, or AFib gate changed.
