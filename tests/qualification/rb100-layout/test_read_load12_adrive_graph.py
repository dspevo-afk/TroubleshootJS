"""Synthetic independent reader controls; none of these fixtures is graph evidence."""
from __future__ import annotations

import copy
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

import read_load12_adrive_graph as reader


SYNTHETIC_ONLY = True


def _pack_graph(graph):
    packed = dict(graph)
    packed["steps"] = "".join(format(mask, "x") for mask in graph["steps"])
    packed["vias"] = "".join("1" if value else "0" for value in graph["vias"])
    packed["goals"] = "".join("1" if value else "0" for value in graph["goals"])
    return packed


def _wire_graph(width=3, height=2, goals=None, steps=None, vias=None, primary=0):
    cells = width * height
    graph = {
        "protocol": reader.GRAPH_PROTOCOL,
        "model": reader.GRAPH_MODEL,
        "caseId": reader.GRAPH_CASE_ID,
        "width": width,
        "height": height,
        "primary": primary,
        "steps": list(steps if steps is not None else [0] * (2 * cells)),
        "vias": list(vias if vias is not None else [False] * cells),
        "goals": list(goals if goals is not None else [False] * (2 * cells)),
        "starts": [0, cells],
    }
    graph = _pack_graph(graph)
    integrity = _reseal_wire_graph(graph)
    grid = {"width": width, "height": height, "cells": cells,
            "originX": 0, "originY": 0, "pitch": 10}
    return graph, integrity, grid


def _graph(width=3, height=2, goals=None, steps=None, vias=None, primary=0):
    wire, integrity, grid = _wire_graph(
        width=width, height=height, goals=goals, steps=steps, vias=vias, primary=primary)
    graph, _ = reader._validate_graph(wire, integrity, grid, 0)
    return graph, integrity, grid


def _reseal_wire_graph(graph):
    canonical = reader._canonical_json(graph)
    return {
        "encoding": reader.GRAPH_ENCODING,
        "canonicalBytes": len(canonical),
        "sha256": hashlib.sha256(canonical).hexdigest(),
    }


def _root_face_goals(cells, extra_states=()):
    goals = [False] * (2 * cells)
    goals[0] = True
    goals[cells] = True
    for state in extra_states:
        goals[state] = True
    return goals


def _synthetic_pad(pad_id, attachment="PLATED_THROUGH_HOLE", side="TOP"):
    return {
        "padId": pad_id, "netId": "A_DRIVE", "x": 0, "y": 0,
        "escapeDx": 0, "escapeDy": 0, "escapeLength": 0,
        "attachment": attachment, "mountingSide": side, "exposure": "EXPOSED",
        "padBounds": {"x": 0, "y": 0, "width": 10, "height": 10},
        "probeBounds": {"x": 0, "y": 0, "width": 10, "height": 10},
        "copperLayerMask": (3 if attachment == "PLATED_THROUGH_HOLE" else
                             (1 if side == "TOP" else 2)),
        "gridCell": 0,
    }


def _synthetic_holes(count=37):
    # Off-grid / out-of-grid NPTH rows remain present as obstacles and contribute
    # to the all-hole count, but cannot create graph goals.
    return [
        {"id": "H%03d" % index, "kind": "NON_PLATED", "netId": None,
         "x": 100, "y": 100, "drillRadius": 1, "landRadius": 1,
         "exposure": "EXPOSED", "conductiveLayerMask": 0, "gridCell": None}
        for index in range(count)
    ]


def _source_census(graph, grid, traces=None, reached_pads=None):
    start = _synthetic_pad("START")
    root = _synthetic_pad("ROOT")
    reached_pads = list(reached_pads or [])
    pads = sorted((start, root, *reached_pads),
                  key=lambda row: reader._java_sort_key(row["padId"]))
    traces = list(traces or [])
    holes = _synthetic_holes()
    reached_ids = sorted((row["padId"] for row in reached_pads),
                         key=reader._java_sort_key)
    goals = [state for state, goal in enumerate(graph["goals"]) if goal]
    provenance = []
    for state in goals:
        layer, cell = divmod(state, grid["cells"])
        refs = []
        if cell == 0:
            refs.append({"sourceType": "PAD", "sourceId": "ROOT", "role": "ROOT_PAD"})
        for pad in reached_pads:
            if pad["gridCell"] == cell and pad["copperLayerMask"] & (1 << layer):
                refs.append({"sourceType": "PAD", "sourceId": pad["padId"],
                             "role": "REACHED_PTH_PAD"})
        for trace in traces:
            if trace["layer"] == layer and cell in trace["routerGridCells"]:
                refs.append({"sourceType": "TRACE", "sourceId": trace["sourceId"],
                             "role": "SAME_NET_TREE_TRACE"})
        refs.sort(key=reader._source_ref_key)
        provenance.append({"state": state, "cell": cell, "layer": layer,
                           "sourceRefs": refs})
    kind_counts = [{"kind": "VIA", "count": 0}, {"kind": "PLATED", "count": 0},
                   {"kind": "NON_PLATED", "count": len(holes)}]
    row_counts = {
        "padRows": len(pads), "traceRows": len(traces), "holeRows": len(holes),
        "sourceRows": len(pads) + len(traces) + len(holes),
        "goalStates": len(goals), "allHoleCount": len(holes), "viaKindCount": 0,
    }
    canonical_object = {"goalProvenance": provenance, "holeRows": holes,
                        "padRows": pads, "traceRows": traces}
    canonical = reader._canonical_json(canonical_object)
    return {
        "padRows": pads, "traceRows": traces, "holeRows": holes,
        "reachedPthPadIds": reached_ids, "holeKindCounts": kind_counts,
        "goalProvenance": provenance, "rowCounts": row_counts,
        "sourceRowsCanonicalBytes": len(canonical),
        "sourceRowsSha256": hashlib.sha256(canonical).hexdigest(),
    }


