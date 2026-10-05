"""Single serial entrypoint for the remaining Q30 qualification phases.

Each named phase runs as a child of the maintained Windows Job Object runner.
Application receipts keep their existing schema 1 and electrical limits; this
file records only the host operation, cleanup, source binding, and phase result.
"""
from __future__ import annotations

import argparse
import ctypes
import hashlib
import importlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import traceback

from receipts import LIMITS, ReceiptError, validate_case, write_exclusive


PHASES = {
    "audit-retained": 180_000,
    "release": 180_000,
    "private-prepare": 240_000,
    "private-build": 1_100_000,
    "private-menu": 1_200_000,
    "private-archive": 360_000,
    "visible": 1_200_000,
}
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
HASHES = {"release", "batchResult", "controllerResult", "controllerPublication", "oldPlanManifest"}
REPARSE_POINT = 0x400
INVALID_ATTRIBUTES = 0xFFFFFFFF


class QualificationError(RuntimeError):
    pass


def _need(ok, message):
    if not ok:
        raise QualificationError(message)


def _json(raw: bytes, label: str) -> dict:
    def pairs(rows):
        result = {}
        for key, value in rows:
            _need(key not in result, f"{label} has a duplicate JSON key")
            result[key] = value
        return result

    try:
        value = json.loads(raw.decode("utf-8-sig"), object_pairs_hook=pairs,
                           parse_constant=lambda item: (_ for _ in ()).throw(
                               QualificationError(f"{label} contains {item}")))
    except (UnicodeError, json.JSONDecodeError) as error:
        raise QualificationError(f"{label} is not valid UTF-8 JSON: {error}") from error
    _need(type(value) is dict, f"{label} must be an object")
    return value


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _win_attributes(path: Path):
    if os.name != "nt":
        return None
    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel32.GetFileAttributesW.restype = ctypes.c_uint32
    kernel32.GetFileAttributesW.argtypes = [ctypes.c_wchar_p]
    value = int(kernel32.GetFileAttributesW(str(path)))
    if value == INVALID_ATTRIBUTES:
        raise OSError(ctypes.get_last_error(), f"GetFileAttributesW failed for {path}")
    return value


def _plain_path(path: Path, *, file: bool | None = None) -> Path:
    """Resolve an existing path and reject symlink/reparse components."""
    absolute = path.absolute()
    current = Path(absolute.anchor)
    for part in absolute.parts[1:]:
        current = current / part
        try:
            if current.is_symlink():
                raise QualificationError(f"symlink in evidence path: {current}")
            attrs = _win_attributes(current)
            if attrs is not None and attrs & REPARSE_POINT:
                raise QualificationError(f"reparse point in evidence path: {current}")
        except OSError as error:
            raise QualificationError(f"cannot inspect evidence path {current}: {error}") from error
    try:
        resolved = path.resolve(strict=True)
    except OSError as error:
        raise QualificationError(f"path is unavailable: {path}: {error}") from error
    if file is True:
        _need(resolved.is_file(), f"expected a regular file: {path}")
    elif file is False:
        _need(resolved.is_dir(), f"expected a directory: {path}")
    return resolved


def _absolute(value, label) -> Path:
    _need(isinstance(value, str) and value and not any(ord(c) < 32 for c in value),
          f"{label} must be a plain path string")
    path = Path(value)
    _need(path.is_absolute(), f"{label} must be absolute")
    return path


