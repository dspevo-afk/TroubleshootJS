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
    """Reimplemented from D's pure 33-field domain, without importing D."""
    keys(s, SNAPSHOT_KEYS, "SNAPSHOT_KEYS")
    net = c["requestedNetIds"][0]
    need(s["netId"] == net and c["traversedNetIds"] == [net], "SNAPSHOT_NET")
    start, root = s["startPadId"], s["rootPadId"]
    need(type(start) is str and type(root) is str and start != root
         and start in c["padsById"] and root in c["padsById"], "SNAPSHOT_PADS")
    for prefix, pad_id in (("start", start), ("root", root)):
        for suffix, axis in (("X", "x"), ("Y", "y")):
            coordinate = s[prefix + suffix]
            need(integer(coordinate, MIN_INT)
                 and coordinate == c["padsById"][pad_id][axis], "SNAPSHOT_COORDINATES")
    need(integer(s["pass"], 2, 2), "SNAPSHOT_PASS")
    need(integer(s["gridWidth"], 1, 250000) and integer(s["gridHeight"], 1, 250000)
         and s["gridWidth"] == c["gridWidth"]
         and s["gridHeight"] == c["gridHeight"], "SNAPSHOT_GRID")
    cells = c["gridWidth"] * c["gridHeight"]
    need(integer(s["goalsTop"], 0, cells) and integer(s["goalsBottom"], 0, cells)
         and s["goalsTop"] + s["goalsBottom"] > 0, "SNAPSHOT_GOALS")
    need(integer(s["prefixViaCount"], 0, 48) and integer(s["reachedPadCount"], 1, 2)
         and integer(s["initialManhattanDistance"]), "SNAPSHOT_COUNTS")
    branch, total = s["branchExpanded"], s["totalExpanded"]
    need(integer(branch, 0, 100000) and integer(total, branch, 1000000)
         and integer(s["expandedTop"], 0, branch)
         and integer(s["expandedBottom"], 0, branch)
         and s["expandedTop"] + s["expandedBottom"] == branch, "SNAPSHOT_EXPANSIONS")
    minimum = s["minimumExploredManhattanDistance"]
    need(integer(minimum, -1, -1) if branch == 0 else
         integer(minimum, 0, s["initialManhattanDistance"]), "SNAPSHOT_MINIMUM")
    need(integer(s["frontierCount"]) and integer(s["bestStateCount"]), "SNAPSHOT_STATES")
    reason = s["terminalReason"]
    need(type(reason) is str and reason in
         ("NO_PATH", "BRANCH_SEARCH_LIMIT", "TOTAL_SEARCH_LIMIT", "GRID_LIMIT", "FOUND"),
         "SNAPSHOT_REASON")
    if reason == "NO_PATH":
        need(s["frontierCount"] == 0, "SNAPSHOT_NO_PATH")
    elif reason == "BRANCH_SEARCH_LIMIT":
        need(branch == 100000, "SNAPSHOT_BRANCH_CAP")
    elif reason == "TOTAL_SEARCH_LIMIT":
        need(total == 1000000, "SNAPSHOT_TOTAL_CAP")
    elif reason == "FOUND":
        need(branch > 0, "SNAPSHOT_FOUND")
    need(all(type(s[name]) is bool for name in RELAXED_FLAGS), "RELAXED_FLAGS")
    need(all(integer(s[name]) for name in RELAXED_WORK), "RELAXED_WORK")
    started, complete, checked = (s[name] for name in RELAXED_FLAGS[:3])
    expected_checked = complete and s["prefixViaCount"] == 48
    need((not started or complete) and checked is expected_checked
         and all(s[name] is checked for name in RELAXED_FLAGS[3:]), "RELAXED_STATE")
    if not started:
        need(not complete and not checked and all(s[name] == 0 for name in RELAXED_WORK),
             "RELAXED_ZERO")


def _head(h, s):
    keys(h, HEAD_KEYS, "HEAD_KEYS")
    need(all(integer(h[name], MIN_INT) for name in HEAD_KEYS), "HEAD_INTS")
    cells = s["gridWidth"] * s["gridHeight"]
    need(h["branchOrdinal"] >= 1 and 0 <= h["cell"] < cells
         and 0 <= h["layer"] <= 1 and 0 <= h["direction"] <= 4
         and 0 <= h["transitions"] <= 48 - s["prefixViaCount"]
         and h["cost"] >= 0 and h["storedHeuristic"] >= 0 and h["serial"] >= 0,
         "HEAD_DOMAIN")
    expected_key = (((h["transitions"] * 2 + h["layer"]) * 5 + h["direction"])
                    * cells) + h["cell"]
    wrapped = ((h["cost"] + h["storedHeuristic"] + 2147483648) % 4294967296) - 2147483648
    need(h["key"] == expected_key and h["storedPriority"] == wrapped, "HEAD_EQUATIONS")
    need(s["branchExpanded"] == 100000 and s["totalExpanded"] < 1000000
         and s["bestStateCount"] >= 1, "HEAD_SNAPSHOT")


def _observation_accounting(o):
    """General pure V2 accounting; fixed packet fixtures impose tighter counts."""
    d = o["callbackCount"]
    need(integer(d, 0, 247), "CALLBACK_BOUND")
    h = int(o["selectedHead"] is not None)
    need(h <= d, "HEAD_CALLBACK_BOUND")
    v = o["validationCount"]
    need(integer(v, d, 94 * d + 12 * h), "VALIDATION_BOUND")
    entries, chunks, units = 33 * d + 10 * h, d + h, 69 * d + v + 21 * h
    need(integer(o["transientTupleCopies"], chunks, chunks)
         and integer(o["copyChunks"], chunks, chunks)
         and integer(o["transientFieldCopies"], entries, entries)
         and integer(o["copiedEntries"], entries, entries)
         and integer(o["observerUnits"], units, units)
         and units <= 163 * d + 33 * h, "OBSERVER_ACCOUNTING")
    return entries, chunks, units


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
