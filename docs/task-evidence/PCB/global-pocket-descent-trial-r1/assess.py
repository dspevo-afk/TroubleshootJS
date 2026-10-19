"""Strict offline reader for the COPY-only global pocket descent trial."""

import argparse
import hashlib
import json
import math
import re
import stat
import sys
from pathlib import Path, PurePosixPath


HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
TASK_REL = "docs/task-evidence/PCB/global-pocket-descent-trial-r1"
PROPOSAL_REL = TASK_REL + "/proposal.json"
PROTOCOL_REL = TASK_REL + "/protocol.json"
BASELINE_REL = "docs/task-evidence/PCB/passive-local-pose-choice-r1/accepted15-baselines.json"
BASELINE_SOURCE_REL = "docs/task-evidence/PCB/post-selection-edge-trim-r1/final-native.txt"

# These digests are frozen from the packet bytes present at reader implementation.
PROPOSAL_SHA = "0a5ec22a2990bd6df7ffcc32d4102b497f2974cc58b9e8c6a90f0db8cb659662"
PROTOCOL_SHA = "a934363b2817217dfde6b9bfdf7f2c8b8c9d953f3335803e9f1882a5648f17bd"
UNIT_SUITES_SHA = "d70197f6f11dd4919b16983af9f9a3f470caad856bd8c0c8346edcea32a581e2"
ROUTES_SUITES_SHA = "ec06fedcbc76e823839b4590d2f2061e52ff817467a79f1941cca5f09227b3c8"
BASELINE_SHA = "12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816"
BASELINE_SOURCE_SHA = "db538fe4cca9aff18e33a974a6afa53ecb2fc244c7827c65201cc5408b65330d"

MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_REPORTS = 100000
MAX_COUNT = 1000000
MAX_FIELD_TEXT = 1024 * 1024
MAX_ELAPSED_SECONDS = 36000
MAX_CASE_MILLIS = 60000
# Root-confirmed exact native fixture counts.
GLOBAL_FIXTURE_COUNT = 11
MEDIUM_COST_FIXTURE_COUNT = 1
TIMING_FIELDS = (
    "status", "exitCode", "elapsedSeconds", "suiteCount", "phase", "scope",
    "terminalCleanupIncluded", "cleanupDuration",
)
TIMING_SCOPE = "Native global pocket descent trial; unchanged per-JVM/search/physical limits"
TERMINAL_TEMPLATE = "PASS: focused current contracts; {count} Java suites. Full matrix and independent oracles NOT RUN."
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."
WHITESPACE_SCOPE = (
    "GRID10_FULL_PACKAGE_ACCESS_BOTH_COPPER_FACES_VIAS_SILK_AND_DECLARED_"
    "ISOLATION;FREE_IS_NOT_PACKABLE_PROOF"
)

ROOTS = ["-1017", "-1004", "-1022", "0", "17", "42", "1", "7", "5", "14", "2", "3", "4", "6"]
VERSIONS = [15, 22]
METRICS = ("area", "largestProtectedEmptyRectangleArea", "copperManhattanLength", "vias")
SCORE_FIELDS = ("total", "electrical", "region", "connector", "corridor", "outline", "courtyard", "demand")
MAX_PLACEMENT_SCORE = (1 << 63) - 1
SCORE_RECORD = re.compile(
    r"([0-5])\{" + ";".join(name + r"=([0-9]+)" for name in SCORE_FIELDS) + r"\}"
)
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
CASE_INTEGER_FIELDS = (
    "version", "density", "parts", "pads", "area", "width", "height",
    "largestProtectedEmptyRectangleArea", "copperManhattanLength", "vias",
    "selectedAttempt", "acceptedPlanEvaluations", "placementRejections", "oneFaceAttempts",
    "twoLayerAttempts", "routeAttempts", "routingOrders", "routingExpansions",
    "selectedQualityMilli", "fixedMarginFloor", "accessEnvelopeMargin", "requiredMargin",
    "legacyRequestedMargin", "requestedMargin", "actualMargin", "occupiedX", "occupiedY",
    "occupiedWidth", "occupiedHeight", "outlineX", "outlineY", "elapsedMillis",
)
WHITESPACE_FIELDS = (
    "seed", "version", "parts", "gridUnits", "sampledCells", "packageAccessOccupiedCells",
    "protectedFreeCells", "protectedFreeFraction", "largestProtectedEmptyRectangle", "scope",
)
RECTANGLE_FIELDS = ("x", "y", "width", "height", "area")
REPORT_PREFIXES = ("PCB_WHITESPACE_CASE ", "PCB_GLOBAL_POCKET_CASE ")
FAILURE_LINE = re.compile(
    r"^\s*(?:FAIL(?:ED)?\b|ERROR\b|FATAL\b|Exception in thread\b|AssertionError\b|"
    r"BUILD FAILED\b|\[ERROR\]|CURRENT_CONTRACT_FAILURE:|TIMEOUT\b|TIMED OUT\b|"
    r"CURRENT_CONTRACT_TIMEOUT:|CANCELLED\b|SKIPPED\b|IGNORED\b)", re.IGNORECASE
)
SKIP_OR_FAILURE_WORD = re.compile(r"\b(?:SKIPPED?|IGNORED|TIMEOUT|TIMED\s+OUT|CANCELLED)\b", re.IGNORECASE)
SIGNED_INT = re.compile(r"(?:0|-[1-9][0-9]*|[1-9][0-9]*)\Z")
SIGNED_LONG_TEXT = SIGNED_INT
HEX64 = re.compile(r"[0-9a-f]{64}\Z")


