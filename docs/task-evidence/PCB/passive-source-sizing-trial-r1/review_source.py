"""Root scope checks and normalized diff; behavior still requires fresh native gates."""
import ast
import difflib
import hashlib
import json
import re
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
CONTROL = HERE.parent/'passive-sizing-control-r1'
POLICY = HERE.parent/'passive-pose-policy-r1'
COPY = Path(json.loads((HERE/'local-state.json').read_text())['scratch'])
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
base_map = {r['path']: r['sha256'] for r in read(HERE/'copy-inputs.json')['files']}
core = ('GenerationRequest', 'MediumBoardNormalAdmission', 'PcbPassivePosePolicy',
        'PcbPlacementPlanner', 'PcbTwoLayerRules', 'Rb56Placement')
overrides = {'src/com/lushprojects/circuitjs1/client/'+n+'.java' for n in core} | {
    'tests/contracts/'+n+'.java' for n in ('PcbPassivePoseAdmissionContractTest',
    'PcbPassivePosePolicyContractTest', 'PcbPassivePoseTrialSourceContractTest',
    'PcbPassiveSizingSourceContractTest', 'PcbPostSelectionEdgeTrimContractTest')} | {
    'scripts/verify-current-contracts.ps1'}
new = read(HERE/'new-suites.json')
additions = {'tests/contracts/'+row[0]+'.java' for row in new['unit']+new['routes']}
actual = {p.relative_to(COPY).as_posix(): sha(p) for p in COPY.rglob('*') if p.is_file()}
assert len(actual) == 1528 and set(actual) == set(base_map)|additions
assert {name for name in base_map if actual[name] != base_map[name]} == overrides
def before(name):
    p = next((p for p in (CONTROL/'source'/name, POLICY/'source'/name, ROOT/name)
              if p.is_file() and sha(p) == base_map[name]), None)
    assert p is not None, name
    return p.read_bytes()
tree = ast.parse((HERE/'prepare_fixtures.py').read_text())
changes = next(ast.literal_eval(n.value) for n in tree.body if isinstance(n, ast.Assign)
               and any(isinstance(t, ast.Name) and t.id == 'changes' for t in n.targets))
adapter = 'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
data = (COPY/adapter).read_bytes()
assert len(changes) == 6
for old, new_text in reversed(changes):
    assert data.count(new_text) == 1; data = data.replace(new_text, old)
assert data == before(adapter), 'Physical/routing/strict-whitespace/work core changed'
for filename in ('PcbPassivePoseTrialSourceContractTest.java', 'PcbPassiveSizingSourceContractTest.java'):
    name = 'tests/contracts/'+filename
    data = (COPY/name).read_bytes()
    assert data.count(b'Rb56Plan.resolve(42L, 19)')+data.count(b'Rb56Plan.resolve(42L,19)') == 1
    data = data.replace(b'Rb56Plan.resolve(42L, 19)', b'Rb56Plan.resolve(42L, 18)').replace(
        b'Rb56Plan.resolve(42L,19)', b'Rb56Plan.resolve(42L,18)').replace(b'interpretation19 rejects', b'interpretation18 rejects')
    assert data == before(name), filename
registry_name = 'scripts/verify-current-contracts.ps1'
data = (COPY/registry_name).read_bytes(); nl = b'\r\n' if b'\r\n' in data else b'\n'
for name, marker in read(HERE/'new-suites.json')['unit']+read(HERE/'new-suites.json')['routes']:
    entry = ("        @{ Name = '%s'; Marker = '%s' }," % (name, marker)).encode()
    assert data.count(nl+entry) == 1; data = data.replace(nl+entry, b'')
assert data == before(registry_name)
registry_rows = re.findall(r"^\s*@\{ Name = '([^']+)'; Marker = '([^']*)' \},?\s*$",
    (COPY/registry_name).read_text(), re.MULTILINE)
for filename, count in [('unit-suites.json', 8), ('routes-suites.json', 14)]:
    manifest = read(HERE/filename); names = {r[0] for r in manifest}
    assert len(manifest) == count and [[n, m] for n, m in registry_rows if n in names] == manifest
planner_name = 'src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java'
planner = (COPY/planner_name).read_text()
old_planner = before(planner_name).decode().replace('\r\n', '\n')
for literal in ('OUTLINE_CANDIDATES=6', 'MAX_PARTS=128', 'MAX_EXTENT=16000',
                'MAX_AREA=64000000L', 'MEDIUM_MAX_EVALUATIONS=60000'):
    assert planner.count(literal) == old_planner.count(literal) == 1
