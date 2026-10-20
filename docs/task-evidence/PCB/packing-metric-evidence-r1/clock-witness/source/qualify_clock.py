"""Reject any replay difference outside the observed wall-clock field."""
import hashlib,json,re,runpy
from pathlib import Path
HERE=Path(__file__).resolve().parent
ATTEMPT=HERE/"clock-witness"
def read(n):return json.loads((ATTEMPT/n).read_text(encoding="utf-8-sig"))
assert __debug__
assert runpy.run_path(str(HERE/"clock_inputs.py"))["record"]()==read("inputs.json")
t=read("timing.json");c=read("process-cleanup.json");l=read("process-launch.json")
assert t["status"]=="PASS" and type(t["exitCode"]) is int and t["exitCode"]==0 and t["failure"] is None
assert c["status"]=="PASS" and type(c["actualExecExitCode"]) is int and c["actualExecExitCode"]==0
assert c["launchSha256"]==hashlib.sha256((ATTEMPT/"process-launch.json").read_bytes()).hexdigest()
assert len(l["drivers"])==len(c["checks"])==2 and all(x["recordedBirthExited"] is True for x in c["checks"])
assert [{k:x[k] for k in ("pid","startTicks","executable")} for x in c["checks"]]==l["drivers"]
rows={}
for line in (ATTEMPT/"receipt.txt").read_text(encoding="utf-8-sig").splitlines():
    if line.startswith("PCB_CLOCK_WITNESS "):
        _,seed,repeat,geometry,canonical=line.split(" ",4)
        assert (seed,repeat) not in rows;rows[seed,repeat]=(geometry,canonical)
assert set(rows)=={(s,r) for s in ("10","0") for r in ("0","1")}
pattern=r"(normal-packing@2;attempts=[01];orderings=\d+;expansions=\d+;millis=)([0-9]+)(;route=)"
result={}
for seed in ("10","0"):
    a,b=rows[seed,"0"],rows[seed,"1"]
    assert a[0]==b[0] and re.fullmatch('[a-f0-9]{64}',a[0])
    observations=[];normalized=[]
    for _,value in (a,b):
        matches=list(re.finditer(pattern,value));assert len(matches)==1
        observations.append(int(matches[0][2]));normalized.append(re.sub(pattern,r"\1[wall-clock]\3",value))
    assert normalized[0]==normalized[1],"Non-clock canonical replay difference"
    result[seed]={"geometrySha256":a[0],"packingMillis":observations,"fullCanonicalEqual":a[1]==b[1],"onlyNamespacedPackingClockMayDiffer":True}
assert not result["10"]["fullCanonicalEqual"],"Witness did not reproduce suspected seed10 clock difference"
(ATTEMPT/"assessment.json").write_text(json.dumps({"status":"PASS_ONLY_PACKING_CLOCK_REPLAY_DIFFERENCE","elapsedSeconds":t["elapsedSeconds"],"roots":result,"limits":"Actual task-only same-owner observations, four accepted routes. No production/test oracle change; existing failed contract remains FAIL."},indent=2)+"\n")
print('PASS exact geometry/work/canonical except only observed packing wall clock')
