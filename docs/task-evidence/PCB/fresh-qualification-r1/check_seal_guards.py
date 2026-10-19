"""Synthetic sealer canaries; never read actual qualification measurements."""
import contextlib
import copy
import io
import json
import tempfile
from pathlib import Path
import seal_baselines

PACKET = Path(__file__).resolve().parent
scratch = Path(tempfile.mkdtemp(prefix='tsj-seal-guards-')).resolve()
assert scratch.parent == Path(tempfile.gettempdir()).resolve()
assert not scratch.is_symlink() and not scratch.is_junction()
seal_baselines.HERE = scratch
names = {'native-timing.json','post-native-input-audit.json','baseline-provenance.json',
         'native-inputs.json','native.txt','sealed-baselines.json','sealed-receipt.json'}
rows = [dict(status='PASS',seed=seed,version=13,density=12,parts=40,pads=80,area=10000,width=100,height=100,
             largestProtectedEmptyRectangleArea=400,copperManhattanLength=1000,vias=2,
             selectedAttempt=0,acceptedPlanEvaluations=100,placementRejections=0,
             oneFaceAttempts=3,twoLayerAttempts=3,routeAttempts=6,elapsedMillis=10,
             layoutSeed='9007199254740993',routingSeed='-9223372036854775808',
             geometrySha256='a'*64,sourceGraphSha256='b'*64,rankedPlacementAttempts=[0,1,2,3,4,5])
        for seed in ('2','3','4','6')]
results = []


def write_json(name, value):
    (scratch/name).write_text(json.dumps(value),encoding='utf-8')


def check(name, cases, expected='REJECT', change=None):
    for filename in ('sealed-baselines.json','sealed-receipt.json'):
        path = scratch/filename
        if path.exists():
            assert path.is_file() and not path.is_symlink() and not path.is_junction()
            path.unlink()
    write_json('native-timing.json',dict(status='PASS'))
    write_json('post-native-input-audit.json',dict(status='PASS'))
    write_json('baseline-provenance.json',dict(SourceDigest='b5c9c975207e19659fcdc2c7b8def06fe8a282ecec3d53c2e456556522101ee1'))
    write_json('native-inputs.json',dict(files=[]))
    lines = []
    for case in cases:
        lines += ['PCB_FRESH_BASELINE_CASE '+json.dumps(case),
                  'PCB_WHITESPACE_CASE '+json.dumps(dict(seed=case['seed'],version=13,
                      largestProtectedEmptyRectangle=dict(area=400)))]
    lines += ['PASS: PCB fresh qualification baseline contracts roots=4 assertions=synthetic',
              'CLEANUP: current JVM contract classes and task-owned scratch removed.']
    (scratch/'native.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    if change:
        change()
    try:
        with contextlib.redirect_stdout(io.StringIO()):
            seal_baselines.main()
        actual = 'PASS'
    except ValueError:
        actual = 'REJECT'
    if actual != expected:
        raise AssertionError((name,actual,expected))
    results.append(dict(case=name,status='PASS',actual=actual))


try:
    check('complete synthetic baseline accepts exact signed-long strings',rows,'PASS')
    with contextlib.redirect_stdout(io.StringIO()):
        seal_baselines.main()
    results.append(dict(case='same frozen inputs are idempotent',status='PASS',actual='PASS'))
    check('missing root',rows[:-1])
    check('duplicate root',rows+[rows[0]])
    wrong = copy.deepcopy(rows); wrong[0]['seed']='99'; check('unexpected root',wrong)
    wrong = copy.deepcopy(rows); wrong[0]['status']='FAIL'; check('full-field physical FAIL row',wrong)
    for key, value in (('version',14),('density',13),('area',9999),('copperManhattanLength',1.5),
                       ('geometrySha256','g'*64),('layoutSeed',9007199254740993),
                       ('routingSeed',str(1<<63)),('twoLayerAttempts',4),('elapsedMillis',-1)):
        wrong = copy.deepcopy(rows); wrong[0][key]=value; check('invalid field: '+key,wrong)
    check('failed native operation',rows,change=lambda:write_json('native-timing.json',dict(status='FAIL')))
    for key, before, after in (
            ('whitespace version','"version": 13, "largestProtectedEmptyRectangle"','"version": 13.0, "largestProtectedEmptyRectangle"'),
            ('whitespace area','"area": 400','"area": 400.0')):
        check('noninteger '+key,rows,change=lambda before=before,after=after:
              (scratch/'native.txt').write_text((scratch/'native.txt').read_text(encoding='utf-8')
                  .replace(before,after),encoding='utf-8'))
    check('failed input audit',rows,change=lambda:write_json('post-native-input-audit.json',dict(status='FAIL')))
    check('different production source',rows,change=lambda:write_json('baseline-provenance.json',dict(SourceDigest='c'*64)))
    check('missing cleanup terminal',rows,change=lambda:(scratch/'native.txt').write_text(
        (scratch/'native.txt').read_text(encoding='utf-8').replace('CLEANUP: current JVM contract classes and task-owned scratch removed.',''),encoding='utf-8'))
    check('refuse replacement of existing frozen output',rows,
          change=lambda:(scratch/'sealed-baselines.json').write_bytes(b'different synthetic frozen data'))
finally:
    assert scratch.parent == Path(tempfile.gettempdir()).resolve()
    assert not scratch.is_symlink() and not scratch.is_junction()
    entries = list(scratch.iterdir())
    assert {p.name for p in entries} <= names
    assert all(p.is_file() and not p.is_symlink() and not p.is_junction() for p in entries)
    for path in entries:
        path.unlink()
    scratch.rmdir()

(PACKET/'sealer-canaries.json').write_bytes((json.dumps(dict(status='PASS',
    scope='Synthetic sealer/schema boundary only; no actual native or electrical proof and no real baseline values read',
    scratchCleanup='Exact owned files and empty verified OS-temp directory removed',cases=results),indent=2)+'\n').encode('utf-8'))
print('PASS: synthetic sealer canaries='+str(len(results)))
