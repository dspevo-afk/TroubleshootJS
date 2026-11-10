"""Pure tests for the synthetic current-arms projection verifier."""

import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


_ORACLE_PATH = Path(__file__).resolve().with_name("arms_oracle.py")
_SPEC = importlib.util.spec_from_file_location(
    "arms_oracle_under_test", str(_ORACLE_PATH)
)
ORACLE = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(ORACLE)


SMOKE_NAMES = ("arms_transfer", "arms_ownership_disjoint")
SUITE_NAMES = (
    "arms_argument_meter",
    "arms_argument_null_id",
    "arms_argument_null_run",
    "arms_argument_null_observation",
    "arms_argument_null_cleanup",
    "arms_argument_observe_id",
    "arms_argument_observe_run",
    "arms_argument_observe_observation",
    "arms_argument_observe_cleanup",
    "arms_alias_rejected",
    "arms_semantic_passthrough",
    "arms_copy_allowance_4",
    "arms_copy_allowance_5",
    "arms_copy_allowance_10",
    "arms_copy_allowance_12",
    "arms_group_guard",
    "arms_success_open",
)

ARGUMENT_NAMES = frozenset(SUITE_NAMES[:9])
REJECTED_NAMES = ARGUMENT_NAMES | frozenset((
    "arms_alias_rejected",
    "arms_copy_allowance_4",
    "arms_copy_allowance_5",
    "arms_copy_allowance_10",
    "arms_group_guard",
))
NO_PAYLOAD_NAMES = frozenset((
    *ARGUMENT_NAMES,
    "arms_copy_allowance_4",
    "arms_copy_allowance_5",
    "arms_copy_allowance_10",
    "arms_group_guard",
))

LEDGERS = {
    "arms_argument_meter": (0, 0, 0, 0, 0, 0, 0),
    "arms_argument_null_id": (0, 0, 0, 0, 0, 0, 0),
    "arms_argument_null_run": (0, 0, 0, 0, 0, 0, 0),
    "arms_argument_null_observation": (0, 0, 0, 0, 0, 0, 0),
    "arms_argument_null_cleanup": (0, 0, 0, 0, 0, 0, 0),
    "arms_argument_observe_id": (0, 0, 0, 0, 0, 0, 0),
    "arms_argument_observe_run": (0, 0, 0, 0, 0, 0, 0),
    "arms_argument_observe_observation": (0, 0, 0, 0, 0, 0, 0),
    "arms_argument_observe_cleanup": (0, 0, 0, 0, 0, 0, 0),
    "arms_alias_rejected": (187, 19, 10, 0, 187, 0, 0),
    "arms_semantic_passthrough": (15, 12, 3, 1, 15, 15, 0),
    "arms_copy_allowance_4": (0, 0, 0, 0, 0, 0, 0),
    "arms_copy_allowance_5": (6, 5, 1, 0, 6, 0, 0),
    "arms_copy_allowance_10": (12, 10, 2, 0, 12, 0, 0),
    "arms_copy_allowance_12": (15, 12, 3, 1, 15, 15, 0),
    "arms_group_guard": (640, 0, 0, 640, 1, 1, 0),
    "arms_success_open": (15, 12, 3, 0, 15, 0, 0),
    "arms_transfer": (15, 12, 3, 1, 15, 15, 0),
    "arms_ownership_disjoint": (254, 21, 12, 1, 254, 254, 0),
}

HELPERS = {
    **{name: (0, 0, 0) for name in ARGUMENT_NAMES},
    "arms_alias_rejected": (15, 12, 3),
    "arms_semantic_passthrough": (15, 12, 3),
    "arms_copy_allowance_4": (0, 0, 0),
    "arms_copy_allowance_5": (6, 5, 1),
    "arms_copy_allowance_10": (12, 10, 2),
    "arms_copy_allowance_12": (15, 12, 3),
    "arms_group_guard": (0, 0, 0),
    "arms_success_open": (15, 12, 3),
    "arms_transfer": (15, 12, 3),
    "arms_ownership_disjoint": (15, 12, 3),
}

