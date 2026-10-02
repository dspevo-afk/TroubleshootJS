"""One serial fail-fast harness canary batch; retain task-owned evidence."""
import ctypes
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import time
import unittest

import serial_runner
from receipts import write_exclusive


def utc():
    return datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")


class Result(unittest.TextTestResult):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.rows = []
        self.current = None

    def startTest(self, test):
        self.current = {"test": test.id(), "startedUtc": utc(), "status": "RUNNING"}
        self.t0 = time.monotonic()
        super().startTest(test)

    def addSuccess(self, test):
        self.current["status"] = "PASS"
        super().addSuccess(test)

    def addFailure(self, test, err):
        self.current["status"] = "FAIL"
        super().addFailure(test, err)

    def addError(self, test, err):
        self.current["status"] = "ERROR"
        super().addError(test, err)

    def addSkip(self, test, reason):
        self.current.update(status="SKIPPED", reason=reason)
        super().addSkip(test, reason)

    def addSubTest(self, test, subtest, err):
        if err is not None:
            self.current["status"] = "FAIL"
        super().addSubTest(test, subtest, err)

    def stopTest(self, test):
        self.current.update(finishedUtc=utc(), elapsedSeconds=time.monotonic()-self.t0,
                            retainedTaskRoot=str(test.base) if hasattr(test, "base") else str(test.evidence) if hasattr(test, "evidence") else None)
        self.rows.append(self.current)
        super().stopTest(test)


def main():
    work = Path(__file__).resolve().parent
    files = ["receipts.py", "serial_runner.py", "test_receipts.py", "test_serial_runner.py", "test_normalizer.py",
             "normalize_cold_evidence.py", "test_serial_runner_held_pipe.py", Path(__file__).name]
    hashes = {name: hashlib.sha256((work/name).read_bytes()).hexdigest() for name in files}
    root = Path(tempfile.mkdtemp(prefix="q30-workflow-tests-"))
    k = serial_runner._api()
    k.GetCurrentProcess.restype = ctypes.c_void_p
    image, created = serial_runner._identity(k, k.GetCurrentProcess(), Path(sys.executable).resolve(strict=True))
    owner = {"pid": os.getpid(), "executable": image, "creationFileTimeTicks": created}
    start, t0 = utc(), time.monotonic()
    write_exclusive(root/"launch.json", {"schema": 1, "owner": owner, "startedUtc": start,
        "sourceHashes": hashes, "scope": "synthetic harness canaries, not electrical qualification",
        "qualification": False, "maximumBatchSeconds": 120, "concurrency": 1, "requests": 0})
    names = sys.argv[1:] or ["test_receipts", "test_normalizer", "test_serial_runner", "test_serial_runner_held_pipe"]
    suite = unittest.defaultTestLoader.loadTestsFromNames(names)
    planned = suite.countTestCases()
    result = unittest.TextTestRunner(verbosity=2, failfast=True, resultclass=Result).run(suite)
    elapsed = time.monotonic()-t0
    unchanged = all(hashlib.sha256((work/name).read_bytes()).hexdigest()==digest
                    for name, digest in hashes.items())
    passed = result.wasSuccessful() and result.testsRun == planned and not result.skipped and unchanged and elapsed <= 120
    proof = {"schema": 1, "status": "PASS" if passed else "FAIL", "qualification": False,
        "scope": "Actual selected Windows process/job boundary plus synthetic receipt validation",
        "owner": owner, "startedUtc": start, "finishedUtc": utc(), "operationSeconds": elapsed,
        "plannedTests": planned, "testsRun": result.testsRun, "failures": len(result.failures),
        "errors": len(result.errors), "skipped": len(result.skipped), "sourceHashes": hashes,
        "sourceInputsUnchanged": unchanged, "results": result.rows}
    write_exclusive(root/"test-results.json", proof)
    print(json.dumps({"status": proof["status"], "testsRun": result.testsRun,
                      "plannedTests": planned, "seconds": elapsed, "taskRoot": str(root)}))
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
