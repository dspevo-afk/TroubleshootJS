#!/usr/bin/env python3
"""Strict source-bound reader for the RB100 query-clone shortcut receipt.

The reader independently derives a bounded geometric replacement family and
validates the actual Router query results. Query qualification does not prove
route adoption, finalization, physical layout, electrical behavior, or playable
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
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple


INPUT_SCHEMA = "p07-quality-shortcut-input-v1"
RUN_SCHEMA = "q100-quality-shortcut-native-run-v1"
RESULT_SCHEMA = "q100-quality-shortcut-reader-result-v1"
SUITE = "Rb100QualityShortcutContractTest"
ENTRY_SCRIPT = "scripts/verify-current-contracts.ps1"
SOURCE_REVISION = 15
SOURCE_INPUT_COUNT = 1070
CONTRACT_JAVA_COUNT = 214
LAYOUT_VERSION = 26
DEPENDENCY_EPOCH = "tsj-generation-dependencies-v28"
PLAN_VERSION = 1
MODEL_VERSION = 4
PHYSICAL_INTENT_VERSION = 9
MAX_SCALAR = 2_147_483_647
MAX_GRID_CELLS = 250_000
SECONDARY_LAYER_COST = 2
TURN_COST = 35
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

PINNED_SCALAR_READER_SHA256 = "0396ae2566454063c6c744fbeb49e68859edb9ef092a23b027e01a3977f52802"
QUALITY_REQUIRED_INPUTS = frozenset({
    "tests/contracts/Rb100QualityShortcutContractTest.java",
    "tests/contracts/Rb100PhysicalDiagnostics.java",
    "tests/contracts/P07ShortcutQueryDiagnostics.java",
    "tests/contracts/P07ShortcutQueryContractTest.java",
    "scripts/verify-current-contracts.ps1",
    "tests/contracts/current-contract-runner-contract.ps1",
})

PATH_EVENT_MARKER = "RB100_QUALITY_PATH_EVENT"
PATH_SUMMARY_MARKER = "PASS: RB100 quality path contracts"
SHORTCUT_EVENT_MARKER = "RB100_SHORTCUT_QUERY_EVENT"
SHORTCUT_SUMMARY_MARKER = "PASS: RB100 quality shortcut contracts"
PATH_EVENT_KEYS = frozenset((
    "protocolVersion sourceRevision runId operationId order captureStage "
    "captureQualityIndex parentIdentityVerified nodeIdentityUnique nodes scope "
    "feasibility legalShortcutProof layoutQualification"
).split())
PATH_NODE_KEYS = frozenset((
    "index parentIndex cell x y layer direction transitions cost heuristic serial key"
).split())
PATH_SUMMARY_KEYS = frozenset((
    "protocolVersion sourceRevision status runId operationId events nodes scope "
    "feasibility legalShortcutProof layoutQualification"
).split())
SHORTCUT_EVENT_KEYS = frozenset((
    "protocolVersion sourceRevision runId operationId scope binding diagnostic "
    "feasibility legalShortcutProof layoutQualification routeAdoption"
).split())
SHORTCUT_BINDING_KEYS = frozenset((
    "branchIdentityVerified sourcePublisherUnchanged pass netId startPadId rootPadId "
    "reachedPthPadIds foundTerminalPadId endCell endLayer goalMaskLength"
).split())
SHORTCUT_DIAGNOSTIC_KEYS = frozenset("stability family work edges candidates result".split())
SHORTCUT_STABILITY_KEYS = frozenset((
    "prefixGeometrySha256 liveBeforeVerified liveAfterVerified liveEffectsUnchanged "
    "goalVerified originalPathVerified"
).split())
SHORTCUT_FAMILY_KEYS = frozenset((
    "order minimumSaving maximumLength maximumBends candidateCount candidateTupleSha256 "
    "replacementEdgeVisits distinctDirectedEdges"
).split())
SHORTCUT_WORK_KEYS = frozenset((
    "advanceCalls workSteps copyEntries comparisonEntries queryCalls reusedEdgeVisits "
    "activeMillis originalPathQueries"
).split())
SHORTCUT_EDGE_KEYS = frozenset(("fromCell toCell allowed").split())
SHORTCUT_CANDIDATE_KEYS = frozenset(("i j orientation length bends allowed").split())
SHORTCUT_RESULT_KEYS = frozenset((
    "queryQualifiedCount minimumQualifiedLength firstQualified"
).split())
SHORTCUT_FIRST_KEYS = frozenset(("i j orientation length bends").split())
SHORTCUT_SUMMARY_KEYS = frozenset((
    "protocolVersion sourceRevision status runId operationId events "
    "queryQualifiedCount scope feasibility legalShortcutProof layoutQualification "
    "routeAdoption"
).split())
RECEIPT_MARKERS = (
    "RB100_QUALITY_SCALAR_CONTROLS",
    "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS",
    "RB100_QUALITY_SCALAR_OPERATION",
    "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS",
    "RB100_QUALITY_SCALAR_OPERATION",
    "RB100_QUALITY_SCALAR_EVENT",
    "RB100_QUALITY_SCALAR_PAIR",
    "PASS: RB100 quality scalar contracts",
    PATH_EVENT_MARKER,
    PATH_SUMMARY_MARKER,
    SHORTCUT_EVENT_MARKER,
    SHORTCUT_SUMMARY_MARKER,
)
EVENT_PREFIXES = ("RB100_", "P07_", "PASS: RB100")
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
ERROR_CODE_RE = re.compile(r"[A-Z0-9_]{1,96}\Z", re.ASCII)

MAX_SPEC_BYTES = 1024 * 1024
MAX_RESULT_BYTES = 2 * 1024 * 1024
MAX_CAPTURE_BYTES = 32 * 1024 * 1024
MAX_QUALIFIED_CANDIDATES = 4290
MAX_REPLACEMENT_EDGE_VISITS = 95810
MAX_DISTINCT_DIRECTED_EDGES = 4354
MAX_ORIGINAL_PATH_EDGES = 65
MAX_ADVANCE_CALLS = 640
MAX_MANUAL_WORK_QUANTUM = 32768
MAX_DIAGNOSTIC_WORK_STEPS = MAX_ADVANCE_CALLS * MAX_MANUAL_WORK_QUANTUM
MAX_DIAGNOSTIC_ACTIVE_MS = 90_000
class QualityPathInputError(ValueError):
    """A static sanitized failure; input text and paths are never interpolated."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _fail(code: str) -> None:
    raise QualityPathInputError(code)


