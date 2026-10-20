"""Freeze the observer baseline and exact unique-reference candidate owners."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

if not __debug__:
    raise RuntimeError("Reference input audit requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
GATES = ("scope.md", "inputs.py", "run_phase.ps1", "assess.py")
OBSERVER = {"tests/contracts/PcbNormalPackingCensusContractTest.java"}
PRODUCTION = {"src/com/lushprojects/circuitjs1/client/" + name + ".java" for name in (
    "BoardComponent", "PhysicalConstructionMaterializer", "SeededPcbLayoutGenerator",
    "ControlledIndicatorPcbLayoutFactory", "GenerationDependencyContext",
    "ServiceableBoardConstruction")}
TESTS = {"tests/contracts/ControlledIndicatorAssemblyContractTest.java",
         "tests/contracts/E05IsolationGeometryContractTest.java",
         "tests/contracts/D01DiagnosticContractTest.java",
         "tests/contracts/Rb56ModelIdentityContractTest.java"}


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def record():
    accepted = read(HERE / "accepted-inputs.json")
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip()
    branch = subprocess.check_output(["git", "branch", "--show-current"], cwd=ROOT).decode().strip()
    assert head == accepted["baseCommit"] and branch == "codex/q60-faulted-profile-counts"
    names = set(accepted["files"])
    assert len(names) == accepted["sourceCount"] == 1481
    java = {p.relative_to(ROOT).as_posix() for folder in ("src", "tests/contracts")
            for p in (ROOT / folder).rglob("*.java")}
    assert java == {p for p in names if p.endswith(".java") and p.startswith(("src/", "tests/contracts/"))}
    machinery = subprocess.check_output(["git", "ls-files", "-z", "--", "scripts", "*.xml", "*.jar", ".classpath", "*.properties"], cwd=ROOT).decode().rstrip("\0").split("\0")
    assert machinery and all(name and (ROOT / name).is_file() for name in machinery)
    return {"baseCommit": accepted["baseCommit"], "actualHead": head, "branch": branch,
        "files": {name: sha(ROOT / name) for name in sorted(names)},
        "machinery": {name: sha(ROOT / name) for name in sorted(set(machinery))},
        "gates": {name: sha(HERE / name) for name in GATES},
        "acceptedSealSha256": sha(HERE / "accepted-inputs.json"),
        "transport": {name: sha(HERE.parent / "normal-packing-r2" / name)
                      for name in ("suite_transport.ps1", "routes-suites.json")}}


def main():
    command, phase = sys.argv[1:]
    assert command in ("freeze", "audit") and phase in ("baseline", "candidate")
    value = record()
    accepted = read(HERE / "accepted-inputs.json")["files"]
    changes = {p for p in accepted if accepted[p] != value["files"][p]}
    assert changes == (OBSERVER if phase == "baseline" else OBSERVER | PRODUCTION | TESTS), changes
    if phase == "candidate":
        baseline = read(HERE / "baseline-inputs.json")
        assert all(value[k] == baseline[k] for k in ("actualHead", "branch", "machinery", "gates", "acceptedSealSha256", "transport"))
        assert all(value["files"][p] == baseline["files"][p] for p in OBSERVER)
    path = HERE / (phase + "-inputs.json")
    if command == "freeze":
        assert not path.exists(), "Preserve the completed attempt before freezing again"
        archive = HERE / (phase + "-source")
        assert not archive.exists()
        for name in sorted(changes):
            target = archive / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / name).read_bytes())
        path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    assert read(path) == value, "Frozen phase source/gates changed"
    print("PASS reference exact input audit phase=" + phase + " sources=1481 gates=4")


if __name__ == "__main__":
    main()
