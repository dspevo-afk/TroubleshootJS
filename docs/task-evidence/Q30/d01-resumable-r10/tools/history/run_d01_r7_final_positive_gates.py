from pathlib import Path
import hashlib,json,subprocess,sys,time,importlib.util,copy
s=Path(__file__).resolve().parent
repo=Path(r'$REPO')
prep=s/'scratch/d01-r7-final-preparation'
source=Path((prep/'d01-r7-final-source-path.txt').read_text().strip())
audit_raw=(prep/'source-audit.json').read_bytes();audit=json.loads(audit_raw)
assert hashlib.sha256(audit_raw).hexdigest()=='729360f3cf12ceace58b4fc627afeb2951dc055a39e7a0aa9289faa8b408c505'
assert json.loads((s/'d01-r7-final-boundary-host-gates.json').read_bytes())['status']=='BOUNDARY_CASES_PASS_POSITIVE_AND_VISIBLE_NOT_RUN'
module_path=s/'d01-r7-construction-metadata-validator-r2.py'
assert hashlib.sha256(module_path.read_bytes()).hexdigest()=='2c4d4dd34d7c5ec1633f5fcd066908fb9bfc38433afd1459521f1f779bf340c8'
sp=importlib.util.spec_from_file_location('meta',module_path);meta=importlib.util.module_from_spec(sp);sp.loader.exec_module(meta)
runner=repo/'tests/browser/compiled_attribute_acceptance.py'
assert hashlib.sha256(runner.read_bytes()).hexdigest()=='1bb267655c97e35f2869925ae91d92a56c708256305e4bfb10106a172d2748a4'
readers=[repo/'docs/task-evidence/Q30/scale-plan-4'/name for name in ('check_d01.py','check_d01_canaries.py')]
readerhash={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in readers}
out=s/'d01-r7-final-positive-gates.json';assert not out.exists()
result={'status':'RUNNING','scope':'focused real D01 cold/warm20+40 cases after resumable construction repair, not normal or scale acceptance','sourceAuditSha256':hashlib.sha256(audit_raw).hexdigest(),'readers':readerhash,'rows':[],'notRun':['10387','10014']}
def save():out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
def unchanged():
 for e in audit['inputs']:
  raw=(source/e['path']).read_bytes()
  assert hashlib.sha256(raw).hexdigest()==e['sha256'] and len(raw)==e['bytes'],e['path']
 for p in readers:assert hashlib.sha256(p.read_bytes()).hexdigest()==readerhash[p.name]
save()
try:
 for seed in ('10387','10014'):
  unchanged();label='d01-r7-final-positive-'+seed;folder=s/label;casefile=s/(label+'-spec.json');assert not folder.exists() and not casefile.exists()
  case={'name':label,'path':'circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ30=true&tsjQ30D01=true&tsjQ30Seed='+seed,'stateAttribute':'data-tsj-q30-d01-state','expectedState':'PASS','terminalPrefixes':['PASS','FAIL'],'timeoutSeconds':600,'reportAttribute':'data-tsj-q30-d01-report'}
  casefile.write_text(json.dumps({'cases':[case]},indent=2)+'\n',encoding='utf-8');print('START '+label,flush=True);started=time.monotonic()
  with (s/(label+'.log')).open('w',encoding='utf-8') as log:p=subprocess.run([sys.executable,'-B',str(runner),str(source),str(folder),str(casefile)],stdout=log,stderr=subprocess.STDOUT)
  host=json.loads((folder/'result.json').read_bytes())
  assert p.returncode==0 and host['outcome']=='PASS' and host['errors']==[]
  assert host['inputAudit']['status']=='PASS'
  cleanup=host['cleanup'];assert cleanup['status']=='PASS' and cleanup['serverStopped'] and not cleanup['ownedSurvivors'] and not cleanup['errors']
  assert len(host['cases'])==1
  row=host['cases'][0];assert row['outcome']=='PASS' and row['observedState']=='PASS' and row['stateMatch'] and row['reportObserved'] and row['reportJsonValid'] and row['reportMatch'] and not row['timedOut'] and not row['navigationError']
  for key in ('pageErrors','httpErrors','consoleErrors','attributeReadErrors','listenerCleanupErrors'):assert row[key]==[],key
  raw=(folder/row['reportFile']).read_bytes();assert hashlib.sha256(raw).hexdigest()==row['reportSha256']
  report=json.loads(raw);assert report['seed']==seed and report['requestedSeed']==seed
  metadata=meta.validate_positive(report)
  wrapper=s/(label+'-wrapper.json');assert not wrapper.exists()
  identity=host['inputAudit']
  wrapper.write_text(json.dumps({'schema':1,'identityProvenance':'Content digests from maintained host input manifest; not preview DOM identity-route digests','rows':[{'seed':seed,'previewSourceDigest':identity['sourceSha256Before'],'previewWebDigest':identity['webSha256Before'],'report':report}]},indent=2)+'\n',encoding='utf-8')
  readerresults=[]
  for reader in readers:
   args=[sys.executable,'-B',str(reader),str(wrapper),'--seeds',seed]+(['--require-warm'] if reader.name=='check_d01.py' else [])
   output=s/(label+'-'+reader.stem+'.log')
   with output.open('w',encoding='utf-8') as log:reading=subprocess.run(args,stdout=log,stderr=subprocess.STDOUT)
   readerresults.append({'reader':reader.name,'exitCode':reading.returncode,'output':output.name})
   assert reading.returncode==0,reader.name+' failed'
  negatives=[]
  for key,value in [('normalAdmission',True),('cleanupComplete',False),('hostBoundaryLeaseCloseStatus','FAIL'),('hostBoundaryLeaseCloseError','injected error'),('hostBoundaryLeaseReleased',False),('hostBoundaryWindowHookRemoved',False),('hostBoundarySnapshotReceiptRemoved',False)]:
   bad=copy.deepcopy(report);bad[key]=value
   try:meta.validate_positive(bad)
   except meta.ConstructionMetadataError:negatives.append(key)
   else:raise AssertionError('accepted actual report corruption '+key)
  unchanged()
  record={'label':label,'seed':seed,'status':'FOCUSED_D01_PASS_NOT_ACCEPTANCE','hostMonotonicSeconds':time.monotonic()-started,'reportSha256':row['reportSha256'],'appStatus':report['status'],'appElapsedMs':report['elapsedMs'],'metadata':metadata,'readers':readerresults,'metadataCorruptionsRejected':negatives,'cleanup':cleanup,'inputAudit':identity}
  result['rows'].append(record);result['notRun'].remove(seed);save();print(json.dumps(record),flush=True)
 result['status']='FOCUSED_D01_20_40_PASS_VISIBLE_AND_POSTSTOP_NOT_RUN';save()
except BaseException as e:
 result['status']='STOPPED_FAILURE';result['failure']=str(e);save();raise
