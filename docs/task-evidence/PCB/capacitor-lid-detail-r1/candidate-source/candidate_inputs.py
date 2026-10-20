"""Freeze the one-owner paint candidate and finite final gates."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
GATES=('scope.md','candidate_inputs.py','run_phase.ps1','record_exit.ps1','qualify.py','suites.json','compare_census.py','census-reader-notes.md')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    baseline=read(HERE/'baseline-inputs.json');owner=baseline['owner']
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==baseline['baseCommit']
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    assert all(sha(HERE/n)==h and sha(HERE/'baseline-source'/n)==h for n,h in baseline['gates'].items())
    assert sha(HERE/'baseline-source/StandardPhysicalPartRenderProviders.java')==baseline['ownerSha256']
    inventory=HERE.parent/'sparse-board-diagnostic-r1/baseline-inputs.json';assert sha(inventory)==baseline['sourceInventorySealSha256']
    old=read(inventory);files={n:sha(ROOT/n) for n in old['files']};assert len(files)==1482
    assert [n for n in files if files[n]!=old['files'][n]]==[owner]
    java={p.relative_to(ROOT).as_posix() for folder in ('src','tests/contracts') for p in (ROOT/folder).rglob('*.java')}
    assert java=={n for n in files if n.endswith('.java') and n.startswith(('src/','tests/contracts/'))}
    assert len(old['machinery'])==24 and {n:sha(ROOT/n) for n in old['machinery']}==old['machinery']
    return {'baseCommit':baseline['baseCommit'],'baselineSealSha256':sha(HERE/'baseline-inputs.json'),
            'sourceInventorySealSha256':sha(inventory),'changedSources':{owner:files[owner]},
            'allCompilerInputsDigest':hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
            'sourceCount':1482,'machineryCount':24,'gates':{n:sha(HERE/n) for n in GATES},
            'baselineCensusSha256':sha(HERE.parent/'packing-via-cap-r1/final/census-receipt.txt')}
def main():
    command,=sys.argv[1:];assert command in ('freeze','audit')
    value=record();target=HERE/'candidate-inputs.json';archive=HERE/'candidate-source'
    if command=='freeze':
        assert not target.exists() and not archive.exists();archive.mkdir()
        for n in GATES:(archive/n).write_bytes((HERE/n).read_bytes())
        (archive/'StandardPhysicalPartRenderProviders.java').write_bytes((ROOT/next(iter(value['changedSources']))).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n')
    assert value==read(target)
    assert {p.name:sha(p) for p in archive.iterdir()}==dict(value['gates'],**{'StandardPhysicalPartRenderProviders.java':next(iter(value['changedSources'].values()))})
    print('PASS final capacitor lid candidate source1482 exactly one paint owner machinery24 helpers8')
if __name__=='__main__':main()