def load_manifest(manifest_path: Path):
    path = _plain_path(manifest_path, file=True)
    raw = path.read_bytes()
    _need(0 < len(raw) <= 2 * 1024 * 1024, "local manifest size is unsupported")
    manifest = _json(raw, "local qualification manifest")
    expected = {"schema", "repo", "sourceSnapshot", "sourceBinding", "tools", "tempRoot",
                "retainedEvidence", "retainedEvidenceSha256"}
    _need(set(manifest) in (expected, expected | {"workflowSources"}),
          "local qualification manifest fields differ")
    _need(type(manifest["schema"]) is int and manifest["schema"] == 1,
          "local qualification manifest schema must be integer 1")
    repo = _absolute(manifest["repo"], "repo")
    snapshot = manifest["sourceSnapshot"]
    _need(type(snapshot) is dict and set(snapshot) == {"path", "sha256"},
          "sourceSnapshot fields differ")
    _absolute(snapshot["path"], "sourceSnapshot.path")
    _need(isinstance(snapshot["sha256"], str) and SHA256.fullmatch(snapshot["sha256"]),
          "sourceSnapshot.sha256 is malformed")
    binding = manifest["sourceBinding"]
    _need(type(binding) is dict and set(binding) == {
        "baseHead", "observedHead", "sourceIdentity", "sourceFiles", "reuseBoundary"},
        "sourceBinding fields differ")
    _need(isinstance(binding["baseHead"], str) and re.fullmatch(r"[0-9a-f]{40}", binding["baseHead"]),
          "sourceBinding.baseHead is malformed")
    _need(isinstance(binding["observedHead"], str) and re.fullmatch(r"[0-9a-f]{40}", binding["observedHead"]),
          "sourceBinding.observedHead is malformed")
    _need(isinstance(binding["sourceIdentity"], str) and SHA256.fullmatch(binding["sourceIdentity"]),
          "sourceBinding.sourceIdentity is malformed")
    _need(type(binding["sourceFiles"]) is int and binding["sourceFiles"] == 1355,
          "sourceBinding.sourceFiles must preserve the frozen 1,355 inputs")
    _need(binding["reuseBoundary"] == "exact raw1355 source files; new docs workflow owners excluded",
          "sourceBinding reuse boundary differs")
    tools = manifest["tools"]
    _need(type(tools) is dict and set(tools) == {"python", "powershell", "javaHome", "gwtHome", "deps", "gwtJars"},
          "tools fields differ")
    for name in ("python", "powershell", "javaHome", "gwtHome", "deps"):
        _absolute(tools[name], "tools." + name)
    _need(type(tools["gwtJars"]) is list and len(tools["gwtJars"]) == 9,
          "tools.gwtJars must contain nine pinned jars")
    for item in tools["gwtJars"]:
        _need(type(item) is dict and set(item) == {"path", "sha256", "size"},
              "a GWT jar pin has unexpected fields")
        _absolute(item["path"], "GWT jar path")
        _need(isinstance(item["sha256"], str) and SHA256.fullmatch(item["sha256"]) and
              type(item["size"]) is int and item["size"] > 0, "a GWT jar pin is malformed")
    temp_root = _absolute(manifest["tempRoot"], "tempRoot")
    retained = manifest["retainedEvidence"]
    _need(type(retained) is dict and set(retained) == {
        "release", "cohortRoot", "oldPlanManifest", "quietWindowEnd"},
        "retainedEvidence fields differ")
    for name in ("release", "cohortRoot", "oldPlanManifest"):
        _absolute(retained[name], "retainedEvidence." + name)
    _need(isinstance(retained["quietWindowEnd"], str) and retained["quietWindowEnd"].endswith("Z"),
          "retainedEvidence.quietWindowEnd must be UTC-Z text")
    pins = manifest["retainedEvidenceSha256"]
    _need(type(pins) is dict and set(pins) == HASHES and
          all(isinstance(item, str) and SHA256.fullmatch(item) for item in pins.values()),
          "retainedEvidenceSha256 fields or hashes differ")
    if "workflowSources" in manifest:
        sources = manifest["workflowSources"]
        _need(type(sources) is dict and bool(sources), "workflowSources must be a nonempty source pin map")
        for name, digest in sources.items():
            _need(isinstance(name, str) and re.fullmatch(r"[A-Za-z0-9_]+\.py", name) and
                  isinstance(digest, str) and SHA256.fullmatch(digest),
                  "workflowSources must map plain Python basenames to SHA-256")
    return manifest, raw, path, repo, temp_root


def _verify_source_files(repo: Path, snapshot: dict, binding: dict):
    _need(type(snapshot.get("schema")) is int and snapshot.get("schema") == 1 and
          snapshot.get("baseHead") == binding["baseHead"] and
          snapshot.get("sourceIdentity") == binding["sourceIdentity"],
          "source snapshot schema or binding differs")
    rows = snapshot.get("files")
    _need(type(rows) is list and type(binding.get("sourceFiles")) is int and
          len(rows) == binding["sourceFiles"],
          "source snapshot file count differs from its source binding")
    seen, checked = set(), 0
    for row in rows:
        _need(type(row) is dict and set(row) == {"path", "sha256", "size"},
              "source snapshot row fields differ")
        name = row["path"]
        _need(isinstance(name, str) and "\\" not in name and not PurePosixPath(name).is_absolute(),
              "source snapshot contains a noncanonical path")
        relative = PurePosixPath(name)
        _need(bool(relative.parts) and str(relative) == name and
              all(part not in ("", ".", "..") for part in relative.parts) and
              ":" not in relative.parts[0], "source snapshot path escaped its repository")
        _need(name not in seen, "source snapshot contains a duplicate path")
        seen.add(name)
        _need(isinstance(row["sha256"], str) and SHA256.fullmatch(row["sha256"]) and
              type(row["size"]) is int and row["size"] >= 0, "source snapshot row hash or size is malformed")
        member = _plain_path(repo.joinpath(*relative.parts), file=True)
        try:
            member.relative_to(repo)
            data = member.read_bytes()
        except (ValueError, OSError) as error:
            raise QualificationError(f"source file escaped or could not be read: {name}") from error
        _need(len(data) == row["size"] and _sha(data) == row["sha256"],
              f"source snapshot file changed: {name}")
        checked += 1
    return {"fileCount": checked, "baseHead": snapshot["baseHead"],
            "sourceIdentity": snapshot["sourceIdentity"]}


