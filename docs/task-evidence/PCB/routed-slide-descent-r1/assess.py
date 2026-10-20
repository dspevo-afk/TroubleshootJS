"""Strict offline reader for the selected-frame routed slide probe."""

import argparse
import copy
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
TASK_REL = "docs/task-evidence/PCB/routed-slide-descent-r1"
OLD_READER_REL = "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/assess.py"
OLD_READER_SHA = "c03cfd51dc2a93612a94438347b1a5fbbe92c9191a472e2d0b00efc749a4476e"
OLD_PROTOCOL_REL = "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/protocol.json"
OLD_PROTOCOL_SHA = "88123f76e526d42dce97bb656eda8ffdf4479ca947052cf819738e8e79aa93cb"
PROTOCOL_SHA = "e047c6b87e72538b7b161449b7938b80c8e1ec9c081bea27ba40c26d3c4b73ba"

PUBLISHED_INPUTS = {
    "docs/task-evidence/PCB/passive-local-pose-choice-r1/accepted15-baselines.json":
        "12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816",
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/protocol.json":
        "6982e4e88969f599c3c859ed8bb9fc2e67946f24c9f5b0453c7e705231102d30",
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
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/assess.py":
        "4cf7a9311de3252b6f3f04ca74a5a3945b3aa80fea71ac2f35ebfb962f417c1a",
    "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/native-inputs.json":
        "a4433f982b2453c976e414a7bf873f0a95ebbf18d218e154adbcb125f66fcb64",
    "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/root-review.json":
        "f815c2db92af515df7a79b1813f25730f2892acab045519d742ebfffcb3db342",
    "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/routes.txt":
        "e19b55230786618773216b781a5f0277e02daa29487ecd8601484a2e10567c03",
    "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/assessment.json":
        "1b565131398e2df611457ebbee3a89ab20cdd210959f64602218b79252ba335b",
    "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/compaction-analysis.json":
        "714eca2aef1905f3d2fb4412f747f32ff93c91302115e54b14f6c4cc8e503845",
    OLD_READER_REL: OLD_READER_SHA,
}
SUITE_MANIFEST_SHA = {
    "unit-suites.json": "26267a6a820ac5d09adfe331c8255209efa43beb6005b0745224bcedc57cf56a",
    "routes-suites.json": "3e1ffb21131fa7c0270b66e6a8bf1e7cd516d379a80f0c72609ab48d39138f66",
}

WHITESPACE_PREFIX = "PCB_WHITESPACE_CASE "
LEG_CASE_PREFIX = "PCB_ROUTED_LEG_CASE "
SUMMARY_PREFIX = "PCB_ROUTED_SLIDE_DESCENT_CASE "
ROOTS = ("5", "2")
VERSION = 15
MAX_SIGNED_LONG = (1 << 63) - 1
MIN_SIGNED_LONG = -(1 << 63)
MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_REPORTS = 100000
MAX_FIELD_TEXT = 1024 * 1024
MAX_COUNT = 1000000
EXPECTED_REACHABILITY = "9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7"
SELECTED_ROUTE = "P07_FULLER_TWO_LAYER"
SOURCE_GENERATION = "ACCEPTED_SELECTED"
PRIVATE_GENERATION = "UNASSIGNED_PRIVATE_CANDIDATE"
PROTOCOL_ROUTE_DETAIL = "roots=2 rows=2 evaluator=selectedRoutedSlideDescent"

SUMMARY_FIELDS = (
    "seed", "label", "version", "phase", "status", "selectedAttempt",
    "selectedRoute", "newRouteRequests", "selectedMutation", "limits",
    "source", "sourceMetrics", "initialControl", "steps", "finalMetrics",
    "aggregate", "elapsedMillis",
)
STEP_FIELDS = ("index", "source", "sourceMetrics", "control", "structural",
               "rows", "acceptedRowIndex")
STRUCTURAL_FIELDS = ("geometryHashBefore", "geometryHashAfter", "parts")
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
    "steps", "acceptedSteps", "evaluatedMoves", "validatedMoves",
    "rejectedMoves", "compactions",
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




# The definitions below are the descent reader.  The copied compaction reader
# above is retained only as a pinned implementation source; these overrides
# keep the new contract independent from its fixed single-step/31-row shape.
PHYS = OLD.OLD
LEG = PHYS.PRIOR
PUBLISHED_INPUTS = {key: PUBLISHED_INPUTS[key] for key in (
    "docs/task-evidence/PCB/passive-local-pose-choice-r1/accepted15-baselines.json",
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/protocol.json",
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/native-inputs.json",
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/root-review.json",
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/routes.txt",
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/assessment.json",
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/feasibility-analysis.json",
    "docs/task-evidence/PCB/routed-passive-feasibility-census-r1/assess.py",
    OLD_PROTOCOL_REL,
    "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/native-inputs.json",
    "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/root-review.json",
    "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/routes.txt",
    "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/assessment.json",
    OLD_READER_REL,
    "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/compaction-analysis.json",
)}
DESC_PROTOCOL_KEYS = (
    "schema", "baseCommit", "scope", "roots", "version", "density", "phase",
    "summaryPrefix", "reportOrder", "summaryFields", "limits", "structuralLimits",
    "statuses", "stepFields", "structuralFields",
    "sourceFields", "metricsFields", "controlFields", "rowFields",
    "compactionFields", "proofFields", "feasibilityProofFields",
    "sourceGenerationMetadata", "privateGenerationMetadata", "aggregateFields",
    "improvementRule", "selectionRule", "terminationRule", "chainRule",
    "proofRule", "structuralRule", "selectedBaselines", "priorContract",
    "unitFixtureCounts", "unitEvaluators", "unitFixtureNames", "routePassDetail",
    "timingScope", "maxPhaseElapsedSeconds", "maxCaseMillis", "maxFileBytes",
    "publishedInputs", "classificationOwnerSha256", "suiteManifestSha256",
    "limitsDisclosure",
)
DESC_LIMITS = {"maxAcceptedSteps": 4, "maxEvaluatedMoves": 128}
DESC_STRUCTURAL_LIMITS = {
    "maxParts": 128, "maxDirections": 512,
    "directions": [[-10, 0], [10, 0], [0, -10], [0, 10]],
}
DESC_STATUSES = (
    "STEP_CAP", "EVALUATION_CAP", "NO_STRUCTURAL_MOVES", "NO_STRICT_IMPROVEMENT",
)
DESC_UNIT_EXPECTED = [
    ["PcbRoutedPassiveLegCensusContractTest", "PCB routed passive leg census contracts "],
    ["PcbRoutedPassiveFeasibilityCensusContractTest", "PCB routed passive feasibility census contracts "],
    ["PcbRoutedSlideCompactionContractTest", "PCB routed slide compaction contracts "],
    ["PcbRoutedSlideDescentContractTest", "PCB routed slide descent contracts "],
]
DESC_ROUTES_EXPECTED = [[
    "PcbRoutedSlideDescentTrialContractTest",
    "PCB routed slide descent trial contracts ",
]]


