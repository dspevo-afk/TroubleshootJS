"""Independent primitive, lineage and transport controls for A_DRIVE guard."""
from __future__ import annotations

import copy
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import read_load12_adrive_guard as reader
import test_read_load12_remainder as remainder_fixtures


def _sha(label: str) -> str:
    return hashlib.sha256(label.encode("ascii")).hexdigest()


def _base_event():
    """Adapt the frozen predecessor fixture to the accepted A_DRIVE boundary."""
    value = copy.deepcopy(remainder_fixtures._event_fixture("QUALITY_BOUNDARY"))
    value["remainder"].update({
        "boundaryReason": "A_DRIVE:BRANCH_QUALITY_LIMIT",
        "cumulativeExpansions": 774_550,
        "remainingRequestBudget": 225_450,
    })
    value["work"]["phases"][6]["calls"] = 5
    value["work"]["controllerUnits"] = sum(
        row["calls"] for row in value["work"]["phases"])
    return value


def _payload(value):
    return json.dumps(value, separators=(",", ":"), ensure_ascii=True,
                      allow_nan=False).encode("utf-8")


def _rows(actions, width, height=64, origin_x=0, origin_y=0):
    """Make only primitive rows; the consumer independently derives metrics."""
    column = 0
    row = 0
    layer = 0
    transitions = 0
    direction = 4
    result = []

    def append(parent):
        cell = row * width + column
        x = origin_x + column * reader.GRID
        y = origin_y + row * reader.GRID
        key = (((transitions * 2 + layer) * 5 + direction) *
               (width * height)) + cell
        result.append([cell, layer, x, y, direction, transitions,
                       len(result), 0, len(result) + 1, key, parent])

    append(-1)
    for index, action in enumerate(actions):
        if action == "E":
            column += 1
            direction = 1
        elif action == "W":
            column -= 1
            direction = 3
        elif action == "S":
            row += 1
            direction = 2
        elif action == "N":
            row -= 1
            direction = 0
        elif action == "V":
            layer = 1 - layer
            transitions += 1
            direction = 4
        else:
            raise AssertionError("unknown fixture move")
        append(index)
    return result