def _read_pinned_json(path: Path, digest: str, label: str):
    file = _plain_path(path, file=True)
    raw = file.read_bytes()
    _need(_sha(raw) == digest, f"retained evidence pin changed: {label}")
    return _json(raw, label), file


def _validate_batch(spec: dict, binding: dict, batch: dict):
    _need(spec.get("sourceIdentity") == binding["sourceIdentity"] and
          spec.get("baseHead") == binding["baseHead"] and
          spec.get("planSha256") == binding["planSha256"] and
          spec.get("limits") == LIMITS and type(spec.get("steps")) is list and
          len(spec["steps"]) == 77,
          "retained cohort spec differs from its pinned source/plan/caps")
    expected_seeds = [step.get("rootSeed") for step in spec["steps"]]
    _need(all(isinstance(seed, str) for seed in expected_seeds) and len(set(expected_seeds)) == 77,
          "retained cohort seeds are malformed or duplicated")
    rows = batch.get("rows")
    _need(batch.get("status") == "PASS_COLD77" and batch.get("complete") is True and
          batch.get("qualified") is True and type(rows) is list and len(rows) == 77 and
          batch.get("publishedPackageSizes") == list(range(20, 41)),
          "retained cold cohort is not a complete 77-root PASS")
    maxima = {"elapsedMs": 0, "workUnits": 0, "maxActiveOperationMs": 0,
              "maxCoordinatorAdvanceMs": 0}
    for expected, row in zip(expected_seeds, rows):
        _need(type(row) is dict and row.get("seed") == expected and row.get("status") == "PASS",
              "retained cohort row order or status differs")
        receipt = row.get("receipt")
        _need(type(receipt) is dict and receipt.get("sourceIdentity") == binding["sourceIdentity"] and
              receipt.get("baseHead") == binding["baseHead"] and
              receipt.get("planSha256") == binding["planSha256"] and
              receipt.get("sourceInputsUnchanged") is True and
              receipt.get("serialRunner", {}).get("status") == "PASS" and
              validate_case(receipt) == "PASS", "a retained case receipt failed the unchanged validator")
        app = receipt["application"]
        for key in maxima:
            maxima[key] = max(maxima[key], app[key])
    return maxima


