"""Finite current CODE/NO_DATA compile composition; never native Study admission.

The trusted lab caller authenticates the config and its independent CODE class
profile before launch. This script only composes existing owners. The sole
compiler launch belongs to VerifierIsolation inside the selected PS test host.
"""
import argparse
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import types
import zipfile


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def save(path, obj):
    raw = (json.dumps(obj, indent=2) + "\n").encode("ascii")
    with path.open("xb") as output:
        output.write(raw)


def pinned(row):
    assert Path(row["path"]).stat().st_size == row["bytes"]
    raw = Path(row["path"]).read_bytes()
    assert len(raw) == row["bytes"] and digest(raw) == row["sha256"]
    return raw


def load_owners(cfg):
    row = cfg["modules"]["loader"]
    loader = types.ModuleType("_current_compile_lab_loader")
    loader.__file__ = row["path"]
    exec(compile(pinned(row), row["path"], "exec", dont_inherit=True), loader.__dict__)
    rows = cfg["modules"]
    caller, reader, _, _ = loader._load_modules(types.SimpleNamespace(
        reader=rows["reader"]["path"], reader_sha=rows["reader"]["sha256"],
        caller=rows["caller"]["path"], caller_sha=rows["caller"]["sha256"]))
    owners = {"loader": loader, "caller": caller, "reader": reader}
    for role in ("observer", "stager", "invocations", "byteFacts", "inventory"):
        row = rows[role]
        raw, sha, _ = reader._read_file_once(row["path"], 4194304,
            row["bytes"], row["sha256"], "CURRENT_COMPILE_CODE_OWNER")
        assert sha == row["sha256"]
        owners[role] = loader._exec_authenticated_module(
            "_current_compile_" + role, row["path"], raw)
    return owners


def inspect_jars(cfg):
    names = set()
    for row in cfg["jars"]:
        # Exact, known CODE libraries, external to the reader's 8MiB roles.
        pinned(row)
        with zipfile.ZipFile(row["path"]) as archive:
            names.update(info.filename for info in archive.infolist()
                         if info.filename.endswith(".class"))
        pinned(row)
    assert names == set(cfg["classProfile"]["jarClassNames"])
    return frozenset(names)


def inventory(owners, cfg, fresh, jars):
    profile = cfg["classProfile"]
    actual = owners["inventory"].observe_current_code_classes(
        reader=owners["reader"], io_owner=owners["caller"],
        invocation_owner=owners["invocations"], fresh_root=str(fresh),
        inherited_root=profile["inheritedRoot"], fresh_rows=profile["freshRows"],
        inherited_rows=profile["inheritedRows"], jar_class_names=jars)
    assert len(actual["freshClasses"]) == 141
    assert len(actual["inheritedClasses"]) == 2396
    assert actual["classCount"] == 2537 and actual["classBytes"] == 12196044
    assert actual["freshClasses"] == sorted(profile["freshRows"], key=lambda x: x["path"])
    assert actual["inheritedClasses"] == sorted(profile["inheritedRows"], key=lambda x: x["path"])
    return {"freshClasses": 141, "inheritedClasses": 2396,
            "classCount": actual["classCount"], "classBytes": actual["classBytes"],
            "freshRowsSha256": digest(json.dumps(actual["freshClasses"], sort_keys=True).encode("ascii")),
            "inheritedRowsSha256": digest(json.dumps(actual["inheritedClasses"], sort_keys=True).encode("ascii"))}


