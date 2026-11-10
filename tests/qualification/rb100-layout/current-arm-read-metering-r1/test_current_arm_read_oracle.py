"""Pure synthetic tests for the independent current-arm read oracle."""

import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


_ORACLE_PATH = Path(__file__).resolve().with_name("current_arm_read_oracle.py")
_SPEC = importlib.util.spec_from_file_location(
    "current_arm_read_oracle_under_test", str(_ORACLE_PATH)
)
ORACLE = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(ORACLE)


SMOKE_NAMES = (
    "list_read",
    "contains_first",
    "validator_no_failure_zero",
)
SUITE_NAMES = (
    "contains_last",
    "contains_missing",
    "contains_empty",
    "list_index_rejected",
    "list_group_guard",
    "contains_group_guard",
    "validator_schema_rejected",
    "validator_callback_limit",
    "validator_no_failure_callbacks",
    "validator_quality_limit_zero",
    "validator_first_non_found",
    "validator_quality_net_callbacks",
)

# Facts are derived from the admitted Study paths and the canary's synthetic
# singleton/three-element fixture shapes. They are intentionally literal here.
CASE_FACTS = {
    "list_read": (
        "list", "OK", "", 1, 0, 0, 1, 0, 1, 1, False,
        (0, 1, 0, 0, 0, 0), True, "SYNTHETIC_METADATA", False,
    ),
    "contains_first": (
        "contains", "OK", "", 10, 0, 0, 1, 0, 10, 10, False,
        (0, 0, 1, 1, 1, 1), True, "SYNTHETIC_METADATA", False,
    ),
    "validator_no_failure_zero": (
        "validator", "OK", "", 18, 0, 0, 1, 0, 18, 18, False,
        (11, 0, 0, 0, 0, 0), True, "SYNTHETIC_VALIDATOR_METADATA", False,
    ),
    "contains_last": (
        "contains", "OK", "", 12, 0, 0, 1, 0, 12, 12, False,
        (0, 0, 1, 1, 3, 3), True, "SYNTHETIC_METADATA", False,
    ),
    "contains_missing": (
        "contains", "OK", "", 12, 0, 0, 1, 0, 12, 12, False,
        (0, 0, 1, 1, 4, 3), True, "SYNTHETIC_METADATA", False,
    ),
    "contains_empty": (
        "contains", "OK", "", 3, 0, 0, 1, 0, 3, 3, False,
        (0, 0, 1, 1, 1, 0), True, "SYNTHETIC_METADATA", False,
    ),
    "list_index_rejected": (
        "list", "EXPECTED_REJECTION", "java.lang.IndexOutOfBoundsException",
        1, 0, 0, 0, 0, 1, 0, True,
        (0, 1, 0, 0, 0, 0), False, "", True,
    ),
    "list_group_guard": (
        "guard", "EXPECTED_REJECTION", ORACLE.GROUP_GUARD_ERROR,
        640, 0, 0, 640, 0, 1, 1, False,
        (0, 0, 0, 0, 0, 0), False, "", True,
    ),
    "contains_group_guard": (
        "guard", "EXPECTED_REJECTION", ORACLE.GROUP_GUARD_ERROR,
        640, 0, 0, 640, 0, 1, 1, False,
        (0, 0, 0, 0, 0, 0), False, "", True,
    ),
    "validator_schema_rejected": (
        "validator", "EXPECTED_REJECTION", "OBSERVER_V2_SCHEMA",
        1, 0, 0, 0, 0, 1, 0, True,
        (1, 0, 0, 0, 0, 0), False, "", True,
    ),
    "validator_callback_limit": (
        "validator", "EXPECTED_REJECTION", "OBSERVER_CALLBACK_COUNT",
        7, 0, 0, 0, 0, 7, 0, True,
        (6, 0, 0, 0, 0, 0), False, "", True,
    ),
    "validator_no_failure_callbacks": (
        "validator", "OK", "", 40, 0, 0, 1, 0, 40, 40, False,
        (15, 2, 3, 2, 2, 2), True, "SYNTHETIC_VALIDATOR_METADATA", False,
    ),
    "validator_quality_limit_zero": (
        "validator", "OK", "", 24, 0, 0, 1, 0, 24, 24, False,
        (13, 0, 0, 0, 0, 0), True, "SYNTHETIC_VALIDATOR_METADATA", False,
    ),
    "validator_first_non_found": (
        "validator", "OK", "", 61, 1, 1, 1, 0, 61, 61, False,
        (26, 3, 5, 3, 3, 3), True, "SYNTHETIC_VALIDATOR_METADATA", False,
    ),
    "validator_quality_net_callbacks": (
        "validator", "OK", "", 55, 1, 1, 1, 0, 55, 55, False,
        (22, 4, 4, 2, 2, 2), True, "SYNTHETIC_VALIDATOR_METADATA", False,
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
        reads,
        identity,
        payload,
        discarded,
    ) = CASE_FACTS[name]
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
        "mapReads": reads[0],
        "listGetReads": reads[1],
        "sizeReads": reads[2],
        "iteratorConstructions": reads[3],
        "hasNextReads": reads[4],
        "nextReads": reads[5],
        "identity": identity,
        "payload": payload,
        "operationNanos": 1,
        "totalElapsedNanos": 2,
        "openGroupElapsedNanos": 1 if is_open else 0,
        "discarded": discarded,
    }


