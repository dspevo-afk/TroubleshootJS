"""Independent consumer for the frozen R2 cohort-terminal telemetry contract.

This reader composes the immutable N reader's pure per-run kernel with the D
outer schema.  It never invokes N's whole-result semantic validator or prior
reader CLI.
"""
from __future__ import annotations

import hashlib
import json
import math
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



ROOT = Path(__file__).resolve().parent
N_ROOT = ROOT / "_unavailable_full_dependencies"
U_ROOT = N_ROOT.parent / "tsj-q100-shared-capture-budget-09b455afcdac46049a6d4abbb4a40fab"
N_RUN_SHA256 = "1274185600f9da79412f133ba569fb727eba673c7dd672e53abd8dcc5fd28a56"
N_READER_PATH = N_ROOT / "read_failure_cohort.py"
N_READER_SHA256 = "84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52"
F_OWNER_SHA256 = "c382f3bc08f8ad65b3501dad57c927dd5c1f9f1c04914ac79edb772f4b4f4e3e"
CANARY_FIXTURE_SOURCE_PATH = N_ROOT / "source" / "FailedFirstFixtures.java"
CANARY_FIXTURE_SOURCE_BYTES = 14_200
CANARY_FIXTURE_SOURCE_SHA256 = "dd333ff9c448ce012c3c1a4e603be39b70e1ac1d8d7ef79509d12ab21bd17619"
CANARY_CAPTURE_PARENT_PATH = U_ROOT / "source-r3" / "FailureCohortRoutingStudy.java"
CANARY_CAPTURE_PARENT_BYTES = 182_535
CANARY_CAPTURE_PARENT_SHA256 = "4ed1c8561adf1619a8b7ffbd21e23fdf2544dcaafe541af4e5d2b2b1e95cabe4"
CANARY_CAPTURE_PARENT_ORDINAL = 1088
R9_CANARY_TEXT_POLICY_PATH = ROOT / "r9_canary_text_policy_r1.py"
R9_CANARY_TEXT_POLICY_SHA256 = "dab163d6b0126aeb3b3cc38afeef35c310d746a7234868e345f476e6ee8fc36e"
R9_CANARY_TEXT_POLICY_MAX_BYTES = 64 * 1024
D_BUILDER_PATH = ROOT / "build_inputs_r2.py"
D_BUILDER_SHA256 = "271db07f1b1dbd0a30e8016024b4fe87f89b3050447dd4bab64ff8a135751321"
DESCRIPTOR_PATH = ROOT / "prior-input-r4" / "descriptor.json"
DESCRIPTOR_SCHEMA = "tsj-prior-input-descriptor-r2-v3"
WIRE_PATH = ROOT / "WIRE_R2.md"
WIRE_SOURCE_PATH = ROOT / "source" / "WIRE_R2.md"
WIRE_SHA256 = "31a81de89f91655fa64f348a0cdcc543ed13429eccf62a16286b6f4a5f55ca70"
DESCRIPTOR_DOC_PATH = ROOT / "DESCRIPTOR_R2.md"
DESCRIPTOR_DOC_SHA256 = "573b3e64083456597c7a3cba09a011843515785d2f1cc780eb574d75e2655ad4"
PLAN_PATH = ROOT / "PLAN_R2.md"
PLAN_SHA256 = "47da41ea19c9bf7d47c893724d8b0f28902bca77f7faabe5e9406edc1d5cb2d2"
EXPORT_FINALIZATION_CONTRACT_PATH = ROOT / "EXPORT_FINALIZATION_CONTRACT_R5.md"
EXPORT_FINALIZATION_CONTRACT_SHA256 = "2f13f4c9e2fe23ba7ee1962ca3e662580a5551b6dde6c5b0c5f80c65faf0b82c"
MARKER_SIZE_BOUND_PATH = ROOT / "MARKER_SIZE_BOUND_R1.json"
MARKER_SIZE_BOUND_SHA256 = "0b16e5404931fb7cf2f47b2062f9846dd5c6ac6e62ad05a63a8eb651c2a79bd1"
ENTRY_CLASS = "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy"
RESULT_SCHEMA = "tsj-cohort-terminal-result-v1"
MARKER_SCHEMA = "tsj-cohort-terminal-marker-v1"
CANARY_SCHEMA = "tsj-cohort-terminal-canary-v1"
CLEANUP_SCHEMA = "tsj-cohort-terminal-cleanup-v1"
PARENT_SCHEMA = "tsj-q100-reservation-provenance-parent-v1"
RESULT_MODE = "OBSERVED_COHORT_TERMINAL_TELEMETRY"
NORMAL_NATIVE_MODE = "COHORT_TERMINAL_TELEMETRY"
CANARY_NATIVE_MODE = "COHORT_TERMINAL_CANARIES"
CANARY_HOST_SUMMARY_SCHEMA = "tsj-cohort-terminal-canary-host-summary-v1"
CANARY_HOST_SUMMARY_KEYS = (
    "schema", "status", "qualityStatus", "runId", "sourceManifestSha256",
    "descriptorSha256", "wireContractSha256", "canarySha256", "canaryBytes",
    "fullD",
)
MAX_JSON_LINE = 1_048_576
MAX_MARKER_BYTES = 722
EXPORT_DIGEST_BYTES = 32
MIN_SIGNED_LONG = -(2**63)
MAX_SIGNED_LONG = 2**63 - 1
MAX_OPERATION1_BYTES = 8 * 1024 * 1024
MAX_HOST_RECEIPT_BYTES = 256 * 1024
MAX_HOST_OUTPUT_BYTES = 1_048_576
MAX_HOST_SOURCE_MANIFEST_BYTES = 16 * 1024 * 1024
MAX_READER_NS = 180_000_000_000
NATIVE_LIMIT_MS = 480_000
MAX_GROUPS = 640
MAX_GROUP_NS = 5_000_000_000
MAX_GROUP_UNITS = 32_768
MAX_TOTAL_NS = 90_000_000_000
MAX_CLEANUP_NS = 120_000_000_000
MAX_EXPANSIONS = 1_000_000
MAX_VIAS = 48
MAX_TRANSITIONS = 4
MAX_COPY_ENTRIES = 1_048_576
COPY_CHUNK = 4096
MAX_OBSERVER_CALLBACK_CEILING = 512
MAX_POSTCALL_UNITS = 104
MAX_READER_RESERVATION = 4096
MAX_INT = 2**31 - 1
UUID_RE = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")
SHA_RE = re.compile(r"[0-9a-f]{64}\Z")
CALL_FILES = (
    "owner.json", "parent.json", "process.json", "request.json",
    "result.json", "stderr.txt", "stdout.txt",
)
DATA_FILES = ("op-1.jsonl", "result.json")
CANARY_DATA_FILES = ("canary.json",)

RESULT_KEYS = (
    "schema", "mode", "status", "runId", "sourceManifestSha256",
    "wireContractSha256", "descriptorSha256", "qualifiedPrior",
    "originalCapture", "cohort", "arms", "work", "cleanup",
)
R7_MARKER_KEYS = (
    "schema", "status", "runId", "sourceManifestSha256",
    "wireContractSha256", "descriptorSha256", "resultSha256", "resultBytes",
    "elapsedNanos", "finalExportNanos", "totalGroups", "totalUnits",
    "maxGroupNanos", "maximumUnitsPerGroup",
)
PRIOR_KEYS = (
    "schema", "descriptorSha256", "sourceParent", "manifestSha256",
    "sourceManifestSha256", "readerSha256", "resultSha256",
    "operation1Sha256", "parentReceiptSha256", "priorRunId",
    "priorOriginalReceipt", "trialDisposition",
)
RECEIPT_KEYS = ("receiptPath", "receiptBytes", "receiptSha256",
                "receiptOrdinal", "lineageOrdinal")
CAPTURE_KEYS = (
    "schema", "captureId", "sourceManifestSha256", "operation", "receipt",
    "request", "seeds", "ownerSettings", "priorPromotedNetId", "netIds",
    "template", "padIdentity", "ownersReleased", "contextRestored",
    "cleanupNanos",
)
CAPTURE_REQUEST_KEYS = ("requestIndex", "selectedAttempt", "pass")
SEED_KEYS = ("rootSeed", "placementSeed", "routingSeed")
CAPTURE_OWNER_KEYS = (
    "policy", "maximumExpansions", "maximumVias", "viaCost",
    "policyTransitions", "policyVias", "branchOrderStrategy",
    "netOrderStrategy", "roleWidths", "orderingStream",
)
TEMPLATE_KEYS = ("geometryBeforeSha256", "geometryAfterSha256",
                 "effectsBeforeSha256", "effectsAfterSha256", "traceCount",
                 "holeCount")
PAD_IDENTITY_KEYS = ("schema", "padCount", "padLandSha256")
COHORT_KEYS = ("netIds", "requestedNetIds")
ARM_KEYS = ("armRole", "armId", "freshRun", "observation", "cleanup")
ARM_CLEANUP_KEYS = (
    "schema", "adapterSessionAliasDropped", "adapterSearchAliasDropped",
    "adapterCandidateAliasDropped", "adapterRouteResultAliasDropped",
    "adapterBoardTemplateAliasesDropped", "sourceGeometryBeforeSha256",
    "sourceGeometryAfterSha256", "contextRestored", "cleanupNanos",
)
WORK_KEYS = (
    "elapsedCutoffNanos", "cleanupNanos", "groupsBeforeExport",
    "wrapperGroupsBeforeExport", "maxGroupNanosBeforeExport",
    "maximumUnitsPerGroupBeforeExport", "unitsBeforeExport", "setupUnits",
    "setupGroups", "setupCopiedEntries", "setupCopyChunks",
    "originalCaptureOwnerUnits", "originalCaptureWrapperUnits",
    "originalCaptureCallbackUnits", "originalCaptureDiagnosticUnits",
    "originalCaptureGroups", "originalCaptureCopiedEntries",
    "originalCaptureCopyChunks", "nullOwnerUnits", "nullWrapperUnits",
    "nullObserverUnits", "observeOwnerUnits", "observeWrapperUnits",
    "observeObserverUnits", "observerUnits", "meterUnits", "metadataUnits",
    "metadataGroups", "metadataCopiedEntries", "metadataCopyChunks",
    "copiedEntries", "copyChunks", "copyChunkLimit", "copiedEntryLimit",
)
TOP_CLEANUP_KEYS = (
    "schema", "originalCaptureOwnersReleased", "originalCaptureContextRestored",
    "nullArmAliasesDropped", "observeArmAliasesDropped",
    "sourceGeometryUnchanged", "effectsScope",
    "originalCaptureCopyEffectsUnchanged", "contextRestored", "cleanupNanos",
)
CANARY_KEYS = (
    "schema", "status", "runId", "sourceManifestSha256",
    "wireContractSha256", "complete", "blocked", "nonBranchQuality",
    "genericCallbackThrow", "directOwnerAbortReuse", "sourceReview",
)
CANARY_FIXTURE_GRID = (59, 49)
CANARY_FIXTURE_COHORT = ("NET_A",)
_CANARY_OPEN_NETS = ("NET_B", "NET_A")
_CANARY_OPEN_REQUESTED = ("NET_A", "NET_B")
_CANARY_OPEN_PADS = (
    ("A1.1", "NET_A", "A1", 260, 300, "HIGH_CURRENT"),
    ("A2.1", "NET_A", "A2", 500, 300, "HIGH_CURRENT"),
    ("B1.1", "NET_B", "B1", 260, 450, "CONTROL"),
    ("B2.1", "NET_B", "B2", 500, 450, "CONTROL"),
)
_CANARY_BLOCKED_NETS = (
    "BLOCK_W_NET", "BLOCK_S_NET", "BLOCK_N_NET", "BLOCK_E_NET", "NET_B", "NET_A",
)
_CANARY_BLOCKED_REQUESTED = (
    "NET_A", "BLOCK_W_NET", "BLOCK_S_NET", "BLOCK_N_NET", "BLOCK_E_NET", "NET_B",
)
_CANARY_BLOCKED_PADS = _CANARY_OPEN_PADS + (
    ("BLOCK_E.1", "BLOCK_E_NET", "BLOCK_E", 520, 300, "SIGNAL"),
    ("BLOCK_W.1", "BLOCK_W_NET", "BLOCK_W", 480, 300, "SIGNAL"),
    ("BLOCK_N.1", "BLOCK_N_NET", "BLOCK_N", 500, 280, "SIGNAL"),
    ("BLOCK_S.1", "BLOCK_S_NET", "BLOCK_S", 500, 320, "SIGNAL"),
)
GENERIC_THROW_KEYS = (
    "caughtSameThrowable", "constructorEntriesBefore", "constructorEntriesAfter",
    "callbackExitMeterUnits", "afterInvokeMeterUnits",
)
DIRECT_ABORT_KEYS = (
    "sentinelKind", "caughtSameThrowable", "lastWork",
    "diagnosticFailureSameThrowable", "searchCleared", "candidateCleared",
    "resultCleared", "retrySameThrowable", "sourcePlacementUnchanged",
    "harnessAliasesDropped",
)


