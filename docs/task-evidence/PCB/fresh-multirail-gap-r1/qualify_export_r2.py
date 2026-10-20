"""Independently cross-check selected geometry against the maintained native row."""
import hashlib,json,math,re,runpy
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
ATTEMPT=HERE/"export-attempt1"
def read(n):return json.loads((ATTEMPT/n).read_text(encoding="utf-8-sig"))
def text(n):return (ATTEMPT/n).read_text(encoding="utf-8-sig")
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
r2seal=json.loads((HERE/"export-review-r2-inputs.json").read_text())
assert all(sha(HERE/n)==h for n,h in r2seal["files"].items())
assert all(sha(HERE.parents[3]/n)==h for n,h in json.loads((HERE/"export-clock-source-evidence.json").read_text())["files"].items())
assert runpy.run_path(str(HERE/"export_inputs.py"))["record"]()==read("inputs.json")
timing=read("timing.json")
assert timing["status"]=="PASS" and type(timing["exitCode"]) is int and timing["exitCode"]==0 and timing["failure"] is None and timing["terminalCleanupIncluded"] is True
assert type(timing["elapsedSeconds"]) in (int,float) and math.isfinite(timing["elapsedSeconds"]) and timing["elapsedSeconds"]>0
launch=read("process-launch.json");cleanup=read("process-cleanup.json")
assert cleanup["status"]=="PASS" and type(cleanup["actualExecExitCode"]) is int and cleanup["actualExecExitCode"]==0 and cleanup["launchSha256"]==sha(ATTEMPT/"process-launch.json")
assert len(cleanup["checks"])==len(launch["drivers"])==2 and all(x["recordedBirthExited"] is True for x in cleanup["checks"])
assert [{k:x[k] for k in ("pid","startTicks","executable")} for x in cleanup["checks"]]==launch["drivers"]
for side in ("before","after"):assert text("input-audit-"+side+".txt").strip()=="PASS frozen selected RB30 export source1482 machinery24 helpers12 exact-seed"
negative=read("invalid-seed-canary.json")
assert negative["status"]=="PASS_REJECTED_INVALID_SEED" and type(negative["actualAdapterExitCode"]) is int and negative["actualAdapterExitCode"]==1 and negative["privateLogBytes"]>0
log=text("native.txt");receipt=text("receipt.txt")
assert "CURRENT_CONTRACT_FAILURE:" not in log and "CURRENT_CONTRACT_CLEANUP:" not in log
assert log.count("PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.")==1
rows=[json.loads(x[len("PCB_FRESH_RB30_GAP "):]) for x in receipt.splitlines() if x.startswith("PCB_FRESH_RB30_GAP ")]
assert len(rows)==1;row=rows[0]
assert row["schema"]=="fresh-rb30-gap@1" and row["seed"]=="-5776064669694490064"
assert len(re.findall(r"(?m)^PASS: fresh RB30 gap diagnostic seed=-5776064669694490064 assertions=\d+\r?$",receipt))==1
native=json.loads((HERE/"native-route-assessment.json").read_text())
def omit_only_packing_wall_clock(value):
    pattern=r"(normal-packing@2;attempts=[01];orderings=\d+;expansions=\d+;millis=)([0-9]+)(;route=)"
    matches=list(re.finditer(pattern,value))
    assert len(matches)==1
    normalized,count=re.subn(pattern,r"\1[wall-clock-observation]\3",value)
    assert count==1
    return normalized,int(matches[0][2])
assert native["row"]["coldRouteCanonical"]==native["row"]["warmRouteCanonical"]
assert omit_only_packing_wall_clock(row["route"]["routeCanonical"])[0]==omit_only_packing_wall_clock(native["row"]["coldRouteCanonical"])[0]
assert row["route"]["selectedPlacementAttempt"]==5 and row["route"]["selectedRoutePolicy"]=="P07_FULLER_TWO_LAYER"
assert omit_only_packing_wall_clock(row["route"]["packingProbe"]["canonical"])[0]==omit_only_packing_wall_clock(native["packingCanonical"])[0]
geometry=row["geometryIdentity"]
expected=row["route"]["packingProbe"]["selectedGeometry"]
assert expected=="3c92cfef42211be4fdc2a81b22889d203bbdd3d7b29fdbbb4bc1961682856df3"
assert geometry["geometryFingerprintBefore"]==geometry["geometryFingerprintAfter"]==expected and geometry["geometryFingerprintEqual"] is True
assert geometry["logicalBoardFingerprintBefore"]==geometry["logicalBoardFingerprintAfter"] and geometry["logicalBoardFingerprintEqual"] is True
components=row["layout"]["components"]
assert len(components)==row["plan"]["packageCount"]==native["row"]["partCount"]==38
assert sorted(x["componentId"] for x in components)==geometry["componentIds"] and len(set(geometry["componentIds"]))==38
pads={}
for part in components:
    for i,terminal in enumerate(part["terminals"]):
        assert terminal["index"]==i and terminal["padId"] not in pads
        pads[terminal["padId"]]=(part["componentId"],terminal)
