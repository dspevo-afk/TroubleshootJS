"""Create an exact owned copy of the rejected policy before observer edits."""
import hashlib
import json
import stat
import tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
POLICY=HERE.parent/'passive-pose-policy-r1'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
state=HERE/'local-state.json'
assert not state.exists()
main_seal=HERE.parent/'silkscreen-scale-r1/final-inputs.json'
main=json.loads(main_seal.read_text())['files']
assert len(main)==1475
for row in main:
    assert sha(ROOT/row['path'])==row['sha256']
native=[r for r in main if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')]
assert len(native)==1467
policy_seal=json.loads((POLICY/'native-inputs.json').read_text())
assert policy_seal['copyCount']==1504 and json.loads((POLICY/'root-review.json').read_text())['strictTrialStatus']=='FAIL'
for name,digest in policy_seal['gateOwners'].items():
    assert sha(POLICY/name)==digest
deltas=dict(policy_seal['overrides'],**policy_seal['additions'])
assert len(deltas)==49
for name,digest in deltas.items():
    assert sha(POLICY/'source'/name)==digest
copy=Path(tempfile.mkdtemp(prefix='tsj-passive-choice-')).resolve()
assert copy.parent==Path(tempfile.gettempdir()).resolve()
assert not copy.is_symlink() and not copy.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
state.write_text(json.dumps({'scratch':str(copy)},indent=2)+'\n')
for row in native:
    source=ROOT/row['path']
    assert source.resolve().is_relative_to(ROOT) and not source.is_symlink()
    assert not source.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    target=copy/row['path'];target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes(source.read_bytes())
for name,digest in deltas.items():
    target=copy/name;target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes((POLICY/'source'/name).read_bytes())
expected={r['path']:r['sha256'] for r in native};expected.update(deltas)
actual={p.relative_to(copy).as_posix():sha(p) for p in copy.rglob('*') if p.is_file()}
assert len(actual)==1504 and actual==expected
cases=[('42',15),('42',16),('3',15),('3',16)]
text=(POLICY/'routes.txt').read_text(encoding='utf-8-sig')
rows=[json.loads(l[len('PCB_PASSIVE_POLICY_ROUTE '):]) for l in text.splitlines() if l.startswith('PCB_PASSIVE_POLICY_ROUTE ')]
bound=[next(r for r in rows if (r['seed'],r['version'])==key) for key in cases]
(HERE/'route-baselines.json').write_text(json.dumps({'rows':bound,'origin':'Published policy native28; informed diagnostic cases only'},indent=2)+'\n')
(HERE/'accepted15-baselines.json').write_bytes((POLICY/'accepted15-baselines.json').read_bytes())
(HERE/'copy-inputs.json').write_text(json.dumps({'status':'PASS','acceptedBase':native,
 'reusedPolicyDeltas':deltas,'files':[{'path':p,'sha256':h} for p,h in sorted(expected.items())],
 'main1475SealSha256':sha(main_seal),'policySealSha256':sha(POLICY/'native-inputs.json'),
 'routeBaselinesSha256':sha(HERE/'route-baselines.json')},indent=2)+'\n')
(HERE/'main-input-audit.json').write_text(json.dumps({'status':'PASS','main1475Exact':True,
 'acceptedNativeBase':1467,'exactPolicyInputs':1504,'reusedDeltas':49},indent=2)+'\n')
print('PASS main1475/accepted1467/exact rejected policy1504 and four full baseline rows; private owned copy prepared')
