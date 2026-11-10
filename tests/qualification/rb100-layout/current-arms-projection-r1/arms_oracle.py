"""Independent verifier for the synthetic current-arms projection canary."""

import hashlib
import json
import os
import re
import stat


CONTRACT_SHA256 = "4c7f3220424643694c58ae42405e447ed18d0588b3708428e72cfe1b0419079a"
OWNERSHIP_SHA256 = "8a7a5b0dfe82d4e230adcd1d531e6942664afbd96563dc5d21f40167b41c3832"
METER_METHODS_SHA256 = "e22304cce46eb5674afb1f56c2a4e96b3d237e2953826cea01429581245bf52f"
GROUP_METHODS_SHA256 = "bff7f81f6ea019badc38dc3b48113db773e5b61da487b7610ad383a204d1a648"

SOURCE_PIN_NAME = "ROOT_ORACLE_SOURCE_R1.json"
SOURCE_PIN_STATUS = "ROOT_ACCEPTED_CURRENT_ARMS_ORACLE_SOURCE_BEFORE_EXECUTION"
SOURCE_NAMES = (
    "arms_oracle.py",
    "verify_arms_projection_r1.py",
    "test_arms_oracle.py",
    "ORACLE_NOTE_R1.md",
)
MAX_PIN_BYTES = 1_048_576
MAX_SOURCE_BYTES = 4_194_304
MAX_REPORT_BYTES = 1_048_576
MAX_PATH_LENGTH = 32_768
MAX_DEPTH = 64
MAX_VISITS = 1_000_000
PRODUCER_LIMIT_NS = 90_000_000_000
OPEN_GROUP_LIMIT_NS = 5_000_000_000

SMOKE_CASES = (
    "arms_transfer",
    "arms_ownership_disjoint",
)
SUITE_CASES = (
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
    "operation",
    "error",
    "rejections",
    "payloadJson",
    "units",
    "copiedEntries",
    "copyChunks",
    "groups",
    "priorGroups",
    "groupUnits",
    "maxGroupUnits",
    "maxGroupNanos",
    "meterOpen",
    "finalOpen",
    "helperUnits",
    "helperCopies",
    "helperChunks",
    "identity",
    "inputUnchanged",
    "sourceReads",
    "discarded",
    "operationNanos",
    "totalElapsedNanos",
    "openGroupElapsedNanos",
)
IDENTITY_KEYS = (
    "outerFresh",
    "armMapsFresh",
    "fieldOrder",
    "idsPreserved",
    "childMapsPreserved",
    "inputMapsDisjoint",
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
    # On Windows path and handle ctime can expose different timestamps.
    # Keep each API's full ctime stability check in _signature, and use the
    # stable identity fields for the cross-API comparison.
    return (
        info.st_dev,
        info.st_ino,
        stat.S_IFMT(info.st_mode),
        info.st_size,
        _stat_time(info, "st_mtime_ns", "st_mtime"),
        _stat_time(info, "st_birthtime_ns", "st_birthtime"),
    )


def _component_signature(info):
    # Directory contents may change independently of this path identity.
    if stat.S_ISDIR(info.st_mode):
        return (info.st_dev, info.st_ino, stat.S_IFMT(info.st_mode),
                getattr(info, "st_birthtime_ns", None))
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
    size_code = "SOURCE_SIZE" if code == "SOURCE_PIN" else "INPUT_SIZE"
    if path_before.st_size < 0 or path_before.st_size > limit:
        reject(size_code)
    path_signature = _signature(path_before)
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0)
    flags |= getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(absolute, flags)
    except Exception:
        reject(code)

    close_failure = None
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
        os.path.join(root, "CONTRACT_R1.md"), MAX_SOURCE_BYTES,
        "REFERENCE_CONTRACT_PIN",
    )
    if _sha256(contract) != CONTRACT_SHA256:
        reject("REFERENCE_CONTRACT_PIN")
    ownership = read_bounded_file(
        os.path.join(root, "source", "CurrentBodyOwnership.java"),
        MAX_SOURCE_BYTES,
        "REFERENCE_OWNERSHIP_PIN",
    )
    if _sha256(ownership) != OWNERSHIP_SHA256:
        reject("REFERENCE_OWNERSHIP_PIN")
    meter = read_bounded_file(
        os.path.join(root, "source", "PARENT_METER_METHODS.txt"),
        MAX_SOURCE_BYTES,
        "REFERENCE_METER_PIN",
    )
    if _sha256(meter) != METER_METHODS_SHA256:
        reject("REFERENCE_METER_PIN")
    group = read_bounded_file(
        os.path.join(root, "source", "PARENT_GROUP_METHODS.txt"),
        MAX_SOURCE_BYTES,
        "REFERENCE_METER_PIN",
    )
    if _sha256(group) != GROUP_METHODS_SHA256:
        reject("REFERENCE_METER_PIN")


