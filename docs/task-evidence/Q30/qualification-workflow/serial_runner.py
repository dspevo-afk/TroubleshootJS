"""Fail-closed serial Windows runner for future Q30 qualification steps."""
from __future__ import annotations

import ctypes
from ctypes import wintypes
import hashlib
import json
import msvcrt
import os
from pathlib import Path, PureWindowsPath
import re
import subprocess
import stat
import tempfile
import threading
import time
from datetime import datetime, timezone

from receipts import LIMITS, ReceiptError, summarize_cases, validate_case, write_exclusive

try:
    import _winapi
except ImportError:  # pragma: no cover - Windows-only execution boundary
    _winapi = None

HASH64, HASH40 = re.compile(r"[0-9a-f]{64}\Z"), re.compile(r"[0-9a-f]{40}\Z")
SEED = re.compile(r"-?(0|[1-9][0-9]*)\Z")
TOKEN = "{receipt}"
MAX_OUTER_MS, MAX_LOG_BYTES, MAX_RECEIPT = 180000, 1024 * 1024, 4 * 1024 * 1024
MAX_CASES, CLEANUP_MS, POST_CLOSE_MS, POLL_MS = 1000, 10000, 5000, 25
JOB_ACCOUNTING_DRAIN_MS = 500
CREATE_SUSPENDED, EXTENDED_STARTUPINFO_PRESENT, CREATE_NO_WINDOW = 4, 0x80000, 0x08000000
STARTF_USESTDHANDLES, JOB_EXTENDED, JOB_ACCOUNTING = 0x100, 9, 1
JOB_KILL_ON_CLOSE, FILE_REPARSE, INVALID_ATTR = 0x2000, 0x400, 0xFFFFFFFF
WAIT_OBJECT_0, WAIT_TIMEOUT, WAIT_FAILED = 0, 258, 0xFFFFFFFF


class RunnerError(RuntimeError):
    pass


class SpawnError(RunnerError):
    def __init__(self, message, cleanup):
        super().__init__(message)
        self.cleanup = cleanup


def _need(ok, message):
    if not ok:
        raise RunnerError(message)


def _sha(raw):
    return hashlib.sha256(raw).hexdigest()


def _utc():
    return datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")


def _seed(value):
    _need(isinstance(value, str) and SEED.fullmatch(value) is not None,
          "rootSeed must be canonical signed decimal text")
    n = int(value)
    _need(-(2**63) <= n <= 2**63 - 1 and str(n) == value, "rootSeed outside signed-long range")
    return value


def _api():
    _need(os.name == "nt", "serial runner requires Windows")
    k = ctypes.WinDLL("kernel32", use_last_error=True)
    C, D, H, B, W, P = ctypes.c_void_p, wintypes.DWORD, wintypes.HANDLE, wintypes.BOOL, wintypes.LPCWSTR, ctypes.POINTER
    defs = {
        "GetFileAttributesW": (D, [W]), "CreateJobObjectW": (H, [C, W]),
        "SetInformationJobObject": (B, [H, ctypes.c_int, C, D]),
        "QueryInformationJobObject": (B, [H, ctypes.c_int, C, D, P(D)]),
        "AssignProcessToJobObject": (B, [H, H]), "TerminateJobObject": (B, [H, D]),
        "TerminateProcess": (B, [H, D]), "ResumeThread": (D, [H]),
        "WaitForSingleObject": (D, [H, D]), "GetExitCodeProcess": (B, [H, P(D)]),
        "GetProcessTimes": (B, [H, C, C, C, C]),
        "QueryFullProcessImageNameW": (B, [H, D, wintypes.LPWSTR, P(D)]),
        "CloseHandle": (B, [H]),
    }
    for name, (result, args) in defs.items():
        fn = getattr(k, name); fn.restype, fn.argtypes = result, args
    return k


class _FILETIME(ctypes.Structure):
    _fields_ = [("low", wintypes.DWORD), ("high", wintypes.DWORD)]


class _BASIC_LIMIT(ctypes.Structure):
    _fields_ = [(n, t) for n, t in (
        ("processTime", ctypes.c_int64), ("jobTime", ctypes.c_int64), ("flags", wintypes.DWORD),
        ("minWork", ctypes.c_size_t), ("maxWork", ctypes.c_size_t), ("active", wintypes.DWORD),
        ("affinity", ctypes.c_size_t), ("priority", wintypes.DWORD), ("schedule", wintypes.DWORD))]


