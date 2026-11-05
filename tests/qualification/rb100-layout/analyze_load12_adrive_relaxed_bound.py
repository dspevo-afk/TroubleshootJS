#!/usr/bin/env python3
"""Bounded relaxed length analysis for the accepted LOAD12 A_DRIVE graph.

The core API analyzes captured directed masks. It does not construct or select
a route, retain predecessors, replay bend/spacing history, or qualify a board.
"""
from __future__ import annotations

from collections import deque
from collections.abc import Mapping
import hashlib
import json
import math
import os
from pathlib import Path
import re
import sys
import time
import types
from typing import Any, Callable, Dict, Iterable, List, Optional, Sequence, Tuple


INPUT_SCHEMA = "p07-load12-cost240-adrive-relaxed-bound-input-v1"
RESULT_SCHEMA = "p07-load12-cost240-adrive-relaxed-length-result-v1"
ERROR_SCHEMA = "p07-load12-cost240-adrive-relaxed-length-error-v1"
TARGET = "LOAD12"
PURPOSE = "FIXED_CAPTURE_ADRIVE_BOUNDED_RELAXATION"
GRAPH_PROTOCOL = "p07-fixed-prefix-packed-graph-v1"
GRAPH_MODEL = "DIRECTED_TWO_FACE_HISTORY_RELAXED"

GRAPH_READER_SHA256 = "fa31046816c4c70b643357fcce34b5daf161fe71e784bc0e0ccddce40baa9892"
ACCEPTED_READER_REPORT_SHA256 = "82b5eab039994175816f65c21e914282793c56d3987a48b8722b7dc81b0570ba"
ACCEPTED_CAPTURE_SPEC_SHA256 = "e60add9bd806a469e479893ac40f6571e37d32be114c833a5289d680df9669cd"
ACCEPTED_GRAPH_SHA256 = "0c73ce21580a9c4e2508a251425907da01d11c200483409870181a5d8d30ef11"
ACCEPTED_GRAPH_CANONICAL_BYTES = 738_743
ACCEPTED_NATIVE_RESULT_SHA256 = "ac118abf69d373860e9c58365c11cbbf0128e4871046fb92744871e1dd681647"
ACCEPTED_RECEIPT_SHA256 = "8ed3156d48fde4aa9615e76abaf48b352e27bbb1302250312202d0f7c98f33ca"
ACCEPTED_STDOUT_SHA256 = "483f1e04acaaba8e0f255b667a4b66dc88758abe0dfd1591bc38cb7e8ef5d456"

MAX_SPEC_BYTES = 16 * 1024
MAX_RESULT_BYTES = 1024 * 1024
MAX_READER_REPORT_BYTES = 2 * 1024 * 1024
MAX_READER_SOURCE_BYTES = 2 * 1024 * 1024
MAX_CAPTURE_BYTES = 32 * 1024 * 1024
MAX_GRAPH_CELLS = 250_000
MAX_PATH_CHARS = 4096
MAX_GRID_DIMENSION = 250_000
MAX_PITCH = 2_147_483_647
MAX_CAPACITY_VALUE = 2_147_483_647
MAX_TRANSITIONS = 4
MAX_OPERATION_SECONDS = 90.0
MAX_GROUPS = 640
MAX_GROUP_SECONDS = 5.0
MAX_SEARCH_OPERATIONS_PER_GROUP = 32_768
MAX_DECODE_ENTRIES_PER_GROUP = 32_768
MAX_DISCOVERED_LABELS = 1_000_000
MAX_QUEUE_ENTRIES = 250_000

NORTH, EAST, SOUTH, WEST = 1, 2, 4, 8
DIRECTIONS = (
    (NORTH, 0, -1),
    (EAST, 1, 0),
    (SOUTH, 0, 1),
    (WEST, -1, 0),
)
GRAPH_KEYS = frozenset((
    "width", "height", "pitch", "starts", "goalStates", "steps", "vias",
    "allHoleCount", "maximumHoles", "maximumTransitions",
))
OUTER_SPEC_KEYS = frozenset((
    "schema", "target", "purpose", "captureInputSpecPath",
    "captureInputSpecSha256", "captureReaderReportPath",
    "captureReaderReportSha256", "captureReaderSha256", "graphSha256",
    "graphCanonicalBytes",
))
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
ERROR_CODE_RE = re.compile(r"[A-Z0-9_]{1,96}\Z", re.ASCII)
QUALIFICATION_FALSE = {
    "feasibilityProven": False,
    "routeAccepted": False,
    "routeAdoption": False,
    "geometryPublished": False,
    "layoutQualification": False,
    "playable100": False,
}


class AnalysisInputError(ValueError):
    """A sanitized rejected-input code with no path or artifact details."""

    def __init__(self, code: str):
        safe = code if type(code) is str and ERROR_CODE_RE.fullmatch(code) else "INPUT_INVALID"
        self.code = safe
        super().__init__(safe)


class _BudgetStop(Exception):
    """A bounded nonconclusive stop, never a proof of exclusion."""

    def __init__(self, code: str, phase: str):
        self.code = code
        self.phase = phase
        super().__init__(code)


