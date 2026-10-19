"""Strict offline reader for the bounded compactor cut-cause observation."""

import argparse
import hashlib
import json
import math
import os
import re
import stat
import sys
from pathlib import Path, PurePosixPath


HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
TASK_REL = "docs/task-evidence/PCB/compactor-cut-cause-r1"
PROTOCOL_REL = TASK_REL + "/protocol.json"
BASELINE_SHA = "12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816"
PROTOCOL_SHA = "62f7f85d4511130b9d60c0b12e4ac1a73a7c23064ad1ec40b1779083198c040d"
UNIT_SUITES_SHA = "f62a967c3b44f5ee42be87ea60a32f7852344bc32c20584e2170f23d548741b1"
ROUTES_SUITES_SHA = "7a62dd11beb86db19580bc9599302820d8f4dc5531b212c051b114d725592fd4"
BASELINE_SOURCE = "docs/task-evidence/PCB/post-selection-edge-trim-r1/final-native.txt"
BASELINE_SOURCE_SHA = "db538fe4cca9aff18e33a974a6afa53ecb2fc244c7827c65201cc5408b65330d"
MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_REPORTS = 100000
MAX_CALLS_PER_ROOT = 1024
MAX_CUTS_PER_CALL = 10000
MAX_BOARD_DIMENSION = 16000
MAX_COORDINATE = 100000000
MAX_CUT_AMOUNT = 16000
MAX_COUNT = 1000000
TIMING_SCOPE = "Native compactor cut observation; unchanged per-JVM/search/physical limits"
TERMINAL_TEMPLATE = "PASS: focused current contracts; {count} Java suites. Full matrix and independent oracles NOT RUN."
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."

ROOTS = ["-1017", "-1004", "-1022", "0", "17", "42", "1", "7", "5", "14", "2", "3", "4", "6"]
CASE_FIELDS = (
    "status", "seed", "version", "density", "parts", "pads", "area", "width", "height",
    "largestProtectedEmptyRectangleArea", "copperManhattanLength", "vias", "geometrySha256",
    "sourcePlanSha256", "liveBoardGraphSha256", "sourceIntentSha256", "layoutSeed", "routingSeed",
    "selectedAttempt", "rankedPlacementAttempts", "acceptedPlanEvaluations", "placementRejections",
    "oneFaceAttempts", "twoLayerAttempts", "routeAttempts", "routingOrders", "routingExpansions",
    "selectedQualityMilli", "statisticsCanonical", "fixedMarginFloor", "accessEnvelopeMargin",
    "requiredMargin", "legacyRequestedMargin", "requestedMargin", "actualMargin", "occupiedX",
    "occupiedY", "occupiedWidth", "occupiedHeight", "outlineX", "outlineY",
    "excludedWorkbenchChrome", "elapsedMillis",
)
EVENT_FIELDS = {
    "BEGIN": {"seed", "call", "candidate", "axis", "event", "beforeWidth", "beforeHeight", "traces", "holes"},
    "CUT": {"seed", "call", "candidate", "axis", "event", "index", "coordinate", "amount"},
    "END": {"seed", "call", "candidate", "axis", "event", "cutCount", "removed", "outcome", "reason", "afterWidth", "afterHeight"},
}
REPORT_PREFIXES = (
    "PCB_COMPACTOR_CUT_CASE ",
    "PCB_COMPACTOR_CUT_EVENT ",
    "PCB_WHITESPACE_CASE ",
)
FAILURE_LINE = re.compile(
    r"^\s*(?:FAIL(?:ED)?\b|ERROR\b|FATAL\b|Exception in thread\b|AssertionError\b|"
    r"BUILD FAILED\b|\[ERROR\]|CURRENT_CONTRACT_FAILURE:|TIMEOUT\b|TIMED OUT\b|"
    r"CURRENT_CONTRACT_TIMEOUT:|CANCELLED\b|SKIPPED\b|IGNORED\b)", re.IGNORECASE
)
SIGNED_INT = re.compile(r"(?:0|-[1-9][0-9]*|[1-9][0-9]*)\Z")
SIGNED_LONG_TEXT = re.compile(r"(?:0|-[1-9][0-9]*|[1-9][0-9]*)\Z")
HEX64 = re.compile(r"[0-9a-f]{64}\Z")


