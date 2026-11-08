"""Source-bound admission for the pinned natural generic reference packet.

This generated reader binds the current host call and its source/class inputs.
It does not admit authored originals, full D, R7, HARD/PSYCHOTIC, or Player.
"""
from __future__ import annotations

import hashlib
import json
import math
import os
import re
import stat
import sys
import time
import types
from pathlib import Path

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

RH_CLASS_SCHEMA = "tsj-copper-free-branch-classes-v1"

RH_PARENT_SCHEMA = "tsj-q100-reservation-provenance-parent-v1"

RH_ROOT_CALL_OWNER_SCHEMA = "tsj-p9-root-call-owner-v1"

RH_P9_ROOT_CALL_SHA256 = "28aab617e686d2a4eec074a46b2e2861f6fa7c008544b33f7b070f0c6d77ea1d"

RH_P9_PROCESS_MODULE_SHA256 = "fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c"

RH_PS5_SHA256 = "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f"

RH_MAX_JSON = 8 * 1024 * 1024

RH_MAX_CLASS_BYTES = 256 * 1024 * 1024

RH_MAX_CLASS_COUNT = 10_000

RH_HEX = re.compile(r"[0-9a-f]{64}\Z")

class RH_ReaderError(ValueError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code if code.startswith("BF_") else "BF_" + code

def RH_need(condition: bool, code: str) -> None:
    if not condition:
        raise RH_ReaderError(code)

def RH_sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()

def RH_exact_keys(value, names, code: str) -> None:
    RH_need(type(value) is dict and set(value) == set(names), code)

def RH_is_int(value, low: int = 0, high: int = 2**63 - 1) -> bool:
    return type(value) is int and low <= value <= high

def RH_is_digest(value) -> bool:
    return type(value) is str and RH_HEX.fullmatch(value) is not None

def RH__pairs(rows):
    result = {}
    for key, value in rows:
        if key in result:
            raise RH_ReaderError("BF_JSON_DUPLICATE_KEY")
        result[key] = value
    return result

def RH__parse_int(text):
    if text == "-0":
        raise RH_ReaderError("BF_JSON_NEGATIVE_ZERO")
    return int(text)

def RH__parse_float(text):
    value = float(text)
    if not math.isfinite(value):
        raise RH_ReaderError("BF_JSON_NONFINITE")
    return value

def RH__reject_constant(_text):
    raise RH_ReaderError("BF_JSON_NONFINITE")

def RH__json_load(raw: bytes):
    RH_need(type(raw) is bytes and not raw.startswith(b"\xef\xbb\xbf"), "JSON_BOM")
    try:
        value = json.loads(raw.decode("utf-8", errors="strict"), object_pairs_hook=RH__pairs,
                           parse_int=RH__parse_int, parse_float=RH__parse_float,
                           parse_constant=RH__reject_constant)
    except RH_ReaderError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError, OverflowError):
        raise RH_ReaderError("BF_JSON_INVALID") from None
    def check(v):
        if type(v) is str:
            RH_need(not any(0xD800 <= ord(c) <= 0xDFFF for c in v), "JSON_SURROGATE")
        elif type(v) is dict:
            for k, item in v.items():
                check(k)
                check(item)
        elif type(v) is list:
            for item in v:
                check(item)
        elif type(v) is float:
            RH_need(math.isfinite(v), "JSON_NONFINITE")
    check(value)
    return value

def RH__is_compact(raw: bytes) -> bool:
    in_string = False
    escaped = False
    for byte in raw:
        if in_string:
            if escaped:
                escaped = False
            elif byte == 0x5C:
                escaped = True
            elif byte == 0x22:
                in_string = False
        elif byte == 0x22:
            in_string = True
        elif byte in b" \t\r\n":
            return False
    return not in_string and not escaped

def RH_strict_compact_line(raw: bytes, code: str):
    RH_need(RH__is_compact(raw), code + "_COMPACT")
    return RH__json_load(raw)

def RH_strict_document(raw: bytes, limit: int = RH_MAX_JSON):
    RH_need(0 < len(raw) <= limit and raw.endswith(b"\n") and not raw.endswith(b"\n\n")
         and b"\r" not in raw and not raw.startswith(b"\xef\xbb\xbf"), "DOCUMENT_FRAMING")
    return RH_strict_compact_line(raw[:-1], "DOCUMENT")

def RH_host_document(raw: bytes, limit: int = RH_MAX_JSON):
    """Parse bounded PowerShell JSON without normalizing or rewriting its bytes."""
    RH_need(type(raw) is bytes and 0 < len(raw) <= limit
         and not raw.startswith(b"\xef\xbb\xbf"), "HOST_DOCUMENT_FRAMING")
    return RH__json_load(raw)

def RH__lstat_regular(path: Path, code: str) -> None:
    try:
        info = path.lstat()
    except OSError:
        raise RH_ReaderError(code) from None
    RH_need(stat.S_ISREG(info.st_mode) and not stat.S_ISLNK(info.st_mode)
         and not (getattr(info, "st_file_attributes", 0) & 0x400), code)

def RH__lstat_directory(path: Path, code: str) -> None:
    try:
        info = path.lstat()
    except OSError:
        raise RH_ReaderError(code) from None
    RH_need(stat.S_ISDIR(info.st_mode) and not stat.S_ISLNK(info.st_mode)
         and not (getattr(info, "st_file_attributes", 0) & 0x400), code)

def RH__read(path: Path, limit: int, code: str) -> bytes:
    RH__lstat_regular(path, code)
    try:
        if path.stat().st_size > limit:
            raise RH_ReaderError(code)
        raw = path.read_bytes()
    except RH_ReaderError:
        raise
    except OSError:
        raise RH_ReaderError(code) from None
    RH_need(len(raw) <= limit, code)
    return raw

def RH__canonical(path: str | Path, code: str, require_exists: bool = True) -> Path:
    try:
        p = Path(path)
        resolved = p.resolve(strict=require_exists)
    except (OSError, RuntimeError, ValueError):
        raise RH_ReaderError(code) from None
    RH_need(p.is_absolute() and os.path.normcase(str(p)) == os.path.normcase(str(resolved)), code)
    for parent in (resolved, *resolved.parents):
        try:
            info = parent.lstat()
        except FileNotFoundError:
            continue
        except OSError:
            raise RH_ReaderError(code) from None
        RH_need(not stat.S_ISLNK(info.st_mode)
             and not (getattr(info, "st_file_attributes", 0) & 0x400), code)
    return resolved

def RH__same_path(left: str | Path, right: str | Path) -> bool:
    try:
        a = Path(left).resolve(strict=False)
        b = Path(right).resolve(strict=False)
    except (OSError, RuntimeError, ValueError):
        return False
    return os.path.normcase(str(a)) == os.path.normcase(str(b))

def RH__same_host_path(value, expected: str | Path, code: str) -> bool:
    RH_need(type(value) is str, code)
    actual = RH__canonical(value, code)
    return RH__same_path(actual, expected)

