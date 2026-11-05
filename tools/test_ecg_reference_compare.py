import importlib.util
import subprocess
import sys
import unittest
from pathlib import Path

import numpy as np

TOOLS = Path(__file__).resolve().parent
SCRIPT = TOOLS / "ecg_reference_compare.py"
SPEC = importlib.util.spec_from_file_location("ecg_reference_compare", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class EcgReferenceCompareTest(unittest.TestCase):
    def test_cli_refuses_implicit_watch_sample_rate(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "watch.csv", "reference.dat", "out.json"],
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("watch_sample_rate_hz_from_external_evidence", result.stderr)

    def test_cli_refuses_non_positive_watch_sample_rate(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "watch.csv", "reference.dat", "out.json", "0"],
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("finite and positive", result.stderr)

    def test_metrics_preserve_explicit_sample_rate_and_shape(self):
        rate = 360.0
        time = np.arange(0, 10, 1 / rate)
        signal = np.sin(2 * np.pi * 1.2 * time) + 0.1 * np.sin(2 * np.pi * 17 * time)
        result = MODULE.metrics(signal, rate)
        self.assertEqual(rate, result["sample_rate_hz"])
        self.assertEqual(len(signal), result["points"])
        self.assertAlmostEqual(10.0, result["duration_seconds"], places=2)


if __name__ == "__main__":
    unittest.main()
