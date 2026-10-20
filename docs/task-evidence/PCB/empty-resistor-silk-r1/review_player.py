"""Read finite final-player evidence; no solver or visual assertion is inferred from text."""
import hashlib,json,runpy
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
def read(n):return json.loads((HERE/n).read_text(encoding="utf-8-sig"))
def text(n):return (HERE/n).read_text(encoding="utf-8-sig")
def sha(n):return hashlib.sha256((HERE/n).read_bytes()).hexdigest()
def contains(n,*values):
    body=text(n)
    assert all(v in body for v in values),(n,values)

candidate=read("candidate-inputs.json")
assert runpy.run_path(str(HERE/"inputs.py"))["record"]()==candidate
assert all(sha(n)==h for n,h in read("player-plan-seal.json")["files"].items())
for phase in ("contracts","census","build"):
    result=read(phase+"-assessment.json")
    assert result["status"]=="PASS" and result["sources"]==1482
baseline=HERE.parent/"empty-resistor-baseline-r1"
assert json.loads((baseline/"baseline-assessment.json").read_text())["status"]=="PASS_PRE_EDIT_BASELINE"
# inputs.record above verifies the immutable pre-edit evidence hashes; do not
# rerun that baseline's deliberately unchanged-production reader after edits.
execution=read("player-expected-execution.json")
assert execution==read("post-player-provenance.json")
attrs={"identity-protocol":"Protocol","source-digest":"SourceDigest","script-digest":"ScriptDigest",
       "web-digest":"WebDigest","execution-digest":"Digest","file-count":"FileCount"}
for side in ("before","after"):
    served=read("player-served-"+side+".json")
    assert set(served)=={"data-tsj-preview-"+k for k in attrs}
    assert all(served["data-tsj-preview-"+k]==str(execution[v]) for k,v in attrs.items())
    assert text("player-input-audit-"+side+".txt").strip()=="PASS empty axial exact inputs phase=candidate sources=1482 gates=6"
    assert read("preview-owner-"+side+".json")["status"]=="PASS"
plan=read("player-plan.json")
for family in ("led","sensor"):
    assert read(family+"-public-identity.json")=={
        "replay":plan["replays"][family],"build":"tsj-alpha/6 / F4CA0D0FF6F4318FEF7078DA7D7978B3"}
controls={x["label"]:{y["value"]:y["label"] for y in x["options"]} for x in read("led-public-controls.json")}
assert controls["Select component"]["R1"]=="R1"
assert all(controls["Probe terminal"]["R1."+str(i)]=="R1 terminal "+str(i) for i in (1,2))
controls={x["label"]:{y["value"]:y["label"] for y in x["options"]} for x in read("sensor-public-controls.json")}
for component,reference in {"RBIAS":"R1","RREF":"R2","RFB":"R3","RREF_LOW":"R4","RFB_HYST":"R5"}.items():
    assert controls["Select component"][component]==reference
    assert all(controls["Probe terminal"][component+"."+str(i)]==reference+" terminal "+str(i) for i in (1,2))
for family in ("led","sensor"):
    contains(family+"-original-fail-body.txt","Customer retest did not pass")
contains("led-original-installed-off-body.txt","State: Installed","Board Power: OFF")
contains("led-original-lifted-off-body.txt","State: Lead Lifted","Board Power: OFF")
for face in ("top","bottom"):
    contains("led-empty-"+face+"-body.txt","Board Power: OFF","1 part")
