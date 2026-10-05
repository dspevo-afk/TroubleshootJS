"""Focused checks for the maintained Q30 phase entrypoint's retained gates."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import qualify
import windows_process_identity


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _receipt(seed, *, elapsed=1000, identity="a" * 64, base="b" * 40, plan="c" * 64):
    return {
        "schema": 1,
        "rootSeed": seed,
        "sourceIdentity": identity,
        "baseHead": base,
        "planSha256": plan,
        "limits": dict(qualify.LIMITS),
        "application": {"status": "PASS", "elapsedMs": elapsed, "workUnits": 2,
                         "maxActiveOperationMs": 10, "maxCoordinatorAdvanceMs": 10},
        "host": {"status": "PASS", "observationElapsedMs": elapsed + 10,
                 "deadlineMs": 150000, "exitCode": 0, "terminal": True},
        "cleanup": {"status": "PASS", "elapsedMs": 1, "elapsedLimitations": None,
                    "serverStopped": True, "ownedSurvivors": []},
        "operation": {"elapsedMs": elapsed + 20, "elapsedLimitations": None},
        "sourceInputsUnchanged": True,
        "serialRunner": {"status": "PASS"},
    }


def _batch(*, elapsed=1000):
    seeds = [str(value) for value in range(77)]
    spec = {"sourceIdentity": "a" * 64, "baseHead": "b" * 40,
            "planSha256": "c" * 64, "limits": dict(qualify.LIMITS),
            "steps": [{"rootSeed": seed} for seed in seeds]}
    result = {"status": "PASS_COLD77", "complete": True, "qualified": True,
              "publishedPackageSizes": list(range(20, 41)),
              "rows": [{"seed": seed, "status": "PASS",
                        "receipt": _receipt(seed, elapsed=elapsed)} for seed in seeds]}
    return spec, result


class SourceSnapshotTests(unittest.TestCase):
    def test_exact_source_bytes_pass(self):
        with tempfile.TemporaryDirectory(prefix="q30-qualify-test-") as root:
            repository = Path(root)
            rows = []
            for name, content in (("src/A.java", b"class A {}\n"), ("tests/check.py", b"assert True\n")):
                path = repository / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
                rows.append({"path": name, "sha256": _sha(content), "size": len(content)})
            binding = {"baseHead": "b" * 40, "sourceIdentity": "a" * 64, "sourceFiles": 2}
            snapshot = {"schema": 1, **binding, "files": rows}
            self.assertEqual(qualify._verify_source_files(repository, snapshot, binding)["fileCount"], 2)

    def test_changed_bytes_and_path_traversal_fail(self):
        with tempfile.TemporaryDirectory(prefix="q30-qualify-test-") as root:
            repository = Path(root)
            path = repository / "src" / "A.java"
            path.parent.mkdir()
            path.write_bytes(b"changed")
            binding = {"baseHead": "b" * 40, "sourceIdentity": "a" * 64, "sourceFiles": 1}
            snapshot = {"schema": 1, **binding,
                        "files": [{"path": "src/A.java", "sha256": _sha(b"original"), "size": 8}]}
            with self.assertRaises(qualify.QualificationError):
                qualify._verify_source_files(repository, snapshot, binding)
            snapshot["files"] = [{"path": "../A.java", "sha256": _sha(b"x"), "size": 1}]
            with self.assertRaises(qualify.QualificationError):
                qualify._verify_source_files(repository, snapshot, binding)

    def test_duplicate_json_keys_fail(self):
        with self.assertRaises(qualify.QualificationError):
            qualify._json(b'{"phase":"audit-retained","phase":"release"}', "test")


class ColdCohortTests(unittest.TestCase):
    def test_77_receipts_pass_under_frozen_caps(self):
        spec, batch = _batch(elapsed=85003)
        maxima = qualify._validate_batch(spec, {
            "sourceIdentity": "a" * 64, "baseHead": "b" * 40, "planSha256": "c" * 64,
        }, batch)
        self.assertEqual(maxima["elapsedMs"], 85003)
        self.assertEqual(len(batch["rows"]), 77)

    def test_over_budget_receipt_fails_without_relaxing_cap(self):
        spec, batch = _batch(elapsed=90001)
        with self.assertRaises(qualify.QualificationError):
            qualify._validate_batch(spec, {
                "sourceIdentity": "a" * 64, "baseHead": "b" * 40, "planSha256": "c" * 64,
            }, batch)


class PhaseProvenanceTests(unittest.TestCase):
    def _setup(self, parent: Path, *, include_sources=True):
        temp_root = parent / "owned-temp"
        temp_root.mkdir()
        manifest_path = temp_root / "qualification-manifest.json"
        binding = {"baseHead": "b" * 40, "observedHead": "d" * 40,
                   "sourceIdentity": "a" * 64, "sourceFiles": 1355,
                   "reuseBoundary": "exact raw1355 source files; new docs workflow owners excluded"}
        names = qualify._workflow_names("audit-retained")
        sources = {name: _sha((Path(qualify.__file__).parent / name).read_bytes()) for name in names}
        current = windows_process_identity.query_process(os.getpid())
        if current.get("queryStatus") != "PRESENT" or not current.get("identity", {}).get("executable"):
            raise RuntimeError("native Python process image unavailable for phase-gate fixture")
        manifest = {"sourceBinding": binding,
                    "tools": {"python": current["identity"]["executable"]},
                    "tempRoot": str(temp_root)}
        if include_sources:
            manifest["workflowSources"] = sources
        raw = json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode("utf-8")
        manifest_path.write_bytes(raw)
        return manifest, raw, manifest_path, temp_root

    @staticmethod
    def _host_result():
        return {
            "schema": 1, "status": "PASS", "handlesClosed": True,
            "process": {"pid": 4321, "birthPrecision": "NATIVE_100NS"},
            "operation": {"exitCode": 0},
            "cleanup": {"cleanupVerified": True, "jobActiveProcesses": 0, "liveDrainers": 0},
            "logs": {name: {"overflow": False, "errors": [], "readerAlive": False,
                            "streamClosed": True} for name in ("stdout", "stderr")},
        }

    def test_worker_epoch_mismatches_fail_the_phase_gate(self):
        with tempfile.TemporaryDirectory(prefix="q30-phase-gate-") as root:
            parent = Path(root)
            manifest, raw, manifest_path, temp_root = self._setup(parent)
            phase = "audit-retained"
            hashes = manifest["workflowSources"]
            good_worker = {"schema": 1, "phase": phase, "status": "PASS",
                           "manifestSha256": _sha(raw), "sourceBinding": manifest["sourceBinding"],
                           "workflowSourcesBefore": hashes, "workflowSourcesAfter": hashes}
            changes = {
                "manifestSha256": "0" * 64,
                "phase": "release",
                "sourceBinding": {**manifest["sourceBinding"], "observedHead": "e" * 40},
                "workflowSourcesBefore": {**hashes, "qualify.py": "0" * 64},
                "workflowSourcesAfter": {**hashes, "receipts.py": "0" * 64},
            }

            def invoke(worker_result, leaf):
                output = temp_root / leaf

                def fake_run(argv, output_root, timeout):
                    output_root.mkdir()
                    (output_root / "worker-result.json").write_text(
                        json.dumps(worker_result, sort_keys=True), encoding="utf-8")
                    return self._host_result()

                with patch.object(qualify, "load_manifest",
                                  return_value=(manifest, raw, manifest_path, parent, temp_root)), \
                     patch("serial_runner.run_command", side_effect=fake_run):
                    return qualify.run_phase(manifest_path, phase, output)

            accepted = invoke(dict(good_worker), "valid")
            self.assertEqual(accepted["status"], "PASS")
            self.assertTrue(accepted["workerIdentityMatches"])

            for index, (field, wrong) in enumerate(changes.items()):
                with self.subTest(field=field):
                    tampered = dict(good_worker)
                    tampered[field] = wrong
                    result = invoke(tampered, f"mismatch-{index}")
                    self.assertEqual(result["status"], "FAIL")
                    self.assertFalse(result["workerIdentityMatches"])

    def test_missing_workflow_pins_refuse_before_runner_launch(self):
        with tempfile.TemporaryDirectory(prefix="q30-phase-pin-") as root:
            parent = Path(root)
            manifest, raw, manifest_path, temp_root = self._setup(parent, include_sources=False)
            output = temp_root / "must-not-launch"
            with patch.object(qualify, "load_manifest",
                              return_value=(manifest, raw, manifest_path, parent, temp_root)), \
                 patch("serial_runner.run_command") as run_command:
                with self.assertRaises(qualify.QualificationError):
                    qualify.run_phase(manifest_path, "audit-retained", output)
                run_command.assert_not_called()
            self.assertFalse(output.exists())

    def test_out_of_order_or_missing_root_fails(self):
        spec, batch = _batch()
        batch["rows"][0], batch["rows"][1] = batch["rows"][1], batch["rows"][0]
        with self.assertRaises(qualify.QualificationError):
            qualify._validate_batch(spec, {
                "sourceIdentity": "a" * 64, "baseHead": "b" * 40, "planSha256": "c" * 64,
            }, batch)


if __name__ == "__main__":
    unittest.main(verbosity=2)