def RH__validate_owner_call(call_dir: Path, parent_path: Path, parent, request, process,
                         expected_file: str, expected_args: list[str], limit_ms: int,
                         process_module: Path, required_stdout: str | None = None,
                         expected_stderr: str = ""):
    RH__lstat_directory(call_dir, "PROCESS_CALL_DIR")
    owner_raw = RH__read(call_dir / "owner.json", 64 * 1024, "PROCESS_OWNER")
    owner = RH_host_document(owner_raw, 64 * 1024)
    RH_exact_keys(owner, ("schema", "pid", "startTicks", "executable", "script", "request", "createdUtc"),
               "PROCESS_OWNER_SCHEMA")
    RH_need(owner["schema"] == RH_ROOT_CALL_OWNER_SCHEMA and RH_is_int(owner["pid"], 1)
         and type(owner["startTicks"]) is str and owner["startTicks"].isdigit()
         and len(owner["startTicks"]) <= 20 and type(owner["executable"]) is str
         and type(owner["script"]) is str and type(owner["request"]) is str
         and type(owner["createdUtc"]) is str, "PROCESS_OWNER_FIELDS")
    RH_need(RH__same_path(owner["request"], call_dir / "request.json"), "PROCESS_OWNER_REQUEST")
    owner_exe = RH__canonical(owner["executable"], "PROCESS_OWNER_EXECUTABLE")
    owner_script = RH__canonical(owner["script"], "PROCESS_OWNER_SCRIPT")
    owner_script_raw = RH__read(owner_script, 256 * 1024, "PROCESS_OWNER_SCRIPT")
    RH_need(RH_sha(RH__read(owner_exe, 32 * 1024 * 1024, "PROCESS_OWNER_EXECUTABLE")) == RH_PS5_SHA256
         and RH_sha(owner_script_raw) == RH_P9_ROOT_CALL_SHA256
         and str(process_module).encode("utf-8") in owner_script_raw
         and RH_sha(RH__read(process_module, 1024 * 1024, "PROCESS_MODULE")) == RH_P9_PROCESS_MODULE_SHA256,
         "PROCESS_OWNER_TOOL_HASH")

    request_raw = RH__read(call_dir / "request.json", 64 * 1024, "PROCESS_REQUEST")
    on_disk_request = RH_host_document(request_raw, 64 * 1024)
    RH_exact_keys(on_disk_request, ("FilePath", "Arguments", "LimitMs"), "PROCESS_REQUEST_SCHEMA")
    RH_need(on_disk_request == request, "PROCESS_REQUEST_COPY")
    RH_need(RH__same_host_path(request["FilePath"], expected_file, "PROCESS_REQUEST_FILE_PATH")
         and request["Arguments"] == expected_args
         and RH_is_int(request["LimitMs"], 1, 480_000) and request["LimitMs"] == limit_ms,
         "PROCESS_REQUEST_BINDING")

    process_raw = RH__read(call_dir / "process.json", 256 * 1024, "PROCESS_COPY")
    on_disk_process = RH_host_document(process_raw, 256 * 1024)
    RH_exact_keys(on_disk_process, ("FilePath", "Arguments", "ProcessId", "ProcessStartTicks", "ExitCode",
                                 "Stdout", "Stderr", "StdoutLog", "StderrLog", "ProcessRoot",
                                 "TerminationProven"), "PROCESS_SCHEMA")
    RH_need(on_disk_process == process, "PROCESS_COPY")
    RH_need(parent.get("process") == process, "PROCESS_PARENT_BINDING")
    RH_need(type(process["FilePath"]) is str
         and RH__same_host_path(process["FilePath"], expected_file, "PROCESS_FILE_PATH")
         and type(process["Arguments"]) is list and process["Arguments"] == expected_args
         and RH_is_int(process["ProcessId"], 1) and RH_is_int(process["ProcessStartTicks"], 1, 2**63 - 1)
         and type(process["ExitCode"]) is int and process["ExitCode"] == 0
         and type(process["Stdout"]) is str and type(process["Stderr"]) is str
         and type(process["StdoutLog"]) is str and type(process["StderrLog"]) is str
         and type(process["ProcessRoot"]) is str and process["TerminationProven"] is True,
         "PROCESS_FIELDS")
    RH_exact_keys(parent, ("schema", "elapsedMs", "process"), "PROCESS_PARENT_SCHEMA")
    RH_need(parent.get("schema") == RH_PARENT_SCHEMA and RH__is_finite_number(parent.get("elapsedMs"))
         and 0 < parent["elapsedMs"] <= limit_ms, "PROCESS_PARENT_TIMING")
    result_raw = RH__read(call_dir / "result.json", 64 * 1024, "PROCESS_RESULT")
    result = RH_host_document(result_raw, 64 * 1024)
    RH_exact_keys(result, ("status", "elapsedMs", "exitCode", "terminationProven", "stdoutBytes",
                        "stderrBytes", "processRootAbsent", "totalElapsedMs"), "PROCESS_RESULT_SCHEMA")
    RH_need(result["status"] == "RETURNED" and RH__is_finite_number(result["elapsedMs"])
         and result["elapsedMs"] == parent["elapsedMs"] and result["exitCode"] == 0
         and result["terminationProven"] is True and RH_is_int(result["stdoutBytes"])
         and RH_is_int(result["stderrBytes"]) and result["processRootAbsent"] is True
         and RH__is_finite_number(result["totalElapsedMs"])
         and result["totalElapsedMs"] >= result["elapsedMs"], "PROCESS_RESULT_BINDING")
    stream_specs = (("stdout.txt", "Stdout", result["stdoutBytes"]),
                    ("stderr.txt", "Stderr", result["stderrBytes"]))
    for name, field, count in stream_specs:
        raw = RH__read(call_dir / name, 8 * 1024 * 1024, "PROCESS_STREAM")
        RH_need(raw == process[field].encode("utf-8", errors="strict") and len(raw) == count,
             "PROCESS_STREAM_BINDING")
    RH__validate_process_stderr(process["Stderr"], result["stderrBytes"], expected_stderr)
    for field in ("StdoutLog", "StderrLog", "ProcessRoot"):
        try:
            RH_need(not Path(process[field]).exists(), "PROCESS_RESOURCE_REMAINS")
        except (OSError, ValueError):
            raise RH_ReaderError("PROCESS_RESOURCE_PATH") from None
    if required_stdout is not None:
        RH_need(process["Stdout"] == required_stdout, "PROCESS_STDOUT_MARKER")
    return result

def RH__validate_process_stderr(stderr: str, byte_count: int, expected: str = "") -> None:
    RH_need(type(stderr) is str and type(expected) is str and RH_is_int(byte_count)
         and stderr == expected
         and byte_count == len(expected.encode("utf-8", errors="strict")),
         "PROCESS_STDERR")

def RH__is_finite_number(value) -> bool:
    return type(value) in (int, float) and math.isfinite(value)

