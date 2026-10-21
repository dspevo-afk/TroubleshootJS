"""Keep the reproduced production failure distinct from diagnostic success."""
import hashlib,json,math,subprocess,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main(folder):
    subprocess.run([sys.executable,'-X','utf8','-B',str(HERE/'inputs.py'),'audit',str(folder)],check=True)
    reader_seal=read(HERE/'reader-inputs.json')
    assert reader_seal['diagnosticSealSha256']==sha(HERE/'diagnostic-inputs.json')
    assert reader_seal['files']=={n:sha(HERE/n) for n in ('read_diagnostic.py','reader-notes.md','qualify_v2.py','post-native-observation-notes.json')}
    phases={}
    for phase in ('canary','current','nohook'):
        p=HERE/phase;t=read(p/'timing.json');l=read(p/'process-launch.json');c=read(p/'process-cleanup.json')
        assert t['phase']==l['phase']==phase and t['failure'] is None
        assert t['terminalCleanupIncluded'] is True and math.isfinite(t['elapsedSeconds']) and t['elapsedSeconds']>0
        expected=1 if phase=='current' else 0
        assert c['actualExecExitCode']==expected and t['exitCode']==(2 if phase=='current' else 0)
        assert t['status']==('FAIL' if phase=='current' else 'PASS')
        assert c['status']==('PASS_CLEANUP_OF_FAILED_PHASE' if phase=='current' else 'PASS')
        assert c['launchSha256']==sha(p/'process-launch.json')
        assert len(l['drivers'])==len(c['checks'])==2 and all(x['recordedBirthExited'] is True for x in c['checks'])
        assert [{k:x[k] for k in ('pid','startTicks','executable')} for x in c['checks']]==l['drivers']
        for side in ('before','after'):
            assert (p/('input-audit-'+side+'.txt')).read_text(encoding='utf-8-sig').strip()=='PASS diversity diagnostic unchanged1482 machinery24 sealed helpers/private inputs'
        phases[phase]={'actualToolExitCode':expected,'operationExitCode':t['exitCode'],'elapsedSeconds':t['elapsedSeconds'],'timingSha256':sha(p/'timing.json'),'cleanupSha256':sha(p/'process-cleanup.json')}
    canary=read(HERE/'canary/canary.json')
    assert canary['status']=='PASS_REJECTED_AT_PARAMETER_BINDING' and canary['actualAdapterExitCode']==1
    reader=subprocess.run([sys.executable,'-X','utf8','-B',str(HERE/'read_diagnostic.py'),str(HERE/'current/native.txt'),str(HERE/'nohook/receipt.txt'),str(HERE/'current/native.txt')],capture_output=True,check=True,text=True,encoding='utf-8')
    comparison=json.loads(reader.stdout)
    with (HERE/'comparison.json').open('x',encoding='utf-8',newline='\n') as f:json.dump(comparison,f,indent=2);f.write('\n')
    result={'status':'DIAGNOSIS_COMPLETED_PRODUCTION_REMAINS_BLOCKED','productionMatrixStatus':'FAIL_PHYSICAL_DIVERSITY_REGRESSION','phases':phases,'diagnosticSealSha256':sha(HERE/'diagnostic-inputs.json'),'comparisonSha256':sha(HERE/'comparison.json'),'limits':'Task-only LED batch; no final-source correction, GWT, player, all-seed or production publication claim'}
    with (HERE/'assessment.json').open('x',encoding='utf-8',newline='\n') as f:json.dump(result,f,indent=2);f.write('\n')
    print('PASS diagnostic evidence; production matrix remains FAIL')
if __name__=='__main__':main(Path(sys.argv[1]))
