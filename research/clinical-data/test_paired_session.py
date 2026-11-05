import json
import tempfile
import unittest
from pathlib import Path

from validate_paired_session import load_metadata, validate


def metadata():
    return {
        "schema_version": 2,
        "governance": {"protocol_id": "draft-1", "ethics_status": "approved", "approval_code": "IRB-TEST", "consent_status": "obtained", "consent_at": "2026-09-28T08:00:00Z"},
        "participant_code": "P-ABC123", "session_id": "8a1ef857-57b3-46dd-9f7c-d3e02a7e1544",
        "device": {"watch_model": "Xiaomi Watch 2 Pro", "watch_firmware": "documented", "watch_os": "documented", "phone_os": "documented", "phone_build_sha256": "a" * 64, "wear_build_sha256": "b" * 64},
        "measurement": {"channel": "HEART_RATE_API", "source": "HEALTH_SERVICES", "permission": "granted", "unit": "bpm", "mapping_status": "documented", "comparison_eligible": True, "sample_count": 20, "sample_interval_ms": 1000, "jitter_ms": 10, "start_at": "2026-09-28T08:01:00Z", "end_at": "2026-09-28T08:02:00Z", "time_basis": "wall_clock_synchronized"},
        "reference": {"reference_code": "REF-ABC123", "modality": "ECG reference", "model": "documented", "unit": "bpm", "calibration_status": "valid", "calibration_valid_from": "2026-01-01T00:00:00Z", "calibration_valid_through": "2026-12-31T23:59:59Z"},
        "synchronization": {"method": "predeclared marker", "start_offset_ms": 20, "end_offset_ms": -25, "max_allowed_error_ms": 100},
        "condition": {"activity": "REST", "watch_worn": True, "charging": False},
        "artifact": {"artifact_id": "a1ec6f23-ea5c-48f2-a81b-06baf25494c1", "sha256": "c" * 64, "encrypted_at_rest": True},
    }


