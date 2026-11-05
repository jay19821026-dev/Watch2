# AILISA Full-Coverage Signal Composer

Updated: 2026-10-03. New direction supersedes gap-rejection-only presentation, but not provenance or physiological validity requirements.

## Initial core delivered

The opt-in index-domain composer preserves finite measured raw values and creates a separate complete finite analysis list. Raw null, NaN and infinity remain unchanged. Explicit channel mapping and ordering attestations are required from the caller; current hardware is not asserted to satisfy them.

- DataOrigin now includes FILTERED and SYNTHETIC_ESTIMATE; existing enum names are retained for compatibility.
- GapDetector detects explicit missing indices only. It does not infer sample loss from rejected callbacks or manufacture a grid.
- Interior gaps of at most two indices use linear statistical imputation; larger/boundary gaps use the finite session median, marked IMPUTED. This is baseline imputation, not physiological waveform recovery or an ML generator.
- Every output point includes provenance, method, confidence and measured-neighbor distances in **indices**, not milliseconds. No physical time is inferred. Timestamp source stays UNKNOWN.
- Confidence caps 0.25/0.05 are conservative engineering policy values, not calibrated probabilities.
- Empty/all-missing/unmapped/unordered inputs return Unavailable, not a purported complete waveform. Complete results contain no missing values.
- Waveform viewing is LIMITED; trend/rate/HRV/physical conversion are FAIL until independent validation. Completeness cannot upgrade validity.
- No model is loaded by this composer. It never labels median imputation as SYNTHETIC_ESTIMATE.

## Remaining work

### PPG Data Layer archive integration

WearDataProcessor now dispatches final accepted PPG preview storage to Room on IO, before calibration transforms. Non-finite preview encodings remain preserved. Event/raw-value/preview counts are separate metadata; original event timestamps and per-event sample counts are retained as metadata, never expanded into an invented time grid. Rows have unknown event/channel mapping (-1) and null acquisition/callback timestamps because the preview cannot be reliably aligned per point.

This archives received previews, not the complete raw ADC stream or all event-row payloads. Snapshot preference/ACK handling remains independent; archive capacity/cancellation/errors can leave a preview unarchived and report archive_failed. Persistent retry/idempotent archive recovery remains open. It does not yet invoke FullCoverageSignalComposer on unmapped data or provide a full-coverage UI.

Validation batch: .tooling/verify/ppg-archive-validation.log. Real phone test adds NaN preservation, preview-only row count, unknown clock and unknown channel assertions. These synthetic test fixtures are not a new watch scan.

### Room evidence store patch

Added a separate Room v1 database: session and raw sample tables plus versioned/provenanced generic derived result records. Raw missing/non-finite encoding is stored as text, with event/channel indices and distinct source/callback timestamps. Raw/session inserts use ABORT, no update/delete DAO is exposed; derived-only deletion and transactional replacement cannot cascade into raw. Store admission stops at 200 sessions or 1,000,000 raw points; no automatic raw purge is performed. This is admission bounding, not a finished retention/export lifecycle.

Debug app and instrumentation test APK compilation succeeded. A real phone instrumentation test has been dispatched; its terminal result is in .tooling/verify/room-device-test.log. It covers raw preservation after derived deletion, duplicate raw insertion refusal and model-version storage. The database is not yet connected to the production PPG path. Dedicated quality/model/explanation schemas, encrypted-at-rest application storage and future migrations remain pending; this new database does not migrate or overwrite existing preferences.

Terminal test result: 1/1 instrumentation test passed on the connected Android 16 phone; Gradle connectedDebugAndroidTest succeeded. The test uses its own in-memory database and synthetic test fixtures, not personal health data. The task installed Debug/test APKs on the phone; no Wear update or sensor scan was performed. Room 2.7.2/KAPT integration was checked against https://developer.android.com/jetpack/androidx/releases/room .

### Numerical primitives implemented

