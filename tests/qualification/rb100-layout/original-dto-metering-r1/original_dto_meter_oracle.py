"""Independent summary oracle for the synthetic original-DTO meter canary."""

import hashlib
import json
import os
import re
import stat


CONTRACT_SHA256 = "cd5dbf7b13b3061cdb5a5bbb1e153eb003e63235995649caf5e5d52e2d4f9b3e"
FRAGMENT_SHA256 = "16173e6b40e11efb2c517a99a424fadd53cfeeb549a170682f5ed473a1cdf702"

SOURCE_PIN_NAME = "ROOT_ORACLE_SOURCE_R1.json"
SOURCE_PIN_STATUS = "ROOT_ACCEPTED_ORIGINAL_DTO_METER_ORACLE_BEFORE_EXECUTION"
SOURCE_NAMES = (
    "original_dto_meter_oracle.py",
    "verify_original_dto_meter_r1.py",
    "test_original_dto_meter_oracle.py",
    "ORACLE_NOTE_R1.md",
)
MAX_PIN_BYTES = 1_048_576
MAX_SOURCE_BYTES = 4_194_304
MAX_REFERENCE_BYTES = 65_536
MAX_REPORT_BYTES = 1_048_576
MAX_PATH_LENGTH = 32_768
MAX_DEPTH = 64
MAX_VISITS = 1_000_000
PRODUCER_LIMIT_NS = 90_000_000_000
OPEN_GROUP_LIMIT_NS = 5_000_000_000
GROUP_GUARD_ERROR = "native wrapper exceeded 640 measured groups"

SMOKE_CASES = ("map_read", "synthetic_recipe")
SUITE_CASES = (
    "field_read",
    "map_null_source",
    "field_null_source",
    "map_group_guard",
    "field_group_guard",
    "invalid_capture_null",
    "invalid_capture_text",
    "invalid_capture_same_run",
)

TOP_KEYS = (
    "schema",
    "mode",
    "status",
    "names",
    "cases",
    "parentConstructions",
    "error",
)
CASE_KEYS = (
    "name",
    "status",
    "phase",
    "reason",
    "error",
    "units",
    "copiedEntries",
    "copyChunks",
    "groups",
    "priorGroups",
    "groupUnits",
    "maxGroupUnits",
    "maxGroupNanos",
    "open",
    "sourceReads",
    "identity",
    "payload",
    "operationNanos",
    "totalElapsedNanos",
    "openGroupElapsedNanos",
    "discarded",
)


class OracleError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


def reject(code):
    raise OracleError(code)


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
    # Windows path lstat and handle fstat can expose different ctime values.
    # Keep full per-API stability checks while comparing stable identity fields
    # across the two APIs.
    return (
        info.st_dev,
        info.st_ino,
        stat.S_IFMT(info.st_mode),
        info.st_size,
        _stat_time(info, "st_mtime_ns", "st_mtime"),
        _stat_time(info, "st_birthtime_ns", "st_birthtime"),
    )


def _component_signature(info):
    # A directory's content timestamps and size change when unrelated siblings
    # are created. Its identity is its device, inode, type and birth time.
    if stat.S_ISDIR(info.st_mode):
        return (
            info.st_dev,
            info.st_ino,
            stat.S_IFMT(info.st_mode),
            _stat_time(info, "st_birthtime_ns", "st_birthtime"),
        )
    return _signature(info)


def _checked_components(value, want_directory, code):
    try:
        raw = os.fspath(value)
        if type(raw) is not str or not raw or len(raw) > MAX_PATH_LENGTH:
            reject(code)
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
            reject(code)
        snapshots.append((current, _component_signature(root)))
        final = root
        for index, item in enumerate(pieces):
            current = os.path.join(current, item)
            info = os.lstat(current)
            if _is_reparse(info):
                reject(code)
            is_final = index == len(pieces) - 1
            if not is_final and not stat.S_ISDIR(info.st_mode):
                reject(code)
            snapshots.append((current, _component_signature(info)))
            final = info
        if want_directory:
            if not stat.S_ISDIR(final.st_mode):
                reject(code)
        elif not stat.S_ISREG(final.st_mode):
            reject(code)
        return absolute, final, tuple(snapshots)
    except OracleError:
        raise
    except Exception:
        reject(code)


