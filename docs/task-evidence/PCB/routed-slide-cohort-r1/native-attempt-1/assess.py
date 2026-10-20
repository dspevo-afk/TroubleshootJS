"""Strict offline reader for the informed14 routed-slide cohort probe."""

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
TASK_REL = "docs/task-evidence/PCB/routed-slide-cohort-r1"
PRIOR_READER_REL = "docs/task-evidence/PCB/routed-slide-descent-r1/assess.py"
PRIOR_READER_SHA = "460c5bf29b2a65e4ad8ee83e8726a00afbfc90bedcab75459ab966ec2f2e38aa"
PRIOR_PROTOCOL_REL = "docs/task-evidence/PCB/routed-slide-descent-r1/protocol.json"
PRIOR_PROTOCOL_SHA = "e047c6b87e72538b7b161449b7938b80c8e1ec9c081bea27ba40c26d3c4b73ba"
PROTOCOL_SHA = "f65e822905cb8b525305b4025d404d8dababde09c41ef992c2778462a85b686d"

BASELINES_PATH = "docs/task-evidence/PCB/passive-local-pose-choice-r1/accepted15-baselines.json"
FINAL_NATIVE_PATH = "docs/task-evidence/PCB/post-selection-edge-trim-r1/final-native.txt"
ROOTS = (
    "-1017", "-1004", "-1022", "0", "17", "42", "1", "7", "5", "14", "2", "3", "4", "6",
)
PARTITIONS = (
    ("-1017", "-1004"), ("-1022", "0"), ("17", "42"), ("1", "7"),
    ("5", "14"), ("2", "3"), ("4", "6"),
)
VERSION = 15
SUMMARY_PREFIX = "PCB_ROUTED_SLIDE_COHORT_CASE "
WHITESPACE_PREFIX = "PCB_WHITESPACE_CASE "
LEG_PREFIX = "PCB_ROUTED_LEG_CASE "
SELECTED_ROUTE = "P07_FULLER_TWO_LAYER"
SOURCE_GENERATION = "ACCEPTED_SELECTED"
PRIVATE_GENERATION = "UNASSIGNED_PRIVATE_CANDIDATE"
MAX_SIGNED_LONG = (1 << 63) - 1
MIN_SIGNED_LONG = -(1 << 63)
MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_REPORTS = 100000
MAX_FIELD_TEXT = 1024 * 1024
MAX_COUNT = 1000000
EXPECTED_REACHABILITY = "9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7"

