# Device acceptance checkpoint — 2026-10-03

## Verified software and installation

- Mobile 559/559 and Wear 178/178 Debug unit tests pass. Both Release assemblies/lint pass. New tests cover feature field provenance and invalid equal-timestamp HR replays; they remain policy tests rather than DataMap/SharedPreferences integration tests.
- Both Debug APK signing certificates match the previously installed identity: SHA-256 `0c35a370032bfe706ef54ec6a789bcdc7e53d19c94b444fa6b67027a3cb8750f`.
- Both updates returned `Success`. Phone APK was pulled after installation; its hash matches the locally built Debug artifact. Watch install/update time was observed before its wireless ADB connection dropped.
- Mobile Debug APK SHA-256: `FDCE4CF5877F00B144BFD867F3F50E71FF537C67FF22B14A70F88990FAC9A848`.
- Wear Debug APK SHA-256: `45E8B90B4F95FAB94C686C0A1F921A9FBF9211118E11AFF6112AEFAE08110DD8`.
- Both retain versionCode 147 / versionName 3.13.37. Hashes identify this source batch; version number alone does not distinguish it from earlier builds.
- Phone reports Android 16 / API 36; watch reports Android 14 / API 34. Watch BODY_SENSORS and ACTIVITY_RECOGNITION grants were observed. The API-36 READ_HEART_RATE entry is ungranted on this API-34 watch; the applicable BODY_SENSORS grant is present.

## Runtime evidence

- Phone engineering UI obtained a correlated watch status response at 01:07:22. Response reports control connectivity, no charging and inactive suite. This verifies status-query round-trip, not full sensor acquisition.
- Phone persisted a Health Services HR field with measurement timestamp `1790960929909` and source `health_services_datapoint_epoch`. It is separate from the previous suite and does not prove which HR producer route delivered it or restart durability.
- The latest observed full-suite ID remains `fbe90950-f8db-4d0a-b273-be03b322eaa0`, started at `1790940851912`, ended at `1790941013388`, state `partial`. This historical suite is not counted as a scan of the newly installed batch.
- Phone UI navigation reached Engineering Debug. ADB-injected taps on its scan button did not expose the consent dialog. Cause is not established. User was asked to initiate the same UI scan action by touch.
- Temporarily changed phone `stay_on_while_plugged_in` from `0` to USB mode for the unlocked acceptance session. Restore `0` after the session.
- Raw local artifacts, UI hierarchy/screenshot and before/current preferences are under `build/acceptance/20261003/`. No health artifacts were sent to external reviewers.

## Scan 1

- UI keyboard focus opened the normal scan consent dialog and activated its affirmative button. No direct service launch was used.
- Session `340fb61b-f7ce-41ab-bc60-b74adaa088c1`: start `1790961401216`, end `1790961563096`, state `completed`; duration about 162 seconds. This terminal state means the scan procedure finished, not that all sensor measurements succeeded.
- Received 35 final entries with this session ID. They include one derived orientation entry; they are not 35 independent physical sensors. Entry states: has_data 22, research_signal_unscaled 3, needs_motion 4, no_events 2, derived_estimate 1, invalid_optical 1, research_optical 1, worn 1. See `scan1-channel-final.csv` for per-entry event/sample counts and source.
- ECG delivered 214 raw events and zero decoded waveform samples. PPG delivered 8 events/128 raw samples with missing event timestamp. BioZ delivered 423 events/3384 raw samples with timestamp ahead of capture. Raw SpO₂ delivered 495 events/3960 raw samples with SensorEvent time provenance. These remain raw acquisition evidence; no calibrated percentages, impedance, ECG voltage or diagnoses are established.
- Phone force-stop removed the old process, followed by a new process after MainActivity launch. Compared 284 persisted fields across 35 entries plus suite markers; no differences. Before/after XML and process IDs are saved in `scan1-restart-report.json` and associated artifacts.
- Opened Health Center after restart. UI shows this scan's start time and procedure-completed state, 28 entries with readings, 11 within display freshness window, 2 stale and 15 with unknown timestamp. This is one rendered overview check; detailed per-channel UI remains to be reconciled.
- New scan count is one. The previous historical partial scan remains outside this installed-batch count.

## Scan 2 and next checkpoint

- The second normal App UI consent action was submitted at epoch `1790962233436`; USB disconnected before a new suite ID could be read. This attempt is pending, not silently excluded or declared successful.
- On continuation, ADB lists no devices. Recover the existing attempt's session/state before starting another scan; do not equate transport loss with acquisition failure.
- A fresh independent QA task has been dispatched. Prior agent IDs are unavailable in this runtime; their previous deliverables remain historical evidence, not current activity.