def read_bounded_file(value, limit, code="INPUT_PATH"):
    absolute, path_before, components_before = _checked_components(
        value, False, code
    )
    if code == "SOURCE_PIN":
        size_code = "SOURCE_SIZE"
    elif code.startswith("REFERENCE_"):
        size_code = "REFERENCE_SIZE"
    else:
        size_code = "INPUT_SIZE"
    if path_before.st_size < 0 or path_before.st_size > limit:
        reject(size_code)
    path_signature = _signature(path_before)
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0)
    flags |= getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(absolute, flags)
    except Exception:
        reject(code)

    close_failure = False
    try:
        before = os.fstat(descriptor)
        if _is_reparse(before) or not stat.S_ISREG(before.st_mode):
            reject(code)
        if before.st_size < 0 or before.st_size > limit:
            reject(size_code)
        if _identity(before) != _identity(path_before):
            reject(code)
        handle_signature = _signature(before)
        chunks = []
        remaining = limit + 1
        while remaining:
            try:
                chunk = os.read(descriptor, min(65_536, remaining))
            except Exception:
                reject("INPUT_READ")
            if not chunk:
                break
            chunks.append(chunk)
            remaining -= len(chunk)
        raw = b"".join(chunks)
        if len(raw) > limit:
            reject(size_code)
        after = os.fstat(descriptor)
        if _is_reparse(after) or _signature(after) != handle_signature:
            reject("INPUT_CHANGED")
    finally:
        try:
            os.close(descriptor)
        except Exception:
            close_failure = True
    if close_failure:
        reject("INPUT_CLOSE")

    _absolute_after, path_after, components_after = _checked_components(
        absolute, False, code
    )
    if (
        components_after != components_before
        or _signature(path_after) != path_signature
        or _identity(path_after) != _identity(before)
        or len(raw) != path_after.st_size
    ):
        reject("INPUT_CHANGED")
    return raw


class _DuplicateKey(ValueError):
    pass


def _no_float(_value):
    raise ValueError("JSON_FLOAT")


def _no_constant(_value):
    raise ValueError("JSON_NONFINITE")


def _pairs_without_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise _DuplicateKey()
        result[key] = value
    return result


def decode_json_bytes(raw, code="INPUT_JSON"):
    try:
        value = json.loads(
            raw.decode("utf-8", "strict"),
            object_pairs_hook=_pairs_without_duplicates,
            parse_float=_no_float,
            parse_constant=_no_constant,
        )
    except Exception:
        reject(code)
    stack = [(value, 0)]
    visits = 0
    while stack:
        node, depth = stack.pop()
        visits += 1
        if visits > MAX_VISITS:
            reject("JSON_BOUNDS")
        if depth > MAX_DEPTH:
            reject("JSON_DEPTH")
        if type(node) is dict:
            for key, child in node.items():
                stack.append((key, depth + 1))
                stack.append((child, depth + 1))
        elif type(node) is list:
            for child in node:
                stack.append((child, depth + 1))
    return value


def _sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def _ordered_object(value, keys, code):
    if type(value) is not dict or tuple(value.keys()) != tuple(keys):
        reject(code)


def _typed_int(value, low, high, code):
    if type(value) is not int or value < low or value > high:
        reject(code)


def _typed_bool(value, code):
    if type(value) is not bool:
        reject(code)


def load_source_pin(source_dir):
    source_dir, _info, _components = _checked_components(
        source_dir, True, "SOURCE_PIN"
    )
    pin_raw = read_bounded_file(
        os.path.join(source_dir, SOURCE_PIN_NAME), MAX_PIN_BYTES, "SOURCE_PIN"
    )
    pin = decode_json_bytes(pin_raw, "SOURCE_JSON")
    _ordered_object(pin, ("status", "files"), "SOURCE_PIN")
    if pin["status"] != SOURCE_PIN_STATUS:
        reject("SOURCE_PIN")
    files = pin["files"]
    if type(files) is not dict or set(files.keys()) != set(SOURCE_NAMES):
        reject("SOURCE_PIN")
    authenticated = {}
    for name in SOURCE_NAMES:
        expected = files[name]
        if type(expected) is not str or re.fullmatch(r"[0-9a-f]{64}", expected) is None:
            reject("SOURCE_PIN")
        raw = read_bounded_file(
            os.path.join(source_dir, name), MAX_SOURCE_BYTES, "SOURCE_PIN"
        )
        if _sha256(raw) != expected:
            reject("SOURCE_PIN")
        authenticated[name] = raw
    return authenticated


