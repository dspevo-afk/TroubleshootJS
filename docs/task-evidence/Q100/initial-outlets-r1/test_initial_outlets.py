"""Literal tests for the initial outlet reader's pure validators."""
from __future__ import annotations

import copy
import importlib.util
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("initial_outlet_reader", ROOT / "read_initial_outlets.py")
reader = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(reader)


def fixture_inputs():
    pads = [
        {"padId": "A1", "netId": "NET_A", "x": 0, "y": 0, "role": "HIGH_CURRENT"},
        {"padId": "A2", "netId": "NET_A", "x": 10, "y": 0, "role": "HIGH_CURRENT"},
        {"padId": "A3", "netId": "NET_A", "x": -10, "y": 0, "role": "HIGH_CURRENT"},
        {"padId": "B1", "netId": "NET_B", "x": 0, "y": 20, "role": "SIGNAL"},
        {"padId": "B2", "netId": "NET_B", "x": 0, "y": 30, "role": "SIGNAL"},
        {"padId": "C1", "netId": "NET_C", "x": 40, "y": 40, "role": "CONTROL"},
    ]
    branches = [
        {"ordinal": 0, "group": 0,
         "snapshot": {"pass": 2, "netId": "NET_A", "rootPadId": "A1", "startPadId": "A2"}},
        {"ordinal": 1, "group": 0,
         "snapshot": {"pass": 2, "netId": "NET_B", "rootPadId": "B1", "startPadId": "B2"}},
    ]
    role_by_net = {"NET_A": "HIGH_CURRENT", "NET_B": "SIGNAL", "NET_C": "CONTROL"}
    source_geometry = "a" * 64

    def orientation(top, bottom):
        top_count = top.bit_count()
        bottom_count = bottom.bit_count()
        return {"topMask": top, "bottomMask": bottom,
                "topCount": top_count, "bottomCount": bottom_count,
                "totalCount": top_count + bottom_count}

    rows = [
        {"netId": "NET_A", "padIds": ["A1", "A2", "A3"], "paired": True,
         "rootPadId": "A1", "startPadId": "A2", "role": "HIGH_CURRENT", "traceWidth": 13,
         "forward": orientation(1, 4), "reverse": orientation(8, 2), "value": 2},
        {"netId": "NET_B", "padIds": ["B1", "B2"], "paired": True,
         "rootPadId": "B1", "startPadId": "B2", "role": "SIGNAL", "traceWidth": 9,
         "forward": orientation(0, 3), "reverse": orientation(4, 0), "value": 1},
        {"netId": "NET_C", "padIds": ["C1"], "paired": False,
         "rootPadId": None, "startPadId": None, "role": "CONTROL", "traceWidth": 9,
         "forward": None, "reverse": None, "value": None},
    ]
    histogram = {str(value): int(value in (1, 2)) for value in range(9)}
    measurement = {
        "policy": "FULLER_TWO_LAYER", "pass": 2, "widthProfile": "ROLE_WIDTHS", "grid": 10,
        "primaryFace": "BOTTOM", "oppositeFace": "TOP", "faceOrder": ["TOP", "BOTTOM"],
        "directionOrder": ["N", "E", "S", "W"],
        "directionBits": {"N": 1, "E": 2, "S": 4, "W": 8},
        "queriesPerPair": 16, "queryCount": 32, "singletonNetCount": 1,
        "pairedNetCount": 2, "rows": rows,
        "distribution": {"pairedValueHistogram": histogram,
                         "distinctValueCount": 2, "varies": True},
        "sourceGeometryBeforeSha256": source_geometry,
        "sourceGeometryAfterSha256": source_geometry,
        "coldReplayMatches": None,
    }
    return pads, branches, role_by_net, measurement, {"traceWidthProfile": "ROLE_WIDTHS"}, source_geometry