SUMMARY_FIELDS = (
    "seed", "label", "version", "phase", "status", "selectedAttempt",
    "selectedRoute", "newRouteRequests", "selectedMutation", "limits",
    "source", "sourceMetrics", "initialControl", "steps", "finalMetrics",
    "aggregate", "elapsedMillis",
)
STEP_FIELDS = (
    "index", "source", "sourceMetrics", "control", "structural", "rows",
    "acceptedRowIndex",
)
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
    "conductorReachabilityVerified", "traceProvenanceVerified", "sourceImmutable",
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
TIME_PATHS = (
    "elapsedMillis",
    "steps[*].rows[*].feasibility.timing.constructionMillis",
    "steps[*].rows[*].feasibility.timing.placementMillis",
    "steps[*].rows[*].feasibility.timing.geometryMillis",
    "steps[*].rows[*].feasibility.timing.twoLayerMillis",
    "steps[*].rows[*].feasibility.timing.proofMillis",
    "steps[*].rows[*].feasibility.timing.totalMillis",
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
PROTOCOL_KEYS = (
    "schema", "baseCommit", "scope", "roots", "partitions", "version", "density",
    "phase", "summaryPrefix", "reportOrder", "selectedRoute", "summaryFields",
    "limits", "structuralLimits", "statuses", "stepFields", "structuralFields",
    "sourceFields", "metricsFields", "controlFields", "rowFields",
    "compactionFields", "proofFields", "feasibilityProofFields",
    "sourceGenerationMetadata", "privateGenerationMetadata", "aggregateFields",
    "improvementRule", "selectionRule", "terminationRule", "chainRule",
    "proofRule", "structuralRule", "selectedBaselines", "originalBoundary",
    "sourceBoundary", "matchedRoots", "matchedTimePaths", "matchedTimeRule",
    "priorContract", "unitFixtureCounts", "unitEvaluators", "unitFixtureNames",
    "routePassDetail", "timingScope", "maxPhaseElapsedSeconds", "maxCaseMillis",
    "perJvmMillis", "maxFileBytes", "publishedInputs", "classificationOwnerSha256",
    "suiteManifestSha256", "limitsDisclosure",
)
PUBLISHED_INPUTS = {
    BASELINES_PATH: "12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816",
    FINAL_NATIVE_PATH: "db538fe4cca9aff18e33a974a6afa53ecb2fc244c7827c65201cc5408b65330d",
    PRIOR_PROTOCOL_REL: PRIOR_PROTOCOL_SHA,
    "docs/task-evidence/PCB/routed-slide-descent-r1/native-inputs.json":
        "8c1c0a121455a4e02d85253c3df03c456d27dc1ae8044673ee29c764d0928ad5",
    "docs/task-evidence/PCB/routed-slide-descent-r1/root-review.json":
        "57bc9be9f5a2faaffef94fed0e9640d3639173f65a558906e27f017e5445f1c6",
    "docs/task-evidence/PCB/routed-slide-descent-r1/routes.txt":
        "d39a3033b90f87842d85298fcb66eafb6969d8e1556d9b6d21e2dc0948495dc2",
    "docs/task-evidence/PCB/routed-slide-descent-r1/assessment.json":
        "9afda2b35062f4cec8f3028469be031f65336a195d6357645ff250d73ea2922c",
    PRIOR_READER_REL: PRIOR_READER_SHA,
    "docs/task-evidence/PCB/routed-slide-descent-r1/descent-analysis.json":
        "9b73b3ffa5cce112bc8b84da0d4ebc7e08bc0fff6d6957bf7a3867cc51f410ff",
}
SUITE_MANIFEST_SHA = {
    "unit-suites.json": "ff50bb2927192cc3f03d73ba4a535b5da04732c2b3c6d029aa36ad2289d987eb",
    "routes-suites.json": "88bdaf9bed95d9a092d59b42bdb3f53d93063457a514c067be983e56671db147",
}
UNIT_EXPECTED = (
    ("PcbRoutedPassiveLegCensusContractTest", "PCB routed passive leg census contracts "),
    ("PcbRoutedPassiveFeasibilityCensusContractTest", "PCB routed passive feasibility census contracts "),
    ("PcbRoutedSlideCompactionContractTest", "PCB routed slide compaction contracts "),
    ("PcbRoutedSlideDescentContractTest", "PCB routed slide descent contracts "),
    ("PcbRoutedSlideCohortContractTest", "PCB routed slide cohort contracts "),
)
ROUTES_EXPECTED = (
    ("PcbRoutedSlideCohortTrialContractTest", "PCB routed slide cohort trial contracts "),
    ("PcbRoutedSlideCohortTrialContractTestGroup2", "PCB routed slide cohort Group2 contracts "),
    ("PcbRoutedSlideCohortTrialContractTestGroup3", "PCB routed slide cohort Group3 contracts "),
    ("PcbRoutedSlideCohortTrialContractTestGroup4", "PCB routed slide cohort Group4 contracts "),
    ("PcbRoutedSlideCohortTrialContractTestGroup5", "PCB routed slide cohort Group5 contracts "),
    ("PcbRoutedSlideCohortTrialContractTestGroup6", "PCB routed slide cohort Group6 contracts "),
    ("PcbRoutedSlideCohortTrialContractTestGroup7", "PCB routed slide cohort Group7 contracts "),
)


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
    return stat.S_ISLNK(info.st_mode) or bool(getattr(info, "st_file_attributes", 0) & 0x400)


def _repo_file(relative, max_bytes=MAX_FILE_BYTES):
    _need(type(relative) is str and "\\" not in relative,
          "repository path must be slash-relative")
    pure = PurePosixPath(relative)
    _need(not pure.is_absolute() and bool(pure.parts) and
          all(part not in ("", ".", "..") for part in pure.parts),
          "repository path is not contained")
    target = REPO.joinpath(*pure.parts)
    try:
        root = REPO.resolve(strict=True)
        cursor = root
        for part in pure.parts:
            cursor = cursor / part
            _need(not _is_reparse(cursor), "repository input crosses a reparse point")
        resolved = target.resolve(strict=True)
        _need(resolved == target.absolute() or resolved.is_relative_to(root),
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
    _need(type(data) is bytes and len(data) <= MAX_FILE_BYTES,
          label + " bytes are missing or too large")
    try:
        value = json.loads(data.decode("utf-8", errors="strict"),
                           object_pairs_hook=_strict_pairs,
                           parse_constant=_reject_constant)
    except ReaderError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, TypeError, ValueError) as exc:
        raise ReaderError(label + " is not strict JSON") from exc
    return value


def _require_sha(data, expected, label):
    _need(type(expected) is str and re.fullmatch(r"[0-9a-f]{64}", expected) and
          _sha(data) == expected, label + " hash changed")


def _bounded_int(value, label, low=MIN_SIGNED_LONG, high=MAX_SIGNED_LONG):
    _need(type(value) is int and not isinstance(value, bool) and low <= value <= high,
          label + " is not a bounded integer")
    return value


def _bounded_text(value, label, allow_null=False):
    if allow_null and value is None:
        return value
    _need(type(value) is str and 0 < len(value.encode("utf-8", errors="strict")) <= MAX_FIELD_TEXT,
          label + " is not bounded text")
    return value


def _hex_digest(value, label):
    _need(type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value) is not None,
          label + " is not a lowercase SHA-256 digest")
    return value


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


def _load_prior_reader():
    source = _repo_file(PRIOR_READER_REL)
    _require_sha(source, PRIOR_READER_SHA, "pinned descent reader")
    path = REPO.joinpath(*PurePosixPath(PRIOR_READER_REL).parts)
    try:
        module = _load_module_without_cache(source, path, "pinned_routed_slide_descent_reader")
    except Exception as exc:
        raise ReaderError("pinned descent reader failed to load") from exc
    module.ReaderError = ReaderError
    _need(_sha(source) == PRIOR_READER_SHA, "loaded descent reader differs from its pin")
    return module


PRIOR = _load_prior_reader()
_typed_equal = PRIOR._typed_equal
PHYS = PRIOR.PHYS
LEG = PRIOR.LEG


