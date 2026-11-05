"""Independent fixtures for the bounded LOAD12 cap17 consumer."""
from __future__ import annotations

import copy
import hashlib
import unittest
from pathlib import Path

import read_load12_cap17 as reader


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


def _goal_hash(bits):
    return hashlib.sha256(bits.encode("ascii")).hexdigest()


def _graph_path_fixture():
    goal_bits = "0011"
    nodes = [
        {"cell": 0, "x": 0, "y": 0, "layer": 0},
        {"cell": 1, "x": 10, "y": 0, "layer": 0},
        {"cell": 3, "x": 10, "y": 10, "layer": 0},
    ]
    graphspec = {
        "grid": {"cells": 4, "endCell": 2, "endLayer": 0},
        "load12Pre": {"goal": {"faceBits": goal_bits}},
        "sourceGraph": {"event": {"diagnostic": {"masks": {
            "topStepsHex": "2400",
            "bottomStepsHex": "0000",
            "goalFacesHex": goal_bits,
        }}}},
    }
    pre = {
        "pre": {"goal": {"faceBits": goal_bits,
                           "faceBitsSha256": _goal_hash(goal_bits)}},
        "path": {"nodes": nodes, "gridSteps": 2, "transitions": 0},
    }
    return graphspec, pre


def _publication_fixture():
    rows = [{"id": "via/%02d" % index, "kind": "VIA"}
            for index in range(17)]
    pre = {
        "pre": {"source": {"grid": {"maximumVias": 17}}},
        "prefix": {"inventory": {"rows": rows}},
        "prefixTraces": [{} for _ in range(88)],
        "path": {"transitions": 0},
    }
    post = {
        "inventoryObject": {
            "createdHoleCount": 0, "createdHoleIds": [], "holeCount": 17,
            "transitionBindings": [],
        },
        "rows": [dict(row) for row in rows],
        "createdTraceRows": [{}],
        "transitionNodes": [],
        "createdHoleIds": [],
        "source": {"preHoleCount": 17, "preTraceCount": 88,
                   "publisherStage": 6},
        "work": {"holeRows": 17, "traceRows": 89, "transitionMatches": 0},
    }
    publisher = {
        "completed": True, "stage": 6, "replayComplete": True,
        "preHoleCount": 17, "preViaCount": 17, "preTraceCount": 88,
        "postHoleCount": 17, "postViaCount": 17, "postTraceCount": 89,
        "createdHoleCount": 0, "createdViaCount": 0, "createdTraceCount": 1,
    }
    return pre, post, publisher


def _work_fixture():
    pre = {"work": {"advanceCalls": 1, "workSteps": 2}}
    post = {"work": {"advanceCalls": 1, "workSteps": 2}}
    value = {
        "sourceOperation": {
            "operationMs": 1, "workUnits": 1, "maxUnitMs": 1,
            "cleanupMs": 0, "manualGroups": 1, "manualSliceSteps": 1,
        },
        "copy": {
            "advanceCalls": 1, "workSteps": 1, "copiedEntries": 1,
            "lastWork": 1, "observerChecks": 1,
        },
        "replay": {
            "toPreCalls": 1, "toPreWork": 1,
            "toPostCalls": 1, "toPostWork": 1,
        },
        "cloneCursor": {
            "preAdvanceCalls": 1, "preWork": 1,
            "postAdvanceCalls": 1, "postWork": 1,
        },
        "sourceCursor": {
            "preAdvanceCalls": 1, "preWork": 1,
            "postAdvanceCalls": 1, "postWork": 1,
        },
        "sourceOutcome": {
            "phase": "COMPLETE", "qualityRejected": True,
            "targetRequestAccounted": True, "sourceResumed": True,
        },
    }
    return value, pre, post