def prepare_inputs(owners, cfg, root):
    root.mkdir()
    n, d, scratch = root / "CODE-N", root / "CODE-D", root / "scratch"
    for path in (n, d, scratch):
        path.mkdir()
    upstream = scratch / "sealed-upstream"
    staged = scratch / "native-source # ;"
    classes = root / "classes"
    empty = root / "empty-sourcepath #"
    for path in (upstream, staged, classes, empty):
        path.mkdir()
    roots = {"N": str(n), "D": str(d)}
    sc = {"roots": roots, "scratchRoot": str(scratch), "dataOutputRoot": str(upstream)}
    dc = {"roots": roots, "scratchRoot": str(scratch), "dataOutputRoot": str(staged)}
    caller, reader = owners["caller"], owners["reader"]
    ss = caller._root_bindings(reader, roots, str(scratch), str(upstream))[1]
    ds = caller._root_bindings(reader, roots, str(scratch), str(staged))[1]
    fixture = pinned(cfg["noData"])
    assert len(fixture) == 862 and digest(fixture) == "14182207c6126d8d0ec936ae4985e2fb57343e10d4c1d32024ac4b9d46d291b3"
    source = upstream / "current-input.java"
    write = caller._write_exclusive(reader, sc, str(source), fixture, 1048576,
        str(scratch), ss, "compile-lab-no-data", with_readback_token=True)
    staged_fact = owners["stager"].stage_producer_source(reader=reader,
        io_owner=caller, source_config=sc, source_roots_sha256=ss,
        destination_config=dc, destination_roots_sha256=ds,
        expected_bytes=len(fixture), expected_sha256=digest(fixture),
        expected_source_token=write["readbackToken"])
    specs, writes = {}, []
    for role, _basename in owners["invocations"].SOURCE_ROLES:
        if role == "inputProducer":
            path = staged / "CohortTerminalTelemetryInputV2.java"
            specs[role] = dict(path=str(path), expectedBytes=len(fixture),
                               maxBytes=1048576, sha256=digest(fixture))
        else:
            row = cfg["coreSources"][role]
            raw = pinned(row)
            path = staged / Path(row["path"]).name
            fact = caller._write_exclusive(reader, dc, str(path), raw, 4194304,
                str(scratch), ds, "compile-lab-core-source", with_readback_token=True)
            writes.append((path, raw, fact["readbackToken"], 4194304))
            specs[role] = dict(path=str(path), expectedBytes=len(raw),
                               maxBytes=4194304, sha256=digest(raw))
    facts = owners["byteFacts"].observe_native_code_tools(reader=reader,
        io_owner=caller, observer=owners["observer"], invocation_owner=owners["invocations"],
        source_specs=specs, tool_specs=cfg["toolSpecs"], include_identity_tokens=True)
    target = facts["sourceFacts"]["inputProducer"]
    assert target == {"path": specs["inputProducer"]["path"], "bytes": staged_fact["bytes"], "sha256": staged_fact["sha256"]}
    assert caller._reader_token_identity(reader, staged_fact["destinationToken"],
        "COMPILE_PRODUCER", "compile-stage-token") == facts["sourceTokens"]["inputProducer"][2]
    return dict(root=root, classes=classes, empty=empty, scratch=scratch,
                dc=dc, ds=ds, specs=specs, facts=facts, writes=writes,
                producerToken=staged_fact["destinationToken"], producerRaw=fixture)


def retain_write(owners, prepared, path, raw, maximum):
    fact = owners["caller"]._write_exclusive(owners["reader"], prepared["dc"],
        str(path), raw, maximum, str(prepared["scratch"]), prepared["ds"],
        "compile-lab-request-write", with_readback_token=True)
    prepared["writes"].append((path, raw, fact["readbackToken"], maximum))


def recheck_inputs(owners, cfg, prepared):
    observer, reader, caller = owners["observer"], owners["reader"], owners["caller"]
    api = observer._reader_api(reader)
    for category, specs, key, maximum in (
            ("SOURCE", prepared["specs"], "sourceTokens", 4194304),
            ("TOOL", cfg["toolSpecs"], "toolTokens", 8388608)):
        observer._recheck_fact_map(api, specs, prepared["facts"][key],
            category=category, maximum_limit=maximum, names=tuple(prepared["facts"][key]))
    producer_path = prepared["specs"]["inputProducer"]["path"]
    raw, token = caller._read_with_reader(reader, producer_path, 1048576,
        len(prepared["producerRaw"]), digest(prepared["producerRaw"]),
        "COMPILE_PRODUCER", "compile-producer-readback")
    caller._require_same_input_document(reader, "COMPILE_PRODUCER",
        prepared["producerRaw"], prepared["producerToken"], raw, token,
        "compile-producer-original-writer")
    for path, raw, original, maximum in prepared["writes"]:
        current = caller._readback_output(reader, prepared["dc"], str(path), raw,
            maximum, prepared["ds"], "compile-input-closure")
        caller._require_same_input_document(reader, "COMPILE_INPUT", raw,
            original, raw, current, "compile-input-original-writer")