class ReaderError(ValueError):
    """An input failed one of the frozen offline-reader gates."""


def _need(condition, message):
    if not condition:
        raise ReaderError(message)


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def _require_sha(data, expected, label):
    _need(type(expected) is str and HEX64.fullmatch(expected) is not None and _sha(data) == expected,
          label + " hash changed")


def _json_int(token):
    _need(SIGNED_INT.fullmatch(token) is not None and token != "-0", "JSON integer is not canonical")
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
    except (UnicodeDecodeError, json.JSONDecodeError, OverflowError, RecursionError) as exc:
        raise ReaderError(label + " is not strict UTF-8 JSON") from exc


def _typed_equal(left, right):
    if type(left) is not type(right):
        return False
    if type(left) is dict:
        return tuple(left) == tuple(right) and all(_typed_equal(left[key], right[key]) for key in left)
    if type(left) is list:
        return len(left) == len(right) and all(_typed_equal(a, b) for a, b in zip(left, right))
    return left == right


def _same_type_shape(actual, expected):
    if type(actual) is not type(expected):
        return False
    if type(actual) is dict:
        return (tuple(actual) == tuple(expected) and
                all(_same_type_shape(actual[key], expected[key]) for key in expected))
    if type(actual) is list:
        return len(actual) == len(expected) and all(
            _same_type_shape(a, e) for a, e in zip(actual, expected)
        )
    return True


def _is_reparse(path):
    try:
        info = path.lstat()
    except OSError as exc:
        raise ReaderError("sealed input is unavailable") from exc
    if stat.S_ISLNK(info.st_mode):
        return True
    return bool(getattr(info, "st_file_attributes", 0) & 0x400)


def _repo_file(relative, max_bytes=MAX_FILE_BYTES):
    _need(type(relative) is str and "\\" not in relative, "repository path must be slash-relative")
    pure = PurePosixPath(relative)
    _need(not pure.is_absolute() and bool(pure.parts) and
          all(part not in ("", ".", "..") for part in pure.parts), "repository path is not contained")
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


def _expected_proposal():
    return {
        "schema": 1,
        "baseCommit": "a3a0b851c1e5b80212b1d604a3962a8e77437893",
        "scope": "COPY-only private22/density15 candidate2 whole-plan-scored passive pocket descent; default15/general16 unchanged",
        "hypothesis": "Using the existing authoritative whole-plan placement cost for complete proposed moves can avoid the measured local/global score mismatch; physical benefit remains unproved",
        "selection": "One canonical component-ID pass after candidate2 initial legal placement; same four passive package IDs and connector/anchor/isolation exclusions as the rejected21 experiment",
        "origins": "Existing largest40-grid pocket and at mostfive center/corner origins, existing20000-cell bound, domain clipping and10-grid snapping; current position incumbent, ties retained",
        "cost": "A narrow package-private MediumBoardPhysicalPolicy.scorePlan(board,plan) adapter delegates to its existing private scorer; accept only strict lower complete-plan PlacementScore.total and unchanged full placement/access/domain validation; initial greedy mediumPlacementScore stays unchanged",
        "purity": "No new weights/formula/state: existing MST role weights, region cohesion, connector distances, corridor overlaps and area normalization remain exact; scorer adapter requires nonnull board/plan; source graph/inventory/pose/outline unchanged",
        "budget": "Every distinct snapped nonincumbent proposal tried consumes the same60000 plan counter including scoring; optional exhaustion keeps legal incumbent; no extra candidate/router request or changed caps",
        "roots": list(ROOTS),
        "requiredBeforeCorpus": ["root source/core review", "literal independent global cost components and role/MST/corridor/connector fixtures", "actual global-pass move including a linked case", "source/pose identity and raw other-candidate parity", "budget/eligibility/access-envelope/tie fixtures", "strict reader canaries including global score monotonicity", "actual selected-PowerShell4/7-suite transport", "frozen consumed inputs", "fresh native unit4 PASS"],
        "finiteAcceptance": "All14 saved15 rows exact42 non-time fields; all14 explicit22 physical/source admissions; all14 existing global candidate2 total costs never increase and otherfive score records remain exact; board area, protected void, copper and vias never increase with at least one area/void decrease",
        "rejection": "Preserve exact failure and source; restore/retire owned COPY; no promotion or altered acceptance limits",
        "promotion": "Only after finite acceptance, separate actual production/native/JDK8-GWT/visible player/replay qualification",
        "limits": "Informed finite corpus; whole-plan cost is still a coarse proxy, not route/electrical truth; no all-seed/manufacturing/general performance claim",
    }


