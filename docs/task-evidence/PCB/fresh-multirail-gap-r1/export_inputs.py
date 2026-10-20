"""Freeze the task-only exporter and its actual native adapter before execution."""
import hashlib,json,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
ATTEMPT=HERE/"export-attempt1"
GATES=("export-scope.md","make_export_adapter.py","export-adapter-manifest.json","export-adapter-diff.txt",
       "run_export.ps1","export_inputs.py","qualify_export.py","record_export_exit.ps1",
       "kept-path-reader.json","export-integration-review.json",
       "export-source/FreshRb30GapDiagnostic.java","export-source/NOTES.md")
def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    phase=read(HERE/"inputs.json")
    assert runpy.run_path(str(HERE/"inputs.py"))["record"]()==phase
    native=read(HERE/"native-route-assessment.json")
    assert native["status"]=="PASS_EXACT_NATIVE_ROUTE_DIAGNOSIS" and native["seed"]=="-5776064669694490064"
    assert native["packingDisposition"]=="KEPT" and native["row"]["coldRouteCanonical"]==native["row"]["warmRouteCanonical"]
    adapter=read(HERE/"export-adapter-manifest.json")
    assert adapter["maintainedSourceSha256"]==phase["machinery"]["scripts/verify-current-contracts.ps1"]
    assert len(adapter["changes"])==5 and all(x["replacements"]==1 for x in adapter["changes"])
    assert {p.name for p in (HERE/"export-source").iterdir() if p.is_file()}=={"FreshRb30GapDiagnostic.java","NOTES.md"}
    return {"baseCommit":phase["baseCommit"],"phaseAInputSealSha256":sha(HERE/"inputs.json"),
            "phaseAAssessmentSha256":sha(HERE/"native-route-assessment.json"),"phaseAReceiptSha256":sha(HERE/"native-route-receipt.txt"),
            "gates":{n:sha(HERE/n) for n in GATES},"sourceInputs":1482,"machinery":24,
            "adapterSha256":adapter["generatedSha256"],"seed":native["seed"]}
def main():
    assert sys.argv[1:] in (["freeze"],["audit"])
    value=record();target=ATTEMPT/"inputs.json"
    if sys.argv[1]=="freeze":
        assert not ATTEMPT.exists(),"Preserve attempted export inputs/source"
        ATTEMPT.mkdir()
        for name in GATES:
            dest=ATTEMPT/"source"/name;dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes((HERE/name).read_bytes())
        target.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
    assert read(target)==value
    assert {p.relative_to(ATTEMPT/"source").as_posix():sha(p) for p in (ATTEMPT/"source").rglob("*") if p.is_file()}==value["gates"]
    print("PASS frozen selected RB30 export source1482 machinery24 helpers12 exact-seed")
if __name__=="__main__":main()
