"""Literal-only tests for the failed-first reader's independent validators.

These synthetic inputs exercise pure validators; they are not source, native,
historical receipt, or authored-board evidence.
"""
from __future__ import annotations

import copy
import importlib.util
import json
from pathlib import Path
import sys
import types
import unittest


HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("_failed_first_reader_under_test",
                                               HERE / "read_failed_first.py")
reader = importlib.util.module_from_spec(SPEC)
assert SPEC is not None and SPEC.loader is not None
SPEC.loader.exec_module(reader)

ConductorError = reader.ConductorError
_CONDUCTOR_ROLE_CANONICAL = reader._CONDUCTOR_ROLE_CANONICAL
_conductor_copy = reader._conductor_copy
validate_complete_layout = reader.validate_complete_layout


def _synthetic_fixture():
    raw = b'{"literal":"not an actual P07 receipt"}\n'
    manifest_sha = "a" * 64
    wire_sha = "b" * 64
    run_id = "01234567-89ab-cdef-0123-456789abcdef"
    pads = [
        {"padId": "QB.A", "netId": "NET_B", "componentId": "U1",
         "role": "SIGNAL", "x": 0, "y": 0},
        {"padId": "QA.C", "netId": "A_COIL_LOW", "componentId": "U2",
         "role": "HIGH_CURRENT", "x": 100, "y": 0},
    ]
    snapshot = {
        "pass": 2, "netId": "A_COIL_LOW", "startPadId": "QA.C", "rootPadId": "DA.A",
        "terminalReason": "NO_PATH", "prefixViaCount": 48, "reachedPadCount": 1,
    }
    branch = {"ordinal": 307, "group": 0, "prefixSha256": reader.PREFIX_SHA256,
              "prefixBytes": 256, "snapshot": snapshot}
    parsed = {
        "candidate": {"traceWidthProfile": "ROLE_WIDTHS", "componentCount": 100,
                      "padCount": 246, "netCount": 60},
        "groups": [{"ordinal": 0, "p07Steps": 3, "diagnosticUnits": 1,
                    "elapsedBeforeOwnLineNanos": 100}],
        "pads": pads, "branches": [branch], "selectedBranch": branch,
        "selectedIndex": 307, "snapshot": snapshot, "lineageOrdinal": 77,
        "p07OwnerSteps": 3,
    }
    operation = {
        "operation": 1, "mode": "AUTHORED", "runId": run_id,
        "sourceManifestSha256": manifest_sha, "wireSha256": wire_sha,
        "receiptPath": "op-1.jsonl", "receiptBytes": len(raw),
        "receiptSha256": reader.sha(raw), "elapsedToCloseNanos": 1000,
        "maxFullGroupNanos": 100, "cleanupNanos": 10,
        "ownersReleased": True, "contextRestored": True, "groupCount": 1,
        "p07OwnerSteps": 3, "callbackUnits": 2, "requestIndex": 0,
        "selectedAttempt": 4, "rootSeed": reader.ROOT_SEED,
        "placementSeed": reader.PLACEMENT_SEED, "routingSeed": reader.ROUTING_SEED,
    }
    reference = {
        "receiptOrdinal": 307, "groupOrdinal": 0, "lineageOrdinal": 77, "pass": 2,
        "netId": "A_COIL_LOW", "startPadId": "QA.C", "rootPadId": "DA.A",
        "terminalReason": "NO_PATH", "prefixGeometrySha256": reader.PREFIX_SHA256,
        "prefixGeometryBytes": 256, "prefixHoleCount": 48, "prefixViaCount": 48,
        "reachedPadIds": ["DA.A"], "reachedPthPadIds": [], "reachedPadCount": 1,
        "reachedPthPadCount": 0,
    }
    digest = "c" * 64
    source = {
        "templateGeometryBeforeSha256": digest, "templateGeometryAfterSha256": digest,
        "templateEffectsBeforeSha256": "d" * 64, "templateEffectsAfterSha256": "d" * 64,
        "detachedGeometrySha256": "e" * 64, "detachedGeometryAfterSha256": "e" * 64,
        "padLands": [
            {"padId": "QB.A", "netId": "NET_B", "x": 0, "y": 0,
             "bounds": [-5, -5, 10, 10], "attachment": "PLATED_THROUGH_HOLE",
             "layer": "TOP+BOTTOM"},
            {"padId": "QA.C", "netId": "A_COIL_LOW", "x": 100, "y": 0,
             "bounds": [95, -5, 10, 10], "attachment": "PLATED_THROUGH_HOLE",
             "layer": "TOP+BOTTOM"},
        ],
        "templateTraceCount": 0, "templateHoleCount": 0, "sourceUnchanged": True,
    }
    captured_order = {
        "pass": 2, "failedNetId": "A_COIL_LOW", "priorPromotedNetId": "NET_B",
        "netIds": ["NET_B", "A_COIL_LOW"], "liveSearchIndex": 2,
        "owner": {
            "policy": "FULLER_TWO_LAYER", "maximumExpansions": 100,
            "expandedAtFailure": 8, "maximumVias": 48, "viaCost": 100,
            "policyTransitions": 4, "policyVias": 48,
            "branchOrderStrategy": "CANONICAL_NEAREST", "netOrderStrategy": "CANONICAL",
            "roleWidths": True, "orderingStream": True,
        },
    }

    def make_run(kind, expected, first):
        return {
            "kind": kind,
            "initial": {
                "pass": 2, "passes": 0, "expanded": 0, "maximumExpansions": 100,
                "maximumVias": 48, "priorPromotedNetId": "NET_B",
                "policy": "FULLER_TWO_LAYER", "viaCost": 100,
                "policyTransitions": 4, "policyVias": 48,
                "branchOrderStrategy": "CANONICAL_NEAREST", "netOrderStrategy": "CANONICAL",
                "roleWidths": True, "capturedOrderingStream": True,
                "sessionOrderingStream": False, "instrumentationModeDifference": True,
                "routingRelevantSettingsMatch": True, "traceCount": 0, "holeCount": 0,
            },
            "ordering": {
                "capturedNetIds": captured_order["netIds"],
                "computedNetIds": captured_order["netIds"],
                "requestedNetIds": expected, "traversedNetIds": [first],
                "orderMatchesCapture": True, "searchCount": 1,
            },
            "outcome": {
                "disposition": "NO_PATH", "reason": first + ":NO_PATH",
                "complete": False, "ownerComplete": True, "pass": 3, "passes": 1,
                "expanded": 8, "maximumExpansions": 100,
                "errorClass": None, "errorMessage": None,
            },
            "finalizers": {"routingGeometry": "NOT_RUN", "allNetConnectivity": "NOT_RUN",
                           "twoLayerRules": "NOT_RUN", "routeQuality": "NOT_RUN",
                           "viaCap": "NOT_RUN"},
            "layout": None,
            "work": {"advanceCalls": 8, "ownerUnits": 2, "wrapperUnits": 8,
                     "copyEntries": 2, "copyChunks": 1, "groups": 1,
                     "maxGroupNanos": 1000, "maximumUnitsPerGroup": 16},
        }

    control = make_run("CAPTURED_ORDER_CONTROL", ["NET_B", "A_COIL_LOW"], "NET_B")
    trial = make_run("FAIL_FIRST_TRIAL", ["A_COIL_LOW", "NET_B"], "A_COIL_LOW")
    doc = {
        "schema": reader.RESULT_SCHEMA, "mode": "OBSERVE", "runId": run_id,
        "finalRunManifestSha256": manifest_sha, "wireContractSha256": wire_sha,
        "operation1": operation, "reference": reference, "source": source,
        "capturedOrder": captured_order, "control": control, "failFirst": trial,
        "orderChange": "MOVED",
        "work": {
            "elapsedCutoffNanos": 2000, "cleanupNanos": 20,
            "groupsBeforeExport": 5, "wrapperGroupsBeforeExport": 2,
            "maxGroupNanosBeforeExport": 2000, "maximumUnitsPerGroupBeforeExport": 4096,
            "unitsBeforeExport": 15, "originalOwnerSteps": 3,
            "originalDiagnosticUnits": 1, "originalCaptureUnits": 2,
            "extraTerminalCallbackUnits": 1, "controlOwnerUnits": 2,
            "controlWrapperUnits": 8, "trialOwnerUnits": 2, "trialWrapperUnits": 8,
            "metadataUnits": 3, "copiedEntries": 4, "copyChunks": 2,
            "copyChunkLimit": reader.COPY_CHUNK, "copiedEntryLimit": reader.MAX_COPY_ENTRIES,
            "cleanup": {"originalOwnersReleased": True, "originalContextRestored": True,
                        "controlReferencesDropped": True, "trialReferencesDropped": True,
                        "sourceUnchanged": True, "allReferencesDropped": True},
        },
    }
    work = doc["work"]
    work["metadataUnits"] = reader._result_tree_minimum_units(doc)
    work["unitsBeforeExport"] = (
        operation["p07OwnerSteps"] + sum(group["diagnosticUnits"] for group in parsed["groups"])
        + operation["callbackUnits"] + work["extraTerminalCallbackUnits"]
        + work["controlOwnerUnits"] + work["controlWrapperUnits"]
        + work["trialOwnerUnits"] + work["trialWrapperUnits"] + work["metadataUnits"])
    base = types.ModuleType("_literal_p07_pad_adapter")

    def validate_pad_lands(lands, actual_pads):
        p07 = {row["padId"]: row for row in actual_pads}
        need_ids = {row["padId"] for row in lands}
        assert need_ids == set(p07)
        role_by_net = {}
        for row in actual_pads:
            previous = role_by_net.setdefault(row["netId"], row["role"])
            assert previous == row["role"]
        return {"byId": {row["padId"]: row for row in lands},
                "p07ById": p07, "roleByNet": role_by_net}

    base._validate_pad_lands = validate_pad_lands
    return doc, parsed, raw, base


