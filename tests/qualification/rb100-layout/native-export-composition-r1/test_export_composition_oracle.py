"""Source-only tests for the frozen synthetic export composition vector."""

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import unittest
import types


SOURCE_CAP = 4 * 1024 * 1024
REPORT_CAP = 1024 * 1024
PIN_CAP = 64 * 1024
REPARSE_POINT = 0x400
PIN_NAME = "ROOT_ORACLE_SOURCE_R1.json"
PIN_STATUS = "ROOT_ACCEPTED_ORACLE_SOURCE_BEFORE_EXECUTION"
PIN_ROLES = (
    "export_composition_oracle.py",
    "verify_export_composition_r1.py",
    "test_export_composition_oracle.py",
)
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z")
VECTOR_SHA256 = "1e0ea8682a7b7a90e85129475faf8a942eb5d84e587a992a084eb3c6f2afb97b"
RESULT_SHA256 = "ed6c27a049f278736ec97156bb335dba3563b4e5bdc581bb374c567f4b99eadc"
MARKER_SHA256 = "4dde7863dd0b8f58da47315e5dd67dafafa9426bf5aa93cb203a8d867b8cfc80"
RESULT_BYTES = 3168
MARKER_BYTES = 619


class _SourceFailure(Exception):
    pass


def _snapshot(item):
    return (
        item.st_dev,
        item.st_ino,
        item.st_mode,
        item.st_nlink,
        item.st_size,
        getattr(item, "st_mtime_ns", int(item.st_mtime * 1000000000)),
        getattr(item, "st_ctime_ns", int(item.st_ctime * 1000000000)),
        getattr(item, "st_file_attributes", 0),
    )


def _handle_identity(snapshot):
    return (
        snapshot[0], snapshot[1], stat.S_IFMT(snapshot[2]), snapshot[4],
        snapshot[5], snapshot[7],
    )


def _safe_path(path):
    path = Path(path)
    absolute = Path(os.path.abspath(str(path)))
    try:
        resolved = path.resolve(strict=True)
    except (OSError, RuntimeError, ValueError):
        raise _SourceFailure("unsafe path") from None
    if os.path.normcase(str(absolute)) != os.path.normcase(str(resolved)):
        raise _SourceFailure("path resolves through an alias")
    for current in list(reversed(absolute.parents)) + [absolute]:
        try:
            item = os.lstat(str(current))
        except (OSError, RuntimeError, ValueError):
            raise _SourceFailure("path ancestor is unavailable") from None
        if stat.S_ISLNK(item.st_mode) or getattr(item, "st_file_attributes", 0) & REPARSE_POINT:
            raise _SourceFailure("path contains a reparse point")
    return absolute


def _safe_directory(path):
    safe = _safe_path(path)
    try:
        if not stat.S_ISDIR(os.lstat(str(safe)).st_mode):
            raise _SourceFailure("reference root is not a directory")
    except OSError:
        raise _SourceFailure("reference root is unavailable") from None
    return safe


def _read_bounded(path, cap):
    try:
        safe = _safe_path(path)
        before = os.lstat(str(safe))
        before_snapshot = _snapshot(before)
        if not stat.S_ISREG(before.st_mode) or before.st_size <= 0 or before.st_size > cap:
            raise _SourceFailure("file is not within its bound")
        with safe.open("rb") as stream:
            opened = _snapshot(os.fstat(stream.fileno()))
            if _handle_identity(before_snapshot) != _handle_identity(opened):
                raise _SourceFailure("opened file identity changed")
            raw = stream.read(cap + 1)
            after_handle = _snapshot(os.fstat(stream.fileno()))
        if before_snapshot != _snapshot(os.lstat(str(safe))):
            raise _SourceFailure("file changed during read")
        if _handle_identity(opened) != _handle_identity(after_handle):
            raise _SourceFailure("opened file changed during read")
        if len(raw) != before.st_size or len(raw) > cap:
            raise _SourceFailure("file size changed during read")
        _safe_path(safe)
        return raw
    except _SourceFailure:
        raise
    except (OSError, RuntimeError, ValueError):
        raise _SourceFailure("bounded file read failed") from None


