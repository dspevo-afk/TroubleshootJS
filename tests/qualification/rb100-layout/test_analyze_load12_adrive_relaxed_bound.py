#!/usr/bin/env python3
"""Independent contracts for the bounded relaxed A_DRIVE length analysis."""
from __future__ import annotations

import copy
import random
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import analyze_load12_adrive_relaxed_bound as analysis


NORTH, EAST, SOUTH, WEST = 1, 2, 4, 8
DIRECTIONS = (
    (NORTH, 0, -1),
    (EAST, 1, 0),
    (SOUTH, 0, 1),
    (WEST, -1, 0),
)


def graph(width, height, *, pitch=10, starts=None, goals=None, steps=None,
          vias=None, all_holes=0, maximum_holes=None, maximum_transitions=4):
    cells = width * height
    return {
        "width": width,
        "height": height,
        "pitch": pitch,
        "starts": [0] if starts is None else list(starts),
        "goalStates": [cells - 1] if goals is None else list(goals),
        "steps": [0] * (2 * cells) if steps is None else list(steps),
        "vias": [False] * cells if vias is None else list(vias),
        "allHoleCount": all_holes,
        "maximumHoles": (
            all_holes + 4 if maximum_holes is None else maximum_holes
        ),
        "maximumTransitions": maximum_transitions,
    }


def add_edge(value, layer, x, y, bit):
    cells = value["width"] * value["height"]
    cell = y * value["width"] + x
    value["steps"][layer * cells + cell] |= bit


def full_expanded_bellman_ford(value):
    """Independent full expanded-graph Bellman-Ford oracle; no pruning."""
    width, height = value["width"], value["height"]
    cells = width * height
    face_states = 2 * cells
    cap = min(
        value["maximumTransitions"],
        value["maximumHoles"] - value["allHoleCount"],
    )
    expanded_states = face_states * (cap + 1)
    edges = []
    for used in range(cap + 1):
        for layer in range(2):
            for cell in range(cells):
                source_base = layer * cells + cell
                source = used * face_states + source_base
                x, y = cell % width, cell // width
                mask = value["steps"][source_base]
                for bit, dx, dy in DIRECTIONS:
                    if not mask & bit:
                        continue
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < width and 0 <= ny < height:
                        neighbor = ny * width + nx
                        target = used * face_states + layer * cells + neighbor
                        edges.append((source, target, 1))
                if value["vias"][cell] and used < cap:
                    target_base = (1 - layer) * cells + cell
                    target = (used + 1) * face_states + target_base
                    edges.append((source, target, 0))

    distances = [None] * expanded_states
    for state in value["starts"]:
        distances[state] = 0
    for _ in range(max(0, expanded_states - 1)):
        changed = False
        for source, target, cost in edges:
            distance = distances[source]
            if distance is not None and (
                    distances[target] is None or
                    distance + cost < distances[target]):
                distances[target] = distance + cost
                changed = True
        if not changed:
            break
    return distances, cap


def oracle_goal_minimum(value, distances, cap, goal_state):
    face_states = 2 * value["width"] * value["height"]
    hits = (
        distances[used * face_states + goal_state]
        for used in range(cap + 1)
    )
    finite = [distance for distance in hits if distance is not None]
    return min(finite) if finite else None


def assert_operation_ledger(test, result):
    counters = result["counters"]
    expected = sum(counters[key] for key in (
        "queuePushes", "queuePops", "finalizedStates",
        "planarDirectionChecks", "viaEligibilityChecks",
        "validEdgeRelaxationAttempts",
    ))
    test.assertEqual(result["analysisOperations"], expected)
    grouped = sum(
        row["analysisOperations"]
        for row in result["budget"]["groups"]
        if type(row["analysisOperations"]) is int
    )
    test.assertEqual(grouped, expected)


