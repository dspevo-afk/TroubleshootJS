"""Source-authored synthetic N-run cases for the current pure kernel.

The test loads only the current extracted kernel (by exact SHA-256) and builds
small synthetic run dictionaries in memory.  It never reads the prior DATA,
imports an old reader/builder, or admits a public role byte map.  When the
kernel is not beside this file, run this file with ``--kernel-directory``
pointing to the private pure-kernel-r3 directory.
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
from pathlib import Path
import sys
import unittest


_EXPECTED_KERNEL_SHA256 = "f87c3ef799a47f14955240111d7af20737db3c329242c036f449eb1002f11873"

_NORMALIZER_SOURCE = Path(__file__).with_name("normalize_prior.py")
_NORMALIZER_SPEC = importlib.util.spec_from_file_location(
    "_tsj_normalizer_semantics_subject", _NORMALIZER_SOURCE)
if _NORMALIZER_SPEC is None or _NORMALIZER_SPEC.loader is None:
    raise RuntimeError("NORMALIZER_SOURCE_LOAD")
_NORMALIZER = importlib.util.module_from_spec(_NORMALIZER_SPEC)
sys.modules[_NORMALIZER_SPEC.name] = _NORMALIZER
_NORMALIZER_SPEC.loader.exec_module(_NORMALIZER)


def _kernel_path() -> Path:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--kernel-directory")
    args, remaining = parser.parse_known_args()
    sys.argv[:] = [sys.argv[0], *remaining]
    directory = Path(args.kernel_directory) if args.kernel_directory else Path(__file__).parent
    return directory / "prior_kernel.py"


_KERNEL_SOURCE = _kernel_path()
if not _KERNEL_SOURCE.is_file():
    raise RuntimeError("CURRENT_PURE_KERNEL_UNAVAILABLE: pass --kernel-directory")
_KERNEL_BYTES = _KERNEL_SOURCE.read_bytes()
if hashlib.sha256(_KERNEL_BYTES).hexdigest() != _EXPECTED_KERNEL_SHA256:
    raise RuntimeError("CURRENT_PURE_KERNEL_SHA256")
_KERNEL_SPEC = importlib.util.spec_from_file_location(
    "_tsj_current_pure_prior_kernel_fixture", _KERNEL_SOURCE)
if _KERNEL_SPEC is None or _KERNEL_SPEC.loader is None:
    raise RuntimeError("CURRENT_PURE_KERNEL_LOAD")
_KERNEL = importlib.util.module_from_spec(_KERNEL_SPEC)
sys.modules[_KERNEL_SPEC.name] = _KERNEL
_KERNEL_SPEC.loader.exec_module(_KERNEL)


def _callbacks():
    return {
        "stable_cohort_order": _KERNEL.stable_cohort_order,
        "validate_prior_run": _KERNEL.validate_prior_run,
        "validate_prior_terminal_failure": _KERNEL.validate_prior_terminal_failure,
        "result_tree_minimum_units": _KERNEL.result_tree_minimum_units,
        "validate_operation": _KERNEL.validate_operation,
    }


def _captured_fixture():
    net_ids = ["A_COIL_LOW", "B_COIL_LOW", "D_SENSE"]
    net_ids.extend("SYN_NET_{:02d}".format(index) for index in range(57))
    owner = {
        "policy": "FULLER_TWO_LAYER",
        "maximumExpansions": 1_000_000,
        "expandedAtFailure": 0,
        "maximumVias": 48,
        "viaCost": 60,
        "policyTransitions": 4,
        "policyVias": 48,
        "branchOrderStrategy": "CANONICAL_NEAREST",
        "netOrderStrategy": "CANONICAL",
        "roleWidths": True,
        "orderingStream": True,
    }
    return {
        "owner": owner,
        "netIds": net_ids,
        "failedNetId": "A_COIL_LOW",
        "priorPromotedNetId": "D_SENSE",
    }


def _expected_order(captured_ids: list[str], cohort_ids: list[str]) -> list[str]:
    # Independent fixture oracle, intentionally separate from the kernel helper.
    cohort_set = set(cohort_ids)
    return list(cohort_ids) + [net_id for net_id in captured_ids if net_id not in cohort_set]


def _run_fixture(kind: str, captured: dict, cohort_ids: list[str],
                 failed_net: str, disposition: str, reason: str):
    expected = _expected_order(captured["netIds"], cohort_ids)
    search_index = expected.index(failed_net) + 1
    traversed = expected[:search_index]
    terminal_units = 11 + len(traversed)
    if kind == "OBSERVED_FAILURE_CONTROL" and disposition == "NO_PATH":
        terminal_units += 1
    advance_calls = 2
    work = {
        "advanceCalls": advance_calls,
        "ownerUnits": 7,
        "wrapperUnits": advance_calls + advance_calls + terminal_units,
        "copyEntries": 0,
        "copyChunks": 0,
        "groups": 1,
        "maxGroupNanos": 100,
        "maximumUnitsPerGroup": 7 + advance_calls + advance_calls + terminal_units,
        "failureObservationUnits": advance_calls,
        "terminalEvidenceUnits": terminal_units,
    }
    owner = captured["owner"]
    initial = {
        "pass": 2,
        "passes": 0,
        "expanded": 0,
        "maximumExpansions": owner["maximumExpansions"],
        "maximumVias": owner["maximumVias"],
        "priorPromotedNetId": captured["priorPromotedNetId"],
        "policy": owner["policy"],
        "viaCost": owner["viaCost"],
        "policyTransitions": owner["policyTransitions"],
        "policyVias": owner["policyVias"],
        "branchOrderStrategy": owner["branchOrderStrategy"],
        "netOrderStrategy": owner["netOrderStrategy"],
        "roleWidths": owner["roleWidths"],
        "capturedOrderingStream": owner["orderingStream"],
        "sessionOrderingStream": False,
        "instrumentationModeDifference": owner["orderingStream"],
        "routingRelevantSettingsMatch": True,
        "traceCount": 0,
        "holeCount": 0,
    }
    ordering = {
        "capturedNetIds": list(captured["netIds"]),
        "computedNetIds": list(captured["netIds"]),
        "requestedCohortNetIds": list(cohort_ids),
        "requestedNetIds": expected,
        "traversedNetIds": traversed,
        "orderMatchesCapture": True,
        "searchCount": 1,
    }
    outcome = {
        "disposition": disposition,
        "reason": reason,
        "complete": False,
        "ownerComplete": True,
        "pass": 3,
        "passes": 1,
        "expanded": 4,
        "maximumExpansions": owner["maximumExpansions"],
        "errorClass": None,
        "errorMessage": None,
    }
    terminal_failure = {
        "failedNetId": failed_net,
        "searchIndex": search_index,
        "searchPass": 2,
        "passBefore": 2,
        "passAfter": 3,
        "advanceCallOrdinal": advance_calls - 1,
        "ownerSearchCleared": True,
        "searchReleased": True,
        "ownerPromotedNetId": failed_net,
        "ownerReason": reason,
    }
    return {
        "kind": kind,
        "initial": initial,
        "ordering": ordering,
        "outcome": outcome,
        "terminalFailure": terminal_failure,
        "finalizers": {
            "routingGeometry": "NOT_RUN",
            "allNetConnectivity": "NOT_RUN",
            "twoLayerRules": "NOT_RUN",
            "routeQuality": "NOT_RUN",
            "viaCap": "NOT_RUN",
        },
        "layout": None,
        "work": work,
    }


class NormalizerSyntheticRunTests(unittest.TestCase):
    def _validate(self, run, kind, captured, cohort_ids, *, control):
        return _NORMALIZER._validate_prior_run(
            _callbacks(), run, kind, captured, cohort_ids, control=control)

    def test_synthetic_control_no_path_run_passes_current_n_kernel_and_adapter(self):
        captured = _captured_fixture()
        cohort = ["A_COIL_LOW"]
        expected = _expected_order(captured["netIds"], cohort)
        run = _run_fixture(
            "OBSERVED_FAILURE_CONTROL", captured, cohort, "B_COIL_LOW",
            "NO_PATH", "B_COIL_LOW:NO_PATH")

        result = self._validate(run, run["kind"], captured, cohort, control=True)

        self.assertEqual(run["ordering"]["requestedNetIds"], expected)
        self.assertEqual(result["expectedOrder"], expected)
        self.assertEqual(result["terminalFailure"], run["terminalFailure"])
        self.assertEqual(result["outcome"]["disposition"], "NO_PATH")

    def test_synthetic_trial_cap_rejection_run_passes_current_n_kernel_and_adapter(self):
        captured = _captured_fixture()
        cohort = ["A_COIL_LOW", "B_COIL_LOW"]
        expected = _expected_order(captured["netIds"], cohort)
        run = _run_fixture(
            "OBSERVED_FAILURE_COHORT_TRIAL", captured, cohort, "SYN_NET_00",
            "CAP_REJECTED", "SYN_NET_00:TOTAL_SEARCH_LIMIT")

        result = self._validate(run, run["kind"], captured, cohort, control=False)

        self.assertEqual(run["ordering"]["requestedNetIds"], expected)
        self.assertEqual(result["expectedOrder"], expected)
        self.assertEqual(result["terminalFailure"], run["terminalFailure"])
        self.assertEqual(result["work"]["terminalEvidenceUnits"], 15)

    def test_synthetic_requested_order_mutation_is_rejected_by_current_n_kernel(self):
        captured = _captured_fixture()
        cohort = ["A_COIL_LOW"]
        run = _run_fixture(
            "OBSERVED_FAILURE_CONTROL", captured, cohort, "B_COIL_LOW",
            "NO_PATH", "B_COIL_LOW:NO_PATH")
        run["ordering"]["requestedNetIds"][1] = "SYN_NET_56"

        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            self._validate(run, run["kind"], captured, cohort, control=True)

        self.assertEqual(caught.exception.code, "PRIOR_RUN_BF_FRESH_ORDERING_BINDING")

    def test_synthetic_terminal_advance_mutation_is_rejected_by_current_n_kernel(self):
        captured = _captured_fixture()
        cohort = ["A_COIL_LOW"]
        run = _run_fixture(
            "OBSERVED_FAILURE_CONTROL", captured, cohort, "B_COIL_LOW",
            "NO_PATH", "B_COIL_LOW:NO_PATH")
        run["terminalFailure"]["advanceCallOrdinal"] = 0

        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            self._validate(run, run["kind"], captured, cohort, control=True)

        self.assertEqual(caught.exception.code, "PRIOR_RUN_BF_TERMINAL_FAILURE_BINDING")


if __name__ == "__main__":
    unittest.main()
