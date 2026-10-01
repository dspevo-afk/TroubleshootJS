#!/usr/bin/env python3
"""Strict, stdlib-only reader for isolated Q30 cold-screen receipts."""
import argparse, hashlib, json, math, pathlib, sys

PLAN_SHA256 = "c0d376dd0f6f4df09ac85da64cf4c99a987d8a78c1472207043eee68dc218f84"
SOURCE = "41f02f1b9f6c32cb8cf4ffbf9e8bd2bb3ea2476b"
SEEDS = [("7",33),("64",35),("13",37)]
LIMITS = [90000,640,5000,4]
POLICY_CANON = "NORMAL_MEDIUM_EXECUTION@2;physical=MEDIUM_BOARD_NORMAL@1;maxJobMillis=90000;maxJobSteps=640;maxUnitMillis=5000;candidateBudget=shared;clock=foreground"
class Invalid(ValueError): pass

def pairs(pairs):
    d={}
    for k,v in pairs:
        if k in d: raise Invalid("duplicate JSON key: "+k)
        d[k]=v
    return d
def bad_constant(x): raise Invalid("non-finite JSON constant: "+x)
def load(path):
    raw=pathlib.Path(path).read_bytes()
    try: obj=json.loads(raw.decode("utf-8"),object_pairs_hook=pairs,parse_constant=bad_constant)
    except (UnicodeError,json.JSONDecodeError) as e: raise Invalid(f"invalid UTF-8 JSON {path}: {e}") from e
    if not isinstance(obj,dict): raise Invalid(f"expected JSON object: {path}")
    return obj,raw
def need(ok,msg):
    if not ok: raise Invalid(msg)
def integer(o,k,lo=0,hi=None):
    x=o.get(k); need(type(x) is int and x>=lo and (hi is None or x<=hi),f"{k} integer range"); return x
def num(o,k,lo=0):
    x=o.get(k); need(type(x) in (int,float) and math.isfinite(x) and x>=lo,f"{k} finite range"); return float(x)
def text(o,k):
    x=o.get(k); need(isinstance(x,str) and bool(x),f"{k} non-empty string"); return x
def flag(o,k,v=True): need(type(o.get(k)) is bool and o[k] is v,f"{k} must be {v}")
def frozen(p,raw):
    need(hashlib.sha256(raw).hexdigest()==PLAN_SHA256,"plan SHA256 mismatch")
    need((p.get("schema"),p.get("sourceCommit"),p.get("planVersion"))==(1,SOURCE,3),"plan epoch mismatch")
    need((p.get("maxFreshColdJobs"),p.get("freshColdJobsExecuted"))==(3,0),"cold-job plan budget mismatch")
    need((p.get("requestPrivate"),p.get("requestCandidateSearch"),p.get("requestQuickPlayFlag"),p.get("executionPolicy"))==(False,True,False,"NORMAL_MEDIUM_EXECUTION@2"),"plan request policy mismatch")
    lim=p.get("limits",{}); need([lim.get(k) for k in ("maxJobMillis","maxWorkUnits","maxActiveOperationMillis","maxCandidatesPerSharedJob")]==LIMITS,"plan limits mismatch")
    cs=p.get("cases"); need(isinstance(cs,list) and len(cs)==3,"expected exactly three frozen cases")
    out={}
    for c,(seed,count) in zip(cs,SEEDS):
        need(isinstance(c,dict) and (c.get("seed"),c.get("declaredPackageCount"),c.get("recipeComponentCount"))==(seed,count,count),"frozen seed/package mapping mismatch")
        need(f"packages={count}" in c.get("planCanonical",""),"plan canonical package mismatch")
        ps=c.get("plannedCandidates"); need(isinstance(ps,list) and len(ps)==4,"expected four candidates per case")
        for i,x in enumerate(ps):
            need(isinstance(x,dict) and x.get("ordinal")==i and type(x.get("declaredPackageCount")) is int and x["declaredPackageCount"]>0 and isinstance(x.get("seed"),str) and isinstance(x.get("manifest"),str) and x["manifest"].startswith(f"candidate={i:02d};"),"frozen candidate manifest/order mismatch")
        out[seed]=c
    return out