def _desc_protocol(protocol):
    _need(type(protocol) is dict and tuple(protocol) == DESC_PROTOCOL_KEYS,
          "slide descent protocol schema/order changed")
    _need(protocol["schema"] == 1 and
          protocol["baseCommit"] == "80303258be31da72e238455a05068c2015ee84d4" and
          protocol["scope"] ==
          "Bounded generic repeated private slide/compaction for both informed roots; no production change",
          "slide descent protocol identity changed")
    _need(_typed_equal(protocol["roots"], list(ROOTS)) and
          protocol["version"] == VERSION and type(protocol["version"]) is int and
          protocol["density"] == 14 and type(protocol["density"]) is int and
          protocol["phase"] == "SELECTED_AFTER_EDGE_TRIM" and
          protocol["summaryPrefix"] == SUMMARY_PREFIX and
          _typed_equal(protocol["reportOrder"],
                       [WHITESPACE_PREFIX, LEG_CASE_PREFIX, SUMMARY_PREFIX]),
          "slide descent phase identity changed")
    for key, expected in (
        ("summaryFields", SUMMARY_FIELDS), ("stepFields", STEP_FIELDS),
        ("structuralFields", STRUCTURAL_FIELDS), ("sourceFields", SOURCE_FIELDS),
        ("metricsFields", METRICS_FIELDS), ("controlFields", CONTROL_FIELDS),
        ("rowFields", ROW_FIELDS), ("compactionFields", COMPACTION_FIELDS),
        ("proofFields", PROOF_FIELDS),
        ("feasibilityProofFields", FEASIBILITY_PROOF_FIELDS),
        ("aggregateFields", AGGREGATE_FIELDS),
    ):
        _need(_typed_equal(protocol[key], list(expected)),
              "slide descent " + key + " changed")
    _need(_typed_equal(protocol["limits"], DESC_LIMITS) and
          _typed_equal(protocol["structuralLimits"], DESC_STRUCTURAL_LIMITS) and
          _typed_equal(protocol["statuses"], list(DESC_STATUSES)) and
          protocol["sourceGenerationMetadata"] == SOURCE_GENERATION and
          protocol["privateGenerationMetadata"] == PRIVATE_GENERATION,
          "slide descent limits/status/generation changed")
    _need(type(protocol["selectedBaselines"]) is dict and
          tuple(protocol["selectedBaselines"]) == ROOTS,
          "selected baseline root order changed")
    for seed in ROOTS:
        baseline = protocol["selectedBaselines"][seed]
        _need(type(baseline) is dict and baseline["seed"] == seed and
              baseline["version"] == VERSION and baseline["density"] == 14 and
              baseline["status"] == "PASS",
              "selected baseline identity changed for seed" + seed)
        for field in ("geometrySha256", "sourcePlanSha256", "liveBoardGraphSha256",
                      "sourceIntentSha256"):
            _hex_digest(baseline[field], "selected baseline " + field)
        _canonical_seed(baseline["layoutSeed"], "selected layout seed")
        _canonical_seed(baseline["routingSeed"], "selected routing seed")
        _need(type(baseline["rankedPlacementAttempts"]) is list and
              len(baseline["rankedPlacementAttempts"]) == 6 and
              all(type(item) is int and not isinstance(item, bool)
                  for item in baseline["rankedPlacementAttempts"]),
              "selected baseline ranking changed")
    _need(_typed_equal(protocol["priorContract"], {
        "path": OLD_READER_REL, "sha256": OLD_READER_SHA,
        "protocolPath": OLD_PROTOCOL_REL, "protocolSha256": OLD_PROTOCOL_SHA,
    }), "prior reader pin changed")
    _need(_typed_equal(protocol["unitFixtureCounts"], {
        "PcbRoutedPassiveLegCensusContractTest": 6,
        "PcbRoutedPassiveFeasibilityCensusContractTest": 6,
        "PcbRoutedSlideCompactionContractTest": 5,
        "PcbRoutedSlideDescentContractTest": 6,
    }) and _typed_equal(protocol["unitEvaluators"], {
        "PcbRoutedPassiveLegCensusContractTest": "endpointLegCensus",
        "PcbRoutedPassiveFeasibilityCensusContractTest": "physicalFeasibilityCensus",
        "PcbRoutedSlideCompactionContractTest": "selectedRoutedSlideCompaction",
        "PcbRoutedSlideDescentContractTest": "selectedRoutedSlideDescent",
    }) and _typed_equal(protocol["unitFixtureNames"], [
        "canonicalFirstImprovementAndTransitions", "strictDescentAndInitialGuards",
        "exhaustionAndEmptyCohort", "acceptedStepAndEvaluationCaps",
        "failureAndCancellationPropagation", "selectedObserverOwnershipAndAdapterIsolation",
    ]), "descent unit evaluator contract changed")
    _need(protocol["routePassDetail"] == PROTOCOL_ROUTE_DETAIL and
          protocol["timingScope"] ==
          "Native selected routed slide descent; unchanged per-JVM/search/physical limits" and
          protocol["maxPhaseElapsedSeconds"] == 36000 and
          protocol["maxCaseMillis"] == 60000 and
          protocol["maxFileBytes"] == MAX_FILE_BYTES,
          "descent timing bounds changed")
    _need(_typed_equal(protocol["publishedInputs"], PUBLISHED_INPUTS) and
          _typed_equal(protocol["suiteManifestSha256"], SUITE_MANIFEST_SHA) and
          type(protocol["classificationOwnerSha256"]) is dict and
          len(protocol["classificationOwnerSha256"]) == 7 and
          all(re.fullmatch(r"[0-9a-f]{64}", value) for value in
              protocol["classificationOwnerSha256"].values()) and
          type(protocol["limitsDisclosure"]) is list and
          len(protocol["limitsDisclosure"]) == 5 and
          all(type(item) is str and item for item in protocol["limitsDisclosure"]),
          "descent source pins/limits disclosure changed")
    # The pinned final protocol carries the adapter declaration in its
    # surrounding packet rather than as a top-level key.  Keep the adapter
    # constants above bound to the exact prior reader/protocol bytes; adding a
    # synthetic protocol field here would change the sealed contract.
    for key in ("improvementRule", "selectionRule", "terminationRule", "chainRule",
                "proofRule", "structuralRule"):
        _bounded_text(protocol[key], "protocol " + key)


