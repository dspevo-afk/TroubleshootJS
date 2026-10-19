"""Static integrated review and exact narrow adapter boundary before native freeze."""
import ast
import difflib
import hashlib
import json
import re
import sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
COPY=Path(json.loads((HERE/'local-state.json').read_text())['scratch'])
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
base={r['path']:r['sha256'] for r in read(HERE/'copy-inputs.json')['files']}
core=('GenerationRequest','MediumBoardNormalAdmission','PcbPassivePosePolicy',
      'PcbPlacementPlanner','PcbTwoLayerRules','Rb56Placement')
overrides={'src/com/lushprojects/circuitjs1/client/'+n+'.java' for n in core}|{
    'tests/contracts/'+n+'.java' for n in ('PcbPassivePoseAdmissionContractTest',
    'PcbPassivePosePolicyContractTest','PcbPassivePoseTrialSourceContractTest',
    'PcbPassiveSizingSourceContractTest','PcbSourceSizedPoseSourceContractTest',
    'PcbPostSelectionEdgeTrimContractTest')}|{'scripts/verify-current-contracts.ps1'}
new=read(HERE/'new-suites.json')
additions={'tests/contracts/'+row[0]+'.java' for row in new['unit']+new['routes']}
actual={p.relative_to(COPY).as_posix():sha(p) for p in COPY.rglob('*') if p.is_file()}
assert len(actual)==1547 and set(actual)==set(base)|additions
assert len(overrides)==13 and len(additions)==19
assert {n for n in base if actual[n]!=base[n]}==overrides
def before(name):
    sources=[HERE.parent/folder/'source'/name for folder in (
        'passive-source-sizing-trial-r1','passive-sizing-control-r1','passive-pose-policy-r1')]+[ROOT/name]
    p=next((p for p in sources if p.is_file() and sha(p)==base[name]),None)
    assert p is not None,name
    return p.read_bytes()
tree=ast.parse((HERE/'prepare_fixtures.py').read_text())
changes=next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign)
    and any(isinstance(t,ast.Name) and t.id=='changes' for t in n.targets))
adapter='tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
data=(COPY/adapter).read_bytes(); assert len(changes)==6
for old,new_text in reversed(changes):
    assert data.count(new_text)==1;data=data.replace(new_text,old)
assert data==before(adapter),'Native physical/routing/whitespace/work core changed'
for filename in ('PcbPassivePoseTrialSourceContractTest.java','PcbPassiveSizingSourceContractTest.java',
                 'PcbSourceSizedPoseSourceContractTest.java'):
    name='tests/contracts/'+filename;data=(COPY/name).read_bytes()
    assert data.count(b'Rb56Plan.resolve(42L, 20)')+data.count(b'Rb56Plan.resolve(42L,20)')==1
    data=data.replace(b'Rb56Plan.resolve(42L, 20)',b'Rb56Plan.resolve(42L, 19)').replace(
        b'Rb56Plan.resolve(42L,20)',b'Rb56Plan.resolve(42L,19)').replace(b'interpretation20 rejects',b'interpretation19 rejects')
    assert data==before(name),filename
registry='scripts/verify-current-contracts.ps1';data=(COPY/registry).read_bytes()
nl=b'\r\n' if b'\r\n' in data else b'\n'
for name,marker in new['unit']+new['routes']:
    entry=("        @{ Name = '%s'; Marker = '%s' },"%(name,marker)).encode()
    assert data.count(nl+entry)==1;data=data.replace(nl+entry,b'')
assert data==before(registry)
rows=re.findall(r"^\s*@\{ Name = '([^']+)'; Marker = '([^']*)' \},?\s*$",(COPY/registry).read_text(),re.MULTILINE)
for filename,count in [('unit-suites.json',9),('routes-suites.json',14)]:
    manifest=read(HERE/filename);names={r[0] for r in manifest}
    assert len(manifest)==count and [[n,m] for n,m in rows if n in names]==manifest
    for name,marker in manifest:assert marker in (COPY/'tests/contracts'/(name+'.java')).read_text()
planner_name='src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java'
planner=(COPY/planner_name).read_text();old_planner=before(planner_name).decode().replace('\r\n','\n')
for literal in ('OUTLINE_CANDIDATES=6','MAX_PARTS=128','MAX_EXTENT=16000',
                'MAX_AREA=64000000L','MEDIUM_MAX_EVALUATIONS=60000'):
    assert planner.count(literal)==old_planner.count(literal)==1