def _witness_fixture(actions=()):
    base = _base_event()
    base_raw = _payload(base)
    grid = dict(base["selector"]["grid"])
    nodes = _rows(actions, grid["width"], grid["height"],
                  grid["originX"], grid["originY"])
    rows_raw = reader._canonical_json(nodes)
    first = nodes[0]
    last = nodes[-1]
    start = {"padId": "J1", "identity": 700, "x": first[2],
             "y": first[3], "cell": first[0]}
    root = {"padId": "ROOT1", "identity": 701, "x": first[2],
            "y": first[3], "cell": first[0]}
    end = {"identity": 600, "x": last[2], "y": last[3], "cell": last[0]}
    metrics = reader._derive_path_metrics(nodes, grid)
    direct = abs(start["x"] - end["x"]) + abs(start["y"] - end["y"])
    bits = reader._guard_predicates(direct, metrics["length"], metrics["bends"])
    value = {
        "protocolVersion": 1,
        "schema": reader.EVENT_SCHEMA,
        "status": "PASS",
        "runId": base["runId"],
        "operationId": "QUALITY_PROBE",
        "scope": "ACTUAL_LOAD12_COST240_COPIED_ORDER_ADRIVE_PRE_GUARD",
        "base": {"runId": base["runId"], "eventSha256": hashlib.sha256(base_raw).hexdigest()},
        "inputs": dict(reader.EXPECTED_INPUTS),
        "lineage": reader._base_projection(base),
        "target": {
            "ordering": 2, "pass": 1, "netId": "A_DRIVE",
            "netVectorPosition": 4, "searchNetIndex": 5,
            "netVectorIdentity": 401, "branchOrdinal": 74,
            "searchBranches": 74, "ownerIdentity": base["copy"]["ownerIdentity"],
            "searchIdentity": base["copy"]["searchIdentity"],
            "layoutIdentity": base["copy"]["layoutIdentity"],
            "branchIdentity": 500, "publisherIdentity": 501,
            "foundNodeIdentity": end["identity"], "foundTerminalIdentity": None,
        },
        "grid": grid,
        "endpoints": {"start": start, "root": root, "end": end,
                      "foundTerminal": None},
        "path": {
            "encoding": reader.NODE_ENCODING, "nodeCount": len(nodes),
            "maximumNodes": reader.MAX_WITNESS_NODES,
            "rowsSha256": hashlib.sha256(rows_raw).hexdigest(), "nodes": nodes,
        },
        "checkpoint": {
            "publisherStage": 1, "qualityIndex": len(nodes),
            "reversedSize": len(nodes), "storedLength": metrics["length"],
            "storedBends": metrics["bends"],
            "storedLastDirection": metrics["lastDirection"], "direct": direct,
            "recomputedLength": metrics["length"],
            "recomputedBends": metrics["bends"],
            "transitions": metrics["transitions"],
            "directZero": bits[0], "lengthOverThreeDirect": bits[1],
            "bendsOver16": bits[2], "rejected": any(bits),
        },
        "boundary": {
            "status": "ORDERING_BOUNDARY", "type": "ORDERING_BOUNDARY",
            "reason": "A_DRIVE:BRANCH_QUALITY_LIMIT", "orderings": 2,
            "cumulativeExpansions": 774_550,
            "remainingRequestBudget": 225_450,
            "sameTypedBoundary": True, "rejectedBranchPublished": False,
        },
        "prefix": {
            "before": {
                "layoutIdentity": base["copy"]["layoutIdentity"],
                "geometrySha256": _sha("geometry"), "traceCount": 90,
                "traceRowsSha256": _sha("trace rows"), "holeCount": 18,
                "holeRowsSha256": _sha("hole rows"),
            },
            "after": {
                "layoutIdentity": base["copy"]["layoutIdentity"],
                "geometrySha256": _sha("geometry"), "traceCount": 90,
                "traceRowsSha256": _sha("trace rows"), "holeCount": 18,
                "holeRowsSha256": _sha("hole rows"),
            },
            "unchanged": True,
        },
        "work": {
            "budgetPhase": "orderingRemainder", "controllerUnitLimit": 640,
            "ownerStepsPerUnitLimit": 32_768, "controllerUnits": 5,
            "subphases": {
                "advance": {"controllerUnits": 1, "elapsedNanos": 1_000_000},
                "capture": {"controllerUnits": 1, "elapsedNanos": 1_000_000},
                "recompute": {"controllerUnits": 1, "elapsedNanos": 1_000_000},
                "serialize": {"controllerUnits": 1, "elapsedNanos": 1_000_000},
                "boundaryCheck": {"controllerUnits": 1, "elapsedNanos": 1_000_000},
            },
            "continuationAdvanceApiCalls": 1, "continuationOwnerSteps": 10,
            "maximumGroupOwnerSteps": 10,
            "operationElapsedNanos": 10_000_000,
            "phaseElapsedNanos": 6_000_000,
            "overheadElapsedNanos": 1_000_000,
            "capturedNodeRows": len(nodes), "verifiedNodeRows": len(nodes),
            "pathRowsUtf8Bytes": len(rows_raw),
        },
        "cleanup": {"status": "PASS", "elapsedMs": base["cleanup"]["elapsedMs"],
                    "limitMs": reader.MAX_CLEANUP_MS},
        "qualification": {
            "routeAccepted": False, "geometryPublished": False,
            "layoutQualification": False, "routeAdoption": False,
            "playable100": False,
        },
    }
    return value, base, base_raw


