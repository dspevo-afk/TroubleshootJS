"""Independent geometry, wire, isolation, and source-binding reader tests."""
from __future__ import annotations

import hashlib
import io
import json
import shutil
import sys
import tempfile
import types
import unittest
from contextlib import redirect_stdout
from pathlib import Path

import read_quality_path as reader


RUN_ID = "d1c10000-0000-4000-8000-000000000001"
ELIGIBLE = [4, 2, 3, 0, 1]
ROUTES = [
    "P07_FULLER_TWO_LAYER@4=REJECTED:P07_FULLER_TWO_LAYER:A_COIL_LOW:NO_PATH",
    "P07_FULLER_TWO_LAYER@2=REJECTED:P07_FULLER_TWO_LAYER:TOTAL_SEARCH_LIMIT",
    "P07_FULLER_TWO_LAYER@3=REJECTED:P07_FULLER_TWO_LAYER:TOTAL_SEARCH_LIMIT",
]
RANKED = [4, 3, 0, 1, 2, 5]


def _canonical_stats(stats):
    vector = lambda values: "[" + ", ".join(str(value) for value in values) + "]"
    selected = "null" if stats["selectedRoutePolicy"] is None else stats["selectedRoutePolicy"]
    value = (
        stats["policyIdentity"] + ";outcome=" + stats["outcome"] +
        ";selected=" + selected +
        ";placements=" + str(stats["placementCandidates"]) +
        ";placementRejects=" + str(stats["placementRejections"]) +
        ";placementEvaluations=" + str(stats["placementEvaluations"]) +
        ";routes=" + str(stats["routeAttempts"]) +
        ";routingWork=" + str(stats["routingOrders"]) + "/" +
        str(stats["routingExpansions"]) +
        ";p05=" + str(stats["oneFaceAttempts"]) + "/" + str(stats["oneFaceSuccesses"]) +
        ";p07=" + str(stats["twoLayerAttempts"]) + "/" + str(stats["twoLayerSuccesses"]) +
        ";selectedAttempt=" + str(stats["selectedPlacementAttempt"]) +
        ";ranked=" + vector(stats["rankedPlacementAttempts"]) +
        ";placementScores=" + vector(stats["placementScores"]) +
        ";selectedScore=" + str(stats["selectedPlacementScore"]) +
        ";selectedQualityMilli=" + str(stats["selectedRouteQualityMilli"]) +
        ";routeOutcomes=" + vector(stats["routeOutcomes"]) +
        ";routeEligiblePlacementAttempts=" + vector(stats["routeEligiblePlacementAttempts"]) +
        ";routeEligibilitySkips=" + vector(stats["routeEligibilitySkips"])
    )
    if stats["orderingSearchReceipts"]:
        rows = []
        for row in stats["orderingSearchReceipts"]:
            rows.append(
                "attempt={attempt};startedOrderings={startedOrderings};"
                "completedOrderings={completedOrderings};yieldedOrderings={yieldedOrderings};"
                "finalizedOrderings={finalizedOrderings};selectedOrdering={selectedOrdering};"
                "expansions={expansions};guidanceStarted={guidanceStarted};"
                "comparisonComplete={comparisonComplete};termination={termination}"
                .format(
                    attempt=row["attempt"],
                    startedOrderings=row["startedOrderings"],
                    completedOrderings=row["completedOrderings"],
                    yieldedOrderings=row["yieldedOrderings"],
                    finalizedOrderings=row["finalizedOrderings"],
                    selectedOrdering=row["selectedOrdering"],
                    expansions=row["expansions"],
                    guidanceStarted=str(row["guidanceStarted"]).lower(),
                    comparisonComplete=str(row["comparisonComplete"]).lower(),
                    termination=row["termination"],
                ))
        value += ";orderingSearchReceipts=[" + ",".join(rows) + "]"
    return value


def _java_hash(value):
    result = 0
    raw = value.encode("utf-16-be")
    for offset in range(0, len(raw), 2):
        result = ((31 * result) + (raw[offset] << 8 | raw[offset + 1])) & 0xFFFFFFFF
    return format(result, "x")


def _statistics():
    stats = {
        "policyIdentity": "MEDIUM_BOARD@1", "outcome": "REJECTED",
        "selectedRoutePolicy": None,
        "placementCandidates": 6, "placementRejections": 0,
        "placementEvaluations": 102_828, "routeAttempts": 3,
        "oneFaceAttempts": 0, "oneFaceSuccesses": 0,
        "twoLayerAttempts": 3, "twoLayerSuccesses": 0,
        "selectedPlacementAttempt": -1, "rankedPlacementCount": 6,
        "rankedPlacementAttempts": RANKED.copy(), "placementScoreCount": 6,
        "placementScores": ["4:100", "3:90", "0:80", "1:70", "2:60", "5:50"],
        "routeOutcomesCount": 3, "routeOutcomes": ROUTES.copy(),
        "orderingSearchReceiptsCount": 3,
        "orderingSearchReceipts": [
            {"attempt": 4, "startedOrderings": 3, "completedOrderings": 3,
             "yieldedOrderings": 0, "finalizedOrderings": 0, "selectedOrdering": -1,
             "expansions": 918_222, "guidanceStarted": False,
             "comparisonComplete": True, "termination": "A_COIL_LOW:NO_PATH"},
            {"attempt": 2, "startedOrderings": 3, "completedOrderings": 3,
             "yieldedOrderings": 0, "finalizedOrderings": 0, "selectedOrdering": -1,
             "expansions": 1_000_000, "guidanceStarted": False,
             "comparisonComplete": False, "termination": "TOTAL_SEARCH_LIMIT"},
            {"attempt": 3, "startedOrderings": 3, "completedOrderings": 3,
             "yieldedOrderings": 0, "finalizedOrderings": 0, "selectedOrdering": -1,
             "expansions": 1_000_000, "guidanceStarted": False,
             "comparisonComplete": False, "termination": "TOTAL_SEARCH_LIMIT"},
        ],
        "routeEligiblePlacementAttemptCount": 5,
        "routeEligiblePlacementAttempts": ELIGIBLE.copy(),
        "routeEligibilitySkipCount": 1,
        "routeEligibilitySkips": [
            "attempt=5;width=929;height=643;cells=597347;reason=GRID_LIMIT",
        ],
        "routingOrders": 9, "routingExpansions": 2_918_222,
        "selectedPlacementScore": -1, "selectedRouteQualityMilli": -1,
    }
    canonical = _canonical_stats(stats)
    stats["canonicalLength"] = len(canonical.encode("utf-16-le")) // 2
    stats["canonicalJavaHash"] = _java_hash(canonical)
    stats["canonical"] = canonical
    return stats


def _zero_layout():
    return {
        "layoutReturned": False, "zeroOutput": True, "fingerprint": None,
        "outline": None, "rawSegments": 0, "topTraceCount": 0,
        "bottomTraceCount": 0, "totalTraceCount": 0, "holeCount": 0,
        "viaCount": 0, "platedHoleCount": 0, "nonPlatedHoleCount": 0,
    }


def _limits():
    return {
        "placementCandidates": 6, "routeCandidates": 3, "p07Expansions": 1_000_000,
        "p07BranchExpansions": 100_000, "p07GridCells": 250_000,
        "p07Orderings": 3, "p07Vias": 48, "outerPlacements": 80,
        "outerRouteAttempts": 800, "outerRouteExpansions": 160_000_000,
        "outerAccessCells": 700_000, "operationMs": 90_000,
        "workUnits": 640, "unitMs": 5_000, "cleanupMs": 120_000,
    }


