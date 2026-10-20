"""Read finite final player evidence; root inspects the actual screenshots."""
import hashlib,json,re,runpy
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
def read(n):return json.loads((HERE/n).read_text(encoding="utf-8-sig"))
def text(n):return (HERE/n).read_text(encoding="utf-8-sig")
def sha(n):return hashlib.sha256((HERE/n).read_bytes()).hexdigest()
def contains(n,*values):
    body=text(n);assert all(v in body for v in values),(n,values)
assert runpy.run_path(str(HERE/"player_inputs.py"))["record"]()==read("player-inputs.json")
execution=read("player-expected-execution.json")
assert execution==read("post-player-provenance.json")
attrs={"identity-protocol":"Protocol","source-digest":"SourceDigest","script-digest":"ScriptDigest","web-digest":"WebDigest","execution-digest":"Digest","file-count":"FileCount"}
for side in ("before","after"):
    served=read("player-served-"+side+".json")
    assert set(served)=={"data-tsj-preview-"+k for k in attrs}
    assert all(served["data-tsj-preview-"+k]==str(execution[v]) for k,v in attrs.items())
    assert text("player-input-audit-"+side+".txt").strip()=="PASS final packing via-cap player source1482 machinery24 helpers6"
    assert read("preview-owner-"+side+".json")["status"]=="PASS"
plan=read("player-plan.json")
for family in ("led","multirail"):
    identity=read(family+"-public-identity.json")
    assert identity["replay"]==plan["replays"][family]
    assert re.fullmatch(r"tsj-alpha/6 / [A-F0-9]{32}",identity["build"])
assert read("led-public-identity.json")["build"]==read("multirail-public-identity.json")["build"]
for family, expected in plan["generatedFamilies"].items():
    identity=read(family+"-public-identity.json")
    fields=identity["replay"].split("/")
    assert len(fields)==5 and fields[:4]==["tsj-alpha","6",expected["profile"],expected["family"]]
    assert str(int(fields[4]))==fields[4] and -(2**63)<=int(fields[4])<2**63
    assert identity["build"]==read("led-public-identity.json")["build"]
    contains(family+"-original-fail-body.txt","Customer retest did not pass")
    contains(family+"-final-off-body.txt","Board Power: OFF","0 parts")
    supplies=read(family+"-disconnected-supplies.json")
    assert len(supplies)>0 and all(x["disconnectedObserved"] is True for x in supplies)

contains("multirail-original-fail-body.txt","Customer retest did not pass")
contains("multirail-final-off-body.txt","Board Power: OFF","0 parts")
supplies=read("multirail-disconnected-supplies.json")
assert len(supplies)==4 and all(x["disconnectedObserved"] is True for x in supplies)
contains("led-original-fail-body.txt","Customer retest did not pass")
contains("led-isolated-original-ohm-body.txt","\nOL\n","Board Power: OFF","R1 terminal 1","R1 terminal 2")
contains("led-replacement-330-ohm-body.txt","\n330 Ohm\n","Board Power: OFF")
contains("led-customer-passed-body.txt","FUNCTION VERIFIED","Return to board","Customer retest passed")
contains("led-final-off-body.txt","Supply 1 (+5V): disconnected","Board Power: OFF","1 part")
bodies=list(HERE.glob('*-body.txt'));assert len(bodies)>=10
for body in bodies:
    value=body.read_text(encoding="utf-8-sig")
    assert all(token not in value for token in ("metricEvidence","BASELINE_UNAVAILABLE","CANDIDATE_UNAVAILABLE","protectedVoid","5016608","SENSOR_A_OPEN","DREV_OPEN")),body.name
events=read("player-events.json");assert events and all(x["status"] in ("PASS","FAIL") for x in events)
logs=read("player-log-hashes.json")
assert logs["errors"]=={"page":[],"request":[],"response":[]}
cleanup=read("process-resource-cleanup.json")
assert cleanup["status"]=="PASS" and cleanup["actualPreviewStopExecExitCode"]==0
assert len(cleanup["checks"])==cleanup["recordedBirths"] and all(x["recordedBirthExited"] is True for x in cleanup["checks"])
assert all(cleanup[k] is True for k in ("managedStateAbsent","port8907Released","browserApiExit0","currentProfileAbsent"))
frames=plan["primaryImages"]
assert len(frames)==5 and all((HERE/n).read_bytes().startswith(bytes.fromhex('ffd8')) for n in frames)
visual=read("root-visual-review.json")
assert visual["status"]=="PASS" and visual["images"]=={n:sha(n) for n in frames}
result={"status":"PASS_FINITE_FINAL_PLAYER","candidateSha256":sha("final/candidate-inputs.json"),"identities":{f:read(f+'-public-identity.json') for f in ('led','multirail','rc','composed')},"events":len(events),"failedObservers":sum(x['status']=='FAIL' for x in events),"images":{n:{"sha256":sha(n),"bytes":(HERE/n).stat().st_size} for n in frames},"publicBodies":len(bodies),"diagnosticPrivacyTokensAbsent":True,"sourceAndServedProvenanceBeforeAfterExact":True,"limits":"Finite two exact replays plus first actual generated RC/composed and known LED diagnosis-to-freshPASS and finalOFF only; source/gates/native44+active220 separately bound. Console details and every failed observer retained; no all-seed/manufacturing/performance/Edge/CDP/FileReader/full-suite claim"}
(HERE/'player-assessment.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS final visible player/privacy/provenance/repair/cleanup')