class ReaderError(ValueError):
    """Input failed one of the frozen reader gates."""


def _need(condition, message):
    if not condition:
        raise ReaderError(message)


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def _require_sha(data, expected, label):
    _need(type(expected) is str and HEX64.fullmatch(expected) is not None and
          _sha(data) == expected, label + " hash changed")


def _json_int(token):
    _need(SIGNED_INT.fullmatch(token) is not None and token != "-0",
          "JSON integer is not canonical")
    _need(len(token.lstrip("-")) <= 19, "JSON integer exceeds reader bound")
    return int(token)


def _json_float(token):
    value = float(token)
    _need(math.isfinite(value), "non-finite JSON number")
    return value


def _json_constant(_token):
    raise ReaderError("non-standard JSON number")


def _pairs_object(pairs):
    result = {}
    for key, value in pairs:
        _need(key not in result, "duplicate JSON object key")
        result[key] = value
    return result


def _parse_json(data, label):
    _need(type(data) is bytes and len(data) <= MAX_FILE_BYTES, label + " size/type invalid")
    try:
        return json.loads(data.decode("utf-8", errors="strict"), parse_int=_json_int,
                          parse_float=_json_float, parse_constant=_json_constant,
                          object_pairs_hook=_pairs_object)
    except (UnicodeDecodeError, json.JSONDecodeError, OverflowError) as exc:
        raise ReaderError(label + " is not strict UTF-8 JSON") from exc


def _typed_equal(left, right):
    if type(left) is not type(right):
        return False
    if type(left) is dict:
        return (tuple(left) == tuple(right) and
                all(_typed_equal(left[key], right[key]) for key in left))
    if type(left) is list:
        return len(left) == len(right) and all(_typed_equal(a, b) for a, b in zip(left, right))
    return left == right


def _is_reparse(path):
    try:
        info = path.lstat()
    except OSError as exc:
        raise ReaderError("sealed input is unavailable") from exc
    if stat.S_ISLNK(info.st_mode):
        return True
    # Windows reparse point attribute; getattr keeps the check portable for canaries.
    return bool(getattr(info, "st_file_attributes", 0) & 0x400)


def _repo_file(relative, max_bytes=MAX_FILE_BYTES):
    _need(type(relative) is str and "\\" not in relative, "repository path must be slash-relative")
    pure = PurePosixPath(relative)
    _need(not pure.is_absolute() and bool(pure.parts) and all(part not in ("", ".", "..") for part in pure.parts),
          "repository path is not contained")
    target = REPO.joinpath(*pure.parts)
    try:
        repo_real = REPO.resolve(strict=True)
        cursor = repo_real
        for part in pure.parts:
            cursor = cursor / part
            _need(not _is_reparse(cursor), "repository input crosses a reparse point")
        resolved = target.resolve(strict=True)
        _need(resolved == target.absolute() or resolved.is_relative_to(repo_real),
              "repository input escapes its root")
        info = target.stat()
        _need(stat.S_ISREG(info.st_mode) and info.st_size <= max_bytes,
              "repository input is not a bounded regular file")
        data = target.read_bytes()
    except ReaderError:
        raise
    except (OSError, RuntimeError, ValueError) as exc:
        raise ReaderError("sealed input is unavailable or unsafe") from exc
    _need(len(data) <= max_bytes, "repository input grew beyond its bound")
    return data


def _task_file(name, max_bytes=MAX_FILE_BYTES):
    _need(type(name) is str and name and "/" not in name and "\\" not in name,
          "task artifact name is invalid")
    return _repo_file(TASK_REL + "/" + name, max_bytes)


