from pathlib import Path
import json,hashlib,subprocess,time,sys
s=Path(__file__).resolve().parents[2]
prep=Path('$TASKROOT/scratch/d01-r10-preparation')
source=Path((prep/'source-path.txt').read_text(encoding='utf-8').strip())
raw=(prep/'source-audit.json').read_bytes()
assert hashlib.sha256(raw).hexdigest()=='3e802932fd1df9177371b0e3e2ae211b9837fddda66039d36847be5ad298c4c3'
audit=json.loads(raw)
assert len(audit['inputs'])==1340
assert audit['r10OverlayCount']==2
assert sorted(audit['changedFromR9'])==sorted(['src/com/lushprojects/circuitjs1/client/Q30DiagnosticAdmissionVerifier.java','src/com/lushprojects/circuitjs1/client/Q30D01HostBoundarySnapshot.java'])
assert {x['path']:x['r10Sha256'] for x in audit['r10Overlays']}=={'src/com/lushprojects/circuitjs1/client/Q30DiagnosticAdmissionVerifier.java':'7180d16178a712b1a0fc35a9be70e2b693d1442a802b1c2e1be99b78233efe60','src/com/lushprojects/circuitjs1/client/Q30D01HostBoundarySnapshot.java':'f7cded8104787967e36602872775e2ab7a0d6d316b15adbf8b7811881d257178'}
out=s/'d01-r10-native-gwt-gates.json'
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
 ('native-d01-r10',['pwsh','-NoProfile','-File',str(s/'native-d01-r5.ps1'),'-SourceRoot',str(source),'-Label','native-d01-r10']),
 ('d01-r10-gwt',['pwsh','-NoProfile','-File',str(s/'build-run.ps1'),'-SourceRoot',str(source),'-Label','d01-r10-gwt'])]:
  unchanged();t=time.monotonic();print('START '+label,flush=True)
  p=subprocess.run(command)
  record['steps'].append({'name':label,'command':command,'exitCode':p.returncode,'hostElapsedSeconds':time.monotonic()-t});save()
  assert p.returncode==0,label+' failed'
  unchanged()
 record['status']='NATIVE_GWT_PASS_BROWSER_NOT_RUN'
 save();print(record['status'],flush=True)
except BaseException as e:
 record['status']='STOPPED_FAILURE';record['failure']=str(e);save();raise
