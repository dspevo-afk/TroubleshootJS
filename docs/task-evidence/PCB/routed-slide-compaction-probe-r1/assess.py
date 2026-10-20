"""Strict offline reader for the selected-frame routed slide probe."""

import argparse
import hashlib
import json
import math
import re
import stat
import sys
import types
from pathlib import Path, PurePosixPath


HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
TASK_REL = "docs/task-evidence/PCB/routed-slide-compaction-probe-r1"
OLD_READER_REL = "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/assess.py"
OLD_READER_SHA = "4cf7a9311de3252b6f3f04ca74a5a3945b3aa80fea71ac2f35ebfb962f417c1a"
OLD_PROTOCOL_REL = "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/protocol.json"
OLD_PROTOCOL_SHA = "6982e4e88969f599c3c859ed8bb9fc2e67946f24c9f5b0453c7e705231102d30"
PROTOCOL_SHA = "88123f76e526d42dce97bb656eda8ffdf4479ca947052cf819738e8e79aa93cb"

PUBLISHED_INPUTS = {
    "docs/task-evidence/PCB/passive-local-pose-choice-r1/accepted15-baselines.json":
        "12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816",
    OLD_PROTOCOL_REL: OLD_PROTOCOL_SHA,
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/native-inputs.json":
        "44243c64f1d494760d89c00598895858e17c409b30abc0ad898e01e49fc3d0bf",
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/root-review.json":
        "6dd3da2733a2708549a9c36b31a541872b460336dc10d8fdb499b1ff8643b1d3",
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/routes.txt":
        "ad0901034961f1ea079131080d5e242f63ac7a317cc3d13afb1516437c606ea3",
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/assessment.json":
        "c5d561e8953387b0f8f5e5d7c5767921e3bd21c1ff374ad1f995c63b7a81e593",
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/feasibility-analysis.json":
        "3be27e5cccef6241f405667eb1a8c3c372b3768fe735a10eb8a35ee12fa3858f",
    OLD_READER_REL: OLD_READER_SHA,
}
SUITE_MANIFEST_SHA = {
    "unit-suites.json": "bd6116a36c02756dd793c981ded3960558ab90952b17c0c644146403549cd37d",
    "routes-suites.json": "f3efdcf7da52c8a18849cd7f1421e490a80eb1c81b55926d558bce6f27094fef",
}

WHITESPACE_PREFIX = "PCB_WHITESPACE_CASE "
LEG_CASE_PREFIX = "PCB_ROUTED_LEG_CASE "
SUMMARY_PREFIX = "PCB_ROUTED_SLIDE_COMPACTION_CASE "
ROOTS = ("5", "2")
VERSION = 15
MAX_SIGNED_LONG = (1 << 63) - 1
MIN_SIGNED_LONG = -(1 << 63)
MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_REPORTS = 100000
MAX_FIELD_TEXT = 1024 * 1024
MAX_COUNT = 1000000
EXPECTED_REACHABILITY = "9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7"
OLD_SEED2_TRACE_PROVENANCE = "3953fa912c58ba8a62097c7d9eb6c03bfe1016d3bfd312920706c498e3708de1"
OLD_FEASIBILITY_PREFIX = "PCB_ROUTED_PASSIVE_FEASIBILITY_CENSUS "
SELECTED_ROUTE = "P07_FULLER_TWO_LAYER"
SOURCE_GENERATION = "ACCEPTED_SELECTED"
PRIVATE_GENERATION = "UNASSIGNED_PRIVATE_CANDIDATE"
PROTOCOL_ROUTE_DETAIL = "roots=2 rows=2 evaluator=selectedRoutedSlideCompaction"

SUMMARY_FIELDS = (
    "seed", "label", "version", "phase", "status", "selectedAttempt",
    "selectedRoute", "newRouteRequests", "selectedMutation", "source",
    "sourceMetrics", "control", "rows", "aggregate", "elapsedMillis",
)
SOURCE_FIELDS = (
    "liveGraphBefore", "liveGraphAfter", "geometryBefore", "geometryAfter",
    "copperLength", "area", "componentCount", "padCount", "traceCount",
    "traceProvenanceHash", "sourceRouteSourceCount", "reachabilityHash",
    "padPairCount",
)
METRICS_FIELDS = (
    "area", "copperLength", "vias", "largestProtectedEmptyRectangleArea",
    "geometrySha256", "stableIdentitySha256", "labelIdentitySha256",
    "generationMetadata",
)
CONTROL_FIELDS = ("compacted", "metrics", "proof", "sourceImmutable")
ROW_FIELDS = ("feasibility", "compaction")
COMPACTION_FIELDS = (
    "compacted", "metrics", "proof", "sourceImmutable", "strictImprovement",
)
AGGREGATE_FIELDS = (
    "structuralDirections", "validatedMoves", "rejectedMoves", "compactions",
    "areaReductions", "strictImprovements",
)
PROOF_FIELDS = (
    "placementValidated", "geometryValidated", "routingValidated",
    "routeQualityValidated", "traceClearanceValidated", "twoLayerValidated",
    "padPairCount", "sourceReachabilityHash", "candidateReachabilityHash",
    "sourceRouteSourceCount", "candidateRouteSourceCount",
    "sourceTraceProvenanceHash", "candidateTraceProvenanceHash",
    "stableIdentityVerified", "labelIdentityVerified",
    "conductorReachabilityVerified", "traceProvenanceVerified",
    "sourceImmutable",
)
FEASIBILITY_PROOF_FIELDS = (
    "placementValidated", "geometryValidated", "twoLayerValidated", "padPairCount",
    "sourceReachabilityHash", "candidateReachabilityHash",
    "sourceRouteSourceCount", "candidateRouteSourceCount",
    "sourceTraceProvenanceHash", "candidateTraceProvenanceHash",
    "traceEndpointOnlyVerified", "unrelatedGeometryVerified",
    "conductorReachabilityVerified", "traceProvenanceVerified",
)
TIMING_FIELDS = (
    "status", "exitCode", "elapsedSeconds", "suiteCount", "phase", "scope",
    "terminalCleanupIncluded", "cleanupDuration",
)
TERMINAL_TEMPLATE = (
    "PASS: focused current contracts; {count} Java suites. "
    "Full matrix and independent oracles NOT RUN."
)
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."
FAILURE_LINE = re.compile(
    r"^\s*(?:FAIL(?:ED)?\b|ERROR\b|FATAL\b|Exception in thread\b|"
    r"AssertionError\b|BUILD FAILED\b|\[ERROR\]|CURRENT_CONTRACT_FAILURE:|"
    r"TIMEOUT\b|TIMED OUT\b|CURRENT_CONTRACT_TIMEOUT:|CANCELLED\b|"
    r"SKIPPED\b|IGNORED\b)", re.IGNORECASE)
