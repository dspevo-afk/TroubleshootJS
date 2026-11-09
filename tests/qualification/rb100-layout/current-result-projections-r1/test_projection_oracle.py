"""Pure synthetic tests for the authenticated R1 projection reader.

These cases are constructed only from the frozen root reference and wire
fixtures, with fixed clock values. They do not invoke Java or simulate native
execution.
"""

import hashlib
import json
import os
import pathlib
import re
import stat
import sys
import unittest
from types import ModuleType

PIN_NAME = "ROOT_ORACLE_SOURCE_R1.json"
PIN_STATUS = "ROOT_ACCEPTED_PROJECTION_ORACLE_SOURCE_BEFORE_EXECUTION"
SOURCE_NAMES = (
    "projection_oracle.py",
    "verify_projection_r1.py",
    "test_projection_oracle.py",
    "ORACLE_NOTE_R1.md",
)
MAX_PIN_BYTES = 1 * 1024 * 1024
MAX_SOURCE_BYTES = 4 * 1024 * 1024
_FIXED_CLOCK = 1_000
REFERENCE_ROOT = None
ORACLE = None
REFERENCE = None
WIRE = None


def _is_reparse(st):
    attrs = getattr(st, "st_file_attributes", 0)
    reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    return stat.S_ISLNK(st.st_mode) or bool(attrs & reparse_flag)


def _checked_path(value, want_directory):
    raw = os.fspath(value)
    if not isinstance(raw, str) or not raw or len(raw) > 4096:
        raise ValueError("unsafe test path")
    path = pathlib.Path(os.path.abspath(raw))
    parts = path.parts
    if not parts:
        raise ValueError("unsafe test path")
    current = pathlib.Path(parts[0])
    for part in parts[1:]:
        current = current / part
        st = os.lstat(current)
        if _is_reparse(st):
            raise ValueError("reparse test path")
    final = os.lstat(path)
    if _is_reparse(final):
        raise ValueError("reparse test path")
    if want_directory and not stat.S_ISDIR(final.st_mode):
        raise ValueError("not a directory")
    if not want_directory and not stat.S_ISREG(final.st_mode):
        raise ValueError("not a file")
    return path, final


def _read_bounded(path_value, limit):
    path, initial = _checked_path(path_value, False)
    if initial.st_size < 0 or initial.st_size > limit:
        raise ValueError("test input exceeds bound")
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0)
    flags |= getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(os.fspath(path), flags)
    try:
        opened = os.fstat(descriptor)
        if (
            not stat.S_ISREG(opened.st_mode)
            or _is_reparse(opened)
            or opened.st_size < 0
            or opened.st_size > limit
            or (opened.st_dev, opened.st_ino) != (initial.st_dev, initial.st_ino)
        ):
            raise ValueError("test input changed")
        current = os.lstat(path)
        if _is_reparse(current) or (current.st_dev, current.st_ino) != (
            opened.st_dev, opened.st_ino
        ):
            raise ValueError("test input changed")
        with os.fdopen(descriptor, "rb", closefd=False) as stream:
            raw = stream.read(limit + 1)
        after = os.fstat(descriptor)
        if len(raw) > limit or len(raw) != opened.st_size:
            raise ValueError("test input changed")
        if (after.st_dev, after.st_ino, after.st_size) != (
            opened.st_dev, opened.st_ino, opened.st_size
        ):
            raise ValueError("test input changed")
        return raw
    finally:
        os.close(descriptor)


class _DuplicateKey(ValueError):
    pass


def _pairs_without_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise _DuplicateKey()
        result[key] = value
    return result


def _decode_pin(raw):
    return json.loads(
        raw.decode("utf-8", "strict"),
        object_pairs_hook=_pairs_without_duplicates,
        parse_constant=lambda _value: (_ for _ in ()).throw(ValueError()),
    )


def _sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def _load_authenticated_oracle():
    source_file, _ = _checked_path(__file__, False)
    source_dir, _ = _checked_path(source_file.parent, True)
    pin_raw = _read_bounded(source_dir / PIN_NAME, MAX_PIN_BYTES)
    pin = _decode_pin(pin_raw)
    if type(pin) is not dict or tuple(pin.keys()) != ("status", "files"):
        raise ValueError("invalid root source pin")
    if pin.get("status") != PIN_STATUS:
        raise ValueError("unaccepted root source pin")
    files = pin.get("files")
    if type(files) is not dict or set(files.keys()) != set(SOURCE_NAMES):
        raise ValueError("incomplete root source pin")

    authenticated = {}
    for name in SOURCE_NAMES:
        expected_hash = files[name]
        if (
            type(expected_hash) is not str
            or re.fullmatch(r"[0-9a-f]{64}", expected_hash) is None
        ):
            raise ValueError("invalid source digest")
        raw = _read_bounded(source_dir / name, MAX_SOURCE_BYTES)
        if _sha256(raw) != expected_hash:
            raise ValueError("source digest mismatch")
        authenticated[name] = raw

    oracle_raw = authenticated["projection_oracle.py"]
    module = ModuleType("_authenticated_projection_oracle_tests_r1")
    module.__file__ = os.fspath(source_dir / "projection_oracle.py")
    code = compile(
        oracle_raw,
        module.__file__,
        "exec",
        dont_inherit=True,
        optimize=0,
    )
    exec(code, module.__dict__)
    return module


