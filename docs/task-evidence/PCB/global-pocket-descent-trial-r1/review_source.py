"""Generate exact source deltas and prove the per-board witness core unchanged."""
import difflib
import hashlib
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
COPY = Path(json.loads((HERE/'local-state.json').read_text())['scratch'])
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
audit = json.loads((HERE/"source-audit.json").read_text())
assert audit["status"] == "PASS" and len(audit["files"]) == 12
for name, digest in audit["files"].items():
    assert sha(ROOT/name) == digest, name
main = json.loads((ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json').read_text())['files']
base = {r['path']:r['sha256'] for r in main if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')}
overrides = ['src/com/lushprojects/circuitjs1/client/'+n+'.java' for n in
    ('Rb56Placement','PcbPlacementConstraints','PcbPlacementPlanner','MediumBoardPhysicalPolicy','SeededPcbLayoutGenerator','PlayerSessionReplay')]
overrides += ['tests/contracts/PcbPostSelectionEdgeTrimContractTest.java','scripts/verify-current-contracts.ps1']
additions = ['tests/contracts/PcbGlobalPocketDescentContractTest.java',
             'tests/contracts/PcbMediumPlacementCostContractTest.java',
             'tests/contracts/PcbGlobalPocketDescentTrialContractTest.java']
additions += ['tests/contracts/PcbGlobalPocketDescentTrialContractTestGroup'+str(i)+'.java' for i in range(2,8)]
actual = {p.relative_to(COPY).as_posix():sha(p) for p in COPY.rglob('*') if p.is_file()}
assert set(actual) == set(base)|set(additions) and len(actual) == 1476
assert {n for n in base if actual[n] != base[n]} == set(overrides)
helper = 'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
before = (ROOT/helper).read_bytes().decode('utf-8')
after = (COPY/helper).read_bytes().decode('utf-8')
constructor = '    private PcbPostSelectionEdgeTrimContractTest() { }'
start = after.index(constructor)+len(constructor)
end = after.index('    public static void main(String[] args)', start)
before_start = before.index(constructor)+len(constructor)
before_end = before.index('    public static void main(String[] args)', before_start)
assert before[before_start:before_end].strip() == ''
assert 'static String observeGlobalPocketDescent' in after[start:end]
reconstructed = after[:start]+before[before_start:before_end]+after[end:]
reconstructed = reconstructed.replace(
    'String label = version == 22 ? "global22" : version == SAVED_VERSION ? "saved14" : "trial15";',
    'String label = version == SAVED_VERSION ? "saved14" : "trial15";')
reconstructed = reconstructed.replace(
    'check(version == SAVED_VERSION || version == TRIAL_VERSION || version == 22,',
    'check(version == SAVED_VERSION || version == TRIAL_VERSION,')
assert reconstructed == before, 'existing per-board physical core changed'
policy = 'src/com/lushprojects/circuitjs1/client/MediumBoardPhysicalPolicy.java'
before_policy = (ROOT/policy).read_bytes().decode('utf-8')
after_policy = (COPY/policy).read_bytes().decode('utf-8')
boundary = '    private static PlacementScore score('
assert before_policy.count(boundary) == after_policy.count(boundary) == 1
prefix = before_policy[:before_policy.index(boundary)]
assert after_policy.startswith(prefix)
new_adapter = after_policy[len(prefix):after_policy.index(boundary)]
assert new_adapter.count('static PlacementScore scorePlan(') == 1
assert prefix+after_policy[after_policy.index(boundary):] == before_policy, 'existing score owner changed outside adapter'
registry = 'scripts/verify-current-contracts.ps1'
before = (ROOT/registry).read_bytes().decode('utf-8')
after = (COPY/registry).read_bytes().decode('utf-8')
new_classes = {Path(n).stem for n in additions}
erased = ''.join(line for line in after.splitlines(keepends=True)
    if not any("Name = '"+name+"'" in line for name in new_classes))
assert erased == before, 'maintained verifier changed outside nine registrations'
diff = []
for name in sorted(overrides+additions):
    a = (ROOT/name).read_text().splitlines(keepends=True) if name in base else []
    b = (COPY/name).read_text().splitlines(keepends=True)
    diff.extend(difflib.unified_diff(a,b,fromfile='accepted/'+name,tofile='trial/'+name,n=0))
(HERE/'source-review.diff').write_text(''.join(diff))
(HERE/'source-review.json').write_text(json.dumps({
    'status':'PASS','copyCount':1476,'overrides':{n:actual[n] for n in sorted(overrides)},
    'additions':{n:actual[n] for n in sorted(additions)},'existingPhysicalWitnessCoreExact':True,
    'verifierUnchangedOutsideRegistrations':True,'existingGlobalCostOwnerExactOutsideAdapter':True,'diffSha256':sha(HERE/'source-review.diff')},indent=2)+'\n')
print('PASS eight overrides/nine additions; physical witness core and verifier reconstruct exact')
