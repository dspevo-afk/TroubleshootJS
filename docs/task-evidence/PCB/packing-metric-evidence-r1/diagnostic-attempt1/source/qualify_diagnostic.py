"""Compare every existing export field and independently explain strict rejection."""
import copy,hashlib,json,math,re,runpy
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
ATTEMPT=HERE/"diagnostic-attempt1"
def read(n):return json.loads((ATTEMPT/n).read_text(encoding="utf-8-sig"))
def text(n):return (ATTEMPT/n).read_text(encoding="utf-8-sig")
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert runpy.run_path(str(HERE/"diagnostic_inputs.py"))["record"]()==read("inputs.json")
t=read("timing.json")
assert t["status"]=="PASS" and type(t["exitCode"]) is int and t["exitCode"]==0 and t["failure"] is None and t["terminalCleanupIncluded"] is True
assert type(t["elapsedSeconds"]) in (int,float) and math.isfinite(t["elapsedSeconds"]) and t["elapsedSeconds"]>0
launch=read("process-launch.json");cleanup=read("process-cleanup.json")
assert cleanup["status"]=="PASS" and type(cleanup["actualExecExitCode"]) is int and cleanup["actualExecExitCode"]==0 and cleanup["launchSha256"]==sha(ATTEMPT/"process-launch.json")
assert len(cleanup["checks"])==len(launch["drivers"])==2 and all(x["recordedBirthExited"] is True for x in cleanup["checks"])
assert [{k:x[k] for k in ("pid","startTicks","executable")} for x in cleanup["checks"]]==launch["drivers"]
for side in ("before","after"):assert text("input-audit-"+side+".txt").strip()=="PASS frozen packing metric diagnostic source1482 machinery24 helpers10 exact-seed"
negative=read("invalid-seed-canary.json")
assert negative["status"]=="PASS_REJECTED_INVALID_SEED" and type(negative["actualAdapterExitCode"]) is int and negative["actualAdapterExitCode"]==1 and negative["privateLogBytes"]>0
log=text("native.txt");receipt=text("receipt.txt")
assert "CURRENT_CONTRACT_FAILURE:" not in log and "CURRENT_CONTRACT_CLEANUP:" not in log
assert log.count("PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.")==1
prefix="PCB_PACKING_METRIC_EVIDENCE "
rows=[json.loads(x[len(prefix):]) for x in receipt.splitlines() if x.startswith(prefix)]
assert len(rows)==1;row=rows[0]
assert row["schema"]=="packing-metric-evidence@1" and row["seed"]=="-5776064669694490064"
marker=re.findall(r"(?m)^PASS: fresh RB30 gap diagnostic seed=-5776064669694490064 assertions=(\d+)\r?$",receipt)
assert len(marker)==1 and int(marker[0])==376
previous=json.loads((HERE.parent/"fresh-multirail-gap-r1/export-attempt1/assessment-r2.json").read_text(encoding="utf-8-sig"))
old=previous["fullExport"]
assert previous["status"]=="PASS_SELECTED_GEOMETRY_EXPORT_REVISED_SCOPE_R2"
probe=row["route"]["packingProbe"];evidence=probe["metricEvidence"]
assert set(evidence)=={"state","unavailableReason","baseline","candidate"}
assert evidence["state"]=="COMPARED" and evidence["unavailableReason"] is None
metricKeys={"area","copper","vias","protectedVoid"}
for kind in ("baseline","candidate"):
    assert set(evidence[kind])==metricKeys and all(type(v) is int and v>=0 for v in evidence[kind].values())
literal={"area":7520448,"copper":29840,"vias":34,"protectedVoid":2270400}
assert evidence["baseline"]==literal=={k:row["packingMetrics"][k] for k in metricKeys}
assert row["packingMetrics"]["available"] is True
assert probe["disposition"]=="KEPT"

def normalize_only_clock(value):
    result,count=re.subn(r"(normal-packing@2;attempts=[01];orderings=\d+;expansions=\d+;millis=)([0-9]+)(;route=)",r"\1[wall-clock-observation]\3",value)
    assert count==1
    return result

def existing_projection(value,has_evidence):
    result=copy.deepcopy(value)
    assert result.pop("schema")==("packing-metric-evidence@1" if has_evidence else "fresh-rb30-gap@1")
    p=result["route"]["packingProbe"]
    if has_evidence:assert p.pop("metricEvidence")==evidence
    assert type(p["millis"]) is int and p["millis"]>=0
    observed=p.pop("millis")
    for value in (p["canonical"],result["route"]["routeCanonical"]):
        assert ";millis="+str(observed)+";route=" in value
    p["canonical"]=normalize_only_clock(p["canonical"])
    result["route"]["routeCanonical"]=normalize_only_clock(result["route"]["routeCanonical"])
    return result

assert existing_projection(row,True)==existing_projection(old,False),"Existing selected export changed outside explicit diagnostic/clock scope"
selected="3c92cfef42211be4fdc2a81b22889d203bbdd3d7b29fdbbb4bc1961682856df3"
assert probe["selectedGeometry"]==probe["baselineGeometry"]==row["geometryIdentity"]["geometryFingerprintBefore"]==row["geometryIdentity"]["geometryFingerprintAfter"]==selected
components=row["layout"]["components"]
assert len(components)==38 and sum(len(x["terminals"]) for x in components)==92 and len(row["nets"])==26 and len(row["powerInputs"])==4
before,after=evidence["baseline"],evidence["candidate"]
predicates={"areaStrictlySmaller":after["area"]<before["area"],"copperNonincreasing":after["copper"]<=before["copper"],"viasNonincreasing":after["vias"]<=before["vias"],"protectedVoidNonincreasing":after["protectedVoid"]<=before["protectedVoid"]}
failed=[k for k,v in predicates.items() if not v]
assert failed,"KEPT contradicts independent strict metric predicates"
result={"status":"PASS_ACTUAL_PACKING_METRIC_OBSERVATION","seed":row["seed"],"elapsedSeconds":t["elapsedSeconds"],"assertions":376,"inputSealSha256":sha(ATTEMPT/"inputs.json"),"selectedGeometrySha256":selected,"metricEvidence":evidence,"strictPredicates":predicates,"failedPredicates":failed,"packingMillis":{"prior":old["route"]["packingProbe"]["millis"],"current":probe["millis"]},"allExistingExportFieldsExactExceptOnlyNamespacedPackingClock":True,"fullExport":row,"limits":"Actual already-computed owner metric tuples; task-only pinned verifier adapter with real invalid/valid host canaries. Prior complete selected geometry/declarations/validation/work comparison permits only explicit schema/evidence and namespaced clock. No rejected-candidate reconstruction, changed selection/budget/oracle, new-size/manufacturing/performance/all-seed or live electrical proof"}
(ATTEMPT/"assessment.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
print("PASS actual packing metric observation; failed predicates="+",".join(failed))