def _expected_protocol():
    unit = [
        ["PcbGlobalPocketDescentContractTest", "PCB global pocket descent contracts "],
        ["PcbMediumPlacementCostContractTest", "PCB medium placement cost contracts "],
        ["PcbVacancyTargetsContractTest", "PCB vacancy targets contracts "],
        ["RelBSessionIdentityContractTest", "REL-B session identity contracts "],
    ]
    routes = [[
        "PcbGlobalPocketDescentTrialContractTest" + ("Group" + str(group) if group > 1 else ""),
        "PCB global pocket descent trial " + ("Group" + str(group) + " contracts " if group > 1 else "contracts "),
    ] for group in range(1, 8)]
    return {
        "schema": 1,
        "baseCommit": "a3a0b851c1e5b80212b1d604a3962a8e77437893",
        "roots": list(ROOTS),
        "versions": list(VERSIONS),
        "densities": [14, 15],
        "unitSuites": unit,
        "routesSuites": routes,
        "baselinePath": BASELINE_REL,
        "baselineSha256": BASELINE_SHA,
        "baselineSourcePath": BASELINE_SOURCE_REL,
        "baselineSourceSha256": BASELINE_SOURCE_SHA,
        "casePrefix": "PCB_GLOBAL_POCKET_CASE ",
        "routePassDetail": "roots=2 pairs=2 rows=4 evaluator=planMedium",
        "metrics": list(METRICS),
        "timingScope": TIMING_SCOPE,
        "scope": "COPY-only explicit22; all14 explicit15 replay42 non-time fields exact; strict four-metric no-regression with an area/void improvement required",
    }


def _protocol_from_bytes(data, verify_digest=True):
    if verify_digest:
        _require_sha(data, PROTOCOL_SHA, "sealed protocol")
    doc = _parse_json(data, "protocol")
    _need(type(doc) is dict and _typed_equal(doc, _expected_protocol()),
          "protocol content differs from frozen contract")
    return doc


def _proposal_from_bytes(data):
    _require_sha(data, PROPOSAL_SHA, "sealed proposal")
    doc = _parse_json(data, "proposal")
    _need(type(doc) is dict and _typed_equal(doc, _expected_proposal()),
          "proposal content differs from frozen contract")
    return doc


def _read_baseline(protocol):
    data = _repo_file(protocol["baselinePath"])
    _require_sha(data, BASELINE_SHA, "accepted15 baseline")
    source = _repo_file(protocol["baselineSourcePath"])
    _require_sha(source, BASELINE_SOURCE_SHA, "original accepted15 native log")
    doc = _parse_json(data, "accepted15 baseline")
    expected_keys = ("status", "scope", "source", "sourceSha256", "rows", "reuseBoundary")
    _need(type(doc) is dict and tuple(doc) == expected_keys, "accepted15 baseline schema/order changed")
    _need(doc["status"] == "PINNED" and
          doc["scope"] == "All14 informed accepted15 regressions; not unseen" and
          doc["source"] == BASELINE_SOURCE_REL and doc["sourceSha256"] == BASELINE_SOURCE_SHA,
          "accepted15 baseline provenance changed")
    rows = doc["rows"]
    _need(type(rows) is list and len(rows) == len(ROOTS), "accepted15 baseline row count changed")
    for seed, row in zip(ROOTS, rows):
        _need(type(row) is dict and tuple(row) == CASE_FIELDS,
              "accepted15 baseline case schema/order changed")
        _need(row["status"] == "PASS" and row["seed"] == seed and
              type(row["version"]) is int and row["version"] == 15 and
              type(row["density"]) is int and row["density"] == 14,
              "accepted15 baseline root/version identity changed")
        _need(type(row["parts"]) is int and 20 <= row["parts"] <= 128 and
              type(row["pads"]) is int and 1 <= row["pads"] <= 1024,
              "accepted15 baseline population outside retained bounds")
        _validate_case_shape(row, seed, 15, 14)
    return data, doc, rows, source


def _load_contract():
    proposal = _proposal_from_bytes(_repo_file(PROPOSAL_REL, 256 * 1024))
    protocol = _protocol_from_bytes(_repo_file(PROTOCOL_REL, 256 * 1024))
    baseline_data, baseline_doc, baseline_rows, source_data = _read_baseline(protocol)
    return proposal, protocol, baseline_data, baseline_doc, baseline_rows, source_data


def _suite_manifest(phase, protocol):
    if phase == "unit":
        name, expected, digest = "unit-suites.json", protocol["unitSuites"], UNIT_SUITES_SHA
    else:
        name, expected, digest = "routes-suites.json", protocol["routesSuites"], ROUTES_SUITES_SHA
    raw = _task_file(name, 64 * 1024)
    _require_sha(raw, digest, "sealed " + phase + " suite manifest")
    actual = _parse_json(raw, phase + " suite manifest")
    _need(_typed_equal(actual, expected), phase + " suite manifest order/content changed")
    return actual, _sha(raw)


def _canonical_seed(value):
    _need(type(value) is str and SIGNED_LONG_TEXT.fullmatch(value) is not None,
          "seed is not canonical signed-long text")
    _need(len(value) <= 20, "seed exceeds signed-long text bound")
    number = int(value)
    _need(-(1 << 63) <= number < (1 << 63) and str(number) == value,
          "seed is outside signed-long range")
    return value


