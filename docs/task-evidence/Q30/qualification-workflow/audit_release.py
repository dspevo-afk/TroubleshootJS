"""Fresh, read-only exact-instance and listener release audit for Q30 evidence."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import tempfile
import time
import uuid
from datetime import datetime, timezone

from serial_runner import run_command
from windows_process_identity import ABSENT, QUERY_ABSENT, QUERY_FAILED, QUERY_PRESENT, UNRESOLVED, compare_identity, query_process


MAX_AUDIT_MS = 30000
CIM_TIMEOUT_MS = 20000
EXPECTED_CASES = 77
EXPECTED_PROCESSES = 809
EXPECTED_PORTS = 77


def _sha(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def _json(path):
    with Path(path).open("r", encoding="utf-8-sig") as stream:
        return json.load(stream)


def _inside(path, roots):
    real = Path(path).resolve(strict=True)
    for root in roots:
        try:
            real.relative_to(Path(root).resolve(strict=True))
            return real
        except ValueError:
            pass
    raise ValueError(f"evidence path is outside approved T/B/N roots: {path}")


def _pin(path, expected):
    if not isinstance(expected, str) or len(expected) != 64:
        raise ValueError(f"invalid SHA-256 pin for {path}")
    actual = _sha(path)
    if actual != expected.lower():
        raise ValueError(f"SHA-256 mismatch for {path}")
    return actual


def _verify_source_and_cohort(manifest):
    retained, bind = manifest["retainedEvidence"], manifest["sourceBinding"]
    pins = manifest["retainedEvidenceSha256"]
    release_path = Path(retained["release"])
    cohort = Path(retained["cohortRoot"])
    old_plan = Path(retained["oldPlanManifest"])
    roots = (release_path.parent, cohort, old_plan.parent)
    release_sha = _pin(release_path, pins["release"])
    release = _json(release_path)
    if release.get("status") != "BLOCKED" or release.get("terminationUsed") is not False:
        raise ValueError("retained R5 receipt is not the expected preserved blocked/no-termination audit")
    fixed = {
        "oldPlanManifest": old_plan,
        "batchResult": cohort / "batch-result.json",
        "controllerResult": cohort / "controller-result.json",
        "controllerPublication": cohort / "controller-publication.json",
    }
    pinned_objects = {}
    for key, path in fixed.items():
        _pin(path, pins[key])
        pinned_objects[key] = _json(path)
    batch, controller, publication = (pinned_objects[k] for k in
                                      ("batchResult", "controllerResult", "controllerPublication"))
    if batch.get("status") != "PASS_COLD77" or batch.get("complete") is not True:
        raise ValueError("retained cold77 batch result is not complete PASS")
    if controller.get("status") != "PASS_COLD77_CONTROLLER" or controller.get("qualified") is not True:
        raise ValueError("retained cohort controller result is not PASS")
    if publication.get("status") != "PASS_COLD77_CONTROLLER" or publication.get("withinFinalizationDeadline") is not True:
        raise ValueError("retained controller publication is not PASS")

    evidence = release.get("evidenceFiles")
    if not isinstance(evidence, list) or len(evidence) != 477:
        raise ValueError("R5 evidenceFiles must contain exactly 477 pinned files")
    seen = set()
    for row in evidence:
        path = row.get("path") if isinstance(row, dict) else None
        if not isinstance(path, str) or path in seen:
            raise ValueError("R5 evidenceFiles contains a malformed or duplicate path")
        seen.add(path)
        _inside(path, roots)
        _pin(path, row.get("sha256"))

    snapshot_info = manifest["sourceSnapshot"]
    snapshot_path = Path(snapshot_info["path"])
    _pin(snapshot_path, snapshot_info["sha256"])
    snapshot = _json(snapshot_path)
    if (snapshot.get("schema") != 1 or snapshot.get("baseHead") != bind.get("baseHead") or
            snapshot.get("sourceIdentity") != bind.get("sourceIdentity") or
            len(snapshot.get("files", [])) != 1355 or bind.get("sourceFiles") != 1355):
        raise ValueError("1355-file source snapshot identity/count does not match the manifest")
    repo = Path(manifest["repo"]).resolve(strict=True)
    verified = 0
    for row in snapshot["files"]:
        rel = PurePosixPath(row.get("path", ""))
        if rel.is_absolute() or not rel.parts or any(part in ("", ".", "..") for part in rel.parts):
            raise ValueError("source snapshot contains an unsafe relative path")
        path = repo.joinpath(*rel.parts)
        real = path.resolve(strict=True)
        real.relative_to(repo)
        if path.stat().st_size != row.get("size") or _sha(path) != row.get("sha256"):
            raise ValueError(f"source snapshot file changed: {row['path']}")
        verified += 1
    return release, {"status": "PASS", "sourceFiles": verified, "sourceIdentity": bind["sourceIdentity"],
        "sourceSnapshotSha256": snapshot_info["sha256"], "retainedEvidenceFiles": len(evidence),
        "retainedEvidenceSha256": pins, "batchStatus": batch["status"],
        "controllerStatus": controller["status"], "publicationStatus": publication["status"]}


def classify_process_rows(rows, queries):
    """Pure classifier used by the auditor and saved-reuse canary tests."""
    result = []
    for row in rows:
        pid = row.get("pid")
        recorded = {key: row.get(key) for key in
                    ("pid", "creationFileTimeTicks", "birthPrecision", "executable")}
        query = queries.get(pid, {"pid": pid, "queryStatus": QUERY_FAILED, "identity": None})
        classification = compare_identity(recorded, query)
        result.append({"pid": pid, "recordedBirthTicks": row.get("creationFileTimeTicks"),
            "birthPrecision": row.get("birthPrecision"), "status": classification["status"],
            "reason": classification["reason"], "queryStatus": classification["queryStatus"],
            "sources": row.get("sources", [])})
    return result


def _ps_script(pids):
    ids = ",".join(str(pid) for pid in sorted(pids))
    return rf'''
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$ids = @({ids})
try {{
  $processes = @(Get-CimInstance -ClassName Win32_Process -ErrorAction Stop |
    Where-Object {{ $ids -contains [int]$_.ProcessId }} |
    ForEach-Object {{
      $ticks = $null
      if ($null -ne $_.CreationDate) {{ $ticks = [Int64]$_.CreationDate.ToUniversalTime().ToFileTimeUtc() }}
      [pscustomobject]@{{ pid = [int]$_.ProcessId; creationFileTimeTicks = $ticks;
        birthPrecision = if ($null -ne $ticks) {{ 'CIM_MICROSECOND' }} else {{ $null }};
        executable = $_.ExecutablePath }}
    }})
  $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop |
    ForEach-Object {{ [pscustomobject]@{{ localPort = [int]$_.LocalPort; ownerPid = [int]$_.OwningProcess }} }})
  [pscustomobject]@{{ ok = $true; processes = $processes; listeners = $listeners }} |
    ConvertTo-Json -Depth 5 -Compress
}} catch {{
  [pscustomobject]@{{ ok = $false; error = $_.Exception.Message }} | ConvertTo-Json -Compress
  exit 1
}}
'''.strip()


def _run_cim_and_ports(manifest, pids):
    ps = manifest["tools"]["powershell"]
    if not Path(ps).is_file():
        raise FileNotFoundError("pinned physical PowerShell 7 executable is missing")
    query_root = Path(manifest["tempRoot"]) / ("release-query-" + uuid.uuid4().hex)
    query_root.mkdir(exist_ok=False)
    script = query_root / "query.ps1"
    with script.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(_ps_script(pids))
    scratch = query_root / "host"
    result = run_command([ps, "-NoLogo", "-NoProfile", "-NonInteractive", "-File", str(script)],
                         scratch, CIM_TIMEOUT_MS)
    stdout = scratch / "stdout.log"
    if result.get("status") != "PASS" or result.get("handlesClosed") is not True or not result.get("cleanup", {}).get("cleanupVerified"):
        raise RuntimeError(f"owned physical PS7 query failed: {result.get('reason') or result.get('status')}")
    data = json.loads(stdout.read_text(encoding="utf-8-sig"))
    if data.get("ok") is not True:
        raise RuntimeError(f"CIM/listener query failed: {data.get('error')}")
    return data, {"hostRoot": str(scratch), "hostResult": result}


def audit(manifest, output_dir):
    """Verify pinned evidence, query current PIDs/listeners, and write a new audit.

    ``manifest`` is either the qualification-manifest JSON path or its decoded
    object. The output directory must be a new task-owned location.
    """
    started_ns = time.monotonic_ns()
    started = datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")
    if isinstance(manifest, (str, os.PathLike)):
        manifest_path = Path(manifest)
        manifest = _json(manifest_path)
    else:
        manifest_path = None
    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=False)
    report = {"schema": 1, "status": "BLOCKED", "startedUtc": started,
              "sourceValidation": {"status": "FAIL"}, "processAudit": [], "portAudit": [], "blockers": []}
    try:
        release, source = _verify_source_and_cohort(manifest)
        report["sourceValidation"] = source
        rows = release["processAudit"]
        ports = release["portAudit"]
        cleanup = release["caseCleanup"]
        if (len(rows) != EXPECTED_PROCESSES or release.get("recordedInstances") != EXPECTED_PROCESSES or
                len(ports) != EXPECTED_PORTS or len(cleanup) != EXPECTED_CASES or
                release.get("caseCount") != EXPECTED_CASES):
            raise ValueError("retained R5 process/port/case totals do not match 809/77/77")
        if not release.get("allCaseCleanupVerified") or not release.get("outerCleanupVerified"):
            raise ValueError("retained release cleanup evidence is incomplete")
        for case in cleanup:
            if not all(case.get(key) is True for key in
                       ("hostCleanupPass", "stepPass", "readersClosed", "drainsComplete",
                        "cleanupVerified", "jobZero", "applicationPass")):
                raise ValueError("a retained cold77 case cleanup row is not PASS")

        unique_pids = sorted({row.get("pid") for row in rows})
        queries = {pid: query_process(pid) for pid in unique_pids}
        classified = classify_process_rows(rows, queries)
        fallback_pids = sorted({x["pid"] for x in classified if x["status"] == UNRESOLVED})
        host = None
        if fallback_pids or ports:
            live, host = _run_cim_and_ports(manifest, fallback_pids)
            cim_rows = live.get("processes")
            if not isinstance(cim_rows, list):
                raise ValueError("physical CIM query omitted its process array")
            by_pid = {}
            duplicates = set()
            for row in cim_rows:
                pid = row.get("pid")
                if type(pid) is not int or pid not in fallback_pids or pid in by_pid:
                    duplicates.add(pid)
                by_pid[pid] = row
            if duplicates:
                raise ValueError("physical CIM query returned duplicate or unrequested PID rows")
            for pid in fallback_pids:
                queries[pid] = ({"pid": pid, "queryStatus": QUERY_PRESENT, "identity": by_pid[pid]}
                                if pid in by_pid else {"pid": pid, "queryStatus": QUERY_ABSENT, "identity": None})
            classified = classify_process_rows(rows, queries)
            listeners = live.get("listeners")
            if not isinstance(listeners, list):
                raise ValueError("physical TCP query omitted its listener array")
            listening = {}
            for item in listeners:
                port = item.get("localPort")
                if type(port) is int:
                    listening.setdefault(port, []).append(item.get("ownerPid"))
            report["portAudit"] = [{"port": p["port"], "status": "LISTENING" if p["port"] in listening else "CLOSED",
                                     "liveOwners": listening.get(p["port"], []), "rootSeed": p.get("rootSeed")}
                                    for p in ports]
        else:
            report["portAudit"] = [{"port": p["port"], "status": "NOT_QUERIED"} for p in ports]
        report["processAudit"] = classified
        report["nativeQueryCount"] = len(unique_pids)
        report["cimFallbackPidCount"] = len(fallback_pids)
        report["physicalQuery"] = host
        absent = sum(x["status"] == ABSENT for x in classified)
        live_count = sum(x["status"] == "MATCH" for x in classified)
        unresolved = sum(x["status"] == UNRESOLVED for x in classified)
        closed = sum(x["status"] == "CLOSED" for x in report["portAudit"])
        report["counts"] = {"recordedInstances": len(classified), "absent": absent,
            "liveExactMatches": live_count, "unresolved": unresolved, "recordedPorts": len(ports),
            "portsClosed": closed, "casesCleanupVerified": len(cleanup)}
        if absent != EXPECTED_PROCESSES or live_count or unresolved or closed != EXPECTED_PORTS:
            report["blockers"].append("fresh process-instance or TCP-listener predicate failed")
        elapsed = (time.monotonic_ns() - started_ns) // 1_000_000
        finished = datetime.now(timezone.utc)
        end = datetime.fromisoformat(manifest["retainedEvidence"]["quietWindowEnd"].replace("Z", "+00:00"))
        report.update({"elapsedMs": elapsed, "finishedUtc": finished.isoformat(timespec="microseconds").replace("+00:00", "Z"),
            "quietWindowEndUtc": manifest["retainedEvidence"]["quietWindowEnd"],
            "observedWithinWindow": finished <= end, "terminationUsed": False,
            "status": "PASS" if not report["blockers"] and elapsed <= MAX_AUDIT_MS and finished <= end else "BLOCKED"})
        if elapsed > MAX_AUDIT_MS:
            report["blockers"].append("audit exceeded 30000 ms")
        if finished > end:
            report["blockers"].append("audit finished after the approved quiet-window end")
    except Exception as exc:
        report["blockers"].append(f"{type(exc).__name__}: {exc}")
        report["elapsedMs"] = (time.monotonic_ns() - started_ns) // 1_000_000
        report["finishedUtc"] = datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")
        report["status"] = "BLOCKED"
    out_file = out / "release-audit.json"
    with out_file.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(report, stream, indent=2, sort_keys=True, ensure_ascii=False, allow_nan=False)
        stream.write("\n")
    report["outputPath"] = str(out_file)
    return report


if __name__ == "__main__":
    import sys
    result = audit(sys.argv[1], sys.argv[2])
    print(json.dumps({k: result.get(k) for k in ("status", "elapsedMs", "counts", "blockers", "outputPath")}, sort_keys=True))
    raise SystemExit(0 if result["status"] == "PASS" else 2)
