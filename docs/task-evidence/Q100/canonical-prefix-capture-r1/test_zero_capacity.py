"""Synthetic tests for the independent directed zero-VIA BFS.

All graphs are small in-memory literals. No run outputs, source roots, protected
data, or native processes are accessed.
"""
from pathlib import Path
import sys
import unittest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import audit_zero_capacity as audit
import read_capture


def graph(width, height, steps, vias, goals, starts):
    return read_capture.graph_oracle.validate_payload({
        "protocol": "p07-fixed-prefix-graph-v1",
        "model": "DIRECTED_TWO_FACE_HISTORY_RELAXED",
        "caseId": "LITERAL_ZERO_CAPACITY",
        "width": width,
        "height": height,
        "primary": 0,
        "steps": steps,
        "vias": vias,
        "goals": goals,
        "starts": starts,
    })


class DirectedPlanarBfsTests(unittest.TestCase):
    def test_east_edge_is_directed(self):
        fixture = graph(
            2, 1,
            [2, 0, 0, 0],
            [True, True],
            [False, True, False, False],
            [0],
        )
        result = audit.directed_zero_via_bfs(fixture)
        self.assertTrue(result["reachable"])
        self.assertEqual(result["goalState"], 1)
        self.assertEqual(result["verticalTransitionsExamined"], 0)
        self.assertFalse(result["viaMaskUsedForConnectivity"])

        reverse = graph(
            2, 1,
            [2, 0, 0, 0],
            [True, True],
            [True, False, False, False],
            [1],
        )
        self.assertFalse(audit.directed_zero_via_bfs(reverse)["reachable"])

    def test_north_and_south_edges_keep_their_directions(self):
        south = graph(
            1, 2,
            [4, 0, 0, 0],
            [False, False],
            [False, True, False, False],
            [0],
        )
        self.assertTrue(audit.directed_zero_via_bfs(south)["reachable"])

        north = graph(
            1, 2,
            [0, 1, 0, 0],
            [False, False],
            [True, False, False, False],
            [1],
        )
        self.assertTrue(audit.directed_zero_via_bfs(north)["reachable"])

    def test_bottom_face_uses_layer_times_cell_count_plus_cell(self):
        fixture = graph(
            2, 1,
            [0, 0, 2, 0],
            [True, True],
            [False, False, False, True],
            [2],
        )
        result = audit.directed_zero_via_bfs(fixture)
        self.assertTrue(result["reachable"])
        self.assertEqual(result["goalState"], 3)
        self.assertEqual(result["startStates"], [2])

    def test_via_bits_never_create_a_vertical_transition_at_zero_capacity(self):
        fixture = graph(
            1, 1,
            [0, 0],
            [True],
            [False, True],
            [0],
        )
        result = audit.directed_zero_via_bfs(fixture)
        minimum = read_capture.zero_via_result(fixture)
        self.assertFalse(result["reachable"])
        self.assertEqual(result["termination"], "FRONTIER_EXHAUSTED")
        self.assertEqual(result["verticalTransitionsExamined"], 0)
        self.assertTrue(fixture.vias[0])
        self.assertEqual(minimum["minimumViaCount"], 1)
        self.assertTrue(result["reachable"] == (minimum["minimumViaCount"] == 0))

    def test_zero_via_planar_route_agrees_with_minimum_via_zero(self):
        fixture = graph(
            2, 1,
            [2, 0, 0, 0],
            [True, False],
            [False, True, False, False],
            [0],
        )
        result = audit.directed_zero_via_bfs(fixture)
        minimum = read_capture.zero_via_result(fixture)
        self.assertTrue(result["reachable"])
        self.assertEqual(minimum["minimumViaCount"], 0)
        self.assertEqual(result["reachable"], minimum["minimumViaCount"] == 0)

    def test_queue_is_bounded_by_the_two_face_state_count(self):
        fixture = graph(
            2, 2,
            [2, 4, 0, 8, 0, 0, 0, 0],
            [True] * 4,
            [False, False, False, True, False, False, False, False],
            [0],
        )
        result = audit.directed_zero_via_bfs(fixture)
        self.assertLessEqual(result["maximumQueueStates"], fixture.state_count)
        self.assertLessEqual(result["statesVisited"], fixture.state_count)


if __name__ == "__main__":
    unittest.main()