PAYLOAD = (
    '{"null":{"armRole":"NULL","armId":"SYNTH_NULL",'
    '"freshRun":{"nullRun":"N_RUN"},'
    '"observation":{"nullObservation":"N_OBS"},'
    '"cleanup":{"nullCleanup":"N_CLEAN"}},'
    '"observe":{"armRole":"OBSERVE","armId":"SYNTH_OBSERVE",'
    '"freshRun":{"observeRun":"O_RUN"},'
    '"observation":{"observeObservation":"O_OBS"},'
    '"cleanup":{"observeCleanup":"O_CLEAN"}}}'
)
ALIAS_PAYLOAD = PAYLOAD.replace(
    '"freshRun":{"observeRun":"O_RUN"}',
    '"freshRun":{"nullRun":"N_RUN"}',
)


def _expected_error(name):
    if name in ARGUMENT_NAMES:
        return "RESULT_PROJECTION_ARGUMENT"
    if name in (
        "arms_copy_allowance_4",
        "arms_copy_allowance_5",
        "arms_copy_allowance_10",
    ):
        return "copy exceeds its prebounded chunk or aggregate allowance"
    if name == "arms_group_guard":
        return "native wrapper exceeded 640 measured groups"
    if name == "arms_alias_rejected":
        return "BODY_OWNERSHIP_ALIAS"
    return ""


def _case_phase(name):
    if name in ARGUMENT_NAMES:
        return "argument_guard"
    if name.startswith("arms_copy_allowance_") or name == "arms_group_guard":
        return "projection_guard"
    if name in ("arms_alias_rejected", "arms_ownership_disjoint"):
        return "ownership_guard"
    return "projection"


def _identity(name):
    present = name not in NO_PAYLOAD_NAMES
    return {
        "outerFresh": present,
        "armMapsFresh": present,
        "fieldOrder": present,
        "idsPreserved": present,
        "childMapsPreserved": present,
        "inputMapsDisjoint": present and name != "arms_alias_rejected",
    }


def _make_case(name):
    rejected = name in REJECTED_NAMES
    payload = ""
    if name not in NO_PAYLOAD_NAMES:
        payload = ALIAS_PAYLOAD if name == "arms_alias_rejected" else PAYLOAD
    units, copied, chunks, groups, group_units, max_units, _max_nanos = LEDGERS[name]
    helper_units, helper_copies, helper_chunks = HELPERS[name]
    final_open = name in (
        "arms_copy_allowance_5",
        "arms_copy_allowance_10",
        "arms_alias_rejected",
        "arms_success_open",
    )
    meter_open = name not in (
        ARGUMENT_NAMES
        | frozenset(("arms_copy_allowance_4", "arms_group_guard"))
    )
    return {
        "name": name,
        "status": "PASS",
        "phase": _case_phase(name),
        "reason": "EXPECTED_REJECTION" if rejected else "OK",
        "operation": "CurrentResultProjections.arms",
        "error": _expected_error(name),
        "rejections": int(rejected),
        "payloadJson": payload,
        "units": units,
        "copiedEntries": copied,
        "copyChunks": chunks,
        "groups": groups,
        "priorGroups": 0,
        "groupUnits": group_units,
        "maxGroupUnits": max_units,
        "maxGroupNanos": 0 if groups == 0 else 1,
        "meterOpen": meter_open,
        "finalOpen": final_open,
        "helperUnits": helper_units,
        "helperCopies": helper_copies,
        "helperChunks": helper_chunks,
        "identity": _identity(name),
        "inputUnchanged": True,
        "sourceReads": 0,
        "discarded": rejected,
        "operationNanos": 1,
        "totalElapsedNanos": 2,
        "openGroupElapsedNanos": 1 if final_open else 0,
    }


def _summary(mode):
    names = SMOKE_NAMES if mode == "SMOKE" else SUITE_NAMES
    return {
        "schema": "TSJ_CURRENT_ARMS_PROJECTION_CANARY_R1",
        "mode": mode,
        "status": "PASS",
        "names": list(names),
        "cases": [_make_case(name) for name in names],
        "parentConstructions": len(names),
        "error": "",
    }


