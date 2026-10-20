"""Freeze read-only exact-seed native diagnosis at the published source boundary."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
BASE="030678bba7d861d0a0a7c3fd44317e589d6c320e"
GATES=("scope.md","inputs.py","run_native.ps1","qualify.py","record_exit.ps1","request.json","source-scope-reader.json")
def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    assert subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT).decode().strip()==BASE
    assert subprocess.check_output(["git","branch","--show-current"],cwd=ROOT).decode().strip()=="codex/q60-faulted-profile-counts"
    prior=HERE.parent/"fresh-board-visual-r4"
    accepted=read(prior/"inputs.json")
    files={p:sha(ROOT/p) for p in accepted["files"]}
    assert len(files)==1482 and files==accepted["files"]
    java={p.relative_to(ROOT).as_posix() for folder in ("src","tests/contracts") for p in (ROOT/folder).rglob("*.java")}
    assert java=={p for p in files if p.endswith(".java") and p.startswith(("src/","tests/contracts/"))}
    paths=subprocess.check_output(["git","ls-files","-z","--","scripts","*.xml","*.jar",".classpath","*.properties"],cwd=ROOT).decode().rstrip("\0").split("\0")
    machinery={p:sha(ROOT/p) for p in sorted(set(paths))}
    assert len(machinery)==24 and machinery==accepted["machinery"]
    request=read(HERE/"request.json")
    assert request["seed"]=="-5776064669694490064" and request["suite"]==["Q30NormalCorpusContractTest"]
    assert read(prior/"multirail-identity.json")["replay"]=="tsj-alpha/6/MEDIUM/RB30_CONTROL/"+request["seed"]
    assert read(prior/"publication.json")["sha"]==BASE
    return {"baseCommit":BASE,"files":files,"machinery":machinery,"gates":{p:sha(HERE/p) for p in GATES},
        "sampleEvidence":{p:sha(prior/p) for p in ("inputs.json","multirail-identity.json","multirail-fitted.jpg","multirail-detail.jpg","player-assessment.json","post-player-provenance.json","process-resource-cleanup.json","publication.json")}}
def main():
    assert sys.argv[1:] in (["freeze"],["audit"])
    value=record();target=HERE/"inputs.json"
    if sys.argv[1]=="freeze":
        assert not target.exists(),"Preserve attempted input freeze"
        target.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
    assert read(target)==value
    print("PASS exact fresh multi-rail native inputs1482 machinery24 gates7 seed=-5776064669694490064")
if __name__=="__main__":main()