def _operation_result(operation_ms):
    facts = {
        "protocolVersion": 2, "runId": RUN_ID, "fixture": "AUTHORED_RB100",
        "variant": reader.VARIANT, "layoutVersion": 26,
        "dependencyEpoch": reader.DEPENDENCY_EPOCH, "planVersion": 1,
        "modelVersion": 4, "physicalIntentVersion": 9,
        "physicalPolicyId": "MEDIUM_BOARD", "physicalPolicyVersion": 1,
        "placementDensityVersion": 14, "routingLayer": "BOTTOM",
        "traceWidthProfile": "ROLE_TRACE@1", "placementPackingVersion": 4,
        "requiresTwoLayerRouting": True, "seed": reader.ROOT_SEED,
        "placementSeed": reader.PLACEMENT_SEED, "routingSeed": reader.ROUTING_SEED,
        "components": 100, "pads": 246, "nets": 60,
        "boardIdentityStatus": "PASS", "solverGraphIdentityStatus": "PASS",
        "sessionTerminalStatus": "EXHAUSTED",
        "sessionException": {
            "type": "PcbRoutingRejectedException", "kind": "EXHAUSTED",
            "attemptIndex": 2, "attemptCount": 3, "seed": reader.ROUTING_SEED,
            "exhausted": True, "message": "final generator rejection",
        },
        "advanceCalls": 120, "observerChecks": 4, "limits": _limits(),
        "primaryRoutingOrders": 9, "primaryRoutingExpansions": 2_918_222,
        "policyFailure": "A_COIL_LOW:NO_PATH", "policyStatistics": _statistics(),
        "packingProbe": None, "layoutOutput": _zero_layout(),
        "layoutQualification": False,
    }
    return {
        "protocolVersion": 2, "runId": RUN_ID, "status": "PASS",
        "phase": "TERMINAL", "operationMs": operation_ms, "cleanupMs": 4,
        "cleanupStatus": "PASS", "workUnits": 120, "maxUnitMs": 5_000,
        "failurePhase": "", "failureType": "none", "failure": "",
        "cleanupFailureType": "none", "cleanupFailure": "",
        "scope": "OBSERVATIONAL_PRIVATE_P07_POLICY_ONLY",
        "layoutQualification": False, "reportFacts": facts,
    }


def _operation(operation_id, mode, operation_ms):
    return {
        "protocolVersion": 1, "sourceRevision": reader.SOURCE_REVISION,
        "runId": RUN_ID, "operationId": operation_id, "variant": reader.VARIANT,
        "mode": mode, "result": _operation_result(operation_ms),
    }


def _controls():
    rows = []
    for name, length, direct, bends, nodes, transitions, stage in (
            ("direct_zero_rejected", 0, 0, 0, 1, 0, 1),
            ("length_over_3x_rejected", 80, 20, 2, 9, 0, 1),
            ("bends_over_16_rejected", 180, 180, 17, 19, 0, 1),
            ("exact_3x_16_accepted", 270, 90, 16, 28, 0, 2),
            ("via_direction_reset", 30, 30, 1, 5, 1, 2)):
        rows.append({
            "name": name, "status": "PASS", "planarLength": length,
            "directDistance": direct, "bends": bends, "pathNodeCount": nodes,
            "transitionCount": transitions, "publisherStage": stage,
        })
    return {
        "protocolVersion": 1, "sourceRevision": reader.SOURCE_REVISION,
        "runId": RUN_ID, "status": "PASS",
        "fixture": "SYNTHETIC_PUBLISHER_METRICS_ONLY", "checks": rows,
        "assertions": 18, "layoutQualification": False,
    }


def _subset():
    return {
        "protocol": "rb100-physical-subsets-v1", "runId": RUN_ID,
        "variant": reader.VARIANT, "status": "NOT_RUN_NO_LAYOUT",
        "independentTwoLayerAccess": False, "layoutQualification": False,
    }


def _scalar_event():
    return {
        "protocolVersion": 1, "sourceRevision": reader.SOURCE_REVISION,
        "runId": RUN_ID, "operationId": reader.PROBE_OPERATION_ID,
        "variant": reader.VARIANT, "mode": "PROBE",
        "rootSeed": reader.ROOT_SEED, "placementSeed": reader.PLACEMENT_SEED,
        "routingSeed": reader.ROUTING_SEED,
        "layoutVersion": 26, "dependencyEpoch": reader.DEPENDENCY_EPOCH,
        "planVersion": 1, "modelVersion": 4, "physicalIntentVersion": 9,
        "routeRequestIndex": 0, "selectedPlacementIndex": 0,
        "selectedPlacementAttempt": 4, "eligiblePlacementAttempts": ELIGIBLE.copy(),
        "placementFingerprintLength": 252_676,
        "placementFingerprintSha256": "6553ad3df059c6bfe2980efe39ff951d3f6322fc0694b2c96e26508f8d3dfe2e",
        "pass": 1, "netId": "D_SENSE", "startPadId": "PAD_START",
        "rootPadId": "PAD_ROOT", "endPadId": None,
        "startX": 3540, "startY": 1790, "endX": 3570, "endY": 1970,
        "endLayer": 0, "primaryLayer": 1, "firstLayer": 0,
        "gridWidth": 478, "gridHeight": 309,
        "gridOriginX": 30, "gridOriginY": 30, "gridPitch": 10,
        "pathNodeCount": 66, "transitionCount": 0,
        "planarLength": 650, "directDistance": 210, "bends": 6,
        "rejectDirectZero": False, "rejectLengthOverThreeDirect": True,
        "rejectBendsOver16": False, "qualityRejected": True,
        "qualityRejectionOwner": "PcbLayerRoutingPrototype.Session#reason",
        "qualityRejectionMessage": "D_SENSE:BRANCH_QUALITY_LIMIT",
        "generatorLastFailureSource": "SeededPcbLayoutGenerator.Session#lastFailure",
        "generatorLastFailureType": "PcbRoutingRejectedException",
        "generatorLastFailureMessage": "final generator rejection is independently typed",
        "routeMillisStatus": "INCOMPLETE_MANUAL_SINGLE_STEP_CADENCE_NOT_REPRESENTATIVE",
        "manualDriverMillis": 3_000, "manualSliceSteps": 100,
        "acceptanceFlags": {"accepted": False, "routed": False, "playable": False},
        "feasibility": "UNKNOWN", "layoutQualification": False, "graphCallbacks": 0,
    }


def _pair():
    value = {
        "protocolVersion": 1, "sourceRevision": reader.SOURCE_REVISION,
        "runId": RUN_ID, "variant": reader.VARIANT,
        "nullOperationId": reader.NULL_OPERATION_ID,
        "probeOperationId": reader.PROBE_OPERATION_ID,
        "rootSeed": reader.ROOT_SEED, "placementSeed": reader.PLACEMENT_SEED,
        "routingSeed": reader.ROUTING_SEED, "layoutVersion": 26,
        "dependencyEpoch": reader.DEPENDENCY_EPOCH, "planVersion": 1,
        "modelVersion": 4, "physicalIntentVersion": 9, "status": "PASS",
        "routeEligiblePlacementAttemptsEqual": True,
        "firstP07RequestAttemptEqual": True, "routeOutcomesEqual": True,
        "primaryRoutingOrdersEqual": True, "primaryRoutingExpansionsEqual": True,
        "policyCanonicalEqual": True, "boardIdentityEqual": True,
        "solverIdentityEqual": True, "terminalStatusEqual": True,
        "layoutOutputEqual": True, "layoutAbsent": True,
        "routeMillisStatus": "INCOMPLETE_MANUAL_SINGLE_STEP_CADENCE_NOT_REPRESENTATIVE",
        "layoutQualification": False,
    }
    return value