def _validate_protocol(protocol):
    _need(type(protocol) is dict and tuple(protocol) == PROTOCOL_KEYS,
          "cohort protocol schema/order changed")
    _need(protocol["schema"] == 1 and type(protocol["schema"]) is int and
          protocol["baseCommit"] == "1202f18e48bcd3401aa117c3d5fd3b25bc0c21c9" and
          protocol["scope"] ==
          "All14 informed accepted15 regression roots with exactly the published bounded generic private descent; no production change",
          "cohort protocol identity changed")
    _need(_typed_equal(protocol["roots"], list(ROOTS)) and
          _typed_equal(protocol["partitions"], [list(item) for item in PARTITIONS]) and
          protocol["version"] == VERSION and type(protocol["version"]) is int and
          protocol["density"] == 14 and type(protocol["density"]) is int and
          protocol["phase"] == "SELECTED_AFTER_EDGE_TRIM" and
          protocol["summaryPrefix"] == SUMMARY_PREFIX and
          _typed_equal(protocol["reportOrder"], [WHITESPACE_PREFIX, LEG_PREFIX, SUMMARY_PREFIX]) and
          protocol["selectedRoute"] == SELECTED_ROUTE,
          "cohort root/phase identity changed")
    for key, expected in (
        ("summaryFields", SUMMARY_FIELDS), ("stepFields", STEP_FIELDS),
        ("structuralFields", STRUCTURAL_FIELDS), ("sourceFields", SOURCE_FIELDS),
        ("metricsFields", METRICS_FIELDS), ("controlFields", CONTROL_FIELDS),
        ("rowFields", ROW_FIELDS), ("compactionFields", COMPACTION_FIELDS),
        ("proofFields", PROOF_FIELDS), ("feasibilityProofFields", FEASIBILITY_PROOF_FIELDS),
        ("aggregateFields", AGGREGATE_FIELDS), ("matchedTimePaths", TIME_PATHS),
    ):
        _need(_typed_equal(protocol[key], list(expected)), "cohort " + key + " changed")
    _need(_typed_equal(protocol["limits"], {"maxAcceptedSteps": 4, "maxEvaluatedMoves": 128}) and
          _typed_equal(protocol["structuralLimits"], {
              "maxParts": 128, "maxDirections": 512,
              "directions": [[-10, 0], [10, 0], [0, -10], [0, 10]],
          }) and
          _typed_equal(protocol["statuses"], [
              "STEP_CAP", "EVALUATION_CAP", "NO_STRUCTURAL_MOVES", "NO_STRICT_IMPROVEMENT",
          ]) and protocol["sourceGenerationMetadata"] == SOURCE_GENERATION and
          protocol["privateGenerationMetadata"] == PRIVATE_GENERATION,
          "cohort limits/status/generation changed")
    _need(tuple(protocol["selectedBaselines"]) == ROOTS and
          _typed_equal(protocol["matchedRoots"], ["5", "2"]),
          "cohort baseline root order changed")
    for seed in ROOTS:
        baseline = protocol["selectedBaselines"][seed]
        _need(type(baseline) is dict and baseline["status"] == "PASS" and
              baseline["seed"] == seed and baseline["version"] == VERSION and
              baseline["density"] == 14,
              "cohort selected baseline identity changed for " + seed)
        for key in ("geometrySha256", "sourcePlanSha256", "liveBoardGraphSha256",
                    "sourceIntentSha256"):
            _hex_digest(baseline[key], "baseline " + key)
        _canonical_seed(baseline["layoutSeed"], "baseline layoutSeed")
        _canonical_seed(baseline["routingSeed"], "baseline routingSeed")
        _bounded_int(baseline["parts"], "baseline parts", 1, 128)
        _bounded_int(baseline["pads"], "baseline pads", 1, MAX_COUNT)
    _need(_typed_equal(protocol["priorContract"], {
        "path": PRIOR_READER_REL, "sha256": PRIOR_READER_SHA,
        "protocolPath": PRIOR_PROTOCOL_REL, "protocolSha256": PRIOR_PROTOCOL_SHA,
    }), "prior cohort reader pin changed")
    _need(_typed_equal(protocol["unitFixtureCounts"], {
        "PcbRoutedPassiveLegCensusContractTest": 6,
        "PcbRoutedPassiveFeasibilityCensusContractTest": 6,
        "PcbRoutedSlideCompactionContractTest": 5,
        "PcbRoutedSlideDescentContractTest": 6,
        "PcbRoutedSlideCohortContractTest": 3,
    }) and _typed_equal(protocol["unitEvaluators"], {
        "PcbRoutedPassiveLegCensusContractTest": "endpointLegCensus",
        "PcbRoutedPassiveFeasibilityCensusContractTest": "physicalFeasibilityCensus",
        "PcbRoutedSlideCompactionContractTest": "selectedRoutedSlideCompaction",
        "PcbRoutedSlideDescentContractTest": "selectedRoutedSlideDescent",
        "PcbRoutedSlideCohortContractTest": "selectedRoutedSlideCohort",
    }) and _typed_equal(protocol["unitFixtureNames"], [
        "genericFrameAndMetadata", "genericFrameRejectionAndImmutability",
        "cohortObserverCleanup",
    ]), "cohort unit evaluator contract changed")
    _need(protocol["routePassDetail"] == "roots=2 rows=2 evaluator=selectedRoutedSlideCohort" and
          protocol["timingScope"] ==
          "Native informed14 routed slide cohort; unchanged per-JVM/search/physical limits" and
          protocol["maxPhaseElapsedSeconds"] == 36000 and
          protocol["maxCaseMillis"] == 60000 and protocol["perJvmMillis"] == 60000 and
          protocol["maxFileBytes"] == MAX_FILE_BYTES,
          "cohort timing bounds changed")
    _need(_typed_equal(protocol["publishedInputs"], PUBLISHED_INPUTS) and
          _typed_equal(protocol["suiteManifestSha256"], SUITE_MANIFEST_SHA) and
          type(protocol["classificationOwnerSha256"]) is dict and
          len(protocol["classificationOwnerSha256"]) == 7 and
          all(re.fullmatch(r"[0-9a-f]{64}", value)
              for value in protocol["classificationOwnerSha256"].values()) and
          type(protocol["limitsDisclosure"]) is list and len(protocol["limitsDisclosure"]) == 5,
          "cohort source pins or disclosure changed")
    for key in ("originalBoundary", "sourceBoundary", "matchedTimeRule", "improvementRule",
                "selectionRule", "terminationRule", "chainRule", "proofRule", "structuralRule"):
        _bounded_text(protocol[key], "protocol " + key)


