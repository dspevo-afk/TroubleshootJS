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
            {"index": 0, "parentIndex": -1, "cell": 0, "x": 0, "y": 0,
             "layer": 1, "direction": 4, "transitions": 0, "cost": 0,
             "heuristic": 10, "serial": 0, "key": 54},
            {"index": 1, "parentIndex": 0, "cell": 1, "x": 10, "y": 0,
             "layer": 1, "direction": 1, "transitions": 0, "cost": 10,
             "heuristic": 0, "serial": 1, "key": 37},
        ],
        "transitionCount": 0,
        "pathNodeCount": 2,
    }
    query_count = reader._ordinary_query_count(width, height)
    work = {
        "advanceCalls": 1, "workSteps": 0,
        "copyEntries": 6, "comparisonEntries": 6,
        "geometryBytes": 1, "effectUnits": 1,
        "ordinaryQueries": query_count, "goalCells": cells,
        "reachedPadChecks": 0, "encodedCells": 3 * cells,
        "pathNodes": 2, "pathEdgesVerified": 1,
        "tupleBytes": len(reader._graph_tuple_bytes(path["nodes"])),
        "serializedUtf16Units": 0, "activeMillis": 1,
    }
    goal_bits = "020000"
    binding = {
        "branchIdentityVerified": True, "sourcePublisherUnchanged": True,
        "sourceKind": "D_SENSE_PUBLISHER_PATH", "pass": 1,
        "branchOrdinal": 9, "netPosition": 2, "netIndexNextFetch": 3,
        "netId": "D_SENSE", "startPadId": "U2D.SENSOR",
        "rootPadId": "CFLT_D.1", "sourceReleasedBeforeSuccessorWork": True,
        "reachedPthPadIds": [],
        "foundTerminalPadId": "CFLT_D.1", "endCell": 1, "endLayer": 1,
        "goalMaskLength": 2 * cells, "goalUnionCellCount": 1,
        "policy": "FULLER_TWO_LAYER",
        "primaryLayer": 1, "initialLayerMask": 3,
        "maximumVias": 48, "maximumTransitions": 4, "prefixHoleCount": 0,
        "pathNodeCount": 2, "pathTransitionCount": 0,
        "pathTupleSha256": reader._graph_tuple_sha256(path["nodes"]),
        "goalFaceBitsSha256": hashlib.sha256(goal_bits.encode("ascii")).hexdigest(),
    }
    event = {
        "protocolVersion": 1, "sourceRevision": 20, "runId": RUN_ID,
        "operationId": "QUALITY_PROBE", "target": "D_SENSE",
        "scope": "ACTUAL_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY",
        "binding": binding,
        "diagnostic": {
            "schema": "p07-zero-via-graph-r5",
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
                "endCell": 1, "endLayer": 1, "goalUnionCellCount": 1,
            },
            "path": {
                "nodeCount": 2, "planarEdgeCount": 1,
                "transitionCount": 0,
                "tupleSha256": binding["pathTupleSha256"],
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
    work["serializedUtf16Units"] = reader._graph_serialized_json_utf16_units(
        event["diagnostic"])
    work["workSteps"] = (
        work["copyEntries"] + work["comparisonEntries"] +
        work["geometryBytes"] + work["effectUnits"] +
        work["ordinaryQueries"] + work["goalCells"] +
        work["reachedPadChecks"] + work["pathNodes"] +
        work["pathEdgesVerified"] + work["tupleBytes"] +
        work["serializedUtf16Units"])
    return scalar, path, event


def _fixture_allocation_binding(event):
    binding = event["binding"]
    return {
        "currentBranchOrdinal": binding["branchOrdinal"],
        "currentNetPosition": binding["netPosition"],
        "searchNetIndex": binding["netIndexNextFetch"],
        "prefixHoleCount": binding["prefixHoleCount"],
        "maximumVias": binding["maximumVias"],
    }


def _validate_graph_fixture(event, scalar, path):
    return reader._validate_graph_event(
        event, scalar, path,
        allocation_binding=_fixture_allocation_binding(event))


def _load12_graph_fixture():
    scalar, _d_path, event = _event_fixture()
    cells = 4
    nodes = [
        {"index": 0, "parentIndex": -1, "cell": 0, "x": 0, "y": 0,
         "layer": 0, "direction": 4, "transitions": 0, "cost": 0,
         "heuristic": 20, "serial": 0, "key": 16},
        {"index": 1, "parentIndex": 0, "cell": 1, "x": 10, "y": 0,
         "layer": 0, "direction": 1, "transitions": 0, "cost": 10,
         "heuristic": 10, "serial": 1, "key": 5},
        {"index": 2, "parentIndex": 1, "cell": 1, "x": 10, "y": 0,
         "layer": 1, "direction": 4, "transitions": 1, "cost": 12,
         "heuristic": 10, "serial": 2, "key": 77},
        {"index": 3, "parentIndex": 2, "cell": 3, "x": 10, "y": 10,
         "layer": 1, "direction": 2, "transitions": 1, "cost": 22,
         "heuristic": 0, "serial": 3, "key": 71},
    ]
    tuple_sha = reader._graph_tuple_sha256(nodes)
    goal_bits = "0002"
    goal_sha = hashlib.sha256(goal_bits.encode("ascii")).hexdigest()
    source_grid = {
        "minX": 0, "minY": 0, "width": 2, "height": 2, "cells": cells,
        "primaryLayer": "BOTTOM", "maximumVias": 48,
        "maximumTransitions": 4,
    }
    source_fields = {
        "pass": 1, "netId": "LOAD12", "branchOrdinal": 73,
        "netPosition": 3, "netIndexNextFetch": 4,
        "policy": "FULLER_TWO_LAYER", "templateHoleCount": 0,
        "startPadId": "JLOAD.1", "rootPadId": "CSTORE_E.1",
        "reachedPthPadIds": [], "startX": 0, "startY": 0,
        "rootX": 10, "rootY": 10,
    }
    path_source = {
        "path": {"nodeCount": len(nodes)}, "transitions": 1,
        "tupleSha256": tuple_sha, "endCell": 3, "endLayer": 1,
        "nodes": nodes,
        "goal": {"faceBitsSha256": goal_sha, "cellCount": 1},
    }
    pre = {
        "source": {"source": source_fields, "grid": source_grid,
                   "startCell": 0},
        "path": path_source,
        "prefix": {
            "inventory": {"totalHoles": 1},
            "stability": {"prefixGeometrySha256": "c" * 64},
        },
        "seeds": {"snapshot": {"faceMask": 3}},
    }
    path_source["endTerminalPadId"] = "CSTORE_E.1"
    work = {
        "advanceCalls": 1, "workSteps": 0,
        "copyEntries": 4, "comparisonEntries": 4,
        "geometryBytes": 1, "effectUnits": 1,
        "ordinaryQueries": reader._ordinary_query_count(2, 2),
        "goalCells": cells, "reachedPadChecks": 0,
        "encodedCells": 3 * cells, "pathNodes": len(nodes),
        "pathEdgesVerified": len(nodes) - 1,
        "tupleBytes": len(reader._graph_tuple_bytes(nodes)),
        "serializedUtf16Units": 0, "activeMillis": 1,
    }
    binding = {
        "branchIdentityVerified": True, "sourcePublisherUnchanged": True,
        "sourceKind": "LOAD12_ACCEPTED_PRE_RESULT", "pass": 1,
        "branchOrdinal": 73, "netPosition": 3, "netIndexNextFetch": 4,
        "netId": "LOAD12", "startPadId": "JLOAD.1",
        "rootPadId": "CSTORE_E.1", "sourceReleasedBeforeSuccessorWork": True,
        "reachedPthPadIds": [], "foundTerminalPadId": "CSTORE_E.1",
        "endCell": 3, "endLayer": 1, "goalMaskLength": 2 * cells,
        "goalUnionCellCount": 1, "policy": "FULLER_TWO_LAYER",
        "primaryLayer": 1, "initialLayerMask": 3, "maximumVias": 48,
        "maximumTransitions": 4, "prefixHoleCount": 1,
        "pathNodeCount": len(nodes), "pathTransitionCount": 1,
        "pathTupleSha256": tuple_sha, "goalFaceBitsSha256": goal_sha,
    }
    event["target"] = "LOAD12"
    event["scope"] = "ACTUAL_LOAD12_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY"
    event["binding"] = binding
    event["diagnostic"] = {
        "schema": "p07-zero-via-graph-r5",
        "stability": {
            "prefixGeometrySha256": "c" * 64,
            "liveBeforeVerified": True, "liveAfterVerified": True,
            "liveEffectsUnchanged": True, "goalVerified": True,
            "originalPathVerified": True,
        },
        "grid": {
            "width": 2, "height": 2, "cells": cells, "originX": 0,
            "originY": 0, "pitch": 10, "startCell": 0,
            "initialLayerMask": 3, "endCell": 3, "endLayer": 1,
            "goalUnionCellCount": 1,
        },
        "path": {
            "nodeCount": len(nodes), "planarEdgeCount": 2,
            "transitionCount": 1, "tupleSha256": tuple_sha,
        },
        "work": work,
        "masks": {
            "topStepsHex": "2000", "bottomStepsHex": "2400",
            "goalFacesHex": goal_bits,
        },
    }
    work["serializedUtf16Units"] = reader._graph_serialized_json_utf16_units(
        event["diagnostic"])
    work["workSteps"] = (
        work["copyEntries"] + work["comparisonEntries"] +
        work["geometryBytes"] + work["effectUnits"] +
        work["ordinaryQueries"] + work["goalCells"] +
        work["reachedPadChecks"] + work["pathNodes"] +
        work["pathEdgesVerified"] + work["tupleBytes"] +
        work["serializedUtf16Units"])
    return scalar, event, pre


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
    current_position = net_order.index("D_SENSE")
    graph_event["binding"]["branchOrdinal"] = 9
    graph_event["binding"]["netPosition"] = current_position
    graph_event["binding"]["netIndexNextFetch"] = current_position + 1
    graph_event["binding"]["maximumVias"] = 48
    graph_event["binding"]["prefixHoleCount"] = len(rows)
    graph_event["diagnostic"]["stability"]["prefixGeometrySha256"] = "b" * 64
    graph = reader._validate_graph_event(
        graph_event, scalar, path,
        allocation_binding={
            "currentBranchOrdinal": 9, "currentNetPosition": current_position,
            "searchNetIndex": current_position + 1,
            "prefixHoleCount": len(rows), "maximumVias": 48,
        })
    by_net = [
        {"netPosition": position, "netId": net_id,
         "holeCount": sum(1 for row in rows if row["netId"] == net_id)}
        for position, net_id in enumerate(net_order)
    ]
    event = {
        "protocolVersion": 1, "sourceRevision": 20, "runId": RUN_ID,
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
        "protocolVersion": 1, "sourceRevision": 20, "status": "PASS",
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


def _literal_trace(index, source_id, net_id, start_pad_id, end_pad_id,
                   layer, width, x_points, y_points):
    def field(value):
        if value is None:
            return "-1:"
        length = len(value.encode("utf-16-le", errors="strict")) // 2
        return f"{length}:{value}"

    exposure = "EXPOSED"
    raw = (field(source_id) + field(net_id) + field(start_pad_id) +
           field(end_pad_id) + layer + "\n" + exposure + "\n" + str(width) + "\n" +
           ",".join(str(point) for point in x_points) + "\n" +
           ",".join(str(point) for point in y_points))
    return {
        "index": index, "sourceId": source_id, "netId": net_id,
        "startPadId": start_pad_id, "endPadId": end_pad_id,
        "layer": layer, "exposure": exposure, "width": width,
        "xPoints": list(x_points), "yPoints": list(y_points),
        "traceFingerprint": hashlib.sha256(raw.encode("utf-8")).hexdigest(),
    }


def _fixture_json_string_units(value, legacy_prefix):
    units = 2
    for character in value:
        codepoint = ord(character)
        if character in ('"', "\\", "\b", "\f", "\n", "\r", "\t"):
            units += 2
        elif codepoint < 0x20 or codepoint == 0x7F:
            units += 6
        elif legacy_prefix:
            units += 2 if codepoint > 0xFFFF else 1
        elif 0xD800 <= codepoint <= 0xDFFF:
            units += 6
        elif codepoint > 0xFFFF:
            units += 12
        else:
            units += 1
    return units


def _fixture_serialized_json_units(value, fixed_counter_paths,
                                   legacy_raw_paths=()):
    def measure(item, path=(), legacy_prefix=False):
        if path in fixed_counter_paths:
            if type(item) is not int or item < 0:
                raise AssertionError("counter fixture must be a nonnegative integer")
            return 19
        if path in legacy_raw_paths:
            legacy_prefix = True
        if item is None:
            return 4
        if type(item) is bool:
            return 4 if item else 5
        if type(item) is int:
            return len(str(item))
        if type(item) is str:
            return _fixture_json_string_units(item, legacy_prefix)
        if type(item) is list:
            return (2 + max(0, len(item) - 1) +
                    sum(measure(child, path, legacy_prefix) for child in item))
        if type(item) is dict:
            total = 2 + max(0, len(item) - 1)
            for key, child in item.items():
                total += (_fixture_json_string_units(key, legacy_prefix) + 1 +
                          measure(child, path + (key,), legacy_prefix))
            return total
        raise AssertionError("fixture contains a non-JSON value")

    return measure(value)


def _load12_branch_fixture():
    net_order = ["NET_😀", "LOAD12", "D_SENSE"]
    old_hole = _literal_hole(
        0, "p07-via/LOAD12/10/0", "LOAD12", 1, 10, 0, 10, 0)
    prefix_inventory = {
        "stability": {
            "prefixGeometrySha256": "a" * 64,
            "liveBeforeVerified": True, "liveAfterVerified": True,
            "holeIdentitiesVerified": True, "lineageVerified": True,
        },
        "work": {
            "advanceCalls": 1, "workSteps": 7, "rowsScanned": 1,
            "netComparisons": 3, "netTotalsWritten": 3, "activeMillis": 1,
        },
        "totals": {
            "totalHoles": 1,
            "byKind": {"VIA": 1, "PLATED": 0, "NON_PLATED": 0},
            "byNet": [
                {"netPosition": 0, "netId": "NET_😀", "holeCount": 0},
                {"netPosition": 1, "netId": "LOAD12", "holeCount": 1},
                {"netPosition": 2, "netId": "D_SENSE", "holeCount": 0},
            ],
        },
        "rows": [old_hole],
    }
    prefix_trace = _literal_trace(
        0, "p07/LOAD12/68/0", "LOAD12", "ROOT.PAD", "PREV.PTH",
        "TOP", 10, [0, 10], [0, 0])

    # Literal source Nodes: four face flips, six planar steps, exact key and
    # parent identities. The seed serials are deliberately not 0 and 1.
    node_values = [
        (0, 0, 0, 0, 4, 0, 0, 60, 41, 64),
        (1, 10, 0, 0, 1, 0, 10, 50, 43, 17),
        (1, 10, 0, 1, 4, 1, 70, 50, 44, 305),
        (2, 20, 0, 1, 1, 1, 82, 40, 45, 258),
        (2, 20, 0, 0, 4, 2, 142, 40, 46, 386),
        (6, 20, 10, 0, 2, 2, 152, 30, 47, 358),
        (6, 20, 10, 1, 4, 3, 212, 30, 48, 630),
        (7, 30, 10, 1, 1, 3, 224, 20, 49, 583),
        (7, 30, 10, 0, 4, 4, 284, 20, 50, 711),
        (11, 30, 20, 0, 2, 4, 294, 10, 51, 683),
        (15, 30, 30, 0, 2, 4, 304, 0, 52, 687),
    ]
    path_nodes = []
    for index, fields in enumerate(node_values):
        cell, x, y, layer, direction, transitions, cost, heuristic, serial, key = fields
        path_nodes.append({
            "index": index, "parentIndex": -1 if index == 0 else index - 1,
            "cell": cell, "x": x, "y": y, "layer": layer,
            "direction": direction, "transitions": transitions,
            "cost": cost, "heuristic": heuristic, "serial": serial, "key": key,
        })
    tuple_fields = ("index", "parentIndex", "cell", "x", "y", "layer",
                    "direction", "transitions", "cost", "heuristic", "serial", "key")
    tuple_sha = hashlib.sha256("\n".join(
        ",".join(str(node[field]) for field in tuple_fields)
        for node in path_nodes).encode("utf-8")).hexdigest()
    planar_runs = [
        {"layer": 0, "firstNodeIndex": 0, "lastNodeIndex": 1,
         "xPoints": [0, 10], "yPoints": [0, 0]},
        {"layer": 1, "firstNodeIndex": 2, "lastNodeIndex": 3,
         "xPoints": [10, 20], "yPoints": [0, 0]},
        {"layer": 0, "firstNodeIndex": 4, "lastNodeIndex": 5,
         "xPoints": [20, 20], "yPoints": [0, 10]},
        {"layer": 1, "firstNodeIndex": 6, "lastNodeIndex": 7,
         "xPoints": [20, 30], "yPoints": [10, 10]},
        {"layer": 0, "firstNodeIndex": 8, "lastNodeIndex": 10,
         "xPoints": [30, 30], "yPoints": [10, 30]},
    ]
    face_bits = "0" * 15 + "3"
    source = {
        "pass": 1, "netId": "LOAD12", "netIndexNextFetch": 2,
        "netPosition": 1, "netOrder": list(net_order), "branchOrdinal": 73,
        "policy": "FULLER_TWO_LAYER", "templateHoleCount": 0,
        "startPadAttachment": "SURFACE_PAD", "startCopperFaceMask": 1,
        "rootCopperFaceMask": 3,
        "startPadId": "START.PAD", "rootPadId": "ROOT.PAD",
        "startX": 0, "startY": 0, "rootX": 30, "rootY": 30,
        "reachedPadIds": ["ROOT.PAD", "PREV.PTH"],
        "reachedPthPadIds": ["PREV.PTH"],
        "grid": {
            "minX": 0, "minY": 0, "width": 4, "height": 4,
            "cells": 16, "primaryLayer": "TOP", "maximumVias": 48,
            "maximumTransitions": 4,
        },
    }
    seed_nodes = [
        {"heapIndex": 0, "cell": 0, "x": 0, "y": 0, "layer": 0,
         "direction": 4, "transitions": 0, "cost": 0, "heuristic": 60,
         "serial": 41, "key": 64},
        {"heapIndex": 1, "cell": 0, "x": 0, "y": 0, "layer": 1,
         "direction": 4, "transitions": 0, "cost": 2, "heuristic": 60,
         "serial": 42, "key": 144},
    ]
    pre = {
        "schema": "p07-accepted-branch-pre-r1", "source": source,
        "seeds": {"schema": "p07-accepted-branch-seeds-r1", "queueSize": 2,
                  "faceMask": 3, "nodes": seed_nodes},
        "prefixInventory": prefix_inventory,
        "prefixTraces": {"count": 1, "rows": [prefix_trace]},
        "path": {
            "nodeCount": 11, "gridSteps": 6, "length": 60, "bends": 0,
            "transitionCount": 4, "directDistance": 60,
            "tupleSha256": tuple_sha, "nodes": path_nodes,
            "planarRuns": planar_runs,
        },
        "goal": {
            "cellCount": 1, "topCount": 1, "bottomCount": 1,
            "bothCount": 1, "faceBits": face_bits,
            "faceBitsSha256": hashlib.sha256(face_bits.encode("ascii")).hexdigest(),
            "foundGoal": True,
        },
        "nativePublisher": {
            "stage": 1, "qualityIndex": 11, "nodeCount": 11,
            "length": 60, "bends": 0, "directDistance": 60,
            "endCell": 15, "endX": 30, "endY": 30, "endLayer": 0,
            "endTransitions": 4, "endTerminalPadId": "ROOT.PAD",
        },
        "work": {
            "advanceCalls": 1, "workSteps": 137, "prefixWorkSteps": 7,
            "seedEntries": 2, "pathNodes": 11, "goalCells": 16,
            "traceRows": 1, "serializedUtf16Units": 100,
            "activeMillis": 1,
        },
        "stability": {
            "liveBeforeVerified": True, "liveAfterVerified": True,
            "seedIdentityVerified": True, "pathChainVerified": True,
            "prefixLineageVerified": True, "goalMaskVerified": True,
            "traceIdentityVerified": True,
        },
    }
    pre["work"]["serializedUtf16Units"] = _fixture_serialized_json_units(
        pre,
        {("work", "advanceCalls"), ("work", "workSteps"),
         ("work", "serializedUtf16Units"), ("work", "activeMillis")},
        {("prefixInventory",)})
    pre_work_floor = (pre["work"]["prefixWorkSteps"] +
                      pre["work"]["seedEntries"] + pre["work"]["pathNodes"] +
                      pre["work"]["goalCells"] + pre["work"]["traceRows"] +
                      pre["work"]["serializedUtf16Units"])
    pre["work"]["workSteps"] = pre_work_floor
    pre["work"]["advanceCalls"] = max(1, (pre_work_floor + 32767) // 32768)
    created_specs = [
        ("p07-via/LOAD12/73/2", "LOAD12", 73, 2, 20, 0),
        ("p07-via/LOAD12/73/3", "LOAD12", 73, 3, 20, 10),
        ("p07-via/LOAD12/73/4", "LOAD12", 73, 4, 30, 10),
    ]
    post_rows = sorted(
        [old_hole] + [
            _literal_hole(index, spec[0], spec[1], 1,
                          spec[2], spec[3], spec[4], spec[5])
            for index, spec in enumerate(created_specs, 1)
        ], key=lambda row: row["id"].encode("utf-16-be"))
    for index, row in enumerate(post_rows):
        row["index"] = index
    created_ids = sorted((spec[0] for spec in created_specs),
                         key=lambda item: item.encode("utf-16-be"))
    created_traces = [
        _literal_trace(0 if index == 0 else index + 1,
                       "p07/LOAD12/73/" + str(index), "LOAD12",
                       "START.PAD" if index == 0 else None,
                       "ROOT.PAD" if index == 4 else None,
                       ("TOP", "BOTTOM")[run["layer"]], 10,
                       run["xPoints"], run["yPoints"])
        for index, run in enumerate(planar_runs)
    ]
    post = {
        "schema": "p07-accepted-branch-post-r1",
        "source": {
            "pass": 1, "netId": "LOAD12", "branchOrdinal": 73,
            "searchBranches": 73, "currentNet": "LOAD12",
            "searchBranchIsNull": True, "searchPublisherIsNull": True,
            "publisherStage": 6, "startPadId": "START.PAD",
            "reachedStartCount": 1, "preHoleCount": 1, "preTraceCount": 1,
        },
        "post": {
            "holeCount": 4, "createdHoleCount": 3,
            "createdTraceCount": 5, "holeRows": post_rows,
            "createdHoleIds": created_ids,
            "retainedHoleBindings": [{
                "id": old_hole["id"], "preFingerprint": old_hole["fingerprint"],
                "postFingerprint": old_hole["fingerprint"],
                "preIndex": 0, "postIndex": 0, "sameObject": True,
            }],
            "createdTraceRows": created_traces,
            "retainedTraceBindings": [{
                "sourceId": prefix_trace["sourceId"],
                "preFingerprint": prefix_trace["traceFingerprint"],
                "postFingerprint": prefix_trace["traceFingerprint"],
                "preIndex": 0, "postIndex": 1, "sameObject": True,
            }],
            "transitionBindings": [
                {"pathNodeIndex": 2, "fromLayer": 0, "toLayer": 1,
                 "cell": 1, "x": 10, "y": 0, "netId": "LOAD12",
                 "viaId": old_hole["id"],
                 "viaFingerprint": old_hole["fingerprint"],
                 "creatorNetPosition": 1, "creatorBranchOrdinal": 10,
                 "piece": 0, "createdInBranch": False},
                {"pathNodeIndex": 4, "fromLayer": 1, "toLayer": 0,
                 "cell": 2, "x": 20, "y": 0, "netId": "LOAD12",
                 "viaId": post_rows[1]["id"],
                 "viaFingerprint": post_rows[1]["fingerprint"],
                 "creatorNetPosition": 1, "creatorBranchOrdinal": 73,
                 "piece": 2, "createdInBranch": True},
                {"pathNodeIndex": 6, "fromLayer": 0, "toLayer": 1,
                 "cell": 6, "x": 20, "y": 10, "netId": "LOAD12",
                 "viaId": post_rows[2]["id"],
                 "viaFingerprint": post_rows[2]["fingerprint"],
                 "creatorNetPosition": 1, "creatorBranchOrdinal": 73,
                 "piece": 3, "createdInBranch": True},
                {"pathNodeIndex": 8, "fromLayer": 1, "toLayer": 0,
                 "cell": 7, "x": 30, "y": 10, "netId": "LOAD12",
                 "viaId": post_rows[3]["id"],
                 "viaFingerprint": post_rows[3]["fingerprint"],
                 "creatorNetPosition": 1, "creatorBranchOrdinal": 73,
                 "piece": 4, "createdInBranch": True},
            ],
        },
        "work": {
            "advanceCalls": 1, "workSteps": 120,
            "aggregateWorkSteps": 257, "holeRows": 4,
            "traceRows": 6, "transitionMatches": 4,
            "serializedUtf16Units": 100, "activeMillis": 1,
        },
        "stability": {
            "liveBeforeVerified": True, "liveAfterVerified": True,
            "searchIdentityVerified": True, "publisherCompleted": True,
            "reachedStartVerified": True, "holeIdentityVerified": True,
            "traceIdentityVerified": True, "transitionBindingsVerified": True,
            "traceRunsVerified": True,
        },
        "claims": {"avoidability": "UNKNOWN", "chronologyProof": False,
                   "routeAdoption": False, "layoutQualification": False,
                   "playable100": 0},
    }
    post["work"]["serializedUtf16Units"] = _fixture_serialized_json_units(
        post,
        {("work", "advanceCalls"), ("work", "workSteps"),
         ("work", "aggregateWorkSteps"),
         ("work", "serializedUtf16Units"), ("work", "activeMillis")})
    post_work_floor = (post["work"]["holeRows"] + post["work"]["traceRows"] +
                       post["work"]["transitionMatches"] +
                       post["work"]["serializedUtf16Units"])
    post["work"]["workSteps"] = post_work_floor
    post["work"]["advanceCalls"] = max(1, (post_work_floor + 32767) // 32768)
    post["work"]["aggregateWorkSteps"] = (
        pre["work"]["workSteps"] + post["work"]["workSteps"])
    claims = dict(post["claims"])
    event = {
        "protocolVersion": 1, "sourceRevision": 20, "runId": RUN_ID,
        "operationId": "QUALITY_PROBE",
        "scope": "ACTUAL_LOAD12_BRANCH_ENTRY_PATH_AND_PUBLICATION_ONLY",
        "rootSeed": "9007199254740993",
        "placementSeed": "-6587958367974952701",
        "routingSeed": "8445301674744508279",
        "routeRequestIndex": 0, "selectedPlacementIndex": 0,
        "selectedPlacementAttempt": 4, "status": "PASS",
        "claims": claims, "pre": pre, "post": post,
    }
    summary = {
        "protocolVersion": 1, "sourceRevision": 20, "status": "PASS",
        "runId": RUN_ID, "operationId": "QUALITY_PROBE", "events": 1,
        "scope": event["scope"], "rootSeed": event["rootSeed"],
        "placementSeed": event["placementSeed"],
        "routingSeed": event["routingSeed"], "routeRequestIndex": 0,
        "selectedPlacementIndex": 0, "selectedPlacementAttempt": 4,
        "pathNodes": 11, "transitions": 4, "createdHoles": 3,
        "claims": claims,
    }
    d_hole_specs = [
        ("p07-via/LOAD12/10/0", "LOAD12", 10, 0, 10, 0),
        *created_specs,
    ]
    scalar, _old_path, graph, allocation_event, _allocation_summary = \
        _allocation_fixture(net_order, d_hole_specs)
    scalar.update({
        "rootSeed": "9007199254740993",
        "placementSeed": "-6587958367974952701",
        "routingSeed": "8445301674744508279",
        "routeRequestIndex": 0, "selectedPlacementIndex": 0,
        "selectedPlacementAttempt": 4,
    })
    allocation_event["binding"]["currentBranchOrdinal"] = 74
    allocation = reader._validate_prefix_allocation_event(
        allocation_event, scalar, graph, "LOAD12")
    return scalar, event, summary, allocation


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
        analysis = reader._analyze_graph(width, height, pitch, start, end,
                                         end_layer, seeds, top, bottom, goals)
        expected_faces, expected_eligible = _oracle_face(
            width, height, pitch, start, end, end_layer,
            top, bottom, goals, seeds)
        self.assertEqual(analysis["target"], "D_SENSE")
        self.assertEqual(analysis["scope"], "FIXED_PREFIX_ZERO_VIA_LENGTH_ONLY")
        self.assertEqual(
            analysis["crossCheck"],
            "IDENTICAL_PER_FACE_DISTANCES_AND_GOAL_COUNTS")
        self.assertEqual(analysis["queueBfs"], analysis["heapDijkstra"])
        actual = analysis["queueBfs"]
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
        parsed = _validate_graph_fixture(event, scalar, path)
        self.assertEqual(parsed["analysis"]["queueBfs"]["seedLayerMask"], 3)
        self.assertEqual(
            parsed["analysis"]["queueBfs"]["faces"][1]["distanceCapturedEnd"], 10)
        self.assertIs(parsed["legalRouteProof"], False)
        self.assertIs(parsed["routeAdoption"], False)

    def test_load12_graph_binds_multiface_path_and_independent_face_algorithms(self):
        scalar, event, pre = _load12_graph_fixture()
        parsed = reader._validate_graph_event(
            event, scalar, {}, "LOAD12", load12_pre=pre)
        self.assertEqual(parsed["target"], "LOAD12")
        self.assertEqual(parsed["binding"]["branchOrdinal"], 73)
        self.assertEqual(parsed["binding"]["netPosition"], 3)
        self.assertEqual(parsed["binding"]["netIndexNextFetch"], 4)
        self.assertEqual(parsed["diagnostic"]["path"]["planarEdgeCount"], 2)
        self.assertEqual(parsed["diagnostic"]["path"]["transitionCount"], 1)
        self.assertEqual(parsed["analysis"]["queueBfs"],
                         parsed["analysis"]["heapDijkstra"])
        self.assertEqual(parsed["analysis"]["queueBfs"]["faces"], [
            {"layer": 0, "reachableGoals": 0, "shortestAnyGoal": None,
             "distanceCapturedEnd": None, "lengthEligibleGoals": 0,
             "minimumEligibleLength": None},
            {"layer": 1, "reachableGoals": 1, "shortestAnyGoal": 20,
             "distanceCapturedEnd": 20, "lengthEligibleGoals": 1,
             "minimumEligibleLength": 20},
        ])
        self.assertIs(parsed["legalRouteProof"], False)
        self.assertIs(parsed["routeAdoption"], False)

    def test_load12_source_binding_rejects_wrong_target_source_and_late_release(self):
        scalar, event, pre = _load12_graph_fixture()
        event["target"] = "D_SENSE"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, {}, "LOAD12", pre)
        self.assertEqual(caught.exception.code, "GRAPH_TARGET_SPEC_MISMATCH")

        scalar, event, pre = _load12_graph_fixture()
        event["binding"]["sourceKind"] = "D_SENSE_PUBLISHER_PATH"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, {}, "LOAD12", pre)
        self.assertEqual(caught.exception.code, "GRAPH_BINDING_IDENTITY_INVALID")

        scalar, event, pre = _load12_graph_fixture()
        event["binding"]["sourceReleasedBeforeSuccessorWork"] = False
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, {}, "LOAD12", pre)
        self.assertEqual(caught.exception.code, "GRAPH_BINDING_IDENTITY_INVALID")

        scalar, event, pre = _load12_graph_fixture()
        pre["source"]["grid"]["width"] = 3
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, {}, "LOAD12", pre)
        self.assertEqual(caught.exception.code,
                         "GRAPH_GRID_DOMAIN_OR_SCALAR_BINDING_INVALID")

    def test_load12_planar_edges_use_each_face_and_flips_are_not_graph_edges(self):
        scalar, event, pre = _load12_graph_fixture()
        event["diagnostic"]["masks"]["topStepsHex"] = "0000"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, {}, "LOAD12", pre)
        self.assertEqual(caught.exception.code, "GRAPH_ORIGINAL_PATH_EDGE_MISSING")

        scalar, event, pre = _load12_graph_fixture()
        event["diagnostic"]["masks"]["bottomStepsHex"] = "0000"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, {}, "LOAD12", pre)
        self.assertEqual(caught.exception.code, "GRAPH_ORIGINAL_PATH_EDGE_MISSING")

        scalar, event, pre = _load12_graph_fixture()
        pre["path"]["nodes"][2]["cell"] = 2
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_event(event, scalar, {}, "LOAD12", pre)
        self.assertEqual(caught.exception.code,
                         "GRAPH_ORIGINAL_PATH_FACE_FLIP_INVALID")

    def test_graph_serialized_length_counts_fixed_counters_and_java_escapes(self):
        self.assertEqual(reader._graph_json_string_utf16_units("A\U0001F527\x7f"),
                         2 + 1 + 12 + 6)
        scalar, path, event = _event_fixture()
        event["diagnostic"]["work"]["serializedUtf16Units"] -= 1
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            _validate_graph_fixture(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_WORK_CONSERVATION_INVALID")

    def test_load12_summary_repeats_target_scope_and_release_claim_exactly(self):
        scalar, event, pre = _load12_graph_fixture()
        graph = reader._validate_graph_event(
            event, scalar, {}, "LOAD12", load12_pre=pre)
        summary = {
            "protocolVersion": 1, "sourceRevision": 20, "status": "PASS",
            "runId": RUN_ID, "operationId": "QUALITY_PROBE", "events": 1,
            "target": "LOAD12", "scope": event["scope"],
            "ordinaryQueries": event["diagnostic"]["work"]["ordinaryQueries"],
            "sourceReleasedBeforeSuccessorWork": True,
            "feasibility": "UNKNOWN", "legalRouteProof": False,
            "layoutQualification": False, "routeAdoption": False,
        }
        self.assertEqual(reader._validate_graph_summary(summary, event, graph), summary)
        summary["target"] = "D_SENSE"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_graph_summary(summary, event, graph)
        self.assertEqual(caught.exception.code,
                         "GRAPH_SUMMARY_IDENTITY_OR_SCOPE_INVALID")

    def test_closed_keys_and_revision_are_required(self):
        scalar, path, event = _event_fixture()
        event["unrecognized"] = True
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            _validate_graph_fixture(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_EVENT_KEYS_INVALID")
        scalar, path, event = _event_fixture()
        event["sourceRevision"] = 15
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            _validate_graph_fixture(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_EVENT_IDENTITY_OR_SCOPE_INVALID")

    def test_claim_flags_and_live_stability_are_false_or_verified_exactly(self):
        for field in ("legalRouteProof", "layoutQualification", "routeAdoption"):
            scalar, path, event = _event_fixture()
            event[field] = True
            with self.subTest(field=field):
                with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                    _validate_graph_fixture(event, scalar, path)
                self.assertEqual(caught.exception.code,
                                 "GRAPH_EVENT_IDENTITY_OR_SCOPE_INVALID")
        scalar, path, event = _event_fixture()
        event["diagnostic"]["stability"]["liveAfterVerified"] = False
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            _validate_graph_fixture(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_STABILITY_INVALID")

    def test_original_path_edges_must_be_present_in_the_correct_face(self):
        scalar, path, event = _event_fixture()
        event["diagnostic"]["masks"]["bottomStepsHex"] = "000000"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            _validate_graph_fixture(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_ORIGINAL_PATH_EDGE_MISSING")

    def test_query_work_and_goal_mask_conservation_are_bound(self):
        scalar, path, event = _event_fixture()
        event["diagnostic"]["work"]["ordinaryQueries"] -= 1
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            _validate_graph_fixture(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_WORK_CONSERVATION_INVALID")
        scalar, path, event = _event_fixture()
        event["binding"]["goalMaskLength"] -= 1
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            _validate_graph_fixture(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_BINDING_GRAPH_DOMAIN_INVALID")
        scalar, path, event = _event_fixture()
        event["binding"]["initialLayerMask"] = 1
        event["diagnostic"]["grid"]["initialLayerMask"] = 1
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            _validate_graph_fixture(event, scalar, path)
        self.assertEqual(caught.exception.code,
                         "GRAPH_GRID_DOMAIN_OR_SCALAR_BINDING_INVALID")
        scalar, path, event = _event_fixture()
        event["binding"]["prefixHoleCount"] = 49
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            _validate_graph_fixture(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_BINDING_GRAPH_DOMAIN_INVALID")

        scalar, path, event = _event_fixture()
        event["diagnostic"]["work"]["workSteps"] = reader.MAX_TOTAL_WORK + 1
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            _validate_graph_fixture(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_WORK_DOMAIN_INVALID")
        scalar, path, event = _event_fixture()
        event["diagnostic"]["work"]["copyEntries"] = reader.MAX_TOTAL_WORK
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            _validate_graph_fixture(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_WORK_CONSERVATION_INVALID")

    def test_mask_hex_is_canonical_lowercase_and_goal_faces_are_two_bits(self):
        scalar, path, event = _event_fixture()
        event["diagnostic"]["masks"]["topStepsHex"] = "A00000"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            _validate_graph_fixture(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_MASK_ENCODING_INVALID")
        scalar, path, event = _event_fixture()
        event["diagnostic"]["masks"]["goalFacesHex"] = "040000"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            _validate_graph_fixture(event, scalar, path)
        self.assertEqual(caught.exception.code, "GRAPH_GOAL_FACE_MASK_INVALID")

    def test_summary_must_match_event_and_actual_query_count(self):
        scalar, path, event = _event_fixture()
        graph = _validate_graph_fixture(event, scalar, path)
        summary = {
            "protocolVersion": 1, "sourceRevision": 20, "status": "PASS",
            "runId": RUN_ID, "operationId": "QUALITY_PROBE", "events": 1,
            "target": event["target"],
            "scope": event["scope"],
            "sourceReleasedBeforeSuccessorWork": True,
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


class Load12BranchWireTests(unittest.TestCase):
    def _validate(self):
        scalar, event, summary, allocation = _load12_branch_fixture()
        checked = reader._validate_load12_branch_event(event, scalar, allocation)
        return event, summary, allocation, checked

    def _assert_event_code(self, event, scalar, allocation, expected):
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_load12_branch_event(event, scalar, allocation)
        self.assertEqual(caught.exception.code, expected)

    def test_trace_compaction_matches_unequal_same_direction_segments(self):
        trace = {
            "xPoints": [0, 10, 30, 45, 40, 40, 40, 50],
            "yPoints": [0, 0, 0, 0, 0, 5, 20, 20],
        }
        self.assertEqual(
            reader._load12_compact_trace_points(trace),
            [(0, 0), (45, 0), (40, 0), (40, 20), (50, 20)],
        )

    def test_post_result_schema_is_required_and_versioned(self):
        scalar, event, _summary, allocation = _load12_branch_fixture()
        event["post"].pop("schema")
        self._assert_event_code(
            event, scalar, allocation, "LOAD12_POST_KEYS_INVALID")

        scalar, event, _summary, allocation = _load12_branch_fixture()
        event["post"]["schema"] = "p07-accepted-branch-post-r0"
        self._assert_event_code(
            event, scalar, allocation, "LOAD12_POST_SCHEMA_INVALID")
    def test_literal_four_flip_path_metrics_seed_and_created_delta(self):
        event, summary, _allocation, checked = self._validate()
        pre = event["pre"]
        path = pre["path"]
        post = event["post"]["post"]
        self.assertEqual(
            [node["key"] for node in path["nodes"]],
            [64, 17, 305, 258, 386, 358, 630, 583, 711, 683, 687])
        self.assertEqual(
            [node["parentIndex"] for node in path["nodes"]],
            [-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9])
        self.assertEqual((path["nodeCount"], path["gridSteps"], path["length"],
                          path["bends"], path["transitionCount"],
                          path["directDistance"]), (11, 6, 60, 0, 4, 60))
        self.assertEqual(pre["seeds"]["faceMask"], 3)
        self.assertEqual(pre["source"]["startCopperFaceMask"], 1)
        self.assertEqual([node["serial"] for node in pre["seeds"]["nodes"]],
                         [41, 42])
        self.assertEqual(pre["goal"]["faceBits"], "0" * 15 + "3")
        self.assertEqual(pre["goal"]["cellCount"], 1)
        self.assertEqual(pre["goal"]["topCount"], 1)
        self.assertEqual(pre["goal"]["bottomCount"], 1)
        self.assertEqual(pre["goal"]["bothCount"], 1)
        self.assertEqual(len(post["transitionBindings"]), 4)
        self.assertEqual([row["index"] for row in post["createdTraceRows"]],
                         [0, 2, 3, 4, 5])
        self.assertEqual(post["retainedTraceBindings"][0]["postIndex"], 1)
        self.assertEqual(post["createdHoleCount"], 3)
        self.assertEqual(post["createdHoleIds"], [
            "p07-via/LOAD12/73/2", "p07-via/LOAD12/73/3",
            "p07-via/LOAD12/73/4",
        ])
        self.assertEqual(
            [row["fingerprint"] for row in post["holeRows"]], [
                "19:p07-via/LOAD12/10/0VIA6:LOAD1210,0,2,6,EXPOSED",
                "19:p07-via/LOAD12/73/2VIA6:LOAD1220,0,2,6,EXPOSED",
                "19:p07-via/LOAD12/73/3VIA6:LOAD1220,10,2,6,EXPOSED",
                "19:p07-via/LOAD12/73/4VIA6:LOAD1230,10,2,6,EXPOSED",
            ])
        self.assertEqual(checked["pre"]["path"]["tupleSha256"],
                         path["tupleSha256"])
        self.assertEqual(
            path["tupleSha256"],
            "073217a0eb6d91135fd69a6bc2a13eaf41847908d45e39a9c7991f48a631391e")
        self.assertEqual(
            reader._validate_load12_branch_summary(summary, event, checked), summary)

    def test_closed_header_claims_and_typed_summary_fail_closed(self):
        scalar, event, _summary, allocation = _load12_branch_fixture()
        event["extra"] = True
        self._assert_event_code(event, scalar, allocation, "LOAD12_EVENT_KEYS_INVALID")

        scalar, event, _summary, allocation = _load12_branch_fixture()
        event["routingSeed"] = "8445301674744508278"
        self._assert_event_code(
            event, scalar, allocation, "LOAD12_EVENT_IDENTITY_OR_SCOPE_INVALID")

        scalar, event, _summary, allocation = _load12_branch_fixture()
        event["claims"]["chronologyProof"] = True
        self._assert_event_code(event, scalar, allocation, "LOAD12_EVENT_CLAIMS_INVALID")

        event, summary, _allocation, checked = self._validate()
        summary["transitions"] = 3
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_load12_branch_summary(summary, event, checked)
        self.assertEqual(caught.exception.code,
                         "LOAD12_SUMMARY_IDENTITY_OR_SCOPE_INVALID")

        event, summary, _allocation, checked = self._validate()
        summary["claims"]["playable100"] = 1
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_load12_branch_summary(summary, event, checked)
        self.assertEqual(caught.exception.code, "LOAD12_SUMMARY_CLAIMS_INVALID")

    def test_path_parent_key_hash_goal_and_publisher_bindings_are_checked(self):
        scalar, event, _summary, allocation = _load12_branch_fixture()
        source = reader._validate_load12_source(event["pre"]["source"])
        unsupported_start_node = dict(event["pre"]["path"]["nodes"][0], layer=1)
        prefix_start_via = _literal_hole(
            0, "p07-via/LOAD12/68/9", "LOAD12", 1, 68, 9, 0, 0)
        reader._validate_load12_start_face(
            source, unsupported_start_node, [prefix_start_via])

        cases = (
            (lambda event: event["pre"]["source"].update(startX=1),
             "LOAD12_SOURCE_START_PAD_OFF_GRID"),
            (lambda event: event["pre"]["source"].update(rootY=31),
             "LOAD12_SOURCE_ROOT_PAD_OFF_GRID"),
            (lambda event: event["pre"]["source"].update(startX=10),
             "LOAD12_SEED_START_CELL_INCONSISTENT"),
            (lambda event: event["pre"]["path"]["nodes"][0].update(
                layer=1, cost=2, serial=42, key=144),
             "LOAD12_START_FACE_UNSUPPORTED"),
            (lambda event: event["pre"]["path"]["nodes"][3].update(parentIndex=3),
             "LOAD12_PATH_NODE_DOMAIN_INVALID"),
            (lambda event: event["pre"]["path"]["nodes"][2].update(key=306),
             "LOAD12_PATH_NODE_KEY_INVALID"),
            (lambda event: event["pre"]["path"]["nodes"][5].update(cell=5),
             "LOAD12_PATH_NODE_CELL_INVALID"),
            (lambda event: event["pre"]["path"].update(tupleSha256="0" * 64),
             "LOAD12_PATH_METRICS_OR_HASH_INVALID"),
            (lambda event: event["pre"]["path"].update(directDistance=30),
             "LOAD12_PATH_METRICS_OR_HASH_INVALID"),
            (lambda event: event["pre"]["goal"].update(faceBitsSha256="0" * 64),
             "LOAD12_GOAL_MASK_INVALID"),
            (lambda event: event["pre"]["nativePublisher"].update(endTransitions=3),
             "LOAD12_NATIVE_PUBLISHER_BINDING_INVALID"),
            (lambda event: event["pre"]["path"]["planarRuns"][0]["xPoints"].__setitem__(1, 11),
             "LOAD12_PLANAR_RUN_MISMATCH"),
        )
        for mutate, expected in cases:
            scalar, event, _summary, allocation = _load12_branch_fixture()
            mutate(event)
            with self.subTest(expected=expected):
                self._assert_event_code(event, scalar, allocation, expected)

    def test_source_order_prefix_owner_and_raw_fingerprint_fail_closed(self):
        cases = (
            (lambda event: event["pre"]["source"].update(netIndexNextFetch=1),
             "LOAD12_SOURCE_NET_POSITION_INVALID"),
            (lambda event: event["pre"]["source"].update(branchOrdinal=74),
             "LOAD12_SOURCE_IDENTITY_INVALID"),
            (lambda event: event["pre"]["prefixInventory"]["rows"][0].update(
                fingerprint="wrong"),
             "ALLOCATION_ROW_FINGERPRINT_INVALID"),
            (lambda event: event["pre"]["prefixInventory"]["totals"]["byNet"][1].update(
                holeCount=0),
             "ALLOCATION_NET_TOTALS_INVALID"),
            (lambda event: event["pre"]["prefixInventory"]["work"].update(
                workSteps=6),
             "LOAD12_PREFIX_WORK_CONSERVATION_INVALID"),
        )
        for mutate, expected in cases:
            scalar, event, _summary, allocation = _load12_branch_fixture()
            mutate(event)
            with self.subTest(expected=expected):
                self._assert_event_code(event, scalar, allocation, expected)

        for hole_id, creator_ordinal, expected in (
                ("p07-via/LOAD12/73/0", 73, "ALLOCATION_ROW_OWNER_INVALID"),
                ("p07-via/LOAD12/10/0/1", 10, "ALLOCATION_ROW_ID_INVALID")):
            scalar, event, _summary, allocation = _load12_branch_fixture()
            row = event["pre"]["prefixInventory"]["rows"][0]
            row["id"] = hole_id
            row["creatorBranchOrdinal"] = creator_ordinal
            _refresh_literal_fingerprint(row)
            with self.subTest(hole_id=hole_id):
                self._assert_event_code(event, scalar, allocation, expected)

    def test_post_delta_retention_current_owner_and_transition_bindings_fail(self):
        cases = (
            (lambda event: event["post"]["post"]["createdHoleIds"].pop(),
             "LOAD12_CREATED_HOLE_DELTA_INVALID"),
            (lambda event: event["post"]["post"]["retainedHoleBindings"][0].update(
                postIndex=1),
             "LOAD12_RETAINED_HOLE_BINDING_INVALID"),
            (lambda event: event["post"]["post"]["transitionBindings"][0].update(
                createdInBranch=True),
             "LOAD12_TRANSITION_BINDING_INVALID"),
            (lambda event: event["post"]["post"]["transitionBindings"][1].update(
                x=21),
             "LOAD12_TRANSITION_BINDING_INVALID"),
            (lambda event: event["post"]["post"]["transitionBindings"][2].update(
                viaFingerprint="wrong"),
             "LOAD12_TRANSITION_BINDING_INVALID"),
        )
        for mutate, expected in cases:
            scalar, event, _summary, allocation = _load12_branch_fixture()
            mutate(event)
            with self.subTest(expected=expected):
                self._assert_event_code(event, scalar, allocation, expected)

        scalar, event, _summary, allocation = _load12_branch_fixture()
        row = event["post"]["post"]["holeRows"][1]
        row["id"] = "p07-via/LOAD12/74/2"
        row["creatorBranchOrdinal"] = 74
        _refresh_literal_fingerprint(row)
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_load12_branch_event(event, scalar, allocation)
        self.assertEqual(caught.exception.code, "ALLOCATION_ROW_OWNER_INVALID")

    def test_created_trace_runs_fingerprints_and_width_bind_to_prior_same_net(self):
        scalar, event, _summary, allocation = _load12_branch_fixture()
        event["post"]["post"]["createdTraceRows"][0]["index"] = 1
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_load12_branch_event(event, scalar, allocation)
        self.assertEqual(caught.exception.code,
                         "LOAD12_POST_TRACE_INDEX_PARTITION_INVALID")

        scalar, event, _summary, allocation = _load12_branch_fixture()
        trace = event["post"]["post"]["createdTraceRows"][0]
        trace.update(_literal_trace(
            trace["index"], trace["sourceId"], trace["netId"],
            trace["startPadId"], trace["endPadId"], trace["layer"], 11,
            trace["xPoints"], trace["yPoints"]))
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_load12_branch_event(event, scalar, allocation)
        self.assertEqual(caught.exception.code,
                         "LOAD12_CREATED_TRACE_WIDTH_NOT_BOUND_TO_PREFIX")

        scalar, event, _summary, allocation = _load12_branch_fixture()
        trace = event["post"]["post"]["createdTraceRows"][0]
        trace.update(_literal_trace(
            trace["index"], trace["sourceId"], trace["netId"],
            trace["startPadId"], trace["endPadId"], trace["layer"],
            trace["width"], [0, 20], trace["yPoints"]))
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_load12_branch_event(event, scalar, allocation)
        self.assertEqual(caught.exception.code,
                         "LOAD12_CREATED_TRACE_PATH_BINDING_INVALID")

        scalar, event, _summary, allocation = _load12_branch_fixture()
        event["post"]["claims"]["routeAdoption"] = True
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_load12_branch_event(event, scalar, allocation)
        self.assertEqual(caught.exception.code, "LOAD12_POST_CLAIMS_INVALID")

    def test_serialized_utf16_units_bind_to_native_and_legacy_json_encodings(self):
        event, _summary, _allocation, checked = self._validate()
        self.assertIn("😀", event["pre"]["source"]["netOrder"][0])
        self.assertIn(
            "😀", event["pre"]["prefixInventory"]["totals"]["byNet"][0]["netId"])
        self.assertEqual(
            checked["pre"]["pre"]["work"]["serializedUtf16Units"],
            _fixture_serialized_json_units(
                event["pre"],
                {("work", "advanceCalls"), ("work", "workSteps"),
                 ("work", "serializedUtf16Units"), ("work", "activeMillis")},
                {("prefixInventory",)}))
        self.assertEqual(
            checked["post"]["post"]["work"]["serializedUtf16Units"],
            _fixture_serialized_json_units(
                event["post"],
                {("work", "advanceCalls"), ("work", "workSteps"),
                 ("work", "aggregateWorkSteps"),
                 ("work", "serializedUtf16Units"), ("work", "activeMillis")}))

        for section, expected_code in (
                ("pre", "LOAD12_PRE_SERIALIZED_UTF16_UNITS_INVALID"),
                ("post", "LOAD12_POST_SERIALIZED_UTF16_UNITS_INVALID")):
            for undercount in (False, True):
                scalar, mutated, _summary, allocation = _load12_branch_fixture()
                count = mutated[section]["work"]["serializedUtf16Units"]
                mutated[section]["work"]["serializedUtf16Units"] = (
                    count - 1 if undercount else 0)
                with self.subTest(section=section, undercount=undercount):
                    self._assert_event_code(
                        mutated, scalar, allocation, expected_code)

    def test_nested_work_and_later_D_sense_rows_only_corroborate_ids_and_fp(self):
        scalar, event, _summary, allocation = _load12_branch_fixture()
        event["post"]["work"]["aggregateWorkSteps"] += 1
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_load12_branch_event(event, scalar, allocation)
        self.assertEqual(caught.exception.code,
                         "LOAD12_POST_WORK_CONSERVATION_INVALID")

        scalar, event, _summary, allocation = _load12_branch_fixture()
        allocation["diagnostic"]["rows"][0]["fingerprint"] = "changed"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_load12_branch_event(event, scalar, allocation)
        self.assertEqual(caught.exception.code,
                         "LOAD12_LATER_ALLOCATION_CORROBORATION_INVALID")

        scalar, event, _summary, allocation = _load12_branch_fixture()
        allocation["binding"]["netOrder"] = ["OTHER", "LOAD12", "D_SENSE"]
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_load12_branch_event(event, scalar, allocation)
        self.assertEqual(caught.exception.code,
                         "LOAD12_LATER_ALLOCATION_NET_ORDER_MISMATCH")


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

    def test_receipt_is_the_closed_sixteen_record_v5_sequence(self):
        expected = (
            "RB100_QUALITY_SCALAR_CONTROLS",
            "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS",
            "RB100_QUALITY_SCALAR_OPERATION",
            "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS",
            "RB100_QUALITY_SCALAR_OPERATION",
            "RB100_QUALITY_SCALAR_EVENT",
            "RB100_QUALITY_SCALAR_PAIR",
            "PASS: RB100 quality scalar contracts",
            "RB100_QUALITY_PATH_EVENT",
            "PASS: RB100 quality path contracts",
            "RB100_ZERO_VIA_GRAPH_EVENT",
            "PASS: RB100 quality zero-via graph contracts",
            "RB100_PREFIX_ALLOCATION_EVENT",
            "PASS: RB100 prefix allocation contracts",
            "RB100_LOAD12_BRANCH_EVENT",
            "PASS: RB100 LOAD12 branch contracts",
        )
        self.assertEqual(reader.RECEIPT_MARKERS, expected)
        receipt = self._receipt()
        self.assertEqual(len(receipt), 16)
        self.assertTrue(receipt[-2].startswith("RB100_LOAD12_BRANCH_EVENT "))
        self.assertTrue(receipt[-1].startswith(
            "PASS: RB100 LOAD12 branch contracts "))

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
    def test_coordinated_v5_schema_and_native_census_are_pinned(self):
        self.assertEqual(reader.INPUT_SCHEMA, "p07-quality-zero-via-input-v5")
        self.assertEqual(reader.RUN_SCHEMA, "q100-quality-zero-via-native-run-v5")
        self.assertEqual(reader.RESULT_SCHEMA,
                         "q100-quality-zero-via-reader-result-v5")
        self.assertEqual(reader.SOURCE_REVISION, 20)
        self.assertEqual(reader.SOURCE_INPUT_COUNT, 1077)
        self.assertEqual(reader.CONTRACT_JAVA_COUNT, 221)
        self.assertEqual(len(reader.QUALITY_REQUIRED_INPUTS), 13)
        self.assertEqual(reader.SUITES, {
            "D_SENSE": "Rb100QualityZeroViaGraphContractTest",
            "LOAD12": "Rb100Load12ZeroViaGraphContractTest",
        })
        self.assertIn("tests/contracts/Rb100Load12ZeroViaGraphContractTest.java",
                      reader.QUALITY_REQUIRED_INPUTS)
        self.assertIn("tests/contracts/P07AcceptedBranchCaptureDiagnostics.java",
                      reader.QUALITY_REQUIRED_INPUTS)
        self.assertIn("tests/contracts/P07AcceptedBranchCaptureContractTest.java",
                      reader.QUALITY_REQUIRED_INPUTS)

    def test_source_schema_and_revision_are_rejected_before_source_reads(self):
        with tempfile.TemporaryDirectory(prefix="q100-zero-via-source-") as temporary:
            base = Path(temporary)
            spec_path = base / "input-spec.json"
            for schema, revision in (
                    ("p07-quality-zero-via-input-v1", 16),
                    ("p07-quality-zero-via-input-v2", 17),
                    ("p07-quality-zero-via-input-v3", 18),
                    ("p07-quality-zero-via-input-v4", 19),
                    (reader.INPUT_SCHEMA, 19),
                    (reader.INPUT_SCHEMA, 17)):
                spec = {
                    "schema": schema,
                    "sourceRoot": str(base / "missing-source"),
                    "pinsPath": str(base / "missing-pins.json"),
                    "pinsSha256": "a" * 64,
                    "inputCount": 1077, "inputBytes": 100,
                    "sourceSnapshotRevision": revision,
                    "resultPath": str(base / "missing-result.json"),
                    "stdoutPath": str(base / "missing-stdout.txt"),
                    "receiptPath": str(base / "missing-receipt.txt"),
                    "stderrPath": str(base / "missing-stderr.txt"),
                    "target": "D_SENSE",
                    "suite": reader.SUITES["D_SENSE"],
                }
                raw = json.dumps(spec, separators=(",", ":"), sort_keys=True).encode()
                spec_path.write_bytes(raw)
                with self.subTest(schema=schema, revision=revision):
                    with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                        reader.run_validation(
                            str(spec_path), hashlib.sha256(raw).hexdigest())
                    self.assertEqual(caught.exception.code,
                                     "INPUT_SPEC_IDENTITY_INVALID")

    def test_input_spec_target_and_suite_are_a_closed_pair(self):
        base = {
            "schema": reader.INPUT_SCHEMA,
            "sourceRoot": "unused", "pinsPath": "unused",
            "pinsSha256": "a" * 64, "inputCount": reader.SOURCE_INPUT_COUNT,
            "inputBytes": 100, "sourceSnapshotRevision": reader.SOURCE_REVISION,
            "resultPath": "unused", "stdoutPath": "unused",
            "receiptPath": "unused", "stderrPath": "unused",
            "target": "D_SENSE", "suite": reader.SUITES["D_SENSE"],
        }
        target, suite, legacy = reader._validate_target_spec(base)
        self.assertEqual((target, suite),
                         ("D_SENSE", "Rb100QualityZeroViaGraphContractTest"))
        self.assertNotIn("target", legacy)
        self.assertNotIn("suite", legacy)

        for target, suite in (
                ("LOAD12", reader.SUITES["D_SENSE"]),
                ("UNKNOWN", reader.SUITES["D_SENSE"]),
                ("D_SENSE", reader.SUITES["LOAD12"])):
            spec = dict(base, target=target, suite=suite)
            with self.subTest(target=target, suite=suite):
                with self.assertRaises(reader.QualityZeroViaInputError) as caught:
                    reader._validate_target_spec(spec)
                self.assertEqual(caught.exception.code,
                                 "INPUT_SPEC_IDENTITY_INVALID")

    def test_native_run_target_is_required_and_matches_input_spec(self):
        legacy = {key: None for key in ("schema", "suite", "command")}
        run = dict(legacy, target="LOAD12")
        self.assertEqual(reader._validate_native_run_target(
            run, "LOAD12", legacy.keys()), legacy)
        run["target"] = "D_SENSE"
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_native_run_target(run, "LOAD12", legacy.keys())
        self.assertEqual(caught.exception.code, "NATIVE_RUN_TARGET_INVALID")
        del run["target"]
        with self.assertRaises(reader.QualityZeroViaInputError) as caught:
            reader._validate_native_run_target(run, "LOAD12", legacy.keys())
        self.assertEqual(caught.exception.code, "NATIVE_RUN_KEYS_INVALID")

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
