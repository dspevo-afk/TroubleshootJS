"""Accept finite first-result observations while retaining failed observer actions."""
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
identities={"rc":"tsj-alpha/6/EASY/RC_DELAY/5630627058468165895",
            "composed":"tsj-alpha/6/MEDIUM/COMPOSED_CONTROLLED_INDICATOR/-7472674895268110325",
            "multirail":"tsj-alpha/6/MEDIUM/RB30_CONTROL/-5776064669694490064"}
for family,replay in identities.items():
    assert read(family+"-identity.json")=={"replay":replay,"build":"tsj-alpha/6 / F4CA0D0FF6F4318FEF7078DA7D7978B3"}
    assert "Accept ticket and start" in text(family+"-first-ticket-body.txt")
    assert "Customer retest did not pass" in text(family+"-original-fail-body.txt")
    assert "Board Power: OFF" in text(family+"-final-off-body.txt") and "0 parts" in text(family+"-final-off-body.txt")
supplies={"rc":["Supply 1 (+12V)"],"composed":["Supply "+str(i)+" (+5V)" for i in (1,2,3)],
          "multirail":["Supply 1 (+12V)","Supply 2 (+5V)","Supply 3 (+5V)","Supply 4 (+12V)"]}
for family,names in supplies.items():
    assert all(n+": disconnected" in text(family+"-final-off-body.txt") for n in names)
assert "Board Power: OFF" in text("multirail-final-board-off-body.txt") and "0 parts" in text("multirail-final-board-off-body.txt")
assert "Preparing and checking the board" in text("multirail-first-terminal-observation-body.txt")
assert "Candidate 1 of 4: Verify measurements and repairs" in text("multirail-first-terminal-observation-body.txt")
events=read("player-events.json");failed=[e for e in events if e["status"]=="FAIL"]
assert len(events)==26 and sum(e["status"]=="PASS" for e in events)==22
assert [e["name"] for e in failed]==[
    "Open actual unchanged accepted production build",
    "Record first Timing board public identity and return to normal menu",
    "Fit first fresh Two-channel controller",
    "Request first fresh MEDIUM Multi-rail control board through actual New board"]
assert all("Timeout" in e["error"] for e in (failed[0],failed[2],failed[3]))
assert "strict mode violation" in failed[1]["error"]
assert len([e for e in events if e["name"].startswith("Request first fresh ")])==3
assert any(e["name"]=="Observe completion of the same pending first Multi-rail request without retry" and e["status"]=="PASS" for e in events)
logs=read("player-log-hashes.json")
assert logs["consoleEntries"]==1293 and logs["consoleTypes"]=={"error":2,"log":1291}
assert logs["errors"]=={"page":[],"request":[],"response":[]}
cleanup=read("process-resource-cleanup.json")
assert cleanup["status"]=="PASS" and cleanup["recordedBirths"]==6 and len(cleanup["checks"])==6 and cleanup["actualPreviewStopExecExitCode"]==0
assert all(c["recordedBirthExited"] is True for c in cleanup["checks"])
assert all(cleanup[k] is True for k in ("managedStateAbsent","port8907Released","browserApiExit0","currentProfileAbsent"))
frames=["rc-fitted.jpg","composed-fitted.jpg","composed-bottom.jpg","multirail-fitted.jpg","multirail-detail.jpg"]
assert all((HERE/n).read_bytes().startswith(bytes.fromhex("ffd8")) for n in frames)
result={"status":"PASS_FINITE_SAMPLE_WITH_RETAINED_OBSERVER_FAILURES","baseCommit":"29c85f95fa2849d46bde4227a48ef2b62ab7420b",
    "inputSealSha256":sha("inputs.json"),"identities":{f:read(f+"-identity.json") for f in identities},
    "sample":"Exactly3 first normal New board requests/admissions/untouched customerFAILs; no requested seed/replay substitution, retry, part/probe/measurement/repair mutation or forced/controller/DOM input",
    "startup":"QuickPlay automatically displayed an initial timing ticket before any sample request; retained startup-default-ticket-body.txt, not counted as a fresh sample",
    "actions":"26 logged observations:22PASS/4FAIL retained. Initial wrong New board wait30.639s, hidden-duplicate Main menu strict violation0.009s, wrong View role wait30.009s; corrected only selectors. Multi-rail40.063s wait expired while same first request was at Candidate1/measurement checks; separate pending observation completed without a new request. No generation performance claim",
    "images":{n:{"sha256":sha(n),"bytes":(HERE/n).stat().st_size} for n in frames},
    "visual":"Root inspected all5 raw1280x900 views;3fitted boards pasted. Composed bottom golden exposed PTH annuli are separate from mounted-face metallic collars; no changed bottom paint selected. Multi-rail far-right isolated connector and broad green gap warrant exact replay diagnosis, not a demonstrated removable physical region",
    "sourceBoundary":"Exact1482/24/gates5 and independent compiled/served6 before/after; accepted native10/strict44/actualJDK8GWT5 reused only at unchanged consumed inputs; no new native/build/matrix run",
    "finalState":"RC12V, composed3x5V, multi12V/5V/5V/12V each independently disconnected; finalBoardOFF/tray0 each; final multi fitted",
    "console":"1293=1291logs+2unattributed resource404; private raw/hash-bound, page/request/observed-response errors0; no clean-console attribution",
    "cleanup":"All6 recorded births exited, browserAPI0/currentprofile absent, maintained stop2.0199258s/state absent/8907released; older blockedprofiles/private/unrelated data retained",
    "limits":"Finite first3 results, preserved failed observers, no all-seed/new-size/manufacturing/performance/Edge/CDP-wrapper/FileReader/full-matrix claim; screenshots do not establish an electrically legal compaction"}
(HERE/"player-assessment.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
print("PASS finite first3 samples/source/provenance/off/cleanup;4 failed observer actions retained")