contains("led-original-tray-body.txt","Part 2 - Color bands")
contains("led-original-install-options-body.txt","Install as Circuit / R1")
contains("led-original-restored-installed-body.txt","State: Installed","0 parts")
contains("led-original-restored-fail-observed-body.txt","Customer retest did not pass")
contains("led-isolated-original-lifted-body.txt","State: Lead Lifted","Board Power: OFF")
contains("led-original-isolated-ohm-body.txt","\nOL\n","Board Power: OFF","R1 terminal 1","R1 terminal 2")
contains("led-replacement-tray-body.txt","Part 4","330")
contains("led-330-install-options-body.txt","Install as Circuit / R1")
contains("led-replacement-330-ohm-body.txt","\n330 Ohm\n","Board Power: OFF")
contains("led-customer-passed-body.txt","FUNCTION VERIFIED","Return to board","Customer retest passed")
contains("led-final-off-body.txt","Supply 1 (+5V): disconnected","Board Power: OFF","1 part")
contains("sensor-r5-original-installed-off-body.txt","State: Installed","Board Power: OFF","R5")
contains("sensor-r5-empty-top-body.txt","Board Power: OFF","1 part")
contains("sensor-r5-original-tray-body.txt","Part 10 - Color bands")
contains("sensor-r5-original-install-options-body.txt","Install as Circuit / R5")
contains("sensor-r5-original-restored-installed-body.txt","State: Installed","0 parts")
contains("sensor-original-restored-fail-observed-body.txt","Customer retest did not pass")
contains("sensor-final-off-body.txt","Board Power: OFF","0 parts")
events=read("player-events.json")
assert len(events)==26 and all(e["status"]=="PASS" for e in events)
logs=read("player-log-hashes.json")
assert logs["consoleEntries"]==130 and logs["consoleTypes"]=={"error":1,"log":129}
assert logs["errors"]=={"page":[],"request":[],"response":[]}
cleanup=read("process-resource-cleanup.json")
assert cleanup["status"]=="PASS" and cleanup["recordedBirths"]==12 and cleanup["preEditBaselineBirths"]==6 and cleanup["combinedRecordedBirths"]==18
assert cleanup["actualPreviewStopExecExitCode"]==0
assert len(cleanup["checks"])==12 and len(cleanup["baselineChecks"])==6
assert all(c["recordedBirthExited"] is True for c in cleanup["checks"]+cleanup["baselineChecks"])
assert all(cleanup[k] is True for k in ("managedStateAbsent","port8907Released","browserApiExit0","currentProfileAbsent"))
frames=plan["primaryImages"]
assert len(frames)==5 and all((HERE/n).read_bytes().startswith(bytes.fromhex("ffd8")) for n in frames)
result={"status":"PASS_FINITE_FINAL_PLAYER","baseCommit":candidate["baseCommit"],
    "candidateSha256":sha("candidate-inputs.json"),"build":"F4CA0D0FF6F4318FEF7078DA7D7978B3",
    "identities":{f:read(f+"-public-identity.json") for f in ("led","sensor")},
    "input":"26 actual normal production actions allPASS; no timeouts, controller calls, DOM mutation, forced input or seed substitution",
    "visual":"Root inspected all5 raw1280x900 JPEGs: installed LED resistor; thin unfilled rounded empty R1 guide clear of lands/reference; bottom view without guide; fresh customerPASS; denser empty Sensor R5 guide clear of adjacent parts/lands/reference. Empty LED and Sensor images pasted in chat",
    "images":{n:{"sha256":sha(n),"bytes":(HERE/n).stat().st_size} for n in frames},
    "led":"OriginalPart2 Installed/customerFAIL, OFF lift/remove/top-bottom/original restore; original complaint observed after repeat retest. ActualOFF isolated original OL, actualShopPart4/330Ohm install andOFF330Ohm, exitmode/connect5V/fresh awaited customerPASS modal; final5Vdisconnected/BoardOFF/tray1",
    "sensor":"Exact prior fresh optionalR5 replay, initialFAIL; BoardOFF originalPart10 removal/rounded guide/original CircuitR5 restore Installed/tray0; original complaint observed after repeat retest; finalBoardOFF/tray0. No Sensor measurement/repair claim",
    "provenance":"Exact candidate1482/24/gates6 and independent CLI/served6 before/after, player wrappers/plan seal retained; prior baseline hashes immutable",
    "console":"130=129log+1unattributed resource404; private raw hash retained, page/request/observed-response errors0; no clean-console attribution",
    "cleanup":"All current12 plus pre-edit6 recorded births exited, API0/current profile absent, maintained stop1.7925389s/state absent/8907 released; older blocked profiles/private/unrelated data preserved",
    "limits":"Finite2 actual replays and native44 census only; pure native slot association does not simulate lifted state. Repeated originalFAIL is observed complaint after actual click, without independent freshness token; freshPASS awaited new modal. No all-seed/new-size/manufacturing/performance/Edge/CDP-wrapper/newFileReader/full-matrix claim"}
(HERE/"player-assessment.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
print("PASS actual final empty axial player/source/provenance/repair/restoration/cleanup")