def _paused_source_fixture():
    seeds = {"queueSize": 2, "faceMask": 3}
    source_pre = {"pre": {"seeds": seeds}}
    source_initial = {"seeds": seeds}
    after_replay = {
        "sessionMaximumVias": 48, "searchMaximumVias": 48,
        "branchExpanded": 0, "holeCount": 17, "traceCount": 88,
        "seeds": seeds,
    }
    return source_initial, source_pre, after_replay


def _comparison_fixture():
    source_initial_seed, _, source_after_replay = _paused_source_fixture()
    seeds = source_initial_seed["seeds"]
    net_order = ["LOAD12"]
    prefix_rows = [{"id": "via/old", "fingerprint": "fingerprint/old"}]
    goal = {"faceBits": "1"}
    source_pre = {"pre": {
        "seeds": seeds,
        "source": {"netOrder": net_order},
        "prefixInventory": {"rows": prefix_rows},
        "goal": goal,
    }}
    clone_pre = {
        "pre": {
            "seeds": seeds,
            "source": {"netOrder": net_order},
            "prefixInventory": {"rows": prefix_rows},
            "goal": goal,
        },
        "prefix": {"inventory": {"rows": [{"id": "via/old"}]}},
    }
    source_initial = {
        "seeds": seeds, "netOrder": net_order,
        "prefixRows": prefix_rows,
        "preGeometrySha256": reader.SOURCE_PREFIX_GEOMETRY_SHA256,
    }
    clone_initial = dict(source_initial)
    post = {
        "inventoryObject": {"createdHoleCount": 0, "createdHoleIds": []},
        "rows": [{"id": "via/old", "kind": "VIA"}],
    }
    work = {"sourceOutcome": {"qualityRejected": True, "sourceResumed": True}}
    original = {
        "path": {"pathTupleSha256": "a" * 64},
        "_preChecked": {
            "source": {"grid": {"maximumVias": 48}},
            "pre": {"seeds": seeds},
            "path": {"publisher": {"stage": 1}},
        },
        "_postChecked": {"source": {
            "pass": 1, "publisherStage": 6,
            "searchBranchIsNull": True, "searchPublisherIsNull": True,
            "preHoleCount": 17, "preTraceCount": 88,
        }},
        "_preCounts": {"holeCount": 17, "viaCount": 17, "traceCount": 88},
        "_postCounts": {
            "holeCount": 21, "viaCount": 21, "traceCount": 93,
            "createdViaCount": 4, "createdTraceCount": 5,
        },
    }
    clone_path = {"pathTupleSha256": "b" * 64}
    comparison = {
        "initialSeedsEqual": True,
        "initialNetOrderEqual": True,
        "initialPrefixRowsEqual": True,
        "initialPrefixGeometryEqual": True,
        "fullGoalFaceBitsEqual": True,
        "pathTupleEqualObserved": False,
        "postGeometryEqualObserved": False,
        "publicationEqualObserved": False,
        "prefixRetained": True,
        "zeroNewVias": True,
        "sourcePreserved": True,
        "sourceResumed": True,
    }
    return (comparison, source_initial, clone_initial, source_pre,
            clone_pre, source_after_replay, post, work, original, clone_path)


def _source_pre_binding_fixture():
    seeds = {"schema": "p07-accepted-branch-seeds-r1",
             "queueSize": 2, "faceMask": 3, "nodes": [{"heapIndex": 0},
                                                          {"heapIndex": 1}]}
    grid = {"maximumVias": 48, "width": 478, "height": 309}
    source = {"netId": "LOAD12", "branchOrdinal": 73, "grid": grid}
    rows = [{"id": "via/%02d" % index,
             "kind": "VIA", "fingerprint": "fingerprint/%02d" % index}
            for index in range(17)]
    traces = [{"index": index, "traceFingerprint": "trace/%02d" % index}
              for index in range(88)]
    path = {"path": {"tupleSha256": reader.SOURCE_GRAPH_PATH_SHA256},
            "publisher": {"stage": 1}}
    raw = {
        "source": source,
        "seeds": seeds,
        "goal": {"faceBits": "0011",
                 "faceBitsSha256": reader.SOURCE_GOAL_SHA256},
        "prefixInventory": {"rows": rows, "stability": {
            "prefixGeometrySha256": reader.SOURCE_PREFIX_GEOMETRY_SHA256}},
    }
    normalized = {
        "pre": raw,
        "source": {"source": source, "grid": grid},
        "seeds": seeds,
        "prefix": {"inventory": {"rows": rows}},
        "prefixTraces": traces,
        "path": path,
        "work": {}, "stability": {},
    }
    return normalized


