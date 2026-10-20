"""Pin the separate axial paint owner and additive fixture without geometry churn."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
BASE="891d6ce75ff97fd62adc98fc88b331230cf210f1"
GATES=("scope.md","inputs.py","qualify.py","run_phase.ps1","suites.json","record_exit.ps1")
OWNERS={"src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java","tests/contracts/PcbSilkscreenScaleContractTest.java"}
def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record(phase="candidate"):
    assert phase in ("baseline","candidate")
    assert subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT).decode().strip()==BASE
    assert subprocess.check_output(["git","branch","--show-current"],cwd=ROOT).decode().strip()=="codex/q60-faulted-profile-counts"
    accepted=read(HERE/"accepted-inputs.json")
    assert accepted["baseCommit"]==BASE and len(accepted["files"])==1482
    files={p:sha(ROOT/p) for p in accepted["files"]}
    assert {p for p in files if files[p]!=accepted["files"][p]}==(OWNERS if phase=="candidate" else set())
    java={p.relative_to(ROOT).as_posix() for folder in ("src","tests/contracts") for p in (ROOT/folder).rglob("*.java")}
    assert java=={p for p in files if p.endswith(".java") and p.startswith(("src/","tests/contracts/"))}
    paths=subprocess.check_output(["git","ls-files","-z","--","scripts","*.xml","*.jar",".classpath","*.properties"],cwd=ROOT).decode().rstrip("\0").split("\0")
    machinery={p:sha(ROOT/p) for p in sorted(set(paths))}
    assert len(machinery)==24 and machinery==accepted["machinery"]
    baseline=HERE.parent/"empty-resistor-baseline-r1"
    prior=HERE.parent/"legacy-resistor-references-r1"
    assert read(baseline/"inputs.json")["files"]==accepted["files"]
    assert read(prior/"candidate-inputs.json")["files"]==accepted["files"]
    return {"baseCommit":BASE,"phase":phase,"files":files,"machinery":machinery,
            "gates":{p:sha(HERE/p) for p in GATES},"acceptedSealSha256":sha(HERE/"accepted-inputs.json"),
            "baselineEvidence":{p:sha(baseline/p) for p in ("inputs.json","baseline-assessment.json","post-player-provenance.json","baseline-installed-r1.jpg","baseline-empty-r1-top.jpg","process-resource-cleanup.json","source-scope-reader.json")},
            "censusBaseline":{p:sha(prior/p) for p in ("candidate-inputs.json","census-receipt.txt","census-assessment.json","census-comparison.json","publication.json")}}
def main():
    command,phase=sys.argv[1:];assert command in ("freeze","audit")
    value=record(phase)
    if phase=="candidate":
        baseline=read(HERE/"baseline-inputs.json")
        assert all(value[k]==baseline[k] for k in ("baseCommit","machinery","gates","acceptedSealSha256","baselineEvidence","censusBaseline"))
    target=HERE/(phase+"-inputs.json")
    if command=="freeze":
        assert not target.exists(),"Preserve attempted manifest"
        archive=HERE/(phase+"-source");assert not archive.exists()
        for name in OWNERS:
            dest=archive/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((ROOT/name).read_bytes())
        target.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
    assert read(target)==value
    print("PASS empty axial exact inputs phase="+phase+" sources=1482 gates=6")
if __name__=="__main__":main()
