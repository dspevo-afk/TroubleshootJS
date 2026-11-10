"""Independent literal oracle for the synthetic current-arm read canary."""

import hashlib
import json
import os
import re
import stat


CONTRACT_SHA256 = "7c1cac18553d9bd10e70cb7d40bc75b05acd84211132f20cb1c1886805fb3156"
FRAGMENT_SHA256 = "5f5a569b10f3579de8f700e84496c8762450531e986e732e338ec79133c60222"

SOURCE_PIN_NAME = "ROOT_ORACLE_SOURCE_R1.json"
SOURCE_PIN_STATUS = "ROOT_ACCEPTED_CURRENT_ARM_READ_ORACLE_BEFORE_EXECUTION"
SOURCE_NAMES = (
    "current_arm_read_oracle.py",
    "verify_current_arm_reads_r1.py",
    "test_current_arm_read_oracle.py",
    "ORACLE_NOTE_R1.md",
)

MAX_PIN_BYTES = 1_048_576
MAX_SOURCE_BYTES = 4_194_304
MAX_REFERENCE_BYTES = 65_536
MAX_REPORT_BYTES = 1_048_576
MAX_PATH_LENGTH = 32_768
MAX_DEPTH = 64
MAX_VISITS = 1_000_000
MAX_KEY_LENGTH = 1_000_000
MAX_OPERATION_NS = 90_000_000_000
MAX_TOTAL_NS = 90_000_000_000
MAX_GROUP_NS = 5_000_000_000
MAX_GROUP_UNITS = 32_768
MAX_GROUPS = 640
MAX_COPIED_ENTRIES = 1_048_576

SCHEMA = "TSJ_CURRENT_ARM_READ_METER_CANARY_R1"
GROUP_GUARD_ERROR = "native wrapper exceeded 640 measured groups"

SMOKE_CASES = (
    "list_read",
    "contains_first",
    "validator_no_failure_zero",
)
SUITE_CASES = (
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
    "mapReads",
    "listGetReads",
    "sizeReads",
    "iteratorConstructions",
    "hasNextReads",
    "nextReads",
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
    # Compare stable path/handle identity separately from each API's stability.
    return (
        info.st_dev,
        info.st_ino,
        stat.S_IFMT(info.st_mode),
        info.st_size,
        _stat_time(info, "st_mtime_ns", "st_mtime"),
        _stat_time(info, "st_birthtime_ns", "st_birthtime"),
    )


def _component_signature(info):
    # Directory content timestamps and size move when unrelated siblings appear.
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
                part for part in tail.replace("\\", os.sep).split(os.sep)
                if part
            ]
        else:
            current = os.sep
            pieces = [part for part in absolute.split(os.sep) if part]

        snapshots = []
        root = os.lstat(current)
        if _is_reparse(root) or not stat.S_ISDIR(root.st_mode):
            reject(code)
        snapshots.append((current, _component_signature(root)))
        final = root
        for index, part in enumerate(pieces):
            current = os.path.join(current, part)
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
                if type(key) is not str or len(key) > MAX_KEY_LENGTH:
                    reject("JSON_KEY_SIZE")
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
        if type(expected) is not str or re.fullmatch(
            r"[0-9a-f]{64}", expected
        ) is None:
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
        os.path.join(root, "reference", "CurrentArmReadMetering.java.fragment"),
        MAX_REFERENCE_BYTES,
        "REFERENCE_FRAGMENT_PIN",
    )
    if _sha256(fragment) != FRAGMENT_SHA256:
        reject("REFERENCE_FRAGMENT_PIN")


