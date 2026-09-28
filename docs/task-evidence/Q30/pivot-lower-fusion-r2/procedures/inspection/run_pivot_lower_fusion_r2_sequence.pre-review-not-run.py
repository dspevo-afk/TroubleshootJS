from pathlib import Path
import hashlib,json,subprocess,sys,time
s=Path(__file__).resolve().parent
repo=Path('<TASK_TEMP>
sha=lambda b:hashlib.sha256(b).hexdigest()
prep=Path((s/'pivot-lower-fusion-r2-source-pair-path.txt').read_text().strip())
audit_raw=(prep/'source-audit.json').read_bytes();assert sha(audit_raw)=='0d27e176dc46afb4db91316570f4674e52b51ce2b462c64f6b6a37b98cbec197';audit=json.loads(audit_raw)
plan_raw=(s/'pivot-lower-fusion-measurement-plan.json').read_bytes();assert sha(plan_raw)=='7ba6ef68fefbe59282a719771483bf2ffd6e8b5d9a971cec87b4a6769971c4b3';plan=json.loads(plan_raw)
gate_raw=(s/'pivot-lower-fusion-r2-gates.json').read_bytes();gate=json.loads(gate_raw)
assert gate['status']=='FOCUSED_GATES_PASS_NOT_ACCEPTANCE' and gate['sourceAuditSha256']==sha(audit_raw) and gate['planSha256']==sha(plan_raw)
assert gate['controlEvidenceReuse']['status']=='PASS' and len(gate['steps'])==4 and all(r['exitCode']==0 for r in gate['steps'])
assert sha((s/'run_timing.py').read_bytes())=='a7ef67c63397a28c1781b40746507fc0fa129f030742b8c6201350f099a9765f'
assert sha((s/'validate_current_q30_timing_pair.py').read_bytes())=='67430d7c56d872d115a7c38732bf60101cff52e8bf40f7af075213debc8e7e3b'
hostpath=repo/'tests/browser/compiled_attribute_acceptance.py'
assert sha(hostpath.read_bytes())=='1bb267655c97e35f2869925ae91d92a56c708256305e4bfb10106a172d2748a4'
sequence=plan['sequence'];assert len(sequence)==8
result={'status':'RUNNING','planSha256':sha(plan_raw),'sourceAuditSha256':sha(audit_raw),'gateSha256':sha(gate_raw),'driverSha256':sha(Path(__file__).read_bytes()),'rows':[],'pairs':[],'notRun':[r['label'] for r in sequence]}
out=s/'pivot-lower-fusion-sequence-result.json';assert not out.exists()
identities={}
def save():out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
def unchanged():
 for arm in ('control','candidate'):
  source=Path(audit[arm+'Source'])
  for e in audit['armInputs'][arm]:
   b=(source/e['path']).read_bytes();assert len(b)==e['bytes'] and sha(b)==e['sha256'],(arm,e['path'])
 assert sha(hostpath.read_bytes())=='1bb267655c97e35f2869925ae91d92a56c708256305e4bfb10106a172d2748a4'
def hostcheck(row,summary):
 folder=s/row['label'];host=json.loads((folder/'result.json').read_bytes())
 assert host['outcome']=='PASS' and host['errors']==[] and host['inputAudit']['status']=='PASS'
 cleanup=host['cleanup'];assert cleanup['status']=='PASS' and cleanup['serverStopped'] and not cleanup['errors'] and not cleanup['ownedSurvivors']
 assert len(host['cases'])==1
 case=host['cases'][0]
 assert case['outcome']=='PASS' and case['terminalReached'] and case['stateMatch'] and case['reportMatch'] and case['reportJsonValid'] and not case['timedOut'] and not case['navigationError']
 for key in ('pageErrors','httpErrors','consoleErrors','attributeReadErrors','listenerCleanupErrors'):assert case[key]==[]
 raw=(folder/case['reportFile']).read_bytes();assert sha(raw)==case['reportSha256']
 d=json.loads(raw);assert d['seed']==row['seed'] and d['requestedSeed']==row['seed']
 for phase in ('cold','warm'):
  assert summary[phase]['cleanupComplete'] and summary[phase]['profileKeys']==[]
 identity={key:host['inputAudit'][key] for key in ('beforeSha256','sourceSha256Before','webSha256Before','runnerBeforeSha256','fileCountBefore')}
 if row['arm'] in identities:assert identity==identities[row['arm']]
 else:identities[row['arm']]=identity
 return {'inputIdentity':identity,'reportSha256':sha(raw),'cleanup':cleanup}
save()
try:
 for index,row in enumerate(sequence):
  unchanged();label=row['label']
  assert not (s/label).exists() and not (s/(label+'-summary.json')).exists() and not (s/(label+'-spec.json')).exists()
  print('START '+label,flush=True)
  p=subprocess.run([sys.executable,'-B',str(s/'run_timing.py'),str(repo),str(s),audit[row['arm']+'Source'],label,row['seed']],capture_output=True,text=True)
  assert p.returncode==0,p.stdout+p.stderr
  summary=json.loads((s/(label+'-summary.json')).read_bytes());assert summary['exitCode']==0
  details=hostcheck(row,summary);unchanged()
  record={**row,'summary':summary,**details};result['rows'].append(record);result['notRun'].remove(label);save()
  print(json.dumps({'label':label,'cold':summary['cold'],'hostSeconds':summary['hostMonotonicSeconds'],'cleanupSeconds':details['cleanup']['seconds']}),flush=True)
  if index%2==1:
   a,b=result['rows'][-2:];assert a['seed']==b['seed'];tag='pivot-lower-pair-'+str(index//2+1)
   command=[sys.executable,'-B',str(s/'validate_current_q30_timing_pair.py'),str(s),str(repo),a['label'],b['label'],row['seed'],'--tag',tag]
   began=time.monotonic();v=subprocess.run(command,capture_output=True,text=True)
   (s/(tag+'-validation.log')).write_text(v.stdout+v.stderr,encoding='utf-8')
   assert v.returncode==0,v.stderr
   validation=json.loads(v.stdout);assert validation['status']=='PASS'
   result['pairs'].append({'seed':row['seed'],'labels':[a['label'],b['label']],'status':'PASS','validatorElapsedSeconds':time.monotonic()-began,'validationSha256':sha(v.stdout.encode('utf-8'))});save()
   if index==1:
    cold={r['arm']:r['summary']['cold']['elapsedMs'] for r in result['rows']}
    ratio=cold['candidate']/cold['control']
    if ratio>=0.99:
     result['status']='REJECTED_FIRST_PAIR_REGRESSION' if ratio>=1.05 else 'INCONCLUSIVE_FIRST_PAIR_GAIN_BELOW_ONE_PERCENT'
     result['earlyStopRatio']=ratio;save();print(result['status'],flush=True);sys.exit(0)
 result['status']='FOCUSED_PAIRS_PASS_PENDING_ROOT_ACCEPTANCE';save();print(result['status'],flush=True)
except BaseException as error:
 if isinstance(error,SystemExit) and error.code==0:raise
 result['status']='STOPPED_FAILURE';result['failure']=str(error);save();raise