class ReaderError(ValueError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code if code.startswith("BF_") else "BF_" + code


def need(condition: bool, code: str) -> None:
    if not condition:
        raise ReaderError(code)


def exact_keys(value, keys, code: str) -> None:
    need(type(value) is dict and set(value) == set(keys), code)


def is_int(value, low: int = 0, high: int = 2**63 - 1) -> bool:
    return type(value) is int and low <= value <= high


def is_sha(value) -> bool:
    return type(value) is str and SHA_RE.fullmatch(value) is not None


def is_uuid(value) -> bool:
    return type(value) is str and UUID_RE.fullmatch(value) is not None


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _pairs(items):
    result = {}
    for key, value in items:
        if key in result:
            raise ValueError("duplicate JSON key")
        result[key] = value
    return result


def _reject_constant(_value):
    raise ValueError("nonfinite JSON value")


def _finite_tree(value) -> bool:
    if type(value) is float:
        return math.isfinite(value)
    if type(value) is dict:
        return all(type(key) is str and _finite_tree(item)
                   for key, item in value.items())
    if type(value) is list:
        return all(_finite_tree(item) for item in value)
    return value is None or type(value) in (str, int, bool)


def _strict_json_line_core(raw: bytes, code: str) -> dict:
    """Validate one compact JSON object line without applying a text domain."""
    need(type(raw) is bytes and 2 <= len(raw) <= MAX_JSON_LINE
         and raw.endswith(b"\n") and b"\r" not in raw
         and not raw.startswith(b"\xef\xbb\xbf"), code)
    body = raw[:-1]
    need(body and b"\n" not in body, code)
    try:
        text = body.decode("utf-8", errors="strict")
        value = json.loads(text, object_pairs_hook=_pairs,
                           parse_constant=_reject_constant)
    except (UnicodeError, json.JSONDecodeError, ValueError, TypeError):
        raise ReaderError(code) from None
    need(type(value) is dict and _finite_tree(value), code)
    quoted = escaped = False
    for char in text:
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        else:
            need(not char.isspace(), code)
    need(not quoted and text.startswith("{") and text.endswith("}"), code)
    return value


def strict_json_line(raw: bytes, code: str = "JSON_FRAMING") -> dict:
    """Parse one compact object line and apply the ordinary 256-char domain."""
    value = _strict_json_line_core(raw, code)
    _ascii_tree(value, code)
    return value


def parse_canary_json_line(raw: bytes,
                           code: str = "CANARY_JSON_FRAMING") -> dict:
    """Parse a compact canary object line; framing alone is not acceptance.

    The returned tree must still pass validate_canary, which applies the
    hash-pinned R9 canary text policy and all canary semantic/source checks.
    """
    return _strict_json_line_core(raw, code)


def _ascii_tree(value, code: str) -> None:
    if type(value) is str:
        try:
            raw = value.encode("ascii", errors="strict")
        except UnicodeEncodeError:
            raise ReaderError(code) from None
        need(len(raw) <= 256 and all(0x20 <= byte <= 0x7e for byte in raw), code)
    elif type(value) is dict:
        for key, child in value.items():
            _ascii_tree(key, code)
            _ascii_tree(child, code)
    elif type(value) is list:
        for child in value:
            _ascii_tree(child, code)


def _read_regular(path: Path, limit: int, code: str) -> bytes:
    try:
        info = path.lstat()
        okay = (path.is_file() and not path.is_symlink()
                and not (getattr(info, "st_file_attributes", 0) & 0x400))
        need(okay, code)
        with path.open("rb") as stream:
            raw = stream.read(limit + 1)
    except ReaderError:
        raise
    except OSError:
        raise ReaderError(code) from None
    need(0 < len(raw) <= limit, code)
    return raw


def _require_n_reader(context):
    module = context.get("baseReader") if type(context) is dict else None
    need(type(module) is types.ModuleType
         and callable(getattr(module, "_validate_run", None))
         and callable(getattr(module, "stable_cohort_order", None)),
         "N_READER_CONTEXT")
    try:
        module_path = Path(module.__file__).resolve()
        expected_path = N_READER_PATH.resolve()
    except (AttributeError, OSError):
        raise ReaderError("N_READER_PATH") from None
    need(module_path == expected_path, "N_READER_PATH")
    raw = _read_regular(expected_path, 2 * 1024 * 1024, "N_READER_READ")
    need(sha(raw) == N_READER_SHA256, "N_READER_HASH")
    return module


def _prior_semantics(context):
    descriptor = context.get("descriptor")
    prior_result = context.get("priorResult")
    need(type(descriptor) is dict and type(prior_result) is dict,
         "PRIOR_CONTEXT")
    capture = descriptor.get("capture")
    settings = descriptor.get("ownerSettings")
    need(type(capture) is dict and type(settings) is dict, "DESCRIPTOR_CAPTURE")
    source = prior_result.get("source")
    prior_capture = prior_result.get("capturedOrder")
    prior_owner = prior_capture.get("owner") if type(prior_capture) is dict else None
    need(type(source) is dict and type(source.get("padLands")) is list
         and type(prior_capture) is dict and type(prior_owner) is dict,
         "PRIOR_CAPTURE_CONTEXT")
    expected_capture = {
        "pass": capture.get("passIndex"),
        "failedNetId": capture.get("failedNetId"),
        "priorPromotedNetId": capture.get("priorPromotedNetId"),
        "netIds": capture.get("capturedNetIds"),
        "liveSearchIndex": capture.get("liveSearchIndex"),
        "owner": {
            "policy": settings.get("boardMode"),
            "maximumExpansions": settings.get("maxExpansions"),
            "expandedAtFailure": settings.get("expandedAtFailure"),
            "maximumVias": settings.get("maxVias"),
            "viaCost": settings.get("viaCost"),
            "policyTransitions": settings.get("maxTransitions"),
            "policyVias": settings.get("maxVias"),
            "branchOrderStrategy": settings.get("branchStrategy"),
            "netOrderStrategy": settings.get("netStrategy"),
            "roleWidths": settings.get("roleWidthsEnabled"),
            "orderingStream": settings.get("orderingStreamEnabled"),
        },
    }
    captured_ids = expected_capture["netIds"]
    need(expected_capture["pass"] == 2 and type(captured_ids) is list
         and len(captured_ids) == 60 and all(type(net) is str and bool(net)
                                             for net in captured_ids)
         and len(set(captured_ids)) == 60
         and expected_capture["failedNetId"] in captured_ids
         and expected_capture["priorPromotedNetId"] in captured_ids
         and is_int(expected_capture["liveSearchIndex"], 1, 60)
         and captured_ids[expected_capture["liveSearchIndex"] - 1]
         == expected_capture["failedNetId"], "PRIOR_VECTOR_BINDING")
    need(prior_capture.get("pass") == expected_capture["pass"]
         and prior_capture.get("failedNetId") == expected_capture["failedNetId"]
         and prior_capture.get("priorPromotedNetId") == expected_capture["priorPromotedNetId"]
         and prior_capture.get("netIds") == expected_capture["netIds"]
         and prior_capture.get("liveSearchIndex") == expected_capture["liveSearchIndex"]
         and prior_owner == expected_capture["owner"],
         "PRIOR_DESCRIPTOR_BINDING")
    need(prior_result.get("trialDisposition") == capture.get("trialDisposition")
         and type(prior_result.get("control")) is dict
         and type(prior_result["control"].get("outcome")) is dict
         and prior_result["control"]["outcome"].get("disposition")
         == capture.get("controlDisposition")
         and type(prior_result.get("trial")) is dict
         and type(prior_result["trial"].get("outcome")) is dict
         and prior_result["trial"]["outcome"].get("disposition")
         == capture.get("trialOutcome"),
         "PRIOR_RAW_PROJECTION")
    qualification = descriptor.get("qualification")
    summary = qualification.get("readerSummary") if type(qualification) is dict else None
    need(type(summary) is dict
         and summary.get("controlDisposition") == capture.get("controlDisposition")
         and summary.get("trialOutcome") == capture.get("trialOutcome")
         and summary.get("trialLabel") == capture["trialDisposition"].get("reason")
         and summary.get("netCount") == len(capture.get("capturedNetIds", [])),
         "PRIOR_SUMMARY_BINDING")
    cohort_ids = capture.get("cohortNetIds")
    trial_disposition = capture.get("trialDisposition")
    need(type(cohort_ids) is list and len(cohort_ids) == 2
         and all(type(net) is str and bool(net) for net in cohort_ids)
         and len(set(cohort_ids)) == 2 and type(trial_disposition) is dict
         and trial_disposition.get("status") == "RUN"
         and trial_disposition.get("reason") == "TRIAL_EXECUTED"
         and trial_disposition.get("feedbackValidated") is True
         and trial_disposition.get("cohortNetIds") == cohort_ids
         and trial_disposition.get("capturedFailedNetId") == cohort_ids[0]
         and trial_disposition.get("controlFailedNetId") == cohort_ids[1],
         "PRIOR_COHORT_BINDING")
    need(expected_capture["priorPromotedNetId"] == "D_SENSE",
         "PRIOR_PROMOTION_BINDING")
    captured = {"owner": expected_capture["owner"],
                "netIds": expected_capture["netIds"],
                "failedNetId": expected_capture["failedNetId"],
                "priorPromotedNetId": expected_capture["priorPromotedNetId"]}
    return source["padLands"], captured


def _prior_grid_dimensions(context):
    """Read the independently authenticated fresh-owner grid from qualified op-1."""
    parsed = context.get("priorParsedOperation1") if type(context) is dict else None
    selected = parsed.get("selectedBranch") if type(parsed) is dict else None
    selected_snapshot = (selected.get("snapshot")
                         if type(selected) is dict else None)
    need(type(parsed) is dict and type(selected_snapshot) is dict,
         "PRIOR_GRID_CONTEXT")
    width = selected_snapshot.get("gridWidth")
    height = selected_snapshot.get("gridHeight")
    need(is_int(width, 1, 250_000) and is_int(height, 1, 250_000)
         and width * height <= 250_000, "PRIOR_GRID_BINDING")
    parsed_snapshot = parsed.get("snapshot")
    branches = parsed.get("branches")
    need(type(parsed_snapshot) is dict
         and parsed_snapshot.get("gridWidth") == width
         and parsed_snapshot.get("gridHeight") == height
         and type(branches) is list and bool(branches)
         and selected in branches, "PRIOR_GRID_BINDING")
    for branch in branches:
        branch_snapshot = branch.get("snapshot") if type(branch) is dict else None
        need(type(branch_snapshot) is dict
             and branch_snapshot.get("gridWidth") == width
             and branch_snapshot.get("gridHeight") == height,
             "PRIOR_GRID_BINDING")
    return width, height


def _pad_lookup(pads):
    need(type(pads) is list and len(pads) == 246, "PAD_CENSUS")
    by_id = {}
    by_net = {}
    for row in pads:
        need(type(row) is dict, "PAD_ROW")
        pad_id, net_id = row.get("padId"), row.get("netId")
        x, y = row.get("x"), row.get("y")
        need(type(pad_id) is str and bool(pad_id) and type(net_id) is str
             and bool(net_id) and is_int(x, -(2**31), 2**31 - 1)
             and is_int(y, -(2**31), 2**31 - 1) and pad_id not in by_id,
             "PAD_ROW_BINDING")
        point = (net_id, x, y)
        by_id[pad_id] = point
        by_net.setdefault(net_id, set()).add(pad_id)
    return by_id, by_net


def _expected_prior_ref(context):
    descriptor = context["descriptor"]
    descriptor_sha = context["descriptorSha256"]
    prior_closure = descriptor["priorSourceClosure"]
    qualification = descriptor["qualification"]
    summary = qualification["readerSummary"]
    prior_op = descriptor["priorOperationReference"]
    approved = descriptor["approvedPriorArtifactRows"]
    op1_ref = next(row for row in approved if row["role"] == "data-op-1")
    parent_rows = [row for row in approved if row["role"] == "call-parent"]
    need(len(parent_rows) == 1, "PRIOR_PARENT_REF")
    parent_ref = parent_rows[0]
    prior_result = context["priorResult"]
    raw_trial = descriptor["capture"]["trialDisposition"]
    return {
        "schema": "tsj-cohort-qualified-prior-ref-v1",
        "descriptorSha256": descriptor_sha,
        "sourceParent": prior_closure["sourceParent"],
        "manifestSha256": prior_closure["runManifestRef"]["sha256"],
        "sourceManifestSha256": prior_closure["sourceManifestRef"]["sha256"],
        "readerSha256": summary["readerSha256"],
        "resultSha256": summary["resultSha256"],
        "operation1Sha256": summary["operation1Sha256"],
        "parentReceiptSha256": parent_ref["sha256"],
        "priorRunId": prior_result["runId"],
        "priorOriginalReceipt": {
            "receiptPath": "op-1.jsonl",
            "receiptBytes": op1_ref["bytes"],
            "receiptSha256": op1_ref["sha256"],
            "receiptOrdinal": prior_op["receiptOrdinal"],
            "lineageOrdinal": prior_op["lineageOrdinal"],
        },
        "trialDisposition": raw_trial,
    }


def _validate_original_capture(value, context, captured, op1_raw, run_id):
    exact_keys(value, CAPTURE_KEYS, "ORIGINAL_CAPTURE_SCHEMA")
    need(value["schema"] == "tsj-cohort-terminal-original-capture-v1"
         and is_uuid(value["captureId"])
         and value["captureId"] != run_id
         and value["sourceManifestSha256"] == context["sourceManifestSha256"]
         and value["operation"] == "AUTHORED", "ORIGINAL_CAPTURE_IDENTITY")
    receipt = value["receipt"]
    exact_keys(receipt, RECEIPT_KEYS, "ORIGINAL_RECEIPT_SCHEMA")
    need(receipt["receiptPath"] == "op-1.jsonl"
         and is_int(receipt["receiptBytes"], len(op1_raw), len(op1_raw))
         and receipt["receiptSha256"] == sha(op1_raw)
         and is_int(receipt["receiptOrdinal"], 1, 2**31 - 1)
         and is_int(receipt["lineageOrdinal"], 1, 2**31 - 1)
         and receipt["receiptOrdinal"] == 307
         and receipt["lineageOrdinal"] == 77,
         "ORIGINAL_RECEIPT_BINDING")
    request = value["request"]
    exact_keys(request, CAPTURE_REQUEST_KEYS, "ORIGINAL_REQUEST_SCHEMA")
    need(request == {"requestIndex": 0, "selectedAttempt": 4, "pass": 2},
         "ORIGINAL_REQUEST_BINDING")
    descriptor = context["descriptor"]
    need(value["seeds"] == descriptor["seedValues"], "ORIGINAL_SEED_BINDING")
    expected_owner = {
        "policy": descriptor["ownerSettings"]["boardMode"],
        "maximumExpansions": descriptor["ownerSettings"]["maxExpansions"],
        "maximumVias": descriptor["ownerSettings"]["maxVias"],
        "viaCost": descriptor["ownerSettings"]["viaCost"],
        "policyTransitions": descriptor["ownerSettings"]["maxTransitions"],
        "policyVias": descriptor["ownerSettings"]["maxVias"],
        "branchOrderStrategy": descriptor["ownerSettings"]["branchStrategy"],
        "netOrderStrategy": descriptor["ownerSettings"]["netStrategy"],
        "roleWidths": descriptor["ownerSettings"]["roleWidthsEnabled"],
        "orderingStream": descriptor["ownerSettings"]["orderingStreamEnabled"],
    }
    exact_keys(value["ownerSettings"], CAPTURE_OWNER_KEYS, "ORIGINAL_OWNER_SCHEMA")
    need(value["ownerSettings"] == expected_owner
         and value["priorPromotedNetId"] == captured["priorPromotedNetId"]
         and type(value["netIds"]) is list and len(value["netIds"]) == 60
         and len(set(value["netIds"])) == 60
         and value["netIds"] == captured["netIds"], "ORIGINAL_OWNER_BINDING")
    source_identity = descriptor["sourceIdentity"]
    template = value["template"]
    exact_keys(template, TEMPLATE_KEYS, "ORIGINAL_TEMPLATE_SCHEMA")
    expected_template = {
        "geometryBeforeSha256": source_identity["geometryBeforeSha256"],
        "geometryAfterSha256": source_identity["geometryAfterSha256"],
        "effectsBeforeSha256": source_identity["effectsBeforeSha256"],
        "effectsAfterSha256": source_identity["effectsAfterSha256"],
        "traceCount": source_identity["traceCount"],
        "holeCount": source_identity["holeCount"],
    }
    need(template == expected_template and template["traceCount"] == 0
         and template["holeCount"] == 0
         and template["effectsBeforeSha256"] == template["effectsAfterSha256"],
         "ORIGINAL_TEMPLATE_BINDING")
    pad_identity = value["padIdentity"]
    exact_keys(pad_identity, PAD_IDENTITY_KEYS, "ORIGINAL_PAD_SCHEMA")
    prior_pad = source_identity["padLandIdentity"]
    need(pad_identity == {
        "schema": prior_pad["format"], "padCount": prior_pad["rowCount"],
        "padLandSha256": prior_pad["sha256"],
    } and pad_identity["schema"] == "TSJ_PAD_LAND_ASCII_V1"
         and pad_identity["padCount"] == 246,
         "ORIGINAL_PAD_BINDING")
    need(all(is_sha(template[name]) for name in (
        "geometryBeforeSha256", "geometryAfterSha256",
        "effectsBeforeSha256", "effectsAfterSha256")),
        "ORIGINAL_TEMPLATE_DIGEST")
    need(value["ownersReleased"] is True and value["contextRestored"] is True
         and is_int(value["cleanupNanos"], 0, MAX_CLEANUP_NS),
         "ORIGINAL_CAPTURE_CLEANUP")
    return receipt


def _validate_arm_cleanup(value, original_template=None):
    exact_keys(value, ARM_CLEANUP_KEYS, "ARM_CLEANUP_SCHEMA")
    need(value["schema"] == "tsj-cohort-terminal-arm-cleanup-v1"
         and all(value[name] is True for name in (
             "adapterSessionAliasDropped", "adapterSearchAliasDropped",
             "adapterCandidateAliasDropped", "adapterRouteResultAliasDropped",
             "adapterBoardTemplateAliasesDropped", "contextRestored"))
         and all(is_sha(value[name]) for name in (
             "sourceGeometryBeforeSha256", "sourceGeometryAfterSha256"))
         and value["sourceGeometryBeforeSha256"] == value["sourceGeometryAfterSha256"]
         and (original_template is None
              or value["sourceGeometryBeforeSha256"]
              == original_template["geometryBeforeSha256"]
              == original_template["geometryAfterSha256"])
         and is_int(value["cleanupNanos"], 0, MAX_CLEANUP_NS),
         "ARM_CLEANUP_BINDING")
    return value


def _validate_snapshot(snapshot, requested, traversed, pads_by_id, pads_by_net,
                       expected_grid_dimensions):
    try:
        return ObservationV2Kernel.validate_snapshot(
            snapshot, requested, traversed, pads_by_id, pads_by_net,
            expected_grid_dimensions)
    except ObservationV2Error as failure:
        raise ReaderError(failure.code) from None


def _validate_observation(value, arm_role, fresh_run, facts, captured,
                          pads_by_id, pads_by_net, expected_grid_dimensions):
    """Observation component only; captured is retained for caller compatibility."""
    try:
        return ObservationV2Kernel.validate_observation(
            value, arm_role, fresh_run["work"]["advanceCalls"],
            fresh_run["ordering"]["requestedNetIds"],
            fresh_run["ordering"]["traversedNetIds"], pads_by_id, pads_by_net,
            expected_grid_dimensions, facts["outcome"], facts["terminalFailure"])
    except ObservationV2Error as failure:
        raise ReaderError(failure.code) from None


def _validate_arm(value, arm_role, cohort_ids, captured, nreader,
                  pads_by_id, pads_by_net, original_template, run_id,
                  expected_grid_dimensions, *, canary=False):
    exact_keys(value, ARM_KEYS, "ARM_SCHEMA")
    need(value["armRole"] == arm_role and is_uuid(value["armId"])
         and value["armId"] != run_id,
         "ARM_IDENTITY")
    fresh_run = value["freshRun"]
    try:
        facts = nreader._validate_run(fresh_run, "OBSERVED_FAILURE_COHORT_TRIAL",
                                      captured, cohort_ids)
    except Exception as failure:
        if getattr(failure, "code", None):
            raise
        raise ReaderError("FRESH_RUN_VALIDATION") from None
    observation_facts = _validate_observation(value["observation"], arm_role,
                                               fresh_run, facts, captured,
                                               pads_by_id, pads_by_net,
                                               expected_grid_dimensions)
    cleanup = _validate_arm_cleanup(value["cleanup"], original_template)
    if not canary:
        need(fresh_run["kind"] == "OBSERVED_FAILURE_COHORT_TRIAL",
             "ARM_RUN_KIND")
    return {"freshRun": fresh_run, "observation": value["observation"],
            "facts": facts,
            "observationFacts": observation_facts, "cleanup": cleanup}


def _validate_work(value, original_capture, arms, observations):
    exact_keys(value, WORK_KEYS, "WORK_SCHEMA")
    copy_names = {"setupCopiedEntries", "originalCaptureCopiedEntries",
                  "metadataCopiedEntries", "copiedEntries"}
    chunk_names = {"setupCopyChunks", "originalCaptureCopyChunks",
                   "metadataCopyChunks", "copyChunks", "copyChunkLimit"}
    group_names = {"groupsBeforeExport", "wrapperGroupsBeforeExport", "setupGroups",
                   "originalCaptureGroups", "metadataGroups"}
    for name in WORK_KEYS:
        if name == "elapsedCutoffNanos":
            okay = is_int(value[name], 1, MAX_TOTAL_NS)
        elif name == "cleanupNanos":
            okay = is_int(value[name], 0, MAX_CLEANUP_NS)
        elif name in copy_names or name in chunk_names:
            okay = is_int(value[name], 0, MAX_COPY_ENTRIES)
        elif name in group_names:
            okay = is_int(value[name], 0, MAX_GROUPS)
        elif name == "maxGroupNanosBeforeExport":
            okay = is_int(value[name], 1, MAX_GROUP_NS)
        elif name == "maximumUnitsPerGroupBeforeExport":
            okay = is_int(value[name], 1, MAX_GROUP_UNITS)
        else:
            okay = is_int(value[name], 0, MAX_GROUP_UNITS * MAX_GROUPS)
        need(okay, "WORK_INTEGER")
    need(0 < value["elapsedCutoffNanos"] <= MAX_TOTAL_NS
         and value["setupUnits"] > 0 and value["setupGroups"] > 0
         and value["metadataUnits"] > 0 and value["metadataGroups"] > 0
         and value["cleanupNanos"] <= MAX_CLEANUP_NS
         and value["groupsBeforeExport"] + 1 <= MAX_GROUPS
         and 0 < value["wrapperGroupsBeforeExport"] <= value["groupsBeforeExport"]
         and value["maxGroupNanosBeforeExport"] <= MAX_GROUP_NS
         and value["maximumUnitsPerGroupBeforeExport"] <= MAX_GROUP_UNITS
         and value["unitsBeforeExport"] <= value["groupsBeforeExport"] * MAX_GROUP_UNITS
         and value["copyChunkLimit"] == COPY_CHUNK
         and value["copiedEntryLimit"] == MAX_COPY_ENTRIES,
         "WORK_LIMITS")
    null_run, observe_run = arms["null"]["freshRun"], arms["observe"]["freshRun"]
    null_obs, observe_obs = observations["null"], observations["observe"]
    need(value["nullOwnerUnits"] == null_run["work"]["ownerUnits"]
         and value["nullWrapperUnits"] == null_run["work"]["wrapperUnits"]
         and value["nullObserverUnits"] == null_obs["observerUnits"] == 0
         and value["observeOwnerUnits"] == observe_run["work"]["ownerUnits"]
         and value["observeWrapperUnits"] == observe_run["work"]["wrapperUnits"]
         and value["observeObserverUnits"] == observe_obs["observerUnits"]
         and value["observerUnits"] == value["nullObserverUnits"] + value["observeObserverUnits"],
         "WORK_ARM_BINDING")
    need(value["originalCaptureGroups"] >= 1
         and value["originalCaptureCopiedEntries"] >= 0
         and value["originalCaptureCopyChunks"] <= value["originalCaptureCopiedEntries"],
         "WORK_CAPTURE_GROUPS")
    expected_units = sum(value[name] for name in (
        "setupUnits", "originalCaptureOwnerUnits", "originalCaptureWrapperUnits",
        "originalCaptureCallbackUnits", "originalCaptureDiagnosticUnits",
        "nullOwnerUnits", "nullWrapperUnits", "nullObserverUnits",
        "observeOwnerUnits", "observeWrapperUnits", "observeObserverUnits",
        "metadataUnits"))
    need(value["meterUnits"] == expected_units == value["unitsBeforeExport"],
         "WORK_UNIT_EQUATION")
    expected_groups = (value["setupGroups"] + value["originalCaptureGroups"]
                       + null_run["work"]["groups"] + observe_run["work"]["groups"]
                       + value["metadataGroups"])
    need(value["groupsBeforeExport"] == expected_groups,
         "WORK_GROUP_EQUATION")
    expected_entries = (value["setupCopiedEntries"]
                        + value["originalCaptureCopiedEntries"]
                        + null_run["work"]["copyEntries"]
                        + observe_run["work"]["copyEntries"]
                        + observe_obs["copiedEntries"]
                        + value["metadataCopiedEntries"])
    expected_chunks = (value["setupCopyChunks"]
                       + value["originalCaptureCopyChunks"]
                       + null_run["work"]["copyChunks"]
                       + observe_run["work"]["copyChunks"]
                       + observe_obs["copyChunks"]
                       + value["metadataCopyChunks"])
    need(value["copiedEntries"] == expected_entries
         and value["copyChunks"] == expected_chunks
         and value["copiedEntries"] <= MAX_COPY_ENTRIES
         and value["copyChunks"] <= value["copiedEntries"]
         and (value["copiedEntries"] == 0 or value["copyChunks"] >=
              (value["copiedEntries"] + COPY_CHUNK - 1) // COPY_CHUNK),
         "WORK_COPY_EQUATION")
    # Setup remains its own cumulative phase in the exact aggregate equations;
    # do not reinterpret the original-capture delta as a cumulative counter.
    need(value["maxGroupNanosBeforeExport"] >= max(
        null_run["work"]["maxGroupNanos"], observe_run["work"]["maxGroupNanos"])
         and value["maximumUnitsPerGroupBeforeExport"] >= max(
             null_run["work"]["maximumUnitsPerGroup"],
             observe_run["work"]["maximumUnitsPerGroup"]), "WORK_MAXIMA")
    return value


def _validate_top_cleanup(value, capture, arms, work):
    exact_keys(value, TOP_CLEANUP_KEYS, "TOP_CLEANUP_SCHEMA")
    null_cleanup, observe_cleanup = arms["null"]["cleanup"], arms["observe"]["cleanup"]
    geometry = (capture["template"]["geometryBeforeSha256"]
                == capture["template"]["geometryAfterSha256"]
                == null_cleanup["sourceGeometryBeforeSha256"]
                == null_cleanup["sourceGeometryAfterSha256"]
                == observe_cleanup["sourceGeometryBeforeSha256"]
                == observe_cleanup["sourceGeometryAfterSha256"])
    copy_effects_unchanged = (
        capture["template"]["effectsBeforeSha256"]
        == capture["template"]["effectsAfterSha256"])
    aliases = lambda arm: all(arm["cleanup"][name] is True for name in (
        "adapterSessionAliasDropped", "adapterSearchAliasDropped",
        "adapterCandidateAliasDropped", "adapterRouteResultAliasDropped",
        "adapterBoardTemplateAliasesDropped"))
    expected = {
        "schema": CLEANUP_SCHEMA,
        "originalCaptureOwnersReleased": capture["ownersReleased"],
        "originalCaptureContextRestored": capture["contextRestored"],
        "nullArmAliasesDropped": aliases(arms["null"]),
        "observeArmAliasesDropped": aliases(arms["observe"]),
        "sourceGeometryUnchanged": geometry,
        "effectsScope": "ORIGINAL_CAPTURE_COPY_ONLY",
        "originalCaptureCopyEffectsUnchanged": copy_effects_unchanged,
        "contextRestored": (capture["contextRestored"]
                             and null_cleanup["contextRestored"]
                             and observe_cleanup["contextRestored"]),
        "cleanupNanos": work["cleanupNanos"],
    }
    need(geometry is True and copy_effects_unchanged is True,
         "TOP_CLEANUP_UNCHANGED")
    need(value == expected and all(type(value[name]) is bool for name in (
        "originalCaptureOwnersReleased", "originalCaptureContextRestored",
        "nullArmAliasesDropped", "observeArmAliasesDropped",
        "sourceGeometryUnchanged", "originalCaptureCopyEffectsUnchanged",
        "contextRestored")),
        "TOP_CLEANUP_BINDING")
    minimum_cleanup = (capture["cleanupNanos"] + null_cleanup["cleanupNanos"]
                       + observe_cleanup["cleanupNanos"])
    need(value["cleanupNanos"] >= minimum_cleanup, "TOP_CLEANUP_ACCOUNTING")


def validate_result(doc, context, run_id: str, op1_raw: bytes, result_raw=None):
    _ascii_tree(doc, "RESULT_ASCII")
    nreader = _require_n_reader(context)
    need(type(op1_raw) is bytes and 0 < len(op1_raw) <= 8 * 1024 * 1024,
         "ORIGINAL_RECEIPT_BYTES")
    exact_keys(doc, RESULT_KEYS, "RESULT_SCHEMA")
    need(doc["schema"] == RESULT_SCHEMA and doc["mode"] == RESULT_MODE
         and doc["status"] == "COMPLETED_DIAGNOSTIC_ONLY"
         and is_uuid(run_id)
         and doc["runId"] == run_id
         and doc["wireContractSha256"] == WIRE_SHA256
         and doc["sourceManifestSha256"] == context["sourceManifestSha256"]
         and doc["descriptorSha256"] == context["descriptorSha256"],
         "RESULT_IDENTITY")
    prior_expected = _expected_prior_ref(context)
    exact_keys(doc["qualifiedPrior"], PRIOR_KEYS, "QUALIFIED_PRIOR_SCHEMA")
    exact_keys(doc["qualifiedPrior"]["priorOriginalReceipt"], RECEIPT_KEYS,
               "PRIOR_RECEIPT_SCHEMA")
    need(doc["qualifiedPrior"] == prior_expected, "QUALIFIED_PRIOR_BINDING")
    pad_rows, captured = _prior_semantics(context)
    capture = doc["originalCapture"]
    receipt = _validate_original_capture(capture, context, captured, op1_raw, run_id)
    # This is read from the qualified prior op-1 only after the new original
    # capture has bound its template and pad identity to that same source.
    expected_grid_dimensions = _prior_grid_dimensions(context)
    cohort = doc["cohort"]
    exact_keys(cohort, COHORT_KEYS, "COHORT_SCHEMA")
    prior_disposition = prior_expected["trialDisposition"]
    cohort_ids = prior_disposition["cohortNetIds"]
    requested = nreader.stable_cohort_order(captured["netIds"], cohort_ids)
    need(cohort["netIds"] == cohort_ids and cohort["requestedNetIds"] == requested,
         "COHORT_ORDER_BINDING")
    arms_value = doc["arms"]
    exact_keys(arms_value, ("null", "observe"), "ARMS_SCHEMA")
    arm_ids = [arms_value[name].get("armId") if type(arms_value[name]) is dict else None
               for name in ("null", "observe")]
    need(len(set(arm_ids + [run_id, capture["captureId"]])) == 4,
         "ARM_ID_DUPLICATE")
    pads_by_id, pads_by_net = _pad_lookup(pad_rows)
    need(len(pads_by_net) == 60 and set(pads_by_net) == set(captured["netIds"])
         and all(pads_by_net[net] for net in captured["netIds"]),
         "PAD_NET_BIJECTION")
    arms = {}
    observations = {}
    for name, role in (("null", "NULL"), ("observe", "OBSERVE")):
        arms[name] = _validate_arm(arms_value[name], role, cohort_ids, captured,
                                   nreader, pads_by_id, pads_by_net,
                                   capture["template"], run_id,
                                   expected_grid_dimensions)
        observations[name] = arms[name]["observationFacts"]
    null_run, observe_run = arms["null"]["freshRun"], arms["observe"]["freshRun"]
    need(_parity_projection(null_run) == _parity_projection(observe_run),
         "ARM_PARITY")
    _validate_finite_expectation(arms)
    work = _validate_work(doc["work"], capture, arms, observations)
    metadata_minimum = nreader._result_tree_minimum_units(doc)
    need(work["metadataUnits"] >= metadata_minimum, "WORK_METADATA_TREE")
    need(receipt["receiptOrdinal"] == 307 and receipt["lineageOrdinal"] == 77,
         "ORIGINAL_RECEIPT_PRECONDITION")
    _validate_top_cleanup(doc["cleanup"], capture, arms, work)
    result_sha = sha(result_raw) if type(result_raw) is bytes else None
    return {
        "status": "PASS_OBSERVED_COHORT_TERMINAL_TELEMETRY",
        "comparisonStatus": "FINITE_REFERENCE_MATCH",
        "runId": run_id,
        "sourceManifestSha256": context["sourceManifestSha256"],
        "descriptorSha256": context["descriptorSha256"],
        "resultSha256": result_sha,
        "arms": {name: {"disposition": arms[name]["facts"]["outcome"]["disposition"],
                        "reason": arms[name]["facts"]["outcome"]["reason"],
                        "expanded": arms[name]["facts"]["outcome"]["expanded"],
                        "traversed": len(arms[name]["freshRun"]["ordering"]["traversedNetIds"]),
                        "observation": arms[name]["observationFacts"]["state"]}
                 for name in ("null", "observe")},
        "work": work,
    }


def _parity_projection(run):
    return {
        "initial": run["initial"],
        "ordering": run["ordering"],
        "outcome": run["outcome"],
        "terminalFailure": run["terminalFailure"],
        "finalizers": run["finalizers"],
        "layout": run["layout"],
        "advanceCalls": run["work"]["advanceCalls"],
    }


def _validate_finite_expectation(arms):
    for name in ("null", "observe"):
        run = arms[name]["freshRun"]
        facts = arms[name]["facts"]
        observation = arms[name]["observationFacts"]
        observation_dto = arms[name]["observation"]
        need(facts["outcome"]["reason"] == "D_COIL_LOW:BRANCH_SEARCH_LIMIT"
             and facts["outcome"]["expanded"] == 419_905
             and len(run["ordering"]["traversedNetIds"]) == 43
             and observation["state"] == (
                 "NOT_ENABLED" if name == "null" else "FIRST_NON_FOUND"),
             "FINITE_REFERENCE_MISMATCH")
        if name == "null":
            need(observation_dto["selectedSnapshot"] is None,
                 "FINITE_REFERENCE_MISMATCH")
        else:
            selected = observation_dto["selectedSnapshot"]
            need(type(selected) is dict
                 and selected["netId"] == "D_COIL_LOW"
                 and selected["terminalReason"] == "BRANCH_SEARCH_LIMIT"
                 and selected["totalExpanded"] == 419_905,
                 "FINITE_REFERENCE_MISMATCH")


def _validate_marker(marker, result_raw, doc, context):
    _ascii_tree(marker, "MARKER_ASCII")
    exact_keys(marker, R7_MARKER_KEYS, "MARKER_SCHEMA")
    work = doc["work"]
    export_units = 5 * ((len(result_raw) + COPY_CHUNK - 1) // COPY_CHUNK) + 3
    need(marker["schema"] == MARKER_SCHEMA
         and marker["status"] == "COMPLETED_DIAGNOSTIC"
         and marker["runId"] == doc["runId"]
         and marker["sourceManifestSha256"] == context["sourceManifestSha256"]
         and marker["wireContractSha256"] == WIRE_SHA256
         and marker["descriptorSha256"] == context["descriptorSha256"]
         and marker["resultSha256"] == sha(result_raw)
         and is_int(marker["resultBytes"], len(result_raw), len(result_raw))
         and is_int(marker["elapsedNanos"], 1, MAX_TOTAL_NS)
         and is_int(marker["finalExportNanos"], 1, MAX_GROUP_NS)
         and is_int(marker["totalGroups"], work["groupsBeforeExport"] + 1,
                    work["groupsBeforeExport"] + 1)
         and is_int(marker["totalUnits"], work["unitsBeforeExport"] + export_units,
                    work["unitsBeforeExport"] + export_units)
         and is_int(marker["maxGroupNanos"],
                    max(work["maxGroupNanosBeforeExport"], marker["finalExportNanos"]),
                    max(work["maxGroupNanosBeforeExport"], marker["finalExportNanos"]))
         and is_int(marker["maximumUnitsPerGroup"],
                    max(work["maximumUnitsPerGroupBeforeExport"], export_units),
                    max(work["maximumUnitsPerGroupBeforeExport"], export_units))
         and export_units <= MAX_GROUP_UNITS
         and marker["elapsedNanos"] >= work["elapsedCutoffNanos"]
         + marker["finalExportNanos"], "MARKER_BINDING")
    return marker


def _r7_validate_json_domain(value):
    """R7 canonical writer domain: Long/Boolean/null, lists, maps and ASCII text."""
    if value is None or type(value) is bool:
        return
    if type(value) is int:
        need(MIN_SIGNED_LONG <= value <= MAX_SIGNED_LONG, "R7_INTEGER_DOMAIN")
        return
    if type(value) is str:
        need(len(value) <= 256 and all(0x20 <= ord(char) <= 0x7e
                                       for char in value), "R7_STRING_DOMAIN")
        return
    if type(value) is list:
        for item in value:
            _r7_validate_json_domain(item)
        return
    if type(value) is dict:
        for key, item in value.items():
            need(type(key) is str and len(key) <= 256
                 and all(0x20 <= ord(char) <= 0x7e for char in key),
                 "R7_KEY_DOMAIN")
            _r7_validate_json_domain(item)
        return
    raise ReaderError("R7_VALUE_DOMAIN")


def _r7_canonical_json_line(value, raw, max_bytes, code):
    need(type(raw) is bytes and len(raw) >= 2
         and raw.endswith(b"\n") and b"\r" not in raw
         and not raw.startswith(b"\xef\xbb\xbf")
         and b"\n" not in raw[:-1], code + "_FRAMING")
    need(len(raw) <= max_bytes, code + "_SIZE")
    _r7_validate_json_domain(value)
    try:
        canonical = json.dumps(value, ensure_ascii=True, allow_nan=False,
                              separators=(",", ":"), sort_keys=False).encode("ascii") + b"\n"
    except (TypeError, ValueError, UnicodeError, RecursionError):
        raise ReaderError(code + "_ENCODING") from None
    need(raw == canonical, code + "_CANONICAL")
    return len(raw)


def validate_r7_marker_line(marker, marker_raw):
    """Validate the frozen 14-field marker shape and exact M-byte line bound."""
    need(type(marker) is dict, "R7_MARKER_SCHEMA")
    exact_keys(marker, R7_MARKER_KEYS, "R7_MARKER_SCHEMA")
    size = _r7_canonical_json_line(marker, marker_raw, MAX_MARKER_BYTES,
                                   "R7_MARKER")
    string_fields = (
        "schema", "status", "runId", "sourceManifestSha256",
        "wireContractSha256", "descriptorSha256", "resultSha256",
    )
    need(all(type(marker[name]) is str for name in string_fields)
         and marker["schema"] == MARKER_SCHEMA
         and marker["status"] == "COMPLETED_DIAGNOSTIC"
         and is_uuid(marker["runId"])
         and all(is_sha(marker[name]) for name in (
             "sourceManifestSha256", "wireContractSha256", "descriptorSha256",
             "resultSha256")), "R7_MARKER_STRINGS")
    numeric_fields = (
        "resultBytes", "elapsedNanos", "finalExportNanos", "totalGroups",
        "totalUnits", "maxGroupNanos", "maximumUnitsPerGroup",
    )
    need(all(type(marker[name]) is int
             and 0 <= marker[name] <= MAX_SIGNED_LONG
             for name in numeric_fields), "R7_MARKER_NUMBERS")
    return size


def _r7_export_copy_deltas(result_bytes, marker_bytes):
    need(is_int(result_bytes, 2, MAX_JSON_LINE)
         and is_int(marker_bytes, 2, MAX_MARKER_BYTES), "R7_COPY_LENGTHS")
    result_chunks = (result_bytes + COPY_CHUNK - 1) // COPY_CHUNK
    return (2 * result_bytes + EXPORT_DIGEST_BYTES + marker_bytes,
            2 * result_chunks + 2, result_chunks)


def derive_export_copy_reconciliation(result_doc, result_raw,
                                      marker_doc, marker_raw):
    """Derive R7 export copy admissions from the two canonical output lines.

    The return value reports a bounded source derivation only. It is not a
    direct Meter observation or a top-level D acceptance result.
    """
    need(type(result_doc) is dict, "R7_RESULT_SCHEMA")
    exact_keys(result_doc, RESULT_KEYS, "R7_RESULT_SCHEMA")
    result_bytes = _r7_canonical_json_line(
        result_doc, result_raw, MAX_JSON_LINE, "R7_RESULT")
    need(result_doc["schema"] == RESULT_SCHEMA
         and result_doc["mode"] == RESULT_MODE
         and result_doc["status"] == "COMPLETED_DIAGNOSTIC_ONLY"
         and is_uuid(result_doc["runId"])
         and is_sha(result_doc["sourceManifestSha256"])
         and result_doc["wireContractSha256"] == WIRE_SHA256
         and is_sha(result_doc["descriptorSha256"]), "R7_RESULT_IDENTITY")
    marker_bytes = validate_r7_marker_line(marker_doc, marker_raw)
    need(marker_doc["runId"] == result_doc["runId"]
         and marker_doc["sourceManifestSha256"] == result_doc["sourceManifestSha256"]
         and marker_doc["wireContractSha256"] == result_doc["wireContractSha256"]
         and marker_doc["descriptorSha256"] == result_doc["descriptorSha256"]
         and marker_doc["resultBytes"] == result_bytes
         and marker_doc["resultSha256"] == sha(result_raw),
         "R7_MARKER_RESULT_BINDING")

    work = result_doc["work"]
    exact_keys(work, WORK_KEYS, "R7_WORK_SCHEMA")
    pre_entries = work["copiedEntries"]
    pre_chunks = work["copyChunks"]
    need(is_int(pre_entries, 0, MAX_COPY_ENTRIES)
         and is_int(pre_chunks, 0, MAX_COPY_ENTRIES)
         and pre_chunks <= pre_entries
         and work["copiedEntryLimit"] == MAX_COPY_ENTRIES
         and work["copyChunkLimit"] == COPY_CHUNK,
         "R7_PREEXPORT_COPY_BINDING")

    export_entries, export_chunks, result_chunks = _r7_export_copy_deltas(
        result_bytes, marker_bytes)
    required_entries = pre_entries + export_entries
    required_chunks = pre_chunks + export_chunks
    need(required_entries <= MAX_COPY_ENTRIES
         and required_chunks <= MAX_COPY_ENTRIES
         and required_chunks <= required_entries,
         "R7_EXPORT_COPY_CAPACITY")
    return {
        "status": "R7_COPY_RECONCILIATION_DERIVED_ONLY",
        "resultBytes": result_bytes,
        "resultChunks": result_chunks,
        "markerBytes": marker_bytes,
        "derivedExportCopiedEntries": export_entries,
        "derivedExportCopyChunks": export_chunks,
        "requiredCumulativeCopiedEntries": required_entries,
        "requiredCumulativeCopyChunks": required_chunks,
        "scope": "SOURCE_BOUND_DERIVATION_NOT_METER_OBSERVATION",
    }


R8_P07_PAD_KEYS = (
    "record", "group", "padId", "netId", "componentId", "x", "y", "role",
)
R8_PAD_PHYSICAL_KEYS = ("netId", "componentId", "x", "y", "role")


def _r8_pad_projection(rows, code):
    need(type(rows) is list, code + "_ROWS")
    by_pad_id = {}
    for row in rows:
        exact_keys(row, R8_P07_PAD_KEYS, code + "_SCHEMA")
        pad_id = row["padId"]
        net_id = row["netId"]
        component_id = row["componentId"]
        role = row["role"]
        x, y = row["x"], row["y"]
        need(all(type(value) is str and bool(value)
                 for value in (pad_id, net_id, component_id, role))
             and is_int(x, -(2**31), 2**31 - 1)
             and is_int(y, -(2**31), 2**31 - 1),
             code + "_PHYSICAL_FIELDS")
        _ascii_tree(pad_id, code + "_PHYSICAL_FIELDS")
        _ascii_tree(net_id, code + "_PHYSICAL_FIELDS")
        _ascii_tree(component_id, code + "_PHYSICAL_FIELDS")
        _ascii_tree(role, code + "_PHYSICAL_FIELDS")
        need(pad_id not in by_pad_id, code + "_DUPLICATE_PAD_ID")
        by_pad_id[pad_id] = tuple(row[name] for name in R8_PAD_PHYSICAL_KEYS)
    return by_pad_id


def derive_original_group_reconciliation(result_doc, op1_raw, p07_reader,
                                         qualified_pads):
    """Derive owner-group census and bind fresh physical PAD identity.

    The P07 parser receives only its required conservative elapsed/group
    envelope. Its PAD rows are compared in the same parse with the already
    authenticated qualified-prior P07 PAD rows. This result is a bounded
    source census, not an exact timing DTO or a top-level D acceptance result.
    """
    need(type(result_doc) is dict, "R8_RESULT_SCHEMA")
    exact_keys(result_doc, RESULT_KEYS, "R8_RESULT_SCHEMA")
    need(result_doc["schema"] == RESULT_SCHEMA
         and result_doc["mode"] == RESULT_MODE
         and result_doc["status"] == "COMPLETED_DIAGNOSTIC_ONLY"
         and is_uuid(result_doc["runId"])
         and is_sha(result_doc["sourceManifestSha256"])
         and result_doc["wireContractSha256"] == WIRE_SHA256,
         "R8_RESULT_IDENTITY")
    need(type(op1_raw) is bytes and 0 < len(op1_raw) <= MAX_OPERATION1_BYTES,
         "R8_OPERATION1_BYTES")
    need(type(p07_reader) is types.ModuleType
         and callable(getattr(p07_reader, "validate_operation", None)),
         "R8_P07_READER")

    work = result_doc["work"]
    exact_keys(work, WORK_KEYS, "R8_WORK_SCHEMA")
    elapsed_envelope = work["elapsedCutoffNanos"]
    group_envelope = work["maxGroupNanosBeforeExport"]
    original_capture_groups = work["originalCaptureGroups"]
    groups_before_export = work["groupsBeforeExport"]
    wrapper_groups = work["wrapperGroupsBeforeExport"]
    need(is_int(elapsed_envelope, 1, MAX_TOTAL_NS)
         and is_int(group_envelope, 1, MAX_GROUP_NS)
         and is_int(original_capture_groups, 1, MAX_GROUPS)
         and is_int(groups_before_export, 1, MAX_GROUPS - 1)
         and is_int(wrapper_groups, 1, MAX_GROUPS)
         and wrapper_groups <= groups_before_export,
         "R8_WORK_GROUP_BOUNDS")

    # P07 operation-1 has no exact capture timing DTO. These two values are
    # deliberately passed as a conservative total-prefix envelope.
    entry = {
        "elapsedToCloseNanos": elapsed_envelope,
        "maxFullGroupNanos": group_envelope,
    }
    try:
        parsed = p07_reader.validate_operation(
            op1_raw, entry, result_doc["sourceManifestSha256"],
            result_doc["wireContractSha256"], 1, result_doc["runId"], "AUTHORED")
    except Exception as failure:
        reason = getattr(failure, "code", None)
        if type(reason) is str and re.fullmatch(r"[A-Za-z0-9_]{1,96}", reason):
            raise ReaderError("R8_OPERATION1_" + reason.upper()) from None
        raise ReaderError("R8_OPERATION1_PARSE") from None
    need(type(parsed) is dict and type(parsed.get("groups")) is list
         and type(parsed.get("pads")) is list,
         "R8_OPERATION1_PARSED_SHAPE")
    owner_group_count = len(parsed["groups"])
    need(is_int(owner_group_count, 1, MAX_GROUPS), "R8_OWNER_GROUP_CENSUS")
    need(original_capture_groups >= owner_group_count,
         "R8_CAPTURE_OWNER_GROUPS")
    need(groups_before_export - owner_group_count == wrapper_groups,
         "R8_WRAPPER_GROUP_RECONCILIATION")
    prior_pad_projection = _r8_pad_projection(qualified_pads, "R8_PRIOR_PAD")
    fresh_pad_projection = _r8_pad_projection(parsed["pads"], "R8_FRESH_PAD")
    need(bool(prior_pad_projection)
         and fresh_pad_projection == prior_pad_projection,
         "R8_PAD_PHYSICAL_IDENTITY_MISMATCH")
    return {
        "schema": "R8_ORIGINAL_GROUP_CENSUS_DERIVED_ONLY",
        "ownerGroupCount": owner_group_count,
        "originalCaptureGroups": original_capture_groups,
        "groupsBeforeExport": groups_before_export,
        "wrapperGroupsBeforeExport": wrapper_groups,
        "padBinding": "QUALIFIED_PRIOR_PHYSICAL_IDENTITY_MATCH",
        "padCount": len(prior_pad_projection),
        "timingBinding": "CONSERVATIVE_TOTAL_PREFIX_ENVELOPE",
        "scope": "AUTHENTICATED_OPERATION1_GROUP_AND_PAD_CENSUS_ONLY",
    }


def _canary_fixture_profile(name):
    need(type(name) is str, "CANARY_FIXTURE_PROFILE")
    if name == "open":
        captured, requested, pads = (
            _CANARY_OPEN_NETS, _CANARY_OPEN_REQUESTED, _CANARY_OPEN_PADS)
    elif name == "blocked":
        captured, requested, pads = (
            _CANARY_BLOCKED_NETS, _CANARY_BLOCKED_REQUESTED, _CANARY_BLOCKED_PADS)
    else:
        raise ReaderError("CANARY_FIXTURE_PROFILE")
    return {
        "name": name,
        "capturedNetIds": tuple(captured),
        "requestedCohortNetIds": CANARY_FIXTURE_COHORT,
        "requestedNetIds": tuple(requested),
        "pads": tuple(tuple(row) for row in pads),
        "gridDimensions": CANARY_FIXTURE_GRID,
    }


def _canary_profile_for_arm(name, run):
    if name == "complete":
        return _canary_fixture_profile("open")
    if name == "blocked":
        return _canary_fixture_profile("blocked")
    need(name == "quality", "CANARY_ARM_NAME")
    ordering = run.get("ordering") if type(run) is dict else None
    captured = ordering.get("capturedNetIds") if type(ordering) is dict else None
    matches = [fixture for fixture in ("open", "blocked")
               if type(captured) is list
               and captured == list(_canary_fixture_profile(fixture)["capturedNetIds"])]
    need(len(matches) == 1, "CANARY_FIXTURE_VECTOR")
    return _canary_fixture_profile(matches[0])


def _canary_source_bindings(context):
    need(type(context) is dict, "CANARY_SOURCE_CONTEXT")
    n_context = context.get("nContext")
    sources = n_context.get("sources") if type(n_context) is dict else None
    fixture_path = str(CANARY_FIXTURE_SOURCE_PATH)
    need(type(n_context) is dict and n_context.get("sourceSha") == N_RUN_SHA256
         and type(sources) is list, "CANARY_FIXTURE_SOURCE_CONTEXT")
    fixture_rows = [row for row in sources
                    if type(row) is dict and row.get("path") == fixture_path]
    need(len(fixture_rows) == 1
         and fixture_rows[0].get("bytes") == CANARY_FIXTURE_SOURCE_BYTES
         and fixture_rows[0].get("sha256") == CANARY_FIXTURE_SOURCE_SHA256,
         "CANARY_FIXTURE_SOURCE_BINDING")

    descriptor = context.get("descriptor")
    native = (descriptor.get("nativeSharedBudgetSource")
              if type(descriptor) is dict else None)
    capture_ref = native.get("derivedCaptureRef") if type(native) is dict else None
    need(type(native) is dict
         and native.get("captureOwnerRowOrdinal") == CANARY_CAPTURE_PARENT_ORDINAL
         and type(capture_ref) is dict
         and capture_ref.get("role") == "derived-capture-source"
         and capture_ref.get("path") == str(CANARY_CAPTURE_PARENT_PATH)
         and capture_ref.get("bytes") == CANARY_CAPTURE_PARENT_BYTES
         and capture_ref.get("sha256") == CANARY_CAPTURE_PARENT_SHA256,
         "CANARY_CAPTURE_PARENT_BINDING")
    return CANARY_CAPTURE_PARENT_SHA256


def _validate_canary_source_review(source_review, capture_parent_sha256):
    exact_keys(source_review, ("sourceSha256", "localAliasRelease"),
               "SOURCE_REVIEW_SCHEMA")
    need(source_review["sourceSha256"] == capture_parent_sha256
         and source_review["localAliasRelease"] == "SOURCE_ONLY",
         "SOURCE_REVIEW_BINDING")
    return source_review


def _canary_pad_lookup(profile):
    need(type(profile) is dict and type(profile.get("pads")) is tuple,
         "CANARY_FIXTURE_PAD_PROFILE")
    by_id, by_net = {}, {}
    for row in profile["pads"]:
        need(type(row) is tuple and len(row) == 6
             and all(type(row[index]) is str and bool(row[index])
                     for index in (0, 1, 2, 5))
             and all(is_int(row[index], -(2**31), 2**31 - 1)
                     for index in (3, 4)), "CANARY_FIXTURE_PAD_PROFILE")
        pad_id, net_id, _component_id, x, y, _role = row
        need(pad_id not in by_id, "CANARY_FIXTURE_PAD_PROFILE")
        by_id[pad_id] = (net_id, x, y)
        by_net.setdefault(net_id, set()).add(pad_id)
    need(len(by_id) == (4 if profile["name"] == "open" else 8)
         and set(by_net) == set(profile["capturedNetIds"]),
         "CANARY_FIXTURE_PAD_PROFILE")
    return by_id, by_net


def _validate_canary_fixture_binding(run, profile):
    need(type(run) is dict, "CANARY_CAPTURE_CONTEXT")
    need(run.get("kind") == "OBSERVED_FAILURE_COHORT_TRIAL", "CANARY_RUN_KIND")
    initial, ordering = run.get("initial"), run.get("ordering")
    need(type(initial) is dict and type(ordering) is dict,
         "CANARY_CAPTURE_CONTEXT")
    need(initial.get("priorPromotedNetId") is None,
         "CANARY_FIXTURE_PRIOR_PROMOTION")
    need(type(ordering.get("capturedNetIds")) is list
         and ordering["capturedNetIds"] == list(profile["capturedNetIds"])
         and type(ordering.get("computedNetIds")) is list
         and ordering["computedNetIds"] == list(profile["capturedNetIds"])
         and type(ordering.get("requestedCohortNetIds")) is list
         and ordering["requestedCohortNetIds"]
         == list(profile["requestedCohortNetIds"])
         and type(ordering.get("requestedNetIds")) is list
         and ordering["requestedNetIds"] == list(profile["requestedNetIds"]),
         "CANARY_FIXTURE_ORDERING")


def _validate_canary_layout_pad_binding(layout, profile):
    expected = {
        (row[0], row[1], row[2], row[3], row[4])
        for row in profile["pads"]
    }
    pads = layout.get("pads") if type(layout) is dict else None
    need(type(pads) is list and len(pads) == len(expected),
         "CANARY_FIXTURE_LAYOUT_PADS")
    actual = []
    for row in pads:
        need(type(row) is dict
             and all(type(row.get(name)) is str and bool(row.get(name))
                     for name in ("padId", "netId", "componentId"))
             and all(is_int(row.get(name), -(2**31), 2**31 - 1)
                     for name in ("x", "y"))
             and row.get("attachment") == "PLATED_THROUGH_HOLE"
             and row.get("topCopper") is True and row.get("bottomCopper") is True,
             "CANARY_FIXTURE_LAYOUT_PADS")
        actual.append((row.get("padId"), row.get("netId"),
                       row.get("componentId"), row.get("x"), row.get("y")))
    need(len(set(actual)) == len(actual) and set(actual) == expected,
         "CANARY_FIXTURE_LAYOUT_PADS")
    return len(actual)


def _load_r9_canary_text_policy():
    raw = _read_regular(R9_CANARY_TEXT_POLICY_PATH,
                        R9_CANARY_TEXT_POLICY_MAX_BYTES,
                        "CANARY_TEXT_POLICY_READ")
    need(sha(raw) == R9_CANARY_TEXT_POLICY_SHA256,
         "CANARY_TEXT_POLICY_HASH")
    module = types.ModuleType("_pinned_r9_canary_text_policy")
    module.__file__ = str(R9_CANARY_TEXT_POLICY_PATH)
    try:
        exec(compile(raw, str(R9_CANARY_TEXT_POLICY_PATH), "exec"), module.__dict__)
    except Exception:
        raise ReaderError("CANARY_TEXT_POLICY_LOAD") from None
    need(callable(getattr(module, "validate_canary_text_tree", None)),
         "CANARY_TEXT_POLICY_API")
    return module


def _validate_canary_text_domain(doc):
    policy = _load_r9_canary_text_policy()
    try:
        summary = policy.validate_canary_text_tree(doc)
    except Exception as failure:
        code = getattr(failure, "code", None)
        if type(code) is str and re.fullmatch(r"BF_[A-Z0-9_]{1,96}", code):
            raise ReaderError(code) from None
        raise ReaderError("CANARY_TEXT_POLICY_INTERNAL") from None
    need(type(summary) is dict
         and summary.get("schema") == "tsj-r9-canary-text-policy-summary-v1"
         and all(is_int(summary.get(name), 0, MAX_EXPANSIONS)
                 for name in ("visitedValues", "visitedKeys", "stringValues",
                              "extendedGeometryCanonicalValues")),
         "CANARY_TEXT_POLICY_SUMMARY")
    return summary


def _validate_canary_arm(arm, name, context, run_id):
    need(type(arm) is dict, "CANARY_ARM_SCHEMA")
    run = arm.get("freshRun")
    profile = _canary_profile_for_arm(name, run)
    _validate_canary_fixture_binding(run, profile)
    pads_by_id, pads_by_net = _canary_pad_lookup(profile)
    cohort = list(profile["requestedCohortNetIds"])
    net_ids = list(profile["capturedNetIds"])
    initial = run["initial"]
    owner = {
        "policy": initial.get("policy"),
        "maximumExpansions": initial.get("maximumExpansions"),
        "expandedAtFailure": 0,
        "maximumVias": initial.get("maximumVias"),
        "viaCost": initial.get("viaCost"),
        "policyTransitions": initial.get("policyTransitions"),
        "policyVias": initial.get("policyVias"),
        "branchOrderStrategy": initial.get("branchOrderStrategy"),
        "netOrderStrategy": initial.get("netOrderStrategy"),
        "roleWidths": initial.get("roleWidths"),
        "orderingStream": initial.get("capturedOrderingStream"),
    }
    captured = {"owner": owner, "netIds": net_ids, "failedNetId": cohort[0],
                "priorPromotedNetId": None}
    # Canary arms are still actual sessions and retain the same strict shape.
    validated = _validate_arm(arm, "OBSERVE", cohort, captured,
                              context["baseReader"], pads_by_id, pads_by_net,
                              None, run_id, profile["gridDimensions"], canary=True)
    fresh_run = validated["freshRun"]
    if name == "complete":
        _validate_canary_layout_pad_binding(fresh_run["layout"], profile)
    elif name == "blocked":
        need(fresh_run["layout"] is None, "CANARY_BLOCKED_LAYOUT")
    return validated


def validate_canary(doc, context, expected_run_id):
    _require_n_reader(context)
    _validate_canary_text_domain(doc)
    exact_keys(doc, CANARY_KEYS, "CANARY_SCHEMA")
    need(doc["schema"] == CANARY_SCHEMA
         and doc["status"] == "OWNER_CANARIES_RECORDED_ONLY"
         and is_uuid(expected_run_id)
         and doc["runId"] == expected_run_id
         and doc["sourceManifestSha256"] == context["sourceManifestSha256"]
         and doc["wireContractSha256"] == WIRE_SHA256,
         "CANARY_IDENTITY")
    # Small canary fixtures bind to their own immutable N source projections.
    capture_parent_sha256 = _canary_source_bindings(context)
    complete = _validate_canary_arm(doc["complete"], "complete", context,
                                    expected_run_id)
    blocked = _validate_canary_arm(doc["blocked"], "blocked", context,
                                   expected_run_id)
    need(complete["observationFacts"]["state"] == "NO_FAILURE"
         and complete["facts"]["outcome"]["disposition"] == "FOUND"
         and blocked["observationFacts"]["state"] == "FIRST_NON_FOUND"
         and blocked["facts"]["outcome"]["disposition"] != "FOUND",
         "CANARY_OUTCOME_BINDING")
    quality = doc["nonBranchQuality"]
    if quality is None:
        quality_status = "UNQUALIFIED_QUALITY_CANARY"
    else:
        quality_arm = _validate_canary_arm(quality, "quality", context,
                                           expected_run_id)
        need(quality_arm["observationFacts"]["state"] == "NON_BRANCH_REJECTION",
             "CANARY_QUALITY_STATE")
        quality_status = "QUALIFIED_QUALITY_CANARY"
    canary_arms = [doc["complete"], doc["blocked"]]
    if quality is not None:
        canary_arms.append(quality)
    canary_arm_ids = [arm.get("armId") if type(arm) is dict else None
                      for arm in canary_arms]
    need(all(is_uuid(arm_id) for arm_id in canary_arm_ids)
         and len(set(canary_arm_ids + [expected_run_id])) == len(canary_arm_ids) + 1,
         "CANARY_ARM_ID_DUPLICATE")
    generic = doc["genericCallbackThrow"]
    exact_keys(generic, GENERIC_THROW_KEYS, "GENERIC_THROW_SCHEMA")
    need(generic["caughtSameThrowable"] is True
         and is_int(generic["constructorEntriesBefore"], 0, MAX_INT)
         and is_int(generic["constructorEntriesAfter"], 1, MAX_INT)
         and generic["constructorEntriesAfter"] - generic["constructorEntriesBefore"] == 1
         and is_int(generic["callbackExitMeterUnits"], 0, MAX_GROUP_UNITS * MAX_GROUPS)
         and is_int(generic["afterInvokeMeterUnits"], 1, MAX_GROUP_UNITS * MAX_GROUPS)
         and generic["afterInvokeMeterUnits"] - generic["callbackExitMeterUnits"] == 1,
         "GENERIC_THROW_BINDING")
    direct = doc["directOwnerAbortReuse"]
    exact_keys(direct, DIRECT_ABORT_KEYS, "DIRECT_ABORT_SCHEMA")
    need(type(direct["sentinelKind"]) is str and bool(direct["sentinelKind"])
         and direct["caughtSameThrowable"] is True
         and is_int(direct["lastWork"], 0, 0)
         and all(direct[name] is True for name in (
             "diagnosticFailureSameThrowable", "searchCleared", "candidateCleared",
             "resultCleared", "retrySameThrowable", "sourcePlacementUnchanged",
             "harnessAliasesDropped")), "DIRECT_ABORT_BINDING")
    _validate_canary_source_review(doc["sourceReview"], capture_parent_sha256)
    return {"status": "PASS_OWNER_CANARIES_RECORDED_ONLY",
            "qualityStatus": quality_status}


def _require_exact_names(names, expected, code):
    need(type(names) in (list, tuple)
         and all(type(name) is str for name in names)
         and len(names) == len(set(names))
         and tuple(sorted(names)) == tuple(sorted(expected)), code)


def _parse_cli_args(argv):
    """Parse the frozen reader CLI without allowing abbreviations or duplicates."""
    need(type(argv) is list and all(type(item) is str for item in argv),
         "CLI_ARGUMENTS")
    canary = bool(argv) and argv[0] == "--canary"
    pairs = argv[1:] if canary else argv
    need(len(pairs) == 8 and len(pairs) % 2 == 0, "CLI_ARGUMENTS")
    values = {}
    for index in range(0, len(pairs), 2):
        name, value = pairs[index], pairs[index + 1]
        need(name in ("--manifest", "--manifest-sha", "--parent", "--data")
             and name not in values and type(value) is str and bool(value)
             and "\x00" not in value, "CLI_ARGUMENTS")
        values[name] = value
    need(set(values) == {"--manifest", "--manifest-sha", "--parent", "--data"}
         and is_sha(values["--manifest-sha"]), "CLI_ARGUMENTS")
    return {"canary": canary,
            "manifest": values["--manifest"],
            "manifestSha256": values["--manifest-sha"],
            "parent": values["--parent"],
            "data": values["--data"]}


def _require_supported_cli(options):
    need(type(options) is dict and type(options.get("canary")) is bool
         and set(options) == {"canary", "manifest", "manifestSha256", "parent", "data"},
         "CLI_OPTIONS")


def _expected_native_args(context, run_id, data_dir, mode=NORMAL_NATIVE_MODE):
    """Return the exact typed child argv for one explicitly selected protocol."""
    need(type(context) is dict and is_uuid(run_id), "NATIVE_RUN_ID")
    need(mode in (NORMAL_NATIVE_MODE, CANARY_NATIVE_MODE), "NATIVE_MODE")
    source_sha = context.get("sourceManifestSha256")
    descriptor_sha = context.get("descriptorSha256")
    classes_root = context.get("classesRoot")
    jars = context.get("jars")
    need(is_sha(source_sha) and is_sha(descriptor_sha)
         and isinstance(classes_root, Path) and classes_root.is_absolute()
         and type(jars) is dict, "NATIVE_CONTEXT")
    user_jar = jars.get("gwt-user-2.7.0.jar")
    dev_jar = jars.get("gwt-dev-2.7.0.jar")
    need(type(user_jar) is dict and type(dev_jar) is dict
         and type(user_jar.get("path")) is str and type(dev_jar.get("path")) is str
         and Path(user_jar["path"]).is_absolute()
         and Path(dev_jar["path"]).is_absolute(), "NATIVE_JAR_CONTEXT")
    data_path = data_dir if isinstance(data_dir, Path) else Path(data_dir)
    need(data_path.is_absolute(), "NATIVE_DATA_PATH")
    classpath = ";".join((str(classes_root), user_jar["path"], dev_jar["path"]))
    return ["-ea", "-cp", classpath, ENTRY_CLASS,
            mode, run_id, source_sha,
            WIRE_SHA256, descriptor_sha, str(data_path)]


def _load_d_builder():
    raw = _read_regular(D_BUILDER_PATH, 4 * 1024 * 1024, "D_BUILD_HELPER_READ")
    need(sha(raw) == D_BUILDER_SHA256, "D_BUILD_HELPER_HASH")
    module = types.ModuleType("_authenticated_cohort_terminal_build_inputs_r2")
    module.__file__ = str(D_BUILDER_PATH)
    try:
        exec(compile(raw, str(D_BUILDER_PATH), "exec"), module.__dict__)
    except (Exception, SystemExit):
        raise ReaderError("D_BUILD_HELPER_LOAD") from None
    need(callable(getattr(module, "validate_run_manifest", None)),
         "D_BUILD_HELPER_API")
    return module


def _host_call(host, name, *args, error_code, **kwargs):
    method = getattr(host, name, None)
    need(callable(method), "HOST_API_" + name.upper())
    try:
        return method(*args, **kwargs)
    except Exception as failure:
        reason = getattr(failure, "code", None)
        if type(reason) is str and re.fullmatch(r"[A-Za-z0-9_]{1,96}", reason):
            raise ReaderError(error_code + "_" + reason.upper()) from None
        raise ReaderError(error_code) from None


def _canonical_host_path(host, value, code):
    need(type(value) is str and bool(value), code + "_TYPE")
    path = Path(value)
    need(path.is_absolute() and str(path) == value, code + "_ABSOLUTE")
    canonical = _host_call(host, "_canonical", value, code,
                           error_code=code + "_CANONICAL")
    need(isinstance(canonical, Path) and canonical == path, code + "_IDENTITY")
    return canonical


def _host_directory_names(host, path, expected, code):
    _host_call(host, "_lstat_directory", path, code + "_TYPE",
               error_code=code + "_TYPE")
    try:
        names = sorted(entry.name for entry in path.iterdir())
    except OSError:
        raise ReaderError(code + "_READ") from None
    _require_exact_names(names, expected, code + "_CLOSURE")
    return names


def _data_closure_for_mode(mode, *, after_child=False):
    if mode == NORMAL_NATIVE_MODE:
        return DATA_FILES
    if mode == CANARY_NATIVE_MODE:
        return CANARY_DATA_FILES if after_child else ()
    raise ReaderError("NATIVE_MODE")


def _validate_host_process_completion(process):
    need(type(process) is dict and process.get("ExitCode") == 0
         and process.get("TerminationProven") is True
         and process.get("Stderr") == "", "PROCESS_COMPLETION")


def _validate_host_process_logs_gone(process):
    for field in ("ProcessRoot", "StdoutLog", "StderrLog"):
        value = process.get(field) if type(process) is dict else None
        need(type(value) is str and Path(value).is_absolute()
             and not os.path.lexists(value), "PROCESS_CLEANUP_" + field.upper())


def _verify_context_pins(context, host):
    need(type(context) is dict and type(host) is types.ModuleType,
         "HOST_CONTEXT")
    for path, expected, limit, code in (
        (WIRE_PATH, WIRE_SHA256, 102152, "WIRE_CONTRACT"),
        (DESCRIPTOR_DOC_PATH, DESCRIPTOR_DOC_SHA256, 256 * 1024,
         "DESCRIPTOR_CONTRACT"),
        (PLAN_PATH, PLAN_SHA256, 256 * 1024, "PLAN_CONTRACT"),
        (WIRE_SOURCE_PATH, WIRE_SHA256, 102152, "WIRE_SOURCE"),
    ):
        canonical = _host_call(host, "_canonical", str(path), code + "_PATH",
                               error_code=code + "_PATH")
        raw = _host_call(host, "_read", canonical, limit, code + "_READ",
                         error_code=code + "_READ")
        need(type(raw) is bytes and sha(raw) == expected, code + "_HASH")
        if path == WIRE_SOURCE_PATH:
            root_raw = _host_call(host, "_read", WIRE_PATH, limit,
                                  "WIRE_CONTRACT_READ",
                                  error_code="WIRE_CONTRACT_READ")
            need(raw == root_raw, "WIRE_SOURCE_IDENTITY")

    wire = context.get("wire")
    need(type(wire) is dict and wire.get("sha256") == WIRE_SHA256,
         "WIRE_CONTEXT_BINDING")
    descriptor = context.get("descriptor")
    descriptor_raw = context.get("descriptorRaw")
    descriptor_sha = context.get("descriptorSha256")
    need(type(descriptor) is dict
         and descriptor.get("schema") == DESCRIPTOR_SCHEMA
         and type(descriptor_raw) is bytes and is_sha(descriptor_sha)
         and sha(descriptor_raw) == descriptor_sha
         and context.get("descriptorPath") == str(DESCRIPTOR_PATH),
         "DESCRIPTOR_CONTEXT_BINDING")
    descriptor_file = _canonical_host_path(
        host, context["descriptorPath"], "DESCRIPTOR_PATH")
    descriptor_dir = _canonical_host_path(
        host, str(DESCRIPTOR_PATH.parent), "DESCRIPTOR_DIRECTORY")
    need(descriptor_file == DESCRIPTOR_PATH and descriptor_dir == DESCRIPTOR_PATH.parent,
         "DESCRIPTOR_LOCATION")
    _host_directory_names(host, descriptor_dir, ("descriptor.json",),
                          "DESCRIPTOR_DIRECTORY")
    disk_descriptor = _host_call(host, "_read", descriptor_file, 1024 * 1024,
                                 "DESCRIPTOR_FILE_READ",
                                 error_code="DESCRIPTOR_FILE_READ")
    need(disk_descriptor == descriptor_raw and sha(disk_descriptor) == descriptor_sha,
         "DESCRIPTOR_FILE_BINDING")

    manifest = context.get("manifest")
    class_inventory = context.get("classInventory")
    classes_root = context.get("classesRoot")
    need(type(manifest) is dict and type(manifest.get("build")) is dict
         and type(class_inventory) is dict and isinstance(classes_root, Path),
         "CLASS_INVENTORY_CONTEXT")
    build = manifest["build"]
    entries = class_inventory.get("entries")
    class_count = class_inventory.get("classCount")
    class_bytes = class_inventory.get("totalBytes")
    need(type(entries) is list and is_int(class_count, 1, 10000)
         and class_count == len(entries) and is_int(class_bytes, 1, 256 * 1024 * 1024)
         and build.get("classCount") == class_count
         and build.get("classBytes") == class_bytes,
         "CLASS_INVENTORY_BINDING")
    expected_classes = {
        "com/lushprojects/circuitjs1/client/CohortTerminalTelemetryStudy.class",
        "com/lushprojects/circuitjs1/client/CohortTerminalTelemetryInput.class",
    }
    class_paths = {row.get("path") for row in entries if type(row) is dict}
    need(expected_classes.issubset(class_paths), "STUDY_CLASS_INVENTORY")
    classes_path = _host_call(host, "_canonical", str(classes_root),
                              "CLASSES_ROOT_PATH", error_code="CLASSES_ROOT_PATH")
    need(classes_path == classes_root, "CLASSES_ROOT_BINDING")
    _host_call(host, "_lstat_directory", classes_path, "CLASSES_ROOT_TYPE",
               error_code="CLASSES_ROOT_TYPE")
    inventory_path = _canonical_host_path(
        host, build.get("classInventoryPath"), "CLASS_INVENTORY_PATH")
    need(inventory_path == classes_root.parent / "class-inventory.json",
         "CLASS_INVENTORY_LOCATION")
    inventory_raw = _host_call(host, "_read", inventory_path,
                               4 * 1024 * 1024, "CLASS_INVENTORY_READ",
                               error_code="CLASS_INVENTORY_READ")
    need(sha(inventory_raw) == build.get("classInventorySha256"),
         "CLASS_INVENTORY_HASH")
    inventory_disk = _host_call(host, "host_document", inventory_raw,
                                4 * 1024 * 1024,
                                error_code="CLASS_INVENTORY_PARSE")
    need(inventory_disk == class_inventory, "CLASS_INVENTORY_DISK_BINDING")


def _recheck_host_source_inputs(context, host):
    manifest = context.get("manifest")
    source_manifest = context.get("sourceManifest")
    source_path_value = context.get("sourceManifestPath")
    run_path_value = context.get("path")
    need(type(manifest) is dict and type(source_manifest) is dict
         and isinstance(source_path_value, Path) and isinstance(run_path_value, Path)
         and is_sha(context.get("sourceSha"))
         and is_sha(context.get("sourceManifestSha256")),
         "SOURCE_RECHECK_CONTEXT")
    build = manifest.get("build")
    need(type(build) is dict
         and build.get("sourceManifestPath") == str(source_path_value)
         and build.get("sourceManifestSha256") == context["sourceManifestSha256"],
         "SOURCE_RECHECK_BINDING")
    for path_value, expected_sha, expected_doc, label in (
            (str(run_path_value), context["sourceSha"], manifest, "RUN_MANIFEST"),
            (str(source_path_value), context["sourceManifestSha256"],
             source_manifest, "SOURCE_MANIFEST")):
        path = _canonical_host_path(host, path_value, label + "_PATH")
        _host_call(host, "_inside", path, ROOT, label + "_ROOT",
                   error_code=label + "_ROOT")
        raw = _host_call(host, "_read", path, MAX_HOST_SOURCE_MANIFEST_BYTES,
                         label + "_READ", error_code=label + "_READ")
        need(type(raw) is bytes and sha(raw) == expected_sha,
             label + "_HASH")
        parsed = _host_call(host, "host_document", raw,
                            MAX_HOST_SOURCE_MANIFEST_BYTES,
                            error_code=label + "_PARSE")
        need(parsed == expected_doc, label + "_CHANGED")


def _canonical_canary_line(doc):
    need(type(doc) is dict and tuple(doc) == CANARY_KEYS,
         "CANARY_FIELD_ORDER")
    try:
        return (json.dumps(doc, ensure_ascii=True, separators=(",", ":"),
                            allow_nan=False) + "\n").encode("ascii")
    except (TypeError, ValueError, UnicodeError):
        raise ReaderError("CANARY_CANONICAL_SERIALIZE") from None


def _write_canary_exclusive(path, raw):
    need(isinstance(path, Path) and path.is_absolute() and type(raw) is bytes
         and 0 < len(raw) <= MAX_HOST_OUTPUT_BYTES,
         "CANARY_OUTPUT_WRITE_INPUT")
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    if hasattr(os, "O_BINARY"):
        flags |= os.O_BINARY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(str(path), flags, 0o600)
    except FileExistsError:
        raise ReaderError("CANARY_OUTPUT_EXISTS") from None
    except OSError:
        raise ReaderError("CANARY_OUTPUT_CREATE") from None
    try:
        stream = os.fdopen(descriptor, "wb", closefd=True)
        descriptor = None
        with stream:
            view = memoryview(raw)
            offset = 0
            while offset < len(view):
                end = min(offset + COPY_CHUNK, len(view))
                written = stream.write(view[offset:end])
                need(written is not None and written > 0, "CANARY_OUTPUT_WRITE")
                offset += written
            stream.flush()
            os.fsync(stream.fileno())
    except ReaderError:
        raise
    except OSError:
        raise ReaderError("CANARY_OUTPUT_WRITE") from None
    finally:
        if descriptor is not None:
            os.close(descriptor)


def _canary_host_summary_line(summary):
    need(type(summary) is dict and tuple(summary) == CANARY_HOST_SUMMARY_KEYS,
         "CANARY_HOST_SUMMARY_SCHEMA")
    try:
        return (json.dumps(summary, ensure_ascii=True, separators=(",", ":"),
                            allow_nan=False) + "\n").encode("ascii")
    except (TypeError, ValueError, UnicodeError):
        raise ReaderError("CANARY_HOST_SUMMARY_SERIALIZE") from None


def _audit_host_call(context, parent_value, data_value, *, mode):
    """Authenticate one selected-host call and its mode-specific output closure."""
    need(mode in (NORMAL_NATIVE_MODE, CANARY_NATIVE_MODE), "NATIVE_MODE")
    host = context.get("hostReader") if type(context) is dict else None
    need(type(host) is types.ModuleType, "HOST_READER_CONTEXT")
    for name in ("_canonical", "_inside", "_lstat_directory", "_read",
                 "host_document", "_validate_owner_call"):
        need(callable(getattr(host, name, None)), "HOST_API_" + name.upper())
    if mode == NORMAL_NATIVE_MODE:
        need(callable(getattr(host, "strict_console_line", None)),
             "HOST_API_STRICT_CONSOLE_LINE")
    _verify_context_pins(context, host)

    parent_file = _canonical_host_path(host, parent_value, "PARENT_PATH")
    data_dir = _canonical_host_path(host, data_value, "DATA_PATH")
    need(parent_file.name == "parent.json", "PARENT_PATH_NAME")
    call_dir = parent_file.parent
    _host_call(host, "_inside", call_dir, ROOT, "CALL_DIRECTORY_ROOT",
               error_code="CALL_DIRECTORY_ROOT")
    _host_call(host, "_inside", data_dir, ROOT, "DATA_DIRECTORY_ROOT",
               error_code="DATA_DIRECTORY_ROOT")
    need(call_dir.parent == ROOT and data_dir.parent == ROOT
         and call_dir != data_dir, "OUTPUT_DIRECTORY_LOCATION")
    _host_directory_names(host, call_dir, CALL_FILES, "CALL_DIRECTORY")
    _host_directory_names(host, data_dir, _data_closure_for_mode(mode),
                          "DATA_DIRECTORY")

    java = context.get("java")
    process_module = context.get("processModule")
    need(type(java) is dict and type(java.get("path")) is str
         and process_module is not None, "SELECTED_JAVA_CONTEXT")
    java_path = _canonical_host_path(host, java["path"], "SELECTED_JAVA_PATH")
    try:
        java_info = java_path.lstat()
    except OSError:
        raise ReaderError("SELECTED_JAVA_STAT") from None
    need(stat.S_ISREG(java_info.st_mode) and not stat.S_ISLNK(java_info.st_mode)
         and not (getattr(java_info, "st_file_attributes", 0) & 0x400),
         "SELECTED_JAVA_FILE")

    args = context.get("manifest")
    build = args.get("build") if type(args) is dict else None
    manifest_sha = context.get("sourceManifestSha256")
    need(type(build) is dict and is_sha(manifest_sha)
         and is_sha(context.get("descriptorSha256")), "NATIVE_MANIFEST_CONTEXT")
    run_args = _expected_native_args(
        context, "00000000-0000-0000-0000-000000000000", data_dir, mode)
    # The request's run ID is producer-chosen and becomes the result identity.
    # Validate its shape before rebuilding the exact full argv.
    parent_raw = _host_call(host, "_read", parent_file, MAX_HOST_RECEIPT_BYTES,
                            "PARENT_READ", error_code="PARENT_READ")
    parent = _host_call(host, "host_document", parent_raw,
                        MAX_HOST_RECEIPT_BYTES, error_code="PARENT_PARSE")
    exact_keys(parent, ("schema", "elapsedMs", "process"), "PARENT_SCHEMA")
    process = parent.get("process")
    process_args = process.get("Arguments") if type(process) is dict else None
    need(type(process_args) is list and len(process_args) == len(run_args)
         and process_args[0:5] == run_args[0:5]
         and is_uuid(process_args[5]), "NATIVE_ARGUMENTS")
    expected_args = _expected_native_args(context, process_args[5], data_dir, mode)
    need(process_args == expected_args, "NATIVE_ARGUMENT_BINDING")
    parent_elapsed = parent.get("elapsedMs")
    need(parent.get("schema") == PARENT_SCHEMA
         and type(parent_elapsed) in (int, float)
         and 0 <= parent_elapsed <= NATIVE_LIMIT_MS
         and math.isfinite(parent_elapsed),
         "PARENT_TIMING")

    owner_path = call_dir / "owner.json"
    call_result_path = call_dir / "result.json"
    request_path = call_dir / "request.json"
    process_path = call_dir / "process.json"
    owner_raw = _host_call(host, "_read", owner_path, MAX_HOST_RECEIPT_BYTES,
                           "OWNER_READ", error_code="OWNER_READ")
    call_result_raw = _host_call(host, "_read", call_result_path,
                                 MAX_HOST_RECEIPT_BYTES, "CALL_RESULT_READ",
                                 error_code="CALL_RESULT_READ")
    request_raw = _host_call(host, "_read", request_path, MAX_HOST_RECEIPT_BYTES,
                             "REQUEST_READ", error_code="REQUEST_READ")
    process_raw = _host_call(host, "_read", process_path, MAX_HOST_RECEIPT_BYTES,
                             "PROCESS_READ", error_code="PROCESS_READ")
    request = _host_call(host, "host_document", request_raw,
                         MAX_HOST_RECEIPT_BYTES, error_code="REQUEST_PARSE")
    process_disk = _host_call(host, "host_document", process_raw,
                              MAX_HOST_RECEIPT_BYTES, error_code="PROCESS_PARSE")
    need(type(request) is dict and type(process_disk) is dict
         and process_disk == process and request.get("FilePath") == java["path"]
         and request.get("Arguments") == expected_args
         and process.get("FilePath") == java["path"]
         and process.get("Arguments") == expected_args
         and parent["process"] == process_disk,
         "REQUEST_PROCESS_BINDING")
    _validate_host_process_completion(process)
    need(type(process.get("Stdout")) is str, "PROCESS_STDOUT_TYPE")
    try:
        stdout_expected = process["Stdout"].encode("utf-8", errors="strict")
        stderr_expected = process["Stderr"].encode("utf-8", errors="strict")
    except UnicodeError:
        raise ReaderError("PROCESS_OUTPUT_UTF8") from None
    stdout_raw = _host_call(host, "_read", call_dir / "stdout.txt",
                            MAX_HOST_OUTPUT_BYTES, "STDOUT_READ",
                            error_code="STDOUT_READ")
    stderr_raw = _host_call(host, "_read", call_dir / "stderr.txt",
                            MAX_HOST_OUTPUT_BYTES, "STDERR_READ",
                            error_code="STDERR_READ")
    need(stdout_raw == stdout_expected and stderr_raw == b"" == stderr_expected,
         "PROCESS_OUTPUT_BINDING")
    _validate_host_process_logs_gone(process)

    _host_call(host, "_validate_owner_call", call_dir, parent_file, parent,
               request, process, java["path"], expected_args, NATIVE_LIMIT_MS,
               process_module, expected_stderr="", error_code="OWNER_CALL")

    output_rechecks = []
    if mode == NORMAL_NATIVE_MODE:
        op1_raw = _host_call(host, "_read", data_dir / "op-1.jsonl",
                             MAX_OPERATION1_BYTES, "OP1_READ", error_code="OP1_READ")
        need(0 < len(op1_raw) <= MAX_OPERATION1_BYTES, "OP1_BYTES")
        result_raw = _host_call(host, "_read", data_dir / "result.json",
                                MAX_JSON_LINE, "RESULT_READ", error_code="RESULT_READ")
        result_doc = strict_json_line(result_raw, "RESULT_FRAMING")
        exact_keys(result_doc, RESULT_KEYS, "RESULT_SCHEMA")
        marker_raw = stdout_raw
        marker = _host_call(host, "strict_console_line", marker_raw,
                            "MARKER_FRAMING", error_code="MARKER_PARSE")
        need(type(marker) is dict and is_uuid(process_args[5])
             and result_doc.get("schema") == RESULT_SCHEMA
             and result_doc.get("mode") == RESULT_MODE
             and result_doc.get("status") == "COMPLETED_DIAGNOSTIC_ONLY"
             and result_doc.get("runId") == process_args[5]
             and result_doc.get("sourceManifestSha256") == manifest_sha
             and result_doc.get("wireContractSha256") == WIRE_SHA256
             and result_doc.get("descriptorSha256") == context["descriptorSha256"],
             "RESULT_HOST_BINDING")
        _validate_marker(marker, result_raw, result_doc, context)
        output_rechecks.extend((
            (data_dir / "op-1.jsonl", op1_raw, MAX_OPERATION1_BYTES, "OP1_RECHECK"),
            (data_dir / "result.json", result_raw, MAX_JSON_LINE, "RESULT_RECHECK"),
        ))
    else:
        # The child must have left the owned destination untouched. Only this
        # host tier may materialize canary.json after semantic acceptance.
        _host_directory_names(host, data_dir, (), "CANARY_DATA_EMPTY_AFTER_RUN")
        canary_doc = parse_canary_json_line(stdout_raw)
        canonical_raw = _canonical_canary_line(canary_doc)
        need(stdout_raw == canonical_raw, "CANARY_CANONICAL_BYTES")
        need(canary_doc.get("runId") == process_args[5]
             and canary_doc.get("sourceManifestSha256") == process_args[6]
             and canary_doc.get("wireContractSha256") == process_args[7],
             "CANARY_PROCESS_BINDING")
        canary_semantics = validate_canary(canary_doc, context, process_args[5])
        canary_path = data_dir / "canary.json"
        _write_canary_exclusive(canary_path, stdout_raw)
        canary_readback = _host_call(host, "_read", canary_path,
                                     MAX_HOST_OUTPUT_BYTES, "CANARY_READBACK",
                                     error_code="CANARY_READBACK")
        need(canary_readback == stdout_raw, "CANARY_READBACK_BINDING")
        # Recheck frozen contract, descriptor, class inventory and the two
        # authenticated manifests after exclusive materialization. This is a
        # byte/input recheck only; it does not rerun builder or N validation.
        _verify_context_pins(context, host)
        _recheck_host_source_inputs(context, host)
        output_rechecks.append((canary_path, stdout_raw,
                                MAX_HOST_OUTPUT_BYTES, "CANARY_RECHECK"))

    # Re-read the consumed invocation receipts and mode-specific outputs after
    # the single owner-call audit so they cannot mix receipt generations.
    receipt_rechecks = (
        (owner_path, owner_raw, MAX_HOST_RECEIPT_BYTES, "OWNER_RECHECK"),
        (parent_file, parent_raw, MAX_HOST_RECEIPT_BYTES, "PARENT_RECHECK"),
        (request_path, request_raw, MAX_HOST_RECEIPT_BYTES, "REQUEST_RECHECK"),
        (process_path, process_raw, MAX_HOST_RECEIPT_BYTES, "PROCESS_RECHECK"),
        (call_result_path, call_result_raw, MAX_HOST_RECEIPT_BYTES,
         "CALL_RESULT_RECHECK"),
        (call_dir / "stdout.txt", stdout_raw, MAX_HOST_OUTPUT_BYTES, "STDOUT_RECHECK"),
        (call_dir / "stderr.txt", stderr_raw, MAX_HOST_OUTPUT_BYTES, "STDERR_RECHECK"),
    )
    for path, initial, limit, code in receipt_rechecks + tuple(output_rechecks):
        current = _host_call(host, "_read", path, limit, code,
                             error_code=code)
        need(current == initial, code + "_CHANGED")
    _host_directory_names(host, call_dir, CALL_FILES, "CALL_DIRECTORY_RECHECK")
    _host_directory_names(host, data_dir,
                          _data_closure_for_mode(mode, after_child=True),
                          "DATA_DIRECTORY_RECHECK")

    if mode == CANARY_NATIVE_MODE:
        summary = {
            "schema": CANARY_HOST_SUMMARY_SCHEMA,
            "status": "PASS_CANARY_HOST_AND_SEMANTICS_ONLY",
            "qualityStatus": canary_semantics["qualityStatus"],
            "runId": process_args[5],
            "sourceManifestSha256": process_args[6],
            "descriptorSha256": process_args[8],
            "wireContractSha256": process_args[7],
            "canarySha256": sha(stdout_raw),
            "canaryBytes": len(stdout_raw),
            "fullD": "NOT_RUN_NOT_ACCEPTED",
        }
        exact_keys(summary, CANARY_HOST_SUMMARY_KEYS, "CANARY_HOST_SUMMARY_SCHEMA")
        return {"status": "CANARY_HOST_AND_SEMANTICS_ONLY",
                "hostAudit": "PASS", "canaryRaw": stdout_raw,
                "canaryDocument": canary_doc, "canaryPath": canary_path,
                "canaryHostSummary": summary,
                "elapsedMs": parent_elapsed}

    return {
        "status": "HOST_PROVENANCE_AND_OUTPUT_BINDING_ONLY",
        "runId": process_args[5],
        "sourceManifestSha256": manifest_sha,
        "descriptorSha256": context["descriptorSha256"],
        "resultSha256": sha(result_raw),
        "operation1Sha256": sha(op1_raw),
        "elapsedMs": parent_elapsed,
        "hostAudit": "PASS",
        "resultDocument": result_doc,
        "operation1Raw": op1_raw,
        "resultRaw": result_raw,
        "markerRaw": marker_raw,
        "marker": marker,
    }


"""Normal-reader dispatcher members; source-only, no producer invocation.

Insert beside the existing validated reader owners. Required module globals:
need, validate_result, derive_original_group_reconciliation,
derive_export_copy_reconciliation, MAX_READER_NS, time, json and sys.
"""


def accept_normal_pipeline(context, host_result):
    """Return the existing semantic summary only after both derivations gate it."""
    need(type(host_result) is dict and all(name in host_result for name in (
        "resultDocument", "operation1Raw", "resultRaw", "marker", "markerRaw",
        "runId", "hostAudit",
    )), "NORMAL_PIPELINE_HOST_FIELDS")
    need(host_result["hostAudit"] == "PASS", "HOST_AUDIT_INCOMPLETE")
    need(type(context) is dict
         and type(context.get("nContext")) is dict
         and "p07Reader" in context["nContext"]
         and type(context.get("priorParsedOperation1")) is dict
         and "pads" in context["priorParsedOperation1"],
         "NORMAL_PIPELINE_CONTEXT_FIELDS")

    doc = host_result["resultDocument"]
    op1_raw = host_result["operation1Raw"]
    result_raw = host_result["resultRaw"]
    summary = validate_result(doc, context, host_result["runId"], op1_raw,
                              result_raw=result_raw)
    # Each existing helper must return normally. Its derived-only diagnostics
    # are not promoted to a new PASS/parity fact or merged into the wire summary.
    derive_original_group_reconciliation(
        doc, op1_raw, context["nContext"]["p07Reader"],
        context["priorParsedOperation1"]["pads"])
    derive_export_copy_reconciliation(
        doc, result_raw, host_result["marker"], host_result["markerRaw"])
    return summary


def finish_normal_cli_pipeline(context, host_result, started) -> int:
    """Normal main tail: retain the original reader clock and emit one summary."""
    summary = accept_normal_pipeline(context, host_result)
    need(time.monotonic_ns() - started <= MAX_READER_NS, "READER_DEADLINE")
    line = json.dumps(summary, ensure_ascii=True, allow_nan=False,
                      separators=(",", ":")) + "\n"
    need(time.monotonic_ns() - started <= MAX_READER_NS, "READER_DEADLINE")
    sys.stdout.write(line)
    sys.stdout.flush()
    # A late complete line is still a failed invocation: the enclosing existing
    # main reports ReaderError on stderr and returns nonzero, with no second line.
    need(time.monotonic_ns() - started <= MAX_READER_NS, "READER_DEADLINE")
    return 0


def main(argv=None) -> int:
    # No argument parsing, dependency loading, input reading or artifact writes.
    sys.stderr.write("GENERAL_OBSERVATION_COMPONENT_ONLY\n")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