def _reseal_source_census(census):
    canonical_object = {"goalProvenance": census["goalProvenance"],
                        "holeRows": census["holeRows"],
                        "padRows": census["padRows"],
                        "traceRows": census["traceRows"]}
    canonical = reader._canonical_json(canonical_object)
    census["sourceRowsCanonicalBytes"] = len(canonical)
    census["sourceRowsSha256"] = hashlib.sha256(canonical).hexdigest()


def _rehashed_valid_graph(graph, grid):
    wire = _pack_graph(graph)
    canonical = reader._canonical_json(wire)
    integrity = {
        "encoding": reader.GRAPH_ENCODING,
        "canonicalBytes": len(canonical),
        "sha256": hashlib.sha256(canonical).hexdigest(),
    }
    validated, _ = reader._validate_graph(wire, integrity, grid, 0)
    return validated


def _synthetic_capture(graph):
    target = {"ownerIdentity": 1, "searchIdentity": 2,
              "branchIdentity": 3, "layoutIdentity": 4,
              "branchOrdinal": 142, "pass": 1, "netId": "A_DRIVE"}
    bit_bytes = bytes(ord("1") if bit else ord("0") for bit in graph["goals"])
    capture = {
        "status": "COMPLETE", "taskClass": reader.EXPECTED_TASK_CLASS,
        "captureTaskIdentity": 5, "snapshotIdentity": 6, "contextIdentity": 7,
        "ownerIdentity": 1, "searchIdentity": 2, "branchIdentity": 3,
        "layoutIdentity": 4, "routerIdentities": [8, 9], "branchOrdinal": 142,
        "pass": 1, "netId": "A_DRIVE", "sourceGoalFaceCount": sum(graph["goals"]),
        "sourceGoalBitsSha256": hashlib.sha256(bit_bytes).hexdigest(),
        "branchAtPublisherStage": True, "sourcePaused": True,
        "routerStateUnchanged": True, "routerEffectsUnchanged": True,
        "prefixGeometryUnchanged": True, "goalArrayParity": True,
        "sourceAttributionComplete": True, "liveReferencesReleased": True,
    }
    return target, capture


def _synthetic_prefix():
    return {"before": {"holeCount": 37, "traceCount": 177}}


class GraphShapeTests(unittest.TestCase):
    def test_cell_limit_exact_boundary_and_overflow(self):
        self.assertEqual(250_000, reader._check_grid_dimensions(500, 500))
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._check_grid_dimensions(500, 501)

    def test_closed_graph_shape_and_canonical_digest(self):
        goals = [True] + [False] * 11
        graph, integrity, grid = _wire_graph(goals=goals)
        checked, canonical = reader._validate_graph(graph, integrity, grid, 0)
        self.assertEqual(goals, checked["goals"])
        self.assertEqual([0] * 12, checked["steps"])
        self.assertEqual([False] * 6, checked["vias"])
        self.assertEqual(integrity["canonicalBytes"], len(canonical))
        changed = copy.deepcopy(graph)
        changed["extra"] = False
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_graph(changed, integrity, grid, 0)

    def test_graph_rejects_old_arrays_bad_packed_masks_and_out_of_grid_step(self):
        graph, _integrity, grid = _wire_graph()
        invalid_shapes = []
        old_arrays = copy.deepcopy(graph)
        old_arrays["steps"] = [0] * 12
        old_arrays["vias"] = [False] * 6
        old_arrays["goals"] = [False] * 12
        invalid_shapes.append(old_arrays)
        for malformed_steps in (
                graph["steps"][:-1], graph["steps"] + "0",
                "A" + graph["steps"][1:], "g" + graph["steps"][1:],
                "\uff10" + graph["steps"][1:]):
            malformed = copy.deepcopy(graph)
            malformed["steps"] = malformed_steps
            invalid_shapes.append(malformed)
        malformed_vias = copy.deepcopy(graph)
        malformed_vias["vias"] = "2" + malformed_vias["vias"][1:]
        invalid_shapes.append(malformed_vias)
        short_vias = copy.deepcopy(graph)
        short_vias["vias"] = short_vias["vias"][:-1]
        invalid_shapes.append(short_vias)
        malformed_goals = copy.deepcopy(graph)
        malformed_goals["goals"] = "\uff11" + malformed_goals["goals"][1:]
        invalid_shapes.append(malformed_goals)
        short_goals = copy.deepcopy(graph)
        short_goals["goals"] = short_goals["goals"][:-1]
        invalid_shapes.append(short_goals)
        old_protocol = copy.deepcopy(graph)
        old_protocol["protocol"] = "p07-fixed-prefix-graph-v1"
        invalid_shapes.append(old_protocol)
        for malformed in invalid_shapes:
            with self.subTest(steps=malformed["steps"]):
                with self.assertRaises(reader.ADriveGraphInputError):
                    reader._validate_graph(malformed, _reseal_wire_graph(malformed), grid, 0)

        old_encoding = _reseal_wire_graph(graph)
        old_encoding["encoding"] = "CANONICAL_COMPACT_ASCII_SORTED_KEYS"
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_graph(graph, old_encoding, grid, 0)

        graph, _integrity, grid = _wire_graph(width=2, height=2)
        graph["steps"] = "1" + graph["steps"][1:]  # north from the top row
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_graph(graph, _reseal_wire_graph(graph), grid, 0)

    def test_start_array_must_include_both_faces_at_the_same_cell(self):
        graph, _integrity, grid = _wire_graph()
        graph["starts"] = [0]
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_graph(graph, _reseal_wire_graph(graph), grid, 0)

    def test_nested_graph_canonical_byte_limit(self):
        self.assertLessEqual(reader.MAX_GRAPH_BYTES, reader.MAX_GRAPH_PAYLOAD_BYTES)
        exact = b"x" * reader.MAX_GRAPH_BYTES
        self.assertEqual(exact, reader._bounded_bytes(
            exact, reader.MAX_GRAPH_BYTES, "SYNTHETIC_GRAPH_BOUNDARY"))
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._bounded_bytes(exact + b"x", reader.MAX_GRAPH_BYTES,
                                  "SYNTHETIC_GRAPH_BOUNDARY")


