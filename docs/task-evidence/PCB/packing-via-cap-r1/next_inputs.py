"""Separate frozen helper revision; original baseline/failed attempt stays intact."""
import hashlib,json,runpy,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
GATES=('revised-scope.md','next_inputs.py','run_fixture_r2.ps1','record_next_exit.ps1',
       'qualify_next.py','run_target.ps1','make_export_adapter.py','adapter-manifest.json',
       'adapter-diff.txt','diagnostic-source/FreshRb30GapDiagnostic.java')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record(phase):
    assert phase in ('fixture-r2','target-run')
    original=runpy.run_path(str(HERE/'inputs.py'))
    baseline=read(HERE/'baseline-inputs.json')
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==baseline['baseCommit']
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    files={n:sha(ROOT/n) for n in baseline['files']}
    assert len(files)==1482 and {n for n in files if files[n]!=baseline['files'][n]}==original['OWNERS']
    java={p.relative_to(ROOT).as_posix() for folder in ('src','tests/contracts') for p in (ROOT/folder).rglob('*.java')}
    assert java=={n for n in files if n.endswith('.java') and n.startswith(('src/','tests/contracts/'))}
    assert {n:sha(ROOT/n) for n in baseline['machinery']}==baseline['machinery']
    assert {n:sha(HERE/n) for n in original['GATES']}==baseline['gates']
    adapter=read(HERE/'adapter-manifest.json')
    assert adapter['maintainedSourceSha256']==baseline['machinery']['scripts/verify-current-contracts.ps1']
    assert adapter['generatedSha256']=='19af62225c5b3b4242343c81ebfa34a707df572eb1db80af5038d5ffdaa1dbec'
    assert len(adapter['changes'])==5 and all(x['replacements']==1 for x in adapter['changes'])
    return {'phase':phase,'baseCommit':baseline['baseCommit'],'files':files,'machinery':baseline['machinery'],
            'gates':{n:sha(HERE/n) for n in GATES},'baselineSealSha256':sha(HERE/'baseline-inputs.json'),
            'failedFixtureSealSha256':sha(HERE/'fixture-attempt1/candidate-inputs.json')}
def main():
    command,phase=sys.argv[1:];assert command in ('freeze','audit')
    value=record(phase);attempt=HERE/phase
    if command=='freeze':
        assert not attempt.exists();attempt.mkdir()
        for n in GATES:
            d=attempt/'source'/n;d.parent.mkdir(parents=True,exist_ok=True);d.write_bytes((HERE/n).read_bytes())
        for n in runpy.run_path(str(HERE/'inputs.py'))['OWNERS']:
            d=attempt/'candidate-source'/n;d.parent.mkdir(parents=True,exist_ok=True);d.write_bytes((ROOT/n).read_bytes())
        (attempt/'inputs.json').write_text(json.dumps(value,indent=2)+'\n')
    assert read(attempt/'inputs.json')==value
    assert {p.relative_to(attempt/'source').as_posix():sha(p) for p in (attempt/'source').rglob('*') if p.is_file()}==value['gates']
    assert all(sha(attempt/'candidate-source'/n)==filesha for n,filesha in value['files'].items() if (attempt/'candidate-source'/n).exists())
    print('PASS frozen via-cap '+phase+' source1482 machinery24 helpers10')
if __name__=='__main__':main()
