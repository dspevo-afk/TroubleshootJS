"""Bind the fixed-envelope revision to unchanged accepted physical oracles."""
import hashlib,json,runpy,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError("Clearance input audit requires assertion-enabled Python")
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
OLD=HERE.with_name("reference-lead-clearance-r1")
OWNERS=("src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java",)+tuple("tests/contracts/"+n+".java" for n in ("PcbNormalPackingCensusContractTest","PcbCompactnessContractTest","PcbFunctionalGroupingContractTest","PcbPocketPackingContractTest","PcbSilkscreenScaleContractTest","PcbNormalPackingIdentityContractTest","A03IdentityContractTest"))
CHANGED={OWNERS[0],OWNERS[5]}
GATES=("scope.md","final_inputs.py","run_final.ps1","observe_final_exit.ps1","final_assess.py","final-suites.json","silkscreen-plan-fixtures.patch","silkscreen-plan-fixtures-draft.md")

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def write(p,v):p.write_text(json.dumps(v,indent=2)+"\n",encoding="utf-8")

def record():
    pre=read(HERE/"pre-edit-inputs.json");accepted=read(OLD/"accepted-inputs.json")
    assert subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT).decode().strip()==accepted["baseCommit"]
    assert subprocess.check_output(["git","branch","--show-current"],cwd=ROOT).decode().strip()==accepted["branch"]
    java={p.relative_to(ROOT).as_posix() for folder in ("src","tests/contracts") for p in (ROOT/folder).rglob("*.java")}
    assert java=={n for n in accepted["files"] if n.endswith(".java") and n.startswith(("src/","tests/contracts/"))}
    machinery=subprocess.check_output(["git","ls-files","-z","--","scripts","*.xml","*.jar",".classpath","*.properties"],cwd=ROOT).decode().rstrip("\0").split("\0")
    assert {n:sha(ROOT/n) for n in sorted(set(machinery))}==accepted["machinery"]==pre["machinery"]
    assert {n:sha(HERE/n) for n in GATES}==pre["gates"]
    assert {n:sha(ROOT/n) for n in pre["prerequisites"]}==pre["prerequisites"]
    assert {p.relative_to(HERE/"pre-edit-source").as_posix():sha(p) for p in (HERE/"pre-edit-source").rglob("*") if p.is_file()}=={n:pre["files"][n] for n in OWNERS}
    return {"baseCommit":accepted["baseCommit"],"files":{n:sha(ROOT/n) for n in sorted(accepted["files"])},"machinery":pre["machinery"],"gates":pre["gates"],"prerequisites":pre["prerequisites"],"preEditSha256":sha(HERE/"pre-edit-inputs.json")}

def main():
    mode,=sys.argv[1:];assert mode in ("prepare","preaudit","freeze","audit")
    if mode=="prepare":
        assert not(HERE/"pre-edit-inputs.json").exists()
        prior=runpy.run_path(str(OLD/"final_inputs.py"))["record"]()
        assert prior==read(OLD/"final-inputs.json")
        required=[p for p in OLD.rglob("*") if p.is_file() and (p.parent.name in ("accepted-source","final-source") or p.name in ("accepted-inputs.json","final-inputs.json","baseline-inputs.json","baseline-assessment.json","baseline-process-cleanup.json","baseline-receipt.txt","oracle-map.json","oracle_reader.py","oracle-consumer-migration.json","final-pre-edit-gates.json","helper-static-review.json"))]
        required += [p for p in (OLD/"accepted-source").rglob("*") if p.is_file()]
        required += [p for p in (OLD/"final-source").rglob("*") if p.is_file()]
        trial=HERE.with_name("lead-silk-local-trial-r1")
        required += [trial/n for n in ("assess.py","baseline-inputs.json","baseline-receipt.txt","baseline-timing.json","baseline-assessment.json","baseline-process-cleanup.json")]
        cause=HERE.with_name("reference-silk-crop-cause-r1")
        required += [cause/n for n in ("results.json","candidate-analysis.json","accepted80-lead-intersections.json","final-input-audit-final.txt")]
        for n in OWNERS:
            p=HERE/"pre-edit-source"/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/n).read_bytes())
        write(HERE/"pre-edit-inputs.json",{"baseCommit":prior["baseCommit"],"files":prior["files"],"machinery":prior["machinery"],"gates":{n:sha(HERE/n) for n in GATES},"prerequisites":{p.relative_to(ROOT).as_posix():sha(p) for p in sorted(set(required))}})
    value=record();pre=read(HERE/"pre-edit-inputs.json")
    if mode in ("prepare","preaudit"):
        assert value["files"]==pre["files"]
        print("PASS exact pre-edit clearance inputs sources=1481 owners=8 gates=8");return
    accepted=read(OLD/"accepted-inputs.json")
    assert len(value["files"])==1481
    assert {n for n,h in value["files"].items() if h!=pre["files"][n]}==CHANGED
    assert {n for n,h in value["files"].items() if h!=accepted["files"][n]}==set(OWNERS)
    if mode=="freeze":
        assert not(HERE/"final-inputs.json").exists()
        for n in OWNERS:
            p=HERE/"final-source"/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/n).read_bytes())
        write(HERE/"final-inputs.json",value)
    assert value==read(HERE/"final-inputs.json"),"Final source/gate drift"
    assert {p.relative_to(HERE/"final-source").as_posix():sha(p) for p in (HERE/"final-source").rglob("*") if p.is_file()}=={n:value["files"][n] for n in OWNERS}
    print("PASS exact final clearance inputs sources=1481 owners=8 gates=8")
if __name__=="__main__":main()
