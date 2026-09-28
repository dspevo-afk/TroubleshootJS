from pathlib import Path
import json,hashlib,subprocess,sys,time
s=Path(__file__).resolve().parent
repo=Path(r'$REPO')
prep=s/'scratch/d01-r8-source-only-6193901599054f2ea474d199e12d23ae'
source=prep/'fixture';audit_raw=(prep/'source-audit.json').read_bytes()
assert hashlib.sha256(audit_raw).hexdigest()=='717cc9bcf649d87b6dd801d76a52b78143e35b74acd395387807eefff7544d38'
audit=json.loads(audit_raw)
assert json.loads((s/'d01-r8-native-gwt-gates.json').read_bytes())['status']=='NATIVE_GWT_PASS_BROWSER_NOT_RUN'
label='d01-r8-no-click-10387';out=s/(label+'-gate.json');folder=s/label;spec=s/(label+'-spec.json')
assert not out.exists() and not folder.exists() and not spec.exists()
result={'status':'RUNNING','scope':'expected negative private visible-power canary when no player click occurs; not normal acceptance','sourceAuditSha256':hashlib.sha256(audit_raw).hexdigest()}
def save():out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
def unchanged():
 for e in audit['inputs']:
  b=(source/e['path']).read_bytes();assert hashlib.sha256(b).hexdigest()==e['sha256'] and len(b)==e['bytes'],e['path']
save()
try:
 unchanged()
 spec.write_text(json.dumps({'cases':[{'name':label,'path':'circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ30=true&tsjQ30D01=true&tsjQ30Seed=10387&tsjQ30D01VisiblePowerBoundary=true','stateAttribute':'data-tsj-q30-d01-state','expectedState':'FAIL','terminalPrefixes':['PASS','FAIL'],'timeoutSeconds':600,'reportAttribute':'data-tsj-q30-d01-report'}]},indent=2)+'\n',encoding='utf-8')
 started=time.monotonic()
 with (s/(label+'.log')).open('w',encoding='utf-8') as log:
  run=subprocess.run([sys.executable,'-B',str(repo/'tests/browser/compiled_attribute_acceptance.py'),str(source),str(folder),str(spec)],stdout=log,stderr=subprocess.STDOUT)
 result['hostMonotonicSeconds']=time.monotonic()-started
 host=json.loads((folder/'result.json').read_bytes());assert run.returncode==0 and host['outcome']=='PASS' and host['errors']==[]
 assert host['inputAudit']['status']=='PASS' and len(host['cases'])==1
 c=host['cleanup'];assert c['status']=='PASS' and c['serverStopped'] and not c['errors'] and not c['ownedSurvivors']
 row=host['cases'][0];assert row['outcome']=='PASS' and row['observedState']=='FAIL' and row['reportMatch']
 for k in ('pageErrors','httpErrors','consoleErrors','attributeReadErrors','listenerCleanupErrors'):assert row[k]==[]
 raw=(folder/row['reportFile']).read_bytes();assert hashlib.sha256(raw).hexdigest()==row['reportSha256']
 r=json.loads(raw);assert r['seed']==r['requestedSeed']=='10387'
 assert r['status']=='FAIL' and r['failurePhase']=='visiblePowerBoundary'
 assert r['failure']=='D01 completed before a visible power boundary was observed'
 assert r['d01Admission'] is False and r['normalAdmission'] is False and r['playerPublished'] is False
 assert r['cold']['status']=='PASS' and r['warm']['status']=='PASS'
 assert r['cleanupComplete'] is True and r['restored'] is True and r['ownerRestored'] is True and r['cleanupPending'] is False
 assert r['hostBoundaryLeaseCloseStatus']=='PASS' and r['hostBoundaryLeaseCloseError']==''
 for k in ('hostBoundaryLeaseReleased','hostBoundaryWindowHookRemoved','hostBoundarySnapshotReceiptRemoved'):assert r[k] is True
 v=r['constructionVisiblePowerBoundaryCanary'];assert v['status']=='FAIL' and v['failure']==r['failure']
 assert v['snapshotLeaseCloseStatus']=='PASS' and v['snapshotLeaseCloseError']==''
 for k in ('snapshotLeaseReleased','snapshotWindowHookRemoved','snapshotReceiptRemoved'):assert v[k] is True
 unchanged();result.update({'status':'EXPECTED_NO_CLICK_FAILURE_AND_CLEANUP_PASS','appStatus':r['status'],'reportSha256':row['reportSha256'],'coldStatus':r['cold']['status'],'warmStatus':r['warm']['status'],'failurePhase':r['failurePhase'],'cleanup':c,'inputAudit':host['inputAudit']});save()
except BaseException as e:
 result['status']='STOPPED_FAILURE';result['failure']=str(e);save();raise