SKIP_OR_FAILURE_WORD = re.compile(
    r"\b(?:SKIPPED?|IGNORED|TIMEOUT|TIMED\s+OUT|CANCELLED)\b", re.IGNORECASE)


class ReaderError(ValueError):
    """An input failed a frozen offline-reader gate."""


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


def _repo_file(relative, max_bytes=MAX_FILE_BYTES):
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
        raise ReaderError("repository input is unavailable or unsafe") from exc
    _need(len(data) <= max_bytes, "repository input grew beyond its bound")
    return data


def _task_file(name, max_bytes=MAX_FILE_BYTES):
    _need(type(name) is str and name and "/" not in name and "\\" not in name,
          "task artifact name is invalid")
    return _repo_file(TASK_REL + "/" + name, max_bytes)


def _load_module_without_cache(source, path, name):
    module = types.ModuleType(name)
    module.__file__ = str(path)
    module.__package__ = ""
    sys.modules[name] = module
    exec(compile(source.decode("utf-8", errors="strict"), str(path), "exec"),
         module.__dict__)
    return module


def _load_old_reader():
    source = _repo_file(OLD_READER_REL)
    _need(_sha(source) == OLD_READER_SHA, "pinned physical reader hash changed")
    path = REPO.joinpath(*PurePosixPath(OLD_READER_REL).parts)
    try:
        module = _load_module_without_cache(source, path,
                                            "pinned_routed_passive_physical_reader")
    except Exception as exc:
        raise ReaderError("pinned physical reader failed to load") from exc
    _need(_sha(source) == OLD_READER_SHA, "loaded physical reader differs from its pin")
    module.ReaderError = ReaderError
    if hasattr(module, "PRIOR"):
        module.PRIOR.ReaderError = ReaderError
    return module


OLD = _load_old_reader()
_typed_equal = OLD._typed_equal


def _strict_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ReaderError("duplicate JSON object key")
        result[key] = value
    return result


def _reject_constant(value):
    raise ReaderError("JSON contains a non-finite numeric constant")


def _parse_json(data, label):
    _need(type(data) is bytes, label + " bytes are missing")
    _need(len(data) <= MAX_FILE_BYTES, label + " exceeds its file bound")
    try:
        text = data.decode("utf-8", errors="strict")
        value = json.loads(text, object_pairs_hook=_strict_pairs,
                           parse_constant=_reject_constant)
    except ReaderError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, TypeError, ValueError) as exc:
        raise ReaderError(label + " is not strict JSON") from exc
    return value


def _require_sha(data, expected, label):
    _need(type(expected) is str and re.fullmatch(r"[0-9a-f]{64}", expected) and
          _sha(data) == expected, label + " hash changed")


def _canonical_seed(value, label):
    _need(type(value) is str and bool(value) and
          (value == "0" or not value.startswith("0")) and
          (value.startswith("-") or value.isdigit()),
          label + " is not canonical signed decimal text")
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ReaderError(label + " is not canonical signed decimal text") from exc
    _need(MIN_SIGNED_LONG <= parsed <= MAX_SIGNED_LONG and str(parsed) == value,
          label + " is outside the signed-long bound")
    return value


def _bounded_int(value, label, low=MIN_SIGNED_LONG, high=MAX_SIGNED_LONG):
    _need(type(value) is int and not isinstance(value, bool) and low <= value <= high,
          label + " is not a bounded integer")
    return value


def _bounded_text(value, label, allow_null=False):
    if allow_null and value is None:
        return value
    try:
        size = len(value.encode("utf-8", errors="strict")) if type(value) is str else -1
    except UnicodeEncodeError as exc:
        raise ReaderError(label + " is not strict UTF-8 text") from exc
    _need(type(value) is str and 0 < size <= MAX_FIELD_TEXT,
          label + " is not bounded text")
    return value


def _hex_digest(value, label):
    _need(type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value) is not None,
          label + " is not a lowercase SHA-256 digest")
    return value


