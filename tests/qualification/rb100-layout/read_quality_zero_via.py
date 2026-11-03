#!/usr/bin/env python3
"""Source-bound reader for the bounded RB100 prefix allocation and graph.

Each graph is a directed, two-face relaxation of one captured source branch:
D_SENSE's publisher path or LOAD12's accepted pre-publication state.
The allocation report records creation ownership for the captured prefix; it
does not infer later use, chronological history, route acceptance, or playable
qualification.
"""
from __future__ import annotations

import hashlib
import heapq
import json
import os
import re
import sys
import types
import uuid
from collections import deque
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple


INPUT_SCHEMA = "p07-quality-zero-via-input-v5"
RUN_SCHEMA = "q100-quality-zero-via-native-run-v5"
RESULT_SCHEMA = "q100-quality-zero-via-reader-result-v5"
SUITES = {
    "D_SENSE": "Rb100QualityZeroViaGraphContractTest",
    "LOAD12": "Rb100Load12ZeroViaGraphContractTest",
}
SUITE = SUITES["D_SENSE"]
ENTRY_SCRIPT = "scripts/verify-current-contracts.ps1"
SOURCE_REVISION = 20
SOURCE_INPUT_COUNT = 1077
CONTRACT_JAVA_COUNT = 221
LAYOUT_VERSION = 26
DEPENDENCY_EPOCH = "tsj-generation-dependencies-v28"
PLAN_VERSION = 1
MODEL_VERSION = 4
PHYSICAL_INTENT_VERSION = 9
MAX_SCALAR = 2_147_483_647
MAX_GRID_CELLS = 250_000
MAX_JAVA_LONG = 9_223_372_036_854_775_807
MAX_WORK_UNITS = 640
MAX_WORK_QUANTUM = 32_768
MAX_TOTAL_WORK = MAX_WORK_UNITS * MAX_WORK_QUANTUM
MAX_ACTIVE_MS = 90_000
MAX_VIAS = 48
MAX_ABS_BOARD_COORDINATE = 100_000_000
MAX_VIA_LAND_RADIUS = 6
MAX_VIA_CENTER_COORDINATE = MAX_ABS_BOARD_COORDINATE - MAX_VIA_LAND_RADIUS
ROOT_SEED = "9007199254740993"
PLACEMENT_SEED = "-6587958367974952701"
ROUTING_SEED = "8445301674744508279"
VARIANT = "SEPARATE_DIRECT_SWITCHING"
NULL_OPERATION_ID = "QUALITY_NULL"
PROBE_OPERATION_ID = "QUALITY_PROBE"
FOCUSED_PASS = (
    "PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN."
)
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."

PINNED_QUALITY_PATH_READER_SHA256 = (
    "66d482a7e00fb5be95606091ff5870c369b132065951cf08cf59e3ac26bdeec6"
)
QUALITY_REQUIRED_INPUTS = frozenset({
    "tests/contracts/Rb100QualityZeroViaGraphContractTest.java",
    "tests/contracts/Rb100Load12ZeroViaGraphContractTest.java",
    "tests/contracts/Rb100PhysicalDiagnostics.java",
    "tests/contracts/P07ZeroViaGraphDiagnostics.java",
    "tests/contracts/P07ZeroViaGraphContractTest.java",
    "tests/contracts/P07ShortcutQueryDiagnostics.java",
    "tests/contracts/P07ShortcutQueryContractTest.java",
    "tests/contracts/P07PrefixAllocationDiagnostics.java",
    "tests/contracts/P07PrefixAllocationContractTest.java",
    "tests/contracts/P07AcceptedBranchCaptureDiagnostics.java",
    "tests/contracts/P07AcceptedBranchCaptureContractTest.java",
    "scripts/verify-current-contracts.ps1",
    "tests/contracts/current-contract-runner-contract.ps1",
})

GRAPH_EVENT_MARKER = "RB100_ZERO_VIA_GRAPH_EVENT"
GRAPH_SUMMARY_MARKER = "PASS: RB100 quality zero-via graph contracts"
ALLOCATION_EVENT_MARKER = "RB100_PREFIX_ALLOCATION_EVENT"
ALLOCATION_SUMMARY_MARKER = "PASS: RB100 prefix allocation contracts"
LOAD12_BRANCH_EVENT_MARKER = "RB100_LOAD12_BRANCH_EVENT"
LOAD12_BRANCH_SUMMARY_MARKER = "PASS: RB100 LOAD12 branch contracts"
RECEIPT_MARKERS = (
    "RB100_QUALITY_SCALAR_CONTROLS",
    "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS",
    "RB100_QUALITY_SCALAR_OPERATION",
    "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS",
    "RB100_QUALITY_SCALAR_OPERATION",
    "RB100_QUALITY_SCALAR_EVENT",
    "RB100_QUALITY_SCALAR_PAIR",
    "PASS: RB100 quality scalar contracts",
    "RB100_QUALITY_PATH_EVENT",
    "PASS: RB100 quality path contracts",
    GRAPH_EVENT_MARKER,
    GRAPH_SUMMARY_MARKER,
    ALLOCATION_EVENT_MARKER,
    ALLOCATION_SUMMARY_MARKER,
    LOAD12_BRANCH_EVENT_MARKER,
    LOAD12_BRANCH_SUMMARY_MARKER,
)
EVENT_PREFIXES = ("RB100_", "P07_", "PASS: RB100")

GRAPH_EVENT_KEYS = frozenset((
    "protocolVersion sourceRevision runId operationId target scope binding diagnostic "
    "feasibility legalRouteProof layoutQualification routeAdoption"
).split())
GRAPH_BINDING_KEYS = frozenset((
    "branchIdentityVerified sourcePublisherUnchanged sourceKind pass branchOrdinal "
    "netPosition netIndexNextFetch netId startPadId rootPadId sourceReleasedBeforeSuccessorWork "
    "reachedPthPadIds foundTerminalPadId endCell endLayer goalMaskLength goalUnionCellCount "
    "policy primaryLayer initialLayerMask maximumVias maximumTransitions prefixHoleCount "
    "pathNodeCount pathTransitionCount pathTupleSha256 goalFaceBitsSha256"
).split())
GRAPH_DIAGNOSTIC_KEYS = frozenset("schema stability grid path work masks".split())
GRAPH_STABILITY_KEYS = frozenset((
    "prefixGeometrySha256 liveBeforeVerified liveAfterVerified liveEffectsUnchanged "
    "goalVerified originalPathVerified"
).split())
GRAPH_GRID_KEYS = frozenset((
    "width height cells originX originY pitch startCell initialLayerMask endCell endLayer "
    "goalUnionCellCount"
).split())
GRAPH_PATH_KEYS = frozenset(
    "nodeCount planarEdgeCount transitionCount tupleSha256".split())
GRAPH_WORK_KEYS = frozenset((
    "advanceCalls workSteps copyEntries comparisonEntries geometryBytes effectUnits "
    "ordinaryQueries goalCells reachedPadChecks encodedCells pathNodes pathEdgesVerified "
    "tupleBytes serializedUtf16Units activeMillis"
).split())
GRAPH_MASK_KEYS = frozenset("topStepsHex bottomStepsHex goalFacesHex".split())
GRAPH_SUMMARY_KEYS = frozenset((
    "protocolVersion sourceRevision status runId operationId events target scope ordinaryQueries "
    "sourceReleasedBeforeSuccessorWork "
    "feasibility legalRouteProof layoutQualification routeAdoption"
).split())

ALLOCATION_EVENT_KEYS = frozenset((
    "protocolVersion sourceRevision runId operationId scope binding diagnostic "
    "chronologyProof routeAdoption layoutQualification"
).split())
ALLOCATION_BINDING_KEYS = frozenset((
    "branchIdentityVerified sourcePublisherUnchanged pass netId startPadId rootPadId "
    "prefixHoleCount maximumVias templateHoleCount searchNetIndex currentNetPosition "
    "currentBranchOrdinal netOrder"
).split())
ALLOCATION_DIAGNOSTIC_KEYS = frozenset("stability work totals rows".split())
ALLOCATION_STABILITY_KEYS = frozenset((
    "prefixGeometrySha256 liveBeforeVerified liveAfterVerified "
    "holeIdentitiesVerified lineageVerified"
).split())
ALLOCATION_WORK_KEYS = frozenset((
    "advanceCalls workSteps rowsScanned netComparisons netTotalsWritten activeMillis"
).split())
ALLOCATION_TOTALS_KEYS = frozenset("totalHoles byKind byNet".split())
ALLOCATION_KIND_TOTAL_KEYS = frozenset("VIA PLATED NON_PLATED".split())
ALLOCATION_NET_TOTAL_KEYS = frozenset("netPosition netId holeCount".split())
ALLOCATION_ROW_KEYS = frozenset((
    "index id netId kind x y drillRadius landRadius exposure fingerprint "
    "creatorNetPosition creatorBranchOrdinal piece"
).split())
ALLOCATION_SUMMARY_KEYS = frozenset((
    "protocolVersion sourceRevision status runId operationId events scope totalHoles "
    "chronologyProof routeAdoption layoutQualification"
).split())

LOAD12_EVENT_KEYS = frozenset((
    "protocolVersion sourceRevision runId operationId scope rootSeed placementSeed "
    "routingSeed routeRequestIndex selectedPlacementIndex selectedPlacementAttempt "
    "status claims pre post"
).split())
LOAD12_CLAIMS_KEYS = frozenset((
    "avoidability chronologyProof routeAdoption layoutQualification playable100"
).split())
LOAD12_PRE_KEYS = frozenset((
    "schema source seeds prefixInventory prefixTraces path goal nativePublisher work stability"
).split())
LOAD12_SOURCE_KEYS = frozenset((
    "pass netId netIndexNextFetch netPosition netOrder branchOrdinal policy "
    "templateHoleCount startPadAttachment startCopperFaceMask rootCopperFaceMask "
    "startPadId rootPadId startX startY rootX rootY reachedPadIds "
    "reachedPthPadIds grid"
).split())
LOAD12_GRID_KEYS = frozenset((
    "minX minY width height cells primaryLayer maximumVias maximumTransitions"
).split())
LOAD12_SEEDS_KEYS = frozenset("schema queueSize faceMask nodes".split())
LOAD12_SEED_NODE_KEYS = frozenset((
    "heapIndex cell x y layer direction transitions cost heuristic serial key"
).split())
LOAD12_PREFIX_TRACE_KEYS = frozenset(("count rows".split()))
LOAD12_TRACE_ROW_KEYS = frozenset((
    "index sourceId netId startPadId endPadId layer exposure width xPoints yPoints "
    "traceFingerprint"
).split())
LOAD12_PATH_KEYS = frozenset((
    "nodeCount gridSteps length bends transitionCount directDistance tupleSha256 "
    "nodes planarRuns"
).split())
LOAD12_PATH_NODE_KEYS = frozenset((
    "index parentIndex cell x y layer direction transitions cost heuristic serial key"
).split())
LOAD12_PLANAR_RUN_KEYS = frozenset(
    "layer firstNodeIndex lastNodeIndex xPoints yPoints".split())
LOAD12_GOAL_KEYS = frozenset((
    "cellCount topCount bottomCount bothCount faceBits faceBitsSha256 foundGoal"
).split())
LOAD12_PUBLISHER_KEYS = frozenset((
    "stage qualityIndex nodeCount length bends directDistance endCell endX endY "
    "endLayer endTransitions endTerminalPadId"
).split())
LOAD12_PRE_WORK_KEYS = frozenset((
    "advanceCalls workSteps prefixWorkSteps seedEntries pathNodes goalCells "
    "traceRows serializedUtf16Units activeMillis"
).split())
LOAD12_PRE_STABILITY_KEYS = frozenset((
    "liveBeforeVerified liveAfterVerified seedIdentityVerified pathChainVerified "
    "prefixLineageVerified goalMaskVerified traceIdentityVerified"
).split())
LOAD12_POST_KEYS = frozenset(
    "schema source post work stability claims".split())
LOAD12_POST_SOURCE_KEYS = frozenset((
    "pass netId branchOrdinal searchBranches currentNet searchBranchIsNull "
    "searchPublisherIsNull publisherStage startPadId reachedStartCount "
    "preHoleCount preTraceCount"
).split())
LOAD12_POST_INVENTORY_KEYS = frozenset((
    "holeCount createdHoleCount createdTraceCount holeRows createdHoleIds "
    "retainedHoleBindings createdTraceRows retainedTraceBindings transitionBindings"
).split())
LOAD12_RETAINED_HOLE_KEYS = frozenset((
    "id preFingerprint postFingerprint preIndex postIndex sameObject"
).split())
LOAD12_RETAINED_TRACE_KEYS = frozenset((
    "sourceId preFingerprint postFingerprint preIndex postIndex sameObject"
).split())
LOAD12_TRANSITION_BINDING_KEYS = frozenset((
    "pathNodeIndex fromLayer toLayer cell x y netId viaId viaFingerprint "
    "creatorNetPosition creatorBranchOrdinal piece createdInBranch"
).split())
LOAD12_POST_WORK_KEYS = frozenset((
    "advanceCalls workSteps aggregateWorkSteps holeRows traceRows transitionMatches "
    "serializedUtf16Units activeMillis"
).split())
LOAD12_POST_STABILITY_KEYS = frozenset((
    "liveBeforeVerified liveAfterVerified searchIdentityVerified publisherCompleted "
    "reachedStartVerified holeIdentityVerified traceIdentityVerified "
    "transitionBindingsVerified traceRunsVerified"
).split())
LOAD12_SUMMARY_KEYS = frozenset((
    "protocolVersion sourceRevision status runId operationId events scope rootSeed "
    "placementSeed routingSeed routeRequestIndex selectedPlacementIndex "
    "selectedPlacementAttempt pathNodes transitions createdHoles claims"
).split())
LOAD12_PRE_FIXED_JSON_COUNTERS = frozenset((
    ("work", "advanceCalls"), ("work", "workSteps"),
    ("work", "serializedUtf16Units"), ("work", "activeMillis"),
))
LOAD12_POST_FIXED_JSON_COUNTERS = frozenset((
    ("work", "advanceCalls"), ("work", "workSteps"),
    ("work", "aggregateWorkSteps"), ("work", "serializedUtf16Units"),
    ("work", "activeMillis"),
))

INPUT_SPEC_KEYS = frozenset((
    "schema sourceRoot pinsPath pinsSha256 inputCount inputBytes "
    "sourceSnapshotRevision resultPath stdoutPath receiptPath stderrPath target suite"
).split())
LEGACY_INPUT_SPEC_KEYS = INPUT_SPEC_KEYS - frozenset(("target", "suite"))

SHA256_RE = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
UUID_RE = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\Z",
    re.ASCII,
)
ERROR_CODE_RE = re.compile(r"[A-Z0-9_]{1,96}\Z", re.ASCII)
HEX_MASK_RE = re.compile(r"[0-9a-f]+\Z", re.ASCII)
ALLOCATION_OWNER_RE = re.compile(r"([1-9][0-9]*)/(0|[1-9][0-9]*)\Z", re.ASCII)
ALLOCATION_NET_COUNT = 256
ALLOCATION_ID_UTF16_LIMIT = 4096
MAX_SPEC_BYTES = 1024 * 1024
MAX_RESULT_BYTES = 2 * 1024 * 1024
MAX_CAPTURE_BYTES = 32 * 1024 * 1024


