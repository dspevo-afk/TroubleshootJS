"""Freeze the render-only reference-mark candidate without altering prior seals."""
import hashlib
import json
import sys
from pathlib import Path

if not __debug__:
    raise RuntimeError("Reference-mark audit requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
GATES = ("scope.md", "inputs.py", "run_phase.ps1", "assess.py")
CHANGED = {
    "src/com/lushprojects/circuitjs1/client/ChipPinout.java",
    "src/com/lushprojects/circuitjs1/client/PhysicalPartRenderContext.java",
    "src/com/lushprojects/circuitjs1/client/StandardPhysicalPartRenderProviders.java",
    "src/com/lushprojects/circuitjs1/client/PcbWorkbenchController.java",
    "tests/contracts/BenchInstrumentPresentationContractTest.java",
}


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def record():
    prior = read(HERE.parent / "normal-silkscreen-title-r1/candidate-inputs.json")
    names = set(prior["files"])
    java = {p.relative_to(ROOT).as_posix() for folder in ("src", "tests/contracts")
            for p in (ROOT / folder).rglob("*.java")}
    assert java == {p for p in names if p.endswith(".java") and p.startswith(("src/", "tests/contracts/"))}
    assert len(names) == 1481
    return {"baseCommit": "bda25debed8c83a838198d3b8709af161963314a",
            "files": {name: sha(ROOT / name) for name in sorted(names)},
            "gates": {name: sha(HERE / name) for name in GATES}}


def main():
    command, phase = sys.argv[1:]
    assert command in ("freeze", "audit") and phase in ("baseline", "candidate")
    value = record()
    path = HERE / (phase + "-inputs.json")
    prior = read(HERE.parent / "normal-silkscreen-title-r1/candidate-inputs.json")["files"]
    assert set(prior) == set(value["files"])
    changes = {name for name in prior if prior[name] != value["files"][name]}
    assert changes == (set() if phase == "baseline" else CHANGED), changes
    if phase == "candidate":
        assert value["gates"] == read(HERE / "baseline-inputs.json")["gates"]
    if command == "freeze":
        assert not path.exists(), "Preserve prior completed attempt before freezing"
        if phase == "candidate":
            for name in sorted(changes):
                target = HERE / "candidate-source" / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((ROOT / name).read_bytes())
        path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    assert read(path) == value, "Frozen phase source/gates changed"
    print("PASS chip reference exact input audit phase=" + phase + " sources=1481 gates=4")


if __name__ == "__main__":
    main()
