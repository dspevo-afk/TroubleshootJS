"""Independent generic V2 domain reader; no authored-result or execution proof.

validate_packet is pure. The CLI loads only byte-pinned public checked-I/O code,
reads one bounded packet, and emits a semantic summary. Execution/source/class
authentication and exclusive accepted-artifact materialization belong to the host.
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

"""Independent, profile-neutral observation V2 validation.

This source is embedded verbatim in standalone consumers. It owns no I/O,
producer imports, retained input references, or mutable accounting state.
"""

import types as _ov2_types


_ov2_MAX_INT = 2147483647
_ov2_MIN_INT = -2147483648
_ov2_OBSERVATION_KEYS = (
    "schema", "mode", "state", "callbackCount", "foundCallbackCount",
    "nonFoundCallbackCount", "firstNetOrdinal", "lastNetOrdinal",
    "lastCallbackTotalExpanded", "selectedCallbackOrdinal",
    "transientTupleCopies", "transientFieldCopies", "validationCount",
    "observerUnits", "copiedEntries", "copyChunks", "selectedSnapshot",
    "selectedHead",
)
_ov2_SNAPSHOT_KEYS = (
    "pass", "netId", "startPadId", "rootPadId", "startX", "startY",
    "rootX", "rootY", "gridWidth", "gridHeight", "goalsTop",
    "goalsBottom", "prefixViaCount", "reachedPadCount",
    "initialManhattanDistance", "minimumExploredManhattanDistance",
    "branchExpanded", "totalExpanded", "expandedTop", "expandedBottom",
    "frontierCount", "bestStateCount", "terminalReason",
    "relaxedBoundStarted", "relaxedBoundComplete",
    "relaxedBoundObservationChecked", "relaxedBoundLiveLayoutStable",
    "relaxedBoundRouterStateStable", "relaxedBoundRouterEffectsStable",
    "relaxedBoundWorkUnits", "relaxedBoundOrdinaryProbes",
    "relaxedBoundViaProbes", "relaxedBoundRefreshes",
)
_ov2_HEAD_KEYS = (
    "branchOrdinal", "cell", "layer", "direction", "transitions", "cost",
    "storedHeuristic", "serial", "key", "storedPriority",
)


class ObservationV2Error(Exception):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


def _ov2_need(condition, code):
    if not condition:
        raise ObservationV2Error(code)


def _ov2_int(value, low=0, high=_ov2_MAX_INT):
    return type(value) is int and low <= value <= high


def _ov2_keys(value, expected, code):
    _ov2_need(type(value) is dict and tuple(value) == expected, code)


def _ov2_context(requested, traversed, pads_by_id, pads_by_net, grid):
    # Shape admission precedes every state return and all membership/index use.
    _ov2_need(type(requested) in (list, tuple) and type(traversed) in (list, tuple)
              and all(type(net) is str and bool(net) for net in requested)
              and all(type(net) is str and bool(net) for net in traversed),
              "OBSERVATION_CONTEXT_ORDERING")
    _ov2_need(type(pads_by_id) is dict and type(pads_by_net) is dict,
              "OBSERVATION_CONTEXT_PADS")
    _ov2_need(all(type(pad) is str and bool(pad) and type(row) is tuple
                  and len(row) == 3 and type(row[0]) is str and bool(row[0])
                  and _ov2_int(row[1], _ov2_MIN_INT) and _ov2_int(row[2], _ov2_MIN_INT)
                  for pad, row in pads_by_id.items()), "OBSERVATION_CONTEXT_PAD_ROWS")
    _ov2_need(all(type(net) is str and bool(net) and type(members) is set
                  and all(type(pad) is str and bool(pad) for pad in members)
                  for net, members in pads_by_net.items()), "OBSERVATION_CONTEXT_MEMBERSHIP")
    _ov2_need(type(grid) is tuple and len(grid) == 2
              and _ov2_int(grid[0], 1, 250000) and _ov2_int(grid[1], 1, 250000)
              and grid[0] * grid[1] <= 250000, "PRIOR_GRID_CONTEXT")


class ObservationV2Kernel:
    @staticmethod
    def validate_snapshot(s, requested, traversed, pads_by_id, pads_by_net, grid):
        _ov2_context(requested, traversed, pads_by_id, pads_by_net, grid)
        _ov2_keys(s, _ov2_SNAPSHOT_KEYS, "SNAPSHOT_SCHEMA")
        net = s["netId"]
        _ov2_need(type(requested) in (list, tuple)
                  and type(traversed) in (list, tuple)
                  and type(net) is str and net in requested, "SNAPSHOT_NET")
        ordinal = requested.index(net) + 1
        _ov2_need(ordinal <= len(traversed) and traversed[ordinal - 1] == net,
                  "SNAPSHOT_TRAVERSAL_BINDING")
        start, root = s["startPadId"], s["rootPadId"]
        _ov2_need(type(start) is str and bool(start)
                  and type(root) is str and bool(root), "SNAPSHOT_PAD_IDENTITY")
        _ov2_need(all(_ov2_int(s[name], _ov2_MIN_INT) for name in
                      ("startX", "startY", "rootX", "rootY")),
                  "SNAPSHOT_PAD_COORDINATES")
        _ov2_need(type(pads_by_id) is dict and type(pads_by_net) is dict
                  and start in pads_by_id and root in pads_by_id
                  and start in pads_by_net.get(net, ())
                  and root in pads_by_net.get(net, ()), "SNAPSHOT_PAD_IDENTITY")
        start_row, root_row = pads_by_id[start], pads_by_id[root]
        _ov2_need(type(start_row) is tuple and len(start_row) == 3
                  and type(root_row) is tuple and len(root_row) == 3,
                  "SNAPSHOT_PAD_IDENTITY")
        _ov2_need(start_row[0] == root_row[0] == net
                  and all(_ov2_int(coordinate, _ov2_MIN_INT)
                          for coordinate in (start_row[1], start_row[2], root_row[1], root_row[2]))
                  and s["startX"] == start_row[1] and s["startY"] == start_row[2]
                  and s["rootX"] == root_row[1] and s["rootY"] == root_row[2],
                  "SNAPSHOT_PAD_COORDINATES")
        _ov2_need(_ov2_int(s["pass"], 2, 2), "SNAPSHOT_PASS")
        _ov2_need(_ov2_int(s["gridWidth"], 1, 250000)
                  and _ov2_int(s["gridHeight"], 1, 250000)
                  and s["gridWidth"] * s["gridHeight"] <= 250000, "SNAPSHOT_GRID")
        _ov2_need(type(grid) is tuple and len(grid) == 2
                  and _ov2_int(grid[0], 1, 250000)
                  and _ov2_int(grid[1], 1, 250000)
                  and grid[0] * grid[1] <= 250000, "PRIOR_GRID_CONTEXT")
        _ov2_need((s["gridWidth"], s["gridHeight"]) == grid, "SNAPSHOT_GRID_BINDING")
        cells = s["gridWidth"] * s["gridHeight"]
        _ov2_need(_ov2_int(s["goalsTop"], 0, cells)
                  and _ov2_int(s["goalsBottom"], 0, cells)
                  and s["goalsTop"] + s["goalsBottom"] > 0, "SNAPSHOT_GOALS")
        _ov2_need(_ov2_int(s["prefixViaCount"], 0, 48)
                  and _ov2_int(s["reachedPadCount"], 1, len(pads_by_net.get(net, ())))
                  and _ov2_int(s["initialManhattanDistance"]), "SNAPSHOT_COUNTS")
        branch, total = s["branchExpanded"], s["totalExpanded"]
        _ov2_need(_ov2_int(branch, 0, 100000) and _ov2_int(total, branch, 1000000)
                  and _ov2_int(s["expandedTop"], 0, branch)
                  and _ov2_int(s["expandedBottom"], 0, branch)
                  and s["expandedTop"] + s["expandedBottom"] == branch,
                  "SNAPSHOT_EXPANSIONS")
        minimum = s["minimumExploredManhattanDistance"]
        _ov2_need(_ov2_int(minimum, -1, -1) if branch == 0 else
                  _ov2_int(minimum, 0, s["initialManhattanDistance"]),
                  "SNAPSHOT_MINIMUM_DISTANCE")
        _ov2_need(_ov2_int(s["frontierCount"]) and _ov2_int(s["bestStateCount"]),
                  "SNAPSHOT_STATE_COUNTS")
        reason = s["terminalReason"]
        _ov2_need(type(reason) is str and reason in
                  ("NO_PATH", "BRANCH_SEARCH_LIMIT", "TOTAL_SEARCH_LIMIT", "GRID_LIMIT", "FOUND"),
                  "SNAPSHOT_TERMINAL_REASON")
        if reason == "NO_PATH":
            _ov2_need(s["frontierCount"] == 0, "SNAPSHOT_NO_PATH_FRONTIER")
        elif reason == "BRANCH_SEARCH_LIMIT":
            _ov2_need(branch == 100000, "SNAPSHOT_BRANCH_LIMIT")
        elif reason == "TOTAL_SEARCH_LIMIT":
            _ov2_need(total == 1000000, "SNAPSHOT_TOTAL_LIMIT")
        elif reason == "FOUND":
            _ov2_need(branch > 0, "SNAPSHOT_FOUND_EXPANSION")
        flags, work = _ov2_SNAPSHOT_KEYS[23:29], _ov2_SNAPSHOT_KEYS[29:33]
        _ov2_need(all(type(s[name]) is bool for name in flags), "SNAPSHOT_RELAXED_FLAGS")
        _ov2_need(all(_ov2_int(s[name]) for name in work), "SNAPSHOT_RELAXED_WORK")
        started, complete, checked = (s[name] for name in flags[:3])
        expected_checked = complete and s["prefixViaCount"] == 48
        _ov2_need((not started or complete) and checked is expected_checked
                  and all(s[name] is checked for name in flags[3:]),
                  "SNAPSHOT_RELAXED_STATE")
        if not started:
            _ov2_need(not complete and not checked and all(s[name] == 0 for name in work),
                      "SNAPSHOT_RELAXED_ZERO_WORK")
        return ordinal

    @staticmethod
    def validate_head(h, s):
        _ov2_keys(h, _ov2_HEAD_KEYS, "HEAD_KEYS")
        _ov2_need(all(_ov2_int(h[name], _ov2_MIN_INT) for name in _ov2_HEAD_KEYS),
                  "HEAD_INTS")
        _ov2_keys(s, _ov2_SNAPSHOT_KEYS, "SNAPSHOT_SCHEMA")
        _ov2_need(_ov2_int(s["gridWidth"], 1, 250000)
                  and _ov2_int(s["gridHeight"], 1, 250000)
                  and s["gridWidth"] * s["gridHeight"] <= 250000
                  and _ov2_int(s["prefixViaCount"], 0, 48), "HEAD_SNAPSHOT")
        cells = s["gridWidth"] * s["gridHeight"]
        _ov2_need(h["branchOrdinal"] >= 1 and 0 <= h["cell"] < cells
                  and 0 <= h["layer"] <= 1 and 0 <= h["direction"] <= 4
                  and 0 <= h["transitions"] <= 48 - s["prefixViaCount"]
                  and h["cost"] >= 0 and h["storedHeuristic"] >= 0 and h["serial"] >= 0,
                  "HEAD_DOMAIN")
        expected_key = (((h["transitions"] * 2 + h["layer"]) * 5 + h["direction"])
                        * cells) + h["cell"]
        priority = ((h["cost"] + h["storedHeuristic"] + 2147483648) % 4294967296) - 2147483648
        _ov2_need(h["key"] == expected_key and h["storedPriority"] == priority,
                  "HEAD_EQUATIONS")
        _ov2_need(_ov2_int(s["branchExpanded"], 100000, 100000)
                  and _ov2_int(s["totalExpanded"], 100000, 999999)
                  and _ov2_int(s["bestStateCount"], 1), "HEAD_SNAPSHOT")

    @staticmethod
    def accounting(o):
        _ov2_keys(o, _ov2_OBSERVATION_KEYS, "OBSERVATION_SCHEMA")
        d = o["callbackCount"]
        _ov2_need(_ov2_int(d, 0, 247), "CALLBACK_BOUND")
        h = int(o["selectedHead"] is not None)
        _ov2_need(h <= d, "HEAD_CALLBACK_BOUND")
        v = o["validationCount"]
        _ov2_need(_ov2_int(v, d, 94 * d + 12 * h), "VALIDATION_BOUND")
        entries, chunks, units = 33 * d + 10 * h, d + h, 69 * d + v + 21 * h
        _ov2_need(_ov2_int(o["transientTupleCopies"], chunks, chunks)
                  and _ov2_int(o["copyChunks"], chunks, chunks)
                  and _ov2_int(o["transientFieldCopies"], entries, entries)
                  and _ov2_int(o["copiedEntries"], entries, entries)
                  and _ov2_int(o["observerUnits"], units, units)
                  and units <= 163 * d + 33 * h, "OBSERVER_ACCOUNTING")
        return entries, chunks, units

    @staticmethod
    def validate_observation(o, role, opportunities, requested, traversed,
                             pads_by_id, pads_by_net, grid, outcome, terminal):
        _ov2_context(requested, traversed, pads_by_id, pads_by_net, grid)
        _ov2_keys(o, _ov2_OBSERVATION_KEYS, "OBSERVATION_SCHEMA")
        _ov2_need(o["schema"] == "tsj-cohort-terminal-observation-v2", "OBSERVATION_IDENTITY")
        _ov2_need(_ov2_int(opportunities), "CALLBACK_OPPORTUNITIES")
        if role == "NULL":
            _ov2_need(o["mode"] == "NULL" and o["state"] == "NOT_ENABLED"
                      and all(_ov2_int(o[name], 0, 0) for name in (
                          "callbackCount", "foundCallbackCount", "nonFoundCallbackCount",
                          "transientTupleCopies", "transientFieldCopies", "validationCount",
                          "observerUnits", "copiedEntries", "copyChunks"))
                      and all(o[name] is None for name in (
                          "firstNetOrdinal", "lastNetOrdinal", "lastCallbackTotalExpanded",
                          "selectedCallbackOrdinal", "selectedSnapshot", "selectedHead")),
                      "NULL_OBSERVATION")
            return _ov2_types.MappingProxyType({
                "observerUnits": 0, "copiedEntries": 0, "copyChunks": 0, "state": "NOT_ENABLED"})
        _ov2_need(role == "OBSERVE" and o["mode"] == "OBSERVE"
                  and o["state"] in ("NO_FAILURE", "FIRST_NON_FOUND", "NON_BRANCH_REJECTION"),
                  "OBSERVATION_STATE")
        d, found, nonfound = (o[name] for name in
                              ("callbackCount", "foundCallbackCount", "nonFoundCallbackCount"))
        _ov2_need(_ov2_int(d, 0, min(247, opportunities))
                  and _ov2_int(found, 0, d) and _ov2_int(nonfound, 0, d)
                  and found + nonfound == d, "OBSERVATION_ACCOUNTING")
        entries, chunks, units = ObservationV2Kernel.accounting(o)
        _ov2_need(type(requested) in (list, tuple) and type(traversed) in (list, tuple),
                  "OBSERVATION_ORDINALS")
        _ov2_need(type(outcome) is dict
                  and type(outcome.get("disposition")) is str
                  and type(outcome.get("reason")) is str
                  and _ov2_int(outcome.get("expanded"), 0, 1000000), "OBSERVATION_OUTCOME")
        first, last, last_total = (o[name] for name in
                                   ("firstNetOrdinal", "lastNetOrdinal", "lastCallbackTotalExpanded"))
        if d == 0:
            _ov2_need(first is None and last is None and last_total is None,
                      "OBSERVATION_EMPTY_ORDINALS")
        else:
            _ov2_need(_ov2_int(first, 1, len(requested))
                      and _ov2_int(last, first, len(requested)) and last <= len(traversed)
                      and _ov2_int(last_total, 0, outcome["expanded"]), "OBSERVATION_ORDINALS")
        selected, selected_ordinal, head = (o[name] for name in
                                            ("selectedSnapshot", "selectedCallbackOrdinal", "selectedHead"))
        disposition, reason, state = outcome["disposition"], outcome["reason"], o["state"]
        if state == "NO_FAILURE":
            _ov2_need(disposition == "FOUND" and nonfound == 0 and found == d
                      and selected is None and selected_ordinal is None and terminal is None,
                      "OBSERVATION_SUCCESS_STATE")
        elif state == "FIRST_NON_FOUND":
            _ov2_need(disposition in ("NO_PATH", "CAP_REJECTED", "QUALITY_REJECTED")
                      and d >= 1 and nonfound == 1 and found + 1 == d
                      and _ov2_int(selected_ordinal, d, d)
                      and type(selected) is dict and type(terminal) is dict,
                      "OBSERVATION_FAILURE_STATE")
            net_ordinal = ObservationV2Kernel.validate_snapshot(
                selected, requested, traversed, pads_by_id, pads_by_net, grid)
            _ov2_need(net_ordinal == last and selected["totalExpanded"] == last_total
                      == outcome["expanded"]
                      and selected["netId"] == terminal.get("failedNetId")
                      and _ov2_int(terminal.get("searchPass"), 2, 2)
                      and selected["terminalReason"] != "FOUND"
                      and selected["terminalReason"] == reason.rsplit(":", 1)[-1]
                      and reason == selected["netId"] + ":" + selected["terminalReason"]
                      and terminal.get("ownerReason") == reason, "OBSERVATION_FAILURE_BINDING")
        else:
            _ov2_need(disposition == "QUALITY_REJECTED" and nonfound == 0
                      and found == d and selected is None and selected_ordinal is None
                      and (reason == "QUALITY_LIMIT" or reason.endswith(":BRANCH_QUALITY_LIMIT")),
                      "OBSERVATION_QUALITY_STATE")
            if reason == "QUALITY_LIMIT":
                _ov2_need(terminal is None, "OBSERVATION_QUALITY_TERMINAL")
            else:
                failed = reason.split(":", 1)[0]
                _ov2_need(d > 0 and type(terminal) is dict
                          and terminal.get("failedNetId") == failed
                          and terminal.get("ownerReason") == reason
                          and requested[last - 1] == failed and last == len(traversed)
                          and last_total == outcome["expanded"], "OBSERVATION_SCOPED_QUALITY_BINDING")
        has_branch_head = (state == "FIRST_NON_FOUND"
                           and selected["terminalReason"] == "BRANCH_SEARCH_LIMIT")
        _ov2_need((head is not None) is has_branch_head, "OBSERVATION_HEAD_PRESENCE")
        if has_branch_head:
            ObservationV2Kernel.validate_head(head, selected)
        return _ov2_types.MappingProxyType({
            "observerUnits": units, "copiedEntries": entries, "copyChunks": chunks, "state": state})


MAX_BYTES = 1048576
MAX_INT = 2147483647
MIN_INT = -2147483648
MAX_READER_NS = 180000000000
BASE_SHA = "d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0"
IO_SHA = "df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923"
OWNER_SHA = "7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c"
STUDY_SHA = "d6485b0b0f9c518431d440fcb57e477143d50cae78302c29598d12aaa4601dbd"
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")
SHA = re.compile(r"[0-9a-f]{64}\Z")
PACKET_KEYS = ("schema", "status", "runId", "sourceManifestSha256",
               "wireContractSha256", "complete", "blocked", "nonBranchQuality",
               "genericCallbackThrow", "directOwnerAbortReuse", "sourceReview")
CASE_KEYS = ("caseId", "provenance", "armRole", "observation", "context",
             "meterDelta", "metadata")
CONTEXT_KEYS = ("requestedNetIds", "traversedNetIds", "padsById", "gridWidth",
                "gridHeight", "outcome", "terminalFailure", "callbackOpportunities")
OBS_KEYS = ("schema", "mode", "state", "callbackCount", "foundCallbackCount",
            "nonFoundCallbackCount", "firstNetOrdinal", "lastNetOrdinal",
            "lastCallbackTotalExpanded", "selectedCallbackOrdinal",
            "transientTupleCopies", "transientFieldCopies", "validationCount",
            "observerUnits", "copiedEntries", "copyChunks", "selectedSnapshot",
            "selectedHead")
SNAPSHOT_KEYS = ("pass", "netId", "startPadId", "rootPadId", "startX", "startY",
                 "rootX", "rootY", "gridWidth", "gridHeight", "goalsTop",
                 "goalsBottom", "prefixViaCount", "reachedPadCount",
                 "initialManhattanDistance", "minimumExploredManhattanDistance",
                 "branchExpanded", "totalExpanded", "expandedTop", "expandedBottom",
                 "frontierCount", "bestStateCount", "terminalReason",
                 "relaxedBoundStarted", "relaxedBoundComplete",
                 "relaxedBoundObservationChecked", "relaxedBoundLiveLayoutStable",
                 "relaxedBoundRouterStateStable", "relaxedBoundRouterEffectsStable",
                 "relaxedBoundWorkUnits", "relaxedBoundOrdinaryProbes",
                 "relaxedBoundViaProbes", "relaxedBoundRefreshes")
HEAD_KEYS = ("branchOrdinal", "cell", "layer", "direction", "transitions", "cost",
             "storedHeuristic", "serial", "key", "storedPriority")
RELAXED_FLAGS = SNAPSHOT_KEYS[23:29]
RELAXED_WORK = SNAPSHOT_KEYS[29:33]
GENERIC_KEYS = ("caughtSameThrowable", "constructorEntriesBefore",
                "constructorEntriesAfter", "callbackExitMeterUnits", "afterInvokeMeterUnits")
DIRECT_KEYS = ("sentinelKind", "caughtSameThrowable", "lastWork",
               "diagnosticFailureSameThrowable", "searchCleared", "candidateCleared",
               "resultCleared", "retrySameThrowable", "sourcePlacementUnchanged",
               "harnessAliasesDropped")
REVIEW_KEYS = ("scope", "physicalOwnerSha256", "studyParentSha256", "headSemantics",
               "injectedSessionBegins", "otherSessionCounts")
CLI_FLAGS = ("--input", "--run-id", "--source-sha", "--wire-sha",
             "--base-generator", "--io-helper")


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
    pending = [(value, 0)]
    visits = 0
    while pending:
        item, depth = pending.pop()
        visits += 1
        need(visits <= MAX_BYTES and depth <= 64, "TREE_LIMIT")
        kind = type(item)
        if kind is str:
            need(len(item) <= 256 and all(32 <= ord(c) <= 126 for c in item), "TEXT")
        elif kind is dict:
            need(visits + len(pending) + 2 * len(item) <= MAX_BYTES, "TREE_LIMIT")
            for key, child in item.items():
                need(type(key) is str, "KEY_TYPE")
                pending.append((key, depth + 1))
                pending.append((child, depth + 1))
        elif kind is list:
            need(visits + len(pending) + len(item) <= MAX_BYTES, "TREE_LIMIT")
            pending.extend((child, depth + 1) for child in item)
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


def _context(value):
    keys(value, CONTEXT_KEYS, "CONTEXT_KEYS")
    requested = value["requestedNetIds"]
    traversed = value["traversedNetIds"]
    need(type(requested) is list and len(requested) == 1
         and type(requested[0]) is str and bool(requested[0]), "CONTEXT_NET")
    need(type(traversed) is list and (traversed == [] or traversed == requested),
         "CONTEXT_TRAVERSAL")
    net = requested[0]
    pads = value["padsById"]
    need(type(pads) is dict and len(pads) == 2, "CONTEXT_PADS")
    for pad_id, pad in pads.items():
        need(type(pad_id) is str and bool(pad_id), "CONTEXT_PAD_ID")
        keys(pad, ("netId", "x", "y"), "CONTEXT_PAD_KEYS")
        need(pad["netId"] == net and integer(pad["x"], MIN_INT)
             and integer(pad["y"], MIN_INT), "CONTEXT_PAD")
    need(integer(value["gridWidth"], 1, 250000)
         and integer(value["gridHeight"], 1, 250000)
         and value["gridWidth"] * value["gridHeight"] <= 250000, "CONTEXT_GRID")
    keys(value["outcome"], ("disposition", "reason", "expanded"), "OUTCOME_KEYS")
    need(integer(value["outcome"]["expanded"], 0, 1000000), "OUTCOME_EXPANDED")
    need(integer(value["callbackOpportunities"], 0, 1), "CALLBACK_OPPORTUNITIES")
    return net


def _snapshot(s, c):
    """Keep the fixed R profile; project authenticated rows for the kernel."""
    net = _context(c)
    keys(s, SNAPSHOT_KEYS, "SNAPSHOT_KEYS")
    need(s["netId"] == net and c["traversedNetIds"] == [net], "SNAPSHOT_NET")
    start, root = s["startPadId"], s["rootPadId"]
    need(type(start) is str and type(root) is str and start != root
         and start in c["padsById"] and root in c["padsById"], "SNAPSHOT_PADS")
    pads = {pad_id: (pad["netId"], pad["x"], pad["y"])
            for pad_id, pad in c["padsById"].items()}
    try:
        ObservationV2Kernel.validate_snapshot(
            s, c["requestedNetIds"], c["traversedNetIds"], pads,
            {net: set(pads)}, (c["gridWidth"], c["gridHeight"]))
    except ObservationV2Error as failure:
        raise ReaderError(failure.code) from None


def _head(h, s):
    try:
        return ObservationV2Kernel.validate_head(h, s)
    except ObservationV2Error as failure:
        raise ReaderError(failure.code) from None


def _observation_accounting(o):
    try:
        return ObservationV2Kernel.accounting(o)
    except ObservationV2Error as failure:
        raise ReaderError(failure.code) from None


def _case(value, name):
    keys(value, CASE_KEYS, "CASE_KEYS")
    blocked = name in ("BRANCH_SEARCH_LIMIT", "NO_PATH", "TOTAL_SEARCH_LIMIT")
    need(value["caseId"] == name and value["provenance"] ==
         ("INJECTED_ACTUAL_P06_SESSION" if blocked else "LITERAL_CALLBACK_FIXTURE")
         and value["armRole"] == ("NULL" if name == "NULL" else "OBSERVE"), "CASE_IDENTITY")
    c = value["context"]
    net = _context(c)
    o = value["observation"]
    keys(o, OBS_KEYS, "OBSERVATION_KEYS")
    need(o["schema"] == "tsj-cohort-terminal-observation-v2", "OBSERVATION_VERSION")
    need(o["mode"] == value["armRole"], "OBSERVATION_MODE")
    state = {"NULL": "NOT_ENABLED", "FOUND": "NO_FAILURE",
             "QUALITY_LIMIT": "NON_BRANCH_REJECTION"}.get(name, "FIRST_NON_FOUND")
    need(o["state"] == state, "OBSERVATION_STATE")
    d = 1 if blocked or name == "FOUND" else 0
    h = 1 if name == "BRANCH_SEARCH_LIMIT" else 0
    selected = o["selectedSnapshot"]
    head = o["selectedHead"]
    need(integer(o["callbackCount"], d, d)
         and integer(o["foundCallbackCount"], int(name == "FOUND"), int(name == "FOUND"))
         and integer(o["nonFoundCallbackCount"], int(blocked), int(blocked))
         and c["callbackOpportunities"] == d, "CALLBACK_COUNTS")
    if d:
        need(integer(o["firstNetOrdinal"], 1, 1) and integer(o["lastNetOrdinal"], 1, 1)
             and c["traversedNetIds"] == [net]
             and integer(o["lastCallbackTotalExpanded"], 0, 1000000)
             and o["lastCallbackTotalExpanded"] == c["outcome"]["expanded"], "CALLBACK_BINDING")
    else:
        need(all(o[name] is None for name in
                 ("firstNetOrdinal", "lastNetOrdinal", "lastCallbackTotalExpanded"))
             and c["traversedNetIds"] == [], "EMPTY_CALLBACK")
    if blocked:
        need(integer(o["selectedCallbackOrdinal"], 1, 1), "SELECTED_ORDINAL")
        _snapshot(selected, c)
        need(selected["terminalReason"] == name, "SELECTED_REASON")
        need(all(selected[field] is False for field in RELAXED_FLAGS)
             and all(selected[field] == 0 for field in RELAXED_WORK), "FIXTURE_RELAXED")
        terminal = c["terminalFailure"]
        keys(terminal, ("failedNetId", "ownerReason", "searchPass"), "TERMINAL_KEYS")
        reason = net + ":" + name
        need(terminal["failedNetId"] == net and terminal["ownerReason"] == reason
             and integer(terminal["searchPass"], 2, 2)
             and c["outcome"]["reason"] == reason
             and c["outcome"]["disposition"] == ("NO_PATH" if name == "NO_PATH" else "CAP_REJECTED")
             and selected["totalExpanded"] == c["outcome"]["expanded"], "TERMINAL_BINDING")
    else:
        need(selected is None and o["selectedCallbackOrdinal"] is None
             and c["terminalFailure"] is None, "NO_SELECTION")
        disposition, reason = {"NULL": ("NOT_RUN", "OBSERVER_DISABLED"),
                               "FOUND": ("FOUND", "FOUND"),
                               "QUALITY_LIMIT": ("QUALITY_REJECTED", "QUALITY_LIMIT")}[name]
        need(c["outcome"]["disposition"] == disposition and c["outcome"]["reason"] == reason,
             "OUTCOME_BINDING")
        need(c["outcome"]["expanded"] > 0 if name == "FOUND" else
             c["outcome"]["expanded"] == 0, "OUTCOME_TOTAL")
    if h:
        _head(head, selected)
    else:
        need(head is None, "UNEXPECTED_HEAD")
    entries, chunks, units = _observation_accounting(o)
    need(o["validationCount"] == {"FOUND": 91, "BRANCH_SEARCH_LIMIT": 102,
         "NO_PATH": 90, "TOTAL_SEARCH_LIMIT": 90, "NULL": 0, "QUALITY_LIMIT": 0}[name],
         "FIXTURE_VALIDATION")
    meter = value["meterDelta"]
    keys(meter, ("units", "copiedEntries", "copyChunks"), "METER_KEYS")
    need(integer(meter["units"], units, units)
         and integer(meter["copiedEntries"], entries, entries)
         and integer(meter["copyChunks"], chunks, chunks), "METER_BINDING")
    metadata = value["metadata"]
    keys(metadata, ("copiedEntries", "copyChunks"), "METADATA_KEYS")
    meta_entries = 18 if name == "NULL" else 36 + 33 * int(blocked) + 10 * h
    meta_chunks = 1 if name == "NULL" else 2 + int(blocked) + h
    need(integer(metadata["copiedEntries"], meta_entries, meta_entries)
         and integer(metadata["copyChunks"], meta_chunks, meta_chunks), "METADATA_ACCOUNTING")


def validate_packet(value, expected_run_id, expected_source_sha, expected_wire_sha):
    """Pure generic wire semantics; returns no execution-authentication claim."""
    need(type(expected_run_id) is str and UUID.fullmatch(expected_run_id) is not None
         and type(expected_source_sha) is str and SHA.fullmatch(expected_source_sha) is not None
         and type(expected_wire_sha) is str and SHA.fullmatch(expected_wire_sha) is not None,
         "EXPECTED_CONTEXT")
    _tree(value)
    keys(value, PACKET_KEYS, "PACKET_KEYS")
    need(value["schema"] == "tsj-selected-observation-wire-canary-v2"
         and value["status"] == "GENERIC_OBSERVATIONS_RECORDED_ONLY", "PACKET_VERSION")
    need(value["runId"] == expected_run_id
         and value["sourceManifestSha256"] == expected_source_sha
         and value["wireContractSha256"] == expected_wire_sha, "PACKET_CONTEXT")
    for group, names in (("complete", ("NULL", "FOUND")),
                         ("blocked", ("BRANCH_SEARCH_LIMIT", "NO_PATH", "TOTAL_SEARCH_LIMIT")),
                         ("nonBranchQuality", ("QUALITY_LIMIT",))):
        keys(value[group], names, "CASE_GROUP_KEYS")
        for name in names:
            _case(value[group][name], name)
    generic = value["genericCallbackThrow"]
    keys(generic, GENERIC_KEYS, "GENERIC_KEYS")
    need(generic["caughtSameThrowable"] is True
         and integer(generic["constructorEntriesBefore"])
         and integer(generic["constructorEntriesAfter"], 1)
         and generic["constructorEntriesAfter"] - generic["constructorEntriesBefore"] == 1
         and integer(generic["callbackExitMeterUnits"], 0, 32768 * 640)
         and integer(generic["afterInvokeMeterUnits"], 1, 32768 * 640)
         and generic["afterInvokeMeterUnits"] - generic["callbackExitMeterUnits"] == 1,
         "GENERIC_LEDGER")
    direct = value["directOwnerAbortReuse"]
    keys(direct, DIRECT_KEYS, "DIRECT_KEYS")
    need(type(direct["sentinelKind"]) is str and bool(direct["sentinelKind"])
         and direct["caughtSameThrowable"] is True and integer(direct["lastWork"], 0, 0)
         and all(direct[name] is True for name in DIRECT_KEYS[3:]), "DIRECT_BINDING")
    review = value["sourceReview"]
    keys(review, REVIEW_KEYS, "REVIEW_KEYS")
    need(review["scope"] == "GENERIC_OBSERVATION_V2_ONLY"
         and review["physicalOwnerSha256"] == OWNER_SHA
         and review["studyParentSha256"] == STUDY_SHA
         and review["headSemantics"] == "SELECTED_AND_POPPED_BEFORE_REFRESH_NOT_PATH_WITNESS"
         and integer(review["injectedSessionBegins"], 3, 3)
         and review["otherSessionCounts"] == "SEPARATE_EXISTING_GENERIC_HELPERS", "SOURCE_REVIEW")
    return {"schema": "tsj-selected-observation-reader-summary-v2",
            "status": "PASS_GENERIC_OBSERVATION_WIRE_ONLY", "runId": expected_run_id,
            "sourceManifestSha256": expected_source_sha, "wireContractSha256": expected_wire_sha,
            "cases": 6, "headCases": 1, "fullD": False}


def parse_args(argv):
    need(type(argv) in (list, tuple) and len(argv) == 12, "CLI_ARGUMENTS")
    options = {}
    for at in range(0, 12, 2):
        flag, value = argv[at:at + 2]
        need(type(flag) is str and flag in CLI_FLAGS and flag not in options
             and type(value) is str and bool(value) and "\x00" not in value
             and not value.startswith("--"), "CLI_ARGUMENTS")
        options[flag] = value
    need(set(options) == set(CLI_FLAGS), "CLI_ARGUMENTS")
    return options


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


def main(argv=None):
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("SW_PYTHON_ISOLATION_REQUIRED\n")
        return 2
    started = time.monotonic_ns()
    try:
        options = parse_args(sys.argv[1:] if argv is None else argv)
        raw = checked_read(options["--input"], options["--base-generator"], options["--io-helper"])
        summary = validate_packet(parse_packet(raw), options["--run-id"],
                                  options["--source-sha"], options["--wire-sha"])
        summary["inputSha256"] = hashlib.sha256(raw).hexdigest()
        summary["inputBytes"] = len(raw)
        line = canonical_line(summary)
        need(time.monotonic_ns() - started <= MAX_READER_NS, "READER_DEADLINE")
        output = sys.stdout.buffer
        need(output.write(line) == len(line), "STDOUT_WRITE")
        output.flush()
        return 0
    except ReaderError as failure:
        sys.stderr.write(failure.code + "\n")
        return 2
    except Exception:
        sys.stderr.write("SW_INTERNAL\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
