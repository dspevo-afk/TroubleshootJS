"""Root pre-runtime scope review; static checks do not prove native behavior."""
import ast
import difflib
import hashlib
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
COPY = Path(json.loads((HERE/'local-state.json').read_text())['scratch'])
BASE = json.loads((HERE/'copy-inputs.json').read_text())
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
old = {r['path']: r['sha256'] for r in BASE['files']}
expected = {
    'scripts/verify-current-contracts.ps1',
    *('src/com/lushprojects/circuitjs1/client/'+n+'.java' for n in (
        'GenerationRequest', 'MediumBoardNormalAdmission', 'PcbPassivePosePolicy',
        'PcbPlacementConstraints', 'PcbPlacementPlanner', 'PcbTwoLayerRules', 'Rb56Placement')),
    *('tests/contracts/'+n+'.java' for n in (
        'PcbPassivePoseAdmissionContractTest', 'PcbPassivePosePolicyContractTest',
        'PcbPassivePoseTrialSourceContractTest', 'PcbPostSelectionEdgeTrimContractTest'))}
additions = {'tests/contracts/'+n[0]+'.java' for n in
    json.loads((HERE/'unit-suites.json').read_text())[:4]+
    json.loads((HERE/'route-suites.json').read_text())}
actual = {p.relative_to(COPY).as_posix(): sha(p) for p in COPY.rglob('*') if p.is_file()}
assert len(actual) == 1510 and set(actual) == set(old)|additions
assert {p for p in old if actual[p] != old[p]} == expected

def base(path):
    p = HERE.parent/'passive-pose-policy-r1/source'/path
    if not p.is_file(): p = ROOT/path
    assert sha(p) == old[path]
    return p.read_bytes()

def text(path):
    return (COPY/path).read_text()

# The maintained physical/routing verifier body is byte-exact after the six
# explicitly declared wrapper migrations; no physical, whitespace or work gate changes.
tree = ast.parse((HERE/'prepare_fixtures.py').read_text())
changes = next(ast.literal_eval(n.value) for n in tree.body
               if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'changes' for t in n.targets))
adapter = 'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
data = (COPY/adapter).read_bytes()
for before, after in reversed(changes):
    assert data.count(after) == 1
    data = data.replace(after, before)
assert data == base(adapter)
trial = 'tests/contracts/PcbPassivePoseTrialSourceContractTest.java'
data = (COPY/trial).read_bytes()
for before, after in [(b'Rb56Plan.resolve(42L, 17)', b'Rb56Plan.resolve(42L, 18)'),
                      (b'unknown RB56 interpretation17 rejects', b'unknown RB56 interpretation18 rejects')]:
    assert data.count(after) == 1
    data = data.replace(after, before)
assert data == base(trial)

registry = 'scripts/verify-current-contracts.ps1'
data = (COPY/registry).read_bytes()
nl = b'\r\n' if b'\r\n' in data else b'\n'
rows = json.loads((HERE/'unit-suites.json').read_text())[:4]+json.loads((HERE/'route-suites.json').read_text())
for name, marker in rows:
    entry = ("        @{ Name = '%s'; Marker = '%s' }," % (name, marker)).encode()
    assert data.count(nl+entry) == 1
    data = data.replace(nl+entry, b'')
assert data == base(registry)
registry_rows = re.findall(r"^\s*@\{ Name = '([^']+)'; Marker = '([^']*)' \},?\s*$",
                           text(registry), re.MULTILINE)
for manifest in ('unit-suites.json', 'route-suites.json'):
    declared = json.loads((HERE/manifest).read_text())
    selected = {row[0] for row in declared}
    actual_order = [[name, marker] for name, marker in registry_rows if name in selected]
    assert actual_order == declared, 'Declared suites must match maintained registry execution order'

planner = 'src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java'
data = text(planner)
edits = [
    ('boolean passivePoseSizing=constraints.passivePosePolicyVersion==\n                PcbPassivePosePolicy.ORTHOGONAL &&',
     'boolean passivePoseSizing=(constraints.passivePosePolicyVersion==\n                PcbPassivePosePolicy.ORTHOGONAL || constraints.passivePosePolicyVersion==\n                PcbPassivePosePolicy.SIZING_ONLY) &&'),
    ('(long)ring*count+index);', 'constraints.passivePosePolicyVersion,(long)ring*count+index);'),
    ('PcbPassivePosePolicy.forVisit(item.footprint,passivePoseAlternative,visit);',
     'PcbPassivePosePolicy.forVisit(item.footprint,passivePoseAlternative,\n                        constraints.passivePosePolicyVersion,visit);')]
for before, after in reversed(edits):
    assert data.count(after) == 1
    data = data.replace(after, before)
assert data == base(planner).decode().replace('\r\n', '\n')
callers = []
for p in (COPY/'src').rglob('*.java'):
    if 'PcbPassivePosePolicy.forVisit(' in p.read_text(): callers.append(p.relative_to(COPY).as_posix())
assert callers == [planner]

# Reject late source/canary syntax and whitespace mistakes before sealing bytes.
for p in sorted(HERE.glob('*.py')):
    ast.parse(p.read_text(encoding='utf-8-sig'), filename=p.name)
for path in expected|additions:
    lines = (COPY/path).read_text().splitlines()
    assert not any(line.rstrip() != line for line in lines), path

diff = []
for path in sorted(expected):
    a = base(path).decode().splitlines(keepends=False)
    b = text(path).splitlines(keepends=False)
    diff.extend(difflib.unified_diff([s+'\n' for s in a], [s+'\n' for s in b],
                                   fromfile=path, tofile=path, n=3))
(HERE/'source-review.diff').write_text(''.join(diff))
plan = {'rootReviewed': True, 'baseCopyCount': 1504, 'copyCount': 1510,
        'overrides': sorted(expected), 'additions': sorted(additions)}
(HERE/'source-plan.json').write_text(json.dumps(plan, indent=2)+'\n')
report = {'status': 'PASS', 'scope': 'Root static integrated source review before native execution',
          'copyCount': len(actual), 'overrides': len(expected), 'additions': len(additions),
          'physicalCoreByteExactAfterSixWrapperMigrations': True,
          'oldSourceFixtureByteExactExceptUnsupported18': True,
          'registryByteExactExceptSixEntries': True,
          'declaredSuitesMatchMaintainedRegistryOrder': True,
          'plannerByteExactExceptPolicy2GateAndTwoPolicyArguments': True,
          'productionVisitCallers': callers,
          'diffSha256': sha(HERE/'source-review.diff'),
          'behaviorProof': 'NOT RUN; fresh native fixtures and six routes required'}
(HERE/'source-review.json').write_text(json.dumps(report, indent=2)+'\n')
print('PASS static1510 / 12 overrides / 6 additions; physical core and work bounds exact')