def _unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise _SourceFailure("duplicate JSON key")
        result[key] = value
    return result


def _reject_number(_value):
    raise _SourceFailure("JSON float or constant is not allowed")


def _strict_json(raw):
    try:
        return json.loads(
            raw.decode("utf-8"),
            object_pairs_hook=_unique_pairs,
            parse_float=_reject_number,
            parse_constant=_reject_number,
        )
    except _SourceFailure:
        raise
    except (UnicodeError, ValueError, TypeError, RecursionError):
        raise _SourceFailure("invalid strict JSON") from None


def _equal(actual, expected, label):
    if actual != expected:
        raise AssertionError(label)


def _script_directory():
    return _safe_path(Path(os.path.abspath(__file__))).parent


def _validate_release(directory):
    pin_raw = _read_bounded(directory / PIN_NAME, PIN_CAP)
    pin = _strict_json(pin_raw)
    if type(pin) is not dict or set(pin) != {"status", "files"}:
        raise _SourceFailure("root acceptance shape mismatch")
    if pin["status"] != PIN_STATUS or type(pin["files"]) is not list:
        raise _SourceFailure("root acceptance status mismatch")
    if len(pin["files"]) != len(PIN_ROLES):
        raise _SourceFailure("root acceptance role count mismatch")
    authenticated_sources = {}
    for expected_role, item in zip(PIN_ROLES, pin["files"]):
        if type(item) is not dict or set(item) != {"role", "sha256"}:
            raise _SourceFailure("root acceptance entry shape mismatch")
        role = item["role"]
        digest = item["sha256"]
        if role != expected_role or type(digest) is not str or not SHA256_RE.fullmatch(digest):
            raise _SourceFailure("root acceptance role or digest mismatch")
        raw = _read_bounded(directory / role, SOURCE_CAP)
        if hashlib.sha256(raw).hexdigest() != digest:
            raise _SourceFailure("candidate source pin mismatch")
        authenticated_sources[role] = raw
    return directory / "export_composition_oracle.py", authenticated_sources[
        "export_composition_oracle.py"
    ]


def _load_pinned_oracle(oracle_path, authenticated_raw):
    """Execute the exact stable-read source bytes whose root pin was verified."""
    try:
        module = types.ModuleType("_accepted_export_composition_oracle_tests_r1")
        module.__file__ = str(oracle_path)
        code = compile(
            authenticated_raw, str(oracle_path), "exec",
            dont_inherit=True, optimize=0,
        )
        exec(code, module.__dict__)
        if not callable(getattr(module, "verify_summary", None)):
            raise _SourceFailure("oracle API missing")
        return module
    except _SourceFailure:
        raise
    except Exception:
        raise _SourceFailure("oracle module load failed") from None


def _parse_test_arguments():
    parser = argparse.ArgumentParser(add_help=True)
    parser.add_argument("--reference-root", required=True)
    options, unittest_args = parser.parse_known_args()
    sys.argv = [sys.argv[0]] + unittest_args
    return options


try:
    _OPTIONS = _parse_test_arguments()
    _SCRIPT_DIRECTORY = _script_directory()
    _ORACLE_PATH, _ORACLE_RAW = _validate_release(_SCRIPT_DIRECTORY)
    ORACLE = _load_pinned_oracle(_ORACLE_PATH, _ORACLE_RAW)
    REFERENCE_ROOT = _safe_directory(_OPTIONS.reference_root)
except _SourceFailure as failure:
    sys.stderr.write("ORACLE_SOURCE_PIN\n")
    raise SystemExit(2) from failure


class ExportCompositionOracleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.vector_raw = _read_bounded(REFERENCE_ROOT / "REFERENCE_VECTOR_R1.json", REPORT_CAP)
        cls.result_file = _read_bounded(REFERENCE_ROOT / "REFERENCE_RESULT_R1.bin", 32768)
        cls.marker_file = _read_bounded(REFERENCE_ROOT / "REFERENCE_MARKER_R1.bin", 656)
        cls.vector = _strict_json(cls.vector_raw)
        _equal(hashlib.sha256(cls.vector_raw).hexdigest(), VECTOR_SHA256, "vector digest")
        _equal(len(cls.result_file), RESULT_BYTES, "result byte count")
        _equal(hashlib.sha256(cls.result_file).hexdigest(), RESULT_SHA256, "result digest")
        _equal(len(cls.marker_file), MARKER_BYTES, "marker byte count")
        _equal(hashlib.sha256(cls.marker_file).hexdigest(), MARKER_SHA256, "marker digest")
        _equal(type(cls.vector), dict, "vector object type")
        _equal(cls.vector["status"], "PRE_CODE_SYNTHETIC_CLOCK_REFERENCE_NOT_NATIVE_MEASUREMENT", "vector status")
        _equal(cls.vector["resultBytes"], RESULT_BYTES, "vector result length")
        _equal(cls.vector["resultSha256"], RESULT_SHA256, "vector result digest")
        _equal(cls.vector["markerBytes"], MARKER_BYTES, "vector marker length")
        _equal(cls.vector["markerSha256"], MARKER_SHA256, "vector marker digest")
        _equal(cls.vector["blocks"], 1, "vector block count")
        _equal(cls.vector["exportFee"], 8, "vector export fee")
        _equal(cls.vector["genuineEnvelope"], "UNSPENT_GATED", "vector envelope")
        cls.positive = cls.vector["positive"]
        cls.negative = cls.vector["negative"]
        _equal(bytes.fromhex(cls.positive["resultHex"]), cls.result_file, "positive result vector bytes")
        _equal(bytes.fromhex(cls.negative["resultHex"]), cls.result_file, "negative result vector bytes")
        _equal(bytes.fromhex(cls.positive["markerHex"]), cls.marker_file, "positive marker vector bytes")
        _equal(cls.negative["markerHex"], "", "negative marker must be absent")
        _equal(cls.vector["integerFields"], list(ORACLE.INTEGER_FIELDS), "integer field list")

    @staticmethod
    def _summary(mode, record):
        name = ORACLE.MODE_NAMES[mode]
        return {
            "mode": mode.upper(),
            "status": "PASS",
            "names": [name],
            "cases": [record],
        }

    def _assert_code(self, expected_code, callback):
        with self.assertRaises(ORACLE.OracleFailure) as raised:
            callback()
        self.assertEqual(raised.exception.code, expected_code)

    def test_frozen_positive_bytes_ledger_and_marker(self):
        summary = self._summary("smoke", self.positive)
        result = ORACLE.verify_summary(summary, "smoke", 11)
        self.assertEqual(result["expectedR"], RESULT_BYTES)
        self.assertEqual(result["resultSha256"], RESULT_SHA256)
        self.assertEqual(result["expectedM"], MARKER_BYTES)
        self.assertEqual(result["markerSha256"], MARKER_SHA256)
        self.assertEqual(result["expectedFeeUnits"], 8)
        self.assertEqual(result["expectedFeeCopiedEntries"], 7627 - 640)
        self.assertEqual(result["expectedFeeCopyChunks"], 4)
        expected_body = ORACLE.expected_body_bytes(
            11, 80000, 1000, 2000
        )
        self.assertEqual(expected_body, self.result_file)
        expected_marker = ORACLE.expected_marker_bytes(
            RESULT_SHA256, RESULT_BYTES, 90000, 3000,
            28836, 3000, 18821,
        )
        self.assertEqual(expected_marker, self.marker_file)

    def test_frozen_negative_ticket_failure_ledger(self):
        summary = self._summary("suite", self.negative)
        result = ORACLE.verify_summary(summary, "suite", 11)
        self.assertEqual(result["expectedR"], RESULT_BYTES)
        self.assertEqual(result["resultSha256"], RESULT_SHA256)
        self.assertEqual(result["expectedM"], 0)
        self.assertEqual(result["markerSha256"], "")
        self.assertEqual(result["expectedFeeUnits"], 6)
        self.assertEqual(result["expectedFeeCopiedEntries"], 2 * RESULT_BYTES)
        self.assertEqual(result["expectedFeeCopyChunks"], 2)

    def test_rejects_changed_result_bytes(self):
        changed = copy.deepcopy(self.positive)
        first = "0" if changed["resultHex"][0] != "0" else "1"
        changed["resultHex"] = first + changed["resultHex"][1:]
        self._assert_code(
            "ORACLE_RESULT_BYTES",
            lambda: ORACLE.verify_summary(self._summary("smoke", changed), "smoke", 11),
        )

    def test_rejects_changed_marker_bytes(self):
        changed = copy.deepcopy(self.positive)
        first = "0" if changed["markerHex"][0] != "0" else "1"
        changed["markerHex"] = first + changed["markerHex"][1:]
        self._assert_code(
            "ORACLE_MARKER_BYTES",
            lambda: ORACLE.verify_summary(self._summary("smoke", changed), "smoke", 11),
        )

    def test_rejects_counter_mutation(self):
        changed = copy.deepcopy(self.positive)
        changed["units"] += 1
        self._assert_code(
            "ORACLE_COUNTERS",
            lambda: ORACLE.verify_summary(self._summary("smoke", changed), "smoke", 11),
        )

    def test_rejects_negative_open_group_mutation(self):
        changed = copy.deepcopy(self.negative)
        changed["groupUnits"] += 1
        self._assert_code(
            "ORACLE_COUNTERS",
            lambda: ORACLE.verify_summary(self._summary("suite", changed), "suite", 11),
        )

    def test_rejects_clock_mutation(self):
        changed = copy.deepcopy(self.positive)
        changed["metricsElapsedNanos"] = changed["exportCutoffNanos"] - 1
        self._assert_code(
            "ORACLE_CLOCK",
            lambda: ORACLE.verify_summary(self._summary("smoke", changed), "smoke", 11),
        )

    def test_rejects_zero_cutoff(self):
        changed = copy.deepcopy(self.positive)
        changed["exportCutoffNanos"] = 0
        changed["beforeExportElapsedNanos"] = 0
        self._assert_code(
            "ORACLE_CLOCK",
            lambda: ORACLE.verify_summary(self._summary("smoke", changed), "smoke", 11),
        )

    def test_rejects_schema_mutation(self):
        changed = self._summary("smoke", copy.deepcopy(self.positive))
        changed["unexpected"] = True
        self._assert_code(
            "ORACLE_SCHEMA",
            lambda: ORACLE.verify_summary(changed, "smoke", 11),
        )

    def test_rejects_independent_path_mismatch(self):
        summary = self._summary("smoke", self.positive)
        self._assert_code(
            "ORACLE_PATH",
            lambda: ORACLE.verify_summary(summary, "smoke", 10),
        )

    def test_rejects_reported_path_mutation(self):
        changed = copy.deepcopy(self.positive)
        changed["pathEntries"] = 10
        self._assert_code(
            "ORACLE_PATH",
            lambda: ORACLE.verify_summary(self._summary("smoke", changed), "smoke", 11),
        )

    def test_rejects_path_outside_two_group_profile(self):
        changed = copy.deepcopy(self.positive)
        changed["pathEntries"] = 1406
        self._assert_code(
            "ORACLE_PATH",
            lambda: ORACLE.verify_summary(self._summary("smoke", changed), "smoke", 1406),
        )

    def test_literal_compact_ascii_escaping(self):
        value = {"quote": 'a"b', "backslash": "c\\d"}
        self.assertEqual(
            ORACLE.canonical_json_line(value),
            b'{"quote":"a\\"b","backslash":"c\\\\d"}\n',
        )


if __name__ == "__main__":
    unittest.main()
