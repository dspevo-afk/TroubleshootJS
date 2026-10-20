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
    old=HERE.parent/"lead-silk-local-trial-r1/candidate-receipt.txt";new=HERE/"census-receipt.txt"
    for marker in ("PCB_NORMAL_PACKING_CASE ","PCB_NORMAL_REFERENCE_CASE ","PCB_NORMAL_SILK_CASE ","PCB_LEAD_SILK_CASE "):
        a=helpers["cases"](old,marker);b=helpers["cases"](new,marker);assert set(a)==set(b)
        for key in a:
            if marker=="PCB_NORMAL_PACKING_CASE ":
                assert {k:v for k,v in a[key].items() if k not in helpers["TIMING"]}=={k:v for k,v in b[key].items() if k not in helpers["TIMING"]},key
            else:assert a[key]==b[key],(marker,key)
    leads=helpers["cases"](new,"PCB_LEAD_SILK_CASE ");refs=helpers["cases"](new,"PCB_NORMAL_REFERENCE_CASE ")
    assert sum(helpers["observation"](row,refs[key]) for key,row in leads.items())==0
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