class ArmsOracleTests(unittest.TestCase):
    def assert_rejected(self, code, callback):
        with self.assertRaises(ORACLE.OracleError) as raised:
            callback()
        self.assertEqual(raised.exception.code, code)

    def test_smoke_component_fixture_passes(self):
        result = ORACLE.verify_summary(_summary("SMOKE"), "SMOKE")
        self.assertEqual(result["status"], "PASS_INDEPENDENT_CURRENT_ARMS_PROJECTION")
        self.assertFalse(result["normalHostAcceptance"])
        self.assertFalse(result["semanticAcceptance"])

    def test_suite_component_fixture_passes(self):
        result = ORACLE.verify_summary(_summary("SUITE"), "SUITE")
        self.assertEqual(result["caseCount"], 17)
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

    def test_rejects_extra_top_field(self):
        value = _summary("SMOKE")
        value["extraField"] = "unexpected"
        self.assert_rejected(
            "SUMMARY_TOP_KEYS", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_case_order_mutation(self):
        value = _summary("SMOKE")
        value["cases"][0]["name"] = "arms_ownership_disjoint"
        self.assert_rejected(
            "SUMMARY_CASE_ORDER", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_boolean_counter(self):
        value = _summary("SMOKE")
        value["cases"][0]["units"] = True
        self.assert_rejected(
            "SUMMARY_COUNTER", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_wrong_helper_debit(self):
        value = _summary("SMOKE")
        value["cases"][0]["helperUnits"] += 1
        self.assert_rejected(
            "SUMMARY_COUNTER", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_error_mutation(self):
        value = _summary("SMOKE")
        value["cases"][0]["error"] = "unexpected error"
        self.assert_rejected(
            "SUMMARY_CASE_VALUE", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_payload_mutation(self):
        value = _summary("SMOKE")
        value["cases"][0]["payloadJson"] = value["cases"][0]["payloadJson"].replace(
            "SYNTH_NULL", "OTHER"
        )
        self.assert_rejected(
            "SUMMARY_PAYLOAD", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_identity_mutation(self):
        value = _summary("SMOKE")
        value["cases"][0]["identity"]["outerFresh"] = False
        self.assert_rejected(
            "SUMMARY_IDENTITY", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_clock_overrun(self):
        value = _summary("SMOKE")
        value["cases"][0]["totalElapsedNanos"] = ORACLE.PRODUCER_LIMIT_NS + 1
        self.assert_rejected(
            "SUMMARY_CLOCK", lambda: ORACLE.verify_summary(value, "SMOKE")
        )

    def test_rejects_missing_case_field(self):
        value = _summary("SMOKE")
        del value["cases"][0]["finalOpen"]
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
            contents = {}
            for name in ORACLE.SOURCE_NAMES:
                raw = ("fixture:" + name).encode("ascii")
                (source_dir / name).write_bytes(raw)
                contents[name] = hashlib.sha256(raw).hexdigest()
            pin = {"status": ORACLE.SOURCE_PIN_STATUS, "files": contents}
            (source_dir / ORACLE.SOURCE_PIN_NAME).write_text(
                json.dumps(pin, separators=(",", ":")), encoding="utf-8"
            )
            self.assertEqual(
                ORACLE.load_source_pin(str(source_dir)),
                {
                    name: ("fixture:" + name).encode("ascii")
                    for name in ORACLE.SOURCE_NAMES
                },
            )

    def test_rejects_source_pin_digest_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            source_dir = Path(directory)
            contents = {}
            for name in ORACLE.SOURCE_NAMES:
                raw = ("fixture:" + name).encode("ascii")
                (source_dir / name).write_bytes(raw)
                contents[name] = hashlib.sha256(raw).hexdigest()
            contents[ORACLE.SOURCE_NAMES[0]] = "0" * 64
            pin = {"status": ORACLE.SOURCE_PIN_STATUS, "files": contents}
            (source_dir / ORACLE.SOURCE_PIN_NAME).write_text(
                json.dumps(pin, separators=(",", ":")), encoding="utf-8"
            )
            self.assert_rejected(
                "SOURCE_PIN", lambda: ORACLE.load_source_pin(str(source_dir))
            )

    def test_rejects_reference_contract_pin_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "source").mkdir()
            (root / "CONTRACT_R1.md").write_text("synthetic wrong contract", encoding="ascii")
            self.assert_rejected(
                "REFERENCE_CONTRACT_PIN",
                lambda: ORACLE.load_reference_root(str(root)),
            )


if __name__ == "__main__":
    unittest.main()