def _validate_protocol(protocol):
    expected_keys = (
        "schema", "baseCommit", "scope", "roots", "version", "density", "phase",
        "summaryPrefix", "reportOrder", "summaryFields", "summaryStatuses",
        "sourceFields", "metricsFields", "sourceGenerationMetadata",
        "privateGenerationMetadata", "controlFields", "rowFields", "compactionFields",
        "aggregateFields", "proofFields", "feasibilityProofFields", "expectedMoves",
        "selectedBaselines", "physicalRowContract", "improvementRule",
        "areaReductionRule", "compactedRule", "sourceRule", "labelRule", "proofRule",
        "quietWhitespaceRule", "unitFixtureCounts", "unitEvaluators", "unitFixtureNames",
        "routePassDetail", "timingScope", "maxPhaseElapsedSeconds", "maxCaseMillis",
        "maxFileBytes", "publishedInputs", "classificationOwnerSha256",
        "suiteManifestSha256", "limits",
    )
    _need(type(protocol) is dict and tuple(protocol) == expected_keys,
          "slide compaction protocol schema/order changed")
    _need(protocol["schema"] == 1 and protocol["baseCommit"] ==
          "39edb4823ec9658b7f60f63c9b5fc7eb71de5db6" and
          protocol["scope"] ==
          "All31 independent selected-frame seed2 slides with matched unchanged compactor; seed5 control only; no selection or production change",
          "slide compaction protocol identity/scope changed")
    _need(_typed_equal(protocol["roots"], list(ROOTS)) and
          type(protocol["version"]) is int and protocol["version"] == VERSION and
          type(protocol["density"]) is int and protocol["density"] == 14 and
          protocol["phase"] == "SELECTED_AFTER_EDGE_TRIM" and
          protocol["summaryPrefix"] == SUMMARY_PREFIX and
          _typed_equal(protocol["reportOrder"],
                       [WHITESPACE_PREFIX, LEG_CASE_PREFIX, SUMMARY_PREFIX]),
          "slide compaction phase or marker identity changed")
    for key, expected in (
        ("summaryFields", SUMMARY_FIELDS), ("sourceFields", SOURCE_FIELDS),
        ("metricsFields", METRICS_FIELDS), ("controlFields", CONTROL_FIELDS),
        ("rowFields", ROW_FIELDS), ("compactionFields", COMPACTION_FIELDS),
        ("aggregateFields", AGGREGATE_FIELDS), ("proofFields", PROOF_FIELDS),
        ("feasibilityProofFields", FEASIBILITY_PROOF_FIELDS),
    ):
        _need(_typed_equal(protocol[key], list(expected)),
              "slide compaction " + key + " changed")
    _need(_typed_equal(protocol["summaryStatuses"],
                       {"5": "CONTROL_ONLY", "2": "PROBE_COMPLETE"}) and
          protocol["sourceGenerationMetadata"] == SOURCE_GENERATION and
          protocol["privateGenerationMetadata"] == PRIVATE_GENERATION,
          "slide compaction status or generation metadata changed")
    expected_moves = protocol["expectedMoves"]
    _need(type(expected_moves) is list and len(expected_moves) == 31,
          "slide compaction expected move count changed")
    move_fields = ("componentId", "dx", "dy", "analyticCopperDelta", "actualCopperDelta")
    for index, move in enumerate(expected_moves):
        _need(type(move) is dict and tuple(move) == move_fields,
              "slide compaction move schema changed at " + str(index))
        _bounded_text(move["componentId"], "expected move component")
        for field in move_fields[1:]:
            _bounded_int(move[field], "expected move " + field)
        _need(move["analyticCopperDelta"] == move["actualCopperDelta"],
              "expected move analytic/actual delta differs")
    _need(type(protocol["selectedBaselines"]) is dict and
          tuple(protocol["selectedBaselines"]) == ROOTS,
          "selected baseline root order changed")
    baseline_fields = (
        "status", "seed", "version", "density", "parts", "pads", "area", "width",
        "height", "largestProtectedEmptyRectangleArea", "copperManhattanLength", "vias",
        "geometrySha256", "sourcePlanSha256", "liveBoardGraphSha256", "sourceIntentSha256",
        "layoutSeed", "routingSeed", "selectedAttempt", "rankedPlacementAttempts",
        "acceptedPlanEvaluations", "placementRejections", "oneFaceAttempts",
        "twoLayerAttempts", "routeAttempts", "routingOrders", "routingExpansions",
        "selectedQualityMilli", "statisticsCanonical", "fixedMarginFloor",
        "accessEnvelopeMargin", "requiredMargin", "legacyRequestedMargin",
        "requestedMargin", "actualMargin", "occupiedX", "occupiedY", "occupiedWidth",
        "occupiedHeight", "outlineX", "outlineY", "excludedWorkbenchChrome", "elapsedMillis",
    )
    for seed in ROOTS:
        baseline = protocol["selectedBaselines"][seed]
        _need(type(baseline) is dict and tuple(baseline) == baseline_fields,
              "selected baseline schema changed for seed" + seed)
        _canonical_seed(baseline["seed"], "selected baseline seed")
        _need(baseline["seed"] == seed and baseline["version"] == VERSION and
              baseline["density"] == 14 and baseline["status"] == "PASS",
              "selected baseline identity changed for seed" + seed)
        for key in ("geometrySha256", "sourcePlanSha256", "liveBoardGraphSha256",
                    "sourceIntentSha256"):
            _hex_digest(baseline[key], "selected baseline " + key)
        _bounded_text(baseline["statisticsCanonical"], "selected statisticsCanonical")
        _bounded_text(baseline["excludedWorkbenchChrome"], "selected chrome exclusion")
        for key, value in baseline.items():
            if key not in ("status", "seed", "statisticsCanonical", "excludedWorkbenchChrome",
                           "geometrySha256", "sourcePlanSha256", "liveBoardGraphSha256",
                           "sourceIntentSha256", "rankedPlacementAttempts", "layoutSeed",
                           "routingSeed"):
                _bounded_int(value, "selected baseline " + key, 0, MAX_SIGNED_LONG)
        _canonical_seed(baseline["layoutSeed"], "selected baseline layoutSeed")
        _canonical_seed(baseline["routingSeed"], "selected baseline routingSeed")
        _need(type(baseline["rankedPlacementAttempts"]) is list and
              len(baseline["rankedPlacementAttempts"]) == 6 and
              all(type(item) is int and not isinstance(item, bool) for item in baseline["rankedPlacementAttempts"]),
              "selected baseline ranking is malformed")
    _need(_typed_equal(protocol["physicalRowContract"], {
        "readerPath": OLD_READER_REL, "readerSha256": OLD_READER_SHA,
        "protocolPath": OLD_PROTOCOL_REL, "protocolSha256": OLD_PROTOCOL_SHA,
        "adapter": "Use pinned _validate_row with selected source facts and published original structural source summary; do not apply preselection summary/source/first-message checks",
    }), "physical row adapter pin changed")
    _need(type(protocol["unitFixtureCounts"]) is dict and
          _typed_equal(protocol["unitFixtureCounts"], {
              "PcbRoutedPassiveLegCensusContractTest": 6,
              "PcbRoutedPassiveFeasibilityCensusContractTest": 6,
              "PcbRoutedSlideCompactionContractTest": 5,
          }) and _typed_equal(protocol["unitEvaluators"], {
              "PcbRoutedPassiveLegCensusContractTest": "endpointLegCensus",
              "PcbRoutedPassiveFeasibilityCensusContractTest": "physicalFeasibilityCensus",
              "PcbRoutedSlideCompactionContractTest": "selectedRoutedSlideCompaction",
          }), "unit evaluator contract changed")
    _need(_typed_equal(protocol["unitFixtureNames"], [
        "selectedPlanFrameAndMetadata", "freshPrivateLayoutAndStableIdentity",
        "strictImprovementComparator", "selectedObserverOwnershipAndFailureCleanup",
        "quietWhitespaceOwnerCleanup",
    ]), "unit fixture names changed")
    _need(protocol["routePassDetail"] == "roots=2 rows=2 evaluator=selectedRoutedSlideCompaction" and
          protocol["timingScope"] ==
          "Native selected routed slide compaction probe; unchanged per-JVM/search/physical limits" and
          protocol["maxPhaseElapsedSeconds"] == 36000 and
          protocol["maxCaseMillis"] == 60000 and
          protocol["maxFileBytes"] == MAX_FILE_BYTES,
          "slide compaction timing bounds changed")
    _need(_typed_equal(protocol["publishedInputs"], PUBLISHED_INPUTS) and
          _typed_equal(protocol["suiteManifestSha256"], SUITE_MANIFEST_SHA) and
          type(protocol["classificationOwnerSha256"]) is dict and
          len(protocol["classificationOwnerSha256"]) == 7,
          "slide compaction input/source pins changed")
    _need(type(protocol["limits"]) is list and len(protocol["limits"]) == 5 and
          all(type(item) is str and item for item in protocol["limits"]),
          "slide compaction limits changed")


