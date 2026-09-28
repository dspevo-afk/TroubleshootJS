from pathlib import Path
import json,hashlib,subprocess,time,sys
s=Path(__file__).resolve().parent
prep=s/'scratch/d01-r8-source-only-6193901599054f2ea474d199e12d23ae'
source=Path((prep/'source-path.txt').read_text(encoding='utf-8').strip())
raw=(prep/'source-audit.json').read_bytes()
assert hashlib.sha256(raw).hexdigest()=='717cc9bcf649d87b6dd801d76a52b78143e35b74acd395387807eefff7544d38'
audit=json.loads(raw)
assert len(audit['inputs'])==1340
out=s/'d01-r8-native-gwt-gates.json'
assert not out.exists()
record={'status':'RUNNING','sourceAuditSha256':hashlib.sha256(raw).hexdigest(),'steps':[]}
def unchanged():
 for e in audit['inputs']:
  data=(source/e['path']).read_bytes()
  assert hashlib.sha256(data).hexdigest()==e['sha256'] and len(data)==e['bytes'],e['path']
def save():out.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
save()
try:
 for label,command in [
 ('native-d01-r8',['pwsh','-NoProfile','-File',str(s/'native-d01-r5.ps1'),'-SourceRoot',str(source),'-Label','native-d01-r8']),
 ('d01-r8-gwt',['pwsh','-NoProfile','-File',str(s/'build-run.ps1'),'-SourceRoot',str(source),'-Label','d01-r8-gwt'])]:
  unchanged();t=time.monotonic();print('START '+label,flush=True)
  p=subprocess.run(command)
  record['steps'].append({'name':label,'command':command,'exitCode':p.returncode,'hostElapsedSeconds':time.monotonic()-t});save()
  assert p.returncode==0,label+' failed'
  unchanged()
 record['status']='NATIVE_GWT_PASS_BROWSER_NOT_RUN'
 save();print(record['status'],flush=True)
except BaseException as e:
 record['status']='STOPPED_FAILURE';record['failure']=str(e);save();raise
