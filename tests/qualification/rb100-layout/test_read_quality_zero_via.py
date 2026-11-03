"""Independent fixtures for the bounded zero-via graph reader."""
from __future__ import annotations

import hashlib
import importlib.util
import io
import json
import shutil
import sys
import tempfile
import types
import unittest
from contextlib import redirect_stdout
from pathlib import Path

import read_quality_zero_via as reader


RUN_ID = "d1c10000-0000-4000-8000-000000000001"
READER_DIR = Path(reader.__file__).parent


def _event_fixture():
    width, height, cells = 3, 2, 6
    scalar = {
        "runId": RUN_ID, "pass": 1, "netId": "D_SENSE",
        "startPadId": "U2D.SENSOR", "rootPadId": "CFLT_D.1",
        "startX": 0, "startY": 0, "endX": 10, "endY": 0,
        "endLayer": 1, "primaryLayer": 1, "transitionCount": 0,
        "gridWidth": width, "gridHeight": height,
        "gridOriginX": 0, "gridOriginY": 0, "gridPitch": 10,
    }
    path = {
        "nodes": [
            {"cell": 0, "x": 0, "y": 0, "layer": 1, "transitions": 0},
            {"cell": 1, "x": 10, "y": 0, "layer": 1, "transitions": 0},
        ],
        "transitionCount": 0,
    }
    query_count = reader._ordinary_query_count(width, height)
    work = {
        "advanceCalls": 1, "workSteps": query_count + 3 * cells + cells + 12,
        "copyEntries": 6, "comparisonEntries": 6,
        "ordinaryQueries": query_count, "goalCells": cells,
        "encodedCells": 3 * cells, "activeMillis": 1,
    }
    binding = {
        "branchIdentityVerified": True, "sourcePublisherUnchanged": True,
        "pass": 1, "netId": "D_SENSE", "startPadId": "U2D.SENSOR",
        "rootPadId": "CFLT_D.1", "reachedPthPadIds": [],
        "foundTerminalPadId": "CFLT_D.1", "endCell": 1, "endLayer": 1,
        "goalMaskLength": 2 * cells, "policy": "FULLER_TWO_LAYER",
        "primaryLayer": 1, "initialLayerMask": 3,
        "maximumVias": 4, "prefixHoleCount": 0,
    }
    event = {
        "protocolVersion": 1, "sourceRevision": 17, "runId": RUN_ID,
        "operationId": "QUALITY_PROBE",
        "scope": "ACTUAL_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY",
        "binding": binding,
        "diagnostic": {
            "stability": {
                "prefixGeometrySha256": "a" * 64,
                "liveBeforeVerified": True, "liveAfterVerified": True,
                "liveEffectsUnchanged": True, "goalVerified": True,
                "originalPathVerified": True,
            },
            "grid": {
                "width": width, "height": height, "cells": cells,
                "originX": 0, "originY": 0, "pitch": 10,
                "startCell": 0, "initialLayerMask": 3,
                "endCell": 1, "endLayer": 1,
            },
            "work": work,
            # The original path's only edge is on BOTTOM/layer1.
            "masks": {
                "topStepsHex": "000000", "bottomStepsHex": "200000",
                "goalFacesHex": "020000",
            },
        },
        "feasibility": "UNKNOWN", "legalRouteProof": False,
        "layoutQualification": False, "routeAdoption": False,
    }
    return scalar, path, event


def _literal_hole(index, hole_id, net_id, net_position, ordinal, piece, x, y):
    java_id_length = len(hole_id.encode("utf-16-le", errors="strict")) // 2
    java_net_length = len(net_id.encode("utf-16-le", errors="strict")) // 2
    fingerprint = (f"{java_id_length}:{hole_id}VIA{java_net_length}:{net_id}"
                   f"{x},{y},2,6,EXPOSED")
    return {
        "index": index, "id": hole_id, "netId": net_id, "kind": "VIA",
        "x": x, "y": y, "drillRadius": 2, "landRadius": 6,
        "exposure": "EXPOSED", "fingerprint": fingerprint,
        "creatorNetPosition": net_position,
        "creatorBranchOrdinal": ordinal, "piece": piece,
    }


