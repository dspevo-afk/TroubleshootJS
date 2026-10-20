"""Read actual final phase evidence; do not turn a partial phase into PASS."""
import hashlib,json,math,re,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError("Final clearance assessment requires assertion-enabled Python")
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
PHASES=("fixtures","contracts","census","matrix","build")
FAMILIES=("LED_INDICATOR","DIODE_PROTECTED_INDICATOR","PARALLEL_DUAL_INDICATOR","RC_DELAY","NPN_LOW_SIDE_SWITCH","NMOS_LOW_SIDE_SWITCH","RELAY_OUTPUT","SENSOR_CONTROL","RB15_CONTROL","COMPOSED_CONTROLLED_INDICATOR","RB30_CONTROL")
def read(n):return json.loads((HERE/n).read_text(encoding="utf-8-sig"))
def text(n):return (HERE/n).read_text(encoding="utf-8-sig")
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def timing(t):
    assert t["status"]=="PASS" and type(t["exitCode"])is int and t["exitCode"]==0
    assert type(t["elapsedSeconds"])in(int,float) and math.isfinite(t["elapsedSeconds"]) and t["elapsedSeconds"]>=0
    assert t["terminalCleanupIncluded"] is True and t["failure"] is None
def common(phase):
    inp=runpy.run_path(str(HERE/"final_inputs.py"));assert inp["record"]()==read("final-inputs.json")
    t=read(phase+"-timing.json");timing(t);assert t["phase"]==phase
    launch=read(phase+"-process-launch.json");clean=read(phase+"-process-cleanup.json")
    assert launch["phase"]==clean["phase"]==phase and clean["status"]=="PASS"
    assert type(clean["actualExecExitCode"])is int and clean["actualExecExitCode"]==0
    assert clean["launchSha256"]==sha(HERE/(phase+"-process-launch.json"))
    assert len(launch["drivers"])==len(clean["checks"])==2
    assert [{k:c[k] for k in ("pid","startTicks","executable")} for c in clean["checks"]]==launch["drivers"]
    assert all(c["recordedBirthExited"]is True for c in clean["checks"])
    for side in ("before","after"):assert text(phase+"-input-audit-"+side+".txt").strip()=="PASS exact final clearance inputs sources=1481 owners=8 gates=8"
    log=text(phase+".txt")
    if phase!="build":
        suites=read("final-suites.json")[phase]
        assert log.count("PASS: focused current contracts; "+str(len(suites))+" Java suites. Full matrix and independent oracles NOT RUN.")==1
        assert "CURRENT_CONTRACT_FAILURE:" not in log
        if phase in ("fixtures","contracts"):
            definitions=dict(re.findall(r"Name = '([^']+)'; Marker = '([^']+)'",(ROOT/"scripts/verify-current-contracts.ps1").read_text(encoding="utf-8-sig")))
            receipt=text(phase+"-receipt.txt")
            for suite in suites:assert re.search(r"(?m)^PASS: "+re.escape(definitions[suite]),receipt),suite
    return t,log