def _load_contract(protocol_override=None, published_overrides=None):
    protocol_data = _task_file("protocol.json", 512 * 1024) if protocol_override is None else protocol_override
    _need(type(protocol_data) is bytes, "cohort protocol bytes are missing or malformed")
    _require_sha(protocol_data, PROTOCOL_SHA, "sealed cohort protocol")
    protocol = _parse_json(protocol_data, "cohort protocol")
    _validate_protocol(protocol)
    overrides = {} if published_overrides is None else published_overrides
    _need(type(overrides) is dict and all(type(k) is str and type(v) is bytes for k, v in overrides.items()) and
          set(overrides).issubset(PUBLISHED_INPUTS), "published input overrides are malformed")
    published = {}
    for path, digest in PUBLISHED_INPUTS.items():
        data = overrides[path] if path in overrides else _repo_file(path)
        _require_sha(data, digest, "published input " + path)
        published[path] = data
    _require_sha(published[PRIOR_READER_REL], PRIOR_READER_SHA, "pinned descent reader")
    return {"protocol": protocol, "protocolData": protocol_data, "published": published}


def _load_baselines(contract):
    protocol = contract["protocol"]
    accepted = _parse_json(contract["published"][BASELINES_PATH], "accepted15 baselines")
    _need(type(accepted) is dict and tuple(accepted) ==
          ("status", "scope", "source", "sourceSha256", "rows", "reuseBoundary"),
          "accepted15 baseline schema/order changed")
    _need(accepted["status"] == "PINNED" and accepted["scope"] ==
          "All14 informed accepted15 regressions; not unseen" and
          accepted["source"] == FINAL_NATIVE_PATH and
          accepted["sourceSha256"] == PUBLISHED_INPUTS[FINAL_NATIVE_PATH] and
          type(accepted["rows"]) is list and len(accepted["rows"]) == len(ROOTS),
          "accepted15 baseline identity changed")
    expected_rows = [protocol["selectedBaselines"][seed] for seed in ROOTS]
    _need(_typed_equal(accepted["rows"], expected_rows),
          "accepted15 rows differ from protocol selected baselines")
    selected = {seed: accepted["rows"][index] for index, seed in enumerate(ROOTS)}
    raw = contract["published"][FINAL_NATIVE_PATH]
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ReaderError("accepted15 native text is not strict UTF-8") from exc
    whitespace = {}
    for line in text.splitlines():
        if not line.startswith(WHITESPACE_PREFIX):
            continue
        row = _parse_json(line[len(WHITESPACE_PREFIX):].encode("utf-8"), "accepted15 whitespace")
        _need(type(row) is dict, "accepted15 whitespace row is not an object")
        if row.get("version") != VERSION:
            continue
        _need(row.get("seed") in ROOTS, "accepted15 whitespace row identity changed")
        key = row["seed"]
        _need(key not in whitespace, "accepted15 whitespace row duplicated")
        whitespace[key] = row
    _need(tuple(whitespace) == ROOTS, "accepted15 whitespace rows lack exact root order")
    return {"selected": selected, "whitespace": whitespace}


def _load_prior_contract(contract):
    try:
        prior_contract = PRIOR._load_contract()
        prior = PRIOR._load_prior_contract(prior_contract)
    except ReaderError:
        raise
    except (ValueError, KeyError, TypeError, OSError, AttributeError) as exc:
        raise ReaderError("pinned descent reader failed to load") from exc
    _need(_sha(prior_contract["protocolData"]) == PRIOR_PROTOCOL_SHA,
          "pinned descent protocol hash changed")
    raw = contract["published"]["docs/task-evidence/PCB/routed-slide-descent-r1/routes.txt"]
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ReaderError("published descent routes are not strict UTF-8") from exc
    reports = PRIOR._parse_report_lines(text, prior_contract["protocol"])
    PRIOR._assess_routes(reports, {"protocol": prior_contract["protocol"]}, prior)
    summaries = {}
    for prefix, row, _line in reports:
        if prefix == PRIOR.SUMMARY_PREFIX:
            _need(row.get("seed") in ("5", "2") and row["seed"] not in summaries,
                  "published descent summary identity changed")
            summaries[row["seed"]] = row
    _need(tuple(summaries) == ("5", "2"), "published descent summaries lack exact root order")
    return {
        "module": PRIOR, "contract": prior_contract, "prior": prior,
        "summaries": summaries,
    }