class _IO(ctypes.Structure):
    _fields_ = [(n, ctypes.c_uint64) for n in ("readOps", "writeOps", "otherOps", "readBytes", "writeBytes", "otherBytes")]


class _EXT_LIMIT(ctypes.Structure):
    _fields_ = [("basic", _BASIC_LIMIT), ("io", _IO)] + [
        (n, ctypes.c_size_t) for n in ("processMemory", "jobMemory", "peakProcess", "peakJob")]


class _ACCOUNTING(ctypes.Structure):
    _fields_ = [(n, t) for n, t in [(n, ctypes.c_int64) for n in
        ("user", "kernel", "periodUser", "periodKernel")]] + [
        (n, wintypes.DWORD) for n in ("faults", "total", "active", "terminated")]


def _error(what):
    code = ctypes.get_last_error()
    return OSError(code, f"{what}: {ctypes.FormatError(code).strip()}")


def _attrs(k, path):
    value = k.GetFileAttributesW(str(path))
    if value == INVALID_ATTR:
        return None
    return value


def _check_chain(k, path, root):
    path, root = Path(path).absolute(), Path(root).absolute()
    try:
        parts = path.relative_to(root).parts
    except ValueError as exc:
        raise RunnerError("path escaped approved root") from exc
    cur = root
    for item in (None, *parts):
        if item is not None:
            cur = cur / item
        attrs = _attrs(k, cur)
        if attrs is not None:
            _need(not attrs & FILE_REPARSE, "path contains a symlink/reparse point")


def _executable(k, raw):
    _need(isinstance(raw, str) and PureWindowsPath(raw).is_absolute(),
          "executable must be an absolute Windows path")
    path = Path(raw)
    _need(path.is_absolute(), "executable must be an absolute Windows path")
    _check_chain(k, path, Path(path.anchor))
    resolved = path.resolve(strict=True)
    _need(resolved.is_file() and os.path.normcase(str(path)) == os.path.normcase(str(resolved)),
          "executable is not a plain regular file")
    return resolved


def _validate_spec(spec, k):
    _need(type(spec) is dict and set(spec) == {"sourceIdentity", "baseHead", "planSha256", "limits", "steps"},
          "spec must contain exactly sourceIdentity/baseHead/planSha256/limits/steps")
    _need(isinstance(spec["sourceIdentity"], str) and HASH64.fullmatch(spec["sourceIdentity"]) and
          isinstance(spec["baseHead"], str) and HASH40.fullmatch(spec["baseHead"]) and
          isinstance(spec["planSha256"], str) and HASH64.fullmatch(spec["planSha256"]),
          "spec identity hashes are malformed")
    _need(type(spec["limits"]) is dict and spec["limits"] == LIMITS and
          all(type(v) is int for v in spec["limits"].values()), "spec changed frozen caps")
    _need(type(spec["steps"]) is list and 1 <= len(spec["steps"]) <= MAX_CASES, "invalid steps array")
    seen, steps = set(), []
    for i, item in enumerate(spec["steps"]):
        _need(type(item) is dict and set(item) == {"rootSeed", "argv", "outerTimeoutMs"},
              f"step {i} schema differs")
        seed, argv, timeout = _seed(item["rootSeed"]), item["argv"], item["outerTimeoutMs"]
        _need(seed not in seen, "duplicate rootSeed"); seen.add(seed)
        _need(type(argv) is list and 1 <= len(argv) <= 128 and
              all(isinstance(a, str) and a and "\x00" not in a for a in argv), f"step {i} argv invalid")
        exe = _executable(k, argv[0])
        _need(sum(a == TOKEN for a in argv) == 1 and sum(a.count(TOKEN) for a in argv) == 1,
              f"step {i} must have one standalone {TOKEN} placeholder")
        _need(type(timeout) is int and 0 < timeout <= MAX_OUTER_MS, f"step {i} timeout outside supported range")
        _need(len(subprocess.list2cmdline(argv)) < 32767, f"step {i} command line exceeds Windows limit")
        steps.append({"rootSeed": seed, "argv": list(argv), "outerTimeoutMs": timeout, "executable": exe})
    return [x["rootSeed"] for x in steps], steps


