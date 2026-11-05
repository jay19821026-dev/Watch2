# PM Month Review — Wear Health Bridge — 2026-09-27

## Role and scope

**Owner: PM / evidence and progress.** This is a read-only review of the project records and previously completed independent reviews. No source code, device state, APK installation, or upload was changed in this review. The month-to-date review is cut off at 2026-09-27; it is not a claim that September is complete.

Decision rule: distinguish **verified**, **inferred**, and **unknown**. Do not merge scans without a matching session ID. A file export time is not a measurement time. Build/test success is not hardware acceptance.

## Executive decision

The project has delivered multiple code fixes and green local builds, but it has not demonstrated one uninterrupted, hash-pinned, current-build watch-to-phone scan through visible UI. Therefore the program is **not accepted as end-to-end reliable and not release-ready**. The blocking issue is not one proven sensor defect: it is missing, inconsistent evidence across scan identity, version/artifact lineage, durability, freshness, and UI status. Several concrete code risks also remain.

Freeze feature expansion. The single next acceptance objective is:

> **For one identified mobile/Wear build pair and one new UI-started scan ID, reconcile every catalog channel from watch acquisition through units/status, snapshot, Data Layer, durable phone storage, freshness, export, and visible UI. Every channel must have a truthful terminal state; no old value may masquerade as current and no raw value may be presented as a calibrated physiological measurement.**

This objective validates truthful data handling and supported transport, **not** that every sensor emits data, that all metrics are clinically valid, or that failures are impossible.

## Evidence ledger — keep sessions separate

| Record | Evidence as recorded | Classification and limitation |
|---|---|---|
| 2026-09-25 23:32 session; exported 2026-09-26 00:14 | 28/38 rows carried data; one fresh and 27 stale at export; 0 accepted-quality rows. Raw PPG/ECG/BioZ/SpO₂ packets were recorded, with no trusted units/timebase/calibration. | **Verified in that export only.** Export timestamp did not refresh the measurements. Not a live scan on 9/26. |
| 2026-09-27 07:16–07:19, suite `3e8cab48-c6b3-41f6-9e16-01b047670fb4`, installed 3.13.15/code125 | Terminal `partial`; 28/38 catalog channels associated with session data; 20 fresh, 0 stale; 0 accepted-quality; 0 payload rejects; 42 final-snapshot rejects (38 duplicate, 4 ordering). SpO₂ raw had 3,896 scalar values; no percent calibration. | **Verified for this session/export.** Reject counters are not unique lost-sample counts. `has_data` means received, not valid or clinical. |
| 2026-09-27 09:02:39, suite `e8be9128-21da-437c-b1c2-c9a4eba340d3`; recovery confirmed 09:35 | Package update occurred during a still-running scan; subsequent export marked the same suite `interrupted`. At the earlier export, 2/38 channels had events and one was fresh; many biomedical rows were about 1h44 old. | **Verified interruption/recovery path.** This is not evidence of sensor-driver crash, and stale data is not a current-session sensor failure. Recovery acceptance validates terminal-state delivery only. |
| 2026-09-27 09:58 session, later re-exported | Notes report 26/38 channels, 0 fresh/26 stale, 0 accepted-quality; PPG event timestamps invalid while callback receipt timestamps existed. | **Separate record; no session ID is present in the cited brief.** Do not merge with 07:16 or infer that re-export created a new scan. |
| 2026-09-27 user-confirmed worn/off-charger scan | Another handoff entry reports 24/34 stages with samples, 26/36 catalog channels with raw events, 18 fresh, 0 accepted-quality; OFF_BODY disagreed with the user's worn confirmation. | **Unlinked scan evidence.** No matching suite ID/time/build/hash in that entry; counts conflict with the 07:16 record. Preserve as a separate, unresolved record until identifiers are reconciled. |
| Latest bounded ADB check in this task | Watch: Wear OS 5 / Android 14 API 34 / installed 3.13.16 code126. Phone: Android 16 API36 / installed 3.13.16 code126. Candidate source was reported as 3.13.17/code127. No new scan or install was performed. | **Verified at that ADB check only.** Older handoff entries report code125 and various APK hashes; they are historical snapshots, not proof of the currently installed artifact. |

