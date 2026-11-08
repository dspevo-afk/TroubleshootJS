"""Independent unit controls; literal packets below are NOT native captures.

Root runs this later with Python -I -B and explicit source paths. --packet may
add a genuine producer packet after execution; its optional acceptance is not an
oracle for fixture settings, geometry, outcome, work or terminal expectations.
No authored original, execution receipt or electrical proof is constructed here.
"""
from __future__ import annotations

import argparse
import ast
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock

OPTIONS = None
RUN_ID = "12345678-1234-1234-1234-123456789abc"
SOURCE_SHA = "a" * 64
SPEC_SHA = "5d2b62c98bd229a73be0e54e4b6adcde4d5080fb87046d8bd770455f80c1224a"
WIRE_SHA = "dd738f0bbac0edc4f4cbaf4a0d2bba80ebeda9a581d79c052e29df8976e73dd6"
SUMMARY_KEYS = ("schema", "status", "runId", "sourceManifestSha256", "wireContractSha256",
                "references", "found", "noPath", "newAuthoredInvocations", "fullD")


def load_unit_module(raw, name):
    module = types.ModuleType(name)
    module.__file__ = "<unit-test-source>"
    exec(compile(raw, module.__file__, "exec"), module.__dict__)
    return module


def literal_layout():
    """Hand-authored two-net straight copper example, never a captured export."""
    pad_rows = (("A1.1", "NET_A", "A1", 260, 300),
                ("A2.1", "NET_A", "A2", 500, 300),
                ("B1.1", "NET_B", "B1", 260, 450),
                ("B2.1", "NET_B", "B2", 500, 450))
    pads, components = [], []
    for pad_id, net_id, component_id, x, y in pad_rows:
        pads.append({"padId": pad_id, "netId": net_id, "componentId": component_id,
                     "terminalId": "1", "x": x, "y": y,
                     "padBounds": [x - 7, y - 7, 14, 14], "probeBounds": [x - 9, y - 9, 18, 18],
                     "escapeDirection": [0, 0], "escapeLength": 0,
                     "attachment": "PLATED_THROUGH_HOLE", "mountingSide": "TOP",
                     "exposure": "EXPOSED", "topCopper": True, "bottomCopper": True})
        components.append({"componentId": component_id, "terminals": [{"terminalId": "1"}]})
    return {"geometrySha256": "b" * 64, "width": 900, "height": 700,
            "outline": [100, 100, 600, 500], "partsTray": [720, 120, 160, 400],
            "pads": pads, "components": components, "regions": [], "labels": [],
            "placementConstraints": {
                "traceWidthProfileIdentity": "ROLE_TRACE@1",
                "traceWidthProfileCanonical": "ROLE_TRACE@1;RETURN=11;SUPPLY=11;HIGH_CURRENT=13;CONTROL=9;SIGNAL=9;units=drawing;edge-clearance=6"},
            "clearanceRules": {"traceWidth": 9, "copperHeight": 9,
                               "minimumVisibleClearance": 6, "minimumCenterlineClearance": 15,
                               "routingGridClearanceCells": 1,
                               "roleWidths": {"RETURN": 11, "SUPPLY": 11, "HIGH_CURRENT": 13, "CONTROL": 9, "SIGNAL": 9},
                               "maximumTransitions": 4, "maximumVias": 48},
            "traceCount": 2, "holeCount": 0, "viaCount": 0,
            "traces": [{"sourceId": "UNIT_A", "netId": "NET_A", "startPadId": "A1.1", "endPadId": "A2.1",
                        "layer": "TOP", "exposure": "EXPOSED", "width": 13, "x": [260, 500], "y": [300, 300]},
                       {"sourceId": "UNIT_B", "netId": "NET_B", "startPadId": "B1.1", "endPadId": "B2.1",
                        "layer": "TOP", "exposure": "EXPOSED", "width": 9, "x": [260, 500], "y": [450, 450]}],
            "holes": []}


