from pathlib import Path
import hashlib,json,sys
s=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
old=Path((s/'sticky-finite-r2-source-pair-path.txt').read_text().strip())
raw=(old/'source-audit.json').read_bytes()
assert sha(raw)=='80110e21c0d5f4baf31460d3e0a12e12d9fc5cdecc1e83d49319fbc226a3c82a'
records=json.loads(raw)['armInputs']['candidate'];assert len(records)==1339
control=old/'candidate';draft=s/'scratch/pivot-lower-fusion-draft/candidate'
cir='src/com/lushprojects/circuitjs1/client/CirSim.java'
test='tests/contracts/Q30LuPivotCollectionContractTest.java'
assert sha((control/cir).read_bytes())=='2658e94560fd9e1f0e9157a54be773bd9568d9350fa6ce56c39acaa7cf87e0b1'
overlays={cir:sys.argv[1],test:sys.argv[2]}
for p,h in overlays.items():assert sha((draft/p).read_bytes())==h,p
prep=s/'scratch/pivot-lower-fusion-r1-preparation';assert not prep.exists()
candidate=prep/'candidate';candidate.mkdir(parents=True)
for e in records:
 b=(control/e['path']).read_bytes();assert sha(b)==e['sha256'] and len(b)==e['bytes'],e['path']
 target=candidate/e['path'];target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(b)
for p in overlays:
 target=candidate/p;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((draft/p).read_bytes())
driver='scripts/verify-current-contracts.ps1';path=candidate/driver;b=path.read_bytes()
needle=b"        @{ Name = 'Q30OwnedLuZeroRowContractTest'; Marker = 'Q30 owned LU zero-row contracts ' },"
assert b.count(needle)==1
ending=b'\r\n' if b'\r\n' in b else b'\n'
addition=b"        @{ Name = 'Q30LuPivotCollectionContractTest'; Marker = 'Q30 LU pivot collection contracts ' },"
path.write_bytes(b.replace(needle,needle+ending+addition))
inputs=[]
for p in sorted(candidate.rglob('*')):
 if p.is_file():
  b=p.read_bytes();inputs.append({'path':p.relative_to(candidate).as_posix(),'bytes':len(b),'sha256':sha(b)})
assert len(inputs)==1340
base={e['path']:e['sha256'] for e in records}
changed=[e['path'] for e in inputs if e['sha256']!=base.get(e['path'])]
assert set(changed)=={cir,test,driver}
assert sum(e['path'].startswith('war/') for e in inputs)==29
audit={'status':'SOURCE_ONLY_PREPARED_NOT_VALIDATED','baselineAuditSha256':sha(raw),'controlSource':str(control),'candidateSource':str(candidate),'productionDifferences':[cir],'testAndRegistrationChanges':[test,driver],'generatedWarCopied':False,'armInputs':{'control':records,'candidate':inputs},'overlayHashes':overlays}
(prep/'source-audit.json').write_text(json.dumps(audit,indent=2)+'\n',encoding='utf-8')
(s/'pivot-lower-fusion-source-pair-path.txt').write_text(str(prep),encoding='utf-8')
print(json.dumps({'status':audit['status'],'candidateInputs':len(inputs),'auditSha256':sha((prep/'source-audit.json').read_bytes()),'productionDifferences':audit['productionDifferences']}))