def _verify_cohort(manifest: dict):
    retained, pins = manifest["retainedEvidence"], manifest["retainedEvidenceSha256"]
    root = _plain_path(Path(retained["cohortRoot"]), file=False)
    evidence = {}
    for name, filename in (("batchResult", "batch-result.json"),
                            ("controllerResult", "controller-result.json"),
                            ("controllerPublication", "controller-publication.json")):
        value, path = _read_pinned_json(root / filename, pins[name], name)
        evidence[name] = value
    spec_path = _plain_path(root / "batch-spec.json", file=True)
    spec = _json(spec_path.read_bytes(), "retained batch spec")
    old_plan, old_plan_path = _read_pinned_json(Path(retained["oldPlanManifest"]),
                                                 pins["oldPlanManifest"], "old plan manifest")
    _need(old_plan_path == _plain_path(Path(retained["oldPlanManifest"]), file=True),
          "old plan manifest path changed")
    binding = old_plan.get("inputBinding")
    _need(type(binding) is dict and binding.get("sourceIdentity") == manifest["sourceBinding"]["sourceIdentity"] and
          binding.get("baseHead") == manifest["sourceBinding"]["baseHead"],
          "old candidate manifest source binding differs")
    plan_sha = binding.get("planSha256")
    _need(isinstance(plan_sha, str) and SHA256.fullmatch(plan_sha), "old candidate plan hash is malformed")
    batch = evidence["batchResult"]
    batch_binding = {"sourceIdentity": binding["sourceIdentity"], "baseHead": binding["baseHead"],
                     "planSha256": plan_sha}
    maxima = _validate_batch(spec, batch_binding, batch)
    rows = batch["rows"]
    controller = evidence["controllerResult"]
    publication = evidence["controllerPublication"]
    operation = controller.get("operation", {})
    cleanup = operation.get("cleanup", {}) if type(operation) is dict else {}
    _need(controller.get("schema") == 1 and controller.get("status") == "PASS_COLD77_CONTROLLER" and
          controller.get("qualified") is True and controller.get("errors") == [] and
          controller.get("candidateManifestSha256") == pins["oldPlanManifest"] and
          controller.get("taskRoot") == str(root) and controller.get("operation", {}).get("exitCode") == 0 and
          operation.get("operationElapsedMs", 0) <= controller.get("operationLimitMs", -1) and
          controller.get("operationLimitMs") == 15_043_439 and
          controller.get("cleanupLimitMs") == 15_000 and
          cleanup.get("cleanupVerified") is True and cleanup.get("jobActiveProcesses") == 0 and
          cleanup.get("processSignaled") is True and cleanup.get("drainsComplete") is True and
          cleanup.get("jobAccountingDrainSucceeded") is True and cleanup.get("liveDrainers") == 0 and
          controller.get("retainedSequence", {}).get("complete") is True and
          controller.get("retainedSequence", {}).get("outcome") == "PASS" and
          controller.get("retainedSequence", {}).get("rows") == rows and
          controller.get("windowEndUtc") == manifest["retainedEvidence"]["quietWindowEnd"],
          "retained cold controller operation, cleanup, or copied rows failed")
    _need(publication.get("status") == "PASS_COLD77_CONTROLLER" and
          publication.get("qualified") is True and publication.get("withinFinalizationDeadline") is True,
          "retained cold controller publication failed")
    return {"status": "PASS", "roots": len(rows), "maxima": maxima,
            "controllerExitCode": controller["operation"]["exitCode"],
            "controllerCleanupVerified": controller["operation"]["cleanup"]["cleanupVerified"],
            "retainedReleaseStatus": "BLOCKED_R5_PRESERVED"}


def audit_retained(manifest: dict):
    repo = _plain_path(Path(manifest["repo"]), file=False)
    snapshot_path = _plain_path(Path(manifest["sourceSnapshot"]["path"]), file=True)
    raw = snapshot_path.read_bytes()
    _need(_sha(raw) == manifest["sourceSnapshot"]["sha256"], "source snapshot digest changed")
    snapshot = _json(raw, "source snapshot")
    binding = manifest["sourceBinding"]
    source = _verify_source_files(repo, snapshot, binding)
    pins = manifest["retainedEvidenceSha256"]
    release, _ = _read_pinned_json(Path(manifest["retainedEvidence"]["release"]), pins["release"],
                                   "historical release")
    _need(release.get("schema") == 1 and release.get("status") == "BLOCKED" and
          release.get("terminationUsed") is False,
          "historical R5 release receipt changed or no longer records the preserved block")
    old_plan, _ = _read_pinned_json(Path(manifest["retainedEvidence"]["oldPlanManifest"]),
                                    pins["oldPlanManifest"], "old plan manifest")
    _need(old_plan.get("schema") == 1 and old_plan.get("inputBinding", {}).get("sourceIdentity") ==
          binding["sourceIdentity"], "retained old plan manifest binding differs")
    # The nine toolchain jars are part of the private build's frozen tool binding.
    jar_checks = []
    for jar in manifest["tools"]["gwtJars"]:
        path = _plain_path(Path(jar["path"]), file=True)
        data = path.read_bytes()
        _need(len(data) == jar["size"] and _sha(data) == jar["sha256"],
              "pinned GWT jar changed: " + path.name)
        jar_checks.append(path.name)
    cohort = _verify_cohort(manifest)
    return {"status": "PASS", "source": source, "observedHeadLabel": binding["observedHead"],
            "sourceSnapshotSha256": manifest["sourceSnapshot"]["sha256"],
            "retainedEvidenceSha256": dict(pins), "gwtJars": jar_checks,
            "historicalRelease": "BLOCKED preserved", "cohort": cohort}


def _phase_result_path(output: Path):
    return output / "phase.json"


