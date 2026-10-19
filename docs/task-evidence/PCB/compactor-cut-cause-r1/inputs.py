"""Freeze or audit the root-reviewed observation-only source copy and readers."""
import hashlib
import json
import stat
import subprocess
import sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
COPY=Path(read(HERE/'local-state.json')['scratch']).resolve()
SEAL=ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(SEAL)=='9ff8de2abae0d3e555bc57c84ff7231d2aa8abd5dfdc2383a65398915074da9a'
main=read(SEAL)['files'];assert len(main)==1475
for row in main:assert sha(ROOT/row['path'])==row['sha256'],row['path']
base={r['path']:r['sha256'] for r in main if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')}
assert len(base)==1467
overrides={'src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java',
    'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java','scripts/verify-current-contracts.ps1'}
additions={'tests/contracts/PcbCompactorCutObserverContractTest.java','tests/contracts/PcbCompactorCutCauseContractTest.java'}
additions.update('tests/contracts/PcbCompactorCutCauseContractTestGroup'+str(i)+'.java' for i in range(2,8))
assert len(overrides)==3 and len(additions)==8
def inventory(folder):
    assert not folder.is_symlink() and folder.resolve().is_relative_to(COPY)
    assert not folder.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    result={}
    for p in folder.iterdir():
        assert not p.is_symlink() and p.resolve().is_relative_to(COPY)
        assert not p.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
        if p.is_dir():result.update(inventory(p))
        else:result[p.relative_to(COPY).as_posix()]=sha(p)
    return result
actual=inventory(COPY)
assert len(actual)==1475 and set(actual)==set(base)|additions
assert {p for p in base if actual[p]!=base[p]}==overrides
subprocess.run([sys.executable,'-X','utf8','-B',str(HERE/'review_source.py')],check=True)
assert read(HERE/'review-ack.json')['rootReviewed'] is True
assert read(HERE/'review-ack.json')['diffSha256']==sha(HERE/'source-review.diff')
assert read(HERE/'assessment-canary-results.json')['status']=='PASS'
assert read(HERE/'suite-transport-canaries.json')['status']=='PASS'
assert read(HERE/'suite-transport-canaries.json')['caseCount']==12
owners=('proposal.json','prepare.py','prepare_cases.py','prepare_phase_gates.py',
    'protocol.json','unit-suites.json','routes-suites.json','inputs.py','run_native.ps1',
    'check_process_cleanup.ps1','sanitize.py','assess.py','check_assessment.py',
    'assessment-canary-results.json','reader-notes.md','worker-notes.md',
    'review_source.py','source-review.diff','source-review.json','review-ack.json','retire_copy.py',
    'suite_transport.ps1','check_suite_transport.ps1','suite-transport-canaries.json')
assert all((HERE/name).is_file() for name in owners)
mode=sys.argv[1];assert mode in ('freeze','audit')
archive=HERE/'source'
if mode=='freeze':
    assert not archive.exists() and not (HERE/'native-inputs.json').exists()
    for name in sorted(overrides|additions):
        p=archive/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((COPY/name).read_bytes())
for name in overrides|additions:assert sha(archive/name)==actual[name],name
baseline=ROOT/read(HERE/'protocol.json')['baselinePath']
assert sha(baseline)==read(HERE/'protocol.json')['baselineSha256']
record={'baseMainSealSha256':sha(SEAL),'baseCount':1467,'copyCount':1475,
    'overrides':{n:actual[n] for n in sorted(overrides)},'additions':{n:actual[n] for n in sorted(additions)},
    'gateOwners':{n:sha(HERE/n) for n in owners},'accepted15BaselineSha256':sha(baseline)}
if mode=='freeze':
    (HERE/'native-inputs.json').write_text(json.dumps(record,indent=2)+'\n')
    print('FROZEN observation COPY1475 /3 overrides /8 additions /24 gates; main1475 exact')
else:
    assert read(HERE/'native-inputs.json')==record,'Frozen observation source/gate inputs changed'
    (HERE/'native-input-audit.json').write_text(json.dumps({'status':'PASS','main1475Exact':True,'copyCount':1475,'sourceDeltas':11,'gateOwners':24},indent=2)+'\n')
    print('PASS frozen observation COPY/source/readers exact; main1475 exact')