def _expected_protocol():
    roots = list(ROOTS)
    unit = [["PcbCompactorCutObserverContractTest", "PCB compactor cut observer contracts "]]
    routes = [["PcbCompactorCutCauseContractTest", "PCB compactor cut cause contracts "]]
    for group in range(2, 8):
        routes.append([
            "PcbCompactorCutCauseContractTestGroup" + str(group),
            "PCB compactor cut cause Group" + str(group) + " contracts ",
        ])
    return {
        "schema": 1,
        "baseCommit": "8b88311959d3faa8df2fefa4e31b0dcd9594ff8f",
        "roots": roots,
        "unitSuites": unit,
        "routesSuites": routes,
        "baselinePath": "docs/task-evidence/PCB/passive-local-pose-choice-r1/accepted15-baselines.json",
        "baselineSha256": BASELINE_SHA,
        "unitPassDetail": "assertions=N fixtures=3 evaluator=compactRouted",
        "routePassDetail": "roots=2 rows=2 evaluator=compactRouted diagnosticOnly=true",
        "casePrefix": "PCB_COMPACTOR_CUT_CASE ",
        "eventPrefix": "PCB_COMPACTOR_CUT_EVENT ",
        "outcomes": ["NOCUT", "ACCEPTED", "PLACEMENT_REJECTED", "QUALITY_REJECTED"],
        "placementReasons": [
            "MISSING_COMPONENT", "DUPLICATE_COMPONENT", "ENVELOPE_ACCESS_OR_DOMAIN",
            "STRANDED_ESCAPE", "DOMAIN_CORRIDOR", "ACCESS_GRID_BUDGET",
            "DISCONNECTED_ESCAPE_CHANNEL",
        ],
        "qualityReasons": ["BENDS", "DETOUR"],
        "scope": "Observational current15 only; all42 non-time fields must remain exact; no shrinkage claim",
    }


def _protocol_from_bytes(data, verify_digest=True):
    if verify_digest:
        _require_sha(data, PROTOCOL_SHA, "sealed protocol")
    doc = _parse_json(data, "protocol")
    expected = _expected_protocol()
    _need(type(doc) is dict and _typed_equal(doc, expected), "protocol content differs from frozen contract")
    return doc


def _read_baseline(protocol):
    data = _repo_file(protocol["baselinePath"])
    _require_sha(data, BASELINE_SHA, "accepted15 baseline")
    doc = _parse_json(data, "accepted15 baseline")
    expected_keys = ("status", "scope", "source", "sourceSha256", "rows", "reuseBoundary")
    _need(type(doc) is dict and tuple(doc) == expected_keys, "accepted15 baseline schema changed")
    _need(doc["status"] == "PINNED" and doc["scope"] == "All14 informed accepted15 regressions; not unseen",
          "accepted15 baseline identity changed")
    _need(doc["source"] == BASELINE_SOURCE and doc["sourceSha256"] == BASELINE_SOURCE_SHA,
          "accepted15 baseline provenance changed")
    rows = doc["rows"]
    _need(type(rows) is list and len(rows) == len(ROOTS), "accepted15 baseline row count changed")
    for seed, row in zip(ROOTS, rows):
        _need(type(row) is dict and tuple(row) == CASE_FIELDS, "accepted15 baseline row schema/order changed")
        _need(row["status"] == "PASS" and row["seed"] == seed and
              type(row["version"]) is int and row["version"] == 15 and
              type(row["density"]) is int and row["density"] == 14,
              "accepted15 baseline root/version identity changed")
        _need(type(row["parts"]) is int and 20 <= row["parts"] <= 128,
              "accepted15 baseline part count outside retained scope")
        _need(type(row["elapsedMillis"]) is int and 0 <= row["elapsedMillis"] <= 60000,
              "accepted15 baseline elapsed time outside declared bound")
    return data, doc, rows


def _load_contract():
    protocol = _protocol_from_bytes(_repo_file(PROTOCOL_REL, 256 * 1024))
    baseline_data, baseline_doc, baseline_rows = _read_baseline(protocol)
    return protocol, baseline_data, baseline_doc, baseline_rows