class PairedMetadataPreflightTests(unittest.TestCase):
    def test_metadata_loader_rejects_duplicate_keys_at_any_depth(self):
        cases = (
            '{"schema_version":2,"schema_version":2}',
            '{"nested":{"consent_status":"obtained","consent_status":"missing"}}',
        )
        for content in cases:
            with self.subTest(content=content), tempfile.TemporaryDirectory() as directory:
                metadata_file = Path(directory) / "metadata.json"
                metadata_file.write_text(content, encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "duplicate JSON field"):
                    load_metadata(metadata_file)

    def test_metadata_loader_rejects_oversized_input(self):
        with tempfile.TemporaryDirectory() as directory:
            metadata_file = Path(directory) / "oversized.json"
            metadata_file.write_bytes(b" " * (128 * 1024 + 1))
            with self.assertRaisesRegex(ValueError, "128 KiB"):
                load_metadata(metadata_file)

    def test_schema_requires_calibration_effective_interval(self):
        schema = json.loads(Path(__file__).with_name("paired_session.schema.json").read_text(encoding="utf-8"))

        self.assertEqual(schema["properties"]["schema_version"]["const"], 2)
        required = schema["properties"]["reference"]["required"]
        self.assertIn("calibration_valid_from", required)
        self.assertIn("calibration_valid_through", required)

    def test_approved_metadata_passes(self):
        self.assertEqual(validate(metadata()), [])

    def test_rejects_missing_ethics_or_consent(self):
        for field, value in (("ethics_status", "pending"), ("consent_status", "not_obtained")):
            record = metadata()
            record["governance"][field] = value
            self.assertTrue(validate(record), field)

    def test_rejects_identity_fields_and_raw_payloads(self):
        record = metadata()
        record["participant_name"] = "not permitted"
        self.assertTrue(validate(record))

    def test_rejects_uncalibrated_or_undocumented_comparison(self):
        for section, field, value in (("reference", "calibration_status", "unknown"), ("measurement", "mapping_status", "unknown")):
            record = metadata()
            record[section][field] = value
            self.assertTrue(validate(record), field)

    def test_rejects_sync_outside_predeclared_limit(self):
        record = metadata()
        record["synchronization"]["end_offset_ms"] = 101
        self.assertTrue(validate(record))

    def test_rejects_unencrypted_artifact(self):
        record = metadata()
        record["artifact"]["encrypted_at_rest"] = False
        self.assertTrue(validate(record))

    def test_rejects_nested_unregistered_fields(self):
        record = metadata()
        record["measurement"]["raw_samples"] = [1, 2, 3]
        self.assertTrue(validate(record))

    def test_reference_calibration_must_cover_entire_session(self):
        record = metadata()
        record["reference"]["calibration_valid_through"] = "2026-09-28T08:01:30Z"
        self.assertTrue(validate(record))

    def test_reference_calibration_must_be_effective_before_session_start(self):
        record = metadata()
        record["reference"]["calibration_valid_from"] = "2026-09-28T08:01:30Z"

        self.assertTrue(validate(record))

        record["reference"]["calibration_valid_from"] = "2026-09-28T08:01:00Z"
        self.assertEqual(validate(record), [])

    def test_comparison_requires_matching_units_and_mapped_time(self):
        record = metadata()
        record["reference"]["unit"] = "mV"
        self.assertTrue(validate(record))

    def test_declared_channel_and_unit_must_be_semantically_compatible(self):
        record = metadata()
        record["measurement"]["unit"] = "mV"
        self.assertTrue(validate(record))

        record = metadata()
        record["measurement"].update(channel="RAW_PPG", unit="raw_adc")
        self.assertTrue(validate(record))

        record = metadata()
        record["measurement"].update(channel="RAW_PPG", unit="raw_adc", mapping_status="raw_only", comparison_eligible=False)
        self.assertEqual(validate(record), [])

    def test_raw_ppg_cannot_be_declared_a_direct_physiological_comparison(self):
        record = metadata()
        record["measurement"].update(channel="RAW_PPG", unit="raw_adc")

        errors = validate(record)

        self.assertTrue(any("not directly comparable" in error for error in errors))
        record = metadata()
        record["measurement"]["time_basis"] = "relative_unverified"
        self.assertTrue(validate(record))

    def test_raw_only_metadata_can_be_logged_without_claiming_comparison(self):
        record = metadata()
        record["measurement"].update(channel="RAW_PPG", mapping_status="raw_only", comparison_eligible=False, unit="raw_adc")
        record["reference"]["calibration_status"] = "unknown"
        self.assertEqual(validate(record), [])

    def test_malformed_timestamp_types_are_rejected_without_crashing(self):
        for location in (("measurement", "start_at"), ("governance", "consent_at")):
            record = metadata()
            record[location[0]][location[1]] = 42
            self.assertTrue(validate(record), location)

    def test_consent_must_precede_measurement_start(self):
        record = metadata()
        record["governance"]["consent_at"] = "2026-09-28T08:01:01Z"

        self.assertTrue(validate(record))

    def test_nonfinite_or_negative_sampling_metadata_is_rejected(self):
        for field, value in (("sample_interval_ms", float("nan")), ("sample_interval_ms", -1), ("jitter_ms", float("inf")), ("jitter_ms", -0.1)):
            record = metadata()
            record["measurement"][field] = value
            self.assertTrue(validate(record), field)

    def test_non_text_identifiers_and_artifact_ids_are_rejected(self):
        record = metadata()
        record["participant_code"] = ["P-ABC123"]
        self.assertTrue(validate(record))
        record = metadata()
        record["artifact"]["artifact_id"] = {"unexpected": "object"}
        self.assertTrue(validate(record))

    def test_invalid_session_id_is_rejected(self):
        record = metadata()
        record["session_id"] = "not-a-uuid"

        self.assertTrue(validate(record))

    def test_comparison_rejects_missing_or_unknown_time_basis(self):
        for value in (None, "unmapped"):
            record = metadata()
            record["measurement"]["time_basis"] = value
            self.assertTrue(validate(record))

    def test_comparison_eligibility_must_be_a_boolean(self):
        for value in ("false", 0, None):
            record = metadata()
            record["measurement"]["comparison_eligible"] = value
            self.assertTrue(validate(record))

    def test_rfc3339_timestamps_require_seconds_and_offset(self):
        for value in ("2026-09-28T08:01Z", "2026-09-28T08:01:00"):
            record = metadata()
            record["measurement"]["start_at"] = value
            self.assertTrue(validate(record))

    def test_non_comparison_metadata_still_obeys_schema_enums(self):
        record = metadata()
        record["measurement"].update(comparison_eligible=False, mapping_status="raw_only", unit="raw_adc")
        record["reference"]["calibration_status"] = ["not-reviewed"]

        self.assertTrue(validate(record))


if __name__ == "__main__":
    unittest.main()
