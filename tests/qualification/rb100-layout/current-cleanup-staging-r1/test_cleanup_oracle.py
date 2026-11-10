"""Pure fixtures for the authenticated cleanup-staging oracle and reader."""

import hashlib
import json
import os
import copy
import argparse
import sys
import unittest


def _configure_reference_root():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--reference-root",
                        default=os.path.dirname(os.path.abspath(__file__)))
    parsed, remaining = parser.parse_known_args()
    sys.argv = [sys.argv[0]] + remaining
    return os.path.abspath(parsed.reference_root)


ROOT = _configure_reference_root()
CONTRACT_SHA256 = "212ac9daea16bafeae9feb397bfbfaafe9d752260dfe0d4a2346a364f7351443"
REFERENCE_SHA256 = "d5744cfe7118d1f480e88d3fabec06749c32cba1c84cd0cf5379307d670d549d"
WIRE_SHA256 = "3d0ba4ed424ae0251bfa7d151cd4da6a043f41b5183858d117906c3584ced375"
BODY_KEYS = (
    "schema", "mode", "status", "runId", "sourceManifestSha256",
    "wireContractSha256", "inputContextSha256", "qualifiedPrior",
    "originalCapture", "cohort", "arms", "work", "cleanup",
)
BODY_WIRE_SHA256 = "1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed"


def _load_source(path, namespace_name):
    with open(path, "rb") as source_file:
        raw = source_file.read(4194305)
    if len(raw) > 4194304:
        raise AssertionError("source exceeds the independent test cap")
    namespace = {
        "__name__": namespace_name,
        "__file__": os.path.basename(path),
        "__builtins__": __builtins__,
    }
    code = compile(raw, os.path.basename(path), "exec", dont_inherit=True, optimize=0)
    exec(code, namespace, namespace)
    return namespace


def _strict_read(path, expected_sha, expected_bytes):
    with open(path, "rb") as source_file:
        raw = source_file.read(expected_bytes + 1)
    if len(raw) != expected_bytes or hashlib.sha256(raw).hexdigest() != expected_sha:
        raise AssertionError("frozen independent reference pin mismatch")
    return raw


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON field")
        result[key] = value
    return result


def _compact(value):
    return json.dumps(value, ensure_ascii=True, allow_nan=False, separators=(",", ":"))


def _case_census():
    # Independent literal expected values; no canary output is read here.
    rows = {
        "pending_constructor": (14, 13, 1, 1, 0, 14, 14, 14, 13, 1, False, 0),
        "installed_true": (67, 11, 2, 1, 0, 67, 67, 67, 11, 2, False, 27),
        "null_alias_false": (67, 11, 2, 1, 0, 67, 67, 67, 11, 2, False, 27),
        "observe_alias_false": (67, 11, 2, 1, 0, 67, 67, 67, 11, 2, False, 27),
        "geometry_false": (67, 11, 2, 1, 0, 67, 67, 67, 11, 2, False, 27),
        "scalar_below_sum": (67, 11, 2, 1, 0, 67, 67, 67, 11, 2, False, 27),
        "scalar_at_limit": (67, 11, 2, 1, 0, 67, 67, 67, 11, 2, False, 27),
        "negative_scalar": (1, 0, 0, 0, 0, 1, 0, 1, 0, 0, True, 0),
        "over_limit_scalar": (1, 0, 0, 0, 0, 1, 0, 1, 0, 0, True, 0),
        "body_type": (2, 0, 0, 0, 0, 2, 0, 2, 0, 0, True, 0),
        "slot_missing": (3, 0, 0, 0, 0, 3, 0, 3, 0, 0, True, 0),
        "slot_occupied": (4, 0, 0, 0, 0, 4, 0, 4, 0, 0, True, 0),
        "child_view_missing": (10, 0, 0, 0, 0, 10, 0, 10, 0, 0, True, 4),
        "projection_read_error": (12, 0, 0, 0, 0, 12, 0, 12, 0, 0, True, 6),
        "projection_copy_guard": (53, 0, 0, 0, 0, 53, 0, 53, 0, 0, True, 27),
        "replacement_copy_guard": (65, 10, 1, 0, 0, 65, 0, 65, 10, 1, True, 27),
        "group_guard_before_read": (640, 0, 0, 640, 0, 1, 1, 0, 0, 0, False, 0),
        "write_group_guard": (33407, 11, 2, 640, 0, 32768, 32768, 66, 11, 2, False, 27),
        "null_arguments": (0, 0, 0, 0, 0, 0, 0, 0, 0, 0, False, 0),
        "pending_export_reject": (2, 0, 0, 0, 0, 2, 0, 2, 0, 0, True, 0),
        "r7_handoff_true": (18576, 132, 19, 1, 1, 18576, 18576, 67, 11, 2, False, 27),
        "r7_handoff_below_sum": (18576, 132, 19, 1, 1, 18576, 18576, 67, 11, 2, False, 27),
        "r7_handoff_alias_false": (18576, 132, 19, 1, 1, 18576, 18576, 67, 11, 2, False, 27),
        "r7_zero_credit_reject": (18576, 132, 19, 1, 0, 18576, 18576, 67, 11, 2, False, 27),
    }
    return rows