class MeasurementTests(unittest.TestCase):
    def test_01_independent_nearest_then_lexical_pair_and_histogram(self):
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        summary = reader.validate_measurement(measurement, pads, branches, candidate,
                                              roles, geometry, geometry)
        self.assertEqual(summary["pairedNetCount"], 2)
        self.assertEqual(summary["singletonNetCount"], 1)
        self.assertEqual(summary["queryCount"], 32)
        self.assertEqual(summary["pairedValueHistogram"],
                         {str(value): int(value in (1, 2)) for value in range(9)})
        self.assertTrue(summary["varies"])
        self.assertEqual(summary["expectedPairs"]["NET_A"]["startPadId"], "A2")

    def test_02_zero_outlets_remain_a_valid_diagnostic_value(self):
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        for row in measurement["rows"][:2]:
            row["forward"] = {"topMask": 0, "bottomMask": 0,
                               "topCount": 0, "bottomCount": 0, "totalCount": 0}
            row["reverse"] = copy.deepcopy(row["forward"])
            row["value"] = 0
        measurement["distribution"]["pairedValueHistogram"] = {
            str(value): int(value == 0) * 2 for value in range(9)}
        measurement["distribution"]["distinctValueCount"] = 1
        measurement["distribution"]["varies"] = False
        summary = reader.validate_measurement(measurement, pads, branches, candidate,
                                              roles, geometry, geometry)
        self.assertEqual(summary["pairedValueHistogram"]["0"], 2)
        self.assertFalse(summary["varies"])

    def test_03_incomplete_net_coverage_is_rejected(self):
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        measurement["rows"].pop()
        with self.assertRaises(reader.ReaderError) as caught:
            reader.validate_measurement(measurement, pads, branches, candidate,
                                        roles, geometry, geometry)
        self.assertEqual(caught.exception.code, "IO_MEASUREMENT_ROW_COUNT")

    def test_04_duplicate_or_wrong_net_order_is_rejected(self):
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        measurement["rows"][1] = copy.deepcopy(measurement["rows"][0])
        with self.assertRaises(reader.ReaderError) as caught:
            reader.validate_measurement(measurement, pads, branches, candidate,
                                        roles, geometry, geometry)
        self.assertEqual(caught.exception.code, "IO_MEASUREMENT_ROW_ORDER")

    def test_05_noncanonical_branch_start_is_rejected(self):
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        branches[0]["snapshot"]["startPadId"] = "A3"
        with self.assertRaises(reader.ReaderError) as caught:
            reader.validate_measurement(measurement, pads, branches, candidate,
                                        roles, geometry, geometry)
        self.assertEqual(caught.exception.code, "IO_PAIR_FIRST_BRANCH_BINDING")

    def test_06_unvisited_paired_net_is_still_derived_from_pad_census(self):
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        branches.pop(1)
        summary = reader.validate_measurement(measurement, pads, branches, candidate,
                                              roles, geometry, geometry)
        self.assertEqual(summary["expectedPairs"]["NET_B"]["startPadId"], "B2")
        self.assertIsNone(summary["expectedPairs"]["NET_B"]["branch"])

    def test_07_bad_direction_order_and_bit_assignment_are_rejected(self):
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        measurement["directionOrder"] = ["E", "N", "S", "W"]
        with self.assertRaises(reader.ReaderError) as caught:
            reader.validate_measurement(measurement, pads, branches, candidate,
                                        roles, geometry, geometry)
        self.assertEqual(caught.exception.code, "IO_MEASUREMENT_POLICY")
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        measurement["directionBits"]["S"] = 8
        with self.assertRaises(reader.ReaderError) as caught:
            reader.validate_measurement(measurement, pads, branches, candidate,
                                        roles, geometry, geometry)
        self.assertEqual(caught.exception.code, "IO_DIRECTION_BITS")

    def test_08_mask_popcounts_and_orientation_totals_are_reconciled(self):
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        measurement["rows"][0]["forward"]["topCount"] = 0
        with self.assertRaises(reader.ReaderError) as caught:
            reader.validate_measurement(measurement, pads, branches, candidate,
                                        roles, geometry, geometry)
        self.assertEqual(caught.exception.code, "IO_MEASUREMENT_FORWARD")

    def test_09_role_width_and_singleton_nulls_are_enforced(self):
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        measurement["rows"][0]["traceWidth"] = 11
        with self.assertRaises(reader.ReaderError) as caught:
            reader.validate_measurement(measurement, pads, branches, candidate,
                                        roles, geometry, geometry)
        self.assertEqual(caught.exception.code, "IO_MEASUREMENT_PAIR_IDENTITY")
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        measurement["rows"][2]["forward"] = {"topMask": 0}
        with self.assertRaises(reader.ReaderError) as caught:
            reader.validate_measurement(measurement, pads, branches, candidate,
                                        roles, geometry, geometry)
        self.assertEqual(caught.exception.code, "IO_MEASUREMENT_SINGLETON_FIELDS")

    def test_10_query_count_histogram_and_source_hash_are_checked(self):
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        measurement["queryCount"] = 31
        with self.assertRaises(reader.ReaderError) as caught:
            reader.validate_measurement(measurement, pads, branches, candidate,
                                        roles, geometry, geometry)
        self.assertEqual(caught.exception.code, "IO_MEASUREMENT_COUNTS")
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        measurement["distribution"]["pairedValueHistogram"]["2"] = 0
        with self.assertRaises(reader.ReaderError) as caught:
            reader.validate_measurement(measurement, pads, branches, candidate,
                                        roles, geometry, geometry)
        self.assertEqual(caught.exception.code, "IO_DISTRIBUTION_HISTOGRAM")
        pads, branches, roles, measurement, candidate, geometry = fixture_inputs()
        with self.assertRaises(reader.ReaderError) as caught:
            reader.validate_measurement(measurement, pads, branches, candidate,
                                        roles, geometry, "b" * 64)
        self.assertEqual(caught.exception.code, "IO_MEASUREMENT_SOURCE_GEOMETRY")


