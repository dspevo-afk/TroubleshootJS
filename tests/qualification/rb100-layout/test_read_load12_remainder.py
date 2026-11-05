"""Independent controls for the finite actual LOAD12 remainder reader."""
from __future__ import annotations

import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import read_load12_remainder as reader


def _sha(label: str) -> str:
    return hashlib.sha256(label.encode("ascii")).hexdigest()


def _not_run_oracle():
    return {
        "status": "NOT_RUN",
        "productionGeometry": "NOT_RUN",
        "twoLayerRules": "NOT_RUN",
        "productionClearancePairs": None,
        "independentClearancePairs": None,
        "graphNetEquality": "NOT_RUN",
        "referenceGraphSha256": None,
        "orderedPadPairsExpected": 60_516,
        "orderedPadPairsChecked": None,
        "rasterPadPairsExpected": 60_516,
        "rasterPadPairsChecked": None,
        "rasterPointCount": None,
        "boardIdentity": "NOT_RUN",
        "p03CourtyardExpected": 4_950,
        "p03CourtyardChecked": None,
        "p03EscapeExpected": 24_354,
        "p03EscapeChecked": None,
        "productionPlacementAccess": {
            "status": "NOT_RUN",
            "gridCells": None,
            "reachableCells": None,
            "cellLimit": 700_000,
        },
    }


def _pass_oracle(label: str):
    return {
        "status": "PASS",
        "productionGeometry": "PASS",
        "twoLayerRules": "PASS",
        "productionClearancePairs": 1_200,
        "independentClearancePairs": 1_100,
        "graphNetEquality": "PASS",
        "referenceGraphSha256": _sha(label + "/graph"),
        "orderedPadPairsExpected": 60_516,
        "orderedPadPairsChecked": 60_516,
        "rasterPadPairsExpected": 60_516,
        "rasterPadPairsChecked": 60_516,
        "rasterPointCount": 147_702,
        "boardIdentity": "PASS",
        "p03CourtyardExpected": 4_950,
        "p03CourtyardChecked": 4_950,
        "p03EscapeExpected": 24_354,
        "p03EscapeChecked": 24_354,
        "productionPlacementAccess": {
            "status": "PASS",
            "gridCells": 147_702,
            "reachableCells": 90_000,
            "cellLimit": 700_000,
        },
    }


def _work_fixture(route: bool):
    phases = [
        {"name": name, "calls": 0, "ownerSteps": 0, "elapsedMs": 0}
        for name in reader.PHASE_NAMES
    ]
    phases[0].update(calls=1, ownerSteps=8, elapsedMs=4)
    phases[6].update(calls=1, ownerSteps=10, elapsedMs=6)
    if route:
        phases[8]["elapsedMs"] = 5
        phases[9]["elapsedMs"] = 3
        phases[10]["elapsedMs"] = 2
    elapsed = sum(row["elapsedMs"] for row in phases)
    calls = sum(row["calls"] for row in phases)
    return {
        "operationLimitMs": 90_000,
        "controllerUnitLimit": 640,
        "unitLimitMs": 5_000,
        "ownerStepsPerUnitLimit": 32_768,
        "phases": phases,
        "operationElapsedMs": elapsed,
        "controllerUnits": calls,
        "maxUnitElapsedMs": 6,
        "maxOwnerStepsInUnit": 10,
    }


