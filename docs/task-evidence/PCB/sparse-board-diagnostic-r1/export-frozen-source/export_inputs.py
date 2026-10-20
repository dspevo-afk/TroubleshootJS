"""Freeze task-only observation inputs over the unchanged published candidate."""
import hashlib,json,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
GATES=('scope.md','inputs.py','make_export_adapter.py','adapter-manifest.json','adapter-diff.txt',
       'export_inputs.py','run_export.ps1','record_exit.ps1','qualify.py','reader-scope.md',
       'export-source/SparseNormalBoardDiagnostic.java','export-source/NOTES.md')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    baseline=runpy.run_path(str(HERE/'inputs.py'))['record']()
    assert baseline==read(HERE/'baseline-inputs.json')
    adapter=read(HERE/'adapter-manifest.json')
    assert adapter['maintainedSourceSha256']==baseline['machinery']['scripts/verify-current-contracts.ps1']
    assert len(adapter['changes'])==5 and all(x['replacements']==1 for x in adapter['changes'])
    assert {p.name for p in (HERE/'export-source').iterdir() if p.is_file()}=={'SparseNormalBoardDiagnostic.java','NOTES.md'}
    return {'baselineSealSha256':sha(HERE/'baseline-inputs.json'),'baseCommit':baseline['baseCommit'],
            'gates':{n:sha(HERE/n) for n in GATES},'adapterSha256':adapter['generatedSha256'],
            'requests':{'rc':'tsj-alpha/6/EASY/RC_DELAY/-9187226374781659024',
                        'composed':'tsj-alpha/6/MEDIUM/COMPOSED_CONTROLLED_INDICATOR/7141843758046216173'}}
def main():
    command,=sys.argv[1:];assert command in ('freeze','audit')
    value=record();target=HERE/'export-inputs.json';archive=HERE/'export-frozen-source'
    if command=='freeze':
        assert not target.exists() and not archive.exists(),'Preserve prior export inputs'
        for n in GATES:
            d=archive/n;d.parent.mkdir(parents=True,exist_ok=True);d.write_bytes((HERE/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    assert value==read(target)
    assert {p.relative_to(archive).as_posix():sha(p) for p in archive.rglob('*') if p.is_file()}==value['gates']
    print('PASS sparse export unchanged published source1482 machinery24 frozen helpers'+str(len(GATES)))
if __name__=='__main__':main()
