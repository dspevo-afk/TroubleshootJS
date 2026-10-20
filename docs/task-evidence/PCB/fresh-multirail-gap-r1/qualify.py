"""Read exact native phase completion and unchanged-input/cleanup receipts."""
import hashlib,json,math,re,runpy
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
def read(n):return json.loads((HERE/n).read_text(encoding="utf-8-sig"))
def text(n):return (HERE/n).read_text(encoding="utf-8-sig")
def sha(n):return hashlib.sha256((HERE/n).read_bytes()).hexdigest()
assert runpy.run_path(str(HERE/"inputs.py"))["record"]()==read("inputs.json")
timing=read("native-route-timing.json")
assert timing["phase"]=="native-route" and timing["status"]=="PASS" and type(timing["exitCode"]) is int and timing["exitCode"]==0
assert type(timing["elapsedSeconds"]) in (int,float) and math.isfinite(timing["elapsedSeconds"]) and timing["elapsedSeconds"]>0 and timing["failure"] is None
assert timing["terminalCleanupIncluded"] is True
launch=read("native-route-process-launch.json");cleanup=read("native-route-process-cleanup.json")
assert cleanup["status"]=="PASS" and type(cleanup["actualExecExitCode"]) is int and cleanup["actualExecExitCode"]==0
assert cleanup["launchSha256"]==sha("native-route-process-launch.json")
assert len(launch["drivers"])==len(cleanup["checks"])==2
assert [{k:x[k] for k in ("pid","startTicks","executable")} for x in cleanup["checks"]]==launch["drivers"]
assert all(x["recordedBirthExited"] is True for x in cleanup["checks"])
for side in ("before","after"):
    assert text("native-route-input-audit-"+side+".txt").strip()=="PASS exact fresh multi-rail native inputs1482 machinery24 gates7 seed=-5776064669694490064"
log=text("native-route.txt");receipt=text("native-route-receipt.txt")
assert "CURRENT_CONTRACT_FAILURE:" not in log and "CURRENT_CONTRACT_CLEANUP:" not in log
assert log.count("PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.")==1
assert log.count("Q30_CORPUS_CENSUS attempted=1 accepted=1 rejected=0 failed=0 childBudgetMs=60000")==1
prefix="Q30_CORPUS_JSON:"
rows=[json.loads(line[len(prefix):]) for line in receipt.splitlines() if line.startswith(prefix)]
assert len(rows)==1;row=rows[0]
assert row["seed"]==read("request.json")["seed"] and row["schema"]==1 and row["planEpoch"]==4
assert row["outcome"]=="ACCEPTED" and row["routeOutcome"]=="SUCCESS" and row["cleanup"] is True and row["contractValidated"] is True and row["validationFailure"] is None
assert row["coldRouteCanonical"]==row["warmRouteCanonical"] and row["coldRouteOutcomes"]==row["warmRouteOutcomes"]
assert row["proofPhase"]=="NOT_RUN_BY_NORMAL_CORPUS_CONTRACT"
packing=re.search(r"packingProbe=(normal-packing@2;attempts=([01]);orderings=(\d+);expansions=(\d+);millis=(\d+);route=([^;]+);disposition=(NO_CHANGE|NO_HEADROOM|KEPT|SELECTED|ROUTE_REJECTED);baseline=([0-9a-f]{64});selected=([0-9a-f]{64}))",row["coldRouteCanonical"])
assert packing,"Missing exact existing packing receipt"
assert bool(packing[8]!=packing[9])==(packing[7]=="SELECTED")
result={"status":"PASS_EXACT_NATIVE_ROUTE_DIAGNOSIS","baseCommit":read("inputs.json")["baseCommit"],"seed":row["seed"],"elapsedSeconds":timing["elapsedSeconds"],"inputSealSha256":sha("inputs.json"),"normalAdmissionOutcome":row["normalAdmissionOutcome"],"packingCanonical":packing[1],"packingDisposition":packing[7],"coldWarmRouteExact":True,"row":row,"limits":"Existing exact one-seed cold/warm route/normal physical admission/structural contract only; proofPhase explicitly NOT_RUN. No connector identity/bounds/empty-region conclusion or all-seed/new-size/performance claim yet"}
(HERE/"native-route-assessment.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
print("PASS exact native route seed="+row["seed"]+" packingDisposition="+packing[7])