def _original_post_fixture():
    rows = [{"id": "via/%02d" % index, "kind": "VIA"}
            for index in range(17)]
    created_ids = ["via/new/%02d" % index for index in range(4)]
    rows.extend({"id": hole_id, "kind": "VIA"} for hole_id in created_ids)
    created_traces = [{} for _ in range(5)]
    source = {"pass": 1, "publisherStage": 6,
              "searchBranchIsNull": True, "searchPublisherIsNull": True,
              "preHoleCount": 17, "preTraceCount": 88}
    inventory_object = {
        "holeCount": 21, "createdHoleCount": 4, "createdTraceCount": 5,
        "holeRows": rows, "createdHoleIds": created_ids,
        "createdTraceRows": created_traces,
        "retainedHoleBindings": [], "retainedTraceBindings": [],
        "transitionBindings": [],
    }
    raw_post = {
        "schema": "p07-accepted-branch-post-r1",
        "source": source, "post": inventory_object,
        "work": {}, "stability": {}, "claims": {},
    }
    return {
        "post": raw_post,
        "inventoryObject": inventory_object,
        "source": source,
        "inventory": {"totalHoles": 21, "netCounts": [],
                       "byNet": [], "rows": rows},
        "rows": rows,
        "createdHoleIds": created_ids,
        "createdTraceRows": created_traces,
        "transitionNodes": [], "work": {}, "stability": {}, "claims": {},
    }


def _original_summary_fixture():
    source_pre = _source_pre_binding_fixture()
    original_post = _original_post_fixture()
    graphspec = {
        "sourcePath": {
            "tupleSha256": reader.SOURCE_GRAPH_PATH_SHA256,
            "nodeCount": 222, "length": 2170, "bends": 2,
            "transitions": 4,
        },
        "load12Pre": {"goal": {"faceBitsSha256": reader.SOURCE_GOAL_SHA256}},
        "prefixGeometrySha256": reader.SOURCE_PREFIX_GEOMETRY_SHA256,
    }
    value = {
        "path": {
            "pathTupleSha256": reader.SOURCE_GRAPH_PATH_SHA256,
            "goalFaceBitsSha256": reader.SOURCE_GOAL_SHA256,
            "nodeCount": 222, "planarLength": 2170,
            "bends": 2, "flips": 4,
        },
        "pre": {"holeCount": 17, "viaCount": 17, "traceCount": 88},
        "post": {"holeCount": 21, "viaCount": 21, "traceCount": 93,
                 "createdViaCount": 4, "createdTraceCount": 5},
        "preGeometrySha256": reader.SOURCE_PREFIX_GEOMETRY_SHA256,
        "postGeometrySha256": "a" * 64,
        "publicationSha256": "b" * 64,
        "preResult": {"cached": "pre"},
        "postResult": {"cached": "post"},
    }

    class _Quality:
        class QualityZeroViaInputError(ValueError):
            pass

        def __init__(self, checked_pre, checked_post):
            self.pre_result = None
            self.post_pre = None
            self.expected_cap = None
            self.source_pre = copy.deepcopy(checked_pre)
            self.original_post = checked_post

        def _validate_load12_pre(self, raw, expected_maximum_vias):
            self.pre_result = raw
            self.expected_cap = expected_maximum_vias
            return self.source_pre

        def _validate_load12_post(self, raw, pre):
            self.post_pre = pre
            return self.original_post

    return graphspec, value, _Quality(source_pre, original_post), source_pre, original_post