def _suite_manifest(phase, protocol):
    if phase == "unit":
        name, expected, digest = "unit-suites.json", protocol["unitSuites"], UNIT_SUITES_SHA
    else:
        name, expected, digest = "routes-suites.json", protocol["routesSuites"], ROUTES_SUITES_SHA
    raw = _task_file(name, 64 * 1024)
    _require_sha(raw, digest, "sealed suite manifest")
    actual = _parse_json(raw, "suite manifest")
    _need(_typed_equal(actual, expected), "suite manifest order/content changed")
    return actual, _sha(raw)


def _canonical_seed(value):
    _need(type(value) is str and SIGNED_LONG_TEXT.fullmatch(value) is not None,
          "seed is not canonical signed-long text")
    number = int(value)
    _need(-(1 << 63) <= number < (1 << 63) and str(number) == value,
          "seed is outside signed-long range")
    return value


def _parse_reports(text):
    _need(type(text) is str and len(text.encode("utf-8")) <= MAX_FILE_BYTES,
          "native phase text is missing or oversized")
    reports = []
    for line in text.splitlines():
        if not line.startswith("PCB_"):
            continue
        prefix = next((value for value in REPORT_PREFIXES if line.startswith(value)), None)
        _need(prefix is not None, "unknown native PCB report prefix")
        row = _parse_json(line[len(prefix):].encode("utf-8"), "native report")
        _need(type(row) is dict, "native report must be a JSON object")
        reports.append((prefix, row, line))
        _need(len(reports) <= MAX_REPORTS, "native report count exceeds reader bound")
    return reports


def _int_field(row, key, low, high):
    value = row.get(key)
    _need(type(value) is int and low <= value <= high, "event integer field outside bounds: " + key)
    return value


def _event_base(row, seed, active=None):
    _need(_canonical_seed(row.get("seed")) == seed, "event seed is missing, malformed, or foreign")
    call = _int_field(row, "call", 0, MAX_CALLS_PER_ROOT - 1)
    candidate = _int_field(row, "candidate", 0, 5)
    axis = _int_field(row, "axis", 0, 1)
    if active is not None:
        _need((call, candidate, axis) == (active["call"], active["candidate"], active["axis"]),
              "event does not belong to the open axis")
    return call, candidate, axis


