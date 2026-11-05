# AILISA milestone 1 — evidence-preserving reconstruction

Updated: 2026-10-03. Status: initial core patch, not milestone completion.

## Scope and invariants

PPG first. Preserve immutable raw evidence separately from derived data. Callback receipt time is not acquisition time. Unknown clocks, channel mapping, calibration and motion evidence remain unknown. Do not derive SpO2 percent, ECG mV or BioZ ohms. Explanation models cannot create physiological measurements.

## Delivered and verified

- Shared Kotlin DataOrigin, SensorProvenance and clock classification.
- Explicitly constrained linear short-gap interpolation; separate derived points with provenance. No extrapolation; reject unknown rate/timeline/motion, low quality and long gaps.
- Engineering defaults: at most two missing samples and 0.1 seconds; quality threshold 80/100, motion threshold 0.2. These are unvalidated research policy settings, not clinical thresholds. Confidence is a conservative policy cap, not a calibrated probability.
- Classifier only declares eligibility for an estimated timeline. It does not generate timestamps or validate an OEM sample clock.
- health-engine tests: 7 passed, including 5 new regression tests.

## Model artifact

Downloaded D:\AI\models\gemma-4-E2B-it\gemma-4-E2B-it.litertlm.

Repository: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm

Artifact size: 2588147712 bytes.

SHA-256: 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c

Local SHA-256 and size match repository LFS metadata. Runtime inference has not been tested. Model is not bundled in an APK or copied to either device.

## Remaining milestones, in order

1. Quality engine: finite/missing/clipping/flatline/amplitude/derivative/outlier and motion evidence; clock-dependent metrics stay unknown without clock evidence. Independent waveform/trend/rate/HRV/conversion gates.
2. Validate per-channel ingestion and indices before invoking reconstruction; current core is not yet wired into capture or UI. Explicit channel mapping and a uniform sample grid must be established by the caller. Never treat duplicate-event rejection as a missing sample.
3. Room evidence/derived schemas and append-only DAO; bounded retention; derived-only deletion and reprocessing. Preserve existing storage behavior.
4. LiteRT-LM integration, device capability benchmark, bounded inference and deterministic fallback. AI output validator before rendering any model explanation.
5. Purpose-specific UI source labels and provenance; no standalone success boolean.
6. Both-device tests, debug/release builds, then separately authorized real-device acceptance. No release/install claimed by this patch.

## Reviewer availability

Browser inventory showed Grok, a separate-project ChatGPT conversation and local scheduler tabs. Cursor, Gemini and Claude tabs were not present in the connected inventory. No external reviewer decision has been received for this patch. Do not equate open tabs or downloaded models with completed team review.