def _validate_fixture(doc=None):
    doc, parsed, raw, base = _synthetic_fixture() if doc is None else doc
    return reader.validate_semantics(doc, parsed, raw, doc["finalRunManifestSha256"],
                                     doc["wireContractSha256"], doc["runId"], base)


def _conductor_fixture():
    role_widths = {
        "RETURN": 11, "SUPPLY": 11, "HIGH_CURRENT": 13,
        "CONTROL": 9, "SIGNAL": 9,
    }
    pads = []
    p07 = []
    components = []
    definitions = (
        ("P1", "N1", "CONTROL", "C1", "T1", 20, 20, "SURFACE_PAD", "TOP"),
        ("P2", "N1", "CONTROL", "C2", "T2", 80, 20, "PLATED_THROUGH_HOLE", "TOP"),
        ("P3", "N1", "CONTROL", "C3", "T3", 80, 40, "SURFACE_PAD", "BOTTOM"),
        ("Q1", "N2", "SIGNAL", "C4", "T4", 20, 60, "SURFACE_PAD", "TOP"),
        ("Q2", "N2", "SIGNAL", "C5", "T5", 80, 60, "SURFACE_PAD", "BOTTOM"),
        ("S1", "NS", "RETURN", "C6", "T6", 120, 60, "SURFACE_PAD", "TOP"),
    )
    for pad_id, net_id, role, component_id, terminal_id, x, y, attachment, mounting in definitions:
        plated = attachment == "PLATED_THROUGH_HOLE"
        pads.append({
            "padId": pad_id, "netId": net_id, "componentId": component_id,
            "terminalId": terminal_id, "x": x, "y": y,
            "padBounds": [x - 4, y - 4, 8, 8],
            "probeBounds": [x - 6, y - 6, 12, 12],
            "escapeDirection": [1, 0], "escapeLength": 5,
            "attachment": attachment, "mountingSide": mounting,
            "exposure": "EXPOSED", "topCopper": plated or mounting == "TOP",
            "bottomCopper": plated or mounting == "BOTTOM",
        })
        p07.append({
            "padId": pad_id, "netId": net_id, "componentId": component_id,
            "role": role, "x": x, "y": y,
        })
        components.append({
            "componentId": component_id,
            "terminals": [{"terminalId": terminal_id}],
        })
    traces = [
        {"sourceId": "route-1", "netId": "N1", "startPadId": "P1",
         "endPadId": "P2", "layer": "TOP", "exposure": "EXPOSED", "width": 9,
         "x": [20, 80], "y": [20, 20]},
        {"sourceId": "route-2", "netId": "N1", "startPadId": "P2",
         "endPadId": "P3", "layer": "BOTTOM", "exposure": "EXPOSED", "width": 9,
         "x": [80, 80], "y": [20, 40]},
        {"sourceId": "route-3", "netId": "N2", "startPadId": "Q1",
         "endPadId": None, "layer": "TOP", "exposure": "EXPOSED", "width": 9,
         "x": [20, 50], "y": [60, 60]},
        {"sourceId": "route-4", "netId": "N2", "startPadId": None,
         "endPadId": "Q2", "layer": "BOTTOM", "exposure": "EXPOSED", "width": 9,
         "x": [50, 80], "y": [60, 60]},
    ]
    holes = [{
        "id": "V1", "netId": "N2", "kind": "VIA", "exposure": "EXPOSED",
        "x": 50, "y": 60, "drillRadius": 2, "landRadius": 5,
    }]
    layout = {
        "geometrySha256": "a" * 64,
        "width": 160, "height": 100, "outline": [0, 0, 160, 100],
        "partsTray": [0, 0, 10, 10], "pads": pads, "components": components,
        "regions": [], "labels": [],
        "placementConstraints": {
            "physicalPolicyId": "FULLER_TWO_LAYER", "physicalPolicyVersion": 1,
            "placementDensityVersion": 1, "placementPackingVersion": 1,
            "requiresTwoLayerRouting": True, "routingLayer": "TOP",
            "traceWidthProfileIdentity": "ROLE_TRACE@1",
            "traceWidthProfileCanonical": _CONDUCTOR_ROLE_CANONICAL,
            "parts": [], "barriers": [],
        },
        "clearanceRules": {
            "traceWidth": 9, "copperHeight": 1,
            "minimumVisibleClearance": 6, "minimumCenterlineClearance": 15,
            "routingGridClearanceCells": 1, "roleWidths": role_widths,
            "maximumTransitions": 4, "maximumVias": 8,
        },
        "traceCount": len(traces), "holeCount": len(holes), "viaCount": 1,
        "traces": traces, "holes": holes,
    }
    return layout, p07, {"N1": "CONTROL", "N2": "SIGNAL", "NS": "RETURN"}, ["N1", "N2", "NS"]


