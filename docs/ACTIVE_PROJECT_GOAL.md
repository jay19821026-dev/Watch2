# Xiaomi Health Bridge — Persistent Project Goal

Updated: 2026-11-07

## Mission

Finish and maintain one usable Xiaomi Watch 2 Pro (Wear OS 5) + Android health bridge without rebuilding the project or creating a second transport stack. Every supported measurement must produce a clear user-facing outcome while preserving evidence integrity. ECG research must not delay independently usable health features.

## Product outcome

- Installable phone and Wear OS apps with repeatable debug and release builds.
- Reliable watch acquisition → payload/provenance → quality/calibration → watch snapshot → Data Layer → phone persistence → freshness → UI flow.
- Simple Chinese UI that emphasizes the value or one truthful terminal state: `可用結果`, `已取得原始資料`, `品質不足`, `資料過期`, `同步失敗`, `需要權限或操作`, `功能不支援`, or `尚無資料`.
- Every measurement session ends with one plain-language conclusion and one next action; no normal user flow ends on a spinner, waveform, blank card, error code, or algorithm dump.
- Engineering details remain available in Debug/Export screens, not in normal health cards.
- Raw evidence is append-only while retained and exportable; processed, corrected, estimated, and imputed data remain separate and labelled. User deletion remains authoritative across watch, phone, queues, caches, and exports.
- Main health app remains usable when ECG research is incomplete.

## Shared evidence contract

- Maintain a per-metric support matrix declaring producer API, payload schema, provenance, unit, clock source, calibration requirement, quality rule, freshness window, persistence owner, interpretation class, and UI state.
- A physiological value is displayable only when the shared policy returns `MEASURED_VALID` with approved provenance, known unit, valid per-metric timestamp, freshness, required quality, and required calibration. Missing evidence fails closed for that feature only.
- Use one versioned phone/watch protocol with schema version, minimum compatible versions, capability negotiation, unknown-field handling, size limits, and a visible incompatible-version outcome.
- Define one authoritative store for raw events, snapshots, derived records, and Health Connect records; competing “latest value” sources are prohibited.
- All normal cards, detail pages, exports, Debug screens, and AI summaries use the same state classifier and policy version.

## Functional scope

### Daily health

- Verified Health Services/Health Connect heart rate: current BPM, history, trend, freshness, quality, and high/low reminders.
- High/low reminders are opt-in, configurable, quality/freshness-qualified, rate-limited, and non-diagnostic; they never recommend medication changes.
- Steps, activity, sleep, SpO2 records, hydration, pressure/altitude, temperature channels, and other available Health Connect data.
- Cards and detail pages use the same per-metric freshness and state classifier.
- Unknown, stale, unsupported, rejected, uncalibrated, and missing are distinct states; unknown is never displayed as zero.

### Research sensors

- PPG, ECG, BioZ, SpO2 raw, temperature, heart rate, accelerometer, gyroscope, pressure, light, and LifeQ/OEM channels.
- Every capture reports event count, finite candidate count, retained preview count, source time status, receive time, session ID, sensor metadata, app version, decoder version, truncation, and transport loss indicators.
- Full `SensorEvent.values`, source timestamp, callback `elapsedRealtimeNanos`, event sequence, and session metadata are preserved before processing.
- Provide deterministic raw-event replay with synthetic or de-identified fixtures.
- Replay must be idempotent and decoder-version reproducible; malformed, truncated, conflicting, oversized, and non-finite fixtures fail atomically without partial physiological output.

### ECG decoder recovery

