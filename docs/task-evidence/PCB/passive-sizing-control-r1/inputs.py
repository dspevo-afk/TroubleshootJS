"""Freeze exact control deltas only after root reviews the explicit source plan."""
import hashlib
import json
import stat
import sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
POLICY=HERE.parent/'passive-pose-policy-r1'
COPY=Path(json.loads((HERE/'local-state.json').read_text())['scratch']).resolve()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
read=lambda p:json.loads(p.read_text())
mode=sys.argv[1]
assert mode in ('freeze','audit')
base=read(HERE/'copy-inputs.json')
plan=read(HERE/'source-plan.json')
assert plan['rootReviewed'] is True and plan['baseCopyCount']==1504 and plan['copyCount']==1510
expected_base={r['path']:r['sha256'] for r in base['files']}
overrides=set(plan['overrides']);additions=set(plan['additions'])
assert len(additions)==6

def inventory(directory):
    assert not directory.is_symlink() and not directory.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    result={}
    for child in sorted(directory.iterdir()):
        assert not child.is_symlink() and not child.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
        assert child.resolve().is_relative_to(COPY)
        if child.is_dir():
            assert child.name!='__pycache__'
            result.update(inventory(child))
        else:result[child.relative_to(COPY).as_posix()]=sha(child)
    return result

current=inventory(COPY)
assert len(current)==1510 and set(current)==set(expected_base)|additions
assert {p for p in expected_base if current[p]!=expected_base[p]}==overrides
policy=read(POLICY/'native-inputs.json')
assert sha(POLICY/'native-inputs.json')==base['policySealSha256']
for name,digest in policy['gateOwners'].items():assert sha(POLICY/name)==digest
for name,digest in dict(policy['overrides'],**policy['additions']).items():
    assert sha(POLICY/'source'/name)==digest
main_seal=HERE.parent/'silkscreen-scale-r1/final-inputs.json'
assert sha(main_seal)==base['main1475SealSha256']
main=read(main_seal)['files']
assert len(main)==1475
for row in main:assert sha(ROOT/row['path'])==row['sha256'],'Main accepted owner changed'
protocol=read(HERE/'protocol.json')
for name,digest in protocol['baselinesSha256'].items():assert sha(HERE/name)==digest
assert sha(HERE/'route-baselines.json')==base['routeBaselinesSha256']
assert sha(HERE/'raw-baselines.json')==base['rawBaselinesSha256']
owners=('proposal.json','protocol.json','prepare.py','prepare_protocol.py','prepare_fixtures.py',
 'prepare_phase_gates.py','copy-inputs.json','accepted15-baselines.json','route-baselines.json',
 'raw-baselines.json','pose-baselines.json','unit-suites.json','route-suites.json',
 'worker-notes.md','reader-notes.md','register.py','inputs.py','run_native.ps1',
 'check_process_cleanup.ps1','sanitize.py','validate_unit.py','run_assessment.py',
 'assess.py','check_assessment.py','assessment-canaries.json','assessment-canary-results.json',
 'source-plan.json','review_source.py','source-review.json','retire_copy.py','preserve_attempt.py')
assert all((HERE/name).is_file() for name in owners),'Gate owner missing'
archive=HERE/'source'
if mode=='freeze':
    assert not archive.exists() and not (HERE/'native-inputs.json').exists()
    for name in sorted(overrides|additions):
        target=archive/name;target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes((COPY/name).read_bytes())
for name in sorted(overrides|additions):assert sha(archive/name)==current[name]
actual={'baseSha256':sha(HERE/'copy-inputs.json'),'copyCount':1510,
 'overrides':{name:current[name] for name in sorted(overrides)},
 'additions':{name:current[name] for name in sorted(additions)},
 'gateOwners':{name:sha(HERE/name) for name in owners},
 'main1475SealSha256':sha(main_seal),'reusedPolicySealSha256':sha(POLICY/'native-inputs.json')}
target=HERE/'native-inputs.json'
if mode=='freeze':
    target.write_text(json.dumps(actual,indent=2)+'\n')
    print('FROZEN native1510 / explicit root-reviewed deltas / '+str(len(owners))+' gates; main1475 exact')
else:
    same=read(target)==actual
    (HERE/'native-input-audit.json').write_text(json.dumps({'status':'PASS' if same else 'FAIL',
      'copyCount':1510,'main1475Exact':True,'policy1504ReferenceExact':True,
      'changedOwners':sorted(overrides),'addedOwners':sorted(additions)},indent=2)+'\n')
    print('PASS exact native1510/gates/policy reference/main1475' if same else 'FAIL control seal')
    raise SystemExit(not same)
