#!/usr/bin/env python3
"""Literal and exhaustive synthetic contracts for minimum-VIA analysis."""

from __future__ import annotations

import dataclasses
import json
import unittest

import fixed_prefix_minimum_vias as minimum_vias
import read_fixed_prefix_graph as oracle


def make_payload(width=1, height=1, primary=0, steps=None, vias=None,
                 goals=None, starts=None, case_id="MIN_VIA_SYNTHETIC"):
    cells = width * height
    return {
        "protocol": oracle.GRAPH_PROTOCOL,
        "model": oracle.GRAPH_MODEL,
        "caseId": case_id,
        "width": width,
        "height": height,
        "primary": primary,
        "steps": [0] * (cells * 2) if steps is None else list(steps),
        "vias": [False] * cells if vias is None else list(vias),
        "goals": [False] * (cells * 2) if goals is None else list(goals),
        "starts": [0] if starts is None else list(starts),
    }


def analyze(payload):
    graph = oracle.validate_payload(payload)
    return graph, minimum_vias.analyze_minimum_vias(graph)


def exhaustive_simple_path_minimum(graph):
    """Independent tiny-graph oracle by enumerating all simple directed paths."""
    north, east, south, west = 1, 2, 4, 8
    directions = (
        (north, 0, -1),
        (east, 1, 0),
        (south, 0, 1),
        (west, -1, 0),
    )
    best = None

    def visit(state, visited_mask, via_count):
        nonlocal best
        if graph.goals[state]:
            if best is None or via_count < best:
                best = via_count
            return

        cells = graph.width * graph.height
        layer, cell = divmod(state, cells)
        x, y = cell % graph.width, cell // graph.width
        for bit, dx, dy in directions:
            if not graph.steps[state] & bit:
                continue
            neighbor = (y + dy) * graph.width + (x + dx)
            target = layer * cells + neighbor
            target_bit = 1 << target
            if not visited_mask & target_bit:
                visit(target, visited_mask | target_bit, via_count)

        if graph.vias[cell]:
            target = (1 - layer) * cells + cell
            target_bit = 1 << target
            if not visited_mask & target_bit:
                visit(target, visited_mask | target_bit, via_count + 1)

    for start in graph.starts:
        visit(start, 1 << start, 0)
    return best