class RelaxedLengthLiterals(unittest.TestCase):
    def test_directed_edges_are_not_reversed_and_row_wrap_is_rejected(self):
        value = graph(
            2, 1, starts=[1], goals=[0],
            steps=[EAST, 0, 0, 0],
            maximum_holes=0, maximum_transitions=0,
        )
        result = analysis.analyze_relaxed_graph(value)
        self.assertEqual(result["goalOutcomes"][0]["outcome"], "NO_PATH_WITHIN_LIMIT")
        self.assertEqual(result["resultClass"], "EXCLUDED_BY_BOUNDED_RELAXATION")

        wrapped = graph(
            2, 2, goals=[1], steps=[0, EAST, 0, 0, 0, 0, 0, 0],
        )
        with self.assertRaises(analysis.AnalysisInputError) as caught:
            analysis.analyze_relaxed_graph(wrapped)
        self.assertEqual(caught.exception.code, "GRAPH_STEP_LEAVES_GRID")

    def test_partial_seed_and_zero_cost_via(self):
        value = graph(
            2, 1, starts=[0], goals=[3],
            steps=[EAST, 0, 0, 0], vias=[False, True],
            maximum_holes=1, maximum_transitions=1,
        )
        result = analysis.analyze_relaxed_graph(value)
        self.assertEqual(result["graph"]["starts"], [0])
        self.assertEqual(result["graph"]["effectiveTransitionCap"], 1)
        self.assertEqual(result["goalOutcomes"][0]["minimumRelaxedLengthWithinLimit"], 10)
        self.assertEqual(result["counters"]["maximumTransitionsUsed"], 1)
        self.assertEqual(result["resultClass"], "NOT_EXCLUDED_BY_RELAXATION")

    def test_transition_zero_four_and_all_hole_boundaries(self):
        no_planar = graph(
            2, 1, starts=[0], goals=[1], vias=[True, False],
            maximum_holes=4, maximum_transitions=0,
        )
        zero = analysis.analyze_relaxed_graph(no_planar)
        self.assertEqual(zero["graph"]["effectiveTransitionCap"], 0)
        self.assertEqual(zero["counters"]["maximumTransitionsUsed"], 0)
        self.assertEqual(zero["resultClass"], "EXCLUDED_BY_BOUNDED_RELAXATION")

        four = graph(
            2, 1, starts=[0], goals=[1], vias=[True, False],
            maximum_holes=4, maximum_transitions=4,
        )
        four_result = analysis.analyze_relaxed_graph(four)
        self.assertEqual(four_result["graph"]["effectiveTransitionCap"], 4)
        self.assertEqual(four_result["counters"]["maximumTransitionsUsed"], 4)
        self.assertEqual(four_result["counters"]["discoveredLabels"], 5)

        full = graph(
            2, 1, starts=[0], goals=[1], vias=[True, False],
            all_holes=4, maximum_holes=4, maximum_transitions=4,
        )
        full_result = analysis.analyze_relaxed_graph(full)
        self.assertEqual(full_result["graph"]["effectiveTransitionCap"], 0)
        self.assertEqual(full_result["counters"]["maximumTransitionsUsed"], 0)

        negative = dict(full, allHoleCount=5)
        with self.assertRaises(analysis.AnalysisInputError) as caught:
            analysis.analyze_relaxed_graph(negative)
        self.assertEqual(
            caught.exception.code, "GRAPH_NEGATIVE_REMAINING_HOLE_CAPACITY")

    def test_each_goal_uses_its_own_length_limit(self):
        value = graph(
            4, 3, starts=[0], goals=[1, 3],
            maximum_holes=0, maximum_transitions=0,
        )
        # The only directed path reaches the near goal after five moves, then
        # continues to the farther goal after two more.
        for layer, x, y, bit in (
            (0, 0, 0, SOUTH),
            (0, 0, 1, SOUTH),
            (0, 0, 2, EAST),
            (0, 1, 2, NORTH),
            (0, 1, 1, NORTH),
            (0, 1, 0, EAST),
            (0, 2, 0, EAST),
        ):
            add_edge(value, layer, x, y, bit)
        result = analysis.analyze_relaxed_graph(value)
        self.assertEqual(
            result["goalOutcomes"],
            [
                {
                    "goalState": 1,
                    "directLength": 10,
                    "lengthLimit": 30,
                    "outcome": "NO_PATH_WITHIN_LIMIT",
                    "minimumRelaxedLengthWithinLimit": None,
                },
                {
                    "goalState": 3,
                    "directLength": 30,
                    "lengthLimit": 90,
                    "outcome": "WITHIN_LIMIT",
                    "minimumRelaxedLengthWithinLimit": 70,
                },
            ],
        )
        self.assertEqual(result["resultClass"], "NOT_EXCLUDED_BY_RELAXATION")

    def test_exact_three_times_direct_and_smallest_reachable_overlimit(self):
        boundary = graph(
            2, 2, starts=[0], goals=[1],
            maximum_holes=0, maximum_transitions=0,
        )
        add_edge(boundary, 0, 0, 0, SOUTH)
        add_edge(boundary, 0, 0, 1, EAST)
        add_edge(boundary, 0, 1, 1, NORTH)
        exact = analysis.analyze_relaxed_graph(boundary)
        self.assertEqual(exact["goalOutcomes"][0]["directLength"], 10)
        self.assertEqual(exact["goalOutcomes"][0]["lengthLimit"], 30)
        self.assertEqual(exact["goalOutcomes"][0]["minimumRelaxedLengthWithinLimit"], 30)

        over = graph(
            2, 3, starts=[0], goals=[1],
            maximum_holes=0, maximum_transitions=0,
        )
        for x, y, bit in (
            (0, 0, SOUTH), (0, 1, SOUTH), (0, 2, EAST),
            (1, 2, NORTH), (1, 1, NORTH),
        ):
            add_edge(over, 0, x, y, bit)
        direct_steps, route_steps = 1, 5
        self.assertEqual((route_steps - direct_steps) % 2, 0)
        self.assertEqual(route_steps, 3 * direct_steps + 2)
        self.assertNotEqual(3 * direct_steps + 1, direct_steps % 2)
        beyond = analysis.analyze_relaxed_graph(over)
        self.assertEqual(beyond["goalOutcomes"][0]["lengthLimit"], 30)
        self.assertEqual(beyond["goalOutcomes"][0]["outcome"], "NO_PATH_WITHIN_LIMIT")
        self.assertIsNone(
            beyond["goalOutcomes"][0]["minimumRelaxedLengthWithinLimit"])

    def test_omitted_bends_and_prior_via_spacing_never_grant_feasibility(self):
        snake = graph(
            18, 2, starts=[0], goals=[17],
            maximum_holes=0, maximum_transitions=0,
        )
        row = 0
        for x in range(18):
            add_edge(snake, 0, x, row, SOUTH if row == 0 else NORTH)
            row = 1 - row
            if x < 17:
                add_edge(snake, 0, x, row, EAST)
        # This sole route has 35 moves and 34 bends, but is within 3*17.
        self.assertEqual(35, 17 + 18)
        bend_result = analysis.analyze_relaxed_graph(snake)
        self.assertEqual(bend_result["goalOutcomes"][0]["lengthLimit"], 510)
        self.assertEqual(
            bend_result["goalOutcomes"][0]["minimumRelaxedLengthWithinLimit"], 350)
        self.assertEqual(bend_result["resultClass"], "NOT_EXCLUDED_BY_RELAXATION")

        close_vias = graph(
            3, 1, starts=[0], goals=[2],
            steps=[0, EAST, 0, EAST, 0, 0],
            vias=[True, True, False],
            maximum_holes=2, maximum_transitions=2,
        )
        spacing_result = analysis.analyze_relaxed_graph(close_vias)
        self.assertEqual(spacing_result["graph"]["effectiveTransitionCap"], 2)
        self.assertEqual(
            spacing_result["goalOutcomes"][0]["minimumRelaxedLengthWithinLimit"], 20)
        self.assertEqual(spacing_result["resultClass"], "NOT_EXCLUDED_BY_RELAXATION")
        self.assertFalse(any(
            spacing_result["qualification"].values()))

    def test_direct_zero_rejected_and_no_route_witness_is_emitted(self):
        value = graph(
            1, 1, starts=[0], goals=[1], vias=[True],
            maximum_holes=4, maximum_transitions=4,
        )
        result = analysis.analyze_relaxed_graph(value)
        self.assertEqual(result["goalOutcomes"][0]["outcome"], "DIRECT_ZERO_REJECTED")
        self.assertEqual(result["resultClass"], "EXCLUDED_BY_BOUNDED_RELAXATION")
        self.assertFalse(any(result["qualification"].values()))
        self.assertNotIn("path", result)
        self.assertNotIn("witness", result)
        self.assertNotIn("predecessors", result)

        original = copy.deepcopy(value)
        analysis.analyze_relaxed_graph(value)
        self.assertEqual(value, original)