def RH__validate_class_tree(classes_root: Path, inventory_path: Path, inventory_raw: bytes,
                         manifest_build) -> list[dict]:
    RH_need(RH_sha(inventory_raw) == manifest_build["classInventorySha256"], "CLASS_INVENTORY_HASH")
    inv = RH_strict_document(inventory_raw, 4 * 1024 * 1024)
    RH_need(inventory_raw == (json.dumps(inv, ensure_ascii=True, sort_keys=True,
                                      separators=(",", ":"), allow_nan=False) + "\n").encode("ascii"),
         "CLASS_INVENTORY_CANONICAL")
    RH_exact_keys(inv, ("schema", "files", "count", "bytes"), "CLASS_INVENTORY_SCHEMA")
    RH_need(inv["schema"] == RH_CLASS_SCHEMA and type(inv["files"]) is list
         and RH_is_int(inv["count"], 1, RH_MAX_CLASS_COUNT)
         and inv["count"] == len(inv["files"]) and RH_is_int(inv["bytes"], 1, RH_MAX_CLASS_BYTES),
         "CLASS_INVENTORY_FIELDS")
    RH__lstat_directory(classes_root, "CLASS_ROOT")
    actual = []
    total = 0
    for current, directories, files in os.walk(classes_root, followlinks=False):
        base = Path(current)
        for name in directories + files:
            item = base / name
            try:
                info = item.lstat()
            except OSError:
                raise RH_ReaderError("CLASS_TREE_READ") from None
            RH_need(not stat.S_ISLNK(info.st_mode)
                 and not (getattr(info, "st_file_attributes", 0) & 0x400), "CLASS_TREE_REPARSE")
        for name in files:
            path = base / name
            RH_need(path.suffix == ".class", "CLASS_TREE_FOREIGN_FILE")
            body = RH__read(path, RH_MAX_CLASS_BYTES, "CLASS_TREE_READ")
            total += len(body)
            RH_need(total <= RH_MAX_CLASS_BYTES and len(actual) < RH_MAX_CLASS_COUNT, "CLASS_TREE_BOUND")
            actual.append({"path": path.relative_to(classes_root).as_posix(),
                           "bytes": len(body), "sha256": RH_sha(body)})
    actual.sort(key=lambda row: row["path"])
    previous = None
    for row in inv["files"]:
        RH_exact_keys(row, ("path", "bytes", "sha256"), "CLASS_ROW_SCHEMA")
        RH_need(type(row["path"]) is str and row["path"].endswith(".class")
             and (previous is None or previous < row["path"])
             and RH_is_int(row["bytes"], 1, RH_MAX_CLASS_BYTES) and RH_is_digest(row["sha256"]),
             "CLASS_ROW_FIELDS")
        previous = row["path"]
    RH_need(actual == inv["files"] and len(actual) == inv["count"] and total == inv["bytes"],
         "CLASS_TREE_BINDING")
    return actual


import hashlib
import json
import os
import re
import stat
import sys
import time
from pathlib import Path


ADMISSION_CONTEXT_SCHEMA = "tsj-natural-reference-host-context-v1"
ADMISSION_SUMMARY_SCHEMA = "tsj-source-bound-natural-reference-summary-v1"
ADMISSION_SUMMARY_STATUS = "PASS_SOURCE_BOUND_NATURAL_REFERENCE_ONLY"
ADMISSION_MAX_READER_NS = 180_000_000_000
ADMISSION_PACKET_BYTES = 1_048_576
ADMISSION_COMPILE_STDERR = (
    "warning: [options] bootstrap class path not set in conjunction with -source 1.7\r\n"
    "1 warning\r\n"
)
ADMISSION_CONTEXT_KEYS = (
    "schema", "runId", "sourceManifestSha256", "wireContractSha256",
    "producerManifest", "sourcePaths", "classesRoot", "classInventory",
    "classInventorySha256", "inheritedClassesRoot", "inheritedClassInventory",
    "inheritedClassInventorySha256", "legacyInventory", "gwtUser", "gwtDev",
    "java", "javac", "powershell", "rootCall", "processModule", "compileCall",
    "sourceList", "emptySourcepath", "nativeCall", "fixtureSpec",
    "nativeReceiptProof",
)
ADMISSION_SOURCE_MANIFEST_SHA256 = "df27c7750f61e4d4c959d60988a3381d64b6e94737da785e2b5ff76eaa811ada"
ADMISSION_WIRE_SHA256 = "dd738f0bbac0edc4f4cbaf4a0d2bba80ebeda9a581d79c052e29df8976e73dd6"
ADMISSION_FIXTURE_SHA256 = "5d2b62c98bd229a73be0e54e4b6adcde4d5080fb87046d8bd770455f80c1224a"
ADMISSION_LEGACY_INVENTORY_SHA256 = "7fe14c6f95ae1c99ddfb3f81d975ff7f9ee2a6e1851b09e32d9ca8f0d47889f9"
ADMISSION_NATIVE_PROOF_SHA256 = "aa88f01a93f7a1408268bbec37b9e271b87a7719df304b8e54a991e3d10c208a"
ADMISSION_TOOL_SHA256 = {
    "java": "e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce",
    "javac": "61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64",
    "powershell": "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f",
    "rootCall": "28aab617e686d2a4eec074a46b2e2861f6fa7c008544b33f7b070f0c6d77ea1d",
    "processModule": "fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c",
    "gwtUser": "d3721bcdd7c6855a524801212dd74548967d8fb65cbc212a20890732ff2c6948",
    "gwtDev": "c7321e367fc24e5c7ad97b74d1cf2980c45fbd7cbe1fd412f8a8ba128f685135",
}
ADMISSION_TOOL_LIMITS = {
    "java": 64 * 1024 * 1024,
    "javac": 64 * 1024 * 1024,
    "powershell": 32 * 1024 * 1024,
    "rootCall": 256 * 1024,
    "processModule": 1024 * 1024,
    "gwtUser": 32_024_115,
    "gwtDev": 38_276_750,
}
ADMISSION_SOURCE_ROWS = (
    ("PcbLayerRoutingPrototype.java", "owner", 172_568,
     "7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c"),
    ("CohortTerminalTelemetryStudy.java", "study", 229_656,
     "8581ca447ca5d7fde0c56970daa9ea7fc38a1f2ea97eeb8b911621f3ccedfa14"),
    ("GenericRoutingReferenceCanary.java", "driver", 22_088,
     "68c78e29148d062969943b6ddf77bba15e3ca352ce03b16000eb9dfcded9e9c0"),
)
ADMISSION_FRESH_CLASS_COUNT = 173
ADMISSION_FRESH_CLASS_BYTES = 563_848
ADMISSION_INHERITED_CLASS_COUNT = 4_288
ADMISSION_INHERITED_CLASS_BYTES = 19_644_833
ADMISSION_COMPILE_RECEIPT_NAMES = (
    "owner.json", "parent.json", "request.json", "process.json",
    "result.json", "stdout.txt", "stderr.txt",
)
ADMISSION_CALL_NAMES = ADMISSION_COMPILE_RECEIPT_NAMES + ("wrapper.stdout", "wrapper.stderr")
ADMISSION_CALL_NAME_SET = frozenset(ADMISSION_CALL_NAMES)
ADMISSION_OUTPUT_NAME = "GENERIC_NATURAL_REFERENCES_V1.json"
ADMISSION_RECEIPT_LIMITS = {
    "owner.json": 64 * 1024,
    "parent.json": 256 * 1024,
    "request.json": 64 * 1024,
    "process.json": 256 * 1024,
    "result.json": 64 * 1024,
    "stdout.txt": 8 * 1024 * 1024,
    "stderr.txt": 8 * 1024 * 1024,
    "wrapper.stdout": 64 * 1024,
    "wrapper.stderr": 64 * 1024,
}


def admission_need(condition, code):
    if not condition:
        raise ReaderError("ADMISSION_" + code)


def admission_lexical_path(value, code):
    admission_need(type(value) is str and bool(value) and "\x00" not in value, code)
    try:
        path = Path(value)
        raw_parts = re.split(r"[\\/]", value)
        admission_need(path.is_absolute() and str(path) == value
                       and not any(part in (".", "..") for part in raw_parts), code)
    except (OSError, RuntimeError, TypeError, ValueError):
        raise ReaderError("ADMISSION_" + code) from None
    return path


def admission_canonical_path(value, code, kind=None):
    path = admission_lexical_path(value, code)
    try:
        resolved = RH__canonical(value, "ADMISSION_" + code)
    except RH_ReaderError:
        raise
    admission_need(str(resolved) == value, code)
    if kind == "file":
        RH__lstat_regular(resolved, "ADMISSION_" + code)
    elif kind == "directory":
        RH__lstat_directory(resolved, "ADMISSION_" + code)
    return resolved


