"""Reject a lead-clear legend trial if any accepted physical board changes."""
import hashlib
import json
import runpy
import sys
from pathlib import Path
if not __debug__:
    raise RuntimeError("Lead-silk trial requires assertion-enabled Python")
HERE=Path(__file__).resolve().parent
FAMILIES=("LED_INDICATOR","DIODE_PROTECTED_INDICATOR","PARALLEL_DUAL_INDICATOR","RC_DELAY",
          "NPN_LOW_SIDE_SWITCH","NMOS_LOW_SIDE_SWITCH","RELAY_OUTPUT","SENSOR_CONTROL",
          "RB15_CONTROL","COMPOSED_CONTROLLED_INDICATOR","RB30_CONTROL")
ROOTS=("0","17","13","-9223372036854775808")
TIMING={"elapsedMillis","maxAdvanceMillis","generationRoutingMillis"}
def read(n):return json.loads((HERE/n).read_text(encoding="utf-8-sig"))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def text(n):return (HERE/n).read_text(encoding="utf-8-sig")
def cases(path,marker):
    out={}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if line.startswith(marker):
            row=json.loads(line[len(marker):]);key=(row["family"],row["root"])
            assert key not in out;out[key]=row
    assert set(out)=={(f,r) for f in FAMILIES for r in ROOTS}
    return out
def intersects(a,b):
    assert len(a)==len(b)==4 and all(type(n)is int for n in a+b)
    assert a[2]>0 and a[3]>0 and b[2]>0 and b[3]>0
    return a[0]<b[0]+b[2] and b[0]<a[0]+a[2] and a[1]<b[1]+b[3] and b[1]<a[1]+a[3]
def timing(t):assert t["status"]=="PASS" and type(t["exitCode"]) is int and t["exitCode"]==0
def success(phase):
    t=read(phase+"-timing.json");timing(t)
    launch=read(phase+"-process-launch.json");clean=read(phase+"-process-cleanup.json")
    assert launch["phase"]==clean["phase"]==phase and clean["status"]=="PASS"
    assert clean["launchSha256"]==sha(HERE/(phase+"-process-launch.json"))
    assert len(launch["drivers"])==len(clean["checks"])==2
    assert [{k:c[k] for k in ("pid","startTicks","executable")} for c in clean["checks"]]==launch["drivers"]
    assert all(c["recordedBirthExited"] is True for c in clean["checks"])
    for side in ("before","after"):
        assert text(phase+"-input-audit-"+side+".txt").strip()=="PASS lead-silk exact input audit phase="+phase+" sources=1481 gates=4"
    assert text(phase+".txt").count("PASS: focused current contracts; 8 Java suites. Full matrix and independent oracles NOT RUN.")==1
    return t
def observation(row,ref):
    assert set(row)=={"family","root","geometryApartFromLegendPositionAndEpochSha256","components","labels","overlaps"}
    assert row["labels"]==[{"id":v["id"],"bounds":v["bounds"]} for v in ref["labels"]]
    assert [v["id"] for v in row["components"]]==[v["id"] for v in ref["components"]]
    computed=[]
    for label in row["labels"]:
        for component in row["components"]:
            assert set(component)=={"id","leads"} and component["leads"]
            for i,lead in enumerate(component["leads"]):
                if intersects(label["bounds"],lead):computed.append({"label":label["id"],"component":component["id"],"terminal":i})
    assert row["overlaps"]==computed,"native collision export differs from independent rectangles"
    return len(computed)
def canaries():
    assert intersects([0,0,10,10],[9,9,3,3])
    assert not intersects([0,0,10,10],[10,0,3,3])
    assert not intersects([-10,-10,2,2],[0,0,2,2])
    rejected=0
    for t in ({"status":"FAIL","exitCode":0},{"status":"PASS","exitCode":2},
              {"status":"PASS","exitCode":False},{"status":"PASS","exitCode":0.0}):
        try:timing(t)
        except AssertionError:rejected+=1
    assert rejected==4
    (HERE/"gate-canaries.json").write_text(json.dumps({"status":"PASS","literalRectangles":3,"failedTimingFramesRejected":4,"limits":"Pure rectangle/timing framing only; not phase/native/OS proof"},indent=2)+"\n")
    print("PASS three literal rectangle/four timing-refusal canaries")