def _load_contract(protocol_override=None, published_overrides=None):
    protocol_data = (_task_file("protocol.json", 256 * 1024)
                     if protocol_override is None else protocol_override)
    _require_sha(protocol_data, PROTOCOL_SHA, "sealed slide compaction protocol")
    protocol = _parse_json(protocol_data, "slide compaction protocol")
    _validate_protocol(protocol)
    overrides = {} if published_overrides is None else published_overrides
    _need(type(overrides) is dict and
          all(type(key) is str and type(value) is bytes for key, value in overrides.items()) and
          set(overrides).issubset(PUBLISHED_INPUTS), "published input overrides are malformed")
    blobs = {}
    for path, digest in PUBLISHED_INPUTS.items():
        data = overrides[path] if path in overrides else _repo_file(path)
        _require_sha(data, digest, "published input " + path)
        blobs[path] = data
    _require_sha(blobs[OLD_READER_REL], OLD_READER_SHA, "physical reader source")
    _require_sha(blobs[OLD_PROTOCOL_REL], OLD_PROTOCOL_SHA, "physical reader protocol")
    return {"protocol": protocol, "protocolData": protocol_data, "published": blobs}


def _parse_published_old_source(contract):
    raw = contract["published"]["docs/task-evidence/PCB/routed-passive-feasibility-census-r1/routes.txt"]
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ReaderError("published feasibility routes are not strict UTF-8") from exc
    rows = []
    for line in text.splitlines():
        if line.startswith(OLD_FEASIBILITY_PREFIX):
            row = _parse_json(line[len(OLD_FEASIBILITY_PREFIX):].encode("utf-8"),
                               "published feasibility summary")
            _need(type(row) is dict, "published feasibility summary is not an object")
            rows.append(row)
    _need(tuple(row.get("seed") for row in rows) == ROOTS,
          "published feasibility source rows lack exact root order")
    source = rows[1].get("source")
    _need(type(source) is dict and tuple(source) == tuple(OLD.SOURCE_FIELDS),
          "published seed2 feasibility source schema changed")
    _need(source["traceProvenanceHash"] == OLD_SEED2_TRACE_PROVENANCE and
          source["reachabilityHash"] == EXPECTED_REACHABILITY and
          source["traceCount"] == 145 and source["sourceRouteSourceCount"] == 145 and
          source["padPairCount"] == 9045,
          "published seed2 trace proof anchor changed")
    return source


def _load_prior_contract(contract):
    try:
        old_contract = OLD._load_contract()
        old_prior = OLD._load_prior_contract(old_contract)
    except ReaderError:
        raise
    except (ValueError, OSError) as exc:
        raise ReaderError("pinned old physical boundary failed to load") from exc
    _need(_sha(old_contract["protocolData"]) == OLD_PROTOCOL_SHA,
          "pinned old protocol hash changed")
    _need(tuple(old_prior["selected"]) == (("5", VERSION), ("2", VERSION)) and
          tuple(old_prior["whitespace"]) == (("5", VERSION), ("2", VERSION)),
          "pinned selected old cases/whitespace lack exact root order")
    source = _parse_published_old_source(contract)
    for seed in ROOTS:
        actual = old_prior["selected"][(seed, VERSION)]
        expected = contract["protocol"]["selectedBaselines"][seed]
        a, b = dict(actual), dict(expected)
        a.pop("elapsedMillis")
        b.pop("elapsedMillis")
        _need(_typed_equal(a, b), "selected baseline differs from pinned old case for seed" + seed)
    return {
        "old": OLD,
        "oldContract": old_contract,
        "oldProtocol": old_contract["protocol"],
        "selected": old_prior["selected"],
        "whitespace": old_prior["whitespace"],
        "publishedCensusSummaries": old_prior["publishedCensusSummaries"],
        "publishedCensusCases": old_prior["publishedCensusCases"],
        "publishedPoolSource": old_prior["publishedPoolSource"],
        "publishedFeasibilitySource": source,
    }


def _load_suite_manifest(phase, protocol, raw_override=None):
    _need(phase in ("unit", "routes"), "unknown native phase")
    name = "unit-suites.json" if phase == "unit" else "routes-suites.json"
    expected = (
        [["PcbRoutedPassiveLegCensusContractTest", "PCB routed passive leg census contracts "],
         ["PcbRoutedPassiveFeasibilityCensusContractTest", "PCB routed passive feasibility census contracts "],
         ["PcbRoutedSlideCompactionContractTest", "PCB routed slide compaction contracts "]]
        if phase == "unit" else
        [["PcbRoutedSlideCompactionTrialContractTest", "PCB routed slide compaction trial contracts "]]
    )
    raw = _task_file(name, 64 * 1024) if raw_override is None else raw_override
    _require_sha(raw, SUITE_MANIFEST_SHA[name], "sealed " + phase + " suite manifest")
    actual = _parse_json(raw, phase + " suite manifest")
    _need(_typed_equal(actual, expected), phase + " suite manifest order/content changed")
    return actual, _sha(raw)


def _parse_report_lines(text, protocol):
    _need(type(text) is str, "native phase text is missing")
    try:
        _need(len(text.encode("utf-8", errors="strict")) <= protocol["maxFileBytes"],
              "native phase text exceeds its bound")
    except UnicodeEncodeError as exc:
        raise ReaderError("native phase text is not strict UTF-8") from exc
    prefixes = (WHITESPACE_PREFIX, LEG_CASE_PREFIX, protocol["summaryPrefix"])
    reports = []
    for line in text.splitlines():
        if not line.startswith("PCB_"):
            continue
        prefix = next((candidate for candidate in prefixes if line.startswith(candidate)), None)
        _need(prefix is not None, "unknown native PCB report prefix")
        row = _parse_json(line[len(prefix):].encode("utf-8"), "native report")
        _need(type(row) is dict, "native report must be an object")
        reports.append((prefix, row, line))
        _need(len(reports) <= MAX_REPORTS, "native report count exceeds bound")
    return reports