def load_reference_root(reference_root):
    root, _info, _components = _checked_components(
        reference_root, True, "REFERENCE_ROOT"
    )
    contract = read_bounded_file(
        os.path.join(root, "CONTRACT_R1.md"),
        MAX_REFERENCE_BYTES,
        "REFERENCE_CONTRACT_PIN",
    )
    if _sha256(contract) != CONTRACT_SHA256:
        reject("REFERENCE_CONTRACT_PIN")
    fragment = read_bounded_file(
        os.path.join(root, "reference", "OriginalDtoMetering.java.fragment"),
        MAX_REFERENCE_BYTES,
        "REFERENCE_FRAGMENT_PIN",
    )
    if _sha256(fragment) != FRAGMENT_SHA256:
        reject("REFERENCE_FRAGMENT_PIN")


def verify_summary(summary, mode):
    if mode not in ("SMOKE", "SUITE"):
        reject("SUMMARY_MODE")
    _ordered_object(summary, TOP_KEYS, "SUMMARY_TOP_KEYS")
    if summary["schema"] != "TSJ_ORIGINAL_DTO_METER_CANARY_R1":
        reject("SUMMARY_SCHEMA")
    if summary["mode"] != mode:
        reject("SUMMARY_MODE")
    if summary["status"] != "PASS" or summary["error"] != "":
        reject("SUMMARY_STATUS")
    names = SMOKE_CASES if mode == "SMOKE" else SUITE_CASES
    if type(summary["names"]) is not list or summary["names"] != list(names):
        reject("SUMMARY_NAMES")
    cases = summary["cases"]
    if type(cases) is not list or len(cases) != len(names):
        reject("SUMMARY_CASE_ORDER")
    _typed_int(summary["parentConstructions"], 1, 1, "SUMMARY_PARENT_CONSTRUCTIONS")
    for name, case in zip(names, cases):
        _verify_case(case, name)
    return {
        "status": "PASS_INDEPENDENT_ORIGINAL_DTO_METER_COMPONENT",
        "mode": mode,
        "caseCount": len(names),
        "parentConstructions": 1,
        "actualPositiveOriginalDto": False,
        "normalHostAcceptance": False,
        "genuineEnvelope": "UNSPENT_GATED",
    }


def _expected_case(name):
    """Return independent literal expectations from the frozen contract."""
    if name == "map_read":
        return {
            "phase": "map",
            "reason": "OK",
            "error": "",
            "ledger": (1, 0, 0, 1, 1, 1),
            "maxGroupNanos": None,
            "open": False,
            "sourceReads": 1 if name == "map_read" else 0,
            "identity": True,
            "payload": "SYNTHETIC_METADATA",
            "discarded": False,
        }
    if name == "field_read":
        return {
            "phase": "field",
            "reason": "OK",
            "error": "",
            "ledger": (1, 0, 0, 1, 1, 1),
            "maxGroupNanos": None,
            "open": False,
            "sourceReads": 0,
            "identity": True,
            "payload": "SYNTHETIC_METADATA",
            "discarded": False,
        }
    if name == "synthetic_recipe":
        return {
            "phase": "recipe",
            "reason": "OK",
            "error": "",
            "ledger": (154, 105, 2, 1, 154, 154),
            "maxGroupNanos": None,
            "open": False,
            "sourceReads": 14,
            "identity": True,
            "payload": "SYNTHETIC_RECIPE_NOT_ORIGINAL_DTO",
            "discarded": False,
        }
    if name in ("map_null_source", "field_null_source"):
        return {
            "phase": "map" if name == "map_null_source" else "field",
            "reason": "EXPECTED_REJECTION",
            "error": (
                "ORIGINAL_DTO_SOURCE_MAP_MISSING"
                if name == "map_null_source" else "R7_SOURCE_MISSING"
            ),
            "ledger": (0, 0, 0, 0, 0, 0),
            "maxGroupNanos": 0,
            "open": False,
            "sourceReads": 0,
            "identity": False,
            "payload": "",
            "discarded": True,
        }
    if name in ("map_group_guard", "field_group_guard"):
        return {
            "phase": "guard",
            "reason": "EXPECTED_REJECTION",
            "error": GROUP_GUARD_ERROR,
            "ledger": (640, 0, 0, 640, 1, 1),
            "maxGroupNanos": None,
            "open": False,
            "sourceReads": 0,
            "identity": False,
            "payload": "",
            "discarded": True,
        }
    if name in ("invalid_capture_null", "invalid_capture_text"):
        return {
            "phase": "projection",
            "reason": "EXPECTED_REJECTION",
            "error": "CAPTURE_ID",
            "ledger": (2, 0, 0, 0, 2, 0),
            "maxGroupNanos": 0,
            "open": True,
            "sourceReads": 0,
            "identity": False,
            "payload": "",
            "discarded": True,
        }
    if name == "invalid_capture_same_run":
        return {
            "phase": "projection",
            "reason": "EXPECTED_REJECTION",
            "error": "CAPTURE_ID",
            "ledger": (3, 0, 0, 0, 3, 0),
            "maxGroupNanos": 0,
            "open": True,
            "sourceReads": 0,
            "identity": False,
            "payload": "",
            "discarded": True,
        }
    reject("SUMMARY_CASE_ORDER")