class SourceMaskTests(unittest.TestCase):
    def test_clone_edges_use_source_direction_bits_and_any_goal_face(self):
        graphspec, pre = _graph_path_fixture()
        checked = reader._validate_graphspec_path_edges(pre, graphspec)
        self.assertEqual(checked, {
            "planarEdges": 2, "transitions": 0,
            "endpointCell": 3, "endpointLayer": 0,
        })
        self.assertNotEqual(checked["endpointCell"], graphspec["grid"]["endCell"])

    def test_wrong_planar_direction_bit_is_rejected(self):
        graphspec, pre = _graph_path_fixture()
        graphspec["sourceGraph"]["event"]["diagnostic"]["masks"][
            "topStepsHex"] = "0400"
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_graphspec_path_edges(pre, graphspec)
        self.assertEqual(caught.exception.code,
                         "CAP17_CLONE_EDGE_MISSING_FROM_SOURCE_MASK")

    def test_clone_endpoint_must_have_a_goal_face_bit(self):
        graphspec, pre = _graph_path_fixture()
        bits = "1000"
        graphspec["load12Pre"]["goal"]["faceBits"] = bits
        graphspec["sourceGraph"]["event"]["diagnostic"]["masks"][
            "goalFacesHex"] = bits
        pre["pre"]["goal"]["faceBits"] = bits
        pre["pre"]["goal"]["faceBitsSha256"] = _goal_hash(bits)
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_graphspec_path_edges(pre, graphspec)
        self.assertEqual(caught.exception.code,
                         "CAP17_CLONE_ENDPOINT_NOT_IN_SOURCE_GOAL_MASK")


class PublicationLineageTests(unittest.TestCase):
    def test_normal_publisher_retains_all_prefix_rows_without_new_vias(self):
        pre, post, publisher = _publication_fixture()
        self.assertEqual(reader._validate_cap17_publication(
            pre, post, publisher), {
                "prefixRowsRetained": True, "createdVias": 0,
                "createdTraces": 1, "publisherStage": 6,
            })

    def test_wrong_post_row_or_created_via_fails_lineage(self):
        pre, post, publisher = _publication_fixture()
        post["rows"][0] = dict(post["rows"][0], id="other/via")
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_cap17_publication(pre, post, publisher)
        self.assertEqual(caught.exception.code,
                         "CAP17_POST_PREFIX_OR_CREATION_LINEAGE_INVALID")

        pre, post, publisher = _publication_fixture()
        post["inventoryObject"]["createdHoleCount"] = 1
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_cap17_publication(pre, post, publisher)
        self.assertEqual(caught.exception.code,
                         "CAP17_POST_PREFIX_OR_CREATION_LINEAGE_INVALID")


class ComparisonShapeTests(unittest.TestCase):
    def test_comparison_uses_helper_normalized_post_rows(self):
        (comparison, source_initial, clone_initial, source_pre,
         clone_pre, source_after, post, work, original, clone_path) = \
            _comparison_fixture()
        self.assertNotIn("holeRows", post)
        checked = reader._validate_comparisons(
            comparison, source_initial, clone_initial, source_pre,
            clone_pre, source_after, post, work, original, clone_path)
        self.assertTrue(checked["zeroNewVias"])

    def test_inconsistent_new_via_comparison_is_rejected_from_rows(self):
        (comparison, source_initial, clone_initial, source_pre,
         clone_pre, source_after, post, work, original, clone_path) = \
            _comparison_fixture()
        post["rows"].append({"id": "via/new", "kind": "VIA"})
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_comparisons(
                comparison, source_initial, clone_initial, source_pre,
                clone_pre, source_after, post, work, original, clone_path)
        self.assertEqual(caught.exception.code,
                         "CAP17_COMPARISON_DATA_MISMATCH")

    def test_source_preservation_flag_is_derived_from_paused_snapshot(self):
        (comparison, source_initial, clone_initial, source_pre,
         clone_pre, source_after, post, work, original, clone_path) = \
            _comparison_fixture()
        source_after["branchExpanded"] = 1
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_comparisons(
                comparison, source_initial, clone_initial, source_pre,
                clone_pre, source_after, post, work, original, clone_path)
        self.assertEqual(caught.exception.code,
                         "CAP17_COMPARISON_DATA_MISMATCH")