def _allocation_fixture(net_order=None, hole_specs=None):
    if net_order is None:
        # Deliberately source ordered, not lexically sorted. A/1 exercises a
        # known net whose slash and numeric suffix could fool a naive split.
        net_order = ["A", "A/1", "D_SENSE", "E"]
    if hole_specs is None:
        hole_specs = [
            ("p07-via/A/1/0", "A", 1, 0, 100, 110),
            ("p07-via/A/1/3/1", "A/1", 3, 1, 120, 130),
            ("p07-via/D_SENSE/8/2", "D_SENSE", 8, 2, 140, 150),
        ]
    positions = {net_id: position for position, net_id in enumerate(net_order)}
    ordered_specs = sorted(
        hole_specs, key=lambda item: item[0].encode("utf-16-be", errors="strict"))
    rows = [
        _literal_hole(index, hole_id, net_id, positions[net_id], ordinal,
                      piece, x, y)
        for index, (hole_id, net_id, ordinal, piece, x, y)
        in enumerate(ordered_specs)
    ]

    scalar, path, graph_event = _event_fixture()
    graph_event["binding"]["maximumVias"] = 48
    graph_event["binding"]["prefixHoleCount"] = len(rows)
    graph_event["diagnostic"]["stability"]["prefixGeometrySha256"] = "b" * 64
    graph = reader._validate_graph_event(graph_event, scalar, path)
    current_position = net_order.index("D_SENSE")
    by_net = [
        {"netPosition": position, "netId": net_id,
         "holeCount": sum(1 for row in rows if row["netId"] == net_id)}
        for position, net_id in enumerate(net_order)
    ]
    event = {
        "protocolVersion": 1, "sourceRevision": 17, "runId": RUN_ID,
        "operationId": "QUALITY_PROBE",
        "scope": "ACTUAL_FIXED_PREFIX_CREATION_ALLOCATION_ONLY",
        "binding": {
            "branchIdentityVerified": True, "sourcePublisherUnchanged": True,
            "pass": 1, "netId": "D_SENSE", "startPadId": "U2D.SENSOR",
            "rootPadId": "CFLT_D.1", "prefixHoleCount": len(rows),
            "maximumVias": 48, "templateHoleCount": 0,
            "searchNetIndex": current_position + 1,
            "currentNetPosition": current_position,
            "currentBranchOrdinal": 9, "netOrder": list(net_order),
        },
        "diagnostic": {
            "stability": {
                "prefixGeometrySha256": "b" * 64,
                "liveBeforeVerified": True, "liveAfterVerified": True,
                "holeIdentitiesVerified": True, "lineageVerified": True,
            },
            "work": {
                "advanceCalls": 1,
                "workSteps": (len(rows) + len(rows) * len(net_order) +
                              len(net_order) + len(rows) * (len(rows) - 1) // 2),
                "rowsScanned": len(rows),
                "netComparisons": len(rows) * len(net_order),
                "netTotalsWritten": len(net_order), "activeMillis": 1,
            },
            "totals": {
                "totalHoles": len(rows),
                "byKind": {"VIA": len(rows), "PLATED": 0, "NON_PLATED": 0},
                "byNet": by_net,
            },
            "rows": rows,
        },
        "chronologyProof": False, "routeAdoption": False,
        "layoutQualification": False,
    }
    summary = {
        "protocolVersion": 1, "sourceRevision": 17, "status": "PASS",
        "runId": RUN_ID, "operationId": "QUALITY_PROBE", "events": 1,
        "scope": event["scope"], "totalHoles": len(rows),
        "chronologyProof": False, "routeAdoption": False,
        "layoutQualification": False,
    }
    return scalar, path, graph, event, summary


def _refresh_literal_fingerprint(row):
    identity = row["id"]
    net_id = row["netId"]
    id_length = len(identity.encode("utf-16-le", errors="strict")) // 2
    net_length = len(net_id.encode("utf-16-le", errors="strict")) // 2
    row["fingerprint"] = (f"{id_length}:{identity}VIA{net_length}:{net_id}"
                           f"{row['x']},{row['y']},2,6,EXPOSED")


def _floyd_warshall(width, height, masks):
    """Separate all-pairs oracle over directed permissions, independent of BFS."""
    count = width * height
    infinity = count + 1
    distances = [[infinity] * count for _ in range(count)]
    for source in range(count):
        distances[source][source] = 0
        x, y = source % width, source // width
        for dx, dy, bit in ((0, -1, 1), (1, 0, 2), (0, 1, 4), (-1, 0, 8)):
            if masks[source] & bit:
                tx, ty = x + dx, y + dy
                if 0 <= tx < width and 0 <= ty < height:
                    distances[source][ty * width + tx] = 1
    for pivot in range(count):
        for source in range(count):
            for target in range(count):
                distances[source][target] = min(
                    distances[source][target],
                    distances[source][pivot] + distances[pivot][target],
                )
    return distances


def _oracle_face(width, height, pitch, start, end, end_layer,
                 top, bottom, goals, seeded_layers):
    expected = []
    all_eligible = []
    for layer, mask in enumerate((top, bottom)):
        if not (seeded_layers & (1 << layer)):
            continue
        all_pairs = _floyd_warshall(width, height, mask)
        distances = all_pairs[start]
        reachable = []
        eligible = []
        for cell, goal_faces in enumerate(goals):
            if not (goal_faces & (1 << layer)) or distances[cell] > width * height:
                continue
            length = distances[cell] * pitch
            direct = (abs(cell % width - start % width) +
                      abs(cell // width - start // width)) * pitch
            reachable.append(length)
            if direct > 0 and length <= 3 * direct:
                eligible.append(length)
        captured = (distances[end] * pitch
                    if layer == end_layer and distances[end] <= width * height else None)
        expected.append({
            "layer": layer,
            "reachableGoals": len(reachable),
            "shortestAnyGoal": min(reachable, default=None),
            "distanceCapturedEnd": captured,
            "lengthEligibleGoals": len(eligible),
            "minimumEligibleLength": min(eligible, default=None),
        })
        all_eligible.extend(eligible)
    return expected, all_eligible


class GraphOracleTests(unittest.TestCase):
    def _compare_oracle(self, width, height, pitch, start, end, end_layer,
                        seeds, top, bottom, goals):
        actual = reader._analyze_graph(width, height, pitch, start, end,
                                       end_layer, seeds, top, bottom, goals)
        expected_faces, expected_eligible = _oracle_face(
            width, height, pitch, start, end, end_layer,
            top, bottom, goals, seeds)
        self.assertEqual(actual["faces"], expected_faces)
        self.assertEqual(actual["lengthEligibleGoals"], len(expected_eligible))
        self.assertEqual(actual["minimumEligibleLength"],
                         min(expected_eligible, default=None))
        return actual

    def test_literal_multigoal_case_uses_each_goals_own_three_to_one_threshold(self):
        # Frozen independent literal: 0->3->4->5->2->1. Goal1's captured
        # path is 50/10 and fails30; goal2 is 40/20 and passes60.
        top = [4, 0, 8, 2, 2, 1]
        bottom = [0] * 6
        goals = [0, 1, 1, 0, 0, 0]
        result = self._compare_oracle(3, 2, 10, 0, 1, 0, 1,
                                      top, bottom, goals)
        self.assertEqual(result["faces"][0]["shortestAnyGoal"], 40)
        self.assertEqual(result["faces"][0]["distanceCapturedEnd"], 50)
        self.assertEqual(result["faces"][0]["lengthEligibleGoals"], 1)
        self.assertEqual(result["faces"][0]["minimumEligibleLength"], 40)
        self.assertEqual(result["geometricRelaxation"],
                         "LENGTH_ELIGIBLE_GOAL_EXISTS_AS_RELAXATION_ONLY")

    def test_both_seed_faces_are_analyzed_independently(self):
        # Top is disconnected; bottom has one directed ten-unit edge to its goal.
        result = self._compare_oracle(
            2, 1, 10, 0, 1, 1, 3,
            [0, 0], [2, 0], [0, 2],
        )
        self.assertEqual([face["layer"] for face in result["faces"]], [0, 1])
        self.assertEqual(result["faces"][0]["shortestAnyGoal"], None)
        self.assertEqual(result["faces"][1]["shortestAnyGoal"], 10)
        self.assertEqual(result["seedLayerMask"], 3)

    def test_reverse_only_edge_is_not_treated_as_undirected(self):
        result = self._compare_oracle(
            2, 1, 10, 0, 1, 0, 1,
            [0, 8], [0, 0], [0, 1],
        )
        self.assertEqual(result["faces"][0]["reachableGoals"], 0)
        self.assertIsNone(result["faces"][0]["shortestAnyGoal"])
        self.assertIsNone(result["faces"][0]["distanceCapturedEnd"])

    def test_disconnected_goals_report_null_distance(self):
        result = self._compare_oracle(
            2, 2, 10, 0, 3, 0, 1,
            [0, 0, 0, 0], [0, 0, 0, 0], [0, 1, 0, 0],
        )
        self.assertEqual(result["faces"][0]["reachableGoals"], 0)
        self.assertIsNone(result["faces"][0]["shortestAnyGoal"])
        self.assertIsNone(result["faces"][0]["distanceCapturedEnd"])

    def test_zero_direct_distance_goal_is_never_length_eligible(self):
        result = self._compare_oracle(
            2, 1, 10, 0, 0, 0, 1,
            [0, 0], [0, 0], [1, 0],
        )
        self.assertEqual(result["faces"][0]["reachableGoals"], 1)
        self.assertEqual(result["faces"][0]["shortestAnyGoal"], 0)
        self.assertEqual(result["faces"][0]["lengthEligibleGoals"], 0)

    def test_boundary_permissions_cannot_wrap_to_another_row_or_edge(self):
        cases = (
            (2, 2, [1, 0, 0, 0], "GRAPH_MASK_NORTH_BOUNDARY_INVALID"),
            (2, 2, [0, 2, 0, 0], "GRAPH_MASK_EAST_BOUNDARY_INVALID"),
            (2, 2, [0, 0, 4, 0], "GRAPH_MASK_SOUTH_BOUNDARY_INVALID"),
            (2, 2, [8, 0, 0, 0], "GRAPH_MASK_WEST_BOUNDARY_INVALID"),
        )
        for width, height, top, expected in cases:
            with self.subTest(expected=expected):
                with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                    reader._analyze_graph(width, height, 10, 0, 0, 0, 1,
                                          top, [0] * (width * height),
                                          [0] * (width * height))
                self.assertEqual(caught.exception.code, expected)


class GraphWireTests(unittest.TestCase):
    def test_closed_event_validates_full_grid_identity_both_seeds_and_original_edge(self):
        scalar, path, event = _event_fixture()
        parsed = reader._validate_graph_event(event, scalar, path)
        self.assertEqual(parsed["analysis"]["seedLayerMask"], 3)
        self.assertEqual(parsed["analysis"]["faces"][1]["distanceCapturedEnd"], 10)
        self.assertIs(parsed["legalRouteProof"], False)
        self.assertIs(parsed["routeAdoption"], False)

    def test_closed_keys_and_revision_are_required(self):
        scalar, path, event = _event_fixture()
        event["unrecognized"] = True
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_EVENT_KEYS_INVALID")
        scalar, path, event = _event_fixture()
        event["sourceRevision"] = 15
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_EVENT_IDENTITY_OR_SCOPE_INVALID")

    def test_claim_flags_and_live_stability_are_false_or_verified_exactly(self):
        for field in ("legalRouteProof", "layoutQualification", "routeAdoption"):
            scalar, path, event = _event_fixture()
            event[field] = True
            with self.subTest(field=field):
                with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                    reader._validate_graph_event(event, scalar, path)
                self.assertEqual(caught.exception.code,
                                 "GRAPH_EVENT_IDENTITY_OR_SCOPE_INVALID")
        scalar, path, event = _event_fixture()
        event["diagnostic"]["stability"]["liveAfterVerified"] = False
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_STABILITY_INVALID")

    def test_original_path_edges_must_be_present_in_the_correct_face(self):
        scalar, path, event = _event_fixture()
        event["diagnostic"]["masks"]["bottomStepsHex"] = "000000"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_ORIGINAL_PATH_EDGE_MISSING")

    def test_query_work_and_goal_mask_conservation_are_bound(self):
        scalar, path, event = _event_fixture()
        event["diagnostic"]["work"]["ordinaryQueries"] -= 1
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_WORK_CONSERVATION_INVALID")
        scalar, path, event = _event_fixture()
        event["binding"]["goalMaskLength"] -= 1
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_BINDING_GRAPH_DOMAIN_INVALID")
        scalar, path, event = _event_fixture()
        event["binding"]["prefixHoleCount"] = 5
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_BINDING_GRAPH_DOMAIN_INVALID")

        scalar, path, event = _event_fixture()
        event["diagnostic"]["work"]["workSteps"] = reader.MAX_TOTAL_WORK + 1
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_WORK_DOMAIN_INVALID")
        scalar, path, event = _event_fixture()
        event["diagnostic"]["work"]["copyEntries"] = reader.MAX_TOTAL_WORK
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_WORK_CONSERVATION_INVALID")

    def test_mask_hex_is_canonical_lowercase_and_goal_faces_are_two_bits(self):
        scalar, path, event = _event_fixture()
        event["diagnostic"]["masks"]["topStepsHex"] = "A00000"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_MASK_ENCODING_INVALID")
        scalar, path, event = _event_fixture()
        event["diagnostic"]["masks"]["goalFacesHex"] = "040000"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_GOAL_FACE_MASK_INVALID")

    def test_summary_must_match_event_and_actual_query_count(self):
        scalar, path, event = _event_fixture()
        graph = reader._validate_graph_event(event, scalar, path)
        summary = {
            "protocolVersion": 1, "sourceRevision": 17, "status": "PASS",
            "runId": RUN_ID, "operationId": "QUALITY_PROBE", "events": 1,
            "scope": event["scope"],
            "ordinaryQueries": event["diagnostic"]["work"]["ordinaryQueries"],
            "feasibility": "UNKNOWN", "legalRouteProof": False,
            "layoutQualification": False, "routeAdoption": False,
        }
        self.assertEqual(reader._validate_graph_summary(summary, event, graph), summary)
        summary["ordinaryQueries"] -= 1
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_summary(summary, event, graph)
        self.assertEqual(caught.exception.code,
                         "GRAPH_SUMMARY_IDENTITY_OR_SCOPE_INVALID")


class PrefixAllocationWireTests(unittest.TestCase):
    def test_literal_rows_preserve_source_net_order_and_same_net_prior_owner(self):
        scalar, _path, graph, event, summary = _allocation_fixture()
        allocation = reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(
            allocation["binding"]["netOrder"], ["A", "A/1", "D_SENSE", "E"])
        self.assertEqual(
            [row["netId"] for row in allocation["diagnostic"]["totals"]["byNet"]],
            ["A", "A/1", "D_SENSE", "E"])
        self.assertEqual(
            [(row["netPosition"], row["netId"], row["holeCount"])
             for row in allocation["diagnostic"]["totals"]["byNet"]],
            [(0, "A", 1), (1, "A/1", 1), (2, "D_SENSE", 1), (3, "E", 0)])
        self.assertEqual(allocation["binding"]["currentNetPosition"], 2)
        self.assertEqual(allocation["binding"]["searchNetIndex"], 3)
        self.assertEqual(allocation["binding"]["currentBranchOrdinal"], 9)
        self.assertEqual(allocation["diagnostic"]["work"]["workSteps"], 22)
        rows = allocation["diagnostic"]["rows"]
        self.assertEqual([row["id"] for row in rows], [
            "p07-via/A/1/0", "p07-via/A/1/3/1", "p07-via/D_SENSE/8/2",
        ])
        self.assertEqual([row["fingerprint"] for row in rows], [
            "13:p07-via/A/1/0VIA1:A100,110,2,6,EXPOSED",
            "15:p07-via/A/1/3/1VIA3:A/1120,130,2,6,EXPOSED",
            "19:p07-via/D_SENSE/8/2VIA7:D_SENSE140,150,2,6,EXPOSED",
        ])
        self.assertEqual(rows[2]["creatorNetPosition"],
                         allocation["binding"]["currentNetPosition"])
        self.assertEqual(rows[2]["creatorBranchOrdinal"], 8)
        self.assertEqual(rows[1]["netId"], "A/1")
        self.assertEqual(summary["totalHoles"], 3)
        self.assertIs(allocation["chronologyProof"], False)
        self.assertIs(allocation["routeAdoption"], False)
        self.assertIs(allocation["layoutQualification"], False)

    def test_escaped_json_uses_java_utf16_id_length_and_order(self):
        net_order = ["A/\uE000", "A/\U0001F527", "D_SENSE"]
        hole_specs = [
            ("p07-via/A/\U0001F527/2/1", "A/\U0001F527", 2, 1, 10, 20),
            ("p07-via/A/\uE000/1/0", "A/\uE000", 1, 0, 20, 30),
            ("p07-via/D_SENSE/3/2", "D_SENSE", 3, 2, 30, 40),
        ]
        scalar, _path, graph, event, _summary = _allocation_fixture(
            net_order, hole_specs)
        raw = json.dumps(event, separators=(",", ":"), ensure_ascii=True)
        self.assertIn(r"\ud83d\udd27", raw.lower())
        parsed = reader._parse_tagged(
            reader.ALLOCATION_EVENT_MARKER + " " + raw,
            reader.ALLOCATION_EVENT_MARKER, _LineLedger)
        allocation = reader._validate_prefix_allocation_event(parsed, scalar, graph)
        self.assertEqual([row["id"] for row in allocation["diagnostic"]["rows"]], [
            "p07-via/A/\U0001F527/2/1",
            "p07-via/A/\uE000/1/0",
            "p07-via/D_SENSE/3/2",
        ])
        self.assertEqual([row["fingerprint"]
                          for row in allocation["diagnostic"]["rows"]], [
            "16:p07-via/A/\U0001F527/2/1VIA4:A/\U0001F52710,20,2,6,EXPOSED",
            "15:p07-via/A/\uE000/1/0VIA3:A/\uE00020,30,2,6,EXPOSED",
            "19:p07-via/D_SENSE/3/2VIA7:D_SENSE30,40,2,6,EXPOSED",
        ])
        self.assertEqual(allocation["binding"]["netOrder"], net_order)
        self.assertEqual(reader._java_string_length("A/\U0001F527"), 4)
        self.assertEqual(
            reader._java_string_length("p07-via/A/\U0001F527/2/1"), 16)

    def test_event_binding_requires_exact_search_position_and_unique_valid_nets(self):
        for mutation, expected in (
                (lambda event: event["binding"].update(searchNetIndex=2),
                 "ALLOCATION_CURRENT_NET_POSITION_INVALID"),
                (lambda event: event["binding"].update(netOrder=[
                    "A", "A/1", "D_SENSE", "E", "A"]),
                 "ALLOCATION_NET_ORDER_INVALID"),
                (lambda event: event["binding"].update(netOrder=[
                    "A", "A/1", "\u0001", "E"]),
                 "ALLOCATION_NET_ORDER_INVALID")):
            scalar, _path, graph, event, _summary = _allocation_fixture()
            mutation(event)
            with self.subTest(expected=expected):
                with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                    reader._validate_prefix_allocation_event(event, scalar, graph)
                self.assertEqual(caught.exception.code, expected)

    def test_future_net_or_branch_owner_and_malformed_numeric_identity_fail_closed(self):
        scalar, _path, graph, event, _summary = _allocation_fixture()
        event["binding"]["netOrder"] = ["A", "D_SENSE", "A/1", "E"]
        event["binding"]["currentNetPosition"] = 1
        event["binding"]["searchNetIndex"] = 2
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(caught.exception.code, "ALLOCATION_ROW_OWNER_INVALID")

        scalar, _path, graph, event, _summary = _allocation_fixture()
        row = event["diagnostic"]["rows"][0]
        row["id"] = "p07-via/A/9/0"
        row["creatorBranchOrdinal"] = 9
        _refresh_literal_fingerprint(row)
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(caught.exception.code, "ALLOCATION_ROW_OWNER_INVALID")

        scalar, _path, graph, event, _summary = _allocation_fixture()
        event["diagnostic"]["rows"][0]["id"] = "p07-via/A/03/2"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(caught.exception.code, "ALLOCATION_ROW_ID_INVALID")

    def test_branch_ordinals_have_one_owner_and_monotonic_search_net_positions(self):
        scalar, _path, graph, event, _summary = _allocation_fixture()
        row = event["diagnostic"]["rows"][1]
        row["id"] = "p07-via/A/1/1/1"
        row["creatorBranchOrdinal"] = 1
        _refresh_literal_fingerprint(row)
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(caught.exception.code,
                         "ALLOCATION_BRANCH_OWNER_INCONSISTENT")

        backward_rows = [
            ("p07-via/A/1/1/1", "A/1", 1, 1, 20, 30),
            ("p07-via/A/3/0", "A", 3, 0, 10, 20),
            ("p07-via/D_SENSE/8/2", "D_SENSE", 8, 2, 30, 40),
        ]
        scalar, _path, graph, event, _summary = _allocation_fixture(
            hole_specs=backward_rows)
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(caught.exception.code,
                         "ALLOCATION_BRANCH_ORDER_INCONSISTENT")

    def test_unknown_owner_control_text_and_inconsistent_net_are_rejected(self):
        cases = (
            (lambda row: row.update(id="p07-via/UNKNOWN/1/0"),
             "ALLOCATION_ROW_ID_INVALID"),
            (lambda row: row.update(id="p07-via/A/3/2\n"),
             "ALLOCATION_ROW_ID_INVALID"),
            (lambda row: row.update(id="p07-via/" + "x" * 4097),
             "ALLOCATION_ROW_ID_INVALID"),
            (lambda row: row.update(netId="D_SENSE"),
             "ALLOCATION_ROW_OWNER_INVALID"),
        )
        for mutation, expected in cases:
            scalar, _path, graph, event, _summary = _allocation_fixture()
            mutation(event["diagnostic"]["rows"][0])
            with self.subTest(expected=expected):
                with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                    reader._validate_prefix_allocation_event(event, scalar, graph)
                self.assertEqual(caught.exception.code, expected)

    def test_rows_must_be_unique_and_java_string_sorted_with_exact_indices(self):
        scalar, _path, graph, event, _summary = _allocation_fixture()
        event["diagnostic"]["rows"][0], event["diagnostic"]["rows"][1] = (
            event["diagnostic"]["rows"][1], event["diagnostic"]["rows"][0])
        for index, row in enumerate(event["diagnostic"]["rows"]):
            row["index"] = index
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(caught.exception.code, "ALLOCATION_ROW_ORDER_INVALID")

        scalar, _path, graph, event, _summary = _allocation_fixture()
        duplicate = dict(event["diagnostic"]["rows"][0])
        duplicate["index"] = 1
        event["diagnostic"]["rows"][1] = duplicate
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(caught.exception.code, "ALLOCATION_ROW_ORDER_INVALID")

    def test_row_factory_geometry_fingerprint_and_total_conservation_are_closed(self):
        mutations = (
            (lambda event: event["diagnostic"]["rows"][0].update(kind="PLATED"),
             "ALLOCATION_ROW_GEOMETRY_INVALID"),
            (lambda event: event["diagnostic"]["rows"][0].update(fingerprint="wrong"),
             "ALLOCATION_ROW_FINGERPRINT_INVALID"),
            (lambda event: event["diagnostic"]["totals"].update(totalHoles=2),
             "ALLOCATION_TOTAL_HOLES_INVALID"),
            (lambda event: event["diagnostic"]["totals"]["byKind"].update(PLATED=1),
             "ALLOCATION_KIND_TOTALS_INVALID"),
            (lambda event: event["diagnostic"]["totals"]["byNet"][0].update(
                holeCount=(event["diagnostic"]["totals"]["byNet"][0]["holeCount"] + 1)),
             "ALLOCATION_NET_TOTALS_INVALID"),
            (lambda event: event["diagnostic"]["work"].update(rowsScanned=2),
             "ALLOCATION_WORK_CONSERVATION_INVALID"),
            (lambda event: event["diagnostic"]["work"].update(workSteps=19),
             "ALLOCATION_WORK_CONSERVATION_INVALID"),
        )
        for mutation, expected in mutations:
            scalar, _path, graph, event, _summary = _allocation_fixture()
            mutation(event)
            with self.subTest(expected=expected):
                with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                    reader._validate_prefix_allocation_event(event, scalar, graph)
                self.assertEqual(caught.exception.code, expected)

    def test_via_centers_keep_the_land_bounds_inside_board_coordinate_limit(self):
        scalar, _path, graph, event, _summary = _allocation_fixture()
        boundary_row = event["diagnostic"]["rows"][0]
        boundary_row["x"] = 99_999_994
        boundary_row["y"] = -99_999_994
        _refresh_literal_fingerprint(boundary_row)
        allocation = reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(allocation["diagnostic"]["rows"][0]["x"], 99_999_994)
        self.assertEqual(allocation["diagnostic"]["rows"][0]["y"], -99_999_994)

        for coordinate, value in (("x", 99_999_995), ("y", -99_999_995),
                                  ("x", 100_000_000)):
            scalar, _path, graph, event, _summary = _allocation_fixture()
            row = event["diagnostic"]["rows"][0]
            row[coordinate] = value
            _refresh_literal_fingerprint(row)
            with self.subTest(coordinate=coordinate, value=value):
                with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                    reader._validate_prefix_allocation_event(event, scalar, graph)
                self.assertEqual(caught.exception.code,
                                 "ALLOCATION_ROW_GEOMETRY_INVALID")

    def test_event_summary_and_stability_claims_remain_false_and_source_bound(self):
        for field in ("chronologyProof", "routeAdoption", "layoutQualification"):
            scalar, _path, graph, event, _summary = _allocation_fixture()
            event[field] = True
            with self.subTest(field=field):
                with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                    reader._validate_prefix_allocation_event(event, scalar, graph)
                self.assertEqual(caught.exception.code,
                                 "ALLOCATION_EVENT_IDENTITY_OR_SCOPE_INVALID")

        for field in ("branchIdentityVerified", "sourcePublisherUnchanged"):
            scalar, _path, graph, event, _summary = _allocation_fixture()
            event["binding"][field] = False
            with self.subTest(field=field):
                with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                    reader._validate_prefix_allocation_event(event, scalar, graph)
                self.assertEqual(caught.exception.code,
                                 "ALLOCATION_BINDING_IDENTITY_INVALID")

        scalar, _path, graph, event, summary = _allocation_fixture()
        allocation = reader._validate_prefix_allocation_event(event, scalar, graph)
        summary["totalHoles"] += 1
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_summary(summary, event, allocation)
        self.assertEqual(caught.exception.code,
                         "ALLOCATION_SUMMARY_IDENTITY_OR_SCOPE_INVALID")

        for field in ("chronologyProof", "routeAdoption", "layoutQualification"):
            scalar, _path, graph, event, summary = _allocation_fixture()
            allocation = reader._validate_prefix_allocation_event(event, scalar, graph)
            summary[field] = True
            with self.subTest(field=field):
                with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                    reader._validate_prefix_allocation_summary(
                        summary, event, allocation)
                self.assertEqual(caught.exception.code,
                                 "ALLOCATION_SUMMARY_IDENTITY_OR_SCOPE_INVALID")

        scalar, _path, graph, event, _summary = _allocation_fixture()
        event["diagnostic"]["stability"]["prefixGeometrySha256"] = "c" * 64
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(caught.exception.code, "ALLOCATION_STABILITY_INVALID")

        scalar, _path, graph, event, _summary = _allocation_fixture()
        event["diagnostic"]["stability"]["lineageVerified"] = False
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(caught.exception.code, "ALLOCATION_STABILITY_INVALID")

    def test_source_prefix_hole_count_and_exact_closed_keys_are_required(self):
        scalar, _path, graph, event, _summary = _allocation_fixture()
        event["binding"]["prefixHoleCount"] = 2
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(caught.exception.code, "ALLOCATION_BINDING_IDENTITY_INVALID")

        scalar, _path, graph, event, _summary = _allocation_fixture()
        event["sourceRevision"] = 16
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(caught.exception.code,
                         "ALLOCATION_EVENT_IDENTITY_OR_SCOPE_INVALID")

        scalar, _path, graph, event, _summary = _allocation_fixture()
        event["extra"] = True
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_event(event, scalar, graph)
        self.assertEqual(caught.exception.code, "ALLOCATION_EVENT_KEYS_INVALID")

        scalar, _path, graph, event, summary = _allocation_fixture()
        summary["extra"] = True
        allocation = reader._validate_prefix_allocation_event(event, scalar, graph)
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_prefix_allocation_summary(summary, event, allocation)
        self.assertEqual(caught.exception.code, "ALLOCATION_SUMMARY_KEYS_INVALID")


class _LineLedger:
    class LedgerInputError(ValueError):
        pass

    MAX_LINE_BYTES = 1024 * 1024

    @staticmethod
    def _decode_lines(raw, _code):
        return raw.decode("utf-8-sig", errors="strict").splitlines()

    @staticmethod
    def _strict_json(raw, _code):
        return json.loads(raw.decode("utf-8", errors="strict"))


class ReceiptTests(unittest.TestCase):
    def _receipt(self):
        return [marker + " {}" for marker in reader.RECEIPT_MARKERS]

    def test_receipt_is_the_closed_fourteen_record_v2_sequence(self):
        self.assertEqual(len(reader.RECEIPT_MARKERS), 14)
        self.assertEqual(reader.RECEIPT_MARKERS[-2:], (
            reader.ALLOCATION_EVENT_MARKER, reader.ALLOCATION_SUMMARY_MARKER))
        receipt = self._receipt()
        self.assertEqual(len(receipt), 14)
        self.assertTrue(receipt[-2].startswith("RB100_PREFIX_ALLOCATION_EVENT "))
        self.assertTrue(receipt[-1].startswith(
            "PASS: RB100 prefix allocation contracts "))

    def test_console_receipt_must_have_exact_marker_order_and_no_stderr_events(self):
        ledger = _LineLedger()
        receipt = self._receipt()
        stdout = ("\n".join(receipt + [reader.FOCUSED_PASS,
                                       reader.CLEANUP_LINE]) + "\n").encode()
        self.assertEqual(reader._validate_console_and_receipt(
            stdout, ("\n".join(receipt) + "\n").encode(), b"", ledger)[1], receipt)
        reordered = list(receipt)
        reordered[10], reordered[11] = reordered[11], reordered[10]
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_console_and_receipt(
                stdout, ("\n".join(reordered) + "\n").encode(), b"", ledger)
        self.assertEqual(caught.exception.code, "RUN_RECEIPT_MARKER_ORDER_INVALID")

        stderr = (reader.GRAPH_EVENT_MARKER + " {}\n").encode()
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_console_and_receipt(
                stdout, ("\n".join(receipt) + "\n").encode(), stderr, ledger)
        self.assertEqual(caught.exception.code, "RUN_CONSOLE_RECEIPT_MISMATCH")

    def test_unknown_or_missing_record_is_rejected_before_old_helpers_run(self):
        lines = self._receipt()
        lines[-1] = "RB100_UNEXPECTED_EVENT {}"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_receipt_lines(lines, _LineLedger(), object(), object())
        self.assertEqual(caught.exception.code, "GRAPH_RECEIPT_MARKER_ORDER_INVALID")

    def test_frozen_scalar_error_code_crosses_the_new_reader_boundary(self):
        frozen, scalar, ledger, _capture, _hashes = reader._load_pinned_helpers()
        lines = self._receipt()
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_receipt_lines(lines, ledger, frozen, scalar)
        self.assertEqual(caught.exception.code, "CONTROLS_KEYS_INVALID")


class SourceBoundRunTests(unittest.TestCase):
    def test_coordinated_v2_schema_and_native_census_are_pinned(self):
        self.assertEqual(reader.INPUT_SCHEMA, "p07-quality-zero-via-input-v2")
        self.assertEqual(reader.RUN_SCHEMA, "q100-quality-zero-via-native-run-v2")
        self.assertEqual(reader.RESULT_SCHEMA,
                         "q100-quality-zero-via-reader-result-v2")
        self.assertEqual(reader.SOURCE_REVISION, 17)
        self.assertEqual(reader.SOURCE_INPUT_COUNT, 1074)
        self.assertEqual(reader.CONTRACT_JAVA_COUNT, 218)
        self.assertEqual(len(reader.QUALITY_REQUIRED_INPUTS), 10)

    def test_source_schema_and_revision_are_rejected_before_source_reads(self):
        with tempfile.TemporaryDirectory(prefix="q100-zero-via-source-") as temporary:
            base = Path(temporary)
            spec_path = base / "input-spec.json"
            for schema, revision in (
                    ("p07-quality-zero-via-input-v1", 16),
                    (reader.INPUT_SCHEMA, 16)):
                spec = {
                    "schema": schema,
                    "sourceRoot": str(base / "missing-source"),
                    "pinsPath": str(base / "missing-pins.json"),
                    "pinsSha256": "a" * 64,
                    "inputCount": 1072, "inputBytes": 100,
                    "sourceSnapshotRevision": revision,
                    "resultPath": str(base / "missing-result.json"),
                    "stdoutPath": str(base / "missing-stdout.txt"),
                    "receiptPath": str(base / "missing-receipt.txt"),
                    "stderrPath": str(base / "missing-stderr.txt"),
                }
                raw = json.dumps(spec, separators=(",", ":"), sort_keys=True).encode()
                spec_path.write_bytes(raw)
                with self.subTest(schema=schema, revision=revision):
                    with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                        reader.run_validation(
                            str(spec_path), hashlib.sha256(raw).hexdigest())
                    self.assertEqual(caught.exception.code,
                                     "INPUT_SPEC_IDENTITY_INVALID")

    def test_input_spec_hash_is_checked_before_source_or_artifact_reads(self):
        with tempfile.TemporaryDirectory(prefix="q100-zero-via-spec-hash-") as temporary:
            path = Path(temporary) / "input-spec.json"
            path.write_text("{}", encoding="utf-8")
            with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                reader.run_validation(str(path), "0" * 64)
            self.assertEqual(caught.exception.code, "INPUT_SPEC_SHA256_MISMATCH")

    def test_modified_frozen_reader_is_rejected_in_a_private_copy(self):
        filenames = (
            "read_quality_zero_via.py", "read_quality_path.py",
            "read_quality_scalars.py", "read_current_prefix_ledger.py",
            "read_fixed_prefix_capture.py", "read_fixed_prefix_graph.py",
            "fixed_prefix_minimum_vias.py",
        )
        with tempfile.TemporaryDirectory(prefix="q100-zero-via-helper-") as temporary:
            directory = Path(temporary)
            for filename in filenames:
                shutil.copyfile(READER_DIR / filename, directory / filename)
            frozen_path = directory / "read_quality_path.py"
            frozen_path.write_bytes(frozen_path.read_bytes() + b"\n# changed\n")
            module_path = directory / "read_quality_zero_via.py"
            name = "_q100_zero_via_helper_negative_" + str(id(self))
            spec = importlib.util.spec_from_file_location(name, module_path)
            module = importlib.util.module_from_spec(spec)
            sys.modules[name] = module
            try:
                spec.loader.exec_module(module)
                with self.assertRaises(module.QualityZeroViaInputError) as caught:
                    module._load_pinned_helpers()
                self.assertEqual(caught.exception.code,
                                 "FROZEN_QUALITY_PATH_READER_HASH_MISMATCH")
            finally:
                sys.modules.pop(name, None)

    def test_cli_flags_and_failure_text_remain_closed(self):
        self.assertEqual(reader._parse_cli([
            "--input-spec", "spec.json", "--input-spec-sha256", "a" * 64,
        ]), ("spec.json", "a" * 64))
        output = io.StringIO()
        sensitive = "C:/Users/Private Person/secret-input.json"
        with redirect_stdout(output):
            code = reader.main([
                "--input-spec", sensitive, "--input-spec-sha256", "not-a-sha",
            ])
        self.assertEqual(code, 2)
        self.assertEqual(output.getvalue(), "FAIL: CLI_ARGUMENTS_INVALID\n")
        self.assertNotIn("Private Person", output.getvalue())

    def test_frozen_validators_leave_no_private_modules_registered(self):
        prefixes = (
            "_q100_quality_zero_via_path_", "_q100_quality_path_scalar_",
            "_q100_quality_scalar_graph", "_q100_quality_scalar_capture",
            "_q100_quality_scalar_prefix_ledger",
        )
        before = {name for name in sys.modules
                  if any(name.startswith(prefix) for prefix in prefixes)}
        reader._load_pinned_helpers()
        after = {name for name in sys.modules
                 if any(name.startswith(prefix) for prefix in prefixes)}
        self.assertEqual(after, before)

    def test_reparse_reader_input_is_rejected_when_host_supports_symlinks(self):
        with tempfile.TemporaryDirectory(prefix="q100-zero-via-reparse-") as temporary:
            base = Path(temporary)
            target = base / "target.json"
            link = base / "link.json"
            target.write_text("{}", encoding="utf-8")
            try:
                link.symlink_to(target)
            except (OSError, NotImplementedError):
                self.skipTest("Host does not allow creating a test symlink")
            with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                reader._read_local(link, 1024, "TEST_INPUT")
            self.assertEqual(caught.exception.code, "TEST_INPUT_REPARSE_PATH")


if __name__ == "__main__":
    unittest.main()
