"""Independent, literal oracle for the cleanup-staging canary report.

This module consumes only the authenticated frozen reference and wire objects.
It never calls Java, current R7 helpers, or a canary producer.
"""

import copy
import hashlib
import json


class OracleError(ValueError):
    """A report did not match the frozen cleanup-staging contract."""


BODY_KEYS = (
    "schema", "mode", "status", "runId", "sourceManifestSha256",
    "wireContractSha256", "inputContextSha256", "qualifiedPrior",
    "originalCapture", "cohort", "arms", "work", "cleanup",
)
BODY_SCHEMA = "tsj-cohort-terminal-result-v2"
BODY_MODE = "OBSERVED_COHORT_TERMINAL_TELEMETRY"
BODY_STATUS = "COMPLETED_DIAGNOSTIC_ONLY"
BODY_WIRE_SHA256 = "1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed"
PHASES = frozenset((
    "pending_constructor", "cleanup_install", "guard", "pending_header",
    "r7_handoff",
))
MAX_REPORT_NANOS = 90000000000
MAX_GROUP_NANOS = 5000000000
CONTRACT_SHA256 = "212ac9daea16bafeae9feb397bfbfaafe9d752260dfe0d4a2346a364f7351443"
CONTRACT_BYTES = 7674
REFERENCE_SHA256 = "d5744cfe7118d1f480e88d3fabec06749c32cba1c84cd0cf5379307d670d549d"
REFERENCE_BYTES = 5782
WIRE_SHA256 = "3d0ba4ed424ae0251bfa7d151cd4da6a043f41b5183858d117906c3584ced375"
WIRE_BYTES = 875


def _require(condition, code):
    if not condition:
        raise OracleError(code)


def verify_frozen_inputs(contract_raw, reference_raw, wire_raw):
    """Recheck the exact frozen input bytes before decoding their protocol."""
    frozen = (
        (contract_raw, CONTRACT_SHA256, CONTRACT_BYTES),
        (reference_raw, REFERENCE_SHA256, REFERENCE_BYTES),
        (wire_raw, WIRE_SHA256, WIRE_BYTES),
    )
    for raw, expected_sha, expected_bytes in frozen:
        _require(type(raw) is bytes and len(raw) == expected_bytes and
                 hashlib.sha256(raw).hexdigest() == expected_sha,
                 "FROZEN_INPUT_PIN")


def _ordered_keys(value, expected, code):
    _require(type(value) is dict, code)
    _require(list(value.keys()) == list(expected), code)


def _integer(value, code):
    _require(type(value) is int, code)
    return value


def _compact_json(value):
    return json.dumps(value, ensure_ascii=True, allow_nan=False,
                      separators=(",", ":"))


def _pairs_no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise OracleError("PAYLOAD_DUPLICATE_KEY")
        result[key] = value
    return result


def _reject_float(_value):
    raise OracleError("PAYLOAD_FLOAT")


def _reject_constant(_value):
    raise OracleError("PAYLOAD_NONFINITE")


def _payload_object(payload):
    _require(type(payload) is str and payload != "", "PAYLOAD_TYPE")
    try:
        payload.encode("ascii", "strict")
        value = json.loads(payload, object_pairs_hook=_pairs_no_duplicates,
                           parse_float=_reject_float,
                           parse_constant=_reject_constant)
    except OracleError:
        raise
    except Exception:
        raise OracleError("PAYLOAD_JSON")
    _require(type(value) is dict, "PAYLOAD_ROOT")
    _require(_depth(value) <= 64, "PAYLOAD_DEPTH")
    return value


def _depth(value):
    if type(value) is dict:
        return 1 + max([_depth(k) for k in value.keys()] +
                       [_depth(v) for v in value.values()] + [0])
    if type(value) is list:
        return 1 + max([_depth(v) for v in value] + [0])
    return 0


def _cleanup_for(name, reference):
    cleanup = dict(reference["expectedCleanup"])
    if name == "null_alias_false":
        cleanup["nullArmAliasesDropped"] = False
    elif name == "observe_alias_false":
        cleanup["observeArmAliasesDropped"] = False
    elif name == "geometry_false":
        cleanup["sourceGeometryUnchanged"] = False
    elif name == "r7_handoff_alias_false":
        cleanup["nullArmAliasesDropped"] = False
    if name in ("scalar_below_sum", "r7_handoff_below_sum"):
        cleanup["cleanupNanos"] = 5
    elif name == "scalar_at_limit":
        cleanup["cleanupNanos"] = 120000000000
    return cleanup


