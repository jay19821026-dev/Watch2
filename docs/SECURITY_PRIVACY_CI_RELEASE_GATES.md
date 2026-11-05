# Engineering Sample Security, Privacy, and Release Gates

> Scope: local engineering/sample builds of the Xiaomi Watch Health Bridge. This document defines required controls; unchecked items are not implemented or verified merely by being listed here.

## Current-state audit (2026-09-27)

- Mobile raw-data export is user-initiated from `HealthCenterActivity.exportRawSensorData()` and uses Android's share chooser. Its JSON includes sensor timestamps, statuses, latest values, and unpacked waveform samples. The chooser hands data to the app selected by the user; it is not a privacy boundary after handoff.
- The export currently lacks an explicit per-export consent/preview and a documented redaction selection. Add these before treating external sharing as safe for routine use.
- No `.github` CI workflow exists in this checkout. Mobile and Wear release build configuration currently signs with the debug signing config; the resulting APKs are engineering artifacts, not store/release-signed deliverables.
- Workspace contains mobile and Wear debug/release APK outputs, but no ADB devices were connected during this audit. Installed package/version state is therefore **unknown**, not assumed to match these files.
- Manifests set `allowBackup=false`. FileProvider paths are constrained to export cache/external-files on mobile and transfer files on Wear; review any future path broadening as a security-sensitive change.
- No evidence of automatic Internet upload was found in the inspected export path. This is not a whole-program network audit; CI must enforce a deny-by-default outbound-data policy.

## Data classification

| Class | Examples | Default handling |
|---|---|---|
| C0 public | App version, public sensor model documentation | May be shared; no user/device identifiers |
| C1 operational | Build hash, generic error category, test result | Local by default; strip serials, account names, exact paths and timestamps before sharing |
| C2 personal | Age, sex, height/weight, profile/name, coarse activity history | On-device only; export only when individually selected and consented |
| C3 sensitive health | HR/HRV, SpO₂, sleep, activity, derived health interpretations | On-device only by default; explicit one-time consent, purpose and recipient review for export |
| C4 raw biomedical | PPG/ECG/BioZ waveforms, sensor payloads, detailed event timestamps | Highest sensitivity; separate explicit opt-in, default redacted, bounded export, no logs/analytics/network transmission |
| C5 secrets/security | API keys, tokens, signing keys, pairing credentials | Never log/export/commit; managed secret store only |

## Required engineering-mode controls

1. Engineering mode must be an explicit build flavor (`engineering`), not a hidden production switch. Keep raw capture, diagnostics and export entry points absent from ordinary release builds unless a deliberate product requirement approves them.
2. Require a fresh in-app confirmation before every C3/C4 export. Show included data classes, time range, destination via chooser, and a cancel action. Consent is per export, not a persistent blanket grant; revocation/clear-data must delete app-owned temporary exports.
3. Default export to redacted: remove profile identifiers, device IDs/serials, precise location, absolute timestamps (retain relative offsets when useful), free-text logs and account identifiers. Waveform inclusion must be a separate checkbox with an explanation that raw signals can be identifying/sensitive.
4. Keep exports in app-private cache, short-lived, with restrictive FileProvider grants. Avoid external shared storage, clipboard, automatic email/cloud/AI uploads, analytics, and background transmission. Delete temporary files after share completion or a short TTL; make cleanup idempotent.
5. Network policy: no health payload egress. Add a CI source/manifest scan and instrumented test that fails if an unapproved network client or endpoint is introduced. Any explicitly authorized research upload requires a separate threat/privacy review, TLS, destination allowlist, payload minimization, audit record and opt-in—not a general-purpose uploader.
6. Error logging may include bounded event IDs, channel names, categorical stop/reject reasons and app version only. Never log raw values/waveforms, exact location, person/device identifiers, tokens, or full exception payloads that may contain them.

## Full-pipeline regression fixtures

Maintain synthetic, non-personal fixtures for the complete path: acquisition result → payload schema/units/provenance → quality/calibration state → watch snapshot → Data Layer receive/rejection → phone persistence → freshness classifier → UI/export representation.

