"""Strict offline reader for the COPY-only successful route pool observation."""

import argparse
import hashlib
import importlib.util
import json
import math
import re
import stat
import sys
from pathlib import Path, PurePosixPath


HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
TASK_REL = "docs/task-evidence/PCB/successful-route-pool-r1"
OLD_TASK_REL = "docs/task-evidence/PCB/global-pocket-descent-trial-r1"
OLD_ASSESS_REL = OLD_TASK_REL + "/assess.py"

PROTOCOL_SHA = "536fa0f92ff22f3c0c43505ee6b01a1bdf0c798161ec6051e9b2f858de4691f8"
OLD_ASSESS_SHA = "0a45548e3091f6bccbf34d6ceab9fe1be1cb5c802796ae7aaa5d63ddbdca5e86"
PUBLISHED_INPUTS = {
    "native-inputs.json": "f3fd5759e6796e204cbf147ca969fa674cd490e351d6be2bfe1ecf9a463553f4",
    "root-review.json": "c373edd3ae507af14a9a488518987cf3d2f9b840a23e497843f33dff7a862bb9",
    "routes.txt": "dcca3fd2f61eae74b770857c56ac87082a03e491fac83a147c46f9f9cfe773d7",
    "assess.py": OLD_ASSESS_SHA,
}
SUITE_MANIFEST_SHA = {
    "unit-suites.json": "06166a405f390342ec130cf14d352885ff4aa180ef357fdbde952550be80489f",
    "routes-suites.json": "fe298e4e97a4c947569fba6a0aabc78c5aae9da500b5275d2727b6b3f09dd3fa",
}
EXPECTED_UNIT_SUITES = [[
    "PcbSuccessfulRoutePoolObserverContractTest",
    "PCB successful route pool observer contracts ",
]]
EXPECTED_ROUTE_SUITES = [
    ["PcbSuccessfulRoutePoolSeed5ContractTest", "PCB successful route pool seed5 contracts "],
    ["PcbSuccessfulRoutePoolSeed2ContractTest", "PCB successful route pool seed2 contracts "],
]
ROOTS = ("5", "2")
VERSIONS = (15, 22)
EVENT_FIELDS = (
    "caseLabel", "routeIndex", "seed", "version", "strategy", "attempt", "phase",
    "placementCheapScore", "routeQualityMilli", "area", "majorSide", "preferCompact",
    "preferDensePacking", "rawOutline", "occupiedBounds", "copperManhattanLength", "vias",
    "traceCount", "geometrySha256", "componentGeometryFingerprint", "traceGeometryFingerprint",
    "sourceGraphBeforeSha256", "sourceGraphAfterSha256",
)
RECTANGLE_FIELDS = ("x", "y", "width", "height")
STRATEGIES = ("P05_ONE_FACE", "P07_FULLER_TWO_LAYER")
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
MAX_SIGNED_LONG = (1 << 63) - 1
MAX_SIGNED_MIN = -(1 << 63)


class ReaderError(ValueError):
    """An input failed one of the frozen offline-reader gates."""


def _need(condition, message):
    if not condition:
        raise ReaderError(message)


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def _is_reparse(path):
    try:
        info = path.lstat()
    except OSError as exc:
        raise ReaderError("sealed input is unavailable") from exc
    if stat.S_ISLNK(info.st_mode):
        return True
    return bool(getattr(info, "st_file_attributes", 0) & 0x400)


