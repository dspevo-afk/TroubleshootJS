"""Independent synthetic tests for the detached LOAD12 cost240 reader."""
from __future__ import annotations

import copy
import hashlib
import io
import json
import unittest
from contextlib import redirect_stdout
from pathlib import Path

import read_load12_cost240 as reader
import test_read_quality_scalars as scalar_fixtures


def _source_graphspec_evidence_fixture():
    return {
        "inputSpecSha256": reader.SOURCE_GRAPH_INPUT_SPEC_SHA256,
        "inputPinsSha256": reader.SOURCE_GRAPH_PINS_SHA256,
        "inputCount": 1079,
        "inputBytes": 86_847_809,
        "readerAndHelperSha256": {
            "currentReader": "d16c7385a604aa3e126ae7fd39f98d6b8fe5753c2ea6bb2d8244dc8b74ce5cbc",
        },
        "exporterSha256": "4475aaded5c56c79ab236ba58fdefa1ca89926e08df67a151aacdd7a0b52e112",
    }


def _current_input_spec_fixture():
    return {
        "schema": reader.INPUT_SCHEMA,
        "target": "LOAD12",
        "purpose": reader.PURPOSE,
        "suite": reader.SUITE,
        "sourceRoot": "source",
        "pinsPath": "pins.json",
        "pinsSha256": "a" * 64,
        "inputCount": 1080,
        "inputBytes": 100,
        "sourceSnapshotRevision": 20,
        "resultPath": "result.json",
        "stdoutPath": "stdout.txt",
        "receiptPath": "receipt.txt",
        "stderrPath": "stderr.txt",
        "graphspecPath": "graphspec.json",
        "graphspecSha256": reader.SOURCE_GRAPH_SPEC_SHA256,
        "sourceGraphInputSpecPath": "source-graph-input.json",
        "frozenOracleDirectory": "../frozen-oracle/tests/qualification/rb100-layout",
        "sourceGraphInputSpecSha256": reader.SOURCE_GRAPH_INPUT_SPEC_SHA256,
    }


def _node(index, x, y, layer, direction, transitions, cost):
    return {
        "index": index,
        "cell": index,
        "x": x,
        "y": y,
        "layer": layer,
        "direction": direction,
        "transitions": transitions,
        "cost": cost,
    }


def _both_face_turn_flip_path(via_cost, primary_layer):
    secondary_layer = 1 - primary_layer
    return [
        _node(0, 0, 0, primary_layer, 4, 0, 0),
        _node(1, 10, 0, primary_layer, 1, 0, 10),
        _node(2, 10, 10, primary_layer, 2, 0, 55),
        _node(2, 10, 10, secondary_layer, 4, 1, 55 + via_cost),
        _node(3, 20, 10, secondary_layer, 1, 1, 67 + via_cost),
    ]


def _secondary_seed_path(primary_layer):
    secondary_layer = 1 - primary_layer
    return [
        _node(0, 0, 0, secondary_layer, 4, 0, 2),
        _node(1, 10, 0, secondary_layer, 1, 0, 14),
        _node(2, 10, 10, secondary_layer, 2, 0, 61),
        _node(2, 10, 10, primary_layer, 4, 1, 61 + 240),
        _node(3, 20, 10, primary_layer, 1, 1, 71 + 240),
    ]


def _source_clone_settings():
    source = {
        "sessionMaximumVias": 48,
        "searchMaximumVias": 48,
        "sessionViaCost": 60,
        "searchViaCost": 60,
        "initial": {},
        "afterReplayBeforeResume": {},
    }
    clone = {
        "requestedMaximumVias": 48,
        "requestedViaCost": 240,
        "sessionMaximumVias": 48,
        "searchMaximumVias": 48,
        "sessionViaCost": 240,
        "searchViaCost": 240,
        "maximumTransitions": 4,
        "initial": {},
        "path": {},
        "preResult": {},
        "postResult": {},
        "normalPublisher": {},
    }
    return source, clone