def _summary(mode):
    names = SMOKE_NAMES if mode == "SMOKE" else SUITE_NAMES
    return {
        "schema": "TSJ_CURRENT_ARM_READ_METER_CANARY_R1",
        "mode": mode,
        "status": "PASS",
        "names": list(names),
        "cases": [_make_case(name) for name in names],
        "parentConstructions": 1,
        "error": "",
    }


class CurrentArmReadOracleTests(unittest.TestCase):
    def assert_rejected(self, code, callback):
        with self.assertRaises(ORACLE.OracleError) as raised:
            callback()
        self.assertEqual(raised.exception.code, code)

    def test_smoke_synthetic_cases_pass(self):
        result = ORACLE.verify_summary(_summary("SMOKE"), "SMOKE")
        self.assertEqual(
            result["status"], "PASS_INDEPENDENT_CURRENT_ARM_READ_COMPONENT"
        )
        self.assertFalse(result["actualPositiveOwnerResult"])
        self.assertFalse(result["normalHostAcceptance"])

    def test_suite_synthetic_cases_pass(self):
        result = ORACLE.verify_summary(_summary("SUITE"), "SUITE")
        self.assertEqual(result["caseCount"], 12)
        self.assertEqual(result["genuineEnvelope"], "UNSPENT_GATED")

    def test_rejects_wrong_mode(self):
        self.assert_rejected(
            "SUMMARY_MODE",
            lambda: ORACLE.verify_summary(_summary("SMOKE"), "SUITE"),
        )

    def test_rejects_schema_mutation(self):
        value = _summary("SMOKE")
        value["schema"] = "OTHER_SCHEMA"
        self.assert_rejected(
            "SUMMARY_SCHEMA", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_top_key_order_mutation(self):
        value = _summary("SMOKE")
        value["status"] = value.pop("status")
        self.assert_rejected(
            "SUMMARY_TOP_KEYS", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_canary_failure_status(self):
        value = _summary("SMOKE")
        value["status"] = "FAIL"
        self.assert_rejected(
            "SUMMARY_STATUS", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_boolean_parent_count(self):
        value = _summary("SMOKE")
        value["parentConstructions"] = True
        self.assert_rejected(
            "SUMMARY_PARENT_CONSTRUCTIONS",
            lambda: ORACLE.verify_summary(value, "SMOKE"),
        )

    def test_rejects_case_order_mutation(self):
        value = _summary("SMOKE")
        value["cases"][0]["name"] = "contains_first"
        self.assert_rejected(
            "SUMMARY_CASE_ORDER", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_boolean_counter(self):
        value = _summary("SMOKE")
        value["cases"][0]["units"] = True
        self.assert_rejected(
            "SUMMARY_COUNTER", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_list_read_debit_mutation(self):
        value = _summary("SMOKE")
        value["cases"][0]["units"] = 0
        self.assert_rejected(
            "SUMMARY_COUNTER", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_contains_iteration_counter_mutation(self):
        value = _summary("SMOKE")
        value["cases"][1]["hasNextReads"] = 2
        self.assert_rejected(
            "SUMMARY_COUNTER", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_group_admission_ledger_mutation(self):
        value = _summary("SUITE")
        value["cases"][4]["groups"] = 639
        self.assert_rejected(
            "SUMMARY_COUNTER", lambda: ORACLE.verify_summary(value, "SUITE")
        )

    def test_rejects_validator_map_read_mutation(self):
        value = _summary("SUITE")
        value["cases"][11]["mapReads"] = 21
        self.assert_rejected(
            "SUMMARY_COUNTER", lambda: ORACLE.verify_summary(value, "SUITE")
        )

    def test_rejects_callback_read_ledger_mutation(self):
        value = _summary("SUITE")
        value["cases"][8]["listGetReads"] = 1
        self.assert_rejected(
            "SUMMARY_COUNTER", lambda: ORACLE.verify_summary(value, "SUITE")
        )

    def test_rejects_clock_overrun(self):
        value = _summary("SMOKE")
        value["cases"][0]["totalElapsedNanos"] = ORACLE.MAX_TOTAL_NS + 1
        self.assert_rejected(
            "SUMMARY_CLOCK", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_operation_clock_overrun(self):
        value = _summary("SMOKE")
        value["cases"][0]["operationNanos"] = ORACLE.MAX_OPERATION_NS + 1
        self.assert_rejected(
            "SUMMARY_CLOCK", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_completed_group_clock_overrun(self):
        value = _summary("SUITE")
        value["cases"][4]["maxGroupNanos"] = ORACLE.MAX_GROUP_NS + 1
        self.assert_rejected(
            "SUMMARY_CLOCK", lambda: ORACLE.verify_summary(value, "SUITE")
        )

    def test_rejects_boolean_clock(self):
        value = _summary("SMOKE")
        value["cases"][0]["operationNanos"] = True
        self.assert_rejected(
            "SUMMARY_CLOCK", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_group_age_when_closed(self):
        value = _summary("SMOKE")
        value["cases"][0]["openGroupElapsedNanos"] = 1
        self.assert_rejected(
            "SUMMARY_CLOCK", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_open_group_age_overrun(self):
        value = _summary("SUITE")
        value["cases"][3]["openGroupElapsedNanos"] = ORACLE.MAX_GROUP_NS + 1
        self.assert_rejected(
            "SUMMARY_CLOCK", lambda: ORACLE.verify_summary(value, "SUITE")
        )

    def test_rejects_identity_mutation(self):
        value = _summary("SMOKE")
        value["cases"][0]["identity"] = False
        self.assert_rejected(
            "SUMMARY_IDENTITY", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_genuine_payload_marker(self):
        value = _summary("SMOKE")
        value["cases"][0]["payload"] = "ORIGINAL_ARM_RESULT"
        self.assert_rejected(
            "SUMMARY_PAYLOAD", lambda: ORACLE.verify_summary(value, "SMOKE")
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
            lambda: ORACLE.decode_json_bytes(
                b'{"mode":"SMOKE","mode":"SUITE"}'
            ),
        )

    def test_rejects_floating_point_json(self):
        self.assert_rejected(
            "INPUT_JSON",
            lambda: ORACLE.decode_json_bytes(b'{"units":1.0}'),
        )

    def test_rejects_json_depth_over_limit(self):
        raw = (b"[" * (ORACLE.MAX_DEPTH + 1)) + b"0" + (
            b"]" * (ORACLE.MAX_DEPTH + 1)
        )
        self.assert_rejected(
            "JSON_DEPTH", lambda: ORACLE.decode_json_bytes(raw)
        )

    def test_rejects_oversized_json_key(self):
        raw = b'{"' + (b"k" * (ORACLE.MAX_KEY_LENGTH + 1)) + b'":0}'
        self.assert_rejected(
            "JSON_KEY_SIZE", lambda: ORACLE.decode_json_bytes(raw)
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
            pin = {
                "status": ORACLE.SOURCE_PIN_STATUS,
                "files": file_hashes,
            }
            (source_dir / ORACLE.SOURCE_PIN_NAME).write_text(
                json.dumps(pin, separators=(",", ":")), encoding="utf-8"
            )
            self.assertEqual(
                ORACLE.load_source_pin(str(source_dir)), expected_bytes
            )

    def test_rejects_source_pin_digest_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            source_dir = Path(directory)
            file_hashes = {}
            for name in ORACLE.SOURCE_NAMES:
                raw = ("fixture:" + name).encode("ascii")
                (source_dir / name).write_bytes(raw)
                file_hashes[name] = hashlib.sha256(raw).hexdigest()
            file_hashes[ORACLE.SOURCE_NAMES[0]] = "0" * 64
            pin = {
                "status": ORACLE.SOURCE_PIN_STATUS,
                "files": file_hashes,
            }
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
            (root / "CONTRACT_R1.md").write_text(
                "synthetic mismatch", encoding="ascii"
            )
            self.assert_rejected(
                "REFERENCE_CONTRACT_PIN",
                lambda: ORACLE.load_reference_root(str(root)),
            )


if __name__ == "__main__":
    unittest.main()