def _load_suite_manifest(phase, protocol, raw_override=None):
    _need(phase in ("unit", "routes"), "unknown native phase")
    name = "unit-suites.json" if phase == "unit" else "routes-suites.json"
    expected = UNIT_EXPECTED if phase == "unit" else ROUTES_EXPECTED
    raw = _task_file(name, 64 * 1024) if raw_override is None else raw_override
    _require_sha(raw, SUITE_MANIFEST_SHA[name], "sealed " + phase + " suite manifest")
    actual = _parse_json(raw, phase + " suite manifest")
    _need(_typed_equal(actual, [list(item) for item in expected]), phase + " suite manifest changed")
    return actual, _sha(raw)


def _parse_report_lines(text, protocol):
    _need(type(text) is str and len(text.encode("utf-8", errors="strict")) <= protocol["maxFileBytes"],
          "native phase text is missing or too large")
    prefixes = (WHITESPACE_PREFIX, LEG_PREFIX, SUMMARY_PREFIX)
    reports = []
    for line in text.splitlines():
        if not line.startswith("PCB_"):
            continue
        prefix = next((item for item in prefixes if line.startswith(item)), None)
        _need(prefix is not None, "unknown native PCB report prefix")
        row = _parse_json(line[len(prefix):].encode("utf-8"), "native report")
        _need(type(row) is dict, "native report must be an object")
        reports.append((prefix, row, line))
        _need(len(reports) <= MAX_REPORTS, "native report count exceeds bound")
    return reports


def _validate_leg_row(row, seed, baselines, protocol):
    expected = baselines["selected"][seed]
    _need(type(row) is dict and tuple(row) == tuple(expected),
          "routed-leg row schema/order changed for " + seed)
    _bounded_int(row["elapsedMillis"], "routed-leg elapsedMillis", 0, protocol["maxCaseMillis"])
    actual = dict(row)
    wanted = dict(expected)
    actual.pop("elapsedMillis")
    wanted.pop("elapsedMillis")
    _need(_typed_equal(actual, wanted), "routed-leg non-time baseline changed for " + seed)


def _validate_whitespace_row(row, seed, baselines):
    _need(_typed_equal(row, baselines["whitespace"][seed]),
          "current15 whitespace baseline changed for " + seed)


def _validate_initial_source(source, seed, baseline):
    _need(type(source) is dict and tuple(source) == SOURCE_FIELDS,
          "cohort source schema/order changed")
    for key in ("liveGraphBefore", "liveGraphAfter", "geometryBefore", "geometryAfter",
                "traceProvenanceHash", "reachabilityHash"):
        _hex_digest(source[key], "source " + key)
    for key in ("copperLength", "area", "componentCount", "padCount", "traceCount",
                "sourceRouteSourceCount", "padPairCount"):
        _bounded_int(source[key], "source " + key, 0, MAX_COUNT * 100)
    _need(source["liveGraphBefore"] == source["liveGraphAfter"] == baseline["liveBoardGraphSha256"] and
          source["geometryBefore"] == source["geometryAfter"] == baseline["geometrySha256"] and
          source["copperLength"] == baseline["copperManhattanLength"] and
          source["area"] == baseline["area"] and
          source["componentCount"] == baseline["parts"] and
          source["padCount"] == baseline["pads"] and
          source["traceCount"] > 0 and
          source["sourceRouteSourceCount"] == source["traceCount"] and
          source["padPairCount"] == source["padCount"] * (source["padCount"] - 1) // 2,
          "initial source does not match the sealed selected baseline")
    return source


def _validate_initial_metrics(metrics, source, baseline):
    _need(type(metrics) is dict and tuple(metrics) == METRICS_FIELDS,
          "cohort source metrics schema/order changed")
    for key in ("area", "copperLength", "vias", "largestProtectedEmptyRectangleArea"):
        _bounded_int(metrics[key], "source metrics " + key, 0, MAX_COUNT * 100)
    _need(metrics["area"] > 0 and metrics["copperLength"] > 0 and
          0 <= metrics["largestProtectedEmptyRectangleArea"] <= metrics["area"],
          "source metrics area/copper/protected void relationship changed")
    for key in ("geometrySha256", "stableIdentitySha256", "labelIdentitySha256"):
        _hex_digest(metrics[key], "source metrics " + key)
    _need(metrics["generationMetadata"] == SOURCE_GENERATION and
          metrics["area"] == baseline["area"] and
          metrics["copperLength"] == baseline["copperManhattanLength"] and
          metrics["vias"] == baseline["vias"] and
          metrics["largestProtectedEmptyRectangleArea"] == baseline["largestProtectedEmptyRectangleArea"] and
          metrics["geometrySha256"] == source["geometryBefore"],
          "source metrics differ from the sealed selected baseline")
    return metrics


def _root_prior(prior_bundle, seed, source, metrics):
    root_prior = dict(prior_bundle["prior"])
    root_prior["publishedSourceFacts"] = {
        seed: {"source": copy.deepcopy(source), "metrics": copy.deepcopy(metrics)},
    }
    root_prior["physicalProtocol"] = prior_bundle["prior"]["physicalProtocol"]
    return root_prior


