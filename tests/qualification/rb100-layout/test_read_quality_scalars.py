"""Synthetic contract and source-binding tests for the quality-scalar reader."""
from __future__ import annotations

import copy
import hashlib
import io
import json
import sys
import tempfile
import types
import unittest
import uuid
from contextlib import redirect_stdout
from pathlib import Path

import read_quality_scalars as reader


RUN_ID = "d1c10000-0000-4000-8000-000000000001"
ROUTES = list(reader.EXPECTED_ROUTE_OUTCOMES)
ELIGIBLE = list(reader.EXPECTED_ELIGIBLE_ATTEMPTS)
RANKED = [4, 3, 0, 1, 2, 5]


def _jvector(items):
    return "[" + ", ".join(str(item) for item in items) + "]"


def _canonical_ordering(row):
    return (
        "attempt={attempt};startedOrderings={startedOrderings};"
        "completedOrderings={completedOrderings};yieldedOrderings={yieldedOrderings};"
        "finalizedOrderings={finalizedOrderings};selectedOrdering={selectedOrdering};"
        "expansions={expansions};guidanceStarted={guidanceStarted};"
        "comparisonComplete={comparisonComplete};termination={termination}"
    ).format(
        attempt=row["attempt"],
        startedOrderings=row["startedOrderings"],
        completedOrderings=row["completedOrderings"],
        yieldedOrderings=row["yieldedOrderings"],
        finalizedOrderings=row["finalizedOrderings"],
        selectedOrdering=row["selectedOrdering"],
        expansions=row["expansions"],
        guidanceStarted=str(row["guidanceStarted"]).lower(),
        comparisonComplete=str(row["comparisonComplete"]).lower(),
        termination=row["termination"],
    )


def _canonical_stats(stats):
    selected = "null" if stats["selectedRoutePolicy"] is None else stats["selectedRoutePolicy"]
    value = (
        stats["policyIdentity"] + ";outcome=" + stats["outcome"] +
        ";selected=" + selected +
        ";placements=" + str(stats["placementCandidates"]) +
        ";placementRejects=" + str(stats["placementRejections"]) +
        ";placementEvaluations=" + str(stats["placementEvaluations"]) +
        ";routes=" + str(stats["routeAttempts"]) +
        ";routingWork=" + str(stats["routingOrders"]) + "/" +
        str(stats["routingExpansions"]) +
        ";p05=" + str(stats["oneFaceAttempts"]) + "/" + str(stats["oneFaceSuccesses"]) +
        ";p07=" + str(stats["twoLayerAttempts"]) + "/" + str(stats["twoLayerSuccesses"]) +
        ";selectedAttempt=" + str(stats["selectedPlacementAttempt"]) +
        ";ranked=" + _jvector(stats["rankedPlacementAttempts"]) +
        ";placementScores=" + _jvector(stats["placementScores"]) +
        ";selectedScore=" + str(stats["selectedPlacementScore"]) +
        ";selectedQualityMilli=" + str(stats["selectedRouteQualityMilli"]) +
        ";routeOutcomes=" + _jvector(stats["routeOutcomes"]) +
        ";routeEligiblePlacementAttempts=" + _jvector(stats["routeEligiblePlacementAttempts"]) +
        ";routeEligibilitySkips=" + _jvector(stats["routeEligibilitySkips"])
    )
    if stats["orderingSearchReceipts"]:
        value += ";orderingSearchReceipts=[" + ",".join(
            _canonical_ordering(row) for row in stats["orderingSearchReceipts"]) + "]"
    return value


def _java_hash(value):
    result = 0
    raw = value.encode("utf-16-be")
    for offset in range(0, len(raw), 2):
        result = ((31 * result) + (raw[offset] << 8 | raw[offset + 1])) & 0xFFFFFFFF
    return format(result, "x")


def _ordering_rows():
    rows = []
    for attempt, expansions, term, complete in (
        (4, 918_222, "A_COIL_LOW:NO_PATH", True),
        (2, 1_000_000, "TOTAL_SEARCH_LIMIT", False),
        (3, 1_000_000, "TOTAL_SEARCH_LIMIT", False),
    ):
        rows.append({
            "attempt": attempt,
            "startedOrderings": 3,
            "completedOrderings": 3,
            "yieldedOrderings": 0,
            "finalizedOrderings": 0,
            "selectedOrdering": -1,
            "expansions": expansions,
            "guidanceStarted": False,
            "comparisonComplete": complete,
            "termination": term,
        })
    return rows


