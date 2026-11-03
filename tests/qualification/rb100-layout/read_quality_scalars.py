#!/usr/bin/env python3
"""Strict source-bound reader for the RB100 quality-scalar native receipt.

This diagnostic reports observed Publisher scalars. It does not certify routing,
layout feasibility, electrical behavior, or product quality.
"""
from __future__ import annotations

import builtins
import hashlib
import json
import math
import os
import re
import sys
import types
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple


INPUT_SCHEMA = "p07-quality-scalar-input-v1"
RUN_SCHEMA = "q100-quality-scalar-native-run-v1"
RESULT_SCHEMA = "q100-quality-scalar-reader-result-v1"
SUITE = "Rb100QualityScalarContractTest"
ENTRY_SCRIPT = "scripts/verify-current-contracts.ps1"
SOURCE_REVISION = 8
SOURCE_INPUT_COUNT = 1068
CONTRACT_JAVA_COUNT = 212
LAYOUT_VERSION = 26
DEPENDENCY_EPOCH = "tsj-generation-dependencies-v28"
PLAN_VERSION = 1
MODEL_VERSION = 4
PHYSICAL_INTENT_VERSION = 9
MAX_SCALAR = 2_147_483_647
MAX_JAVA_LONG = 9_223_372_036_854_775_807
ROOT_SEED = "9007199254740993"
PLACEMENT_SEED = "-6587958367974952701"
ROUTING_SEED = "8445301674744508279"
VARIANT = "SEPARATE_DIRECT_SWITCHING"
NULL_OPERATION_ID = "QUALITY_NULL"
PROBE_OPERATION_ID = "QUALITY_PROBE"
ROUTE_MILLIS_STATUS = "INCOMPLETE_MANUAL_SINGLE_STEP_CADENCE_NOT_REPRESENTATIVE"
FOCUSED_PASS = (
    "PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN."
)
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."

PINNED_LEDGER_SHA256 = "339230dd7019fb509cc98011baa7c880462a448bbf16cd25da8203423eff34b5"
PINNED_CAPTURE_SHA256 = "26cbdc32361f07f759cc32dd3fe432fd640e329ce7e51dd583094dc10d0af962"
PINNED_GRAPH_SHA256 = "c146cef4336d96eb6b6597569115d746d6a1fffe3b0c2ee2759d0e259d8b2a00"
FROZEN_WIRE_SHA256 = "c603e6ac45ca3fc1983cd1d87026f78c3b6896589007cd55e2a30c59eb7d9685"

def _keyset(names: str):
    return frozenset(names.split())


NATIVE_RUN_KEYS = _keyset(
    "schema source suite cohortId command layoutVersion dependencyEpoch sourceSnapshotRevision "
    "inputCount inputBytes inputPinsSha256 inputAudit exitCode nativeExitCode terminationProven "
    "cleanupStatus cleanupElapsedMs viaCost nativeLimitMs outerLimitMs processId processStartTicks "
    "elapsedSeconds requestedExecutable stdoutBytes stdoutSha256 stderrBytes stderrSha256 receiptBytes receiptSha256")
QUALITY_REQUIRED_INPUTS = frozenset({
    "tests/contracts/Rb100QualityScalarContractTest.java", "tests/contracts/Rb100PhysicalDiagnostics.java",
    "scripts/verify-current-contracts.ps1", "tests/contracts/current-contract-runner-contract.ps1",
})
CONTROL_KEYS = _keyset("protocolVersion sourceRevision runId status fixture checks assertions layoutQualification")
CONTROL_ROW_KEYS = _keyset("name status planarLength directDistance bends pathNodeCount transitionCount publisherStage")
SUBSET_KEYS = _keyset("protocol runId variant status independentTwoLayerAccess layoutQualification")
OPERATION_KEYS = _keyset("protocolVersion sourceRevision runId operationId variant mode result")
OPERATION_RESULT_KEYS = _keyset(
    "protocolVersion runId status phase operationMs cleanupMs cleanupStatus workUnits maxUnitMs "
    "failurePhase failureType failure cleanupFailureType cleanupFailure scope layoutQualification reportFacts")
FACT_KEYS = _keyset(
    "protocolVersion runId fixture variant layoutVersion dependencyEpoch planVersion modelVersion "
    "physicalIntentVersion physicalPolicyId physicalPolicyVersion placementDensityVersion routingLayer "
    "traceWidthProfile placementPackingVersion requiresTwoLayerRouting seed placementSeed routingSeed "
    "components pads nets boardIdentityStatus solverGraphIdentityStatus sessionTerminalStatus "
    "sessionException advanceCalls observerChecks limits primaryRoutingOrders primaryRoutingExpansions "
    "policyFailure policyStatistics packingProbe layoutOutput layoutQualification")
LIMIT_KEYS = _keyset(
    "placementCandidates routeCandidates p07Expansions p07BranchExpansions p07GridCells p07Orderings "
    "p07Vias outerPlacements outerRouteAttempts outerRouteExpansions outerAccessCells operationMs "
    "workUnits unitMs cleanupMs")
STATISTIC_KEYS = _keyset(
    "policyIdentity outcome selectedRoutePolicy placementCandidates placementRejections placementEvaluations "
    "routeAttempts oneFaceAttempts oneFaceSuccesses twoLayerAttempts twoLayerSuccesses "
    "selectedPlacementAttempt rankedPlacementCount rankedPlacementAttempts placementScoreCount placementScores "
    "routeOutcomesCount routeOutcomes orderingSearchReceiptsCount orderingSearchReceipts "
    "routeEligiblePlacementAttemptCount routeEligiblePlacementAttempts routeEligibilitySkipCount "
    "routeEligibilitySkips routingOrders routingExpansions selectedPlacementScore selectedRouteQualityMilli "
    "canonicalLength canonicalJavaHash canonical")
STATISTIC_INTEGER_RANGES = {
    **{name: (0, MAX_SCALAR) for name in "placementCandidates placementRejections routeAttempts oneFaceAttempts oneFaceSuccesses twoLayerAttempts twoLayerSuccesses rankedPlacementCount placementScoreCount routeOutcomesCount orderingSearchReceiptsCount routeEligiblePlacementAttemptCount routeEligibilitySkipCount routingOrders".split()},
    "placementEvaluations": (1, MAX_JAVA_LONG),
    "selectedPlacementAttempt": (-1, MAX_SCALAR),
    "routingExpansions": (0, 160_000_000),
    "selectedPlacementScore": (-1, MAX_JAVA_LONG),
    "selectedRouteQualityMilli": (-1, MAX_JAVA_LONG),
    "canonicalLength": (1, 4 * 1024 * 1024),
}
ORDERING_KEYS = _keyset(
    "attempt startedOrderings completedOrderings yieldedOrderings finalizedOrderings "
    "selectedOrdering expansions guidanceStarted comparisonComplete termination")
ORDERING_INTEGER_RANGES = {
    **{name: (0, MAX_SCALAR) for name in "attempt startedOrderings completedOrderings yieldedOrderings finalizedOrderings".split()},
    "selectedOrdering": (-1, MAX_SCALAR), "expansions": (0, MAX_SCALAR),
}

LAYOUT_OUTPUT_KEYS = _keyset(
    "layoutReturned zeroOutput fingerprint outline rawSegments topTraceCount bottomTraceCount "
    "totalTraceCount holeCount viaCount platedHoleCount nonPlatedHoleCount")