def _validate_root_events(seed, events):
    _need(bool(events), "fixed root has no observed compactor events")
    active = None
    expected_call = 0
    axis0_end = None
    axis0_candidate = None
    calls = []
    proposed_removed = 0
    outcomes = {}
    reasons = {}
    for kind, row in events:
        _need(kind in EVENT_FIELDS and set(row) == EVENT_FIELDS[kind], "event key set changed")
        if kind == "BEGIN":
            _need(active is None, "nested or unclosed axis begin")
            call, candidate, axis = _event_base(row, seed)
            _need(call == expected_call and call % 2 == axis, "axis calls are missing, duplicated, or reordered")
            _need(expected_call < MAX_CALLS_PER_ROOT, "root call count exceeds bound")
            width = _int_field(row, "beforeWidth", 1, MAX_BOARD_DIMENSION)
            height = _int_field(row, "beforeHeight", 1, MAX_BOARD_DIMENSION)
            _int_field(row, "traces", 0, MAX_COUNT)
            _int_field(row, "holes", 0, MAX_COUNT)
            if axis == 0:
                axis0_end = None
                axis0_candidate = candidate
            else:
                _need(axis0_end is not None and candidate == axis0_candidate,
                      "paired axes use different candidates or lack axis zero")
                _need((width, height) == axis0_end, "axis one input differs from axis zero retained result")
            active = {"call": call, "candidate": candidate, "axis": axis,
                      "width": width, "height": height, "cuts": 0,
                      "removed": 0, "priorCoordinate": None}
        elif kind == "CUT":
            _need(active is not None, "cut appears outside an open axis")
            _event_base(row, seed, active)
            index = _int_field(row, "index", 0, MAX_CUTS_PER_CALL - 1)
            coordinate = _int_field(row, "coordinate", -MAX_COORDINATE, MAX_COORDINATE)
            amount = _int_field(row, "amount", 10, MAX_CUT_AMOUNT)
            _need(index == active["cuts"], "cut indexes are not contiguous")
            _need(amount % 10 == 0, "cut amount is outside the canonical 10-unit grid")
            previous = active["priorCoordinate"]
            _need(previous is None or coordinate > previous, "cut coordinates are not strictly increasing")
            active["cuts"] += 1
            active["removed"] += amount
            _need(active["removed"] <= MAX_CUT_AMOUNT * MAX_CUTS_PER_CALL,
                  "proposed cut sum exceeds reader bound")
            active["priorCoordinate"] = coordinate
        else:
            _need(active is not None, "axis end appears without begin")
            _event_base(row, seed, active)
            cut_count = _int_field(row, "cutCount", 0, MAX_CUTS_PER_CALL)
            removed = _int_field(row, "removed", 0, MAX_CUT_AMOUNT * MAX_CUTS_PER_CALL)
            after_width = _int_field(row, "afterWidth", 1, MAX_BOARD_DIMENSION)
            after_height = _int_field(row, "afterHeight", 1, MAX_BOARD_DIMENSION)
            outcome = row.get("outcome")
            reason = row.get("reason")
            _need(type(outcome) is str and outcome in ("NOCUT", "ACCEPTED", "PLACEMENT_REJECTED", "QUALITY_REJECTED"),
                  "outcome is outside the finite protocol")
            _need(type(reason) is str, "outcome reason must be text")
            _need(cut_count == active["cuts"] and removed == active["removed"],
                  "axis cut count or proposed sum differs from observed cuts")
            _need((outcome == "NOCUT") == (cut_count == 0), "NOCUT cut-count semantics changed")
            if outcome in ("NOCUT", "ACCEPTED"):
                _need(reason == "", "accepted/no-cut outcome carries a rejection reason")
            elif outcome == "PLACEMENT_REJECTED":
                _need(reason in _expected_protocol()["placementReasons"], "placement rejection reason is not finite")
            else:
                _need(reason in _expected_protocol()["qualityReasons"], "quality rejection reason is not finite")
            expected_width = active["width"] - (removed if outcome == "ACCEPTED" and active["axis"] == 0 else 0)
            expected_height = active["height"] - (removed if outcome == "ACCEPTED" and active["axis"] == 1 else 0)
            _need((after_width, after_height) == (expected_width, expected_height),
                  "axis dimensions do not reflect the accepted cut or retained input")
            if active["axis"] == 0:
                axis0_end = (after_width, after_height)
            else:
                _need(axis0_end is not None, "axis one has no completed axis-zero partner")
            record = {"call": active["call"], "candidate": active["candidate"],
                      "axis": active["axis"], "cutCount": cut_count,
                      "removed": removed, "outcome": outcome, "reason": reason}
            calls.append(record)
            proposed_removed += removed
            outcomes[outcome] = outcomes.get(outcome, 0) + 1
            if reason:
                reasons[reason] = reasons.get(reason, 0) + 1
            expected_call += 1
            active = None
    _need(active is None, "root ends with an open axis")
    _need(expected_call > 0 and expected_call % 2 == 0,
          "root has no complete pair or an unmatched axis")
    return {
        "seed": seed,
        "calls": expected_call,
        "pairs": expected_call // 2,
        "eventRecords": len(events),
        "outcomes": {key: outcomes[key] for key in ("NOCUT", "ACCEPTED", "PLACEMENT_REJECTED", "QUALITY_REJECTED") if key in outcomes},
        "reasons": {key: reasons[key] for key in sorted(reasons)},
        "proposedRemoved": proposed_removed,
    }