assert planner.count('new Random(') == old_planner.count('new Random(')
assert planner.count('PcbPassivePosePolicy.forVisit(') == old_planner.count('PcbPassivePosePolicy.forVisit(') == 2
assert 'guardDemandArea' in planner and 'MAX_AREA/2' in planner
planner_edits = [
    ('', '        // Preserve the all-pose demand cap when policy3 ranks from TOP0 only.\n        final long guardDemandArea;\n'),
    ('            sizingEnvelopeWidth=alternateEnvelope==null ? envelope.width :',
     '            boolean sourceSized=passivePosePolicyVersion==\n                PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL;\n            sizingEnvelopeWidth=alternateEnvelope==null || sourceSized ? envelope.width :'),
    ('            sizingEnvelopeHeight=alternateEnvelope==null ? envelope.height :',
     '            sizingEnvelopeHeight=alternateEnvelope==null || sourceSized ? envelope.height :'),
    ('            sizingDemandArea=Math.max(originalDemand,alternateDemand);',
     '            sizingDemandArea=alternateEnvelope==null || sourceSized ? originalDemand :\n                Math.max(originalDemand,alternateDemand);\n            guardDemandArea=Math.max(originalDemand,alternateDemand);'),
    ('                PcbPassivePosePolicy.SIZING_ONLY) &&',
     '                PcbPassivePosePolicy.SIZING_ONLY || constraints.passivePosePolicyVersion==\n                PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL) &&'),
    ('        long demandArea=0,courtyardArea=0; int maxWidth=0,anchorWidth=0;',
     '        long demandArea=0,guardDemandArea=0,courtyardArea=0; int maxWidth=0,anchorWidth=0;'),
    ('', '            guardDemandArea+=item.guardDemandArea;\n'),
    ('        if (demandArea>MAX_AREA/2) throw new Rejected("DEMAND_LIMIT");',
     '        if (guardDemandArea>MAX_AREA/2) throw new Rejected("DEMAND_LIMIT");')]
normalized = planner
for old, new_text in reversed(planner_edits):
    assert normalized.count(new_text) == 1
    normalized = normalized.replace(new_text, old)
assert normalized == old_planner, 'Planner changed outside declared sizing gate and conservative demand guard'
for path in sorted(HERE.glob('*.py')): ast.parse(path.read_text(encoding='utf-8-sig'), filename=path.name)
for name in overrides|additions:
    assert not any(s.rstrip() != s for s in (COPY/name).read_text().splitlines()), name
diff = []
for name in sorted(overrides):
    diff.extend(difflib.unified_diff([s+'\n' for s in before(name).decode().splitlines()],
        [s+'\n' for s in (COPY/name).read_text().splitlines()], fromfile=name, tofile=name, n=0))
(HERE/'source-review.diff').write_text(''.join(diff))
# Root must inspect this exact diff and write the separate acknowledgement before freeze.
digest = sha(HERE/'source-review.diff')
ack = read(HERE/'review-ack.json') if (HERE/'review-ack.json').exists() else {}
assert ack.get('rootReviewedDiffSha256') == digest, 'Root review acknowledgement required for this exact diff'
assert ack.get('actualSelectedFootprintFitsUnchanged') is True
assert ack.get('conservativeDemandLimitBasisRetained') is True
plan = {'rootReviewed': True, 'baseCopyCount': 1510, 'copyCount': 1528,
        'overrides': sorted(overrides), 'additions': sorted(additions)}
(HERE/'source-plan.json').write_text(json.dumps(plan, indent=2)+'\n')
(HERE/'source-review.json').write_text(json.dumps({
    'status': 'PASS', 'scope': 'Root static integrated diff and source-bound native adapters',
    'copyCount': 1528, 'overrides': 12, 'additions': 18, 'diffSha256': digest,
    'rootAcknowledgementSha256': sha(HERE/'review-ack.json'),
    'physicalCoreByteExactAfterSixWrapperMigrations': True,
    'oldSourceFixturesExactExceptUnsupported19': True, 'registryExactExcept18Entries': True,
    'sourceDerivedExecutionOrder': True, 'numericWorkCapsUnchanged': True,
    'plannerByteExactExceptDeclaredSizingGateAndConservativeGuard': True,
    'behaviorProof': 'NOT RUN; fresh unit8 and28 full routes required'}, indent=2)+'\n')
print('PASS root static1528 /12 overrides /18 additions / exact physical core and registry order')
