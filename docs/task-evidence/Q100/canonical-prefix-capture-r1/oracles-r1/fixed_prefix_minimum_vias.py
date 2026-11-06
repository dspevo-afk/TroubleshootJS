#!/usr/bin/env python3
"""Exact minimum-VIA lower bound for a fixed-prefix relaxed graph.

This diagnostic treats every directed planar edge as cost zero and every
enabled face toggle as cost one. It does not use the weighted oracle's cost
model, retain predecessors, replay routing history, or accept a layout.
"""

from __future__ import annotations

from array import array
import re
from typing import Any, Dict

import read_fixed_prefix_graph as oracle


RESULT_SCHEMA = "p07-fixed-prefix-minimum-vias-v1"
MAX_STATES = 500_000
MAX_OUTGOING_EDGES = 2_500_000

_SHA256 = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
_DIRECTIONS = (
    (oracle.NORTH, 0, -1),
    (oracle.EAST, 1, 0),
    (oracle.SOUTH, 0, 1),
    (oracle.WEST, -1, 0),
)
_ERROR_CODES = frozenset((
    "GRAPH_TYPE_INVALID",
    "GRAPH_INVALID",
    "UINT32_ARRAY_UNSUPPORTED",
))


class MinimumViaError(ValueError):
    """A sanitized failure with a static error code only."""

    def __init__(self, code: str):
        if type(code) is not str or code not in _ERROR_CODES:
            code = "GRAPH_INVALID"
        self.code = code
        super().__init__(code)


