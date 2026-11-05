"""Preflight validator for paired-study metadata; never accepts waveform payloads."""

import json
import math
import re
import sys
from datetime import datetime
from pathlib import Path


HASH = re.compile(r"^[a-f0-9]{64}$")
PARTICIPANT = re.compile(r"^P-[A-Z0-9]{6,12}$")
REFERENCE = re.compile(r"^REF-[A-Z0-9]{6,12}$")
UUID = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
RFC3339 = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$")
MAX_METADATA_BYTES = 128 * 1024


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON field: {key}")
        result[key] = value
    return result


def load_metadata(path):
    with Path(path).open("rb") as metadata_file:
        content = metadata_file.read(MAX_METADATA_BYTES + 1)
    if len(content) > MAX_METADATA_BYTES:
        raise ValueError("metadata exceeds 128 KiB limit")
    return json.loads(content.decode("utf-8"), object_pairs_hook=_unique_object)


def parse_time(value):
    if not isinstance(value, str) or not RFC3339.fullmatch(value):
        raise ValueError("timestamp must be RFC 3339 date-time")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("timestamp must include a timezone")
    return parsed


def validate(record):
    errors = []

    def require(condition, message):
        if not condition:
            errors.append(message)

    def is_text(value):
        return isinstance(value, str) and bool(value.strip())

    def matches(pattern, value):
        return isinstance(value, str) and bool(pattern.fullmatch(value))

    def is_choice(value, choices):
        return isinstance(value, str) and value in choices

    require(isinstance(record, dict), "root must be an object")
    if not isinstance(record, dict):
        return ["root must be an object"]
    expected = {"schema_version", "governance", "participant_code", "session_id", "device", "measurement", "reference", "synchronization", "condition", "artifact"}
    require(set(record) == expected, "root fields must exactly match metadata contract")
    g, d, m, ref, sync, cond, artifact = (record.get(k, {}) for k in ("governance", "device", "measurement", "reference", "synchronization", "condition", "artifact"))
    section_fields = {
        "governance": {"protocol_id", "ethics_status", "approval_code", "consent_status", "consent_at"},
        "device": {"watch_model", "watch_firmware", "watch_os", "phone_os", "phone_build_sha256", "wear_build_sha256"},
        "measurement": {"channel", "source", "permission", "unit", "mapping_status", "comparison_eligible", "sample_count", "sample_interval_ms", "jitter_ms", "start_at", "end_at", "time_basis"},
        "reference": {"reference_code", "modality", "model", "unit", "calibration_status", "calibration_valid_from", "calibration_valid_through"},
        "synchronization": {"method", "start_offset_ms", "end_offset_ms", "max_allowed_error_ms"},
        "condition": {"activity", "watch_worn", "charging"},
        "artifact": {"artifact_id", "sha256", "encrypted_at_rest"},
    }
    for name, allowed in section_fields.items():
        section = record.get(name)
        require(isinstance(section, dict) and set(section) == allowed, f"{name} fields must exactly match metadata contract")
        if not isinstance(section, dict):
            return errors
    require(type(record.get("schema_version")) is int and record.get("schema_version") == 2, "unsupported schema_version")
    require(matches(PARTICIPANT, record.get("participant_code")), "participant_code must be pseudonymous")
    require(matches(UUID, record.get("session_id")), "session_id must be a UUID")
    require(g.get("ethics_status") == "approved" and is_text(g.get("approval_code")), "ethics approval required")
    require(g.get("consent_status") == "obtained" and is_text(g.get("consent_at")), "documented consent required")
    require(is_text(g.get("protocol_id")), "protocol_id is required")
    require(all(is_text(d.get(field)) for field in ("watch_firmware", "watch_os", "phone_os")), "device versions are required")
    require(d.get("watch_model") == "Xiaomi Watch 2 Pro", "unexpected watch model")
    for field in ("phone_build_sha256", "wear_build_sha256"):
        require(matches(HASH, d.get(field)), f"{field} must be SHA-256")
    require(m.get("permission") == "granted", "measurement permission must be recorded")
    require(type(m.get("sample_count")) is int and m["sample_count"] > 0, "sample_count must be a positive integer")
    require(is_choice(m.get("channel"), {"HEART_RATE_API", "RAW_PPG", "SPO2", "ECG", "BIOZ", "SLEEP_STAGE"}), "unsupported measurement channel")
    require(is_choice(m.get("source"), {"HEALTH_SERVICES", "SENSOR_MANAGER", "HEALTH_CONNECT", "OEM_APP_EXPORT", "OTHER_DOCUMENTED"}), "unsupported measurement source")
    require(is_choice(m.get("unit"), {"bpm", "percent", "mV", "ohm", "raw_adc", "sleep_stage_label", "unknown"}), "unsupported unit")
    channel_units = {
        "HEART_RATE_API": {"bpm"},
        "RAW_PPG": {"raw_adc", "unknown"},
        "SPO2": {"percent", "raw_adc", "unknown"},
        "ECG": {"mV", "raw_adc", "unknown"},
        "BIOZ": {"ohm", "raw_adc", "unknown"},
        "SLEEP_STAGE": {"sleep_stage_label"},
    }
    if m.get("channel") in channel_units:
        require(m.get("unit") in channel_units[m["channel"]], "measurement unit is incompatible with declared channel")
    require(is_choice(m.get("mapping_status"), {"documented", "raw_only", "unknown"}), "unsupported mapping status")
    require(type(m.get("comparison_eligible")) is bool, "comparison_eligible must be boolean")
    require(is_choice(m.get("time_basis"), {"wall_clock_synchronized", "monotonic_with_epoch_mapping", "relative_unverified"}), "unsupported time basis")
    try:
        start, end = parse_time(m["start_at"]), parse_time(m["end_at"])
        consent_at = parse_time(g["consent_at"])
        require(end > start, "measurement end_at must follow start_at")
        calibration_start = parse_time(ref["calibration_valid_from"])
        calibration_end = parse_time(ref["calibration_valid_through"])
    except (KeyError, TypeError, ValueError):
        start = end = calibration_start = calibration_end = consent_at = None
        errors.append("measurement and calibration timestamps must be timezone-aware ISO-8601")
    require(start is not None and consent_at is not None and consent_at <= start, "documented consent must precede measurement")
    require(cond.get("watch_worn") is True and cond.get("charging") is False, "watch must be worn and not charging")
    require(matches(REFERENCE, ref.get("reference_code")), "reference_code must be pseudonymous")
    require(is_text(ref.get("modality")) and is_text(ref.get("model")), "reference equipment must be documented")
    require(is_choice(ref.get("unit"), {"bpm", "percent", "mV", "ohm", "sleep_stage_label", "other_documented"}), "unsupported reference unit")
    require(is_choice(ref.get("calibration_status"), {"valid", "unknown", "expired"}), "unsupported reference calibration status")
    require(is_choice(cond.get("activity"), {"REST", "WALK", "RUN", "CYCLE", "SLEEP", "OTHER_PREDECLARED"}), "unsupported activity condition")
    require(artifact.get("encrypted_at_rest") is True, "artifact must be encrypted at rest")
    require(matches(HASH, artifact.get("sha256")), "artifact sha256 must be valid")
    for field, lower_bound, inclusive in (("sample_interval_ms", 0.0, False), ("jitter_ms", 0.0, True)):
        value = m.get(field)
        valid = value is None or (
            type(value) in (int, float) and math.isfinite(value) and
            (value >= lower_bound if inclusive else value > lower_bound)
        )
        require(valid, f"{field} must be null or a finite {'nonnegative' if inclusive else 'positive'} number")
    require(is_text(sync.get("method")), "synchronization method is required")
    limit = sync.get("max_allowed_error_ms")
    offsets = (sync.get("start_offset_ms"), sync.get("end_offset_ms"), limit)
    valid_sync = all(type(value) in (int, float) and math.isfinite(value) for value in offsets)
    require(valid_sync and limit > 0 if valid_sync else False, "synchronization values must be finite and maximum error positive")
    require(matches(UUID, artifact.get("artifact_id")), "artifact_id must be a UUID")
    if m.get("comparison_eligible"):
        require(m.get("channel") != "RAW_PPG", "raw PPG samples are not directly comparable as a physiological endpoint")
        require(m.get("mapping_status") == "documented", "comparison requires documented channel mapping")
        require(ref.get("calibration_status") == "valid", "comparison requires valid reference calibration")
        require(start is not None and end is not None and calibration_start is not None and calibration_end is not None and calibration_start <= start and calibration_end >= end, "reference calibration must cover the full measurement interval")
        require(is_choice(m.get("unit"), {"bpm", "percent", "mV", "ohm", "sleep_stage_label"}) and m.get("unit") == ref.get("unit"), "comparison requires matching documented physiological units")
        require(is_choice(m.get("time_basis"), {"wall_clock_synchronized", "monotonic_with_epoch_mapping"}), "comparison requires a mapped measurement time basis")
        if valid_sync and limit > 0:
            require(abs(sync["start_offset_ms"]) <= limit and abs(sync["end_offset_ms"]) <= limit, "synchronization error exceeds predeclared limit")
    return errors


def main():
    try:
        record = load_metadata(sys.argv[1])
    except (IndexError, OSError, UnicodeError, ValueError) as exc:
        print(f"invalid input: {exc}", file=sys.stderr)
        return 2
    errors = validate(record)
    if errors:
        print("REJECTED: " + "; ".join(errors))
        return 1
    print("METADATA PREFLIGHT PASSED; not authorization to collect or evidence of clinical validity")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