def _load_contract(protocol_override=None, published_overrides=None):
    protocol_data = (_task_file("protocol.json", 256 * 1024)
                     if protocol_override is None else protocol_override)
    _require_sha(protocol_data, PROTOCOL_SHA, "sealed slide descent protocol")
    protocol = _parse_json(protocol_data, "slide descent protocol")
    _desc_protocol(protocol)
    overrides = {} if published_overrides is None else published_overrides
    _need(type(overrides) is dict and
          all(type(key) is str and type(value) is bytes for key, value in overrides.items()) and
          set(overrides).issubset(PUBLISHED_INPUTS),
          "published input overrides are malformed")
    blobs = {}
    for path, digest in PUBLISHED_INPUTS.items():
        data = overrides[path] if path in overrides else _repo_file(path)
        _require_sha(data, digest, "published input " + path)
        blobs[path] = data
    _require_sha(blobs[OLD_READER_REL], OLD_READER_SHA, "prior reader source")
    _require_sha(blobs[OLD_PROTOCOL_REL], OLD_PROTOCOL_SHA, "prior reader protocol")
    return {"protocol": protocol, "protocolData": protocol_data, "published": blobs}


def _published_slide_sources(contract):
    path = "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/routes.txt"
    raw = contract["published"][path]
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ReaderError("published compaction routes are not strict UTF-8") from exc
    sources = {}
    prefixes = (WHITESPACE_PREFIX, LEG_CASE_PREFIX,
                "PCB_ROUTED_SLIDE_COMPACTION_CASE ")
    for line in text.splitlines():
        if line.startswith("PCB_"):
            prefix = next((item for item in prefixes if line.startswith(item)), None)
            _need(prefix is not None, "published compaction report marker changed")
            row = _parse_json(line[len(prefix):].encode("utf-8"),
                              "published compaction report")
            _need(type(row) is dict, "published compaction report is not an object")
            if prefix == "PCB_ROUTED_SLIDE_COMPACTION_CASE ":
                seed = row.get("seed")
                _need(seed in ROOTS and seed not in sources,
                      "published compaction source order/identity changed")
                source = row.get("source")
                source_metrics = row.get("sourceMetrics")
                _need(type(source) is dict and tuple(source) == SOURCE_FIELDS and
                      type(source_metrics) is dict and tuple(source_metrics) == METRICS_FIELDS,
                      "published compaction source schema changed")
                sources[seed] = {"source": source, "metrics": source_metrics}
    _need(tuple(sources) == ROOTS, "published compaction sources lack exact root order")
    for seed in ROOTS:
        source = sources[seed]["source"]
        _need(source["liveGraphBefore"] == source["liveGraphAfter"] and
              source["geometryBefore"] == source["geometryAfter"] and
              source["componentCount"] ==
              contract["protocol"]["selectedBaselines"][seed]["parts"] and
              source["padCount"] ==
              contract["protocol"]["selectedBaselines"][seed]["pads"] and
              source["sourceRouteSourceCount"] == source["traceCount"] and
              source["padPairCount"] ==
              source["padCount"] * (source["padCount"] - 1) // 2,
              "published source facts are inconsistent for seed" + seed)
        for key in ("liveGraphBefore", "liveGraphAfter", "geometryBefore",
                    "geometryAfter", "traceProvenanceHash", "reachabilityHash"):
            _hex_digest(source[key], "published source " + key)
        for key in ("area", "copperLength", "componentCount", "padCount",
                    "traceCount", "sourceRouteSourceCount", "padPairCount"):
            _bounded_int(source[key], "published source " + key, 0, MAX_COUNT * 100)
        _need(sources[seed]["metrics"]["generationMetadata"] == SOURCE_GENERATION,
              "published source generation changed")
    return sources


def _load_prior_contract(contract):
    try:
        old_contract = OLD._load_contract()
        old_prior = OLD._load_prior_contract(old_contract)
    except ReaderError:
        raise
    except (ValueError, KeyError, TypeError, OSError, AttributeError) as exc:
        raise ReaderError("pinned prior reader failed to load") from exc
    _need(_sha(old_contract["protocolData"]) == OLD_PROTOCOL_SHA,
          "pinned prior protocol hash changed")
    _need(tuple(old_prior["selected"]) == (("5", VERSION), ("2", VERSION)) and
          tuple(old_prior["whitespace"]) == (("5", VERSION), ("2", VERSION)),
          "pinned old selected/whitespace rows lack exact root order")
    for seed in ROOTS:
        actual = dict(old_prior["selected"][(seed, VERSION)])
        expected = dict(contract["protocol"]["selectedBaselines"][seed])
        actual.pop("elapsedMillis", None)
        expected.pop("elapsedMillis", None)
        _need(_typed_equal(actual, expected),
              "selected baseline differs from sealed prior case for seed" + seed)
    sources = _published_slide_sources(contract)
    # The complete old census is independently validated by the pinned prior
    # chain.  It is used as the seed2 structural source; seed5 is checked from
    # the current descent report because the sealed old packet has no pad map.
    return {
        "old": PHYS, "oldContract": old_contract,
        "oldProtocol": old_prior["oldProtocol"],
        "selected": old_prior["selected"], "whitespace": old_prior["whitespace"],
        "publishedCensusSummaries": old_prior["publishedCensusSummaries"],
        "publishedCensusCases": old_prior["publishedCensusCases"],
        "publishedPoolSource": old_prior["publishedPoolSource"],
        "publishedFeasibilitySource": old_prior["publishedFeasibilitySource"],
        "publishedSourceFacts": sources,
        "oldPrior": old_prior,
        "physicalProtocol": old_prior["oldProtocol"],
    }


def _load_suite_manifest(phase, protocol, raw_override=None):
    _need(phase in ("unit", "routes"), "unknown native phase")
    name = "unit-suites.json" if phase == "unit" else "routes-suites.json"
    expected = DESC_UNIT_EXPECTED if phase == "unit" else DESC_ROUTES_EXPECTED
    raw = _task_file(name, 64 * 1024) if raw_override is None else raw_override
    _require_sha(raw, SUITE_MANIFEST_SHA[name], "sealed " + phase + " suite manifest")
    actual = _parse_json(raw, phase + " suite manifest")
    _need(_typed_equal(actual, expected), phase + " suite manifest changed")
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
    except (ValueError, KeyError, TypeError, AttributeError) as exc:
        raise ReaderError(str(exc)) from exc
    _need(_typed_equal(row, prior["whitespace"][(seed, VERSION)]),
          "whitespace report differs from pinned accepted15 row")


