"""Real Windows process-boundary tests for serial_runner; never run the Q30 matrix here."""
from __future__ import annotations

import json
import os
from pathlib import Path
import sys
import tempfile
import unittest

import serial_runner


SOURCE = "a" * 64
HEAD = "b" * 40
PLAN = "c" * 64
LIMITS = {"jobMillis": 90000, "sharedWork": 640, "activeOperationMillis": 5000}


WORKER = r'''import json, pathlib, subprocess, sys, time

receipt_path = pathlib.Path(sys.argv[1])
seed, source, head, plan, mode, timeline, child_pid = sys.argv[2:]

def event(label):
    if timeline != "-":
        with open(timeline, "a", encoding="ascii") as stream:
            stream.write(f"{seed} {label} {time.perf_counter_ns()}\n")
            stream.flush()

def receipt(app_elapsed=80000):
    value = {
        "schema": 1, "rootSeed": seed, "sourceIdentity": source,
        "baseHead": head, "planSha256": plan,
        "limits": {"jobMillis": 90000, "sharedWork": 640, "activeOperationMillis": 5000},
        "application": {"status": "PASS", "elapsedMs": app_elapsed, "workUnits": 600,
            "maxActiveOperationMs": 4000, "maxCoordinatorAdvanceMs": 4500},
        "host": {"status": "PASS", "observationElapsedMs": 12, "deadlineMs": 120000,
            "exitCode": 0, "terminal": True},
        "cleanup": {"status": "PASS", "elapsedMs": 250, "elapsedLimitations": None,
            "serverStopped": True, "ownedSurvivors": []},
        "operation": {"elapsedMs": app_elapsed, "elapsedLimitations": None},
        "sourceInputsUnchanged": True,
    }
    receipt_path.write_text(json.dumps(value), encoding="utf-8")

if mode == "missing":
    sys.exit(0)
if mode == "flood":
    sys.stderr.write("x" * (2 * 1024 * 1024))
    sys.stderr.flush()
    time.sleep(30)
    sys.exit(0)

receipt(90221 if mode == "over-cap" else 80000)
event("start")
if mode in ("spawn-child", "orphan-child"):
    child = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(60)"])
    pathlib.Path(child_pid).write_text(str(child.pid), encoding="ascii")
    if mode == "spawn-child":
        time.sleep(60)
    else:
        event("finish")
        sys.exit(0)
elif mode == "slow":
    time.sleep(30)
else:
    time.sleep(0.12)
event("finish")
'''


def _selected_python() -> str:
    raw = os.environ.get("Q30_SERIAL_RUNNER_PYTHON", sys.executable)
    return str(Path(raw).resolve(strict=True))


def _passing_receipt(seed: str, *, elapsed: int = 80000) -> dict:
    """Independent fixture; keep this separate from test_receipts.passing()."""
    return {
        "schema": 1, "rootSeed": seed, "sourceIdentity": SOURCE,
        "baseHead": HEAD, "planSha256": PLAN, "limits": dict(LIMITS),
        "application": {"status": "PASS", "elapsedMs": elapsed, "workUnits": 600,
            "maxActiveOperationMs": 4000, "maxCoordinatorAdvanceMs": 4500},
        "host": {"status": "PASS", "observationElapsedMs": 12, "deadlineMs": 120000,
            "exitCode": 0, "terminal": True},
        "cleanup": {"status": "PASS", "elapsedMs": 250, "elapsedLimitations": None,
            "serverStopped": True, "ownedSurvivors": []},
        "operation": {"elapsedMs": elapsed, "elapsedLimitations": None},
        "sourceInputsUnchanged": True,
    }