def _source_after_replay(seeds):
    return {
        "sessionMaximumVias": 48,
        "searchMaximumVias": 48,
        "sessionViaCost": 60,
        "searchViaCost": 60,
        "branchExpanded": 0,
        "frontier": {"queueSize": 2, "bestSize": 2, "seeds": seeds},
        "holeCount": 17,
        "traceCount": 88,
    }


def _work_fixture():
    return {
        "sourceOperation": {
            "operationMs": 10,
            "workUnits": 2,
            "maxUnitMs": 5,
            "cleanupMs": 0,
            "manualGroups": 1,
            "manualSliceSteps": 1,
        },
        "copy": {
            "advanceCalls": 1,
            "workSteps": 2,
            "copiedEntries": 1,
            "lastWork": 1,
            "observerChecks": 1,
        },
        "replay": {
            "toPreCalls": 1,
            "toPreWork": 1,
            "toPostCalls": 1,
            "toPostWork": 1,
        },
        "cloneCursor": {
            "preAdvanceCalls": 1,
            "preWork": 1,
            "postAdvanceCalls": 1,
            "postWork": 1,
        },
        "sourceCursor": {
            "preAdvanceCalls": 1,
            "preWork": 1,
            "postAdvanceCalls": 1,
            "postWork": 1,
        },
        "sourceOutcome": {
            "phase": "COMPLETE",
            "qualityRejected": True,
            "targetRequestAccounted": True,
            "sourceResumed": True,
        },
    }


def _publication_fixture():
    prefix_rows = [
        {"id": "via/%02d" % index, "fingerprint": "fp/%02d" % index,
         "kind": "VIA"}
        for index in range(17)
    ]
    pre = {
        "prefix": {"inventory": {"rows": prefix_rows}},
        "prefixTraces": [{} for _ in range(88)],
        "path": {"planarRuns": [{}], "transitions": 0},
    }
    post = {
        "rows": copy.deepcopy(prefix_rows),
        "createdTraceRows": [{}],
        "inventoryObject": {"createdHoleCount": 0},
        "createdHoleIds": [],
        "transitionNodes": [],
        "source": {"publisherStage": 6},
    }
    publisher = {
        "completed": True,
        "stage": 6,
        "replayComplete": True,
        "preHoleCount": 17,
        "preViaCount": 17,
        "preTraceCount": 88,
        "postHoleCount": 17,
        "postViaCount": 17,
        "postTraceCount": 89,
        "createdHoleCount": 0,
        "createdViaCount": 0,
        "createdTraceCount": 1,
    }
    return pre, post, publisher


