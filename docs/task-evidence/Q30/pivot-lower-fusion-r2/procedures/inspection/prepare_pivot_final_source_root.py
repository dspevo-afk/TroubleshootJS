from pathlib import Path
import hashlib,io,json,subprocess
s=Path(__file__).resolve().parent
root=Path('<TASK_TEMP>
pair=s/'scratch/pivot-lower-fusion-r2-preparation'
audit=json.loads((pair/'source-audit.json').read_bytes())
sha=lambda b:hashlib.sha256(b).hexdigest()
result=json.loads((s/'pivot-lower-fusion-sequence-result.json').read_bytes())
assert result['status']=='FOCUSED_PAIRS_PASS_PENDING_ROOT_ACCEPTANCE' and len(result['rows'])==8 and len(result['pairs'])==4 and not result['notRun']
assert all(p['status']=='PASS' for p in result['pairs'])
assert not subprocess.check_output(['git','diff','--cached','--name-only'],cwd=root)
for arm in ('control','candidate'):
    base=Path(audit[arm+'Source'])
    for row in audit['armInputs'][arm]:
        b=(base/row['path']).read_bytes()
        assert sha(b)==row['sha256'] and len(b)==row['bytes'],(arm,row['path'])
cir='src/com/lushprojects/circuitjs1/client/CirSim.java'
driver='scripts/verify-current-contracts.ps1'
test='tests/contracts/Q30LuPivotCollectionContractTest.java'
control=Path(audit['controlSource']);candidate=Path(audit['candidateSource'])
assert sha((root/cir).read_bytes())=='2658e94560fd9e1f0e9157a54be773bd9568d9350fa6ce56c39acaa7cf87e0b1'
assert (root/driver).read_bytes()==(control/driver).read_bytes()
assert not (root/test).exists()
def kernel(raw):
    start=raw.index(b'    private static boolean lu_factorAfterInputScan(')
    brace=raw.index(b'{',start);depth=1;i=brace+1
    while depth:
        if raw[i:i+1]==b'{':depth+=1
        elif raw[i:i+1]==b'}':depth-=1
        i+=1
    return raw[start:i]
old=kernel((control/cir).read_bytes());new=kernel((candidate/cir).read_bytes())
assert (root/cir).read_bytes().replace(old,new)==(candidate/cir).read_bytes()
registration=b"        @{ Name = 'Q30LuPivotCollectionContractTest'; Marker = 'Q30 LU pivot collection contracts ' },\n"
anchor=b"        @{ Name = 'Q30OwnedLuZeroRowContractTest'; Marker = 'Q30 owned LU zero-row contracts ' },\n"
assert (control/driver).read_bytes().replace(anchor,anchor+registration)==(candidate/driver).read_bytes()
head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
headcir=subprocess.check_output(['git','show',head+':'+cir],cwd=root)
headdriver=subprocess.check_output(['git','show',head+':'+driver],cwd=root)
assert headcir.count(old)==1 and headdriver.count(anchor)==1
desired={cir:headcir.replace(old,new),driver:headdriver.replace(anchor,anchor+registration),test:(candidate/test).read_bytes().replace(b'\r\n',b'\n')}
paths=[p for p in subprocess.check_output(['git','ls-tree','-r','--name-only','-z',head,'--','src','scripts','tests','war','.settings','.classpath','.project'],cwd=root).decode().split('\0') if p]
assert test not in paths and 'src/com/lushprojects/circuitjs1/client/Q30NormalAcceptanceVerifier.java' not in paths
assert not any('__pycache__' in p or p.startswith('war/circuitjs1/') for p in paths)
stream=io.BytesIO(subprocess.check_output(['git','cat-file','--batch'],cwd=root,input=''.join(head+':'+p+'\n' for p in paths).encode()))
folder=s/'pivot-lower-fusion-final-source';assert not folder.exists();folder.mkdir()
records=[]
for p in paths:
    header=stream.readline().decode().split();assert header[1]=='blob'
    raw=stream.read(int(header[2]));assert stream.read(1)==b'\n'
    b=desired.get(p,raw);dest=folder/p;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(b)
    records.append({'path':p,'bytes':len(b),'sha256':sha(b),'source':'proposed-final-blob' if p in desired else 'head-blob'})
assert not stream.read()
(folder/test).write_bytes(desired[test]);records.append({'path':test,'bytes':len(desired[test]),'sha256':sha(desired[test]),'source':'proposed-final-blob'})
jars=sorted((root/'.tools/gwt-2.7.0').glob('*.jar'));assert len(jars)==9
for p in jars:
    rel=p.relative_to(root);b=p.read_bytes();dest=folder/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(b)
    records.append({'path':rel.as_posix(),'sha256':sha(b),'bytes':len(b),'source':'pinned-local-jar'})
assert len(records)==1339
for name in desired:(root/name).write_bytes((candidate/name).read_bytes())
receipt={'status':'WORKING_SOURCE_INTEGRATED_FINAL_SOURCE_EXPORTED_UNSTAGED','baseHead':head,'scope':'LU kernel and focused test/registration only; proposed final blobs exclude unaccepted plan4 and normal hook','sourceOnly':True,'inputCount':len(records),'inputs':records,'changedFiles':[{'path':p,'finalSha256':sha(b),'workingSha256':sha((root/p).read_bytes())} for p,b in desired.items()]}
(folder/'final-source-audit.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
(s/'pivot-lower-fusion-final-source-path.txt').write_text(str(folder),encoding='utf-8')
print(json.dumps({'status':receipt['status'],'inputCount':len(records),'auditSha256':sha((folder/'final-source-audit.json').read_bytes()),'changedFiles':receipt['changedFiles']}))
