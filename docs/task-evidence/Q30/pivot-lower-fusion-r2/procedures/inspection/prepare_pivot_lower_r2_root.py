from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent
old=s/'scratch/pivot-lower-fusion-r1-preparation'
raw=(old/'source-audit.json').read_bytes()
assert hashlib.sha256(raw).hexdigest()=='09f2b38b543ee944d26d0a139a306b8125b1b432bba2008ed02183686e008baa'
audit=json.loads(raw);source=Path(audit['candidateSource'])
prep=s/'scratch/pivot-lower-fusion-r2-preparation';assert not prep.exists()
target=prep/'candidate';target.mkdir(parents=True)
for e in audit['armInputs']['candidate']:
 b=(source/e['path']).read_bytes();assert len(b)==e['bytes'] and hashlib.sha256(b).hexdigest()==e['sha256'],e['path']
 p=target/e['path'];p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
test='tests/contracts/Q30LuPivotCollectionContractTest.java'
p=target/test;b=p.read_bytes()
assert hashlib.sha256(b).hexdigest()=='7ed22987745acb2e9425d8dec8c863724f492f4eead84903115cbd413233915a'
needle=b'System.out.println("Q30 LU pivot collection contracts " + run());'
assert b.count(needle)==1;b=b.replace(needle,b'System.out.println("PASS: Q30 LU pivot collection contracts " + run());');p.write_bytes(b)
entries=[]
for e in audit['armInputs']['candidate']:
 p=target/e['path'];b=p.read_bytes();entries.append({'path':e['path'],'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest()})
changed=[a['path'] for a,b in zip(entries,audit['armInputs']['candidate']) if a!=b]
assert changed==[test]
audit['r1AuditSha256']=hashlib.sha256(raw).hexdigest();audit['candidateSource']=str(target)
audit['r2ChangedPaths']=changed;audit['r2Reason']='Test output prefix only: 388 assertions completed, but r1 maintained driver rejected missing PASS: marker. R1 gate FAIL retained; no solver or assertion change.'
audit['armInputs']['candidate']=entries;audit['overlayHashes'][test]=next(e['sha256'] for e in entries if e['path']==test)
out=prep/'source-audit.json';out.write_text(json.dumps(audit,indent=2)+'\n',encoding='utf-8');digest=hashlib.sha256(out.read_bytes()).hexdigest()
(s/'pivot-lower-fusion-r2-source-pair-path.txt').write_text(str(prep),encoding='utf-8')
for oldname,newname in [('run_pivot_lower_fusion_r1_gates.py','run_pivot_lower_fusion_r2_gates.py'),('run_pivot_lower_fusion_sequence.py','run_pivot_lower_fusion_r2_sequence.py')]:
 text=(s/oldname).read_text(encoding='utf-8')
 assert text.count('09f2b38b543ee944d26d0a139a306b8125b1b432bba2008ed02183686e008baa')==1
 text=text.replace('09f2b38b543ee944d26d0a139a306b8125b1b432bba2008ed02183686e008baa',digest)
 text=text.replace('pivot-lower-fusion-source-pair-path.txt','pivot-lower-fusion-r2-source-pair-path.txt')
 text=text.replace('pivot-lower-fusion-r1','pivot-lower-fusion-r2')
 p=s/newname;assert not p.exists();compile(text,newname,'exec');p.write_text(text,encoding='utf-8')
print(json.dumps({'status':'SOURCE_ONLY_R2_PREPARED_NOT_RUN','auditSha256':digest,'testSha256':audit['overlayHashes'][test],'sourceSha256':audit['overlayHashes']['src/com/lushprojects/circuitjs1/client/CirSim.java'],'changedFromR1':changed}))
