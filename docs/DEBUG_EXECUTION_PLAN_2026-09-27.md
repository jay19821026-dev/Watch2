# Debug execution plan — 2026-09-27

## Objective

Stop repairing symptoms one at a time. Establish one reproducible, per-session evidence chain from watch acquisition through phone display, then fix only the earliest failed stage and run the same regression path. This is a research-data reliability goal, not a promise of 100% sensing or medical accuracy.

## Evidence and decision

- Last known watch export still describes the 09:58 partial scan: 26 stale channels, 0 fresh, 0 quality-accepted. No new scan has been captured. Current `.tooling/android-sdk/platform-tools/adb.exe devices -l` returns no connected device, so current runtime state cannot be confirmed.
- Old scan proves raw acquisition happened for some channels and phone snapshots were accepted at least once; it does not prove a new scan ran, nor that all phone persistence/UI stages succeeded.
- PPG event timestamps were reported invalid with fill-heavy values; callback receipt time is not a sensor/ADC clock. ECG event ordering is not its intra-event ADC rate. BioZ has no verified physical unit. Do not derive physiology or calibration from these.
- Transport counters were 0 payload rejects, 38 duplicate/timestamp collisions and 4 ordering rejects. This is not evidence of wholesale transport loss; duplicates may be expected snapshot overlap. Inspect exact same-session producer/consumer identity before changing deduplication.
- `wear=false` is a sensor report, not conclusive proof that the user was not wearing the watch. Preserve the gate and record provenance; do not attribute every missing channel to off-body.
- No bounded recent app fatal/ANR was seen; no app notification was active in the prior snapshot. Neither proves absence of all battery cost or all lifecycle failures.
- Eight independent reviewers (five internal roles plus Gemini, ChatGPT and Claude) converge on stale/unverified session, raw-vs-valid separation, and missing end-to-end evidence. Reject overconfident claims that Doze, no notification, off-body, or HAL alone has already been proven as the root cause.

## PM work orders

1. **QA / evidence owner — one canonical session trace.** For each UI-started run, correlate existing `suite_id` across phase start/end, sensor registration result, callback/event counts, source timestamp validity, raw sample count, quality/calibration decision and reason, publish task outcome, phone receive/store/reject outcome, and per-channel freshness/UI state. Keep bounded counters and one export; no continuous high-rate logs or raw waveform upload.
2. **Sensor engineer — isolate acquisition from interpretation.** First run a single short, foreground PPG probe; record event timestamp and `elapsedRealtimeNanos()` separately, sample count/fill metrics, registration result, and wear-sensor provenance. Do not infer sample rate from callback cadence. If timestamps remain invalid on a fresh session, report it as an acquisition-clock contract failure and retain raw data only.
3. **Sync engineer — explain each rejection.** For the same session, pair producer and consumer IDs/sequence and reject reason. Determine which of the 38 collisions are expected overlap versus distinct samples lost to timestamp-keying; change identity/dedup only with a regression reproducer.
4. **Mobile engineer — prove persistence and presentation.** For each received test snapshot, assert stored session/time and the same state in export, health card, and detail page. Differentiate no event, stale, rejected quality, uncalibrated raw, transport delay, and hidden UI.
5. **CI/QC — one end-to-end regression matrix.** Fixtures cover valid/zero/negative/non-monotonic timestamps, fill/flatline/NaN payloads, duplicate/out-of-order delivery, stale and future timestamps, wear unknown/false/true, interrupted/partial sessions, and successful store/display. Preserve raw packets while blocking unsupported physiological conversions.
6. **PM release gate.** Do not mark fixed until one fresh UI-originated session yields a matched watch/phone `suite_id`, terminal state, expected counts and explicit reasons at every stage; rerun targeted plus both app test suites/builds. Hardware success, compilation, and clinical validation are separate gates.

## Next action / blocker

ADB currently sees no devices. Reconnect the watch/phone and provide a fresh Wireless Debugging endpoint if it changes. Then run only the short UI-started PPG falsification probe first; do not run a full 38-channel sweep while the watch is at low battery. Use its session trace to assign the earliest concrete failing boundary, then parallelize code work across the responsible roles.

## Source review notes

- Existing code already has `suite_id`, per-channel raw snapshots, timing diagnostics, freshness status, and categorized phone rejection counters. Prefer completing correlation/acceptance across these existing pieces rather than adding a second framework.
- Wear health FGS permission preflight exists and the manifest declares `FOREGROUND_SERVICE_HEALTH`; old crash logs alone do not establish a current permission bug. Validate the actual UI-start path and current runtime grants before altering it.
- The last export's raw signals and calibration blocks are distinct. Keep raw/research plots, but do not label raw arrays as clinically valid signals or map them to bpm, SpO₂, mV, Ω, body composition, or diagnosis without device-specific evidence.
