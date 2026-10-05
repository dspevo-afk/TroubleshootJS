"""Focused tests for the qualification receipt ledger; no process or physics fixtures."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import receipts



def passing(seed="75"):
    return {
        "schema": 1, "rootSeed": seed, "sourceIdentity": "a" * 64,
        "baseHead": "b" * 40, "planSha256": "c" * 64,
        "limits": {"jobMillis": 90000, "sharedWork": 640, "activeOperationMillis": 5000},
        "application": {"status": "PASS", "elapsedMs": 82000, "workUnits": 600,
                       "maxActiveOperationMs": 4000, "maxCoordinatorAdvanceMs": 4500},
        "host": {"status": "PASS", "observationElapsedMs": 12000, "deadlineMs": 150000,
                 "exitCode": 0, "terminal": True},
        "cleanup": {"status": "PASS", "elapsedMs": 250, "elapsedLimitations": None,
                    "serverStopped": True, "ownedSurvivors": []},
        "operation": {"elapsedMs": 82000, "elapsedLimitations": None},
        "sourceInputsUnchanged": True,
    }


def _manifest_fixture(root):
    sources = {}
    for name in receipts.WORKFLOW_SOURCE_NAMES:
        content = ("fixture:" + name).encode("utf-8")
        (root / name).write_bytes(content)
        sources[name] = {"sha256": hashlib.sha256(content).hexdigest(),
                         "size": len(content)}
    helper_sha = "d" * 64
    manifest = {
        "schema": 2,
        "inputBinding": {
            "sourceIdentity": "a" * 64, "baseHead": "b" * 40,
            "planSha256": "c" * 64, "acceptancePlanSha256": "e" * 64,
            "repositoryInputs": 1355, "repositoryMapSha256": "f" * 64,
            "preparedAppStateSha256": "1" * 64,
            "preparedInputIdentity": {
                "combinedSha256": "2" * 64, "fileCount": 1526,
                "sourceFileCount": 1134, "sourceSha256": "3" * 64,
                "webFileCount": 392, "webSha256": "4" * 64,
            },
            "readerSha256": "5" * 64, "hostRunnerSha256": "6" * 64,
            "pointerSha256": "7" * 64, "matchedHelperSha256": helper_sha,
        },
        "externalMatchedHelperSha256": helper_sha,
        "limits": dict(receipts.LIMITS),
        "sources": sources,
        "windowMs": 15120000,
    }
    path = root / receipts.WORKFLOW_MANIFEST_FILENAME
    path.write_text(json.dumps(manifest, sort_keys=True), encoding="utf-8")
    return path, manifest


class WorkflowManifestTests(unittest.TestCase):
    def test_current_manifest_schema_binds_exact_local_and_external_sources(self):
        with tempfile.TemporaryDirectory(prefix="q30-workflow-manifest-") as temp:
            root = Path(temp)
            path, expected = _manifest_fixture(root)
            actual = receipts.load_workflow_manifest(root, path)
            self.assertEqual(actual, expected)

    def test_legacy_schema_and_unknown_source_are_rejected(self):
        with tempfile.TemporaryDirectory(prefix="q30-workflow-manifest-") as temp:
            root = Path(temp)
            path, manifest = _manifest_fixture(root)
            manifest["schema"] = 1
            path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaises(receipts.ReceiptError):
                receipts.load_workflow_manifest(root, path)

            manifest["schema"] = 2
            manifest["sources"]["old-copy.py"] = {"sha256": "8" * 64, "size": 1}
            path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaises(receipts.ReceiptError):
                receipts.load_workflow_manifest(root, path)

    def test_changed_source_pin_and_external_helper_mismatch_are_rejected(self):
        with tempfile.TemporaryDirectory(prefix="q30-workflow-manifest-") as temp:
            root = Path(temp)
            path, _ = _manifest_fixture(root)
            (root / "serial_runner.py").write_bytes(b"changed")
            with self.assertRaisesRegex(receipts.ReceiptError, "pin changed"):
                receipts.load_workflow_manifest(root, path)

        with tempfile.TemporaryDirectory(prefix="q30-workflow-manifest-") as temp:
            root = Path(temp)
            path, manifest = _manifest_fixture(root)
            manifest["externalMatchedHelperSha256"] = "8" * 64
            path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(receipts.ReceiptError, "differs"):
                receipts.load_workflow_manifest(root, path)

    def test_duplicate_manifest_key_is_rejected(self):
        with tempfile.TemporaryDirectory(prefix="q30-workflow-manifest-") as temp:
            root = Path(temp)
            path, _ = _manifest_fixture(root)
            path.write_text('{"schema":2,"schema":2}', encoding="utf-8")
            with self.assertRaisesRegex(receipts.ReceiptError, "duplicate"):
                receipts.load_workflow_manifest(root, path)


class ReceiptTests(unittest.TestCase):
    def test_complete_receipt_passes(self):
        self.assertEqual(receipts.validate_case(passing()), "PASS")

    def test_application_timeout_over_cap_fails_even_when_host_passes(self):
        for status in ("TIMEOUT", "PASS"):
            with self.subTest(status=status):
                item = passing(); item["application"].update(status=status, elapsedMs=90221)
                self.assertEqual(receipts.validate_case(item), "FAIL")

    def test_mixed_source_or_plan_cannot_pass_aggregate(self):
        for key, changed in (("sourceIdentity", "d" * 64), ("baseHead", "e" * 40),
                             ("planSha256", "f" * 64)):
            with self.subTest(key=key):
                item = passing("76"); item[key] = changed
                with self.assertRaises(receipts.ReceiptError):
                    receipts.summarize_cases(["75", "76"], [passing(), item])

    def test_work_and_both_active_caps_cannot_be_mislabeled_pass(self):
        for key, value in (("workUnits", 641), ("maxActiveOperationMs", 5001),
                           ("maxCoordinatorAdvanceMs", 5001)):
            with self.subTest(key=key):
                item = passing(); item["application"][key] = value
                self.assertEqual(receipts.validate_case(item), "FAIL")

    def test_source_drift_fails(self):
        item = passing(); item["sourceInputsUnchanged"] = False
        self.assertEqual(receipts.validate_case(item), "FAIL")

    def test_missing_timing_field_raises(self):
        item = passing(); del item["application"]["elapsedMs"]
        with self.assertRaises(receipts.ReceiptError): receipts.validate_case(item)

    def test_passed_app_failed_host_fails(self):
        item = passing(); item["host"].update(status="FAIL", exitCode=1)
        self.assertEqual(receipts.validate_case(item), "FAIL")

    def test_cleanup_failure_fails(self):
        item = passing(); item["cleanup"].update(status="FAIL", serverStopped=False)
        self.assertEqual(receipts.validate_case(item), "FAIL")

    def test_existing_bytes_are_never_overwritten(self):
        self.base = Path(tempfile.mkdtemp(prefix="q30-receipt-overwrite-test-"))
        path = self.base / "receipt.json"; original = b"keep these bytes\x00"
        path.write_bytes(original)
        with self.assertRaises(FileExistsError): receipts.write_exclusive(path, passing())
        self.assertEqual(path.read_bytes(), original)

    def test_omitted_seed_is_explicit_not_run_and_incomplete(self):
        summary = receipts.summarize_cases(["75", "76"], [passing("75")])
        self.assertFalse(summary["complete"]); self.assertEqual(summary["outcome"], "FAIL")
        self.assertEqual(summary["rows"][1], {"seed": "76", "status": "NOT_RUN", "receipt": None})

    def test_out_of_order_receipts_raise(self):
        with self.assertRaises(receipts.ReceiptError):
            receipts.summarize_cases(["75", "76"], [passing("76"), passing("75")])

    def test_duplicate_receipts_raise(self):
        with self.assertRaises(receipts.ReceiptError):
            receipts.summarize_cases(["75"], [passing(), passing()])

    def test_unknown_seed_raises(self):
        with self.assertRaises(receipts.ReceiptError): receipts.summarize_cases(["75"], [passing("77")])

    def test_boolean_limit_is_not_integer_limit(self):
        item = passing(); item["limits"]["sharedWork"] = True
        with self.assertRaises(receipts.ReceiptError): receipts.validate_case(item)

    def test_unsupported_limit_is_rejected(self):
        item = passing(); item["limits"]["jobMillis"] = 90001
        with self.assertRaises(receipts.ReceiptError): receipts.validate_case(item)

    def test_host_observation_must_fit_declared_deadline(self):
        item = passing(); item["host"].update(observationElapsedMs=150001, deadlineMs=150000)
        self.assertEqual(receipts.validate_case(item), "FAIL")

    def test_all_explicit_not_run_returns_not_run(self):
        item = passing()
        item["application"].update(status="NOT_RUN", elapsedMs=None, workUnits=None,
                                   maxActiveOperationMs=None, maxCoordinatorAdvanceMs=None)
        item["host"].update(status="NOT_RUN", observationElapsedMs=None, exitCode=None, terminal=False)
        item["cleanup"].update(status="NOT_RUN", elapsedMs=None,
                                elapsedLimitations="case never launched", serverStopped=False)
        item["operation"].update(elapsedMs=None, elapsedLimitations="case never launched")
        self.assertEqual(receipts.validate_case(item), "NOT_RUN")

    def test_unrecorded_operation_time_requires_explanation(self):
        item = passing(); item["operation"].update(elapsedMs=None, elapsedLimitations=" ")
        with self.assertRaises(receipts.ReceiptError): receipts.validate_case(item)

    def test_boolean_measurement_is_rejected(self):
        item = passing(); item["application"]["workUnits"] = True
        with self.assertRaises(receipts.ReceiptError): receipts.validate_case(item)

    def test_provenance_extensions_survive_summary(self):
        item = passing(); item["provenance"] = {"source": "retained"}
        row = receipts.summarize_cases(["75"], [item])["rows"][0]
        self.assertEqual(row["receipt"]["provenance"], {"source": "retained"})
        self.assertEqual(json.loads(json.dumps(row))["status"], "PASS")

    def test_summary_owns_its_nested_receipt_snapshot(self):
        item = passing()
        row = receipts.summarize_cases(["75"], [item])["rows"][0]
        item["application"]["elapsedMs"] = 90221
        self.assertEqual(row["receipt"]["application"]["elapsedMs"], 82000)

    def test_explicit_not_run_receipt_does_not_complete_sequence(self):
        item = passing()
        item["application"]["status"] = item["host"]["status"] = item["cleanup"]["status"] = "NOT_RUN"
        summary = receipts.summarize_cases(["75"], [item])
        self.assertFalse(summary["complete"])
        self.assertEqual(summary["outcome"], "FAIL")


if __name__ == "__main__":
    unittest.main()