class ReplaySnapshotTests(unittest.TestCase):
    def test_source_state_after_clone_replay_is_bound_to_initial_seeds(self):
        initial, source_pre, after_replay = _paused_source_fixture()
        self.assertEqual(reader._validate_source_after_replay(
            after_replay, initial, source_pre), after_replay)

        invalid_cases = (
            ("sessionMaximumVias", 17),
            ("searchMaximumVias", 17),
            ("branchExpanded", 1),
            ("holeCount", 16),
            ("traceCount", 87),
            ("seeds", {"queueSize": 2, "faceMask": 1}),
            ("branchExpanded", True),
        )
        for field, bad_value in invalid_cases:
            with self.subTest(field=field, bad_value=bad_value):
                changed = copy.deepcopy(after_replay)
                changed[field] = bad_value
                with self.assertRaises(reader.Load12Cap17InputError) as caught:
                    reader._validate_source_after_replay(
                        changed, initial, source_pre)
                self.assertEqual(caught.exception.code,
                                 "CAP17_SOURCE_AFTER_REPLAY_STATE_INVALID")


class OriginalCaptureTests(unittest.TestCase):
    def test_cached_original_results_are_helper_validated_and_summary_bound(self):
        graphspec, value, quality, graph_pre, _ = _original_summary_fixture()
        checked = reader._validate_original_source(
            value, graphspec, graph_pre, quality)
        self.assertEqual(quality.expected_cap, 48)
        self.assertIs(quality.pre_result, value["preResult"])
        self.assertIs(quality.post_pre, quality.source_pre)
        self.assertEqual(checked["_preCounts"], value["pre"])
        self.assertEqual(checked["_postCounts"], value["post"])
        self.assertEqual(checked["_postChecked"]["inventoryObject"]["holeCount"], 21)
        self.assertNotIn("holeCount", checked["_postChecked"]["post"])

    def test_original_pre_and_post_must_match_independent_captured_data(self):
        graphspec, value, quality, graph_pre, _ = _original_summary_fixture()
        quality.source_pre["pre"]["goal"]["faceBits"] = "1000"
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_original_source(value, graphspec, graph_pre, quality)
        self.assertEqual(caught.exception.code,
                         "CAP17_ORIGINAL_PRE_SOURCE_BINDING_INVALID")

        graphspec, value, quality, graph_pre, _ = _original_summary_fixture()
        value["post"]["createdViaCount"] = 3
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_original_source(value, graphspec, graph_pre, quality)
        self.assertEqual(caught.exception.code,
                         "CAP17_ORIGINAL_POST_COUNTS_INVALID")


class WorkConservationTests(unittest.TestCase):
    def test_bounded_work_fixture_passes_and_copy_overrun_fails(self):
        value, pre, post = _work_fixture()
        self.assertEqual(reader._validate_work(value, pre, post)[
            "sourceOutcome"]["sourceResumed"], True)

        value, pre, post = _work_fixture()
        value["copy"]["workSteps"] = reader.MAX_WORK_QUANTUM + 1
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_work(value, pre, post)
        self.assertEqual(caught.exception.code,
                         "CAP17_COPY_WORK_CONSERVATION_INVALID")

    def test_clone_cursor_must_match_validated_pre_and_post_work(self):
        value, pre, post = _work_fixture()
        value["cloneCursor"]["postAdvanceCalls"] = 2
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_work(value, pre, post)
        self.assertEqual(caught.exception.code,
                         "CAP17_CLONE_CURSOR_RESULT_MISMATCH")


