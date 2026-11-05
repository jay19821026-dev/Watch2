# Flags runtime acceptance — 2026-10-06

- Phone and watch packages confirmed installed, version 3.13.37 / 147.
- Temperature preview selection and evidence UI fixes passed targeted TemperaturePresentationTest and ClinicalVerdictTest; both Debug builds succeeded. Phone replacement install returned Success.
- Phone app-private database directory contains ailisa-signal-evidence-encrypted.db and WAL files.
- Phone archive status reports stored_preview for ECG, thermometer and body_temperature_raw. This proves archive completion reports, not calibrated physiological validity or full database-content verification.
- New PPG session started through WearMeasurePageActivity. Device diagnostics report registration=registered, event_count=0, timestamp_source=pending and stop_reason=running at inspection. Do not substitute older samples for this session or label zero events successful acquisition.
- Watch UI automation returned null root. No visible-result acceptance inferred from that failure.
- Next acceptance gate: inspect terminal PPG session state, correlate fresh callback evidence and phone archive status, then verify stored flags and per-channel display. ECG/BioZ timing and calibration remain separate gates; vascular age is not established by raw-preview quality.

## Follow-up

- PPG terminal diagnostics now report 8 events, stop_reason=time_limit_reached and sensor_event_timestamp_missing. Phone ppg_status=stored_preview. Earlier zero-event inspection was an intermediate state, not the final result.
- Latest ECG phone state: 573 total events; bounded recent subset of 263 events research-decoded to 2630 values. Summary was incorrectly calling the entire capture undecoded. HealthConclusionEngine now distinguishes those counts without promoting decoding to diagnosis; regression test passed and phone installation succeeded.
- Wear flags now distinguish unknown finite-value count from missing values and separately report finite raw payload values. Neither flag asserts physiological decoding. Targeted test and Wear Debug build passed.

## BioZ and temperature follow-up

- BioZ previously omitted event rows/callback arrays in publisher and phone preference storage. Both endpoints now retain bounded rows and timing evidence, with unchanged unknown channel mapping and physical calibration.
- New paired-device capture confirmed 8 BioZ events and 643 characters of saved event rows, status complete_for_observed_events. This session's source time is unverified, unlike the preceding valid-time session; neither is promoted by publication time.
- Temperature detail now lists every candidate channel separately, preventing a zero-valued thermometer preview from hiding another raw channel. Units remain unverified.
- Blood pressure analysis currently reads manual cuff storage while MainActivity separately reads Health Connect records. An engineer is integrating that missing link with independent source/time provenance; no wrist blood-pressure calibration claim.
- PPG review found insufficient transport provenance for selected channels and removed fill gaps. Bounded original event rows are now included in the same evidence preservation path so a selected preview does not erase raw metadata/gaps.

## Independent review decisions

- BloodPressureEvidencePolicy now selects the newest valid manual or Health Connect reading, retaining distinct source/time caches. Health Connect method is unavailable in this API and remains research-only; do not label imported records as verified cuff measurements. Four policy regression tests cover validity and selection.
- Vascular research reviewed the 2025 wrist ECG/PPG baPWV paper and 2026 ring vascular-age paper plus author artifacts. Neither inspected release provides a deployable validated Xiaomi wrist model. Published ring/finger accuracy does not establish wrist accuracy. Required gates remain simultaneous channel mapping, trustworthy clock, published model/rights and wrist reference validation.
- Local reviews: D:\AI\AI\04 代理交換\responses\20261006-bp-cache-link.md; 20261006-ppg-contamination-review.md; 20261006-vascular-new-evidence.md. External literature proposals do not override measured evidence.

## Measurement exit failure

- Fresh PPG acquisition reported events on watch, but phone retained an older final snapshot. Inspection found WearMeasurePageActivity.onStop publishing on exit only when whsBpm was present, discarding raw-only captures on exit; onDestroy also stopped without publication.
- Finishing/destroying the measurement activity now publishes partial raw evidence with explicit lifecycle stop reasons, independently of Health Services BPM. This does not declare incomplete acquisition clinically valid. Wear unit tests and Debug/Release builds passed.
- Targeted phone tests and Debug/Release builds passed after the blood-pressure integration; phone install returned Success. Scope does not include all mobile regression tests or clinical validation.

## ECG saved evidence chart

- Phone UI inspection confirmed an archived ECG session with raw_data_available=true but zero scalar preview rows. Event rows had not been routed into the evidence-page graph.
- Evidence page now decodes bounded saved ECG event rows off the main thread and stores a separately versioned derived result, leaving raw session evidence untouched. It displays decoded subset event/point counts and an index-domain research graph; no RR, AF, mV or clinical diagnosis is inferred.
- Targeted ECG decoder/conclusion tests and phone Debug build passed. New chart UI still requires direct visible acceptance after installation.

## Clinical lab integration

- Clinical ECG card previously forced an empty chart for unverified payload encoding, even when separately stored research decoding succeeded. It now displays the research-decoded subset with index/raw-unit labels; physiological interpretation gates are unchanged.
- Clinical PPG/BioZ cards now expose structured per-field values from retained packet rows, rather than only a mixed preview. Field indices may include metadata and are not asserted to be physiological channels.
- RawPacketChannelDecoder rejects inconsistent packet widths without truncating and preserves zeros; tests cover column separation and rejection. Targeted decoder/verdict tests and phone Debug build passed; installation returned Success.