def _active(k, job):
    info = _ACCOUNTING()
    if not k.QueryInformationJobObject(job, JOB_ACCOUNTING, ctypes.byref(info), ctypes.sizeof(info), None):
        raise _error("QueryInformationJobObject")
    return int(info.active)


def _identity(k, handle, expected):
    name, length = ctypes.create_unicode_buffer(32768), wintypes.DWORD(32768)
    if not k.QueryFullProcessImageNameW(handle, 0, name, ctypes.byref(length)):
        raise _error("QueryFullProcessImageNameW")
    actual = Path(name.value[:length.value]).resolve(strict=True)
    _need(os.path.normcase(str(actual)) == os.path.normcase(str(expected)), "created image differs from executable")
    times = [_FILETIME() for _ in range(4)]
    if not k.GetProcessTimes(handle, *(ctypes.byref(x) for x in times)):
        raise _error("GetProcessTimes")
    return str(actual), (int(times[0].high) << 32) | int(times[0].low)


class _Drain:
    def __init__(self, fd, stream):
        self.fd, self.stream, self.count = fd, stream, 0
        self.overflow, self.errors = threading.Event(), []
        self.streamClosed = False
        self.thread = threading.Thread(target=self._read, daemon=True)

    def _read(self):
        try:
            while True:
                chunk = os.read(self.fd, 65536)
                if not chunk:
                    return
                room = MAX_LOG_BYTES - self.count
                if room > 0:
                    data = chunk[:room]; self.stream.write(data); self.count += len(data)
                if len(chunk) > room:
                    self.overflow.set()
        except BaseException as exc:
            self.errors.append("reader: " + repr(exc))
        finally:
            try:
                os.close(self.fd)
            except BaseException as exc:
                self.errors.append("pipe-close: " + repr(exc))
            try:
                self.stream.flush()
            except BaseException as exc:
                self.errors.append("log-flush: " + repr(exc))
            try:
                self.stream.close()
                self.streamClosed = True
            except BaseException as exc:
                self.errors.append("log-close: " + repr(exc))


def _make_job(k):
    job = k.CreateJobObjectW(None, None)
    if not job:
        raise _error("CreateJobObjectW")
    limits = _EXT_LIMIT(); limits.basic.flags = JOB_KILL_ON_CLOSE
    if not k.SetInformationJobObject(job, JOB_EXTENDED, ctypes.byref(limits), ctypes.sizeof(limits)):
        err = _error("SetInformationJobObject"); k.CloseHandle(job); raise err
    return job


def _stop(k, job, proc, assigned, pid, cleanup_started=None, drains=()):
    started = cleanup_started if cleanup_started is not None else time.monotonic()
    cleanup_deadline = started + (CLEANUP_MS + POST_CLOSE_MS) / 1000
    job_deadline = started + CLEANUP_MS / 1000
    drain_started = time.monotonic()
    drain_ms, drain_succeeded, accounting_error = 0, None, None
    if proc:
        if assigned and job:
            if k.WaitForSingleObject(proc, 0) == WAIT_OBJECT_0:
                drain_succeeded = False
                drain_end = min(job_deadline, time.monotonic() + JOB_ACCOUNTING_DRAIN_MS / 1000)
                while time.monotonic() < drain_end:
                    try:
                        active_before_termination = _active(k, job)
                    except OSError as exc:
                        accounting_error = str(exc)
                        break
                    if active_before_termination == 0:
                        drain_succeeded = True
                        break
                    time.sleep(min(POLL_MS / 1000, max(0, drain_end - time.monotonic())))
                if not drain_succeeded:
                    k.TerminateJobObject(job, 0xE201)
                drain_ms = int((time.monotonic() - drain_started) * 1000)
            else:
                k.TerminateJobObject(job, 0xE201)
        else:
            k.TerminateProcess(proc, 0xE202)
    active, signaled = None, False
    while time.monotonic() < job_deadline:
        signaled = bool(proc and k.WaitForSingleObject(proc, 0) == WAIT_OBJECT_0)
        try:
            active = _active(k, job) if job else 0
        except OSError:
            active = None
        if signaled and active == 0:
            break
        time.sleep(POLL_MS / 1000)
    closed = False
    if not signaled or active != 0:
        if job:
            k.CloseHandle(job); job = None; closed = True
        if proc:
            remaining_ms = max(0, int((cleanup_deadline - time.monotonic()) * 1000))
            if remaining_ms:
                k.WaitForSingleObject(proc, remaining_ms)
            signaled = k.WaitForSingleObject(proc, 0) == WAIT_OBJECT_0
        active = None
    drainer_started = time.monotonic()
    for drain in drains:
        if drain.thread.ident is None or not drain.thread.is_alive():
            continue
        remaining = cleanup_deadline - time.monotonic()
        if remaining <= 0:
            break
        drain.thread.join(timeout=remaining)
    drainer_ms = int((time.monotonic() - drainer_started) * 1000)
    live_drainers = sum(1 for drain in drains if drain.thread.is_alive())
    drain_errors = [error for drain in drains for error in drain.errors]
    drains_complete = (live_drainers == 0 and all(drain.streamClosed for drain in drains) and not drain_errors)
    return job, {"pid": pid, "processSignaled": bool(signaled), "jobActiveProcesses": active,
                 "jobClosedForCleanup": closed, "drainsComplete": drains_complete,
                 "liveDrainers": live_drainers, "drainerWaitMs": drainer_ms,
                 "drainerErrors": drain_errors,
                 "cleanupVerified": bool(signaled and active == 0 and drains_complete),
                 "cleanupElapsedMs": int((time.monotonic() - started) * 1000),
                 "jobAccountingDrainMs": drain_ms, "jobAccountingDrainSucceeded": drain_succeeded,
                 "jobAccountingError": accounting_error}


