"""Seal the private strip-before-pairs sources, fixtures and actual-host helpers."""
import json,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
UTIL=runpy.run_path(str(HERE/'inputs.py'));read,sha=UTIL['read'],UTIL['sha']
GATES=('prepare_sources.py','private-inputs.json','source-review.json','generator-diff.txt',
       'compactor-diff.txt','exporter-diff.txt','preparation-notes.md','host-helper-review.json',
       'run_export.ps1','record_exit.ps1','observe_execution.ps1','qualify.py','review_export.py',
       'preseal-review.json','export_inputs.py')
NAMES={'common/SparseNormalBoardDiagnostic.java','common/verify-two-pad-span.ps1',
       'variant/SeededPcbLayoutGenerator.java','variant/PcbPlacementPlanner.java','variant/PcbPlacementCompactor.java'}
def record(private):
    baseline=UTIL['record']();assert baseline==read(HERE/'baseline-inputs.json')
    manifest=read(HERE/'private-inputs.json')
    assert manifest['status']=='PREPARED_PRIVATE_STRIP_BEFORE_PAIRS'
    assert manifest['baseCommit']==baseline['baseCommit'] and manifest['baselineSealSha256']==sha(HERE/'baseline-inputs.json')
    assert manifest['candidateInterpretation']==baseline['candidateInterpretation']
    assert len(manifest['changes'])==4 and all(c['replacements']==1 for c in manifest['changes'])
    assert manifest['controlPrivateInputs']==baseline['controlPrivateInputs']
    assert set(manifest['privateInputs'])==NAMES and {n:sha(Path(private)/n) for n in NAMES}==manifest['privateInputs']
    control=baseline['controlPrivateInputs']
    assert manifest['privateInputs']['variant/PcbPlacementPlanner.java']==control['control/PcbPlacementPlanner.java']
    assert manifest['privateInputs']['common/verify-two-pad-span.ps1']==control['common/verify-two-pad-span.ps1']
    review=read(HERE/'source-review.json')
    assert review['status']==manifest['status'] and review['privateInputs']==manifest['privateInputs']
    assert review['pythonPreparationExit']==0 and len(review['checks'])==11
    assert all(v=='PASS' for k,v in review['checks'].items() if k!='runtimeBudgetFixture')
    assert review['checks']['runtimeBudgetFixture']=='NOT RUN (Java/native leaf restriction)'
    return {'baseCommit':baseline['baseCommit'],'baselineSealSha256':sha(HERE/'baseline-inputs.json'),
        'request':baseline['request'],'privateInputs':manifest['privateInputs'],
        'gates':{n:sha(HERE/n) for n in GATES}}
def audit_archive(value):
    archive=HERE/'export-frozen-source'
    expected=dict(value['gates'],**{'private-inputs/'+n:h for n,h in value['privateInputs'].items()})
    assert {f.relative_to(archive).as_posix():sha(f) for f in archive.rglob('*') if f.is_file()}==expected
if __name__=='__main__':
    command,private=sys.argv[1:];assert command in ('freeze','audit');value=record(private)
    target,archive=HERE/'export-inputs.json',HERE/'export-frozen-source'
    if command=='freeze':
        assert not target.exists() and not archive.exists();archive.mkdir()
        for n in GATES:(archive/n).write_bytes((HERE/n).read_bytes())
        for n in value['privateInputs']:
            f=archive/'private-inputs'/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes((Path(private)/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    assert value==read(target);audit_archive(value)
    print('PASS strip-before-pairs source1482 machinery24 sealed helpers private5')
