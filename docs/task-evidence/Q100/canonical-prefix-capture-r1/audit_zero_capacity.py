"""Independent zero-VIA connectivity audit for a validated canonical capture.

The BFS consumes the frozen directed graph codec, but does not use the minimum-VIA
oracle's search implementation. At H48 it never follows a vertical face change,
even when the relaxed graph carries VIA mask bits.
"""
from __future__ import annotations

from array import array
import hashlib
import json
import math
import os
from pathlib import Path
import sys
from typing import Any, Dict, Optional, Sequence, Tuple

sys.dont_write_bytecode = True
import read_capture


SCHEMA = "tsj-canonical-prefix-zero-capacity-v1"
FACTS_SCHEMA = "tsj-canonical-prefix-facts-v1"
INDEX_SCHEMA = "tsj-canonical-prefix-index-v1"
TRANSPORT_SCHEMA = "tsj-canonical-prefix-transport-v1"
CASE_ID = "CANONICAL_SEPARATE_REQ0_PASS2_A_COIL_LOW"
SCOPE = "FIXED_CANONICAL_GRAPH_ZERO_VIA_CONNECTIVITY_ONLY"
WIRE = read_capture.WIRE
MAX_INDEX_BYTES = 65536
MAX_AGGREGATE_BYTES = 8 * 1024 * 1024
MAX_CELLS = 250000
MAX_STATES = MAX_CELLS * 2

_DIRECTIONS = (
    (read_capture.graph_oracle.NORTH, 0, -1, "N"),
    (read_capture.graph_oracle.EAST, 1, 0, "E"),
    (read_capture.graph_oracle.SOUTH, 0, 1, "S"),
    (read_capture.graph_oracle.WEST, -1, 0, "W"),
)


class AuditError(ValueError):
    """Sanitized zero-capacity audit failure."""


def _need(ok: bool, code: str) -> None:
    if not ok:
        raise AuditError(code)


def _int(value: Any, low: int, high: int, code: str) -> int:
    _need(type(value) is int and low <= value <= high, code)
    return value


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _read(path: Path, limit: int) -> bytes:
    try:
        return read_capture.stable_read(path, limit)
    except read_capture.Rejected as error:
        raise AuditError("INPUT_" + str(error)) from None


def _exact_keys(value: Any, names: Tuple[str, ...], code: str) -> None:
    _need(type(value) is dict and set(value) == set(names), code)


