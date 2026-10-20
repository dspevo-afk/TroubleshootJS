"""Require literal public marks and exact accepted physical/graph/work observations."""
import copy, difflib, hashlib, json, math, re, runpy, sys
from pathlib import Path
if not __debug__: raise RuntimeError("Assertion-enabled Python required")
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
FAMILIES = ("LED_INDICATOR", "DIODE_PROTECTED_INDICATOR", "PARALLEL_DUAL_INDICATOR", "RC_DELAY",
            "NPN_LOW_SIDE_SWITCH", "NMOS_LOW_SIDE_SWITCH", "RELAY_OUTPUT", "SENSOR_CONTROL",
            "RB15_CONTROL", "COMPOSED_CONTROLLED_INDICATOR", "RB30_CONTROL")
ROOTS = ("0", "17", "13", "-9223372036854775808")
CHANGED_FAMILIES = {"NPN_LOW_SIDE_SWITCH", "NMOS_LOW_SIDE_SWITCH", "RELAY_OUTPUT", "SENSOR_CONTROL", "RB15_CONTROL"}
def read(p): return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def text(p): return p.read_text(encoding="utf-8-sig")
def marks(family, components):
    ids = {p["id"] for p in components}
    if family == "SENSOR_CONTROL":
        result = {"RBIAS":"R1", "RREF":"R2", "RFB":"R3", "RREF_LOW":"R4"}
        if "RFB_HYST" in ids: result["RFB_HYST"] = "R5"
    elif family == "NMOS_LOW_SIDE_SWITCH": result = {"RLOAD":"R1", "RPD":"R2"}
    elif family == "NPN_LOW_SIDE_SWITCH": result = {"RLOAD":"R1", "RB":"R2", "RPD":"R3"}
    elif family == "RELAY_OUTPUT": result = {"RDRIVE":"R1", "RPD":"R2"}
    elif family == "RB15_CONTROL":
        result = {"RBLEED":"R1", "RIN":"R2"}
        if "RBIAS" in ids: result.update({"RBIAS":"R3", "RDRIVE":"R4", "RPD":"R5", "RLED":"R6"})
        else: result.update({"RDRIVE":"R3", "RPD":"R4", "RLED":"R5"})
    else: return {}
    assert set(result) == {p["id"] for p in components if p["type"].endswith("RESISTOR")}
    assert len(set(result.values())) == len(result)
    return result
def cases(path, prefix):
    rows = {}
    for line in text(path).splitlines():
        if line.startswith(prefix):
            row = json.loads(line[len(prefix):]); key = (row["family"],row["root"])
            assert key not in rows; rows[key] = row
    assert set(rows) == {(f,r) for f in FAMILIES for r in ROOTS}
    return rows
def valid_timing(t):
    assert t["status"] == "PASS" and type(t["exitCode"]) is int and t["exitCode"] == 0
    assert type(t["elapsedSeconds"]) in (int,float) and math.isfinite(t["elapsedSeconds"]) and t["elapsedSeconds"] >= 0
    assert t["terminalCleanupIncluded"] is True and t["failure"] is None
def baseline_contract():
    prior = HERE.parent/"connector-housing-r1/census-receipt.txt"
    references = cases(prior,"PCB_NORMAL_REFERENCE_CASE ")
    sensor, rb15 = set(),set()
    for (family,root),row in references.items():
        expected = marks(family,row["components"])
        for c in row["components"]:
            if c["id"] in expected:
                assert c["name"] == c["id"] and c["referenceLabelCharacters"] == len(c["id"])
                assert len(expected[c["id"]]) <= c["referenceLabelCharacters"]
        if family == "SENSOR_CONTROL": sensor.add("RFB_HYST" in expected)
        if family == "RB15_CONTROL": rb15.add("RBIAS" in expected)
    assert sensor == rb15 == {False,True}
    return {"cases":44,"changedFamilyCases":20,"sensorFeedbackBranches":2,"rb15FilterBranches":2,"receiptSha256":sha(prior)}
