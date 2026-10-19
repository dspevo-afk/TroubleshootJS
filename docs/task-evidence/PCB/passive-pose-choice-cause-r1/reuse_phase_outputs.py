"""Reuse exact completed native bytes only across the declared reader-only fix."""
import hashlib
import json
import stat
from pathlib import Path
HERE=Path(__file__).resolve().parent
OLD=HERE/'attempt1'
COPY=Path(json.loads((HERE/'local-state.json').read_text())['scratch']).resolve()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
archive=read(OLD/'archive-sha256.json')
assert archive['status']=='PASS'
for name,digest in archive['files'].items():assert sha(OLD/name)==digest,'Preserved failure changed'
old=read(OLD/'native-inputs.json');new=read(HERE/'native-inputs.json')
assert old['copyCount']==new['copyCount']==1508
for key in ('baseSha256','overrides','additions','main1475SealSha256',
            'reusedPolicySealSha256','sourceStudySha256'):
    assert old[key]==new[key],'Native consumed input changed: '+key
changed={name for name,digest in old['gateOwners'].items() if new['gateOwners'][name]!=digest}
allowed={'assess.py','check_assessment.py','assessment-canaries.json',
         'assessment-canary-results.json','reader-notes.md','inputs.py','preserve_attempt.py'}
assert changed<=allowed and {'assess.py','check_assessment.py'}<=changed
assert set(new['gateOwners'])-set(old['gateOwners'])=={'reader-correction.json','reuse_phase_outputs.py'}
for name,digest in new['gateOwners'].items():assert sha(HERE/name)==digest
for name,digest in dict(new['overrides'],**new['additions']).items():
    assert sha(COPY/name)==sha(HERE/'source'/name)==digest
base=read(HERE/'copy-inputs.json')
expected={r['path']:r['sha256'] for r in base['files']}
expected.update(new['overrides']);expected.update(new['additions'])
actual={}
for path in COPY.rglob('*'):
    assert not path.is_symlink() and not path.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    assert path.resolve().is_relative_to(COPY)
    if path.is_file():actual[path.relative_to(COPY).as_posix()]=sha(path)
assert actual==expected and len(actual)==1508
assert read(HERE/'native-input-audit.json')['status']=='PASS'
assert read(HERE/'assessment-canary-results.json')['status']=='PASS'
copied={}
for phase in ('unit','routes'):
    assert read(OLD/(phase+'-timing.json'))['status']=='PASS'
    cleanup=read(OLD/(phase+'-process-cleanup.json'))
    assert cleanup['status']=='PASS' and not cleanup['terminationAttempted']
    assert len(cleanup['drivers'])==2 and all(not row['sameCreationStillRunning'] for row in cleanup['drivers'])
    assert read(OLD/(phase+'-log-sanitization.json'))['status']=='PASS'
    for suffix in ('.txt','-receipt.txt','-timing.json','-process-launch.json',
                   '-process-cleanup.json','-log-sanitization.json'):
        name=phase+suffix;target=HERE/name
        assert not target.exists() and sha(OLD/name)==archive['files'][name]
        target.write_bytes((OLD/name).read_bytes())
        assert sha(target)==archive['files'][name]
        copied[name]=sha(target)
(HERE/'native-evidence-reuse.json').write_text(json.dumps({'status':'PASS',
 'scope':'Exact native phase bytes reused across a source-derived reader correction; no new native execution',
 'oldSealSha256':sha(OLD/'native-inputs.json'),'newSealSha256':sha(HERE/'native-inputs.json'),
 'unchangedNativeInputs':1508,'nativeSourceChanged':False,
 'changedGateOwners':sorted(changed),'addedGateOwners':['reader-correction.json','reuse_phase_outputs.py'],
 'nativeDriversOriginallyVerifiedExited':4,'phaseArtifactsSha256':copied},indent=2)+'\n')
print('PASS exact native1508 and12 phase artifacts reused; reader-only boundary audited; no new native run')
