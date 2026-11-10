"""Authenticated CLI reader for the synthetic original-DTO meter canary."""

import hashlib
import json
import os
import re
import stat
import sys
from types import ModuleType


_REJECTIONS = frozenset((
    "CLI_ARGUMENTS",
    "RUNTIME_FLAGS",
    "SOURCE_PIN",
    "SOURCE_SIZE",
    "SOURCE_JSON",
    "SOURCE_LOAD",
    "REFERENCE_ROOT",
    "REFERENCE_SIZE",
    "REFERENCE_CONTRACT_PIN",
    "REFERENCE_FRAGMENT_PIN",
    "INPUT_PATH",
    "INPUT_SIZE",
    "INPUT_READ",
    "INPUT_CLOSE",
    "INPUT_CHANGED",
    "INPUT_JSON",
    "JSON_BOUNDS",
    "JSON_DEPTH",
    "SUMMARY_MODE",
    "SUMMARY_TOP_KEYS",
    "SUMMARY_SCHEMA",
    "SUMMARY_STATUS",
    "SUMMARY_NAMES",
    "SUMMARY_PARENT_CONSTRUCTIONS",
    "SUMMARY_CASE_ORDER",
    "SUMMARY_CASE_KEYS",
    "SUMMARY_CASE_VALUE",
    "SUMMARY_COUNTER",
    "SUMMARY_CLOCK",
    "SUMMARY_PAYLOAD",
    "SUMMARY_IDENTITY",
    "SUMMARY_RESULT_FACT",
    "ORACLE_FAILURE",
))
_SOURCE_PIN_NAME = "ROOT_ORACLE_SOURCE_R1.json"
_SOURCE_PIN_STATUS = "ROOT_ACCEPTED_ORIGINAL_DTO_METER_ORACLE_BEFORE_EXECUTION"
_SOURCE_NAMES = (
    "original_dto_meter_oracle.py",
    "verify_original_dto_meter_r1.py",
    "test_original_dto_meter_oracle.py",
    "ORACLE_NOTE_R1.md",
)
_MAX_PIN_BYTES = 1_048_576
_MAX_SOURCE_BYTES = 4_194_304
_MAX_REFERENCE_BYTES = 65_536
_MAX_PATH_LENGTH = 32_768


class _CliFailure(Exception):
    def __init__(self, code):
        self.code = code if code in _REJECTIONS else "ORACLE_FAILURE"
        Exception.__init__(self, self.code)


def _reject(code):
    raise _CliFailure(code)


def _parse_args(argv):
    if len(argv) != 6 or len(argv) % 2:
        _reject("CLI_ARGUMENTS")
    values = {}
    allowed = {"--case", "--input-json", "--reference-root"}
    for index in range(0, len(argv), 2):
        key, value = argv[index], argv[index + 1]
        if key not in allowed or key in values or not value or value.startswith("--"):
            _reject("CLI_ARGUMENTS")
        values[key] = value
    if set(values) != allowed or values["--case"] not in ("smoke", "suite"):
        _reject("CLI_ARGUMENTS")
    return values


def _emit(value):
    sys.stdout.write(json.dumps(
        value, ensure_ascii=True, allow_nan=False, separators=(",", ":")
    ))
    sys.stdout.write("\n")


def _authenticated_oracle(source_dir):
    pin_dir, _info, _components = _checked_components(
        source_dir, True, "SOURCE_PIN"
    )
    authenticated = _load_source_pin(pin_dir)
    raw = authenticated["original_dto_meter_oracle.py"]
    module = ModuleType("_authenticated_original_dto_meter_oracle_r1")
    module.__file__ = os.path.join(pin_dir, "original_dto_meter_oracle.py")
    code = compile(raw, module.__file__, "exec", dont_inherit=True, optimize=0)
    exec(code, module.__dict__)
    return module


def _is_reparse(info):
    return stat.S_ISLNK(info.st_mode) or bool(
        getattr(info, "st_file_attributes", 0) & 0x400
    )


def _stat_time(info, ns_name, seconds_name):
    value = getattr(info, ns_name, None)
    if value is not None:
        return value
    seconds = getattr(info, seconds_name, None)
    return None if seconds is None else int(seconds * 1_000_000_000)


def _signature(info):
    return (
        info.st_dev,
        info.st_ino,
        stat.S_IFMT(info.st_mode),
        info.st_size,
        _stat_time(info, "st_mtime_ns", "st_mtime"),
        _stat_time(info, "st_ctime_ns", "st_ctime"),
        _stat_time(info, "st_birthtime_ns", "st_birthtime"),
    )


def _identity(info):
    return (
        info.st_dev,
        info.st_ino,
        stat.S_IFMT(info.st_mode),
        info.st_size,
        _stat_time(info, "st_mtime_ns", "st_mtime"),
        _stat_time(info, "st_birthtime_ns", "st_birthtime"),
    )


def _component_signature(info):
    if stat.S_ISDIR(info.st_mode):
        return (
            info.st_dev,
            info.st_ino,
            stat.S_IFMT(info.st_mode),
            _stat_time(info, "st_birthtime_ns", "st_birthtime"),
        )
    return _signature(info)