def _spawn(k, step, argv, cwd):
    job, proc, thread, assigned = _make_job(k), None, None, False
    fds, drains, files = [], [], []
    try:
        out_r, out_w = os.pipe(); err_r, err_w = os.pipe(); nul = os.open("NUL", os.O_RDONLY | getattr(os, "O_BINARY", 0))
        fds.extend((out_r, out_w, err_r, err_w, nul))
        handles = [msvcrt.get_osfhandle(fd) for fd in (nul, out_w, err_w)]
        for fd in (out_r, err_r):
            os.set_handle_inheritable(msvcrt.get_osfhandle(fd), False)
        for handle in handles:
            os.set_handle_inheritable(handle, True)
        si = subprocess.STARTUPINFO()
        si.dwFlags = STARTF_USESTDHANDLES | subprocess.STARTF_USESHOWWINDOW
        si.wShowWindow = subprocess.SW_HIDE
        si.hStdInput, si.hStdOutput, si.hStdError = handles
        si.lpAttributeList = {"handle_list": handles}
        command = subprocess.list2cmdline(argv)
        try:
            proc, thread, pid, _ = _winapi.CreateProcess(str(step["executable"]), command, None, None, True,
                CREATE_SUSPENDED | EXTENDED_STARTUPINFO_PRESENT | CREATE_NO_WINDOW, None, str(cwd), si)
        finally:
            for handle in handles:
                os.set_handle_inheritable(handle, False)
        os.close(out_w); os.close(err_w); os.close(nul)
        fds = [out_r, err_r]
        files.append((cwd / "stdout.log").open("xb", buffering=0))
        files.append((cwd / "stderr.log").open("xb", buffering=0))
        drains.append(_Drain(out_r, files[0])); drains[-1].thread.start()
        fds.remove(out_r); out_r = None
        drains.append(_Drain(err_r, files[1])); drains[-1].thread.start()
        fds.remove(err_r); err_r = None
        image, created = _identity(k, proc, step["executable"])
        if not k.AssignProcessToJobObject(job, proc):
            raise _error("AssignProcessToJobObject")
        assigned = True
        if k.ResumeThread(thread) != 1:
            raise _error("ResumeThread")
        return {"job": job, "proc": proc, "thread": thread, "pid": int(pid), "image": image,
                "creationTicks": created, "commandSha256": _sha(command.encode("utf-16-le")),
                "drains": drains, "files": files, "resumeNs": time.monotonic_ns(), "assigned": True}
    except Exception as exc:
        if proc:
            job, cleanup = _stop(k, job, proc, assigned, int(pid), drains=drains)
        else:
            cleanup = {"pid": None, "processSignaled": True, "jobActiveProcesses": 0,
                       "jobClosedForCleanup": False, "cleanupVerified": True, "cleanupElapsedMs": 0}
        for d in drains:
            if d.thread.ident is None:
                try:
                    d.stream.close(); d.streamClosed = True
                except OSError as close_error:
                    d.errors.append(repr(close_error))
        for fd in fds:
            try: os.close(fd)
            except OSError: pass
        drained_streams = {id(d.stream) for d in drains}
        for stream in files:
            if id(stream) not in drained_streams:
                try: stream.close()
                except OSError: pass
        if job: k.CloseHandle(job)
        if thread: k.CloseHandle(thread)
        if proc: k.CloseHandle(proc)
        raise SpawnError(f"owned child creation/assignment/resume failed: {exc}", cleanup) from exc