def _parse_test_args(argv):
    root = None
    rest = []
    index = 0
    while index < len(argv):
        token = argv[index]
        if token == "--reference-root":
            if root is not None or index + 1 >= len(argv):
                raise ValueError("invalid --reference-root")
            root = argv[index + 1]
            index += 2
        else:
            rest.append(token)
            index += 1
    if not root:
        raise ValueError("--reference-root is required")
    return root, rest


def _case_from_reference(reference_case, wire):
    name = reference_case["name"]
    error = reference_case["expectedError"]
    units = reference_case["units"]
    groups = reference_case["groups"]
    payload_json = reference_case["payloadJson"]
    helper_units = reference_case["helperUnits"]
    if name == "cohort_transfer":
        helper_copies, helper_chunks = 2, 1
    elif name.startswith("cleanup_"):
        helper_copies, helper_chunks = 10, 1
    else:
        helper_copies, helper_chunks = 0, 0
    group_units = 1 if name == "group_guard_before_read" else units
    max_group_units = (
        1 if name == "group_guard_before_read"
        else units if payload_json
        else 0
    )
    is_cleanup_payload = name.startswith("cleanup_")
    return {
        "name": name,
        "status": wire["normalStatus"],
        "phase": wire["normalPhase"],
        "reason": wire["normalReason"],
        "payloadJson": payload_json,
        "observedError": error,
        "rejections": reference_case.get("rejections", 1 if error else 0),
        "units": units,
        "copiedEntries": reference_case["copiedEntries"],
        "copyChunks": reference_case["copyChunks"],
        "groups": groups,
        "priorGroups": reference_case["priorGroups"],
        "groupUnits": group_units,
        "maxGroupUnits": max_group_units,
        "maxGroupNanos": _FIXED_CLOCK if groups else 0,
        "meterOpen": reference_case["meterOpen"],
        "helperUnits": helper_units,
        "helperCopies": helper_copies,
        "helperChunks": helper_chunks,
        "operationNanos": _FIXED_CLOCK,
        "totalElapsedNanos": _FIXED_CLOCK,
        "openGroupElapsedNanos": _FIXED_CLOCK if reference_case["meterOpen"] else 0,
        "cleanupDisposition": (
            wire["discardedCleanupDisposition"]
            if error
            else wire["normalCleanupDisposition"]
        ),
        "inputUnchanged": True,
        "listsTransferred": name == "cohort_transfer",
        "externalContainersDetached": name == "cohort_transfer",
        "noInputContainersRetained": is_cleanup_payload,
        "sourceReads": 23 if is_cleanup_payload else 0,
        "sourceSemanticRejectionExpected": reference_case.get(
            "sourceSemanticRejectionExpected", False
        ),
        "discarded": bool(error),
    }


def _synthetic_summary(mode):
    expected_mode = mode.upper()
    reference_cases = [
        case for case in REFERENCE["cases"] if case["mode"] == expected_mode
    ]
    names = [case["name"] for case in reference_cases]
    return {
        "schema": WIRE["schema"],
        "mode": expected_mode,
        "status": WIRE["normalStatus"],
        "names": names,
        "cases": [_case_from_reference(case, WIRE) for case in reference_cases],
        "parentConstructions": len(reference_cases),
        "error": WIRE["normalError"],
    }


class ProjectionOracleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        global ORACLE, REFERENCE, WIRE
        if (
            sys.flags.isolated != 1
            or sys.flags.dont_write_bytecode != 1
            or sys.flags.optimize != 0
        ):
            raise RuntimeError("tests require Python -I -B with optimize=0")
        ORACLE = _load_authenticated_oracle()
        pinned = ORACLE.load_reference_root(REFERENCE_ROOT)
        REFERENCE = pinned["reference"]
        WIRE = pinned["wire"]

    def assert_rejected(self, value, mode):
        with self.assertRaises(ORACLE.ProjectionReject):
            ORACLE.verify_summary(value, mode)

    def test_smoke_positive(self):
        result = ORACLE.verify_summary(_synthetic_summary("smoke"), "SMOKE")
        self.assertEqual(result["status"], "PASS_INDEPENDENT_RESULT_PROJECTIONS")
        self.assertEqual(result["caseCount"], 2)
        self.assertEqual(result["names"], ["cohort_transfer", "cleanup_true"])
        self.assertEqual(result["resultFacts"]["units"], 315)
        self.assertEqual(result["resultFacts"]["copiedEntries"], 134)
        self.assertEqual(result["resultFacts"]["copyChunks"], 64)

    def test_suite_positive(self):
        result = ORACLE.verify_summary(_synthetic_summary("suite"), "SUITE")
        self.assertEqual(result["caseCount"], 15)
        self.assertEqual(result["resultFacts"]["payloadCases"], 9)
        self.assertEqual(result["resultFacts"]["expectedErrorCases"], 6)
        self.assertEqual(result["resultFacts"]["sourceSemanticRejectionCases"], 9)
        self.assertEqual(result["resultFacts"]["units"], 1793)
        self.assertEqual(result["resultFacts"]["copiedEntries"], 417)
        self.assertEqual(result["resultFacts"]["copyChunks"], 156)
        self.assertEqual(result["resultFacts"]["groups"], 649)

    def test_payload_bytes_tamper_rejected(self):
        value = _synthetic_summary("smoke")
        value["cases"][1]["payloadJson"] = value["cases"][1]["payloadJson"].replace(
            '"cleanupNanos":600', '"cleanupNanos":601'
        )
        self.assert_rejected(value, "SMOKE")

    def test_counter_tamper_rejected(self):
        value = _synthetic_summary("suite")
        value["cases"][0]["copiedEntries"] += 1
        self.assert_rejected(value, "SUITE")

    def test_identity_tamper_rejected(self):
        value = _synthetic_summary("smoke")
        value["cases"][0]["name"] = "unexpected_case"
        self.assert_rejected(value, "SMOKE")
        value = _synthetic_summary("suite")
        value["cases"][0], value["cases"][1] = (
            value["cases"][1],
            value["cases"][0],
        )
        self.assert_rejected(value, "SUITE")

    def test_top_key_order_tamper_rejected(self):
        value = _synthetic_summary("smoke")
        pairs = list(value.items())
        pairs[0], pairs[1] = pairs[1], pairs[0]
        self.assert_rejected(dict(pairs), "SMOKE")

    def test_case_key_order_tamper_rejected(self):
        value = _synthetic_summary("smoke")
        case = value["cases"][0]
        pairs = list(case.items())
        pairs[0], pairs[1] = pairs[1], pairs[0]
        value["cases"][0] = dict(pairs)
        self.assert_rejected(value, "SMOKE")

    def test_boolean_counter_rejected_as_non_integer(self):
        value = _synthetic_summary("smoke")
        value["cases"][0]["units"] = True
        self.assert_rejected(value, "SMOKE")

    def test_negative_operation_duration_rejected(self):
        value = _synthetic_summary("smoke")
        value["cases"][0]["operationNanos"] = -1
        self.assert_rejected(value, "SMOKE")

    def test_operation_duration_has_no_incorrect_ninety_second_cap(self):
        value = _synthetic_summary("smoke")
        value["cases"][0]["operationNanos"] = 91_000_000_000
        result = ORACLE.verify_summary(value, "SMOKE")
        self.assertEqual(result["status"], "PASS_INDEPENDENT_RESULT_PROJECTIONS")

    def test_total_duration_ninety_second_cap(self):
        value = _synthetic_summary("smoke")
        value["cases"][0]["totalElapsedNanos"] = 90_000_000_001
        self.assert_rejected(value, "SMOKE")

    def test_group_duration_cap_and_closed_open_clock(self):
        value = _synthetic_summary("smoke")
        value["cases"][0]["maxGroupNanos"] = 5_000_000_001
        self.assert_rejected(value, "SMOKE")
        value = _synthetic_summary("smoke")
        value["cases"][0]["openGroupElapsedNanos"] = 1
        self.assert_rejected(value, "SMOKE")

    def test_pending_error_cannot_be_reported_as_not_discarded(self):
        value = _synthetic_summary("suite")
        value["cases"][0]["discarded"] = False
        self.assert_rejected(value, "SUITE")

    def test_rejection_count_tamper_rejected(self):
        value = _synthetic_summary("suite")
        value["cases"][13]["rejections"] = 2
        self.assert_rejected(value, "SUITE")

    def test_duplicate_json_key_rejected(self):
        with self.assertRaises(ORACLE.ProjectionReject):
            ORACLE.decode_json_bytes(b'{"case":1,"case":2}', "INPUT_JSON")




def main(argv=None):
    global REFERENCE_ROOT
    if argv is None:
        argv = sys.argv[1:]
    try:
        REFERENCE_ROOT, unittest_args = _parse_test_args(list(argv))
    except Exception:
        sys.stderr.write("REJECTED TEST_ARGUMENTS\n")
        return 2
    unittest.main(argv=[sys.argv[0]] + unittest_args)
    return 0


if __name__ == "__main__":
    main()