LinearInterpolator now backs index-domain composer interpolation. SplineInterpolator implements cubic Hermite interpolation with explicitly supplied slopes and interval; it is not an automatic smoothing spline fit. KalmanReconstructor implements a scalar random-walk state model with caller-supplied positive process/observation/initial variance and bounded prediction across missing indices. Leading gaps remain unsupported, long gaps stop prediction and reset at the next finite observation. Observed filtered values are FILTERED, missing predictions IMPUTED, never MEASURED. Neither primitive infers clocks, physiological units or calibrated confidence.

Numerical tests cover endpoints, a linear spline reference, unchanged raw missing values, prediction variance growth, long-gap reset and invalid input rejection. Shared-module total: 25 passing tests. Kalman/Spline are not yet wired to policy-driven PPG ingestion, Room or UI. Model tuning and device validation remain pending.

### Additional core patch

PpgCompositionQualityEngine now assesses finite/missing ratio, amplitude range, exact-value flatline ratio, explicit ADC-boundary clipping and supplied motion score. Unknown ADC limits/motion remain unknown. Its score is an engineering heuristic, not calibrated signal quality. Spectral metrics and motion-stream synchronization are not implemented.

ReconstructionPolicyEngine selects linear, available Kalman, domain-validated versioned learned model, or statistical baseline. It does not itself execute Kalman or learned inference. Rate/timestamp uncertainty selects NORMALIZED_INDEX; no callback clock promotion occurs. Selected confidence is a policy cap, not probability. Initial composer is not yet wired to this policy or the hardware path.

Shared tests now total 17, all passing. The preceding paired-device unit/debug/release build batch passed. The additional policy batch is separately recorded in .tooling/verify/full-coverage-policy-build.log. No runtime model, database, UI or device acceptance is claimed by these results.

1. Explicit verified/estimated/index-domain axis evidence, quality-based ReconstructionPolicyEngine and validated Kalman configuration. Index-domain interpolation must remain IMPUTED, not time-based RECONSTRUCTED.
2. Versioned trained numerical generator interface and benchmark. Long-gap SYNTHETIC_ESTIMATE requires an actual model, context and validation; Gemma is explanation-only.
3. Room immutable raw and separately regenerable derived tables, retention and deletion tests.
4. Connect single-channel PPG ingestion only after mapping/order evidence; never flatten multi-channel HAL events into a presumed temporal waveform.
5. UI opt-in, measured-only/derived overlay, per-purpose gates and origin ratios. Do not export imputed points as measured Health Connect records.
6. Hardware acceptance and independent review. A successful shared-module test/build is not proof of PPG acquisition or waveform recovery.

## Verification

### Local reviewer integration, 2026-10-03

Successfully submitted a bounded sanitized question to hermes3:8b via local Ollama and received a review in D:\AI\AI\04 代理交換\responses\20261003-xhb-hermes-full-coverage-review.md. This is a local model response, not proof that the Hermes desktop agent is orchestrating work. Reviewer recommends raw immutability, derived-only deletion and unsupported-claim tests. Raw immutability has unit coverage; derived database deletion remains unimplemented/unverified. Reviewer text is general and does not certify accuracy.

Fixed request-ai-review.ps1 to select an explicit Ollama/OpenAICompatible request contract, avoid missing-property access under StrictMode, limit generated tokens and reject empty/oversized answers. Helper accepts only loopback endpoints. Hermes/Ollama real invocation succeeded; OpenAICompatible/Grokbot route remains unverified and is not reported operational.

Both devices were visible in ADB inventory. No new scan, installation or acceptance run occurred. The previous Grok submitted review tab handle is missing; no response was recovered, so no Grok consensus is claimed.

Shared-module tests passed including five new FullCoverageSignal tests. Paired mobile/Wear unit tests and debug/release build results are tracked in .tooling/verify/full-coverage-build.log; only a completed successful process verifies that batch. No install or publish is performed by this patch.
