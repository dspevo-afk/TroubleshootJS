"""Freeze task-only observation helpers against the final production candidate."""
import hashlib,json,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
ATTEMPT=HERE/"diagnostic-attempt1"
GATES=("diagnostic-scope.md","diagnostic_inputs.py","qualify_diagnostic.py",
       "make_export_adapter.py","diagnostic-adapter-manifest.json",
       "diagnostic-adapter-diff.txt","run_diagnostic.ps1","record_diagnostic_exit.ps1",
       "diagnostic-source/FreshRb30GapDiagnostic.java","integration-review.json")

def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def record():
    candidate=read(HERE/"candidate-inputs.json")
    assert runpy.run_path(str(HERE/"inputs.py"))["record"]()==candidate
    adapter=read(HERE/"diagnostic-adapter-manifest.json")
    assert adapter["maintainedSourceSha256"]==candidate["machinery"]["scripts/verify-current-contracts.ps1"]
    assert adapter["generatedSha256"]=="19af62225c5b3b4242343c81ebfa34a707df572eb1db80af5038d5ffdaa1dbec"
    assert len(adapter["changes"])==5 and all(x["replacements"]==1 for x in adapter["changes"])
    assert {p.name for p in (HERE/"diagnostic-source").iterdir()}=={"FreshRb30GapDiagnostic.java"}
    return {"baseCommit":candidate["baseCommit"],"candidateSealSha256":sha(HERE/"candidate-inputs.json"),
            "gates":{n:sha(HERE/n) for n in GATES},"sourceInputs":1482,"machinery":24,
            "previousAssessmentSha256":sha(HERE.parent/"fresh-multirail-gap-r1/export-attempt1/assessment-r2.json"),
            "adapterSha256":adapter["generatedSha256"],"seed":"-5776064669694490064"}

def main():
    assert sys.argv[1:] in (["freeze"],["audit"])
    value=record();target=ATTEMPT/"inputs.json"
    if sys.argv[1]=="freeze":
        assert not ATTEMPT.exists(),"Preserve attempted diagnostic inputs/source"
        ATTEMPT.mkdir()
        for name in GATES:
            dest=ATTEMPT/"source"/name;dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes((HERE/name).read_bytes())
        target.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
    assert read(target)==value
    assert {p.relative_to(ATTEMPT/"source").as_posix():sha(p) for p in (ATTEMPT/"source").rglob("*") if p.is_file()}==value["gates"]
    print("PASS frozen packing metric diagnostic source1482 machinery24 helpers10 exact-seed")
if __name__=="__main__":main()
