"""Bind one TO92 paint delta and unchanged accepted census reader inputs."""
import hashlib,json,runpy,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
GATES=('scope.md','candidate_inputs.py','run_phase.ps1','record_exit.ps1','qualify.py','suites.json','compare_census.py')
EXTERNAL=('compare_census.py','census-reader-notes.md','reader-control-r2.json','reader-control-r2-operation.json')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    baseline=read(HERE/'baseline-inputs.json');owner=baseline['owner'];prior=HERE.parent/'capacitor-lid-detail-r1'
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==baseline['baseCommit']
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    assert all(sha(HERE/n)==h and sha(HERE/'baseline-source'/n)==h for n,h in baseline['gates'].items())
    assert sha(HERE/'baseline-source/StandardPhysicalPartRenderProviders.java')==baseline['ownerSha256']
    files={n:sha(ROOT/n) for n in baseline['files']};assert len(files)==1482
    assert [n for n in files if files[n]!=baseline['files'][n]]==[owner]
    review=read(HERE/'final-source-review.json')
    assert review['status']=='PASS_SOURCE_ONLY' and review['owner']==owner and review['ownerSha256']==files[owner]
    java={p.relative_to(ROOT).as_posix() for folder in ('src','tests/contracts') for p in (ROOT/folder).rglob('*.java')}
    assert java=={n for n in files if n.endswith('.java') and n.startswith(('src/','tests/contracts/'))}
    assert len(baseline['machinery'])==24 and {n:sha(ROOT/n) for n in baseline['machinery']}==baseline['machinery']
    external={n:sha(prior/n) for n in EXTERNAL}
    for n,h in external.items():
        committed=subprocess.check_output(['git','show',baseline['baseCommit']+':'+(prior/n).relative_to(ROOT).as_posix()],cwd=ROOT)
        assert hashlib.sha256(committed).hexdigest()==h,n
    control=read(prior/'reader-control-r2-operation.json')
    assert control['status']=='PASS' and control['actualExecExitCode']==0
    reader=runpy.run_path(str(prior/'compare_census.py'));reader_sources=reader['check_source_contract']()
    receipt=HERE.parent/'packing-via-cap-r1/final/census-receipt.txt'
    assert sha(receipt)==reader['BASELINE_SHA256']
    return {'baseCommit':baseline['baseCommit'],'baselineSealSha256':sha(HERE/'baseline-inputs.json'),'finalSourceReviewSha256':sha(HERE/'final-source-review.json'),
            'changedSources':{owner:files[owner]},'allCompilerInputsDigest':hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
            'sourceCount':1482,'machineryCount':24,'gates':{n:sha(HERE/n) for n in GATES},
            'externalPublishedReaderInputs':external,'readerProtectedSources':reader_sources,'baselineCensusSha256':sha(receipt),
            'readerReuseBoundary':'Accepted raw reader/control/notes and every source pin/baseline unchanged; old baseline-control evidence reused. Fresh comparison executes reader and all positive/negative canaries again.'}
def main():
    command,=sys.argv[1:];assert command in ('freeze','audit');value=record();target=HERE/'candidate-inputs.json';archive=HERE/'candidate-source'
    if command=='freeze':
        assert not target.exists() and not archive.exists();archive.mkdir()
        for n in GATES:(archive/n).write_bytes((HERE/n).read_bytes())
        (archive/'StandardPhysicalPartRenderProviders.java').write_bytes((ROOT/next(iter(value['changedSources']))).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n')
    assert value==read(target)
    assert {p.name:sha(p) for p in archive.iterdir()}==dict(value['gates'],**{'StandardPhysicalPartRenderProviders.java':next(iter(value['changedSources'].values()))})
    print('PASS final TO92 molded candidate source1482 exactly one paint owner machinery24 helpers7 reader reuse bound')
if __name__=='__main__':main()