class AnalysisBudget:
    """Shared operation/group budget used by the API and the CLI.

    Tests may choose stricter limits and inject a deterministic monotonic clock.
    Every configured limit remains at or below the frozen CLI ceilings.
    """

    def __init__(
        self,
        *,
        operation_seconds: float = MAX_OPERATION_SECONDS,
        max_groups: int = MAX_GROUPS,
        max_group_seconds: float = MAX_GROUP_SECONDS,
        search_operations_per_group: int = MAX_SEARCH_OPERATIONS_PER_GROUP,
        decode_entries_per_group: int = MAX_DECODE_ENTRIES_PER_GROUP,
        max_discovered_labels: int = MAX_DISCOVERED_LABELS,
        max_queue_entries: int = MAX_QUEUE_ENTRIES,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        if (type(operation_seconds) not in (int, float) or
                not math.isfinite(operation_seconds) or
                not 0 < operation_seconds <= MAX_OPERATION_SECONDS):
            raise AnalysisInputError("BUDGET_OPERATION_LIMIT_INVALID")
        if (type(max_group_seconds) not in (int, float) or
                not math.isfinite(max_group_seconds) or
                not 0 < max_group_seconds <= MAX_GROUP_SECONDS):
            raise AnalysisInputError("BUDGET_GROUP_TIME_LIMIT_INVALID")
        bounds = (
            (max_groups, MAX_GROUPS, "BUDGET_GROUP_LIMIT_INVALID"),
            (search_operations_per_group, MAX_SEARCH_OPERATIONS_PER_GROUP,
             "BUDGET_SEARCH_QUANTUM_INVALID"),
            (decode_entries_per_group, MAX_DECODE_ENTRIES_PER_GROUP,
             "BUDGET_DECODE_QUANTUM_INVALID"),
            (max_discovered_labels, MAX_DISCOVERED_LABELS,
             "BUDGET_LABEL_LIMIT_INVALID"),
            (max_queue_entries, MAX_QUEUE_ENTRIES, "BUDGET_QUEUE_LIMIT_INVALID"),
        )
        for value, maximum, error in bounds:
            if type(value) is not int or not 1 <= value <= maximum:
                raise AnalysisInputError(error)
        if not callable(clock):
            raise AnalysisInputError("BUDGET_CLOCK_INVALID")

        self.operation_seconds = float(operation_seconds)
        self.max_groups = max_groups
        self.max_group_seconds = float(max_group_seconds)
        self.search_operations_per_group = search_operations_per_group
        self.decode_entries_per_group = decode_entries_per_group
        self.max_discovered_labels = max_discovered_labels
        self.max_queue_entries = max_queue_entries
        self._clock = clock
        self._last_clock: Optional[float] = None
        self._started = self._now("operation-start")
        self._groups: List[Dict[str, Any]] = []
        self._current: Optional[Dict[str, Any]] = None
        self.analysis_operations = 0
        self.max_closed_group_ms = 0

    def _now(self, phase: str) -> float:
        try:
            value = self._clock()
        except Exception:
            raise _BudgetStop("MONOTONIC_CLOCK_FAILED", phase)
        if type(value) not in (int, float) or not math.isfinite(value):
            raise _BudgetStop("MONOTONIC_CLOCK_INVALID", phase)
        value = float(value)
        if self._last_clock is not None and value < self._last_clock:
            raise _BudgetStop("MONOTONIC_CLOCK_MOVED_BACKWARD", phase)
        self._last_clock = value
        return value

    def _check_at(self, now: float, phase: str) -> None:
        if now - self._started > self.operation_seconds:
            raise _BudgetStop("OPERATION_DEADLINE", phase)
        if (self._current is not None and
                now - self._current["started"] > self.max_group_seconds):
            raise _BudgetStop("GROUP_DEADLINE", self._current["row"]["phase"])

    def mark(self, phase: str = "phase-mark") -> float:
        now = self._now(phase)
        self._check_at(now, phase)
        return now

    def elapsed_ms(self, mark: Optional[float] = None,
                   phase: str = "elapsed-time") -> int:
        now = self._now(phase)
        self._check_at(now, phase)
        start = self._started if mark is None else mark
        return max(0, int(math.ceil((now - start) * 1000.0)))

    def start_group(self, phase: str, *, unexported_work: bool = False) -> None:
        if type(phase) is not str or not phase or len(phase) > 96:
            raise AnalysisInputError("BUDGET_GROUP_NAME_INVALID")
        if self._current is not None:
            self.end_group()
        now = self._now(phase)
        self._check_at(now, phase)
        if len(self._groups) >= self.max_groups:
            raise _BudgetStop("GROUP_COUNT_LIMIT", phase)
        row: Dict[str, Any] = {
            "index": len(self._groups) + 1,
            "phase": phase,
            "analysisOperations": "NOT_EXPORTED" if unexported_work else 0,
            "decodeEntries": 0,
            "validationEntries": 0,
            "workMeasure": "NOT_EXPORTED" if unexported_work else "PHASE_ONLY",
            "elapsedMillis": None,
            "timingPendingFinalFooter": False,
        }
        self._groups.append(row)
        self._current = {
            "row": row, "started": now, "operations": 0,
            "decode_entries": 0, "validation_entries": 0,
            "unexported_work": unexported_work,
        }

    def end_group(self, *, final_footer: bool = False) -> None:
        if self._current is None:
            return
        current = self._current
        now = self._now(current["row"]["phase"] + "-end")
        self._check_at(now, current["row"]["phase"])
        elapsed_ms = max(0, int(math.ceil((now - current["started"]) * 1000.0)))
        current["row"]["elapsedMillis"] = elapsed_ms
        current["row"]["timingPendingFinalFooter"] = bool(final_footer)
        self.max_closed_group_ms = max(self.max_closed_group_ms, elapsed_ms)
        self._current = None

    def _record_entries(self, amount: int, field: str) -> None:
        if self._current is None or self._current["unexported_work"]:
            raise AnalysisInputError("BOUNDED_ENTRY_GROUP_MISSING")
        if type(amount) is not int or amount < 0:
            raise AnalysisInputError("BOUNDED_ENTRY_COUNT_INVALID")
        current = self._current
        counter = "decode_entries" if field == "decode" else "validation_entries"
        if current[counter] + amount > self.decode_entries_per_group:
            code = "DECODE_QUANTUM_LIMIT" if field == "decode" else "VALIDATION_QUANTUM_LIMIT"
            raise _BudgetStop(code, current["row"]["phase"])
        now = self._now(current["row"]["phase"])
        self._check_at(now, current["row"]["phase"])
        current[counter] += amount
        if field == "decode":
            current["row"]["decodeEntries"] = current[counter]
        else:
            current["row"]["validationEntries"] = current[counter]
        current["row"]["workMeasure"] = (
            "DECODE_ENTRIES" if field == "decode" else "VALIDATION_ENTRIES"
        )

    def _record_decode_entries(self, amount: int) -> None:
        self._record_entries(amount, "decode")

    def _record_validation_entries(self, amount: int) -> None:
        self._record_entries(amount, "validation")

    def _charge_search_operation(self, phase: str) -> None:
        if self._current is None:
            self.start_group(phase)
        elif self._current["row"]["phase"] != phase:
            self.end_group()
            self.start_group(phase)
        elif self._current["operations"] >= self.search_operations_per_group:
            # The next real operation opens the next group; exact-boundary
            # completion therefore leaves no empty fence group.
            self.end_group()
            self.start_group(phase)
        current = self._current
        if current is None or current["unexported_work"]:
            raise AnalysisInputError("SEARCH_GROUP_MISSING")
        now = self._now(phase)
        self._check_at(now, phase)
        if current["operations"] >= self.search_operations_per_group:
            raise _BudgetStop("SEARCH_QUANTUM_LIMIT", phase)
        current["operations"] += 1
        current["row"]["analysisOperations"] = current["operations"]
        current["row"]["workMeasure"] = "ANALYSIS_OPERATIONS"
        self.analysis_operations += 1

    def snapshot(self, *, close_current: bool = False,
                 final_footer: bool = False) -> List[Dict[str, Any]]:
        if close_current:
            self.end_group(final_footer=final_footer)
        elif self._current is not None:
            self._current["row"]["timingPendingFinalFooter"] = True
        return [dict(row) for row in self._groups]

    def _best_effort_close(self) -> None:
        if self._current is None:
            return
        try:
            self.end_group()
        except _BudgetStop:
            self._current["row"]["timingPendingFinalFooter"] = True
            self._current = None


def _exact_int(value: Any, minimum: int, maximum: int) -> bool:
    return type(value) is int and minimum <= value <= maximum


def _input_error(code: str) -> None:
    raise AnalysisInputError(code)


def _validate_graph_header(graph: Any, budget: AnalysisBudget) -> Dict[str, Any]:
    budget.start_group("graph-header-validation")
    try:
        if not isinstance(graph, Mapping) or set(graph.keys()) != GRAPH_KEYS:
            _input_error("GRAPH_KEYS_INVALID")
        width, height, pitch = graph["width"], graph["height"], graph["pitch"]
        if (not _exact_int(width, 1, MAX_GRID_DIMENSION) or
                not _exact_int(height, 1, MAX_GRID_DIMENSION) or
                width * height > MAX_GRAPH_CELLS or
                not _exact_int(pitch, 1, MAX_PITCH)):
            _input_error("GRAPH_DIMENSIONS_INVALID")
        cells = width * height
        face_states = 2 * cells
        steps, vias = graph["steps"], graph["vias"]
        starts, goals = graph["starts"], graph["goalStates"]
        if (type(steps) not in (list, tuple) or len(steps) != face_states or
                type(vias) not in (list, tuple) or len(vias) != cells or
                type(starts) not in (list, tuple) or not starts or
                type(goals) not in (list, tuple) or not goals or
                len(starts) > face_states or len(goals) > face_states):
            _input_error("GRAPH_ARRAY_LENGTH_INVALID")
        if (not _exact_int(graph["allHoleCount"], 0, MAX_CAPACITY_VALUE) or
                not _exact_int(graph["maximumHoles"], 0, MAX_CAPACITY_VALUE) or
                not _exact_int(graph["maximumTransitions"], 0, MAX_TRANSITIONS)):
            _input_error("GRAPH_CAPACITY_INVALID")
        remaining_holes = graph["maximumHoles"] - graph["allHoleCount"]
        if remaining_holes < 0:
            _input_error("GRAPH_NEGATIVE_REMAINING_HOLE_CAPACITY")
        effective_cap = min(graph["maximumTransitions"], remaining_holes)

        starts_seen = set()
        start_cells = set()
        for state in starts:
            if not _exact_int(state, 0, face_states - 1) or state in starts_seen:
                _input_error("GRAPH_START_STATES_INVALID")
            starts_seen.add(state)
            start_cells.add(state % cells)
        if len(start_cells) != 1:
            _input_error("GRAPH_START_CELLS_INVALID")

        goals_seen = set()
        for state in goals:
            if not _exact_int(state, 0, face_states - 1) or state in goals_seen:
                _input_error("GRAPH_GOAL_STATES_INVALID")
            goals_seen.add(state)

        return {
            "width": width,
            "height": height,
            "pitch": pitch,
            "cells": cells,
            "faceStates": face_states,
            "starts": starts,
            "goalStateList": tuple(goals),
            "steps": steps,
            "vias": vias,
            "allHoleCount": graph["allHoleCount"],
            "maximumHoles": graph["maximumHoles"],
            "maximumTransitions": graph["maximumTransitions"],
            "effectiveTransitionCap": effective_cap,
            "startCell": next(iter(start_cells)),
        }
    finally:
        budget.end_group()


def _validate_graph_entries(header: Mapping[str, Any],
                            budget: AnalysisBudget) -> None:
    width, height, cells = header["width"], header["height"], header["cells"]
    steps, vias = header["steps"], header["vias"]
    for field_name, sequence, is_steps in (
        ("steps", steps, True),
        ("vias", vias, False),
    ):
        limit = budget.decode_entries_per_group
        for offset in range(0, len(sequence), limit):
            amount = min(limit, len(sequence) - offset)
            budget.start_group("graph-validation-" + field_name)
            try:
                budget._record_validation_entries(amount)
                if is_steps:
                    for local_index in range(amount):
                        state = offset + local_index
                        mask = sequence[state]
                        if type(mask) is not int or not 0 <= mask <= 15:
                            _input_error("GRAPH_STEPS_INVALID")
                        cell = state % cells
                        x, y = cell % width, cell // width
                        if ((y == 0 and mask & NORTH) or
                                (x == width - 1 and mask & EAST) or
                                (y == height - 1 and mask & SOUTH) or
                                (x == 0 and mask & WEST)):
                            _input_error("GRAPH_STEP_LEAVES_GRID")
                else:
                    for value in sequence[offset:offset + amount]:
                        if type(value) is not bool:
                            _input_error("GRAPH_VIAS_INVALID")
            finally:
                budget.end_group()


def _goal_geometry(header: Mapping[str, Any]) -> List[Dict[str, int]]:
    width, cells, pitch = header["width"], header["cells"], header["pitch"]
    start_cell = header["startCell"]
    start_x, start_y = start_cell % width, start_cell // width
    result: List[Dict[str, int]] = []
    for state in header["goalStateList"]:
        cell = state % cells
        goal_x, goal_y = cell % width, cell // width
        direct_steps = abs(goal_x - start_x) + abs(goal_y - start_y)
        result.append({
            "goalState": state,
            "cell": cell,
            "directSteps": direct_steps,
            "directLength": direct_steps * pitch,
            "limitSteps": 3 * direct_steps,
            "lengthLimit": 3 * direct_steps * pitch,
        })
    return result


def _keep_for_any_goal(cell: int, distance_steps: int,
                       goals: Sequence[Mapping[str, int]],
                       width: int) -> bool:
    for goal in goals:
        if goal["directSteps"] == 0:
            continue
        goal_cell = goal["cell"]
        x, y = cell % width, cell // width
        gx, gy = goal_cell % width, goal_cell // width
        lower_bound = distance_steps + abs(gx - x) + abs(gy - y)
        if lower_bound <= goal["limitSteps"]:
            return True
    return False


def _fresh_counters() -> Dict[str, int]:
    return {
        "queuePushes": 0,
        "queuePops": 0,
        "staleQueuePops": 0,
        "finalizedStates": 0,
        "planarDirectionChecks": 0,
        "viaEligibilityChecks": 0,
        "validEdgeRelaxationAttempts": 0,
        "acceptedEdges": 0,
        "successfulRelaxations": 0,
        "discoveredLabels": 0,
        "pendingQueueEntries": 0,
        "peakQueueEntries": 0,
        "prunedRelaxationCandidates": 0,
        "prunedStartStates": 0,
        "maximumTransitionsUsed": 0,
    }


def _analysis_operation_total(counters: Mapping[str, int]) -> int:
    return (
        counters["queuePushes"] + counters["queuePops"] +
        counters["finalizedStates"] + counters["planarDirectionChecks"] +
        counters["viaEligibilityChecks"] +
        counters["validEdgeRelaxationAttempts"]
    )


def _push(queue: Any, state_id: int, distance: int, *,
          zero_cost: bool, counters: Dict[str, int],
          budget: AnalysisBudget) -> None:
    if len(queue) >= budget.max_queue_entries:
        raise _BudgetStop("QUEUE_ENTRY_LIMIT", "analysis-search")
    budget._charge_search_operation("analysis-search")
    entry = (distance, state_id)
    if zero_cost:
        queue.appendleft(entry)
    else:
        queue.append(entry)
    counters["queuePushes"] += 1
    counters["pendingQueueEntries"] += 1
    counters["peakQueueEntries"] = max(
        counters["peakQueueEntries"], len(queue))


def _search(header: Mapping[str, Any], budget: AnalysisBudget,
            counters: Dict[str, int], *,
            finalized_distances: Optional[Dict[int, int]] = None
            ) -> Tuple[List[Dict[str, Any]], Dict[str, int]]:
    width, height, cells = header["width"], header["height"], header["cells"]
    face_states = header["faceStates"]
    cap = header["effectiveTransitionCap"]
    goals = _goal_geometry(header)
    goal_by_state = {goal["goalState"]: goal for goal in goals}
    nonzero_goals = [goal for goal in goals if goal["directSteps"] > 0]
    distances: Dict[int, int] = {}
    queue = deque()
    operation_count_before = budget.analysis_operations

    def offer(state_id: int, distance_steps: int, *, zero_cost: bool) -> None:
        base_state = state_id % face_states
        cell = base_state % cells
        old_distance = distances.get(state_id)
        if not _keep_for_any_goal(cell, distance_steps, nonzero_goals, width):
            counters["prunedRelaxationCandidates"] += 1
            return
        counters["acceptedEdges"] += 1
        if old_distance is not None and distance_steps >= old_distance:
            return
        if old_distance is None:
            if counters["discoveredLabels"] >= budget.max_discovered_labels:
                raise _BudgetStop("DISCOVERED_LABEL_LIMIT", "analysis-search")
        _push(queue, state_id, distance_steps, zero_cost=zero_cost,
              counters=counters, budget=budget)
        if old_distance is None:
            counters["discoveredLabels"] += 1
        distances[state_id] = distance_steps
        counters["maximumTransitionsUsed"] = max(
            counters["maximumTransitionsUsed"], state_id // face_states)
        counters["successfulRelaxations"] += 1

    for start_state in header["starts"]:
        state_id = start_state
        if state_id in distances:
            continue
        if not _keep_for_any_goal(state_id % cells, 0, nonzero_goals, width):
            counters["prunedStartStates"] += 1
            continue
        if counters["discoveredLabels"] >= budget.max_discovered_labels:
            raise _BudgetStop("DISCOVERED_LABEL_LIMIT", "analysis-search")
        _push(queue, state_id, 0, zero_cost=False, counters=counters,
              budget=budget)
        distances[state_id] = 0
        counters["discoveredLabels"] += 1

    minima_steps: Dict[int, Optional[int]] = {
        goal["goalState"]: None for goal in goals
    }
    while queue:
        budget._charge_search_operation("analysis-search")
        queued_distance, state_id = queue.popleft()
        counters["queuePops"] += 1
        counters["pendingQueueEntries"] -= 1
        if distances.get(state_id) != queued_distance:
            counters["staleQueuePops"] += 1
            continue

        budget._charge_search_operation("analysis-search")
        counters["finalizedStates"] += 1
        # Private tiny-contract observer only. The CLI/API never requests this
        # map, and no state distances or route witness enter their result.
        if finalized_distances is not None:
            finalized_distances[state_id] = queued_distance
        transitions_used = state_id // face_states
        base_state = state_id % face_states
        layer, cell = divmod(base_state, cells)
        x, y = cell % width, cell // width

        goal = goal_by_state.get(base_state)
        if (goal is not None and goal["directSteps"] > 0 and
                queued_distance <= goal["limitSteps"]):
            previous = minima_steps[base_state]
            if previous is None or queued_distance < previous:
                minima_steps[base_state] = queued_distance

        source_state = layer * cells + cell
        mask = header["steps"][source_state]
        for bit, dx, dy in DIRECTIONS:
            budget._charge_search_operation("analysis-search")
            counters["planarDirectionChecks"] += 1
            if not mask & bit:
                continue
            nx, ny = x + dx, y + dy
            if nx < 0 or nx >= width or ny < 0 or ny >= height:
                raise AnalysisInputError("GRAPH_STEP_LEAVES_GRID")
            neighbor_cell = ny * width + nx
            target_base_state = layer * cells + neighbor_cell
            target_id = transitions_used * face_states + target_base_state
            budget._charge_search_operation("analysis-search")
            counters["validEdgeRelaxationAttempts"] += 1
            offer(target_id, queued_distance + 1, zero_cost=False)

        budget._charge_search_operation("analysis-search")
        counters["viaEligibilityChecks"] += 1
        if header["vias"][cell] and transitions_used < cap:
            target_base_state = (1 - layer) * cells + cell
            target_id = (transitions_used + 1) * face_states + target_base_state
            budget._charge_search_operation("analysis-search")
            counters["validEdgeRelaxationAttempts"] += 1
            offer(target_id, queued_distance, zero_cost=True)

    if (_analysis_operation_total(counters) !=
            budget.analysis_operations - operation_count_before):
        raise AnalysisInputError("ANALYSIS_OPERATION_LEDGER_MISMATCH")

    outputs: List[Dict[str, Any]] = []
    for goal in goals:
        goal_state = goal["goalState"]
        minimum_steps = minima_steps[goal_state]
        if goal["directSteps"] == 0:
            outcome = "DIRECT_ZERO_REJECTED"
            minimum_length: Optional[int] = None
        elif minimum_steps is None:
            outcome = "NO_PATH_WITHIN_LIMIT"
            minimum_length = None
        else:
            outcome = "WITHIN_LIMIT"
            minimum_length = minimum_steps * header["pitch"]
        outputs.append({
            "goalState": goal_state,
            "directLength": goal["directLength"],
            "lengthLimit": goal["lengthLimit"],
            "outcome": outcome,
            "minimumRelaxedLengthWithinLimit": minimum_length,
        })
    return outputs, counters


def _base_record(*, result_class: str, analysis_status: str,
                 graph_header: Optional[Mapping[str, Any]],
                 goal_outcomes: Sequence[Mapping[str, Any]],
                 counters: Mapping[str, int], budget: AnalysisBudget,
                 incomplete_reason: Optional[str] = None) -> Dict[str, Any]:
    record: Dict[str, Any] = {
        "schema": RESULT_SCHEMA,
        "status": analysis_status,
        "analysisStatus": analysis_status,
        "resultClass": result_class,
        "graph": None,
        "goalOutcomes": [dict(goal) for goal in goal_outcomes],
        "counters": dict(counters),
        "analysisOperations": _analysis_operation_total(counters),
        "budget": {
            "operationLimitSeconds": budget.operation_seconds,
            "maximumGroups": budget.max_groups,
            "maximumGroupSeconds": budget.max_group_seconds,
            "maximumAnalysisOperationsPerGroup": budget.search_operations_per_group,
            "maximumDecodeEntriesPerGroup": budget.decode_entries_per_group,
            "maximumDiscoveredLabels": budget.max_discovered_labels,
            "maximumQueueEntries": budget.max_queue_entries,
            "theoreticalExpandedStateBound": None,
            "groups": budget.snapshot(),
            "readerInternalWorkUnits": "NOT_EXPORTED",
            "p07OwnerSteps": 0,
        },
        "qualification": dict(QUALIFICATION_FALSE),
    }
    if incomplete_reason is not None:
        record["incompleteReason"] = incomplete_reason
    if graph_header is not None:
        state_bound = 2 * graph_header["cells"] * (
            graph_header["effectiveTransitionCap"] + 1
        )
        record["graph"] = {
            "width": graph_header["width"],
            "height": graph_header["height"],
            "cells": graph_header["cells"],
            "pitch": graph_header["pitch"],
            "starts": list(graph_header["starts"]),
            "goalStates": list(graph_header["goalStateList"]),
            "allHoleCount": graph_header["allHoleCount"],
            "maximumHoles": graph_header["maximumHoles"],
            "maximumTransitions": graph_header["maximumTransitions"],
            "effectiveTransitionCap": graph_header["effectiveTransitionCap"],
            "theoreticalExpandedStateBound": state_bound,
        }
        record["budget"]["theoreticalExpandedStateBound"] = state_bound
    return record


def _incomplete_goals(graph_header: Optional[Mapping[str, Any]]
                      ) -> List[Dict[str, Any]]:
    if graph_header is None:
        return []
    return [{
        "goalState": goal["goalState"],
        "directLength": goal["directLength"],
        "lengthLimit": goal["lengthLimit"],
        "outcome": ("DIRECT_ZERO_REJECTED" if goal["directSteps"] == 0
                    else "INCOMPLETE"),
        "minimumRelaxedLengthWithinLimit": None,
    } for goal in _goal_geometry(graph_header)]


def analyze_relaxed_graph(graph: Mapping[str, Any],
                          budget: Optional[AnalysisBudget] = None) -> Dict[str, Any]:
    """Analyze a normalized directed graph without mutating it.

    The graph keys are width, height, pitch, starts, goalStates, steps, vias,
    allHoleCount, maximumHoles and maximumTransitions. Face IDs are
    layer * cells + cell. A stricter AnalysisBudget is available to tests.
    """
    if budget is None:
        budget = AnalysisBudget()
    if type(budget) is not AnalysisBudget:
        raise AnalysisInputError("ANALYSIS_BUDGET_TYPE_INVALID")
    counters = _fresh_counters()
    header: Optional[Dict[str, Any]] = None
    try:
        header = _validate_graph_header(graph, budget)
        _validate_graph_entries(header, budget)
        outcomes, counters = _search(header, budget, counters)
        budget.end_group()
        result_class = (
            "NOT_EXCLUDED_BY_RELAXATION"
            if any(row["outcome"] == "WITHIN_LIMIT" for row in outcomes)
            else "EXCLUDED_BY_BOUNDED_RELAXATION"
        )
        return _base_record(
            result_class=result_class,
            analysis_status="COMPLETE",
            graph_header=header,
            goal_outcomes=outcomes,
            counters=counters,
            budget=budget,
        )
    except _BudgetStop as stop:
        budget._best_effort_close()
        return _base_record(
            result_class="INCOMPLETE",
            analysis_status="INCOMPLETE",
            graph_header=header,
            goal_outcomes=_incomplete_goals(header),
            counters=counters,
            budget=budget,
            incomplete_reason=stop.code,
        )


def _canonical_json(value: Any) -> bytes:
    try:
        return json.dumps(
            value, sort_keys=True, separators=(",", ":"),
            ensure_ascii=True, allow_nan=False,
        ).encode("ascii", errors="strict")
    except (TypeError, ValueError, UnicodeEncodeError):
        _input_error("JSON_CANONICALIZATION_FAILED")


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _validate_outer_spec(value: Any) -> Dict[str, Any]:
    """Check the fixed ten-key CLI schema and accepted capture bindings."""
    if type(value) is not dict or set(value.keys()) != OUTER_SPEC_KEYS:
        _input_error("INPUT_SPEC_KEYS_INVALID")
    if (value["schema"] != INPUT_SCHEMA or value["target"] != TARGET or
            value["purpose"] != PURPOSE or
            type(value["schema"]) is not str or
            type(value["target"]) is not str or
            type(value["purpose"]) is not str):
        _input_error("INPUT_SPEC_IDENTITY_INVALID")
    for key in ("captureInputSpecPath", "captureReaderReportPath"):
        if (type(value[key]) is not str or not value[key] or
                len(value[key]) > MAX_PATH_CHARS or "\x00" in value[key]):
            _input_error("INPUT_SPEC_PATH_INVALID")
    for key in ("captureInputSpecSha256", "captureReaderReportSha256",
                "captureReaderSha256", "graphSha256"):
        if type(value[key]) is not str or SHA256_RE.fullmatch(value[key]) is None:
            _input_error("INPUT_SPEC_SHA_INVALID")
    if (value["captureReaderReportSha256"] != ACCEPTED_READER_REPORT_SHA256 or
            value["captureReaderSha256"] != GRAPH_READER_SHA256 or
            value["graphSha256"] != ACCEPTED_GRAPH_SHA256 or
            type(value["graphCanonicalBytes"]) is not int or
            value["graphCanonicalBytes"] != ACCEPTED_GRAPH_CANONICAL_BYTES):
        _input_error("INPUT_SPEC_ACCEPTED_CAPTURE_BINDING_INVALID")
    return dict(value)


def _bootstrap_has_reparse(path: Path) -> bool:
    try:
        absolute = Path(os.path.abspath(os.fspath(path)))
        current = Path(absolute.anchor)
        for part in absolute.parts[1:]:
            current = current / part
            try:
                if current.is_symlink():
                    return True
                junction = getattr(current, "is_junction", None)
                if callable(junction) and junction():
                    return True
                if bool(getattr(current.lstat(), "st_file_attributes", 0) & 0x400):
                    return True
            except FileNotFoundError:
                return False
        return False
    except (OSError, RuntimeError, TypeError, ValueError):
        return True


def _bootstrap_read(path: Path, limit: int, code: str, *,
                    allow_empty: bool = False) -> bytes:
    try:
        if _bootstrap_has_reparse(path):
            _input_error(code + "_REPARSE_PATH")
        info = path.stat()
        if (not os.path.isfile(path) or info.st_size > limit or info.st_size < 0 or
                (info.st_size == 0 and not allow_empty)):
            _input_error(code + "_SIZE_INVALID")
        raw = path.read_bytes()
        if (len(raw) != info.st_size or len(raw) > limit or
                (len(raw) == 0 and not allow_empty)):
            _input_error(code + "_READ_SIZE_CHANGED")
        return raw
    except AnalysisInputError:
        raise
    except (OSError, RuntimeError, ValueError):
        _input_error(code + "_READ_FAILED")


def _load_private_reader(budget: AnalysisBudget, source_root: Optional[Path] = None
                         ) -> Tuple[types.ModuleType, types.ModuleType,
                                    types.ModuleType, Dict[str, Any]]:
    budget.start_group("private-reader-import")
    try:
        reader_directory = (Path(__file__).parent if source_root is None else
                            source_root / "tests/qualification/rb100-layout")
        reader_path = Path(os.path.abspath(
            reader_directory / "read_load12_adrive_graph.py"))
        reader_raw = _bootstrap_read(reader_path, MAX_READER_SOURCE_BYTES,
                                     "PINNED_GRAPH_READER")
        if _sha256(reader_raw) != GRAPH_READER_SHA256:
            _input_error("PINNED_GRAPH_READER_HASH_MISMATCH")
        source = reader_raw.decode("utf-8", errors="strict")
        reader = types.ModuleType("_p07_load12_adrive_graph_reader_private")
        reader.__file__ = os.fspath(reader_path)
        reader.__package__ = ""
        exec(compile(source, os.fspath(reader_path), "exec", dont_inherit=True),
             reader.__dict__)
        after = _bootstrap_read(reader_path, MAX_READER_SOURCE_BYTES,
                                "PINNED_GRAPH_READER_RECHECK")
        if after != reader_raw or _sha256(after) != GRAPH_READER_SHA256:
            _input_error("PINNED_GRAPH_READER_CHANGED_DURING_IMPORT")
        required = (
            "run_validation", "_load_guard_reader", "_resolve_path", "_read_bounded",
            "_strict_json", "_canonical_json", "_same_json",
            "_validate_console_and_receipt",
        )
        if any(not callable(getattr(reader, name, None)) for name in required):
            _input_error("PINNED_GRAPH_READER_API_INVALID")
        guard, helper, guard_raw, guard_path, helper_raw, helper_path = (
            reader._load_guard_reader()
        )
        if (_sha256(guard_raw) != reader.GUARD_READER_SHA256 or
                _sha256(helper_raw) != reader.BASE_READER_SHA256):
            _input_error("PINNED_READER_HELPER_HASH_MISMATCH")
        snapshot = {
            "readerPath": reader_path,
            "readerRaw": reader_raw,
            "guardPath": guard_path,
            "guardRaw": guard_raw,
            "guardSha256": _sha256(guard_raw),
            "helperPath": helper_path,
            "helperRaw": helper_raw,
            "helperSha256": _sha256(helper_raw),
        }
    except AnalysisInputError:
        raise
    except Exception:
        _input_error("PINNED_GRAPH_READER_LOAD_FAILED")
    finally:
        budget.end_group()
    return reader, guard, helper, snapshot


def _resolve_path(reader: types.ModuleType, value: Any, base: Path,
                  code: str, helper: types.ModuleType) -> Path:
    try:
        return reader._resolve_path(value, base, code, helper)
    except Exception:
        _input_error(code + "_PATH_INVALID")


def _read_with_reader(reader: types.ModuleType, helper: types.ModuleType,
                      path: Path, limit: int, code: str,
                      *, allow_empty: bool = False) -> bytes:
    try:
        return reader._read_bounded(
            path, limit, code, helper, allow_empty=allow_empty,
        )
    except Exception:
        _input_error(code + "_READ_FAILED")


def _strict_json_with_reader(reader: types.ModuleType, helper: types.ModuleType,
                             raw: bytes, label: str) -> Any:
    try:
        return reader._strict_json(raw, label, helper)
    except Exception:
        _input_error(label + "_JSON_INVALID")


def _capture_paths(reader: types.ModuleType, helper: types.ModuleType,
                   spec_path: Path, outer: Mapping[str, Any],
                   capture_spec_raw: bytes) -> Dict[str, Any]:
    if _sha256(capture_spec_raw) != outer["captureInputSpecSha256"]:
        _input_error("CAPTURE_INPUT_SPEC_SHA256_MISMATCH")
    capture_path = _resolve_path(
        reader, outer["captureInputSpecPath"], spec_path.parent,
        "CAPTURE_INPUT_SPEC", helper,
    )
    capture_spec = _strict_json_with_reader(
        reader, helper, capture_spec_raw, "CAPTURE_INPUT_SPEC",
    )
    if type(capture_spec) is not dict:
        _input_error("CAPTURE_INPUT_SPEC_SHAPE_INVALID")
    try:
        reader._exact_keys(capture_spec, reader.INPUT_SPEC_KEYS,
                           "CAPTURE_INPUT_SPEC_KEYS_INVALID")
    except Exception:
        _input_error("CAPTURE_INPUT_SPEC_KEYS_INVALID")

    artifacts: Dict[str, Path] = {}
    for name in ("result", "stdout", "receipt", "stderr"):
        key = name + "Path"
        artifacts[name] = _resolve_path(
            reader, capture_spec[key], capture_path.parent,
            "CAPTURE_" + name.upper(), helper,
        )
    pins_path = _resolve_path(
        reader, capture_spec["pinsPath"], capture_path.parent,
        "CAPTURE_PINS", helper,
    )
    source_root = _resolve_path(
        reader, capture_spec["sourceRoot"], capture_path.parent,
        "CAPTURE_SOURCE_ROOT", helper,
    )
    report_path = _resolve_path(
        reader, outer["captureReaderReportPath"], spec_path.parent,
        "ACCEPTED_READER_REPORT", helper,
    )
    paths = [spec_path, capture_path, report_path, pins_path, source_root,
             *artifacts.values()]
    if len(set(paths)) != len(paths):
        _input_error("INPUT_PATH_ALIAS")
    return {
        "captureSpecPath": capture_path,
        "captureSpecRaw": capture_spec_raw,
        "captureSpec": capture_spec,
        "artifacts": artifacts,
        "pinsPath": pins_path,
        "sourceRoot": source_root,
        "readerReportPath": report_path,
    }


def _decode_graph_masks(graph_value: Any, graph_integrity: Any,
                        validation_result: Mapping[str, Any],
                        budget: AnalysisBudget) -> Dict[str, Any]:
    budget.start_group("validated-graph-binding")
    try:
        experiment = validation_result.get("experiment")
        if (type(graph_value) is not dict or type(graph_integrity) is not dict or
                type(experiment) is not dict):
            _input_error("VALIDATED_GRAPH_REPORT_INVALID")
        if (graph_integrity.get("sha256") != ACCEPTED_GRAPH_SHA256 or
                graph_integrity.get("canonicalBytes") != ACCEPTED_GRAPH_CANONICAL_BYTES or
                graph_integrity != experiment.get("graphIntegrity")):
            _input_error("VALIDATED_GRAPH_CANONICAL_BINDING_INVALID")

        width, height = graph_value.get("width"), graph_value.get("height")
        cells = width * height if type(width) is int and type(height) is int else 0
        if (width != 478 or height != 309 or cells != 147_702 or
                graph_value.get("protocol") != GRAPH_PROTOCOL or
                graph_value.get("model") != GRAPH_MODEL or
                graph_value.get("caseId") != "Q100_LOAD12_COST240_ADRIVE_PRE_GUARD"):
            _input_error("CAPTURE_GRAPH_IDENTITY_INVALID")
        starts = graph_value.get("starts")
        if type(starts) is not list or starts != [121_310, 269_012]:
            _input_error("CAPTURE_START_STATES_INVALID")

        steps_text, vias_text, goals_text = (
            graph_value.get("steps"), graph_value.get("vias"), graph_value.get("goals")
        )
        if (type(steps_text) is not str or len(steps_text) != 2 * cells or
                type(vias_text) is not str or len(vias_text) != cells or
                type(goals_text) is not str or len(goals_text) != 2 * cells):
            _input_error("CAPTURE_MASK_LENGTH_INVALID")

        graph_summary = experiment.get("graph")
        grid = experiment.get("grid")
        target = experiment.get("target")
        endpoints = experiment.get("endpoints")
        policy = experiment.get("policy")
        census = experiment.get("sourceCensus")
        prefix = experiment.get("prefix")
        if any(type(value) is not dict for value in
               (graph_summary, grid, target, endpoints, policy, census, prefix)):
            _input_error("VALIDATED_GRAPH_REPORT_SHAPE_INVALID")
        rows = census.get("rowCounts")
        start_endpoint = endpoints.get("start")
        root_endpoint = endpoints.get("root")
        prefix_before, prefix_after = prefix.get("before"), prefix.get("after")
        if any(type(value) is not dict for value in
               (rows, start_endpoint, root_endpoint, prefix_before, prefix_after)):
            _input_error("VALIDATED_GRAPH_ENDPOINTS_INVALID")
        if (graph_summary.get("goalStates") != 2 or
                grid.get("originX") != 30 or grid.get("originY") != 30 or
                grid.get("pitch") != 10 or target.get("branchOrdinal") != 142 or
                target.get("netId") != "A_DRIVE" or target.get("ordering") != 2 or
                target.get("netVectorPosition") != 24 or
                target.get("searchNetIndex") != 25 or target.get("pass") != 1 or
                rows.get("allHoleCount") != 37 or rows.get("viaKindCount") != 37 or
                rows.get("padRows") != 2 or rows.get("traceRows") != 0 or
                prefix.get("unchanged") is not True or prefix_before != prefix_after or
                prefix_after.get("holeCount") != 37 or
                prefix_after.get("traceCount") != 177 or
                policy.get("maximumHoleCount") != 48 or
                policy.get("maximumTransitions") != 4 or
                start_endpoint.get("cell") != 121_310 or
                root_endpoint.get("cell") != 114_144 or
                root_endpoint.get("x") != 3_830 or root_endpoint.get("y") != 2_410):
            _input_error("CAPTURE_POLICY_OR_CENSUS_INVALID")
    finally:
        budget.end_group()

    steps: List[int] = []
    for offset in range(0, len(steps_text), budget.decode_entries_per_group):
        amount = min(budget.decode_entries_per_group, len(steps_text) - offset)
        budget.start_group("decode-planar-masks")
        try:
            budget._record_decode_entries(amount)
            for index in range(offset, offset + amount):
                char = steps_text[index]
                if char not in "0123456789abcdef":
                    _input_error("CAPTURE_STEP_MASK_INVALID")
                mask = int(char, 16)
                cell = index % cells
                x, y = cell % width, cell // width
                if ((y == 0 and mask & NORTH) or
                        (x == width - 1 and mask & EAST) or
                        (y == height - 1 and mask & SOUTH) or
                        (x == 0 and mask & WEST)):
                    _input_error("CAPTURE_STEP_LEAVES_GRID")
                steps.append(mask)
        finally:
            budget.end_group()

    vias: List[bool] = []
    for offset in range(0, len(vias_text), budget.decode_entries_per_group):
        amount = min(budget.decode_entries_per_group, len(vias_text) - offset)
        budget.start_group("decode-via-mask")
        try:
            budget._record_decode_entries(amount)
            for char in vias_text[offset:offset + amount]:
                if char not in "01":
                    _input_error("CAPTURE_VIA_MASK_INVALID")
                vias.append(char == "1")
        finally:
            budget.end_group()

    goal_states: List[int] = []
    for offset in range(0, len(goals_text), budget.decode_entries_per_group):
        amount = min(budget.decode_entries_per_group, len(goals_text) - offset)
        budget.start_group("decode-complete-goals")
        try:
            budget._record_decode_entries(amount)
            for index in range(offset, offset + amount):
                char = goals_text[index]
                if char not in "01":
                    _input_error("CAPTURE_GOAL_MASK_INVALID")
                if char == "1":
                    goal_states.append(index)
        finally:
            budget.end_group()

    budget.start_group("complete-goal-set-check")
    try:
        if goal_states != [114_144, 261_846]:
            _input_error("CAPTURE_COMPLETE_FACE_STATES_INVALID")
    finally:
        budget.end_group()

    return {
        "width": width,
        "height": height,
        "pitch": 10,
        "starts": list(starts),
        "goalStates": goal_states,
        "steps": steps,
        "vias": vias,
        "allHoleCount": 37,
        "maximumHoles": 48,
        "maximumTransitions": 4,
    }


def _recheck_inputs(reader: types.ModuleType, guard: types.ModuleType,
                    helper: types.ModuleType, budget: AnalysisBudget,
                    outer_spec_path: Path, outer_spec_raw: bytes,
                    capture_paths: Mapping[str, Any],
                    artifact_hashes: Mapping[str, str], report_raw: bytes,
                    pins_raw: bytes, validation_result: Mapping[str, Any],
                    source_snapshots: Sequence[Mapping[str, Any]],
                    own_path: Path, own_raw: bytes) -> None:
    budget.start_group("input-artifact-recheck")
    try:
        if _read_with_reader(
                reader, helper, outer_spec_path, MAX_SPEC_BYTES,
                "OUTER_SPEC_RECHECK") != outer_spec_raw:
            _input_error("OUTER_SPEC_CHANGED_DURING_ANALYSIS")
        if _read_with_reader(
                reader, helper, capture_paths["captureSpecPath"], 1024 * 1024,
                "CAPTURE_SPEC_RECHECK") != capture_paths["captureSpecRaw"]:
            _input_error("CAPTURE_SPEC_CHANGED_DURING_ANALYSIS")
        if _read_with_reader(
                reader, helper, capture_paths["readerReportPath"],
                MAX_READER_REPORT_BYTES, "READER_REPORT_RECHECK") != report_raw:
            _input_error("READER_REPORT_CHANGED_DURING_ANALYSIS")
        for name, path in capture_paths["artifacts"].items():
            limit = (2 * 1024 * 1024 if name == "result" else MAX_CAPTURE_BYTES)
            raw = _read_with_reader(
                reader, helper, path, limit, "CAPTURE_" + name.upper() + "_RECHECK",
                allow_empty=(name == "stderr"),
            )
            if _sha256(raw) != artifact_hashes[name]:
                _input_error("CAPTURE_ARTIFACT_CHANGED_DURING_ANALYSIS")
    finally:
        budget.end_group()

    budget.start_group("source-manifest-and-files-recheck", unexported_work=True)
    try:
        pins_again = _read_with_reader(
            reader, helper, capture_paths["pinsPath"],
            reader.MAX_PIN_BYTES, "SOURCE_PINS_RECHECK",
        )
        if pins_again != pins_raw:
            _input_error("SOURCE_PINS_CHANGED_DURING_ANALYSIS")
        try:
            guard._bind_selected_source_root(capture_paths["sourceRoot"], helper)
            census = helper._audit_inputs_manifest(
                pins_again, capture_paths["captureSpec"],
                capture_paths["sourceRoot"],
            )
        except Exception:
            _input_error("SOURCE_INPUTS_CHANGED_DURING_ANALYSIS")
        source = validation_result.get("source")
        if (type(source) is not dict or
                census.get("inputCount") != source.get("inputCount") or
                census.get("inputBytes") != source.get("inputBytes") or
                census.get("pinsSha256") != source.get("pinsSha256")):
            _input_error("SOURCE_INPUTS_CHANGED_DURING_ANALYSIS")
    finally:
        budget.end_group()

    budget.start_group("pinned-helper-and-analysis-source-recheck")
    try:
        for snapshot in source_snapshots:
            for path_key, raw_key in (
                ("readerPath", "readerRaw"),
                ("guardPath", "guardRaw"),
                ("helperPath", "helperRaw"),
            ):
                raw = _bootstrap_read(
                    snapshot[path_key], MAX_READER_SOURCE_BYTES,
                    "PINNED_HELPER_RECHECK",
                )
                if raw != snapshot[raw_key]:
                    _input_error("PINNED_HELPER_CHANGED_DURING_ANALYSIS")
        if _bootstrap_read(
                own_path, MAX_READER_SOURCE_BYTES, "ANALYSIS_SOURCE_RECHECK") != own_raw:
            _input_error("ANALYSIS_SOURCE_CHANGED_DURING_ANALYSIS")
    finally:
        budget.end_group()


def _parse_cli(argv: Sequence[str]) -> Tuple[str, str]:
    if (type(argv) not in (list, tuple) or len(argv) != 4 or
            argv[0] != "--input-spec" or argv[2] != "--input-spec-sha256"):
        _input_error("CLI_ARGUMENTS_INVALID")
    path, digest = argv[1], argv[3]
    if (type(path) is not str or not path or len(path) > MAX_PATH_CHARS or
            "\x00" in path or type(digest) is not str or
            SHA256_RE.fullmatch(digest) is None):
        _input_error("CLI_ARGUMENTS_INVALID")
    return path, digest


def _build_cli_result(core_result: Mapping[str, Any],
                      capture_details: Mapping[str, Any],
                      own_sha256: str,
                      normalized_graph: Mapping[str, Any]) -> Dict[str, Any]:
    core_graph = core_result.get("graph")
    if type(core_graph) is dict:
        graph = dict(core_graph)
        goal_outcomes = core_result["goalOutcomes"]
    else:
        width, height = normalized_graph["width"], normalized_graph["height"]
        cells = width * height
        start_cell = normalized_graph["starts"][0] % cells
        start_x, start_y = start_cell % width, start_cell // width
        goal_outcomes = []
        for goal_state in normalized_graph["goalStates"]:
            goal_cell = goal_state % cells
            direct_steps = (
                abs(goal_cell % width - start_x) +
                abs(goal_cell // width - start_y)
            )
            goal_outcomes.append({
                "goalState": goal_state,
                "directLength": direct_steps * normalized_graph["pitch"],
                "lengthLimit": 3 * direct_steps * normalized_graph["pitch"],
                "outcome": (
                    "DIRECT_ZERO_REJECTED" if direct_steps == 0 else "INCOMPLETE"
                ),
                "minimumRelaxedLengthWithinLimit": None,
            })
        effective_cap = min(
            normalized_graph["maximumTransitions"],
            normalized_graph["maximumHoles"] - normalized_graph["allHoleCount"],
        )
        graph = {
            "width": width,
            "height": height,
            "cells": cells,
            "pitch": normalized_graph["pitch"],
            "starts": list(normalized_graph["starts"]),
            "goalStates": list(normalized_graph["goalStates"]),
            "allHoleCount": normalized_graph["allHoleCount"],
            "maximumHoles": normalized_graph["maximumHoles"],
            "maximumTransitions": normalized_graph["maximumTransitions"],
            "effectiveTransitionCap": effective_cap,
            "theoreticalExpandedStateBound": 2 * cells * (effective_cap + 1),
        }
    graph.update({
        "originX": 30,
        "originY": 30,
        "protocol": GRAPH_PROTOCOL,
        "model": GRAPH_MODEL,
    })
    record: Dict[str, Any] = {
        "schema": RESULT_SCHEMA,
        "status": core_result["resultClass"],
        "analysisStatus": core_result["analysisStatus"],
        "resultClass": core_result["resultClass"],
        "source": {
            "analysisSourceSha256": own_sha256,
            "graphReaderSha256": GRAPH_READER_SHA256,
            "guardReaderSha256": capture_details["guardReaderSha256"],
            "baseReaderSha256": capture_details["baseReaderSha256"],
            "captureReaderReportSha256": capture_details["readerReportSha256"],
            "captureInputSpecSha256": capture_details["captureInputSpecSha256"],
            "referenceCaptureInputSpecSha256": ACCEPTED_CAPTURE_SPEC_SHA256,
            "nativeResultSha256": capture_details["nativeResultSha256"],
            "stdoutSha256": capture_details["stdoutSha256"],
            "receiptSha256": capture_details["receiptSha256"],
            "stderrSha256": capture_details["stderrSha256"],
            "sourceManifestSha256": capture_details["sourceManifestSha256"],
            "sourceSnapshotRevision": capture_details["sourceSnapshotRevision"],
            "sourceInputCount": capture_details["sourceInputCount"],
            "sourceInputBytes": capture_details["sourceInputBytes"],
            "layoutVersion": capture_details["layoutVersion"],
            "dependencyEpoch": capture_details["dependencyEpoch"],
            "nativeElapsedSeconds": capture_details["nativeElapsedSeconds"],
            "readerValidationElapsedMillis": (
                capture_details["readerValidationElapsedMillis"]
            ),
            "readerInternalWorkUnits": "NOT_EXPORTED",
            "analysisWorkSource": "INDEPENDENT_0_1_BFS",
            "p07OwnerSteps": 0,
            "graphSha256": capture_details["graphSha256"],
            "graphCanonicalBytes": capture_details["graphCanonicalBytes"],
        },
        "candidate": {
            "target": TARGET,
            "purpose": PURPOSE,
            "rootSeed": "9007199254740993",
            "placementSeed": "-6587958367974952701",
            "routingSeed": "8445301674744508279",
            "routeRequestIndex": 0,
            "selectedPlacementIndex": 0,
            "selectedPlacementAttempt": 4,
            "sourcePass": 1,
            "ordering": 2,
            "branchOrdinal": 142,
            "netId": "A_DRIVE",
            "netVectorPosition": 24,
            "searchNetIndex": 25,
        },
        "graph": graph,
        "goalOutcomes": goal_outcomes,
        "counters": core_result["counters"],
        "analysisOperations": core_result["analysisOperations"],
        "budget": core_result["budget"],
        "limitations": {
            "boundedAbsenceMeaning": (
                "NO_PATH_WITHIN_LIMIT; no global minimum above a goal limit or "
                "unbounded unreachability claim"
            ),
            "omittedBendAndPriorViaSpacingHistory": True,
            "actualRouteFeasibility": "UNPROVEN",
            "routeSelection": "NONE",
            "routeAdoption": False,
        },
        "qualification": dict(QUALIFICATION_FALSE),
    }
    if "incompleteReason" in core_result:
        record["incompleteReason"] = core_result["incompleteReason"]
    return record


def _prepare_output(record: Mapping[str, Any], budget: AnalysisBudget,
                    operation_mark: float, *,
                    group_started: bool = False) -> Tuple[bytes, bytes, bytes]:
    if not group_started:
        budget.start_group("report-preparation")
    try:
        # The open preparation row is included in the one serialized body.
        # Its final elapsed time is the bounded scalar footer below.
        record_copy = dict(record)
        budget_copy = dict(record_copy["budget"])
        budget_copy["groups"] = budget.snapshot()
        record_copy["budget"] = budget_copy
        base = _canonical_json(record_copy)
        if not base.endswith(b"}") or len(base) > MAX_RESULT_BYTES:
            _input_error("RESULT_SIZE_LIMIT")
        # Prepare the large immutable prefix while this group is charged.
        prefix = base[:-1]
    except Exception:
        budget._best_effort_close()
        raise
    budget.end_group(final_footer=True)
    report_ms = budget._groups[-1]["elapsedMillis"] if budget._groups else 0
    footer = {
        "operationElapsedMillis": budget.elapsed_ms(operation_mark, "final-timing"),
        "reportPreparationElapsedMillis": report_ms,
        "groupCount": len(budget._groups),
        "maximumClosedGroupMillis": budget.max_closed_group_ms,
        "selectedHostTermination": "NOT_EXPORTED",
        "selectedHostCleanupMillis": "NOT_EXPORTED",
        "readerInternalWorkUnits": "NOT_EXPORTED",
    }
    footer_bytes = _canonical_json(footer)
    if len(footer_bytes) > 1024:
        _input_error("TIMING_FOOTER_SIZE_LIMIT")
    middle = b',"finalTiming":' + footer_bytes
    suffix = b"}\n"
    if len(prefix) + len(middle) + len(suffix) > MAX_RESULT_BYTES:
        _input_error("RESULT_SIZE_LIMIT")
    return prefix, middle, suffix


def _error_record(status: str, code: str, phase: Optional[str] = None) -> Dict[str, Any]:
    record: Dict[str, Any] = {
        "schema": ERROR_SCHEMA,
        "status": status,
        "errorCode": (
            code if type(code) is str and ERROR_CODE_RE.fullmatch(code)
            else "ANALYSIS_FAILED"
        ),
        "qualification": dict(QUALIFICATION_FALSE),
    }
    if phase is not None and type(phase) is str:
        record["phase"] = phase
    return record


def _emit_error(status: str, code: str, phase: Optional[str] = None) -> int:
    try:
        raw = _canonical_json(_error_record(status, code, phase)) + b"\n"
        sys.stdout.buffer.write(raw)
        sys.stdout.buffer.flush()
    except Exception:
        pass
    return 2


def main(argv: Optional[Sequence[str]] = None) -> int:
    budget: Optional[AnalysisBudget] = None
    try:
        budget = AnalysisBudget()
        operation_mark = budget.mark("operation-entry")
        spec_argument, spec_sha256 = _parse_cli(
            list(sys.argv[1:] if argv is None else argv)
        )
        reader, guard, helper, source_snapshot = _load_private_reader(budget)
        source_snapshots = [source_snapshot]
        budget.start_group("analysis-source-snapshot")
        try:
            own_path = Path(os.path.abspath(__file__))
            own_raw = _bootstrap_read(
                own_path, MAX_READER_SOURCE_BYTES, "ANALYSIS_SOURCE",
            )
            own_sha256 = _sha256(own_raw)
        finally:
            budget.end_group()

        budget.start_group("outer-spec-read-and-validation")
        try:
            spec_path = _resolve_path(
                reader, spec_argument, Path.cwd(), "OUTER_INPUT_SPEC", helper,
            )
            spec_raw = _read_with_reader(
                reader, helper, spec_path, MAX_SPEC_BYTES, "OUTER_INPUT_SPEC",
            )
            if _sha256(spec_raw) != spec_sha256:
                _input_error("OUTER_INPUT_SPEC_SHA256_MISMATCH")
            spec_value = _strict_json_with_reader(
                reader, helper, spec_raw, "OUTER_INPUT_SPEC",
            )
            outer = _validate_outer_spec(spec_value)
        finally:
            budget.end_group()

        budget.start_group("capture-spec-report-and-manifest-read")
        try:
            capture_path = _resolve_path(
                reader, outer["captureInputSpecPath"], spec_path.parent,
                "CAPTURE_INPUT_SPEC", helper,
            )
            capture_raw = _read_with_reader(
                reader, helper, capture_path, 1024 * 1024,
                "CAPTURE_INPUT_SPEC",
            )
            capture_paths = _capture_paths(
                reader, helper, spec_path, outer, capture_raw,
            )
            report_raw = _read_with_reader(
                reader, helper, capture_paths["readerReportPath"],
                MAX_READER_REPORT_BYTES, "ACCEPTED_READER_REPORT",
            )
            if _sha256(report_raw) != ACCEPTED_READER_REPORT_SHA256:
                _input_error("ACCEPTED_READER_REPORT_SHA256_MISMATCH")
            accepted_report = _strict_json_with_reader(
                reader, helper, report_raw, "ACCEPTED_READER_REPORT",
            )
            pins_raw = _read_with_reader(
                reader, helper, capture_paths["pinsPath"],
                reader.MAX_PIN_BYTES, "SOURCE_PINS",
            )
        finally:
            budget.end_group()

        # The unchanged validator binds its real file location to the native
        # source checkout. Load those same pinned bytes at that original path;
        # never rewrite its __file__ or weaken its selected-checkout checks.
        reader, guard, helper, source_snapshot = _load_private_reader(
            budget, capture_paths["sourceRoot"],
        )
        source_snapshots.append(source_snapshot)
        reader_validation_started = budget.mark("accepted-reader-validation-start")
        budget.start_group("accepted-reader-validation", unexported_work=True)
        try:
            validation_result = reader.run_validation(
                os.fspath(capture_path), outer["captureInputSpecSha256"],
            )
        except Exception as error:
            code = getattr(error, "code", None)
            if type(code) is str and ERROR_CODE_RE.fullmatch(code) is not None:
                _input_error(code)
            _input_error("ACCEPTED_CAPTURE_VALIDATION_FAILED")
        finally:
            budget.end_group()
        reader_validation_elapsed_ms = budget.elapsed_ms(
            reader_validation_started, "accepted-reader-validation-elapsed",
        )
        budget.start_group("accepted-reader-report-binding")
        try:
            if (type(validation_result) is not dict or
                    not reader._same_json(validation_result, accepted_report)):
                _input_error("ACCEPTED_READER_REPORT_MISMATCH")
            native = validation_result.get("nativeRun")
            source = validation_result.get("source")
            if (type(native) is not dict or type(source) is not dict or
                    native.get("resultSha256") != ACCEPTED_NATIVE_RESULT_SHA256 or
                    native.get("stdoutSha256") != ACCEPTED_STDOUT_SHA256 or
                    native.get("receiptSha256") != ACCEPTED_RECEIPT_SHA256 or
                    native.get("cleanupStatus") != "PASS" or
                    native.get("exitCode") != 0 or
                    native.get("nativeExitCode") != 0 or
                    native.get("terminationProven") is not True or
                    _sha256(pins_raw) != source.get("pinsSha256")):
                _input_error("ACCEPTED_NATIVE_RUN_BINDING_INVALID")
        finally:
            budget.end_group()

        artifact_hashes: Dict[str, str] = {}
        artifact_raw: Dict[str, bytes] = {}
        budget.start_group("validated-capture-reread")
        try:
            for name, path in capture_paths["artifacts"].items():
                limit = (reader.MAX_RESULT_BYTES if name == "result"
                         else reader.MAX_CAPTURE_BYTES)
                raw = _read_with_reader(
                    reader, helper, path, limit,
                    "CAPTURE_" + name.upper() + "_REREAD",
                    allow_empty=(name == "stderr"),
                )
                artifact_hashes[name] = _sha256(raw)
                if name != "result":
                    artifact_raw[name] = raw
            if (artifact_hashes.get("result") != native.get("resultSha256") or
                    artifact_hashes.get("stdout") != native.get("stdoutSha256") or
                    artifact_hashes.get("receipt") != native.get("receiptSha256") or
                    artifact_hashes.get("stderr") != native.get("stderrSha256") or
                    artifact_hashes.get("receipt") != ACCEPTED_RECEIPT_SHA256):
                _input_error("VALIDATED_CAPTURE_ARTIFACT_CHANGED")
        finally:
            budget.end_group()

        budget.start_group("validated-receipt-reparse")
        try:
            parsed_receipt = reader._validate_console_and_receipt(
                artifact_raw["stdout"], artifact_raw["receipt"],
                artifact_raw["stderr"], helper,
            )
            graph_event = parsed_receipt[8]
        except Exception:
            _input_error("VALIDATED_RECEIPT_REPARSE_FAILED")
        finally:
            budget.end_group()
        if _sha256(artifact_raw["receipt"]) != ACCEPTED_RECEIPT_SHA256:
            _input_error("ACCEPTED_RECEIPT_CHANGED_BEFORE_GRAPH_DECODE")
        normalized = _decode_graph_masks(
            graph_event.get("graph"), graph_event.get("graphIntegrity"),
            validation_result, budget,
        )

        capture_details = {
            "readerReportSha256": ACCEPTED_READER_REPORT_SHA256,
            "captureInputSpecSha256": outer["captureInputSpecSha256"],
            "nativeResultSha256": artifact_hashes["result"],
            "stdoutSha256": artifact_hashes["stdout"],
            "receiptSha256": artifact_hashes["receipt"],
            "stderrSha256": artifact_hashes["stderr"],
            "sourceManifestSha256": source["pinsSha256"],
            "sourceSnapshotRevision": source["snapshotRevision"],
            "sourceInputCount": source["inputCount"],
            "sourceInputBytes": source["inputBytes"],
            "layoutVersion": native["layoutVersion"],
            "dependencyEpoch": native["dependencyEpoch"],
            "nativeElapsedSeconds": native["elapsedSeconds"],
            "readerValidationElapsedMillis": reader_validation_elapsed_ms,
            "graphSha256": outer["graphSha256"],
            "graphCanonicalBytes": outer["graphCanonicalBytes"],
        }
        core_result = analyze_relaxed_graph(normalized, budget=budget)
        _recheck_inputs(
            reader, guard, helper, budget, spec_path, spec_raw,
            capture_paths, artifact_hashes, report_raw, pins_raw,
            validation_result, source_snapshots, own_path, own_raw,
        )

        # The nested group timing is retained in the common ledger; this scalar
        # avoids copying any task-owned path into the public result.
        capture_details["guardReaderSha256"] = source_snapshot["guardSha256"]
        capture_details["baseReaderSha256"] = source_snapshot["helperSha256"]
        budget.start_group("report-preparation")
        record = _build_cli_result(
            core_result, capture_details, own_sha256, normalized,
        )
        prepared = _prepare_output(
            record, budget, operation_mark, group_started=True,
        )
        for chunk in prepared:
            sys.stdout.buffer.write(chunk)
        sys.stdout.buffer.flush()
        return 0 if core_result["resultClass"] != "INCOMPLETE" else 2
    except _BudgetStop as stop:
        if budget is not None:
            budget._best_effort_close()
        return _emit_error("INCOMPLETE", stop.code, stop.phase)
    except AnalysisInputError as error:
        if budget is not None:
            budget._best_effort_close()
        return _emit_error("REJECTED", error.code)
    except Exception:
        if budget is not None:
            budget._best_effort_close()
        return _emit_error("REJECTED", "ANALYSIS_FAILED")
    except BaseException:
        if budget is not None:
            budget._best_effort_close()
        return _emit_error("INCOMPLETE", "EXECUTION_INTERRUPTED")


if __name__ == "__main__":
    raise SystemExit(main())