def directed_zero_via_bfs(graph: Any) -> Dict[str, Any]:
    """Search directed planar edges only; no TOP/BOTTOM transitions are allowed."""
    _need(type(graph) is read_capture.graph_oracle.FixedPrefixGraph,
          "GRAPH_TYPE_INVALID")
    cells = _int(graph.width * graph.height, 1, MAX_CELLS, "GRID_CELLS")
    state_count = _int(cells * 2, 2, MAX_STATES, "GRAPH_STATES")
    _need(type(graph.steps) is tuple and len(graph.steps) == state_count
          and type(graph.goals) is tuple and len(graph.goals) == state_count
          and type(graph.starts) is tuple and 1 <= len(graph.starts) <= 2,
          "GRAPH_ARRAYS")
    _need(len(set(graph.starts)) == len(graph.starts), "GRAPH_STARTS")
    try:
        seen = bytearray(state_count)
        queue = array("I")
    except (MemoryError, OverflowError):
        raise AuditError("BFS_ALLOCATION") from None
    _need(queue.itemsize == 4, "UINT32_QUEUE_UNSUPPORTED")

    for state in sorted(graph.starts):
        _need(type(state) is int and 0 <= state < state_count, "GRAPH_START_STATE")
        seen[state] = 1
        queue.append(state)
    _need(len(queue) <= state_count, "BFS_QUEUE_BOUND")

    head = 0
    visited = 0
    edge_probes = 0
    goal_tests = 0
    maximum_queue = len(queue)
    found_goal: Optional[int] = None
    while head < len(queue):
        state = queue[head]
        head += 1
        visited += 1
        goal_tests += 1
        if graph.goals[state]:
            found_goal = state
            break

        layer = state // cells
        cell = state % cells
        x, y = cell % graph.width, cell // graph.width
        mask = graph.steps[state]
        for bit, dx, dy, _name in _DIRECTIONS:
            if not (mask & bit):
                continue
            edge_probes += 1
            nx, ny = x + dx, y + dy
            _need(0 <= nx < graph.width and 0 <= ny < graph.height,
                  "GRAPH_EDGE_LEAVES_GRID")
            next_cell = ny * graph.width + nx
            next_state = layer * cells + next_cell
            if not seen[next_state]:
                seen[next_state] = 1
                queue.append(next_state)
                _need(len(queue) <= state_count, "BFS_QUEUE_BOUND")
                if len(queue) - head > maximum_queue:
                    maximum_queue = len(queue) - head

    return {
        "reachable": found_goal is not None,
        "termination": "GOAL_FOUND" if found_goal is not None else "FRONTIER_EXHAUSTED",
        "goalState": found_goal,
        "startStates": list(sorted(graph.starts)),
        "statesVisited": visited,
        "goalTests": goal_tests,
        "directedPlanarEdgesExamined": edge_probes,
        "verticalTransitionsExamined": 0,
        "maximumQueueStates": maximum_queue,
        "stateCapacity": state_count,
        "cellCount": cells,
        "layerEncoding": {"TOP": 0, "BOTTOM": 1},
        "directions": {"N": 1, "E": 2, "S": 4, "W": 8},
        "viaMaskUsedForConnectivity": False,
    }