assert sorted(pads)==geometry["padIds"]
nets={n["netId"]:n for n in row["nets"]}
assert len(nets)==len(row["nets"]) and sorted(nets)==geometry["netIds"]
for net,n in nets.items():
    assert sorted(n["padIds"])==sorted(p for p,(_,t) in pads.items() if t["net"]==net)
assert all(t["net"] in nets for _,t in pads.values())
power=row["powerInputs"]
assert len(power)==4 and len({x["id"] for x in power})==4
for item in power:
    assert pads[item["positivePadId"]][1]["net"]==item["positiveNetId"]
    assert pads[item["returnPadId"]][1]["net"]==item["returnNetId"]
# Reconstruct the task-defined logical declaration snapshot in independent Python.
# This is explicitly not a CircuitJS live-solver fingerprint.
tokens=[]
def token(v):
    assert isinstance(v,str)
    tokens.append(str(len(v.encode("utf-16-le"))//2)+":"+v+"|")
token(row["boardId"])
for part in sorted(components,key=lambda x:x["componentId"]):
    for v in ("component",part["componentId"],part["type"],part["publicReference"],part["packageId"]):token(v)
    for t in part["terminals"]:token(t["terminalId"])
    for t in part["terminals"]:
        for v in ("pad",t["padId"],part["componentId"],t["terminalId"],t["net"]):token(v)
for net,n in sorted(nets.items()):
    for v in ("net",net,n["routingRole"]):token(v)
    for p in sorted(n["padIds"]):token(p)
for item in sorted(power,key=lambda x:x["id"]):
    for v in ("power",item["id"],item["positivePadId"],item["returnPadId"],item["positiveNetId"],item["returnNetId"]):token(v)
logical=hashlib.sha256("".join(tokens).encode("utf-8")).hexdigest()
assert logical==geometry["logicalBoardFingerprintBefore"]
assert {t["layer"] for t in row["layout"]["traces"]}=={"TOP","BOTTOM"}
assert all(t["net"] in nets and len(t["points"])>=2 for t in row["layout"]["traces"])
assert all(h["net"] in nets and h["kind"]=="VIA" for h in row["layout"]["vias"])
assert len(row["layout"]["vias"])==row["validation"]["viaCount"]>0
for key in ("geometry","routingGeometry","routeQuality","traceClearance","p07Rules","normalMediumAdmission","access"):assert row["validation"][key]=="PASS"
assert row["validation"]["bothCopperFaces"] is True and row["validation"]["accessReachableCells"]>0
outline=row["layout"]["outline"]
assert all(type(v) is int for v in outline) and outline[2]>0 and outline[3]>0
metrics=row["packingMetrics"]
assert metrics["available"] is True and metrics["area"]==outline[2]*outline[3]
assert metrics["vias"]==row["validation"]["viaCount"]
assert row["physicalConstraints"]["barriers"]==[]
assert {p["componentId"] for p in components if p["anchor"]=="RIGHT"}=={"JLOAD"}
outlier=next(p for p in components if p["componentId"]=="JLOAD")
assert len(outlier["terminals"])==2
result={"status":"PASS_SELECTED_GEOMETRY_EXPORT_REVISED_SCOPE_R2","comparisonScopeSha256":sha(HERE/"export-review-r2-scope.md"),"packingWallClockObservations":{"maintained":omit_only_packing_wall_clock(native["packingCanonical"])[1],"export":omit_only_packing_wall_clock(row["route"]["packingProbe"]["canonical"])[1]},"baseCommit":read("inputs.json")["baseCommit"],"seed":row["seed"],"elapsedSeconds":timing["elapsedSeconds"],"inputSealSha256":sha(ATTEMPT/"inputs.json"),"geometrySha256":expected,"independentlyReconstructedLogicalDeclarationSha256":logical,"componentCount":38,"padCount":len(pads),"netCount":len(nets),"powerInputs":4,"packingMetrics":metrics,"outlier":outlier,"fullExport":row,"limits":"Task-only pinned verifier adapter with actual invalid/valid host canaries; selected geometry/route compared to independent maintained cold/warm receipt except only namespaced raw wall-clock millis; original full-route comparison failure retained. Task-defined logical declaration snapshot is not live CircuitJS state. No rejected-candidate metric reason, removable-region, new-size, manufacturing, performance or all-seed proof"}
(ATTEMPT/"assessment-r2.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
print("PASS revised-scope R2 selected geometry/route except only wall-clock millis/independent inventory;original failed comparison retained")