def _parse_reports(text):
    _need(type(text) is str, "native phase text is missing or oversized")
    try:
        text_size = len(text.encode("utf-8", errors="strict"))
    except UnicodeEncodeError as exc:
        raise ReaderError("native phase text is not strict UTF-8") from exc
    _need(text_size <= MAX_FILE_BYTES, "native phase text is missing or oversized")
    reports = []
    for line in text.splitlines():
        if not line.startswith("PCB_"):
            continue
        prefix = next((candidate for candidate in REPORT_PREFIXES if line.startswith(candidate)), None)
        _need(prefix is not None, "unknown native PCB report prefix")
        try:
            encoded = line[len(prefix):].encode("utf-8", errors="strict")
        except UnicodeEncodeError as exc:
            raise ReaderError("native report is not strict UTF-8 text") from exc
        row = _parse_json(encoded, "native report")
        _need(type(row) is dict, "native report must be a JSON object")
        reports.append((prefix, row, line))
        _need(len(reports) <= MAX_REPORTS, "native report count exceeds reader bound")
    return reports


def _validate_case_shape(row, seed, version, density):
    _need(type(row) is dict and tuple(row) == CASE_FIELDS,
          "global pocket descent case schema/order changed")
    _need(row["status"] == "PASS" and _canonical_seed(row["seed"]) == seed,
          "global pocket descent case status/root changed")
    _need(type(row["version"]) is int and row["version"] == version and
          type(row["density"]) is int and row["density"] == density,
          "global pocket descent case version/density changed")
    _need(type(row["elapsedMillis"]) is int and 0 <= row["elapsedMillis"] <= MAX_CASE_MILLIS,
          "global pocket descent case elapsedMillis is not a bounded integer")
    _need(type(row["parts"]) is int and 20 <= row["parts"] <= 128 and
          type(row["pads"]) is int and 1 <= row["pads"] <= 1024,
          "global pocket descent case population is outside reader bounds")
    for key in CASE_INTEGER_FIELDS:
        value = row[key]
        _need(type(value) is int and 0 <= value <= MAX_COUNT * 1000000,
              "global pocket descent case numeric field is outside reader bounds: " + key)
    for key in ("area", "width", "height", "largestProtectedEmptyRectangleArea",
                "copperManhattanLength", "vias"):
        _need(type(row[key]) is int and row[key] >= 0, "global pocket descent metric is not a nonnegative integer: " + key)
    _need(row["width"] > 0 and row["height"] > 0 and row["area"] == row["width"] * row["height"],
          "global pocket descent area does not equal width times height")
    for key in ("geometrySha256", "sourcePlanSha256", "liveBoardGraphSha256", "sourceIntentSha256"):
        _need(type(row[key]) is str and HEX64.fullmatch(row[key]) is not None,
              "global pocket descent identity digest is malformed: " + key)
    for key in ("layoutSeed", "routingSeed"):
        _canonical_seed(row[key])
    _need(type(row["rankedPlacementAttempts"]) is list and
          len(row["rankedPlacementAttempts"]) == 6 and
          all(type(item) is int and 0 <= item <= 5 for item in row["rankedPlacementAttempts"]) and
          sorted(row["rankedPlacementAttempts"]) == list(range(6)),
          "global pocket descent ranked-placement order is malformed")
    for key in ("statisticsCanonical", "excludedWorkbenchChrome"):
        _need(type(row[key]) is str and 0 < len(row[key].encode("utf-8")) <= MAX_FIELD_TEXT,
              "global pocket descent text field is malformed: " + key)
    _placement_score_records(row)


def _check_saved15(actual, expected, seed):
    _validate_case_shape(actual, seed, 15, 14)
    _need(tuple(expected) == CASE_FIELDS and expected["seed"] == seed,
          "sealed accepted15 case schema/root changed")
    actual_core, expected_core = dict(actual), dict(expected)
    actual_core.pop("elapsedMillis")
    expected_core.pop("elapsedMillis")
    _need(_typed_equal(actual_core, expected_core),
          "accepted15 non-time case fields differ from sealed baseline")


def _check_trial22(actual, saved15, seed):
    _validate_case_shape(actual, seed, 22, 15)
    _need(_same_type_shape(actual, saved15), "explicit22 case types differ from saved15 schema")
    for key in ("parts", "pads", "liveBoardGraphSha256", "sourceIntentSha256", "layoutSeed", "routingSeed"):
        _need(_typed_equal(actual[key], saved15[key]), "explicit22 identity changed: " + key)
    deltas = {key: actual[key] - saved15[key] for key in METRICS}
    return deltas


def _int_field(row, key, low, high):
    value = row.get(key)
    _need(type(value) is int and low <= value <= high, "whitespace integer field outside bounds: " + key)
    return value


