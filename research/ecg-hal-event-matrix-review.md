# ECG HAL Event-Matrix Review

Date: 2026-10-10

## Decision

The observed 16-value `SensorEvent.values` row can be shown as a bounded, per-field min–max preview against event index, provided the UI calls it a **raw-field trend/inspection view**, not an ECG waveform. Per-field normalization is dimensionless and independently rescales each field; it therefore cannot establish which field contains ECG samples, preserve cross-field amplitude ratios, create a sampling clock, or convert values to mV. Event index is not elapsed time.

The current implementation keeps `mappingVerified=false`, retains the event rows separately, limits each trace to 512 preview points, stores the derived view with `NORMALIZED_EVENT_INDEX` and `calibration_status=UNVERIFIED`, and does not emit R peaks, RR, bpm, HRV, or diagnosis. This is a display/inspection path, not a successful ECG decoder.

## Evidence and limits

- The [MAX86178 data sheet](https://www.analog.com/media/en/technical-documentation/data-sheets/max86178.pdf) and [EV-kit guide](https://www.analog.com/media/en/technical-documentation/user-guides/max86178evkit.pdf) describe chip/evaluation hardware capabilities and data paths. They do not identify Xiaomi Watch 2 Pro Wear OS callback field mapping, device register settings, sampling clock, units, or gain. Do not copy EV-kit assumptions into the watch parser.
- The [WFDB guide](https://physionet.org/physiotools/wpg/wpg_3.htm) defines ECG signals as samples at a fixed, specified sampling frequency; the [WFDB signal metadata guide](https://physionet.org/physiotools/wpg/wpg_27.htm) documents gain and physical-unit metadata. The 16-field row shape alone does not satisfy those signal-contract requirements.
- IEEE ECG/PPG signal-quality literature evaluates identified signals with known sample organization and timing; it does not establish arbitrary HAL field semantics from a plausible-looking curve. See [Orphanidou et al., IEEE JBHI](https://doi.org/10.1109/JBHI.2014.2338351) and [sampling frequency/resolution analysis](https://doi.org/10.1109/TELFOR.2017.8249438).
- FDA's [Diagnostic ECG Guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/diagnostic-ecg-guidance-including-non-alarming-st-segment-measurement-guidance-industry) explicitly does not cover automated interpretation/classification/diagnosis. Its [Arrhythmia Detector Guidance](https://www.fda.gov/medical-devices/guidance-documents-medical-devices-and-radiation-emitting-products/arrhythmia-detector-and-alarm-class-ii-special-controls-guidance-document-industry-and-fda-staff) describes validation against ECG waveform databases; neither document supplies the missing Xiaomi payload contract.
- A scoped public GitHub search found no verifiable Xiaomi/MAX86178 Wear OS event decoder or compatible EV-kit parser. ADI's [MAXREFDES106](https://www.analog.com/en/resources/reference-designs/maxrefdes106.html) describes its own platform and must not be treated as a compatible watch format without a byte/field-level specification and paired verification.

## 34 stages versus 36 catalog channels

These are different counters. `RawVitalsProbe.Kind.entries` and the catalog enumerate 36 channel kinds. A suite stage is a successfully started probe/check in the acquisition workflow; failed/unavailable starts can reduce the stage denominator, and several channels are grouped into phases. The counters must not be presented as 34-versus-36 decoding success. Keep separate fields for stage progress, channels with events, parsed candidate values, verified decoded samples, timing, quality, and calibration.

## Public reference record downloaded

Downloaded MIT-BIH Arrhythmia Database record 100 (`100.hea`, `100.dat`, `100.atr`) to `.local-research-data/mitdb-record-100/`. Its header reports two channels, 360 samples/s, 650,000 samples, MLII and V5 leads, and 200 ADC units/mV. PhysioNet describes the database as 48 two-channel, half-hour ambulatory ECG records with cardiologist-resolved beat annotations; the data is available under ODC-By 1.0 and must be attributed if its data or outputs are publicly used. Dataset citation and terms: [PhysioNet MIT-BIH record collection](https://physionet.org/content/mitdb/1.0.0/) and [ODC-By 1.0](https://physionet.org/content/mitdb/view-license/1.0.0/).

This is a suitable reference fixture for testing a generic ECG algorithm against annotated beats after a compatible WFDB reader/benchmark harness is added. It is not compatible evidence for the watch's opaque 16-field event matrix: the watch lacks established lead mapping, sample packing, sample clock, and gain metadata. No watch-vs-MIT-BIH clinical comparison or accuracy result is claimed.

## Promotion gates

1. Keep all 16 observed fields visible only as raw-field inspection, with explicit field indices and event-index x-axis.
2. Promote a field to ECG sample stream only after OEM/HAL documentation or synchronized reference-capture evidence proves field mapping, within-row ordering, cross-row continuity, and event/sample relationship.
3. Derive a physical time axis or sampling rate only from a verified device sample clock/period, not callback receipt spacing or the event index.
4. Convert to mV only with the watch-path gain/baseline/units contract or a device-specific reference calibration independently validated on held-out synchronized recordings.
5. Enable R-peak/RR/rate or rhythm interpretation only after the preceding signal/time gates and validation against annotated reference ECG data.

## Remaining evidence needed

- OEM/LifeQ contract for the 16 `SensorEvent.values` positions and counter semantics, or controlled paired capture from the watch and a synchronized reference ECG.
- Watch-side source timestamp/sample-period semantics and the precise event-to-sample packing rule.
- Device-specific lead/electrode, gain, baseline, ADC scaling, filtering, and supported clinical/research use description.
