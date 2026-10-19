"""Freeze diagnostic deltas against exact published policy1504 and main1475."""
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
copy_manifest=HERE/'copy-inputs.json'
prepared=json.loads(copy_manifest.read_text())
base={r['path']:r['sha256'] for r in prepared['files']}
overrides={'src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java','scripts/verify-current-contracts.ps1'}
additions={'src/com/lushprojects/circuitjs1/client/PcbPassiveChoiceObserver.java',
 'tests/contracts/PcbPassiveChoiceObserverContractTest.java',
 'tests/contracts/PcbPassiveChoiceRouteContractTest.java','tests/contracts/PcbPassiveChoiceRouteContractTestGroup2.java'}
mode=sys.argv[1]
assert mode in ('freeze','audit')


def inventory(directory):
    assert not directory.is_symlink() and not directory.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    result={}
    for child in sorted(directory.iterdir()):
        assert not child.is_symlink() and not child.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
        assert child.resolve().is_relative_to(COPY)
        if child.is_dir():
            assert child.name!='__pycache__'
            result.update(inventory(child))
        else:
            result[child.relative_to(COPY).as_posix()]=sha(child)
    return result


current=inventory(COPY)
assert len(base)==1504 and len(current)==1508 and set(current)==set(base)|additions
assert {p for p in base if base[p]!=current[p]}==overrides,'Unscoped diagnostic edit'
assert sha(HERE/'route-baselines.json')==prepared['routeBaselinesSha256']
assert sha(POLICY/'native-inputs.json')==prepared['policySealSha256']
policy_seal=json.loads((POLICY/'native-inputs.json').read_text())
for name,digest in policy_seal['gateOwners'].items():
    assert sha(POLICY/name)==digest,'Reused policy gate changed'
for name,digest in dict(policy_seal['overrides'],**policy_seal['additions']).items():
    assert sha(POLICY/'source'/name)==digest,'Reused policy source changed'
main_seal=HERE.parent/'silkscreen-scale-r1/final-inputs.json'
assert sha(main_seal)==prepared['main1475SealSha256']
main=json.loads(main_seal.read_text())['files']
assert len(main)==1475
for row in main:
    assert sha(ROOT/row['path'])==row['sha256'],'Main accepted owner changed'
owners=('proposal.json','prepare.py','prepare_routes.py','prepare_phase_gates.py',
 'copy-inputs.json','accepted15-baselines.json','route-baselines.json','unit-suites.json','route-suites.json',
 'worker-notes.md','reader-notes.md','register.py','inputs.py','run_native.ps1','check_process_cleanup.ps1',
 'sanitize.py','review_source.py','hook-normalization.json','source-review.json','assess.py',
 'check_assessment.py','assessment-canaries.json','assessment-canary-results.json','validate_unit.py','run_assessment.py',
 'retire_copy.py','preserve_attempt.py','reader-correction.json','reuse_phase_outputs.py')
assert all((HERE/p).is_file() for p in owners),'Gate owner missing'
archive=HERE/'source'
if mode=='freeze':
    assert not archive.exists() and not (HERE/'native-inputs.json').exists()
    for name in sorted(overrides|additions):
        target=archive/name;target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes((COPY/name).read_bytes())
for name in sorted(overrides|additions):
    assert sha(archive/name)==current[name],'Frozen diagnostic source mismatch'
actual={'baseSha256':sha(copy_manifest),'copyCount':1508,
 'overrides':{p:current[p] for p in sorted(overrides)},'additions':{p:current[p] for p in sorted(additions)},
 'gateOwners':{p:sha(HERE/p) for p in owners},'main1475SealSha256':sha(main_seal),
 'reusedPolicySealSha256':sha(POLICY/'native-inputs.json'),
 'sourceStudySha256':sha(POLICY/'next-cause-source-review.md')}
target=HERE/'native-inputs.json'
if mode=='freeze':
    target.write_text(json.dumps(actual,indent=2)+'\n')
    print('FROZEN native1508 via policy1504/2overrides/4additions; accepted main1475 exact')
else:
    same=json.loads(target.read_text())==actual
    (HERE/'native-input-audit.json').write_text(json.dumps({'status':'PASS' if same else 'FAIL',
      'copyCount':1508,'main1475Exact':True,'policy1504ReferenceExact':True,
      'changedOwners':sorted(overrides),'addedOwners':sorted(additions)},indent=2)+'\n')
    print('PASS exact native1508/gates/policyreference/main1475' if same else 'FAIL diagnostic seal')
    raise SystemExit(not same)
