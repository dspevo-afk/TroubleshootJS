#!/usr/bin/env python3
"""Synthetic contracts for the independent fixed-prefix graph oracle."""

from __future__ import annotations

import contextlib
import hashlib
import io
import itertools
import json
import os
import tempfile
import unittest

import read_fixed_prefix_graph as oracle


def make_payload(width=1, height=1, primary=0, steps=None, vias=None,
                 goals=None, starts=None, case_id="SYNTHETIC"):
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


def edge_cost(graph, first, second, edge_kind):
    cells = graph.cells
    first_layer, first_cell = divmod(first, cells)
    second_layer, second_cell = divmod(second, cells)
    if edge_kind == "VIA":
        if first_layer == second_layer or first_cell != second_cell or not graph.vias[first_cell]:
            raise AssertionError("witness contains an invalid via transition")
        return oracle.VIA_COST
    if first_layer != second_layer:
        raise AssertionError("ordinary witness transition changes face")
    x0, y0 = first_cell % graph.width, first_cell // graph.width
    x1, y1 = second_cell % graph.width, second_cell // graph.width
    delta = (x1 - x0, y1 - y0)
    expected = {
        "STEP_NORTH": (0, -1, oracle.NORTH),
        "STEP_EAST": (1, 0, oracle.EAST),
        "STEP_SOUTH": (0, 1, oracle.SOUTH),
        "STEP_WEST": (-1, 0, oracle.WEST),
    }
    if edge_kind not in expected:
        raise AssertionError("witness contains an unknown edge kind")
    dx, dy, bit = expected[edge_kind]
    if delta != (dx, dy) or not (graph.steps[first] & bit):
        raise AssertionError("witness ordinary edge is absent or points the wrong way")
    return oracle.PRIMARY_STEP_COST if first_layer == graph.primary else oracle.SECONDARY_STEP_COST


def verify_witness(graph, result):
    if not result.reachable:
        if result.witness_states or result.witness_edge_kinds or result.minimum_cost is not None:
            raise AssertionError("unreachable result exposed a path")
        return
    states = result.witness_states
    edges = result.witness_edge_kinds
    if not states or len(edges) + 1 != len(states):
        raise AssertionError("witness dimensions do not agree")
    if states[0] not in graph.starts or not graph.goals[states[-1]]:
        raise AssertionError("witness endpoints are not declared starts/goals")
    cost = 0 if states[0] // graph.cells == graph.primary else oracle.SECONDARY_START_COST
    for index, kind in enumerate(edges):
        cost += edge_cost(graph, states[index], states[index + 1], kind)
    if cost != result.minimum_cost:
        raise AssertionError("witness edge costs do not sum to the reported cost")


def floyd_warshall_cost(payload, start, goal):
    """Independent tiny-graph reference using literal frozen costs."""
    width, height = payload["width"], payload["height"]
    cells = width * height
    states = cells * 2
    infinity = 10 ** 15
    distance = [[infinity] * states for _ in range(states)]
    for state in range(states):
        distance[state][state] = 0
    for layer in range(2):
        for cell in range(cells):
            x, y = cell % width, cell // width
            state = layer * cells + cell
            mask = payload["steps"][state]
            neighbors = (
                (oracle.NORTH, 0, -1),
                (oracle.EAST, 1, 0),
                (oracle.SOUTH, 0, 1),
                (oracle.WEST, -1, 0),
            )
            cost = 10 if layer == payload["primary"] else 12
            for bit, dx, dy in neighbors:
                if mask & bit:
                    target = layer * cells + (y + dy) * width + x + dx
                    distance[state][target] = cost
    for cell, allowed in enumerate(payload["vias"]):
        if allowed:
            distance[cell][cells + cell] = 60
            distance[cells + cell][cell] = 60
    for pivot in range(states):
        for source in range(states):
            if distance[source][pivot] >= infinity:
                continue
            for target in range(states):
                candidate = distance[source][pivot] + distance[pivot][target]
                if candidate < distance[source][target]:
                    distance[source][target] = candidate
    if distance[start][goal] >= infinity:
        return None
    start_face = start // cells
    initial_cost = 0 if start_face == payload["primary"] else 2
    return initial_cost + distance[start][goal]


