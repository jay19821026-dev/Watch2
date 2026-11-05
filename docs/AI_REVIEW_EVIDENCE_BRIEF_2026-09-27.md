# AI Review Evidence Brief — 2026-09-27

## Scope and privacy

This is a de-identified engineering summary for independent review. It excludes account/device identifiers, timestamps more precise than scan windows, user profile values, and raw waveforms. Counts and sensor statuses below are device data, but no physiological interpretation is requested. Do not infer calibration or clinical validity from them.

## Keep scan sessions separate

### 2026-09-27 07:16–07:19 local, installed 3.13.15/code125

- UI-started full suite ended `partial`; 28/38 catalog channels had data associated with that session, 20 were fresh at export, none were stale, and 0 met the configured accepted-signal-quality rule. Payload rejects were 0.
- Export had 35 session rows: 21 `has_data`, 3 `research_signal_unscaled`, 1 `research_optical`, 1 `invalid_optical`, 5 phone-side `no_events`, 2 `needs_motion`, 1 `off_wrist`, and 1 derived estimate. `has_data` means received, not valid/calibrated.
- Sync diagnostic: 42 final snapshot rejections = 38 same-timestamp duplicate and 4 ordering; 0 payload rejects. These counters do not prove unique data loss.
- SpO₂ raw: 487 events / 3,896 raw scalar values, no OEM scale for a percentage. PPG/ECG/BioZ remain raw/research-only. LifeQ and MI SpO₂ registered but ended `needs_oem_mode`/no events. Motion channels need an actual walking stimulus.
- Temperature: separate wrist surface channel produced raw data; it is not core temperature. A thermometer channel produced events but no valid Celsius vector and should be classified invalid-values, not as an empty acquisition.
- No app fatal/ANR was recorded in the bounded scan logs; battery drain was not controlled or established.

### 2026-09-25 23:32 local session, exported 2026-09-26 00:14 local

- Local JSON export (`sensor-suite-check.json`) has 38 catalog rows, 28 with data, 1 fresh Health Services HR row and 27 stale rows at export, 0 accepted-quality rows. Session is older than export by about 43 minutes; its later file modification/export time is not a new scan.
- This export's sync diagnostic has 48 final-snapshot rejections, 0 payload, 0 ordering, 0 duplicate counters in that particular export snapshot; one recorded last rejection is an older/duplicate Health Services HR sample. Do not merge these counters with the 07:16 suite's 42 rejections.
- It records raw/research status for PPG (8 events, quality 25, sample rate unreported), optical MAX channel (487 events, quality 40, preview capped), raw SpO₂ (487 events, no percentage conversion), ECG (219 events / 2,190 reported values), BioZ (418 events / 510 reported values), plus stale motion/environmental sensor rows. The old export schema does not consistently carry v2 finite-raw counts; treat `sample_count` as legacy/ambiguous for these rows.

### 2026-09-27 09:58 local session, later re-exported

- Existing project notes say terminal `partial`, 26/38 session channels, 0 fresh / 26 stale, 0 accepted-quality, 0 payload rejects at the later check. Re-export time did not refresh measurement timestamps.
- Historical raw counts in that separate session: PPG 128, ECG 2,140, BioZ 3,304, raw SpO₂ 3,960. PPG sensor event timestamps were reported invalid while callback receipt timestamps existed; receipt time is not a source/ADC clock. ECG event cadence is not an intra-event ADC sampling clock.

## Current source/test state

- Source/build version: mobile + Wear 3.13.18/code128. The newest artifacts were built after the installed packages' last-update times; they were not installed or freshly scanned on hardware in this review.
- The count contract distinguishes `raw_value_count`, `event_count`, and preview points. A follow-up removes the v2-biomedical legacy fallback from exported `sample_count`; unknown exports as null and compatibility value is separate. Raw snapshot DataItem paths now have unique suffixes so same-kind preview/final at the same millisecond cannot alias.
- Follow-up receipt/recovery audit: Claude identified a null-outcome ambiguity where failed recovery could be treated as not-applicable; six internal reviewers identified bounded pending eviction, pending/receipt ledger coupling, legacy-receipt migration, same-timestamp ordering, and missing crash-injection coverage. ChatGPT separately confirmed old scan counters are cumulative and must not be attributed to one scan, and flagged receipt replay ordering.
- Current code separates `NotApplicable`, `RecoveryFailed`, and `Recovered`; co-commits receipt and pending-work IDs with the raw snapshot; does not evict unresolved pending work (new work is retained unacknowledged at capacity); processes pending even after receipt-history eviction; attempts ordered recovery for valid legacy receipts; and uses session/sequence tie-breaking for equal timestamps. Invalid formula BPM falls back to a valid Health Services average. Diagnostic dump exceptions are isolated from the persisted-data acknowledgement path.
- Forced mobile/Wear Debug unit suites passed: mobile 260/260 tests, Wear 112/112. Both Debug and Release APK builds passed; APK v2 signatures verify. Release signing is configured to use the debug key, so these are not Play Store signing artifacts. This does not verify Data Layer round-trip, process-death recovery, final DataItem deletion success, fresh sensor acquisition, UI, or battery behavior.
- Project-local ADB now sees a phone (Android 16) and Watch 2 Pro (Wear OS 14); both installed packages report 3.13.18/code128, with install times earlier than the just-built APKs. Phone was USB-powered and watch was not charging at inspection. A bounded recent logcat filter returned no sensor-snapshot or crash lines; absence in that window is not proof of successful scan. No fresh scan was run and the new APKs were not installed. Workspace has no Git metadata, limiting source-history attribution.

## Required reviewer output

Each reviewer must produce one concrete minimal code patch (or exact Kotlin/API change) with a falsifiable test, plus the strongest counterexample and missing evidence. Focus on source-to-phone data reliability, JSON compatibility, unique snapshot identity, DataClient final-write failure recovery, and one coherent producer/consumer contract test. Do not recommend formula/calibration changes unless inputs, units, device mapping, and validation evidence support them. No consensus or code review substitutes for a fresh paired-device run.