def main():
    if sys.argv[1:]==["--canaries"]:canaries();return
    phase=sys.argv[1];assert phase in ("baseline","candidate")
    inp=runpy.run_path(str(HERE/"inputs.py"));assert inp["record"]()==read(phase+"-inputs.json")
    t=success(phase);receipt=HERE/(phase+"-receipt.txt")
    rows=cases(receipt,"PCB_NORMAL_PACKING_CASE ");refs=cases(receipt,"PCB_NORMAL_REFERENCE_CASE ")
    silk=cases(receipt,"PCB_LEAD_SILK_CASE ");titles=cases(receipt,"PCB_NORMAL_SILK_CASE ")
    errors=[];total=0;changed=[]
    if phase=="baseline":prior=HERE.parent/"multirail-public-references-r1/candidate-receipt.txt"
    else:prior=HERE/"baseline-receipt.txt"
    before=cases(prior,"PCB_NORMAL_PACKING_CASE ");oldrefs=cases(prior,"PCB_NORMAL_REFERENCE_CASE ")
    oldtitles=cases(prior,"PCB_NORMAL_SILK_CASE ")
    oldsilk=cases(prior,"PCB_LEAD_SILK_CASE ") if phase=="candidate" else None
    for key,row in rows.items():
        assert row["status"]=="ACCEPTED" and row["graphBefore"]==row["graphAfter"]
        assert row["layoutAlgorithmVersion"]==(17 if phase=="baseline" else 18)
        total+=observation(silk[key],refs[key])
        excluded=TIMING|({"layoutAlgorithmVersion","geometrySha256"} if phase=="candidate" else set())
        if set(row)!=set(before[key]) or any(row[k]!=before[key][k] for k in row if k not in excluded):errors.append({"case":key,"reason":"case physical/admission/graph/metrics/work changed"})
        if phase=="baseline":
            assert refs[key]==oldrefs[key] and titles[key]==oldtitles[key]
        else:
            a,b=oldrefs[key],refs[key]
            if a["components"]!=b["components"] or a["physicalBoardSha256"]!=b["physicalBoardSha256"]:errors.append({"case":key,"reason":"physical/reference inventory changed"})
            if silk[key]["geometryApartFromLegendPositionAndEpochSha256"]!=oldsilk[key]["geometryApartFromLegendPositionAndEpochSha256"] or silk[key]["components"]!=oldsilk[key]["components"]:errors.append({"case":key,"reason":"normalized complete geometry or leads changed"})
            assert len(a["labels"])==len(b["labels"])
            for first,second in zip(a["labels"],b["labels"]):
                assert {k:v for k,v in first.items() if k!="bounds"}=={k:v for k,v in second.items() if k!="bounds"}
                assert first["bounds"][2:]==second["bounds"][2:]
            if a["labels"]!=b["labels"]:changed.append(key)
            if silk[key]["overlaps"]:errors.append({"case":key,"reason":"candidate lead/legend overlap remains"})
    if phase=="baseline":assert total>0,"predeclared collision cause absent"
    else:assert read("baseline-assessment.json")["status"]=="PASS" and total==0
    result={"status":"FAIL_REJECTED_TRIAL" if errors else "PASS","phase":phase,"cases":44,"leadLegendIntersectionPairs":total,"changedLegendCases":changed,"errors":errors,"elapsedSeconds":t["elapsedSeconds"],"limits":"Bounded diagnostic corpus only; not final production acceptance/current epoch migration/build/player/all-seed proof"}
    (HERE/(phase+"-assessment.json")).write_text(json.dumps(result,indent=2)+"\n")
    if errors:print("REJECT lead-silk trial physical differences="+str(len(errors)));sys.exit(2)
    print("PASS lead-silk trial "+phase+" cases44 collisionPairs="+str(total))
if __name__=="__main__":main()