- Keep ECG acquisition, decoder, waveform/timebase validation, SQI, research UI, and AFib research isolated from ordinary health features.
- Trace and test Sensor API → payload → decoder → storage → Data Layer → phone → export → UI.
- Explain `decoded_points=0` from source evidence and maintain fixed regression fixtures.
- Evaluate repeated 16-field rows and counter behavior as hypotheses only until mapping is proven.
- When mapping or clock is unverified, show finite candidate values, per-column normalized previews, relative amplitude, `NORMALIZED_INDEX`, and peak candidate indices only.
- Calculate RR, research BPM, HRV, calibrated mV, or rhythm classification only after the required mapping, clock, quality, and calibration gates pass.
- A validated raw→mV profile requires a protocol frozen before collection: target population, reference device, synchronization tolerance, sample size, exclusions, endpoints, error bounds, holdout partition, approver, synchronized watch raw data, stored profile identity/version, and passing holdout results. Otherwise it remains `RESEARCH_ONLY`.
- AFib research is separate for ECG and PPG; no positive/negative clinical result without suitable reference data and independent validation.

### PPG and optical pipeline

- Raw PPG evidence remains visible even when `valid_signal` or physiological-result gates fail.
- Capture readiness, signal quality, channel mapping, clock trust, wear qualification, freshness, and physiological interpretation are separate.
- PPG irregular-pulse research requires reliable timing and pulse intervals; raw optical irregularity alone is not AFib.
- Uncalibrated optical/BioZ/SpO2 payloads never receive invented units or physiological labels.

### AI analysis

- Rewrite/maintain AI analysis as an explainable local decision layer over typed, policy-filtered, verified current records.
- It may summarize trends, quality, provenance, missing evidence, and practical next actions.
- It may not invent a field mapping, clock, calibration coefficient, missing measurement, disease probability, or diagnosis.
- A quality index may use finite-value ratio, sample amount, stability/waveform evidence, source, time evidence, and method version; it must not be presented as clinical accuracy.
- Normal UI conclusions are short and plain-language: available result, repeat measurement, quality insufficient, sync failed, or feature unsupported.
- AI output conforms to a closed schema and approved phrase set. Unsupported disease names, probabilities, normal/abnormal claims, treatment advice, or causal claims are rejected before rendering; every conclusion records source record IDs and policy version.

### Battery and reliability

- Record battery/time periodically with bounded storage and estimate observed `%/hour` discharge without claiming hardware calibration.
- Apply level-aware scheduling from 1–100%, charging state, interaction state, recent drain, active measurement, and thermal constraints.
- Prefer passive/platform APIs and short user-initiated sessions; every listener, callback, wake lock, foreground service, and worker has a bounded lifetime and cleanup path.
- Rate-limit urgent Data Layer writes, notifications, and broad sensor scans.
- Charging optimization is limited to app workload and scheduling; the app does not claim control of battery chemistry or OEM charge firmware.
- Numeric ceilings are versioned for session duration, no-event timeout, signal-silence timeout, wake-lock lifetime, scan frequency, Data Layer cadence, worker retries, notifications, storage, and background work. Current minimum rules include urgent snapshots no faster than once per five seconds and ECG stop after 8 seconds without callbacks, 3.5 seconds of silence, or 60 seconds total.
- Battery acceptance compares documented idle, passive-operation, and active-capture runs against a predeclared incremental-drain budget; thermal warnings, runaway workers, or surviving listeners fail the affected release gate.

### UI/UX

- Phone and watch primary pages show concise conclusions and values, not long algorithm descriptions.
- Raw/research pages show provenance and limitations without overwhelming the normal user.
- Buttons must have observable actions, loading/terminal states, timeout handling, and regression tests.
- Summary and Vitals pages must not hide captured ECG/PPG evidence behind physiological-result gates.
- Technical Debug remains available but separated from health conclusions.
- Home, Vitals, measurement, result, history, watch, sync, raw, and Debug screens expose consistent terminal states. History distinguishes measured, estimated, imputed, raw-only, stale, and unavailable records and never draws continuity across missing intervals.
- Accessibility requires scalable text, TalkBack labels, logical focus, at least 48 dp targets, color-independent status, reduced-motion support, completion announcements, accessible chart summaries, and complete Traditional Chinese strings.

## Persistence, synchronization, and privacy