def run_ps_host(cfg, request, evidence, expected=0, bad_hash=False):
    ps, bridge, module = cfg["powershell"], cfg["bridge"], cfg["processModule"]
    for row in (ps, bridge, module):
        pinned(row)
    request_raw = request.read_bytes()
    argv = [ps["path"], "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", bridge["path"], "-ModulePath", module["path"],
            "-InvocationPath", str(request), "-ExpectedInvocationSha256",
            "0" * 64 if bad_hash else digest(request_raw), "-ExpectedInvocationBytes",
            str(len(request_raw)), "-EvidenceRoot", str(evidence)]
    receipt = evidence.parent / (evidence.name + "-outer.json")
    assert not evidence.exists() and not receipt.exists()
    assert not receipt.with_suffix(".launch.json").exists()
    assert not receipt.with_suffix(".verified.json").exists()
    started = time.monotonic_ns()
    env = dict(os.environ)
    env["PSModulePath"] = str(Path(ps["path"]).parent / "Modules")
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
    kernel.GetProcessTimes.restype = wintypes.BOOL
    kernel.QueryFullProcessImageNameW.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
    kernel.QueryFullProcessImageNameW.restype = wintypes.BOOL
    record = {"selectedImage": ps["path"], "selectedSha256": ps["sha256"],
              "arguments": argv[1:], "deadlineMs": 180000, "originalOuterExitObserved": False}
    process = subprocess.Popen(argv, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, cwd=evidence.parent, env=env)
    stdout, stderr = b"", b""
    try:
        record["pid"] = process.pid
        save(receipt.with_suffix(".launch.json"), record)
        handle = wintypes.HANDLE(int(process._handle))
        times = [wintypes.FILETIME() for _ in range(4)]
        assert kernel.GetProcessTimes(handle, *[ctypes.byref(t) for t in times])
        record["creationFileTime"] = (times[0].dwHighDateTime << 32) | times[0].dwLowDateTime
        size = wintypes.DWORD(32768)
        image = ctypes.create_unicode_buffer(size.value)
        assert kernel.QueryFullProcessImageNameW(handle, 0, image, ctypes.byref(size))
        assert Path(image.value).resolve() == Path(ps["path"]).resolve()
        record["observedImage"] = image.value
        save(receipt.with_suffix(".verified.json"), record)
        stdout, stderr = process.communicate(timeout=180)
    except BaseException as error:
        operation = time.monotonic_ns() - started
        cleanup = time.monotonic_ns()
        cleanup_error = None
        capture_complete = False
        try:
            if process.poll() is None:
                process.kill()  # Original outer Popen handle only; never a PID lookup.
            stdout, stderr = process.communicate(timeout=15)
            capture_complete = True
        except BaseException as failure:
            cleanup_error = type(failure).__name__
            stdout = getattr(failure, "output", None) or getattr(error, "output", None) or stdout
            stderr = getattr(failure, "stderr", None) or getattr(error, "stderr", None) or stderr
        finally:
            try:
                observed_exit = process.poll()
            except BaseException as failure:
                observed_exit = None
                cleanup_error = cleanup_error or type(failure).__name__
            record.update(status="FAIL_NOT_PASS_NATIVE_CLOSURE_UNPROVEN", failureType=type(error).__name__,
                          operationNanos=operation, cleanupNanos=time.monotonic_ns() - cleanup,
                          cleanupFailureType=cleanup_error, captureComplete=capture_complete,
                          exitCode=observed_exit, originalOuterExitObserved=observed_exit is not None,
                          stdoutAvailableBytes=len(stdout), stderrAvailableBytes=len(stderr),
                          stdoutRetainedBytes=min(len(stdout), 1048576), stderrRetainedBytes=min(len(stderr), 1048576))
            save(receipt, record)
            (evidence.parent / (evidence.name + "-stdout.bin")).write_bytes(stdout[:1048576])
            (evidence.parent / (evidence.name + "-stderr.bin")).write_bytes(stderr[:1048576])
        raise
    excessive = len(stdout) > 1048576 or len(stderr) > 1048576
    record.update(status="FAIL_NOT_PASS_OUTPUT_LIMIT" if excessive else "EXIT_OBSERVED", exitCode=process.returncode,
                  originalOuterExitObserved=process.poll() is not None,
                  operationNanos=time.monotonic_ns() - started, cleanupNanos=None,
                  stdoutBytes=len(stdout), stderrBytes=len(stderr), captureComplete=True,
                  stdoutRetainedBytes=min(len(stdout), 1048576), stderrRetainedBytes=min(len(stderr), 1048576))
    save(receipt, record)
    (evidence.parent / (evidence.name + "-stdout.bin")).write_bytes(stdout[:1048576])
    (evidence.parent / (evidence.name + "-stderr.bin")).write_bytes(stderr[:1048576])
    assert not excessive, "outer test host output exceeded the post-capture limit"
    assert process.returncode == expected
    if expected == 0:
        assert stdout == b"Verifier current CODE compile contract status=PASS\r\n" and stderr == b""
    else:
        assert stdout == b"" and stderr.startswith(b"Verifier current CODE compile contract FAIL:")
    for row in (ps, bridge, module):
        pinned(row)
    return record