def _whitespace_case(row, route, seed, version):
    _need(type(row) is dict and tuple(row) == WHITESPACE_FIELDS,
          "whitespace report schema/order changed")
    _need(_canonical_seed(row["seed"]) == seed and type(row["version"]) is int and row["version"] == version,
          "whitespace report root/version changed")
    _need(type(row["parts"]) is int and row["parts"] == route["parts"] and
          type(row["gridUnits"]) is int and row["gridUnits"] == 10,
          "whitespace report part/grid identity changed")
    sampled = _int_field(row, "sampledCells", 1, MAX_COUNT * 1000000)
    occupied = _int_field(row, "packageAccessOccupiedCells", 0, sampled)
    free = _int_field(row, "protectedFreeCells", 0, sampled)
    fraction = row["protectedFreeFraction"]
    _need(type(fraction) in (int, float) and math.isfinite(fraction) and 0 <= fraction <= 1 and
          abs(fraction - free / sampled) <= 1e-9,
          "whitespace protected-free fraction is malformed or inconsistent")
    rectangle = row["largestProtectedEmptyRectangle"]
    _need(type(rectangle) is dict and tuple(rectangle) == RECTANGLE_FIELDS,
          "protected rectangle schema/order changed")
    x, y, width, height, area = (rectangle[key] for key in RECTANGLE_FIELDS)
    _need(all(type(value) is int for value in (x, y, width, height, area)) and
          width > 0 and height > 0 and width % 10 == 0 and height % 10 == 0,
          "protected rectangle dimensions are malformed")
    _need(area == width * height and area == route["largestProtectedEmptyRectangleArea"],
          "protected rectangle area differs from route metric")
    _need(type(route["outlineX"]) is int and type(route["outlineY"]) is int and
          (x - route["outlineX"]) % 10 == 0 and (y - route["outlineY"]) % 10 == 0,
          "protected rectangle is not aligned to the route outline")
    _need(x >= route["outlineX"] and y >= route["outlineY"] and
          x + width <= route["outlineX"] + route["width"] and
          y + height <= route["outlineY"] + route["height"],
          "protected rectangle lies outside the board outline")
    _need(row["scope"] == WHITESPACE_SCOPE, "whitespace declared scope changed")


def assess_routes(text, baseline_rows):
    """Return native admission and strict fitness as separate, independently visible results."""
    _need(type(baseline_rows) is list and len(baseline_rows) == len(ROOTS),
          "sealed accepted15 rows required")
    reports = _parse_reports(text)
    _need(len(reports) == len(ROOTS) * 2 * 2, "routes phase requires exactly 28 whitespace and 28 case rows")
    case_rows = []
    whitespace_rows = []
    deltas = []
    violations = []
    global_rows = []
    global_violations = []
    any_area_or_void_decrease = False
    cursor = 0
    for seed, old in zip(ROOTS, baseline_rows):
        root_cases, root_whitespace = [], []
        for version in VERSIONS:
            _need(cursor + 1 < len(reports), "required version report pair is missing")
            whitespace_prefix, whitespace, _ = reports[cursor]
            case_prefix, case, _ = reports[cursor + 1]
            _need(whitespace_prefix == "PCB_WHITESPACE_CASE " and
                  case_prefix == "PCB_GLOBAL_POCKET_CASE ",
                  "reports must be whitespace then pocket descent case for each ordered version")
            _whitespace_case(whitespace, case, seed, version)
            if version == 15:
                _check_saved15(case, old, seed)
                delta = {metric: 0 for metric in METRICS}
            else:
                delta = _check_trial22(case, old, seed)
                for metric in METRICS:
                    if delta[metric] > 0:
                        violations.append({
                            "seed": seed, "metric": metric, "baseline": old[metric],
                            "trial": case[metric], "delta": delta[metric],
                        })
                if delta["area"] < 0 or delta["largestProtectedEmptyRectangleArea"] < 0:
                    any_area_or_void_decrease = True
                deltas.append({"seed": seed, **delta})
                saved_scores = _placement_score_records(old)
                trial_scores = _placement_score_records(case)
                candidate2_before = saved_scores[2]["total"]
                candidate2_after = trial_scores[2]["total"]
                changed_others = [candidate for candidate in range(6) if candidate != 2 and
                                  not _typed_equal(trial_scores[candidate], saved_scores[candidate])]
                candidate2_nonincreasing = candidate2_after <= candidate2_before
                if not candidate2_nonincreasing:
                    global_violations.append({
                        "seed": seed, "rule": "candidate2-total-nonincreasing",
                        "baseline": candidate2_before, "trial": candidate2_after,
                        "delta": candidate2_after - candidate2_before,
                    })
                if changed_others:
                    global_violations.append({
                        "seed": seed, "rule": "other-five-score-records-exact",
                        "candidateIds": changed_others,
                    })
                global_rows.append({
                    "seed": seed,
                    "candidate2TotalBefore": candidate2_before,
                    "candidate2TotalAfter": candidate2_after,
                    "candidate2TotalDelta": candidate2_after - candidate2_before,
                    "candidate2Nonincreasing": candidate2_nonincreasing,
                    "otherFiveScoreRecordsExact": not changed_others,
                    "changedOtherCandidateIds": changed_others,
                })
            root_cases.append(case)
            root_whitespace.append(whitespace)
            case_rows.append(case)
            whitespace_rows.append(whitespace)
            cursor += 2
    _need(cursor == len(reports), "extra or foreign native report rows remain")
    strict_status = "PASS" if not violations and any_area_or_void_decrease else "FAIL"
    strict_reasons = []
    if violations:
        strict_reasons.append("one or more strict metrics increased")
    if not any_area_or_void_decrease:
        strict_reasons.append("no area or protected-empty-rectangle decrease was observed")
    global_score = {
        "status": "PASS" if not global_violations else "FAIL",
        "requirements": "candidate2 existing whole-plan total never increases; all eight score components for the other five ranked candidates remain exact to saved15; every recorded total is recomputed from the frozen formula",
        "candidateCount": len(global_rows),
        "violations": global_violations,
        "rows": global_rows,
    }
    return {
        "nativeAdmission": {"status": "PASS", "cases": 28, "roots": 14,
                            "explicit15ExactRows": 14, "explicit22AdmittedRows": 14},
        "strictFitness": {
            "status": strict_status,
            "requirements": "area, protected empty rectangle, copper length and vias never increase; at least one area or protected-empty-rectangle decrease",
            "reasons": strict_reasons,
            "violations": violations,
            "deltas": deltas,
        },
        "globalScoreMonotonicity": global_score,
        "routeCaseRows": len(case_rows),
        "whitespaceRows": len(whitespace_rows),
        "orderedRootVersions": [{"seed": row["seed"], "version": row["version"]} for row in case_rows],
    }


