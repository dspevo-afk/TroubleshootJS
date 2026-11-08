"""Pure generic R7 result/marker reader; semantics and derived export demand only.

This reader does not prove native execution, measured counters, source classes,
the host process receipt, authored data, full D, R7 execution or Player behavior.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import time
import types

MAX_BYTES = 1048576

MAX_TREE_VISITS = 1000000

MAX_INT = 2147483647

MIN_INT = -2147483648

MAX_READER_NS = 180000000000

UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")

SHA = re.compile(r"[0-9a-f]{64}\Z")

FIXTURE_KIND = "GENERIC_ROUTING_REFERENCE_V1"

PACKET_KEYS = ("schema", "status", "runId", "sourceManifestSha256",
               "wireContractSha256", "references", "sourceReview")

REFERENCE_KEYS = ("caseId", "fixtureContextSha256", "run", "cleanup", "construction", "sourceGeometry")

RUN_KEYS = ("kind", "initial", "ordering", "outcome", "terminalFailure", "finalizers", "layout", "work")

INITIAL_KEYS = ("pass", "passes", "expanded", "maximumExpansions", "maximumVias",
                "priorPromotedNetId", "policy", "viaCost", "policyTransitions",
                "policyVias", "branchOrderStrategy", "netOrderStrategy", "roleWidths",
                "capturedOrderingStream", "sessionOrderingStream",
                "instrumentationModeDifference", "routingRelevantSettingsMatch", "traceCount", "holeCount")

ORDERING_KEYS = ("capturedNetIds", "computedNetIds", "requestedCohortNetIds", "requestedNetIds",
                 "traversedNetIds", "orderMatchesCapture", "searchCount")

OUTCOME_KEYS = ("disposition", "reason", "complete", "ownerComplete", "pass", "passes",
                "expanded", "maximumExpansions", "errorClass", "errorMessage")

TERMINAL_KEYS = ("failedNetId", "searchIndex", "searchPass", "passBefore", "passAfter",
                 "advanceCallOrdinal", "ownerSearchCleared", "searchReleased", "ownerPromotedNetId", "ownerReason")

FINALIZER_KEYS = ("routingGeometry", "allNetConnectivity", "twoLayerRules", "routeQuality", "viaCap")

WORK_KEYS = ("advanceCalls", "failureObservationUnits", "terminalEvidenceUnits", "ownerUnits",
             "wrapperUnits", "copyEntries", "copyChunks", "groups", "maxGroupNanos", "maximumUnitsPerGroup")

REVIEW_KEYS = ("scope", "physicalOwnerSha256", "studyParentSha256", "runOwnerSha256",
               "fixtureSourceSha256", "fixtureOracleSha256", "sessionConstructorEntries")

BASE_SHA = 'd11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0'

IO_SHA = 'df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923'

FIXTURE_SPEC_SHA256 = '5d2b62c98bd229a73be0e54e4b6adcde4d5080fb87046d8bd770455f80c1224a'

WIRE_SHA256 = 'dd738f0bbac0edc4f4cbaf4a0d2bba80ebeda9a581d79c052e29df8976e73dd6'

STUDY_PARENT_SHA256 = '88986781508aa34783d11e651082122f4bf8e0710a2263b39cc9b597fce9a8cf'

SOURCE_FIXTURE_SPEC = {'schema': 'tsj-generic-routing-reference-fixtures-v1', 'kind': 'GENERIC_ROUTING_REFERENCE_V1', 'fixtureSourceSha256': 'dd333ff9c448ce012c3c1a4e603be39b70e1ac1d8d7ef79509d12ab21bd17619', 'physicalOwnerSha256': '7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c', 'runOwnerSha256': '4ed1c8561adf1619a8b7ffbd21e23fdf2544dcaafe541af4e5d2b2b1e95cabe4', 'initial': {'pass': 2, 'passes': 0, 'expanded': 0, 'maximumExpansions': 1000000, 'maximumVias': 48, 'priorPromotedNetId': None, 'policy': 'FULLER_TWO_LAYER', 'viaCost': 60, 'policyTransitions': 4, 'policyVias': 48, 'branchOrderStrategy': 'CANONICAL_NEAREST', 'netOrderStrategy': 'CANONICAL', 'roleWidths': True, 'capturedOrderingStream': False, 'sessionOrderingStream': False, 'instrumentationModeDifference': False, 'routingRelevantSettingsMatch': True, 'traceCount': 0, 'holeCount': 0}, 'cases': {'OPEN': {'boardId': 'FAILED_FIRST_OPEN', 'cohortNetIds': ['NET_A', 'NET_B'], 'computedNetIds': ['NET_B', 'NET_A'], 'requestedNetIds': ['NET_A', 'NET_B'], 'layoutDimensions': {'width': 900, 'height': 700}, 'outline': {'x': 100, 'y': 100, 'width': 600, 'height': 500}, 'partsTray': {'x': 720, 'y': 120, 'width': 160, 'height': 400}, 'grid': {'width': 59, 'height': 49, 'originX': 110, 'originY': 110}, 'pads': [{'padId': 'A1.1', 'netId': 'NET_A', 'componentId': 'A1', 'role': 'HIGH_CURRENT', 'x': 260, 'y': 300, 'access': 'PTH'}, {'padId': 'A2.1', 'netId': 'NET_A', 'componentId': 'A2', 'role': 'HIGH_CURRENT', 'x': 500, 'y': 300, 'access': 'PTH'}, {'padId': 'B1.1', 'netId': 'NET_B', 'componentId': 'B1', 'role': 'CONTROL', 'x': 260, 'y': 450, 'access': 'PTH'}, {'padId': 'B2.1', 'netId': 'NET_B', 'componentId': 'B2', 'role': 'CONTROL', 'x': 500, 'y': 450, 'access': 'PTH'}], 'roleByNet': {'NET_A': 'HIGH_CURRENT', 'NET_B': 'CONTROL'}, 'expectedOutcome': {'disposition': 'FOUND', 'reason': 'SUCCESS', 'traversedNetIds': ['NET_A', 'NET_B'], 'searchCount': 1, 'terminalRequired': False}}, 'BLOCKED': {'boardId': 'FAILED_FIRST_BLOCKED', 'cohortNetIds': ['NET_A', 'NET_B'], 'computedNetIds': ['BLOCK_W_NET', 'BLOCK_S_NET', 'BLOCK_N_NET', 'BLOCK_E_NET', 'NET_B', 'NET_A'], 'requestedNetIds': ['NET_A', 'NET_B', 'BLOCK_W_NET', 'BLOCK_S_NET', 'BLOCK_N_NET', 'BLOCK_E_NET'], 'layoutDimensions': {'width': 900, 'height': 700}, 'outline': {'x': 100, 'y': 100, 'width': 600, 'height': 500}, 'partsTray': {'x': 720, 'y': 120, 'width': 160, 'height': 400}, 'grid': {'width': 59, 'height': 49, 'originX': 110, 'originY': 110}, 'pads': [{'padId': 'A1.1', 'netId': 'NET_A', 'componentId': 'A1', 'role': 'HIGH_CURRENT', 'x': 260, 'y': 300, 'access': 'PTH'}, {'padId': 'A2.1', 'netId': 'NET_A', 'componentId': 'A2', 'role': 'HIGH_CURRENT', 'x': 500, 'y': 300, 'access': 'PTH'}, {'padId': 'B1.1', 'netId': 'NET_B', 'componentId': 'B1', 'role': 'CONTROL', 'x': 260, 'y': 450, 'access': 'PTH'}, {'padId': 'B2.1', 'netId': 'NET_B', 'componentId': 'B2', 'role': 'CONTROL', 'x': 500, 'y': 450, 'access': 'PTH'}, {'padId': 'BLOCK_E.1', 'netId': 'BLOCK_E_NET', 'componentId': 'BLOCK_E', 'role': 'SIGNAL', 'x': 520, 'y': 300, 'access': 'PTH'}, {'padId': 'BLOCK_W.1', 'netId': 'BLOCK_W_NET', 'componentId': 'BLOCK_W', 'role': 'SIGNAL', 'x': 480, 'y': 300, 'access': 'PTH'}, {'padId': 'BLOCK_N.1', 'netId': 'BLOCK_N_NET', 'componentId': 'BLOCK_N', 'role': 'SIGNAL', 'x': 500, 'y': 280, 'access': 'PTH'}, {'padId': 'BLOCK_S.1', 'netId': 'BLOCK_S_NET', 'componentId': 'BLOCK_S', 'role': 'SIGNAL', 'x': 500, 'y': 320, 'access': 'PTH'}], 'roleByNet': {'NET_A': 'HIGH_CURRENT', 'NET_B': 'CONTROL', 'BLOCK_E_NET': 'SIGNAL', 'BLOCK_W_NET': 'SIGNAL', 'BLOCK_N_NET': 'SIGNAL', 'BLOCK_S_NET': 'SIGNAL'}, 'expectedOutcome': {'disposition': 'NO_PATH', 'reason': 'NET_A:NO_PATH', 'traversedNetIds': ['NET_A'], 'searchCount': 1, 'terminalRequired': True}}}}

class ReaderError(ValueError):
    """Fixed path-free rejection code; callers must not echo untrusted text."""
    def __init__(self, code):
        self.code = "SW_" + code
        super().__init__(self.code)

def need(condition, code):
    if not condition:
        raise ReaderError(code)

def integer(value, low=0, high=MAX_INT):
    return type(value) is int and low <= value <= high

def keys(value, expected, code):
    need(type(value) is dict and tuple(value) == expected, code)

def _tree(value):
    """Iterative limits also reject cycles and arbitrary/custom Python values."""
    pending = [(value, 0, (), False)]
    visits = 0
    while pending:
        item, depth, path, is_key = pending.pop()
        visits += 1
        need(visits <= MAX_TREE_VISITS and depth <= 64, "TREE_LIMIT")
        kind = type(item)
        geometry_leaf = (not is_key and len(path) == 7
                         and path[0] == "references"
                         and path[1] in ("OPEN", "BLOCKED")
                         and path[2:5] == ("run", "layout", "components")
                         and type(path[5]) is int and path[5] >= 0
                         and path[6] == "geometryCanonical")
        need(not geometry_leaf or kind is str, "GEOMETRY_TEXT_TYPE")
        if kind is str:
            limit = 4096 if geometry_leaf else 256
            need(len(item) <= limit and all(32 <= ord(c) <= 126 for c in item), "TEXT")
        elif kind is dict:
            need(visits + len(pending) + 2 * len(item) <= MAX_TREE_VISITS, "TREE_LIMIT")
            for key, child in item.items():
                need(type(key) is str, "KEY_TYPE")
                pending.append((key, depth + 1, path, True))
                pending.append((child, depth + 1, path + (key,), False))
        elif kind is list:
            need(visits + len(pending) + len(item) <= MAX_TREE_VISITS, "TREE_LIMIT")
            pending.extend((child, depth + 1, path + (index,), False)
                           for index, child in enumerate(item))
        else:
            need(item is None or kind in (int, bool), "VALUE_TYPE")

def _pairs(items):
    result = {}
    for key, value in items:
        need(key not in result, "DUPLICATE_KEY")
        result[key] = value
    return result

def _no_number(_value):
    raise ReaderError("NUMBER")

def canonical_line(value):
    _tree(value)
    return (json.dumps(value, ensure_ascii=True, allow_nan=False,
                       separators=(",", ":")) + "\n").encode("ascii")

def parse_packet(raw):
    need(type(raw) is bytes and 2 <= len(raw) <= MAX_BYTES
         and raw.endswith(b"\n") and b"\n" not in raw[:-1]
         and b"\r" not in raw, "FRAMING")
    try:
        value = json.loads(raw[:-1].decode("ascii"), object_pairs_hook=_pairs,
                           parse_float=_no_number, parse_constant=_no_number)
    except ReaderError:
        raise
    except (ValueError, TypeError, UnicodeError, RecursionError):
        raise ReaderError("JSON") from None
    need(type(value) is dict, "JSON_OBJECT")
    need(canonical_line(value) == raw, "CANONICAL")
    return value

def _bootstrap_base(path_value):
    """Small fixed-pin bootstrap; P's public loader owns subsequent checked I/O."""
    try:
        need(type(path_value) is str, "BASE_PATH")
        path = Path(path_value)
        need(path.is_absolute() and str(path) == path_value, "BASE_PATH")
        for part in (path, *path.parents):
            info = part.lstat()
            need(not stat.S_ISLNK(info.st_mode)
                 and not (getattr(info, "st_file_attributes", 0) & 0x400), "BASE_REPARSE")
        need(str(path.resolve(strict=True)) == str(path), "BASE_CANONICAL")
        before = path.lstat()
        need(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= 256 * 1024, "BASE_FILE")
        flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
        descriptor = os.open(str(path), flags)
        try:
            opened = os.fstat(descriptor)
            need(stat.S_ISREG(opened.st_mode)
                 and (opened.st_dev, opened.st_ino) == (before.st_dev, before.st_ino), "BASE_IDENTITY")
            with os.fdopen(descriptor, "rb") as stream:
                descriptor = None
                raw = stream.read(256 * 1024 + 1)
        finally:
            if descriptor is not None:
                os.close(descriptor)
        need(0 < len(raw) <= 256 * 1024 and hashlib.sha256(raw).hexdigest() == BASE_SHA, "BASE_HASH")
        module = types.ModuleType("_selected_wire_pinned_public_loader")
        module.__file__ = str(path)
        exec(compile(raw, str(path), "exec"), module.__dict__)
        return module
    except ReaderError:
        raise
    except Exception:
        raise ReaderError("BASE_READ") from None