def _errors():
    return {
        "negative_scalar": "IOException:METADATA_CLEANUP_SCALAR",
        "over_limit_scalar": "IOException:METADATA_CLEANUP_SCALAR",
        "body_type": "IOException:METADATA_CLEANUP_BODY",
        "slot_missing": "IOException:METADATA_CLEANUP_SLOT",
        "slot_occupied": "IOException:METADATA_CLEANUP_SLOT",
        "child_view_missing": "IOException:METADATA_CLEANUP_VIEW",
        "projection_read_error": "IllegalStateException:SYNTHETIC_VIEW_READ",
        "projection_copy_guard":
            "IOException:copy exceeds its prebounded chunk or aggregate allowance",
        "replacement_copy_guard":
            "IOException:copy exceeds its prebounded chunk or aggregate allowance",
        "group_guard_before_read":
            "IOException:native wrapper exceeded 640 measured groups",
        "write_group_guard":
            "IOException:native wrapper exceeded 640 measured groups",
        "null_arguments": "IOException:METADATA_ARGUMENTS",
        "pending_export_reject": "CodecFailure:BODY_ROOT_CONTAINER",
        "r7_zero_credit_reject": "IOException:R5_METADATA_SNAPSHOT",
    }


def _cleanup(name, reference):
    result = dict(reference["expectedCleanup"])
    if name == "null_alias_false" or name == "r7_handoff_alias_false":
        result["nullArmAliasesDropped"] = False
    elif name == "observe_alias_false":
        result["observeArmAliasesDropped"] = False
    elif name == "geometry_false":
        result["sourceGeometryUnchanged"] = False
    if name in ("scalar_below_sum", "r7_handoff_below_sum"):
        result["cleanupNanos"] = 5
    elif name == "scalar_at_limit":
        result["cleanupNanos"] = 120000000000
    return result


def _body(reference, prior, work, cleanup, arms=None):
    fixture = reference["fixture"]
    if arms is None:
        arms = fixture["arms"]
    return {
        "schema": "tsj-cohort-terminal-result-v2",
        "mode": "OBSERVED_COHORT_TERMINAL_TELEMETRY",
        "status": "COMPLETED_DIAGNOSTIC_ONLY",
        "runId": fixture["runId"],
        "sourceManifestSha256": fixture["sourceManifestSha256"],
        "wireContractSha256": BODY_WIRE_SHA256,
        "inputContextSha256": fixture["inputContextSha256"],
        "qualifiedPrior": prior,
        "originalCapture": fixture["originalCapture"],
        "cohort": fixture["cohort"],
        "arms": arms,
        "work": work,
        "cleanup": cleanup,
    }