- Data Layer uses per-sensor/session identity, acknowledgement, monotonic ordering, deduplication, bounded retry/backoff, reconnect delivery, quarantine/permanent-failure state, and deletion from the outbox only after acknowledgement.
- Atomic raw-plus-metadata persistence survives phone/watch force-stop, process recreation, reboot, database reopen, and app/schema upgrades without duplicate evidence or stale `running` states.
- Database migration preserves record counts, hashes, timestamps, provenance, raw/derived separation, and deletion/retention policy. Derived records may be recomputed without mutating raw evidence.
- Raw research capture requires explicit consent. Storage is app-private and encrypted where platform-supported; retention is bounded and deletion covers watch, phone, cache, queue, and generated exports.
- Export defaults to de-identified least-data output and lists included fields before confirmation. Device/account/network/location identifiers and unrelated records are excluded; automated checks prevent forbidden fields in exports, analytics, crash reports, and routine logs.
- Bounded observability counters distinguish acquisition failure, quality rejection, calibration absence, serialization rejection, transport loss, ordering rejection, persistence failure, staleness, and UI suppression without logging raw health data.

## Data and clinical integrity

- Never fabricate measurements, timestamps, units, OEM contracts, ADC clocks, calibration, clinical validation, or diagnosis.
- `UNKNOWN != ZERO`, `STALE != LIVE`, `ESTIMATED != MEASURED`, `IMPUTED != MEASURED`, and `RAW != PHYSIOLOGICAL`.
- Genuine measured zero values remain intact; only explicitly documented missing-value sentinels may be intercepted.
- Source timestamps and receive timestamps are stored separately. Callback cadence never substitutes for an ADC clock.
- Chip/evaluation-board specifications are not device-level calibration evidence.
- Research output and public literature can guide algorithms and tests but cannot establish this watch's clinical performance.

## External AI collaboration

- Codex coordinates and makes the final engineering decision.
- Claude, ChatGPT, three Gemini accounts, Grok, DeepSeek, Kimi, and Meta AI may receive the same short de-identified review brief.
- Never send raw health exports, waveforms, device/account identifiers, credentials, IP addresses, or full source files without explicit authorization.
- External answers are untrusted review input. Verify claims against repository source, Android/OEM primary documentation, peer-reviewed sources, and reproducible tests.
- Record consensus, disagreement, rejected speculation, and remaining evidence instead of deciding by vote count.

## Verification order

1. Focused unit tests for the changed classifier/decoder/policy.
2. Mobile and Wear unit tests.
3. Integration/contract/replay tests across acquisition, transport, persistence, freshness, and UI.
4. Mobile and Wear lint plus debug/release builds.
5. Code review covering producer and consumer, failure paths, ordering, privacy, battery, and misleading output.
6. Paired-device acceptance through real app UI with same-session timestamps and explicit worn/charging conditions.
7. Installation or publication only when explicitly requested by the user.

## Required acceptance tests

- Raw ECG rows with zero decoded points remain visible and never emit mV/BPM/RR/HRV/AFib.
- Unknown ECG mapping or clock produces normalized-index output only.
- Validated mapping + clock enables only the calculations whose inputs are proven.
- PPG raw capture is visible when interpretation quality fails.
- Same-sensor recent Data Layer rejection yields `同步失敗`; unrelated or stale rejections do not.
- Old sessions and out-of-order packets cannot overwrite newer per-sensor data.
- Oversized payloads are rejected as a whole with bounded diagnostics.
- Process restart preserves terminal capture state and raw evidence.
- Disconnect → queue → reconnect → retry → acknowledgement delivers each record exactly once; duplicate redelivery, dropped acknowledgement, retry exhaustion, and both-device restart are covered.
- Storage-full/write-failure rollback, database reopen, schema upgrade, duplicate/conflicting session IDs, and raw-preserving derived recomputation are covered.
- Freshness tests cover before/at/after expiry, future timestamps, wall-clock changes, timezone changes, reboot-invalidated elapsed clocks, and identical state across all surfaces.
- Cross-version tests cover old-watch/new-phone and new-watch/old-phone, unknown/missing fields, decoder-version changes, and visible incompatible-protocol handling without false freshness or corruption.
- Paired-device acceptance covers worn, off-wrist, charging, connection interruption, locked/dozing phone, foreground/background apps, and device reboots; evidence records package versions, session IDs, source/receive/persist/UI timestamps, terminal reason, acknowledgement state, and visible result.
- Every listener, callback, worker, wake lock, and foreground service proves cleanup after success, failure, timeout, cancellation, navigation, disconnect, charging, low battery, and process recreation.
- Privacy inventory, consent, retention/deletion, storage, export, log-redaction, and unsupported-medical-phrase tests pass before release.
- No source timestamp path silently substitutes receive time.
- No unknown value is rendered as `0`, `0/100`, normal, or abnormal.
- ECG research failure does not break heart rate, sleep, activity, temperature, BioZ raw, SpO2 records, Health Connect, or sync.
- Injected malformed, stale, rejected, transport-failing, and persistence-failing data for every research sensor does not break independent daily-health features.