def verify_summary(summary, mode):
    if mode not in ("SMOKE", "SUITE"):
        reject("SUMMARY_MODE")
    _ordered_object(summary, TOP_KEYS, "SUMMARY_TOP_KEYS")
    if summary["schema"] != "TSJ_CURRENT_ARMS_PROJECTION_CANARY_R1":
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
        summary["parentConstructions"], len(names), len(names),
        "SUMMARY_PARENT_CONSTRUCTIONS",
    )

    for expected_name, case in zip(names, cases):
        _verify_case(case, expected_name)

    return {
        "status": "PASS_INDEPENDENT_CURRENT_ARMS_PROJECTION",
        "mode": mode,
        "caseCount": len(names),
        "parentConstructions": len(names),
        "normalHostAcceptance": False,
        "semanticAcceptance": False,
        "genuineEnvelope": "UNSPENT_GATED",
    }


_ARGUMENT_CASES = frozenset(
    name for name in SUITE_CASES if name.startswith("arms_argument_")
)
_NO_OUTPUT_CASES = _ARGUMENT_CASES | frozenset((
    "arms_copy_allowance_4",
    "arms_copy_allowance_5",
    "arms_copy_allowance_10",
    "arms_group_guard",
))
_REJECTION_CASES = _NO_OUTPUT_CASES | frozenset(("arms_alias_rejected",))
_OPEN_FINAL_CASES = frozenset((
    "arms_copy_allowance_5",
    "arms_copy_allowance_10",
    "arms_alias_rejected",
    "arms_success_open",
))


def _expected_payload(alias):
    observe_run = (
        '{"nullRun":"N_RUN"}' if alias else '{"observeRun":"O_RUN"}'
    )
    return (
        '{"null":{"armRole":"NULL","armId":"SYNTH_NULL",'
        '"freshRun":{"nullRun":"N_RUN"},'
        '"observation":{"nullObservation":"N_OBS"},'
        '"cleanup":{"nullCleanup":"N_CLEAN"}},'
        '"observe":{"armRole":"OBSERVE","armId":"SYNTH_OBSERVE",'
        '"freshRun":' + observe_run + ',"observation":'
        '{"observeObservation":"O_OBS"},"cleanup":'
        '{"observeCleanup":"O_CLEAN"}}}'
    )


def _expected_ledger(name):
    if name in _ARGUMENT_CASES or name == "arms_copy_allowance_4":
        return (0, 0, 0, 0, 0, 0)
    if name == "arms_group_guard":
        return (640, 0, 0, 640, 1, 1)
    if name == "arms_copy_allowance_5":
        return (6, 5, 1, 0, 6, 0)
    if name == "arms_copy_allowance_10":
        return (12, 10, 2, 0, 12, 0)
    if name == "arms_alias_rejected":
        return (187, 19, 10, 0, 187, 0)
    if name == "arms_ownership_disjoint":
        return (254, 21, 12, 1, 254, 254)
    if name == "arms_success_open":
        return (15, 12, 3, 0, 15, 0)
    return (15, 12, 3, 1, 15, 15)


def _expected_helper(name):
    if name in _ARGUMENT_CASES or name in (
        "arms_copy_allowance_4",
        "arms_group_guard",
    ):
        return (0, 0, 0)
    if name == "arms_copy_allowance_5":
        return (6, 5, 1)
    if name == "arms_copy_allowance_10":
        return (12, 10, 2)
    return (15, 12, 3)


