#!/usr/bin/env python3
"""Literal synthetic contracts for fixed-prefix witness metrics."""

from __future__ import annotations

import dataclasses
import json
import unittest

import fixed_prefix_witness_metrics as metrics
import read_fixed_prefix_graph as oracle


def make_payload(width=1, height=1, primary=0, steps=None, vias=None,
                 goals=None, starts=None, case_id="METRICS_SYNTHETIC"):
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


def analyzed_result(payload):
    graph = oracle.validate_payload(payload)
    return graph, oracle.analyze_graph(graph)


def manual_result(graph, states, edge_kinds, cost):
    """Build a structurally valid literal path without a second search oracle."""
    states = tuple(states)
    edge_kinds = tuple(edge_kinds)
    return oracle.OracleResult(
        graph=graph,
        reachable=True,
        minimum_cost=cost,
        start_state=states[0],
        goal_state=states[-1],
        witness_states=states,
        witness_edge_kinds=edge_kinds,
        witness_sha256=oracle._witness_digest(states, edge_kinds, cost),
        settled_states=0,
        heap_pops=0,
        ordinary_edges_examined=0,
        via_checks=0,
        strict_relaxations=0,
    )


def summarize(graph, result, **kwargs):
    values = {"prefix_hole_count": 0}
    values.update(kwargs)
    return metrics.summarize_witness(graph, result, **values)


