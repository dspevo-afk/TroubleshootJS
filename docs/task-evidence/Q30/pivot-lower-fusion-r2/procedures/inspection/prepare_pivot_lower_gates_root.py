from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent
prep=Path((s/'pivot-lower-fusion-source-pair-path.txt').read_text().strip())
audit_raw=(prep/'source-audit.json').read_bytes();audit=json.loads(audit_raw)
assert len(audit['armInputs']['candidate'])==1340
native=(s/'native-owned-zero-row.ps1').read_text(encoding='utf-8')
needle="'Q30OwnedLuZeroRowContractTest'"
assert native.count(needle)==1
native=native.replace(needle,needle+",'Q30LuPivotCollectionContractTest'")
out=s/'native-pivot-lower-fusion.ps1';assert not out.exists();out.write_text(native,encoding='utf-8')
runner='''from pathlib import Path
import hashlib,importlib.util,json,subprocess,sys,time
s=Path(__file__).resolve().parent
repo=Path('<TASK_TEMP>
prep=Path((s/'pivot-lower-fusion-source-pair-path.txt').read_text().strip())
raw=(prep/'source-audit.json').read_bytes()
assert hashlib.sha256(raw).hexdigest()==AUDIT_HASH
audit=json.loads(raw);candidate=Path(audit['candidateSource']);control=Path(audit['controlSource'])
plan_raw=(s/'pivot-lower-fusion-measurement-plan.json').read_bytes()
assert hashlib.sha256(plan_raw).hexdigest()==PLAN_HASH
out=s/'pivot-lower-fusion-r1-gates.json';assert not out.exists()
receipt={'status':'RUNNING','scope':'focused LU pivot/lower collection trial; not Q30 acceptance','sourceAuditSha256':hashlib.sha256(raw).hexdigest(),'planSha256':hashlib.sha256(plan_raw).hexdigest(),'steps':[]}
def save():out.write_text(json.dumps(receipt,indent=2)+'\\n',encoding='utf-8')
def unchanged():
 for arm,source in [('control',control),('candidate',candidate)]:
  for e in audit['armInputs'][arm]:
   b=(source/e['path']).read_bytes();assert len(b)==e['bytes'] and hashlib.sha256(b).hexdigest()==e['sha256'],(arm,e['path'])
def run(name,args):
 unchanged();print('START '+name,flush=True);started=time.monotonic();p=subprocess.run(args)
 receipt['steps'].append({'name':name,'command':args,'exitCode':p.returncode,'elapsedSeconds':time.monotonic()-started});save()
 assert p.returncode==0,name
 unchanged();print('PASS '+name,flush=True)
save()
try:
 unchanged()
 hostpath=repo/'tests/browser/compiled_attribute_acceptance.py'
 assert hashlib.sha256(hostpath.read_bytes()).hexdigest()=='1bb267655c97e35f2869925ae91d92a56c708256305e4bfb10106a172d2748a4'
 spec=importlib.util.spec_from_file_location('host_capture',hostpath);hostmod=importlib.util.module_from_spec(spec);spec.loader.exec_module(hostmod)
 current=hostmod.capture_input_manifest(control)
 prior=json.loads((s/'sticky-finite-r2-06-candidate-10014/input-manifest-before.json').read_bytes())
 for key in ['files','sha256','sourceSha256','webSha256','fileCount','sourceFileCount','webFileCount']:assert current[key]==prior[key],key
 legacy_gwt=json.loads((s/'sticky-finite-r2-candidate-gwt.json').read_bytes());assert legacy_gwt['status']=='PASS' and legacy_gwt['exitCode']==0
 legacy_native=json.loads((s/'native-sticky-finite-r2-candidate.json').read_bytes());assert legacy_native['status']=='PASS' and legacy_native['exitCode']==0
 legacy_canary=json.loads((s/'sticky-finite-r2-candidate-canaries/result.json').read_bytes());assert legacy_canary['outcome']=='PASS' and legacy_canary['cleanup']['status']=='PASS'
 receipt['controlEvidenceReuse']={'status':'PASS','boundary':'All 1339 source-fixture inputs and all 1525 consumed runtime source/web inputs unchanged from the accepted sticky-finite candidate. Reuse its native/GWT/A07/disabled-normal evidence; every timing row still starts a fresh browser and cold private cache.','runtimeInputCount':current['fileCount'],'sourceSha256':current['sourceSha256'],'webSha256':current['webSha256'],'priorGwtReceiptSha256':hashlib.sha256((s/'sticky-finite-r2-candidate-gwt.json').read_bytes()).hexdigest(),'priorManifestSha256':hashlib.sha256((s/'sticky-finite-r2-06-candidate-10014/input-manifest-before.json').read_bytes()).hexdigest()};save()
 run('native6',['pwsh','-NoProfile','-File',str(s/'native-pivot-lower-fusion.ps1'),'-SourceRoot',str(candidate),'-Label','native-pivot-lower-fusion-r1'])
 run('gwt5',['pwsh','-NoProfile','-File',str(s/'build-run.ps1'),'-SourceRoot',str(candidate),'-Label','pivot-lower-fusion-r1-gwt'])
 run('compiled-a07-disabled-normal',[sys.executable,'-B',str(s/'run_current_solver_canaries.py'),str(repo),str(s),str(candidate),'pivot-lower-fusion-r1-canaries'])
 host=json.loads((s/'pivot-lower-fusion-r1-canaries/result.json').read_bytes())
 assert host['outcome']=='PASS' and host['inputAudit']['status']=='PASS' and len(host['cases'])==2
 cleanup=host['cleanup'];assert cleanup['status']=='PASS' and cleanup['serverStopped'] and not cleanup['errors'] and not cleanup['ownedSurvivors']
 reports=[]
 for row in host['cases']:
  assert row['outcome']=='PASS' and row['terminalReached'] and row['stateMatch'] and row['reportMatch'] and not row['timedOut'] and not row['navigationError']
  for key in ('pageErrors','httpErrors','consoleErrors','attributeReadErrors','listenerCleanupErrors'):assert row[key]==[]
  data=(s/'pivot-lower-fusion-r1-canaries'/row['reportFile']).read_bytes();assert hashlib.sha256(data).hexdigest()==row['reportSha256']
  if row['observedState']=='PASS:a07':reports.append(s/'pivot-lower-fusion-r1-canaries'/row['reportFile'])
 assert len(reports)==1
 run('exact-a07-reader-three-corruptions',['pwsh','-NoProfile','-File',str(s/'read_a07.ps1'),'-Repo',str(repo),'-Report',str(reports[0])])
 receipt['status']='FOCUSED_GATES_PASS_NOT_ACCEPTANCE';save()
except BaseException as error:
 receipt['status']='STOPPED_FAILURE';receipt['failure']=str(error);save();raise
'''
runner=runner.replace('AUDIT_HASH',repr(hashlib.sha256(audit_raw).hexdigest())).replace('PLAN_HASH',repr(hashlib.sha256((s/'pivot-lower-fusion-measurement-plan.json').read_bytes()).hexdigest()))
compile(runner,'run_pivot_lower_fusion_r1_gates.py','exec')
path=s/'run_pivot_lower_fusion_r1_gates.py';assert not path.exists();path.write_text(runner,encoding='utf-8')
print(json.dumps({'status':'PREPARED_NOT_RUN','runnerSha256':hashlib.sha256(path.read_bytes()).hexdigest()}))
