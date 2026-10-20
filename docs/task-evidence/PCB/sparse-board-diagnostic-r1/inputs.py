"""Audit the source-only diagnosis boundary against the published packing3 build."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
BASE='e877c2cf6a790804dbb5a64693ab799ee9e7ea1d'
GATES=('scope.md','inputs.py')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==BASE
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    old=HERE.parent/'packing-via-cap-r1'
    published=read(old/'publication.json');assert published['status']=='PASS_VERIFIED_PUBLICATION' and published['sha']==BASE
    accepted=read(old/'final/candidate-inputs.json')
    files={n:sha(ROOT/n) for n in accepted['files']}
    assert len(files)==1482 and files==accepted['files']
    java={p.relative_to(ROOT).as_posix() for folder in ('src','tests/contracts') for p in (ROOT/folder).rglob('*.java')}
    assert java=={n for n in files if n.endswith('.java') and n.startswith(('src/','tests/contracts/'))}
    paths=subprocess.check_output(['git','ls-files','-z','--','scripts','*.xml','*.jar','.classpath','*.properties'],cwd=ROOT).decode().rstrip('\0').split('\0')
    machinery={n:sha(ROOT/n) for n in sorted(set(paths))}
    assert len(machinery)==24 and machinery==accepted['machinery']
    assert read(old/'post-publication-provenance.json')==read(old/'post-player-provenance.json')
    return {'baseCommit':BASE,'files':files,'machinery':machinery,'gates':{n:sha(HERE/n) for n in GATES},
            'publishedEvidence':{n:sha(old/n) for n in ('publication.json','post-publication-provenance.json','final/candidate-inputs.json','player-assessment.json','rc-public-identity.json','composed-public-identity.json','rc-generated-fitted.jpg','composed-generated-fitted.jpg')}}
def main():
    command,=sys.argv[1:];assert command in ('freeze','audit')
    value=record();target=HERE/'baseline-inputs.json'
    if command=='freeze':
        assert not target.exists();(HERE/'baseline-source').mkdir()
        for n in GATES:(HERE/'baseline-source'/n).write_bytes((HERE/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n')
    assert read(target)==value
    assert {p.name:sha(p) for p in (HERE/'baseline-source').iterdir()}==value['gates']
    print('PASS sparse diagnosis unchanged published source1482 machinery24 helpers2')
if __name__=='__main__':main()
