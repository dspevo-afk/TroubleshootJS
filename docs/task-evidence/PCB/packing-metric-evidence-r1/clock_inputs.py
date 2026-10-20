"""Bind the unchanged production candidate and task-only replay witness."""
import hashlib,json,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
ATTEMPT=HERE/"clock-witness"
GATES=("clock-scope.md","clock_inputs.py","run_clock.ps1","clock_exit.ps1","qualify_clock.py","clock-source/FreshRb30GapDiagnostic.java")
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def record():
    assert runpy.run_path(str(HERE/"inputs.py"))["record"]()==read(HERE/"candidate-inputs.json")
    assert read(HERE/"diagnostic-attempt1/assessment.json")["status"]=="PASS_ACTUAL_PACKING_METRIC_OBSERVATION"
    return {"candidateSealSha256":sha(HERE/"candidate-inputs.json"),"gates":{n:sha(HERE/n) for n in GATES},"failedOperationSha256":sha(HERE/"contracts.txt"),"adapterSha256":"19af62225c5b3b4242343c81ebfa34a707df572eb1db80af5038d5ffdaa1dbec"}
def main():
    value=record();target=ATTEMPT/"inputs.json"
    assert sys.argv[1:] in (["freeze"],["audit"])
    if sys.argv[1]=="freeze":
        assert not ATTEMPT.exists();ATTEMPT.mkdir()
        for n in GATES:
            dest=ATTEMPT/"source"/n;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((HERE/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+"\n")
    assert read(target)==value
    assert {p.relative_to(ATTEMPT/"source").as_posix():sha(p) for p in (ATTEMPT/"source").rglob("*") if p.is_file()}==value["gates"]
    print("PASS clock witness exact candidate and helpers6")
if __name__=="__main__":main()