class Cost240RecurrenceTests(unittest.TestCase):
    def test_primary_secondary_turn_and_flip_charges_at_240(self):
        nodes = _both_face_turn_flip_path(240, primary_layer=1)
        self.assertEqual(307, reader._validate_cost_recurrence(nodes, 240, 1))

    def test_same_path_recomputes_with_original_cost_60(self):
        nodes = _both_face_turn_flip_path(60, primary_layer=1)
        self.assertEqual(127, reader._validate_cost_recurrence(nodes, 60, 1))

    def test_top_primary_mapping_is_an_independent_valid_case(self):
        nodes = _both_face_turn_flip_path(240, primary_layer=0)
        self.assertEqual(307, reader._validate_cost_recurrence(nodes, 240, 0))

    def test_secondary_seed_includes_two_unit_seed_charge(self):
        for primary_layer in (0, 1):
            with self.subTest(primary_layer=primary_layer):
                nodes = _secondary_seed_path(primary_layer=primary_layer)
                self.assertEqual(
                    311, reader._validate_cost_recurrence(
                        nodes, 240, primary_layer))

    def test_missing_secondary_seed_charge_is_rejected(self):
        for primary_layer in (0, 1):
            with self.subTest(primary_layer=primary_layer):
                nodes = _secondary_seed_path(primary_layer=primary_layer)
                nodes[0]["cost"] = 0
                with self.assertRaises(reader.Load12Cost240InputError):
                    reader._validate_cost_recurrence(
                        nodes, 240, primary_layer)

    def test_wrong_primary_planar_cost_is_rejected(self):
        for primary_layer in (0, 1):
            with self.subTest(primary_layer=primary_layer):
                nodes = _both_face_turn_flip_path(
                    240, primary_layer=primary_layer)
                nodes[1]["cost"] += 1
                with self.assertRaises(reader.Load12Cost240InputError):
                    reader._validate_cost_recurrence(
                        nodes, 240, primary_layer)

    def test_wrong_turn_charge_is_rejected(self):
        for primary_layer in (0, 1):
            with self.subTest(primary_layer=primary_layer):
                nodes = _both_face_turn_flip_path(
                    240, primary_layer=primary_layer)
                nodes[2]["cost"] -= 35
                with self.assertRaises(reader.Load12Cost240InputError):
                    reader._validate_cost_recurrence(
                        nodes, 240, primary_layer)

    def test_wrong_secondary_face_turn_charge_is_rejected(self):
        for primary_layer in (0, 1):
            with self.subTest(primary_layer=primary_layer):
                nodes = _secondary_seed_path(primary_layer=primary_layer)
                nodes[2]["cost"] -= 35
                with self.assertRaises(reader.Load12Cost240InputError):
                    reader._validate_cost_recurrence(
                        nodes, 240, primary_layer)

    def test_wrong_secondary_face_surcharge_is_rejected(self):
        for primary_layer in (0, 1):
            with self.subTest(primary_layer=primary_layer):
                nodes = _both_face_turn_flip_path(
                    240, primary_layer=primary_layer)
                nodes[4]["cost"] -= 2
                with self.assertRaises(reader.Load12Cost240InputError):
                    reader._validate_cost_recurrence(
                        nodes, 240, primary_layer)

    def test_wrong_flip_cost_is_rejected_on_both_cost_modes(self):
        for via_cost in (60, 240):
            for primary_layer in (0, 1):
                with self.subTest(via_cost=via_cost,
                                  primary_layer=primary_layer):
                    nodes = _both_face_turn_flip_path(
                        via_cost, primary_layer=primary_layer)
                    nodes[3]["cost"] += 1
                    with self.assertRaises(reader.Load12Cost240InputError):
                        reader._validate_cost_recurrence(
                            nodes, via_cost, primary_layer)

    def test_invalid_via_cost_is_rejected(self):
        nodes = _both_face_turn_flip_path(240, primary_layer=1)
        for via_cost in (0, 34, 241):
            with self.subTest(via_cost=via_cost):
                with self.assertRaises(reader.Load12Cost240InputError):
                    reader._validate_cost_recurrence(nodes, via_cost, 1)

    def test_flip_must_preserve_cell_and_reset_direction(self):
        nodes = _both_face_turn_flip_path(240, primary_layer=1)
        nodes[3]["x"] += 10
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_cost_recurrence(nodes, 240, 1)

    def test_correct_costs_for_opposite_primary_mapping_are_rejected(self):
        bottom_primary = _both_face_turn_flip_path(240, primary_layer=1)
        top_primary = _both_face_turn_flip_path(240, primary_layer=0)
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_cost_recurrence(bottom_primary, 240, 0)
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_cost_recurrence(top_primary, 240, 1)

    def test_primary_layer_is_required_and_bounded(self):
        nodes = _both_face_turn_flip_path(240, primary_layer=1)
        with self.assertRaises(TypeError):
            reader._validate_cost_recurrence(nodes, 240)
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_cost_recurrence(nodes, 240, 2)


