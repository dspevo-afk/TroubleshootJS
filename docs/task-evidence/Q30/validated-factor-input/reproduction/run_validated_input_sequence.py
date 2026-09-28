from pathlib import Path
import json,subprocess,sys,time
scratch=Path(__file__).resolve().parent
repo=Path('<REPOSITORY>')
fixture=Path((scratch/'validated-input-r3-source-pair-path.txt').read_text())
plan=json.loads((scratch/'validated-input-measurement-plan.json').read_text())
result_path=scratch/'validated-input-sequence-result.json'
assert not result_path.exists()
rows=[];pairs=[];started=time.monotonic()
result={'status':'RUNNING','scope':plan['scope'],'rows':rows,'pairs':pairs,'notRun':[r['label'] for r in plan['sequence']]}
def save():
 result['hostSequenceSeconds']=time.monotonic()-started
 result_path.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
save()
for index,row in enumerate(plan['sequence']):
 label=row['label']
 assert not (scratch/label).exists() and not (scratch/(label+'-spec.json')).exists()
 command=[sys.executable,'-B',str(scratch/'run_timing.py'),str(repo),str(scratch),str(fixture/row['arm']),label,row['seed']]
 print('START '+label,flush=True)
 process=subprocess.run(command,check=False)
 summary=json.loads((scratch/(label+'-summary.json')).read_text(encoding='utf-8'))
 rows.append(summary);result['notRun'].remove(label);save()
 if process.returncode!=0:
  result['status']='STOPPED_HOST_OR_CORRECTNESS_FAILURE';save();sys.exit(1)
 if index%2==1:
  prior=plan['sequence'][index-1]
  validator=[sys.executable,'-B',str(scratch/'validate_current_q30_timing_pair.py'),str(scratch),str(repo),prior['label'],label,row['seed'],'--tag','r3']
  with (scratch/('validated-input-pair-'+str((index+1)//2)+'-validation.log')).open('w',encoding='utf-8') as log:
   check=subprocess.run(validator,stdout=log,stderr=subprocess.STDOUT,check=False)
  receipt=scratch/(prior['label']+'-vs-'+label+'-timing-parity-r3.json')
  pair=json.loads(receipt.read_text(encoding='utf-8')) if receipt.exists() else {'status':'NO_RECEIPT'}
  pairs.append({'file':receipt.name,'status':pair['status']});save()
  if check.returncode!=0:
   result['status']='STOPPED_STRICT_OR_PARITY_FAILURE';save();sys.exit(1)
  print('PAIR PASS '+str((index+1)//2),flush=True)
  if index==1:
   candidate,control=rows[0],rows[1]
   if candidate['cold']['proofElapsedMs']>control['cold']['proofElapsedMs']*1.05:
    result['status']='STOPPED_PREDECLARED_FIRST_PAIR_REGRESSION';save();sys.exit(2)
result['status']='FOCUSED_EXPERIMENT_PASS_NOT_ACCEPTANCE';save()
print(json.dumps(result),flush=True)