class SourceCensusTests(unittest.TestCase):
    def test_small_synthetic_census_validates_rows_and_root_goal(self):
        graph, _integrity, grid = _graph(goals=_root_face_goals(6))
        target = {"netId": "A_DRIVE"}
        endpoints = {
            "start": {"padId": "START", "x": 0, "y": 0, "cell": 0},
            "root": {"padId": "ROOT", "x": 0, "y": 0, "cell": 0},
        }
        checked = reader._validate_source_census(
            _source_census(graph, grid), graph, grid, endpoints, target,
            _synthetic_prefix())
        self.assertEqual(37, checked["rowCounts"]["allHoleCount"])
        self.assertEqual(2, checked["rowCounts"]["goalStates"])
        self.assertEqual(0, checked["rowCounts"]["viaKindCount"])

    def test_missing_or_false_goal_source_is_rejected(self):
        graph, _integrity, grid = _graph(goals=_root_face_goals(6))
        target = {"netId": "A_DRIVE"}
        endpoints = {"start": {"padId": "START", "x": 0, "y": 0, "cell": 0},
                     "root": {"padId": "ROOT", "x": 0, "y": 0, "cell": 0}}
        census = _source_census(graph, grid)
        census["goalProvenance"][0]["sourceRefs"] = []
        _reseal_source_census(census)
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_source_census(census, graph, grid, endpoints, target,
                                           _synthetic_prefix())

        def rejects_missing_face(face_graph, face_census, state):
            face_graph["goals"][state] = False
            face_census["goalProvenance"] = [
                row for row in face_census["goalProvenance"] if row["state"] != state]
            face_census["rowCounts"]["goalStates"] = sum(face_graph["goals"])
            _reseal_source_census(face_census)
            validated_graph = _rehashed_valid_graph(face_graph, grid)
            capture_target, capture = _synthetic_capture(validated_graph)
            reader._validate_capture(capture, validated_graph, capture_target, grid)
            with self.assertRaises(reader.ADriveGraphInputError):
                reader._validate_source_census(
                    face_census, validated_graph, grid, endpoints, target,
                    _synthetic_prefix())

        root_graph, _integrity, _root_grid = _graph(goals=_root_face_goals(6))
        root_census = _source_census(root_graph, grid)
        rejects_missing_face(root_graph, root_census, grid["cells"])

        reached = _synthetic_pad("TERM")
        reached.update({
            "x": 10, "padBounds": {"x": 10, "y": 0, "width": 10, "height": 10},
            "probeBounds": {"x": 10, "y": 0, "width": 10, "height": 10},
            "gridCell": 1,
        })
        reached_graph, _integrity, _reached_grid = _graph(
            goals=_root_face_goals(6, (1, grid["cells"] + 1)))
        reached_census = _source_census(reached_graph, grid, reached_pads=[reached])
        reader._validate_source_census(
            reached_census, reached_graph, grid, endpoints, target, _synthetic_prefix())
        rejects_missing_face(reached_graph, reached_census, grid["cells"] + 1)

    def test_surface_pad_source_row_is_rejected(self):
        graph, _integrity, grid = _graph(goals=_root_face_goals(6))
        target = {"netId": "A_DRIVE"}
        endpoints = {"start": {"padId": "START", "x": 0, "y": 0, "cell": 0},
                     "root": {"padId": "ROOT", "x": 0, "y": 0, "cell": 0}}
        census = _source_census(graph, grid)
        root = next(row for row in census["padRows"] if row["padId"] == "ROOT")
        root.update({"attachment": "SURFACE_PAD", "mountingSide": "TOP",
                     "copperLayerMask": 1})
        _reseal_source_census(census)
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_source_census(census, graph, grid, endpoints, target,
                                           _synthetic_prefix())

        start_reached = _source_census(graph, grid)
        start_reached["reachedPthPadIds"] = ["START"]
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_source_census(
                start_reached, graph, grid, endpoints, {"netId": "A_DRIVE"}, _synthetic_prefix())

        goals = [False] * 12
        goals[0] = goals[1] = goals[7] = True
        graph, _integrity, grid = _graph(goals=goals)
        census = _source_census(graph, grid)
        root = next(row for row in census["padRows"] if row["padId"] == "ROOT")
        root.update({
            "x": 10, "padBounds": {"x": 10, "y": 0, "width": 10, "height": 10},
            "probeBounds": {"x": 10, "y": 0, "width": 10, "height": 10},
            "gridCell": 1,
        })
        for row in census["goalProvenance"]:
            row["sourceRefs"] = ([] if row["state"] == 0 else [{
                "sourceType": "PAD", "sourceId": "ROOT", "role": "ROOT_PAD",
            }])
        _reseal_source_census(census)
        endpoints = {"start": {"padId": "START", "x": 0, "y": 0, "cell": 0},
                     "root": {"padId": "ROOT", "x": 10, "y": 0, "cell": 1}}
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_source_census(
                census, graph, grid, endpoints, {"netId": "A_DRIVE"},
                _synthetic_prefix())

    def test_trace_goal_requires_exact_router_occupancy_and_identity(self):
        goals = _root_face_goals(6, (1,))
        graph, _integrity, grid = _graph(goals=goals)
        trace = {
            "sourceId": "TRACE-1", "netId": "A_DRIVE", "startPadId": None,
            "endPadId": None, "layer": 0, "exposure": "EXPOSED", "width": 9,
            "xPoints": [0, 20], "yPoints": [0, 0], "routerGridCells": [0, 1, 2],
        }
        census = _source_census(graph, grid, [trace])
        endpoints = {"start": {"padId": "START", "x": 0, "y": 0, "cell": 0},
                     "root": {"padId": "ROOT", "x": 0, "y": 0, "cell": 0}}
        checked = reader._validate_source_census(
            census, graph, grid, endpoints, {"netId": "A_DRIVE"}, _synthetic_prefix())
        trace_goal = next(row for row in checked["goalProvenance"] if row["state"] == 1)
        self.assertEqual([{"sourceType": "TRACE", "sourceId": "TRACE-1",
                           "role": "SAME_NET_TREE_TRACE"}],
                         trace_goal["sourceRefs"])
        broken = copy.deepcopy(census)
        broken_trace_goal = next(row for row in broken["goalProvenance"]
                                 if row["state"] == 1)
        broken_trace_goal["sourceRefs"][0]["sourceId"] = "TRACE-2"
        _reseal_source_census(broken)
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_source_census(
                broken, graph, grid, endpoints, {"netId": "A_DRIVE"}, _synthetic_prefix())

    def test_only_same_net_conductive_holes_are_barrel_sources(self):
        goals = _root_face_goals(6)
        graph, _integrity, grid = _graph(goals=goals)
        census = _source_census(graph, grid)
        census["holeRows"][0].update({
            "kind": "VIA", "netId": "A_DRIVE", "x": 0, "y": 0,
            "drillRadius": 1, "landRadius": 2,
            "conductiveLayerMask": 3, "gridCell": 0,
        })
        census["holeKindCounts"] = [
            {"kind": "VIA", "count": 1}, {"kind": "PLATED", "count": 0},
            {"kind": "NON_PLATED", "count": 36},
        ]
        census["rowCounts"]["viaKindCount"] = 1
        for provenance in census["goalProvenance"]:
            provenance["sourceRefs"].append(
                {"sourceType": "HOLE", "sourceId": "H000",
                 "role": "SAME_NET_VIA_BARREL"})
            provenance["sourceRefs"].sort(key=reader._source_ref_key)
        _reseal_source_census(census)
        endpoints = {"start": {"padId": "START", "x": 0, "y": 0, "cell": 0},
                     "root": {"padId": "ROOT", "x": 0, "y": 0, "cell": 0}}
        reader._validate_source_census(
            census, graph, grid, endpoints, {"netId": "A_DRIVE"}, _synthetic_prefix())
        invalid = copy.deepcopy(census)
        invalid["holeRows"][0]["kind"] = "NON_PLATED"
        invalid["holeRows"][0]["netId"] = None
        invalid["holeRows"][0]["conductiveLayerMask"] = 0
        invalid["holeRows"][0]["landRadius"] = invalid["holeRows"][0]["drillRadius"]
        _reseal_source_census(invalid)
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_source_census(
                invalid, graph, grid, endpoints, {"netId": "A_DRIVE"}, _synthetic_prefix())

    def test_exact_source_row_collection_caps(self):
        self.assertEqual(16_384, reader._check_source_row_limits(246, 15_748, 390))
        for pads, traces, holes in ((247, 0, 0), (0, 0, 391), (246, 15_749, 390)):
            with self.subTest(pads=pads, traces=traces, holes=holes):
                with self.assertRaises(reader.ADriveGraphInputError):
                    reader._check_source_row_limits(pads, traces, holes)

    def test_pad_mapping_rejects_off_grid_and_escape_is_signed_unit(self):
        grid = {"width": 3, "height": 2, "cells": 6,
                "originX": 0, "originY": 0, "pitch": 10}
        row = _synthetic_pad("ROOT")
        self.assertEqual(0, reader._validate_pad_row(row, grid)["gridCell"])
        off_grid = copy.deepcopy(row)
        off_grid["x"] = 1
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_pad_row(off_grid, grid)
        bad_escape = copy.deepcopy(row)
        bad_escape["escapeDx"] = 2
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_pad_row(bad_escape, grid)
        escaped = copy.deepcopy(row)
        escaped["escapeDx"] = -1
        escaped["escapeLength"] = 5
        escaped["x"] = 10
        escaped["padBounds"] = {"x": 10, "y": 0, "width": 2, "height": 2}
        escaped["probeBounds"] = {"x": 10, "y": 0, "width": 2, "height": 2}
        escaped["gridCell"] = 1
        self.assertEqual(1, reader._validate_pad_row(escaped, grid)["gridCell"])

    def test_router_grid_points_follow_segment_ceil_floor_mapping(self):
        grid = {"width": 3, "height": 2, "cells": 6,
                "originX": 0, "originY": 0, "pitch": 10}
        self.assertEqual([0, 1, 2], reader._segment_grid_cells([0, 20], [0, 0], grid))
        self.assertEqual([0, 3], reader._segment_grid_cells([0, 0], [0, 10], grid))
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._segment_grid_cells([0, 20], [1, 1], grid)
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._segment_grid_cells([0, 10], [0, 10], grid)

    def test_hole_grid_mapping_keeps_off_grid_and_outside_holes_as_null(self):
        grid = {"width": 2, "height": 2, "cells": 4,
                "originX": 0, "originY": 0, "pitch": 10}
        self.assertEqual(0, reader._map_hole_cell(0, 0, grid))
        self.assertIsNone(reader._map_hole_cell(1, 0, grid))
        self.assertIsNone(reader._map_hole_cell(20, 0, grid))

    def test_java_utf16_sort_key_is_used_for_ids_and_source_refs(self):
        # U+10000 sorts before U+E000 in Java UTF-16 code units.
        astral = "\U00010000"
        private_use = "\ue000"
        self.assertLess(reader._java_sort_key(astral), reader._java_sort_key(private_use))
        reader._is_sorted_unique([astral, private_use], "SYNTHETIC_UTF16_ORDER")
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._is_sorted_unique([private_use, astral], "SYNTHETIC_UTF16_ORDER")

    def test_unpaired_surrogates_are_rejected(self):
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._identifier("bad\ud800id", "SYNTHETIC_UTF16")
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._check_all_strings({"bad": "\udfff"})