def _validate_whitespace(row, seed, prior):
    expected = prior["selected"][(seed, VERSION)]
    try:
        prior["old"].PRIOR._whitespace_case(row, expected, seed, VERSION)
    except ReaderError:
        raise
    except ValueError as exc:
        raise ReaderError(str(exc)) from exc
    _need(_typed_equal(row, prior["whitespace"][(seed, VERSION)]),
          "whitespace report differs from pinned accepted15 row")


def _validate_case_report(row, seed, contract, prior):
    expected = prior["selected"][(seed, VERSION)]
    try:
        prior["old"].PRIOR._validate_selected_case(row, expected, seed,
                                                    prior["oldProtocol"])
    except ReaderError:
        raise
    except ValueError as exc:
        raise ReaderError(str(exc)) from exc
    actual, baseline = dict(row), dict(contract["protocol"]["selectedBaselines"][seed])
    actual.pop("elapsedMillis")
    baseline.pop("elapsedMillis")
    _need(_typed_equal(actual, baseline), "selected old case differs from sealed baseline")


def _validate_metrics(metrics, seed, protocol, source_metrics=None, generation=None):
    _need(type(metrics) is dict and tuple(metrics) == METRICS_FIELDS,
          "slide compaction metrics schema/order changed")
    for key in ("area", "copperLength", "vias", "largestProtectedEmptyRectangleArea"):
        _bounded_int(metrics[key], "metrics " + key, 0, MAX_COUNT * 100)
    _need(metrics["area"] > 0 and metrics["copperLength"] > 0,
          "metrics area and copperLength must be positive")
    _need(metrics["largestProtectedEmptyRectangleArea"] <= metrics["area"],
          "metrics protected empty rectangle exceeds area")
    for key in ("geometrySha256", "stableIdentitySha256", "labelIdentitySha256"):
        _hex_digest(metrics[key], "metrics " + key)
    _need(metrics["generationMetadata"] == generation, "metrics generation metadata changed")
    if source_metrics is not None:
        _need(metrics["stableIdentitySha256"] == source_metrics["stableIdentitySha256"] and
              metrics["labelIdentitySha256"] == source_metrics["labelIdentitySha256"],
              "stable or label identity digest differs from selected source")
    return metrics


def _pair_count(pads):
    return pads * (pads - 1) // 2


def _validate_source(source, seed, contract, prior):
    protocol = contract["protocol"]
    _need(type(source) is dict and tuple(source) == SOURCE_FIELDS,
          "selected source schema/order changed")
    for key in ("liveGraphBefore", "liveGraphAfter", "geometryBefore", "geometryAfter",
                "traceProvenanceHash", "reachabilityHash"):
        _hex_digest(source[key], "source " + key)
    baseline = protocol["selectedBaselines"][seed]
    _need(source["liveGraphBefore"] == source["liveGraphAfter"] == baseline["liveBoardGraphSha256"] and
          source["geometryBefore"] == source["geometryAfter"] == baseline["geometrySha256"] and
          source["copperLength"] == baseline["copperManhattanLength"] and
          source["area"] == baseline["area"] and
          source["componentCount"] == baseline["parts"] and
          source["padCount"] == baseline["pads"] and
          source["sourceRouteSourceCount"] == source["traceCount"] and
          source["padPairCount"] == _pair_count(baseline["pads"]),
          "selected source graph/geometry/metrics changed")
    for key in ("copperLength", "area", "componentCount", "padCount", "traceCount",
                "sourceRouteSourceCount", "padPairCount"):
        _bounded_int(source[key], "source " + key, 0, MAX_COUNT * 100)
    _need(source["traceCount"] > 0, "selected source has no route trace count")
    if seed == "2":
        _need(source["traceCount"] == 145 and source["reachabilityHash"] == EXPECTED_REACHABILITY and
              source["traceProvenanceHash"] == prior["publishedFeasibilitySource"]["traceProvenanceHash"],
              "seed2 selected source proof anchor changed")
    return source


def _validate_proof(proof, source, protocol, prefix, final=True):
    fields = PROOF_FIELDS if final else FEASIBILITY_PROOF_FIELDS
    _need(type(proof) is dict and tuple(proof) == fields,
          prefix + " proof schema/order changed")
    flags = ("placementValidated", "geometryValidated", "twoLayerValidated",
             "conductorReachabilityVerified", "traceProvenanceVerified")
    if final:
        flags += ("routingValidated", "routeQualityValidated", "traceClearanceValidated",
                  "stableIdentityVerified", "labelIdentityVerified", "sourceImmutable")
    else:
        flags += ("traceEndpointOnlyVerified", "unrelatedGeometryVerified")
    for key in flags:
        _need(type(proof[key]) is bool and proof[key] is True,
              prefix + " proof flag is not strict true")
    for key in ("padPairCount", "sourceRouteSourceCount", "candidateRouteSourceCount"):
        _bounded_int(proof[key], prefix + " " + key, 0, MAX_COUNT * 100)
    _need(proof["padPairCount"] == _pair_count(source["padCount"]) and
          proof["sourceRouteSourceCount"] == source["sourceRouteSourceCount"] and
          proof["candidateRouteSourceCount"] == source["sourceRouteSourceCount"],
          prefix + " proof counts changed")
    for key in ("sourceReachabilityHash", "candidateReachabilityHash",
                "sourceTraceProvenanceHash", "candidateTraceProvenanceHash"):
        _hex_digest(proof[key], prefix + " " + key)
    _need(proof["sourceTraceProvenanceHash"] == proof["candidateTraceProvenanceHash"] ==
          source["traceProvenanceHash"], prefix + " provenance differs from source")
    if source["padCount"] == 135:
        _need(proof["sourceReachabilityHash"] == proof["candidateReachabilityHash"] ==
              EXPECTED_REACHABILITY, prefix + " seed2 reachability hash changed")
    else:
        _need(proof["sourceReachabilityHash"] == proof["candidateReachabilityHash"] ==
              source["reachabilityHash"], prefix + " seed5 reachability hashes differ")


