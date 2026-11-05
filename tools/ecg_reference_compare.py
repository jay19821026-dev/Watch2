import json
import sys
from pathlib import Path

import numpy as np


def load_csv(path: Path) -> np.ndarray:
    return np.loadtxt(path, delimiter=",", dtype=float)


def load_wfdb_16(path: Path, channels: int = 2) -> list[np.ndarray]:
    raw = np.fromfile(path, dtype="<i2")
    matrix = raw.reshape((-1, channels))
    return [matrix[:, index].astype(float) for index in range(channels)]


def band_power(frequencies: np.ndarray, psd: np.ndarray, low: float, high: float) -> float:
    mask = (frequencies >= low) & (frequencies < high)
    return float(np.trapezoid(psd[mask], frequencies[mask])) if np.any(mask) else 0.0


def biquad(values: np.ndarray, sample_rate: float, kind: str, frequency: float, q: float) -> np.ndarray:
    omega = 2.0 * np.pi * frequency / sample_rate
    cosine = np.cos(omega)
    sine = np.sin(omega)
    alpha = sine / (2.0 * q)
    if kind == "notch":
        b0, b1, b2 = 1.0, -2.0 * cosine, 1.0
        a0, a1, a2 = 1.0 + alpha, -2.0 * cosine, 1.0 - alpha
    elif kind == "lowpass":
        b0, b1, b2 = (1.0 - cosine) / 2.0, 1.0 - cosine, (1.0 - cosine) / 2.0
        a0, a1, a2 = 1.0 + alpha, -2.0 * cosine, 1.0 - alpha
    elif kind == "highpass":
        b0, b1, b2 = (1.0 + cosine) / 2.0, -(1.0 + cosine), (1.0 + cosine) / 2.0
        a0, a1, a2 = 1.0 + alpha, -2.0 * cosine, 1.0 - alpha
    else:
        raise ValueError(kind)
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    output = np.zeros_like(values, dtype=float)
    x1 = x2 = y1 = y2 = 0.0
    for index, x0 in enumerate(values):
        y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        output[index] = y0
        x2, x1, y2, y1 = x1, x0, y1, y0
    return output


def research_filter(values: np.ndarray, sample_rate: float) -> np.ndarray:
    filtered = biquad(values, sample_rate, "notch", 60.0, 30.0)
    filtered = biquad(filtered, sample_rate, "highpass", 0.5, 1.0 / np.sqrt(2.0))
    return biquad(filtered, sample_rate, "lowpass", 40.0, 1.0 / np.sqrt(2.0))