class PathAndPolicyTests(unittest.TestCase):
    def test_strict_via_spacing_19_rejects_20_per_axis_all_signs(self):
        for dx, dy in ((19, 19), (-19, 19), (19, -19), (-19, -19)):
            self.assertTrue(reader._via_spacing_conflict(0, 0, dx, dy))
        for dx, dy in ((20, 0), (0, 20), (-20, 0), (0, -20), (20, 19), (19, -20)):
            self.assertFalse(reader._via_spacing_conflict(0, 0, dx, dy))

    def test_observed_planar_path_checks_directional_mask_and_exact_cost(self):
        goals = [False] * 12
        goals[1] = True
        steps = [2] + [0] * 11
        graph, _integrity, grid = _graph(goals=goals, steps=steps)
        endpoints = {"start": {"cell": 0}, "end": {"cell": 1}}
        path = {"rowsSha256": "a" * 64, "nodes": [
            [0, 0, 0, 0, 4, 0, 0, 0, 0, 0, -1],
            [1, 0, 10, 0, 1, 0, 10, 0, 1, 0, 0],
        ]}
        policy = {"maximumHoleCount": 48}
        self.assertEqual(1, reader._verify_observed_path(
            graph, path, grid, endpoints, policy, 37)["endState"])
        graph["steps"][0] = 0
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._verify_observed_path(graph, path, grid, endpoints, policy, 37)
        graph["steps"][0] = 2
        path["nodes"][1][6] = 11
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._verify_observed_path(graph, path, grid, endpoints, policy, 37)

    def test_observed_via_uses_initial_secondary_cost_and_all_holes(self):
        goals = [True] + [False] * 11
        vias = [True] + [False] * 5
        graph, _integrity, grid = _graph(goals=goals, vias=vias)
        graph["primary"] = 0
        endpoints = {"start": {"cell": 0}, "end": {"cell": 0}}
        path = {"rowsSha256": "b" * 64, "nodes": [
            [0, 1, 0, 0, 4, 0, 2, 0, 0, 0, -1],
            [0, 0, 0, 0, 4, 1, 242, 0, 1, 0, 0],
        ]}
        self.assertEqual(1, reader._verify_observed_path(
            graph, path, grid, endpoints, {"maximumHoleCount": 48}, 37)["transitions"])
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._verify_observed_path(
                graph, path, grid, endpoints, {"maximumHoleCount": 48}, 48)

    def test_capture_goal_digest_binds_all_layer_major_bits(self):
        goals = [True] + [False] * 11
        graph, _integrity, grid = _graph(goals=goals)
        target, capture = _synthetic_capture(graph)
        self.assertEqual(capture, reader._validate_capture(capture, graph, target, grid))
        graph["goals"][1] = True
        validated_graph = _rehashed_valid_graph(graph, grid)
        changed_target, changed_capture = _synthetic_capture(validated_graph)
        reader._validate_capture(changed_capture, validated_graph, changed_target, grid)
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_capture(capture, validated_graph, target, grid)

    def test_node_count_exact_limit_and_overflow(self):
        self.assertEqual(4_096, reader._check_witness_node_count(4_096))
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._check_witness_node_count(4_097)

    def test_final_report_unit_count_boundary(self):
        reader._check_final_controller_units(639, 640)
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._check_final_controller_units(640, 641)


