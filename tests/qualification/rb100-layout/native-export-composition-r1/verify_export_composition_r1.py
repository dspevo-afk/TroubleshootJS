"""Bounded actual-report reader for the independent export composition oracle."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
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


class _CliFailure(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


class _ArgumentParser(argparse.ArgumentParser):
    def error(self, _message):
        raise _CliFailure("ORACLE_INPUT")


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
        raise _CliFailure("ORACLE_PATH") from None
    if os.path.normcase(str(absolute)) != os.path.normcase(str(resolved)):
        raise _CliFailure("ORACLE_PATH")
    for current in list(reversed(absolute.parents)) + [absolute]:
        try:
            item = os.lstat(str(current))
        except (OSError, RuntimeError, ValueError):
            raise _CliFailure("ORACLE_PATH") from None
        if stat.S_ISLNK(item.st_mode) or getattr(item, "st_file_attributes", 0) & REPARSE_POINT:
            raise _CliFailure("ORACLE_PATH")
    return absolute


def _read_bounded(path, cap, *, code="ORACLE_PATH"):
    try:
        safe = _safe_path(path)
        before = os.lstat(str(safe))
        before_snapshot = _snapshot(before)
        if not stat.S_ISREG(before.st_mode) or before.st_size <= 0 or before.st_size > cap:
            raise _CliFailure(code)
        with safe.open("rb") as stream:
            opened = _snapshot(os.fstat(stream.fileno()))
            if _handle_identity(before_snapshot) != _handle_identity(opened):
                raise _CliFailure(code)
            raw = stream.read(cap + 1)
            after_handle = _snapshot(os.fstat(stream.fileno()))
        if before_snapshot != _snapshot(os.lstat(str(safe))):
            raise _CliFailure(code)
        if _handle_identity(opened) != _handle_identity(after_handle):
            raise _CliFailure(code)
        if len(raw) != before.st_size or len(raw) > cap:
            raise _CliFailure(code)
        _safe_path(safe)
        return raw
    except _CliFailure:
        raise
    except (OSError, RuntimeError, ValueError):
        raise _CliFailure(code) from None


def _unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise _CliFailure("ORACLE_INPUT")
        result[key] = value
    return result


def _reject_json_number(_value):
    raise _CliFailure("ORACLE_INPUT")


def _strict_json(raw, code="ORACLE_INPUT"):
    try:
        return json.loads(
            raw.decode("utf-8"),
            object_pairs_hook=_unique_pairs,
            parse_float=_reject_json_number,
            parse_constant=_reject_json_number,
        )
    except _CliFailure:
        raise
    except (UnicodeError, ValueError, TypeError, RecursionError):
        raise _CliFailure(code) from None


def _script_directory():
    script_path = _safe_path(Path(os.path.abspath(__file__)))
    return script_path.parent


def _validate_release(directory):
    pin_path = directory / PIN_NAME
    pin_raw = _read_bounded(pin_path, PIN_CAP, code="ORACLE_SOURCE_PIN")
    pin = _strict_json(pin_raw, code="ORACLE_SOURCE_PIN")
    if type(pin) is not dict or set(pin) != {"status", "files"}:
        raise _CliFailure("ORACLE_SOURCE_PIN")
    if pin["status"] != PIN_STATUS or type(pin["files"]) is not list:
        raise _CliFailure("ORACLE_SOURCE_PIN")
    if len(pin["files"]) != len(PIN_ROLES):
        raise _CliFailure("ORACLE_SOURCE_PIN")

    pins = {}
    authenticated_sources = {}
    for expected_role, item in zip(PIN_ROLES, pin["files"]):
        if type(item) is not dict or set(item) != {"role", "sha256"}:
            raise _CliFailure("ORACLE_SOURCE_PIN")
        role = item["role"]
        digest = item["sha256"]
        if role != expected_role or type(role) is not str or type(digest) is not str:
            raise _CliFailure("ORACLE_SOURCE_PIN")
        if not SHA256_RE.fullmatch(digest) or role in pins:
            raise _CliFailure("ORACLE_SOURCE_PIN")
        pins[role] = digest

    if set(pins) != set(PIN_ROLES):
        raise _CliFailure("ORACLE_SOURCE_PIN")
    for role in PIN_ROLES:
        candidate = directory / role
        raw = _read_bounded(candidate, SOURCE_CAP, code="ORACLE_SOURCE_PIN")
        if hashlib.sha256(raw).hexdigest() != pins[role]:
            raise _CliFailure("ORACLE_SOURCE_PIN")
        authenticated_sources[role] = raw
    return directory / "export_composition_oracle.py", authenticated_sources[
        "export_composition_oracle.py"
    ]


def _load_pinned_oracle(oracle_path, authenticated_raw):
    """Execute the exact stable-read source bytes whose root pin was verified."""
    try:
        module = types.ModuleType("_accepted_export_composition_oracle_r1")
        module.__file__ = str(oracle_path)
        code = compile(
            authenticated_raw, str(oracle_path), "exec",
            dont_inherit=True, optimize=0,
        )
        exec(code, module.__dict__)
        if not callable(getattr(module, "verify_summary", None)):
            raise _CliFailure("ORACLE_SOURCE_PIN")
        return module
    except _CliFailure:
        raise
    except Exception:
        raise _CliFailure("ORACLE_SOURCE_PIN") from None


def _arguments(argv):
    parser = _ArgumentParser(add_help=True)
    parser.add_argument("--case", required=True, choices=("smoke", "suite"))
    parser.add_argument("--input-json", required=True)
    parser.add_argument("--expected-path-entries", required=True, type=int)
    return parser.parse_args(argv)


def main(argv=None):
    try:
        options = _arguments(argv)
        directory = _script_directory()
        oracle_path, oracle_raw = _validate_release(directory)
        oracle = _load_pinned_oracle(oracle_path, oracle_raw)
        report_raw = _read_bounded(options.input_json, REPORT_CAP)
        report = _strict_json(report_raw)
        summary = oracle.verify_summary(
            report, options.case, options.expected_path_entries
        )
        output = {
            "status": "PASS_INDEPENDENT_EXPORT_BYTES_AND_LEDGER",
            "mode": summary["mode"],
            "name": summary["name"],
            "expectedR": summary["expectedR"],
            "resultSha256": summary["resultSha256"],
            "expectedM": summary["expectedM"],
            "markerSha256": summary["markerSha256"],
            "fee": {
                "units": summary["expectedFeeUnits"],
                "copiedEntries": summary["expectedFeeCopiedEntries"],
                "copyChunks": summary["expectedFeeCopyChunks"],
            },
        }
        sys.stdout.write(json.dumps(output, ensure_ascii=True, separators=(",", ":")) + "\n")
        return 0
    except _CliFailure as failure:
        sys.stderr.write(failure.code + "\n")
        return 2
    except Exception as failure:
        code = getattr(failure, "code", "ORACLE_INPUT")
        allowed = {
            "ORACLE_SCHEMA", "ORACLE_PATH", "ORACLE_RESULT_BYTES",
            "ORACLE_MARKER_BYTES", "ORACLE_COUNTERS", "ORACLE_CLOCK",
            "ORACLE_INPUT", "ORACLE_SOURCE_PIN",
        }
        sys.stderr.write((code if type(code) is str and code in allowed else "ORACLE_INPUT") + "\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