### Data interpretation

- A nonzero raw/event count proves receipt at a named stage, not usable physiology.
- Zero accepted-quality rows means none met the configured gate; it does **not** mean zero raw data or that hardware is broken.
- “No events,” “invalid values,” “quality rejected,” “uncalibrated raw,” “not attempted/OEM mode,” “stale,” “sync/storage failure,” and “UI-hidden” are distinct outcomes.
- PPG, ECG, BioZ, and raw SpO₂ can be described as received/raw only unless source mapping, units, timing, quality and device-specific calibration are evidenced. No current record supports medical-grade interpretation.

## Month-level findings

### Verified

1. Multiple focused and full mobile/Wear unit-test and Release-assembly runs are recorded as passing. They are software evidence only and their exact applicability depends on the source/artifact snapshot.
2. The code/review history contains real fixes around count semantics, invalid-versus-missing display, HR timestamp/order handling, workout sync throttling, snapshot identity, and retry handling.
3. A same-session watch UI scan and phone export exist for older builds, but the records do not establish a clean current-build end-to-end acceptance run.
4. Current installed version and candidate version differ in the latest ADB record. Historic notes contain conflicting version/hash/install statements; use timestamps plus SHA-256, not version label alone.
5. Internal independent reviews identified concrete algorithm and persistence risks listed below. They are code-path findings; they do not prove specific historic readings were wrong.

### Inferred, not proven

- Repeated scope changes, inconsistent count meanings, and lack of one frozen acceptance gate likely contributed to “fix A, break B” and repeated rework.
- Some blank or misleading values likely arise after acquisition (parse/quality/calibration/persistence/freshness/UI), not solely from absent hardware events. Each instance requires a session-correlated trace to locate its boundary.
- A failed durable write could cause replay/loss edge cases. No device-level induced disk failure has demonstrated a historic loss.

### Unknown / unverified

- Candidate 3.13.17/127 behavior on these devices; one current paired scan on a matching APK pair; user-visible status parity; Data Layer failure/reconnect behavior; process-restart durability; long-duration battery attribution.
- Xiaomi Watch 2 Pro device-level ECG lead/map, ADC-to-mV scale, PPG/SpO₂ transfer function, BioZ ohmic scale, sample clock, and clinical validation.
- Whether any `same_timestamp` counter represented distinct unique measurements lost; existing counters lack sufficient stable identity correlation.

## Independent challenge: counterexamples to tempting fixes

| Proposed shortcut | Counterexample / why it is unsafe | Required falsification test or evidence |
|---|---|---|
| Treat later export time as fresh data | The 9/25 session exported after midnight remained 27/28 stale; export is not acquisition. | Assert per-measurement source timestamps and age across export/UI. |
| Treat every equal-millisecond final as duplicate | Different DataItem URIs can carry distinct final snapshots at the same millisecond; timestamp alone cannot identify the same packet. Conversely, accepting arrival order alone does not guarantee deterministic final state. | Stable producer sequence in ordering key; tests for same-ms different URI, exact replay, and late older packet. |
| Delete DataItem after `SharedPreferences.commit()` returns false because in-memory receipt exists | Process restart clears in-memory retry state; disk may not contain the snapshot/receipt. A later redelivery may be rejected/deleted or applied twice. Current helper tests do not simulate disk failure plus process recreation. | Inject persistence failure, reconstruct storage/process, redeliver same URI; assert not deleted before durable snapshot+receipt and exactly-once logical application. |
| Retry by generating a new UUID path each attempt | Each retry becomes a distinct delivery identity and may duplicate snapshots. | Durable outbox must reuse the same logical capture/URI and be idempotent. |
| Interpret temperature 20–45 as Celsius | Arbitrary raw HAL values may fall inside this interval; plausible range is not unit provenance. | Require source/unit mapping, otherwise preserve raw-only; test missing/wrong unit. |
| Use session duration as sleep duration | SleepSession can contain awake stages; HR-inferred sleep is not ground truth. | Sum classified sleep stages, exclude awake/unknown intervals; label session interval if stages are unavailable. |
| Calculate HRV/SQI from RR count or waveform length | Filtering RR by range does not prove continuity/timebase; ECG minimum score from nonempty RR and BioZ score from point count can reward quantity rather than signal fidelity. | Gate on verified RR timing/continuity and independently validated SQI; test adversarial sparse, duplicated, padded, and malformed arrays. |
| Force sensor/contact gates open or fill missing values | OFF_BODY disagreement and unavailable OEM modes can be real uncertainty; forced acceptance creates plausible-looking false measurements. | Preserve raw data and report channel-specific reason; resolve with synchronized worn/reference evidence, not bypass. |