def _event_fixture(outcome="NO_ROUTE_BOUNDARY"):
    source_digests = {
        "seeds": _sha("source/seeds"),
        "netOrder": _sha("source/net-order"),
        "prefixRows": _sha("source/prefix-rows"),
        "geometry": _sha("source/geometry"),
    }
    state = {
        "pass": 1,
        "netIndex": 4,
        "branchOrdinal": 73,
        "branchExpanded": 0,
        "queueSize": 2,
        "bestSize": 2,
        "holes": 17,
        "vias": 17,
        "traces": 88,
        "viaCost": 60,
        "maximumVias": 48,
        "searchBudgetExpanded": 120,
        "geometrySha256": source_digests["geometry"],
        "seedSha256": source_digests["seeds"],
        "netOrderSha256": source_digests["netOrder"],
        "prefixRowsSha256": source_digests["prefixRows"],
    }
    route = outcome in {
        "FINALIZER_REJECTED", "FINALIZED_SELECTED", "FINALIZED_UNSELECTED",
    }
    finalized = outcome in {"FINALIZED_SELECTED", "FINALIZED_UNSELECTED"}
    boundary_reason = ("LOAD12:BRANCH_QUALITY_LIMIT"
                       if outcome == "QUALITY_BOUNDARY" else "LOAD12:NO_PATH")
    result = ({
        "pass": 1,
        "orderings": 2,
        "expansions": 220,
        "vias": 22,
        "layoutIdentity": 204,
    } if route else None)
    if route:
        remainder = {
            "status": "ROUTE_READY",
            "boundaryType": None,
            "boundaryReason": None,
            "phase": "CURRENT_ORDER_REMAINDER",
            "orderings": 2,
            "cumulativeExpansions": 220,
            "remainingRequestBudget": 999_780,
            "result": result,
        }
        finalizer = ({
            "status": "ACCEPTED", "reasonType": None, "reason": None,
        } if finalized else {
            "status": "REJECTED",
            "reasonType": "BENDS",
            "reason": "bounded production finalizer rejection",
        })
        candidate = {
            "layoutIdentity": 402,
            "attempt": 4,
            "placementScore": 123,
            "area": 5_000,
            "quality": 0.75,
        }
        winner = (dict(candidate) if outcome == "FINALIZED_SELECTED" else {
            "layoutIdentity": 501,
            "attempt": 3,
            "placementScore": 111,
            "area": 4_900,
            "quality": 0.8,
        })
        comparator = ({
            "status": "SELECTED", "candidate": candidate, "winner": winner,
        } if outcome == "FINALIZED_SELECTED" else {
            "status": "UNSELECTED", "candidate": candidate, "winner": winner,
        } if outcome == "FINALIZED_UNSELECTED" else {
            "status": "NOT_RUN", "candidate": None, "winner": None,
        })
        geometry = {
            "raw": {
                "status": "PASS", "layoutIdentity": 204,
                "sha256": _sha("raw-layout"), "traceCount": 100,
                "holeCount": 25, "viaCount": 22,
            },
            "final": ({
                "status": "PASS", "layoutIdentity": 402,
                "sha256": _sha("final-layout"), "traceCount": 95,
                "holeCount": 23, "viaCount": 20,
            } if finalized else None),
        }
        oracles = {
            "raw": _pass_oracle("raw"),
            "final": _pass_oracle("final") if finalized else _not_run_oracle(),
            "independentTwoLayerAccess": "NOT_AVAILABLE",
        }
    else:
        remainder = {
            "status": "ORDERING_BOUNDARY",
            "boundaryType": "ORDERING_BOUNDARY",
            "boundaryReason": boundary_reason,
            "phase": "CURRENT_ORDER_REMAINDER",
            "orderings": 2,
            "cumulativeExpansions": 220,
            "remainingRequestBudget": 999_780,
            "result": None,
        }
        finalizer = {"status": "NOT_RUN", "reasonType": None, "reason": None}
        comparator = {"status": "NOT_RUN", "candidate": None, "winner": None}
        geometry = {"raw": None, "final": None}
        oracles = {
            "raw": _not_run_oracle(), "final": _not_run_oracle(),
            "independentTwoLayerAccess": "NOT_AVAILABLE",
        }
    return {
        "protocolVersion": 1,
        "schema": "rb100-load12-cost240-remainder-r1",
        "status": "PASS",
        "runId": "123e4567-e89b-42d3-a456-426614174000",
        "operationId": "QUALITY_PROBE",
        "scope": "ACTUAL_LOAD12_COST240_COPIED_CURRENT_ORDER_REMAINDER_ONLY",
        "outcome": outcome,
        "selector": {
            "rootSeed": "9007199254740993",
            "placementSeed": "-6587958367974952701",
            "routingSeed": "8445301674744508279",
            "routeRequestIndex": 0,
            "selectedPlacementIndex": 0,
            "selectedPlacementAttempt": 4,
            "pass": 1,
            "netPosition": 3,
            "nextFetch": 4,
            "branchOrdinal": 73,
            "searchBranches": 73,
            "netId": "LOAD12",
            "primaryLayer": "BOTTOM",
            "startPadId": "JLOAD.1",
            "goalPadId": None,
            "grid": {
                "width": 478, "height": 309, "cells": 147_702,
                "originX": 30, "originY": 30, "pitch": 10,
            },
            "sourcePrefix": {
                "vias": 17, "traces": 88, "viaCost": 60,
                "maximumVias": 48,
            },
        },
        "source": {
            "routeSessionIdentity": 101,
            "searchIdentity": 102,
            "branchIdentity": 103,
            "layoutIdentity": 104,
            "digests": source_digests,
            "before": dict(state),
            "after": dict(state),
            "preserved": True,
        },
        "copy": {
            "ownerIdentity": 201,
            "searchIdentity": 202,
            "branchIdentity": 203,
            "layoutIdentity": 204,
            "digests": {
                "seeds": source_digests["seeds"],
                "netOrder": source_digests["netOrder"],
                "prefixRows": source_digests["prefixRows"],
                "initialGeometry": source_digests["geometry"],
                "postGeometry": _sha("copy/post-geometry"),
                "publication": _sha("copy/publication"),
            },
            "requested": {"maximumVias": 48, "viaCost": 240},
            "publisher": {
                "status": "PUBLISHED", "stage": 6,
                "createdVias": 1, "createdTraces": 2,
                "postVias": 18, "postTraces": 90,
                "prefixRetained": True,
            },
            "continuationHandoff": {
                "status": "TRANSFERRED", "singleUse": True,
                "identity": 301, "ownerIdentity": 201,
            },
        },
        "remainder": remainder,
        "finalizer": finalizer,
        "comparator": comparator,
        "geometry": geometry,
        "oracles": oracles,
        "work": _work_fixture(route),
        "cleanup": {
            "status": "PASS", "elapsedMs": 5,
            "limitMs": 120_000, "shutdownElapsedMs": None,
        },
        "sourcePreserved": True,
        "sourceResumption": "NOT_RUN",
        "ordinaryComparisonComplete": False,
        "layoutQualification": False,
        "routeAdoption": False,
        "playable100": False,
        "beta": "held",
        "limits": {
            "requestExpansions": 1_000_000,
            "branchExpansions": 100_000,
            "gridCells": 250_000,
            "orderings": 3,
            "vias": 48,
            "transitions": 4,
            "operationMs": 90_000,
            "controllerUnits": 640,
            "unitMs": 5_000,
            "ownerStepsPerUnit": 32_768,
            "cleanupMs": 120_000,
            "shutdownMs": 15_000,
            "accessCells": 700_000,
            "suiteMs": 480_000,
        },
        "suiteMs": 40,
        "suiteLimitMs": 480_000,
        "failure": None,
    }


