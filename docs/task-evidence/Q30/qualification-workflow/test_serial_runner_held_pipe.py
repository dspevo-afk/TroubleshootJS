"""Draft Windows canaries for shared cleanup/drainer deadlines; do not run before review."""
from __future__ import annotations

import json
import os
from pathlib import Path
import sys
import tempfile
import threading
import time
import unittest
from unittest import mock

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "qualification-workflow"))
sys.path.insert(0, str(HERE))
import serial_runner

SOURCE, HEAD, PLAN = "a" * 64, "b" * 40, "c" * 64
LIMITS = {"jobMillis": 90000, "sharedWork": 640, "activeOperationMillis": 5000}
PYTHON = str(Path(os.environ.get("Q30_SERIAL_RUNNER_PYTHON", sys.executable)).resolve(strict=True))

WORKER = r'''import json,pathlib,sys
path=pathlib.Path(sys.argv[1]); seed=sys.argv[2]
value={"schema":1,"rootSeed":seed,"sourceIdentity":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
"baseHead":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","planSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
"limits":{"jobMillis":90000,"sharedWork":640,"activeOperationMillis":5000},
"application":{"status":"PASS","elapsedMs":81000,"workUnits":620,"maxActiveOperationMs":4000,"maxCoordinatorAdvanceMs":4500},
"host":{"status":"PASS","observationElapsedMs":100,"deadlineMs":150000,"exitCode":0,"terminal":True},
"cleanup":{"status":"PASS","elapsedMs":100,"elapsedLimitations":None,"serverStopped":True,"ownedSurvivors":[]},
"operation":{"elapsedMs":81000,"elapsedLimitations":None},"sourceInputsUnchanged":True}
path.write_text(json.dumps(value),encoding="utf-8")
print("held-pipe-test-output",flush=True)
'''


class GateStream:
    """Test-only writer stall after a real Windows worker has emitted stdout."""
    def __init__(self, stream, entered, release):
        self.stream, self.entered, self.release = stream, entered, release

    def write(self, data):
        self.entered.set()
        self.release.wait()
        return self.stream.write(data)

    def flush(self):
        return self.stream.flush()

    def close(self):
        return self.stream.close()