def _validate_case_report(row, seed, contract, prior):
    expected = prior["selected"][(seed, VERSION)]
    try:
        prior["old"].PRIOR._validate_selected_case(
            row, expected, seed, prior["oldProtocol"])
    except ReaderError:
        raise
    except (ValueError, KeyError, TypeError, AttributeError) as exc:
        raise ReaderError(str(exc)) from exc
    actual, baseline = dict(row), dict(contract["protocol"]["selectedBaselines"][seed])
    actual.pop("elapsedMillis", None)
    baseline.pop("elapsedMillis", None)
    _need(_typed_equal(actual, baseline),
          "selected old case differs from sealed baseline")


def _utf16_units(value):
    try:
        return len(value.encode("utf-16-le", errors="strict")) // 2
    except UnicodeEncodeError as exc:
        raise ReaderError("pad ID is not valid UTF-16 text") from exc


def _reachability_hash_parts(parts):
    pads = []
    for part in parts:
        for pad in part["pads"]:
            pads.append((pad["padId"], pad["netId"]))
    _need(len(pads) == len({item[0] for item in pads}),
          "structural census pad IDs are not unique")
    pads.sort(key=lambda item: item[0])
    payload = bytearray(b"R1\n")
    pairs = 0
    for index, (left_id, left_net) in enumerate(pads):
        left = str(_utf16_units(left_id)) + ":" + left_id
        for right_id, right_net in pads[index + 1:]:
            right = str(_utf16_units(right_id)) + ":" + right_id
            payload.extend((left + right +
                            ("1" if left_net == right_net else "0") + "\n").encode("utf-8"))
            pairs += 1
    return hashlib.sha256(payload).hexdigest(), pairs, len(payload)


def _trace_facts(parts):
    pads = []
    traces = {}
    trace_roles = {}
    endpoint_keys = set()
    pad_ids = set()
    for part in parts:
        for pad in part["pads"]:
            _need(pad["padId"] not in pad_ids, "structural census pad identity duplicated")
            pad_ids.add(pad["padId"])
            pads.append(pad)
            for incident in pad["incidents"]:
                endpoint_key = (incident["traceIndex"], incident["endRole"])
                _need(endpoint_key not in endpoint_keys,
                      "structural census trace endpoint duplicated")
                endpoint_keys.add(endpoint_key)
                identity = tuple(incident[key] for key in (
                    "traceSourceId", "traceNetId", "layer", "width"))
                if incident["traceIndex"] in traces:
                    _need(traces[incident["traceIndex"]] == identity,
                          "structural census trace identity changed")
                else:
                    traces[incident["traceIndex"]] = identity
                trace_roles.setdefault(incident["traceIndex"], set()).add(
                    incident["endRole"])
    # The leg census is endpoint-focused: an incident may be the one passive
    # endpoint observed for a full trace whose other endpoint is elsewhere on
    # the board.  Preserve metadata and endpoint uniqueness without claiming
    # that the offline census reconstructs those absent full paths.
    return pads, traces


def _validate_structural(structural, source, prior, seed):
    _need(type(structural) is dict and tuple(structural) == STRUCTURAL_FIELDS,
          "descent structural schema/order changed")
    for key in ("geometryHashBefore", "geometryHashAfter"):
        _hex_digest(structural[key], "structural " + key)
    _need(structural["geometryHashBefore"] == structural["geometryHashAfter"] ==
          source["geometryBefore"], "structural geometry is not the current source")
    parts = structural["parts"]
    _need(type(parts) is list and 0 < len(parts) <= DESC_STRUCTURAL_LIMITS["maxParts"],
          "structural census part count is outside its bound")
    previous = None
    for part in parts:
        try:
            component_id, _ = LEG._validate_part(part, previous)
        except ReaderError:
            raise
        except (ValueError, KeyError, TypeError, AttributeError) as exc:
            raise ReaderError(str(exc)) from exc
        previous = component_id
    pads, traces = _trace_facts(parts)
    _need(all(type(index) is int and not isinstance(index, bool) and
              0 <= index < source["traceCount"] for index in traces),
          "structural census trace index is outside the source trace bound")
    _need(len(parts) == source["componentCount"] and len(pads) == source["padCount"] and
          0 < len(traces) <= source["traceCount"],
          "structural census counts differ from source facts")
    digest, pair_count, _byte_count = _reachability_hash_parts(parts)
    _need(pair_count == source["padPairCount"] and digest == source["reachabilityHash"],
          "structural census reachability differs from source net oracle")
    if seed == "2":
        _need(digest == EXPECTED_REACHABILITY,
              "seed2 structural reachability differs from independent published oracle")
    moves = []
    for part in parts:
        for direction in part["directions"]:
            if direction["eligible"] and direction["copperDelta"] <= 0:
                moves.append({
                    "componentId": part["componentId"], "dx": direction["dx"],
                    "dy": direction["dy"],
                    "analyticCopperDelta": direction["copperDelta"],
                    "actualCopperDelta": direction["copperDelta"],
                })
    _need(len(moves) <= DESC_STRUCTURAL_LIMITS["maxDirections"],
          "structural census direction count exceeds its bound")
    for move in moves:
        _bounded_text(move["componentId"], "derived move component")
        for key in ("dx", "dy", "analyticCopperDelta", "actualCopperDelta"):
            _bounded_int(move[key], "derived move " + key)
        _need(move["analyticCopperDelta"] == move["actualCopperDelta"],
              "derived move analytic/actual delta differs")
    return parts, moves, {"pads": pads, "traces": traces, "reachabilityHash": digest}


def _validate_metrics(metrics, seed, protocol, expected=None, generation=None,
                      identity=None, geometry_differs_from=None):
    _need(type(metrics) is dict and tuple(metrics) == METRICS_FIELDS,
          "descent metrics schema/order changed")
    for key in ("area", "copperLength", "vias", "largestProtectedEmptyRectangleArea"):
        _bounded_int(metrics[key], "metrics " + key, 0, MAX_COUNT * 100)
    _need(metrics["area"] > 0 and metrics["copperLength"] > 0 and
          0 <= metrics["largestProtectedEmptyRectangleArea"] <= metrics["area"],
          "metrics area/copper/protected-void relationship changed")
    for key in ("geometrySha256", "stableIdentitySha256", "labelIdentitySha256"):
        _hex_digest(metrics[key], "metrics " + key)
    _need(type(metrics["generationMetadata"]) is str and
          metrics["generationMetadata"] in (SOURCE_GENERATION, PRIVATE_GENERATION),
          "metrics generation metadata is outside the closed catalog")
    if generation is not None:
        _need(metrics["generationMetadata"] == generation,
              "metrics generation metadata changed")
    if identity is not None:
        _need(metrics["stableIdentitySha256"] == identity["stableIdentitySha256"] and
              metrics["labelIdentitySha256"] == identity["labelIdentitySha256"],
              "stable/label identity digest changed")
    if expected is not None:
        for key in ("area", "copperLength", "vias", "largestProtectedEmptyRectangleArea",
                    "geometrySha256", "stableIdentitySha256", "labelIdentitySha256",
                    "generationMetadata"):
            _need(metrics[key] == expected[key], "metrics differ from chained source")
    if geometry_differs_from is not None:
        _need(metrics["geometrySha256"] != geometry_differs_from,
              "private result geometry did not change from its source")
    return metrics


