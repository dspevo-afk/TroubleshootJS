"""Synthetic-only tests for the pinned normal-prior observation adapter."""

from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import unittest


_SOURCE = Path(__file__).with_name("normalize_prior.py")
_SPEC = importlib.util.spec_from_file_location(
    "_tsj_normalizer_observation_subject", _SOURCE)
if _SPEC is None or _SPEC.loader is None:
    raise RuntimeError("NORMALIZER_SOURCE_LOAD")
_NORMALIZER = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = _NORMALIZER
_SPEC.loader.exec_module(_NORMALIZER)


_OBSERVATION_KEYS = (
    "approvedPriorArtifactRows", "capturedVectorCount", "isolatedCleanupDurationMs",
    "newAuthoredSessions", "newNativeComparisonStatus", "padLandCount",
    "processElapsedMs", "processRootAndLogsAbsentOnRecheck", "projectedCohortNetIds",
    "projectionIsFrozenDescriptor", "projectionSource", "qualificationReceiptHashes",
    "qualificationSummary", "reuseBoundary", "schema", "status",
    "wrapperTotalElapsedMs",
)
_SUMMARY_KEYS = (
    "schema", "status", "manifestSha256", "readerSha256", "resultSha256",
    "operation1Sha256", "trialDisposition", "netCount", "controlDisposition",
    "trialOutcomeDisposition", "controlTraversedCount", "trialTraversedCount",
    "controlLayoutConnectivity", "trialLayoutConnectivity", "layoutQualification",
    "routingTreatmentSelected", "productionAdopted", "feasibilityClaimed",
    "playable100Accepted",
)
_N_ARTIFACTS = (
    ("N_PRIOR_CALL_OWNER_JSON", "call-owner"),
    ("N_PRIOR_CALL_PARENT_JSON", "call-parent"),
    ("N_PRIOR_CALL_PROCESS_JSON", "call-process"),
    ("N_PRIOR_CALL_REQUEST_JSON", "call-request"),
    ("N_PRIOR_CALL_RESULT_JSON", "call-result"),
    ("N_PRIOR_CALL_STDERR_TXT", "call-stderr"),
    ("N_PRIOR_CALL_STDOUT_TXT", "call-stdout"),
    ("N_PRIOR_OPERATION1", "data-op-1"),
    ("N_PRIOR_RESULT", "data-result"),
)
_D_QUALIFICATION_RECEIPTS = (
    ("D_PRIOR_QUALIFICATION_OWNER_JSON", "owner.json"),
    ("D_PRIOR_QUALIFICATION_PARENT_JSON", "parent.json"),
    ("D_PRIOR_QUALIFICATION_PROCESS_JSON", "process.json"),
    ("D_PRIOR_QUALIFICATION_REQUEST_JSON", "request.json"),
    ("D_PRIOR_QUALIFICATION_RESULT_JSON", "result.json"),
    ("D_PRIOR_QUALIFICATION_STDERR_TXT", "stderr.txt"),
    ("D_PRIOR_QUALIFICATION_STDOUT_TXT", "stdout.txt"),
)
_PROCESS_ELAPSED_MS = 112691.18650000001
_WRAPPER_ELAPSED_MS = 112943.8217


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _fixture():
    role_bytes = {}
    pins = {}
    expected_artifacts = []
    for index, (role, artifact) in enumerate(_N_ARTIFACTS):
        raw = bytes((index + 1,))
        path = "synthetic/N/" + artifact
        role_bytes[role] = raw
        pins[role] = {"root": "N", "path": path}
        expected_artifacts.append({
            "path": path, "bytes": len(raw), "sha256": _sha(raw),
        })

    expected_receipts = {}
    for index, (role, filename) in enumerate(_D_QUALIFICATION_RECEIPTS):
        raw = bytes((index + 32,))
        role_bytes[role] = raw
        pins[role] = {"root": "D", "path": "synthetic/D/" + filename}
        expected_receipts[filename] = _sha(raw)

    qualification_summary = {
        "schema": "tsj-failure-cohort-reader-v1",
        "status": "PASS_OBSERVED_FAILURE_COHORT_DIAGNOSIS_ONLY",
        "manifestSha256": "a" * 64,
        "readerSha256": "b" * 64,
        "resultSha256": "c" * 64,
        "operation1Sha256": "d" * 64,
        "trialDisposition": "TRIAL_EXECUTED",
        "netCount": 60,
        "controlDisposition": "NO_PATH",
        "trialOutcomeDisposition": "CAP_REJECTED",
        "controlTraversedCount": 44,
        "trialTraversedCount": 43,
        "controlLayoutConnectivity": None,
        "trialLayoutConnectivity": None,
        "layoutQualification": False,
        "routingTreatmentSelected": False,
        "productionAdopted": False,
        "feasibilityClaimed": False,
        "playable100Accepted": False,
    }
    net_ids = ["SYN_NET_{:02d}".format(index) for index in range(60)]
    cohort_ids = [net_ids[0], net_ids[1]]
    prior_result = {
        "capturedOrder": {"netIds": net_ids},
        "source": {"padLands": [{"padId": "SYN_PAD_{:03d}".format(i)}
                                  for i in range(246)]},
        "trialDisposition": {"cohortNetIds": cohort_ids},
    }
    host_calls = {
        "qualification": {
            "parent": {"elapsedMs": _PROCESS_ELAPSED_MS},
            "result": {"totalElapsedMs": _WRAPPER_ELAPSED_MS},
        },
    }
    observation = {
        "approvedPriorArtifactRows": expected_artifacts,
        "capturedVectorCount": 60,
        "isolatedCleanupDurationMs": None,
        "newAuthoredSessions": 0,
        "newNativeComparisonStatus": "NOT_IMPLEMENTED_NOT_RUN_NOT_ACCEPTED",
        "padLandCount": 246,
        "processElapsedMs": _PROCESS_ELAPSED_MS,
        "processRootAndLogsAbsentOnRecheck": True,
        "projectedCohortNetIds": list(cohort_ids),
        "projectionIsFrozenDescriptor": False,
        "projectionSource": "SYNTHETIC descriptive projection detail",
        "qualificationReceiptHashes": expected_receipts,
        "qualificationSummary": copy.deepcopy(qualification_summary),
        "reuseBoundary": "SYNTHETIC descriptive reuse boundary",
        "schema": "tsj-r2-prior-input-qualification-observation-v1",
        "status": "PASS_PRIOR_INPUT_ONLY",
        "wrapperTotalElapsedMs": _WRAPPER_ELAPSED_MS,
    }
    return observation, role_bytes, pins, host_calls, prior_result, qualification_summary