## Remaining acceptance

| Gate | Status | Evidence required |
|---|---|---|
| Three non-overlapping UI-started scans | 1/3 observed | Two additional sessions, including failure/partial/cancellation accounting |
| Watch capture → phone receipt | Scan 1 partial evidence | Same-session raw payload entries received; independent watch export still needed |
| Persistence/restart readback | Scan 1 verified | Repeat same-session comparison for remaining scans |
| UI/source/freshness agreement | Pending | Rendered values/states reconciled to the same persisted fields |
| Formal Release signing | Pending | Authorized release key and verified signed artifacts |

Full project acceptance remains open.

## Collection harness review

- Independent QA corroborated scan 1 CSV session/final markers and full before/after persisted XML equality. These support phone persistence, not device calibration or all physical sensors succeeding.
- QA found a collection-harness defect: a single channel's final marker could stop polling before the suite finished. The collector now uses the expected `suite_session_id` and aggregate `suite_run_state` terminal marker. Procedure termination still does not prove every channel arrived or succeeded.
- PowerShell parser reports zero syntax errors. With no ADB devices, one bounded poll records `transport_unavailable`, does not declare completion and creates zero raw XML files.
- Regression harness work and Hermes/Grokbot endpoint discovery have been separately assigned to actual newly spawned agents. No external AI connection or private-data upload has been established.
- Collector regression implementation has now been independently executed: 13 cases / 17 assertions pass using local mocked ADB. Terminal state may end collection even when channels remain incomplete; such results are explicitly not full-channel acceptance. No Android code changed in this harness batch, so APK rebuild/install was not performed.
- Final QA flagged ambiguous auto-latching without prior-session identity. Collection now requires an expected suite or a verified previous suite before any ADB read. The 13-case harness still passes; a separate missing-identity rejection check passes. The caller must verify the previous ID; this guard cannot infer an unknown historical session. Remaining harness coverage gaps include empty snapshots, exceptions, malformed field types and polling exhaustion.
- Expanded harness independently rerun: 20 cases / 35 assertions, zero failures. Added identity rejection, empty snapshot, unknown run state, missing suite fields and polling exhaustion. These are mocked collector tests; exceptions and malformed field types remain separate coverage gaps. Mobile ECG audit of scan 1 found no supported evidence of BPM/RR/HRV leaking from the zero-decoded unverified payload; persisted summary wording and clinical-page guards both withhold physiological output for this artifact. Rendered per-channel device verification remains open.
- Wear timebase audit found PPG persisted event timestamps negative while callback clocks were positive; `timestamp_missing` includes nonpositive values, not only absent fields. BioZ compares the event timestamp against a later callback elapsed clock, so ordinary app processing delay does not explain ahead-of-capture. Its final source is overwritten per event and does not prove all 423 events were invalid. BioZ lacks per-event event/capture pairs, preventing quantification or OEM attribution. Resolver boundary regressions are assigned; no clock substitution or calibration change is justified.
- Resolver boundary regressions delivered: five added cases; targeted JUnit result confirms 9 tests, zero failures/errors. These are pure resolver tests, not hardware/callback integration. Minimum BioZ diagnostic evidence should pair raw event, callback-entry and resolver-capture elapsed clocks with epoch offset/wall clock and per-event classification; no schema implementation or new device capture is claimed.
- Export cleanup implementation now reuses the existing bounded atomic writer and includes the exact legacy summary temporary file in local deletion. Independent review still requires narrower hidden-temp matching and crash/restart cleanup evidence; fallback move failure and failed-delete retry/retention are not proven. Mobile build/test work remains active, so this patch is not yet a verified release artifact.
- Cleanup patch software verification now passed: focused 14 tests, Mobile Debug and Release 563 tests each, zero failures/errors; Release lint/assembly successful. Narrow hidden-temp matching preserves same-prefix non-temp files. Up to 64 expired partials are checked on the next suite write, with failed deletions reported. No startup/readonly hook exists, so idle-time retention and actual device deletion remain unverified. No new APK was installed.
- Main-screen cleanup hook is now present alongside Application and Engineering Debug scheduling, using the existing bounded executor path. Mobile Debug 565/565 passed; Mobile Release assembly/lint passed; unsigned artifact SHA-256 is `62726E97225761E50113F8ED23A9175688CA0A723DC21376CE21EE1EC0CDD62E`. Device install, runtime cleanup and paired scan acceptance remain unverified.