def _case_facts(name):
    """Literal expectations derived from Study path and canary input shapes."""
    facts = {
        "list_read": (
            "list", "OK", "", (1, 0, 0, 1, 0, 1, 1), False,
            (0, 1, 0, 0, 0, 0), True, "SYNTHETIC_METADATA", False,
        ),
        "contains_first": (
            "contains", "OK", "", (10, 0, 0, 1, 0, 10, 10), False,
            (0, 0, 1, 1, 1, 1), True, "SYNTHETIC_METADATA", False,
        ),
        "validator_no_failure_zero": (
            "validator", "OK", "",
            (18, 0, 0, 1, 0, 18, 18), False,
            (11, 0, 0, 0, 0, 0), True,
            "SYNTHETIC_VALIDATOR_METADATA", False,
        ),
        "contains_last": (
            "contains", "OK", "", (12, 0, 0, 1, 0, 12, 12), False,
            (0, 0, 1, 1, 3, 3), True, "SYNTHETIC_METADATA", False,
        ),
        "contains_missing": (
            "contains", "OK", "", (12, 0, 0, 1, 0, 12, 12), False,
            (0, 0, 1, 1, 4, 3), True, "SYNTHETIC_METADATA", False,
        ),
        "contains_empty": (
            "contains", "OK", "", (3, 0, 0, 1, 0, 3, 3), False,
            (0, 0, 1, 1, 1, 0), True, "SYNTHETIC_METADATA", False,
        ),
        "list_index_rejected": (
            "list", "EXPECTED_REJECTION", "java.lang.IndexOutOfBoundsException",
            (1, 0, 0, 0, 0, 1, 0), True,
            (0, 1, 0, 0, 0, 0), False, "", True,
        ),
        "list_group_guard": (
            "guard", "EXPECTED_REJECTION", GROUP_GUARD_ERROR,
            (640, 0, 0, 640, 0, 1, 1), False,
            (0, 0, 0, 0, 0, 0), False, "", True,
        ),
        "contains_group_guard": (
            "guard", "EXPECTED_REJECTION", GROUP_GUARD_ERROR,
            (640, 0, 0, 640, 0, 1, 1), False,
            (0, 0, 0, 0, 0, 0), False, "", True,
        ),
        "validator_schema_rejected": (
            "validator", "EXPECTED_REJECTION", "OBSERVER_V2_SCHEMA",
            (1, 0, 0, 0, 0, 1, 0), True,
            (1, 0, 0, 0, 0, 0), False, "", True,
        ),
        "validator_callback_limit": (
            "validator", "EXPECTED_REJECTION", "OBSERVER_CALLBACK_COUNT",
            (7, 0, 0, 0, 0, 7, 0), True,
            (6, 0, 0, 0, 0, 0), False, "", True,
        ),
        "validator_no_failure_callbacks": (
            "validator", "OK", "",
            (40, 0, 0, 1, 0, 40, 40), False,
            (15, 2, 3, 2, 2, 2), True,
            "SYNTHETIC_VALIDATOR_METADATA", False,
        ),
        "validator_quality_limit_zero": (
            "validator", "OK", "",
            (24, 0, 0, 1, 0, 24, 24), False,
            (13, 0, 0, 0, 0, 0), True,
            "SYNTHETIC_VALIDATOR_METADATA", False,
        ),
        "validator_first_non_found": (
            "validator", "OK", "",
            (61, 1, 1, 1, 0, 61, 61), False,
            (26, 3, 5, 3, 3, 3), True,
            "SYNTHETIC_VALIDATOR_METADATA", False,
        ),
        "validator_quality_net_callbacks": (
            "validator", "OK", "",
            (55, 1, 1, 1, 0, 55, 55), False,
            (22, 4, 4, 2, 2, 2), True,
            "SYNTHETIC_VALIDATOR_METADATA", False,
        ),
    }
    try:
        return facts[name]
    except KeyError:
        reject("SUMMARY_CASE_ORDER")