def _native_run_fixture(context):
    stdout = context["stdoutRaw"]
    receipt = context["receiptRaw"]
    stderr = context["stderrRaw"]
    return {
        "schema": reader.RUN_SCHEMA,
        "source": "candidate",
        "suite": reader.SUITE,
        "cohortId": None,
        "command": reader._expected_command(),
        "layoutVersion": 26,
        "dependencyEpoch": "tsj-generation-dependencies-v28",
        "sourceSnapshotRevision": reader.SOURCE_REVISION,
        "inputCount": reader.SOURCE_INPUT_COUNT,
        "inputBytes": context["inputBytes"],
        "inputPinsSha256": context["pinsSha256"],
        "inputAudit": "PASS_BEFORE_AND_AFTER",
        "exitCode": 0,
        "nativeExitCode": 0,
        "terminationProven": True,
        "cleanupStatus": "PASS",
        "cleanupElapsedMs": None,
        "viaCost": 60,
        "nativeLimitMs": reader.MAX_NATIVE_LIMIT_MS,
        "outerLimitMs": reader.MAX_OUTER_LIMIT_MS,
        "processId": 100,
        "processStartTicks": "134089000000000000",
        "elapsedSeconds": 3.5,
        "requestedExecutable": "powershell.exe",
        "stdoutBytes": len(stdout),
        "stdoutSha256": hashlib.sha256(stdout).hexdigest(),
        "stderrBytes": len(stderr),
        "stderrSha256": hashlib.sha256(stderr).hexdigest(),
        "receiptBytes": len(receipt),
        "receiptSha256": hashlib.sha256(receipt).hexdigest(),
        "target": reader.TARGET,
    }


class GuardWitnessTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.helper, cls.helper_raw, cls.helper_path = reader._load_base_reader()

    def assert_witness_rejected(self, value, base, raw):
        with self.assertRaises(reader.ADriveGuardInputError):
            reader._validate_witness(value, base, raw, self.helper)

    def test_one_node_direct_zero_is_a_valid_bounded_rejection(self):
        witness, base, raw = _witness_fixture()
        checked = reader._validate_witness(witness, base, raw, self.helper)
        self.assertEqual(1, checked["path"]["nodeCount"])
        self.assertEqual(0, checked["checkpoint"]["storedLength"])
        self.assertTrue(checked["checkpoint"]["directZero"])
        self.assertTrue(checked["checkpoint"]["rejected"])

    def test_exact_three_times_length_and_sixteen_bends_are_allowed(self):
        self.assertEqual((False, False, False), reader._guard_predicates(10, 30, 16))
        self.assertEqual((False, True, False), reader._guard_predicates(10, 31, 16))
        self.assertEqual((False, False, True), reader._guard_predicates(10, 30, 17))
        self.assertEqual((True, False, False), reader._guard_predicates(0, 0, 0))

    def test_via_resets_direction_for_independent_bend_recomputation(self):
        rows = _rows(("E", "V", "W"), 64, 64)
        metrics = reader._derive_path_metrics(rows, {
            "width": 64, "height": 64, "cells": 4096,
            "originX": 0, "originY": 0, "pitch": 10,
        })
        self.assertEqual(0, metrics["bends"])
        self.assertEqual(1, metrics["transitions"])
        self.assertEqual(3, metrics["lastDirection"])

    def test_corrupt_parent_key_and_coordinates_are_rejected(self):
        for label, edit in (
                ("parent", lambda row: row.__setitem__(10, 8)),
                ("key", lambda row: row.__setitem__(9, row[9] + 1)),
                ("coordinates", lambda row: row.__setitem__(2, row[2] + 1))):
            with self.subTest(label=label):
                witness, _base, _raw = _witness_fixture(("E",))
                edit(witness["path"]["nodes"][1])
                with self.assertRaises(reader.ADriveGuardInputError):
                    reader._validate_path(witness["path"], witness["grid"],
                                          witness["endpoints"])

    def test_base_hash_run_id_and_projection_lineage_are_bound(self):
        witness, base, raw = _witness_fixture()
        changed = copy.deepcopy(witness)
        changed["base"]["eventSha256"] = "0" * 64
        self.assert_witness_rejected(changed, base, raw)

        changed = copy.deepcopy(witness)
        changed["runId"] = "123e4567-e89b-42d3-a456-426614174001"
        self.assert_witness_rejected(changed, base, raw)

        changed = copy.deepcopy(witness)
        changed["lineage"]["copy"]["layoutIdentity"] += 1
        self.assert_witness_rejected(changed, base, raw)

    def test_base_event_hashes_exact_payload_bytes_without_reserialization(self):
        witness, base, _raw = _witness_fixture()
        raw = json.dumps(base, indent=1, ensure_ascii=True).encode("utf-8")
        witness["base"]["eventSha256"] = hashlib.sha256(raw).hexdigest()
        checked = reader._validate_witness(witness, base, raw, self.helper)
        self.assertEqual(base["runId"], checked["runId"])

    def test_stale_owner_endpoints_predicates_and_boundary_fail_closed(self):
        mutations = (
            ("stale owner", lambda e: e["target"].update(ownerIdentity=999)),
            ("wrong copy search", lambda e: e["target"].update(searchIdentity=999)),
            ("wrong end node", lambda e: e["endpoints"]["end"].update(identity=999)),
            ("wrong endpoint cell", lambda e: e["endpoints"]["end"].update(cell=1)),
            ("false predicate", lambda e: e["checkpoint"].update(directZero=False)),
            ("observer failure", lambda e: e.update(status="OBSERVER_FAILURE")),
            ("wrong typed boundary", lambda e: e["boundary"].update(type="ROUTE_READY")),
            ("missing boundary", lambda e: e["boundary"].pop("reason")),
            ("publication", lambda e: e["boundary"].update(rejectedBranchPublished=True)),
        )
        for label, mutate in mutations:
            with self.subTest(label=label):
                witness, base, raw = _witness_fixture()
                mutate(witness)
                self.assert_witness_rejected(witness, base, raw)

    def test_pad_mapping_rejects_non_grid_physical_coordinates(self):
        witness, _base, _raw = _witness_fixture()
        witness["endpoints"]["start"]["x"] = 1
        with self.assertRaises(reader.ADriveGuardInputError):
            reader._validate_endpoints(witness["endpoints"], witness["grid"],
                                       witness["target"])

    def test_grid_cursor_and_branch_counter_bind_to_frozen_search(self):
        witness, base, raw = _witness_fixture()
        self.assertEqual(base["selector"]["grid"], witness["grid"])

        changed = copy.deepcopy(witness)
        changed["grid"]["originX"] += reader.GRID
        for name in ("start", "root", "end"):
            changed["endpoints"][name]["x"] += reader.GRID
        for row in changed["path"]["nodes"]:
            row[2] += reader.GRID
        rows_raw = reader._canonical_json(changed["path"]["nodes"])
        changed["path"]["rowsSha256"] = hashlib.sha256(rows_raw).hexdigest()
        changed["work"]["pathRowsUtf8Bytes"] = len(rows_raw)
        with self.assertRaises(reader.ADriveGuardInputError):
            reader._validate_witness(changed, base, raw, self.helper)

        changed = copy.deepcopy(witness)
        changed["target"]["searchNetIndex"] += 1
        with self.assertRaises(reader.ADriveGuardInputError):
            reader._validate_witness(changed, base, raw, self.helper)

        changed = copy.deepcopy(witness)
        changed["target"]["branchOrdinal"] += 1
        with self.assertRaises(reader.ADriveGuardInputError):
            reader._validate_witness(changed, base, raw, self.helper)

    def test_prefix_and_operation_failure_never_become_a_guard_success(self):
        witness, base, raw = _witness_fixture()
        witness["prefix"]["after"]["traceCount"] += 1
        self.assert_witness_rejected(witness, base, raw)

        witness, base, raw = _witness_fixture()
        witness["work"]["operationElapsedNanos"] = reader.MAX_OPERATION_NS + 1
        self.assert_witness_rejected(witness, base, raw)

        witness, base, raw = _witness_fixture()
        witness["cleanup"]["status"] = "FAIL"
        self.assert_witness_rejected(witness, base, raw)

    def test_controller_counts_and_nanosecond_floor_bind_to_base(self):
        witness, base, raw = _witness_fixture()
        witness["work"]["controllerUnits"] += 1
        self.assert_witness_rejected(witness, base, raw)
        witness, base, raw = _witness_fixture()
        witness["work"]["phaseElapsedNanos"] += 1
        self.assert_witness_rejected(witness, base, raw)
        witness, base, raw = _witness_fixture()
        witness["work"]["continuationOwnerSteps"] += 1
        self.assert_witness_rejected(witness, base, raw)

    def test_advance_api_calls_are_independent_from_groups_and_steps(self):
        witness, base, raw = _witness_fixture()
        witness["work"]["continuationAdvanceApiCalls"] = 1_000_000
        checked = reader._validate_witness(witness, base, raw, self.helper)
        self.assertEqual(1_000_000,
                         checked["work"]["continuationAdvanceApiCalls"])
        self.assertEqual(1, checked["work"]["subphases"]["advance"]["controllerUnits"])
        self.assertEqual(10, checked["work"]["continuationOwnerSteps"])

        for invalid in (True, 0, -1, reader.MAX_JAVA_LONG + 1):
            witness, base, raw = _witness_fixture()
            witness["work"]["continuationAdvanceApiCalls"] = invalid
            with self.subTest(invalid=invalid):
                self.assert_witness_rejected(witness, base, raw)

    def test_unchanged_boundary_call_charges_its_own_controller_group(self):
        witness, base, raw = _witness_fixture()
        base["work"]["phases"][6]["ownerSteps"] = 32_769
        base["work"]["maxOwnerStepsInUnit"] = 32_768
        witness["work"]["continuationOwnerSteps"] = 32_769
        witness["work"]["continuationAdvanceApiCalls"] = 32_769
        witness["work"]["maximumGroupOwnerSteps"] = 32_768
        raw = _payload(base)
        witness["base"]["eventSha256"] = hashlib.sha256(raw).hexdigest()
        checked = reader._validate_witness(witness, base, raw, self.helper)
        self.assertEqual(32_769, checked["work"]["continuationOwnerSteps"])

        # Capture/recompute/serialize groups grant no continuation owner work.
        base["work"]["phases"][6]["ownerSteps"] = 65_537
        witness["work"]["continuationOwnerSteps"] = 65_537
        witness["work"]["continuationAdvanceApiCalls"] = 65_537
        raw = _payload(base)
        witness["base"]["eventSha256"] = hashlib.sha256(raw).hexdigest()
        self.assert_witness_rejected(witness, base, raw)

    def test_4096_node_bound_accepts_and_4097_is_rejected(self):
        actions = tuple("E" if index % 2 == 0 else "W" for index in range(4_094)) + ("V",)
        witness, base, raw = _witness_fixture(actions)
        self.assertEqual(4_096, witness["path"]["nodeCount"])
        self.assertLessEqual(len(_payload(witness)), reader.MAX_GUARD_PAYLOAD_BYTES)
        checked = reader._validate_witness(witness, base, raw, self.helper)
        self.assertEqual(4_096, checked["path"]["nodeCount"])

        actions = tuple("E" if index % 2 == 0 else "W" for index in range(4_096))
        witness, base, raw = _witness_fixture(actions)
        self.assertEqual(4_097, witness["path"]["nodeCount"])
        self.assert_witness_rejected(witness, base, raw)

    def test_payload_size_exact_limit_and_overflow(self):
        exact = reader.GUARD_EVENT_MARKER + " " + ("x" * reader.MAX_GUARD_PAYLOAD_BYTES)
        self.assertEqual(reader.MAX_GUARD_PAYLOAD_BYTES,
                         len(reader._line_body(exact, reader.GUARD_EVENT_MARKER,
                                               "GUARD", reader.MAX_GUARD_PAYLOAD_BYTES)))
        overflow = exact + "x"
        with self.assertRaises(reader.ADriveGuardInputError):
            reader._line_body(overflow, reader.GUARD_EVENT_MARKER,
                              "GUARD", reader.MAX_GUARD_PAYLOAD_BYTES)

    def test_strict_json_rejects_duplicate_nonfinite_and_malformed_utf8(self):
        for raw in (b'{"a":1,"a":2}', b'{"a":NaN}', b"\xff"):
            with self.subTest(raw=raw[:12]):
                with self.assertRaises(reader.ADriveGuardInputError):
                    reader._strict_json(raw, "CONTROL", self.helper)


class GuardTransportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.helper, cls.helper_raw, cls.helper_path = reader._load_base_reader()

    def _transport(self):
        witness, base, base_raw = _witness_fixture()
        base_line = reader.BASE_EVENT_MARKER + " " + base_raw.decode("utf-8")
        guard_line = reader.GUARD_EVENT_MARKER + " " + _payload(witness).decode("utf-8")
        summary = reader.SUMMARY_PREFIX + "9"
        receipt = (base_line + "\n" + guard_line + "\n" + summary + "\n").encode("utf-8")
        stdout = ("\n".join((base_line, guard_line, summary, reader.FOCUSED_PASS,
                              reader.CLEANUP_LINE)) + "\n").encode("utf-8")
        return stdout, receipt, base, witness, base_raw

    def test_exact_three_record_transport_and_receipt_match(self):
        stdout, receipt, base, witness, base_raw = self._transport()
        _console, lines, parsed_base, parsed_raw, parsed_guard, guard_raw = \
            reader._validate_console_and_receipt(stdout, receipt, b"", self.helper)
        self.assertEqual(3, len(lines))
        self.assertEqual(base, parsed_base)
        self.assertEqual(base_raw, parsed_raw)
        self.assertEqual(witness, parsed_guard)
        self.assertEqual(_payload(witness), guard_raw)
        checked = reader._validate_witness(parsed_guard, parsed_base, parsed_raw, self.helper)
        self.assertEqual("ORDERING_BOUNDARY", checked["boundary"]["status"])

    def test_transport_rejects_wrong_order_noncontiguous_extra_and_stderr(self):
        stdout, receipt, _base, _witness, _raw = self._transport()
        lines = receipt.decode("utf-8").splitlines()
        bad_rows = (
            [lines[1], lines[0], lines[2]],
            [lines[0], "diagnostic", lines[1], lines[2]],
            [lines[0], lines[1], lines[2], "EXTRA"],
        )
        for bad in bad_rows:
            bad_receipt = ("\n".join(bad) + "\n").encode("utf-8")
            with self.subTest(bad=bad[:1]):
                with self.assertRaises(reader.ADriveGuardInputError):
                    reader._validate_console_and_receipt(
                        stdout, bad_receipt, b"", self.helper)
        with self.assertRaises(reader.ADriveGuardInputError):
            reader._validate_console_and_receipt(stdout, receipt, b"error\n", self.helper)

    def test_transport_rejects_extra_console_copy_and_missing_wrapper_cleanup(self):
        stdout, receipt, _base, _witness, _raw = self._transport()
        duplicate_stdout = stdout + receipt.splitlines()[0] + b"\n"
        with self.assertRaises(reader.ADriveGuardInputError):
            reader._validate_console_and_receipt(
                duplicate_stdout, receipt, b"", self.helper)
        missing_cleanup = stdout.replace((reader.CLEANUP_LINE + "\n").encode(), b"")
        with self.assertRaises(reader.ADriveGuardInputError):
            reader._validate_console_and_receipt(missing_cleanup, receipt, b"", self.helper)

    def test_native_v5_requires_exact_new_command_and_original_closed_keys(self):
        stdout, receipt, _base, _witness, _raw = self._transport()
        context = {
            "inputCount": reader.SOURCE_INPUT_COUNT,
            "inputBytes": 543_210,
            "pinsSha256": "a" * 64,
            "stdoutRaw": stdout,
            "receiptRaw": receipt,
            "stderrRaw": b"",
        }
        good = _native_run_fixture(context)
        self.assertEqual(reader._expected_command(),
                         reader._validate_native_run(good, context)["command"])
        mutations = (
            lambda row: row["command"].update(args=["-Rb100Load12Cost240Replay"]),
            lambda row: row.update(inputAudit="NOT_AUDITED"),
            lambda row: row.update(nativeExitCode=2),
            lambda row: row.update(terminationProven=False),
            lambda row: row.update(inputManifestBytes=123),
        )
        for mutate in mutations:
            row = copy.deepcopy(good)
            mutate(row)
            with self.assertRaises(reader.ADriveGuardInputError):
                reader._validate_native_run(row, context)

    def test_private_f11_loader_is_hash_pinned_and_does_not_leak_module(self):
        private_name = "_p07_adrive_guard_base_reader_private"
        self.assertNotIn(private_name, sys.modules)
        helper, raw, _path = reader._load_base_reader()
        self.assertEqual(reader.BASE_READER_SHA256, hashlib.sha256(raw).hexdigest())
        self.assertTrue(callable(helper._validate_event))
        self.assertNotIn(private_name, sys.modules)

    def test_mutated_helper_source_is_rejected_before_execution(self):
        with tempfile.TemporaryDirectory(prefix="adrive-guard-helper-") as temp:
            directory = Path(temp)
            helper_copy = directory / "read_load12_remainder.py"
            helper_copy.write_bytes(self.helper_raw + b"\n# changed\n")
            fake_reader = directory / "reader.py"
            fake_reader.write_text("", encoding="utf-8")
            with patch.object(reader, "__file__", str(fake_reader)):
                with self.assertRaises(reader.ADriveGuardInputError) as caught:
                    reader._load_base_reader()
            self.assertEqual("BASE_READER_HASH_MISMATCH", caught.exception.code)

    def test_reparse_source_path_rejected_when_host_can_create_symlink(self):
        with tempfile.TemporaryDirectory(prefix="adrive-guard-reparse-") as temp:
            directory = Path(temp)
            target = directory / "plain.json"
            link = directory / "alias.json"
            target.write_text("{}", encoding="utf-8")
            try:
                link.symlink_to(target)
            except (OSError, NotImplementedError):
                self.skipTest("host does not permit an unprivileged file symlink")
            with self.assertRaises(reader.ADriveGuardInputError):
                reader._resolve_path(str(link), directory, "CONTROL_PATH", self.helper)


class InputManifestTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.helper, _raw, _path = reader._load_base_reader()

    def _fixture(self, root: Path):
        source = {
            "scripts/verify-current-contracts.ps1",
            "tests/contracts/current-contract-runner-contract.ps1",
            "tests/contracts/Rb100Load12DetachedBaselineContractTest.java",
            "tests/contracts/Rb100PhysicalDiagnostics.java",
            "src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java",
            "src/com/lushprojects/circuitjs1/client/PcbNetRouter.java",
            "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java",
        }
        source.update(self.helper.STATIC_SOURCE_INPUTS)
        source.update("src/fixtures/Fixture%04d.java" % i
                      for i in range(reader.PRODUCTION_JAVA_COUNT - 3))
        source.update("tests/contracts/Fixture%04d.java" % i
                      for i in range(reader.CONTRACT_JAVA_COUNT - 2))
        self.assertEqual(reader.SOURCE_INPUT_COUNT, len(source))
        rows = []
        for relative in sorted(source):
            raw = ("pinned:" + relative + "\n").encode("ascii")
            path = root.joinpath(*relative.split("/"))
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
            rows.append({"path": relative, "bytes": len(raw),
                         "sha256": hashlib.sha256(raw).hexdigest()})
        manifest = json.dumps(rows, separators=(",", ":")).encode("utf-8")
        spec = {"pinsSha256": hashlib.sha256(manifest).hexdigest(),
                "inputBytes": sum(row["bytes"] for row in rows)}
        return manifest, spec, rows

    def test_complete_native_source_manifest_binds_census_bytes_and_root(self):
        with tempfile.TemporaryDirectory(prefix="adrive-guard-source-") as temp:
            root = Path(temp) / "source"
            root.mkdir()
            raw, spec, _rows = self._fixture(root)
            result = reader._audit_inputs_manifest(raw, spec, root, self.helper)
            self.assertEqual(reader.SOURCE_INPUT_COUNT, result["inputCount"])
            self.assertEqual(spec["inputBytes"], result["inputBytes"])
            self.assertEqual(spec["pinsSha256"], result["pinsSha256"])

    def test_new_spec_purpose_and_selected_source_root_are_closed(self):
        with tempfile.TemporaryDirectory(prefix="adrive-guard-spec-") as temp:
            directory = Path(temp)
            root = directory / "source"
            root.mkdir()
            pins = directory / "pins.json"
            pins.write_bytes(b"[]")
            artifacts = {}
            for name in ("resultPath", "stdoutPath", "receiptPath", "stderrPath"):
                path = directory / (name + ".bin")
                path.write_bytes(b"" if name == "stderrPath" else b"artifact")
                artifacts[name] = str(path)
            spec = {
                "schema": reader.INPUT_SCHEMA,
                "target": reader.TARGET,
                "purpose": reader.PURPOSE,
                "suite": reader.SUITE,
                "sourceRoot": str(root),
                "pinsPath": str(pins),
                "pinsSha256": "a" * 64,
                "inputCount": reader.SOURCE_INPUT_COUNT,
                "inputBytes": 1,
                "sourceSnapshotRevision": reader.SOURCE_REVISION,
                **artifacts,
            }
            selected = reader.Path(reader.__file__).resolve().parents[3]
            self.assertEqual(selected, reader._bind_selected_source_root(
                selected, self.helper))
            with self.assertRaises(reader.ADriveGuardInputError) as caught:
                reader._validate_input_spec(spec, directory, self.helper)
            self.assertEqual("INPUT_SPEC_SOURCE_ROOT_NOT_SELECTED_WORKTREE",
                             caught.exception.code)
            for altered in (
                    dict(spec, purpose="ACTUAL_COST240"),
                    dict(spec, sourceRoot=str(directory)),
                    dict(spec, unexpected="field")):
                with self.subTest(altered=tuple(altered)):
                    with self.assertRaises(reader.ADriveGuardInputError):
                        reader._validate_input_spec(altered, directory, self.helper)

    def test_manifest_mutation_duplicate_and_incomplete_rows_are_rejected(self):
        with tempfile.TemporaryDirectory(prefix="adrive-guard-manifest-") as temp:
            root = Path(temp) / "source"
            root.mkdir()
            raw, spec, rows = self._fixture(root)
            first = root.joinpath(*rows[0]["path"].split("/"))
            original = first.read_bytes()
            first.write_bytes(original + b"changed")
            with self.assertRaises(reader.ADriveGuardInputError):
                reader._audit_inputs_manifest(raw, spec, root, self.helper)
            first.write_bytes(original)

            duplicate = copy.deepcopy(rows)
            duplicate[1] = copy.deepcopy(duplicate[0])
            duplicate_raw = json.dumps(duplicate, separators=(",", ":")).encode("utf-8")
            duplicate_spec = {"pinsSha256": hashlib.sha256(duplicate_raw).hexdigest(),
                              "inputBytes": sum(row["bytes"] for row in duplicate)}
            with self.assertRaises(reader.ADriveGuardInputError):
                reader._audit_inputs_manifest(duplicate_raw, duplicate_spec, root, self.helper)

            incomplete_raw = json.dumps(rows[:-1], separators=(",", ":")).encode("utf-8")
            incomplete_spec = {"pinsSha256": hashlib.sha256(incomplete_raw).hexdigest(),
                               "inputBytes": sum(row["bytes"] for row in rows[:-1])}
            with self.assertRaises(reader.ADriveGuardInputError):
                reader._audit_inputs_manifest(incomplete_raw, incomplete_spec,
                                              root, self.helper)


if __name__ == "__main__":
    unittest.main()
