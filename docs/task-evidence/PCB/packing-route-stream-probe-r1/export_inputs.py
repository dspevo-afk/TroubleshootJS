"""Freeze the finite optional-stream private sources, independent vectors and helpers."""
import hashlib,json,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
UTIL=runpy.run_path(str(HERE/'inputs.py'));read,sha=UTIL['read'],UTIL['sha']
GATES=('prepare_sources.py','seed_reference.py','source-review.json','private-inputs.json',
       'variant-generator-diff.txt','exporter-diff.txt','derivation-vectors.json',
       'preparation-notes.md','host-helper-review.json','run_export.ps1','record_exit.ps1',
       'observe_execution.ps1','qualify.py','review_export.py','review-helper-adaptation.json','preseal-review.json','export_inputs.py')
NAMES={'common/SparseNormalBoardDiagnostic.java','common/verify-two-pad-span.ps1',
       'variant/SeededPcbLayoutGenerator.java','variant/PcbPlacementPlanner.java','variant/PcbPlacementCompactor.java'}
def record(private):
    baseline=UTIL['record']();assert baseline==read(HERE/'baseline-inputs.json')
    manifest=read(HERE/'private-inputs.json')
    assert manifest['status']=='PREPARED_FIXED_OPTIONAL_P05_STREAM'
    assert manifest['baseCommit']==baseline['baseCommit'] and manifest['baselineSealSha256']==sha(HERE/'baseline-inputs.json')
    assert manifest['request']==baseline['request'] and manifest['candidateInterpretation']==baseline['candidateInterpretation']
    assert manifest['controlPrivateInputs']==baseline['controlPrivateInputs']
    assert len(manifest['changes'])==4 and all(c['replacements']==1 for c in manifest['changes'])
    assert len(manifest['checks'])==10 and all(v=='PASS' for v in manifest['checks'].values())
    assert set(manifest['privateInputs'])==NAMES
    assert {n:sha(Path(private)/n) for n in NAMES}==manifest['privateInputs']
    control=baseline['controlPrivateInputs']
    for n in ('PcbPlacementPlanner.java','PcbPlacementCompactor.java'):
        assert manifest['privateInputs']['variant/'+n]==control['control/'+n]
    assert manifest['privateInputs']['common/verify-two-pad-span.ps1']==control['common/verify-two-pad-span.ps1']
    vectors=read(HERE/'derivation-vectors.json');reference=runpy.run_path(str(HERE/'seed_reference.py'))
    assert manifest['derivationVectorsSha256']==sha(HERE/'derivation-vectors.json')
    assert {k:vectors[k] for k in baseline['derivation']}==baseline['derivation']
    assert [v['sourceSeed'] for v in vectors['vectors']]==['0','1','-1',str(-(1<<63)),str((1<<63)-1),vectors['actualSourceSeed']]
    assert all(str(reference['derive_seed'](int(v['sourceSeed'])))==v['derivedSeed'] for v in vectors['vectors'])
    assert vectors['actualDerivedSeed']==str(reference['derive_seed'](int(vectors['actualSourceSeed'])))
    review=read(HERE/'source-review.json')
    assert review['status']==manifest['status'] and review['privateInputs']==manifest['privateInputs']
    assert review['checks']==manifest['checks'] and review['derivationVectors']==vectors and review['pythonPreparationExit']==0
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
    print('PASS fixed optional stream source1482 machinery24 sealed helpers private5')