EVENT_KEYS = _keyset(
    "protocolVersion sourceRevision runId operationId variant mode rootSeed placementSeed routingSeed "
    "layoutVersion dependencyEpoch planVersion modelVersion physicalIntentVersion routeRequestIndex "
    "selectedPlacementIndex selectedPlacementAttempt eligiblePlacementAttempts placementFingerprintLength "
    "placementFingerprintSha256 pass netId startPadId rootPadId endPadId startX startY endX endY "
    "endLayer primaryLayer firstLayer gridWidth gridHeight gridOriginX gridOriginY gridPitch "
    "pathNodeCount transitionCount planarLength directDistance bends rejectDirectZero "
    "rejectLengthOverThreeDirect rejectBendsOver16 qualityRejected qualityRejectionOwner "
    "qualityRejectionMessage generatorLastFailureSource generatorLastFailureType "
    "generatorLastFailureMessage routeMillisStatus manualDriverMillis manualSliceSteps "
    "acceptanceFlags feasibility layoutQualification graphCallbacks")
PAIR_KEYS = _keyset(
    "protocolVersion sourceRevision runId variant nullOperationId probeOperationId rootSeed placementSeed "
    "routingSeed layoutVersion dependencyEpoch planVersion modelVersion physicalIntentVersion status "
    "routeEligiblePlacementAttemptsEqual firstP07RequestAttemptEqual routeOutcomesEqual "
    "primaryRoutingOrdersEqual primaryRoutingExpansionsEqual policyCanonicalEqual boardIdentityEqual "
    "solverIdentityEqual terminalStatusEqual layoutOutputEqual layoutAbsent routeMillisStatus layoutQualification")
PAIR_TRUE_FIELDS = (
    "routeEligiblePlacementAttemptsEqual", "firstP07RequestAttemptEqual", "routeOutcomesEqual",
    "primaryRoutingOrdersEqual", "primaryRoutingExpansionsEqual", "policyCanonicalEqual",
    "boardIdentityEqual", "solverIdentityEqual", "terminalStatusEqual", "layoutOutputEqual", "layoutAbsent",
)
SUMMARY_KEYS = _keyset(
    "protocolVersion sourceRevision status operations nullOperations probeOperations pairs confirmedScalarEvents "
    "variant runId rootSeed placementSeed routingSeed layoutVersion dependencyEpoch planVersion modelVersion "
    "physicalIntentVersion assertions suiteMs suiteLimitMs manualDriverMillis manualSliceSteps routeMillisStatus "
    "feasibility layoutQualification scope")
EVENT_PREFIXES = ("RB100_", "P07_", "PASS: RB100")
RECEIPT_MARKERS = (
    "RB100_QUALITY_SCALAR_CONTROLS",
    "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS",
    "RB100_QUALITY_SCALAR_OPERATION",
    "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS",
    "RB100_QUALITY_SCALAR_OPERATION",
    "RB100_QUALITY_SCALAR_EVENT",
    "RB100_QUALITY_SCALAR_PAIR",
    "PASS: RB100 quality scalar contracts",
)
UUID_RE = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\Z",
    re.ASCII,
)
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
PROCESS_TICKS_RE = re.compile(r"[1-9][0-9]{0,19}\Z", re.ASCII)
DECIMAL_RE = re.compile(r"-?(?:0|[1-9][0-9]*)\Z", re.ASCII)

MAX_SPEC_BYTES = 1024 * 1024
MAX_RESULT_BYTES = 2 * 1024 * 1024
MAX_CAPTURE_BYTES = 32 * 1024 * 1024
MAX_NATIVE_LIMIT_MS = 480_000
MAX_OUTER_LIMIT_MS = 1_800_000
MAX_OPERATION_MS = 90_000
MAX_CLEANUP_MS = 120_000
MAX_WORK_UNITS = 640
MAX_UNIT_MS = 5_000
MAX_GRID_CELLS = 250_000
MAX_MANUAL_SLICES = MAX_WORK_UNITS * 32_768

CONTROL_EXPECTATIONS = (
    ("direct_zero_rejected", 0, 0, 0, 1, 0, 1), ("length_over_3x_rejected", 80, 20, 2, 9, 0, 1),
    ("bends_over_16_rejected", 180, 180, 17, 19, 0, 1), ("exact_3x_16_accepted", 270, 90, 16, 28, 0, 2),
    ("via_direction_reset", 30, 30, 1, 5, 1, 2),
)
EXPECTED_ELIGIBLE_ATTEMPTS = [4, 2, 3, 0, 1]
EXPECTED_ROUTE_OUTCOMES = [
    "P07_FULLER_TWO_LAYER@4=REJECTED:P07_FULLER_TWO_LAYER:A_COIL_LOW:NO_PATH",
    "P07_FULLER_TWO_LAYER@2=REJECTED:P07_FULLER_TWO_LAYER:TOTAL_SEARCH_LIMIT",
    "P07_FULLER_TWO_LAYER@3=REJECTED:P07_FULLER_TWO_LAYER:TOTAL_SEARCH_LIMIT",
]