def _verify_case(case, name):
    _ordered_object(case, CASE_KEYS, "SUMMARY_CASE_KEYS")
    if case["name"] != name:
        reject("SUMMARY_CASE_ORDER")
    expected = _expected_case(name)
    if case["status"] != "PASS":
        reject("SUMMARY_CASE_VALUE")
    for key in ("phase", "reason", "error", "payload"):
        if case[key] != expected[key]:
            reject("SUMMARY_PAYLOAD" if key == "payload" else "SUMMARY_CASE_VALUE")

    units, copied, chunks, groups, group_units, max_units = expected["ledger"]
    for key, value in (
        ("units", units),
        ("copiedEntries", copied),
        ("copyChunks", chunks),
        ("groups", groups),
        ("priorGroups", 0),
        ("groupUnits", group_units),
        ("maxGroupUnits", max_units),
    ):
        _typed_int(case[key], value, value, "SUMMARY_COUNTER")
    max_group_nanos = case["maxGroupNanos"]
    _typed_int(max_group_nanos, 0, OPEN_GROUP_LIMIT_NS, "SUMMARY_CLOCK")
    if expected["maxGroupNanos"] is not None and max_group_nanos != expected["maxGroupNanos"]:
        reject("SUMMARY_CLOCK")
    if groups == 0 and max_group_nanos != 0:
        reject("SUMMARY_CLOCK")

    _typed_bool(case["open"], "SUMMARY_RESULT_FACT")
    if case["open"] is not expected["open"]:
        reject("SUMMARY_RESULT_FACT")
    _typed_int(case["sourceReads"], expected["sourceReads"], expected["sourceReads"], "SUMMARY_COUNTER")
    _typed_bool(case["identity"], "SUMMARY_IDENTITY")
    if case["identity"] is not expected["identity"]:
        reject("SUMMARY_IDENTITY")
    _typed_bool(case["discarded"], "SUMMARY_RESULT_FACT")
    if case["discarded"] is not expected["discarded"]:
        reject("SUMMARY_RESULT_FACT")

    _typed_int(case["operationNanos"], 0, PRODUCER_LIMIT_NS, "SUMMARY_CLOCK")
    _typed_int(case["totalElapsedNanos"], 0, PRODUCER_LIMIT_NS, "SUMMARY_CLOCK")
    _typed_int(case["openGroupElapsedNanos"], 0, OPEN_GROUP_LIMIT_NS, "SUMMARY_CLOCK")
    if case["operationNanos"] > case["totalElapsedNanos"]:
        reject("SUMMARY_CLOCK")
    if not expected["open"] and case["openGroupElapsedNanos"] != 0:
        reject("SUMMARY_CLOCK")
    if case["openGroupElapsedNanos"] > case["totalElapsedNanos"]:
        reject("SUMMARY_CLOCK")