def proof_ok(proof):
    need(isinstance(proof,dict),"PASS lacks proof"); flag(proof,"warmReuse",False); flag(proof,"explicitCompletion")
    for k in ("context","program","partition"): text(proof,k)
    rows=proof.get("evidence"); need(isinstance(rows,list) and len(rows)==5,"proof must have five hypotheses")
    hs=set()
    for r in rows:
        need(isinstance(r,dict),"proof row malformed"); h=text(r,"hypothesis"); need(h not in hs,"duplicate hypothesis"); hs.add(h)
        for k in ("repairReachable","customerRetestPassed","stateIsolated"): flag(r,k)
        text(r,"deterministicResult"); ss=r.get("samples"); need(isinstance(ss,list) and len(ss)==37,"each hypothesis must have 37 samples")
        ids=set()
        for s in ss:
            need(isinstance(s,dict),"sample malformed"); sid=text(s,"id"); need(sid not in ids,"duplicate sample id"); ids.add(sid)
            need(s.get("outcome") in ("NUMERIC","OVER_RANGE"),"unknown sample outcome")
            if s["outcome"]=="NUMERIC":
                need(type(s.get("value")) in (int,float) and math.isfinite(s["value"]) and type(s.get("tolerance")) in (int,float) and math.isfinite(s["tolerance"]) and s["tolerance"]>=0,"invalid numeric sample")
            else: need("value" not in s and "tolerance" not in s,"over-range sample has numeric payload")

def rows_ok(r,c,passed):
    aa,cc=r.get("attempts"),r.get("candidates")
    need(isinstance(aa,list) and isinstance(cc,list) and len(aa)==len(cc)<=4,"attempt/candidate rows incomplete")
    ps=c["plannedCandidates"]
    for i,(a,x) in enumerate(zip(aa,cc)):
        need(isinstance(a,dict) and isinstance(x,dict) and a.get("ordinal")==i and x.get("ordinal")==i,"attempt order mismatch")
        need(a.get("manifest")==ps[i]["manifest"] and x.get("seed")==ps[i]["seed"],"attempt identity differs from frozen plan")
        need(a.get("stage") in ("RESOLVE","HEALTHY","PHYSICAL","HYPOTHESES","SYMPTOM","PUBLISH"),"unknown stage")
        need(a.get("outcome") in ("PASS","EXPECTED_REJECTION","WORK_EXHAUSTED","TIMEOUT","INFRASTRUCTURE_FAILURE","PROGRAMMING_FAILURE"),"unknown attempt outcome")
        integer(a,"workUnits"); integer(a,"proofUnits"); need(a["proofUnits"]<=a["workUnits"],"proof work exceeds attempt work")
        need(("failureType" in a and "failureMessage" in a) and all(v is None or isinstance(v,str) for v in (a["failureType"],a["failureMessage"])),"attempt failure receipt malformed")
        n=x.get("actualPackages"); need(type(n) is int and n in (-1,ps[i]["declaredPackageCount"]),"actual package count mismatch")
        for k in ("physical","diagnostic","published","aborted"): need(type(x.get(k)) is bool,"candidate flag malformed: "+k)
        if n==-1: need(not x["physical"] and not x["diagnostic"] and not x["published"] and x.get("difficulty") is None,"unconstructed candidate claims proof")
        if x["diagnostic"]: need(isinstance(x.get("difficulty"),str) and x["difficulty"].startswith(("difficulty/2;profile=EASY;","difficulty/2;profile=MEDIUM;")),"candidate difficulty evidence missing")
    if not passed:
        need(not any(x["published"] for x in cc) and all(x["aborted"] for x in cc),"nonpass publication/cleanup flags inconsistent")
        if aa: need(all(a["outcome"]=="EXPECTED_REJECTION" for a in aa[:-1]),"nonpass attempt history order mismatch")
        return
    need(bool(aa),"PASS without candidate attempt"); pubs=[i for i,x in enumerate(cc) if x["published"]]
    need(len(pubs)==1 and pubs[0]==len(cc)-1,"PASS owner is not the unique last candidate")
    i=pubs[0]; x=cc[i]
    need(x["actualPackages"]==ps[i]["declaredPackageCount"] and x["physical"] and x["diagnostic"] and not x["aborted"] and "MEDIUM" in x.get("difficulty",""),"published owner evidence incomplete")
    need(aa[i]["outcome"]=="PASS" and all(a["outcome"]=="EXPECTED_REJECTION" for a in aa[:i]),"candidate rejection order mismatch")
    need(all(not x["published"] and x["aborted"] for x in cc[:i]),"rejected candidate cleanup flags mismatch")
    need(r.get("generationReceiptManifest")==aa[i]["manifest"],"publication receipt manifest mismatch")