@unittest.skipUnless(os.name == "nt", "selected Windows CreateProcess/job-object path")
class SerialRunnerProcessTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.python = _selected_python()

    def setUp(self):
        self.base = Path(tempfile.mkdtemp(prefix="q30-serial-runner-test-")).resolve(strict=True)
        self.worker = self.base / "worker.py"
        self.worker.write_text(WORKER, encoding="utf-8", newline="\n")

    def tearDown(self):
        print(f"retained serial-runner test evidence: {self.base}", flush=True)

    def spec(self, seeds, modes, *, timeout_ms=5000, timeline=None):
        steps = []
        for seed, mode in zip(seeds, modes):
            args = [self.python, str(self.worker), serial_runner.TOKEN, seed,
                    SOURCE, HEAD, PLAN, mode,
                    str(timeline) if timeline else "-", str(self.base / ("child-" + seed + ".pid"))]
            steps.append({"rootSeed": seed, "argv": args, "outerTimeoutMs": timeout_ms})
        return {"sourceIdentity": SOURCE, "baseHead": HEAD, "planSha256": PLAN,
                "limits": dict(LIMITS), "steps": steps}

    def run_spec(self, spec, leaf):
        return serial_runner.run_serial(spec, self.base / leaf)

    def test_two_successful_steps_run_serially_through_selected_windows_boundary(self):
        timeline = self.base / "events.log"
        summary = self.run_spec(self.spec(["74", "75"], ["pass", "pass"], timeline=timeline), "serial-pass")
        self.assertEqual(summary["outcome"], "PASS")
        self.assertTrue(summary["complete"])
        self.assertEqual([row["status"] for row in summary["rows"]], ["PASS", "PASS"])
        events = [line.split() for line in timeline.read_text(encoding="ascii").splitlines()]
        self.assertEqual([(row[0], row[1]) for row in events],
                         [("74", "start"), ("74", "finish"), ("75", "start"), ("75", "finish")])
        ticks = [int(row[2]) for row in events]
        self.assertLessEqual(ticks[1], ticks[2])
        for step in summary["steps"]:
            self.assertEqual(step["process"]["executable"].lower(), self.python.lower())
            self.assertTrue(step["cleanup"]["cleanupVerified"])

    def test_90221_mislabeled_pass_fails_and_later_seed_is_not_run(self):
        summary = self.run_spec(self.spec(["74", "75"], ["over-cap", "pass"]), "over-cap")
        self.assertEqual(summary["outcome"], "FAIL")
        self.assertFalse(summary["complete"])
        self.assertEqual([row["status"] for row in summary["rows"]], ["FAIL", "NOT_RUN"])
        first = summary["rows"][0]["receipt"]
        self.assertEqual(first["application"]["status"], "PASS")
        self.assertEqual(first["application"]["elapsedMs"], 90221)
        self.assertEqual(first["host"]["status"], "PASS")
        self.assertEqual(summary["steps"][0]["runnerStatus"], "FAIL")

    def test_outer_timeout_preserves_app_metrics_and_cleans_owned_job_tree(self):
        summary = self.run_spec(self.spec(["74", "75"], ["spawn-child", "pass"], timeout_ms=500),
                                "outer-timeout")
        self.assertEqual(summary["outcome"], "FAIL")
        self.assertEqual([row["status"] for row in summary["rows"]], ["FAIL", "NOT_RUN"])
        receipt = summary["rows"][0]["receipt"]
        self.assertEqual(receipt["application"]["status"], "PASS")
        self.assertEqual(receipt["application"]["elapsedMs"], 80000)
        self.assertEqual(receipt["host"]["status"], "TIMEOUT")
        self.assertFalse(receipt["host"]["terminal"])
        self.assertEqual(receipt["cleanup"]["status"], "FAIL")
        child_pid_file = self.base / "child-74.pid"
        self.assertTrue(child_pid_file.is_file())
        step = summary["steps"][0]
        self.assertGreaterEqual(step["operationElapsedMs"], 500)
        self.assertTrue(step["cleanup"]["processSignaled"])
        self.assertEqual(step["cleanup"]["jobActiveProcesses"], 0)
        self.assertTrue(step["cleanup"]["cleanupVerified"])

    def test_root_exit_with_persistent_descendant_fails_then_cleans_job(self):
        summary = self.run_spec(self.spec(["74"], ["orphan-child"]), "orphan-child")
        self.assertEqual(summary["outcome"], "FAIL")
        self.assertIn("live/unverified job descendants", summary["steps"][0]["reason"])
        self.assertEqual(summary["steps"][0]["cleanup"]["jobActiveProcesses"], 0)
        self.assertTrue(summary["steps"][0]["cleanup"]["cleanupVerified"])

    def test_missing_receipt_is_failure_with_explicit_not_run_suffix(self):
        summary = self.run_spec(self.spec(["74", "75"], ["missing", "pass"]), "missing-receipt")
        self.assertEqual([row["status"] for row in summary["rows"]], ["FAIL", "NOT_RUN"])
        self.assertFalse(summary["complete"])
        self.assertIn("did not create receipt", summary["steps"][0]["reason"])

    def test_output_cap_fails_closed_and_keeps_bounded_log(self):
        summary = self.run_spec(self.spec(["74"], ["flood"], timeout_ms=5000), "bounded-log")
        step = summary["steps"][0]
        self.assertEqual(summary["outcome"], "FAIL")
        self.assertLessEqual(step["logs"]["stderr"]["bytes"], 1024 * 1024)
        self.assertTrue(step["logs"]["stderr"]["limitExceeded"])
        self.assertTrue(step["cleanup"]["cleanupVerified"])

    def test_existing_output_root_is_never_reused_or_overwritten(self):
        root = self.base / "occupied"
        root.mkdir()
        sentinel = root / "keep.bin"
        sentinel.write_bytes(b"preserve-existing-bytes")
        with self.assertRaises(serial_runner.RunnerError):
            serial_runner.run_serial(self.spec(["74"], ["pass"]), root)
        self.assertEqual(sentinel.read_bytes(), b"preserve-existing-bytes")
        self.assertEqual(list(root.iterdir()), [sentinel])

    def test_relative_executable_and_changed_caps_reject_before_output_creation(self):
        relative = self.spec(["74"], ["pass"])
        relative["steps"][0]["argv"][0] = "python.exe"
        target = self.base / "relative-rejected"
        with self.assertRaises(serial_runner.RunnerError):
            serial_runner.run_serial(relative, target)
        self.assertFalse(target.exists())

        changed = self.spec(["74"], ["pass"])
        changed["limits"]["sharedWork"] = 641
        target = self.base / "cap-rejected"
        with self.assertRaises(serial_runner.RunnerError):
            serial_runner.run_serial(changed, target)
        self.assertFalse(target.exists())

    def test_relative_output_root_is_rejected_before_normalization(self):
        previous = Path.cwd()
        try:
            os.chdir(self.base)
            target = self.base / "relative-root"
            with self.assertRaises(serial_runner.RunnerError):
                serial_runner.run_serial(self.spec(["74"], ["pass"]), Path("relative-root"))
            self.assertFalse(target.exists())
        finally:
            os.chdir(previous)


if __name__ == "__main__":
    unittest.main()
