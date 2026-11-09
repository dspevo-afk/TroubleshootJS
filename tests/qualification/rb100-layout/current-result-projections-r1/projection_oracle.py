"""Independent reader for the frozen CurrentResultProjections R1 canary."""

import hashlib
import json
import os
import re
import stat
from pathlib import Path

CONTRACT_SHA256 = "24f40e39a9d52704a51aefbdf05170c25056447009c4b25605e45ad9de04fd4f"
REFERENCE_SHA256 = "bd0c6f081a6a9d11c47befe531debc87ddbccf02541542253b202a6debe491bc"
WIRE_SHA256 = "b5b20358c18806889b53c8394be627bd5078f0fd7c282e09e285f0f60a357b59"

REFERENCE_FILE = "REFERENCE_R1.json"
CONTRACT_FILE = "PROJECTION_CONTRACT_R1.md"
WIRE_FILE = "CANARY_WIRE_R1.json"
MAX_INPUT_BYTES = 1 * 1024 * 1024
MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_CLOCK_NANOS = 90 * 1_000_000_000
MAX_GROUP_NANOS = 5 * 1_000_000_000
MAX_SIGNED_LONG = (1 << 63) - 1

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
    "payloadJson",
    "observedError",
    "rejections",
    "units",
    "copiedEntries",
    "copyChunks",
    "groups",
    "priorGroups",
    "groupUnits",
    "maxGroupUnits",
    "maxGroupNanos",
    "meterOpen",
    "helperUnits",
    "helperCopies",
    "helperChunks",
    "operationNanos",
    "totalElapsedNanos",
    "openGroupElapsedNanos",
    "cleanupDisposition",
    "inputUnchanged",
    "listsTransferred",
    "externalContainersDetached",
    "noInputContainersRetained",
    "sourceReads",
    "sourceSemanticRejectionExpected",
    "discarded",
)

EXPECTED_SCHEMA = "TSJ_CURRENT_RESULT_PROJECTIONS_CANARY_R1"
OUTPUT_STATUS = "PASS_INDEPENDENT_RESULT_PROJECTIONS"
SMOKE_NAMES = ("cohort_transfer", "cleanup_true")
SUITE_NAMES = (
    "cohort_requested_mismatch",
    "cohort_duplicate_capture",
    "cohort_missing_capture",
    "cohort_incompatible_disposition",
    "cleanup_null_alias_false",
    "cleanup_observe_alias_false",
    "cleanup_geometry_false",
    "cleanup_effects_false",
    "cleanup_capture_context_false",
    "cleanup_null_context_false",
    "cleanup_observe_context_false",
    "cleanup_owner_false",
    "cleanup_scalar_below_sum",
    "argument_guards",
    "group_guard_before_read",
)

# Literal independent synthetic fixture: the supplied cohort order is explicit,
# and all remaining captured IDs retain their original order.
CAPTURED_IDS = tuple("SYNTH_N%02d" % index for index in range(60))
COHORT_IDS = ("SYNTH_N37", "SYNTH_N11")
REQUESTED_IDS = COHORT_IDS + tuple(
    net_id for net_id in CAPTURED_IDS if net_id not in COHORT_IDS
)
GEOMETRY_SHA = "a" * 64
EFFECTS_SHA = "b" * 64
DIFFERENT_SHA = "c" * 64
SOURCE_CLEANUP_DURATIONS = (100, 200, 300)