class FixedPrefixWitnessMetricsContracts(unittest.TestCase):
    def test_straight_path_has_no_turn_penalty_and_literal_cost(self):
        payload = make_payload(
            width=3, height=1,
            steps=[2, 2, 0, 0, 0, 0],
            goals=[False, False, True, False, False, False],
            starts=[0], case_id="STRAIGHT_LITERAL",
        )
        graph, result = analyzed_result(payload)
        record = summarize(graph, result)
        witness = record["witness"]

        self.assertEqual(result.minimum_cost, 20)
        self.assertEqual(witness["stateCount"], 3)
        self.assertEqual(witness["edgeCount"], 2)
        self.assertEqual(witness["stepCount"], 2)
        self.assertEqual(witness["viaCount"], 0)
        self.assertEqual(witness["planarTurnCount"], 0)
        self.assertEqual(witness["turnPenalty"], 0)
        self.assertEqual(witness["productionStyleCost"], 20)
        self.assertFalse(record["historySubset"]["viaHoleBudget"]["applicable"])
        self.assertIsNone(record["historySubset"]["viaHoleBudget"]["conforms"])

    def test_planar_turn_adds_35_after_first_unpenalized_step(self):
        payload = make_payload(
            width=2, height=2,
            steps=[2, 4, 0, 0, 0, 0, 0, 0],
            goals=[False, False, False, True, False, False, False, False],
            starts=[0], case_id="ONE_TURN_LITERAL",
        )
        graph, result = analyzed_result(payload)
        record = summarize(graph, result)
        witness = record["witness"]

        self.assertEqual(result.minimum_cost, 20)
        self.assertEqual(witness["stepCount"], 2)
        self.assertEqual(witness["planarTurnCount"], 1)
        self.assertEqual(witness["turnPenalty"], 35)
        self.assertEqual(witness["productionStyleCost"], 55)

    def test_via_resets_planar_direction_for_turn_count(self):
        # EAST on the primary face, VIA, then SOUTH on the secondary face.
        payload = make_payload(
            width=2, height=2,
            steps=[2, 0, 0, 0, 0, 4, 0, 0],
            vias=[False, True, False, False],
            goals=[False, False, False, False, False, False, False, True],
            starts=[0], case_id="VIA_RESETS_DIRECTION",
        )
        graph, result = analyzed_result(payload)
        record = summarize(graph, result)
        witness = record["witness"]

        self.assertEqual(result.witness_edge_kinds,
                         ("STEP_EAST", "VIA", "STEP_SOUTH"))
        self.assertEqual(result.minimum_cost, 82)
        self.assertEqual(witness["stateCount"], 4)
        self.assertEqual(witness["stepCount"], 2)
        self.assertEqual(witness["viaCount"], 1)
        self.assertEqual(witness["planarTurnCount"], 0)
        self.assertEqual(witness["productionStyleCost"], 82)

    def test_initial_and_consecutive_via_are_reported_at_first_edges(self):
        payload = make_payload(
            width=2, height=1,
            steps=[2, 0, 0, 0], vias=[True, False],
            goals=[False, True, False, False], starts=[0],
            case_id="INITIAL_CONSECUTIVE_VIA",
        )
        graph = oracle.validate_payload(payload)
        result = manual_result(graph, (0, 2, 0, 1),
                               ("VIA", "VIA", "STEP_EAST"), 130)
        record = summarize(graph, result)
        history = record["historySubset"]

        self.assertEqual(record["witness"]["viaCount"], 2)
        self.assertFalse(history["noInitialVia"]["conforms"])
        self.assertEqual(history["noInitialVia"]["firstViolation"]["edgeIndex"], 0)
        self.assertFalse(history["noConsecutiveVia"]["conforms"])
        self.assertEqual(history["noConsecutiveVia"]["firstViolation"]["edgeIndex"], 1)
        self.assertFalse(history["viaSpacing"]["conforms"])
        self.assertFalse(history["allApplicableChecksConform"])

    def _five_transition_witness(self):
        # Five VIA edges separated by directed east steps; the first edge is planar.
        payload = make_payload(
            width=6, height=1,
            steps=[2, 0, 2, 0, 2, 0, 0, 2, 0, 2, 0, 0],
            vias=[False, True, True, True, True, True],
            goals=[False] * 11 + [True], starts=[0],
            case_id="FIVE_VIA_TRANSITIONS",
        )
        graph = oracle.validate_payload(payload)
        states = (0, 1, 7, 8, 2, 3, 9, 10, 4, 5, 11)
        kinds = ("STEP_EAST", "VIA", "STEP_EAST", "VIA", "STEP_EAST",
                 "VIA", "STEP_EAST", "VIA", "STEP_EAST", "VIA")
        return graph, manual_result(graph, states, kinds, 354)

    def test_fifth_transition_reports_first_overflow_edge(self):
        graph, result = self._five_transition_witness()
        record = summarize(graph, result)
        check = record["historySubset"]["transitionLimit"]

        self.assertEqual(record["witness"]["viaCount"], 5)
        self.assertEqual(check["observedViaCount"], 5)
        self.assertEqual(check["maximum"], 4)
        self.assertFalse(check["conforms"])
        self.assertEqual(check["firstViolation"]["edgeIndex"], 9)
        self.assertTrue(record["historySubset"]["noInitialVia"]["conforms"])
        self.assertTrue(record["historySubset"]["noConsecutiveVia"]["conforms"])

    def test_hole_budget_is_checked_on_addition_and_accepts_exact_boundary(self):
        payload = make_payload(
            width=3, height=1,
            steps=[2, 0, 0, 0, 2, 0],
            vias=[False, True, True],
            goals=[False, False, True, False, False, False],
            starts=[0], case_id="TWO_VIA_HOLE_BOUNDARY",
        )
        graph = oracle.validate_payload(payload)
        result = manual_result(
            graph, (0, 1, 4, 5, 2),
            ("STEP_EAST", "VIA", "STEP_EAST", "VIA"), 142)

        exact = summarize(graph, result, prefix_hole_count=46,
                          maximum_vias=48)
        exact_budget = exact["historySubset"]["viaHoleBudget"]
        self.assertTrue(exact_budget["conforms"])
        self.assertIsNone(exact_budget["firstViolation"])
        self.assertEqual(exact_budget["checkedViaCount"], 2)

        overflow = summarize(graph, result, prefix_hole_count=47,
                             maximum_vias=48)
        overflow_budget = overflow["historySubset"]["viaHoleBudget"]
        self.assertFalse(overflow_budget["conforms"])
        self.assertEqual(overflow_budget["firstViolation"]["edgeIndex"], 3)
        self.assertEqual(overflow_budget["firstViolation"]["holeCountAfterAddition"], 49)

        zero_payload = make_payload(
            goals=[True, False], starts=[0], case_id="ZERO_VIA_HOLE_GATE")
        zero_graph, zero_result = analyzed_result(zero_payload)
        zero = summarize(zero_graph, zero_result, prefix_hole_count=49,
                         maximum_vias=48)
        zero_budget = zero["historySubset"]["viaHoleBudget"]
        self.assertFalse(zero_budget["applicable"])
        self.assertIsNone(zero_budget["conforms"])
        self.assertIsNone(zero_budget["firstViolation"])

    def _two_spaced_via_witness(self):
        payload = make_payload(
            width=3, height=1,
            steps=[2, 0, 0, 0, 2, 0],
            vias=[False, True, True],
            goals=[False, False, True, False, False, False],
            starts=[0], case_id="TWO_SPACED_VIAS",
        )
        return analyzed_result(payload)

    def test_spacing_uses_strict_axiswise_20_unit_predicate(self):
        graph, result = self._two_spaced_via_witness()
        nineteen = summarize(graph, result, grid_pitch=19)
        nineteen_check = nineteen["historySubset"]["viaSpacing"]
        self.assertEqual(nineteen_check["coordinateSpace"],
                         "GRID_ORIGIN_RELATIVE_UNITS")
        self.assertFalse(nineteen_check["conforms"])
        self.assertEqual(nineteen_check["firstViolation"]["secondCoordinate"],
                         {"x": 38, "y": 0})
        self.assertEqual(nineteen_check["checkedPairCount"], 1)

        twenty = summarize(graph, result, grid_pitch=20)
        twenty_check = twenty["historySubset"]["viaSpacing"]
        self.assertTrue(twenty_check["conforms"])
        self.assertIsNone(twenty_check["firstViolation"])

    def test_diagonal_19_by_19_is_rejected_even_above_20_euclidean(self):
        payload = make_payload(
            width=2, height=2,
            steps=[0, 8, 0, 0, 2, 4, 0, 0],
            vias=[True, False, False, True],
            goals=[False, False, False, True, False, False, False, False],
            starts=[1], case_id="DIAGONAL_OPEN_SQUARE",
        )
        graph = oracle.validate_payload(payload)
        result = manual_result(
            graph, (1, 0, 4, 5, 7, 3),
            ("STEP_WEST", "VIA", "STEP_EAST", "STEP_SOUTH", "VIA"), 154)
        record = summarize(graph, result, grid_pitch=19)
        check = record["historySubset"]["viaSpacing"]

        first = check["firstViolation"]["firstCoordinate"]
        second = check["firstViolation"]["secondCoordinate"]
        self.assertEqual(first, {"x": 0, "y": 0})
        self.assertEqual(second, {"x": 19, "y": 19})
        self.assertGreater((19 ** 2 + 19 ** 2) ** 0.5, 20)
        self.assertFalse(check["conforms"])

    def test_unreachable_and_zero_edge_witness_have_truthful_metrics(self):
        unreachable_payload = make_payload(
            width=2, height=1,
            goals=[False, True, False, False], starts=[0],
            case_id="UNREACHABLE_NO_EDGE",
        )
        graph, result = analyzed_result(unreachable_payload)
        unreachable = summarize(graph, result)
        self.assertFalse(unreachable["witness"]["reachable"])
        self.assertEqual(unreachable["witness"]["stateCount"], 0)
        self.assertEqual(unreachable["witness"]["stepCount"], 0)
        self.assertIsNone(unreachable["witness"]["minimumRelaxedCost"])
        self.assertEqual(unreachable["historySubset"]["evaluation"], "NO_WITNESS")
        self.assertIsNone(unreachable["historySubset"]["allApplicableChecksConform"])
        self.assertEqual(
            unreachable["historySubset"]["viaSpacing"]["coordinateSpace"],
            "GRID_ORIGIN_RELATIVE_UNITS")

        zero_payload = make_payload(
            goals=[True, False], starts=[0], case_id="ZERO_EDGE_REACHABLE")
        zero_graph, zero_result = analyzed_result(zero_payload)
        zero = summarize(zero_graph, zero_result)
        self.assertTrue(zero["witness"]["reachable"])
        self.assertEqual(zero["witness"]["stateCount"], 1)
        self.assertEqual(zero["witness"]["edgeCount"], 0)
        self.assertEqual(zero["witness"]["minimumRelaxedCost"], 0)
        self.assertEqual(zero["witness"]["productionStyleCost"], 0)
        self.assertTrue(zero["historySubset"]["allApplicableChecksConform"])
        self.assertFalse(zero["historySubset"]["viaHoleBudget"]["applicable"])
        self.assertEqual(zero["productionFeasibility"], "UNKNOWN")
        self.assertFalse(any(zero["acceptance"].values()))
        encoded = json.dumps(zero, sort_keys=True, allow_nan=False)
        self.assertNotIn("witnessStates", encoded)
        self.assertNotIn("edgeKinds", encoded)

    def test_invalid_parameter_types_ranges_and_bounds_are_static(self):
        graph, result = self._two_spaced_via_witness()
        cases = (
            ("prefix_hole_count", True, "PREFIX_HOLE_COUNT_INVALID"),
            ("prefix_hole_count", 1.0, "PREFIX_HOLE_COUNT_INVALID"),
            ("prefix_hole_count", -1, "PREFIX_HOLE_COUNT_INVALID"),
            ("prefix_hole_count", oracle.MAX_CELLS + 1,
             "PREFIX_HOLE_COUNT_INVALID"),
            ("maximum_vias", True, "MAXIMUM_VIAS_INVALID"),
            ("maximum_vias", 48.0, "MAXIMUM_VIAS_INVALID"),
            ("maximum_vias", -1, "MAXIMUM_VIAS_INVALID"),
            ("maximum_vias", oracle.MAX_CELLS + 1, "MAXIMUM_VIAS_INVALID"),
            ("max_transitions", False, "MAX_TRANSITIONS_INVALID"),
            ("max_transitions", 4.0, "MAX_TRANSITIONS_INVALID"),
            ("max_transitions", -1, "MAX_TRANSITIONS_INVALID"),
            ("max_transitions", oracle.MAX_CELLS + 1,
             "MAX_TRANSITIONS_INVALID"),
            ("grid_pitch", True, "GRID_PITCH_INVALID"),
            ("grid_pitch", 0, "GRID_PITCH_INVALID"),
            ("grid_pitch", -1, "GRID_PITCH_INVALID"),
            ("grid_pitch", 1.5, "GRID_PITCH_INVALID"),
            ("grid_pitch", metrics.MAX_GRID_PITCH + 1, "GRID_PITCH_INVALID"),
        )
        for parameter, value, expected_code in cases:
            with self.subTest(parameter=parameter, value=value):
                with self.assertRaises(metrics.WitnessMetricError) as caught:
                    summarize(graph, result, **{parameter: value})
                self.assertEqual(caught.exception.code, expected_code)
                self.assertEqual(str(caught.exception), expected_code)

    def test_witness_edges_endpoints_cost_and_digest_are_validated(self):
        payload = make_payload(
            width=2, height=1,
            steps=[2, 0, 0, 0],
            goals=[False, True, False, False], starts=[0],
            case_id="WITNESS_VALIDATION",
        )
        graph, result = analyzed_result(payload)

        invalid_edge = dataclasses.replace(
            result, witness_edge_kinds=("STEP_WEST",))
        with self.assertRaises(metrics.WitnessMetricError) as edge_error:
            summarize(graph, invalid_edge, prefix_hole_count=0)
        self.assertEqual(edge_error.exception.code, "WITNESS_EDGE_INVALID")

        invalid_cost = dataclasses.replace(result, minimum_cost=11)
        with self.assertRaises(metrics.WitnessMetricError) as cost_error:
            summarize(graph, invalid_cost, prefix_hole_count=0)
        self.assertEqual(cost_error.exception.code, "WITNESS_COST_MISMATCH")

        invalid_digest = dataclasses.replace(result, witness_sha256="0" * 64)
        with self.assertRaises(metrics.WitnessMetricError) as digest_error:
            summarize(graph, invalid_digest, prefix_hole_count=0)
        self.assertEqual(digest_error.exception.code, "WITNESS_DIGEST_MISMATCH")

        invalid_state_type = dataclasses.replace(
            result, witness_states=(True, 1))
        with self.assertRaises(metrics.WitnessMetricError) as state_error:
            summarize(graph, invalid_state_type, prefix_hole_count=0)
        self.assertEqual(state_error.exception.code, "WITNESS_INVALID")

        another_graph, _ = analyzed_result(make_payload(
            width=2, height=1, steps=[0, 0, 0, 0],
            goals=[False, True, False, False], starts=[0],
            case_id="OTHER_GRAPH"))
        with self.assertRaises(metrics.WitnessMetricError) as mismatch_error:
            summarize(another_graph, result, prefix_hole_count=0)
        self.assertEqual(mismatch_error.exception.code, "RESULT_GRAPH_MISMATCH")

    def test_acceptance_and_feasibility_never_promote_history_subset(self):
        payload = make_payload(
            width=2, height=1,
            steps=[2, 0, 0, 0],
            goals=[False, True, False, False], starts=[0],
            case_id="ALL_SUBSET_CHECKS_PASS",
        )
        graph, result = analyzed_result(payload)
        record = summarize(graph, result)

        self.assertTrue(record["historySubset"]["allApplicableChecksConform"])
        self.assertEqual(record["productionFeasibility"], "UNKNOWN")
        self.assertEqual(record["acceptance"], {
            "actualCaptureAttested": False,
            "route": False,
            "layout": False,
            "electrical": False,
            "q100": False,
        })


if __name__ == "__main__":
    unittest.main(verbosity=2)
