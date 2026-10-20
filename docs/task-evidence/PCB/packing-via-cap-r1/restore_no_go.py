"""Restore only the verified eleven task-owned sources after an actual NO_GO."""
import hashlib,json,runpy,subprocess
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert not (HERE/'restored-inputs.json').exists()
result=read(HERE/'target-r3/assessment.json')
assert result['status']=='PASS_ACTUAL_OPERATION' and result['decision']=='NO_GO'
for phase,prefix in (('fixture-r3','fixture-'),('target-r3','')):
    c=read(HERE/phase/(prefix+'process-cleanup.json'))
    assert c['status']=='PASS' and c['actualExecExitCode']==0 and all(x['recordedBirthExited'] is True for x in c['checks'])
current=runpy.run_path(str(HERE/'third_inputs.py'))['record']('target-r3')
assert current==read(HERE/'target-r3/inputs.json')
baseline=read(HERE/'baseline-inputs.json')
owners=runpy.run_path(str(HERE/'inputs.py'))['OWNERS']
assert len(owners)==11
prepared={}
for n in sorted(owners):
    assert sha(ROOT/n)==current['files'][n]==sha(HERE/'target-r3/candidate-source'/n)
    raw=(HERE/'baseline-source'/n).read_bytes()
    assert hashlib.sha256(raw).hexdigest()==baseline['files'][n]
    expected=subprocess.check_output(['git','rev-parse',baseline['baseCommit']+':'+n],cwd=ROOT).decode().strip()
    observed=subprocess.check_output(['git','hash-object','--path='+n,'--stdin'],input=raw,cwd=ROOT).decode().strip()
    assert observed==expected
    prepared[n]=raw
for n,raw in prepared.items():(ROOT/n).write_bytes(raw)
files={n:sha(ROOT/n) for n in baseline['files']}
assert files==baseline['files'] and {n:sha(ROOT/n) for n in baseline['machinery']}==baseline['machinery']
assert not subprocess.check_output(['git','diff','--name-only','--',*sorted(owners)],cwd=ROOT)
(HERE/'restored-inputs.json').write_text(json.dumps({'status':'PASS_NO_GO_OWNED_SOURCE_RESTORATION','baseCommit':baseline['baseCommit'],'files':files,'machinery':baseline['machinery'],'restoredOwners':sorted(owners),'targetAssessmentSha256':sha(HERE/'target-r3/assessment.json'),'scope':'Exact eleven owned paths restored only after source/raw/HEAD blob agreement and observed terminal exits; all1482 compiler+24 machinery inputs exact. Prior candidate source/failures/receipts retained. No unrelated path touched.'},indent=2)+'\n')
print('PASS NO_GO restored only11 verified task-owned sources;1482+24 exact')