def _work(name, reference):
    cleanup_nanos = 5 if name == "r7_handoff_below_sum" else 123
    values = {
        "elapsedCutoffNanos": 4,
        "cleanupNanos": cleanup_nanos,
        "groupsBeforeExport": 2,
        "wrapperGroupsBeforeExport": 1,
        "maxGroupNanosBeforeExport": 3,
        "maximumUnitsPerGroupBeforeExport": 18576,
        "unitsBeforeExport": 18576,
        "setupUnits": 0,
        "setupGroups": 0,
        "setupCopiedEntries": 0,
        "setupCopyChunks": 0,
        "originalCaptureOwnerUnits": 0,
        "originalCaptureWrapperUnits": 0,
        "originalCaptureCallbackUnits": 0,
        "originalCaptureDiagnosticUnits": 0,
        "originalCaptureGroups": 1,
        "originalCaptureCopiedEntries": 0,
        "originalCaptureCopyChunks": 0,
        "nullOwnerUnits": 0,
        "nullWrapperUnits": 0,
        "nullObserverUnits": 0,
        "observeOwnerUnits": 0,
        "observeWrapperUnits": 0,
        "observeObserverUnits": 0,
        "observerUnits": 0,
        "meterUnits": 18576,
        "metadataUnits": 18576,
        "metadataGroups": 1,
        "metadataCopiedEntries": 132,
        "metadataCopyChunks": 19,
        "copiedEntries": 132,
        "copyChunks": 19,
        "copyChunkLimit": 4096,
        "copiedEntryLimit": 1048576,
    }
    if list(values) != reference["workKeys"]:
        raise AssertionError("independent work-key fixture order drift")
    return values


def _payload(name, reference):
    kind = "constructor" if name == "pending_constructor" else (
        "handoff" if name.startswith("r7_handoff_") else
        "install" if name in ("installed_true", "null_alias_false",
                              "observe_alias_false", "geometry_false",
                              "scalar_below_sum", "scalar_at_limit") else "reject")
    cleanup = _cleanup(name, reference)
    if kind == "constructor":
        return _compact(_body(reference, None, None, None)), 0
    if kind == "install":
        return _compact(cleanup), 0
    if kind == "handoff":
        arms = copy.deepcopy(reference["fixture"]["arms"])
        if name == "r7_handoff_alias_false":
            arms["null"]["cleanup"]["adapterSessionAliasDropped"] = False
        payload = _compact(_body(reference, reference["fixture"]["qualifiedPrior"],
                                 _work(name, reference), cleanup, arms))
        return payload, len(payload.encode("utf-8")) + 1
    return "", 0


def _make_case(name, reference, wire):
    (units, copied, chunks, groups, prior_groups, group_units, max_group_units,
     helper_units, helper_copied, helper_chunks, meter_open, nested_gets) = _case_census()[name]
    kind = "constructor" if name == "pending_constructor" else (
        "handoff" if name.startswith("r7_handoff_") else
        "install" if name in ("installed_true", "null_alias_false",
                              "observe_alias_false", "geometry_false",
                              "scalar_below_sum", "scalar_at_limit") else "reject")
    payload, canonical = _payload(name, reference)
    downstream_r7_reject = name == "r7_zero_credit_reject"
    record = {
        "name": name,
        "status": "PASS",
        "phase": ("pending_constructor" if kind == "constructor" else
                  "r7_handoff" if kind == "handoff" or downstream_r7_reject else
                  "cleanup_install" if kind == "install" else
                  "pending_header" if name == "pending_export_reject" else "guard"),
        "reason": "EXPECTED_REJECTION" if kind == "reject" else "OK",
        "observedError": _errors().get(name, ""),
        "units": units,
        "copiedEntries": copied,
        "copyChunks": chunks,
        "groups": groups,
        "priorGroups": prior_groups,
        "groupUnits": group_units,
        "maxGroupUnits": max_group_units,
        "maxGroupNanos": 3,
        "meterOpen": meter_open,
        "helperUnits": helper_units,
        "helperCopies": helper_copied,
        "helperChunks": helper_chunks,
        "operationNanos": 2,
        "totalElapsedNanos": 10,
        "openGroupElapsedNanos": 0,
        "bodyUnchanged": kind == "reject" and not downstream_r7_reject,
        "inputsUnchanged": True,
        "cleanupInstalled": kind in ("install", "handoff") or downstream_r7_reject,
        "observedNestedGets": nested_gets,
        "semanticDurationGets": 0,
        "discarded": kind == "reject",
        "finishAfterFailure": False,
        "payloadJson": payload,
        "canonicalBytes": canonical,
    }
    if list(record) != wire["caseKeys"]:
        raise AssertionError("independent case fixture order drift")
    return record


