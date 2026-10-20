"""Require actual maintained phase results and exact unchanged generated boards."""
import copy,difflib,hashlib,json,math,re,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def text(p):return p.read_text(encoding="utf-8-sig")
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def timing(t):
    assert t["status"]=="PASS" and type(t["exitCode"]) is int and t["exitCode"]==0
    assert type(t["elapsedSeconds"]) in (int,float) and math.isfinite(t["elapsedSeconds"]) and t["elapsedSeconds"]>=0
    assert t["terminalCleanupIncluded"] is True and t["failure"] is None
def census():
    old=HERE.parent/"empty-resistor-silk-r1/census-receipt.txt";new=HERE/"census-receipt.txt"
    families=("LED_INDICATOR","DIODE_PROTECTED_INDICATOR","PARALLEL_DUAL_INDICATOR","RC_DELAY","NPN_LOW_SIDE_SWITCH","NMOS_LOW_SIDE_SWITCH","RELAY_OUTPUT","SENSOR_CONTROL","RB15_CONTROL","COMPOSED_CONTROLLED_INDICATOR","RB30_CONTROL")
    keys={(f,r) for f in families for r in ("0","17","13","-9223372036854775808")}
    def rows(path,prefix):
        result={}
        for line in text(path).splitlines():
            if line.startswith(prefix):
                row=json.loads(line[len(prefix):]);key=(row["family"],row["root"])
                assert key not in result;result[key]=row
        assert set(result)==keys
        return result
    for prefix in ("PCB_NORMAL_PACKING_CASE ","PCB_NORMAL_REFERENCE_CASE ","PCB_LEAD_SILK_CASE ","PCB_NORMAL_SILK_CASE "):
        a,b=rows(old,prefix),rows(new,prefix)
        for key in keys:
            first,second=a[key],b[key]
            skip={"elapsedMillis","maxAdvanceMillis","generationRoutingMillis"} if prefix=="PCB_NORMAL_PACKING_CASE " else set()
            assert set(first)==set(second) and {k:v for k,v in first.items() if k not in skip}=={k:v for k,v in second.items() if k not in skip},(prefix,key)
            if skip:assert second["status"]=="ACCEPTED" and second["graphBefore"]==second["graphAfter"]
    whitespace=lambda p:[json.loads(s[len("PCB_WHITESPACE_CASE "):]) for s in text(p).splitlines() if s.startswith("PCB_WHITESPACE_CASE ")]
    a,b=whitespace(old),whitespace(new);assert len(a)==len(b)==44 and a==b
    (HERE/"census-comparison.json").write_text(json.dumps({"status":"PASS","cases":44,"allFullReferenceLeadTitleRowsExact":132,"packingRowsExactExceptExisting3TimingFields":44,"fullOrderedWhitespaceRowsExact":44,"limits":"No hash/text/bounds/style/target/metric/work/oracle exclusions; finite unchanged44 corpus only"},indent=2)+"\n",encoding="utf-8")
