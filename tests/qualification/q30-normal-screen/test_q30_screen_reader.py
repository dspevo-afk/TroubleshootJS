"""Contract tests use in-memory synthetic values only; their timings are not evidence."""
import copy, hashlib, json, os, pathlib, unittest
import q30_screen_reader as reader

PLAN_PATH=pathlib.Path(os.environ.get("TSJ_Q30_PLAN",str(pathlib.Path(__file__).with_name("screen-plan.json"))))
def synthetic_pass_test_only(plan):
    """Schema fixture, never a production measurement or completion receipt."""
    c=next(x for x in plan["cases"] if x["seed"]=="7"); p=c["plannedCandidates"][0]
    return {"schema":1,"sourceCommit":reader.SOURCE,"mode":"cold","rootSeed":"7","planVersion":3,
      "planCanonical":c["planCanonical"],"plannedPackages":33,
      "requestCanonical":p["manifest"].split(";",1)[1].replace(";search=false;",";search=true;"),
      "requestPrivate":False,"candidateSearch":True,"quickPlay":False,"explicitCompletion":True,
      "maxJobMillis":90000,"maxWorkUnits":640,"maxActiveOperationMillis":5000,"candidateLimit":4,
      "executionPolicy":reader.POLICY_CANON,"cache":"fresh ordinary instance-owned; reuse enabled",
      "scheduler":"GenerationCoordinator.start(request, completion, true); standard Timer/watchdog",
      "clock":"normal foreground; debug=false","initialCachesEmpty":True,
      "plannedManifests":[x["manifest"] for x in c["plannedCandidates"]],
      "applicationOutcome":"PASS","classification":"PASS","terminalStage":"PUBLISH",
      "generationElapsedMs":1200,"startToCallbackWallMs":1300,"workUnits":396,"maxActiveOperationMs":5,
      "maxCoordinatorAdvanceMs":8,"headroomMs":88800,"yields":42,"foregroundClock":True,"hiddenEvents":0,
      "ordinaryCacheHits":0,"ordinaryCacheMisses":3,"ordinaryCacheSizeBeforeCleanup":1,
      "routingElapsedMsNested":450,"proofElapsedMsNested":620,
      "stages":[{"stage":n,"elapsedMs":200,"workUnits":units} for n,units in zip(("RESOLVE","HEALTHY","PHYSICAL","HYPOTHESES","SYMPTOM","PUBLISH"),(1,2,1,390,1,1))],
      "attempts":[{"ordinal":0,"manifest":p["manifest"],"stage":"PUBLISH","outcome":"PASS","workUnits":396,"proofUnits":390,"failureType":None,"failureMessage":None}],
      "candidates":[{"ordinal":0,"seed":p["seed"],"actualPackages":33,"physical":True,"diagnostic":True,
        "difficulty":"difficulty/2;profile=MEDIUM;test-only","published":True,"aborted":False}],
      "startScopeChecks":1,"publicationScopeChecks":1,"generationReceiptManifest":p["manifest"],
      "difficultyAssessment":"MEDIUM@2;test-only","cleanupComplete":True,"cleanupElapsedMs":15,
      "privateCacheUntouched":True,
      "proof":{"warmReuse":False,"context":"test-only","program":"test-only","partition":"test-only",
        "explicitCompletion":True,"evidence":[{"hypothesis":f"H{i}","repairReachable":True,"customerRetestPassed":True,
        "stateIsolated":True,"deterministicResult":"test-only","samples":[{"id":f"S{n}","outcome":"NUMERIC","value":1.25,"tolerance":0.01} for n in range(37)]} for i in range(5)]}}

class ReaderContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.plan,cls.plan_raw=reader.load(PLAN_PATH); cls.plans=reader.frozen(cls.plan,cls.plan_raw)
    def test_synthetic_pass_fixture_is_only_a_schema_test(self):
        r=synthetic_pass_test_only(self.plan); result=reader.app_ok(r,b"synthetic-test-fixture",self.plans)
        self.assertEqual(result["status"],"APP_PASS")
    def test_clean_timeout_is_completed_nonpass_without_proof(self):
        r=synthetic_pass_test_only(self.plan); r["classification"]="TIMEOUT"; r["applicationOutcome"]="TIMEOUT"
        r["terminalStage"]="HYPOTHESES"; r["attempts"][0]["outcome"]="TIMEOUT"
        r["candidates"][0].update(published=False,aborted=True); r.pop("proof"); r.pop("difficultyAssessment")
        self.assertEqual(reader.app_ok(r,b"synthetic-timeout",self.plans)["status"],"COMPLETED_NONPASS")
        r=synthetic_pass_test_only(self.plan); r["classification"]="ADMISSION_REJECTED"; r["applicationOutcome"]="EXPECTED_REJECTION"
        r["attempts"][0]["outcome"]="EXPECTED_REJECTION"; r["candidates"][0].update(published=False,aborted=True)
        r.pop("proof"); r.pop("difficultyAssessment")
        self.assertEqual(reader.app_ok(r,b"synthetic-admission-rejected",self.plans)["status"],"COMPLETED_NONPASS")
    def test_pass_rejects_mutated_contracts(self):
        mutations=[("warm hits",lambda r:r.update(ordinaryCacheHits=1)),
          ("hidden tab",lambda r:r.update(hiddenEvents=1)),("cold cache",lambda r:r.update(initialCachesEmpty=False)),
          ("private cache",lambda r:r.update(privateCacheUntouched=False)),("work limit",lambda r:r.update(workUnits=641)),
          ("operation limit",lambda r:r.update(maxActiveOperationMs=5001)),("deadline",lambda r:r.update(generationElapsedMs=90001)),
          ("seed",lambda r:r.update(rootSeed="8")),("candidate order",lambda r:r["plannedManifests"].reverse()),
          ("cleanup",lambda r:r.update(cleanupComplete=False)),("proof sample count",lambda r:r["proof"]["evidence"][0]["samples"].pop())]
        for name,mutate in mutations:
            with self.subTest(name=name):
                r=synthetic_pass_test_only(self.plan); mutate(r)
                with self.assertRaises(reader.Invalid): reader.app_ok(r,b"synthetic-negative",self.plans)
    def test_frozen_plan_hash_is_enforced(self):
        with self.assertRaises(reader.Invalid): reader.frozen(self.plan,self.plan_raw+b" ")
    def test_wrong_difficulty_is_completed_rejection(self):
        r=synthetic_pass_test_only(self.plan)
        r.update(classification="ADMISSION_REJECTED",applicationOutcome="EXPECTED_REJECTION")
        r["attempts"][0]["outcome"]="EXPECTED_REJECTION"
        r["candidates"][0].update(published=False,aborted=True,difficulty="difficulty/2;profile=EASY;test-only")
        r.pop("proof")
        self.assertEqual(reader.app_ok(r,b"synthetic-difficulty-rejection",self.plans)["status"],"COMPLETED_NONPASS")
    def test_host_pass_never_changes_application_status(self):
        app=synthetic_pass_test_only(self.plan); raw=b"synthetic-app-report"; digest=hashlib.sha256(raw).hexdigest()
        case={"outcome":"PASS","terminalReached":True,"stateMatch":True,"reportObserved":True,"reportJsonValid":True,
          "reportMatch":True,"reportSha256":digest,"reportLength":len(raw),"url":"http://127.0.0.1/?tsjNormalMode=cold&tsjNormalSeed=7"}
        for k in ("pageErrors","httpErrors","consoleErrors","attributeReadErrors","listenerCleanupErrors","compiledResourceErrors"): case[k]=[]
        host={"outcome":"PASS","caseCount":1,"errors":[],"inputAudit":{"status":"PASS","beforeSha256":"same","afterSha256":"same","sourceSha256Before":"src","sourceSha256After":"src","webSha256Before":"web","webSha256After":"web","runnerBeforeSha256":"run","runnerAfterSha256":"run"},
          "cleanup":{"status":"PASS","serverStopped":True,"ownedSurvivors":[],"errors":[]},"cases":[case]}
        self.assertEqual(reader.host_ok(host,app,raw)["status"],"HOST_PASS")
        self.assertEqual(reader.app_ok(app,b"synthetic-app-report",self.plans)["status"],"APP_PASS")

if __name__=="__main__": unittest.main()