def _validate_graph(graph: Any) -> oracle.FixedPrefixGraph:
    """Bound graph dimensions before a canonical codec round-trip copy."""
    if type(graph) is not oracle.FixedPrefixGraph:
        raise MinimumViaError("GRAPH_TYPE_INVALID")

    if (type(graph.width) is not int or type(graph.height) is not int or
            graph.width < 1 or graph.height < 1 or
            graph.width * graph.height > oracle.MAX_CELLS or
            type(graph.primary) is not int or graph.primary not in (0, 1) or
            type(graph.protocol) is not str or
            graph.protocol != oracle.GRAPH_PROTOCOL or
            type(graph.model) is not str or graph.model != oracle.GRAPH_MODEL or
            type(graph.case_id) is not str or not 1 <= len(graph.case_id) <= 128 or
            type(graph.graph_sha256) is not str or
            _SHA256.fullmatch(graph.graph_sha256) is None or
            type(graph.steps) is not tuple or
            type(graph.vias) is not tuple or
            type(graph.goals) is not tuple or
            type(graph.starts) is not tuple):
        raise MinimumViaError("GRAPH_INVALID")

    cells = graph.width * graph.height
    states = cells * 2
    if (states > MAX_STATES or states * 5 > MAX_OUTGOING_EDGES or
            len(graph.steps) != states or len(graph.vias) != cells or
            len(graph.goals) != states or len(graph.starts) not in (1, 2)):
        raise MinimumViaError("GRAPH_INVALID")

    try:
        validated = oracle.validate_payload({
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
    except oracle.GraphInputError:
        raise MinimumViaError("GRAPH_INVALID")
    if validated != graph:
        raise MinimumViaError("GRAPH_INVALID")
    return graph


def _record(graph: oracle.FixedPrefixGraph, *, reachable: bool,
            minimum_via_count: Any, goal_state: Any, termination: str,
            counters: Dict[str, int]) -> Dict[str, Any]:
    return {
        "schema": RESULT_SCHEMA,
        "protocol": graph.protocol,
        "model": graph.model,
        "caseId": graph.case_id,
        "graphSha256": graph.graph_sha256,
        "status": "REACHABLE" if reachable else "UNREACHABLE",
        "reachable": reachable,
        "minimumViaCount": minimum_via_count,
        "goalState": goal_state,
        "termination": termination,
        "counters": counters,
        "fullReachabilityCensus": not reachable,
        "productionFeasibility": "UNKNOWN",
        "acceptance": {
            "actualCaptureAttested": False,
            "route": False,
            "layout": False,
            "electrical": False,
            "q100": False,
        },
    }


def analyze_minimum_vias(graph: oracle.FixedPrefixGraph) -> Dict[str, Any]:
    """Compute the exact minimum VIA count in the immutable relaxed graph.

    Each level is the complete planar (zero-cost) closure for one VIA count.
    VIA targets wait in a separate queue until that closure is exhausted. If a
    pending target is reached by a planar edge first, it moves back into the
    current level. The visited byte values are 0=unseen, 1=admitted at the best
    known level, and 2=deferred to the next VIA level.

    ``uniqueStatesAdmitted`` includes first discoveries queued as pending VIA
    targets, even if later promoted by a planar edge or left pending at goal exit.
    ``maximumQueueStates`` counts live current-level FIFO entries;
    ``maximumPendingViaStates`` counts distinct still-deferred VIA targets.
    ``planarEdgesExamined`` counts enabled directed planar edges considered,
    ``viaChecks`` counts expanded states checked for a via, and
    ``viaTransitionsExamined`` counts enabled via edges considered.

    Search storage is one visited bytearray and two bounded ``array('I')``
    queues. Graph validation delegates to the pinned codec using bounded
    transient payload copies before those search structures are allocated.
    """
    graph = _validate_graph(graph)
    state_count = graph.state_count
    cells = graph.cells

    current = array("I")
    pending = array("I")
    if current.itemsize != 4 or pending.itemsize != 4:
        raise MinimumViaError("UINT32_ARRAY_UNSUPPORTED")
    seen = bytearray(state_count)

    unique_states_admitted = 0
    for state in sorted(graph.starts):
        current.append(state)
        seen[state] = 1
        unique_states_admitted += 1

    counters = {
        "uniqueStatesAdmitted": unique_states_admitted,
        "statePops": 0,
        "planarEdgesExamined": 0,
        "viaChecks": 0,
        "viaTransitionsExamined": 0,
        "levelsStarted": 1,
        "maximumQueueStates": len(current),
        "maximumPendingViaStates": 0,
    }

    level = 0
    head = 0
    pending_active = 0
    while True:
        while head < len(current):
            state = current[head]
            head += 1
            counters["statePops"] += 1
            if graph.goals[state]:
                return _record(
                    graph,
                    reachable=True,
                    minimum_via_count=level,
                    goal_state=state,
                    termination="GOAL_POPPED",
                    counters=counters,
                )

            layer = state // cells
            cell = state % cells
            x, y = cell % graph.width, cell // graph.width
            mask = graph.steps[state]
            for bit, dx, dy in _DIRECTIONS:
                if not mask & bit:
                    continue
                counters["planarEdgesExamined"] += 1
                neighbor_cell = (y + dy) * graph.width + (x + dx)
                neighbor_state = layer * cells + neighbor_cell
                state_status = seen[neighbor_state]
                if state_status == 0:
                    seen[neighbor_state] = 1
                    counters["uniqueStatesAdmitted"] += 1
                    current.append(neighbor_state)
                    live_queue = len(current) - head
                    if live_queue > counters["maximumQueueStates"]:
                        counters["maximumQueueStates"] = live_queue
                elif state_status == 2:
                    # A zero-cost route in this closure beats the deferred VIA.
                    seen[neighbor_state] = 1
                    pending_active -= 1
                    current.append(neighbor_state)
                    live_queue = len(current) - head
                    if live_queue > counters["maximumQueueStates"]:
                        counters["maximumQueueStates"] = live_queue

            counters["viaChecks"] += 1
            if graph.vias[cell]:
                counters["viaTransitionsExamined"] += 1
                via_state = (1 - layer) * cells + cell
                if seen[via_state] == 0:
                    seen[via_state] = 2
                    counters["uniqueStatesAdmitted"] += 1
                    pending.append(via_state)
                    pending_active += 1
                    if pending_active > counters["maximumPendingViaStates"]:
                        counters["maximumPendingViaStates"] = pending_active

        # Retain only still-deferred targets, assigning them their next level.
        write_index = 0
        for index in range(len(pending)):
            state = pending[index]
            if seen[state] == 2:
                seen[state] = 1
                pending[write_index] = state
                write_index += 1
        del pending[write_index:]
        if write_index == 0:
            break

        current, pending = pending, current
        pending.clear()
        head = 0
        level += 1
        counters["levelsStarted"] += 1
        pending_active = 0
        if len(current) > counters["maximumQueueStates"]:
            counters["maximumQueueStates"] = len(current)

    return _record(
        graph,
        reachable=False,
        minimum_via_count=None,
        goal_state=None,
        termination="FRONTIER_EXHAUSTED",
        counters=counters,
    )