def _time_shape(summary):
    value = copy.deepcopy(summary)
    value["elapsedMillis"] = "__TIME__"
    for step in value["steps"]:
        for row in step["rows"]:
            timing = row["feasibility"]["timing"]
            for field in ("constructionMillis", "placementMillis", "geometryMillis",
                          "twoLayerMillis", "proofMillis", "totalMillis"):
                if timing[field] is not None:
                    timing[field] = "__TIME__"
    return value


def _validate_summary(summary, seed, contract, baselines, prior_bundle):
    protocol = contract["protocol"]
    _need(type(summary) is dict and tuple(summary) == SUMMARY_FIELDS,
          "cohort summary schema/order changed")
    _need(_canonical_seed(summary["seed"], "cohort seed") == seed and
          type(summary["label"]) is str and summary["label"] == "seed" + seed and
          type(summary["version"]) is int and summary["version"] == VERSION and
          summary["phase"] == protocol["phase"] and
          summary["selectedRoute"] == SELECTED_ROUTE and
          type(summary["newRouteRequests"]) is int and summary["newRouteRequests"] == 0 and
          type(summary["selectedMutation"]) is bool and summary["selectedMutation"] is False and
          _typed_equal(summary["limits"], protocol["limits"]),
          "cohort summary identity/selection/limits changed for " + seed)
    baseline = protocol["selectedBaselines"][seed]
    _bounded_int(summary["selectedAttempt"], "selectedAttempt", 0, 5)
    _need(summary["selectedAttempt"] == baseline["selectedAttempt"],
          "selected attempt differs from the sealed baseline for " + seed)
    _bounded_int(summary["elapsedMillis"], "cohort elapsedMillis", 0, protocol["maxCaseMillis"])
    source = _validate_initial_source(summary["source"], seed, baseline)
    source_metrics = _validate_initial_metrics(summary["sourceMetrics"], source, baseline)
    root_prior = _root_prior(prior_bundle, seed, source, source_metrics)
    initial_control = summary["initialControl"]
    _need(type(initial_control) is dict and tuple(initial_control) == CONTROL_FIELDS,
          "initial control schema/order changed for " + seed)
    initial_control_metrics = PRIOR._validate_control(
        initial_control, source, source_metrics, seed)
    steps = summary["steps"]
    _need(type(steps) is list and 0 < len(steps) <= protocol["limits"]["maxAcceptedSteps"],
          "cohort step count is outside its bound for " + seed)
    _need(_typed_equal(initial_control, steps[0]["control"]),
          "initialControl is not exactly the first step control for " + seed)
    accepted_count = evaluated = validated = rejected = compactions = timing_total = 0
    current_metrics = source_metrics
    terminal = None
    last_info = None
    for step_number, step in enumerate(steps, 1):
        _need(step_number == 1 or accepted_count > 0,
              "later cohort step lacks a preceding accepted step")
        info = PRIOR._validate_step(
            step, step_number, seed, protocol, root_prior, current_metrics,
            source_metrics, initial_control_metrics, evaluated)
        if step_number == 1:
            _need(_typed_equal(info["source"], source) and
                  _typed_equal(info["sourceMetrics"], source_metrics),
                  "first cohort step source differs from selected source")
        evaluated += info["evaluated"]
        validated += info["validated"]
        rejected += info["rejected"]
        compactions += info["compactions"]
        timing_total += info["timingTotal"]
        if info["acceptedRowIndex"] is not None:
            accepted_count += 1
            _need(info["acceptedMetrics"] is not None, "accepted row lacks chained metrics")
            current_metrics = info["acceptedMetrics"]
            if accepted_count >= protocol["limits"]["maxAcceptedSteps"]:
                terminal = "STEP_CAP"
            elif evaluated >= protocol["limits"]["maxEvaluatedMoves"]:
                terminal = "EVALUATION_CAP"
        else:
            if evaluated >= protocol["limits"]["maxEvaluatedMoves"]:
                terminal = "EVALUATION_CAP"
            elif not info["moves"]:
                terminal = "NO_STRUCTURAL_MOVES"
            elif info["evaluated"] == len(info["moves"]):
                terminal = "NO_STRICT_IMPROVEMENT"
            else:
                raise ReaderError("cohort step ended without acceptance or a terminal bound")
        last_info = info
        if terminal is not None:
            _need(step_number == len(steps), "cohort summary continues after terminal status")
            break
        _need(step_number < protocol["limits"]["maxAcceptedSteps"],
              "cohort summary exceeds accepted-step bound")
    _need(last_info is not None and terminal in protocol["statuses"] and
          summary["status"] == terminal,
          "cohort termination status does not match its bounds")
    PRIOR._validate_metrics(
        summary["finalMetrics"], seed, protocol, expected=current_metrics,
        generation=current_metrics["generationMetadata"], identity=source_metrics)
    aggregate = {
        "steps": len(steps), "acceptedSteps": accepted_count,
        "evaluatedMoves": evaluated, "validatedMoves": validated,
        "rejectedMoves": rejected, "compactions": compactions,
    }
    _need(_typed_equal(summary["aggregate"], aggregate),
          "cohort aggregate does not match every step and row")
    _need(summary["elapsedMillis"] >= timing_total,
          "cohort elapsedMillis does not cover row timing totals")
    if seed in protocol["matchedRoots"]:
        expected = prior_bundle["summaries"][seed]
        _need(_typed_equal(_time_shape(summary), _time_shape(expected)),
              "published root " + seed + " differs outside declared time paths")
    return {"seed": seed, "status": terminal, "aggregate": aggregate}


