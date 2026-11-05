import argparse
import hashlib
import json
import math
import statistics
import struct
from pathlib import Path


def describe(values):
    span = max(values) - min(values)
    changes = sum(abs(right - left) > span * 0.001
                  for left, right in zip(values, values[1:]))
    return {
        "sample_count": len(values),
        "finite_ratio": sum(math.isfinite(value) for value in values) / len(values),
        "mean": statistics.mean(values),
        "standard_deviation": statistics.pstdev(values),
        "amplitude_range": span,
        "variation_coverage": changes / max(1, len(values) - 1),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    header = (args.directory / "bidmc01.hea").read_text().splitlines()
    record, channels, rate, frames = header[0].split()
    channels, frames = int(channels), int(frames)
    rate = float(rate)
    payload = (args.directory / "bidmc01.dat").read_bytes()
    if len(payload) != channels * frames * 2:
        raise ValueError("Incomplete WFDB payload")
    raw = struct.unpack(f"<{channels * frames}h", payload)
    results = {}
    for channel in range(channels):
        fields = header[channel + 1].split()
        if fields[1] != "16":
            raise ValueError("Only WFDB format 16 supported")
        scale, unit = fields[2].split("/")
        gain, baseline = scale.split("(")
        gain, baseline = float(gain), int(baseline.rstrip(")"))
        values = [(value - baseline) / gain for value in raw[channel::channels]]
        results[fields[-1].rstrip(",")] = {**describe(values), "unit": unit}
    flat = describe([12000.0] * 128)
    spike = describe([12000.0] * 64 + [12024.0] + [12000.0] * 63)
    assert flat["variation_coverage"] == 0
    assert spike["variation_coverage"] < 0.02
    report = {
        "method_version": "bidmc-format16-integrity-v1",
        "source": "https://physionet.org/content/bidmc/1.0.0/",
        "record": record,
        "sample_rate_hz": rate,
        "duration_seconds": (frames - 1) / rate,
        "payload_sha256": hashlib.sha256(payload).hexdigest(),
        "channels": results,
        "flatline_control": flat,
        "isolated_spike_control": spike,
        "scope": "Reference parsing and descriptive statistics only; no diagnosis, watch calibration or clinical quality score",
    }
    args.output.write_text(json.dumps(report, indent=2, allow_nan=False) + "\n")
    print(f"Validated {frames} frames, {channels} channels; controls passed")


if __name__ == "__main__":
    main()
