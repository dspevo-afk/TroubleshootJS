"""Pure synthetic tests for the original-DTO metering summary oracle."""

import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


_ORACLE_PATH = Path(__file__).resolve().with_name("original_dto_meter_oracle.py")
_SPEC = importlib.util.spec_from_file_location(
    "original_dto_meter_oracle_under_test", str(_ORACLE_PATH)
)
ORACLE = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(ORACLE)


SMOKE_NAMES = ("map_read", "synthetic_recipe")
SUITE_NAMES = (
    "field_read",
    "map_null_source",
    "field_null_source",
    "map_group_guard",
    "field_group_guard",
    "invalid_capture_null",
    "invalid_capture_text",
    "invalid_capture_same_run",
)

CASE_FACTS = {
    "map_read": (
        "map", "OK", "", 1, 0, 0, 1, 0, 1, 1, False, 1, True,
        "SYNTHETIC_METADATA", False, False,
    ),
    "synthetic_recipe": (
        "recipe", "OK", "", 154, 105, 2, 1, 0, 154, 154, False, 14, True,
        "SYNTHETIC_RECIPE_NOT_ORIGINAL_DTO", False, False,
    ),
    "field_read": (
        "field", "OK", "", 1, 0, 0, 1, 0, 1, 1, False, 0, True,
        "SYNTHETIC_METADATA", False, False,
    ),
    "map_null_source": (
        "map", "EXPECTED_REJECTION", "ORIGINAL_DTO_SOURCE_MAP_MISSING",
        0, 0, 0, 0, 0, 0, 0, False, 0, False, "", True, True,
    ),
    "field_null_source": (
        "field", "EXPECTED_REJECTION", "R7_SOURCE_MISSING",
        0, 0, 0, 0, 0, 0, 0, False, 0, False, "", True, True,
    ),
    "map_group_guard": (
        "guard", "EXPECTED_REJECTION", "native wrapper exceeded 640 measured groups",
        640, 0, 0, 640, 0, 1, 1, False, 0, False, "", True, False,
    ),
    "field_group_guard": (
        "guard", "EXPECTED_REJECTION", "native wrapper exceeded 640 measured groups",
        640, 0, 0, 640, 0, 1, 1, False, 0, False, "", True, False,
    ),
    "invalid_capture_null": (
        "projection", "EXPECTED_REJECTION", "CAPTURE_ID",
        2, 0, 0, 0, 0, 2, 0, True, 0, False, "", True, True,
    ),
    "invalid_capture_text": (
        "projection", "EXPECTED_REJECTION", "CAPTURE_ID",
        2, 0, 0, 0, 0, 2, 0, True, 0, False, "", True, True,
    ),
    "invalid_capture_same_run": (
        "projection", "EXPECTED_REJECTION", "CAPTURE_ID",
        3, 0, 0, 0, 0, 3, 0, True, 0, False, "", True, True,
    ),
}


def _make_case(name):
    (
        phase,
        reason,
        error,
        units,
        copied,
        chunks,
        groups,
        prior_groups,
        group_units,
        max_units,
        is_open,
        source_reads,
        identity,
        payload,
        discarded,
        _unused,
    ) = CASE_FACTS[name]
    open_age = 1 if is_open else 0
    return {
        "name": name,
        "status": "PASS",
        "phase": phase,
        "reason": reason,
        "error": error,
        "units": units,
        "copiedEntries": copied,
        "copyChunks": chunks,
        "groups": groups,
        "priorGroups": prior_groups,
        "groupUnits": group_units,
        "maxGroupUnits": max_units,
        "maxGroupNanos": 0 if groups == 0 else 1,
        "open": is_open,
        "sourceReads": source_reads,
        "identity": identity,
        "payload": payload,
        "operationNanos": 1,
        "totalElapsedNanos": 2,
        "openGroupElapsedNanos": open_age,
        "discarded": discarded,
    }


def _summary(mode):
    names = SMOKE_NAMES if mode == "SMOKE" else SUITE_NAMES
    return {
        "schema": "TSJ_ORIGINAL_DTO_METER_CANARY_R1",
        "mode": mode,
        "status": "PASS",
        "names": list(names),
        "cases": [_make_case(name) for name in names],
        "parentConstructions": 1,
        "error": "",
    }