def _statistics(placement_evaluations=102_828):
    stats = {
        "policyIdentity": "MEDIUM_BOARD@1",
        "outcome": "REJECTED",
        "selectedRoutePolicy": None,
        "placementCandidates": 6,
        "placementRejections": 0,
        "placementEvaluations": placement_evaluations,
        "routeAttempts": 3,
        "oneFaceAttempts": 0,
        "oneFaceSuccesses": 0,
        "twoLayerAttempts": 3,
        "twoLayerSuccesses": 0,
        "selectedPlacementAttempt": -1,
        "rankedPlacementCount": 6,
        "rankedPlacementAttempts": RANKED.copy(),
        "placementScoreCount": 6,
        "placementScores": ["4:100", "3:90", "0:80", "1:70", "2:60", "5:50"],
        "routeOutcomesCount": 3,
        "routeOutcomes": ROUTES.copy(),
        "orderingSearchReceiptsCount": 3,
        "orderingSearchReceipts": _ordering_rows(),
        "routeEligiblePlacementAttemptCount": 5,
        "routeEligiblePlacementAttempts": ELIGIBLE.copy(),
        "routeEligibilitySkipCount": 1,
        "routeEligibilitySkips": [
            "attempt=5;width=929;height=643;cells=597347;reason=GRID_LIMIT",
        ],
        "routingOrders": 9,
        "routingExpansions": 2_918_222,
        "selectedPlacementScore": -1,
        "selectedRouteQualityMilli": -1,
    }
    canonical = _canonical_stats(stats)
    stats["canonicalLength"] = len(canonical.encode("utf-16-le")) // 2
    stats["canonicalJavaHash"] = _java_hash(canonical)
    stats["canonical"] = canonical
    return stats


def _zero_layout():
    return {
        "layoutReturned": False, "zeroOutput": True, "fingerprint": None,
        "outline": None, "rawSegments": 0, "topTraceCount": 0,
        "bottomTraceCount": 0, "totalTraceCount": 0, "holeCount": 0,
        "viaCount": 0, "platedHoleCount": 0, "nonPlatedHoleCount": 0,
    }


def _limits():
    return {
        "placementCandidates": 6, "routeCandidates": 3, "p07Expansions": 1_000_000,
        "p07BranchExpansions": 100_000, "p07GridCells": 250_000,
        "p07Orderings": 3, "p07Vias": 48, "outerPlacements": 80,
        "outerRouteAttempts": 800, "outerRouteExpansions": 160_000_000,
        "outerAccessCells": 700_000, "operationMs": 90_000,
        "workUnits": 640, "unitMs": 5_000, "cleanupMs": 120_000,
    }


def _operation_result(operation_ms):
    stats = _statistics()
    facts = {
        "protocolVersion": 2, "runId": RUN_ID, "fixture": "AUTHORED_RB100",
        "variant": reader.VARIANT, "layoutVersion": 26,
        "dependencyEpoch": reader.DEPENDENCY_EPOCH, "planVersion": 1,
        "modelVersion": 4, "physicalIntentVersion": 9,
        "physicalPolicyId": "MEDIUM_BOARD", "physicalPolicyVersion": 1,
        "placementDensityVersion": 1, "routingLayer": "P07_FULLER_TWO_LAYER",
        "traceWidthProfile": "ROLE_TRACE", "placementPackingVersion": 4,
        "requiresTwoLayerRouting": True, "seed": reader.ROOT_SEED,
        "placementSeed": reader.PLACEMENT_SEED, "routingSeed": reader.ROUTING_SEED,
        "components": 100, "pads": 246, "nets": 60,
        "boardIdentityStatus": "PASS", "solverGraphIdentityStatus": "PASS",
        "sessionTerminalStatus": "EXHAUSTED",
        "sessionException": {
            "type": "PcbRoutingRejectedException", "kind": "EXHAUSTED",
            "attemptIndex": 2, "attemptCount": 3, "seed": reader.ROUTING_SEED,
            "exhausted": True, "message": "final generator rejection",
        },
        "advanceCalls": 120, "observerChecks": 4, "limits": _limits(),
        "primaryRoutingOrders": 9, "primaryRoutingExpansions": 2_918_222,
        "policyFailure": "A_COIL_LOW:NO_PATH", "policyStatistics": stats,
        "packingProbe": None, "layoutOutput": _zero_layout(),
        "layoutQualification": False,
    }
    return {
        "protocolVersion": 2, "runId": RUN_ID, "status": "PASS",
        "phase": "TERMINAL", "operationMs": operation_ms, "cleanupMs": 4,
        "cleanupStatus": "PASS", "workUnits": 120, "maxUnitMs": 5_000,
        "failurePhase": "", "failureType": "none", "failure": "",
        "cleanupFailureType": "none", "cleanupFailure": "",
        "scope": "OBSERVATIONAL_PRIVATE_P07_POLICY_ONLY",
        "layoutQualification": False, "reportFacts": facts,
    }


def _operation(operation_id, mode, operation_ms):
    return {
        "protocolVersion": 1, "sourceRevision": 8, "runId": RUN_ID,
        "operationId": operation_id, "variant": reader.VARIANT, "mode": mode,
        "result": _operation_result(operation_ms),
    }


def _controls():
    rows = []
    for name, length, direct, bends, nodes, transitions, stage in reader.CONTROL_EXPECTATIONS:
        rows.append({
            "name": name, "status": "PASS", "planarLength": length,
            "directDistance": direct, "bends": bends, "pathNodeCount": nodes,
            "transitionCount": transitions, "publisherStage": stage,
        })
    return {
        "protocolVersion": 1, "sourceRevision": 8, "runId": RUN_ID,
        "status": "PASS", "fixture": "SYNTHETIC_PUBLISHER_METRICS_ONLY",
        "checks": rows, "assertions": 18, "layoutQualification": False,
    }


