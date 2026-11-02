#!/usr/bin/env python3
"""Bounded descriptive metrics for a relaxed fixed-prefix graph witness.

This module consumes the pinned graph codec/oracle result without changing its
search.  The history checks below describe a few production predicates only;
they do not replay the production scheduler or attest a route/layout.
"""

from __future__ import annotations

import re
from typing import Any, Dict, List, Optional, Tuple

import read_fixed_prefix_graph as oracle


METRICS_SCHEMA = "p07-fixed-prefix-witness-metrics-v1"
TURN_PENALTY = 35
VIA_SPACING_OPEN_SQUARE = 20
MAX_GRID_PITCH = 1_000_000
MAX_SPACING_VIAS_TO_COMPARE = 49

_SHA256 = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
_DIRECTIONS = {
    "STEP_NORTH": (0, -1, oracle.NORTH),
    "STEP_EAST": (1, 0, oracle.EAST),
    "STEP_SOUTH": (0, 1, oracle.SOUTH),
    "STEP_WEST": (-1, 0, oracle.WEST),
}
_ERROR_CODES = frozenset((
    "GRAPH_INVALID",
    "RESULT_INVALID",
    "RESULT_GRAPH_MISMATCH",
    "RESULT_UNREACHABLE_INVALID",
    "WITNESS_INVALID",
    "WITNESS_ENDPOINT_INVALID",
    "WITNESS_EDGE_INVALID",
    "WITNESS_COST_INVALID",
    "WITNESS_COST_MISMATCH",
    "WITNESS_DIGEST_INVALID",
    "WITNESS_DIGEST_MISMATCH",
    "PREFIX_HOLE_COUNT_INVALID",
    "MAXIMUM_VIAS_INVALID",
    "MAX_TRANSITIONS_INVALID",
    "GRID_PITCH_INVALID",
))


class WitnessMetricError(ValueError):
    """A sanitized metrics-input failure with a static code only."""

    def __init__(self, code: str):
        if type(code) is not str or code not in _ERROR_CODES:
            code = "WITNESS_INVALID"
        self.code = code
        super().__init__(code)


def _bounded_int(value: Any, code: str, minimum: int, maximum: int) -> int:
    if type(value) is not int or value < minimum or value > maximum:
        raise WitnessMetricError(code)
    return value


def _validate_graph(graph: Any) -> oracle.FixedPrefixGraph:
    if type(graph) is not oracle.FixedPrefixGraph:
        raise WitnessMetricError("GRAPH_INVALID")
    if (type(graph.width) is not int or type(graph.height) is not int or
            graph.width < 1 or graph.height < 1 or
            graph.width * graph.height > oracle.MAX_CELLS or
            type(graph.primary) is not int or graph.primary not in (0, 1) or
            type(graph.steps) is not tuple or
            type(graph.vias) is not tuple or
            type(graph.goals) is not tuple or
            type(graph.starts) is not tuple or
            len(graph.steps) != graph.cells * 2 or
            len(graph.vias) != graph.cells or
            len(graph.goals) != graph.cells * 2 or
            len(graph.starts) not in (1, 2) or
            any(type(mask) is not int or mask < 0 or mask > 15
                for mask in graph.steps) or
            any(type(value) is not bool for value in graph.vias) or
            any(type(value) is not bool for value in graph.goals) or
            any(type(value) is not int or value < 0 or value >= graph.state_count
                for value in graph.starts) or
            type(graph.graph_sha256) is not str or
            _SHA256.fullmatch(graph.graph_sha256) is None):
        raise WitnessMetricError("GRAPH_INVALID")
    try:
        verified = oracle.validate_payload({
            "protocol": graph.protocol,
            "model": graph.model,
            "caseId": graph.case_id,
            "width": graph.width,
            "height": graph.height,
            "primary": graph.primary,
            "steps": list(graph.steps),
            "vias": list(graph.vias),
            "goals": list(graph.goals),
            "starts": list(graph.starts),
        })
    except (oracle.GraphInputError, TypeError, ValueError):
        raise WitnessMetricError("GRAPH_INVALID") from None
    if verified != graph:
        raise WitnessMetricError("GRAPH_INVALID")
    return graph