def census():
    prior = HERE.parent/"connector-housing-r1/census-receipt.txt"
    current = HERE/"census-receipt.txt"
    old = {n:cases(prior,p) for n,p in (("packing","PCB_NORMAL_PACKING_CASE "),("references","PCB_NORMAL_REFERENCE_CASE "),("leads","PCB_LEAD_SILK_CASE "),("titles","PCB_NORMAL_SILK_CASE "))}
    new = {n:cases(current,p) for n,p in (("packing","PCB_NORMAL_PACKING_CASE "),("references","PCB_NORMAL_REFERENCE_CASE "),("leads","PCB_LEAD_SILK_CASE "),("titles","PCB_NORMAL_SILK_CASE "))}
    changed = []
    for key,a in old["references"].items():
        b = new["references"][key]; expected = marks(key[0],a["components"])
        assert set(a) == set(b) and a["labelCount"] == b["labelCount"]
        assert a["physicalBoardSha256"] == b["physicalBoardSha256"]
        assert a["geometryApartFromReferenceTextSha256"] == b["geometryApartFromReferenceTextSha256"]
        assert len(a["components"]) == len(b["components"])
        for first,second in zip(a["components"],b["components"]):
            wanted = dict(first)
            if first["id"] in expected: wanted["name"] = expected[first["id"]]
            assert second == wanted,("component metadata",key,first["id"])
        assert len(a["labels"]) == len(b["labels"])
        for first,second in zip(a["labels"],b["labels"]):
            wanted = dict(first)
            if first["id"].startswith("component:") and first["id"][10:] in expected:
                wanted["text"] = expected[first["id"][10:]]
            assert second == wanted,("physical label metadata",key,first["id"])
        if expected:
            assert a["components"] != b["components"] and a["labels"] != b["labels"]
            changed.append(key)
        else: assert a == b
        first,second = old["packing"][key],new["packing"][key]
        assert second["status"] == "ACCEPTED" and second["graphBefore"] == second["graphAfter"]
        skip = {"elapsedMillis","maxAdvanceMillis","generationRoutingMillis"} | ({"geometrySha256"} if expected else set())
        assert set(first) == set(second) and {k:v for k,v in first.items() if k not in skip} == {k:v for k,v in second.items() if k not in skip},("graph/physical/metrics/work",key)
        for name,hashkey in (("leads","geometryApartFromLegendPositionAndEpochSha256"),("titles","geometryApartFromTitleSha256")):
            first,second = old[name][key],new[name][key]
            assert set(first) == set(second)
            skip = {hashkey} if expected else set()
            assert {k:v for k,v in first.items() if k not in skip} == {k:v for k,v in second.items() if k not in skip},(name,key)
    assert len(changed) == 20 and {f for f,r in changed} == CHANGED_FAMILIES
    whitespace = lambda p:[json.loads(s[len("PCB_WHITESPACE_CASE "):]) for s in text(p).splitlines() if s.startswith("PCB_WHITESPACE_CASE ")]
    a,b = whitespace(prior),whitespace(current)
    assert len(a) == len(b) == 44 and a == b
    result = {"status":"PASS","cases":44,"changedReferenceCases":changed,"unchangedReferenceCases":24,"whitespaceRowsExact":44,"baselineContract":baseline_contract(),"boundary":"Only literal five-family resistor display names/label texts and dependent full ink hashes; graph, physical fingerprint, normalized geometry, all label boxes/style/targets/reservations, metrics and work exact; only3 existing packing timing fields excluded"}
    (HERE/"census-comparison.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
def canaries():
    sample = {"status":"PASS","exitCode":0,"elapsedSeconds":1.0,"terminalCleanupIncluded":True,"failure":None}
    valid_timing(sample)
    for k,v in (("exitCode",False),("exitCode",0.0),("exitCode",2),("status","FAIL"),("elapsedSeconds",False),("elapsedSeconds",float("nan"))):
        frame = copy.deepcopy(sample); frame[k] = v
        try: valid_timing(frame)
        except AssertionError: pass
        else: raise RuntimeError("Invalid timing accepted")
    result = {"status":"PASS","pureTimingPositive":1,"invalidTimingRejected":6,"actualAcceptedBaseline":baseline_contract(),"limits":"Pure reader framing and existing baseline parsing only; no new native/build/process claim"}
    (HERE/"pre-edit-canaries.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    print("PASS pure timing canaries and accepted44 literal baseline/variant coverage")
def main():
    if sys.argv[1:] == ["--canaries"]: canaries(); return
    phase, = sys.argv[1:]; assert phase in ("fixtures","contracts","census","build")
    inp = runpy.run_path(str(HERE/"inputs.py")); candidate = read(HERE/"candidate-inputs.json")
    assert inp["record"]() == candidate
    baseline = read(HERE/"baseline-inputs.json")
    for which,manifest,names in (("baseline",baseline,inp["EXISTING"]),("candidate",candidate,inp["EXISTING"]|{inp["NEW"]})):
        archive = HERE/(which+"-source")
        assert {p.relative_to(archive).as_posix():sha(p) for p in archive.rglob("*") if p.is_file()} == {n:manifest["files"][n] for n in names}
    test = "tests/contracts/PhysicalServiceabilityContractTest.java"
    old,new = [text(HERE/(w+"-source")/test).splitlines() for w in ("baseline","candidate")]
    assert all(op in ("equal","insert") for op,*_ in difflib.SequenceMatcher(None,old,new,autojunk=False).get_opcodes())
    t = read(HERE/(phase+"-timing.json")); valid_timing(t); assert t["phase"] == phase
    launchpath = HERE/(phase+"-process-launch.json"); launch = read(launchpath)
    cleanup = read(HERE/(phase+"-process-cleanup.json"))
    assert launch["phase"] == cleanup["phase"] == phase and cleanup["status"] == "PASS"
    assert type(cleanup["actualExecExitCode"]) is int and cleanup["actualExecExitCode"] == 0
    assert cleanup["launchSha256"] == sha(launchpath)
    assert len(launch["drivers"]) == len(cleanup["checks"]) == 2
    assert [{k:r[k] for k in ("pid","startTicks","executable")} for r in cleanup["checks"]] == launch["drivers"]
    assert all(r["recordedBirthExited"] is True for r in cleanup["checks"])
    for side in ("before","after"):
        assert text(HERE/(phase+"-input-audit-"+side+".txt")).strip() == "PASS normal resistor exact inputs phase=candidate sources=1482 gates=5"
    log = text(HERE/(phase+".txt"))
    if phase == "build":
        for marker in ("Compiling module com.lushprojects.circuitjs1.circuitjs1","Compiling 5 permutations","Compile of permutations succeeded","Compilation succeeded --","Link succeeded","Linking succeeded --"): assert log.count(marker) == 1,marker
        assert "BUILD_FAILURE" not in log and "BUILD_INFRASTRUCTURE" not in log
    else:
        suites = read(HERE/"suites.json")[phase]
        assert log.count("PASS: focused current contracts; "+str(len(suites))+" Java suites. Full matrix and independent oracles NOT RUN.") == 1
        assert "CURRENT_CONTRACT_FAILURE:" not in log
        definitions = dict(re.findall(r"Name = '([^']+)'; Marker = '([^']+)'",text(ROOT/"scripts/verify-current-contracts.ps1")))
        receipt = text(HERE/(phase+"-receipt.txt"))
        for suite in suites: assert re.search(r"(?m)^PASS: "+re.escape(definitions[suite]),receipt),suite
        if phase == "census": census()
    (HERE/(phase+"-assessment.json")).write_text(json.dumps({"status":"PASS","phase":phase,"elapsedSeconds":t["elapsedSeconds"],"sources":1482,"limits":"Named actual final-source phase only; player/publication and root production-diff acceptance separate"},indent=2)+"\n",encoding="utf-8")
    print("PASS normal resistor actual phase "+phase)
if __name__ == "__main__": main()
