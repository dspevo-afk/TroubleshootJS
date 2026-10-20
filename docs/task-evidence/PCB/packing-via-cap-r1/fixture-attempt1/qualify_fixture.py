"""Accept only real final-source native fixture and separately observed cleanup."""
import hashlib,json,math,re,runpy
from pathlib import Path
if not __debug__:raise RuntimeError("Assertion-enabled Python required")
HERE=Path(__file__).resolve().parent
def read(n):return json.loads((HERE/n).read_text(encoding="utf-8-sig"))
def text(n):return (HERE/n).read_text(encoding="utf-8-sig")
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert runpy.run_path(str(HERE/"inputs.py"))["record"]()==read("candidate-inputs.json")
t=read("fixture-timing.json");c=read("fixture-process-cleanup.json");l=read("fixture-process-launch.json")
assert t["status"]=="PASS" and type(t["exitCode"]) is int and t["exitCode"]==0 and t["failure"] is None and t["terminalCleanupIncluded"] is True
assert type(t["elapsedSeconds"]) in (int,float) and math.isfinite(t["elapsedSeconds"]) and t["elapsedSeconds"]>0
assert c["status"]=="PASS" and type(c["actualExecExitCode"]) is int and c["actualExecExitCode"]==0 and c["launchSha256"]==sha(HERE/"fixture-process-launch.json")
assert len(l["drivers"])==len(c["checks"])==2 and all(x["recordedBirthExited"] is True for x in c["checks"])
assert [{k:x[k] for k in ("pid","startTicks","executable")} for x in c["checks"]]==l["drivers"]
for side in ("before","after"):assert text("fixture-input-audit-"+side+".txt").strip()=="PASS packing via-cap exact inputs phase=candidate sources=1482 gates=5"
log=text("fixture.txt");receipt=text("fixture-receipt.txt")
assert "CURRENT_CONTRACT_FAILURE:" not in log and "CURRENT_CONTRACT_CLEANUP:" not in log
assert log.count("PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.")==1
assert len(re.findall(r"(?m)^PASS: P07 two-layer contracts ",receipt))==1
name="tests/contracts/P07TwoLayerContractTest.java"
old=text("baseline-source/"+name);new=text("candidate-source/"+name)
assert set(re.findall(r'"(?:\\.|[^"\\])*"',old))<=set(re.findall(r'"(?:\\.|[^"\\])*"',new))
(HERE/"fixture-assessment.json").write_text(json.dumps({"status":"PASS_ACTUAL_VIA_CAP_FIXTURE","elapsedSeconds":t["elapsedSeconds"],"sources":1482,"limits":"Actual maintained P07 fixture/all declared test compilation only; root source review and exact target GO decision/final full gates separate"},indent=2)+"\n")
print("PASS actual via-cap fixture and separate root birth cleanup")
