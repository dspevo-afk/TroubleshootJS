"""Freeze the bounded eleven-owner packing interpretation experiment."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
BASE="076674ffe402dffee7238352533fc117cb4b1d82"
GATES=("scope.md","inputs.py","run_fixture.ps1","record_fixture_exit.ps1","qualify_fixture.py")
OWNERS={"src/com/lushprojects/circuitjs1/client/"+n+".java" for n in ("PcbLayerRoutingPrototype","SeededPcbLayoutGenerator","PcbPlacementConstraints","PcbPackingProbeReceipt")}|{"tests/contracts/"+n+".java" for n in ("P07TwoLayerContractTest","PcbNormalPackingIdentityContractTest","PcbCompactnessContractTest","A03IdentityContractTest","PcbPackingProbeReceiptContractTest","MediumBoardPhysicalPolicyContractTest","Q30NormalCorpusContractTest")}
def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record(phase="candidate"):
    assert phase in ("baseline","candidate")
    assert subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT).decode().strip()==BASE
    assert subprocess.check_output(["git","branch","--show-current"],cwd=ROOT).decode().strip()=="codex/q60-faulted-profile-counts"
    previous=HERE.parent/"packing-metric-evidence-r1";old=read(previous/"final-r2/candidate-inputs.json")
    assert read(previous/"publication.json")["sha"]==BASE
    files={n:sha(ROOT/n) for n in old["files"]}
    assert len(files)==1482 and {n for n in files if files[n]!=old["files"][n]}==(OWNERS if phase=="candidate" else set())
    java={p.relative_to(ROOT).as_posix() for folder in ("src","tests/contracts") for p in (ROOT/folder).rglob("*.java")}
    assert java=={n for n in files if n.endswith(".java") and n.startswith(("src/","tests/contracts/"))}
    paths=subprocess.check_output(["git","ls-files","-z","--","scripts","*.xml","*.jar",".classpath","*.properties"],cwd=ROOT).decode().rstrip("\0").split("\0")
    machinery={n:sha(ROOT/n) for n in sorted(set(paths))};assert len(machinery)==24 and machinery==old["machinery"]
    return {"baseCommit":BASE,"phase":phase,"files":files,"machinery":machinery,"gates":{n:sha(HERE/n) for n in GATES},"publishedBaseline":{n:sha(previous/n) for n in ("final-r2/candidate-inputs.json","final-r2/census-receipt.txt","final-r2/census-assessment.json","diagnostic-final-r4/assessment.json","player-assessment.json","post-player-provenance.json","publication.json")}}
def main():
    command,phase=sys.argv[1:];assert command in ("freeze","audit")
    value=record(phase)
    if phase=="candidate":
        b=read(HERE/"baseline-inputs.json");assert all(value[k]==b[k] for k in value if k not in ("phase","files"))
    target=HERE/(phase+"-inputs.json")
    if command=="freeze":
        assert not target.exists();archive=HERE/(phase+"-source");assert not archive.exists()
        for n in OWNERS:
            f=archive/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes((ROOT/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+"\n")
    assert read(target)==value
    print("PASS packing via-cap exact inputs phase="+phase+" sources=1482 gates=5")
if __name__=="__main__":main()