def _need(condition: bool, code: str) -> None:
    if not condition:
        _fail(code)


def _integer(value: Any, minimum: int, maximum: int) -> bool:
    return type(value) is int and minimum <= value <= maximum


def _exact_keys(value: Any, expected: Iterable[str], code: str) -> None:
    _need(type(value) is dict and set(value) == set(expected), code)


def _raise_static_error(error: BaseException, fallback: str) -> None:
    code = getattr(error, "code", None)
    _fail(code if type(code) is str and ERROR_CODE_RE.fullmatch(code) is not None
          else fallback)


def _scalar_call(scalar: types.ModuleType, function, fallback: str, *args):
    try:
        return function(*args)
    except scalar.QualityScalarInputError as error:
        _raise_static_error(error, fallback)


def _ledger_call(ledger: types.ModuleType, function, fallback: str, *args, **kwargs):
    try:
        return function(*args, **kwargs)
    except ledger.LedgerInputError as error:
        _raise_static_error(error, fallback)


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


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
    except QualityPathInputError:
        raise
    except (OSError, RuntimeError, ValueError):
        _fail(code + "_READ_FAILED")


def _exec_scalar_private(raw: bytes, path: Path, name: str) -> types.ModuleType:
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
        _fail("FROZEN_SCALAR_READER_SOURCE_INVALID")
    finally:
        if had_previous:
            sys.modules[name] = previous
        else:
            sys.modules.pop(name, None)
    return module


def _load_frozen_scalar() -> Tuple[types.ModuleType, types.ModuleType,
                                   types.ModuleType, Dict[str, str]]:
    """Load the byte-frozen scalar reader privately and override metadata only."""
    try:
        own_path = Path(os.path.abspath(__file__))
        _need(not _has_reparse_component(own_path), "READER_SOURCE_REPARSE_PATH")
        own_path = own_path.resolve(strict=True)
        _need(own_path.is_file() and not _is_reparse(own_path), "READER_SOURCE_INVALID")
        scalar_path = own_path.parent / "read_quality_scalars.py"
        scalar_raw = _read_local(scalar_path, 2 * 1024 * 1024, "FROZEN_SCALAR_READER")
        _need(_sha256(scalar_raw) == PINNED_SCALAR_READER_SHA256,
              "FROZEN_SCALAR_READER_HASH_MISMATCH")
        scalar = _exec_scalar_private(
            scalar_raw, scalar_path, "_q100_quality_path_scalar_" + uuid.uuid4().hex)
        # Explicit epoch/schema compatibility metadata only. Validators and source
        # remain unchanged, and this module is never left in sys.modules.
        scalar.INPUT_SCHEMA = INPUT_SCHEMA
        scalar.RUN_SCHEMA = RUN_SCHEMA
        scalar.RESULT_SCHEMA = RESULT_SCHEMA
        scalar.SUITE = SUITE
        scalar.SOURCE_REVISION = SOURCE_REVISION
        scalar.SOURCE_INPUT_COUNT = SOURCE_INPUT_COUNT
        scalar.CONTRACT_JAVA_COUNT = CONTRACT_JAVA_COUNT
        scalar.QUALITY_REQUIRED_INPUTS = QUALITY_REQUIRED_INPUTS
        _need(scalar.SOURCE_INPUT_COUNT == SOURCE_INPUT_COUNT and
              scalar.CONTRACT_JAVA_COUNT == CONTRACT_JAVA_COUNT,
              "FROZEN_SCALAR_CENSUS_INVALID")
        try:
            ledger, capture, helper_hashes = scalar._load_pinned_helpers()
        except scalar.QualityScalarInputError as error:
            _raise_static_error(error, "FROZEN_SCALAR_HELPER_LOAD_FAILED")
        helper_hashes["frozenScalarReaderSha256"] = _sha256(scalar_raw)
        return scalar, ledger, capture, helper_hashes
    except QualityPathInputError:
        raise


def _parse_tagged(line: str, marker: str, ledger: types.ModuleType,
                  scalar: types.ModuleType) -> Any:
    return _scalar_call(scalar, scalar._parse_tagged, "PATH_RECEIPT_JSON_INVALID",
                        line, marker, ledger, "PATH_RECEIPT_RECORD")