def _validate_control(control, source, source_metrics, seed, protocol):
    _need(type(control) is dict and tuple(control) == CONTROL_FIELDS,
          "control schema/order changed")
    _need(type(control["compacted"]) is bool and type(control["sourceImmutable"]) is bool and
          control["sourceImmutable"] is True, "control flags are not strict")
    metrics = _validate_metrics(control["metrics"], seed, protocol, source_metrics,
                                PRIVATE_GENERATION)
    _validate_proof(control["proof"], source, protocol, "control", final=True)
    return control, metrics


def _validate_feasibility_row(feasibility, index, source, prior, protocol):
    _need(type(feasibility) is dict, "row feasibility is missing")
    try:
        prior["old"]._validate_row(
            feasibility, index, source, prior["publishedCensusSummaries"]["2"],
            prior["oldProtocol"],
        )
    except ReaderError:
        raise
    except ValueError as exc:
        raise ReaderError(str(exc)) from exc
    _need(tuple(feasibility) == tuple(prior["old"].ROW_FIELDS),
          "embedded physical row schema differs from pinned old schema")
    return feasibility


def _strict_improvement(metrics, source_metrics, control_metrics):
    return (
        metrics["area"] < source_metrics["area"] and
        metrics["area"] < control_metrics["area"] and
        metrics["copperLength"] <= source_metrics["copperLength"] and
        metrics["copperLength"] <= control_metrics["copperLength"] and
        metrics["vias"] <= source_metrics["vias"] and
        metrics["vias"] <= control_metrics["vias"] and
        metrics["largestProtectedEmptyRectangleArea"] <=
        source_metrics["largestProtectedEmptyRectangleArea"] and
        metrics["largestProtectedEmptyRectangleArea"] <=
        control_metrics["largestProtectedEmptyRectangleArea"]
    )


def _validate_compaction(compaction, source, source_metrics, control_metrics, seed, protocol):
    _need(type(compaction) is dict and tuple(compaction) == COMPACTION_FIELDS,
          "compaction schema/order changed")
    _need(type(compaction["compacted"]) is bool and
          type(compaction["sourceImmutable"]) is bool and
          compaction["sourceImmutable"] is True and
          type(compaction["strictImprovement"]) is bool,
          "compaction flags are not strict")
    metrics = _validate_metrics(compaction["metrics"], seed, protocol, source_metrics,
                                PRIVATE_GENERATION)
    _validate_proof(compaction["proof"], source, protocol, "compaction", final=True)
    expected_strict = _strict_improvement(metrics, source_metrics, control_metrics)
    _need(compaction["strictImprovement"] is expected_strict,
          "strict improvement flag is not independently derived")
    return compaction, metrics


def _validate_summary(summary, seed, contract, prior):
    protocol = contract["protocol"]
    _need(type(summary) is dict and tuple(summary) == SUMMARY_FIELDS,
          "slide compaction summary schema/order changed")
    _need(_canonical_seed(summary["seed"], "summary seed") == seed and
          summary["label"] == "seed" + seed and type(summary["label"]) is str and
          type(summary["version"]) is int and not isinstance(summary["version"], bool) and
          summary["version"] == VERSION and summary["phase"] == protocol["phase"] and
          summary["selectedRoute"] == SELECTED_ROUTE and
          type(summary["newRouteRequests"]) is int and summary["newRouteRequests"] == 0 and
          type(summary["selectedMutation"]) is bool and summary["selectedMutation"] is False,
          "summary identity/selection boundary changed")
    baseline = protocol["selectedBaselines"][seed]
    _need(type(summary["selectedAttempt"]) is int and
          summary["selectedAttempt"] == baseline["selectedAttempt"],
          "summary selected attempt differs from sealed baseline")
    _bounded_int(summary["elapsedMillis"], "summary elapsedMillis", 0,
                 protocol["maxCaseMillis"])
    source = _validate_source(summary["source"], seed, contract, prior)
    source_metrics = _validate_metrics(summary["sourceMetrics"], seed, protocol, None,
                                       SOURCE_GENERATION)
    _need(source_metrics["area"] == baseline["area"] and
          source_metrics["copperLength"] == baseline["copperManhattanLength"] and
          source_metrics["vias"] == baseline["vias"] and
          source_metrics["largestProtectedEmptyRectangleArea"] ==
          baseline["largestProtectedEmptyRectangleArea"] and
          source_metrics["geometrySha256"] == source["geometryBefore"],
          "source metrics differ from selected baseline")
    control, control_metrics = _validate_control(summary["control"], source,
                                                 source_metrics, seed, protocol)
    if seed == "5":
        _need(summary["status"] == protocol["summaryStatuses"][seed] and
              summary["rows"] == [] and
              _typed_equal(summary["aggregate"], {
                  "structuralDirections": 0, "validatedMoves": 0,
                  "rejectedMoves": 0, "compactions": 0,
                  "areaReductions": 0, "strictImprovements": 0,
              }), "seed5 control-only summary changed")
        return {"seed": seed, "status": summary["status"], "aggregate": summary["aggregate"]}
    _need(summary["status"] == protocol["summaryStatuses"][seed] and
          type(summary["rows"]) is list and len(summary["rows"]) == 31,
          "seed2 probe row count/status changed")
    observed = []
    for index, row in enumerate(summary["rows"]):
        _need(type(row) is dict and tuple(row) == ROW_FIELDS,
              "slide compaction row schema/order changed")
        feasibility = _validate_feasibility_row(row["feasibility"], index, source, prior, protocol)
        disposition = feasibility["disposition"]
        if disposition == prior["old"].DISPOSITION_REJECTED:
            _need(row["compaction"] is None, "physically rejected row has compaction proof")
            observed.append({"validated": False, "area": False, "strict": False})
        elif disposition == prior["old"].DISPOSITION_VALIDATED:
            _need(row["compaction"] is not None, "physically validated row lacks compaction proof")
            compaction, metrics = _validate_compaction(
                row["compaction"], source, source_metrics, control_metrics, seed, protocol)
            observed.append({
                "validated": True,
                "area": metrics["area"] < source_metrics["area"] and metrics["area"] < control_metrics["area"],
                "strict": compaction["strictImprovement"],
            })
        else:
            raise ReaderError("embedded physical row disposition is outside the pinned catalog")
    counts = {
        "structuralDirections": 31,
        "validatedMoves": sum(item["validated"] for item in observed),
        "rejectedMoves": sum(not item["validated"] for item in observed),
        "compactions": sum(item["validated"] for item in observed),
        "areaReductions": sum(item["area"] for item in observed),
        "strictImprovements": sum(item["strict"] for item in observed),
    }
    _need(_typed_equal(summary["aggregate"], counts),
          "slide compaction aggregate does not match all31 rows")
    _need(summary["elapsedMillis"] >= sum(
        row["feasibility"]["timing"]["totalMillis"] for row in summary["rows"]),
          "slide compaction summary elapsed time does not cover physical rows")
    return {"seed": seed, "status": summary["status"], "aggregate": counts}