class WorkAndCleanupTests(unittest.TestCase):
    @staticmethod
    def work():
        return {"cutoff": "PRE_RESULT_SERIALIZATION", "elapsedNanos": 100,
                "units": 898, "groups": 11, "maxGroupNanos": 10,
                "maximumUnitsPerGroup": 896, "originalP07OwnerSteps": 0,
                "originalDiagnosticUnits": 0, "originalCallbackUnits": 1,
                "metricUnits": 896, "preExportUnits": 1, "metricGroups": 9,
                "preExportGroups": 1, "queryCount": 32}

    @staticmethod
    def cleanup():
        return {"originalOwnersReleased": True, "contextRestored": True,
                "querySearchReleased": True, "querySessionReleased": True,
                "queryLayoutReleased": True, "queryRoutersReleased": True,
                "sourceTemplateReleased": True, "sourceBoardReleased": True,
                "allReferencesReleased": True, "originalCleanupNanos": 3,
                "queryCleanupNanos": 5, "sourceReleaseNanos": 7,
                "combinedCleanupNanos": 15}

    def test_10_work_arithmetic_and_query_charge(self):
        operation = {"groupCount": 1, "maxFullGroupNanos": 10}
        summary = {"queryCount": 32, "netCount": 3}
        valid = self.work()
        self.assertIs(reader._validate_work(valid, operation, 0, 0, 1, 1, summary), valid)
        bad = self.work()
        bad["units"] += 1
        with self.assertRaises(reader.ReaderError) as caught:
            reader._validate_work(bad, operation, 0, 0, 1, 1, summary)
        self.assertEqual(caught.exception.code, "IO_WORK_ACCOUNTING")
        bad = self.work()
        bad["metricUnits"] = 31
        bad["units"] = 33
        with self.assertRaises(reader.ReaderError) as caught:
            reader._validate_work(bad, operation, 0, 0, 1, 1, summary)
        self.assertEqual(caught.exception.code, "IO_WORK_ACCOUNTING")
        bad = self.work()
        bad["metricGroups"] -= 1
        bad["groups"] -= 1
        with self.assertRaises(reader.ReaderError) as caught:
            reader._validate_work(bad, operation, 0, 0, 1, 1, summary)
        self.assertEqual(caught.exception.code, "IO_WORK_ACCOUNTING")

    def test_pre_export_tree_walk_count_is_independent_of_work_arithmetic(self):
        tree = {"a": 1, "b": [2, {}, []], "c": {}}
        self.assertEqual(reader._count_json_nodes(tree), 8)

        work = WorkAndCleanupTests.work()
        doc = {"schema": "fixture", "work": work}
        expected = reader._count_json_nodes(doc)
        work["preExportUnits"] = expected
        work["units"] = 897 + expected
        operation = {"groupCount": 1, "maxFullGroupNanos": 10}
        summary = {"queryCount": 32, "netCount": 3}
        reader._validate_work(work, operation, 0, 0, 1, 1, summary)
        self.assertEqual(reader._validate_pre_export_units(doc, work), expected)

        work["preExportUnits"] += 1
        work["units"] += 1
        reader._validate_work(work, operation, 0, 0, 1, 1, summary)
        with self.assertRaises(reader.ReaderError) as caught:
            reader._validate_pre_export_units(doc, work)
        self.assertEqual(caught.exception.code, "IO_PREEXPORT_TREE_UNITS")

    def test_11_cleanup_requires_all_releases_and_exact_sum(self):
        valid = self.cleanup()
        operation = {"cleanupNanos": 3}
        self.assertIs(reader._validate_cleanup(valid, operation), valid)
        bad = self.cleanup()
        bad["combinedCleanupNanos"] = 8
        with self.assertRaises(reader.ReaderError) as caught:
            reader._validate_cleanup(bad, operation)
        self.assertEqual(caught.exception.code, "IO_CLEANUP_PROOF")
        bad = self.cleanup()
        bad["queryRoutersReleased"] = False
        with self.assertRaises(reader.ReaderError) as caught:
            reader._validate_cleanup(bad, operation)
        self.assertEqual(caught.exception.code, "IO_CLEANUP_PROOF")
        bad = self.cleanup()
        bad["sourceReleaseNanos"] = True
        with self.assertRaises(reader.ReaderError) as caught:
            reader._validate_cleanup(bad, operation)
        self.assertEqual(caught.exception.code, "IO_CLEANUP_PROOF")