ACTUAL_V5_WRAPPER_PROLOGUE = (
    "WARNING: The names of some imported commands from the module "
    "'VerifierIsolation' include unapproved verbs that might ",
    "make them less discoverable. To find the commands with unapproved verbs, "
    "run the Import-Module command again with the ",
    "Verbose parameter. For a list of approved verbs, type Get-Verb.",
    "JDK: javac 1.8.0_502",
    "PASS: current-contract runner pass detection assertions=16",
    "",
    "NATIVE BRIDGE: E06 scratch-only factory-name interning preserves GWT "
    "string-value dispatch; actual compiled factory gate remains required.",
    "NATIVE BRIDGE: scratch-only exact CirSim.console -> System.err.println; "
    "CircuitJS solver/models unchanged.",
    "NATIVE BRIDGE: E06 scratch-only ChipElm full-grid drawing preference "
    "(smallGrid=false); actual compiled constructor gate remains required.",
    "",
    "warning: [options] bootstrap class path not set in conjunction with -source 1.7",
    "Note: Some input files use unchecked or unsafe operations.",
    "Note: Recompile with -Xlint:unchecked for details.",
    "1 warning",
    "",
)


def _actual_wrapper_lines(event_line, summary_line):
    return [*ACTUAL_V5_WRAPPER_PROLOGUE, event_line, summary_line, "",
            reader.FOCUSED_PASS, reader.CLEANUP_LINE]


def _native_run_fixture(context):
    return {
        "schema": "q100-quality-zero-via-native-run-v5",
        "source": "candidate",
        "suite": "Rb100Load12DetachedBaselineContractTest",
        "cohortId": None,
        "command": reader._expected_command(),
        "layoutVersion": 26,
        "dependencyEpoch": "tsj-generation-dependencies-v28",
        "sourceSnapshotRevision": 20,
        "inputCount": context["inputCount"],
        "inputBytes": context["inputBytes"],
        "inputPinsSha256": context["pinsSha256"],
        "inputAudit": "PASS_BEFORE_AND_AFTER",
        "exitCode": 0,
        "nativeExitCode": 0,
        "terminationProven": True,
        "cleanupStatus": "PASS",
        "cleanupElapsedMs": None,
        "viaCost": 60,
        "nativeLimitMs": 480_000,
        "outerLimitMs": 1_800_000,
        "processId": 100,
        "processStartTicks": "134089000000000000",
        "elapsedSeconds": 3.5,
        "requestedExecutable": "powershell.exe",
        "stdoutBytes": len(context["stdoutRaw"]),
        "stdoutSha256": reader._sha256(context["stdoutRaw"]),
        "stderrBytes": len(context["stderrRaw"]),
        "stderrSha256": reader._sha256(context["stderrRaw"]),
        "receiptBytes": len(context["receiptRaw"]),
        "receiptSha256": reader._sha256(context["receiptRaw"]),
        "target": "LOAD12",
    }


