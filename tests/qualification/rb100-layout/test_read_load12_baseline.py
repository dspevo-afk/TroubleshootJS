"""Independent fixtures for the LOAD12 detached-baseline reader."""
from __future__ import annotations

import hashlib
import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path

import read_load12_baseline as reader
import test_read_quality_scalars as scalar_fixtures


RUN_ID = "d1c10000-0000-4000-8000-000000000001"
SHA_A = "a" * 64
SHA_B = "b" * 64
SHA_C = "c" * 64


def _capture():
    return {
        "pathTupleSha256": reader.EXPECTED_PATH_TUPLE_SHA256,
        "goalSha256": reader.EXPECTED_GOAL_SHA256,
        "nodeCount": 222,
        "planarLength": 2170,
        "bends": 2,
        "flips": 4,
        "seedSha256": SHA_A,
        "netOrderSha256": SHA_B,
        "prefixRowsSha256": SHA_C,
        "preGeometrySha256": reader.EXPECTED_PRE_GEOMETRY_SHA256,
        "postGeometrySha256": "d" * 64,
        "publicationSha256": "e" * 64,
        "pre": {"holeCount": 17, "viaCount": 17, "traceCount": 88},
        "post": {
            "holeCount": 21, "viaCount": 21, "traceCount": 93,
            "createdViaCount": 4, "createdTraceCount": 5,
        },
    }


def _work():
    return {
        "sourceOperation": {
            "operationMs": 45_010, "workUnits": 120, "maxUnitMs": 5_000,
            "cleanupMs": 4, "manualGroups": 6, "manualSliceSteps": 75,
            "preAdvanceCalls": 2, "postAdvanceCalls": 2,
        },
        "copy": {
            "advanceCalls": 2, "workSteps": 100, "copiedEntries": 75,
            "lastWork": 50, "observerChecks": 4,
        },
        "replay": {
            "toPreCalls": 100, "toPreWork": 100,
            "toPostCalls": 1, "toPostWork": 100,
        },
        "cloneCursor": {
            "preAdvanceCalls": 2, "preWork": 100,
            "postAdvanceCalls": 2, "postWork": 100,
        },
        "sourceCursor": {
            "preAdvanceCalls": 2, "preWork": 100,
            "postAdvanceCalls": 2, "postWork": 100,
        },
        "limits": {
            "operationMs": 90_000, "workUnits": 640, "unitMs": 5_000,
            "cleanupMs": 120_000, "quantum": 32_768,
            "maximumVias": 48, "maximumTransitions": 4,
        },
    }


def _event():
    capture = _capture()
    return {
        "protocolVersion": 1,
        "status": "PASS",
        "runId": RUN_ID,
        "operationId": "QUALITY_PROBE",
            "scope": "BASELINE48_ONLY",
            "purpose": "DETACHED_LOAD12_REPLAY",
        "selector": {
            "rootSeed": reader.ROOT_SEED,
            "placementSeed": reader.PLACEMENT_SEED,
            "routingSeed": reader.ROUTING_SEED,
            "routeRequestIndex": 0,
            "selectedPlacementIndex": 0,
            "selectedPlacementAttempt": 4,
            "pass": 1,
            "netPosition": 3,
            "nextFetch": 4,
            "netId": "LOAD12",
            "branchOrdinal": 73,
            "searchBranches": 73,
            "primaryLayer": "BOTTOM",
            "grid": {"width": 478, "height": 309, "cells": 147702,
                     "minX": 30, "minY": 30, "pitch": 10},
            "startPadId": "JLOAD.1",
            "rootPadId": "CSTORE_E.1",
            "prefix": {"vias": 17, "traces": 88},
        },
        "original": capture,
        "clone": json.loads(json.dumps(capture)),
        "work": _work(),
        "comparison": {
            **{name: True for name in reader.COMPARISON_TRUE_FIELDS},
            **{name: False for name in reader.COMPARISON_FALSE_FIELDS},
        },
    }


def _receipt_lines(event=None):
    event = _event() if event is None else event
    lines = []
    for line in scalar_fixtures._receipt_lines()[:7]:
        marker, body = line.split(" ", 1)
        record = json.loads(body)
        if "sourceRevision" in record:
            record["sourceRevision"] = reader.SOURCE_REVISION
        lines.append(marker + " " +
                     json.dumps(record, sort_keys=True, separators=(",", ":")))
    lines.append(reader.EVENT_MARKER + " " +
                 json.dumps(event, sort_keys=True, separators=(",", ":")))
    lines.append(reader.SUMMARY_PREFIX + "17")
    return lines