def checked_read(input_value, base_value, io_value):
    for value in (input_value, io_value):
        need(type(value) is str and Path(value).is_absolute()
             and str(Path(value)) == value, "IO_PATH")
    base = _bootstrap_base(base_value)
    try:
        need(str(Path(input_value).resolve(strict=True)) == input_value
             and str(Path(io_value).resolve(strict=True)) == io_value, "IO_CANONICAL")
        io = base.load_io(io_value)
        need(getattr(io, "_sha", None) is not None, "IO_API")
        return io._read_regular(input_value, MAX_BYTES, "SELECTED_PACKET")
    except ReaderError:
        raise
    except Exception:
        raise ReaderError("CHECKED_IO") from None

MAX_GROUPS = 640

MAX_GROUP_NS = 5_000_000_000

MAX_GROUP_UNITS = 32_768

MAX_EXPANSIONS = 1_000_000

MAX_VIAS = 48

MAX_TRANSITIONS = 4

MAX_COPY_ENTRIES = 1_048_576

COPY_CHUNK = 4096

def exact_keys(value, keys, code: str) -> None:
    need(type(value) is dict and set(value) == set(keys), code)

def is_int(value, low: int = 0, high: int = 2**63 - 1) -> bool:
    return type(value) is int and low <= value <= high

def stable_cohort_order(net_ids, cohort_ids) -> list[str]:
    """Independently derive a stable requested order from observed net IDs."""
    need(type(net_ids) is list and bool(net_ids) and type(cohort_ids) is list
         and bool(cohort_ids), "ORDER_INPUT")
    need(all(type(net) is str and bool(net) for net in net_ids)
         and all(type(net) is str and bool(net) for net in cohort_ids), "ORDER_INPUT")
    seen = set()
    for net in net_ids:
        need(net not in seen, "ORDER_DUPLICATE")
        seen.add(net)
    cohort_seen = set()
    for net in cohort_ids:
        need(net not in cohort_seen, "ORDER_COHORT_DUPLICATE")
        cohort_seen.add(net)
        need(net in seen, "ORDER_COHORT_UNKNOWN")
    return list(cohort_ids) + [net for net in net_ids if net not in cohort_seen]

def _validate_terminal_failure(value, outcome, ordering, run_work, *, control):
    if value is None:
        need(not (control and outcome["disposition"] == "NO_PATH"),
             "TERMINAL_FAILURE_REQUIRED")
        return None
    exact_keys(value, ("failedNetId", "searchIndex", "searchPass", "passBefore",
                       "passAfter", "advanceCallOrdinal", "ownerSearchCleared",
                       "searchReleased", "ownerPromotedNetId", "ownerReason"),
               "TERMINAL_FAILURE_SCHEMA")
    failed = value["failedNetId"]
    requested = ordering["requestedNetIds"]
    traversed = ordering["traversedNetIds"]
    advance_calls = run_work["advanceCalls"]
    advance_ordinal = value["advanceCallOrdinal"]
    need(type(failed) is str and bool(failed)
         and is_int(value["searchPass"], 2, 2)
         and is_int(value["passBefore"], 2, 2)
         and is_int(value["passAfter"], 3, 3)
         and is_int(value["searchIndex"], 1, len(requested))
         and is_int(advance_calls, 2, 2_000_000)
         and is_int(advance_ordinal, 1, advance_calls - 1)
         and advance_ordinal + 1 == advance_calls
         and value["ownerSearchCleared"] is True and value["searchReleased"] is True
         and value["ownerPromotedNetId"] == failed
         and type(value["ownerReason"]) is str and bool(value["ownerReason"])
         and requested[value["searchIndex"] - 1] == failed
         and traversed.count(failed) == 1,
         "TERMINAL_FAILURE_BINDING")
    reason = outcome["reason"]
    suffix = reason.rsplit(":", 1)[-1] if type(reason) is str else None
    reason_net = reason.rsplit(":", 1)[0] if type(reason) is str and ":" in reason else None
    allowed = {
        "NO_PATH": {"NO_PATH"},
        "CAP_REJECTED": {"GRID_LIMIT", "TOTAL_SEARCH_LIMIT", "BRANCH_SEARCH_LIMIT"},
        "QUALITY_REJECTED": {"QUALITY_LIMIT", "BRANCH_QUALITY_LIMIT"},
    }
    need(value["ownerReason"] == reason
         and suffix in allowed.get(outcome["disposition"], set())
         and (reason_net is None or reason_net == failed)
         and (suffix != "GRID_LIMIT" or reason_net is None)
         and (outcome["disposition"] != "NO_PATH" or reason_net == failed),
         "TERMINAL_FAILURE_REASON")
    return value

