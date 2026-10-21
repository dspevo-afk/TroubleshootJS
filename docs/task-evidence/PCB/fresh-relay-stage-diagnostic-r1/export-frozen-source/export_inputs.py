"""Seal actual private5 relay diagnostic sources and all runtime readers/helpers."""
import hashlib,json,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
UTIL=runpy.run_path(str(HERE/'inputs.py'));read,sha=UTIL['read'],UTIL['sha']
GATES=('initial-sizing-advice.json','scope-clarification.md','preparation-review.json','relay_binding_patch.py','binding-notes.md',
       'prepare_sources.py','private-inputs.json','exporter-diff.txt','adapter-diff.txt',
       'host-helper-review.json','run-helper-diff.txt','run_export.ps1','record_exit.ps1',
       'observe_execution.ps1','source-review.json','qualify.py','reader-scope.md',
       'reader-preseal-review.json','review_export.py','export_inputs.py')
def record(private_root):
    baseline=UTIL['record']();assert baseline==read(HERE/'baseline-inputs.json')
    assert {f.name:sha(f) for f in (HERE/'baseline-source').iterdir()}==baseline['gates']
    manifest=read(HERE/'private-inputs.json');assert manifest['status']=='PREPARED_FINITE_RELAY_PASSIVE_CONTROL'
    assert manifest['baseCommit']==baseline['baseCommit'] and len(manifest['privateInputs'])==5
    assert set(manifest['privateInputs'])==set(UTIL['NAMES']) and manifest['borrowedPrivateSources']==baseline['borrowedPrivateSources']
    assert {n:sha(Path(private_root)/n) for n in manifest['privateInputs']}==manifest['privateInputs']
    assert len(manifest['changes'])==4 and all(x['replacements']==1 for x in manifest['changes'])
    for n in manifest['privateInputs']:
        if n.startswith('control/'):assert manifest['privateInputs'][n]==manifest['borrowedPrivateSources'][n]
    review=read(HERE/'source-review.json')
    assert review['status']=='PASS_PRIVATE_RELAY_BINDING_SOURCE_ONLY' and review['manifestSha256']==sha(HERE/'private-inputs.json')
    assert review['privateInputs']==manifest['privateInputs']
    return {'baseCommit':baseline['baseCommit'],'baselineSealSha256':sha(HERE/'baseline-inputs.json'),
        'request':baseline['request'],'privateInputs':manifest['privateInputs'],
        'gates':{n:sha(HERE/n) for n in GATES}}
if __name__=='__main__':
    command,private_root=sys.argv[1:];assert command in ('freeze','audit');value=record(private_root)
    target,archive=HERE/'export-inputs.json',HERE/'export-frozen-source'
    if command=='freeze':
        assert not target.exists() and not archive.exists();archive.mkdir()
        for n in GATES:
            f=archive/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes((HERE/n).read_bytes())
        for n in value['privateInputs']:
            f=archive/'private-inputs'/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes((Path(private_root)/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    assert value==read(target)
    expected=dict(value['gates'],**{'private-inputs/'+n:h for n,h in value['privateInputs'].items()})
    assert {f.relative_to(archive).as_posix():sha(f) for f in archive.rglob('*') if f.is_file()}==expected
    print('PASS fresh relay export source1482 machinery24 sealed helpers private5')