class NativeCommandAdapterTests(unittest.TestCase):
    class _Scalar:
        NATIVE_RUN_KEYS = frozenset(("suite", "command"))

        class QualityScalarInputError(ValueError):
            pass

        def __init__(self):
            self.seen = None

        def _validate_native_run(self, value, context, stdout, receipt, stderr):
            self.seen = value

    def test_exact_cap17_argument_is_checked_before_metadata_view(self):
        scalar = self._Scalar()
        command = {
            "entry": reader.ENTRY_SCRIPT,
            "suite": reader.SUITE,
            "suites": [reader.SUITE],
            "args": ["-Rb100Load12Cap17Replay"],
            "environment": {},
        }
        run = {"suite": reader.SUITE, "command": command, "target": "LOAD12"}
        reader._validate_native_run(run, {}, b"", b"", b"", scalar)
        self.assertEqual(run["command"]["args"], ["-Rb100Load12Cap17Replay"])
        self.assertEqual(scalar.seen["command"]["args"], [])

        wrong = dict(run, command=dict(command, args=[]))
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_native_run(wrong, {}, b"", b"", b"", scalar)
        self.assertEqual(caught.exception.code,
                         "NATIVE_RUN_CAP17_COMMAND_INVALID")


class ClosedInputAndCapTests(unittest.TestCase):
    def test_only_the_detached_copy_has_the_exact_17_via_cap(self):
        source = {"sessionMaximumVias": 48, "searchMaximumVias": 48,
                  "initial": {},
                  "afterReplayBeforeResume": _paused_source_fixture()[2]}
        clone = {
            "requestedMaximumVias": 17, "sessionMaximumVias": 17,
            "searchMaximumVias": 17, "initial": {}, "path": {},
            "preResult": {}, "postResult": {}, "normalPublisher": {},
        }
        reader._validate_cap_pair(source, clone)
        for field, invalid in (("requestedMaximumVias", 18),
                               ("sessionMaximumVias", True),
                               ("searchMaximumVias", 48)):
            bad = dict(clone, **{field: invalid})
            with self.subTest(field=field, invalid=invalid):
                with self.assertRaises(reader.Load12Cap17InputError) as caught:
                    reader._validate_cap_pair(source, bad)
                self.assertEqual(caught.exception.code,
                                 "CAP17_SOURCE_OR_CLONE_CAP_INVALID")

    def test_input_spec_explicitly_binds_both_source_artifacts(self):
        self.assertEqual("p07-load12-detached-cap17-input-v2",
                         reader.INPUT_SCHEMA)
        spec = {
            "schema": reader.INPUT_SCHEMA, "target": "LOAD12",
            "purpose": reader.PURPOSE, "suite": reader.SUITE,
            "sourceRoot": "source", "pinsPath": "pins.json",
            "pinsSha256": "a" * 64, "inputCount": 1080,
            "inputBytes": 100, "sourceSnapshotRevision": 20,
            "resultPath": "result.json", "stdoutPath": "stdout.txt",
            "receiptPath": "receipt.txt", "stderrPath": "stderr.txt",
            "graphspecPath": "graphspec.json",
            "graphspecSha256": "b" * 64,
            "sourceGraphInputSpecPath": "source-graph-input.json",
            "frozenOracleDirectory": "../frozen-oracle/tests/qualification/rb100-layout",
            "sourceGraphInputSpecSha256": reader.SOURCE_GRAPH_INPUT_SPEC_SHA256,
        }
        legacy = reader._validate_input_spec_identity(spec)
        self.assertNotIn("graphspecPath", legacy)
        self.assertNotIn("sourceGraphInputSpecPath", legacy)
        self.assertNotIn("frozenOracleDirectory", legacy)
        old_spec = dict(spec)
        del old_spec["frozenOracleDirectory"]
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_input_spec_identity(old_spec)
        self.assertEqual(caught.exception.code, "INPUT_SPEC_KEYS_INVALID")
        invalid = dict(spec, schema="p07-load12-detached-cap17-input-v1")
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_input_spec_identity(invalid)
        self.assertEqual(caught.exception.code, "INPUT_SPEC_IDENTITY_INVALID")
        for field, value in (("graphspecSha256", "bad"),
                             ("sourceGraphInputSpecSha256", "c" * 64)):
            with self.subTest(field=field):
                invalid = dict(spec, **{field: value})
                with self.assertRaises(reader.Load12Cap17InputError) as caught:
                    reader._validate_input_spec_identity(invalid)
                self.assertEqual(caught.exception.code,
                                 "INPUT_SPEC_IDENTITY_INVALID")
        invalid = dict(spec, inputCount=1079)
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_input_spec_identity(invalid)
        self.assertEqual(caught.exception.code, "INPUT_SPEC_IDENTITY_INVALID")
        invalid = dict(spec, frozenOracleDirectory="C:\\private\\frozen-oracle")
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_input_spec_identity(invalid)
        self.assertEqual(caught.exception.code, "INPUT_SPEC_IDENTITY_INVALID")