def main():
    if sys.argv[1:]==["--canaries"]:
        sample={"status":"PASS","exitCode":0,"elapsedSeconds":1.0,"terminalCleanupIncluded":True,"failure":None};timing(sample)
        for k,v in (("exitCode",False),("exitCode",0.0),("exitCode",2),("status","FAIL"),("elapsedSeconds",False),("elapsedSeconds",float("nan"))):
            value=copy.deepcopy(sample);value[k]=v
            try:timing(value)
            except AssertionError:pass
            else:raise RuntimeError("Bad timing accepted")
        (HERE/"pre-edit-canaries.json").write_text(json.dumps({"status":"PASS","positive":1,"rejected":6,"limits":"Pure timing reader framing only, no native/host execution"},indent=2)+"\n",encoding="utf-8")
        print("PASS pure phase timing framing canaries1/6");return
    phase,=sys.argv[1:];assert phase in ("contracts","census","target","build")
    inp=runpy.run_path(str(HERE/"inputs.py"));candidate=read(HERE/"candidate-inputs.json");assert inp["record"]()==candidate
    for which in ("baseline","candidate"):
        seal=read(HERE/(which+"-inputs.json"));archive=HERE/(which+"-source")
        assert {p.relative_to(archive).as_posix():sha(p) for p in archive.rglob("*") if p.is_file()}=={n:seal["files"][n] for n in inp["OWNERS"]}
    name="tests/contracts/PcbPackingProbeReceiptContractTest.java"
    before,after=[text(HERE/(s+"-source")/name) for s in ("baseline","candidate")]
    # Constructor migration is scoped; every original literal oracle must remain.
    assert set(re.findall(r'"(?:\\.|[^"\\])*"',before))<=set(re.findall(r'"(?:\\.|[^"\\])*"',after))
    t=read(HERE/(phase+"-timing.json"));timing(t);assert t["phase"]==phase
    launchPath=HERE/(phase+"-process-launch.json");launch=read(launchPath);cleanup=read(HERE/(phase+"-process-cleanup.json"))
    assert cleanup["status"]=="PASS" and cleanup["phase"]==phase and type(cleanup["actualExecExitCode"]) is int and cleanup["actualExecExitCode"]==0
    assert cleanup["launchSha256"]==sha(launchPath) and len(launch["drivers"])==len(cleanup["checks"])==2
    assert [{k:r[k] for k in ("pid","startTicks","executable")} for r in cleanup["checks"]]==launch["drivers"] and all(r["recordedBirthExited"] is True for r in cleanup["checks"])
    for side in ("before","after"):assert text(HERE/(phase+"-input-audit-"+side+".txt")).strip()=="PASS packing metric exact inputs phase=candidate sources=1482 gates=6"
    log=text(HERE/(phase+".txt"))
    if phase=="build":
        for marker in ("Compiling module com.lushprojects.circuitjs1.circuitjs1","Compiling 5 permutations","Compile of permutations succeeded","Compilation succeeded --","Link succeeded","Linking succeeded --"):assert log.count(marker)==1,marker
        assert "BUILD_FAILURE" not in log and "BUILD_INFRASTRUCTURE" not in log
    else:
        suites=read(HERE/"suites.json")[phase]
        assert log.count("PASS: focused current contracts; "+str(len(suites))+" Java suites. Full matrix and independent oracles NOT RUN.")==1 and "CURRENT_CONTRACT_FAILURE:" not in log
        definitions=dict(re.findall(r"Name = '([^']+)'; Marker = '([^']+)'",text(ROOT/"scripts/verify-current-contracts.ps1")))
        receipt=text(HERE/(phase+"-receipt.txt"))
        for suite in suites:assert re.search(r"(?m)^PASS: "+re.escape(definitions[suite]),receipt),suite
        if phase=="contracts":assert int(re.search(r"PASS: PCB packing probe receipt contracts assertions=(\d+)",receipt).group(1))>31
        elif phase=="census":census()
        else:
            rows=[json.loads(s[len("Q30_CORPUS_JSON:"):]) for s in receipt.splitlines() if s.startswith("Q30_CORPUS_JSON:")]
            assert len(rows)==1;row=rows[0]
            prior=read(HERE.parent/"fresh-multirail-gap-r1/native-route-assessment.json")["row"]
            assert row["seed"]=="-5776064669694490064" and row["outcome"]==row["normalAdmissionOutcome"]=="ACCEPTED" and row["cleanup"] is True and row["contractValidated"] is True
            assert row["coldRouteCanonical"]==row["warmRouteCanonical"]
            def normalize_only_clock(value):
                result,count=re.subn(r"(normal-packing@2;attempts=[01];orderings=\d+;expansions=\d+;millis=)\d+(;route=)",r"\1[wall-clock]\2",value)
                assert count==1;return result
            assert normalize_only_clock(row["coldRouteCanonical"])==normalize_only_clock(prior["coldRouteCanonical"])
            for key in ("physicalFingerprintHash","layoutFingerprintHash","partCount","hypothesisCount","observationCount","topologyAxis","support","coldPlacementAdvances","coldRoutingAdvances","coldRouteOutcomes","warmRouteOutcomes","proofPhase"):
                assert row[key]==prior[key],key
            assert log.count("Q30_CORPUS_CENSUS attempted=1 accepted=1 rejected=0 failed=0 childBudgetMs=60000")==1
    (HERE/(phase+"-assessment.json")).write_text(json.dumps({"status":"PASS","phase":phase,"elapsedSeconds":t["elapsedSeconds"],"sources":1482,"limits":"Actual named final-source phase only; root production/player/publication acceptance separate"},indent=2)+"\n",encoding="utf-8")
    print("PASS actual packing metric phase "+phase)
if __name__=="__main__":main()
