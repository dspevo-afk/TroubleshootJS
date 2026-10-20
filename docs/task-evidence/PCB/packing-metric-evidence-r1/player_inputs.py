"""Freeze the actual final candidate and visible player-flow helpers."""
import hashlib,json,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
GATES=("player-plan.json","player_inputs.py","launch_player.ps1","stop_player.ps1","observe_player_host.ps1","review_player.py")
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def record():
    assert runpy.run_path(str(HERE/"final-r2/inputs.py"))["record"]()==read(HERE/"final-r2/candidate-inputs.json")
    for phase in ("contracts","census","target","build"):
        assert read(HERE/("final-r2/"+phase+"-assessment.json"))["status"]=="PASS"
    return {"candidateSha256":sha(HERE/"final-r2/candidate-inputs.json"),"gates":{n:sha(HERE/n) for n in GATES},"finalGates":{p:sha(HERE/("final-r2/"+p+"-assessment.json")) for p in ("contracts","census","target","build")}}
def main():
    assert sys.argv[1:] in (["freeze"],["audit","candidate"])
    value=record();target=HERE/"player-inputs.json"
    if sys.argv[1]=="freeze":
        assert not target.exists();target.write_text(json.dumps(value,indent=2)+"\n")
    assert read(target)==value
    print("PASS final packing metric player source1482 machinery24 helpers6")
if __name__=="__main__":main()
