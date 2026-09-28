from pathlib import Path
import copy,hashlib,importlib.util,json,subprocess,sys
s=Path(__file__).resolve().parent
prep=s/'scratch/d01-r10-preparation'
audit_raw=(prep/'source-audit.json').read_bytes()
assert hashlib.sha256(audit_raw).hexdigest()=='3e802932fd1df9177371b0e3e2ae211b9837fddda66039d36847be5ad298c4c3'
audit=json.loads(audit_raw);fixture=prep/'fixture'
for e in audit['inputs']:
 raw=(fixture/e['path']).read_bytes()
 assert len(raw)==e['bytes'] and hashlib.sha256(raw).hexdigest()==e['sha256'],e['path']
path=s/'d01-r10-browser-poststop-scope-report.json'
raw=path.read_bytes();report=json.loads(raw);c=report['constructionPostStopScopeWithdrawalCanary']
reader=s/'scratch/d01-resumable-draft/r10/check_d01_normal_successor_canary.py'
assert hashlib.sha256(reader.read_bytes()).hexdigest()=='b38dc3ee763446065024a9d46037e56cb4d5fb53d7b7c7b35ea951f0104761f8'
p=subprocess.run([sys.executable,'-B',str(reader),'--post-stop-scope-withdrawal',str(path)],text=True,capture_output=True)
assert p.returncode==0,p.stdout+p.stderr
spec=importlib.util.spec_from_file_location('d01_r10',reader);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
negative=[]
for field,value in [('sameSuccessorPhysicalStateAcrossLeaseClose',False),('completionObserverDisarmed',False),('captureStateReleased',False),('scopeFlagRestoredSafely',False),('normalMaximumJobMillis',300000),('successorStillRunningImmediatelyAfterLeaseClose',False)]:
 changed=copy.deepcopy(report);changed['constructionPostStopScopeWithdrawalCanary'][field]=value
 try:module.check_post_stop_scope_withdrawal(changed)
 except ValueError:negative.append({'field':field,'status':'REJECTED_AS_EXPECTED'})
 else:raise AssertionError('corruption accepted: '+field)
changed=copy.deepcopy(report);changed['constructionPostStopScopeWithdrawalCanary']['successorPhysicalStateAfterLeaseClose']['graphVectorToken']='C9999'
try:module.check_post_stop_scope_withdrawal(changed)
except ValueError:negative.append({'field':'successorPhysicalStateAfterLeaseClose.graphVectorToken','status':'REJECTED_AS_EXPECTED'})
else:raise AssertionError('graph identity corruption accepted')
result={'status':'PASS_ACTUAL_POSTSTOP_SCOPE_CANARY','scope':'Actual in-app Browser private compiled canary; not headless host certification or Q30 acceptance','sourceAuditSha256':hashlib.sha256(audit_raw).hexdigest(),'sourceInputCount':len(audit['inputs']),'sourceInputsUnchangedAfterRun':True,'browserId':'2','tabId':'5','previewPort':34917,'previewPid':20332,'previewCreationTime':'2026-09-28T06:01:02.096545-04:00','previewExecutable':'WindowsPowerShell/powershell.exe','previewScript':'scratch/d01-r10-preparation/fixture/scripts/preview.ps1','input':'Navigate explicit private canary URL tsjQ30D01ConstructionScopeWithdrawalAfterStop=true, seed10387; no app-controller calls or player-input claim','reportFile':path.name,'reportSha256':hashlib.sha256(raw).hexdigest(),'reportBytes':len(raw),'readerSha256':hashlib.sha256(reader.read_bytes()).hexdigest(),'readerExitCode':p.returncode,'readerOutput':p.stdout,'actualNegativeCanaries':negative,'actual':{k:v for k,v in c.items() if not isinstance(v,(dict,list))},'cleanup':{'status':'PASS','tabClosed':True,'stopCommand':'pwsh -NoProfile -File scripts/stop-preview.ps1','ownedProcessStopped':True,'portPositivelyReleased':True,'initialWrongParameterAttempt':{'exitCode':1,'reason':'Unsupported -Port argument; no process was stopped by this attempt; corrected maintained invocation succeeded.'}},'limitations':['No native hook-removal exception injection.','No screenshots captured.','No default-one-second polling latency claim; this canary explicitly uses ten milliseconds.','Manual Browser evidence does not certify maintained headless host error arrays.','Witness omits simIsRunning/developerVerifierRunning and does not independently prove all internal token cross-links; reviewed close path does not write those omitted fields.']}
out=s/'d01-r10-manual-browser-evidence.json';assert not out.exists();out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'status':result['status'],'sourceInputsUnchanged':True,'actualNegativeCanaries':len(negative),'reportSha256':result['reportSha256'],'bytes':len(raw),'cleanup':result['cleanup']['status']}))