def _verify_case(case, name):
    _ordered_object(case, CASE_KEYS, "SUMMARY_CASE_KEYS")
    if case["name"] != name:
        reject("SUMMARY_CASE_ORDER")
    if case["status"] != "PASS":
        reject("SUMMARY_CASE_VALUE")

    if name in _ARGUMENT_CASES:
        phase = "argument_guard"
    elif name.startswith("arms_copy_allowance_") or name == "arms_group_guard":
        phase = "projection_guard"
    elif name in ("arms_alias_rejected", "arms_ownership_disjoint"):
        phase = "ownership_guard"
    else:
        phase = "projection"
    rejected = name in _REJECTION_CASES
    expected_error = ""
    if name in _ARGUMENT_CASES:
        expected_error = "RESULT_PROJECTION_ARGUMENT"
    elif name in (
        "arms_copy_allowance_4",
        "arms_copy_allowance_5",
        "arms_copy_allowance_10",
    ):
        expected_error = (
            "copy exceeds its prebounded chunk or aggregate allowance"
        )
    elif name == "arms_group_guard":
        expected_error = "native wrapper exceeded 640 measured groups"
    elif name == "arms_alias_rejected":
        expected_error = "BODY_OWNERSHIP_ALIAS"

    static_values = (
        ("status", "PASS"),
        ("phase", phase),
        ("reason", "EXPECTED_REJECTION" if rejected else "OK"),
        ("operation", "CurrentResultProjections.arms"),
        ("error", expected_error),
    )
    for key, expected in static_values:
        if case[key] != expected:
            reject("SUMMARY_CASE_VALUE")

    _typed_int(case["rejections"], int(rejected), int(rejected), "SUMMARY_COUNTER")
    _typed_int(case["sourceReads"], 0, 0, "SUMMARY_RESULT_FACT")
    _typed_bool(case["inputUnchanged"], "SUMMARY_RESULT_FACT")
    if case["inputUnchanged"] is not True:
        reject("SUMMARY_RESULT_FACT")
    _typed_bool(case["discarded"], "SUMMARY_RESULT_FACT")
    if case["discarded"] is not rejected:
        reject("SUMMARY_RESULT_FACT")

    has_output = name not in _NO_OUTPUT_CASES
    expected_payload = _expected_payload(name == "arms_alias_rejected") if has_output else ""
    if type(case["payloadJson"]) is not str or case["payloadJson"] != expected_payload:
        reject("SUMMARY_PAYLOAD")

    expected_identity = has_output
    identity = case["identity"]
    _ordered_object(identity, IDENTITY_KEYS, "SUMMARY_IDENTITY")
    identity_values = (
        expected_identity,
        expected_identity,
        expected_identity,
        expected_identity,
        expected_identity,
        expected_identity and name != "arms_alias_rejected",
    )
    for key, expected in zip(IDENTITY_KEYS, identity_values):
        _typed_bool(identity[key], "SUMMARY_IDENTITY")
        if identity[key] is not expected:
            reject("SUMMARY_IDENTITY")

    ledger_keys = (
        "units",
        "copiedEntries",
        "copyChunks",
        "groups",
        "groupUnits",
        "maxGroupUnits",
    )
    expected_ledger = _expected_ledger(name)
    for key, expected in zip(ledger_keys, expected_ledger):
        _typed_int(case[key], expected, expected, "SUMMARY_COUNTER")
    _typed_int(case["priorGroups"], 0, 0, "SUMMARY_COUNTER")
    _typed_int(case["maxGroupNanos"], 0, OPEN_GROUP_LIMIT_NS, "SUMMARY_CLOCK")
    if case["groups"] == 0 and case["maxGroupNanos"] != 0:
        reject("SUMMARY_CLOCK")

    expected_helper = _expected_helper(name)
    for key, expected in zip(
        ("helperUnits", "helperCopies", "helperChunks"), expected_helper
    ):
        _typed_int(case[key], expected, expected, "SUMMARY_COUNTER")

    meter_open = name not in _ARGUMENT_CASES and name not in (
        "arms_copy_allowance_4",
        "arms_group_guard",
    )
    final_open = name in _OPEN_FINAL_CASES
    for key, expected in (("meterOpen", meter_open), ("finalOpen", final_open)):
        _typed_bool(case[key], "SUMMARY_RESULT_FACT")
        if case[key] is not expected:
            reject("SUMMARY_RESULT_FACT")

    _typed_int(case["operationNanos"], 0, PRODUCER_LIMIT_NS, "SUMMARY_CLOCK")
    _typed_int(case["totalElapsedNanos"], 0, PRODUCER_LIMIT_NS, "SUMMARY_CLOCK")
    _typed_int(
        case["openGroupElapsedNanos"], 0, OPEN_GROUP_LIMIT_NS, "SUMMARY_CLOCK"
    )
    if case["operationNanos"] > case["totalElapsedNanos"]:
        reject("SUMMARY_CLOCK")
    if not final_open and case["openGroupElapsedNanos"] != 0:
        reject("SUMMARY_CLOCK")
    if case["openGroupElapsedNanos"] > case["totalElapsedNanos"]:
        reject("SUMMARY_CLOCK")
