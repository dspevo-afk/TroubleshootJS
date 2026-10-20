"""Strict offline reader for the closed physical-feasibility census packet."""

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
TASK_REL = "docs/task-evidence/PCB/routed-passive-feasibility-census-r1"
PRIOR_ASSESS_REL = "docs/task-evidence/PCB/routed-passive-leg-census-r1/assess.py"

PROTOCOL_SHA = "6982e4e88969f599c3c859ed8bb9fc2e67946f24c9f5b0453c7e705231102d30"
PRIOR_ASSESS_SHA = "f9b79e21875864aecde86e41d1b49d955d6f9201585cebebf462ffd87fb82229"
PUBLISHED_INPUTS = {
    "docs/task-evidence/PCB/routed-passive-leg-census-r1/native-inputs.json":
        "c3567e48821637702bd1f88ffc05a09ab34f8f0dde0f13553e5f5885acf1da05",
    "docs/task-evidence/PCB/routed-passive-leg-census-r1/root-review.json":
        "45eed3d73435073eb3eeb7126e7be04374e1fce0354843bf2b364da8c1a5631d",
    "docs/task-evidence/PCB/routed-passive-leg-census-r1/protocol.json":
        "82212f6b7df2ecfdc3301a21908775ebdc6d63dac47539f2635693cd703236fd",
    "docs/task-evidence/PCB/routed-passive-leg-census-r1/routes.txt":
        "ea6076b1189b44ada29a7638e5892990cd7271117594a875d28a485f2012f22e",
    "docs/task-evidence/PCB/successful-route-pool-r1/native-inputs.json":
        "6b1a003c2a76bad4488d04e69b82ab86b87fa4e2284ed7545c698cc94f778ea8",
    "docs/task-evidence/PCB/successful-route-pool-r1/routes.txt":
        "c75c440c15780f4cf222ad882a19e2cad6d55fcedf10c993bd66fad6e209b63f",
    "docs/task-evidence/PCB/routed-passive-move-proof-r1/native-inputs.json":
        "31e18fc232029db9f0f2b70cb13360a416f4eab97d9ff318a9d980ada102482a",
    "docs/task-evidence/PCB/routed-passive-move-proof-r1/root-review.json":
        "12d5847a66312cfb1afd92d8e1625437da4c6e4076348948c7f11e9600025bfb",
    "docs/task-evidence/PCB/routed-passive-move-proof-r1/protocol.json":
        "6bdde6eed11b1b441e8440843d0130eecf70079043017472c1e8cf7ce214568d",
    "docs/task-evidence/PCB/routed-passive-move-proof-r1/routes.txt":
        "8baa2c846408214a6d150a4646802f86dc8e4c2cdd18957fcc3f135f738b0ffe",
}
SUITE_MANIFEST_SHA = {
    "unit-suites.json":
        "718f18342482b23a4731abd6d919f0c4ab44a789c9af348c0be6ef6df373bc47",
    "routes-suites.json":
        "d8bc074e52d22f07463a7398b8bae6e36d33f6469d37fd63705814b03c247808",
}

CENSUS_ROUTES_PATH = "docs/task-evidence/PCB/routed-passive-leg-census-r1/routes.txt"
POOL_ROUTES_PATH = "docs/task-evidence/PCB/successful-route-pool-r1/routes.txt"
WHITESPACE_PREFIX = "PCB_WHITESPACE_CASE "
CENSUS_PREFIX = "PCB_ROUTED_LEG_CENSUS "
CASE_PREFIX = "PCB_ROUTED_LEG_CASE "
POOL_PREFIX = "PCB_SUCCESSFUL_ROUTE_POOL "
ROOTS = ("5", "2")
VERSION = 15
MAX_SIGNED_LONG = (1 << 63) - 1
MIN_SIGNED_LONG = -(1 << 63)
MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_REPORTS = 100000
MAX_FIELD_TEXT = 1024 * 1024
MAX_COUNT = 1000000
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
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
    r"\b(?:SKIPPED?|IGNORED|TIMEOUT|TIMED\s+OUT|CANCELLED)\b",
    re.IGNORECASE,
)

SUMMARY_FIELDS = (
    "seed", "label", "version", "phase", "status", "newRouteRequests",
    "selectedMutation", "constructionCount", "source", "rows", "aggregate",
    "elapsedMillis",
)
SOURCE_FIELDS = (
    "liveGraphBefore", "liveGraphAfter", "geometryBefore", "geometryAfter",
    "copperLength", "area", "componentCount", "padCount", "traceCount",
    "traceProvenanceHash", "sourceRouteSourceCount", "reachabilityHash",
    "padPairCount",
)
ROW_FIELDS = (
    "move", "candidate", "disposition", "stage", "reason", "message",
    "checks", "proof", "timing",
)
MOVE_FIELDS = (
    "componentId", "dx", "dy", "analyticCopperDelta", "actualCopperDelta",
)
CANDIDATE_FIELDS = (
    "geometryHash", "componentCount", "padCount", "traceCount",
    "affectedTraceCount", "movedPadCount", "copperLength", "area",
    "generationMetadata",
)
CHECK_FIELDS = (
    "traceEndpointOnlyVerified", "unrelatedGeometryVerified", "sourceImmutable",
    "placementValidated", "geometryValidated", "twoLayerValidated",
)
PROOF_FIELDS = (
    "placementValidated", "geometryValidated", "twoLayerValidated",
    "padPairCount", "sourceReachabilityHash", "candidateReachabilityHash",
    "sourceRouteSourceCount", "candidateRouteSourceCount",
    "sourceTraceProvenanceHash", "candidateTraceProvenanceHash",
    "traceEndpointOnlyVerified", "unrelatedGeometryVerified",
    "conductorReachabilityVerified", "traceProvenanceVerified",
)
ROW_TIMING_FIELDS = (
    "constructionMillis", "placementMillis", "geometryMillis",
    "twoLayerMillis", "proofMillis", "totalMillis",
)
AGGREGATE_FIELDS = (
    "structuralDirections", "constructionCount", "physicallyValidated",
    "physicallyRejected", "placementRejected", "geometryRejected",
    "twoLayerRejected",
)
DIRECTIONS = ((-10, 0), (10, 0), (0, -10), (0, 10))
REJECTION_STAGES = ("PLACEMENT", "GEOMETRY", "TWO_LAYER")
DISPOSITION_REJECTED = "PHYSICAL_REJECTED"
DISPOSITION_VALIDATED = "PHYSICAL_VALIDATED"
SUMMARY_STATUSES = {"5": "NO_TARGET", "2": "CENSUS_COMPLETE"}
ROW_DISPOSITIONS = (DISPOSITION_REJECTED, DISPOSITION_VALIDATED)
ROW_STAGES = ("PLACEMENT", "GEOMETRY", "TWO_LAYER", "COMPLETE")
VALIDATED_ROW_REASON = None
SUMMARY_TIMING_SCOPE = (
    "Feasibility observer callback only, measured with monotonic nanos truncated to milliseconds; "
    "no target is zero; complete generation elapsed remains in the original selected CASE")
