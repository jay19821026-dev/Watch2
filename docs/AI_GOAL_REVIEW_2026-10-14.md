# AI Goal Review — 2026-10-14

## Purpose

Adversarially review the persistent project goal without sharing raw health exports, waveforms, device/account identifiers, credentials, IP addresses, or source files. AI opinions are review input, not evidence of hardware behavior, calibration, or clinical validity.

## Reviewers consulted

- Local architecture reviewer: transport contract, ownership, migration, offline delivery, release gates.
- Local QA reviewer: deterministic replay, restart boundaries, persistence, freshness, ordering, cross-version and paired-device cases.
- Local safety/privacy/power reviewer: measurable evidence policy, validation protocol, reminders, privacy lifecycle, AI claim controls, battery gates.
- Local product/UX reviewer: terminal states, plain-language conclusions, next actions, history, watch UX, accessibility.
- Browser AI reviewers received or may receive the same sanitized brief. Their responses are accepted only when independently verified; non-response is not treated as agreement.

## Consensus adopted

1. Freeze a per-metric support matrix and versioned phone/watch contract before expanding interpretations.
2. Every session ends in one truthful terminal state plus one practical next action.
3. `可用結果` requires a machine-readable evidence contract; missing provenance, unit, timestamp, freshness, quality, calibration, or consent fails closed for that feature.
4. Raw, processed, estimated, imputed, and physiological outputs remain distinct and traceable.
5. ECG research remains isolated and cannot block verified daily-health functions.
6. Replay, transport acknowledgement, deduplication, ordering, process death, migration, freshness, cross-version, privacy, power, and paired-device UI are release gates.
7. AI output uses typed inputs, a closed output schema, approved language, and traceable policy/source versions.
8. Ordinary UI is concise; technical evidence remains in Raw/Debug/Export.

## Important disagreements resolved

- Five visible states were insufficient. The project uses eight user-facing terminal outcomes while preserving more detailed internal reason codes.
- “Immutable” now means append-only while retained, never exemption from user deletion.
- ECG readiness is split into raw capture, research analysis, and clinical validation instead of one ambiguous `READY` state.
- Numeric battery and timeout gates must be predeclared and measured; no AI may invent a universal drain threshold.
- External AI consensus cannot establish OEM field mapping, sampling clocks, calibration, or medical validity.

## Unresolved evidence

- Xiaomi/OEM payload field contract and device-specific ADC/timebase semantics.
- Device-level ECG raw-to-mV calibration and synchronized external reference validation.
- Clinical AFib performance on a frozen protocol and independent reference dataset.
- Final battery budget after controlled paired-device baseline runs.
- Release signing credentials and store publication evidence when applicable.

The authoritative objective is `docs/ACTIVE_PROJECT_GOAL.md`.