class OriginalGroupAccountingTests(unittest.TestCase):
    @staticmethod
    def inputs():
        groups = [
            {"ordinal": 0, "p07Steps": 2, "diagnosticUnits": 1,
             "elapsedBeforeOwnLineNanos": 10},
            {"ordinal": 1, "p07Steps": 3, "diagnosticUnits": 1,
             "elapsedBeforeOwnLineNanos": 20},
        ]
        operation = {"groupCount": 2, "p07OwnerSteps": 5,
                     "groupP07StepsSum": 5,
                     "diagnosticUnits": 2, "callbackUnits": 4,
                     "maximumUnitsPerGroup": 8, "maxFullGroupNanos": 20}
        return operation, {"groups": groups, "p07OwnerSteps": 5}, {"groupOrdinal": 1}

    def test_12_original_group_steps_reconcile_to_arm_total(self):
        operation, parsed, reference = self.inputs()
        self.assertEqual(reader._derive_original_units(operation, parsed, reference),
                         (5, 2, 4, 20, 8))
        operation, parsed, reference = self.inputs()
        operation["groupP07StepsSum"] = 6
        with self.assertRaises(reader.ReaderError) as caught:
            reader._derive_original_units(operation, parsed, reference)
        self.assertEqual(caught.exception.code, "IO_ORIGINAL_GROUP_ACCOUNTING")
        operation, parsed, reference = self.inputs()
        parsed["p07OwnerSteps"] = 6
        with self.assertRaises(reader.ReaderError) as caught:
            reader._derive_original_units(operation, parsed, reference)
        self.assertEqual(caught.exception.code, "IO_ORIGINAL_GROUP_ACCOUNTING")


class FramingTests(unittest.TestCase):
    def test_12_strict_json_line_rejects_noncanonical_transport(self):
        self.assertEqual(reader._strict_json_line(b'{"ok":true}\n', 128, "FRAMING"),
                         {"ok": True})
        for raw in (b'{"x":1,"x":2}\n', b'{"x":NaN}\n', b'{ "x":1}\n',
                    b'\xef\xbb\xbf{"x":1}\n', b'{"x":1}\r\n', b'{"x":1}\n\n',
                    b'{"x":1e9999}\n'):
            with self.subTest(raw=raw):
                with self.assertRaises(reader.ReaderError):
                    reader._strict_json_line(raw, 128, "FRAMING")

    def test_13_native_line_limit_is_one_mib_not_the_receipt_limit(self):
        exact = b'{"x":"' + b"a" * (reader.MAX_LINE - 9) + b'"}\n'
        self.assertEqual(len(exact), reader.MAX_LINE)
        self.assertEqual(len(reader._strict_json_line(exact, reader.MAX_JSON, "FRAMING")["x"]),
                         reader.MAX_LINE - 9)
        oversized = exact[:-3] + b"a" + exact[-3:]
        self.assertEqual(len(oversized), reader.MAX_LINE + 1)
        with self.assertRaises(reader.ReaderError) as caught:
            reader._strict_json_line(oversized, reader.MAX_JSON, "FRAMING")
        self.assertEqual(caught.exception.code, "IO_FRAMING")


def main() -> int:
    if sys.argv[1:] != ["--literal"]:
        print("usage: test_initial_outlets.py --literal", file=sys.stderr)
        return 2
    suite = unittest.TestSuite((
        unittest.defaultTestLoader.loadTestsFromTestCase(MeasurementTests),
        unittest.defaultTestLoader.loadTestsFromTestCase(WorkAndCleanupTests),
        unittest.defaultTestLoader.loadTestsFromTestCase(OriginalGroupAccountingTests),
        unittest.defaultTestLoader.loadTestsFromTestCase(FramingTests),
    ))
    result = unittest.TextTestRunner(stream=sys.stderr, verbosity=1).run(suite)
    if not result.wasSuccessful():
        return 1
    print(f'PASS_INITIAL_OUTLETS_LITERAL {{"testCount":{result.testsRun}}}')
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
