# Offline wearable signal benchmark (research only)

## Paired-session metadata preflight (not collection authorization)

`paired_session.schema.json` defines metadata shape and comparison-mode conditions; the CLI validator is authoritative for cross-field checks such as matching units and synchronization error bounds. Run `validate_paired_session.py` for every record before admitting it to comparison. Example invocation: `.local-research-data/venv/Scripts/python research/clinical-data/validate_paired_session.py <metadata.json>`. Do not put names, contact details, raw waveform samples, or secrets in the record. A passing preflight does not authorize recruitment, data capture, clinical interpretation, or product claims. Collection remains disabled until the protocol owner documents applicable ethics/privacy/regulatory approvals and consent.

This harness evaluates a deliberately simple PPG peak-count baseline against the Wrist PPG During Exercise dataset's chest-ECG R-peak annotations. It is for reproducible signal-processing research, not diagnosis, calibration, clinical validation, or a claim about Xiaomi Watch 2 Pro.

## ECG QRS detector benchmark

`benchmark_ecg_qrs.py` evaluates WFDB XQRS or GQRS against the PhysioNet MIT-BIH Arrhythmia Database `atr` beat annotations. It reports per-record and pooled/macro sensitivity and positive predictive value with one-to-one beat matching and a fixed 150 ms tolerance. Run it after downloading the MIT-BIH data outside the app:

```powershell
.local-research-data/venv/Scripts/python research/clinical-data/benchmark_ecg_qrs.py `
  --data-dir .local-research-data/mitdb `
  --detector xqrs `
  --output .local-research-data/results/mitdb-xqrs.json
```

This is an algorithm/annotation pipeline benchmark on historical ambulatory ECG from other recorders. It does not validate the Xiaomi HAL payload, wrist electrode path, sample clock, ECG mapping, mV scale, AF classification, or product clinical performance. Keep the complete dataset outside the source/app distribution, follow the PhysioNet license, and cite Moody & Mark (2001; DOI `10.1109/51.932724`). A detector's beat sensitivity/PPV does not equal rhythm-classification performance.

## Setup

Each JSON benchmark report records SHA-256 fingerprints for the benchmark source, dataset registry, and complete dependency lock, plus Python/NumPy/SciPy/WFDB versions. This makes the analysis code, catalog, and dependency environment auditable alongside the downloaded dataset checksum manifest.

1. Review the dataset's [license and citation](https://physionet.org/content/wrist/1.0.0/) before use.
2. Download/extract the PhysioNet `wrist/1.0.0` files into `.local-research-data/wrist/1.0.0/`. Keep the data outside the app and source distribution.
3. Use Python `3.12.14` (see `.python-version`) and install the full, transitive dependency lock in an isolated virtual environment:

   ```powershell
   py -3 -m venv .local-research-data/venv
   .local-research-data/venv/Scripts/python -m pip install -r research/clinical-data/requirements-lock.txt
   ```

4. Run the benchmark:

   ```powershell
   .local-research-data/venv/Scripts/python research/clinical-data/benchmark_wrist_ppg.py `
     --data-dir .local-research-data/wrist/1.0.0 `
     --output .local-research-data/results/wrist-ppg-benchmark.json
   ```

The output includes dataset checksum verification, participant-level and pooled metrics, participant-cluster bootstrap percentile intervals for macro participant-level MAE/bias, explicit missing/invalid-record failures, and a literature-inspired exploratory candidate rejection rule. Bootstrap intervals use 10,000 deterministic resamples of participants (not windows), but with only eight participants they are coarse exploratory uncertainty summaries—not population or clinical-performance intervals. Any 30-second window intersecting an observed sample-time gap greater than two nominal sample periods is withheld from estimates and counted in the report; this is an input-integrity rule, not a physiological-quality threshold. The exploratory rule combines the same peak detector, physiological feasibility checks, and average pulse-template correlation ≥0.86 across all three 10-second subwindows in a 30-second window. Its acceptance rate is not a validated classifier-performance estimate because manual SQI labels are unavailable. It must not be enabled in the app or called a clinical quality classifier. Reported 30-second MAE/bias are descriptive comparisons under relative-zero assumed, unverified alignment, with no inter-device offset correction. The published dataset describes up to 1.21 s difference in recording durations and supports coarse averaged-rate comparison, not beat-level synchronization. No individual-level names or device identifiers are emitted. The participant ID in record names is treated as dataset-local pseudonymous metadata and is not copied to aggregate output.

The baseline filter/peak detector is intentionally explicit and versioned in source. Change it only with a version bump and rerun; report participant-disjoint results, not only pooled windows. Keep incomplete or rejected windows in coverage accounting. This public dataset can test a method on its own acquisition system; it cannot provide Xiaomi device calibration.

`requirements.txt` lists the direct dependencies with exact versions; `requirements-lock.txt` pins the complete tested environment, including transitive packages. When dependencies change, update both files from a clean Python `3.12.14` environment and rerun all registry/benchmark tests and the offline benchmark. The runner fingerprints `requirements-lock.txt` in each result.