class WorkAndTransportTests(unittest.TestCase):
    def _group(self, kind, **overrides):
        calls = overrides.get("internalCalls", 1)
        bound = (reader.MAX_COPY_QUANTUM * calls
                 if kind == "COPY_OR_COMPARE" else
                 reader.MAX_SCAN_PROBES * calls if kind == "SCAN" else
                 reader.MAX_COPY_QUANTUM)
        row = {
            "kind": kind, "internalCalls": calls, "copyEntries": 0,
            "comparisonEntries": 0, "goalProbes": 0,
            "ordinaryEdgeProbes": 0, "viaProbes": 0,
            "conservativeWorkUpperBound": bound,
            "elapsedNanos": 1,
        }
        row.update(overrides)
        return row

    def test_ledger_covers_copy_scan_and_required_finalize_calls(self):
        ledger = [
            self._group("COPY_OR_COMPARE", internalCalls=4, copyEntries=131_072),
            self._group("SCAN", internalCalls=4, ordinaryEdgeProbes=16_384),
            self._group("TASK_FINALIZE"),
        ]
        totals = reader._validate_group_ledger(ledger, 3)
        self.assertEqual(9, totals["internalCalls"])
        self.assertEqual(16_384, totals["ordinaryEdgeProbes"])
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_group_ledger(ledger, 2)

    def test_ledger_rejects_mixed_categories_missing_finalize_and_false_bound(self):
        mixed = [self._group("SCAN", copyEntries=1),
                 self._group("TASK_FINALIZE")]
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_group_ledger(mixed, 2)
        missing_finalize = [self._group("COPY_OR_COMPARE")]
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_group_ledger(missing_finalize, 1)
        false_bound = [self._group("SCAN", ordinaryEdgeProbes=4,
                                   conservativeWorkUpperBound=3),
                       self._group("TASK_FINALIZE")]
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_group_ledger(false_bound, 2)
        for bad_group in (
                self._group("COPY_OR_COMPARE", internalCalls=5),
                self._group("COPY_OR_COMPARE", internalCalls=4, copyEntries=131_073),
                self._group("COPY_OR_COMPARE", internalCalls=4, copyEntries=1,
                            conservativeWorkUpperBound=reader.MAX_COPY_QUANTUM),
                self._group("SCAN", internalCalls=4, viaProbes=16_385)):
            with self.subTest(group=bad_group):
                with self.assertRaises(reader.ADriveGraphInputError):
                    reader._validate_group_ledger(
                        [bad_group, self._group("TASK_FINALIZE")], 2)

    def test_ledger_requires_one_finalizer_as_the_last_task_group(self):
        finalizer_first = [self._group("TASK_FINALIZE"),
                           self._group("COPY_OR_COMPARE")]
        duplicate_finalizer = [self._group("TASK_FINALIZE"),
                               self._group("TASK_FINALIZE")]
        for ledger in (finalizer_first, duplicate_finalizer):
            with self.subTest(ledger=ledger):
                with self.assertRaises(reader.ADriveGraphInputError):
                    reader._validate_group_ledger(ledger, len(ledger))

    def test_metadata_groups_are_separate_from_capture_task_ledger(self):
        ledger = [self._group("TASK_FINALIZE")]
        self.assertEqual(1, len(ledger))
        totals = reader._validate_group_ledger(ledger, 1)
        self.assertEqual(1, totals["internalCalls"])
        self.assertEqual(1, totals["copyEntries"] + totals["comparisonEntries"] +
                         totals["goalProbes"] + totals["ordinaryEdgeProbes"] +
                         totals["viaProbes"] + 1)  # metadata is not a ledger row
        reader._validate_metadata_envelope(16_384, 16_384, 32_768, 16_384, 1)
        for values in ((16_385, 16_384, 1, 16_384, 1),
                       (1, 1, 1, 16_385, 1),
                       (1, 1, 32_769, 16_384, 1)):
            with self.subTest(envelope=values):
                with self.assertRaises(reader.ADriveGraphInputError):
                    reader._validate_metadata_envelope(*values)

    def test_graph_guard_line_and_capture_file_byte_boundaries(self):
        for limit in (reader.MAX_GRAPH_PAYLOAD_BYTES, reader.MAX_GUARD_PAYLOAD_BYTES,
                      reader.MAX_LINE_BYTES, reader.MAX_CAPTURE_BYTES):
            raw = b"x" * limit
            self.assertEqual(limit, len(reader._bounded_bytes(
                raw, limit, "SYNTHETIC_SIZE")))
            with self.assertRaises(reader.ADriveGraphInputError):
                reader._bounded_bytes(raw + b"x", limit, "SYNTHETIC_SIZE")

    def test_exact_command_is_new_mode_with_no_projected_old_command(self):
        self.assertEqual(["-Rb100Load12Cost240GuardGraphReplay"],
                         reader._expected_command()["args"])
        self.assertNotEqual(["-Rb100Load12Cost240GuardReplay"],
                            reader._expected_command()["args"])
        self.assertEqual("p07-load12-cost240-adrive-guard-graph-input-v2",
                         reader.INPUT_SCHEMA)
        self.assertEqual("p07-load12-cost240-adrive-graph-reader-result-v2",
                         reader.RESULT_SCHEMA)
        self.assertEqual("rb100-load12-adrive-guard-graph-r2", reader.EVENT_SCHEMA)
        self.assertNotEqual("rb100-load12-adrive-guard-graph-r1", reader.EVENT_SCHEMA)
        self.assertEqual("p07-fixed-prefix-packed-graph-v1", reader.GRAPH_PROTOCOL)
        self.assertEqual("CANONICAL_COMPACT_ASCII_SORTED_KEYS_PACKED_MASKS",
                         reader.GRAPH_ENCODING)
        guard, helper, _guard_raw, _guard_path, _helper_raw, _helper_path = \
            reader._load_guard_reader()
        old_input = {key: None for key in reader.INPUT_SPEC_KEYS}
        old_input["schema"] = "p07-load12-cost240-adrive-guard-graph-input-v1"
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_input_spec(old_input, Path.cwd(), guard, helper)
        old_event = {key: None for key in reader.EVENT_KEYS}
        old_event.update({
            "protocolVersion": 1,
            "schema": "rb100-load12-adrive-guard-graph-r1",
            "status": "PASS",
            "runId": "12345678-1234-4234-9234-123456789abc",
            "operationId": "QUALITY_PROBE",
            "scope": reader.SCOPE,
        })
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_graph_event(
                old_event, b"{}", None, None, b"", b"", "0" * 64, "0" * 64,
                guard, helper)

    def test_exact_four_lf_record_transport_and_byte_bindings(self):
        _guard, helper, _guard_raw, _guard_path, _helper_raw, _helper_path = \
            reader._load_guard_reader()
        rows = [
            reader.BASE_EVENT_MARKER + ' {"base":1}',
            reader.GUARD_EVENT_MARKER + ' {"guard":2}',
            reader.GRAPH_EVENT_MARKER + ' {"note":"\u2028"}',
            reader.SUMMARY_PREFIX + "9",
        ]
        receipt = ("\n".join(rows) + "\n").encode("utf-8")
        stdout = ("\n".join(rows + [reader.FOCUSED_PASS, reader.CLEANUP_LINE]) +
                  "\n").encode("utf-8")
        parsed = reader._validate_console_and_receipt(stdout, receipt, b"", helper)
        self.assertEqual(hashlib.sha256(b'{"base":1}').hexdigest(),
                         hashlib.sha256(parsed[3]).hexdigest())
        self.assertEqual('{"note":"\u2028"}', parsed[9].decode("utf-8"))

    def test_receipt_rejects_bom_crlf_extra_or_missing_records(self):
        _guard, helper, _guard_raw, _guard_path, _helper_raw, _helper_path = \
            reader._load_guard_reader()
        rows = [
            reader.BASE_EVENT_MARKER + " {}",
            reader.GUARD_EVENT_MARKER + " {}",
            reader.GRAPH_EVENT_MARKER + " {}",
            reader.SUMMARY_PREFIX + "1",
        ]
        console = ("\n".join(rows + [reader.FOCUSED_PASS, reader.CLEANUP_LINE]) +
                   "\n").encode("utf-8")
        good = ("\n".join(rows) + "\n").encode("utf-8")
        for invalid in (b"\xef\xbb\xbf" + good,
                        good.replace(b"\n", b"\r\n"),
                        good + b"EXTRA\n",
                        ("\n".join(rows[:3]) + "\n").encode("utf-8")):
            with self.subTest(size=len(invalid)):
                with self.assertRaises(reader.ADriveGraphInputError):
                    reader._validate_console_and_receipt(console, invalid, b"", helper)
        with self.assertRaises(reader.ADriveGraphInputError):
            reader._validate_console_and_receipt(
                console + b"rb100_unexpected {}\n", good, b"", helper)

    def test_reparse_reader_helper_source_is_rejected(self):
        with tempfile.TemporaryDirectory(prefix="adrive-graph-reparse-") as temp:
            directory = Path(temp)
            source = directory / "plain.py"
            link = directory / "reader.py"
            source.write_text("safe", encoding="utf-8")
            try:
                link.symlink_to(source)
            except (OSError, NotImplementedError):
                self.skipTest("host does not permit an unprivileged file symlink")
            with self.assertRaises(reader.ADriveGraphInputError):
                reader._bootstrap_read(link, 100, "SYNTHETIC_REPARSE")

    def test_private_frozen_guard_and_base_loaders_leave_no_module_registration(self):
        guard_name = "_p07_adrive_guard_reader_private"
        base_name = "_p07_adrive_guard_base_reader_private"
        self.assertNotIn(guard_name, sys.modules)
        self.assertNotIn(base_name, sys.modules)
        guard, helper, guard_raw, _guard_path, helper_raw, _helper_path = \
            reader._load_guard_reader()
        self.assertEqual(reader.GUARD_READER_SHA256, hashlib.sha256(guard_raw).hexdigest())
        self.assertEqual(reader.BASE_READER_SHA256, hashlib.sha256(helper_raw).hexdigest())
        self.assertTrue(callable(guard._validate_witness))
        self.assertTrue(callable(helper._validate_event))
        self.assertNotIn(guard_name, sys.modules)
        self.assertNotIn(base_name, sys.modules)

    def test_closed_native_result_rejects_old_switch_and_optional_manifest_bytes(self):
        context = {
            "inputCount": reader.SOURCE_INPUT_COUNT, "inputBytes": 10,
            "pinsSha256": "a" * 64, "stdoutRaw": b"stdout\n",
            "receiptRaw": b"receipt\n", "stderrRaw": b"",
        }
        native = {
            "schema": reader.RUN_SCHEMA, "source": "candidate", "suite": reader.SUITE,
            "cohortId": None, "command": reader._expected_command(),
            "layoutVersion": 26, "dependencyEpoch": "tsj-generation-dependencies-v28",
            "sourceSnapshotRevision": 20, "inputCount": reader.SOURCE_INPUT_COUNT,
            "inputBytes": 10, "inputPinsSha256": "a" * 64,
            "inputAudit": "PASS_BEFORE_AND_AFTER", "exitCode": 0, "nativeExitCode": 0,
            "terminationProven": True, "cleanupStatus": "PASS", "cleanupElapsedMs": None,
            "viaCost": 60, "nativeLimitMs": reader.MAX_NATIVE_LIMIT_MS,
            "outerLimitMs": reader.MAX_OUTER_LIMIT_MS, "processId": 10,
            "processStartTicks": "134089000000000000", "elapsedSeconds": 1.0,
            "requestedExecutable": "powershell.exe", "stdoutBytes": 7,
            "stdoutSha256": hashlib.sha256(context["stdoutRaw"]).hexdigest(),
            "stderrBytes": 0, "stderrSha256": hashlib.sha256(b"").hexdigest(),
            "receiptBytes": 8, "receiptSha256": hashlib.sha256(context["receiptRaw"]).hexdigest(),
            "target": "LOAD12",
        }
        self.assertEqual(native, reader._validate_native_run(native, context))
        for mutation in (
                lambda row: row["command"].update(args=["-Rb100Load12Cost240GuardReplay"]),
                lambda row: row.update(inputManifestBytes=1)):
            changed = copy.deepcopy(native)
            mutation(changed)
            with self.assertRaises(reader.ADriveGraphInputError):
                reader._validate_native_run(changed, context)


