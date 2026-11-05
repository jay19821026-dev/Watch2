"""Research-only QRS detector benchmark against WFDB beat annotations."""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
from pathlib import Path
import platform
from typing import Any

import numpy as np
import wfdb
from wfdb import processing

BEAT_SYMBOLS = {
    "N", "L", "R", "B", "A", "a", "J", "S", "V", "r", "F", "e", "j", "n", "E", "/", "f", "Q", "?"
}
TOLERANCE_MS = 150


def match_peaks(reference: np.ndarray, detected: np.ndarray, tolerance_samples: int) -> dict[str, Any]:
    reference = np.asarray(reference, dtype=int)
    detected = np.asarray(detected, dtype=int)
    if reference.ndim != 1 or detected.ndim != 1 or tolerance_samples < 0:
        raise ValueError("peak arrays must be one-dimensional and tolerance non-negative")
    if reference.size and np.any(np.diff(reference) <= 0):
        raise ValueError("reference peaks must be strictly increasing")
    if detected.size and np.any(np.diff(detected) <= 0):
        raise ValueError("detected peaks must be strictly increasing")

    matched_reference: set[int] = set()
    true_positive = 0
    for peak in detected:
        insertion = int(np.searchsorted(reference, peak))
        candidates = [index for index in (insertion - 1, insertion) if 0 <= index < reference.size]
        candidates = [index for index in candidates if index not in matched_reference]
        if not candidates:
            continue
        closest = min(candidates, key=lambda index: abs(int(reference[index]) - int(peak)))
        if abs(int(reference[closest]) - int(peak)) <= tolerance_samples:
            matched_reference.add(closest)
            true_positive += 1

    false_positive = int(detected.size) - true_positive
    false_negative = int(reference.size) - true_positive
    return {
        "reference_beats": int(reference.size),
        "detected_peaks": int(detected.size),
        "true_positive": true_positive,
        "false_positive": false_positive,
        "false_negative": false_negative,
        "sensitivity": true_positive / reference.size if reference.size else None,
        "positive_predictive_value": true_positive / detected.size if detected.size else None,
    }


def evaluate_record(record_path: Path, detector_name: str = "xqrs") -> dict[str, Any]:
    record = wfdb.rdrecord(str(record_path), physical=True)
    if not record.fs or not np.isfinite(float(record.fs)):
        raise ValueError("record has no finite sample rate")
    annotations = wfdb.rdann(str(record_path), "atr")
    reference = np.asarray(
        [sample for sample, symbol in zip(annotations.sample, annotations.symbol) if symbol in BEAT_SYMBOLS],
        dtype=int,
    )
    if reference.size == 0:
        raise ValueError("record has no recognized beat annotations")
    signal_names = [name.strip() for name in record.sig_name or []]
    channel = 0
    signal = np.asarray(record.p_signal[:, channel], dtype=float)
    if signal.ndim != 1 or signal.size < 3 or not np.isfinite(signal).all():
        raise ValueError("reference channel is empty or non-finite")
    fs = float(record.fs)
    if detector_name == "xqrs":
        detected = processing.xqrs_detect(signal, fs=fs, verbose=False)
    elif detector_name == "gqrs":
        detected = processing.gqrs_detect(sig=signal, fs=fs)
    else:
        raise ValueError(f"unsupported detector: {detector_name}")
    metrics = match_peaks(reference, np.asarray(detected, dtype=int), round(TOLERANCE_MS * fs / 1000.0))
    return {
        "record": record_path.name,
        "signal_name": signal_names[channel] if channel < len(signal_names) else None,
        "sample_rate_hz": fs,
        "tolerance_ms": TOLERANCE_MS,
        "detector": detector_name,
        **metrics,
    }


def summarize(records: list[dict[str, Any]]) -> dict[str, Any]:
    valid = [item for item in records if item.get("sensitivity") is not None]
    total_reference = sum(item["reference_beats"] for item in valid)
    total_detected = sum(item["detected_peaks"] for item in valid)
    total_tp = sum(item["true_positive"] for item in valid)
    return {
        "record_count": len(valid),
        "pooled_sensitivity": total_tp / total_reference if total_reference else None,
        "pooled_positive_predictive_value": total_tp / total_detected if total_detected else None,
        "macro_record_sensitivity": float(np.mean([item["sensitivity"] for item in valid])) if valid else None,
        "macro_record_positive_predictive_value": float(np.mean([item["positive_predictive_value"] for item in valid if item["positive_predictive_value"] is not None])) if valid else None,
    }


def verify_sha256_manifest(data_dir: Path) -> dict[str, Any]:
    manifest = data_dir / "SHA256SUMS.txt"
    if not manifest.is_file():
        return {"state": "unverified", "reason": "SHA256SUMS.txt missing", "expected": 0, "checked": 0, "missing": [], "mismatches": []}
    expected: list[tuple[str, str]] = []
    invalid_paths: list[str] = []
    root = data_dir.resolve()
    for line in manifest.read_text(encoding="utf-8").splitlines():
        parts = line.strip().split(maxsplit=1)
        if len(parts) != 2:
            continue
        digest, relative = parts
        relative = relative.lstrip("* ").replace("\\", "/")
        path = (data_dir / relative).resolve()
        if len(digest) != 64 or any(character not in "0123456789abcdefABCDEF" for character in digest) or not path.is_relative_to(root):
            invalid_paths.append(relative)
            continue
        expected.append((digest.lower(), relative))
    missing: list[str] = []
    mismatches: list[str] = []
    checked = 0
    for digest, relative in expected:
        path = data_dir / relative
        if not path.is_file():
            missing.append(relative)
            continue
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        checked += 1
        if actual != digest:
            mismatches.append(relative)
    state = "verified" if expected and checked == len(expected) and not missing and not mismatches and not invalid_paths else "incomplete_or_mismatch"
    return {
        "state": state,
        "expected": len(expected),
        "checked": checked,
        "missing": missing,
        "mismatches": mismatches,
        "invalid_paths": invalid_paths,
    }


def run(data_dir: Path, detector: str = "xqrs") -> dict[str, Any]:
    headers = sorted(data_dir.rglob("*.hea"))
    records: list[dict[str, Any]] = []
    errors: list[dict[str, str]] = []
    for header in headers:
        record_path = header.with_suffix("")
        if not record_path.with_suffix(".dat").exists() or not record_path.with_suffix(".atr").exists():
            continue
        try:
            records.append(evaluate_record(record_path, detector))
        except Exception as error:
            errors.append({"record": record_path.name, "error": str(error)})
    return {
        "schema_version": 1,
        "purpose": "QRS peak detector benchmark against reference beat annotations; not rhythm classification, calibration, Xiaomi validation, or diagnosis",
        "dataset_path": data_dir.name,
        "data_integrity": verify_sha256_manifest(data_dir),
        "provenance": {
            "benchmark_source_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            "python_version": platform.python_version(),
            "wfdb_version": importlib.metadata.version("wfdb"),
            "numpy_version": np.__version__,
        },
        "detector": detector,
        "tolerance_ms": TOLERANCE_MS,
        "summary": summarize(records),
        "records": records,
        "errors": errors,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--detector", choices=("xqrs", "gqrs"), default="xqrs")
    args = parser.parse_args()
    report = run(args.data_dir, args.detector)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report["summary"], indent=2))


if __name__ == "__main__":
    main()