def _subset():
    return {
        "protocol": "rb100-physical-subsets-v1", "runId": RUN_ID,
        "variant": reader.VARIANT, "status": "NOT_RUN_NO_LAYOUT",
        "independentTwoLayerAccess": False, "layoutQualification": False,
    }


def _event():
    return {
        "protocolVersion": 1, "sourceRevision": 8, "runId": RUN_ID,
        "operationId": reader.PROBE_OPERATION_ID, "variant": reader.VARIANT,
        "mode": "PROBE", "rootSeed": reader.ROOT_SEED,
        "placementSeed": reader.PLACEMENT_SEED, "routingSeed": reader.ROUTING_SEED,
        "layoutVersion": 26, "dependencyEpoch": reader.DEPENDENCY_EPOCH,
        "planVersion": 1, "modelVersion": 4, "physicalIntentVersion": 9,
        "routeRequestIndex": 0, "selectedPlacementIndex": 0,
        "selectedPlacementAttempt": 4, "eligiblePlacementAttempts": ELIGIBLE.copy(),
        "placementFingerprintLength": 128, "placementFingerprintSha256": "a" * 64,
        "pass": 1, "netId": "D_SENSE", "startPadId": "PAD_START",
        "rootPadId": "PAD_ROOT", "endPadId": None,
        "startX": 100, "startY": 100, "endX": 130, "endY": 100,
        "endLayer": 0, "primaryLayer": 0, "firstLayer": 0,
        "gridWidth": 40, "gridHeight": 40, "gridOriginX": 0, "gridOriginY": 0,
        "gridPitch": 10, "pathNodeCount": 21, "transitionCount": 0,
        "planarLength": 200, "directDistance": 30, "bends": 17,
        "rejectDirectZero": False, "rejectLengthOverThreeDirect": True,
        "rejectBendsOver16": True, "qualityRejected": True,
        "qualityRejectionOwner": "PcbLayerRoutingPrototype.Session#reason",
        "qualityRejectionMessage": "D_SENSE:BRANCH_QUALITY_LIMIT",
        "generatorLastFailureSource": "SeededPcbLayoutGenerator.Session#lastFailure",
        "generatorLastFailureType": "PcbRoutingRejectedException",
        "generatorLastFailureMessage": "final generator rejection is independently typed",
        "routeMillisStatus": reader.ROUTE_MILLIS_STATUS,
        "manualDriverMillis": 30, "manualSliceSteps": 75,
        "acceptanceFlags": {"accepted": False, "routed": False, "playable": False},
        "feasibility": "UNKNOWN", "layoutQualification": False, "graphCallbacks": 0,
    }


def _pair():
    value = {
        "protocolVersion": 1, "sourceRevision": 8, "runId": RUN_ID,
        "variant": reader.VARIANT, "nullOperationId": reader.NULL_OPERATION_ID,
        "probeOperationId": reader.PROBE_OPERATION_ID,
        "rootSeed": reader.ROOT_SEED, "placementSeed": reader.PLACEMENT_SEED,
        "routingSeed": reader.ROUTING_SEED, "layoutVersion": 26,
        "dependencyEpoch": reader.DEPENDENCY_EPOCH, "planVersion": 1,
        "modelVersion": 4, "physicalIntentVersion": 9, "status": "PASS",
        "routeMillisStatus": reader.ROUTE_MILLIS_STATUS, "layoutQualification": False,
    }
    value.update({name: True for name in reader.PAIR_TRUE_FIELDS})
    return value


def _summary():
    return {
        "protocolVersion": 1, "sourceRevision": 8, "status": "PASS",
        "operations": 2, "nullOperations": 1, "probeOperations": 1,
        "pairs": 1, "confirmedScalarEvents": 1, "variant": reader.VARIANT,
        "runId": RUN_ID, "rootSeed": reader.ROOT_SEED,
        "placementSeed": reader.PLACEMENT_SEED, "routingSeed": reader.ROUTING_SEED,
        "layoutVersion": 26, "dependencyEpoch": reader.DEPENDENCY_EPOCH,
        "planVersion": 1, "modelVersion": 4, "physicalIntentVersion": 9,
        "assertions": 1_478_265, "suiteMs": 37_202, "suiteLimitMs": 480_000,
        "manualDriverMillis": 30, "manualSliceSteps": 75,
        "routeMillisStatus": reader.ROUTE_MILLIS_STATUS, "feasibility": "UNKNOWN",
        "layoutQualification": False,
        "scope": "OBSERVATIONAL_ACTUAL_GENERATOR_QUALITY_SCALAR_ONLY",
    }