def _checked_components(path, want_directory, code):
    try:
        raw = os.fspath(path)
        if type(raw) is not str or not raw or len(raw) > _MAX_PATH_LENGTH:
            _reject(code)
        absolute = os.path.abspath(raw)
        drive, tail = os.path.splitdrive(absolute)
        if drive:
            current = drive + os.sep
            pieces = [
                item for item in tail.replace("\\", os.sep).split(os.sep)
                if item
            ]
        else:
            current = os.sep
            pieces = [item for item in absolute.split(os.sep) if item]
        snapshots = []
        root = os.lstat(current)
        if _is_reparse(root) or not stat.S_ISDIR(root.st_mode):
            _reject(code)
        snapshots.append((current, _component_signature(root)))
        final = root
        for index, item in enumerate(pieces):
            current = os.path.join(current, item)
            info = os.lstat(current)
            if _is_reparse(info):
                _reject(code)
            is_final = index == len(pieces) - 1
            if not is_final and not stat.S_ISDIR(info.st_mode):
                _reject(code)
            snapshots.append((current, _component_signature(info)))
            final = info
        if want_directory:
            if not stat.S_ISDIR(final.st_mode):
                _reject(code)
        elif not stat.S_ISREG(final.st_mode):
            _reject(code)
        return absolute, final, tuple(snapshots)
    except _CliFailure:
        raise
    except Exception:
        _reject(code)


def _read_bounded(path, limit, code):
    absolute, initial, components = _checked_components(path, False, code)
    if code == "SOURCE_PIN":
        size_code = "SOURCE_SIZE"
    elif code.startswith("REFERENCE_"):
        size_code = "REFERENCE_SIZE"
    else:
        size_code = "INPUT_SIZE"
    if initial.st_size < 0 or initial.st_size > limit:
        _reject(size_code)
    path_signature = _signature(initial)
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0)
    flags |= getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(absolute, flags)
    except Exception:
        _reject(code)
    close_failed = False
    try:
        before = os.fstat(descriptor)
        if _is_reparse(before) or not stat.S_ISREG(before.st_mode):
            _reject(code)
        if before.st_size < 0 or before.st_size > limit:
            _reject(size_code)
        if _identity(before) != _identity(initial):
            _reject(code)
        handle_signature = _signature(before)
        chunks = []
        remaining = limit + 1
        while remaining:
            try:
                chunk = os.read(descriptor, min(65_536, remaining))
            except Exception:
                _reject("INPUT_READ")
            if not chunk:
                break
            chunks.append(chunk)
            remaining -= len(chunk)
        raw = b"".join(chunks)
        if len(raw) > limit:
            _reject(size_code)
        after = os.fstat(descriptor)
        if _is_reparse(after) or _signature(after) != handle_signature:
            _reject("INPUT_CHANGED")
    finally:
        try:
            os.close(descriptor)
        except Exception:
            close_failed = True
    if close_failed:
        _reject("INPUT_CLOSE")
    _absolute_after, final, after_components = _checked_components(
        absolute, False, code
    )
    if (
        components != after_components
        or _signature(final) != path_signature
        or _identity(final) != _identity(before)
        or len(raw) != final.st_size
    ):
        _reject("INPUT_CHANGED")
    return raw


class _DuplicateKey(ValueError):
    pass


def _pairs(items):
    result = {}
    for key, value in items:
        if key in result:
            raise _DuplicateKey()
        result[key] = value
    return result


def _decode_json(raw, code):
    try:
        return json.loads(
            raw.decode("utf-8", "strict"),
            object_pairs_hook=_pairs,
            parse_float=lambda _value: (_ for _ in ()).throw(ValueError()),
            parse_constant=lambda _value: (_ for _ in ()).throw(ValueError()),
        )
    except Exception:
        _reject(code)


def _load_source_pin(source_dir):
    raw = _read_bounded(
        os.path.join(source_dir, _SOURCE_PIN_NAME), _MAX_PIN_BYTES, "SOURCE_PIN"
    )
    pin = _decode_json(raw, "SOURCE_JSON")
    if type(pin) is not dict or tuple(pin.keys()) != ("status", "files"):
        _reject("SOURCE_PIN")
    if pin["status"] != _SOURCE_PIN_STATUS:
        _reject("SOURCE_PIN")
    files = pin["files"]
    if type(files) is not dict or set(files.keys()) != set(_SOURCE_NAMES):
        _reject("SOURCE_PIN")
    authenticated = {}
    for name in _SOURCE_NAMES:
        expected = files[name]
        if type(expected) is not str or re.fullmatch(r"[0-9a-f]{64}", expected) is None:
            _reject("SOURCE_PIN")
        source = _read_bounded(
            os.path.join(source_dir, name), _MAX_SOURCE_BYTES, "SOURCE_PIN"
        )
        if hashlib.sha256(source).hexdigest() != expected:
            _reject("SOURCE_PIN")
        authenticated[name] = source
    return authenticated


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
        source_dir = os.path.dirname(os.path.abspath(__file__))
        try:
            oracle = _authenticated_oracle(source_dir)
        except Exception as failed:
            code = getattr(failed, "code", None)
            _reject(code if code in _REJECTIONS else "SOURCE_LOAD")
        oracle.load_reference_root(args["--reference-root"])
        raw = oracle.read_bounded_file(
            args["--input-json"], oracle.MAX_REPORT_BYTES, "INPUT_PATH"
        )
        summary = oracle.decode_json_bytes(raw, "INPUT_JSON")
        result = oracle.verify_summary(summary, args["--case"].upper())
        _emit(result)
        return 0
    except Exception as error:
        code = getattr(error, "code", None)
        if code not in _REJECTIONS:
            code = "ORACLE_FAILURE"
        _emit({"status": "REJECTED", "code": code})
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
