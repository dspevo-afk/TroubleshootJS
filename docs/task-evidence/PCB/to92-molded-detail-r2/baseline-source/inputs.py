"""Freeze the current published paint owner before the next bounded edit."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
BASE='41b678b5034deda1a451c0175aa92aaf41d35efd'
OWNER='src/com/lushprojects/circuitjs1/client/StandardPhysicalPartRenderProviders.java'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==BASE
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    rejected=HERE.parent/'to92-molded-detail-r1'
    assert read(rejected/'source-review-blocker.json')['status']=='FAIL_SOURCE_REVIEW_CANDIDATE_NOT_ACCEPTED'
    assert read(rejected/'player-resource-cleanup.json')['status']=='PASS_CLEANUP_OF_SOURCE_REJECTED_CANDIDATE'
    assert read(rejected/'production-restoration.json')['restoredPublishedSha256']==sha(ROOT/OWNER)
    prior=HERE.parent/'capacitor-lid-detail-r1';pub=read(prior/'publication.json')
    assert pub['status']=='PASS_VERIFIED_PUBLICATION' and pub['sha']==BASE and pub['productionOwnerRawExactlyTested'] is True
    assert read(prior/'postpublication-execution.json')==read(prior/'player-expected-execution.json')
    inventory=HERE.parent/'sparse-board-diagnostic-r1/baseline-inputs.json';old=read(inventory)
    files=dict(old['files']);files.update(read(prior/'candidate-r2-inputs.json')['changedSources'])
    assert len(files)==1482 and {n:sha(ROOT/n) for n in files}==files
    assert len(old['machinery'])==24 and {n:sha(ROOT/n) for n in old['machinery']}==old['machinery']
    java={p.relative_to(ROOT).as_posix() for folder in ('src','tests/contracts') for p in (ROOT/folder).rglob('*.java')}
    assert java=={n for n in files if n.endswith('.java') and n.startswith(('src/','tests/contracts/'))}
    return {'baseCommit':BASE,'sourceInventorySealSha256':sha(inventory),'priorCandidateSealSha256':sha(prior/'candidate-r2-inputs.json'),
            'priorPublicationSha256':sha(prior/'publication.json'),'rejectedAttempt':{n:sha(rejected/n) for n in ('candidate-inputs.json','source-review-blocker.json','player-resource-cleanup.json','production-restoration.json')},'owner':OWNER,'ownerSha256':sha(ROOT/OWNER),
            'files':files,'machinery':old['machinery'],'gates':{n:sha(HERE/n) for n in ('scope.md','inputs.py')}}
def main():
    command,=sys.argv[1:];assert command in ('freeze','audit');value=record();target=HERE/'baseline-inputs.json'
    if command=='freeze':
        assert not target.exists();archive=HERE/'baseline-source';archive.mkdir()
        for n in value['gates']:(archive/n).write_bytes((HERE/n).read_bytes())
        (archive/'StandardPhysicalPartRenderProviders.java').write_bytes((ROOT/OWNER).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n')
    assert value==read(target)
    assert sha(HERE/'baseline-source/StandardPhysicalPartRenderProviders.java')==value['ownerSha256']
    assert all(sha(HERE/'baseline-source'/n)==h for n,h in value['gates'].items())
    print('PASS TO92 molded detail baseline source1482 machinery24 owner+helpers2')
if __name__=='__main__':main()