class Cost240PrimaryLayerTests(unittest.TestCase):
    def test_raw_source_layer_names_map_to_grid_ordinals(self):
        for name, ordinal in (("TOP", 0), ("BOTTOM", 1)):
            raw_pre = {"source": {"grid": {"primaryLayer": name}}}
            with self.subTest(primary_layer=name):
                self.assertEqual(
                    ordinal, reader._primary_layer_ordinal(
                        raw_pre, "TEST_PRIMARY_LAYER"))

    def test_unknown_primary_layer_is_rejected(self):
        raw_pre = {"source": {"grid": {"primaryLayer": "LEFT"}}}
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._primary_layer_ordinal(raw_pre, "TEST_PRIMARY_LAYER")


class Cost240ProtocolTests(unittest.TestCase):
    def test_frozen_graphspec_sha_matches_authoritative_literal(self):
        expected = "602fb4b250f22e8ab55b40ee124ac9dfc7b6575d3268132ae2123b0076b78df1"
        self.assertEqual(64, len(expected))
        self.assertEqual(expected, reader.SOURCE_GRAPH_SPEC_SHA256)

    def test_frozen_graphspec_and_current_input_counts_stay_distinct(self):
        self.assertEqual("p07-load12-detached-cost240-input-v2",
                         reader.INPUT_SCHEMA)
        self.assertEqual(1080, reader.SOURCE_INPUT_COUNT)
        self.assertEqual(1079, reader.FROZEN_SOURCE_GRAPH_INPUT_COUNT)
        self.assertEqual(
            "d16c7385a604aa3e126ae7fd39f98d6b8fe5753c2ea6bb2d8244dc8b74ce5cbc",
            reader.FROZEN_SOURCE_GRAPH_READER_SHA256)
        self.assertEqual(
            "4475aaded5c56c79ab236ba58fdefa1ca89926e08df67a151aacdd7a0b52e112",
            reader.FROZEN_SOURCE_GRAPH_EXPORTER_SHA256)
        self.assertNotEqual(reader.FROZEN_SOURCE_GRAPH_READER_SHA256,
                            reader.QUALITY_READER_SHA256)
        self.assertEqual(86847809, reader.FROZEN_SOURCE_GRAPH_INPUT_BYTES)

        spec = _current_input_spec_fixture()
        legacy = reader._validate_input_spec_identity(spec)
        self.assertNotIn("frozenOracleDirectory", legacy)
        old_spec = dict(spec)
        del old_spec["frozenOracleDirectory"]
        with self.assertRaises(reader.Load12Cost240InputError) as caught:
            reader._validate_input_spec_identity(old_spec)
        self.assertEqual(caught.exception.code, "INPUT_SPEC_KEYS_INVALID")
        with self.assertRaises(reader.Load12Cost240InputError) as caught:
            reader._validate_input_spec_identity(
                dict(spec, schema="p07-load12-detached-cost240-input-v1"))
        self.assertEqual(caught.exception.code, "INPUT_SPEC_IDENTITY_INVALID")
        with self.assertRaises(reader.Load12Cost240InputError) as caught:
            reader._validate_input_spec_identity(dict(spec, inputCount=1079))
        self.assertEqual(caught.exception.code, "INPUT_SPEC_IDENTITY_INVALID")
        with self.assertRaises(reader.Load12Cost240InputError) as caught:
            reader._validate_input_spec_identity(
                dict(spec, frozenOracleDirectory="C:\\private\\frozen-oracle"))
        self.assertEqual(caught.exception.code, "INPUT_SPEC_IDENTITY_INVALID")

        evidence = _source_graphspec_evidence_fixture()
        reader._validate_source_graphspec_evidence(evidence)
        with self.assertRaises(reader.Load12Cost240InputError) as caught:
            reader._validate_source_graphspec_evidence(
                dict(evidence, inputCount=1080))
        self.assertEqual(caught.exception.code,
                         "SOURCE_GRAPH_SPEC_EVIDENCE_INVALID")
        tampered_current = dict(evidence)
        tampered_current["readerAndHelperSha256"] = dict(
            evidence["readerAndHelperSha256"],
            currentReader=reader.QUALITY_READER_SHA256)
        with self.assertRaises(reader.Load12Cost240InputError) as caught:
            reader._validate_source_graphspec_evidence(tampered_current)
        self.assertEqual(caught.exception.code,
                         "SOURCE_GRAPH_SPEC_EVIDENCE_INVALID")
        tampered_exporter = dict(
            evidence, exporterSha256=
            "63e6457f4af9f45cdf620b172a90114fb4b3f2bd8779bc104ae6f78b935287c2")
        with self.assertRaises(reader.Load12Cost240InputError) as caught:
            reader._validate_source_graphspec_evidence(tampered_exporter)
        self.assertEqual(caught.exception.code,
                         "SOURCE_GRAPH_SPEC_EVIDENCE_INVALID")

    def test_current_checkout_cannot_supply_the_frozen_exporter(self):
        with self.assertRaises(reader.Load12Cost240InputError) as caught:
            reader._load_frozen_graphspec_exporter(
                Path(reader.__file__).resolve().parent)
        self.assertEqual(caught.exception.code,
                         "FROZEN_GRAPH_SPEC_EXPORTER_HASH_MISMATCH")

    def test_main_returns_sanitized_exit_two_for_reader_error(self):
        output = io.StringIO()
        with redirect_stdout(output):
            exit_code = reader.main(["--invalid"])
        self.assertEqual(2, exit_code)
        self.assertEqual("FAIL: CLI_ARGUMENTS_INVALID\n", output.getvalue())

    def test_source_and_detached_settings_bind_both_caps_and_costs(self):
        source, clone = _source_clone_settings()
        reader._validate_source_clone_settings(source, clone)

    def test_source_cap_cannot_change(self):
        source, clone = _source_clone_settings()
        source["sessionMaximumVias"] = 47
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_source_clone_settings(source, clone)

    def test_detached_cost_and_cap_are_closed(self):
        for field, bad in (("requestedViaCost", 60),
                           ("searchMaximumVias", 47),
                           ("searchViaCost", 60)):
            with self.subTest(field=field):
                source, clone = _source_clone_settings()
                clone[field] = bad
                with self.assertRaises(reader.Load12Cost240InputError):
                    reader._validate_source_clone_settings(source, clone)

    def test_source_default_cost_cannot_change(self):
        source, clone = _source_clone_settings()
        source["sessionViaCost"] = 61
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_source_clone_settings(source, clone)

    def test_source_after_replay_requires_original_frontier_and_cost(self):
        seeds = {"queueSize": 2, "nodes": [{"heapIndex": 0},
                                            {"heapIndex": 1}]}
        value = _source_after_replay(seeds)
        initial = {"seeds": seeds}
        reader._validate_source_after_replay(value, initial)
        for field, bad in (("sessionViaCost", 240),
                           ("branchExpanded", 1),
                           ("holeCount", 18),
                           ("searchMaximumVias", 17)):
            with self.subTest(field=field):
                altered = copy.deepcopy(value)
                altered[field] = bad
                with self.assertRaises(reader.Load12Cost240InputError):
                    reader._validate_source_after_replay(altered, initial)
        altered_frontier = copy.deepcopy(value)
        altered_frontier["frontier"]["seeds"] = {"queueSize": 1}
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_source_after_replay(altered_frontier, initial)

    def test_source_after_replay_rejects_extra_fields(self):
        seeds = {"queueSize": 2}
        value = _source_after_replay(seeds)
        value["hidden"] = True
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_source_after_replay(value, {"seeds": seeds})

    def test_cost240_limits_are_exact(self):
        limits = {
            "operationMs": 90_000,
            "workUnits": 640,
            "unitMs": 5_000,
            "cleanupMs": 120_000,
            "quantum": 32_768,
            "maximumSourceVias": 48,
            "maximumDetachedVias": 48,
            "sourceViaCost": 60,
            "detachedViaCost": 240,
            "maximumTransitions": 4,
        }
        reader._validate_limits(limits)
        altered = dict(limits, detachedViaCost=211)
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_limits(altered)

    def test_closed_event_rejects_unknown_keys(self):
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_event({"unrecognized": True}, {}, {}, None, None)
        event_with_extra = {key: None for key in reader.EVENT_KEYS}
        event_with_extra["unexpected"] = True
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_event(event_with_extra, {}, {}, None, None)

    def test_work_and_source_resume_must_be_complete(self):
        work = _work_fixture()
        pre = {"work": {"advanceCalls": 1, "workSteps": 1}}
        post = {"work": {"advanceCalls": 1, "workSteps": 1}}
        reader._validate_work(work, pre, post)
        altered = copy.deepcopy(work)
        altered["sourceOutcome"]["sourceResumed"] = False
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_work(altered, pre, post)
        altered = copy.deepcopy(work)
        altered["sourceOperation"]["operationMs"] = 90_001
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_work(altered, pre, post)

    def test_normal_publisher_counts_and_stage_are_checked_from_raw_rows(self):
        pre, post, publisher = _publication_fixture()
        reader._validate_normal_publisher(pre, post, publisher)
        altered = dict(publisher, stage=5)
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_normal_publisher(pre, post, altered)
        altered = dict(publisher, createdViaCount=1)
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_normal_publisher(pre, post, altered)

    def test_graph_path_oracle_allows_alternate_endpoint_but_requires_edges(self):
        graph_reader, _raw, _path = reader._load_pinned_pure_reader(
            Path(reader.__file__).resolve().parent)
        goal_bits = "01"
        graphspec = {
            "grid": {"cells": 2, "endCell": 0, "endLayer": 0},
            "load12Pre": {"goal": {"faceBits": goal_bits}},
            "sourceGraph": {"event": {"diagnostic": {"masks": {
                "topStepsHex": "20",
                "bottomStepsHex": "00",
                "goalFacesHex": goal_bits,
            }}}},
        }
        pre = {
            "pre": {"goal": {
                "faceBits": goal_bits,
                "faceBitsSha256": hashlib.sha256(
                    goal_bits.encode("ascii")).hexdigest(),
            }},
            "path": {
                "nodes": [
                    {"cell": 0, "x": 0, "y": 0, "layer": 0},
                    {"cell": 1, "x": 10, "y": 0, "layer": 0},
                ],
                "gridSteps": 1,
                "transitions": 0,
            },
        }
        result = graph_reader._validate_graphspec_path_edges(pre, graphspec)
        self.assertEqual(1, result["endpointCell"])
        altered = copy.deepcopy(graphspec)
        altered["sourceGraph"]["event"]["diagnostic"]["masks"][
            "topStepsHex"] = "00"
        with self.assertRaises(graph_reader.Load12Cap17InputError):
            graph_reader._validate_graphspec_path_edges(pre, altered)
        altered = copy.deepcopy(pre)
        altered["path"]["nodes"][-1]["cell"] = 0
        with self.assertRaises(graph_reader.Load12Cap17InputError):
            graph_reader._validate_graphspec_path_edges(altered, graphspec)


