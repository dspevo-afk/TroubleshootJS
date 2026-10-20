"""Assess actual focused phases, unchanged physical census, and exact source scope."""
import copy, difflib, hashlib, json, math, re, runpy, sys
from pathlib import Path
if not __debug__: raise RuntimeError("Assertion-enabled Python required")
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
def read(p): return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def valid_timing(t):
    assert t["status"] == "PASS" and type(t["exitCode"]) is int and t["exitCode"] == 0
    assert type(t["elapsedSeconds"]) in (int, float) and math.isfinite(t["elapsedSeconds"]) and t["elapsedSeconds"] >= 0
    assert t["terminalCleanupIncluded"] is True and t["failure"] is None
def census_rows(path):
    groups = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if not line.startswith(("PCB_NORMAL_PACKING_CASE ", "PCB_NORMAL_REFERENCE_CASE ", "PCB_NORMAL_SILK_CASE ", "PCB_LEAD_SILK_CASE ")): continue
        prefix, raw = line.split(" ", 1)
        row = json.loads(raw)
        key = (prefix, row["family"], row["root"])
        assert key not in groups
        if prefix == "PCB_NORMAL_PACKING_CASE":
            assert row["status"] == "ACCEPTED" and row["graphBefore"] == row["graphAfter"] and row["layoutAlgorithmVersion"] == 18
            for k in ("elapsedMillis", "maxAdvanceMillis", "generationRoutingMillis"): del row[k]
        groups[key] = row
    assert len(groups) == 176 and len({(k[1], k[2]) for k in groups}) == 44
    return groups
def canaries():
    sample = {"status":"PASS", "exitCode":0, "elapsedSeconds":1.0, "terminalCleanupIncluded":True, "failure":None}
    valid_timing(sample)
    for key, value in (("exitCode", False), ("exitCode", 0.0), ("exitCode", 2), ("status", "FAIL"), ("elapsedSeconds", False), ("elapsedSeconds", float("nan"))):
        frame = copy.deepcopy(sample); frame[key] = value
        try: valid_timing(frame)
        except AssertionError: pass
        else: raise RuntimeError("Invalid timing accepted")
    print("PASS pure timing canaries positive1 rejected6; no process execution claim")
def main():
    if sys.argv[1:] == ["--canaries"]: canaries(); return
    phase, = sys.argv[1:]; assert phase in ("contracts", "census", "build")
    inp = runpy.run_path(str(HERE / "inputs.py"))
    candidate = read(HERE / "candidate-inputs.json")
    assert inp["record"]() == candidate
    baseline = read(HERE / "baseline-inputs.json")
    for which, manifest in (("baseline", baseline), ("candidate", candidate)):
        archive = HERE / (which + "-source")
        assert {p.relative_to(archive).as_posix(): sha(p) for p in archive.rglob("*") if p.is_file()} == {p:manifest["files"][p] for p in inp["CHANGED"]}
    for path in inp["CHANGED"]:
        old = (HERE / "baseline-source" / path).read_text(encoding="utf-8")
        new = (HERE / "candidate-source" / path).read_text(encoding="utf-8")
        assert all(op in ("equal", "insert") for op, *_ in difflib.SequenceMatcher(None, old.splitlines(), new.splitlines(), autojunk=False).get_opcodes()), path
    t = read(HERE / (phase + "-timing.json")); valid_timing(t); assert t["phase"] == phase
    launch_path = HERE / (phase + "-process-launch.json")
    launch = read(launch_path); cleanup = read(HERE / (phase + "-process-cleanup.json"))
    assert launch["phase"] == cleanup["phase"] == phase and cleanup["status"] == "PASS"
    assert type(cleanup["actualExecExitCode"]) is int and cleanup["actualExecExitCode"] == 0
    assert cleanup["launchSha256"] == sha(launch_path)
    assert len(launch["drivers"]) == len(cleanup["checks"]) == 2
    assert [{k:r[k] for k in ("pid", "startTicks", "executable")} for r in cleanup["checks"]] == launch["drivers"]
    assert all(r["recordedBirthExited"] is True for r in cleanup["checks"])
    for side in ("before", "after"):
        assert (HERE / (phase + "-input-audit-" + side + ".txt")).read_text(encoding="utf-8-sig").strip() == "PASS connector housing exact inputs phase=candidate sources=1481 gates=5"
    log = (HERE / (phase + ".txt")).read_text(encoding="utf-8-sig")
    if phase == "build":
        for marker in ("Compiling module com.lushprojects.circuitjs1.circuitjs1", "Compiling 5 permutations", "Compile of permutations succeeded", "Compilation succeeded --", "Link succeeded", "Linking succeeded --"): assert log.count(marker) == 1, marker
        assert "BUILD_FAILURE" not in log and "BUILD_INFRASTRUCTURE" not in log
    else:
        suites = read(HERE / "suites.json")[phase]
        assert log.count("PASS: focused current contracts; " + str(len(suites)) + " Java suites. Full matrix and independent oracles NOT RUN.") == 1
        assert "CURRENT_CONTRACT_FAILURE:" not in log
        receipt = (HERE / (phase + "-receipt.txt")).read_text(encoding="utf-8-sig")
        definitions = dict(re.findall(r"Name = '([^']+)'; Marker = '([^']+)'", (ROOT / "scripts/verify-current-contracts.ps1").read_text(encoding="utf-8-sig")))
        for suite in suites: assert re.search(r"(?m)^PASS: " + re.escape(definitions[suite]), receipt), suite
        if phase == "census":
            assert census_rows(HERE / "census-receipt.txt") == census_rows(HERE.parent / "reference-lead-clearance-r2/census-receipt.txt")
    (HERE / (phase + "-assessment.json")).write_text(json.dumps({"status":"PASS", "phase":phase, "elapsedSeconds":t["elapsedSeconds"], "sources":1481, "boundary":"Only insertion-only theme/provider paint and additive native fixtures differ; current18 physical census exact except3 timing fields", "limits":"Named actual phase only; visible production and publication assessed separately; no full matrix/all-seed/new-size claim"}, indent=2) + "\n", encoding="utf-8")
    print("PASS connector housing actual phase " + phase)
if __name__ == "__main__": main()
