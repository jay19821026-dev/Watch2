import tempfile
import unittest
from pathlib import Path

import numpy as np

from benchmark_wrist_ppg import (
    alignment_assessment,
    benchmark_provenance,
    elapsed_seconds_from_wrapped_clock,
    participant_id,
    ppg_peak_indices,
    ppg_quality_screen,
    ppg_spectral_bpm,
    resolve_dataset_path,
    summarize_by_participant,
    verify_checksums,
    window_has_sampling_integrity,
)


class PpgBaselineTests(unittest.TestCase):
    def test_report_provenance_has_source_registry_and_dependency_hashes(self):
        provenance = benchmark_provenance()

        self.assertRegex(provenance["benchmark_source_sha256"], r"^[a-f0-9]{64}$")
        self.assertRegex(provenance["registry_sha256"], r"^[a-f0-9]{64}$")
        self.assertRegex(provenance["dependency_lock_sha256"], r"^[a-f0-9]{64}$")
        self.assertTrue(provenance["python_version"])
        self.assertEqual(set(provenance["dependency_versions"]), {"numpy", "scipy", "wfdb"})

    def test_report_declares_unverified_relative_zero_alignment(self):
        assessment = alignment_assessment()

        self.assertEqual(assessment["status"], "relative_zero_assumed_unverified")
        self.assertFalse(assessment["offset_correction_applied"])
        self.assertEqual(assessment["maximum_documented_device_duration_difference_seconds"], 1.21)

    def test_participant_macro_summary_is_equal_weighted(self):
        records = [
            {"record": "s1_walk", "state": "processed", "windows": [
                {"reference_accepted": True, "peak_count_accepted": True, "estimate": 80.0, "reference_bpm": 70.0},
            ]},
            {"record": "s1_run", "state": "processed", "windows": [
                {"reference_accepted": True, "peak_count_accepted": True, "estimate": 80.0, "reference_bpm": 70.0},
            ]},
            {"record": "s2_walk", "state": "processed", "windows": [
                {"reference_accepted": True, "peak_count_accepted": True, "estimate": 100.0, "reference_bpm": 70.0},
            ]},
        ]

        result = summarize_by_participant(records, "estimate", "peak_count_accepted")

        self.assertEqual(participant_id("s1_walk"), "s1")
        self.assertEqual(result["participants_with_estimates"], 2)
        self.assertEqual(result["macro_mae_bpm"], 20.0)
        self.assertEqual(result["participants"]["s1"]["mae_bpm"], 10.0)
        interval = result["macro_mae_ci95_bpm"]
        self.assertLessEqual(interval["lower"], result["macro_mae_bpm"])
        self.assertGreaterEqual(interval["upper"], result["macro_mae_bpm"])
        self.assertEqual(result["confidence_interval_method"]["resampling_unit"], "participant")

    def test_participant_bootstrap_is_deterministic_and_withheld_for_one_participant(self):
        from benchmark_wrist_ppg import participant_bootstrap_ci

        values = [10.0, 20.0, 30.0]
        self.assertEqual(participant_bootstrap_ci(values), participant_bootstrap_ci(values))
        self.assertIsNone(participant_bootstrap_ci([10.0]))

    def test_peak_detector_finds_synthetic_75_bpm_signal(self):
        sample_rate = 64.0
        duration = 60.0
        time = np.arange(int(sample_rate * duration)) / sample_rate
        signal = np.sin(2 * np.pi * 1.25 * time)

        peaks = ppg_peak_indices(signal, sample_rate)

        self.assertGreaterEqual(len(peaks), 70)
        self.assertLessEqual(len(peaks), 80)
        self.assertAlmostEqual(ppg_spectral_bpm(signal, sample_rate), 75.0, delta=2.5)

    def test_peak_detector_rejects_nonfinite_or_short_input(self):
        self.assertEqual(len(ppg_peak_indices(np.ones(100), 25.0)), 0)
        invalid = np.full(1000, np.nan)
        self.assertEqual(len(ppg_peak_indices(invalid, 25.0)), 0)

    def test_quality_screen_accepts_clean_periodic_waveform_as_research_only(self):
        sample_rate = 64.0
        time = np.arange(int(sample_rate * 10)) / sample_rate
        signal = np.sin(2 * np.pi * 1.25 * time)

        result = ppg_quality_screen(signal, sample_rate)

        self.assertTrue(result["accepted"])
        self.assertGreaterEqual(result["mean_template_correlation"], 0.86)

    def test_quality_screen_rejects_flat_nonfinite_and_short_signals(self):
        flat = ppg_quality_screen(np.ones(640), 64.0)
        short = ppg_quality_screen(np.ones(100), 64.0)
        nonfinite = ppg_quality_screen(np.full(640, np.nan), 64.0)

        self.assertEqual(flat["reason"], "too_few_detected_pulses")
        self.assertEqual(short["reason"], "short_window")
        self.assertEqual(nonfinite["reason"], "nonfinite_samples")

    def test_sample_clock_rollover_is_unwrapped_without_time_shift(self):
        wrapped = np.array([59.98, 59.99, 0.00, 0.01, 0.02])

        elapsed = elapsed_seconds_from_wrapped_clock(wrapped)

        np.testing.assert_allclose(elapsed, [0.0, 0.01, 0.02, 0.03, 0.04], atol=1e-8)

    def test_sample_clock_rejects_nonmonotonic_corruption(self):
        corrupted = np.array([10.0, 10.01, 9.7, 9.71])
        self.assertEqual(len(elapsed_seconds_from_wrapped_clock(corrupted)), 0)

    def test_sampling_integrity_rejects_windows_crossing_large_gaps(self):
        sample_rate = 256.0
        period = 1.0 / sample_rate
        regular = np.array([0.0, period, period * 2, period * 3])
        with_gap = np.array([0.0, period, period * 4, period * 5])

        self.assertTrue(window_has_sampling_integrity(regular, sample_rate, 0.0, period * 3))
        self.assertFalse(window_has_sampling_integrity(with_gap, sample_rate, 0.0, period * 5))
        self.assertTrue(window_has_sampling_integrity(with_gap, sample_rate, period * 4, period * 6))
        self.assertFalse(window_has_sampling_integrity(with_gap, sample_rate, 1.0, 0.0))

    def test_checksum_verifier_reports_match_and_mismatch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            data = root / "record.dat"
            data.write_bytes(b"research fixture")
            import hashlib

            digest = hashlib.sha256(data.read_bytes()).hexdigest()
            manifest = root / "SHA256SUMS.txt"
            manifest.write_text(f"{digest}  record.dat\n", encoding="utf-8")
            self.assertEqual(verify_checksums(root)["state"], "verified")
            data.write_bytes(b"changed")
            self.assertEqual(verify_checksums(root)["state"], "failed")

    def test_checksum_verifier_rejects_malformed_manifest_entries(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            data = root / "record.dat"
            data.write_bytes(b"research fixture")
            import hashlib

            digest = hashlib.sha256(data.read_bytes()).hexdigest()
            (root / "SHA256SUMS.txt").write_text(
                f"{digest}  record.dat\nnot-a-valid-checksum-line\n", encoding="utf-8"
            )

            result = verify_checksums(root)

            self.assertEqual(result["state"], "failed")
            self.assertEqual(result["files_checked"], 1)
            self.assertEqual(result["malformed_manifest_lines"], [2])

    def test_checksum_verifier_rejects_duplicate_resolved_paths(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            data = root / "record.dat"
            data.write_bytes(b"research fixture")
            import hashlib

            digest = hashlib.sha256(data.read_bytes()).hexdigest()
            (root / "SHA256SUMS.txt").write_text(
                f"{digest}  record.dat\n{digest}  ./record.dat\n", encoding="utf-8"
            )

            result = verify_checksums(root)

            self.assertEqual(result["state"], "failed")
            self.assertEqual(result["files_checked"], 1)
            self.assertEqual(result["duplicate_manifest_paths"], ["./record.dat"])

    def test_checksum_verifier_rejects_missing_manifest_member(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = root / "SHA256SUMS.txt"
            manifest.write_text(f"{'0' * 64}  absent.dat\n", encoding="utf-8")
            self.assertEqual(verify_checksums(root)["state"], "incomplete")

    def test_checksum_verifier_rejects_paths_outside_dataset(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "dataset"
            root.mkdir()
            outside = Path(temp) / "outside.dat"
            outside.write_bytes(b"must not be accepted")
            import hashlib

            digest = hashlib.sha256(outside.read_bytes()).hexdigest()
            (root / "SHA256SUMS.txt").write_text(f"{digest}  ../outside.dat\n", encoding="utf-8")

            result = verify_checksums(root)

            self.assertEqual(result["state"], "failed")
            self.assertEqual(result["invalid_paths"], ["../outside.dat"])

    def test_record_manifest_paths_are_confined_to_dataset_root(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "dataset"
            root.mkdir()
            with self.assertRaises(ValueError):
                resolve_dataset_path(root, "../outside.hea")


if __name__ == "__main__":
    unittest.main()