def _make_report(case, reference, wire):
    names = reference["smoke"] if case == "smoke" else reference["suite"]
    mode = "SMOKE" if case == "smoke" else "SUITE"
    records = [_make_case(name, reference, wire) for name in names]
    report = {
        "schema": wire["schema"],
        "mode": mode,
        "status": "PASS",
        "names": list(names),
        "cases": records,
        "parentConstructions": len(names),
        "error": "",
    }
    if list(report) != wire["topKeys"]:
        raise AssertionError("independent report fixture order drift")
    return report


class CleanupOracleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pin_path = os.path.join(ROOT, "ROOT_ORACLE_SOURCE_R1.json")
        with open(pin_path, "rb") as pin_file:
            pin_raw = pin_file.read(1025)
        if len(pin_raw) > 1024:
            raise AssertionError("oracle source pin record exceeds test cap")
        pin = json.loads(pin_raw.decode("utf-8", "strict"), object_pairs_hook=_pairs)
        if (type(pin) is not dict or list(pin) != ["sha256", "bytes"] or
                type(pin["sha256"]) is not str or type(pin["bytes"]) is not int):
            raise AssertionError("unexpected root source pin record shape")
        source_path = os.path.join(ROOT, "cleanup_oracle.py")
        with open(source_path, "rb") as source_file:
            source_raw = source_file.read(4194305)
        if (len(source_raw) != pin["bytes"] or
                hashlib.sha256(source_raw).hexdigest() != pin["sha256"]):
            raise AssertionError("oracle source bytes do not match the exact root pin")
        namespace = {
            "__name__": "_cleanup_oracle_pure_test_",
            "__file__": "cleanup_oracle.py",
            "__builtins__": __builtins__,
        }
        exec(compile(source_raw, "cleanup_oracle.py", "exec",
                      dont_inherit=True, optimize=0), namespace, namespace)
        cls.oracle = namespace

        contract_raw = _strict_read(os.path.join(ROOT, "CONTRACT_R1.md"),
                                    CONTRACT_SHA256, 7674)
        reference_raw = _strict_read(os.path.join(ROOT, "REFERENCE_R1.json"),
                                     REFERENCE_SHA256, 5782)
        wire_raw = _strict_read(os.path.join(ROOT, "WIRE_R1.json"), WIRE_SHA256, 875)
        cls.oracle["verify_frozen_inputs"](contract_raw, reference_raw, wire_raw)
        cls.reference = json.loads(reference_raw.decode("utf-8", "strict"),
                                    object_pairs_hook=_pairs)
        cls.wire = json.loads(wire_raw.decode("utf-8", "strict"),
                              object_pairs_hook=_pairs)
        cls.reader = _load_source(os.path.join(ROOT, "verify_cleanup_staging_r1.py"),
                                  "_cleanup_reader_test_")

    def assert_rejected(self, report, case):
        with self.assertRaises(self.oracle["OracleError"]):
            self.oracle["verify_report"](report, case, self.reference, self.wire)

    def test_smoke_literals_accept(self):
        self.assertEqual(self.oracle["verify_report"](
            _make_report("smoke", self.reference, self.wire), "smoke",
            self.reference, self.wire), 2)

    def test_suite_literals_accept(self):
        self.assertEqual(self.oracle["verify_report"](
            _make_report("suite", self.reference, self.wire), "suite",
            self.reference, self.wire), 22)

    def test_wrong_cleanup_payload_rejected(self):
        report = _make_report("smoke", self.reference, self.wire)
        row = report["cases"][1]
        wrong = dict(self.reference["expectedCleanup"])
        wrong["cleanupNanos"] = 124
        row["payloadJson"] = _compact(wrong)
        self.assert_rejected(report, "smoke")

    def test_wrong_counter_rejected(self):
        report = _make_report("smoke", self.reference, self.wire)
        report["cases"][1]["units"] += 1
        self.assert_rejected(report, "smoke")

    def test_boolean_is_not_an_integer_counter(self):
        report = _make_report("suite", self.reference, self.wire)
        report["cases"][7]["units"] = True
        self.assert_rejected(report, "suite")

    def test_case_order_and_names_are_fixed(self):
        report = _make_report("suite", self.reference, self.wire)
        report["names"][0], report["names"][1] = report["names"][1], report["names"][0]
        self.assert_rejected(report, "suite")

    def test_duplicate_top_and_case_json_keys_are_rejected(self):
        parser = self.reader["strict_json_bytes"]
        with self.assertRaises(self.reader["ReaderFailure"]):
            parser(b'{"schema":"a","schema":"b"}')
        with self.assertRaises(self.reader["ReaderFailure"]):
            parser(b'{"case":{"units":1,"units":2}}')

    def test_duplicate_payload_key_is_rejected(self):
        report = _make_report("suite", self.reference, self.wire)
        report["cases"][18]["payloadJson"] = '{"schema":"a","schema":"b"}'
        self.assert_rejected(report, "suite")

    def test_clock_limits_and_cutoff_binding_are_enforced(self):
        report = _make_report("smoke", self.reference, self.wire)
        report["cases"][1]["totalElapsedNanos"] = 90000000001
        report["cases"][1]["operationNanos"] = 90000000001
        self.assert_rejected(report, "smoke")
        report = _make_report("suite", self.reference, self.wire)
        row = report["cases"][18]
        payload = json.loads(row["payloadJson"], object_pairs_hook=_pairs)
        payload["work"]["elapsedCutoffNanos"] = 11
        row["payloadJson"] = _compact(payload)
        self.assert_rejected(report, "suite")

    def test_parent_construction_count_is_exact(self):
        report = _make_report("smoke", self.reference, self.wire)
        report["parentConstructions"] = 1
        self.assert_rejected(report, "smoke")

    def test_r7_work_value_and_field_order_are_independent(self):
        report = _make_report("suite", self.reference, self.wire)
        row = report["cases"][18]
        payload = json.loads(row["payloadJson"], object_pairs_hook=_pairs)
        self.assertEqual(row["priorGroups"], 1)
        self.assertEqual(payload["work"]["groupsBeforeExport"], 2)
        self.assertEqual(payload["work"]["wrapperGroupsBeforeExport"], 1)
        self.assertEqual(payload["work"]["originalCaptureGroups"], 1)
        self.assertEqual(payload["work"]["metadataGroups"], 1)
        self.assertEqual(self.oracle["verify_report"](
            report, "suite", self.reference, self.wire), 22)
        payload["work"]["metadataCopiedEntries"] = 131
        row["payloadJson"] = _compact(payload)
        self.assert_rejected(report, "suite")

        report = _make_report("suite", self.reference, self.wire)
        row = report["cases"][18]
        payload = json.loads(row["payloadJson"], object_pairs_hook=_pairs)
        payload["work"] = dict(reversed(list(payload["work"].items())))
        row["payloadJson"] = _compact(payload)
        self.assert_rejected(report, "suite")

    def test_constructor_arm_mapping_is_fixed(self):
        report = _make_report("smoke", self.reference, self.wire)
        row = report["cases"][0]
        payload = json.loads(row["payloadJson"], object_pairs_hook=_pairs)
        payload["arms"]["null"], payload["arms"]["observe"] = (
            payload["arms"]["observe"], payload["arms"]["null"])
        row["payloadJson"] = _compact(payload)
        self.assert_rejected(report, "smoke")
        report = _make_report("suite", self.reference, self.wire)
        row = report["cases"][20]
        payload = json.loads(row["payloadJson"], object_pairs_hook=_pairs)
        self.assertIs(payload["arms"]["null"]["cleanup"][
            "adapterSessionAliasDropped"], False)
        self.assertIs(payload["cleanup"]["nullArmAliasesDropped"], False)
        self.assertEqual(self.oracle["verify_report"](
            report, "suite", self.reference, self.wire), 22)
        payload["arms"]["null"]["cleanup"]["adapterSessionAliasDropped"] = True
        row["payloadJson"] = _compact(payload)
        self.assert_rejected(report, "suite")

    def test_below_sum_scalar_is_accepted_as_the_explicit_projection_scalar(self):
        report = _make_report("suite", self.reference, self.wire)
        standalone_payload = json.loads(report["cases"][3]["payloadJson"],
                                        object_pairs_hook=_pairs)
        self.assertEqual(self.reference["rawDurationSum"], 71)
        self.assertEqual(standalone_payload["cleanupNanos"], 5)
        handoff_payload = json.loads(report["cases"][19]["payloadJson"],
                                     object_pairs_hook=_pairs)
        self.assertEqual(handoff_payload["cleanup"]["cleanupNanos"], 5)
        self.assertEqual(handoff_payload["work"]["cleanupNanos"], 5)
        self.assertEqual(self.oracle["verify_report"](
            report, "suite", self.reference, self.wire), 22)

    def test_rejection_preserves_slot_and_never_finishes_after_failure(self):
        report = _make_report("suite", self.reference, self.wire)
        report["cases"][8]["bodyUnchanged"] = False
        self.assert_rejected(report, "suite")
        report = _make_report("suite", self.reference, self.wire)
        report["cases"][8]["finishAfterFailure"] = True
        self.assert_rejected(report, "suite")

        report = _make_report("suite", self.reference, self.wire)
        downstream = report["cases"][21]
        self.assertEqual(downstream["phase"], "r7_handoff")
        self.assertEqual(downstream["observedError"],
                         "IOException:R5_METADATA_SNAPSHOT")
        self.assertEqual((downstream["units"], downstream["copiedEntries"],
                          downstream["copyChunks"], downstream["groups"],
                          downstream["priorGroups"], downstream["groupUnits"],
                          downstream["maxGroupUnits"], downstream["helperUnits"],
                          downstream["helperCopies"], downstream["helperChunks"],
                          downstream["observedNestedGets"]),
                         (18576, 132, 19, 1, 0, 18576, 18576, 67, 11, 2, 27))
        self.assertFalse(downstream["bodyUnchanged"])
        self.assertTrue(downstream["cleanupInstalled"])
        self.assertTrue(downstream["discarded"])
        self.assertFalse(downstream["finishAfterFailure"])
        self.assertEqual(downstream["payloadJson"], "")
        self.assertEqual(downstream["canonicalBytes"], 0)
        self.assertEqual(self.oracle["verify_report"](
            report, "suite", self.reference, self.wire), 22)

        downstream["priorGroups"] = 1
        self.assert_rejected(report, "suite")

    def test_float_counter_and_nonfinite_json_are_rejected(self):
        parser = self.reader["strict_json_bytes"]
        with self.assertRaises(self.reader["ReaderFailure"]):
            parser(b'{"units":1.0}')
        with self.assertRaises(self.reader["ReaderFailure"]):
            parser(b'{"units":NaN}')

    def test_existing_windows_junction_is_rejected_before_missing_leaf(self):
        drive, _tail = os.path.splitdrive(ROOT)
        self.assertTrue(drive)
        junction = os.path.join(drive + os.sep, "Documents and Settings")
        self.assertTrue(os.path.lexists(junction))
        missing_path = os.path.join(
            junction, "__tsj_cleanup_oracle_missing_parent_r1__", "report.json")
        self.assertFalse(os.path.lexists(missing_path))
        with self.assertRaises(self.reader["ReaderFailure"]) as caught:
            self.reader["_check_path_components"](missing_path)
        self.assertEqual(caught.exception.code, "REPARSE_ANCESTOR")


if __name__ == "__main__":
    unittest.main()
