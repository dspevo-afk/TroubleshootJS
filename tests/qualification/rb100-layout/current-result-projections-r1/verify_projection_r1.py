"""Authenticated command-line reader for the frozen R1 projection canary."""

import hashlib
import json
import os
import pathlib
import re
import stat
import sys
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
_REJECTION_CODES = frozenset((
    "REFERENCE_ROOT",
    "REFERENCE_CONTRACT_PIN",
    "REFERENCE_JSON_PIN",
    "REFERENCE_WIRE_PIN",
    "REFERENCE_FIXTURE",
    "WIRE_FIXTURE",
    "INPUT_PATH",
    "INPUT_SIZE",
    "INPUT_JSON",
    "SOURCE_PIN",
    "SOURCE_SIZE",
    "SOURCE_JSON",
    "SUMMARY_MODE",
    "SUMMARY_TOP_KEYS",
    "SUMMARY_CASE_KEYS",
    "SUMMARY_SCHEMA",
    "SUMMARY_STATUS",
    "SUMMARY_NAMES",
    "SUMMARY_PARENT_COUNT",
    "SUMMARY_CASE_ORDER",
    "SUMMARY_CASE_VALUE",
    "SUMMARY_COUNTER",
    "SUMMARY_CLOCK",
    "SUMMARY_PAYLOAD",
    "SUMMARY_RESULT_FACT",
))
_CLI_REJECTION_CODES = _REJECTION_CODES | frozenset((
    "CLI_ARGUMENTS",
    "RUNTIME_FLAGS",
    "ORACLE_FAILURE",
))


class _Reject(Exception):
    def __init__(self, code):
        self.code = code if code in _CLI_REJECTION_CODES else "ORACLE_FAILURE"
        Exception.__init__(self, self.code)


def _reject(code):
    raise _Reject(code)


def _is_reparse(st):
    attrs = getattr(st, "st_file_attributes", 0)
    reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    return stat.S_ISLNK(st.st_mode) or bool(attrs & reparse_flag)


def _checked_path(value, want_directory, code):
    try:
        raw = os.fspath(value)
        if not isinstance(raw, str) or not raw or len(raw) > 4096:
            _reject(code)
        path = pathlib.Path(os.path.abspath(raw))
        parts = path.parts
        if not parts:
            _reject(code)
        current = pathlib.Path(parts[0])
        for part in parts[1:]:
            current = current / part
            st = os.lstat(current)
            if _is_reparse(st):
                _reject(code)
        final = os.lstat(path)
        if _is_reparse(final):
            _reject(code)
        if want_directory:
            if not stat.S_ISDIR(final.st_mode):
                _reject(code)
        elif not stat.S_ISREG(final.st_mode):
            _reject(code)
        return path, final
    except _Reject:
        raise
    except Exception:
        _reject(code)


def _read_bounded(value, limit, code):
    path, initial = _checked_path(value, False, code)
    if initial.st_size < 0 or initial.st_size > limit:
        _reject("SOURCE_SIZE" if code == "SOURCE_PIN" else "INPUT_SIZE")
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0)
    flags |= getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(os.fspath(path), flags)
    except OSError:
        _reject(code)
    try:
        opened = os.fstat(descriptor)
        if not stat.S_ISREG(opened.st_mode) or _is_reparse(opened):
            _reject(code)
        if opened.st_size < 0 or opened.st_size > limit:
            _reject("SOURCE_SIZE" if code == "SOURCE_PIN" else "INPUT_SIZE")
        if (opened.st_dev, opened.st_ino) != (initial.st_dev, initial.st_ino):
            _reject(code)
        current = os.lstat(path)
        if _is_reparse(current) or (current.st_dev, current.st_ino) != (
            opened.st_dev, opened.st_ino
        ):
            _reject(code)
        with os.fdopen(descriptor, "rb", closefd=False) as stream:
            raw = stream.read(limit + 1)
        after = os.fstat(descriptor)
        if len(raw) > limit or len(raw) != opened.st_size:
            _reject("SOURCE_SIZE" if code == "SOURCE_PIN" else "INPUT_SIZE")
        if (after.st_dev, after.st_ino, after.st_size) != (
            opened.st_dev, opened.st_ino, opened.st_size
        ):
            _reject(code)
        return raw
    except _Reject:
        raise
    except OSError:
        _reject(code)
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


