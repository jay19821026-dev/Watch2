# AI review routing — 2026-10-05

## Verified

- **Hermes**: local Ollama endpoint `127.0.0.1:11434` responds to `/api/tags`; use only sanitized summaries and treat output as untrusted review input.
- **Grokbot**: not verified. Local port `8080` belongs to NVIDIA Broadcast and returns HTTP 404 at `/`; it is not routed as Grok.

Latest Hermes review is saved at `.tooling/verify/hermes-ecg-review-latest-20261005.txt`. Its useful findings were the unresolved field mapping/calibration and the reproducibility risk of bounded/manual capture. Its suggestion to decode or calibrate before OEM evidence was rejected by the project safety gate.

## Decision

Codex remains the integrator and source-of-truth reviewer. Hermes may independently review de-identified acquisition, provenance, timestamp and UI evidence. No raw health export, device identifier, credential, or source repository is sent to either service. A Grok result is recorded only after a real authenticated endpoint returns a response.

## Current sensor boundary

AI may explain received raw events and suggest the next measurement. It may not generate or overwrite sensor values, timestamps, units, calibration coefficients, or diagnoses. ECG/PPG/BioZ charts remain explicitly unscaled/uncalibrated when the device mapping is not verified. The capture safety limit and manual continuation remain enabled.
# 2026-10-05 routing verification

- Hermes route remains configured for the verified loopback Ollama endpoint and `hermes3:8b`; the 120-second bounded retry completed and was saved to `.tooling/verify/hermes-final-review-20261005.md`.
- Hermes independently identified the same three unresolved ECG risks: sample clock, field mapping, and units. It did not receive raw data and did not provide a diagnosis or calibration value.
- Local port 8080 still returns `Cannot GET /` and is owned by NVIDIA Broadcast, not a verified Grokbot endpoint.
- Grokbot remains opt-in and unintegrated until a loopback endpoint and request schema are verified. No health data, device identifiers, credentials, or source files were sent.
- Codex remains the integrator and source of truth; external reviews remain untrusted input and cannot authorize values, calibration, or medical diagnoses.