def _wait(k, child, timeout_ms):
    deadline = child["resumeNs"] + timeout_ms * 1_000_000
    reason, state = None, WAIT_TIMEOUT
    while True:
        if any(d.overflow.is_set() for d in child["drains"]):
            reason = "stdout/stderr exceeded 1 MiB"; break
        if any(d.errors for d in child["drains"]):
            reason = "stdout/stderr capture failed"; break
        state = k.WaitForSingleObject(child["proc"], POLL_MS)
        if state == WAIT_OBJECT_0:
            if time.monotonic_ns() > deadline:
                reason = "outer process deadline expired"
            break
        if state == WAIT_FAILED: reason = _error("WaitForSingleObject").strerror; break
        if state != WAIT_TIMEOUT: reason = f"unexpected wait status {state}"; break
        if time.monotonic_ns() >= deadline: reason = "outer process deadline expired"; break
    process_end = time.monotonic_ns()
    signaled = k.WaitForSingleObject(child["proc"], 0) == WAIT_OBJECT_0
    cleanup_started = time.monotonic()
    job, clean = _stop(k, child["job"], child["proc"], True, child["pid"], cleanup_started,
                       drains=child["drains"])
    child["job"] = job
    if reason is None and signaled and not clean["jobAccountingDrainSucceeded"]:
        detail = clean["jobAccountingError"]
        reason = (f"job accounting query failed after root exit: {detail}" if detail else
                  "root exited with live/unverified job descendants")
    if not clean["drainsComplete"]:
        reason = reason or "stdout/stderr readers did not finish within shared cleanup deadline"
    if not clean["cleanupVerified"]: reason = reason or "owned job or log-reader cleanup was not verified"
    code = wintypes.DWORD()
    exit_code = int(code.value) if k.GetExitCodeProcess(child["proc"], ctypes.byref(code)) else None
    return {"reason": reason, "exitCode": exit_code, "cleanup": clean,
            "operationElapsedMs": int(max(0, (process_end - child["resumeNs"]) // 1_000_000))}


def _load_receipt(path, k):
    attrs = _attrs(k, path)
    if attrs is None: return None, "worker did not create receipt"
    _need(not attrs & FILE_REPARSE, "worker receipt is reparse point")
    st = path.stat(); _need(stat.S_ISREG(st.st_mode) and st.st_size <= MAX_RECEIPT,
                            "worker receipt is not regular or exceeds 4 MiB")
    raw = path.read_bytes(); _need(len(raw) == st.st_size, "receipt changed during read")
    try:
        value = json.loads(raw.decode("utf-8-sig", errors="strict"), object_pairs_hook=_pairs,
                           parse_constant=lambda x: (_ for _ in ()).throw(ValueError("nonfinite JSON")))
    except (UnicodeError, json.JSONDecodeError, ValueError) as exc:
        return None, f"invalid worker receipt JSON: {exc}"
    _need(type(value) is dict, "worker receipt must be a JSON object")
    return value, None


def _pairs(items):
    result = {}
    for key, value in items:
        _need(key not in result, "duplicate receipt JSON key"); result[key] = value
    return result


def _fallback(spec, seed, timeout, reason, elapsed, cleanup, exit_code=None):
    cleanup = cleanup or {"cleanupVerified": False, "cleanupElapsedMs": 0, "pid": None}
    return {"schema": 1, "rootSeed": seed, "sourceIdentity": spec["sourceIdentity"],
        "baseHead": spec["baseHead"], "planSha256": spec["planSha256"], "limits": dict(LIMITS),
        "application": {"status": "FAIL", "elapsedMs": None,
            "workUnits": None, "maxActiveOperationMs": None, "maxCoordinatorAdvanceMs": None},
        "host": {"status": "TIMEOUT" if "deadline" in reason else "FAIL", "observationElapsedMs": int(elapsed),
            "deadlineMs": timeout, "exitCode": exit_code, "terminal": False},
        "cleanup": {"status": "FAIL", "elapsedMs": cleanup.get("cleanupElapsedMs", 0),
            "elapsedLimitations": None, "serverStopped": False,
            "ownedSurvivors": ["application-cleanup-unreported"]},
        "operation": {"elapsedMs": int(elapsed), "elapsedLimitations": None},
        "sourceInputsUnchanged": False, "serialRunner": {"status": "FAIL", "reason": reason}}


def _apply_outer_host_failure(receipt, reason, waited, timeout, cleanup):
    """Preserve measured application results while recording the outer host failure."""
    host = dict(receipt["host"])
    host.update({"status": "TIMEOUT" if "deadline" in reason else "FAIL",
                 "observationElapsedMs": waited["operationElapsedMs"],
                 "deadlineMs": timeout, "exitCode": waited["exitCode"], "terminal": False})
    receipt["host"] = host
    if waited["reason"] is not None or not cleanup.get("cleanupVerified", False):
        gate_cleanup = dict(receipt["cleanup"])
        gate_cleanup.update({"status": "FAIL", "serverStopped": False,
                             "ownedSurvivors": list(gate_cleanup.get("ownedSurvivors", []))})
        receipt["cleanup"] = gate_cleanup


def _case_dir(root, i, seed):
    return root / "cases" / f"{i:03d}-seed-{seed.replace('-', 'minus-')}"


def _run_case(k, spec, step, index, root):
    seed, timeout = step["rootSeed"], step["outerTimeoutMs"]
    case = _case_dir(root, index, seed); case.mkdir(exist_ok=False)
    receipt_path = case / "worker-receipt.json"; _need(_attrs(k, receipt_path) is None, "receipt path already exists")
    argv = [str(receipt_path) if a == TOKEN else a for a in step["argv"]]
    started, t0, child = _utc(), time.monotonic_ns(), None
    record = {"index": index, "rootSeed": seed, "startedUtc": started, "runnerStatus": "FAIL"}
    try:
        child = _spawn(k, step, argv, case)
        waited = _wait(k, child, timeout)
        out, err = child["drains"]
        logs = {"stdout": {"bytes": out.count, "limitExceeded": out.overflow.is_set(), "errors": out.errors,
                           "readerAlive": out.thread.is_alive(), "streamClosed": out.streamClosed},
                "stderr": {"bytes": err.count, "limitExceeded": err.overflow.is_set(), "errors": err.errors,
                           "readerAlive": err.thread.is_alive(), "streamClosed": err.streamClosed}}
        identity = {"pid": child["pid"], "creationFileTimeTicks": child["creationTicks"],
            "executable": child["image"], "commandSha256": child["commandSha256"], "exitCode": waited["exitCode"]}
        host_reason = waited["reason"]
        reason = host_reason
        if any(v["limitExceeded"] for v in logs.values()): reason = reason or "stdout/stderr exceeded 1 MiB"
        if any(v["errors"] or v["readerAlive"] or not v["streamClosed"] for v in logs.values()):
            reason = reason or "stdout/stderr reader did not finish cleanly"
        if reason and host_reason is None:
            host_reason = reason
        raw, read_error = _load_receipt(receipt_path, k)
        status, receipt_error = None, None
        if raw is not None:
            try:
                status = validate_case(raw)
                for field, expected in (("rootSeed", seed), ("sourceIdentity", spec["sourceIdentity"]),
                    ("baseHead", spec["baseHead"]), ("planSha256", spec["planSha256"]), ("limits", LIMITS)):
                    _need(raw.get(field) == expected, "worker receipt identity mismatch: " + field)
            except (ReceiptError, RunnerError, TypeError, ValueError) as exc:
                receipt_error, status = str(exc), "FAIL"
        else: receipt_error = read_error
        if waited["exitCode"] != 0:
            reason = reason or "worker exit code was nonzero"
            host_reason = host_reason or "worker exit code was nonzero"
        if status != "PASS": reason = reason or receipt_error or f"worker receipt validation was {status}"
        if raw is None or receipt_error:
            normalized = _fallback(spec, seed, timeout, reason or receipt_error, waited["operationElapsedMs"],
                                   waited["cleanup"], waited["exitCode"])
        else:
            normalized = dict(raw)
            if host_reason:
                normalized["host"] = dict(normalized["host"])
                normalized["cleanup"] = dict(normalized["cleanup"])
                _apply_outer_host_failure(normalized, host_reason, waited, timeout, waited["cleanup"])
            normalized["serialRunner"] = {"status": "FAIL" if reason else "PASS", "reason": reason}
        normalized_status = validate_case(normalized)
        outcome = "PASS" if not reason and normalized_status == "PASS" else "FAIL"
        write_exclusive(case / "normalized-receipt.json", normalized)
        record.update({"runnerStatus": outcome, "reason": reason, "receiptValidation": normalized_status,
            "process": identity, "operationElapsedMs": waited["operationElapsedMs"],
            "cleanup": waited["cleanup"], "logs": logs,
            "workerReceiptSha256": _sha(receipt_path.read_bytes()) if receipt_path.is_file() else None})
    except SpawnError as exc:
        reason = str(exc); clean = exc.cleanup
        normalized = _fallback(spec, seed, timeout, reason, (time.monotonic_ns()-t0)//1_000_000, clean)
        write_exclusive(case / "normalized-receipt.json", normalized)
        record.update({"runnerStatus": "FAIL", "reason": reason, "cleanup": clean,
                      "receiptValidation": validate_case(normalized)})
    except Exception as exc:
        reason = f"runner error: {type(exc).__name__}: {exc}"
        clean = {"cleanupVerified": False, "cleanupElapsedMs": 0, "pid": child["pid"] if child else None}
        if child and child["job"]:
            child["job"], clean = _stop(k, child["job"], child["proc"], True, child["pid"],
                                        drains=child["drains"])
        normalized = _fallback(spec, seed, timeout, reason, (time.monotonic_ns()-t0)//1_000_000, clean)
        if not (case / "normalized-receipt.json").exists(): write_exclusive(case / "normalized-receipt.json", normalized)
        record.update({"runnerStatus": "FAIL", "reason": reason, "cleanup": clean,
                      "receiptValidation": validate_case(normalized)})
    finally:
        if child:
            if child["job"]: k.CloseHandle(child["job"])
            k.CloseHandle(child["thread"]); k.CloseHandle(child["proc"])
    record["finishedUtc"] = _utc()
    record["elapsedWallMs"] = (time.monotonic_ns()-t0)//1_000_000
    write_exclusive(case / "step-result.json", record)
    return record, normalized


def run_serial(spec, new_os_temp_root):
    """Run ordered steps; first FAIL stops later launches and reports them NOT_RUN."""
    k = _api(); expected, steps = _validate_spec(spec, k)
    supplied_root = Path(new_os_temp_root)
    _need(supplied_root.is_absolute(), "runner root must be an absolute path")
    root, temp = supplied_root, Path(tempfile.gettempdir()).resolve(strict=True)
    _check_chain(k, root.parent, temp)
    _need(root.parent.resolve(strict=True).is_relative_to(temp), "runner root must be under OS temp")
    _need(_attrs(k, root) is None, "runner root already exists")
    root.mkdir(exist_ok=False); (root / "cases").mkdir(exist_ok=False)
    write_exclusive(root / "spec.json", spec)
    receipts, results = [], []
    for i, step in enumerate(steps, 1):
        result, receipt = _run_case(k, spec, step, i, root)
        results.append(result); receipts.append(receipt)
        if result["runnerStatus"] != "PASS": break
    summary = summarize_cases(expected, receipts)
    summary.update({"schema": 1, "sourceIdentity": spec["sourceIdentity"], "baseHead": spec["baseHead"],
        "planSha256": spec["planSha256"], "limits": dict(LIMITS), "steps": results, "root": str(root),
        "serial": True, "stoppedAtFirstFailure": len(results) < len(steps) or summary["outcome"] != "PASS"})
    write_exclusive(root / "sequence-summary.json", summary)
    return summary
