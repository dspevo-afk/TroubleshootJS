"""Freeze the private native substitution and all observation/decision helpers."""
import hashlib,json,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
GATES=('experiment-scope.md','make_experiment.py','private-input-manifest.json','owner-diff.txt','adapter-diff.txt',
       'run_experiment.ps1','record_exit.ps1','experiment_inputs.py','qualify_experiment.py','reader-scope.md')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    baseline=runpy.run_path(str(HERE/'inputs.py'))['record']()
    assert baseline==read(HERE/'baseline-inputs.json')
    previous=HERE.parent/'sparse-board-diagnostic-r1'
    manifest=read(HERE/'private-input-manifest.json')
    sources=read(previous/'baseline-inputs.json')
    assert manifest['publishedOwnerSha256']==sources['files'][manifest['publishedOwner']]
    assert len(manifest['ownerChanges'])==len(manifest['adapterChanges'])==3
    assert manifest['reusedExporterSha256']==sha(previous/'export-source/SparseNormalBoardDiagnostic.java')
    return {'baselineSealSha256':sha(HERE/'baseline-inputs.json'),'baseCommit':baseline['baseCommit'],
            'gates':{n:sha(HERE/n) for n in GATES},
            'reusedReadOnlyValidatorSha256':sha(previous/'qualify.py'),'reusedExporterSha256':manifest['reusedExporterSha256'],
            'privateOwnerSha256':manifest['privateOwnerSha256'],'privateAdapterSha256':manifest['privateAdapterSha256'],
            'request':'tsj-alpha/6/MEDIUM/COMPOSED_CONTROLLED_INDICATOR/7141843758046216173',
            'interpretation':'TASK_ONLY_SECOND_SWEEP_NOT_CURRENT_PRODUCTION_REPLAY'}
def main():
    command,=sys.argv[1:];assert command in ('freeze','audit')
    value=record();target=HERE/'experiment-inputs.json';archive=HERE/'experiment-frozen-source'
    if command=='freeze':
        assert not target.exists() and not archive.exists()
        for n in GATES:
            d=archive/n;d.parent.mkdir(parents=True,exist_ok=True);d.write_bytes((HERE/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n')
    assert value==read(target)
    assert {p.relative_to(archive).as_posix():sha(p) for p in archive.rglob('*') if p.is_file()}==value['gates']
    print('PASS private second-sweep experiment unchanged published source1482 machinery24 helpers10 and exact supplemental inputs')
if __name__=='__main__':main()