class QualityScalarInputError(ValueError):
    """A static sanitized failure; input text and paths are never interpolated."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)

def _fail(code: str) -> None: raise QualityScalarInputError(code)

def _need(condition: bool, code: str) -> None: condition or _fail(code)

def _integer(value: Any, minimum: int, maximum: int) -> bool:
    return type(value) is int and minimum <= value <= maximum

def _exact_keys(value: Any, expected: Iterable[str], code: str) -> None:
    _need(type(value) is dict and set(value) == set(expected), code)

def _sha256(raw: bytes) -> str: return hashlib.sha256(raw).hexdigest()

def _is_reparse(path: Path) -> bool:
    try:
        if path.is_symlink():
            return True
        is_junction = getattr(path, "is_junction", None)
        if callable(is_junction) and is_junction():
            return True
        return bool(getattr(path.lstat(), "st_file_attributes", 0) & 0x400)
    except FileNotFoundError:
        return False
    except (OSError, RuntimeError, ValueError):
        return True

def _has_reparse_component(path: Path) -> bool:
    try:
        absolute = Path(os.path.abspath(os.fspath(path)))
        current = Path(absolute.anchor)
        for part in absolute.parts[1:]:
            current = current / part
            if _is_reparse(current):
                return True
        return False
    except (OSError, RuntimeError, TypeError, ValueError):
        return True

def _read_local(path: Path, limit: int, code: str, allow_empty: bool = False) -> bytes:
    try:
        _need(not _has_reparse_component(path) and not _is_reparse(path),
              code + "_REPARSE_PATH")
        info = path.stat()
        _need(path.is_file(), code + "_NOT_FILE")
        _need((0 <= info.st_size if allow_empty else 0 < info.st_size) and
              info.st_size <= limit, code + "_SIZE_INVALID")
        raw = path.read_bytes()
        _need(len(raw) == info.st_size and len(raw) <= limit, code + "_READ_SIZE_CHANGED")
        return raw
    except QualityScalarInputError:
        raise
    except (OSError, RuntimeError, ValueError):
        _fail(code + "_READ_FAILED")

def _exec_private_module(raw: bytes, path: Path, name: str,
                         graph_module: Optional[types.ModuleType] = None) -> types.ModuleType:
    module = types.ModuleType(name)
    module.__file__ = os.fspath(path)
    module.__package__ = ""
    namespace = dict(vars(builtins))
    if graph_module is not None:
        standard_import = builtins.__import__

        def private_import(import_name, globals=None, locals=None, fromlist=(), level=0):
            if level == 0 and import_name == "read_fixed_prefix_graph":
                return graph_module
            return standard_import(import_name, globals, locals, fromlist, level)

        namespace["__import__"] = private_import
    module.__dict__["__builtins__"] = namespace
    previous = sys.modules.get(name)
    had_previous = name in sys.modules
    sys.modules[name] = module
    try:
        source = raw.decode("utf-8", errors="strict")
        exec(compile(source, os.fspath(path), "exec", dont_inherit=True), module.__dict__)
    except (UnicodeDecodeError, SyntaxError, ValueError, TypeError):
        _fail("PINNED_HELPER_SOURCE_INVALID")
    finally:
        if had_previous:
            sys.modules[name] = previous
        else:
            sys.modules.pop(name, None)
    return module

def _load_pinned_helpers() -> Tuple[types.ModuleType, types.ModuleType, Dict[str, str]]:
    try:
        own_path = Path(os.path.abspath(__file__))
        _need(not _has_reparse_component(own_path), "READER_SOURCE_REPARSE_PATH")
        own_path = own_path.resolve(strict=True)
        _need(own_path.is_file() and not _is_reparse(own_path), "READER_SOURCE_INVALID")
        directory = own_path.parent

        graph_path = directory / "read_fixed_prefix_graph.py"
        graph_raw = _read_local(graph_path, 4 * 1024 * 1024, "GRAPH_HELPER")
        _need(_sha256(graph_raw) == PINNED_GRAPH_SHA256, "GRAPH_HELPER_HASH_MISMATCH")
        graph = _exec_private_module(graph_raw, graph_path, "_q100_quality_scalar_graph")

        capture_path = directory / "read_fixed_prefix_capture.py"
        capture_raw = _read_local(capture_path, 4 * 1024 * 1024, "CAPTURE_HELPER")
        _need(_sha256(capture_raw) == PINNED_CAPTURE_SHA256, "CAPTURE_HELPER_HASH_MISMATCH")
        capture = _exec_private_module(
            capture_raw, capture_path, "_q100_quality_scalar_capture", graph_module=graph)

        ledger_path = directory / "read_current_prefix_ledger.py"
        ledger_raw = _read_local(ledger_path, 2 * 1024 * 1024, "PREFIX_LEDGER_HELPER")
        _need(_sha256(ledger_raw) == PINNED_LEDGER_SHA256,
              "PREFIX_LEDGER_HELPER_HASH_MISMATCH")
        ledger = _exec_private_module(
            ledger_raw, ledger_path, "_q100_quality_scalar_prefix_ledger")
        # These are the only compatibility metadata overrides. Keep this pinned
        # helper isolated; its source file and all other module constants stay frozen.
        ledger.INPUT_SCHEMA = INPUT_SCHEMA
        ledger.SOURCE_REVISION = SOURCE_REVISION
        ledger.SOURCE_INPUT_COUNT = SOURCE_INPUT_COUNT
        ledger.CONTRACT_JAVA_COUNT = CONTRACT_JAVA_COUNT
        reader_raw = _read_local(own_path, 2 * 1024 * 1024, "READER_SOURCE")
        hashes = {
            "readerSha256": _sha256(reader_raw),
            "prefixLedgerSha256": _sha256(ledger_raw),
            "captureHelperSha256": _sha256(capture_raw),
            "graphOracleSha256": _sha256(graph_raw),
        }
        return ledger, capture, hashes
    except QualityScalarInputError:
        raise
    except Exception:
        _fail("PINNED_HELPER_LOAD_FAILED")

def _valid_text(value: Any, maximum: int = 512) -> bool:
    if type(value) is not str or not 0 < len(value) <= maximum:
        return False
    try:
        encoded = value.encode("utf-8", errors="strict")
    except UnicodeEncodeError:
        return False
    return bool(encoded) and all(ord(char) >= 0x20 and ord(char) != 0x7F for char in value)

def _run_id(value: Any) -> bool:
    return type(value) is str and UUID_RE.fullmatch(value) is not None

def _decimal_long(value: Any) -> bool:
    if type(value) is not str or DECIMAL_RE.fullmatch(value) is None:
        return False
    try:
        numeric = int(value)
    except ValueError:
        return False
    return -(1 << 63) <= numeric < (1 << 63)

def _parse_tagged(line: str, marker: str, ledger: types.ModuleType,
                  code: str) -> Any:
    prefix = marker + " "
    _need(line.startswith(prefix), code + "_PREFIX_INVALID")
    body = line[len(prefix):].encode("utf-8", errors="strict")
    _need(0 < len(body) <= ledger.MAX_LINE_BYTES, code + "_SIZE_INVALID")
    try:
        return ledger._strict_json(body, code)
    except Exception as error:
        code_value = getattr(error, "code", None)
        if type(code_value) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code_value, re.ASCII):
            _fail(code_value)
        _fail(code + "_JSON_INVALID")

def _validate_identity(record: Any, run_id: str) -> None:
    _need(type(record) is dict and record.get("runId") == run_id and _run_id(run_id),
          "RECORD_RUN_ID_INVALID")
    _need(type(record.get("protocolVersion")) is int and
          record["protocolVersion"] == 1 and
          type(record.get("sourceRevision")) is int and
          record["sourceRevision"] == SOURCE_REVISION,
          "RECORD_PROTOCOL_INVALID")

def _is_quality_rejected(direct: int, length: int, bends: int) -> bool:
    return direct == 0 or length > 3 * direct or bends > 16

def _validate_controls(value: Any) -> str:
    _exact_keys(value, CONTROL_KEYS, "CONTROLS_KEYS_INVALID")
    run_id = value["runId"]
    _need(type(value["protocolVersion"]) is int and value["protocolVersion"] == 1 and
          type(value["sourceRevision"]) is int and
          value["sourceRevision"] == SOURCE_REVISION and _run_id(run_id) and
          value["status"] == "PASS" and
          value["fixture"] == "SYNTHETIC_PUBLISHER_METRICS_ONLY" and
          value["layoutQualification"] is False and
          _integer(value["assertions"], 1, MAX_SCALAR),
          "CONTROLS_IDENTITY_INVALID")
    _need(type(value["checks"]) is list and len(value["checks"]) == len(CONTROL_EXPECTATIONS),
          "CONTROLS_ROWS_INVALID")
    for row, expected in zip(value["checks"], CONTROL_EXPECTATIONS):
        _exact_keys(row, CONTROL_ROW_KEYS, "CONTROL_ROW_KEYS_INVALID")
        name, length, direct, bends, nodes, transitions, stage = expected
        _need(row["name"] == name and row["status"] == "PASS" and
              _integer(row["planarLength"], 0, 10_000_000) and
              _integer(row["directDistance"], 0, 10_000_000) and
              _integer(row["bends"], 0, 10_000_000) and
              _integer(row["pathNodeCount"], 1, MAX_GRID_CELLS * 2) and
              _integer(row["transitionCount"], 0, 4) and
              _integer(row["publisherStage"], 1, 2),
              "CONTROL_ROW_INVALID")
        _need((row["planarLength"], row["directDistance"], row["bends"],
               row["pathNodeCount"], row["transitionCount"], row["publisherStage"]) ==
              (length, direct, bends, nodes, transitions, stage),
              "CONTROL_LITERAL_MISMATCH")
        _need(_is_quality_rejected(direct, length, bends) == (stage == 1),
              "CONTROL_PREDICATE_STAGE_MISMATCH")
    return run_id

def _validate_subset(value: Any, run_id: str) -> None:
    _exact_keys(value, SUBSET_KEYS, "SUBSET_KEYS_INVALID")
    _need(value["protocol"] == "rb100-physical-subsets-v1" and
          value["runId"] == run_id and value["variant"] == VARIANT and
          value["status"] == "NOT_RUN_NO_LAYOUT" and
          value["independentTwoLayerAccess"] is False and
          value["layoutQualification"] is False,
          "SUBSET_NO_LAYOUT_INVALID")

def _validate_layout_output(value: Any) -> None:
    _exact_keys(value, LAYOUT_OUTPUT_KEYS, "LAYOUT_OUTPUT_KEYS_INVALID")
    _need(value["layoutReturned"] is False and value["zeroOutput"] is True and
          value["fingerprint"] is None and value["outline"] is None,
          "LAYOUT_OUTPUT_PRESENT")
    names = (
        "rawSegments", "topTraceCount", "bottomTraceCount", "totalTraceCount",
        "holeCount", "viaCount", "platedHoleCount", "nonPlatedHoleCount",
    )
    for name in names:
        _need(_integer(value[name], 0, 160_000_000), "LAYOUT_OUTPUT_COUNT_INVALID")
    _need(value["totalTraceCount"] == value["topTraceCount"] + value["bottomTraceCount"] and
          value["holeCount"] == value["viaCount"] + value["platedHoleCount"] +
          value["nonPlatedHoleCount"] and all(value[name] == 0 for name in names),
          "LAYOUT_OUTPUT_CENSUS_INVALID")

def _java_string_length(value: str) -> int:
    try:
        return len(value.encode("utf-16-le", errors="strict")) // 2
    except UnicodeEncodeError:
        _fail("CANONICAL_STRING_INVALID")

def _java_hash(value: str) -> str:
    try:
        raw = value.encode("utf-16-be", errors="strict")
    except UnicodeEncodeError:
        _fail("CANONICAL_STRING_INVALID")
    result = 0
    for offset in range(0, len(raw), 2):
        unit = (raw[offset] << 8) | raw[offset + 1]
        result = ((31 * result) + unit) & 0xFFFFFFFF
    return format(result, "x")

def _java_vector(values: Sequence[Any]) -> str:
    def render(value: Any) -> str:
        if type(value) is bool:
            return "true" if value else "false"
        if value is None:
            return "null"
        return str(value)
    return "[" + ", ".join(render(value) for value in values) + "]"

def _validate_statistics(value: Any, facts: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, STATISTIC_KEYS, "POLICY_STATISTICS_KEYS_INVALID")
    limits = facts["limits"]
    ranked = value["rankedPlacementAttempts"]
    scores = value["placementScores"]
    eligible = value["routeEligiblePlacementAttempts"]
    skips = value["routeEligibilitySkips"]
    candidates = value["placementCandidates"]
    _need(all(_integer(value[name], *bounds) for name, bounds in STATISTIC_INTEGER_RANGES.items()) and
          value["policyIdentity"] == "MEDIUM_BOARD@1" and
          value["outcome"] == "REJECTED" and
          value["selectedRoutePolicy"] is None and value["selectedPlacementAttempt"] == -1 and
          value["selectedPlacementScore"] == value["selectedRouteQualityMilli"] == -1 and
          candidates == limits["placementCandidates"] == 6 and
          type(ranked) is list and len(ranked) == value["rankedPlacementCount"] == candidates and
          all(_integer(item, 0, candidates - 1) for item in ranked) and
          len(set(ranked)) == candidates and set(ranked) == set(range(candidates)) and
          value["placementRejections"] + len(ranked) == candidates and
          type(scores) is list and len(scores) == value["placementScoreCount"] == len(ranked) and
          all(_valid_text(item, 4096) for item in scores) and
          type(value["routeOutcomes"]) is list and
          value["routeOutcomes"] == EXPECTED_ROUTE_OUTCOMES and
          value["routeOutcomesCount"] == len(value["routeOutcomes"]) ==
          value["routeAttempts"] == value["twoLayerAttempts"] == 3 and
          value["oneFaceAttempts"] == value["oneFaceSuccesses"] ==
          value["twoLayerSuccesses"] == 0 and
          type(eligible) is list and len(eligible) == value["routeEligiblePlacementAttemptCount"] == 5 and
          all(_integer(item, 0, candidates - 1) for item in eligible) and
          len(set(eligible)) == len(eligible) and eligible == EXPECTED_ELIGIBLE_ATTEMPTS and
          set(eligible) <= set(ranked) and type(skips) is list and
          len(skips) == value["routeEligibilitySkipCount"] and
          len(eligible) + len(skips) <= len(ranked) and
          all(_valid_text(item, 4096) for item in skips) and
          value["routingOrders"] == facts["primaryRoutingOrders"] == 9 and
          value["routingOrders"] <= limits["routeCandidates"] * limits["p07Orderings"] and
          value["routingExpansions"] == facts["primaryRoutingExpansions"] == 2_918_222 and
          value["routingExpansions"] <= limits["outerRouteExpansions"],
          "POLICY_ROUTE_CENSUS_INVALID")
    orderings = value["orderingSearchReceipts"]
    _need(type(orderings) is list and len(orderings) ==
          value["orderingSearchReceiptsCount"] == value["twoLayerAttempts"] == 3 and all(
              type(row) is dict and set(row) == ORDERING_KEYS and
              all(_integer(row[name], *bounds) for name, bounds in ORDERING_INTEGER_RANGES.items()) and
              row["attempt"] < candidates and
              1 <= row["startedOrderings"] <= limits["p07Orderings"] and
              1 <= row["completedOrderings"] <= row["startedOrderings"] and
              row["yieldedOrderings"] <= row["completedOrderings"] and
              row["finalizedOrderings"] <= row["yieldedOrderings"] and
              -1 <= row["selectedOrdering"] < row["startedOrderings"] and
              row["expansions"] <= limits["p07Expansions"] and
              type(row["guidanceStarted"]) is bool and
              type(row["comparisonComplete"]) is bool and _valid_text(row["termination"], 256)
              for row in orderings) and
          [row["attempt"] for row in orderings] == [4, 2, 3] and
          sum(row["expansions"] for row in orderings) == value["routingExpansions"],
          "POLICY_ORDERING_RECEIPTS_INVALID")
    canonical = value["canonical"]
    _need(type(canonical) is str and 0 < len(canonical.encode("utf-8")) <= 4 * 1024 * 1024 and
          value["canonicalLength"] == _java_string_length(canonical) and
          type(value["canonicalJavaHash"]) is str and
          re.fullmatch(r"[0-9a-f]{1,8}", value["canonicalJavaHash"], re.ASCII) is not None and
          value["canonicalJavaHash"] == _java_hash(canonical),
          "POLICY_CANONICAL_BINDING_INVALID")
    _need(";routingWork=9/2918222;" in canonical and
          ";routeEligiblePlacementAttempts=" + _java_vector(EXPECTED_ELIGIBLE_ATTEMPTS) +
          ";routeEligibilitySkips=" in canonical and
          ";routeOutcomes=" + _java_vector(EXPECTED_ROUTE_OUTCOMES) in canonical and
          ";orderingSearchReceipts=[" in canonical,
          "POLICY_CANONICAL_CENSUS_MISMATCH")
    return dict(value)

def _validate_limits(value: Any) -> None:
    _exact_keys(value, LIMIT_KEYS, "LIMITS_KEYS_INVALID")
    expected = {
        "placementCandidates": 6, "routeCandidates": 3, "p07Expansions": 1_000_000,
        "p07BranchExpansions": 100_000, "p07GridCells": 250_000, "p07Orderings": 3,
        "p07Vias": 48, "outerPlacements": 80, "outerRouteAttempts": 800,
        "outerRouteExpansions": 160_000_000, "outerAccessCells": 700_000,
        "operationMs": MAX_OPERATION_MS, "workUnits": MAX_WORK_UNITS,
        "unitMs": MAX_UNIT_MS, "cleanupMs": MAX_CLEANUP_MS,
    }
    _need(all(_integer(value[name], limit, limit) for name, limit in expected.items()),
          "LIMITS_FROZEN_BUDGET_INVALID")

def _validate_operation_result(value: Any, run_id: str,
                               operation_id: str, mode: str) -> Dict[str, Any]:
    _exact_keys(value, OPERATION_RESULT_KEYS, "OPERATION_RESULT_KEYS_INVALID")
    _need(type(value["protocolVersion"]) is int and value["protocolVersion"] == 2 and
          value["runId"] == run_id and value["status"] == "PASS" and
          _valid_text(value["phase"]) and
          _integer(value["operationMs"], 0, MAX_OPERATION_MS) and
          _integer(value["cleanupMs"], 0, MAX_CLEANUP_MS) and
          value["cleanupStatus"] == "PASS" and
          _integer(value["workUnits"], 0, MAX_WORK_UNITS) and
          _integer(value["maxUnitMs"], 0, MAX_UNIT_MS) and
          value["failurePhase"] == "" and value["failureType"] == "none" and
          value["failure"] == "" and value["cleanupFailureType"] == "none" and
          value["cleanupFailure"] == "" and
          value["scope"] == "OBSERVATIONAL_PRIVATE_P07_POLICY_ONLY" and
          value["layoutQualification"] is False,
          "OPERATION_RESULT_STATUS_INVALID")
    facts = value["reportFacts"]
    _exact_keys(facts, FACT_KEYS, "OPERATION_FACT_KEYS_INVALID")
    _need(type(facts["protocolVersion"]) is int and facts["protocolVersion"] == 2 and
          facts["runId"] == run_id and facts["fixture"] == "AUTHORED_RB100" and
          facts["variant"] == VARIANT and
          type(facts["layoutVersion"]) is int and facts["layoutVersion"] == LAYOUT_VERSION and
          facts["dependencyEpoch"] == DEPENDENCY_EPOCH and
          type(facts["planVersion"]) is int and facts["planVersion"] == PLAN_VERSION and
          type(facts["modelVersion"]) is int and facts["modelVersion"] == MODEL_VERSION and
          type(facts["physicalIntentVersion"]) is int and
          facts["physicalIntentVersion"] == PHYSICAL_INTENT_VERSION and
          _decimal_long(facts["seed"]) and facts["seed"] == ROOT_SEED and
          facts["placementSeed"] == PLACEMENT_SEED and facts["routingSeed"] == ROUTING_SEED,
          "OPERATION_FACT_IDENTITY_INVALID")
    _need(_valid_text(facts["physicalPolicyId"]) and
          _integer(facts["physicalPolicyVersion"], 1, MAX_SCALAR) and
          _integer(facts["placementDensityVersion"], 1, MAX_SCALAR) and
          _valid_text(facts["routingLayer"]) and _valid_text(facts["traceWidthProfile"]) and
          _integer(facts["placementPackingVersion"], 1, MAX_SCALAR) and
          facts["requiresTwoLayerRouting"] is True and
          _integer(facts["components"], 100, 100) and
          _integer(facts["pads"], 246, 246) and _integer(facts["nets"], 60, 60) and
          facts["boardIdentityStatus"] == "PASS" and
          facts["solverGraphIdentityStatus"] == "PASS" and
          facts["sessionTerminalStatus"] == "EXHAUSTED" and
          _integer(facts["advanceCalls"], 0, value["workUnits"]) and
          _integer(facts["observerChecks"], 0, MAX_SCALAR) and
          facts["layoutQualification"] is False,
          "OPERATION_FACT_IDENTITY_INVALID")
    exception = facts["sessionException"]
    _exact_keys(exception, {"type", "kind", "attemptIndex", "attemptCount",
                            "seed", "exhausted", "message"},
                "SESSION_EXCEPTION_KEYS_INVALID")
    _need(exception["type"] == "PcbRoutingRejectedException" and
          _valid_text(exception["kind"]) and
          _integer(exception["attemptIndex"], -1, 10_000) and
          _integer(exception["attemptCount"], 0, 10_000) and
          _decimal_long(exception["seed"]) and exception["exhausted"] is True and
          _valid_text(exception["message"], 4096),
          "SESSION_EXCEPTION_INVALID")
    if exception["attemptIndex"] >= 0:
        _need(exception["attemptIndex"] < exception["attemptCount"],
              "SESSION_EXCEPTION_ATTEMPT_INVALID")
    _validate_limits(facts["limits"])
    _need(_integer(facts["primaryRoutingOrders"], 1, 10_000) and
          _integer(facts["primaryRoutingExpansions"], 0, 160_000_000) and
          facts["packingProbe"] is None and
          (facts["policyFailure"] is None or _valid_text(facts["policyFailure"], 4096)),
          "OPERATION_ROUTE_STATE_INVALID")
    _validate_layout_output(facts["layoutOutput"])
    stats = _validate_statistics(facts["policyStatistics"], facts)
    return {"id": operation_id, "mode": mode, "result": value,
            "facts": facts, "statistics": stats}

def _validate_operation(value: Any, run_id: str, operation_id: str,
                        mode: str) -> Dict[str, Any]:
    _exact_keys(value, OPERATION_KEYS, "OPERATION_KEYS_INVALID")
    _need(type(value["protocolVersion"]) is int and value["protocolVersion"] == 1 and
          type(value["sourceRevision"]) is int and
          value["sourceRevision"] == SOURCE_REVISION and value["runId"] == run_id and
          value["operationId"] == operation_id and value["variant"] == VARIANT and
          value["mode"] == mode,
          "OPERATION_IDENTITY_INVALID")
    return _validate_operation_result(value["result"], run_id, operation_id, mode)

def _validate_event(value: Any, run_id: str, probe: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, EVENT_KEYS, "EVENT_KEYS_INVALID")
    _validate_identity(value, run_id)
    _need(value["operationId"] == PROBE_OPERATION_ID and
          value["variant"] == VARIANT and value["mode"] == "PROBE" and
          value["rootSeed"] == ROOT_SEED and value["placementSeed"] == PLACEMENT_SEED and
          value["routingSeed"] == ROUTING_SEED and
          type(value["layoutVersion"]) is int and value["layoutVersion"] == LAYOUT_VERSION and
          value["dependencyEpoch"] == DEPENDENCY_EPOCH and
          type(value["planVersion"]) is int and value["planVersion"] == PLAN_VERSION and
          type(value["modelVersion"]) is int and value["modelVersion"] == MODEL_VERSION and
          type(value["physicalIntentVersion"]) is int and
          value["physicalIntentVersion"] == PHYSICAL_INTENT_VERSION and
          _integer(value["routeRequestIndex"], 0, 0) and
          _integer(value["selectedPlacementIndex"], 0, 0) and
          _integer(value["selectedPlacementAttempt"], 4, 4) and
          type(value["eligiblePlacementAttempts"]) is list and
          len(value["eligiblePlacementAttempts"]) == 5 and
          all(_integer(item, 0, 10_000) for item in value["eligiblePlacementAttempts"]) and
          value["eligiblePlacementAttempts"] == EXPECTED_ELIGIBLE_ATTEMPTS and
          _integer(value["placementFingerprintLength"], 1, 4 * 1024 * 1024) and
          type(value["placementFingerprintSha256"]) is str and
          SHA256_RE.fullmatch(value["placementFingerprintSha256"]) is not None and
          _integer(value["pass"], 1, 1) and value["netId"] == "D_SENSE" and
          _valid_text(value["startPadId"]) and _valid_text(value["rootPadId"]) and
          value["endPadId"] is None,
          "EVENT_CONTEXT_INVALID")
    for name in ("startX", "startY", "endX", "endY", "gridOriginX", "gridOriginY"):
        _need(_integer(value[name], -MAX_SCALAR - 1, MAX_SCALAR), "EVENT_COORDINATE_INVALID")
    _need(_integer(value["gridWidth"], 1, MAX_GRID_CELLS) and
          _integer(value["gridHeight"], 1, MAX_GRID_CELLS) and
          value["gridWidth"] * value["gridHeight"] <= MAX_GRID_CELLS and
          _integer(value["gridPitch"], 10, 10),
          "EVENT_GRID_INVALID")
    direct = abs(value["startX"] - value["endX"]) + abs(value["startY"] - value["endY"])
    for x_name, y_name in (("startX", "startY"), ("endX", "endY")):
        dx, dy = value[x_name] - value["gridOriginX"], value[y_name] - value["gridOriginY"]
        _need(dx >= 0 and dy >= 0 and dx % 10 == 0 and dy % 10 == 0 and
              dx // 10 < value["gridWidth"] and dy // 10 < value["gridHeight"],
              "EVENT_GRID_COORDINATE_INVALID")
    _need(_integer(value["endLayer"], 0, 1) and
          _integer(value["primaryLayer"], 0, 1) and
          _integer(value["firstLayer"], 0, 1) and
          _integer(value["pathNodeCount"], 1, value["gridWidth"] * value["gridHeight"] * 2) and
          _integer(value["transitionCount"], 0, 4) and
          _integer(value["planarLength"], 0, 10_000_000) and
          _integer(value["directDistance"], 0, 10_000_000) and
          _integer(value["bends"], 0, 10_000_000),
          "EVENT_SCALAR_RANGE_INVALID")
    planar_edges = value["planarLength"] // 10
    _need(value["planarLength"] % 10 == 0 and
          value["pathNodeCount"] - 1 == planar_edges + value["transitionCount"] and
          value["directDistance"] == direct and
          value["directDistance"] <= value["planarLength"] and
          value["bends"] <= max(0, planar_edges - 1) and
          (value["endLayer"] - value["firstLayer"]) % 2 ==
          value["transitionCount"] % 2,
          "EVENT_SCALAR_CONSERVATION_INVALID")
    reject_zero = direct == 0
    reject_length = value["planarLength"] > 3 * direct
    reject_bends = value["bends"] > 16
    _need(type(value["rejectDirectZero"]) is bool and
          value["rejectDirectZero"] is reject_zero and
          type(value["rejectLengthOverThreeDirect"]) is bool and
          value["rejectLengthOverThreeDirect"] is reject_length and
          type(value["rejectBendsOver16"]) is bool and
          value["rejectBendsOver16"] is reject_bends and
          value["qualityRejected"] is True and
          (reject_zero or reject_length or reject_bends),
          "EVENT_PREDICATE_INVALID")
    _need(value["qualityRejectionOwner"] ==
          "PcbLayerRoutingPrototype.Session#reason" and
          value["qualityRejectionMessage"] == "D_SENSE:BRANCH_QUALITY_LIMIT" and
          value["generatorLastFailureSource"] ==
          "SeededPcbLayoutGenerator.Session#lastFailure" and
          _valid_text(value["generatorLastFailureType"]) and
          _valid_text(value["generatorLastFailureMessage"], 4096),
          "EVENT_REJECTION_PROVENANCE_INVALID")
    _need(value["routeMillisStatus"] == ROUTE_MILLIS_STATUS and
          _integer(value["manualDriverMillis"], 0, MAX_OPERATION_MS) and
          _integer(value["manualSliceSteps"], 1, MAX_MANUAL_SLICES) and
          value["manualDriverMillis"] <= probe["result"]["operationMs"] and
          type(value["acceptanceFlags"]) is dict and
          set(value["acceptanceFlags"]) == {"accepted", "routed", "playable"} and
          all(value["acceptanceFlags"][name] is False
              for name in ("accepted", "routed", "playable")) and
          value["feasibility"] == "UNKNOWN" and
          value["layoutQualification"] is False and
          _integer(value["graphCallbacks"], 0, 0),
          "EVENT_QUALIFICATION_INVALID")
    return dict(value)

def _validate_pair(value: Any, run_id: str, null: Mapping[str, Any],
                   probe: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, PAIR_KEYS, "PAIR_KEYS_INVALID")
    _validate_identity(value, run_id)
    _need(value["variant"] == VARIANT and
          value["nullOperationId"] == NULL_OPERATION_ID and
          value["probeOperationId"] == PROBE_OPERATION_ID and
          value["rootSeed"] == ROOT_SEED and
          value["placementSeed"] == PLACEMENT_SEED and
          value["routingSeed"] == ROUTING_SEED and
          type(value["layoutVersion"]) is int and value["layoutVersion"] == LAYOUT_VERSION and
          value["dependencyEpoch"] == DEPENDENCY_EPOCH and
          type(value["planVersion"]) is int and value["planVersion"] == PLAN_VERSION and
          type(value["modelVersion"]) is int and value["modelVersion"] == MODEL_VERSION and
          type(value["physicalIntentVersion"]) is int and
          value["physicalIntentVersion"] == PHYSICAL_INTENT_VERSION and
          value["status"] == "PASS" and
          all(type(value[name]) is bool and value[name] is True for name in PAIR_TRUE_FIELDS) and
          value["routeMillisStatus"] == ROUTE_MILLIS_STATUS and
          value["layoutQualification"] is False,
          "PAIR_FLAGS_OR_CONTEXT_INVALID")
    first, second = null["facts"], probe["facts"]
    a, b = null["statistics"], probe["statistics"]
    metadata = ("physicalPolicyId", "physicalPolicyVersion", "placementDensityVersion",
                "routingLayer", "traceWidthProfile", "placementPackingVersion",
                "requiresTwoLayerRouting")
    board_equal = (
        first["components"] == second["components"] and first["pads"] == second["pads"] and
        first["nets"] == second["nets"] and
        first["boardIdentityStatus"] == second["boardIdentityStatus"] == "PASS" and
        all(first[key] == second[key] for key in metadata)
    )
    solver_equal = first["solverGraphIdentityStatus"] == second["solverGraphIdentityStatus"] == "PASS"
    _need(a["routeEligiblePlacementAttempts"] == b["routeEligiblePlacementAttempts"] ==
          EXPECTED_ELIGIBLE_ATTEMPTS and
          a["routeOutcomes"] == b["routeOutcomes"] == EXPECTED_ROUTE_OUTCOMES and
          a["routingOrders"] == b["routingOrders"] == 9 and
          a["routingExpansions"] == b["routingExpansions"] == 2_918_222 and
          a["canonical"] == b["canonical"] and a == b and board_equal and solver_equal and
          first["sessionTerminalStatus"] == second["sessionTerminalStatus"] == "EXHAUSTED" and
          first["layoutOutput"] == second["layoutOutput"] and
          first["layoutOutput"]["layoutReturned"] is False,
          "PAIR_OPERATION_PARITY_INVALID")
    return dict(value)

def _validate_summary(value: Any, run_id: str, controls: Mapping[str, Any],
                      event: Mapping[str, Any], pair: Mapping[str, Any],
                      null: Mapping[str, Any], probe: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, SUMMARY_KEYS, "SUMMARY_KEYS_INVALID")
    _validate_identity(value, run_id)
    _need(value["status"] == "PASS" and
          _integer(value["operations"], 2, 2) and
          _integer(value["nullOperations"], 1, 1) and
          _integer(value["probeOperations"], 1, 1) and
          _integer(value["pairs"], 1, 1) and
          _integer(value["confirmedScalarEvents"], 1, 1) and
          value["variant"] == VARIANT and value["rootSeed"] == ROOT_SEED and
          value["placementSeed"] == PLACEMENT_SEED and value["routingSeed"] == ROUTING_SEED and
          type(value["layoutVersion"]) is int and value["layoutVersion"] == LAYOUT_VERSION and
          value["dependencyEpoch"] == DEPENDENCY_EPOCH and
          type(value["planVersion"]) is int and value["planVersion"] == PLAN_VERSION and
          type(value["modelVersion"]) is int and value["modelVersion"] == MODEL_VERSION and
          type(value["physicalIntentVersion"]) is int and
          value["physicalIntentVersion"] == PHYSICAL_INTENT_VERSION and
          _integer(value["assertions"], 1, MAX_SCALAR) and
          _integer(value["suiteMs"], 0, 480_000) and
          _integer(value["suiteLimitMs"], 480_000, 480_000) and
          _integer(value["manualDriverMillis"], 0, MAX_OPERATION_MS) and
          _integer(value["manualSliceSteps"], 1, MAX_MANUAL_SLICES) and
          value["routeMillisStatus"] == ROUTE_MILLIS_STATUS and
          value["feasibility"] == "UNKNOWN" and
          value["layoutQualification"] is False and
          value["scope"] == "OBSERVATIONAL_ACTUAL_GENERATOR_QUALITY_SCALAR_ONLY",
          "SUMMARY_IDENTITY_OR_LIMIT_INVALID")
    _need(value["manualDriverMillis"] == event["manualDriverMillis"] and
          value["manualSliceSteps"] == event["manualSliceSteps"] and
          value["manualDriverMillis"] <= probe["result"]["operationMs"] and
          controls["status"] == "PASS" and pair["status"] == "PASS" and
          null["mode"] == "NULL" and probe["mode"] == "PROBE",
          "SUMMARY_CROSS_RECORD_INVALID")
    return dict(value)

def _validate_receipt_lines(lines: Sequence[str], ledger: types.ModuleType) -> Dict[str, Any]:
    markers = RECEIPT_MARKERS
    _need(type(lines) in (list, tuple) and len(lines) == len(markers) and
          all(type(line) is str and line for line in lines),
          "RECEIPT_EVENT_COUNT_INVALID")
    records = [
        _parse_tagged(line, marker, ledger, "RECEIPT_RECORD")
        for line, marker in zip(lines, markers)
    ]
    controls = records[0]
    run_id = _validate_controls(controls)
    _validate_subset(records[1], run_id)
    null = _validate_operation(records[2], run_id, NULL_OPERATION_ID, "NULL")
    _validate_subset(records[3], run_id)
    probe = _validate_operation(records[4], run_id, PROBE_OPERATION_ID, "PROBE")
    event = _validate_event(records[5], run_id, probe)
    pair = _validate_pair(records[6], run_id, null, probe)
    summary = _validate_summary(records[7], run_id, controls, event, pair, null, probe)
    return {
        "runId": run_id, "controls": controls, "nullOperation": null,
        "probeOperation": probe, "event": event, "pair": pair, "summary": summary,
    }

def _validate_console_and_receipt(stdout_raw: bytes, receipt_raw: bytes,
                                  stderr_raw: bytes, ledger: types.ModuleType
                                  ) -> Tuple[List[str], List[str]]:
    try:
        console = ledger._decode_lines(stdout_raw, "RUN_STDOUT")
        receipt = ledger._decode_lines(receipt_raw, "RUN_RECEIPT")
        stderr = ledger._decode_lines(stderr_raw, "RUN_STDERR")
    except Exception as error:
        code = getattr(error, "code", None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code, re.ASCII):
            _fail(code)
        _fail("RUN_CAPTURE_UTF8_INVALID")
    _need(bool(receipt) and all(receipt), "RUN_RECEIPT_EMPTY_OR_BLANK")
    _need(all(line.startswith(EVENT_PREFIXES) and
              any(line.startswith(marker + " ") for marker in RECEIPT_MARKERS)
              for line in receipt), "RUN_RECEIPT_UNEXPECTED_LINE")
    console_events = [line for line in console if line.startswith(EVENT_PREFIXES)]
    stderr_events = [line for line in stderr if line.startswith(EVENT_PREFIXES)]
    _need(all(any(line.startswith(marker + " ") for marker in RECEIPT_MARKERS)
              for line in console_events), "RUN_CONSOLE_UNEXPECTED_EVENT")
    _need(console_events == receipt and not stderr_events, "RUN_CONSOLE_RECEIPT_MISMATCH")
    _need(console.count(FOCUSED_PASS) == 1 and console.count(CLEANUP_LINE) == 1 and
          console.index(FOCUSED_PASS) < console.index(CLEANUP_LINE),
          "RUN_CONSOLE_PASS_OR_CLEANUP_INVALID")
    _need(sum(line.startswith("PASS: RB100 quality scalar contracts ") for line in receipt) == 1,
          "RUN_SUITE_SUMMARY_MARKER_INVALID")
    return console, receipt

def _validate_native_run(value: Any, context: Mapping[str, Any],
                         stdout_raw: bytes, receipt_raw: bytes, stderr_raw: bytes) -> None:
    _exact_keys(value, NATIVE_RUN_KEYS, "NATIVE_RUN_KEYS_INVALID")
    command = {
        "entry": ENTRY_SCRIPT, "suite": SUITE, "suites": [SUITE],
        "args": [], "environment": {},
    }
    _need(value["schema"] == RUN_SCHEMA and value["source"] == "candidate" and
          value["suite"] == SUITE and value["cohortId"] is None and
          value["command"] == command,
          "NATIVE_RUN_IDENTITY_INVALID")
    _need(type(value["layoutVersion"]) is int and value["layoutVersion"] == LAYOUT_VERSION and
          value["dependencyEpoch"] == DEPENDENCY_EPOCH and
          type(value["sourceSnapshotRevision"]) is int and
          value["sourceSnapshotRevision"] == SOURCE_REVISION and
          type(value["inputCount"]) is int and value["inputCount"] == context["inputCount"] and
          type(value["inputBytes"]) is int and value["inputBytes"] == context["inputBytes"] and
          value["inputPinsSha256"] == context["pinsSha256"] and
          value["inputAudit"] == "PASS_BEFORE_AND_AFTER",
          "NATIVE_RUN_SOURCE_BINDING_INVALID")
    _need(type(value["exitCode"]) is int and value["exitCode"] == 0 and
          type(value["nativeExitCode"]) is int and value["nativeExitCode"] == 0 and
          value["terminationProven"] is True and value["cleanupStatus"] == "PASS" and
          value["cleanupElapsedMs"] is None and
          _integer(value["viaCost"], 60, 60) and
          _integer(value["nativeLimitMs"], MAX_NATIVE_LIMIT_MS, MAX_NATIVE_LIMIT_MS) and
          _integer(value["outerLimitMs"], MAX_OUTER_LIMIT_MS, MAX_OUTER_LIMIT_MS) and
          value["requestedExecutable"] == "powershell.exe",
          "NATIVE_RUN_EXIT_OR_BUDGET_INVALID")
    _need(_integer(value["processId"], 1, (1 << 63) - 1) and
          type(value["processStartTicks"]) is str and
          PROCESS_TICKS_RE.fullmatch(value["processStartTicks"]) is not None and
          type(value["elapsedSeconds"]) in (int, float) and
          math.isfinite(value["elapsedSeconds"]) and
          0 < value["elapsedSeconds"] <= 1800,
          "NATIVE_RUN_PROCESS_INVALID")
    for label, raw in (("stdout", stdout_raw), ("receipt", receipt_raw),
                       ("stderr", stderr_raw)):
        size, digest = value[label + "Bytes"], value[label + "Sha256"]
        _need(_integer(size, 0, MAX_CAPTURE_BYTES) and size == len(raw) and
              type(digest) is str and SHA256_RE.fullmatch(digest) is not None and
              digest == _sha256(raw), "NATIVE_RUN_ARTIFACT_BINDING_INVALID")

def _stable_source_check(spec_raw: bytes, spec_path: Path, spec_sha: str,
                         spec: Any, context: Mapping[str, Any],
                         ledger: types.ModuleType, capture: types.ModuleType) -> None:
    again = ledger._read_bounded(spec_path, MAX_SPEC_BYTES, "INPUT_SPEC")
    _need(again == spec_raw and _sha256(again) == spec_sha,
          "INPUT_SPEC_CHANGED_DURING_READ")
    try:
        second = ledger._validate_input_spec(spec, spec_path.parent, capture)
    except Exception as error:
        code = getattr(error, "code", None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code, re.ASCII):
            _fail(code)
        _fail("SOURCE_REVALIDATION_FAILED")
    _need(second["pinsSha256"] == context["pinsSha256"] and
          second["inputCount"] == context["inputCount"] and
          second["inputBytes"] == context["inputBytes"] and
          second["pinMap"] == context["pinMap"] and
          second["artifacts"] == context["artifacts"],
          "SOURCE_CHANGED_DURING_READ")

def _stable_helper_check(expected: Mapping[str, str]) -> None:
    directory = Path(os.path.abspath(__file__)).parent
    rows = (
        ("read_quality_scalars.py", "readerSha256", 2 * 1024 * 1024),
        ("read_current_prefix_ledger.py", "prefixLedgerSha256", 2 * 1024 * 1024),
        ("read_fixed_prefix_capture.py", "captureHelperSha256", 4 * 1024 * 1024),
        ("read_fixed_prefix_graph.py", "graphOracleSha256", 4 * 1024 * 1024),
    )
    for filename, key, limit in rows:
        raw = _read_local(directory / filename, limit, "PINNED_HELPER")
        _need(_sha256(raw) == expected[key], "PINNED_HELPER_CHANGED_DURING_READ")

def run_validation(spec_argument: str, spec_sha256: str) -> Dict[str, Any]:
    ledger, capture, helper_hashes = _load_pinned_helpers()
    _need(type(spec_sha256) is str and SHA256_RE.fullmatch(spec_sha256) is not None,
          "CLI_ARGUMENTS_INVALID")
    try:
        spec_path = ledger._resolve_path(spec_argument, Path.cwd(), "INPUT_SPEC")
        spec_raw = ledger._read_bounded(spec_path, MAX_SPEC_BYTES, "INPUT_SPEC")
        _need(_sha256(spec_raw) == spec_sha256, "INPUT_SPEC_SHA256_MISMATCH")
        spec = ledger._strict_json(spec_raw, "INPUT_SPEC")
        context = ledger._validate_input_spec(spec, spec_path.parent, capture)
    except QualityScalarInputError:
        raise
    except Exception as error:
        code = getattr(error, "code", None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code, re.ASCII):
            _fail(code)
        _fail("INPUT_SPEC_INVALID")
    _need(QUALITY_REQUIRED_INPUTS <= set(context["pinMap"]),
          "QUALITY_SOURCE_PINS_OMIT_REQUIRED_INPUT")
    result_raw = ledger._read_bounded(context["artifacts"]["resultPath"],
                                      MAX_RESULT_BYTES, "NATIVE_RUN")
    stdout_raw = ledger._read_bounded(context["artifacts"]["stdoutPath"],
                                      MAX_CAPTURE_BYTES, "RUN_STDOUT")
    receipt_raw = ledger._read_bounded(context["artifacts"]["receiptPath"],
                                       MAX_CAPTURE_BYTES, "RUN_RECEIPT")
    stderr_raw = ledger._read_bounded(context["artifacts"]["stderrPath"],
                                      MAX_CAPTURE_BYTES, "RUN_STDERR", allow_empty=True)
    run_result = ledger._strict_json(result_raw, "NATIVE_RUN")
    _validate_native_run(run_result, context, stdout_raw, receipt_raw, stderr_raw)
    _console, receipt_lines = _validate_console_and_receipt(
        stdout_raw, receipt_raw, stderr_raw, ledger)
    parsed = _validate_receipt_lines(receipt_lines, ledger)
    _stable_source_check(spec_raw, spec_path, spec_sha256, spec, context, ledger, capture)
    _stable_helper_check(helper_hashes)
    required_hashes = {
        relative: context["pinMap"][relative]["sha256"]
        for relative in sorted(QUALITY_REQUIRED_INPUTS)
    }
    event = parsed["event"]
    return {
        "schema": RESULT_SCHEMA, "status": "PASS",
        "sourceSnapshotRevision": SOURCE_REVISION,
        "source": {
            "inputCount": context["inputCount"], "inputBytes": context["inputBytes"],
            "pinsSha256": context["pinsSha256"],
            "requiredNativeInputSha256": required_hashes,
            "readerSha256": helper_hashes["readerSha256"],
            "prefixLedgerSha256": helper_hashes["prefixLedgerSha256"],
            "captureHelperSha256": helper_hashes["captureHelperSha256"],
            "graphOracleSha256": helper_hashes["graphOracleSha256"],
            "frozenWireSha256": FROZEN_WIRE_SHA256,
        },
        "artifacts": {
            "inputSpecSha256": _sha256(spec_raw), "nativeRunSha256": _sha256(result_raw),
            "stdoutBytes": len(stdout_raw), "stdoutSha256": _sha256(stdout_raw),
            "receiptBytes": len(receipt_raw), "receiptSha256": _sha256(receipt_raw),
            "stderrBytes": len(stderr_raw), "stderrSha256": _sha256(stderr_raw),
        },
        "nativeRun": {
            "processId": run_result["processId"],
            "processStartTicks": run_result["processStartTicks"],
            "elapsedSeconds": run_result["elapsedSeconds"],
            "nativeLimitMs": run_result["nativeLimitMs"],
            "outerLimitMs": run_result["outerLimitMs"],
            "cleanupStatus": run_result["cleanupStatus"],
            "inputAudit": run_result["inputAudit"],
        },
        "qualityObservation": {
            "runId": parsed["runId"], "variant": VARIANT,
            "operations": 2, "nullOperations": 1, "probeOperations": 1,
            "pairs": 1, "confirmedScalarEvents": 1,
            "routeRequestIndex": event["routeRequestIndex"],
            "selectedPlacementIndex": event["selectedPlacementIndex"],
            "selectedPlacementAttempt": event["selectedPlacementAttempt"],
            "pass": event["pass"], "pathNodeCount": event["pathNodeCount"],
            "transitionCount": event["transitionCount"],
            "planarLength": event["planarLength"],
            "directDistance": event["directDistance"], "bends": event["bends"],
            "rejectDirectZero": event["rejectDirectZero"],
            "rejectLengthOverThreeDirect": event["rejectLengthOverThreeDirect"],
            "rejectBendsOver16": event["rejectBendsOver16"],
            "qualityRejected": event["qualityRejected"],
            "manualDriverMillis": event["manualDriverMillis"],
            "manualSliceSteps": event["manualSliceSteps"],
            "routeMillisStatus": ROUTE_MILLIS_STATUS,
            "feasibility": "UNKNOWN", "layoutQualification": False,
        },
    }

def _canonical_json(value: Any) -> bytes:
    try:
        return json.dumps(value, sort_keys=True, separators=(",", ":"),
                          ensure_ascii=True, allow_nan=False).encode("ascii")
    except (TypeError, ValueError, UnicodeError, RecursionError):
        _fail("OUTPUT_CANONICALIZATION_INVALID")

def _parse_cli(argv: Sequence[str]) -> Tuple[str, str]:
    _need(len(argv) == 4 and argv[0] == "--input-spec" and
          argv[2] == "--input-spec-sha256", "CLI_ARGUMENTS_INVALID")
    _need(type(argv[1]) is str and 0 < len(argv[1]) <= 4096 and
          type(argv[3]) is str and SHA256_RE.fullmatch(argv[3]) is not None,
          "CLI_ARGUMENTS_INVALID")
    return argv[1], argv[3]

def main(argv: Optional[Sequence[str]] = None) -> int:
    try:
        spec_path, spec_sha = _parse_cli(list(sys.argv[1:] if argv is None else argv))
        report = run_validation(spec_path, spec_sha)
        raw = _canonical_json(report) + b"\n"
        _need(len(raw) <= 128 * 1024, "OUTPUT_SIZE_INVALID")
        sys.stdout.buffer.write(raw)
        sys.stdout.buffer.flush()
        return 0
    except Exception as error:
        code = getattr(error, "code", None)
        if type(code) is not str or re.fullmatch(r"[A-Z0-9_]{1,96}", code, re.ASCII) is None:
            code = "INPUT_INVALID"
        try:
            sys.stdout.write("FAIL: " + code + "\n")
            sys.stdout.flush()
        except Exception:
            pass
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
