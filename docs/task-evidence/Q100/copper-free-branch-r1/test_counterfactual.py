"""Literal independent fixtures for the copper-free branch reader."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest


MODULE_PATH = Path(__file__).resolve().with_name("read_counterfactual.py")
SPEC = importlib.util.spec_from_file_location("branch_first_reader_under_test", MODULE_PATH)
reader = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = reader
SPEC.loader.exec_module(reader)


def pad(pad_id, net_id, x, y, bounds, role="HIGH_CURRENT"):
    land = {"padId": pad_id, "netId": net_id, "x": x, "y": y,
            "bounds": list(bounds), "attachment": "PLATED_THROUGH_HOLE",
            "layer": "TOP+BOTTOM"}
    source = {"padId": pad_id, "netId": net_id, "x": x, "y": y,
              "componentId": "fixture-component", "role": role}
    return land, source


def target_pads(extra=()):
    start = pad("S", "N", 0, 0, (-2, -2, 4, 4))
    root = pad("R", "N", 10, 0, (8, -2, 4, 4))
    lands = {"S": start[0], "R": root[0]}
    p07 = {"S": start[1], "R": root[1]}
    for item in extra:
        lands[item[0]["padId"]] = item[0]
        p07[item[1]["padId"]] = item[1]
    return {"byId": lands, "p07ById": p07, "roleByNet": {"N": "HIGH_CURRENT"}}


def straight_nodes():
    return [
        {"x": 0, "y": 0, "layer": "TOP", "direction": 4, "transitions": 0, "cost": 0},
        {"x": 10, "y": 0, "layer": "TOP", "direction": 1, "transitions": 0, "cost": 10},
    ]


def straight_snapshot():
    return {"startX": 0, "startY": 0, "rootX": 10, "rootY": 0}


def straight_trace():
    return {"sourceId": "route/top/s-r", "netId": "N", "startPadId": "S",
            "endPadId": "R", "layer": "TOP", "width": 13,
            "x": [0, 10], "y": [0, 0]}


def via_fixture():
    nodes = [
        {"x": 0, "y": 0, "layer": "TOP", "direction": 4, "transitions": 0, "cost": 0},
        {"x": 10, "y": 0, "layer": "TOP", "direction": 1, "transitions": 0, "cost": 10},
        {"x": 10, "y": 0, "layer": "BOTTOM", "direction": 4, "transitions": 1, "cost": 70},
        {"x": 20, "y": 0, "layer": "BOTTOM", "direction": 1, "transitions": 1, "cost": 82},
    ]
    snap = {"startX": 0, "startY": 0, "rootX": 20, "rootY": 0}
    pads = target_pads((pad("R", "N", 20, 0, (18, -2, 4, 4)),))
    traces = [
        {"sourceId": "route/top/s-v", "netId": "N", "startPadId": "S",
         "endPadId": None, "layer": "TOP", "width": 13,
         "x": [0, 10], "y": [0, 0]},
        {"sourceId": "route/bottom/v-r", "netId": "N", "startPadId": None,
         "endPadId": "R", "layer": "BOTTOM", "width": 13,
         "x": [10, 20], "y": [0, 0]},
    ]
    holes = [{"id": "via-1", "netId": "N", "kind": "VIA", "x": 10,
              "y": 0, "drillRadius": 2, "landRadius": 6}]
    return nodes, snap, pads, traces, holes


class BranchGeometryTests(unittest.TestCase):
    def assertReaderCode(self, code, function, *args, **kwargs):
        with self.assertRaises(reader.ReaderError) as caught:
            function(*args, **kwargs)
        self.assertEqual(caught.exception.code, "BF_" + code)

    def test_01_straight_node_and_physical_trace_agree(self):
        facts = reader._validate_node_chain(straight_nodes(), straight_snapshot())
        result = reader._validate_published_geometry(
            [straight_trace()], [], target_pads(), "N", "S", "R", facts, True)
        self.assertEqual((facts["planarSteps"], facts["totalCost"], result["traceLength"]), (1, 10, 10))

    def test_02_turn_cost_and_manhattan_length_are_recomputed(self):
        nodes = [
            {"x": 0, "y": 0, "layer": "TOP", "direction": 4, "transitions": 0, "cost": 0},
            {"x": 10, "y": 0, "layer": "TOP", "direction": 1, "transitions": 0, "cost": 10},
            {"x": 10, "y": -10, "layer": "TOP", "direction": 0, "transitions": 0, "cost": 55},
        ]
        facts = reader._validate_node_chain(nodes, {"startX": 0, "startY": 0, "rootX": 10, "rootY": -10})
        self.assertEqual((facts["planarSteps"], facts["turns"], facts["totalCost"]), (2, 1, 55))

    def test_03_via_transition_cost_and_two_face_trace_join(self):
        nodes, snap, pads, traces, holes = via_fixture()
        facts = reader._validate_node_chain(nodes, snap)
        result = reader._validate_published_geometry(traces, holes, pads, "N", "S", "R", facts, True)
        self.assertEqual((facts["viaPoints"], result["holeCount"], result["traceLength"]),
                         ([(10, 0)], 1, 20))

    def test_04_rejects_diagonal_node_step(self):
        nodes = straight_nodes()
        nodes[1]["y"] = 10
        self.assertReaderCode("NODE_PLANAR_STEP", reader._validate_node_chain, nodes,
                              {"startX": 0, "startY": 0, "rootX": 10, "rootY": 10})

    def test_05_rejects_non_grid_node_pitch(self):
        nodes = straight_nodes()
        nodes[1]["x"] = 11
        self.assertReaderCode("NODE_PLANAR_STEP", reader._validate_node_chain, nodes,
                              straight_snapshot())

    def test_06_rejects_direction_mismatch(self):
        nodes = straight_nodes()
        nodes[1]["direction"] = 0
        self.assertReaderCode("NODE_DIRECTION", reader._validate_node_chain, nodes,
                              straight_snapshot())

    def test_07_rejects_wrong_search_cost(self):
        nodes = straight_nodes()
        nodes[1]["cost"] = 9
        self.assertReaderCode("NODE_COST_ARITHMETIC", reader._validate_node_chain, nodes,
                              straight_snapshot())

    def test_08_rejects_bad_via_flip_cost(self):
        nodes, snap, _, _, _ = via_fixture()
        nodes[2]["cost"] = 69
        self.assertReaderCode("NODE_VIA_STEP", reader._validate_node_chain, nodes, snap)

    def test_09_rejects_wrong_cumulative_transition_count(self):
        nodes, snap, _, _, _ = via_fixture()
        nodes[2]["transitions"] = 0
        self.assertReaderCode("NODE_VIA_STEP", reader._validate_node_chain, nodes, snap)

    def test_10_rejects_boolean_as_coordinate(self):
        nodes = straight_nodes()
        nodes[1]["x"] = True
        self.assertReaderCode("NODE_FIELDS", reader._validate_node_chain, nodes, straight_snapshot())

    def test_11_rejects_wrong_profile_width(self):
        trace = straight_trace()
        trace["width"] = 11
        facts = reader._validate_node_chain(straight_nodes(), straight_snapshot())
        self.assertReaderCode("TRACE_IDENTITY_OR_WIDTH", reader._validate_published_geometry,
                              [trace], [], target_pads(), "N", "S", "R", facts, True)

    def test_12_rejects_foreign_pad_copper_contact(self):
        foreign = pad("F", "OTHER", 5, 0, (4, -1, 2, 2), "SIGNAL")
        facts = reader._validate_node_chain(straight_nodes(), straight_snapshot())
        self.assertReaderCode("FOREIGN_PAD_CONTACT", reader._validate_published_geometry,
                              [straight_trace()], [], target_pads((foreign,)), "N", "S", "R", facts, True)

    def test_13_rejects_foreign_via_land_contact(self):
        nodes, snap, pads, _, holes = via_fixture()
        foreign = pad("F", "OTHER", 10, 8, (8, 6, 4, 4), "SIGNAL")
        pads = target_pads((pad("R", "N", 20, 0, (18, -2, 4, 4)), foreign))
        facts = reader._validate_node_chain(nodes, snap)
        self.assertReaderCode("FOREIGN_VIA_LAND_CONTACT", reader._validate_published_geometry,
                              [], holes, pads, "N", "S", "R", facts, False)

    def test_14_rejects_trace_that_differs_from_search_path(self):
        trace = straight_trace()
        trace["endPadId"] = None
        trace["x"] = [0, 20]
        facts = reader._validate_node_chain(straight_nodes(), straight_snapshot())
        self.assertReaderCode("TRACE_NODE_PATH_MISMATCH", reader._validate_published_geometry,
                              [trace], [], target_pads(), "N", "S", "R", facts, True)

    def test_15_rejects_hole_without_matching_layer_transition(self):
        nodes = straight_nodes()
        facts = reader._validate_node_chain(nodes, straight_snapshot())
        hole = {"id": "via-1", "netId": "N", "kind": "VIA", "x": 10,
                "y": 0, "drillRadius": 2, "landRadius": 6}
        self.assertReaderCode("VIA_NODE_PATH_MISMATCH", reader._validate_published_geometry,
                              [straight_trace()], [hole], target_pads(), "N", "S", "R", facts, True)

    def test_16_closed_rectangle_contact_is_contact(self):
        self.assertTrue(reader._rectangles_touch((0, 0, 10, 10), (10, 2, 3, 3)))
        self.assertFalse(reader._rectangles_touch((0, 0, 10, 10), (11, 2, 3, 3)))

    def test_17_rejects_zero_area_pad_bounds(self):
        bad = pad("F", "OTHER", 10, 0, (10, -1, 0, 2), "SIGNAL")
        self.assertReaderCode("PAD_BOUNDS", reader._pad_rectangle, bad[0])


class CompileSourceTailTests(unittest.TestCase):
    study = "task/source/CopperFreeBranchStudy.java"
    fixtures = "task/source/CopperFreeBranchFixtures.java"
    canary = "task/source/CopperFreeBranchCanary.java"

    @staticmethod
    def old_source_paths():
        return [f"parent/source/File{index:04d}.java" for index in range(1085)]

    def test_18_requires_exact_study_and_fixture_tail(self):
        paths = self.old_source_paths() + [self.study, self.fixtures]
        reader._validate_compile_source_order(paths, [self.study, self.fixtures])

    def test_19_rejects_missing_fixture_source(self):
        paths = self.old_source_paths() + [self.study]
        with self.assertRaises(reader.ReaderError) as caught:
            reader._validate_compile_source_order(paths, [self.study, self.fixtures])
        self.assertEqual(caught.exception.code, "BF_SOURCE_COUNT")

    def test_20_rejects_wrong_or_extra_task_source(self):
        paths = self.old_source_paths() + [self.study, "task/source/CopperFreeSeamProbe.java"]
        with self.assertRaises(reader.ReaderError) as caught:
            reader._validate_compile_source_order(paths, [self.study, self.fixtures])
        self.assertEqual(caught.exception.code, "BF_SOURCE_ORDER")

    def test_21_rejects_reordered_fixture_tail(self):
        paths = self.old_source_paths() + [self.fixtures, self.study]
        with self.assertRaises(reader.ReaderError) as caught:
            reader._validate_compile_source_order(paths, [self.study, self.fixtures])
        self.assertEqual(caught.exception.code, "BF_SOURCE_ORDER")

    def test_22_accepts_only_exact_optional_canary_tail(self):
        paths = self.old_source_paths() + [self.study, self.fixtures, self.canary]
        reader._validate_compile_source_order(paths, [self.study, self.fixtures, self.canary])

    def test_23_rejects_unpinned_optional_canary(self):
        paths = self.old_source_paths() + [self.study, self.fixtures, self.canary]
        with self.assertRaises(reader.ReaderError) as caught:
            reader._validate_compile_source_order(paths, [self.study, self.fixtures])
        self.assertEqual(caught.exception.code, "BF_SOURCE_COUNT")

    def test_24_rejects_unsorted_parent_source_prefix(self):
        old = self.old_source_paths()
        old[0], old[1] = old[1], old[0]
        with self.assertRaises(reader.ReaderError) as caught:
            reader._validate_compile_source_order(old + [self.study, self.fixtures],
                                                  [self.study, self.fixtures])
        self.assertEqual(caught.exception.code, "BF_SOURCE_ORDER")


class HostMetadataParserTests(unittest.TestCase):
    def assertCode(self, code, raw):
        with self.assertRaises(reader.ReaderError) as caught:
            reader.host_document(raw, 4096)
        self.assertEqual(caught.exception.code, "BF_" + code)

    def test_33_accepts_pretty_powershell_crlf_json_without_rewriting_input(self):
        raw = (b'{\r\n  "FilePath": "C:\\\\jdk\\\\bin\\\\java.exe",\r\n'
               b'  "Arguments": ["-ea", "-cp"],\r\n'
               b'  "elapsedMs": 12.5,\r\n'
               b'  "Stdout": "line one\\r\\nline two"\r\n}\r\n')
        parsed = reader.host_document(raw, 4096)
        self.assertEqual(parsed["elapsedMs"], 12.5)
        self.assertEqual(parsed["Stdout"], "line one\r\nline two")
        self.assertTrue(raw.startswith(b"{\r\n"))

    def test_34_accepts_json_whitespace_around_single_document(self):
        self.assertEqual(reader.host_document(b' \t{ "ok" : true }\r\n', 4096), {"ok": True})

    def test_35_rejects_duplicate_keys(self):
        self.assertCode("JSON_DUPLICATE_KEY", b'{\r\n "ok":true, "ok":false\r\n}\r\n')

    def test_36_rejects_nonfinite_numbers(self):
        self.assertCode("JSON_NONFINITE", b'{"value":NaN}\r\n')
        self.assertCode("JSON_NONFINITE", b'{"value":1e999}\r\n')

    def test_37_rejects_trailing_document_and_bom(self):
        self.assertCode("JSON_INVALID", b'{}\r\n{}\r\n')
        self.assertCode("HOST_DOCUMENT_FRAMING", b'\xef\xbb\xbf{}\r\n')

    def test_38_native_compact_documents_still_reject_crlf(self):
        with self.assertRaises(reader.ReaderError) as caught:
            reader.strict_document(b'{\r\n "ok":true\r\n}\r\n', 4096)
        self.assertEqual(caught.exception.code, "BF_DOCUMENT_FRAMING")


class CompilerStderrTests(unittest.TestCase):
    def test_39_accepts_only_the_exact_jdk_compile_warning(self):
        expected = reader.COMPILE_STDERR
        self.assertEqual(len(expected.encode("utf-8")), 204)
        reader._validate_process_stderr(expected, 204, expected)

    def test_40_rejects_changed_warning_bytes_and_keeps_native_stderr_empty(self):
        expected = reader.COMPILE_STDERR
        variants = (
            (expected.replace("warning: [options]", "Warning: [options]"), 204, expected),
            (expected + "unexpected\r\n", 216, expected),
            (expected, 203, expected),
            (expected, 204, ""),
        )
        for stderr, count, allowed in variants:
            with self.subTest(count=count, allowed_empty=(allowed == "")):
                with self.assertRaises(reader.ReaderError) as caught:
                    reader._validate_process_stderr(stderr, count, allowed)
                self.assertEqual(caught.exception.code, "BF_PROCESS_STDERR")
        reader._validate_process_stderr("", 0)


class NativeConsoleAndPathTests(unittest.TestCase):
    def test_41_accepts_canonical_slash_alias_for_host_file_path(self):
        actual = Path(reader.__file__).resolve()
        alias = str(actual).replace("\\", "/")
        self.assertTrue(reader._same_host_path(alias, str(actual), "HOST_PATH"))
        self.assertFalse(reader._same_host_path(str(actual), str(actual.parent / "absent.exe"),
                                                "HOST_PATH"))

    def test_42_rejects_reparse_alias_for_host_file_path(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "tool.exe"
            target.write_bytes(b"pinned image")
            alias = root / "alias.exe"
            try:
                alias.symlink_to(target)
            except OSError:
                self.skipTest("file symlink creation is unavailable")
            with self.assertRaises(reader.ReaderError) as caught:
                reader._same_host_path(str(alias), str(target), "HOST_PATH")
            self.assertEqual(caught.exception.code, "BF_HOST_PATH")

    def test_43_accepts_exactly_one_compact_console_line_with_lf_or_crlf(self):
        expected = {"schema": "marker", "status": "ok"}
        self.assertEqual(reader.strict_console_line(b'{"schema":"marker","status":"ok"}\n',
                                                    "NATIVE_MARKER"), expected)
        self.assertEqual(reader.strict_console_line(b'{"schema":"marker","status":"ok"}\r\n',
                                                    "NATIVE_MARKER"), expected)

    def test_44_rejects_extra_lines_missing_terminator_and_noncompact_console_json(self):
        for raw in (b'{"schema":"marker"}', b'{"schema":"marker"}\n\n',
                    b'{"schema":"marker"}\r\n\r\n',
                    b'{"schema":"marker"}\n{}\n'):
            with self.subTest(raw=raw):
                with self.assertRaises(reader.ReaderError) as caught:
                    reader.strict_console_line(raw, "NATIVE_MARKER")
                self.assertEqual(caught.exception.code, "BF_NATIVE_MARKER_FRAMING")
        with self.assertRaises(reader.ReaderError) as caught:
            reader.strict_console_line(b'{ "schema":"marker" }\r\n', "NATIVE_MARKER")
        self.assertEqual(caught.exception.code, "BF_NATIVE_MARKER_COMPACT")


class P07BranchIdentityTests(unittest.TestCase):
    @staticmethod
    def branches(target_pass=2, earlier_pass2=76):
        rows = []
        for ordinal in range(308):
            pass_number = 2 if ordinal >= 307 - earlier_pass2 and ordinal < 307 else 0
            if ordinal == 307:
                pass_number = target_pass
            rows.append({"ordinal": ordinal, "snapshot": {"pass": pass_number}})
        return rows

    def test_45_global_branch_ordinal_and_pass_local_lineage_are_distinct(self):
        branch, lineage = reader._select_authored_branch(self.branches())
        self.assertEqual(branch["ordinal"], 307)
        self.assertEqual(lineage, 77)

    def test_46_rejects_wrong_pass_local_lineage(self):
        with self.assertRaises(reader.ReaderError) as caught:
            reader._select_authored_branch(self.branches(earlier_pass2=75))
        self.assertEqual(caught.exception.code, "BF_OP1_TARGET_LINEAGE")

    def test_47_rejects_target_not_in_pass_two(self):
        with self.assertRaises(reader.ReaderError) as caught:
            reader._select_authored_branch(self.branches(target_pass=1))
        self.assertEqual(caught.exception.code, "BF_OP1_TARGET_LINEAGE")


class ValidatorScopeTests(unittest.TestCase):
    @staticmethod
    def validators(routing="NOT_APPLICABLE", two_layer="PASS", quality="PASS",
                   copper="PASS", endpoints=True, all_net="NOT_RUN"):
        return {"routingGeometry": routing, "twoLayerRules": two_layer,
                "routeQuality": quality, "copperGraph": copper,
                "endpointsConnected": endpoints, "allNetContinuity": all_net}

    def assertScopeCode(self, code, disposition, has_candidate, validators):
        with self.assertRaises(reader.ReaderError) as caught:
            reader._validate_validator_scope(disposition, has_candidate, validators)
        self.assertEqual(caught.exception.code, "BF_" + code)

    def test_25_found_scope_runs_only_partial_checks(self):
        reader._validate_validator_scope("FOUND", True, self.validators())

    def test_26_found_must_not_claim_whole_board_geometry_pass(self):
        self.assertScopeCode("FOUND_VALIDATOR_SCOPE", "FOUND", True,
                             self.validators(routing="PASS"))

    def test_27_found_requires_partial_checks_and_endpoint(self):
        self.assertScopeCode("FOUND_VALIDATOR_SCOPE", "FOUND", True,
                             self.validators(two_layer="NOT_RUN"))
        self.assertScopeCode("FOUND_VALIDATOR_SCOPE", "FOUND", True,
                             self.validators(endpoints=False))

    def test_28_no_path_leaves_route_validators_unrun_and_endpoint_null(self):
        validators = self.validators(routing="NOT_RUN", two_layer="NOT_RUN", quality="NOT_RUN",
                                     copper="NOT_RUN", endpoints=None)
        reader._validate_validator_scope("NO_PATH", False, validators)

    def test_29_no_path_rejects_partial_validator_or_false_endpoint_claim(self):
        self.assertScopeCode("REJECTED_VALIDATOR_SCOPE", "NO_PATH", False,
                             self.validators(routing="NOT_RUN", two_layer="NOT_RUN",
                                             quality="NOT_RUN", copper="NOT_RUN", endpoints=False))
        self.assertScopeCode("REJECTED_VALIDATOR_SCOPE", "NO_PATH", False,
                             self.validators(routing="NOT_RUN", two_layer="PASS",
                                             quality="NOT_RUN", copper="NOT_RUN", endpoints=None))

    def test_30_quality_rejection_with_candidate_does_not_claim_validity(self):
        validators = self.validators(routing="NOT_RUN", two_layer="NOT_RUN", quality="NOT_RUN",
                                     copper="NOT_RUN", endpoints=None)
        reader._validate_validator_scope("QUALITY_REJECTED", True, validators)
        self.assertScopeCode("REJECTED_CANDIDATE_SCOPE", "QUALITY_REJECTED", False, validators)

    def test_31_cap_rejection_can_have_or_lack_search_candidate_but_never_validates_route(self):
        validators = self.validators(routing="NOT_RUN", two_layer="NOT_RUN", quality="NOT_RUN",
                                     copper="NOT_RUN", endpoints=None)
        reader._validate_validator_scope("CAP_REJECTED", False, validators)
        reader._validate_validator_scope("CAP_REJECTED", True, validators)

    def test_32_all_net_continuity_must_remain_unrun(self):
        self.assertScopeCode("VALIDATOR_SCOPE", "FOUND", True,
                             self.validators(all_net="PASS"))

def main() -> int:
    if sys.argv[1:] != ["--literal"]:
        print("usage: test_counterfactual.py --literal", file=sys.stderr)
        return 2
    suite = unittest.TestSuite((
        unittest.defaultTestLoader.loadTestsFromTestCase(BranchGeometryTests),
        unittest.defaultTestLoader.loadTestsFromTestCase(CompileSourceTailTests),
        unittest.defaultTestLoader.loadTestsFromTestCase(HostMetadataParserTests),
        unittest.defaultTestLoader.loadTestsFromTestCase(CompilerStderrTests),
        unittest.defaultTestLoader.loadTestsFromTestCase(NativeConsoleAndPathTests),
        unittest.defaultTestLoader.loadTestsFromTestCase(P07BranchIdentityTests),
        unittest.defaultTestLoader.loadTestsFromTestCase(ValidatorScopeTests),
    ))
    result = unittest.TextTestRunner(stream=sys.stderr, verbosity=1).run(suite)
    if not result.wasSuccessful():
        return 1
    print(f'PASS_COUNTERFACTUAL_LITERAL {{"testCount":{result.testsRun}}}')
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