assert planner.count('new Random(')==old_planner.count('new Random(')
assert 'guardDemandArea' in planner and 'MAX_AREA/2' in planner
# Delete only the new policy's evaluator block, dispatch, and two sizing gates.
# Exact reconstruction of18 then protects every legacy loop/counter statement.
normalized=planner
start='    private static final class LocalPoseSelection {'
end='    private static PcbFootprint mediumPlace(Item item,Vector<PcbFootprint> placed,'
assert normalized.count(start)==normalized.count(end)==1
i=normalized.index(start);j=normalized.index(end,i)
normalized=normalized[:i]+normalized[j:]
dispatch='''        if (constraints.passivePosePolicyVersion == PcbPassivePosePolicy.LOCAL_POSE_CHOICE &&
                passivePoseAlternative != null)
            return mediumPlaceLocalPoseChoice(item,placed,topology,board,constraints,outline,
                seed,algorithmCandidate,useCompact,scoreWholeContent,anchor,side,preferredX,
                baseX,baseY,direction,hint,group,support,vacancies,vacancyPreference,
                vacancyPocket,evaluations);
'''
assert normalized.count(dispatch)==1;normalized=normalized.replace(dispatch,'')
for old_gate,new_gate in [
    ('PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL;',
     'PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL || passivePosePolicyVersion == PcbPassivePosePolicy.LOCAL_POSE_CHOICE;'),
    ('PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL) &&',
     'PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL || constraints.passivePosePolicyVersion ==\n                PcbPassivePosePolicy.LOCAL_POSE_CHOICE) &&')]:
    assert normalized.count(new_gate)==1;normalized=normalized.replace(new_gate,old_gate)
assert normalized==old_planner,'Planner changed outside declared new policy block/dispatch/two gates'
for path in HERE.glob('*.py'):ast.parse(path.read_text(encoding='utf-8-sig'),filename=path.name)
for name in overrides|additions:
    assert not any(s.rstrip()!=s for s in (COPY/name).read_text().splitlines()),name
diff=[]
for name in sorted(overrides|additions):
    old=before(name).decode().splitlines() if name in overrides else []
    diff.extend(difflib.unified_diff([s+'\n' for s in old],
        [s+'\n' for s in (COPY/name).read_text().splitlines()],fromfile=name,tofile=name,n=0))
(HERE/'source-review.diff').write_text(''.join(diff))
digest=sha(HERE/'source-review.diff')
if sys.argv[1:]==['emit']:
    print('READY for root diff review:1547 /13 overrides /19 additions /'+digest)
    raise SystemExit(0)
ack=read(HERE/'review-ack.json') if (HERE/'review-ack.json').exists() else {}
assert ack.get('rootReviewedDiffSha256')==digest,'Root must review this exact integrated diff before freeze'
for key in ('actualSelectedFootprintFitsUnchanged','conservativeDemandLimitBasisRetained',
            'legacyPoliciesAccountingPreserved','newPolicyReservesEveryPose','actualLoopFixturesUseSelectedImplementation'):
    assert ack.get(key) is True,key
plan={'rootReviewed':True,'baseCopyCount':1528,'copyCount':1547,
      'overrides':sorted(overrides),'additions':sorted(additions)}
(HERE/'source-plan.json').write_text(json.dumps(plan,indent=2)+'\n')
(HERE/'source-review.json').write_text(json.dumps({
    'status':'PASS','scope':'Root static integrated diff and source-bound native adapters',
    'copyCount':1547,'overrides':13,'additions':19,'diffSha256':digest,
    'rootAcknowledgementSha256':sha(HERE/'review-ack.json'),
    'physicalCoreByteExactAfterSixWrapperMigrations':True,
    'oldSourceFixturesExactExceptUnsupported20':True,'registryExactExcept19Entries':True,
    'sourceDerivedExecutionOrder':True,'numericWorkCapsUnchanged':True,
    'legacyPlannerByteExactAfterDeclaredNewPolicyRemoval':True,
    'behaviorProof':'NOT RUN; fresh unit9 and28 full routes required'},indent=2)+'\n')
print('PASS root static1547 /13 overrides /19 additions / exact physical core and registry order')