def _case_kind(name):
    if name == "pending_constructor":
        return "constructor"
    if name in ("installed_true", "null_alias_false", "observe_alias_false",
                "geometry_false", "scalar_below_sum", "scalar_at_limit"):
        return "install"
    if name.startswith("r7_handoff_"):
        return "handoff"
    return "reject"


def _expected_work(name, reference, record, payload_work=None):
    cleanup_nanos = 5 if name == "r7_handoff_below_sum" else 123
    values = {
        "elapsedCutoffNanos": None,
        "cleanupNanos": cleanup_nanos,
        "groupsBeforeExport": 2,
        "wrapperGroupsBeforeExport": 1,
        "maxGroupNanosBeforeExport": record["maxGroupNanos"],
        "maximumUnitsPerGroupBeforeExport": 18576,
        "unitsBeforeExport": 18576,
        "setupUnits": 0,
        "setupGroups": 0,
        "setupCopiedEntries": 0,
        "setupCopyChunks": 0,
        "originalCaptureOwnerUnits": 0,
        "originalCaptureWrapperUnits": 0,
        "originalCaptureCallbackUnits": 0,
        "originalCaptureDiagnosticUnits": 0,
        "originalCaptureGroups": 1,
        "originalCaptureCopiedEntries": 0,
        "originalCaptureCopyChunks": 0,
        "nullOwnerUnits": 0,
        "nullWrapperUnits": 0,
        "nullObserverUnits": 0,
        "observeOwnerUnits": 0,
        "observeWrapperUnits": 0,
        "observeObserverUnits": 0,
        "observerUnits": 0,
        "meterUnits": 18576,
        "metadataUnits": 18576,
        "metadataGroups": 1,
        "metadataCopiedEntries": 132,
        "metadataCopyChunks": 19,
        "copiedEntries": 132,
        "copyChunks": 19,
        "copyChunkLimit": 4096,
        "copiedEntryLimit": 1048576,
    }
    if payload_work is not None:
        values["elapsedCutoffNanos"] = payload_work.get("elapsedCutoffNanos")
    _ordered_keys(values, reference["workKeys"], "REFERENCE_WORK_KEYS")
    return values


def _body_header(reference, qualified_prior, work, cleanup, arms=None):
    fixture = reference["fixture"]
    if arms is None:
        arms = fixture["arms"]
    return {
        "schema": BODY_SCHEMA,
        "mode": BODY_MODE,
        "status": BODY_STATUS,
        "runId": fixture["runId"],
        "sourceManifestSha256": fixture["sourceManifestSha256"],
        "wireContractSha256": BODY_WIRE_SHA256,
        "inputContextSha256": fixture["inputContextSha256"],
        "qualifiedPrior": qualified_prior,
        "originalCapture": fixture["originalCapture"],
        "cohort": fixture["cohort"],
        "arms": arms,
        "work": work,
        "cleanup": cleanup,
    }


def _expected_payload(name, reference, record):
    kind = _case_kind(name)
    cleanup = _cleanup_for(name, reference)
    if kind == "constructor":
        body = _body_header(reference, None, None, None)
        return _compact_json(body), 0
    if kind == "install":
        return _compact_json(cleanup), 0
    if kind == "handoff":
        parsed = _payload_object(record["payloadJson"])
        _ordered_keys(parsed, BODY_KEYS, "HANDOFF_BODY_KEYS")
        payload_work = parsed["work"]
        _ordered_keys(payload_work, reference["workKeys"], "HANDOFF_WORK_KEYS")
        cutoff = _integer(payload_work["elapsedCutoffNanos"], "HANDOFF_CUTOFF_TYPE")
        _require(0 < cutoff <= record["totalElapsedNanos"], "HANDOFF_CUTOFF_RANGE")
        work = _expected_work(name, reference, record, payload_work)
        arms = copy.deepcopy(reference["fixture"]["arms"])
        if name == "r7_handoff_alias_false":
            arms["null"]["cleanup"]["adapterSessionAliasDropped"] = False
        body = _body_header(reference, reference["fixture"]["qualifiedPrior"],
                            work, cleanup, arms)
        expected = _compact_json(body)
        return expected, len(expected.encode("utf-8")) + 1
    return "", 0