def admission_identity(path, kind, code):
    try:
        info = Path(path).lstat()
    except OSError:
        raise ReaderError("ADMISSION_" + code) from None
    attributes = getattr(info, "st_file_attributes", 0)
    admission_need(not stat.S_ISLNK(info.st_mode) and not (attributes & 0x400), code)
    if kind == "file":
        admission_need(stat.S_ISREG(info.st_mode), code)
        return (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns,
                info.st_ctime_ns, attributes)
    admission_need(stat.S_ISDIR(info.st_mode), code)
    return (info.st_dev, info.st_ino, info.st_mode, attributes)


def admission_load_io(base_value, io_value):
    base_path = admission_canonical_path(base_value, "BASE_PATH", "file")
    io_path = admission_canonical_path(io_value, "IO_HELPER_PATH", "file")
    try:
        base = _bootstrap_base(str(base_path))
        io = base.load_io(str(io_path))
    except ReaderError:
        raise
    except Exception:
        raise ReaderError("ADMISSION_CHECKED_IO_BOOTSTRAP") from None
    admission_need(getattr(io, "_sha", None) is not None, "CHECKED_IO_API")
    return io, admission_identity(base_path, "file", "BASE_IDENTITY"), \
        admission_identity(io_path, "file", "IO_IDENTITY")


def admission_read_file(io, path, limit, code):
    before = admission_identity(path, "file", code)
    try:
        if Path(path).lstat().st_size == 0:
            # The exporter intentionally admits nonempty assets only. Empty
            # host streams belong to the existing host reader; retain the
            # surrounding regular-file and before/after identity fences.
            raw = RH__read(Path(path), limit, "ADMISSION_" + code)
            admission_need(raw == b"", code + "_EMPTY_STREAM_CHANGED")
        else:
            raw = io._read_regular(str(path), limit, "ADMISSION_" + code)
    except (ReaderError, RH_ReaderError):
        raise
    except Exception:
        raise ReaderError("ADMISSION_" + code) from None
    admission_need(type(raw) is bytes and len(raw) <= limit, code)
    after = admission_identity(path, "file", code)
    admission_need(before == after, code + "_IDENTITY")
    return raw, after


def admission_file_record(path, raw, identity):
    return (str(path), len(raw), hashlib.sha256(raw).hexdigest(), identity)


def admission_check_file(io, raw_path, limit, role, expected_sha=None, expected_bytes=None):
    path = admission_canonical_path(raw_path, role + "_PATH", "file")
    raw, identity = admission_read_file(io, path, limit, role + "_READ")
    if expected_bytes is not None:
        admission_need(len(raw) == expected_bytes, role + "_BYTES")
    if expected_sha is not None:
        admission_need(hashlib.sha256(raw).hexdigest() == expected_sha, role + "_HASH")
    return raw, admission_file_record(path, raw, identity)


def admission_check_empty_directory(raw_path, role):
    path = admission_canonical_path(raw_path, role + "_PATH", "directory")
    identity = admission_identity(path, "directory", role + "_IDENTITY")
    try:
        names = list(path.iterdir())
    except OSError:
        raise ReaderError("ADMISSION_" + role + "_READ") from None
    admission_need(not names, role + "_NOT_EMPTY")
    return path, identity


def admission_validate_context_values(context):
    RH_exact_keys(context, ADMISSION_CONTEXT_KEYS, "ADMISSION_CONTEXT_KEYS")
    admission_need(context["schema"] == ADMISSION_CONTEXT_SCHEMA, "CONTEXT_SCHEMA")
    admission_need(type(context["runId"]) is str and UUID.fullmatch(context["runId"]) is not None,
                   "CONTEXT_RUN_ID")
    admission_need(context["sourceManifestSha256"] == ADMISSION_SOURCE_MANIFEST_SHA256
                   and context["wireContractSha256"] == ADMISSION_WIRE_SHA256,
                   "CONTEXT_SOURCE_PROFILE")
    for field in ("classInventorySha256", "inheritedClassInventorySha256"):
        admission_need(RH_is_digest(context[field]), "CONTEXT_INVENTORY_HASH")
    RH_exact_keys(context["sourcePaths"], ("owner", "study", "driver"),
                  "ADMISSION_SOURCE_PATH_KEYS")
    path_fields = (
        "producerManifest", "classesRoot", "classInventory", "inheritedClassesRoot",
        "inheritedClassInventory", "legacyInventory", "gwtUser", "gwtDev", "java",
        "javac", "powershell", "rootCall", "processModule", "compileCall", "sourceList",
        "emptySourcepath", "nativeCall", "fixtureSpec", "nativeReceiptProof",
    )
    paths = []
    for field in path_fields:
        paths.append((field, admission_lexical_path(context[field], "CONTEXT_" + field.upper())))
    for role in ("owner", "study", "driver"):
        paths.append(("sourcePaths." + role,
                      admission_lexical_path(context["sourcePaths"][role], "CONTEXT_SOURCE_PATH")))
    keys = [os.path.normcase(str(path)) for _field, path in paths]
    admission_need(len(keys) == len(set(keys)), "CONTEXT_PATH_ALIAS")
    admission_need(Path(context["compileCall"]) != Path(context["nativeCall"]), "CONTEXT_CALL_ALIAS")
    admission_need(Path(context["classesRoot"]) != Path(context["inheritedClassesRoot"]),
                   "CONTEXT_CLASS_ROOT_ALIAS")
    return context


def admission_parse_context(raw, expected_sha):
    admission_need(type(raw) is bytes and hashlib.sha256(raw).hexdigest() == expected_sha,
                   "CONTEXT_EXTERNAL_HASH")
    context = RH_host_document(raw, 1024 * 1024)
    return admission_validate_context_values(context)


