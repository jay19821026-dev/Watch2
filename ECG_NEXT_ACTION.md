# ECG next action

1. Restore this checkout's original `.git` metadata or identify the authoritative remote and base commit; then create `feature/ecg-decoder-recovery`. Do not initialize an unrelated history.
2. The 2026-10-10 paired-device capture is now available: first segment source event timestamps were positive, while later segments from the same measurement flow were negative. Investigate this sign change at the watch SensorEvent boundary with the same sensor profile fingerprint and session IDs; do not convert negative timestamps into acquisition time or use callback cadence as a replacement. Obtain an OEM/HAL field contract or a synchronized reference ECG with full event rows and source/callback times. Do not upload personal traces to external AI.
3. Test within-row and cross-row mapping against the reference; verify counter semantics and source sample clock independently. Only then revise the decoder and repeat unit, transport, Room, UI, and paired-device tests.
4. Keep AFib classification in research isolation until independent labelled validation establishes its scope. PPG irregular-pulse screening and daily HR are separate outputs and cannot substitute for ECG validation.

## Latest evidence

On 2026-10-14 the watch supplied 209 ECG events with populated elapsed-time metadata, but no verified payload mapping or ADC/sample clock. The next necessary action is to obtain the OEM/HAL event contract or a synchronized reference ECG containing the complete event rows; another formula-only decoder pass is not justified. The phone must also be connected for paired transport verification.

The app's `2,000µs` ECG registration request and the sensor's `500Hz` metadata maximum are capability/request evidence only; neither can unlock time-domain ECG calculations.

Public MAX86178 evaluation documentation was checked. It documents selectable ECG ADC rates and a 512 Hz requirement for its evaluation HR algorithm, but it does not expose Xiaomi Watch 2 Pro's vendor payload contract or configuration. Do not transplant the evaluation setting into the watch. The next actionable evidence remains either the Xiaomi/HAL field contract or a synchronized reference ECG capture containing complete event rows and source/callback times.

Investigation budget for this request is one bounded cycle. Record actual start/end times in the test report; do not claim estimated engineering hours. If no new mapping/time evidence is obtained, leave `ECG_STATUS = RESEARCH_BLOCKED` and ship other features only after their own acceptance gates pass.

## Current bounded decision

The software-side presentation ambiguity is resolved: raw capture, quality rejection, usable calculation, and sync failure are separate. No additional formula-only decoder change is justified by current evidence. Resume waveform decoding only after receiving either the Xiaomi/vendor HAL field contract or a synchronized watch-plus-reference ECG capture that identifies field mapping and acquisition timing. Until then, keep `ECG_STATUS = RESEARCH_BLOCKED`; this status must not block release of independently accepted non-ECG features.