class OriginalDtoMeterOracleTests(unittest.TestCase):
    def assert_rejected(self, code, callback):
        with self.assertRaises(ORACLE.OracleError) as raised:
            callback()
        self.assertEqual(raised.exception.code, code)

    def test_smoke_synthetic_cases_pass(self):
        result = ORACLE.verify_summary(_summary("SMOKE"), "SMOKE")
        self.assertEqual(result["status"], "PASS_INDEPENDENT_ORIGINAL_DTO_METER_COMPONENT")
        self.assertFalse(result["actualPositiveOriginalDto"])
        self.assertFalse(result["normalHostAcceptance"])

    def test_suite_synthetic_cases_pass(self):
        result = ORACLE.verify_summary(_summary("SUITE"), "SUITE")
        self.assertEqual(result["caseCount"], 8)
        self.assertEqual(result["genuineEnvelope"], "UNSPENT_GATED")

    def test_rejects_wrong_mode(self):
        self.assert_rejected(
            "SUMMARY_MODE", lambda: ORACLE.verify_summary(_summary("SMOKE"), "SUITE")
        )

    def test_rejects_schema_mutation(self):
        value = _summary("SMOKE")
        value["schema"] = "OTHER_SCHEMA"
        self.assert_rejected(
            "SUMMARY_SCHEMA", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_case_order_mutation(self):
        value = _summary("SMOKE")
        value["cases"][0]["name"] = "synthetic_recipe"
        self.assert_rejected(
            "SUMMARY_CASE_ORDER", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_boolean_counter(self):
        value = _summary("SMOKE")
        value["cases"][0]["units"] = True
        self.assert_rejected(
            "SUMMARY_COUNTER", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_recipe_price_mutation(self):
        value = _summary("SMOKE")
        value["cases"][1]["units"] -= 1
        self.assert_rejected(
            "SUMMARY_COUNTER", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_group_ledger_mutation(self):
        value = _summary("SUITE")
        value["cases"][3]["groups"] -= 1
        self.assert_rejected(
            "SUMMARY_COUNTER", lambda: ORACLE.verify_summary(value, "SUITE")
        )

    def test_rejects_clock_overrun(self):
        value = _summary("SMOKE")
        value["cases"][0]["totalElapsedNanos"] = ORACLE.PRODUCER_LIMIT_NS + 1
        self.assert_rejected(
            "SUMMARY_CLOCK", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_identity_mutation(self):
        value = _summary("SMOKE")
        value["cases"][0]["identity"] = False
        self.assert_rejected(
            "SUMMARY_IDENTITY", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_payload_mutation(self):
        value = _summary("SMOKE")
        value["cases"][0]["payload"] = "ORIGINAL_DTO"
        self.assert_rejected(
            "SUMMARY_PAYLOAD", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_error_mutation(self):
        value = _summary("SUITE")
        value["cases"][1]["error"] = "different error"
        self.assert_rejected(
            "SUMMARY_CASE_VALUE", lambda: ORACLE.verify_summary(value, "SUITE")
        )

    def test_rejects_missing_case_field(self):
        value = _summary("SMOKE")
        del value["cases"][0]["discarded"]
        self.assert_rejected(
            "SUMMARY_CASE_KEYS", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_unknown_case_field(self):
        value = _summary("SMOKE")
        value["cases"][0]["extraField"] = "unexpected"
        self.assert_rejected(
            "SUMMARY_CASE_KEYS", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_duplicate_json_key(self):
        self.assert_rejected(
            "INPUT_JSON",
            lambda: ORACLE.decode_json_bytes(b'{"mode":"SMOKE","mode":"SUITE"}'),
        )

    def test_accepts_hash_bound_synthetic_source_pin(self):
        with tempfile.TemporaryDirectory() as directory:
            source_dir = Path(directory)
            file_hashes = {}
            expected_bytes = {}
            for name in ORACLE.SOURCE_NAMES:
                raw = ("fixture:" + name).encode("ascii")
                (source_dir / name).write_bytes(raw)
                file_hashes[name] = hashlib.sha256(raw).hexdigest()
                expected_bytes[name] = raw
            pin = {"status": ORACLE.SOURCE_PIN_STATUS, "files": file_hashes}
            (source_dir / ORACLE.SOURCE_PIN_NAME).write_text(
                json.dumps(pin, separators=(",", ":")), encoding="utf-8"
            )
            self.assertEqual(ORACLE.load_source_pin(str(source_dir)), expected_bytes)

    def test_rejects_source_pin_digest_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            source_dir = Path(directory)
            file_hashes = {}
            for name in ORACLE.SOURCE_NAMES:
                raw = ("fixture:" + name).encode("ascii")
                (source_dir / name).write_bytes(raw)
                file_hashes[name] = hashlib.sha256(raw).hexdigest()
            file_hashes[ORACLE.SOURCE_NAMES[0]] = "0" * 64
            pin = {"status": ORACLE.SOURCE_PIN_STATUS, "files": file_hashes}
            (source_dir / ORACLE.SOURCE_PIN_NAME).write_text(
                json.dumps(pin, separators=(",", ":")), encoding="utf-8"
            )
            self.assert_rejected(
                "SOURCE_PIN", lambda: ORACLE.load_source_pin(str(source_dir))
            )

    def test_rejects_reference_contract_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "reference").mkdir()
            (root / "CONTRACT_R1.md").write_text("synthetic mismatch", encoding="ascii")
            self.assert_rejected(
                "REFERENCE_CONTRACT_PIN",
                lambda: ORACLE.load_reference_root(str(root)),
            )


if __name__ == "__main__":
    unittest.main()