def _assess_routes(reports, contract, prior):
    protocol = contract["protocol"]
    _need(type(reports) is list and len(reports) == 6,
          "routes phase requires exactly six ordered PCB report rows")
    summaries = []
    cursor = 0
    for seed in ROOTS:
        _need(reports[cursor][0] == WHITESPACE_PREFIX and
              reports[cursor][1].get("seed") == seed,
              "whitespace report is missing or out of order")
        _validate_whitespace(reports[cursor][1], seed, prior)
        cursor += 1
        _need(reports[cursor][0] == LEG_CASE_PREFIX and
              reports[cursor][1].get("seed") == seed,
              "baseline routed-leg case is missing or out of order")
        _validate_case_report(reports[cursor][1], seed, contract, prior)
        cursor += 1
        _need(reports[cursor][0] == protocol["summaryPrefix"] and
              reports[cursor][1].get("seed") == seed,
              "slide compaction summary is missing or out of order")
        summaries.append(_validate_summary(reports[cursor][1], seed, contract, prior))
        cursor += 1
    _need(cursor == len(reports), "extra or foreign slide compaction reports remain")
    return {
        "status": "PASS", "scope": protocol["scope"], "roots": 2, "rows": 2,
        "oldBaselineReports": 4, "slideSummaries": summaries,
        "routeReports": 6,
        "observationDisposition": "PASS; selected-frame compaction observation only",
        "fitnessDisposition": "NOT_EVALUATED",
    }


def _unit_pass_line(line, suite, protocol):
    expected_prefix = "PASS: " + suite[1]
    match = re.fullmatch(
        re.escape(expected_prefix) + r"assertions=([1-9][0-9]*) fixtures=" +
        str(protocol["unitFixtureCounts"][suite[0]]) + r" evaluator=" +
        re.escape(protocol["unitEvaluators"][suite[0]]), line)
    _need(match is not None, "unit PASS marker/count/evaluator is malformed for " + suite[0])


def _phase_summary(text, receipt, timing, suites, phase, protocol, prior):
    _need(type(timing) is dict and tuple(timing) == TIMING_FIELDS,
          "native timing schema/order changed")
    _need(phase in ("unit", "routes") and timing["phase"] == phase,
          "native phase differs from requested phase")
    expected_suites, _manifest_sha = _load_suite_manifest(phase, protocol)
    _need(_typed_equal(suites, expected_suites), "native suite rows differ from pinned order")
    _need(timing["status"] == "PASS" and type(timing["status"]) is str and
          type(timing["exitCode"]) is int and timing["exitCode"] == 0 and
          type(timing["suiteCount"]) is int and timing["suiteCount"] == len(expected_suites),
          "native phase status/exit/suite count failed")
    elapsed = timing["elapsedSeconds"]
    _need(type(elapsed) in (int, float) and not isinstance(elapsed, bool) and
          math.isfinite(elapsed) and 0 <= elapsed <= protocol["maxPhaseElapsedSeconds"],
          "native phase elapsed time is malformed or outside its bound")
    _need(timing["scope"] == protocol["timingScope"] and
          type(timing["terminalCleanupIncluded"]) is bool and
          timing["terminalCleanupIncluded"] is True and
          timing["cleanupDuration"] == "NOT_SEPARATELY_MEASURED",
          "native phase scope or cleanup boundary changed")
    _need(type(text) is str and type(receipt) is str, "native phase log/receipt is missing")
    try:
        text_size = len(text.encode("utf-8", errors="strict"))
        receipt_size = len(receipt.encode("utf-8", errors="strict"))
    except UnicodeEncodeError as exc:
        raise ReaderError("native phase log/receipt is not strict UTF-8") from exc
    _need(text_size <= protocol["maxFileBytes"] and receipt_size <= protocol["maxFileBytes"],
          "native phase log/receipt exceeds its bound")
    text_lines, receipt_lines = text.splitlines(), receipt.splitlines()
    _need(not any(FAILURE_LINE.search(line) or SKIP_OR_FAILURE_WORD.search(line)
                  for line in text_lines + receipt_lines),
          "native phase contains failure, timeout, skip, or cancellation output")
    terminal = TERMINAL_TEMPLATE.format(count=len(expected_suites))
    _need(text_lines.count(terminal) == 1 and text_lines.count(CLEANUP_LINE) == 1,
          "native terminal PASS or cleanup marker is missing/duplicated")
    _need(terminal not in receipt_lines and CLEANUP_LINE not in receipt_lines and
          text_lines.index(terminal) < text_lines.index(CLEANUP_LINE),
          "native receipt or terminal cleanup boundary changed")
    suite_passes = [line for line in text_lines if line.startswith("PASS: ") and line != terminal]
    receipt_passes = [line for line in receipt_lines if line.startswith("PASS: ")]
    _need(suite_passes == receipt_passes and len(suite_passes) == len(expected_suites),
          "suite PASS lines differ between terminal log and child receipt")
    for line, suite in zip(suite_passes, expected_suites):
        if phase == "unit":
            _unit_pass_line(line, suite, protocol)
        else:
            _need(line == "PASS: " + suite[1] + protocol["routePassDetail"],
                  "routes PASS detail/order differs from frozen contract")
        _need(text_lines.index(line) < text_lines.index(terminal),
              "suite PASS appeared after terminal marker")
    text_reports = _parse_report_lines(text, protocol)
    receipt_reports = _parse_report_lines(receipt, protocol)
    _need([item[2] for item in text_reports] == [item[2] for item in receipt_reports],
          "native report lines differ between terminal log and child receipt")
    terminal_position = text_lines.index(terminal)
    _need(all(position < terminal_position for position, line in enumerate(text_lines)
              if line.startswith("PCB_")), "native report appeared after terminal marker")
    if phase == "unit":
        _need(not text_reports and not receipt_reports, "unit contract leaked PCB reports")
        result = None
    else:
        report_positions = [position for position, line in enumerate(text_lines) if line.startswith("PCB_")]
        suite_positions = [text_lines.index(line) for line in suite_passes]
        _need(report_positions and max(report_positions) < min(suite_positions),
              "routes suite PASS appeared before all PCB reports")
        receipt_report_positions = [position for position, line in enumerate(receipt_lines)
                                    if line.startswith("PCB_")]
        receipt_suite_positions = [receipt_lines.index(line) for line in receipt_passes]
        _need(receipt_report_positions and max(receipt_report_positions) < min(receipt_suite_positions),
              "routes child receipt PASS appeared before all PCB reports")
        result = _assess_routes(text_reports, {"protocol": protocol}, prior)
    return {
        "status": "PASS", "phase": phase, "suiteCount": len(expected_suites),
        "suiteClasses": [row[0] for row in expected_suites],
        "reportRows": len(text_reports),
        "orderedMarkers": [row[1] for row in expected_suites],
        "slideCompaction": result,
    }


