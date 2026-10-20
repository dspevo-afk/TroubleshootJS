"""Pin declaration-only public marks, original reservations and unchanged physical owners."""
import hashlib, json, subprocess, sys
from pathlib import Path
if not __debug__: raise RuntimeError("Assertion-enabled Python required")
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
BASE = "79baf499dd8c14a1ed5e8aa049611026b2077e22"
GATES = ("scope.md", "inputs.py", "qualify.py", "run_phase.ps1", "suites.json")
PREFIX = "src/com/lushprojects/circuitjs1/client/"
EXISTING = {PREFIX+n+".java" for n in ("SensorControlGenerator", "NmosLowSideSwitchGenerator",
            "NpnLowSideSwitchGenerator", "RelayOutputGenerator", "Rb15Plan")}
EXISTING.add("tests/contracts/PhysicalServiceabilityContractTest.java")
NEW = PREFIX + "NormalResistorReferences.java"
def read(p): return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def record(phase="candidate"):
    assert phase in ("baseline", "candidate")
    assert subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip() == BASE
    assert subprocess.check_output(["git", "branch", "--show-current"], cwd=ROOT).decode().strip() == "codex/q60-faulted-profile-counts"
    accepted = read(HERE/"accepted-inputs.json")
    assert accepted["baseCommit"] == BASE and len(accepted["files"]) == 1481
    assert (ROOT/NEW).exists() is (phase == "candidate")
    names = set(accepted["files"]) | ({NEW} if phase == "candidate" else set())
    java = {p.relative_to(ROOT).as_posix() for folder in ("src", "tests/contracts") for p in (ROOT/folder).rglob("*.java")}
    assert java == {p for p in names if p.endswith(".java") and p.startswith(("src/", "tests/contracts/"))}
    files = {p:sha(ROOT/p) for p in sorted(names)}
    changed = {p for p in accepted["files"] if accepted["files"][p] != files[p]}
    assert changed == (EXISTING if phase == "candidate" else set()), changed
    paths = subprocess.check_output(["git", "ls-files", "-z", "--", "scripts", "*.xml", "*.jar", ".classpath", "*.properties"], cwd=ROOT).decode().rstrip("\0").split("\0")
    machinery = {p:sha(ROOT/p) for p in sorted(set(paths))}
    assert len(machinery) == 24 and machinery == accepted["machinery"]
    prior = HERE.parent/"connector-housing-r1"
    return {"baseCommit":BASE, "phase":phase, "files":files, "machinery":machinery,
            "gates":{p:sha(HERE/p) for p in GATES}, "acceptedSealSha256":sha(HERE/"accepted-inputs.json"),
            "baselineEvidence":{n:sha(prior/n) for n in ("candidate-inputs.json", "census-receipt.txt", "census-assessment.json", "physical-comparison.json", "publication.json")}}
def main():
    command, phase = sys.argv[1:]
    assert command in ("freeze", "audit")
    value = record(phase)
    if phase == "candidate":
        baseline = read(HERE/"baseline-inputs.json")
        assert all(value[k] == baseline[k] for k in ("baseCommit", "machinery", "gates", "acceptedSealSha256", "baselineEvidence"))
    target = HERE/(phase+"-inputs.json")
    if command == "freeze":
        assert not target.exists(), "Preserve attempted artifacts"
        archive = HERE/(phase+"-source"); assert not archive.exists()
        for name in sorted(EXISTING | ({NEW} if phase == "candidate" else set())):
            dest = archive/name; dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes((ROOT/name).read_bytes())
        target.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
    assert read(target) == value
    print("PASS normal resistor exact inputs phase="+phase+" sources="+str(len(value["files"]))+" gates=5")
if __name__ == "__main__": main()