# (name, mode, units, copies, chunks, groups, priorGroups, meterOpen,
#  expectedError, rejections, sourceSemanticRejectionExpected, sourceReads)
_CASE_CENSUS = (
    ("cohort_transfer", "SMOKE", 260, 124, 63, 1, 0, False, "", 0, False, 0),
    ("cleanup_true", "SMOKE", 55, 10, 1, 1, 0, False, "", 0, False, 23),
    ("cohort_requested_mismatch", "SUITE", 255, 122, 62, 0, 0, True,
     "COHORT_REQUESTED_VECTOR", 1, False, 0),
    ("cohort_duplicate_capture", "SUITE", 137, 83, 23, 0, 0, True,
     "COHORT_VECTOR_DUPLICATE", 1, False, 0),
    ("cohort_missing_capture", "SUITE", 254, 122, 62, 0, 0, True,
     "COHORT_NOT_IN_CAPTURE", 1, False, 0),
    ("cohort_incompatible_disposition", "SUITE", 12, 0, 0, 0, 0, True,
     "COHORT_DISPOSITION", 1, False, 0),
    ("cleanup_null_alias_false", "SUITE", 55, 10, 1, 1, 0, False,
     "", 0, True, 23),
    ("cleanup_observe_alias_false", "SUITE", 55, 10, 1, 1, 0, False,
     "", 0, True, 23),
    ("cleanup_geometry_false", "SUITE", 55, 10, 1, 1, 0, False,
     "", 0, True, 23),
    ("cleanup_effects_false", "SUITE", 55, 10, 1, 1, 0, False,
     "", 0, True, 23),
    ("cleanup_capture_context_false", "SUITE", 55, 10, 1, 1, 0, False,
     "", 0, True, 23),
    ("cleanup_null_context_false", "SUITE", 55, 10, 1, 1, 0, False,
     "", 0, True, 23),
    ("cleanup_observe_context_false", "SUITE", 55, 10, 1, 1, 0, False,
     "", 0, True, 23),
    ("cleanup_owner_false", "SUITE", 55, 10, 1, 1, 0, False,
     "", 0, True, 23),
    ("cleanup_scalar_below_sum", "SUITE", 55, 10, 1, 1, 0, False,
     "", 0, True, 23),
    ("argument_guards", "SUITE", 0, 0, 0, 0, 0, False,
     "RESULT_PROJECTION_ARGUMENT", 3, False, 0),
    ("group_guard_before_read", "SUITE", 640, 0, 0, 640, 0, False,
     "native wrapper exceeded 640 measured groups", 1, False, 0),
)

