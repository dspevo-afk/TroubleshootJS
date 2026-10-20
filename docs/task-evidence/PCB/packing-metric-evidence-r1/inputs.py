"""Pin the actual packing observation owners, scoped constructor fixture and gates."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
BASE="3028ceb0e90e23b2c2ac0e35eaf493609985bffa"
GATES=("scope.md","inputs.py","qualify.py","run_phase.ps1","suites.json","record_exit.ps1")
OWNERS={"src/com/lushprojects/circuitjs1/client/PcbPackingProbeReceipt.java",
        "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java",
        "tests/contracts/PcbPackingProbeReceiptContractTest.java"}
def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record(phase="candidate"):
    assert phase in ("baseline","candidate")
    assert subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT).decode().strip()==BASE
    assert subprocess.check_output(["git","branch","--show-current"],cwd=ROOT).decode().strip()=="codex/q60-faulted-profile-counts"
    accepted=read(HERE/"accepted-inputs.json")
    files={n:sha(ROOT/n) for n in accepted["files"]}
    assert len(files)==1482 and {n for n in files if files[n]!=accepted["files"][n]}==(OWNERS if phase=="candidate" else set())
    java={p.relative_to(ROOT).as_posix() for folder in ("src","tests/contracts") for p in (ROOT/folder).rglob("*.java")}
    assert java=={n for n in files if n.endswith(".java") and n.startswith(("src/","tests/contracts/"))}
    paths=subprocess.check_output(["git","ls-files","-z","--","scripts","*.xml","*.jar",".classpath","*.properties"],cwd=ROOT).decode().rstrip("\0").split("\0")
    machinery={n:sha(ROOT/n) for n in sorted(set(paths))}
    assert len(machinery)==24 and machinery==accepted["machinery"]
    previous=HERE.parent/"fresh-multirail-gap-r1"
    census=HERE.parent/"empty-resistor-silk-r1"
    assert read(census/"candidate-inputs.json")["files"]==accepted["files"]
    assert read(previous/"publication.json")["sha"]==BASE
    return {"baseCommit":BASE,"phase":phase,"files":files,"machinery":machinery,
        "gates":{n:sha(HERE/n) for n in GATES},"acceptedSealSha256":sha(HERE/"accepted-inputs.json"),
        "previousDiagnosis":{n:sha(previous/n) for n in ("inputs.json","native-route-assessment.json","native-route-receipt.txt","export-attempt1/assessment-r2.json","export-review-r2-scope.md","geometry-analysis.json","publication.json")},
        "censusBaseline":{n:sha(census/n) for n in ("candidate-inputs.json","census-receipt.txt","census-assessment.json","census-comparison.json","publication.json")}}
def main():
    command,phase=sys.argv[1:];assert command in ("freeze","audit")
    value=record(phase)
    if phase=="candidate":
        baseline=read(HERE/"baseline-inputs.json")
        assert all(value[k]==baseline[k] for k in ("baseCommit","machinery","gates","acceptedSealSha256","previousDiagnosis","censusBaseline"))
    target=HERE/(phase+"-inputs.json")
    if command=="freeze":
        assert not target.exists(),"Preserve attempted input manifest"
        archive=HERE/(phase+"-source");assert not archive.exists()
        for n in OWNERS:
            dest=archive/n;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((ROOT/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
    assert read(target)==value
    print("PASS packing metric exact inputs phase="+phase+" sources=1482 gates=6")
if __name__=="__main__":main()
