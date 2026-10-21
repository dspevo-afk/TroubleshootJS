"""Seal the six owned source deltas and exact final gate inputs."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
CHANGED=('src/com/lushprojects/circuitjs1/client/PcbPlacementConstraints.java',
         'src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java',
         'src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java',
         'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java',
         'tests/contracts/PcbConnectedPocketPackingContractTest.java',
         'tests/contracts/PcbNormalPackingIdentityContractTest.java')
GATES=('scope.md','candidate_inputs.py','run_phase.ps1','record_exit.ps1','qualify.py',
       'suites.json','compare_census.py','census-reader-notes.md','target_reader.py','final-source-review.json','host-canary.json',
       'reader-controls.txt','reader-control-operation.json')
TARGET_EXPORT='docs/task-evidence/PCB/paired-pocket-probe-r1/export-source/SparseNormalBoardDiagnostic.java'
TARGET_READER='docs/task-evidence/PCB/npn-sparse-diagnostic-r1/qualify.py'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record(adapter):
    b=read(HERE/'baseline-inputs.json')
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==b['baseCommit']
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    assert sha(HERE/'scope.md')==b['scopeSha256']
    files={n:sha(ROOT/n) for n in b['files']};assert len(files)==1482
    assert {n for n in files if files[n]!=b['files'][n]}==set(CHANGED)
    java={p.relative_to(ROOT).as_posix() for f in ('src','tests/contracts') for p in (ROOT/f).rglob('*.java')}
    assert java=={n for n in files if n.endswith('.java') and n.startswith(('src/','tests/contracts/'))}
    assert len(b['machinery'])==24 and {n:sha(ROOT/n) for n in b['machinery']}==b['machinery']
    assert {n:sha(ROOT/n) for n in b['publishedInputs']}==b['publishedInputs']
    for n,h in b['publishedInputs'].items():assert hashlib.sha256(subprocess.check_output(['git','show',b['baseCommit']+':'+n],cwd=ROOT)).hexdigest()==h,n
    assert sha(adapter)=='a079fd62c07d9790a73884ae8f284e60ff60e7122e4b92711033a64d502c4247'
    assert sha(ROOT/TARGET_EXPORT)=='55658de039f165c621f8062a3b938b8d4e8d16adf1d3009c8dfa975fdf327eef'
    review=read(HERE/'final-source-review.json')
    assert review['status']=='PASS_SOURCE_ONLY' and review['changedSources']=={n:files[n] for n in CHANGED}
    assert review['presealFindingSha256']==sha(HERE/'preseal-review-findings.json')
    canary=read(HERE/'host-canary.json')
    assert canary['status']=='PASS_REJECTED_MISSING_ADAPTER_AT_PARAMETER_BINDING' and canary['actualNativeExitCode']==1 and canary['runnerSha256']==sha(HERE/'run_phase.ps1')
    controls=read(HERE/'reader-control-operation.json')
    assert controls['status']=='PASS' and controls['actualExecExitCode']==0 and controls['readerSha256']==sha(HERE/'compare_census.py') and controls['outputSha256']==sha(HERE/'reader-controls.txt')
    return {'baseCommit':b['baseCommit'],'baselineSealSha256':sha(HERE/'baseline-inputs.json'),
            'files':files,'sourceCount':1482,'machinery':b['machinery'],
            'changedSources':{n:files[n] for n in CHANGED},
            'allCompilerInputsDigest':hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
            'gates':{n:sha(HERE/n) for n in GATES},'targetAdapterSha256':sha(adapter),
            'publishedTargetExporter':{TARGET_EXPORT:sha(ROOT/TARGET_EXPORT)},
            'publishedTargetReader':{TARGET_READER:sha(ROOT/TARGET_READER)},
            'packingInterpretation':4,'receiptSchema':'normal-packing@3','layoutAlgorithmVersion':19}
if __name__=='__main__':
    command,folder=sys.argv[1:];assert command in ('freeze','audit');value=record(Path(folder));target=HERE/'candidate-inputs.json'
    if command=='freeze':
        archive=HERE/'candidate-source';assert not archive.exists();archive.mkdir()
        for n in GATES:
            p=archive/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((HERE/n).read_bytes())
        for n in CHANGED:
            p=archive/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/n).read_bytes())
        with target.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
    assert value==read(target)
    expected=dict(value['gates'],**value['changedSources']);archive=HERE/'candidate-source'
    assert {p.relative_to(archive).as_posix():sha(p) for p in archive.rglob('*') if p.is_file()}==expected
    print('PASS paired packing final source1482 six owned deltas machinery24 sealed gates/target inputs')