def _receipt_lines():
    records = (
        ("RB100_QUALITY_SCALAR_CONTROLS", _controls()),
        ("RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS", _subset()),
        ("RB100_QUALITY_SCALAR_OPERATION",
         _operation(reader.NULL_OPERATION_ID, "NULL", 45_000)),
        ("RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS", _subset()),
        ("RB100_QUALITY_SCALAR_OPERATION",
         _operation(reader.PROBE_OPERATION_ID, "PROBE", 45_010)),
        ("RB100_QUALITY_SCALAR_EVENT", _event()),
        ("RB100_QUALITY_SCALAR_PAIR", _pair()),
        ("PASS: RB100 quality scalar contracts", _summary()),
    )
    return [marker + " " + json.dumps(record, separators=(",", ":"))
            for marker, record in records]


class QualityScalarReceiptTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ledger, cls.capture, cls.helper_hashes = reader._load_pinned_helpers()

    def _read(self, lines=None):
        return reader._validate_receipt_lines(
            _receipt_lines() if lines is None else lines, self.ledger)

    def _assert_code(self, expected, callable_):
        with self.assertRaises(reader.QualityScalarInputError) as caught:
            callable_()
        self.assertEqual(caught.exception.code, expected)

    def test_synthetic_receipt_conserves_controls_pair_and_observation(self):
        report = self._read()
        observation = report["event"]
        self.assertEqual(report["summary"]["routeMillisStatus"],
                         "INCOMPLETE_MANUAL_SINGLE_STEP_CADENCE_NOT_REPRESENTATIVE")
        self.assertEqual(observation["netId"], "D_SENSE")
        self.assertTrue(observation["qualityRejected"])
        self.assertNotEqual(observation["generatorLastFailureMessage"],
                            observation["qualityRejectionMessage"])
        self.assertEqual(report["nullOperation"]["statistics"]["routingExpansions"],
                         2_918_222)

    def test_literal_quality_predicate_boundaries(self):
        rejected = reader._is_quality_rejected
        self.assertTrue(rejected(0, 0, 0))
        self.assertFalse(rejected(90, 270, 16))
        self.assertTrue(rejected(90, 271, 16))
        self.assertFalse(rejected(90, 270, 16))
        self.assertTrue(rejected(90, 270, 17))

    def test_frozen_controls_reject_changed_literal_or_stage(self):
        lines = _receipt_lines()
        controls = _decode_line(lines[0])
        controls["checks"][3]["publisherStage"] = 1
        lines[0] = _tag("RB100_QUALITY_SCALAR_CONTROLS", controls)
        self._assert_code("CONTROL_LITERAL_MISMATCH", lambda: self._read(lines))

    def test_wrong_target_seed_is_rejected(self):
        lines = _receipt_lines()
        event = _decode_line(lines[5])
        event["placementSeed"] = "9007199254740993"
        lines[5] = _tag("RB100_QUALITY_SCALAR_EVENT", event)
        self._assert_code("EVENT_CONTEXT_INVALID", lambda: self._read(lines))

    def test_coordinate_mismatch_and_nonconserved_path_are_rejected(self):
        lines = _receipt_lines()
        event = _decode_line(lines[5])
        event["directDistance"] = 20
        lines[5] = _tag("RB100_QUALITY_SCALAR_EVENT", event)
        self._assert_code("EVENT_SCALAR_CONSERVATION_INVALID", lambda: self._read(lines))

        lines = _receipt_lines()
        event = _decode_line(lines[5])
        event["pathNodeCount"] += 1
        lines[5] = _tag("RB100_QUALITY_SCALAR_EVENT", event)
        self._assert_code("EVENT_SCALAR_CONSERVATION_INVALID", lambda: self._read(lines))

    def test_predicate_flags_are_recomputed_not_trusted(self):
        lines = _receipt_lines()
        event = _decode_line(lines[5])
        event["rejectBendsOver16"] = False
        lines[5] = _tag("RB100_QUALITY_SCALAR_EVENT", event)
        self._assert_code("EVENT_PREDICATE_INVALID", lambda: self._read(lines))

    def test_end_goal_does_not_claim_a_pad_identity(self):
        lines = _receipt_lines()
        event = _decode_line(lines[5])
        event["endPadId"] = event["rootPadId"]
        lines[5] = _tag("RB100_QUALITY_SCALAR_EVENT", event)
        self._assert_code("EVENT_CONTEXT_INVALID", lambda: self._read(lines))

    def test_acceptance_or_qualification_claims_are_rejected(self):
        lines = _receipt_lines()
        event = _decode_line(lines[5])
        event["acceptanceFlags"]["playable"] = True
        lines[5] = _tag("RB100_QUALITY_SCALAR_EVENT", event)
        self._assert_code("EVENT_QUALIFICATION_INVALID", lambda: self._read(lines))

        lines = _receipt_lines()
        summary = _decode_line(lines[7])
        summary["layoutQualification"] = True
        lines[7] = _tag("PASS: RB100 quality scalar contracts", summary)
        self._assert_code("SUMMARY_IDENTITY_OR_LIMIT_INVALID", lambda: self._read(lines))

    def test_route_millis_remains_explicitly_incomplete(self):
        lines = _receipt_lines()
        event = _decode_line(lines[5])
        event["routeMillisStatus"] = "COMPLETE"
        lines[5] = _tag("RB100_QUALITY_SCALAR_EVENT", event)
        self._assert_code("EVENT_QUALIFICATION_INVALID", lambda: self._read(lines))

    def test_pair_true_flags_cannot_hide_changed_identity(self):
        lines = _receipt_lines()
        operation = _decode_line(lines[2])
        operation["result"]["reportFacts"]["physicalPolicyVersion"] = 2
        lines[2] = _tag("RB100_QUALITY_SCALAR_OPERATION", operation)
        self._assert_code("PAIR_OPERATION_PARITY_INVALID", lambda: self._read(lines))

    def test_false_pair_claim_is_rejected_even_when_operations_match(self):
        lines = _receipt_lines()
        pair = _decode_line(lines[6])
        pair["policyCanonicalEqual"] = False
        lines[6] = _tag("RB100_QUALITY_SCALAR_PAIR", pair)
        self._assert_code("PAIR_FLAGS_OR_CONTEXT_INVALID", lambda: self._read(lines))

    def test_duplicate_reordered_and_unknown_receipt_events_fail_closed(self):
        lines = _receipt_lines()
        lines[4], lines[5] = lines[5], lines[4]
        self._assert_code("RECEIPT_RECORD_PREFIX_INVALID", lambda: self._read(lines))

        lines = _receipt_lines()
        lines[5] = lines[4]
        self._assert_code("RECEIPT_RECORD_PREFIX_INVALID", lambda: self._read(lines))

        lines = _receipt_lines()
        lines[3] = "P07_UNEXPECTED {}"
        self._assert_code("RECEIPT_RECORD_PREFIX_INVALID", lambda: self._read(lines))

    def test_duplicate_json_keys_are_rejected(self):
        with self.assertRaises(Exception) as caught:
            self.ledger._strict_json(b'{"runId":"a","runId":"b"}', "SYNTHETIC")
        self.assertEqual(getattr(caught.exception, "code", None), "JSON_DUPLICATE_KEY")

    def test_stdout_receipt_binding_ignores_compiler_warnings_but_not_events(self):
        receipt_lines = _receipt_lines()
        receipt = ("\n".join(receipt_lines) + "\n").encode("utf-8")
        stdout = ("\n".join([
            "warning: harmless compiler warning",
            *receipt_lines,
            reader.FOCUSED_PASS,
            reader.CLEANUP_LINE,
        ]) + "\n").encode("utf-8")
        stderr = b"host setup warning\n"
        _, checked = reader._validate_console_and_receipt(
            stdout, receipt, stderr, self.ledger)
        self.assertEqual(checked, receipt_lines)

        unknown = receipt_lines.copy()
        unknown[3] = "RB100_UNRECOGNIZED {}"
        stdout_unknown = ("\n".join(["warning", *unknown, reader.FOCUSED_PASS,
                                     reader.CLEANUP_LINE]) + "\n").encode("utf-8")
        self._assert_code(
            "RECEIPT_RECORD_PREFIX_INVALID",
            lambda: reader._validate_receipt_lines(unknown, self.ledger))
        with self.assertRaises(reader.QualityScalarInputError):
            reader._validate_console_and_receipt(
                stdout_unknown, ("\n".join(unknown) + "\n").encode(),
                stderr, self.ledger)
        with self.assertRaises(reader.QualityScalarInputError):
            reader._validate_console_and_receipt(
                stdout, receipt, b"P07_DUPLICATE_EVENT {}\n", self.ledger)

    def test_source_budget_literals_remain_frozen(self):
        reader._validate_limits(_limits())
        damaged = _limits()
        damaged["p07Expansions"] += 1
        self._assert_code(
            "LIMITS_FROZEN_BUDGET_INVALID", lambda: reader._validate_limits(damaged))

    def test_statistics_integer_fields_and_ordering_rows_reject_aliases(self):
        facts = {"primaryRoutingOrders": 9, "primaryRoutingExpansions": 2_918_222,
                 "limits": _limits()}
        stats = _statistics()
        for name in reader.STATISTIC_INTEGER_RANGES:
            for alias in (float(stats[name]), True):
                damaged = copy.deepcopy(stats)
                damaged[name] = alias
                with self.subTest(field=name, alias=alias):
                    self._assert_code(
                        "POLICY_ROUTE_CENSUS_INVALID",
                        lambda: reader._validate_statistics(damaged, facts))
        for name in reader.ORDERING_INTEGER_RANGES:
            for alias in (float(stats["orderingSearchReceipts"][0][name]), True):
                damaged = copy.deepcopy(stats)
                damaged["orderingSearchReceipts"][0][name] = alias
                with self.subTest(ordering_field=name, alias=alias):
                    self._assert_code(
                        "POLICY_ORDERING_RECEIPTS_INVALID",
                        lambda: reader._validate_statistics(damaged, facts))
        for key in ("rankedPlacementAttempts", "routeEligiblePlacementAttempts"):
            bool_index = next(i for i, item in enumerate(stats[key]) if item == 1)
            for alias, index in ((4.0, 0), (True, bool_index)):
                damaged = copy.deepcopy(stats)
                damaged[key][index] = alias
                with self.subTest(list_field=key, alias=alias):
                    self._assert_code(
                        "POLICY_ROUTE_CENSUS_INVALID",
                        lambda: reader._validate_statistics(damaged, facts))

    def test_policy_identity_matches_source_and_rejects_router_suffix(self):
        facts = {"primaryRoutingOrders": 9, "primaryRoutingExpansions": 2_918_222,
                 "limits": _limits()}
        stats = _statistics()
        reader._validate_statistics(stats, facts)
        damaged = copy.deepcopy(stats)
        damaged["policyIdentity"] = "MEDIUM_BOARD@1/P07"
        self._assert_code(
            "POLICY_ROUTE_CENSUS_INVALID",
            lambda: reader._validate_statistics(damaged, facts))

    def test_ordering_expansions_conserve_total_and_obey_per_request_limit(self):
        facts = {"primaryRoutingOrders": 9, "primaryRoutingExpansions": 2_918_222,
                 "limits": _limits()}
        stats = _statistics()
        self.assertEqual(sum(row["expansions"] for row in stats["orderingSearchReceipts"]),
                         stats["routingExpansions"])
        reader._validate_statistics(stats, facts)
        damaged = copy.deepcopy(stats)
        damaged["orderingSearchReceipts"][2]["expansions"] = 1_000_001
        self._assert_code(
            "POLICY_ORDERING_RECEIPTS_INVALID",
            lambda: reader._validate_statistics(damaged, facts))
        damaged = copy.deepcopy(stats)
        damaged["orderingSearchReceipts"][0]["expansions"] += 1
        self._assert_code(
            "POLICY_ORDERING_RECEIPTS_INVALID",
            lambda: reader._validate_statistics(damaged, facts))

    def test_long_evaluation_counter_uses_java_long_domain(self):
        facts = {"primaryRoutingOrders": 9, "primaryRoutingExpansions": 2_918_222,
                 "limits": _limits()}
        high = _statistics(placement_evaluations=(1 << 31) + 1)
        accepted = reader._validate_statistics(high, facts)
        self.assertEqual(accepted["placementEvaluations"], (1 << 31) + 1)
        for invalid in (-1, 0, float((1 << 31) + 1), True, reader.MAX_JAVA_LONG + 1):
            damaged = copy.deepcopy(high)
            damaged["placementEvaluations"] = invalid
            with self.subTest(value=invalid):
                self._assert_code(
                    "POLICY_ROUTE_CENSUS_INVALID",
                    lambda: reader._validate_statistics(damaged, facts))

    def test_observer_counter_uses_int_domain_while_advance_calls_follow_work_units(self):
        operation = _operation_result(45_000)
        facts = operation["reportFacts"]
        for count in (1_000_001, reader.MAX_SCALAR):
            facts["observerChecks"] = count
            checked = reader._validate_operation_result(
                operation, RUN_ID, reader.NULL_OPERATION_ID, "NULL")
            self.assertEqual(checked["facts"]["observerChecks"], count)
        for invalid in (-1, 1.0, True, reader.MAX_SCALAR + 1):
            facts["observerChecks"] = invalid
            with self.subTest(observer_checks=invalid):
                self._assert_code(
                    "OPERATION_FACT_IDENTITY_INVALID",
                    lambda: reader._validate_operation_result(
                        operation, RUN_ID, reader.NULL_OPERATION_ID, "NULL"))
        facts["observerChecks"] = 4
        for invalid in (operation["workUnits"] + 1, float(facts["advanceCalls"]), True):
            facts["advanceCalls"] = invalid
            with self.subTest(advance_calls=invalid):
                self._assert_code(
                    "OPERATION_FACT_IDENTITY_INVALID",
                    lambda: reader._validate_operation_result(
                        operation, RUN_ID, reader.NULL_OPERATION_ID, "NULL"))

    def test_event_list_and_acceptance_flags_require_exact_json_types(self):
        for alias, index in ((4.0, 0), (True, 4)):
            lines = _receipt_lines()
            event = _decode_line(lines[5])
            event["eligiblePlacementAttempts"][index] = alias
            lines[5] = _tag("RB100_QUALITY_SCALAR_EVENT", event)
            self._assert_code("EVENT_CONTEXT_INVALID", lambda: self._read(lines))
        for alias in (0, 0.0):
            lines = _receipt_lines()
            event = _decode_line(lines[5])
            event["acceptanceFlags"]["accepted"] = alias
            lines[5] = _tag("RB100_QUALITY_SCALAR_EVENT", event)
            self._assert_code("EVENT_QUALIFICATION_INVALID", lambda: self._read(lines))

    def test_native_cost_and_time_limits_reject_float_and_boolean_aliases(self):
        stdout, receipt, stderr = b"out\n", b"receipt\n", b""
        context = {"inputCount": 1068, "inputBytes": 86_013_187,
                   "pinsSha256": "a" * 64}
        run = {
            "schema": reader.RUN_SCHEMA, "source": "candidate",
            "suite": reader.SUITE, "cohortId": None,
            "command": {"entry": reader.ENTRY_SCRIPT, "suite": reader.SUITE,
                        "suites": [reader.SUITE], "args": [], "environment": {}},
            "layoutVersion": 26, "dependencyEpoch": reader.DEPENDENCY_EPOCH,
            "sourceSnapshotRevision": 8, "inputCount": 1068,
            "inputBytes": context["inputBytes"], "inputPinsSha256": context["pinsSha256"],
            "inputAudit": "PASS_BEFORE_AND_AFTER", "exitCode": 0, "nativeExitCode": 0,
            "terminationProven": True, "cleanupStatus": "PASS", "cleanupElapsedMs": None,
            "viaCost": 60, "nativeLimitMs": 480_000, "outerLimitMs": 1_800_000,
            "processId": 412, "processStartTicks": "638981234567890123",
            "elapsedSeconds": 73.5, "requestedExecutable": "powershell.exe",
            "stdoutBytes": len(stdout), "stdoutSha256": hashlib.sha256(stdout).hexdigest(),
            "stderrBytes": len(stderr), "stderrSha256": hashlib.sha256(stderr).hexdigest(),
            "receiptBytes": len(receipt), "receiptSha256": hashlib.sha256(receipt).hexdigest(),
        }
        for field in ("viaCost", "nativeLimitMs", "outerLimitMs"):
            for alias in (float(run[field]), True):
                damaged = copy.deepcopy(run)
                damaged[field] = alias
                with self.subTest(field=field, alias=alias):
                    self._assert_code(
                        "NATIVE_RUN_EXIT_OR_BUDGET_INVALID",
                        lambda: reader._validate_native_run(
                            damaged, context, stdout, receipt, stderr))

    def test_native_run_binds_all_captured_artifact_bytes(self):
        stdout, receipt, stderr = b"out\n", b"receipt\n", b""
        context = {"inputCount": 1068, "inputBytes": 86_013_187,
                   "pinsSha256": "a" * 64}
        result = {
            "schema": reader.RUN_SCHEMA, "source": "candidate",
            "suite": reader.SUITE, "cohortId": None,
            "command": {
                "entry": reader.ENTRY_SCRIPT, "suite": reader.SUITE,
                "suites": [reader.SUITE], "args": [], "environment": {},
            },
            "layoutVersion": 26, "dependencyEpoch": reader.DEPENDENCY_EPOCH,
            "sourceSnapshotRevision": 8, "inputCount": 1068,
            "inputBytes": context["inputBytes"], "inputPinsSha256": context["pinsSha256"],
            "inputAudit": "PASS_BEFORE_AND_AFTER", "exitCode": 0, "nativeExitCode": 0,
            "terminationProven": True, "cleanupStatus": "PASS", "cleanupElapsedMs": None,
            "viaCost": 60, "nativeLimitMs": 480_000, "outerLimitMs": 1_800_000,
            "processId": 412, "processStartTicks": "638981234567890123",
            "elapsedSeconds": 73.5, "requestedExecutable": "powershell.exe",
            "stdoutBytes": len(stdout), "stdoutSha256": hashlib.sha256(stdout).hexdigest(),
            "stderrBytes": len(stderr), "stderrSha256": hashlib.sha256(stderr).hexdigest(),
            "receiptBytes": len(receipt), "receiptSha256": hashlib.sha256(receipt).hexdigest(),
        }
        reader._validate_native_run(result, context, stdout, receipt, stderr)
        result["stdoutSha256"] = "b" * 64
        self._assert_code(
            "NATIVE_RUN_ARTIFACT_BINDING_INVALID",
            lambda: reader._validate_native_run(result, context, stdout, receipt, stderr))

    def test_malformed_cli_is_static_and_does_not_echo_path(self):
        output = io.StringIO()
        sensitive = "C:/Users/Private Person/secret-input.json"
        with redirect_stdout(output):
            code = reader.main([
                "--input-spec", sensitive, "--input-spec-sha256", "not-a-sha",
            ])
        self.assertEqual(code, 2)
        self.assertEqual(output.getvalue(), "FAIL: CLI_ARGUMENTS_INVALID\n")
        self.assertNotIn("Private Person", output.getvalue())

    def test_private_module_registration_is_temporary_and_restored_on_failure(self):
        name = "_q100_scalar_exec_" + uuid.uuid4().hex
        path = Path("private-dataclass-fixture.py")
        source = (
            "from __future__ import annotations\n"
            "from dataclasses import dataclass\n"
            "@dataclass\n"
            "class Sample:\n"
            "    value: UnresolvedFixtureType\n"
        ).encode("utf-8")
        self.assertNotIn(name, sys.modules)
        module = reader._exec_private_module(source, path, name)
        self.assertEqual(module.Sample.__module__, name)
        self.assertNotIn(name, sys.modules)

        previous = types.ModuleType(name)
        sys.modules[name] = previous
        try:
            loaded = reader._exec_private_module(source, path, name)
            self.assertIsNot(loaded, previous)
            self.assertIs(sys.modules[name], previous)
            with self.assertRaises(RuntimeError):
                reader._exec_private_module(b"raise RuntimeError('fixture failure')", path, name)
            self.assertIs(sys.modules[name], previous)
        finally:
            sys.modules.pop(name, None)

    def test_reader_loads_the_ledger_in_a_private_pinned_module(self):
        ledger, _capture, hashes = reader._load_pinned_helpers()
        self.assertTrue(ledger.__name__.startswith("_q100_quality_scalar_prefix_ledger"))
        self.assertEqual(hashes["prefixLedgerSha256"], reader.PINNED_LEDGER_SHA256)
        self.assertEqual(ledger.INPUT_SCHEMA, reader.INPUT_SCHEMA)
        self.assertEqual(ledger.SOURCE_REVISION, 8)
        self.assertEqual(ledger.SOURCE_INPUT_COUNT, 1068)
        self.assertEqual(ledger.CONTRACT_JAVA_COUNT, 212)
        self.assertEqual(ledger.SOURCE_JAVA_COUNT, 850)


