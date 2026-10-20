"""Accept actual final-source phases only after separate driver exit proof."""
import hashlib,json,math,re,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def text(p):return p.read_text(encoding='utf-8-sig')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    phase,=sys.argv[1:];assert phase in ('contracts','census','build')
    assert runpy.run_path(str(HERE/'candidate_inputs.py'))['record']()==read(HERE/'candidate-inputs.json')
    t=read(HERE/(phase+'-timing.json'));launch_path=HERE/(phase+'-process-launch.json');launch=read(launch_path);cleanup=read(HERE/(phase+'-process-cleanup.json'))
    assert t['phase']==phase and t['status']=='PASS' and type(t['exitCode']) is int and t['exitCode']==0 and t['failure'] is None and t['terminalCleanupIncluded'] is True
    assert type(t['elapsedSeconds']) in (int,float) and math.isfinite(t['elapsedSeconds']) and t['elapsedSeconds']>0
    assert launch['phase']==phase and cleanup['status']=='PASS' and type(cleanup['actualExecExitCode']) is int and cleanup['actualExecExitCode']==0 and cleanup['launchSha256']==sha(launch_path)
    assert len(launch['drivers'])==len(cleanup['checks'])==2 and all(x['recordedBirthExited'] is True for x in cleanup['checks'])
    assert [{k:x[k] for k in ('pid','startTicks','executable')} for x in cleanup['checks']]==launch['drivers']
    for side in ('before','after'):assert text(HERE/(phase+'-input-audit-'+side+'.txt')).strip()=='PASS final capacitor lid candidate source1482 exactly one paint owner machinery24 helpers8'
    log=text(HERE/(phase+'.txt'))
    result={'status':'PASS','phase':phase,'elapsedSeconds':t['elapsedSeconds'],'inputSealSha256':sha(HERE/'candidate-inputs.json'),'scope':'Actual named finite final-source phase with separate root birth observation; other phases/player/publication separate'}
    if phase=='build':
        for marker in ('Compiling module com.lushprojects.circuitjs1.circuitjs1','Compiling 5 permutations','Compile of permutations succeeded','Compilation succeeded --','Link succeeded','Linking succeeded --'):assert log.count(marker)==1,marker
        assert 'BUILD_FAILURE' not in log and 'BUILD_INFRASTRUCTURE' not in log
    else:
        suites=read(HERE/'suites.json')[phase]
        assert log.count('PASS: focused current contracts; '+str(len(suites))+' Java suites. Full matrix and independent oracles NOT RUN.')==1
        assert 'CURRENT_CONTRACT_FAILURE:' not in log and 'CURRENT_CONTRACT_CLEANUP:' not in log
        definitions=dict(re.findall(r"Name = '([^']+)'; Marker = '([^']+)'",text(ROOT/'scripts/verify-current-contracts.ps1')))
        receipt=text(HERE/(phase+'-receipt.txt'))
        for suite in suites:assert re.search(r'(?m)^PASS: '+re.escape(definitions[suite]),receipt),suite
        result['suites']=suites;result['receiptSha256']=sha(HERE/(phase+'-receipt.txt'))
        if phase=='census':
            comparison=read(HERE/'census-comparison.json')
            assert comparison['status']=='PASS' and comparison['cases']==44 and comparison['observationPairs']==220
            result['pairedComparisonSha256']=sha(HERE/'census-comparison.json')
    target=HERE/(phase+'-assessment.json');assert not target.exists(),'Preserve prior assessment'
    target.write_text(json.dumps(result,indent=2)+'\n')
    print('PASS actual final capacitor lid phase '+phase)
if __name__=='__main__':main()
