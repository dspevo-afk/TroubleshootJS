"""Freeze the observer-only baseline and exact multi-rail reference owners."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

if not __debug__:
    raise RuntimeError("Multi-rail reference audit requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
GATES = ("scope.md", "inputs.py", "run_phase.ps1", "assess.py")
OBSERVER = {"tests/contracts/PcbNormalPackingCensusContractTest.java"}
PRODUCTION = {"src/com/lushprojects/circuitjs1/client/" + n + ".java" for n in
              ("Rb30Plan", "Rb30Generator", "Rb30RelayService")}
TESTS = {"tests/contracts/" + n + ".java" for n in
         ("Q30PlanContractTest", "Rb30PhysicalMetadataContractTest", "Q30RelayServiceContractTest")}
OWNERS = OBSERVER | PRODUCTION | TESTS


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def record():
    accepted = read(HERE / "accepted-inputs.json")
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip()
    branch = subprocess.check_output(["git", "branch", "--show-current"], cwd=ROOT).decode().strip()
    assert head == accepted["baseCommit"] == "e8d151144e0bb66e24b7aa00f49fcee41620a922"
    assert branch == "codex/q60-faulted-profile-counts"
    names = set(accepted["files"])
    assert len(names) == 1481
    java = {p.relative_to(ROOT).as_posix() for folder in ("src", "tests/contracts")
            for p in (ROOT / folder).rglob("*.java")}
    assert java == {p for p in names if p.endswith(".java") and p.startswith(("src/", "tests/contracts/"))}
    machinery = subprocess.check_output(["git", "ls-files", "-z", "--", "scripts", "*.xml", "*.jar", ".classpath", "*.properties"], cwd=ROOT).decode().rstrip("\0").split("\0")
    pins = {p: sha(ROOT / p) for p in sorted(set(machinery))}
    assert pins == accepted["machinery"]
    assert {p.relative_to(HERE / "accepted-source").as_posix(): sha(p)
            for p in (HERE / "accepted-source").rglob("*") if p.is_file()} == {
                n: accepted["files"][n] for n in OWNERS}
    evidence = {n: sha(HERE.parent / n) for n in accepted["evidence"]}
    assert evidence == accepted["evidence"]
    return {"baseCommit": head, "branch": branch,
        "files": {n: sha(ROOT / n) for n in sorted(names)}, "machinery": pins,
        "gates": {n: sha(HERE / n) for n in GATES},
        "acceptedSealSha256": sha(HERE / "accepted-inputs.json"), "evidence": evidence,
        "transport": {n: sha(HERE.parent / "normal-packing-r2" / n)
                      for n in ("suite_transport.ps1", "routes-suites.json")}}


def main():
    command, phase = sys.argv[1:]
    assert command in ("freeze", "audit") and phase in ("baseline", "candidate")
    value = record()
    accepted = read(HERE / "accepted-inputs.json")["files"]
    changed = {n for n in accepted if accepted[n] != value["files"][n]}
    assert changed == (OBSERVER if phase == "baseline" else OWNERS), changed
    if phase == "candidate":
        before = read(HERE / "baseline-inputs.json")
        assert all(value[k] == before[k] for k in value if k != "files")
        assert all(value["files"][n] == before["files"][n] for n in OBSERVER)
    path = HERE / (phase + "-inputs.json")
    if command == "freeze":
        archive = HERE / (phase + "-source")
        assert not path.exists() and not archive.exists(), "Preserve attempted artifacts before retry"
        for n in sorted(changed):
            target = archive / n
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / n).read_bytes())
        path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    assert read(path) == value, "Frozen source/gates changed"
    print("PASS multi-rail reference exact input audit phase=" + phase + " sources=1481 gates=4")


if __name__ == "__main__":
    main()
