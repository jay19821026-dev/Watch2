# AI Health Analysis Rewrite — 2026-10-08

## Review outcome

Three independent local, read-only agents reviewed the current analyzer and sensor-to-UI paths. No raw health exports, waveforms, account/device identifiers, or source files were sent to external services. No external AI browser tabs were open during this review.

The current “AI” page is a local deterministic rules-and-reference summary, not a generative diagnostic model. It combines a small recovery/readiness rule score with a separate `HealthConclusionEngine` report. Those are not a single validated disease classifier. The fixed report contains heart rate, SpO₂, ECG, HRV, temperature, pressure, BioZ, PPG, sleep, cuff blood pressure, and a glucose availability slot; steps/calories/activity remain separate observations.

The reviewers agreed the rewrite should separate measured-reference classifications from research-only signal observations and recovery estimates. The main UX should show a concise result and coverage first; source, clock, calibration, and parser details belong in expandable evidence. None of the reviewers considered sample counts or model consensus proof of clinical normality.

## Immediate fixes in this increment

- The recovery composite now receives recorded effective sleep only; a session-window duration is not silently used as measured sleep for recovery scoring.
- Public `ClinicalVerdict.Result.score` is nullable. Zero/unavailable quality values render as “未評分”; unknown temperature and unscored sensor-suite results no longer expose `0/100`. Raw event/sample counts remain unchanged.
- `ClinicalMetricCalibration.Result.score` now preserves missing input as null instead of reintroducing `0`; score consumers require both a trusted result and an available score.
- The research evidence page now includes raw MAX/OEM SpO₂ channels and shows finite-value ratio, raw mean, and standard deviation in the uncalibrated encoding domain. It does not convert these values to SpO₂ percent or call them calibrated signal quality.
- SpO₂ acquisition preserves an explicitly selected `spo2_raw` versus `mi_spo2` source. Without a selected source it chooses the newest available channel. Unknown sample rate is not coerced to 1 Hz; the displayed index is a raw-evidence-completeness indicator, not waveform quality or oxygen accuracy.
- The analysis heading now identifies its method as local rules/statistics rather than implying a learned diagnostic model.
- Existing separate measurement classifications remain distinct from the recovery score; research-only records do not increment normal/watch/attention counts.

## Literature and evidence boundaries