@unittest.skipUnless(os.name == "nt", "selected Windows CreateProcess/job-object boundary")
class HeldPipeCleanupTests(unittest.TestCase):
    def setUp(self):
        self.evidence = Path(tempfile.mkdtemp(prefix="q30-held-drainer-review-"))
        (self.evidence / "worker.py").write_text(WORKER, encoding="utf-8", newline="\n")

    def tearDown(self):
        print("retained held-drainer test evidence: " + str(self.evidence), flush=True)

    def spec(self):
        steps = []
        for seed in ("75", "76"):
            steps.append({"rootSeed": seed, "argv": [PYTHON, "-B", str(self.evidence / "worker.py"),
                serial_runner.TOKEN, seed], "outerTimeoutMs": 5000})
        return {"sourceIdentity": SOURCE, "baseHead": HEAD, "planSha256": PLAN,
                "limits": dict(LIMITS), "steps": steps}

    def test_normal_two_step_run_finishes_drains_and_remains_serial(self):
        summary = serial_runner.run_serial(self.spec(), self.evidence / "ordinary")
        self.assertEqual(summary["outcome"], "PASS")
        self.assertEqual([row["status"] for row in summary["rows"]], ["PASS", "PASS"])
        for step in summary["steps"]:
            self.assertTrue(step["cleanup"]["cleanupVerified"])
            self.assertTrue(step["cleanup"]["drainsComplete"])
            self.assertFalse(step["logs"]["stdout"]["readerAlive"])
            self.assertTrue(step["logs"]["stdout"]["streamClosed"])

    def test_live_output_drainer_fails_within_shared_cleanup_and_marks_next_not_run(self):
        entered, release = threading.Event(), threading.Event()
        base_drain = serial_runner._Drain
        made = []

        class HeldDrain(base_drain):
            def __init__(self, fd, stream):
                if not made:
                    stream = GateStream(stream, entered, release)
                super().__init__(fd, stream)
                made.append(self)

        old_cleanup, old_post = serial_runner.CLEANUP_MS, serial_runner.POST_CLOSE_MS
        serial_runner.CLEANUP_MS, serial_runner.POST_CLOSE_MS = 750, 750
        started = time.monotonic()
        try:
            with mock.patch.object(serial_runner, "_Drain", HeldDrain):
                summary = serial_runner.run_serial(self.spec(), self.evidence / "sequence")
            elapsed = time.monotonic() - started
            self.assertTrue(entered.wait(1), "worker stdout did not reach the held writer")
            self.assertLess(elapsed, 3.0, "shared cleanup/drainer deadline was exceeded")
            self.assertEqual(summary["outcome"], "FAIL")
            self.assertEqual([row["status"] for row in summary["rows"]], ["FAIL", "NOT_RUN"])
            first = summary["rows"][0]["receipt"]
            self.assertEqual(first["application"]["status"], "PASS")
            self.assertEqual(first["application"]["elapsedMs"], 81000)
            step = summary["steps"][0]
            self.assertTrue(step["cleanup"]["processSignaled"])
            self.assertEqual(step["cleanup"]["jobActiveProcesses"], 0)
            self.assertFalse(step["cleanup"]["drainsComplete"])
            self.assertGreater(step["cleanup"]["liveDrainers"], 0)
            self.assertFalse(step["cleanup"]["cleanupVerified"])
            self.assertTrue(step["logs"]["stdout"]["readerAlive"])
            self.assertFalse(step["logs"]["stdout"]["streamClosed"])
        finally:
            release.set()
            for drain in made:
                if drain.thread.ident is not None:
                    drain.thread.join(timeout=2)
            serial_runner.CLEANUP_MS, serial_runner.POST_CLOSE_MS = old_cleanup, old_post
        self.assertTrue(made[0].streamClosed, "drainer must close its own file after its final write")
        self.assertFalse(made[0].thread.is_alive())

    def test_parent_owned_duplicate_writer_holds_real_pipe_until_shared_deadline(self):
        import ctypes
        from ctypes import wintypes

        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.GetCurrentProcess.restype = wintypes.HANDLE
        kernel.DuplicateHandle.argtypes = [wintypes.HANDLE, wintypes.HANDLE, wintypes.HANDLE,
                                           ctypes.POINTER(wintypes.HANDLE), wintypes.DWORD,
                                           wintypes.BOOL, wintypes.DWORD]
        kernel.DuplicateHandle.restype = wintypes.BOOL
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel.CloseHandle.restype = wintypes.BOOL
        parent = kernel.GetCurrentProcess()
        writer_handles = []
        drains = []
        base_drain = serial_runner._Drain

        class TrackingDrain(base_drain):
            def __init__(self, fd, stream):
                super().__init__(fd, stream)
                drains.append(self)

        create_process = serial_runner._winapi.CreateProcess

        def hold_stdout_writer(*args):
            startup = args[-1]
            duplicate = wintypes.HANDLE()
            ok = kernel.DuplicateHandle(parent, wintypes.HANDLE(startup.hStdOutput), parent,
                ctypes.byref(duplicate), 0, False, 2)  # DUPLICATE_SAME_ACCESS
            if not ok:
                raise ctypes.WinError(ctypes.get_last_error())
            writer_handles.append(duplicate)
            return create_process(*args)

        old_cleanup, old_post = serial_runner.CLEANUP_MS, serial_runner.POST_CLOSE_MS
        serial_runner.CLEANUP_MS, serial_runner.POST_CLOSE_MS = 750, 750
        started = time.monotonic()
        try:
            with mock.patch.object(serial_runner, "_Drain", TrackingDrain), \
                 mock.patch.object(serial_runner._winapi, "CreateProcess", new=hold_stdout_writer):
                summary = serial_runner.run_serial(self.spec(), self.evidence / "real-held-pipe")
            elapsed = time.monotonic() - started
            self.assertEqual(len(writer_handles), 1)
            self.assertLess(elapsed, 3.0, "held pipe exceeded shared cleanup deadline")
            self.assertEqual(summary["outcome"], "FAIL")
            self.assertEqual([row["status"] for row in summary["rows"]], ["FAIL", "NOT_RUN"])
            step = summary["steps"][0]
            self.assertTrue(step["cleanup"]["processSignaled"])
            self.assertEqual(step["cleanup"]["jobActiveProcesses"], 0)
            self.assertFalse(step["cleanup"]["drainsComplete"])
            self.assertGreater(step["cleanup"]["liveDrainers"], 0)
            self.assertFalse(step["cleanup"]["cleanupVerified"])
            self.assertTrue(step["logs"]["stdout"]["readerAlive"])
            self.assertFalse(step["logs"]["stdout"]["streamClosed"])
        finally:
            close_errors = []
            for handle in writer_handles:
                if not kernel.CloseHandle(handle):
                    close_errors.append("failed to close test-owned duplicate pipe writer")
            for drain in drains:
                if drain.thread.ident is not None:
                    drain.thread.join(timeout=2)
            serial_runner.CLEANUP_MS, serial_runner.POST_CLOSE_MS = old_cleanup, old_post
        self.assertFalse(close_errors, close_errors)
        self.assertTrue(drains and all(drain.streamClosed for drain in drains))
        self.assertTrue(all(not drain.thread.is_alive() for drain in drains))


if __name__ == "__main__":
    unittest.main()
