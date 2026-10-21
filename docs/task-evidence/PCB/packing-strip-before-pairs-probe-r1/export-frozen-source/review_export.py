"""Review actual relay host operations and independent exported observations."""
import argparse,hashlib,json,math,re,runpy
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
AUDIT='PASS strip-before-pairs source1482 machinery24 sealed helpers private5'
KINDS=('seed','family','previous-family','generator','planner','compactor')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def require(ok,message):
    if not ok:raise AssertionError(message)
def phase(name):
    p=HERE/name;t=read(p/'timing.json');launch=read(p/'process-launch.json');cleanup=read(p/'process-cleanup.json')
    require(t['phase']==launch['phase']==name and t['status']=='PASS' and type(t['exitCode']) is int and t['exitCode']==0 and t['failure'] is None,'actual successful phase '+name)
    require(type(t['elapsedSeconds']) in (int,float) and math.isfinite(t['elapsedSeconds']) and t['elapsedSeconds']>0 and t['terminalCleanupIncluded'] is True and t['cleanupDuration']=='NOT_SEPARATELY_MEASURED','phase operation duration '+name)
    require(cleanup['status']=='PASS' and type(cleanup['actualExecExitCode']) is int and cleanup['actualExecExitCode']==0 and cleanup['launchSha256']==sha(p/'process-launch.json'),'actual exit and cleanup launch seal '+name)
    require(len(launch['drivers'])==len(cleanup['checks'])==2 and all(c['recordedBirthExited'] is True for c in cleanup['checks']),'two recorded driver exits '+name)
    require([{k:c[k] for k in ('pid','startTicks','executable')} for c in cleanup['checks']]==launch['drivers'],'exact birth correspondence '+name)
    for side in ('before','after'):require((p/('input-audit-'+side+'.txt')).read_text(encoding='utf-8-sig').strip()==AUDIT,'actual input audit '+name+' '+side)
    return t
def main(private):
    module=runpy.run_path(str(HERE/'export_inputs.py'));seal=module['record'](private)
    require(seal==read(HERE/'export-inputs.json'),'final runtime/private input seal')
    archive=HERE/'export-frozen-source';expected=dict(seal['gates'],**{'private-inputs/'+n:h for n,h in seal['privateInputs'].items()})
    require({f.relative_to(archive).as_posix():sha(f) for f in archive.rglob('*') if f.is_file()}==expected,'actual frozen byte inventory')
    expected_execution=read(HERE/'baseline-inputs.json')['expectedExecution']
    require(read(HERE/'execution-before.json')==read(HERE/'execution-after.json')==expected_execution,'actual six-field execution boundary')
    canary_timing=phase('canaries');canaries=read(HERE/'canaries/canaries.json')
    require([c['kind'] for c in canaries['checks']]==list(KINDS),'six ordered selected-host negatives')
    parameters={'seed':'TaskExportSeed','family':'TaskExportFamily','previous-family':'TaskExportFamily','generator':'TaskGeneratorSource','planner':'TaskPlannerSource','compactor':'TaskCompactorSource'}
    for c in canaries['checks']:
        require(set(c)=={'kind','status','actualAdapterExitCode','elapsedSeconds','privateLogSha256','privateLogBytes'} and c['status']=='PASS_REJECTED_AT_PARAMETER_BINDING' and type(c['actualAdapterExitCode']) is int and c['actualAdapterExitCode']==1 and type(c['elapsedSeconds']) in (int,float) and math.isfinite(c['elapsedSeconds']) and c['elapsedSeconds']>0,'actual rejected host binding '+c['kind'])
        path=Path(private)/('invalid-'+c['kind']+'.txt');raw=path.read_bytes();decoded=raw.decode('utf-16') if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else raw.decode('utf-8-sig')
        require(sha(path)==c['privateLogSha256'] and len(raw)==c['privateLogBytes'] and ("Cannot validate argument on parameter '"+parameters[c['kind']]+"'") in decoded and re.search(r'CURRENT_CONTRACT_|javac|PASS: focused current contracts',decoded) is None,'actual private negative log '+c['kind'])
    require(not (HERE/'canaries/native.txt').exists() and not (HERE/'canaries/receipt.txt').exists(),'no native work from invalid bindings')
    timing=phase('variant');native=(HERE/'variant/native.txt').read_text(encoding='utf-8-sig')
    marker='PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.'
    require(native.count(marker)==1 and 'CURRENT_CONTRACT_FAILURE:' not in native and 'CURRENT_CONTRACT_CLEANUP:' not in native,'actual maintained native completion')
    reader=runpy.run_path(str(HERE/'qualify.py'));observation=reader['validate_comparison']((HERE/'variant/receipt.txt').read_text(encoding='utf-8-sig'))
    require(isinstance(observation,dict) and observation['status'] in ('GO_PRIVATE_TARGET_ONLY','NO_GO_PRIVATE_TARGET') and observation['variant']['identity']['replayId']==seal['request'],'independent relay observation')
    result={'status':'PASS_FINITE_PRIVATE_STRIP_BEFORE_PAIRS_EXPERIMENT','inputSealSha256':sha(HERE/'export-inputs.json'),'receiptSha256':sha(HERE/'variant/receipt.txt'),'nativeSha256':sha(HERE/'variant/native.txt'),'sourceReviewSha256':sha(HERE/'source-review.json'),'execution':expected_execution,'canaries':{'elapsedSeconds':canary_timing['elapsedSeconds'],'checks':canaries['checks']},'nativeOperationSeconds':timing['elapsedSeconds'],'driverBirthsSeparatelyExited':4,'observation':observation,'comparisonNegativeControls':reader['negatives']((HERE/'variant/receipt.txt').read_text(encoding='utf-8-sig')),'limits':'One exact ordinary relay replay, private P05 strip before unchanged pair pass. Exact routed SOURCE and original routing stream; shared placement cap and actual boundary fixtures, original graph and accepted baseline tuple checked. Target GO requires broader qualification including all four prior optional wins; no production promotion, optimum, all-seed, new electrical, fresh matrix/GWT/browser/repair proof. Prior visual cleanup remains BLOCKED; five retained profiles untouched.'}
    target=HERE/'assessment.json';require(not target.exists(),'preserve previous assessment')
    with target.open('x',encoding='utf-8',newline='\n') as f:json.dump(result,f,indent=2);f.write('\n')
    print('PASS strip-before-pairs experiment; exact source, budgets and independent metrics; private target result only')
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--private-root',required=True,type=Path);main(parser.parse_args().private_root)