def census():
    helpers=runpy.run_path(str(HERE.parent/"lead-silk-local-trial-r1/assess.py"))
    old=HERE.parent/"lead-silk-local-trial-r1/baseline-receipt.txt";new=HERE/"census-receipt.txt"
    rows=helpers["cases"](new,"PCB_NORMAL_PACKING_CASE ");before=helpers["cases"](old,"PCB_NORMAL_PACKING_CASE ")
    refs=helpers["cases"](new,"PCB_NORMAL_REFERENCE_CASE ");oldrefs=helpers["cases"](old,"PCB_NORMAL_REFERENCE_CASE ")
    leads=helpers["cases"](new,"PCB_LEAD_SILK_CASE ");oldleads=helpers["cases"](old,"PCB_LEAD_SILK_CASE ")
    titles=helpers["cases"](new,"PCB_NORMAL_SILK_CASE ");oldtitles=helpers["cases"](old,"PCB_NORMAL_SILK_CASE ")
    changed=[];oldpairs=0
    for key,row in rows.items():
        assert row["status"]=="ACCEPTED" and row["graphBefore"]==row["graphAfter"]
        assert row["layoutAlgorithmVersion"]==18 and before[key]["layoutAlgorithmVersion"]==17
        excluded=helpers["TIMING"]|{"layoutAlgorithmVersion","geometrySha256"}
        assert set(row)==set(before[key])
        assert {k:v for k,v in row.items() if k not in excluded}=={k:v for k,v in before[key].items() if k not in excluded},("physical/graph/packing/work",key)
        a,b=oldrefs[key],refs[key]
        assert a["components"]==b["components"] and a["physicalBoardSha256"]==b["physicalBoardSha256"]
        assert a["labelCount"]==b["labelCount"]==len(a["labels"])==len(b["labels"])
        assert oldleads[key]["geometryApartFromLegendPositionAndEpochSha256"]==leads[key]["geometryApartFromLegendPositionAndEpochSha256"]
        assert oldleads[key]["components"]==leads[key]["components"]
        for first,second in zip(a["labels"],b["labels"]):
            assert {k:v for k,v in first.items() if k!="bounds"}=={k:v for k,v in second.items() if k!="bounds"}
            assert first["bounds"][2:]==second["bounds"][2:]
            if not first["id"].startswith("component:"):assert first==second,("nonreference moved",key,first["id"])
        assert {k:v for k,v in titles[key].items() if k!="geometryApartFromTitleSha256"}=={k:v for k,v in oldtitles[key].items() if k!="geometryApartFromTitleSha256"}
        oldpairs+=helpers["observation"](oldleads[key],oldrefs[key])
        assert helpers["observation"](leads[key],refs[key])==0,("connected lead overlap",key)
        if a["labels"]!=b["labels"]:changed.append(key)
    assert oldpairs==15 and changed
    (HERE/"census-comparison.json").write_text(json.dumps({"status":"PASS","cases":44,"baselineLeadInkPairs":oldpairs,"candidateLeadInkPairs":0,"changedReferenceCases":changed,"boundary":"Only component-reference XY/epoch and dependent full-ink hashes allowed; all physical/graph/metrics/work/nonreference XY exact"},indent=2)+"\n")

def matrix():
    receipt=text("matrix-receipt.txt")
    assert "PASS: Quick Play physical matrix contracts pairs=10 assertions=" in receipt
    assert "PASS: Quick Play physical matrix contracts pairs=1 assertions=" in receipt
    assert "PASS: physical matrix partition coverage families=11 accepted=220 exactReplays=220 smallChildBudgetMs=180000 mediumChildBudgetMs=600000" in receipt
    rows=[v.split("|") for v in receipt.splitlines() if v.startswith("PHYSICAL_MATRIX|")]
    assert len(rows)==11
    for f,row in zip(FAMILIES,rows):
        assert len(row)==13 and row[1]==f and row[2]==("MEDIUM" if f in ("COMPOSED_CONTROLLED_INDICATOR","RB30_CONTROL") else "EASY")
        assert row[3]==row[4]=="20" and 20<=int(row[11])<=48
def canaries():
    timing({"status":"PASS","exitCode":0,"elapsedSeconds":1.25,"terminalCleanupIncluded":True,"failure":None})
    rejected=0
    for k,v in (("status","FAIL"),("exitCode",False),("exitCode",0.0),("exitCode",2),("elapsedSeconds",False)):
        frame={"status":"PASS","exitCode":0,"elapsedSeconds":1.25,"terminalCleanupIncluded":True,"failure":None};frame[k]=v
        try:timing(frame)
        except AssertionError:rejected+=1
    assert rejected==5
    print("PASS final assessment timing canaries positive1 rejected5; pure framing only")
def main():
    if sys.argv[1:]==["--canaries"]:canaries();return
    phase,=sys.argv[1:];assert phase in PHASES
    t,log=common(phase)
    if phase=="census":census()
    if phase=="matrix":matrix()
    if phase=="build":
        for marker in ("Compiling module com.lushprojects.circuitjs1.circuitjs1","Compiling 5 permutations","Compile of permutations succeeded","Compilation succeeded --","Link succeeded","Linking succeeded --"):assert marker in log,marker
        assert "BUILD_FAILURE" not in log and "BUILD_INFRASTRUCTURE" not in log
    (HERE/(phase+"-assessment.json")).write_text(json.dumps({"status":"PASS","phase":phase,"elapsedSeconds":t["elapsedSeconds"],"limits":"Actual named phase only; player/publication acceptance separate"},indent=2)+"\n",encoding="utf-8")
    print("PASS final clearance assessment "+phase)
if __name__=="__main__":main()