def literal_packet(spec):
    """Only a semantic test example; counter literals are not measured goldens."""
    references = {}
    for ordinal, case_id in enumerate(("OPEN", "BLOCKED")):
        fixture = spec["cases"][case_id]
        found = case_id == "OPEN"
        terminal = None if found else {
            "failedNetId": "NET_A", "searchIndex": 1, "searchPass": 2, "passBefore": 2, "passAfter": 3,
            "advanceCallOrdinal": 1, "ownerSearchCleared": True, "searchReleased": True,
            "ownerPromotedNetId": "NET_A", "ownerReason": "NET_A:NO_PATH"}
        run = {"kind": "GENERIC_ROUTING_REFERENCE_V1", "initial": copy.deepcopy(spec["initial"]),
               "ordering": {"capturedNetIds": list(fixture["computedNetIds"]),
                            "computedNetIds": list(fixture["computedNetIds"]),
                            "requestedCohortNetIds": ["NET_A", "NET_B"],
                            "requestedNetIds": list(fixture["requestedNetIds"]),
                            "traversedNetIds": ["NET_A", "NET_B"] if found else ["NET_A"],
                            "orderMatchesCapture": True, "searchCount": 1},
               "outcome": {"disposition": "FOUND" if found else "NO_PATH",
                           "reason": "SUCCESS" if found else "NET_A:NO_PATH", "complete": found,
                           "ownerComplete": True, "pass": 2 if found else 3, "passes": 1,
                           "expanded": 7, "maximumExpansions": 1000000, "errorClass": None, "errorMessage": None},
               "terminalFailure": terminal,
               "finalizers": dict.fromkeys(("routingGeometry", "allNetConnectivity", "twoLayerRules", "routeQuality", "viaCap"),
                                            "PASS" if found else "NOT_RUN"),
               "layout": literal_layout() if found else None,
               "work": {"advanceCalls": 2, "failureObservationUnits": 2,
                        "terminalEvidenceUnits": 0 if found else 12, "ownerUnits": 9,
                        "wrapperUnits": 4 if found else 16, "copyEntries": 0, "copyChunks": 0,
                        "groups": 1, "maxGroupNanos": 1, "maximumUnitsPerGroup": 25}}
        references[case_id] = {"caseId": case_id, "fixtureContextSha256": SPEC_SHA, "run": run,
                               "cleanup": {"ownerReferencesDropped": True, "ownerCleanupNanos": 0, "wrapperAliasesDropped": True},
                               "construction": {"before": ordinal, "after": ordinal + 1},
                               "sourceGeometry": {"beforeSha256": "c" * 64, "afterSha256": "c" * 64, "stable": True}}
    return {"schema": "tsj-generic-routing-reference-v1", "status": "GENERIC_NATURAL_REFERENCES_RECORDED_ONLY",
            "runId": RUN_ID, "sourceManifestSha256": SOURCE_SHA, "wireContractSha256": WIRE_SHA,
            "references": references,
            "sourceReview": {"scope": "GENERIC_NATURAL_ROUTING_REFERENCE_ONLY",
                             "physicalOwnerSha256": "7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c",
                             "studyParentSha256": "88986781508aa34783d11e651082122f4bf8e0710a2263b39cc9b597fce9a8cf",
                             "runOwnerSha256": "4ed1c8561adf1619a8b7ffbd21e23fdf2544dcaafe541af4e5d2b2b1e95cabe4",
                             "fixtureSourceSha256": "dd333ff9c448ce012c3c1a4e603be39b70e1ac1d8d7ef79509d12ab21bd17619",
                             "fixtureOracleSha256": SPEC_SHA, "sessionConstructorEntries": 2}}


def literal_geometry_text_tree(text, case_id="OPEN"):
    """Transport text-policy unit only, not a semantic or native capture."""
    return {"references": {case_id: {"run": {"layout": {"components": [{"geometryCanonical": text}]}}}}}


def set_path(value, path, replacement):
    for part in path[:-1]:
        value = value[part]
    value[path[-1]] = replacement


def get_path(value, path):
    for part in path:
        value = value[part]
    return value


class ReaderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.generator_raw = Path(OPTIONS.generator).read_bytes()
        cls.generator = load_unit_module(cls.generator_raw, "_unit_generic_reader_generator")
        cls.run_source = Path(OPTIONS.run_reader).read_bytes()
        cls.transport_source = Path(OPTIONS.transport_reader).read_bytes()
        cls.fixture_raw = Path(OPTIONS.fixture_spec).read_bytes()
        if hashlib.sha256(cls.fixture_raw).hexdigest() != SPEC_SHA:
            raise RuntimeError("UNIT_FIXTURE_PIN")
        cls.spec = json.loads(cls.fixture_raw.decode("ascii"))
        cls.derived, cls.proof = cls.generator.derive(cls.run_source, cls.transport_source, cls.fixture_raw)
        cls.reader = load_unit_module(cls.derived, "_unit_generic_reference_reader")

    def packet(self):
        return literal_packet(self.spec)

    def admit(self, packet):
        return self.reader.validate_packet(packet, RUN_ID, SOURCE_SHA, WIRE_SHA, self.fixture_raw)

    def reject_mutation(self, path, replacement):
        packet = self.packet()
        set_path(packet, path, replacement)
        with self.assertRaises(self.reader.ReaderError):
            self.admit(packet)

    def test_literal_semantics_only_and_summary(self):
        summary = self.admit(self.packet())
        self.assertEqual(tuple(summary), SUMMARY_KEYS)
        self.assertEqual(summary, {"schema": "tsj-generic-routing-reference-reader-summary-v1",
                                  "status": "PASS_NATURAL_GENERIC_REFERENCE_ONLY", "runId": RUN_ID,
                                  "sourceManifestSha256": SOURCE_SHA, "wireContractSha256": WIRE_SHA,
                                  "references": 2, "found": 1, "noPath": 1, "newAuthoredInvocations": 0, "fullD": False})

    def test_optional_actual_packet(self):
        if OPTIONS.packet is None:
            self.skipTest("Actual producer packet not supplied; unit examples are not capture evidence")
        raw = Path(OPTIONS.packet).read_bytes()
        packet = self.reader.parse_packet(raw)
        summary = self.reader.validate_packet(packet, packet["runId"], packet["sourceManifestSha256"], WIRE_SHA, self.fixture_raw)
        self.assertEqual(tuple(summary), SUMMARY_KEYS)
        self.assertEqual((summary["references"], summary["found"], summary["noPath"]), (2, 1, 1))

    def test_canonical_roundtrip(self):
        packet = self.packet()
        self.assertEqual(self.reader.parse_packet(self.reader.canonical_line(packet)), packet)

    def test_noncanonical_and_invalid_transport(self):
        raw = self.reader.canonical_line(self.packet())
        variants = (raw[:-1], raw + b"\n", raw.replace(b"\n", b"\r\n"), b" " + raw,
                    raw.replace(b'"schema":', b'"schema" :', 1),
                    raw.replace(b'"schema":', b'"schema":null,"schema":', 1),
                    raw.replace(b'"expanded":7', b'"expanded":7.0', 1),
                    raw.replace(b'"expanded":7', b'"expanded":NaN', 1),
                    raw.replace(b'"expanded":7', b'"expanded":Infinity', 1),
                    raw.replace(b'"expanded":7', b'"expanded":07', 1),
                    raw.replace(b'"schema"', b'"\\u0073chema"', 1), b"[]\n", b"\xff\n",
                    b"{" + b" " * 1048576 + b"}\n", b'{"a":' + b'[' * 65 + b'0' + b']' * 65 + b'}\n')
        for variant in variants:
            with self.subTest(digest=hashlib.sha256(variant).hexdigest()):
                with self.assertRaises(self.reader.ReaderError):
                    self.reader.parse_packet(variant)

    def test_parser_text_and_visit_limits(self):
        for packet in ({"x": "x" * 257}, {"x": "\u00e9"}, {"x": 0.0}, {"x": object()}):
            with self.subTest(packet_type=type(packet["x"]).__name__):
                with self.assertRaises(self.reader.ReaderError):
                    self.reader.canonical_line(packet)
        # Exercise the iterative bound without materializing a million objects.
        cyclic = []
        cyclic.append(cyclic)
        with self.assertRaises(self.reader.ReaderError):
            self.reader._tree(cyclic)
        with mock.patch.object(self.reader, "MAX_TREE_VISITS", 5):
            with self.assertRaises(self.reader.ReaderError):
                self.reader._tree({"x": [1, 2, 3, 4]})

    def test_geometry_text_policy_exact_leaf_boundaries_only(self):
        # These intentionally incomplete trees test transport policy alone.
        # They confer no semantic packet acceptance or runtime provenance.
        for case_id in ("OPEN", "BLOCKED"):
            for length in (256, 257, 4096):
                with self.subTest(case=case_id, length=length):
                    tree = literal_geometry_text_tree("g" * length, case_id)
                    self.reader._tree(tree)
                    raw = self.reader.canonical_line(tree)
                    self.assertEqual(self.reader.parse_packet(raw), tree)
                    rows = tree["references"][case_id]["run"]["layout"]["components"]
                    rows.insert(0, {"label": "ordinary"})
                    self.reader._tree(tree)  # index one must be a real list element too
            for text in ("g" * 4097, "g" * 256 + "\u00e9", "g" * 256 + "\n"):
                with self.subTest(case=case_id, rejected_length=len(text)):
                    tree = literal_geometry_text_tree(text, case_id)
                    with self.assertRaises(self.reader.ReaderError):
                        self.reader._tree(tree)
                    raw = (json.dumps(tree, ensure_ascii=True, separators=(",", ":")) + "\n").encode("ascii")
                    with self.assertRaises(self.reader.ReaderError):
                        self.reader.parse_packet(raw)
        # An ordinary leaf keeps the original inclusive 256 limit.
        self.reader._tree({"text": "x" * 256})
        with self.assertRaises(self.reader.ReaderError):
            self.reader._tree({"text": "x" * 257})

    def test_geometry_text_policy_denies_wrong_path_container_and_type(self):
        text = "g" * 257
        leaf = {"geometryCanonical": text}
        layout = {"components": [leaf]}
        wrong = {
            "dotted_whole_key": {"references.OPEN.run.layout.components.0.geometryCanonical": text},
            "dotted_case_key": {"references": {"OPEN.run": {"layout": layout}}},
            "wrong_case": literal_geometry_text_tree(text, "open"),
            "unknown_case": literal_geometry_text_tree(text, "OTHER"),
            "wrong_key": {"references": {"OPEN": {"run": {"layout": {"components": [{"geometrycanonical": text}]}}}}},
            "root_alias": {"complete": {"OPEN": {"run": {"layout": layout}}}},
            "references_list": {"references": [{"run": {"layout": layout}}]},
            "case_list": {"references": {"OPEN": [{"run": {"layout": layout}}]}},
            "run_list": {"references": {"OPEN": {"run": [{"layout": layout}]}}},
            "layout_list": {"references": {"OPEN": {"run": {"layout": [layout]}}}},
            "components_map_string_index": {"references": {"OPEN": {"run": {"layout": {"components": {"0": leaf}}}}}},
            "components_map_integer_index": {"references": {"OPEN": {"run": {"layout": {"components": {0: leaf}}}}}},
            "components_map_bool_index": {"references": {"OPEN": {"run": {"layout": {"components": {True: leaf}}}}}},
            "components_tuple": {"references": {"OPEN": {"run": {"layout": {"components": (leaf,)}}}}},
            "component_row_list": {"references": {"OPEN": {"run": {"layout": {"components": [[text]]}}}}},
            "component_row_text": {"references": {"OPEN": {"run": {"layout": {"components": [text]}}}}},
            "geometry_value_list": literal_geometry_text_tree([text]),
            "geometry_value_map": literal_geometry_text_tree({"text": text}),
            "geometry_value_integer": literal_geometry_text_tree(1),
            "geometry_value_bool": literal_geometry_text_tree(True),
            "geometry_value_null": literal_geometry_text_tree(None),
            "geometry_value_float": literal_geometry_text_tree(1.0),
            "geometry_value_custom": literal_geometry_text_tree(object()),
            "ordinary_component_field": {"references": {"OPEN": {"run": {"layout": {"components": [{"label": text}]}}}}},
            "extra_root_depth": {"wrapper": literal_geometry_text_tree(text)},
            "long_dictionary_key": {"references": {"OPEN": {"run": {"layout": {"components": [{text: "short"}]}}}}},
        }
        for name, tree in wrong.items():
            with self.subTest(path=name):
                with self.assertRaises(self.reader.ReaderError):
                    self.reader._tree(tree)
        # Depth stays bounded even when the final suffix names the geometry path.
        deep = literal_geometry_text_tree("short")
        for _ in range(65):
            deep = {"wrapper": deep}
        with self.assertRaises(self.reader.ReaderError):
            self.reader._tree(deep)

    def test_fixture_pin_before_parse(self):
        with mock.patch.object(self.reader.json, "loads", side_effect=AssertionError("parsed unpinned input")):
            with self.assertRaises(self.reader.ReaderError):
                self.reader.load_fixture_spec(b"not JSON")
        self.assertEqual(self.reader.load_fixture_spec(self.fixture_raw), self.spec)

    def test_frozen_context_rejects_self_derived_expectations(self):
        context = self.reader.fixture_context(self.spec, "OPEN")
        context["initial"] = dict(context["initial"], maximumExpansions=999999)
        run = self.packet()["references"]["OPEN"]["run"]
        run["initial"]["maximumExpansions"] = 999999
        with self.assertRaises(self.reader.ReaderError):
            self.reader.validate_generic_fixture_run(run, context)

    def test_initial_context_and_independent_order(self):
        prefix = ("references", "OPEN", "run")
        for field, value in (("pass", 3), ("passes", 1), ("expanded", 1), ("maximumExpansions", 999999),
                             ("maximumVias", 47), ("priorPromotedNetId", "NET_A"), ("policy", "OTHER"),
                             ("viaCost", 61), ("policyTransitions", 3), ("policyVias", 47),
                             ("branchOrderStrategy", "OTHER"), ("netOrderStrategy", "OTHER"),
                             ("roleWidths", False), ("capturedOrderingStream", True), ("sessionOrderingStream", True),
                             ("instrumentationModeDifference", True), ("routingRelevantSettingsMatch", False),
                             ("traceCount", 1), ("holeCount", 1), ("pass", True)):
            with self.subTest(initial=field, value=value):
                self.reject_mutation(prefix + ("initial", field), value)
        for field in ("capturedNetIds", "computedNetIds", "requestedCohortNetIds", "requestedNetIds", "traversedNetIds"):
            with self.subTest(order=field):
                self.reject_mutation(prefix + ("ordering", field), ["NET_B", "NET_A"] if field != "computedNetIds" and field != "capturedNetIds" else ["NET_A", "NET_B"])
        self.reject_mutation(prefix + ("ordering", "orderMatchesCapture"), 1)
        self.reject_mutation(prefix + ("ordering", "searchCount"), True)
        self.reject_mutation(("references", "BLOCKED", "run", "ordering", "computedNetIds"), ["NET_A", "NET_B"])

    def test_natural_terminal_and_work_equations(self):
        blocked = ("references", "BLOCKED", "run")
        for field, value in (("failedNetId", "NET_B"), ("searchIndex", 2), ("searchPass", 3), ("passBefore", 3),
                             ("passAfter", 2), ("advanceCallOrdinal", 0), ("ownerSearchCleared", False),
                             ("searchReleased", False), ("ownerPromotedNetId", "NET_B"), ("ownerReason", "NO_PATH")):
            with self.subTest(terminal=field):
                self.reject_mutation(blocked + ("terminalFailure", field), value)
        self.reject_mutation(blocked + ("terminalFailure",), None)
        self.reject_mutation(blocked + ("work", "terminalEvidenceUnits"), 13)  # control-only fee must not leak in
        for field, value in (("advanceCalls", 0), ("failureObservationUnits", 1), ("terminalEvidenceUnits", 11),
                             ("ownerUnits", -1), ("wrapperUnits", 15), ("copyEntries", 1048577),
                             ("copyChunks", 1), ("groups", 641), ("maxGroupNanos", 5000000001),
                             ("maximumUnitsPerGroup", 32769), ("advanceCalls", True)):
            with self.subTest(work=field, value=value):
                self.reject_mutation(blocked + ("work", field), value)
        packet = self.packet()
        work = packet["references"]["OPEN"]["run"]["work"]
        work["copyEntries"], work["copyChunks"] = 4097, 1
        with self.assertRaises(self.reader.ReaderError):
            self.admit(packet)

    def test_outcome_finalizer_and_layout_disposition(self):
        open_run = ("references", "OPEN", "run")
        blocked = ("references", "BLOCKED", "run")
        for field, value in (("disposition", "NO_PATH"), ("reason", "NET_A:NO_PATH"), ("complete", False),
                             ("ownerComplete", False), ("pass", 3), ("passes", True), ("expanded", 1000001),
                             ("maximumExpansions", 999999), ("errorClass", "Exception"), ("errorMessage", "bad")):
            with self.subTest(outcome=field):
                self.reject_mutation(open_run + ("outcome", field), value)
        self.reject_mutation(open_run + ("terminalFailure",), self.packet()["references"]["BLOCKED"]["run"]["terminalFailure"])
        self.reject_mutation(open_run + ("finalizers", "routingGeometry"), "NOT_RUN")
        self.reject_mutation(blocked + ("finalizers", "routeQuality"), "PASS")
        self.reject_mutation(open_run + ("layout",), None)
        self.reject_mutation(blocked + ("layout",), literal_layout())

    def test_independent_copper_and_source_pad_binding(self):
        prefix = ("references", "OPEN", "run", "layout")
        mutations = (("broken", ("traces",), [literal_layout()["traces"][0]]),
                     ("pad_move", ("pads", 0, "x"), 261),
                     ("net", ("pads", 0, "netId"), "NET_B"),
                     ("component", ("pads", 0, "componentId"), "A2"),
                     ("missing_pad", ("pads",), literal_layout()["pads"][:-1]),
                     ("surface_instead_of_PTH", ("pads", 0, "attachment"), "SURFACE_PAD"),
                     ("role_width", ("traces", 0, "width"), 9),
                     ("detached_endpoint", ("traces", 0, "x"), [270, 500]),
                     ("wrong_dimensions", ("width",), 901),
                     ("wrong_outline", ("outline",), [100, 100, 610, 500]),
                     ("wrong_tray", ("partsTray",), [720, 120, 160, 399]))
        for name, path, value in mutations:
            with self.subTest(physical=name):
                self.reject_mutation(prefix + path, value)
        # Keep shape/count/endpoint metadata internally consistent; copper itself
        # must independently reject the missing net and the foreign-net bridge.
        for kind in ("missing_copper", "short", "detached_plated_hole"):
            with self.subTest(physical=kind):
                packet = self.packet()
                layout = packet["references"]["OPEN"]["run"]["layout"]
                if kind == "missing_copper":
                    layout["traces"].pop()
                    layout["traceCount"] = 1
                elif kind == "short":
                    layout["traces"].append({"sourceId": "UNIT_SHORT", "netId": "NET_A", "startPadId": None,
                                             "endPadId": None, "layer": "TOP", "exposure": "EXPOSED", "width": 13,
                                             "x": [300, 300], "y": [300, 450]})
                    layout["traceCount"] = 3
                else:
                    layout["holes"] = [{"id": "UNIT_DETACHED", "netId": "NET_A", "kind": "PLATED", "exposure": "EXPOSED",
                                         "x": 600, "y": 550, "drillRadius": 2, "landRadius": 5}]
                    layout["holeCount"] = 1
                with self.assertRaises(self.reader.ReaderError):
                    self.admit(packet)

    def test_cleanup_constructor_geometry_and_source_review(self):
        for case_id in ("OPEN", "BLOCKED"):
            prefix = ("references", case_id)
            for field, value in (("ownerReferencesDropped", False), ("ownerReferencesDropped", 1),
                                 ("wrapperAliasesDropped", False), ("ownerCleanupNanos", True),
                                 ("ownerCleanupNanos", -1), ("ownerCleanupNanos", 120000000001)):
                with self.subTest(case=case_id, cleanup=field, value=value):
                    self.reject_mutation(prefix + ("cleanup", field), value)
            for path, value in ((("construction", "before"), 2), (("construction", "after"), True),
                                (("sourceGeometry", "beforeSha256"), "C" * 64),
                                (("sourceGeometry", "afterSha256"), "d" * 64), (("sourceGeometry", "stable"), False)):
                with self.subTest(case=case_id, field=path):
                    self.reject_mutation(prefix + path, value)
        for field, value in (("scope", "FULL_D"), ("physicalOwnerSha256", "d" * 64),
                             ("studyParentSha256", "d" * 64), ("runOwnerSha256", "d" * 64),
                             ("fixtureSourceSha256", "d" * 64), ("fixtureOracleSha256", "d" * 64),
                             ("sessionConstructorEntries", 3), ("sessionConstructorEntries", True)):
            with self.subTest(review=field):
                self.reject_mutation(("sourceReview", field), value)

    def test_envelope_identity_legacy_and_type_denial(self):
        for path, value in ((("schema",), "tsj-selected-observation-wire-v2"),
                            (("status",), "PASS"), (("runId",), "other"),
                            (("sourceManifestSha256",), "d" * 64), (("wireContractSha256",), "d" * 64),
                            (("references",), []), (("references", "OPEN", "caseId"), "BLOCKED"),
                            (("references", "OPEN", "fixtureContextSha256"), "d" * 64),
                            (("references", "OPEN", "run", "kind"), "AUTHORED")):
            with self.subTest(path=path):
                self.reject_mutation(path, value)
        for context in ((RUN_ID.upper(), SOURCE_SHA, WIRE_SHA), (RUN_ID, "A" * 64, WIRE_SHA),
                        (RUN_ID, SOURCE_SHA, "d" * 64), (True, SOURCE_SHA, WIRE_SHA)):
            with self.subTest(context=context):
                with self.assertRaises(self.reader.ReaderError):
                    self.reader.validate_packet(self.packet(), *context, self.fixture_raw)

    def test_all_wire_objects_missing_extra_and_order(self):
        paths = [(), ("references",), ("sourceReview",)]
        for case_id in ("OPEN", "BLOCKED"):
            prefix = ("references", case_id)
            paths += [prefix, prefix + ("cleanup",), prefix + ("construction",), prefix + ("sourceGeometry",), prefix + ("run",)]
            paths += [prefix + ("run", key) for key in ("initial", "ordering", "outcome", "finalizers", "work")]
        paths.append(("references", "BLOCKED", "run", "terminalFailure"))
        for path in paths:
            for mutation in ("missing", "extra", "order"):
                with self.subTest(path=path, mutation=mutation):
                    packet = self.packet()
                    target = get_path(packet, path)
                    if mutation == "missing":
                        target.pop(next(iter(target)))
                    elif mutation == "extra":
                        target["unexpected"] = None
                    else:
                        first = next(iter(target))
                        value = target.pop(first)
                        target[first] = value
                    with self.assertRaises(self.reader.ReaderError):
                        self.admit(packet)

    def test_extraction_equivalence_and_source_only_generation(self):
        n = self.generator._source_nodes(self.run_source)
        r = self.generator._source_nodes(self.transport_source)
        derived_nodes = self.generator._source_nodes(self.derived)
        for name in self.generator.TRANSPORT_NAMES:
            with self.subTest(transport=name):
                if name == "_tree":
                    # This is the explicitly revised R2 boundary, with semantic
                    # path/budget tests above rather than a copied implementation.
                    row = next(item for item in self.proof["extracted"] if item["name"] == name)
                    self.assertFalse(row["byteEqual"])
                    self.assertEqual(row["sourceSha256"], hashlib.sha256(r[name]).hexdigest())
                    self.assertEqual(row["derivedSha256"], hashlib.sha256(derived_nodes[name]).hexdigest())
                else:
                    self.assertEqual(derived_nodes[name], r[name])
        for name in self.generator.RUN_NAMES + self.generator.RUN_CONSTANTS:
            with self.subTest(run=name):
                self.assertEqual(derived_nodes[name], n[name])
        for name in n:
            if name == "ConductorError" or name.startswith(("_CONDUCTOR_", "_conductor_")) or name == "validate_complete_layout":
                with self.subTest(conductor=name):
                    self.assertEqual(derived_nodes[name], n[name])
        # Independent suffix comparison protects the complete inherited outcome,
        # finalizer, work and ordinary terminal fee equations after seam edits.
        original = n["_validate_run"].split(b'    outcome = run["outcome"]\n', 1)[1]
        adapted = derived_nodes["validate_generic_fixture_run"].split(b'    outcome = run["outcome"]\n', 1)[1]
        adapted = adapted.replace(b'fixture_context["computedNetIds"]', b'captured["netIds"]')
        adapted = adapted.replace(b'    _natural_fixture_result(run, fixture_context, terminal)\n', b'')
        self.assertEqual(adapted, original)
        with mock.patch("builtins.exec", side_effect=AssertionError("parent/kernel execution")):
            source, proof = self.generator.derive(self.run_source, self.transport_source, self.fixture_raw)
        self.assertEqual(source, self.derived)
        self.assertTrue(proof["conductorClosureByteEqual"])
        self.assertNotIn(b"sys.path", source)

    def test_generator_pins_and_exact_args(self):
        for index in range(3):
            sources = [self.run_source, self.transport_source, self.fixture_raw]
            sources[index] += b"\n"
            with self.subTest(parent=index):
                with self.assertRaises(self.generator.OverlayError):
                    self.generator.derive(*sources)
        args = [part for flag in self.generator.CLI_FLAGS for part in (flag, str(Path(OPTIONS.generator).resolve()))]
        self.assertEqual(set(self.generator.parse_args(args)), set(self.generator.CLI_FLAGS))
        for bad in (args[:-1], args + ["--other", "x"], args[:-2] + args[:2], ["--other", "x"] + args[2:]):
            with self.subTest(args=bad):
                with self.assertRaises(self.generator.OverlayError):
                    self.generator.parse_args(bad)

    def test_generator_real_selected_checked_io(self):
        with tempfile.TemporaryDirectory(prefix="tsj-generic-reader-unit-") as temporary:
            root = Path(temporary).resolve()
            output = root / "output"
            output.mkdir()
            options = {"--run-reader": str(Path(OPTIONS.run_reader).resolve()),
                       "--transport-reader": str(Path(OPTIONS.transport_reader).resolve()),
                       "--fixture-spec": str(Path(OPTIONS.fixture_spec).resolve()),
                       "--base-generator": str(Path(OPTIONS.base_generator).resolve()),
                       "--io-helper": str(Path(OPTIONS.io_helper).resolve()), "--output-dir": str(output)}
            proof = self.generator.generate(options)
            self.assertEqual(set(item.name for item in output.iterdir()), set(self.generator.OUTPUT_NAMES))
            self.assertEqual((output / self.generator.OUTPUT_NAMES[0]).read_bytes(), self.derived)
            self.assertEqual(proof["derivedSha256"], hashlib.sha256(self.derived).hexdigest())
            self.assertEqual(proof["wireContractSha256"], WIRE_SHA)
            self.assertEqual(proof["treeTextPolicy"]["ordinaryMaximum"], 256)
            self.assertEqual(proof["treeTextPolicy"]["geometryMaximum"], 4096)
            self.assertNotIn(str(root).encode(), (output / "SOURCE_PROOF.json").read_bytes())
            with self.assertRaises(self.generator.OverlayError) as failure:
                self.generator.generate(options)
            self.assertEqual(failure.exception.code, "OUTPUT_NOT_EMPTY")
            for label, flag, code in (("base", "--base-generator", "BASE_HASH"), ("io", "--io-helper", "IO_HELPER_HASH"),
                                      ("run", "--run-reader", "RUN_READER_HASH"),
                                      ("transport", "--transport-reader", "TRANSPORT_READER_HASH"),
                                      ("fixture", "--fixture-spec", "FIXTURE_SPEC_HASH")):
                with self.subTest(bad_pin=label):
                    bad = root / (label + ".py")
                    bad.write_bytes(Path(options[flag]).read_bytes() + b"\n")
                    empty = root / (label + "-output")
                    empty.mkdir()
                    changed = dict(options, **{flag: str(bad), "--output-dir": str(empty)})
                    with self.assertRaises(ValueError) as failure:
                        self.generator.generate(changed)
                    self.assertEqual(getattr(failure.exception, "code", None), code)
                    self.assertEqual(list(empty.iterdir()), [])
            for alias in (str(root / "." / "output") + str(Path("/")) + ".", "relative-output"):
                with self.subTest(alias=alias):
                    changed = dict(options, **{"--output-dir": alias})
                    with self.assertRaises(self.generator.OverlayError) as failure:
                        self.generator.generate(changed)
                    self.assertEqual(failure.exception.code, "INPUT_CANONICAL")

    def test_readonly_cli_real_io_and_bad_controls(self):
        with tempfile.TemporaryDirectory(prefix="tsj-generic-reader-cli-unit-") as temporary:
            root = Path(temporary).resolve()
            script, packet_file = root / "reader.py", root / "packet.json"
            script.write_bytes(self.derived)
            packet_file.write_bytes(self.reader.canonical_line(self.packet()))
            args = [sys.executable, "-I", "-B", str(script), "--input", str(packet_file), "--run-id", RUN_ID,
                    "--source-sha", SOURCE_SHA, "--wire-sha", WIRE_SHA,
                    "--fixture-spec", str(Path(OPTIONS.fixture_spec).resolve()),
                    "--base-generator", str(Path(OPTIONS.base_generator).resolve()), "--io-helper", str(Path(OPTIONS.io_helper).resolve())]
            before = {item.name: item.read_bytes() for item in root.iterdir()}
            result = subprocess.run(args, capture_output=True, timeout=60, check=False)
            self.assertEqual((result.returncode, result.stderr), (0, b""))
            summary = self.reader.parse_packet(result.stdout)
            self.assertEqual(tuple(summary), SUMMARY_KEYS + ("inputSha256", "inputBytes"))
            self.assertEqual(summary["inputSha256"], hashlib.sha256(before["packet.json"]).hexdigest())
            self.assertEqual({item.name: item.read_bytes() for item in root.iterdir()}, before)
            for flag, value in (("--run-id", "bad"), ("--source-sha", "A" * 64), ("--wire-sha", "d" * 64)):
                with self.subTest(flag=flag):
                    changed = list(args)
                    changed[changed.index(flag) + 1] = value
                    result = subprocess.run(changed, capture_output=True, timeout=60, check=False)
                    self.assertEqual(result.returncode, 2)
                    self.assertEqual(result.stdout, b"")
                    self.assertNotIn(str(root).encode(), result.stderr)


def main():
    global OPTIONS
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("UNIT_PYTHON_ISOLATION_REQUIRED\n")
        return 2
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generator", default=str(Path(__file__).with_name("generate_generic_reference_reader.py")))
    parser.add_argument("--run-reader", required=True)
    parser.add_argument("--transport-reader", required=True)
    parser.add_argument("--fixture-spec", required=True)
    parser.add_argument("--base-generator", required=True)
    parser.add_argument("--io-helper", required=True)
    parser.add_argument("--packet")
    OPTIONS = parser.parse_args()
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(ReaderTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    raise SystemExit(main())