class Cost240ReceiptPrefixTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        _quality, _frozen, cls.scalar, cls.ledger, _capture, _hashes = (
            reader._load_helpers())
        cls.graph_reader, _raw, _path = reader._load_pinned_pure_reader(
            Path(reader.__file__).resolve().parent)

    def _prefix_lines(self):
        # Reuse the full scalar fixture's actual record shapes, then adapt its
        # protocol revision to the cost240 input epoch before trimming summary.
        source = scalar_fixtures._receipt_lines()[:7]
        expected_markers = tuple(self.scalar.RECEIPT_MARKERS[:7])
        lines = []
        for line, marker in zip(source, expected_markers):
            source_marker, payload = line.split(" ", 1)
            self.assertEqual(marker, source_marker)
            record = json.loads(payload)
            if "sourceRevision" in record:
                record["sourceRevision"] = self.scalar.SOURCE_REVISION
            lines.append(marker + " " + json.dumps(
                record, separators=(",", ":")))
        self.assertEqual(7, len(lines))
        return lines

    def test_complete_receipt_uses_the_pinned_seven_line_prefix_contract(self):
        prefix = self._prefix_lines()
        receipt = prefix + [
            reader.COST240_EVENT_MARKER + " {}",
            reader.COST240_SUMMARY_PREFIX + "1",
        ]
        stdout = "\n".join(receipt + [
            reader.FOCUSED_PASS, reader.CLEANUP_LINE,
        ]).encode("utf-8")
        _console, checked_receipt, _stderr, scalar_prefix = (
            reader._validate_console_and_receipt(
                stdout, "\n".join(receipt).encode("utf-8"), b"",
                self.ledger, self.scalar, self.graph_reader))
        self.assertEqual(receipt, checked_receipt)
        self.assertEqual(scalar_fixtures.RUN_ID, scalar_prefix["runId"])

    def test_missing_scalar_prefix_record_is_rejected(self):
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_scalar_prefix(
                self._prefix_lines()[:-1], self.ledger, self.scalar,
                self.graph_reader)

    def test_duplicate_scalar_prefix_marker_is_rejected(self):
        lines = self._prefix_lines()
        lines[6] = lines[5]
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_scalar_prefix(
                lines, self.ledger, self.scalar, self.graph_reader)

    def test_bad_scalar_pair_is_rejected(self):
        lines = self._prefix_lines()
        marker, payload = lines[6].split(" ", 1)
        pair = json.loads(payload)
        pair["runId"] = "d1c10000-0000-4000-8000-000000000002"
        lines[6] = marker + " " + json.dumps(pair, separators=(",", ":"))
        with self.assertRaises(reader.Load12Cost240InputError):
            reader._validate_scalar_prefix(
                lines, self.ledger, self.scalar, self.graph_reader)