class MinimumViaContracts(unittest.TestCase):
    def test_literal_zero_one_via_and_unreachable_results(self):
        _zero_graph, zero = analyze(make_payload(
            width=3, height=1,
            steps=[2, 2, 0, 0, 0, 0],
            goals=[False, False, True, False, False, False],
            starts=[0], case_id="LITERAL_ZERO_VIA"))
        self.assertEqual(zero["schema"], "p07-fixed-prefix-minimum-vias-v1")
        self.assertEqual(set(zero["counters"]), {
            "uniqueStatesAdmitted", "statePops", "planarEdgesExamined",
            "viaChecks", "viaTransitionsExamined", "levelsStarted",
            "maximumQueueStates", "maximumPendingViaStates",
        })
        self.assertEqual(zero["minimumViaCount"], 0)
        self.assertEqual(zero["goalState"], 2)
        self.assertEqual(zero["termination"], "GOAL_POPPED")
        self.assertEqual(zero["counters"]["levelsStarted"], 1)
        self.assertFalse(zero["fullReachabilityCensus"])

        _one_graph, one = analyze(make_payload(
            vias=[True], goals=[False, True], starts=[0],
            case_id="LITERAL_ONE_VIA"))
        self.assertEqual(one["minimumViaCount"], 1)
        self.assertEqual(one["goalState"], 1)
        self.assertEqual(one["counters"]["levelsStarted"], 2)
        self.assertEqual(one["counters"]["viaChecks"], 1)
        self.assertEqual(one["counters"]["viaTransitionsExamined"], 1)

        _unreachable_graph, unreachable = analyze(make_payload(
            width=2, height=1,
            goals=[False, True, False, False], starts=[0],
            case_id="LITERAL_UNREACHABLE"))
        self.assertFalse(unreachable["reachable"])
        self.assertEqual(unreachable["status"], "UNREACHABLE")
        self.assertIsNone(unreachable["minimumViaCount"])
        self.assertIsNone(unreachable["goalState"])
        self.assertEqual(unreachable["termination"], "FRONTIER_EXHAUSTED")
        self.assertTrue(unreachable["fullReachabilityCensus"])

    def test_start_goal_is_zero_via_and_multiple_start_goal_order_is_stable(self):
        graph, record = analyze(make_payload(
            vias=[True], goals=[True, True], starts=[1, 0],
            case_id="MULTI_START_MULTI_GOAL"))
        self.assertEqual(record["minimumViaCount"], 0)
        self.assertEqual(record["goalState"], 0)
        self.assertEqual(record["counters"]["uniqueStatesAdmitted"], 2)
        self.assertEqual(record["counters"]["statePops"], 1)
        self.assertEqual(record["counters"]["levelsStarted"], 1)
        self.assertFalse(record["fullReachabilityCensus"])
        self.assertEqual(record["graphSha256"], graph.graph_sha256)

    def test_literal_two_via_path_starts_three_search_levels(self):
        _graph, record = analyze(make_payload(
            width=2, height=1,
            steps=[0, 0, 2, 0],
            vias=[True, True],
            goals=[False, True, False, False],
            starts=[0], case_id="LITERAL_TWO_VIAS"))

        self.assertEqual(record["minimumViaCount"], 2)
        self.assertEqual(record["goalState"], 1)
        self.assertEqual(record["termination"], "GOAL_POPPED")
        self.assertEqual(record["counters"]["levelsStarted"], 3)
        self.assertEqual(record["counters"]["viaTransitionsExamined"], 3)

    def test_deferred_via_target_does_not_hide_same_level_planar_goal(self):
        # From top 0, planar edges reach top 2 in level 0. Bottom 2 is also a
        # start and offers a one-VIA route to top 2. That via target must wait
        # without claiming top 2 before the current planar closure reaches it.
        _graph, record = analyze(make_payload(
            width=3, height=1,
            steps=[2, 2, 0, 0, 0, 0],
            vias=[False, False, True],
            goals=[False, False, True, False, False, False],
            starts=[0, 5], case_id="DEFERRED_PROMOTION"))

        self.assertEqual(record["minimumViaCount"], 0)
        self.assertEqual(record["goalState"], 2)
        counters = record["counters"]
        self.assertEqual(counters["uniqueStatesAdmitted"], 4)
        self.assertEqual(counters["statePops"], 4)
        self.assertEqual(counters["planarEdgesExamined"], 2)
        self.assertEqual(counters["viaChecks"], 3)
        self.assertEqual(counters["viaTransitionsExamined"], 1)
        self.assertEqual(counters["levelsStarted"], 1)
        self.assertEqual(counters["maximumQueueStates"], 2)
        self.assertEqual(counters["maximumPendingViaStates"], 1)

    def test_long_zero_via_route_beats_shorter_one_via_route(self):
        # Top 0 reaches the top-face goal through four planar edges. The other
        # start is at bottom 4, where one enabled VIA reaches the same goal.
        _graph, record = analyze(make_payload(
            width=5, height=1,
            steps=[2, 2, 2, 2, 0, 0, 0, 0, 0, 0],
            vias=[False, False, False, False, True],
            goals=[False, False, False, False, True,
                   False, False, False, False, False],
            starts=[0, 9], case_id="LONG_ZERO_BEATS_SHORT_VIA"))

        self.assertEqual(record["minimumViaCount"], 0)
        self.assertEqual(record["goalState"], 4)
        self.assertGreater(record["counters"]["statePops"], 1)
        self.assertGreater(record["counters"]["maximumPendingViaStates"], 0)

    def test_tiny_directed_graphs_against_exhaustive_simple_paths(self):
        # Four possible directed planar edges and two VIA locations produce 64
        # graph shapes. Compare each single-start/single-goal pair by exhaustive
        # simple-path enumeration, independent of the layered closure algorithm.
        edge_sources = (0, 1, 2, 3)
        for graph_bits in range(64):
            steps = [0, 0, 0, 0]
            for index, state in enumerate(edge_sources):
                if graph_bits & (1 << index):
                    steps[state] = 2 if state in (0, 2) else 8
            vias = [bool(graph_bits & (1 << 4)),
                    bool(graph_bits & (1 << 5))]
            for start in range(4):
                for goal in range(4):
                    goals = [False] * 4
                    goals[goal] = True
                    payload = make_payload(
                        width=2, height=1,
                        steps=steps,
                        vias=vias,
                        goals=goals,
                        starts=[start],
                        case_id="ENUM_%02X_%d_%d" % (graph_bits, start, goal),
                    )
                    graph = oracle.validate_payload(payload)
                    expected = exhaustive_simple_path_minimum(graph)
                    record = minimum_vias.analyze_minimum_vias(graph)
                    self.assertEqual(record["minimumViaCount"], expected)
                    self.assertEqual(record["reachable"], expected is not None)
                    if expected is not None:
                        self.assertTrue(graph.goals[record["goalState"]])
                        self.assertFalse(record["fullReachabilityCensus"])
                    else:
                        self.assertIsNone(record["goalState"])
                        self.assertTrue(record["fullReachabilityCensus"])

    def test_graph_is_immutable_and_output_never_grants_acceptance(self):
        graph = oracle.validate_payload(make_payload(
            width=2, height=1,
            steps=[2, 0, 0, 0],
            goals=[False, True, False, False], starts=[0],
            case_id="IMMUTABLE_GRAPH"))
        original_steps = graph.steps
        original_vias = graph.vias
        record = minimum_vias.analyze_minimum_vias(graph)

        self.assertEqual(graph.steps, original_steps)
        self.assertEqual(graph.vias, original_vias)
        with self.assertRaises((AttributeError, TypeError)):
            graph.steps[0] = 0
        self.assertEqual(record["productionFeasibility"], "UNKNOWN")
        self.assertEqual(record["acceptance"], {
            "actualCaptureAttested": False,
            "route": False,
            "layout": False,
            "electrical": False,
            "q100": False,
        })
        encoded = json.dumps(record, sort_keys=True, allow_nan=False)
        self.assertNotIn("witnessStates", encoded)
        self.assertNotIn("predecessor", encoded)

    def test_graph_type_and_structural_guards_use_static_codes(self):
        valid = oracle.validate_payload(make_payload(
            goals=[True, False], starts=[0], case_id="INVALID_GUARDS"))
        valid_step_graph = oracle.validate_payload(make_payload(
            width=2, height=1,
            goals=[False, True, False, False], starts=[0],
            case_id="STALE_STEP_HASH"))
        stale_hash_content_change = dataclasses.replace(
            valid_step_graph, steps=(2, 0, 0, 0))
        self.assertEqual(
            minimum_vias.analyze_minimum_vias(valid)["minimumViaCount"], 0)
        invalid_cases = (
            (object(), "GRAPH_TYPE_INVALID"),
            (dataclasses.replace(valid, width=True), "GRAPH_INVALID"),
            (dataclasses.replace(valid, steps=(True, 0)), "GRAPH_INVALID"),
            (dataclasses.replace(valid, vias=(1,)), "GRAPH_INVALID"),
            (dataclasses.replace(valid, starts=(False,)), "GRAPH_INVALID"),
            (dataclasses.replace(valid, goals=(False, 0)), "GRAPH_INVALID"),
            (dataclasses.replace(valid, steps=[0, 0]), "GRAPH_INVALID"),
            (stale_hash_content_change, "GRAPH_INVALID"),
        )
        for graph, expected_code in invalid_cases:
            with self.subTest(graph=type(graph).__name__, code=expected_code):
                with self.assertRaises(minimum_vias.MinimumViaError) as caught:
                    minimum_vias.analyze_minimum_vias(graph)
                self.assertEqual(caught.exception.code, expected_code)
                self.assertEqual(str(caught.exception), expected_code)


if __name__ == "__main__":
    unittest.main(verbosity=2)
