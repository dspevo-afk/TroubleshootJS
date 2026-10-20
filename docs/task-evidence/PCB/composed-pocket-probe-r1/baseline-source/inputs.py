"""Freeze the read-only scope over the already published source inventory."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
BASE='395fe5d87efabf2b189181374b11fff489b15dce'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==BASE
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    prior=HERE.parent/'sparse-board-diagnostic-r1'
    assert read(prior/'publication.json')['status']=='PASS_VERIFIED_PUBLICATION' and read(prior/'publication.json')['sha']==BASE
    baseline=read(prior/'baseline-inputs.json')
    for key,count in (('files',1482),('machinery',24)):
        assert len(baseline[key])==count
        assert {n:sha(ROOT/n) for n in baseline[key]}==baseline[key]
    java={p.relative_to(ROOT).as_posix() for folder in ('src','tests/contracts') for p in (ROOT/folder).rglob('*.java')}
    assert java=={n for n in baseline['files'] if n.endswith('.java') and n.startswith(('src/','tests/contracts/'))}
    assert read(prior/'assessment.json')['status']=='PASS_EXACT_SELECTED_NORMAL_BOARD_DIAGNOSIS'
    return {'baseCommit':BASE,'priorSourceInventorySealSha256':sha(prior/'baseline-inputs.json'),
            'publishedEvidence':{n:sha(prior/n) for n in ('publication.json','assessment.json','composed/receipt.txt','execution-reuse-audit.json')},
            'gates':{n:sha(HERE/n) for n in ('scope.md','inputs.py')}}
def main():
    command,=sys.argv[1:];assert command in ('freeze','audit')
    value=record();target=HERE/'baseline-inputs.json'
    if command=='freeze':
        assert not target.exists();(HERE/'baseline-source').mkdir()
        for n in value['gates']:(HERE/'baseline-source'/n).write_bytes((HERE/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n')
    assert value==read(target)
    assert {p.name:sha(p) for p in (HERE/'baseline-source').iterdir()}==value['gates']
    print('PASS bounded pocket scope unchanged published source1482 machinery24 helpers2')
if __name__=='__main__':main()