GENERATION_METADATA = "NOT_ASSIGNED_PRIVATE_CANDIDATE"

CLASSIFICATION_OWNER_SHA = {
    "src/com/lushprojects/circuitjs1/client/PcbBoardLayout.java":
        "68ef5eb7c2ed16f40d64fdf112ee4220498a16b2998d0613c3a2480f6218da6b",
    "src/com/lushprojects/circuitjs1/client/PcbTwoLayerRules.java":
        "b04f9906d726117412d62b6022ea5b8e2f26bf111063292b0d7560f51b0fb8cf",
    "src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java":
        "c2068645584013a658c97e9940c20e0a7a887da24c621d1a0e05f5e447979ba9",
    "src/com/lushprojects/circuitjs1/client/PcbAccessPlanner.java":
        "6615906eb5df6fcf322e084fc7a32f83c5eda1c7b09a32d6bb2c2343eb0396e2",
    "src/com/lushprojects/circuitjs1/client/PcbConductorBuilder.java":
        "c253b133070cbfdec2b0d5b917476ea6262c3edfd2bbacb847218f5165165d37",
    "src/com/lushprojects/circuitjs1/client/PcbConductorGraph.java":
        "a5c84942c07c5d7f01caa3fe12b7700235132fbc81f4cb86b3718029b216c972",
    "src/com/lushprojects/circuitjs1/client/PcbTraceClearance.java":
        "19984434852043ce9ef961ae4483cae36028e6deb444ae79fb1488b1c61ad5d4",
}
PLACEMENT_REASONS = (
    "ENVELOPE_ACCESS_OR_DOMAIN", "STRANDED_ESCAPE", "DOMAIN_CORRIDOR",
    "DISCONNECTED_ESCAPE_CHANNEL",
)
GEOMETRY_PREFIX_REASONS = {
    "COMPONENT_BODY_OVERLAP": "PCB component bodies overlap: ",
    "COMPONENT_COURTYARD_OVERLAP": "PCB component routing courtyards overlap: ",
    "PAD_OVERLAP": "PCB pads overlap: ",
    "TRACE_OUTSIDE_BOARD": "PCB trace leaves board: ",
    "TRACE_COMPONENT_COURTYARD": "PCB trace passes through component routing courtyard: ",
    "TRACE_SELF_OVERLAP": "PCB trace has repeated or overlapping segments: ",
    "TRACE_SELF_INTERSECTION": "PCB trace self-intersects: ",
    "LABEL_COMPONENT_OVERLAP": "Silkscreen label overlaps component: ",
    "LABEL_PAD_OVERLAP": "Silkscreen label overlaps pad: ",
    "LABEL_COPPER_OVERLAP": "Silkscreen label overlaps copper: ",
    "LABEL_LABEL_OVERLAP": "PCB silkscreen labels overlap: ",
    "FOREIGN_NET_CONTACT": "Unrelated PCB conductors share copper: ",
    "FOREIGN_COPPER_CLEARANCE": "PCB traces violate copper clearance: ",
    "PHYSICAL_NET_DISCONNECTED": "PCB net is electrically disconnected: ",
}
QUALITY_REASONS = {
    "ROUTE_QUALITY_BENDS": "PCB trace has excessive bends: ",
    "ROUTE_QUALITY_DETOUR": "PCB trace has excessive detour: ",
}
TWO_LAYER_EXACT_REASONS = {
    "INSULATING_BODY_BARRIER": "Insulating body crosses an unrelated barrier",
    "INSULATING_SPAN": "Package body crosses outside its declared insulating span",
    "LAND_DOMAIN_BARRIER": "Component land crosses a physical domain barrier",
    "LEAD_DOMAIN_BARRIER": "Component lead crosses a physical domain barrier",
    "COPPER_DOMAIN_BARRIER": "P07 copper crosses a physical domain barrier",
    "DRILL_DOMAIN_BARRIER": "Drill or land crosses a physical domain barrier",
    "VIA_DOMAIN_OR_NET": "P07 via crosses a domain barrier or has an unknown net",
    "VIA_PACKAGE_COURTYARD": "P07 via drills through a package courtyard",
    "VIA_PAD_CLEARANCE": "P07 via overlaps a component pad or its clearance",
    "VIA_COPPER_CLEARANCE": "P07 via violates foreign copper clearance",
}
EXPECTED_REACHABILITY = "9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7"
EXPECTED_FIRST_REJECTION = {
    "stage": "GEOMETRY",
    "reason": "TRACE_COMPONENT_COURTYARD",
    "message": "PCB trace passes through component routing courtyard: CTRL_RETURN / C5 segment 3256,1094 -> 3226,1094 keepOut=Rect(3171,1094,80,90) startPad=U1.RETURN endPad=C5.2",
}


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


def _load_prior_reader():
    source = _repo_file(PRIOR_ASSESS_REL)
    _need(_sha(source) == PRIOR_ASSESS_SHA, "pinned prior reader hash changed")
    path = REPO.joinpath(*PurePosixPath(PRIOR_ASSESS_REL).parts)
    spec = importlib.util.spec_from_file_location(
        "pinned_routed_leg_census_assess", path)
    _need(spec is not None and spec.loader is not None,
          "pinned prior reader cannot be loaded")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    try:
        spec.loader.exec_module(module)
    except Exception as exc:
        raise ReaderError("pinned prior reader failed to load") from exc
    _need(module._sha(source) == PRIOR_ASSESS_SHA,
          "loaded prior reader differs from its pin")
    module.ReaderError = ReaderError
    return module


PRIOR = _load_prior_reader()
_typed_equal = PRIOR._typed_equal