def _check_case_row(actual, expected, seed):
    _need(type(actual) is dict and tuple(actual) == CASE_FIELDS,
          "observed accepted15 case schema/order changed")
    _need(actual.get("seed") == seed, "observed accepted15 case root changed")
    elapsed = actual.get("elapsedMillis")
    _need(type(elapsed) is int and 0 <= elapsed <= 60000,
          "observed case elapsedMillis is not a bounded integer")
    _need(expected.get("seed") == seed and tuple(expected) == CASE_FIELDS,
          "sealed accepted15 case schema/root changed")
    actual_core = dict(actual)
    expected_core = dict(expected)
    actual_core.pop("elapsedMillis")
    expected_core.pop("elapsedMillis")
    _need(_typed_equal(actual_core, expected_core),
          "accepted15 non-time case fields differ from the sealed baseline")


def assess_events(text, baseline):
    """Validate ordered route reports and return a cause census, not a shrinkage claim."""
    _need(type(baseline) is list and len(baseline) == len(ROOTS), "sealed accepted15 rows required")
    reports = _parse_reports(text)
    roots_summary = []
    cursor = 0
    for seed, expected in zip(ROOTS, baseline):
        events = []
        while cursor < len(reports) and reports[cursor][0] == "PCB_COMPACTOR_CUT_EVENT ":
            row = reports[cursor][1]
            kind = row.get("event")
            _need(type(kind) is str and kind in EVENT_FIELDS, "unknown cut-event kind")
            _need(_canonical_seed(row.get("seed")) == seed,
                  "cut events are missing, duplicated, or reordered by fixed root")
            events.append((kind, row))
            cursor += 1
        roots_summary.append(_validate_root_events(seed, events))
        _need(cursor < len(reports) and reports[cursor][0] == "PCB_WHITESPACE_CASE ",
              "legacy whitespace report missing or reordered")
        whitespace = reports[cursor][1]
        _need(_canonical_seed(whitespace.get("seed")) == seed and
              type(whitespace.get("version")) is int and whitespace["version"] == 15,
              "legacy whitespace report root/version changed")
        cursor += 1
        _need(cursor < len(reports) and reports[cursor][0] == "PCB_COMPACTOR_CUT_CASE ",
              "accepted15 cut case report missing or reordered")
        _check_case_row(reports[cursor][1], expected, seed)
        cursor += 1
    _need(cursor == len(reports), "extra, hidden, or foreign native reports remain")
    total = sum(item["proposedRemoved"] for item in roots_summary)
    return {
        "status": "PASS",
        "roots": roots_summary,
        "caseRows": len(roots_summary),
        "eventCount": sum(item["eventRecords"] for item in roots_summary),
        "axisCalls": sum(item["calls"] for item in roots_summary),
        "proposedRemovedTotal": total,
        "interpretation": "Sum of proposed axis removals across outcomes; not accepted overall shrinkage.",
    }


def _suite_pairs(suites, phase, protocol):
    expected = protocol["unitSuites"] if phase == "unit" else protocol["routesSuites"]
    _need(type(suites) is list and _typed_equal(suites, expected),
          "phase suite order/content differs from the frozen protocol")
    return [(item[0], item[1]) for item in expected]