def _assess_routes(reports, contract, prior_bundle):
    protocol = contract["protocol"]
    baselines = _load_baselines(contract)
    _need(type(reports) is list and len(reports) == len(ROOTS) * 3,
          "cohort routes phase requires exactly 42 ordered PCB reports")
    old_rows = []
    summaries = []
    cursor = 0
    for seed in ROOTS:
        _need(type(reports[cursor]) is tuple and len(reports[cursor]) == 3 and
              type(reports[cursor][1]) is dict,
              "whitespace report tuple is malformed")
        _need(reports[cursor][0] == WHITESPACE_PREFIX and reports[cursor][1].get("seed") == seed,
              "whitespace report is missing or out of order for " + seed)
        old_rows.append(reports[cursor]); cursor += 1
        _need(type(reports[cursor]) is tuple and len(reports[cursor]) == 3 and
              type(reports[cursor][1]) is dict,
              "routed-leg report tuple is malformed")
        _need(reports[cursor][0] == LEG_PREFIX and reports[cursor][1].get("seed") == seed,
              "routed-leg report is missing or out of order for " + seed)
        old_rows.append(reports[cursor]); cursor += 1
        _need(type(reports[cursor]) is tuple and len(reports[cursor]) == 3 and
              type(reports[cursor][1]) is dict,
              "cohort summary tuple is malformed")
        _need(reports[cursor][0] == protocol["summaryPrefix"] and reports[cursor][1].get("seed") == seed,
              "cohort summary is missing or out of order for " + seed)
        summaries.append((seed, reports[cursor][1])); cursor += 1
    _need(cursor == len(reports), "extra or foreign cohort reports remain")
    for prefix, row, _line in old_rows:
        seed = row.get("seed")
        if prefix == WHITESPACE_PREFIX:
            _validate_whitespace_row(row, seed, baselines)
        else:
            _validate_leg_row(row, seed, baselines, protocol)
    validated = [_validate_summary(row, seed, contract, baselines, prior_bundle)
                 for seed, row in summaries]
    return {
        "status": "PASS", "scope": protocol["scope"], "roots": len(ROOTS),
        "rows": len(ROOTS), "oldBaselineReports": len(ROOTS) * 2,
        "cohortSummaries": validated, "routeReports": len(reports),
        "observationDisposition": "PASS; bounded informed14 cohort observation only",
        "fitnessDisposition": "NOT_EVALUATED",
    }


def _unit_pass_line(line, suite, protocol):
    expected = "PASS: " + suite[1]
    match = re.fullmatch(
        re.escape(expected) + r"assertions=([1-9][0-9]*) fixtures=" +
        str(protocol["unitFixtureCounts"][suite[0]]) + r" evaluator=" +
        re.escape(protocol["unitEvaluators"][suite[0]]), line)
    _need(match is not None, "unit PASS marker/count/evaluator is malformed for " + suite[0])


def _phase_summary(text, receipt, timing, suites, phase, contract, prior_bundle):
    protocol = contract["protocol"]
    _need(type(timing) is dict and tuple(timing) == TIMING_FIELDS,
          "native timing schema/order changed")
    expected_suites, _manifest_sha = _load_suite_manifest(phase, protocol)
    _need(_typed_equal(suites, expected_suites) and timing["phase"] == phase,
          "native suite rows or phase differ from the sealed contract")
    _need(timing["status"] == "PASS" and type(timing["status"]) is str and
          type(timing["exitCode"]) is int and timing["exitCode"] == 0 and
          type(timing["suiteCount"]) is int and timing["suiteCount"] == len(expected_suites),
          "native phase status/exit/suite count failed")
    elapsed = timing["elapsedSeconds"]
    _need(type(elapsed) in (int, float) and not isinstance(elapsed, bool) and
          math.isfinite(elapsed) and 0 <= elapsed <= protocol["maxPhaseElapsedSeconds"],
          "native phase elapsed time is malformed or outside its bound")
    _need(timing["scope"] == protocol["timingScope"] and
          type(timing["terminalCleanupIncluded"]) is bool and timing["terminalCleanupIncluded"] is True and
          timing["cleanupDuration"] == "NOT_SEPARATELY_MEASURED",
          "native phase timing/cleanup boundary changed")
    _need(type(text) is str and type(receipt) is str and
          len(text.encode("utf-8", errors="strict")) <= protocol["maxFileBytes"] and
          len(receipt.encode("utf-8", errors="strict")) <= protocol["maxFileBytes"],
          "native phase log/receipt is missing or too large")
    text_lines, receipt_lines = text.splitlines(), receipt.splitlines()
    _need(not any(FAILURE_LINE.search(line) or SKIP_OR_FAILURE_WORD.search(line)
                  for line in text_lines + receipt_lines),
          "native phase contains failure, timeout, skip, or cancellation output")
    terminal = TERMINAL_TEMPLATE.format(count=len(expected_suites))
    _need(text_lines.count(terminal) == 1 and text_lines.count(CLEANUP_LINE) == 1 and
          terminal not in receipt_lines and CLEANUP_LINE not in receipt_lines and
          text_lines.index(terminal) < text_lines.index(CLEANUP_LINE),
          "native terminal PASS or cleanup boundary is missing/duplicated")
    suite_passes = [line for line in text_lines if line.startswith("PASS: ") and line != terminal]
    receipt_passes = [line for line in receipt_lines if line.startswith("PASS: ")]
    _need(suite_passes == receipt_passes and len(suite_passes) == len(expected_suites),
          "suite PASS lines differ between terminal log and child receipt")
    for line, suite in zip(suite_passes, expected_suites):
        if phase == "unit":
            _unit_pass_line(line, suite, protocol)
        else:
            _need(line == "PASS: " + suite[1] + protocol["routePassDetail"],
                  "routes PASS detail/order differs from sealed contract")
        _need(text_lines.index(line) < text_lines.index(terminal),
              "suite PASS appeared after terminal marker")
    reports = _parse_report_lines(text, protocol)
    receipt_reports = _parse_report_lines(receipt, protocol)
    _need([item[2] for item in reports] == [item[2] for item in receipt_reports],
          "native report lines differ between terminal log and child receipt")
    terminal_position = text_lines.index(terminal)
    _need(all(position < terminal_position for position, line in enumerate(text_lines)
              if line.startswith("PCB_")), "native report appeared after terminal marker")
    if phase == "unit":
        _need(not reports and not receipt_reports, "unit contract leaked cohort reports")
        cohort = None
    else:
        report_positions = [position for position, line in enumerate(text_lines) if line.startswith("PCB_")]
        suite_positions = [text_lines.index(line) for line in suite_passes]
        _need(len(reports) == 42 and max(report_positions) < min(suite_positions),
              "all cohort reports must precede route suite PASS")
        receipt_report_positions = [position for position, line in enumerate(receipt_lines) if line.startswith("PCB_")]
        receipt_suite_positions = [receipt_lines.index(line) for line in receipt_passes]
        _need(len(receipt_reports) == 42 and max(receipt_report_positions) < min(receipt_suite_positions),
              "child receipt reports must precede route suite PASS")
        cohort = _assess_routes(reports, contract, prior_bundle)
    return {
        "status": "PASS", "phase": phase, "suiteCount": len(expected_suites),
        "suiteClasses": [row[0] for row in expected_suites], "reportRows": len(reports),
        "orderedMarkers": [row[1] for row in expected_suites], "cohort": cohort,
    }