class FrozenGraphspecSourceCountTests(unittest.TestCase):
    def test_frozen_graphspec_uses_1079_while_current_spec_uses_1080(self):
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

        evidence = _source_graphspec_evidence_fixture()
        reader._validate_source_graphspec_evidence(evidence)
        tampered_frozen = dict(evidence, inputCount=1080)
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_source_graphspec_evidence(tampered_frozen)
        self.assertEqual(caught.exception.code,
                         "SOURCE_GRAPH_SPEC_EVIDENCE_INVALID")
        tampered_current = dict(evidence)
        tampered_current["readerAndHelperSha256"] = dict(
            evidence["readerAndHelperSha256"],
            currentReader=reader.QUALITY_READER_SHA256)
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_source_graphspec_evidence(tampered_current)
        self.assertEqual(caught.exception.code,
                         "SOURCE_GRAPH_SPEC_EVIDENCE_INVALID")
        tampered_exporter = dict(
            evidence, exporterSha256=
            "63e6457f4af9f45cdf620b172a90114fb4b3f2bd8779bc104ae6f78b935287c2")
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._validate_source_graphspec_evidence(tampered_exporter)
        self.assertEqual(caught.exception.code,
                         "SOURCE_GRAPH_SPEC_EVIDENCE_INVALID")

    def test_current_checkout_cannot_supply_the_frozen_exporter(self):
        with self.assertRaises(reader.Load12Cap17InputError) as caught:
            reader._load_frozen_graphspec_exporter(
                Path(reader.__file__).resolve().parent)
        self.assertEqual(caught.exception.code,
                         "FROZEN_GRAPH_SPEC_EXPORTER_HASH_MISMATCH")

    def test_cli_accepts_only_the_closed_spec_and_digest_pair(self):
        self.assertEqual(reader._parse_cli([
            "--input-spec", "input.json", "--input-spec-sha256", "a" * 64,
        ]), ("input.json", "a" * 64))
        for argv in (
                ["--input-spec", "input.json", "--input-spec-sha256", "bad"],
                ["--input-spec", "input.json", "--input-spec-sha256", "a" * 64,
                 "--unknown", "x"],
                ["--input-spec-sha256", "a" * 64, "--input-spec", "input.json"]):
            with self.subTest(argv=argv):
                with self.assertRaises(reader.Load12Cap17InputError) as caught:
                    reader._parse_cli(argv)
                self.assertEqual(caught.exception.code, "CLI_ARGUMENTS_INVALID")


if __name__ == "__main__":
    unittest.main()
