"""Pin the bounded capacitor-face paint change and its unchanged physical owners."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

if not __debug__:
    raise RuntimeError("Capacitor face audit requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
GATES = ("scope.md", "inputs.py", "run_phase.ps1", "qualify.py")
CHANGED = {"src/com/lushprojects/circuitjs1/client/StandardPhysicalPartRenderProviders.java",
           "src/com/lushprojects/circuitjs1/client/WorkbenchVisualTheme.java",
           "tests/contracts/PcbSilkscreenScaleContractTest.java"}


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def record():
    prior = read(HERE.parent / "unique-public-references-r1/candidate-inputs.json")
    accepted = read(HERE / "accepted-inputs.json")
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip()
    branch = subprocess.check_output(["git", "branch", "--show-current"], cwd=ROOT).decode().strip()
    assert head == accepted["baseCommit"] == "1ce06e70182d77bad5c628bd834eb4aa9b59743e"
    assert branch == "codex/q60-faulted-profile-counts"
    names = set(prior["files"])
    java = {p.relative_to(ROOT).as_posix() for folder in ("src", "tests/contracts")
            for p in (ROOT / folder).rglob("*.java")}
    assert len(names) == 1481 and names == set(accepted["files"])
    assert java == {p for p in names if p.endswith(".java") and p.startswith(("src/", "tests/contracts/"))}
    machinery = subprocess.check_output(["git", "ls-files", "-z", "--", "scripts", "*.xml", "*.jar", ".classpath", "*.properties"], cwd=ROOT).decode().rstrip("\0").split("\0")
    pins = {p: sha(ROOT / p) for p in sorted(set(machinery))}
    assert pins == prior["machinery"] == accepted["machinery"]
    return {"baseCommit": head, "branch": branch,
        "files": {p: sha(ROOT / p) for p in sorted(names)}, "machinery": pins,
        "gates": {p: sha(HERE / p) for p in GATES},
        "acceptedSealSha256": sha(HERE / "accepted-inputs.json"),
        "reusedPhysicalEvidence": {p: sha(HERE.parent / "unique-public-references-r1" / p)
            for p in ("candidate-inputs.json", "candidate-assessment.json", "candidate-receipt.txt",
                      "native-assessment.json", "staged-review.json", "publication.json")}}


def main():
    command, phase = sys.argv[1:]
    assert command in ("freeze", "audit") and phase in ("baseline", "candidate")
    value = record()
    accepted = read(HERE / "accepted-inputs.json")["files"]
    changed = {p for p in accepted if accepted[p] != value["files"][p]}
    assert changed == (set() if phase == "baseline" else CHANGED), changed
    if phase == "candidate":
        baseline = read(HERE / "baseline-inputs.json")
        assert all(value[k] == baseline[k] for k in ("baseCommit", "branch", "machinery", "gates", "acceptedSealSha256", "reusedPhysicalEvidence"))
    path = HERE / (phase + "-inputs.json")
    if command == "freeze":
        assert not path.exists(), "Preserve attempted artifacts before another freeze"
        archive = HERE / (phase + "-source")
        assert not archive.exists()
        for p in sorted(CHANGED):
            target = archive / p
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / p).read_bytes())
        path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    assert read(path) == value, "Frozen capacitor source/gates changed"
    print("PASS capacitor face exact input audit phase=" + phase + " sources=1481 gates=4")


if __name__ == "__main__":
    main()
