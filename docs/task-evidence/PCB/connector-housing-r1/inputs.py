"""Freeze the connector paint scope without changing physical model inputs."""
import hashlib, json, subprocess, sys
from pathlib import Path
if not __debug__: raise RuntimeError("Assertion-enabled Python required")
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
GATES = ("scope.md", "inputs.py", "run_phase.ps1", "qualify.py", "suites.json")
CHANGED = {"src/com/lushprojects/circuitjs1/client/StandardPhysicalPartRenderProviders.java",
           "src/com/lushprojects/circuitjs1/client/WorkbenchVisualTheme.java",
           "tests/contracts/PcbSilkscreenScaleContractTest.java"}
def read(p): return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    accepted = read(HERE / "accepted-inputs.json")
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip()
    assert head == accepted["baseCommit"] == "2c987e45553a3a9346296f59e027e911222fcf6c"
    assert subprocess.check_output(["git", "branch", "--show-current"], cwd=ROOT).decode().strip() == "codex/q60-faulted-profile-counts"
    names = set(accepted["files"])
    java = {p.relative_to(ROOT).as_posix() for folder in ("src", "tests/contracts")
            for p in (ROOT / folder).rglob("*.java")}
    assert len(names) == 1481 and java == {p for p in names if p.endswith(".java") and p.startswith(("src/", "tests/contracts/"))}
    paths = subprocess.check_output(["git", "ls-files", "-z", "--", "scripts", "*.xml", "*.jar", ".classpath", "*.properties"], cwd=ROOT).decode().rstrip("\0").split("\0")
    machinery = {p: sha(ROOT / p) for p in sorted(set(paths))}
    assert len(machinery) == 24 and machinery == accepted["machinery"]
    return {"baseCommit": head, "files": {p: sha(ROOT / p) for p in sorted(names)},
            "machinery": machinery, "gates": {p: sha(HERE / p) for p in GATES},
            "acceptedSealSha256": sha(HERE / "accepted-inputs.json"),
            "physicalBaselineReceiptSha256": sha(HERE.parent / "reference-lead-clearance-r2/census-receipt.txt")}
def main():
    command, phase = sys.argv[1:]
    assert command in ("freeze", "audit") and phase in ("baseline", "candidate")
    value = record()
    accepted = read(HERE / "accepted-inputs.json")
    changed = {p for p in value["files"] if value["files"][p] != accepted["files"][p]}
    assert changed == (set() if phase == "baseline" else CHANGED), changed
    if phase == "candidate":
        before = read(HERE / "baseline-inputs.json")
        assert all(value[k] == before[k] for k in value if k != "files")
    target = HERE / (phase + "-inputs.json")
    if command == "freeze":
        assert not target.exists(), "Preserve prior freeze"
        for p in sorted(CHANGED):
            dest = HERE / (phase + "-source") / p
            assert not dest.exists()
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes((ROOT / p).read_bytes())
        target.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    assert read(target) == value, "Frozen source or gate drift"
    print("PASS connector housing exact inputs phase=" + phase + " sources=1481 gates=5")
if __name__ == "__main__": main()
