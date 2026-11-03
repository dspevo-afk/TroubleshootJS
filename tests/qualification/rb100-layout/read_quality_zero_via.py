#!/usr/bin/env python3
"""Source-bound reader for the bounded RB100 prefix allocation and graph.

The graph is a directed, two-face relaxation of one captured failed branch.
The allocation report records creation ownership for the captured prefix; it
does not infer later use, chronological history, route acceptance, or playable
qualification.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import sys
import types
import uuid
from collections import deque
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple


INPUT_SCHEMA = "p07-quality-zero-via-input-v2"
RUN_SCHEMA = "q100-quality-zero-via-native-run-v2"
RESULT_SCHEMA = "q100-quality-zero-via-reader-result-v2"
SUITE = "Rb100QualityZeroViaGraphContractTest"
ENTRY_SCRIPT = "scripts/verify-current-contracts.ps1"
SOURCE_REVISION = 17
SOURCE_INPUT_COUNT = 1074
CONTRACT_JAVA_COUNT = 218
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
    "tests/contracts/Rb100PhysicalDiagnostics.java",
    "tests/contracts/P07ZeroViaGraphDiagnostics.java",
    "tests/contracts/P07ZeroViaGraphContractTest.java",
    "tests/contracts/P07ShortcutQueryDiagnostics.java",
    "tests/contracts/P07ShortcutQueryContractTest.java",
    "tests/contracts/P07PrefixAllocationDiagnostics.java",
    "tests/contracts/P07PrefixAllocationContractTest.java",
    "scripts/verify-current-contracts.ps1",
    "tests/contracts/current-contract-runner-contract.ps1",
})

GRAPH_EVENT_MARKER = "RB100_ZERO_VIA_GRAPH_EVENT"
GRAPH_SUMMARY_MARKER = "PASS: RB100 quality zero-via graph contracts"
ALLOCATION_EVENT_MARKER = "RB100_PREFIX_ALLOCATION_EVENT"
ALLOCATION_SUMMARY_MARKER = "PASS: RB100 prefix allocation contracts"
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
)
EVENT_PREFIXES = ("RB100_", "P07_", "PASS: RB100")

GRAPH_EVENT_KEYS = frozenset((
    "protocolVersion sourceRevision runId operationId scope binding diagnostic "
    "feasibility legalRouteProof layoutQualification routeAdoption"
).split())
GRAPH_BINDING_KEYS = frozenset((
    "branchIdentityVerified sourcePublisherUnchanged pass netId startPadId rootPadId "
    "reachedPthPadIds foundTerminalPadId endCell endLayer goalMaskLength policy "
    "primaryLayer initialLayerMask maximumVias prefixHoleCount"
).split())
GRAPH_DIAGNOSTIC_KEYS = frozenset("stability grid work masks".split())
GRAPH_STABILITY_KEYS = frozenset((
    "prefixGeometrySha256 liveBeforeVerified liveAfterVerified liveEffectsUnchanged "
    "goalVerified originalPathVerified"
).split())
GRAPH_GRID_KEYS = frozenset((
    "width height cells originX originY pitch startCell initialLayerMask endCell endLayer"
).split())
GRAPH_WORK_KEYS = frozenset((
    "advanceCalls workSteps copyEntries comparisonEntries ordinaryQueries goalCells "
    "encodedCells activeMillis"
).split())
GRAPH_MASK_KEYS = frozenset("topStepsHex bottomStepsHex goalFacesHex".split())
GRAPH_SUMMARY_KEYS = frozenset((
    "protocolVersion sourceRevision status runId operationId events scope ordinaryQueries "
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
    """Load frozen validators privately; only the documented epoch metadata varies."""
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
    # These are the only compatibility overrides on the frozen reader. No
    # validator function or shared module state is changed.
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
                   goal_faces: Sequence[int]) -> Dict[str, Any]:
    """Forward BFS on each seeded face, using only captured directed permissions."""
    cells = width * height if _integer(width, 1, MAX_GRID_CELLS) and \
        _integer(height, 1, MAX_GRID_CELLS) else 0
    _need(0 < cells <= MAX_GRID_CELLS and
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
    faces: List[Dict[str, Any]] = []
    all_eligible: List[int] = []
    for layer, mask in enumerate((top_steps, bottom_steps)):
        bit = 1 << layer
        if not (initial_layer_mask & bit):
            continue
        distances: List[Optional[int]] = [None] * cells
        distances[start_cell] = 0
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
                # Boundary validation above makes every permission an in-grid
                # directed edge. Keep the guard so malformed direct fixtures fail closed.
                _need(0 <= target_x < width and 0 <= target_y < height,
                      "GRAPH_EDGE_OUT_OF_BOUNDS")
                target = target_y * width + target_x
                if distances[target] is None:
                    distances[target] = distance + 1
                    queue.append(target)

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
            "distanceCapturedEnd": (None if end_steps is None else end_steps * pitch),
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
        "scope": "FIXED_PREFIX_ZERO_NEW_VIA_LENGTH_ONLY",
    }


def _validate_graph_event(value: Any, scalar_event: Mapping[str, Any],
                          path: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, GRAPH_EVENT_KEYS, "GRAPH_EVENT_KEYS_INVALID")
    _need(_integer(value["protocolVersion"], 1, 1) and
          _integer(value["sourceRevision"], SOURCE_REVISION, SOURCE_REVISION) and
          type(value["runId"]) is str and UUID_RE.fullmatch(value["runId"]) is not None and
          value["runId"] == scalar_event["runId"] and
          value["operationId"] == PROBE_OPERATION_ID and
          value["scope"] == "ACTUAL_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY" and
          value["feasibility"] == "UNKNOWN" and
          value["legalRouteProof"] is False and
          value["layoutQualification"] is False and
          value["routeAdoption"] is False,
          "GRAPH_EVENT_IDENTITY_OR_SCOPE_INVALID")

    binding = value["binding"]
    _exact_keys(binding, GRAPH_BINDING_KEYS, "GRAPH_BINDING_KEYS_INVALID")
    _need(binding["branchIdentityVerified"] is True and
          binding["sourcePublisherUnchanged"] is True and
          _integer(binding["pass"], 0, MAX_SCALAR) and
          binding["pass"] == scalar_event["pass"] and
          binding["netId"] == scalar_event["netId"] and
          binding["startPadId"] == scalar_event["startPadId"] and
          binding["rootPadId"] == scalar_event["rootPadId"] and
          all(type(binding[field]) is str and 0 < len(binding[field]) <= 512
              for field in ("netId", "startPadId", "rootPadId")),
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
          grid["initialLayerMask"] == 3 and
          (width, height, grid["originX"], grid["originY"], grid["pitch"]) ==
          (scalar_event["gridWidth"], scalar_event["gridHeight"],
           scalar_event["gridOriginX"], scalar_event["gridOriginY"],
           scalar_event["gridPitch"]),
          "GRAPH_GRID_DOMAIN_OR_SCALAR_BINDING_INVALID")
    first, last = path["nodes"][0], path["nodes"][-1]
    _need(grid["startCell"] == first["cell"] and
          grid["endCell"] == last["cell"] and grid["endLayer"] == last["layer"] and
          (first["x"], first["y"]) ==
          (scalar_event["startX"], scalar_event["startY"]) and
          (last["x"], last["y"], last["layer"]) ==
          (scalar_event["endX"], scalar_event["endY"], scalar_event["endLayer"]),
          "GRAPH_PATH_ENDPOINT_BINDING_INVALID")
    _need(_integer(binding["endCell"], 0, cells - 1) and
          binding["endCell"] == grid["endCell"] and
          _integer(binding["endLayer"], 0, 1) and
          binding["endLayer"] == grid["endLayer"] and
          _integer(binding["goalMaskLength"], 1, 2 * MAX_GRID_CELLS) and
          binding["goalMaskLength"] == 2 * cells and
          binding["policy"] == "FULLER_TWO_LAYER" and
          _integer(binding["primaryLayer"], 0, 1) and
          binding["primaryLayer"] == 1 and
          binding["primaryLayer"] == scalar_event["primaryLayer"] and
          binding["initialLayerMask"] == grid["initialLayerMask"] and
          _integer(binding["maximumVias"], 0, MAX_VIAS) and
          _integer(binding["prefixHoleCount"], 0, MAX_VIAS) and
          binding["prefixHoleCount"] <= binding["maximumVias"],
          "GRAPH_BINDING_GRAPH_DOMAIN_INVALID")

    masks = diagnostic["masks"]
    _exact_keys(masks, GRAPH_MASK_KEYS, "GRAPH_MASK_KEYS_INVALID")
    top, bottom, goals = _decode_masks(masks, cells)
    _need(all(0 <= nibble <= 3 for nibble in goals),
          "GRAPH_GOAL_FACE_MASK_INVALID")
    _need(goals[grid["endCell"]] & (1 << grid["endLayer"]),
          "GRAPH_CAPTURED_END_NOT_A_GOAL")
    _need(path["transitionCount"] == 0 and scalar_event["transitionCount"] == 0 and
          all(node["layer"] == first["layer"] for node in path["nodes"]),
          "GRAPH_ORIGINAL_PATH_NOT_ZERO_VIA")
    for before, after in zip(path["nodes"], path["nodes"][1:]):
        dx, dy = after["x"] - before["x"], after["y"] - before["y"]
        direction_bit = (0x2 if dx == grid["pitch"] else
                         0x8 if dx == -grid["pitch"] else
                         0x4 if dy == grid["pitch"] else
                         0x1 if dy == -grid["pitch"] else 0)
        _need(direction_bit != 0 and
              (top if before["layer"] == 0 else bottom)[before["cell"]] & direction_bit,
              "GRAPH_ORIGINAL_PATH_EDGE_MISSING")

    work = diagnostic["work"]
    _exact_keys(work, GRAPH_WORK_KEYS, "GRAPH_WORK_KEYS_INVALID")
    ranges = {
        "advanceCalls": (1, MAX_WORK_UNITS),
        "workSteps": (1, MAX_TOTAL_WORK),
        "copyEntries": (0, MAX_TOTAL_WORK),
        "comparisonEntries": (0, MAX_TOTAL_WORK),
        "ordinaryQueries": (0, MAX_TOTAL_WORK),
        "goalCells": (0, MAX_TOTAL_WORK),
        "encodedCells": (0, MAX_TOTAL_WORK),
        "activeMillis": (0, MAX_ACTIVE_MS),
    }
    for name, (minimum, maximum) in ranges.items():
        _need(_integer(work[name], minimum, maximum), "GRAPH_WORK_DOMAIN_INVALID")
    expected_queries = _ordinary_query_count(width, height)
    _need(work["ordinaryQueries"] == expected_queries and
          work["goalCells"] == cells and work["encodedCells"] == 3 * cells and
          work["workSteps"] <= work["advanceCalls"] * MAX_WORK_QUANTUM and
          work["workSteps"] >= (work["copyEntries"] + work["comparisonEntries"] +
                                work["ordinaryQueries"] + work["goalCells"] +
                                work["encodedCells"]),
          "GRAPH_WORK_CONSERVATION_INVALID")

    analysis = _analyze_graph(width, height, grid["pitch"], grid["startCell"],
                              grid["endCell"], grid["endLayer"],
                              grid["initialLayerMask"], top, bottom, goals)
    return {
        "event": dict(value), "binding": dict(binding),
        "diagnostic": dict(diagnostic), "analysis": analysis,
        "scope": value["scope"], "feasibility": "UNKNOWN",
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
          value["scope"] == event["scope"] and
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


def _validate_prefix_allocation_event(value: Any,
                                      scalar_event: Mapping[str, Any],
                                      graph: Mapping[str, Any]) -> Dict[str, Any]:
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
    graph_binding = graph["binding"]
    _need(binding["branchIdentityVerified"] is True and
          binding["sourcePublisherUnchanged"] is True and
          _integer(binding["pass"], 0, MAX_SCALAR) and
          binding["pass"] == scalar_event["pass"] == graph_binding["pass"] and
          binding["netId"] == scalar_event["netId"] == graph_binding["netId"] and
          binding["startPadId"] == scalar_event["startPadId"] ==
          graph_binding["startPadId"] and
          binding["rootPadId"] == scalar_event["rootPadId"] ==
          graph_binding["rootPadId"] and
          _integer(binding["prefixHoleCount"], 0, MAX_VIAS) and
          binding["prefixHoleCount"] == graph_binding["prefixHoleCount"] and
          _integer(binding["maximumVias"], 0, MAX_VIAS) and
          binding["maximumVias"] == graph_binding["maximumVias"] and
          binding["prefixHoleCount"] <= binding["maximumVias"] and
          _integer(binding["templateHoleCount"], 0, 0) and
          _integer(binding["currentBranchOrdinal"], 1, MAX_SCALAR),
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
          stability["prefixGeometrySha256"] == graph_prefix_sha and
          stability["liveBeforeVerified"] is True and
          stability["liveAfterVerified"] is True and
          stability["holeIdentitiesVerified"] is True and
          stability["lineageVerified"] is True,
          "ALLOCATION_STABILITY_INVALID")

    totals = diagnostic["totals"]
    _exact_keys(totals, ALLOCATION_TOTALS_KEYS, "ALLOCATION_TOTALS_KEYS_INVALID")
    total_holes = binding["prefixHoleCount"]
    _need(_integer(totals["totalHoles"], 0, MAX_VIAS) and
          totals["totalHoles"] == total_holes,
          "ALLOCATION_TOTAL_HOLES_INVALID")

    rows = diagnostic["rows"]
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
        _need(owner_position == net_positions[net_id] and
              owner_position <= binding["currentNetPosition"] and
              0 < branch_ordinal < binding["currentBranchOrdinal"] and
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


def _validate_receipt_lines(lines: Sequence[str], ledger: types.ModuleType,
                            frozen: types.ModuleType, scalar: types.ModuleType
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
    graph = _validate_graph_event(records[10], old["event"], path)
    graph_summary = _validate_graph_summary(records[11], records[10], graph)
    allocation = _validate_prefix_allocation_event(
        records[12], old["event"], graph)
    allocation_summary = _validate_prefix_allocation_summary(
        records[13], records[12], allocation)
    return {"scalar": old, "currentContext": current_context,
            "path": path, "pathSummary": path_summary,
            "graph": graph, "graphSummary": graph_summary,
            "allocation": allocation,
            "allocationSummary": allocation_summary}


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
    context = _ledger_call(ledger, ledger._validate_input_spec,
                           "INPUT_SPEC_INVALID", spec, spec_path.parent, capture)
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
    _path_call(frozen, frozen._validate_native_run, "NATIVE_RUN_INVALID",
               run_result, context, stdout_raw, receipt_raw, stderr_raw, scalar)
    _console, receipt_lines = _validate_console_and_receipt(
        stdout_raw, receipt_raw, stderr_raw, ledger)
    parsed = _validate_receipt_lines(receipt_lines, ledger, frozen, scalar)
    _scalar_call(scalar, scalar._stable_source_check, "SOURCE_REVALIDATION_FAILED",
                 spec_raw, spec_path, spec_sha256, spec, context, ledger, capture)
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
    return {
        "schema": RESULT_SCHEMA,
        "status": "PASS",
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