def app_ok(r,raw,plans):
    sha=hashlib.sha256(raw).hexdigest()
    if r.get("applicationOutcome")=="PROGRAMMING_FAILURE": return {"status":"FAIL_PROGRAMMING","sha256":sha,"errors":[r.get("failure") or "application programming failure"]}
    if r.get("classification")=="INFRASTRUCTURE_FAILURE" or r.get("applicationOutcome")=="INFRASTRUCTURE_FAILURE": return {"status":"FAIL_INFRASTRUCTURE","sha256":sha,"errors":[r.get("harnessFailure") or r.get("failure") or "infrastructure failure"]}
    need((r.get("schema"),r.get("sourceCommit"),r.get("mode"),r.get("planVersion"))==(1,SOURCE,"cold",3),"app report schema/source/mode/epoch mismatch")
    seed=r.get("rootSeed"); need(seed in plans,"seed not frozen"); c=plans[seed]
    need(r.get("planCanonical")==c["planCanonical"] and r.get("plannedPackages")==c["declaredPackageCount"],"plan canonical/package mismatch")
    expected_request=c["plannedCandidates"][0]["manifest"].split(";",1)[1].replace(";search=false;",";search=true;")
    need(r.get("requestCanonical")==expected_request,"normal request canonical differs from frozen search")
    for k,v in (("requestPrivate",False),("candidateSearch",True),("quickPlay",False),("explicitCompletion",True),("initialCachesEmpty",True),("foregroundClock",True),("privateCacheUntouched",True),("cleanupComplete",True)): flag(r,k,v)
    need([r.get(k) for k in ("maxJobMillis","maxWorkUnits","maxActiveOperationMillis","candidateLimit","executionPolicy")] == [90000,640,5000,4,POLICY_CANON],"normal request limits/policy mismatch")
    need(r.get("cache")=="fresh ordinary instance-owned; reuse enabled" and r.get("clock")=="normal foreground; debug=false" and "GenerationCoordinator.start" in r.get("scheduler",""),"cold cache/scheduler/clock mismatch")
    need(r.get("plannedManifests")==[x["manifest"] for x in c["plannedCandidates"]],"planned manifests mismatch")
    integer(r,"hiddenEvents"); need(r["hiddenEvents"]==0 and integer(r,"ordinaryCacheHits")==0,"hidden event or warm cache hit")
    cls=r.get("classification"); out=r.get("applicationOutcome")
    need(cls in ("PASS","TIMEOUT","ADMISSION_REJECTED"),"unexpected terminal classification")
    need((cls=="PASS" and out=="PASS") or (cls=="TIMEOUT" and out=="TIMEOUT") or (cls=="ADMISSION_REJECTED" and out in ("EXPECTED_REJECTION","WORK_EXHAUSTED")),"classification/outcome mismatch")
    for k in ("generationElapsedMs","startToCallbackWallMs","maxActiveOperationMs","maxCoordinatorAdvanceMs","cleanupElapsedMs"): num(r,k)
    for k in ("routingElapsedMsNested","proofElapsedMsNested"):
        if r.get(k) is None: need(cls!="PASS",f"{k} missing on PASS")
        else: num(r,k)
    num(r,"headroomMs",-1e300); text(r,"terminalStage"); work=integer(r,"workUnits"); integer(r,"yields"); integer(r,"ordinaryCacheMisses"); integer(r,"ordinaryCacheSizeBeforeCleanup")
    stages=r.get("stages"); need(isinstance(stages,list) and bool(stages),"stage timing/work missing")
    need([s.get("stage") for s in stages]==["RESOLVE","HEALTHY","PHYSICAL","HYPOTHESES","SYMPTOM","PUBLISH"],"stage order/set mismatch")
    for s in stages: need(isinstance(s,dict),"stage malformed"); num(s,"elapsedMs"); integer(s,"workUnits")
    need(sum(s["workUnits"] for s in stages)==work,"stage work does not reconcile to total work")
    integer(r,"startScopeChecks"); need(r["startScopeChecks"]>=1,"normal start not scope checked")
    passed=cls=="PASS"; rows_ok(r,c,passed)
    need(sum(a["workUnits"] for a in r["attempts"])==work and work<=640,"attempt work does not reconcile to shared budget")
    need(abs(r["headroomMs"]-(90000-r["generationElapsedMs"]))<=1,"headroom does not match elapsed time")
    if passed:
        need(num(r,"generationElapsedMs")<=90000 and work<=640 and num(r,"maxActiveOperationMs")<=5000 and abs(num(r,"headroomMs")-(90000-num(r,"generationElapsedMs")))<=1,"PASS exceeds budget or headroom mismatch")
        need(r["ordinaryCacheMisses"]>0,"PASS lacks cold ordinary-cache lookup")
        need(integer(r,"publicationScopeChecks")>=1 and "MEDIUM" in r.get("difficultyAssessment",""),"PASS lacks publication/difficulty evidence")
        proof_ok(r.get("proof"))
    return {"status":"APP_PASS" if passed else "COMPLETED_NONPASS","classification":cls,"applicationOutcome":out,"rootSeed":seed,"sha256":sha,"metrics":{"elapsedMs":r.get("generationElapsedMs"),"workUnits":r.get("workUnits"),"maxActiveOperationMs":r.get("maxActiveOperationMs"),"maxCoordinatorAdvanceMs":r.get("maxCoordinatorAdvanceMs"),"headroomMs":r.get("headroomMs")},"errors":[]}