def _phase_summary(text, receipt, timing, suites, protocol, baseline_rows):
    phase = timing.get("phase") if type(timing) is dict else None
    _need(phase in ("unit", "routes"), "unknown native phase")
    pairs = _suite_pairs(suites, phase, protocol)
    suite_count = len(pairs)
    _need(type(timing) is dict and tuple(timing) == (
        "status", "exitCode", "elapsedSeconds", "suiteCount", "phase", "scope",
        "terminalCleanupIncluded", "cleanupDuration"), "native timing schema/order changed")
    elapsed = timing["elapsedSeconds"]
    _need(timing["status"] == "PASS" and type(timing["exitCode"]) is int and timing["exitCode"] == 0,
          "native phase status/exit failed")
    _need(type(timing["suiteCount"]) is int and timing["suiteCount"] == suite_count,
          "native suite count differs from phase contract")
    _need(type(elapsed) in (int, float) and math.isfinite(elapsed) and 0 < elapsed <= 36000,
          "native elapsed time is malformed or outside the bounded phase")
    _need(timing["phase"] == phase and timing["scope"] == TIMING_SCOPE and
          timing["terminalCleanupIncluded"] is True and
          timing["cleanupDuration"] == "NOT_SEPARATELY_MEASURED",
          "native phase scope or cleanup boundary changed")
    _need(type(text) is str and type(receipt) is str and
          len(text.encode("utf-8")) <= MAX_FILE_BYTES and len(receipt.encode("utf-8")) <= MAX_FILE_BYTES,
          "native phase log/receipt missing or oversized")
    text_lines, receipt_lines = text.splitlines(), receipt.splitlines()
    _need(not any(FAILURE_LINE.search(line) for line in text_lines + receipt_lines),
          "native phase contains failure, timeout, skip, or cancellation output")
    terminal = TERMINAL_TEMPLATE.format(count=suite_count)
    _need(text_lines.count(terminal) == 1 and text_lines.count(CLEANUP_LINE) == 1,
          "native terminal PASS or cleanup marker missing/duplicated")
    _need(terminal not in receipt_lines and CLEANUP_LINE not in receipt_lines,
          "child receipt includes outer terminal or cleanup data")
    _need(text_lines.index(terminal) < text_lines.index(CLEANUP_LINE),
          "terminal cleanup appears before phase PASS")
    suite_passes = [line for line in text_lines if line.startswith("PASS: ") and line != terminal]
    receipt_passes = [line for line in receipt_lines if line.startswith("PASS: ")]
    _need(suite_passes == receipt_passes and len(suite_passes) == suite_count,
          "suite PASS lines differ between terminal log and child receipt")
    for line, (name, marker) in zip(suite_passes, pairs):
        _need(line.startswith("PASS: " + marker), "suite PASS marker missing, duplicate, or reordered")
        if phase == "unit":
            match = re.fullmatch(re.escape("PASS: " + marker) +
                                 r"assertions=([1-9][0-9]*) fixtures=3 evaluator=compactRouted", line)
            _need(match is not None and int(match.group(1)) <= MAX_COUNT,
                  "unit PASS detail differs from frozen contract")
        else:
            _need(line == "PASS: " + marker + "roots=2 rows=2 evaluator=compactRouted diagnosticOnly=true",
                  "routes PASS detail differs from frozen contract")
    _need(all(text_lines.index(line) < text_lines.index(terminal) for line in suite_passes),
          "suite PASS appeared after terminal marker")
    text_reports = _parse_reports(text)
    receipt_reports = _parse_reports(receipt)
    _need([item[2] for item in text_reports] == [item[2] for item in receipt_reports],
          "native report lines differ between terminal log and child receipt")
    report_positions = [index for index, line in enumerate(text_lines) if line.startswith("PCB_")]
    _need(all(index < text_lines.index(terminal) for index in report_positions),
          "native report appeared after terminal marker")
    if phase == "unit":
        _need(not text_reports and not receipt_reports, "unit observer contract leaked corpus reports")
        census = None
    else:
        census = assess_events(text, baseline_rows)
    return {
        "status": "PASS",
        "phase": phase,
        "suiteCount": suite_count,
        "suiteClasses": [name for name, _ in pairs],
        "reportRows": len(text_reports),
        "orderedMarkers": [marker for _, marker in pairs],
        "census": census,
    }


def check_phase(text, receipt, timing, suites):
    """Validate maintained phase log, child receipt, timing, suite order, and route reports."""
    protocol, _baseline_data, _baseline_doc, baseline_rows = _load_contract()
    return _phase_summary(text, receipt, timing, suites, protocol, baseline_rows)


