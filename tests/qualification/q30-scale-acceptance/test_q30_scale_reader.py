import hashlib
import json
import copy
import unittest
from pathlib import Path

import q30_scale_reader as reader
import freeze_plan


def hash_text(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def frozen_plan():
    acceptance, raw = reader.load(reader.PACKAGE / "acceptance-plan.json")
    files = [{"path": "src/example.java", "size": 1, "sha256": hash_text("x")}]
    source_identity = hash_text(json.dumps(
        {"schema": 1, "baseHead": acceptance["baseHead"], "files": files},
        sort_keys=True, separators=(",", ":"), ensure_ascii=False))
    cases = []
    candidate_counter = 0
    for index, source in enumerate(acceptance["cases"]):
        candidates = []
        for ordinal in range(4):
            seed = str(300000 + candidate_counter)
            candidate_counter += 1
            manifest = ("candidate=%02d;seed=%s;plan=rb30-plan@4;packages=24;channels=1;"
                        "search=false;" % (ordinal, seed))
            candidates.append({"ordinal": ordinal, "seed": seed, "packages": 24,
                              "channels": 1, "planCanonical": "rb30-plan@4;seed=" + seed +
                              ";packages=24;channels=1", "manifest": manifest})
        first = candidates[0]["manifest"].split(";", 1)[1]
        cases.append({"manifestOrder": index, "seed": source["seed"],
                      "cohort": source["cohort"], "canonical": source["canonical"],
                      "packages": source["packages"], "channels": source["channels"],
                      "requestCanonical": first.replace(";search=false;", ";search=true;"),
                      "candidates": candidates})
    prepared = {"fileCount": 3, "sourceFileCount": 2, "webFileCount": 1,
                "combinedSha256": hash_text("combined"), "sourceSha256": hash_text("src"),
                "webSha256": hash_text("war")}
    plan = {"schema": 1, "status": "PLANNED", "baseHead": acceptance["baseHead"],
            "planEpoch": 4, "manifest": reader.MANIFEST,
            "manifestSha256": acceptance["manifestSha256"],
            "acceptancePlanSha256": hash_text(raw.decode("utf-8")),
            "compiledJavaExportSha256": hash_text("export"),
            "manifestExportEvidence": {"outcome": "PASS",
                "reportSha256": hash_text("export"),
                "hostRecordSha256": hash_text("host"),
                "startedUtc": "2026-10-01T00:00:00Z",
                "finishedUtc": "2026-10-01T00:00:01Z"},
            "sourceIdentity": source_identity,
            "sourceSnapshotSha256": hash_text("snapshot"), "sourceFileCount": 1,
            "sourceFiles": files, "wrapperIdentity": hash_text("wrapper"),
            "hostRunnerSha256": hash_text("runner"),
            "baselinePatchSha256": hash_text("patch"),
            "preparedInputIdentity": prepared, "limits": reader.LIMITS,
            "rootCount": 77, "coldOrder": acceptance["coldOrder"], "cases": cases}
    return plan


def sample(sample_id="s0"):
    return {"id": sample_id, "outcome": "NUMERIC", "value": 0.0, "tolerance": 0.1}


def evidence():
    # Synthetic sample objects exercise schema only; they are never acceptance receipts.
    return [{"hypothesis": "h%d" % i, "repairReachable": True,
             "customerRetestPassed": True, "stateIsolated": True,
             "deterministicResult": "stable", "samples": [sample("%d-%d" % (i, j))
                 for j in range(17)]} for i in range(5)]


class ScaleReaderContractTests(unittest.TestCase):
    def test_synthetic_complete_plan_shape_and_epoch_mismatch(self):
        plan = frozen_plan()
        reader.validate_plan(plan, b"fixture")
        plan["planEpoch"] = 3
        with self.assertRaisesRegex(reader.Invalid, "epoch"):
            reader.validate_plan(plan, b"fixture")

    def test_rejects_missing_hypothesis(self):
        proof = {"warmReuse": False, "explicitCompletion": True,
                 "context": "c", "program": "p", "partition": "x",
                 "evidence": evidence()[:-1]}
        with self.assertRaisesRegex(reader.Invalid, "five hypotheses"):
            reader.validate_proof(proof, 17)

    def test_rejects_missing_channel_sample(self):
        proof = {"warmReuse": False, "explicitCompletion": True,
                 "context": "c", "program": "p", "partition": "x",
                 "evidence": evidence()}
        proof["evidence"][0]["samples"].pop()
        with self.assertRaisesRegex(reader.Invalid, "sample count"):
            reader.validate_proof(proof, 17)

    def test_synthetic_two_channel_sample_contract(self):
        proof = {"warmReuse": False, "explicitCompletion": True,
                 "context": "c", "program": "p", "partition": "x",
                 "evidence": evidence()}
        for row in proof["evidence"]:
            row["samples"].extend(sample("extra-%s-%d" % (row["hypothesis"], i))
                                  for i in range(20))
        reader.validate_proof(proof, 37)

    def test_synthetic_two_channel_proof_rejects_missing_sample(self):
        proof = {"warmReuse": False, "explicitCompletion": True,
                 "context": "c", "program": "p", "partition": "x",
                 "evidence": evidence()}
        for row in proof["evidence"]:
            row["samples"].extend(sample("extra-%s-%d" % (row["hypothesis"], i))
                                  for i in range(20))
        proof["evidence"][0]["samples"].pop()
        with self.assertRaisesRegex(reader.Invalid, "sample count"):
            reader.validate_proof(proof, 37)

    def test_rejects_warm_or_private_cache_and_incomplete_cleanup(self):
        for key in ("initialCachesEmpty", "privateCacheUntouched", "cleanupComplete"):
            report = {"initialCachesEmpty": True, "privateCacheUntouched": True,
                      "cleanupComplete": True, "ordinaryCacheHits": 0}
            report[key] = False
            with self.subTest(key=key), self.assertRaisesRegex(reader.Invalid, key):
                reader.validate_cache_cleanup(report)
        report = {"initialCachesEmpty": True, "privateCacheUntouched": True,
                  "cleanupComplete": True, "ordinaryCacheHits": 1}
        with self.assertRaisesRegex(reader.Invalid, "warm"):
            reader.validate_cache_cleanup(report)

    def test_optional_matrix_observation_metadata_accepts_current_and_legacy_shapes(self):
        scope = reader.MATRIX_OBSERVATION_SCOPE
        reader.validate_matrix_observations({
            "classification": "TIMEOUT", "observedMatrixOrderMax": 90,
            "matrixObservations": 200, "matrixObservationScope": scope,
        })
        reader.validate_matrix_observations({
            "classification": "INFRASTRUCTURE_FAILURE", "observedMatrixOrderMax": 0,
            "matrixObservations": 0, "matrixObservationScope": scope,
        })
        reader.validate_matrix_observations({
            "classification": "PASS", "observedMatrixOrderMax": 90,
            "matrixObservations": 1, "matrixObservationScope": scope,
        })
        # Historical r1-r3 reports have no matrix telemetry and remain valid.
        reader.validate_matrix_observations({"classification": "PASS"})

    def test_optional_matrix_observation_metadata_rejects_partial_or_inconsistent_receipts(self):
        valid = {
            "classification": "TIMEOUT",
            "observedMatrixOrderMax": 90,
            "matrixObservations": 200,
            "matrixObservationScope": reader.MATRIX_OBSERVATION_SCOPE,
        }
        mutations = [
            ("partial fields", lambda r: r.pop("matrixObservationScope")),
            ("boolean maximum", lambda r: r.update(observedMatrixOrderMax=True)),
            ("boolean count", lambda r: r.update(matrixObservations=False)),
            ("fractional maximum", lambda r: r.update(observedMatrixOrderMax=90.0)),
            ("negative maximum", lambda r: r.update(observedMatrixOrderMax=-1)),
            ("negative count", lambda r: r.update(matrixObservations=-1)),
            ("wrong scope", lambda r: r.update(matrixObservationScope="exhaustive maximum")),
            ("zero count with positive maximum",
             lambda r: r.update(matrixObservations=0)),
            ("positive count with zero maximum",
             lambda r: r.update(observedMatrixOrderMax=0)),
        ]
        for label, mutate in mutations:
            changed = copy.deepcopy(valid)
            mutate(changed)
            with self.subTest(label=label), self.assertRaises(reader.Invalid):
                reader.validate_matrix_observations(changed)

        passed_without_observation = dict(valid, classification="PASS",
                                          observedMatrixOrderMax=0,
                                          matrixObservations=0)
        with self.assertRaisesRegex(reader.Invalid, "PASS lacks"):
            reader.validate_matrix_observations(passed_without_observation)

    def test_manifest_export_host_may_cover_canaries_but_rejects_duplicate_binding(self):
        export_raw = b"compiled java export"
        export_sha = hashlib.sha256(export_raw).hexdigest()
        matched = {"reportSha256": export_sha, "outcome": "PASS",
                   "reportObserved": True, "reportJsonValid": True,
                   "reportMatch": True, "stateMatch": True,
                   "terminalReached": True,
                   "url": "http://127.0.0.1/circuitjs.html?tsjNormalMode=manifest-export"}
        host = {"outcome": "PASS", "errors": [], "caseCount": 4,
                "startedUtc": "2026-10-01T00:00:00Z",
                "finishedUtc": "2026-10-01T00:00:01Z",
                "cases": [matched, {"reportSha256": "other"},
                          {"reportSha256": "other-2"}, {"reportSha256": "other-3"}]}
        evidence = freeze_plan.validate_manifest_export_host(export_raw, host, b"host")
        self.assertEqual(evidence["caseCount"], 4)
        host["cases"].append(dict(matched))
        host["caseCount"] = 5
        with self.assertRaisesRegex(freeze_plan.Invalid, "exactly one case"):
            freeze_plan.validate_manifest_export_host(export_raw, host, b"host")

    def test_synthetic_cold003_timeout_links_terminal_attempt_and_failure(self):
        # Reduced synthetic fixture mirrors the captured cold-003 timeout shape;
        # it is not a generated or accepted Q30 result.
        report = {
            "classification": "TIMEOUT",
            "applicationOutcome": "TIMEOUT",
            "terminalStage": "HYPOTHESES",
            "failure": ("com.lushprojects.circuitjs1.client.GenerationJob$Deadline: "
                        "Generation wall budget exhausted"),
            "attempts": [{
                "ordinal": 0,
                "stage": "HYPOTHESES",
                "outcome": "TIMEOUT",
                "failureType": "Deadline",
                "failureMessage": "Generation wall budget exhausted",
            }],
        }
        reader.validate_terminal_consistency(report)
        mutations = [
            ("outcome", lambda r: r["attempts"][0].update(outcome="PASS")),
            ("stage", lambda r: r["attempts"][0].update(stage="PHYSICAL")),
            ("terminalStage", lambda r: r.update(terminalStage="SYMPTOM")),
            ("failureType", lambda r: r["attempts"][0].update(failureType="Other")),
            ("failureMessage", lambda r: r["attempts"][0].update(failureMessage="changed")),
            ("applicationFailure", lambda r: r.update(failure="Deadline: changed")),
        ]
        for label, mutate in mutations:
            changed = copy.deepcopy(report)
            mutate(changed)
            with self.subTest(label=label), self.assertRaises(reader.Invalid):
                reader.validate_terminal_consistency(changed)

    def test_actual_schema_programming_failure_is_a_consistent_nonpass(self):
        # Captured cold-r2 seed 35 shape: the harness classifies the terminal
        # GenerationJob programming outcome as infrastructure failure while
        # preserving the application's exact enum and failure receipt.
        message = "Normal medium admission rejected an invalid route receipt"
        report = {
            "schema": 1,
            "classification": "INFRASTRUCTURE_FAILURE",
            "applicationOutcome": "PROGRAMMING_FAILURE",
            "terminalStage": "HEALTHY",
            "failure": "java.lang.IllegalArgumentException: " + message,
            "attempts": [{
                "ordinal": 0,
                "stage": "HEALTHY",
                "outcome": "PROGRAMMING_FAILURE",
                "failureType": "IllegalArgumentException",
                "failureMessage": message,
            }],
        }
        reader.validate_terminal_consistency(report)

        mutations = [
            ("classification", lambda r: r.update(classification="ADMISSION_REJECTED")),
            ("application outcome", lambda r: r.update(applicationOutcome="WORK_EXHAUSTED")),
            ("attempt outcome", lambda r: r["attempts"][0].update(outcome="INFRASTRUCTURE_FAILURE")),
            ("attempt stage", lambda r: r["attempts"][0].update(stage="PHYSICAL")),
            ("terminal stage", lambda r: r.update(terminalStage="PHYSICAL")),
            ("failure type", lambda r: r["attempts"][0].update(failureType="IllegalStateException")),
            ("failure message", lambda r: r["attempts"][0].update(failureMessage="changed")),
            ("top-level failure", lambda r: r.update(failure="java.lang.IllegalArgumentException: changed")),
            ("missing failure", lambda r: r.update(failure=None)),
        ]
        for label, mutate in mutations:
            changed = copy.deepcopy(report)
            mutate(changed)
            with self.subTest(label=label), self.assertRaises(reader.Invalid):
                reader.validate_terminal_consistency(changed)

    def test_reader_digest_matches_exact_current_script_bytes(self):
        digests = reader.qualification_code_digests()
        expected = hashlib.sha256(Path(reader.__file__).resolve().read_bytes()).hexdigest()
        self.assertEqual(digests["readerSha256"], expected)
        for value in digests.values():
            self.assertRegex(value, r"^[0-9a-f]{64}$")


if __name__ == "__main__":
    unittest.main()
