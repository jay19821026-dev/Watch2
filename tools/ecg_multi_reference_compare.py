import json
import sys
from pathlib import Path

import numpy as np

from ecg_reference_compare import metrics, research_filter


def load_wfdb_212(path: Path, frames: int = 20_000) -> tuple[np.ndarray, np.ndarray]:
    raw = np.fromfile(path, dtype=np.uint8, count=frames * 3).reshape((-1, 3))
    first = raw[:, 0].astype(np.int16) | ((raw[:, 1].astype(np.int16) & 0x0F) << 8)
    second = raw[:, 2].astype(np.int16) | ((raw[:, 1].astype(np.int16) & 0xF0) << 4)
    first = np.where(first >= 2048, first - 4096, first)
    second = np.where(second >= 2048, second - 4096, second)
    return first.astype(float), second.astype(float)


def load_interleaved_16(path: Path, channels: int, frames: int | None = None) -> list[np.ndarray]:
    count = -1 if frames is None else frames * channels
    raw = np.fromfile(path, dtype="<i2", count=count).reshape((-1, channels))
    return [raw[:, index].astype(float) for index in range(channels)]


def noise_distance(watch: dict, reference: dict) -> float:
    keys = [
        "baseline_power_ratio_below_0_5_hz",
        "ecg_band_power_ratio_0_5_40_hz",
        "mains_60hz_power_ratio_59_61_hz",
        "high_frequency_power_ratio_over_40_hz",
        "normalized_derivative_rms",
    ]
    return float(np.sqrt(sum((watch[key] - reference[key]) ** 2 for key in keys)))


def main() -> None:
    if len(sys.argv) != 5:
        raise SystemExit(
            "usage: ecg_multi_reference_compare.py <reference_root> <watch_samples.csv> "
            "<output.json> <watch_sample_rate_hz_from_external_evidence>\n"
            "Do not run this comparison while the Watch sample clock is unknown."
        )
    root = Path(sys.argv[1])
    watch = np.loadtxt(sys.argv[2], delimiter=",", dtype=float)
    output_path = Path(sys.argv[3])
    try:
        watch_sample_rate = float(sys.argv[4])
    except ValueError as error:
        raise SystemExit("watch sample rate must be numeric") from error
    if not np.isfinite(watch_sample_rate) or watch_sample_rate <= 0:
        raise SystemExit("watch sample rate must be finite and positive")
    if watch.ndim != 1 or watch.size < 2 or not np.isfinite(watch).all():
        raise SystemExit("watch CSV must contain one finite, non-empty sample column")
    watch_metrics = metrics(watch, watch_sample_rate)
    references = {}

    for name in ("bw", "em", "ma"):
        channel, _ = load_wfdb_212(root / "nstdb" / f"{name}.dat")
        references[f"nstdb_{name}"] = metrics(channel, 360.0)

    ptb_leads = load_interleaved_16(root / "ptb-xl" / "00001_hr.dat", 12)
    references["ptb_xl_normal_lead_i"] = metrics(ptb_leads[0], 500.0)

    ecg_id = load_interleaved_16(root / "ecg-id" / "Person_01" / "rec_1.dat", 2)
    references["ecg_id_lead_i_raw"] = metrics(ecg_id[0], 500.0)
    references["ecg_id_lead_i_filtered"] = metrics(ecg_id[1], 500.0)

    distances = {
        name: round(noise_distance(watch_metrics, value), 6)
        for name, value in references.items()
        if name.startswith("nstdb_")
    }
    output = {
        "method": "scale-independent spectral/noise-feature comparison; no beat matching, voltage calibration, or diagnosis",
        "watch_timebase": {
            "sample_rate_hz": watch_sample_rate,
            "source": "caller-supplied; evidence not validated by this script",
            "timebase_verification": "NOT_PERFORMED",
            "verified_oem_adc_clock": False,
        },
        "watch_raw": watch_metrics,
        "watch_research_filtered": metrics(research_filter(watch, watch_sample_rate), watch_sample_rate),
        "references": references,
        "noise_template_distance_lower_is_closer": distances,
        "closest_noise_template": min(distances, key=distances.get),
        "limitations": [
            "Noise-template distance is descriptive and not a validated classifier.",
            "Databases use different leads, subjects, devices, durations, and gains.",
            "This tool does not verify the supplied watch timebase; the caller must retain supporting device evidence.",
        ],
    }
    output_path.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
