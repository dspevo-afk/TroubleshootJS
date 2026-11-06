#!/usr/bin/env python3
"""Strict codec and independent oracle for generic P07 fixed-prefix graphs.

This module deliberately knows nothing about PCB routing predicates.  It accepts
only the frozen, bounded graph format and reports shortest paths in that graph's
history-relaxed two-face model.  Its output never attests a capture or accepts a
layout, electrical design, or Q100 board.
"""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import heapq
import json
import os
import re
import stat
import sys
from typing import Any, Dict, List, Optional, Sequence, Tuple


GRAPH_PROTOCOL = "p07-fixed-prefix-graph-v1"
GRAPH_MODEL = "DIRECTED_TWO_FACE_HISTORY_RELAXED"
RESULT_SCHEMA = "p07-fixed-prefix-graph-oracle-result-v1"
MAX_INPUT_BYTES = 8_388_608
MAX_CELLS = 250_000
WITNESS_PREFIX_STATES = 64

PRIMARY_STEP_COST = 10
SECONDARY_STEP_COST = 12
SECONDARY_START_COST = 2
VIA_COST = 60

NORTH = 1
EAST = 2
SOUTH = 4
WEST = 8
_DIRECTIONS = (
    (NORTH, 0, -1, "STEP_NORTH"),
    (EAST, 1, 0, "STEP_EAST"),
    (SOUTH, 0, 1, "STEP_SOUTH"),
    (WEST, -1, 0, "STEP_WEST"),
)
_GRAPH_KEYS = frozenset(
    ("protocol", "model", "caseId", "width", "height", "primary",
     "steps", "vias", "goals", "starts")
)
_CASE_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,127}\Z", re.ASCII)