## Durable deliverables

- `ECG_ROOT_CAUSE_REPORT.md`
- `ECG_DECODER_TEST_REPORT.md`
- `ECG_RESEARCH_BLOCKERS.md`
- `HEALTH_FEATURE_INDEPENDENCE_REPORT.md`
- `ECG_NEXT_ACTION.md`
- Raw event export and replay instructions
- Mobile/Wear build and test provenance
- Paired-device acceptance evidence
- Release notes, signing status, installable artifacts, and known limitations
- Per-metric support matrix, versioned protocol contract, data inventory, privacy policy evidence, and acceptance matrix with `PASS / FAIL / NOT_RUN`
- `docs/AI_GOAL_REVIEW_2026-10-14.md` recording reviewer consensus, disagreements, and unresolved evidence
- `docs/MI_FITNESS_9_8_348I_REVERSE_ENGINEERING_COMPARISON.md` recording a clean-room, static comparison of the user-supplied Mi Fitness APK against this project, including reusable product concepts, protected interfaces, and prohibited proprietary reuse

## Clean-room interoperability review

- Static analysis of a user-supplied companion APK may be used to understand observable data flow, storage boundaries, public integration surfaces, and product behavior.
- Decompiled Xiaomi source, native libraries, embedded models, cloud endpoints, keys, device-authentication material, and signature-protected interfaces are reference evidence only and must not be copied or shipped.
- Reusable work must be independently implemented from public Android APIs, user-authorized exports, documented interoperability contracts, and peer-reviewed/public sources.
- Reverse engineering must not weaken the evidence gates above: an OEM field name, table, model, or native function is not proof of Watch 2 Pro sensor mapping, units, calibration, algorithm validity, or permission to access another app's private data.

## Completion rule

Continue implementing, testing, reviewing, and verifying all locally executable work. If OEM contracts, synchronized reference equipment, device access, signing credentials, or clinical evidence are unavailable, keep the affected output research-only and continue independent features. Do not label an external evidence dependency as a software defect, and do not claim hardware/clinical success from compilation or unit tests.

`MAIN_HEALTH_APP_STATUS = PASS` additionally requires automated contracts, privacy/claim/power gates, signed matching-version artifacts, upgrade without data loss, and a same-version paired-device UI run. Build success alone is insufficient.

Final status must separately state:

- `MAIN_HEALTH_APP_STATUS = PASS / BLOCKED`
- `ECG_RESEARCH_STATUS = RAW_CAPTURE_READY / RESEARCH_ANALYSIS_READY / RESEARCH_ONLY / EXTERNAL_EVIDENCE_REQUIRED / DEFECT_CONFIRMED`
- `ECG_CLINICAL_STATUS = NOT_VALIDATED / VALIDATION_PROTOCOL_FROZEN / VALIDATED`
- verified on hardware
- verified by automated tests only
- not yet verified