def _expected_counters(name):
    # Literal census from the frozen cleanup-staging contract.
    cases = {
        "pending_constructor": (14, 13, 1, 1, 0, 14, 14, 14, 13, 1, False, 0),
        "installed_true": (67, 11, 2, 1, 0, 67, 67, 67, 11, 2, False, 27),
        "null_alias_false": (67, 11, 2, 1, 0, 67, 67, 67, 11, 2, False, 27),
        "observe_alias_false": (67, 11, 2, 1, 0, 67, 67, 67, 11, 2, False, 27),
        "geometry_false": (67, 11, 2, 1, 0, 67, 67, 67, 11, 2, False, 27),
        "scalar_below_sum": (67, 11, 2, 1, 0, 67, 67, 67, 11, 2, False, 27),
        "scalar_at_limit": (67, 11, 2, 1, 0, 67, 67, 67, 11, 2, False, 27),
        "negative_scalar": (1, 0, 0, 0, 0, 1, 0, 1, 0, 0, True, 0),
        "over_limit_scalar": (1, 0, 0, 0, 0, 1, 0, 1, 0, 0, True, 0),
        "body_type": (2, 0, 0, 0, 0, 2, 0, 2, 0, 0, True, 0),
        "slot_missing": (3, 0, 0, 0, 0, 3, 0, 3, 0, 0, True, 0),
        "slot_occupied": (4, 0, 0, 0, 0, 4, 0, 4, 0, 0, True, 0),
        "child_view_missing": (10, 0, 0, 0, 0, 10, 0, 10, 0, 0, True, 4),
        "projection_read_error": (12, 0, 0, 0, 0, 12, 0, 12, 0, 0, True, 6),
        "projection_copy_guard": (53, 0, 0, 0, 0, 53, 0, 53, 0, 0, True, 27),
        "replacement_copy_guard": (65, 10, 1, 0, 0, 65, 0, 65, 10, 1, True, 27),
        "group_guard_before_read": (640, 0, 0, 640, 0, 1, 1, 0, 0, 0, False, 0),
        "write_group_guard": (33407, 11, 2, 640, 0, 32768, 32768, 66, 11, 2, False, 27),
        "null_arguments": (0, 0, 0, 0, 0, 0, 0, 0, 0, 0, False, 0),
        "pending_export_reject": (2, 0, 0, 0, 0, 2, 0, 2, 0, 0, True, 0),
        "r7_handoff_true": (18576, 132, 19, 1, 1, 18576, 18576, 67, 11, 2, False, 27),
        "r7_handoff_below_sum": (18576, 132, 19, 1, 1, 18576, 18576, 67, 11, 2, False, 27),
        "r7_handoff_alias_false": (18576, 132, 19, 1, 1, 18576, 18576, 67, 11, 2, False, 27),
        "r7_zero_credit_reject": (18576, 132, 19, 1, 0, 18576, 18576, 67, 11, 2, False, 27),
    }
    units, copies, chunks, groups, prior_groups, group_units, max_units, \
        helper_units, helper_copies, helper_chunks, meter_open, nested_gets = cases[name]
    return {
        "units": units,
        "copiedEntries": copies,
        "copyChunks": chunks,
        "groups": groups,
        "priorGroups": prior_groups,
        "groupUnits": group_units,
        "maxGroupUnits": max_units,
        "helperUnits": helper_units,
        "helperCopies": helper_copies,
        "helperChunks": helper_chunks,
        "meterOpen": meter_open,
        "observedNestedGets": nested_gets,
    }


def _expected_error(name):
    return {
        "negative_scalar": "IOException:METADATA_CLEANUP_SCALAR",
        "over_limit_scalar": "IOException:METADATA_CLEANUP_SCALAR",
        "body_type": "IOException:METADATA_CLEANUP_BODY",
        "slot_missing": "IOException:METADATA_CLEANUP_SLOT",
        "slot_occupied": "IOException:METADATA_CLEANUP_SLOT",
        "child_view_missing": "IOException:METADATA_CLEANUP_VIEW",
        "projection_read_error": "IllegalStateException:SYNTHETIC_VIEW_READ",
        "projection_copy_guard":
            "IOException:copy exceeds its prebounded chunk or aggregate allowance",
        "replacement_copy_guard":
            "IOException:copy exceeds its prebounded chunk or aggregate allowance",
        "group_guard_before_read":
            "IOException:native wrapper exceeded 640 measured groups",
        "write_group_guard":
            "IOException:native wrapper exceeded 640 measured groups",
        "null_arguments": "IOException:METADATA_ARGUMENTS",
        "pending_export_reject": "CodecFailure:BODY_ROOT_CONTAINER",
        "r7_zero_credit_reject": "IOException:R5_METADATA_SNAPSHOT",
    }.get(name, "")


