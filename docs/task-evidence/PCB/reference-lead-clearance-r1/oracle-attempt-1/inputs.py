"""Frozen old-source export; assertion-enabled and candidate-independent."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path
if not __debug__:
    raise RuntimeError("Reference oracle requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
GATES = ("scope.md", "inputs.py", "run_oracle.ps1", "observe_oracle_exit.ps1", "oracle_reader.py", "oracle-observer.patch", "oracle-observer-draft.md", "oracle-suites.json")
GENERATOR = "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java"
CENSUS = "tests/contracts/PcbNormalPackingCensusContractTest.java"
OBSERVER = "tests/contracts/PcbCompactnessContractTest.java"
ARCHIVED = (GENERATOR, CENSUS, OBSERVER,
            "tests/contracts/PcbFunctionalGroupingContractTest.java",
            "tests/contracts/PcbPocketPackingContractTest.java")
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def read(n): return json.loads((HERE/n).read_text(encoding="utf-8-sig"))
def record():
    a = read("accepted-inputs.json")
    assert subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip() == a["baseCommit"]
    assert subprocess.check_output(["git", "branch", "--show-current"], cwd=ROOT).decode().strip() == a["branch"]
    assert len(a["files"]) == 1481
    assert {p.relative_to(ROOT).as_posix() for folder in ("src", "tests/contracts") for p in (ROOT/folder).rglob("*.java")} == {n for n in a["files"] if n.endswith(".java") and n.startswith(("src/", "tests/contracts/"))}
    machinery = subprocess.check_output(["git", "ls-files", "-z", "--", "scripts", "*.xml", "*.jar", ".classpath", "*.properties"], cwd=ROOT).decode().rstrip("\0").split("\0")
    assert {n:sha(ROOT/n) for n in sorted(set(machinery))} == a["machinery"]
    assert read("archived-owners.json") == list(ARCHIVED)
    assert {p.relative_to(HERE/"accepted-source").as_posix():sha(p) for p in (HERE/"accepted-source").rglob("*") if p.is_file()} == {n:a["files"][n] for n in ARCHIVED}
    assert sha(HERE/"passing-generator18.java.txt") == read("pre-edit-gates.json")["passingTrialGeneratorSha256"]
    assert sha(HERE.parent/"lead-silk-local-trial-r1/candidate-inputs.json") == read("pre-edit-gates.json")["passingTrialInputsSha256"]
    return {"baseCommit":a["baseCommit"], "files":{n:sha(ROOT/n) for n in sorted(a["files"])}, "machinery":a["machinery"], "gates":{n:sha(HERE/n) for n in GATES}, "acceptedSha256":sha(HERE/"accepted-inputs.json")}
def main():
    assert sys.argv[1:] in (["freeze"], ["audit"])
    value=record(); a=read("accepted-inputs.json")
    assert {n for n,h in value["files"].items() if h!=a["files"][n]} == {CENSUS,OBSERVER}
    assert value["files"][GENERATOR] == a["files"][GENERATOR]
    assert value["files"][CENSUS] == read("pre-edit-gates.json")["passingTrialObserverSha256"]
    assert value["gates"] == read("pre-edit-gates.json")["gates"]
    if sys.argv[1]=="freeze":
        assert not (HERE/"baseline-inputs.json").exists()
        for n in (CENSUS,OBSERVER):
            p=HERE/"baseline-source"/n; p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/n).read_bytes())
        (HERE/"baseline-inputs.json").write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
    assert value==read("baseline-inputs.json"),"Frozen old-source oracle input drift"
    print("PASS exact accepted17 oracle inputs sources=1481 gates=8")
if __name__=="__main__": main()