def check_phase(text, receipt, timing, suites, phase):
    contract = _load_contract()
    prior_bundle = _load_prior_contract(contract)
    expected, _sha_value = _load_suite_manifest(phase, contract["protocol"])
    _need(_typed_equal(suites, expected), "caller suite manifest differs from sealed bytes")
    return _phase_summary(text, receipt, timing, suites, phase, contract, prior_bundle)


def _read_phase_files(phase, contract, prior_bundle):
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
    summary = _phase_summary(text, receipt, timing, suites, phase, contract, prior_bundle)
    return summary, {name: _sha(data) for name, data in raw.items()}, manifest_sha


def _unit_validation(contract, summary, phase_inputs, manifest_sha):
    protocol = contract["protocol"]
    return {
        "schema": 1, "status": "PASS", "phase": "unit",
        "scope": "Informed14 routed slide cohort unit fixtures only; native routes and fitness are not evaluated.",
        "baseCommit": protocol["baseCommit"], "protocolSha256": _sha(contract["protocolData"]),
        "priorAssessSha256": PRIOR_READER_SHA,
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
    cohort = summary.get("cohort")
    _need(type(cohort) is dict and cohort.get("status") == "PASS",
          "selected routed cohort observation is missing or failed")
    return {
        "schema": 1, "assessmentStatus": "PASS", "scope": protocol["scope"],
        "baseCommit": protocol["baseCommit"], "protocolSha256": _sha(contract["protocolData"]),
        "priorAssessSha256": PRIOR_READER_SHA,
        "publishedInputsSha256": {name: _sha(data) for name, data in contract["published"].items()},
        "unitPrerequisiteSha256": unit_prerequisite_sha,
        "routesSuiteManifestSha256": manifest_sha, "routePhaseInputsSha256": phase_inputs,
        "nativePhase": {"status": "PASS", "phase": "routes", "evidence": summary},
        "cohortObservation": cohort,
        "observationDisposition": "PASS; bounded informed14 cohort observation only",
        "fitnessDisposition": "NOT_EVALUATED", "policyDisposition": "NO_POLICY_OR_ACCEPTANCE_CHANGE",
        "productionChange": "NONE", "limits": list(protocol["limitsDisclosure"]),
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
        prior_bundle = _load_prior_contract(contract)
        summary, phase_inputs, manifest_sha = _read_phase_files(args.phase, contract, prior_bundle)
        if args.phase == "unit":
            _write_artifact("unit-validation.json",
                            _unit_validation(contract, summary, phase_inputs, manifest_sha))
            print("PASS unit phase; routes and fitness are not evaluated")
            return 0
        unit_data = _task_file("unit-validation.json", 1024 * 1024)
        unit_summary, unit_inputs, unit_manifest_sha = _read_phase_files("unit", contract, prior_bundle)
        expected_unit = _unit_validation(contract, unit_summary, unit_inputs, unit_manifest_sha)
        prerequisite_sha = _validate_unit_prerequisite(unit_data, expected_unit)
        _write_artifact("assessment.json", _routes_assessment(
            contract, summary, phase_inputs, manifest_sha, prerequisite_sha))
        print("PASS routes phase; cohort observation PASS; fitness NOT_EVALUATED")
        return 0
    except ReaderError as exc:
        print("FAIL offline reader: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
