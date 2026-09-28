from pathlib import Path
import hashlib,json,subprocess,sys,time,importlib.util,copy
s=Path(__file__).resolve().parent
repo=Path(r'$REPO')
prep=s/'scratch/d01-r7-final-preparation'
source=Path((prep/'d01-r7-final-source-path.txt').read_text().strip())
audit_raw=(prep/'source-audit.json').read_bytes()
assert hashlib.sha256(audit_raw).hexdigest()=='729360f3cf12ceace58b4fc627afeb2951dc055a39e7a0aa9289faa8b408c505'
audit=json.loads(audit_raw)
gates=json.loads((s/'d01-r7-final-native-gwt-gates.json').read_bytes())
assert gates['status']=='NATIVE_GWT_PASS_BROWSER_NOT_RUN' and len(gates['steps'])==2 and all(x['exitCode']==0 for x in gates['steps'])
module_path=s/'d01-r7-construction-metadata-validator-r2.py'
assert hashlib.sha256(module_path.read_bytes()).hexdigest()=='2c4d4dd34d7c5ec1633f5fcd066908fb9bfc38433afd1459521f1f779bf340c8'
specmod=importlib.util.spec_from_file_location('d01meta',module_path);meta=importlib.util.module_from_spec(specmod);specmod.loader.exec_module(meta)
out=s/'d01-r7-final-boundary-host-gates.json';assert not out.exists()
runner=repo/'tests/browser/compiled_attribute_acceptance.py'
assert hashlib.sha256(runner.read_bytes()).hexdigest()=='1bb267655c97e35f2869925ae91d92a56c708256305e4bfb10106a172d2748a4'
result={'status':'RUNNING','scope':'actual private expected-failure construction lifecycle cases, not D01 or normal acceptance','sourceAuditSha256':hashlib.sha256(audit_raw).hexdigest(),'metadataValidatorSha256':hashlib.sha256(module_path.read_bytes()).hexdigest(),'syntheticMetadataCanaries':meta.synthetic_canary_self_test(),'rows':[]}
def save():out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
def unchanged():
 for e in audit['inputs']:
  raw=(source/e['path']).read_bytes()
  assert hashlib.sha256(raw).hexdigest()==e['sha256'] and len(raw)==e['bytes'],e['path']
save()
try:
 for kind,query in [('scope','tsjQ30D01ConstructionScopeLoss=true'),('timing','tsjQ30D01ConstructionTerminalFailure=timing'),('clock','tsjQ30D01ConstructionTerminalFailure=clock')]:
  unchanged();label='d01-r7-final-'+kind;folder=s/label;casefile=s/(label+'-spec.json');assert not folder.exists() and not casefile.exists()
  case={'name':label,'path':'circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ30=true&tsjQ30D01=true&tsjQ30Seed=10387&'+query,'stateAttribute':'data-tsj-q30-d01-state','expectedState':'FAIL','terminalPrefixes':['PASS','FAIL'],'timeoutSeconds':600,'reportAttribute':'data-tsj-q30-d01-report'}
  casefile.write_text(json.dumps({'cases':[case]},indent=2)+'\n',encoding='utf-8')
  print('START '+label,flush=True);started=time.monotonic()
  with (s/(label+'.log')).open('w',encoding='utf-8') as log:
   p=subprocess.run([sys.executable,'-B',str(runner),str(source),str(folder),str(casefile)],stdout=log,stderr=subprocess.STDOUT)
  host=json.loads((folder/'result.json').read_bytes())
  assert p.returncode==0 and host['outcome']=='PASS' and host['errors']==[]
  assert host['inputAudit']['status']=='PASS'
  cleanup=host['cleanup'];assert cleanup['status']=='PASS' and cleanup['serverStopped'] and not cleanup['ownedSurvivors'] and not cleanup['errors']
  assert len(host['cases'])==1
  row=host['cases'][0];assert row['outcome']=='PASS' and row['observedState']=='FAIL' and row['stateMatch'] and row['reportObserved'] and row['reportJsonValid'] and row['reportMatch'] and not row['timedOut'] and not row['navigationError']
  for key in ('pageErrors','httpErrors','consoleErrors','attributeReadErrors','listenerCleanupErrors'):assert row[key]==[],key
  raw=(folder/row['reportFile']).read_bytes();assert hashlib.sha256(raw).hexdigest()==row['reportSha256']
  report=json.loads(raw)
  validate=meta.validate_scope_loss if kind=='scope' else lambda doc:meta.validate_terminal_failure(doc,kind)
  validated=validate(report)
  negatives=[]
  for key,value in [('normalAdmission',True),('cleanupComplete',False),('hostBoundaryLeaseCloseStatus','FAIL'),('hostBoundaryLeaseCloseError','injected error'),('hostBoundaryLeaseReleased',False),('hostBoundaryWindowHookRemoved',False),('hostBoundarySnapshotReceiptRemoved',False)]:
   bad=copy.deepcopy(report);bad[key]=value
   try:validate(bad)
   except meta.ConstructionMetadataError:negatives.append(key)
   else:raise AssertionError('accepted actual report corruption '+key)
  unchanged()
  record={'label':label,'status':'EXPECTED_FAILURE_BOUNDARY_PASS','hostMonotonicSeconds':time.monotonic()-started,'reportSha256':row['reportSha256'],'appStatus':report['status'],'metadata':validated,'actualReportCorruptionsRejected':negatives,'cleanup':cleanup,'inputAudit':host['inputAudit']}
  result['rows'].append(record);save();print(json.dumps(record),flush=True)
 result['status']='BOUNDARY_CASES_PASS_POSITIVE_AND_VISIBLE_NOT_RUN';save()
except BaseException as e:
 result['status']='STOPPED_FAILURE';result['failure']=str(e);save();raise