class QualityZeroViaInputError(ValueError):
    """Static failure code; input contents and private paths are never echoed."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _fail(code: str) -> None:
    raise QualityZeroViaInputError(code)


def _need(condition: bool, code: str) -> None:
    if not condition:
        _fail(code)


def _integer(value: Any, minimum: int, maximum: int) -> bool:
    return type(value) is int and minimum <= value <= maximum


def _exact_keys(value: Any, expected: Iterable[str], code: str) -> None:
    _need(type(value) is dict and set(value) == set(expected), code)


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _safe_foreign_code(error: BaseException, fallback: str) -> str:
    code = getattr(error, "code", None)
    return code if type(code) is str and ERROR_CODE_RE.fullmatch(code) else fallback


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


def _read_local(path: Path, limit: int, code: str,
                allow_empty: bool = False) -> bytes:
    try:
        _need(not _has_reparse_component(path) and not _is_reparse(path),
              code + "_REPARSE_PATH")
        info = path.stat()
        _need(path.is_file(), code + "_NOT_FILE")
        _need((0 <= info.st_size if allow_empty else 0 < info.st_size) and
              info.st_size <= limit, code + "_SIZE_INVALID")
        raw = path.read_bytes()
        _need(len(raw) == info.st_size and len(raw) <= limit,
              code + "_READ_SIZE_CHANGED")
        return raw
    except QualityZeroViaInputError:
        raise
    except (OSError, RuntimeError, ValueError):
        _fail(code + "_READ_FAILED")


def _exec_private(raw: bytes, path: Path, name: str) -> types.ModuleType:
    module = types.ModuleType(name)
    module.__file__ = os.fspath(path)
    module.__package__ = ""
    previous = sys.modules.get(name)
    had_previous = name in sys.modules
    sys.modules[name] = module
    try:
        source = raw.decode("utf-8", errors="strict")
        exec(compile(source, os.fspath(path), "exec", dont_inherit=True), module.__dict__)
    except (UnicodeDecodeError, SyntaxError, ValueError, TypeError):
        _fail("FROZEN_QUALITY_PATH_READER_SOURCE_INVALID")
    finally:
        if had_previous:
            sys.modules[name] = previous
        else:
            sys.modules.pop(name, None)
    return module


def _load_pinned_helpers() -> Tuple[types.ModuleType, types.ModuleType,
                                    types.ModuleType, types.ModuleType,
                                    Dict[str, str]]:
    """Load frozen validators privately; only pinned schema metadata varies."""
    own_path = Path(os.path.abspath(__file__))
    _need(not _has_reparse_component(own_path), "READER_SOURCE_REPARSE_PATH")
    own_path = own_path.resolve(strict=True)
    _need(own_path.is_file() and not _is_reparse(own_path), "READER_SOURCE_INVALID")
    frozen_path = own_path.parent / "read_quality_path.py"
    frozen_raw = _read_local(frozen_path, 2 * 1024 * 1024,
                             "FROZEN_QUALITY_PATH_READER")
    _need(_sha256(frozen_raw) == PINNED_QUALITY_PATH_READER_SHA256,
          "FROZEN_QUALITY_PATH_READER_HASH_MISMATCH")
    frozen = _exec_private(
        frozen_raw, frozen_path, "_q100_quality_zero_via_path_" + uuid.uuid4().hex)
    # These are the compatibility metadata overrides on the private frozen
    # reader. The requested target suite is applied after its spec is validated;
    # no validator function or shared module state is changed.
    frozen.INPUT_SCHEMA = INPUT_SCHEMA
    frozen.RUN_SCHEMA = RUN_SCHEMA
    frozen.RESULT_SCHEMA = RESULT_SCHEMA
    frozen.SUITE = SUITE
    frozen.SOURCE_REVISION = SOURCE_REVISION
    frozen.SOURCE_INPUT_COUNT = SOURCE_INPUT_COUNT
    frozen.CONTRACT_JAVA_COUNT = CONTRACT_JAVA_COUNT
    frozen.QUALITY_REQUIRED_INPUTS = QUALITY_REQUIRED_INPUTS
    try:
        scalar, ledger, capture, helper_hashes = frozen._load_frozen_scalar()
    except frozen.QualityPathInputError as error:
        _fail(_safe_foreign_code(error, "FROZEN_VALIDATOR_LOAD_FAILED"))

    # The ledger pins the minimum-via validator as a nested helper. Record its
    # byte identity too, because the frozen scalar reader's legacy result omits it.
    minimum_path = own_path.parent / "fixed_prefix_minimum_vias.py"
    minimum_raw = _read_local(minimum_path, 2 * 1024 * 1024,
                              "MINIMUM_VIA_HELPER")
    expected_minimum_sha = getattr(ledger, "MINIMUM_VIAS_SHA256", None)
    _need(type(expected_minimum_sha) is str and
          _sha256(minimum_raw) == expected_minimum_sha,
          "MINIMUM_VIA_HELPER_HASH_MISMATCH")
    helper_hashes["frozenQualityPathReaderSha256"] = _sha256(frozen_raw)
    helper_hashes["minimumViaHelperSha256"] = _sha256(minimum_raw)
    helper_hashes["newReaderSha256"] = _sha256(
        _read_local(own_path, 2 * 1024 * 1024, "READER_SOURCE"))
    return frozen, scalar, ledger, capture, helper_hashes


def _ledger_call(ledger: types.ModuleType, function, fallback: str,
                 *args, **kwargs):
    try:
        return function(*args, **kwargs)
    except ledger.LedgerInputError as error:
        _fail(_safe_foreign_code(error, fallback))


def _path_call(frozen: types.ModuleType, function, fallback: str, *args):
    try:
        return function(*args)
    except frozen.QualityPathInputError as error:
        _fail(_safe_foreign_code(error, fallback))


def _scalar_call(scalar: types.ModuleType, function, fallback: str, *args):
    try:
        return function(*args)
    except scalar.QualityScalarInputError as error:
        _fail(_safe_foreign_code(error, fallback))


def _parse_tagged(line: str, marker: str, ledger: types.ModuleType) -> Any:
    prefix = marker + " "
    _need(type(line) is str and line.startswith(prefix),
          "GRAPH_RECEIPT_MARKER_ORDER_INVALID")
    try:
        body = line[len(prefix):].encode("utf-8", errors="strict")
    except UnicodeEncodeError:
        _fail("GRAPH_RECEIPT_ENCODING_INVALID")
    _need(0 < len(body) <= ledger.MAX_LINE_BYTES,
          "GRAPH_RECEIPT_RECORD_SIZE_INVALID")
    return _ledger_call(ledger, ledger._strict_json,
                        "GRAPH_RECEIPT_JSON_INVALID", body,
                        "GRAPH_RECEIPT_RECORD")


def _validate_target_spec(spec: Any) -> Tuple[str, str, Dict[str, Any]]:
    _exact_keys(spec, INPUT_SPEC_KEYS, "INPUT_SPEC_KEYS_INVALID")
    target = spec["target"]
    _need(type(target) is str and target in SUITES and
          type(spec["suite"]) is str and spec["suite"] == SUITES[target] and
          spec["schema"] == INPUT_SCHEMA and
          type(spec["sourceSnapshotRevision"]) is int and
          spec["sourceSnapshotRevision"] == SOURCE_REVISION and
          type(spec["inputCount"]) is int and
          spec["inputCount"] == SOURCE_INPUT_COUNT,
          "INPUT_SPEC_IDENTITY_INVALID")
    return target, spec["suite"], {
        key: spec[key] for key in LEGACY_INPUT_SPEC_KEYS
    }


def _validate_native_run_target(value: Any, target: str,
                                legacy_keys: Iterable[str]) -> Dict[str, Any]:
    _exact_keys(value, set(legacy_keys) | {"target"}, "NATIVE_RUN_KEYS_INVALID")
    _need(type(target) is str and target in SUITES and
          type(value["target"]) is str and value["target"] == target,
          "NATIVE_RUN_TARGET_INVALID")
    return {key: item for key, item in value.items() if key != "target"}


def _decode_masks(masks: Mapping[str, Any], cells: int) -> Tuple[List[int], List[int], List[int]]:
    top: List[int] = []
    bottom: List[int] = []
    goals: List[int] = []
    for name, output in (("topStepsHex", top), ("bottomStepsHex", bottom),
                         ("goalFacesHex", goals)):
        value = masks[name]
        _need(type(value) is str and len(value) == cells and
              HEX_MASK_RE.fullmatch(value) is not None,
              "GRAPH_MASK_ENCODING_INVALID")
        output.extend(int(char, 16) for char in value)
    return top, bottom, goals


def _ordinary_query_count(width: int, height: int) -> int:
    return 2 * (4 * width * height - 2 * width - 2 * height)


def _validate_boundaries(width: int, height: int,
                         top: Sequence[int], bottom: Sequence[int]) -> None:
    for layer, mask in enumerate((top, bottom)):
        for cell, nibble in enumerate(mask):
            x, y = cell % width, cell // width
            _need(y > 0 or not (nibble & 0x1), "GRAPH_MASK_NORTH_BOUNDARY_INVALID")
            _need(x + 1 < width or not (nibble & 0x2),
                  "GRAPH_MASK_EAST_BOUNDARY_INVALID")
            _need(y + 1 < height or not (nibble & 0x4),
                  "GRAPH_MASK_SOUTH_BOUNDARY_INVALID")
            _need(x > 0 or not (nibble & 0x8), "GRAPH_MASK_WEST_BOUNDARY_INVALID")


def _analyze_graph(width: int, height: int, pitch: int, start_cell: int,
                   end_cell: int, end_layer: int, initial_layer_mask: int,
                   top_steps: Sequence[int], bottom_steps: Sequence[int],
                   goal_faces: Sequence[int], target: str = "D_SENSE"
                   ) -> Dict[str, Any]:
    """Compare independent BFS and heap/Dijkstra relaxations per seeded face."""
    cells = width * height if _integer(width, 1, MAX_GRID_CELLS) and \
        _integer(height, 1, MAX_GRID_CELLS) else 0
    _need(0 < cells <= MAX_GRID_CELLS and
          target in SUITES and
          _integer(pitch, 1, MAX_SCALAR) and
          _integer(start_cell, 0, cells - 1) and
          _integer(end_cell, 0, cells - 1) and
          _integer(end_layer, 0, 1) and
          _integer(initial_layer_mask, 1, 3),
          "GRAPH_ANALYSIS_GRID_INVALID")
    _need(len(top_steps) == len(bottom_steps) == len(goal_faces) == cells,
          "GRAPH_ANALYSIS_MASK_SIZE_INVALID")
    _need(all(_integer(nibble, 0, 15) for nibble in top_steps) and
          all(_integer(nibble, 0, 15) for nibble in bottom_steps) and
          all(_integer(nibble, 0, 3) for nibble in goal_faces),
          "GRAPH_ANALYSIS_MASK_VALUE_INVALID")
    _validate_boundaries(width, height, top_steps, bottom_steps)
    directions = ((0, -1, 0x1), (1, 0, 0x2), (0, 1, 0x4), (-1, 0, 0x8))
    def summarize(algorithm: str) -> Dict[str, Any]:
        faces: List[Dict[str, Any]] = []
        all_eligible: List[int] = []
        for layer, mask in enumerate((top_steps, bottom_steps)):
            bit = 1 << layer
            if not (initial_layer_mask & bit):
                continue
            distances: List[Optional[int]] = [None] * cells
            distances[start_cell] = 0
            if algorithm == "queueBfs":
                queue = deque([start_cell])
                while queue:
                    source = queue.popleft()
                    distance = distances[source]
                    assert distance is not None
                    x, y = source % width, source // width
                    for dx, dy, direction_bit in directions:
                        if not (mask[source] & direction_bit):
                            continue
                        target_x, target_y = x + dx, y + dy
                        _need(0 <= target_x < width and 0 <= target_y < height,
                              "GRAPH_EDGE_OUT_OF_BOUNDS")
                        target_cell = target_y * width + target_x
                        if distances[target_cell] is None:
                            distances[target_cell] = distance + 1
                            queue.append(target_cell)
            else:
                frontier = [(0, start_cell)]
                while frontier:
                    distance, source = heapq.heappop(frontier)
                    if distances[source] != distance:
                        continue
                    x, y = source % width, source // width
                    for dx, dy, direction_bit in directions:
                        if not (mask[source] & direction_bit):
                            continue
                        target_x, target_y = x + dx, y + dy
                        _need(0 <= target_x < width and 0 <= target_y < height,
                              "GRAPH_EDGE_OUT_OF_BOUNDS")
                        target_cell = target_y * width + target_x
                        candidate = distance + 1
                        known = distances[target_cell]
                        if known is None or candidate < known:
                            distances[target_cell] = candidate
                            heapq.heappush(frontier, (candidate, target_cell))

            reachable_lengths: List[int] = []
            eligible_lengths: List[int] = []
            for cell, face_mask in enumerate(goal_faces):
                distance_steps = distances[cell]
                if not (face_mask & bit) or distance_steps is None:
                    continue
                length = distance_steps * pitch
                goal_x, goal_y = cell % width, cell // width
                direct = (abs(goal_x - start_cell % width) +
                          abs(goal_y - start_cell // width)) * pitch
                reachable_lengths.append(length)
                if direct > 0 and length <= 3 * direct:
                    eligible_lengths.append(length)
            end_steps = distances[end_cell] if layer == end_layer else None
            faces.append({
                "layer": layer,
                "reachableGoals": len(reachable_lengths),
                "shortestAnyGoal": min(reachable_lengths, default=None),
                "distanceCapturedEnd": (
                    None if end_steps is None else end_steps * pitch),
                "lengthEligibleGoals": len(eligible_lengths),
                "minimumEligibleLength": min(eligible_lengths, default=None),
            })
            all_eligible.extend(eligible_lengths)
        return {
            "seedLayerMask": initial_layer_mask,
            "faces": faces,
            "lengthEligibleGoals": len(all_eligible),
            "minimumEligibleLength": min(all_eligible, default=None),
            "geometricRelaxation": (
                "LENGTH_ELIGIBLE_GOAL_EXISTS_AS_RELAXATION_ONLY"
                if all_eligible else "NO_ZERO_VIA_LENGTH_ELIGIBLE_GOAL_IN_FIXED_PREFIX"
            ),
        }

    queue_summary = summarize("queueBfs")
    heap_summary = summarize("heapDijkstra")
    _need(queue_summary == heap_summary, "GRAPH_ORACLE_DISAGREEMENT")
    return {
        "target": target,
        "scope": "FIXED_PREFIX_ZERO_VIA_LENGTH_ONLY",
        "queueBfs": queue_summary,
        "heapDijkstra": heap_summary,
        "crossCheck": "IDENTICAL_PER_FACE_DISTANCES_AND_GOAL_COUNTS",
    }


def _graph_tuple_sha256(nodes: Sequence[Mapping[str, Any]]) -> str:
    return _sha256(_graph_tuple_bytes(nodes))


def _graph_tuple_bytes(nodes: Sequence[Mapping[str, Any]]) -> bytes:
    tuple_text = "\n".join(
        ",".join(str(node[field]) for field in (
            "index", "parentIndex", "cell", "x", "y", "layer", "direction",
            "transitions", "cost", "heuristic", "serial", "key"))
        for node in nodes)
    return tuple_text.encode("utf-8", errors="strict")


def _graph_json_string_utf16_units(value: str) -> int:
    units = 2
    for character in value:
        codepoint = ord(character)
        if character in ('"', "\\", "\b", "\f", "\n", "\r", "\t"):
            units += 2
        elif codepoint < 0x20 or codepoint == 0x7F:
            units += 6
        elif 0xD800 <= codepoint <= 0xDFFF:
            units += 6
        elif codepoint > 0xFFFF:
            units += 12
        else:
            units += 1
    return units


def _graph_serialized_json_utf16_units(value: Any) -> int:
    fixed_counter_names = frozenset(GRAPH_WORK_KEYS - {"advanceCalls", "workSteps"}) | {
        "advanceCalls", "workSteps"
    }

    def measure(item: Any, path: Tuple[str, ...] = ()) -> int:
        if path[:1] == ("work",) and len(path) == 2 and path[1] in fixed_counter_names:
            _need(type(item) is int and item >= 0,
                  "GRAPH_SERIALIZED_LENGTH_COUNTER_INVALID")
            return 19
        if item is None:
            return 4
        if type(item) is bool:
            return 4 if item else 5
        if type(item) is int:
            return len(str(item))
        if type(item) is str:
            return _graph_json_string_utf16_units(item)
        if type(item) is list:
            return 2 + max(0, len(item) - 1) + sum(
                measure(child, path) for child in item)
        if type(item) is dict:
            total = 2 + max(0, len(item) - 1)
            for key, child in item.items():
                _need(type(key) is str,
                      "GRAPH_SERIALIZED_LENGTH_KEY_INVALID")
                total += (_graph_json_string_utf16_units(key) + 1 +
                          measure(child, path + (key,)))
            return total
        _fail("GRAPH_SERIALIZED_LENGTH_VALUE_INVALID")

    return measure(value)


def _validate_graph_event(value: Any, scalar_event: Mapping[str, Any],
                          path: Mapping[str, Any], target: str = "D_SENSE",
                          load12_pre: Optional[Mapping[str, Any]] = None,
                          allocation_binding: Optional[Mapping[str, Any]] = None
                          ) -> Dict[str, Any]:
    _exact_keys(value, GRAPH_EVENT_KEYS, "GRAPH_EVENT_KEYS_INVALID")
    _need(target in SUITES and value["target"] == target,
          "GRAPH_TARGET_SPEC_MISMATCH")
    scope = ("ACTUAL_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY" if target == "D_SENSE"
             else "ACTUAL_LOAD12_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY")
    source_kind = ("D_SENSE_PUBLISHER_PATH" if target == "D_SENSE"
                   else "LOAD12_ACCEPTED_PRE_RESULT")
    if target == "LOAD12":
        _need(load12_pre is not None, "GRAPH_LOAD12_SOURCE_MISSING")
        source = load12_pre["source"]["source"]
        path_source = load12_pre["path"]
        source_grid = load12_pre["source"]["grid"]
        expected_source = {
            "pass": source["pass"], "branchOrdinal": source["branchOrdinal"],
            "netPosition": source["netPosition"],
            "netIndexNextFetch": source["netIndexNextFetch"],
            "netId": source["netId"], "startPadId": source["startPadId"],
            "rootPadId": source["rootPadId"],
            "reachedPthPadIds": source["reachedPthPadIds"],
            "foundTerminalPadId": path_source["endTerminalPadId"],
            "prefixHoleCount": load12_pre["prefix"]["inventory"]["totalHoles"],
            "maximumVias": source_grid["maximumVias"],
            "maximumTransitions": source_grid["maximumTransitions"],
            "primaryLayer": (0 if source_grid["primaryLayer"] == "TOP" else 1),
            "initialLayerMask": load12_pre["seeds"]["snapshot"]["faceMask"],
            "pathNodeCount": path_source["path"]["nodeCount"],
            "pathTransitionCount": path_source["transitions"],
            "pathTupleSha256": path_source["tupleSha256"],
            "goalFaceBitsSha256": load12_pre["path"]["goal"]["faceBitsSha256"],
            "goalUnionCellCount": load12_pre["path"]["goal"]["cellCount"],
            "startX": source["startX"], "startY": source["startY"],
            "rootX": source["rootX"], "rootY": source["rootY"],
            "width": source_grid["width"], "height": source_grid["height"],
            "originX": source_grid["minX"], "originY": source_grid["minY"],
            "pitch": 10, "startCell": load12_pre["source"]["startCell"],
            "endCell": path_source["endCell"],
            "endLayer": path_source["endLayer"],
            "endX": path_source["nodes"][-1]["x"],
            "endY": path_source["nodes"][-1]["y"],
        }
        source_nodes = path_source["nodes"]
        expected_policy = source["policy"]
    else:
        source = scalar_event
        path_source = path
        source_grid = None
        expected_source = {
            "pass": scalar_event["pass"], "netId": scalar_event["netId"],
            "startPadId": scalar_event["startPadId"],
            "rootPadId": scalar_event["rootPadId"],
            "primaryLayer": scalar_event["primaryLayer"],
            "startX": scalar_event["startX"], "startY": scalar_event["startY"],
            "endX": scalar_event["endX"], "endY": scalar_event["endY"],
            "endLayer": scalar_event["endLayer"],
            "width": scalar_event["gridWidth"],
            "height": scalar_event["gridHeight"],
            "originX": scalar_event["gridOriginX"],
            "originY": scalar_event["gridOriginY"],
            "pitch": scalar_event["gridPitch"],
            "pathNodeCount": path["pathNodeCount"],
            "pathTransitionCount": path["transitionCount"],
            "pathTupleSha256": _graph_tuple_sha256(path["nodes"]),
            "startCell": path["nodes"][0]["cell"],
            "endCell": path["nodes"][-1]["cell"],
        }
        _need(isinstance(allocation_binding, Mapping),
              "GRAPH_D_SENSE_ALLOCATION_BINDING_MISSING")
        expected_source.update({
            "branchOrdinal": allocation_binding.get("currentBranchOrdinal"),
            "netPosition": allocation_binding.get("currentNetPosition"),
            "netIndexNextFetch": allocation_binding.get("searchNetIndex"),
            "prefixHoleCount": allocation_binding.get("prefixHoleCount"),
            "maximumVias": allocation_binding.get("maximumVias"),
            "maximumTransitions": 4,
        })
        source_nodes = path["nodes"]
        expected_policy = "FULLER_TWO_LAYER"

    _need(_integer(value["protocolVersion"], 1, 1) and
          _integer(value["sourceRevision"], SOURCE_REVISION, SOURCE_REVISION) and
          type(value["runId"]) is str and UUID_RE.fullmatch(value["runId"]) is not None and
          value["runId"] == scalar_event["runId"] and
          value["operationId"] == PROBE_OPERATION_ID and
          value["scope"] == scope and
          value["feasibility"] == "UNKNOWN" and
          value["legalRouteProof"] is False and
          value["layoutQualification"] is False and
          value["routeAdoption"] is False,
          "GRAPH_EVENT_IDENTITY_OR_SCOPE_INVALID")

    binding = value["binding"]
    _exact_keys(binding, GRAPH_BINDING_KEYS, "GRAPH_BINDING_KEYS_INVALID")
    _need(binding["branchIdentityVerified"] is True and
          binding["sourcePublisherUnchanged"] is True and
          binding["sourceReleasedBeforeSuccessorWork"] is True and
          binding["sourceKind"] == source_kind and
          _integer(binding["pass"], 1, 1) and
          _integer(binding["branchOrdinal"], 1, MAX_SCALAR) and
          _integer(binding["netPosition"], 0, ALLOCATION_NET_COUNT - 1) and
          _integer(binding["netIndexNextFetch"], 1, ALLOCATION_NET_COUNT) and
          binding["netIndexNextFetch"] == binding["netPosition"] + 1 and
          all(type(binding[field]) is str and 0 < len(binding[field]) <= 512
              for field in ("netId", "startPadId", "rootPadId")) and
          all(binding[field] == expected_source[field] for field in (
              "pass", "branchOrdinal", "netPosition", "netIndexNextFetch",
              "netId", "startPadId", "rootPadId", "maximumVias",
              "prefixHoleCount", "maximumTransitions", "pathNodeCount",
              "pathTransitionCount", "pathTupleSha256")) and
          (target != "LOAD12" or
           (binding["initialLayerMask"] == expected_source["initialLayerMask"] and
            binding["reachedPthPadIds"] == expected_source["reachedPthPadIds"] and
            binding["foundTerminalPadId"] ==
            expected_source["foundTerminalPadId"])) and
          binding["policy"] == expected_policy,
          "GRAPH_BINDING_IDENTITY_INVALID")
    reached = binding["reachedPthPadIds"]
    _need(type(reached) is list and len(reached) <= 246 and
          all(type(item) is str and 0 < len(item) <= 256 for item in reached) and
          len(set(reached)) == len(reached) and reached == sorted(reached) and
          binding["rootPadId"] not in reached,
          "GRAPH_REACHED_PTH_IDS_INVALID")
    found_terminal = binding["foundTerminalPadId"]
    _need(found_terminal is None or
          (type(found_terminal) is str and 0 < len(found_terminal) <= 256 and
           (found_terminal == binding["rootPadId"] or found_terminal in reached)),
          "GRAPH_FOUND_TERMINAL_ID_INVALID")

    diagnostic = value["diagnostic"]
    _exact_keys(diagnostic, GRAPH_DIAGNOSTIC_KEYS, "GRAPH_DIAGNOSTIC_KEYS_INVALID")
    stability = diagnostic["stability"]
    _exact_keys(stability, GRAPH_STABILITY_KEYS, "GRAPH_STABILITY_KEYS_INVALID")
    _need(type(stability["prefixGeometrySha256"]) is str and
          SHA256_RE.fullmatch(stability["prefixGeometrySha256"]) is not None and
          stability["liveBeforeVerified"] is True and
          stability["liveAfterVerified"] is True and
          stability["liveEffectsUnchanged"] is True and
          stability["goalVerified"] is True and
          stability["originalPathVerified"] is True,
          "GRAPH_STABILITY_INVALID")
    _need(target != "LOAD12" or
          stability["prefixGeometrySha256"] ==
          load12_pre["prefix"]["stability"]["prefixGeometrySha256"],
          "GRAPH_LOAD12_PREFIX_GEOMETRY_MISMATCH")

    grid = diagnostic["grid"]
    _exact_keys(grid, GRAPH_GRID_KEYS, "GRAPH_GRID_KEYS_INVALID")
    width, height = grid["width"], grid["height"]
    cells = width * height if _integer(width, 1, MAX_GRID_CELLS) and \
        _integer(height, 1, MAX_GRID_CELLS) else 0
    _need(0 < cells <= MAX_GRID_CELLS and _integer(grid["cells"], cells, cells) and
          _integer(grid["originX"], -MAX_SCALAR - 1, MAX_SCALAR) and
          _integer(grid["originY"], -MAX_SCALAR - 1, MAX_SCALAR) and
          _integer(grid["pitch"], 1, MAX_SCALAR) and grid["pitch"] == 10 and
          _integer(grid["startCell"], 0, cells - 1) and
          _integer(grid["endCell"], 0, cells - 1) and
          _integer(grid["endLayer"], 0, 1) and
          _integer(grid["initialLayerMask"], 1, 3) and
          grid["initialLayerMask"] == binding["initialLayerMask"] and
          (target != "D_SENSE" or grid["initialLayerMask"] == 3) and
          (width, height, grid["originX"], grid["originY"], grid["pitch"]) ==
          (expected_source["width"], expected_source["height"],
           expected_source["originX"], expected_source["originY"],
           expected_source["pitch"]) and
          _integer(grid["goalUnionCellCount"], 0, cells),
          "GRAPH_GRID_DOMAIN_OR_SCALAR_BINDING_INVALID")
    first, last = source_nodes[0], source_nodes[-1]
    _need(grid["startCell"] == first["cell"] == expected_source["startCell"] and
          grid["endCell"] == last["cell"] == expected_source["endCell"] and
          grid["endLayer"] == last["layer"] == expected_source["endLayer"] and
          (first["x"], first["y"]) ==
          (expected_source["startX"], expected_source["startY"]) and
          (last["x"], last["y"]) ==
          (expected_source["endX"], expected_source["endY"]),
          "GRAPH_PATH_ENDPOINT_BINDING_INVALID")
    _need(_integer(binding["endCell"], 0, cells - 1) and
          binding["endCell"] == grid["endCell"] and
          _integer(binding["endLayer"], 0, 1) and
          binding["endLayer"] == grid["endLayer"] and
          _integer(binding["goalMaskLength"], 1, 2 * MAX_GRID_CELLS) and
          binding["goalMaskLength"] == 2 * cells and
          _integer(binding["goalUnionCellCount"], 0, cells) and
          binding["goalUnionCellCount"] == grid["goalUnionCellCount"] and
          binding["policy"] == expected_policy and
          _integer(binding["primaryLayer"], 0, 1) and
          binding["primaryLayer"] == expected_source["primaryLayer"] and
          binding["initialLayerMask"] == grid["initialLayerMask"] and
          _integer(binding["maximumVias"], MAX_VIAS, MAX_VIAS) and
          _integer(binding["maximumTransitions"], 4, 4) and
          _integer(binding["prefixHoleCount"], 0, MAX_VIAS) and
          binding["prefixHoleCount"] <= binding["maximumVias"] and
          _integer(binding["pathNodeCount"], 2, 4096) and
          _integer(binding["pathTransitionCount"], 0, 4) and
          type(binding["pathTupleSha256"]) is str and
          SHA256_RE.fullmatch(binding["pathTupleSha256"]) is not None and
          type(binding["goalFaceBitsSha256"]) is str and
          SHA256_RE.fullmatch(binding["goalFaceBitsSha256"]) is not None,
          "GRAPH_BINDING_GRAPH_DOMAIN_INVALID")

    _need(diagnostic["schema"] == "p07-zero-via-graph-r5",
          "GRAPH_DIAGNOSTIC_SCHEMA_INVALID")
    path_diagnostic = diagnostic["path"]
    _exact_keys(path_diagnostic, GRAPH_PATH_KEYS, "GRAPH_PATH_KEYS_INVALID")
    transitions = sum(
        1 for before, after in zip(source_nodes, source_nodes[1:])
        if before["layer"] != after["layer"])
    planar_count = len(source_nodes) - 1 - transitions
    source_tuple_sha = expected_source["pathTupleSha256"]
    _need(_integer(path_diagnostic["nodeCount"], len(source_nodes), len(source_nodes)) and
          _integer(path_diagnostic["planarEdgeCount"], planar_count, planar_count) and
          _integer(path_diagnostic["transitionCount"], transitions, transitions) and
          type(path_diagnostic["tupleSha256"]) is str and
          path_diagnostic["tupleSha256"] == source_tuple_sha and
          binding["pathNodeCount"] == len(source_nodes) and
          binding["pathTransitionCount"] == transitions and
          binding["pathTupleSha256"] == source_tuple_sha,
          "GRAPH_PATH_SOURCE_BINDING_INVALID")

    masks = diagnostic["masks"]
    _exact_keys(masks, GRAPH_MASK_KEYS, "GRAPH_MASK_KEYS_INVALID")
    top, bottom, goals = _decode_masks(masks, cells)
    _need(all(0 <= nibble <= 3 for nibble in goals),
          "GRAPH_GOAL_FACE_MASK_INVALID")
    goal_text = masks["goalFacesHex"]
    union_count = sum(1 for nibble in goals if nibble)
    goal_sha = _sha256(goal_text.encode("ascii"))
    _need(goals[grid["endCell"]] & (1 << grid["endLayer"]) and
          union_count == grid["goalUnionCellCount"] == binding["goalUnionCellCount"] and
          goal_sha == binding["goalFaceBitsSha256"] and
          (target != "LOAD12" or
           (goal_sha == load12_pre["path"]["goal"]["faceBitsSha256"] and
            union_count == load12_pre["path"]["goal"]["cellCount"])),
          "GRAPH_CAPTURED_END_NOT_A_GOAL")
    _need(transitions <= 4 and
          (target != "D_SENSE" or
           (transitions == 0 and scalar_event["transitionCount"] == 0)),
          "GRAPH_ORIGINAL_PATH_TRANSITIONS_INVALID")
    observed_transitions = 0
    observed_planar = 0
    for before, after in zip(source_nodes, source_nodes[1:]):
        dx, dy = after["x"] - before["x"], after["y"] - before["y"]
        if before["layer"] != after["layer"]:
            _need(before["cell"] == after["cell"] and dx == dy == 0 and
                  after["layer"] == 1 - before["layer"] and
                  after["transitions"] == before["transitions"] + 1 and
                  after["transitions"] <= binding["maximumTransitions"],
                  "GRAPH_ORIGINAL_PATH_FACE_FLIP_INVALID")
            observed_transitions += 1
            continue
        direction_bit = (0x2 if dx == grid["pitch"] else
                         0x8 if dx == -grid["pitch"] else
                         0x4 if dy == grid["pitch"] else
                         0x1 if dy == -grid["pitch"] else 0)
        _need(direction_bit != 0 and abs(dx) + abs(dy) == grid["pitch"] and
              after["transitions"] == before["transitions"] and
              (top if before["layer"] == 0 else bottom)[before["cell"]] & direction_bit,
              "GRAPH_ORIGINAL_PATH_EDGE_MISSING")
        observed_planar += 1
    _need(observed_transitions == transitions and observed_planar == planar_count and
          first["layer"] in (0, 1) and
          grid["initialLayerMask"] & (1 << first["layer"]) and
          (target != "LOAD12" or
           grid["initialLayerMask"] == load12_pre["seeds"]["snapshot"]["faceMask"]),
          "GRAPH_ORIGINAL_PATH_SOURCE_INCONSISTENT")

    work = diagnostic["work"]
    _exact_keys(work, GRAPH_WORK_KEYS, "GRAPH_WORK_KEYS_INVALID")
    ranges = {
        "advanceCalls": (1, MAX_WORK_UNITS),
        "workSteps": (1, MAX_TOTAL_WORK),
        "copyEntries": (1, MAX_TOTAL_WORK),
        "comparisonEntries": (1, MAX_TOTAL_WORK),
        "geometryBytes": (1, MAX_TOTAL_WORK),
        "effectUnits": (1, MAX_TOTAL_WORK),
        "ordinaryQueries": (0, MAX_TOTAL_WORK),
        "goalCells": (0, MAX_TOTAL_WORK),
        "reachedPadChecks": (0, MAX_TOTAL_WORK),
        "encodedCells": (0, MAX_TOTAL_WORK),
        "pathNodes": (0, MAX_TOTAL_WORK),
        "pathEdgesVerified": (0, MAX_TOTAL_WORK),
        "tupleBytes": (0, MAX_TOTAL_WORK),
        "serializedUtf16Units": (0, MAX_TOTAL_WORK),
        "activeMillis": (0, MAX_ACTIVE_MS),
    }
    for name, (minimum, maximum) in ranges.items():
        _need(_integer(work[name], minimum, maximum), "GRAPH_WORK_DOMAIN_INVALID")
    expected_queries = _ordinary_query_count(width, height)
    serialized_units = _graph_serialized_json_utf16_units(diagnostic)
    work_lower_bound = (
        work["copyEntries"] + work["comparisonEntries"] +
        work["geometryBytes"] + work["effectUnits"] +
        work["ordinaryQueries"] + work["goalCells"] +
        work["reachedPadChecks"] + work["pathNodes"] +
        work["pathEdgesVerified"] + work["tupleBytes"] +
        work["serializedUtf16Units"])
    _need(work["ordinaryQueries"] == expected_queries and
          work["goalCells"] == cells and work["encodedCells"] == 3 * cells and
          work["pathNodes"] == len(source_nodes) and
          work["pathEdgesVerified"] == len(source_nodes) - 1 and
          work["tupleBytes"] == len(_graph_tuple_bytes(source_nodes)) and
          work["reachedPadChecks"] >=
          len(reached) + len(reached) * (len(reached) - 1) // 2 and
          work["serializedUtf16Units"] == serialized_units and
          work["workSteps"] <= work["advanceCalls"] * MAX_WORK_QUANTUM and
          work["workSteps"] >= work_lower_bound,
          "GRAPH_WORK_CONSERVATION_INVALID")

    analysis = _analyze_graph(width, height, grid["pitch"], grid["startCell"],
                              grid["endCell"], grid["endLayer"],
                              grid["initialLayerMask"], top, bottom, goals, target)
    return {
        "event": dict(value), "binding": dict(binding),
        "diagnostic": dict(diagnostic), "analysis": analysis,
        "target": target, "scope": value["scope"], "feasibility": "UNKNOWN",
        "legalRouteProof": False, "layoutQualification": False,
        "routeAdoption": False,
    }


def _validate_graph_summary(value: Any, event: Mapping[str, Any],
                            graph: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, GRAPH_SUMMARY_KEYS, "GRAPH_SUMMARY_KEYS_INVALID")
    work = graph["diagnostic"]["work"]
    _need(_integer(value["protocolVersion"], 1, 1) and
          _integer(value["sourceRevision"], SOURCE_REVISION, SOURCE_REVISION) and
          value["status"] == "PASS" and value["runId"] == event["runId"] and
          value["operationId"] == PROBE_OPERATION_ID and
          _integer(value["events"], 1, 1) and
          value["target"] == event["target"] == graph["target"] and
          value["scope"] == event["scope"] and
          value["sourceReleasedBeforeSuccessorWork"] is True and
          value["sourceReleasedBeforeSuccessorWork"] ==
          event["binding"]["sourceReleasedBeforeSuccessorWork"] and
          _integer(value["ordinaryQueries"], 0, MAX_TOTAL_WORK) and
          value["ordinaryQueries"] == work["ordinaryQueries"] and
          value["feasibility"] == "UNKNOWN" and
          value["legalRouteProof"] is False and
          value["layoutQualification"] is False and
          value["routeAdoption"] is False,
          "GRAPH_SUMMARY_IDENTITY_OR_SCOPE_INVALID")
    return dict(value)


def _java_string_length(value: str) -> int:
    return len(value.encode("utf-16-le", errors="strict")) // 2


def _java_string_sort_key(value: str) -> bytes:
    return value.encode("utf-16-be", errors="strict")


def _valid_allocation_text(value: Any) -> bool:
    if type(value) is not str or not value:
        return False
    if any(ord(char) <= 0x1F or ord(char) == 0x7F for char in value):
        return False
    try:
        return 0 < _java_string_length(value) <= ALLOCATION_ID_UTF16_LIMIT
    except UnicodeEncodeError:
        return False


def _allocation_hole_fingerprint(hole_id: str, net_id: str,
                                 x: int, y: int) -> str:
    identity = (f"{_java_string_length(hole_id)}:{hole_id}VIA"
                f"{_java_string_length(net_id)}:{net_id}"
                f"{x},{y},2,6,EXPOSED")
    return identity


def _allocation_owner(hole_id: str, net_order: Sequence[str]
                      ) -> Tuple[int, int, int]:
    matches: List[Tuple[int, int, int]] = []
    for net_position, net_id in enumerate(net_order):
        prefix = "p07-via/" + net_id + "/"
        if not hole_id.startswith(prefix):
            continue
        suffix = hole_id[len(prefix):]
        parsed = ALLOCATION_OWNER_RE.fullmatch(suffix)
        if parsed is None:
            continue
        ordinal_text, piece_text = parsed.groups()
        if len(ordinal_text) > 10 or len(piece_text) > 10:
            continue
        ordinal, piece = int(ordinal_text), int(piece_text)
        if ordinal <= MAX_SCALAR and piece <= MAX_SCALAR:
            matches.append((net_position, ordinal, piece))
    _need(len(matches) == 1, "ALLOCATION_ROW_ID_INVALID")
    return matches[0]


def _validate_allocation_inventory(totals: Any, rows: Any,
                                   net_order: Sequence[str],
                                   current_net_position: int,
                                   current_branch_ordinal: int,
                                   expected_total: Optional[int] = None,
                                   allow_current_owner: bool = False
                                   ) -> Dict[str, Any]:
    """Validate the shared prefix-hole census used at both capture boundaries."""
    if totals is None:
        total_holes = expected_total
        _need(_integer(total_holes, 0, MAX_VIAS),
              "ALLOCATION_TOTAL_HOLES_INVALID")
    else:
        _exact_keys(totals, ALLOCATION_TOTALS_KEYS, "ALLOCATION_TOTALS_KEYS_INVALID")
        total_holes = totals["totalHoles"]
        _need(_integer(total_holes, 0, MAX_VIAS) and
              (expected_total is None or total_holes == expected_total),
              "ALLOCATION_TOTAL_HOLES_INVALID")
    _need(type(rows) is list and len(rows) == total_holes,
          "ALLOCATION_ROWS_INVALID")

    net_positions = {net_id: position for position, net_id in enumerate(net_order)}
    net_counts = [0] * len(net_order)
    row_ids: List[str] = []
    branch_owners: Dict[int, Tuple[int, str]] = {}
    for index, row in enumerate(rows):
        _exact_keys(row, ALLOCATION_ROW_KEYS, "ALLOCATION_ROW_KEYS_INVALID")
        hole_id, net_id = row["id"], row["netId"]
        _need(_integer(row["index"], 0, max(0, total_holes - 1)) and
              row["index"] == index and
              _valid_allocation_text(hole_id) and
              _valid_allocation_text(net_id) and net_id in net_positions,
              "ALLOCATION_ROW_ID_INVALID")
        owner_position, branch_ordinal, piece = _allocation_owner(hole_id, net_order)
        before_current = branch_ordinal < current_branch_ordinal
        current_owner = (allow_current_owner and
                         branch_ordinal == current_branch_ordinal and
                         owner_position == current_net_position)
        _need(owner_position == net_positions[net_id] and
              owner_position <= current_net_position and
              0 < branch_ordinal and (before_current or current_owner) and
              piece >= 0 and
              row["creatorNetPosition"] == owner_position and
              _integer(row["creatorNetPosition"], 0, len(net_order) - 1) and
              row["creatorBranchOrdinal"] == branch_ordinal and
              _integer(row["creatorBranchOrdinal"], 1, MAX_SCALAR) and
              row["piece"] == piece and _integer(row["piece"], 0, MAX_SCALAR),
              "ALLOCATION_ROW_OWNER_INVALID")
        prior_owner = branch_owners.get(branch_ordinal)
        _need(prior_owner is None or prior_owner == (owner_position, net_id),
              "ALLOCATION_BRANCH_OWNER_INCONSISTENT")
        branch_owners[branch_ordinal] = (owner_position, net_id)
        _need(row["kind"] == "VIA" and
              _integer(row["x"], -MAX_VIA_CENTER_COORDINATE,
                       MAX_VIA_CENTER_COORDINATE) and
              _integer(row["y"], -MAX_VIA_CENTER_COORDINATE,
                       MAX_VIA_CENTER_COORDINATE) and
              _integer(row["drillRadius"], 2, 2) and
              _integer(row["landRadius"], 6, 6) and
              row["exposure"] == "EXPOSED",
              "ALLOCATION_ROW_GEOMETRY_INVALID")
        _need(type(row["fingerprint"]) is str and
              row["fingerprint"] == _allocation_hole_fingerprint(
                  hole_id, net_id, row["x"], row["y"]),
              "ALLOCATION_ROW_FINGERPRINT_INVALID")
        row_ids.append(hole_id)
        net_counts[owner_position] += 1

    prior_position = -1
    for branch_ordinal in sorted(branch_owners):
        owner_position, _net_id = branch_owners[branch_ordinal]
        _need(owner_position >= prior_position,
              "ALLOCATION_BRANCH_ORDER_INCONSISTENT")
        prior_position = owner_position

    try:
        _need(len(set(row_ids)) == len(row_ids) and
              row_ids == sorted(row_ids, key=_java_string_sort_key),
              "ALLOCATION_ROW_ORDER_INVALID")
    except UnicodeEncodeError:
        _fail("ALLOCATION_ROW_ID_INVALID")

    by_net = None
    if totals is not None:
        by_kind = totals["byKind"]
        _exact_keys(by_kind, ALLOCATION_KIND_TOTAL_KEYS,
                    "ALLOCATION_KIND_TOTALS_INVALID")
        _need(all(_integer(by_kind[kind], 0, total_holes)
                  for kind in ("VIA", "PLATED", "NON_PLATED")) and
              by_kind["VIA"] == total_holes and
              by_kind["PLATED"] == 0 and by_kind["NON_PLATED"] == 0,
              "ALLOCATION_KIND_TOTALS_INVALID")

        by_net = totals["byNet"]
        _need(type(by_net) is list and len(by_net) == len(net_order),
              "ALLOCATION_NET_TOTALS_INVALID")
        for position, net_total in enumerate(by_net):
            _exact_keys(net_total, ALLOCATION_NET_TOTAL_KEYS,
                        "ALLOCATION_NET_TOTALS_INVALID")
            _need(_integer(net_total["netPosition"], 0, len(net_order) - 1) and
                  net_total["netPosition"] == position and
                  net_total["netId"] == net_order[position] and
                  _integer(net_total["holeCount"], 0, total_holes) and
                  net_total["holeCount"] == net_counts[position],
                  "ALLOCATION_NET_TOTALS_INVALID")
        _need(sum(net_counts) == total_holes and
              sum(net_total["holeCount"] for net_total in by_net) == total_holes,
              "ALLOCATION_TOTALS_CONSERVATION_INVALID")
    return {"totalHoles": total_holes, "netCounts": net_counts,
            "byNet": by_net, "rows": rows}


def _validate_prefix_allocation_event(value: Any,
                                      scalar_event: Mapping[str, Any],
                                      graph: Mapping[str, Any],
                                      target: str = "D_SENSE") -> Dict[str, Any]:
    _exact_keys(value, ALLOCATION_EVENT_KEYS, "ALLOCATION_EVENT_KEYS_INVALID")
    _need(_integer(value["protocolVersion"], 1, 1) and
          _integer(value["sourceRevision"], SOURCE_REVISION, SOURCE_REVISION) and
          type(value["runId"]) is str and UUID_RE.fullmatch(value["runId"]) is not None and
          value["runId"] == scalar_event["runId"] and
          value["operationId"] == PROBE_OPERATION_ID and
          value["scope"] == "ACTUAL_FIXED_PREFIX_CREATION_ALLOCATION_ONLY" and
          value["chronologyProof"] is False and
          value["routeAdoption"] is False and
          value["layoutQualification"] is False,
          "ALLOCATION_EVENT_IDENTITY_OR_SCOPE_INVALID")

    binding = value["binding"]
    _exact_keys(binding, ALLOCATION_BINDING_KEYS, "ALLOCATION_BINDING_KEYS_INVALID")
    _need(target in SUITES and
          (target != "D_SENSE" or graph["target"] == "D_SENSE"),
          "ALLOCATION_GRAPH_TARGET_MISMATCH")
    graph_binding = graph["binding"]
    _need(binding["branchIdentityVerified"] is True and
          binding["sourcePublisherUnchanged"] is True and
          _integer(binding["pass"], 0, MAX_SCALAR) and
          binding["pass"] == scalar_event["pass"] and
          binding["netId"] == scalar_event["netId"] and
          binding["startPadId"] == scalar_event["startPadId"] and
          binding["rootPadId"] == scalar_event["rootPadId"] and
          _integer(binding["prefixHoleCount"], 0, MAX_VIAS) and
          _integer(binding["maximumVias"], MAX_VIAS, MAX_VIAS) and
          binding["prefixHoleCount"] <= binding["maximumVias"] and
          _integer(binding["templateHoleCount"], 0, 0) and
          _integer(binding["currentBranchOrdinal"], 1, MAX_SCALAR) and
          (target != "D_SENSE" or
           (binding["pass"] == graph_binding["pass"] and
            binding["netId"] == graph_binding["netId"] and
            binding["startPadId"] == graph_binding["startPadId"] and
            binding["rootPadId"] == graph_binding["rootPadId"] and
            binding["prefixHoleCount"] == graph_binding["prefixHoleCount"] and
            binding["maximumVias"] == graph_binding["maximumVias"] and
            binding["currentBranchOrdinal"] == graph_binding["branchOrdinal"])),
          "ALLOCATION_BINDING_IDENTITY_INVALID")

    net_order = binding["netOrder"]
    _need(type(net_order) is list and 1 <= len(net_order) <= ALLOCATION_NET_COUNT and
          all(_valid_allocation_text(net_id) for net_id in net_order) and
          len(set(net_order)) == len(net_order),
          "ALLOCATION_NET_ORDER_INVALID")
    _need(_integer(binding["searchNetIndex"], 1, len(net_order)) and
          _integer(binding["currentNetPosition"], 0, len(net_order) - 1) and
          binding["currentNetPosition"] == binding["searchNetIndex"] - 1 and
          net_order[binding["currentNetPosition"]] == binding["netId"],
          "ALLOCATION_CURRENT_NET_POSITION_INVALID")

    diagnostic = value["diagnostic"]
    _exact_keys(diagnostic, ALLOCATION_DIAGNOSTIC_KEYS,
                "ALLOCATION_DIAGNOSTIC_KEYS_INVALID")
    stability = diagnostic["stability"]
    _exact_keys(stability, ALLOCATION_STABILITY_KEYS,
                "ALLOCATION_STABILITY_KEYS_INVALID")
    graph_prefix_sha = graph["diagnostic"]["stability"]["prefixGeometrySha256"]
    _need(type(stability["prefixGeometrySha256"]) is str and
          SHA256_RE.fullmatch(stability["prefixGeometrySha256"]) is not None and
          (target != "D_SENSE" or
           stability["prefixGeometrySha256"] == graph_prefix_sha) and
          stability["liveBeforeVerified"] is True and
          stability["liveAfterVerified"] is True and
          stability["holeIdentitiesVerified"] is True and
          stability["lineageVerified"] is True,
          "ALLOCATION_STABILITY_INVALID")

    inventory = _validate_allocation_inventory(
        diagnostic["totals"], diagnostic["rows"], net_order,
        binding["currentNetPosition"], binding["currentBranchOrdinal"],
        expected_total=binding["prefixHoleCount"])
    total_holes = inventory["totalHoles"]
    _need(target != "D_SENSE" or
          (binding["currentNetPosition"] == graph_binding["netPosition"] and
           binding["searchNetIndex"] == graph_binding["netIndexNextFetch"]),
          "ALLOCATION_BINDING_IDENTITY_INVALID")

    work = diagnostic["work"]
    _exact_keys(work, ALLOCATION_WORK_KEYS, "ALLOCATION_WORK_KEYS_INVALID")
    for field, minimum, maximum in (
            ("advanceCalls", 1, MAX_WORK_UNITS),
            ("workSteps", 1, MAX_TOTAL_WORK),
            ("rowsScanned", 0, MAX_VIAS),
            ("netComparisons", 0, MAX_VIAS * ALLOCATION_NET_COUNT),
            ("netTotalsWritten", 1, ALLOCATION_NET_COUNT),
            ("activeMillis", 0, MAX_ACTIVE_MS)):
        _need(_integer(work[field], minimum, maximum), "ALLOCATION_WORK_INVALID")
    expected_comparisons = total_holes * len(net_order)
    lineage_pair_checks = total_holes * (total_holes - 1) // 2
    expected_work = (total_holes + expected_comparisons + len(net_order) +
                     lineage_pair_checks)
    _need(work["rowsScanned"] == total_holes and
          work["netComparisons"] == expected_comparisons and
          work["netTotalsWritten"] == len(net_order) and
          work["workSteps"] >= expected_work and
          work["workSteps"] <= work["advanceCalls"] * MAX_WORK_QUANTUM and
          work["workSteps"] <= MAX_TOTAL_WORK,
          "ALLOCATION_WORK_CONSERVATION_INVALID")

    return {
        "event": dict(value), "binding": dict(binding),
        "diagnostic": dict(diagnostic), "scope": value["scope"],
        "chronologyProof": False, "routeAdoption": False,
        "layoutQualification": False,
    }


def _validate_prefix_allocation_summary(value: Any, event: Mapping[str, Any],
                                        allocation: Mapping[str, Any]
                                        ) -> Dict[str, Any]:
    _exact_keys(value, ALLOCATION_SUMMARY_KEYS,
                "ALLOCATION_SUMMARY_KEYS_INVALID")
    _need(_integer(value["protocolVersion"], 1, 1) and
          _integer(value["sourceRevision"], SOURCE_REVISION, SOURCE_REVISION) and
          value["status"] == "PASS" and
          value["runId"] == event["runId"] and
          value["operationId"] == PROBE_OPERATION_ID and
          _integer(value["events"], 1, 1) and
          value["scope"] == event["scope"] and
          _integer(value["totalHoles"], 0, MAX_VIAS) and
          value["totalHoles"] == allocation["binding"]["prefixHoleCount"] and
          value["chronologyProof"] is False and
          value["routeAdoption"] is False and
          value["layoutQualification"] is False,
          "ALLOCATION_SUMMARY_IDENTITY_OR_SCOPE_INVALID")
    return dict(value)


def _validate_load12_claims(value: Any, code: str) -> Dict[str, Any]:
    _exact_keys(value, LOAD12_CLAIMS_KEYS, code + "_KEYS_INVALID")
    _need(value["avoidability"] == "UNKNOWN" and
          value["chronologyProof"] is False and
          value["routeAdoption"] is False and
          value["layoutQualification"] is False and
          _integer(value["playable100"], 0, 0), code + "_INVALID")
    return dict(value)


def _load12_grid_cell_for_position(grid: Mapping[str, Any], cells: int,
                                  x: int, y: int, code: str) -> int:
    dx, dy = x - grid["minX"], y - grid["minY"]
    _need(dx >= 0 and dy >= 0 and dx % 10 == 0 and dy % 10 == 0 and
          dx // 10 < grid["width"] and dy // 10 < grid["height"], code)
    cell = (dy // 10) * grid["width"] + dx // 10
    _need(_integer(cell, 0, cells - 1), code)
    return cell


def _trace_field(value: Optional[str]) -> str:
    if value is None:
        return "-1:"
    return str(_java_string_length(value)) + ":" + value


def _allocation_net_totals_digest(net_order: Sequence[str],
                                 counts: Sequence[int]) -> str:
    canonical = "\n".join(
        str(position) + "," + str(_java_string_length(net_id)) + ":" + net_id +
        "," + str(counts[position])
        for position, net_id in enumerate(net_order))
    return _sha256(canonical.encode("utf-8", errors="strict"))


def _java_string_list_digest(values: Sequence[str]) -> str:
    return _sha256("\n".join(values).encode("utf-8", errors="strict"))


def _trace_fingerprint(row: Mapping[str, Any]) -> str:
    x_csv = ",".join(str(value) for value in row["xPoints"])
    y_csv = ",".join(str(value) for value in row["yPoints"])
    canonical = (_trace_field(row["sourceId"]) + _trace_field(row["netId"]) +
                 _trace_field(row["startPadId"]) + _trace_field(row["endPadId"]) +
                 row["layer"] + "\n" + row["exposure"] + "\n" +
                 str(row["width"]) + "\n" + x_csv + "\n" + y_csv)
    return _sha256(canonical.encode("utf-8", errors="strict"))


def _load12_json_string_utf16_units(value: str, legacy_prefix: bool) -> int:
    units = 2
    for character in value:
        codepoint = ord(character)
        if character in ('"', "\\", "\b", "\f", "\n", "\r", "\t"):
            units += 2
        elif codepoint < 0x20 or codepoint == 0x7F:
            units += 6
        elif legacy_prefix:
            units += 2 if codepoint > 0xFFFF else 1
        elif 0xD800 <= codepoint <= 0xDFFF:
            units += 6
        elif codepoint > 0xFFFF:
            units += 12
        else:
            units += 1
    return units


def _load12_serialized_json_utf16_units(
        value: Any, fixed_counter_paths: frozenset,
        legacy_raw_paths: frozenset = frozenset()) -> int:
    def measure(item: Any, path: Tuple[str, ...] = (),
                legacy_prefix: bool = False) -> int:
        if path in fixed_counter_paths:
            _need(type(item) is int and item >= 0,
                  "LOAD12_SERIALIZED_LENGTH_COUNTER_INVALID")
            return 19
        if path in legacy_raw_paths:
            legacy_prefix = True
        if item is None:
            return 4
        if type(item) is bool:
            return 4 if item else 5
        if type(item) is int:
            return len(str(item))
        if type(item) is str:
            return _load12_json_string_utf16_units(item, legacy_prefix)
        if type(item) is list:
            return (2 + max(0, len(item) - 1) +
                    sum(measure(child, path, legacy_prefix) for child in item))
        if type(item) is dict:
            total = 2 + max(0, len(item) - 1)
            for key, child in item.items():
                _need(type(key) is str,
                      "LOAD12_SERIALIZED_LENGTH_KEY_INVALID")
                total += (_load12_json_string_utf16_units(key, legacy_prefix) + 1 +
                          measure(child, path + (key,), legacy_prefix))
            return total
        _fail("LOAD12_SERIALIZED_LENGTH_VALUE_INVALID")

    return measure(value)


def _validate_load12_trace_rows(rows: Any, code: str) -> List[Dict[str, Any]]:
    _need(type(rows) is list and len(rows) <= 100_000, code + "_ROWS_INVALID")
    checked: List[Dict[str, Any]] = []
    for index, row in enumerate(rows):
        _exact_keys(row, LOAD12_TRACE_ROW_KEYS, code + "_ROW_KEYS_INVALID")
        source_id = row["sourceId"]
        start_pad = row["startPadId"]
        end_pad = row["endPadId"]
        _need(_integer(row["index"], 0, 99_999) and
              (source_id is None or _valid_allocation_text(source_id)) and
              _valid_allocation_text(row["netId"]) and
              (start_pad is None or _valid_allocation_text(start_pad)) and
              (end_pad is None or _valid_allocation_text(end_pad)) and
              row["layer"] in ("TOP", "BOTTOM") and
              row["exposure"] in ("EXPOSED", "COVERED") and
              _integer(row["width"], 1, MAX_SCALAR),
              code + "_ROW_DOMAIN_INVALID")
        x_points, y_points = row["xPoints"], row["yPoints"]
        _need(type(x_points) is list and type(y_points) is list and
              2 <= len(x_points) == len(y_points) <= 4096 and
              all(_integer(point, -MAX_ABS_BOARD_COORDINATE,
                           MAX_ABS_BOARD_COORDINATE)
                  for point in x_points + y_points),
              code + "_ROW_GEOMETRY_INVALID")
        fingerprint = row["traceFingerprint"]
        _need(type(fingerprint) is str and SHA256_RE.fullmatch(fingerprint) is not None and
              fingerprint == _trace_fingerprint(row),
              code + "_ROW_FINGERPRINT_INVALID")
        checked.append(row)
    return checked


def _validate_load12_source(value: Any) -> Dict[str, Any]:
    _exact_keys(value, LOAD12_SOURCE_KEYS, "LOAD12_SOURCE_KEYS_INVALID")
    _need(_integer(value["pass"], 1, 1) and value["netId"] == "LOAD12" and
          _integer(value["branchOrdinal"], 73, 73) and
          value["policy"] == "FULLER_TWO_LAYER" and
          _integer(value["templateHoleCount"], 0, 0) and
          value["startPadAttachment"] in ("PLATED_THROUGH_HOLE", "SURFACE_PAD") and
          _integer(value["startCopperFaceMask"], 1, 3) and
          _integer(value["rootCopperFaceMask"], 1, 3) and
          all(_valid_allocation_text(value[field])
              for field in ("netId", "startPadId", "rootPadId")) and
          value["startPadId"] != value["rootPadId"] and
          all(_integer(value[field], -MAX_ABS_BOARD_COORDINATE,
                       MAX_ABS_BOARD_COORDINATE)
              for field in ("startX", "startY", "rootX", "rootY")),
          "LOAD12_SOURCE_IDENTITY_INVALID")

    net_order = value["netOrder"]
    _need(type(net_order) is list and 1 <= len(net_order) <= ALLOCATION_NET_COUNT and
          all(_valid_allocation_text(net_id) for net_id in net_order) and
          len(set(net_order)) == len(net_order),
          "LOAD12_SOURCE_NET_ORDER_INVALID")
    _need(_integer(value["netIndexNextFetch"], 1, len(net_order)) and
          _integer(value["netPosition"], 0, len(net_order) - 1) and
          value["netPosition"] == value["netIndexNextFetch"] - 1 and
          net_order[value["netPosition"]] == value["netId"],
          "LOAD12_SOURCE_NET_POSITION_INVALID")

    reached = value["reachedPadIds"]
    reached_pth = value["reachedPthPadIds"]
    _need(type(reached) is list and len(reached) <= 246 and
          all(_valid_allocation_text(pad_id) for pad_id in reached) and
          len(set(reached)) == len(reached) and
          type(reached_pth) is list and len(reached_pth) <= len(reached) and
          all(_valid_allocation_text(pad_id) for pad_id in reached_pth) and
          len(set(reached_pth)) == len(reached_pth) and
          set(reached_pth) <= set(reached) and
          value["rootPadId"] in reached and
          value["rootPadId"] not in reached_pth and
          value["startPadId"] not in reached,
          "LOAD12_SOURCE_REACHED_PADS_INVALID")

    grid = value["grid"]
    _exact_keys(grid, LOAD12_GRID_KEYS, "LOAD12_GRID_KEYS_INVALID")
    width, height = grid["width"], grid["height"]
    cells = (width * height if _integer(width, 1, MAX_GRID_CELLS) and
             _integer(height, 1, MAX_GRID_CELLS) else 0)
    _need(0 < cells <= MAX_GRID_CELLS and
          _integer(grid["cells"], cells, cells) and
          _integer(grid["minX"], -MAX_ABS_BOARD_COORDINATE,
                   MAX_ABS_BOARD_COORDINATE) and
          _integer(grid["minY"], -MAX_ABS_BOARD_COORDINATE,
                   MAX_ABS_BOARD_COORDINATE) and
          grid["primaryLayer"] in ("TOP", "BOTTOM") and
          _integer(grid["maximumVias"], 48, 48) and
          _integer(grid["maximumTransitions"], 4, 4) and
          grid["minX"] + (width - 1) * 10 <= MAX_ABS_BOARD_COORDINATE and
          grid["minY"] + (height - 1) * 10 <= MAX_ABS_BOARD_COORDINATE and
          grid["minX"] + (width - 1) * 10 >= -MAX_ABS_BOARD_COORDINATE and
          grid["minY"] + (height - 1) * 10 >= -MAX_ABS_BOARD_COORDINATE,
          "LOAD12_GRID_DOMAIN_INVALID")
    start_cell = _load12_grid_cell_for_position(
        grid, cells, value["startX"], value["startY"],
        "LOAD12_SOURCE_START_PAD_OFF_GRID")
    root_cell = _load12_grid_cell_for_position(
        grid, cells, value["rootX"], value["rootY"],
        "LOAD12_SOURCE_ROOT_PAD_OFF_GRID")
    return {"source": dict(value), "grid": dict(grid), "cells": cells,
            "netOrder": list(net_order), "startCell": start_cell,
            "rootCell": root_cell}


def _validate_load12_seed_snapshot(value: Any,
                                   source: Mapping[str, Any]
                                   ) -> Dict[str, Any]:
    _exact_keys(value, LOAD12_SEEDS_KEYS, "LOAD12_SEEDS_KEYS_INVALID")
    _need(value["schema"] == "p07-accepted-branch-seeds-r1" and
          _integer(value["queueSize"], 2, 2) and
          _integer(value["faceMask"], 3, 3) and
          type(value["nodes"]) is list and len(value["nodes"]) == 2,
          "LOAD12_SEED_SNAPSHOT_INVALID")
    grid = source["grid"]
    nodes: List[Dict[str, Any]] = []
    layers = set()
    serials = set()
    start_cell = None
    for index, node in enumerate(value["nodes"]):
        _exact_keys(node, LOAD12_SEED_NODE_KEYS, "LOAD12_SEED_NODE_KEYS_INVALID")
        _need(_integer(node["heapIndex"], 0, 1) and node["heapIndex"] == index and
              _integer(node["cell"], 0, source["cells"] - 1) and
              _integer(node["x"], -MAX_ABS_BOARD_COORDINATE,
                       MAX_ABS_BOARD_COORDINATE) and
              _integer(node["y"], -MAX_ABS_BOARD_COORDINATE,
                       MAX_ABS_BOARD_COORDINATE) and
              _integer(node["layer"], 0, 1) and node["layer"] not in layers and
              _integer(node["direction"], 4, 4) and
              _integer(node["transitions"], 0, 0) and
              _integer(node["cost"], 0, MAX_SCALAR) and
              _integer(node["heuristic"], 0, MAX_SCALAR) and
              _integer(node["serial"], 0, MAX_SCALAR) and
              node["serial"] not in serials and
              _integer(node["key"], 0, MAX_SCALAR) and
              node["key"] == (((node["transitions"] * 2 + node["layer"]) * 5 +
                               node["direction"]) * source["cells"] + node["cell"]),
              "LOAD12_SEED_NODE_INVALID")
        expected_x = grid["minX"] + (node["cell"] % grid["width"]) * 10
        expected_y = grid["minY"] + (node["cell"] // grid["width"]) * 10
        _need((node["x"], node["y"]) == (expected_x, expected_y),
              "LOAD12_SEED_NODE_GRID_MAPPING_INVALID")
        if start_cell is None:
            start_cell = node["cell"]
        _need(node["cell"] == start_cell and
              node["cell"] == source["startCell"],
              "LOAD12_SEED_START_CELL_INCONSISTENT")
        layers.add(node["layer"])
        serials.add(node["serial"])
        nodes.append(node)
    _need(layers == {0, 1}, "LOAD12_SEED_LAYERS_INVALID")
    return {"snapshot": dict(value), "nodes": nodes,
            "startCell": start_cell}


def _validate_load12_start_face(source: Mapping[str, Any],
                                start_node: Mapping[str, Any],
                                prefix_rows: Sequence[Mapping[str, Any]]) -> None:
    source_fields = source["source"]
    required_face = 1 << start_node["layer"]
    if source_fields["startCopperFaceMask"] & required_face:
        return
    start_vias = [row for row in prefix_rows
                  if row["kind"] == "VIA" and
                  row["netId"] == source_fields["netId"] and
                  row["x"] == source_fields["startX"] and
                  row["y"] == source_fields["startY"]]
    _need(len(start_vias) == 1, "LOAD12_START_FACE_UNSUPPORTED")


def _validate_load12_prefix_inventory(value: Any,
                                      source: Mapping[str, Any]
                                      ) -> Dict[str, Any]:
    _exact_keys(value, ALLOCATION_DIAGNOSTIC_KEYS,
                "LOAD12_PREFIX_INVENTORY_KEYS_INVALID")
    stability = value["stability"]
    _exact_keys(stability, ALLOCATION_STABILITY_KEYS,
                "LOAD12_PREFIX_STABILITY_KEYS_INVALID")
    _need(type(stability["prefixGeometrySha256"]) is str and
          SHA256_RE.fullmatch(stability["prefixGeometrySha256"]) is not None and
          stability["liveBeforeVerified"] is True and
          stability["liveAfterVerified"] is True and
          stability["holeIdentitiesVerified"] is True and
          stability["lineageVerified"] is True,
          "LOAD12_PREFIX_STABILITY_INVALID")
    inventory = _validate_allocation_inventory(
        value["totals"], value["rows"], source["netOrder"],
        source["source"]["netPosition"], source["source"]["branchOrdinal"])
    _need(inventory["totalHoles"] <= source["grid"]["maximumVias"],
          "LOAD12_PREFIX_HOLE_CAP_INVALID")

    work = value["work"]
    _exact_keys(work, ALLOCATION_WORK_KEYS, "LOAD12_PREFIX_WORK_KEYS_INVALID")
    for field, minimum, maximum in (
            ("advanceCalls", 1, MAX_WORK_UNITS),
            ("workSteps", 1, MAX_TOTAL_WORK),
            ("rowsScanned", 0, MAX_VIAS),
            ("netComparisons", 0, MAX_VIAS * ALLOCATION_NET_COUNT),
            ("netTotalsWritten", 1, ALLOCATION_NET_COUNT),
            ("activeMillis", 0, MAX_ACTIVE_MS)):
        _need(_integer(work[field], minimum, maximum),
              "LOAD12_PREFIX_WORK_DOMAIN_INVALID")
    total_holes = inventory["totalHoles"]
    net_count = len(source["netOrder"])
    expected_comparisons = total_holes * net_count
    pair_checks = total_holes * (total_holes - 1) // 2
    minimum_work = total_holes + expected_comparisons + net_count + pair_checks
    _need(work["rowsScanned"] == total_holes and
          work["netComparisons"] == expected_comparisons and
          work["netTotalsWritten"] == net_count and
          work["workSteps"] >= minimum_work and
          work["workSteps"] <= work["advanceCalls"] * MAX_WORK_QUANTUM and
          work["workSteps"] <= MAX_TOTAL_WORK,
          "LOAD12_PREFIX_WORK_CONSERVATION_INVALID")
    return {"inventory": inventory, "work": dict(work),
            "stability": dict(stability)}


def _load12_expected_planar_runs(
        nodes: Sequence[Mapping[str, Any]]) -> List[Dict[str, Any]]:
    groups: List[Tuple[int, int]] = []
    first = 0
    for index in range(1, len(nodes)):
        if nodes[index]["layer"] != nodes[index - 1]["layer"]:
            groups.append((first, index - 1))
            first = index
    groups.append((first, len(nodes) - 1))

    runs: List[Dict[str, Any]] = []
    for first_index, last_index in groups:
        _need(last_index > first_index, "LOAD12_PATH_SINGLE_NODE_PLANAR_RUN")
        compact: List[Tuple[int, int]] = []
        for index in range(first_index, last_index + 1):
            node = nodes[index]
            compact.append((node["x"], node["y"]))
            while len(compact) >= 3:
                a, b, c = compact[-3:]
                same_direction = (
                    a[0] == b[0] == c[0] and
                    (b[1] - a[1]) * (c[1] - b[1]) > 0
                ) or (
                    a[1] == b[1] == c[1] and
                    (b[0] - a[0]) * (c[0] - b[0]) > 0
                )
                if not same_direction:
                    break
                compact.pop(-2)
        runs.append({
            "layer": nodes[first_index]["layer"],
            "firstNodeIndex": first_index,
            "lastNodeIndex": last_index,
            "xPoints": [point[0] for point in compact],
            "yPoints": [point[1] for point in compact],
        })
    return runs


def _load12_compact_trace_points(trace: Mapping[str, Any]
                                ) -> List[Tuple[int, int]]:
    points: List[Tuple[int, int]] = []
    for x, y in zip(trace["xPoints"], trace["yPoints"]):
        point = (x, y)
        if len(points) >= 2:
            before, middle = points[-2:]
            same_direction_collinear = (
                (before[0] == middle[0] == point[0] and
                 (middle[1] - before[1]) * (point[1] - middle[1]) > 0)
                or
                (before[1] == middle[1] == point[1] and
                 (middle[0] - before[0]) * (point[0] - middle[0]) > 0)
            )
            if same_direction_collinear:
                points[-1] = point
                continue
        points.append(point)
    return points


def _validate_load12_path_and_goal(path_value: Any, goal_value: Any,
                                   publisher_value: Any,
                                   source: Mapping[str, Any],
                                   seeds: Mapping[str, Any],
                                   prefix_rows: Sequence[Mapping[str, Any]]
                                   ) -> Dict[str, Any]:
    _exact_keys(path_value, LOAD12_PATH_KEYS, "LOAD12_PATH_KEYS_INVALID")
    path_nodes = path_value["nodes"]
    _need(type(path_nodes) is list and 2 <= len(path_nodes) <= 4096 and
          _integer(path_value["nodeCount"], len(path_nodes), len(path_nodes)),
          "LOAD12_PATH_NODE_COUNT_INVALID")
    grid = source["grid"]
    source_fields = source["source"]
    cells = source["cells"]
    seen_serials = set()
    normalized: List[Dict[str, Any]] = []
    for index, node in enumerate(path_nodes):
        _exact_keys(node, LOAD12_PATH_NODE_KEYS, "LOAD12_PATH_NODE_KEYS_INVALID")
        _need(_integer(node["index"], 0, len(path_nodes) - 1) and
              node["index"] == index and
              _integer(node["parentIndex"], -1, len(path_nodes) - 1) and
              node["parentIndex"] == (-1 if index == 0 else index - 1) and
              _integer(node["cell"], 0, cells - 1) and
              _integer(node["x"], -MAX_ABS_BOARD_COORDINATE,
                       MAX_ABS_BOARD_COORDINATE) and
              _integer(node["y"], -MAX_ABS_BOARD_COORDINATE,
                       MAX_ABS_BOARD_COORDINATE) and
              _integer(node["layer"], 0, 1) and
              _integer(node["direction"], 0, 4) and
              _integer(node["transitions"], 0, 4) and
              _integer(node["cost"], 0, MAX_SCALAR) and
              _integer(node["heuristic"], 0, MAX_SCALAR) and
              _integer(node["serial"], 0, MAX_SCALAR) and
              node["serial"] not in seen_serials and
              _integer(node["key"], 0, MAX_SCALAR),
              "LOAD12_PATH_NODE_DOMAIN_INVALID")
        dx, dy = node["x"] - grid["minX"], node["y"] - grid["minY"]
        _need(dx >= 0 and dy >= 0 and dx % 10 == 0 and dy % 10 == 0 and
              dx // 10 < grid["width"] and dy // 10 < grid["height"] and
              node["cell"] == (dy // 10) * grid["width"] + dx // 10,
              "LOAD12_PATH_NODE_CELL_INVALID")
        expected_key = (((node["transitions"] * 2 + node["layer"]) * 5 +
                         node["direction"]) * cells + node["cell"])
        _need(node["key"] == expected_key,
              "LOAD12_PATH_NODE_KEY_INVALID")
        seen_serials.add(node["serial"])
        normalized.append(node)

    first_node = normalized[0]
    last_node = normalized[-1]
    _need(first_node["direction"] == 4 and first_node["transitions"] == 0 and
          first_node["cell"] == seeds["startCell"] == source["startCell"] and
          (first_node["x"], first_node["y"]) ==
          (source_fields["startX"], source_fields["startY"]),
          "LOAD12_PATH_START_INVALID")
    seed_match = any(all(first_node[field] == seed[field] for field in (
        "cell", "x", "y", "layer", "direction", "transitions", "cost",
        "heuristic", "serial", "key")) for seed in seeds["nodes"])
    _need(seed_match, "LOAD12_PATH_START_NOT_CAPTURED_SEED")
    _validate_load12_start_face(source, first_node, prefix_rows)

    grid_steps = 0
    transitions = 0
    length = 0
    bends = 0
    last_direction = 4
    for before, after in zip(normalized, normalized[1:]):
        if before["layer"] == after["layer"]:
            dx = after["x"] - before["x"]
            dy = after["y"] - before["y"]
            direction = {(0, -10): 0, (10, 0): 1,
                         (0, 10): 2, (-10, 0): 3}.get((dx, dy))
            _need(direction is not None and
                  after["cell"] - before["cell"] in
                  (-grid["width"], -1, 1, grid["width"]) and
                  after["direction"] == direction and
                  after["transitions"] == before["transitions"],
                  "LOAD12_PATH_PLANAR_EDGE_INVALID")
            if last_direction != 4 and last_direction != direction:
                bends += 1
            last_direction = direction
            grid_steps += 1
            length += 10
        else:
            _need(after["cell"] == before["cell"] and
                  (after["x"], after["y"]) == (before["x"], before["y"]) and
                  after["layer"] == 1 - before["layer"] and
                  before["direction"] != 4 and after["direction"] == 4 and
                  after["transitions"] == before["transitions"] + 1 and
                  after["transitions"] <= grid["maximumTransitions"],
                  "LOAD12_PATH_FACE_FLIP_INVALID")
            transitions += 1
            last_direction = 4

    tuple_text = "\n".join(",".join(str(node[field]) for field in (
        "index", "parentIndex", "cell", "x", "y", "layer", "direction",
        "transitions", "cost", "heuristic", "serial", "key"))
                          for node in normalized)
    tuple_sha = _sha256(tuple_text.encode("utf-8", errors="strict"))
    direct_distance = (abs(source_fields["startX"] - last_node["x"]) +
                       abs(source_fields["startY"] - last_node["y"]))
    _need(_integer(path_value["gridSteps"], grid_steps, grid_steps) and
          _integer(path_value["length"], length, length) and
          _integer(path_value["bends"], bends, bends) and
          _integer(path_value["transitionCount"], transitions, transitions) and
          _integer(path_value["directDistance"], direct_distance, direct_distance) and
          direct_distance > 0 and
          type(path_value["tupleSha256"]) is str and
          path_value["tupleSha256"] == tuple_sha,
          "LOAD12_PATH_METRICS_OR_HASH_INVALID")

    expected_runs = _load12_expected_planar_runs(normalized)
    supplied_runs = path_value["planarRuns"]
    _need(type(supplied_runs) is list and len(supplied_runs) == len(expected_runs),
          "LOAD12_PLANAR_RUNS_INVALID")
    for expected, supplied in zip(expected_runs, supplied_runs):
        _exact_keys(supplied, LOAD12_PLANAR_RUN_KEYS,
                    "LOAD12_PLANAR_RUN_KEYS_INVALID")
        _need(_integer(supplied["layer"], 0, 1) and
              _integer(supplied["firstNodeIndex"], 0, len(normalized) - 1) and
              _integer(supplied["lastNodeIndex"], 0, len(normalized) - 1) and
              type(supplied["xPoints"]) is list and
              type(supplied["yPoints"]) is list and
              all(_integer(point, -MAX_ABS_BOARD_COORDINATE,
                           MAX_ABS_BOARD_COORDINATE)
                  for point in supplied["xPoints"] + supplied["yPoints"]) and
              supplied == expected,
              "LOAD12_PLANAR_RUN_MISMATCH")

    _exact_keys(goal_value, LOAD12_GOAL_KEYS, "LOAD12_GOAL_KEYS_INVALID")
    face_bits = goal_value["faceBits"]
    _need(type(face_bits) is str and len(face_bits) == cells and
          all(char in "0123" for char in face_bits),
          "LOAD12_GOAL_FACE_BITS_INVALID")
    top_count = sum(1 for char in face_bits if int(char) & 1)
    bottom_count = sum(1 for char in face_bits if int(char) & 2)
    both_count = face_bits.count("3")
    cell_count = sum(1 for char in face_bits if char != "0")
    _need(_integer(goal_value["cellCount"], cell_count, cell_count) and
          _integer(goal_value["topCount"], top_count, top_count) and
          _integer(goal_value["bottomCount"], bottom_count, bottom_count) and
          _integer(goal_value["bothCount"], both_count, both_count) and
          type(goal_value["faceBitsSha256"]) is str and
          goal_value["faceBitsSha256"] == _sha256(face_bits.encode("ascii")) and
          goal_value["foundGoal"] is True and
          int(face_bits[last_node["cell"]]) & (1 << last_node["layer"]),
          "LOAD12_GOAL_MASK_INVALID")

    _exact_keys(publisher_value, LOAD12_PUBLISHER_KEYS,
                "LOAD12_PUBLISHER_KEYS_INVALID")
    end_terminal = publisher_value["endTerminalPadId"]
    valid_end_terminal = (end_terminal is None or
                          end_terminal == source_fields["rootPadId"] or
                          end_terminal in source_fields["reachedPthPadIds"])
    root_terminal_matches_grid = (
        end_terminal != source_fields["rootPadId"] or
        (last_node["cell"] == source["rootCell"] and
         (last_node["x"], last_node["y"]) ==
         (source_fields["rootX"], source_fields["rootY"]))
    )
    _need(_integer(publisher_value["stage"], 1, 1) and
          _integer(publisher_value["qualityIndex"], len(normalized), len(normalized)) and
          _integer(publisher_value["nodeCount"], len(normalized), len(normalized)) and
          _integer(publisher_value["length"], length, length) and
          _integer(publisher_value["bends"], bends, bends) and
          _integer(publisher_value["directDistance"], direct_distance, direct_distance) and
          _integer(publisher_value["endCell"], last_node["cell"], last_node["cell"]) and
          _integer(publisher_value["endX"], last_node["x"], last_node["x"]) and
          _integer(publisher_value["endY"], last_node["y"], last_node["y"]) and
          _integer(publisher_value["endLayer"], last_node["layer"], last_node["layer"]) and
          _integer(publisher_value["endTransitions"], transitions, transitions) and
          root_terminal_matches_grid and
          valid_end_terminal,
          "LOAD12_NATIVE_PUBLISHER_BINDING_INVALID")
    _need(end_terminal is None or _valid_allocation_text(end_terminal),
          "LOAD12_NATIVE_PUBLISHER_TERMINAL_INVALID")

    return {"path": dict(path_value), "nodes": normalized,
            "goal": dict(goal_value), "publisher": dict(publisher_value),
            "gridSteps": grid_steps, "length": length, "bends": bends,
            "transitions": transitions, "directDistance": direct_distance,
            "tupleSha256": tuple_sha, "planarRuns": expected_runs,
            "endCell": last_node["cell"], "endLayer": last_node["layer"],
            "endTerminalPadId": end_terminal}


def _validate_load12_pre(value: Any) -> Dict[str, Any]:
    _exact_keys(value, LOAD12_PRE_KEYS, "LOAD12_PRE_KEYS_INVALID")
    _need(value["schema"] == "p07-accepted-branch-pre-r1",
          "LOAD12_PRE_SCHEMA_INVALID")
    source = _validate_load12_source(value["source"])
    seeds = _validate_load12_seed_snapshot(value["seeds"], source)
    prefix = _validate_load12_prefix_inventory(value["prefixInventory"], source)
    traces_value = value["prefixTraces"]
    _exact_keys(traces_value, LOAD12_PREFIX_TRACE_KEYS,
                "LOAD12_PREFIX_TRACES_KEYS_INVALID")
    traces = _validate_load12_trace_rows(
        traces_value["rows"], "LOAD12_PREFIX_TRACES")
    _need(_integer(traces_value["count"], len(traces), len(traces)),
          "LOAD12_PREFIX_TRACES_COUNT_INVALID")
    _need(all(trace["index"] == index for index, trace in enumerate(traces)),
          "LOAD12_PREFIX_TRACE_INDEX_INVALID")
    path = _validate_load12_path_and_goal(
        value["path"], value["goal"], value["nativePublisher"], source,
        seeds, prefix["inventory"]["rows"])

    work = value["work"]
    _exact_keys(work, LOAD12_PRE_WORK_KEYS, "LOAD12_PRE_WORK_KEYS_INVALID")
    for field, minimum, maximum in (
            ("advanceCalls", 1, MAX_WORK_UNITS),
            ("workSteps", 1, MAX_TOTAL_WORK),
            ("prefixWorkSteps", 0, MAX_TOTAL_WORK),
            ("seedEntries", 2, 2),
            ("pathNodes", 2, 4096),
            ("goalCells", 1, MAX_GRID_CELLS),
            ("traceRows", 0, 100_000),
            ("serializedUtf16Units", 0, MAX_TOTAL_WORK),
            ("activeMillis", 0, MAX_ACTIVE_MS)):
        _need(_integer(work[field], minimum, maximum),
              "LOAD12_PRE_WORK_DOMAIN_INVALID")
    serialized_units = _load12_serialized_json_utf16_units(
        value, LOAD12_PRE_FIXED_JSON_COUNTERS,
        frozenset((("prefixInventory",),)))
    _need(serialized_units <= MAX_TOTAL_WORK and
          work["serializedUtf16Units"] == serialized_units,
          "LOAD12_PRE_SERIALIZED_UTF16_UNITS_INVALID")
    minimum_work = (work["prefixWorkSteps"] + work["seedEntries"] +
                    work["pathNodes"] + work["goalCells"] + work["traceRows"] +
        work["serializedUtf16Units"])
    _need(work["prefixWorkSteps"] == prefix["work"]["workSteps"] and
          work["pathNodes"] == path["path"]["nodeCount"] and
          work["goalCells"] == source["cells"] and
          work["traceRows"] == len(traces) and
          work["workSteps"] >= minimum_work and
          work["workSteps"] <= work["advanceCalls"] * MAX_WORK_QUANTUM and
          work["workSteps"] <= MAX_TOTAL_WORK,
          "LOAD12_PRE_WORK_CONSERVATION_INVALID")

    stability = value["stability"]
    _exact_keys(stability, LOAD12_PRE_STABILITY_KEYS,
                "LOAD12_PRE_STABILITY_KEYS_INVALID")
    _need(all(stability[field] is True for field in LOAD12_PRE_STABILITY_KEYS),
          "LOAD12_PRE_STABILITY_INVALID")
    return {"pre": dict(value), "source": source, "seeds": seeds,
            "prefix": prefix, "prefixTraces": traces,
            "path": path, "work": dict(work), "stability": dict(stability)}


def _validate_load12_post(value: Any, pre: Mapping[str, Any]
                          ) -> Dict[str, Any]:
    _exact_keys(value, LOAD12_POST_KEYS, "LOAD12_POST_KEYS_INVALID")
    _need(value["schema"] == "p07-accepted-branch-post-r1",
          "LOAD12_POST_SCHEMA_INVALID")
    source_value = value["source"]
    _exact_keys(source_value, LOAD12_POST_SOURCE_KEYS,
                "LOAD12_POST_SOURCE_KEYS_INVALID")
    pre_source = pre["source"]["source"]
    prefix_rows = pre["prefix"]["inventory"]["rows"]
    prefix_traces = pre["prefixTraces"]
    _need(_integer(source_value["pass"], 1, 1) and
          source_value["netId"] == pre_source["netId"] == "LOAD12" and
          _integer(source_value["branchOrdinal"], 73, 73) and
          _integer(source_value["searchBranches"], 73, 73) and
          source_value["currentNet"] == pre_source["netId"] and
          source_value["searchBranchIsNull"] is True and
          source_value["searchPublisherIsNull"] is True and
          _integer(source_value["publisherStage"], 6, 6) and
          source_value["startPadId"] == pre_source["startPadId"] and
          _integer(source_value["reachedStartCount"], 1, 1) and
          _integer(source_value["preHoleCount"], len(prefix_rows), len(prefix_rows)) and
          _integer(source_value["preTraceCount"], len(prefix_traces), len(prefix_traces)),
          "LOAD12_POST_SOURCE_BINDING_INVALID")

    post_inventory_value = value["post"]
    _exact_keys(post_inventory_value, LOAD12_POST_INVENTORY_KEYS,
                "LOAD12_POST_INVENTORY_KEYS_INVALID")
    hole_count = post_inventory_value["holeCount"]
    _need(_integer(hole_count, 0, pre_source["grid"]["maximumVias"]),
          "LOAD12_POST_HOLE_COUNT_INVALID")
    inventory = _validate_allocation_inventory(
        None,
        post_inventory_value["holeRows"], pre["source"]["netOrder"],
        pre_source["netPosition"], pre_source["branchOrdinal"],
        expected_total=hole_count, allow_current_owner=True)
    # The helper validates the row identities and lineage above. Build and
    # validate totals against those rows without accepting a producer summary.
    post_rows = inventory["rows"]
    post_ids = [row["id"] for row in post_rows]
    try:
        _need(post_ids == sorted(post_ids, key=_java_string_sort_key),
              "LOAD12_POST_HOLE_ORDER_INVALID")
    except UnicodeEncodeError:
        _fail("LOAD12_POST_HOLE_ORDER_INVALID")

    pre_by_id = {row["id"]: row for row in prefix_rows}
    post_by_id = {row["id"]: row for row in post_rows}
    _need(set(pre_by_id) <= set(post_by_id), "LOAD12_RETAINED_HOLE_MISSING")
    created_ids = [hole_id for hole_id in post_ids if hole_id not in pre_by_id]
    supplied_created_ids = post_inventory_value["createdHoleIds"]
    _need(type(supplied_created_ids) is list and
          all(_valid_allocation_text(hole_id) for hole_id in supplied_created_ids) and
          len(set(supplied_created_ids)) == len(supplied_created_ids),
          "LOAD12_CREATED_HOLE_IDS_INVALID")
    try:
        _need(supplied_created_ids == created_ids and
              supplied_created_ids == sorted(supplied_created_ids,
                                             key=_java_string_sort_key),
              "LOAD12_CREATED_HOLE_DELTA_INVALID")
    except UnicodeEncodeError:
        _fail("LOAD12_CREATED_HOLE_IDS_INVALID")
    created_set = set(created_ids)
    _need(_integer(post_inventory_value["createdHoleCount"], len(created_ids),
                   len(created_ids)) and
          all(post_by_id[hole_id]["creatorBranchOrdinal"] == 73 and
              post_by_id[hole_id]["creatorNetPosition"] == pre_source["netPosition"] and
              post_by_id[hole_id]["netId"] == pre_source["netId"]
              for hole_id in created_ids),
          "LOAD12_CREATED_HOLE_OWNER_INVALID")

    retained_holes = post_inventory_value["retainedHoleBindings"]
    _need(type(retained_holes) is list and len(retained_holes) == len(prefix_rows),
          "LOAD12_RETAINED_HOLE_BINDINGS_INVALID")
    retained_ids: List[str] = []
    for binding in retained_holes:
        _exact_keys(binding, LOAD12_RETAINED_HOLE_KEYS,
                    "LOAD12_RETAINED_HOLE_BINDING_KEYS_INVALID")
        hole_id = binding["id"]
        valid_id = _valid_allocation_text(hole_id)
        before = pre_by_id.get(hole_id) if valid_id else None
        after = post_by_id.get(hole_id) if valid_id else None
        _need(valid_id and before is not None and after is not None and
              type(binding["preFingerprint"]) is str and
              binding["preFingerprint"] == before["fingerprint"] and
              binding["postFingerprint"] == after["fingerprint"] and
              _integer(binding["preIndex"], 0, max(0, len(prefix_rows) - 1)) and
              binding["preIndex"] == before["index"] and
              _integer(binding["postIndex"], 0, max(0, len(post_rows) - 1)) and
              binding["postIndex"] == after["index"] and
              binding["sameObject"] is True,
              "LOAD12_RETAINED_HOLE_BINDING_INVALID")
        retained_ids.append(hole_id)
    try:
        _need(retained_ids == sorted(pre_by_id, key=_java_string_sort_key) and
              len(set(retained_ids)) == len(retained_ids),
              "LOAD12_RETAINED_HOLE_BINDINGS_INVALID")
    except UnicodeEncodeError:
        _fail("LOAD12_RETAINED_HOLE_BINDINGS_INVALID")

    created_trace_rows = _validate_load12_trace_rows(
        post_inventory_value["createdTraceRows"], "LOAD12_CREATED_TRACES")
    _need(_integer(post_inventory_value["createdTraceCount"],
                   len(created_trace_rows), len(created_trace_rows)) and
          len(created_trace_rows) == len(pre["path"]["planarRuns"]),
          "LOAD12_CREATED_TRACE_COUNT_INVALID")
    post_trace_count = len(prefix_traces) + len(created_trace_rows)
    _need(post_trace_count <= 100_000,
          "LOAD12_POST_TRACE_COUNT_INVALID")

    retained_traces = post_inventory_value["retainedTraceBindings"]
    _need(type(retained_traces) is list and len(retained_traces) == len(prefix_traces),
          "LOAD12_RETAINED_TRACE_BINDINGS_INVALID")
    retained_pre_indexes = []
    retained_post_indexes = []
    prior_retained_post_index = -1
    for binding in retained_traces:
        _exact_keys(binding, LOAD12_RETAINED_TRACE_KEYS,
                    "LOAD12_RETAINED_TRACE_BINDING_KEYS_INVALID")
        pre_index = binding["preIndex"]
        before = (prefix_traces[pre_index]
                  if _integer(pre_index, 0, len(prefix_traces) - 1) else None)
        post_index = binding["postIndex"]
        _need(before is not None and
              binding["sourceId"] == before["sourceId"] and
              binding["preFingerprint"] == before["traceFingerprint"] and
              binding["postFingerprint"] == before["traceFingerprint"] and
              _integer(post_index, 0, max(0, post_trace_count - 1)) and
              post_index > prior_retained_post_index and
              binding["sameObject"] is True,
              "LOAD12_RETAINED_TRACE_BINDING_INVALID")
        retained_pre_indexes.append(pre_index)
        retained_post_indexes.append(post_index)
        prior_retained_post_index = post_index
    _need(sorted(retained_pre_indexes) == list(range(len(prefix_traces))),
          "LOAD12_RETAINED_TRACE_BINDINGS_INVALID")

    created_post_indexes = [trace["index"] for trace in created_trace_rows]
    _need(all(index < post_trace_count for index in created_post_indexes) and
          all(before < after for before, after in zip(
              created_post_indexes, created_post_indexes[1:])),
          "LOAD12_CREATED_TRACE_INDEX_ORDER_INVALID")
    all_post_indexes = sorted(retained_post_indexes + created_post_indexes)
    _need(all_post_indexes == list(range(post_trace_count)),
          "LOAD12_POST_TRACE_INDEX_PARTITION_INVALID")

    same_net_prefix_traces = [trace for trace in prefix_traces
                              if trace["netId"] == pre_source["netId"]]
    same_net_widths = {trace["width"] for trace in same_net_prefix_traces}
    _need(same_net_prefix_traces and len(same_net_widths) == 1 and
          all(trace["width"] in same_net_widths for trace in created_trace_rows),
          "LOAD12_CREATED_TRACE_WIDTH_NOT_BOUND_TO_PREFIX")
    runs = pre["path"]["planarRuns"]
    path_nodes = pre["path"]["nodes"]
    effective_end_pad = pre["path"]["endTerminalPadId"]
    if effective_end_pad is None and \
            (path_nodes[-1]["x"], path_nodes[-1]["y"]) == \
            (pre_source["rootX"], pre_source["rootY"]):
        effective_end_pad = pre_source["rootPadId"]
    expected_runs = {}
    for index, run in enumerate(runs):
        source_id = "p07/" + pre_source["netId"] + "/" + \
            str(pre_source["branchOrdinal"]) + "/" + str(index)
        expected_runs[source_id] = (index, run)
    matched_runs = set()
    for trace in created_trace_rows:
        matched = expected_runs.get(trace["sourceId"])
        _need(matched is not None,
              "LOAD12_CREATED_TRACE_PATH_BINDING_INVALID")
        run_index, run = matched
        run_is_last = run_index == len(runs) - 1
        expected_start_pad = pre_source["startPadId"] if run_index == 0 else None
        expected_end_pad = effective_end_pad if run_is_last else None
        expected_layer = "TOP" if run["layer"] == 0 else "BOTTOM"
        compacted_points = _load12_compact_trace_points(trace)
        expected_points = list(zip(run["xPoints"], run["yPoints"]))
        _need(run_index not in matched_runs and
              trace["netId"] == pre_source["netId"] and
              trace["startPadId"] == expected_start_pad and
              trace["endPadId"] == expected_end_pad and
              trace["layer"] == expected_layer and
              trace["exposure"] == "EXPOSED" and
              compacted_points == expected_points,
              "LOAD12_CREATED_TRACE_PATH_BINDING_INVALID")
        matched_runs.add(run_index)
    _need(matched_runs == set(range(len(runs))),
          "LOAD12_CREATED_TRACE_PATH_BINDING_INVALID")

    transition_nodes = []
    for index, (before, after) in enumerate(zip(path_nodes, path_nodes[1:]), 1):
        if before["layer"] != after["layer"]:
            transition_nodes.append((index, before, after))
    transition_bindings = post_inventory_value["transitionBindings"]
    _need(type(transition_bindings) is list and
          len(transition_bindings) == len(transition_nodes),
          "LOAD12_TRANSITION_BINDINGS_INVALID")
    for binding, (path_index, before, after) in zip(transition_bindings,
                                                    transition_nodes):
        _exact_keys(binding, LOAD12_TRANSITION_BINDING_KEYS,
                    "LOAD12_TRANSITION_BINDING_KEYS_INVALID")
        candidates = [row for row in post_rows
                      if row["kind"] == "VIA" and
                      row["netId"] == pre_source["netId"] and
                      row["x"] == after["x"] and row["y"] == after["y"]]
        via_id = binding["viaId"]
        valid_via_id = _valid_allocation_text(via_id)
        via = post_by_id.get(via_id) if valid_via_id else None
        _need(len(candidates) == 1 and via is candidates[0] and
              _integer(binding["pathNodeIndex"], path_index, path_index) and
              _integer(binding["fromLayer"], before["layer"], before["layer"]) and
              _integer(binding["toLayer"], after["layer"], after["layer"]) and
              _integer(binding["cell"], after["cell"], after["cell"]) and
              _integer(binding["x"], after["x"], after["x"]) and
              _integer(binding["y"], after["y"], after["y"]) and
              binding["netId"] == pre_source["netId"] and
              valid_via_id and
              binding["viaFingerprint"] == via["fingerprint"] and
              binding["creatorNetPosition"] == via["creatorNetPosition"] and
              binding["creatorBranchOrdinal"] == via["creatorBranchOrdinal"] and
              binding["piece"] == via["piece"] and
              _integer(binding["creatorNetPosition"], 0,
                       len(pre["source"]["netOrder"]) - 1) and
              _integer(binding["creatorBranchOrdinal"], 1, 73) and
              _integer(binding["piece"], 0, MAX_SCALAR) and
              type(binding["createdInBranch"]) is bool and
              binding["createdInBranch"] is (via_id in created_set) and
              ((via_id in created_set and via["creatorBranchOrdinal"] == 73) or
               (via_id not in created_set and via_id in pre_by_id)),
              "LOAD12_TRANSITION_BINDING_INVALID")

    claims = _validate_load12_claims(value["claims"], "LOAD12_POST_CLAIMS")
    stability = value["stability"]
    _exact_keys(stability, LOAD12_POST_STABILITY_KEYS,
                "LOAD12_POST_STABILITY_KEYS_INVALID")
    _need(all(stability[field] is True for field in LOAD12_POST_STABILITY_KEYS),
          "LOAD12_POST_STABILITY_INVALID")
    work = value["work"]
    _exact_keys(work, LOAD12_POST_WORK_KEYS, "LOAD12_POST_WORK_KEYS_INVALID")
    for field, minimum, maximum in (
            ("advanceCalls", 1, MAX_WORK_UNITS),
            ("workSteps", 1, MAX_TOTAL_WORK),
            ("aggregateWorkSteps", 1, MAX_TOTAL_WORK),
            ("holeRows", 0, MAX_VIAS),
            ("traceRows", 0, 100_000),
            ("transitionMatches", 0, 4096),
            ("serializedUtf16Units", 0, MAX_TOTAL_WORK),
            ("activeMillis", 0, MAX_ACTIVE_MS)):
        _need(_integer(work[field], minimum, maximum),
              "LOAD12_POST_WORK_DOMAIN_INVALID")
    serialized_units = _load12_serialized_json_utf16_units(
        value, LOAD12_POST_FIXED_JSON_COUNTERS)
    _need(serialized_units <= MAX_TOTAL_WORK and
          work["serializedUtf16Units"] == serialized_units,
          "LOAD12_POST_SERIALIZED_UTF16_UNITS_INVALID")
    aggregate_steps = pre["work"]["workSteps"] + work["workSteps"]
    aggregate_calls = pre["work"]["advanceCalls"] + work["advanceCalls"]
    aggregate_active = pre["work"]["activeMillis"] + work["activeMillis"]
    minimum_post_work = (work["holeRows"] + work["traceRows"] +
                         work["transitionMatches"] + work["serializedUtf16Units"])
    _need(work["holeRows"] == len(post_rows) and
          work["traceRows"] == len(prefix_traces) + len(created_trace_rows) and
          work["transitionMatches"] == len(transition_nodes) and
          work["workSteps"] >= minimum_post_work and
          work["workSteps"] <= work["advanceCalls"] * MAX_WORK_QUANTUM and
          work["aggregateWorkSteps"] == aggregate_steps and
          aggregate_steps <= MAX_TOTAL_WORK and aggregate_calls <= MAX_WORK_UNITS and
          aggregate_active <= MAX_ACTIVE_MS,
          "LOAD12_POST_WORK_CONSERVATION_INVALID")
    return {"post": dict(value), "inventoryObject": dict(post_inventory_value),
            "source": dict(source_value),
            "inventory": inventory, "rows": post_rows,
            "createdHoleIds": created_ids,
            "createdTraceRows": created_trace_rows,
            "transitionNodes": transition_nodes,
            "work": dict(work), "stability": dict(stability),
            "claims": claims}


def _validate_load12_header_and_pre(value: Any,
                                   scalar_event: Mapping[str, Any]
                                   ) -> Dict[str, Any]:
    _exact_keys(value, LOAD12_EVENT_KEYS, "LOAD12_EVENT_KEYS_INVALID")
    _need(_integer(value["protocolVersion"], 1, 1) and
          _integer(value["sourceRevision"], SOURCE_REVISION, SOURCE_REVISION) and
          type(value["runId"]) is str and UUID_RE.fullmatch(value["runId"]) is not None and
          value["runId"] == scalar_event["runId"] and
          value["operationId"] == PROBE_OPERATION_ID and
          value["scope"] == "ACTUAL_LOAD12_BRANCH_ENTRY_PATH_AND_PUBLICATION_ONLY" and
          value["rootSeed"] == ROOT_SEED and
          value["placementSeed"] == PLACEMENT_SEED and
          value["routingSeed"] == ROUTING_SEED and
          _integer(value["routeRequestIndex"], 0, 0) and
          _integer(value["selectedPlacementIndex"], 0, 0) and
          _integer(value["selectedPlacementAttempt"], 4, 4) and
          value["rootSeed"] == scalar_event["rootSeed"] and
          value["placementSeed"] == scalar_event["placementSeed"] and
          value["routingSeed"] == scalar_event["routingSeed"] and
          value["routeRequestIndex"] == scalar_event["routeRequestIndex"] and
          value["selectedPlacementIndex"] == scalar_event["selectedPlacementIndex"] and
          value["selectedPlacementAttempt"] ==
          scalar_event["selectedPlacementAttempt"] and
          value["status"] == "PASS",
          "LOAD12_EVENT_IDENTITY_OR_SCOPE_INVALID")
    claims = _validate_load12_claims(value["claims"], "LOAD12_EVENT_CLAIMS")
    pre = _validate_load12_pre(value["pre"])
    _need(pre["pre"]["source"]["pass"] == scalar_event["pass"] and
          pre["pre"]["source"]["netId"] == "LOAD12",
          "LOAD12_PRE_SCALAR_SOURCE_MISMATCH")
    return {"event": dict(value), "claims": claims, "pre": pre}


def _validate_load12_branch_event(value: Any,
                                  scalar_event: Mapping[str, Any],
                                  allocation: Mapping[str, Any]
                                  ) -> Dict[str, Any]:
    header = _validate_load12_header_and_pre(value, scalar_event)
    claims = header["claims"]
    pre = header["pre"]
    post = _validate_load12_post(value["post"], pre)
    _need(post["claims"] == claims,
          "LOAD12_EVENT_POST_CLAIMS_MISMATCH")

    # Later D_SENSE inventory is corroboration of stable source IDs and raw
    # fingerprints only. Its later row indexes and graph are not backprojected.
    later_binding = allocation["binding"]
    later_rows = allocation["diagnostic"]["rows"]
    later_by_id = {row["id"]: row for row in later_rows}
    all_load12_rows = (pre["prefix"]["inventory"]["rows"] + post["rows"])
    for row in all_load12_rows:
        later = later_by_id.get(row["id"])
        _need(later is not None and later["fingerprint"] == row["fingerprint"],
              "LOAD12_LATER_ALLOCATION_CORROBORATION_INVALID")
    _need(later_binding["netOrder"] == pre["source"]["netOrder"],
          "LOAD12_LATER_ALLOCATION_NET_ORDER_MISMATCH")
    later_branch_rows = [row for row in later_rows
                         if row["creatorBranchOrdinal"] == 73 and
                         row["netId"] == "LOAD12"]
    later_branch_ids = {row["id"] for row in later_branch_rows}
    created_transition_ids = {
        binding["viaId"]
        for binding in post["inventoryObject"]["transitionBindings"]
        if binding["createdInBranch"] is True
    }
    _need(later_branch_ids == set(post["createdHoleIds"]) and
          created_transition_ids == set(post["createdHoleIds"]),
          "LOAD12_LATER_BRANCH_DELTA_MISMATCH")

    return {"event": dict(value), "claims": claims,
            "pre": pre, "post": post,
            "scope": value["scope"], "runId": value["runId"]}


def _validate_load12_branch_summary(value: Any, event: Mapping[str, Any],
                                    load12: Mapping[str, Any]
                                    ) -> Dict[str, Any]:
    _exact_keys(value, LOAD12_SUMMARY_KEYS,
                "LOAD12_SUMMARY_KEYS_INVALID")
    source_event = load12["event"]
    pre = load12["pre"]
    post = load12["post"]
    _need(_integer(value["protocolVersion"], 1, 1) and
          _integer(value["sourceRevision"], SOURCE_REVISION, SOURCE_REVISION) and
          value["status"] == "PASS" and
          value["runId"] == source_event["runId"] == event["runId"] and
          value["operationId"] == PROBE_OPERATION_ID and
          _integer(value["events"], 1, 1) and
          value["scope"] == source_event["scope"] and
          value["rootSeed"] == source_event["rootSeed"] and
          value["placementSeed"] == source_event["placementSeed"] and
          value["routingSeed"] == source_event["routingSeed"] and
          _integer(value["routeRequestIndex"],
                   source_event["routeRequestIndex"],
                   source_event["routeRequestIndex"]) and
          _integer(value["selectedPlacementIndex"],
                   source_event["selectedPlacementIndex"],
                   source_event["selectedPlacementIndex"]) and
          _integer(value["selectedPlacementAttempt"],
                   source_event["selectedPlacementAttempt"],
                   source_event["selectedPlacementAttempt"]) and
          _integer(value["pathNodes"], pre["path"]["path"]["nodeCount"],
                   pre["path"]["path"]["nodeCount"]) and
          _integer(value["transitions"], pre["path"]["transitions"],
                   pre["path"]["transitions"]) and
          _integer(value["createdHoles"], len(post["createdHoleIds"]),
                   len(post["createdHoleIds"])),
          "LOAD12_SUMMARY_IDENTITY_OR_SCOPE_INVALID")
    claims = _validate_load12_claims(value["claims"], "LOAD12_SUMMARY_CLAIMS")
    _need(claims == load12["claims"], "LOAD12_SUMMARY_CLAIMS_MISMATCH")
    return dict(value)


def _validate_receipt_lines(lines: Sequence[str], ledger: types.ModuleType,
                            frozen: types.ModuleType, scalar: types.ModuleType,
                            target: str = "D_SENSE"
                            ) -> Dict[str, Any]:
    _need(type(lines) in (list, tuple) and len(lines) == len(RECEIPT_MARKERS) and
          all(type(line) is str and line for line in lines),
          "GRAPH_RECEIPT_EVENT_COUNT_INVALID")
    records = [_parse_tagged(line, marker, ledger)
               for line, marker in zip(lines, RECEIPT_MARKERS)]
    old = _scalar_call(scalar, scalar._validate_receipt_lines,
                       "SCALAR_RECEIPT_INVALID", lines[:8], ledger)
    current_context = _path_call(
        frozen, frozen._validate_current_authored_context,
        "QUALITY_CONTEXT_INVALID", old)
    path = _path_call(frozen, frozen._validate_path_event,
                      "QUALITY_PATH_EVENT_INVALID", records[8], old["event"], scalar)
    path_summary = _path_call(frozen, frozen._validate_path_summary,
                               "QUALITY_PATH_SUMMARY_INVALID", records[9], records[8], scalar)
    _need(target in SUITES, "GRAPH_TARGET_SPEC_INVALID")
    # Validate the immutable accepted LOAD12 pre-boundary before selecting it as
    # a graph source. The complete branch validator later binds post and the
    # shared D_SENSE inventory without backprojecting either into this prefix.
    load12_header = _validate_load12_header_and_pre(records[14], old["event"])
    load12_pre = load12_header["pre"]
    graph = _validate_graph_event(
        records[10], old["event"], path, target, load12_pre,
        records[12].get("binding"))
    graph_summary = _validate_graph_summary(records[11], records[10], graph)
    allocation = _validate_prefix_allocation_event(
        records[12], old["event"], graph, target)
    allocation_summary = _validate_prefix_allocation_summary(
        records[13], records[12], allocation)
    load12 = _validate_load12_branch_event(records[14], old["event"], allocation)
    load12_summary = _validate_load12_branch_summary(
        records[15], records[14], load12)
    return {"scalar": old, "currentContext": current_context,
            "path": path, "pathSummary": path_summary,
            "graph": graph, "graphSummary": graph_summary,
            "allocation": allocation,
            "allocationSummary": allocation_summary,
            "load12Branch": load12,
            "load12BranchSummary": load12_summary,
            "target": target, "suite": SUITES[target]}


def _validate_console_and_receipt(stdout_raw: bytes, receipt_raw: bytes,
                                  stderr_raw: bytes, ledger: types.ModuleType
                                  ) -> Tuple[List[str], List[str]]:
    console = _ledger_call(ledger, ledger._decode_lines,
                           "RUN_STDOUT_DECODE_INVALID", stdout_raw, "RUN_STDOUT")
    receipt = _ledger_call(ledger, ledger._decode_lines,
                           "RUN_RECEIPT_DECODE_INVALID", receipt_raw, "RUN_RECEIPT")
    stderr = _ledger_call(ledger, ledger._decode_lines,
                          "RUN_STDERR_DECODE_INVALID", stderr_raw, "RUN_STDERR")
    _need(len(receipt) == len(RECEIPT_MARKERS) and all(receipt),
          "RUN_RECEIPT_EVENT_COUNT_INVALID")
    _need(all(line.startswith(marker + " ")
              for line, marker in zip(receipt, RECEIPT_MARKERS)),
          "RUN_RECEIPT_MARKER_ORDER_INVALID")
    console_events = [line for line in console if line.startswith(EVENT_PREFIXES)]
    stderr_events = [line for line in stderr if line.startswith(EVENT_PREFIXES)]
    _need(console_events == receipt and not stderr_events,
          "RUN_CONSOLE_RECEIPT_MISMATCH")
    _need(console.count(FOCUSED_PASS) == 1 and console.count(CLEANUP_LINE) == 1 and
          console.index(FOCUSED_PASS) < console.index(CLEANUP_LINE),
          "RUN_CONSOLE_PASS_OR_CLEANUP_INVALID")
    return console, receipt


def _stable_helpers(own_sha: str, frozen_sha: str,
                    helper_hashes: Mapping[str, str], frozen: types.ModuleType,
                    scalar: types.ModuleType) -> None:
    own_path = Path(os.path.abspath(__file__))
    own_raw = _read_local(own_path, 2 * 1024 * 1024, "READER_SOURCE")
    _need(_sha256(own_raw) == own_sha, "READER_SOURCE_CHANGED_DURING_READ")
    _path_call(frozen, frozen._stable_reader_check,
               "FROZEN_HELPER_REVALIDATION_FAILED", frozen_sha,
               helper_hashes, scalar)
    minimum_raw = _read_local(own_path.parent / "fixed_prefix_minimum_vias.py",
                              2 * 1024 * 1024, "MINIMUM_VIA_HELPER")
    _need(_sha256(minimum_raw) == helper_hashes["minimumViaHelperSha256"],
          "MINIMUM_VIA_HELPER_CHANGED_DURING_READ")


def _run_validation(spec_argument: str, spec_sha256: str) -> Dict[str, Any]:
    own_path = Path(os.path.abspath(__file__))
    own_raw = _read_local(own_path, 2 * 1024 * 1024, "READER_SOURCE")
    own_sha = _sha256(own_raw)
    frozen, scalar, ledger, capture, helper_hashes = _load_pinned_helpers()
    frozen_sha = helper_hashes["frozenQualityPathReaderSha256"]
    _need(type(spec_sha256) is str and SHA256_RE.fullmatch(spec_sha256) is not None,
          "CLI_ARGUMENTS_INVALID")
    spec_path = _ledger_call(ledger, ledger._resolve_path,
                             "INPUT_SPEC_INVALID", spec_argument,
                             Path.cwd(), "INPUT_SPEC")
    spec_raw = _ledger_call(ledger, ledger._read_bounded,
                            "INPUT_SPEC_INVALID", spec_path,
                            MAX_SPEC_BYTES, "INPUT_SPEC")
    _need(_sha256(spec_raw) == spec_sha256, "INPUT_SPEC_SHA256_MISMATCH")
    spec = _ledger_call(ledger, ledger._strict_json,
                        "INPUT_SPEC_INVALID", spec_raw, "INPUT_SPEC")
    target, suite, legacy_spec = _validate_target_spec(spec)
    # The byte-frozen prefix ledger has a closed legacy key set. Target/suite
    # are validated above and remain bound to the raw spec hash; pass its exact
    # legacy projection to the pinned source-census validator.
    frozen.SUITE = suite
    scalar.SUITE = suite
    context = _ledger_call(ledger, ledger._validate_input_spec,
                           "INPUT_SPEC_INVALID", legacy_spec,
                           spec_path.parent, capture)
    _need(QUALITY_REQUIRED_INPUTS <= set(context["pinMap"]),
          "QUALITY_SOURCE_PINS_OMIT_REQUIRED_INPUT")

    artifacts = context["artifacts"]
    result_raw = _ledger_call(ledger, ledger._read_bounded,
                              "NATIVE_RUN_READ_FAILED", artifacts["resultPath"],
                              MAX_RESULT_BYTES, "NATIVE_RUN")
    stdout_raw = _ledger_call(ledger, ledger._read_bounded,
                              "RUN_STDOUT_READ_FAILED", artifacts["stdoutPath"],
                              MAX_CAPTURE_BYTES, "RUN_STDOUT")
    receipt_raw = _ledger_call(ledger, ledger._read_bounded,
                               "RUN_RECEIPT_READ_FAILED", artifacts["receiptPath"],
                               MAX_CAPTURE_BYTES, "RUN_RECEIPT")
    stderr_raw = _ledger_call(ledger, ledger._read_bounded,
                              "RUN_STDERR_READ_FAILED", artifacts["stderrPath"],
                              MAX_CAPTURE_BYTES, "RUN_STDERR", allow_empty=True)
    run_result = _ledger_call(ledger, ledger._strict_json,
                              "NATIVE_RUN_JSON_INVALID", result_raw, "NATIVE_RUN")
    legacy_run_result = _validate_native_run_target(
        run_result, target, scalar.NATIVE_RUN_KEYS)
    _path_call(frozen, frozen._validate_native_run, "NATIVE_RUN_INVALID",
               legacy_run_result, context,
               stdout_raw, receipt_raw, stderr_raw, scalar)
    _console, receipt_lines = _validate_console_and_receipt(
        stdout_raw, receipt_raw, stderr_raw, ledger)
    parsed = _validate_receipt_lines(receipt_lines, ledger, frozen, scalar, target)
    _scalar_call(scalar, scalar._stable_source_check, "SOURCE_REVALIDATION_FAILED",
                 spec_raw, spec_path, spec_sha256, legacy_spec, context, ledger, capture)
    _stable_helpers(own_sha, frozen_sha, helper_hashes, frozen, scalar)

    # Re-read all native captures after validation so the report is tied to the
    # same artifact bytes whose complete native receipt and hashes were checked.
    for name, raw, limit, allow_empty in (
            ("resultPath", result_raw, MAX_RESULT_BYTES, False),
            ("stdoutPath", stdout_raw, MAX_CAPTURE_BYTES, False),
            ("receiptPath", receipt_raw, MAX_CAPTURE_BYTES, False),
            ("stderrPath", stderr_raw, MAX_CAPTURE_BYTES, True)):
        again = _ledger_call(ledger, ledger._read_bounded,
                             "NATIVE_ARTIFACT_REVALIDATION_FAILED",
                             artifacts[name], limit, name.upper(),
                             allow_empty=allow_empty)
        _need(again == raw, "NATIVE_ARTIFACT_CHANGED_DURING_READ")

    required_hashes = {
        relative: context["pinMap"][relative]["sha256"]
        for relative in sorted(QUALITY_REQUIRED_INPUTS)
    }
    scalar_event = parsed["scalar"]["event"]
    path = parsed["path"]
    graph = parsed["graph"]
    work = graph["diagnostic"]["work"]
    allocation = parsed["allocation"]
    allocation_diagnostic = allocation["diagnostic"]
    load12 = parsed["load12Branch"]
    load12_pre = load12["pre"]
    load12_post = load12["post"]
    load12_source = load12_pre["source"]["source"]
    load12_path = load12_pre["path"]
    load12_prefix = load12_pre["prefix"]
    load12_post_object = load12_post["inventoryObject"]
    load12_post_rows = load12_post["rows"]
    load12_net_order = load12_pre["source"]["netOrder"]
    load12_prefix_net_counts = load12_prefix["inventory"]["netCounts"]
    load12_post_net_counts = load12_post["inventory"]["netCounts"]
    load12_transition_created_ids = sorted(
        {binding["viaId"]
         for binding in load12_post_object["transitionBindings"]
         if binding["createdInBranch"] is True}, key=_java_string_sort_key)
    return {
        "schema": RESULT_SCHEMA,
        "status": "PASS",
        "target": target,
        "suite": suite,
        "sourceSnapshotRevision": SOURCE_REVISION,
        "source": {
            "inputCount": context["inputCount"],
            "inputBytes": context["inputBytes"],
            "pinsSha256": context["pinsSha256"],
            "requiredNativeInputSha256": required_hashes,
            "readerSha256": own_sha,
            "frozenQualityPathReaderSha256": frozen_sha,
            "frozenScalarReaderSha256": helper_hashes["frozenScalarReaderSha256"],
            "prefixLedgerSha256": helper_hashes["prefixLedgerSha256"],
            "captureHelperSha256": helper_hashes["captureHelperSha256"],
            "graphOracleSha256": helper_hashes["graphOracleSha256"],
            "minimumViaHelperSha256": helper_hashes["minimumViaHelperSha256"],
        },
        "artifacts": {
            "inputSpecSha256": _sha256(spec_raw),
            "nativeRunSha256": _sha256(result_raw),
            "stdoutBytes": len(stdout_raw), "stdoutSha256": _sha256(stdout_raw),
            "receiptBytes": len(receipt_raw), "receiptSha256": _sha256(receipt_raw),
            "stderrBytes": len(stderr_raw), "stderrSha256": _sha256(stderr_raw),
        },
        "nativeRun": {
            "target": run_result["target"],
            "suite": run_result["suite"],
            "processId": run_result["processId"],
            "processStartTicks": run_result["processStartTicks"],
            "elapsedSeconds": run_result["elapsedSeconds"],
            "nativeLimitMs": run_result["nativeLimitMs"],
            "outerLimitMs": run_result["outerLimitMs"],
            "cleanupStatus": run_result["cleanupStatus"],
            "inputAudit": run_result["inputAudit"],
        },
        "qualityObservation": {
            "runId": parsed["scalar"]["runId"], "variant": VARIANT,
            "routeRequestIndex": scalar_event["routeRequestIndex"],
            "selectedPlacementIndex": scalar_event["selectedPlacementIndex"],
            "selectedPlacementAttempt": scalar_event["selectedPlacementAttempt"],
            "pass": scalar_event["pass"], "netId": scalar_event["netId"],
            **parsed["currentContext"],
            "pathNodeCount": scalar_event["pathNodeCount"],
            "transitionCount": scalar_event["transitionCount"],
            "planarLength": scalar_event["planarLength"],
            "directDistance": scalar_event["directDistance"],
            "bends": scalar_event["bends"],
            "qualityRejected": scalar_event["qualityRejected"],
            "feasibility": "UNKNOWN", "layoutQualification": False,
        },
        "qualityPathObservation": {
            "pathNodeCount": path["pathNodeCount"],
            "planarLength": path["planarLength"],
            "directDistance": path["directDistance"],
            "bends": path["bends"],
            "transitionCount": path["transitionCount"],
            "repeatedSameFaceCells": path["repeatedSameFaceCells"],
            "repeatedSameFaceEdges": path["repeatedSameFaceEdges"],
            "scope": "ACTUAL_PUBLISHER_PATH_ONLY",
            "feasibility": "UNKNOWN", "legalShortcutProof": False,
            "layoutQualification": False,
        },
        "qualityZeroViaGraphObservation": {
            "target": graph["target"],
            "scope": graph["scope"],
            "binding": graph["binding"],
            "stability": graph["diagnostic"]["stability"],
            "grid": graph["diagnostic"]["grid"],
            "work": work,
            "analysis": graph["analysis"],
            "feasibility": "UNKNOWN", "legalRouteProof": False,
            "layoutQualification": False, "routeAdoption": False,
        },
        "prefixAllocationObservation": {
            "scope": allocation["scope"],
            "binding": allocation["binding"],
            "stability": allocation_diagnostic["stability"],
            "work": allocation_diagnostic["work"],
            "totals": allocation_diagnostic["totals"],
            "rows": allocation_diagnostic["rows"],
            "chronologyProof": False, "routeAdoption": False,
            "layoutQualification": False,
        },
        "load12BranchObservation": {
            "scope": load12["scope"],
            "runId": load12["runId"],
            "operationId": PROBE_OPERATION_ID,
            "approvedRequest": {
                "rootSeed": load12["event"]["rootSeed"],
                "placementSeed": load12["event"]["placementSeed"],
                "routingSeed": load12["event"]["routingSeed"],
                "routeRequestIndex": load12["event"]["routeRequestIndex"],
                "selectedPlacementIndex":
                    load12["event"]["selectedPlacementIndex"],
                "selectedPlacementAttempt":
                    load12["event"]["selectedPlacementAttempt"],
            },
            "source": {
                "pass": load12_source["pass"],
                "netId": load12_source["netId"],
                "netPosition": load12_source["netPosition"],
                "netOrderCount": len(load12_source["netOrder"]),
                "branchOrdinal": load12_source["branchOrdinal"],
                "policy": load12_source["policy"],
                "templateHoleCount": load12_source["templateHoleCount"],
                "startPadAttachment": load12_source["startPadAttachment"],
                "startCopperFaceMask": load12_source["startCopperFaceMask"],
                "rootCopperFaceMask": load12_source["rootCopperFaceMask"],
                "startPadId": load12_source["startPadId"],
                "rootPadId": load12_source["rootPadId"],
                "startX": load12_source["startX"],
                "startY": load12_source["startY"],
                "rootX": load12_source["rootX"],
                "rootY": load12_source["rootY"],
                "grid": load12_source["grid"],
            },
            "seeds": {
                "queueSize": load12_pre["seeds"]["snapshot"]["queueSize"],
                "faceMask": load12_pre["seeds"]["snapshot"]["faceMask"],
                "serials": [node["serial"]
                            for node in load12_pre["seeds"]["nodes"]],
            },
            "prefixInventory": {
                "holeCount": load12_prefix["inventory"]["totalHoles"],
                "byKind": load12_pre["pre"]["prefixInventory"]["totals"]["byKind"],
                "netTotalCount": len(load12_net_order),
                "netTotalsSha256": _allocation_net_totals_digest(
                    load12_net_order, load12_prefix_net_counts),
                "stability": load12_prefix["stability"],
                "work": load12_prefix["work"],
            },
            "prefixTraceCount": len(load12_pre["prefixTraces"]),
            "path": {
                "nodeCount": load12_path["path"]["nodeCount"],
                "gridSteps": load12_path["gridSteps"],
                "length": load12_path["length"],
                "bends": load12_path["bends"],
                "transitionCount": load12_path["transitions"],
                "directDistance": load12_path["directDistance"],
                "tupleSha256": load12_path["tupleSha256"],
                "planarRunCount": len(load12_path["planarRuns"]),
            },
            "goal": {
                "cellCount": load12_path["goal"]["cellCount"],
                "topCount": load12_path["goal"]["topCount"],
                "bottomCount": load12_path["goal"]["bottomCount"],
                "bothCount": load12_path["goal"]["bothCount"],
                "faceBitsSha256": load12_path["goal"]["faceBitsSha256"],
                "foundGoal": True,
            },
            "publisher": {
                "stage": load12_path["publisher"]["stage"],
                "qualityIndex": load12_path["publisher"]["qualityIndex"],
                "nodeCount": load12_path["publisher"]["nodeCount"],
                "endCell": load12_path["publisher"]["endCell"],
                "endLayer": load12_path["publisher"]["endLayer"],
                "endTransitions": load12_path["publisher"]["endTransitions"],
                "endTerminalPadId":
                    load12_path["publisher"]["endTerminalPadId"],
            },
            "post": {
                "holeCount": len(load12_post_rows),
                "createdHoleCount": len(load12_post["createdHoleIds"]),
                "createdHoleIdsSha256": _java_string_list_digest(
                    load12_post["createdHoleIds"]),
                "retainedHoleCount":
                    len(load12_post_object["retainedHoleBindings"]),
                "netTotalCount": len(load12_net_order),
                "netTotalsSha256": _allocation_net_totals_digest(
                    load12_net_order, load12_post_net_counts),
                "createdTraceCount": len(load12_post["createdTraceRows"]),
                "createdTraceFingerprintsSha256": _java_string_list_digest(
                    [trace["traceFingerprint"]
                     for trace in load12_post["createdTraceRows"]]),
                "retainedTraceCount":
                    len(load12_post_object["retainedTraceBindings"]),
                "transitionCount": len(load12_post["transitionNodes"]),
                "createdTransitionCount": len(load12_transition_created_ids),
                "createdTransitionViaIdsSha256": _java_string_list_digest(
                    load12_transition_created_ids),
            },
            "work": {"pre": load12_pre["work"],
                     "post": load12_post["work"]},
            "stability": {"pre": load12_pre["stability"],
                          "post": load12_post["stability"]},
            "claims": load12["claims"],
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


def run_validation(spec_argument: str, spec_sha256: str) -> Dict[str, Any]:
    try:
        return _run_validation(spec_argument, spec_sha256)
    except QualityZeroViaInputError:
        raise
    except Exception:
        # Do not leak implementation exceptions, source contents or paths.
        _fail("INPUT_INVALID")


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
        code = _safe_foreign_code(error, "INPUT_INVALID")
        try:
            sys.stdout.write("FAIL: " + code + "\n")
            sys.stdout.flush()
        except Exception:
            pass
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