def _worker(manifest_path: Path, phase: str, output: Path):
    manifest, manifest_raw, _, _, temp_root = load_manifest(manifest_path)
    _need(phase in PHASES, "unsupported phase")
    _need(_plain_path(Path.cwd(), file=False) == _plain_path(output, file=False),
          "worker current directory differs from its owned output root")
    _need(_plain_path(output.parent, file=False) == _plain_path(temp_root, file=False),
          "phase output must be a direct child of the task OS-temp root")
    workflow_before = _workflow_hashes(manifest, phase)
    result = {"schema": 1, "phase": phase, "status": "FAIL", "startedUtc": _utc_now(),
              "sourceBinding": dict(manifest["sourceBinding"])}
    try:
        if phase == "audit-retained":
            details = audit_retained(manifest)
        elif phase == "release":
            details = importlib.import_module("audit_release").audit(manifest, output / "release-output")
        elif phase.startswith("private-"):
            owner = importlib.import_module("private_qualification")
            method = {"private-prepare": owner.prepare, "private-build": owner.build,
                      "private-menu": owner.menu, "private-archive": owner.archive}[phase]
            details = method(manifest, output)
        elif phase == "visible":
            import visible_repair
            visible_output = output / "visible-output"
            sys.argv = [str(Path(visible_repair.__file__).resolve()), "--app",
                        str(temp_root / "private-app"), "--output", str(visible_output),
                        "--powershell", manifest["tools"]["powershell"], "--deps", manifest["tools"]["deps"]]
            result["visibleExitCode"] = visible_repair.main()
            details_path = visible_output / "result.json"
            _need(details_path.is_file(), "visible repair driver did not write result.json")
            details = _json(details_path.read_bytes(), "visible repair result")
            details["status"] = "PASS" if details.get("outcome") == "PASS" and result["visibleExitCode"] == 0 else (
                "BLOCKED" if details.get("outcome") == "BLOCKED" else "FAIL")
            result["visibleResult"] = {"path": str(details_path), "sha256": _sha(details_path.read_bytes())}
        else:
            raise QualificationError("phase dispatcher has no owner")
        _need(type(details) is dict and details.get("status") in {"PASS", "FAIL", "BLOCKED"},
              "phase owner must return a PASS/FAIL/BLOCKED result object")
        result.update(status=details["status"], details=details)
    except BaseException as error:
        result.update(status="BLOCKED" if isinstance(error, (ImportError, OSError)) else "FAIL",
                      error=f"{type(error).__name__}: {error}", traceback=traceback.format_exc())
    result["finishedUtc"] = _utc_now()
    result["manifestSha256"] = _sha(manifest_raw)
    try:
        workflow_after = _workflow_hashes(manifest, phase)
    except BaseException as error:
        workflow_after = {"error": f"{type(error).__name__}: {error}"}
        result.update(status="FAIL", error="phase-owner source changed during execution")
    result["workflowSourcesBefore"] = workflow_before
    result["workflowSourcesAfter"] = workflow_after
    if workflow_before != workflow_after:
        result.update(status="FAIL", error="phase-owner source hashes differ before and after execution")
    write_exclusive(output / "worker-result.json", result)
    print(json.dumps({"phase": phase, "status": result["status"]}, sort_keys=True), flush=True)
    return 0 if result["status"] == "PASS" else (2 if result["status"] == "BLOCKED" else 1)


def _utc_now():
    from datetime import datetime, timezone
    return datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")


def _workflow_names(phase: str):
    names = ["qualify.py", "serial_runner.py", "receipts.py", "windows_process_identity.py"]
    if phase == "release":
        names.append("audit_release.py")
    if phase.startswith("private-"):
        names.append("private_qualification.py")
        if phase == "private-menu":
            names.append("private_menu.py")
    if phase == "visible":
        names.append("visible_repair.py")
    return tuple(dict.fromkeys(names))


def _workflow_hashes(manifest: dict, phase: str):
    pinned = manifest.get("workflowSources")
    _need(type(pinned) is dict and bool(pinned), "phase execution requires frozen workflow source pins")
    required = _workflow_names(phase)
    if pinned is not None:
        _need(set(required) <= set(pinned),
              "workflowSources omits a phase owner or shared runner")
    hashes = {}
    for name in required:
        source = _plain_path(Path(__file__).parent / name, file=True)
        digest = _sha(source.read_bytes())
        if pinned is not None:
            _need(pinned.get(name) == digest, f"workflow source pin changed: {name}")
        hashes[name] = digest
    return hashes


