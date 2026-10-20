"""Assess captured visible input and exact provenance; preserve preparation failures."""
import hashlib, json, runpy
from pathlib import Path
if not __debug__: raise RuntimeError("Assertion-enabled Python required")
HERE = Path(__file__).resolve().parent
def read(n): return json.loads((HERE/n).read_text(encoding="utf-8-sig"))
def text(n): return (HERE/n).read_text(encoding="utf-8-sig")
def sha(n): return hashlib.sha256((HERE/n).read_bytes()).hexdigest()
inputs = runpy.run_path(str(HERE/"inputs.py"))
assert inputs["record"]() == read("candidate-inputs.json")
for seal in ("player-plan-seal.json", "player-host-plan-seal.json"):
    assert all(sha(n) == digest for n,digest in read(seal)["files"].items()), seal
assert sha("observe-player-host-attempt1.ps1") == read("player-host-plan-seal-attempt1.json")["files"]["observe_player_host.ps1"]
for phase in ("fixtures","contracts","census","build"):
    assert read(phase+"-assessment.json")["status"] == "PASS"
expected = read("player-expected-execution.json")
attrs = {"identity-protocol":"Protocol","source-digest":"SourceDigest","script-digest":"ScriptDigest",
         "web-digest":"WebDigest","execution-digest":"Digest","file-count":"FileCount"}
assert set(expected) == set(attrs.values()) and expected == read("post-player-provenance.json")
for side in ("before","after"):
    actual = read("player-served-"+side+".json")
    assert set(actual) == {"data-tsj-preview-"+k for k in attrs}
    assert all(actual["data-tsj-preview-"+k] == str(expected[v]) for k,v in attrs.items())
    assert read("preview-owner-"+side+".json")["status"] == "PASS"
    assert text("player-input-audit-"+side+".txt").strip() == "PASS normal resistor exact inputs phase=candidate sources=1482 gates=5"
assert read("preview-owner-before.json")["pid"] == read("preview-owner-after.json")["pid"]
assert read("preview-owner-before.json")["startTicks"] == read("preview-owner-after.json")["startTicks"]
plan = read("player-plan.json")
identities = {family:read(family+"-public-identity.json") for family in plan["replays"]}
assert all(value["replay"] == plan["replays"][family] for family,value in identities.items())
assert {value["build"] for value in identities.values()} == {"tsj-alpha/6 / 47CB6E7FB2C6397086F05F5DFA2345EB"}
maps = {"sensor":{"RBIAS":"R1","RREF":"R2","RFB":"R3","RREF_LOW":"R4","RFB_HYST":"R5"},
        "nmos":{"RLOAD":"R1","RPD":"R2"},"npn":{"RLOAD":"R1","RB":"R2","RPD":"R3"}}
for family,mapping in maps.items():
    controls = {c["label"]:c["options"] for c in read(family+"-public-controls.json")}
    assert set(controls) == {"Select component","Probe terminal"}
    components = {o["value"]:o["label"] for o in controls["Select component"]}
    probes = {o["value"]:o["label"] for o in controls["Probe terminal"]}
    for identity,mark in mapping.items():
        assert components[identity] == mark
        for terminal in ("1","2"): assert probes[identity+"."+terminal] == mark+" terminal "+terminal
for family in plan["replays"]:
    assert "Customer retest did not pass" in text(family+"-original-fail-body.txt")
assert "State: Lead Lifted" in text("sensor-r1-lifted-body.txt")
assert "Part 6 - Color bands" in text("sensor-r1-install-options-body.txt")
assert "Install as Circuit / R1" in text("sensor-r1-install-options-body.txt")
assert "Lead 1: R5.1" in text("sensor-r5-service-body.txt")
assert "Customer retest did not pass" in text("sensor-original-restored-retest-observed-body.txt")
for family,marks in (("sensor",("R1",)),("nmos",("R1",)),("npn",("R1","R2"))):
    for mark in marks:
        body = text(family+"-"+mark.lower()+"-service-body.txt")
        assert "State: Installed" in body and "Lead 1: "+mark+".1" in body and "Lead 2: "+mark+".2" in body
        assert "Install as Circuit / "+mark in text(family+"-"+mark.lower()+"-install-options-body.txt")