class RemainderEventTests(unittest.TestCase):
    def test_typed_no_route_boundary_is_conclusive_but_grants_no_layout(self):
        checked = reader._validate_event(_event_fixture())
        self.assertEqual("NO_ROUTE_BOUNDARY", checked["outcome"])
        self.assertEqual("NOT_RUN", checked["sourceResumption"])
        self.assertEqual("NOT_AVAILABLE",
                         checked["oracles"]["independentTwoLayerAccess"])
        self.assertFalse(checked["oracles"]["raw"]["status"] == "PASS")
        for reason in ("TOTAL_SEARCH_LIMIT", "LOAD12:TOTAL_SEARCH_LIMIT",
                       "LOAD12:BRANCH_SEARCH_LIMIT"):
            with self.subTest(reason=reason):
                event = _event_fixture()
                event["remainder"]["boundaryReason"] = reason
                self.assertEqual("NO_ROUTE_BOUNDARY",
                                 reader._validate_event(event)["outcome"])

    def test_quality_boundary_is_classified_from_typed_reason(self):
        checked = reader._validate_event(_event_fixture("QUALITY_BOUNDARY"))
        self.assertEqual("QUALITY_BOUNDARY", checked["outcome"])
        event = _event_fixture("QUALITY_BOUNDARY")
        event["remainder"]["boundaryReason"] = "QUALITY_LIMIT"
        self.assertEqual("QUALITY_BOUNDARY", reader._validate_event(event)["outcome"])
        event = _event_fixture("QUALITY_BOUNDARY")
        event["remainder"]["boundaryReason"] = "LOAD12:NO_PATH"
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_boundary_can_name_a_later_net_without_accepting_operation_failures(self):
        for outcome, reason in (
                ("QUALITY_BOUNDARY", "A_DRIVE:BRANCH_QUALITY_LIMIT"),
                ("NO_ROUTE_BOUNDARY", "D_SENSE:NO_PATH")):
            with self.subTest(reason=reason):
                event = _event_fixture(outcome)
                event["remainder"]["boundaryReason"] = reason
                checked = reader._validate_event(event)
                self.assertEqual(reason, checked["remainder"]["boundaryReason"])
                self.assertEqual(outcome, checked["outcome"])
        for reason in (":NO_PATH", "A DRIVE:NO_PATH", "OBSERVER_ERROR:NO_PATH",
                       "OPERATION_TIMEOUT:BRANCH_QUALITY_LIMIT", "UNIT:NO_PATH",
                       "A_DRIVE:OBSERVER_FAILURE", "A_DRIVE:UNIT_LIMIT"):
            with self.subTest(reason=reason):
                event = _event_fixture()
                event["remainder"]["boundaryReason"] = reason
                with self.assertRaises(reader.Load12RemainderInputError):
                    reader._validate_event(event)

    def test_closed_limits_are_exact_and_bound_to_observed_counters(self):
        event = _event_fixture()
        checked = reader._validate_event(event)
        self.assertEqual(1_000_000, checked["limits"]["requestExpansions"])
        self.assertEqual(147_702, checked["selector"]["grid"]["cells"])
        mutations = (
            ("requestExpansions", 999_999),
            ("branchExpansions", 99_999),
            ("gridCells", 250_001),
            ("orderings", 4),
            ("vias", 49),
            ("transitions", 5),
            ("operationMs", 90_001),
            ("controllerUnits", 641),
            ("unitMs", 5_001),
            ("ownerStepsPerUnit", 32_769),
            ("cleanupMs", 120_001),
            ("shutdownMs", 15_001),
            ("accessCells", 700_001),
            ("suiteMs", 480_001),
        )
        for name, altered_value in mutations:
            with self.subTest(limit=name):
                altered = copy.deepcopy(event)
                altered["limits"][name] = altered_value
                with self.assertRaises(reader.Load12RemainderInputError):
                    reader._validate_event(altered)
        altered = copy.deepcopy(event)
        altered["limits"]["transitions"] = True
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(altered)
        altered = copy.deepcopy(event)
        altered["limits"].pop("branchExpansions")
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(altered)

    def test_terminal_boundary_reports_second_completed_ordering(self):
        event = _event_fixture()
        event["remainder"]["orderings"] = 2
        self.assertEqual("NO_ROUTE_BOUNDARY",
                         reader._validate_event(event)["outcome"])
        event["remainder"]["orderings"] = 1
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_finalized_selected_requires_both_complete_physical_oracle_phases(self):
        checked = reader._validate_event(_event_fixture("FINALIZED_SELECTED"))
        self.assertEqual("FINALIZED_SELECTED", checked["outcome"])
        self.assertEqual("PASS", checked["oracles"]["raw"]["status"])
        self.assertEqual("PASS", checked["oracles"]["final"]["status"])
        self.assertFalse(checked["geometry"]["final"]["layoutIdentity"] ==
                         checked["geometry"]["raw"]["layoutIdentity"])
        self.assertFalse(checked["oracles"]["independentTwoLayerAccess"] == "PASS")

    def test_finalized_unselected_retains_real_candidate_and_existing_winner(self):
        event = _event_fixture("FINALIZED_UNSELECTED")
        checked = reader._validate_event(event)
        self.assertEqual("FINALIZED_UNSELECTED", checked["outcome"])
        self.assertNotEqual(event["comparator"]["candidate"]["layoutIdentity"],
                            event["comparator"]["winner"]["layoutIdentity"])
        event["comparator"]["winner"] = copy.deepcopy(
            event["comparator"]["candidate"])
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_expected_finalizer_rejection_keeps_raw_proof_without_final_claim(self):
        event = _event_fixture("FINALIZER_REJECTED")
        checked = reader._validate_event(event)
        self.assertEqual("FINALIZER_REJECTED", checked["outcome"])
        self.assertEqual("PASS", checked["oracles"]["raw"]["status"])
        self.assertEqual("NOT_RUN", checked["oracles"]["final"]["status"])

    def test_finalizer_rejection_requires_a_production_reason_code(self):
        event = _event_fixture("FINALIZER_REJECTED")
        event["finalizer"]["reasonType"] = "IllegalArgumentException"
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_exact_seed_transport_requires_decimal_strings(self):
        event = _event_fixture("FINALIZED_SELECTED")
        event["selector"]["rootSeed"] = 9_007_199_254_740_992
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_source_copy_cap_cost_prefix_and_pass_must_match(self):
        mutations = (
            ("copy cost", lambda e: e["copy"]["requested"].update(viaCost=60)),
            ("copy cap", lambda e: e["copy"]["requested"].update(maximumVias=47)),
            ("publisher count", lambda e: e["copy"]["publisher"].update(postVias=17)),
            ("source prefix", lambda e: e["source"]["before"].update(vias=18)),
            ("copy order digest", lambda e: e["copy"]["digests"].update(netOrder=_sha("other"))),
            ("pass lineage", lambda e: e["selector"].update({"pass": 0})),
        )
        for label, mutate in mutations:
            with self.subTest(label=label):
                event = _event_fixture("FINALIZED_SELECTED")
                mutate(event)
                with self.assertRaises(reader.Load12RemainderInputError):
                    reader._validate_event(event)

    def test_source_snapshot_and_copy_ownership_must_remain_independent(self):
        event = _event_fixture("FINALIZED_SELECTED")
        event["source"]["after"]["geometrySha256"] = _sha("mutated-source")
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)
        event = _event_fixture("FINALIZED_SELECTED")
        event["copy"]["ownerIdentity"] = event["source"]["routeSessionIdentity"]
        event["copy"]["continuationHandoff"]["ownerIdentity"] = \
            event["source"]["routeSessionIdentity"]
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)
        event = _event_fixture("FINALIZED_SELECTED")
        event["sourcePreserved"] = False
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_route_result_requires_pass_one_and_ordering_two(self):
        for field, wrong in (("pass", 0), ("orderings", 1)):
            with self.subTest(field=field):
                event = _event_fixture("FINALIZED_SELECTED")
                event["remainder"]["result"][field] = wrong
                with self.assertRaises(reader.Load12RemainderInputError):
                    reader._validate_event(event)

    def test_route_result_layout_and_vias_bind_to_copy_and_raw_geometry(self):
        event = _event_fixture("FINALIZED_SELECTED")
        checked = reader._validate_event(event)
        self.assertEqual(event["copy"]["layoutIdentity"],
                         checked["remainder"]["result"]["layoutIdentity"])
        event["remainder"]["result"]["layoutIdentity"] = 401
        event["geometry"]["raw"]["layoutIdentity"] = 401
        with self.assertRaises(reader.Load12RemainderInputError) as caught:
            reader._validate_event(event)
        self.assertEqual("REMAINDER_RESULT_COPY_LAYOUT_IDENTITY_MISMATCH",
                         caught.exception.code)
        event = _event_fixture("FINALIZED_SELECTED")
        event["remainder"]["result"]["vias"] = 17
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_missing_or_partial_physical_pair_proof_is_rejected(self):
        event = _event_fixture("FINALIZED_SELECTED")
        event["oracles"]["raw"]["orderedPadPairsChecked"] = 60_515
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)
        event = _event_fixture("FINALIZED_SELECTED")
        event["oracles"]["raw"]["orderedPadPairsChecked"] = 60_516.0
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)
        event = _event_fixture("FINALIZED_SELECTED")
        event["oracles"]["final"]["graphNetEquality"] = "NOT_RUN"
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_independent_two_layer_access_cannot_be_inferred(self):
        event = _event_fixture("FINALIZED_SELECTED")
        event["oracles"]["independentTwoLayerAccess"] = "PASS"
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_boundary_timeout_and_operation_failure_are_not_no_route(self):
        event = _event_fixture()
        event["remainder"]["boundaryReason"] = "SEARCH_TIMEOUT"
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)
        event = _event_fixture()
        event["status"] = "FAIL"
        event["outcome"] = "OPERATION_FAILURE"
        event["failure"] = {"phase": "orderingRemainder", "type": "Timeout", "message": "bounded timeout"}
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_untyped_ordering_boundary_and_later_completed_order_rejected(self):
        event = _event_fixture()
        event["remainder"]["boundaryType"] = None
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)
        event = _event_fixture("FINALIZED_SELECTED")
        event["remainder"]["orderings"] = 3
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_request_expansion_overflow_and_unaccounted_work_are_rejected(self):
        event = _event_fixture()
        event["remainder"]["cumulativeExpansions"] = 1_000_001
        event["remainder"]["remainingRequestBudget"] = 0
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)
        event = _event_fixture()
        event["work"]["operationElapsedMs"] += 1
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)
        event = _event_fixture()
        event["work"]["phases"][0]["calls"] += 1
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_work_units_owner_steps_and_time_are_closed_and_bounded(self):
        self.assertEqual("finalizationAndReporting", reader.PHASE_NAMES[9])
        event = _event_fixture("FINALIZED_SELECTED")
        event["work"]["operationLimitMs"] = 90_000.0
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)
        event = _event_fixture("FINALIZED_SELECTED")
        event["work"]["phases"][0]["ownerSteps"] = 32_769
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)
        event = _event_fixture("FINALIZED_SELECTED")
        event["work"]["controllerUnits"] = 641
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)
        event = _event_fixture("FINALIZED_SELECTED")
        event["work"]["operationElapsedMs"] = 90_001
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_failed_cleanup_cannot_be_a_conclusive_boundary(self):
        event = _event_fixture()
        event["cleanup"]["status"] = "FAIL"
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_protocol_and_exact_keysets_fail_closed(self):
        event = _event_fixture()
        event["protocolVersion"] = 2
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)
        event = _event_fixture()
        event["unexpected"] = True
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)
        event = _event_fixture()
        event["operationId"] = "ORDINARY_COMPARISON"
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_event(event)

    def test_source_resumption_or_qualification_claims_are_rejected(self):
        for field, value in (
                ("sourceResumption", "RESUMED"),
                ("ordinaryComparisonComplete", True),
                ("layoutQualification", True),
                ("routeAdoption", True),
                ("playable100", True)):
            with self.subTest(field=field):
                event = _event_fixture()
                event[field] = value
                with self.assertRaises(reader.Load12RemainderInputError):
                    reader._validate_event(event)