_ALLOWED_REJECTION_CODES = frozenset((
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


class ProjectionReject(Exception):
    """A controlled, allowlisted oracle rejection."""

    def __init__(self, code):
        if code not in _ALLOWED_REJECTION_CODES:
            code = "SUMMARY_CASE_VALUE"
        Exception.__init__(self, code)
        self.code = code


def _reject(code):
    raise ProjectionReject(code)


def _is_reparse(st):
    attrs = getattr(st, "st_file_attributes", 0)
    reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    return stat.S_ISLNK(st.st_mode) or bool(attrs & reparse_flag)


def _absolute_checked_path(value, want_directory, code):
    try:
        raw = os.fspath(value)
        if not isinstance(raw, str) or not raw or len(raw) > 4096:
            _reject(code)
        absolute = os.path.abspath(raw)
        path = Path(absolute)
        # Inspect every component without resolving through a link or junction.
        parts = path.parts
        if not parts:
            _reject(code)
        current = type(path)(parts[0])
        for part in parts[1:]:
            current = current / part
            try:
                st = os.lstat(current)
            except OSError:
                _reject(code)
            if _is_reparse(st):
                _reject(code)
        try:
            final_st = os.lstat(path)
        except OSError:
            _reject(code)
        if _is_reparse(final_st):
            _reject(code)
        if want_directory:
            if not stat.S_ISDIR(final_st.st_mode):
                _reject(code)
        elif not stat.S_ISREG(final_st.st_mode):
            _reject(code)
        return path, final_st
    except ProjectionReject:
        raise
    except Exception:
        _reject(code)


def read_bounded_file(value, limit, code):
    path, initial = _absolute_checked_path(value, False, code)
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
        try:
            current = os.lstat(path)
        except OSError:
            _reject(code)
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
    except ProjectionReject:
        raise
    except OSError:
        _reject(code)
    finally:
        os.close(descriptor)


class _DuplicateKey(ValueError):
    pass


def _reject_duplicate_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise _DuplicateKey()
        result[key] = value
    return result


def _reject_constant(_value):
    raise ValueError("invalid JSON constant")


def decode_json_bytes(raw, code):
    try:
        source = raw.decode("utf-8", "strict")
        return json.loads(
            source,
            object_pairs_hook=_reject_duplicate_pairs,
            parse_constant=_reject_constant,
        )
    except Exception:
        _reject(code)


def _sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def _compact_json(value):
    try:
        return json.dumps(
            value,
            ensure_ascii=True,
            separators=(",", ":"),
            allow_nan=False,
        )
    except Exception:
        _reject("REFERENCE_FIXTURE")


def _cleanup_payload(case_name):
    capture_context = case_name != "cleanup_capture_context_false"
    null_context = case_name != "cleanup_null_context_false"
    observe_context = case_name != "cleanup_observe_context_false"
    output = {
        "schema": "tsj-cohort-terminal-cleanup-v1",
        "originalCaptureOwnersReleased": case_name != "cleanup_owner_false",
        "originalCaptureContextRestored": capture_context,
        "nullArmAliasesDropped": case_name != "cleanup_null_alias_false",
        "observeArmAliasesDropped": case_name != "cleanup_observe_alias_false",
        "sourceGeometryUnchanged": case_name != "cleanup_geometry_false",
        "effectsScope": "ORIGINAL_CAPTURE_COPY_ONLY",
        "originalCaptureCopyEffectsUnchanged": case_name != "cleanup_effects_false",
        "contextRestored": capture_context and null_context and observe_context,
        "cleanupNanos": 599 if case_name == "cleanup_scalar_below_sum" else sum(
            SOURCE_CLEANUP_DURATIONS
        ),
    }
    return output


def _build_expected_cases():
    expected = []
    for row in _CASE_CENSUS:
        (
            name,
            mode,
            units,
            copies,
            chunks,
            groups,
            prior_groups,
            meter_open,
            error,
            rejections,
            semantic_rejection,
            source_reads,
        ) = row
        if name == "cohort_transfer":
            payload = {
                "netIds": list(COHORT_IDS),
                "requestedNetIds": list(REQUESTED_IDS),
            }
            helper_units, helper_copies, helper_chunks = 5, 2, 1
        elif name.startswith("cleanup_"):
            payload = _cleanup_payload(name)
            helper_units, helper_copies, helper_chunks = 55, 10, 1
        else:
            payload = None
            helper_units, helper_copies, helper_chunks = 0, 0, 0

        payload_json = "" if payload is None else _compact_json(payload)
        transferred = name == "cohort_transfer"
        cleanup_output = name.startswith("cleanup_")
        if name == "group_guard_before_read":
            group_units = 1
            max_group_units = 1
        else:
            group_units = units
            max_group_units = units if payload is not None else 0
        expected.append({
            "name": name,
            "mode": mode,
            "payloadJson": payload_json,
            "units": units,
            "copiedEntries": copies,
            "copyChunks": chunks,
            "groups": groups,
            "priorGroups": prior_groups,
            "groupUnits": group_units,
            "maxGroupUnits": max_group_units,
            "meterOpen": meter_open,
            "helperUnits": helper_units,
            "helperCopies": helper_copies,
            "helperChunks": helper_chunks,
            "observedError": error,
            "rejections": rejections,
            "cleanupDisposition": (
                "METER_DISCARDED_NO_OS_RESOURCES"
                if error
                else "NO_OWNED_OS_RESOURCES"
            ),
            "inputUnchanged": True,
            "listsTransferred": transferred,
            "externalContainersDetached": transferred,
            "noInputContainersRetained": cleanup_output,
            "sourceReads": source_reads,
            "sourceSemanticRejectionExpected": semantic_rejection,
            "discarded": bool(error),
        })
    return tuple(expected)


_EXPECTED_CASES = _build_expected_cases()
_EXPECTED_BY_NAME = {case["name"]: case for case in _EXPECTED_CASES}


def _same(actual, expected):
    return type(actual) is type(expected) and actual == expected


def _require(condition, code):
    if not condition:
        _reject(code)


def _validate_reference_and_wire(reference, wire):
    _require(type(reference) is dict, "REFERENCE_FIXTURE")
    _require(
        reference.get("status")
        == "INDEPENDENT_LITERAL_SOURCE_CONTRACT_REFERENCE_BEFORE_IMPLEMENTATION",
        "REFERENCE_FIXTURE",
    )
    _require(reference.get("contractSha256") == CONTRACT_SHA256, "REFERENCE_FIXTURE")
    _require(reference.get("synthetic") is True, "REFERENCE_FIXTURE")
    _require(reference.get("actualPriorOrOriginalAuthority") is False, "REFERENCE_FIXTURE")
    _require(reference.get("capturedNetIds") == list(CAPTURED_IDS), "REFERENCE_FIXTURE")
    _require(reference.get("cohortIds") == list(COHORT_IDS), "REFERENCE_FIXTURE")
    _require(reference.get("requestedNetIds") == list(REQUESTED_IDS), "REFERENCE_FIXTURE")
    _require(reference.get("geometrySha") == GEOMETRY_SHA, "REFERENCE_FIXTURE")
    _require(reference.get("effectsSha") == EFFECTS_SHA, "REFERENCE_FIXTURE")
    _require(reference.get("differentSha") == DIFFERENT_SHA, "REFERENCE_FIXTURE")
    _require(
        reference.get("sourceCleanupDurations") == list(SOURCE_CLEANUP_DURATIONS),
        "REFERENCE_FIXTURE",
    )
    _require(reference.get("nativeClockValuesIncluded") is False, "REFERENCE_FIXTURE")
    reference_cases = reference.get("cases")
    _require(type(reference_cases) is list, "REFERENCE_FIXTURE")
    _require(len(reference_cases) == len(_EXPECTED_CASES), "REFERENCE_FIXTURE")

    reference_keys = (
        "name",
        "mode",
        "payloadJson",
        "units",
        "copiedEntries",
        "copyChunks",
        "helperUnits",
        "groups",
        "priorGroups",
        "meterOpen",
        "expectedError",
    )
    for actual, expected in zip(reference_cases, _EXPECTED_CASES):
        _require(type(actual) is dict, "REFERENCE_FIXTURE")
        _require(actual.get("name") == expected["name"], "REFERENCE_FIXTURE")
        for key in reference_keys:
            expected_key = (
                "observedError" if key == "expectedError" else key
            )
            if key == "helperUnits":
                value = expected["helperUnits"]
            else:
                value = expected[expected_key]
            _require(_same(actual.get(key), value), "REFERENCE_FIXTURE")
        _require(
            actual.get("sourceSemanticRejectionExpected", False)
            is expected["sourceSemanticRejectionExpected"],
            "REFERENCE_FIXTURE",
        )
        if "rejections" in actual:
            _require(
                _same(actual["rejections"], expected["rejections"]),
                "REFERENCE_FIXTURE",
            )
        if "sourceReads" in actual:
            _require(
                _same(actual["sourceReads"], expected["sourceReads"]),
                "REFERENCE_FIXTURE",
            )

    _require(type(wire) is dict, "WIRE_FIXTURE")
    _require(
        wire.get("schema") == "TSJ_CURRENT_RESULT_PROJECTIONS_CANARY_R1",
        "WIRE_FIXTURE",
    )
    _require(wire.get("topKeys") == list(TOP_KEYS), "WIRE_FIXTURE")
    _require(wire.get("caseKeys") == list(CASE_KEYS), "WIRE_FIXTURE")
    _require(wire.get("normalStatus") == "PASS", "WIRE_FIXTURE")
    _require(wire.get("normalPhase") == "complete", "WIRE_FIXTURE")
    _require(wire.get("normalReason") == "OK", "WIRE_FIXTURE")
    _require(wire.get("normalError") == "", "WIRE_FIXTURE")
    _require(
        wire.get("cohortHelperDelta")
        == {"units": 5, "copiedEntries": 2, "copyChunks": 1},
        "WIRE_FIXTURE",
    )
    _require(
        wire.get("cleanupHelperDelta")
        == {"units": 55, "copiedEntries": 10, "copyChunks": 1},
        "WIRE_FIXTURE",
    )
    _require(
        wire.get("normalCleanupDisposition") == "NO_OWNED_OS_RESOURCES",
        "WIRE_FIXTURE",
    )
    _require(
        wire.get("discardedCleanupDisposition")
        == "METER_DISCARDED_NO_OS_RESOURCES",
        "WIRE_FIXTURE",
    )
    _require(
        wire.get("casesIndependentReferenceSha256") == REFERENCE_SHA256,
        "WIRE_FIXTURE",
    )


def load_reference_root(reference_root):
    root, _ = _absolute_checked_path(reference_root, True, "REFERENCE_ROOT")
    contract_raw = read_bounded_file(
        root / CONTRACT_FILE, MAX_SOURCE_BYTES, "REFERENCE_CONTRACT_PIN"
    )
    reference_raw = read_bounded_file(
        root / REFERENCE_FILE, MAX_INPUT_BYTES, "REFERENCE_JSON_PIN"
    )
    wire_raw = read_bounded_file(
        root / WIRE_FILE, MAX_INPUT_BYTES, "REFERENCE_WIRE_PIN"
    )
    _require(_sha256(contract_raw) == CONTRACT_SHA256, "REFERENCE_FIXTURE")
    _require(_sha256(reference_raw) == REFERENCE_SHA256, "REFERENCE_FIXTURE")
    _require(_sha256(wire_raw) == WIRE_SHA256, "WIRE_FIXTURE")
    reference = decode_json_bytes(reference_raw, "REFERENCE_FIXTURE")
    wire = decode_json_bytes(wire_raw, "WIRE_FIXTURE")
    _validate_reference_and_wire(reference, wire)
    return {"reference": reference, "wire": wire}


def _expect_int(value, expected, counter_name):
    _require(type(value) is int, "SUMMARY_COUNTER")
    _require(0 <= value <= MAX_SIGNED_LONG, "SUMMARY_COUNTER")
    _require(value == expected, "SUMMARY_COUNTER")


def _expect_bool(value, expected):
    _require(type(value) is bool, "SUMMARY_CASE_VALUE")
    _require(value is expected, "SUMMARY_CASE_VALUE")


def _expect_string(value, expected):
    _require(type(value) is str, "SUMMARY_CASE_VALUE")
    _require(value == expected, "SUMMARY_CASE_VALUE")


def _validate_payload(payload_json, expected_json):
    _require(type(payload_json) is str, "SUMMARY_PAYLOAD")
    _require(payload_json == expected_json, "SUMMARY_PAYLOAD")
    if not expected_json:
        return
    payload = decode_json_bytes(payload_json.encode("utf-8"), "SUMMARY_PAYLOAD")
    expected = decode_json_bytes(expected_json.encode("utf-8"), "SUMMARY_PAYLOAD")
    _require(type(payload) is dict, "SUMMARY_PAYLOAD")
    _require(list(payload.keys()) == list(expected.keys()), "SUMMARY_PAYLOAD")
    _require(payload == expected, "SUMMARY_PAYLOAD")


def _validate_case(actual, expected):
    _require(type(actual) is dict, "SUMMARY_CASE_KEYS")
    _require(tuple(actual.keys()) == CASE_KEYS, "SUMMARY_CASE_KEYS")
    _expect_string(actual["name"], expected["name"])
    _expect_string(actual["status"], "PASS")
    _expect_string(actual["phase"], "complete")
    _expect_string(actual["reason"], "OK")
    _validate_payload(actual["payloadJson"], expected["payloadJson"])
    _expect_string(actual["observedError"], expected["observedError"])

    for key in (
        "rejections",
        "units",
        "copiedEntries",
        "copyChunks",
        "groups",
        "priorGroups",
        "groupUnits",
        "maxGroupUnits",
        "helperUnits",
        "helperCopies",
        "helperChunks",
        "sourceReads",
    ):
        _expect_int(actual[key], expected[key], key)

    for key in (
        "meterOpen",
        "inputUnchanged",
        "listsTransferred",
        "externalContainersDetached",
        "noInputContainersRetained",
        "sourceSemanticRejectionExpected",
        "discarded",
    ):
        _expect_bool(actual[key], expected[key])

    _expect_string(actual["cleanupDisposition"], expected["cleanupDisposition"])

    operation_nanos = actual["operationNanos"]
    _require(
        type(operation_nanos) is int
        and 0 <= operation_nanos <= MAX_SIGNED_LONG,
        "SUMMARY_CLOCK",
    )
    total_elapsed = actual["totalElapsedNanos"]
    _require(
        type(total_elapsed) is int
        and 0 <= total_elapsed <= MAX_CLOCK_NANOS,
        "SUMMARY_CLOCK",
    )
    max_group_nanos = actual["maxGroupNanos"]
    _require(
        type(max_group_nanos) is int
        and 0 <= max_group_nanos <= MAX_GROUP_NANOS,
        "SUMMARY_CLOCK",
    )
    open_group_elapsed = actual["openGroupElapsedNanos"]
    _require(
        type(open_group_elapsed) is int
        and 0 <= open_group_elapsed <= MAX_GROUP_NANOS,
        "SUMMARY_CLOCK",
    )
    if expected["meterOpen"] is False:
        _require(open_group_elapsed == 0, "SUMMARY_CLOCK")


def verify_summary(value, mode):
    """Validate one observed canary summary against independent R1 expectations."""
    _require(type(mode) is str and mode in ("SMOKE", "SUITE"), "SUMMARY_MODE")
    _require(type(value) is dict, "SUMMARY_TOP_KEYS")
    _require(tuple(value.keys()) == TOP_KEYS, "SUMMARY_TOP_KEYS")
    _expect_string(value["schema"], EXPECTED_SCHEMA)
    _expect_string(value["mode"], mode)
    _expect_string(value["status"], "PASS")
    _expect_string(value["error"], "")
    expected = tuple(
        case for case in _EXPECTED_CASES if case["mode"] == mode
    )
    names = [case["name"] for case in expected]
    _require(value["names"] == names, "SUMMARY_NAMES")
    _require(type(value["names"]) is list, "SUMMARY_NAMES")
    _expect_int(value["parentConstructions"], len(expected), "parentConstructions")
    actual_cases = value["cases"]
    _require(type(actual_cases) is list, "SUMMARY_CASE_ORDER")
    _require(len(actual_cases) == len(expected), "SUMMARY_CASE_ORDER")
    for actual, expected_case in zip(actual_cases, expected):
        _validate_case(actual, expected_case)

    payload_cases = sum(1 for case in expected if case["payloadJson"])
    expected_error_cases = sum(1 for case in expected if case["observedError"])
    semantic_rejection_cases = sum(
        1 for case in expected if case["sourceSemanticRejectionExpected"]
    )
    facts = {
        "payloadCases": payload_cases,
        "expectedErrorCases": expected_error_cases,
        "sourceSemanticRejectionCases": semantic_rejection_cases,
        "units": sum(case["units"] for case in expected),
        "copiedEntries": sum(case["copiedEntries"] for case in expected),
        "copyChunks": sum(case["copyChunks"] for case in expected),
        "groups": sum(case["groups"] for case in expected),
        "allInputsUnchanged": True,
    }
    return {
        "status": OUTPUT_STATUS,
        "mode": mode,
        "names": names,
        "caseCount": len(expected),
        "resultFacts": facts,
    }