class PolicyWireContractTests(unittest.TestCase):
    def test_exact_wire_spacing_flag_name_and_value(self):
        policy = {
            "layerMap": ["TOP", "BOTTOM"],
            "directionBits": "NORTH=1,EAST=2,SOUTH=4,WEST=8",
            "viaMaskSemantics": "HISTORY_RELAXED",
            "viaMaskPredicate": "relaxedViaFits",
            "primaryStepCost": 10, "secondaryStepCost": 12,
            "secondaryStartCost": 2, "turnCost": 35, "copiedViaCost": 240,
            "maximumHoleCount": 48, "actualAllHoleCount": 37,
            "actualViaKindCount": 37, "maximumTransitions": 4,
            "pathTransitionPredicate": "pathViaFits",
            "viaMaskExcludesRemainingHoleCapacity": True,
            "viaMaskExcludesEarlierPathViaSpacing": True,
            "searchPerformed": False, "routeFeasibilityClaim": False,
            "viaSpacing": {"axisThreshold": 20, "comparison": "STRICT_LT",
                           "combineAxes": "AND",
                           "compareAgainst": "EVERY_EARLIER_PATH_VIA",
                           "equalityAt20Permitted": True},
        }
        counts = {"allHoleCount": 37, "viaKindCount": 37}
        self.assertEqual(policy, reader._validate_policy(policy, counts))
        wrong = copy.deepcopy(policy)
        wrong["viaMaskExcludesEarlierPathSpacing"] = wrong.pop(
            "viaMaskExcludesEarlierPathViaSpacing")
        with self.assertRaisesRegex(reader.ADriveGraphInputError, "POLICY_KEYS_INVALID"):
            reader._validate_policy(wrong, counts)
        wrong = copy.deepcopy(policy)
        wrong["viaMaskExcludesEarlierPathViaSpacing"] = False
        with self.assertRaisesRegex(reader.ADriveGraphInputError, "POLICY_VALUES_INVALID"):
            reader._validate_policy(wrong, counts)


