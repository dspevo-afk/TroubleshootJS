"""Freeze exact accepted base plus explicitly owned native deltas and gate bytes."""
import hashlib
import json
import stat
import sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
COPY=Path(json.loads((HERE/'local-state.json').read_text())['scratch']).resolve()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
base_path=HERE/'copy-inputs.json'
base={r['path']:r['sha256'] for r in json.loads(base_path.read_text())['files']}
source_names=['PhysicalPackages','Rb56Placement','PcbPlacementConstraints','PcbPlacementPlanner',
 'MediumBoardNormalAdmission','PcbTwoLayerRules','Rb56Generator','Rb56PlayerFamilyCapability',
 'GenerationDependencyContext','GenerationRequest']
overrides={'src/com/lushprojects/circuitjs1/client/'+n+'.java' for n in source_names}
overrides.update(['tests/contracts/PcbPostSelectionEdgeTrimContractTest.java','scripts/verify-current-contracts.ps1'])
test_names=['PcbPassivePoseFoundationContractTest','PcbPassivePoseSourceContractTest',
 'PcbPassivePosePolicyContractTest','PcbPassivePoseAdmissionContractTest',
 'PcbPassivePoseTrialSourceContractTest','PcbPassivePoseRawContractTest',
 'PcbPassivePoseRawContractTestGroup2','PcbPassivePoseRawContractTestGroup3']
test_names += [r[0] for r in json.loads((HERE/'route-suites.json').read_text())]
additions={'tests/contracts/'+n+'.java' for n in test_names}
additions.add('src/com/lushprojects/circuitjs1/client/PcbPassivePosePolicy.java')
assert len(overrides)==12 and len(additions)==37


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
assert len(base)==1467 and len(current)==1504 and set(current)==set(base)|additions
assert {p for p in base if base[p]!=current[p]}==overrides,'Unexpected changed native owner'
main_seal=HERE.parent/'silkscreen-scale-r1/final-inputs.json'
main=json.loads(main_seal.read_text())['files']
assert len(main)==1475
for row in main:
    assert sha(ROOT/row['path'])==row['sha256'],'Main accepted owner changed'
owners=('proposal.json','proposal-initial.json','sizing-clarification.json','prepare.py','prepare_tests.py',
 'copy-inputs.json','accepted15-baselines.json','route-suites.json','unit-suites.json',
 'worker-notes.md','admission-worker-notes.md','review_source.py','source-review.json',
 'register.py','inputs.py','run_native.ps1','check_process_cleanup.ps1','assess.py',
 'check_assessment.py','assessment-canaries.json','assessment-canary-results.json','validate_unit.py','sanitize.py',
 'retire_copy.py','preserve_attempt.py','prepare_lifecycle_gates.py')
assert all((HERE/p).is_file() for p in owners),'Gate owner missing'
archive=HERE/'source'
mode=sys.argv[1]
if mode=='freeze':
    assert not archive.exists() and not (HERE/'native-inputs.json').exists()
    for relative in sorted(overrides|additions):
        target=archive/relative
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes((COPY/relative).read_bytes())
for relative in sorted(overrides|additions):
    assert sha(archive/relative)==current[relative],'Frozen source archive differs'
actual={'baseSha256':sha(base_path),'copyCount':len(current),
 'overrides':{p:current[p] for p in sorted(overrides)},'additions':{p:current[p] for p in sorted(additions)},
 'gateOwners':{p:sha(HERE/p) for p in owners},'main1475SealSha256':sha(main_seal)}
target=HERE/'native-inputs.json'
if mode=='freeze':
    target.write_text(json.dumps(actual,indent=2)+'\n')
    print('FROZEN native1504 via accepted1467/12overrides/37additions; main1475 exact')
elif mode=='audit':
    same=json.loads(target.read_text())==actual
    (HERE/'native-input-audit.json').write_text(json.dumps({'status':'PASS' if same else 'FAIL',
      'copyCount':len(current),'changedOwners':sorted(overrides),'addedOwners':sorted(additions),
      'main1475Exact':True},indent=2)+'\n')
    print('PASS exact native1504, frozen gates and main1475' if same else 'FAIL native seal')
    raise SystemExit(not same)
else:
    raise ValueError('Expected freeze/audit')