def metrics(values: np.ndarray, sample_rate: float) -> dict:
    finite = values[np.isfinite(values)]
    indexes = np.arange(len(finite), dtype=float)
    slope, intercept = np.polyfit(indexes, finite, 1)
    centered = finite - (slope * indexes + intercept)
    scale = np.median(np.abs(centered - np.median(centered))) * 1.4826
    normalized = centered / max(scale, 1e-9)
    window = np.hanning(len(normalized))
    spectrum = np.fft.rfft(normalized * window)
    frequencies = np.fft.rfftfreq(len(normalized), d=1.0 / sample_rate)
    psd = (np.abs(spectrum) ** 2) / max(np.sum(window ** 2) * sample_rate, 1e-12)
    total = band_power(frequencies, psd, 0.05, sample_rate / 2)
    fft_size = 1 << (2 * len(normalized) - 1).bit_length()
    transformed = np.fft.rfft(normalized, fft_size)
    autocorrelation = np.fft.irfft(transformed * np.conjugate(transformed), fft_size)[:len(normalized)]
    autocorrelation /= max(autocorrelation[0], 1e-9)
    low_lag = max(1, round(sample_rate * 0.3))
    high_lag = min(len(autocorrelation), round(sample_rate * 2.0))
    plausible = autocorrelation[low_lag:high_lag]
    peak_index = int(np.argmax(plausible)) + low_lag if len(plausible) else 0
    return {
        "points": int(len(finite)),
        "duration_seconds": round(len(finite) / sample_rate, 3),
        "sample_rate_hz": sample_rate,
        "amplitude_range_raw_units": round(float(np.ptp(finite)), 6),
        "robust_scale_raw_units": round(float(scale), 6),
        "baseline_power_ratio_below_0_5_hz": round(band_power(frequencies, psd, 0.05, 0.5) / max(total, 1e-12), 6),
        "ecg_band_power_ratio_0_5_40_hz": round(band_power(frequencies, psd, 0.5, 40.0) / max(total, 1e-12), 6),
        "mains_50hz_power_ratio_49_51_hz": round(band_power(frequencies, psd, 49.0, 51.0) / max(total, 1e-12), 6),
        "mains_60hz_power_ratio_59_61_hz": round(band_power(frequencies, psd, 59.0, 61.0) / max(total, 1e-12), 6),
        "high_frequency_power_ratio_over_40_hz": round(band_power(frequencies, psd, 40.0, sample_rate / 2) / max(total, 1e-12), 6),
        "normalized_derivative_rms": round(float(np.sqrt(np.mean(np.diff(normalized) ** 2))), 6),
        "kurtosis": round(float(np.mean((normalized - np.mean(normalized)) ** 4) / max(np.var(normalized) ** 2, 1e-12)), 6),
        "periodicity_peak": round(float(autocorrelation[peak_index]), 6) if peak_index else None,
        "periodicity_lag_seconds": round(peak_index / sample_rate, 4) if peak_index else None,
    }


def main() -> None:
    if len(sys.argv) != 5:
        raise SystemExit(
            "usage: ecg_reference_compare.py <watch_samples.csv> <reference_record.dat> "
            "<output.json> <watch_sample_rate_hz_from_external_evidence>\n"
            "Do not run this comparison while the Watch sample clock is unknown."
        )
    watch_csv, reference_dat, output_json = map(Path, sys.argv[1:4])
    try:
        watch_sample_rate = float(sys.argv[4])
    except ValueError as error:
        raise SystemExit("watch sample rate must be numeric") from error
    if not np.isfinite(watch_sample_rate) or watch_sample_rate <= 0:
        raise SystemExit("watch sample rate must be finite and positive")
    watch = load_csv(watch_csv)
    if watch.ndim != 1 or watch.size < 2 or not np.isfinite(watch).all():
        raise SystemExit("watch CSV must contain one finite, non-empty sample column")
    if not reference_dat.exists():
        raise SystemExit(f"reference ECG file not found: {reference_dat}")
    reference_raw, reference_filtered = load_wfdb_16(reference_dat)
    reference_header = reference_dat.with_suffix(".hea")
    if not reference_header.exists():
        raise SystemExit(f"reference WFDB header not found: {reference_header}")
    output = {
        "method": "descriptive signal comparison; not voltage calibration, beat validation, or diagnosis",
        "watch_timebase": {
            "sample_rate_hz": watch_sample_rate,
            "source": "caller-supplied; evidence not validated by this tool",
            "timebase_verification": "NOT_PERFORMED",
        },
        "watch": metrics(watch, watch_sample_rate),
        "watch_research_filtered_60hz_notch_0_5_40hz": metrics(research_filter(watch, watch_sample_rate), watch_sample_rate),
        "ecg_id_lead_i_raw": metrics(reference_raw, 500.0),
        "ecg_id_lead_i_filtered": metrics(reference_filtered, 500.0),
        "limitations": [
            "This tool does not verify the supplied Watch sample clock; caller must provide independent device evidence.",
            "Amplitude units, lead mapping, polarity, INA/PGA gain, and ADC scale remain unknown.",
            "Different subjects and recording setups cannot be compared point-by-point.",
        ],
    }
    output_json.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