def admission_validate_manifest(raw):
    manifest = RH_host_document(raw, RH_MAX_JSON)
    RH_exact_keys(manifest, (
        "classBytes", "classCount", "classes", "classpathOrder", "compile",
        "fixtureOracleSha256", "generatorSha256", "inheritedClosure", "inheritedSources",
        "newAuthoredInvocations", "ownerByteEqualQualifiedP", "ownerClassCount",
        "parentCommit", "schema", "scope", "sourceProofSha256", "sources",
        "wireContractSha256",
    ), "ADMISSION_MANIFEST_KEYS")
    admission_need(manifest["schema"] == "tsj-natural-generic-reference-producer-manifest-v1"
                   and manifest["scope"] == "GENERIC_NATURAL_ROUTING_REFERENCE_ONLY"
                   and manifest["wireContractSha256"] == ADMISSION_WIRE_SHA256
                   and manifest["fixtureOracleSha256"] == ADMISSION_FIXTURE_SHA256
                   and type(manifest["newAuthoredInvocations"]) is int
                   and manifest["newAuthoredInvocations"] == 0
                   and manifest["ownerByteEqualQualifiedP"] is True,
                   "MANIFEST_IDENTITY")
    admission_need(type(manifest["classCount"]) is int
                   and manifest["classCount"] == ADMISSION_FRESH_CLASS_COUNT
                   and type(manifest["classBytes"]) is int
                   and manifest["classBytes"] == ADMISSION_FRESH_CLASS_BYTES
                   and type(manifest["classes"]) is list,
                   "MANIFEST_CLASS_COUNTS")
    expected_sources = tuple((role, size, digest) for role, _key, size, digest in ADMISSION_SOURCE_ROWS)
    admission_need(type(manifest["sources"]) is list and len(manifest["sources"]) == 3,
                   "MANIFEST_SOURCE_COUNT")
    observed_sources = []
    for row in manifest["sources"]:
        RH_exact_keys(row, ("role", "bytes", "sha256"), "ADMISSION_MANIFEST_SOURCE_KEYS")
        observed_sources.append((row["role"], row["bytes"], row["sha256"]))
    admission_need(tuple(observed_sources) == expected_sources, "MANIFEST_SOURCE_PROFILE")
    prior = None
    for row in manifest["classes"]:
        RH_exact_keys(row, ("path", "bytes", "sha256"), "ADMISSION_MANIFEST_CLASS_KEYS")
        admission_need(type(row["path"]) is str and row["path"].endswith(".class")
                       and (prior is None or prior < row["path"])
                       and RH_is_int(row["bytes"], 1, RH_MAX_CLASS_BYTES)
                       and RH_is_digest(row["sha256"]), "MANIFEST_CLASS_ROW")
        prior = row["path"]
    inherited = manifest["inheritedClosure"]
    RH_exact_keys(inherited, ("classBytes", "classFilesRehashed", "inventorySha256", "jars",
                              "toolsRehashed"), "ADMISSION_INHERITED_MANIFEST_KEYS")
    admission_need(inherited["classBytes"] == ADMISSION_INHERITED_CLASS_BYTES
                   and inherited["classFilesRehashed"] == ADMISSION_INHERITED_CLASS_COUNT
                   and inherited["inventorySha256"] == ADMISSION_LEGACY_INVENTORY_SHA256
                   and inherited["toolsRehashed"] is True
                   and type(inherited["jars"]) is list and len(inherited["jars"]) == 2,
                   "MANIFEST_INHERITED_PROFILE")
    expected_jars = (
        ("GWT_DEPENDENCY", ADMISSION_TOOL_LIMITS["gwtUser"], ADMISSION_TOOL_SHA256["gwtUser"]),
        ("GWT_DEPENDENCY", ADMISSION_TOOL_LIMITS["gwtDev"], ADMISSION_TOOL_SHA256["gwtDev"]),
    )
    observed_jars = []
    for row in inherited["jars"]:
        RH_exact_keys(row, ("bytes", "role", "sha256"), "ADMISSION_MANIFEST_JAR_KEYS")
        observed_jars.append((row["role"], row["bytes"], row["sha256"]))
    admission_need(tuple(observed_jars) == expected_jars, "MANIFEST_JAR_PROFILE")
    compile_meta = manifest["compile"]
    RH_exact_keys(compile_meta, (
        "childExitCode", "isolatedCleanupDurationMs", "ownedRootAndLogsAbsent", "phase",
        "processElapsedMs", "receipts", "status", "terminationProven", "wrapperTotalElapsedMs",
    ), "ADMISSION_COMPILE_MANIFEST_KEYS")
    admission_need(compile_meta["phase"] == "compile-producer-r1"
                   and compile_meta["status"] == "PASS"
                   and type(compile_meta["childExitCode"]) is int and compile_meta["childExitCode"] == 0
                   and compile_meta["terminationProven"] is True
                   and compile_meta["ownedRootAndLogsAbsent"] is True
                   and compile_meta["isolatedCleanupDurationMs"] is None
                   and RH__is_finite_number(compile_meta["processElapsedMs"])
                   and 0 < compile_meta["processElapsedMs"] <= 60_000
                   and RH__is_finite_number(compile_meta["wrapperTotalElapsedMs"])
                   and compile_meta["wrapperTotalElapsedMs"] >= compile_meta["processElapsedMs"],
                   "MANIFEST_COMPILE_PROFILE")
    RH_exact_keys(compile_meta["receipts"], ADMISSION_COMPILE_RECEIPT_NAMES,
                  "ADMISSION_COMPILE_RECEIPT_KEYS")
    for digest in compile_meta["receipts"].values():
        admission_need(RH_is_digest(digest), "MANIFEST_COMPILE_RECEIPT_HASH")
    return manifest


def admission_validate_legacy_inventory(raw):
    inventory = RH_host_document(raw, 4 * 1024 * 1024)
    RH_exact_keys(inventory, ("schema", "entries", "classCount", "totalBytes", "sourceManifestSha256"),
                  "ADMISSION_LEGACY_INVENTORY_KEYS")
    admission_need(inventory["schema"] == "tsj-failure-cohort-classes-v1"
                   and type(inventory["entries"]) is list
                   and type(inventory["classCount"]) is int
                   and inventory["classCount"] == ADMISSION_INHERITED_CLASS_COUNT
                   and type(inventory["totalBytes"]) is int
                   and inventory["totalBytes"] == ADMISSION_INHERITED_CLASS_BYTES
                   and RH_is_digest(inventory["sourceManifestSha256"])
                   and len(inventory["entries"]) == inventory["classCount"],
                   "LEGACY_INVENTORY_PROFILE")
    prior = None
    for row in inventory["entries"]:
        RH_exact_keys(row, ("path", "bytes", "sha256"), "ADMISSION_LEGACY_CLASS_KEYS")
        admission_need(type(row["path"]) is str and row["path"].endswith(".class")
                       and (prior is None or prior < row["path"])
                       and RH_is_int(row["bytes"], 1, RH_MAX_CLASS_BYTES)
                       and RH_is_digest(row["sha256"]), "LEGACY_INVENTORY_ROW")
        prior = row["path"]
    return inventory


def admission_inventory_bytes(files, count, total):
    value = {"schema": RH_CLASS_SCHEMA, "files": files, "count": count, "bytes": total}
    return (json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(",", ":"),
                       allow_nan=False) + "\n").encode("ascii")


def admission_tree_snapshot(root):
    RH__lstat_directory(root, "ADMISSION_CLASS_ROOT")
    root_identity = admission_identity(root, "directory", "CLASS_ROOT_IDENTITY")
    rows = []
    count = 0
    total = 0
    entries = [("", "D", root_identity)]
    for current, directories, files in os.walk(root, topdown=True, followlinks=False):
        directories.sort()
        files.sort()
        base = Path(current)
        for name in directories:
            path = base / name
            try:
                info = path.lstat()
            except OSError:
                raise ReaderError("ADMISSION_CLASS_TREE_READ") from None
            attributes = getattr(info, "st_file_attributes", 0)
            admission_need(stat.S_ISDIR(info.st_mode) and not stat.S_ISLNK(info.st_mode)
                           and not (attributes & 0x400), "CLASS_TREE_REPARSE")
            rel = path.relative_to(root).as_posix()
            entries.append((rel, "D", admission_identity(path, "directory", "CLASS_TREE_READ")))
        for name in files:
            path = base / name
            try:
                info = path.lstat()
            except OSError:
                raise ReaderError("ADMISSION_CLASS_TREE_READ") from None
            attributes = getattr(info, "st_file_attributes", 0)
            admission_need(stat.S_ISREG(info.st_mode) and not stat.S_ISLNK(info.st_mode)
                           and not (attributes & 0x400) and path.suffix == ".class",
                           "CLASS_TREE_FOREIGN_FILE")
            count += 1
            total += info.st_size
            admission_need(count <= RH_MAX_CLASS_COUNT and total <= RH_MAX_CLASS_BYTES,
                           "CLASS_TREE_BOUND")
            rows.append((path.relative_to(root).as_posix(), info.st_size))
            entries.append((path.relative_to(root).as_posix(), "F",
                            admission_identity(path, "file", "CLASS_TREE_READ")))
    entries.sort(key=lambda item: (item[0], item[1]))
    rows.sort()
    return {"rows": tuple(rows), "entries": tuple(entries), "count": count, "bytes": total}


