"""Expected refusal of the current CODE-only entrypoint; no normal admission.

The external lab caller authenticates this config and its independent class
profile. Synthetic argument digests have no upstream authority. The unconditional
main hold runs before parsing, so this proves dispatch and refusal only.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import types


EXPECTED_STDERR = ('Exception in thread "main" java.lang.IllegalStateException: '
    'ADOPTED_SNAPSHOT_COMPONENT_ONLY\r\n\tat '
    'com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy.main'
    '(CohortTerminalTelemetryStudy.java:29)\r\n')


def execute(cfg, root, mode):
    row = cfg["compileLab"]
    raw = Path(row["path"]).read_bytes()
    assert len(raw) == row["bytes"] and hashlib.sha256(raw).hexdigest() == row["sha256"]
    lab = types.ModuleType("_current_held_compile_lab")
    lab.__file__ = row["path"]
    exec(compile(raw, row["path"], "exec", dont_inherit=True), lab.__dict__)
    owners = lab.load_owners(cfg)
    jars = lab.inspect_jars(cfg)
    reference = lab.inventory(owners, cfg, cfg["classProfile"]["freshRoot"], jars)
    lab.save(root / "class-closure-before.json", reference)
    prepared = lab.prepare_inputs(owners, cfg, root / "dispatch")
    # The prepared Source bundle retains its original writer identities. Runtime
    # consumes the independently checked, already compiled CODE profile only.
    output = prepared["root"] / "held-output"
    output.mkdir()
    executable, arguments, recipe_timeout = owners["invocations"].native_invocation(
        java=cfg["toolSpecs"]["nativeJava"]["path"],
        classes_root=cfg["classProfile"]["freshRoot"],
        inherited_classes_root=cfg["classProfile"]["inheritedRoot"],
        gwt_user=cfg["jars"][0]["path"], gwt_dev=cfg["jars"][1]["path"],
        run_id="65000000-0000-4000-8000-000000000001",
        native_source_manifest_sha256="0" * 64, wire_contract_sha256="1" * 64,
        input_context_sha256="2" * 64, output_directory=str(output))
    assert recipe_timeout == 480000 and len(arguments) == 10
    request_obj = dict(FilePath=executable, Arguments=list(arguments),
        WorkingDirectory=str(prepared["root"]), TimeoutMilliseconds=10000,
        MaxStdoutBytes=4096, MaxStderrBytes=4096)

    def frame(name, obj):
        path = Path(prepared["dc"]["dataOutputRoot"]) / (name + ".json")
        lab.retain_write(owners, prepared, path,
            (json.dumps(obj, indent=2) + "\n").encode("ascii"), 65536)
        return path

    request = frame("held-main", request_obj)
    lab.recheck_inputs(owners, cfg, prepared)
    assert lab.inventory(owners, cfg, cfg["classProfile"]["freshRoot"], jars) == reference
    assert not list(output.iterdir())
    host = root / "held-host"
    outer = lab.run_ps_host(cfg, request, host, role="HeldMain")
    result = json.loads((host / "result.json").read_bytes())
    actual = result["rawResult"]
    assert result["status"] == "PASS" and result["classification"] == "PASS_EXPECTED_REFUSAL"
    assert actual["FilePath"] == executable and actual["Arguments"] == list(arguments)
    assert actual["ExitCode"] == 1 and actual["Stdout"] == "" and actual["Stderr"] == EXPECTED_STDERR
    native = actual["CaptureFacts"]["NativeContainment"]
    assert all(native[k] is True for k in (
        "RootExitObserved", "JobEmpty", "ReadHandlesClosed", "LeaseClosed", "CleanupProven"))
    assert native["TerminationRequested"] is False
    assert native["NativeExitCode"] == 1 and native["ProcessExitCode"] == 1
    assert result["callerJobCleanup"]["disposed"] is True
    assert result["callerJobCleanup"]["terminationRequested"] is False
    assert not list(output.iterdir())
    lab.recheck_inputs(owners, cfg, prepared)
    assert lab.inventory(owners, cfg, cfg["classProfile"]["freshRoot"], jars) == reference
    lab.inspect_jars(cfg)
    cases = [{"name": "held-code-entrypoint-refusal", "status": "PASS_EXPECTED_REFUSAL",
        "nativeExitCode": 1, "stderrBytes": len(EXPECTED_STDERR.encode("ascii")),
        "outputEmpty": True, "outerReceipt": outer,
        "operationMilliseconds": result["operationMilliseconds"],
        "cleanupMilliseconds": result["cleanupMilliseconds"]}]
    if mode == "Suite":
        invalid_timeout = dict(request_obj, TimeoutMilliseconds=480000)
        invalid_arguments = dict(request_obj, Arguments=["-Xmx1024m", "-cp", arguments[2],
            "com.lushprojects.circuitjs1.client.OtherStudy"] + list(arguments[4:]))
        for name, obj, bad_hash in (
                ("normal-timeout-not-canary", invalid_timeout, False),
                ("wrong-entrypoint", invalid_arguments, False),
                ("wrong-frame-digest", request_obj, True)):
            negative = frame(name, obj)
            lab.recheck_inputs(owners, cfg, prepared)
            target = root / name
            receipt = lab.run_ps_host(cfg, negative, target, expected=2,
                                      bad_hash=bad_hash, role="HeldMain")
            assert not target.exists() and not list(output.iterdir())
            cases.append({"name": name, "status": "PASS_EXPECTED_REJECTION",
                          "nativeAttempted": False, "outerReceipt": receipt})
    lab.recheck_inputs(owners, cfg, prepared)
    after = lab.inventory(owners, cfg, cfg["classProfile"]["freshRoot"], jars)
    assert after == reference and not list(output.iterdir())
    lab.inspect_jars(cfg)
    lab.save(root / "class-closure-after.json", after)
    lab.pinned(row)
    return {"status": "PASS", "mode": mode,
        "caseCount": len(cases), "cases": cases, "recipeTimeoutMilliseconds": recipe_timeout,
        "testTimeoutMilliseconds": 10000, "testStreamCaps": 4096,
        "syntheticArguments": "NO_UPSTREAM_AUTHORITY", "argumentParsing": "NOT_REACHED",
        "normalGenuine": "UNSPENT_GATED", "normalAdmission": "NOT_QUALIFIED"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True)
    parser.add_argument("--config-sha256", required=True)
    parser.add_argument("--evidence-root", required=True)
    parser.add_argument("--mode", choices=("Smoke", "Suite"), default="Smoke")
    args = parser.parse_args()
    assert sys.platform == "win32" and sys.flags.isolated and sys.dont_write_bytecode and sys.flags.optimize == 0
    config = Path(args.config)
    raw = config.read_bytes()
    assert len(raw) <= 8388608 and hashlib.sha256(raw).hexdigest() == args.config_sha256
    cfg = json.loads(raw)
    root = Path(args.evidence_root)
    assert not root.exists() and root.resolve().is_relative_to(Path(tempfile.gettempdir()).resolve())
    root.mkdir()
    try:
        cases = execute(cfg, root, args.mode)
        assert config.read_bytes() == raw
        with (root / "cases.json").open("x", encoding="ascii") as output:
            json.dump(cases, output, indent=2)
            output.write("\n")
    except BaseException as error:
        with (root / "failure.json").open("x", encoding="ascii") as output:
            json.dump({"status": "FAIL_NOT_PASS", "type": type(error).__name__,
                "message": str(error), "normalGenuine": "UNSPENT_GATED"}, output, indent=2)
        raise
    print("Current CODE held main mode=" + args.mode + " status=PASS")


if __name__ == "__main__":
    main()
