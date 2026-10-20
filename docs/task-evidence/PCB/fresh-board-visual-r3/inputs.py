"""Pin exact accepted source, machinery and compiled reuse for a read-only sample."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__: raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
PRIOR=HERE.parent/"legacy-resistor-references-r1"
BASE="4934d4a05e743e88039fdacdc45a054e18961eeb"
GATES=("scope.md","inputs.py","launch_player.ps1","stop_player.ps1","observe_player_host.ps1")
def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    assert subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT).decode().strip()==BASE
    assert subprocess.check_output(["git","branch","--show-current"],cwd=ROOT).decode().strip()=="codex/q60-faulted-profile-counts"
    old=read(PRIOR/"candidate-inputs.json")
    files={p:sha(ROOT/p) for p in old["files"]}
    assert len(files)==1482 and files==old["files"]
    java={p.relative_to(ROOT).as_posix() for folder in ("src","tests/contracts") for p in (ROOT/folder).rglob("*.java")}
    assert java=={p for p in files if p.endswith(".java") and p.startswith(("src/","tests/contracts/"))}
    paths=subprocess.check_output(["git","ls-files","-z","--","scripts","*.xml","*.jar",".classpath","*.properties"],cwd=ROOT).decode().rstrip("\0").split("\0")
    machinery={p:sha(ROOT/p) for p in sorted(set(paths))}
    assert len(machinery)==24 and machinery==old["machinery"]
    assert read(HERE/"reused-execution.json")==read(PRIOR/"post-player-provenance.json")
    return {"baseCommit":BASE,"files":files,"machinery":machinery,
            "gates":{p:sha(HERE/p) for p in GATES},
            "reusedEvidence":{p:sha(PRIOR/p) for p in ("candidate-inputs.json","fixtures-assessment.json","contracts-assessment.json","census-assessment.json","census-comparison.json","build-assessment.json","player-assessment.json","post-player-provenance.json","publication.json")},
            "executionSha256":sha(HERE/"reused-execution.json")}
def main():
    assert sys.argv[1:] in (["freeze"],["audit","candidate"])
    value=record();target=HERE/"inputs.json"
    if sys.argv[1]=="freeze":
        assert not target.exists(),"Preserve prior evidence"
        target.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
    assert read(target)==value
    print("PASS read-only fresh sample exact1482 machinery24 gates5 compiled6 reuse")
if __name__=="__main__":main()