def admission_validate_tree(root_value, inventory_path_value, inventory_raw, inventory_sha,
                            expected_rows, expected_count, expected_bytes):
    root = admission_canonical_path(root_value, "CLASS_ROOT_PATH", "directory")
    inventory_path = admission_canonical_path(inventory_path_value, "CLASS_INVENTORY_PATH", "file")
    admission_need(hashlib.sha256(inventory_raw).hexdigest() == inventory_sha,
                   "CLASS_INVENTORY_CONTEXT_HASH")
    sidecar = RH_strict_document(inventory_raw, 4 * 1024 * 1024)
    admission_need(inventory_raw == admission_inventory_bytes(expected_rows, expected_count, expected_bytes),
                   "CLASS_INVENTORY_EXPECTED_BYTES")
    before = admission_tree_snapshot(root)
    actual = RH__validate_class_tree(
        root, inventory_path, inventory_raw, {"classInventorySha256": inventory_sha})
    after = admission_tree_snapshot(root)
    admission_need(before == after, "CLASS_TREE_CHANGED_DURING_CHECK")
    admission_need(actual == expected_rows and before["count"] == expected_count
                   and before["bytes"] == expected_bytes,
                   "CLASS_TREE_EXPECTED_ROWS")
    return {"snapshot": after, "inventory": sidecar}


def admission_expected_invocations(context):
    classpath = ";".join((context["classesRoot"], context["inheritedClassesRoot"],
                           context["gwtUser"], context["gwtDev"]))
    compile_args = [
        "-source", "7", "-target", "7", "-encoding", "UTF-8",
        "-classpath", classpath, "-sourcepath", context["emptySourcepath"],
        "-d", context["classesRoot"], "@" + context["sourceList"],
    ]
    native_args = [
        "-Xmx1024m", "-cp", classpath,
        "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy",
        "GENERIC_ROUTING_REFERENCE_V1", context["runId"],
        context["sourceManifestSha256"], context["wireContractSha256"],
    ]
    return {
        "compile": (context["javac"], compile_args, 60_000, ADMISSION_COMPILE_STDERR),
        "native": (context["java"], native_args, 480_000, ""),
    }


def admission_snapshot_call(io, context, call_role, expected_file, expected_args, limit_ms,
                            expected_stderr, expected_receipts):
    call_path = admission_canonical_path(context[call_role], call_role.upper() + "_PATH", "directory")
    directory_identity = admission_identity(call_path, "directory", call_role + "_IDENTITY")
    try:
        names = sorted(entry.name for entry in os.scandir(call_path))
    except OSError:
        raise ReaderError("ADMISSION_" + call_role.upper() + "_DIRECTORY_READ") from None
    admission_need(tuple(names) == tuple(sorted(ADMISSION_CALL_NAMES)),
                   call_role.upper() + "_EXACT_NINE_FILES")
    raw_by_name = {}
    file_records = {}
    for name in ADMISSION_CALL_NAMES:
        raw, record = admission_check_file(
            io, str(call_path / name), ADMISSION_RECEIPT_LIMITS[name],
            call_role.upper() + "_" + name.replace(".", "_").upper())
        raw_by_name[name] = raw
        file_records[name] = record
    result = admission_validate_call_envelope(call_role, raw_by_name, expected_receipts)
    owner = RH_host_document(raw_by_name["owner.json"], 64 * 1024)
    parent = RH_host_document(raw_by_name["parent.json"], 256 * 1024)
    request = RH_host_document(raw_by_name["request.json"], 64 * 1024)
    process = RH_host_document(raw_by_name["process.json"], 256 * 1024)
    admission_need(type(owner) is dict
                   and RH__same_host_path(owner.get("executable"), context["powershell"],
                                          "ADMISSION_OWNER_EXECUTABLE")
                   and RH__same_host_path(owner.get("script"), context["rootCall"],
                                          "ADMISSION_OWNER_SCRIPT")
                   and RH__same_host_path(owner.get("request"), call_path / "request.json",
                                          "ADMISSION_OWNER_REQUEST"),
                   call_role.upper() + "_OWNER_BINDING")
    RH_exact_keys(request, ("FilePath", "Arguments", "LimitMs"), "ADMISSION_REQUEST_KEYS")
    admission_need(request["FilePath"] == expected_file
                   and request["Arguments"] == expected_args
                   and type(request["LimitMs"]) is int and request["LimitMs"] == limit_ms,
                   call_role.upper() + "_INDEPENDENT_REQUEST")
    admission_need(raw_by_name["stdout.txt"] == b"" if call_role == "compileCall" else True,
                   "COMPILE_STDOUT_EMPTY")
    kernel_result = RH__validate_owner_call(
        call_path, call_path / "parent.json", parent, request, process,
        expected_file, expected_args, limit_ms, Path(context["processModule"]),
        expected_stderr=expected_stderr)
    admission_need(kernel_result == result, call_role.upper() + "_KERNEL_RESULT")
    admission_need(process.get("FilePath") == expected_file
                   and process.get("Arguments") == expected_args,
                   call_role.upper() + "_PROCESS_ARGUMENTS")
    admission_need(parent.get("process") == process, call_role.upper() + "_PARENT_PROCESS")
    for field in ("StdoutLog", "StderrLog", "ProcessRoot"):
        admission_resource_absent(process.get(field), call_role.upper() + "_" + field.upper())
    admission_need(directory_identity == admission_identity(call_path, "directory", call_role + "_IDENTITY"),
                   call_role.upper() + "_DIRECTORY_CHANGED")
    snapshots = {
        "directory": (str(call_path), directory_identity),
        "files": tuple((name, file_records[name]) for name in ADMISSION_CALL_NAMES),
    }
    return {"snapshot": snapshots, "parent": parent, "result": result,
            "process": process, "stdout": raw_by_name["stdout.txt"]}


def admission_validate_call_envelope(call_role, raw_by_name, expected_receipts):
    admission_need(raw_by_name["wrapper.stderr"] == b"", call_role.upper() + "_WRAPPER_STDERR")
    result = RH_host_document(raw_by_name["result.json"], 64 * 1024)
    wrapper_result = RH_host_document(raw_by_name["wrapper.stdout"], 64 * 1024)
    admission_need(wrapper_result == result, call_role.upper() + "_WRAPPER_RESULT")
    admission_need(type(result.get("exitCode")) is int and result["exitCode"] == 0,
                   call_role.upper() + "_RESULT_EXIT_CODE_TYPE")
    RH_exact_keys(expected_receipts, ADMISSION_COMPILE_RECEIPT_NAMES,
                  "ADMISSION_EXPECTED_RECEIPT_KEYS")
    for name in ADMISSION_COMPILE_RECEIPT_NAMES:
        admission_need(hashlib.sha256(raw_by_name[name]).hexdigest() == expected_receipts[name],
                       call_role.upper() + "_RECEIPT_PIN_" + name.replace(".", "_").upper())
    return result


def admission_resource_absent(value, role):
    admission_lexical_path(value, role + "_PATH")
    try:
        remains = os.path.lexists(value)
    except (OSError, ValueError):
        raise ReaderError("ADMISSION_" + role + "_PATH") from None
    admission_need(not remains, role + "_REMAINS")


