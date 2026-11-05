# Xiaomi Watch 2 Pro paired-measurement protocol — draft

**Status:** protocol design only; no participants recruited and no physiological data collected. This is not a clinical investigation protocol, regulatory submission, calibration procedure, or medical-performance claim. Do not start collection until the gates below are reviewed and approved for the intended jurisdiction and use.

## Purpose and limits

Plan a consent-based, controlled comparison of each app-reported quantity against an appropriate independent reference. Treat each output separately: success on heart rate cannot validate SpO₂, ECG, BioZ, sleep staging, or a composite score. Public datasets from other watches/sensors are useful for software tests but cannot establish Xiaomi-device performance.

The evaluation should distinguish the evidence layers described in [IMDRF SaMD Clinical Evaluation](https://www.imdrf.org/sites/default/files/docs/imdrf/final/technical/imdrf-tech-170921-samd-n41-clinical-evaluation_1.pdf): scientific/clinical association, analytical/technical validation, and clinical validation. For pulse oximetry, the FDA's January 2025 recommendations are explicitly **draft and non-binding**; consult the current applicable standards and regulatory advice rather than treating this plan as a substitute ([FDA draft guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/pulse-oximeters-medical-purposes-non-clinical-and-clinical-performance-testing-labeling-and)).

## Gate 0 — channel feasibility (before recruitment)

Create a signed-off per-channel evidence sheet from fresh device captures. Record app/build version, watch model/OS, sensor/API source, permission state, sample count, monotonic and wall-clock time basis, sampling interval/jitter, units, payload schema, quality flags, and lifecycle/scan conditions. Preserve raw data without converting unknown units.

| Output | May enter paired comparison only if… | Reference / current stop condition |
| --- | --- | --- |
| Heart rate from watch API | the app receives timestamped numeric HR with documented bpm semantics and traceable source | Simultaneous ECG reference; if only raw PPG is present, verify its sample clock and freeze a separately specified estimator before comparison |
| SpO₂ | watch emits a device-reported percentage with documented unit/algorithm provenance and quality state | Appropriate traceable reference under the applicable protocol; current uncalibrated raw optical payload is **not** SpO₂ and must not be compared as a percentage |
| ECG | raw waveform has validated channel/lead semantics, sample timing, scale/gain, saturation behavior, and reproducible export | Calibrated ECG reference with documented electrode placement; if mV conversion/lead are unknown, analysis is limited to raw transport integrity and stops before physiological interpretation |
| BioZ | acquisition channel, excitation/measurement semantics, sampling/timebase, and physical units or explicitly raw ADC counts are documented | Calibrated impedance instrument/electrode setup; without traceable Ω scale, assess only within-device raw repeatability, not body composition or clinical quantities |
| Sleep stages / derived scores | input provenance, scoring algorithm/version, epoch duration, and ground-truth target are predeclared | Concurrent PSG with qualified scoring process if making stage-performance claims; actigraphy/HR alone is not PSG truth |

No channel advances by substituting a chip datasheet or evaluation-board values for watch-specific configuration. Missing/unverified channels remain “not evaluable”; do not impute them.

## Gate 1 — governance and safety

1. Write the intended research question, intended-use boundary, jurisdiction, analysis plan, stopping rules, and data-flow diagram before recruitment.
2. Obtain independent ethics/IRB determination and any required regulatory authorization before collecting research data. Obtain informed consent specifying signals, purpose, risks, retention/deletion, access, sharing, withdrawal, and whether data can be used for future research. Consent alone does not waive other requirements.
3. Use a study ID unrelated to name/account/device serial; keep the re-identification key separately under the approved custodian. Minimize demographics to justified variables. Never upload raw health data to general-purpose AI or embed dataset/participant waveforms in the APK.
4. Encrypt approved storage and transfer, restrict role-based access, audit access, set retention/deletion dates, and define incident response. Keep raw source, derived features, reference data, and analysis outputs linked by study ID and immutable acquisition/session IDs.
5. Define an adverse-event/participant stop process and do not alter watch firmware or bypass device protections during study acquisition.

## Gate 2 — paired acquisition procedure

- Freeze the app and algorithm build hash before each study phase. Record watch firmware/OS, phone OS, sensor access path, permissions, watch fit/placement, charging state, environment, activity, and session start/stop. Firmware/app changes create a new versioned analysis cohort.
- Use simultaneous acquisition. Align watch/reference clocks with a documented synchronization event at start and end; retain original timestamps and estimate offset/drift without rewriting raw timestamps. Predeclare the maximum acceptable alignment error per modality based on the reference sampling rate and endpoint.
- Capture a prespecified set of rest and movement conditions only when both the watch channel and reference are technically valid. Record condition transitions and artifact/interruptions; do not selectively remove difficult segments after seeing errors.
- Validate each reference device's calibration/service status and its effective-from/valid-through interval covering the entire session, configuration, operator, sampling rate, units, and file integrity. Maintain chain-of-custody and checksums. Record failed sessions and reasons.
- Do a small feasibility pilot first to estimate missingness, usable-data rate, synchronization error, and within-person variability. The pilot is not the confirmatory evaluation and must not be counted as a final test set if it informs algorithm changes.

## Gate 3 — analysis and acceptance plan

Pre-register endpoints, exclusions, windows, quality rules, subgroup variables, statistical methods, and acceptance criteria before opening the held-out set. Keep participants—not randomly selected windows—separated between development and held-out evaluation if any tuning occurs.

For continuous, reference-comparable outputs, report paired bias, 95% limits of agreement, MAE/RMSE, confidence intervals, usable-output coverage, missingness, and error by predeclared activity/device/population strata. Plot paired differences against means and inspect heteroscedasticity; correlation alone is not agreement. For event/classification outputs, report confusion matrix, sensitivity, specificity, predictive values with confidence intervals, prevalence, and indeterminate/unreadable rate. Report all enrolled and failed sessions in a flow table. Define acceptance thresholds from intended use and applicable standards with qualified clinical/statistical/regulatory reviewers; **do not invent numeric thresholds in this draft**.

No model fitting or threshold tuning on the held-out data. Any algorithm, sensor mapping, preprocessing, or quality-gate change resets the version and requires a new locked evaluation. An unsuccessful or indeterminate result is retained in coverage accounting, never silently dropped or fabricated.

## Current decision / next actions

- No paired collection is authorized by this document. The project must first complete Gate 0 evidence sheets for each intended channel, then obtain governance and jurisdiction-specific approval.
- Initial feasible scope should be limited to outputs already emitted by the watch with traceable units and timestamps (likely API heart rate, if fresh device evidence confirms it). Raw PPG/SpO₂/ECG/BioZ remain research-only until their channel semantics and reference protocol are verified.
- Before recruitment: name the qualified protocol owner, obtain ethics/regulatory/privacy review, select and calibrate reference equipment, predeclare acceptance criteria and sample-size rationale, and test secure capture/deletion end to end.
- A metadata-only session contract (schema v2) and preflight validator are available in `research/clinical-data/paired_session.schema.json` and `validate_paired_session.py`. The validator rejects missing approval/consent, direct-identity fields, undocumented mappings for comparisons, calibration intervals that do not cover the full session, excessive synchronization error, or unencrypted artifacts. A pass is only a metadata consistency check; it is not approval to enroll or collect. Do not store waveforms or direct identifiers in the metadata record.
- An agent review was attempted for this draft, but the agent concurrency limit prevented dispatch. No external AI was sent participant data or source code.