def validate_generic_fixture_run(run, fixture_context):
    _check_fixture_context(fixture_context)
    kind = FIXTURE_KIND
    cohort_ids = fixture_context['cohortNetIds']
    exact_keys(run, ("kind", "initial", "ordering", "outcome", "terminalFailure",
                     "finalizers", "layout", "work"), "FRESH_RUN_SCHEMA")
    need(run["kind"] == kind, "FRESH_RUN_KIND")
    owner = fixture_context["initial"]
    initial = run["initial"]
    exact_keys(initial, ("pass", "passes", "expanded", "maximumExpansions", "maximumVias",
                         "priorPromotedNetId", "policy", "viaCost", "policyTransitions",
                         "policyVias", "branchOrderStrategy", "netOrderStrategy", "roleWidths",
                         "capturedOrderingStream", "sessionOrderingStream",
                         "instrumentationModeDifference", "routingRelevantSettingsMatch",
                         "traceCount", "holeCount"), "FRESH_INITIAL_SCHEMA")
    int_expectations = (("pass", 2), ("passes", 0), ("expanded", 0),
                        ("maximumExpansions", owner["maximumExpansions"]),
                        ("maximumVias", owner["maximumVias"]), ("viaCost", owner["viaCost"]),
                        ("policyTransitions", owner["policyTransitions"]),
                        ("policyVias", owner["policyVias"]), ("traceCount", 0), ("holeCount", 0))
    need(all(is_int(initial[name], expected, expected) for name, expected in int_expectations)
         and initial["priorPromotedNetId"] == owner["priorPromotedNetId"]
         and initial["policy"] == owner["policy"]
         and initial["branchOrderStrategy"] == owner["branchOrderStrategy"]
         and initial["netOrderStrategy"] == owner["netOrderStrategy"]
         and initial["roleWidths"] is owner["roleWidths"]
         and initial["capturedOrderingStream"] is owner["capturedOrderingStream"]
         and initial["sessionOrderingStream"] is False
         and initial["instrumentationModeDifference"] is owner["instrumentationModeDifference"]
         and initial["routingRelevantSettingsMatch"] is True,
         "FRESH_INITIAL_BINDING")
    ordering = run["ordering"]
    exact_keys(ordering, ("capturedNetIds", "computedNetIds", "requestedCohortNetIds",
                          "requestedNetIds", "traversedNetIds", "orderMatchesCapture",
                          "searchCount"), "FRESH_ORDERING_SCHEMA")
    captured_ids = fixture_context["computedNetIds"]
    expected = stable_cohort_order(captured_ids, cohort_ids)
    need(expected == fixture_context["requestedNetIds"], "FIXTURE_REQUESTED_ORDER")
    traversed = ordering["traversedNetIds"]
    need(type(traversed) is list and all(type(net) is str for net in traversed)
         and ordering["capturedNetIds"] == captured_ids
         and ordering["computedNetIds"] == captured_ids
         and ordering["requestedCohortNetIds"] == cohort_ids
         and ordering["requestedNetIds"] == expected
         and ordering["orderMatchesCapture"] is True
         and is_int(ordering["searchCount"], 1, 1)
         and len(traversed) <= len(expected)
         and traversed == expected[:len(traversed)]
         and len(set(traversed)) == len(traversed),
         "FRESH_ORDERING_BINDING")
    outcome = run["outcome"]
    exact_keys(outcome, ("disposition", "reason", "complete", "ownerComplete", "pass", "passes",
                         "expanded", "maximumExpansions", "errorClass", "errorMessage"),
               "FRESH_OUTCOME_SCHEMA")
    disposition, reason = outcome["disposition"], outcome["reason"]
    need(disposition in ("FOUND", "NO_PATH", "CAP_REJECTED", "QUALITY_REJECTED")
         and (reason is None or (type(reason) is str and bool(reason)))
         and type(outcome["complete"]) is bool and type(outcome["ownerComplete"]) is bool
         and outcome["ownerComplete"] is True
         and is_int(outcome["pass"], 2, 3) and is_int(outcome["passes"], 1, 1)
         and is_int(outcome["expanded"], 0, owner["maximumExpansions"])
         and is_int(outcome["maximumExpansions"], owner["maximumExpansions"],
                    owner["maximumExpansions"])
         and outcome["errorClass"] is None and outcome["errorMessage"] is None,
         "FRESH_OUTCOME_BINDING")
    if disposition == "FOUND":
        need(reason == "SUCCESS" and outcome["complete"] is True
             and outcome["pass"] == 2 and traversed == expected,
             "FOUND_OUTCOME")
    else:
        need(type(reason) is str and bool(reason)
             and outcome["complete"] is False and outcome["pass"] == 3,
             "NONFOUND_OUTCOME")
        suffix = reason.rsplit(":", 1)[-1]
        reason_net = reason.rsplit(":", 1)[0] if ":" in reason else None
        allowed_suffixes = {
            "NO_PATH": {"NO_PATH"},
            "CAP_REJECTED": {"GRID_LIMIT", "TOTAL_SEARCH_LIMIT", "BRANCH_SEARCH_LIMIT"},
            "QUALITY_REJECTED": {"QUALITY_LIMIT", "BRANCH_QUALITY_LIMIT"},
        }
        need(suffix in allowed_suffixes[disposition]
             and (reason_net is None or reason_net in fixture_context["computedNetIds"])
             and (suffix != "GRID_LIMIT" or reason_net is None),
             "OUTCOME_REASON")
    need((disposition == "FOUND") == (run["layout"] is not None),
         "FRESH_LAYOUT_DISPOSITION")
    finalizers = run["finalizers"]
    exact_keys(finalizers, ("routingGeometry", "allNetConnectivity", "twoLayerRules",
                            "routeQuality", "viaCap"), "FINALIZERS_SCHEMA")
    if disposition == "FOUND":
        need(all(finalizers[field] == "PASS" for field in finalizers), "FOUND_FINALIZERS")
    elif disposition == "QUALITY_REJECTED" and reason == "QUALITY_LIMIT":
        need(finalizers == {"routingGeometry": "PASS", "allNetConnectivity": "PASS",
                            "twoLayerRules": "PASS", "routeQuality": "REJECTED",
                            "viaCap": "NOT_RUN"}, "QUALITY_FINALIZERS")
    else:
        need(all(finalizers[field] == "NOT_RUN" for field in finalizers),
             "NONFOUND_FINALIZERS")
    work = run["work"]
    exact_keys(work, ("advanceCalls", "ownerUnits", "wrapperUnits", "copyEntries", "copyChunks",
                      "groups", "maxGroupNanos", "maximumUnitsPerGroup",
                      "failureObservationUnits", "terminalEvidenceUnits"), "RUN_WORK_SCHEMA")
    need(is_int(work["advanceCalls"], 1, 2_000_000)
         and is_int(work["ownerUnits"], 0, MAX_GROUP_UNITS * MAX_GROUPS)
         and is_int(work["wrapperUnits"], 0, MAX_GROUP_UNITS * MAX_GROUPS)
         and is_int(work["failureObservationUnits"], work["advanceCalls"],
                    work["advanceCalls"])
         and is_int(work["terminalEvidenceUnits"], 0, MAX_GROUP_UNITS * MAX_GROUPS)
         and work["wrapperUnits"] >= (work["advanceCalls"]
                                       + work["failureObservationUnits"]
                                       + work["terminalEvidenceUnits"])
         and is_int(work["copyEntries"], 0, MAX_COPY_ENTRIES)
         and is_int(work["copyChunks"], 0, MAX_COPY_ENTRIES)
         and is_int(work["groups"], 1, MAX_GROUPS)
         and is_int(work["maxGroupNanos"], 1, MAX_GROUP_NS)
         and is_int(work["maximumUnitsPerGroup"], 1, MAX_GROUP_UNITS)
         and work["copyChunks"] <= work["copyEntries"]
         and (work["copyEntries"] == 0 or work["copyChunks"] >=
              (work["copyEntries"] + COPY_CHUNK - 1) // COPY_CHUNK),
         "RUN_WORK_BOUNDS")
    need(traversed or disposition == "FOUND", "EMPTY_TRAVERSAL")
    terminal = _validate_terminal_failure(run["terminalFailure"], outcome, ordering, work,
                                          control=kind == "OBSERVED_FAILURE_CONTROL")
    expected_terminal_units = (0 if terminal is None else
                               11 + len(traversed)
                               + (1 if kind == "OBSERVED_FAILURE_CONTROL"
                                  and disposition == "NO_PATH" else 0))
    need(work["terminalEvidenceUnits"] == expected_terminal_units,
         "TERMINAL_EVIDENCE_ACCOUNTING")
    _natural_fixture_result(run, fixture_context, terminal)
    return {"expectedOrder": expected, "ordering": ordering, "outcome": outcome,
            "terminalFailure": terminal, "work": work, "layout": run["layout"]}

class ConductorError(ValueError):
    def __init__(self, code):
        self.code = code
        super().__init__(code)

_CONDUCTOR_ROLE_WIDTHS = {
    "RETURN": 11,
    "SUPPLY": 11,
    "HIGH_CURRENT": 13,
    "CONTROL": 9,
    "SIGNAL": 9,
}

_CONDUCTOR_ROLE_CANONICAL = (
    "ROLE_TRACE@1;RETURN=11;SUPPLY=11;HIGH_CURRENT=13;CONTROL=9;SIGNAL=9;"
    "units=drawing;edge-clearance=6"
)

_CONDUCTOR_LAYERS = ("TOP", "BOTTOM")

_CONDUCTOR_EXPOSURES = ("EXPOSED", "COVERED")

_CONDUCTOR_ATTACHMENTS = ("PLATED_THROUGH_HOLE", "SURFACE_PAD")

_CONDUCTOR_HOLE_KINDS = ("VIA", "PLATED", "NON_PLATED")

_CONDUCTOR_MAX_COORD = 100000000

_CONDUCTOR_MAX_IDS = 100000

_CONDUCTOR_MAX_TRACE_POINTS = 200000

_CONDUCTOR_CELL = 256

_CONDUCTOR_MAX_SHAPES = 200000

_CONDUCTOR_MAX_BUCKET_REFS = 2000000

_CONDUCTOR_MAX_CONTACT_TESTS = 8000000

_CONDUCTOR_MAX_CELLS_PER_SHAPE = 4096

_CONDUCTOR_LAYOUT_KEYS = {
    "geometrySha256", "width", "height", "outline", "partsTray", "pads",
    "components", "regions", "labels", "placementConstraints",
    "clearanceRules", "traceCount", "holeCount", "viaCount", "traces", "holes",
}

_CONDUCTOR_PAD_KEYS = {
    "padId", "netId", "componentId", "terminalId", "x", "y", "padBounds",
    "probeBounds", "escapeDirection", "escapeLength", "attachment", "mountingSide",
    "exposure", "topCopper", "bottomCopper",
}

_CONDUCTOR_TRACE_KEYS = {
    "sourceId", "netId", "startPadId", "endPadId", "layer", "exposure", "width",
    "x", "y",
}

_CONDUCTOR_HOLE_KEYS = {
    "id", "netId", "kind", "exposure", "x", "y", "drillRadius", "landRadius",
}

_CONDUCTOR_CLEARANCE_KEYS = {
    "traceWidth", "copperHeight", "minimumVisibleClearance",
    "minimumCenterlineClearance", "routingGridClearanceCells", "roleWidths",
    "maximumTransitions", "maximumVias",
}

def _conductor_need(condition, code):
    if not condition:
        raise ConductorError(code)

def _conductor_id(value, code):
    _conductor_need(type(value) is str and 0 < len(value) <= 1024, code)
    _conductor_need("\x00" not in value, code)
    return value

def _conductor_int(value, low, high, code):
    _conductor_need(type(value) is int and low <= value <= high, code)
    return value

def _conductor_coordinate(value, code):
    return _conductor_int(value, -_CONDUCTOR_MAX_COORD,
                           _CONDUCTOR_MAX_COORD, code)

def _conductor_exact_object(value, keys, code):
    _conductor_need(type(value) is dict and set(value) == keys, code)
    return value

def _conductor_rectangle(value, code):
    _conductor_need(type(value) is list and len(value) == 4, code)
    x = _conductor_coordinate(value[0], code)
    y = _conductor_coordinate(value[1], code)
    width = _conductor_int(value[2], 1, 2 * _CONDUCTOR_MAX_COORD, code)
    height = _conductor_int(value[3], 1, 2 * _CONDUCTOR_MAX_COORD, code)
    _conductor_coordinate(x + width, code)
    _conductor_coordinate(y + height, code)
    return (x, y, width, height)

def _conductor_contains_rect(outer, inner):
    return (inner[0] >= outer[0] and inner[1] >= outer[1] and
            inner[0] + inner[2] <= outer[0] + outer[2] and
            inner[1] + inner[3] <= outer[1] + outer[3])

def _conductor_contains_point(rect, x, y):
    return (x >= rect[0] and y >= rect[1] and
            x <= rect[0] + rect[2] and y <= rect[1] + rect[3])

def _conductor_touch(a, b):
    return (a[0] + a[2] >= b[0] and b[0] + b[2] >= a[0] and
            a[1] + a[3] >= b[1] and b[1] + b[3] >= a[1])

def _conductor_stroke(x1, y1, x2, y2, width):
    _conductor_need((x1 == x2) != (y1 == y2), "TRACE_GEOMETRY")
    half = width // 2
    box = (min(x1, x2) - half, min(y1, y2) - half,
           abs(x2 - x1) + width, abs(y2 - y1) + width)
    _conductor_rectangle(list(box), "TRACE_GEOMETRY")
    return box

def _conductor_copy(value):
    if type(value) is dict:
        return {key: _conductor_copy(item) for key, item in value.items()}
    if type(value) is list:
        return [_conductor_copy(item) for item in value]
    if type(value) is tuple:
        return tuple(_conductor_copy(item) for item in value)
    return value

def validate_complete_layout(layout, p07_pads, role_by_net, requested_net_ids):
    """Validate complete copper conductor connectivity independently of Java.

    `p07_pads` is the authenticated parsed operation-1 PAD census.  Its required
    fields are padId/netId/componentId/role/x/y; unrelated P07 fields are ignored.
    No IDs are returned in `facts`.
    """
    _conductor_need(type(layout) is dict and set(layout) == _CONDUCTOR_LAYOUT_KEYS,
                    "LAYOUT_SCHEMA")
    _conductor_need(type(p07_pads) is list and type(role_by_net) is dict,
                    "INPUT_SHAPE")
    if type(requested_net_ids) in (list, tuple):
        requested = [_conductor_id(net, "NET_VECTOR_ID") for net in requested_net_ids]
        _conductor_need(len(requested) == len(set(requested)), "NET_VECTOR_DUPLICATE")
    elif type(requested_net_ids) in (set, frozenset):
        requested = [_conductor_id(net, "NET_VECTOR_ID") for net in requested_net_ids]
    else:
        raise ConductorError("INPUT_SHAPE")
    _conductor_need(len(requested) <= _CONDUCTOR_MAX_IDS, "CONDUCTOR_COMPLEXITY")
    requested_set = set(_conductor_id(net, "NET_VECTOR_ID") for net in requested)
    _conductor_need(bool(requested_set) and len(requested_set) <= _CONDUCTOR_MAX_IDS,
                    "NET_VECTOR_EMPTY")

    geometry_sha = layout.get("geometrySha256")
    _conductor_need(type(geometry_sha) is str and len(geometry_sha) == 64 and
                    all(ch in "0123456789abcdef" for ch in geometry_sha),
                    "LAYOUT_GEOMETRY_HASH")
    _conductor_int(layout.get("width"), 1, 2 * _CONDUCTOR_MAX_COORD, "LAYOUT_DIMENSIONS")
    _conductor_int(layout.get("height"), 1, 2 * _CONDUCTOR_MAX_COORD, "LAYOUT_DIMENSIONS")
    _conductor_rectangle(layout.get("outline"), "LAYOUT_OUTLINE")
    _conductor_rectangle(layout.get("partsTray"), "LAYOUT_PARTS_TRAY")
    _conductor_need(type(layout.get("pads")) is list and
                    type(layout.get("components")) is list and
                    type(layout.get("regions")) is list and
                    type(layout.get("labels")) is list and
                    type(layout.get("placementConstraints")) is dict and
                    type(layout.get("traces")) is list and type(layout.get("holes")) is list,
                    "LAYOUT_COLLECTIONS")
    _conductor_need(len(p07_pads) <= _CONDUCTOR_MAX_IDS and
                    len(layout["pads"]) <= _CONDUCTOR_MAX_IDS and
                    len(layout["components"]) <= _CONDUCTOR_MAX_IDS and
                    len(layout["holes"]) <= _CONDUCTOR_MAX_IDS and
                    len(layout["traces"]) <= _CONDUCTOR_MAX_IDS,
                    "CONDUCTOR_COMPLEXITY")

    constraints = layout["placementConstraints"]
    _conductor_need(constraints.get("traceWidthProfileIdentity") == "ROLE_TRACE@1" and
                    constraints.get("traceWidthProfileCanonical") ==
                    _CONDUCTOR_ROLE_CANONICAL,
                    "TRACE_PROFILE")
    clearance = _conductor_exact_object(layout.get("clearanceRules"),
                                        _CONDUCTOR_CLEARANCE_KEYS, "CLEARANCE_SCHEMA")
    _conductor_need(clearance.get("traceWidth") == 9 and
                    clearance.get("roleWidths") == _CONDUCTOR_ROLE_WIDTHS,
                    "TRACE_PROFILE")
    maximum_vias = _conductor_int(clearance.get("maximumVias"), 0,
                                  _CONDUCTOR_MAX_COORD, "CLEARANCE_SCHEMA")

    _conductor_need(type(layout.get("traceCount")) is int and
                    layout["traceCount"] == len(layout["traces"]) and
                    type(layout.get("holeCount")) is int and
                    layout["holeCount"] == len(layout["holes"]), "LAYOUT_COUNTS")
    _conductor_int(layout.get("traceCount"), 0, _CONDUCTOR_MAX_SHAPES, "LAYOUT_COUNTS")
    _conductor_int(layout.get("holeCount"), 0, _CONDUCTOR_MAX_SHAPES, "LAYOUT_COUNTS")
    _conductor_int(layout.get("viaCount"), 0, _CONDUCTOR_MAX_SHAPES, "LAYOUT_COUNTS")

    _conductor_need(set(role_by_net) == requested_set, "ROLE_NET_SET")
    for net, role in role_by_net.items():
        _conductor_id(net, "ROLE_NET_ID")
        _conductor_need(type(role) is str and role in _CONDUCTOR_ROLE_WIDTHS,
                        "ROLE_NAME")

    p07_by_id = {}
    p07_role_by_net = {}
    for row in p07_pads:
        _conductor_need(type(row) is dict, "P07_PAD_SCHEMA")
        pad_id = _conductor_id(row.get("padId"), "P07_PAD_SCHEMA")
        net_id = _conductor_id(row.get("netId"), "P07_PAD_SCHEMA")
        component_id = _conductor_id(row.get("componentId"), "P07_PAD_SCHEMA")
        role = row.get("role")
        _conductor_need(type(role) is str and role in _CONDUCTOR_ROLE_WIDTHS,
                        "P07_PAD_ROLE")
        x = _conductor_coordinate(row.get("x"), "P07_PAD_SCHEMA")
        y = _conductor_coordinate(row.get("y"), "P07_PAD_SCHEMA")
        _conductor_need(net_id in requested_set, "P07_PAD_NET")
        _conductor_need(pad_id not in p07_by_id, "P07_PAD_DUPLICATE")
        p07_by_id[pad_id] = (net_id, component_id, x, y, role)
        prior_role = p07_role_by_net.get(net_id)
        _conductor_need(prior_role is None or prior_role == role, "P07_NET_ROLE_CONFLICT")
        p07_role_by_net[net_id] = role
    _conductor_need(set(p07_role_by_net) == requested_set and
                    p07_role_by_net == role_by_net, "ROLE_IDENTITY")

    component_terminals = {}
    component_ids = set()
    for component in layout["components"]:
        _conductor_need(type(component) is dict, "COMPONENT_SCHEMA")
        _conductor_need(len(component_ids) < _CONDUCTOR_MAX_IDS,
                        "CONDUCTOR_COMPLEXITY")
        component_id = _conductor_id(component.get("componentId"), "COMPONENT_SCHEMA")
        _conductor_need(component_id not in component_ids, "COMPONENT_DUPLICATE")
        component_ids.add(component_id)
        terminals = component.get("terminals")
        _conductor_need(type(terminals) is list, "COMPONENT_TERMINALS")
        _conductor_need(len(terminals) <= _CONDUCTOR_MAX_IDS,
                        "CONDUCTOR_COMPLEXITY")
        terminal_ids = set()
        for terminal in terminals:
            _conductor_need(type(terminal) is dict, "COMPONENT_TERMINALS")
            terminal_id = _conductor_id(terminal.get("terminalId"), "COMPONENT_TERMINALS")
            _conductor_need(terminal_id not in terminal_ids, "COMPONENT_TERMINAL_DUPLICATE")
            terminal_ids.add(terminal_id)
        component_terminals[component_id] = terminal_ids

    pads_by_id = {}
    pad_nodes = {}
    pad_nodes_by_net = {net: [] for net in requested_set}
    shapes = []
    nodes = []
    parents = []
    ranks = []
    interlayer_links = 0

    def new_node(net_id):
        _conductor_need(net_id in requested_set, "CONDUCTOR_NET")
        index = len(nodes)
        nodes.append(net_id)
        parents.append(index)
        ranks.append(0)
        return index

    def find_node(index):
        root = index
        while parents[root] != root:
            root = parents[root]
        while parents[index] != index:
            following = parents[index]
            parents[index] = root
            index = following
        return root

    def union_nodes(first, second):
        first_root = find_node(first)
        second_root = find_node(second)
        if first_root == second_root:
            return False
        _conductor_need(nodes[first_root] == nodes[second_root], "FOREIGN_COPPER_SHORT")
        if ranks[first_root] < ranks[second_root]:
            first_root, second_root = second_root, first_root
        parents[second_root] = first_root
        if ranks[first_root] == ranks[second_root]:
            ranks[first_root] += 1
        return True

    def add_shape(node, net_id, layer, box, kind, owner):
        _conductor_need(len(shapes) < _CONDUCTOR_MAX_SHAPES, "CONDUCTOR_COMPLEXITY")
        shapes.append((node, net_id, layer, box, kind, owner))

    layout_pads = layout["pads"]
    _conductor_need(len(layout_pads) == len(p07_by_id), "PAD_SET_MISMATCH")
    for row in layout_pads:
        _conductor_exact_object(row, _CONDUCTOR_PAD_KEYS, "PAD_SCHEMA")
        pad_id = _conductor_id(row.get("padId"), "PAD_SCHEMA")
        net_id = _conductor_id(row.get("netId"), "PAD_SCHEMA")
        component_id = _conductor_id(row.get("componentId"), "PAD_SCHEMA")
        terminal_id = _conductor_id(row.get("terminalId"), "PAD_SCHEMA")
        x = _conductor_coordinate(row.get("x"), "PAD_SCHEMA")
        y = _conductor_coordinate(row.get("y"), "PAD_SCHEMA")
        _conductor_need(pad_id not in pads_by_id, "PAD_DUPLICATE")
        _conductor_need(pad_id in p07_by_id, "PAD_SET_MISMATCH")
        _conductor_need(p07_by_id[pad_id][:4] == (net_id, component_id, x, y),
                        "PAD_IDENTITY")
        _conductor_need(net_id in requested_set, "PAD_NET")
        _conductor_need(component_id in component_terminals and
                        terminal_id in component_terminals[component_id],
                        "PAD_TERMINAL_IDENTITY")
        attachment = row.get("attachment")
        mounting = row.get("mountingSide")
        exposure = row.get("exposure")
        _conductor_need(attachment in _CONDUCTOR_ATTACHMENTS and
                        mounting in _CONDUCTOR_LAYERS and
                        exposure in _CONDUCTOR_EXPOSURES, "PAD_PHYSICAL_SCHEMA")
        _conductor_need(type(row.get("topCopper")) is bool and
                        type(row.get("bottomCopper")) is bool, "PAD_PHYSICAL_SCHEMA")
        expected_layers = ("TOP", "BOTTOM") if attachment == "PLATED_THROUGH_HOLE" else (mounting,)
        expected_top = "TOP" in expected_layers
        expected_bottom = "BOTTOM" in expected_layers
        _conductor_need(row["topCopper"] is expected_top and
                        row["bottomCopper"] is expected_bottom, "PAD_COPPER_FLAGS")
        pad_bounds = _conductor_rectangle(row.get("padBounds"), "PAD_BOUNDS")
        probe_bounds = _conductor_rectangle(row.get("probeBounds"), "PAD_PROBE_BOUNDS")
        _conductor_need(_conductor_contains_rect(probe_bounds, pad_bounds) and
                        _conductor_contains_point(pad_bounds, x, y), "PAD_BOUNDS")
        escape = row.get("escapeDirection")
        _conductor_need(type(escape) is list and len(escape) == 2, "PAD_ESCAPE")
        dx = _conductor_int(escape[0], -1, 1, "PAD_ESCAPE")
        dy = _conductor_int(escape[1], -1, 1, "PAD_ESCAPE")
        _conductor_need(abs(dx) + abs(dy) <= 1, "PAD_ESCAPE")
        escape_length = _conductor_int(row.get("escapeLength"), 0,
                                       2 * _CONDUCTOR_MAX_COORD, "PAD_ESCAPE")
        _conductor_need(escape_length == 0 or dx != 0 or dy != 0, "PAD_ESCAPE")
        _conductor_coordinate(x + dx * escape_length, "PAD_ESCAPE")
        _conductor_coordinate(y + dy * escape_length, "PAD_ESCAPE")
        pads_by_id[pad_id] = row
        pad_nodes[pad_id] = {}
        for layer in expected_layers:
            node = new_node(net_id)
            pad_nodes[pad_id][layer] = node
            pad_nodes_by_net[net_id].append(node)
            add_shape(node, net_id, layer, pad_bounds, "pad", pad_id)
        if attachment == "PLATED_THROUGH_HOLE":
            if union_nodes(pad_nodes[pad_id]["TOP"], pad_nodes[pad_id]["BOTTOM"]):
                interlayer_links += 1

    _conductor_need(set(pads_by_id) == set(p07_by_id), "PAD_SET_MISMATCH")
    for pad_id, (net_id, component_id, x, y, role) in p07_by_id.items():
        _conductor_need(role_by_net[net_id] == role, "ROLE_IDENTITY")

    holes = layout["holes"]
    hole_ids = set()
    hole_nodes = {}
    via_count = 0
    for row in holes:
        _conductor_exact_object(row, _CONDUCTOR_HOLE_KEYS, "HOLE_SCHEMA")
        hole_id = _conductor_id(row.get("id"), "HOLE_SCHEMA")
        _conductor_need(hole_id not in hole_ids, "HOLE_DUPLICATE")
        hole_ids.add(hole_id)
        kind = row.get("kind")
        exposure = row.get("exposure")
        _conductor_need(kind in _CONDUCTOR_HOLE_KINDS and
                        exposure in _CONDUCTOR_EXPOSURES, "HOLE_SCHEMA")
        net_id = row.get("netId")
        x = _conductor_coordinate(row.get("x"), "HOLE_SCHEMA")
        y = _conductor_coordinate(row.get("y"), "HOLE_SCHEMA")
        drill = _conductor_int(row.get("drillRadius"), 1,
                               _CONDUCTOR_MAX_COORD, "HOLE_RADIUS")
        land = _conductor_int(row.get("landRadius"), 1,
                              _CONDUCTOR_MAX_COORD, "HOLE_RADIUS")
        _conductor_need((kind == "NON_PLATED" and net_id is None and land == drill) or
                        (kind != "NON_PLATED" and type(net_id) is str and
                         net_id in requested_set and land > drill), "HOLE_NET_OR_RADIUS")
        if kind == "VIA":
            via_count += 1
        if kind == "NON_PLATED":
            continue
        box = _conductor_rectangle([x - land, y - land, 2 * land, 2 * land],
                                   "HOLE_BOUNDS")
        hole_nodes[hole_id] = {}
        for layer in _CONDUCTOR_LAYERS:
            node = new_node(net_id)
            hole_nodes[hole_id][layer] = node
            add_shape(node, net_id, layer, box, "hole", (hole_id, layer))
        if union_nodes(hole_nodes[hole_id]["TOP"], hole_nodes[hole_id]["BOTTOM"]):
            interlayer_links += 1
    _conductor_need(via_count == layout["viaCount"] and via_count <= maximum_vias,
                    "VIA_COUNT")

    trace_nodes = []
    trace_source_ids = set()
    total_trace_points = 0
    trace_layer_counts = {"TOP": 0, "BOTTOM": 0}
    role_trace_counts = dict((role, 0) for role in _CONDUCTOR_ROLE_WIDTHS)
    exposure_counts = {"EXPOSED": 0, "COVERED": 0}
    for row in layout["traces"]:
        _conductor_exact_object(row, _CONDUCTOR_TRACE_KEYS, "TRACE_SCHEMA")
        source_id = _conductor_id(row.get("sourceId"), "TRACE_SCHEMA")
        _conductor_need(source_id not in trace_source_ids, "TRACE_SOURCE_DUPLICATE")
        trace_source_ids.add(source_id)
        net_id = _conductor_id(row.get("netId"), "TRACE_SCHEMA")
        layer = row.get("layer")
        exposure = row.get("exposure")
        _conductor_need(layer in _CONDUCTOR_LAYERS and
                        exposure in _CONDUCTOR_EXPOSURES, "TRACE_LAYER_OR_EXPOSURE")
        _conductor_need(net_id in requested_set, "TRACE_NET")
        width = _conductor_int(row.get("width"), 1, 63, "TRACE_WIDTH")
        role = role_by_net[net_id]
        _conductor_need(width == _CONDUCTOR_ROLE_WIDTHS[role], "TRACE_WIDTH")
        xs = row.get("x")
        ys = row.get("y")
        _conductor_need(type(xs) is list and type(ys) is list and
                        len(xs) == len(ys) and 2 <= len(xs) <= _CONDUCTOR_MAX_SHAPES,
                        "TRACE_GEOMETRY")
        total_trace_points += len(xs)
        _conductor_need(total_trace_points <= _CONDUCTOR_MAX_TRACE_POINTS,
                        "CONDUCTOR_COMPLEXITY")
        points = []
        for px, py in zip(xs, ys):
            points.append((_conductor_coordinate(px, "TRACE_GEOMETRY"),
                           _conductor_coordinate(py, "TRACE_GEOMETRY")))
        start_id = row.get("startPadId")
        end_id = row.get("endPadId")
        _conductor_need(start_id is None or type(start_id) is str, "TRACE_ENDPOINT_PAD")
        _conductor_need(end_id is None or type(end_id) is str, "TRACE_ENDPOINT_PAD")
        trace_node = new_node(net_id)
        for endpoint, point in ((start_id, points[0]), (end_id, points[-1])):
            if endpoint is None:
                continue
            _conductor_need(endpoint in pads_by_id, "TRACE_ENDPOINT_PAD")
            pad = pads_by_id[endpoint]
            _conductor_need(pad["netId"] == net_id, "TRACE_ENDPOINT_NET")
            _conductor_need(point == (pad["x"], pad["y"]), "TRACE_ENDPOINT_COORDINATE")
            _conductor_need(layer in pad_nodes[endpoint], "TRACE_ENDPOINT_LAYER")
            union_nodes(trace_node, pad_nodes[endpoint][layer])
        segment_count = 0
        previous = points[0]
        for point in points[1:]:
            if point == previous:
                continue
            box = _conductor_stroke(previous[0], previous[1], point[0], point[1], width)
            add_shape(trace_node, net_id, layer, box, "trace", source_id)
            segment_count += 1
            previous = point
        _conductor_need(segment_count > 0, "TRACE_GEOMETRY")
        trace_nodes.append((trace_node, net_id))
        trace_layer_counts[layer] += 1
        role_trace_counts[role] += 1
        exposure_counts[exposure] += 1

    _conductor_need(len(shapes) <= _CONDUCTOR_MAX_SHAPES, "CONDUCTOR_COMPLEXITY")
    bucket_index = {}
    bucket_refs = 0
    contact_tests = 0
    contact_merges = 0
    hole_contact_keys = set()
    for shape_index, shape in enumerate(shapes):
        node, net_id, layer, box, kind, owner = shape
        x0 = box[0] // _CONDUCTOR_CELL
        y0 = box[1] // _CONDUCTOR_CELL
        x1 = (box[0] + box[2]) // _CONDUCTOR_CELL
        y1 = (box[1] + box[3]) // _CONDUCTOR_CELL
        cell_count = (x1 - x0 + 1) * (y1 - y0 + 1)
        _conductor_need(0 < cell_count <= _CONDUCTOR_MAX_CELLS_PER_SHAPE,
                        "CONDUCTOR_COMPLEXITY")
        candidate_indexes = set()
        for cell_x in range(x0, x1 + 1):
            for cell_y in range(y0, y1 + 1):
                for candidate in bucket_index.get((layer, cell_x, cell_y), ()):
                    candidate_indexes.add(candidate)
        for candidate in candidate_indexes:
            other = shapes[candidate]
            if other[0] == node:
                continue
            contact_tests += 1
            _conductor_need(contact_tests <= _CONDUCTOR_MAX_CONTACT_TESTS,
                            "CONDUCTOR_COMPLEXITY")
            if not _conductor_touch(box, other[3]):
                continue
            _conductor_need(net_id == other[1], "FOREIGN_COPPER_SHORT")
            if kind == "hole":
                hole_contact_keys.add(owner)
            if other[4] == "hole":
                hole_contact_keys.add(other[5])
            if union_nodes(node, other[0]):
                contact_merges += 1
        for cell_x in range(x0, x1 + 1):
            for cell_y in range(y0, y1 + 1):
                key = (layer, cell_x, cell_y)
                bucket = bucket_index.get(key)
                if bucket is None:
                    bucket = []
                    bucket_index[key] = bucket
                bucket.append(shape_index)
                bucket_refs += 1
                _conductor_need(bucket_refs <= _CONDUCTOR_MAX_BUCKET_REFS,
                                "CONDUCTOR_COMPLEXITY")

    for hole_id in hole_nodes:
        for layer in _CONDUCTOR_LAYERS:
            _conductor_need((hole_id, layer) in hole_contact_keys,
                            "PLATED_HOLE_UNATTACHED")

    net_pad_roots = {}
    net_pad_ids = {net: set() for net in requested_set}
    for pad_id, pad in pads_by_id.items():
        net_pad_ids[pad["netId"]].add(pad_id)
    for net_id in requested_set:
        _conductor_need(bool(net_pad_ids[net_id]), "NET_WITHOUT_PADS")
        roots = set(find_node(node) for node in pad_nodes_by_net[net_id])
        _conductor_need(len(roots) == 1, "NET_DISCONNECTED")
        net_pad_roots[net_id] = next(iter(roots))
    for node, net_id in trace_nodes:
        _conductor_need(find_node(node) == net_pad_roots[net_id], "ORPHAN_TRACE")
    for net_holes in hole_nodes.values():
        for node in net_holes.values():
            net_id = nodes[find_node(node)]
            _conductor_need(find_node(node) == net_pad_roots[net_id], "ORPHAN_HOLE")

    singleton_count = sum(1 for ids in net_pad_ids.values() if len(ids) == 1)
    return {
        "allNetsConnected": True,
        "netCount": len(requested_set),
        "connectedNetCount": len(requested_set),
        "singletonNetCount": singleton_count,
        "padCount": len(pads_by_id),
        "traceCount": len(layout["traces"]),
        "holeCount": len(holes),
        "viaCount": via_count,
        "traceLayerCounts": trace_layer_counts,
        "roleTraceCounts": role_trace_counts,
        "traceExposureCounts": exposure_counts,
        "sameLayerContactMerges": contact_merges,
        "interlayerLinkCount": interlayer_links,
        "conductorShapeCount": len(shapes),
    }

def load_fixture_spec(raw):
    # Authenticate the entire source oracle before its first JSON parse.
    need(type(raw) is bytes and len(raw) <= MAX_BYTES
         and hashlib.sha256(raw).hexdigest() == FIXTURE_SPEC_SHA256, "FIXTURE_SPEC_HASH")
    try:
        value = json.loads(raw.decode("ascii"), object_pairs_hook=_pairs,
                           parse_float=_no_number, parse_constant=_no_number)
    except ReaderError:
        raise
    except (ValueError, TypeError, UnicodeError, RecursionError):
        raise ReaderError("FIXTURE_SPEC_JSON") from None
    _tree(value)
    need(value == SOURCE_FIXTURE_SPEC, "FIXTURE_SPEC_BINDING")
    return value

def fixture_context(spec, case_id):
    need(type(case_id) is str and case_id in ("OPEN", "BLOCKED")
         and spec == SOURCE_FIXTURE_SPEC, "FIXTURE_CONTEXT")
    return dict(spec["cases"][case_id], initial=spec["initial"], caseId=case_id)

def _check_fixture_context(context):
    need(type(context) is dict and context.get("caseId") in ("OPEN", "BLOCKED"), "FIXTURE_CONTEXT")
    expected = fixture_context(SOURCE_FIXTURE_SPEC, context["caseId"])
    # Canonical bytes keep bool distinct from int, including within nested rows.
    need(canonical_line(context) == canonical_line(expected), "FIXTURE_CONTEXT_BINDING")

def _wire_run_order(run):
    keys(run, RUN_KEYS, "RUN_ORDER")
    for name, expected in (("initial", INITIAL_KEYS), ("ordering", ORDERING_KEYS),
                           ("outcome", OUTCOME_KEYS), ("finalizers", FINALIZER_KEYS), ("work", WORK_KEYS)):
        keys(run[name], expected, "RUN_" + name.upper() + "_ORDER")
    if run["terminalFailure"] is not None:
        keys(run["terminalFailure"], TERMINAL_KEYS, "TERMINAL_ORDER")

def _natural_fixture_result(run, context, terminal):
    expected = context["expectedOutcome"]
    outcome, ordering = run["outcome"], run["ordering"]
    need(outcome["disposition"] == expected["disposition"]
         and outcome["reason"] == expected["reason"]
         and ordering["traversedNetIds"] == expected["traversedNetIds"]
         and is_int(ordering["searchCount"], expected["searchCount"], expected["searchCount"]),
         "NATURAL_FIXTURE_OUTCOME")
    if expected["terminalRequired"]:
        need(type(terminal) is dict, "NATURAL_NO_PATH_TERMINAL_REQUIRED")
        need(terminal["failedNetId"] == "NET_A"
             and terminal["searchIndex"] == len(ordering["traversedNetIds"])
             and ordering["traversedNetIds"][-1] == terminal["failedNetId"], "NATURAL_TERMINAL_FINAL_NET")
    else:
        need(terminal is None, "FOUND_TERMINAL_NULL")
    layout = run["layout"]
    if context["caseId"] == "BLOCKED":
        need(layout is None, "BLOCKED_LAYOUT_NULL")
        return
    need(type(layout) is dict, "OPEN_LAYOUT_REQUIRED")
    dimensions = context["layoutDimensions"]
    need(is_int(layout.get("width"), dimensions["width"], dimensions["width"])
         and is_int(layout.get("height"), dimensions["height"], dimensions["height"]), "FIXTURE_LAYOUT_DIMENSIONS")
    for name in ("outline", "partsTray"):
        expected_rect = [context[name][axis] for axis in ("x", "y", "width", "height")]
        rect = layout.get(name)
        need(type(rect) is list and len(rect) == 4
             and all(is_int(value, target, target) for value, target in zip(rect, expected_rect)), "FIXTURE_LAYOUT_RECTANGLE")
    # These rows come exclusively from the source oracle, not exported layout.
    try:
        validate_complete_layout(layout, context["pads"], context["roleByNet"], context["requestedNetIds"])
    except ConductorError as failure:
        raise ReaderError("CONDUCTOR_" + failure.code) from None
    expected_pads = {pad["padId"]: pad for pad in context["pads"]}
    for pad in layout["pads"]:
        source = expected_pads[pad["padId"]]
        need(source["access"] == "PTH" and pad["attachment"] == "PLATED_THROUGH_HOLE"
             and pad["topCopper"] is True and pad["bottomCopper"] is True,
             "FIXTURE_PTH_BINDING")

def validate_packet(value, expected_run_id, expected_source_sha, expected_wire_sha, fixture_spec_raw):
    """Pure semantic admission only; inputs do not confer execution provenance."""
    need(type(expected_run_id) is str and UUID.fullmatch(expected_run_id) is not None
         and type(expected_source_sha) is str and SHA.fullmatch(expected_source_sha) is not None
         and type(expected_wire_sha) is str and expected_wire_sha == WIRE_SHA256, "EXPECTED_CONTEXT")
    spec = load_fixture_spec(fixture_spec_raw)
    _tree(value)
    keys(value, PACKET_KEYS, "PACKET_KEYS")
    need(value["schema"] == "tsj-generic-routing-reference-v1"
         and value["status"] == "GENERIC_NATURAL_REFERENCES_RECORDED_ONLY"
         and value["runId"] == expected_run_id
         and value["sourceManifestSha256"] == expected_source_sha
         and value["wireContractSha256"] == expected_wire_sha, "PACKET_IDENTITY")
    references = value["references"]
    keys(references, ("OPEN", "BLOCKED"), "REFERENCES")
    for ordinal, case_id in enumerate(("OPEN", "BLOCKED")):
        reference = references[case_id]
        keys(reference, REFERENCE_KEYS, "REFERENCE_KEYS")
        need(reference["caseId"] == case_id
             and reference["fixtureContextSha256"] == FIXTURE_SPEC_SHA256, "REFERENCE_IDENTITY")
        _wire_run_order(reference["run"])
        validate_generic_fixture_run(reference["run"], fixture_context(spec, case_id))
        cleanup = reference["cleanup"]
        keys(cleanup, ("ownerReferencesDropped", "ownerCleanupNanos", "wrapperAliasesDropped"), "CLEANUP_KEYS")
        need(cleanup["ownerReferencesDropped"] is True and cleanup["wrapperAliasesDropped"] is True
             and is_int(cleanup["ownerCleanupNanos"], 0, 120000000000), "CLEANUP_BINDING")
        construction = reference["construction"]
        keys(construction, ("before", "after"), "CONSTRUCTION_KEYS")
        need(is_int(construction["before"], ordinal, ordinal)
             and is_int(construction["after"], ordinal + 1, ordinal + 1), "CONSTRUCTOR_BINDING")
        geometry = reference["sourceGeometry"]
        keys(geometry, ("beforeSha256", "afterSha256", "stable"), "SOURCE_GEOMETRY_KEYS")
        need(type(geometry["beforeSha256"]) is str and SHA.fullmatch(geometry["beforeSha256"]) is not None
             and geometry["beforeSha256"] == geometry["afterSha256"] and geometry["stable"] is True, "SOURCE_GEOMETRY_BINDING")
    review = value["sourceReview"]
    keys(review, REVIEW_KEYS, "REVIEW_KEYS")
    need(review["scope"] == "GENERIC_NATURAL_ROUTING_REFERENCE_ONLY"
         and review["physicalOwnerSha256"] == spec["physicalOwnerSha256"]
         and review["studyParentSha256"] == STUDY_PARENT_SHA256
         and review["runOwnerSha256"] == spec["runOwnerSha256"]
         and review["fixtureSourceSha256"] == spec["fixtureSourceSha256"]
         and review["fixtureOracleSha256"] == FIXTURE_SPEC_SHA256
         and is_int(review["sessionConstructorEntries"], 2, 2), "SOURCE_REVIEW_BINDING")
    return {"schema": "tsj-generic-routing-reference-reader-summary-v1",
            "status": "PASS_NATURAL_GENERIC_REFERENCE_ONLY", "runId": expected_run_id,
            "sourceManifestSha256": expected_source_sha, "wireContractSha256": expected_wire_sha,
            "references": 2, "found": 1, "noPath": 1, "newAuthoredInvocations": 0, "fullD": False}

"""Pure current-only R7 result/marker admission kernel.

This source is embedded after the frozen generic-routing T reader definitions.
It intentionally has no CLI, loader, filesystem, producer, or execution-authentication
path. T ReaderError failures retain their SW_* owner; errors introduced here use
GR7_ReaderError with GR7_* codes.
"""

GR7_RESULT_SCHEMA = "tsj-generic-routing-r7-result-v1"
GR7_RESULT_STATUS = "GENERIC_NATURAL_R7_REFERENCE_RECORDED_ONLY"
GR7_MARKER_SCHEMA = "tsj-generic-routing-r7-marker-v1"
GR7_MARKER_STATUS = "GENERIC_NATURAL_R7_REFERENCE_EXPORTED"
GR7_SUMMARY_SCHEMA = "tsj-generic-routing-r7-reader-summary-v1"
GR7_SUMMARY_STATUS = "PASS_GENERIC_R7_CANONICAL_AND_DERIVED_DEMAND_ONLY"
GR7_SCOPE = "GENERIC_NATURAL_ROUTING_R7_REFERENCE_ONLY"
GR7_STUDY_PARENT_SHA256 = "8581ca447ca5d7fde0c56970daa9ea7fc38a1f2ea97eeb8b911621f3ccedfa14"
GR7_MAX_BYTES = 1_048_576
GR7_MAX_MARKER_BYTES = 666
GR7_MAX_PRODUCER_NS = 90_000_000_000
GR7_MAX_GROUPS = 640
GR7_MAX_GROUP_NS = 5_000_000_000
GR7_MAX_GROUP_UNITS = 32_768
GR7_MAX_COPY_ENTRIES = 1_048_576
GR7_COPY_CHUNK = 4096
GR7_PHASE_KEYS = ("units", "groups", "copiedEntries", "copyChunks")
GR7_WORK_KEYS = ("setup", "open", "blocked", "metadata", "preExport", "limits")
GR7_PRE_EXPORT_KEYS = ("units", "groups", "copiedEntries", "copyChunks",
                       "maxGroupNanos", "maximumUnitsPerGroup",
                       "sessionConstructorEntries", "elapsedNanos")
GR7_LIMIT_KEYS = ("producerNanos", "maximumGroups", "maximumGroupNanos",
                  "maximumUnitsPerGroup", "maximumCopiedEntries", "copyChunkSize")
GR7_LIMIT_VALUES = (("producerNanos", GR7_MAX_PRODUCER_NS),
                    ("maximumGroups", GR7_MAX_GROUPS),
                    ("maximumGroupNanos", GR7_MAX_GROUP_NS),
                    ("maximumUnitsPerGroup", GR7_MAX_GROUP_UNITS),
                    ("maximumCopiedEntries", GR7_MAX_COPY_ENTRIES),
                    ("copyChunkSize", GR7_COPY_CHUNK))
GR7_RESULT_KEYS = ("schema", "status", "runId", "sourceManifestSha256",
                   "wireContractSha256", "references", "sourceReview", "work")
GR7_REFERENCE_IDS = ("OPEN", "BLOCKED")
GR7_REFERENCE_KEYS = ("caseId", "fixtureContextSha256", "run", "cleanup",
                      "construction", "sourceGeometry")
GR7_CLEANUP_KEYS = ("ownerReferencesDropped", "ownerCleanupNanos",
                    "wrapperAliasesDropped")
GR7_CONSTRUCTION_KEYS = ("before", "after")
GR7_GEOMETRY_KEYS = ("beforeSha256", "afterSha256", "stable")
GR7_SOURCE_REVIEW_KEYS = ("scope", "physicalOwnerSha256", "studyParentSha256",
                          "runOwnerSha256", "fixtureSourceSha256",
                          "fixtureOracleSha256", "sessionConstructorEntries")
GR7_MARKER_KEYS = ("schema", "status", "runId", "sourceManifestSha256",
                   "wireContractSha256", "fixtureOracleSha256", "resultSha256",
                   "resultBytes", "elapsedNanos", "finalExportNanos", "totalGroups",
                   "totalUnits", "maxGroupNanos", "maximumUnitsPerGroup")


class GR7_ReaderError(ValueError):
    """Path-free kernel rejection; T's separate ReaderError keeps SW_* ownership."""
    def __init__(self, code):
        self.code = "GR7_" + code
        super().__init__(self.code)


def gr7_need(condition, code):
    if not condition:
        raise GR7_ReaderError(code)


def gr7_keys(value, expected, code):
    gr7_need(type(value) is dict and tuple(value) == expected, code)


def gr7_is_int(value, low=0, high=2**63 - 1):
    return type(value) is int and low <= value <= high


def gr7_export_demand(result_bytes, marker_bytes):
    """Return only the formula-derived export demand for canonical byte lengths."""
    gr7_need(gr7_is_int(result_bytes, 1, GR7_MAX_BYTES)
             and gr7_is_int(marker_bytes, 1, GR7_MAX_MARKER_BYTES),
             "EXPORT_LENGTH")
    chunks = (result_bytes + GR7_COPY_CHUNK - 1) // GR7_COPY_CHUNK
    export_units = 5 * chunks + 3
    export_copies = 2 * result_bytes + 32 + marker_bytes
    export_chunks = 2 * chunks + 2
    return {"units": export_units, "copiedEntries": export_copies,
            "copyChunks": export_chunks, "groups": 1}


def gr7_validate_phase(value, phase_name):
    gr7_keys(value, GR7_PHASE_KEYS, "WORK_" + phase_name.upper() + "_KEYS")
    for name in GR7_PHASE_KEYS:
        gr7_need(gr7_is_int(value[name]),
                 "WORK_" + phase_name.upper() + "_INTEGER")
    gr7_need(value["groups"] <= GR7_MAX_GROUPS
             and value["units"] <= GR7_MAX_GROUPS * GR7_MAX_GROUP_UNITS
             and value["units"] <= value["groups"] * GR7_MAX_GROUP_UNITS
             and value["copiedEntries"] <= GR7_MAX_COPY_ENTRIES
             and value["copyChunks"] <= GR7_MAX_COPY_ENTRIES
             and value["copyChunks"] <= value["copiedEntries"],
             "WORK_" + phase_name.upper() + "_BOUNDS")
    if value["copiedEntries"]:
        minimum_chunks = (value["copiedEntries"] + GR7_COPY_CHUNK - 1) // GR7_COPY_CHUNK
        gr7_need(value["copyChunks"] >= minimum_chunks,
                 "WORK_" + phase_name.upper() + "_CHUNK_COVERAGE")


def gr7_validate_run_delta(phase, phase_name, run_work):
    gr7_need(type(run_work) is dict, "WORK_RUN_SHAPE")
    fields = ("ownerUnits", "wrapperUnits", "groups", "copyEntries", "copyChunks",
              "maxGroupNanos", "maximumUnitsPerGroup")
    gr7_need(all(name in run_work and gr7_is_int(run_work[name]) for name in fields),
             "WORK_RUN_COUNTERS")
    gr7_need(run_work["groups"] <= GR7_MAX_GROUPS
             and run_work["ownerUnits"] + run_work["wrapperUnits"]
                 <= run_work["groups"] * GR7_MAX_GROUP_UNITS
             and run_work["copyEntries"] <= GR7_MAX_COPY_ENTRIES
             and run_work["copyChunks"] <= GR7_MAX_COPY_ENTRIES,
             "WORK_RUN_BOUNDS")
    gr7_need(phase["units"] == run_work["ownerUnits"] + run_work["wrapperUnits"]
             and phase["groups"] == run_work["groups"]
             and phase["copiedEntries"] == run_work["copyEntries"]
             and phase["copyChunks"] == run_work["copyChunks"],
             "WORK_" + phase_name.upper() + "_RUN_DELTA")


def gr7_validate_work_ledger(work, open_run_work, blocked_run_work,
                             constructor_entries):
    """Validate the four measured phase maps and the pre-export scalar ledger.

    Run-work mappings are the preserved T per-run facts. Producer-wide totals are
    accepted only from the four explicit phase maps and their exact preExport sum.
    """
    gr7_keys(work, GR7_WORK_KEYS, "WORK_KEYS")
    phases = {}
    for phase_name in ("setup", "open", "blocked", "metadata"):
        phase = work[phase_name]
        gr7_validate_phase(phase, phase_name)
        phases[phase_name] = phase
    gr7_validate_run_delta(phases["open"], "open", open_run_work)
    gr7_validate_run_delta(phases["blocked"], "blocked", blocked_run_work)

    gr7_keys(work["preExport"], GR7_PRE_EXPORT_KEYS, "WORK_PRE_EXPORT_KEYS")
    pre = work["preExport"]
    gr7_need(all(gr7_is_int(pre[name]) for name in
                 ("units", "groups", "copiedEntries", "copyChunks")),
             "WORK_PRE_EXPORT_COUNTERS")
    gr7_need(gr7_is_int(pre["maxGroupNanos"], 1, GR7_MAX_GROUP_NS)
             and gr7_is_int(pre["maximumUnitsPerGroup"], 1, GR7_MAX_GROUP_UNITS)
             and gr7_is_int(pre["sessionConstructorEntries"], 2, 2)
             and gr7_is_int(pre["elapsedNanos"], 1, GR7_MAX_PRODUCER_NS),
             "WORK_PRE_EXPORT_SCALARS")
    gr7_need(gr7_is_int(constructor_entries, 2, 2)
             and pre["sessionConstructorEntries"] == constructor_entries,
             "WORK_CONSTRUCTOR_BINDING")

    gr7_need(pre["units"] == sum(phases[name]["units"] for name in GR7_WORK_KEYS[:4])
             and pre["groups"] == sum(phases[name]["groups"] for name in GR7_WORK_KEYS[:4])
             and pre["copiedEntries"] == sum(
                 phases[name]["copiedEntries"] for name in GR7_WORK_KEYS[:4])
             and pre["copyChunks"] == sum(
                 phases[name]["copyChunks"] for name in GR7_WORK_KEYS[:4]),
             "WORK_PHASE_SUM")
    gr7_need(pre["groups"] <= GR7_MAX_GROUPS
             and pre["units"] <= GR7_MAX_GROUPS * GR7_MAX_GROUP_UNITS
             and pre["units"] <= pre["groups"] * GR7_MAX_GROUP_UNITS
             and pre["copiedEntries"] <= GR7_MAX_COPY_ENTRIES
             and pre["copyChunks"] <= GR7_MAX_COPY_ENTRIES
             and pre["copyChunks"] <= pre["copiedEntries"],
             "WORK_PRE_EXPORT_BOUNDS")
    gr7_need(pre["units"] <= pre["groups"] * pre["maximumUnitsPerGroup"],
             "WORK_PRE_EXPORT_MAXIMUM_CAPACITY")
    if pre["copiedEntries"]:
        minimum_chunks = (pre["copiedEntries"] + GR7_COPY_CHUNK - 1) // GR7_COPY_CHUNK
        gr7_need(pre["copyChunks"] >= minimum_chunks,
                 "WORK_PRE_EXPORT_CHUNK_COVERAGE")
    gr7_need(pre["maxGroupNanos"] >= max(open_run_work["maxGroupNanos"],
                                          blocked_run_work["maxGroupNanos"])
             and pre["maximumUnitsPerGroup"] >= max(
                 open_run_work["maximumUnitsPerGroup"],
                 blocked_run_work["maximumUnitsPerGroup"]),
             "WORK_PRE_EXPORT_MAXIMA")

    gr7_keys(work["limits"], GR7_LIMIT_KEYS, "WORK_LIMIT_KEYS")
    for name, expected in GR7_LIMIT_VALUES:
        gr7_need(gr7_is_int(work["limits"][name], expected, expected),
                 "WORK_LIMIT_VALUE")
    return {name: pre[name] for name in GR7_PRE_EXPORT_KEYS}


def gr7_validate_result_value(value, expected_run_id, expected_source_sha,
                              expected_wire_sha, spec):
    gr7_keys(value, GR7_RESULT_KEYS, "RESULT_KEYS")
    gr7_need(value["schema"] == GR7_RESULT_SCHEMA
             and value["status"] == GR7_RESULT_STATUS
             and value["runId"] == expected_run_id
             and value["sourceManifestSha256"] == expected_source_sha
             and value["wireContractSha256"] == expected_wire_sha,
             "RESULT_IDENTITY")

    references = value["references"]
    gr7_keys(references, GR7_REFERENCE_IDS, "REFERENCES_KEYS")
    for ordinal, case_id in enumerate(GR7_REFERENCE_IDS):
        reference = references[case_id]
        gr7_keys(reference, GR7_REFERENCE_KEYS, "REFERENCE_KEYS")
        gr7_need(reference["caseId"] == case_id
                 and reference["fixtureContextSha256"] == FIXTURE_SPEC_SHA256,
                 "REFERENCE_IDENTITY")
        run = reference["run"]
        _wire_run_order(run)
        validate_generic_fixture_run(run, fixture_context(spec, case_id))

        cleanup = reference["cleanup"]
        gr7_keys(cleanup, GR7_CLEANUP_KEYS, "CLEANUP_KEYS")
        gr7_need(cleanup["ownerReferencesDropped"] is True
                 and cleanup["wrapperAliasesDropped"] is True
                 and gr7_is_int(cleanup["ownerCleanupNanos"], 0, 120_000_000_000),
                 "CLEANUP_BINDING")

        construction = reference["construction"]
        gr7_keys(construction, GR7_CONSTRUCTION_KEYS, "CONSTRUCTION_KEYS")
        gr7_need(gr7_is_int(construction["before"], ordinal, ordinal)
                 and gr7_is_int(construction["after"], ordinal + 1, ordinal + 1),
                 "CONSTRUCTION_BINDING")

        geometry = reference["sourceGeometry"]
        gr7_keys(geometry, GR7_GEOMETRY_KEYS, "SOURCE_GEOMETRY_KEYS")
        gr7_need(type(geometry["beforeSha256"]) is str
                 and SHA.fullmatch(geometry["beforeSha256"]) is not None
                 and geometry["beforeSha256"] == geometry["afterSha256"]
                 and geometry["stable"] is True,
                 "SOURCE_GEOMETRY_BINDING")

    review = value["sourceReview"]
    gr7_keys(review, GR7_SOURCE_REVIEW_KEYS, "SOURCE_REVIEW_KEYS")
    gr7_need(review["scope"] == GR7_SCOPE
             and review["physicalOwnerSha256"] == spec["physicalOwnerSha256"]
             and review["studyParentSha256"] == GR7_STUDY_PARENT_SHA256
             and review["runOwnerSha256"] == spec["runOwnerSha256"]
             and review["fixtureSourceSha256"] == spec["fixtureSourceSha256"]
             and review["fixtureOracleSha256"] == FIXTURE_SPEC_SHA256
             and gr7_is_int(review["sessionConstructorEntries"], 2, 2),
             "SOURCE_REVIEW_BINDING")
    return gr7_validate_work_ledger(
        value["work"], references["OPEN"]["run"]["work"],
        references["BLOCKED"]["run"]["work"],
        review["sessionConstructorEntries"])


def gr7_validate_marker_scalars(marker, marker_raw, result_raw, expected_run_id,
                                expected_source_sha, expected_wire_sha,
                                pre_export):
    gr7_keys(marker, GR7_MARKER_KEYS, "MARKER_KEYS")
    gr7_need(type(marker_raw) is bytes and len(marker_raw) <= GR7_MAX_MARKER_BYTES,
             "MARKER_SIZE")
    gr7_need(marker["schema"] == GR7_MARKER_SCHEMA
             and marker["status"] == GR7_MARKER_STATUS
             and marker["runId"] == expected_run_id
             and marker["sourceManifestSha256"] == expected_source_sha
             and marker["wireContractSha256"] == expected_wire_sha
             and marker["fixtureOracleSha256"] == FIXTURE_SPEC_SHA256,
             "MARKER_IDENTITY")

    result_sha = hashlib.sha256(result_raw).hexdigest()
    gr7_need(type(marker["resultSha256"]) is str
             and SHA.fullmatch(marker["resultSha256"]) is not None
             and marker["resultSha256"] == result_sha
             and gr7_is_int(marker["resultBytes"], 2, GR7_MAX_BYTES)
             and marker["resultBytes"] == len(result_raw),
             "MARKER_RESULT_BINDING")
    gr7_need(gr7_is_int(marker["elapsedNanos"], 1, GR7_MAX_PRODUCER_NS)
             and marker["elapsedNanos"] >= pre_export["elapsedNanos"]
             and gr7_is_int(marker["finalExportNanos"], 1, GR7_MAX_GROUP_NS)
             and marker["elapsedNanos"] >= marker["finalExportNanos"],
             "MARKER_DURATION")

    demand = gr7_export_demand(len(result_raw), len(marker_raw))
    expected_groups = pre_export["groups"] + demand["groups"]
    expected_units = pre_export["units"] + demand["units"]
    expected_copies = pre_export["copiedEntries"] + demand["copiedEntries"]
    expected_chunks = pre_export["copyChunks"] + demand["copyChunks"]
    gr7_need(gr7_is_int(marker["maximumUnitsPerGroup"], 1, GR7_MAX_GROUP_UNITS),
             "MARKER_MAXIMA")
    gr7_need(gr7_is_int(marker["totalGroups"], 1, GR7_MAX_GROUPS)
             and marker["totalGroups"] == expected_groups
             and gr7_is_int(marker["totalUnits"], 0,
                            GR7_MAX_GROUPS * GR7_MAX_GROUP_UNITS)
             and marker["totalUnits"] == expected_units
             and expected_units <= expected_groups * GR7_MAX_GROUP_UNITS
             and expected_units <= expected_groups * marker["maximumUnitsPerGroup"],
             "MARKER_TOTAL_WORK")
    gr7_need(expected_copies <= GR7_MAX_COPY_ENTRIES
             and expected_chunks <= GR7_MAX_COPY_ENTRIES
             and expected_chunks <= expected_copies,
             "MARKER_COPY_CAPACITY")
    expected_max_group = max(pre_export["maxGroupNanos"],
                             marker["finalExportNanos"])
    expected_max_units = max(pre_export["maximumUnitsPerGroup"], demand["units"])
    gr7_need(gr7_is_int(marker["maxGroupNanos"], 1, GR7_MAX_GROUP_NS)
             and marker["maxGroupNanos"] == expected_max_group
             and gr7_is_int(marker["maximumUnitsPerGroup"], 1, GR7_MAX_GROUP_UNITS)
             and marker["maximumUnitsPerGroup"] == expected_max_units,
             "MARKER_MAXIMA")
    return {"derivedExportUnits": demand["units"],
            "derivedExportCopyEntries": demand["copiedEntries"],
            "derivedExportCopyChunks": demand["copyChunks"],
            "derivedExportGroups": demand["groups"]}


def validate_result_and_marker(result_raw, marker_raw, expected_run_id,
                               expected_source_sha, expected_wire_sha,
                               fixture_spec_raw):
    """Admit canonical semantics and derived demand, never native counter proof.

    The caller must separately bind real source/class/tool/argv/receipts, require
    successful zero exit, and verify unsuppressed native after-publication checks
    before asserting actual R7 counter proof.
    """
    gr7_need(type(expected_run_id) is str and UUID.fullmatch(expected_run_id) is not None
             and type(expected_source_sha) is str
             and SHA.fullmatch(expected_source_sha) is not None
             and type(expected_wire_sha) is str
             and SHA.fullmatch(expected_wire_sha) is not None,
             "EXPECTED_CONTEXT")
    spec = load_fixture_spec(fixture_spec_raw)
    result = parse_packet(result_raw)
    marker = parse_packet(marker_raw)
    pre_export = gr7_validate_result_value(
        result, expected_run_id, expected_source_sha, expected_wire_sha, spec)
    derived = gr7_validate_marker_scalars(
        marker, marker_raw, result_raw, expected_run_id, expected_source_sha,
        expected_wire_sha, pre_export)
    return {"schema": GR7_SUMMARY_SCHEMA,
            "status": GR7_SUMMARY_STATUS,
            "runId": expected_run_id,
            "sourceManifestSha256": expected_source_sha,
            "wireContractSha256": expected_wire_sha,
            "fixtureOracleSha256": FIXTURE_SPEC_SHA256,
            "resultSha256": hashlib.sha256(result_raw).hexdigest(),
            "resultBytes": len(result_raw),
            "markerBytes": len(marker_raw),
            "references": 2,
            "found": 1,
            "noPath": 1,
            "derivedExportUnits": derived["derivedExportUnits"],
            "derivedExportCopyEntries": derived["derivedExportCopyEntries"],
            "derivedExportCopyChunks": derived["derivedExportCopyChunks"],
            "derivedExportGroups": derived["derivedExportGroups"],
            "fullD": False,
            "R7actualCounterProof": False}


# Read-only CLI adapter for the embedded T reader and generic R7 kernel.
# Native execution and host receipt authentication remain separate contracts.

R7_WIRE_SHA256 = "4142410d8719f2e3b73edc9481ba9a2a40f0476f19f242844e97b42bde366741"
R7_CLI_FLAGS = ("--result", "--marker", "--run-id", "--source-sha",
                "--wire-sha", "--fixture-spec", "--base-generator", "--io-helper")
R7_PATH_FLAGS = ("--result", "--marker", "--fixture-spec", "--base-generator",
                 "--io-helper")


class R7CLIError(ValueError):
    def __init__(self, code):
        self.code = code if code.startswith("GR7_CLI_") else "GR7_CLI_" + code
        super().__init__(self.code)


def _r7_need(condition, code):
    if not condition:
        raise R7CLIError(code)


def _r7_raw_path(value, code):
    _r7_need(type(value) is str and bool(value) and "\x00" not in value,
             code + "_VALUE")
    if os.name == "nt":
        _r7_need("/" not in value, code + "_SEPARATOR")
        drive = re.match(r"^[A-Za-z]:\\", value)
        unc = value.startswith("\\\\")
        _r7_need(not value.startswith(("\\\\?\\", "\\\\.\\")),
                 code + "_DEVICE_PATH")
        _r7_need(drive is not None or unc, code + "_ABSOLUTE")
        parts = value[2:].split("\\") if unc else value[3:].split("\\")
        _r7_need(all(part not in ("", ".", "..") and ":" not in part
                     and not part.endswith((" ", ".")) for part in parts),
                 code + "_ALIAS")
    else:
        _r7_need(value.startswith("/") and not value.startswith("//"),
                 code + "_ABSOLUTE")
        parts = value.split("/")[1:]
        _r7_need(all(part not in ("", ".", "..") for part in parts),
                 code + "_ALIAS")
    try:
        path = Path(value)
    except (TypeError, ValueError, OSError):
        raise R7CLIError(code + "_PATH") from None
    _r7_need(path.is_absolute() and str(path) == value, code + "_CANONICAL")
    return path


def _r7_path_identity(value, code):
    path = _r7_raw_path(value, code)
    try:
        resolved = path.resolve(strict=True)
        _r7_need(str(resolved) == str(path), code + "_CANONICAL")
        for item in (path, *path.parents):
            info = item.lstat()
            _r7_need(not stat.S_ISLNK(info.st_mode)
                     and not (getattr(info, "st_file_attributes", 0) & 0x400),
                     code + "_REPARSE")
        info = path.lstat()
    except R7CLIError:
        raise
    except (OSError, RuntimeError, ValueError):
        raise R7CLIError(code + "_PATH") from None
    _r7_need(stat.S_ISREG(info.st_mode)
             and not stat.S_ISLNK(info.st_mode)
             and not (getattr(info, "st_file_attributes", 0) & 0x400),
             code + "_FILE")
    return (info.st_dev, info.st_ino, info.st_mode, info.st_size,
            getattr(info, "st_mtime_ns", None), getattr(info, "st_ctime_ns", None),
            getattr(info, "st_file_attributes", 0))


def parse_r7_cli_args(argv):
    _r7_need(type(argv) in (list, tuple) and len(argv) == 2 * len(R7_CLI_FLAGS),
             "ARGUMENTS")
    options = {}
    for at in range(0, len(argv), 2):
        flag, value = argv[at:at + 2]
        _r7_need(type(flag) is str and flag in R7_CLI_FLAGS and flag not in options
                 and type(value) is str and bool(value) and "\x00" not in value
                 and not value.startswith("--"), "ARGUMENTS")
        options[flag] = value
    _r7_need(set(options) == set(R7_CLI_FLAGS), "ARGUMENTS")
    for flag in R7_PATH_FLAGS:
        _r7_raw_path(options[flag], "PATH")
    _r7_need(type(options["--run-id"]) is str
             and UUID.fullmatch(options["--run-id"]) is not None
             and type(options["--source-sha"]) is str
             and SHA.fullmatch(options["--source-sha"]) is not None
             and options["--wire-sha"] == R7_WIRE_SHA256,
             "EXPECTED_CONTEXT")
    normalized_paths = [os.path.normcase(options[flag]) for flag in R7_PATH_FLAGS]
    _r7_need(len(normalized_paths) == len(set(normalized_paths)), "PATH_ROLE_ALIAS")
    return options


def _r7_checked_read(options, flag):
    return checked_read(options[flag], options["--base-generator"], options["--io-helper"])


def _r7_recheck_inputs(options, identities, original):
    for flag in R7_PATH_FLAGS:
        _r7_need(_r7_path_identity(options[flag], flag[2:].upper().replace("-", "_"))
                 == identities[flag], "INPUT_IDENTITY_CHANGED")
    for flag in ("--result", "--marker", "--fixture-spec"):
        _r7_need(_r7_checked_read(options, flag) == original[flag],
                 "INPUT_BYTES_CHANGED")
    for flag in R7_PATH_FLAGS:
        _r7_need(_r7_path_identity(options[flag], flag[2:].upper().replace("-", "_"))
                 == identities[flag], "INPUT_IDENTITY_CHANGED")


def main(argv=None):
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("GR7_PYTHON_ISOLATION_REQUIRED\n")
        return 2
    started = time.monotonic_ns()
    try:
        options = parse_r7_cli_args(sys.argv[1:] if argv is None else argv)
        identities = {flag: _r7_path_identity(
            options[flag], flag[2:].upper().replace("-", "_")) for flag in R7_PATH_FLAGS}
        _r7_need(len(set(identities.values())) == len(identities), "PATH_ROLE_ALIAS")
        original = {}
        for flag in ("--fixture-spec", "--result", "--marker"):
            original[flag] = _r7_checked_read(options, flag)
            _r7_need(_r7_path_identity(
                options[flag], flag[2:].upper().replace("-", "_")) == identities[flag],
                "INPUT_IDENTITY_CHANGED")
        load_fixture_spec(original["--fixture-spec"])
        summary = validate_result_and_marker(
            original["--result"], original["--marker"], options["--run-id"],
            options["--source-sha"], options["--wire-sha"], original["--fixture-spec"])
        _r7_recheck_inputs(options, identities, original)
        _r7_need(time.monotonic_ns() - started <= MAX_READER_NS, "READER_DEADLINE")
        line = canonical_line(summary)
        output = sys.stdout.buffer
        _r7_need(output.write(line) == len(line), "STDOUT_WRITE")
        output.flush()
        return 0
    except (R7CLIError, ReaderError, GR7_ReaderError) as failure:
        code = getattr(failure, "code", "GR7_INTERNAL")
        if type(code) is not str or not code or any(
                char not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789" for char in code):
            code = "GR7_INTERNAL"
        sys.stderr.write(code + "\n")
        return 2
    except Exception:
        sys.stderr.write("GR7_INTERNAL\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
