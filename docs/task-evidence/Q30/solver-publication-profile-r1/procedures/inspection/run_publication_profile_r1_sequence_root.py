from pathlib import Path
import hashlib,importlib.util,json,subprocess,sys
sys.dont_write_bytecode=True
s=Path(__file__).resolve().parent
repo=Path('<TASK_PATH>')
sha=lambda b:hashlib.sha256(b).hexdigest()
bindingraw=(s/'publication-profile-r1-execution-binding.json').read_bytes()
binding=json.loads(bindingraw)
assert binding['status']=='FROZEN_READY_FOR_PRIVATE_MEASUREMENT'
assert sha(Path(__file__).read_bytes())==binding['driverSha256']
plan=json.loads((s/'solver-publication-profile-r1-measurement-plan.json').read_bytes())
auditraw=(s/'solver-publication-profile-r1-preparation/source-audit.json').read_bytes()
assert sha(auditraw)==plan['sourceAuditSha256']
audit=json.loads(auditraw)
hostpath=repo/'tests/browser/compiled_attribute_acceptance.py'
spec=importlib.util.spec_from_file_location('publication_runtime_auditor',hostpath)
host=importlib.util.module_from_spec(spec);spec.loader.exec_module(host)
out=s/'publication-profile-r1-sequence-result.json';assert not out.exists()
result={'status':'RUNNING','bindingSha256':sha(bindingraw),'rows':[],'pairs':[],
        'notRun':[r['label'] for r in plan['sequence']]}
def save():out.write_bytes((json.dumps(result,indent=2)+'\n').encode())
def unchanged():
    for e in binding['files']:assert sha((s/e['path']).read_bytes())==e['sha256'],e['path']
    for e in binding['repoFiles']:assert sha((repo/e['path']).read_bytes())==e['sha256'],e['path']
    for arm,key in [('control','control'),('profile','candidate')]:
        source=s/plan['source'][arm]
        for e in audit['armInputs'][key]:
            raw=(source/e['path']).read_bytes()
            assert (len(raw),sha(raw))==(e['bytes'],e['sha256']),arm+':'+e['path']
        actual=host.capture_input_manifest(source)
        assert all(actual[k]==v for k,v in plan['runtime'][arm].items()),arm
save()
try:
    for index,row in enumerate(plan['sequence']):
        unchanged();label=row['label'];arm='profile' if row['profile'] else 'control'
        for f in [s/label,s/(label+'-summary.json'),s/(label+'-spec.json')]:assert not f.exists(),f
        args=[sys.executable,'-B',str(s/'run_publication_profile_timing.py'),str(repo),str(s),str(s/plan['source'][arm]),label,plan['seed']]
        if row['profile']:args.append('profile')
        result['activeRow']=row;result['notRun'].remove(label);save()
        print('START '+label,flush=True)
        proc=subprocess.run(args,capture_output=True,text=True)
        assert proc.returncode==0,proc.stdout[-2200:]+proc.stderr[-2200:]
        summary=json.loads((s/(label+'-summary.json')).read_bytes())
        result['rows'].append(summary);result.pop('activeRow');save()
        assert summary['exitCode']==0 and summary['runnerOutcome']=='PASS'
        receipt=json.loads((s/label/'result.json').read_bytes())
        assert receipt['cleanup']['status']=='PASS' and not receipt['cleanup']['errors'] and not receipt['cleanup']['ownedSurvivors']
        assert receipt['inputAudit']['beforeSha256']==receipt['inputAudit']['afterSha256']==plan['runtime'][arm]['sha256']
        assert receipt['cases'][0]['case']['path']==plan['applicationQuery']+('&'+plan['profileQuery'] if row['profile'] else '')
        unchanged()
        print(json.dumps({'label':label,'cold':summary['cold'],'hostSeconds':summary['hostMonotonicSeconds'],'cleanupSeconds':receipt['cleanup']['seconds']}),flush=True)
        if index>=1:
            tag='publication-profile-r1-pair-'+str(index)
            args=[sys.executable,'-B',str(s/'validate_publication_profile_pair_root.py'),str(s),str(repo),
              plan['sequence'][1]['label'],plan['sequence'][0 if index==1 else 2]['label'],'--tag',tag]
            checked=subprocess.run(args,capture_output=True,text=True)
            (s/(tag+'.log')).write_bytes((checked.stdout+checked.stderr).encode())
            assert checked.returncode==0,checked.stdout[-1800:]+checked.stderr[-1800:]
            result['pairs'].append({'status':'PASS','exitCode':0,'log':tag+'.log'});save()
    result['status']='FOCUSED_PUBLICATION_PROFILE_BOTH_COMPARISONS_PASS_PENDING_INTERPRETATION';save()
except BaseException as error:
    if 'activeRow' in result:result['failedRow']=result.pop('activeRow')
    result['status']='STOPPED_FAILURE';result['failure']=str(error);save();raise