class PrefixSourceBindingTests(unittest.TestCase):
    def test_remainder_source_keys_preservation_and_wrong_guard_aliases(self):
        row = {"layoutIdentity": 7, "traceCount": 177, "holeCount": 37,
               "geometrySha256": "a" * 64, "traceRowsSha256": "b" * 64,
               "holeRowsSha256": "c" * 64}
        prefix = {"before": row, "after": copy.deepcopy(row), "unchanged": True}
        guard = {"prefix": copy.deepcopy(prefix)}
        source = {"holes": 17, "traces": 88}
        base = {"copy": {"layoutIdentity": 7},
                "source": {"before": source, "after": copy.deepcopy(source)}}
        self.assertEqual(prefix, reader._validate_prefix(prefix, guard, base))
        for wrong in (
                {"before": {"holes": 18, "traces": 88},
                 "after": {"holes": 18, "traces": 88}},
                {"before": source, "after": {"holes": 17, "traces": 87}},
                {"before": {"holeCount": 17, "traceCount": 88},
                 "after": {"holeCount": 17, "traceCount": 88}}):
            with self.subTest(source=wrong):
                changed = copy.deepcopy(base)
                changed["source"] = wrong
                with self.assertRaisesRegex(reader.ADriveGraphInputError,
                                            "PREFIX_SOURCE_LINEAGE_INVALID"):
                    reader._validate_prefix(prefix, guard, changed)


if __name__ == "__main__":
    unittest.main()