def _validate_source(source, seed, prior, expected_metrics):
    _need(type(source) is dict and tuple(source) == SOURCE_FIELDS,
          "descent source schema/order changed")
    static = prior["publishedSourceFacts"][seed]["source"]
    for key in ("liveGraphBefore", "liveGraphAfter", "geometryBefore", "geometryAfter",
                "traceProvenanceHash", "reachabilityHash"):
        _hex_digest(source[key], "source " + key)
    for key in ("copperLength", "area", "componentCount", "padCount", "traceCount",
                "sourceRouteSourceCount", "padPairCount"):
        _bounded_int(source[key], "source " + key, 0, MAX_COUNT * 100)
    _need(source["liveGraphBefore"] == source["liveGraphAfter"] ==
          static["liveGraphBefore"] == static["liveGraphAfter"] and
          source["geometryBefore"] == source["geometryAfter"] == expected_metrics["geometrySha256"] and
          source["copperLength"] == expected_metrics["copperLength"] and
          source["area"] == expected_metrics["area"] and
          source["componentCount"] == static["componentCount"] and
          source["padCount"] == static["padCount"] and
          source["traceCount"] == static["traceCount"] and
          source["traceProvenanceHash"] == static["traceProvenanceHash"] and
          source["sourceRouteSourceCount"] == static["sourceRouteSourceCount"] and
          source["reachabilityHash"] == static["reachabilityHash"] and
          source["padPairCount"] == static["padPairCount"],
          "source facts changed across the private chain")
    _need(source["padPairCount"] == source["padCount"] * (source["padCount"] - 1) // 2,
          "source pad pair count is not an exact combinatorial count")
    return source


def _validate_feasibility_proof(proof, source):
    _need(type(proof) is dict and tuple(proof) == FEASIBILITY_PROOF_FIELDS,
          "embedded feasibility proof schema/order changed")
    for key in ("placementValidated", "geometryValidated", "twoLayerValidated",
                "traceEndpointOnlyVerified", "unrelatedGeometryVerified",
                "conductorReachabilityVerified", "traceProvenanceVerified"):
        _need(type(proof[key]) is bool and proof[key] is True,
              "embedded feasibility proof flag is not strict true")
    for key in ("padPairCount", "sourceRouteSourceCount", "candidateRouteSourceCount"):
        _bounded_int(proof[key], "embedded proof " + key, 0, MAX_COUNT * 100)
    for key in ("sourceReachabilityHash", "candidateReachabilityHash",
                "sourceTraceProvenanceHash", "candidateTraceProvenanceHash"):
        _hex_digest(proof[key], "embedded proof " + key)
    _need(proof["padPairCount"] == source["padPairCount"] and
          proof["sourceReachabilityHash"] == proof["candidateReachabilityHash"] ==
          source["reachabilityHash"] and
          proof["sourceRouteSourceCount"] == proof["candidateRouteSourceCount"] ==
          source["sourceRouteSourceCount"] and
          proof["sourceTraceProvenanceHash"] == proof["candidateTraceProvenanceHash"] ==
          source["traceProvenanceHash"],
          "embedded feasibility proof facts differ from the current source")


def _validate_final_proof(proof, source):
    _need(type(proof) is dict and tuple(proof) == PROOF_FIELDS,
          "final proof schema/order changed")
    for key in ("placementValidated", "geometryValidated", "routingValidated",
                "routeQualityValidated", "traceClearanceValidated",
                "twoLayerValidated", "stableIdentityVerified", "labelIdentityVerified",
                "conductorReachabilityVerified", "traceProvenanceVerified",
                "sourceImmutable"):
        _need(type(proof[key]) is bool and proof[key] is True,
              "final proof flag is not strict true")
    for key in ("padPairCount", "sourceRouteSourceCount", "candidateRouteSourceCount"):
        _bounded_int(proof[key], "final proof " + key, 0, MAX_COUNT * 100)
    for key in ("sourceReachabilityHash", "candidateReachabilityHash",
                "sourceTraceProvenanceHash", "candidateTraceProvenanceHash"):
        _hex_digest(proof[key], "final proof " + key)
    _need(proof["padPairCount"] == source["padPairCount"] and
          proof["sourceReachabilityHash"] == proof["candidateReachabilityHash"] ==
          source["reachabilityHash"] and
          proof["sourceRouteSourceCount"] == proof["candidateRouteSourceCount"] ==
          source["sourceRouteSourceCount"] and
          proof["sourceTraceProvenanceHash"] == proof["candidateTraceProvenanceHash"] ==
          source["traceProvenanceHash"],
          "final proof facts differ from the current source")


def _physical_protocol(prior, source, moves):
    protocol = copy.deepcopy(prior["physicalProtocol"])
    protocol["expectedMoves"] = copy.deepcopy(moves)
    protocol["expectedSource"] = {
        "area": source["area"], "copperLength": source["copperLength"],
        "traceCount": source["traceCount"], "geometryHash": source["geometryBefore"],
        "componentCount": source["componentCount"], "padCount": source["padCount"],
        "sourceRouteSourceCount": source["sourceRouteSourceCount"],
        "padPairCount": source["padPairCount"],
        "reachabilityHash": source["reachabilityHash"],
    }
    return protocol


def _source_summary(parts):
    return {"successfulCallbacks": [{"target": True, "parts": parts}]}