class ValidationAndBudgets(unittest.TestCase):
    def test_capture_net_census_is_distinct_from_whole_prefix(self):
        cells = 478 * 309
        goal_mask = ["0"] * (2 * cells)
        for state in (114144, 261846):
            goal_mask[state] = "1"
        packed = {
            "width": 478, "height": 309, "protocol": analysis.GRAPH_PROTOCOL,
            "model": analysis.GRAPH_MODEL,
            "caseId": "Q100_LOAD12_COST240_ADRIVE_PRE_GUARD",
            "starts": [121310, 269012], "steps": "0" * (2 * cells),
            "vias": "0" * cells, "goals": "".join(goal_mask),
        }
        integrity = {"sha256": analysis.ACCEPTED_GRAPH_SHA256,
                     "canonicalBytes": analysis.ACCEPTED_GRAPH_CANONICAL_BYTES}
        prefix = {"holeCount": 37, "traceCount": 177}
        report = {"experiment": {
            "graphIntegrity": integrity, "graph": {"goalStates": 2},
            "grid": {"originX": 30, "originY": 30, "pitch": 10},
            "target": {"branchOrdinal": 142, "netId": "A_DRIVE", "ordering": 2,
                       "netVectorPosition": 24, "searchNetIndex": 25, "pass": 1},
            "endpoints": {"start": {"cell": 121310},
                          "root": {"cell": 114144, "x": 3830, "y": 2410}},
            "policy": {"maximumHoleCount": 48, "maximumTransitions": 4},
            "sourceCensus": {"rowCounts": {"allHoleCount": 37, "viaKindCount": 37,
                                           "padRows": 2, "traceRows": 0}},
            "prefix": {"before": prefix, "after": dict(prefix), "unchanged": True},
        }}
        # Literal adapter fixture only; native integrity validation is upstream.
        normalized = analysis._decode_graph_masks(
            packed, integrity, report, analysis.AnalysisBudget())
        self.assertEqual(normalized["goalStates"], [114144, 261846])
        self.assertEqual(normalized["allHoleCount"], 37)
        for part, key, wrong in (("sourceCensus", "traceRows", 177),
                                 ("prefix", "traceCount", 0)):
            malformed = copy.deepcopy(report)
            if part == "sourceCensus":
                malformed["experiment"][part]["rowCounts"][key] = wrong
            else:
                malformed["experiment"][part]["after"][key] = wrong
            with self.assertRaises(analysis.AnalysisInputError):
                analysis._decode_graph_masks(
                    packed, integrity, malformed, analysis.AnalysisBudget())

    def test_malformed_graphs_and_outer_source_tampering_are_rejected(self):
        valid = graph(2, 1, starts=[0], goals=[1])
        cases = (
            (dict(valid, width=True), "GRAPH_DIMENSIONS_INVALID"),
            (dict(valid, vias=[1, False]), "GRAPH_VIAS_INVALID"),
            (dict(valid, starts=[0, 1]), "GRAPH_START_CELLS_INVALID"),
            (dict(valid, goalStates=[True]), "GRAPH_GOAL_STATES_INVALID"),
            (dict(valid, maximumTransitions=True), "GRAPH_CAPACITY_INVALID"),
        )
        for malformed, code in cases:
            with self.subTest(code=code):
                with self.assertRaises(analysis.AnalysisInputError) as caught:
                    analysis.analyze_relaxed_graph(malformed)
                self.assertEqual(caught.exception.code, code)
                self.assertNotIn("C:\\", str(caught.exception))

        outer = {
            "schema": analysis.INPUT_SCHEMA,
            "target": analysis.TARGET,
            "purpose": analysis.PURPOSE,
            "captureInputSpecPath": "capture-spec.json",
            "captureInputSpecSha256": "a" * 64,
            "captureReaderReportPath": "reader-report.json",
            "captureReaderReportSha256": analysis.ACCEPTED_READER_REPORT_SHA256,
            "captureReaderSha256": analysis.GRAPH_READER_SHA256,
            "graphSha256": analysis.ACCEPTED_GRAPH_SHA256,
            "graphCanonicalBytes": analysis.ACCEPTED_GRAPH_CANONICAL_BYTES,
        }
        # The outer hash binds each supplied capture spec. A task-owned tamper
        # copy can therefore reach the unchanged reader's raw-artifact checks.
        self.assertEqual(
            analysis._validate_outer_spec(outer)["captureInputSpecSha256"],
            "a" * 64,
        )
        for field, value, code in (
            ("captureReaderSha256", "f" * 64,
             "INPUT_SPEC_ACCEPTED_CAPTURE_BINDING_INVALID"),
            ("graphSha256", "f" * 64,
             "INPUT_SPEC_ACCEPTED_CAPTURE_BINDING_INVALID"),
            ("captureInputSpecSha256", "A" * 64, "INPUT_SPEC_SHA_INVALID"),
        ):
            tampered = dict(outer)
            tampered[field] = value
            with self.subTest(field=field):
                with self.assertRaises(analysis.AnalysisInputError) as caught:
                    analysis._validate_outer_spec(tampered)
                self.assertEqual(caught.exception.code, code)

        extra = dict(outer, extra=True)
        with self.assertRaises(analysis.AnalysisInputError) as caught:
            analysis._validate_outer_spec(extra)
        self.assertEqual(caught.exception.code, "INPUT_SPEC_KEYS_INVALID")
        wrong_count = dict(outer, graphCanonicalBytes=True)
        with self.assertRaises(analysis.AnalysisInputError) as caught:
            analysis._validate_outer_spec(wrong_count)
        self.assertEqual(
            caught.exception.code, "INPUT_SPEC_ACCEPTED_CAPTURE_BINDING_INVALID")

    def test_deadline_label_queue_and_group_limits_return_incomplete_with_work(self):
        one_edge = graph(
            2, 1, starts=[0], goals=[1],
            steps=[EAST, 0, 0, 0],
            maximum_holes=0, maximum_transitions=0,
        )
        label_limited = analysis.analyze_relaxed_graph(
            one_edge, analysis.AnalysisBudget(max_discovered_labels=1),
        )
        self.assertEqual(label_limited["resultClass"], "INCOMPLETE")
        self.assertEqual(label_limited["incompleteReason"], "DISCOVERED_LABEL_LIMIT")
        self.assertGreater(label_limited["analysisOperations"], 0)
        assert_operation_ledger(self, label_limited)

        branch = graph(
            2, 2, starts=[0], goals=[3],
            steps=[EAST | SOUTH, 0, 0, 0, 0, 0, 0, 0],
            maximum_holes=0, maximum_transitions=0,
        )
        queue_limited = analysis.analyze_relaxed_graph(
            branch, analysis.AnalysisBudget(max_queue_entries=1),
        )
        self.assertEqual(queue_limited["resultClass"], "INCOMPLETE")
        self.assertEqual(queue_limited["incompleteReason"], "QUEUE_ENTRY_LIMIT")
        self.assertGreater(queue_limited["analysisOperations"], 0)
        assert_operation_ledger(self, queue_limited)

        group_limited = analysis.analyze_relaxed_graph(
            one_edge,
            analysis.AnalysisBudget(
                max_groups=4, search_operations_per_group=1,
            ),
        )
        self.assertEqual(group_limited["resultClass"], "INCOMPLETE")
        self.assertEqual(group_limited["incompleteReason"], "GROUP_COUNT_LIMIT")
        self.assertGreater(group_limited["analysisOperations"], 0)
        assert_operation_ledger(self, group_limited)

        class AdvancingClock:
            def __init__(self):
                self.value = 0.0

            def __call__(self):
                self.value += 0.001
                return self.value

        deadline_limited = analysis.analyze_relaxed_graph(
            one_edge,
            analysis.AnalysisBudget(
                operation_seconds=0.020,
                max_group_seconds=1.0,
                clock=AdvancingClock(),
            ),
        )
        self.assertEqual(deadline_limited["resultClass"], "INCOMPLETE")
        self.assertIn(
            deadline_limited["incompleteReason"],
            ("OPERATION_DEADLINE", "GROUP_DEADLINE"),
        )
        self.assertGreater(deadline_limited["analysisOperations"], 0)
        assert_operation_ledger(self, deadline_limited)

    def test_exact_quantum_boundary_has_no_empty_next_group(self):
        one_edge = graph(
            2, 1, starts=[0], goals=[1],
            steps=[EAST, 0, 0, 0],
            maximum_holes=0, maximum_transitions=0,
        )
        result = analysis.analyze_relaxed_graph(
            one_edge,
            analysis.AnalysisBudget(search_operations_per_group=17),
        )
        self.assertEqual(result["resultClass"], "NOT_EXCLUDED_BY_RELAXATION")
        search_groups = [
            row for row in result["budget"]["groups"]
            if row["phase"] == "analysis-search"
        ]
        self.assertEqual(len(search_groups), 1)
        self.assertEqual(search_groups[0]["analysisOperations"], 17)
        self.assertFalse(search_groups[0]["timingPendingFinalFooter"])
        self.assertEqual(result["analysisOperations"], 17)
        assert_operation_ledger(self, result)