def _conductor_expect_error(expected_code, layout, p07, role_by_net, requested):
    try:
        validate_complete_layout(layout, p07, role_by_net, requested)
    except ConductorError as failure:
        if failure.code != expected_code:
            raise AssertionError("conductor fixture expected " + expected_code +
                                 " but received " + failure.code)
        return
    raise AssertionError("conductor fixture did not reject " + expected_code)


def run_conductor_fixture_tests():
    layout, p07, roles, requested = _conductor_fixture()
    facts = validate_complete_layout(layout, p07, roles, requested)
    if not facts["allNetsConnected"] or facts["netCount"] != 3 or \
            facts["singletonNetCount"] != 1 or facts["viaCount"] != 1 or \
            facts["interlayerLinkCount"] != 2:
        raise AssertionError("two-net PTH/via plus singleton conductor positive failed")

    two_net = _conductor_copy(layout)
    two_net["pads"] = [pad for pad in two_net["pads"] if pad["padId"] != "S1"]
    two_net["components"] = [component for component in two_net["components"]
                             if component["componentId"] != "C6"]
    two_net_p07 = [row for row in p07 if row["padId"] != "S1"]
    two_net_roles = {"N1": "CONTROL", "N2": "SIGNAL"}
    two_net_facts = validate_complete_layout(two_net, two_net_p07, two_net_roles,
                                              ["N1", "N2"])
    if not two_net_facts["allNetsConnected"] or two_net_facts["netCount"] != 2 or \
            two_net_facts["singletonNetCount"] != 0:
        raise AssertionError("two-net PTH/via conductor positive failed")

    reordered = _conductor_copy(layout)
    for name in ("pads", "components", "traces", "holes"):
        reordered[name].reverse()
    reversed_facts = validate_complete_layout(reordered, list(reversed(p07)), roles,
                                               list(reversed(requested)))
    if reversed_facts != facts:
        raise AssertionError("conductor validator depended on physical row/vector order")

    bad_p07 = _conductor_copy(p07)
    bad_p07.append(_conductor_copy(bad_p07[0]))
    _conductor_expect_error("P07_PAD_DUPLICATE", layout, bad_p07, roles, requested)
    _conductor_expect_error("NET_VECTOR_DUPLICATE", layout, p07, roles,
                            requested + [requested[0]])

    disconnected = _conductor_copy(layout)
    disconnected["traces"] = [trace for trace in disconnected["traces"]
                               if trace["sourceId"] != "route-1"]
    disconnected["traceCount"] = len(disconnected["traces"])
    _conductor_expect_error("NET_DISCONNECTED", disconnected, p07, roles, requested)

    foreign_short = _conductor_copy(layout)
    for trace in foreign_short["traces"]:
        if trace["sourceId"] == "route-3":
            trace["x"] = [20, 20, 50, 50]
            trace["y"] = [60, 20, 20, 60]
            break
    _conductor_expect_error("FOREIGN_COPPER_SHORT", foreign_short, p07, roles, requested)

    wrong_width = _conductor_copy(layout)
    wrong_width["traces"][0]["width"] = 11
    _conductor_expect_error("TRACE_WIDTH", wrong_width, p07, roles, requested)

    unknown_endpoint = _conductor_copy(layout)
    unknown_endpoint["traces"][0]["startPadId"] = "missing-pad"
    _conductor_expect_error("TRACE_ENDPOINT_PAD", unknown_endpoint, p07, roles, requested)

    misplaced_endpoint = _conductor_copy(layout)
    misplaced_endpoint["traces"][0]["x"][0] = 21
    _conductor_expect_error("TRACE_ENDPOINT_COORDINATE", misplaced_endpoint,
                            p07, roles, requested)

    wrong_identity = _conductor_copy(layout)
    for pad in wrong_identity["pads"]:
        if pad["padId"] == "P2":
            pad["netId"] = "N2"
            break
    _conductor_expect_error("PAD_IDENTITY", wrong_identity, p07, roles, requested)

    unknown_terminal = _conductor_copy(layout)
    for pad in unknown_terminal["pads"]:
        if pad["padId"] == "P2":
            pad["terminalId"] = "unknown-terminal"
            break
    _conductor_expect_error("PAD_TERMINAL_IDENTITY", unknown_terminal,
                            p07, roles, requested)

    broken_pth = _conductor_copy(layout)
    for pad in broken_pth["pads"]:
        if pad["padId"] == "P2":
            pad["attachment"] = "SURFACE_PAD"
            pad["bottomCopper"] = False
            break
    _conductor_expect_error("TRACE_ENDPOINT_LAYER", broken_pth, p07, roles, requested)

    broken_via = _conductor_copy(layout)
    broken_via["holes"][0]["kind"] = "NON_PLATED"
    broken_via["holes"][0]["netId"] = None
    broken_via["holes"][0]["landRadius"] = broken_via["holes"][0]["drillRadius"]
    broken_via["viaCount"] = 0
    _conductor_expect_error("NET_DISCONNECTED", broken_via, p07, roles, requested)

    bad_exposure = _conductor_copy(layout)
    bad_exposure["holes"][0]["exposure"] = "HIDDEN"
    _conductor_expect_error("HOLE_SCHEMA", bad_exposure, p07, roles, requested)

    unknown_hole_net = _conductor_copy(layout)
    unknown_hole_net["holes"][0]["netId"] = "unknown-net"
    _conductor_expect_error("HOLE_NET_OR_RADIUS", unknown_hole_net,
                            p07, roles, requested)

    bad_role = dict(roles)
    bad_role["N1"] = "SIGNAL"
    _conductor_expect_error("ROLE_IDENTITY", layout, p07, bad_role, requested)

    return {"caseCount": 17, "positiveNets": 2,
            "singletonNetCount": 1, "allNetsConnected": True}