def _validate_feasibility(row, index, source, parts, move, prior):
    _need(type(row) is dict and tuple(row) == PHYS.ROW_FIELDS,
          "embedded feasibility row schema/order changed")
    try:
        PHYS._validate_move(row["move"], move)
        dynamic = _physical_protocol(prior, source, [move])
        PHYS._validate_candidate(row["candidate"], source, move,
                                 _source_summary(parts), dynamic)
    except ReaderError:
        raise
    except (ValueError, KeyError, TypeError, AttributeError) as exc:
        raise ReaderError(str(exc)) from exc
    disposition = row["disposition"]
    _need(type(disposition) is str and disposition in PHYS.ROW_DISPOSITIONS,
          "embedded feasibility disposition is outside the closed catalog")
    if disposition == PHYS.DISPOSITION_REJECTED:
        stage = row["stage"]
        _need(type(stage) is str and stage in PHYS.ROW_STAGES and stage != "COMPLETE",
              "rejected feasibility stage is outside the closed catalog")
        try:
            PHYS._validate_rejection(stage, row["reason"], row["message"], dynamic)
            PHYS._validate_checks(row["checks"], stage)
            PHYS._validate_timing(row["timing"], stage, dynamic)
        except ReaderError:
            raise
        except (ValueError, KeyError, TypeError, AttributeError) as exc:
            raise ReaderError(str(exc)) from exc
        _need(row["proof"] is None, "rejected feasibility row proof must be null")
        return {"disposition": disposition, "stage": stage,
                "timing": row["timing"], "strict": False, "metrics": None}
    _need(disposition == PHYS.DISPOSITION_VALIDATED and row["stage"] == "COMPLETE" and
          row["reason"] is None and row["message"] is None,
          "validated feasibility classification changed")
    try:
        PHYS._validate_checks(row["checks"], "COMPLETE")
        PHYS._validate_timing(row["timing"], "COMPLETE", dynamic)
    except ReaderError:
        raise
    except (ValueError, KeyError, TypeError, AttributeError) as exc:
        raise ReaderError(str(exc)) from exc
    _validate_feasibility_proof(row["proof"], source)
    return {"disposition": disposition, "stage": "COMPLETE",
            "timing": row["timing"], "strict": False, "metrics": None}


def _validate_control(control, source, source_metrics, seed):
    _need(type(control) is dict and tuple(control) == CONTROL_FIELDS,
          "descent control schema/order changed")
    _need(type(control["compacted"]) is bool and
          type(control["sourceImmutable"]) is bool and control["sourceImmutable"] is True,
          "descent control flags changed")
    metrics = _validate_metrics(
        control["metrics"], seed, {},
        generation=PRIVATE_GENERATION,
        identity=source_metrics)
    _validate_final_proof(control["proof"], source)
    return metrics


def _strict_improvement(metrics, current_source, current_control,
                        initial_source, initial_control):
    references = (current_source, current_control, initial_source, initial_control)
    return (
        all(metrics["area"] < item["area"] for item in references) and
        all(metrics["copperLength"] <= item["copperLength"] for item in references) and
        all(metrics["vias"] <= item["vias"] for item in references) and
        all(metrics["largestProtectedEmptyRectangleArea"] <=
            item["largestProtectedEmptyRectangleArea"] for item in references)
    )


def _validate_compaction(compaction, source, source_metrics, control_metrics,
                         initial_metrics, initial_control_metrics, seed):
    _need(type(compaction) is dict and tuple(compaction) == COMPACTION_FIELDS,
          "descent compaction schema/order changed")
    _need(type(compaction["compacted"]) is bool and
          type(compaction["sourceImmutable"]) is bool and
          compaction["sourceImmutable"] is True and
          type(compaction["strictImprovement"]) is bool,
          "descent compaction flags changed")
    metrics = _validate_metrics(
        compaction["metrics"], seed, {},
        generation=PRIVATE_GENERATION, identity=source_metrics,
        geometry_differs_from=source["geometryBefore"])
    _validate_final_proof(compaction["proof"], source)
    expected = _strict_improvement(metrics, source_metrics, control_metrics,
                                   initial_metrics, initial_control_metrics)
    _need(compaction["strictImprovement"] is expected,
          "strict improvement was not independently derived from all four references")
    return metrics


def _validate_step(step, expected_index, seed, protocol, prior, current_metrics,
                   initial_metrics, initial_control_metrics, evaluated_before):
    _need(type(step) is dict and tuple(step) == STEP_FIELDS,
          "descent step schema/order changed")
    _need(type(step["index"]) is int and not isinstance(step["index"], bool) and
          step["index"] == expected_index,
          "descent step index is not the canonical one-based order")
    source = _validate_source(step["source"], seed, prior, current_metrics)
    source_metrics = _validate_metrics(
        step["sourceMetrics"], seed, protocol,
        expected=current_metrics, generation=current_metrics["generationMetadata"],
        identity=initial_metrics)
    _need(_typed_equal(source_metrics, current_metrics),
          "step source metrics differ from chained source")
    control_metrics = _validate_control(step["control"], source, source_metrics, seed)
    parts, moves, structural_facts = _validate_structural(
        step["structural"], source, prior, seed)
    rows = step["rows"]
    _need(type(rows) is list, "descent step rows are not a list")
    remaining = protocol["limits"]["maxEvaluatedMoves"] - evaluated_before
    _need(remaining >= 0 and len(rows) <= remaining and len(rows) <= len(moves),
          "descent step exceeds the remaining evaluation bound")
    if moves:
        _need(len(rows) > 0, "nonempty structural census has no attempted rows")
    else:
        _need(len(rows) == 0, "empty structural census has attempted rows")
    observed = []
    accepted_index = None
    accepted_metrics = None
    timing_total = 0
    for row_index, row in enumerate(rows):
        _need(type(row) is dict and tuple(row) == ROW_FIELDS,
              "descent row schema/order changed")
        feasibility = _validate_feasibility(
            row["feasibility"], row_index, source, parts, moves[row_index], prior)
        timing_total += feasibility["timing"]["totalMillis"]
        compact = row["compaction"]
        if feasibility["disposition"] == PHYS.DISPOSITION_REJECTED:
            _need(compact is None, "physically rejected move has compaction output")
            observed.append({"validated": False, "strict": False})
            continue
        _need(feasibility["disposition"] == PHYS.DISPOSITION_VALIDATED and
              compact is not None, "physically validated move lacks compaction output")
        compact_metrics = _validate_compaction(
            compact, source, source_metrics, control_metrics,
            initial_metrics, initial_control_metrics, seed)
        strict = compact["strictImprovement"]
        _need(type(strict) is bool, "compaction strictImprovement is not boolean")
        if strict:
            _need(accepted_index is None,
                  "rows continue after the first strict accepted move")
            accepted_index = row_index
            accepted_metrics = compact_metrics
        observed.append({"validated": True, "strict": strict})
    if accepted_index is not None:
        _need(len(rows) == accepted_index + 1,
              "rows after the first strict accepted move are not permitted")
    elif len(moves) <= remaining:
        _need(len(rows) == len(moves),
              "non-capped step omitted a canonical move row")
    else:
        _need(len(rows) == remaining,
              "evaluation-capped step did not report its attempted prefix")
    declared_index = step["acceptedRowIndex"]
    _need(declared_index is None or
          (type(declared_index) is int and not isinstance(declared_index, bool) and
           0 <= declared_index < len(rows)),
          "acceptedRowIndex is outside the current row prefix")
    _need(declared_index == accepted_index,
          "acceptedRowIndex does not identify the first strict row")
    return {
        "source": source, "sourceMetrics": source_metrics,
        "controlMetrics": control_metrics, "parts": parts, "moves": moves,
        "structuralFacts": structural_facts, "rows": rows,
        "acceptedRowIndex": accepted_index, "acceptedMetrics": accepted_metrics,
        "evaluated": len(rows),
        "validated": sum(item["validated"] for item in observed),
        "rejected": sum(not item["validated"] for item in observed),
        "compactions": sum(item["validated"] for item in observed),
        "timingTotal": timing_total,
    }


