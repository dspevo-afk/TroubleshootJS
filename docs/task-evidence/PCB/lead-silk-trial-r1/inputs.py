"""Exact unchanged corpus/machinery and two owned source inputs for a trial."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path
if not __debug__:
    raise RuntimeError("Lead-silk trial requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
GATES = ("scope.md", "inputs.py", "run_phase.ps1", "assess.py")
OBSERVER = "tests/contracts/PcbNormalPackingCensusContractTest.java"
PRODUCTION = "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java"
def read(n): return json.loads((HERE/n).read_text(encoding="utf-8-sig"))
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    a=read("accepted-inputs.json")
    assert subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT).decode().strip()==a["baseCommit"]
    assert subprocess.check_output(["git","branch","--show-current"],cwd=ROOT).decode().strip()==a["branch"]
    names=set(a["files"]); assert len(names)==1481
    assert {p.relative_to(ROOT).as_posix() for folder in ("src","tests/contracts") for p in (ROOT/folder).rglob("*.java")}=={n for n in names if n.endswith(".java") and n.startswith(("src/","tests/contracts/"))}
    machinery=subprocess.check_output(["git","ls-files","-z","--","scripts","*.xml","*.jar",".classpath","*.properties"],cwd=ROOT).decode().rstrip("\0").split("\0")
    assert {n:sha(ROOT/n) for n in sorted(set(machinery))}==a["machinery"]
    assert {p.relative_to(HERE/"accepted-source").as_posix():sha(p) for p in (HERE/"accepted-source").rglob("*") if p.is_file()}=={n:a["files"][n] for n in (OBSERVER,PRODUCTION)}
    assert sha(HERE.parent/"multirail-public-references-r1/candidate-receipt.txt")==a["priorCandidateReceiptSha256"]
    assert {n:sha(HERE.parent/"normal-packing-r2"/n) for n in a["transport"]}==a["transport"]
    return {"baseCommit":a["baseCommit"],"branch":a["branch"],"files":{n:sha(ROOT/n) for n in sorted(names)},"machinery":a["machinery"],"transport":a["transport"],"priorCandidateReceiptSha256":a["priorCandidateReceiptSha256"],"gates":{n:sha(HERE/n) for n in GATES},"acceptedSha256":sha(HERE/"accepted-inputs.json")}
def main():
    command,phase=sys.argv[1:]; assert command in ("freeze","audit") and phase in ("baseline","candidate")
    value=record();a=read("accepted-inputs.json")
    changed={n for n,h in value["files"].items() if h!=a["files"][n]}
    assert changed==({OBSERVER} if phase=="baseline" else {OBSERVER,PRODUCTION}),changed
    if phase=="candidate":
        b=read("baseline-inputs.json");assert all(value[k]==b[k] for k in value if k!="files")
        assert value["files"][OBSERVER]==b["files"][OBSERVER]
    path=HERE/(phase+"-inputs.json");archive=HERE/(phase+"-source")
    if command=="freeze":
        assert not path.exists() and not archive.exists()
        for n in sorted(changed):
            target=archive/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/n).read_bytes())
        path.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
    assert read(phase+"-inputs.json")==value,"Frozen source/gate drift"
    print("PASS lead-silk exact input audit phase="+phase+" sources=1481 gates=4")
if __name__=="__main__": main()