class GraphInputError(ValueError):
    """A sanitized input failure.  `code` never contains input data or a path."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


class _DuplicateKey(Exception):
    pass


class _NonFiniteNumber(Exception):
    pass


def _reject_duplicate_keys(pairs: Sequence[Tuple[str, Any]]) -> Dict[str, Any]:
    result: Dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise _DuplicateKey
        result[key] = value
    return result


def _reject_constant(_value: str) -> None:
    raise _NonFiniteNumber


def _canonical_json_bytes(value: Any) -> bytes:
    return json.dumps(
        value,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=True,
        allow_nan=False,
    ).encode("ascii")


@dataclass(frozen=True)
class FixedPrefixGraph:
    protocol: str
    model: str
    case_id: str
    width: int
    height: int
    primary: int
    steps: Tuple[int, ...]
    vias: Tuple[bool, ...]
    goals: Tuple[bool, ...]
    starts: Tuple[int, ...]
    graph_sha256: str

    @property
    def cells(self) -> int:
        return self.width * self.height

    @property
    def state_count(self) -> int:
        return self.cells * 2


@dataclass(frozen=True)
class OracleResult:
    graph: FixedPrefixGraph
    reachable: bool
    minimum_cost: Optional[int]
    start_state: Optional[int]
    goal_state: Optional[int]
    witness_states: Tuple[int, ...]
    witness_edge_kinds: Tuple[str, ...]
    witness_sha256: Optional[str]
    settled_states: int
    heap_pops: int
    ordinary_edges_examined: int
    via_checks: int
    strict_relaxations: int

    def to_record(self) -> Dict[str, Any]:
        witness: Optional[Dict[str, Any]]
        if not self.reachable:
            witness = None
        else:
            state_prefix = self.witness_states[:WITNESS_PREFIX_STATES]
            edge_prefix = self.witness_edge_kinds[:max(0, len(state_prefix) - 1)]
            witness = {
                "startState": self.start_state,
                "goalState": self.goal_state,
                "stateCount": len(self.witness_states),
                "edgeCount": len(self.witness_edge_kinds),
                "statePrefix": list(state_prefix),
                "edgeKindPrefix": list(edge_prefix),
                "prefixTruncated": len(state_prefix) < len(self.witness_states),
                "sha256": self.witness_sha256,
            }
        return {
            "schema": RESULT_SCHEMA,
            "protocol": self.graph.protocol,
            "model": self.graph.model,
            "caseId": self.graph.case_id,
            "graphSha256": self.graph.graph_sha256,
            "status": "REACHABLE" if self.reachable else "UNREACHABLE",
            "reachable": self.reachable,
            "minimumRelaxedCost": self.minimum_cost,
            "search": {
                "termination": "FIRST_GOAL_SETTLED" if self.reachable else "QUEUE_EXHAUSTED",
                "fullReachabilityCensus": not self.reachable,
            },
            "counters": {
                "settledStatesBeforeTermination": self.settled_states,
                "heapPopsBeforeTermination": self.heap_pops,
                "ordinaryEdgesExaminedBeforeTermination": self.ordinary_edges_examined,
                "viaChecksBeforeTermination": self.via_checks,
                "strictRelaxationsBeforeTermination": self.strict_relaxations,
            },
            "witness": witness,
            "acceptance": {
                "actualCaptureAttested": False,
                "layout": False,
                "electrical": False,
                "q100": False,
            },
        }


def _require_int(value: Any, code: str) -> int:
    if type(value) is not int:
        raise GraphInputError(code)
    return value


def validate_payload(payload: Any) -> FixedPrefixGraph:
    """Validate and freeze one exact-key graph payload."""
    if type(payload) is not dict or frozenset(payload.keys()) != _GRAPH_KEYS:
        raise GraphInputError("GRAPH_KEYS_INVALID")

    if payload["protocol"] != GRAPH_PROTOCOL or type(payload["protocol"]) is not str:
        raise GraphInputError("PROTOCOL_INVALID")
    if payload["model"] != GRAPH_MODEL or type(payload["model"]) is not str:
        raise GraphInputError("MODEL_INVALID")

    case_id = payload["caseId"]
    if type(case_id) is not str or not _CASE_ID.fullmatch(case_id):
        raise GraphInputError("CASE_ID_INVALID")

    width = _require_int(payload["width"], "DIMENSIONS_INVALID")
    height = _require_int(payload["height"], "DIMENSIONS_INVALID")
    if width < 1 or height < 1 or width * height > MAX_CELLS:
        raise GraphInputError("GRID_SIZE_INVALID")
    cells = width * height

    primary = _require_int(payload["primary"], "PRIMARY_INVALID")
    if primary not in (0, 1):
        raise GraphInputError("PRIMARY_INVALID")

    raw_steps = payload["steps"]
    if type(raw_steps) is not list or len(raw_steps) != cells * 2:
        raise GraphInputError("STEPS_INVALID")
    steps: List[int] = []
    for mask in raw_steps:
        value = _require_int(mask, "STEPS_INVALID")
        if value < 0 or value > 15:
            raise GraphInputError("STEPS_INVALID")
        steps.append(value)
    for state, mask in enumerate(steps):
        cell = state % cells
        x, y = cell % width, cell // width
        if ((y == 0 and mask & NORTH) or
                (x == width - 1 and mask & EAST) or
                (y == height - 1 and mask & SOUTH) or
                (x == 0 and mask & WEST)):
            raise GraphInputError("STEP_LEAVES_GRID")

    raw_vias = payload["vias"]
    if type(raw_vias) is not list or len(raw_vias) != cells:
        raise GraphInputError("VIAS_INVALID")
    if any(type(value) is not bool for value in raw_vias):
        raise GraphInputError("VIAS_INVALID")

    raw_goals = payload["goals"]
    if type(raw_goals) is not list or len(raw_goals) != cells * 2:
        raise GraphInputError("GOALS_INVALID")
    if any(type(value) is not bool for value in raw_goals):
        raise GraphInputError("GOALS_INVALID")

    raw_starts = payload["starts"]
    if type(raw_starts) is not list or len(raw_starts) not in (1, 2):
        raise GraphInputError("STARTS_INVALID")
    starts: List[int] = []
    for state in raw_starts:
        value = _require_int(state, "STARTS_INVALID")
        if value < 0 or value >= cells * 2:
            raise GraphInputError("STARTS_INVALID")
        starts.append(value)
    if len(set(starts)) != len(starts):
        raise GraphInputError("STARTS_INVALID")

    canonical_payload = {
        "protocol": GRAPH_PROTOCOL,
        "model": GRAPH_MODEL,
        "caseId": case_id,
        "width": width,
        "height": height,
        "primary": primary,
        "steps": list(steps),
        "vias": list(raw_vias),
        "goals": list(raw_goals),
        "starts": list(starts),
    }
    digest = hashlib.sha256(_canonical_json_bytes(canonical_payload)).hexdigest()
    return FixedPrefixGraph(
        protocol=GRAPH_PROTOCOL,
        model=GRAPH_MODEL,
        case_id=case_id,
        width=width,
        height=height,
        primary=primary,
        steps=tuple(steps),
        vias=tuple(raw_vias),
        goals=tuple(raw_goals),
        starts=tuple(starts),
        graph_sha256=digest,
    )


def parse_graph_bytes(raw: bytes) -> FixedPrefixGraph:
    """Parse a bounded JSON payload, accepting one leading UTF-8 BOM."""
    if type(raw) is not bytes:
        raise GraphInputError("INPUT_BYTES_INVALID")
    if len(raw) > MAX_INPUT_BYTES:
        raise GraphInputError("INPUT_TOO_LARGE")
    try:
        text = raw.decode("utf-8-sig", errors="strict")
    except UnicodeDecodeError:
        raise GraphInputError("INPUT_ENCODING_INVALID") from None
    try:
        payload = json.loads(
            text,
            object_pairs_hook=_reject_duplicate_keys,
            parse_constant=_reject_constant,
        )
    except _DuplicateKey:
        raise GraphInputError("DUPLICATE_JSON_KEY") from None
    except _NonFiniteNumber:
        raise GraphInputError("NONFINITE_JSON_NUMBER") from None
    except (json.JSONDecodeError, RecursionError, ValueError):
        raise GraphInputError("JSON_INVALID") from None
    return validate_payload(payload)


def _witness_digest(states: Sequence[int], edge_kinds: Sequence[str], cost: int) -> str:
    value = {
        "states": list(states),
        "edgeKinds": list(edge_kinds),
        "cost": cost,
    }
    return hashlib.sha256(_canonical_json_bytes(value)).hexdigest()


def analyze_graph(graph: FixedPrefixGraph) -> OracleResult:
    """Run deterministic independent Dijkstra on the immutable encoded graph."""
    if type(graph) is not FixedPrefixGraph:
        raise TypeError("graph must be a validated FixedPrefixGraph")

    state_count = graph.state_count
    cells = graph.cells
    infinity = 1 << 60
    distance = [infinity] * state_count
    previous = [-1] * state_count
    previous_edge: List[Optional[str]] = [None] * state_count
    queue: List[Tuple[int, int]] = []

    for state in sorted(graph.starts):
        layer = state // cells
        initial_cost = 0 if layer == graph.primary else SECONDARY_START_COST
        if initial_cost < distance[state]:
            distance[state] = initial_cost
            heapq.heappush(queue, (initial_cost, state))

    heap_pops = 0
    settled_states = 0
    ordinary_edges_examined = 0
    via_checks = 0
    strict_relaxations = 0
    final_goal: Optional[int] = None

    while queue:
        current_cost, state = heapq.heappop(queue)
        heap_pops += 1
        if current_cost != distance[state]:
            continue
        settled_states += 1
        if graph.goals[state]:
            final_goal = state
            break

        layer = state // cells
        cell = state % cells
        x, y = cell % graph.width, cell // graph.width
        mask = graph.steps[state]
        step_cost = PRIMARY_STEP_COST if layer == graph.primary else SECONDARY_STEP_COST
        for bit, dx, dy, edge_kind in _DIRECTIONS:
            if not (mask & bit):
                continue
            ordinary_edges_examined += 1
            neighbor = (y + dy) * graph.width + (x + dx)
            next_state = layer * cells + neighbor
            candidate = current_cost + step_cost
            if candidate < distance[next_state]:
                distance[next_state] = candidate
                previous[next_state] = state
                previous_edge[next_state] = edge_kind
                strict_relaxations += 1
                heapq.heappush(queue, (candidate, next_state))

        via_checks += 1
        if graph.vias[cell]:
            next_state = (1 - layer) * cells + cell
            candidate = current_cost + VIA_COST
            if candidate < distance[next_state]:
                distance[next_state] = candidate
                previous[next_state] = state
                previous_edge[next_state] = "VIA"
                strict_relaxations += 1
                heapq.heappush(queue, (candidate, next_state))

    if final_goal is None:
        return OracleResult(
            graph=graph,
            reachable=False,
            minimum_cost=None,
            start_state=None,
            goal_state=None,
            witness_states=tuple(),
            witness_edge_kinds=tuple(),
            witness_sha256=None,
            settled_states=settled_states,
            heap_pops=heap_pops,
            ordinary_edges_examined=ordinary_edges_examined,
            via_checks=via_checks,
            strict_relaxations=strict_relaxations,
        )

    reverse_states = [final_goal]
    reverse_edges: List[str] = []
    current = final_goal
    while previous[current] != -1:
        edge_kind = previous_edge[current]
        if edge_kind is None:
            raise RuntimeError("internal witness predecessor is incomplete")
        reverse_edges.append(edge_kind)
        current = previous[current]
        reverse_states.append(current)
        if len(reverse_states) > state_count:
            raise RuntimeError("internal witness contains a cycle")
    states = tuple(reversed(reverse_states))
    edge_kinds = tuple(reversed(reverse_edges))
    cost = distance[final_goal]
    return OracleResult(
        graph=graph,
        reachable=True,
        minimum_cost=cost,
        start_state=states[0],
        goal_state=final_goal,
        witness_states=states,
        witness_edge_kinds=edge_kinds,
        witness_sha256=_witness_digest(states, edge_kinds, cost),
        settled_states=settled_states,
        heap_pops=heap_pops,
        ordinary_edges_examined=ordinary_edges_examined,
        via_checks=via_checks,
        strict_relaxations=strict_relaxations,
    )


def _error_record(status: str, code: str) -> Dict[str, Any]:
    return {
        "schema": RESULT_SCHEMA,
        "status": status,
        "error": code,
        "acceptance": {
            "actualCaptureAttested": False,
            "layout": False,
            "electrical": False,
            "q100": False,
        },
    }


def _write_record(record: Dict[str, Any]) -> None:
    sys.stdout.write(_canonical_json_bytes(record).decode("ascii") + "\n")


def main(argv: Optional[Sequence[str]] = None) -> int:
    """CLI: one graph-file argument; output exactly one sanitized JSON line."""
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) != 1:
        _write_record(_error_record("ERROR", "ARGUMENT_COUNT_INVALID"))
        return 2
    try:
        if not stat.S_ISREG(os.stat(args[0]).st_mode):
            _write_record(_error_record("ERROR", "INPUT_UNAVAILABLE"))
            return 2
        with open(args[0], "rb") as source:
            raw = source.read(MAX_INPUT_BYTES + 1)
    except (OSError, ValueError):
        _write_record(_error_record("ERROR", "INPUT_UNAVAILABLE"))
        return 2
    try:
        graph = parse_graph_bytes(raw)
    except GraphInputError as error:
        _write_record(_error_record("REJECTED", error.code))
        return 1
    result = analyze_graph(graph)
    _write_record(result.to_record())
    return 0


if __name__ == "__main__":
    sys.exit(main())
