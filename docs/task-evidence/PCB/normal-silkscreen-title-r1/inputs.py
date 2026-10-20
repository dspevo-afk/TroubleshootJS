"""Freeze/audit each title-only phase without rewriting the accepted PCB evidence."""
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
R2 = HERE.parent / "normal-packing-r2"
GATES = ("scope.md", "inputs.py", "run_phase.ps1", "assess.py")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def record():
    old = read(R2 / "candidate-inputs.json")
    names = [row["path"] for row in old["files"]]
    assert len(names) == len(set(names)) == 1481
    java = {p.relative_to(ROOT).as_posix() for folder in ("src", "tests/contracts")
            for p in (ROOT / folder).rglob("*.java")}
    assert java == {p for p in names if p.endswith(".java") and
                    p.startswith(("src/", "tests/contracts/"))}
    return {"baseCommit": "438ac2e2aba6e433a192c29807f24ef283b2c404",
            "files": {name: sha(ROOT / name) for name in names},
            "gates": {name: sha(HERE / name) for name in GATES},
            "suiteManifestSha256": sha(R2 / "routes-suites.json"),
            "suiteTransportSha256": sha(R2 / "suite_transport.ps1"),
            "acceptedRoutesSha256": sha(R2 / "routes-receipt.txt")}


def main():
    command, phase = sys.argv[1:]
    assert phase in ("baseline", "candidate") and command in ("freeze", "audit")
    path = HERE / (phase + "-inputs.json")
    value = record()
    if command == "freeze":
        assert not path.exists(), "preserve completed attempt before refreezing"
        base = {row["path"]: row["sha256"] for row in read(R2 / "candidate-inputs.json")["files"]}
        base["tests/browser/quickplay_procedural_acceptance.py"] = read(
            R2 / "publication-inputs.json")["overlay"]["currentSha256"]
        changes = {name: digest for name, digest in value["files"].items()
                   if digest != base[name]}
        if phase == "baseline":
            assert set(changes) == {"tests/contracts/PcbNormalPackingCensusContractTest.java"}
        else:
            baseline = read(HERE / "baseline-inputs.json")
            production = {name for name in value["files"]
                          if name.startswith("src/") and value["files"][name] != baseline["files"][name]}
            assert len(production) == 7, "exact six metadata owners plus shared title layout owner"
            assert value["gates"] == baseline["gates"], "phase gate drift"
        archive = HERE / (phase + "-source")
        assert not archive.exists()
        for name in changes:
            target = archive / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / name).read_bytes())
        path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    assert read(path) == value, "frozen phase inputs changed"
    print("PASS title phase exact input audit phase=" + phase + " sources=1481 gates=4")


if __name__ == "__main__":
    main()