def _validate_summary(summary, seed, contract, prior):
    protocol = contract["protocol"]
    _need(type(summary) is dict and tuple(summary) == SUMMARY_FIELDS,
          "descent summary schema/order changed")
    _need(_canonical_seed(summary["seed"], "descent seed") == seed and
          type(summary["label"]) is str and summary["label"] == "seed" + seed and
          type(summary["version"]) is int and summary["version"] == VERSION and
          summary["phase"] == protocol["phase"] and
          summary["selectedRoute"] == SELECTED_ROUTE and
          type(summary["newRouteRequests"]) is int and summary["newRouteRequests"] == 0 and
          type(summary["selectedMutation"]) is bool and summary["selectedMutation"] is False and
          _typed_equal(summary["limits"], protocol["limits"]),
          "descent summary identity/selection/limits changed")
    baseline = protocol["selectedBaselines"][seed]
    _need(type(summary["selectedAttempt"]) is int and
          summary["selectedAttempt"] == baseline["selectedAttempt"],
          "descent selected attempt differs from sealed baseline")
    _bounded_int(summary["elapsedMillis"], "descent summary elapsedMillis", 0,
                 protocol["maxCaseMillis"])
    published = prior["publishedSourceFacts"][seed]
    initial_source = _validate_source(
        summary["source"], seed, prior, published["metrics"])
    _need(_typed_equal(initial_source, published["source"]),
          "initial descent source differs from pinned selected source")
    initial_metrics = _validate_metrics(
        summary["sourceMetrics"], seed, protocol,
        expected=published["metrics"], generation=SOURCE_GENERATION)
    _need(_typed_equal(initial_metrics, published["metrics"]),
          "initial source metrics differ from pinned selected metrics")
    _need(type(summary["initialControl"]) is dict and
          tuple(summary["initialControl"]) == CONTROL_FIELDS,
          "initial control is missing or malformed")
    initial_control_metrics = _validate_control(
        summary["initialControl"], initial_source, initial_metrics, seed)
    steps = summary["steps"]
    _need(type(steps) is list and 0 < len(steps) <= protocol["limits"]["maxAcceptedSteps"],
          "descent step count is outside its bound")
    _need(_typed_equal(summary["initialControl"], steps[0]["control"]),
          "initialControl is not exactly the first step control")
    accepted_count = evaluated = validated = rejected = compactions = 0
    timing_total = 0
    current_metrics = initial_metrics
    last_info = None
    terminal_status = None
    for step_number, step in enumerate(steps, 1):
        _need(step_number == 1 or accepted_count > 0,
              "later descent step lacks a preceding accepted step")
        info = _validate_step(
            step, step_number, seed, protocol, prior, current_metrics,
            initial_metrics, initial_control_metrics, evaluated)
        if step_number == 1:
            _need(_typed_equal(info["source"], initial_source) and
                  _typed_equal(info["sourceMetrics"], initial_metrics),
                  "first descent step source is not the original selected source")
        evaluated += info["evaluated"]
        validated += info["validated"]
        rejected += info["rejected"]
        compactions += info["compactions"]
        timing_total += info["timingTotal"]
        if info["acceptedRowIndex"] is not None:
            accepted_count += 1
            _need(info["acceptedMetrics"] is not None,
                  "accepted row has no chained private metrics")
            current_metrics = info["acceptedMetrics"]
            if accepted_count == protocol["limits"]["maxAcceptedSteps"]:
                terminal_status = "STEP_CAP"
            elif evaluated >= protocol["limits"]["maxEvaluatedMoves"]:
                terminal_status = "EVALUATION_CAP"
        else:
            if evaluated >= protocol["limits"]["maxEvaluatedMoves"]:
                terminal_status = "EVALUATION_CAP"
            elif not info["moves"]:
                terminal_status = "NO_STRUCTURAL_MOVES"
            elif info["evaluated"] == len(info["moves"]):
                terminal_status = "NO_STRICT_IMPROVEMENT"
            else:
                raise ReaderError("descent step ended without acceptance or a terminal bound")
        last_info = info
        if terminal_status is not None:
            _need(step_number == len(steps),
                  "descent reports continue after its terminal condition")
            break
        _need(step_number < protocol["limits"]["maxAcceptedSteps"],
              "descent exceeds its accepted-step bound")
    _need(last_info is not None and terminal_status in DESC_STATUSES and
          summary["status"] == terminal_status,
          "descent termination status does not match the canonical bounds")
    final_expected = current_metrics
    _validate_metrics(summary["finalMetrics"], seed, protocol,
                      expected=final_expected,
                      generation=final_expected["generationMetadata"],
                      identity=initial_metrics)
    aggregate = {
        "steps": len(steps), "acceptedSteps": accepted_count,
        "evaluatedMoves": evaluated, "validatedMoves": validated,
        "rejectedMoves": rejected, "compactions": compactions,
    }
    _need(_typed_equal(summary["aggregate"], aggregate),
          "descent aggregate does not match every step and row")
    _need(summary["elapsedMillis"] >= timing_total,
          "descent summary elapsed time does not cover physical rows")
    return {"seed": seed, "status": terminal_status, "aggregate": aggregate}


def _assess_routes(reports, contract, prior):
    protocol = contract["protocol"]
    _need(type(reports) is list and len(reports) == 6,
          "routes phase requires exactly six ordered PCB report rows")
    # Validate the immutable baseline rows before touching new summaries.  The
    # prior helpers are wrapped so malformed current rows become ReaderError,
    # rather than leaking KeyError/TypeError through the CLI boundary.
    old_rows = []
    descent_rows = []
    cursor = 0
    for seed in ROOTS:
        _need(reports[cursor][0] == WHITESPACE_PREFIX and
              reports[cursor][1].get("seed") == seed,
              "whitespace report is missing or out of order")
        old_rows.append(reports[cursor])
        cursor += 1
        _need(reports[cursor][0] == LEG_CASE_PREFIX and
              reports[cursor][1].get("seed") == seed,
              "routed-leg report is missing or out of order")
        old_rows.append(reports[cursor])
        cursor += 1
        _need(reports[cursor][0] == protocol["summaryPrefix"] and
              reports[cursor][1].get("seed") == seed,
              "descent summary is missing or out of order")
        descent_rows.append((seed, reports[cursor][1]))
        cursor += 1
    _need(cursor == len(reports), "extra or foreign descent reports remain")
    for prefix, row, _line in old_rows:
        seed = row.get("seed")
        if prefix == WHITESPACE_PREFIX:
            _validate_whitespace(row, seed, prior)
        else:
            _validate_case_report(row, seed, contract, prior)
    summaries = [_validate_summary(row, seed, contract, prior)
                 for seed, row in descent_rows]
    return {
        "status": "PASS", "scope": protocol["scope"], "roots": 2, "rows": 2,
        "oldBaselineReports": 4, "descentSummaries": summaries,
        "routeReports": 6,
        "observationDisposition": "PASS; bounded private descent observation only",
        "fitnessDisposition": "NOT_EVALUATED",
    }