def _parse_json(data, label):
    try:
        return PRIOR._parse_json(data, label)
    except ReaderError:
        raise
    except ValueError as exc:
        raise ReaderError(str(exc)) from exc


def _require_sha(data, expected, label):
    _need(type(expected) is str and HEX64.fullmatch(expected) is not None and
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
    _need(type(value) is int and not isinstance(value, bool) and
          low <= value <= high, label + " is not a bounded integer")
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
    _need(type(value) is str and HEX64.fullmatch(value) is not None,
          label + " is not a lowercase SHA-256 digest")
    return value


def _validate_protocol(protocol):
    expected_keys = (
        "schema", "baseCommit", "scope", "roots", "version", "density",
        "phase", "targetStrategy", "targetAttempt",
        "constructionLimitPerTargetCallback", "sourceRule", "summaryPrefix",
        "reportOrder", "summaryFields", "summaryStatuses", "rowDispositions",
        "rowStages", "validatedRowReason", "summaryTimingScope", "sourceFields",
        "rowFields",
        "moveFields", "candidateFields", "checkFields", "proofFields",
        "timingFields", "aggregateFields", "expectedMoves", "expectedSource",
        "expectedFirstRejection", "placementReasons", "geometryPrefixReasons",
        "twoLayerExactReasons", "qualityReasons", "rejectionRule", "stageSemantics",
        "unitFixtureCounts", "unitEvaluators", "routePassDetail", "timingScope",
        "maxPhaseElapsedSeconds", "maxCaseMillis", "maxFileBytes",
        "publishedInputs", "priorCensusReader", "classificationOwnerSha256",
        "suiteManifestSha256", "limits",
    )
    _need(type(protocol) is dict and tuple(protocol) == expected_keys,
          "feasibility protocol schema/order changed")
    _need(protocol["schema"] == 1 and protocol["baseCommit"] ==
          "8ddbd8af545a8e66bf9bf52985b2486a0a734481" and
          protocol["scope"] ==
          "Independent physical census of all31 nonincreasing structural directions; no selection or production change",
          "feasibility protocol identity/scope changed")
    _need(_typed_equal(protocol["roots"], list(ROOTS)) and
          type(protocol["version"]) is int and protocol["version"] == VERSION and
          type(protocol["density"]) is int and protocol["density"] == 14 and
          protocol["phase"] == "FINALIZED_ROUTE_CANDIDATE" and
          protocol["targetStrategy"] == "P07_FULLER_TWO_LAYER" and
          protocol["targetAttempt"] == 2 and
          protocol["constructionLimitPerTargetCallback"] == 31 and
          protocol["sourceRule"] ==
          "Every candidate starts from the identical original target, never another candidate" and
          protocol["summaryPrefix"] ==
          "PCB_ROUTED_PASSIVE_FEASIBILITY_CENSUS ",
          "feasibility target/source rule changed")
    _need(_typed_equal(protocol["reportOrder"], [
        WHITESPACE_PREFIX, CENSUS_PREFIX, CASE_PREFIX, protocol["summaryPrefix"]]),
          "feasibility report order changed")
    _need(_typed_equal(protocol["summaryStatuses"], SUMMARY_STATUSES) and
          _typed_equal(protocol["rowDispositions"], list(ROW_DISPOSITIONS)) and
          _typed_equal(protocol["rowStages"], list(ROW_STAGES)) and
          protocol["validatedRowReason"] is VALIDATED_ROW_REASON and
          protocol["summaryTimingScope"] == SUMMARY_TIMING_SCOPE,
          "feasibility outcome enums/timing scope changed")
    for key, expected in (
        ("summaryFields", SUMMARY_FIELDS), ("sourceFields", SOURCE_FIELDS),
        ("rowFields", ROW_FIELDS), ("moveFields", MOVE_FIELDS),
        ("candidateFields", CANDIDATE_FIELDS), ("checkFields", CHECK_FIELDS),
        ("proofFields", PROOF_FIELDS), ("timingFields", ROW_TIMING_FIELDS),
        ("aggregateFields", AGGREGATE_FIELDS),
    ):
        _need(_typed_equal(protocol[key], list(expected)),
              "feasibility " + key + " changed")
    expected_moves = protocol["expectedMoves"]
    _need(type(expected_moves) is list and len(expected_moves) == 31,
          "feasibility expected move count changed")
    for index, move in enumerate(expected_moves):
        _need(type(move) is dict and tuple(move) == MOVE_FIELDS,
              "feasibility expected move schema changed at " + str(index))
        _bounded_text(move["componentId"], "expected move component")
        for field in ("dx", "dy", "analyticCopperDelta", "actualCopperDelta"):
            _bounded_int(move[field], "expected move " + field)
        _need(move["actualCopperDelta"] == move["analyticCopperDelta"],
              "expected move actual/analytic delta differs")
    _need(_typed_equal(protocol["expectedSource"], {
        "area": 9313352, "copperLength": 51860, "traceCount": 145,
        "geometryHash": "e5242445527c50145b0848e79efb045518ef56e0a3f559ddeeb9ba798efd6c6e",
        "componentCount": 56, "padCount": 135, "sourceRouteSourceCount": 145,
        "padPairCount": 9045, "reachabilityHash": EXPECTED_REACHABILITY,
    }), "feasibility source expectations changed")
    _need(_typed_equal(protocol["expectedFirstRejection"], EXPECTED_FIRST_REJECTION),
          "feasibility first rejection changed")
    _need(_typed_equal(protocol["placementReasons"], list(PLACEMENT_REASONS)) and
          _typed_equal(protocol["geometryPrefixReasons"], GEOMETRY_PREFIX_REASONS) and
          _typed_equal(protocol["twoLayerExactReasons"], TWO_LAYER_EXACT_REASONS) and
          _typed_equal(protocol["qualityReasons"], QUALITY_REASONS),
          "feasibility rejection catalog changed")
    _need(protocol["rejectionRule"] ==
          "Only at the actual named validator call; unknown errors, budgets, malformed construction, proof mismatches and cancellation propagate",
          "feasibility rejection rule changed")
    _need(protocol["stageSemantics"] ==
          "Outer actual validator invocation: GEOMETRY includes nested two-layer and quality checks, leaving geometryValidated false on rejection; TWO_LAYER is only the explicit later call after geometry completed",
          "feasibility stage semantics changed")
    _need(_typed_equal(protocol["unitFixtureCounts"], {
        "PcbRoutedPassiveLegCensusContractTest": 6,
        "PcbRoutedPassiveFeasibilityCensusContractTest": 6,
    }) and _typed_equal(protocol["unitEvaluators"], {
        "PcbRoutedPassiveLegCensusContractTest": "endpointLegCensus",
        "PcbRoutedPassiveFeasibilityCensusContractTest": "physicalFeasibilityCensus",
    }) and protocol["routePassDetail"] ==
          "roots=2 rows=2 evaluator=physicalFeasibilityCensus",
          "feasibility suite contract changed")
    _need(protocol["timingScope"] ==
          "Native routed passive physical census; unchanged per-JVM/search/physical limits" and
          protocol["maxPhaseElapsedSeconds"] == 36000 and
          protocol["maxCaseMillis"] == 60000 and
          protocol["maxFileBytes"] == MAX_FILE_BYTES,
          "feasibility timing bounds changed")
    _need(_typed_equal(protocol["publishedInputs"], PUBLISHED_INPUTS) and
          _typed_equal(protocol["priorCensusReader"], {
              "path": PRIOR_ASSESS_REL, "sha256": PRIOR_ASSESS_SHA,
          }) and _typed_equal(protocol["classificationOwnerSha256"],
                              CLASSIFICATION_OWNER_SHA) and
          _typed_equal(protocol["suiteManifestSha256"], SUITE_MANIFEST_SHA),
          "feasibility input/source pins changed")
    _need(type(protocol["limits"]) is list and len(protocol["limits"]) == 6 and
          all(type(item) is str and item for item in protocol["limits"]),
          "feasibility limits changed")


def _parse_report_lines(text, protocol):
    _need(type(text) is str, "native phase text is missing")
    try:
        _need(len(text.encode("utf-8", errors="strict")) <= protocol["maxFileBytes"],
              "native phase text exceeds its bound")
    except UnicodeEncodeError as exc:
        raise ReaderError("native phase text is not strict UTF-8") from exc
    prefixes = (WHITESPACE_PREFIX, CENSUS_PREFIX, CASE_PREFIX,
                protocol["summaryPrefix"])
    reports = []
    for line in text.splitlines():
        if not line.startswith("PCB_"):
            continue
        prefix = next((candidate for candidate in prefixes
                       if line.startswith(candidate)), None)
        _need(prefix is not None, "unknown native PCB report prefix")
        row = _parse_json(line[len(prefix):].encode("utf-8", errors="strict"),
                          "native report")
        _need(type(row) is dict, "native report must be an object")
        reports.append((prefix, row, line))
        _need(len(reports) <= MAX_REPORTS, "native report count exceeds bound")
    return reports


def _load_contract(protocol_override=None, published_overrides=None):
    protocol_data = (_task_file("protocol.json", 256 * 1024)
                     if protocol_override is None else protocol_override)
    _require_sha(protocol_data, PROTOCOL_SHA, "sealed feasibility protocol")
    protocol = _parse_json(protocol_data, "feasibility protocol")
    _validate_protocol(protocol)
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
    return {"protocol": protocol, "protocolData": protocol_data, "published": blobs}


def _load_pool_source(contract, source_case):
    try:
        text = contract["published"][POOL_ROUTES_PATH].decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ReaderError("sealed route-pool input is not strict UTF-8") from exc
    rows = []
    for line in text.splitlines():
        if line.startswith(POOL_PREFIX):
            row = _parse_json(line[len(POOL_PREFIX):].encode("utf-8"),
                              "published route-pool row")
            _need(type(row) is dict, "published route-pool row is not an object")
            rows.append(row)
    _need(len(rows) == 5, "sealed route-pool row count changed")
    expected = contract["protocol"]["expectedSource"]
    matches = [row for row in rows
               if row.get("seed") == "2" and row.get("caseLabel") == "seed2" and
               row.get("strategy") == "P07_FULLER_TWO_LAYER" and
               row.get("attempt") == 2 and row.get("routeIndex") == 0 and
               row.get("phase") == "FINALIZED_ROUTE_CANDIDATE" and
               row.get("geometrySha256") == expected["geometryHash"] and
               row.get("area") == expected["area"] and
               row.get("copperManhattanLength") == expected["copperLength"] and
               row.get("traceCount") == expected["traceCount"]]
    _need(len(matches) == 1, "sealed route-pool source event is not unique")
    row = matches[0]
    _hex_digest(row.get("geometrySha256"), "route-pool geometry")
    _hex_digest(row.get("sourceGraphBeforeSha256"), "route-pool source graph before")
    _hex_digest(row.get("sourceGraphAfterSha256"), "route-pool source graph after")
    _need(row["sourceGraphBeforeSha256"] == row["sourceGraphAfterSha256"] ==
          source_case["liveBoardGraphSha256"],
          "route-pool source graph differs from sealed census case")
    for field in ("area", "copperManhattanLength", "traceCount"):
        _bounded_int(row.get(field), "route-pool " + field, 0, MAX_COUNT * 100)
    return {
        "area": row["area"], "copperLength": row["copperManhattanLength"],
        "traceCount": row["traceCount"], "geometryHash": row["geometrySha256"],
        "liveGraphBefore": row["sourceGraphBeforeSha256"],
        "liveGraphAfter": row["sourceGraphAfterSha256"],
    }


def _load_prior_contract(contract):
    try:
        prior = PRIOR._load_contract()
        raw = contract["published"][CENSUS_ROUTES_PATH]
        text = raw.decode("utf-8", errors="strict")
        reports = PRIOR._parse_report_lines(text, prior["protocol"])
        assessed = PRIOR._assess_routes(
            reports, prior["selected"], prior["whitespace"], prior["protocol"])
    except ReaderError:
        raise
    except (UnicodeDecodeError, ValueError) as exc:
        raise ReaderError(str(exc)) from exc
    _need(assessed["status"] == "PASS" and assessed["rows"] == 2 and
          assessed["selectedCaseRows"] == 2,
          "sealed old census did not pass the pinned prior reader")
    summaries = {}
    cases = {}
    for prefix, row, _line in reports:
        if prefix == CENSUS_PREFIX:
            _need(row["seed"] not in summaries, "published census summary is duplicated")
            summaries[row["seed"]] = row
        elif prefix == CASE_PREFIX:
            _need(row["seed"] not in cases, "published census case is duplicated")
            cases[row["seed"]] = row
    _need(tuple(summaries) == ROOTS and tuple(cases) == ROOTS,
          "sealed old census lacks the exact root order")
    pool_source = _load_pool_source(contract, cases["2"])
    target = _target_callback(summaries["2"])
    derived = []
    for part in target["parts"]:
        for direction in part["directions"]:
            if direction["eligible"] and direction["copperDelta"] <= 0:
                derived.append({
                    "componentId": part["componentId"], "dx": direction["dx"],
                    "dy": direction["dy"],
                    "analyticCopperDelta": direction["copperDelta"],
                    "actualCopperDelta": direction["copperDelta"],
                })
    _need(_typed_equal(derived, contract["protocol"]["expectedMoves"]),
          "sealed current15 directions differ from the 31 protocol moves")
    _need(_reachability_hash(summaries["2"]) == EXPECTED_REACHABILITY,
          "sealed source net oracle differs from protocol")
    prior["publishedCensusReports"] = reports
    prior["publishedCensusSummaries"] = summaries
    prior["publishedCensusCases"] = cases
    prior["publishedPoolSource"] = pool_source
    return prior


def _load_suite_manifest(phase, protocol, raw_override=None):
    _need(phase in ("unit", "routes"), "unknown native phase")
    name = "unit-suites.json" if phase == "unit" else "routes-suites.json"
    expected = (
        [["PcbRoutedPassiveLegCensusContractTest",
          "PCB routed passive leg census contracts "],
         ["PcbRoutedPassiveFeasibilityCensusContractTest",
          "PCB routed passive feasibility census contracts "]]
        if phase == "unit" else
        [["PcbRoutedPassiveFeasibilityTrialContractTest",
          "PCB routed passive feasibility census trial contracts "]]
    )
    raw = (_task_file(name, 64 * 1024) if raw_override is None else raw_override)
    _require_sha(raw, SUITE_MANIFEST_SHA[name], "sealed " + phase + " suite manifest")
    actual = _parse_json(raw, phase + " suite manifest")
    _need(_typed_equal(actual, expected) and
          _typed_equal(protocol["suiteManifestSha256"], SUITE_MANIFEST_SHA),
          phase + " suite manifest order/content changed")
    return actual, _sha(raw)


def _target_callback(summary):
    callbacks = summary["successfulCallbacks"]
    targets = [callback for callback in callbacks if callback["target"]]
    _need(len(targets) <= 1, "census target callback is duplicated")
    _need(targets, "census target callback is missing")
    return targets[0]


def _utf16_units(value):
    try:
        return len(value.encode("utf-16-le", errors="strict")) // 2
    except UnicodeEncodeError as exc:
        raise ReaderError("pad ID is not valid UTF-16 text") from exc


def _reachability_hash(summary):
    callback = _target_callback(summary)
    pads = []
    for part in callback["parts"]:
        for pad in part["pads"]:
            pads.append((pad["padId"], pad["netId"]))
    _need(len(pads) == 135 and len({pad_id for pad_id, _net in pads}) == 135,
          "reachability oracle requires all 135 unique pads")
    pads.sort(key=lambda item: item[0])
    payload = bytearray(b"R1\n")
    for index, (left_id, left_net) in enumerate(pads):
        left_field = str(_utf16_units(left_id)) + ":" + left_id
        for right_id, right_net in pads[index + 1:]:
            right_field = str(_utf16_units(right_id)) + ":" + right_id
            bit = "1" if left_net == right_net else "0"
            payload.extend((left_field + right_field + bit + "\n").encode("utf-8"))
    return hashlib.sha256(payload).hexdigest()


def _validate_old_boundary(old_reports, prior_contract):
    try:
        result = PRIOR._assess_routes(
            old_reports, prior_contract["selected"], prior_contract["whitespace"],
            prior_contract["protocol"])
    except ReaderError:
        raise
    except ValueError as exc:
        raise ReaderError(str(exc)) from exc
    _need(result["status"] == "PASS" and result["rows"] == 2 and
          result["selectedCaseRows"] == 2,
          "current old census rows failed the pinned prior reader")
    current = {}
    cases = {}
    for prefix, row, _line in old_reports:
        if prefix == CENSUS_PREFIX:
            current[row["seed"]] = row
        elif prefix == CASE_PREFIX:
            cases[row["seed"]] = row
    _need(tuple(current) == ROOTS and tuple(cases) == ROOTS,
          "current old census rows lack the exact root order")
    for seed in ROOTS:
        _need(_typed_equal(current[seed], prior_contract["publishedCensusSummaries"][seed]),
              "current census summary differs from sealed published census facts")
    return current, cases, result


def _validate_source(source, seed, prior_contract, protocol):
    _need(type(source) is dict and tuple(source) == SOURCE_FIELDS,
          "feasibility source schema/order changed")
    for key in ("liveGraphBefore", "liveGraphAfter", "geometryBefore",
                "geometryAfter", "traceProvenanceHash", "reachabilityHash"):
        _hex_digest(source[key], "source " + key)
    case = prior_contract["publishedCensusCases"][seed]
    pool = prior_contract["publishedPoolSource"] if seed == "2" else None
    _need(source["liveGraphBefore"] == source["liveGraphAfter"] ==
          case["liveBoardGraphSha256"] == (pool["liveGraphBefore"] if pool else
                                             case["liveBoardGraphSha256"]) and
          source["geometryBefore"] == source["geometryAfter"] ==
          protocol["expectedSource"]["geometryHash"] == (pool["geometryHash"] if pool else
                                                            protocol["expectedSource"]["geometryHash"]) and
          source["copperLength"] == protocol["expectedSource"]["copperLength"] == (pool["copperLength"] if pool else
                                                                                       source["copperLength"]) and
          source["area"] == protocol["expectedSource"]["area"] == (pool["area"] if pool else
                                                                       source["area"]) and
          source["componentCount"] == protocol["expectedSource"]["componentCount"] and
          source["padCount"] == protocol["expectedSource"]["padCount"] and
          source["traceCount"] == protocol["expectedSource"]["traceCount"] == (pool["traceCount"] if pool else
                                                                                   source["traceCount"]) and
          source["sourceRouteSourceCount"] == protocol["expectedSource"]["sourceRouteSourceCount"] and
          source["reachabilityHash"] == protocol["expectedSource"]["reachabilityHash"] and
          source["padPairCount"] == protocol["expectedSource"]["padPairCount"],
          "feasibility source facts changed")
    for key in ("copperLength", "area", "componentCount", "padCount", "traceCount",
                "sourceRouteSourceCount", "padPairCount"):
        _bounded_int(source[key], "source " + key, 0, MAX_COUNT * 100)
    return source


def _validate_move(move, expected):
    _need(type(move) is dict and tuple(move) == MOVE_FIELDS,
          "feasibility move schema/order changed")
    _need(_typed_equal(move, expected), "feasibility move differs from canonical 31-row order")


def _part_for_move(summary, move):
    target = _target_callback(summary)
    matches = [part for part in target["parts"]
               if part["componentId"] == move["componentId"]]
    _need(len(matches) == 1, "feasibility move component is not unique in source census")
    return matches[0]


def _validate_candidate(candidate, source, move, source_summary, protocol):
    _need(type(candidate) is dict and tuple(candidate) == CANDIDATE_FIELDS,
          "feasibility candidate schema/order changed")
    _hex_digest(candidate["geometryHash"], "candidate geometryHash")
    _need(candidate["geometryHash"] != source["geometryBefore"] and
          candidate["componentCount"] == protocol["expectedSource"]["componentCount"] and
          candidate["padCount"] == protocol["expectedSource"]["padCount"] and
          candidate["traceCount"] == protocol["expectedSource"]["traceCount"] and
          candidate["movedPadCount"] == 2 and
          candidate["copperLength"] == source["copperLength"] + move["actualCopperDelta"] and
          candidate["area"] == source["area"] and
          candidate["generationMetadata"] == GENERATION_METADATA,
          "feasibility candidate facts changed")
    part = _part_for_move(source_summary, move)
    traces = {incident["traceIndex"] for pad in part["pads"]
              for incident in pad["incidents"]}
    _need(candidate["affectedTraceCount"] == len(traces),
          "candidate affectedTraceCount is not derived from source incidents")
    for key in ("componentCount", "padCount", "traceCount", "affectedTraceCount",
                "movedPadCount", "copperLength", "area"):
        _bounded_int(candidate[key], "candidate " + key, 0, MAX_COUNT * 100)
    return candidate


def _millis(value, label):
    _need(type(value) is int and not isinstance(value, bool) and value >= 0,
          label + " is not a nonnegative integer millisecond value")
    return value


def _validate_timing(timing, stage, protocol):
    _need(type(timing) is dict and tuple(timing) == ROW_TIMING_FIELDS,
          "feasibility row timing schema/order changed")
    _millis(timing["constructionMillis"], "constructionMillis")
    _millis(timing["placementMillis"], "placementMillis")
    active = {
        "PLACEMENT": ("placementMillis",),
        "GEOMETRY": ("placementMillis", "geometryMillis"),
        "TWO_LAYER": ("placementMillis", "geometryMillis", "twoLayerMillis"),
        "COMPLETE": ("placementMillis", "geometryMillis", "twoLayerMillis", "proofMillis"),
    }[stage]
    for key in ("geometryMillis", "twoLayerMillis", "proofMillis"):
        if key in active:
            _millis(timing[key], key)
        else:
            _need(timing[key] is None, key + " must be null before its stage")
    _millis(timing["totalMillis"], "totalMillis")
    floor = sum(timing[key] for key in ("constructionMillis", "placementMillis",
                                        "geometryMillis", "twoLayerMillis", "proofMillis")
                if timing[key] is not None)
    _need(timing["totalMillis"] >= floor and
          timing["totalMillis"] <= protocol["maxCaseMillis"],
          "feasibility row timing total is outside its floor/bound")


def _validate_checks(checks, stage):
    _need(type(checks) is dict and tuple(checks) == CHECK_FIELDS,
          "feasibility checks schema/order changed")
    for key in ("traceEndpointOnlyVerified", "unrelatedGeometryVerified",
                "sourceImmutable"):
        _need(type(checks[key]) is bool and checks[key] is True,
              "feasibility source check is not strict true")
    expected = {
        "PLACEMENT": (False, False, False),
        "GEOMETRY": (True, False, False),
        "TWO_LAYER": (True, True, False),
        "COMPLETE": (True, True, True),
    }[stage]
    actual = tuple(checks[key] for key in
                   ("placementValidated", "geometryValidated", "twoLayerValidated"))
    _need(all(type(item) is bool for item in actual) and actual == expected,
          "feasibility stage checks are inconsistent")


def _validate_proof(proof, source, protocol):
    _need(type(proof) is dict and tuple(proof) == PROOF_FIELDS,
          "feasibility proof schema/order changed")
    for key in ("placementValidated", "geometryValidated", "twoLayerValidated",
                "traceEndpointOnlyVerified", "unrelatedGeometryVerified",
                "conductorReachabilityVerified", "traceProvenanceVerified"):
        _need(type(proof[key]) is bool and proof[key] is True,
              "feasibility proof flag is not strict true")
    _bounded_int(proof["padPairCount"], "proof padPairCount", 0, MAX_COUNT * 100)
    _bounded_int(proof["sourceRouteSourceCount"],
                 "proof sourceRouteSourceCount", 0, MAX_COUNT * 100)
    _bounded_int(proof["candidateRouteSourceCount"],
                 "proof candidateRouteSourceCount", 0, MAX_COUNT * 100)
    _need(proof["padPairCount"] == protocol["expectedSource"]["padPairCount"] and
          proof["sourceReachabilityHash"] == protocol["expectedSource"]["reachabilityHash"] and
          proof["candidateReachabilityHash"] == protocol["expectedSource"]["reachabilityHash"] and
          proof["sourceRouteSourceCount"] == 145 and
          proof["candidateRouteSourceCount"] == 145,
          "feasibility proof net/source counts changed")
    for key in ("sourceReachabilityHash", "candidateReachabilityHash",
                "sourceTraceProvenanceHash", "candidateTraceProvenanceHash"):
        _hex_digest(proof[key], "proof " + key)
    _need(proof["sourceTraceProvenanceHash"] ==
          proof["candidateTraceProvenanceHash"] == source["traceProvenanceHash"],
          "feasibility proof provenance is not the source provenance")


def _validate_rejection(stage, reason, message, protocol):
    _need(stage in REJECTION_STAGES, "feasibility rejection stage is unknown")
    _bounded_text(reason, "feasibility rejection reason")
    _bounded_text(message, "feasibility rejection message")
    if stage == "PLACEMENT":
        _need(reason in protocol["placementReasons"],
              "placement rejection reason is outside the closed catalog")
        _need(message == reason,
              "placement rejection message must equal its catalog reason")
    elif stage == "GEOMETRY":
        catalog = dict(protocol["geometryPrefixReasons"])
        catalog.update(protocol["qualityReasons"])
        if reason in catalog:
            _need(message.startswith(catalog[reason]) and
                  len(message) > len(catalog[reason]),
                  "geometry rejection reason/message is outside the closed catalog")
        else:
            _need(reason in protocol["twoLayerExactReasons"] and
                  message == protocol["twoLayerExactReasons"][reason],
                  "nested two-layer rejection reason/message is outside the closed catalog")
    else:
        _need(reason in protocol["twoLayerExactReasons"] and
              message == protocol["twoLayerExactReasons"][reason],
              "two-layer rejection reason/message is outside the closed catalog")


def _validate_row(row, index, source, source_summary, protocol):
    _need(type(row) is dict and tuple(row) == ROW_FIELDS,
          "feasibility row schema/order changed")
    expected_move = protocol["expectedMoves"][index]
    _validate_move(row["move"], expected_move)
    _need(type(row["candidate"]) is dict, "feasibility candidate is missing")
    _validate_candidate(row["candidate"], source, expected_move,
                        source_summary, protocol)
    disposition = row["disposition"]
    _need(type(disposition) is str and disposition in protocol["rowDispositions"],
          "feasibility disposition is outside the closed catalog")
    if disposition == DISPOSITION_REJECTED:
        stage = row["stage"]
        _need(type(stage) is str and stage in protocol["rowStages"] and
              stage != "COMPLETE", "rejected feasibility stage is not text/catalog")
        _validate_rejection(stage, row["reason"], row["message"], protocol)
        _validate_checks(row["checks"], stage)
        _need(row["proof"] is None, "rejected feasibility row must have null proof")
        _validate_timing(row["timing"], stage, protocol)
        return {"disposition": disposition, "stage": stage,
                "timing": row["timing"]}
    if disposition == DISPOSITION_VALIDATED:
        _need(row["stage"] == "COMPLETE" and row["reason"] is None and
              row["reason"] is protocol["validatedRowReason"] and
              row["message"] is None,
              "validated feasibility row classification changed")
        _validate_checks(row["checks"], "COMPLETE")
        _validate_proof(row["proof"], source, protocol)
        _validate_timing(row["timing"], "COMPLETE", protocol)
        return {"disposition": disposition, "stage": "COMPLETE",
                "timing": row["timing"]}
    raise ReaderError("feasibility disposition is outside the closed catalog")


def _validate_summary(row, seed, prior_contract, protocol):
    _need(type(row) is dict and tuple(row) == SUMMARY_FIELDS,
          "feasibility summary schema/order changed")
    _need(_canonical_seed(row["seed"], "feasibility seed") == seed and
          type(row["label"]) is str and row["label"] == "seed" + seed and
          type(row["version"]) is int and row["version"] == VERSION and
          row["phase"] == protocol["phase"] and type(row["phase"]) is str and
          type(row["newRouteRequests"]) is int and row["newRouteRequests"] == 0 and
          type(row["selectedMutation"]) is bool and row["selectedMutation"] is False,
          "feasibility summary identity/construction boundary changed")
    _bounded_int(row["constructionCount"], "summary constructionCount", 0, 31)
    _bounded_int(row["elapsedMillis"], "summary elapsedMillis", 0,
                 protocol["maxCaseMillis"])
    if seed == "5":
        _need(row["status"] == protocol["summaryStatuses"][seed] and
              row["source"] is None and
              row["rows"] == [] and
              _typed_equal(row["aggregate"], {
                  "structuralDirections": 0, "constructionCount": 0,
                  "physicallyValidated": 0, "physicallyRejected": 0,
                  "placementRejected": 0, "geometryRejected": 0,
                  "twoLayerRejected": 0,
              }) and row["constructionCount"] == 0 and row["elapsedMillis"] == 0,
              "seed5 no-target feasibility summary changed")
        return {"seed": seed, "status": row["status"], "counts": row["aggregate"]}
    _need(row["status"] == protocol["summaryStatuses"][seed] and
          row["constructionCount"] == 31 and
          type(row["rows"]) is list and len(row["rows"]) == 31,
          "seed2 feasibility census identity/count changed")
    source = _validate_source(row["source"], seed, prior_contract, protocol)
    current_source_summary = prior_contract["publishedCensusSummaries"][seed]
    observed = []
    for index, candidate in enumerate(row["rows"]):
        observed.append(_validate_row(candidate, index, source,
                                       current_source_summary, protocol))
    counts = {
        "structuralDirections": len(observed),
        "constructionCount": row["constructionCount"],
        "physicallyValidated": sum(item["disposition"] == DISPOSITION_VALIDATED
                                    for item in observed),
        "physicallyRejected": sum(item["disposition"] == DISPOSITION_REJECTED
                                   for item in observed),
        "placementRejected": sum(item["stage"] == "PLACEMENT" for item in observed),
        "geometryRejected": sum(item["stage"] == "GEOMETRY" for item in observed),
        "twoLayerRejected": sum(item["stage"] == "TWO_LAYER" for item in observed),
    }
    _need(_typed_equal(row["aggregate"], counts),
          "feasibility aggregate does not match all31 row classifications")
    _need(counts["physicallyValidated"] + counts["physicallyRejected"] == 31 and
          row["elapsedMillis"] >= sum(item["timing"]["totalMillis"]
                                      for item in observed) and
          row["elapsedMillis"] > 0,
          "feasibility summary elapsed time does not cover row totals")
    first = row["rows"][0]
    _need(first["disposition"] == DISPOSITION_REJECTED and
          first["stage"] == protocol["expectedFirstRejection"]["stage"] and
          first["reason"] == protocol["expectedFirstRejection"]["reason"] and
          first["message"] == protocol["expectedFirstRejection"]["message"],
          "first C5 feasibility rejection is not the sealed literal")
    return {"seed": seed, "status": row["status"], "counts": counts}


def _assess_routes(reports, contract, prior_contract):
    protocol = contract["protocol"]
    _need(type(reports) is list and len(reports) == 8,
          "routes phase requires exactly eight ordered PCB report rows")
    old_reports = []
    rows = []
    cursor = 0
    for seed in ROOTS:
        expected = (WHITESPACE_PREFIX, CENSUS_PREFIX, CASE_PREFIX,
                    protocol["summaryPrefix"])
        _need(tuple(reports[cursor + offset][0] for offset in range(4)) == expected,
              "routes reports must be whitespace, census, case, feasibility per root")
        old_reports.extend(reports[cursor:cursor + 3])
        rows.append((seed, reports[cursor + 1][1], reports[cursor + 2][1],
                     reports[cursor + 3][1]))
        cursor += 4
    _need(cursor == len(reports), "extra or foreign feasibility reports remain")
    _current_summaries, _current_cases, old_result = _validate_old_boundary(
        old_reports, prior_contract)
    feasibility = []
    for seed, _summary, _case, row in rows:
        feasibility.append(_validate_summary(row, seed, prior_contract, protocol))
    return {
        "status": "PASS", "scope": protocol["scope"], "roots": 2, "rows": 2,
        "census": {
            "status": old_result["status"], "roots": old_result["rows"],
            "rows": old_result["rows"], "selectedCaseRows": old_result["selectedCaseRows"],
            "whitespaceRows": 2,
        },
        "feasibility": {
            "status": "PASS", "roots": 2, "rows": 2,
            "statuses": [{"seed": item["seed"], "status": item["status"]}
                         for item in feasibility],
            "aggregate": feasibility[1]["counts"],
        },
        "observationDisposition": "PASS; independent physical census only",
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


def _phase_summary(text, receipt, timing, suites, phase, protocol,
                   prior_contract, selected_rows=None, whitespace_rows=None):
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
    _need(type(text) is str and type(receipt) is str,
          "native phase log/receipt is missing")
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
              "unit contract leaked physical census reports")
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
        result = _assess_routes(text_reports, {
            "protocol": protocol, "selected": selected_rows,
            "whitespace": whitespace_rows,
        }, prior_contract)
    return {
        "status": "PASS", "phase": phase, "suiteCount": len(expected_suites),
        "suiteClasses": [row[0] for row in expected_suites],
        "reportRows": len(text_reports), "orderedMarkers": [row[1] for row in expected_suites],
        "feasibility": result,
    }


def check_phase(text, receipt, timing, suites, phase):
    """Validate an in-memory native phase against the sealed census contract."""
    contract = _load_contract()
    prior_contract = _load_prior_contract(contract)
    actual_suites, _manifest_sha = _load_suite_manifest(phase, contract["protocol"])
    _need(_typed_equal(suites, actual_suites),
          "caller suite manifest differs from pinned bytes")
    return _phase_summary(
        text, receipt, timing, suites, phase, contract["protocol"], prior_contract,
        prior_contract["selected"], prior_contract["whitespace"])


def _read_phase_files(phase, contract, prior_contract):
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
    summary = _phase_summary(text, receipt, timing, suites, phase, protocol,
                             prior_contract, prior_contract["selected"],
                             prior_contract["whitespace"])
    return summary, {name: _sha(data) for name, data in raw.items()}, manifest_sha


def _unit_validation(contract, summary, phase_inputs, manifest_sha):
    protocol = contract["protocol"]
    return {
        "schema": 1, "status": "PASS", "phase": "unit",
        "scope": "Private physical-feasibility census unit fixtures only; native routes and fitness are not evaluated.",
        "baseCommit": protocol["baseCommit"],
        "protocolSha256": _sha(contract["protocolData"]),
        "priorAssessSha256": PRIOR_ASSESS_SHA,
        "publishedInputsSha256": {name: _sha(data)
                                   for name, data in contract["published"].items()},
        "suiteManifestSha256": manifest_sha, "phaseInputsSha256": phase_inputs,
        "phaseEvidence": summary,
        "fixtureCounts": dict(protocol["unitFixtureCounts"]),
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
    feasibility = summary["feasibility"]
    _need(type(feasibility) is dict and feasibility.get("status") == "PASS",
          "physical feasibility census is missing or failed")
    return {
        "schema": 1, "assessmentStatus": "PASS", "scope": protocol["scope"],
        "baseCommit": protocol["baseCommit"],
        "protocolSha256": _sha(contract["protocolData"]),
        "priorAssessSha256": PRIOR_ASSESS_SHA,
        "publishedInputsSha256": {name: _sha(data)
                                   for name, data in contract["published"].items()},
        "unitPrerequisiteSha256": unit_prerequisite_sha,
        "routesSuiteManifestSha256": manifest_sha,
        "routePhaseInputsSha256": phase_inputs,
        "nativePhase": {"status": "PASS", "phase": "routes", "evidence": summary},
        "observationDisposition": "PASS; independent physical census only",
        "fitnessDisposition": "NOT_EVALUATED",
        "policyDisposition": "NO_POLICY_OR_ACCEPTANCE_CHANGE",
        "productionChange": "NONE", "limits": list(protocol["limits"]),
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
        prior_contract = _load_prior_contract(contract)
        summary, phase_inputs, manifest_sha = _read_phase_files(
            args.phase, contract, prior_contract)
        if args.phase == "unit":
            output = _unit_validation(contract, summary, phase_inputs, manifest_sha)
            _write_artifact("unit-validation.json", output)
            print("PASS unit phase; routes and fitness are not evaluated")
            return 0
        unit_data = _task_file("unit-validation.json", 1024 * 1024)
        unit_summary, unit_inputs, unit_manifest_sha = _read_phase_files(
            "unit", contract, prior_contract)
        expected_unit = _unit_validation(
            contract, unit_summary, unit_inputs, unit_manifest_sha)
        prerequisite_sha = _validate_unit_prerequisite(unit_data, expected_unit)
        output = _routes_assessment(
            contract, summary, phase_inputs, manifest_sha, prerequisite_sha)
        _write_artifact("assessment.json", output)
        print("PASS routes phase; observation PASS; fitness NOT_EVALUATED")
        return 0
    except ReaderError as exc:
        print("FAIL offline reader: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
