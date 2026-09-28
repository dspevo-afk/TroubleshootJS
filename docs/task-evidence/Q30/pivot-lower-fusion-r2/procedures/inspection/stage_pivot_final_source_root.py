from pathlib import Path
import hashlib,io,json,subprocess
s=Path(__file__).resolve().parent;root=Path('<TASK_TEMP>
f=s/'pivot-lower-fusion-final-source';a=json.loads((f/'final-source-audit.json').read_bytes());sha=lambda b:hashlib.sha256(b).hexdigest()
g=json.loads((s/'pivot-lower-fusion-final-gates.json').read_bytes());eq=json.loads((s/'pivot-lower-fusion-root-runtime-equality.json').read_bytes())
assert g['status']=='FINAL_SOURCE_GATES_PASS_PENDING_ROOT_INPUT_EQUALITY' and all(x['exitCode']==0 for x in g['steps']) and eq['status']=='PASS'
assert not subprocess.check_output(['git','diff','--cached','--name-only'],cwd=root)
for row in a['inputs']:
    b=(f/row['path']).read_bytes();assert len(b)==row['bytes'] and sha(b)==row['sha256']
staged=[]
for row in a['changedFiles']:
    name=row['path'];b=(f/name).read_bytes();assert sha(b)==row['finalSha256'] and sha((root/name).read_bytes())==row['workingSha256']
    blob=subprocess.check_output(['git','hash-object','-w','--stdin'],input=b,cwd=root).decode().strip()
    subprocess.check_call(['git','update-index','--add','--cacheinfo','100644,'+blob+','+name],cwd=root)
    staged.append({'path':name,'blob':blob,'sha256':sha(b)})
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],cwd=root,text=True).splitlines())=={x['path'] for x in staged}
tree=subprocess.check_output(['git','write-tree'],cwd=root,text=True).strip()
tracked=[r for r in a['inputs'] if r['source']!='pinned-local-jar']
stream=io.BytesIO(subprocess.check_output(['git','cat-file','--batch'],cwd=root,input=''.join(tree+':'+r['path']+'\n' for r in tracked).encode()))
for row in tracked:
    h=stream.readline().decode().split();assert h[1]=='blob';b=stream.read(int(h[2]));assert stream.read(1)==b'\n'
    assert sha(b)==row['sha256'] and len(b)==row['bytes'],row['path']
assert not stream.read()
subprocess.check_call(['git','diff','--cached','--check'],cwd=root)
result={'status':'SELECTIVE_STAGING_AND_FINAL_SOURCE_EQUALITY_PASS','sourceInputs':len(a['inputs']),'trackedBlobInputs':len(tracked),'pinnedJars':9,'files':staged,'tree':tree,'baseHead':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'scope':'Only reviewed LU kernel/new focused test/registration staged; all 1339 consumed final-source inputs match the passing fixture. Unaccepted plan4/normal hook remain unstaged.'}
(s/'pivot-lower-fusion-staging-audit.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
