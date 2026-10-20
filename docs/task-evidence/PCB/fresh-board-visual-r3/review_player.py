"""Assess the predeclared first normal results at an exact accepted build boundary."""
import hashlib,json,runpy
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
def read(n):return json.loads((HERE/n).read_text(encoding="utf-8-sig"))
def text(n):return (HERE/n).read_text(encoding="utf-8-sig")
def sha(n):return hashlib.sha256((HERE/n).read_bytes()).hexdigest()
assert runpy.run_path(str(HERE/"inputs.py"))["record"]()==read("inputs.json")
expected=read("reused-execution.json")
assert expected==read("player-expected-execution.json")==read("post-player-provenance.json")
attrs={"identity-protocol":"Protocol","source-digest":"SourceDigest","script-digest":"ScriptDigest",
       "web-digest":"WebDigest","execution-digest":"Digest","file-count":"FileCount"}
for side in ("before","after"):
    served=read("player-served-"+side+".json")
    assert set(served)=={"data-tsj-preview-"+k for k in attrs}
    assert all(served["data-tsj-preview-"+k]==str(expected[v]) for k,v in attrs.items())
    assert text("player-input-audit-"+side+".txt").strip()=="PASS read-only fresh sample exact1482 machinery24 gates5 compiled6 reuse"
    assert read("preview-owner-"+side+".json")["status"]=="PASS"
for field in ("pid","startTicks"):
    assert read("preview-owner-before.json")[field]==read("preview-owner-after.json")[field]
cases={"protected":("DIODE_PROTECTED_INDICATOR","-3095773799014904360",("+12V",)),
       "dual":("PARALLEL_DUAL_INDICATOR","5196087953877956905",("+5V",)),
       "relay":("RELAY_OUTPUT","838360342305795577",("+5V","+5V","+12V"))}
identities={}
for name,(family,root,voltages) in cases.items():
    identity=read(name+"-identity.json");identities[name]=identity
    assert identity["replay"]=="tsj-alpha/6/EASY/"+family+"/"+root
    assert identity["build"]=="tsj-alpha/6 / 47CB6E7FB2C6397086F05F5DFA2345EB"
    assert "Accept ticket and start" in text(name+"-first-ticket-body.txt")
    assert "Customer retest did not pass" in text(name+"-original-fail-body.txt")
    off=text(name+"-final-off-body.txt")
    assert "Board Power: OFF" in off and "0 parts" in off
    assert all("Supply "+str(i)+" ("+v+"): disconnected" in off for i,v in enumerate(voltages,1))
events=read("player-events.json")
assert len(events)==17 and all(e["status"]=="PASS" for e in events)
assert [e["name"] for e in events if "New board request" in e["name"]]==[
    "First actual EASY Protected New board request","First actual EASY Dual indicator New board request","First actual EASY Relay New board request"]
logs=read("player-log-hashes.json")
assert logs["consoleEntries"]==279 and logs["consoleTypes"]=={"error":1,"log":278}
assert logs["errors"]=={"page":[],"request":[],"response":[]}
cleanup=read("process-resource-cleanup.json")
assert cleanup["status"]=="PASS" and cleanup["recordedBirths"]==6 and cleanup["actualPreviewStopExecExitCode"]==0
assert all(c["recordedBirthExited"] is True for c in cleanup["checks"])
assert all(cleanup[k] is True for k in ("managedStateAbsent","port8907Released","browserApiExit0","currentProfileAbsent"))
frames=["protected-fitted.jpg","dual-fitted.jpg","relay-fitted.jpg","relay-transistor-inspection.jpg"]
assert all((HERE/n).read_bytes().startswith(bytes.fromhex("ffd8")) for n in frames)
result={"status":"PASS_READ_ONLY_FIRST_RESULTS","baseCommit":"4934d4a05e743e88039fdacdc45a054e18961eeb",
        "identities":identities,"admittedFirstResults":3,"retriesOrSubstitutions":0,"actions":17,
        "sourceReuse":"All1482 accepted source/24 machinery/gates5 and independent compiled6/served6 exact before/after; accepted prior native/build evidence reused only at this boundary; no new native/build/matrix run",
        "player":"Each first normal EASY New board admitted, untouched customerFAIL, all outputs disconnected/BoardOFF/tray0 before leaving; no part/probe/measurement/repair/save mutation",
        "visual":"Four raw1280x900 primary JPEGs inspected, three fitted images pasted in chat; fresh Relay uses conventional R1/R2 marks",
        "images":{n:{"sha256":sha(n),"bytes":(HERE/n).stat().st_size} for n in frames},
        "postedInChat":["protected-fitted.jpg","dual-fitted.jpg","relay-fitted.jpg"],
        "observation":"Initial fitted transistor body looked oval; actual wheel close-up shows existing flat lower face. Separate read-only source owner review confirms it; no paint defect/fix claimed.",
        "console":"279 entries=278log+1unattributed resource404; raw private/hash-bound, page/request/observed-response errors0; no clean-console attribution",
        "cleanup":"All6 recorded births exited; API browser0/current profile absent; maintained stop2.0978425s/state absent/8907 released; older blocked profiles/private resources retained",
        "next":"Read-only source feasibility for inert empty axial resistor assembly outlines; actual normal removal baseline required before any production scope selection",
        "limits":"Three first normal family results only; no all-seed/new-size/manufacturing/performance/Edge/CDP-wrapper/newFileReader/full-matrix claim"}
(HERE/"player-assessment.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
print("PASS first Protected/Dual/Relay visible sample, exact source/build reuse, identity/power/cleanup")