## Roles and review status

Six internal reviewer seats have supplied bounded reviews: **PM/process**, **data pipeline**, **persistence**, **sensor algorithms**, **release QA**, and **clinical safety**. They are advisory and findings above are reconciled against project records; model consensus is not proof. The latest available work context does not expose a control to launch additional subagents, so this document does not claim forty agents were started.

**Claude:** a prior narrow review addressed Android `SharedPreferences.commit(false)` semantics. It was not a full current-code audit. A fresh Claude audit of the final frozen diff is still pending; no claim is made that Claude approved the current candidate. No raw waveform export is attached to this PM report or sent by this task.

## Single acceptance plan and exit gates

### Gate 0 — Freeze the candidate (engineering/QA)

- Freeze source snapshot and stop feature additions; record immutable revision/archive identity (the current workspace lacks Git metadata, so establish a hash-pinned source snapshot before editing).
- Build mobile and Wear APKs in one recorded invocation; record version/code, timestamps, SHA-256, signer certificate and source identity. Debug-signed artifacts are internal test artifacts, not production-release APKs.
- Run relevant regression tests, both module unit suites, release lint and both release builds. Add the real persistence-failure/restart and same-millisecond ordering tests before claiming durable sync correctness.

**Exit:** one unambiguous pair of artifacts and reproducible source identity; no conflicting “latest” APKs.

### Gate 1 — One paired end-to-end scan (requires explicit install authorization)

- Install only the hash-pinned pair after authorization. Confirm exact package/version/hash on both devices.
- Start from the watch UI and record one new suite ID; no package update mid-scan. Record wear/charging state and provide walking stimulus only during motion-dependent windows.
- For each catalog channel reconcile: registration/start/stop; event count; finite raw count; preview count; source timestamp/unit; quality/calibration state; watch snapshot; Data Layer receipt; durable phone storage; export; displayed freshness/status.
- Preserve bounded logs and state. Terminal channel states must be one of: acquired-and-eligible, received-raw/research-only, rejected/invalid, no-event, not-attempted with reason, stale, sync/storage failure, or UI-hidden. Do not collapse these categories.

**Exit:** all rows reconcile to the same suite ID; no old value appears fresh; no unexplained drop/duplicate at a boundary; statuses agree across export and UI; no app crash/ANR in the correlated window. This proves data integrity for that run, not 100% sensor availability or clinical accuracy.

### Gate 2 — Separate product claims

- Battery soak and notification replay are separate controlled tests.
- Physiological conversions require device-specific mapping, units, clock, calibration, synchronized reference data, and appropriately designed validation. Otherwise keep raw/research-only.
- Production publication additionally requires a production signing key and release process; current debug certificate blocks that claim.

**Exit:** each claim is supported by its own evidence; unresolved calibration remains explicitly out of scope rather than hidden behind software acceptance.

## PM decision / next action

**Status: not accepted; Gate 0/Gate 1 incomplete.** The current bounded instruction is read-only, so do not install or trigger a scan in this review. Next implementation work should address the two concrete transport falsification cases first (durable commit failure + process recreation; equal-time distinct packets with stable sequence), then run Gate 0. Request install approval only when the artifact pair is fixed, and then execute one Gate 1 scan. Algorithm findings should be fixed in separate, source-aware changes with targeted regression tests; do not mix them into the transport acceptance patch.

## Source records

- `docs/AI_REVIEW_EVIDENCE_BRIEF_2026-09-27.md`
- `docs/PM_HANDOFF_SENSOR_SCAN_2026-09-27.md`
- `docs/PROJECT_POSTMORTEM_2026-09-27.md`
- `.agents/skills/healthbridge-debugger/SKILL.md`
