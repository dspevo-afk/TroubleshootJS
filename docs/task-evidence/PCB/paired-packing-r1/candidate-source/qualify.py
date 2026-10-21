"""Qualify only actual final-source phases after independent birth observations."""
import hashlib,json,math,re,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def text(p):return p.read_text(encoding='utf-8-sig')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main(phase,adapter):
    assert phase in ('contracts','census','matrix','target','build')
    assert runpy.run_path(str(HERE/'candidate_inputs.py'))['record'](adapter)==read(HERE/'candidate-inputs.json')
    t=read(HERE/(phase+'-timing.json'));lp=HERE/(phase+'-process-launch.json');launch=read(lp);cleanup=read(HERE/(phase+'-process-cleanup.json'))
    assert t['phase']==phase and t['status']=='PASS' and type(t['exitCode']) is int and t['exitCode']==0 and t['failure'] is None
    assert type(t['elapsedSeconds']) in (int,float) and math.isfinite(t['elapsedSeconds']) and t['elapsedSeconds']>0 and t['terminalCleanupIncluded'] is True
    assert launch['phase']==phase and cleanup['status']=='PASS' and type(cleanup['actualExecExitCode']) is int and cleanup['actualExecExitCode']==0 and cleanup['launchSha256']==sha(lp)
    assert len(launch['drivers'])==len(cleanup['checks'])==2 and all(x['recordedBirthExited'] is True for x in cleanup['checks'])
    assert [{k:x[k] for k in ('pid','startTicks','executable')} for x in cleanup['checks']]==launch['drivers']
    for side in ('before','after'):assert text(HERE/(phase+'-input-audit-'+side+'.txt')).strip()=='PASS paired packing final source1482 six owned deltas machinery24 sealed gates/target inputs'
    log=text(HERE/(phase+'.txt'));assert 'CURRENT_CONTRACT_FAILURE:' not in log and 'CURRENT_CONTRACT_CLEANUP:' not in log
    result={'status':'PASS','phase':phase,'elapsedSeconds':t['elapsedSeconds'],'candidateSealSha256':sha(HERE/'candidate-inputs.json'),'limits':'Actual named finite final-source phase; other native/build/player/publication gates separate'}
    if phase=='build':
        for marker in ('Compiling module com.lushprojects.circuitjs1.circuitjs1','Compiling 5 permutations','Compile of permutations succeeded','Compilation succeeded --','Link succeeded','Linking succeeded --'):assert log.count(marker)==1,marker
        assert 'BUILD_FAILURE' not in log and 'BUILD_INFRASTRUCTURE' not in log
    else:
        suites=read(HERE/'suites.json')[phase]
        assert log.count('PASS: focused current contracts; '+str(len(suites))+' Java suites. Full matrix and independent oracles NOT RUN.')==1
        receipt=text(HERE/(phase+'-receipt.txt'));result['receiptSha256']=sha(HERE/(phase+'-receipt.txt'));result['suites']=suites
        if phase=='target':
            marker='PASS: sparse normal board diagnostic family=PARALLEL_DUAL_INDICATOR seed=-9223372036854775808 assertions='
            assert len([s for s in receipt.splitlines() if s.startswith(marker)])==1
            comparison=read(HERE/'target-comparison.json');assert comparison['status']=='PASS_EXACT_CURRENT_TARGET'
            result['comparisonSha256']=sha(HERE/'target-comparison.json')
        else:
            definitions=dict(re.findall(r"Name = '([^']+)'; Marker = '([^']+)'",text(ROOT/'scripts/verify-current-contracts.ps1')))
            for suite in suites:assert re.search(r'(?m)^PASS: '+re.escape(definitions[suite]),receipt),suite
        if phase=='census':
            comparison=read(HERE/'census-comparison.json')
            assert comparison['status']=='PASS' and comparison['cases']==44 and comparison['observationPairs']==220 and comparison['canaries']['status']=='PASS'
            result['comparisonSha256']=sha(HERE/'census-comparison.json')
        if phase=='matrix':
            rows=[s.split('|') for s in receipt.splitlines() if s.startswith('PHYSICAL_MATRIX|')]
            families=('LED_INDICATOR','DIODE_PROTECTED_INDICATOR','PARALLEL_DUAL_INDICATOR','RC_DELAY','NPN_LOW_SIDE_SWITCH','NMOS_LOW_SIDE_SWITCH','RELAY_OUTPUT','SENSOR_CONTROL','RB15_CONTROL','COMPOSED_CONTROLLED_INDICATOR','RB30_CONTROL')
            assert len(rows)==11 and tuple(r[1] for r in rows)==families
            for r in rows:
                assert len(r)==13 and r[3:5]==['20','20'] and 20<=int(r[11])<=48
                assert r[2]==('MEDIUM' if r[1] in ('COMPOSED_CONTROLLED_INDICATOR','RB30_CONTROL') else 'EASY')
            assert receipt.count('PASS: physical matrix partition coverage families=11 accepted=220 exactReplays=220 smallChildBudgetMs=180000 mediumChildBudgetMs=600000')==1
            result.update(families=11,accepted=220,exactReplays=220)
    with (HERE/(phase+'-assessment.json')).open('x',encoding='utf-8',newline='\n') as f:json.dump(result,f,indent=2);f.write('\n')
    print('PASS actual final paired packing phase '+phase)
if __name__=='__main__':main(sys.argv[1],Path(sys.argv[2]))