def _suite_pairs(suites, phase, protocol):
    expected = protocol["unitSuites"] if phase == "unit" else protocol["routesSuites"]
    _need(type(suites) is list and _typed_equal(suites, expected),
          "phase suite order/content differs from frozen protocol")
    return [(item[0], item[1]) for item in expected]


def _positive_count(text, label):
    _need(type(text) is str and 0 < len(text) <= len(str(MAX_COUNT)),
          label + " exceeds the reader bound")
    value = int(text)
    _need(1 <= value <= MAX_COUNT, label + " is outside the positive reader bound")
    return value


def _score_total(score):
    """Recompute the existing eight-component medium whole-plan score."""
    _need(type(score) is dict and tuple(score) == SCORE_FIELDS,
          "placement score component order changed")
    values = []
    for name in SCORE_FIELDS:
        value = score[name]
        _need(type(value) is int and 0 <= value <= MAX_PLACEMENT_SCORE,
              "placement score component is outside bounded nonnegative long range: " + name)
        values.append(value)
    total = (8 * score["electrical"] + 4 * score["region"] + 6 * score["connector"] +
             12 * score["corridor"] + score["outline"] // 1000 +
             score["courtyard"] // 1000 + score["demand"] // 1000)
    _need(total <= MAX_PLACEMENT_SCORE, "recomputed placement score exceeds bounded long range")
    _need(score["total"] == total, "placement score total differs from the frozen component formula")
    return total


def _placement_score_records(row):
    """Parse and independently validate all ranked candidate score records."""
    canonical = row.get("statisticsCanonical") if type(row) is dict else None
    _need(type(canonical) is str and canonical.count("placementScores=[") == 1 and
          canonical.count("];selectedScore=") == 1,
          "canonical statistics must contain one complete placement-score block")
    start = canonical.index("placementScores=[") + len("placementScores=[")
    end = canonical.index("];selectedScore=", start)
    block = canonical[start:end]
    matches = list(SCORE_RECORD.finditer(block))
    _need(len(matches) == 6 and ", ".join(match.group(0) for match in matches) == block,
          "canonical placement-score records are malformed or duplicated")
    ranked = row.get("rankedPlacementAttempts")
    _need(type(ranked) is list and [int(match.group(1)) for match in matches] == ranked,
          "placement-score record order differs from ranked candidate order")
    records = {}
    for match in matches:
        candidate = int(match.group(1))
        score = {}
        for name, token in zip(SCORE_FIELDS, match.groups()[1:]):
            _need(0 < len(token) <= 19, "placement score component exceeds bounded long text")
            value = int(token)
            _need(value <= MAX_PLACEMENT_SCORE and str(value) == token,
                  "placement score component is noncanonical or outside bounded long range")
            score[name] = value
        _score_total(score)
        records[candidate] = score
    _need(tuple(records) == tuple(ranked), "placement-score candidate identity/order changed")
    return records


def _unit_pass_line(line, marker):
    _need(line.startswith("PASS: " + marker), "unit PASS marker missing, duplicate, or reordered")
    if marker == "PCB global pocket descent contracts ":
        match = re.fullmatch(re.escape("PASS: " + marker) +
                             r"assertions=([1-9][0-9]*) fixtures=([1-9][0-9]*) evaluator=globalPocketDescent", line)
        _need(match is not None, "global pocket descent unit detail is malformed")
        _positive_count(match.group(1), "global pocket descent assertion count")
        fixtures = _positive_count(match.group(2), "global pocket descent fixture count")
        _need(fixtures == GLOBAL_FIXTURE_COUNT,
              "global pocket descent fixture count differs from frozen contract")
    elif marker == "PCB medium placement cost contracts ":
        match = re.fullmatch(re.escape("PASS: " + marker) +
                             r"assertions=([1-9][0-9]*) fixtures=([1-9][0-9]*) evaluator=mediumWholePlanCost", line)
        _need(match is not None, "medium whole-plan cost unit detail is malformed")
        _positive_count(match.group(1), "medium whole-plan cost assertion count")
        fixtures = _positive_count(match.group(2), "medium whole-plan cost fixture count")
        _need(fixtures == MEDIUM_COST_FIXTURE_COUNT,
              "medium whole-plan cost fixture count differs from frozen contract")
    elif marker == "PCB vacancy targets contracts ":
        match = re.fullmatch(re.escape("PASS: " + marker) + r"assertions=([1-9][0-9]*)", line)
        _need(match is not None, "vacancy targets unit detail is malformed")
        _positive_count(match.group(1), "vacancy targets assertion count")
    else:
        match = re.fullmatch(re.escape("PASS: " + marker) +
                             r"assertions=([1-9][0-9]*) scope=IDENTITY_AND_ADMISSION_ONLY ownerRoundTrip=false",
                             line)
        _need(match is not None, "REL-B session identity unit detail is malformed")
        _positive_count(match.group(1), "REL-B session identity assertion count")

def _phase_summary(text, receipt, timing, suites, protocol, baseline_rows):
    _need(type(timing) is dict and tuple(timing) == TIMING_FIELDS,
          "native timing schema/order changed")
    phase = timing["phase"]
    _need(phase in ("unit", "routes"), "unknown native phase")
    pairs = _suite_pairs(suites, phase, protocol)
    suite_count = len(pairs)
    elapsed = timing["elapsedSeconds"]
    _need(timing["status"] == "PASS" and type(timing["exitCode"]) is int and timing["exitCode"] == 0,
          "native phase status/exit failed")
    _need(type(timing["suiteCount"]) is int and timing["suiteCount"] == suite_count,
          "native suite count differs from phase contract")
    _need(type(elapsed) in (int, float) and math.isfinite(elapsed) and
          0 <= elapsed <= MAX_ELAPSED_SECONDS,
          "native elapsed time is malformed or outside the unchanged bound")
    _need(timing["phase"] == phase and timing["scope"] == TIMING_SCOPE and
          timing["terminalCleanupIncluded"] is True and
          timing["cleanupDuration"] == "NOT_SEPARATELY_MEASURED",
          "native phase scope or cleanup boundary changed")
    _need(type(text) is str and type(receipt) is str,
          "native phase log/receipt missing or oversized")
    try:
        text_size = len(text.encode("utf-8", errors="strict"))
        receipt_size = len(receipt.encode("utf-8", errors="strict"))
    except UnicodeEncodeError as exc:
        raise ReaderError("native phase log/receipt is not strict UTF-8") from exc
    _need(text_size <= MAX_FILE_BYTES and receipt_size <= MAX_FILE_BYTES,
          "native phase log/receipt missing or oversized")
    text_lines, receipt_lines = text.splitlines(), receipt.splitlines()
    _need(not any(FAILURE_LINE.search(line) or SKIP_OR_FAILURE_WORD.search(line)
                  for line in text_lines + receipt_lines),
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
    for line, (_name, marker) in zip(suite_passes, pairs):
        if phase == "unit":
            _unit_pass_line(line, marker)
        else:
            expected = "PASS: " + marker + protocol["routePassDetail"]
            _need(line == expected, "routes PASS detail differs from frozen contract")
    _need(all(text_lines.index(line) < text_lines.index(terminal) for line in suite_passes),
          "suite PASS appeared after terminal marker")
    text_reports, receipt_reports = _parse_reports(text), _parse_reports(receipt)
    _need([item[2] for item in text_reports] == [item[2] for item in receipt_reports],
          "native report lines differ between terminal log and child receipt")
    report_positions = [index for index, line in enumerate(text_lines) if line.startswith("PCB_")]
    _need(all(index < text_lines.index(terminal) for index in report_positions),
          "native report appeared after terminal marker")
    if phase == "unit":
        _need(not text_reports and not receipt_reports, "unit contract leaked route corpus reports")
        census = None
        # Unit suites have no per-root report blocks.
    else:
        for suite_index, pass_line in enumerate(suite_passes):
            marker_position = text_lines.index(pass_line)
            prior_positions = [p for p in report_positions if p < marker_position]
            prior_marker_positions = [text_lines.index(line) for line in suite_passes[:suite_index]]
            lower = max(prior_marker_positions, default=-1)
            block_count = sum(lower < p < marker_position for p in report_positions)
            _need(block_count == 8, "each routes suite must report exactly two roots and four version rows")
        census = assess_routes(text, baseline_rows)
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
    """Validate phase log, child receipt, timing, frozen suites, and route rows."""
    _proposal, protocol, _baseline_data, _baseline_doc, rows, _source = _load_contract()
    phase = timing.get("phase") if type(timing) is dict else None
    _need(phase in ("unit", "routes"), "unknown native phase")
    manifest, _digest = _suite_manifest(phase, protocol)
    _need(_typed_equal(suites, manifest), "caller suite manifest differs from pinned bytes")
    return _phase_summary(text, receipt, timing, suites, protocol, rows)


def _phase_inputs(phase):
    _proposal, protocol, _baseline_data, _baseline_doc, baseline_rows, _source = _load_contract()
    suites, manifest_sha = _suite_manifest(phase, protocol)
    suite_name = "unit-suites.json" if phase == "unit" else "routes-suites.json"
    names = (phase + ".txt", phase + "-receipt.txt", phase + "-timing.json", suite_name)
    raw = {name: _task_file(name) for name in names}
    try:
        text = raw[names[0]].decode("utf-8", errors="strict")
        receipt = raw[names[1]].decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ReaderError("native phase log/receipt is not strict UTF-8") from exc
    timing = _parse_json(raw[names[2]], "native timing")
    summary = _phase_summary(text, receipt, timing, suites, protocol, baseline_rows)
    return summary, {name: _sha(data) for name, data in raw.items()}, manifest_sha


def _unit_validation(protocol, baseline_data):
    summary, inputs, manifest_sha = _phase_inputs("unit")
    return _unit_validation_from_evidence(protocol, baseline_data, summary, inputs, manifest_sha)


def _unit_validation_from_evidence(protocol, baseline_data, summary, inputs, manifest_sha):
    return {
        "schema": 1,
        "status": "PASS",
        "phase": "unit",
        "scope": "Unit global-pocket, whole-plan cost, vacancy-target, and session-identity contracts only; routes and fitness are not evaluated.",
        "proposalSha256": _sha(_repo_file(PROPOSAL_REL, 256 * 1024)),
        "protocolSha256": _sha(_repo_file(PROTOCOL_REL, 256 * 1024)),
        "baselineSha256": _sha(baseline_data),
        "baselineSourceSha256": BASELINE_SOURCE_SHA,
        "suiteManifestSha256": manifest_sha,
        "phaseInputs": inputs,
        "phaseEvidence": summary,
        "nativeRuntimeEvidence": "PASS",
    }


def _validate_unit_prerequisite(prereq_data, current_unit):
    prereq = _parse_json(prereq_data, "unit prerequisite")
    _need(_typed_equal(prereq, current_unit),
          "routes phase lacks a fresh matching current unit-validation PASS prerequisite")
    return _sha(prereq_data)


def _routes_assessment(protocol, baseline_data, unit_prerequisite_data=None):
    current_unit = _unit_validation(protocol, baseline_data)
    prereq_data = (_task_file("unit-validation.json", 1024 * 1024)
                   if unit_prerequisite_data is None else unit_prerequisite_data)
    prereq_sha = _validate_unit_prerequisite(prereq_data, current_unit)
    route_summary, route_inputs, manifest_sha = _phase_inputs("routes")
    census = route_summary["census"]
    return {
        "schema": 1,
        "assessmentStatus": "PASS",
        "scope": _expected_proposal()["scope"],
        "proposalSha256": _sha(_repo_file(PROPOSAL_REL, 256 * 1024)),
        "protocolSha256": _sha(_repo_file(PROTOCOL_REL, 256 * 1024)),
        "baselineSha256": _sha(baseline_data),
        "baselineSourceSha256": BASELINE_SOURCE_SHA,
        "unitPrerequisiteSha256": prereq_sha,
        "routesSuiteManifestSha256": manifest_sha,
        "routePhaseInputs": route_inputs,
        "nativePhase": {"status": "PASS", "phase": "routes", "evidence": route_summary},
        "admission": census["nativeAdmission"],
        "strictFitness": census["strictFitness"],
        "globalScoreMonotonicity": census["globalScoreMonotonicity"],
        "caseCounts": {"globalPocketDescent": census["routeCaseRows"], "whitespace": census["whitespaceRows"]},
        "nativeRuntimeEvidence": "PASS",
        "limits": [
            "Fourteen fixed roots only; the result makes no unseen-root or all-seed claim.",
            "All14 explicit15 route cases match all42 non-time saved fields; elapsedMillis is bounded separately.",
            "Explicit22 native admission, whole-plan cost monotonicity, and four-metric fitness are reported separately.",
            "Explicit22 player replay, solver, manufacturing and production promotion are NOT RUN.",
        ],
    }


def _write_task_artifact(name, value):
    path = HERE / name
    _need(not path.exists(), "refusing to overwrite an existing task phase artifact")
    data = (json.dumps(value, ensure_ascii=True, indent=2, allow_nan=False) + "\n").encode("utf-8")
    _need(len(data) <= 4 * 1024 * 1024, "phase artifact exceeds output bound")
    try:
        with path.open("xb") as stream:
            stream.write(data)
    except FileExistsError as exc:
        raise ReaderError("refusing to overwrite an existing task phase artifact") from exc
    except OSError as exc:
        raise ReaderError("phase artifact could not be written") from exc


def main(argv=None):
    parser = argparse.ArgumentParser(description="Validate the bounded offline global pocket descent trial")
    parser.add_argument("phase", choices=("unit", "routes"))
    args = parser.parse_args(argv)
    try:
        _proposal, protocol, baseline_data, _baseline_doc, _rows, _source = _load_contract()
        if args.phase == "unit":
            result = _unit_validation(protocol, baseline_data)
            _write_task_artifact("unit-validation.json", result)
            print("PASS unit phase receipt; routes and fitness are not evaluated")
            return 0
        result = _routes_assessment(protocol, baseline_data)
        _write_task_artifact("assessment.json", result)
        fitness = result["strictFitness"]["status"]
        global_score = result["globalScoreMonotonicity"]["status"]
        print("PASS native routes/admission; strict fitness " + fitness +
              "; global score monotonicity " + global_score)
        return 0 if fitness == "PASS" and global_score == "PASS" else 2
    except ReaderError as exc:
        print("FAIL offline reader: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
