"""Root assessment of recorded visible input; premature captures do not certify results."""
import hashlib, json
from pathlib import Path
if not __debug__: raise RuntimeError("Assertion-enabled Python required")
HERE = Path(__file__).resolve().parent
def read(n): return json.loads((HERE/n).read_text(encoding="utf-8-sig"))
def text(n): return (HERE/n).read_text(encoding="utf-8-sig")
def sha(n): return hashlib.sha256((HERE/n).read_bytes()).hexdigest()
expected = read("player-expected-execution.json")
attrs = {"identity-protocol":"Protocol", "source-digest":"SourceDigest",
         "script-digest":"ScriptDigest", "web-digest":"WebDigest",
         "execution-digest":"Digest", "file-count":"FileCount"}
for name in ("player-served-before.json", "player-served-after.json"):
    actual = read(name)
    assert len(actual) == 6 and all(actual["data-tsj-preview-"+a] == str(expected[k]) for a,k in attrs.items())
assert expected == read("post-player-provenance.json")
assert read("rb30-public-identity.json")["replay"] == "tsj-alpha/6/MEDIUM/RB30_CONTROL/0"
assert read("led-public-identity.json")["replay"] == "tsj-alpha/6/EASY/LED_INDICATOR/5577758334702827054"
assert "State: Lead Lifted" in text("rb30-j1-lifted-body.txt")
assert "Part 1 - 12 V supply" in text("rb30-j1-loose-selected-body.txt")
assert "Board Power: OFF" in text("rb30-final-off-body.txt")
assert all("Supply "+str(i)+" ("+v+"): disconnected" in text("rb30-final-off-body.txt")
           for i,v in ((1,"+12V"),(2,"+5V"),(3,"+5V"),(4,"+12V")))
assert "Customer retest did not pass" in text("rb30-original-restored-retest-observed-body.txt")
assert "0 parts" in text("rb30-final-off-body.txt")
# The immediate post-click capture is NOT a completed result. The later public
# original-part menu records FAIL and Installed before the next lift action.
assert "Customer retest did not pass" not in text("led-initial-retest-body.txt")
assert "Customer retest did not pass" in text("led-r1-original-body.txt")
assert "State: Installed" in text("led-r1-original-body.txt")
assert "Board Power: OFF" in text("led-r1-original-body.txt")
assert "\nOL\n" in text("led-original-isolated-ohm-confirmed-body.txt")
assert "\n330 Ohm\n" in text("led-replacement-330-ohm-body.txt")
assert "The live board passed the customer" in text("led-customer-passed-body.txt")
assert "Return to board" in text("led-customer-passed-body.txt")
assert "Board Power: OFF" in text("led-final-off-body.txt") and "1 part" in text("led-final-off-body.txt")
assert read("process-resource-cleanup.json")["recordedBirths"] == 12
assert read("process-resource-cleanup.json")["status"] == "PASS"
frames = ["rb30-fitted.jpg", "rb30-connector-inspection.jpg", "rb30-j1-loose-tray.jpg",
          "led-fitted.jpg", "led-customer-passed.jpg"]
for name in frames: assert (HERE/name).read_bytes().startswith(bytes.fromhex("ffd8"))
result = {
    "status":"PASS_WITH_RETAINED_TOOLING_LIMITS",
    "identities":[read("rb30-public-identity.json"),read("led-public-identity.json")],
    "servedAndIndependentSixFieldsExact":True,
    "native":"Native9/census44/actual GWT5 separate exact source-bound PASS",
    "visual":"Root inspected five primary1280x900 JPEGs; plastic divider clears contacts in installed mirrored input/output and clockwise loose input; separate bottom-face diagnostic has no housing",
    "service":"RB30 all4OFF, original J1 Part1 lifted/removed/reinstalled, tray0; all4 reconnected for retest and original complaint observed again; all4OFF before leaving",
    "repair":"Known LED original Installed FAIL observed before lift; allOFF isolatedR1 physicalOHM/public probes OL; actual Shop330Ohm Part4 installed; actualOFF330Ohm; exit mode/connect5V/fresh actual customerPASS modal; finalOFF/tray1",
    "primaryImages":{n:{"sha256":sha(n),"bytes":(HERE/n).stat().st_size} for n in frames},
    "postedInChat":["rb30-fitted.jpg","rb30-connector-inspection.jpg","led-fitted.jpg"],
    "retainedPreparationIssues":[
        "Owner raw exe path comparison failed before navigation; normalized same executable, birth and unique profile validated",
        "Evidence helper closure declared before page binding failed on body capture after successful navigation; rebound, no kernel reset",
        "First30s RB30 ticket wait NOT_READY at65pct/49s; same case completed later, no substitution or generation-budget change",
        "First loose image showed menu only before tray hover; retained separately, later actual physical tray inspected",
        "First physical OHM click with popup open did not select mode; no OL claimed, screenshot retained; explicit popup close then real mode selection gave OL",
        "Existing stale Select a meter mode first panel feedback remained after successful placements; OL/330Ohm and actual customerPASS separately proven",
        "First root reader rejected premature post-retest capture without FAIL; later existing original-Installed pre-lift capture proves initial FAIL; premature frame and rejection retained"],
    "console":"1008 entries:1006 informational log and2 unattributed resource404 errors; no page/request/observed-response errors; raw private/hash-bound; no clean-console/favicon claim",
    "cleanup":"All12 recorded births exited; browserAPI exit0/current profile absent; maintained preview stop1.8466316s/state absent/8907 released; three earlier blocked profiles untouched",
    "limits":"Finite fixed11family/four-root sample, exact replays and actual Brave fallback; no all-seed, new-size, manufacturing, Edge/CDP wrapper, new FileReader or full unrelated matrix claim"}
(HERE/"player-assessment.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
print("PASS root actual player/body/image/provenance/cleanup assessment with explicit limits")
