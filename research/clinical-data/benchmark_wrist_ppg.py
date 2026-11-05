#!/usr/bin/env python3
"""Research-only wrist PPG pulse-rate benchmark against chest ECG annotations."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import platform
import re
from importlib.metadata import version
from pathlib import Path
from typing import Any

import numpy as np
import wfdb
from scipy.signal import butter, find_peaks, periodogram, sosfiltfilt


BASELINE_VERSION = "research-ppg-baselines-v4"
WINDOW_SECONDS = 30
QUALITY_WINDOW_SECONDS = 10
MIN_REFERENCE_BEATS = 15
MIN_PPG_BEATS = 15
RESEARCH_SQI_CORRELATION_THRESHOLD = 0.86
MAX_INTER_SAMPLE_GAP_PERIODS = 2.0
PARTICIPANT_BOOTSTRAP_REPLICATES = 10_000
PARTICIPANT_BOOTSTRAP_SEED = 20260928


def resolve_dataset_path(data_dir: Path, relative_path: str) -> Path:
    root = data_dir.resolve()
    candidate = (root / relative_path).resolve()
    candidate.relative_to(root)
    return candidate


def verify_checksums(data_dir: Path) -> dict[str, Any]:
    manifest = data_dir / "SHA256SUMS.txt"
    if not manifest.exists():
        return {"state": "not_checked", "reason": "SHA256SUMS.txt missing"}
    checked = 0
    failures: list[str] = []
    missing: list[str] = []
    invalid_paths: list[str] = []
    malformed_lines: list[int] = []
    duplicate_paths: list[str] = []
    seen_paths: set[str] = set()
    expected_count = 0
    for line_number, line in enumerate(manifest.read_text(encoding="utf-8", errors="replace").splitlines(), start=1):
        if not line.strip():
            continue
        match = re.match(r"^([0-9a-fA-F]{64})\s+\*?(.+?)\s*$", line)
        if not match:
            malformed_lines.append(line_number)
            continue
        expected_digest, relative = match.groups()
        expected_count += 1
        relative_path = relative.lstrip("* ")
        try:
            file_path = resolve_dataset_path(data_dir, relative_path)
        except ValueError:
            invalid_paths.append(relative_path)
            continue
        normalized_path = str(file_path).casefold()
        if normalized_path in seen_paths:
            duplicate_paths.append(relative_path)
            continue
        seen_paths.add(normalized_path)
        if not file_path.is_file():
            missing.append(relative)
            continue
        actual = hashlib.sha256(file_path.read_bytes()).hexdigest()
        checked += 1
        if actual.lower() != expected_digest.lower():
            failures.append(relative)
    return {
        "state": "failed" if failures or invalid_paths or malformed_lines or duplicate_paths else "incomplete" if missing else "verified" if checked and expected_count else "not_checked",
        "files_expected": expected_count,
        "files_checked": checked,
        "mismatches": failures,
        "missing": missing,
        "invalid_paths": invalid_paths,
        "malformed_manifest_lines": malformed_lines,
        "duplicate_manifest_paths": duplicate_paths,
    }


def ppg_peak_indices(signal: np.ndarray, sample_rate_hz: float) -> np.ndarray:
    if signal.ndim != 1 or signal.size < sample_rate_hz * 10:
        return np.array([], dtype=int)
    if not math.isfinite(sample_rate_hz) or sample_rate_hz <= 8:
        return np.array([], dtype=int)
    finite = np.isfinite(signal)
    if not finite.any():
        return np.array([], dtype=int)
    valid_length = int(np.count_nonzero(finite))
    if not finite[:valid_length].all() or finite[valid_length:].any():
        return np.array([], dtype=int)
    signal = signal[:valid_length].astype(float)
    if signal.size < sample_rate_hz * 10:
        return np.array([], dtype=int)
    centered = signal - float(np.median(signal))
    try:
        sos = butter(3, (0.5, 4.0), btype="bandpass", fs=sample_rate_hz, output="sos")
        filtered = sosfiltfilt(sos, centered)
    except ValueError:
        return np.array([], dtype=int)
    spread = float(np.std(filtered))
    if not math.isfinite(spread) or spread <= 1e-12:
        return np.array([], dtype=int)
    min_distance = max(1, int(sample_rate_hz * 60.0 / 240.0))
    prominence = spread * 0.20
    positive, _ = find_peaks(filtered, distance=min_distance, prominence=prominence)
    negative, _ = find_peaks(-filtered, distance=min_distance, prominence=prominence)
    return positive if len(positive) >= len(negative) else negative


def ppg_spectral_bpm(signal: np.ndarray, sample_rate_hz: float) -> float | None:
    if signal.ndim != 1 or signal.size < sample_rate_hz * 10:
        return None
    if not math.isfinite(sample_rate_hz) or sample_rate_hz <= 8 or not np.isfinite(signal).all():
        return None
    centered = signal.astype(float) - float(np.median(signal))
    try:
        sos = butter(3, (0.5, 4.0), btype="bandpass", fs=sample_rate_hz, output="sos")
        filtered = sosfiltfilt(sos, centered)
    except ValueError:
        return None
    if not math.isfinite(float(np.std(filtered))) or float(np.std(filtered)) <= 1e-12:
        return None
    frequencies, power = periodogram(filtered, fs=sample_rate_hz)
    physiological_band = (frequencies >= 0.5) & (frequencies <= 4.0)
    if not physiological_band.any():
        return None
    selected = int(np.argmax(np.where(physiological_band, power, -np.inf)))
    bpm = float(frequencies[selected] * 60.0)
    return bpm if math.isfinite(bpm) else None


def ppg_quality_screen(signal: np.ndarray, sample_rate_hz: float) -> dict[str, Any]:
    """Apply a literature-inspired candidate screen; not validated for this dataset/device."""
    if signal.ndim != 1 or not math.isfinite(sample_rate_hz) or sample_rate_hz <= 0:
        return {"accepted": False, "reason": "invalid_shape_or_rate", "mean_template_correlation": None}
    if signal.size < sample_rate_hz * QUALITY_WINDOW_SECONDS:
        return {"accepted": False, "reason": "short_window", "mean_template_correlation": None}
    if not np.isfinite(signal).all():
        return {"accepted": False, "reason": "nonfinite_samples", "mean_template_correlation": None}

    peaks = ppg_peak_indices(signal, sample_rate_hz)
    intervals = np.diff(peaks) / sample_rate_hz
    if len(intervals) < 3:
        return {"accepted": False, "reason": "too_few_detected_pulses", "mean_template_correlation": None}
    bpm = 60.0 / float(np.mean(intervals))
    if not 40.0 <= bpm <= 180.0:
        return {"accepted": False, "reason": "heart_rate_outside_research_screen", "mean_template_correlation": None}
    if float(np.max(intervals)) > 3.0:
        return {"accepted": False, "reason": "inter_pulse_gap_over_3s", "mean_template_correlation": None}
    if float(np.max(intervals) / np.min(intervals)) >= 2.2:
        return {"accepted": False, "reason": "inter_pulse_interval_ratio", "mean_template_correlation": None}

    template = pulse_template(signal, peaks, sample_rate_hz)
    if template is None:
        return {"accepted": False, "reason": "template_unavailable", "mean_template_correlation": None}
    mean_correlation = float(np.mean(template["correlations"]))
    accepted = mean_correlation >= RESEARCH_SQI_CORRELATION_THRESHOLD
    return {
        "accepted": accepted,
        "reason": None if accepted else "template_correlation_below_research_threshold",
        "mean_template_correlation": mean_correlation,
        "research_threshold": RESEARCH_SQI_CORRELATION_THRESHOLD,
    }


def pulse_template(signal: np.ndarray, peaks: np.ndarray, sample_rate_hz: float) -> dict[str, Any] | None:
    intervals = np.diff(peaks)
    if len(intervals) < 3:
        return None
    template_size = max(16, int(round(float(np.median(intervals)))))
    half = template_size // 2
    x = np.linspace(-half, half, 64)
    beats: list[np.ndarray] = []
    for peak in peaks:
        start, end = int(peak) - half, int(peak) + half
        if start < 0 or end > len(signal):
            continue
        beat = signal[start:end].astype(float)
        if len(beat) < 8 or float(np.std(beat)) <= 1e-12:
            continue
        beats.append(np.interp(x, np.arange(len(beat)) - half, beat))
    if len(beats) < 3:
        return None
    matrix = np.vstack(beats)
    mean = np.mean(matrix, axis=0)
    mean_std = float(np.std(mean))
    if mean_std <= 1e-12:
        return None
    correlations = []
    for beat in matrix:
        beat_std = float(np.std(beat))
        if beat_std <= 1e-12:
            continue
        correlations.append(float(np.corrcoef(beat, mean)[0, 1]))
    if len(correlations) < 3 or not np.isfinite(correlations).all():
        return None
    return {"correlations": correlations}


def elapsed_seconds_from_wrapped_clock(clock: np.ndarray) -> np.ndarray:
    """Unwrap dataset timestamps documented to wrap once per 60-second minute."""
    if clock.ndim != 1 or clock.size < 2 or not np.isfinite(clock).all():
        return np.array([], dtype=float)
    if np.any(clock < 0.0) or np.any(clock >= 60.0):
        return np.array([], dtype=float)
    deltas = np.diff(clock.astype(float))
    deltas[deltas < -30.0] += 60.0
    if np.any(deltas <= 0.0):
        return np.array([], dtype=float)
    return np.concatenate(([0.0], np.cumsum(deltas)))


def window_has_sampling_integrity(
    elapsed: np.ndarray,
    sample_rate_hz: float,
    start_seconds: float,
    end_seconds: float,
) -> bool:
    if (
        elapsed.ndim != 1
        or elapsed.size < 2
        or not np.isfinite(elapsed).all()
        or not math.isfinite(sample_rate_hz)
        or sample_rate_hz <= 0
        or not math.isfinite(start_seconds)
        or not math.isfinite(end_seconds)
        or start_seconds >= end_seconds
    ):
        return False
    gaps = np.diff(elapsed)
    maximum_gap = MAX_INTER_SAMPLE_GAP_PERIODS / sample_rate_hz
    intersects_window = (elapsed[:-1] < end_seconds) & (elapsed[1:] > start_seconds)
    return not bool(np.any(intersects_window & (gaps > maximum_gap)))


def record_comparisons(record_path: Path) -> list[dict[str, Any]]:
    record = wfdb.rdrecord(str(record_path), physical=True)
    if not record.fs or not math.isfinite(float(record.fs)):
        return []
    signal_names = [name.strip().lower() for name in (record.sig_name or [])]
    if "wrist_ppg" not in signal_names:
        return []
    if "sample_times_for_all_signals_apart_from_ecg" not in signal_names:
        return []
    ppg_index = signal_names.index("wrist_ppg")
    ppg = np.asarray(record.p_signal[:, ppg_index], dtype=float)
    clock_index = signal_names.index("sample_times_for_all_signals_apart_from_ecg")
    clock = np.asarray(record.p_signal[:, clock_index], dtype=float)
    finite = np.isfinite(ppg) & np.isfinite(clock)
    if not finite.any():
        return []
    valid_length = int(np.count_nonzero(finite))
    if not finite[:valid_length].all() or finite[valid_length:].any():
        return []
    ppg = ppg[:valid_length]
    elapsed = elapsed_seconds_from_wrapped_clock(clock[:valid_length])
    if elapsed.size != ppg.size:
        return []
    nominal_fs = float(record.fs)
    uniform_time = np.arange(0.0, elapsed[-1], 1.0 / nominal_fs)
    if uniform_time.size < nominal_fs * 10:
        return []
    uniform_ppg = np.interp(uniform_time, elapsed, ppg)
    annotation = wfdb.rdann(str(record_path), "atr")
    r_peaks = np.asarray(annotation.sample, dtype=int)
    ppg_peak_indices_uniform = ppg_peak_indices(uniform_ppg, nominal_fs)
    ppg_peak_times = uniform_time[ppg_peak_indices_uniform]
    duration = min(float(elapsed[-1]), int(record.sig_len or len(ppg)) / nominal_fs)
    windows = int(duration // WINDOW_SECONDS)
    rows: list[dict[str, Any]] = []
    for window_index in range(windows):
        start_seconds = window_index * WINDOW_SECONDS
        end_seconds = start_seconds + WINDOW_SECONDS
        sampling_integrity_accepted = window_has_sampling_integrity(
            elapsed, nominal_fs, start_seconds, end_seconds
        )
        ref_times = r_peaks / nominal_fs
        ref_count = int(np.count_nonzero((ref_times >= start_seconds) & (ref_times < end_seconds)))
        ppg_count = int(np.count_nonzero((ppg_peak_times >= start_seconds) & (ppg_peak_times < end_seconds)))
        reference_bpm = ref_count * 60.0 / WINDOW_SECONDS
        sample_start = int(start_seconds * nominal_fs)
        sample_end = int(end_seconds * nominal_fs)
        ppg_window = uniform_ppg[sample_start:sample_end]
        spectral_bpm = ppg_spectral_bpm(ppg_window, nominal_fs) if sampling_integrity_accepted else None
        quality_checks = [
            ppg_quality_screen(ppg_window[offset * int(nominal_fs * QUALITY_WINDOW_SECONDS):(offset + 1) * int(nominal_fs * QUALITY_WINDOW_SECONDS)], nominal_fs)
            for offset in range(WINDOW_SECONDS // QUALITY_WINDOW_SECONDS)
        ] if sampling_integrity_accepted else []
        quality_reasons = sorted({check["reason"] for check in quality_checks if not check["accepted"]})
        if not sampling_integrity_accepted:
            quality_reasons.append("sample_clock_gap_over_2_periods")
        quality_accepted = sampling_integrity_accepted and len(quality_checks) == WINDOW_SECONDS // QUALITY_WINDOW_SECONDS and not quality_reasons
        valid_reference = ref_count >= MIN_REFERENCE_BEATS
        peak_accepted = valid_reference and sampling_integrity_accepted and ppg_count >= MIN_PPG_BEATS
        spectral_accepted = valid_reference and sampling_integrity_accepted and spectral_bpm is not None
        rows.append({
            "window_start_seconds": start_seconds,
            "reference_bpm": reference_bpm if valid_reference else None,
            "peak_count_bpm": ppg_count * 60.0 / WINDOW_SECONDS if peak_accepted else None,
            "spectral_bpm": spectral_bpm if spectral_accepted else None,
            "reference_accepted": valid_reference,
            "sampling_integrity_accepted": sampling_integrity_accepted,
            "peak_count_accepted": peak_accepted,
            "spectral_accepted": spectral_accepted,
            "quality_accepted": quality_accepted,
            "quality_rejection_reasons": quality_reasons,
            "mean_sqi_template_correlation": float(np.mean([
                check["mean_template_correlation"] for check in quality_checks
                if check["mean_template_correlation"] is not None
            ])) if any(check["mean_template_correlation"] is not None for check in quality_checks) else None,
            "reason": None if valid_reference and sampling_integrity_accepted else (
                "insufficient_reference_beats" if not valid_reference else "sample_clock_gap_over_2_periods"
            ),
        })
    return rows


def summarize_method(windows: list[dict[str, Any]], field: str, acceptance_key: str) -> dict[str, Any]:
    reference_windows = [window for window in windows if window["reference_accepted"]]
    accepted = [window for window in windows if window[acceptance_key]]
    errors = [window[field] - window["reference_bpm"] for window in accepted]
    return {
        "accepted_window_count": len(accepted),
        "reference_window_count": len(reference_windows),
        "estimate_emission_coverage": len(accepted) / len(reference_windows) if reference_windows else None,
        "mae_bpm": float(np.mean(np.abs(errors))) if errors else None,
        "bias_bpm": float(np.mean(errors)) if errors else None,
        "note": "emission coverage is not signal-quality or clinical-validity coverage",
    }


def summarize_quality_gated_method(windows: list[dict[str, Any]], field: str, acceptance_key: str) -> dict[str, Any]:
    reference_windows = [window for window in windows if window["reference_accepted"]]
    quality_windows = [window for window in reference_windows if window["quality_accepted"]]
    accepted = [window for window in quality_windows if window[acceptance_key]]
    errors = [window[field] - window["reference_bpm"] for window in accepted]
    rejection_counts: dict[str, int] = {}
    for window in reference_windows:
        for reason in window["quality_rejection_reasons"]:
            rejection_counts[reason] = rejection_counts.get(reason, 0) + 1
    return {
        "accepted_window_count": len(accepted),
        "reference_window_count": len(reference_windows),
        "quality_accepted_window_count": len(quality_windows),
        "quality_rejected_window_count": len(reference_windows) - len(quality_windows),
        "quality_rejection_reason_counts_nonexclusive": rejection_counts,
        "estimate_emission_coverage_after_quality_gate": len(accepted) / len(reference_windows) if reference_windows else None,
        "mae_bpm_after_quality_gate": float(np.mean(np.abs(errors))) if errors else None,
        "bias_bpm_after_quality_gate": float(np.mean(errors)) if errors else None,
        "note": "exploratory literature-derived screen; no manual quality labels, not validated or transferable to Xiaomi",
    }


def participant_id(record_name: str) -> str:
    """Derive dataset-local participant grouping from the record basename."""
    return record_name.split("_")[0]


def participant_bootstrap_ci(values: list[float]) -> dict[str, float] | None:
    if len(values) < 2:
        return None
    rng = np.random.default_rng(PARTICIPANT_BOOTSTRAP_SEED)
    samples = np.asarray(values, dtype=np.float64)
    indices = rng.integers(0, len(samples), size=(PARTICIPANT_BOOTSTRAP_REPLICATES, len(samples)))
    bootstrapped_means = np.mean(samples[indices], axis=1)
    lower, upper = np.percentile(bootstrapped_means, [2.5, 97.5])
    return {"lower": float(lower), "upper": float(upper)}


def summarize_by_participant(records: list[dict[str, Any]], field: str, acceptance_key: str) -> dict[str, Any]:
    grouped: dict[str, list[dict[str, Any]]] = {}
    for record in records:
        if record["state"] != "processed":
            continue
        grouped.setdefault(participant_id(record["record"]), []).extend(record["windows"])
    summaries = {
        participant: summarize_method(windows, field, acceptance_key)
        for participant, windows in sorted(grouped.items())
    }
    participant_maes = [row["mae_bpm"] for row in summaries.values() if row["mae_bpm"] is not None]
    participant_biases = [row["bias_bpm"] for row in summaries.values() if row["bias_bpm"] is not None]
    return {
        "participants": summaries,
        "participants_with_estimates": len(participant_maes),
        "macro_mae_bpm": float(np.mean(participant_maes)) if participant_maes else None,
        "macro_bias_bpm": float(np.mean(participant_biases)) if participant_biases else None,
        "macro_mae_ci95_bpm": participant_bootstrap_ci(participant_maes),
        "macro_bias_ci95_bpm": participant_bootstrap_ci(participant_biases),
        "confidence_interval_method": {
            "method": "percentile bootstrap of participant-level metrics",
            "resampling_unit": "participant",
            "replicates": PARTICIPANT_BOOTSTRAP_REPLICATES,
            "seed": PARTICIPANT_BOOTSTRAP_SEED,
            "interpretation": "exploratory uncertainty interval; small cohort and dataset-specific, not population or clinical performance",
        } if len(participant_maes) >= 2 else None,
        "aggregation": "unweighted mean of participant-level metrics; records/windows within a participant are pooled",
    }


def alignment_assessment() -> dict[str, Any]:
    return {
        "status": "relative_zero_assumed_unverified",
        "offset_correction_applied": False,
        "maximum_documented_device_duration_difference_seconds": 1.21,
        "note": "PPG timestamps and ECG annotation indices are compared from relative zero; this is a coarse average-rate comparison, not beat-level synchronization.",
    }


def benchmark_provenance() -> dict[str, Any]:
    source_path = Path(__file__).resolve()
    registry_path = source_path.with_name("registry.json")
    lock_path = source_path.with_name("requirements-lock.txt")
    return {
        "benchmark_source_sha256": hashlib.sha256(source_path.read_bytes()).hexdigest(),
        "registry_sha256": hashlib.sha256(registry_path.read_bytes()).hexdigest(),
        "dependency_lock_sha256": hashlib.sha256(lock_path.read_bytes()).hexdigest(),
        "python_version": platform.python_version(),
        "dependency_versions": {
            package: version(package)
            for package in ("numpy", "scipy", "wfdb")
        },
    }


def run(data_dir: Path) -> dict[str, Any]:
    records_file = data_dir / "RECORDS"
    if not records_file.is_file():
        raise FileNotFoundError(f"Missing WFDB RECORDS manifest: {records_file}")
    checksums = verify_checksums(data_dir)
    if checksums["state"] != "verified":
        raise ValueError(f"Dataset integrity not established: state={checksums['state']} missing={checksums.get('missing', [])} mismatches={checksums.get('mismatches', [])}")
    record_names = [line.strip() for line in records_file.read_text().splitlines() if line.strip()]
    results: list[dict[str, Any]] = []
    for name in record_names:
        try:
            base = resolve_dataset_path(data_dir, name)
        except ValueError:
            results.append({"record": name, "state": "invalid_manifest_path", "windows": []})
            continue
        header = base.with_suffix(".hea")
        annotation = base.with_suffix(".atr")
        if not header.is_file() or not annotation.is_file():
            missing = []
            if not header.is_file():
                missing.append("header")
            if not annotation.is_file():
                missing.append("annotation")
            results.append({"record": name, "state": "missing_files", "missing": missing, "windows": []})
            continue
        try:
            windows = record_comparisons(base)
        except Exception as exc:  # Retain a bounded per-record failure, not a silent omission.
            results.append({"record": name, "state": "read_error", "error": type(exc).__name__, "windows": []})
            continue
        results.append({"record": name, "state": "processed", "windows": windows})

    all_windows = [window for item in results for window in item["windows"]]
    method_results: dict[str, Any] = {
        "peak_count": summarize_method(all_windows, "peak_count_bpm", "peak_count_accepted"),
        "spectral_peak": summarize_method(all_windows, "spectral_bpm", "spectral_accepted"),
    }
    method_results["peak_count"]["participant_summary"] = summarize_by_participant(
        results, "peak_count_bpm", "peak_count_accepted"
    )
    method_results["spectral_peak"]["participant_summary"] = summarize_by_participant(
        results, "spectral_bpm", "spectral_accepted"
    )
    for method_name, field, accepted_key in (
        ("peak_count", "peak_count_bpm", "peak_count_accepted"),
        ("spectral_peak", "spectral_bpm", "spectral_accepted"),
    ):
        method_results[method_name]["quality_gated"] = summarize_quality_gated_method(
            all_windows, field, accepted_key
        )
    for item in results:
        if item["state"] != "processed":
            continue
        item["method_results"] = {
            "peak_count": summarize_method(item["windows"], "peak_count_bpm", "peak_count_accepted"),
            "spectral_peak": summarize_method(item["windows"], "spectral_bpm", "spectral_accepted"),
            "quality_rejected_window_count": sum(
                window["reference_accepted"] and not window["quality_accepted"] for window in item["windows"]
            ),
            "sampling_gap_rejected_window_count": sum(
                not window["sampling_integrity_accepted"] for window in item["windows"]
            ),
        }
    valid_references = [window for window in all_windows if window["reference_accepted"]]
    return {
        "schema_version": 2,
        "dataset_id": "physionet-wrist-ppg-exercise-1.0.0",
        "baseline_version": BASELINE_VERSION,
        "provenance": benchmark_provenance(),
        "task": "research-only PPG pulse-rate estimate vs chest ECG annotated R-peak count",
        "window_seconds": WINDOW_SECONDS,
        "metric_interpretation": "descriptive_30_second_window_metrics_under_relative_zero_assumed_unverified_alignment; not beat-level agreement or population performance",
        "checksum": checksums,
        "record_count_listed": len(record_names),
        "record_count_processed": sum(item["state"] == "processed" for item in results),
        "record_read_errors": [item for item in results if item["state"] == "read_error"],
        "record_input_failures": [item for item in results if item["state"] in {"read_error", "missing_files", "invalid_manifest_path"}],
        "window_count": len(all_windows),
        "reference_valid_window_count": len(valid_references),
        "sampling_gap_rejected_window_count": sum(
            not window["sampling_integrity_accepted"] for window in all_windows
        ),
        "alignment_assessment": alignment_assessment(),
        "method_results": method_results,
        "limitations": [
            "not a clinical validation, medical-device calibration, or Xiaomi Watch 2 Pro performance result",
            "simple offline peak-count baseline; windowed counts quantize rates and do not assess beat-to-beat agreement",
            "30-second MAE and bias use relative-zero assumed alignment without offset correction; participant bootstrap intervals are exploratory only and eight participants do not establish population performance",
            "spectral dominant-frequency baseline can select motion harmonics; no validated signal-quality rejection threshold is implemented",
            "Shimmer sample clock wraps every 60 seconds and is unwrapped before uniform resampling; ECG reference uses its own 256 Hz sample index",
            "windows intersecting an observed sample-time gap greater than two nominal sample periods are withheld; this is a data-integrity rule, not a physiological quality threshold",
            "dataset paper reports up to 1.21 s inter-device alignment/duration difference; this is included as a limitation",
            "candidate quality rule combines peak detection and physiological feasibility checks with template correlation; zero accepted windows do not imply zero usable recordings because manual quality labels are absent",
            "dataset-local records are grouped by participant prefix for split design; this aggregate run is not an independent holdout evaluation",
            "annotation, timing, sample-rate, and signal semantics must be verified for each dataset record",
        ],
        "records": results,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    report = run(args.data_dir)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({key: report[key] for key in (
        "dataset_id", "baseline_version", "checksum", "record_count_processed",
        "record_count_listed", "reference_valid_window_count", "window_count",
        "alignment_assessment", "method_results",
    )}, indent=2))


if __name__ == "__main__":
    main()