class FixedPrefixGraphOracleContracts(unittest.TestCase):
    def test_root_cli_fixture_expected_cost_and_witness(self):
        payload = make_payload(
            width=2,
            height=1,
            primary=0,
            steps=[2, 0, 2, 0],
            vias=[False, True],
            goals=[False, False, False, True],
            starts=[0],
            case_id="ROOT_VIA_POSITIVE",
        )
        result = oracle.analyze_graph(oracle.validate_payload(payload))
        self.assertTrue(result.reachable)
        self.assertEqual(result.minimum_cost, 70)
        self.assertEqual(result.witness_states, (0, 1, 3))
        self.assertEqual(result.witness_edge_kinds, ("STEP_EAST", "VIA"))
        verify_witness(result.graph, result)

    def test_literal_asymmetric_two_face_path(self):
        # Row-major cells 0..5.  Top has 0->1->2; bottom has only 2->5.
        # The only layer transition is at cell 2, and the exact goal is bottom 5.
        payload = make_payload(
            width=3,
            height=2,
            primary=0,
            steps=[2, 2, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0],
            vias=[False, False, True, False, False, False],
            goals=[False] * 11 + [True],
            starts=[0],
            case_id="LITERAL_ASYMMETRIC_PATH",
        )
        graph = oracle.validate_payload(payload)
        result = oracle.analyze_graph(graph)
        self.assertEqual(result.minimum_cost, 92)
        self.assertEqual(result.witness_states, (0, 1, 2, 8, 11))
        self.assertEqual(result.witness_edge_kinds,
                         ("STEP_EAST", "STEP_EAST", "VIA", "STEP_SOUTH"))
        verify_witness(graph, result)

        reverse_payload = dict(payload)
        reverse_payload["caseId"] = "LITERAL_ASYMMETRIC_REVERSE"
        reverse_payload["starts"] = [11]
        reverse_payload["goals"] = [True] + [False] * 11
        reverse = oracle.analyze_graph(oracle.validate_payload(reverse_payload))
        self.assertFalse(reverse.reachable)
        verify_witness(reverse.graph, reverse)

    def test_wrong_goal_face_and_via_present_absent(self):
        absent = make_payload(
            primary=0, goals=[True, False], starts=[1], case_id="WRONG_FACE_NO_VIA"
        )
        absent_result = oracle.analyze_graph(oracle.validate_payload(absent))
        self.assertFalse(absent_result.reachable)

        present = make_payload(
            primary=0, vias=[True], goals=[True, False], starts=[1],
            case_id="WRONG_FACE_WITH_VIA",
        )
        present_result = oracle.analyze_graph(oracle.validate_payload(present))
        self.assertTrue(present_result.reachable)
        self.assertEqual(present_result.minimum_cost, 62)
        self.assertEqual(present_result.witness_edge_kinds, ("VIA",))
        verify_witness(present_result.graph, present_result)

    def test_blocked_cell_and_disconnected_graph(self):
        # The first edge enters cell 1, but the graph supplies no edge out of it
        # and no via there; this is only an isolated encoded graph, not a claim
        # about why a physical router would block that cell.
        payload = make_payload(
            width=3,
            height=1,
            steps=[2, 0, 0, 0, 0, 0],
            vias=[False, False, False],
            goals=[False, False, True, False, False, False],
            starts=[0],
            case_id="BLOCKED_CELL_DISCONNECTED",
        )
        result = oracle.analyze_graph(oracle.validate_payload(payload))
        self.assertFalse(result.reachable)
        self.assertIsNone(result.minimum_cost)
        verify_witness(result.graph, result)

        no_goals = make_payload(
            width=2, height=1, starts=[0], goals=[False] * 4,
            case_id="ZERO_GOALS_UNREACHABLE",
        )
        no_goal_result = oracle.analyze_graph(oracle.validate_payload(no_goals))
        self.assertFalse(no_goal_result.reachable)
        self.assertIsNone(no_goal_result.minimum_cost)
        verify_witness(no_goal_result.graph, no_goal_result)

    def test_zero_cost_start_goal_and_secondary_start_cost(self):
        zero = make_payload(goals=[True, False], starts=[0], case_id="ZERO_START_GOAL")
        zero_result = oracle.analyze_graph(oracle.validate_payload(zero))
        self.assertTrue(zero_result.reachable)
        self.assertEqual(zero_result.minimum_cost, 0)
        self.assertEqual(zero_result.witness_states, (0,))
        verify_witness(zero_result.graph, zero_result)

        secondary = make_payload(
            width=2,
            height=1,
            primary=1,
            steps=[2, 0, 0, 0],
            goals=[False, True, False, False],
            starts=[0],
            case_id="SECONDARY_START_STEP",
        )
        secondary_result = oracle.analyze_graph(oracle.validate_payload(secondary))
        self.assertEqual(secondary_result.minimum_cost, 14)
        verify_witness(secondary_result.graph, secondary_result)

    def test_multi_goal_exact_face_and_deterministic_tie(self):
        multiple = make_payload(
            width=2,
            height=1,
            steps=[2, 0, 0, 0],
            vias=[False, True],
            goals=[False, True, False, True],
            starts=[0],
            case_id="MULTI_GOAL_EXACT_FACE",
        )
        result = oracle.analyze_graph(oracle.validate_payload(multiple))
        self.assertEqual(result.goal_state, 1)
        self.assertEqual(result.minimum_cost, 10)

        two_starts = make_payload(
            primary=1, starts=[0, 1], goals=[False, True], case_id="TWO_DISTINCT_STARTS"
        )
        two_start_result = oracle.analyze_graph(oracle.validate_payload(two_starts))
        self.assertEqual(two_start_result.witness_states, (1,))
        self.assertEqual(two_start_result.minimum_cost, 0)
        verify_witness(two_start_result.graph, two_start_result)

        tied = make_payload(
            width=2,
            height=2,
            steps=[6, 4, 2, 0, 0, 0, 0, 0],
            goals=[False, False, False, True, False, False, False, False],
            starts=[0],
            case_id="DETERMINISTIC_EQUAL_COST",
        )
        first = oracle.analyze_graph(oracle.validate_payload(tied))
        second = oracle.analyze_graph(oracle.validate_payload(tied))
        self.assertEqual(first.minimum_cost, 20)
        self.assertEqual(first.witness_states, (0, 1, 3))
        self.assertEqual(first.witness_states, second.witness_states)
        self.assertEqual(first.witness_edge_kinds, second.witness_edge_kinds)
        self.assertEqual(first.witness_sha256, second.witness_sha256)
        verify_witness(first.graph, first)

    def test_64_step_and_via_graphs_against_floyd_warshall(self):
        comparisons = 0
        for graph_bits in range(64):
            steps = [0, 0, 0, 0]
            if graph_bits & (1 << 0):
                steps[0] |= oracle.EAST       # top 0 -> 1
            if graph_bits & (1 << 1):
                steps[1] |= oracle.WEST       # top 1 -> 0
            if graph_bits & (1 << 2):
                steps[2] |= oracle.EAST       # bottom 0 -> 1
            if graph_bits & (1 << 3):
                steps[3] |= oracle.WEST       # bottom 1 -> 0
            vias = [bool(graph_bits & (1 << 4)), bool(graph_bits & (1 << 5))]
            for start, goal in itertools.product(range(4), repeat=2):
                goals = [False] * 4
                goals[goal] = True
                payload = make_payload(
                    width=2,
                    height=1,
                    primary=0,
                    steps=steps,
                    vias=vias,
                    goals=goals,
                    starts=[start],
                    case_id="ENUM_%02X_%d_%d" % (graph_bits, start, goal),
                )
                graph = oracle.validate_payload(payload)
                result = oracle.analyze_graph(graph)
                expected = floyd_warshall_cost(payload, start, goal)
                self.assertEqual(result.minimum_cost, expected)
                self.assertEqual(result.reachable, expected is not None)
                verify_witness(graph, result)
                comparisons += 1
        self.assertEqual(comparisons, 1024)

    def test_immutable_snapshot_and_exact_canonical_digest(self):
        payload = make_payload(
            width=2,
            height=1,
            steps=[2, 0, 0, 0],
            goals=[False, True, False, False],
            starts=[0],
            case_id="CANONICAL_HASH",
        )
        graph = oracle.validate_payload(payload)
        canonical = (b'{"caseId":"CANONICAL_HASH","goals":[false,true,false,false],'
                     b'"height":1,"model":"DIRECTED_TWO_FACE_HISTORY_RELAXED",'
                     b'"primary":0,"protocol":"p07-fixed-prefix-graph-v1",'
                     b'"starts":[0],"steps":[2,0,0,0],"vias":[false,false],"width":2}')
        expected = "7bbeb134d37c45dd046121dbe6f2823538a901c8fae480e05c6f998910f8fcbb"
        self.assertEqual(hashlib.sha256(canonical).hexdigest(), expected)
        self.assertEqual(graph.graph_sha256, expected)

        payload["steps"][0] = 0
        payload["starts"][0] = 3
        payload["goals"][1] = False
        self.assertEqual(graph.steps, (2, 0, 0, 0))
        self.assertEqual(graph.starts, (0,))
        self.assertEqual(graph.goals, (False, True, False, False))
        self.assertEqual(graph.graph_sha256, expected)
        with self.assertRaises((AttributeError, TypeError)):
            graph.steps[0] = 0

    def test_strict_types_keys_boundaries_and_resource_limits(self):
        valid = make_payload(width=2, height=2)
        for field, value in (("width", True), ("height", 1.0), ("primary", False)):
            changed = dict(valid)
            changed[field] = value
            with self.subTest(field=field, value=value):
                with self.assertRaises(oracle.GraphInputError):
                    oracle.validate_payload(changed)

        for field, value in (("steps", [False] + [0] * 7),
                             ("vias", [False, 1, False, False]),
                             ("goals", [False] * 7 + [0]),
                             ("starts", [True])):
            changed = dict(valid)
            changed[field] = value
            with self.subTest(field=field):
                with self.assertRaises(oracle.GraphInputError):
                    oracle.validate_payload(changed)

        with self.assertRaises(oracle.GraphInputError):
            oracle.validate_payload(dict(valid, unknown=False))
        missing = dict(valid)
        del missing["model"]
        with self.assertRaises(oracle.GraphInputError):
            oracle.validate_payload(missing)
        with self.assertRaises(oracle.GraphInputError):
            oracle.validate_payload(dict(valid, caseId="C:/private/path"))
        with self.assertRaises(oracle.GraphInputError):
            oracle.validate_payload(dict(valid, width=250001, height=1))
        for starts in ([], [0, 0], [0, 1, 2], [8]):
            with self.subTest(starts=starts):
                with self.assertRaises(oracle.GraphInputError):
                    oracle.validate_payload(dict(valid, starts=starts))

        boundary_cases = (
            (2, 2, 0, oracle.NORTH),
            (2, 2, 1, oracle.EAST),
            (2, 2, 2, oracle.SOUTH),
            (2, 2, 0, oracle.WEST),
            (2, 1, 1, oracle.EAST),  # explicit row-end wrap rejection
        )
        for width, height, cell, bit in boundary_cases:
            cells = width * height
            changed = make_payload(width=width, height=height)
            changed["steps"][cell] = bit
            with self.subTest(width=width, height=height, cell=cell, bit=bit):
                with self.assertRaises(oracle.GraphInputError) as caught:
                    oracle.validate_payload(changed)
                self.assertEqual(caught.exception.code, "STEP_LEAVES_GRID")

        with self.assertRaises(oracle.GraphInputError) as too_large:
            oracle.parse_graph_bytes(b" " * (oracle.MAX_INPUT_BYTES + 1))
        self.assertEqual(too_large.exception.code, "INPUT_TOO_LARGE")

    def test_json_duplicate_nonfinite_encoding_and_bom(self):
        duplicate = b'{"protocol":"x","protocol":"y"}'
        with self.assertRaises(oracle.GraphInputError) as duplicate_error:
            oracle.parse_graph_bytes(duplicate)
        self.assertEqual(duplicate_error.exception.code, "DUPLICATE_JSON_KEY")

        with self.assertRaises(oracle.GraphInputError) as nonfinite_error:
            oracle.parse_graph_bytes(b'{"value":NaN}')
        self.assertEqual(nonfinite_error.exception.code, "NONFINITE_JSON_NUMBER")

        deep = b"[" * 4096 + b"0" + b"]" * 4096
        with self.assertRaises(oracle.GraphInputError) as deep_error:
            oracle.parse_graph_bytes(deep)
        self.assertEqual(deep_error.exception.code, "JSON_INVALID")

        with self.assertRaises(oracle.GraphInputError) as encoding_error:
            oracle.parse_graph_bytes(b"\xff")
        self.assertEqual(encoding_error.exception.code, "INPUT_ENCODING_INVALID")

        payload = make_payload(goals=[True, False], starts=[0], case_id="LEADING_BOM")
        encoded = json.dumps(payload, separators=(",", ":")).encode("ascii")
        self.assertEqual(oracle.parse_graph_bytes(b"\xef\xbb\xbf" + encoded).case_id,
                         "LEADING_BOM")
        with self.assertRaises(oracle.GraphInputError):
            oracle.parse_graph_bytes(b"\xef\xbb\xbf\xef\xbb\xbf" + encoded)

    def test_cli_single_json_and_sanitized_negative_results(self):
        payload = make_payload(
            width=2,
            height=1,
            steps=[2, 0, 2, 0],
            vias=[False, True],
            goals=[False, False, False, True],
            starts=[0],
            case_id="ROOT_VIA_POSITIVE",
        )
        with tempfile.TemporaryDirectory() as directory:
            good_path = os.path.join(directory, "graph.json")
            bad_path = os.path.join(directory, "malformed.json")
            missing_path = os.path.join(directory, "private-missing-name.json")
            with open(good_path, "wb") as output:
                output.write(json.dumps(payload, separators=(",", ":")).encode("ascii"))
            with open(bad_path, "wb") as output:
                output.write(b"{\"unknownPersonalMarker\":NaN}")

            good_text, good_code = self.call_main([good_path])
            self.assertEqual(good_code, 0)
            self.assertEqual(len(good_text.splitlines()), 1)
            good_record = json.loads(good_text)
            self.assertEqual(good_record["minimumRelaxedCost"], 70)
            self.assertEqual(good_record["witness"]["statePrefix"], [0, 1, 3])
            self.assertEqual(good_record["witness"]["stateCount"], 3)
            self.assertTrue(good_record["search"]["fullReachabilityCensus"] is False)
            self.assertFalse(any(good_record["acceptance"].values()))
            self.assertNotIn(good_path, good_text)

            bad_text, bad_code = self.call_main([bad_path])
            self.assertEqual(bad_code, 1)
            self.assertEqual(len(bad_text.splitlines()), 1)
            self.assertEqual(json.loads(bad_text)["error"], "NONFINITE_JSON_NUMBER")
            self.assertNotIn(bad_path, bad_text)
            self.assertNotIn("unknownPersonalMarker", bad_text)

            missing_text, missing_code = self.call_main([missing_path])
            self.assertEqual(missing_code, 2)
            self.assertEqual(len(missing_text.splitlines()), 1)
            self.assertEqual(json.loads(missing_text)["error"], "INPUT_UNAVAILABLE")
            self.assertNotIn(missing_path, missing_text)

    def test_cli_bounds_long_witness_prefix_but_reports_full_digest_and_counts(self):
        width = 70
        steps = [0] * (2 * width)
        for cell in range(width - 1):
            steps[cell] = oracle.EAST
        goals = [False] * (2 * width)
        goals[width - 1] = True
        payload = make_payload(
            width=width,
            height=1,
            primary=0,
            steps=steps,
            goals=goals,
            starts=[0],
            case_id="CLI_LONG_WITNESS_BOUND",
        )
        graph = oracle.validate_payload(payload)
        full = oracle.analyze_graph(graph)
        self.assertEqual(full.minimum_cost, 690)
        self.assertEqual(len(full.witness_states), 70)
        self.assertEqual(len(full.witness_edge_kinds), 69)
        expected_witness_sha = "26e9aef9d5f40f3705ddd5a9cb849ca2df734cc10f797fd01d7194cf45d728ac"
        self.assertEqual(full.witness_sha256, expected_witness_sha)

        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "long-graph.json")
            with open(path, "wb") as output:
                output.write(json.dumps(payload, separators=(",", ":")).encode("ascii"))
            text, status = self.call_main([path])
        self.assertEqual(status, 0)
        self.assertEqual(len(text.splitlines()), 1)
        record = json.loads(text)
        witness = record["witness"]
        self.assertEqual(record["minimumRelaxedCost"], 690)
        self.assertEqual(witness["stateCount"], 70)
        self.assertEqual(witness["edgeCount"], 69)
        self.assertEqual(witness["statePrefix"], list(range(64)))
        self.assertEqual(witness["edgeKindPrefix"], ["STEP_EAST"] * 63)
        self.assertTrue(witness["prefixTruncated"])
        self.assertEqual(witness["sha256"], expected_witness_sha)

    @staticmethod
    def call_main(args):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            status = oracle.main(args)
        return output.getvalue(), status


if __name__ == "__main__":
    unittest.main(verbosity=2)