class FailedFirstLiteralTests(unittest.TestCase):
    def assertReaderCode(self, expected, callback):
        with self.assertRaises(reader.ReaderError) as caught:
            callback()
        self.assertEqual(expected, caught.exception.code)

    def test_independent_conductor_literals(self):
        facts = run_conductor_fixture_tests()
        self.assertEqual(17, facts["caseCount"])
        self.assertEqual(2, facts["positiveNets"])
        self.assertEqual(1, facts["singletonNetCount"])
        self.assertTrue(facts["allNetsConnected"])

    def test_live_search_cursor_points_after_current_net(self):
        doc, parsed, _raw, _base = _synthetic_fixture()
        pad_data = {"roleByNet": {row["netId"]: row["role"] for row in parsed["pads"]}}
        for order, cursor in ((["A_COIL_LOW", "NET_B"], 1),
                              (["NET_B", "A_COIL_LOW"], 2)):
            captured = copy.deepcopy(doc["capturedOrder"])
            captured["netIds"] = order
            captured["priorPromotedNetId"] = None
            captured["liveSearchIndex"] = cursor
            self.assertEqual("A_COIL_LOW", reader._validate_captured_order(
                captured, parsed, pad_data)["failedNetId"])
            for wrong_cursor in (0, len(order) + 1, cursor - 1):
                bad = copy.deepcopy(captured)
                bad["liveSearchIndex"] = wrong_cursor
                self.assertReaderCode("BF_CAPTURED_VECTOR_BINDING",
                    lambda: reader._validate_captured_order(bad, parsed, pad_data))

    def test_polling_work_cannot_be_hidden_in_a_reconciled_total(self):
        doc, parsed, raw, base = _synthetic_fixture()
        work = doc["control"]["work"]
        old = work["wrapperUnits"]
        work["wrapperUnits"] = work["advanceCalls"] - 1
        delta = old - work["wrapperUnits"]
        doc["work"]["controlWrapperUnits"] -= delta
        doc["work"]["unitsBeforeExport"] -= delta
        self.assertReaderCode("BF_RUN_WORK_BOUNDS", lambda: reader.validate_semantics(
            doc, parsed, raw, doc["finalRunManifestSha256"],
            doc["wireContractSha256"], doc["runId"], base))

    def test_stable_move_to_front_preserves_every_other_relative_order(self):
        source = ["n0", "n1", "failed", "n2", "n3"]
        moved = reader.stable_move_to_front(source, "failed")
        expected_remaining = [item for item in source if item != "failed"]
        self.assertEqual(["failed"] + expected_remaining, moved)
        self.assertEqual(expected_remaining, moved[1:])
        self.assertEqual(source, reader.stable_move_to_front(source, source[0]))

    def test_stable_move_rejects_duplicate_unknown_and_nonstring(self):
        self.assertReaderCode("BF_ORDER_DUPLICATE",
                              lambda: reader.stable_move_to_front(["a", "a"], "a"))
        self.assertReaderCode("BF_ORDER_SELECTED_MISSING",
                              lambda: reader.stable_move_to_front(["a", "b"], "c"))
        self.assertReaderCode("BF_ORDER_INPUT",
                              lambda: reader.stable_move_to_front(["a", 1], "a"))

    def test_canary_width_uses_fixture_role_identity_for_shared_width(self):
        layout = {
            "clearanceRules": {"roleWidths": dict(reader.ROLE_WIDTHS)},
            "traces": [
                {"netId": "NET_A", "width": 13},
                {"netId": "NET_B", "width": 9},
            ],
        }
        roles = reader._canary_roles(layout)
        self.assertEqual({"NET_A": "HIGH_CURRENT", "NET_B": "CONTROL"}, roles)
        expected = reader._expected_canary_order(roles)
        self.assertEqual(["NET_B", "NET_A"], expected)
        self.assertEqual(expected, reader._bind_canary_priority_order(
            {"netIds": ["NET_B", "NET_A"]}, roles))
        self.assertReaderCode("BF_CANARY_PRIORITY_ORDER",
                              lambda: reader._bind_canary_priority_order(
                                  {"netIds": ["NET_A", "NET_B"]}, roles))
        blocked_order = reader._expected_canary_order(reader.BLOCKED_CANARY_ROLES)
        self.assertEqual(["BLOCK_W_NET", "BLOCK_S_NET", "BLOCK_N_NET",
                          "BLOCK_E_NET", "NET_B", "NET_A"], blocked_order)
        self.assertReaderCode("BF_CANARY_PRIORITY_ORDER",
                              lambda: reader._bind_canary_priority_order(
                                  {"netIds": list(reversed(blocked_order))},
                                  reader.BLOCKED_CANARY_ROLES))
        layout["traces"][1]["width"] = 11
        self.assertReaderCode("BF_CANARY_TRACE_WIDTH_CONSISTENCY",
                              lambda: reader._canary_roles(layout))

    def test_blocked_canary_priority_order_includes_singleton_signal_nets(self):
        expected = ["BLOCK_W_NET", "BLOCK_S_NET", "BLOCK_N_NET", "BLOCK_E_NET",
                    "NET_B", "NET_A"]

        def make_run(net_ids):
            return {
                "kind": "CAPTURED_ORDER_CONTROL",
                "initial": {
                    "pass": 2, "passes": 0, "expanded": 0,
                    "maximumExpansions": 100, "maximumVias": 48,
                    "priorPromotedNetId": None, "policy": "FULLER_TWO_LAYER",
                    "viaCost": 10, "policyTransitions": 4, "policyVias": 48,
                    "branchOrderStrategy": "CANONICAL_NEAREST", "netOrderStrategy": "CANONICAL",
                    "roleWidths": True, "capturedOrderingStream": False,
                    "sessionOrderingStream": False, "instrumentationModeDifference": False,
                    "routingRelevantSettingsMatch": True, "traceCount": 0, "holeCount": 0,
                },
                "ordering": {
                    "capturedNetIds": list(net_ids), "computedNetIds": list(net_ids),
                    "requestedNetIds": list(net_ids), "traversedNetIds": list(net_ids),
                    "orderMatchesCapture": True, "searchCount": 1,
                },
                "outcome": {
                    "disposition": "NO_PATH", "reason": "NET_A:NO_PATH",
                    "complete": False, "ownerComplete": True, "pass": 3, "passes": 1,
                    "expanded": 28, "maximumExpansions": 100,
                    "errorClass": None, "errorMessage": None,
                },
                "finalizers": {"routingGeometry": "NOT_RUN", "allNetConnectivity": "NOT_RUN",
                               "twoLayerRules": "NOT_RUN", "routeQuality": "NOT_RUN",
                               "viaCap": "NOT_RUN"},
                "layout": None,
                "work": {"advanceCalls": 145, "ownerUnits": 145, "wrapperUnits": 145,
                         "copyEntries": 0, "copyChunks": 0, "groups": 1,
                         "maxGroupNanos": 1, "maximumUnitsPerGroup": 512},
            }

        roles = dict(reader.BLOCKED_CANARY_ROLES)
        self.assertEqual(expected, reader._expected_canary_order(roles))
        valid = reader._validate_canary_run(make_run(expected), "NONFOUND")
        self.assertEqual(expected, reader._bind_canary_priority_order(valid, roles))
        duplicate = make_run(expected)
        duplicate["ordering"]["capturedNetIds"][1] = duplicate["ordering"]["capturedNetIds"][0]
        self.assertReaderCode("BF_CANARY_ORDERING",
                              lambda: reader._validate_canary_run(duplicate, "NONFOUND"))
        wrong_order = list(reversed(expected))
        wrong = reader._validate_canary_run(make_run(wrong_order), "NONFOUND")
        self.assertReaderCode("BF_CANARY_PRIORITY_ORDER",
                              lambda: reader._bind_canary_priority_order(wrong, roles))

    def test_marker_recomputes_exact_final_export_units_and_maxima(self):
        result_raw = b'{"x":1}\n'
        final_export_units = 5 * ((len(result_raw) + reader.COPY_CHUNK - 1)
                                  // reader.COPY_CHUNK) + 3
        doc = {"work": {"groupsBeforeExport": 4, "unitsBeforeExport": 100,
                        "maxGroupNanosBeforeExport": 20,
                        "maximumUnitsPerGroupBeforeExport": 20,
                        "elapsedCutoffNanos": 1_000, "cleanupNanos": 500}}
        marker = {
            "schema": reader.MARKER_SCHEMA, "status": "COMPLETED_DIAGNOSTIC",
            "resultSha256": reader.sha(result_raw), "resultBytes": len(result_raw),
            "elapsedNanos": 1_010, "finalExportNanos": 10,
            "totalGroups": 5, "totalUnits": 100 + final_export_units,
            "maxGroupNanos": 20,
            "maximumUnitsPerGroup": max(20, final_export_units),
        }
        self.assertEqual(marker, reader._validate_marker(marker, result_raw, doc))
        bad = dict(marker)
        bad["totalUnits"] += 1
        self.assertReaderCode("BF_MARKER_BINDING",
                              lambda: reader._validate_marker(bad, result_raw, doc))

    def test_metadata_units_cover_an_independent_json_tree_walk(self):
        self.assertEqual(4, reader._result_tree_minimum_units({"x": 1}))
        doc, parsed, raw, base = _synthetic_fixture()
        self.assertGreaterEqual(doc["work"]["metadataUnits"],
                                reader._result_tree_minimum_units(doc))
        bad = copy.deepcopy(doc)
        bad["work"]["metadataUnits"] -= 1
        bad["work"]["unitsBeforeExport"] -= 1
        self.assertReaderCode("BF_METADATA_TREE_ACCOUNTING",
                              lambda: reader.validate_semantics(
                                  bad, parsed, raw, bad["finalRunManifestSha256"],
                                  bad["wireContractSha256"], bad["runId"], base))

    def test_compact_result_json_framing_and_one_mib_line_cap(self):
        self.assertEqual({"x": "has spaces"}, reader.strict_json_line(b'{"x":"has spaces"}\n'))
        self.assertReaderCode("BF_RESULT_FRAMING",
                              lambda: reader.strict_json_line(b'{"x":1,"x":2}\n'))
        self.assertReaderCode("BF_RESULT_FRAMING",
                              lambda: reader.strict_json_line(b'{"x":NaN}\n'))
        self.assertReaderCode("BF_RESULT_FRAMING",
                              lambda: reader.strict_json_line(b'{ "x":1}\n'))
        self.assertReaderCode("BF_RESULT_FRAMING",
                              lambda: reader.strict_json_line(b'{"x":1}\r\n'))
        self.assertReaderCode("BF_RESULT_FRAMING",
                              lambda: reader.strict_json_line(b'{"x":1}\n\n'))
        oversized = b'{"x":"' + b"a" * (reader.MAX_JSON_LINE - 8) + b'"}\n'
        self.assertEqual(reader.MAX_JSON_LINE + 1, len(oversized))
        json.loads(oversized.decode("utf-8"))
        self.assertReaderCode("BF_RESULT_FRAMING",
                              lambda: reader.strict_json_line(oversized))

    def test_full_pure_semantics_positive_uses_moved_failed_net_and_p07_group_census(self):
        result = _validate_fixture()
        self.assertEqual("MOVED", result["orderChange"])
        self.assertEqual(["A_COIL_LOW", "NET_B"],
                         result["failFirst"]["expectedOrder"])
        self.assertEqual(1, result["workFacts"]["diagnosticUnits"])
        self.assertEqual(5, result["work"]["groupsBeforeExport"])
        self.assertFalse(result["layoutFacts"]["control"])

    def test_outer_identity_and_prefix_reference_are_bound(self):
        doc, parsed, raw, base = _synthetic_fixture()
        bad = copy.deepcopy(doc)
        bad["wireContractSha256"] = "f" * 64
        self.assertReaderCode("BF_RESULT_IDENTITY",
                              lambda: reader.validate_semantics(
                                  bad, parsed, raw, bad["finalRunManifestSha256"],
                                  doc["wireContractSha256"], doc["runId"], base))
        bad = copy.deepcopy(doc)
        bad["reference"]["prefixGeometrySha256"] = "f" * 64
        self.assertReaderCode("BF_REFERENCE_BINDING",
                              lambda: reader.validate_semantics(
                                  bad, parsed, raw, bad["finalRunManifestSha256"],
                                  bad["wireContractSha256"], bad["runId"], base))

    def test_captured_identity_and_exact_trial_order_are_enforced(self):
        doc, parsed, raw, base = _synthetic_fixture()
        bad = copy.deepcopy(doc)
        bad["capturedOrder"]["netIds"] = ["NET_B", "NET_B"]
        self.assertReaderCode("BF_CAPTURED_VECTOR_BINDING",
                              lambda: reader.validate_semantics(
                                  bad, parsed, raw, bad["finalRunManifestSha256"],
                                  bad["wireContractSha256"], bad["runId"], base))
        bad = copy.deepcopy(doc)
        bad["failFirst"]["ordering"]["requestedNetIds"] = ["NET_B", "A_COIL_LOW"]
        self.assertReaderCode("BF_FRESH_ORDERING_BINDING",
                              lambda: reader.validate_semantics(
                                  bad, parsed, raw, bad["finalRunManifestSha256"],
                                  bad["wireContractSha256"], bad["runId"], base))

    def test_unknown_nonfound_reason_and_ledger_mismatch_reject(self):
        doc, parsed, raw, base = _synthetic_fixture()
        bad = copy.deepcopy(doc)
        bad["control"]["outcome"]["reason"] = "NET_B:SOME_OTHER_REASON"
        self.assertReaderCode("BF_OUTCOME_REASON",
                              lambda: reader.validate_semantics(
                                  bad, parsed, raw, bad["finalRunManifestSha256"],
                                  bad["wireContractSha256"], bad["runId"], base))
        bad = copy.deepcopy(doc)
        bad["work"]["unitsBeforeExport"] += 1
        self.assertReaderCode("BF_WORK_UNITS",
                              lambda: reader.validate_semantics(
                                  bad, parsed, raw, bad["finalRunManifestSha256"],
                                  bad["wireContractSha256"], bad["runId"], base))

    def test_work_caps_and_boolean_as_integer_reject(self):
        doc, parsed, raw, base = _synthetic_fixture()
        bad = copy.deepcopy(doc)
        bad["work"]["copyChunkLimit"] = reader.COPY_CHUNK + 1
        self.assertReaderCode("BF_WORK_BOUNDS",
                              lambda: reader.validate_semantics(
                                  bad, parsed, raw, bad["finalRunManifestSha256"],
                                  bad["wireContractSha256"], bad["runId"], base))
        bad = copy.deepcopy(doc)
        bad["capturedOrder"]["pass"] = True
        self.assertReaderCode("BF_CAPTURED_VECTOR_BINDING",
                              lambda: reader.validate_semantics(
                                  bad, parsed, raw, bad["finalRunManifestSha256"],
                                  bad["wireContractSha256"], bad["runId"], base))


if __name__ == "__main__":
    if sys.argv[1:] != ["--literal"]:
        print("Usage: test_read_failed_first.py --literal", file=sys.stderr)
        raise SystemExit(2)
    sys.argv[:] = ["test_read_failed_first.py"]
    unittest.main(verbosity=2)