def _check_case(record, name, reference, wire):
    _ordered_keys(record, wire["caseKeys"], "CASE_KEYS")
    _require(record["name"] == name, "CASE_NAME")
    kind = _case_kind(name)
    expected_error = _expected_error(name)
    rejected = kind == "reject"
    _require(record["status"] == "PASS", "CASE_STATUS")
    _require(record["reason"] == ("EXPECTED_REJECTION" if rejected else "OK"),
             "CASE_REASON")
    _require(record["observedError"] == expected_error, "CASE_ERROR")
    _require(type(record["phase"]) is str and record["phase"] in PHASES,
             "CASE_PHASE")
    _require(record["phase"].isascii() and len(record["phase"]) <= 64,
             "CASE_PHASE_LENGTH")
    if name == "r7_zero_credit_reject":
        _require(record["phase"] == "r7_handoff", "ZERO_CREDIT_PHASE")

    expected = _expected_counters(name)
    for field, value in expected.items():
        actual = record[field]
        if type(value) is bool:
            _require(type(actual) is bool and actual is value, "CASE_" + field.upper())
        else:
            _require(type(actual) is int and actual == value, "CASE_" + field.upper())
    _require(type(record["semanticDurationGets"]) is int and
             record["semanticDurationGets"] == 0, "SEMANTIC_DURATION_GETS")

    operation = _integer(record["operationNanos"], "OPERATION_CLOCK_TYPE")
    total = _integer(record["totalElapsedNanos"], "TOTAL_CLOCK_TYPE")
    max_group = _integer(record["maxGroupNanos"], "MAX_GROUP_CLOCK_TYPE")
    open_elapsed = _integer(record["openGroupElapsedNanos"], "OPEN_GROUP_CLOCK_TYPE")
    _require(0 < operation <= total <= MAX_REPORT_NANOS, "TOTAL_CLOCK_RANGE")
    _require(0 <= max_group <= MAX_GROUP_NANOS, "MAX_GROUP_CLOCK_RANGE")
    _require(0 <= open_elapsed <= min(MAX_GROUP_NANOS, total), "OPEN_GROUP_CLOCK_RANGE")
    if not expected["meterOpen"]:
        _require(open_elapsed == 0, "CLOSED_GROUP_CLOCK")

    downstream_r7_reject = name == "r7_zero_credit_reject"
    body_unchanged = False if not rejected or downstream_r7_reject else True
    cleanup_installed = kind in ("install", "handoff") or downstream_r7_reject
    _require(type(record["bodyUnchanged"]) is bool and
             record["bodyUnchanged"] is body_unchanged, "BODY_UNCHANGED")
    _require(type(record["inputsUnchanged"]) is bool and
             record["inputsUnchanged"] is True, "INPUTS_UNCHANGED")
    _require(type(record["cleanupInstalled"]) is bool and
             record["cleanupInstalled"] is cleanup_installed, "CLEANUP_INSTALLED")
    _require(type(record["discarded"]) is bool and record["discarded"] is rejected,
             "DISCARDED")
    _require(type(record["finishAfterFailure"]) is bool and
             record["finishAfterFailure"] is False, "FINISH_AFTER_FAILURE")

    payload, canonical_bytes = _expected_payload(name, reference, record)
    _require(type(record["payloadJson"]) is str and record["payloadJson"] == payload,
             "PAYLOAD_MISMATCH")
    _require(type(record["canonicalBytes"]) is int and
             record["canonicalBytes"] == canonical_bytes, "CANONICAL_BYTES")
    if kind == "handoff":
        _require(record["maxGroupNanos"] ==
                 _payload_object(record["payloadJson"])["work"]["maxGroupNanosBeforeExport"],
                 "HANDOFF_MAX_GROUP_BINDING")


def verify_report(report, case, reference, wire):
    """Validate one native report against the frozen independent oracle."""
    _ordered_keys(report, wire["topKeys"], "TOP_KEYS")
    expected_names = reference["smoke"] if case == "smoke" else reference["suite"]
    mode = "SMOKE" if case == "smoke" else "SUITE"
    _require(report["schema"] == wire["schema"], "SCHEMA")
    _require(report["mode"] == mode, "MODE")
    _require(report["status"] == "PASS", "REPORT_STATUS")
    _require(report["names"] == expected_names, "CASE_ORDER")
    _require(type(report["cases"]) is list and len(report["cases"]) == len(expected_names),
             "CASE_COUNT")
    _require(type(report["parentConstructions"]) is int and
             report["parentConstructions"] == len(expected_names), "PARENT_COUNT")
    _require(report["error"] == "", "REPORT_ERROR")
    for record, name in zip(report["cases"], expected_names):
        _check_case(record, name, reference, wire)
    return len(expected_names)
