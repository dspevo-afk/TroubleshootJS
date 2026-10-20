"""Final four-owner scope; preserve and audit the initial measured evidence."""
import hashlib,json,runpy,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
PARENT=HERE.parent
ROOT=HERE.parents[4]
BASE="3028ceb0e90e23b2c2ac0e35eaf493609985bffa"
GATES=("scope.md","inputs.py","qualify.py","run_phase.ps1","suites.json","record_exit.ps1")
OWNERS={"src/com/lushprojects/circuitjs1/client/PcbPackingProbeReceipt.java","src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java","tests/contracts/PcbPackingProbeReceiptContractTest.java","tests/contracts/MediumBoardPhysicalPolicyContractTest.java","tests/contracts/Q30NormalCorpusContractTest.java"}
def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record(phase="candidate"):
    assert phase in ("baseline","candidate")
    assert subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT).decode().strip()==BASE
    assert subprocess.check_output(["git","branch","--show-current"],cwd=ROOT).decode().strip()=="codex/q60-faulted-profile-counts"
    accepted=read(PARENT/"accepted-inputs.json");previous=read(PARENT/"candidate-inputs.json")
    files={n:sha(ROOT/n) for n in accepted["files"]}
    assert len(files)==1482
    if phase=="baseline":assert files==read(PARENT/"oracle-r3-attempt1/candidate-inputs.json")["files"]
    else:
        assert {n for n in files if files[n]!=accepted["files"][n]}==OWNERS
        assert {n for n in files if files[n]!=previous["files"][n]}=={"tests/contracts/MediumBoardPhysicalPolicyContractTest.java","tests/contracts/Q30NormalCorpusContractTest.java"}
    java={p.relative_to(ROOT).as_posix() for folder in ("src","tests/contracts") for p in (ROOT/folder).rglob("*.java")}
    assert java=={n for n in files if n.endswith(".java") and n.startswith(("src/","tests/contracts/"))}
    paths=subprocess.check_output(["git","ls-files","-z","--","scripts","*.xml","*.jar",".classpath","*.properties"],cwd=ROOT).decode().rstrip("\0").split("\0")
    machinery={n:sha(ROOT/n) for n in sorted(set(paths))}
    assert len(machinery)==24 and machinery==accepted["machinery"]
    # Production remains exact; every final native gate is fresh after all test edits.
    assert all(files[n]==previous["files"][n] for n in files if n.startswith("src/"))
    diagnostic=read(PARENT/"diagnostic-attempt1/inputs.json")
    assert all(sha(PARENT/n)==h for n,h in diagnostic["gates"].items())
    observed=read(PARENT/"diagnostic-attempt1/assessment.json")
    assert observed["status"]=="PASS_ACTUAL_PACKING_METRIC_OBSERVATION" and observed["failedPredicates"]==["viasNonincreasing"]
    assert read(PARENT/"clock-witness/assessment.json")["status"]=="PASS_ONLY_PACKING_CLOCK_REPLAY_DIFFERENCE"
    return {"baseCommit":BASE,"phase":phase,"files":files,"machinery":machinery,"gates":{n:sha(HERE/n) for n in GATES},"originalCandidateSealSha256":sha(PARENT/"candidate-inputs.json"),"originalAcceptedSealSha256":sha(PARENT/"accepted-inputs.json"),"historicalObservedEvidenceNotReusedAsFinal":{n:sha(PARENT/n) for n in ("diagnostic-attempt1/inputs.json","diagnostic-attempt1/assessment.json","diagnostic-attempt1/receipt.txt","diagnostic-attempt1/process-cleanup.json","diagnostic-adapter-manifest.json","clock-witness/inputs.json","clock-witness/assessment.json","clock-witness/receipt.txt","clock-witness/process-cleanup.json","contracts-timing.json","contracts-process-cleanup.json")},"previousDiagnosis":previous["previousDiagnosis"],"censusBaseline":previous["censusBaseline"]}
def main():
    command,phase=sys.argv[1:];assert command in ("freeze","audit")
    value=record(phase)
    if phase=="candidate":
        baseline=read(HERE/"baseline-inputs.json")
        assert all(value[k]==baseline[k] for k in value if k not in ("phase","files"))
    target=HERE/(phase+"-inputs.json")
    if command=="freeze":
        assert not target.exists();archive=HERE/(phase+"-source");assert not archive.exists()
        for n in OWNERS:
            dest=archive/n;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((ROOT/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
    assert read(target)==value
    print("PASS packing metric exact inputs phase="+phase+" sources=1482 gates=6")
if __name__=="__main__":main()