class NativeTransportTests(unittest.TestCase):
    def _context(self):
        stdout = b"stdout artifact"
        receipt = b"receipt artifact"
        stderr = b""
        return {
            "inputCount": 1_080,
            "inputBytes": 123_456,
            "pinsSha256": "a" * 64,
            "stdoutRaw": stdout,
            "receiptRaw": receipt,
            "stderrRaw": stderr,
        }

    def test_v5_native_manifest_binds_source_command_process_cleanup_and_captures(self):
        context = self._context()
        value = _native_run_fixture(context)
        checked = reader._validate_native_run(value, context)
        self.assertEqual("LOAD12", checked["target"])
        self.assertTrue(checked["terminationProven"])
        altered = dict(value, stdoutSha256="0" * 64)
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_native_run(altered, context)

    def test_v5_native_manifest_rejects_wrong_command_audit_exit_and_cleanup(self):
        context = self._context()
        valid = _native_run_fixture(context)
        mutations = (
            ("mode", lambda row: row["command"].update(args=["-Rb100Load12Cost240Replay"])),
            ("audit", lambda row: row.update(inputAudit="NOT_AUDITED")),
            ("native exit", lambda row: row.update(nativeExitCode=1)),
            ("termination", lambda row: row.update(terminationProven=False)),
            ("cleanup", lambda row: row.update(cleanupStatus="FAIL")),
            ("target", lambda row: row.update(target="OTHER")),
        )
        for label, mutate in mutations:
            with self.subTest(label=label):
                altered = copy.deepcopy(valid)
                mutate(altered)
                with self.assertRaises(reader.Load12RemainderInputError):
                    reader._validate_native_run(altered, context)

    def test_optional_manifest_byte_count_is_checked_without_changing_source_bytes(self):
        context = self._context()
        value = _native_run_fixture(context)
        value["inputManifestBytes"] = 41
        checked_context = dict(context, inputManifestBytes=41)
        self.assertEqual(41, reader._validate_native_run(value, checked_context)[
            "inputManifestBytes"])
        value["inputManifestBytes"] = 42
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_native_run(value, checked_context)

    def test_receipt_requires_event_then_summary_and_console_capture_match(self):
        event_line = reader.EVENT_MARKER + " {\"protocolVersion\":1}"
        summary = reader.SUMMARY_PREFIX + "7"
        receipt = (event_line + "\n" + summary + "\n").encode("utf-8")
        stdout = (event_line + "\n" + summary + "\n" + reader.FOCUSED_PASS +
                  "\n" + reader.CLEANUP_LINE + "\n").encode("utf-8")
        _console, lines, event = reader._validate_console_and_receipt(stdout, receipt, b"")
        self.assertEqual([event_line, summary], lines)
        self.assertEqual(1, event["protocolVersion"])
        bad_receipt = (summary + "\n" + event_line + "\n").encode("utf-8")
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._validate_console_and_receipt(stdout, bad_receipt, b"")

    def test_actual_v5_wrapper_prologue_allows_whitespace_outside_exact_payload(self):
        event_line = (reader.EVENT_MARKER + " " + json.dumps(
            _event_fixture(), separators=(",", ":")))
        summary = reader.SUMMARY_PREFIX + "8444480"
        receipt = (event_line + "\n" + summary + "\n").encode("utf-8")
        stdout = ("\r\n".join(_actual_wrapper_lines(event_line, summary)) +
                  "\r\n").encode("utf-8")
        console, lines, event = reader._validate_console_and_receipt(
            stdout, receipt, b"")
        self.assertEqual([event_line, summary], lines)
        self.assertEqual("NO_ROUTE_BOUNDARY", event["outcome"])
        self.assertTrue(console[0].endswith(" "))
        self.assertEqual("", console[5])
        self.assertEqual("", console[14])

    def test_console_adapter_rejects_payload_edges_blanks_and_wrapper_failures(self):
        event_line = (reader.EVENT_MARKER + " " + json.dumps(
            _event_fixture(), separators=(",", ":")))
        summary = reader.SUMMARY_PREFIX + "7"
        receipt = (event_line + "\n" + summary + "\n").encode("utf-8")
        base = _actual_wrapper_lines(event_line, summary)
        payload_index = len(ACTUAL_V5_WRAPPER_PROLOGUE)
        mutations = (
            ("blank inside payload", lambda lines: lines.insert(payload_index + 1, "")),
            ("leading event whitespace", lambda lines: lines.__setitem__(payload_index, " " + event_line)),
            ("trailing event whitespace", lambda lines: lines.__setitem__(payload_index, event_line + " ")),
            ("leading summary whitespace", lambda lines: lines.__setitem__(payload_index + 1, " " + summary)),
            ("trailing summary whitespace", lambda lines: lines.__setitem__(payload_index + 1, summary + " ")),
            ("wrapper error", lambda lines: lines.insert(1, "ERROR: synthetic wrapper failure")),
            ("compiler error", lambda lines: lines.insert(1, "tests/Foo.java:8: error: synthetic compiler failure")),
            ("exception", lambda lines: lines.insert(1, "Exception in thread \"main\" synthetic failure")),
            ("wrapper failure after payload", lambda lines: lines.insert(payload_index + 2, "FAIL: synthetic wrapper failure")),
        )
        for label, mutate in mutations:
            with self.subTest(label=label):
                lines = list(base)
                mutate(lines)
                stdout = ("\n".join(lines) + "\n").encode("utf-8")
                with self.assertRaises(reader.Load12RemainderInputError):
                    reader._validate_console_and_receipt(stdout, receipt, b"")
        for bad_receipt in (
                (event_line + "\n\n" + summary + "\n").encode("utf-8"),
                (" " + event_line + "\n" + summary + "\n").encode("utf-8"),
                (event_line + " \n" + summary + "\n").encode("utf-8")):
            with self.subTest(receipt=bad_receipt[:24]):
                with self.assertRaises(reader.Load12RemainderInputError):
                    reader._validate_console_and_receipt(
                        ("\n".join(base) + "\n").encode("utf-8"),
                        bad_receipt, b"")

    def test_spec_sha_and_cli_arguments_are_exact(self):
        parsed = reader._parse_cli([
            "--input-spec", "input.json", "--input-spec-sha256", "a" * 64,
        ])
        self.assertEqual(("input.json", "a" * 64), parsed)
        for args in (
                ["--input-spec", "input.json", "--input-spec-sha256", "A" * 64],
                ["--input-spec", "input.json", "--other", "a" * 64],
                ["--input-spec", "input.json"],):
            with self.subTest(args=args):
                with self.assertRaises(reader.Load12RemainderInputError):
                    reader._parse_cli(args)


class InputManifestTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="load12-remainder-inputs-")
        cls.root = Path(cls.temp.name) / "source"
        cls.root.mkdir()
        paths = {
            "src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java",
            "src/com/lushprojects/circuitjs1/client/PcbNetRouter.java",
            "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java",
        }
        paths.update(
            "src/fixtures/Fixture%04d.java" % index
            for index in range(reader.PRODUCTION_JAVA_COUNT - 3)
        )
        paths.update({
            "tests/contracts/Rb100Load12DetachedBaselineContractTest.java",
            "tests/contracts/Rb100PhysicalDiagnostics.java",
        })
        paths.update(
            "tests/contracts/Fixture%04d.java" % index
            for index in range(reader.CONTRACT_JAVA_COUNT - 2)
        )
        paths.update(reader.STATIC_SOURCE_INPUTS)
        assert len(paths) == reader.SOURCE_INPUT_COUNT
        rows = []
        for relative in sorted(paths):
            content = ("pinned:" + relative + "\n").encode("ascii")
            path = cls.root.joinpath(*relative.split("/"))
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
            rows.append({
                "path": relative,
                "bytes": len(content),
                "sha256": hashlib.sha256(content).hexdigest(),
            })
        cls.rows = rows
        cls.raw = json.dumps(rows, separators=(",", ":")).encode("utf-8")
        cls.spec = {
            "pinsSha256": hashlib.sha256(cls.raw).hexdigest(),
            "inputBytes": sum(row["bytes"] for row in rows),
        }

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_complete_manifest_binds_all_native_inputs_and_closed_java_census(self):
        result = reader._audit_inputs_manifest(self.raw, self.spec, self.root)
        self.assertEqual(1_080, result["inputCount"])
        self.assertEqual(self.spec["inputBytes"], result["inputBytes"])
        self.assertEqual(self.spec["pinsSha256"], result["pinsSha256"])
        self.assertEqual(reader.SOURCE_INPUT_COUNT, len(result["pinMap"]))

    def test_source_content_mutation_is_rejected(self):
        relative = self.rows[0]["path"]
        path = self.root.joinpath(*relative.split("/"))
        original = path.read_bytes()
        try:
            path.write_bytes(original + b"changed")
            with self.assertRaises(reader.Load12RemainderInputError):
                reader._audit_inputs_manifest(self.raw, self.spec, self.root)
        finally:
            path.write_bytes(original)

    def test_duplicate_or_incomplete_input_manifests_are_rejected(self):
        duplicate_rows = copy.deepcopy(self.rows)
        duplicate_rows[1] = copy.deepcopy(duplicate_rows[0])
        duplicate_raw = json.dumps(duplicate_rows, separators=(",", ":")).encode("utf-8")
        duplicate_spec = dict(self.spec, pinsSha256=hashlib.sha256(duplicate_raw).hexdigest())
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._audit_inputs_manifest(duplicate_raw, duplicate_spec, self.root)
        incomplete_raw = json.dumps(self.rows[:-1], separators=(",", ":")).encode("utf-8")
        incomplete_spec = dict(self.spec, pinsSha256=hashlib.sha256(incomplete_raw).hexdigest())
        with self.assertRaises(reader.Load12RemainderInputError):
            reader._audit_inputs_manifest(incomplete_raw, incomplete_spec, self.root)

    def test_exact_fourteen_field_spec_resolves_source_pins_and_artifacts(self):
        with tempfile.TemporaryDirectory(prefix="load12-remainder-spec-") as temp:
            spec_directory = Path(temp)
            pins_path = spec_directory / "inputs.json"
            pins_path.write_bytes(self.raw)
            artifact_paths = {}
            for name in ("resultPath", "stdoutPath", "receiptPath", "stderrPath"):
                path = spec_directory / (name + ".bin")
                path.write_bytes(b"" if name == "stderrPath" else b"artifact")
                artifact_paths[name] = str(path)
            spec = {
                "schema": reader.INPUT_SCHEMA,
                "target": "LOAD12",
                "purpose": reader.PURPOSE,
                "suite": "Rb100Load12DetachedBaselineContractTest",
                "sourceRoot": str(self.root),
                "pinsPath": str(pins_path),
                "pinsSha256": self.spec["pinsSha256"],
                "inputCount": 1_080,
                "inputBytes": self.spec["inputBytes"],
                "sourceSnapshotRevision": 20,
                **artifact_paths,
            }
            checked_spec, context = reader._validate_input_spec(spec, spec_directory)
            self.assertEqual(spec, checked_spec)
            self.assertEqual(1_080, context["inputCount"])
            self.assertEqual(self.spec["pinsSha256"], context["pinsSha256"])

    def test_source_and_capture_artifacts_are_rechecked_for_immutability(self):
        with tempfile.TemporaryDirectory(prefix="load12-remainder-stability-") as temp:
            root = Path(temp)
            source_root = root / "source"
            source_root.mkdir()
            spec_path = root / "spec.json"
            pins_path = root / "inputs.json"
            spec_raw = b"{}\n"
            pins_raw = b"[]\n"
            spec_path.write_bytes(spec_raw)
            pins_path.write_bytes(pins_raw)
            spec = {"sourceRoot": "source", "pinsPath": "inputs.json"}
            artifact_raw = {
                "result": b"result",
                "stdout": b"stdout",
                "receipt": b"receipt",
                "stderr": b"",
            }
            artifacts = {}
            for name, raw in artifact_raw.items():
                suffix = "Path"
                path = root / (name + ".bin")
                path.write_bytes(raw)
                artifacts[name + suffix] = path.resolve()
            context = {
                "sourceRoot": source_root.resolve(),
                "pinsPath": pins_path.resolve(),
                "pinsRaw": pins_raw,
                "pinMap": {"fixture": {"bytes": 1, "sha256": _sha("x")}},
                "inputCount": 1_080,
                "inputBytes": 123,
                "artifacts": artifacts,
                "artifactRaw": artifact_raw,
            }
            expected_audit = {
                "pinMap": context["pinMap"],
                "inputCount": context["inputCount"],
                "inputBytes": context["inputBytes"],
            }
            with patch.object(reader, "_audit_inputs_manifest",
                              return_value=expected_audit):
                reader._stable_source_check(
                    spec_raw, spec_path, hashlib.sha256(spec_raw).hexdigest(),
                    spec, context)
                artifacts["stdoutPath"].write_bytes(b"stale!")
                with self.assertRaises(reader.Load12RemainderInputError):
                    reader._stable_source_check(
                        spec_raw, spec_path, hashlib.sha256(spec_raw).hexdigest(),
                        spec, context)


if __name__ == "__main__":
    unittest.main()