def execute(cfg, root, mode):
    owners = load_owners(cfg)
    jars = inspect_jars(cfg)
    reference = inventory(owners, cfg, cfg["classProfile"]["freshRoot"], jars)
    save(root / "reference-code-closure.json", reference)
    prepared = prepare_inputs(owners, cfg, root / "positive")
    source_list = Path(prepared["dc"]["dataOutputRoot"]) / "sources.txt"
    raw = owners["invocations"].source_list_bytes(
        {name: row["path"] for name, row in prepared["specs"].items()})
    retain_write(owners, prepared, source_list, raw, 65536)
    executable, arguments, timeout = owners["invocations"].compile_invocation(
        javac=cfg["toolSpecs"]["nativeJavac"]["path"], classes_root=str(prepared["classes"]),
        inherited_classes_root=cfg["classProfile"]["inheritedRoot"],
        gwt_user=cfg["jars"][0]["path"], gwt_dev=cfg["jars"][1]["path"],
        empty_sourcepath=str(prepared["empty"]), source_list=str(source_list))
    assert timeout == 60000 and not list(prepared["classes"].iterdir())
    request = source_list.parent / "invocation.json"
    request_obj = dict(FilePath=executable, Arguments=list(arguments),
        WorkingDirectory=str(prepared["root"]), TimeoutMilliseconds=timeout,
        MaxStdoutBytes=4096, MaxStderrBytes=4096)
    retain_write(owners, prepared, request,
        (json.dumps(request_obj, indent=2) + "\n").encode("ascii"), 65536)
    recheck_inputs(owners, cfg, prepared)
    host = root / "compile-host"
    outer = run_ps_host(cfg, request, host)
    result = json.loads((host / "result.json").read_bytes())
    actual = result["rawResult"]
    assert result["status"] == "PASS" and actual["ExitCode"] == 0
    assert actual["Stdout"] == "" and actual["Stderr"] == "" and actual["TerminationProven"] is True
    native = actual["CaptureFacts"]["NativeContainment"]
    assert all(native[k] is True for k in ("RootExitObserved", "JobEmpty", "ReadHandlesClosed", "LeaseClosed", "CleanupProven"))
    assert native["NativeExitCode"] == 0 and native["ProcessExitCode"] == 0
    recheck_inputs(owners, cfg, prepared)
    closed = inventory(owners, cfg, prepared["classes"], jars)
    assert closed == reference
    cases = [{"name": "current-code-native-compile", "status": "PASS", "closure": closed,
              "compilerAttempted": True, "outerReceipt": outer,
              "compileOperationMilliseconds": result["operationMilliseconds"],
              "compileCleanupMilliseconds": result["cleanupMilliseconds"]}]
    if mode == "Suite":
        guarded = prepare_inputs(owners, cfg, root / "source-guard")
        source = Path(guarded["specs"]["study"]["path"])
        raw = source.read_bytes()
        preserved = guarded["scratch"] / "original-study-preserved.java"
        old_id = source.stat().st_ino
        source.rename(preserved)
        with source.open("xb") as output:
            output.write(raw)
        assert source.stat().st_ino != old_id == preserved.stat().st_ino
        assert digest(source.read_bytes()) == guarded["specs"]["study"]["sha256"]
        try:
            recheck_inputs(owners, cfg, guarded)
        except owners["observer"].CallerObservationError as error:
            assert error.code == "SOURCE_CHANGED"
        else:
            raise AssertionError("late same-byte Source replacement was accepted")
        cases.append({"name": "late-source-identity-rejection", "status": "PASS_EXPECTED_REJECTION",
                      "compilerAttempted": False, "rejection": "SOURCE_CHANGED"})
        row = next(x for x in cfg["classProfile"]["freshRows"]
                   if not Path(x["path"]).name.startswith("CohortTerminalTelemetryInput"))
        target = prepared["classes"] / row["path"]
        original = target.read_bytes()
        assert digest(original) == row["sha256"]
        retained = root / "original-class-preserved.class"
        target.rename(retained)
        altered = original[:-1] + bytes([original[-1] ^ 1])
        with target.open("xb") as output:
            output.write(altered)
        try:
            inventory(owners, cfg, prepared["classes"], jars)
        except owners["caller"].CallerError as error:
            assert error.code == "READER_SHA256"
        else:
            raise AssertionError("mutated class was accepted against independent rows")
        target.rename(root / "rejected-class-preserved.class")
        retained.rename(target)
        assert digest(target.read_bytes()) == row["sha256"]
        restored = inventory(owners, cfg, prepared["classes"], jars)
        assert restored == closed
        cases.append({"name": "class-hash-rejection-and-restoration", "status": "PASS_EXPECTED_REJECTION",
                      "rejection": "READER_SHA256", "restoredClosure": restored})
        refused = root / "wrong-request-host"
        wrong_outer = run_ps_host(cfg, request, refused, expected=2, bad_hash=True)
        assert not refused.exists()
        cases.append({"name": "request-pin-before-launch-rejection", "status": "PASS_EXPECTED_REJECTION",
                      "compilerAttempted": False, "evidenceNamespaceCreated": False, "outerReceipt": wrong_outer})
    recheck_inputs(owners, cfg, prepared)
    inspect_jars(cfg)
    for row in cfg["modules"].values():
        pinned(row)
    for row in cfg["coreSources"].values():
        pinned(row)
    pinned(cfg["noData"])
    save(root / "cases.json", {"status": "PASS", "mode": mode, "caseCount": len(cases),
        "cases": cases, "normalGenuine": "UNSPENT_GATED", "sixSourceCompile": "CODE_NO_DATA_ONLY",
        "nativeStudyExecuted": False, "fullJdkClosure": "NOT_ATTESTED",
        "atomicCheckToLaunch": False, "roleCaps": "TEST_ONLY_NOT_NORMAL_POLICY"})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True)
    parser.add_argument("--config-sha256", required=True)
    parser.add_argument("--evidence-root", required=True)
    parser.add_argument("--mode", choices=("Smoke", "Suite"), default="Smoke")
    args = parser.parse_args()
    assert sys.platform == "win32" and sys.flags.isolated and sys.dont_write_bytecode and sys.flags.optimize == 0
    config = Path(args.config)
    raw = config.read_bytes()
    assert len(raw) <= 8388608 and digest(raw) == args.config_sha256
    cfg = json.loads(raw)
    root = Path(args.evidence_root)
    assert not root.exists() and root.resolve().is_relative_to(Path(tempfile.gettempdir()).resolve())
    root.mkdir()
    try:
        execute(cfg, root, args.mode)
        assert config.read_bytes() == raw
    except BaseException as error:
        save(root / "failure.json", {"status": "FAIL_NOT_PASS", "type": type(error).__name__,
             "message": str(error), "normalGenuine": "UNSPENT_GATED"})
        raise
    print("Current CODE native compile mode=" + args.mode + " status=PASS")


if __name__ == "__main__":
    main()