Required fixture cases: valid fresh timestamp; absent, future, stale and reordered timestamps; duplicate delivery; payload limit rejection (whole payload, never prefix); unknown channel/unit; raw-but-uncalibrated PPG/SpO₂/ECG/BioZ; explicit quality rejection; wear/contact unknown/false/true; charging skip; no events/permission denied; partial and complete scan; legacy schema missing optional keys; temporary export cancellation and cleanup; redacted versus explicitly raw export; and proof no network request is emitted.

Expected invariant: raw acquisition, quality pass, calibration, physiological value, clinical interpretation and UI visibility are separate fields/states. A test must fail if raw/research-only data is counted or rendered as calibrated physiology.

## CI and artifact provenance gates

- CI runs mobile + Wear unit tests, lint, manifest/security checks, fixture contract tests, and both release APK builds from the same commit.
- Produce a machine-readable manifest with commit SHA, versionName/versionCode, variant, package ID, APK SHA-256, signing certificate SHA-256, build timestamp, test/lint results, and artifact paths. Never include secrets.
- Verify APK package metadata and signature after build; fail if engineering artifacts are presented as store-ready or if release uses a debug key. Signing secrets must come from protected CI secrets and are not required for local engineering builds.
- Upload build artifacts only to the approved private CI store with retention limits. Do not automatically publish or distribute. Artifact upload is not device installation.
- Installation acceptance is a separate evidence record: ADB device alias (redacted), installed package/versionCode, install time, APK hash/signing identity match, launch result, and paired phone/watch status. If no device is connected, report `NOT VERIFIED`; never infer installed state from workspace files.
- Paired-device acceptance requires a user-visible scan, matching session ID/start time, fresh per-channel timestamps, terminal status, phone receive/persistence, UI state and bounded logs. Build/test success alone cannot pass measurement acceptance.

## Release gates and stop-ship risks

| Gate | Pass evidence | Stop-ship condition |
|---|---|---|
| Privacy boundary | Export preview/one-shot consent; redaction tests; temporary-file cleanup; no-egress test | Silent/background upload, default raw waveform export, or unreviewed sharing |
| Data integrity | Full synthetic pipeline matrix; schema/backward-compatibility tests; raw/calibrated states distinct | Future/stale data shown fresh; unknown units converted; partial payload accepted as complete |
| Security | Least-privilege manifest, private FileProvider scope, no secrets in source/log/artifacts | Broad file URI access, committed credentials, debug signing used as a public release key |
| CI provenance | Tests/lint/build green and APK manifest/hash/signature verified | APK cannot be tied to commit/version or signer; artifact state ambiguous |
| Device install | Installed package/version and signer/hash verified on each target | No connected target or install/version mismatch (mark not verified) |
| Sensor acceptance | Fresh paired UI scan and independent channel-level evidence | Old export, no scan, missing timestamp, or calibration/clinical evidence absent |
| Clinical claims | Device-specific calibration and reference-protocol validation reviewed | AI/literature/chip datasheet substituted for device calibration or diagnostic validation |

## Current risks / next implementation sequence

1. High: raw waveform export has no shown per-export consent/redaction gate. Implement an engineering-only export preview and redaction pipeline, then test cancellation and cleanup.
2. High: all workspace APK outputs are not tied to a recorded installed state; ADB list was empty. Add provenance manifest and installation checklist before accepting field results.
3. High: no CI workflow exists in this checkout; add one for the full mobile/Wear test-lint-build matrix and private artifact manifests.
4. Medium: release build types use debug signing. Keep these as local engineering samples; configure protected release signing only if a distributable release is explicitly needed.
5. Medium: no-every-egress claim is not yet backed by a repository-wide network inventory/test. Audit clients, dependencies, manifest permissions and runtime traffic; do not assert “no upload” until that gate passes.
6. Clinical validity remains blocked independently of software quality by unknown OEM mappings, timebases and device calibration for affected channels.
