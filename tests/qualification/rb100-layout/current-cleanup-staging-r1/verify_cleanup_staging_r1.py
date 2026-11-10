"""Authenticated, bounded reader for cleanup-staging native canary reports."""

import hashlib
import json
import os
import stat
import sys


CONTRACT_SHA256 = "212ac9daea16bafeae9feb397bfbfaafe9d752260dfe0d4a2346a364f7351443"
CONTRACT_BYTES = 7674
REFERENCE_SHA256 = "d5744cfe7118d1f480e88d3fabec06749c32cba1c84cd0cf5379307d670d549d"
REFERENCE_BYTES = 5782
WIRE_SHA256 = "3d0ba4ed424ae0251bfa7d151cd4da6a043f41b5183858d117906c3584ced375"
WIRE_BYTES = 875
REPORT_LIMIT = 1048576
SOURCE_LIMIT = 4194304
REPARSE_FLAG = 0x400


class ReaderFailure(Exception):
    def __init__(self, code):
        Exception.__init__(self, code)
        self.code = code


def _fail(code):
    raise ReaderFailure(code)


def _pairs_without_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            _fail("DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def _reject_float(_value):
    _fail("JSON_FLOAT")


def _reject_constant(_value):
    _fail("JSON_NONFINITE")


def strict_json_bytes(raw, depth_limit=64):
    """Decode strict UTF-8 JSON, preserving order and rejecting duplicate keys."""
    try:
        decoded = raw.decode("utf-8", "strict")
        value = json.loads(decoded, object_pairs_hook=_pairs_without_duplicates,
                           parse_float=_reject_float,
                           parse_constant=_reject_constant)
    except ReaderFailure:
        raise
    except Exception:
        _fail("INVALID_JSON")
    if _json_depth(value) > depth_limit:
        _fail("JSON_DEPTH")
    return value


def _json_depth(value):
    if type(value) is dict:
        return 1 + max([_json_depth(key) for key in value.keys()] +
                       [_json_depth(item) for item in value.values()] + [0])
    if type(value) is list:
        return 1 + max([_json_depth(item) for item in value] + [0])
    return 0


def _is_reparse(info):
    return (stat.S_ISLNK(info.st_mode) or
            bool(getattr(info, "st_file_attributes", 0) & REPARSE_FLAG))


def _absolute_components(path):
    if type(path) is not str or not path or len(path) > 32768:
        _fail("PATH_INVALID")
    absolute = os.path.abspath(path)
    drive, tail = os.path.splitdrive(absolute)
    if drive:
        current = drive + os.sep
        pieces = [piece for piece in tail.replace("\\", os.sep).split(os.sep) if piece]
    else:
        current = os.sep
        pieces = [piece for piece in absolute.split(os.sep) if piece]
    return absolute, current, pieces


def _check_path_components(path):
    absolute, current, pieces = _absolute_components(path)
    try:
        root_info = os.lstat(current)
    except Exception:
        _fail("PATH_UNAVAILABLE")
    if _is_reparse(root_info):
        _fail("REPARSE_ANCESTOR")
    if not stat.S_ISDIR(root_info.st_mode):
        _fail("PATH_NOT_DIRECTORY")
    missing = False
    final_info = root_info
    for index, piece in enumerate(pieces):
        current = os.path.join(current, piece)
        try:
            info = os.lstat(current)
        except FileNotFoundError:
            missing = True
            final_info = None
            continue
        except NotADirectoryError:
            _fail("PATH_NOT_DIRECTORY")
        except Exception:
            _fail("PATH_UNAVAILABLE")
        if _is_reparse(info):
            _fail("REPARSE_ANCESTOR")
        if index < len(pieces) - 1 and not stat.S_ISDIR(info.st_mode):
            _fail("PATH_NOT_DIRECTORY")
        final_info = info
    if missing or final_info is None:
        _fail("PATH_MISSING")
    return absolute, final_info


def _signature(info):
    return (info.st_dev, info.st_ino, info.st_size,
            getattr(info, "st_mtime_ns", int(info.st_mtime * 1000000000)),
            getattr(info, "st_ctime_ns", int(info.st_ctime * 1000000000)))


def _file_identity(info):
    # Windows path and handle ctime describe different timestamps on changed
    # files. Check each API's full signature across the read, and compare their
    # actual file identity, type, length, modification and birth times here.
    return (info.st_dev, info.st_ino, stat.S_IFMT(info.st_mode), info.st_size,
            getattr(info, "st_mtime_ns", int(info.st_mtime * 1000000000)),
            getattr(info, "st_birthtime_ns", None))


def _read_regular_file(path, byte_limit):
    absolute, path_before = _check_path_components(path)
    if not stat.S_ISREG(path_before.st_mode):
        _fail("PATH_NOT_REGULAR")
    if path_before.st_size < 0 or path_before.st_size > byte_limit:
        _fail("FILE_TOO_LARGE")
    path_signature = _signature(path_before)
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(absolute, flags)
    except Exception:
        _fail("FILE_OPEN_FAILED")
    try:
        before = os.fstat(descriptor)
        if _is_reparse(before) or not stat.S_ISREG(before.st_mode):
            _fail("PATH_NOT_REGULAR")
        if before.st_size < 0 or before.st_size > byte_limit:
            _fail("FILE_TOO_LARGE")
        if _file_identity(before) != _file_identity(path_before):
            _fail("FILE_CHANGED")
        signature = _signature(before)
        chunks = []
        remaining = byte_limit + 1
        while remaining > 0:
            try:
                part = os.read(descriptor, min(65536, remaining))
            except Exception:
                _fail("FILE_READ_FAILED")
            if not part:
                break
            chunks.append(part)
            remaining -= len(part)
        raw = b"".join(chunks)
        if len(raw) > byte_limit:
            _fail("FILE_TOO_LARGE")
        after = os.fstat(descriptor)
        if _is_reparse(after) or _signature(after) != signature:
            _fail("FILE_CHANGED")
    finally:
        try:
            os.close(descriptor)
        except Exception:
            _fail("FILE_CLOSE_FAILED")
    _absolute_after, path_after = _check_path_components(absolute)
    if (_is_reparse(path_after) or _signature(path_after) != path_signature or
            _file_identity(path_after) != _file_identity(before) or
            len(raw) != path_after.st_size):
        _fail("FILE_CHANGED")
    return raw


def _check_directory(path):
    _absolute, info = _check_path_components(path)
    if not stat.S_ISDIR(info.st_mode):
        _fail("REFERENCE_ROOT_INVALID")


def _read_pinned(root, name, sha256, byte_count, limit):
    raw = _read_regular_file(os.path.join(root, name), limit)
    digest = hashlib.sha256(raw).hexdigest()
    if len(raw) != byte_count or digest != sha256:
        _fail("REFERENCE_PIN_MISMATCH")
    return raw


def _load_oracle(root):
    pin_raw = _read_regular_file(os.path.join(root, "ROOT_ORACLE_SOURCE_R1.json"), 1048576)
    pin = strict_json_bytes(pin_raw, 4)
    if type(pin) is not dict or list(pin.keys()) != ["sha256", "bytes"]:
        _fail("ORACLE_PIN_FORMAT")
    source_sha = pin["sha256"]
    source_bytes = pin["bytes"]
    if (type(source_sha) is not str or len(source_sha) != 64 or
            any(character not in "0123456789abcdef" for character in source_sha) or
            type(source_bytes) is not int or source_bytes <= 0 or
            source_bytes > SOURCE_LIMIT):
        _fail("ORACLE_PIN_FORMAT")
    source_raw = _read_regular_file(os.path.join(root, "cleanup_oracle.py"), SOURCE_LIMIT)
    if (len(source_raw) != source_bytes or
            hashlib.sha256(source_raw).hexdigest() != source_sha):
        _fail("ORACLE_PIN_MISMATCH")
    try:
        code = compile(source_raw, "cleanup_oracle.py", "exec",
                       dont_inherit=True, optimize=0)
        namespace = {
            "__name__": "_cleanup_oracle_authenticated_",
            "__file__": "cleanup_oracle.py",
            "__builtins__": __builtins__,
        }
        exec(code, namespace, namespace)
    except Exception:
        _fail("ORACLE_LOAD_FAILED")
    verifier = namespace.get("verify_report")
    pin_verifier = namespace.get("verify_frozen_inputs")
    if not callable(verifier) or not callable(pin_verifier):
        _fail("ORACLE_LOAD_FAILED")
    return verifier, pin_verifier


def _load_protocol_inputs(root):
    # Authenticate all frozen inputs before decoding any JSON or report protocol.
    contract_raw = _read_pinned(root, "CONTRACT_R1.md", CONTRACT_SHA256,
                                CONTRACT_BYTES, SOURCE_LIMIT)
    reference_raw = _read_pinned(root, "REFERENCE_R1.json", REFERENCE_SHA256,
                                 REFERENCE_BYTES, REPORT_LIMIT)
    wire_raw = _read_pinned(root, "WIRE_R1.json", WIRE_SHA256, WIRE_BYTES, REPORT_LIMIT)
    return contract_raw, reference_raw, wire_raw


def _parse_arguments(argv):
    if len(argv) != 6:
        _fail("INVALID_ARGUMENTS")
    values = {}
    index = 0
    while index < len(argv):
        key = argv[index]
        value = argv[index + 1]
        if key not in ("--case", "--input-json", "--reference-root") or key in values:
            _fail("INVALID_ARGUMENTS")
        if value.startswith("--"):
            _fail("INVALID_ARGUMENTS")
        values[key] = value
        index += 2
    if set(values) != {"--case", "--input-json", "--reference-root"}:
        _fail("INVALID_ARGUMENTS")
    if values["--case"] not in ("smoke", "suite"):
        _fail("INVALID_ARGUMENTS")
    return values["--case"], values["--input-json"], values["--reference-root"]


def _emit(value):
    sys.stdout.write(json.dumps(value, ensure_ascii=True, allow_nan=False,
                                separators=(",", ":")) + "\n")


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]
    try:
        case, input_path, reference_root = _parse_arguments(argv)
        _check_directory(reference_root)
        contract_raw, reference_raw, wire_raw = _load_protocol_inputs(reference_root)
        oracle, oracle_pin_verifier = _load_oracle(reference_root)
        try:
            oracle_pin_verifier(contract_raw, reference_raw, wire_raw)
        except Exception:
            _fail("REFERENCE_PIN_MISMATCH")
        reference = strict_json_bytes(reference_raw, 64)
        wire = strict_json_bytes(wire_raw, 16)
        report_raw = _read_regular_file(input_path, REPORT_LIMIT)
        report = strict_json_bytes(report_raw, 64)
        try:
            case_count = oracle(report, case, reference, wire)
        except Exception:
            _fail("REPORT_REJECTED")
        _emit({
            "status": "PASS_INDEPENDENT_CLEANUP_STAGING_READER",
            "caseCount": case_count,
            "normalHostAcceptance": False,
            "genuineEnvelope": "UNSPENT_GATED",
        })
        return 0
    except ReaderFailure as failure:
        _emit({
            "status": "FAIL_INDEPENDENT_CLEANUP_STAGING_READER",
            "error": failure.code,
        })
        return 2
    except Exception:
        _emit({
            "status": "FAIL_INDEPENDENT_CLEANUP_STAGING_READER",
            "error": "INTERNAL_ERROR",
        })
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