def _scalar_summary():
    return {
        "protocolVersion": 1, "sourceRevision": reader.SOURCE_REVISION,
        "status": "PASS", "operations": 2, "nullOperations": 1,
        "probeOperations": 1, "pairs": 1, "confirmedScalarEvents": 1,
        "variant": reader.VARIANT, "runId": RUN_ID,
        "rootSeed": reader.ROOT_SEED, "placementSeed": reader.PLACEMENT_SEED,
        "routingSeed": reader.ROUTING_SEED, "layoutVersion": 26,
        "dependencyEpoch": reader.DEPENDENCY_EPOCH, "planVersion": 1,
        "modelVersion": 4, "physicalIntentVersion": 9,
        "assertions": 1_478_265, "suiteMs": 37_202, "suiteLimitMs": 480_000,
        "manualDriverMillis": 3_000, "manualSliceSteps": 100,
        "routeMillisStatus": "INCOMPLETE_MANUAL_SINGLE_STEP_CADENCE_NOT_REPRESENTATIVE",
        "feasibility": "UNKNOWN", "layoutQualification": False,
        "scope": "OBSERVATIONAL_ACTUAL_GENERATOR_QUALITY_SCALAR_ONLY",
    }


def _path_nodes():
    width, height = 478, 309
    x, y = 351, 176
    nodes = []
    directions = {"E": (1, 0, 1), "S": (0, 1, 2),
                  "W": (-1, 0, 3), "N": (0, -1, 0)}
    segments = (("E", 5), ("S", 8), ("W", 6), ("N", 10),
                ("E", 10), ("S", 20), ("W", 6))
    direction = 4
    # TOP is the secondary face for this fixture; Fuller initializes its source
    # Node at the two-cost secondary-layer surcharge before 65 planar steps.
    cost = reader.SECONDARY_LAYER_COST
    serial = 0

    def append(parent_index):
        cell = y * width + x
        physical_x = 30 + x * 10
        physical_y = 30 + y * 10
        key = (((0 * 2 + 0) * 5 + direction) * (width * height)) + cell
        nodes.append({
            "index": len(nodes), "parentIndex": parent_index,
            "cell": cell, "x": physical_x, "y": physical_y, "layer": 0,
            "direction": direction, "transitions": 0,
            "cost": cost, "heuristic": 0, "serial": serial, "key": key,
        })

    append(-1)
    for heading, steps in segments:
        dx, dy, next_direction = directions[heading]
        for _ in range(steps):
            previous_direction = direction
            x += dx
            y += dy
            direction = next_direction
            cost += 12 + (35 if previous_direction not in (4, direction) else 0)
            serial += 1
            append(len(nodes) - 1)
    return nodes


def _path_event():
    return {
        "protocolVersion": 1, "sourceRevision": reader.SOURCE_REVISION,
        "runId": RUN_ID, "operationId": reader.PROBE_OPERATION_ID,
        "order": "START_TO_END", "captureStage": 1,
        "captureQualityIndex": 66, "parentIdentityVerified": True,
        "nodeIdentityUnique": True, "nodes": _path_nodes(),
        "scope": "ACTUAL_PUBLISHER_PATH_ONLY", "feasibility": "UNKNOWN",
        "legalShortcutProof": False, "layoutQualification": False,
    }


def _path_summary():
    return {
        "protocolVersion": 1, "sourceRevision": reader.SOURCE_REVISION,
        "status": "PASS", "runId": RUN_ID,
        "operationId": reader.PROBE_OPERATION_ID, "events": 1, "nodes": 66,
        "scope": "ACTUAL_PUBLISHER_PATH_ONLY", "feasibility": "UNKNOWN",
        "legalShortcutProof": False, "layoutQualification": False,
    }