def _tag(marker, value):
    return marker + " " + json.dumps(value, separators=(",", ":"))


def _decode_line(line):
    return json.loads(line[line.index("{"):])


class SyntheticInputSpecTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ledger, cls.capture, _ = reader._load_pinned_helpers()

    def _make_input_tree(self, base):
        root = base / "source"
        required = set(self.ledger.STATIC_NATIVE_INPUTS) | set(
            self.ledger.REQUIRED_NATIVE_INPUTS) | set(reader.QUALITY_REQUIRED_INPUTS)
        source_java = sorted(path for path in required
                             if path.startswith("src/") and path.endswith(".java"))
        contract_java = sorted(path for path in required
                               if path.startswith("tests/contracts/") and path.endswith(".java"))
        self.assertLessEqual(len(source_java), 850)
        self.assertLessEqual(len(contract_java), 212)
        source_java.extend("src/qualityReaderFixture/Source{:04d}.java".format(index)
                           for index in range(850 - len(source_java)))
        contract_java.extend("tests/contracts/QualityReaderFixture{:04d}.java".format(index)
                             for index in range(212 - len(contract_java)))
        all_paths = sorted(set(source_java + contract_java + list(self.ledger.STATIC_NATIVE_INPUTS)))
        self.assertEqual(len(all_paths), 1068)
        rows = []
        for relative in all_paths:
            path = root.joinpath(*relative.split("/"))
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"x")
            rows.append({"path": relative, "bytes": 1,
                         "sha256": hashlib.sha256(b"x").hexdigest()})
        pins_path = base / "source-pins.json"
        pins_raw = json.dumps(rows, separators=(",", ":"), sort_keys=True).encode("utf-8")
        pins_path.write_bytes(pins_raw)
        artifact_paths = {}
        for name in ("resultPath", "stdoutPath", "receiptPath", "stderrPath"):
            target = base / (name + ".bin")
            target.write_bytes(b"")
            artifact_paths[name] = str(target)
        spec = {
            "schema": reader.INPUT_SCHEMA, "sourceRoot": str(root),
            "pinsPath": str(pins_path), "pinsSha256": hashlib.sha256(pins_raw).hexdigest(),
            "inputCount": 1068, "inputBytes": 1068, "sourceSnapshotRevision": 8,
            **artifact_paths,
        }
        return spec, root, all_paths

    def test_complete_synthetic_pin_census_passes_and_changed_file_fails(self):
        with tempfile.TemporaryDirectory(prefix="q100-quality-source-") as temporary:
            base = Path(temporary)
            spec, root, all_paths = self._make_input_tree(base)
            context = self.ledger._validate_input_spec(spec, base, self.capture)
            self.assertEqual(context["inputCount"], 1068)
            self.assertEqual(len(context["pinMap"]), 1068)
            self.assertTrue(reader.QUALITY_REQUIRED_INPUTS <= set(context["pinMap"]))

            changed = root.joinpath(*all_paths[0].split("/"))
            changed.write_bytes(b"y")
            with self.assertRaises(Exception) as caught:
                self.ledger._validate_input_spec(spec, base, self.capture)
            self.assertEqual(getattr(caught.exception, "code", None),
                             "SOURCE_PIN_CONTENT_MISMATCH")

    def test_old_schema_and_wrong_revision_are_rejected_before_source_use(self):
        with tempfile.TemporaryDirectory(prefix="q100-quality-spec-") as temporary:
            base = Path(temporary)
            spec, _root, _paths = self._make_input_tree(base)
            for key, wrong in (("schema", "p07-current-prefix-ledger-input-v1"),
                               ("sourceSnapshotRevision", 7),
                               ("inputCount", 1067)):
                modified = dict(spec)
                modified[key] = wrong
                with self.assertRaises(Exception) as caught:
                    self.ledger._validate_input_spec(modified, base, self.capture)
                self.assertEqual(getattr(caught.exception, "code", None),
                                 "INPUT_SPEC_IDENTITY_INVALID")


if __name__ == "__main__":
    unittest.main()