class Cost240GraphOracleNegativeTests(unittest.TestCase):
    def test_bottom_face_missing_edge_is_rejected(self):
        graph_reader, _raw, _path = reader._load_pinned_pure_reader(
            Path(reader.__file__).resolve().parent)
        goal_bits = "02"
        graphspec = {
            "grid": {"cells": 2, "endCell": 0, "endLayer": 0},
            "load12Pre": {"goal": {"faceBits": goal_bits}},
            "sourceGraph": {"event": {"diagnostic": {"masks": {
                "topStepsHex": "00",
                "bottomStepsHex": "20",
                "goalFacesHex": goal_bits,
            }}}},
        }
        pre = {
            "pre": {"goal": {
                "faceBits": goal_bits,
                "faceBitsSha256": hashlib.sha256(
                    goal_bits.encode("ascii")).hexdigest(),
            }},
            "path": {
                "nodes": [
                    {"cell": 0, "x": 0, "y": 0, "layer": 1},
                    {"cell": 1, "x": 10, "y": 0, "layer": 1},
                ],
                "gridSteps": 1,
                "transitions": 0,
            },
        }
        self.assertEqual(
            1, graph_reader._validate_graphspec_path_edges(pre, graphspec)[
                "planarEdges"])
        altered = copy.deepcopy(graphspec)
        altered["sourceGraph"]["event"]["diagnostic"]["masks"][
            "bottomStepsHex"] = "00"
        with self.assertRaises(graph_reader.Load12Cap17InputError):
            graph_reader._validate_graphspec_path_edges(pre, altered)


if __name__ == "__main__":
    unittest.main()