def _independent_shortcut_family(nodes, scalar_event):
    width = scalar_event["gridWidth"]
    pitch = scalar_event["gridPitch"]
    origin_x, origin_y = scalar_event["gridOriginX"], scalar_event["gridOriginY"]
    path_length = scalar_event["planarLength"]
    direct = scalar_event["directDistance"]
    minimum_saving = max(1, path_length - 3 * direct)
    maximum_length = 3 * direct
    candidates = []
    all_edges = set()
    visits = 0

    def cell(point):
        x, y = point
        return ((y - origin_y) // pitch) * width + (x - origin_x) // pitch

    def directions(points):
        result = []
        for (x0, y0), (x1, y1) in zip(points, points[1:]):
            result.append(1 if x1 > x0 else 3 if x1 < x0 else
                          2 if y1 > y0 else 0)
        return result

    for i in range(len(nodes) - 1):
        for j in range(i + 1, len(nodes)):
            start = (nodes[i]["x"], nodes[i]["y"])
            end = (nodes[j]["x"], nodes[j]["y"])
            dx, dy = end[0] - start[0], end[1] - start[1]
            saving = ((j - i) - (abs(dx) + abs(dy)) // pitch) * pitch
            length = path_length - saving
            if saving < minimum_saving or length > maximum_length:
                continue
            for orientation in ((0,) if dx == 0 or dy == 0 else (0, 1)):
                replacement = [start]
                x, y = start
                for axis in ((0, 1) if orientation == 0 else (1, 0)):
                    target = end[axis]
                    value = x if axis == 0 else y
                    delta = target - value
                    step = pitch if delta > 0 else -pitch
                    for _ in range(abs(delta) // pitch):
                        if axis == 0:
                            x += step
                        else:
                            y += step
                        replacement.append((x, y))
                before = [node["direction"] for node in nodes[1:i + 1]]
                middle = directions(replacement)
                after = [node["direction"] for node in nodes[j + 1:]]
                whole = before + middle + after
                bends = sum(a != b for a, b in zip(whole, whole[1:]))
                if bends > 16:
                    continue
                route_cells = [cell(point) for point in replacement]
                replacement_edges = list(zip(route_cells, route_cells[1:]))
                visits += len(replacement_edges)
                all_edges.update(replacement_edges)
                candidates.append({
                    "i": i, "j": j, "orientation": orientation,
                    "saving": saving, "length": length, "bends": bends,
                    "edges": replacement_edges,
                })
    ordered_edges = sorted(all_edges)
    tuples = [[row["i"], row["j"], row["orientation"],
               row["saving"], row["bends"]] for row in candidates]
    tuple_hash = hashlib.sha256(
        json.dumps(tuples, separators=(",", ":"), ensure_ascii=True).encode("ascii")
    ).hexdigest()
    return {
        "minimumSaving": minimum_saving, "maximumLength": maximum_length,
        "candidates": candidates, "edges": ordered_edges, "visits": visits,
        "tupleHash": tuple_hash,
    }


def _shortcut_fixture():
    nodes = _path_nodes()
    scalar_event = _scalar_event()
    family = _independent_shortcut_family(nodes, scalar_event)
    original_edges = {(before["cell"], after["cell"])
                      for before, after in zip(nodes, nodes[1:])}
    family_edge_set = set(family["edges"])
    original_path_queries = len(original_edges - family_edge_set)
    edge_allowed = {edge: False for edge in family["edges"]}
    for edge in original_edges & family_edge_set:
        edge_allowed[edge] = True
    if family["candidates"]:
        for edge in family["candidates"][0]["edges"]:
            edge_allowed[edge] = True
    edges = [
        {"fromCell": from_cell, "toCell": to_cell,
         "allowed": edge_allowed[(from_cell, to_cell)]}
        for from_cell, to_cell in family["edges"]
    ]
    candidates = []
    qualified = []
    for row in family["candidates"]:
        allowed = all(edge_allowed[edge] for edge in row["edges"])
        candidate = {key: row[key] for key in ("i", "j", "orientation", "length", "bends")}
        candidate["allowed"] = allowed
        candidates.append(candidate)
        if allowed:
            qualified.append({key: candidate[key]
                              for key in ("i", "j", "orientation", "length", "bends")})
    query_count = len(edges)
    query_count += original_path_queries
    result = {
        "queryQualifiedCount": len(qualified),
        "minimumQualifiedLength": min((item["length"] for item in qualified), default=None),
        "firstQualified": qualified[0] if qualified else None,
    }
    copy_entries = 1
    comparison_entries = 1
    work_steps = (len(candidates) + family["visits"] + query_count +
                  copy_entries + comparison_entries)
    advance_calls = max(
        1, (work_steps + reader.MAX_MANUAL_WORK_QUANTUM - 1) //
        reader.MAX_MANUAL_WORK_QUANTUM)
    query_event = {
        "protocolVersion": 1, "sourceRevision": reader.SOURCE_REVISION,
        "runId": RUN_ID, "operationId": reader.PROBE_OPERATION_ID,
        "scope": "ACTUAL_QUERY_CLONE_SHORTCUT_FAMILY_ONLY",
        "binding": {
            "branchIdentityVerified": True, "sourcePublisherUnchanged": True,
            "pass": 1, "netId": scalar_event["netId"],
            "startPadId": scalar_event["startPadId"],
            "rootPadId": scalar_event["rootPadId"],
            "reachedPthPadIds": ["PAD_PTH_1", "PAD_PTH_2"],
            "foundTerminalPadId": "PAD_PTH_1",
            "endCell": nodes[-1]["cell"], "endLayer": nodes[-1]["layer"],
            "goalMaskLength": 2 * scalar_event["gridWidth"] * scalar_event["gridHeight"],
        },
        "diagnostic": {
            "stability": {
                "prefixGeometrySha256": "a" * 64,
                "liveBeforeVerified": True, "liveAfterVerified": True,
                "liveEffectsUnchanged": True, "goalVerified": True,
                "originalPathVerified": True,
            },
            "family": {
                "order": "I_J_ORIENTATION_X_FIRST_0_Y_FIRST_1",
                "minimumSaving": family["minimumSaving"],
                "maximumLength": family["maximumLength"],
                "maximumBends": 16,
                "candidateCount": len(candidates),
                "candidateTupleSha256": family["tupleHash"],
                "replacementEdgeVisits": family["visits"],
                "distinctDirectedEdges": len(edges),
            },
            "work": {
                "advanceCalls": advance_calls,
                "workSteps": work_steps,
                "copyEntries": copy_entries, "comparisonEntries": comparison_entries,
                "queryCalls": query_count,
                "reusedEdgeVisits": family["visits"] - len(edges),
                "activeMillis": 5,
                "originalPathQueries": original_path_queries,
            },
            "edges": edges,
            "candidates": candidates,
            "result": result,
        },
        "feasibility": "UNKNOWN", "legalShortcutProof": False,
        "layoutQualification": False, "routeAdoption": False,
    }
    query_summary = {
        "protocolVersion": 1, "sourceRevision": reader.SOURCE_REVISION,
        "status": "PASS", "runId": RUN_ID,
        "operationId": reader.PROBE_OPERATION_ID, "events": 1,
        "queryQualifiedCount": result["queryQualifiedCount"],
        "scope": "ACTUAL_QUERY_CLONE_SHORTCUT_FAMILY_ONLY",
        "feasibility": "UNKNOWN", "legalShortcutProof": False,
        "layoutQualification": False, "routeAdoption": False,
    }
    return query_event, query_summary, family


def _tag(marker, value):
    return marker + " " + json.dumps(value, separators=(",", ":"))


def _decode(line):
    return json.loads(line[line.index("{"):])


def _receipt_lines():
    shortcut, shortcut_summary, _family = _shortcut_fixture()
    records = (
        ("RB100_QUALITY_SCALAR_CONTROLS", _controls()),
        ("RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS", _subset()),
        ("RB100_QUALITY_SCALAR_OPERATION",
         _operation(reader.NULL_OPERATION_ID, "NULL", 45_000)),
        ("RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS", _subset()),
        ("RB100_QUALITY_SCALAR_OPERATION",
         _operation(reader.PROBE_OPERATION_ID, "PROBE", 45_010)),
        ("RB100_QUALITY_SCALAR_EVENT", _scalar_event()),
        ("RB100_QUALITY_SCALAR_PAIR", _pair()),
        ("PASS: RB100 quality scalar contracts", _scalar_summary()),
        (reader.PATH_EVENT_MARKER, _path_event()),
        (reader.PATH_SUMMARY_MARKER, _path_summary()),
        (reader.SHORTCUT_EVENT_MARKER, shortcut),
        (reader.SHORTCUT_SUMMARY_MARKER, shortcut_summary),
    )
    return [_tag(marker, value) for marker, value in records]


class QualityPathReceiptTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.scalar, cls.ledger, cls.capture, cls.helper_hashes = reader._load_frozen_scalar()

    def _assert_path_code(self, code, path_event=None, scalar_event=None):
        with self.assertRaises(reader.QualityPathInputError) as caught:
            reader._validate_path_event(
                _path_event() if path_event is None else path_event,
                _scalar_event() if scalar_event is None else scalar_event,
                self.scalar,
            )
        self.assertEqual(caught.exception.code, code)

    def test_frozen_scalar_reader_is_loaded_with_scoped_metadata(self):
        before = {name for name in sys.modules
                  if name.startswith("_q100_quality_path_scalar_")}
        scalar, ledger, _capture, hashes = reader._load_frozen_scalar()
        after = {name for name in sys.modules
                 if name.startswith("_q100_quality_path_scalar_")}
        self.assertEqual(before, after)
        self.assertEqual(scalar.SOURCE_REVISION, 15)
        self.assertEqual(scalar.INPUT_SCHEMA, "p07-quality-shortcut-input-v1")
        self.assertEqual(scalar.RUN_SCHEMA, "q100-quality-shortcut-native-run-v1")
        self.assertEqual(scalar.RESULT_SCHEMA, "q100-quality-shortcut-reader-result-v1")
        self.assertEqual(scalar.SUITE, "Rb100QualityShortcutContractTest")
        self.assertEqual(scalar.SOURCE_INPUT_COUNT, 1070)
        self.assertEqual(scalar.CONTRACT_JAVA_COUNT, 214)
        self.assertEqual(ledger.SOURCE_JAVA_COUNT, 850)
        self.assertEqual(ledger.SOURCE_INPUT_COUNT, 1070)
        self.assertEqual(ledger.CONTRACT_JAVA_COUNT, 214)
        self.assertIn("tests/contracts/Rb100QualityShortcutContractTest.java",
                      scalar.QUALITY_REQUIRED_INPUTS)
        self.assertIn("tests/contracts/P07ShortcutQueryDiagnostics.java",
                      scalar.QUALITY_REQUIRED_INPUTS)
        self.assertIn("tests/contracts/P07ShortcutQueryContractTest.java",
                      scalar.QUALITY_REQUIRED_INPUTS)
        self.assertNotIn("tests/contracts/Rb100QualityScalarContractTest.java",
                         scalar.QUALITY_REQUIRED_INPUTS)
        self.assertNotIn("tests/contracts/Rb100QualityPathContractTest.java",
                         scalar.QUALITY_REQUIRED_INPUTS)
        self.assertEqual(hashes["frozenScalarReaderSha256"],
                         reader.PINNED_SCALAR_READER_SHA256)

    def test_synthetic_seven_segment_path_recomputes_all_metrics(self):
        old = self.scalar._validate_receipt_lines(_receipt_lines()[:8], self.ledger)
        path = reader._validate_path_event(_path_event(), old["event"], self.scalar)
        self.assertEqual((path["pathNodeCount"], path["planarLength"],
                          path["directDistance"], path["bends"],
                          path["transitionCount"]), (66, 650, 210, 6, 0))
        self.assertEqual(path["nodes"][0]["cost"], 2)
        self.assertEqual(path["nodes"][-1]["cost"], 992)
        self.assertEqual(path["repeatedSameFaceCells"], 0)
        self.assertEqual(path["repeatedSameFaceEdges"], 0)
        self.assertEqual(path["feasibility"], "UNKNOWN")
        self.assertIs(path["legalShortcutProof"], False)
        self.assertIs(path["layoutQualification"], False)

    def test_repeated_same_face_cells_and_edges_are_counted_without_a_shortcut_claim(self):
        scalar_event = _scalar_event()
        scalar_event.update({
            "startX": 30, "startY": 30, "endX": 30, "endY": 30,
            "gridWidth": 2, "gridHeight": 2,
            "pathNodeCount": 3, "transitionCount": 0,
            "planarLength": 20, "directDistance": 0, "bends": 1,
        })
        cells = 4
        loop_nodes = []
        for index, (x, direction, cost, serial) in enumerate((
                (0, 4, 2, 7), (1, 1, 14, 8), (0, 3, 61, 9))):
            cell = x
            key = (((0 * 2 + 0) * 5 + direction) * cells) + cell
            loop_nodes.append({
                "index": index, "parentIndex": -1 if index == 0 else index - 1,
                "cell": cell, "x": 30 + x * 10, "y": 30, "layer": 0,
                "direction": direction, "transitions": 0,
                "cost": cost, "heuristic": 0, "serial": serial, "key": key,
            })
        event = _path_event()
        event["captureQualityIndex"] = len(loop_nodes)
        event["nodes"] = loop_nodes

        path = reader._validate_path_event(event, scalar_event, self.scalar)
        self.assertEqual(path["repeatedSameFaceCells"], 1)
        self.assertEqual(path["repeatedSameFaceEdges"], 1)
        self.assertIs(path["legalShortcutProof"], False)

    def test_grid_key_cost_heading_parent_and_serial_are_independent_checks(self):
        damaged = _path_event()
        damaged["nodes"][10]["key"] += 1
        self._assert_path_code("PATH_STATE_KEY_INVALID", damaged)

        damaged = _path_event()
        damaged["nodes"][10]["x"] += 1
        self._assert_path_code("PATH_GRID_COORDINATE_INVALID", damaged)

        damaged = _path_event()
        damaged["nodes"][10]["parentIndex"] = 8
        self._assert_path_code("PATH_PARENT_ORDER_INVALID", damaged)

        damaged = _path_event()
        damaged["nodes"][10]["direction"] = 0
        cells = 478 * 309
        node = damaged["nodes"][10]
        node["key"] = (((node["transitions"] * 2 + node["layer"]) * 5 +
                         node["direction"]) * cells) + node["cell"]
        self._assert_path_code("PATH_HEADING_INVALID", damaged)

        damaged = _path_event()
        damaged["nodes"][10]["serial"] = damaged["nodes"][9]["serial"]
        self._assert_path_code("PATH_NODE_IDENTITY_DUPLICATE", damaged)

        damaged = _path_event()
        damaged["nodes"][10]["cost"] += 1
        self._assert_path_code("PATH_COST_CONSERVATION_INVALID", damaged)

        damaged = _path_event()
        damaged["nodes"][0]["cost"] = 0
        self._assert_path_code("PATH_INITIAL_COST_INVALID", damaged)

    def test_scalar_conservation_and_path_claims_fail_closed(self):
        event = _path_event()
        scalar_event = _scalar_event()
        scalar_event["planarLength"] = 640
        self._assert_path_code("PATH_SCALAR_CONSERVATION_INVALID", event, scalar_event)

        event = _path_event()
        event["legalShortcutProof"] = True
        self._assert_path_code("PATH_EVENT_IDENTITY_OR_SCOPE_INVALID", event)

        event = _path_event()
        event["sourceRevision"] = 8
        self._assert_path_code("RECORD_PROTOCOL_INVALID", event)

    def test_all_twelve_markers_are_closed_ordered_and_unique(self):
        lines = _receipt_lines()
        parsed = reader._validate_receipt_lines(lines, self.ledger, self.scalar)
        revisioned = [_decode(line)["sourceRevision"] for line in lines
                      if "sourceRevision" in _decode(line)]
        self.assertTrue(revisioned)
        self.assertEqual(set(revisioned), {15})
        self.assertEqual(parsed["pathSummary"]["nodes"], 66)
        self.assertEqual(parsed["shortcut"]["family"]["candidateCount"],
                         len(_independent_shortcut_family(
                             _path_nodes(), _scalar_event())["candidates"]))
        self.assertGreater(parsed["shortcut"]["queryQualifiedCount"], 0)
        for damaged in (
                lines[:8] + [lines[8], lines[8]] + lines[10:],
                lines[:9] + [lines[10], lines[9]] + lines[11:],
                lines[:-1] + ["RB100_UNEXPECTED {}"]):
            with self.assertRaises(reader.QualityPathInputError):
                reader._validate_receipt_lines(damaged, self.ledger, self.scalar)

    def test_literal_family_covers_both_orientations_aligned_dedup_and_exact_threshold(self):
        _event, _summary, family = _shortcut_fixture()
        tuples = {(row["i"], row["j"], row["orientation"]): row
                  for row in family["candidates"]}
        self.assertEqual(family["minimumSaving"], 20)
        self.assertIn((4, 14, 0), tuples)
        self.assertNotIn((4, 14, 1), tuples)
        self.assertEqual(tuples[(4, 14, 0)]["saving"], 20)
        self.assertEqual(tuples[(4, 14, 0)]["length"], 630)
        self.assertIn((4, 15, 0), tuples)
        self.assertIn((4, 15, 1), tuples)
        self.assertEqual(tuples[(4, 15, 0)]["saving"], 20)
        self.assertEqual(tuples[(4, 15, 1)]["saving"], 20)

    def test_shortcut_edges_are_closed_and_candidates_use_every_edge_flag(self):
        event, summary, _family = _shortcut_fixture()
        diagnostic = event["diagnostic"]
        edges = {(row["fromCell"], row["toCell"]): row["allowed"]
                 for row in diagnostic["edges"]}
        self.assertIn(True, edges.values())
        self.assertIn(False, edges.values())
        self.assertEqual(summary["queryQualifiedCount"],
                         diagnostic["result"]["queryQualifiedCount"])
        original_edges = {(before["cell"], after["cell"])
                          for before, after in zip(_path_nodes(), _path_nodes()[1:])}
        self.assertEqual(diagnostic["work"]["originalPathQueries"],
                         len(original_edges - set(edges)))
        self.assertIs(diagnostic["stability"]["originalPathVerified"], True)

        damaged = json.loads(json.dumps(event))
        damaged["diagnostic"]["edges"].pop()
        self._assert_shortcut_code("SHORTCUT_EDGE_SET_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        damaged["diagnostic"]["edges"][0]["allowed"] = "false"
        self._assert_shortcut_code("SHORTCUT_EDGE_SET_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        family = _shortcut_fixture()[2]
        first_edge = next(edge for edge in family["candidates"][0]["edges"]
                          if edge not in original_edges)
        changed = next(row for row in damaged["diagnostic"]["edges"]
                       if (row["fromCell"], row["toCell"]) == first_edge)
        changed["allowed"] = not changed["allowed"]
        self._assert_shortcut_code("SHORTCUT_CANDIDATE_EDGE_CONJUNCTION_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        original_edge = next(edge for edge in sorted(original_edges)
                             if edge in edges)
        original_row = next(row for row in damaged["diagnostic"]["edges"]
                            if (row["fromCell"], row["toCell"]) == original_edge)
        original_row["allowed"] = False
        self._assert_shortcut_code("SHORTCUT_ORIGINAL_PATH_EDGE_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        damaged["diagnostic"]["candidates"][0]["allowed"] = not \
            damaged["diagnostic"]["candidates"][0]["allowed"]
        self._assert_shortcut_code("SHORTCUT_CANDIDATE_EDGE_CONJUNCTION_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        damaged["diagnostic"]["candidates"][0], damaged["diagnostic"]["candidates"][1] = \
            damaged["diagnostic"]["candidates"][1], damaged["diagnostic"]["candidates"][0]
        self._assert_shortcut_code("SHORTCUT_CANDIDATE_GEOMETRY_INVALID", damaged)

    def test_shortcut_family_hash_count_identity_and_claims_fail_closed(self):
        event, _summary, _family = _shortcut_fixture()
        for field, value, code in (
                ("candidateCount", event["diagnostic"]["family"]["candidateCount"] + 1,
                 "SHORTCUT_FAMILY_DERIVATION_INVALID"),
                ("candidateTupleSha256", "0" * 64,
                 "SHORTCUT_FAMILY_DERIVATION_INVALID"),
                ("replacementEdgeVisits",
                 event["diagnostic"]["family"]["replacementEdgeVisits"] - 1,
                 "SHORTCUT_FAMILY_DERIVATION_INVALID")):
            damaged = json.loads(json.dumps(event))
            damaged["diagnostic"]["family"][field] = value
            self._assert_shortcut_code(code, damaged)

        for field, value in (
                ("netId", "OTHER_NET"), ("startPadId", "OTHER_START"),
                ("rootPadId", "OTHER_ROOT"), ("endCell", 0),
                ("goalMaskLength", 1),
                ("reachedPthPadIds", ["PAD_PTH_2", "PAD_PTH_1"])):
            damaged = json.loads(json.dumps(event))
            damaged["binding"][field] = value
            self._assert_shortcut_code(
                "SHORTCUT_BINDING_IDENTITY_INVALID" if field in
                ("netId", "startPadId", "rootPadId") else
                "SHORTCUT_REACHED_PTH_IDS_INVALID" if field == "reachedPthPadIds" else
                "SHORTCUT_END_GOAL_IDENTITY_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        damaged["legalShortcutProof"] = True
        self._assert_shortcut_code("SHORTCUT_EVENT_IDENTITY_OR_SCOPE_INVALID", damaged)

        for field, value in (("layoutQualification", True), ("routeAdoption", True),
                             ("feasibility", "POSSIBLE")):
            damaged = json.loads(json.dumps(event))
            damaged[field] = value
            self._assert_shortcut_code("SHORTCUT_EVENT_IDENTITY_OR_SCOPE_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        damaged["sourceRevision"] = 14
        self._assert_shortcut_code("SHORTCUT_EVENT_IDENTITY_OR_SCOPE_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        damaged["diagnostic"]["result"]["minimumQualifiedLength"] = 0
        self._assert_shortcut_code("SHORTCUT_RESULT_COUNT_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        damaged["diagnostic"]["result"]["firstQualified"]["i"] += 1
        self._assert_shortcut_code("SHORTCUT_FIRST_QUALIFIED_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        damaged["diagnostic"]["stability"]["prefixGeometrySha256"] = "not-a-hash"
        self._assert_shortcut_code("SHORTCUT_STABILITY_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        damaged["diagnostic"]["work"]["advanceCalls"] = 641
        self._assert_shortcut_code("SHORTCUT_WORK_DOMAIN_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        damaged["diagnostic"]["work"]["copyEntries"] = \
            reader.MAX_DIAGNOSTIC_WORK_STEPS + 1
        self._assert_shortcut_code("SHORTCUT_WORK_DOMAIN_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        work = damaged["diagnostic"]["work"]
        work["advanceCalls"] = reader.MAX_ADVANCE_CALLS
        work["workSteps"] = reader.MAX_DIAGNOSTIC_WORK_STEPS
        work["copyEntries"] = reader.MAX_DIAGNOSTIC_WORK_STEPS // 2
        work["comparisonEntries"] = reader.MAX_DIAGNOSTIC_WORK_STEPS // 2
        self._assert_shortcut_code("SHORTCUT_WORK_CONSERVATION_INVALID", damaged)

        damaged = json.loads(json.dumps(event))
        damaged["diagnostic"]["work"]["queryCalls"] += 1
        self._assert_shortcut_code("SHORTCUT_WORK_CONSERVATION_INVALID", damaged)

        _event, summary, _family = _shortcut_fixture()
        summary["routeAdoption"] = True
        self._assert_shortcut_code(
            "SHORTCUT_SUMMARY_IDENTITY_OR_SCOPE_INVALID", summary=summary)

        _event, summary, _family = _shortcut_fixture()
        summary["queryQualifiedCount"] += 1
        self._assert_shortcut_code(
            "SHORTCUT_SUMMARY_IDENTITY_OR_SCOPE_INVALID", summary=summary)

    def test_copy_and_comparison_counters_can_span_multiple_full_grid_arrays(self):
        event, _summary, family = _shortcut_fixture()
        path = reader._validate_path_event(_path_event(), _scalar_event(), self.scalar)
        work = event["diagnostic"]["work"]
        cells = _scalar_event()["gridWidth"] * _scalar_event()["gridHeight"]
        work["copyEntries"] = 2 * 5 * cells + 500
        work["comparisonEntries"] = 3 * 5 * cells + 700
        minimum_work = (
            len(family["candidates"]) + family["visits"] +
            work["originalPathQueries"] + work["copyEntries"] +
            work["comparisonEntries"]
        )
        work["workSteps"] = max(work["workSteps"], minimum_work)
        work["advanceCalls"] = max(
            1, (work["workSteps"] + reader.MAX_MANUAL_WORK_QUANTUM - 1) //
            reader.MAX_MANUAL_WORK_QUANTUM)
        parsed = reader._validate_shortcut_event(event, _scalar_event(), path)
        self.assertEqual(parsed["queryQualifiedCount"],
                         event["diagnostic"]["result"]["queryQualifiedCount"])

    def _assert_shortcut_code(self, code, event=None, summary=None):
        shortcut, generated_summary, _family = _shortcut_fixture()
        with self.assertRaises(reader.QualityPathInputError) as caught:
            parsed = reader._validate_shortcut_event(
                shortcut if event is None else event,
                _scalar_event(), reader._validate_path_event(
                    _path_event(), _scalar_event(), self.scalar))
            reader._validate_shortcut_summary(
                generated_summary if summary is None else summary,
                shortcut if event is None else event, parsed)
        self.assertEqual(caught.exception.code, code)

    def test_current_authored_context_is_not_inherited_from_the_generic_scalar_reader(self):
        for field, wrong in (
                ("placementDensityVersion", 1),
                ("routingLayer", "P07_FULLER_TWO_LAYER"),
                ("traceWidthProfile", "ROLE_TRACE")):
            with self.subTest(field=field):
                lines = _receipt_lines()
                for position in (2, 4):
                    operation = _decode(lines[position])
                    operation["result"]["reportFacts"][field] = wrong
                    lines[position] = _tag("RB100_QUALITY_SCALAR_OPERATION", operation)
                with self.assertRaises(reader.QualityPathInputError) as caught:
                    reader._validate_receipt_lines(lines, self.ledger, self.scalar)
                self.assertEqual(caught.exception.code, "QUALITY_PATH_CONTEXT_INVALID")

        lines = _receipt_lines()
        for position in (2, 4):
            operation = _decode(lines[position])
            operation["result"]["reportFacts"]["placementDensityVersion"] = True
            lines[position] = _tag("RB100_QUALITY_SCALAR_OPERATION", operation)
        with self.assertRaises(reader.QualityPathInputError) as caught:
            reader._validate_receipt_lines(lines, self.ledger, self.scalar)
        self.assertEqual(caught.exception.code, "OPERATION_FACT_IDENTITY_INVALID")

        lines = _receipt_lines()
        operation = _decode(lines[4])
        operation["result"]["reportFacts"]["placementDensityVersion"] = 1
        lines[4] = _tag("RB100_QUALITY_SCALAR_OPERATION", operation)
        with self.assertRaises(reader.QualityPathInputError) as caught:
            reader._validate_receipt_lines(lines, self.ledger, self.scalar)
        self.assertEqual(caught.exception.code, "PAIR_OPERATION_PARITY_INVALID")

    def test_exact_cli_flags_retain_the_existing_input_spec_contract(self):
        self.assertEqual(reader._parse_cli([
            "--input-spec", "spec.json", "--input-spec-sha256", "a" * 64,
        ]), ("spec.json", "a" * 64))
        with self.assertRaises(reader.QualityPathInputError) as caught:
            reader._parse_cli(["--input-json", "spec.json", "--input-sha256", "a" * 64])
        self.assertEqual(caught.exception.code, "CLI_ARGUMENTS_INVALID")

    def test_malformed_cli_reports_static_failure_without_echoing_path(self):
        output = io.StringIO()
        sensitive = "C:/Users/Private Person/secret-input.json"
        with redirect_stdout(output):
            code = reader.main([
                "--input-spec", sensitive, "--input-spec-sha256", "not-a-sha",
            ])
        self.assertEqual(code, 2)
        self.assertEqual(output.getvalue(), "FAIL: CLI_ARGUMENTS_INVALID\n")
        self.assertNotIn("Private Person", output.getvalue())


class SourceBoundRunTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        _scalar, cls.ledger, cls.capture, _hashes = reader._load_frozen_scalar()

    def _make_source(self, base):
        root = base / "source"
        required = (set(self.ledger.STATIC_NATIVE_INPUTS) |
                    set(self.ledger.REQUIRED_NATIVE_INPUTS) |
                    set(reader.QUALITY_REQUIRED_INPUTS))
        source_java = sorted(path for path in required
                             if path.startswith("src/") and path.endswith(".java"))
        contract_java = sorted(path for path in required
                               if path.startswith("tests/contracts/") and
                               path.endswith(".java"))
        self.assertLessEqual(len(source_java), 850)
        self.assertLessEqual(len(contract_java), 214)
        source_java.extend("src/qualityPathFixture/Source{:04d}.java".format(index)
                           for index in range(850 - len(source_java)))
        contract_java.extend("tests/contracts/QualityShortcutFixture{:04d}.java".format(index)
                             for index in range(214 - len(contract_java)))
        all_paths = sorted(set(source_java + contract_java + list(self.ledger.STATIC_NATIVE_INPUTS)))
        self.assertEqual(len(all_paths), 1070)
        rows = []
        for relative in all_paths:
            path = root.joinpath(*relative.split("/"))
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"x")
            rows.append({"path": relative, "bytes": 1,
                         "sha256": hashlib.sha256(b"x").hexdigest()})
        pins_path = base / "source-pins.json"
        pins_raw = json.dumps(rows, separators=(",", ":"), sort_keys=True).encode("utf-8")
        pins_path.write_bytes(pins_raw)
        return root, pins_path, pins_raw, all_paths

    def _make_bundle(self, base):
        root, pins_path, pins_raw, all_paths = self._make_source(base)
        artifacts = {
            name: base / (name + ".json" if name == "resultPath" else name + ".txt")
            for name in ("resultPath", "stdoutPath", "receiptPath", "stderrPath")
        }
        lines = _receipt_lines()
        receipt = ("\n".join(lines) + "\n").encode("utf-8")
        stdout = ("compiler warning\n" + "\n".join(lines) + "\n" +
                  reader.FOCUSED_PASS + "\n" + reader.CLEANUP_LINE + "\n").encode("utf-8")
        stderr = b""
        artifacts["receiptPath"].write_bytes(receipt)
        artifacts["stdoutPath"].write_bytes(stdout)
        artifacts["stderrPath"].write_bytes(stderr)
        run = {
            "schema": reader.RUN_SCHEMA, "source": "candidate",
            "suite": reader.SUITE, "cohortId": None,
            "command": {"entry": reader.ENTRY_SCRIPT, "suite": reader.SUITE,
                        "suites": [reader.SUITE], "args": [], "environment": {}},
            "layoutVersion": 26, "dependencyEpoch": reader.DEPENDENCY_EPOCH,
            "sourceSnapshotRevision": 15, "inputCount": 1070,
            "inputBytes": 1070, "inputPinsSha256": hashlib.sha256(pins_raw).hexdigest(),
            "inputAudit": "PASS_BEFORE_AND_AFTER", "exitCode": 0, "nativeExitCode": 0,
            "terminationProven": True, "cleanupStatus": "PASS", "cleanupElapsedMs": None,
            "viaCost": 60, "nativeLimitMs": 480_000, "outerLimitMs": 1_800_000,
            "processId": 412, "processStartTicks": "638981234567890123",
            "elapsedSeconds": 73.5, "requestedExecutable": "powershell.exe",
            "stdoutBytes": len(stdout), "stdoutSha256": hashlib.sha256(stdout).hexdigest(),
            "stderrBytes": len(stderr), "stderrSha256": hashlib.sha256(stderr).hexdigest(),
            "receiptBytes": len(receipt), "receiptSha256": hashlib.sha256(receipt).hexdigest(),
        }
        run_raw = json.dumps(run, separators=(",", ":"), sort_keys=True).encode("utf-8")
        artifacts["resultPath"].write_bytes(run_raw)
        spec = {
            "schema": reader.INPUT_SCHEMA, "sourceRoot": str(root),
            "pinsPath": str(pins_path), "pinsSha256": hashlib.sha256(pins_raw).hexdigest(),
            "inputCount": 1070, "inputBytes": 1070, "sourceSnapshotRevision": 15,
            **{name: str(path) for name, path in artifacts.items()},
        }
        spec_path = base / "input-spec.json"
        spec_raw = json.dumps(spec, separators=(",", ":"), sort_keys=True).encode("utf-8")
        spec_path.write_bytes(spec_raw)
        return {
            "base": base, "root": root, "pinsPath": pins_path,
            "pinsRaw": pins_raw, "allPaths": all_paths, "specPath": spec_path,
            "spec": spec, "artifacts": artifacts, "run": run,
            "lines": lines, "receipt": receipt, "stdout": stdout,
        }

    def _rewrite_receipt(self, bundle, lines):
        receipt = ("\n".join(lines) + "\n").encode("utf-8")
        stdout = ("compiler warning\n" + "\n".join(lines) + "\n" +
                  reader.FOCUSED_PASS + "\n" + reader.CLEANUP_LINE + "\n").encode("utf-8")
        bundle["artifacts"]["receiptPath"].write_bytes(receipt)
        bundle["artifacts"]["stdoutPath"].write_bytes(stdout)
        run = json.loads(bundle["artifacts"]["resultPath"].read_text(encoding="utf-8"))
        run.update({
            "receiptBytes": len(receipt), "receiptSha256": hashlib.sha256(receipt).hexdigest(),
            "stdoutBytes": len(stdout), "stdoutSha256": hashlib.sha256(stdout).hexdigest(),
        })
        bundle["artifacts"]["resultPath"].write_text(
            json.dumps(run, separators=(",", ":"), sort_keys=True), encoding="utf-8")

    def _assert_run_code(self, code, bundle):
        with self.assertRaises(reader.QualityPathInputError) as caught:
            reader.run_validation(str(bundle["specPath"]),
                                  hashlib.sha256(bundle["specPath"].read_bytes()).hexdigest())
        self.assertEqual(caught.exception.code, code)

    def test_complete_source_bound_synthetic_run_passes_with_unknown_feasibility(self):
        with tempfile.TemporaryDirectory(prefix="q100-quality-path-") as temporary:
            bundle = self._make_bundle(Path(temporary))
            result = reader.run_validation(
                str(bundle["specPath"]),
                hashlib.sha256(bundle["specPath"].read_bytes()).hexdigest(),
            )
            self.assertEqual(result["schema"], "q100-quality-shortcut-reader-result-v1")
            self.assertEqual(result["sourceSnapshotRevision"], 15)
            self.assertEqual(result["source"]["inputCount"], 1070)
            self.assertIn("tests/contracts/Rb100QualityShortcutContractTest.java",
                          result["source"]["requiredNativeInputSha256"])
            self.assertIn("tests/contracts/P07ShortcutQueryDiagnostics.java",
                          result["source"]["requiredNativeInputSha256"])
            self.assertEqual(result["qualityPathObservation"]["pathNodeCount"], 66)
            self.assertEqual(result["qualityPathObservation"]["planarLength"], 650)
            self.assertEqual(result["qualityPathObservation"]["directDistance"], 210)
            self.assertEqual(result["qualityPathObservation"]["bends"], 6)
            self.assertEqual(result["qualityObservation"]["placementDensityVersion"], 14)
            self.assertEqual(result["qualityObservation"]["routingLayer"], "BOTTOM")
            self.assertEqual(result["qualityPathObservation"]["feasibility"], "UNKNOWN")
            self.assertIs(result["qualityPathObservation"]["legalShortcutProof"], False)
            self.assertIs(result["qualityPathObservation"]["layoutQualification"], False)
            shortcut = result["qualityShortcutObservation"]
            self.assertEqual(shortcut["scope"],
                             "ACTUAL_QUERY_CLONE_SHORTCUT_FAMILY_ONLY")
            self.assertGreater(shortcut["family"]["candidateCount"], 0)
            self.assertGreater(shortcut["family"]["replacementEdgeVisits"], 0)
            self.assertGreater(shortcut["queryQualifiedCount"], 0)
            self.assertIs(shortcut["legalShortcutProof"], False)
            self.assertIs(shortcut["routeAdoption"], False)

    def test_spec_schema_revision_and_sha_are_bound_before_run_artifacts(self):
        with tempfile.TemporaryDirectory(prefix="q100-quality-spec-") as temporary:
            bundle = self._make_bundle(Path(temporary))
            old_schema = dict(bundle["spec"])
            old_schema["schema"] = "p07-quality-scalar-input-v1"
            old_raw = json.dumps(old_schema, separators=(",", ":"),
                                 sort_keys=True).encode("utf-8")
            bundle["specPath"].write_bytes(old_raw)
            self._assert_run_code("INPUT_SPEC_IDENTITY_INVALID", bundle)

        with tempfile.TemporaryDirectory(prefix="q100-quality-spec-sha-") as temporary:
            bundle = self._make_bundle(Path(temporary))
            with self.assertRaises(reader.QualityPathInputError) as caught:
                reader.run_validation(str(bundle["specPath"]), "0" * 64)
            self.assertEqual(caught.exception.code, "INPUT_SPEC_SHA256_MISMATCH")

        with tempfile.TemporaryDirectory(prefix="q100-quality-revision14-") as temporary:
            bundle = self._make_bundle(Path(temporary))
            spec = dict(bundle["spec"])
            spec["sourceSnapshotRevision"] = 14
            raw = json.dumps(spec, separators=(",", ":"), sort_keys=True).encode("utf-8")
            bundle["specPath"].write_bytes(raw)
            with self.assertRaises(reader.QualityPathInputError) as caught:
                reader.run_validation(str(bundle["specPath"]), hashlib.sha256(raw).hexdigest())
            self.assertEqual(caught.exception.code, "INPUT_SPEC_IDENTITY_INVALID")

        with tempfile.TemporaryDirectory(prefix="q100-quality-run-revision14-") as temporary:
            bundle = self._make_bundle(Path(temporary))
            run = dict(bundle["run"])
            run.update({
                "schema": "q100-quality-path-native-run-v1",
                "sourceSnapshotRevision": 14,
                "inputCount": 1068, "inputBytes": 1068,
            })
            bundle["artifacts"]["resultPath"].write_text(
                json.dumps(run, separators=(",", ":"), sort_keys=True), encoding="utf-8")
            with self.assertRaises(reader.QualityPathInputError) as caught:
                reader.run_validation(
                    str(bundle["specPath"]),
                    hashlib.sha256(bundle["specPath"].read_bytes()).hexdigest())
            self.assertEqual(caught.exception.code, "NATIVE_RUN_IDENTITY_INVALID")

    def test_source_pin_content_and_complete_receipt_markers_are_bound(self):
        with tempfile.TemporaryDirectory(prefix="q100-quality-source-") as temporary:
            bundle = self._make_bundle(Path(temporary))
            relative = bundle["allPaths"][0]
            bundle["root"].joinpath(*relative.split("/")).write_bytes(b"y")
            self._assert_run_code("SOURCE_PIN_CONTENT_MISMATCH", bundle)

        with tempfile.TemporaryDirectory(prefix="q100-quality-receipt-") as temporary:
            bundle = self._make_bundle(Path(temporary))
            lines = list(bundle["lines"])
            lines[-2] = lines[-1]
            self._rewrite_receipt(bundle, lines)
            self._assert_run_code("RUN_RECEIPT_MARKER_ORDER_INVALID", bundle)

    def test_changed_pinned_helper_is_rejected_in_an_isolated_copy(self):
        with tempfile.TemporaryDirectory(prefix="q100-quality-helper-") as temporary:
            directory = Path(temporary)
            source_directory = Path(reader.__file__).parent
            filenames = (
                "read_quality_path.py", "read_quality_scalars.py",
                "read_current_prefix_ledger.py", "read_fixed_prefix_capture.py",
                "read_fixed_prefix_graph.py",
            )
            for filename in filenames:
                shutil.copyfile(source_directory / filename, directory / filename)
            ledger_path = directory / "read_current_prefix_ledger.py"
            ledger_path.write_bytes(ledger_path.read_bytes() + b"\n# changed helper\n")

            path = directory / "read_quality_path.py"
            module_name = "_q100_quality_path_helper_negative_" + str(id(self))
            module = types.ModuleType(module_name)
            module.__file__ = str(path)
            previous = sys.modules.get(module_name)
            had_previous = module_name in sys.modules
            sys.modules[module_name] = module
            try:
                source = path.read_text(encoding="utf-8")
                exec(compile(source, str(path), "exec", dont_inherit=True), module.__dict__)
                with self.assertRaises(module.QualityPathInputError) as caught:
                    module._load_frozen_scalar()
                self.assertEqual(caught.exception.code,
                                 "PREFIX_LEDGER_HELPER_HASH_MISMATCH")
            finally:
                if had_previous:
                    sys.modules[module_name] = previous
                else:
                    sys.modules.pop(module_name, None)

    def test_path_corruption_and_native_capture_hash_mismatch_are_rejected(self):
        with tempfile.TemporaryDirectory(prefix="q100-quality-path-key-") as temporary:
            bundle = self._make_bundle(Path(temporary))
            lines = list(bundle["lines"])
            path_event = _decode(lines[8])
            path_event["nodes"][12]["key"] += 1
            lines[8] = _tag(reader.PATH_EVENT_MARKER, path_event)
            self._rewrite_receipt(bundle, lines)
            self._assert_run_code("PATH_STATE_KEY_INVALID", bundle)

        with tempfile.TemporaryDirectory(prefix="q100-quality-artifact-hash-") as temporary:
            bundle = self._make_bundle(Path(temporary))
            bundle["artifacts"]["stdoutPath"].write_bytes(bundle["stdout"] + b"changed\n")
            self._assert_run_code("NATIVE_RUN_ARTIFACT_BINDING_INVALID", bundle)

    def test_reparse_reader_input_is_rejected_when_host_supports_symlinks(self):
        with tempfile.TemporaryDirectory(prefix="q100-quality-reparse-") as temporary:
            base = Path(temporary)
            target = base / "target.json"
            link = base / "link.json"
            target.write_text("{}", encoding="utf-8")
            try:
                link.symlink_to(target)
            except (OSError, NotImplementedError):
                self.skipTest("Host does not allow creating a test symlink")
            with self.assertRaises(reader.QualityPathInputError) as caught:
                reader._read_local(link, 1024, "TEST_INPUT")
            self.assertEqual(caught.exception.code, "TEST_INPUT_REPARSE_PATH")


if __name__ == "__main__":
    unittest.main()
