"""Real Windows canaries for shared bounded host phases."""
import os
from pathlib import Path
import sys
import tempfile
import unittest
from serial_runner import run_command, RunnerError
from windows_process_identity import compare_identity, query_process, ABSENT

@unittest.skipUnless(os.name == "nt", "native Windows runner")
class HostPhaseTests(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp(prefix="q30-host-phase-test-"))
        self.python = str(Path(os.environ.get("Q30_SERIAL_RUNNER_PYTHON", sys.executable)).resolve(strict=True))
    def tearDown(self):
        print("retained host phase evidence: " + str(self.base), flush=True)
    def assert_closed(self, result):
        self.assertTrue(result["cleanup"]["cleanupVerified"], result)
        self.assertTrue(result["handlesClosed"], result)
        self.assertEqual(ABSENT, compare_identity(result["process"], query_process(result["process"]["pid"]))["status"])
    def test_success_records_native_birth_and_separate_operation_cleanup(self):
        result = run_command([self.python, "-B", "-c", "print('positive')"], self.base / "pass", 3000)
        self.assertEqual("PASS", result["status"])
        self.assertEqual(0, result["operation"]["exitCode"])
        self.assertEqual("NATIVE_100NS", result["process"]["birthPrecision"])
        self.assert_closed(result)
    def test_timeout_cleans_exact_owned_descendant_and_remains_fail(self):
        code = "import subprocess,time;subprocess.Popen([" + repr(self.python) + ",'-c','import time;time.sleep(60)']);time.sleep(60)"
        result = run_command([self.python, "-B", "-c", code], self.base / "timeout", 1000)
        self.assertEqual("FAIL", result["status"])
        self.assertEqual("outer process deadline expired", result["operation"]["reason"])
        self.assertGreaterEqual(result["operation"]["elapsedMs"], 1000)
        self.assert_closed(result)
    def test_root_exit_with_live_descendant_is_failure_even_when_cleaned(self):
        code = "import subprocess;subprocess.Popen([" + repr(self.python) + ",'-c','import time;time.sleep(60)'])"
        result = run_command([self.python, "-B", "-c", code], self.base / "orphan", 3000)
        self.assertEqual("FAIL", result["status"])
        self.assertIn("live/unverified job descendants", result["operation"]["reason"])
        self.assert_closed(result)
    def test_fast_exit_overflow_cannot_pass_after_drainers_finish(self):
        code = "import sys;sys.stdout.buffer.write(b'x' * (2 * 1024 * 1024));sys.stdout.flush()"
        result = run_command([self.python, "-B", "-c", code], self.base / "overflow", 3000)
        self.assertEqual("FAIL", result["status"])
        self.assertTrue(result["logs"]["stdout"]["overflow"], result)
        self.assertIn("1 MiB", result["operation"]["reason"])
        self.assertLessEqual((self.base / "overflow" / "stdout.log").stat().st_size, 1024 * 1024)
        self.assert_closed(result)

    def test_invalid_budget_and_occupied_output_reject_before_launch(self):
        args = [self.python, "-B", "-c", "pass"]
        with self.assertRaises(RunnerError):
            run_command(args, self.base / "invalid", 1200001)
        self.assertFalse((self.base / "invalid").exists())
        occupied = self.base / "occupied"
        occupied.mkdir()
        sentinel = occupied / "keep"
        sentinel.write_bytes(b"preserve")
        with self.assertRaises(RunnerError):
            run_command(args, occupied, 3000)
        self.assertEqual(b"preserve", sentinel.read_bytes())