def _unit_pass_line(line, suite, protocol):
    expected_prefix = "PASS: " + suite[1]
    match = re.fullmatch(
        re.escape(expected_prefix) + r"assertions=([1-9][0-9]*) fixtures=" +
        str(protocol["unitFixtureCounts"][suite[0]]) + r" evaluator=" +
        re.escape(protocol["unitEvaluators"][suite[0]]), line)
    _need(match is not None,
          "unit PASS marker/count/evaluator is malformed for " + suite[0])


def _phase_summary(text, receipt, timing, suites, phase, protocol, prior):
    _need(type(timing) is dict and tuple(timing) == TIMING_FIELDS,
          "native timing schema/order changed")
    _need(phase in ("unit", "routes") and timing["phase"] == phase,
          "native phase differs from requested phase")
    expected_suites, _manifest_sha = _load_suite_manifest(phase, protocol)
    _need(_typed_equal(suites, expected_suites),
          "native suite rows differ from pinned order")
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
    suite_passes = [line for line in text_lines
                    if line.startswith("PASS: ") and line != terminal]
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
              if line.startswith("PCB_")),
          "native report appeared after terminal marker")
    if phase == "unit":
        _need(not text_reports and not receipt_reports,
              "unit contract leaked descent reports")
        result = None
    else:
        report_positions = [position for position, line in enumerate(text_lines)
                            if line.startswith("PCB_")]
        suite_positions = [text_lines.index(line) for line in suite_passes]
        _need(report_positions and max(report_positions) < min(suite_positions),
              "routes suite PASS appeared before all PCB reports")
        receipt_report_positions = [position for position, line in enumerate(receipt_lines)
                                    if line.startswith("PCB_")]
        receipt_suite_positions = [receipt_lines.index(line) for line in receipt_passes]
        _need(receipt_report_positions and
              max(receipt_report_positions) < min(receipt_suite_positions),
              "routes child receipt PASS appeared before all PCB reports")
        result = _assess_routes(text_reports, {"protocol": protocol}, prior)
    return {
        "status": "PASS", "phase": phase, "suiteCount": len(expected_suites),
        "suiteClasses": [row[0] for row in expected_suites],
        "reportRows": len(text_reports),
        "orderedMarkers": [row[1] for row in expected_suites],
        "descent": result,
    }


def check_phase(text, receipt, timing, suites, phase):
    contract = _load_contract()
    prior = _load_prior_contract(contract)
    expected_suites, _manifest_sha = _load_suite_manifest(phase, contract["protocol"])
    _need(_typed_equal(suites, expected_suites),
          "caller suite manifest differs from pinned bytes")
    return _phase_summary(text, receipt, timing, suites, phase,
                          contract["protocol"], prior)


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
    summary = _phase_summary(text, receipt, timing, suites, phase,
                             protocol, prior)
    return summary, {name: _sha(data) for name, data in raw.items()}, manifest_sha


def _unit_validation(contract, summary, phase_inputs, manifest_sha):
    protocol = contract["protocol"]
    return {
        "schema": 1, "status": "PASS", "phase": "unit",
        "scope": "Selected-frame routed slide descent unit fixtures only; native routes and fitness are not evaluated.",
        "baseCommit": protocol["baseCommit"],
        "protocolSha256": _sha(contract["protocolData"]),
        "priorAssessSha256": OLD_READER_SHA,
        "publishedInputsSha256": {name: _sha(data)
                                   for name, data in contract["published"].items()},
        "suiteManifestSha256": manifest_sha, "phaseInputsSha256": phase_inputs,
        "phaseEvidence": summary, "fixtureCounts": dict(protocol["unitFixtureCounts"]),
        "nativeRuntimeEvidence": "PASS",
    }


def _validate_unit_prerequisite(data, expected):
    actual = _parse_json(data, "unit prerequisite")
    _need(_typed_equal(actual, expected),
          "routes phase lacks a fresh matching current unit-validation PASS prerequisite")
    return _sha(data)


def _routes_assessment(contract, summary, phase_inputs, manifest_sha,
                       unit_prerequisite_sha):
    protocol = contract["protocol"]
    descent = summary.get("descent")
    _need(type(descent) is dict and descent.get("status") == "PASS",
          "selected routed descent observation is missing or failed")
    return {
        "schema": 1, "assessmentStatus": "PASS", "scope": protocol["scope"],
        "baseCommit": protocol["baseCommit"],
        "protocolSha256": _sha(contract["protocolData"]),
        "priorAssessSha256": OLD_READER_SHA,
        "publishedInputsSha256": {name: _sha(data)
                                   for name, data in contract["published"].items()},
        "unitPrerequisiteSha256": unit_prerequisite_sha,
        "routesSuiteManifestSha256": manifest_sha,
        "routePhaseInputsSha256": phase_inputs,
        "nativePhase": {"status": "PASS", "phase": "routes", "evidence": summary},
        "observationDisposition": "PASS; bounded private descent observation only",
        "fitnessDisposition": "NOT_EVALUATED",
        "policyDisposition": "NO_POLICY_OR_ACCEPTANCE_CHANGE",
        "productionChange": "NONE", "limits": list(protocol["limitsDisclosure"]),
    }


def _write_artifact(name, value):
    target = HERE / name
    _need(not target.exists() and not target.is_symlink(),
          "refusing to overwrite existing task artifact: " + name)
    payload = (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) +
               "\n").encode("utf-8")
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
        unit_summary, unit_inputs, unit_manifest_sha = _read_phase_files(
            "unit", contract, prior)
        expected_unit = _unit_validation(contract, unit_summary,
                                         unit_inputs, unit_manifest_sha)
        prerequisite_sha = _validate_unit_prerequisite(unit_data, expected_unit)
        output = _routes_assessment(contract, summary, phase_inputs,
                                    manifest_sha, prerequisite_sha)
        _write_artifact("assessment.json", output)
        print("PASS routes phase; observation PASS; fitness NOT_EVALUATED")
        return 0
    except ReaderError as exc:
        print("FAIL offline reader: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
