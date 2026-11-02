"""Synthetic safety tests for the current-v28 prefix ledger reader."""

from __future__ import annotations

import contextlib
import copy
import io
import json
import tempfile
from pathlib import Path
import types
import unittest

import read_current_prefix_ledger as ledger


def _pinned_stack():
    oracle, core, capture, *_hashes = ledger._load_pinned_sources()
    return oracle, core, capture


def _graph(oracle, *, vias=None, case_id="SYNTHETIC_PREFIX_JOIN"):
    return oracle.validate_payload({
        "protocol": oracle.GRAPH_PROTOCOL,
        "model": oracle.GRAPH_MODEL,
        "caseId": case_id,
        "width": 3,
        "height": 1,
        "primary": 1,
        "steps": [2, 2, 0, 0, 0, 0],
        "vias": [False, False, False] if vias is None else list(vias),
        "goals": [False, False, False, False, False, True],
        "starts": [0],
    })


def _hole(hole_id="h-1", net_id="F_DRAIN", kind="VIA", x=0, y=0):
    return {
        "id": hole_id,
        "netId": net_id,
        "kind": kind,
        "x": x,
        "y": y,
        "drillRadius": 2,
        "landRadius": 5,
        "exposure": "COVERED",
    }


def _fact(holes=()):
    counts = {"VIA": 0, "PLATED": 0, "NON_PLATED": 0}
    for row in holes:
        counts[row["kind"]] += 1
    return {
        "prefixHoleCountsByKind": counts,
        "prefixHoles": list(holes),
        "prefixHoleCount": len(holes),
        "prefixViaCount": counts["VIA"],
        "gridOriginX": 0,
        "gridOriginY": 0,
        "gridPitch": 10,
        "netId": "F_DRAIN",
    }


def _ordering_entry(attempt, *, expansions, complete, termination):
    return (
        "attempt={};startedOrderings=3;completedOrderings=3;yieldedOrderings=0;"
        "finalizedOrderings=0;selectedOrdering=-1;expansions={};guidanceStarted=false;"
        "comparisonComplete={};termination={}"
    ).format(attempt, expansions, str(complete).lower(), termination)


def _capacity_subset(run_id, variant="SHARED_HYSTERETIC_LINEAR"):
    return {
        "protocol": "rb100-physical-subsets-v1",
        "runId": run_id,
        "variant": variant,
        "status": "NOT_RUN_NO_LAYOUT",
        "independentTwoLayerAccess": False,
        "layoutQualification": False,
    }


class CurrentPrefixLedgerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.oracle, cls.core, cls.capture = _pinned_stack()

    def test_total_hole_budget_uses_plated_and_mechanical_holes(self):
        result = {"reachable": True, "minimumViaCount": 1}
        budget = ledger._budget_disposition(48, result)
        self.assertEqual(budget["charge"], "PREFIX_HOLES_PLUS_TRANSITIONS")
        self.assertEqual(budget["remainingTransitionAllowance"], 0)
        self.assertEqual(budget["disposition"], "EXCLUDED_UNDER_FIXED_GRAPH_BOUND")
        self.assertEqual(ledger._budget_disposition(44, {"reachable": True,
                                                         "minimumViaCount": 4})[
                                                             "disposition"],
                         "INCONCLUSIVE_WITHIN_FIXED_GRAPH_BOUND")

    def test_prefix_inventory_above_48_is_rejected_outside_the_diagnostic_domain(self):
        graph = _graph(self.oracle)
        mechanical = []
        for index in range(49):
            hole = _hole("np-{}".format(index), net_id=None,
                         kind="NON_PLATED", x=1000 + index)
            hole["drillRadius"] = hole["landRadius"]
            mechanical.append(hole)
        fact = _fact(mechanical)
        self.assertEqual(fact["prefixViaCount"], 0)
        with self.assertRaises(ledger.LedgerInputError) as caught:
            ledger._validate_prefix_inventory(fact, graph, ["F_DRAIN"], self.capture)
        self.assertEqual(caught.exception.code, "PREFIX_HOLE_BUDGET_INVALID")

    def test_unreachable_graph_never_claims_whole_board_infeasibility(self):
        budget = ledger._budget_disposition(12, {"reachable": False, "minimumViaCount": None})
        self.assertEqual(budget["disposition"], "NO_PATH_IN_FIXED_GRAPH_ONLY")
        self.assertEqual(budget["remainingTransitionAllowance"], 4)

    def test_same_net_via_join_and_unoccupied_candidate_site_are_distinct(self):
        graph = _graph(self.oracle, vias=[True, True, False])
        fact = _fact([_hole()])
        summary = ledger._validate_prefix_inventory(
            fact, graph, ["A_COIL_LOW", "F_DRAIN"], self.capture)
        self.assertEqual(summary["viaMaskJoins"], {
            "sameNetExistingHole": 1,
            "foreignOrUnownedHoleBlocked": 0,
            "unoccupiedPossibleSite": 1,
        })

    def test_foreign_net_hole_must_block_its_graph_via_cell(self):
        graph = _graph(self.oracle, vias=[True, False, False])
        fact = _fact([_hole(net_id="A_COIL_LOW")])
        with self.assertRaises(ledger.LedgerInputError) as caught:
            ledger._validate_prefix_inventory(
                fact, graph, ["A_COIL_LOW", "F_DRAIN"], self.capture)
        self.assertEqual(caught.exception.code, "PREFIX_FOREIGN_NET_VIA_NOT_BLOCKED")

    def test_foreign_net_hole_is_allowed_only_when_mask_blocks_it(self):
        graph = _graph(self.oracle, vias=[False, True, False])
        fact = _fact([_hole(net_id="A_COIL_LOW")])
        summary = ledger._validate_prefix_inventory(
            fact, graph, ["A_COIL_LOW", "F_DRAIN"], self.capture)
        self.assertEqual(summary["viaMaskJoins"]["foreignOrUnownedHoleBlocked"], 1)

    def test_per_net_inventory_conserves_owned_and_unowned_holes(self):
        mechanical = _hole("np-1", net_id=None, kind="NON_PLATED", x=20)
        mechanical["drillRadius"] = mechanical["landRadius"]
        holes = [
            _hole("v-1", net_id="F_DRAIN", kind="VIA", x=0),
            _hole("pth-1", net_id="A_COIL_LOW", kind="PLATED", x=10),
            mechanical,
        ]
        graph = _graph(self.oracle, vias=[True, False, False])
        summary = ledger._validate_prefix_inventory(
            _fact(holes), graph, ["A_COIL_LOW", "F_DRAIN"], self.capture)
        self.assertEqual(summary["byNet"], {
            "A_COIL_LOW": {"holeCount": 1, "viaCount": 0,
                           "platedHoleCount": 1, "nonPlatedHoleCount": 0},
            "F_DRAIN": {"holeCount": 1, "viaCount": 1,
                        "platedHoleCount": 0, "nonPlatedHoleCount": 0},
        })
        self.assertEqual(summary["unownedMechanical"], {
            "holeCount": 1, "viaCount": 0, "platedHoleCount": 0,
            "nonPlatedHoleCount": 1,
        })
        self.assertEqual(sum(row["holeCount"] for row in summary["byNet"].values()) +
                         summary["unownedMechanical"]["holeCount"], 3)

    def test_unknown_owner_rejects_even_when_old_hole_counts_match(self):
        graph = _graph(self.oracle, vias=[False, False, False])
        fact = _fact([_hole(net_id="NOT_IN_CENSUS")])
        with self.assertRaises(ledger.LedgerInputError) as caught:
            ledger._validate_prefix_inventory(fact, graph, ["F_DRAIN"], self.capture)
        self.assertEqual(caught.exception.code, "PREFIX_HOLE_OWNER_UNKNOWN")

    def test_duplicate_ids_and_kind_count_mismatch_reject(self):
        graph = _graph(self.oracle, vias=[False, False, False])
        duplicate_id = _fact([
            _hole(hole_id="same", x=0),
            _hole(hole_id="same", kind="PLATED", x=10),
        ])
        with self.assertRaises(ledger.LedgerInputError) as caught:
            ledger._validate_prefix_inventory(
                duplicate_id, graph, ["F_DRAIN"], self.capture)
        self.assertEqual(caught.exception.code, "PREFIX_HOLE_INVENTORY_INVALID")

        inconsistent = _fact([])
        inconsistent["prefixHoleCountsByKind"]["VIA"] = 1
        with self.assertRaises(ledger.LedgerInputError) as caught:
            ledger._validate_prefix_inventory(
                inconsistent, graph, ["F_DRAIN"], self.capture)
        self.assertEqual(caught.exception.code, "PREFIX_HOLE_INVENTORY_INVALID")

    def test_duplicate_centers_reject_even_with_unique_ids(self):
        graph = _graph(self.oracle, vias=[True, False, False])
        fact = _fact([_hole("v1"), _hole("v2")])
        with self.assertRaises(ledger.LedgerInputError) as caught:
            ledger._validate_prefix_inventory(fact, graph, ["F_DRAIN"], self.capture)
        self.assertEqual(caught.exception.code, "PREFIX_HOLE_CENTER_DUPLICATE")

    def test_via_center_must_be_an_exact_in_bounds_graph_cell(self):
        graph = _graph(self.oracle)
        fact = _fact([_hole(x=5)])
        with self.assertRaises(ledger.LedgerInputError) as caught:
            ledger._validate_prefix_inventory(fact, graph, ["F_DRAIN"], self.capture)
        self.assertEqual(caught.exception.code, "PREFIX_VIA_GRID_ALIGNMENT_INVALID")

    def test_selector_and_ordering_receipt_bind_the_completed_target(self):
        for variant in ledger.VARIANTS.values():
            expected = copy.deepcopy(variant)
            target_index = expected["routeRequestIndex"]
            entries = []
            for index, attempt in enumerate(expected["orderingAttempts"]):
                is_target = index == target_index
                expansions = (expected["primaryRoutingExpansions"] if is_target else 0)
                entries.append(_ordering_entry(
                    attempt, expansions=expansions, complete=is_target,
                    termination=(expected["netId"] + ":NO_PATH" if is_target else
                                 "TOTAL_SEARCH_LIMIT")))
            # Put the expected expansion total on the first preceding request when
            # the target is request zero; each suite still accounts every request.
            if target_index == 0:
                entries[1] = _ordering_entry(
                    expected["orderingAttempts"][1],
                    expansions=expected["primaryRoutingExpansions"], complete=False,
                    termination="TOTAL_SEARCH_LIMIT")
                entries[0] = _ordering_entry(expected["orderingAttempts"][0],
                                             expansions=0, complete=True,
                                             termination=expected["netId"] + ":NO_PATH")
            operation = {"primaryRoutingOrders": 9}
            operation["primaryRoutingExpansions"] = expected["primaryRoutingExpansions"]
            canonical = "prefix;orderingSearchReceipts=[" + ",".join(entries) + "]"
            ledger._validate_completed_ordering_receipt(canonical, expected, operation)

            undercounted = dict(operation, primaryRoutingOrders=8)
            with self.assertRaises(ledger.LedgerInputError) as caught:
                ledger._validate_completed_ordering_receipt(
                    canonical, expected, undercounted)
            self.assertEqual(caught.exception.code,
                             "OPERATION_ORDERING_STATISTICS_MISMATCH")
            underexpanded = dict(
                operation,
                primaryRoutingExpansions=expected["primaryRoutingExpansions"] - 1)
            with self.assertRaises(ledger.LedgerInputError) as caught:
                ledger._validate_completed_ordering_receipt(
                    canonical, expected, underexpanded)
            self.assertEqual(caught.exception.code,
                             "OPERATION_ORDERING_STATISTICS_MISMATCH")

            bad = copy.deepcopy(expected)
            bad["routeRequestIndex"] = 1
            with self.assertRaises(ledger.LedgerInputError):
                ledger._validate_completed_ordering_receipt(canonical, bad, operation)

    def test_summary_rejects_stale_epoch_or_default_selection(self):
        summary = {
            "status": "PASS", "operations": 4, "nullOperations": 2,
            "captureOperations": 2, "pairs": 2, "graphs": 2,
            "authoredVariants": 2, "variants": list(ledger.VARIANTS),
            "runId": "4c3c43c0-a081-4e0a-a1ed-045f43dc1c50",
            "rootSeed": ledger.ROOT_SEED, "placementSeed": ledger.PLACEMENT_SEED,
            "routingSeed": ledger.ROUTING_SEED, "layoutVersion": 26,
            "dependencyEpoch": ledger.DEPENDENCY_EPOCH, "planVersion": 1,
            "modelVersion": 4, "physicalIntentVersion": 9, "assertions": 12,
            "suiteMs": 12, "suiteLimitMs": 900000,
            "scope": "OBSERVATIONAL_ACTUAL_GENERATOR_FIXED_PREFIX_CAPTURE_ONLY",
            "layoutQualification": False, "captureSelection": "COMPLETED_NO_PATH",
        }
        self.assertEqual(ledger._validate_summary(summary, summary["runId"]), summary)
        stale = dict(summary, dependencyEpoch="tsj-generation-dependencies-v27")
        with self.assertRaises(ledger.LedgerInputError):
            ledger._validate_summary(stale, stale["runId"])
        default = dict(summary, captureSelection="FIRST_FAILURE_REQ0")
        with self.assertRaises(ledger.LedgerInputError):
            ledger._validate_summary(default, default["runId"])

    def test_net_census_requires_the_exact_sorted_unique_capture_join(self):
        variant = "SHARED_HYSTERETIC_LINEAR"
        expected = ledger.VARIANTS[variant]
        graph = _graph(self.oracle, case_id=expected["graphCaseId"])
        operation = {"variant": variant, "operationId": expected["captureOperationId"],
                     "mode": "CAPTURE"}
        ids = sorted(["F_DRAIN"] + ["net-{:02d}".format(index) for index in range(58)])
        census = {
            "protocolVersion": 1,
            "runId": "4c3c43c0-a081-4e0a-a1ed-045f43dc1c50",
            "operationId": expected["captureOperationId"],
            "variant": variant,
            "graphCaseId": expected["graphCaseId"],
            "graphSha256": graph.graph_sha256,
            "netIds": ids,
        }
        checked = ledger._validate_net_census(
            census, operation, {"graph": graph}, census["runId"])
        self.assertEqual(len(checked["netIds"]), 59)

        duplicate = copy.deepcopy(census)
        duplicate["netIds"][-1] = duplicate["netIds"][-2]
        with self.assertRaises(ledger.LedgerInputError):
            ledger._validate_net_census(duplicate, operation, {"graph": graph},
                                        duplicate["runId"])
        wrong_graph = dict(census, graphSha256="0" * 64)
        with self.assertRaises(ledger.LedgerInputError):
            ledger._validate_net_census(wrong_graph, operation, {"graph": graph},
                                        census["runId"])

    def test_capacity_subset_event_is_exactly_not_run_and_never_claims_access(self):
        run_id = "4c3c43c0-a081-4e0a-a1ed-045f43dc1c50"
        valid = _capacity_subset(run_id)
        self.assertEqual(
            ledger._validate_capacity_subset(valid, "SHARED_HYSTERETIC_LINEAR", run_id),
            valid,
        )

        for changed in (
            dict(valid, protocol="rb100-physical-subsets-v2"),
            dict(valid, status="PASS"),
            dict(valid, runId="4c3c43c0-a081-4e0a-a1ed-045f43dc1c51"),
            dict(valid, variant="SEPARATE_DIRECT_SWITCHING"),
            dict(valid, independentTwoLayerAccess=True),
            dict(valid, layoutQualification=True),
            dict(valid, extra="unexpected"),
        ):
            with self.subTest(changed=changed):
                with self.assertRaises(ledger.LedgerInputError):
                    ledger._validate_capacity_subset(
                        changed, "SHARED_HYSTERETIC_LINEAR", run_id)

    def test_capacity_subset_marker_cannot_repeat_instead_of_null_operation(self):
        run_id = "4c3c43c0-a081-4e0a-a1ed-045f43dc1c50"
        marker = ledger.SUBSET_TAG + json.dumps(_capacity_subset(run_id),
                                                separators=(",", ":"))
        with self.assertRaises(ledger.LedgerInputError) as caught:
            ledger._parse_event_stream([marker, marker], self.oracle, self.capture)
        self.assertEqual(caught.exception.code, "NULL_OPERATION_ORDER_OR_MISSING")

    def test_fact_selector_rejects_an_old_first_request_capture(self):
        expected = ledger.VARIANTS["SHARED_HYSTERETIC_LINEAR"]
        fact = {key: None for key in self.capture.FACT_KEYS}
        fact.update({
            "protocolVersion": 1,
            "runId": "4c3c43c0-a081-4e0a-a1ed-045f43dc1c50",
            "operationId": "SHARED_CAPTURE",
            "mode": "CAPTURE",
            "variant": "SHARED_HYSTERETIC_LINEAR",
            "rootSeed": ledger.ROOT_SEED,
            "placementSeed": ledger.PLACEMENT_SEED,
            "routingSeed": ledger.ROUTING_SEED,
            "layoutVersion": 26,
            "dependencyEpoch": ledger.DEPENDENCY_EPOCH,
            "planVersion": 1,
            "modelVersion": 4,
            "physicalIntentVersion": 9,
            "graphCaseId": expected["graphCaseId"],
            "layoutQualification": False,
            "routeEligiblePlacementAttempts": list(expected["eligiblePlacementAttempts"]),
            "selectedPlacementIndex": 0,
            "selectedPlacementAttempt": 4,
            "routeRequestIndex": 0,
        })
        operation = {"variant": "SHARED_HYSTERETIC_LINEAR", "operationId": "SHARED_CAPTURE"}
        graph = _graph(self.oracle, case_id=expected["graphCaseId"])
        with self.assertRaises(ledger.LedgerInputError) as caught:
            ledger._validate_fact(fact, {"graph": graph, "raw": b"{}"}, operation,
                                  fact["runId"], {}, self.capture)
        self.assertEqual(caught.exception.code, "CAPTURE_FACT_SELECTOR_INVALID")

    def test_candidate_census_preserves_the_single_frozen_grid_limit_skip(self):
        for expected in ledger.VARIANTS.values():
            operation = {
                "routeEligiblePlacementAttempts": list(expected["eligiblePlacementAttempts"]),
                "routeEligibilitySkips": list(expected["eligibilitySkips"]),
                "routeOutcomes": list(expected["routeOutcomes"]),
                "firstP07RequestAttempt": 4,
            }
            ledger._validate_route_candidate_census(operation, expected)

            wrong_skip = dict(operation)
            wrong_skip["routeEligibilitySkips"] = [
                "attempt=5;width=929;height=642;cells=597347;reason=GRID_LIMIT",
            ]
            with self.assertRaises(ledger.LedgerInputError) as caught:
                ledger._validate_route_candidate_census(wrong_skip, expected)
            self.assertEqual(caught.exception.code, "OPERATION_ELIGIBILITY_SKIP_INVALID")

            omitted = dict(operation, routeEligibilitySkips=[])
            with self.assertRaises(ledger.LedgerInputError) as caught:
                ledger._validate_route_candidate_census(omitted, expected)
            self.assertEqual(caught.exception.code, "OPERATION_ELIGIBILITY_SKIP_INVALID")

    def test_minimum_oracle_output_cannot_forge_acceptance(self):
        graph = self.oracle.validate_payload({
            "protocol": self.oracle.GRAPH_PROTOCOL,
            "model": self.oracle.GRAPH_MODEL,
            "caseId": "SYNTHETIC_MINIMUM",
            "width": 1,
            "height": 1,
            "primary": 0,
            "steps": [0, 0],
            "vias": [False],
            "goals": [True, False],
            "starts": [0],
        })
        valid = self.core.analyze_minimum_vias(graph)
        self.assertEqual(ledger._validate_minimum_record(valid, graph), valid)
        forged = copy.deepcopy(valid)
        forged["acceptance"]["route"] = True
        with self.assertRaises(ledger.LedgerInputError) as caught:
            ledger._validate_minimum_record(forged, graph)
        self.assertEqual(caught.exception.code, "MINIMUM_VIAS_ACCEPTANCE_INVALID")

    def test_input_spec_epoch_and_count_are_exact(self):
        spec = {
            "schema": ledger.INPUT_SCHEMA,
            "sourceRoot": "source",
            "pinsPath": "pins.json",
            "pinsSha256": "a" * 64,
            "inputCount": 1067,
            "inputBytes": 100,
            "sourceSnapshotRevision": 5,
            "resultPath": "result.json",
            "stdoutPath": "stdout.txt",
            "receiptPath": "receipt.txt",
            "stderrPath": "stderr.txt",
        }
        ledger._validate_spec_identity(spec)
        with self.assertRaises(ledger.LedgerInputError):
            ledger._validate_spec_identity(dict(spec, sourceSnapshotRevision=4))
        with self.assertRaises(ledger.LedgerInputError):
            ledger._validate_spec_identity(dict(spec, inputCount=1066))

    def test_native_receipt_rejects_stale_source_binding_and_nonzero_exit(self):
        stdout = b"native output"
        receipt = b"native receipt"
        stderr = b""
        context = {"inputCount": 1067, "inputBytes": 85_957_798,
                   "sourceSnapshotRevision": 5, "pinsSha256": "a" * 64}
        result = {
            "schema": ledger.RUN_SCHEMA,
            "source": "candidate",
            "suite": ledger.SUITE,
            "cohortId": None,
            "command": ledger._expected_command(),
            "layoutVersion": 26,
            "dependencyEpoch": ledger.DEPENDENCY_EPOCH,
            "sourceSnapshotRevision": 5,
            "inputCount": 1067,
            "inputBytes": 85_957_798,
            "inputPinsSha256": "a" * 64,
            "inputAudit": "PASS_BEFORE_AND_AFTER",
            "exitCode": 0,
            "nativeExitCode": 0,
            "terminationProven": True,
            "cleanupStatus": "PASS",
            "cleanupElapsedMs": None,
            "nativeLimitMs": 900000,
            "outerLimitMs": 1800000,
            "processId": 99,
            "processStartTicks": "1000",
            "elapsedSeconds": 1.0,
            "requestedExecutable": "powershell.exe",
            "stdoutBytes": len(stdout), "stdoutSha256": ledger._sha256(stdout),
            "receiptBytes": len(receipt), "receiptSha256": ledger._sha256(receipt),
            "stderrBytes": len(stderr), "stderrSha256": ledger._sha256(stderr),
        }
        ledger._validate_run_result(result, context, stdout, receipt, stderr)
        stale = dict(result, inputPinsSha256="b" * 64)
        with self.assertRaises(ledger.LedgerInputError):
            ledger._validate_run_result(stale, context, stdout, receipt, stderr)
        failed = dict(result, nativeExitCode=2)
        with self.assertRaises(ledger.LedgerInputError):
            ledger._validate_run_result(failed, context, stdout, receipt, stderr)

    def test_malformed_cli_is_static_and_does_not_echo_private_path(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = ledger.main(["--input-spec", "C:/private/capture.json"])
        self.assertEqual(code, 2)
        self.assertEqual(output.getvalue().strip(), "FAIL: CLI_ARGUMENTS_INVALID")
        self.assertNotIn("private", output.getvalue())

    def test_wrong_spec_digest_is_static_and_writes_no_report(self):
        with tempfile.TemporaryDirectory(prefix="current-prefix-ledger-") as directory:
            path = Path(directory) / "input.json"
            path.write_bytes(b"{}")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                code = ledger.main([
                    "--input-spec", str(path), "--input-spec-sha256", "0" * 64,
                ])
            self.assertEqual(code, 2)
            self.assertEqual(output.getvalue().strip(), "FAIL: INPUT_SPEC_SHA256_MISMATCH")
            self.assertNotIn(directory, output.getvalue())
            self.assertNotIn("PASS", output.getvalue())


if __name__ == "__main__":
    unittest.main()
