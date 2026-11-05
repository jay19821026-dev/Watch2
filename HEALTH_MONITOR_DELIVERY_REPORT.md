# Health monitor convergence — 2026-10-10

## Scope and evidence

This checkout has no `.git` metadata. Historical commits and a commit-bound test claim cannot be audited here. The current source and existing ECG recovery reports were inspected; the results below apply to this checkout, not an identified commit. ADB currently lists no phone or watch, so no new device acceptance was performed.

## Pipeline and root cause

ECG SensorEvent values are retained as raw event rows and inspected by `EcgRawEventDecoder.decode`. The first ten of sixteen columns are candidate values only; column 12 (zero-based index 11) is a counter hypothesis. Neither is a verified electrode/channel contract. `EcgHalEventResearchDecoder.decode` deliberately returns an empty waveform and `decodedSampleCount=0`; `EcgPayloadInterpretationPolicy.VERIFIED_ECG_EVENT_CONTRACT_AVAILABLE=false`. This is the immediate software reason for zero decoded ECG samples, not proof that the watch produced zero events. Source timestamps, acquisition rate, lead mapping and gain are not validated; callback intervals and candidate-point density cannot substitute for an ADC clock. See `ECG_ROOT_CAUSE_REPORT.md` for the raw-event replay and export trail.

The processed heart-rate path is independent: Wear Health Services `HEART_RATE_BPM` callback → `PassiveHealthService` → watch store and `/heart-rate/` Data Layer payload → `HeartRateDataListenerService` / `WearDataProcessor` → phone storage and Health Connect → consumer UI. The observed reminder defect was in `PassiveHealthService`: alert observations happened only after the default five-minute sync throttle, while the sustained alert policy requires adjacent observations at most two minutes apart. Alerts now inspect fresh, plausible callback points before sync throttling; Data Layer throttling remains unchanged. Callback availability and user-configured thresholds still need device verification.

PPG raw candidate values do not establish a channel map, sample clock or pulse-interval reliability. Keep irregular-pulse analysis research-only and gated; do not infer AFib from raw optical anomalies. ECG research cannot promote candidate values to clinical positive/negative AFib classification. Processed Health Services heart rate remains the daily BPM source.

## Four user-facing outcome states

| State | Meaning | Permitted output |
| --- | --- | --- |
| Data valid | A supported processed metric with plausible value, trusted time and acceptable freshness | Value, time, source, trend or threshold advisory |
| Data insufficient | Missing, stale or incomplete evidence | Existing historical evidence and the specific missing input; no current value invented |
| Quality rejected | Evidence exists but fails format, plausibility or signal-quality gate | Raw/research detail and a new-measurement action; no derived physiology |
| Feature unsupported | API, mapping, clock or calibration contract is unavailable | Clear limitation and research-only raw preview where available |

These are presentation categories over the existing detailed `SensorDisplayState` conditions, not a claim that every sensor is clinically interpretable.

## Verification

| Item | Status | Evidence / limitation |
| --- | --- | --- |
| ECG candidate inspection and blocked interpretation | PASS (code/tests) | Unit tests cover candidate/decoded distinction; no waveform or AFib is unlocked. |
| Processed BPM and sustained-alert policy | PASS (code/tests) | Focused `WearHeartRateAlertPolicyTest` and Wear suite pass; no live notification test. |
| Mobile and Wear unit suites | PASS | `:health-engine:test`, `:mobile:testDebugUnitTest`, `:wear:testDebugUnitTest`. |
| Dual-end APK builds | PASS | Mobile and Wear `assembleDebug` and `assembleRelease`; build log `build-verification-ecg-convergence.log`. |
| Paired-device transport, live BPM, high/low reminder, freshness and UI | BLOCKED | ADB listed no devices in this cycle. Build/test is not hardware acceptance. |
| ECG waveform, RR, AFib and medical mV | BLOCKED | No verified event mapping, sample clock or device-specific reference validation. |
| PPG irregular-pulse classification | BLOCKED | No proven optical timeline/channel map and reference validation. |

## Next acceptance evidence

Reconnect both devices; collect a fresh, timestamped processed BPM trace across watch callback, Data Layer, phone storage and screen. Verify a bounded high/low reminder test with the chosen thresholds and actual callback spacing, without inducing unsafe physiology. Then run six-sensor smoke tests so an ECG failure does not suppress other sensor cards. For ECG, obtain OEM event-field/channel and acquisition-clock documentation or a synchronized reference ECG with traceable electrode/gain configuration; replay a de-identified fixture before changing the decoder contract. For AFib research, obtain separately labeled reference data, subject-separated validation and a verified watch waveform first. Until then, ECG status remains `RESEARCH_BLOCKED`; main-app hardware acceptance remains `BLOCKED`, independently of APK build success.
