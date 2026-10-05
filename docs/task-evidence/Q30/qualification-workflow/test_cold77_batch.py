"""Offline regression tests for the additive cold77 batch qualification glue.

The parent test driver supplies retained inputs through Q30_CASE_FIXTURE_JSON,
Q30_PLAN_JSON, and Q30_TEST_ROLES_JSON. These tests do not launch a browser, host,
process worker, or qualification batch.
"""
from __future__ import annotations

import contextlib
import copy
import hashlib
import importlib
import io
import json
import os
from pathlib import Path
import re
import sys
import tarfile
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
from unittest import mock


HERE = Path(__file__).resolve().parent
REQUIRED_ROLES = ("repo", "pointer", "matched_helper", "host_python", "deps")
SEED_PATTERN = re.compile(r"-?(0|[1-9][0-9]*)\Z")


def _load_json_env(name: str):
    """Read a JSON file path (or inline JSON value) from a required test variable."""
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"{name} must identify retained JSON test data")
    path = Path(value)
    if path.is_file():
        return json.loads(path.read_text(encoding="utf-8-sig"))
    return json.loads(value)


class Cold77BatchTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.roles = _load_json_env("Q30_TEST_ROLES_JSON")
        if not isinstance(cls.roles, dict):
            raise RuntimeError("Q30_TEST_ROLES_JSON must contain an object")
        missing = set(REQUIRED_ROLES) - set(cls.roles)
        if missing:
            raise RuntimeError(f"Q30_TEST_ROLES_JSON is missing roles: {sorted(missing)}")
        cls.role_paths = {key: Path(cls.roles[key]).resolve(strict=True) for key in REQUIRED_ROLES}
        cls.repo = cls.role_paths["repo"]
        cls.workflow = cls.repo / "docs/task-evidence/Q30/qualification-workflow"
        receipts_path = (cls.workflow / "receipts.py").resolve(strict=True)
        sys.path.insert(0, str(HERE))
        sys.path.insert(0, str(cls.workflow))

        existing = sys.modules.get("receipts")
        if existing is not None and Path(existing.__file__).resolve(strict=True) != receipts_path:
            del sys.modules["receipts"]
        cls.receipts = importlib.import_module("receipts")
        if Path(cls.receipts.__file__).resolve(strict=True) != receipts_path:
            raise RuntimeError("receipts import did not resolve to the maintained workflow owner")
        cls.batch = importlib.import_module("cold77_batch")
        cls.worker = importlib.import_module("cold77_case_worker")
        if Path(cls.batch.__file__).resolve(strict=True) != (HERE / "cold77_batch.py").resolve(strict=True):
            raise RuntimeError("cold77_batch import escaped the selected candidate directory")
        if Path(cls.worker.__file__).resolve(strict=True) != (HERE / "cold77_case_worker.py").resolve(strict=True):
            raise RuntimeError("cold77_case_worker import escaped the selected candidate directory")

        cls.plan = _load_json_env("Q30_PLAN_JSON")
        cls.fixtures = _load_json_env("Q30_CASE_FIXTURE_JSON")
        if not isinstance(cls.plan, dict) or not isinstance(cls.fixtures, dict):
            raise RuntimeError("plan and case fixtures must be JSON objects")
        if set(("binding", "timeout", "r5", "pass")) - set(cls.fixtures):
            raise RuntimeError("Q30_CASE_FIXTURE_JSON requires binding/timeout/r5/pass")
        for label in ("timeout", "r5", "pass"):
            if set(("report", "host", "checked")) - set(cls.fixtures[label]):
                raise RuntimeError(f"retained {label} fixture requires report/host/checked")
        cls.binding = cls.fixtures["binding"]
        cls.matched = cls.worker.imported(
            cls.role_paths["matched_helper"], "q30_cold77_recovery_matched",
            cls.binding["matchedHelperSha256"],
        )
        cls.variant = cls.matched.load_variant("current", cls.role_paths["pointer"])
        if cls.variant["plan"] != cls.plan:
            raise RuntimeError("Q30_PLAN_JSON differs from the matched retained plan")
        reader_path = cls.repo / "tests/qualification/q30-scale-acceptance/q30_scale_reader.py"
        reader_pin = next(row["sha256"] for row in cls.variant["snapshot"]["files"]
                          if row["path"] == "tests/qualification/q30-scale-acceptance/q30_scale_reader.py")
        cls.reader = cls.worker.imported(reader_path, "q30_cold77_recovery_reader", reader_pin)

    def _build_args(self):
        return SimpleNamespace(**self.role_paths)

    def _receipt(self, fixture_name, seed, *, inputs_unchanged=True, errors=None):
        fixture = self.fixtures[fixture_name]
        # The fixture files carry the actual application report, host record,
        # and strict-reader result. The worker operation clock is not part of
        # this fixture contract, so use the minimum valid placeholder while
        # preserving the retained app/host/cleanup clocks verbatim.
        return self.worker.receipt_from_records(
            seed,
            self.binding,
            fixture["report"],
            fixture["host"],
            fixture["checked"],
            0,
            0,
            inputs_unchanged,
            [] if errors is None else list(errors),
        )

    def _passing_rows(self):
        seeds = self.plan["coldOrder"]
        rows = []
        for index, seed in enumerate(seeds):
            receipt = self._receipt("pass", seed)
            receipt.setdefault("provenance", {})["publishedPackages"] = 20 + index % 21
            rows.append({"seed": seed, "status": "PASS", "receipt": receipt})
        return rows

    def _retained_report_bytes(self, fixture_name):
        if fixture_name == "timeout":
            archive = self.repo / "docs/task-evidence/Q30/epoch15-cold77-timeout-seed75/epoch15-cold77-timeout-seed75.tar.gz"
            member = "cold-batches/cold-rest/032-seed75/app-report.json"
            with tarfile.open(archive, "r:gz") as packet:
                stream = packet.extractfile(member)
                if stream is None:
                    self.fail("retained root75 timeout report is absent from its packet")
                raw = stream.read()
        elif fixture_name == "r5":
            path = self.repo / "docs/task-evidence/Q30/epoch15-root75-offline-diagnostic/metrics-r5/actual-window-1/cold-75.report.json"
            raw = path.read_bytes()
        else:
            self.fail("recovery case must use a retained timeout or R5 report")
        self.assertEqual(json.loads(raw.decode("utf-8")), self.fixtures[fixture_name]["report"])
        return raw

    def _historical_timeout_context(self, destination):
        """Load the timeout's own retained D37 plan and exact bound inputs."""
        archive = self.repo / "docs/task-evidence/Q30/epoch15-cold77-timeout-seed75/epoch15-cold77-timeout-seed75.tar.gz"
        packet_path = self.repo / "docs/task-evidence/Q30/epoch15-cold77-timeout-seed75/packet-manifest.json"
        packet = json.loads(packet_path.read_text(encoding="utf-8"))
        with tarfile.open(archive, "r:gz") as packet_archive:
            plan_raw = packet_archive.extractfile("task/frozen-scale-plan.json").read()
            acceptance_portable = packet_archive.extractfile("task/continued-acceptance-plan.json").read()
            snapshot_portable = packet_archive.extractfile("task/source-snapshot.json").read()

        # The evidence packet stores normalized LF copies while retaining the
        # original byte hashes. Reconstruct and verify those original bytes.
        self.assertNotIn(b"\r", acceptance_portable)
        self.assertNotIn(b"\r", snapshot_portable)
        acceptance_raw = acceptance_portable.replace(b"\n", b"\r\n")
        snapshot_raw = snapshot_portable.replace(b"\n", b"\r\n")
        artifacts = {row["path"]: row for row in packet["artifacts"]}
        plan = json.loads(plan_raw.decode("utf-8-sig"))
        snapshot = json.loads(snapshot_raw.decode("utf-8-sig"))
        self.assertEqual(hashlib.sha256(plan_raw).hexdigest(),
                         artifacts["task/frozen-scale-plan.json"]["rawSha256"])
        self.assertEqual(hashlib.sha256(acceptance_raw).hexdigest(),
                         packet["plan"]["continuedPlanSha256"])
        self.assertEqual(hashlib.sha256(acceptance_raw).hexdigest(),
                         artifacts["task/continued-acceptance-plan.json"]["rawSha256"])
        self.assertEqual(hashlib.sha256(snapshot_raw).hexdigest(),
                         packet["source"]["snapshotSha256"])
        self.assertEqual(snapshot["files"], plan["sourceFiles"])
        self.assertEqual(len(snapshot["files"]), packet["source"]["inputs"])
        self.assertEqual(snapshot["sourceIdentity"], plan["sourceIdentity"])
        self.assertEqual(snapshot["baseHead"], plan["baseHead"])
        self.assertEqual(plan["sourceIdentity"], packet["source"]["sourceIdentity"])
        self.assertEqual(plan["baseHead"], packet["source"]["baseHead"])
        self.assertEqual(hashlib.sha256(acceptance_raw).hexdigest(),
                         plan["acceptancePlanSha256"])

        acceptance_path = destination / "continued-acceptance-plan.json"
        acceptance_path.write_bytes(acceptance_raw)
        variant = {"plan": plan, "planRaw": plan_raw.decode("utf-8-sig"),
                   "paths": {"acceptance": acceptance_path}}
        binding = {"sourceIdentity": plan["sourceIdentity"],
                   "baseHead": plan["baseHead"],
                   "planSha256": hashlib.sha256(plan_raw).hexdigest()}
        return variant, binding

    def _recover_missing_report_receipt(self, fixture_name, *, variant=None,
                                        reader=None, binding=None):
        raw = self._retained_report_bytes(fixture_name)
        variant = self.variant if variant is None else variant
        reader = self.reader if reader is None else reader
        binding = self.binding if binding is None else binding
        cold_order = variant["plan"]["coldOrder"]
        seeds = cold_order[cold_order.index("75"):]
        spec = {"steps": [{"rootSeed": seed} for seed in seeds]}
        with tempfile.TemporaryDirectory(prefix="cold77-recover-offline-") as temp_root:
            batch_root = Path(temp_root) / "batch"
            case = batch_root / "sequence/cases/001-seed-75"
            report_path = case / "host-output/cases/001-cold-75.report.json"
            report_path.parent.mkdir(parents=True)
            report_path.write_bytes(raw)
            recovered = self.batch.recover_rows(
                batch_root, spec, variant=variant, reader=reader, binding=binding,
            )
            self.assertEqual(report_path.read_bytes(), raw)
            return recovered

    def test_build_spec_preserves_frozen_order_and_default_is_audit_only(self):
        seeds = self.plan["coldOrder"]
        self.assertEqual(len(seeds), 77)
        self.assertEqual(len(set(seeds)), 77)
        self.assertEqual(sum(len(row["candidates"]) for row in self.plan["cases"]), 308)
        for seed in seeds:
            self.assertIs(type(seed), str)
            self.assertRegex(seed, SEED_PATTERN)
            numeric = int(seed)
            self.assertGreaterEqual(numeric, -(2**63))
            self.assertLessEqual(numeric, 2**63 - 1)
            self.assertEqual(str(numeric), seed)

        spec = self.batch.build_spec(self._build_args(), self.binding, self.plan)
        steps = spec["steps"]
        self.assertEqual([step["rootSeed"] for step in steps], seeds)
        self.assertEqual(len(steps), 77)
        for step in steps:
            self.assertEqual(step["outerTimeoutMs"], 180000)
            self.assertEqual(step["argv"].count("{receipt}"), 1)
            self.assertEqual(step["argv"][-1], "{receipt}")

        argv = []
        for option, role in (("--repo", "repo"), ("--pointer", "pointer"),
                             ("--matched-helper", "matched_helper"),
                             ("--host-python", "host_python"), ("--deps", "deps")):
            argv.extend((option, str(self.role_paths[role])))
        manifest_sha = "a" * 64
        with mock.patch.object(self.batch, "verify_candidate",
                                return_value=(manifest_sha, self.binding, {"plan": self.plan}, {})), \
             mock.patch.object(self.batch, "controlled_launch", side_effect=AssertionError("default launched")), \
             mock.patch.object(self.batch, "owned_batch", side_effect=AssertionError("default ran owned batch")), \
             contextlib.redirect_stdout(io.StringIO()) as stdout:
            exit_code = self.batch.main(argv)
        self.assertEqual(exit_code, 0)
        default_result = json.loads(stdout.getvalue())
        self.assertEqual(default_result["status"], "PASS_OFFLINE_INPUT_AUDIT")
        self.assertIs(default_result["browserLaunched"], False)
        self.assertEqual(default_result["casesLaunched"], 0)
        self.assertEqual(default_result["spec"], spec)

    def test_verify_candidate_uses_one_validated_manifest_binding(self):
        manifest = {
            "schema": 2, "limits": copy.deepcopy(self.batch.LIMITS),
            "windowMs": self.batch.WINDOW_MS,
            "inputBinding": copy.deepcopy(self.binding),
            "externalMatchedHelperSha256": self.binding["matchedHelperSha256"],
            "sources": {},
        }
        variant = {"plan": self.plan}
        with mock.patch.object(self.batch, "load_workflow_manifest",
                               return_value=manifest), \
             mock.patch.object(self.batch, "audit_inputs",
                               return_value=(self.binding, variant, object(), object())), \
             mock.patch.object(self.batch, "sha", return_value="a" * 64) as digest:
            candidate_sha, binding, observed_variant, observed_manifest = \
                self.batch.verify_candidate(self._build_args())
        self.assertEqual(candidate_sha, "a" * 64)
        self.assertEqual(binding, self.binding)
        self.assertIs(observed_variant, variant)
        self.assertIs(observed_manifest, manifest)
        digest.assert_called_once()

    def test_window_math_is_exactly_15120000_milliseconds(self):
        self.assertEqual(self.batch.STEP_MS, 180000)
        self.assertEqual(self.batch.CLEANUP_MS, 15000)
        self.assertEqual(self.batch.FINAL_MS, 60000)
        self.assertEqual(self.batch.RELEASE_MS, 30000)
        self.assertEqual(self.batch.OPERATION_MS, 15075000)
        self.assertEqual(self.batch.WINDOW_MS, 15120000)

    def test_grant_requires_fresh_exact_bindings_and_full_reserve(self):
        manifest_sha = "a" * 64
        start = datetime(2026, 10, 4, 12, 0, 0, tzinfo=timezone.utc)
        end = start + timedelta(milliseconds=self.batch.WINDOW_MS)
        grant = {
            "schema": 1,
            "kind": "Q30_COLD77_SERIAL_QUALIFICATION",
            "parentReference": "offline-regression-parent-grant",
            "candidateManifestSha256": manifest_sha,
            "sourceIdentity": self.binding["sourceIdentity"],
            "planSha256": self.binding["planSha256"],
            "limits": copy.deepcopy(self.batch.LIMITS),
            "rootCount": 77,
            "notBeforeUtc": start.isoformat().replace("+00:00", "Z"),
            "windowEndUtc": end.isoformat().replace("+00:00", "Z"),
        }
        self.assertEqual(
            self.batch.validate_grant(grant, manifest_sha, self.binding, start),
            self.batch.OPERATION_MS,
        )

        mutations = {
            "missing source identity": lambda row: row.pop("sourceIdentity"),
            "candidate manifest mismatch": lambda row: row.update(candidateManifestSha256="b" * 64),
            "source identity mismatch": lambda row: row.update(sourceIdentity="b" * 64),
            "plan mismatch": lambda row: row.update(planSha256="b" * 64),
            "limits mismatch": lambda row: row["limits"].update(sharedWork=row["limits"]["sharedWork"] + 1),
            "root count mismatch": lambda row: row.update(rootCount=76),
        }
        for label, mutate in mutations.items():
            with self.subTest(binding=label):
                bad = copy.deepcopy(grant)
                mutate(bad)
                with self.assertRaises(ValueError):
                    self.batch.validate_grant(bad, manifest_sha, self.binding, start)

        with self.assertRaises(ValueError):
            self.batch.validate_grant(None, manifest_sha, self.binding, start)
        with self.assertRaises(ValueError):
            self.batch.validate_grant(grant, manifest_sha, self.binding, end)
        too_late = start + timedelta(milliseconds=self.batch.FINAL_MS + 1)
        with self.assertRaises(ValueError):
            self.batch.validate_grant(grant, manifest_sha, self.binding, too_late)

    def test_receipts_retain_original_timeout_and_reject_diagnostic_host_failure(self):
        timeout = self._receipt("timeout", "75")
        self.assertEqual(timeout["application"]["status"], "TIMEOUT")
        self.assertEqual(timeout["application"]["elapsedMs"], 90221)
        self.assertEqual(timeout["host"]["status"], "PASS")
        self.assertEqual(timeout["cleanup"]["status"], "PASS")
        self.assertEqual(self.receipts.validate_case(timeout), "FAIL")

        r5 = self._receipt("r5", "75")
        self.assertEqual(r5["application"]["status"], "PASS")
        self.assertEqual(self.fixtures["r5"]["checked"]["host"]["status"], "HOST_FAIL")
        self.assertEqual(r5["host"]["status"], "FAIL")
        self.assertEqual(self.receipts.validate_case(r5), "FAIL")

    def test_changed_source_or_worker_errors_never_qualify(self):
        seed = self.plan["coldOrder"][0]
        changed = self._receipt("pass", seed, inputs_unchanged=False)
        self.assertEqual(self.receipts.validate_case(changed), "FAIL")

        errored = self._receipt("pass", seed, errors=["retained worker error"])
        self.assertEqual(errored["provenance"]["workerErrors"], ["retained worker error"])
        self.assertNotEqual(self.receipts.validate_case(errored), "PASS")

        rows = self._passing_rows()
        rows[0]["receipt"] = changed
        changed_result = self.batch.full_result({"outcome": "PASS", "rows": rows}, self.plan)
        self.assertFalse(changed_result["qualified"])

        rows = self._passing_rows()
        rows[0]["receipt"] = errored
        error_result = self.batch.full_result({"outcome": "PASS", "rows": rows}, self.plan)
        self.assertFalse(error_result["qualified"])

    def test_full_result_requires_77_ordered_passes_and_every_size_20_through_40(self):
        rows = self._passing_rows()
        summary = {"outcome": "PASS", "rows": rows}
        result = self.batch.full_result(summary, self.plan)
        self.assertTrue(result["qualified"])
        self.assertTrue(result["complete"])
        self.assertEqual(result["publishedPackageSizes"], list(range(20, 41)))
        self.assertEqual(len(result["rows"]), 77)
        self.assertTrue(all(row["status"] == "PASS" for row in result["rows"]))

        omitted = self.batch.full_result({"outcome": "FAIL", "rows": rows[:-1]}, self.plan)
        self.assertFalse(omitted["qualified"])
        self.assertFalse(omitted["complete"])
        self.assertEqual(omitted["rows"][-1]["status"], "NOT_RUN")

        reordered_rows = copy.deepcopy(rows)
        reordered_rows[0], reordered_rows[1] = reordered_rows[1], reordered_rows[0]
        try:
            reordered = self.batch.full_result({"outcome": "PASS", "rows": reordered_rows}, self.plan)
        except self.receipts.ReceiptError:
            pass
        else:
            self.assertFalse(reordered["qualified"])

        missing_size_rows = copy.deepcopy(rows)
        for row in missing_size_rows:
            if row["receipt"]["provenance"]["publishedPackages"] == 40:
                row["receipt"]["provenance"]["publishedPackages"] = 39
        missing_size = self.batch.full_result({"outcome": "PASS", "rows": missing_size_rows}, self.plan)
        self.assertFalse(missing_size["qualified"])
        self.assertNotEqual(missing_size["publishedPackageSizes"], list(range(20, 41)))

        disagreeing_summary = self.batch.full_result({"outcome": "FAIL", "rows": rows}, self.plan)
        self.assertFalse(disagreeing_summary["qualified"])

    def test_interrupted_report_recovery_keeps_app_metrics_but_fails_and_preserves_bytes(self):
        with tempfile.TemporaryDirectory(prefix="cold77-historical-timeout-") as destination:
            historical_variant, historical_binding = self._historical_timeout_context(Path(destination))
            timeout = self._recover_missing_report_receipt(
                "timeout", variant=historical_variant, binding=historical_binding)
            timeout_case = timeout["rows"][0]["receipt"]
            self.assertEqual(timeout["outcome"], "FAIL")
            self.assertEqual(timeout_case["application"]["status"], "TIMEOUT")
            self.assertEqual(timeout_case["application"]["elapsedMs"], 90221)
            self.assertEqual(timeout_case["host"]["status"], "FAIL")
            self.assertEqual(timeout_case["sourceInputsUnchanged"], False)
            self.assertEqual(timeout_case["sourceIdentity"], historical_binding["sourceIdentity"])
            self.assertTrue(all(row["status"] == "NOT_RUN" for row in timeout["rows"][1:]))

        # The archived D37 timeout must fail when re-evaluated under the current
        # C621 binding. Preserve the report's app clock without qualifying it.
        current_epoch_timeout = self._recover_missing_report_receipt("timeout")
        current_timeout_case = current_epoch_timeout["rows"][0]["receipt"]
        self.assertEqual(current_epoch_timeout["outcome"], "FAIL")
        self.assertEqual(current_timeout_case["application"]["status"], "FAIL")
        self.assertEqual(current_timeout_case["application"]["elapsedMs"], 90221)
        self.assertEqual(current_timeout_case["host"]["status"], "FAIL")
        self.assertFalse(current_timeout_case["sourceInputsUnchanged"])
        self.assertEqual(current_timeout_case["provenance"]["binding"], self.binding)
        self.assertEqual(current_timeout_case["sourceIdentity"], self.binding["sourceIdentity"])
        self.assertNotEqual(self.fixtures["timeout"]["report"]["sourceIdentity"],
                            self.binding["sourceIdentity"])
        self.assertTrue(all(row["status"] == "NOT_RUN"
                            for row in current_epoch_timeout["rows"][1:]))

        with tempfile.TemporaryDirectory(prefix="cold77-historical-r5-") as destination:
            historical_variant, historical_binding = self._historical_timeout_context(Path(destination))
            r5 = self._recover_missing_report_receipt(
                "r5", variant=historical_variant, binding=historical_binding)
            r5_case = r5["rows"][0]["receipt"]
            self.assertEqual(r5["outcome"], "FAIL")
            self.assertEqual(r5_case["application"]["status"], "PASS")
            self.assertEqual(r5_case["application"]["elapsedMs"], 77899)
            self.assertEqual(r5_case["host"]["status"], "FAIL")
            self.assertFalse(r5_case["sourceInputsUnchanged"])
            self.assertTrue(all(row["status"] == "NOT_RUN" for row in r5["rows"][1:]))


if __name__ == "__main__":
    unittest.main()