def host_ok(h,app,appraw):
    digest=hashlib.sha256(appraw).hexdigest()
    if h.get("outcome")!="PASS": return {"status":"HOST_TIMEOUT" if any(isinstance(c,dict) and c.get("outcome")=="TIMEOUT" for c in h.get("cases",[])) else "HOST_FAIL","errors":["host transport outcome is not PASS"]}
    errors=[]; audit=h.get("inputAudit",{}); clean=h.get("cleanup",{})
    if h.get("errors")!=[]: errors.append("host global errors are present")
    if h.get("caseCount")!=len(h.get("cases",[])): errors.append("host case count mismatch")
    if audit.get("status")!="PASS" or any(not audit.get(a) or audit.get(a)!=audit.get(b) for a,b in (("beforeSha256","afterSha256"),("sourceSha256Before","sourceSha256After"),("webSha256Before","webSha256After"),("runnerBeforeSha256","runnerAfterSha256"))): errors.append("host input audit failed")
    if clean.get("status")!="PASS" or clean.get("serverStopped") is not True or clean.get("ownedSurvivors")!=[] or clean.get("errors")!=[]: errors.append("host cleanup failed")
    found=False
    for c in h.get("cases",[]):
        if not isinstance(c,dict) or c.get("reportSha256")!=digest: continue
        u=c.get("url","")
        if (c.get("outcome")=="PASS" and c.get("terminalReached") is True and c.get("stateMatch") is True and c.get("reportObserved") is True and c.get("reportJsonValid") is True and c.get("reportMatch") is True and c.get("reportLength")==len(appraw.decode("utf-8")) and "tsjNormalMode=cold" in u and f"tsjNormalSeed={app.get('rootSeed')}" in u and all(c.get(k)==[] for k in ("pageErrors","httpErrors","consoleErrors","attributeReadErrors","listenerCleanupErrors","compiledResourceErrors"))): found=True
    if not found: errors.append("no clean host case matches app report hash, length, cold mode and seed")
    return {"status":"HOST_PASS" if not errors else "HOST_FAIL","errors":errors}

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__); p.add_argument("--report",required=True); p.add_argument("--plan",required=True); p.add_argument("--host-record"); p.add_argument("--output"); a=p.parse_args(argv)
    try:
        plan,pr=load(a.plan); plans=frozen(plan,pr); report,rr=load(a.report); app=app_ok(report,rr,plans)
        host=None
        if a.host_record: h,_=load(a.host_record); host=host_ok(h,report,rr)
        status=app["status"]
        if status=="APP_PASS" and host and host["status"]!="HOST_PASS": status="APP_PASS_HOST_FAIL"
        result={"status":status,"app":app,"host":host,"synthetic":False}
        code=0 if status=="APP_PASS" else (1 if status in ("COMPLETED_NONPASS","APP_PASS_HOST_FAIL","HOST_FAIL","HOST_TIMEOUT") else 2)
    except (OSError,Invalid,KeyError,TypeError) as e:
        result={"status":"FAIL_INVALID","app":{"status":"FAIL_INVALID","errors":[str(e)]},"host":None,"synthetic":False}; code=2
    data=json.dumps(result,sort_keys=True,indent=2,allow_nan=False)+"\n"
    if a.output: pathlib.Path(a.output).write_text(data,encoding="utf-8")
    print(data,end=""); return code
if __name__=="__main__": sys.exit(main())