def _decode_json(raw, code):
    try:
        return json.loads(
            raw.decode("utf-8", "strict"),
            object_pairs_hook=_pairs_without_duplicates,
            parse_constant=lambda _constant: (_ for _ in ()).throw(ValueError()),
        )
    except Exception:
        _reject(code)


def _sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def _load_source_pin(source_dir):
    pin_raw = _read_bounded(
        source_dir / PIN_NAME, MAX_PIN_BYTES, "SOURCE_PIN"
    )
    pin = _decode_json(pin_raw, "SOURCE_JSON")
    if type(pin) is not dict or tuple(pin.keys()) != ("status", "files"):
        _reject("SOURCE_PIN")
    if pin.get("status") != PIN_STATUS:
        _reject("SOURCE_PIN")
    files = pin.get("files")
    if type(files) is not dict or set(files.keys()) != set(SOURCE_NAMES):
        _reject("SOURCE_PIN")
    source_bytes = {}
    for name in SOURCE_NAMES:
        expected_hash = files.get(name)
        if (
            type(expected_hash) is not str
            or re.fullmatch(r"[0-9a-f]{64}", expected_hash) is None
        ):
            _reject("SOURCE_PIN")
        raw = _read_bounded(
            source_dir / name, MAX_SOURCE_BYTES, "SOURCE_PIN"
        )
        if _sha256(raw) != expected_hash:
            _reject("SOURCE_PIN")
        source_bytes[name] = raw
    return source_bytes


def _load_authenticated_oracle(reference_root, cli_file=None):
    if cli_file is None:
        cli_file = __file__
    cli_path, _ = _checked_path(cli_file, False, "SOURCE_PIN")
    source_dir, _ = _checked_path(cli_path.parent, True, "SOURCE_PIN")
    authenticated = _load_source_pin(source_dir)
    # The oracle is compiled from the one authenticated byte buffer above.
    # No import machinery, candidate pycache, or source reread is involved.
    oracle_raw = authenticated["projection_oracle.py"]
    module = ModuleType("_authenticated_projection_oracle_r1")
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


def _parse_args(argv):
    if len(argv) != 6 or len(argv) % 2:
        _reject("CLI_ARGUMENTS")
    values = {}
    allowed = {"--case", "--input-json", "--reference-root"}
    for index in range(0, len(argv), 2):
        option = argv[index]
        value = argv[index + 1]
        if option not in allowed or option in values or not value:
            _reject("CLI_ARGUMENTS")
        values[option] = value
    if set(values) != allowed or values["--case"] not in ("smoke", "suite"):
        _reject("CLI_ARGUMENTS")
    return values


def _emit(value):
    sys.stdout.write(json.dumps(value, ensure_ascii=True, separators=(",", ":")))
    sys.stdout.write("\n")


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]
    try:
        if (
            sys.flags.isolated != 1
            or sys.flags.dont_write_bytecode != 1
            or sys.flags.optimize != 0
        ):
            _reject("RUNTIME_FLAGS")
        args = _parse_args(list(argv))
        oracle = _load_authenticated_oracle(args["--reference-root"])
        oracle.load_reference_root(args["--reference-root"])
        raw = oracle.read_bounded_file(
            args["--input-json"],
            oracle.MAX_INPUT_BYTES,
            "INPUT_PATH",
        )
        summary = oracle.decode_json_bytes(raw, "INPUT_JSON")
        result = oracle.verify_summary(summary, args["--case"].upper())
        _emit(result)
        return 0
    except Exception as error:
        code = getattr(error, "code", None)
        if code not in _CLI_REJECTION_CODES:
            code = "ORACLE_FAILURE"
        _emit({"status": "REJECTED", "code": code})
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