def _load_capture(run_root: Path, pins_path: Path, pins_sha: str
                  ) -> Tuple[Dict[str, Any], bytes, Dict[str, Any], Dict[str, Any]]:
    try:
        reader_result = read_capture.validate_run(run_root, pins_path, pins_sha, "CAPTURE")
    except read_capture.Rejected as error:
        raise AuditError("CAPTURE_READER_" + str(error)) from None
    except (OSError, ValueError, TypeError, KeyError, IndexError):
        raise AuditError("CAPTURE_READER_FAILED") from None

    _need(type(reader_result) is dict
          and reader_result.get("schema") == read_capture.READER_SCHEMA
          and reader_result.get("status") == "PASS_CAPTURE_DIAGNOSIS_ONLY"
          and reader_result.get("mode") == "CAPTURE"
          and reader_result.get("inputPinsSha256") == pins_sha
          and reader_result.get("layoutQualification") is False,
          "CAPTURE_READER_STATUS")
    run_id = reader_result.get("runId")
    _need(type(run_id) is str and read_capture.UUID.fullmatch(run_id) is not None,
          "CAPTURE_RUN_ID")

    root = read_capture.no_reparse(run_root)
    data = root / "data"
    index_path = data / "canonical-prefix-index.json"
    transport_path = data / "fixed-prefix.jsonl"
    index_raw = _read(index_path, MAX_INDEX_BYTES)
    try:
        index = read_capture.strict_json(index_raw)
    except read_capture.Rejected as error:
        raise AuditError("INDEX_" + str(error)) from None
    _exact_keys(index, (
        "schema", "mode", "profile", "kind", "runId", "sourceManifestSha256",
        "wireSha256", "scope", "layoutQualification", "target", "operation",
        "transport", "selection",
    ), "INDEX_FIELDS")
    _need(index["schema"] == INDEX_SCHEMA and index["mode"] == "CAPTURE"
          and index["profile"] == "BASE" and index["kind"] == "AUTHORED"
          and index["runId"] == run_id and index["sourceManifestSha256"] == pins_sha
          and index["wireSha256"] == WIRE
          and index["scope"] == read_capture.SCOPE
          and index["layoutQualification"] is False, "INDEX_IDENTITY")
    transport = index["transport"]
    _exact_keys(transport, ("path", "bytes", "sha256", "graph", "facts"),
                "INDEX_TRANSPORT_FIELDS")
    _need(transport["path"] == "fixed-prefix.jsonl", "INDEX_TRANSPORT_PATH")
    transport_size = _int(transport["bytes"], 1, MAX_AGGREGATE_BYTES,
                          "INDEX_TRANSPORT_BYTES")
    digest = transport["sha256"]
    _need(type(digest) is str and read_capture.HEX.fullmatch(digest) is not None,
          "INDEX_TRANSPORT_SHA")

    raw = _read(transport_path, MAX_AGGREGATE_BYTES)
    _need(len(raw) == transport_size and _sha(raw) == digest, "TRANSPORT_INDEX_BINDING")
    try:
        decoded = read_capture.decode_transport(raw, parse_payloads=True)
    except read_capture.Rejected as error:
        raise AuditError("TRANSPORT_" + str(error)) from None
    header, facts, graph = decoded["header"], decoded["facts"], decoded["graph"]
    _need(header["schema"] == TRANSPORT_SCHEMA and header["runId"] == run_id
          and header["sourceManifestSha256"] == pins_sha
          and header["wireSha256"] == WIRE and header["scope"] == read_capture.SCOPE,
          "TRANSPORT_IDENTITY")
    _need(type(facts) is dict and facts.get("schema") == FACTS_SCHEMA
          and facts.get("runId") == run_id
          and facts.get("sourceManifestSha256") == pins_sha
          and facts.get("wireSha256") == WIRE and facts.get("caseId") == CASE_ID,
          "FACTS_IDENTITY")
    _need(type(graph) is read_capture.graph_oracle.FixedPrefixGraph
          and graph.case_id == CASE_ID, "GRAPH_CASE_ID")
    capacity = {
        "maximumHoles": 48,
        "remainingHoles": 0,
        "prefixHoleCount": 48,
        "prefixViaCount": 48,
        "maximumBranchTransitions": 4,
        "remainingBranchTransitionsFromPrefix": 0,
    }
    _need(facts.get("viaMaskSemantics") ==
          "RELAXED_GEOMETRY_OMITS_REMAINING_HOLE_CAP",
          "FACTS_VIA_MASK_SEMANTICS")
    for key, expected in capacity.items():
        _need(type(facts.get(key)) is int and facts[key] == expected,
              "FACTS_ZERO_CAPACITY")

    graph_descriptor = transport["graph"]
    _exact_keys(graph_descriptor, ("bytes", "sha256"), "INDEX_GRAPH_DESCRIPTOR")
    graph_bytes = decoded["graphBytes"]
    _need(_int(graph_descriptor["bytes"], 1, MAX_AGGREGATE_BYTES,
               "INDEX_GRAPH_BYTES") == len(graph_bytes)
          and graph_descriptor["sha256"] == _sha(graph_bytes),
          "INDEX_GRAPH_BINDING")

    # Ensure the indexed files did not change while the independent search ran.
    return reader_result, raw, {
        "indexPath": index_path, "indexRaw": index_raw,
        "transportPath": transport_path, "transportRaw": raw,
        "graphBytes": graph_bytes,
    }, decoded