def verify_summary(summary, mode):
    if mode not in ("SMOKE", "SUITE"):
        reject("SUMMARY_MODE")
    _ordered_object(summary, TOP_KEYS, "SUMMARY_TOP_KEYS")
    if summary["schema"] != SCHEMA:
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
    _typed_int(
        summary["parentConstructions"], 1, 1, "SUMMARY_PARENT_CONSTRUCTIONS"
    )
    for name, case in zip(names, cases):
        _verify_case(case, name)
    return {
        "status": "PASS_INDEPENDENT_CURRENT_ARM_READ_COMPONENT",
        "mode": mode,
        "caseCount": len(names),
        "parentConstructions": 1,
        "actualPositiveOwnerResult": False,
        "normalHostAcceptance": False,
        "genuineEnvelope": "UNSPENT_GATED",
    }


def _verify_case(case, name):
    _ordered_object(case, CASE_KEYS, "SUMMARY_CASE_KEYS")
    if case["name"] != name:
        reject("SUMMARY_CASE_ORDER")
    if case["status"] != "PASS":
        reject("SUMMARY_CASE_VALUE")

    (
        phase,
        reason,
        error,
        ledger,
        expected_open,
        reads,
        expected_identity,
        payload,
        expected_discarded,
    ) = _case_facts(name)
    for key, expected in (
        ("phase", phase),
        ("reason", reason),
        ("error", error),
    ):
        if case[key] != expected:
            reject("SUMMARY_CASE_VALUE")
    if case["payload"] != payload:
        reject("SUMMARY_PAYLOAD")

    units, copies, chunks, groups, prior_groups, group_units, max_group_units = ledger
    for key, expected in (
        ("units", units),
        ("copiedEntries", copies),
        ("copyChunks", chunks),
        ("groups", groups),
        ("priorGroups", prior_groups),
        ("groupUnits", group_units),
        ("maxGroupUnits", max_group_units),
    ):
        _typed_int(case[key], expected, expected, "SUMMARY_COUNTER")
    if copies > MAX_COPIED_ENTRIES:
        reject("SUMMARY_COUNTER")
    _typed_int(case["maxGroupNanos"], 0, MAX_GROUP_NS, "SUMMARY_CLOCK")
    if groups == 0 and case["maxGroupNanos"] != 0:
        reject("SUMMARY_CLOCK")

    if type(case["open"]) is not bool:
        reject("SUMMARY_RESULT_FACT")
    if case["open"] is not expected_open:
        reject("SUMMARY_RESULT_FACT")
    read_keys = (
        "mapReads",
        "listGetReads",
        "sizeReads",
        "iteratorConstructions",
        "hasNextReads",
        "nextReads",
    )
    for key, expected in zip(read_keys, reads):
        _typed_int(case[key], expected, expected, "SUMMARY_COUNTER")

    if type(case["identity"]) is not bool:
        reject("SUMMARY_IDENTITY")
    if case["identity"] is not expected_identity:
        reject("SUMMARY_IDENTITY")
    if type(case["discarded"]) is not bool:
        reject("SUMMARY_RESULT_FACT")
    if case["discarded"] is not expected_discarded:
        reject("SUMMARY_RESULT_FACT")

    _typed_int(case["operationNanos"], 0, MAX_OPERATION_NS, "SUMMARY_CLOCK")
    _typed_int(case["totalElapsedNanos"], 0, MAX_TOTAL_NS, "SUMMARY_CLOCK")
    _typed_int(
        case["openGroupElapsedNanos"], 0, MAX_GROUP_NS, "SUMMARY_CLOCK"
    )
    if case["operationNanos"] > case["totalElapsedNanos"]:
        reject("SUMMARY_CLOCK")
    if not expected_open and case["openGroupElapsedNanos"] != 0:
        reject("SUMMARY_CLOCK")
    if case["openGroupElapsedNanos"] > case["totalElapsedNanos"]:
        reject("SUMMARY_CLOCK")

    if reason == "OK" and (expected_open or expected_discarded or not expected_identity):
        reject("SUMMARY_RESULT_FACT")
    if reason == "EXPECTED_REJECTION" and (
        not expected_open and groups != MAX_GROUPS or not expected_discarded or expected_identity
    ):
        reject("SUMMARY_RESULT_FACT")

    return True