def run_phase(manifest_path: Path, phase: str, output_root: Path):
    manifest, manifest_raw, manifest_path, _, temp_root = load_manifest(manifest_path)
    _need(phase in PHASES, "unsupported phase")
    _need(output_root.is_absolute(), "phase output must be an absolute new OS-temp directory")
    _need(output_root.parent.resolve(strict=True) == _plain_path(temp_root, file=False),
          "phase output must be a direct child of the task OS-temp root")
    workflow_before = _workflow_hashes(manifest, phase)
    script = Path(__file__).resolve(strict=True)
    python = _plain_path(Path(manifest["tools"]["python"]), file=True)
    argv = [str(python), "-B", str(script), "--worker", "--manifest", str(manifest_path),
            "--phase", phase, "--output", str(output_root)]
    import serial_runner
    host = serial_runner.run_command(argv, output_root, PHASES[phase])
    try:
        workflow_after = _workflow_hashes(manifest, phase)
    except BaseException as error:
        workflow_after = {"error": f"{type(error).__name__}: {error}"}
    worker_path = output_root / "worker-result.json"
    worker = None
    if worker_path.is_file():
        try:
            worker = _json(worker_path.read_bytes(), "phase worker result")
        except (OSError, QualificationError):
            worker = None
    process = host.get("process")
    operation = host.get("operation", {})
    cleanup = host.get("cleanup", {})
    logs = host.get("logs", {})
    host_ok = (host.get("status") == "PASS" and host.get("handlesClosed") is True and
               type(process) is dict and process.get("birthPrecision") == "NATIVE_100NS" and
               type(operation) is dict and operation.get("exitCode") == 0 and
               type(cleanup) is dict and cleanup.get("cleanupVerified") is True and
               cleanup.get("jobActiveProcesses") == 0 and cleanup.get("liveDrainers") == 0 and
               type(logs) is dict and all(type(value) is dict and not value.get("overflow") and
                    not value.get("errors") and not value.get("readerAlive") and value.get("streamClosed")
                    for value in logs.values()))
    manifest_unchanged = manifest_path.read_bytes() == manifest_raw
    worker_identity_matches = bool(worker and worker.get("schema") == 1 and
        worker.get("phase") == phase and worker.get("manifestSha256") == _sha(manifest_raw) and
        worker.get("sourceBinding") == manifest["sourceBinding"] and
        worker.get("workflowSourcesBefore") == workflow_before and
        worker.get("workflowSourcesAfter") == workflow_after)
    status = "PASS" if host_ok and manifest_unchanged and worker_identity_matches and workflow_before == workflow_after and worker.get("status") == "PASS" else (
        "BLOCKED" if worker and worker.get("status") == "BLOCKED" else "FAIL")
    named_gates = {name: (status if name == phase else "NOT_RUN") for name in PHASES}
    envelope = {
        "schema": 1,
        "phase": phase,
        "status": status,
        "manifestSha256": _sha(manifest_raw),
        "sourceBinding": dict(manifest["sourceBinding"]),
        "namedPhaseGates": named_gates,
        "workerIdentityMatches": worker_identity_matches,
        "manifestInputsUnchanged": manifest_unchanged,
        "workerResult": {"path": str(worker_path), "sha256": _sha(worker_path.read_bytes()),
                         "status": worker.get("status")} if worker is not None else None,
        "workflowSources": {"pinsPresent": "workflowSources" in manifest,
                            "before": workflow_before, "after": workflow_after,
                            "unchanged": workflow_before == workflow_after},
        "host": host,
        "applicationMeasurements": "NOT_APPLICABLE; phase envelope records host ownership only",
    }
    write_exclusive(_phase_result_path(output_root), envelope)
    return envelope


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--phase", choices=tuple(PHASES))
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--worker", action="store_true")
    args = parser.parse_args(argv)
    if args.worker:
        _need(args.phase is not None, "worker requires --phase")
        return _worker(args.manifest, args.phase, args.output)
    _need(args.phase is not None, "normal invocation requires --phase")
    result = run_phase(args.manifest, args.phase, args.output)
    print(json.dumps({"phase": result["phase"], "status": result["status"],
                      "output": str(args.output)}, sort_keys=True), flush=True)
    return 0 if result["status"] == "PASS" else (2 if result["status"] == "BLOCKED" else 1)


if __name__ == "__main__":
    sys.dont_write_bytecode = True
    try:
        raise SystemExit(main())
    except (QualificationError, ReceiptError, OSError) as error:
        print(f"qualification phase refused: {error}", file=sys.stderr, flush=True)
        raise SystemExit(2)
