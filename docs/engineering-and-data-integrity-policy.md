# Engineering and Data Integrity Policy

## 1. Health data and calculations

- Never fabricate measurements, units, sensor provenance, calibration, clinical validation, or diagnoses.
- Keep these states distinct: valid measured zero, no record/not measured, permission denied, read failure, stale, invalid timestamp, rejected quality, and uncalibrated raw data.
- A valid measured zero must be explicitly reported by the source and pass type-specific validation. An empty query, missing aggregate, `null`, or absent metric is not zero; never create zero by defaulting missing data.
- Do not use missing, rejected, stale, uncalibrated, or unknown values as inputs to current-value displays, trends, or health scores. Explain why an output is unavailable.
- Health estimates remain non-diagnostic. Raw waveforms and chip specifications do not establish device-level calibration or clinical validity.

## 2. Measurement time and freshness

- Use a sensor-provided, parseable measurement timestamp with a known time basis to determine freshness and ordering.
- Maintain a versioned freshness policy by metric, including maximum age, source time basis, conversion rules, and future-time tolerance. A metric without a configured rule cannot be classified as current/fresh.
- The freshness policy table must record its version and effective date. Tests must cover within-window, just-expired, future-boundary, missing, malformed, and unconvertible timestamps. `timestamp_unknown` records may be retained for history but must not determine ordering, current values, trends, aggregates, or scores.
- Keep measurement time separate from publication, receipt, and synchronization times.
- For legacy snapshots without a trustworthy measurement time, preserve the raw record and its publication time, mark `timestamp_unknown`, and exclude it from current values, trends, and scores. A recent publication time must not make it appear fresh.
- Define units and time bases for every timestamp. Epoch wall-clock times must not be compared directly with elapsed-realtime or sensor monotonic clocks without a traceable conversion.
- Mark future or malformed measurement times `invalid_timestamp`; do not treat them as fresh or silently downgrade them to `stale`.
- UI, persistence, and export must use the same status classifier.

## 3. Health Connect sources and duplicates

- Preserve Health Connect `dataOrigin`, record type, source record ID when available, measurement time, units, and raw source record; show source-specific records and totals.
- Do not add records across sources or sum overlapping records within one source into a health score or total unless a documented, validated rule exists for that record type, source, time range, and overlap semantics. A source-priority rule must be supported by source documentation or project validation; package/brand name alone is not evidence.
- Similar timestamps or values are not proof of duplicate measurements. Preserve cross-source records and, when useful, label them as possible duplicates; do not silently delete or merge them.
- Exact repeated delivery may be handled idempotently only with a stable key scoped by `dataOrigin + record type + source record ID`. Matching time/value alone is insufficient to discard a record. Without a stable source ID, preserve the record and expose ambiguity.
- For any permitted aggregation, retain the source set, time range, overlap resolution, and rule version as provenance. If overlap semantics cannot be established, preserve the records but do not produce a merged total or score.
- If source ambiguity changes an aggregate or score, show source-specific results or withhold the combined result with an explicit explanation.

## 4. Diagnostics, logs, and exports

- Do not write raw physiological values or waveforms to Logcat, background diagnostic files, or automatic uploads.
- Separate a user-initiated measurement's operational capture/phone synchronization from diagnostics export. Any raw capture retained beyond the active measurement session must have an explicit feature purpose, app-private storage location, encryption status, retention period, and deletion path. Background creation of raw-waveform diagnostic artifacts is prohibited.
- Default diagnostics to a minimized summary. Raw waveform export is off by default and requires a separate, per-export user action and confirmation.
- Before export, preview data categories, time range, destination, retention period, and identification risks. Cancellation must leave no export artifact.
- Store app-managed raw exports only in app-private encrypted storage using a verified Android Keystore-backed key. Do not claim encryption until the implementation and key lifecycle are tested.
- Default app-managed raw-export retention is 24 hours, with immediate deletion available. Perform expiry cleanup on app launch and before export; document that exact-time deletion cannot be guaranteed while the app/device is not running.
- Warn that copies sent to another app or destination are outside this app's deletion and security control. Never upload automatically.
- External model reviews may receive only the minimum sanitized technical summary. Never send raw health exports/waveforms, credentials, device identifiers, or complete source files to external models under this policy.
- Before each external-model review, disclose the recipient, purpose, and data categories and obtain explicit user consent for that review. Without it, send no project content; any permitted summary must first remove identifying data and remain limited to the assigned technical question.

## 5. Change and verification policy

- Each delivery batch has one primary root cause or acceptance objective. Necessary cross-layer changes may ship together when required for a consistent contract; list the dependency and test each affected layer.
- For cross-end changes, run mobile and Wear tests and release build checks. Add contract tests for producer/consumer fields, ordering, replay, stale data, and failure states where applicable.
- Sensor-suite time bounds must be derived from the actual channel queue and active timeout policy, including overrides and special cases; verify the worst-case total remains below the global deadline with cleanup margin. A timed-out/partial scan is not a completed scan.
- The automated timing test must use the production queue and production timeout resolver (including overrides), calculate worst-case duration plus an explicit cleanup reserve, and assert it is below the global deadline. A separately hard-coded sum is not sufficient. Timeout tests must also verify partial-result reporting and listener/service/wake-lock cleanup.
- A build or unit test does not prove a sensor works. Hardware acquisition and end-to-end sync are accepted only from a fresh, user-initiated real-device run, with evidence across acquisition → payload/units → quality/calibration → watch snapshot → Data Layer → phone storage → freshness → UI.
- Record the test time window, app versions, scan/session ID, and bounded outcomes. Historical logs or scans do not count as current-run acceptance.
- For every release candidate, record source revision, build version, package IDs, APK SHA-256 hashes, and signing identity; both app artifacts must map to the same delivery version. Artifacts without a traceable source revision cannot pass release.
- Do not claim medical-grade accuracy or diagnostic performance without device-specific calibration and appropriate clinical validation.

## 6. Release gate

- A Release APK must be signed and verified with the authorized release signing identity, compatible with the intended existing install/update chain. Verify package ID, version, and signature certificate/rotation evidence.
- Unsigned APKs and debug-key APKs are not release deliverables.
- Release build success, signature verification, installation, and real-device health-data acceptance are separate gates; report each independently.
- Never place signing passwords, keys, tokens, or other secrets in source control, logs, exported diagnostics, or this policy document.

## Policy review and evidence

- A timeout decision is version- and code-path-specific, not a permanent product rule. Keep the queue-derived timing test and record the verified per-channel timeout policy for each release candidate.
- AI consensus is not evidence of sensor calibration, OEM behavior, or medical validity. Independent reviewers receive only the minimum sanitized technical context needed for their assigned review.
- A user-initiated watch-to-paired-phone Data Layer transfer required by an active measurement is local operational sync, not external upload; it must still follow the measurement purpose, storage, retention, and deletion rules above.