def _phase_inputs(phase):
    protocol, _baseline_data, _baseline_doc, baseline_rows = _load_contract()
    suites, manifest_sha = _suite_manifest(phase, protocol)
    suite_file = "unit-suites.json" if phase == "unit" else "routes-suites.json"
    names = (phase + ".txt", phase + "-receipt.txt", phase + "-timing.json", suite_file)
    raw = {name: _task_file(name) for name in names}
    text = raw[names[0]].decode("utf-8", errors="strict")
    receipt = raw[names[1]].decode("utf-8", errors="strict")
    timing = _parse_json(raw[names[2]], "native timing")
    summary = _phase_summary(text, receipt, timing, suites, protocol, baseline_rows)
    return summary, {name: _sha(data) for name, data in raw.items()}, manifest_sha


def _unit_validation(protocol, baseline_data):
    summary, inputs, manifest_sha = _phase_inputs("unit")
    return {
        "schema": 1,
        "status": "PASS",
        "phase": "unit",
        "scope": "Unit-only observer contract; no corpus route claim.",
        "protocolSha256": _sha(_repo_file(PROTOCOL_REL, 256 * 1024)),
        "baselineSha256": _sha(baseline_data),
        "suiteManifestSha256": manifest_sha,
        "phaseInputs": inputs,
        "phaseEvidence": summary,
        "nativeRuntimeEvidence": "PASS",
    }


def _routes_assessment(protocol, baseline_data):
    unit_expected = _unit_validation(protocol, baseline_data)
    prereq_data = _task_file("unit-validation.json", 1024 * 1024)
    prereq = _parse_json(prereq_data, "unit prerequisite")
    _need(_typed_equal(prereq, unit_expected),
          "routes phase lacks a fresh matching unit-validation PASS prerequisite")
    route_summary, route_inputs, manifest_sha = _phase_inputs("routes")
    return {
        "schema": 1,
        "status": "PASS",
        "scope": protocol["scope"],
        "protocolSha256": _sha(_repo_file(PROTOCOL_REL, 256 * 1024)),
        "baselineSha256": _sha(baseline_data),
        "unitPrerequisiteSha256": _sha(prereq_data),
        "routesSuiteManifestSha256": manifest_sha,
        "routePhaseInputs": route_inputs,
        "routePhaseEvidence": route_summary,
        "census": route_summary["census"],
        "nativeRuntimeEvidence": "PASS",
        "limits": [
            "Fourteen fixed accepted15 roots only; no unseen-root claim.",
            "All 42 non-time row fields match the sealed baseline; elapsedMillis is bounded separately.",
            "Proposed axis removals are counted across outcomes and do not establish accepted overall shrinkage.",
            "No alternative-cut, smaller-board, changed-policy, or broader runtime proof is made.",
        ],
    }


def _write_task_artifact(name, value):
    path = HERE / name
    _need(not path.exists(), "refusing to overwrite an existing task phase artifact")
    data = (json.dumps(value, ensure_ascii=True, indent=2, allow_nan=False) + "\n").encode("utf-8")
    _need(len(data) <= 4 * 1024 * 1024, "phase artifact exceeds output bound")
    try:
        path.write_bytes(data)
    except OSError as exc:
        raise ReaderError("phase artifact could not be written") from exc


def main(argv=None):
    parser = argparse.ArgumentParser(description="Validate the bounded offline compactor cut-cause evidence")
    parser.add_argument("phase", choices=("unit", "routes"))
    args = parser.parse_args(argv)
    try:
        protocol, baseline_data, _baseline_doc, _baseline_rows = _load_contract()
        if args.phase == "unit":
            result = _unit_validation(protocol, baseline_data)
            _write_task_artifact("unit-validation.json", result)
            print("PASS unit phase receipt; one suite; corpus evidence not evaluated")
        else:
            result = _routes_assessment(protocol, baseline_data)
            _write_task_artifact("assessment.json", result)
            print("PASS routes phase receipt; 14 accepted15 rows; proposed removals only")
        return 0
    except Exception as exc:
        # Reader errors intentionally avoid printing absolute paths or raw log contents.
        label = str(exc) if isinstance(exc, ReaderError) else "unexpected input or filesystem error"
        print("FAIL offline reader: " + label, file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
