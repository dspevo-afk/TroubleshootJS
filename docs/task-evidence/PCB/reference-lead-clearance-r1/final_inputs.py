"""Seal the final current-only consumers against accepted-source expectations."""
import hashlib,json,runpy,subprocess,sys
from pathlib import Path
if not __debug__: raise RuntimeError("Final clearance audit requires assertion-enabled Python")
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
OWNERS=("src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java",)+tuple("tests/contracts/"+n+".java" for n in ("PcbNormalPackingCensusContractTest","PcbCompactnessContractTest","PcbFunctionalGroupingContractTest","PcbPocketPackingContractTest","PcbSilkscreenScaleContractTest","PcbNormalPackingIdentityContractTest","A03IdentityContractTest"))
GATES=("final-scope.md","final_inputs.py","run_final.ps1","observe_final_exit.ps1","final_assess.py","final-suites.json","silkscreen-fixtures.patch","silkscreen-fixtures-draft.md")
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(n):return json.loads((HERE/n).read_text(encoding="utf-8-sig"))
def record():
    a=read("accepted-inputs.json");pre=read("final-pre-edit-gates.json")
    assert subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT).decode().strip()==a["baseCommit"]
    assert subprocess.check_output(["git","branch","--show-current"],cwd=ROOT).decode().strip()==a["branch"]
    assert len(a["files"])==1481
    assert {p.relative_to(ROOT).as_posix() for folder in ("src","tests/contracts") for p in (ROOT/folder).rglob("*.java")}=={n for n in a["files"] if n.endswith(".java") and n.startswith(("src/","tests/contracts/"))}
    machinery=subprocess.check_output(["git","ls-files","-z","--","scripts","*.xml","*.jar",".classpath","*.properties"],cwd=ROOT).decode().rstrip("\0").split("\0")
    assert {n:sha(ROOT/n) for n in sorted(set(machinery))}==a["machinery"]
    assert {p.relative_to(HERE/"final-accepted-source").as_posix():sha(p) for p in (HERE/"final-accepted-source").rglob("*") if p.is_file()}=={n:a["files"][n] for n in OWNERS}
    assert {n:sha(HERE/n) for n in GATES}==pre["gates"]
    assert {n:sha(HERE/n) for n in pre["prerequisites"]}==pre["prerequisites"]
    assert sha(HERE.parent/"lead-silk-local-trial-r1/candidate-receipt.txt")==pre["passingTrialReceiptSha256"]
    return {"baseCommit":a["baseCommit"],"files":{n:sha(ROOT/n) for n in sorted(a["files"])},"machinery":a["machinery"],"gates":pre["gates"],"prerequisites":pre["prerequisites"],"preEditSha256":sha(HERE/"final-pre-edit-gates.json")}
def main():
    assert sys.argv[1:] in (["freeze"],["audit"])
    value=record();a=read("accepted-inputs.json")
    assert {n for n,h in value["files"].items() if h!=a["files"][n]}==set(OWNERS)
    assert value["files"][OWNERS[0]]==read("pre-edit-gates.json")["passingTrialGeneratorSha256"]
    assert value["files"][OWNERS[1]]==read("pre-edit-gates.json")["passingTrialObserverSha256"]
    if sys.argv[1]=="freeze":
        assert not(HERE/"final-inputs.json").exists()
        for n in OWNERS:
            p=HERE/"final-source"/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/n).read_bytes())
        (HERE/"final-inputs.json").write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
    assert value==read("final-inputs.json"),"Final-source/gate drift"
    print("PASS exact final clearance inputs sources=1481 owners=8 gates=8")
if __name__=="__main__":main()