def admission_validate_native_proof(raw, context):
    proof = RH_host_document(raw, RH_MAX_JSON)
    try:
        acceptance = proof["gates"]["ACCEPTANCE_R1"]
        proof_context = acceptance["context"]
        native = acceptance["native"]
        receipts = native["receipts"]
    except (KeyError, TypeError):
        raise ReaderError("ADMISSION_NATIVE_PROOF_SHAPE") from None
    admission_need(type(proof_context) is dict
                   and proof_context.get("runId") == context["runId"]
                   and proof_context.get("sourceSha256") == context["sourceManifestSha256"]
                   and proof_context.get("wireSha256") == context["wireContractSha256"],
                   "NATIVE_PROOF_CONTEXT")
    RH_exact_keys(receipts, ADMISSION_COMPILE_RECEIPT_NAMES, "ADMISSION_NATIVE_PROOF_RECEIPTS")
    for digest in receipts.values():
        admission_need(RH_is_digest(digest), "NATIVE_PROOF_RECEIPT_HASH")
    return receipts


def admission_tree_context(io, context, manifest, legacy, file_snapshots):
    fresh_raw, fresh_record = admission_check_file(
        io, context["classInventory"], 4 * 1024 * 1024, "FRESH_INVENTORY",
        context["classInventorySha256"])
    inherited_raw, inherited_record = admission_check_file(
        io, context["inheritedClassInventory"], 4 * 1024 * 1024, "INHERITED_INVENTORY",
        context["inheritedClassInventorySha256"])
    admission_need(fresh_raw == admission_inventory_bytes(
        manifest["classes"], ADMISSION_FRESH_CLASS_COUNT, ADMISSION_FRESH_CLASS_BYTES),
        "FRESH_INVENTORY_CONTENT")
    admission_need(inherited_raw == admission_inventory_bytes(
        legacy["entries"], ADMISSION_INHERITED_CLASS_COUNT, ADMISSION_INHERITED_CLASS_BYTES),
        "INHERITED_INVENTORY_CONTENT")
    fresh = admission_validate_tree(
        context["classesRoot"], context["classInventory"], fresh_raw,
        context["classInventorySha256"], manifest["classes"],
        ADMISSION_FRESH_CLASS_COUNT, ADMISSION_FRESH_CLASS_BYTES)
    inherited = admission_validate_tree(
        context["inheritedClassesRoot"], context["inheritedClassInventory"], inherited_raw,
        context["inheritedClassInventorySha256"], legacy["entries"],
        ADMISSION_INHERITED_CLASS_COUNT, ADMISSION_INHERITED_CLASS_BYTES)
    file_snapshots["freshInventory"] = fresh_record
    file_snapshots["inheritedInventory"] = inherited_record
    return fresh, inherited


def admission_validate_material(io, context, expected_context_raw, context_path,
                                context_sha, file_snapshots, directory_snapshots):
    context_raw, context_record = admission_check_file(
        io, str(context_path), 1024 * 1024, "CONTEXT", context_sha)
    admission_need(context_raw == expected_context_raw, "CONTEXT_RECHECK")
    if "context" in file_snapshots:
        admission_need(file_snapshots["context"] == context_record, "CONTEXT_REPLACED_BEFORE_CHECK")
    context = admission_parse_context(context_raw, context_sha)
    file_snapshots["context"] = context_record

    manifest_raw, manifest_record = admission_check_file(
        io, context["producerManifest"], RH_MAX_JSON, "PRODUCER_MANIFEST",
        ADMISSION_SOURCE_MANIFEST_SHA256)
    manifest = admission_validate_manifest(manifest_raw)
    file_snapshots["producerManifest"] = manifest_record

    proof_raw, proof_record = admission_check_file(
        io, context["nativeReceiptProof"], RH_MAX_JSON, "NATIVE_RECEIPT_PROOF",
        ADMISSION_NATIVE_PROOF_SHA256)
    native_receipts = admission_validate_native_proof(proof_raw, context)
    file_snapshots["nativeReceiptProof"] = proof_record

    fixture_raw, fixture_record = admission_check_file(
        io, context["fixtureSpec"], 1024 * 1024, "FIXTURE_SPEC", ADMISSION_FIXTURE_SHA256)
    load_fixture_spec(fixture_raw)
    file_snapshots["fixtureSpec"] = fixture_record

    legacy_raw, legacy_record = admission_check_file(
        io, context["legacyInventory"], 4 * 1024 * 1024, "LEGACY_INVENTORY",
        ADMISSION_LEGACY_INVENTORY_SHA256)
    legacy = admission_validate_legacy_inventory(legacy_raw)
    file_snapshots["legacyInventory"] = legacy_record

    fresh, inherited = admission_tree_context(io, context, manifest, legacy, file_snapshots)
    tree_snapshots = {"fresh": fresh["snapshot"], "inherited": inherited["snapshot"]}

    for role, key, size, digest in ADMISSION_SOURCE_ROWS:
        path = context["sourcePaths"][key]
        _raw, record = admission_check_file(io, path, size, "SOURCE_" + key.upper(), digest, size)
        file_snapshots["source." + key] = record

    for role, limit in ADMISSION_TOOL_LIMITS.items():
        expected_bytes = limit if role in ("gwtUser", "gwtDev") else None
        _raw, record = admission_check_file(
            io, context[role], limit, "TOOL_" + role.upper(),
            ADMISSION_TOOL_SHA256[role], expected_bytes)
        file_snapshots["tool." + role] = record

    source_paths = [context["sourcePaths"][key] for _role, key, _size, _digest in ADMISSION_SOURCE_ROWS]
    expected_source_list = "".join('"' + path.replace("\\", "/") + '"\n'
                                   for path in source_paths).encode("utf-8")
    source_list_raw, source_list_record = admission_check_file(
        io, context["sourceList"], 2 * 1024 * 1024, "SOURCE_LIST")
    admission_need(source_list_raw == expected_source_list, "SOURCE_LIST_ORDER_AND_BYTES")
    file_snapshots["sourceList"] = source_list_record

    empty_source, empty_source_identity = admission_check_empty_directory(
        context["emptySourcepath"], "EMPTY_SOURCEPATH")
    directory_snapshots["emptySourcepath"] = (str(empty_source), empty_source_identity)

    invocations = admission_expected_invocations(context)
    compile_file, compile_args, compile_limit, compile_stderr = invocations["compile"]
    compile_result = admission_snapshot_call(
        io, context, "compileCall", compile_file, compile_args, compile_limit,
        compile_stderr, manifest["compile"]["receipts"])
    admission_need(compile_result["parent"]["elapsedMs"] == manifest["compile"]["processElapsedMs"]
                   and compile_result["result"]["totalElapsedMs"]
                   == manifest["compile"]["wrapperTotalElapsedMs"],
                   "COMPILE_TIMING_BINDING")
    native_file, native_args, native_limit, native_stderr = invocations["native"]
    native_result = admission_snapshot_call(
        io, context, "nativeCall", native_file, native_args, native_limit,
        native_stderr, native_receipts)
    call_snapshots = {"compile": compile_result["snapshot"], "native": native_result["snapshot"]}
    packet_raw = native_result["stdout"]
    packet = parse_packet(packet_raw)
    semantic = validate_packet(packet, context["runId"], context["sourceManifestSha256"],
                               context["wireContractSha256"], fixture_raw)
    admission_need(semantic["references"] == 2 and semantic["found"] == 1
                   and semantic["noPath"] == 1 and semantic["newAuthoredInvocations"] == 0
                   and semantic["fullD"] is False,
                   "NATURAL_PACKET_SUMMARY")
    return {
        "context": context, "packetRaw": packet_raw, "packet": packet,
        "semantic": semantic, "files": file_snapshots,
        "directories": directory_snapshots, "trees": tree_snapshots,
        "calls": call_snapshots,
    }


def admission_compare_material(left, right):
    admission_need(left["files"] == right["files"], "CONSUMED_FILE_REPLACED")
    admission_need(left["directories"] == right["directories"], "CONSUMED_DIRECTORY_REPLACED")
    admission_need(left["trees"] == right["trees"], "CLASS_TREE_REPLACED")
    admission_need(left["calls"] == right["calls"], "RECEIPT_REPLACED")
    admission_need(left["packetRaw"] == right["packetRaw"], "PACKET_REPLACED")


