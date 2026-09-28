from pathlib import Path
import hashlib,io,json,subprocess,uuid
root=Path('[REDACTED_USER_PATH];s=Path(__file__).resolve().parent
pointer=s/'owned-zero-row-index-path.txt';assert not pointer.exists()
paths=[p for p in subprocess.check_output(['git','ls-files','-z','--','src','scripts','tests','war','.settings','.classpath','.project'],cwd=root).decode().split('\0') if p]
assert not any('__pycache__' in p or p.startswith('war/circuitjs1/') for p in paths)
assert 'tests/contracts/Q30OwnedLuZeroRowContractTest.java' in paths
assert 'src/com/lushprojects/circuitjs1/client/Q30NormalAcceptanceVerifier.java' not in paths
tree=subprocess.check_output(['git','write-tree'],cwd=root,text=True).strip()
stream=io.BytesIO(subprocess.check_output(['git','cat-file','--batch'],cwd=root,input=''.join(tree+':'+p+'\n' for p in paths).encode()))
def sha(raw):return hashlib.sha256(raw).hexdigest()
folder=s/('owned-zero-row-index-'+uuid.uuid4().hex);folder.mkdir()
records=[]
for p in paths:
    h=stream.readline().decode().split();assert h[1]=='blob';raw=stream.read(int(h[2]));assert stream.read(1)==b'\n'
    dest=folder/p;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
    records.append({'path':p,'sha256':sha(raw),'bytes':len(raw),'source':'index-blob'})
assert not stream.read()
jars=sorted((root/'.tools/gwt-2.7.0').glob('*.jar'));assert len(jars)==9
for p in jars:
    rel=p.relative_to(root);raw=p.read_bytes();dest=folder/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
    records.append({'path':rel.as_posix(),'sha256':sha(raw),'bytes':len(raw),'source':'pinned-local-jar'})
assert len(records)==1338
audit={'status':'EXACT_INDEX_SOURCE_EXPORTED_NOT_YET_VALIDATED','indexTree':tree,'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'inputs':records,'sourceOnly':True,'scope':'standalone staged zero-row optimization on committed plan2; unaccepted plan4 and normal-verifier hook excluded'}
(folder/'index-source-audit.json').write_text(json.dumps(audit,indent=2)+'\n',encoding='utf-8')
pointer.write_text(str(folder),encoding='utf-8')
print(json.dumps({'directory':str(folder),'inputs':len(records),'indexTree':tree}))
