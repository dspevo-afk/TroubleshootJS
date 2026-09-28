from pathlib import Path
import hashlib,importlib.util,json,subprocess,sys,time
sys.dont_write_bytecode=True
s=Path(__file__).resolve().parent
repo=Path(r'<TASK_PATH>')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
binding_path=s/'lu-support-census-r4-execution-binding.json'
binding=json.loads(binding_path.read_bytes())
assert binding['status']=='FROZEN_READY_FOR_PRIVATE_MEASUREMENT'
assert sha(Path(__file__))==binding['tools']['sequence']['sha256']
plan_path=s/binding['planPath'];assert sha(plan_path)==binding['planSha256']
plan=json.loads(plan_path.read_bytes())
spec=importlib.util.spec_from_file_location('census_prep',s/'prepare_lu_support_census_sequence_root.py')
prep=importlib.util.module_from_spec(spec);spec.loader.exec_module(prep)
host,hostpath=prep.load_host_module(repo)
audit=json.loads((s/binding['sourceAuditPath']).read_bytes())
base=json.loads((s/binding['baselineAuditPath']).read_bytes())
out=s/'lu-support-census-r4-sequence-result.json';assert not out.exists()
result={'status':'RUNNING','bindingSha256':sha(binding_path),'rows':[],'pairs':[],'notRun':[r['label'] for r in plan['sequence']]}
def save():out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
def unchanged():
 assert sha(binding_path)==result['bindingSha256']
 assert sha(plan_path)==binding['planSha256']
 assert sha(s/binding['sourceAuditPath'])==binding['sourceAuditSha256']
 assert sha(s/binding['baselineAuditPath'])==binding['baselineAuditSha256']
 for e in binding['tools'].values(): assert sha(s/e['path'])==e['sha256'],e['path']
 for e in binding['gateFiles']:assert sha(s/e['path'])==e['sha256'],e['path']
 for name,digest in binding['pinnedFiles'].items():assert sha(s/name)==digest,name
 for name,digest in binding['pinnedRepoFiles'].items():assert sha(repo/name)==digest,name
 assert sha(hostpath)==binding['hostRunner']['sha256']
 for arm,records in [('control',base['armInputs']['candidate']),('candidate',audit['armInputs']['candidate'])]:
  source=s/plan['source'][arm]
  identity=prep.verify_source_tree(source,records,arm)
  assert identity==binding['source'][arm],arm
  actual=prep.runtime_summary(host,source,prep.EXPECTED_RUNTIME[arm],arm)
  assert actual==binding['runtime'][arm],arm
save()
try:
 for index,row in enumerate(plan['sequence']):
  unchanged(); label=row['label'];arm=row['arm']
  result['activeRow']=row;result['notRun'].remove(label);save()
  print('START '+label,flush=True)
  args=[sys.executable,'-B',str(s/'run_lu_support_census_timing.py'),str(repo),str(s),str(s/plan['source'][arm]),label,plan['seed'],'on' if row['supportEnabled'] else 'off']
  proc=subprocess.run(args,capture_output=True,text=True,encoding='utf-8')
  (s/(label+'-driver.log')).write_text(proc.stdout+proc.stderr,encoding='utf-8')
  assert proc.returncode==0,proc.stdout[-1600:]+proc.stderr[-1600:]
  summary=json.loads((s/(label+'-summary.json')).read_bytes())
  receipt=json.loads((s/label/'result.json').read_bytes())
  result['rows'].append(summary);result.pop('activeRow');save()
  assert summary['exitCode']==0 and summary['runnerOutcome']=='PASS'
  assert receipt['cleanup']['status']=='PASS' and not receipt['cleanup']['errors'] and not receipt['cleanup']['ownedSurvivors']
  assert receipt['inputAudit']['beforeSha256']==receipt['inputAudit']['afterSha256']==binding['runtime'][arm]['sha256']
  assert receipt['cases'][0]['case']['path']==plan['applicationQuery']+('&'+plan['supportQuery'] if row['supportEnabled'] else '')
  unchanged()
  print(json.dumps({'label':label,'cold':summary['cold'],'hostSeconds':summary['hostMonotonicSeconds'],'cleanupSeconds':receipt['cleanup']['seconds']}),flush=True)
  if index>=1:
   tag='census-r4-pair-'+str(index)
   args=[sys.executable,'-B',str(s/'validate_lu_support_census_pair_root.py'),str(s),str(repo),plan['sequence'][1]['label'],plan['sequence'][0 if index==1 else 2]['label'],'--tag',tag,'--binding',str(binding_path)]
   checked=subprocess.run(args,capture_output=True,text=True,encoding='utf-8')
   (s/(tag+'.log')).write_text(checked.stdout+checked.stderr,encoding='utf-8')
   assert checked.returncode==0,checked.stdout[-1800:]+checked.stderr[-1800:]
   result['pairs'].append({'status':'PASS','log':tag+'.log'});save()
 result['status']='PRIVATE_CENSUS_BOTH_COMPARISONS_PASS_PENDING_INTERPRETATION';save()
except BaseException as error:
 if 'activeRow' in result:result['failedRow']=result.pop('activeRow')
 result['status']='STOPPED_FAILURE';result['failure']=str(error);save();raise
