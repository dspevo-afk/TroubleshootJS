"""Freeze one paint owner against the unchanged published packing3 inventory."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
BASE='7fd882552405561dc156ebf240be475b2c2ee2d6'
OWNER='src/com/lushprojects/circuitjs1/client/StandardPhysicalPartRenderProviders.java'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==BASE
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    previous=HERE.parent/'composed-pocket-probe-r1';published=read(previous/'publication.json')
    assert published['status']=='PASS_VERIFIED_PUBLICATION' and published['sha']==BASE and published['privateExperimentalOwnerNotPromoted'] is True
    prior=HERE.parent/'sparse-board-diagnostic-r1';baseline=read(prior/'baseline-inputs.json')
    assert len(baseline['files'])==1482 and {n:sha(ROOT/n) for n in baseline['files']}==baseline['files']
    assert len(baseline['machinery'])==24 and {n:sha(ROOT/n) for n in baseline['machinery']}==baseline['machinery']
    java={p.relative_to(ROOT).as_posix() for folder in ('src','tests/contracts') for p in (ROOT/folder).rglob('*.java')}
    assert java=={n for n in baseline['files'] if n.endswith('.java') and n.startswith(('src/','tests/contracts/'))}
    return {'baseCommit':BASE,'sourceInventorySealSha256':sha(prior/'baseline-inputs.json'),
            'priorPublicationSha256':sha(previous/'publication.json'),'owner':OWNER,'ownerSha256':sha(ROOT/OWNER),
            'gates':{n:sha(HERE/n) for n in ('scope.md','inputs.py')}}
def main():
    command,=sys.argv[1:];assert command in ('freeze','audit')
    value=record();target=HERE/'baseline-inputs.json'
    if command=='freeze':
        assert not target.exists();archive=HERE/'baseline-source';archive.mkdir()
        for n in value['gates']:(archive/n).write_bytes((HERE/n).read_bytes())
        (archive/'StandardPhysicalPartRenderProviders.java').write_bytes((ROOT/OWNER).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n')
    assert value==read(target)
    assert sha(HERE/'baseline-source/StandardPhysicalPartRenderProviders.java')==value['ownerSha256']
    assert all(sha(HERE/'baseline-source'/n)==h for n,h in value['gates'].items())
    print('PASS capacitor paint baseline source1482 machinery24 owner+helpers2')
if __name__=='__main__':main()