for name in ("sensor-final-off-body.txt","nmos-restored-final-off-body.txt","npn-final-off-body.txt"):
    assert "Board Power: OFF" in text(name) and "0 parts" in text(name)
for family,voltages in (("nmos",("+5V","+5V")),("npn",("+9V","+5V"))):
    name = "nmos-restored-final-off-body.txt" if family == "nmos" else "npn-final-off-body.txt"
    assert all("Supply "+str(i)+" ("+v+"): disconnected" in text(name) for i,v in enumerate(voltages,1))
assert "State: Installed" in text("led-original-installed-off-body.txt")
assert "\nOL\n" in text("led-original-isolated-ohm-body.txt")
assert "\n330 Ohm\n" in text("led-replacement-330-ohm-body.txt")
assert "The live board passed the customer" in text("led-customer-passed-body.txt")
assert "Return to board" in text("led-customer-passed-body.txt")
assert "Supply 1 (+5V): disconnected" in text("led-final-off-body.txt") and "1 part" in text("led-final-off-body.txt")
cleanup = read("process-resource-cleanup.json")
assert cleanup["status"] == "PASS" and cleanup["recordedBirths"] == 16
assert all(row["recordedBirthExited"] is True for row in cleanup["checks"])
assert all(cleanup[k] is True for k in ("managedStateAbsent","port8907Released","browserApiExit0","currentProfileAbsent"))
events = read("player-events.json")
failed = [e for e in events if e["status"] != "PASS"]
assert len(events) == 44 and len(failed) == 1 and failed[0]["name"] == "Accept Sensor ticket and fit bench"
assert len(read("player-preparation-issues.json")["issues"]) == 5
logs = read("player-log-hashes.json")
assert logs["errors"] == {"page":[],"request":[],"response":[]}
assert logs["consoleEntries"] == 459 and logs["consoleTypes"] == {"error":2,"log":457}
frames = plan["primaryImages"]
assert len(frames) == 5 and all((HERE/n).read_bytes().startswith(bytes.fromhex("ffd8")) for n in frames)
result = {"status":"PASS_WITH_RETAINED_PREPARATION_FAILURES","identities":identities,
    "sourceAndServed":"1482 source/24 machinery/gates and independent compiled/served six fields exact before/after",
    "publicControls":"Literal stable values retain correct R1-R5/R1-R2/R1-R3 component and terminal labels; Sensor optional R5 service observed",
    "service":"Sensor original Part6 lead1 lifted/removed/reinstalled, original-restored customerFAIL, finalOFF/tray0; MOSFET original Part1 and NPN primary load Part1/base Part2 removed/reinstalled with Circuit R1/R2 captions, all suppliesOFF/tray0",
    "repair":"Known LED original Installed customerFAIL, allOFF isolated original physicalOHM/public probes OL, Shop Part4 330Ohm installed, actualOFF330Ohm, exit mode/connect5V/fresh customerPASS modal, finalOFF/tray1",
    "visual":"Root inspected five raw1280x900 primary JPEGs; three actual board images pasted in chat",
    "images":{n:{"sha256":sha(n),"bytes":(HERE/n).stat().st_size} for n in frames},
    "postedInChat":["sensor-fitted.jpg","nmos-fitted.jpg","npn-r2-service.jpg"],
    "actions":{"logged":44,"pass":43,"failedPreparation":failed,"additionalPreparationFailures":"Three direct browser locator timeouts plus one pre-navigation helper parse failure; all five issues explicit in player-preparation-issues.json"},
    "console":"459 entries=457 log+2 unattributed resource404; raw private/hash-bound, page/request/observed-response errors0; no clean-console attribution",
    "cleanup":"All16 recorded births exited, browserAPI0/profile absent, maintained stop1.8229243s, managed state absent/8907 released; older blocked profiles preserved",
    "limits":plan["limits"]}
(HERE/"player-assessment.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
print("PASS actual player/provenance/controls/service/repair/cleanup assessment; preparation failures retained")