def _rewrite_record(lines, index, mutate):
    marker, body = lines[index].split(" ", 1)
    record = json.loads(body)
    mutate(record)
    lines[index] = marker + " " + json.dumps(record, sort_keys=True,
                                             separators=(",", ":"))


def _valid_native_run(stdout=b"console", receipt=b"receipt", stderr=b""):
    scalar, _ledger, _capture, _hashes = reader._load_runtime_helpers()
    suite = reader.SUITE
    return {
        "schema": reader.RUN_SCHEMA,
        "source": "candidate",
        "suite": suite,
        "cohortId": None,
        "command": {
            "entry": reader.ENTRY_SCRIPT, "suite": suite, "suites": [suite],
            "args": [], "environment": {},
        },
        "layoutVersion": reader.LAYOUT_VERSION,
        "dependencyEpoch": reader.DEPENDENCY_EPOCH,
        "sourceSnapshotRevision": reader.SOURCE_REVISION,
        "inputCount": reader.SOURCE_INPUT_COUNT,
        "inputBytes": 123,
        "inputPinsSha256": SHA_A,
        "inputAudit": "PASS_BEFORE_AND_AFTER",
        "exitCode": 0,
        "nativeExitCode": 0,
        "terminationProven": True,
        "cleanupStatus": "PASS",
        "cleanupElapsedMs": None,
        "viaCost": 60,
        "nativeLimitMs": 480_000,
        "outerLimitMs": 1_800_000,
        "processId": 1234,
        "processStartTicks": "638978400000000000",
        "elapsedSeconds": 25.0,
        "requestedExecutable": "powershell.exe",
        "stdoutBytes": len(stdout), "stdoutSha256": hashlib.sha256(stdout).hexdigest(),
        "stderrBytes": len(stderr), "stderrSha256": hashlib.sha256(stderr).hexdigest(),
        "receiptBytes": len(receipt), "receiptSha256": hashlib.sha256(receipt).hexdigest(),
        "target": reader.TARGET,
    }


class InputSpecTests(unittest.TestCase):
    def _spec(self):
        return {
            "schema": reader.INPUT_SCHEMA,
            "target": "LOAD12",
            "purpose": "DETACHED_BASELINE48",
            "suite": "Rb100Load12DetachedBaselineContractTest",
            "sourceRoot": "source",
            "pinsPath": "pins.json",
            "pinsSha256": SHA_A,
            "inputCount": 1079,
            "inputBytes": 123,
            "sourceSnapshotRevision": 20,
            "resultPath": "result.json",
            "stdoutPath": "stdout.txt",
            "receiptPath": "receipt.txt",
            "stderrPath": "stderr.txt",
        }

    def test_schema_target_purpose_suite_and_legacy_projection_are_closed(self):
        legacy = reader._validate_input_spec_identity(self._spec())
        self.assertEqual(set(legacy), reader.LEGACY_INPUT_SPEC_KEYS)
        self.assertNotIn("target", legacy)
        self.assertNotIn("purpose", legacy)
        self.assertNotIn("suite", legacy)

        for changes in (
                {"schema": "p07-quality-zero-via-input-v5"},
                {"target": "D_SENSE"},
                {"purpose": "ZERO_VIA_GRAPH"},
                {"suite": "Rb100Load12ZeroViaGraphContractTest"},
                {"sourceSnapshotRevision": 5},
                {"sourceSnapshotRevision": True},
                {"inputCount": 1067},
                {"inputCount": 1078},
                {"inputCount": True},
                {"inputBytes": 0},
                {"inputBytes": True},
                {"pinsSha256": "not-a-hash"},
                {"unexpected": False}):
            spec = self._spec()
            spec.update(changes)
            with self.subTest(changes=changes):
                with self.assertRaises(reader.Load12BaselineInputError):
                    reader._validate_input_spec_identity(spec)

    def test_source_epoch_is_the_frozen_1079_223_census(self):
        self.assertEqual(reader.SOURCE_REVISION, 20)
        self.assertEqual(reader.SOURCE_INPUT_COUNT, 1079)
        self.assertEqual(reader.SOURCE_JAVA_COUNT, 850)
        self.assertEqual(reader.CONTRACT_JAVA_COUNT, 223)
        self.assertIn("tests/contracts/Rb100Load12DetachedBaselineContractTest.java",
                      reader.REQUIRED_NATIVE_INPUTS)
        self.assertIn("tests/contracts/P07AcceptedBranchCaptureDiagnostics.java",
                      reader.REQUIRED_NATIVE_INPUTS)


class EventTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        reader._load_runtime_helpers()

    def test_independent_baseline_fixture_passes(self):
        parsed = reader._validate_event(_event(), RUN_ID)
        self.assertEqual(parsed["scope"], "BASELINE48_ONLY")
        self.assertEqual(parsed["selector"]["netPosition"], 3)
        self.assertEqual(parsed["original"]["nodeCount"], 222)
        self.assertEqual(parsed["clone"]["post"]["createdViaCount"], 4)

    def test_exact_top_level_shape_and_lineage_are_required(self):
        extra = _event()
        extra["graph"] = {}
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_event(extra)

        for key, wrong in (("rootSeed", "9007199254740992"),
                           ("rootSeed", 9007199254740992),
                           ("routeRequestIndex", 1),
                           ("selectedPlacementAttempt", 2),
                           ("branchOrdinal", 72),
                           ("netPosition", 4),
                           ("primaryLayer", True),
                           ("startPadId", "JLOAD.2")):
            value = _event()
            value["selector"][key] = wrong
            with self.subTest(key=key):
                with self.assertRaises(reader.Load12BaselineInputError):
                    reader._validate_event(value)

    def test_known_hashes_and_path_metrics_are_independent_expectations(self):
        mutations = (
            ("pathTupleSha256", "0" * 64),
            ("goalSha256", "1" * 64),
            ("preGeometrySha256", "2" * 64),
            ("nodeCount", 221),
            ("planarLength", 2171),
            ("bends", 3),
            ("flips", 5),
            ("nodeCount", True),
        )
        for field, wrong in mutations:
            value = _event()
            value["original"][field] = wrong
            with self.subTest(field=field, wrong=wrong):
                with self.assertRaises(reader.Load12BaselineInputError):
                    reader._validate_event(value)

    def test_prefix_publication_counts_and_deltas_are_checked(self):
        mutations = (
            ("pre", "viaCount", 16),
            ("pre", "traceCount", 87),
            ("post", "viaCount", 20),
            ("post", "traceCount", 92),
            ("post", "createdViaCount", 5),
            ("post", "createdTraceCount", 4),
        )
        for group, field, wrong in mutations:
            value = _event()
            value["original"][group][field] = wrong
            with self.subTest(group=group, field=field):
                with self.assertRaises(reader.Load12BaselineInputError):
                    reader._validate_event(value)

    def test_event_flags_cannot_substitute_for_actual_value_equality(self):
        value = _event()
        value["clone"]["publicationSha256"] = "f" * 64
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_event(value)

        for field, wrong in (("pathEqual", False), ("metricsEqual", "true"),
                             ("sourcePreserved", False), ("sourceResumed", False),
                             ("routeAdoption", True), ("layoutQualification", True),
                             ("playable100", True), ("graphReceiptCreated", True)):
            value = _event()
            value["comparison"][field] = wrong
            with self.subTest(field=field):
                with self.assertRaises(reader.Load12BaselineInputError):
                    reader._validate_event(value)

    def test_work_and_limit_receipts_reject_wrong_types_and_overruns(self):
        for section, field, wrong in (
                ("sourceOperation", "workUnits", True),
                ("sourceOperation", "maxUnitMs", 5001),
                ("sourceOperation", "cleanupMs", 120001),
                ("copy", "lastWork", 32769),
                ("replay", "toPreCalls", 0),
                ("replay", "toPreWork", 99),
                ("replay", "toPostWork", 32769),
                ("copy", "workSteps", 1),
                ("cloneCursor", "preWork", 1),
                ("sourceCursor", "postWork", 65537),
                ("limits", "quantum", 16384)):
            value = _event()
            value["work"][section][field] = wrong
            with self.subTest(section=section, field=field):
                with self.assertRaises(reader.Load12BaselineInputError):
                    reader._validate_event(value)

        value = _event()
        value["work"]["sourceOperation"]["workUnits"] = 4
        value["work"]["copy"].update(advanceCalls=2, workSteps=65536,
                                     lastWork=reader.MAX_WORK_QUANTUM)
        value["work"]["replay"].update(toPreCalls=2, toPreWork=2,
                                       toPostCalls=2, toPostWork=65536)
        for cursor_name in ("cloneCursor", "sourceCursor"):
            value["work"][cursor_name].update(
                preAdvanceCalls=2, preWork=65536, postAdvanceCalls=2, postWork=65536)
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_event(value)

    def test_realistic_manual_steps_and_single_step_replay_calls_can_exceed_units(self):
        lines = _receipt_lines()
        event = _event()
        event["work"]["sourceOperation"]["manualSliceSteps"] = 3502
        event["work"]["replay"]["toPreCalls"] = 800
        event["work"]["replay"]["toPreWork"] = 800
        _rewrite_record(lines, 5, lambda value: value.update(manualSliceSteps=3502))
        lines[7] = reader.EVENT_MARKER + " " + json.dumps(
            event, sort_keys=True, separators=(",", ":"))
        self.assertEqual(reader._validate_receipt_lines(lines)["event"]["work"][
            "sourceOperation"]["manualSliceSteps"], 3502)

        too_many = _receipt_lines()
        invalid = _event()
        invalid["work"]["sourceOperation"]["manualSliceSteps"] = reader.MAX_MANUAL_SLICES + 1
        _rewrite_record(too_many, 5,
                        lambda value: value.update(manualSliceSteps=reader.MAX_MANUAL_SLICES + 1))
        too_many[7] = reader.EVENT_MARKER + " " + json.dumps(
            invalid, sort_keys=True, separators=(",", ":"))
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_receipt_lines(too_many)


class ReceiptTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        reader._load_runtime_helpers()

    def test_receipt_sequence_and_run_binding(self):
        parsed = reader._validate_receipt_lines(_receipt_lines())
        self.assertEqual(parsed["event"]["runId"], RUN_ID)
        self.assertEqual(parsed["assertions"], 17)

    def test_baseline_work_cannot_disagree_with_validated_probe_or_source_cursor(self):
        lines = _receipt_lines()
        baseline = _event()
        baseline["work"]["sourceOperation"]["operationMs"] += 1
        lines[7] = reader.EVENT_MARKER + " " + json.dumps(
            baseline, sort_keys=True, separators=(",", ":"))
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_receipt_lines(lines)

        lines = _receipt_lines()
        baseline = _event()
        baseline["work"]["sourceOperation"]["preAdvanceCalls"] += 1
        lines[7] = reader.EVENT_MARKER + " " + json.dumps(
            baseline, sort_keys=True, separators=(",", ":"))
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_receipt_lines(lines)

    def test_receipt_rejects_old_graph_records_duplicates_and_wrong_order(self):
        lines = _receipt_lines()
        lines[7] = "RB100_ZERO_VIA_GRAPH_EVENT {}"
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_receipt_lines(lines)

        for index in (1, 5, 8):
            lines = _receipt_lines()
            lines[index] = lines[7]
            with self.subTest(index=index):
                with self.assertRaises(reader.Load12BaselineInputError):
                    reader._validate_receipt_lines(lines)

        doubled = _receipt_lines()
        doubled.insert(8, doubled[7])
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_receipt_lines(doubled)

    def test_receipt_rejects_split_run_ids_and_false_original_pair(self):
        lines = _receipt_lines()
        lines[4] = lines[4].replace(RUN_ID, "d1c10000-0000-4000-8000-000000000002")
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_receipt_lines(lines)

    def test_corrupt_original_probe_metric_fails_the_pinned_scalar_oracle(self):
        lines = _receipt_lines()
        def corrupt_probe(record):
            record["result"]["reportFacts"]["policyStatistics"]["routingExpansions"] += 1
        _rewrite_record(lines, 4, corrupt_probe)
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_receipt_lines(lines)

        lines = _receipt_lines()
        bad_pair = json.loads(lines[6].split(" ", 1)[1])
        bad_pair["status"] = "FAIL"
        lines[6] = reader.RECEIPT_MARKERS[6] + " " + json.dumps(bad_pair)
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_receipt_lines(lines)

    def test_outer_output_must_match_receipt_and_finish_cleanup(self):
        receipt = ("\n".join(_receipt_lines()) + "\n").encode()
        console = ("JDK warning\n" + receipt.decode() + reader.FOCUSED_PASS + "\n" +
                   reader.CLEANUP_LINE + "\n").encode()
        parsed_console, parsed_receipt = reader._validate_console_and_receipt(
            console, receipt, b"")
        self.assertTrue(parsed_console)
        self.assertEqual(len(parsed_receipt), len(reader.RECEIPT_MARKERS))

        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_console_and_receipt(
                console.replace(b"JDK warning\n", b"JDK warning\nRB100_STALE {}\n"),
                receipt, b"")
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_console_and_receipt(console, receipt,
                                                   b"RB100_STALE {}\n")
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_console_and_receipt(
                console.replace((reader.CLEANUP_LINE + "\n").encode(), b""), receipt, b"")