def run_audit(run_root: Any, pins_path: Any, pins_sha: str) -> Dict[str, Any]:
    """Require full CAPTURE validation, then independently run zero-VIA BFS."""
    _need(type(pins_sha) is str and read_capture.HEX.fullmatch(pins_sha) is not None,
          "INPUT_SHA")
    root = read_capture.no_reparse(run_root)
    pins = read_capture.no_reparse(pins_path)
    reader_result, raw, files, decoded = _load_capture(root, pins, pins_sha)
    graph = decoded["graph"]
    bfs = directed_zero_via_bfs(graph)

    qualified = reader_result.get("graphDiagnosis")
    _need(type(qualified) is dict
          and qualified.get("schema") == read_capture.via_oracle.RESULT_SCHEMA,
          "QUALIFICATION_MIN_VIA_RESULT")
    min_via_count = qualified.get("minimumViaCount")
    _need(min_via_count is None or
          (type(min_via_count) is int and min_via_count >= 0),
          "QUALIFICATION_MIN_VIA_TYPE")
    recalculated = read_capture.zero_via_result(graph)
    for key in ("schema", "status", "minimumViaCount", "termination", "reachable",
                "claim", "productionFeasibility", "acceptance"):
        _need(qualified.get(key) == recalculated.get(key),
              "QUALIFICATION_MIN_VIA_BINDING")
    via_zero = min_via_count == 0
    _need(bfs["reachable"] is via_zero, "ZERO_VIA_ORACLE_DISAGREEMENT")

    pins_raw = _read(pins, 1024 * 1024)
    _need(_sha(pins_raw) == pins_sha, "PINS_CHANGED")
    _need(_read(files["indexPath"], MAX_INDEX_BYTES) == files["indexRaw"]
          and _read(files["transportPath"], MAX_AGGREGATE_BYTES) == raw,
          "CAPTURE_CHANGED_DURING_AUDIT")

    graph_raw = files["graphBytes"]
    return {
        "schema": SCHEMA,
        "status": "PASS_ZERO_VIA_DIAGNOSIS_ONLY",
        "mode": "CAPTURE",
        "runId": reader_result["runId"],
        "inputPinsSha256": pins_sha,
        "scope": SCOPE,
        "layoutQualification": False,
        "graph": {
            "caseId": graph.case_id,
            "bytes": len(graph_raw),
            "sha256": _sha(graph_raw),
            "graphSha256": graph.graph_sha256,
            "width": graph.width,
            "height": graph.height,
            "cellCount": graph.cells,
            "stateCount": graph.state_count,
        },
        "capacityFacts": {
            "prefixHoleCount": 48,
            "prefixViaCount": 48,
            "remainingHoles": 0,
            "viaMaskSemantics": "RELAXED_GEOMETRY_OMITS_REMAINING_HOLE_CAP",
        },
        "zeroViaBfs": bfs,
        "qualificationMinimumVia": {
            "schema": qualified["schema"],
            "status": qualified["status"],
            "minimumViaCount": min_via_count,
            "termination": qualified["termination"],
            "reachable": qualified["reachable"],
            "claim": qualified["claim"],
            "productionFeasibility": "UNKNOWN",
        },
        "agreement": {
            "zeroViaBfsReachable": bfs["reachable"],
            "minimumViaCountIsZero": via_zero,
            "exactAgreement": True,
        },
        "productionFeasibility": "UNKNOWN",
        "acceptance": {
            "actualCaptureAttested": False,
            "route": False,
            "layout": False,
            "electrical": False,
            "q100": False,
        },
    }


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) != 3:
        result = {"schema": SCHEMA, "status": "REJECTED",
                  "error": "ARGUMENT_COUNT_INVALID",
                  "acceptance": {"actualCaptureAttested": False, "route": False,
                                 "layout": False, "electrical": False, "q100": False}}
        print(json.dumps(result, sort_keys=True, separators=(",", ":")))
        return 2
    try:
        result = run_audit(args[0], args[1], args[2])
    except AuditError as error:
        result = {"schema": SCHEMA, "status": "REJECTED", "error": str(error),
                  "acceptance": {"actualCaptureAttested": False, "route": False,
                                 "layout": False, "electrical": False, "q100": False}}
        print(json.dumps(result, sort_keys=True, separators=(",", ":")))
        return 2
    except (OSError, ValueError, TypeError, KeyError, IndexError):
        result = {"schema": SCHEMA, "status": "REJECTED",
                  "error": "VALIDATION_FAILED",
                  "acceptance": {"actualCaptureAttested": False, "route": False,
                                 "layout": False, "electrical": False, "q100": False}}
        print(json.dumps(result, sort_keys=True, separators=(",", ":")))
        return 2
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