def _repo_file(relative, max_bytes=64 * 1024 * 1024):
    _need(type(relative) is str and "\\" not in relative,
          "repository path must be slash-relative")
    pure = PurePosixPath(relative)
    _need(not pure.is_absolute() and bool(pure.parts) and
          all(part not in ("", ".", "..") for part in pure.parts),
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


def _task_file(name, max_bytes=64 * 1024 * 1024):
    _need(type(name) is str and name and "/" not in name and "\\" not in name,
          "task artifact name is invalid")
    return _repo_file(TASK_REL + "/" + name, max_bytes)


def _load_old_reader():
    source = _repo_file(OLD_ASSESS_REL)
    _need(_sha(source) == OLD_ASSESS_SHA, "pinned prior reader hash changed")
    path = REPO.joinpath(*PurePosixPath(OLD_ASSESS_REL).parts)
    spec = importlib.util.spec_from_file_location("pinned_global_pocket_assess", path)
    _need(spec is not None and spec.loader is not None, "pinned prior reader cannot be loaded")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    try:
        spec.loader.exec_module(module)
    except Exception as exc:
        raise ReaderError("pinned prior reader failed to load") from exc
    _need(module._sha(source) == OLD_ASSESS_SHA, "loaded prior reader differs from its pin")
    module.ReaderError = ReaderError
    return module


def _expected_protocol():
    return {
        "schema": 1,
        "baseCommit": "27521289793b21ddfe636d0f6975e962797ebab9",
        "scope": "COPY-only read-only finalized successful route pool; no policy/fitness exception or promotion",
        "roots": ["5", "2"],
        "versions": [15, 22],
        "phase": "FINALIZED_ROUTE_CANDIDATE",
        "unitFixtureCount": 4,
        "unitEvaluator": "finalizedRoutePool",
        "unitPassPattern": "assertions=POSITIVE_INT fixtures=4 evaluator=finalizedRoutePool",
        "routePassDetail": "roots=1 pairs=1 rows=2 evaluator=finalizedRoutePool",
        "casePrefix": "PCB_GLOBAL_POCKET_CASE ",
        "poolPrefix": "PCB_SUCCESSFUL_ROUTE_POOL ",
        "whitespacePrefix": "PCB_WHITESPACE_CASE ",
        "reportOrder": "WHITESPACE then all POOL events then CASE per seed/version; PASS after pair",
        "eventFields": list(EVENT_FIELDS),
        "rectangleFields": list(RECTANGLE_FIELDS),
        "timingScope": "Native successful route pool observation; unchanged per-JVM/search/physical limits",
        "maxPhaseElapsedSeconds": 36000,
        "maxCaseMillis": 60000,
        "maxFileBytes": 67108864,
        "publishedInputs": PUBLISHED_INPUTS,
        "suiteManifestSha256": SUITE_MANIFEST_SHA,
        "metricDefinitions": {
            "area": "Finalized route candidate outline width*height before selected-only edge trim",
            "copperManhattanLength": "Sum abs(dx)+abs(dy) of every trace path segment, overlaps counted; same helper as selected receipts",
            "vias": "PcbBoardHole.Kind.VIA count",
            "routeQualityMilli": "Math.round(existing computed route quality*1000) at finalized candidate comparison",
            "geometrySha256": "PlayerSessionFingerprint.of(layout.geometryFingerprint()) at callbacktime",
        },
        "limits": [
            "Two informed roots only; no unseen/allseed/performance/solver/manufacturing claim",
            "Phase is before selected-only edge trim; compare winning copper/vias to final case but do not equate area/quality",
            "Published22 remains rejected, no production GWT/player change",
        ],
    }


def _contract(protocol_override=None, source_overrides=None):
    old = _load_old_reader()
    protocol_data = (_task_file("protocol.json", 256 * 1024)
                     if protocol_override is None else protocol_override)
    old._require_sha(protocol_data, PROTOCOL_SHA, "sealed successful route pool protocol")
    protocol = old._parse_json(protocol_data, "successful route pool protocol")
    _need(type(protocol) is dict and old._typed_equal(protocol, _expected_protocol()),
          "successful route pool protocol content/order differs from frozen contract")
    source_blobs = _verify_published_inputs(protocol, old, source_overrides)
    selected, whitespace = _published_selected_rows(source_blobs["routes.txt"], old, protocol)
    return old, protocol, protocol_data, source_blobs, selected, whitespace


def _verify_published_inputs(protocol, old, overrides=None):
    _need(old._typed_equal(protocol["publishedInputs"], PUBLISHED_INPUTS),
          "published input pin map differs from frozen protocol")
    overrides = {} if overrides is None else overrides
    _need(type(overrides) is dict and all(type(key) is str and type(value) is bytes
                                         for key, value in overrides.items()),
          "published input canary overrides are malformed")
    blobs = {}
    for name, digest in PUBLISHED_INPUTS.items():
        data = overrides[name] if name in overrides else _repo_file(OLD_TASK_REL + "/" + name)
        old._require_sha(data, digest, "published " + name)
        blobs[name] = data
    return blobs


def _published_selected_rows(routes_data, old, protocol):
    reports = old._parse_reports(routes_data.decode("utf-8", errors="strict"))
    selected = {}
    whitespace = {}
    case_prefix = old.REPORT_PREFIXES[1]
    whitespace_prefix = old.REPORT_PREFIXES[0]
    for prefix, row, _line in reports:
        if row.get("seed") not in ROOTS or row.get("version") not in VERSIONS:
            continue
        seed, version = row["seed"], row["version"]
        key = (seed, version)
        if prefix == case_prefix:
            _need(key not in selected, "published selected-row identity is duplicated")
            density = 14 if version == 15 else 15
            old._validate_case_shape(row, seed, version, density)
            selected[key] = row
        elif prefix == whitespace_prefix:
            _need(key not in whitespace, "published whitespace-row identity is duplicated")
            whitespace[key] = row
    expected_keys = [(seed, version) for seed in ROOTS for version in VERSIONS]
    _need(tuple(selected) == tuple(expected_keys),
          "published selected rows do not contain exact root/version order")
    _need(tuple(whitespace) == tuple(expected_keys),
          "published whitespace rows do not contain exact root/version order")
    for key in expected_keys:
        seed, version = key
        old._whitespace_case(whitespace[key], selected[key], seed, version)
    return selected, whitespace


def _suite_manifest(phase, old, protocol, raw_override=None):
    _need(phase in ("unit", "routes"), "unknown native phase")
    name = "unit-suites.json" if phase == "unit" else "routes-suites.json"
    expected = EXPECTED_UNIT_SUITES if phase == "unit" else EXPECTED_ROUTE_SUITES
    raw = _task_file(name, 64 * 1024) if raw_override is None else raw_override
    old._require_sha(raw, SUITE_MANIFEST_SHA[name], "sealed " + phase + " suite manifest")
    actual = old._parse_json(raw, phase + " suite manifest")
    _need(old._typed_equal(actual, expected), phase + " suite manifest order/content changed")
    _need(old._typed_equal(protocol["suiteManifestSha256"], SUITE_MANIFEST_SHA),
          "suite manifest pin map differs from protocol")
    return actual, _sha(raw)


def _parse_reports(text, protocol, old):
    _need(type(text) is str, "native phase text is missing or oversized")
    try:
        text_size = len(text.encode("utf-8", errors="strict"))
    except UnicodeEncodeError as exc:
        raise ReaderError("native phase text is not strict UTF-8") from exc
    _need(text_size <= protocol["maxFileBytes"], "native phase text is missing or oversized")
    prefixes = (protocol["whitespacePrefix"], protocol["poolPrefix"], protocol["casePrefix"])
    reports = []
    for line in text.splitlines():
        if not line.startswith("PCB_"):
            continue
        prefix = next((candidate for candidate in prefixes if line.startswith(candidate)), None)
        _need(prefix is not None, "unknown native PCB report prefix")
        try:
            encoded = line[len(prefix):].encode("utf-8", errors="strict")
        except UnicodeEncodeError as exc:
            raise ReaderError("native report is not strict UTF-8 text") from exc
        row = old._parse_json(encoded, "native report")
        _need(type(row) is dict, "native report must be a JSON object")
        reports.append((prefix, row, line))
        _need(len(reports) <= 100000, "native report count exceeds reader bound")
    return reports


def _signed_int(value, label):
    _need(type(value) is int and MAX_SIGNED_MIN <= value <= MAX_SIGNED_LONG,
          label + " must be a bounded signed integer")
    return value


def _nonnegative_int(value, label, positive=False):
    low = 1 if positive else 0
    _need(type(value) is int and low <= value <= MAX_SIGNED_LONG,
          label + " must be a bounded " + ("positive" if positive else "nonnegative") + " integer")
    return value


def _rectangle_shape(rectangle, protocol, label):
    _need(type(rectangle) is dict and tuple(rectangle) == tuple(protocol["rectangleFields"]),
          label + " rectangle schema/order changed")
    for key in ("x", "y"):
        _signed_int(rectangle[key], label + " rectangle " + key)
    for key in ("width", "height"):
        _nonnegative_int(rectangle[key], label + " rectangle " + key, positive=True)
    return rectangle


def _successful_route_pairs(case, old):
    text = case["statisticsCanonical"]
    _need(type(text) is str and 0 < len(text.encode("utf-8")) <= 1024 * 1024,
          "selected route statistics are malformed")
    selected_matches = re.findall(r"(?:^|;)selected=(P05_ONE_FACE|P07_FULLER_TWO_LAYER)(?=;|$)", text)
    _need(len(selected_matches) == 1, "selected route strategy is missing or duplicated")
    selected_strategy = selected_matches[0]
    outcome_matches = list(re.finditer(r";routeOutcomes=\[(.*)\]\Z", text))
    _need(len(outcome_matches) == 1, "canonical route outcomes are missing or duplicated")
    raw_outcomes = outcome_matches[0].group(1)
    tokens = raw_outcomes.split(", ") if raw_outcomes else []
    _need(bool(tokens), "canonical route outcomes are empty")
    outcome_pattern = re.compile(
        r"(P05_ONE_FACE|P07_FULLER_TWO_LAYER)@([0-5])=(SUCCESS|REJECTED:(.+))\Z"
    )
    seen = set()
    successes = []
    counts = {strategy: 0 for strategy in STRATEGIES}
    for token in tokens:
        match = outcome_pattern.fullmatch(token)
        _need(match is not None, "canonical route outcome is malformed")
        strategy, attempt_text, status = match.group(1), match.group(2), match.group(3)
        attempt = int(attempt_text)
        pair = (strategy, attempt)
        _need(pair not in seen, "canonical route outcome contains a duplicate attempt")
        seen.add(pair)
        counts[strategy] += 1
        if status == "SUCCESS":
            successes.append(pair)
        else:
            _need(match.group(4) is not None and bool(match.group(4)),
                  "rejected route outcome has no reason")
    _need(type(case["routeAttempts"]) is int and case["routeAttempts"] == len(tokens),
          "route outcome count differs from selected route attempts")
    _need(type(case["oneFaceAttempts"]) is int and case["oneFaceAttempts"] == counts["P05_ONE_FACE"] and
          type(case["twoLayerAttempts"]) is int and case["twoLayerAttempts"] == counts["P07_FULLER_TWO_LAYER"],
          "route outcomes differ from per-strategy attempt counts")
    selected_attempt = case["selectedAttempt"]
    _need(type(selected_attempt) is int and 0 <= selected_attempt <= 5 and
          (selected_strategy, selected_attempt) in successes,
          "selected attempt is absent from its strategy's successful route outcomes")
    return successes, selected_strategy, selected_attempt


def _validate_event(event, seed, version, route_index, expected_pair, selected_case,
                    score_records, selected_strategy, selected_attempt, protocol, old):
    _need(type(event) is dict and tuple(event) == tuple(protocol["eventFields"]),
          "successful route pool event schema/order changed")
    _need(event["caseLabel"] == "seed" + seed and type(event["caseLabel"]) is str,
          "successful route pool case label changed")
    _need(type(event["routeIndex"]) is int and event["routeIndex"] == route_index,
          "successful route pool index is missing, duplicated, or out of order")
    _need(old._canonical_seed(event["seed"]) == seed and
          type(event["version"]) is int and event["version"] == version,
          "successful route pool root/version changed")
    _need(event["strategy"] in STRATEGIES and type(event["strategy"]) is str and
          type(event["attempt"]) is int and 0 <= event["attempt"] <= 5 and
          (event["strategy"], event["attempt"]) == expected_pair,
          "successful route pool entry is not the next successful route outcome")
    _need(event["phase"] == protocol["phase"] and type(event["phase"]) is str,
          "successful route pool phase changed")
    for field in ("placementCheapScore", "routeQualityMilli", "area", "majorSide",
                  "copperManhattanLength", "vias", "traceCount"):
        _nonnegative_int(event[field], "successful route pool " + field,
                         positive=field in ("area", "majorSide"))
    _need(event["placementCheapScore"] == score_records[event["attempt"]]["total"],
          "successful route pool cheap score differs from its pinned placement score")
    raw_outline = _rectangle_shape(event["rawOutline"], protocol, "raw outline")
    _rectangle_shape(event["occupiedBounds"], protocol, "occupied bounds")
    _need(event["area"] == raw_outline["width"] * raw_outline["height"],
          "successful route pool area differs from raw outline dimensions")
    _need(event["majorSide"] == max(raw_outline["width"], raw_outline["height"]),
          "successful route pool major side differs from raw outline")
    _need(type(event["preferCompact"]) is bool and event["preferCompact"] is True and
          type(event["preferDensePacking"]) is bool and event["preferDensePacking"] is False,
          "successful route pool density interpretation flags changed")
    for field in ("geometrySha256", "sourceGraphBeforeSha256", "sourceGraphAfterSha256"):
        _need(type(event[field]) is str and HEX64.fullmatch(event[field]) is not None,
              "successful route pool digest is malformed: " + field)
    _need(event["sourceGraphBeforeSha256"] == event["sourceGraphAfterSha256"] ==
          selected_case["liveBoardGraphSha256"],
          "successful route pool source graph changed or differs from selected live graph")
    for field in ("componentGeometryFingerprint", "traceGeometryFingerprint"):
        value = event[field]
        _need(type(value) is str and bool(value) and
              0 < len(value.encode("utf-8", errors="strict")) <= 1024 * 1024,
              "successful route pool immutable fingerprint is empty or oversized: " + field)
    if expected_pair == (selected_strategy, selected_attempt):
        _need(event["copperManhattanLength"] == selected_case["copperManhattanLength"] and
              event["vias"] == selected_case["vias"],
              "winning pool copper/vias differ from final selected case")


def _assess_routes(reports, selected_rows, whitespace_rows, protocol, old):
    expected_keys = [(seed, version) for seed in ROOTS for version in VERSIONS]
    cursor = 0
    case_rows = 0
    whitespace_count = 0
    candidate_count = 0
    cases = []
    for seed, version in expected_keys:
        _need(cursor < len(reports), "required whitespace report is missing")
        whitespace_prefix, whitespace, _line = reports[cursor]
        cursor += 1
        _need(whitespace_prefix == protocol["whitespacePrefix"],
              "each case must begin with its whitespace report")
        expected_case = selected_rows[(seed, version)]
        expected_whitespace = whitespace_rows[(seed, version)]
        old._whitespace_case(whitespace, expected_case, seed, version)
        _need(old._typed_equal(whitespace, expected_whitespace),
              "whitespace report differs from its pinned prior row")

        successes, selected_strategy, selected_attempt = _successful_route_pairs(expected_case, old)
        score_records = old._placement_score_records(expected_case)
        pool = []
        for route_index, expected_pair in enumerate(successes):
            _need(cursor < len(reports) and reports[cursor][0] == protocol["poolPrefix"],
                  "successful route pool event is missing")
            event = reports[cursor][1]
            cursor += 1
            _validate_event(event, seed, version, route_index, expected_pair, expected_case,
                            score_records, selected_strategy, selected_attempt, protocol, old)
            pool.append(event)
        graph_snapshots = {(item["sourceGraphBeforeSha256"], item["sourceGraphAfterSha256"])
                           for item in pool}
        _need(len(graph_snapshots) == 1,
              "successful route pool events do not share the helper's source-graph snapshots")
        _need(cursor < len(reports), "selected case report is missing after route pool")
        case_prefix, actual_case, _case_line = reports[cursor]
        cursor += 1
        _need(case_prefix == protocol["casePrefix"],
              "selected case must follow all successful route pool events")
        density = 14 if version == 15 else 15
        old._validate_case_shape(actual_case, seed, version, density)
        _need(tuple(actual_case) == tuple(expected_case),
              "selected case schema/order differs from pinned prior row")
        actual_core, expected_core = dict(actual_case), dict(expected_case)
        actual_core.pop("elapsedMillis")
        expected_core.pop("elapsedMillis")
        _need(old._typed_equal(actual_core, expected_core),
              "selected case non-time fields differ from pinned published route row")
        _need(actual_case["elapsedMillis"] <= protocol["maxCaseMillis"],
              "selected case elapsed time exceeds unchanged case bound")
        selected_event = next(item for item in pool
                              if item["strategy"] == selected_strategy and
                              item["attempt"] == selected_attempt)
        cases.append({
            "caseLabel": "seed" + seed,
            "seed": seed,
            "version": version,
            "selectedStrategy": selected_strategy,
            "selectedAttempt": selected_attempt,
            "selectedPoolCopperManhattanLength": selected_event["copperManhattanLength"],
            "selectedPoolVias": selected_event["vias"],
            "successfulRoutePool": pool,
        })
        candidate_count += len(pool)
        case_rows += 1
        whitespace_count += 1
    _need(cursor == len(reports), "extra or foreign native route pool reports remain")
    return {
        "status": "PASS",
        "scope": "Four informed selected rows; finalized route pool observation only",
        "roots": 2,
        "pairs": 2,
        "rows": 4,
        "selectedCaseRows": case_rows,
        "whitespaceRows": whitespace_count,
        "successfulPoolCandidates": candidate_count,
        "cases": cases,
        "selectedRowsExactAgainstPublished22Packet": True,
        "strictFitnessDisposition": "NOT_EVALUATED; published22 remains rejected",
    }


def _unit_pass_line(line, marker, protocol):
    prefix = "PASS: " + marker
    pattern = (re.escape(prefix) + r"assertions=([1-9][0-9]*) fixtures=" +
               str(protocol["unitFixtureCount"]) + r" evaluator=" +
               re.escape(protocol["unitEvaluator"]))
    match = re.fullmatch(pattern, line)
    _need(match is not None, "unit PASS marker/count/evaluator is malformed")


def _phase_summary(text, receipt, timing, suites, phase, protocol, selected_rows,
                   whitespace_rows, old):
    _need(type(timing) is dict and tuple(timing) == tuple(old.TIMING_FIELDS),
          "native timing schema/order changed")
    _need(phase in ("unit", "routes") and timing["phase"] == phase,
          "native phase differs from requested phase")
    expected_suites = EXPECTED_UNIT_SUITES if phase == "unit" else EXPECTED_ROUTE_SUITES
    _need(old._typed_equal(suites, expected_suites), "native suite rows differ from frozen order")
    suite_count = len(expected_suites)
    elapsed = timing["elapsedSeconds"]
    _need(timing["status"] == "PASS" and type(timing["exitCode"]) is int and timing["exitCode"] == 0,
          "native phase status/exit failed")
    _need(type(timing["suiteCount"]) is int and timing["suiteCount"] == suite_count,
          "native suite count differs from phase contract")
    _need(type(elapsed) in (int, float) and math.isfinite(elapsed) and
          0 <= elapsed <= protocol["maxPhaseElapsedSeconds"],
          "native elapsed time is malformed or outside unchanged bound")
    _need(timing["scope"] == protocol["timingScope"] and
          timing["terminalCleanupIncluded"] is True and
          timing["cleanupDuration"] == "NOT_SEPARATELY_MEASURED",
          "native phase scope or cleanup boundary changed")
    _need(type(text) is str and type(receipt) is str,
          "native phase log/receipt missing or oversized")
    try:
        text_lines, receipt_lines = text.splitlines(), receipt.splitlines()
        text_size = len(text.encode("utf-8", errors="strict"))
        receipt_size = len(receipt.encode("utf-8", errors="strict"))
    except UnicodeEncodeError as exc:
        raise ReaderError("native phase log/receipt is not strict UTF-8") from exc
    _need(text_size <= protocol["maxFileBytes"] and receipt_size <= protocol["maxFileBytes"],
          "native phase log/receipt missing or oversized")
    _need(not any(old.FAILURE_LINE.search(line) or old.SKIP_OR_FAILURE_WORD.search(line)
                  for line in text_lines + receipt_lines),
          "native phase contains failure, timeout, skip, or cancellation output")

    terminal = old.TERMINAL_TEMPLATE.format(count=suite_count)
    cleanup = old.CLEANUP_LINE
    _need(text_lines.count(terminal) == 1 and text_lines.count(cleanup) == 1,
          "native terminal PASS or cleanup marker missing/duplicated")
    _need(terminal not in receipt_lines and cleanup not in receipt_lines and
          text_lines.index(terminal) < text_lines.index(cleanup),
          "native child receipt or terminal cleanup boundary changed")
    suite_passes = [line for line in text_lines if line.startswith("PASS: ") and line != terminal]
    receipt_passes = [line for line in receipt_lines if line.startswith("PASS: ")]
    _need(suite_passes == receipt_passes and len(suite_passes) == suite_count,
          "suite PASS lines differ between terminal log and child receipt")
    for index, (line, suite) in enumerate(zip(suite_passes, expected_suites)):
        marker = suite[1]
        if phase == "unit":
            _unit_pass_line(line, marker, protocol)
        else:
            expected = "PASS: " + marker + protocol["routePassDetail"]
            _need(line == expected, "routes PASS detail/order differs from frozen contract")
        _need(text_lines.index(line) < text_lines.index(terminal),
              "suite PASS appeared after terminal marker")
        if phase == "routes":
            report_seed = ROOTS[index]
            earlier_suite_positions = [text_lines.index(pass_line) for pass_line in suite_passes[:index]]
            lower = max(earlier_suite_positions, default=-1)
            upper = text_lines.index(line)
            case_positions = [position for position, candidate in enumerate(text_lines)
                              if candidate.startswith(protocol["casePrefix"]) and lower < position < upper]
            _need(len(case_positions) == 2, "each routes suite must finish its two ordered cases")
            for position in case_positions:
                row = old._parse_json(text_lines[position][len(protocol["casePrefix"]):].encode("utf-8"),
                                      "selected case marker row")
                _need(row.get("seed") == report_seed,
                      "routes suite report block belongs to the wrong root")

    text_reports = _parse_reports(text, protocol, old)
    receipt_reports = _parse_reports(receipt, protocol, old)
    _need([item[2] for item in text_reports] == [item[2] for item in receipt_reports],
          "native report lines differ between terminal log and child receipt")
    terminal_position = text_lines.index(terminal)
    _need(all(position < terminal_position for position, line in enumerate(text_lines)
              if line.startswith("PCB_")),
          "native report appeared after terminal marker")
    if phase == "unit":
        _need(not text_reports and not receipt_reports,
              "unit contract leaked successful route pool reports")
        census = None
    else:
        census = _assess_routes(text_reports, selected_rows, whitespace_rows, protocol, old)
    return {
        "status": "PASS",
        "phase": phase,
        "suiteCount": suite_count,
        "suiteClasses": [row[0] for row in expected_suites],
        "reportRows": len(text_reports),
        "orderedMarkers": [row[1] for row in expected_suites],
        "census": census,
    }


def check_phase(text, receipt, timing, suites, phase):
    """Validate an in-memory native phase against frozen protocol and published evidence."""
    old, protocol, _protocol_data, _source_blobs, selected, whitespace = _contract()
    actual_suites, _manifest_sha = _suite_manifest(phase, old, protocol)
    _need(old._typed_equal(suites, actual_suites), "caller suite manifest differs from pinned bytes")
    return _phase_summary(text, receipt, timing, suites, phase, protocol, selected, whitespace, old)


def _read_phase_files(phase, protocol, old, selected, whitespace):
    _need(phase in ("unit", "routes"), "unknown native phase")
    names = (phase + ".txt", phase + "-receipt.txt", phase + "-timing.json")
    raw = {name: _task_file(name, protocol["maxFileBytes"]) for name in names}
    try:
        text = raw[names[0]].decode("utf-8", errors="strict")
        receipt = raw[names[1]].decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ReaderError("native phase log/receipt is not strict UTF-8") from exc
    timing = old._parse_json(raw[names[2]], "native phase timing")
    suites, manifest_sha = _suite_manifest(phase, old, protocol)
    summary = _phase_summary(text, receipt, timing, suites, phase, protocol,
                             selected, whitespace, old)
    return summary, {name: _sha(data) for name, data in raw.items()}, manifest_sha


def _unit_validation(contract, summary, phase_inputs, manifest_sha):
    old, protocol, protocol_data, source_blobs, _selected, _whitespace = contract
    return {
        "schema": 1,
        "status": "PASS",
        "phase": "unit",
        "scope": "Finalized route pool observer contract only; paired routes and fitness are not evaluated.",
        "baseCommit": protocol["baseCommit"],
        "protocolSha256": _sha(protocol_data),
        "priorReaderSha256": OLD_ASSESS_SHA,
        "publishedInputsSha256": {name: _sha(data) for name, data in source_blobs.items()},
        "suiteManifestSha256": manifest_sha,
        "phaseInputsSha256": phase_inputs,
        "phaseEvidence": summary,
        "nativeRuntimeEvidence": "PASS",
    }


def _validate_unit_prerequisite(data, expected, old):
    actual = old._parse_json(data, "unit prerequisite")
    _need(old._typed_equal(actual, expected),
          "routes phase lacks a fresh matching current unit-validation PASS prerequisite")
    return _sha(data)


def _routes_assessment(contract, summary, phase_inputs, manifest_sha, unit_prerequisite_sha):
    old, protocol, protocol_data, source_blobs, _selected, _whitespace = contract
    census = summary["census"]
    _need(type(census) is dict and census.get("status") == "PASS",
          "route pool census is missing or failed")
    return {
        "schema": 1,
        "assessmentStatus": "PASS",
        "scope": protocol["scope"],
        "baseCommit": protocol["baseCommit"],
        "protocolSha256": _sha(protocol_data),
        "priorReaderSha256": OLD_ASSESS_SHA,
        "publishedInputsSha256": {name: _sha(data) for name, data in source_blobs.items()},
        "unitPrerequisiteSha256": unit_prerequisite_sha,
        "routesSuiteManifestSha256": manifest_sha,
        "routePhaseInputsSha256": phase_inputs,
        "nativePhase": {"status": "PASS", "phase": "routes", "evidence": summary},
        "observationDisposition": "PASS; read-only successful finalized route pool observation",
        "fitnessDisposition": "NOT_EVALUATED; published22 remains rejected",
        "policyDisposition": "NO_POLICY_OR_ACCEPTANCE_CHANGE",
        "productionChange": "NONE",
        "limits": list(protocol["limits"]),
    }


def _write_new_artifact(name, value):
    target = HERE / name
    _need(not target.exists() and not target.is_symlink(),
          "refusing to overwrite existing task artifact: " + name)
    payload = json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
    try:
        with target.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(payload)
    except FileExistsError as exc:
        raise ReaderError("refusing to overwrite existing task artifact: " + name) from exc
    return _sha(payload.encode("utf-8"))


def _run_cli(phase):
    contract = _contract()
    old, protocol, _protocol_data, _source_blobs, selected, whitespace = contract
    summary, phase_inputs, manifest_sha = _read_phase_files(
        phase, protocol, old, selected, whitespace)
    if phase == "unit":
        output = _unit_validation(contract, summary, phase_inputs, manifest_sha)
        name = "unit-validation.json"
    else:
        unit_data = _task_file("unit-validation.json", 1024 * 1024)
        unit_summary, unit_inputs, unit_manifest_sha = _read_phase_files(
            "unit", protocol, old, selected, whitespace)
        expected_unit = _unit_validation(contract, unit_summary, unit_inputs, unit_manifest_sha)
        unit_sha = _validate_unit_prerequisite(unit_data, expected_unit, old)
        output = _routes_assessment(contract, summary, phase_inputs, manifest_sha, unit_sha)
        name = "assessment.json"
    digest = _write_new_artifact(name, output)
    print("PASS offline successful route pool " + phase + ": " + digest)
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("unit", "routes"))
    args = parser.parse_args(argv)
    try:
        return _run_cli(args.phase)
    except ReaderError as exc:
        print("FAIL offline successful route pool " + args.phase + ": " + str(exc), file=sys.stderr)
        return 2
    except OSError as exc:
        print("FAIL offline successful route pool " + args.phase + ": input/output error: " + str(exc),
              file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