def admission_output_state(io, output_dir, expected_names):
    try:
        path = Path(io._checked_directory(str(output_dir), "OUTPUT_DIRECTORY"))
        names = io._directory_names(path, "OUTPUT_DIRECTORY")
    except (ReaderError, RH_ReaderError):
        raise
    except Exception:
        raise ReaderError("ADMISSION_OUTPUT_DIRECTORY_CHECK") from None
    path = admission_canonical_path(str(path), "OUTPUT_DIRECTORY", "directory")
    identity = admission_identity(path, "directory", "OUTPUT_DIRECTORY_IDENTITY")
    admission_need(sorted(names) == expected_names, "OUTPUT_DIRECTORY_CLOSURE")
    for name in names:
        admission_canonical_path(str(path / name), "OUTPUT_ENTRY", "file")
    return path, identity


def admission_write_exclusive(io, output_dir, raw):
    output_dir = admission_canonical_path(str(output_dir), "OUTPUT_DIRECTORY", "directory")
    output = output_dir / ADMISSION_OUTPUT_NAME
    admission_lexical_path(str(output), "OUTPUT_FILE_PATH")
    try:
        checked = Path(io._checked_directory(str(output_dir), "OUTPUT_DIRECTORY"))
        admission_need(not io._directory_names(checked, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
        io._write_exclusive(checked / ADMISSION_OUTPUT_NAME, raw,
                            ADMISSION_PACKET_BYTES, "OUTPUT_FILE")
    except (ReaderError, RH_ReaderError):
        raise
    except Exception:
        raise ReaderError("ADMISSION_OUTPUT_EXCLUSIVE_WRITE") from None
    return output


def parse_args(argv=None):
    if argv is None:
        argv = sys.argv[1:]
    flags = ("--context", "--context-sha", "--output-dir", "--base-generator", "--io-helper")
    admission_need(type(argv) in (list, tuple) and len(argv) == 2 * len(flags), "CLI_ARGUMENTS")
    options = {}
    for at in range(0, len(argv), 2):
        flag, value = argv[at:at + 2]
        admission_need(type(flag) is str and flag in flags and flag not in options
                       and type(value) is str and bool(value) and "\x00" not in value
                       and not value.startswith("--"), "CLI_ARGUMENTS")
        options[flag] = value
    admission_need(set(options) == set(flags), "CLI_ARGUMENTS")
    admission_need(RH_is_digest(options["--context-sha"]), "CONTEXT_SHA_ARGUMENT")
    for flag in ("--context", "--output-dir", "--base-generator", "--io-helper"):
        admission_lexical_path(options[flag], "CLI_PATH")
    return options


def admission_main(options):
    context_path = admission_canonical_path(options["--context"], "CONTEXT_PATH", "file")
    output_dir, output_identity = admission_check_empty_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    io, base_identity, io_identity = admission_load_io(options["--base-generator"], options["--io-helper"])
    context_raw, context_record = admission_check_file(
        io, str(context_path), 1024 * 1024, "CONTEXT", options["--context-sha"])
    context = admission_parse_context(context_raw, options["--context-sha"])
    initial_files = {"context": context_record}
    initial_dirs = {"outputDir": (str(output_dir), output_identity)}
    initial = admission_validate_material(
        io, context, context_raw, context_path, options["--context-sha"], initial_files, initial_dirs)
    raw_packet = initial["packetRaw"]
    output = admission_write_exclusive(io, output_dir, raw_packet)
    admission_canonical_path(str(output), "OUTPUT_FILE_PATH", "file")
    output_read, output_record = admission_read_file(
        io, output, ADMISSION_PACKET_BYTES, "OUTPUT_READBACK")
    admission_need(output_read == raw_packet, "OUTPUT_READBACK_BYTES")
    expected_output_names = [ADMISSION_OUTPUT_NAME]
    output_dir_after, output_identity_after = admission_output_state(io, output_dir, expected_output_names)
    admission_need(str(output_dir_after) == str(output_dir)
                   and (output_identity_after[0], output_identity_after[1])
                   == (output_identity[0], output_identity[1]), "OUTPUT_DIRECTORY_IDENTITY")

    io_after, base_identity_after, io_identity_after = admission_load_io(
        options["--base-generator"], options["--io-helper"])
    admission_need(base_identity_after == base_identity and io_identity_after == io_identity,
                   "CHECKED_IO_REPLACED")
    final_files = {}
    final_dirs = {"outputDir": (str(output_dir), output_identity)}
    final = admission_validate_material(
        io_after, context, context_raw, context_path, options["--context-sha"], final_files, final_dirs)
    admission_compare_material(initial, final)
    io_final, base_identity_final, io_identity_final = admission_load_io(
        options["--base-generator"], options["--io-helper"])
    admission_need(base_identity_final == base_identity and io_identity_final == io_identity,
                   "CHECKED_IO_CHANGED_DURING_RECHECK")
    final_output, final_output_identity = admission_output_state(io_final, output_dir, expected_output_names)
    admission_need(str(final_output) == str(output_dir)
                   and (final_output_identity[0], final_output_identity[1])
                   == (output_identity[0], output_identity[1]), "OUTPUT_DIRECTORY_REPLACED")
    output_read_after, final_output_record = admission_read_file(
        io_final, output, ADMISSION_PACKET_BYTES, "OUTPUT_FINAL_READBACK")
    admission_need(output_read_after == raw_packet and output_record == final_output_record,
                   "OUTPUT_FINAL_READBACK_BYTES")
    semantic = final["semantic"]
    summary = {
        "schema": ADMISSION_SUMMARY_SCHEMA,
        "status": ADMISSION_SUMMARY_STATUS,
        "contextSha256": options["--context-sha"],
        "runId": context["runId"],
        "sourceManifestSha256": context["sourceManifestSha256"],
        "wireContractSha256": context["wireContractSha256"],
        "references": semantic["references"],
        "found": semantic["found"],
        "noPath": semantic["noPath"],
        "inputSha256": hashlib.sha256(raw_packet).hexdigest(),
        "inputBytes": len(raw_packet),
        "sourceClassesToolsBound": True,
        "compileAndNativeReceiptsBound": True,
        "ownedRootsAndLogsAbsent": True,
        "exclusiveWriteReadback": True,
        "consumedInputsRechecked": True,
        "newNativeProducerInvocations": 0,
        "newAuthoredInvocations": 0,
        "fullD": False,
        "R7": False,
    }
    return canonical_line(summary)


def main(argv=None):
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("SW_PYTHON_ISOLATION_REQUIRED\n")
        return 2
    started = time.monotonic_ns()
    try:
        options = parse_args(sys.argv[1:] if argv is None else argv)
        line = admission_main(options)
        need(time.monotonic_ns() - started <= ADMISSION_MAX_READER_NS, "READER_DEADLINE")
        output = sys.stdout.buffer
        admission_need(output.write(line) == len(line), "STDOUT_WRITE")
        output.flush()
        return 0
    except (ReaderError, RH_ReaderError) as failure:
        code = getattr(failure, "code", "ADMISSION_FAILURE")
        if type(code) is not str or re.fullmatch(r"[A-Z0-9_]{1,64}", code) is None:
            code = "ADMISSION_FAILURE"
        sys.stderr.write(code + "\n")
        return 2
    except Exception:
        sys.stderr.write("ADMISSION_INTERNAL\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