def check_phase(text, receipt, timing, suites, phase):
    contract = _load_contract()
    prior = _load_prior_contract(contract)
    actual_suites, _manifest_sha = _load_suite_manifest(phase, contract["protocol"])
    _need(_typed_equal(suites, actual_suites), "caller suite manifest differs from pinned bytes")
    return _phase_summary(text, receipt, timing, suites, phase, contract["protocol"], prior)


def _read_phase_files(phase, contract, prior):
    protocol = contract["protocol"]
    names = (phase + ".txt", phase + "-receipt.txt", phase + "-timing.json")
    raw = {name: _task_file(name, protocol["maxFileBytes"]) for name in names}
    try:
        text = raw[names[0]].decode("utf-8", errors="strict")
        receipt = raw[names[1]].decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ReaderError("native phase log/receipt is not strict UTF-8") from exc
    timing = _parse_json(raw[names[2]], "native phase timing")
    suites, manifest_sha = _load_suite_manifest(phase, protocol)
    summary = _phase_summary(text, receipt, timing, suites, phase, protocol, prior)
    return summary, {name: _sha(data) for name, data in raw.items()}, manifest_sha


def _unit_validation(contract, summary, phase_inputs, manifest_sha):
    protocol = contract["protocol"]
    return {
        "schema": 1, "status": "PASS", "phase": "unit",
        "scope": "Selected-frame routed slide compaction unit fixtures only; native routes and fitness are not evaluated.",
        "baseCommit": protocol["baseCommit"],
        "protocolSha256": _sha(contract["protocolData"]),
        "physicalReaderSha256": OLD_READER_SHA,
        "publishedInputsSha256": {name: _sha(data) for name, data in contract["published"].items()},
        "suiteManifestSha256": manifest_sha, "phaseInputsSha256": phase_inputs,
        "phaseEvidence": summary, "fixtureCounts": dict(protocol["unitFixtureCounts"]),
        "nativeRuntimeEvidence": "PASS",
    }


def _validate_unit_prerequisite(data, expected):
    actual = _parse_json(data, "unit prerequisite")
    _need(_typed_equal(actual, expected),
          "routes phase lacks a fresh matching current unit-validation PASS prerequisite")
    return _sha(data)


def _routes_assessment(contract, summary, phase_inputs, manifest_sha, unit_prerequisite_sha):
    protocol = contract["protocol"]
    _need(type(summary.get("slideCompaction")) is dict and
          summary["slideCompaction"].get("status") == "PASS",
          "selected-frame slide compaction observation is missing or failed")
    return {
        "schema": 1, "assessmentStatus": "PASS", "scope": protocol["scope"],
        "baseCommit": protocol["baseCommit"],
        "protocolSha256": _sha(contract["protocolData"]),
        "physicalReaderSha256": OLD_READER_SHA,
        "publishedInputsSha256": {name: _sha(data) for name, data in contract["published"].items()},
        "unitPrerequisiteSha256": unit_prerequisite_sha,
        "routesSuiteManifestSha256": manifest_sha,
        "routePhaseInputsSha256": phase_inputs,
        "nativePhase": {"status": "PASS", "phase": "routes", "evidence": summary},
        "observationDisposition": "PASS; selected-frame compaction observation only",
        "fitnessDisposition": "NOT_EVALUATED",
        "policyDisposition": "NO_POLICY_OR_ACCEPTANCE_CHANGE",
        "productionChange": "NONE", "limits": list(protocol["limits"]),
    }


def _write_artifact(name, value):
    target = HERE / name
    _need(not target.exists() and not target.is_symlink(),
          "refusing to overwrite existing task artifact: " + name)
    payload = (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode("utf-8")
    try:
        with target.open("xb") as stream:
            stream.write(payload)
    except FileExistsError as exc:
        raise ReaderError("refusing to overwrite existing task artifact: " + name) from exc
    return _sha(payload)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("unit", "routes"))
    args = parser.parse_args(argv)
    try:
        contract = _load_contract()
        prior = _load_prior_contract(contract)
        summary, phase_inputs, manifest_sha = _read_phase_files(args.phase, contract, prior)
        if args.phase == "unit":
            output = _unit_validation(contract, summary, phase_inputs, manifest_sha)
            _write_artifact("unit-validation.json", output)
            print("PASS unit phase; routes and fitness are not evaluated")
            return 0
        unit_data = _task_file("unit-validation.json", 1024 * 1024)
        unit_summary, unit_inputs, unit_manifest_sha = _read_phase_files("unit", contract, prior)
        expected_unit = _unit_validation(contract, unit_summary, unit_inputs, unit_manifest_sha)
        prerequisite_sha = _validate_unit_prerequisite(unit_data, expected_unit)
        output = _routes_assessment(contract, summary, phase_inputs, manifest_sha, prerequisite_sha)
        _write_artifact("assessment.json", output)
        print("PASS routes phase; observation PASS; fitness NOT_EVALUATED")
        return 0
    except ReaderError as exc:
        print("FAIL offline reader: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
