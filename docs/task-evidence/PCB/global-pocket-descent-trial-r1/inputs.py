"""Freeze or audit reviewed native-only global-pocket sources and acceptance gates."""
import hashlib
import json
import stat
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
COPY = Path(read(HERE/'local-state.json')['scratch']).resolve()
SEAL = ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(SEAL) == '9ff8de2abae0d3e555bc57c84ff7231d2aa8abd5dfdc2383a65398915074da9a'
main = read(SEAL)['files']
assert len(main) == 1475
for row in main:
    assert sha(ROOT/row['path']) == row['sha256'],row['path']
base = {r['path']:r['sha256'] for r in main if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')}
assert len(base) == 1467
reuse = read(HERE/'source-audit.json')['scaffoldingReuse']
old = HERE.parent/'local-pocket-descent-trial-r1'
assert sha(old/'native-inputs.json') == reuse['oldSealSha256']
for name,digest in reuse['files'].items():
    assert sha(old/name) == digest,name
overrides = {'src/com/lushprojects/circuitjs1/client/'+n+'.java' for n in
    ('Rb56Placement','PcbPlacementConstraints','PcbPlacementPlanner','MediumBoardPhysicalPolicy','SeededPcbLayoutGenerator','PlayerSessionReplay')}
overrides.update(('tests/contracts/PcbPostSelectionEdgeTrimContractTest.java','scripts/verify-current-contracts.ps1'))
additions = {'tests/contracts/PcbGlobalPocketDescentContractTest.java','tests/contracts/PcbMediumPlacementCostContractTest.java','tests/contracts/PcbGlobalPocketDescentTrialContractTest.java'}
additions.update('tests/contracts/PcbGlobalPocketDescentTrialContractTestGroup'+str(i)+'.java' for i in range(2,8))

def inventory(folder):
    assert not folder.is_symlink() and folder.resolve().is_relative_to(COPY)
    assert not folder.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    result = {}
    for p in folder.iterdir():
        assert not p.is_symlink() and p.resolve().is_relative_to(COPY)
        assert not p.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
        if p.is_dir():
            result.update(inventory(p))
        else:
            result[p.relative_to(COPY).as_posix()] = sha(p)
    return result

actual = inventory(COPY)
assert len(actual) == 1476 and set(actual) == set(base)|additions
assert {p for p in base if actual[p] != base[p]} == overrides
subprocess.run([sys.executable,'-X','utf8','-B',str(HERE/'review_source.py')],check=True)
ack = read(HERE/'review-ack.json')
assert ack['rootReviewed'] is True and ack['diffSha256'] == sha(HERE/'source-review.diff')
canaries = read(HERE/'assessment-canary-results.json')
assert canaries['status'] == 'PASS' and canaries['caseCount'] == 51
for name,digest in canaries['consumedSha256'].items():
    p = ROOT/name if name.startswith('docs/') else HERE/name
    assert sha(p) == digest,name
transport = read(HERE/'suite-transport-canaries.json')
assert transport['status'] == 'PASS' and transport['caseCount'] == 12
for name,digest in transport['consumedSha256'].items():
    assert sha(HERE/name) == digest,name
owners = ('proposal.json','placement-audit.md','source-audit.json','prepare.py','prepare_cases.py','bootstrap.py',
    'protocol.json','unit-suites.json','routes-suites.json','inputs.py','run_native.ps1',
    'check_process_cleanup.ps1','sanitize.py','assess.py','check_assessment.py',
    'assessment-canary-results.json','reader-notes.md','worker-notes.md','score-worker-notes.md',
    'review_source.py','source-review.diff','source-review.json','review-ack.json','retire_copy.py',
    'suite_transport.ps1','check_suite_transport.ps1','suite-transport-canaries.json')
assert len(owners) == 27 and all((HERE/n).is_file() for n in owners)
mode = sys.argv[1]
assert mode in ('freeze','audit')
archive = HERE/'source'
if mode == 'freeze':
    assert not archive.exists() and not (HERE/'native-inputs.json').exists()
    for name in sorted(overrides|additions):
        p = archive/name
        p.parent.mkdir(parents=True,exist_ok=True)
        p.write_bytes((COPY/name).read_bytes())
for name in overrides|additions:
    assert sha(archive/name) == actual[name],name
protocol = read(HERE/'protocol.json')
baseline = ROOT/protocol['baselinePath']
assert sha(baseline) == protocol['baselineSha256']
record = {'baseMainSealSha256':sha(SEAL),'baseCount':1467,'copyCount':1476,
    'overrides':{n:actual[n] for n in sorted(overrides)},'additions':{n:actual[n] for n in sorted(additions)},
    'gateOwners':{n:sha(HERE/n) for n in owners},'accepted15BaselineSha256':sha(baseline)}
if mode == 'freeze':
    (HERE/'native-inputs.json').write_text(json.dumps(record,indent=2)+'\n')
    print('FROZEN global-pocket COPY1476 /8 overrides /9 additions /27 gates; main1475 exact')
else:
    assert read(HERE/'native-inputs.json') == record,'Frozen global-pocket source/gate inputs changed'
    (HERE/'native-input-audit.json').write_text(json.dumps({
        'status':'PASS','main1475Exact':True,'copyCount':1476,'sourceDeltas':17,'gateOwners':27},indent=2)+'\n')
    print('PASS frozen global-pocket COPY/source/readers exact; main1475 exact')