class IndependentBellmanFordContracts(unittest.TestCase):
    def test_1024_seeded_directed_two_face_graphs(self):
        rng = random.Random(42)
        for case_index in range(1024):
            width = rng.randint(1, 3)
            height = rng.randint(1, 3)
            cells = width * height
            starts_cell = rng.randrange(cells)
            start_layers = [0, 1] if rng.randrange(2) else [rng.randrange(2)]
            starts = sorted(layer * cells + starts_cell for layer in start_layers)

            steps = [0] * (2 * cells)
            for layer in range(2):
                for cell in range(cells):
                    x, y = cell % width, cell // width
                    state = layer * cells + cell
                    if y > 0 and rng.random() < 0.28:
                        steps[state] |= NORTH
                    if x + 1 < width and rng.random() < 0.28:
                        steps[state] |= EAST
                    if y + 1 < height and rng.random() < 0.28:
                        steps[state] |= SOUTH
                    if x > 0 and rng.random() < 0.28:
                        steps[state] |= WEST

            vias = [rng.random() < 0.35 for _ in range(cells)]
            goal_count = rng.randint(1, min(3, 2 * cells))
            goals = sorted(rng.sample(range(2 * cells), goal_count))
            all_holes = rng.randint(0, 3)
            remaining_holes = rng.randint(0, 4)
            maximum_transitions = rng.randint(0, 4)
            value = graph(
                width, height, pitch=rng.randint(1, 20),
                starts=starts, goals=goals, steps=steps, vias=vias,
                all_holes=all_holes,
                maximum_holes=all_holes + remaining_holes,
                maximum_transitions=maximum_transitions,
            )

            original = copy.deepcopy(value)
            distances, cap = full_expanded_bellman_ford(value)
            result = analysis.analyze_relaxed_graph(value)
            self.assertEqual(value, original, "input mutation in case %d" % case_index)
            self.assertEqual(result["status"], "COMPLETE")
            expected_outcomes = []
            start_x, start_y = starts_cell % width, starts_cell // width
            for goal_state in goals:
                goal_cell = goal_state % cells
                goal_x, goal_y = goal_cell % width, goal_cell // width
                direct_steps = abs(goal_x - start_x) + abs(goal_y - start_y)
                direct = direct_steps * value["pitch"]
                limit = 3 * direct
                if direct_steps == 0:
                    outcome, minimum = "DIRECT_ZERO_REJECTED", None
                else:
                    shortest = oracle_goal_minimum(value, distances, cap, goal_state)
                    if shortest is None or shortest > 3 * direct_steps:
                        outcome, minimum = "NO_PATH_WITHIN_LIMIT", None
                    else:
                        outcome = "WITHIN_LIMIT"
                        minimum = shortest * value["pitch"]
                expected_outcomes.append({
                    "goalState": goal_state,
                    "directLength": direct,
                    "lengthLimit": limit,
                    "outcome": outcome,
                    "minimumRelaxedLengthWithinLimit": minimum,
                })

            self.assertEqual(result["goalOutcomes"], expected_outcomes, case_index)
            # Compare every retained expanded-state distance independently,
            # including states unrelated to the reported goal minima.
            observed = {}
            observer_budget = analysis.AnalysisBudget()
            header = analysis._validate_graph_header(value, observer_budget)
            analysis._validate_graph_entries(header, observer_budget)
            observer_counters = analysis._fresh_counters()
            analysis._search(header, observer_budget, observer_counters,
                             finalized_distances=observed)
            observer_budget.end_group()
            expected_kept = {}
            sx, sy = starts_cell % width, starts_cell // width
            for expanded_state, distance in enumerate(distances):
                if distance is None:
                    continue
                cell = expanded_state % cells
                x, y = cell % width, cell // width
                for goal in goals:
                    gx, gy = (goal % cells) % width, (goal % cells) // width
                    direct_steps = abs(gx - sx) + abs(gy - sy)
                    if direct_steps and distance + abs(gx - x) + abs(gy - y) <= 3 * direct_steps:
                        expected_kept[expanded_state] = distance
                        break
            self.assertEqual(observed, expected_kept, case_index)
            self.assertEqual(len(observed), observer_counters["finalizedStates"])
            self.assertEqual(observer_counters, result["counters"], case_index)
            expected_class = (
                "NOT_EXCLUDED_BY_RELAXATION"
                if any(row["outcome"] == "WITHIN_LIMIT" for row in expected_outcomes)
                else "EXCLUDED_BY_BOUNDED_RELAXATION"
            )
            self.assertEqual(result["resultClass"], expected_class, case_index)
            self.assertEqual(result["counters"]["queuePushes"],
                             result["counters"]["queuePops"])
            assert_operation_ledger(self, result)


if __name__ == "__main__":
    unittest.main(verbosity=2)
