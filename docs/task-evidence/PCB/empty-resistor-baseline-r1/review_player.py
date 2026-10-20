"""Certify the unchanged pre-edit normal removal observation at its actual boundary."""
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
    assert text("player-input-audit-"+side+".txt").strip()=="PASS normal empty-resistor baseline exact1482 machinery24 gates5 compiled6 reuse"
    assert read("preview-owner-"+side+".json")["status"]=="PASS"
identity=read("public-identity.json")
assert identity=={"replay":"tsj-alpha/6/EASY/LED_INDICATOR/5577758334702827054","build":"tsj-alpha/6 / 47CB6E7FB2C6397086F05F5DFA2345EB"}
assert "Customer retest did not pass" in text("original-fail-body.txt")
assert "State: Installed" in text("r1-installed-off-body.txt") and "Board Power: OFF" in text("r1-installed-off-body.txt")
assert "State: Lead Lifted" in text("r1-lifted-off-body.txt")
assert "1 part" in text("r1-empty-top-body.txt") and "Board Power: OFF" in text("r1-empty-top-body.txt")
assert "Part 2 - Color bands" in text("original-tray-body.txt")
assert "Install as Circuit / R1" in text("original-install-options-body.txt")
assert "State: Installed" in text("original-restored-installed-body.txt") and "0 parts" in text("original-restored-installed-body.txt")
assert "Customer retest did not pass" in text("original-restored-fail-observed-body.txt")
assert "Supply 1 (+5V): disconnected" in text("final-off-body.txt") and "Board Power: OFF" in text("final-off-body.txt") and "0 parts" in text("final-off-body.txt")
events=read("player-events.json")
assert len(events)==11 and all(e["status"]=="PASS" for e in events)
logs=read("player-log-hashes.json")
assert logs["consoleEntries"]==45 and logs["consoleTypes"]=={"error":1,"log":44}
assert logs["errors"]=={"page":[],"request":[],"response":[]}
cleanup=read("process-resource-cleanup.json")
assert cleanup["status"]=="PASS" and cleanup["recordedBirths"]==6 and cleanup["actualPreviewStopExecExitCode"]==0
assert all(c["recordedBirthExited"] is True for c in cleanup["checks"])
assert all(cleanup[k] is True for k in ("managedStateAbsent","port8907Released","browserApiExit0","currentProfileAbsent"))
frames=["baseline-installed-r1.jpg","baseline-empty-r1-top.jpg"]
assert all((HERE/n).read_bytes().startswith(bytes.fromhex("ffd8")) for n in frames)
result={"status":"PASS_PRE_EDIT_BASELINE","baseCommit":"891d6ce75ff97fd62adc98fc88b331230cf210f1",
        "identity":identity,"inputSealSha256":sha("inputs.json"),"sourceBoundary":"Exact unchanged1482/24/gates5 and independent compiled/served6 before/after; no new native/build/matrix run",
        "input":"Actual normal replay/ticket/view/native-select/component/tray/supply/retest only;11 actions allPASS, no controller/DOM mutation/force",
        "observed":"Root inspected two raw1280x900 views: installed R1 body, then actual empty top-side R1 region with only its reference and two lands; no assembly body outline is present",
        "originalRestore":"Sole5V OFF before lead1 lift/removal; actual original Part2 returned to Circuit R1, Installed/tray0 and original complaint observed on repeated retest; final5V disconnected/BoardOFF/tray0",
        "images":{n:{"sha256":sha(n),"bytes":(HERE/n).stat().st_size} for n in frames},
        "finding":"A small unfilled rounded body guide within the immutable axial body is a warranted scoped presentation candidate; preserve chip-only helper/null fixture and all package/identity/geometry/physics policies",
        "implementation":"NOT SELECTED_OR_EDITED by this baseline; subsequent production scope/raw source must be frozen before edits",
        "console":"45 entries=44log+1unattributed resource404; raw private/hash-bound, page/request/observed-response errors0; no clean-console attribution",
        "cleanup":"All6 recorded births exited; API browser0/current profile absent; maintained stop1.8122673s/state absent/8907 released; older blocked profiles/private resources retained",
        "limits":"Single exact normal removal observation, no measurement/repair/manufacturing/new-size/all-seed/Edge/CDP/FileReader/full-matrix claim; this baseline is pre-edit proof, not final-candidate acceptance"}
(HERE/"baseline-assessment.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
print("PASS actual pre-edit empty axial baseline/source/provenance/original restoration/cleanup")