def _validate_path_event(value: Any, scalar_event: Mapping[str, Any],
                         scalar: types.ModuleType) -> Dict[str, Any]:
    _exact_keys(value, PATH_EVENT_KEYS, "PATH_EVENT_KEYS_INVALID")
    run_id = scalar_event["runId"]
    _scalar_call(scalar, scalar._validate_identity, "PATH_EVENT_IDENTITY_INVALID",
                 value, run_id)
    _need(value["operationId"] == PROBE_OPERATION_ID and
          value["order"] == "START_TO_END" and
          _integer(value["captureStage"], 1, 1) and
          value["parentIdentityVerified"] is True and
          value["nodeIdentityUnique"] is True and
          value["scope"] == "ACTUAL_PUBLISHER_PATH_ONLY" and
          value["feasibility"] == "UNKNOWN" and
          value["legalShortcutProof"] is False and
          value["layoutQualification"] is False,
          "PATH_EVENT_IDENTITY_OR_SCOPE_INVALID")

    nodes = value["nodes"]
    _need(type(nodes) is list and len(nodes) == scalar_event["pathNodeCount"] and
          _integer(value["captureQualityIndex"], 1, MAX_GRID_CELLS * 2) and
          value["captureQualityIndex"] == len(nodes),
          "PATH_CAPTURE_INDEX_OR_COUNT_INVALID")
    width, height = scalar_event["gridWidth"], scalar_event["gridHeight"]
    cells = width * height
    pitch = scalar_event["gridPitch"]
    _need(_integer(cells, 1, MAX_GRID_CELLS) and pitch == 10 and
          len(nodes) <= cells * 2,
          "PATH_GRID_DOMAIN_INVALID")

    parsed: List[Dict[str, int]] = []
    keys = set()
    serials = set()
    for index, node in enumerate(nodes):
        _exact_keys(node, PATH_NODE_KEYS, "PATH_NODE_KEYS_INVALID")
        _need(_integer(node["index"], 0, len(nodes) - 1) and node["index"] == index and
              _integer(node["parentIndex"], -1, len(nodes) - 1) and
              node["parentIndex"] == (-1 if index == 0 else index - 1),
              "PATH_PARENT_ORDER_INVALID")
        physical_max_x = scalar_event["gridOriginX"] + (width - 1) * pitch
        physical_max_y = scalar_event["gridOriginY"] + (height - 1) * pitch
        _need(_integer(node["x"], -MAX_SCALAR - 1, MAX_SCALAR) and
              _integer(node["y"], -MAX_SCALAR - 1, MAX_SCALAR) and
              scalar_event["gridOriginX"] <= node["x"] <= physical_max_x and
              scalar_event["gridOriginY"] <= node["y"] <= physical_max_y,
              "PATH_GRID_COORDINATE_INVALID")
        grid_dx = node["x"] - scalar_event["gridOriginX"]
        grid_dy = node["y"] - scalar_event["gridOriginY"]
        _need(grid_dx % pitch == 0 and grid_dy % pitch == 0 and
              grid_dx // pitch < width and grid_dy // pitch < height and
              _integer(node["cell"], 0, cells - 1) and
              node["cell"] == (grid_dy // pitch) * width + (grid_dx // pitch),
              "PATH_GRID_COORDINATE_INVALID")
        _need(_integer(node["layer"], 0, 1) and
              _integer(node["direction"], 0, 4) and
              _integer(node["transitions"], 0, 4) and
              _integer(node["cost"], 0, MAX_SCALAR) and
              _integer(node["heuristic"], 0, MAX_SCALAR) and
              _integer(node["serial"], 0, MAX_SCALAR) and
              _integer(node["key"], 0, MAX_SCALAR),
              "PATH_NODE_DOMAIN_INVALID")
        expected_key = (((node["transitions"] * 2 + node["layer"]) * 5 +
                         node["direction"]) * cells) + node["cell"]
        _need(node["key"] == expected_key, "PATH_STATE_KEY_INVALID")
        _need(node["key"] not in keys and node["serial"] not in serials,
              "PATH_NODE_IDENTITY_DUPLICATE")
        keys.add(node["key"])
        serials.add(node["serial"])
        parsed.append(node)

    first, last = parsed[0], parsed[-1]
    _need((first["x"], first["y"], first["layer"], first["direction"],
           first["transitions"]) ==
          (scalar_event["startX"], scalar_event["startY"],
           scalar_event["firstLayer"], 4, 0) and
          (last["x"], last["y"], last["layer"], last["transitions"]) ==
          (scalar_event["endX"], scalar_event["endY"], scalar_event["endLayer"],
           scalar_event["transitionCount"]),
          "PATH_ENDPOINT_IDENTITY_INVALID")
    initial_cost = (0 if first["layer"] == scalar_event["primaryLayer"]
                    else SECONDARY_LAYER_COST)
    _need(first["cost"] == initial_cost, "PATH_INITIAL_COST_INVALID")

    same_face_cells: Dict[Tuple[int, int], int] = {}
    same_face_edges: Dict[Tuple[int, int, int], int] = {}
    planar_edges = 0
    bends = 0
    serial_previous = -1
    for index, node in enumerate(parsed):
        _need(node["serial"] > serial_previous, "PATH_SERIAL_ORDER_INVALID")
        serial_previous = node["serial"]
        cell_key = (node["layer"], node["cell"])
        same_face_cells[cell_key] = same_face_cells.get(cell_key, 0) + 1
        if index == 0:
            continue
        previous = parsed[index - 1]
        dx, dy = node["x"] - previous["x"], node["y"] - previous["y"]
        _need(node["layer"] == previous["layer"] and
              node["transitions"] == previous["transitions"] and
              abs(dx) + abs(dy) == pitch,
              "PATH_EDGE_ADJACENCY_INVALID")
        direction = (1 if dx == pitch else 3 if dx == -pitch else
                     2 if dy == pitch else 0)
        _need(node["direction"] == direction,
              "PATH_HEADING_INVALID")
        turn = previous["direction"] != 4 and previous["direction"] != direction
        if turn:
            bends += 1
        step_cost = pitch + (SECONDARY_LAYER_COST
                             if node["layer"] != scalar_event["primaryLayer"] else 0)
        if turn:
            step_cost += TURN_COST
        _need(node["cost"] == previous["cost"] + step_cost,
              "PATH_COST_CONSERVATION_INVALID")
        planar_edges += 1
        edge = (node["layer"], min(previous["cell"], node["cell"]),
                max(previous["cell"], node["cell"]))
        same_face_edges[edge] = same_face_edges.get(edge, 0) + 1

    planar_length = planar_edges * pitch
    direct_distance = (abs(last["x"] - first["x"]) +
                       abs(last["y"] - first["y"]))
    transition_count = sum(
        1 for before, after in zip(parsed, parsed[1:])
        if before["layer"] != after["layer"])
    _need(planar_length == scalar_event["planarLength"] and
          direct_distance == scalar_event["directDistance"] and
          bends == scalar_event["bends"] and
          transition_count == scalar_event["transitionCount"] == 0 and
          len(parsed) - 1 == planar_edges + transition_count,
          "PATH_SCALAR_CONSERVATION_INVALID")

    repeated_cells = sum(count - 1 for count in same_face_cells.values() if count > 1)
    repeated_edges = sum(count - 1 for count in same_face_edges.values() if count > 1)
    return {
        "event": dict(value),
        "nodes": parsed,
        "pathNodeCount": len(parsed),
        "planarLength": planar_length,
        "directDistance": direct_distance,
        "bends": bends,
        "transitionCount": transition_count,
        "repeatedSameFaceCells": repeated_cells,
        "repeatedSameFaceEdges": repeated_edges,
        "scope": "ACTUAL_PUBLISHER_PATH_ONLY",
        "feasibility": "UNKNOWN",
        "legalShortcutProof": False,
        "layoutQualification": False,
    }


def _validate_path_summary(value: Any, event: Mapping[str, Any],
                           scalar: types.ModuleType) -> Dict[str, Any]:
    _exact_keys(value, PATH_SUMMARY_KEYS, "PATH_SUMMARY_KEYS_INVALID")
    _scalar_call(scalar, scalar._validate_identity, "PATH_SUMMARY_IDENTITY_INVALID",
                 value, event["runId"])
    _need(value["status"] == "PASS" and
          value["operationId"] == PROBE_OPERATION_ID and
          _integer(value["events"], 1, 1) and
          _integer(value["nodes"], 1, MAX_GRID_CELLS * 2) and
          value["nodes"] == event["captureQualityIndex"] and
          value["scope"] == "ACTUAL_PUBLISHER_PATH_ONLY" and
          value["feasibility"] == "UNKNOWN" and
          value["legalShortcutProof"] is False and
          value["layoutQualification"] is False,
          "PATH_SUMMARY_IDENTITY_INVALID")
    return dict(value)


def _path_direction(before: Mapping[str, int], after: Mapping[str, int]) -> int:
    dx, dy = after["x"] - before["x"], after["y"] - before["y"]
    return (1 if dx > 0 else 3 if dx < 0 else
            2 if dy > 0 else 0)


def _monotone_replacement(start: Mapping[str, int], end: Mapping[str, int],
                          orientation: int, pitch: int) -> List[Tuple[int, int]]:
    x, y = start["x"], start["y"]
    end_x, end_y = end["x"], end["y"]
    points = [(x, y)]
    axes = ("x", "y") if orientation == 0 else ("y", "x")
    for axis in axes:
        target = end_x if axis == "x" else end_y
        coordinate = x if axis == "x" else y
        delta = target - coordinate
        step = pitch if delta > 0 else -pitch
        for _ in range(abs(delta) // pitch):
            if axis == "x":
                x += step
            else:
                y += step
            points.append((x, y))
    return points


def _derive_shortcut_family(path: Mapping[str, Any],
                            scalar_event: Mapping[str, Any]) -> Dict[str, Any]:
    """Derive the complete bounded geometric family from the captured path."""
    nodes = path["nodes"]
    _need(type(nodes) is list and 1 <= len(nodes) <= 66,
          "SHORTCUT_PATH_BOUND_INVALID")
    width, height = scalar_event["gridWidth"], scalar_event["gridHeight"]
    origin_x, origin_y = scalar_event["gridOriginX"], scalar_event["gridOriginY"]
    pitch = scalar_event["gridPitch"]
    cells = width * height
    path_length = path["planarLength"]
    direct_distance = path["directDistance"]
    minimum_saving = max(1, path_length - 3 * direct_distance)
    maximum_length = 3 * direct_distance
    maximum_bends = 16
    family: List[Dict[str, Any]] = []
    edge_visits = 0
    edge_set = set()

    def cell_for(x: int, y: int) -> int:
        grid_x, grid_y = x - origin_x, y - origin_y
        _need(grid_x >= 0 and grid_y >= 0 and grid_x % pitch == 0 and
              grid_y % pitch == 0, "SHORTCUT_REPLACEMENT_GRID_INVALID")
        column, row = grid_x // pitch, grid_y // pitch
        _need(column < width and row < height,
              "SHORTCUT_REPLACEMENT_GRID_INVALID")
        return row * width + column

    for i in range(len(nodes) - 1):
        for j in range(i + 1, len(nodes)):
            start, end = nodes[i], nodes[j]
            dx, dy = end["x"] - start["x"], end["y"] - start["y"]
            _need(dx % pitch == 0 and dy % pitch == 0,
                  "SHORTCUT_ANCHOR_GRID_INVALID")
            manhattan_steps = (abs(dx) + abs(dy)) // pitch
            original_steps = j - i
            saving = (original_steps - manhattan_steps) * pitch
            length = path_length - saving
            if saving < minimum_saving or length > maximum_length:
                continue

            orientations = (0,) if dx == 0 or dy == 0 else (0, 1)
            for orientation in orientations:
                replacement = _monotone_replacement(start, end, orientation, pitch)
                replacement_directions = [
                    _path_direction({"x": before[0], "y": before[1]},
                                    {"x": after[0], "y": after[1]})
                    for before, after in zip(replacement, replacement[1:])
                ]
                prefix_directions = [node["direction"] for node in nodes[1:i + 1]]
                suffix_directions = [node["direction"] for node in nodes[j + 1:]]
                directions = prefix_directions + replacement_directions + suffix_directions
                bends = sum(before != after for before, after in
                            zip(directions, directions[1:]))
                if bends > maximum_bends:
                    continue

                replacement_cells = [cell_for(x, y) for x, y in replacement]
                replacement_edges = list(zip(replacement_cells, replacement_cells[1:]))
                edge_visits += len(replacement_edges)
                _need(edge_visits <= MAX_REPLACEMENT_EDGE_VISITS,
                      "SHORTCUT_EDGE_VISIT_LIMIT_INVALID")
                for edge in replacement_edges:
                    edge_set.add(edge)
                family.append({
                    "i": i, "j": j, "orientation": orientation,
                    "saving": saving, "length": length, "bends": bends,
                    "replacementEdges": replacement_edges,
                })
                _need(len(family) <= MAX_QUALIFIED_CANDIDATES,
                      "SHORTCUT_CANDIDATE_LIMIT_INVALID")

    ordered_edges = sorted(edge_set)
    _need(len(ordered_edges) <= MAX_DISTINCT_DIRECTED_EDGES,
          "SHORTCUT_DIRECTED_EDGE_LIMIT_INVALID")
    tuples = [[item["i"], item["j"], item["orientation"],
               item["saving"], item["bends"]] for item in family]
    tuple_bytes = json.dumps(tuples, separators=(",", ":"),
                             ensure_ascii=True).encode("ascii")
    return {
        "minimumSaving": minimum_saving,
        "maximumLength": maximum_length,
        "maximumBends": maximum_bends,
        "candidates": family,
        "candidateCount": len(family),
        "candidateTupleSha256": _sha256(tuple_bytes),
        "replacementEdgeVisits": edge_visits,
        "edges": ordered_edges,
    }


def _validate_shortcut_binding(binding: Any, scalar_event: Mapping[str, Any],
                                path: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(binding, SHORTCUT_BINDING_KEYS, "SHORTCUT_BINDING_KEYS_INVALID")
    _need(binding["branchIdentityVerified"] is True and
          binding["sourcePublisherUnchanged"] is True and
          _integer(binding["pass"], 0, MAX_SCALAR) and
          binding["pass"] == scalar_event["pass"] and
          binding["netId"] == scalar_event["netId"] and
          binding["startPadId"] == scalar_event["startPadId"] and
          binding["rootPadId"] == scalar_event["rootPadId"],
          "SHORTCUT_BINDING_IDENTITY_INVALID")
    _need(type(binding["netId"]) is str and binding["netId"] and
          type(binding["startPadId"]) is str and binding["startPadId"] and
          type(binding["rootPadId"]) is str and binding["rootPadId"],
          "SHORTCUT_BINDING_IDENTITY_INVALID")
    reached = binding["reachedPthPadIds"]
    _need(type(reached) is list and len(reached) <= 246 and
          all(type(value) is str and 0 < len(value) <= 256 for value in reached) and
          len(set(reached)) == len(reached) and reached == sorted(reached) and
          binding["rootPadId"] not in reached,
          "SHORTCUT_REACHED_PTH_IDS_INVALID")
    found_terminal = binding["foundTerminalPadId"]
    _need(found_terminal is None or
          (type(found_terminal) is str and 0 < len(found_terminal) <= 256),
          "SHORTCUT_FOUND_TERMINAL_ID_INVALID")
    _need(found_terminal is None or
          found_terminal == binding["rootPadId"] or found_terminal in reached,
          "SHORTCUT_FOUND_TERMINAL_ID_INVALID")

    end = path["nodes"][-1]
    cells = scalar_event["gridWidth"] * scalar_event["gridHeight"]
    _need(_integer(binding["endCell"], 0, cells - 1) and
          binding["endCell"] == end["cell"] and
          _integer(binding["endLayer"], 0, 1) and
          binding["endLayer"] == end["layer"] and
          _integer(binding["goalMaskLength"], 1, MAX_GRID_CELLS * 2) and
          binding["goalMaskLength"] == 2 * cells,
          "SHORTCUT_END_GOAL_IDENTITY_INVALID")
    return dict(binding)


def _original_path_edges(path: Mapping[str, Any]) -> set:
    nodes = path["nodes"]
    return {(before["cell"], after["cell"])
            for before, after in zip(nodes, nodes[1:])}


def _validate_shortcut_event(value: Any, scalar_event: Mapping[str, Any],
                             path: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, SHORTCUT_EVENT_KEYS, "SHORTCUT_EVENT_KEYS_INVALID")
    scalar = path["event"]
    _need(_integer(value["protocolVersion"], 1, 1) and
          _integer(value["sourceRevision"], SOURCE_REVISION, SOURCE_REVISION) and
          value["runId"] == scalar["runId"] and
          value["operationId"] == PROBE_OPERATION_ID and
          value["scope"] == "ACTUAL_QUERY_CLONE_SHORTCUT_FAMILY_ONLY" and
          value["feasibility"] == "UNKNOWN" and
          value["legalShortcutProof"] is False and
          value["layoutQualification"] is False and
          value["routeAdoption"] is False,
          "SHORTCUT_EVENT_IDENTITY_OR_SCOPE_INVALID")
    binding = _validate_shortcut_binding(value["binding"], scalar_event, path)
    derived = _derive_shortcut_family(path, scalar_event)
    diagnostic = value["diagnostic"]
    _exact_keys(diagnostic, SHORTCUT_DIAGNOSTIC_KEYS,
                "SHORTCUT_DIAGNOSTIC_KEYS_INVALID")

    stability = diagnostic["stability"]
    _exact_keys(stability, SHORTCUT_STABILITY_KEYS,
                "SHORTCUT_STABILITY_KEYS_INVALID")
    _need(type(stability["prefixGeometrySha256"]) is str and
          SHA256_RE.fullmatch(stability["prefixGeometrySha256"]) is not None and
          stability["liveBeforeVerified"] is True and
          stability["liveAfterVerified"] is True and
          stability["liveEffectsUnchanged"] is True and
          stability["goalVerified"] is True and
          stability["originalPathVerified"] is True,
          "SHORTCUT_STABILITY_INVALID")

    family = diagnostic["family"]
    _exact_keys(family, SHORTCUT_FAMILY_KEYS, "SHORTCUT_FAMILY_KEYS_INVALID")
    _need(family["order"] == "I_J_ORIENTATION_X_FIRST_0_Y_FIRST_1" and
          _integer(family["minimumSaving"], 1, MAX_SCALAR) and
          family["minimumSaving"] == derived["minimumSaving"] and
          _integer(family["maximumLength"], 0, MAX_SCALAR) and
          family["maximumLength"] == derived["maximumLength"] and
          _integer(family["maximumBends"], 16, 16) and
          _integer(family["candidateCount"], 0, MAX_QUALIFIED_CANDIDATES) and
          family["candidateCount"] == derived["candidateCount"] and
          type(family["candidateTupleSha256"]) is str and
          SHA256_RE.fullmatch(family["candidateTupleSha256"]) is not None and
          family["candidateTupleSha256"] == derived["candidateTupleSha256"] and
          _integer(family["replacementEdgeVisits"], 0, MAX_REPLACEMENT_EDGE_VISITS) and
          family["replacementEdgeVisits"] == derived["replacementEdgeVisits"] and
          _integer(family["distinctDirectedEdges"], 0, MAX_DISTINCT_DIRECTED_EDGES),
          "SHORTCUT_FAMILY_DERIVATION_INVALID")

    edges = diagnostic["edges"]
    _need(type(edges) is list and len(edges) == family["distinctDirectedEdges"] and
          len(edges) == len(derived["edges"]), "SHORTCUT_EDGE_SET_INVALID")
    edge_allowed: Dict[Tuple[int, int], bool] = {}
    cells = scalar_event["gridWidth"] * scalar_event["gridHeight"]
    for item, expected in zip(edges, derived["edges"]):
        _exact_keys(item, SHORTCUT_EDGE_KEYS, "SHORTCUT_EDGE_KEYS_INVALID")
        _need(_integer(item["fromCell"], 0, cells - 1) and
              _integer(item["toCell"], 0, cells - 1) and
              type(item["allowed"]) is bool and
              (item["fromCell"], item["toCell"]) == expected,
              "SHORTCUT_EDGE_SET_INVALID")
        edge_allowed[expected] = item["allowed"]

    original_path_edges = _original_path_edges(path)
    family_edges = set(derived["edges"])
    overlapping_original_edges = original_path_edges & family_edges
    missing_original_edges = original_path_edges - family_edges
    _need(all(edge_allowed[edge] is True for edge in overlapping_original_edges),
          "SHORTCUT_ORIGINAL_PATH_EDGE_INVALID")

    work = diagnostic["work"]
    _exact_keys(work, SHORTCUT_WORK_KEYS, "SHORTCUT_WORK_KEYS_INVALID")
    # Copy/comparison task counters can cover several full grid arrays; keep
    # them bounded by the charged work budget and the aggregate conservation
    # check below instead of a one-grid geometry bound.
    work_ranges = {
        "advanceCalls": (1, MAX_ADVANCE_CALLS),
        "workSteps": (0, MAX_DIAGNOSTIC_WORK_STEPS),
        "copyEntries": (0, MAX_DIAGNOSTIC_WORK_STEPS),
        "comparisonEntries": (0, MAX_DIAGNOSTIC_WORK_STEPS),
        "queryCalls": (0, MAX_DISTINCT_DIRECTED_EDGES + MAX_ORIGINAL_PATH_EDGES),
        "reusedEdgeVisits": (0, MAX_REPLACEMENT_EDGE_VISITS),
        "activeMillis": (0, MAX_DIAGNOSTIC_ACTIVE_MS),
        "originalPathQueries": (0, MAX_ORIGINAL_PATH_EDGES),
    }
    for name, bounds in work_ranges.items():
        _need(_integer(work[name], bounds[0], bounds[1]),
              "SHORTCUT_WORK_DOMAIN_INVALID")
    _need(work["originalPathQueries"] == len(missing_original_edges) and
          work["queryCalls"] == len(edges) + work["originalPathQueries"] and
          work["reusedEdgeVisits"] ==
          derived["replacementEdgeVisits"] - len(edges) and
          work["workSteps"] <= work["advanceCalls"] * MAX_MANUAL_WORK_QUANTUM and
          work["workSteps"] >= (family["candidateCount"] +
                                derived["replacementEdgeVisits"] +
                                work["originalPathQueries"] +
                                work["copyEntries"] + work["comparisonEntries"]),
          "SHORTCUT_WORK_CONSERVATION_INVALID")

    candidates = diagnostic["candidates"]
    _need(type(candidates) is list and
          len(candidates) == derived["candidateCount"],
          "SHORTCUT_CANDIDATE_SET_INVALID")
    qualified = []
    for item, expected in zip(candidates, derived["candidates"]):
        _exact_keys(item, SHORTCUT_CANDIDATE_KEYS,
                    "SHORTCUT_CANDIDATE_KEYS_INVALID")
        _need(_integer(item["i"], 0, len(path["nodes"]) - 2) and
              _integer(item["j"], item["i"] + 1, len(path["nodes"]) - 1) and
              _integer(item["orientation"], 0, 1) and
              item["i"] == expected["i"] and
              item["j"] == expected["j"] and
              item["orientation"] == expected["orientation"] and
              _integer(item["length"], 0, MAX_SCALAR) and
              item["length"] == expected["length"] and
              _integer(item["bends"], 0, MAX_SCALAR) and
              item["bends"] == expected["bends"] and
              type(item["allowed"]) is bool,
              "SHORTCUT_CANDIDATE_GEOMETRY_INVALID")
        edge_flags = [edge_allowed[edge] for edge in expected["replacementEdges"]]
        _need(item["allowed"] is all(edge_flags),
              "SHORTCUT_CANDIDATE_EDGE_CONJUNCTION_INVALID")
        if item["allowed"]:
            qualified.append({
                "i": expected["i"], "j": expected["j"],
                "orientation": expected["orientation"],
                "length": expected["length"], "bends": expected["bends"],
            })

    result = diagnostic["result"]
    _exact_keys(result, SHORTCUT_RESULT_KEYS, "SHORTCUT_RESULT_KEYS_INVALID")
    minimum_length = min((item["length"] for item in qualified), default=None)
    first_qualified = qualified[0] if qualified else None
    _need(_integer(result["queryQualifiedCount"], 0, MAX_QUALIFIED_CANDIDATES) and
          result["queryQualifiedCount"] == len(qualified) and
          (result["minimumQualifiedLength"] is None or
           _integer(result["minimumQualifiedLength"], 0, MAX_SCALAR)) and
          result["minimumQualifiedLength"] == minimum_length,
          "SHORTCUT_RESULT_COUNT_INVALID")
    if first_qualified is None:
        _need(result["firstQualified"] is None,
              "SHORTCUT_FIRST_QUALIFIED_INVALID")
    else:
        first_record = result["firstQualified"]
        _exact_keys(first_record, SHORTCUT_FIRST_KEYS,
                    "SHORTCUT_FIRST_QUALIFIED_KEYS_INVALID")
        _need(_integer(first_record["i"], 0, len(path["nodes"]) - 2) and
              _integer(first_record["j"], first_record["i"] + 1,
                       len(path["nodes"]) - 1) and
              _integer(first_record["orientation"], 0, 1) and
              _integer(first_record["length"], 0, MAX_SCALAR) and
              _integer(first_record["bends"], 0, 16) and
              first_record == first_qualified,
              "SHORTCUT_FIRST_QUALIFIED_INVALID")
    return {
        "event": dict(value), "binding": binding,
        "diagnostic": dict(diagnostic), "family": derived,
        "queryQualifiedCount": len(qualified),
        "minimumQualifiedLength": minimum_length,
        "firstQualified": first_qualified,
        "scope": "ACTUAL_QUERY_CLONE_SHORTCUT_FAMILY_ONLY",
        "feasibility": "UNKNOWN", "legalShortcutProof": False,
        "layoutQualification": False, "routeAdoption": False,
    }


def _validate_shortcut_summary(value: Any, event: Mapping[str, Any],
                               shortcut: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, SHORTCUT_SUMMARY_KEYS,
                "SHORTCUT_SUMMARY_KEYS_INVALID")
    _need(_integer(value["protocolVersion"], 1, 1) and
          _integer(value["sourceRevision"], SOURCE_REVISION, SOURCE_REVISION) and
          value["status"] == "PASS" and value["runId"] == event["runId"] and
          value["operationId"] == PROBE_OPERATION_ID and
          _integer(value["events"], 1, 1) and
          _integer(value["queryQualifiedCount"], 0, MAX_QUALIFIED_CANDIDATES) and
          value["queryQualifiedCount"] == shortcut["queryQualifiedCount"] and
          value["scope"] == "ACTUAL_QUERY_CLONE_SHORTCUT_FAMILY_ONLY" and
          value["feasibility"] == "UNKNOWN" and
          value["legalShortcutProof"] is False and
          value["layoutQualification"] is False and
          value["routeAdoption"] is False,
          "SHORTCUT_SUMMARY_IDENTITY_OR_SCOPE_INVALID")
    return dict(value)


def _validate_current_authored_context(scalar_receipt: Mapping[str, Any]) -> Dict[str, Any]:
    expected = {
        "physicalPolicyId": "MEDIUM_BOARD",
        "physicalPolicyVersion": 1,
        "placementDensityVersion": 14,
        "routingLayer": "BOTTOM",
        "traceWidthProfile": "ROLE_TRACE@1",
        "placementPackingVersion": 4,
    }
    for operation_name in ("nullOperation", "probeOperation"):
        operation = scalar_receipt[operation_name]
        facts = operation["facts"]
        for field, wanted in expected.items():
            actual = facts.get(field)
            if type(wanted) is int:
                valid = _integer(actual, wanted, wanted)
            else:
                valid = type(actual) is str and actual == wanted
            _need(valid, "QUALITY_PATH_CONTEXT_INVALID")
    return dict(expected)


def _validate_receipt_lines(lines: Sequence[str], ledger: types.ModuleType,
                            scalar: types.ModuleType) -> Dict[str, Any]:
    _need(type(lines) in (list, tuple) and len(lines) == len(RECEIPT_MARKERS) and
          all(type(line) is str and line for line in lines),
          "PATH_RECEIPT_EVENT_COUNT_INVALID")
    records = [
        _parse_tagged(line, marker, ledger, scalar)
        for line, marker in zip(lines, RECEIPT_MARKERS)
    ]
    old = _scalar_call(scalar, scalar._validate_receipt_lines,
                       "SCALAR_RECEIPT_INVALID", lines[:8], ledger)
    current_context = _validate_current_authored_context(old)
    path = _validate_path_event(records[8], old["event"], scalar)
    summary = _validate_path_summary(records[9], records[8], scalar)
    shortcut = _validate_shortcut_event(records[10], old["event"], path)
    shortcut_summary = _validate_shortcut_summary(
        records[11], records[10], shortcut)
    return {"scalar": old, "path": path, "pathSummary": summary,
            "shortcut": shortcut, "shortcutSummary": shortcut_summary,
            "currentContext": current_context}


def _validate_console_and_receipt(stdout_raw: bytes, receipt_raw: bytes,
                                  stderr_raw: bytes, ledger: types.ModuleType,
                                  scalar: types.ModuleType
                                  ) -> Tuple[List[str], List[str]]:
    try:
        console = ledger._decode_lines(stdout_raw, "RUN_STDOUT")
        receipt = ledger._decode_lines(receipt_raw, "RUN_RECEIPT")
        stderr = ledger._decode_lines(stderr_raw, "RUN_STDERR")
    except ledger.LedgerInputError as error:
        _raise_static_error(error, "RUN_CAPTURE_UTF8_INVALID")
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
    _need(sum(line.startswith(PATH_SUMMARY_MARKER + " ") for line in receipt) == 1 and
          sum(line.startswith(SHORTCUT_SUMMARY_MARKER + " ") for line in receipt) == 1,
          "RUN_PATH_SUMMARY_MARKER_INVALID")
    return console, receipt


def _validate_native_run(value: Any, context: Mapping[str, Any],
                         stdout_raw: bytes, receipt_raw: bytes, stderr_raw: bytes,
                         scalar: types.ModuleType) -> None:
    _scalar_call(scalar, scalar._validate_native_run, "NATIVE_RUN_INVALID",
                 value, context, stdout_raw, receipt_raw, stderr_raw)


def _stable_reader_check(reader_sha: str, helper_hashes: Mapping[str, str],
                         scalar: types.ModuleType) -> None:
    own_path = Path(os.path.abspath(__file__))
    own_raw = _read_local(own_path, 2 * 1024 * 1024, "READER_SOURCE")
    _need(_sha256(own_raw) == reader_sha, "READER_SOURCE_CHANGED_DURING_READ")
    _scalar_call(scalar, scalar._stable_helper_check, "PINNED_HELPER_CHANGED_DURING_READ", {
            "readerSha256": helper_hashes["readerSha256"],
            "prefixLedgerSha256": helper_hashes["prefixLedgerSha256"],
            "captureHelperSha256": helper_hashes["captureHelperSha256"],
            "graphOracleSha256": helper_hashes["graphOracleSha256"],
        })


def run_validation(spec_argument: str, spec_sha256: str) -> Dict[str, Any]:
    reader_raw = _read_local(Path(os.path.abspath(__file__)),
                             2 * 1024 * 1024, "READER_SOURCE")
    reader_sha = _sha256(reader_raw)
    scalar, ledger, capture, helper_hashes = _load_frozen_scalar()
    _need(type(spec_sha256) is str and SHA256_RE.fullmatch(spec_sha256) is not None,
          "CLI_ARGUMENTS_INVALID")
    spec_path = _ledger_call(ledger, ledger._resolve_path, "INPUT_SPEC_INVALID",
                             spec_argument, Path.cwd(), "INPUT_SPEC")
    spec_raw = _ledger_call(ledger, ledger._read_bounded, "INPUT_SPEC_INVALID",
                            spec_path, MAX_SPEC_BYTES, "INPUT_SPEC")
    _need(_sha256(spec_raw) == spec_sha256, "INPUT_SPEC_SHA256_MISMATCH")
    spec = _ledger_call(ledger, ledger._strict_json, "INPUT_SPEC_INVALID",
                        spec_raw, "INPUT_SPEC")
    context = _ledger_call(ledger, ledger._validate_input_spec, "INPUT_SPEC_INVALID",
                           spec, spec_path.parent, capture)
    _need(QUALITY_REQUIRED_INPUTS <= set(context["pinMap"]),
          "QUALITY_SOURCE_PINS_OMIT_REQUIRED_INPUT")
    result_raw = _ledger_call(ledger, ledger._read_bounded, "NATIVE_RUN_READ_FAILED",
                              context["artifacts"]["resultPath"],
                              MAX_RESULT_BYTES, "NATIVE_RUN")
    stdout_raw = _ledger_call(ledger, ledger._read_bounded, "RUN_STDOUT_READ_FAILED",
                              context["artifacts"]["stdoutPath"],
                              MAX_CAPTURE_BYTES, "RUN_STDOUT")
    receipt_raw = _ledger_call(ledger, ledger._read_bounded, "RUN_RECEIPT_READ_FAILED",
                               context["artifacts"]["receiptPath"],
                               MAX_CAPTURE_BYTES, "RUN_RECEIPT")
    stderr_raw = _ledger_call(ledger, ledger._read_bounded, "RUN_STDERR_READ_FAILED",
                              context["artifacts"]["stderrPath"],
                              MAX_CAPTURE_BYTES, "RUN_STDERR", allow_empty=True)
    run_result = _ledger_call(ledger, ledger._strict_json, "NATIVE_RUN_JSON_INVALID",
                              result_raw, "NATIVE_RUN")
    _validate_native_run(run_result, context, stdout_raw, receipt_raw, stderr_raw, scalar)
    _console, receipt_lines = _validate_console_and_receipt(
        stdout_raw, receipt_raw, stderr_raw, ledger, scalar)
    parsed = _validate_receipt_lines(receipt_lines, ledger, scalar)
    _scalar_call(scalar, scalar._stable_source_check, "SOURCE_REVALIDATION_FAILED",
                 spec_raw, spec_path, spec_sha256, spec, context, ledger, capture)
    _stable_reader_check(reader_sha, helper_hashes, scalar)

    required_hashes = {
        relative: context["pinMap"][relative]["sha256"]
        for relative in sorted(QUALITY_REQUIRED_INPUTS)
    }
    scalar_event = parsed["scalar"]["event"]
    path = parsed["path"]
    shortcut = parsed["shortcut"]
    shortcut_binding = shortcut["binding"]
    shortcut_diagnostic = shortcut["diagnostic"]
    shortcut_family = shortcut_diagnostic["family"]
    shortcut_work = shortcut_diagnostic["work"]
    shortcut_stability = shortcut_diagnostic["stability"]
    return {
        "schema": RESULT_SCHEMA,
        "status": "PASS",
        "sourceSnapshotRevision": SOURCE_REVISION,
        "source": {
            "inputCount": context["inputCount"],
            "inputBytes": context["inputBytes"],
            "pinsSha256": context["pinsSha256"],
            "requiredNativeInputSha256": required_hashes,
            "readerSha256": reader_sha,
            "frozenScalarReaderSha256": helper_hashes["frozenScalarReaderSha256"],
            "prefixLedgerSha256": helper_hashes["prefixLedgerSha256"],
            "captureHelperSha256": helper_hashes["captureHelperSha256"],
            "graphOracleSha256": helper_hashes["graphOracleSha256"],
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
            "runId": parsed["scalar"]["runId"],
            "variant": VARIANT,
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
            "feasibility": "UNKNOWN",
            "legalShortcutProof": False,
            "layoutQualification": False,
        },
        "qualityShortcutObservation": {
            "scope": "ACTUAL_QUERY_CLONE_SHORTCUT_FAMILY_ONLY",
            "binding": shortcut_binding,
            "stability": shortcut_stability,
            "family": {
                "minimumSaving": shortcut_family["minimumSaving"],
                "maximumLength": shortcut_family["maximumLength"],
                "maximumBends": shortcut_family["maximumBends"],
                "candidateCount": shortcut_family["candidateCount"],
                "candidateTupleSha256": shortcut_family["candidateTupleSha256"],
                "replacementEdgeVisits": shortcut_family["replacementEdgeVisits"],
                "distinctDirectedEdges": shortcut_family["distinctDirectedEdges"],
            },
            "work": shortcut_work,
            "queryQualifiedCount": shortcut["queryQualifiedCount"],
            "minimumQualifiedLength": shortcut["minimumQualifiedLength"],
            "firstQualified": shortcut["firstQualified"],
            "feasibility": "UNKNOWN",
            "legalShortcutProof": False,
            "layoutQualification": False,
            "routeAdoption": False,
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