- ECG quality assessment literature reviews signal-quality indices and artifact detection, but warns that methods and real-world validation are heterogeneous: [El-Baz et al., Automatic ECG Quality Assessment Techniques: A Systematic Review](https://pmc.ncbi.nlm.nih.gov/articles/PMC9689601/).
- Wearable PPG literature supports separating signal-quality assessment from physiological estimation and accounting for motion/contact artifacts: [Bent et al., Wearable Photoplethysmography for Cardiovascular Monitoring](https://pmc.ncbi.nlm.nih.gov/articles/PMC7612541/); [Ismail et al., Heart rate tracking in PPG signals affected by motion artifacts](https://link.springer.com/article/10.1186/s13634-020-00714-2).
- FDA diagnostic ECG guidance is a device/intended-use guidance, not a source of device-specific calibration coefficients for this watch: [FDA Diagnostic ECG Guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/diagnostic-ecg-guidance-including-non-alarming-st-segment-measurement-guidance-industry).
- MAX86178 silicon specifications and evaluation-kit features do not establish the Watch 2 Pro electrode map, firmware channel encoding, gain, ADC-to-mV conversion, or BioZ electrode model.

## Required architecture for the full rewrite

1. Use one per-session research-result contract: raw receipt, decoded fields, analysis-domain values, timestamp/timebase state, units, quality/evidence completeness, confidence semantics, algorithm/model version, and calibration provenance.
2. Keep each modality's result independently available: ECG, PPG, BioZ, raw SpO₂, temperature, and HR. A missing composite input must not suppress an available per-sensor result.
3. Distinguish `MEASURED`, `FILTERED`, `RECONSTRUCTED`, `IMPUTED`, `MODEL_ESTIMATE`, and `UNKNOWN`. A full or relative sequence must never overwrite raw evidence.
4. For unknown timebase, show index-domain waveform and relative statistics only. Do not derive bpm/RR duration, Hz, degrees C, SpO₂ percent, ECG mV, or BioZ Ω without the corresponding evidence/profile.
5. Use metric-specific states (`current`, `historical`, `research-only`, `unavailable`) consistently across cards, summary counts, freshness gates, and exports.
6. Make the summary disclose that the current implementation is deterministic rules, name the actual input domains, and avoid describing it as a clinical AI diagnosis.
7. Connect the shared research engine to production raw-session storage and the phone UI. Existing algorithm registries, Room tables, or unit tests alone do not prove production wiring.
8. Validate each result against synchronized reference equipment and held-out sessions before enabling a device-specific physiological conversion. A successful linear fit is insufficient without paired acquisition quality, independent holdout data, bias/error limits, and profile integrity.

## Rewrite foundation started

- `HealthConclusionEngine.Item.score` is nullable; unscored, stale, uncalibrated and research-only findings no longer encode “unscored” as numeric zero. Raw event and sample counts are unchanged.
- Each item exposes a typed user-facing state (`NORMAL`, `ATTENTION`, `RESEARCH`, `UNAVAILABLE`), and health cards use that state. A research-looking value such as an uncalibrated bpm remains labelled research instead of being promoted to a reference value.
- Quality/evidence completeness remains in explanatory evidence text and is not reused as a physiological health score.
- Three local review agents independently examined architecture, clinical-evidence boundaries and UX. Their findings are recorded above; no raw sensor data was disclosed externally.
- This is an initial contract cleanup, not a complete analysis rewrite. The unified per-session provenance/time/unit/quality contract and end-to-end sensor wiring remain outstanding.
- The focused health-conclusion/analyzer/presentation regression suite passed after this change; mobile Debug and Release APKs both built successfully.
- A phone reinstall was attempted under the standing install request, but ADB lost the phone before package transfer. The Watch 2 Pro remained connected and its app was not changed; neither fresh sensor acceptance nor post-install UI acceptance is claimed.

## Remaining acceptance work

- Wire and verify per-session relative analysis for BioZ and raw SpO₂, and a meaningful temperature repeatability/baseline view that preserves its unverified unit.
- Make PPG/ECG research output consume retained complete raw evidence where available rather than silently treating a capped preview as a full recording.
- Add producer-to-phone-storage-to-analysis-to-render tests for each modality, with event count, raw sample count, and preview count kept distinct.
- Add explicit time/freshness status and source status to every research result.
- Conduct full code review and mobile/Wear unit/release builds; then verify on paired hardware with fresh measurements. A compiled APK is not hardware acceptance.

## Verification in this increment

- Targeted analyzer, score-availability, clinical-verdict, composite, and research-summary tests: passed.
- `:health-engine:test`, `:mobile:testDebugUnitTest`, `:wear:testDebugUnitTest`, `:mobile:assembleRelease`, `:wear:assembleRelease`, `:mobile:assembleDebug`, and `:wear:assembleDebug`: passed after this patch set.
- Targeted tests for score availability, verdict aggregation, recovery composite, analyzer, index-domain signal summary, calibration nullability, and SpO₂ source selection: passed.
- Debug APK version `3.13.37` / code `147` installed successfully on the connected phone and Xiaomi Watch 2 Pro. Bounded recent logs showed no fatal exception/ANR after installation. This confirms package installation only, not live sensor or UI acceptance.
- Paired-device fresh sensor end-to-end acceptance remains pending; historical phone process exit info includes a pre-install normal memory-pressure kill, which is not evidence of a new crash.
- Repository Git metadata was absent at the supplied workspace path, so no Git diff/status verification was possible.