def _encode(observation):
    return json.dumps(observation, ensure_ascii=True, separators=(",", ":"),
                      allow_nan=True).encode("ascii")


class ObservationCrosscheckTests(unittest.TestCase):
    def _assert_code(self, expected, *, mutate=None, raw_mutate=None, host_mutate=None):
        observation, role_bytes, pins, host_calls, prior_result, summary = _fixture()
        if mutate is not None:
            mutate(observation)
        if host_mutate is not None:
            host_mutate(host_calls)
        raw = _encode(observation)
        if raw_mutate is not None:
            raw = raw_mutate(raw)
        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER._validate_observation_crosscheck(
                raw, role_bytes, pins, host_calls, prior_result, summary)
        self.assertEqual(caught.exception.code, expected)

    def test_full_17_field_observation_joins_synthetic_rows_hashes_and_timings(self):
        observation, role_bytes, pins, host_calls, prior_result, summary = _fixture()
        self.assertEqual(tuple(observation), _OBSERVATION_KEYS)
        self.assertEqual(len(observation["approvedPriorArtifactRows"]), 9)
        self.assertEqual(len(observation["qualificationReceiptHashes"]), 7)
        self.assertEqual(tuple(summary), _SUMMARY_KEYS)
        result = _NORMALIZER._validate_observation_crosscheck(
            _encode(observation), role_bytes, pins, host_calls, prior_result, summary)
        self.assertEqual(result, observation)

    def test_top_level_key_set_is_exact(self):
        self._assert_code(
            "OBSERVATION_KEYS", mutate=lambda observation: observation.pop("reuseBoundary"))
        self._assert_code(
            "OBSERVATION_KEYS",
            mutate=lambda observation: observation.update(syntheticExtra=True))

    def test_timing_values_require_finite_float_type_and_authenticated_join(self):
        self._assert_code(
            "OBSERVATION_TIMING_BINDING",
            mutate=lambda observation: observation.update(processElapsedMs=112691))
        self._assert_code(
            "OBSERVATION_TIMING_BINDING",
            mutate=lambda observation: observation.update(processElapsedMs=True))
        self._assert_code(
            "OBSERVATION_TIMING_BINDING",
            mutate=lambda observation: observation.update(processElapsedMs=112691.0))
        self._assert_code(
            "OBSERVATION_TIMING_BINDING",
            mutate=lambda observation: observation.update(wrapperTotalElapsedMs=112943))
        self._assert_code(
            "OBSERVATION_TIMING_BINDING",
            mutate=lambda observation: observation.update(wrapperTotalElapsedMs=112943.0))
        self._assert_code(
            "JSON_NONFINITE",
            mutate=lambda observation: observation.update(processElapsedMs=float("inf")))
        self._assert_code(
            "OBSERVATION_TIMING_BINDING",
            host_mutate=lambda calls: calls["qualification"]["parent"].update(
                elapsedMs=float("inf")))

    def test_nonfinite_constants_and_float_overflow_reject_locally(self):
        self._assert_code(
            "JSON_NONFINITE",
            mutate=lambda observation: observation.update(processElapsedMs=float("nan")))

        marker = b'"processElapsedMs":112691.18650000001'
        replacement = b'"processElapsedMs":1e999'

        def overflow(raw):
            if marker not in raw:
                raise AssertionError("SYNTHETIC_TIMING_MARKER_MISSING")
            return raw.replace(marker, replacement, 1)

        self._assert_code(
            "OBSERVATION_TIMING_BINDING", raw_mutate=overflow)

    def test_artifact_rows_require_three_exact_fields_and_pinned_order(self):
        self._assert_code(
            "OBSERVATION_ARTIFACT_ROW_KEYS",
            mutate=lambda observation: observation["approvedPriorArtifactRows"][0].update(
                role="call-owner"))
        self._assert_code(
            "OBSERVATION_ARTIFACT_ROW_KEYS",
            mutate=lambda observation: observation["approvedPriorArtifactRows"][0].update(
                extra="SYNTHETIC_ONLY"))
        self._assert_code(
            "OBSERVATION_ARTIFACT_ROW_KEYS",
            mutate=lambda observation: observation["approvedPriorArtifactRows"][0].pop(
                "sha256"))
        self._assert_code(
            "OBSERVATION_ARTIFACT_ROWS",
            mutate=lambda observation: observation["approvedPriorArtifactRows"].pop())
        self._assert_code(
            "OBSERVATION_ARTIFACT_BINDING",
            mutate=lambda observation: observation["approvedPriorArtifactRows"].reverse())

    def test_changed_path_byte_count_or_digest_in_three_field_row_rejects(self):
        changes = (
            ("path", "synthetic/N/changed-path"),
            ("bytes", 999),
            ("bytes", True),
            ("sha256", "0" * 64),
        )
        for key, value in changes:
            with self.subTest(key=key, value=value):
                self._assert_code(
                    "OBSERVATION_ARTIFACT_BINDING",
                    mutate=lambda observation, k=key, v=value:
                        observation["approvedPriorArtifactRows"][0].update({k: v}))

    def test_summary_comparison_preserves_exact_primitive_types(self):
        self._assert_code(
            "OBSERVATION_QUALIFICATION_BINDING",
            mutate=lambda observation: observation["qualificationSummary"].update(
                controlTraversedCount=44.0))
        self._assert_code(
            "OBSERVATION_QUALIFICATION_BINDING",
            mutate=lambda observation: observation["qualificationSummary"].update(
                playable100Accepted=0))
        self._assert_code(
            "OBSERVATION_QUALIFICATION_SUMMARY_KEYS",
            mutate=lambda observation: observation["qualificationSummary"].pop("netCount"))

    def test_counts_cohort_and_denial_cleanup_fields_are_exactly_typed(self):
        self._assert_code(
            "OBSERVATION_RAW_RESULT_BINDING",
            mutate=lambda observation: observation.update(capturedVectorCount=60.0))
        self._assert_code(
            "OBSERVATION_RAW_RESULT_BINDING",
            mutate=lambda observation: observation.update(padLandCount=246.0))
        self._assert_code(
            "OBSERVATION_RAW_RESULT_BINDING",
            mutate=lambda observation: observation.update(projectedCohortNetIds=[]))
        self._assert_code(
            "OBSERVATION_STATE_BINDING",
            mutate=lambda observation: observation.update(newAuthoredSessions=False))
        self._assert_code(
            "OBSERVATION_STATE_BINDING",
            mutate=lambda observation: observation.update(
                projectionIsFrozenDescriptor=0))
        self._assert_code(
            "OBSERVATION_STATE_BINDING",
            mutate=lambda observation: observation.update(
                processRootAndLogsAbsentOnRecheck=1))
        self._assert_code(
            "OBSERVATION_STATE_BINDING",
            mutate=lambda observation: observation.update(isolatedCleanupDurationMs=0))
        self._assert_code(
            "OBSERVATION_STATE_BINDING",
            mutate=lambda observation: observation.update(
                newNativeComparisonStatus="SYNTHETIC_ACCEPTED"))

    def test_descriptive_fields_must_remain_bounded_nonempty_data(self):
        self._assert_code(
            "OBSERVATION_DESCRIPTION",
            mutate=lambda observation: observation.update(projectionSource=""))
        self._assert_code(
            "OBSERVATION_DESCRIPTION",
            mutate=lambda observation: observation.update(reuseBoundary=17))
        self._assert_code(
            "OBSERVATION_DESCRIPTION",
            mutate=lambda observation: observation.update(projectionSource="\x00"))

    def test_qualification_receipt_hash_map_remains_exact(self):
        self._assert_code(
            "OBSERVATION_RECEIPT_HASHES",
            mutate=lambda observation: observation["qualificationReceiptHashes"].update(
                {"owner.json": "0" * 64}))


if __name__ == "__main__":
    unittest.main()