def _validate_result(graph: oracle.FixedPrefixGraph,
                     result: Any) -> Tuple[bool, Optional[int], Tuple[int, ...],
                                           Tuple[str, ...], Optional[int],
                                           Optional[int], Optional[str]]:
    if type(result) is not oracle.OracleResult:
        raise WitnessMetricError("RESULT_INVALID")
    if result.graph is not graph and result.graph != graph:
        raise WitnessMetricError("RESULT_GRAPH_MISMATCH")
    if type(result.reachable) is not bool:
        raise WitnessMetricError("RESULT_INVALID")

    if not result.reachable:
        if (result.minimum_cost is not None or result.start_state is not None or
                result.goal_state is not None or
                type(result.witness_states) is not tuple or result.witness_states or
                type(result.witness_edge_kinds) is not tuple or
                result.witness_edge_kinds or result.witness_sha256 is not None):
            raise WitnessMetricError("RESULT_UNREACHABLE_INVALID")
        return False, None, tuple(), tuple(), None, None, None

    if (type(result.witness_states) is not tuple or
            type(result.witness_edge_kinds) is not tuple or
            not result.witness_states or
            len(result.witness_states) > graph.state_count or
            len(result.witness_edge_kinds) + 1 != len(result.witness_states) or
            any(type(state) is not int or state < 0 or state >= graph.state_count
                for state in result.witness_states) or
            any(type(kind) is not str for kind in result.witness_edge_kinds)):
        raise WitnessMetricError("WITNESS_INVALID")
    if (type(result.start_state) is not int or
            type(result.goal_state) is not int or
            result.start_state != result.witness_states[0] or
            result.goal_state != result.witness_states[-1] or
            result.start_state not in graph.starts or
            not graph.goals[result.goal_state]):
        raise WitnessMetricError("WITNESS_ENDPOINT_INVALID")
    if type(result.minimum_cost) is not int or result.minimum_cost < 0:
        raise WitnessMetricError("WITNESS_COST_INVALID")
    if (type(result.witness_sha256) is not str or
            _SHA256.fullmatch(result.witness_sha256) is None):
        raise WitnessMetricError("WITNESS_DIGEST_INVALID")

    cells = graph.cells
    cost = (0 if result.start_state // cells == graph.primary
            else oracle.SECONDARY_START_COST)
    for edge_index, kind in enumerate(result.witness_edge_kinds):
        source = result.witness_states[edge_index]
        target = result.witness_states[edge_index + 1]
        source_layer, source_cell = divmod(source, cells)
        target_layer, target_cell = divmod(target, cells)
        if kind == "VIA":
            if (source_layer == target_layer or source_cell != target_cell or
                    not graph.vias[source_cell]):
                raise WitnessMetricError("WITNESS_EDGE_INVALID")
            cost += oracle.VIA_COST
            continue

        direction = _DIRECTIONS.get(kind)
        if direction is None or source_layer != target_layer:
            raise WitnessMetricError("WITNESS_EDGE_INVALID")
        dx, dy, bit = direction
        source_x, source_y = source_cell % graph.width, source_cell // graph.width
        target_x, target_y = target_cell % graph.width, target_cell // graph.width
        if ((target_x - source_x, target_y - source_y) != (dx, dy) or
                not graph.steps[source] & bit):
            raise WitnessMetricError("WITNESS_EDGE_INVALID")
        cost += (oracle.PRIMARY_STEP_COST if source_layer == graph.primary
                 else oracle.SECONDARY_STEP_COST)

    if cost != result.minimum_cost:
        raise WitnessMetricError("WITNESS_COST_MISMATCH")
    if oracle._witness_digest(result.witness_states,
                              result.witness_edge_kinds,
                              result.minimum_cost) != result.witness_sha256:
        raise WitnessMetricError("WITNESS_DIGEST_MISMATCH")

    return (True, result.minimum_cost, result.witness_states,
            result.witness_edge_kinds, result.start_state, result.goal_state,
            result.witness_sha256)


def _edge_example(index: int, states: Tuple[int, ...], kinds: Tuple[str, ...]
                  ) -> Dict[str, Any]:
    return {
        "edgeIndex": index,
        "kind": kinds[index],
        "fromState": states[index],
        "toState": states[index + 1],
    }


def _no_witness_history(maximum_vias: int, max_transitions: int,
                        prefix_hole_count: int, grid_pitch: int
                        ) -> Dict[str, Any]:
    no_witness = {"applicable": False, "conforms": None,
                  "firstViolation": None}
    return {
        "evaluation": "NO_WITNESS",
        "maximumVias": maximum_vias,
        "maxTransitions": max_transitions,
        "prefixHoleCount": prefix_hole_count,
        "gridPitch": grid_pitch,
        "transitionLimit": dict(no_witness, observedViaCount=0),
        "noInitialVia": dict(no_witness),
        "noConsecutiveVia": dict(no_witness),
        "viaHoleBudget": dict(no_witness, checkedViaCount=0),
        "viaSpacing": {
            "applicable": False,
            "conforms": None,
            "coordinateSpace": "GRID_ORIGIN_RELATIVE_UNITS",
            "minimumOpenSquareUnits": VIA_SPACING_OPEN_SQUARE,
            "gridPitch": grid_pitch,
            "totalViaCount": 0,
            "checkedViaCount": 0,
            "totalPairChecks": 0,
            "checkedPairCount": 0,
            "complete": False,
            "firstViolation": None,
        },
        "allApplicableChecksConform": None,
    }


