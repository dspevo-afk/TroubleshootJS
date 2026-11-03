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
        "protocolVersion": 1, "sourceRevision": 16, "runId": RUN_ID,
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
            "protocolVersion": 1, "sourceRevision": 16, "status": "PASS",
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
    def test_source_schema_and_revision_are_rejected_before_source_reads(self):
        with tempfile.TemporaryDirectory(prefix="q100-zero-via-source-") as temporary:
            base = Path(temporary)
            spec_path = base / "input-spec.json"
            for schema, revision in (
                    ("p07-quality-scalar-input-v1", 16),
                    (reader.INPUT_SCHEMA, 15)):
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