class NativeRunTests(unittest.TestCase):
    def test_generic_v5_envelope_binds_baseline_target_and_suite(self):
        stdout, receipt, stderr = b"stdout", b"receipt", b""
        context = {
            "inputCount": reader.SOURCE_INPUT_COUNT,
            "inputBytes": 123,
            "pinsSha256": SHA_A,
        }
        run = _valid_native_run(stdout, receipt, stderr)
        self.assertEqual(reader._validate_native_run(
            run, context, stdout, receipt, stderr)["suite"], reader.SUITE)

        wrong_target = dict(run, target="D_SENSE")
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_native_run(wrong_target, context, stdout, receipt, stderr)
        wrong_suite = dict(run, suite="Rb100Load12ZeroViaGraphContractTest")
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_native_run(wrong_suite, context, stdout, receipt, stderr)

    def test_native_envelope_hashes_exit_and_cleanup_are_strict(self):
        stdout, receipt, stderr = b"stdout", b"receipt", b"stderr"
        context = {"inputCount": 1079, "inputBytes": 123, "pinsSha256": SHA_A}
        run = _valid_native_run(stdout, receipt, stderr)
        run["stdoutSha256"] = SHA_B
        with self.assertRaises(reader.Load12BaselineInputError):
            reader._validate_native_run(run, context, stdout, receipt, stderr)

        for field, wrong in (("nativeExitCode", 2), ("terminationProven", False),
                             ("cleanupStatus", "FAIL"), ("cleanupElapsedMs", 1),
                             ("nativeLimitMs", 900_000), ("outerLimitMs", 480_000)):
            run = _valid_native_run(stdout, receipt, stderr)
            run[field] = wrong
            with self.subTest(field=field):
                with self.assertRaises(reader.Load12BaselineInputError):
                    reader._validate_native_run(run, context, stdout, receipt, stderr)


class FileBindingTests(unittest.TestCase):
    def test_artifact_recheck_detects_bytes_changed_after_validation(self):
        reader._load_runtime_helpers()
        with tempfile.TemporaryDirectory(prefix="q100-baseline-artifact-") as temporary:
            artifacts = {}
            captures = {}
            for name in ("resultPath", "stdoutPath", "receiptPath", "stderrPath"):
                path = Path(temporary) / name
                value = ("validated " + name).encode()
                path.write_bytes(value)
                artifacts[name] = path
                captures[name] = value
            reader._stable_artifact_check(artifacts, captures)
            artifacts["stdoutPath"].write_bytes(b"changed bytes")
            with self.assertRaises(reader.Load12BaselineInputError) as caught:
                reader._stable_artifact_check(artifacts, captures)
            self.assertEqual(caught.exception.code,
                             "NATIVE_ARTIFACT_CHANGED_DURING_READ")

    def test_reparse_source_path_is_rejected_when_host_can_create_a_link(self):
        with tempfile.TemporaryDirectory(prefix="q100-baseline-reparse-") as temporary:
            root = Path(temporary)
            target = root / "target.txt"
            link = root / "link.txt"
            target.write_bytes(b"source")
            try:
                link.symlink_to(target)
            except (OSError, NotImplementedError):
                self.skipTest("host does not allow creating a file symlink")
            with self.assertRaises(reader.Load12BaselineInputError):
                reader._read_local(link, 1024, "REPARSE_FIXTURE")

    def test_modified_pinned_scalar_helper_is_rejected_in_an_isolated_copy(self):
        helper_names = (
            "read_quality_scalars.py", "read_current_prefix_ledger.py",
            "read_fixed_prefix_capture.py", "read_fixed_prefix_graph.py",
        )
        source_directory = Path(reader.__file__).parent
        with tempfile.TemporaryDirectory(prefix="q100-baseline-helper-") as temporary:
            directory = Path(temporary)
            consumer_path = directory / "read_load12_baseline.py"
            shutil.copyfile(reader.__file__, consumer_path)
            for name in helper_names:
                shutil.copyfile(source_directory / name, directory / name)
            helper = directory / "read_quality_scalars.py"
            helper.write_bytes(helper.read_bytes() + b"\n# changed helper\n")
            spec = importlib.util.spec_from_file_location(
                "_q100_baseline_changed_helper", consumer_path)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            with self.assertRaises(module.Load12BaselineInputError) as caught:
                module._load_runtime_helpers()
            self.assertEqual(caught.exception.code, "SCALAR_HELPER_HASH_MISMATCH")


if __name__ == "__main__":
    unittest.main()