def summarize_witness(graph: oracle.FixedPrefixGraph,
                      result: oracle.OracleResult, *,
                      prefix_hole_count: int,
                      maximum_vias: int = 48,
                      max_transitions: int = 4,
                      grid_pitch: int = 10) -> Dict[str, Any]:
    """Return bounded JSON-safe metrics for one full oracle witness.

    ``prefix_hole_count`` is the number of all hole kinds already present in
    the fixed prefix. The spacing calculation compares no more than the first
    49 VIA edges, keeping pairwise work bounded even for a malformed or unusually
    long witness. If more vias exist, it marks spacing incomplete unless a
    violation is already found in that examined prefix.

    Spacing coordinates are ``cell_index * grid_pitch`` in
    ``GRID_ORIGIN_RELATIVE_UNITS``; no capture grid origin is applied. Spacing
    uses coordinate deltas, which are unchanged by translation.
    """
    graph = _validate_graph(graph)
    prefix_hole_count = _bounded_int(
        prefix_hole_count, "PREFIX_HOLE_COUNT_INVALID", 0, oracle.MAX_CELLS)
    maximum_vias = _bounded_int(
        maximum_vias, "MAXIMUM_VIAS_INVALID", 0, oracle.MAX_CELLS)
    max_transitions = _bounded_int(
        max_transitions, "MAX_TRANSITIONS_INVALID", 0, oracle.MAX_CELLS)
    grid_pitch = _bounded_int(grid_pitch, "GRID_PITCH_INVALID", 1,
                              MAX_GRID_PITCH)

    (reachable, minimum_cost, states, edges, start_state, goal_state,
     witness_sha256) = _validate_result(graph, result)
    if not reachable:
        return {
            "schema": METRICS_SCHEMA,
            "caseId": graph.case_id,
            "graphSha256": graph.graph_sha256,
            "witness": {
                "reachable": False,
                "stateCount": 0,
                "edgeCount": 0,
                "stepCount": 0,
                "viaCount": 0,
                "minimumRelaxedCost": None,
                "planarTurnCount": 0,
                "turnPenalty": None,
                "productionStyleCost": None,
                "sha256": None,
            },
            "historySubset": _no_witness_history(
                maximum_vias, max_transitions, prefix_hole_count, grid_pitch),
            "productionFeasibility": "UNKNOWN",
            "acceptance": {
                "actualCaptureAttested": False,
                "route": False,
                "layout": False,
                "electrical": False,
                "q100": False,
            },
        }

    step_count = 0
    via_count = 0
    planar_turn_count = 0
    previous_planar_direction: Optional[str] = None
    via_edges: List[Tuple[int, int, int, int, int]] = []
    first_initial_via: Optional[Dict[str, Any]] = None
    first_consecutive_via: Optional[Dict[str, Any]] = None
    first_transition_overflow: Optional[Dict[str, Any]] = None
    first_hole_overflow: Optional[Dict[str, Any]] = None
    first_spacing_violation: Optional[Dict[str, Any]] = None

    spacing_vias: List[Tuple[int, int, int, int, int]] = []
    checked_pair_count = 0
    for edge_index, kind in enumerate(edges):
        if kind == "VIA":
            via_count += 1
            if edge_index == 0 and first_initial_via is None:
                first_initial_via = _edge_example(edge_index, states, edges)
            if edge_index > 0 and edges[edge_index - 1] == "VIA" and \
                    first_consecutive_via is None:
                first_consecutive_via = _edge_example(edge_index, states, edges)
            if via_count == max_transitions + 1 and first_transition_overflow is None:
                first_transition_overflow = _edge_example(edge_index, states, edges)
            if (prefix_hole_count + via_count > maximum_vias and
                    first_hole_overflow is None):
                violation = _edge_example(edge_index, states, edges)
                violation["holeCountAfterAddition"] = prefix_hole_count + via_count
                first_hole_overflow = violation

            cell = states[edge_index] % graph.cells
            x = (cell % graph.width) * grid_pitch
            y = (cell // graph.width) * grid_pitch
            entry = (edge_index, states[edge_index], states[edge_index + 1], x, y)
            if len(spacing_vias) < MAX_SPACING_VIAS_TO_COMPARE:
                for previous in spacing_vias:
                    checked_pair_count += 1
                    previous_edge, previous_from, previous_to, previous_x, previous_y = previous
                    if (first_spacing_violation is None and
                            abs(x - previous_x) < VIA_SPACING_OPEN_SQUARE and
                            abs(y - previous_y) < VIA_SPACING_OPEN_SQUARE):
                        first_spacing_violation = {
                            "firstEdgeIndex": previous_edge,
                            "firstFromState": previous_from,
                            "firstToState": previous_to,
                            "firstCoordinate": {"x": previous_x, "y": previous_y},
                            "secondEdgeIndex": edge_index,
                            "secondFromState": states[edge_index],
                            "secondToState": states[edge_index + 1],
                            "secondCoordinate": {"x": x, "y": y},
                        }
                spacing_vias.append(entry)
            previous_planar_direction = None
        else:
            step_count += 1
            if (previous_planar_direction is not None and
                    kind != previous_planar_direction):
                planar_turn_count += 1
            previous_planar_direction = kind

    total_pair_checks = via_count * (via_count - 1) // 2
    spacing_complete = via_count <= MAX_SPACING_VIAS_TO_COMPARE
    spacing_conforms: Optional[bool]
    if first_spacing_violation is not None:
        spacing_conforms = False
    elif spacing_complete:
        spacing_conforms = True
    else:
        spacing_conforms = None

    initial_conforms = first_initial_via is None
    consecutive_conforms = first_consecutive_via is None
    transition_conforms = via_count <= max_transitions
    hole_applicable = via_count > 0
    hole_conforms: Optional[bool] = (
        first_hole_overflow is None if hole_applicable else None)
    history_checks = (transition_conforms, initial_conforms,
                      consecutive_conforms, spacing_conforms)
    if hole_applicable:
        history_checks += (hole_conforms,)
    all_applicable_conform = (
        False if any(value is False for value in history_checks)
        else None if any(value is None for value in history_checks)
        else True
    )

    turn_penalty = planar_turn_count * TURN_PENALTY
    if minimum_cost is None:
        raise WitnessMetricError("WITNESS_COST_INVALID")
    history_subset = {
        "evaluation": "WITNESS_CHECKED",
        "maximumVias": maximum_vias,
        "maxTransitions": max_transitions,
        "prefixHoleCount": prefix_hole_count,
        "gridPitch": grid_pitch,
        "transitionLimit": {
            "applicable": True,
            "observedViaCount": via_count,
            "maximum": max_transitions,
            "conforms": transition_conforms,
            "firstViolation": first_transition_overflow,
        },
        "noInitialVia": {
            "applicable": True,
            "conforms": initial_conforms,
            "firstViolation": first_initial_via,
        },
        "noConsecutiveVia": {
            "applicable": True,
            "conforms": consecutive_conforms,
            "firstViolation": first_consecutive_via,
        },
        "viaHoleBudget": {
            "applicable": hole_applicable,
            "prefixHoleCount": prefix_hole_count,
            "maximumVias": maximum_vias,
            "checkedViaCount": via_count,
            "conforms": hole_conforms,
            "firstViolation": first_hole_overflow,
        },
        "viaSpacing": {
            "applicable": True,
            "coordinateSpace": "GRID_ORIGIN_RELATIVE_UNITS",
            "minimumOpenSquareUnits": VIA_SPACING_OPEN_SQUARE,
            "gridPitch": grid_pitch,
            "totalViaCount": via_count,
            "checkedViaCount": len(spacing_vias),
            "totalPairChecks": total_pair_checks,
            "checkedPairCount": checked_pair_count,
            "complete": spacing_complete,
            "conforms": spacing_conforms,
            "firstViolation": first_spacing_violation,
        },
        "allApplicableChecksConform": all_applicable_conform,
    }
    return {
        "schema": METRICS_SCHEMA,
        "caseId": graph.case_id,
        "graphSha256": graph.graph_sha256,
        "witness": {
            "reachable": True,
            "startState": start_state,
            "goalState": goal_state,
            "stateCount": len(states),
            "edgeCount": len(edges),
            "stepCount": step_count,
            "viaCount": via_count,
            "minimumRelaxedCost": minimum_cost,
            "planarTurnCount": planar_turn_count,
            "turnPenalty": turn_penalty,
            "productionStyleCost": minimum_cost + turn_penalty,
            "sha256": witness_sha256,
        },
        "historySubset": history_subset,
        "productionFeasibility": "UNKNOWN",
        "acceptance": {
            "actualCaptureAttested": False,
            "route": False,
            "layout": False,
            "electrical": False,
            "q100": False,
        },
    }
