import importlib.util
import hashlib
import tempfile
import unittest
from pathlib import Path

import numpy as np

SCRIPT = Path(__file__).with_name("benchmark_ecg_qrs.py")
SPEC = importlib.util.spec_from_file_location("benchmark_ecg_qrs", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class EcgQrsBenchmarkTest(unittest.TestCase):
    def test_one_to_one_matching_counts_extra_and_missing_peaks(self):
        result = MODULE.match_peaks(np.array([100, 200, 300]), np.array([101, 105, 301]), 2)
        self.assertEqual((2, 1, 1), (result["true_positive"], result["false_positive"], result["false_negative"]))

    def test_empty_reference_does_not_invent_sensitivity(self):
        result = MODULE.match_peaks(np.array([], dtype=int), np.array([10]), 2)
        self.assertIsNone(result["sensitivity"])
        self.assertEqual(1, result["false_positive"])

    def test_summary_reports_pooled_and_record_macro_metrics_separately(self):
        result = MODULE.summarize([
            {"reference_beats": 10, "detected_peaks": 10, "true_positive": 9, "sensitivity": 0.9, "positive_predictive_value": 0.9},
            {"reference_beats": 100, "detected_peaks": 120, "true_positive": 60, "sensitivity": 0.6, "positive_predictive_value": 0.5},
        ])
        self.assertAlmostEqual(69 / 110, result["pooled_sensitivity"])
        self.assertAlmostEqual(0.75, result["macro_record_sensitivity"])

    def test_unannotated_symbol_is_not_counted_as_a_beat(self):
        self.assertNotIn("+", MODULE.BEAT_SYMBOLS)
        self.assertIn("N", MODULE.BEAT_SYMBOLS)

    def test_checksum_manifest_reports_verified_and_mismatched_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            payload = b"fixture ECG evidence"
            (root / "fixture.dat").write_bytes(payload)
            digest = hashlib.sha256(payload).hexdigest()
            (root / "SHA256SUMS.txt").write_text(f"{digest} fixture.dat\n", encoding="utf-8")
            self.assertEqual("verified", MODULE.verify_sha256_manifest(root)["state"])
            (root / "fixture.dat").write_bytes(b"modified")
            self.assertEqual("incomplete_or_mismatch", MODULE.verify_sha256_manifest(root)["state"])


if __name__ == "__main__":
    unittest.main()
